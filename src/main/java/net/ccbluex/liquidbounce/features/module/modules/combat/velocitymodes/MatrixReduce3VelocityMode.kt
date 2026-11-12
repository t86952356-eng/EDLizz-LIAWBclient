/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Velocity「MatrixReduce3」模式 —— 源客户端 Velocity 的 MatrixReduce3：
 * 击退包直接取消并按条件重建 XZ（静止时保留 0.1 倍击退 / 移动时可 BoostAfterReduce）；
 * 零水平分量的特殊击退包（X=0 且 Z=0）也取消（可保留 Y 分量）。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.client.realMotionX
import net.ccbluex.liquidbounce.utils.client.realMotionY
import net.ccbluex.liquidbounce.utils.client.realMotionZ
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceXZ
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

object MatrixReduce3VelocityMode : VelocityMode("MatrixReduce3") {

    /** BoostAfterReduce（默认关）。 */
    var boostAfterReduce = false

    /** BoostFactor（默认 0.33，0..5）。 */
    var boostFactor = 0.33f

    /** BoostCooldown（默认 0，0..2000 ms）。 */
    var boostDelay = 0

    /** KeepMotionYVelocity（默认开）：取消特殊击退包时是否把 Y 分量写回玩家。 */
    var keepMotionY = true

    private val boostTimer = MSTimer()

    override fun onEnable() {
        boostTimer.reset()
    }

    override fun onDisable() {
        boostTimer.reset()
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (event.isCancelled) return

        // 零水平分量的特殊击退包：取消（KeepMotionY 时保留 Y 分量）
        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId &&
            packet.realMotionX == 0.0 && packet.realMotionZ == 0.0
        ) {
            if (keepMotionY) player.motionY = packet.realMotionY
            event.cancelEvent()
            return
        }

        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId &&
            packet.motionY > 0 && (packet.motionX != 0 || packet.motionZ != 0)
        ) {
            event.cancelEvent()

            if (abs(packet.realMotionY) >= 0.1f) {
                player.motionY = (packet.motionY / 8000f).toDouble()

                val currentSpeed = hypot(player.motionX, player.motionZ)

                // 计算击退包中的原始水平速度
                val knockbackX = packet.motionX / 8000f
                val knockbackZ = packet.motionZ / 8000f
                val knockbackSpeed = hypot(knockbackX, knockbackZ)

                if (!player.isMoving) {
                    val reducedSpeed = max(knockbackSpeed * 0.1, currentSpeed)
                    if (knockbackSpeed > 0) {
                        player.motionX = knockbackX / knockbackSpeed * reducedSpeed
                        player.motionZ = knockbackZ / knockbackSpeed * reducedSpeed
                    }
                } else if (boostAfterReduce && boostTimer.hasTimePassed(boostDelay.toLong())) {
                    reduceXZ(boostFactor.toDouble() + 1)
                    boostTimer.reset()
                }
            }
        }
    }
}
