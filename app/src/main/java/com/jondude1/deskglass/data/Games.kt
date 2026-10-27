package com.jondude1.deskglass.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class GameInfo(
    val id: String,
    val title: String,
    val blurb: String,
    val createdAt: Long,
    val engineFile: String,
)

/**
 * Games are written by Claude and built with Godot on GitHub, then published as releases of the
 * DeskGlass repo (marked as pre-releases so the app updater ignores them). This keeps every game
 * it has downloaded, so old ones can be replayed.
 */
class GameStore(context: Context) {
    private val root = File(context.filesDir, "godot-games").apply { mkdirs() }
    private val engineDir = File(root, "_engine").apply { mkdirs() }

    private val _games = MutableStateFlow(load())
    val games: StateFlow<List<GameInfo>> = _games.asStateFlow()

    private fun load(): List<GameInfo> {
        val dirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("game-") } ?: return emptyList()
        return dirs.mapNotNull { d ->
            try {
                val m = JSONObject(File(d, "meta.json").readText())
                if (!File(d, "index.pck").exists()) return@mapNotNull null
                GameInfo(
                    id = d.name,
                    title = m.optString("title", "Untitled game"),
                    blurb = m.optString("blurb"),
                    createdAt = m.optLong("made") * 1000L,
                    engineFile = m.optString("engineFile", "engine.wasm"),
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    /** The file a game page asks for, or null. The engine is shared between all games. */
    fun resolve(game: GameInfo, path: String): File? {
        if (path.contains("..") || path.startsWith("/")) return null
        val f = if (path == "index.wasm") File(engineDir, game.engineFile) else File(root, "${game.id}/$path")
        return f.takeIf { it.isFile }
    }

    /** Downloads games published since the last check. Returns how many are new. */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val body = Http.get(RELEASES, mapOf("Accept" to "application/vnd.github+json"))
        val releases = JSONArray(body)
        var added = 0
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            val tag = r.getString("tag_name")
            if (!tag.startsWith("game-")) continue
            if (File(root, "$tag/meta.json").exists()) continue

            val assets = HashMap<String, String>()
            val arr = r.optJSONArray("assets") ?: continue
            for (k in 0 until arr.length()) {
                val a = arr.getJSONObject(k)
                assets[a.getString("name")] = a.getString("browser_download_url")
            }
            val metaText = Http.get(assets["meta.json"] ?: continue)
            val meta = JSONObject(metaText)
            val files = meta.getJSONArray("files")
            val engineName = meta.optString("engineFile", "engine.wasm")

            val tmp = File(root, ".tmp-$tag")
            tmp.deleteRecursively()
            tmp.mkdirs()
            for (k in 0 until files.length()) {
                val name = files.getString(k)
                if (name.contains("/") || name.contains("..")) continue
                val url = assets[name] ?: error("Missing $name in $tag")
                if (name == engineName) {
                    val dest = File(engineDir, engineName)
                    if (!dest.exists()) download(url, dest)
                } else {
                    download(url, File(tmp, name))
                }
            }
            File(tmp, "meta.json").writeText(metaText)
            if (!tmp.renameTo(File(root, tag))) error("Couldn't save $tag")
            added++
        }
        if (added > 0) _games.value = load()
        added
    }

    private fun download(url: String, dest: File) {
        val part = File(dest.parentFile, dest.name + ".part")
        val c = Http.open(url)
        try {
            c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        if (!part.renameTo(dest)) error("Couldn't save ${dest.name}")
    }

    companion object {
        private const val RELEASES = "https://api.github.com/repos/Jondude1/DeskGlass/releases?per_page=12"
    }
}
