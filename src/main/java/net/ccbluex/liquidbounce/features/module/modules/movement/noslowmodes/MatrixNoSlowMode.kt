/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword

/**
 * NoSlow「Matrix」模式：进食期间按剩余使用 tick 分段控制水平速度。
 * 剑分支使用 NoSlow.usingItemFunc()（与 Custom 模式一致）→ 支持 KillAura 自动格挡。
 */
object MatrixNoSlowMode : NoSlowMode("Matrix") {

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

        // 剑：usingItemFunc() 支持 KillAura 自动格挡（blockStatus）与 UpdatedNCP 手动格挡
        if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
            event.strafe = 1f
            event.forward = 1f
        }
    }

    override fun onStrafe() {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item
        val speedEnabled = Speed.handleEvents()

        fun matrixMotion() {
            if (player.itemInUseCount > 1) {
                PortedMove.strafe(0.0265)
            } else if (!speedEnabled) {
                player.motionX *= 0.992
                player.motionZ *= 0.992
            } else {
                player.motionX *= 0.99
                player.motionZ *= 0.99
            }
        }

        if (NoSlow.food && player.isUsingItem && item is ItemFood) {
            matrixMotion()
        }

        if (NoSlow.potion && player.isUsingItem && item is ItemPotion) {
            matrixMotion()
        }

        // 剑：usingItemFunc() 支持 KillAura 自动格挡（blockStatus）与 UpdatedNCP 手动格挡
        if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
            matrixMotion()
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
            matrixMotion()
        }
    }
}
