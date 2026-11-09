/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers

/**
 * Speed「Polar」模式：固定计时器 1.009 + 起跳循环 + moveFlying 微调。
 */
object PolarSpeed : SpeedMode("Polar") {

    private var jumps = 0

    override fun onEnable() {
        jumps = 0
        mc.timer.timerSpeed = 1f
    }

    override fun onDisable() {
        mc.timer.timerSpeed = 1f
    }

    override fun onUpdate() {
        mc.thePlayer ?: return
        mc.timer.timerSpeed = 1.009f
        // 原实现在空中按前进时重置旋转组件（纯查询无副作用，此处省略）
    }

    override fun onStrafeEvent(event: StrafeEvent) {
        val player = mc.thePlayer ?: return

        if (PortedTrackers.itemUseTicks == 5 && jumps % 2 != 0) {
            player.motionY -= 0.03
        }

        if (player.onGround) {
            player.jump()
        }

        PortedMove.moveFlying(0.002)
    }

    override fun onJump(event: JumpEvent) {
        jumps++
    }
}
