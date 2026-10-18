package com.aikingbd.reader

object AdIds {
    // true = Google's safe test ads (use while testing), false = your real ads
    const val USE_TEST_ADS = false

    private const val APP_OPEN_REAL = "ca-app-pub-4455023088168031/5892222901"
    private const val APP_OPEN_TEST = "ca-app-pub-3940256099942544/9257395921"

    fun appOpen(): String = if (USE_TEST_ADS) APP_OPEN_TEST else APP_OPEN_REAL
}
