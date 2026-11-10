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
import net.ccbluex.liquidbounce.utils.movement.PortedPacketQueue
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

/**
 * NoSlow「Intave」模式：使用物品期间暂存全部出站包，
 * 每 5 tick 用「松开使用 + 重新使用」的组合包刷新服务端状态。
 * 使用判定用 NoSlow.usingItemFunc()（与 Custom 模式一致）→ 支持 KillAura 自动格挡。
 */
object IntaveNoSlowMode : NoSlowMode("Intave") {

    /** 由 NoSlow 模块持有并驱动的出站包队列（两个模式共用，同一时间只会有一个激活）。 */
    val packetQueue = PortedPacketQueue()

    private var usingItem = false

    override fun onEnable() {
        usingItem = false
        packetQueue.clear()
    }

    override fun onDisable() {
        packetQueue.flush()
        packetQueue.deactivate()
        usingItem = false
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        val held = player.heldItem ?: return
        val item = held.item

        // usingItemFunc()：剑 + KillAura.blockStatus（自动格挡）时也视为使用中（与 Custom 一致）
        if (NoSlow.usingItemFunc()) {
            if (item is ItemSword && NoSlow.sword) {
                packetQueue.activate()
                if (player.ticksExisted % 5 == 0) {
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN)
                    )
                    packetQueue.dispatch()
                    mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(player.currentEquippedItem))
                }
            } else if ((item is ItemFood && NoSlow.food) || (item is ItemBow && NoSlow.bow)) {
                packetQueue.activate()
            }
            usingItem = true
        } else if (usingItem) {
            usingItem = false
            packetQueue.deactivate()
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

        if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
            event.strafe = 1f
            event.forward = 1f
        }
    }
}
