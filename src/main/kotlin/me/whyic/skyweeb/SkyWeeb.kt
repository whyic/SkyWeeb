package me.whyic.skyweeb

import com.mojang.brigadier.arguments.StringArgumentType
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen
import com.teamresourceful.resourcefulconfig.api.loader.Configurator
import me.owdding.lib.utils.MeowddingUpdateChecker
import me.whyic.skyweeb.config.Config
import me.whyic.skyweeb.rpc.RPCClient
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.misc.LiteralCommandBuilder
import tech.thatgravyboat.skyblockapi.api.events.misc.RegisterCommandsEvent
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.utils.text.CommonText
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.Text.send
import tech.thatgravyboat.skyblockapi.utils.text.TextBuilder.append
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.hover
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.url

object SkyWeeb : ClientModInitializer, Logger by LoggerFactory.getLogger("SkyWeeb") {

    val MOD_ID = "skyweeb"
    val SELF = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val VERSION: String = SELF.metadata.version.friendlyString

    val prefix = Text.join(
        Text.of("[").withColor(TextColor.GRAY),
        Text.of("SkyWeeb").withColor(TextColor.AQUA),
        Text.of("] ").withColor(TextColor.GRAY),
    )

    val configurator = Configurator(MOD_ID)

    var skyblockJoin: Long? = null
    var tickCounter = 0

    override fun onInitializeClient() {
        Config.register(configurator)
        MeowddingUpdateChecker("qESHWJ0N", SELF, ::sendUpdateMessage)
        SkyBlockAPI.eventBus.register(this)
        SkyBlockTracker.register()

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            RPCClient.stop()
        }

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register {
            tickCounter++
            if (tickCounter >= 100) {
                tickCounter = 0
                updateDiscordRPC()
            }
        }
    }

    fun updateDiscordRPC() {
        if (!SkyBlockTracker.isOnSkyBlock) {
            RPCClient.stop()
            skyblockJoin = null
            return
        }

        if (skyblockJoin == null) {
            skyblockJoin = System.currentTimeMillis()
        }

        RPCClient.start()

        val activeLogoId = when (Config.activeSeries) {
            Series.BLEACH -> Config.bleachIcon.id
            Series.ONE_PIECE -> Config.onePieceIcon.id
            Series.CHAINSAW_MAN -> Config.chainsawManIcon.id
            Series.FRIEREN -> Config.frierenIcon.id
        }

        RPCClient.updateActivity {
            setDetails(Element.getPrimaryLine())
            setState(Element.getSecondaryLine()?.trim())
            setLargeImage(activeLogoId, "Yokoso watashi no Larp Society")

            setStartTimestamp(skyblockJoin!!)
            Buttons.entries.forEach {
                addButton(it.toButton())
            }
        }
    }

    enum class Series(val displayName: String) {
        BLEACH("Bleach"),
        ONE_PIECE("One Piece"),
        CHAINSAW_MAN("Chainsaw Man"),
        FRIEREN("Frieren"),
        ;
        override fun toString() = displayName
    }

    enum class BleachLogo(val id: String, val displayName: String) {
        DEFAULT("default", "Default"),
        HYPIXEL("hypixel", "Hypixel"),
        ORIHIME("bleach_orihime", "Orihime"),
        ICHIGO("bleach_ichigo", "Ichigo"),
        RUKIA("bleach_rukia", "Rukia"),
        URYU("bleach_uryu", "Uryu"),
        ;
        override fun toString() = displayName
    }

    enum class OnePieceLogo(val id: String, val displayName: String) {
        DEFAULT("default", "Default"),
        HYPIXEL("hypixel", "Hypixel"),
        LUFFY("onepiece_luffy", "Luffy"),
        ZORO("onepiece_zoro", "Zoro"),
        SANJI("onepiece_sanji", "Sanji"),
        NAMI("onepiece_nami", "Nami"),
        ROBIN("onepiece_robin", "Robin"),
        ;
        override fun toString() = displayName
    }

    enum class ChainsawManLogo(val id: String, val displayName: String) {
        DEFAULT("default", "Default"),
        HYPIXEL("hypixel", "Hypixel"),
        DENJI("csm_denji", "Denji"),
        POWER("csm_power", "Power"),
        AKI("csm_aki", "Aki"),
        MAKIMA("csm_makima", "Makima"),
        ;
        override fun toString() = displayName
    }

    enum class FrierenLogo(val id: String, val displayName: String) {
        DEFAULT("default", "Default"),
        HYPIXEL("hypixel", "Hypixel"),
        FRIEREN("frieren_frieren", "Frieren"),
        FERN("frieren_fern", "Fern"),
        STARK("frieren_stark", "Stark"),
        HIMMEL("frieren_himmel", "Himmel"),
        ;
        override fun toString() = displayName
    }

    fun sendUpdateMessage(link: String, current: String, new: String) {
        fun MutableComponent.withLink() = this.apply {
            this.url = link
            this.hover = Text.of(link).withColor(TextColor.GRAY)
        }

        McClient.runNextTick {
            CommonText.EMPTY.send()
            Text.join(
                "New version found! (",
                Text.of(current).withColor(TextColor.RED),
                Text.of(" -> ").withColor(TextColor.GRAY),
                Text.of(new).withColor(TextColor.GREEN),
                ")",
            ).withLink().sendWithPrefix()
            Text.of("Click to download.").withLink().sendWithPrefix()
            CommonText.EMPTY.send()
        }
    }

    fun Component.sendWithPrefix() = Text.join(prefix, this).send()

    @Subscription
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val rpcCommand: (LiteralCommandBuilder.() -> Unit) = {
            thenCallback("text text", StringArgumentType.greedyString()) {
                Config.customText = getArgument("text", String::class.java)
                Text.of("Set custom text to: ") {
                    color = TextColor.GRAY
                    append("\"${Config.customText}\"") {
                        color = TextColor.AQUA
                    }
                }.sendWithPrefix()
            }

            callback {
                McClient.setScreenAsync {
                    ResourcefulConfigScreen.getFactory(MOD_ID).apply(null)
                }
            }
        }

        event.register("swrpc") { rpcCommand() }
        event.register("skyweeb") { rpcCommand() }
        event.register("rpc") { rpcCommand() }
    }
}
