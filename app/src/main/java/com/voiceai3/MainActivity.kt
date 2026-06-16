package com.voiceai3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var commandEngine: CommandEngine
    private lateinit var transcriptView: TextView
    private lateinit var responseView: TextView
    private lateinit var micButton: LinearLayout
    private lateinit var micIcon: TextView
    private lateinit var historyLayout: LinearLayout
    private lateinit var accessibilityBanner: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var pulseRing: View
    private var isListening = false
    private var isMuted = false
    private val handler = Handler(Looper.getMainLooper())
    private val history = mutableListOf<Pair<String,String>>()

    private val BG = Color.parseColor("#0A0A0F")
    private val CARD = Color.parseColor("#12121A")
    private val PURPLE = Color.parseColor("#7B2FBE")
    private val PURPLE_L = Color.parseColor("#A855F7")
    private val CYAN = Color.parseColor("#06B6D4")
    private val GREEN = Color.parseColor("#10B981")
    private val RED = Color.parseColor("#EF4444")
    private val TEXT = Color.WHITE
    private val SUB = Color.parseColor("#9CA3AF")
    private val ACCENT = Color.parseColor("#1E1B2E")
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    private val PERMS = arrayOf(
        Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA, Manifest.permission.READ_PHONE_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG; window.navigationBarColor = BG
        buildUI(); initTTS()
        val missing = PERMS.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        checkAccessibility()
    }

    override fun onResume() { super.onResume(); checkAccessibility() }
    override fun onDestroy() { super.onDestroy(); speechRecognizer?.destroy(); if(::tts.isInitialized) tts.shutdown() }

    private fun buildUI() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(80)) }
        scroll.addView(root); setContentView(scroll)

        // Header
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(8),0,dp(20)) }
        val logo = TextView(this).apply { text = "⚡ Voice AI Pro"; textSize = 20f; setTextColor(PURPLE_L); typeface = android.graphics.Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP).apply { weight=1f } }
        val sc = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10),dp(10)).apply{marginEnd=dp(6)}; background = circle(SUB) }
        statusText = TextView(this).apply { text = "Ready"; textSize = 12f; setTextColor(SUB) }
        val muteBtn = TextView(this).apply { text = "🔊"; textSize = 18f; setPadding(dp(12),dp(6),dp(12),dp(6)); setOnClickListener { isMuted=!isMuted; text=if(isMuted)"🔇" else "🔊"; if(isMuted) tts.stop() } }
        sc.addView(statusDot); sc.addView(statusText); hdr.addView(logo); hdr.addView(sc); hdr.addView(muteBtn); root.addView(hdr)

        // Accessibility banner
        accessibilityBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A0A00")); setPadding(dp(14),dp(12),dp(14),dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH,WRAP).apply{bottomMargin=dp(12)}
            visibility = View.GONE
        }
        val bannerText = TextView(this).apply { text = "⚠ Accessibility Service band hai — Full control ke liye enable karein"; textSize=12f; setTextColor(Color.parseColor("#FCD34D")); layoutParams = LinearLayout.LayoutParams(0,WRAP).apply{weight=1f} }
        val enableBtn = TextView(this).apply { text="Enable"; textSize=12f; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#D97706")); setPadding(dp(10),dp(6),dp(10),dp(6)); setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK}) } }
        accessibilityBanner.addView(bannerText); accessibilityBanner.addView(enableBtn); root.addView(accessibilityBanner)

        // Transcript card
        val tc = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(CARD); setPadding(dp(16),dp(14),dp(16),dp(14)); layoutParams=LinearLayout.LayoutParams(MATCH,WRAP).apply{bottomMargin=dp(8)} }
        val tl = TextView(this).apply { text="TRANSCRIPT"; textSize=10f; setTextColor(SUB); letterSpacing=0.15f; setPadding(0,0,0,dp(6)) }
        transcriptView = TextView(this).apply { text="Mic dabao aur bolein..."; textSize=17f; setTextColor(SUB); maxLines=4; ellipsize=TextUtils.TruncateAt.END }
        tc.addView(tl); tc.addView(transcriptView); root.addView(tc)

        // Response card
        val rc = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(ACCENT); setPadding(dp(16),dp(14),dp(16),dp(14)); layoutParams=LinearLayout.LayoutParams(MATCH,WRAP).apply{bottomMargin=dp(20)} }
        val rl = TextView(this).apply { text="RESPONSE"; textSize=10f; setTextColor(CYAN); letterSpacing=0.15f; setPadding(0,0,0,dp(6)) }
        responseView = TextView(this).apply { text="Level 3 tayyar hai!"; textSize=15f; setTextColor(TEXT) }
        rc.addView(rl); rc.addView(responseView); root.addView(rc)

        // Mic button
        val mc = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(0,dp(8),0,dp(24)) }
        pulseRing = View(this).apply { val s=dp(100); layoutParams=LinearLayout.LayoutParams(s,s).apply{gravity=Gravity.CENTER}; background=circle(Color.parseColor("#3D1A7A")); alpha=0f }
        micButton = LinearLayout(this).apply { val s=dp(80); layoutParams=LinearLayout.LayoutParams(s,s).apply{gravity=Gravity.CENTER; setMargins(-dp(90),-dp(10),0,0)}; background=circle(PURPLE); gravity=Gravity.CENTER; elevation=dp(8).toFloat(); isClickable=true; isFocusable=true }
        micIcon = TextView(this).apply { text="🎤"; textSize=28f; gravity=Gravity.CENTER }
        micButton.addView(micIcon); micButton.setOnClickListener { if(isListening) stopListening() else startListening() }
        micButton.setOnTouchListener { v, e -> when(e.action){ MotionEvent.ACTION_DOWN->v.animate().scaleX(.92f).scaleY(.92f).setDuration(100).start(); MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->v.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }; false }
        val hint = TextView(this).apply { text="Tap to speak"; textSize=12f; setTextColor(SUB); gravity=Gravity.CENTER; setPadding(0,dp(14),0,0) }
        mc.addView(pulseRing); mc.addView(micButton); mc.addView(hint); root.addView(mc)

        // Quick actions grid
        val quickLabel = TextView(this).apply { text="Quick Commands"; textSize=13f; setTextColor(SUB); setPadding(0,0,0,dp(10)) }
        root.addView(quickLabel)
        val cmds = listOf("🔋 Battery" to "battery kitni hai","🌤 Weather" to "weather kya hai","💬 WhatsApp" to "whatsapp kholo","📷 Camera" to "camera kholo","🏠 Home" to "home jao","⬅ Back" to "wapas jao","📸 Screenshot" to "screenshot lo","🔦 Torch" to "torch on karo","🔕 Mute" to "mute karo","📝 Notes" to "notes dikhao")
        var row: LinearLayout? = null
        cmds.forEachIndexed { i, (lbl, cmd) ->
            if (i%2==0) { row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; layoutParams=LinearLayout.LayoutParams(MATCH,WRAP).apply{bottomMargin=dp(6)} }; root.addView(row) }
            val btn = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setBackgroundColor(CARD); setPadding(dp(6),dp(12),dp(6),dp(12)); layoutParams=LinearLayout.LayoutParams(0,WRAP).apply{weight=1f; if(i%2==0) marginEnd=dp(4) else marginStart=dp(4)}; isClickable=true; isFocusable=true }
            btn.setOnClickListener { transcriptView.text=cmd; if(::commandEngine.isInitialized) commandEngine.processCommand(cmd) }
            btn.addView(TextView(this).apply{text=lbl.split(" ")[0]; textSize=22f; gravity=Gravity.CENTER})
            btn.addView(TextView(this).apply{text=lbl.substring(lbl.indexOf(" ")+1); textSize=10f; setTextColor(SUB); gravity=Gravity.CENTER; setPadding(0,dp(4),0,0)})
            row!!.addView(btn)
        }

        // History
        root.addView(View(this).apply{layoutParams=LinearLayout.LayoutParams(MATCH,dp(20))})
        root.addView(TextView(this).apply{text="Command History"; textSize=13f; setTextColor(SUB); setPadding(0,0,0,dp(10))})
        historyLayout = LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(historyLayout)
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ur = tts.setLanguage(Locale("ur","PK"))
                if (ur == TextToSpeech.LANG_MISSING_DATA || ur == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts.setLanguage(Locale("en","IN"))
                tts.setSpeechRate(0.88f)
                commandEngine = CommandEngine(this, tts, { msg -> runOnUiThread { showResponse(msg) } }, { app -> runOnUiThread { setStatus("Opening $app...", CYAN) } })
                handler.postDelayed({ tts.speak("Voice AI Level 3 tayyar hai", TextToSpeech.QUEUE_FLUSH, null, "w"); showResponse("Level 3 tayyar hai! Mic dabao aur command bolein") }, 500)
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { showResponse("Voice recognition available nahi hai"); return }
        isListening = true
        micButton.background = circle(RED); micIcon.text = "⏹"
        setStatus("Sun raha hoon...", GREEN)
        pulseRing.animate().alpha(0.5f).scaleX(1.3f).scaleY(1.3f).setDuration(600).start()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { val s=1f+(rms/100f).coerceIn(0f,.3f); runOnUiThread{micButton.scaleX=s; micButton.scaleY=s} }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { runOnUiThread{setStatus("Processing...", CYAN)} }
            override fun onError(error: Int) { runOnUiThread { stopListening(); setStatus(if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Mic permission chahiye!" else "Dobara bolein (err $error)", RED); handler.postDelayed({setStatus("Ready",SUB)},2000) } }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                runOnUiThread { stopListening(); if(text.isNotBlank()){ transcriptView.text=text; transcriptView.setTextColor(TEXT); if(::commandEngine.isInitialized) commandEngine.processCommand(text) }; setStatus("Ready",SUB) }
            }
            override fun onPartialResults(p: Bundle?) { val t=p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:return; runOnUiThread{transcriptView.text="$t..."} }
            override fun onEvent(e: Int, p: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { speechRecognizer?.startListening(intent) } catch(e: Exception) { intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-IN"); speechRecognizer?.startListening(intent) }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        micButton.background = circle(PURPLE); micIcon.text = "🎤"; micButton.scaleX=1f; micButton.scaleY=1f
        pulseRing.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(300).start()
    }

    private fun showResponse(msg: String) {
        responseView.text = msg
        val cmd = transcriptView.text.toString()
        if (cmd != "Mic dabao aur bolein..." && history.size < 20) { history.add(0, Pair(cmd, msg)); updateHistory() }
    }

    private fun updateHistory() {
        historyLayout.removeAllViews()
        history.take(5).forEach { (cmd, resp) ->
            val item = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(CARD); setPadding(dp(14),dp(12),dp(14),dp(12)); layoutParams=LinearLayout.LayoutParams(MATCH,WRAP).apply{bottomMargin=dp(6)} }
            item.addView(TextView(this).apply{text="▶ $cmd"; textSize=13f; setTextColor(PURPLE_L)})
            item.addView(TextView(this).apply{text=resp; textSize=12f; setTextColor(SUB); setPadding(0,dp(4),0,0)})
            historyLayout.addView(item)
        }
    }

    private fun setStatus(t: String, color: Int) { statusText.text=t; statusText.setTextColor(color); statusDot.background=circle(color) }

    private fun checkAccessibility() {
        val enabled = try {
            val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            s.contains(packageName, ignoreCase = true) || VoiceAccessibilityService.instance != null
        } catch(e: Exception) { false }
        accessibilityBanner.visibility = if(enabled) View.GONE else View.VISIBLE
    }

    private fun circle(color: Int) = android.graphics.drawable.GradientDrawable().apply { shape=android.graphics.drawable.GradientDrawable.OVAL; setColor(color) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
