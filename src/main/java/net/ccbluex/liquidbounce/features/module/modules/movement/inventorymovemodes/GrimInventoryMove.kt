/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.inventorymovemodes

import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.GameSettings

/**
 * InventoryMove「Grim」模式：
 * 在 GUI（非聊天/ClickGui）中按真实按键状态回填移动键，且在背包/箱子界面强制取消疾跑。
 * 逻辑按原版 1.8.9 实现等价移植（原名 Grim 2）。
 *
 * 原实现的 SprintEvent 在 Lizz 无对应事件：按 b12.10 的映射在 UpdateEvent 中
 * 直接强制 isSprinting = false。
 */
object GrimInventoryMove : InventoryMoveMode("Grim") {

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

        // 原实现挂在 SprintEvent：GUI 中取消疾跑
        if (screen is GuiInventory || screen is GuiChest) {
            player.isSprinting = false
        }

        // 原实现挂在 PreUpdateEvent：按真实按键状态回填移动键
        if (screen != null && screen !is GuiChat && screen !is ClickGui) {
            for (keybinding in movementKeys) {
                keybinding.pressed = GameSettings.isKeyDown(keybinding)
            }
        }
    }
}
