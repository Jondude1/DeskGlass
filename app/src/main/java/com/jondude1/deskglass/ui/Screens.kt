package com.jondude1.deskglass.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.BuildConfig
import com.jondude1.deskglass.MainViewModel

enum class Screen { Home, Calendar, Notes, Checklists, Chat, Settings }

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        GlassButton("‹  Home", onBack)
        Text(title, color = Glass.Ink, fontSize = 34.sp, fontWeight = FontWeight.Light)
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
