package com.jondude1.deskglass.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class GameInfo(
    val id: String,
    val title: String,
    val blurb: String,
    val createdAt: Long,
)

/** Every game Claude has ever written. They are never deleted, so old ones can be replayed. */
class GameStore(context: Context) {
    private val dir = File(context.filesDir, "games").apply { mkdirs() }
    private val index = File(dir, "index.json")

    private val _games = MutableStateFlow(load())
    val games: StateFlow<List<GameInfo>> = _games.asStateFlow()

    private fun load(): List<GameInfo> = try {
        if (!index.exists()) emptyList() else {
            val a = JSONArray(index.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                GameInfo(o.getString("id"), o.getString("title"), o.optString("blurb"), o.getLong("createdAt"))
            }.filter { File(dir, "${it.id}.html").exists() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveIndex(list: List<GameInfo>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("blurb", it.blurb).put("createdAt", it.createdAt))
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(index)
    }

    fun add(title: String, blurb: String, html: String): GameInfo {
        val info = GameInfo(UUID.randomUUID().toString().take(8), title, blurb, System.currentTimeMillis())
        File(dir, "${info.id}.html").writeText(html)
        val list = listOf(info) + _games.value
        saveIndex(list)
        _games.value = list
        return info
    }

    fun html(id: String): String? = File(dir, "$id.html").takeIf { it.exists() }?.readText()
}
