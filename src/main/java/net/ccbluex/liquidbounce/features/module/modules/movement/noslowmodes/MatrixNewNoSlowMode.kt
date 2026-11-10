/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.extensions.airTicks
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword

/**
 * NoSlow「MatrixNew」模式（源 Matrix 的 BlockingPacketMode + FoodMode 合并）：
 * - Sword 开关 → 源 BlockingPacketMode 的 Matrix 分支（Y 偏移 + 空中摩擦 0.85/0.95）
 * - Food/Potion 开关 → 源 FoodMode 的 Matrix 分支（Y 偏移 + 空中摩擦 0.9/0.95）
 * 剑分支使用 NoSlow.usingItemFunc()（与 Custom 模式一致）→ 支持 KillAura 自动格挡。
 */
object MatrixNewNoSlowMode : NoSlowMode("MatrixNew") {

    /** 与源 CombatHandler.inCombat 等价：1600ms 内攻击过实体。 */
    private var lastAttackTime = 0L

    private val inCombat: Boolean
        get() = System.currentTimeMillis() - lastAttackTime < 1600L

    override fun onEnable() {
        lastAttackTime = 0L
    }

    override fun onAttack(target: net.minecraft.entity.Entity?) {
        lastAttackTime = System.currentTimeMillis()
    }

    override fun onPreMotion(event: MotionEvent) {
        handleMotion(event)
    }

    override fun onPostMotion(event: MotionEvent) {
        handleMotion(event)
    }

    private fun handleMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        // ---- Sword：BlockingPacketMode 的 Matrix 分支 ----
        // 剑：usingItemFunc() 支持 KillAura 自动格挡（blockStatus）与 UpdatedNCP 手动格挡
        if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
            if (player.hurtTime > 0 || !player.isMoving) return

            if (player.isCollidedVertically && !player.movementInput.jump && !inCombat) {
                event.y += if (player.ticksExisted % 2 == 0) 6.0E-4 else 2.0E-4
                player.onGround = false
                event.onGround = false
            } else if (event.eventState == EventState.POST) {
                // 源 switch 落穿语义：1/7/8 → ×0.85；>=9 → 再 ×0.95
                val air = player.airTicks
                if (air == 1 || air == 7 || air == 8) {
                    player.motionX *= 0.85
                    player.motionZ *= 0.85
                }
                if (air >= 9) {
                    player.motionX *= 0.95
                    player.motionZ *= 0.95
                }
            }
            return
        }

        // ---- Food / Potion：FoodMode 的 Matrix 分支 ----
        val isFoodType = item is ItemFood || item is ItemBucketMilk || item is ItemPotion
        if ((NoSlow.food && item is ItemFood || NoSlow.potion && (item is ItemPotion || item is ItemBucketMilk)) &&
            player.isUsingItem && isFoodType
        ) {
            if (player.hurtTime > 0 || !player.isMoving) return

            // 源：!movementInput.jump && !isJumping（1.8.9 的 isJumping 不可访问，用 movementInput.jump 近似）
            if (player.isCollidedVertically && !player.movementInput.jump) {
                event.y += if (player.ticksExisted % 2 == 0) 6.0E-4 else 2.0E-4
                player.onGround = false
                event.onGround = false
            } else if (event.eventState == EventState.POST) {
                // 源落穿语义：1/7/8 → ×0.9；>=7 → 再 ×0.95
                val air = player.airTicks
                if (air == 1 || air == 7 || air == 8) {
                    player.motionX *= 0.9
                    player.motionZ *= 0.9
                }
                if (air >= 7) {
                    player.motionX *= 0.95
                    player.motionZ *= 0.95
                }
            }
        }
    }

    override fun onSlowDown(event: net.ccbluex.liquidbounce.event.SlowDownEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        // 剑：usingItemFunc() 支持 KillAura 自动格挡
        if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.food && player.isUsingItem && item is ItemFood) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.potion && player.isUsingItem && (item is ItemPotion || item is ItemBucketMilk)) {
            event.strafe = 1f
            event.forward = 1f
        }
    }
}
