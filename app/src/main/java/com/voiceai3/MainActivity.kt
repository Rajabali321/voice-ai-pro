package com.voiceai3

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var dp = 0
    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private lateinit var engine: CommandEngine
    private lateinit var outputTv: TextView
    private lateinit var recognizedTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var micBtn: TextView
    private lateinit var accPill: TextView
    private lateinit var cmdInput: EditText
    private var ttsOk = false
    private var isListening = false

    data class AppItem(val name: String, val emoji: String, val color: String, val pkg: String)

    private val apps = listOf(
        AppItem("WhatsApp",   "💬", "#25D366", "com.whatsapp"),
        AppItem("YouTube",    "▶",  "#FF0000", "com.google.android.youtube"),
        AppItem("Instagram",  "📸", "#C13584", "com.instagram.android"),
        AppItem("Camera",     "📷", "#1A73E8", "camera"),
        AppItem("Maps",       "🗺", "#4285F4", "com.google.android.apps.maps"),
        AppItem("Chrome",     "🌐", "#34A853", "com.android.chrome"),
        AppItem("Settings",   "⚙",  "#78909C", "settings"),
        AppItem("Contacts",   "👤", "#FF5722", "contacts"),
        AppItem("Messages",   "✉",  "#2196F3", "messages"),
        AppItem("Calculator", "🔢", "#9C27B0", "calculator"),
        AppItem("Gallery",    "🖼",  "#FF9800", "gallery"),
        AppItem("Files",      "📁", "#795548", "files"),
        AppItem("Facebook",   "📘", "#1877F2", "com.facebook.katana"),
        AppItem("Telegram",   "✈",  "#0088CC", "org.telegram.messenger"),
        AppItem("Snapchat",   "👻", "#FFFC00", "com.snapchat.android"),
        AppItem("Spotify",    "🎵", "#1DB954", "com.spotify.music"),
        AppItem("Torch",      "🔦", "#F59E0B", "torch"),
        AppItem("Google",     "🔍", "#EA4335", "com.google.android.googlequicksearchbox")
    )

    private val accReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateAcc() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        dp = resources.displayMetrics.density.toInt()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp * 14, dp * 28, dp * 14, dp * 28)
        }
        scroll.addView(root)
        setContentView(scroll)

        // ── Title ──────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "🎙 Voice AI Pro"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, row(0, dp * 4))

        root.addView(TextView(this).apply {
            text = "Koi bhi command bolein ya tapein"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
        }, row(0, dp * 14))

        // ── Accessibility pill ─────────────────────────────
        accPill = TextView(this).apply {
            text = "Accessibility check ho raha hai..."
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp * 14, dp * 8, dp * 14, dp * 8)
            background = pill("#1A1A2E")
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(accPill, row(0, dp * 20))

        // ── AI Output card ─────────────────────────────────
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp * 16, dp * 14, dp * 16, dp * 14)
            background = card()
        }
        recognizedTv = TextView(this).apply {
            text = "Awaaz ka intezaar hai..."
            textSize = 11f
            setTextColor(Color.parseColor("#6B7280"))
        }
        outputTv = TextView(this).apply {
            text = "Mic tap karein aur command bolein"
            textSize = 15f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.4f)
        }
        card.addView(recognizedTv, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 6 })
        card.addView(outputTv)
        root.addView(card, row(0, dp * 22))

        // ── Mic button ─────────────────────────────────────
        micBtn = TextView(this).apply {
            text = "🎙\nBOLEIN"
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().also { d ->
                d.setShape(GradientDrawable.OVAL)
                d.setColor(Color.parseColor("#7B2FBE"))
            }
            setOnClickListener { toggleListen() }
        }
        root.addView(micBtn, LinearLayout.LayoutParams(dp * 160, dp * 160).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp * 8
        })

        statusTv = TextView(this).apply {
            text = "Tayyar — mic tap karein"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(statusTv, row(0, dp * 22))

        // ── Text input ─────────────────────────────────────
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cmdInput = EditText(this).apply {
            hint = "Ya yahan type karein..."
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp * 14, dp * 12, dp * 14, dp * 12)
            background = GradientDrawable().also { d ->
                d.cornerRadius = dp * 10f
                d.setColor(Color.parseColor("#111120"))
                d.setStroke(1, Color.parseColor("#2D2D5A"))
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false
            }
        }
        val sendBtn = TextView(this).apply {
            text = "➤"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().also { d ->
                d.setShape(GradientDrawable.OVAL)
                d.setColor(Color.parseColor("#7B2FBE"))
            }
            setOnClickListener { doSend() }
        }
        inputRow.addView(cmdInput, LinearLayout.LayoutParams(0, -2, 1f).also { it.rightMargin = dp * 10 })
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp * 50, dp * 50))
        root.addView(inputRow, row(0, dp * 28))

        // ── Apps section label ─────────────────────────────
        root.addView(sectionLabel("📱 Apps — Tap to Open"))

        // ── App grid ───────────────────────────────────────
        root.addView(buildAppGrid(), row(dp * 6, 0))

        // ── Init TTS + Engine ──────────────────────────────
        tts = TextToSpeech(this) { status ->
            ttsOk = status == TextToSpeech.SUCCESS
            if (ttsOk) {
                for (loc in listOf(Locale("en", "IN"), Locale("ur", "PK"), Locale.ENGLISH)) {
                    if (tts.setLanguage(loc) >= TextToSpeech.LANG_AVAILABLE) break
                }
            }
        }
        engine = CommandEngine(this, tts,
            onResponse = { msg -> runOnUiThread { outputTv.text = msg; statusTv.text = "Tayyar" } },
            onAppOpen  = { app -> runOnUiThread { statusTv.text = "$app khul raha..." } }
        )

        val need = listOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
        registerReceiver(accReceiver, IntentFilter("com.voiceai3.ACCESSIBILITY_CONNECTED"))
    }

    // ── Build 3-column app icon grid ──────────────────────
    private fun buildAppGrid(): LinearLayout {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps.chunked(3).forEach { rowItems ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            rowItems.forEach { app ->
                rowView.addView(makeAppIcon(app), LinearLayout.LayoutParams(0, -2, 1f).also {
                    it.setMargins(dp * 4, dp * 4, dp * 4, dp * 4)
                })
            }
            // fill empty slots
            repeat(3 - rowItems.size) {
                rowView.addView(View(this), LinearLayout.LayoutParams(0, dp * 80, 1f).also {
                    it.setMargins(dp * 4, dp * 4, dp * 4, dp * 4)
                })
            }
            container.addView(rowView)
        }
        return container
    }

    private fun makeAppIcon(app: AppItem): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp * 6, dp * 12, dp * 6, dp * 10)
            background = GradientDrawable().also { d ->
                d.cornerRadius = dp * 14f
                d.setColor(Color.parseColor("#111120"))
                d.setStroke(1, Color.parseColor(app.color) and 0x33FFFFFF or (Color.parseColor(app.color) and 0x00FFFFFF))
            }
            setOnClickListener { openApp(app) }

            // Color dot
            addView(TextView(context).apply {
                text = "●"
                textSize = 7f
                setTextColor(Color.parseColor(app.color))
                gravity = Gravity.END
                setPadding(0, 0, dp * 2, 0)
            }, LinearLayout.LayoutParams(-1, -2))

            // Emoji icon
            addView(TextView(context).apply {
                text = app.emoji
                textSize = 26f
                gravity = Gravity.CENTER
            })

            // App name
            addView(TextView(context).apply {
                text = app.name
                textSize = 10f
                setTextColor(Color.parseColor("#CBD5E1"))
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp * 4 })
        }
    }

    private fun openApp(app: AppItem) {
        outputTv.text = "${app.name} khul raha hai..."
        try {
            when (app.pkg) {
                "camera"     -> startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
                "settings"   -> startActivity(Intent(Settings.ACTION_SETTINGS))
                "contacts"   -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")))
                "messages"   -> startActivity(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_MESSAGING) })
                "calculator" -> startActivity(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_CALCULATOR) })
                "gallery"    -> startActivity(Intent(Intent.ACTION_VIEW).also { it.type = "image/*" })
                "files"      -> startActivity(Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_APP_FILES) })
                "torch"      -> engine.process("torch on")
                else -> {
                    val launch = packageManager.getLaunchIntentForPackage(app.pkg)
                    if (launch != null) startActivity(launch)
                    else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.pkg}")))
                }
            }
            if (ttsOk) tts.speak("${app.name} khul raha hai", TextToSpeech.QUEUE_FLUSH, null, null)
        } catch (e: Exception) {
            outputTv.text = "${app.name} nahi khul saki. Install check karein."
        }
    }

    // ── Voice toggle ──────────────────────────────────────
    private fun toggleListen() {
        if (isListening) {
            sr?.stopListening()
            isListening = false
            resetMic()
        } else {
            startListen()
        }
    }

    private fun startListen() {
        isListening = true
        micBtn.text = "🔴\nSUN RAHA"
        (micBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#DC2626"))
        statusTv.text = "Sun raha hoon..."

        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val txt = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                runOnUiThread { recognizedTv.text = "Suna: \"$txt\""; outputTv.text = "Samajh raha hoon..." }
                engine.process(txt)
                isListening = false
                runOnUiThread { resetMic() }
            }
            override fun onError(e: Int) {
                val msg = when (e) {
                    SpeechRecognizer.ERROR_NO_MATCH    -> "Samjha nahi — dobara bolein"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz nahi aayi"
                    SpeechRecognizer.ERROR_NETWORK     -> "Internet chahiye"
                    else                               -> "Error — dobara bolein"
                }
                isListening = false
                runOnUiThread { statusTv.text = msg; resetMic() }
            }
            override fun onReadyForSpeech(p: Bundle?) { runOnUiThread { statusTv.text = "Ab bolein... 🎙" } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { runOnUiThread { statusTv.text = "Samajh raha hoon..." } }
            override fun onPartialResults(p: Bundle?) {
                val part = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (part.isNotEmpty()) runOnUiThread { recognizedTv.text = "$part..." }
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        sr?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    private fun resetMic() {
        micBtn.text = "🎙\nBOLEIN"
        (micBtn.background as? GradientDrawable)?.setColor(Color.parseColor("#7B2FBE"))
        statusTv.text = "Tayyar — mic tap karein"
    }

    private fun doSend() {
        val txt = cmdInput.text.toString().trim()
        if (txt.isEmpty()) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(cmdInput.windowToken, 0)
        recognizedTv.text = "Type: \"$txt\""
        outputTv.text = "Samajh raha hoon..."
        engine.process(txt)
        cmdInput.setText("")
    }

    override fun onResume() { super.onResume(); updateAcc() }

    private fun updateAcc() {
        val on = VoiceAccessibilityService.instance != null
        runOnUiThread {
            if (on) {
                accPill.text = "✓ Accessibility Active — Sab commands kaam karein ge"
                accPill.setTextColor(Color.parseColor("#34D399"))
                (accPill.background as? GradientDrawable)?.setColor(Color.parseColor("#064E3B"))
            } else {
                accPill.text = "⚠ Accessibility OFF — Tap karein enable karne ke liye"
                accPill.setTextColor(Color.parseColor("#F59E0B"))
                (accPill.background as? GradientDrawable)?.setColor(Color.parseColor("#451A03"))
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────
    private fun row(top: Int, bot: Int) =
        LinearLayout.LayoutParams(-1, -2).also { it.topMargin = top; it.bottomMargin = bot }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#A78BFA"))
        setPadding(dp * 4, 0, 0, 0)
    }

    private fun pill(color: String) = GradientDrawable().also { d ->
        d.cornerRadius = dp * 20f
        d.setColor(Color.parseColor(color))
    }

    private fun card() = GradientDrawable().also { d ->
        d.cornerRadius = dp * 12f
        d.setColor(Color.parseColor("#111120"))
        d.setStroke(1, Color.parseColor("#1E1E3A"))
    }

    override fun onDestroy() {
        super.onDestroy()
        sr?.destroy()
        tts.shutdown()
        try { unregisterReceiver(accReceiver) } catch (e: Exception) {}
    }
}
