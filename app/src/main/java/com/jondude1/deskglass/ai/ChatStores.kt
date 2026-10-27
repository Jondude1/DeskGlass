package com.jondude1.deskglass.ai

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** role is "user", "assistant", or "action" (a small note that Claude changed something; never sent to the API). */
data class ChatMessage(val role: String, val text: String, val ts: Long = System.currentTimeMillis())

/** The running conversation, saved on the tablet so it's still there tomorrow. */
class ChatStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "chat.json")
    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun add(m: ChatMessage) {
        _messages.value = (_messages.value + m).takeLast(300)
        persist()
    }

    fun clear() {
        _messages.value = emptyList()
        persist()
    }

    private fun load(): List<ChatMessage> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                ChatMessage(o.getString("role"), o.getString("text"), o.optLong("ts", 0L))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        val a = JSONArray()
        for (m in _messages.value) {
            a.put(JSONObject().put("role", m.role).put("text", m.text).put("ts", m.ts))
        }
        val tmp = File(file.parentFile, "chat.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}

/** Short facts Claude chose to keep about you. They survive "New chat". */
class MemoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "memory.json")
    private val _facts = MutableStateFlow(load())
    val facts: StateFlow<List<String>> = _facts.asStateFlow()

    fun add(fact: String) {
        val clean = fact.trim()
        if (clean.isEmpty() || _facts.value.any { it.equals(clean, ignoreCase = true) }) return
        _facts.value = (_facts.value + clean).takeLast(100)
        persist()
    }

    fun clear() {
        _facts.value = emptyList()
        persist()
    }

    private fun load(): List<String> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { a.getString(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        val a = JSONArray()
        _facts.value.forEach { a.put(it) }
        val tmp = File(file.parentFile, "memory.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}
