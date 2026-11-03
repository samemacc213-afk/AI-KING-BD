package com.aikingbd.reader
data class VoiceOption(val id: String, val label: String)
object Voices {
    val list = listOf(
        VoiceOption("Kore","Voice 1 \u2022 Kore"), VoiceOption("Aoede","Voice 2 \u2022 Aoede"),
        VoiceOption("Leda","Voice 3 \u2022 Leda"), VoiceOption("Zephyr","Voice 4 \u2022 Zephyr"),
        VoiceOption("Despina","Voice 5 \u2022 Despina"), VoiceOption("Autonoe","Voice 6 \u2022 Autonoe"),
        VoiceOption("Callirrhoe","Voice 7 \u2022 Callirrhoe"), VoiceOption("Erinome","Voice 8 \u2022 Erinome"),
        VoiceOption("Gacrux","Voice 9 \u2022 Gacrux"), VoiceOption("Sulafat","Voice 10 \u2022 Sulafat")
    )
    fun find(id: String) = list.firstOrNull { it.id == id } ?: list[0]
    val msgList = listOf(
        VoiceOption("priya","Voice 1 \u2022 Priya"), VoiceOption("ishita","Voice 2 \u2022 Ishita"),
        VoiceOption("roopa","Voice 3 \u2022 Roopa"), VoiceOption("pooja","Voice 4 \u2022 Pooja"),
        VoiceOption("simran","Voice 5 \u2022 Simran"), VoiceOption("suhani","Voice 6 \u2022 Suhani"),
        VoiceOption("shreya","Voice 7 \u2022 Shreya"), VoiceOption("rupali","Voice 8 \u2022 Rupali"),
        VoiceOption("neha","Voice 9 \u2022 Neha"), VoiceOption("ritu","Voice 10 \u2022 Ritu")
    )
    fun findMsg(id: String) = msgList.firstOrNull { it.id == id } ?: msgList[0]
    val maleMsgList = listOf(
        VoiceOption("abhilash","Voice 1 \u2022 Abhilash"), VoiceOption("karun","Voice 2 \u2022 Karun"),
        VoiceOption("hitesh","Voice 3 \u2022 Hitesh"), VoiceOption("arvind","Voice 4 \u2022 Arvind"),
        VoiceOption("amol","Voice 5 \u2022 Amol"), VoiceOption("arjun","Voice 6 \u2022 Arjun"),
        VoiceOption("ravi","Voice 7 \u2022 Ravi"), VoiceOption("aditya","Voice 8 \u2022 Aditya"),
        VoiceOption("vikram","Voice 9 \u2022 Vikram"), VoiceOption("rahul","Voice 10 \u2022 Rahul")
    )
    fun findMsgMale(id: String) = maleMsgList.firstOrNull { it.id == id } ?: maleMsgList[0]
}
