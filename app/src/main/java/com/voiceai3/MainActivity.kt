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
    private lateinit var ai: MultiAIClient

    // ── UI refs ─────────────────────────────────────────────
    private lateinit var tvLive:    TextView
    private lateinit var tvOut:     TextView
    private lateinit var btnMic:    TextView
    private lateinit var etInput:   EditText
    private lateinit var tvAcc:     TextView
    private lateinit var tvAiBadge: TextView

    // ── State ────────────────────────────────────────────────
    private enum class Mode { IDLE, COMMAND, ASK_AI, TRANSLATE_LANG }
    private var mode        = Mode.IDLE
    private var isListening = false
    private val ui          = Handler(Looper.getMainLooper())

    // ── Broadcast receiver for accessibility ─────────────────
    private val accRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { ui.post { refreshAcc() } }
    }

    // ── App grid ─────────────────────────────────────────────
    private data class App(val icon: String, val name: String, val cmd: String)
    private val APPS = listOf(
        App("💬", "WhatsApp",   "WhatsApp kholo"),
        App("▶️", "YouTube",    "YouTube kholo"),
        App("📸", "Instagram",  "Instagram kholo"),
        App("📷", "Camera",     "Camera kholo"),
        App("🗺️", "Maps",       "Maps kholo"),
        App("🌐", "Chrome",     "Chrome kholo"),
        App("⚙️", "Settings",   "Settings kholo"),
        App("👥", "Contacts",   "Contacts kholo"),
        App("✉️", "Messages",   "Messages kholo"),
        App("🔢", "Calculator", "Calculator kholo"),
        App("🖼️", "Gallery",    "Gallery kholo"),
        App("📁", "Files",      "Files kholo"),
        App("👍", "Facebook",   "Facebook kholo"),
        App("✈️", "Telegram",   "Telegram kholo"),
        App("👻", "Snapchat",   "Snapchat kholo"),
        App("🎵", "Spotify",    "Spotify kholo"),
        App("🔦", "Torch",      "torch on"),
        App("🔍", "Google",     "Google kholo")
    )

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ai = MultiAIClient(this)
        buildUI()
        initTts()
        initSpeech()
        checkAllPermissions()
        registerReceiver(accRx, IntentFilter("com.voiceai3.ACCESSIBILITY_CONNECTED"))
        refreshAcc()
        refreshAiBadge()
    }

    override fun onDestroy() {
        super.onDestroy()
        speech.destroy()
        tts.shutdown()
        try { unregisterReceiver(accRx) } catch (_: Exception) {}
    }

    // ════════════════════════════════════════════════════════
    //  PERMISSIONS
    // ════════════════════════════════════════════════════════
    private fun checkAllPermissions() {
        val needed = mutableListOf<String>()
        val perms = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        for (p in perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p)
        }
        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
    }

    // ════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════
    private fun buildUI() {
        val scroll = ScrollView(this).apply { setBackgroundColor(color("#0A0A1E")) }
        val main   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(48), dp(14), dp(32))
        }
        scroll.addView(main)
        setContentView(scroll)

        // ── Header ──────────────────────────────────────────
        val hdr = hRow()
        hdr.addView(tv("🤖 Voice AI Pro", 22f, 0xFFFFFFFF.toInt()).also { t ->
            t.typeface = Typeface.DEFAULT_BOLD
            t.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        hdr.addView(tv("⚙", 24f, 0xFFAAAAAA.toInt()).apply {
            setPadding(dp(14), dp(6), dp(4), dp(6))
            setOnClickListener { showSettings() }
        })
        hdr.layoutParams = lp(mb = dp(6))
        main.addView(hdr)

        // ── AI badge ────────────────────────────────────────
        tvAiBadge = tv("", 11f, color("#AA88FF")).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }
        main.addView(tvAiBadge)

        // ── Accessibility pill ───────────────────────────────
        tvAcc = tv("", 12f, 0xFF44CC88.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = rd("#0E2233", dp(20).toFloat())
            layoutParams = lp(mb = dp(14))
        }
        main.addView(tvAcc)

        // ── Live transcript bar ──────────────────────────────
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

        // ── App grid ─────────────────────────────────────────
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
            "Salam! Koi bhi baat poochho — Urdu, English, Roman Urdu — main samajh lunga!\n\n" +
            "• 🎙 Mic → Voice Command / Ask AI / Translate\n" +
            "• ⌨️ Neeche type kar ke bhi bhej sakte ho\n" +
            "• ⚙ Settings → AI key daalo (Gemini FREE hai!)",
            14f, 0xFFCCCCEE.toInt()
        ).apply { setLineSpacing(0f, 1.4f) }
        outCard.addView(tvOut)
        main.addView(outCard)

        // ── Text input row ───────────────────────────────────
        val inputRow = hRow().apply { layoutParams = lp(mb = dp(22)) }
        etInput = EditText(this).apply {
            hint = "Type anything in any language..."
            setHintTextColor(color("#444466"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT
            background = rd("#16163A", dp(10).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also {
                it.marginEnd = dp(10)
            }
        }
        val btnSend = tv("Send ▶", 14f, Color.WHITE).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rd("#2233BB", dp(10).toFloat())
            setOnClickListener { doSend() }
        }
        inputRow.addView(etInput)
        inputRow.addView(btnSend)
        main.addView(inputRow)

        // ── Mic button ───────────────────────────────────────
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
    //  MIC OPTIONS DIALOG
    // ════════════════════════════════════════════════════════
    private fun onMicTap() {
        if (isListening) { stopListen(); return }

        val aiLabel = if (ai.hasAnyKey)
            "🤖   Ask AI (${ai.activeNames()})"
        else
            "🤖   Ask AI — ⚙ Settings mein key daalo"

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
                        if (!ai.hasAnyKey) {
                            showSettings()
                            setOut("⚙️ Pehle koi AI key daalo — Settings mein.\nGemini bilkul FREE hai!")
                        } else startMode(Mode.ASK_AI)
                    }
                }
            }.show()
    }

    private fun startMode(m: Mode) {
        mode = m
        when (m) {
            Mode.COMMAND -> { setOut("🎙️ Boliye — Voice Command sun raha hoon..."); startListen() }
            Mode.ASK_AI  -> { setOut("🤖 Boliye — AI sun raha hai..."); startListen() }
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
                "Phir translate karega."
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

            override fun onReadyForSpeech(p: Bundle?) {
                ui.post {
                    isListening = true
                    btnMic.text = "⏹"
                    btnMic.background = oval("#CC2233")
                    tvLive.text = "Listening..."
                    tvLive.setTextColor(color("#44AAFF"))
                }
            }

            override fun onPartialResults(b: Bundle?) {
                val partial = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) ui.post {
                    tvLive.text = partial
                    tvLive.setTextColor(color("#66CCFF"))
                }
            }

            override fun onResults(b: Bundle?) {
                val result = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                ui.post {
                    tvLive.text = if (result.isNotBlank()) "\"$result\"" else "..."
                    tvLive.setTextColor(Color.WHITE)
                    resetMicBtn()
                    if (result.isNotBlank()) handleSpeechResult(result)
                }
            }

            override fun onError(err: Int) {
                ui.post {
                    resetMicBtn()
                    tvLive.text = "Dobara tap karein (${errLabel(err)})"
                    tvLive.setTextColor(color("#888888"))
                }
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
            Mode.COMMAND      -> engine.process(text)
            Mode.ASK_AI       -> { setOut("🤖 \"$text\"\n\nAI se pooch raha hoon..."); askAI(text) }
            Mode.TRANSLATE_LANG -> doTranslate(text)
            Mode.IDLE         -> engine.process(text)
        }
        mode = Mode.IDLE
    }

    private fun askAI(text: String) {
        ai.ask(text) { reply ->
            ui.post {
                setOut(reply)
                say(reply.replace("🤖 ", "").replace("❌ ", ""))
                tvLive.text = "✅ AI ne jawab diya"
                tvLive.setTextColor(color("#AA88FF"))
            }
        }
    }

    private fun startListen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setOut("❌ Speech recognition is device pe nahi hai")
            return
        }
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    private fun stopListen()  { speech.stopListening(); resetMicBtn() }

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
    //  TTS + ENGINE INIT
    // ════════════════════════════════════════════════════════
    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.ENGLISH
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
                        ui.post { setOut("🤖 AI se pooch raha hoon...") }
                        askAI(query)
                    }
                )
            }
        }
    }

    private fun say(text: String) = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)

    // ════════════════════════════════════════════════════════
    //  SETTINGS DIALOG — 5 AI engines + Commands list
    // ════════════════════════════════════════════════════════
    private fun showSettings() {
        val scroll = ScrollView(this)
        val wrap   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        scroll.addView(wrap)

        fun hdr(text: String, clr: String): TextView = tv(text, 12f, color(clr)).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(4))
        }
        fun hint(text: String): TextView = tv(text, 10f, 0xFF666688.toInt()).apply {
            setPadding(0, dp(2), 0, dp(4))
            setLineSpacing(0f, 1.3f)
        }
        fun field(cur: String): EditText = EditText(this).apply {
            setText(cur)
            hint = "Paste key yahan..."
            setHintTextColor(color("#333355"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            inputType = InputType.TYPE_CLASS_TEXT
            background = rd("#1A1A3A", dp(8).toFloat())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // ── 1. Gemini (FREE) ────────────────────────────────
        wrap.addView(hdr("🟢  Google Gemini 2.0 Flash — BILKUL FREE", "#44CC66"))
        wrap.addView(hint("Key kaise milegi:\n1. aistudio.google.com\n2. Sign in → 'Get API Key' → 'Create API key'\n3. Copy karo, yahan paste karo"))
        val etGemini = field(ai.geminiKey)
        wrap.addView(etGemini)

        // ── 2. OpenRouter (FREE) ────────────────────────────
        wrap.addView(hdr("🟡  OpenRouter — FREE (Llama 3.1 AI)", "#FFCC44"))
        wrap.addView(hint("Key kaise milegi:\n1. openrouter.ai → Sign up (free)\n2. 'Keys' → 'Create Key'\n3. Copy karo, yahan paste karo\n(Free credits milte hain sign up pe!)"))
        val etOpenRouter = field(ai.openrouterKey)
        wrap.addView(etOpenRouter)

        // ── 3. Grok (FREE) ──────────────────────────────────
        wrap.addView(hdr("⚡  xAI Grok — FREE tier available", "#44DDFF"))
        wrap.addView(hint("Key kaise milegi:\n1. console.x.ai → Sign in\n2. 'API Keys' → 'Create Key'\n3. Copy karo, yahan paste karo"))
        val etGrok = field(ai.grokKey)
        wrap.addView(etGrok)

        // ── 4. Claude (PAID) ────────────────────────────────
        wrap.addView(hdr("🟣  Anthropic Claude Haiku — PAID (~\$0.001/msg)", "#AA88FF"))
        wrap.addView(hint("console.anthropic.com → API Keys → Create Key\n(Agar upar wali free keys hain to yeh zaruri nahi)"))
        val etClaude = field(ai.claudeKey)
        wrap.addView(etClaude)

        // ── 5. OpenAI (PAID) ────────────────────────────────
        wrap.addView(hdr("🔵  OpenAI GPT-4o Mini — PAID (~\$0.001/msg)", "#4488FF"))
        wrap.addView(hint("platform.openai.com → API keys → Create secret key\n(Agar upar wali free keys hain to yeh zaruri nahi)"))
        val etOpenAI = field(ai.openaiKey)
        wrap.addView(etOpenAI)

        // ── Commands button ─────────────────────────────────
        wrap.addView(spacer(dp(12)))
        wrap.addView(tv("━━━━━━━━━━━━━━━━━━━━━━", 10f, 0xFF333355.toInt()).apply {
            gravity = Gravity.CENTER
        })
        val btnCmds = tv("📋  Commands List Dekhein", 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rd("#1A3A2A", dp(8).toFloat())
            layoutParams = lp(mt = dp(8), mb = dp(4))
            setOnClickListener { showCommandsList() }
        }
        wrap.addView(btnCmds)

        wrap.addView(tv(
            "💡 Bas ek free key bhi kaafi hai! Gemini ya OpenRouter recommend hai.\n" +
            "Keys fail honge to automatically agli key try hogi.",
            10f, 0xFF555577.toInt()
        ).apply { setPadding(0, dp(10), 0, 0); setLineSpacing(0f, 1.4f) })

        AlertDialog.Builder(this)
            .setTitle("⚙  AI Settings — 5 Engines")
            .setView(scroll)
            .setPositiveButton("Save All") { _, _ ->
                ai.geminiKey     = etGemini.text.toString().trim()
                ai.openrouterKey = etOpenRouter.text.toString().trim()
                ai.grokKey       = etGrok.text.toString().trim()
                ai.claudeKey     = etClaude.text.toString().trim()
                ai.openaiKey     = etOpenAI.text.toString().trim()
                refreshAiBadge()
                val count = ai.activeCount()
                setOut(
                    if (count > 0)
                        "✅ $count AI engine${if (count > 1) "s" else ""} active!\n" +
                        "Engines: ${ai.activeNames()}\n\n" +
                        "Ab koi bhi sawaal poochho — main samjh lunga!"
                    else
                        "⚠️ Koi key nahi — Gemini ka free key daalo:\naistudio.google.com"
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ════════════════════════════════════════════════════════
    //  COMMANDS LIST DIALOG
    // ════════════════════════════════════════════════════════
    private fun showCommandsList() {
        val commands = """
📱 APPS KHOLNA:
• "YouTube kholo" / "WhatsApp kholo"
• "Instagram open karo" / "Camera chalo"
• "Maps kholo" / "Settings kholo"
• "Calculator" / "Gallery" / "Files"
• "Telegram" / "Snapchat" / "Facebook"

📞 CALLS:
• "Ahmad ko call karo"
• "Mama ko phone karo"
• "Ali ko ring karo"

💬 WHATSAPP MESSAGE:
• "Ahmad ko WhatsApp karo"
• "Mama ko wa message karo"
• "Ali ko WhatsApp karo likh kal aana"

📝 SMS:
• "Ahmad ko sms bhejo"
• "Ali ko message bhejo"

🔦 TORCH:
• "Torch on" / "Torch off"
• "Torch jala" / "Torch bujha"

🔊 VOLUME:
• "Volume up" / "Volume down"
• "Awaaz badhao" / "Mute"

🧭 NAVIGATION:
• "Home jao" / "Back" / "Wapas"
• "Recent apps" / "Notifications"
• "Scroll down" / "Scroll up"
• "Screenshot" / "Lock screen"

🔍 SEARCH:
• "Google karo Pakistan weather"
• "YouTube pe search karo songs"

⏰ INFO:
• "Battery kitni hai"
• "Time batao" / "Aaj kya date hai"

🤖 AI SAWAAL:
• Koi bhi sawaal — Urdu/English/Roman Urdu
• Mic → Ask AI → Bolein
• Ya neeche text box mein type karein
        """.trimIndent()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setTitle("📋 Commands List")
            .setMessage(commands)
            .setPositiveButton("Close", null)
            .show()
    }

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
            setOnClickListener { engine.process(app.cmd) }
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
    //  SEND (TEXT INPUT)
    // ════════════════════════════════════════════════════════
    private fun doSend() {
        val t = etInput.text.toString().trim()
        if (t.isEmpty()) return
        etInput.setText("")
        tvLive.text = "\"$t\""
        tvLive.setTextColor(Color.WHITE)
        val lc = t.lowercase()
        val isQuestion = t.contains("?") || t.length > 28 ||
            lc.startsWith("kya") || lc.startsWith("what") ||
            lc.startsWith("how") || lc.startsWith("why") ||
            lc.startsWith("kaisy") || lc.startsWith("kyun") ||
            lc.startsWith("batao") || lc.startsWith("tell") ||
            lc.startsWith("bata") || lc.startsWith("explain") ||
            lc.startsWith("samjhao")
        if (isQuestion && ai.hasAnyKey) {
            setOut("🤖 \"$t\"\n\nAI se pooch raha hoon...")
            askAI(t)
        } else {
            engine.process(t)
        }
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════
    private fun setOut(msg: String) { tvOut.text = msg }

    private fun refreshAiBadge() {
        val count = ai.activeCount()
        tvAiBadge.text = if (count > 0)
            "🧠 AI Brain: ${ai.activeNames()} ($count engine${if (count > 1) "s" else ""})"
        else
            "⚠️ No AI key — ⚙ Settings → Gemini FREE key daalo"
        tvAiBadge.setTextColor(if (count > 0) color("#AA88FF") else color("#FF8844"))
    }

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

    // ── Drawing helpers ──────────────────────────────────────
    private fun rd(hex: String, r: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = r; setColor(Color.parseColor(hex))
    }
    private fun oval(hex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex))
    }
    private fun tv(text: String, sp: Float, clr: Int) = TextView(this).apply {
        this.text = text; setTextColor(clr); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }
    private fun hRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
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
