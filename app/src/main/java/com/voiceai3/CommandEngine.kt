package com.voiceai3

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CommandEngine(
    private val context: Context,
    private val tts: TextToSpeech,
    private val onResponse: (String) -> Unit,
    private val onAppOpen: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("voice_ai_notes", Context.MODE_PRIVATE)

    fun processCommand(rawText: String) {
        val text = rawText.trim().lowercase()
        respond("Sun raha hoon...", speak = false)

        when {
            matchesAny(text, "wapas jao", "back jao", "peeche jao") -> {
                VoiceAccessibilityService.instance?.goBack(); respond("Wapas gaya")
            }
            matchesAny(text, "home jao", "home screen", "home par jao") -> {
                VoiceAccessibilityService.instance?.goHome(); respond("Home screen par aa gaya")
            }
            matchesAny(text, "recent apps", "background apps") -> {
                VoiceAccessibilityService.instance?.openRecents(); respond("Recent apps khul gaye")
            }
            matchesAny(text, "notification", "notifications") -> {
                VoiceAccessibilityService.instance?.openNotifications(); respond("Notifications khul gayi")
            }
            matchesAny(text, "screen lock karo", "lock karo") -> {
                VoiceAccessibilityService.instance?.lockScreen(); respond("Screen lock ho gayi")
            }
            matchesAny(text, "screenshot lo", "screenshot") -> {
                VoiceAccessibilityService.instance?.takeScreenshot(); respond("Screenshot le liya")
            }
            matchesAny(text, "scroll karo", "neeche jao", "scroll down") -> {
                VoiceAccessibilityService.instance?.scrollDown(); respond("Neeche scroll kiya")
            }
            matchesAny(text, "upar scroll", "upar jao", "scroll up") -> {
                VoiceAccessibilityService.instance?.scrollUp(); respond("Upar scroll kiya")
            }
            matchOpen(text) != null -> openAppByName(matchOpen(text)!!)
            matchType(text) != null -> doType(matchType(text)!!)
            matchWhatsApp(text) != null -> {
                val (c, m) = matchWhatsApp(text)!!; sendWhatsAppMsg(c, m)
            }
            matchSMS(text) != null -> {
                val (c, m) = matchSMS(text)!!; sendSMS(c, m)
            }
            matchCall(text) != null -> makeCall(matchCall(text)!!)
            matchClick(text) != null -> clickButton(matchClick(text)!!)
            matchesAny(text, "torch on", "torch on karo", "flashlight on") -> {
                setFlashlight(true); respond("Torch on kar di")
            }
            matchesAny(text, "torch off", "torch off karo", "flashlight off") -> {
                setFlashlight(false); respond("Torch band kar di")
            }
            matchesAny(text, "volume badhao", "volume up") -> {
                adjustVolume(1); respond("Volume barh gaya")
            }
            matchesAny(text, "volume kam karo", "volume down") -> {
                adjustVolume(-1); respond("Volume kam ho gaya")
            }
            matchesAny(text, "mute karo", "silent karo") -> {
                setMute(true); respond("Phone mute ho gaya")
            }
            matchesAny(text, "unmute karo", "awaaz on karo") -> {
                setMute(false); respond("Phone unmute ho gaya")
            }
            matchesAny(text, "battery", "battery kitni") -> {
                respond("Battery " + getBatteryLevel() + " percent hai")
            }
            matchesAny(text, "time kya hai", "time batao", "abhi time") -> {
                respond("Abhi " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + " baj rahe hain")
            }
            matchesAny(text, "date kya hai", "aaj date", "aaj ka din") -> {
                respond("Aaj " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + " hai")
            }
            matchesAny(text, "weather", "mausam", "mausam kya hai") -> fetchWeather()
            matchNote(text) != null -> saveNote(matchNote(text)!!)
            matchesAny(text, "notes dikhao", "meri notes") -> showNotes()
            matchMath(text) != null -> respond("Jawab hai: " + matchMath(text)!!)
            matchSearch(text) != null -> searchGoogle(matchSearch(text)!!)
            matchesAny(text, "screen par kya hai", "screen batao") -> {
                val content = VoiceAccessibilityService.instance?.readScreenContent() ?: ""
                if (content.isBlank()) respond("Screen par kuch nahi mila")
                else respond("Screen par: $content")
            }
            matchesAny(text, "help", "commands batao") -> {
                respond("Main kar sakta hoon: Apps kholna, messages bhejna, call karna, torch aur volume control, battery check, mausam batana, notes save karna")
            }
            else -> respond("Samajh nahi aaya: "$rawText" — dobara bolein ya help bolein")
        }
    }

    private fun openAppByName(appName: String) {
        val svc = VoiceAccessibilityService.instance
        val pkg = svc?.findAppByName(appName)
        if (pkg != null) {
            svc.openApp(pkg); respond("$appName khul raha hai..."); onAppOpen(appName)
        } else {
            respond("$appName nahi mila. Naam check karein")
        }
    }

    private fun doType(text: String) {
        val svc = VoiceAccessibilityService.instance
        if (svc == null) { respond("Accessibility service enable karein"); return }
        if (svc.typeText(text)) respond("Type kar diya: $text")
        else respond("Koi text field nahi mila. Pehle input box par tap karein")
    }

    private fun sendWhatsAppMsg(contactName: String, message: String) {
        val svc = VoiceAccessibilityService.instance
        if (svc == null) { respond("Accessibility service chahiye"); return }
        respond("$contactName ko WhatsApp par message bhej raha hoon...")
        svc.sendWhatsAppMessage(contactName, message)
    }

    private fun sendSMS(contactName: String, message: String) {
        val number = findContactNumber(contactName)
        if (number == null) { respond("$contactName ka number nahi mila contacts mein"); return }
        try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            respond("$contactName ko SMS bhej diya")
        } catch (e: Exception) { respond("SMS nahi bheja ja saka. Permission check karein") }
    }

    private fun makeCall(contactName: String) {
        val number = findContactNumber(contactName) ?: contactName.filter { it.isDigit() }
        if (number.isEmpty()) { respond("$contactName ka number nahi mila"); return }
        try {
            context.startActivity(Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            respond("$contactName ko call kar raha hoon...")
        } catch (e: SecurityException) { respond("Call permission chahiye. App settings mein allow karein") }
    }

    private fun findContactNumber(name: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null) ?: return null
        cursor.use {
            while (it.moveToNext()) {
                val cName = it.getString(0) ?: continue
                if (cName.contains(name, ignoreCase = true) || name.contains(cName, ignoreCase = true))
                    return it.getString(1)?.replace(" ", "")
            }
        }
        return null
    }

    private fun clickButton(name: String) {
        val svc = VoiceAccessibilityService.instance ?: run {
            respond("Accessibility service chahiye"); return
        }
        if (svc.clickByText(name) || svc.clickByDescription(name)) respond(""$name" par click kiya")
        else respond(""$name" screen par nahi mila")
    }

    private fun setFlashlight(on: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
        } catch (e: Exception) { respond("Torch kaam nahi kar raha") }
    }

    private fun adjustVolume(direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (direction > 0) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        else am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    private fun setMute(mute: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.ringerMode = if (mute) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
    }

    private fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun fetchWeather() {
        respond("Mausam dekh raha hoon...", speak = true)
        scope.launch {
            try {
                val json = URL("https://wttr.in/?format=j1").readText()
                val data = JSONObject(json)
                val cur = data.getJSONArray("current_condition").getJSONObject(0)
                val area = data.getJSONArray("nearest_area").getJSONObject(0)
                val city = area.getJSONArray("areaName").getJSONObject(0).getString("value")
                val tempC = cur.getString("temp_C")
                val desc = cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value")
                val humidity = cur.getString("humidity")
                val wind = cur.getString("windspeedKmph")
                val msg = "$city mein $desc hai. Garmi $tempC degree, nami $humidity percent, hawa $wind km/h"
                withContext(Dispatchers.Main) { respond(msg) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { respond("Mausam nahi mil saka. Internet check karein") }
            }
        }
    }

    private fun saveNote(text: String) {
        val notes = prefs.getStringSet("notes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val ts = SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(Date())
        notes.add("[$ts] $text")
        prefs.edit().putStringSet("notes", notes).apply()
        respond("Note save kar liya: $text")
    }

    private fun showNotes() {
        val notes = prefs.getStringSet("notes", emptySet())
        if (notes.isNullOrEmpty()) respond("Koi note save nahi hai abhi tak")
        else respond("Aapke notes hain: " + notes.joinToString(". Aur: "))
    }

    private fun searchGoogle(query: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        respond("Google par search kar raha hoon: $query")
    }

    private fun respond(message: String, speak: Boolean = true) {
        onResponse(message)
        if (speak) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "r" + System.currentTimeMillis())
        }
    }

    private fun matchesAny(text: String, vararg patterns: String) =
        patterns.any { text.contains(it, ignoreCase = true) }

    private fun matchOpen(text: String): String? {
        val rx = Regex("""(.+?)s+(?:kholo|kholein|open karo|chalaao|start karo|launch karo)""")
        return rx.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun matchType(text: String): String? {
        if (matchWhatsApp(text) != null) return null
        val rx = Regex("""(?:type karo|likho|likh do|type|write)s+(.+)""")
        return rx.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun matchWhatsApp(text: String): Pair<String, String>? {
        val rx = Regex("""(.+?)s+kos+(?:whatsapp karo|whatsapp par|whatsapp|wa)s+(?:likh|likho|bhejo|bolo)s+(.+)""")
        val m = rx.find(text) ?: return null
        return Pair(m.groupValues[1].trim(), m.groupValues[2].trim())
    }

    private fun matchSMS(text: String): Pair<String, String>? {
        val rx = Regex("""(.+?)s+kos+(?:sms karo|sms|message karo|msg)s+(?:likh|likho|bhejo|bolo)s+(.+)""")
        val m = rx.find(text) ?: return null
        return Pair(m.groupValues[1].trim(), m.groupValues[2].trim())
    }

    private fun matchCall(text: String): String? {
        val rx = Regex("""(.+?)s+kos+(?:call karo|phone karo|call karein|ring karo)""")
        return rx.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun matchClick(text: String): String? {
        val rx = Regex("""(?:click karo|tap karo|dabao|press karo)s+(.+)""")
        return rx.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun matchNote(text: String): String? {
        val rx = Regex("""^(?:note karo|note kar|yaad rakh|likh lo|save karo|remember)s*:?s*(.+)""", RegexOption.IGNORE_CASE)
        return rx.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun matchSearch(text: String): String? {
        val rx = Regex("""(?:search karo|google karo|dhundo|google)s+(.+)""")
        return rx.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun matchMath(text: String): String? {
        val rx = Regex("""(d+)s*([+-*/])s*(d+)""")
        val m = rx.find(text) ?: return null
        val a = m.groupValues[1].toLong()
        val op = m.groupValues[2]
        val b = m.groupValues[3].toLong()
        val result = when(op) {
            "+" -> a + b; "-" -> a - b; "*" -> a * b
            "/" -> if (b != 0L) a / b else return "Zero se taqseem nahi hoti!"
            else -> return null
        }
        return result.toString()
    }
}
