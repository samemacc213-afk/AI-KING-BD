package com.aikingbd.reader

import android.app.Application

class AIKingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdKick.init(this)
    }
}
