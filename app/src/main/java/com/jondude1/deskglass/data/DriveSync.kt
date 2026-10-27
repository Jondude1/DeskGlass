package com.jondude1.deskglass.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.jondude1.deskglass.ui.renderInkPng
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Copies notes into a folder the person picked, which can be a Google Drive folder (Android shows
 * Drive as a storage location when the Drive app is installed). Each note becomes a Markdown file,
 * and handwritten notes also get a PNG picture. It only writes files it created itself.
 */
class DriveSync(private val context: Context, private val prefs: Prefs) {
    private val manifestFile = File(context.filesDir, "drive-manifest.json")

    private class Entry(val md: String, val mdHash: Int, val png: String?, val pngHash: Int)

    suspend fun sync(notes: List<Note>): String = withContext(Dispatchers.IO) {
        val uri = prefs.driveFolder ?: return@withContext "No folder chosen yet."
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(uri))
            ?: throw IllegalStateException("Can't open the Drive folder. Choose it again.")
        if (!folder.canWrite()) throw IllegalStateException("DeskGlass can't write to that folder. Choose it again.")

        val manifest = loadManifest()
        val live = notes.filter { !it.isEmpty }
        val liveIds = live.map { it.id }.toSet()
        var written = 0
        var removed = 0

        for (note in live) {
            val base = fileBase(note)
            val old = manifest[note.id]

            val pngName = if (note.strokes.isNotEmpty()) "$base-handwriting.png" else null
            val mdName = "$base.md"
            val md = markdown(note, pngName)
            val mdHash = md.hashCode()
            val pngHash = if (pngName != null) note.strokes.hashCode() else 0

            if (old == null || old.md != mdName || old.mdHash != mdHash) {
                if (old != null && old.md != mdName) folder.findFile(old.md)?.delete()
                write(folder, mdName, "application/octet-stream", md.toByteArray())
                written++
            }
            if (pngName != null) {
                if (old == null || old.png != pngName || old.pngHash != pngHash) {
                    if (old?.png != null && old.png != pngName) folder.findFile(old.png)?.delete()
                    write(folder, pngName, "image/png", renderInkPng(note.strokes))
                    written++
                }
            } else if (old?.png != null) {
                folder.findFile(old.png)?.delete()
            }
            manifest[note.id] = Entry(mdName, mdHash, pngName, pngHash)
        }

        // Notes deleted on the tablet are removed from the folder too (only files we made).
        for ((id, e) in manifest.toList()) {
            if (id !in liveIds) {
                folder.findFile(e.md)?.delete()
                if (e.png != null) folder.findFile(e.png)?.delete()
                manifest.remove(id)
                removed++
            }
        }
        saveManifest(manifest)

        when {
            written == 0 && removed == 0 -> "Everything is up to date."
            else -> "Updated $written file${if (written == 1) "" else "s"}" +
                (if (removed > 0) ", removed $removed" else "") + "."
        }
    }

    private fun write(folder: DocumentFile, name: String, mime: String, bytes: ByteArray) {
        val target = folder.findFile(name) ?: folder.createFile(mime, name)
            ?: throw IllegalStateException("Couldn't create $name in that folder.")
        val out = context.contentResolver.openOutputStream(target.uri, "wt")
            ?: throw IllegalStateException("Couldn't write $name.")
        out.use { it.write(bytes) }
    }

    private fun fileBase(note: Note): String {
        val clean = note.title
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
            .ifEmpty { "Untitled" }
        return "$clean (${note.id.take(4)})"
    }

    private fun markdown(note: Note, pngName: String?): String {
        val sb = StringBuilder()
        sb.append("# ").append(note.title.ifBlank { "Untitled" }).append("\n\n")
        if (note.tags.isNotEmpty()) sb.append(note.tags.joinToString(" ") { "#$it" }).append("\n\n")
        if (note.body.isNotBlank()) sb.append(note.body.trim()).append("\n\n")
        if (pngName != null) sb.append("![handwriting](").append(pngName.replace(" ", "%20")).append(")\n")
        return sb.toString()
    }

    private fun loadManifest(): MutableMap<String, Entry> {
        val map = mutableMapOf<String, Entry>()
        try {
            if (manifestFile.exists()) {
                val o = JSONObject(manifestFile.readText())
                for (id in o.keys()) {
                    val e = o.getJSONObject(id)
                    map[id] = Entry(
                        e.getString("md"),
                        e.getInt("mdHash"),
                        if (e.isNull("png")) null else e.getString("png"),
                        e.optInt("pngHash", 0),
                    )
                }
            }
        } catch (e: Exception) {
            map.clear()
        }
        return map
    }

    private fun saveManifest(map: Map<String, Entry>) {
        val o = JSONObject()
        for ((id, e) in map) {
            o.put(
                id,
                JSONObject()
                    .put("md", e.md)
                    .put("mdHash", e.mdHash)
                    .put("png", e.png ?: JSONObject.NULL)
                    .put("pngHash", e.pngHash)
            )
        }
        manifestFile.writeText(o.toString())
    }
}
