package com.jondude1.deskglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import com.jondude1.deskglass.MainViewModel
import com.jondude1.deskglass.data.Event
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun formatTime(t: LocalTime): String = t.format(TIME_FMT)

fun eventTimeLabel(e: Event): String = when {
    e.start == null -> "All day"
    e.end == null -> formatTime(e.start)
    else -> "${formatTime(e.start)} – ${formatTime(e.end)}"
}

/** Month grid. Days with events get a small dot; pass [onSelect] to make days tappable. */
@Composable
fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    eventDays: Set<Int>,
    modifier: Modifier = Modifier,
    cell: Dp = 54.dp,
    onSelect: ((LocalDate) -> Unit)? = null,
) {
    val offset = month.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val days = month.lengthOfMonth()
    val weeks = (offset + days + 6) / 7
    val circle = cell - 10.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            for (d in listOf("S", "M", "T", "W", "T", "F", "S")) {
                Box(Modifier.size(width = cell, height = 30.dp), contentAlignment = Alignment.Center) {
                    Text(d, color = Glass.InkFaint, fontSize = 16.sp, letterSpacing = 2.sp)
                }
            }
        }
        for (week in 0 until weeks) {
            Row {
                for (col in 0 until 7) {
                    val day = week * 7 + col - offset + 1
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val date = month.atDay(day)
                            val isToday = date == today
                            val isSelected = date == selected
                            val fill = when {
                                isToday -> Glass.Accent.copy(alpha = 0.9f)
                                isSelected -> Color.White.copy(alpha = 0.20f)
                                else -> Color.Transparent
                            }
                            val textColor = if (isToday) Color(0xFF0B1020) else Glass.Ink
                            var tap = Modifier.size(circle).clip(CircleShape).background(fill)
                            if (onSelect != null) {
                                tap = tap.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(date) }
                            }
                            Box(tap, contentAlignment = Alignment.Center) {
                                Text(
                                    day.toString(),
                                    color = textColor,
                                    fontSize = 21.sp,
                                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Light,
                                )
                                if (day in eventDays) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isToday) textColor else Glass.Accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(vm: MainViewModel, now: State<LocalDateTime>, onBack: () -> Unit) {
    val events by vm.events.collectAsState()
    val today = now.value.toLocalDate()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf<Event?>(null) }

    val eventDays = remember(events, month) {
        events.filter { YearMonth.from(it.date) == month }.map { it.date.dayOfMonth }.toSet()
    }
    val dayEvents = events.filter { it.date == selected }

    Column(Modifier.fillMaxSize()) {
        BackBar("Calendar", onBack)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GlassCard(Modifier.fillMaxHeight()) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.width(54.dp * 7),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        GlassButton("‹", { month = month.minusMonths(1) })
                        Text(
                            "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                            color = Glass.Ink,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Light,
                        )
                        GlassButton("›", { month = month.plusMonths(1) })
                    }
                    Spacer(Modifier.height(18.dp))
                    CalendarGrid(
                        month = month,
                        today = today,
                        selected = selected,
                        eventDays = eventDays,
                        onSelect = { selected = it },
                    )
                    Spacer(Modifier.height(18.dp))
                    GlassButton("Today", {
                        month = YearMonth.from(today)
                        selected = today
                    })
                }
            }

            GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(32.dp).fillMaxSize()) {
                    Text(
                        selected.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
                        color = Glass.InkFaint,
                        fontSize = 16.sp,
                        letterSpacing = 4.sp,
                    )
                    Text(
                        "${selected.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selected.dayOfMonth}",
                        color = Glass.Ink,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (dayEvents.isEmpty()) {
                            Text("Nothing scheduled.", color = Glass.InkFaint, fontSize = 20.sp)
                        }
                        for (e in dayEvents) {
                            EventRow(e) { editing = e }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        "+  Add event",
                        {
                            editing = Event(UUID.randomUUID().toString(), "", selected)
                        },
                        filled = true,
                    )
                }
            }
        }
    }

    editing?.let { e ->
        EventEditor(
            initial = e,
            isNew = events.none { it.id == e.id },
            onSave = {
                vm.saveEvent(it)
                selected = it.date
                month = YearMonth.from(it.date)
                editing = null
            },
            onDelete = {
                vm.deleteEvent(e.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EventRow(e: Event, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(eventTimeLabel(e), color = Glass.Accent, fontSize = 18.sp, modifier = Modifier.width(190.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = Glass.Ink, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.note.isNotBlank()) {
                Text(e.note, color = Glass.InkFaint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EventEditor(
    initial: Event,
    isNew: Boolean,
    onSave: (Event) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var note by remember { mutableStateOf(initial.note) }
    var allDay by remember { mutableStateOf(initial.start == null) }
    var start by remember { mutableStateOf(initial.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(initial.end ?: (initial.start ?: LocalTime.of(9, 0)).plusHours(1)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(32.dp)
        Column(
            Modifier
                .fillMaxWidth(0.62f)
                .widthIn(max = 700.dp)
                .clip(shape)
                .background(Color(0xFF151A33))
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (isNew) "New event" else "Edit event",
                color = Glass.Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                "${initial.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, " +
                    "${initial.date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${initial.date.dayOfMonth}",
                color = Glass.InkDim,
                fontSize = 18.sp,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                colors = editorFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Switch(
                    checked = allDay,
                    onCheckedChange = { allDay = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Glass.Accent),
                )
                Text("All day", color = Glass.Ink, fontSize = 20.sp)
            }
            if (!allDay) {
                TimeStepper("Starts", start) { new ->
                    // Keep the end the same distance after the start.
                    val gap = java.time.Duration.between(start, end)
                    start = new
                    end = new.plus(if (gap.isNegative || gap.isZero) java.time.Duration.ofHours(1) else gap)
                }
                TimeStepper("Ends", end) { end = it }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                colors = editorFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isNew) {
                    GlassButton("Delete", onDelete)
                    Spacer(Modifier.weight(1f))
                }
                GlassButton("Cancel", onDismiss)
                GlassButton(
                    "Save",
                    {
                        if (title.isNotBlank()) {
                            val s = if (allDay) null else start
                            val e = if (allDay || !end.isAfter(start)) null else end
                            onSave(initial.copy(title = title.trim(), start = s, end = e, note = note.trim()))
                        }
                    },
                    filled = true,
                )
            }
        }
    }
}

/** Hour and minute steppers (5-minute steps) with a tappable AM/PM. */
@Composable
private fun TimeStepper(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, color = Glass.InkDim, fontSize = 18.sp, modifier = Modifier.width(80.dp))
        StepBtn("−") { onChange(time.minusHours(1)) }
        Text(hour12.toString(), color = Glass.Ink, fontSize = 28.sp, modifier = Modifier.width(40.dp))
        StepBtn("+") { onChange(time.plusHours(1)) }
        Text(":", color = Glass.InkDim, fontSize = 28.sp)
        StepBtn("−") { onChange(time.withMinute((time.minute + 55) % 60)) }
        Text(time.minute.toString().padStart(2, '0'), color = Glass.Ink, fontSize = 28.sp, modifier = Modifier.width(44.dp))
        StepBtn("+") { onChange(time.withMinute((time.minute + 5) % 60)) }
        GlassButton(if (time.hour < 12) "AM" else "PM", { onChange(time.plusHours(12)) })
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Glass.Ink, fontSize = 22.sp)
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Glass.Ink,
    unfocusedTextColor = Glass.Ink,
    cursorColor = Glass.Accent,
    focusedBorderColor = Color.White.copy(alpha = 0.7f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
    focusedLabelColor = Glass.InkDim,
    unfocusedLabelColor = Glass.InkFaint,
)
