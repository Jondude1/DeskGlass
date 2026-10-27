package com.jondude1.deskglass.ai

import com.jondude1.deskglass.data.Checklist
import com.jondude1.deskglass.data.ChecklistStore
import com.jondude1.deskglass.data.Event
import com.jondude1.deskglass.data.EventStore
import com.jondude1.deskglass.data.Note
import com.jondude1.deskglass.data.NoteStore
import com.jondude1.deskglass.data.Prefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private class ToolResult(val text: String, val isError: Boolean = false, val chip: String? = null)

/**
 * Talks to Claude and lets it act on the tablet: add events, fill checklists, save notes, and
 * remember facts. Actions happen right away; each one leaves a small note in the chat.
 */
class ChatEngine(
    private val prefs: Prefs,
    private val chat: ChatStore,
    private val memory: MemoryStore,
    private val events: EventStore,
    private val checklists: ChecklistStore,
    private val notes: NoteStore,
) {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    suspend fun send(text: String) = withContext(Dispatchers.IO) {
        val key = prefs.apiKey.trim()
        chat.add(ChatMessage("user", text))

        val messages = historyForApi()
        var reply = ""
        for (round in 0 until 6) {
            val body = JSONObject()
                .put("model", prefs.chatModel.trim())
                .put("max_tokens", 1024)
                .put("system", systemPrompt())
                .put("messages", messages)
                .put("tools", tools())
            val resp = ClaudeApi.call(key, body)
            val content = resp.getJSONArray("content")

            val said = StringBuilder()
            val toolUses = mutableListOf<JSONObject>()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                when (block.getString("type")) {
                    "text" -> said.append(block.getString("text"))
                    "tool_use" -> toolUses.add(block)
                }
            }
            if (toolUses.isEmpty() || resp.optString("stop_reason") != "tool_use") {
                reply = said.toString()
                break
            }

            messages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            for (t in toolUses) {
                val r = runTool(t.getString("name"), t.optJSONObject("input") ?: JSONObject())
                val item = JSONObject()
                    .put("type", "tool_result")
                    .put("tool_use_id", t.getString("id"))
                    .put("content", r.text)
                if (r.isError) item.put("is_error", true)
                results.put(item)
                if (r.chip != null) chat.add(ChatMessage("action", r.chip))
            }
            messages.put(JSONObject().put("role", "user").put("content", results))
        }
        chat.add(ChatMessage("assistant", reply.trim().ifEmpty { "Done." }))
    }

    private fun historyForApi(): JSONArray {
        val turns = chat.messages.value
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(40)
            .dropWhile { it.role != "user" }
        val a = JSONArray()
        for (m in turns) a.put(JSONObject().put("role", m.role).put("content", m.text))
        return a
    }

    private fun systemPrompt(): String {
        val now = ZonedDateTime.now()
        val name = prefs.userName.trim().ifEmpty { "the user" }
        val facts = memory.facts.value
        val remembered = if (facts.isEmpty()) "Nothing yet." else facts.joinToString("\n") { "- $it" }
        return """
            You are Claude, chatting with $name on his DeskGlass desk tablet (a Samsung Galaxy Tab S6 Lite) during breaks at work.
            Be warm, curious and genuinely useful. This is a small screen and a quick break, so keep replies short unless he wants depth. Write plain text: no markdown headings, tables or bold markers.

            You can act on his calendar, checklists and notes with your tools. When he asks for something like that, just do it right away without asking for confirmation, then say briefly what you did. Dates you pass to tools must be YYYY-MM-DD and times HH:MM in 24-hour form. If a request is ambiguous (which day, which list), make the most sensible choice and mention it.
            Use remember to keep lasting facts about him that would help in future chats (preferences, people, projects), not passing trivia.

            Right now it is ${now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a (zzzz)", Locale.ENGLISH))}.

            What you remember about him:
            $remembered
        """.trimIndent()
    }

    // ---- Tools ----

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)

    private fun tool(name: String, desc: String, props: JSONObject, required: List<String>) = JSONObject()
        .put("name", name)
        .put("description", desc)
        .put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))
        )

    private fun tools(): JSONArray = JSONArray()
        .put(
            tool(
                "add_event",
                "Add an event to Jon's DeskGlass calendar. Omit start_time for an all-day event.",
                JSONObject()
                    .put("title", str("Short event title"))
                    .put("date", str("Date, YYYY-MM-DD"))
                    .put("start_time", str("Start time, HH:MM 24-hour. Omit for all day."))
                    .put("end_time", str("End time, HH:MM 24-hour. Optional."))
                    .put("note", str("Optional note")),
                listOf("title", "date")
            )
        )
        .put(
            tool(
                "list_events",
                "List calendar events between two dates, inclusive.",
                JSONObject()
                    .put("start_date", str("First day, YYYY-MM-DD"))
                    .put("end_date", str("Last day, YYYY-MM-DD")),
                listOf("start_date", "end_date")
            )
        )
        .put(
            tool(
                "add_checklist_items",
                "Add one or more items to a checklist. Creates the list if it doesn't exist yet.",
                JSONObject()
                    .put("list_name", str("Name of the checklist, e.g. Work or Home"))
                    .put(
                        "items",
                        JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject().put("type", "string"))
                            .put("description", "The items to add")
                    ),
                listOf("list_name", "items")
            )
        )
        .put(
            tool(
                "get_checklists",
                "Read checklists and their items. Give list_name for one list, or leave it out for all.",
                JSONObject().put("list_name", str("Optional checklist name")),
                emptyList()
            )
        )
        .put(
            tool(
                "set_item_done",
                "Check off (or uncheck) a checklist item, matched by its text.",
                JSONObject()
                    .put("list_name", str("Checklist name"))
                    .put("item_text", str("Text of the item, or part of it"))
                    .put("done", JSONObject().put("type", "boolean").put("description", "true to check off, false to uncheck. Default true.")),
                listOf("list_name", "item_text")
            )
        )
        .put(
            tool(
                "create_note",
                "Save a new typed note.",
                JSONObject().put("title", str("Note title")).put("body", str("Note text")),
                listOf("title", "body")
            )
        )
        .put(
            tool(
                "remember",
                "Save a lasting fact about Jon so you remember it in future chats.",
                JSONObject().put("fact", str("One short fact, written plainly")),
                listOf("fact")
            )
        )

    private fun runTool(name: String, input: JSONObject): ToolResult = try {
        when (name) {
            "add_event" -> addEvent(input)
            "list_events" -> listEvents(input)
            "add_checklist_items" -> addChecklistItems(input)
            "get_checklists" -> getChecklists(input)
            "set_item_done" -> setItemDone(input)
            "create_note" -> createNote(input)
            "remember" -> remember(input)
            else -> ToolResult("Unknown tool: $name", isError = true)
        }
    } catch (e: Exception) {
        ToolResult("That didn't work: ${e.message}", isError = true)
    }

    private fun parseTime(s: String): LocalTime? = s.trim().takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) }

    private fun addEvent(i: JSONObject): ToolResult {
        val title = i.optString("title").trim()
        if (title.isEmpty()) return ToolResult("The title is empty.", isError = true)
        val date = LocalDate.parse(i.getString("date").trim())
        val start = parseTime(i.optString("start_time"))
        val end = if (start != null) parseTime(i.optString("end_time")) else null
        events.upsert(Event(UUID.randomUUID().toString(), title, date, start, end, i.optString("note").trim()))
        val when_ = date.format(dayFmt) + (start?.let { " at " + it.format(timeFmt) } ?: "")
        return ToolResult("Added \"$title\" on $when_.", chip = "Added to calendar: $title, $when_")
    }

    private fun listEvents(i: JSONObject): ToolResult {
        val from = LocalDate.parse(i.getString("start_date").trim())
        val to = LocalDate.parse(i.getString("end_date").trim())
        val found = events.events.value.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        if (found.isEmpty()) return ToolResult("No events in that range.")
        return ToolResult(
            found.joinToString("\n") { e ->
                val time = when {
                    e.start == null -> "all day"
                    e.end == null -> e.start.format(timeFmt)
                    else -> e.start.format(timeFmt) + "-" + e.end.format(timeFmt)
                }
                "${e.date} (${e.date.format(dayFmt)}) $time: ${e.title}" + if (e.note.isNotBlank()) " [${e.note}]" else ""
            }
        )
    }

    private fun findList(name: String): Checklist? {
        val all = checklists.lists.value
        return all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: all.firstOrNull { it.name.contains(name.trim(), ignoreCase = true) }
    }

    private fun addChecklistItems(i: JSONObject): ToolResult {
        val listName = i.getString("list_name").trim()
        val arr = i.getJSONArray("items")
        val items = (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) return ToolResult("There were no items to add.", isError = true)
        val existing = findList(listName)
        val id = existing?.id ?: checklists.addList(listName)
        val shown = existing?.name ?: listName
        items.forEach { checklists.addItem(id, it) }
        val what = if (items.size == 1) items[0] else "${items.size} items"
        return ToolResult("Added ${items.joinToString(", ")} to $shown.", chip = "Added to $shown: $what")
    }

    private fun getChecklists(i: JSONObject): ToolResult {
        val wanted = i.optString("list_name").trim()
        val lists = if (wanted.isEmpty()) checklists.lists.value else listOfNotNull(findList(wanted))
        if (lists.isEmpty()) return ToolResult("No matching checklist.")
        return ToolResult(
            lists.joinToString("\n\n") { l ->
                val rows = if (l.items.isEmpty()) "  (empty)" else l.items.joinToString("\n") { (if (it.done) "  [x] " else "  [ ] ") + it.text }
                "${l.name}:\n$rows"
            }
        )
    }

    private fun setItemDone(i: JSONObject): ToolResult {
        val list = findList(i.getString("list_name")) ?: return ToolResult("No checklist with that name.", isError = true)
        val text = i.getString("item_text").trim()
        val done = i.optBoolean("done", true)
        val item = list.items.firstOrNull { it.text.equals(text, ignoreCase = true) }
            ?: list.items.firstOrNull { it.text.contains(text, ignoreCase = true) }
            ?: return ToolResult("No item matching \"$text\" in ${list.name}.", isError = true)
        if (item.done != done) checklists.toggleItem(list.id, item.id)
        val verb = if (done) "Checked off" else "Unchecked"
        return ToolResult("$verb \"${item.text}\".", chip = "$verb: ${item.text}")
    }

    private fun createNote(i: JSONObject): ToolResult {
        val title = i.optString("title").trim()
        notes.upsert(Note(UUID.randomUUID().toString(), title, i.optString("body")))
        return ToolResult("Saved the note.", chip = "Saved note: ${title.ifEmpty { "Untitled" }}")
    }

    private fun remember(i: JSONObject): ToolResult {
        val fact = i.getString("fact").trim()
        memory.add(fact)
        return ToolResult("Remembered.", chip = "Remembered: $fact")
    }
}
