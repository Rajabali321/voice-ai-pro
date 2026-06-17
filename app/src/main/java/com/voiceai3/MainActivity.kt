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
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private lateinit var engine: CommandEngine
    private lateinit var outputTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var micBtn: TextView
    private var ttsOk = false

    private val accReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateBadge() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val dp = resources.displayMetrics.density.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp * 20, dp * 40, dp * 20, dp * 20)
        }
        setContentView(root)

        root.addView(TextView(this).apply {
            text = "Voice AI"; textSize = 28f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Awaaz se apna phone control karein"
            textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
            setPadding(0, dp * 6, 0, dp * 20)
        })

        outputTv = TextView(this).apply {
            text = "Yahan awaaz ki samajh aayegi"; textSize = 15f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111120"))
            setPadding(dp * 20, dp * 20, dp * 20, dp * 20)
        }
        root.addView(outputTv, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp * 24 })

        val btnText = "TAP KAR KE\nBOLEIN"

        micBtn = TextView(this).apply {
            text = btnText; textSize = 16f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
        }
        root.addView(micBtn, LinearLayout.LayoutParams(dp * 200, dp * 200).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp * 24
        })

        micBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> startListen()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sr?.stopListening()
            }
            true
        }

        statusTv = TextView(this).apply {
            text = "Tayyar hoon"; textSize = 13f
            setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
        }
        root.addView(statusTv)

        root.addView(TextView(this).apply {
            text = "Accessibility Service Enable Karein"; textSize = 13f
            setTextColor(Color.parseColor("#22D3EE")); gravity = Gravity.CENTER
            setPadding(0, dp * 20, 0, 0)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

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
            onAppOpen  = { app -> runOnUiThread { statusTv.text = app + " khul raha" } }
        )

        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )
        val need = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)

        registerReceiver(accReceiver, IntentFilter("com.voiceai3.ACCESSIBILITY_CONNECTED"))
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
                val txt = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                outputTv.text = "Suna: " + txt
                engine.process(txt)
                resetBtn()
            }
            override fun onError(e: Int) { statusTv.text = "Dobara bolein"; resetBtn() }
            override fun onReadyForSpeech(p: Bundle?) { statusTv.text = "Bol sakte hain..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        sr?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    private fun resetBtn() {
        runOnUiThread {
            micBtn.text = "TAP KAR KE\nBOLEIN"
            micBtn.background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#7B2FBE"))
            }
        }
    }

    private fun updateBadge() {}

    override fun onDestroy() {
        super.onDestroy()
        sr?.destroy(); tts.shutdown()
        unregisterReceiver(accReceiver)
    }
}
