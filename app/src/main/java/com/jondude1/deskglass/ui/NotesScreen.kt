package com.jondude1.deskglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.deskglass.MainViewModel
import com.jondude1.deskglass.data.InkStroke
import com.jondude1.deskglass.data.Note
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val NOTE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

private val PEN_COLORS = listOf(
    Color(0xFFF4F6FF), // ink white
    Color(0xFF9DBEFF), // blue
    Color(0xFFFF9DC6), // pink
    Color(0xFFFFD36B), // gold
    Color(0xFF8FF0C8), // mint
)

// Pen widths as a fraction of the page width.
private val PEN_WIDTHS = listOf(0.0022f, 0.0042f, 0.0085f)

@Composable
fun NotesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val notes by vm.notes.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val selected = notes.firstOrNull { it.id == selectedId }
    val shown = remember(notes, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) notes else notes.filter { n ->
            n.title.lowercase().contains(q) || n.body.lowercase().contains(q) || n.tags.any { it.contains(q) }
        }
    }

    // Don't leave blank notes lying around when you switch away or leave the screen.
    LaunchedEffect(selectedId) { vm.pruneEmptyNotes(selectedId) }
    DisposableEffect(Unit) { onDispose { vm.pruneEmptyNotes(null) } }

    Column(Modifier.fillMaxSize()) {
        BackBar("Notes", onBack)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GlassCard(Modifier.width(340.dp).fillMaxHeight()) {
                Column(Modifier.padding(22.dp).fillMaxSize()) {
                    if (notes.size > 2) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Search notes", color = Glass.InkFaint) },
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (shown.isEmpty()) {
                            Text(
                                if (notes.isEmpty()) "No notes yet." else "No matches.",
                                color = Glass.InkFaint,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        for (n in shown) {
                            NoteRow(n, n.id == selected?.id) { selectedId = n.id }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GlassButton(
                        "+  New note",
                        {
                            val fresh = Note(id = UUID.randomUUID().toString())
                            vm.saveNote(fresh)
                            selectedId = fresh.id
                        },
                        Modifier.fillMaxWidth(),
                        filled = true,
                    )
                }
            }

            GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                if (selected == null) {
                    Text(
                        "Pick a note, or start a new one.",
                        color = Glass.InkFaint,
                        fontSize = 24.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    key(selected.id) {
                        NoteEditor(
                            vm = vm,
                            note = selected,
                            onSave = { vm.saveNote(it) },
                            onDelete = {
                                vm.deleteNote(selected.id)
                                selectedId = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, isSelected: Boolean, onClick: () -> Unit) {
    val preview = when {
        note.body.isNotBlank() -> note.body.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        note.strokes.isNotEmpty() -> "Handwritten"
        else -> "Empty note"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            note.title.ifBlank { "Untitled" },
            color = Glass.Ink,
            fontSize = 21.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(preview, color = Glass.InkDim, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (note.tags.isNotEmpty()) {
            Text(
                note.tags.joinToString("  ") { "#$it" },
                color = Glass.Accent,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).format(NOTE_DATE),
            color = Glass.InkFaint,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun NoteEditor(vm: MainViewModel, note: Note, onSave: (Note) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    val strokes = remember { mutableStateListOf<InkStroke>().apply { addAll(note.strokes) } }
    var writeMode by remember { mutableStateOf(note.body.isBlank() && note.strokes.isNotEmpty()) }
    var penColor by remember { mutableStateOf(PEN_COLORS[0]) }
    var penWidth by remember { mutableStateOf(PEN_WIDTHS[1]) }
    var fingerDraws by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(note.tags) }

    val hasKey by vm.hasApiKey.collectAsState()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<Triple<String, String, List<String>>?>(null) }

    fun save() {
        onSave(
            note.copy(
                title = title.trim(),
                body = body,
                strokes = strokes.toList(),
                tags = tags,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun runAi(job: suspend () -> Unit) {
        if (working) return
        scope.launch {
            working = true
            status = null
            try {
                job()
            } catch (e: Exception) {
                status = vm.errorMessage(e)
            } finally {
                working = false
            }
        }
    }

    // Typed text saves a moment after you stop typing.
    LaunchedEffect(title, body) {
        if (title.trim() != note.title || body != note.body) {
            delay(500)
            save()
        }
    }

    Column(Modifier.padding(28.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                placeholder = { Text("Title", color = Glass.InkFaint) },
                textStyle = LocalTextStyle.current.copy(fontSize = 26.sp),
                colors = editorFieldColors(),
                modifier = Modifier.weight(1f),
            )
            GlassButton("Type", { writeMode = false }, filled = !writeMode)
            GlassButton("Write", { writeMode = true }, filled = writeMode)
        }

        if (tags.isNotEmpty()) {
            Text(tags.joinToString("   ") { "#$it" }, color = Glass.Accent, fontSize = 17.sp)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!writeMode) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Start typing…", color = Glass.InkFaint) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, lineHeight = 32.sp),
                    colors = editorFieldColors(),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                InkPage(
                    strokes = strokes,
                    penColor = penColor,
                    penWidth = penWidth,
                    fingerDraws = fingerDraws,
                    onStroke = {
                        strokes.add(it)
                        confirmClear = false
                        save()
                    },
                )
            }
        }

        if (writeMode) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in PEN_COLORS) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (c == penColor) 3.dp else 1.dp, if (c == penColor) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { penColor = c }
                    )
                }
                Spacer(Modifier.width(8.dp))
                PEN_WIDTHS.forEachIndexed { i, w ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (w == penWidth) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { penWidth = w },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size((6 + i * 5).dp).clip(CircleShape).background(Glass.Ink))
                    }
                }
                Spacer(Modifier.weight(1f))
                GlassButton("Finger", { fingerDraws = !fingerDraws }, filled = fingerDraws)
                GlassButton("Undo", {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.lastIndex)
                        save()
                    }
                })
                GlassButton(if (confirmClear) "Tap again" else "Clear", {
                    if (confirmClear) {
                        strokes.clear()
                        confirmClear = false
                        save()
                    } else {
                        confirmClear = true
                    }
                })
            }
        }

        if (hasKey) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!writeMode && body.isNotBlank()) {
                    GlassButton(if (working) "Working…" else "Organize", {
                        runAi {
                            val r = vm.ai.organize(title, body)
                            undo = Triple(title, body, tags)
                            if (title.isBlank()) title = r.title
                            body = r.body
                            tags = r.tags
                            save()
                            status = "Tidied up, with a title and tags."
                        }
                    })
                    GlassButton("Summarize", {
                        runAi {
                            val summary = vm.ai.summarize(body)
                            undo = Triple(title, body, tags)
                            body = "Summary: $summary\n\n$body"
                            save()
                            status = "Summary added at the top."
                        }
                    })
                    GlassButton("Actions to To do", {
                        runAi {
                            val items = vm.ai.actionItems(body)
                            if (items.isEmpty()) {
                                status = "No action items found in this note."
                            } else {
                                vm.addToChecklist("To do", items)
                                status = "Added ${items.size} item${if (items.size == 1) "" else "s"} to your To do list."
                            }
                        }
                    })
                }
                if (writeMode && strokes.isNotEmpty()) {
                    GlassButton(if (working) "Reading…" else "Convert to text", {
                        runAi {
                            val r = vm.ai.transcribe(renderInkBase64(strokes.toList()))
                            if (r.text.isBlank()) {
                                status = "I couldn't find any handwriting to read."
                            } else {
                                undo = Triple(title, body, tags)
                                if (title.isBlank()) title = r.title
                                body = if (body.isBlank()) r.text else body + "\n\n" + r.text
                                writeMode = false
                                save()
                                status = "Converted to text. Your handwriting is still under Write."
                            }
                        }
                    })
                }
                if (undo != null) {
                    GlassButton("Undo AI edit", {
                        undo?.let { (t, b, g) ->
                            title = t
                            body = b
                            tags = g
                            save()
                        }
                        undo = null
                        status = null
                    })
                }
                status?.let { Text(it, color = Glass.InkDim, fontSize = 17.sp, modifier = Modifier.weight(1f)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(if (confirmDelete) "Tap again to delete" else "Delete note", {
                if (confirmDelete) onDelete() else confirmDelete = true
            })
        }
    }
}

/** A ruled page you draw on. By default only the S Pen draws, so your palm can rest on the glass. */
@Composable
private fun InkPage(
    strokes: List<InkStroke>,
    penColor: Color,
    penWidth: Float,
    fingerDraws: Boolean,
    onStroke: (InkStroke) -> Unit,
) {
    val live = remember { mutableStateListOf<Offset>() }
    val shape = RoundedCornerShape(24.dp)

    Canvas(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .pointerInput(penColor, penWidth, fingerDraws) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val allowed = down.type == PointerType.Stylus ||
                        down.type == PointerType.Mouse ||
                        (fingerDraws && down.type == PointerType.Touch)
                    if (!allowed) return@awaitEachGesture
                    down.consume()
                    live.clear()
                    live.add(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        live.add(change.position)
                        change.consume()
                    }
                    val w = size.width.toFloat()
                    if (w > 0f && live.isNotEmpty()) {
                        onStroke(
                            InkStroke(
                                color = penColor.toArgb(),
                                width = penWidth,
                                points = live.map { Pair(it.x / w, it.y / w) },
                            )
                        )
                    }
                    live.clear()
                }
            }
    ) {
        val w = size.width
        // Faint writing lines.
        val gap = 52.dp.toPx()
        var y = gap
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            y += gap
        }
        for (s in strokes) {
            drawInk(Color(s.color), s.width * w, s.points.map { Offset(it.first * w, it.second * w) })
        }
        if (live.isNotEmpty()) drawInk(penColor, penWidth * w, live.toList())
    }
}

private fun DrawScope.drawInk(color: Color, widthPx: Float, pts: List<Offset>) {
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(color, widthPx / 2f, pts[0])
        return
    }
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val prev = pts[i - 1]
        val cur = pts[i]
        // Curve through midpoints so handwriting looks smooth instead of jagged.
        path.quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
    }
    path.lineTo(pts.last().x, pts.last().y)
    drawPath(path, color, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
