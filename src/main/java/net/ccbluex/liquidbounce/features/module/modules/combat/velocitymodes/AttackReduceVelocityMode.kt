/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Velocity「AttackReduce」模式 —— 源客户端 NewVelocity 的 AttackReduce：
 * 攻击命中时若 hurtTime 在范围内，把当前 XZ 动量乘 AttackXZFactor。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceXZ
import net.minecraft.entity.Entity

object AttackReduceVelocityMode : VelocityMode("AttackReduce") {

    /** AttackXZFactor（默认 0.6，0..1）。 */
    var attackReduceFactor = 0.6f

    /** AttackHurtTime（默认 9..9，1..10）。 */
    var attackHurtTime = 9..9

    override fun onAttack(target: Entity?) {
        val player = mc.thePlayer ?: return

        if (player.hurtTime in attackHurtTime) {
            reduceXZ(attackReduceFactor.toDouble())
        }
    }
}
