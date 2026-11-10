/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.aac.AACv1
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.aac.AACv2
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.aac.AACv3
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.ncp.NCP
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.Buzz
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.GrimLongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.Hycraft
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.Redesky
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.VulcanLongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.VerusDamage
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.VerusDamage.damaged
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other.WatchdogFireBallLongJump
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump

object LongJump : Module("LongJump", Category.MOVEMENT) {

    private val longJumpModes = arrayOf(
        // NCP
        NCP,

        // AAC
        AACv1, AACv2, AACv3,

        // Other
        Redesky, Hycraft, Buzz, VerusDamage,
        WatchdogFireBallLongJump, GrimLongJump, VulcanLongJump
    )

    private val modes = longJumpModes.map { it.modeName }.toTypedArray()

    val mode by choices("Mode", modes, "NCP")
    val ncpBoost by float("NCPBoost", 4.25f, 1f..10f) { mode == "NCP" }

    private val autoJump by boolean("AutoJump.kt", true)

    /** 新模式集合（自包含：自带起跳/时序，不走 autoJump/jumped 机制）。 */
    private val newLongJumpModes = arrayOf(WatchdogFireBallLongJump, GrimLongJump, VulcanLongJump)

    private val newMode get() = newLongJumpModes.firstOrNull { it.modeName == mode }

    val autoDisable by boolean("AutoDisable", true) { mode == "VerusDamage" || newMode != null }

    var jumped = false
    var canBoost = false
    var teleported = false

    /** 原模块级 Auto Disable 的滞空状态（新模式适用）。 */
    private var wasInAir = false

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.PRE) return@handler
        val player = mc.thePlayer ?: return@handler

        // ---- 原模块级 Auto Disable：滞空后落地即关闭（新模式适用；
        //      源实现同样排除自带关闭逻辑的 Watchdog/Watchdog 2，三者均不适用）----
        val extra = newMode
        if (extra != null) {
            if (autoDisable && wasInAir && player.onGround) {
                wasInAir = false
                toggle()
                return@handler
            }
            wasInAir = !player.onGround
            extra.onPreMotion(event)
        }
    }

    val onStrafe = handler<StrafeEvent> {
        newMode?.onStrafe()
    }

    val onPacket = handler<PacketEvent> { event ->
        newMode?.onPacket(event)
    }

    val onUpdate = handler<UpdateEvent> {
        // ---- 新模式分发：自包含 ----
        newMode?.let {
            it.onUpdate()
            return@handler
        }

        if (jumped) {
            val mode = mode

            if (mc.thePlayer.onGround || mc.thePlayer.capabilities.isFlying) {
                jumped = false

                if (mode == "NCP") {
                    mc.thePlayer.motionX = 0.0
                    mc.thePlayer.motionZ = 0.0
                }
                return@handler
            }

            modeModule.onUpdate()
        }
        if (autoJump && mc.thePlayer.onGround && mc.thePlayer.isMoving) {
            if (autoDisable && !damaged) {
                return@handler
            }

            jumped = true
            mc.thePlayer.tryJump()
        }
    }

    val onMove = handler<MoveEvent> { event ->
        modeModule.onMove(event)
    }

    override fun onEnable() {
        wasInAir = false
        modeModule.onEnable()
    }

    override fun onDisable() {
        // 原模块级 onDisable：重置计时器（所有模式受益）
        mc.timer.timerSpeed = 1f
        wasInAir = false
        modeModule.onDisable()
    }

    val onJump = handler<JumpEvent>(always = true) { event ->
        jumped = true
        canBoost = true
        teleported = false

        if (handleEvents()) {
            modeModule.onJump(event)
        }
    }

    override val tag
        get() = mode

    private val modeModule
        get() = longJumpModes.find { it.modeName == mode }!!
}
