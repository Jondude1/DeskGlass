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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

private fun cellHash(x: Int, y: Int): Int = ((x * 73856093) xor (y * 19349663)) and 0x7fffffff

private fun DrawScope.shadow(cx: Float, cy: Float, w: Float, h: Float) {
    drawOval(Color.Black.copy(alpha = 0.22f), topLeft = Offset(cx - w / 2f, cy - h / 2f), size = Size(w, h))
}

private fun DrawScope.drawWorld(
    sim: CivSim, art: CivArt, cover: Boolean, labels: Boolean,
    textPaint: Paint, bubblePaint: Paint, namePaint: Paint,
) {
    val ts = if (cover) max(size.width / GW, size.height / GH) else min(size.width / GW, size.height / GH)
    val ox = (size.width - GW * ts) / 2f
    val oy = (size.height - GH * ts) / 2f
    val ps = ts / 16f
    val t = sim.time.toFloat()
    val water = ((sim.time * 1.5).toInt()) and 1
    val fireFrame = ((sim.time * 4).toInt()) and 1
    val dark = sim.darkness()

    drawRect(Color(0xFF0B1220))

    fun isWater(x: Int, y: Int): Boolean =
        x in 0 until GW && y in 0 until GH && sim.tiles[y * GW + x] == T.WATER

    // ground: water, sandy shores, flowery grass
    for (y in 0 until GH) for (x in 0 until GW) {
        val i = y * GW + x
        val h = cellHash(x, y)
        val tile = when {
            sim.tiles[i] == T.WATER -> art.water[water]
            isWater(x + 1, y) || isWater(x - 1, y) || isWater(x, y + 1) || isWater(x, y - 1) -> art.sand[h and 1]
            else -> art.grass[h and 3]
        }
        img(tile, ox + x * ts, oy + y * ts, ts + 1f, ts + 1f)
        if (sim.tiles[i] == T.GRASS && h % 9 == 0) {
            val fc = when ((h / 9) % 3) {
                0 -> Color(0xFFFFFFFF)
                1 -> Color(0xFFFFE066)
                else -> Color(0xFFFF8FB8)
            }
            val fx = ox + x * ts + ((h shr 4) % 11 + 2) * ps
            val fy = oy + y * ts + ((h shr 8) % 10 + 3) * ps
            drawRect(fc, Offset(fx, fy), Size(ps * 1.6f, ps * 1.6f))
        }
    }

    // things standing on the ground, row by row so lower ones overlap higher ones
    for (y in 0 until GH) {
        for (x in 0 until GW) {
            val i = y * GW + x
            val h = cellHash(x, y)
            var sp: ImageBitmap? = null
            var shadowW = 0f
            var swaying = false
            when (sim.tiles[i]) {
                T.FOREST -> {
                    if (sim.amt[i] <= 2) {
                        sp = art.stump
                    } else {
                        sp = if (h % 3 == 0) art.pine else art.oak
                        shadowW = 10f
                        swaying = true
                    }
                }
                T.ROCK -> { sp = art.rock; shadowW = 11f }
                T.BUSH -> { sp = if (sim.amt[i] > 0) art.bush else art.bushEmpty; shadowW = 9f }
            }
            if (sp != null) {
                val w = sp.width * ps
                val hh = sp.height * ps
                val cx = ox + (x + 0.5f) * ts
                val baseY = oy + (y + 0.95f) * ts
                if (shadowW > 0f) shadow(cx, baseY, shadowW * ps, 3.5f * ps)
                val sway = if (swaying) kotlin.math.sin(t * 1.3f + (h % 7)) * ps * 0.6f else 0f
                img(sp, cx - w / 2f + sway, baseY - hh, w, hh)
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
            val hh = sp.height * ps
            val cx = ox + (b.x + 0.5f) * ts
            val baseY = oy + (b.y + 0.97f) * ts
            if (b.type != "farm") shadow(cx, baseY, w * 0.9f, 3.5f * ps)
            img(sp, cx - w / 2f, baseY - hh, w, hh)
            // lit windows at night
            if (b.type == "hut" && dark > 0.1f) {
                val glow = Color(0xFFFFD27A).copy(alpha = min(1f, dark * 1.6f))
                drawRect(glow, Offset(cx - w * 0.36f, baseY - hh * 0.5f), Size(ps * 2f, ps * 2.4f))
                drawRect(glow, Offset(cx + w * 0.18f, baseY - hh * 0.5f), Size(ps * 2f, ps * 2.4f))
            }
        }
    }

    // smoke from campfires
    for (b in sim.buildings) {
        if (b.type != "campfire") continue
        val fx = ox + (b.x + 0.5f) * ts
        val fy = oy + (b.y + 0.4f) * ts
        for (k in 0 until 4) {
            val p = ((t * 0.45f + k * 0.25f) % 1f)
            drawCircle(
                Color(0xFFC8C8D0).copy(alpha = 0.32f * (1f - p)),
                radius = ts * (0.08f + 0.2f * p),
                center = Offset(fx + kotlin.math.sin(p * 6f + k) * ts * 0.14f, fy - p * ts * 1.8f),
            )
        }
    }

    // villagers
    val sorted = sim.villagers.sortedBy { it.y }
    for (v in sorted) {
        val walking = v.phase == "walking"
        val sp = art.person(v.colorIdx, if (walking) (((v.x + v.y) * 3).toInt() and 1) else 0)
        val w = sp.width * ps
        val h = sp.height * ps
        val bob = when (v.phase) {
            "walking" -> kotlin.math.abs(kotlin.math.sin(t * 9f + v.id)) * ps * 0.8f
            "working" -> kotlin.math.abs(kotlin.math.sin(t * 6f + v.id)) * ps * 1.2f
            else -> 0f
        }
        val px = ox + v.x * ts - w / 2f
        val feetY = oy + (v.y + 0.45f) * ts
        shadow(ox + v.x * ts, feetY, w * 0.9f, 2.6f * ps)
        if (v.phase == "sleeping") {
            drawIntoCanvas { c ->
                namePaint.textSize = ts * 0.4f
                namePaint.textAlign = Paint.Align.CENTER
                c.nativeCanvas.drawText("z z", ox + v.x * ts, feetY - h * 0.9f, namePaint)
            }
        } else {
            img(sp, px, feetY - h - bob, w, h)
        }
        if (v.phase == "working" && v.action.startsWith("gather_")) {
            val col = when (v.action) {
                "gather_wood" -> Color(0xFFB07A3C)
                "gather_stone" -> Color(0xFFB8BEC8)
                else -> Color(0xFFE0405A)
            }
            for (k in 0 until 3) {
                val p = ((t * 1.6f + k * 0.33f + v.id * 0.17f) % 1f)
                drawRect(
                    col.copy(alpha = 1f - p),
                    Offset(ox + v.x * ts + (k - 1) * ps * 3f + v.facing * ps * 3f, feetY - h * 0.7f - p * ps * 8f),
                    Size(ps * 1.6f, ps * 1.6f),
                )
            }
        }
    }

    // drifting cloud shadows in daylight
    val cloudAlpha = 0.10f * (1f - dark / 0.55f)
    if (cloudAlpha > 0.005f) {
        val span = GW * ts + 14f * ts
        for (k in 0 until 4) {
            val cx = ox - 7f * ts + ((t * 0.9f * ts * 0.12f + k * span / 4f) % span)
            val cy = oy + ((GH * (0.18f + 0.22f * k)) % GH) * ts
            drawOval(
                Color.Black.copy(alpha = cloudAlpha),
                topLeft = Offset(cx - ts * 5f, cy - ts * 1.6f),
                size = Size(ts * 10f, ts * 3.2f),
            )
        }
    }

    // evening and night
    if (dark > 0f) {
        drawRect(Color(0xFF0A1030).copy(alpha = dark))
        for (b in sim.buildings) {
            if (b.type != "campfire") continue
            val c = Offset(ox + (b.x + 0.5f) * ts, oy + (b.y + 0.5f) * ts)
            val flick = 1f + 0.06f * kotlin.math.sin(t * 11f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFB347).copy(alpha = dark * 0.95f), Color.Transparent),
                    center = c,
                    radius = ts * 5.5f * flick,
                ),
                radius = ts * 5.5f * flick,
                center = c,
            )
        }
        for (i in 0 until 16) {
            val fx = ox + ((cellHash(i, 3) % GW) + 0.5f + kotlin.math.sin(t * 0.6f + i) * 1.4f) * ts
            val fy = oy + ((cellHash(i, 9) % GH) + 0.5f + kotlin.math.cos(t * 0.5f + i * 1.7f) * 1.2f) * ts
            val tw = (0.5f + 0.5f * kotlin.math.sin(t * 2.2f + i * 2.3f)).coerceIn(0f, 1f)
            drawCircle(Color(0xFFE8F27A).copy(alpha = 0.25f * tw * (dark / 0.55f)), radius = ts * 0.22f, center = Offset(fx, fy))
            drawCircle(Color(0xFFF8FFB0).copy(alpha = 0.9f * tw * (dark / 0.55f)), radius = ts * 0.06f, center = Offset(fx, fy))
        }
    }

    // rain
    if (sim.raining()) {
        drawRect(Color(0xFF203050).copy(alpha = 0.16f))
        for (i in 0 until 90) {
            val rx = ((cellHash(i, 5) % 1000) / 1000f) * size.width
            val ry = ((((cellHash(i, 11) % 1000) / 1000f) * size.height + t * ts * 14f) % size.height)
            drawLine(
                Color(0xFFB8D4F0).copy(alpha = 0.5f),
                Offset(rx, ry),
                Offset(rx - ts * 0.15f, ry + ts * 0.5f),
                strokeWidth = max(1f, ps),
            )
        }
    }

    // soft vignette
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = max(size.width, size.height) * 0.72f,
        )
    )

    // speech bubbles and names
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        for (v in sorted) {
            val cx = ox + v.x * ts
            val top = oy + (v.y + 0.45f) * ts - 12f * ps
            if (labels) {
                namePaint.textSize = ts * 0.34f
                namePaint.textAlign = Paint.Align.CENTER
                nc.drawText(v.name, cx, oy + (v.y + 0.45f) * ts + ts * 0.42f, namePaint)
            }
            if (v.say.isNotEmpty()) {
                textPaint.textSize = ts * 0.36f
                val txt = if (v.say.length > 46) v.say.take(45) + "…" else v.say
                val tw = textPaint.measureText(txt)
                val r = RectF(cx - tw / 2f - ts * 0.25f, top - ts * 0.85f, cx + tw / 2f + ts * 0.25f, top - ts * 0.15f)
                nc.drawRoundRect(r, ts * 0.2f, ts * 0.2f, bubblePaint)
                val tail = android.graphics.Path().apply {
                    moveTo(cx - ts * 0.12f, r.bottom - 1f)
                    lineTo(cx + ts * 0.12f, r.bottom - 1f)
                    lineTo(cx, r.bottom + ts * 0.18f)
                    close()
                }
                nc.drawPath(tail, bubblePaint)
                textPaint.textAlign = Paint.Align.CENTER
                nc.drawText(txt, cx, r.centerY() + ts * 0.13f, textPaint)
            }
        }
    }
}

