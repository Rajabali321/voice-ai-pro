package com.voiceai3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeminiClient — calls Google Gemini 1.5 Flash API.
 * Free key from: https://aistudio.google.com/app/apikey
 * Key is stored in SharedPreferences under "gemini_key".
 */
class GeminiClient(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("voiceai3_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) { prefs.edit().putString("gemini_key", v.trim()).apply() }

    val hasKey get() = apiKey.isNotBlank()

    /**
     * Send [userText] to Gemini. Runs on background thread.
     * [callback] is called on the background thread — caller must post to UI if needed.
     */
    fun ask(userText: String, callback: (String) -> Unit) {
        if (!hasKey) {
            callback("⚙️ Gemini API key nahi hai. Settings (⚙) mein daalo — free key aistudio.google.com pe milti hai.")
            return
        }
        Thread {
            try {
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-1.5-flash:generateContent?key=${apiKey}"

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).also {
                    it.requestMethod = "POST"
                    it.setRequestProperty("Content-Type", "application/json")
                    it.doOutput = true
                    it.connectTimeout = 10_000
                    it.readTimeout    = 10_000
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

                val responseStr = conn.inputStream.bufferedReader().readText()
                val answer = JSONObject(responseStr)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                callback(answer)

            } catch (e: Exception) {
                val msg = e.message?.take(80) ?: "unknown error"
                callback("🤖 AI jawab nahi de saka: $msg")
            }
        }.start()
    }
}
