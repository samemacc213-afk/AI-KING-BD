package com.aikingbd.reader

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds

class AIKingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {
            Handler(Looper.getMainLooper()).post { AppOpenManager.preload(this) }
        }
    }
}
