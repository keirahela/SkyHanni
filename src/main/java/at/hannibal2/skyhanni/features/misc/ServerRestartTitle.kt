package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.ScoreboardData
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object ServerRestartTitle {

    private val config get() = SkyHanniMod.feature.misc
    private val patternGroup = RepoPattern.group("features.misc.serverrestart")

    /**
     * REGEX-TEST: §cServer closing: 03:11 §8m77A
     */
    private val restartingPattern by patternGroup.pattern(
        "time",
        "§cServer closing: (?<minutes>\\d+):(?<seconds>\\d+) ?§8.*",
    )

    /**
     * REGEX-TEST: §cServer closing: 03:11 §8m77A
     */
    val restartingGreedyPattern by patternGroup.pattern(
        "greedy",
        "§cServer closing.*",
    )

    @SubscribeEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!LorenzUtils.inSkyBlock) return
        if (!config.serverRestartTitle) return

        restartingPattern.firstMatcher(ScoreboardData.sidebarLinesFormatted) {
            val minutes = group("minutes").toInt().minutes
            val seconds = group("seconds").toInt().seconds
            val totalTime = minutes + seconds
            if (totalTime > 2.minutes && totalTime.inWholeSeconds % 30 != 0L) return
            val time = totalTime.format()
            LorenzUtils.sendTitle("§cServer Restart in §b$time", 2.seconds)
        }
    }
}
