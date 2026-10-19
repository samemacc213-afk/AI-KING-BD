package com.aikingbd.reader

object AdIds {
    // true = Google's safe test ads, false = your real ads
    const val USE_TEST_ADS = false

    private const val APP_OPEN_REAL = "ca-app-pub-4455023088168031/5892222901"
    private const val BANNER_REAL = "ca-app-pub-4455023088168031/7516190566"
    private const val INTERSTITIAL_REAL = "ca-app-pub-4455023088168031/2360066803"
    private const val REWARDED_REAL = "ca-app-pub-4455023088168031/4790789026"

    private const val APP_OPEN_TEST = "ca-app-pub-3940256099942544/9257395921"
    private const val BANNER_TEST = "ca-app-pub-3940256099942544/9214589741"
    private const val INTERSTITIAL_TEST = "ca-app-pub-3940256099942544/1033173712"
    private const val REWARDED_TEST = "ca-app-pub-3940256099942544/5224354917"

    fun appOpen(): String = if (USE_TEST_ADS) APP_OPEN_TEST else APP_OPEN_REAL
    fun banner(): String = if (USE_TEST_ADS) BANNER_TEST else BANNER_REAL
    fun interstitial(): String = if (USE_TEST_ADS) INTERSTITIAL_TEST else INTERSTITIAL_REAL
    fun rewarded(): String = if (USE_TEST_ADS) REWARDED_TEST else REWARDED_REAL
}
