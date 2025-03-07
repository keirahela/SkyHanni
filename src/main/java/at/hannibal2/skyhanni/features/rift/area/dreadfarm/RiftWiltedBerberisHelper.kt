package at.hannibal2.skyhanni.features.rift.area.dreadfarm

import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.LorenzRenderWorldEvent
import at.hannibal2.skyhanni.events.LorenzTickEvent
import at.hannibal2.skyhanni.events.PlaySoundEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.features.rift.RiftAPI
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.BlockUtils.getBlockAt
import at.hannibal2.skyhanni.utils.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.draw3DLine
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawFilledBoundingBoxNea
import at.hannibal2.skyhanni.utils.RenderUtils.expandBlock
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.awt.Color
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object RiftWiltedBerberisHelper {

    private val config get() = RiftAPI.config.area.dreadfarm.wiltedBerberis
    private var isOnFarmland = false
    private var hasFarmingToolInHand = false
    private var lastBreakTime = System.currentTimeMillis()
    private var list = listOf<WiltedBerberis>()

    data class WiltedBerberis(var currentParticles: LorenzVec) {

        var previous: LorenzVec? = null
        var moving = true
        var y = 0.0
        var lastTime = SimpleTimeMark.now()
    }

    private fun breakWiltedBerberis(target: LorenzVec) {
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.thePlayer
        val world = minecraft.theWorld

        val blockPos = target.toBlockPos()
        val blockState = world.getBlockState(blockPos)
        val block = blockState.block

        if (block != null && block != Blocks.air) {
            minecraft.playerController.clickBlock(blockPos, player.horizontalFacing)
        }
    }

    private fun smoothRotation(current: Float, target: Float, maxIncrement: Float): Float {
        val delta = normalizeAngle(target - current)

        val easingFactor = (delta.absoluteValue / 180f).coerceIn(0.1f, 1f)

        return current + (delta * easingFactor).coerceIn(-maxIncrement, maxIncrement)
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360
        if (normalized > 180) normalized -= 360
        if (normalized < -180) normalized += 360
        return normalized
    }


    private fun moveToBerberisSmooth(berberis: WiltedBerberis): Boolean {
        if (!isAutoFarmEnabled()) {
            return false
        } else {
            val player = Minecraft.getMinecraft().thePlayer ?: return false

            val target = berberis.currentParticles
            val playerPos = LorenzVec(player.posX, player.posY, player.posZ)

            val directionX = target.x - playerPos.x
            val directionZ = target.z - playerPos.z
            val directionY = target.y - (playerPos.y + player.getEyeHeight())

            val distanceXZ = sqrt(directionX * directionX + directionZ * directionZ)

            if (distanceXZ < 0.8) {
                breakWiltedBerberis(target)
                lastBreakTime = System.currentTimeMillis()
                return true
            }

            val normalizeFactor = 1.0 / distanceXZ
            val moveSpeed = config.autoFarmWalkSpeed
            val motionX = directionX * normalizeFactor * moveSpeed
            val motionZ = directionZ * normalizeFactor * moveSpeed

            val targetYaw = Math.toDegrees(atan2(-directionX, directionZ)).toFloat()

            player.isSprinting = true
            player.motionX = motionX
            player.motionZ = motionZ

            val targetPitch = Math.toDegrees(-atan2(directionY, distanceXZ)).toFloat()

            player.rotationYaw = smoothRotation(player.rotationYaw, targetYaw, 10f)
            player.rotationPitch = smoothRotation(player.rotationPitch, targetPitch, 5f)

            return false
        }
    }

    private fun moveAlongLine(berberis: WiltedBerberis, speed: Double): Boolean {
        val player = Minecraft.getMinecraft().thePlayer ?: return false

        val previousLocation = berberis.previous ?: return false
        val currentLocation = berberis.currentParticles

        val directionX = currentLocation.x - previousLocation.x
        val directionZ = currentLocation.z - previousLocation.z

        val magnitude = sqrt(directionX * directionX + directionZ * directionZ)
        if (magnitude == 0.0) return false

        val normalizedX = directionX / magnitude
        val normalizedZ = directionZ / magnitude

        val moveX = normalizedX * speed
        val moveZ = normalizedZ * speed

        val targetYaw = Math.toDegrees(atan2(-moveX, moveZ)).toFloat()

        if (System.currentTimeMillis() - lastBreakTime < config.breakDelay) {
            player.rotationYaw = smoothRotation(player.rotationYaw, targetYaw, 10f)
            return false
        }

        player.motionX = moveX
        player.motionY = 0.0
        player.motionZ = moveZ

        player.rotationYaw = smoothRotation(player.rotationYaw, targetYaw, 10f)

        return true
    }


    @SubscribeEvent
    fun onTick(event: LorenzTickEvent) {
        if (!isEnabled()) return
        if (!event.isMod(5)) return

        list = list.editCopy { removeIf { it.lastTime.passedSince() > 500.milliseconds } }

        hasFarmingToolInHand = InventoryUtils.getItemInHand()?.getInternalName() == RiftAPI.farmingTool

        if (Minecraft.getMinecraft().thePlayer.onGround) {
            val block = LorenzVec.getBlockBelowPlayer().getBlockAt()
            val currentY = LocationUtils.playerLocation().y
            isOnFarmland = block == Blocks.farmland && (currentY % 1 == 0.0)
        }
    }

    private fun nearestBerberis(location: LorenzVec): WiltedBerberis? =
        list.filter { it.currentParticles.distanceSq(location) < 8 }
            .minByOrNull { it.currentParticles.distanceSq(location) }

    @SubscribeEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!isEnabled()) return
        if (!hasFarmingToolInHand) return

        val location = event.location
        val berberis = nearestBerberis(location)

        if (event.type != EnumParticleTypes.FIREWORKS_SPARK) {
            if (config.hideParticles && berberis != null) {
                event.cancel()
            }
            return
        }

        if (config.hideParticles) {
            event.cancel()
        }

        if (berberis == null) {
            val newBerberis = WiltedBerberis(location)
            list = list.editCopy { add(WiltedBerberis(location)) }

            if (currentTarget == null) {
                currentTarget = newBerberis
            }

            return
        }

        with(berberis) {
            val isMoving = currentParticles != location
            if (isMoving) {
                if (currentParticles.distance(location) > 3) {
                    previous = null
                    moving = true
                }
                if (!moving) {
                    previous = currentParticles
                }
            }
            if (!isMoving) {
                y = location.y - 1
            }

            moving = isMoving
            currentParticles = location
            lastTime = SimpleTimeMark.now()
        }
    }

    @SubscribeEvent
    fun onPlaySound(event: PlaySoundEvent) {
        if (!isMuteOthersSoundsEnabled()) return
        val soundName = event.soundName

        if (soundName == "mob.horse.donkey.death" || soundName == "mob.horse.donkey.hit") {
            event.cancel()
        }
    }

    private var currentTarget: WiltedBerberis? = null

    @SubscribeEvent
    fun onRenderWorld(event: LorenzRenderWorldEvent) {
        if (!isEnabled()) return
        if (!hasFarmingToolInHand) return

        if (config.onlyOnFarmland && !isOnFarmland) return

        for (berberis in list) {
            with(berberis) {
                if (currentParticles.distanceToPlayer() > config.AuraRange) continue
                if (y == 0.0) continue

                val location = currentParticles.fixLocation(berberis)
                if (!moving) {
                    event.drawFilledBoundingBoxNea(axisAlignedBB(location), Color.YELLOW, 0.7f)
                    event.drawDynamicText(location.up(), "§eWilted Berberis", 1.5, ignoreBlocks = false)

                    moveToBerberisSmooth(this)
                } else {
                    event.drawFilledBoundingBoxNea(axisAlignedBB(location), Color.WHITE, 0.5f)
                    previous?.fixLocation(berberis)?.let {
                        event.drawFilledBoundingBoxNea(axisAlignedBB(it), Color.LIGHT_GRAY, 0.2f)
                        event.draw3DLine(it.add(0.5, 0.0, 0.5), location.add(0.5, 0.0, 0.5), Color.WHITE, 3, false)
                    }
                    moveAlongLine(berberis, config.autoFarmWalkSpeed)
                }
            }
        }
    }

    @SubscribeEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(60, "rift.area.dreadfarm.wiltedBerberis.hideparticles", "rift.area.dreadfarm.wiltedBerberis.hideParticles")
    }

    private fun axisAlignedBB(loc: LorenzVec) = loc.add(0.1, -0.1, 0.1).boundingToOffset(0.8, 1.0, 0.8).expandBlock()

    private fun LorenzVec.fixLocation(wiltedBerberis: WiltedBerberis): LorenzVec {
        val x = x - 0.5
        val y = wiltedBerberis.y
        val z = z - 0.5
        return LorenzVec(x, y, z)
    }

    private fun isEnabled() = RiftAPI.inRift() && RiftAPI.inDreadfarm() && config.enabled
    private fun isAutoFarmEnabled() = RiftAPI.inRift() && RiftAPI.inDreadfarm() && config.autoFarmEnabled

    private fun isMuteOthersSoundsEnabled() = RiftAPI.inRift() &&
        config.muteOthersSounds &&
        (RiftAPI.inDreadfarm() || RiftAPI.inWestVillage()) &&
        !(hasFarmingToolInHand && isOnFarmland)
}
