/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoWeb「Intave14」模式 —— 源客户端 nowebmodes/intave/Intave14：
 * 网中纯前进且贴墙 → 空中移动系数 0.74；其余 → 系数 0.2 + 伪装 onGround。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave

import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.NoWebMode

object Intave14 : NoWebMode("Intave14") {
    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (!player.isInWeb) return
        if (player.movementInput.moveStrafe == 0.0F && mc.gameSettings.keyBindForward.isKeyDown && player.isCollidedVertically) {
            player.jumpMovementFactor = 0.74F
        } else {
            player.jumpMovementFactor = 0.2F
            player.onGround = true
        }
    }
}
