package com.aikingbd.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Keeps the microphone allowed while you talk over another app.
class VoiceRecordService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val channelId = "aikb_rec"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Voice recording", NotificationManager.IMPORTANCE_LOW)
            )
            val n: Notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_float_button)
                .setContentTitle("AI KING BD")
                .setContentText("Recording your voice...")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(2, n)
            }
        } catch (e: Exception) {
            VoiceCapture.abort(this, "Could not keep the microphone on")
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
