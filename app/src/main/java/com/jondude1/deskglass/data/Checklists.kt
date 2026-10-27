package com.jondude1.deskglass.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(val id: String, val text: String, val done: Boolean = false)

data class Checklist(val id: String, val name: String, val items: List<ChecklistItem> = emptyList())

/** Several named checklists, kept in a small JSON file on the tablet. */
class ChecklistStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "checklists.json")
    private val _lists = MutableStateFlow(load())
    val lists: StateFlow<List<Checklist>> = _lists.asStateFlow()

    private fun newId() = UUID.randomUUID().toString()

    private fun update(change: (List<Checklist>) -> List<Checklist>) {
        _lists.value = change(_lists.value)
        persist()
    }

    private fun onList(listId: String, change: (Checklist) -> Checklist) =
        update { all -> all.map { if (it.id == listId) change(it) else it } }

    fun addList(name: String): String {
        val id = newId()
        update { it + Checklist(id, name) }
        return id
    }

    fun renameList(listId: String, name: String) = onList(listId) { it.copy(name = name) }

    fun deleteList(listId: String) = update { all -> all.filter { it.id != listId } }

    fun addItem(listId: String, text: String) =
        onList(listId) { it.copy(items = it.items + ChecklistItem(newId(), text)) }

    fun toggleItem(listId: String, itemId: String) = onList(listId) { list ->
        list.copy(items = list.items.map { if (it.id == itemId) it.copy(done = !it.done) else it })
    }

    fun deleteItem(listId: String, itemId: String) =
        onList(listId) { list -> list.copy(items = list.items.filter { it.id != itemId }) }

    fun clearDone(listId: String) =
        onList(listId) { list -> list.copy(items = list.items.filter { !it.done }) }

    private fun load(): List<Checklist> = try {
        if (!file.exists()) {
            // First run: start with the two lists most people want.
            listOf(Checklist(newId(), "Work"), Checklist(newId(), "Home"))
        } else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val items = o.getJSONArray("items")
                Checklist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    items = (0 until items.length()).map { j ->
                        val it = items.getJSONObject(j)
                        ChecklistItem(it.getString("id"), it.getString("text"), it.optBoolean("done", false))
                    },
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        val a = JSONArray()
        for (l in _lists.value) {
            val items = JSONArray()
            for (i in l.items) {
                items.put(JSONObject().put("id", i.id).put("text", i.text).put("done", i.done))
            }
            a.put(JSONObject().put("id", l.id).put("name", l.name).put("items", items))
        }
        val tmp = File(file.parentFile, "checklists.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}
