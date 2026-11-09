/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * NoSlow「WatchdogNCP」模式：
 * 台阶感知 + 空中进食 + 切槽格挡绕过，逻辑按原版 1.8.9 实现等价移植。
 */
object WatchdogNoSlowMode : NoSlowMode("WatchdogNCP") {

    /** Slow down on Slabs（默认开）。 */
    var slowDownOnSlabs = true

    private var airTicks = 0
    private var onSlab = false

    override fun onEnable() {
        airTicks = 0
        onSlab = false
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        if (PortedMove.blockAt(0.0, player.motionY, 0.0) != Blocks.air &&
            !player.isUsingItem && slowDownOnSlabs
        ) {
            onSlab = false
        }

        val posY = event.y
        if (abs(posY - posY.roundToLong().toDouble()) > 0.03 && player.onGround) {
            onSlab = true
        }

        if (player.isUsingItem && player.heldItem?.item !is ItemSword) {
            if (player.onGround) {
                airTicks = 0
            } else {
                airTicks++
            }

            if (airTicks >= 2) {
                // 与原实现一致：空中 2 tick 后重置假状态
            } else if (player.onGround && !onSlab) {
                event.y = posY + 0.001
            }
        }

        if (onSlab && !player.onGround && player.isUsingItem && player.heldItem?.item !is ItemSword) {
            player.motionX *= 0.1
            player.motionZ *= 0.1
        }
    }

    /**
     * 原实现挂在 RightClickEvent 上取消点击；
     * 这里等价地拦截出站的 C08 使用物品包（手持食物/药水/弓时的点击）。
     */
    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.SEND || event.isCancelled) return
        val player = mc.thePlayer ?: return
        val packet = event.packet
        if (packet !is C08PacketPlayerBlockPlacement) return

        val held = player.heldItem ?: return
        val item = held.item
        val consumableClick = player.isUsingItem ||
            (item is ItemPotion && !ItemPotion.isSplash(held.metadata)) ||
            item is ItemFood || item is ItemBow

        if (!consumableClick) return

        val useTicks = net.ccbluex.liquidbounce.utils.movement.PortedTrackers.itemUseTicks
        if (useTicks in 1..1 && !onSlab) {
            chat("You must start eating while in the air even with potions")
            event.cancelEvent()
        } else if (player.onGround) {
            player.jump()
            event.cancelEvent()
        }
    }

    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        if (!onSlab || player.onGround) {
            if (NoSlow.food && player.isUsingItem && item is ItemFood) {
                event.strafe = 1f
                event.forward = 1f
            }

            if (NoSlow.potion && player.isUsingItem && item is ItemPotion) {
                event.strafe = 1f
                event.forward = 1f
            }

            if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
                event.strafe = 1f
                event.forward = 1f
            }
        }

        if (NoSlow.sword && player.isUsingItem && item is ItemSword) {
            val slot = player.inventory.currentItem
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot % 7 + (Math.random() * 2.0).toInt() + 1))
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot))
            event.strafe = 1f
            event.forward = 1f
        }
    }
}
