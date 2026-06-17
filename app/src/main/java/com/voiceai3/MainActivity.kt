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
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
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

    private data class Cmd(val label: String, val desc: String, val fill: String)

    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private lateinit var engine: CommandEngine
    private lateinit var outputTv: TextView
    private lateinit var recognizedTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var micBtn: TextView
    private lateinit var accPill: TextView
    private lateinit var cmdInput: EditText
    private lateinit var helpContainer: LinearLayout
    private var ttsOk = false
    private var helpVisible = false

    private val accReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateAccStatus() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val dp = resources.displayMetrics.density.toInt()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp * 16, dp * 24, dp * 16, dp * 30)
        }
        scroll.addView(root)
        setContentView(scroll)

        // HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp * 16)
        }
        header.addView(TextView(this).apply {
            text = "Voice AI Pro"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(TextView(this).apply {
            text = "v3.0"
            textSize = 11f
            setTextColor(Color.parseColor("#6B7280"))
        })
        root.addView(header)

        // ACCESSIBILITY STATUS PILL
        accPill = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp * 14, dp * 9, dp * 14, dp * 9)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 20f
                it.setColor(Color.parseColor("#1A1A2E"))
            }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(accPill, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 16 })

        // OUTPUT CARD
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp * 16, dp * 14, dp * 16, dp * 14)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 12f
                it.setColor(Color.parseColor("#111120"))
                it.setStroke(1, Color.parseColor("#1E1E3A"))
            }
        }
        recognizedTv = TextView(this).apply {
            text = "Awaaz ya text ka intezaar hai..."
            textSize = 11f
            setTextColor(Color.parseColor("#6B7280"))
        }
        card.addView(recognizedTv, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 8 })
        outputTv = TextView(this).apply {
            text = "Tap karein aur bolein, ya neechay type karein\n\nMisaal:\n  WhatsApp kholo\n  torch on\n  battery\n  Ahmad ko call karo"
            textSize = 14f
            setTextColor(Color.WHITE)
            lineSpacingMultiplier = 1.4f
        }
        card.addView(outputTv)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 20 })

        // MIC BUTTON
        micBtn = TextView(this).apply {
            text = "TAP\nKAR KE\nBOLEIN"
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
        }
        root.addView(micBtn, LinearLayout.LayoutParams(dp * 180, dp * 180).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp * 8
        })
        micBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> startListen()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sr?.stopListening()
            }
            true
        }

        statusTv = TextView(this).apply {
            text = "Tayyar — bol ya likh sakte hain"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }
        root.addView(statusTv, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 20 })

        // OR DIVIDER
        val divRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val divBar = GradientDrawable().also { it.setColor(Color.parseColor("#1E1E3A")) }
        val lv = View(this).apply { background = divBar }
        val rv = View(this).apply { background = divBar }
        divRow.addView(lv, LinearLayout.LayoutParams(0, dp, 1f).also { it.rightMargin = dp * 12 })
        divRow.addView(TextView(this).apply {
            text = "YA TYPE KAREIN"
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
        })
        divRow.addView(rv, LinearLayout.LayoutParams(0, dp, 1f).also { it.leftMargin = dp * 12 })
        root.addView(divRow, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 14 })

        // TEXT INPUT ROW
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cmdInput = EditText(this).apply {
            hint = "Command likho... (WhatsApp kholo)"
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp * 14, dp * 13, dp * 14, dp * 13)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 10f
                it.setColor(Color.parseColor("#111120"))
                it.setStroke(1, Color.parseColor("#2D2D5A"))
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
            }
        }
        inputRow.addView(cmdInput, LinearLayout.LayoutParams(0, -2, 1f).also { it.rightMargin = dp * 10 })
        val sendBtn = TextView(this).apply {
            text = ">"
            textSize = 20f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 10f
                it.setColor(Color.parseColor("#7B2FBE"))
            }
            setOnClickListener { sendTyped() }
        }
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp * 52, dp * 52))
        root.addView(inputRow, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 28 })

        // COMMANDS GUIDE HEADER
        val helpHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp * 12, dp * 12, dp * 12, dp * 12)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 8f
                it.setColor(Color.parseColor("#111120"))
                it.setStroke(1, Color.parseColor("#2D2D5A"))
            }
            setOnClickListener { toggleHelp() }
        }
        helpHeader.addView(TextView(this).apply {
            text = "Commands Guide — Sab commands dekhein"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A78BFA"))
        })
        helpHeader.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        helpHeader.addView(TextView(this).apply {
            text = "v"
            textSize = 14f
            setTextColor(Color.parseColor("#6B7280"))
        })
        root.addView(helpHeader, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 8 })

        // HELP CONTENT
        helpContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp * 4, 0, 0)
        }
        root.addView(helpContainer)

        val categories = listOf(
            "Apps Kholo" to listOf(
                Cmd("WhatsApp kholo", "WhatsApp open hoga", "WhatsApp kholo"),
                Cmd("YouTube kholo", "YouTube open hoga", "YouTube kholo"),
                Cmd("camera kholo", "Camera open hoga", "camera kholo"),
                Cmd("Instagram kholo", "Instagram open hoga", "Instagram kholo"),
                Cmd("settings kholo", "Settings open hogi", "settings kholo"),
                Cmd("maps kholo", "Google Maps open hoga", "maps kholo"),
                Cmd("[app name] kholo", "Koi bhi app kholta hai", "chrome kholo")
            ),
            "Call & Messages" to listOf(
                Cmd("[naam] ko call karo", "Contact ko call karega", "Ahmad ko call karo"),
                Cmd("[naam] ko WhatsApp karo likh [msg]", "WhatsApp msg bhejega", "Ali ko WhatsApp karo likh salam"),
                Cmd("[naam] ko SMS karo likh [msg]", "SMS bhejega", "Ammi ko SMS karo likh aa raha hoon")
            ),
            "Quick Actions" to listOf(
                Cmd("torch on", "Flashlight on", "torch on"),
                Cmd("torch off", "Flashlight off", "torch off"),
                Cmd("screenshot", "Screenshot lega", "screenshot"),
                Cmd("screen lock", "Phone lock", "screen lock"),
                Cmd("home jao", "Home screen", "home jao"),
                Cmd("wapas jao", "Back", "wapas jao"),
                Cmd("scroll down", "Neeche scroll", "scroll down"),
                Cmd("scroll up", "Upar scroll", "scroll up"),
                Cmd("notification", "Notifications", "notification"),
                Cmd("recent apps", "Background apps", "recent apps")
            ),
            "Volume & Sound" to listOf(
                Cmd("volume badhao", "Volume barhega", "volume badhao"),
                Cmd("volume kam", "Volume ghategha", "volume kam"),
                Cmd("mute karo", "Mute", "mute karo"),
                Cmd("unmute", "Sound on", "unmute")
            ),
            "Malumat" to listOf(
                Cmd("battery", "Battery % batayega", "battery"),
                Cmd("time batao", "Abhi ka waqt", "time batao"),
                Cmd("date kya", "Aaj ki tarikh", "date kya"),
                Cmd("weather", "Mausam ki khabar", "weather"),
                Cmd("screen par kya", "Screen content padhega", "screen par kya")
            ),
            "Notes & Search" to listOf(
                Cmd("note karo: [baat]", "Note save karega", "note karo: Kal meeting hai"),
                Cmd("meri notes", "Saari notes sunayega", "meri notes"),
                Cmd("google karo [query]", "Google search", "google karo Pakistan weather")
            ),
            "Type & Click" to listOf(
                Cmd("type karo [text]", "Field mein type karega", "type karo Hello"),
                Cmd("click karo [text]", "Us text par click", "click karo Send"),
                Cmd("help", "Tamam commands sunayega", "help")
            )
        )

        for ((catName, cmds) in categories) {
            helpContainer.addView(TextView(this).apply {
                text = catName
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#7B2FBE"))
                setPadding(dp * 2, dp * 14, 0, dp * 6)
            })
            for (cmd in cmds) {
                val fillVal = cmd.fill
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp * 10, dp * 9, dp * 10, dp * 9)
                    background = GradientDrawable().also {
                        it.cornerRadius = dp * 7f
                        it.setColor(Color.parseColor("#0D0D1A"))
                        it.setStroke(1, Color.parseColor("#16163A"))
                    }
                    setOnClickListener {
                        cmdInput.setText(fillVal)
                        cmdInput.setSelection(fillVal.length)
                        scroll.smoothScrollTo(0, 0)
                    }
                }
                row.addView(TextView(this).apply {
                    text = cmd.label
                    textSize = 12f
                    setTextColor(Color.parseColor("#22D3EE"))
                    typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, -2, 0.5f))
                row.addView(TextView(this).apply {
                    text = cmd.desc
                    textSize = 11f
                    setTextColor(Color.parseColor("#94A3B8"))
                }, LinearLayout.LayoutParams(0, -2, 0.5f))
                helpContainer.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 4 })
            }
        }

        // TTS + ENGINE
        tts = TextToSpeech(this) { status ->
            ttsOk = status == TextToSpeech.SUCCESS
            if (ttsOk) {
                for (loc in listOf(Locale("ur", "PK"), Locale("en", "IN"), Locale.ENGLISH)) {
                    if (tts.setLanguage(loc) >= TextToSpeech.LANG_AVAILABLE) break
                }
            }
        }
        engine = CommandEngine(this, tts,
            onResponse = { msg -> runOnUiThread { outputTv.text = msg; statusTv.text = "Tayyar" } },
            onAppOpen  = { app -> runOnUiThread { statusTv.text = "$app khul raha hai..." } }
        )

        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
        registerReceiver(accReceiver, IntentFilter("com.voiceai3.ACCESSIBILITY_CONNECTED"))
    }

    override fun onResume() { super.onResume(); updateAccStatus() }

    private fun updateAccStatus() {
        val on = VoiceAccessibilityService.instance != null
        runOnUiThread {
            accPill.text = if (on)
                "Accessibility Active — Sab commands kaam karein ge"
            else
                "Accessibility OFF — Tap karein enable karne ke liye"
            accPill.setTextColor(if (on) Color.parseColor("#34D399") else Color.parseColor("#F59E0B"))
            (accPill.background as? GradientDrawable)?.apply {
                setColor(if (on) Color.parseColor("#064E3B") else Color.parseColor("#451A03"))
                setStroke(1, if (on) Color.parseColor("#34D399") else Color.parseColor("#F59E0B"))
            }
        }
    }

    private fun toggleHelp() {
        helpVisible = !helpVisible
        helpContainer.visibility = if (helpVisible) View.VISIBLE else View.GONE
    }

    private fun sendTyped() {
        val txt = cmdInput.text.toString().trim()
        if (txt.isEmpty()) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(cmdInput.windowToken, 0)
        recognizedTv.text = "Typed: $txt"
        outputTv.text = "..."
        engine.process(txt)
        cmdInput.setText("")
    }

    private fun startListen() {
        if (!ttsOk) { statusTv.text = "TTS tayyar nahi"; return }
        statusTv.text = "Sun raha hoon..."
        micBtn.text = "SUN\nRAHA\nHOON..."
        micBtn.background = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.setColor(Color.parseColor("#DC2626"))
        }
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val txt = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                recognizedTv.text = "Suna: $txt"
                outputTv.text = "..."
                engine.process(txt)
                resetBtn()
            }
            override fun onError(e: Int) {
                statusTv.text = when (e) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Samjha nahi, dobara bolein"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz nahi aayi"
                    SpeechRecognizer.ERROR_NETWORK -> "Internet chahiye"
                    else -> "Dobara bolein"
                }
                resetBtn()
            }
            override fun onReadyForSpeech(p: Bundle?) { statusTv.text = "Bol sakte hain..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { statusTv.text = "Samajh raha hoon..." }
            override fun onPartialResults(p: Bundle?) {
                val part = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (part.isNotEmpty()) runOnUiThread { recognizedTv.text = "Suna: $part..." }
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

    private fun resetBtn() {
        runOnUiThread {
            micBtn.text = "TAP\nKAR KE\nBOLEIN"
            micBtn.background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
            statusTv.text = "Tayyar — bol ya likh sakte hain"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sr?.destroy(); tts.shutdown()
        try { unregisterReceiver(accReceiver) } catch (e: Exception) {}
    }
}
