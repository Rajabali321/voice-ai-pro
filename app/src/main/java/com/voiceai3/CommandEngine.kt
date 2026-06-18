package com.voiceai3

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(
    private val ctx: Context,
    private val tts: TextToSpeech,
    private val onResponse: (String) -> Unit,
    private val onAppOpen: (String) -> Unit
) {

    private fun say(text: String) {
        onResponse(text)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun process(raw: String) {
        if (raw.isBlank()) return
        val cmd = raw.lowercase(Locale.getDefault()).trim()

        when {

            // ════════════════════════════════════════════
            //  APPS
            // ════════════════════════════════════════════
            appMatch(cmd, "whatsapp", "wa") ->
                openPkg("com.whatsapp", "WhatsApp")

            appMatch(cmd, "youtube", "yt", "you tube") ->
                openPkg("com.google.android.youtube", "YouTube")

            appMatch(cmd, "instagram", "insta", "ig") ->
                openPkg("com.instagram.android", "Instagram")

            appMatch(cmd, "facebook", "fb") ->
                openPkg("com.facebook.katana", "Facebook")

            appMatch(cmd, "twitter", "x app") ->
                openPkg("com.twitter.android", "Twitter")

            appMatch(cmd, "tiktok", "tik tok") ->
                openPkg("com.zhiliaoapp.musically", "TikTok")

            appMatch(cmd, "telegram") ->
                openPkg("org.telegram.messenger", "Telegram")

            appMatch(cmd, "snapchat", "snap") ->
                openPkg("com.snapchat.android", "Snapchat")

            appMatch(cmd, "spotify", "gaane", "music app") ->
                openPkg("com.spotify.music", "Spotify")

            appMatch(cmd, "netflix") ->
                openPkg("com.netflix.mediaclient", "Netflix")

            appMatch(cmd, "amazon") ->
                openPkg("in.amazon.mShoppingApp", "Amazon")

            appMatch(cmd, "uber", "ride", "taxi") ->
                openPkg("com.ubercab", "Uber")

            appMatch(cmd, "gmail", "email") ->
                openPkg("com.google.android.gm", "Gmail")

            appMatch(cmd, "play store", "playstore", "google play") ->
                openPkg("com.android.vending", "Play Store")

            appMatch(cmd, "maps", "google maps", "navigation", "rasta") ->
                openPkg("com.google.android.apps.maps", "Maps")

            appMatch(cmd, "chrome", "browser", "internet", "web") ->
                openPkg("com.android.chrome", "Chrome")

            appMatch(cmd, "settings", "setting") -> {
                onAppOpen("Settings")
                launch(Intent(Settings.ACTION_SETTINGS))
                say("Settings khul raha hai")
            }

            appMatch(cmd, "camera", "selfie") -> {
                onAppOpen("Camera")
                launch(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
                say("Camera khul raha hai")
            }

            appMatch(cmd, "contacts", "phonebook", "phone book") -> {
                onAppOpen("Contacts")
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")))
                say("Contacts khul rahe hain")
            }

            appMatch(cmd, "calculator", "calc") -> {
                onAppOpen("Calculator")
                launch(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_CALCULATOR) })
                say("Calculator khul raha hai")
            }

            appMatch(cmd, "gallery", "photos", "tasveerein") -> {
                onAppOpen("Gallery")
                launch(Intent(Intent.ACTION_VIEW).also { it.type = "image/*" })
                say("Gallery khul rahi hai")
            }

            appMatch(cmd, "messages", "sms app", "messaging") -> {
                onAppOpen("Messages")
                launch(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_MESSAGING) })
                say("Messages khul raha hai")
            }

            // ════════════════════════════════════════════
            //  CALLS
            // ════════════════════════════════════════════
            has(cmd, "call", "phone karo", "ring karo", "phon karo", "call karo") -> {
                val name = nameFrom(cmd, "ko call", "ko phone", "ko ring", "ko phon", "call")
                if (name.isNotEmpty()) {
                    val num = contactNum(name)
                    if (num != null) {
                        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
                        say("$name ko call kar raha hoon")
                    } else {
                        say("$name ka number nahi mila")
                    }
                } else {
                    launch(Intent(Intent.ACTION_DIAL))
                    say("Dialer khul raha hai")
                }
            }

            // ════════════════════════════════════════════
            //  WHATSAPP MESSAGE
            // ════════════════════════════════════════════
            has(cmd, "whatsapp karo", "whatsapp bhejo", "whatsapp message", "wa bhejo") -> {
                val name = nameFrom(cmd, "ko whatsapp", "ko wa")
                if (name.isNotEmpty()) {
                    val num = contactNum(name)
                    if (num != null) {
                        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$num")))
                        say("$name ko WhatsApp khul raha hai")
                    } else {
                        openPkg("com.whatsapp", "WhatsApp")
                    }
                } else { openPkg("com.whatsapp", "WhatsApp") }
            }

            // ════════════════════════════════════════════
            //  SMS
            // ════════════════════════════════════════════
            has(cmd, "sms karo", "message bhejo", "text bhejo", "sms bhejo") -> {
                val name = nameFrom(cmd, "ko sms", "ko message", "ko text")
                val body = after(cmd, "likh")
                val num  = if (name.isNotEmpty()) contactNum(name) else null
                val uri  = Uri.parse(if (num != null) "smsto:$num" else "smsto:")
                launch(Intent(Intent.ACTION_SENDTO, uri).also { it.putExtra("sms_body", body) })
                say("SMS ${if (name.isNotEmpty()) name + " ko" else ""} bheja ja raha hai")
            }

            // ════════════════════════════════════════════
            //  TORCH
            // ════════════════════════════════════════════
            has(cmd, "torch on", "torch jala", "flashlight on", "light on", "torch chala", "torch lagao") -> {
                torch(true); say("Torch on!")
            }
            has(cmd, "torch off", "torch band", "flashlight off", "light off", "torch bujha") -> {
                torch(false); say("Torch off!")
            }

            // ════════════════════════════════════════════
            //  VOLUME
            // ════════════════════════════════════════════
            has(cmd, "volume up", "volume badhao", "volume badha", "awaaz badhao", "louder", "tez karo") -> {
                volume(AudioManager.ADJUST_RAISE)
                volume(AudioManager.ADJUST_RAISE)
                say("Volume badh gaya")
            }
            has(cmd, "volume down", "volume kam", "volume ghata", "awaaz kam", "quieter", "dheema karo") -> {
                volume(AudioManager.ADJUST_LOWER)
                volume(AudioManager.ADJUST_LOWER)
                say("Volume kam ho gaya")
            }
            has(cmd, "mute", "silent", "awaaz band", "chup karo") -> {
                volume(AudioManager.ADJUST_MUTE)
                say("Mute ho gaya")
            }
            has(cmd, "unmute", "sound on", "awaaz on", "loud karo") -> {
                volume(AudioManager.ADJUST_UNMUTE)
                say("Unmute ho gaya")
            }

            // ════════════════════════════════════════════
            //  NAVIGATION (Accessibility)
            // ════════════════════════════════════════════
            has(cmd, "home", "home jao", "ghar jao", "home screen", "home pe jao") -> {
                VoiceAccessibilityService.instance?.goHome()
                say("Home!")
            }
            has(cmd, "back", "wapas", "wapas jao", "pichey jao", "back karo") -> {
                VoiceAccessibilityService.instance?.goBack()
                say("Wapas!")
            }
            has(cmd, "recent", "recent apps", "last apps", "multitask") -> {
                VoiceAccessibilityService.instance?.showRecents()
                say("Recent apps!")
            }
            has(cmd, "notification", "notifications", "drop down", "pull down") -> {
                VoiceAccessibilityService.instance?.openNotifications()
                say("Notifications!")
            }
            has(cmd, "lock", "screen lock", "lock screen", "phone lock") -> {
                VoiceAccessibilityService.instance?.lockScreen()
                say("Lock ho raha hai...")
            }
            has(cmd, "scroll down", "neechay jao", "neeche jao") -> {
                VoiceAccessibilityService.instance?.scrollDown()
                say("Scroll down!")
            }
            has(cmd, "scroll up", "upar jao", "upar scroll") -> {
                VoiceAccessibilityService.instance?.scrollUp()
                say("Scroll up!")
            }
            has(cmd, "screenshot", "screen capture", "screen shot") -> {
                VoiceAccessibilityService.instance?.takeScreenshot()
                say("Screenshot!")
            }

            // ════════════════════════════════════════════
            //  INFO
            // ════════════════════════════════════════════
            has(cmd, "battery", "charge", "battery kitni", "battery check") -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                say("Battery $pct percent hai")
            }
            has(cmd, "time", "waqt", "time batao", "kya time hai", "baj rahe") -> {
                val t = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                say("Abhi $t baj rahe hain")
            }
            has(cmd, "date", "tarikh", "aaj kya", "today", "aaj kya hai") -> {
                val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
                say("Aaj $d hai")
            }

            // ════════════════════════════════════════════
            //  SEARCH
            // ════════════════════════════════════════════
            has(cmd, "google", "search", "dhundho", "find karo", "dhundhna") -> {
                val query = after(cmd, "google karo", "google mein dhundho", "google", "search karo", "search", "dhundho", "find karo")
                if (query.isNotEmpty()) {
                    launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                    say("$query dhundh raha hoon")
                } else {
                    launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                    say("Google khul raha hai")
                }
            }

            // ════════════════════════════════════════════
            //  GREETINGS / HELP
            // ════════════════════════════════════════════
            has(cmd, "hello", "hi", "salam", "assalam", "hey", "helo") ->
                say("Assalam-o-Alaikum! Kya kaam hai? Koi bhi command bolein!")

            has(cmd, "shukriya", "thanks", "thank you", "meherbani", "shukria") ->
                say("Bilkul! Koi aur kaam ho to batayein.")

            has(cmd, "help", "commands", "kya kar sakta", "kya kya karna") ->
                say("Main apps khol sakta hoon, call kar sakta hoon, WhatsApp message bhej sakta hoon, torch, volume, screenshot, time, battery — bas bolein aur main kar deta hoon!")

            // ════════════════════════════════════════════
            //  UNKNOWN
            // ════════════════════════════════════════════
            else ->
                say("\"$raw\" samajh nahi aaya. Dobara bolein ya help type karein.")
        }
    }

    // ── App match: name + open-word ───────────────────────
    private fun appMatch(cmd: String, vararg names: String): Boolean {
        val open = listOf("kholo", "open", "chalo", "chala", "khol", "launch", "start", "mein jao", "dekho", "chalao")
        for (n in names) {
            if (!cmd.contains(n)) continue
            if (open.any { cmd.contains(it) } || cmd.trim() == n || cmd.endsWith(n) || cmd.startsWith(n))
                return true
        }
        return false
    }

    // ── Contains any of these phrases ────────────────────
    private fun has(cmd: String, vararg phrases: String) = phrases.any { cmd.contains(it) }

    // ── Extract person name from command ─────────────────
    private fun nameFrom(cmd: String, vararg beforeWords: String): String {
        for (w in beforeWords) {
            val i = cmd.indexOf(w)
            if (i > 0) {
                val before = cmd.substring(0, i).trim().split(" ")
                val name = before.lastOrNull() ?: continue
                return name.replaceFirstChar { it.uppercase() }
            }
        }
        return ""
    }

    // ── Extract text after keyword ────────────────────────
    private fun after(cmd: String, vararg keywords: String): String {
        for (kw in keywords) {
            val i = cmd.indexOf(kw)
            if (i >= 0) {
                val rest = cmd.substring(i + kw.length).trim()
                if (rest.isNotEmpty()) return rest
            }
        }
        return ""
    }

    // ── Contact lookup ────────────────────────────────────
    private fun contactNum(name: String): String? = try {
        val uri  = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        ctx.contentResolver.query(uri, proj,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            else null
        }
    } catch (e: Exception) { null }

    // ── Open installed package ────────────────────────────
    private fun openPkg(pkg: String, name: String) {
        onAppOpen(name)
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                launch(i); say("$name khul raha hai")
            } else {
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                say("$name install nahi — Play Store khul raha hai")
            }
        } catch (e: Exception) { say("$name nahi khul saka") }
    }

    private fun launch(i: Intent) =
        ctx.startActivity(i.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })

    // ── Torch ─────────────────────────────────────────────
    private fun torch(on: Boolean) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
        } catch (e: Exception) { onResponse("Torch kaam nahi kiya") }
    }

    // ── Volume ────────────────────────────────────────────
    private fun volume(dir: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustVolume(dir, AudioManager.FLAG_SHOW_UI)
    }
}
