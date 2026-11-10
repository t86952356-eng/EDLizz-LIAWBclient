/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.stepmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.StepEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold2
import net.ccbluex.liquidbounce.utils.block.block
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.block.BlockSlab
import net.minecraft.block.BlockStairs
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.util.BlockPos

/**
 * Step「WatchdogNCP」模式：
 * 地面（非液体、非跳键、非搭路）时步高 1.0；整格 Step 时以 vd() 速度直线、
 * 临时关闭 Speed（300ms 后自动恢复）、并发送 0.42/0.75/1.0 的台阶位置包序列。
 * 逻辑按原版 1.8.9 实现等价移植（原名 Watchdog）。
 */
object WatchdogNCPStep : StepMode("WatchdogNCP") {

    private var height = 0.0
    private var ticksSinceStep = 0
    private var speedReEnableTime = 0L
    private var speedDisabled = false

    private val scaffoldEnabled get() = Scaffold.handleEvents() || Scaffold2.handleEvents()

    override fun onEnable() {
        height = 0.0
        ticksSinceStep = 0
        speedReEnableTime = 0L
        speedDisabled = false
    }

    override fun onDisable() {
        mc.thePlayer?.stepHeight = 0.6f
        // 安全兜底：避免 Step 在 300ms 窗口内被关闭后 Speed 一直保持关闭
        if (speedDisabled) {
            Speed.state = true
            speedDisabled = false
        }
        speedReEnableTime = 0L
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        if (!scaffoldEnabled) {
            if (player.onGround && !(player.isInWater || player.isInLava) &&
                !mc.gameSettings.keyBindJump.isKeyDown
            ) {
                player.stepHeight = 1.0f
            } else {
                player.stepHeight = 0.6f
            }
        }
    }

    override fun onStep(event: StepEvent) {
        val player = mc.thePlayer ?: return

        PortedMove.strafe(PortedMove.vd())
        val d0 = event.stepHeight.toDouble()
        height = d0
        if (Speed.handleEvents() && d0 > 0.6f) {
            Speed.state = false
            speedDisabled = true
        }

        if (!scaffoldEnabled) {
            player.stepHeight = 1.0f
            if (player.onGround && !(player.isInWater || player.isInLava) &&
                BlockPos(player.posX, player.posY, player.posZ).block !is BlockSlab &&
                BlockPos(player.posX, player.posY, player.posZ).block !is BlockStairs
            ) {
                if (d0 > 0.6f) {
                    var offsets = doubleArrayOf()
                    if (d0 == 1.0) {
                        offsets = doubleArrayOf(0.42f.toDouble(), 0.75, 1.0)
                    }

                    speedReEnableTime = System.currentTimeMillis() + 300L

                    for (d1 in offsets) {
                        mc.timer.timerSpeed = 0.25f
                        sendPacket(C04PacketPlayerPosition(player.posX, player.posY + d1, player.posZ, false))
                        ticksSinceStep = 0
                    }
                }
            }
        }
    }

    override fun onTick() {
        ticksSinceStep++
        if (ticksSinceStep == 1) {
            mc.timer.timerSpeed = 1.0f
        }

        if (speedDisabled && System.currentTimeMillis() > speedReEnableTime) {
            Speed.state = true
            speedDisabled = false
            speedReEnableTime = 0L
        }
    }
}