/** The full-screen village: watch, read what everyone is thinking, and speak to the village. */
@Composable
fun VillageScreen(vm: MainViewModel, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit) {
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
    val narration by vm.narration.collectAsState()

    if (fullscreen) {
        Box(Modifier.fillMaxSize()) {
            CivCanvas(sim, Modifier.fillMaxSize(), cover = true, labels = true)
            GlassButton("Exit fullscreen", { onFullscreen(false) }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
            if (narration != null) {
                Text(
                    narration!!,
                    color = Glass.InkDim,
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Village", onBack, trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Fullscreen", { onFullscreen(true) }, filled = true)
                GlassButton(if (confirmReset) "Tap again to start over" else "Start over", {
                    if (confirmReset) {
                        sim.restart()
                        confirmReset = false
                    } else {
                        confirmReset = true
                    }
                })
            }
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
                                "${v.name} the ${v.job.lowercase()} · ${if (v.action.isEmpty()) "thinking" else v.action.replace('_', ' ')}",
                                color = Glass.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            )
                            if (v.thought.isNotBlank()) {
                                Text("“${v.thought}”", color = Glass.InkDim, fontSize = 14.sp)
                            }
                            Text(
                                "feeling ${sim.mood(v)} · hunger ${v.hunger.toInt()} · energy ${v.energy.toInt()}",
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
