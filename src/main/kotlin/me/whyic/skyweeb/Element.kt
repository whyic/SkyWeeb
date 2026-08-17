package me.whyic.skyweeb

import me.whyic.skyweeb.config.Config
import tech.thatgravyboat.skyblockapi.api.profile.currency.CurrencyAPI
import tech.thatgravyboat.skyblockapi.utils.extentions.toFormattedString

enum class Element(val example: String, val getter: () -> String) {
    PURSE("Purse: 123,456 (Motes in Rift)", {
        if (SkyBlockTracker.currentZone.contains("Rift", ignoreCase = true)) "Motes: ${CurrencyAPI.motes.toFormattedString()}"
        else "Purse: ${CurrencyAPI.purse.toFormattedString()}"
    }),
    BANK("Bank: 123,456", {
        "Bank: ${CurrencyAPI.bank.toFormattedString()}"
    }),
    BITS("Bits: 123,456", {
        "Bits: ${CurrencyAPI.bits.toFormattedString()}"
    }),
    AREA("✦ Auction House", {
        "✦ ${SkyBlockTracker.currentZone}"
    }),
    HELD_ITEM("Holding: Aspect of the End", {
        "Holding: ${SkyBlockTracker.heldItem}"
    }),
    CUSTOM_TEXT("Custom Text", {
        Config.customText
    }),
    ;

    override fun toString() = example

    companion object {
        fun getPrimaryLine() = getRotation(Config.primaryLine.toList())?.getter()
        fun getSecondaryLine() = getRotation(Config.secondaryLine.toList())?.getter()

        private fun getRotation(elements: List<Element>): Element? = runCatching {
            val index = (System.currentTimeMillis() / 1000 / Config.timeBetweenRotations) % elements.size
            elements[index.toInt()]
        }.getOrNull()
    }
}
