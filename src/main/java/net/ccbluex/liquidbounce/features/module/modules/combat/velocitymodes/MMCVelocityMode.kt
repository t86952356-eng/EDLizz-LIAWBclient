/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.Velocity
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.play.server.S12PacketEntityVelocity

/**
 * Velocity「MMC」模式：受击后第 1 tick 清零水平速度（必要时狠压垂直），
 * 吃食物末段配合 moveFlying 微调。
 */
object MMCVelocityMode : VelocityMode("MMC") {

    private var receivedVelocity = false

    override fun onEnable() {
        receivedVelocity = false
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val packet = event.packet
        if (packet is S12PacketEntityVelocity) {
            val player = mc.thePlayer ?: return
            if (packet.entityID == player.entityId) {
                receivedVelocity = true
            }
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        if (!Velocity.onSwing || player.isSwingInProgress) {
            if (player.onGround && player.hurtTime > 0) {
                // 原实现此处调用全局 BadPackets 探测（结果未使用），省略
            }

            if (PortedTrackers.ticksSinceVelocity == 1) {
                player.motionX *= 0.0
                player.motionZ *= 0.0
            }

            if (PortedTrackers.ticksSinceVelocity == 1 && (player.isJumping || Speed.handleEvents())) {
                player.motionY -= 9.0
            }

            if (player.itemInUseCount == 1 && PortedTrackers.ticksSinceVelocity < 4 && PortedMove.speed() < 0.31) {
                PortedMove.moveFlying(0.05)
            }
        }
    }
}
