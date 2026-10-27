package com.jondude1.deskglass

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jondude1.deskglass.ai.AiTasks
import com.jondude1.deskglass.ai.ApiException
import com.jondude1.deskglass.ai.ChatEngine
import com.jondude1.deskglass.ai.ChatMessage
import com.jondude1.deskglass.ai.ChatStore
import com.jondude1.deskglass.ai.GameGenerator
import com.jondude1.deskglass.ai.MemoryStore
import com.jondude1.deskglass.data.Checklist
import com.jondude1.deskglass.data.ChecklistStore
import com.jondude1.deskglass.data.DriveSync
import com.jondude1.deskglass.data.Event
import com.jondude1.deskglass.data.GameInfo
import com.jondude1.deskglass.data.GameStore
import com.jondude1.deskglass.data.EventStore
import com.jondude1.deskglass.data.Note
import com.jondude1.deskglass.data.NoteStore
import com.jondude1.deskglass.data.Prefs
import com.jondude1.deskglass.data.WeatherApi
import com.jondude1.deskglass.data.WeatherNow
import com.jondude1.deskglass.update.UpdateInfo
import com.jondude1.deskglass.ui.isActiveHours
import com.jondude1.deskglass.update.Updater
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

sealed interface WeatherState {
    data object NeedsCity : WeatherState
    data object Loading : WeatherState
    data class Ready(val now: WeatherNow) : WeatherState
    data class Failed(val message: String) : WeatherState
}

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
}

