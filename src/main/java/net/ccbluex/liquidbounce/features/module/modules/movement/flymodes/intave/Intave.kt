/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.intave

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.minecraft.network.Packet
import net.minecraft.network.play.INetHandlerPlayClient
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import java.util.LinkedList
import java.util.Queue

/**
 * Fly「Intave」模式：10 倍计时器 + noClip + onGround 恒真 + 慢飞行能力伪装。
 * 收到 S08 后开始缓冲 S32/S12（吃掉回弹）。
 */
object Intave : FlyMode("Intave") {

    private var receivedFlag = false
    val serverBuffer: Queue<Packet<INetHandlerPlayClient>> = LinkedList()

    override fun onDisable() {
        val player = mc.thePlayer
        player?.noClip = false
        mc.timer?.timerSpeed = 1f
        player?.capabilities?.isFlying = false

        for (packet in serverBuffer) {
            try {
                @Suppress("UNCHECKED_CAST")
                (packet as Packet<INetHandlerPlayClient?>).processPacket(mc.netHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        serverBuffer.clear()
        receivedFlag = false
    }

    override fun onMotion(event: MotionEvent) {
        event.onGround = true
    }

    override fun onUpdate() {
        mc.timer?.timerSpeed = 10f

        val player = mc.thePlayer ?: return
        player.noClip = true

        if (!player.isMoving) {
            player.motionX *= 0.0
            player.motionZ *= 0.0
            player.motionY *= 0.0
        }

        player.capabilities.isFlying = true
        player.capabilities.flySpeed = 0.0085f
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is S08PacketPlayerPosLook) {
            receivedFlag = true
        }

        if ((packet is S32PacketConfirmTransaction || packet is S12PacketEntityVelocity) && receivedFlag) {
            event.cancelEvent()
            @Suppress("UNCHECKED_CAST")
            serverBuffer.add(packet as Packet<INetHandlerPlayClient>)
        }
    }
}
