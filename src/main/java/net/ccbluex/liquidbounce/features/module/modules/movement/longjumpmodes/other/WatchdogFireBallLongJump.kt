/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.LongJumpMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.handlePackets
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.init.Items
import net.minecraft.item.ItemFireball
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.S00PacketKeepAlive
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S32PacketConfirmTransaction

/**
 * LongJump「Watchdog Fire Ball」模式：
 * 自动切到火焰弹槽 → 以 89° 俯视放置火焰弹 → 扣住服务器的击退/事务/保活包，
 * 使用开始后第 13 tick 重放（模拟高延迟起跳），空中朝向偏移 +45°。
 * 逻辑按原版 1.8.9 实现等价移植。
 *
 * 原实现的 RotationComponent.setRotations(...) 这里通过 RotationUtils.currentRotation
 * 注入（Mixin 会携带该旋转发送 look 包并自动修正移动输入，等价 MovementFix）。
 */
object WatchdogFireBallLongJump : LongJumpMode("Watchdog Fire Ball") {

    /** Boost（默认开；与原实现一致，此选项仅存在、不参与逻辑）。 */
    @Suppress("unused")
    var boost = true

    private var previousSlot = -1
    private var velocityTicks = -1
    private var receivedVelocity = false
    var boosting = false
        private set
    private var sentPlacement = false
    private var stage = 0
    private var sentFireball = false
    var active = false
        private set
    var replaying = false
        private set
    private val heldPackets = ArrayList<Packet<*>>()

    override fun onEnable() {
        heldPackets.clear()
        replaying = false
        active = false
        if (findFireballSlot() == -1) {
            chat("Could not find Fireball")
            LongJump.toggle()
        } else {
            boosting = true
            stage = 0
        }
    }

    override fun onDisable() {
        heldPackets.clear()
        replaying = false
        active = false
        val player = mc.thePlayer
        if (player != null) {
            if (player.onGround) {
                PortedMove.stop()
            }
            if (previousSlot != -1) {
                player.inventory.currentItem = previousSlot
            }
        }
        velocityTicks = -1
        previousSlot = -1
        receivedVelocity = false
        boosting = false
        sentPlacement = false
        sentFireball = false
        stage = 0
        RotationUtils.currentRotation = null
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventType == EventState.SEND) {
            val packet = event.packet
            if (packet is C08PacketPlayerBlockPlacement &&
                packet.stack != null && packet.stack.item is ItemFireball
            ) {
                sentFireball = true
            }
            return
        }

        if (event.eventType != EventState.RECEIVE || replaying) return

        val packet = event.packet
        when (packet) {
            is S12PacketEntityVelocity -> {
                if (packet.entityID != player.entityId) return

                if (sentFireball) {
                    velocityTicks = 0
                    receivedVelocity = true
                    sentFireball = false
                    boosting = true
                }

                if (packet.entityID == player.entityId && !event.isCancelled) {
                    velocityTicks = 0
                    receivedVelocity = true
                    sentFireball = false
                    boosting = true
                    heldPackets.add(packet)
                    event.cancelEvent()
                    active = true
                }
            }

            is S32PacketConfirmTransaction -> if (active) {
                event.cancelEvent()
                heldPackets.add(packet)
            }

            // S00PacketKeepAlive：ping 欺骗期间直接扣住
            is S00PacketKeepAlive -> if (active) {
                event.cancelEvent()
                heldPackets.add(packet)
            }
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        if (stage == 0) {
            // 原实现：setRotations((yaw, 89°), 10, NORMAL) —— 低头对脚下放火焰弹
            RotationUtils.currentRotation = Rotation(player.rotationYaw, 89f)
            val slot = findFireballSlot()
            if (slot != -1 && slot != player.inventory.currentItem) {
                previousSlot = player.inventory.currentItem
                if (player.itemInUseCount > 1) {
                    player.inventory.currentItem = slot
                }
            }
        }

        if (stage == 1) {
            if (!sentPlacement) {
                sendPacket(C08PacketPlayerBlockPlacement(player.heldItem))
                sentPlacement = true
            }
        } else if (stage == 2 && previousSlot != -1) {
            player.inventory.currentItem = previousSlot
            previousSlot = -1
        }

        if (velocityTicks > 1) {
            LongJump.toggle()
        } else {
            if (receivedVelocity) {
                boosting = true
                velocityTicks++
            }

            if (stage < 3) {
                stage++
            }

            if (receivedVelocity) {
                if (velocityTicks > 1) {
                    boosting = false
                    receivedVelocity = false
                    velocityTicks = 0
                    return
                }

                boosting = true
                velocityTicks++
            }
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        // 原实现挂在 PreUpdateEvent：使用物品第 13 tick 时重放扣住的包
        if (active && PortedTrackers.itemUseTicks == 13) {
            replaying = true
            active = false
            handlePackets(*heldPackets.toTypedArray())
            heldPackets.clear()
            replaying = false
        }

        if (!player.onGround) {
            // 原实现：空中持续 setRotations((yaw+45°, pitch), 10, NORMAL)
            RotationUtils.currentRotation = Rotation(player.rotationYaw + 45f, player.rotationPitch)
        }
    }

    private fun findFireballSlot(): Int {
        val player = mc.thePlayer ?: return -1
        for (j in 0..8) {
            val stack = player.inventory.getStackInSlot(j)
            if (stack != null && stack.item === Items.fire_charge) {
                return j
            }
        }
        return -1
    }
}
