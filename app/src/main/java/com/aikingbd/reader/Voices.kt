package com.aikingbd.reader
data class VoiceOption(val id: String, val label: String)
object Voices {
    val list = listOf(
        VoiceOption("Kore","Voice 1 \u2022 Kore"), VoiceOption("Aoede","Voice 2 \u2022 Aoede"),
        VoiceOption("Leda","Voice 3 \u2022 Leda"), VoiceOption("Zephyr","Voice 4 \u2022 Zephyr"),
        VoiceOption("Despina","Voice 5 \u2022 Despina"), VoiceOption("Autonoe","Voice 6 \u2022 Autonoe"),
        VoiceOption("Callirrhoe","Voice 7 \u2022 Callirrhoe"), VoiceOption("Erinome","Voice 8 \u2022 Erinome"),
        VoiceOption("Laomedeia","Voice 9 \u2022 Laomedeia"), VoiceOption("Sulafat","Voice 10 \u2022 Sulafat")
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
        VoiceOption("rehan","Voice 1 \u2022 Rehan"), VoiceOption("shubh","Voice 2 \u2022 Shubh"),
        VoiceOption("ratan","Voice 3 \u2022 Ratan"), VoiceOption("ashutosh","Voice 4 \u2022 Ashutosh"),
        VoiceOption("rahul","Voice 5 \u2022 Rahul"), VoiceOption("amit","Voice 6 \u2022 Amit"),
        VoiceOption("aditya","Voice 7 \u2022 Aditya"), VoiceOption("mani","Voice 8 \u2022 Mani"),
        VoiceOption("sunny","Voice 9 \u2022 Sunny"), VoiceOption("anand","Voice 10 \u2022 Anand")
    )
    fun findMsgMale(id: String) = maleMsgList.firstOrNull { it.id == id } ?: maleMsgList[0]
}
