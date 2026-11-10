/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.criticalsmodes

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition

/**
 * Criticals「Verus」模式：
 * 使用物品剩余 >2 tick 且刚被击中（hurtTime != 0）时，
 * 发送 0.0625/0.0 两个假位置包制造暴击判定。
 * 逻辑按原版 1.8.9 实现等价移植。
 */
object VerusCriticalsMode : CriticalsMode("Verus") {

    /** Delay（默认 500，0..1000）。 */
    var delay = 500L

    private val offsets = doubleArrayOf(0.0625, 0.0)
    private val delayStopWatch = MSTimer()

    override fun onAttack(event: AttackEvent) {
        val player = mc.thePlayer ?: return
        if (delayStopWatch.hasTimePassed(delay) && player.itemInUseCount > 2 && player.hurtTime != 0) {
            for (offset in offsets) {
                sendPacket(C04PacketPlayerPosition(player.posX, player.posY + offset, player.posZ, false))
            }

            (event.targetEntity as? EntityLivingBase)?.let { player.onCriticalHit(it) }
            delayStopWatch.reset()
        }
    }
}
