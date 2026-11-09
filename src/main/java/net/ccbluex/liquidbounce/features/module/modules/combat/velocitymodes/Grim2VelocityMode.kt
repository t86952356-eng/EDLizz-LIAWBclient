/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.abs
import kotlin.random.Random

/**
 * Velocity「Grim 2」模式：全旋转修正（给整角度加噪声）+
 * 假 S08（客户端自行落位）+ 击退包取消。
 */
object Grim2VelocityMode : VelocityMode("Grim 2") {

    /** Full Rotation Fix（默认开）。 */
    var fullRotationFix = true

    /** Fake S08（默认开）。 */
    var fakeS08 = true

    /** Cancel Velocity（默认开）。 */
    var cancelVelocity = true

    /** Rotation Noise（默认 0.001）。 */
    var rotationNoise = 0.001f

    /** Debug Log（默认关）。 */
    var debugLog = false

    private val random = java.util.Random()

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return
        val packet = event.packet

        if (event.eventType == EventState.SEND) {
            if (fullRotationFix && packet is C03PacketPlayer && packet.rotating) {
                val yaw = packet.yaw
                val pitch = packet.pitch
                if (isBadAngle(yaw) || isBadAngle(pitch)) {
                    val noise = rotationNoise
                    val newYaw = yaw + (if (random.nextBoolean()) 1 else -1) * noise
                    val newPitch = pitch + (if (random.nextBoolean()) 1 else -1) * noise
                    val replacement = if (packet.isMoving) {
                        C06PacketPlayerPosLook(packet.x, packet.y, packet.z, newYaw, newPitch, packet.onGround)
                    } else {
                        C05PacketPlayerLook(newYaw, newPitch, packet.onGround)
                    }
                    event.cancelEvent()
                    sendPacket(replacement, triggerEvent = false)
                    debug("Full rotation bypass")
                }
            }
        } else {
            if (fakeS08 && packet is S08PacketPlayerPosLook) {
                player.setPosition(packet.x, packet.y, packet.z)
                player.motionX = 0.0
                player.motionY = 0.0
                player.motionZ = 0.0
                event.cancelEvent()
                debug("Fake S08")
            }

            if (cancelVelocity && packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
                event.cancelEvent()
                debug("Velocity cancelled")
            }
        }
    }

    /** 与原实现一致：角度落在 90° 网格 ±0.01 内视为「整角度」。 */
    private fun isBadAngle(angle: Float): Boolean {
        val mod = abs(angle % 90f)
        return mod < 0.01f || mod > 89.99f
    }

    private fun debug(message: String) {
        if (debugLog) {
            chat("§8[§cGrimVelocity2§8] §7$message")
        }
    }
}
