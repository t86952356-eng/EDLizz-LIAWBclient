/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import kotlin.math.max

/**
 * Fly「WatchdogNCP-New」模式：阶段机飞行（阶梯加速）+ 原版踢出绕过。
 */
object WatchdogNCPNew : FlyMode("WatchdogNCP-New") {

    /** Speed（默认 1.2，0.8..2）。 */
    var flySpeed = 1.2

    /** Vanilla Kick Bypass（默认开）。 */
    var vanillaKickBypass = true

    private var moveSpeed = 0.0
    private var stage = 0
    private var ticks = 0

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        moveSpeed = 0.0
        stage = if (player.onGround) 0 else -1
        ticks = 0
    }

    override fun onMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        event.onGround = true
    }

    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return

        if (!player.isMoving || player.isCollidedHorizontally) {
            stage = -1
        }

        if (vanillaKickBypass && ticks > 125) {
            stage = -1
            ticks = 0
            sendPacket(
                C04PacketPlayerPosition(player.posX + 5.0, player.posY + 1.0, player.posZ + 5.0, true),
                triggerEvent = false
            )
            return
        }

        when (stage) {
            -1 -> {
                player.motionY = 0.0
                event.y = -1.0E-5
                return
            }
            0 -> moveSpeed = 0.3
            1 -> if (player.onGround) {
                player.motionY = 0.3999
                event.y = 0.3999
                moveSpeed *= 2.14
            }
            2 -> moveSpeed = flySpeed
            else -> {
                moveSpeed -= moveSpeed / 109.0
                player.motionY = 0.0
                event.y = -1.0E-5
            }
        }

        player.jumpMovementFactor = 0.0f
        setMoveEvent(event, max(moveSpeed, PortedMove.allowedHorizontalDistance()))
        stage++
        ticks++
    }

    /** 与原实现 setMoveEvent 相同的方向计算（含斜向 45° 融合）。 */
    private fun setMoveEvent(event: MoveEvent, speed: Double) {
        val player = mc.thePlayer ?: return
        var forward = player.movementInput.moveForward.toDouble()
        var strafe = player.movementInput.moveStrafe.toDouble()
        var yaw = player.rotationYaw.toDouble()

        if (forward == 0.0 && strafe == 0.0) {
            event.x = 0.0
            event.z = 0.0
            return
        }

        if (strafe > 0.0) strafe = 1.0 else if (strafe < 0.0) strafe = -1.0

        if (forward != 0.0) {
            if (strafe > 0.0) {
                yaw += if (forward > 0.0) -45.0 else 45.0
            } else if (strafe < 0.0) {
                yaw += if (forward > 0.0) 45.0 else -45.0
            }
            strafe = 0.0
            if (forward > 0.0) forward = 1.0 else if (forward < 0.0) forward = -1.0
        }

        val cos = Math.cos(Math.toRadians(yaw + 90.0))
        val sin = Math.sin(Math.toRadians(yaw + 90.0))
        event.x = forward * speed * cos + strafe * speed * sin
        event.z = forward * speed * sin - strafe * speed * cos
    }
}
