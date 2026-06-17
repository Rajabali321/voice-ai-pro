package com.voiceai3

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VoiceAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "voice_ai_channel", "Voice AI Service", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Voice AI background service" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
