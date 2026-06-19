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
    private val onAppOpen: (String) -> Unit,
    private val onUnknown: ((String) -> Unit)? = null
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
            //  APPS — direct open
            // ════════════════════════════════════════════

            appMatch(cmd, "whatsapp", "wa", "واٹس ایپ") ->
                openApp("WhatsApp",
                    "com.whatsapp",
                    "com.whatsapp.w4b")

            appMatch(cmd, "youtube", "yt", "you tube", "یوٹیوب") ->
                openApp("YouTube",
                    "com.google.android.youtube",
                    "com.google.android.apps.youtube.app",
                    "com.google.android.apps.youtube.kids")

            appMatch(cmd, "instagram", "insta", "ig", "انسٹاگرام") ->
                openApp("Instagram",
                    "com.instagram.android")

            appMatch(cmd, "facebook", "fb", "فیسبک") ->
                openApp("Facebook",
                    "com.facebook.katana",
                    "com.facebook.lite")

            appMatch(cmd, "tiktok", "tik tok", "ٹک ٹاک") ->
                openApp("TikTok",
                    "com.zhiliaoapp.musically",
                    "com.tiktok.global")

            appMatch(cmd, "twitter", "x app", "ایکس") ->
                openApp("Twitter/X",
                    "com.twitter.android",
                    "com.x.android")

            appMatch(cmd, "telegram", "ٹیلیگرام") ->
                openApp("Telegram",
                    "org.telegram.messenger",
                    "org.telegram.messenger.web")

            appMatch(cmd, "snapchat", "snap", "سنیپ چیٹ") ->
                openApp("Snapchat",
                    "com.snapchat.android")

            appMatch(cmd, "spotify", "gaane", "music app") ->
                openApp("Spotify",
                    "com.spotify.music")

            appMatch(cmd, "netflix", "نیٹ فلکس") ->
                openApp("Netflix",
                    "com.netflix.mediaclient")

            appMatch(cmd, "amazon", "شاپنگ") ->
                openApp("Amazon",
                    "in.amazon.mShoppingApp",
                    "com.amazon.mShop.android.shopping")

            appMatch(cmd, "uber", "ride", "taxi") ->
                openApp("Uber",
                    "com.ubercab")

            appMatch(cmd, "gmail", "email") ->
                openApp("Gmail",
                    "com.google.android.gm")

            appMatch(cmd, "play store", "playstore", "google play", "پلے اسٹور") ->
                openApp("Play Store",
                    "com.android.vending")

            appMatch(cmd, "maps", "google maps", "navigation", "rasta", "maps open") ->
                openApp("Maps",
                    "com.google.android.apps.maps")

            appMatch(cmd, "chrome", "browser", "internet", "web") ->
                openApp("Chrome",
                    "com.android.chrome",
                    "com.google.android.apps.chrome")

            appMatch(cmd, "calculator", "calc", "حساب") -> {
                onAppOpen("Calculator")
                val tried = listOf(
                    "com.android.calculator2",
                    "com.miui.calculator",
                    "com.coloros.calculator",
                    "com.oppo.calculator",
                    "com.vivo.calculator",
                    "com.realme.calculator",
                    "com.samsung.android.calculator"
                )
                var opened = false
                for (pkg in tried) {
                    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    if (i != null) {
                        launch(i); say("Calculator khul raha hai"); opened = true; break
                    }
                }
                if (!opened) {
                    try {
                        launch(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_CALCULATOR) })
                        say("Calculator khul raha hai")
                    } catch (_: Exception) { say("Calculator nahi mila") }
                }
            }

            appMatch(cmd, "gallery", "photos", "tasveerein", "گیلری") -> {
                onAppOpen("Gallery")
                val tried = listOf(
                    "com.miui.gallery",
                    "com.android.gallery3d",
                    "com.sec.android.gallery3d",
                    "com.google.android.apps.photos",
                    "com.coloros.gallery3d",
                    "com.oppo.gallery3d"
                )
                var opened = false
                for (pkg in tried) {
                    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    if (i != null) {
                        launch(i); say("Gallery khul rahi hai"); opened = true; break
                    }
                }
                if (!opened) {
                    try {
                        launch(Intent(Intent.ACTION_VIEW).also { it.type = "image/*" })
                        say("Gallery khul rahi hai")
                    } catch (_: Exception) { say("Gallery nahi mili") }
                }
            }

            appMatch(cmd, "settings", "setting", "سیٹنگز") -> {
                onAppOpen("Settings")
                launch(Intent(Settings.ACTION_SETTINGS))
                say("Settings khul raha hai")
            }

            appMatch(cmd, "camera", "selfie", "کیمرہ") -> {
                onAppOpen("Camera")
                val tried = listOf(
                    "com.android.camera2",
                    "com.miui.camera",
                    "com.sec.android.app.camera",
                    "com.oppo.camera",
                    "com.coloros.camera",
                    "com.vivo.camera",
                    "com.huawei.camera",
                    "org.codeaurora.snapcam"
                )
                var opened = false
                for (pkg in tried) {
                    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    if (i != null) {
                        launch(i); say("Camera khul raha hai"); opened = true; break
                    }
                }
                if (!opened) {
                    try {
                        launch(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
                        say("Camera khul raha hai")
                    } catch (_: Exception) { say("Camera nahi khul saka") }
                }
            }

            appMatch(cmd, "contacts", "phonebook", "phone book", "رابطے") -> {
                onAppOpen("Contacts")
                try {
                    launch(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")))
                } catch (_: Exception) {
                    launch(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_CONTACTS) })
                }
                say("Contacts khul rahe hain")
            }

            appMatch(cmd, "messages", "sms app", "messaging", "پیغامات") -> {
                onAppOpen("Messages")
                try {
                    launch(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_MESSAGING) })
                } catch (_: Exception) {
                    val tried = listOf("com.google.android.apps.messaging", "com.android.mms")
                    for (pkg in tried) {
                        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        if (i != null) { launch(i); break }
                    }
                }
                say("Messages khul raha hai")
            }

            appMatch(cmd, "files", "file manager", "فائلز") ->
                openApp("Files",
                    "com.android.documentsui",
                    "com.miui.fileexplorer",
                    "com.coloros.filemanager",
                    "com.oppo.filemanager")

            appMatch(cmd, "google", "google app", "goog") ->
                openApp("Google",
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.searchlite")

            appMatch(cmd, "phone", "dialer", "dial", "dialpad") -> {
                onAppOpen("Phone")
                launch(Intent(Intent.ACTION_DIAL))
                say("Dialer khul raha hai")
            }

            appMatch(cmd, "clock", "alarm", "timer") ->
                openApp("Clock",
                    "com.android.deskclock",
                    "com.miui.clock",
                    "com.sec.android.app.clockpackage")

            // ════════════════════════════════════════════
            //  CALLS  — "Ahmad ko call karo"
            // ════════════════════════════════════════════
            has(cmd, "call karo", "phone karo", "ring karo", "phon karo",
                     "call kar", "dial karo", "ko call", "ko phone", "ko ring") -> {
                val name = extractContactName(cmd,
                    listOf("ko call karo", "ko call kar", "ko phone karo",
                            "ko ring karo", "call karo", "call kar", "phone karo"))
                if (name.isNotEmpty()) {
                    val num = contactNum(name)
                    if (num != null) {
                        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
                        say("$name ko call kar raha hoon")
                    } else {
                        // Open dialer pre-filled with search
                        launch(Intent(Intent.ACTION_DIAL))
                        say("$name ka number nahi mila. Contacts mein check karo.")
                    }
                } else {
                    launch(Intent(Intent.ACTION_DIAL))
                    say("Dialer khul raha hai")
                }
            }

            // Standalone "call" keyword with a name
            has(cmd, "call") && cmd.split(" ").size >= 2 && !has(cmd,
                "torch", "volume", "scroll", "screenshot", "battery", "time", "date",
                "google", "search", "home", "back", "whatsapp") -> {
                val name = extractContactName(cmd, listOf("call karo", "call kar", "call"))
                if (name.isNotEmpty()) {
                    val num = contactNum(name)
                    if (num != null) {
                        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
                        say("$name ko call kar raha hoon")
                    } else {
                        launch(Intent(Intent.ACTION_DIAL))
                        say("$name ka number nahi mila")
                    }
                } else {
                    launch(Intent(Intent.ACTION_DIAL))
                    say("Dialer khul raha hai")
                }
            }

            // ════════════════════════════════════════════
            //  WHATSAPP MESSAGE + CALL — with contact name
            // ════════════════════════════════════════════
            has(cmd, "whatsapp", "wa") && has(cmd,
                "message", "msg", "bhejo", "karo", "likh", "text", "send") -> {
                val name = extractContactName(cmd,
                    listOf("ko whatsapp", "ko wa message", "ko wa", "ko message whatsapp"))
                val msgText = extractAfter(cmd, "likh ", "likho ", "type ", "text ", "bolo ")
                if (name.isNotEmpty()) {
                    val num = contactNum(name)
                    val waNum = if (num != null) formatForWhatsApp(num) else null
                    if (waNum != null) {
                        val encoded = if (msgText.isNotEmpty())
                            Uri.encode(msgText) else ""
                        val uri = if (encoded.isNotEmpty())
                            "https://api.whatsapp.com/send?phone=$waNum&text=$encoded"
                        else
                            "https://api.whatsapp.com/send?phone=$waNum"
                        try {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            i.setPackage("com.whatsapp")
                            launch(i)
                        } catch (_: Exception) {
                            launch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        }
                        say("$name ko WhatsApp message ${if (msgText.isNotEmpty()) "likh raha hoon" else "khol raha hoon"}")
                    } else {
                        // Name not found — just open WhatsApp
                        openApp("WhatsApp", "com.whatsapp")
                    }
                } else {
                    openApp("WhatsApp", "com.whatsapp")
                }
            }

            // ════════════════════════════════════════════
            //  SMS
            // ════════════════════════════════════════════
            has(cmd, "sms karo", "sms bhejo", "message bhejo", "text bhejo") -> {
                val name = extractContactName(cmd,
                    listOf("ko sms", "ko message", "ko text"))
                val body = extractAfter(cmd, "likh ", "text ", "keh ")
                val num  = if (name.isNotEmpty()) contactNum(name) else null
                val uri  = Uri.parse(if (num != null) "smsto:$num" else "smsto:")
                launch(Intent(Intent.ACTION_SENDTO, uri).also {
                    if (body.isNotEmpty()) it.putExtra("sms_body", body)
                })
                say("SMS ${if (name.isNotEmpty()) "$name ko " else ""}bhej raha hoon")
            }

            // ════════════════════════════════════════════
            //  TORCH
            // ════════════════════════════════════════════
            has(cmd, "torch on", "torch jala", "flashlight on", "light on",
                     "torch chala", "torch lagao", "torch kholo", "torch banda", "torch bana") ->
                { torch(true); say("Torch on!") }

            has(cmd, "torch off", "torch band", "flashlight off", "light off",
                     "torch bujha", "torch bund") ->
                { torch(false); say("Torch off!") }

            // ════════════════════════════════════════════
            //  VOLUME
            // ════════════════════════════════════════════
            has(cmd, "volume up", "volume badhao", "volume badha", "awaaz badhao",
                     "louder", "tez karo", "zyada volume", "sound badhao") -> {
                repeat(2) { volume(AudioManager.ADJUST_RAISE) }
                say("Volume badh gaya")
            }
            has(cmd, "volume down", "volume kam", "volume ghata", "awaaz kam",
                     "quieter", "dheema karo", "sound kam karo") -> {
                repeat(2) { volume(AudioManager.ADJUST_LOWER) }
                say("Volume kam ho gaya")
            }
            has(cmd, "mute", "silent", "awaaz band", "chup karo", "khamosh") -> {
                volume(AudioManager.ADJUST_MUTE); say("Mute ho gaya")
            }
            has(cmd, "unmute", "sound on", "awaaz on", "loud karo", "unmute karo") -> {
                volume(AudioManager.ADJUST_UNMUTE); say("Unmute ho gaya")
            }

            // ════════════════════════════════════════════
            //  NAVIGATION (Accessibility Service)
            // ════════════════════════════════════════════
            has(cmd, "home jao", "home pe jao", "home screen", "ghar jao", "home") -> {
                VoiceAccessibilityService.instance?.goHome() ?: say("Accessibility service off hai")
                say("Home!")
            }
            has(cmd, "wapas jao", "pichey jao", "back karo", "wapas", "back") -> {
                VoiceAccessibilityService.instance?.goBack() ?: say("Accessibility service off hai")
                say("Wapas!")
            }
            has(cmd, "recent apps", "last apps", "multitask", "recents", "recent") -> {
                VoiceAccessibilityService.instance?.showRecents() ?: say("Accessibility service off hai")
                say("Recent apps!")
            }
            has(cmd, "notification", "notifications", "drop down", "pull down", "notif") -> {
                VoiceAccessibilityService.instance?.openNotifications() ?: say("Accessibility service off hai")
                say("Notifications!")
            }
            has(cmd, "lock screen", "screen lock", "phone lock", "lock karo") -> {
                VoiceAccessibilityService.instance?.lockScreen() ?: say("Accessibility service off hai")
                say("Lock ho raha hai...")
            }
            has(cmd, "scroll down", "neechay jao", "neeche jao", "neechay", "scroll kar") -> {
                VoiceAccessibilityService.instance?.scrollDown() ?: say("Accessibility service off hai")
                say("Scroll down!")
            }
            has(cmd, "scroll up", "upar jao", "upar scroll", "upar") -> {
                VoiceAccessibilityService.instance?.scrollUp() ?: say("Accessibility service off hai")
                say("Scroll up!")
            }
            has(cmd, "screenshot", "screen capture", "screen shot", "capture karo") -> {
                VoiceAccessibilityService.instance?.takeScreenshot() ?: say("Screenshot nahi li ja saki")
                say("Screenshot!")
            }

            // ════════════════════════════════════════════
            //  INFO
            // ════════════════════════════════════════════
            has(cmd, "battery kitni", "battery check", "battery", "charge kitni") -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                say("Battery $pct percent hai")
            }
            has(cmd, "time batao", "kya time hai", "waqt", "time kya hai",
                     "baj rahe", "time bata") -> {
                val t = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                say("Abhi $t baj rahe hain")
            }
            has(cmd, "tarikh", "aaj kya hai", "today", "date", "aaj ki date", "kya date hai") -> {
                val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
                say("Aaj $d hai")
            }

            // ════════════════════════════════════════════
            //  SEARCH / GOOGLE
            // ════════════════════════════════════════════
            has(cmd, "google karo", "search karo", "dhundho", "find karo",
                     "google mein", "search mein") -> {
                val query = extractAfter(cmd,
                    "google karo ", "google mein dhundho ", "search karo ", "dhundho ", "find karo ")
                if (query.isNotEmpty()) {
                    launch(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                    say("$query dhundh raha hoon")
                } else {
                    launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                    say("Google khul raha hai")
                }
            }

            // YouTube search
            has(cmd, "youtube pe search", "youtube mein dhundho", "yt search") -> {
                val query = extractAfter(cmd, "youtube pe ", "youtube mein ", "yt ")
                if (query.isNotEmpty()) {
                    launch(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")))
                    say("YouTube pe $query dhundh raha hoon")
                } else {
                    openApp("YouTube", "com.google.android.youtube")
                }
            }

            // ════════════════════════════════════════════
            //  GREETINGS / HELP
            // ════════════════════════════════════════════
            has(cmd, "hello", "hi ", "salam", "assalam", "hey", "helo", "kya haal") ->
                say("Assalam-o-Alaikum! Main haazir hoon. Kya kaam hai? Koi bhi command bolein!")

            has(cmd, "shukriya", "thanks", "thank you", "meherbani", "shukria", "shukar") ->
                say("Bilkul! Koi aur kaam ho to batayein.")

            has(cmd, "help", "madad", "commands", "kya kar sakta", "kya kya karna") ->
                say(
                    "Main yeh sab kar sakta hoon: " +
                    "Apps khol sakta hoon (YouTube, WhatsApp, Instagram...), " +
                    "Call kar sakta hoon, WhatsApp message bhej sakta hoon, " +
                    "Torch, Volume, Battery, Time, Screenshot, Back, Home. " +
                    "Koi bhi baat poochho — AI se bhi puchwa sakta hoon!"
                )

            // ════════════════════════════════════════════
            //  UNKNOWN → AI brain fallback
            // ════════════════════════════════════════════
            else -> {
                if (onUnknown != null) {
                    onResponse("🤖 AI se pooch raha hoon...")
                    tts.speak("Soch raha hoon...", TextToSpeech.QUEUE_FLUSH, null, null)
                    onUnknown.invoke(raw)
                } else {
                    say("\"$raw\" — yeh command samajh nahi aaya. Settings mein AI key daalo!")
                }
            }
        }
    }

    // ── App match helper ─────────────────────────────────
    private fun appMatch(cmd: String, vararg names: String): Boolean {
        val openWords = listOf(
            "kholo", "open", "chalo", "chala", "khol", "launch", "start",
            "mein jao", "dekho", "chalao", "band karo", "shuru karo", "chalaao"
        )
        for (n in names) {
            if (!cmd.contains(n)) continue
            // Matches if: open-word present, OR exact name, OR cmd starts/ends with name
            if (openWords.any { cmd.contains(it) } ||
                cmd.trim() == n ||
                cmd.endsWith(n) ||
                cmd.startsWith(n) ||
                cmd.contains("$n open") ||
                cmd.contains("$n ko"))
                return true
        }
        return false
    }

    // ── Contains any phrase ──────────────────────────────
    private fun has(cmd: String, vararg phrases: String) = phrases.any { cmd.contains(it) }

    // ── Extract contact name from command ─────────────────
    // "Ahmad ko call karo" → "Ahmad"
    // "Ali ko WhatsApp karo" → "Ali"
    private fun extractContactName(cmd: String, patterns: List<String>): String {
        for (pat in patterns) {
            val i = cmd.indexOf(pat)
            if (i > 0) {
                val before = cmd.substring(0, i).trim()
                val words = before.split(" ")
                // Take last 1-2 words as the name
                val name = words.takeLast(2).joinToString(" ").trim()
                if (name.isNotEmpty()) return name.split(" ").joinToString(" ") {
                    it.replaceFirstChar { c -> c.uppercase() }
                }
            }
        }
        // Fallback: try to find a word after "call"/"message" keyword
        val words = cmd.split(" ")
        val keyIdx = words.indexOfFirst {
            it in listOf("call", "whatsapp", "wa", "message", "ring", "phone")
        }
        if (keyIdx >= 0 && keyIdx + 1 < words.size) {
            val candidate = words[keyIdx + 1]
            if (candidate !in listOf("karo", "kar", "bhejo", "pe", "ko", "me", "mein",
                                     "open", "kholo", "chalo", "app", "send"))
                return candidate.replaceFirstChar { it.uppercase() }
        }
        return ""
    }

    // ── Extract text after a keyword ─────────────────────
    private fun extractAfter(cmd: String, vararg keywords: String): String {
        for (kw in keywords) {
            val i = cmd.indexOf(kw)
            if (i >= 0) {
                val rest = cmd.substring(i + kw.length).trim()
                if (rest.isNotEmpty()) return rest
            }
        }
        return ""
    }

    // ── Format phone for WhatsApp (+92 format) ───────────
    private fun formatForWhatsApp(num: String): String {
        val digits = num.replace(Regex("[^0-9+]"), "").trimStart('+')
        return when {
            digits.startsWith("92") -> digits          // already international
            digits.startsWith("0")  -> "92" + digits.substring(1)  // Pakistani local
            digits.startsWith("1")  && digits.length == 11 -> digits  // US
            digits.length < 7       -> digits           // short — pass as-is
            else -> digits
        }
    }

    // ── Contact lookup ────────────────────────────────────
    private fun contactNum(name: String): String? = try {
        val uri  = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        ctx.contentResolver.query(
            uri, proj,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )?.use { c ->
            if (c.moveToFirst())
                c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            else null
        }
    } catch (e: Exception) { null }

    // ── Open app with multiple package fallbacks ──────────
    private fun openApp(name: String, vararg packages: String) {
        onAppOpen(name)
        for (pkg in packages) {
            try {
                val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (i != null) {
                    launch(i)
                    say("$name khul raha hai")
                    return
                }
            } catch (_: Exception) {}
        }
        // None installed — open Play Store
        try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${packages[0]}")))
        } catch (_: Exception) {
            launch(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${packages[0]}")))
        }
        say("$name install nahi — Play Store se download karo")
    }

    private fun launch(i: Intent) =
        ctx.startActivity(i.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })

    // ── Torch ─────────────────────────────────────────────
    private fun torch(on: Boolean) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
        } catch (e: Exception) { onResponse("❌ Torch kaam nahi kiya") }
    }

    // ── Volume ────────────────────────────────────────────
    private fun volume(dir: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustVolume(dir, AudioManager.FLAG_SHOW_UI)
    }
}
