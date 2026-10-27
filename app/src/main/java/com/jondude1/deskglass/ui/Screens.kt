package com.jondude1.deskglass.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.BuildConfig
import com.jondude1.deskglass.MainViewModel

enum class Screen { Home, Calendar, Notes, Checklists, Chat, Village, Settings }

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        GlassButton("‹  Home", onBack)
        Text(title, color = Glass.Ink, fontSize = 34.sp, fontWeight = FontWeight.Light)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, blurb: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar(title, onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.widthIn(max = 640.dp).padding(bottom = 80.dp)) {
                Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Coming next", color = Glass.InkFaint, fontSize = 16.sp, letterSpacing = 4.sp)
                    Text(blurb, color = Glass.Ink, fontSize = 26.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val cityStatus by vm.cityStatus.collectAsState()
    val updateStatus by vm.updateStatus.collectAsState()
    var city by remember { mutableStateOf(vm.prefs.place?.label ?: "") }
    val hasKey by vm.hasApiKey.collectAsState()
    val facts by vm.memoryFacts.collectAsState()
    var keyDraft by remember { mutableStateOf("") }
    var nameDraft by remember { mutableStateOf(vm.prefs.userName) }
    var modelDraft by remember { mutableStateOf(vm.prefs.chatModel) }
    var fastDraft by remember { mutableStateOf(vm.prefs.fastModel) }
    var aiStatus by remember { mutableStateOf<String?>(null) }
    var confirmMemory by remember { mutableStateOf(false) }
    val driveFolder by vm.driveFolder.collectAsState()
    val driveStatus by vm.driveStatus.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setDriveFolder(uri)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Settings", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("WEATHER")
                    Text("City", color = Glass.InkDim, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            singleLine = true,
                            placeholder = { Text("Erie, Pennsylvania", color = Glass.InkFaint) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.saveCity(city) }),
                            colors = glassFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton("Save", { vm.saveCity(city) }, filled = true)
                    }
                    cityStatus?.let { Text(it, color = Glass.InkDim, fontSize = 17.sp) }
                }
            }

            GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("CHAT AND AI")
                    Text(
                        if (hasKey) vm.apiKeyHint().ifEmpty { "API key saved" } else "No API key yet",
                        color = Glass.Ink,
                        fontSize = 20.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            singleLine = true,
                            placeholder = { Text("Paste your Anthropic API key", color = Glass.InkFaint) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            colors = glassFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton("Save key", {
                            if (keyDraft.isNotBlank()) {
                                vm.saveApiKey(keyDraft)
                                keyDraft = ""
                                aiStatus = "Key saved."
                            }
                        }, filled = true)
                    }
                    Text("Your name", color = Glass.InkDim, fontSize = 18.sp)
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        colors = glassFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Chat model", color = Glass.InkDim, fontSize = 18.sp)
                    OutlinedTextField(
                        value = modelDraft,
                        onValueChange = { modelDraft = it },
                        singleLine = true,
                        colors = glassFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Quick model (quick-add, tidy-ups, clock line)", color = Glass.InkDim, fontSize = 18.sp)
                    OutlinedTextField(
                        value = fastDraft,
                        onValueChange = { fastDraft = it },
                        singleLine = true,
                        colors = glassFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassButton("Save name and model", {
                            vm.prefs.userName = nameDraft
                            vm.prefs.chatModel = modelDraft
                            modelDraft = vm.prefs.chatModel
                            vm.prefs.fastModel = fastDraft
                            fastDraft = vm.prefs.fastModel
                            aiStatus = "Saved."
                        })
                        aiStatus?.let { Text(it, color = Glass.InkDim, fontSize = 17.sp) }
                    }
                    Text(
                        if (facts.isEmpty()) "Claude hasn't saved any memories about you yet."
                        else "Claude remembers ${facts.size} thing${if (facts.size == 1) "" else "s"} about you.",
                        color = Glass.InkDim,
                        fontSize = 17.sp,
                    )
                    if (facts.isNotEmpty()) {
                        GlassButton(if (confirmMemory) "Tap again to erase" else "Clear memory", {
                            if (confirmMemory) {
                                vm.clearMemory()
                                confirmMemory = false
                            } else {
                                confirmMemory = true
                            }
                        })
                    }
                }
            }

            GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                var urlDraft by remember { mutableStateOf(vm.prefs.civUrl) }
                var civModelDraft by remember { mutableStateOf(vm.prefs.civModel) }
                var civStatus by remember { mutableStateOf<String?>(null) }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("VILLAGE BRAIN (YOUR LOCAL MODEL)")
                    Text("Server address, for example http://100.101.102.103:11434", color = Glass.InkDim, fontSize = 18.sp)
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        singleLine = true,
                        placeholder = { Text("http://your-tailscale-ip:11434", color = Glass.InkFaint) },
                        colors = glassFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Model name, exactly as your server lists it", color = Glass.InkDim, fontSize = 18.sp)
                    OutlinedTextField(
                        value = civModelDraft,
                        onValueChange = { civModelDraft = it },
                        singleLine = true,
                        placeholder = { Text("llama3.1:8b", color = Glass.InkFaint) },
                        colors = glassFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassButton("Save and test", {
                            vm.prefs.civUrl = urlDraft
                            vm.prefs.civModel = civModelDraft
                            civStatus = "Testing…"
                            scope.launch { civStatus = vm.testCivBrain(urlDraft, civModelDraft) }
                        }, filled = true)
                        civStatus?.let { Text(it, color = Glass.InkDim, fontSize = 17.sp, modifier = Modifier.weight(1f)) }
                    }
                    Text(
                        "Works with Ollama, LM Studio and similar servers. Leave it empty and the villagers use Claude through your saved API key (the quick model), about one thought every few seconds.",
                        color = Glass.InkFaint, fontSize = 15.sp,
                    )
                }
            }

            GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                val log by vm.errorLog.text.collectAsState()
                val clipboard = LocalClipboardManager.current
                Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("ERROR LOG")
                    Text(
                        if (log.isBlank()) "No errors yet." else log.take(3000),
                        color = if (log.isBlank()) Glass.InkDim else Glass.Ink,
                        fontSize = 14.sp,
                    )
                    if (log.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassButton("Copy all", { clipboard.setText(AnnotatedString(log)) })
                            GlassButton("Clear", { vm.errorLog.clear() })
                        }
                    }
                }
            }

            GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("GOOGLE DRIVE BACKUP")
                    Text(
                        if (driveFolder != null) "Notes are copied to: $driveFolder" else "Not set up yet",
                        color = Glass.Ink,
                        fontSize = 20.sp,
                    )
                    Text(
                        "Pick a folder in Google Drive (make a new one called DeskGlass Notes). Each note is saved there as a Markdown file, with a picture of any handwriting. It updates a little after you stop editing.",
                        color = Glass.InkDim,
                        fontSize = 17.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(if (driveFolder == null) "Choose folder" else "Change folder", { picker.launch(null) }, filled = driveFolder == null)
                        if (driveFolder != null) {
                            GlassButton("Sync now", { vm.syncDriveNow() })
                            GlassButton("Stop", { vm.stopDriveSync() })
                        }
                    }
                    driveStatus?.let { Text(it, color = Glass.InkDim, fontSize = 17.sp) }
                }
            }

            GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("UPDATES")
                    Text("Installed version ${BuildConfig.VERSION_NAME}", color = Glass.Ink, fontSize = 22.sp)
                    Text(
                        "DeskGlass checks for new versions on its own and shows a bar at the top when one is ready.",
                        color = Glass.InkDim,
                        fontSize = 17.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassButton("Check for updates", { vm.checkForUpdate(force = true) })
                        updateStatus?.let { Text(it, color = Glass.InkDim, fontSize = 17.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Glass.InkFaint, fontSize = 15.sp, letterSpacing = 4.sp)
}

@Composable
private fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Glass.Ink,
    unfocusedTextColor = Glass.Ink,
    cursorColor = Glass.Accent,
    focusedBorderColor = Color.White.copy(alpha = 0.7f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
    focusedContainerColor = Color.White.copy(alpha = 0.06f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
)
