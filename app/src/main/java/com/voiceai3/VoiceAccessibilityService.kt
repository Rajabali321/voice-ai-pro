package com.voiceai3

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VoiceAccessibilityService? = null
        var currentScreenText = ""
        var currentPackage = ""
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        sendBroadcast(Intent("com.voiceai3.ACCESSIBILITY_CONNECTED"))
    }

    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        currentPackage = event.packageName?.toString() ?: ""
        event.source?.let { node -> currentScreenText = getAllText(node); node.recycle() }
    }

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    fun lockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun scrollDown(times: Int = 1) {
        repeat(times) {
            val root = rootInActiveWindow ?: return@repeat
            findScrollable(root)?.also {
                it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); it.recycle()
            }
            root.recycle()
        }
    }

    fun scrollUp(times: Int = 1) {
        repeat(times) {
            val root = rootInActiveWindow ?: return@repeat
            findScrollable(root)?.also {
                it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); it.recycle()
            }
            root.recycle()
        }
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findScrollable(c)?.let { c.recycle(); return it }
            c.recycle()
        }
        return null
    }

    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        root.recycle()
        if (nodes.isNullOrEmpty()) return false
        val clicked = clickOrParent(nodes[0])
        nodes.forEach { it.recycle() }
        return clicked
    }

    fun clickByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val result = clickDesc(root, desc)
        root.recycle()
        return result
    }

    private fun clickDesc(node: AccessibilityNodeInfo, desc: String): Boolean {
        if ((node.contentDescription?.toString() ?: "").contains(desc, ignoreCase = true) && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (clickDesc(c, desc)) { c.recycle(); return true }
            c.recycle()
        }
        return false
    }

    private fun clickOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
        val p = node.parent ?: return false
        val r = clickOrParent(p); p.recycle(); return r
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)
        root.recycle()
        if (input == null) return false
        input.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        handler.postDelayed({
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            input.recycle()
        }, 200)
        return true
    }

    fun typeViaClipboard(text: String): Boolean {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("v", text))
        val root = rootInActiveWindow ?: return false
        val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)
        root.recycle()
        if (input == null) return false
        input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        input.recycle()
        return true
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findEditable(c)?.let { c.recycle(); return it }
            c.recycle()
        }
        return null
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val t = getAllText(root); root.recycle(); return t
    }

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            sb.append(getAllText(c)); c.recycle()
        }
        return sb.toString()
    }

    fun sendWhatsApp(contactName: String, message: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.whatsapp"); flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) { openApp("com.whatsapp") }
        handler.postDelayed({ clickByDescription("Search") || clickByText("Search") }, 1500)
        handler.postDelayed({ typeText(contactName) }, 2500)
        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            root.findAccessibilityNodeInfosByText(contactName)?.also { nodes ->
                if (nodes.isNotEmpty()) clickOrParent(nodes[0])
                nodes.forEach { it.recycle() }
            }
            root.recycle()
        }, 4000)
        handler.postDelayed({ typeText(message) }, 5500)
        handler.postDelayed({ clickByDescription("Send") || clickByText("Send") }, 6500)
    }

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(intent); true
        } catch (e: Exception) { false }
    }

    fun findApp(name: String): String? {
        val lower = name.lowercase().trim()
        val known = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "settings" to "com.android.settings",
            "gmail" to "com.google.android.gm",
            "camera" to "com.android.camera2",
            "maps" to "com.google.android.apps.maps",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "photos" to "com.google.android.apps.photos",
            "contacts" to "com.google.android.contacts",
            "phone" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "play store" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "tiktok" to "com.zhiliaoapp.musically",
            "zoom" to "us.zoom.videomeetings",
            "notes" to "com.google.android.keep"
        )
        known[lower]?.let { return it }
        packageManager.getInstalledApplications(0).forEach { app ->
            val label = packageManager.getApplicationLabel(app).toString().lowercase()
            if (label.contains(lower) || lower.contains(label)) return app.packageName
        }
        return null
    }
}
