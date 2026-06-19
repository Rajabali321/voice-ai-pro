package com.voiceai3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * MultiAIClient — 5 AI engines, 1 call. Smart auto-fallback.
 *
 *  Priority order (whichever has key + responds first wins):
 *   1. Google  Gemini 2.0 Flash     → FREE  → aistudio.google.com
 *   2. OpenRouter (Llama 3.1 free)  → FREE  → openrouter.ai
 *   3. xAI Grok                     → FREE  → console.x.ai
 *   4. Anthropic Claude Haiku       → PAID  → console.anthropic.com
 *   5. OpenAI GPT-4o Mini           → PAID  → platform.openai.com
 *
 *  Understands ANY language. Responds in Roman Urdu + English mix.
 */
class MultiAIClient(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("voiceai3_prefs", Context.MODE_PRIVATE)

    // ── Key properties ──────────────────────────────────────
    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) { prefs.edit().putString("gemini_key", v.trim()).apply() }

    var openrouterKey: String
        get() = prefs.getString("openrouter_key", "") ?: ""
        set(v) { prefs.edit().putString("openrouter_key", v.trim()).apply() }

    var grokKey: String
        get() = prefs.getString("grok_key", "") ?: ""
        set(v) { prefs.edit().putString("grok_key", v.trim()).apply() }

    var claudeKey: String
        get() = prefs.getString("claude_key", "") ?: ""
        set(v) { prefs.edit().putString("claude_key", v.trim()).apply() }

    var openaiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(v) { prefs.edit().putString("openai_key", v.trim()).apply() }

    val hasAnyKey get() =
        geminiKey.isNotBlank() || openrouterKey.isNotBlank() || grokKey.isNotBlank() ||
        claudeKey.isNotBlank() || openaiKey.isNotBlank()

    fun activeCount() = listOf(geminiKey, openrouterKey, grokKey, claudeKey, openaiKey)
        .count { it.isNotBlank() }

    fun activeNames(): String {
        val list = mutableListOf<String>()
        if (geminiKey.isNotBlank())      list.add("Gemini")
        if (openrouterKey.isNotBlank())  list.add("OpenRouter")
        if (grokKey.isNotBlank())        list.add("Grok")
        if (claudeKey.isNotBlank())      list.add("Claude")
        if (openaiKey.isNotBlank())      list.add("GPT-4o")
        return list.joinToString(" + ")
    }

    // ── System prompt (any language → Hinglish reply) ───────
    private val sysPrompt =
        "You are a smart, helpful AI voice assistant embedded in an Android app. " +
        "The user may speak or type in ANY language (Urdu, Hindi, Arabic, English, " +
        "Roman Urdu, Punjabi, or any mix). " +
        "ALWAYS understand the intent regardless of language or spelling. " +
        "ALWAYS reply in Roman Urdu mixed with English (Hinglish) — " +
        "friendly, conversational, short (2-4 sentences max). " +
        "If someone asks a question, answer it clearly. Never say you can't understand."

    // ── Main entry ──────────────────────────────────────────
    fun ask(userText: String, callback: (String) -> Unit) {
        if (!hasAnyKey) {
            callback("⚙️ Koi AI key nahi — Settings (⚙) mein FREE Gemini key daalo.\naistudio.google.com → Get API Key")
            return
        }
        Thread {
            val engines = mutableListOf<String>()
            if (geminiKey.isNotBlank())      engines.add("gemini")
            if (openrouterKey.isNotBlank())  engines.add("openrouter")
            if (grokKey.isNotBlank())        engines.add("grok")
            if (claudeKey.isNotBlank())      engines.add("claude")
            if (openaiKey.isNotBlank())      engines.add("openai")

            var answer: String? = null
            for (engine in engines) {
                val result = when (engine) {
                    "gemini"     -> askGemini(userText)
                    "openrouter" -> askOpenRouter(userText)
                    "grok"       -> askGrok(userText)
                    "claude"     -> askClaude(userText)
                    "openai"     -> askOpenAI(userText)
                    else         -> null
                }
                if (result != null) { answer = result; break }
            }

            if (answer != null) {
                callback("🤖 $answer")
            } else {
                callback(
                    "❌ Koi AI jawab nahi de saka.\n" +
                    "• Internet check karo\n" +
                    "• Gemini FREE key daalo: aistudio.google.com\n" +
                    "• Ya OpenRouter FREE: openrouter.ai"
                )
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════
    //  ENGINE 1 — Google Gemini 2.0 Flash  (FREE)
    // ════════════════════════════════════════════════════════
    private fun askGemini(text: String): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                      "gemini-2.0-flash:generateContent?key=$geminiKey"
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", "$sysPrompt\n\nUser: $text")
                    ))
                ))
                .put("generationConfig", JSONObject()
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
    }

    // ════════════════════════════════════════════════════════
    //  ENGINE 2 — OpenRouter (FREE — Llama 3.1 8B)
    //  Free key at: openrouter.ai → Sign in → Free Credits
    // ════════════════════════════════════════════════════════
    private fun askOpenRouter(text: String): String? {
        return try {
            val url = "https://openrouter.ai/api/v1/chat/completions"
            val body = JSONObject()
                .put("model", "meta-llama/llama-3.1-8b-instruct:free")
                .put("max_tokens", 300)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", sysPrompt))
                    .put(JSONObject().put("role", "user").put("content", text))
                )
            val resp = httpPost(url, body.toString(), mapOf(
                "Content-Type"  to "application/json",
                "Authorization" to "Bearer $openrouterKey",
                "HTTP-Referer"  to "https://voiceaipro.app",
                "X-Title"       to "Voice AI Pro"
            )) ?: return null
            JSONObject(resp)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) { null }
    }

    // ════════════════════════════════════════════════════════
    //  ENGINE 3 — xAI Grok (FREE tier available)
    //  Free key at: console.x.ai → API Keys
    // ════════════════════════════════════════════════════════
    private fun askGrok(text: String): String? {
        return try {
            val url = "https://api.x.ai/v1/chat/completions"
            val body = JSONObject()
                .put("model", "grok-3-mini")
                .put("max_tokens", 300)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", sysPrompt))
                    .put(JSONObject().put("role", "user").put("content", text))
                )
            val resp = httpPost(url, body.toString(), mapOf(
                "Content-Type"  to "application/json",
                "Authorization" to "Bearer $grokKey"
            )) ?: return null
            JSONObject(resp)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) { null }
    }

    // ════════════════════════════════════════════════════════
    //  ENGINE 4 — Anthropic Claude Haiku (PAID)
    // ════════════════════════════════════════════════════════
    private fun askClaude(text: String): String? {
        return try {
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
    }

    // ════════════════════════════════════════════════════════
    //  ENGINE 5 — OpenAI GPT-4o Mini (PAID)
    // ════════════════════════════════════════════════════════
    private fun askOpenAI(text: String): String? {
        return try {
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
    }

    // ════════════════════════════════════════════════════════
    //  HTTP POST helper — returns null on any non-200 response
    // ════════════════════════════════════════════════════════
    private fun httpPost(urlStr: String, body: String, headers: Map<String, String>): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).also {
                it.requestMethod = "POST"
                headers.forEach { (k, v) -> it.setRequestProperty(k, v) }
                it.doOutput       = true
                it.connectTimeout = 12_000
                it.readTimeout    = 15_000
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) {}
                return null   // try next engine
            }
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
