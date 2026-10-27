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
import androidx.compose.foundation.layout.widthIn
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
    var villageFull by rememberSaveable { mutableStateOf(false) }
    // A fullscreen village never dims or sleeps, even after hours.
    val awake = active || (screen == Screen.Village && villageFull)

    // Really lower the backlight outside active hours, not just darken the picture.
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(awake) {
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = if (awake) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                0.08f
            }
            w.attributes = lp
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        if (screen == Screen.Village && villageFull) villageFull = false else screen = Screen.Home
    }

    val dim by animateFloatAsState(if (awake) 0f else 0.5f, animationSpec = tween(2500), label = "dim")

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(animate = awake)

        // The village, running behind the idle home screen only.
        val narration by vm.narration.collectAsState()
        val showWallpaper = screen == Screen.Home && active
        AnimatedVisibility(
            visible = showWallpaper,
            enter = fadeIn(tween(2000)),
            exit = fadeOut(tween(400)),
        ) {
            Box(Modifier.fillMaxSize()) {
                CivCanvas(vm.civ, Modifier.fillMaxSize(), cover = true)
                Box(Modifier.fillMaxSize().background(Color(0x9905070D)))
            }
        }

        Crossfade(targetState = screen, modifier = Modifier.imePadding(), animationSpec = tween(350), label = "screen") { s ->
            when (s) {
                Screen.Home -> HomeScreen(vm, now, active) { screen = it }
                Screen.Calendar -> CalendarScreen(vm, now) { screen = Screen.Home }
                Screen.Notes -> NotesScreen(vm) { screen = Screen.Home }
                Screen.Checklists -> ChecklistsScreen(vm) { screen = Screen.Home }
                Screen.Chat -> ChatScreen(vm, { screen = Screen.Home }) { screen = Screen.Settings }
                Screen.Village -> VillageScreen(vm, villageFull, { villageFull = it }) { screen = Screen.Home }
                Screen.Settings -> SettingsScreen(vm) { screen = Screen.Home }
            }
        }

        if (showWallpaper) {
            val cfg = androidx.compose.ui.platform.LocalConfiguration.current
            val sUi = minOf(cfg.screenWidthDp / 1000f, cfg.screenHeightDp / 600f).coerceIn(0.72f, 1.2f)
            val dens = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * sUi, dens.fontScale)
            ) {
            if (narration != null) {
                Text(
                    narration!!,
                    color = Color(0xFFF2F4F8),
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 24.dp)
                        .widthIn(max = minOf(380f, cfg.screenWidthDp * 0.46f / sUi).dp)
                        .background(Color(0xCC05070D), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            GlassButton(
                "Open village",
                { screen = Screen.Village },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 24.dp),
            )
            }
        }

        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }

        UpdateBanner(vm, Modifier.align(Alignment.TopCenter))
    }
}
