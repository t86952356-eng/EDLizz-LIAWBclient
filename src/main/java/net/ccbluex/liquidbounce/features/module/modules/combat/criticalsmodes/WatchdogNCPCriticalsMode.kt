/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.criticalsmodes

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.entity.EntityLivingBase

/**
 * Criticals「WatchdogNCP」模式：
 * 攻击时标记 pendingCrit，随后在地面维持若干 tick 的「微落差 + onGround=false +
 * 0.1 步高 + SafeWalk」序列，落地后逐步恢复；攻击后短时间内取消跳跃。
 * 逻辑按原版 1.8.9 实现等价移植（原名 Watchdog）。
 *
 * 原实现的 `thePlayer.crd`（SafeWalk 字段）这里用 SafeWalk 激活标记 + MoveEvent
 * 注入实现；`thePlayer.csk`（原混淆字段，只在恢复步高时用到）按「距上次暴击攻击的
 * tick 数」等价跟踪。
 */
object WatchdogNCPCriticalsMode : CriticalsMode("WatchdogNCP") {

    /** Delay（默认 500，0..1000）。 */
    var delay = 500L

    /** 原实现中未使用的偏移表（保持原样移植）。 */
    @Suppress("unused")
    private val offsets = doubleArrayOf(1.0E-6, PortedMove.predictedMotion(0.03125, 1))

    private val stopwatch = MSTimer()

    var pendingCrit = false
        private set

    private var critTicks = 0
    private var ticksSinceCrit = 0

    /** 距上次暴击攻击的 tick 数（对应原混淆字段 csk 的等价跟踪）。 */
    private var ticksSinceCritAttack = 0

    /** SafeWalk 激活标记（对应原 thePlayer.crd）。 */
    private var safeWalkActive = false

    override fun onEnable() {
        pendingCrit = false
        critTicks = 0
        ticksSinceCrit = 0
        ticksSinceCritAttack = 0
        safeWalkActive = false
    }

    override fun onDisable() {
        safeWalkActive = false
        mc.thePlayer?.stepHeight = 0.6f
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        ticksSinceCritAttack++

        if (player.onGround && pendingCrit) {
            safeWalkActive = true
            player.stepHeight = 0.1f
            critTicks++
            ticksSinceCrit = 0
            if (critTicks > 2) {
                ticksSinceCrit++
            }

            when (critTicks) {
                1 -> event.y = event.y + 0.001
                2 -> pendingCrit = false
            }

            event.onGround = false
            if (critTicks > 4) {
                player.stepHeight = 0.6f
            }

            if (player.onGround && critTicks < 6) {
                safeWalkActive = true
            }

            if (critTicks > 6) {
                safeWalkActive = false
            }
        } else {
            ticksSinceCrit++
            if (ticksSinceCritAttack > 4 || ticksSinceCrit > 4) {
                player.stepHeight = 0.6f
            }

            if (ticksSinceCrit < 5 && player.onGround && critTicks < 6) {
                safeWalkActive = true
            } else {
                safeWalkActive = false
            }

            pendingCrit = false
            critTicks = 0
        }
    }

    override fun onAttack(event: AttackEvent) {
        val player = mc.thePlayer ?: return
        if (player.onGround && !player.isOnLadder && stopwatch.hasTimePassed(delay)) {
            (event.targetEntity as? EntityLivingBase)?.let { player.onCriticalHit(it) }
            stopwatch.reset()
            pendingCrit = true
            ticksSinceCritAttack = 0
        }
    }

    override fun onJump(event: JumpEvent) {
        val player = mc.thePlayer ?: return
        if (event.eventState != EventState.PRE) return
        if (critTicks <= 4 && ticksSinceCrit <= 4 && player.itemInUseCount > 2) {
            event.cancelEvent()
        }
    }

    override fun onMove(event: MoveEvent) {
        // 对应原实现的 thePlayer.crd（SafeWalk 字段）
        if (safeWalkActive) {
            event.isSafeWalk = true
        }
    }
}
