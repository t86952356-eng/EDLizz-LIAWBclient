/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Speed「IntaveNew」模式 —— 源客户端的 IntaveNew：
 * 地面 XZ 微增（×1.00666666666）+ timer 1.1；空中首次 ×1.05 + timer 0.95，
 * 之后持续 ×1.0015 + timer 1.0015。静止时把这三档 timer 复位为 1.0。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.intave

import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceXZ
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.safeJump
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.changeTimer

object IntaveNewSpeed : SpeedMode("IntaveNew") {

    /** AutoJump（源 Speed.intaveNewAutoJump，与 Matrix3 系共用，由 Speed 同步）。 */
    var autoJump = true

    private var boosted = false

    override fun onDisable() {
        boosted = false
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (!player.isMoving) {
            val resetTimer = mc.timer.timerSpeed == 1.1f || mc.timer.timerSpeed == 0.95f || mc.timer.timerSpeed == 1.0015f
            if (resetTimer) changeTimer(1.0f)
            return
        }
        if (player.onGround && !mc.gameSettings.keyBindJump.isKeyDown && autoJump) {
            player.safeJump(0.22)
        }

        when {
            player.onGround -> {
                reduceXZ(1.00666666666)
                changeTimer(1.1f)
                boosted = false
            }
            !boosted -> {
                // 第一次空中加速
                reduceXZ(1.05)
                changeTimer(0.95f)
                boosted = true
            }
            else -> {
                // 持续空中加速
                reduceXZ(1.0015)
                changeTimer(1.0015f)
            }
        }
    }
}
