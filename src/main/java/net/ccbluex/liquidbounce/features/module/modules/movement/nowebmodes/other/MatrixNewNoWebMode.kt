/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoWeb「MatrixNew」模式 —— 源客户端 nowebmodes/other/MatrixNew：
 * 网中移动累计 tick，超过 ThresholdTicks 后每 tick 钩子跳 + XZ×1.1 + 空中移动系数 0.26；
 * 未过阈值时同样钩子跳 + XZ×1.1（系数保持原版）；KeepY 开启时不按跳/潜行就把 Y 冻结（×0）。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.NoWebMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceXZ
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceY

object MatrixNewNoWebMode : NoWebMode("MatrixNew") {

    /** MatrixNew-ThresholdTicks（默认 4，0..20）。 */
    var noWebThreshold = 4

    /** MatrixNew-ShowThresholdTicks（默认关）：聊天栏打印累计 tick。 */
    var showThresholdTicks = false

    /** KeepY（默认关）。 */
    var keepY = false

    /** 源 NoWeb.inWebTicks：网中移动的累计 tick。 */
    private var inWebTicks = 0

    override fun onDisable() {
        inWebTicks = 0
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (!player.isInWeb || !player.isMoving) {
            inWebTicks = 0
            if (!mc.gameSettings.keyBindJump.isKeyDown && !mc.gameSettings.keyBindSneak.isKeyDown && keepY && player.isInWeb) reduceY(0.0)
            return
        }

        if (player.isMoving && player.isInWeb) inWebTicks++
        if (showThresholdTicks) chat("$inWebTicks")
        if (inWebTicks > noWebThreshold && player.isMoving) {
            player.jump()
            reduceXZ(1.1)
            player.jumpMovementFactor = 0.26f
        } else if (player.isMoving) {
            player.jump()
            reduceXZ(1.1)
        }
        if (!mc.gameSettings.keyBindJump.isKeyDown && !mc.gameSettings.keyBindSneak.isKeyDown && keepY && player.isInWeb) reduceY(0.0)
    }
}
