package com.jondude1.deskglass.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A calendar entry. No [start] means an all-day event. */
data class Event(
    val id: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val note: String = "",
)

/** DeskGlass's own calendar, kept in a small JSON file, separate from Outlook or Google. */
class EventStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "events.json")
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    fun upsert(event: Event) {
        _events.value = sorted(_events.value.filter { it.id != event.id } + event)
        persist()
    }

    fun delete(id: String) {
        _events.value = _events.value.filter { it.id != id }
        persist()
    }

    private fun sorted(list: List<Event>) =
        list.sortedWith(compareBy<Event>({ it.date }, { it.start ?: LocalTime.MIN }, { it.title }))

    private fun load(): List<Event> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val a = JSONArray(file.readText())
            sorted((0 until a.length()).map { parse(a.getJSONObject(it)) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parse(o: JSONObject) = Event(
        id = o.getString("id"),
        title = o.getString("title"),
        date = LocalDate.parse(o.getString("date")),
        start = if (o.isNull("start")) null else LocalTime.parse(o.getString("start")),
        end = if (o.isNull("end")) null else LocalTime.parse(o.getString("end")),
        note = o.optString("note", ""),
    )

    private fun persist() {
        val a = JSONArray()
        for (e in _events.value) {
            a.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("date", e.date.toString())
                    .put("start", e.start?.toString() ?: JSONObject.NULL)
                    .put("end", e.end?.toString() ?: JSONObject.NULL)
                    .put("note", e.note)
            )
        }
        val tmp = File(file.parentFile, "events.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}
