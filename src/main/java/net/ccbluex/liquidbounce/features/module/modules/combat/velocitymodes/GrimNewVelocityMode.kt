/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.client.PacketUtils.handlePackets
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.item.ItemStack
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.*
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

/**
 * Velocity「Grim-New」模式：
 * 击退后冻结移动 + 持包（事务/实体/传送/元数据），
 * 冻结期间向脚下补发交互包解除冻结，可选低头视角欺骗。
 */
object GrimNewVelocityMode : VelocityMode("Grim-New") {

    /** Smart Combat (for High Ping)（默认关）。 */
    var smartCombatForHighPing = false

    /** Rotate（默认开）。 */
    var rotate = true

    var movementFrozen = false
        private set
    var dj = false
        private set
    var flushing = false
        private set
    var rotating = false
        private set

    private val placedBlocks = HashSet<BlockPos>()
    private val heldPackets = ArrayList<Packet<*>>()
    private var motionSaved = false
    private var savedMotionX = 0.0
    private var savedMotionY = 0.0
    private var savedMotionZ = 0.0

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        releaseAll()
        reset()
    }

    private fun reset() {
        dj = false
        movementFrozen = false
        placedBlocks.clear()
        motionSaved = false
        rotating = false
    }

    private fun releaseAll() {
        if (heldPackets.isEmpty()) return
        flushing = true
        val packets = heldPackets.toList()
        heldPackets.clear()
        handlePackets(*packets.toTypedArray())
        flushing = false
    }

    private val ticksSinceTeleport get() = PortedTrackers.ticksSinceTeleport

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (!flushing && ticksSinceTeleport >= 7 && !player.isInWeb) {
            if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
                if (player.onGround) {
                    movementFrozen = true
                } else {
                    heldPackets.add(packet)
                    dj = true
                }
                event.cancelEvent()
            }

            if (packet is S32PacketConfirmTransaction && dj) {
                heldPackets.add(packet)
                event.cancelEvent()
            }

            if (packet is S18PacketEntityTeleport && dj) {
                heldPackets.add(packet)
                event.cancelEvent()
            }

            if (packet is S14PacketEntity && dj) {
                heldPackets.add(packet)
                event.cancelEvent()
            }

            if (packet is S1CPacketEntityMetadata && dj) {
                heldPackets.add(packet)
                event.cancelEvent()
            }

            if (packet is S08PacketPlayerPosLook && dj) {
                heldPackets.add(packet)
                event.cancelEvent()
            }

            if (packet is S23PacketBlockChange) {
                val pos = packet.blockPosition
                if (placedBlocks.remove(pos) && placedBlocks.isEmpty()) {
                    movementFrozen = false
                }
            }
        }
    }

    /** 由 Velocity 的 MoveEvent 分发调用（冻结时取消移动事件）。 */
    fun handleMove(cancel: () -> Unit) {
        val player = mc.thePlayer ?: return
        if (ticksSinceTeleport >= 7 && !player.isInWeb || !player.onGround) {
            if (movementFrozen) {
                cancel()
            }

            if (PortedTrackers.itemUseTicks > 25 && dj) {
                dj = false
                flushing = true
                releaseAll()
                flushing = false
            }
        } else if (heldPackets.isNotEmpty() || movementFrozen || dj) {
            flushing = true
            movementFrozen = false
            placedBlocks.clear()
            dj = false
            motionSaved = false
            releaseAll()
            flushing = false
        }
    }

    override fun onStrafe() {
        val player = mc.thePlayer ?: return
        if (ticksSinceTeleport >= 7 && !player.isInWeb) {
            if (player.itemInUseCount > 3 && player.onGround && dj) {
                player.jump()
            }

            if (movementFrozen) {
                if (!motionSaved) {
                    savedMotionX = player.motionX
                    savedMotionY = player.motionY
                    savedMotionZ = player.motionZ
                    motionSaved = true
                }
            } else if (motionSaved) {
                player.motionX = savedMotionX
                player.motionY = savedMotionY
                player.motionZ = savedMotionZ
                motionSaved = false
            }
        }
    }

    override fun onJump(event: JumpEvent) {
        val player = mc.thePlayer ?: return
        if (ticksSinceTeleport >= 7 && !player.isInWeb && player.onGround && dj) {
            movementFrozen = true
            dj = false
            flushing = true
            val savedX = player.motionX
            val savedZ = player.motionZ
            val savedY = player.motionY
            releaseAll()
            player.motionX = savedX
            player.motionZ = savedZ
            player.motionY = savedY
            flushing = false
        }
    }

    override fun onTick() {
        val player = mc.thePlayer ?: return
        if (movementFrozen) {
            if (rotate) {
                rotating = true
                if (!smartCombatForHighPing) {
                    // 原实现走全局旋转组件；这里以出站包欺骗等价替代（见 onPacketSendSpoof）
                }
            } else {
                rotating = false
            }

            val blockPos = BlockPos(player)
            val below = blockPos.down()
            val x = (player.posX - below.x).toFloat()
            val y = 1.0f
            val z = (player.posZ - below.z).toFloat()
            val held: ItemStack? = player.heldItem
            mc.netHandler.addToSendQueue(
                C08PacketPlayerBlockPlacement(below, EnumFacing.UP.index, held, x, y, z)
            )
            placedBlocks.add(below)
        } else {
            rotating = false
        }
    }

    /** 冻结期间把出站 Look 包的 pitch 改写为 90 - random*0.1（低头）。 */
    fun onPacketSendSpoof(event: PacketEvent) {
        if (event.eventType != EventState.SEND || event.isCancelled) return
        if (!rotating) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is C06PacketPlayerPosLook) {
            event.cancelEvent()
            sendPacket(
                C06PacketPlayerPosLook(packet.x, packet.y, packet.z, packet.yaw, 90f - (Math.random() * 0.1).toFloat(), packet.onGround),
                triggerEvent = false
            )
        } else if (packet is C05PacketPlayerLook) {
            event.cancelEvent()
            sendPacket(
                C05PacketPlayerLook(packet.yaw, 90f - (Math.random() * 0.1).toFloat(), packet.onGround),
                triggerEvent = false
            )
        }
    }
}
