/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Speed「Intave14New」模式 —— 源客户端的 Intave14.8.4：
 * 地面移动自动跳；按 airTicks 分段 XZ 增幅（1 → ×1.04；2/3/4 → ×1.02）；
 * 可选 Timer 1.002。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.intave

import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.MovementUtils

object Intave14NewSpeed : SpeedMode("Intave14New") {

    /** Timer（源 useTimer，默认关）。 */
    var useTimer = false

    /** Debugger（源 Debugger，默认关）。 */
    var debugger = false

    override fun onTick() {
        val player = mc.thePlayer ?: return

        if (player.onGround && player.isMoving) player.tryJump()
        when (MovementUtils.airTicks) {
            1 -> {
                factorXZ(1.04)
                if (debugger) {
                    chat("Boost")
                }
            }

            2, 3, 4 -> {
                factorXZ(1.02)
            }
        }
        if (useTimer) {
            mc.timer.timerSpeed = 1.002f
        } else mc.timer.timerSpeed = 1.0f
    }

    private fun factorXZ(factor: Double) {
        mc.thePlayer?.let {
            it.motionX *= factor
            it.motionZ *= factor
        }
    }
}
