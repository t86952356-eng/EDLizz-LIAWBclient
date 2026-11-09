/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.event.async.loopSequence
import net.ccbluex.liquidbounce.event.async.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.aac.*
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.updatedncp.UpdatedNCP
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.updatedncp.UpdatedNCP2
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp.WatchdogNCPStable
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp.WatchdogNCPFree
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp.WatchdogNCP
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other.MatrixTest
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.ncp.NCP
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.ncp.OldNCP
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.matrix.Matrix
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.matrix.MatrixDamage
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp.WatchdogNCPNew
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other.*
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.spartan.BugSpartan
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.spartan.Spartan
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.spartan.Spartan2
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.vanilla.SmoothVanilla
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.vanilla.Vanilla
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.verus.Verus
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.verus.VerusGlide
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.vulcan.Vulcan
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.vulcan.VulcanGhost
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.vulcan.VulcanOld
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.stop
import net.ccbluex.liquidbounce.utils.extensions.stopXZ
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawPlatform
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import org.lwjgl.input.Keyboard
import java.awt.Color

object Fly : Module("Fly", Category.MOVEMENT) {
    private val flyModes = arrayOf(
        Vanilla, SmoothVanilla,

        // NCP
        NCP, OldNCP,

        // AAC
        AAC1910, AAC305, AAC316, AAC3312, AAC3312Glide, AAC3313,

        // CubeCraft
        CubeCraft,

        // WatchdogNCP
        WatchdogNCP, WatchdogNCPStable, WatchdogNCPFree,

        // Other server specific flys
        NeruxVace, Minesucht, UpdatedNCP, UpdatedNCP2,

        // Spartan
        Spartan, Spartan2, BugSpartan,

        // Vulcan
        Vulcan, VulcanOld, VulcanGhost,

        // Verus
        Verus, VerusGlide,

        // Other anti-cheats
        MineSecure, HawkEye, HAC, WatchCat,

        // Other
        Jetpack, KeepAlive, Collide, Jump, Flag, Fireball, MatrixTest,

        // 新增模式
        WatchdogNCPNew, Matrix, MatrixDamage
    )

    /**
     * Old/Deprecated Modes
     */
    private val deprecatedMode = arrayOf(
        Spartan, Spartan2, BugSpartan,

        MineSecure, HawkEye, HAC, WatchCat, NeruxVace, Minesucht,

        UpdatedNCP, UpdatedNCP2,

        WatchdogNCP, WatchdogNCPStable, WatchdogNCPFree,

        NCP, OldNCP,

        AAC1910, AAC305, AAC316, AAC3312, AAC3312Glide, AAC3313,

        CubeCraft
    )

    private val showDeprecated by boolean("DeprecatedMode", true).onChanged { value ->
        modeValue.changeValue(modesList.first { it !in deprecatedMode }.modeName)
        modeValue.updateValues(modesList.filter { value || it !in deprecatedMode }.map { it.modeName }.toTypedArray())
    }

    private var modesList = flyModes

    val modeValue = choices("Mode", modesList.map { it.modeName }.toTypedArray(), "Vanilla")
    val mode by modeValue

    val vanillaSpeed by float("VanillaSpeed", 2f, 0f..10f) {
        mode in arrayOf(
            "Vanilla",
            "KeepAlive",
            "MineSecure",
            "BugSpartan"
        )
    }.subjective()
    val vanillaKickBypass by boolean(
        "VanillaKickBypass",
        false
    ) { mode in arrayOf("Vanilla", "SmoothVanilla") }.subjective()
    val ncpMotion by float("NCPMotion", 0f, 0f..1f) { mode == "NCP" }

    // AAC
    val aacSpeed by float("AAC1.9.10-Speed", 0.3f, 0f..1f) { mode == "AAC1.9.10" }
    val aacFast by boolean("AAC3.0.5-Fast", true) { mode == "AAC3.0.5" }
    val aacMotion by float("AAC3.3.12-Motion", 10f, 0.1f..10f) { mode == "AAC3.3.12" }
    val aacMotion2 by float("AAC3.3.13-Motion", 10f, 0.1f..10f) { mode == "AAC3.3.13" }

    // WatchdogNCP
    val hypixelBoost by boolean("Hypixel-Boost", true) { mode == "WatchdogNCP" }
    val hypixelBoostDelay by int("Hypixel-BoostDelay", 1200, 50..10000) { mode == "WatchdogNCP" && hypixelBoost }
    val hypixelBoostTimer by float("Hypixel-BoostTimer", 1f, 0.1f..5f) { mode == "WatchdogNCP" && hypixelBoost }

    // Other
    val neruxVaceTicks by int("NeruxVace-Ticks", 6, 2..20) { mode == "NeruxVace" }

    // Verus
    val damage by boolean("Damage", false) { mode == "Verus" }
    val timerSlow by boolean("TimerSlow", true) { mode == "Verus" }
    val boostTicksValue by int("BoostTicks", 20, 1..30) { mode == "Verus" }
    val boostMotion by float("BoostMotion", 6.5f, 1f..9.85f) { mode == "Verus" }
    val yBoost by float("YBoost", 0.42f, 0f..10f) { mode == "Verus" }

