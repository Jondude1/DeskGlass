package com.jondude1.deskglass.data

import android.content.Context
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One pen stroke. Points are stored relative to the page width (x and y are both divided by the
 * width), and [width] is a fraction of the page width too, so ink looks the same at any size.
 */
data class InkStroke(val color: Int, val width: Float, val points: List<Pair<Float, Float>>)

data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val strokes: List<InkStroke> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && strokes.isEmpty()
}

/** Typed and handwritten notes, kept in a JSON file on the tablet. */
class NoteStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "notes.json")
    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun upsert(note: Note) {
        _notes.value = sorted(_notes.value.filter { it.id != note.id } + note)
        persist()
    }

    fun delete(id: String) {
        _notes.value = _notes.value.filter { it.id != id }
        persist()
    }

    /** Drops notes that were created but never filled in, except [keepId]. */
    fun pruneEmpty(keepId: String?) {
        val kept = _notes.value.filter { !it.isEmpty || it.id == keepId }
        if (kept.size != _notes.value.size) {
            _notes.value = kept
            persist()
        }
    }

    private fun sorted(list: List<Note>) = list.sortedByDescending { it.updatedAt }

    private fun round4(v: Float): Double = (v * 10000.0).roundToLong() / 10000.0

    private fun load(): List<Note> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val a = JSONArray(file.readText())
            sorted((0 until a.length()).map { parse(a.getJSONObject(it)) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parse(o: JSONObject): Note {
        val strokesJson = o.optJSONArray("strokes") ?: JSONArray()
        val strokes = (0 until strokesJson.length()).map { i ->
            val s = strokesJson.getJSONObject(i)
            val flat = s.getJSONArray("p")
            val pts = (0 until flat.length() / 2).map { k ->
                Pair(flat.getDouble(k * 2).toFloat(), flat.getDouble(k * 2 + 1).toFloat())
            }
            InkStroke(s.getInt("c"), s.getDouble("w").toFloat(), pts)
        }
        return Note(
            id = o.getString("id"),
            title = o.optString("title", ""),
            body = o.optString("body", ""),
            strokes = strokes,
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    private fun persist() {
        val a = JSONArray()
        for (n in _notes.value) {
            val strokes = JSONArray()
            for (s in n.strokes) {
                val flat = JSONArray()
                for (p in s.points) {
                    flat.put(round4(p.first))
                    flat.put(round4(p.second))
                }
                strokes.put(JSONObject().put("c", s.color).put("w", round4(s.width)).put("p", flat))
            }
            a.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("body", n.body)
                    .put("updatedAt", n.updatedAt)
                    .put("strokes", strokes)
            )
        }
        val tmp = File(file.parentFile, "notes.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}
