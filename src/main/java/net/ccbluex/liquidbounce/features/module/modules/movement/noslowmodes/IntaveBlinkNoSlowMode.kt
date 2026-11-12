/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoSlow「IntaveBlink」模式 —— 源客户端 SwordMode/ConsumeMode/BowMode 三个列表里的
 * IntaveBlink 合并为一：使用物品期间把 C07/C02/C12/C19/C03 扣在 BlinkUtils，
 * 每 5 tick 放行一次并补 RELEASE_USE_ITEM + C08 重声明使用；收到自己的击退/爆炸立刻取消。
 * Sword/Food/Potion/Bow 四个开关决定对哪类物品生效。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.ccbluex.liquidbounce.utils.timing.TickTimer
import net.minecraft.item.Item
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.client.C12PacketUpdateSign
import net.minecraft.network.play.client.C19PacketResourcePackStatus
import net.minecraft.network.play.client.C01PacketChatMessage
import net.minecraft.network.status.server.S01PacketPong
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.network.status.client.C00PacketServerQuery
import net.minecraft.network.status.client.C01PacketPing
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

object IntaveBlinkNoSlowMode : NoSlowMode("IntaveBlink") {

    private var shouldBlink = false
    private val blinkTimer = TickTimer()

    /** 四类物品开关 + usingItemFunc（含 KillAura 格挡）合并后的「当前物品是否生效」 */
    private fun isActiveForItem(): Boolean {
        val player = mc.thePlayer ?: return false
        val item: Item = player.heldItem?.item ?: return false
        if (!NoSlow.usingItemFunc()) return false
        return when (item) {
            is ItemSword -> NoSlow.sword
            is ItemFood -> NoSlow.food
            is ItemPotion, is ItemBucketMilk -> NoSlow.potion
            is ItemBow -> NoSlow.bow
            else -> false
        }
    }

    override fun onEnable() {
        shouldBlink = false
        blinkTimer.reset()
    }

    override fun onDisable() {
        if (shouldBlink || BlinkUtils.isBlinking) {
            BlinkUtils.unblink()
        }
        shouldBlink = false
        blinkTimer.reset()
    }

    override fun onPreMotion(event: MotionEvent) {
        handleMotion(event)
    }

    override fun onPostMotion(event: MotionEvent) {
        handleMotion(event)
    }

    private fun handleMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        if (!MovementUtils.hasMotion) return
        val heldItem = player.heldItem ?: return

        // 不在使用物品时，立即取消 blink
        if (!isActiveForItem()) {
            if (shouldBlink) {
                BlinkUtils.unblink()
                shouldBlink = false
                blinkTimer.reset()
            }
            return
        }

        when (event.eventState) {
            EventState.PRE -> {
                if (player.ticksExisted % 5 == 0 && shouldBlink) { // 每5 tick执行一次
                    // 释放blink并立即重新blink
                    BlinkUtils.unblink()
                    sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    sendPacket(C08PacketPlayerBlockPlacement(heldItem))
                    blinkTimer.reset()
                    // shouldBlink 保持为 true 以继续blink
                } else if (!shouldBlink) {
                    // 开始blink
                    sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    sendPacket(C08PacketPlayerBlockPlacement(heldItem))
                    shouldBlink = true
                    blinkTimer.reset()
                }
            }
            else -> {}
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (event.isCancelled) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        // 不在使用物品时，取消 blink
        if (!isActiveForItem()) {
            if (shouldBlink) {
                BlinkUtils.unblink()
                shouldBlink = false
                blinkTimer.reset()
            }
            return
        }

        when (packet) {
            is C00Handshake, is C00PacketServerQuery, is C01PacketPing,
            is C01PacketChatMessage, is S01PacketPong -> return

            is C07PacketPlayerDigging, is C02PacketUseEntity,
            is C12PacketUpdateSign, is C19PacketResourcePackStatus -> {
                blinkTimer.update()
                // 只在应该blink时处理这些包
                if (shouldBlink) {
                    BlinkUtils.blink(packet, event)
                }
                return
            }

            is S12PacketEntityVelocity -> {
                if (player.entityId == packet.entityID) {
                    // 收到击退时取消blink
                    BlinkUtils.unblink()
                    shouldBlink = false
                    return
                }
            }

            is S27PacketExplosion -> {
                if (packet.field_149153_g != 0f || packet.field_149152_f != 0f || packet.field_149159_h != 0f) {
                    // 收到爆炸时取消blink
                    BlinkUtils.unblink()
                    shouldBlink = false
                    return
                }
            }

            is C03PacketPlayer -> {
                // 只在应该blink时缓存移动包
                if (shouldBlink) {
                    BlinkUtils.blink(packet, event)
                }
            }
        }
    }

    override fun onSlowDown(event: net.ccbluex.liquidbounce.event.SlowDownEvent) {
        if (isActiveForItem()) {
            event.forward = 1f
            event.strafe = 1f
        }
    }
}
