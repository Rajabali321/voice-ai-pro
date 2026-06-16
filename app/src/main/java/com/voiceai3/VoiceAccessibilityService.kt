package com.voiceai3

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT

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
            val scrollable = findScrollableNode(root)
            scrollable?.performAction(ACTION_SCROLL_FORWARD)
            scrollable?.recycle(); root.recycle()
        }
    }

    fun scrollUp(times: Int = 1) {
        repeat(times) {
            val root = rootInActiveWindow ?: return@repeat
            val scrollable = findScrollableNode(root)
            scrollable?.performAction(ACTION_SCROLL_BACKWARD)
            scrollable?.recycle(); root.recycle()
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        root.recycle()
        if (nodes.isNullOrEmpty()) return false
        val clicked = clickNodeOrParent(nodes[0])
        nodes.forEach { it.recycle() }
        return clicked
    }

    fun clickByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val result = findAndClickByDescription(root, desc)
        root.recycle()
        return result
    }

    private fun findAndClickByDescription(node: AccessibilityNodeInfo, desc: String): Boolean {
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (nodeDesc.contains(desc, ignoreCase = true) && node.isClickable) {
            node.performAction(ACTION_CLICK); return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickByDescription(child, desc)) { child.recycle(); return true }
            child.recycle()
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) { node.performAction(ACTION_CLICK); return true }
        val parent = node.parent ?: return false
        val result = clickNodeOrParent(parent)
        parent.recycle()
        return result
    }

    fun tapAtCoordinates(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300) {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var inputNode = findFocusedInput(root) ?: findEditableNode(root)
        root.recycle()
        if (inputNode == null) return false
        inputNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        handler.postDelayed({
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            inputNode.performAction(ACTION_SET_TEXT, args)
            inputNode.recycle()
        }, 200)
        return true
    }

    fun typeViaClipboard(text: String): Boolean {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("voice_input", text))
        val root = rootInActiveWindow ?: return false
        val inputNode = findFocusedInput(root) ?: findEditableNode(root)
        root.recycle()
        if (inputNode == null) return false
        inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        inputNode.recycle()
        return true
    }

    fun readScreenContent(): String {
        val root = rootInActiveWindow ?: return ""
        val text = getAllText(root); root.recycle(); return text
    }

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(getAllText(child)); child.recycle()
        }
        return sb.toString()
    }

    private fun findFocusedInput(node: AccessibilityNodeInfo) =
        node.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child)
            if (result != null) { child.recycle(); return result }
            child.recycle()
        }
        return null
    }

    fun sendWhatsAppMessage(contactName: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) { openApp("com.whatsapp") }

        handler.postDelayed({ clickByDescription("Search") || clickByText("Search") }, 1500)
        handler.postDelayed({ typeText(contactName) }, 2500)
        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            val contacts = root.findAccessibilityNodeInfosByText(contactName)
            if (!contacts.isNullOrEmpty()) {
                clickNodeOrParent(contacts[0])
                contacts.forEach { it.recycle() }
            }
            root.recycle()
        }, 4000)
        handler.postDelayed({ typeText(message) }, 5500)
        handler.postDelayed({ clickByDescription("Send") || clickByText("Send") }, 6500)
    }

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent); true
        } catch (e: Exception) { false }
    }

    fun findAppByName(appName: String): String? {
        val lower = appName.lowercase().trim()
        val knownApps = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "camera" to "com.android.camera2",
            "settings" to "com.android.settings",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "gallery" to "com.google.android.apps.photos",
            "photos" to "com.google.android.apps.photos",
            "contacts" to "com.google.android.contacts",
            "phone" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "play store" to "com.android.vending",
            "netflix" to "com.netflix.mediaclient",
            "spotify" to "com.spotify.music",
            "tiktok" to "com.zhiliaoapp.musically",
            "twitter" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "zoom" to "us.zoom.videomeetings",
            "notes" to "com.google.android.keep",
            "keep" to "com.google.android.keep"
        )
        knownApps[lower]?.let { return it }
        val apps = packageManager.getInstalledApplications(0)
        for (app in apps) {
            val label = packageManager.getApplicationLabel(app).toString().lowercase()
            if (label.contains(lower) || lower.contains(label)) return app.packageName
        }
        return null
    }
}
