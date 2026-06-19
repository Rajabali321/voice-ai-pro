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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── Core ────────────────────────────────────────────────
    private lateinit var tts: TextToSpeech
    private lateinit var speech: SpeechRecognizer
    private lateinit var engine: CommandEngine
    private lateinit var gemini: GeminiClient

    // ── UI refs ─────────────────────────────────────────────
    private lateinit var tvLive: TextView   // live transcript bar
    private lateinit var tvOut: TextView    // AI/command output
    private lateinit var btnMic: TextView   // mic emoji button
    private lateinit var etInput: EditText  // text input
    private lateinit var tvAcc: TextView    // accessibility pill

    // ── State ────────────────────────────────────────────────
    private enum class Mode { IDLE, COMMAND, ASK_AI, TRANSLATE_LANG }
    private var mode = Mode.IDLE
    private var isListening = false
    private val ui = Handler(Looper.getMainLooper())

    // ── Broadcast receiver for accessibility status ──────────
    private val accRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { ui.post { refreshAcc() } }
    }

    // ── App grid data ────────────────────────────────────────
    private data class App(val icon: String, val name: String, val pkg: String)
    private val APPS = listOf(
        App("💬", "WhatsApp",   "com.whatsapp"),
        App("▶️", "YouTube",    "com.google.android.youtube"),
        App("📸", "Instagram",  "com.instagram.android"),
        App("📷", "Camera",     ""),
        App("🗺️", "Maps",       "com.google.android.apps.maps"),
        App("🌐", "Chrome",     "com.android.chrome"),
        App("⚙️", "Settings",   ""),
        App("👥", "Contacts",   ""),
        App("✉️", "Messages",   ""),
        App("🔢", "Calculator", ""),
        App("🖼️", "Gallery",    ""),
        App("📁", "Files",      "com.android.documentsui"),
        App("👍", "Facebook",   "com.facebook.katana"),
        App("✈️", "Telegram",   "org.telegram.messenger"),
        App("👻", "Snapchat",   "com.snapchat.android"),
        App("🎵", "Spotify",    "com.spotify.music"),
        App("🔦", "Torch",      ""),
        App("🔍", "Google",     "com.google.android.googlequicksearchbox")
    )

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        gemini = GeminiClient(this)
        buildUI()
        initTts()
        initSpeech()
        checkMicPerm()
        registerReceiver(accRx, IntentFilter("com.voiceai3.ACCESSIBILITY_CONNECTED"))
        refreshAcc()
    }

    override fun onDestroy() {
        super.onDestroy()
        speech.destroy()
        tts.shutdown()
        try { unregisterReceiver(accRx) } catch (_: Exception) {}
    }

    // ════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════
    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color("#0A0A1E"))
        }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(48), dp(14), dp(32))
        }
        scroll.addView(main)
        setContentView(scroll)

        // ── Header: title + settings gear ───────────────────
        val hdr = hRow()
        hdr.addView(tv("🤖 Voice AI Pro", 22f, 0xFFFFFFFF.toInt()).also { t ->
            t.typeface = Typeface.DEFAULT_BOLD
            t.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        hdr.addView(tv("⚙", 24f, 0xFFAAAAAA.toInt()).apply {
            setPadding(dp(14), dp(6), dp(4), dp(6))
            setOnClickListener { showSettings() }
        })
        hdr.layoutParams = lp(mb = dp(10))
        main.addView(hdr)

        // ── Accessibility pill ───────────────────────────────
        tvAcc = tv("", 12f, 0xFF44CC88.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = rd("#0E2233", dp(20).toFloat())
            layoutParams = lp(mb = dp(14))
        }
        main.addView(tvAcc)

        // ── Live transcript bar (always visible) ─────────────
        val liveCard = hRow().apply {
            background = rd("#12122E", dp(12).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = lp(mb = dp(18))
        }
        liveCard.addView(tv("🎙", 16f, 0xFFFFFFFF.toInt()).apply {
            setPadding(0, 0, dp(10), 0)
        })
        tvLive = tv("Tap mic to speak...", 14f, 0xFF7777AA.toInt()).apply {
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        liveCard.addView(tvLive)
        main.addView(liveCard)

        // ── Apps label ───────────────────────────────────────
        main.addView(tv("Apps", 12f, 0xFF8888AA.toInt()).apply {
            setPadding(dp(4), 0, 0, dp(8))
        })

        // ── App icons grid ───────────────────────────────────
        main.addView(buildGrid())
        main.addView(spacer(dp(16)))

        // ── AI output card ───────────────────────────────────
        val outCard = LinearLayout(this).apply {
            background = rd("#0E0E2A", dp(14).toFloat())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(mb = dp(16))
        }
        outCard.addView(tv("AI Response", 11f, 0xFF445599.toInt()).apply {
            setPadding(0, 0, 0, dp(6))
        })
        tvOut = tv(
            "Salam! Mic tap karo → Voice Command / Translate Screen / Ask AI choose karo.",
            15f, 0xFFCCCCEE.toInt()
        ).apply { setLineSpacing(0f, 1.4f) }
        outCard.addView(tvOut)
        main.addView(outCard)

        // ── Text input + send ────────────────────────────────
        val inputRow = hRow().apply { layoutParams = lp(mb = dp(22)) }
        etInput = EditText(this).apply {
            hint = "Type command here..."
            setHintTextColor(color("#444466"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT
            background = rd("#16163A", dp(10).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also { it.marginEnd = dp(10) }
        }
        val btnSend = tv("Send ▶", 14f, Color.WHITE).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rd("#2233BB", dp(10).toFloat())
            setOnClickListener { doSend() }
        }
        inputRow.addView(etInput)
        inputRow.addView(btnSend)
        main.addView(inputRow)

        // ── Big mic button ───────────────────────────────────
        val micWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }
        val micSize = dp(80)
        btnMic = tv("🎤", 34f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(micSize, micSize)
            background = oval("#2233CC")
            setOnClickListener { onMicTap() }
        }
        micWrap.addView(btnMic)
        micWrap.addView(tv("Tap for options", 11f, 0xFF444466.toInt()).apply {
            gravity = Gravity.CENTER
            layoutParams = lp(mt = dp(8))
        })
        main.addView(micWrap)
    }

    // ════════════════════════════════════════════════════════
    //  MIC → OPTIONS DIALOG
    // ════════════════════════════════════════════════════════
    private fun onMicTap() {
        if (isListening) { stopListen(); return }

        val aiLabel = if (gemini.hasKey) "🤖   Ask AI (Gemini)" else "🤖   Ask AI (Set key first)"
        val items = arrayOf(
            "🎙️   Voice Command",
            "🌐   Translate Screen",
            aiLabel,
            "❌   Cancel"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setTitle("Kya karna hai?")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> startMode(Mode.COMMAND)
                    1 -> pickTranslateLang()
                    2 -> {
                        if (!gemini.hasKey) {
                            showSettings()
                            setOut("⚙️ Pehle Gemini API key daalo — Settings (⚙) mein.")
                        } else {
                            startMode(Mode.ASK_AI)
                        }
                    }
                    // 3 = cancel
                }
            }.show()
    }

    private fun startMode(m: Mode) {
        mode = m
        when (m) {
            Mode.COMMAND -> {
                setOut("🎙️ Boliye — Voice Command sun raha hoon...")
                startListen()
            }
            Mode.ASK_AI -> {
                setOut("🤖 Boliye — Gemini AI sun raha hai...")
                startListen()
            }
            else -> {}
        }
    }

    // ════════════════════════════════════════════════════════
    //  TRANSLATE FLOW
    // ════════════════════════════════════════════════════════
    private fun pickTranslateLang() {
        val langs = TranslateHelper.displayList.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setTitle("🌐 Kaunsi language mein?")
            .setItems(langs) { _, i -> doTranslate(langs[i]) }
            .setNeutralButton("Voice se bolen") { _, _ ->
                mode = Mode.TRANSLATE_LANG
                setOut("🌐 Boliye — Kaunsi language mein translate karein?")
                startListen()
            }
            .show()
    }

    private fun doTranslate(lang: String) {
        setOut("🌐 Screen text nikal raha hoon...")

        val acc = VoiceAccessibilityService.instance
        if (acc == null) {
            setOut(
                "⚠️ Accessibility Service OFF hai.\n\n" +
                "Enable karo:\nSettings → Accessibility → Voice AI Pro → ON\n\n" +
                "Tab translate karega."
            )
            say("Accessibility service enable karo")
            return
        }

        val screenText = acc.getScreenText()
        if (screenText.isBlank()) {
            setOut("❌ Screen pe koi readable text nahi mila.")
            return
        }

        val preview = if (screenText.length > 120) screenText.take(120) + "..." else screenText
        setOut("🌐 Translate ho raha hai → $lang\n\nScreen text:\n\"$preview\"")

        val code = TranslateHelper.langCode(lang)
        TranslateHelper.translate(screenText, code) { result ->
            ui.post {
                setOut("🌐 $lang mein Translation:\n\n$result")
                tvLive.text = "✅ $lang mein translate ho gaya"
                tvLive.setTextColor(color("#44CC88"))
                say(result)
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  SPEECH RECOGNIZER
    // ════════════════════════════════════════════════════════
    private fun initSpeech() {
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(p: Bundle?) = ui.post {
                isListening = true
                btnMic.text = "⏹"
                btnMic.background = oval("#CC2233")
                tvLive.text = "Listening..."
                tvLive.setTextColor(color("#44AAFF"))
            }

            // Live transcript — shows text AS you speak
            override fun onPartialResults(b: Bundle?) {
                val partial = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) ui.post {
                    tvLive.text = partial
                    tvLive.setTextColor(color("#66CCFF"))
                }
            }

            override fun onResults(b: Bundle?) {
                val result = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                ui.post {
                    tvLive.text = if (result.isNotBlank()) "\"$result\"" else "..."
                    tvLive.setTextColor(Color.WHITE)
                    resetMicBtn()
                    if (result.isNotBlank()) handleSpeechResult(result)
                }
            }

            override fun onError(err: Int) = ui.post {
                resetMicBtn()
                tvLive.text = "Dobara tap karein (${errLabel(err)})"
                tvLive.setTextColor(color("#888888"))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
    }

    private fun handleSpeechResult(text: String) {
        when (mode) {
            Mode.COMMAND -> engine.process(text)
            Mode.ASK_AI  -> {
                setOut("🤖 \"$text\"\n\nAI se pooch raha hoon...")
                gemini.ask(text) { reply ->
                    ui.post {
                        setOut("🤖 $reply")
                        say(reply)
                        tvLive.text = "✅ AI ne jawab diya"
                        tvLive.setTextColor(color("#AA88FF"))
                    }
                }
            }
            Mode.TRANSLATE_LANG -> doTranslate(text)
            Mode.IDLE -> engine.process(text)
        }
        mode = Mode.IDLE
    }

    private fun startListen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setOut("❌ Speech recognition is device pe nahi hai"); return
        }
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    private fun stopListen() {
        speech.stopListening()
        resetMicBtn()
    }

    private fun resetMicBtn() {
        isListening = false
        btnMic.text = "🎤"
        btnMic.background = oval("#2233CC")
    }

    private fun errLabel(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH       -> "no match"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
        SpeechRecognizer.ERROR_NETWORK        -> "network"
        SpeechRecognizer.ERROR_AUDIO          -> "audio err"
        else -> "err $code"
    }

    // ════════════════════════════════════════════════════════
    //  TTS + ENGINE
    // ════════════════════════════════════════════════════════
    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("ur")
                engine = CommandEngine(
                    ctx        = this,
                    tts        = tts,
                    onResponse = { msg -> ui.post { setOut(msg) } },
                    onAppOpen  = { name ->
                        ui.post {
                            tvLive.text = "Opening $name..."
                            tvLive.setTextColor(color("#44AAFF"))
                        }
                    },
                    onUnknown  = { query ->
                        // Unknown command → ask Gemini AI
                        gemini.ask(query) { reply ->
                            ui.post {
                                setOut("🤖 $reply")
                                say(reply)
                                tvLive.text = "✅ AI ne jawab diya"
                                tvLive.setTextColor(color("#AA88FF"))
                            }
                        }
                    }
                )
            }
        }
    }

    private fun say(text: String) = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)

    // ════════════════════════════════════════════════════════
    //  APP GRID
    // ════════════════════════════════════════════════════════
    private fun buildGrid(): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        APPS.chunked(3).forEach { chunk ->
            val row = hRow().apply { layoutParams = lp(mb = dp(8)) }
            chunk.forEach { row.addView(iconCell(it)) }
            wrap.addView(row)
        }
        return wrap
    }

    private fun iconCell(app: App): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            setOnClickListener { engine.process("${app.name} kholo") }
        }
        cell.addView(tv(app.icon, 26f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            val s = dp(58)
            layoutParams = LinearLayout.LayoutParams(s, s)
            background = oval("#16163A")
        })
        cell.addView(tv(app.name, 9f, color("#9999BB")).apply {
            gravity = Gravity.CENTER
            layoutParams = lp(mt = dp(4))
        })
        return cell
    }

    // ════════════════════════════════════════════════════════
    //  SETTINGS DIALOG (Gemini API key)
    // ════════════════════════════════════════════════════════
    private fun showSettings() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        wrap.addView(tv(
            "Gemini AI Key — free at aistudio.google.com:",
            13f, Color.DKGRAY
        ).apply { setPadding(0, 0, 0, dp(8)) })

        val etKey = EditText(this).apply {
            hint = "AIzaSy..."
            setText(gemini.apiKey)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        wrap.addView(etKey)
        wrap.addView(tv(
            "• aistudio.google.com pe jao\n• 'Get API key' click karo\n• Free key banao aur yahan paste karo",
            11f, 0xFF888888.toInt()
        ).apply { setPadding(0, dp(8), 0, 0); setLineSpacing(0f, 1.4f) })

        AlertDialog.Builder(this)
            .setTitle("⚙  Settings")
            .setView(wrap)
            .setPositiveButton("Save Key") { _, _ ->
                val k = etKey.text.toString().trim()
                gemini.apiKey = k
                setOut(
                    if (k.isNotBlank())
                        "✅ Gemini API key save ho gayi!\nAb Voice Command ya Tap → Ask AI se AI brain kaam karega."
                    else
                        "⚠️ Key khaali — Ask AI sirf tab kaam karega jab key ho."
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════
    private fun setOut(msg: String) { tvOut.text = msg }

    private fun refreshAcc() {
        val on = VoiceAccessibilityService.instance != null
        tvAcc.text = if (on)
            "✅ Accessibility Active — Navigation + Screen Translate Ready"
        else
            "⚠️ Tap here → Settings > Accessibility > Voice AI Pro"
        tvAcc.setTextColor(if (on) color("#44CC88") else color("#FF8844"))
        tvAcc.setOnClickListener {
            if (!on) startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun doSend() {
        val t = etInput.text.toString().trim()
        if (t.isEmpty()) return
        etInput.setText("")
        tvLive.text = "\"$t\""
        tvLive.setTextColor(Color.WHITE)
        engine.process(t)
    }

    private fun checkMicPerm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }

    // ── Drawing helpers ──────────────────────────────────────
    private fun rd(hex: String, r: Float) = GradientDrawable().apply {
        setShape(GradientDrawable.RECTANGLE)
        setCornerRadius(r)
        setColor(Color.parseColor(hex))
    }
    private fun oval(hex: String) = GradientDrawable().apply {
        setShape(GradientDrawable.OVAL)
        setColor(Color.parseColor(hex))
    }
    private fun tv(text: String, sp: Float, clr: Int) = TextView(this).apply {
        this.text = text
        setTextColor(clr)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }
    private fun hRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun lp(mt: Int = 0, mb: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).also {
        it.topMargin = mt; it.bottomMargin = mb
    }
    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, h)
    }
    private fun color(hex: String) = Color.parseColor(hex)
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT
}
