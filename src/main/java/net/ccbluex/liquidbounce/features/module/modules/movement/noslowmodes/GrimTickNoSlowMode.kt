/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoSlow「GrimTick」模式 —— 源客户端 SwordMode/ConsumeMode/BowMode 三个列表里的
 * GrimTick 合并为一：以 32 tick 为周期，tick%4 < 2 的 PRE 发 RELEASE_USE_ITEM，
 * 伪造「松开-又按住」的使用节奏。Sword/Food/Potion/Bow 四个开关决定对哪类物品生效。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.minecraft.item.Item
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

object GrimTickNoSlowMode : NoSlowMode("GrimTick") {

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
        handleMotion(event)
    }

    override fun onPostMotion(event: MotionEvent) {
        handleMotion(event)
    }

    private fun handleMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        if (!MovementUtils.hasMotion) return
        if (!isActiveForItem()) return

        val currentTick = player.ticksExisted % 32
        if (event.eventState == EventState.PRE) {
            if (currentTick % 4 < 2) {
                sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
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
