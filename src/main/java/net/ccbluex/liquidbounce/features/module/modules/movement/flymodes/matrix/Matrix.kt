/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.matrix

import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.Types
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.ViaUseItem
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.server.S08PacketPlayerPosLook

/**
 * Fly「Matrix」模式：吃掉 S08 传送并自行落位/起跳，
 * 二次传送时自动关闭；配合阶段性的速度爆发。
 */
object Matrix : FlyMode("Matrix") {

    private var teleported = false

    override fun onEnable() {
        teleported = false
    }

    override fun onDisable() {
        teleported = false
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is S08PacketPlayerPosLook) {
            event.cancelEvent()
            val x = packet.x
            val y = packet.y
            val z = packet.z
            val yaw = player.rotationYaw
            val pitch = player.rotationPitch

            // 1.9+ 跨版本时向服务器确认传送（1.8 管线无传送 ID，此分支仅在跨版本时有意义）
            if (ViaUseItem.isCrossVersionTo9Plus() &&
                ViaLoadingBase.getInstance().targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_9)
            ) {
                try {
                    val connections: Collection<UserConnection> = Via.getManager().connectionManager.connections
                    if (connections.isNotEmpty()) {
                        val user = connections.iterator().next()
                        val wrapper = PacketWrapper.create(ServerboundPackets1_9.ACCEPT_TELEPORTATION, user)
                        // 原实现读取的是被 Via 转换前的传送 ID；1.8 管线拿不到，用 0 兜底
                        wrapper.write(Types.VAR_INT as Type<Int>, 0)
                        wrapper.sendToServer(Protocol1_9To1_8::class.java)
                    }
                } catch (_: Throwable) {
                }
            }

            sendPacket(C06PacketPlayerPosLook(x, y, z, yaw, pitch, false), triggerEvent = false)
            player.setPosition(x, y, z)
            player.jump()
            if (teleported) {
                Fly.state = false
            }
            teleported = true
        }
    }

    override fun onMoveInput(event: MovementInputEvent) {
        val player = mc.thePlayer ?: return
        val useTicks = PortedTrackers.itemUseTicks
        if (useTicks > 15 && useTicks < 19) {
            event.originalInput.moveForward = 0f
        }
    }

    override fun onStrafe(event: StrafeEvent) {
        val player = mc.thePlayer ?: return

        if (PortedTrackers.ticksSinceVelocity > 1) {
            player.motionY += 0.00348
        }

        if (player.onGround) {
            player.jump()
        }

        if (player.fallDistance > 0.1 && !teleported) {
            player.motionY = 0.42
            PortedMove.strafe(1.97)
        }

        if (teleported && PortedTrackers.itemUseTicks == 20) {
            player.motionY = 0.42
            PortedMove.strafe(9.3)
        }
    }
}
