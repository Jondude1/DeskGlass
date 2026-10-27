package com.jondude1.deskglass.ui

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.MainViewModel
import com.jondude1.deskglass.sim.CivSim
import com.jondude1.deskglass.sim.GH
import com.jondude1.deskglass.sim.GW
import com.jondude1.deskglass.sim.T
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

/** The village, drawn live. It also runs the simulation while it is on screen. */
@Composable
fun CivCanvas(sim: CivSim, modifier: Modifier = Modifier, cover: Boolean = true, labels: Boolean = false) {
    val art = remember { CivArt() }
    var frame by remember { mutableLongStateOf(0L) }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 30, 40)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val bubblePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(225, 255, 255, 255) }
    }
    val namePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(230, 255, 255, 255)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }

    LaunchedEffect(sim) {
        var last = 0L
        var lastDraw = 0L
        while (true) {
            val n = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) sim.tick(((n - last) / 1_000_000_000.0).toFloat())
            last = n
            if (n - lastDraw > 45_000_000L) {
                lastDraw = n
                frame = n
            }
        }
    }
    DisposableEffect(sim) { onDispose { sim.save() } }
    LaunchedEffect(sim) {
        while (true) {
            delay(30_000L)
            sim.save()
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = frame
        drawWorld(sim, art, cover, labels, textPaint, bubblePaint, namePaint)
    }
}

private fun DrawScope.img(
    image: ImageBitmap, x: Float, y: Float, w: Float, h: Float,
) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(max(1, w.toInt()), max(1, h.toInt())),
        filterQuality = FilterQuality.None,
    )
}

private fun DrawScope.drawWorld(
    sim: CivSim, art: CivArt, cover: Boolean, labels: Boolean,
    textPaint: Paint, bubblePaint: Paint, namePaint: Paint,
) {
    val ts = if (cover) max(size.width / GW, size.height / GH) else min(size.width / GW, size.height / GH)
    val ox = (size.width - GW * ts) / 2f
    val oy = (size.height - GH * ts) / 2f
    val ps = ts / 16f
    val water = ((sim.time * 1.5).toInt()) and 1
    val fireFrame = ((sim.time * 4).toInt()) and 1

    drawRect(Color.Black)
    // ground
    for (y in 0 until GH) for (x in 0 until GW) {
        val i = y * GW + x
        val tile = if (sim.tiles[i] == T.WATER) art.water[water] else art.grass[(x * 7 + y * 13) and 3]
        img(tile, ox + x * ts, oy + y * ts, ts + 1f, ts + 1f)
    }
    // things standing on the ground, row by row so lower ones overlap higher ones
    for (y in 0 until GH) {
        for (x in 0 until GW) {
            val i = y * GW + x
            val sp: ImageBitmap? = when (sim.tiles[i]) {
                T.FOREST -> art.tree
                T.ROCK -> art.rock
                T.BUSH -> if (sim.amt[i] > 0) art.bush else art.bushEmpty
                else -> null
            }
            if (sp != null) {
                val w = sp.width * ps
                val h = sp.height * ps
                img(sp, ox + (x + 0.5f) * ts - w / 2f, oy + (y + 1f) * ts - h, w, h)
            }
        }
        for (b in sim.buildings) {
            if (b.y != y) continue
            val sp = when (b.type) {
                "hut" -> art.hut
                "farm" -> art.farm
                else -> art.fire[fireFrame]
            }
            val w = sp.width * ps
            val h = sp.height * ps
            img(sp, ox + (b.x + 0.5f) * ts - w / 2f, oy + (b.y + 1f) * ts - h, w, h)
        }
    }
    // villagers
    val sorted = sim.villagers.sortedBy { it.y }
    for (v in sorted) {
        val walking = v.phase == "walking"
        val sp = art.person(v.colorIdx, if (walking) (((v.x + v.y) * 3).toInt() and 1) else 0)
        val w = sp.width * ps
        val h = sp.height * ps
        val px = ox + v.x * ts - w / 2f
        val py = oy + (v.y + 0.45f) * ts - h
        if (v.phase == "sleeping") {
            drawIntoCanvas { c ->
                namePaint.textSize = ts * 0.4f
                c.nativeCanvas.drawText("z z", px + w * 0.2f, py - ts * 0.1f, namePaint)
            }
        } else {
            img(sp, px, py, w, h)
        }
    }
    // evening and night
    val dark = sim.darkness()
    if (dark > 0f) {
        drawRect(Color(0xFF0A1030).copy(alpha = dark))
        for (b in sim.buildings) {
            if (b.type != "campfire") continue
            val c = Offset(ox + (b.x + 0.5f) * ts, oy + (b.y + 0.5f) * ts)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFB347).copy(alpha = dark * 0.9f), Color.Transparent),
                    center = c,
                    radius = ts * 5f,
                ),
                radius = ts * 5f,
                center = c,
            )
        }
    }
    // speech bubbles and names
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        for (v in sorted) {
            val cx = ox + v.x * ts
            val top = oy + (v.y + 0.45f) * ts - 10f * ps
            if (labels) {
                namePaint.textSize = ts * 0.34f
                namePaint.textAlign = Paint.Align.CENTER
                nc.drawText(v.name, cx, oy + (v.y + 0.45f) * ts + ts * 0.4f, namePaint)
            }
            if (v.say.isNotEmpty()) {
                textPaint.textSize = ts * 0.36f
                val txt = if (v.say.length > 46) v.say.take(45) + "…" else v.say
                val tw = textPaint.measureText(txt)
                val r = RectF(cx - tw / 2f - ts * 0.25f, top - ts * 0.85f, cx + tw / 2f + ts * 0.25f, top - ts * 0.15f)
                nc.drawRoundRect(r, ts * 0.2f, ts * 0.2f, bubblePaint)
                textPaint.textAlign = Paint.Align.CENTER
                nc.drawText(txt, cx, r.centerY() + ts * 0.13f, textPaint)
            }
        }
    }
}

