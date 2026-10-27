package com.jondude1.deskglass.ai

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class ApiException(val code: Int, message: String) : Exception(message)

/** Plain HTTPS call to the Anthropic Messages API. Blocking: call it off the main thread. */
object ClaudeApi {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    fun call(apiKey: String, body: JSONObject): JSONObject {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 120_000
            c.doOutput = true
            c.setRequestProperty("x-api-key", apiKey)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "HTTP $code"
                }
                throw ApiException(code, msg)
            }
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }
}
