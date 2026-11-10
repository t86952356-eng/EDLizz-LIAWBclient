/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.antivoidmodes.VulcanAntiVoid
import net.ccbluex.liquidbounce.features.module.modules.movement.antivoidmodes.WatchdogNCPAntiVoid
import net.ccbluex.liquidbounce.features.module.modules.player.Blink
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Tower
import net.ccbluex.liquidbounce.utils.block.block
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.ccbluex.liquidbounce.utils.extensions.component3
import net.ccbluex.liquidbounce.utils.movement.FallingPlayer
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.strafe
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawFilledBox
import net.ccbluex.liquidbounce.utils.render.RenderUtils.glColor
import net.ccbluex.liquidbounce.utils.render.RenderUtils.renderNameTag
import net.ccbluex.liquidbounce.utils.simulation.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.timing.WaitTickUtils
import net.minecraft.block.BlockAir
import net.minecraft.client.renderer.GlStateManager.resetColor
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

object AntiVoid : Module("AntiVoid", Category.MOVEMENT) {

    /** 在 arraylist 中显示当前模式 tag（与 NoSlow/Disabler 相同）。 */
    override val tag
        get() = mode

    private val mode by choices(
        "Mode",
        arrayOf(
            "Blink", "TeleportBack", "FlyFlag", "OnGroundSpoof", "MotionTeleport-Flag", "GhostBlock",
            "WatchdogNCP", "Vulcan"
        ),
        "FlyFlag"
    )

    /** 新模式集合（与 NoSlowMode/CriticalsMode 同套路的分发模型）。 */
    private val antiVoidModes = arrayOf(WatchdogNCPAntiVoid, VulcanAntiVoid)

    private val antiVoidMode get() = antiVoidModes.firstOrNull { it.modeName == mode }
    private val maxFallDistance by int("MaxFallDistance", 10, 2..255)
    private val maxDistanceWithoutGround by float("MaxDistanceToSetback", 2.5f, 1f..30f) { mode != "Blink" }
    private val blinkDelay by int("BlinkDelay", 10, 1..20) { mode == "Blink" }
    private val onScaffold by boolean("OnScaffold", false) { mode == "Blink" }
    private val ticksToDelay by int("TicksDelay", 5, 1..20) { mode == "Blink" && !onScaffold }
    private val indicator by boolean("Indicator", true).subjective()

    // ---- WatchdogNCP（AntiVoid）选项 ----（名字与旧设置区分，避免同名值冲突）
    private val wdVoidDistance by float("WatchdogNCP Distance", 3f, 0f..10f) { mode == "WatchdogNCP" }

    // ---- Vulcan（AntiVoid）选项 ----
    private val vulcanVoidDistance by float("Vulcan Distance", 2.6f, 0f..10f) { mode == "Vulcan" }

    private var detectedLocation: BlockPos? = null
    private var lastFound = 0F
    private var prevX = 0.0
    private var prevY = 0.0
    private var prevZ = 0.0
    private var shouldSimulateBlock = false
    private var shouldBlink = false
    private var pauseTicks = 0

    override fun onDisable() {
        antiVoidMode?.onDisable()

        prevX = 0.0
        prevY = 0.0
        prevZ = 0.0
        pauseTicks = 0

        shouldSimulateBlock = false

        shouldBlink = false
        BlinkUtils.unblink()
    }

    override fun onEnable() {
        antiVoidMode?.onEnable()
    }

    private fun syncNewModeSettings() {
        WatchdogNCPAntiVoid.distance = wdVoidDistance.toDouble()
        VulcanAntiVoid.distance = vulcanVoidDistance.toDouble()
    }

