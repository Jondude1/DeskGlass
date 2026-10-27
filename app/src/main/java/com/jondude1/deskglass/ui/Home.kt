package com.jondude1.deskglass.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.MainViewModel
import java.time.LocalDateTime
import kotlin.math.max
import kotlinx.coroutines.delay

private const val ROTATE_MS = 17_500L // inside the 15-20 second window
private const val PAUSE_MS = 60_000L

private enum class Panel { Clock, Weather, Calendar }

@Composable
fun HomeScreen(
    vm: MainViewModel,
    now: State<LocalDateTime>,
    active: Boolean,
    onOpen: (Screen) -> Unit,
) {
    val weather by vm.weather.collectAsState()
    val events by vm.events.collectAsState()
    val idleLine by vm.idleLine.collectAsState()
    val panels = remember { Panel.values().toList() }
    var index by remember { mutableIntStateOf(0) }
    var pausedUntil by remember { mutableLongStateOf(0L) }

    // Outside work hours the home screen settles on a calm clock instead of rotating.
    LaunchedEffect(index, pausedUntil, active) {
        if (!active) {
            index = 0
            return@LaunchedEffect
        }
        val wait = max(ROTATE_MS, pausedUntil - System.currentTimeMillis())
        delay(wait)
        index = (index + 1) % panels.size
    }

    val paused = pausedUntil > System.currentTimeMillis() && active

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // Tap to hold the current panel; tap again to let it rotate.
                    pausedUntil = if (pausedUntil > System.currentTimeMillis()) 0L
                    else System.currentTimeMillis() + PAUSE_MS
                },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = index, animationSpec = tween(1400), label = "panels") { i ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (panels[i]) {
                        Panel.Clock -> ClockPanel(now, idleLine)
                        Panel.Weather -> WeatherPanel(weather)
                        Panel.Calendar -> CalendarPanel(now, events)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageDots(
                count = panels.size,
                selected = index,
                paused = paused,
                onSelect = {
                    index = it
                    pausedUntil = System.currentTimeMillis() + PAUSE_MS
                },
            )
            Dock(onOpen)
        }
    }
}

@Composable
private fun PageDots(count: Int, selected: Int, paused: Boolean, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            Box(
                Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (i == selected) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == selected) Glass.Ink else Glass.InkFaint)
                )
            }
        }
        if (paused) {
            Text("held", color = Glass.InkFaint, fontSize = 14.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun Dock(onOpen: (Screen) -> Unit) {
    GlassCard(corner = 44.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton("Calendar", { onOpen(Screen.Calendar) })
            GlassButton("Notes", { onOpen(Screen.Notes) })
            GlassButton("Checklists", { onOpen(Screen.Checklists) })
            GlassButton("Chat", { onOpen(Screen.Chat) })
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpen(Screen.Settings) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Glass.Ink)
            }
        }
    }
}
