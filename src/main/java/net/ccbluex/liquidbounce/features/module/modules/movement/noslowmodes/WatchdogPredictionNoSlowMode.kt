/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.Types
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.ViaUseItem
import net.ccbluex.liquidbounce.utils.movement.PortedBlink
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

/**
 * NoSlow「Watchdog-Protection」模式：
 * 进食超过 Max Ping Spoof 后全量持包（blink）模拟高延迟；
 * 达到 When to finish eating 时自动松开使用键；进食时朝向偏移 45°。
 * 剑分支使用 NoSlow.usingItemFunc()（与 Custom 模式一致）→ 支持 KillAura 自动格挡。
 */
object WatchdogPredictionNoSlowMode : NoSlowMode("WatchdogPrediction") {

    /** Max Ping Spoof（默认 8）。 */
    var maxPingSpoof = 8

    /** When to finish eating（默认 30）。 */
    var whenToFinishEating = 30

    /** Non-Blink Speed Bypass（默认开）。 */
    var nonBlinkSpeedBypass = true

    private val blink = PortedBlink(holdAllClientPackets = true)

    private var wasUsingItem = false
    private var usingTicks = 0
    private var spoofSequence = 0

    /** 本 tick 是否需要 yaw+45° 视角欺骗。 */
    private var spoofYawActive = false

    override fun onEnable() {
        wasUsingItem = false
        usingTicks = 0
        spoofYawActive = false
        blink.clear()
    }

    override fun onDisable() {
        blink.dispatch()
        wasUsingItem = false
        usingTicks = 0
        spoofYawActive = false
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        tickSpoofState()
        val held = player.currentEquippedItem ?: return
        val item = held.item

        if (player.isUsingItem) {
            if ((item !is ItemSword || !NoSlow.sword) &&
                (
                    item is ItemFood && NoSlow.food && player.isEating ||
                        item is ItemBow && NoSlow.bow ||
                        player.heldItem?.item is ItemPotion &&
                        !ItemPotion.isSplash(player.heldItem!!.metadata) &&
                        NoSlow.potion && player.isEating
                    )
            ) {
                usingTicks++
                if (usingTicks > maxPingSpoof) {
                    blink.activate(30000)
                }
            }

            wasUsingItem = true
        } else if (wasUsingItem) {
            usingTicks = 0
            wasUsingItem = false
            blink.dispatch()
        }

        if (usingTicks > whenToFinishEating) {
            mc.gameSettings.keyBindUseItem.pressed = false
        }
    }

    /** 原实现挂在 SprintEvent；这里在玩家更新时直接强制疾跑。 */
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (player.isUsingItem && player.moveForward > 0f && nonBlinkSpeedBypass &&
            usingTicks <= maxPingSpoof
        ) {
            player.isSprinting = true
        }
    }

    /**
     * 原实现的 PreUpdateEvent 分支：进食时（非剑/弓）朝向偏移 +45°，
     * 通过改写出站 C05/C06 的 yaw 实现。
     */
    private fun updateSpoofState() {
        val player = mc.thePlayer ?: run { spoofYawActive = false; return }
        spoofYawActive = (KillAura.target == null || !KillAura.handleEvents()) &&
            (!player.onGround || player.itemInUseCount > 2) &&
            !mc.gameSettings.keyBindRight.isKeyDown &&
            !mc.gameSettings.keyBindLeft.isKeyDown &&
            PortedTrackers.ticksSinceTeleport > 5 &&
            player.isUsingItem &&
            (player.heldItem == null || player.heldItem!!.item !is ItemSword) &&
            (player.heldItem == null || player.heldItem!!.item !is ItemBow)
    }

    override fun onPacket(event: PacketEvent) {
        if (event.isCancelled) return
        val player = mc.thePlayer ?: return

        if (event.eventType == EventState.SEND) {
            // 视角欺骗：把出站 Look 包的 yaw 偏移 +45°
            if (spoofYawActive) {
                val packet = event.packet
                if (packet is C06PacketPlayerPosLook) {
                    event.cancelEvent()
                    sendPacket(
                        C06PacketPlayerPosLook(
                            packet.x, packet.y, packet.z,
                            player.rotationYaw + 45f, packet.pitch, packet.onGround
                        ), triggerEvent = false
                    )
                    return
                }
                if (packet is C05PacketPlayerLook) {
                    event.cancelEvent()
                    sendPacket(
                        C05PacketPlayerLook(player.rotationYaw + 45f, packet.pitch, packet.onGround),
                        triggerEvent = false
                    )
                    return
                }
            }
            blink.onSend(event.packet, event.isCancelled) { event.cancelEvent() }
        } else {
            blink.onReceive(event.packet, event.isCancelled) { event.cancelEvent() }
        }
    }

    /** 每个 tick 更新一次欺骗状态（供 NoSlow 的 UpdateEvent 分发）。 */
    fun tickSpoofState() = updateSpoofState()

    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        if (NoSlow.food && player.isUsingItem && item is ItemFood && usingTicks > maxPingSpoof) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.potion && player.isUsingItem && item is ItemPotion && usingTicks > maxPingSpoof) {
            event.strafe = 1f
            event.forward = 1f
        }

        // 剑：usingItemFunc() 支持 KillAura 自动格挡（blockStatus）与 UpdatedNCP 手动格挡
        if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
            mc.netHandler.addToSendQueue(
                C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN)
            )
            if (ViaUseItem.isCrossVersionTo9Plus() &&
                ViaLoadingBase.getInstance().targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)
            ) {
                sendUseItem19(hand = 0)
            } else {
                mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(player.currentEquippedItem))
            }
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow && usingTicks > maxPingSpoof) {
            event.strafe = 1f
            event.forward = 1f
        }
    }

    /** 1.19+ 分支的 Use Item（可指定主手编号）。 */
    private fun sendUseItem19(hand: Int) {
        try {
            val connections: Collection<UserConnection> = Via.getManager().connectionManager.connections
            if (connections.isEmpty()) return
            val user = connections.iterator().next()
            val wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM, user)
            wrapper.write(Types.VAR_INT as Type<Int>, hand)
            spoofSequence++
            wrapper.write(Types.VAR_INT as Type<Int>, spoofSequence)
            wrapper.sendToServer(Protocol1_19To1_18_2::class.java)
        } catch (_: Throwable) {
        }
    }
}
