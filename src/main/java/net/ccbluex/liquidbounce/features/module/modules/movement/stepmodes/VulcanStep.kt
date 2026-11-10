/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.stepmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.StepEvent
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition

/**
 * Step「Vulcan」模式：
 * 距上次整格 Step 12 tick 后抬高步高到 1.0；整格 Step 时计时器减半
 * 并补发 +0.5 的位置包。
 * 逻辑按原版 1.8.9 实现等价移植。
 *
 * 原实现依赖混淆字段 thePlayer.cuz（>11 抬高步高，全客户端仅此一处引用，
 * 无法解出确切语义）——按「距上次整格 Step 的 tick 数」等价跟踪。
 * 原实现的计时器由外部系统复位；Lizz 无全局复位，这里在下一 tick 复位。
 */
object VulcanStep : StepMode("Vulcan") {

    /** 距上次整格 Step 的 tick 数（对应原混淆字段 cuz 的等价跟踪）。 */
    private var ticksSinceFullStep = 0

    private var pendingTimerReset = false

    override fun onEnable() {
        ticksSinceFullStep = 0
        pendingTimerReset = false
    }

    override fun onDisable() {
        mc.thePlayer?.stepHeight = 0.6f
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        ticksSinceFullStep++
        if (ticksSinceFullStep > 11) {
            player.stepHeight = 1.0f
        } else {
            player.stepHeight = 0.6f
        }
    }

    override fun onStep(event: StepEvent) {
        val player = mc.thePlayer ?: return
        if (event.stepHeight > 0.6f) {
            ticksSinceFullStep = 0
            mc.timer.timerSpeed = 0.5f
            sendPacket(C04PacketPlayerPosition(player.posX, player.posY + 0.5, player.posZ, true))
            pendingTimerReset = true
        }
    }

    override fun onTick() {
        // Lizz 无全局计时器复位：整格 Step 后的下一 tick 恢复 1.0
        if (pendingTimerReset) {
            pendingTimerReset = false
            mc.timer.timerSpeed = 1.0f
        }
    }
}
