/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.elements

import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

abstract class Element : MinecraftInstance {

    var x = 0
    var y = 0
    var width = 0
    abstract val height: Int
    var isVisible = true

    fun setLocation(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    abstract fun drawScreenAndClick(mouseX: Int, mouseY: Int, mouseButton: Int? = null): Boolean
    open fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) = isHovered(mouseX, mouseY)

    open fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) = isHovered(mouseX, mouseY)

    /** open：ModuleElement 在 drawerStyle（Best 抽屉）下行高被撑开，但「悬停/点击」只针对模块行本身 */
    open fun isHovered(mouseX: Int, mouseY: Int) = isVisible && mouseX in x..x + width && mouseY in y..y + height
}