    val onNewModeMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.PRE) return@handler
        syncNewModeSettings()
        antiVoidMode?.onPreMotion(event)
    }

    val onNewModeMove = handler<MoveEvent> { event ->
        antiVoidMode?.onMove(event)
    }

    val onNewModeStrafe = handler<StrafeEvent> { event ->
        antiVoidMode?.onStrafe(event)
    }

    val onNewModeJump = handler<JumpEvent> { event ->
        antiVoidMode?.onJump(event)
    }

    val onNewModeWorld = handler<WorldEvent> {
        antiVoidMode?.onWorldChange()
    }

    val onUpdate = handler<UpdateEvent> {
        detectedLocation = null

        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.onGround && BlockPos(thePlayer).down().block !is BlockAir) {
            prevX = thePlayer.prevPosX
            prevY = thePlayer.prevPosY
            prevZ = thePlayer.prevPosZ
            shouldSimulateBlock = false
        }

        if (!thePlayer.onGround && !thePlayer.isOnLadder && !thePlayer.isInWater) {
            val fallingPlayer = FallingPlayer(thePlayer)

            detectedLocation = fallingPlayer.findCollision(60)?.pos

            if (detectedLocation != null && abs(thePlayer.posY - detectedLocation!!.y) +
                thePlayer.fallDistance <= maxFallDistance
            ) {
                lastFound = thePlayer.fallDistance
            }

            if (thePlayer.fallDistance - lastFound > maxDistanceWithoutGround) {
                when (mode.lowercase()) {
                    "teleportback" -> {
                        thePlayer.setPositionAndUpdate(prevX, prevY, prevZ)
                        thePlayer.fallDistance = 0F
                        thePlayer.motionY = 0.0
                    }

                    "flyflag" -> {
                        thePlayer.motionY += 0.1
                        thePlayer.fallDistance = 0F
                    }

                    "ongroundspoof" -> sendPacket(C03PacketPlayer(true))

                    "motionteleport-flag" -> {
                        thePlayer.setPositionAndUpdate(thePlayer.posX, thePlayer.posY + 1f, thePlayer.posZ)
                        sendPacket(C04PacketPlayerPosition(thePlayer.posX, thePlayer.posY, thePlayer.posZ, true))
                        thePlayer.motionY = 0.1

                        strafe()
                        thePlayer.fallDistance = 0f
                    }

                    "ghostblock" -> shouldSimulateBlock = true
                }
            }
        }

        if (mode == "Blink") {
            val simPlayer = SimulatedPlayer.fromClientPlayer(thePlayer.movementInput)

            repeat(20) {
                simPlayer.tick()
            }

            if (simPlayer.isOnLadder() || simPlayer.inWater || simPlayer.isInLava() || simPlayer.isInWeb || simPlayer.isSneaking()) {
                if (BlinkUtils.isBlinking) BlinkUtils.unblink()
                return@handler
            }

            if (thePlayer.fallDistance < 1.5f && !simPlayer.onGround && simPlayer.fallDistance >= maxFallDistance) {
                shouldBlink = true
            } else if (BlinkUtils.isBlinking) {
                WaitTickUtils.schedule(blinkDelay) {
                    shouldBlink = false
                    BlinkUtils.cancel()
                }
            }
        }
    }

    val onBlockBB = handler<BlockBBEvent> { event ->
        // ---- 新模式分发 ----
        if (antiVoidMode != null) {
            antiVoidMode!!.onBlockBB(event)
            return@handler
        }

        if (mode == "GhostBlock" && shouldSimulateBlock) {
            if (event.y < mc.thePlayer.posY.toInt()) {
                event.boundingBox = AxisAlignedBB(
                    event.x.toDouble(),
                    event.y.toDouble(),
                    event.z.toDouble(),
                    event.x + 1.0,
                    event.y + 1.0,
                    event.z + 1.0
                )
            }
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val packet = event.packet

        // ---- 新模式分发 ----
        val extra = antiVoidMode
        if (extra != null) {
            extra.onPacket(event)
            return@handler
        }

        // Stop considering non colliding blocks as collidable ones on setback.
        if (packet is S08PacketPlayerPosLook) {
            shouldSimulateBlock = false
        }

        if (!onScaffold && mode == "Blink" && pauseTicks > 0) {
            pauseTicks--
            return@handler
        }

        if (!onScaffold && mode == "Blink") {
            // Check for block placement
            if (packet is C08PacketPlayerBlockPlacement) {
                if (packet.stack?.item is ItemBlock) {

                    if (BlinkUtils.isBlinking && player.fallDistance < 1.5f) BlinkUtils.unblink()
                    if (pauseTicks < ticksToDelay) pauseTicks = ticksToDelay
                }
            }

            // Check for scaffold
            if ((Scaffold.handleEvents() || Tower.handleEvents()) && Scaffold.placeRotation != null) {
                if (BlinkUtils.isBlinking && player.fallDistance < 1.5f) BlinkUtils.unblink()
                if (pauseTicks < ticksToDelay) pauseTicks = ticksToDelay
            }
        }

        if (mode != "Blink" || !shouldBlink) return@handler

        if (player.isDead || player.ticksExisted < 20) {
            BlinkUtils.unblink()
            return@handler
        }

        if (Blink.blinkingSend() || Blink.blinkingReceive()) {
            BlinkUtils.unblink()
            return@handler
        }

        BlinkUtils.blink(packet, event, sent = true, receive = false)
    }

    val onRender3D = handler<Render3DEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (detectedLocation == null || !indicator ||
            thePlayer.fallDistance + (thePlayer.posY - (detectedLocation!!.y + 1)) < 3
        ) return@handler

        val (x, y, z) = detectedLocation ?: return@handler

        val renderManager = mc.renderManager

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_BLEND)
        glLineWidth(2f)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)

        glColor(Color(255, 0, 0, 90))
        drawFilledBox(
            AxisAlignedBB.fromBounds(
                x - renderManager.renderPosX,
                y + 1 - renderManager.renderPosY,
                z - renderManager.renderPosZ,
                x - renderManager.renderPosX + 1.0,
                y + 1.2 - renderManager.renderPosY,
                z - renderManager.renderPosZ + 1.0
            )
        )

        glEnable(GL_TEXTURE_2D)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDisable(GL_BLEND)

        val fallDist = floor(thePlayer.fallDistance + (thePlayer.posY - (y + 0.5))).toInt()

        renderNameTag("${fallDist}m (~${max(0, fallDist - 3)} damage)", x + 0.5, y + 1.7, z + 0.5)

        resetColor()
    }
}