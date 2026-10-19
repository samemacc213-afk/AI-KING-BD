package com.aikingbd.reader

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallFlashService : NotificationListenerService() {

    private val callApps = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.imo.android.imoim", "com.imo.android.imoimbeta",
        "com.facebook.orca", "com.facebook.mlite"
    )
    private val ringing = mutableSetOf<String>()
    private var phoneRinging = false
    private var receiver: BroadcastReceiver? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!Prefs(c).flashOn) return
                if (i.getStringExtra(TelephonyManager.EXTRA_STATE) == TelephonyManager.EXTRA_STATE_RINGING) {
                    phoneRinging = true
                    Flash.continuous(c)
                } else {
                    phoneRinging = false
                    if (ringing.isEmpty()) Flash.stop()
                }
            }
        }
        receiver = r
        try {
            ContextCompat.registerReceiver(
                this, r, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        receiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (!prefs.flashOn || sbn.packageName == packageName) return
        val n = sbn.notification ?: return

        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val chrono = n.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        val hasFull = n.fullScreenIntent != null
        val isCall = (n.category == Notification.CATEGORY_CALL && (hasFull || ongoing)) ||
            (sbn.packageName in callApps && hasFull)

        if (isCall) {
            if (chrono) {
                ringing.remove(sbn.key)
                if (ringing.isEmpty() && !phoneRinging) Flash.stop()
            } else {
                ringing.add(sbn.key)
                Flash.continuous(this)
            }
            return
        }

        val smsPkg = try { Telephony.Sms.getDefaultSmsPackage(this) } catch (_: Exception) { null }
        val isSms = smsPkg != null && sbn.packageName == smsPkg &&
            (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        if (isSms && ringing.isEmpty() && !phoneRinging) Flash.blink(this, 4)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (ringing.remove(sbn.key) && ringing.isEmpty() && !phoneRinging) Flash.stop()
    }
}
