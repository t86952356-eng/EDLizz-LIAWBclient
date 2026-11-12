/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object KeepSprint : Module("KeepSprint", Category.COMBAT) {
    val motionAfterAttackOnGround by float("MotionAfterAttackOnGround", 0.6f, 0.0f..1f)
    val motionAfterAttackInAir by float("MotionAfterAttackInAir", 0.6f, 0.0f..1f)

    val motionAfterAttack
        get() = if (mc.thePlayer.onGround) motionAfterAttackOnGround else motionAfterAttackInAir

    /** 与源客户端 KeepSprint.shouldKeepSprint 等价的近似：模块开启且正在疾跑。
     *  （源实现还带燃烧/水/药水等环境检查，lizz 的 KeepSprint 没有这些子选项） */
    val shouldKeepSprint: Boolean
        get() = handleEvents() && mc.thePlayer?.isSprinting == true
}