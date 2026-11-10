/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.item.Item
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion

/**
 * NoSlow「GrimSemi」模式（仅 Food 分支可用，剑分支不生效）：
 * 使用物品的奇数 tick 完全取消减速，偶数 tick 应用可配置乘数
 * （默认 0.2，与原版一致）——「半速」交替绕过。
 */
object GrimSemiNoSlowMode : NoSlowMode("GrimSemi") {

    /** GrimSemi-Forward（默认 0.2，0..1）：偶数 tick 的前进乘数。 */
    var forwardMultiplier = 0.2f

    /** GrimSemi-Strafe（默认 0.2，0..1）：偶数 tick 的平移乘数。 */
    var strafeMultiplier = 0.2f

    private fun isFoodType(item: Item?) =
        item is ItemFood || item is ItemBucketMilk || item is ItemPotion

    private fun foodActive(player: net.minecraft.client.entity.EntityPlayerSP): Boolean {
        val item = player.heldItem?.item
        return player.isUsingItem && isFoodType(item) &&
                (NoSlow.food && item is ItemFood ||
                        NoSlow.potion && (item is ItemPotion || item is ItemBucketMilk))
    }

    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        if (!foodActive(player)) return

        // 源：itemUsingTicks 奇数 → 直接返回（事件默认 = 无减速）；偶数 → 应用自定义乘数
        if (PortedTrackers.itemUseTicks % 2 != 0) {
            event.strafe = 1f
            event.forward = 1f
        } else {
            event.forward = forwardMultiplier
            event.strafe = strafeMultiplier
        }
    }
}
