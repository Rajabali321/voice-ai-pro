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
    private var dp = 0
    private lateinit var scroll: ScrollView
    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private lateinit var engine: CommandEngine
    private lateinit var outputTv: TextView
    private lateinit var recognizedTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var micBtn: TextView
    private lateinit var accPill: TextView
    private lateinit var cmdInput: EditText
    private lateinit var helpBody: LinearLayout
    private var ttsOk = false
    private var helpOpen = false

    private val accReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateAcc() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        dp = resources.displayMetrics.density.toInt()
        scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp * 16, dp * 24, dp * 16, dp * 32)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Voice AI Pro"
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, lp(-1, -2, 0, dp * 4))

        root.addView(TextView(this).apply {
            text = "v3.0 - Bol ya Type karein"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280")); gravity = Gravity.CENTER
        }, lp(-1, -2, 0, dp * 16))

        accPill = TextView(this).apply {
            text = "Accessibility check ho raha hai..."
            textSize = 12f; gravity = Gravity.CENTER
            setPadding(dp * 12, dp * 8, dp * 12, dp * 8)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 20f
                it.setColor(Color.parseColor("#1A1A2E"))
            }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(accPill, lp(-1, -2, 0, dp * 16))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp * 14, dp * 12, dp * 14, dp * 12)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 10f
                it.setColor(Color.parseColor("#111120"))
                it.setStroke(1, Color.parseColor("#1E1E3A"))
            }
        }
        recognizedTv = TextView(this).apply {
            text = "Awaaz ya text ka intezaar..."
            textSize = 11f; setTextColor(Color.parseColor("#6B7280"))
        }
        outputTv = TextView(this).apply {
            text = "Mic tap karein ya neechay type karein"
            textSize = 14f; setTextColor(Color.WHITE); lineSpacingMultiplier = 1.4f
        }
        card.addView(recognizedTv, lp(-1, -2, 0, dp * 6))
        card.addView(outputTv)
        root.addView(card, lp(-1, -2, 0, dp * 20))

        micBtn = TextView(this).apply {
            text = "TAP KAR KE\nBOLEIN"
            textSize = 15f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
        }
        root.addView(micBtn, LinearLayout.LayoutParams(dp * 180, dp * 180).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp * 8
        })
        micBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> startListen()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sr?.stopListening()
            }
            true
        }

        statusTv = TextView(this).apply {
            text = "Tayyar - bol ya type karo"
            textSize = 12f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
        }
        root.addView(statusTv, lp(-1, -2, 0, dp * 20))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        cmdInput = EditText(this).apply {
            hint = "Command type karo..."
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp * 12, dp * 12, dp * 12, dp * 12)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 10f
                it.setColor(Color.parseColor("#111120"))
                it.setStroke(1, Color.parseColor("#2D2D5A"))
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false
            }
        }
        val sendBtn = TextView(this).apply {
            text = ">"; textSize = 20f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
            setOnClickListener { doSend() }
        }
        inputRow.addView(cmdInput, LinearLayout.LayoutParams(0, -2, 1f).also { it.rightMargin = dp * 10 })
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp * 52, dp * 52))
        root.addView(inputRow, lp(-1, -2, 0, dp * 28))

        root.addView(TextView(this).apply {
            text = "Commands Guide - Tap karein"
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A78BFA")); gravity = Gravity.CENTER
            setPadding(dp * 12, dp * 12, dp * 12, dp * 12)
            background = GradientDrawable().also {
                it.cornerRadius = dp * 8f
                it.setColor(Color.parseColor("#111120"))
                it.setStroke(1, Color.parseColor("#2D2D5A"))
            }
            setOnClickListener {
                helpOpen = !helpOpen
                helpBody.visibility = if (helpOpen) View.VISIBLE else View.GONE
            }
        }, lp(-1, -2, 0, dp * 8))

        helpBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
        }
        root.addView(helpBody)
        buildHelp()

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
        val need = listOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
        registerReceiver(accReceiver, IntentFilter("com.voiceai3.ACCESSIBILITY_CONNECTED"))
    }

    private fun buildHelp() {
        val items = listOf(
            "APPS KHOLO" to "", "WhatsApp kholo" to "WhatsApp kholo",
            "YouTube kholo" to "YouTube kholo", "camera kholo" to "camera kholo",
            "Instagram kholo" to "Instagram kholo", "maps kholo" to "maps kholo",
            "settings kholo" to "settings kholo",
            "CALL & MSG" to "", "Ahmad ko call karo" to "Ahmad ko call karo",
            "Ali ko WhatsApp karo likh salam" to "Ali ko WhatsApp karo likh salam",
            "Ammi ko SMS karo likh aa raha hoon" to "Ammi ko SMS karo likh aa raha hoon",
            "QUICK ACTIONS" to "", "torch on" to "torch on", "torch off" to "torch off",
            "screenshot" to "screenshot", "home jao" to "home jao",
            "wapas jao" to "wapas jao", "screen lock" to "screen lock",
            "scroll down" to "scroll down", "scroll up" to "scroll up",
            "notification" to "notification", "recent apps" to "recent apps",
            "VOLUME" to "", "volume badhao" to "volume badhao",
            "volume kam" to "volume kam", "mute karo" to "mute karo",
            "INFO" to "", "battery" to "battery", "time batao" to "time batao",
            "date kya" to "date kya", "weather" to "weather",
            "NOTES & SEARCH" to "",
            "note karo: kal meeting hai" to "note karo: kal meeting hai",
            "meri notes" to "meri notes",
            "google karo Pakistan news" to "google karo Pakistan news",
            "TYPE & CLICK" to "", "type karo Hello" to "type karo Hello",
            "click karo Send" to "click karo Send", "help" to "help"
        )
        for ((label, fill) in items) {
            if (fill.isEmpty()) {
                helpBody.addView(TextView(this).apply {
                    text = label; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#7B2FBE"))
                    setPadding(dp * 4, dp * 12, 0, dp * 4)
                })
            } else {
                val f = fill
                helpBody.addView(TextView(this).apply {
                    text = label; textSize = 13f
                    setTextColor(Color.parseColor("#22D3EE"))
                    setPadding(dp * 12, dp * 8, dp * 12, dp * 8)
                    background = GradientDrawable().also {
                        it.cornerRadius = dp * 6f
                        it.setColor(Color.parseColor("#0D0D1A"))
                        it.setStroke(1, Color.parseColor("#16163A"))
                    }
                    setOnClickListener {
                        cmdInput.setText(f)
                        cmdInput.setSelection(f.length)
                        scroll.smoothScrollTo(0, 0)
                    }
                }, lp(-1, -2, 0, dp * 4))
            }
        }
    }

    override fun onResume() { super.onResume(); updateAcc() }

    private fun updateAcc() {
        val on = VoiceAccessibilityService.instance != null
        runOnUiThread {
            if (on) {
                accPill.text = "Accessibility Active - Sab commands kaam karein ge"
                accPill.setTextColor(Color.parseColor("#34D399"))
                (accPill.background as? GradientDrawable)?.setColor(Color.parseColor("#064E3B"))
            } else {
                accPill.text = "Accessibility OFF - Tap karein enable karne ke liye"
                accPill.setTextColor(Color.parseColor("#F59E0B"))
                (accPill.background as? GradientDrawable)?.setColor(Color.parseColor("#451A03"))
            }
        }
    }

    private fun doSend() {
        val txt = cmdInput.text.toString().trim()
        if (txt.isEmpty()) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(cmdInput.windowToken, 0)
        recognizedTv.text = "Type: $txt"
        outputTv.text = "..."
        engine.process(txt)
        cmdInput.setText("")
    }

    private fun startListen() {
        if (!ttsOk) { statusTv.text = "TTS tayyar nahi"; return }
        statusTv.text = "Sun raha hoon..."
        micBtn.text = "SUN RAHA\nHOON..."
        micBtn.background = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.setColor(Color.parseColor("#DC2626"))
        }
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val txt = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
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
                val part = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
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
            micBtn.text = "TAP KAR KE\nBOLEIN"
            micBtn.background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
            statusTv.text = "Tayyar - bol ya type karo"
        }
    }

    private fun lp(w: Int, h: Int, top: Int = 0, bot: Int = 0) =
        LinearLayout.LayoutParams(w, h).also { it.topMargin = top; it.bottomMargin = bot }

    override fun onDestroy() {
        super.onDestroy()
        sr?.destroy(); tts.shutdown()
        try { unregisterReceiver(accReceiver) } catch (e: Exception) {}
    }
}
