/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Velocity「MatrixBoost」模式（原 Matrix）：
 * 吃掉击退包，落地/下落时直接套用垂直击退；
 * 静止时按击退水平分量 11% 起步，空中可选 Boost 加速；
 * 受击期间可选 HurtStrafe 重设水平速度。
 */
object MatrixBoostVelocityMode : VelocityMode("MatrixBoost") {

    /** Matrix-HurtStrafe（默认关）。 */
    var hurtStrafe = false

    /** StrafeHurtTime-Min（默认 6，1..10）。 */
    var strafeHurtTimeMin = 6

    /** StrafeHurtTime-Max（默认 10，1..10）。 */
    var strafeHurtTimeMax = 10

    /** Matrix-Boost（默认关）。 */
    var boost = false

    /** Matrix-BoostSpeed（默认 0，0.1..0.7）。 */
    var boostSpeed = 0.0

    /** Matrix-OnlyCombat（默认关）。 */
    var onlyCombat = false

    private var receivedVelocity = false

    /** 与源 CombatHandler.inCombat 等价：1600ms 内攻击过实体。 */
    private var lastAttackTime = 0L

    private val inCombat: Boolean
        get() = System.currentTimeMillis() - lastAttackTime < 1600L

    override fun onEnable() {
        receivedVelocity = false
    }

    override fun onDisable() {
        receivedVelocity = false
    }

    override fun onAttack(target: net.minecraft.entity.Entity?) {
        lastAttackTime = System.currentTimeMillis()
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (receivedVelocity && hurtStrafe && (!onlyCombat || inCombat) &&
            player.hurtTime >= strafeHurtTimeMin && player.hurtTime <= strafeHurtTimeMax
        ) {
            // strafe()：按当前速度沿移动方向重设（源 setSpeed(getSpeed(), true)）
            PortedMove.strafe()
        }

        if (player.hurtTime <= 0) {
            receivedVelocity = false
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        if (event.isCancelled) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
            event.cancelEvent()

            if (packet.motionY / 8000.0 > 0.0) {
                val velX = packet.motionX / 8000.0
                val velZ = packet.motionZ / 8000.0

                if (!PortedMove.isMoving()) {
                    // setSpeed(max(getSpeed(vx,vz)*0.11, getSpeed()), false)：无输入也设置
                    setSpeed(max(sqrt(velX * velX + velZ * velZ) * 0.11, PortedMove.speed()))
                } else if (!player.onGround && boost && (!onlyCombat || inCombat)) {
                    // setSpeed(max(getSpeed(), boostSpeed), false)
                    setSpeed(max(PortedMove.speed(), boostSpeed))
                }

                if (player.fallDistance > 0.0f || player.onGround) {
                    player.motionY = packet.motionY / 8000.0
                }

                receivedVelocity = true
            }
        }
    }

    /** 源 setSpeed(speed, false)：沿输入方向（无输入时沿视线）设置固定速度。 */
    private fun setSpeed(speed: Double) {
        val p = mc.thePlayer ?: return
        val input = p.movementInput
        val moveForward = input.moveForward.toDouble()
        val moveStrafe = input.moveStrafe.toDouble()
        val yaw = if (moveForward == 0.0 && moveStrafe == 0.0) {
            Math.toRadians(p.rotationYaw.toDouble())
        } else {
            Math.atan2(-moveStrafe, moveForward) + Math.toRadians(p.rotationYaw.toDouble())
        }
        p.motionX = -Math.sin(yaw) * speed
        p.motionZ = Math.cos(yaw) * speed
    }
}
