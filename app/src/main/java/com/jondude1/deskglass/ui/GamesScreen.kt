package com.jondude1.deskglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GamesScreen(vm: MainViewModel, onBack: () -> Unit, onPlay: () -> Unit) {
    val games by vm.games.collectAsState()
    val status by vm.gameStatus.collectAsState()
    val fmt = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault())

    Column(Modifier.fillMaxSize()) {
        BackBar("Games", onBack, trailing = {
            GlassButton("Check for new games", { vm.checkForGames(true) }, filled = true)
        })
        if (status != null) {
            Text(
                status!!,
                color = Glass.InkDim,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
            )
        }
        if (games.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No games yet. Claude writes a new one at 8, 12 and 4 on weekdays\nand it shows up here. Tap “Check for new games”.",
                    color = Glass.InkDim,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(games, key = { it.id }) { g ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(g.title, color = Glass.Ink, fontSize = 24.sp, fontWeight = FontWeight.Light)
                                Text(fmt.format(Date(g.createdAt)), color = Glass.InkFaint, fontSize = 14.sp)
                                if (g.blurb.isNotBlank()) Text(g.blurb, color = Glass.InkDim, fontSize = 16.sp)
                            }
                            GlassButton("Watch", { vm.startPlay(g.id, "bot"); onPlay() })
                            GlassButton("Play", { vm.startPlay(g.id, "human"); onPlay() }, filled = true)
                        }
                    }
                }
            }
        }
    }
}

/** A game filling the whole screen, watched or played. */
@Composable
fun PlayScreen(vm: MainViewModel, onBack: () -> Unit) {
    val req by vm.play.collectAsState()
    val narration by vm.narration.collectAsState()
    val r = req
    val games by vm.games.collectAsState()
    val game = r?.let { req -> games.firstOrNull { it.id == req.gameId } }
    if (r == null || game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassButton("Back", onBack)
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        key(r.gameId, r.mode) {
            GameView(vm.gameStore, game, r.mode, interactive = true, onNarrate = vm::onNarrate, modifier = Modifier.fillMaxSize())
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassButton("‹  Back", { vm.clearNarration(); onBack() })
            GlassButton(
                if (r.mode == "bot") "Take over" else "Watch the bot",
                { vm.startPlay(r.gameId, if (r.mode == "bot") "human" else "bot") },
            )
        }
        if (r.mode == "bot" && narration != null) {
            Text(
                narration!!,
                color = Glass.InkDim,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }
    }
}
