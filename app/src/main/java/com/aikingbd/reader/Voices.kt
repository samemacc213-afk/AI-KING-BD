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

    // Voices for the voice-message maker (Sarvam AI, Bengali, female)
    val msgList = listOf(
        VoiceOption("priya", "Voice 1 • Priya"),
        VoiceOption("ishita", "Voice 2 • Ishita"),
        VoiceOption("roopa", "Voice 3 • Roopa"),
        VoiceOption("pooja", "Voice 4 • Pooja"),
        VoiceOption("simran", "Voice 5 • Simran"),
        VoiceOption("suhani", "Voice 6 • Suhani"),
        VoiceOption("shreya", "Voice 7 • Shreya"),
        VoiceOption("rupali", "Voice 8 • Rupali"),
        VoiceOption("neha", "Voice 9 • Neha"),
        VoiceOption("ritu", "Voice 10 • Ritu")
    )

    fun findMsg(id: String): VoiceOption = msgList.firstOrNull { it.id == id } ?: msgList[0]
}
