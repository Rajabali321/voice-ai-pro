package com.voiceai3

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VoiceAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            sendBroadcast(Intent("com.voiceai3.ACCESSIBILITY_CONNECTED"))
        } catch (e: Exception) { /* ignore */ }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun goHome()          = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack()          = performGlobalAction(GLOBAL_ACTION_BACK)
    fun showRecents()     = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    fun scrollDown() = scrollNode(rootInActiveWindow, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    fun scrollUp()   = scrollNode(rootInActiveWindow, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

    private fun scrollNode(node: AccessibilityNodeInfo?, action: Int) {
        node ?: return
        if (node.isScrollable) { node.performAction(action); return }
        for (i in 0 until node.childCount) scrollNode(node.getChild(i), action)
    }

    // ── Screen Text Extraction (used by Live Translate feature) ─
    /**
     * Walk the accessibility tree of the current screen and collect all
     * visible text + content descriptions. Returns them as one string.
     */
    fun getScreenText(): String {
        val sb = StringBuilder()
        fun collect(node: AccessibilityNodeInfo?) {
            node ?: return
            val txt  = node.text?.toString()
            val desc = node.contentDescription?.toString()
            if (!txt.isNullOrBlank())  sb.append(txt).append(". ")
            if (!desc.isNullOrBlank() && desc != txt) sb.append(desc).append(". ")
            for (i in 0 until node.childCount) collect(node.getChild(i))
        }
        collect(rootInActiveWindow)
        return sb.toString().trim()
    }
}
