/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.LongJumpMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity

/**
 * LongJump「Grim」模式：
 * 计时器 2x + 每帧俯仰抖动；起跳后冻结 motion 并扣住一个飞行包，
 * 循环发送成对的 C03(onGround=false) 飞行包；拉回（S08）后自适应；
 * 收到向下击退或 20 tick 超时后自动关闭。
 * 逻辑按原版 1.8.9 实现等价移植（原 PostStrafe 家族事件 → onStrafe）。
 */
object GrimLongJump : LongJumpMode("Grim") {

    private var strafeTicks = 0
    private var cancelNextPacket = false
    private var timeoutTicks = 0
    private var started = false
    private var stage = 0

    override fun onEnable() {
        strafeTicks = 0
        started = false
        stage = 0
        cancelNextPacket = false
        timeoutTicks = 0
    }

    override fun onDisable() {
        // 原实现由模块级 onDisable 重置计时器；Lizz 模块层没有该逻辑，这里在模式层等价补齐
        mc.timer.timerSpeed = 1f
        RotationUtils.currentRotation = null
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        mc.timer.timerSpeed = 2f
        // 原实现：preMotionEvent.setPitch(pitch + random * 0.1)（出站俯仰抖动）
        RotationUtils.currentRotation =
            Rotation(player.rotationYaw, player.rotationPitch + Math.random().toFloat() * 0.1f)

        if (!started) return

        player.motionY = 0.42
        if (stage == 1) {
            stage = 2
            timeoutTicks = 0
            return
        }
        if (stage != 2) {
            player.motionY = 0.0
            player.motionX = 0.0
            player.motionZ = 0.0
            cancelNextPacket = true
            timeoutTicks++
            if (timeoutTicks <= 20) return
            LongJump.toggle()
            return
        }

        repeat(2) {
            sendPacket(C03PacketPlayer(false), triggerEvent = false)
        }
        cancelNextPacket = true
        stage = 0
    }

    override fun onStrafe() {
        val player = mc.thePlayer ?: return
        if (strafeTicks == 0) {
            player.jump()
        }
        if (strafeTicks == 1) {
            started = true
            repeat(20) {
                sendPacket(C03PacketPlayer(false), triggerEvent = false)
            }
        }
        strafeTicks++
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventType == EventState.RECEIVE) {
            val packet = event.packet
            if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId &&
                packet.motionY / 8000.0 < 0.0
            ) {
                LongJump.toggle()
            }
            if (packet is S08PacketPlayerPosLook) {
                stage = 1
            }
        } else if (event.eventType == EventState.SEND) {
            val packet = event.packet
            if (packet is C03PacketPlayer && cancelNextPacket) {
                cancelNextPacket = false
                event.cancelEvent()
            }
        }
    }
}
