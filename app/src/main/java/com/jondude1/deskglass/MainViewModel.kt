package com.jondude1.deskglass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jondude1.deskglass.data.Checklist
import com.jondude1.deskglass.data.ChecklistStore
import com.jondude1.deskglass.data.Event
import com.jondude1.deskglass.data.EventStore
import com.jondude1.deskglass.data.Note
import com.jondude1.deskglass.data.NoteStore
import com.jondude1.deskglass.data.Prefs
import com.jondude1.deskglass.data.WeatherApi
import com.jondude1.deskglass.data.WeatherNow
import com.jondude1.deskglass.update.UpdateInfo
import com.jondude1.deskglass.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
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
