package com.jondude1.deskglass.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A plain text file of everything that went wrong, newest first, so errors can be read and copied. */
class ErrorLog(context: Context) {
    private val file = File(context.filesDir, "errors.log")
    private val fmt = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())

    private val _text = MutableStateFlow(read())
    val text: StateFlow<String> = _text.asStateFlow()

    private fun read(): String = try {
        if (file.exists()) file.readText() else ""
    } catch (e: Exception) {
        ""
    }

    @Synchronized
    fun add(where: String, detail: String) {
        val entry = "[${fmt.format(Date())}] $where\n$detail\n\n"
        val merged = (entry + read()).take(30_000)
        try {
            file.writeText(merged)
        } catch (e: Exception) {
            // Logging must never cause a second failure.
        }
        _text.value = merged
    }

    @Synchronized
    fun clear() {
        try { file.delete() } catch (e: Exception) { }
        _text.value = ""
    }
}
