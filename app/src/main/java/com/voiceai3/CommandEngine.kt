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
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(
    private val ctx: Context,
    private val tts: TextToSpeech,
    private val onResponse: (String) -> Unit,
    private val onAppOpen: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = ctx.getSharedPreferences("vai_notes", Context.MODE_PRIVATE)
    private val svc get() = VoiceAccessibilityService.instance

    fun process(raw: String) {
        val t = raw.trim().lowercase()
        when {
            has(t, "wapas jao", "back jao", "peeche jao") -> { svc?.goBack(); say("Wapas gaya") }
            has(t, "home jao", "home screen") -> { svc?.goHome(); say("Home par aa gaya") }
            has(t, "recent apps", "background apps") -> { svc?.openRecents(); say("Recent apps") }
            has(t, "notification") -> { svc?.openNotifications(); say("Notifications") }
            has(t, "screen lock", "lock karo") -> { svc?.lockScreen(); say("Lock ho gayi") }
            has(t, "screenshot") -> { svc?.takeScreenshot(); say("Screenshot le liya") }
            has(t, "scroll down", "neeche jao") -> { svc?.scrollDown(); say("Neeche gaya") }
            has(t, "scroll up", "upar jao") -> { svc?.scrollUp(); say("Upar gaya") }
            getApp(t) != null -> openApp(getApp(t)!!)
            getType(t) != null -> doType(getType(t)!!)
            getWA(t) != null -> { val p = getWA(t)!!; doWA(p.first, p.second) }
            getSMS(t) != null -> { val p = getSMS(t)!!; doSMS(p.first, p.second) }
            getCall(t) != null -> doCall(getCall(t)!!)
            getClick(t) != null -> doClick(getClick(t)!!)
            has(t, "torch on", "flashlight on") -> { torch(true); say("Torch on") }
            has(t, "torch off", "flashlight off") -> { torch(false); say("Torch off") }
            has(t, "volume badhao", "volume up") -> { vol(1); say("Volume barha") }
            has(t, "volume kam", "volume down") -> { vol(-1); say("Volume kama") }
            has(t, "mute karo", "silent") -> { mute(true); say("Mute") }
            has(t, "unmute", "awaaz on") -> { mute(false); say("Unmute") }
            has(t, "battery") -> say("Battery " + bat() + " percent")
            has(t, "time kya", "time batao", "abhi time") -> say("Abhi " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + " baj rahe")
            has(t, "date kya", "aaj date", "aaj ka din") -> say("Aaj " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date()))
            has(t, "weather", "mausam") -> weather()
            getNote(t) != null -> note(getNote(t)!!)
            has(t, "notes dikhao", "meri notes") -> showNotes()
            getMath(t) != null -> say("Jawab: " + getMath(t)!!)
            getSearch(t) != null -> search(getSearch(t)!!)
            has(t, "screen par kya", "screen batao") -> {
                val c = svc?.readScreen() ?: ""
                say(if (c.isBlank()) "Screen par kuch nahi" else "Screen: " + c.take(80))
            }
            has(t, "help", "commands") -> say("Bol sakte ho: app kholo, WhatsApp karo, call karo, SMS karo, torch, volume, battery, mausam, note karo")
            else -> say("Samajh nahi aaya: " + raw)
        }
    }

    private fun openApp(name: String) {
        val pkg = svc?.findApp(name)
        if (pkg != null) { svc?.openApp(pkg); say(name + " khul raha hai"); onAppOpen(name) }
        else say(name + " nahi mila")
    }

    private fun doType(text: String) {
        if (svc == null) { say("Accessibility enable karein"); return }
        if (svc!!.typeText(text)) say("Type kar diya: " + text)
        else say("Text field nahi mila")
    }

    private fun doWA(contact: String, msg: String) {
        if (svc == null) { say("Accessibility chahiye"); return }
        say(contact + " ko WhatsApp bhej raha hoon")
        svc!!.sendWhatsApp(contact, msg)
    }

    private fun doSMS(contact: String, msg: String) {
        val num = findNum(contact) ?: run { say(contact + " ka number nahi mila"); return }
        try { SmsManager.getDefault().sendTextMessage(num, null, msg, null, null); say("SMS bheja: " + contact) }
        catch (e: Exception) { say("SMS fail. Permission check karein") }
    }

    private fun doCall(contact: String) {
        val num = findNum(contact) ?: contact.filter { it.isDigit() }
        if (num.isEmpty()) { say(contact + " ka number nahi mila"); return }
        try {
            ctx.startActivity(Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:" + num); flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            say(contact + " ko call kar raha hoon")
        } catch (e: SecurityException) { say("Call permission chahiye") }
    }

    private fun findNum(name: String): String? {
        val cur = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null) ?: return null
        cur.use {
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                if (n.contains(name, true) || name.contains(n, true))
                    return it.getString(1)?.replace(" ", "")
            }
        }
        return null
    }

    private fun doClick(name: String) {
        if (svc == null) { say("Accessibility chahiye"); return }
        if (svc!!.clickByText(name) || svc!!.clickByDescription(name)) say(name + " click kiya")
        else say(name + " nahi mila")
    }

    private fun torch(on: Boolean) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
        } catch (e: Exception) { say("Torch kaam nahi kar raha") }
    }

    private fun vol(d: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun mute(m: Boolean) {
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode =
            if (m) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
    }

    private fun bat() = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun weather() {
        say("Mausam dekh raha hoon")
        scope.launch {
            try {
                val d = JSONObject(URL("https://wttr.in/?format=j1").readText())
                val cur = d.getJSONArray("current_condition").getJSONObject(0)
                val city = d.getJSONArray("nearest_area").getJSONObject(0)
                    .getJSONArray("areaName").getJSONObject(0).getString("value")
                val desc = cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value")
                val temp = cur.getString("temp_C")
                val hum  = cur.getString("humidity")
                val msg = city + " mein " + desc + ". " + temp + " degree, nami " + hum + " percent"
                withContext(Dispatchers.Main) { say(msg) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { say("Mausam nahi mila") } }
        }
    }

    private fun note(text: String) {
        val s = prefs.getStringSet("notes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        s.add("[" + SimpleDateFormat("d MMM hh:mm", Locale.getDefault()).format(Date()) + "] " + text)
        prefs.edit().putStringSet("notes", s).apply()
        say("Note save: " + text)
    }

    private fun showNotes() {
        val s = prefs.getStringSet("notes", emptySet())
        say(if (s.isNullOrEmpty()) "Koi note nahi" else "Notes: " + s.joinToString(". "))
    }

    private fun search(q: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        say("Search: " + q)
    }

    private fun say(msg: String) {
        onResponse(msg)
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "r" + System.currentTimeMillis())
    }

    private fun has(t: String, vararg p: String) = p.any { t.contains(it, true) }

    private fun getApp(t: String): String? {
        val rx = Regex("""(.+?)\s+(?:kholo|kholein|open karo|chalaao|start karo)""")
        return rx.find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun getType(t: String): String? {
        if (getWA(t) != null) return null
        return Regex("""(?:type karo|likho|likh do|type|write)\s+(.+)""").find(t)?.groupValues?.get(1)?.trim()
    }

    private fun getWA(t: String): Pair<String, String>? {
        val m = Regex("""(.+?)\s+ko\s+(?:whatsapp karo|whatsapp par|whatsapp|wa)\s+(?:likh|likho|bhejo|bolo)\s+(.+)""").find(t) ?: return null
        return Pair(m.groupValues[1].trim(), m.groupValues[2].trim())
    }

    private fun getSMS(t: String): Pair<String, String>? {
        val m = Regex("""(.+?)\s+ko\s+(?:sms karo|sms|message karo)\s+(?:likh|likho|bhejo|bolo)\s+(.+)""").find(t) ?: return null
        return Pair(m.groupValues[1].trim(), m.groupValues[2].trim())
    }

    private fun getCall(t: String) =
        Regex("""(.+?)\s+ko\s+(?:call karo|phone karo|call karein|ring karo)""").find(t)?.groupValues?.get(1)?.trim()

    private fun getClick(t: String) =
        Regex("""(?:click karo|tap karo|dabao|press karo)\s+(.+)""").find(t)?.groupValues?.get(1)?.trim()

    private fun getNote(t: String) =
        Regex("""^(?:note karo|yaad rakh|likh lo|save karo)\s*:?\s*(.+)""", RegexOption.IGNORE_CASE)
            .find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun getSearch(t: String) =
        Regex("""(?:search karo|google karo|dhundo|google)\s+(.+)""").find(t)?.groupValues?.get(1)?.trim()

    private fun getMath(t: String): String? {
        val m = Regex("""(\d+)\s*([+\-*/])\s*(\d+)""").find(t) ?: return null
        val a = m.groupValues[1].toLong(); val op = m.groupValues[2]; val b = m.groupValues[3].toLong()
        return when (op) {
            "+" -> (a + b).toString()
            "-" -> (a - b).toString()
            "*" -> (a * b).toString()
            "/" -> if (b != 0L) (a / b).toString() else "Zero se taqseem nahi hoti"
            else -> null
        }
    }
}
