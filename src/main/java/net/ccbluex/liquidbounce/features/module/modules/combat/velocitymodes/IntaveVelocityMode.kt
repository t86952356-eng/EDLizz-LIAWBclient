/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.features.module.modules.combat.Velocity

/**
 * Velocity「Intave」模式：攻击后主动复现「疾跑重置」减速，
 * 防止攻击瞬间的冲刺动量被判定。
 */
object IntaveVelocityMode : VelocityMode("Intave") {

    private var attacked = false
    private var slowedDown = false

    override fun onEnable() {
        attacked = false
        slowedDown = false
    }

    override fun onAttack(target: net.minecraft.entity.Entity?) {
        attacked = true
        // 原实现由「命中减速」事件置位；等价条件：攻击瞬间正在疾跑
        if (mc.thePlayer?.isSprinting == true) slowedDown = true
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (!Velocity.onSwing || player.isSwingInProgress) {
            if (attacked && !slowedDown && player.isSprinting) {
                player.motionX *= 0.6
                player.motionZ *= 0.6
                player.isSprinting = false
            }

            attacked = false
            slowedDown = false
        }
    }
}
