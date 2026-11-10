/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.matrix

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import java.util.LinkedList
import java.util.Queue

/**
 * Fly「Matrix」模式（原 MatrixTest，改名 Matrix）：
 * 吃掉 S08 传送并缓冲；吞掉全部 C03 出站包，每 tick 以 N 倍计时器
 * 用 C06/C04 重发当前位置，配合 noClip + 固定速度。
 */
object Matrix : FlyMode("Matrix") {

    /** Matrix-Timer（默认 1，1..10）。 */
    var timerSpeed = 1f

    /** Matrix-Speed（默认 0.16，0.1..0.7）。 */
    var matrixSpeed = 0.16

    /** Matrix-Packets（默认 4，1..10）。 */
    var packets = 4

    /** Matrix-PacketMode（C06/C04，默认 C06）。 */
    var packetMode = "C06"

    val serverBuffer: Queue<Packet<*>> = LinkedList()

    override fun onDisable() {
        for (packet in serverBuffer) {
            try {
                @Suppress("UNCHECKED_CAST")
                (packet as net.minecraft.network.Packet<net.minecraft.network.play.INetHandlerPlayClient?>)
                    .processPacket(mc.netHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        serverBuffer.clear()
        mc.timer?.timerSpeed = 1f
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType == EventState.RECEIVE) {
            val packet = event.packet
            if (packet is S08PacketPlayerPosLook) {
                event.cancelEvent()
                serverBuffer.add(packet)
            }
            return
        }

        if (event.eventType == EventState.SEND) {
            if (mc.thePlayer != null && mc.theWorld != null && event.packet is C03PacketPlayer) {
                event.cancelEvent()
            }
        }
    }

    private var lastWorld: Any? = null

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        // 源 onWorld：世界切换时清空 S08 缓冲（FlyMode 无 onWorld 钩子，这里追踪世界对象）
        if (lastWorld !== world) {
            lastWorld = world
            serverBuffer.clear()
        }

        mc.timer?.timerSpeed = timerSpeed
        player.noClip = true

        // stop(true)：水平 + 垂直全停
        player.motionX = 0.0
        player.motionY = 0.0
        player.motionZ = 0.0

        // setSpeed(speed, true)：沿移动方向设置固定速度
        if (isMovingInput()) {
            val dir = direction()
            player.motionX = -Math.sin(dir) * matrixSpeed
            player.motionZ = Math.cos(dir) * matrixSpeed
        }

        val rotation = RotationUtils.currentRotation
        val yaw = rotation?.yaw ?: player.rotationYaw
        val pitch = rotation?.pitch ?: player.rotationPitch

        when (packetMode) {
            "C06" -> repeat(packets) {
                sendPacket(
                    C06PacketPlayerPosLook(player.posX, player.posY, player.posZ, yaw, pitch, player.onGround),
                    triggerEvent = false
                )
            }

            "C04" -> repeat(packets) {
                sendPacket(
                    C04PacketPlayerPosition(player.posX, player.posY, player.posZ, player.onGround),
                    triggerEvent = false
                )
            }
        }
    }

    private fun isMovingInput(): Boolean {
        val p = mc.thePlayer ?: return false
        return p.movementInput.moveForward != 0f || p.movementInput.moveStrafe != 0f
    }

    private fun direction(): Double {
        val p = mc.thePlayer ?: return 0.0
        val input = p.movementInput
        val moveForward = input.moveForward.toDouble()
        val moveStrafe = input.moveStrafe.toDouble()
        var yaw = p.rotationYaw
        if (moveForward == 0.0 && moveStrafe == 0.0) {
            return Math.toRadians(yaw.toDouble())
        }
        val angle = Math.atan2(-moveStrafe, moveForward)
        return angle + Math.toRadians(yaw.toDouble())
    }
}
