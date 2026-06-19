package com.voiceai3

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * TranslateHelper — translates text using MyMemory free API.
 * No API key required. 5000 chars/day free tier.
 * https://mymemory.translated.net/
 */
object TranslateHelper {

    /** Maps spoken/typed language names → ISO 639-1 codes */
    private val langMap = mapOf(
        "urdu"       to "ur",
        "hindi"      to "hi",
        "arabic"     to "ar",
        "french"     to "fr",
        "spanish"    to "es",
        "german"     to "de",
        "turkish"    to "tr",
        "chinese"    to "zh",
        "russian"    to "ru",
        "english"    to "en",
        "punjabi"    to "pa",
        "bengali"    to "bn",
        "italian"    to "it",
        "japanese"   to "ja",
        "korean"     to "ko",
        "malay"      to "ms",
        "indonesian" to "id",
        "portuguese" to "pt",
        "dutch"      to "nl",
        "persian"    to "fa",
        "pashto"     to "ps"
    )

    /** Langs shown in the pick dialog */
    val displayList = listOf(
        "Urdu", "Hindi", "Arabic", "French", "Spanish",
        "German", "Turkish", "Chinese", "Russian", "English",
        "Punjabi", "Bengali", "Italian", "Japanese", "Korean"
    )

    /**
     * Convert a spoken/typed language name to ISO 639-1 code.
     * Falls back to first 2 chars of the input if not found.
     */
    fun langCode(spoken: String): String {
        val key = spoken.lowercase().trim()
        return langMap[key]
            ?: langMap.entries.firstOrNull { key.contains(it.key) }?.value
            ?: key.take(2)
    }

    /**
     * Translate [text] from English to [toLangCode].
     * Runs on a background thread; call runOnUiThread inside [callback] if you update UI.
     */
    fun translate(text: String, toLangCode: String, callback: (String) -> Unit) {
        Thread {
            try {
                val safe    = text.take(500)
                val encoded = URLEncoder.encode(safe, "UTF-8")
                val apiUrl  = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|$toLangCode"

                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).also {
                    it.requestMethod  = "GET"
                    it.connectTimeout = 8_000
                    it.readTimeout    = 8_000
                }

                val resp       = conn.inputStream.bufferedReader().readText()
                val translated = JSONObject(resp)
                    .getJSONObject("responseData")
                    .getString("translatedText")

                callback(translated)

            } catch (e: Exception) {
                callback("Translation nahi ho saki: ${e.message?.take(50)}")
            }
        }.start()
    }
}
