/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.clickgui

import net.ccbluex.liquidbounce.LiquidBounce.clickGui
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.fadeSpeed
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.maxElements
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.panelsForcedInBoundaries
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.scale
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui.clamp
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.Element
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ModuleElement
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.minecraft.client.gui.ScaledResolution
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SideOnly(Side.CLIENT)
class Panel(
    val name: String,
    var x: Int,
    var y: Int,
    val width: Int,
    val height: Int,
    var open: Boolean,
    val elements: List<Element>
) : MinecraftInstance {

    var x2 = 0
    var y2 = 0
    
    private var updatePos = false

    fun parseX(value: Int = x): Int {
        if (!panelsForcedInBoundaries)
            return value

        val settingsWidth =
            if (open) elements.filterIsInstance<ModuleElement>().maxOfOrNull { if (it.showSettings) it.settingsWidth else 0 } ?: 0
            else 0

        return value.clamp(0, (ScaledResolution(mc).scaledWidth / scale - width - settingsWidth).roundToInt())
    }
    fun parseY(value: Int = y): Int {
        if (!panelsForcedInBoundaries)
            return value

        var yPos = height + 4
        var panelHeight = height + fade

        if (open)
            for (element in elements) {
                if (element.isVisible) {
                    // drawerStyle 时 element.height 已包含抽屉高度，这里不再重复叠加
                    if (element is ModuleElement && !element.drawerStyle &&
                        element.showSettings && element.settingsHeight != 0
                    ) {
                        val relativeSettingsHeight = yPos + element.settingsHeight
                        if (relativeSettingsHeight > panelHeight) panelHeight = relativeSettingsHeight
                    }
                    yPos += element.height + 1
                }
            }

        return value.clamp(0, (ScaledResolution(mc).scaledHeight / scale - panelHeight).roundToInt())
    }

    var drag = false
    val scrollbar
        get() = elements.size > maxElements

    /**
     * 只修「彻底丢出屏幕」的离谱坐标（例如被中键拖滚一路推到几千像素外）。
     * 正常滚动可以随意把面板推到负坐标，这里不做限制，否则滚轮会像被卡住。
     */
    fun repairPosition() {
        val resolution = ScaledResolution(mc)
        val maxX = (resolution.scaledWidth / scale).roundToInt()
        val maxY = (resolution.scaledHeight / scale).roundToInt()

        if (x < -maxX * 4 || x > maxX * 4) x = 100
        if (y < -maxY * 4 || y > maxY * 4) y = 5
    }

    var isVisible = true

    /**
     * 固定内容高度。设置列这种「容器型面板」没有 element，
     * 靠它把面板撑到指定高度，否则 fade 会停在 0。
     */
    var minBodyHeight = 0

    var fade = 0
        set(value) {
            val parsed = value.clamp(0, maxOf(elementsHeight, minBodyHeight))

            if (parsed != field) {
                // Update panel pos not to extend beyond border.
                field = parsed

                x = parseX()
                y = parseY()
            }
        }

    private var elementsHeight = 0

    private var scroll = 0
        set(value) {
            // How many elements should be hidden
            val hiddenCount = elements.size - maxElements
            // Don't overscroll
            field = if (hiddenCount > 0) min(hiddenCount, value.coerceAtLeast(0)) else 0
        }

    fun drawScreenAndClick(mouseX: Int, mouseY: Int, mouseButton: Int? = null): Boolean {
        if (!isVisible) return false

        updateElementsHeight()

        // Best 抽屉：内容高度超过上帧的稳定目标 → 立即长高（在 drawPanel 之前！），
        // 保证抽屉在「打开的当帧」就被面板背景盖住 —— 不再出现伸出面板底边的过渡帧。
        // （分类展开动画不受影响：动画期间 elementsHeight == lastContentTarget 不触发）
        if (open && lastContentTarget > 0 && maxOf(elementsHeight, minBodyHeight) > lastContentTarget) {
            lastContentTarget = maxOf(elementsHeight, minBodyHeight)
            fade = maxOf(elementsHeight, minBodyHeight)
        }

        // Drag
        if (drag) {
            x = parseX(x2 + mouseX)
            y = parseY(y2 + mouseY)
        }

        clickGui.style.drawPanel(mouseX, mouseY, this)

        var yPos = y + height - 2

        val visibleRange = getVisibleRange()

        elements.forEachIndexed { index, element ->
            if (index in visibleRange) {
                element.isVisible = true
                element.setLocation(x, yPos)
                element.width = width

                // 面板内容裁切边界（Best 抽屉的 scissor 用）：面板背景实际覆盖的区域
                if (element is ModuleElement) {
                    element.clipTop = y + height - 2
                    element.clipBottom = y + height + fade
                }

                // If mouse wasn't hovering above any ButtonElement, drawScreenAndClick got called with mouseButton != null.
                // Mouse was detected to be hovering above a value while rendering it.
                // True was returned to stop any further values from getting clicked.
                if (yPos <= y + fade && element.drawScreenAndClick(mouseX, mouseY, mouseButton)) {
                    // Update panel pos not to extend beyond border.
                    updatePos = true
                    return true
                }

                yPos += element.height + 1
                element.isVisible = true
            } else element.isVisible = false
        }

        // Position is updated on next draw calls because ModuleElement.settingsHeight gets updated on next draw call.
        // Updated to prevent long settings lists from extending beyond window boundaries.
        if (updatePos) {
            x = parseX()
            y = parseY()
            updatePos = false
        }

        // 元素绘制完（settingsHeight 刚更新），立刻重算内容高度，
        // 下一帧 updateFade 就能拿到最新的抽屉高度
        updateElementsHeight()

        return false
    }

    fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (!isVisible) return false

        if (mouseButton == 1 && isHovered(mouseX, mouseY)) {
            open = !open
            clickGui.style.showSettingsSound()
            return true
        }

        if (elements.any { it.y <= y + fade && it.mouseClicked(mouseX, mouseY, mouseButton) }) {
            // Update panel pos not to extend beyond border.
            updatePos = true
            return true
        }

        return drawScreenAndClick(mouseX, mouseY, mouseButton)
    }

    fun mouseReleased(mouseX: Int, mouseY: Int, button: Int): Boolean {
        if (!isVisible) return false

        drag = false

        if (!open) return false

        return elements.any { it.y <= y + fade && it.mouseReleased(mouseX, mouseY, button) }
    }

    fun handleScroll(mouseX: Int, mouseY: Int, wheel: Int): Boolean {
        if (mouseX in x..x + width && mouseY in y..y + height + elementsHeight) {
            if (wheel < 0) scroll++
            else scroll--

            return true
        }
        return false
    }

    fun updateFade(delta: Int) {
        val target = if (open) maxOf(elementsHeight, minBodyHeight) else 0

        // 面板已展开时内容高度变化（Best 抽屉展开/收起、滚动）：面板高度直接跟随目标，
        // 不再走缓动 —— 否则抽屉会在动画期间伸出面板底边（短分类下尤其明显）。
        // 分类展开/收起（lastContentTarget == 0）仍走原来的缓出动画。
        if (open && lastContentTarget > 0 && target != lastContentTarget) {
            lastContentTarget = target
            fade = target
            return
        }

        lastContentTarget = target

        val remaining = target - fade

        // 已经很接近就直接吸附到目标位置
        if (abs(remaining) <= 2) {
            fade = target
            return
        }

        // 指数缓出：位移正比于剩余距离 —— 起步最快，越接近目标越慢
        val factor = (fadeSpeed * delta / 100F).coerceIn(0.02F, 0.5F)
        val step = (abs(remaining) * factor).roundToInt().coerceAtLeast(1)

        fade += if (remaining > 0) step else -step
    }

    /** 上一次 fade 的目标高度：区分「分类展开动画」和「内容高度变化」 */
    private var lastContentTarget = 0

    private fun updateElementsHeight() {
        var height = 0

        // 与绘制循环一致：只统计「可见范围」内元素的高度（含抽屉撑开的行高），
        // 否则滚动后抽屉高度不进入面板总高，抽屉会伸出面板底边
        val visibleRange = getVisibleRange()

        for ((index, element) in elements.withIndex()) {
            if (index !in visibleRange) continue
            height += element.height + 1
        }

        // 底部留出圆角半径左右的空隙，否则最后一个（开启的）模块的直角色块
        // 会盖住面板底部的圆角
        elementsHeight = height + 3
    }

    fun getVisibleRange(): IntRange {
        val dropLastCount = elements.size - maxElements - scroll

        return max(scroll + min(dropLastCount, 0), 0)..elements.lastIndex - max(dropLastCount, 0)
    }

    fun isHovered(mouseX: Int, mouseY: Int) = mouseX in x..x + width && mouseY in y..y + height

    override fun hashCode(): Int = this.name.hashCode()

    override fun equals(other: Any?): Boolean = other is Panel && other.name == this.name
}