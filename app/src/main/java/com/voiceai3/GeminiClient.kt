package com.voiceai3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeminiClient — calls Google Gemini 2.0 Flash API (free tier).
 * Free key from: https://aistudio.google.com/app/apikey
 * Key is stored in SharedPreferences under "gemini_key".
 */
class GeminiClient(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("voiceai3_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) { prefs.edit().putString("gemini_key", v.trim()).apply() }

    val hasKey get() = apiKey.isNotBlank()

    fun ask(userText: String, callback: (String) -> Unit) {
        if (!hasKey) {
            callback("⚙️ Gemini API key nahi hai. Settings (⚙) mein daalo.")
            return
        }
        Thread {
            var conn: HttpURLConnection? = null
            try {
                // Using gemini-2.0-flash — fastest free model
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.0-flash:generateContent?key=${apiKey}"

                conn = (URL(endpoint).openConnection() as HttpURLConnection).also {
                    it.requestMethod = "POST"
                    it.setRequestProperty("Content-Type", "application/json")
                    it.doOutput = true
                    it.connectTimeout = 12_000
                    it.readTimeout    = 12_000
                }

                val systemPrompt =
                    "Tu ek helpful aur smart Android voice assistant hai. " +
                    "Roman Urdu aur English mix (Hinglish) mein jawab de. " +
                    "Short rakho — 1-3 sentences max. Friendly tone rakh. " +
                    "User ne kaha: $userText"

                val body = JSONObject().put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray().put(
                                JSONObject().put("text", systemPrompt)
                            )
                        )
                    )
                )

                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                // Check HTTP response code first
                val code = conn.responseCode
                if (code != 200) {
                    val errBody = try {
                        conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: "no details"
                    } catch (ex: Exception) { "no details" }
                    callback("❌ API Error $code — $errBody")
                    return@Thread
                }

                val responseStr = conn.inputStream.bufferedReader().readText()
                val answer = JSONObject(responseStr)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                callback("🤖 $answer")

            } catch (e: Exception) {
                val msg = e.message ?: "unknown error"
                // Friendly error messages
                val friendly = when {
                    msg.contains("NETWORK_ERROR") || msg.contains("UnknownHost") ->
                        "Network error — internet check karo"
                    msg.contains("timeout") || msg.contains("SocketTimeout") ->
                        "Timeout — connection slow hai, dobara try karo"
                    msg.contains("PERMISSION_DENIED") || msg.contains("403") ->
                        "API key galat ya expire — Settings mein nayi key daalo"
                    msg.contains("400") ->
                        "Bad request — key check karo"
                    else -> msg.take(100)
                }
                callback("❌ $friendly")
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
