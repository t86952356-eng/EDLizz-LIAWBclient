/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoSlow「GrimNew」模式 —— 源客户端 SwordMode/ConsumeMode/BowMode 三个列表里的
 * GrimNew 合并为一：每个 motion tick 连发三把槽位切换包（%8+1 / %7+2 / 还原），
 * 让服务端的使用状态被刷新。Sword/Food/Potion/Bow 四个开关决定对哪类物品生效。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.minecraft.item.Item
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C09PacketHeldItemChange

object GrimNewNoSlowMode : NoSlowMode("GrimNew") {

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

    override fun onPreMotion(event: MotionEvent) {
        handleMotion()
    }

    override fun onPostMotion(event: MotionEvent) {
        handleMotion()
    }

    private fun handleMotion() {
        val player = mc.thePlayer ?: return
        if (!MovementUtils.hasMotion) return
        if (!isActiveForItem()) return

        val currentItem = player.inventory.currentItem
        player.sendQueue.addToSendQueue(C09PacketHeldItemChange(currentItem % 8 + 1))
        player.sendQueue.addToSendQueue(C09PacketHeldItemChange(currentItem % 7 + 2))
        player.sendQueue.addToSendQueue(C09PacketHeldItemChange(currentItem))
    }

    override fun onSlowDown(event: net.ccbluex.liquidbounce.event.SlowDownEvent) {
        if (isActiveForItem()) {
            event.forward = 1f
            event.strafe = 1f
        }
    }
}
