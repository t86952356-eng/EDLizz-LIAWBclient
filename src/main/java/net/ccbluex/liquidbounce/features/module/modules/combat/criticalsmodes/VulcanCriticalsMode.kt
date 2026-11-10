/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.criticalsmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers

/**
 * Criticals「Vulcan」模式：
 * 击退后 18 tick 内且累计下落距离 < 1.8 时，PRE 移动包上报 onGround=false。
 * 逻辑按原版 1.8.9 实现等价移植（FallDistanceComponent 的累计下落距离在此跟踪）。
 */
object VulcanCriticalsMode : CriticalsMode("Vulcan") {

    /** 累计下落距离（对应原 FallDistanceComponent.cY）。 */
    private var trackedFallDistance = 0f

    override fun onEnable() {
        trackedFallDistance = 0f
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        // FallDistanceComponent 等价跟踪（每 PRE 更新一次，onGround 时清零）
        val delta = player.lastTickPosY - player.posY
        if (delta > 0) {
            trackedFallDistance += delta.toFloat()
        }
        if (event.onGround) {
            trackedFallDistance = 0f
        }

        if (PortedTrackers.ticksSinceVelocity <= 18 && trackedFallDistance < 1.8f) {
            event.onGround = false
        }
    }
}
