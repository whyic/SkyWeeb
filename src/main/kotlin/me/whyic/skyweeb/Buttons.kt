package me.whyic.skyweeb

import me.whyic.skyweeb.rpc.DiscordButton
import tech.thatgravyboat.skyblockapi.helpers.McPlayer

enum class Buttons(val label: String, private val urlProvider: () -> String) {
    DISCORD("Discord", { "https://discord.gg/uY5J3RwmTX" }),
    SKY_CRYPT("SkyCrypt", { "https://sky.shiiyu.moe/stats/${McPlayer.name}" }),
    ;

    val url: String by lazy { "${urlProvider()}?utm_source=SkyWeeb" }

    fun toButton() = DiscordButton(label, url)

    override fun toString() = label
}
