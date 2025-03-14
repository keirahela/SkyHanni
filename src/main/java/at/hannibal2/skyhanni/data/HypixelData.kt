package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigManager.Companion.gson
import at.hannibal2.skyhanni.data.model.TabWidget
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.HypixelJoinEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.LorenzChatEvent
import at.hannibal2.skyhanni.events.LorenzTickEvent
import at.hannibal2.skyhanni.events.LorenzWorldChangeEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.ScoreboardUpdateEvent
import at.hannibal2.skyhanni.events.WidgetUpdateEvent
import at.hannibal2.skyhanni.events.minecraft.ClientDisconnectEvent
import at.hannibal2.skyhanni.events.skyblock.ScoreboardAreaChangeEvent
import at.hannibal2.skyhanni.features.bingo.BingoAPI
import at.hannibal2.skyhanni.features.dungeon.DungeonAPI
import at.hannibal2.skyhanni.features.rift.RiftAPI
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LorenzLogger
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.TabListData
import at.hannibal2.skyhanni.utils.UtilsPatterns
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object HypixelData {

    private val patternGroup = RepoPattern.group("data.hypixeldata")

    // TODO add regex tests
    private val serverNameConnectionPattern by patternGroup.pattern(
        "servername.connection",
        "(?<prefix>.+\\.)?hypixel\\.net",
    )

    /**
     * REGEX-TEST: §ewww.hypixel.net
     * REGEX-TEST: §ealpha.hypixel.net
     */
    private val serverNameScoreboardPattern by patternGroup.pattern(
        "servername.scoreboard",
        "§e(?<prefix>.+\\.)?hypixel\\.net",
    )

    /**
     * REGEX-TEST: §b§lArea: §r§7Private Island
     * REGEX-TEST: §b§lDungeon: §r§7Catacombs
     */
    @Suppress("UnusedPrivateProperty")
    private val islandNamePattern by patternGroup.pattern(
        "islandname",
        "(?:§.)*(?:Area|Dungeon): (?:§.)*(?<island>.*)",
    )

    /**
     * REGEX-TEST: §711/15/24 §8m19CJ
     * REGEX-TEST: §711/15/24 §8m1F
     */
    private val serverIdScoreboardPattern by patternGroup.pattern(
        "serverid.scoreboard",
        "§7\\d+/\\d+/\\d+ §8(?<servertype>[mM])(?<serverid>\\S+).*",
    )
    private val lobbyTypePattern by patternGroup.pattern(
        "lobbytype",
        "(?<lobbyType>.*lobby)\\d+",
    )

    /**
     * REGEX-TEST:          §r§a§lPlayers §r§f(5)
     */
    private val playerAmountPattern by patternGroup.pattern(
        "playeramount",
        "^\\s*(?:§.)+Players (?:§.)+\\((?<amount>\\d+)\\)\\s*$",
    )

    /**
     * REGEX-TEST:           §r§b§lCoop §r§f(4)
     */
    private val playerAmountCoopPattern by patternGroup.pattern(
        "playeramount.coop",
        "^\\s*(?:§.)*Coop (?:§.)*\\((?<amount>\\d+)\\)\\s*$",
    )

    /**
     * REGEX-TEST:           §r§5§lGuests §r§f(0)
     */
    private val playerAmountGuestingPattern by patternGroup.pattern(
        "playeramount.guesting",
        "^\\s*(?:§.)*Guests (?:§.)*\\((?<amount>\\d+)\\)\\s*$",
    )

    /**
     * REGEX-TEST:           §r§b§lParty §r§f(4)
     */
    private val dungeonPartyAmountPattern by patternGroup.pattern(
        "playeramount.dungeonparty",
        "^\\s*(?:§.)+Party (?:§.)+\\((?<amount>\\d+)\\)\\s*$",
    )

    /**
     * REGEX-TEST:            §r§b§lIsland
     */
    private val soloProfileAmountPattern by patternGroup.pattern(
        "solo.profile.amount",
        "^\\s*(?:§.)*Island\\s*$",
    )

    /**
     * REGEX-TEST:  §a✌ §7(§a11§7/20)
     */
    private val scoreboardVisitingAmountPattern by patternGroup.pattern(
        "scoreboard.visiting.amount",
        "\\s+§.✌ §.\\(§.(?<currentamount>\\d+)§./(?<maxamount>\\d+)\\)",
    )
    private val guestPattern by patternGroup.pattern(
        "guesting.scoreboard",
        "SKYBLOCK GUEST",
    )

    /**
     * REGEX-TEST: SKYBLOCK
     * REGEX-TEST: SKYBLOCK GUEST
     * REGEX-TEST: SKYBLOCK CO-OP
     */
    private val scoreboardTitlePattern by patternGroup.pattern(
        "scoreboard.title",
        "SK[YI]BLOCK(?: CO-OP| GUEST)?",
    )

    /**
     * REGEX-TEST:  §7⏣ §bVillage
     * REGEX-TEST:  §5ф §dWizard Tower
     */
    private val skyblockAreaPattern by patternGroup.pattern(
        "skyblock.area",
        "\\s*§(?<symbol>7⏣|5ф) §(?<color>.)(?<area>.*)",
    )

    var lastLocRaw = SimpleTimeMark.farPast()
    private var hasScoreboardUpdated = false

    var hypixelLive = false
    var hypixelAlpha = false
    var inLobby = false
    var inLimbo = false
    var skyBlock = false
    var skyBlockIsland = IslandType.UNKNOWN
    var serverId: String? = null
    private var lastSuccessfulServerIdFetchTime = SimpleTimeMark.farPast()
    private var lastSuccessfulServerIdFetchType: String? = null
    private var failedServerIdFetchCounter = 0

    // Ironman, Stranded and Bingo
    var noTrade = false

    var ironman = false
    var stranded = false
    var bingo = false

    var profileName = ""
    var joinedWorld = SimpleTimeMark.farPast()

    var skyBlockArea: String? = null
    var skyBlockAreaWithSymbol: String? = null

    // Data from locraw
    var locrawData: JsonObject? = null
    private val locraw: MutableMap<String, String> = listOf(
        "server",
        "gametype",
        "lobbyname",
        "lobbytype",
        "mode",
        "map",
    ).associateWith { "" }.toMutableMap()

    val server get() = locraw["server"].orEmpty()
    val gameType get() = locraw["gametype"].orEmpty()
    val lobbyName get() = locraw["lobbyname"].orEmpty()
    val lobbyType get() = locraw["lobbytype"].orEmpty()
    val mode get() = locraw["mode"].orEmpty()
    val map get() = locraw["map"].orEmpty()

    fun checkCurrentServerId() {
        if (!LorenzUtils.inSkyBlock) return
        if (serverId != null) return
        if (LorenzUtils.lastWorldSwitch.passedSince() < 1.seconds) return
        if (!TabListData.fullyLoaded) return

        TabWidget.SERVER.matchMatcherFirstLine {
            serverId = group("serverid")
            lastSuccessfulServerIdFetchTime = SimpleTimeMark.now()
            lastSuccessfulServerIdFetchType = "tab list"
            failedServerIdFetchCounter = 0
            return
        }

        serverIdScoreboardPattern.firstMatcher(ScoreboardData.sidebarLinesFormatted) {
            val serverType = if (group("servertype") == "M") "mega" else "mini"
            serverId = "$serverType${group("serverid")}"
            lastSuccessfulServerIdFetchTime = SimpleTimeMark.now()
            lastSuccessfulServerIdFetchType = "scoreboard"
            failedServerIdFetchCounter = 0
            return
        }

        failedServerIdFetchCounter++
        if (failedServerIdFetchCounter < 3) return
        ErrorManager.logErrorWithData(
            Exception("NoServerId"),
            "Could not find server id",
            "failedServerIdFetchCounter" to failedServerIdFetchCounter,
            "lastSuccessfulServerIdFetchTime" to lastSuccessfulServerIdFetchTime,
            "lastSuccessfulServerIdFetchType" to lastSuccessfulServerIdFetchType,
            "islandType" to LorenzUtils.skyBlockIsland,
            "tablist" to TabListData.getTabList(),
            "scoreboard" to ScoreboardData.sidebarLinesFormatted,
        )
    }

    @SubscribeEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Server ID")
        if (!LorenzUtils.inSkyBlock) {
            event.addIrrelevant("not in sb")
            return
        }

        val id = serverId
        if (id == null) {
            event.addData {
                add("server id is null!")
                add("failedServerIdFetchCounter: $failedServerIdFetchCounter")
                add("")
                add("last successful fetch time: $lastSuccessfulServerIdFetchTime")
                add("last successful fetch type: $lastSuccessfulServerIdFetchType")
            }
        } else {
            event.addIrrelevant {
                add("Server id: '$id'")
                add("fetch time: $lastSuccessfulServerIdFetchTime")
                add("fetch type: $lastSuccessfulServerIdFetchType")
            }
        }
    }

    fun getPlayersOnCurrentServer(): Int {
        var amount = 0
        val playerPatternList = mutableListOf(
            playerAmountPattern,
            playerAmountCoopPattern,
            playerAmountGuestingPattern,
        )
        if (DungeonAPI.inDungeon()) {
            playerPatternList.add(dungeonPartyAmountPattern)
        }

        out@ for (pattern in playerPatternList) {
            for (line in TabListData.getTabList()) {
                pattern.matchMatcher(line) {
                    amount += group("amount").toInt()
                    continue@out
                }
            }
        }
        amount += TabListData.getTabList().count { soloProfileAmountPattern.matches(it) }

        return amount
    }

    fun getMaxPlayersForCurrentServer(): Int {
        scoreboardVisitingAmountPattern.firstMatcher(ScoreboardData.sidebarLinesFormatted) {
            return group("maxamount").toInt()
        }

        return when (skyBlockIsland) {
            IslandType.MINESHAFT -> 4
            IslandType.CATACOMBS -> 5
            IslandType.CRYSTAL_HOLLOWS -> 24
            IslandType.CRIMSON_ISLE -> 24
            else -> if (serverId?.startsWith("mega") == true) 80 else 26
        }
    }

    // This code is modified from NEU, and depends on NEU (or another mod) sending /locraw.
    private val jsonBracketPattern = "^\\{.+}".toPattern()

    // todo convert to proper json object
    fun checkForLocraw(message: String) {
        jsonBracketPattern.matchMatcher(message.removeColor()) {
            try {
                val obj: JsonObject = gson.fromJson(group(), JsonObject::class.java)
                if (obj.has("server")) {
                    locrawData = obj
                    for (key in locraw.keys) {
                        locraw[key] = obj[key]?.asString.orEmpty()
                    }
                    inLimbo = locraw["server"] == "limbo"
                    inLobby = locraw["lobbyname"] != ""

                    if (inLobby) {
                        locraw["lobbyname"]?.let {
                            lobbyTypePattern.matchMatcher(it) {
                                locraw["lobbytype"] = group("lobbyType")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                ErrorManager.logErrorWithData(e, "Failed to parse locraw data")
            }
        }
    }

    private val loggerIslandChange = LorenzLogger("debug/island_change")

    @SubscribeEvent
    fun onWorldChange(event: LorenzWorldChangeEvent) {
        locrawData = null
        skyBlock = false
        inLimbo = false
        inLobby = false
        locraw.forEach { locraw[it.key] = "" }
        joinedWorld = SimpleTimeMark.now()
        serverId = null
        skyBlockArea = null
        skyBlockAreaWithSymbol = null
    }

    @HandleEvent
    fun onDisconnect(event: ClientDisconnectEvent) {
        hypixelLive = false
        hypixelAlpha = false
        skyBlock = false
        inLobby = false
        locraw.forEach { locraw[it.key] = "" }
        locrawData = null
        skyBlockArea = null
        skyBlockAreaWithSymbol = null
        hasScoreboardUpdated = false
    }

    @SubscribeEvent
    fun onScoreboardUpdate(event: ScoreboardUpdateEvent) {
        hasScoreboardUpdated = true
    }

    @SubscribeEvent
    fun onChat(event: LorenzChatEvent) {
        if (!LorenzUtils.onHypixel) return

        val message = event.message.removeColor().lowercase()
        if (message.startsWith("your profile was changed to:")) {
            val newProfile = message.replace("your profile was changed to:", "").replace("(co-op)", "").trim()
            if (profileName == newProfile) return
            profileName = newProfile
            ProfileJoinEvent(newProfile).postAndCatch()
        }
        if (message.startsWith("you are playing on profile:")) {
            val newProfile = message.replace("you are playing on profile:", "").replace("(co-op)", "").trim()
            ProfileStorageData.profileJoinMessage()
            if (profileName == newProfile) return
            profileName = newProfile
            ProfileJoinEvent(newProfile).postAndCatch()
        }
    }

    private fun checkProfile() {
        TabWidget.PROFILE.matchMatcherFirstLine {
            var newProfile = group("profile").lowercase()
            // Hypixel shows the profile name reversed while in the Rift
            if (RiftAPI.inRift()) newProfile = newProfile.reversed()
            if (profileName == newProfile) return
            profileName = newProfile
            ProfileJoinEvent(newProfile).postAndCatch()
        }
    }

    // TODO rewrite everything in here
    @SubscribeEvent
    fun onTick(event: LorenzTickEvent) {
        if (!LorenzUtils.inSkyBlock) {
            sendLocraw()
        }

        if (LorenzUtils.onHypixel && LorenzUtils.inSkyBlock) {
            loop@ for (line in ScoreboardData.sidebarLinesFormatted) {
                skyblockAreaPattern.matchMatcher(line) {
                    val originalLocation = group("area").removeColor()
                    val area = LocationFixData.fixLocation(skyBlockIsland) ?: originalLocation
                    skyBlockAreaWithSymbol = line.trim()
                    if (area != skyBlockArea) {
                        val previousArea = skyBlockArea
                        skyBlockArea = area
                        ScoreboardAreaChangeEvent(area, previousArea).post()
                    }
                    break@loop
                }
            }

            checkProfileName()
        }

        if (!LorenzUtils.onHypixel) {
            checkHypixel()
            if (LorenzUtils.onHypixel) {
                HypixelJoinEvent().postAndCatch()
                SkyHanniMod.repo.displayRepoStatus(true)
            }
        }
        if (!LorenzUtils.onHypixel) return

        if (!event.isMod(5)) return

        val inSkyBlock = checkScoreboard()
        if (inSkyBlock) {
            checkSidebar()
            checkCurrentServerId()
        }

        if (inSkyBlock == skyBlock) return
        skyBlock = inSkyBlock
    }

    private fun sendLocraw() {
        if (LorenzUtils.onHypixel && locrawData == null && lastLocRaw.passedSince() > 15.seconds) {
            lastLocRaw = SimpleTimeMark.now()
            HypixelCommands.locraw()
        }
    }

    @SubscribeEvent
    fun onTabListUpdate(event: WidgetUpdateEvent) {
        when (event.widget) {
            TabWidget.AREA -> checkIsland(event)
            TabWidget.PROFILE -> checkProfile()
            else -> Unit
        }
    }

    private fun checkProfileName() {
        if (profileName.isNotEmpty()) return

        UtilsPatterns.tabListProfilePattern.firstMatcher(TabListData.getTabList()) {
            profileName = group("profile").lowercase()
            ProfileJoinEvent(profileName).postAndCatch()
        }
    }

    private fun checkHypixel() {
        if (!hasScoreboardUpdated) return
        val mc = Minecraft.getMinecraft()
        val player = mc.thePlayer ?: return

        var hypixel = false

        player.clientBrand?.let {
            if (it.contains("hypixel", ignoreCase = true)) {
                hypixel = true
            }
        }

        serverNameConnectionPattern.matchMatcher(mc.currentServerData?.serverIP.orEmpty()) {
            hypixel = true
            if (group("prefix") == "alpha.") {
                hypixelAlpha = true
            }
        }

        for (line in ScoreboardData.sidebarLinesFormatted) {
            serverNameScoreboardPattern.matchMatcher(line) {
                hypixel = true
                if (group("prefix") == "alpha.") {
                    hypixelAlpha = true
                }
            }
        }

        hypixelLive = hypixel && !hypixelAlpha
    }

    private fun checkSidebar() {
        ironman = false
        stranded = false
        bingo = false

        for (line in ScoreboardData.sidebarLinesFormatted) {
            if (BingoAPI.getRankFromScoreboard(line) != null) {
                bingo = true
            }
            when (line) {
                " §7♲ §7Ironman" -> {
                    ironman = true
                }

                " §a☀ §aStranded" -> {
                    stranded = true
                }
            }
        }

        noTrade = ironman || stranded || bingo
    }

    private fun checkIsland(event: WidgetUpdateEvent) {
        val newIsland: IslandType
        val foundIsland: String
        if (event.isClear()) {

            TabListData.fullyLoaded = false
            newIsland = IslandType.NONE
            foundIsland = ""

        } else {
            TabListData.fullyLoaded = true
            // Can not use color coding, because of the color effect (§f§lSKYB§6§lL§e§lOCK§A§L GUEST)
            val guesting = guestPattern.matches(ScoreboardData.objectiveTitle.removeColor())
            foundIsland = TabWidget.AREA.matchMatcherFirstLine { group("island").removeColor() }.orEmpty()
            newIsland = getIslandType(foundIsland, guesting)
        }

        // TODO don't send events when one of the arguments is none, at least when not on sb anymore
        if (skyBlockIsland != newIsland) {
            val oldIsland = skyBlockIsland
            skyBlockIsland = newIsland
            IslandChangeEvent(newIsland, oldIsland).postAndCatch()

            if (newIsland == IslandType.UNKNOWN) {
                ChatUtils.debug("Unknown island detected: '$foundIsland'")
                loggerIslandChange.log("Unknown: '$foundIsland'")
            } else {
                loggerIslandChange.log(newIsland.name)
            }
            if (TabListData.fullyLoaded) {
                TabWidget.reSendEvents()
            }
        }
    }

    private fun getIslandType(name: String, guesting: Boolean): IslandType {
        val islandType = IslandType.getByNameOrUnknown(name)
        if (guesting) {
            if (islandType == IslandType.PRIVATE_ISLAND) return IslandType.PRIVATE_ISLAND_GUEST
            if (islandType == IslandType.GARDEN) return IslandType.GARDEN_GUEST
        }
        return islandType
    }

    private fun checkScoreboard(): Boolean {
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return false

        val objective = world.scoreboard.getObjectiveInDisplaySlot(1) ?: return false
        val displayName = objective.displayName
        val scoreboardTitle = displayName.removeColor()
        return scoreboardTitlePattern.matches(scoreboardTitle)
    }
}
