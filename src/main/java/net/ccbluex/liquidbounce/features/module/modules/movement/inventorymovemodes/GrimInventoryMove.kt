/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * InventoryMove「Grim」模式 —— 本批（同源客户端更新版）等价移植：
 * 在 GUI（非聊天 / 非标准 ClickGUI）中按真实按键状态回填移动键，
 * 在背包/箱子界面强制取消疾跑。
 *
 * 移植说明：
 * - 源 SprintEvent（GuiInventory/GuiChest → setSprinting(false)）→ onUpdate 直接强制；
 * - 「Manager Extra Sprint Ticks」为源 Sprint 管理器读取的选项：Lizz 无该管理器，
 *   保留设置以便配置一致。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.inventorymovemodes

import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.GameSettings

object GrimInventoryMove : InventoryMoveMode("Grim") {

    /** Manager Extra Sprint Ticks（默认 9，0..20）。 */
    var managerExtraSprintTicks = 9

    private val movementKeys
        get() = arrayOf(
            mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindLeft,
            mc.gameSettings.keyBindJump
        )

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        val screen = mc.currentScreen

        // 源挂在 SprintEvent：背包/箱子界面取消疾跑
        if (screen is GuiInventory || screen is GuiChest) {
            player.isSprinting = false
        }

        // 源挂在 PreUpdateEvent：GUI 中按真实物理按键回填移动键
        if (screen != null && screen !is GuiChat && screen !is ClickGui) {
            for (keybinding in movementKeys) {
                keybinding.pressed = GameSettings.isKeyDown(keybinding)
            }
        }
    }
}