    // UpdatedNCP
    val stable by boolean("Stable", false) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }
    val timerSlowed by boolean("TimerSlowed", true) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }
    val boostSpeed by float("BoostSpeed", 6f, 1f..15f) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }
    val extraBoost by float("ExtraSpeed", 1f, 0.0F..2f) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }
    val stopOnLanding by boolean("StopOnLanding", true) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }
    val stopOnNoMove by boolean("StopOnNoMove", false) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }
    val debugFly by boolean("Debug", false) { mode == "UpdatedNCP" || mode == "UpdatedNCP2" }

    // Fireball
    val pitchMode by choices("PitchMode", arrayOf("Custom", "Smart"), "Custom") { mode == "Fireball" }
    val rotationPitch by float("Pitch", 90f, 0f..90f) { pitchMode != "Smart" && mode == "Fireball" }
    val invertYaw by boolean("InvertYaw", true) { pitchMode != "Smart" && mode == "Fireball" }

    val autoFireball by choices(
        "AutoFireball",
        arrayOf("Off", "Pick", "Spoof", "Switch"),
        "Spoof"
    ) { mode == "Fireball" }
    val swing by boolean("Swing", true) { mode == "Fireball" }
    val fireballTry by int("MaxFireballTry", 1, 0..2) { mode == "Fireball" }
    val fireBallThrowMode by choices("FireballThrow", arrayOf("Normal", "Edge"), "Normal") { mode == "Fireball" }
    val edgeThreshold by float(
        "EdgeThreshold",
        1.05f,
        1f..2f
    ) { fireBallThrowMode == "Edge" && mode == "Fireball" }

    val options = RotationSettings(this) { mode == "Fireball" }

    val autoJump by boolean("AutoJump.kt", true) { mode == "Fireball" }
    val matrixPeriod by int("MatrixPeriod", 2, 1..20) { mode == "MatrixTest" }     
    val matrixMotionY1 by float("MatrixMotionY1", -0.04f, -0.3f..0f) { mode == "MatrixTest" }
    val matrixMotionY2 by float("MatrixMotionY2", -0.04f, -0.3f..0f) { mode == "MatrixTest" }

    val matrixTimerSpeed by float("MatrixTimer", 0.9f, 0.1f..1.0f) { mode == "MatrixTest" }
    val matrixMoveTick by int("MatrixMoveTick", 20, 0..30) { mode == "MatrixTest" }
    val matrixOnlyOnDamage by boolean("MatrixOnlyOnDamage", true) { mode == "MatrixTest" }
    val matrixUseStuck by boolean("MatrixUseStuck", true) { mode == "MatrixTest" }

    // ---- WatchdogNCP-New（Fly，原 Funcraft 飞行）选项 ----
    private val flyNewSpeed by float("Speed", 1.2f, 0.8f..2f) { mode == "WatchdogNCP-New" }
    private val flyNewVanillaKickBypass by boolean("Vanilla Kick Bypass", true) { mode == "WatchdogNCP-New" }

    // ---- Matrix Damage（Fly）选项 ----
    private val matrixDamageTimerSpeed by float("Timer Speed", 30f, 1f..100f) { mode == "Matrix Damage" }
    private val matrixDamageSpeed by float("MatrixDamage-Speed", 0.07f, 0f..1f) { mode == "Matrix Damage" }
    private val matrixDamageNoSpeed by boolean("No Speed", false) { mode == "Matrix Damage" }
    private val matrixDamageDetectDamage by boolean("Detect Damage", true) { mode == "Matrix Damage" }
    private val matrixDamageAutoDisable by boolean("Auto Disable", true) { mode == "Matrix Damage" }
    private val matrixDamageFlyTicks by int("Fly Ticks", 1000, 0..1600) { !matrixDamageDetectDamage && mode == "Matrix Damage" }
    private val matrixDamageSelfDamage by boolean("Self Damage", true) { mode == "Matrix Damage" }
    private val matrixDamageNewSelfDamage by boolean("New Self Damage", false) { mode == "Matrix Damage" }
    private val matrixDamagePacket by boolean("Packet", true) { mode == "Matrix Damage" }
    private val matrixDamageMotionY by choices("Motion Y", arrayOf("None", "Simple", "Multiply"), "None") { mode == "Matrix Damage" }
    private val matrixDamageMotion by float("Motion", -0.01f, -0.3f..0.3f) { matrixDamageMotionY == "None" && mode == "Matrix Damage" }

    private fun syncNewFlyModeSettings() {
        WatchdogNCPNew.flySpeed = flyNewSpeed.toDouble()
        WatchdogNCPNew.vanillaKickBypass = flyNewVanillaKickBypass
        MatrixDamage.timerSpeed = matrixDamageTimerSpeed
        MatrixDamage.flySpeed = matrixDamageSpeed.toDouble()
        MatrixDamage.noSpeed = matrixDamageNoSpeed
        MatrixDamage.detectDamage = matrixDamageDetectDamage
        MatrixDamage.autoDisable = matrixDamageAutoDisable
        MatrixDamage.flyTicks = matrixDamageFlyTicks
        MatrixDamage.selfDamage = matrixDamageSelfDamage
        MatrixDamage.newSelfDamage = matrixDamageNewSelfDamage
        MatrixDamage.packet = matrixDamagePacket
        MatrixDamage.motionYMode = matrixDamageMotionY
        MatrixDamage.motion = matrixDamageMotion.toDouble()
    }


    // Visuals
    private val mark by boolean("Mark", true).subjective()

    var wasFired = false
    var firePosition: BlockPos? = null

    var jumpY = 0.0

    var startY = 0.0
        private set

    private val groundTimer = MSTimer()
    private var wasFlying = false

    override fun onEnable() {
        val thePlayer = mc.thePlayer ?: return

        startY = thePlayer.posY
        jumpY = thePlayer.posY
        wasFlying = mc.thePlayer.capabilities.isFlying

        modeModule.onEnable()
    }

    override fun onDisable() {
        val thePlayer = mc.thePlayer ?: return

        if (!mode.startsWith("AAC") && mode != "WatchdogNCP" && mode != "VerusGlide"
            && mode != "SmoothVanilla" && mode != "Vanilla" && mode != "Rewinside"
            && mode != "Fireball" && mode != "Collide" && mode != "Jump"
        ) {

            if (mode == "CubeCraft") thePlayer.stopXZ()
            else thePlayer.stop()
        }

        wasFired = false
        firePosition = null
        thePlayer.capabilities.isFlying = wasFlying
        mc.timer.timerSpeed = 1f
        thePlayer.speedInAir = 0.02f

        modeModule.onDisable()
    }

    val onUpdate = handler<UpdateEvent> {
        syncNewFlyModeSettings()
        modeModule.onUpdate()
    }

    val onTick = handler<GameTickEvent> {
        modeModule.onTick()
    }

    val onTick1 = loopSequence {
        if (mode == "Fireball" && wasFired) {
            waitTicks(2)
            state = false
        }
    }

    val onRender3D = handler<Render3DEvent> { event ->
        if (!mark || mode == "Vanilla" || mode == "SmoothVanilla")
            return@handler

        val y = startY + 2.0 + (if (mode == "WatchdogNCP-Stable") 0.42 else 0.0)
        drawPlatform(
            y,
            if (mc.thePlayer.entityBoundingBox.maxY < y) Color(0, 255, 0, 90) else Color(255, 0, 0, 90),
            1.0
        )

        modeModule.onRender3D(event)
    }

    val onPacket = handler<PacketEvent> { event ->
        mc.thePlayer ?: return@handler

        modeModule.onPacket(event)
    }

    val onBB = handler<BlockBBEvent> { event ->
        mc.thePlayer ?: return@handler

        modeModule.onBB(event)
    }

    val onJump = handler<JumpEvent> { event ->
        modeModule.onJump(event)
    }

    val onStep = handler<StepEvent> { event ->
        modeModule.onStep(event)
    }

    val onMotion = handler<MotionEvent> { event ->
        modeModule.onMotion(event)
    }

    val onMove = handler<MoveEvent> { event ->
        modeModule.onMove(event)
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        modeModule.onMoveInput(event)
    }

    val onStrafe = handler<StrafeEvent> { event ->
        modeModule.onStrafe(event)
    }

    fun handleVanillaKickBypass() {
        if (!vanillaKickBypass || !groundTimer.hasTimePassed(1000)) return
        val ground = calculateGround() + 0.5

        var posY = mc.thePlayer.posY
        while (posY > ground) {
            sendPacket(C04PacketPlayerPosition(mc.thePlayer.posX, posY, mc.thePlayer.posZ, true))
            if (posY - 8.0 < ground) break // Prevent next step
            posY -= 8.0
        }

        sendPacket(C04PacketPlayerPosition(mc.thePlayer.posX, ground, mc.thePlayer.posZ, true))
        posY = ground
        while (posY < mc.thePlayer.posY) {
            sendPacket(C04PacketPlayerPosition(mc.thePlayer.posX, posY, mc.thePlayer.posZ, true))
            if (posY + 8.0 > mc.thePlayer.posY) break // Prevent next step
            posY += 8.0
        }
        sendPacket(C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true))
        groundTimer.reset()
    }

    // TODO: Make better and faster calculation lol
    private fun calculateGround(): Double {
        val playerBoundingBox = mc.thePlayer.entityBoundingBox
        var blockHeight = 0.05
        var ground = mc.thePlayer.posY
        while (ground > 0.0) {
            val customBox = AxisAlignedBB.fromBounds(
                playerBoundingBox.maxX,
                ground + blockHeight,
                playerBoundingBox.maxZ,
                playerBoundingBox.minX,
                ground,
                playerBoundingBox.minZ
            )
            if (mc.theWorld.checkBlockCollision(customBox)) {
                if (blockHeight <= 0.05) return ground + blockHeight
                ground += blockHeight
                blockHeight = 0.05
            }
            ground -= blockHeight
        }
        return 0.0
    }

    override val tag
        get() = mode

    private val modeModule
        get() = flyModes.find { it.modeName == mode }!!
}
