package com.aikingbd.reader

data class VoiceOption(val id: String, val label: String)

object Voices {
    val list = listOf(
        VoiceOption("Kore", "Voice 1 • Kore"),
        VoiceOption("Aoede", "Voice 2 • Aoede"),
        VoiceOption("Leda", "Voice 3 • Leda"),
        VoiceOption("Zephyr", "Voice 4 • Zephyr"),
        VoiceOption("Despina", "Voice 5 • Despina"),
        VoiceOption("Autonoe", "Voice 6 • Autonoe"),
        VoiceOption("Callirrhoe", "Voice 7 • Callirrhoe"),
        VoiceOption("Erinome", "Voice 8 • Erinome"),
        VoiceOption("Gacrux", "Voice 9 • Gacrux"),
        VoiceOption("Sulafat", "Voice 10 • Sulafat")
    )

    fun find(id: String): VoiceOption = list.firstOrNull { it.id == id } ?: list[0]
}
