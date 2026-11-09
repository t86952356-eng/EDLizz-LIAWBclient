/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C09PacketHeldItemChange

/**
 * NoSlow「Grim」模式：PRE 阶段对四类物品发送「切槽再切回」的假动作。
 */
object GrimNoSlowMode : NoSlowMode("Grim") {

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        if (NoSlow.food && player.isUsingItem && item is ItemFood) {
            sendSlotSwap()
        }

        if (NoSlow.potion && player.isUsingItem && item is ItemPotion) {
            sendSlotSwap()
        }

        if (NoSlow.sword && player.isUsingItem && item is ItemSword) {
            sendSlotSwap()
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
            sendSlotSwap()
        }
    }

    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        if (NoSlow.food && player.isUsingItem && item is ItemFood) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.potion && player.isUsingItem && item is ItemPotion) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.sword && player.isUsingItem && item is ItemSword) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
            event.strafe = 1f
            event.forward = 1f
        }
    }

    private fun sendSlotSwap() {
        val player = mc.thePlayer ?: return
        val slot = player.inventory.currentItem
        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot % 8 + 1))
        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot))
    }
}
