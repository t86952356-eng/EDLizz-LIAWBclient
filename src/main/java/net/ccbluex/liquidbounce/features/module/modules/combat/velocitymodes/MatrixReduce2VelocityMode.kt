/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Velocity「MatrixReduce2」模式 —— 源客户端 Velocity 的 MatrixReduce2：
 * 收到击退包（hurtTime>=9）时——移动且非格挡/潜行/进食且在地面 → XZ 直接清零；
 * 其余情况（不动/格挡/潜行/进食/空中）→ XZ 乘 0.2。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceXZ
import net.minecraft.network.play.server.S12PacketEntityVelocity

object MatrixReduce2VelocityMode : VelocityMode("MatrixReduce2") {

    private var hasReceivedVelocity = false

    override fun onEnable() {
        hasReceivedVelocity = false
    }

    override fun onDisable() {
        hasReceivedVelocity = false
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (event.isCancelled) return

        // 收到自己的击退包（源外层条件：motionY > 0 且水平分量非零）
        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId &&
            packet.motionY > 0 && (packet.motionX != 0 || packet.motionZ != 0)
        ) {
            hasReceivedVelocity = true
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (hasReceivedVelocity && player.hurtTime >= 9) {
            if (player.isMoving && !(player.isBlocking || player.isSneaking || player.isEating || !player.onGround)) {
                reduceXZ(0.0)
            } else if (!player.isMoving || (player.isBlocking || player.isSneaking || player.isEating || !player.onGround)) {
                reduceXZ(0.2)
            }
            hasReceivedVelocity = false
        }
    }
}
