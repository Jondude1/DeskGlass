package com.jondude1.deskglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.jondude1.deskglass.data.Event
import java.time.Duration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.WeatherState
import com.jondude1.deskglass.data.WeatherKind
import com.jondude1.deskglass.data.WeatherNow
import com.jondude1.deskglass.data.weatherKind
import com.jondude1.deskglass.data.weatherText
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- Clock

@Composable
fun ClockPanel(now: State<LocalDateTime>, idleLine: String?) {
    val t = now.value
    val hour12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    val minute = t.minute.toString().padStart(2, '0')
    val greeting = when {
        t.hour < 12 -> "GOOD MORNING"
        t.hour < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(greeting, color = Glass.InkFaint, fontSize = 20.sp, letterSpacing = 6.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$hour12:$minute",
                color = Glass.Ink,
                fontSize = 190.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-4).sp,
            )
            Text(
                text = if (t.hour < 12) "AM" else "PM",
                color = Glass.InkDim,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 14.dp, bottom = 50.dp),
            )
        }
        Text(
            text = t.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
            color = Glass.InkDim,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
        )
        if (!idleLine.isNullOrBlank()) {
            Text(
                text = idleLine,
                color = Glass.InkFaint,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 26.dp).widthIn(max = 900.dp),
            )
        }
    }
}

// -------------------------------------------------------------- Weather

@Composable
fun WeatherPanel(state: WeatherState) {
    GlassCard(
        Modifier
            .fillMaxWidth(0.78f)
            .widthIn(max = 820.dp)
            .heightIn(min = 320.dp)
    ) {
        when (state) {
            is WeatherState.Ready -> WeatherContent(state.now)
            WeatherState.NeedsCity -> Note("Add your city in Settings to see the weather.")
            WeatherState.Loading -> Note("Loading weather…")
            is WeatherState.Failed -> Note("Weather isn't available right now.")
        }
    }
}

@Composable
private fun BoxScope.Note(text: String) {
    Text(
        text,
        color = Glass.InkDim,
        fontSize = 26.sp,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(40.dp),
    )
}

