package com.voiceai3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * MultiAIClient — 3 AI engines, 1 call.
 *
 *  Priority order (whichever has key + responds first wins):
 *   1. Google  Gemini 2.0 Flash   → free key: aistudio.google.com
 *   2. Anthropic Claude Haiku      → key: console.anthropic.com
 *   3. OpenAI  GPT-4o Mini         → key: platform.openai.com
 *
 *  If engine 1 fails → engine 2 is tried → engine 3 is tried.
 *  Understands ANY language. Responds in Roman Urdu + English mix.
 *
 *  Keys stored in SharedPreferences ("voiceai3_prefs"):
 *    gemini_key | claude_key | openai_key
 */
class MultiAIClient(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("voiceai3_prefs", Context.MODE_PRIVATE)

    // ── Key getters/setters ─────────────────────────────────
    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) { prefs.edit().putString("gemini_key", v.trim()).apply() }

    var claudeKey: String
        get() = prefs.getString("claude_key", "") ?: ""
        set(v) { prefs.edit().putString("claude_key", v.trim()).apply() }

    var openaiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(v) { prefs.edit().putString("openai_key", v.trim()).apply() }

    val hasAnyKey get() = geminiKey.isNotBlank() || claudeKey.isNotBlank() || openaiKey.isNotBlank()

    fun activeCount() = listOf(geminiKey, claudeKey, openaiKey).count { it.isNotBlank() }

    fun activeNames(): String {
        val list = mutableListOf<String>()
        if (geminiKey.isNotBlank()) list.add("Gemini")
        if (claudeKey.isNotBlank())  list.add("Claude")
        if (openaiKey.isNotBlank())  list.add("GPT-4o")
        return list.joinToString(" + ")
    }

    // ── System prompt — understands ANY language ───────────
    private val sysPrompt =
        "You are a smart, helpful AI voice assistant embedded in an Android app. " +
        "The user may speak or type in ANY language (Urdu, Hindi, Arabic, English, Roman Urdu, Punjabi, or any mix). " +
        "ALWAYS understand the intent regardless of language or spelling. " +
        "ALWAYS reply in Roman Urdu mixed with English (Hinglish) — friendly, conversational, short (2-4 sentences max). " +
        "If someone asks a question, answer it clearly. If they ask for help, help them. Never say you can't understand."

    // ── Main entry point ────────────────────────────────────
    fun ask(userText: String, callback: (String) -> Unit) {
        if (!hasAnyKey) {
            callback("⚙️ Koi AI key nahi — Settings (⚙) mein kam se kam ek key daalo.")
            return
        }

        Thread {
            // Build engine list — keys available first
            val engines = buildList {
                if (geminiKey.isNotBlank()) add("gemini")
                if (claudeKey.isNotBlank())  add("claude")
                if (openaiKey.isNotBlank())  add("openai")
            }

            var answer: String? = null
            var lastError = ""

            for (engine in engines) {
                val result = when (engine) {
                    "gemini" -> askGemini(userText)
                    "claude" -> askClaude(userText)
                    "openai" -> askOpenAI(userText)
                    else     -> null
                }
                if (result != null) {
                    answer = result
                    break
                }
                lastError = engine
            }

            if (answer != null) {
                callback("🤖 $answer")
            } else {
                callback(
                    "❌ Koi AI jawab nahi de saka.\n" +
                    "• Internet connection check karo\n" +
                    "• Ya Settings mein keys theek se daalo"
                )
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════
    //  ENGINE 1: Google Gemini 2.0 Flash (free)
    // ════════════════════════════════════════════════════════
    private fun askGemini(text: String): String? = try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                  "gemini-2.0-flash:generateContent?key=$geminiKey"
        val body = JSONObject().put(
            "contents", JSONArray().put(
                JSONObject().put(
                    "parts", JSONArray()
                        .put(JSONObject().put("text", "$sysPrompt\n\nUser: $text"))
                )
            )
        ).put(
            "generationConfig", JSONObject()
                .put("maxOutputTokens", 300)
                .put("temperature", 0.8)
        )
        val resp = httpPost(url, body.toString(), mapOf("Content-Type" to "application/json"))
            ?: return null
        JSONObject(resp)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    } catch (e: Exception) { null }

    // ════════════════════════════════════════════════════════
    //  ENGINE 2: Anthropic Claude Haiku (paid but cheap)
    // ════════════════════════════════════════════════════════
    private fun askClaude(text: String): String? = try {
        val url = "https://api.anthropic.com/v1/messages"
        val body = JSONObject()
            .put("model", "claude-haiku-4-5")
            .put("max_tokens", 300)
            .put("system", sysPrompt)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", text)
            ))
        val resp = httpPost(url, body.toString(), mapOf(
            "Content-Type"       to "application/json",
            "x-api-key"          to claudeKey,
            "anthropic-version"  to "2023-06-01"
        )) ?: return null
        JSONObject(resp)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()
    } catch (e: Exception) { null }

    // ════════════════════════════════════════════════════════
    //  ENGINE 3: OpenAI GPT-4o Mini (cheap + powerful)
    // ════════════════════════════════════════════════════════
    private fun askOpenAI(text: String): String? = try {
        val url = "https://api.openai.com/v1/chat/completions"
        val body = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("max_tokens", 300)
            .put("temperature", 0.8)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", sysPrompt))
                .put(JSONObject().put("role", "user").put("content", text))
            )
        val resp = httpPost(url, body.toString(), mapOf(
            "Content-Type"  to "application/json",
            "Authorization" to "Bearer $openaiKey"
        )) ?: return null
        JSONObject(resp)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    } catch (e: Exception) { null }

    // ════════════════════════════════════════════════════════
    //  HTTP POST helper
    // ════════════════════════════════════════════════════════
    private fun httpPost(urlStr: String, body: String, headers: Map<String, String>): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).also {
                it.requestMethod = "POST"
                headers.forEach { (k, v) -> it.setRequestProperty(k, v) }
                it.doOutput      = true
                it.connectTimeout = 12_000
                it.readTimeout    = 12_000
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                // Read error body for debugging (ignored, return null to try next engine)
                conn.errorStream?.bufferedReader()?.readText()
                return null
            }
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
