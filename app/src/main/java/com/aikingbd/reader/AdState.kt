package com.aikingbd.reader

object AdState {
    @Volatile var showing = false
    @Volatile var lastClosedAt = 0L

    fun closed() {
        showing = false
        lastClosedAt = System.currentTimeMillis()
    }
}