@Composable
private fun BoxScope.WeatherContent(w: WeatherNow) {
    Row(
        modifier = Modifier.align(Alignment.Center).padding(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        WeatherIcon(weatherKind(w.code, w.isDay), Modifier.size(190.dp))
        Column {
            Text(w.label.uppercase(), color = Glass.InkFaint, fontSize = 18.sp, letterSpacing = 4.sp)
            Text(
                "${w.tempF}°",
                color = Glass.Ink,
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraLight,
            )
            Text(weatherText(w.code), color = Glass.Ink, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(10.dp))
            Text(
                "H ${w.hiF}°   L ${w.loF}°   ·   Feels ${w.feelsF}°   ·   Wind ${w.windMph} mph",
                color = Glass.InkDim,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
fun WeatherIcon(kind: WeatherKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val sunColor = Color(0xFFFFD36B)
        val cloudColor = Color(0xFFEAF0FF)
        when (kind) {
            WeatherKind.Clear -> drawSun(Offset(s * 0.5f, s * 0.5f), s * 0.22f, sunColor)
            WeatherKind.Night -> drawMoon(Offset(s * 0.5f, s * 0.5f), s * 0.26f)
            WeatherKind.PartlyCloudy -> {
                drawSun(Offset(s * 0.36f, s * 0.36f), s * 0.17f, sunColor)
                drawCloud(s * 0.12f, s * 0.30f, s * 0.80f, cloudColor)
            }
            WeatherKind.Cloudy -> drawCloud(s * 0.08f, s * 0.20f, s * 0.84f, cloudColor)
            WeatherKind.Fog -> {
                drawCloud(s * 0.10f, s * 0.10f, s * 0.80f, cloudColor.copy(alpha = 0.85f))
                for (i in 0..2) {
                    val y = s * (0.66f + i * 0.11f)
                    drawLine(
                        cloudColor.copy(alpha = 0.7f),
                        Offset(s * 0.22f, y),
                        Offset(s * 0.78f, y),
                        strokeWidth = s * 0.04f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            WeatherKind.Rain -> {
                drawCloud(s * 0.08f, s * 0.08f, s * 0.84f, cloudColor)
                for (i in 0..2) {
                    val x = s * (0.34f + i * 0.16f)
                    drawLine(
                        Color(0xFF7DB8FF),
                        Offset(x, s * 0.64f),
                        Offset(x - s * 0.05f, s * 0.82f),
                        strokeWidth = s * 0.045f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            WeatherKind.Snow -> {
                drawCloud(s * 0.08f, s * 0.08f, s * 0.84f, cloudColor)
                for (i in 0..2) {
                    drawCircle(Color.White, s * 0.03f, Offset(s * (0.34f + i * 0.16f), s * (0.70f + (i % 2) * 0.09f)))
                }
            }
            WeatherKind.Storm -> {
                drawCloud(s * 0.08f, s * 0.06f, s * 0.84f, Color(0xFFC9D2EE))
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.58f)
                    lineTo(s * 0.40f, s * 0.78f)
                    lineTo(s * 0.50f, s * 0.78f)
                    lineTo(s * 0.44f, s * 0.95f)
                    lineTo(s * 0.64f, s * 0.70f)
                    lineTo(s * 0.53f, s * 0.70f)
                    close()
                }
                drawPath(bolt, sunColor)
            }
        }
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, color: Color) {
    drawCircle(color, r, c)
    for (i in 0 until 8) {
        val a = i * PI / 4
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(
            color,
            c + dir * (r * 1.45f),
            c + dir * (r * 1.9f),
            strokeWidth = r * 0.2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(c: Offset, r: Float) {
    drawCircle(Color(0xFFE9EEFF), r, c)
    drawCircle(Color(0xFF1B2250), r * 0.85f, Offset(c.x + r * 0.55f, c.y - r * 0.25f))
}

private fun DrawScope.drawCloud(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 0.6f
    drawCircle(color, w * 0.22f, Offset(x + w * 0.30f, y + h * 0.62f))
    drawCircle(color, w * 0.28f, Offset(x + w * 0.55f, y + h * 0.48f))
    drawCircle(color, w * 0.20f, Offset(x + w * 0.76f, y + h * 0.68f))
    drawRoundRect(
        color,
        topLeft = Offset(x + w * 0.10f, y + h * 0.62f),
        size = Size(w * 0.80f, h * 0.38f),
        cornerRadius = CornerRadius(h * 0.19f),
    )
}

// ------------------------------------------------------------- Calendar

@Composable
fun CalendarPanel(now: State<LocalDateTime>, events: List<Event>) {
    val t = now.value
    val today = t.toLocalDate()
    val month = YearMonth.from(today)
    GlassCard(
        Modifier
            .fillMaxWidth(0.78f)
            .widthIn(max = 880.dp)
            .heightIn(min = 340.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center).padding(40.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(300.dp)) {
                Text(
                    today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
                    color = Glass.InkFaint,
                    fontSize = 18.sp,
                    letterSpacing = 5.sp,
                )
                Text(
                    today.dayOfMonth.toString(),
                    color = Glass.Ink,
                    fontSize = 130.sp,
                    fontWeight = FontWeight.ExtraLight,
                )
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    color = Glass.InkDim,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(18.dp))
                TodayList(events, t)
            }
            val eventDays = events.filter { YearMonth.from(it.date) == month }.map { it.date.dayOfMonth }.toSet()
            CalendarGrid(month = month, today = today, selected = today, eventDays = eventDays)
        }
    }
}

@Composable
private fun TodayList(events: List<Event>, now: LocalDateTime) {
    val todays = events.filter { it.date == now.toLocalDate() }
    if (todays.isEmpty()) {
        Text("Nothing scheduled", color = Glass.InkFaint, fontSize = 20.sp)
        return
    }
    val nowTime = now.toLocalTime()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in todays.take(4)) {
            val start = e.start
            // A quiet highlight for anything starting in the next half hour, or happening now.
            val soon = start != null &&
                Duration.between(nowTime, start).toMinutes() in 0..30
            val over = start != null && !(e.end ?: start.plusHours(1)).isAfter(nowTime)
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (soon) Glass.Accent.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (start == null) "All day" else formatTime(start),
                    color = if (soon) Glass.Accent else Glass.InkFaint,
                    fontSize = 16.sp,
                )
                Text(
                    e.title,
                    color = if (over) Glass.InkFaint else Glass.Ink,
                    fontSize = 20.sp,
                    fontWeight = if (soon) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (todays.size > 4) {
            Text("+${todays.size - 4} more", color = Glass.InkFaint, fontSize = 16.sp)
        }
    }
}
