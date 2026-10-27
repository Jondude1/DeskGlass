package com.jondude1.deskglass.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.jondude1.deskglass.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** Mon–Fri, 8:00 to 4:59 PM. The tablet is lively inside this window and quiet outside it. */
fun isActiveHours(t: LocalDateTime): Boolean =
    t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY && t.hour in 8..16

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000L - (System.currentTimeMillis() % 1000L))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun DeskGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
fun DeskGlassApp(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val now = rememberNow()
    val active by remember { derivedStateOf { isActiveHours(now.value) } }

    // Really lower the backlight outside active hours, not just darken the picture.
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(active) {
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = if (active) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                0.08f
            }
            w.attributes = lp
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Play) Screen.Games else Screen.Home
    }

    val dim by animateFloatAsState(if (active) 0f else 0.5f, animationSpec = tween(2500), label = "dim")

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(animate = active)

        // The self-playing game, behind the idle home screen only.
        val games by vm.games.collectAsState()
        val narration by vm.narration.collectAsState()
        val wallpaper = games.firstOrNull()
        val wallpaperId = wallpaper?.id
        val showWallpaper = screen == Screen.Home && active && wallpaper != null
        AnimatedVisibility(
            visible = showWallpaper,
            enter = fadeIn(tween(2000)),
            exit = fadeOut(tween(400)),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (wallpaper != null) GameView(vm.gameStore, wallpaper, "bot", interactive = false, onNarrate = vm::onNarrate, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(0xAA05070D)))
            }
        }

        Crossfade(targetState = screen, modifier = Modifier.imePadding(), animationSpec = tween(350), label = "screen") { s ->
            when (s) {
                Screen.Home -> HomeScreen(vm, now, active) { screen = it }
                Screen.Calendar -> CalendarScreen(vm, now) { screen = Screen.Home }
                Screen.Notes -> NotesScreen(vm) { screen = Screen.Home }
                Screen.Checklists -> ChecklistsScreen(vm) { screen = Screen.Home }
                Screen.Chat -> ChatScreen(vm, { screen = Screen.Home }) { screen = Screen.Settings }
                Screen.Games -> GamesScreen(vm, { screen = Screen.Home }) { screen = Screen.Play }
                Screen.Play -> PlayScreen(vm) { screen = Screen.Games }
                Screen.Settings -> SettingsScreen(vm) { screen = Screen.Home }
            }
        }

        if (showWallpaper) {
            if (narration != null) {
                Text(
                    narration!!,
                    color = Glass.InkFaint,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 44.dp),
                )
            }
            GlassButton(
                "Play this",
                {
                    wallpaperId?.let { vm.startPlay(it, "human") }
                    screen = Screen.Play
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 40.dp),
            )
        }

        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }

        UpdateBanner(vm, Modifier.align(Alignment.TopCenter))
    }
}
