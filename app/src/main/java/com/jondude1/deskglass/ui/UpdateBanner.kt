package com.jondude1.deskglass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.MainViewModel
import com.jondude1.deskglass.UpdateUi

/** Slim glass bar at the top: "Update available — Update / Later". */
@Composable
fun UpdateBanner(vm: MainViewModel, modifier: Modifier = Modifier) {
    val state by vm.update.collectAsState()
    val status by vm.updateStatus.collectAsState()

    AnimatedVisibility(
        visible = state !is UpdateUi.Idle,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        GlassCard(
            Modifier.padding(top = 18.dp).widthIn(max = 760.dp).fillMaxWidth(0.8f),
            corner = 28.dp,
        ) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 14.dp)) {
                when (val s = state) {
                    is UpdateUi.Available -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            status ?: "Update available — v${s.info.versionName}",
                            color = Glass.Ink,
                            fontSize = 19.sp,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton("Later", { vm.dismissUpdate() })
                        GlassButton("Update", { vm.startUpdate() }, filled = true)
                    }
                    is UpdateUi.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Downloading v${s.info.versionName}…", color = Glass.Ink, fontSize = 19.sp)
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Glass.Accent,
                            trackColor = Color.White.copy(alpha = 0.15f),
                        )
                    }
                    UpdateUi.Idle -> Unit
                }
            }
        }
    }
}
