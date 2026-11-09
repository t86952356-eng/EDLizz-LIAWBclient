/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.utils.client.PacketUtils.handlePackets
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.Packet
import net.minecraft.network.play.server.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Velocity「-Watchdog Prediction」模式：
 * 击退包全量持包（含保活），直到玩家朝向与击退方向一致（±15°）才释放；
 * 释放前保持 shouldJump 输入欺骗。
 */
object WatchdogPredictionVelocityMode : VelocityMode("-Watchdog Prediction") {

    /** Velocity to let through（默认 0.3，0..0.6）。 */
    var velocityToLetThrough = 0.3f

    /** Prevent Ghosting（默认开）。 */
    var preventGhosting = true

    var dj = false
        private set
    private var releasing = false
    private val heldPackets = ArrayList<Packet<*>>()
    private var velocityYaw = 0f
    private var velocityX = 0.0
    private var velocityZ = 0.0
    private var shouldJump = false

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        releaseAll()
        reset()
    }

    private fun reset() {
        dj = false
        shouldJump = false
    }

    private fun releaseAll() {
        if (heldPackets.isEmpty()) return
        releasing = true
        val packets = heldPackets.toList()
        heldPackets.clear()
        handlePackets(*packets.toTypedArray())
        releasing = false
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (!releasing && (!LongJump.handleEvents() || PortedTrackers.itemUseTicks >= 29) &&
            !Fly.handleEvents() && player.ticksExisted >= 50
        ) {
            when (packet) {
                is S12PacketEntityVelocity -> {
                    if (packet.entityID == player.entityId && !event.isCancelled) {
                        if (packet.entityID == player.entityId && packet.motionY > 0 ||
                            PortedTrackers.ticksSinceVelocity <= 14 ||
                            player.itemInUseCount <= 1
                        ) {
                            shouldJump = true
                        }

                        if (!player.onGround && PortedTrackers.ticksSinceTeleport > 4) {
                            dj = true
                            val vx = packet.motionX / 8000.0
                            val vz = packet.motionZ / 8000.0
                            velocityYaw = Math.toDegrees(atan2(vz, vx)).toFloat()
                            if (velocityYaw < -180f) velocityYaw += 360f
                            if (velocityYaw > 180f) velocityYaw -= 360f
                            heldPackets.add(packet)
                            event.cancelEvent()
                            velocityX = vx
                            velocityZ = vz
                        }
                    }
                }

                is S32PacketConfirmTransaction -> if (dj) {
                    event.cancelEvent()
                    heldPackets.add(packet)
                }

                is S00PacketKeepAlive -> if (dj) {
                    event.cancelEvent()
                    heldPackets.add(packet)
                }

                is S3BPacketScoreboardObjective, is S3CPacketUpdateScore, is S3DPacketDisplayScoreboard, is S3EPacketTeams -> return

                else -> {
                    val name = packet.javaClass.name
                    if (dj && name.startsWith("net.minecraft.network.play.server.")) {
                        event.cancelEvent()
                        heldPackets.add(packet)
                    }
                }
            }
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (PortedTrackers.anyBlinkActive) {
            dj = true
        }

        if (dj || PortedTrackers.ticksSinceTeleport == 1) {
            var yaw = player.rotationYaw % 360f
            if (yaw < -180f) yaw += 360f
            if (yaw > 180f) yaw -= 360f

            val diff = abs(yaw - velocityYaw)
            val threshold = 15f
            if (!player.onGround && (!(Math.random() > 0.98) || !preventGhosting)) {
                if (diff <= threshold || diff >= 360f - threshold) {
                    releaseAll()
                    dj = false
                } else if (PortedTrackers.itemUseTicks > 13) {
                    releaseAll()
                    dj = false
                }
            } else {
                if (PortedTrackers.itemUseTicks > 3 && mc.gameSettings.keyBindJump.isKeyDown && preventGhosting) {
                    // 原实现的「放行阈值」探测（结果未使用），省略
                    val ignored = hypot(PortedTrackers.expectedVelX, PortedTrackers.expectedVelZ) - velocityToLetThrough
                }

                releaseAll()
                dj = false
            }
        }
    }

    override fun onMoveInput(event: MovementInputEvent) {
        if (shouldJump) {
            event.originalInput.jump = true
            shouldJump = false
        }

        if (dj || releasing) {
            shouldJump = false
        }
    }
}