/** The full-screen village: watch, read what everyone is thinking, and speak to the village. */
@Composable
fun VillageScreen(vm: MainViewModel, onBack: () -> Unit) {
    val sim = vm.civ
    var tick by remember { mutableLongStateOf(0L) }
    var whisper by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            tick++
        }
    }
    @Suppress("UNUSED_VARIABLE") val t = tick

    Column(Modifier.fillMaxSize()) {
        BackBar("Village", onBack, trailing = {
            GlassButton(if (confirmReset) "Tap again to start over" else "Start over", {
                if (confirmReset) {
                    sim.restart()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            })
        })
        Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            GlassCard(Modifier.weight(0.68f).fillMaxHeight(), corner = 24.dp) {
                CivCanvas(sim, Modifier.fillMaxSize(), cover = false, labels = true)
            }
            Column(Modifier.weight(0.32f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Day ${sim.day()} · ${sim.partOfDay()} · ${sim.villagers.size} villagers",
                    color = Glass.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    "Wood ${sim.wood}   Stone ${sim.stone}   Food ${sim.food}",
                    color = Glass.InkDim, fontSize = 16.sp,
                )
                Text("Brain: ${sim.brain}", color = Glass.InkFaint, fontSize = 14.sp)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (v in sim.villagers) {
                        Column {
                            Text(
                                "${v.name} · ${if (v.action.isEmpty()) "thinking" else v.action.replace('_', ' ')}",
                                color = Glass.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            )
                            if (v.thought.isNotBlank()) {
                                Text("“${v.thought}”", color = Glass.InkDim, fontSize = 14.sp)
                            }
                            Text(
                                "hunger ${v.hunger.toInt()} · energy ${v.energy.toInt()}",
                                color = Glass.InkFaint, fontSize = 12.sp,
                            )
                        }
                    }
                    Text("NEWS", color = Glass.InkFaint, fontSize = 13.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 8.dp))
                    for (line in sim.log.takeLast(8).reversed()) {
                        Text(line, color = Glass.InkDim, fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = whisper,
                    onValueChange = { whisper = it },
                    singleLine = true,
                    placeholder = { Text("Speak to the village…", color = Glass.InkFaint) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        sim.whisper(whisper)
                        whisper = ""
                    }),
                    colors = editorFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.align(Alignment.End)) {
                    GlassButton("Say it", {
                        sim.whisper(whisper)
                        whisper = ""
                    }, filled = true)
                }
            }
        }
    }
}
