package me.whyic.skyweeb

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

@Suppress("SpellCheckingInspection")
object SkyTracker {

    var currentZone: String = "Hub"
    var heldItem: String = "None"
    var isOnSkyBlock: Boolean = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.level == null || client.player == null) {
                isOnSkyBlock = false
                return@register
            }

            val itemStack = client.player!!.mainHandItem
            heldItem = if (itemStack.isEmpty) "Empty Hand" else itemStack.hoverName.string

            parseScoreboard(client)
        }
    }

    private fun parseScoreboard(client: Minecraft) {
        val scoreboard = client.level?.scoreboard ?: return

        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (objective == null) {
            isOnSkyBlock = false
            return
        }

        val rawTitle = objective.displayName.string
        val cleanTitle = rawTitle.replace(Regex("§."), "")

        isOnSkyBlock = cleanTitle.contains("SKYBLOCK", ignoreCase = true)
        if (!isOnSkyBlock) return

        scoreboard.listPlayerScores(objective).forEach { score ->
            val scoreName = score.owner
            val team = scoreboard.getPlayersTeam(scoreName)

            val prefix = team?.playerPrefix?.string ?: ""
            val suffix = team?.playerSuffix?.string ?: ""

            val rawLine = "$prefix$scoreName$suffix"
            val cleanLine = rawLine.replace(Regex("§."), "").trim()

            // 57447 (U+E067) = overworld decimal
            // 57376 (U+E000) = same shi but in rift (it's an hourglass)
            if (cleanLine.isNotEmpty() && (cleanLine[0].code == 57447 || cleanLine[0].code == 57376)) {
                currentZone = cleanLine.substring(1).trim()
            }
        }
    }
}
