package me.whyic.skyweeb.gui

import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen
import me.whyic.skyweeb.SkyWeeb
import me.whyic.skyweeb.config.Config
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import tech.thatgravyboat.skyblockapi.helpers.McClient

class ConfigScreen(private val parent: Screen? = null) : Screen(Component.literal("SkyWeeb")) {

    private companion object {
        const val PW = 620
        const val PH = 520
        val BG        = 0xFF15131C.toInt()
        val HEADER_BG = 0xFF1C1926.toInt()
        val DIVIDER   = 0xFF2C2836.toInt()
        val LABEL     = 0xFFFFFFFF.toInt()
        val DESC      = 0xFF8A83A0.toInt()
        val ACCENT    = 0xFF7B5CFA.toInt()
        val ON_GREEN  = 0xFF3FB556.toInt()
        val OFF_GREY  = 0xFF4A4658.toInt()
        val OVERLAY   = 0xCC0A0812.toInt()
    }

    private var customTextBox: EditBox? = null
    private var px = 0
    private var py = 0
    private val toggleButtons = mutableMapOf<SkyWeeb.Series, Button>()
    private val rowDividerYs = mutableListOf<Int>()

    override fun init() {
        px = (width - PW) / 2
        py = (height - PH) / 2
        val fx = px + 24
        val fw = PW - 48
        val controlW = 140
        val controlX = px + PW - 24 - controlW

        rowDividerYs.clear()
        var y = py + 76

        // Custom Text row
        customTextBox = EditBox(font, controlX, y, controlW, 18, Component.empty()).also {
            it.value = Config.customText
            it.setMaxLength(100)
            addRenderableWidget(it)
        }
        rowDividerYs.add(y + 34)
        y += 60

        // Time Between Rotations row
        val initSlider = (Config.timeBetweenRotations - 5).toDouble() / 55.0
        addRenderableWidget(object : AbstractSliderButton(controlX, y, controlW, 18, Component.empty(), initSlider) {
            init { updateMessage() }
            override fun updateMessage() {
                setMessage(Component.literal("${Mth.clamp((value * 55 + 5).toInt(), 5, 60)}s"))
            }
            override fun applyValue() {
                Config.timeBetweenRotations = Mth.clamp((value * 55 + 5).toInt(), 5, 60)
            }
        })
        rowDividerYs.add(y + 34)
        y += 60

        // 4 logo section rows: name+desc on the left, ON/OFF then icon-selector on the right
        val toggleW = 54
        val iconW = 130
        val iconX = px + PW - 24 - iconW
        val toggleX = iconX - 8 - toggleW

        SkyWeeb.Series.entries.forEach { series ->
            val toggleButton = Button.builder(toggleLabel(series)) {
                Config.activeSeries = series
                refreshToggleLabels()
            }.bounds(toggleX, y, toggleW, 18).build()
            addRenderableWidget(toggleButton)
            toggleButtons[series] = toggleButton

            addRenderableWidget(
                Button.builder(iconLabel(series)) {
                    cycleIcon(series)
                    it.message = iconLabel(series)
                }.bounds(iconX, y, iconW, 18).build()
            )

            rowDividerYs.add(y + 34)
            y += 46
        }

        val halfW = (fw - 8) / 2
        addRenderableWidget(
            Button.builder(Component.literal("Edit Lines")) {
                openLinesScreen()
            }.bounds(fx, py + PH - 40, halfW, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Save & Close")) {
                Config.customText = customTextBox?.value ?: Config.customText
                onClose()
            }.bounds(fx + halfW + 8, py + PH - 40, halfW, 20).build()
        )
    }

    private fun refreshToggleLabels() {
        toggleButtons.forEach { (series, button) -> button.message = toggleLabel(series) }
    }

    private fun toggleLabel(series: SkyWeeb.Series): Component =
        Component.literal(if (Config.activeSeries == series) "ON" else "OFF")

    private fun iconLabel(series: SkyWeeb.Series): Component = Component.literal(
        "${
            when (series) {
                SkyWeeb.Series.BLEACH -> Config.bleachIcon.displayName
                SkyWeeb.Series.ONE_PIECE -> Config.onePieceIcon.displayName
                SkyWeeb.Series.CHAINSAW_MAN -> Config.chainsawManIcon.displayName
                SkyWeeb.Series.FRIEREN -> Config.frierenIcon.displayName
            }
        } ▾"
    )

    private fun cycleIcon(series: SkyWeeb.Series) {
        when (series) {
            SkyWeeb.Series.BLEACH -> {
                val entries = SkyWeeb.BleachLogo.entries
                Config.bleachIcon = entries[(entries.indexOf(Config.bleachIcon) + 1) % entries.size]
            }
            SkyWeeb.Series.ONE_PIECE -> {
                val entries = SkyWeeb.OnePieceLogo.entries
                Config.onePieceIcon = entries[(entries.indexOf(Config.onePieceIcon) + 1) % entries.size]
            }
            SkyWeeb.Series.CHAINSAW_MAN -> {
                val entries = SkyWeeb.ChainsawManLogo.entries
                Config.chainsawManIcon = entries[(entries.indexOf(Config.chainsawManIcon) + 1) % entries.size]
            }
            SkyWeeb.Series.FRIEREN -> {
                val entries = SkyWeeb.FrierenLogo.entries
                Config.frierenIcon = entries[(entries.indexOf(Config.frierenIcon) + 1) % entries.size]
            }
        }
    }

    private fun openLinesScreen() {
        McClient.setScreenAsync {
            ResourcefulConfigScreen.getFactory(SkyWeeb.MOD_ID).apply(this)
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, pt: Float) {
        g.fill(0, 0, width, height, OVERLAY)
        g.fill(px, py, px + PW, py + PH, BG)
        g.fill(px, py, px + PW, py + 60, HEADER_BG)
        g.text(font, "SkyWeeb", px + 24, py + 16, LABEL, false)
        g.text(font, "v${SkyWeeb.VERSION}", px + 24, py + 34, DESC, false)

        val fx = px + 24
        var y = py + 76

        g.text(font, "Custom Text", fx, y + 4, LABEL, false)
        g.text(font, "Text shown when \"Custom Text\" is selected as a line.", fx, y + 16, DESC, false)
        g.fill(fx, y + rowDividerYs[0] - y, px + PW - 24, y + rowDividerYs[0] - y + 1, DIVIDER)
        y += 60

        g.text(font, "Time Between Rotations", fx, y + 4, LABEL, false)
        g.text(font, "Seconds between primary/secondary line rotation.", fx, y + 16, DESC, false)
        g.fill(fx, y + 34, px + PW - 24, y + 35, DIVIDER)
        y += 60

        SkyWeeb.Series.entries.forEach { series ->
            g.text(font, series.displayName, fx, y + 4, LABEL, false)
            g.fill(fx, y + 34, px + PW - 24, y + 35, DIVIDER)
            y += 46
        }

        toggleButtons.forEach { (series, button) ->
            val color = if (Config.activeSeries == series) ON_GREEN else OFF_GREY
            g.fill(button.x, button.y, button.x + button.width, button.y + button.height, color)
        }

        super.extractRenderState(g, mx, my, pt)
    }

    override fun onClose() {
        McClient.setScreenAsync { parent }
    }

    override fun isPauseScreen() = false
}
