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
import net.minecraft.client.entity.EntityPlayerSP
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

    /** OnlyDamage（默认关）：只有玩家受到伤害后，飞行才生效。 */
    var onlyDamage = false

    /** AutoDamage（默认关）：开启模块时自动触发「damage」自伤行为。 */
    var autoDamage = false

    private var moveSpeed = 0.0
    private var stage = 0
    private var ticks = 0

    /** OnlyDamage：已受到伤害（受到伤害后飞行才启动）。 */
    private var damaged = false

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        moveSpeed = 0.0
        stage = if (player.onGround) 0 else -1
        ticks = 0
        damaged = false

        // AutoDamage：开启模块时自动触发自伤（NCP Glitch 包序列）
        if (autoDamage) triggerSelfDamage(player)
    }

    /** 与 Damage 模块 NCP「Glitch」相同的自伤包序列。 */
    private fun triggerSelfDamage(player: EntityPlayerSP) {
        val x = player.posX
        val y = player.posY
        val z = player.posZ
        repeat(65) {
            sendPacket(C04PacketPlayerPosition(x, y + 0.049, z, false), triggerEvent = false)
            sendPacket(C04PacketPlayerPosition(x, y, z, false), triggerEvent = false)
        }
        sendPacket(C04PacketPlayerPosition(x, y, z, true), triggerEvent = false)
    }

    override fun onMotion(event: MotionEvent) {
        // OnlyDamage：未受伤前完全禁用飞行（不伪造 onGround、不干预任何运动），
        // 玩家保持原版下落/行走；一旦受伤立刻激活完整飞行
        if (onlyDamage && !damaged) {
            checkDamage()
            return
        }

        if (event.eventState != EventState.PRE) return
        event.onGround = true
    }

    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return

        // OnlyDamage：未受伤前完全禁用飞行（原版移动，不悬停）
        if (onlyDamage && !damaged) {
            checkDamage()
            return
        }

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

    /** OnlyDamage：检测到受伤后激活飞行，并重置阶段机（从受击瞬间重新开始 boost）。 */
    private fun checkDamage() {
        val player = mc.thePlayer ?: return
        if (player.hurtTime > 0 && !damaged) {
            damaged = true
            moveSpeed = 0.0
            stage = if (player.onGround) 0 else -1
            ticks = 0
        }
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
