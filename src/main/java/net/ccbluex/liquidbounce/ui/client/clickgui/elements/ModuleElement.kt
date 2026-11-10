/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.elements

import net.ccbluex.liquidbounce.LiquidBounce.clickGui
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.spacedModules
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
class ModuleElement(val module: Module) : ButtonElement(module.name, buttonAction = {
    // This module element handles the click action itself.
}) {
    companion object {
        /** 模块行的固定高度（ButtonElement 原本的 16）：drawerStyle 撑开行高时仍用它定位行/色块 */
        const val ROW_HEIGHT = 16
    }

    override val displayName
        get() = module.getName(spacedModules)

    override var hoverText = ""
        get() = module.description

    var showSettings = false
    var disableFiltering = false

    var supposedWidth = 0

    /**
     * 新布局（Zenith 模式）里设置列宽度是固定的，所以这里强制 settingsWidth。
     * <= 0 表示不启用，仍然走原来的内容自适应逻辑。
     */
    var forcedSettingsWidth = 0
        private set

    /** Zenith 模式下模块行由外部绘制，样式层只负责 settings 内容 */
    var hideRow = false

    /** 设置面板画在模块行「下方」（宽度对齐行宽），而不是右侧 —— 新布局的右列用 */
    var settingsBelow = false

    /**
     * Classic 布局 StyleSettings=Best：设置面板以「抽屉」形式展开在模块行正下方，
     * 展开时把行高撑开，下方的模块自动下移（不会被遮住/重叠）。
     * 设置内容宽度限制在分类面板边界内（minX..maxX 由样式的 below 分支负责）。
     */
    var drawerStyle = false

    /** drawerStyle 展开时的总行高：模块行 16 + 抽屉内容 + 底部间隙 */
    override val height: Int
        get() = if (drawerStyle && showSettings && settingsHeight > 0) {
            ROW_HEIGHT + settingsHeight + 6
        } else {
            ROW_HEIGHT
        }

    /** drawerStyle 时悬停/点击只针对模块行本身（抽屉区域交给设置控件） */
    override fun isHovered(mouseX: Int, mouseY: Int) =
        isVisible && mouseX in x..x + width && mouseY in y..y + ROW_HEIGHT

    fun forceSettingsWidth(width: Int) {
        forcedSettingsWidth = width.coerceAtLeast(0)
        if (width <= 0 || settingsWidth == width) return

        // 原来的 setter 只能变宽，这里借 disableFiltering 允许收缩一次
        disableFiltering = true
        settingsWidth = width
    }

    var settingsWidth = 0
        set(value) {
            val target = if (forcedSettingsWidth > 0) forcedSettingsWidth else value

            if (target > settingsWidth || disableFiltering) {
                disableFiltering = false
                field = target
            }

            if (target > supposedWidth) {
                supposedWidth = target
            }
        }

    var settingsHeight = 0

    var slowlyFade = 0
        set(value) {
            field = value.coerceIn(0, 255)
        }

    override fun drawScreenAndClick(mouseX: Int, mouseY: Int, mouseButton: Int?): Boolean {
        this.supposedWidth = 0

        return clickGui.style.drawModuleElementAndClick(mouseX, mouseY, this, mouseButton)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (!isHovered(mouseX, mouseY)) {
            return false
        }

        when (mouseButton) {
            0 -> {
                module.toggle()
                clickGui.style.clickSound()
            }
            1 -> {
                if (module.values.isNotEmpty()) {
                    showSettings = !showSettings
                    clickGui.style.showSettingsSound()
                }
            }
        }

        return true
    }
    
    fun adjustWidth() {
        if (settingsWidth - supposedWidth > 16) {
            disableFiltering = true
            settingsWidth = supposedWidth
        }
    }

}