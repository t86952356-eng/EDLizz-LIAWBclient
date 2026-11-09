/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.matrix

import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook

/**
 * Speed「Matrix」模式：
 * 低摩擦起步 + 潜行时计时器爆发 + 复位包（C06/C04/C06）。
 */
object MatrixSpeed : SpeedMode("Matrix") {

    /** Timer Boost (on sneak)（默认 30，1..100）。 */
    var timerBoostOnSneak = 30f

    private var ticksSinceSneak = 0

    override fun onEnable() {
        ticksSinceSneak = 0
        mc.timer.timerSpeed = 1f
    }

    override fun onDisable() {
        mc.timer.timerSpeed = 1f
    }

    override fun onJump(event: net.ccbluex.liquidbounce.event.JumpEvent) {
        ticksSinceSneak++
    }

    override fun onStrafeEvent(event: StrafeEvent) {
        val player = mc.thePlayer ?: return
        val onGroundAligned = player.posY % 0.015625 == 0.0
        val slow = PortedMove.speed() < 0.2
        if (onGroundAligned || slow) {
            PortedMove.strafe()
        }

        if (PortedMove.speed() < 0.195 && !player.isUsingItem) {
            PortedMove.strafe(0.195)
        }

        if (player.onGround) {
            player.motionX *= 1.001
            player.motionZ *= 1.001
            PortedMove.strafe()
        }

        if (PortedTrackers.ticksSinceVelocity > 1) {
            player.motionY -= 0.00348
        }

        if (PortedTrackers.itemUseTicks == 1) {
            PortedMove.partialStrafePercent(65.0)
        }

        if (PortedMove.collidesAt(player.motionY)) {
            PortedMove.partialStrafePercent(65.0)
        }

        if (PortedTrackers.ticksSinceVelocity < 12) {
            PortedMove.strafe()
        }

        PortedMove.useDiagonalSpeed()
        if (mc.gameSettings.keyBindSneak.isKeyDown) {
            ticksSinceSneak = 0
            PortedMove.strafe(0.07)
            mc.timer.timerSpeed = timerBoostOnSneak
        } else {
            ticksSinceSneak++
            if (ticksSinceSneak == 1) {
                sendPacket(
                    C06PacketPlayerPosLook(
                        player.posX, player.posY, player.posZ,
                        player.rotationYaw, player.rotationPitch, false
                    ), triggerEvent = false
                )
                sendPacket(
                    C04PacketPlayerPosition(player.posX, player.posY, player.posZ, false),
                    triggerEvent = false
                )
                sendPacket(
                    C06PacketPlayerPosLook(
                        player.posX, player.posY, player.posZ,
                        player.rotationYaw, player.rotationPitch, false
                    ), triggerEvent = false
                )
            }
        }
    }

    override fun onMoveInput(event: net.ccbluex.liquidbounce.event.MovementInputEvent) {
        event.originalInput.sneak = false
        event.originalInput.jump = true
    }

    override fun onPreMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        if (player.onGround && !player.isUsingItem) {
            player.jump()
        }
    }
}
