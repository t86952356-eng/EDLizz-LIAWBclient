/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.play.server.S12PacketEntityVelocity

/**
 * Velocity「Matrix」模式：吃掉击退包并直接套用垂直速度，
 * 配合移动阶段的速度修正。
 */
object MatrixVelocityMode : VelocityMode("Matrix") {

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != net.ccbluex.liquidbounce.event.EventState.RECEIVE) return
        val packet = event.packet
        if (packet is S12PacketEntityVelocity) {
            val player = mc.thePlayer ?: return
            if (packet.entityID == player.entityId) {
                player.motionY = packet.motionY / 8000.0
                if (!Speed.handleEvents() && PortedMove.isMoving()) {
                    event.cancelEvent()
                }
            }
        }
    }

    override fun onStrafe() {
        val player = mc.thePlayer ?: return
        val speedEnabled = Speed.handleEvents()
        val flyEnabled = Fly.handleEvents()
        val ticksSinceVelocity = PortedTrackers.ticksSinceVelocity

        if (!PortedMove.isMoving() && ticksSinceVelocity == 1) {
            player.motionX *= -0.1
            player.motionZ *= -0.1
        } else if (ticksSinceVelocity == 1 && !speedEnabled) {
            PortedMove.strafe()
        }

        if (ticksSinceVelocity < 6 && speedEnabled) {
            PortedMove.strafe()
        } else if (ticksSinceVelocity > 1 && !speedEnabled && ticksSinceVelocity < 15 && !flyEnabled) {
            player.motionY -= 0.00348
        }
    }
}
