package com.jondude1.deskglass.ai

import com.jondude1.deskglass.data.Prefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class QuickEvent(
    val title: String,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val note: String,
)

data class Transcription(val title: String, val text: String)

data class Organized(val title: String, val tags: List<String>, val body: String)

/**
 * One-shot AI jobs: each is a single question to Claude with a single answer.
 * Quick jobs use the fast model; handwriting reading uses the chat model for accuracy.
 */
class AiTasks(private val prefs: Prefs) {

    private suspend fun run(model: String, system: String, content: JSONArray, maxTokens: Int = 1024): String =
        withContext(Dispatchers.IO) {
            val key = prefs.apiKey.trim()
            if (key.isEmpty()) throw IllegalStateException("Add your API key in Settings first.")
            val body = JSONObject()
                .put("model", model.trim())
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            val resp = ClaudeApi.call(key, body)
            val blocks = resp.getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until blocks.length()) {
                val b = blocks.getJSONObject(i)
                if (b.getString("type") == "text") sb.append(b.getString("text"))
            }
            sb.toString().trim()
        }

    private fun text(t: String) = JSONObject().put("type", "text").put("text", t)

    private fun one(t: String) = JSONArray().put(text(t))

    private fun jsonObject(s: String): JSONObject {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        require(a >= 0 && b > a) { "Claude's answer wasn't in the form I expected. Try again." }
        return JSONObject(s.substring(a, b + 1))
    }

    private fun jsonArray(s: String): JSONArray {
        val a = s.indexOf('[')
        val b = s.lastIndexOf(']')
        require(a >= 0 && b > a) { "Claude's answer wasn't in the form I expected. Try again." }
        return JSONArray(s.substring(a, b + 1))
    }

    private fun JSONObject.strOrNull(k: String): String? =
        if (isNull(k)) null else optString(k).trim().ifEmpty { null }

    private fun strings(a: JSONArray): List<String> =
        (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } }

    private fun nowText(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd, h:mm a", Locale.ENGLISH))

    // ---- Calendar quick-add ----

    suspend fun quickEvent(input: String): QuickEvent {
        val system = """
            You turn a short sentence into a calendar event. Right now it is ${nowText()}.
            Reply with ONLY a JSON object: {"title": string, "date": "YYYY-MM-DD", "start_time": "HH:MM" or null, "end_time": "HH:MM" or null, "note": string or null}.
            Resolve relative dates ("Thursday", "tomorrow", "next week") from today; a weekday name means the next one coming up.
            Keep the title short and leave the date and time words out of it. Use 24-hour times; if no time is given use null.
        """.trimIndent()
        val o = jsonObject(run(prefs.fastModel, system, one(input), 300))
        val title = o.strOrNull("title") ?: throw IllegalArgumentException("I couldn't find an event title in that.")
        val date = LocalDate.parse(o.strOrNull("date") ?: throw IllegalArgumentException("I couldn't tell which day."))
        val start = o.strOrNull("start_time")?.let { LocalTime.parse(it) }
        val end = if (start != null) o.strOrNull("end_time")?.let { LocalTime.parse(it) } else null
        return QuickEvent(title, date, start, end, o.strOrNull("note") ?: "")
    }

    // ---- Notes ----

    suspend fun transcribe(pngBase64: String): Transcription {
        val system = """
            You transcribe handwriting from an image of a handwritten note.
            Reply with ONLY a JSON object: {"title": a short title of at most 6 words, "text": the full transcription}.
            Keep line breaks and list structure. If a word is unclear, give your best reading. If there is no handwriting, use an empty text.
        """.trimIndent()
        val image = JSONObject()
            .put("type", "image")
            .put(
                "source",
                JSONObject().put("type", "base64").put("media_type", "image/png").put("data", pngBase64)
            )
        val content = JSONArray().put(image).put(text("Transcribe this note."))
        val o = jsonObject(run(prefs.chatModel, system, content, 4096))
        return Transcription(o.optString("title").trim(), o.optString("text").trim())
    }

    suspend fun organize(title: String, body: String): Organized {
        val system = """
            You tidy personal notes. Fix spelling and grammar, keep the author's meaning and voice, and add simple structure
            (short paragraphs, and "-" bullet lists where they help). Never invent facts.
            Reply with ONLY a JSON object: {"title": a short title, "tags": up to 4 short lowercase tags, "body": the tidied note as plain text}.
        """.trimIndent()
        val o = jsonObject(run(prefs.fastModel, system, one("Title: $title\n\n$body"), 4096))
        return Organized(
            title = o.optString("title").trim(),
            tags = strings(o.optJSONArray("tags") ?: JSONArray()).map { it.lowercase().removePrefix("#") }.take(4),
            body = o.optString("body").trim().ifEmpty { body },
        )
    }

    suspend fun summarize(body: String): String =
        run(prefs.fastModel, "Summarize the note in at most 3 short sentences. Plain text only, no preamble.", one(body), 400)

    suspend fun actionItems(body: String): List<String> {
        val system = """
            Extract the concrete action items or to-dos from the note.
            Reply with ONLY a JSON array of short strings, each starting with a verb. If there are none, reply [].
        """.trimIndent()
        return strings(jsonArray(run(prefs.fastModel, system, one(body), 800)))
    }

    // ---- Checklists ----

    suspend fun breakDown(input: String): List<String> {
        val system = """
            You turn a messy list or a project description into a clear checklist.
            Reply with ONLY a JSON array of short, concrete, actionable items in a sensible order (at most 25).
            Merge duplicates, fix typos, and split compound items into separate ones.
        """.trimIndent()
        return strings(jsonArray(run(prefs.fastModel, system, one(input), 1500)))
    }

    // ---- Clock line ----

    suspend fun idleLine(recent: List<String>): String {
        val system = """
            You write one short line for a desk clock display, read in a glance during a work day.
            Vary the kind: a fun fact, a small observation about the day or season, a gentle nudge, a bit of dry wit.
            Maximum 14 words. Warm, never cheesy, no emoji, no quotation marks, no attribution. Reply with just the line.
        """.trimIndent()
        val avoid = if (recent.isEmpty()) "" else "\n\nDon't repeat or echo these recent lines:\n" + recent.joinToString("\n") { "- $it" }
        val raw = run(prefs.fastModel, system, one("It is ${nowText()}.$avoid"), 80)
        return raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().trim('"', '“', '”')
    }
}