data class PlayRequest(val gameId: String, val mode: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val ai = AiTasks(prefs)
    private val eventStore = EventStore(app)
    val events: StateFlow<List<Event>> = eventStore.events

    private val noteStore = NoteStore(app)
    val notes: StateFlow<List<Note>> = noteStore.notes

    fun saveNote(note: Note) = noteStore.upsert(note)
    fun deleteNote(id: String) = noteStore.delete(id)
    fun pruneEmptyNotes(keepId: String?) = noteStore.pruneEmpty(keepId)

    private val checklistStore = ChecklistStore(app)
    val checklists: StateFlow<List<Checklist>> = checklistStore.lists

    fun addChecklist(name: String): String = checklistStore.addList(name)
    fun renameChecklist(id: String, name: String) = checklistStore.renameList(id, name)
    fun deleteChecklist(id: String) = checklistStore.deleteList(id)
    fun addChecklistItem(listId: String, text: String) = checklistStore.addItem(listId, text)
    fun toggleChecklistItem(listId: String, itemId: String) = checklistStore.toggleItem(listId, itemId)
    fun deleteChecklistItem(listId: String, itemId: String) = checklistStore.deleteItem(listId, itemId)
    fun clearDoneChecklistItems(listId: String) = checklistStore.clearDone(listId)

    fun saveEvent(event: Event) = eventStore.upsert(event)
    fun deleteEvent(id: String) = eventStore.delete(id)

    // ---- Chat ----

    private val chatStore = ChatStore(app)
    private val memoryStore = MemoryStore(app)
    private val chatEngine = ChatEngine(prefs, chatStore, memoryStore, eventStore, checklistStore, noteStore)

    val chatMessages: StateFlow<List<ChatMessage>> = chatStore.messages
    val memoryFacts: StateFlow<List<String>> = memoryStore.facts

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _hasApiKey = MutableStateFlow(prefs.apiKey.isNotBlank())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    val userName: String get() = prefs.userName

    fun sendChat(text: String) {
        if (_chatBusy.value || text.isBlank()) return
        viewModelScope.launch {
            _chatBusy.value = true
            _chatError.value = null
            try {
                chatEngine.send(text)
            } catch (e: Exception) {
                _chatError.value = friendly(e)
            } finally {
                _chatBusy.value = false
            }
        }
    }

    fun newChat() {
        chatStore.clear()
        _chatError.value = null
    }

    fun clearMemory() = memoryStore.clear()

    fun errorMessage(e: Exception): String = friendly(e)

    /** Puts items on the named checklist, creating it if needed. */
    fun addToChecklist(listName: String, items: List<String>) {
        val existing = checklistStore.lists.value.firstOrNull { it.name.equals(listName, ignoreCase = true) }
        val id = existing?.id ?: checklistStore.addList(listName)
        items.forEach { checklistStore.addItem(id, it) }
    }

    // ---- Google Drive folder sync ----

    private val driveSync = DriveSync(app, prefs)
    private val driveLock = Mutex()

    private val _driveFolder = MutableStateFlow(prefs.driveFolderName.ifBlank { null }.takeIf { prefs.driveFolder != null })
    val driveFolder: StateFlow<String?> = _driveFolder.asStateFlow()

    private val _driveStatus = MutableStateFlow<String?>(null)
    val driveStatus: StateFlow<String?> = _driveStatus.asStateFlow()

    fun setDriveFolder(uri: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Some providers don't offer persistable access; syncing may still work this session.
        }
        prefs.driveFolder = uri.toString()
        prefs.driveFolderName = DocumentFile.fromTreeUri(app, uri)?.name ?: "Chosen folder"
        _driveFolder.value = prefs.driveFolderName
        syncDriveNow()
    }

    fun stopDriveSync() {
        prefs.driveFolder = null
        _driveFolder.value = null
        _driveStatus.value = null
    }

    fun syncDriveNow() {
        viewModelScope.launch { runDriveSync(announce = true) }
    }

    private suspend fun runDriveSync(announce: Boolean) {
        if (prefs.driveFolder == null) return
        driveLock.withLock {
            if (announce) _driveStatus.value = "Syncing…"
            try {
                val result = driveSync.sync(noteStore.notes.value)
                val time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                _driveStatus.value = "$result Last checked $time."
            } catch (e: Exception) {
                _driveStatus.value = "Drive sync failed: ${e.message}"
            }
        }
    }

    init {
        // Back up a little after notes stop changing.
        viewModelScope.launch {
            noteStore.notes.collectLatest {
                delay(30_000L)
                runDriveSync(announce = false)
            }
        }
    }

    // ---- Games ----

    private val gameStore = GameStore(app)
    val games: StateFlow<List<GameInfo>> = gameStore.games
    private val gameGenerator = GameGenerator(prefs)
    private val gameMutex = Mutex()

    private val _gameStatus = MutableStateFlow<String?>(null)
    val gameStatus: StateFlow<String?> = _gameStatus.asStateFlow()

    private val _narration = MutableStateFlow<String?>(null)
    val narration: StateFlow<String?> = _narration.asStateFlow()

    private val _play = MutableStateFlow<PlayRequest?>(null)
    val play: StateFlow<PlayRequest?> = _play.asStateFlow()

    fun gameHtml(id: String): String? = gameStore.html(id)
    fun startPlay(id: String, mode: String) { _play.value = PlayRequest(id, mode) }

    /** Called from the game page, on a background thread. */
    fun onNarrate(text: String) {
        val t = text.trim().take(80)
        if (t.isEmpty()) return
        _narration.value = t
        viewModelScope.launch {
            delay(6_000L)
            if (_narration.value == t) _narration.value = null
        }
    }

    fun clearNarration() { _narration.value = null }

    fun makeGameNow() {
        viewModelScope.launch { generateGame(manual = true) }
    }

    private suspend fun generateGame(manual: Boolean): Boolean = gameMutex.withLock {
        if (prefs.apiKey.isBlank()) {
            if (manual) _gameStatus.value = "Add your API key in Settings first."
            return@withLock false
        }
        _gameStatus.value = "Claude is writing a new game… this takes a couple of minutes."
        try {
            val g = gameGenerator.generate(gameStore.games.value.map { it.title })
            gameStore.add(g.title, g.blurb, g.html)
            _gameStatus.value = "New game: ${g.title}"
            true
        } catch (e: Exception) {
            _gameStatus.value = "Couldn't make a game: " + friendly(e)
            false
        }
    }

    init {
        // A new game at 8, 12 and 4 on weekdays. The first game is made whenever there is a key.
        viewModelScope.launch {
            var lastTry = 0L
            while (true) {
                delay(60_000L)
                if (prefs.apiKey.isBlank()) continue
                if (System.currentTimeMillis() - lastTry < 10 * 60_000L) continue
                val t = LocalDateTime.now()
                val empty = gameStore.games.value.isEmpty()
                val slotHour = listOf(8, 12, 16).lastOrNull { it <= t.hour }
                val slot = if (isActiveHours(t) && slotHour != null) "${t.toLocalDate()}-$slotHour" else null
                val due = (slot != null && prefs.lastGameSlot != slot) || empty
                if (!due) continue
                lastTry = System.currentTimeMillis()
                if (generateGame(manual = false) && slot != null) prefs.lastGameSlot = slot
            }
        }
    }

    // ---- The line under the clock ----

    private val _idleLine = MutableStateFlow(prefs.idleLine.ifBlank { null })
    val idleLine: StateFlow<String?> = _idleLine.asStateFlow()

    private suspend fun refreshIdleLine() {
        if (prefs.apiKey.isBlank()) return
        if (System.currentTimeMillis() - prefs.idleLineAt < 2 * 60 * 60_000L) return
        try {
            val line = ai.idleLine(prefs.recentIdleLines())
            if (line.isNotBlank()) {
                prefs.pushIdleLine(line)
                _idleLine.value = line
            }
        } catch (e: Exception) {
            // Quiet on purpose: the clock simply keeps its last line.
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                refreshIdleLine()
                delay(15 * 60_000L)
            }
        }
    }

    fun saveApiKey(key: String) {
        prefs.apiKey = key
        _hasApiKey.value = prefs.apiKey.isNotBlank()
        viewModelScope.launch { refreshIdleLine() }
    }

    fun apiKeyHint(): String {
        val k = prefs.apiKey
        return if (k.length < 8) "" else "Saved key ending in ${k.takeLast(4)}"
    }

    private fun friendly(e: Exception): String = when {
        e is ApiException && e.code == 401 -> "Claude rejected the API key. Check it in Settings."
        e is ApiException && e.code == 404 -> "Claude couldn't find that model. Check the model name in Settings."
        e is ApiException && e.code == 429 -> "Too many requests right now. Try again in a moment."
        e is ApiException && e.code >= 500 -> "Claude is busy right now. Try again in a moment."
        e is ApiException -> e.message ?: "The request failed."
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ->
            "Can't reach Claude. Check the internet connection."
        else -> e.message ?: "Something went wrong."
    }

    private val _weather = MutableStateFlow<WeatherState>(WeatherState.NeedsCity)
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _cityStatus = MutableStateFlow<String?>(null)
    val cityStatus: StateFlow<String?> = _cityStatus.asStateFlow()

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()

    init {
        // This tablet sits in the foreground all day, so onResume rarely fires.
        // Keep weather fresh and keep looking for new versions on a timer.
        viewModelScope.launch {
            while (true) {
                refreshWeather()
                delay(20 * 60_000L)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30 * 60_000L)
                checkForUpdate(force = false)
            }
        }
    }

    fun onResume() {
        checkForUpdate(force = false)
    }

    // ---- Weather ----

    fun refreshWeather() {
        viewModelScope.launch {
            val place = prefs.place
            if (place == null) {
                _weather.value = WeatherState.NeedsCity
                return@launch
            }
            val previous = (_weather.value as? WeatherState.Ready)?.now
            if (previous == null) _weather.value = WeatherState.Loading
            try {
                _weather.value = WeatherState.Ready(WeatherApi.fetch(place))
            } catch (e: Exception) {
                // Keep showing the last good reading if the network blips.
                _weather.value = if (previous != null) {
                    WeatherState.Ready(previous)
                } else {
                    WeatherState.Failed(e.message ?: "offline")
                }
            }
        }
    }

    fun saveCity(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _cityStatus.value = "Looking up ${input.trim()}…"
            try {
                val place = WeatherApi.geocode(input)
                if (place == null) {
                    _cityStatus.value = "No match for \"${input.trim()}\"."
                } else {
                    prefs.place = place
                    _cityStatus.value = "Using ${place.label}."
                    _weather.value = WeatherState.Loading
                    refreshWeather()
                }
            } catch (e: Exception) {
                _cityStatus.value = "Couldn't look that up (${e.message})."
            }
        }
    }

    // ---- Updates ----

    fun checkForUpdate(force: Boolean) {
        if (_update.value is UpdateUi.Downloading) return
        if (!force && System.currentTimeMillis() - prefs.lastUpdateCheck < 30 * 60_000L) return
        viewModelScope.launch {
            if (force) _updateStatus.value = "Checking…"
            try {
                val info = Updater.fetchLatest()
                prefs.lastUpdateCheck = System.currentTimeMillis()
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                    _update.value = UpdateUi.Available(info)
                    if (force) _updateStatus.value = "Version ${info.versionName} is available."
                } else if (force) {
                    _updateStatus.value = "You're up to date."
                }
            } catch (e: Exception) {
                if (force) _updateStatus.value = "Couldn't check for updates (${e.message})."
            }
        }
    }

    fun startUpdate() {
        val info = (_update.value as? UpdateUi.Available)?.info ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            _updateStatus.value = "Allow installs from DeskGlass on the next screen, then tap Update again."
            Updater.openInstallPermission(app)
            return
        }
        viewModelScope.launch {
            _updateStatus.value = null
            _update.value = UpdateUi.Downloading(info, 0f)
            try {
                val apk = Updater.download(app, info) { p ->
                    _update.value = UpdateUi.Downloading(info, p)
                }
                Updater.install(app, apk)
            } catch (e: Exception) {
                _updateStatus.value = "Update failed (${e.message})."
            }
            _update.value = UpdateUi.Available(info)
        }
    }

    fun dismissUpdate() {
        if (_update.value is UpdateUi.Available) _update.value = UpdateUi.Idle
        _updateStatus.value = null
    }
}
