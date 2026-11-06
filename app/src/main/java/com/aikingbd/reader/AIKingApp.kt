package com.aikingbd.reader

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.WeakHashMap

class AIKingApp : Application() {

    private val gates = WeakHashMap<Activity, PinLock>()
    private val handler = Handler(Looper.getMainLooper())

    // Every 5 minutes: if the floating button is on, make sure this phone is still unlocked
    private val gateLoop = object : Runnable {
        override fun run() {
            val app = this@AIKingApp
            if (Prefs(app).enabled) {
                Thread {
                    if (!GateCheck.verify(app)) {
                        handler.post {
                            try {
                                app.stopService(Intent(app, FloatingService::class.java))
                            } catch (e: Exception) {
                            }
                            Prefs(app).enabled = false
                        }
                    }
                }.start()
            }
            handler.postDelayed(this, 5 * 60 * 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    val g = gates.getOrPut(activity) { PinLock(activity, Prefs(activity)) }
                    g.showIfLocked()
                    AdGate.preload(activity)
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                gates.remove(activity)
            }
        })
        handler.postDelayed(gateLoop, 20_000L)
    }
}
