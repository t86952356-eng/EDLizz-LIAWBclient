/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Speed「IntaveTimer」模式 —— 源客户端的 Intave3：
 * 移动中地面起跳，按 fallDistance 切换 timer（>=1.3 → 1.0；0.1..1.3 → 0.7；<=0.1 → 1.4）。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.intave

import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving

object IntaveTimerSpeed : SpeedMode("IntaveTimer") {

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (player.isMoving) {
            if (player.onGround)
                player.jump()
            when {
                player.fallDistance >= 1.3 -> mc.timer.timerSpeed = 1f
                player.fallDistance > 0.1 && player.fallDistance < 1.3 -> mc.timer.timerSpeed = 0.7f
                !player.onGround && player.fallDistance <= 0.1 -> mc.timer.timerSpeed = 1.4f
            }
        }
    }
}
