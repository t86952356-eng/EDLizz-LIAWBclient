/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoWeb「Matrix」模式 —— 源客户端 nowebmodes/other/Matrix：
 * 网中跳跃系数 0.12425、Y 下沉 -0.0125（潜行 -0.1625），
 * 每 40 tick timer 拉到 3.0（下一帧复位）。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.NoWebMode

object Matrix : NoWebMode("Matrix") {
    private var usedTimer = false

    override fun onDisable() {
        if (usedTimer) {
            mc.timer.timerSpeed = 1F
            usedTimer = false
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (usedTimer) {
            mc.timer.timerSpeed = 1F
            usedTimer = false
        }
        if (!player.isInWeb) return

        player.jumpMovementFactor = 0.12425f
        player.motionY = -0.0125
        if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -0.1625
        if (player.ticksExisted % 40 == 0) {
            mc.timer.timerSpeed = 3.0F
            usedTimer = true
        }
    }
}
