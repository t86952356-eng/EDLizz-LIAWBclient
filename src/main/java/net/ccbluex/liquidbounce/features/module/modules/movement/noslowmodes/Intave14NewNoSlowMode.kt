/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoSlow「Intave14New」模式 —— 源客户端 SwordMode/ConsumeMode/BowMode 三个列表里的
 * Intave14 合并为一：800ms 循环 —— 冷却结束发 RELEASE(-1,-1,-1)；冷却中 PRE 发
 * START_SPRINTING、POST 发屏障 ClickWindow + 空 C09 + C08 + STOP_SPRINTING + C0F。
 * Sword/Food/Potion/Bow 四个开关决定对哪类物品生效。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.item.Item
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0EPacketClickWindow
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

object Intave14NewNoSlowMode : NoSlowMode("Intave14New") {

    private val in14MS = MSTimer()

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
        in14MS.reset()
    }

    override fun onDisable() {
        in14MS.reset()
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

        if (in14MS.hasTimePassed(800L)) {
            player.sendQueue.addToSendQueue(
                C07PacketPlayerDigging(
                    RELEASE_USE_ITEM,
                    BlockPos(-1, -1, -1),
                    EnumFacing.DOWN
                )
            )
            in14MS.reset()
        } else if (event.eventState == EventState.PRE) {
            player.sendQueue.addToSendQueue(
                C0BPacketEntityAction(
                    player,
                    C0BPacketEntityAction.Action.START_SPRINTING
                )
            )
        } else {
            player.sendQueue.addToSendQueue(
                C0EPacketClickWindow(
                    0, 36, 0, 1,
                    SomeUtil.barrierStack(),
                    (0).toShort()
                )
            )
            player.sendQueue.addToSendQueue(C09PacketHeldItemChange())
            player.sendQueue.addToSendQueue(
                C08PacketPlayerBlockPlacement(
                    player.inventory.getCurrentItem()
                )
            )
            player.sendQueue.addToSendQueue(
                C0BPacketEntityAction(
                    player,
                    C0BPacketEntityAction.Action.STOP_SPRINTING
                )
            )
            player.sendQueue.addToSendQueue(C0FPacketConfirmTransaction())
        }
    }

    override fun onSlowDown(event: net.ccbluex.liquidbounce.event.SlowDownEvent) {
        if (isActiveForItem()) {
            event.forward = 1f
            event.strafe = 1f
        }
    }
}
