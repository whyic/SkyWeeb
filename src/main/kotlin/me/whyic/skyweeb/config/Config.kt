package me.whyic.skyweeb.config

import com.teamresourceful.resourcefulconfig.api.types.info.ResourcefulConfigLink
import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.ConfigKt
import me.whyic.skyweeb.Element
import me.whyic.skyweeb.SkyWeeb

object Config : ConfigKt("skyweeb/config") {

    override val name = TranslatableValue("SkyWeeb")
    override val description = TranslatableValue("v${SkyWeeb.VERSION}")
    override val links: Array<ResourcefulConfigLink> = arrayOf(
        ResourcefulConfigLink.create(
            "https://discord.gg/uY5J3RwmTX",
            "discord",
            TranslatableValue("Discord"),
        ),
        ResourcefulConfigLink.create(
            "https://www.youtube.com/@kitty-fx",
            "web",
            TranslatableValue("YouTube"),
        ),
    )

    val primaryLine by draggable(Element.PURSE) {
        translation = "skyweeb.config.primary_line"
    }

    val secondaryLine by draggable(Element.AREA) {
        translation = "skyweeb.config.secondary_line"
    }

    var customText by string("Using SkyWeeb") {
        translation = "skyweeb.config.custom_text"
    }

    var timeBetweenRotations by int(15) {
        translation = "skyweeb.config.time_between_rotations"
        slider = true
        range = 5..60
    }

    var activeSeries by enum(SkyWeeb.Series.BLEACH) {
        translation = "skyweeb.config.active_series"
    }

    var bleachIcon by enum(SkyWeeb.BleachLogo.DEFAULT) { translation = "skyweeb.config.bleach_icon" }
    var onePieceIcon by enum(SkyWeeb.OnePieceLogo.DEFAULT) { translation = "skyweeb.config.one_piece_icon" }
    var chainsawManIcon by enum(SkyWeeb.ChainsawManLogo.DEFAULT) { translation = "skyweeb.config.chainsaw_man_icon" }
    var frierenIcon by enum(SkyWeeb.FrierenLogo.DEFAULT) { translation = "skyweeb.config.frieren_icon" }
}
