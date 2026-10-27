package com.jondude1.deskglass.ai

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class StreamResult(val text: String, val stopReason: String)

class ApiException(val code: Int, message: String, val raw: String = "") : Exception(message)

/** Plain HTTPS calls to the Anthropic Messages API. Blocking: call them off the main thread. */
object ClaudeApi {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    private fun open(apiKey: String, body: JSONObject, readTimeoutMs: Int): HttpURLConnection {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = readTimeoutMs
        c.doOutput = true
        c.setRequestProperty("x-api-key", apiKey)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        return c
    }

    private fun fail(c: HttpURLConnection, code: Int): Nothing {
        val text = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        val msg = try {
            JSONObject(text).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            "HTTP $code"
        }
        throw ApiException(code, msg, text.take(1500))
    }

    fun call(apiKey: String, body: JSONObject): JSONObject {
        val c = open(apiKey, body, 120_000)
        try {
            val code = c.responseCode
            if (code !in 200..299) fail(c, code)
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    /**
     * Streams a long answer and returns all the text. Used for things that take minutes, like
     * writing a whole game, where a single silent request could be cut off by the network.
     */
    fun stream(apiKey: String, body: JSONObject): StreamResult {
        body.put("stream", true)
        val c = open(apiKey, body, 300_000)
        try {
            val code = c.responseCode
            if (code !in 200..299) fail(c, code)
            val sb = StringBuilder()
            var stop = ""
            c.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val ev = JSONObject(data)
                    when (ev.optString("type")) {
                        "content_block_delta" -> {
                            val d = ev.getJSONObject("delta")
                            if (d.optString("type") == "text_delta") sb.append(d.getString("text"))
                        }
                        "error" -> throw ApiException(
                            500,
                            ev.optJSONObject("error")?.optString("message") ?: "The stream was interrupted."
                        )
                        "message_delta" -> stop = ev.optJSONObject("delta")?.optString("stop_reason") ?: stop
                        "message_stop" -> return StreamResult(sb.toString(), stop)
                    }
                }
            }
            return StreamResult(sb.toString(), stop)
        } finally {
            c.disconnect()
        }
    }
}
