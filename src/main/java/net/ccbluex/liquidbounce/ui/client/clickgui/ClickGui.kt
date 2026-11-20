/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.clickgui

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.api.ClientApi
import net.ccbluex.liquidbounce.api.autoSettingsList
import net.ccbluex.liquidbounce.api.loadSettings
import net.ccbluex.liquidbounce.chat.IrcClient
import net.ccbluex.liquidbounce.config.SettingsUtils
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.guiBackgroundColor
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.guiColor
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.scale
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.scrolls
import net.ccbluex.liquidbounce.file.FileManager.clickGuiConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ButtonElement
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.Element
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ModuleElement
import net.ccbluex.liquidbounce.ui.client.clickgui.style.Style
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.BlackStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.LiquidBounceStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.SlowlyStyle
import net.ccbluex.liquidbounce.ui.client.hud.HUD
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts.fontSemibold35
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.Targets
import net.ccbluex.liquidbounce.utils.client.ClientUtils
import net.ccbluex.liquidbounce.utils.client.asResourceLocation
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.playSound
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorder
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.disableLighting
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glScaled
import java.awt.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * ClickGUI。
 *
 * 两种布局，全部用原来的 [Panel] / [ButtonElement] / [ModuleElement] 和 [Style] 渲染，
 * 不另开渲染路径 —— 只是把面板摆到不同位置：
 *
 * - [Mode.CLASSIC]：原来的一列独立面板 + 左下角 HUD/IRC
 * - [Mode.ZENITH] ：三列并排（分类栏 | 模块列表 | 设置列），也就是 Zenith 的排布
 *
 * 模式由 ClickGUI 模块的 Layout 设置在打开时写入 [mode]。
 */
object ClickGui : GuiScreen() {

    enum class Mode { CLASSIC, ZENITH }

    var mode = Mode.CLASSIC

    // Note: hash key = [Panel.name]
    val panels = linkedSetOf<Panel>()

    var style: Style = LiquidBounceStyle

    private val hudIcon = ResourceLocation("${CLIENT_NAME.lowercase()}/custom_hud_icon.png")

    /** IRC 面板开关 + 展开进度（纯前端 UI，不接后端） */
    private var ircOpen = false
    private var ircProgress = 0F

    private var mouseX = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }
    private var mouseY = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    private var autoScrollY: Int? = null

    // Used when closing ClickGui using its key bind, prevents it from getting closed instantly after getting opened.
    // Caused by keyTyped being called along with onKey that opens the ClickGui.
    private var ignoreClosing = false

    // ---- 打开 / 关闭的弹簧动画（0..1，会冲过头再弹回来）----
    private var openValue = 0f
    private var openVelocity = 0f
    private var closing = false

    private var fxScale = 1f
    private var fxCx = 0f
    private var fxCy = 0f

    /** 动画没稳定前不吃鼠标，避免缩放过程中点到错的位置 */
    private val animating: Boolean
        get() = abs(openValue - 1f) > 0.02f || abs(openVelocity) > 0.1f

    // ------------------------------------------------------------------ zenith 状态

    private val categories = Category.entries

    private var railPanel: Panel? = null
    private var modulePanel: Panel? = null
    private var settingsPanel: Panel? = null
    private var settingsElement: ModuleElement? = null

    private val zenithElements = HashMap<Module, ModuleElement>()

    /** 选中的分类（categories.size 表示 Targets） */
    private var zenithCat = 0
    private var zenithModule: Module? = null
    private var zenithScroll = 0F
    private var zenithMaxScroll = 0F

    private var zenithX = 0
    private var zenithY = 0

    // ------------------------------------------------------------------ StyleSettings=Menu 状态

    /** StyleSettings=Menu（Classic 布局）是否激活 —— 统一设置面板（Panel Menu） */
    private val menuMode: Boolean
        get() = mode == Mode.CLASSIC && ClickGUI.styleSettings == "Menu" && style.supportsSettingsBelow

    /** 每个模块独立的菜单渲染元素（不干扰分类列表里那一行的状态） */
    private val menuElements = HashMap<Module, ModuleElement>()

    /** 当前在统一 Menu 面板里展示设置的模块（一次只有一个 —— 统一接口） */
    private var menuElement: ModuleElement? = null

    /** menuElement 所属的分类面板（Menu 锚定在它的主列表上方） */
    private var menuHostPanel: Panel? = null

    private var menuScroll = 0F
    private var menuMaxScroll = 0F
    private var menuProgress = 0F
    private var menuBounds: FloatArray? = null
    private var menuCloseBounds: FloatArray? = null

    // ------------------------------------------------------------------ classic 面板

    fun setDefault() {
        panels.clear()

        val width = 100
        val height = 18
        var yPos = 5

        for (category in Category.entries) {
            panels += Panel(
                category.displayName,
                x = 100,
                y = yPos,
                width,
                height,
                false,
                moduleManager[category].map(::ModuleElement)
            )

            yPos += 20
        }

        yPos += 20
        panels += setupTargetsPanel(100, yPos, width, height)

        // Settings Panel
        yPos += 20
        panels += setupSettingsPanel(100, yPos, width, height)
    }

    private fun setupTargetsPanel(xPos: Int = 100, yPos: Int, width: Int, height: Int) =
        Panel("Targets", xPos, yPos, width, height, false, targetButtons())

    private fun targetButtons(): List<ButtonElement> = listOf(
        ButtonElement("Players", { if (Targets.player) guiColor else Int.MAX_VALUE }) {
            Targets.player = !Targets.player
        },
        ButtonElement("Mobs", { if (Targets.mob) guiColor else Int.MAX_VALUE }) {
            Targets.mob = !Targets.mob
        },
        ButtonElement("Animals", { if (Targets.animal) guiColor else Int.MAX_VALUE }) {
            Targets.animal = !Targets.animal
        },
        ButtonElement("Invisible", { if (Targets.invisible) guiColor else Int.MAX_VALUE }) {
            Targets.invisible = !Targets.invisible
        },
        ButtonElement("Dead", { if (Targets.dead) guiColor else Int.MAX_VALUE }) {
            Targets.dead = !Targets.dead
        },
    )

    private fun setupSettingsPanel(xPos: Int = 100, yPos: Int, width: Int, height: Int): Panel {
        val list = autoSettingsList?.map { setting ->
            ButtonElement(setting.name, { Integer.MAX_VALUE }) {
                SharedScopes.IO.launch {
                    try {
                        chat("Loading settings...")

                        // Load settings and apply them
                        val settings = ClientApi.getSettingsScript(settingId = setting.settingId)

                        chat("Applying settings...")
                        SettingsUtils.applyScript(settings)

                        chat("§6Settings applied successfully.")
                        HUD.addNotification(Notification.informative("ClickGUI", "Updated Settings"))
                        mc.playSound("random.anvil_use".asResourceLocation())
                    } catch (e: Exception) {
                        ClientUtils.LOGGER.error("Failed to load settings", e)
                        chat("Failed to load settings: ${e.message}")
                    }
                }
            }.apply {
                this.hoverText = buildString {
                    appendLine("§7Description: §e${setting.description.ifBlank { "No description available" }}")
                    appendLine("§7Type: §e${setting.type.displayName}")
                    appendLine("§7Contributors: §e${setting.contributors}")
                    appendLine("§7Last updated: §e${setting.date}")
                    append("§7Status: §e${setting.statusType.displayName} §a(${setting.statusDate})")
                }
            }
        } ?: run {
            // Try load settings
            loadSettings(useCached = true) {
                mc.addScheduledTask {
                    setupSettingsPanel(xPos, yPos, width, height)
                }
            }

            emptyList()
        }

        return Panel("Auto Settings", xPos, yPos, width, height, false, list)
    }

    // ------------------------------------------------------------------ GuiScreen

    override fun drawScreen(screenMouseX: Int, screenMouseY: Int, partialTicks: Float) {
        updateOpenAnimation()

        // 关闭动画播完才真正退出
        if (closing && openValue <= 0.02f && abs(openVelocity) < 0.08f) {
            mc.displayGuiScreen(null)
            return
        }

        // Enable DisplayList optimization
        assumeNonVolatile {
            // 背景遮罩，颜色可自定义（原来是 drawDefaultBackground() 的半透明黑）
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), guiBackgroundColor.rgb)

            // 整块内容按弹簧缩放：弹出时冲过 1.0 再回弹
            GL11.glPushMatrix()
            GL11.glTranslatef(fxCx, fxCy, 0f)
            GL11.glScalef(fxScale, fxScale, 1f)
            GL11.glTranslatef(-fxCx, -fxCy, 0f)

            if (mode == Mode.ZENITH) {
                drawZenith(screenMouseX, screenMouseY)
            } else {
                drawClassic(screenMouseX, screenMouseY)
            }

            GL11.glPopMatrix()
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    /**
     * 弹簧：位移正比于剩余距离，阻尼不足时会冲过目标再弹回来。
     * 固定 8ms 子步进，低帧率也不会弹飞。
     */
    private fun updateOpenAnimation() {
        // 只有明确要弹出效果风格才播；经典风格保持原来的瞬开瞬关
        if (!style.wantsPopAnimation) {
            openValue = if (closing) 0f else 1f
            openVelocity = 0f
            fxScale = 1f
            fxCx = width / 2f
            fxCy = height / 2f
            return
        }

        val dt = (deltaTime.coerceIn(1, 50) / 1000f)
        val target = if (closing) 0f else 1f
        // 关闭时加大阻尼，避免缩过头再弹回
        val stiffness = 340f
        val damping = if (closing) 24f else 15f

        var remaining = dt
        while (remaining > 0f) {
            val h = if (remaining > 0.008f) 0.008f else remaining

            openVelocity += (target - openValue) * stiffness * h
            openVelocity *= exp(-damping * h).toFloat()
            openValue += openVelocity * h

            remaining -= h
        }

        fxScale = 0.90f + 0.10f * openValue
        fxCx = width / 2f
        fxCy = height / 2f
    }

    // ------------------------------------------------------------------ classic 绘制

    private fun drawClassic(screenMouseX: Int, screenMouseY: Int) {
        mouseX = (screenMouseX / scale).roundToInt()
        mouseY = (screenMouseY / scale).roundToInt()

        val fullHeight = ScaledResolution(mc).scaledHeight

        drawImage(hudIcon, 9, fullHeight - 41, 32, 32)

        // IRC 开关动画：位移正比于剩余距离 —— 起步快、越接近目标越慢
        val ircRemaining = (if (ircOpen) 1F else 0F) - ircProgress
        ircProgress = if (ircRemaining > -0.005F && ircRemaining < 0.005F) {
            if (ircOpen) 1F else 0F
        } else {
            ircProgress + ircRemaining * 0.17F
        }

        // IRC 按钮：紧接 HUD 图标右边，同尺寸 32×32
        val ircHovered = screenMouseX in 41..77 && screenMouseY in fullHeight - 46..fullHeight - 9
        drawRect(
            45f, (fullHeight - 41).toFloat(), 77f, (fullHeight - 9).toFloat(),
            if (ircHovered || ircOpen) 0x50FFFFFF else 0x28FFFFFF
        )
        drawCenteredString(mc.fontRendererObj, "IRC", 61, fullHeight - 27, -1)

        val s = scale.toDouble()
        glScaled(s, s, s)

        // StyleSettings=Best（Classic 布局）：设置面板以「抽屉」形式画在模块行正下方，
        // 下方的模块自动下移；设置宽度被限制在分类面板边界内。
        // Normal（默认）：现有外观完全不变（设置面板画在行右侧）。
        // Menu：模块行不再各自展开内联设置 —— 任何模块的设置都进统一的 Menu 面板。
        val drawerBest = ClickGUI.styleSettings == "Best" && style.supportsSettingsBelow
        val menuActive = menuMode
        for (panel in panels) {
            for (element in panel.elements) {
                if (element is ModuleElement) {
                    element.drawerStyle = drawerBest
                    element.settingsBelow = drawerBest
                    if (menuActive) element.showSettings = false
                }
            }
        }
        if (!menuActive && menuElement != null) resetMenu()

        for (panel in panels) {
            panel.updateFade(deltaTime)
            // IRC 打开时模块列表同步收起（fade 同时驱动面板高度和内容高度）
            panel.fade = (panel.fade * (1F - ircProgress)).toInt()
            panel.drawScreenAndClick(mouseX, mouseY)
        }

        // IRC 面板：纯前端 UI，圆角/阴影/模糊沿用 ClickGUI 自己的参数
        if (ircProgress > 0.01F) {
            LiquidBounceStyle.drawIrcPanel(ircProgress, mouseX, mouseY)
        }

        // StyleSettings=Menu：统一设置面板 —— 画在所有分类面板之上，盖在宿主面板的主列表上方
        if (menuMode) drawMenuPanel(mouseX, mouseY)

        descriptions@ for (panel in panels.reversed()) {
            // Don't draw hover text when hovering over a panel header.
            if (panel.isHovered(mouseX, mouseY)) break

            // StyleSettings=Menu：悬停在统一菜单上时不显示被盖住的模块的描述
            if (menuElement != null) {
                val menuRect = menuBounds
                if (menuRect != null && mouseX >= menuRect[0] && mouseX <= menuRect[2] &&
                    mouseY >= menuRect[1] && mouseY <= menuRect[3]
                ) break@descriptions
            }

            for (element in panel.elements) {
                if (element is ButtonElement) {
                    if (element.isVisible && element.hoverText.isNotBlank() && element.isHovered(
                            mouseX, mouseY
                        ) && element.y <= panel.y + panel.fade
                    ) {
                        style.drawHoverText(mouseX, mouseY, element.hoverText)
                        // Don't draw hover text for any elements below.
                        break@descriptions
                    }
                }
            }
        }

        if (Mouse.hasWheel()) {
            // 中键拖滚：按钮已经松开就清掉 autoScrollY，
            // 否则它会一直拿「按下点 - 当前鼠标」当滚轮量，把面板甩出屏幕
            if (autoScrollY != null && !Mouse.isButtonDown(2)) {
                autoScrollY = null
            }

            val wheel = autoScrollY?.let { it - screenMouseY } ?: Mouse.getDWheel()

            if (wheel != 0) {
                var handledScroll = false

                // StyleSettings=Menu：悬停在统一菜单上时滚轮只滚菜单内容（优先于分类列表）
                if (menuMode && menuElement != null) {
                    val menuRect = menuBounds
                    if (menuRect != null && mouseX >= menuRect[0] && mouseX <= menuRect[2] &&
                        mouseY >= menuRect[1] && mouseY <= menuRect[3]
                    ) {
                        menuScroll = (menuScroll - wheel / 10F).coerceIn(0F, menuMaxScroll)
                        handledScroll = true
                    }
                }

                if (!handledScroll) {
                    // Handle foremost panel.
                    for (panel in panels.reversed()) {
                        if (panel.handleScroll(mouseX, mouseY, wheel)) {
                            handledScroll = true
                            break
                        }
                    }
                }

                if (!handledScroll) handleScroll(wheel)
            }
        }

        disableLighting()
        RenderHelper.disableStandardItemLighting()
        glScaled(1.0, 1.0, 1.0)
    }

    private fun handleScroll(wheel: Int) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            // IRC 面板打开时，滚轮只滚聊天，不再缩放整个 ClickGUI
            if (ircOpen) {
                IrcClient.scroll += wheel
            } else {
                scale += wheel * 0.0001f
            }

            for (panel in panels) {
                panel.x = panel.parseX()
                panel.y = panel.parseY()
            }

        } else if (scrolls) {
            if (ircOpen) {
                IrcClient.scroll += wheel
            } else {
                for (panel in panels) panel.y = panel.parseY(panel.y + wheel / 10)
            }
        }
    }

    // ------------------------------------------------------------------ zenith 绘制

    private fun drawZenith(screenMouseX: Int, screenMouseY: Int) {
        val s = scale.toDouble()
        glScaled(s, s, s)

        val mx = (screenMouseX / scale).roundToInt()
        val my = (screenMouseY / scale).roundToInt()

        ensureZenith()

        val rail = railPanel
        val mods = modulePanel
        val set = settingsPanel

        if (rail == null || mods == null || set == null) {
            glScaled(1.0, 1.0, 1.0)
            return
        }

        rail.updateFade(deltaTime)
        mods.updateFade(deltaTime)
        set.updateFade(deltaTime)

        rail.drawScreenAndClick(mx, my)
        mods.drawScreenAndClick(mx, my)
        set.drawScreenAndClick(mx, my)

        // 设置列内容：还是走 LiquidBounceStyle 的那套控件，只是把面板画在行下方
        val element = settingsElement
        if (element != null) {
            val contentTop = set.y + set.height - 2
            val viewH = (set.height + set.fade - (set.height - 2)).toFloat().coerceAtLeast(1f)

            zenithMaxScroll = (element.settingsHeight - viewH).coerceAtLeast(0f)
            zenithScroll = zenithScroll.coerceIn(0f, zenithMaxScroll)

            element.x = set.x + 2
            element.width = set.width - 4
            // settingsBelow 会把设置面板画在「行下方」，所以把行推到内容区顶部之上
            element.y = (contentTop - element.height - 3 - zenithScroll).roundToInt()
            element.hideRow = true
            element.settingsBelow = true
            // Zenith 的设置列自己管 scissor —— 这里绝不能残留 Classic Best 的 drawerStyle
            // （否则会用 Classic 遗留的 clip 边界裁切）
            element.drawerStyle = false
            element.showSettings = true
            element.forceSettingsWidth(set.width - 4)

            beginScissor(set.x + 1f, contentTop.toFloat(), set.width - 2f, viewH)
            style.drawModuleElementAndClick(mx, my, element, null)
            endScissor()
        }

        if (Mouse.hasWheel()) {
            val wheel = Mouse.getDWheel()
            if (wheel != 0) {
                if (mx in set.x..set.x + set.width && my in set.y..set.y + set.height + set.fade) {
                    zenithScroll = (zenithScroll - wheel / 10f).coerceIn(0f, zenithMaxScroll)
                } else if (mx in mods.x..mods.x + mods.width && my in mods.y..mods.y + mods.height + mods.fade) {
                    mods.handleScroll(mx, my, wheel)
                }
            }
        }

        disableLighting()
        RenderHelper.disableStandardItemLighting()
        glScaled(1.0, 1.0, 1.0)
    }

    /** 三列的位置：分类栏 72 / 模块列表 181 / 设置列 176，间距 4，整体居中 */
    private fun ensureZenith() {
        val resolution = ScaledResolution(mc)
        val availW = (resolution.scaledWidth / scale).roundToInt()
        val availH = (resolution.scaledHeight / scale).roundToInt()

        val totalW = 72 + 4 + 181 + 4 + 176
        zenithX = ((availW - totalW) / 2).coerceAtLeast(2)
        zenithY = ((availH - 216) / 2).coerceAtLeast(2)

        if (railPanel == null) {
            railPanel = Panel(
                "", zenithX, zenithY, 72, 18, true,
                categories.mapIndexed { index, category ->
                    ButtonElement(category.displayName, {
                        if (zenithCat == index) guiColor else Int.MAX_VALUE
                    }) {
                        selectZenithCategory(index)
                    }
                } + ButtonElement("Targets", {
                    if (zenithCat == categories.size) guiColor else Int.MAX_VALUE
                }) {
                    selectZenithCategory(categories.size)
                }
            )
        } else {
            railPanel!!.x = zenithX
            railPanel!!.y = zenithY
        }

        if (modulePanel == null) {
            rebuildModulePanel()
        } else {
            modulePanel!!.x = zenithX + 76
            modulePanel!!.y = zenithY
        }

        if (settingsPanel == null) {
            rebuildSettingsPanel()
        } else {
            settingsPanel!!.x = zenithX + 261
            settingsPanel!!.y = zenithY
        }
    }

    private fun rebuildModulePanel() {
        val isTargets = zenithCat == categories.size
        val title = if (isTargets) "Targets" else categories[zenithCat].displayName
        val elements: List<Element> = if (isTargets) {
            targetButtons()
        } else {
            moduleManager[categories[zenithCat]].map(::ModuleElement)
        }

        modulePanel = Panel(title, zenithX + 76, zenithY, 181, 18, true, elements)
    }

    private fun rebuildSettingsPanel() {
        val module = zenithModule
        settingsElement = if (module == null) {
            null
        } else {
            zenithElements.getOrPut(module) { ModuleElement(module) }
        }

        settingsPanel = Panel(
            module?.name ?: "Settings",
            zenithX + 261, zenithY, 176, 18, true,
            emptyList()
        ).apply {
            // 没有 element，靠固定内容高度把面板撑起来
            minBodyHeight = 216 - 18
        }

        zenithScroll = 0f
        zenithMaxScroll = 0f
    }

    private fun selectZenithCategory(index: Int) {
        if (zenithCat == index) return

        zenithCat = index
        zenithModule = null
        zenithScroll = 0f

        rebuildModulePanel()
        rebuildSettingsPanel()

        style.showSettingsSound()
    }

    private fun selectZenithModule(module: Module) {
        if (zenithModule === module) return

        zenithModule = module
        rebuildSettingsPanel()

        style.clickSound()
    }

    /** 设置列的内容裁切：只留面板内容区，避免超长设置溢出到面板外（Classic Best 抽屉也复用） */
    fun beginScissor(x: Float, y: Float, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val resolution = ScaledResolution(mc)
        val scaleFactor = resolution.scaleFactor
        val factor = scaleFactor.toFloat() * scale
        val fullHeight = (resolution.scaledHeight * scaleFactor).toFloat()

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (x * factor).toInt(),
            (fullHeight - (y + h) * factor).toInt(),
            (w * factor).toInt(),
            (h * factor).toInt()
        )
    }

    fun endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    // ------------------------------------------------------------------ StyleSettings=Menu 面板

    /**
     * StyleSettings=Menu 的统一设置面板（Panel Menu）：
     * 右键任意模块 → 它的设置显示在所属分类面板主列表上方的同一个菜单面板里。
     *
     * - 锚定宿主面板（拖动面板时菜单跟着走），高度不超过宿主面板当前内容高度，
     *   永远罩在主列表范围内、不伸出面板边界（同 Best 抽屉的边界规则）。
     * - 设置内容用与 Zenith 设置列同一套渲染（hideRow + settingsBelow + scissor），
     *   可用滚轮滚动；点击由 [menuHandleMouseClicked] 转发给样式层处理。
     */
    private fun drawMenuPanel(mouseX: Int, mouseY: Int) {
        val element = menuElement ?: return
        val host = menuHostPanel

        // 宿主面板被收起（或消失）→ 菜单一并关闭
        if (host == null || !host.isVisible || !host.open || host.fade <= 0) {
            resetMenu()
            return
        }

        // 打开动画：缓出（0 → 1）
        val remaining = 1F - menuProgress
        menuProgress = if (remaining < 0.02F) 1F else menuProgress + remaining * 0.25F
        val progress = menuProgress

        val titleHeight = 15F
        val menuX = host.x + 1F
        val menuW = (host.width - 2).toFloat()
        val contentTop = host.y + host.height - 2

        // 菜单高度不超过宿主面板当前内容高度（罩在主列表范围内）
        val maxH = (host.fade - 3).coerceAtLeast((titleHeight + 12).toInt()).toFloat()
        // 首帧 settingsHeight 尚未计算 —— 先给个临时高度，画完一帧后自然校正
        val contentH = if (element.settingsHeight > 0) element.settingsHeight.toFloat() else 80F
        val menuH = (titleHeight + contentH + 9F).coerceAtMost(maxH)

        val top = contentTop.toFloat()
        val bottom = top + menuH * progress

        // 背景 + 边框（与 Best 抽屉同一条配色）
        drawRoundedRect(menuX, top, menuX + menuW, bottom, ClickGUI.lbSettingsDrawerColor.rgb, 3F)
        drawRoundedBorder(menuX, top, menuX + menuW, bottom, 1F, ClickGUI.lbPanelBorderColor.rgb, 3F)

        // 标题栏：模块名 + 右上角关闭按钮（hover 变红，同 IRC 面板）
        fontSemibold35.drawString(element.displayName, (menuX + 5F).roundToInt(), (top + 4F).roundToInt(), guiColor)

        val closeSize = 11F
        val closeLeft = menuX + menuW - closeSize - 4F
        val closeTop = top + 2.5F
        menuCloseBounds = floatArrayOf(closeLeft, closeTop, closeLeft + closeSize, closeTop + closeSize)
        menuBounds = floatArrayOf(menuX, top, menuX + menuW, bottom)

        val closeHovered = mouseX >= closeLeft && mouseX <= closeLeft + closeSize &&
                mouseY >= closeTop && mouseY <= closeTop + closeSize
        drawRoundedRect(
            closeLeft, closeTop, closeLeft + closeSize, closeTop + closeSize,
            if (closeHovered) Color(232, 17, 35, 235).rgb else Color(255, 255, 255, 26).rgb, 3F
        )
        fontSemibold35.drawString(
            "x", (closeLeft + 3.5F).roundToInt(), (closeTop + 2F).roundToInt(),
            if (closeHovered) Int.MAX_VALUE else Color(200, 200, 200).rgb
        )

        // 设置内容：与 Zenith 设置列同一套渲染，裁切到菜单内容区
        val viewTop = top + titleHeight + 2F
        val viewH = (bottom - viewTop - 2F).coerceAtLeast(0F)
        if (viewH <= 0F) return

        element.x = (menuX + 2F).roundToInt()
        element.width = (menuW - 4F).roundToInt()
        element.forceSettingsWidth((menuW - 4F).roundToInt())

        menuMaxScroll = (contentH - viewH).coerceAtLeast(0F)
        menuScroll = menuScroll.coerceIn(0F, menuMaxScroll)

        // settingsBelow 把设置画在「行下方 3px」处 —— 把行推到内容区顶部之上
        element.y = (viewTop - ModuleElement.ROW_HEIGHT - 3 - menuScroll).roundToInt()
        element.hideRow = true
        element.settingsBelow = true
        element.drawerStyle = false
        element.showSettings = true

        beginScissor(menuX, viewTop, menuW, viewH)
        style.drawModuleElementAndClick(mouseX, mouseY, element, null)
        endScissor()
    }

    /**
     * StyleSettings=Menu 的点击处理（在原面板逻辑之前拦截）：
     * 1. 菜单已打开 → 关闭按钮 / 设置控件优先，区域内空白不穿透
     * 2. 右键模块行 → 打开/切换统一菜单；再右键同一模块 = 关闭
     * 3. 点到所有面板与菜单之外 → 关菜单（其余交给原逻辑）
     */
    private fun menuHandleMouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        val element = menuElement

        if (element != null) {
            // 关闭按钮
            val close = menuCloseBounds
            if (mouseButton == 0 && close != null && mouseX >= close[0] && mouseX <= close[2] &&
                mouseY >= close[1] && mouseY <= close[3]
            ) {
                closeMenu()
                return true
            }

            // 菜单区域内：设置控件吃掉的返回 true；区域内空白也吃掉（不穿透到下面的列表）
            val bounds = menuBounds
            if (mouseButton == 0 && bounds != null && mouseX >= bounds[0] && mouseX <= bounds[2] &&
                mouseY >= bounds[1] && mouseY <= bounds[3]
            ) {
                style.drawModuleElementAndClick(mouseX, mouseY, element, mouseButton)
                return true
            }
        }

        if (mouseButton == 1) {
            for (panel in panels.reversed()) {
                if (!panel.isVisible) continue
                // 面板头右键 = 原逻辑开关面板，这里直接放行
                if (panel.isHovered(mouseX, mouseY)) return false

                for (e in panel.elements) {
                    if (e is ModuleElement && e.isVisible && e.y <= panel.y + panel.fade &&
                        e.isHovered(mouseX, mouseY)
                    ) {
                        if (e.module === element?.module && panel === menuHostPanel) {
                            closeMenu()
                        } else if (e.module.values.any { it.shouldRender() }) {
                            openMenu(e.module, panel)
                        }
                        return true
                    }
                }
            }

            return false
        }

        if (mouseButton == 0 && element != null) {
            val inMenu = menuBounds?.let {
                mouseX >= it[0] && mouseX <= it[2] && mouseY >= it[1] && mouseY <= it[3]
            } == true
            val inAnyPanel = panels.any { p ->
                p.isVisible && mouseX >= p.x && mouseX <= p.x + p.width &&
                        mouseY >= p.y && mouseY <= p.y + p.height + p.fade
            }
            if (!inMenu && !inAnyPanel) closeMenu()
        }

        return false
    }

    /** 在统一 Menu 里展示 [module] 的设置（锚定 [panel] 的主列表上方） */
    private fun openMenu(module: Module, panel: Panel) {
        menuElement = menuElements.getOrPut(module) { ModuleElement(module) }
        menuHostPanel = panel
        menuScroll = 0F
        menuMaxScroll = 0F
        menuProgress = 0F
        menuBounds = null
        menuCloseBounds = null
        style.showSettingsSound()
    }

    private fun closeMenu() {
        if (menuElement != null) style.clickSound()
        resetMenu()
    }

    private fun resetMenu() {
        menuElement = null
        menuHostPanel = null
        menuBounds = null
        menuCloseBounds = null
        menuScroll = 0F
        menuMaxScroll = 0F
        menuProgress = 0F
    }

    // ------------------------------------------------------------------ 输入

    public override fun mouseClicked(screenMouseX: Int, screenMouseY: Int, mouseButton: Int) {
        // 弹入 / 收起动画期间不响应，坐标还没对齐
        if (closing || animating) return

        if (mode == Mode.ZENITH) {
            zenithMouseClicked(screenMouseX, screenMouseY, mouseButton)
            return
        }

        val fullHeight = ScaledResolution(mc).scaledHeight

        if (mouseButton == 0 && screenMouseX in 8..42 && screenMouseY in fullHeight - 42..fullHeight - 8) {
            mc.displayGuiScreen(GuiHudDesigner())
            return
        }

        // IRC 按钮
        if (mouseButton == 0 && screenMouseX in 41..77 && screenMouseY in fullHeight - 46..fullHeight - 9) {
            ircOpen = !ircOpen
            return
        }

        // IRC 面板的关闭按钮（包围盒是上一帧绘制时留下的）
        if (ircOpen && mouseButton == 0) {
            val ircLocalX = (screenMouseX / scale).roundToInt()
            val ircLocalY = (screenMouseY / scale).roundToInt()
            val bounds = LiquidBounceStyle.ircCloseBounds

            if (bounds != null && ircLocalX >= bounds[0] && ircLocalX <= bounds[2] &&
                ircLocalY >= bounds[1] && ircLocalY <= bounds[3]
            ) {
                ircOpen = false
                return
            }
        }

        // IRC 输入框：点中聚焦，点面板别处失焦
        if (ircOpen && mouseButton == 0) {
            val ircLocalX = (screenMouseX / scale).roundToInt()
            val ircLocalY = (screenMouseY / scale).roundToInt()
            val inputBounds = IrcClient.inputBounds

            // 发送按钮：紧贴文本区右边界（宽度 26，样式那边定的）
            if (inputBounds != null &&
                ircLocalX > inputBounds[2] && ircLocalX <= inputBounds[2] + 29F &&
                ircLocalY >= inputBounds[1] && ircLocalY <= inputBounds[3]
            ) {
                if (IrcClient.draft.isNotBlank()) {
                    IrcClient.send(IrcClient.draft)
                    IrcClient.draft = ""
                }
                return
            }

            if (inputBounds != null && ircLocalX >= inputBounds[0] && ircLocalX <= inputBounds[2] &&
                ircLocalY >= inputBounds[1] && ircLocalY <= inputBounds[3]
            ) {
                if (IrcClient.isLoggedIn) {
                    IrcClient.inputFocused = true
                } else {
                    // 未登录时这块是「登录」按钮 → 打开主界面 auth
                    mc.displayGuiScreen(net.ccbluex.liquidbounce.auth.AuthGuiScreen())
                }
                return
            }

            IrcClient.inputFocused = false
        }

        // IRC 面板打开时，除它自己的控件外一律不响应 ——
        // 否则点击会穿透到下面的模块面板（误开关模块、误拖拽、中键滚动等）
        if (ircOpen) return

        if (mouseButton == 2) {
            autoScrollY = screenMouseY
        }

        mouseX = (screenMouseX / scale).roundToInt()
        mouseY = (screenMouseY / scale).roundToInt()

        // StyleSettings=Menu：统一设置面板的点击（菜单本体 + 右键模块打开/切换/关闭）
        if (menuMode && menuHandleMouseClicked(mouseX, mouseY, mouseButton)) return

        // Handle foremost panel.
        panels.reversed().forEachIndexed { _, panel ->
            if (panel.mouseClicked(mouseX, mouseY, mouseButton)) return

            panel.drag = false

            if (mouseButton == 0 && panel.isHovered(mouseX, mouseY)) {
                panel.x2 = panel.x - mouseX
                panel.y2 = panel.y - mouseY
                panel.drag = true

                // Move dragged panel to top.
                panels.remove(panel)
                panels += panel
                return
            }
        }
    }

    private fun zenithMouseClicked(screenMouseX: Int, screenMouseY: Int, mouseButton: Int) {
        val mx = (screenMouseX / scale).roundToInt()
        val my = (screenMouseY / scale).roundToInt()

        val rail = railPanel ?: return
        val mods = modulePanel ?: return
        val set = settingsPanel ?: return

        // 分类栏
        for (element in rail.elements) {
            if (element is ButtonElement && element.isHovered(mx, my) && element.mouseClicked(mx, my, mouseButton)) {
                return
            }
        }

        // 模块列表 / Targets
        if (mx in mods.x..mods.x + mods.width && my in mods.y..mods.y + mods.height + mods.fade) {
            for (element in mods.elements) {
                if (!element.isHovered(mx, my)) continue

                if (element is ModuleElement) {
                    when (mouseButton) {
                        0 -> {
                            element.module.toggle()
                            style.clickSound()
                        }

                        1 -> if (element.module.values.any { it.shouldRender() }) {
                            selectZenithModule(element.module)
                        }
                    }
                } else if (element is ButtonElement) {
                    element.mouseClicked(mx, my, mouseButton)
                }

                return
            }

            return
        }

        // 设置列：还是样式层自己处理命中
        val element = settingsElement ?: return
        if (mx in set.x..set.x + set.width && my in set.y..set.y + set.height + set.fade) {
            style.drawModuleElementAndClick(mx, my, element, mouseButton)
        }
    }

    public override fun mouseReleased(screenMouseX: Int, screenMouseY: Int, button: Int) {
        if (mode == Mode.ZENITH) return

        mouseX = (screenMouseX / scale).roundToInt()
        mouseY = (screenMouseY / scale).roundToInt()

        if (button == 2) {
            autoScrollY = null
        }

        for (panel in panels) panel.mouseReleased(mouseX, mouseY, button)
    }

    override fun updateScreen() {
        if (mode == Mode.CLASSIC) {
            if (style is SlowlyStyle || style is BlackStyle) {
                for (panel in panels) {
                    for (element in panel.elements) {
                        if (element is ButtonElement) element.hoverTime += if (element.isHovered(mouseX, mouseY)) 1 else -1

                        if (element is ModuleElement) element.slowlyFade += if (element.module.state) 20 else -20
                    }
                }
            }
        }

        super.updateScreen()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (mode == Mode.ZENITH) {
            val chosenText = LiquidBounceStyle.chosenText

            if (keyCode == ClickGUI.keyBind || keyCode == Keyboard.KEY_ESCAPE) {
                if (chosenText != null) {
                    LiquidBounceStyle.chosenText = null
                    return
                }

                closeByKey(keyCode)
                return
            }

            chosenText?.processInput(typedChar, keyCode) { LiquidBounceStyle.moveRGBAIndexBy(it) }
            super.keyTyped(typedChar, keyCode)
            return
        }

        // IRC 输入优先：面板打开且输入框聚焦时，键盘不再触发关闭 GUI 等原有逻辑
        val ircWasFocused = ircOpen && IrcClient.inputFocused
        ircKeyTyped(typedChar, keyCode)
        if (ircWasFocused) return

        // Close ClickGUI by using its key bind.
        if (keyCode == ClickGUI.keyBind || keyCode == Keyboard.KEY_ESCAPE) {
            if (style.chosenText != null) {
                style.chosenText = null
                return
            }

            closeByKey(keyCode)
            return
        }

        style.chosenText?.processInput(typedChar, keyCode) { style.moveRGBAIndexBy(it) }

        super.keyTyped(typedChar, keyCode)
    }

    private fun closeByKey(keyCode: Int) {
        if (keyCode != Keyboard.KEY_ESCAPE) {
            if (ignoreClosing) {
                ignoreClosing = false
                return
            }
        }

        requestClose()
    }

    /** 开始播关闭动画，播完由 drawScreen 真正关掉 */
    fun requestClose() {
        closing = true
    }

    /**
     * IRC 输入：面板打开且输入框聚焦时，键盘归 IRC 用。
     * 别名 irc 指向 IrcClient，避免到处写全限定名。
     */
    private fun ircKeyTyped(typedChar: Char, keyCode: Int) {
        if (ircOpen && IrcClient.inputFocused) {
            // Ctrl+V 粘贴。1.8 的自绘 GUI 不接系统 IME，中文只能靠粘贴进来
            if (keyCode == org.lwjgl.input.Keyboard.KEY_V &&
                (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL) ||
                    org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL))
            ) {
                runCatching {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val text = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    if (!text.isNullOrEmpty()) {
                        // 换行会破坏单行输入框，统一换成空格
                        IrcClient.draft += text.replace('\n', ' ').replace('\r', ' ')
                    }
                }
                return
            }

            when (keyCode) {
                org.lwjgl.input.Keyboard.KEY_RETURN, org.lwjgl.input.Keyboard.KEY_NUMPADENTER -> {
                    if (IrcClient.draft.isNotBlank()) {
                        IrcClient.send(IrcClient.draft)
                        IrcClient.draft = ""
                    }
                    return
                }

                org.lwjgl.input.Keyboard.KEY_ESCAPE -> {
                    IrcClient.inputFocused = false
                    return
                }

                org.lwjgl.input.Keyboard.KEY_BACK -> {
                    if (IrcClient.draft.isNotEmpty()) {
                        IrcClient.draft = IrcClient.draft.dropLast(1)
                    }
                    return
                }
            }

            // 只收可见字符，控制字符（含退格/回车）已经在上面处理掉了
            if (!typedChar.isISOControl()) {
                IrcClient.draft += typedChar
            }
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    // ------------------------------------------------------------------ 生命周期

    override fun initGui() {
        ignoreClosing = true

        // 每次打开都从 0 开始弹
        openValue = 0f
        openVelocity = 0f
        closing = false
        fxScale = 0.90f

        // 进入新布局时重建，避免沿用上一次的分类/模块缓存
        railPanel = null
        modulePanel = null
        settingsPanel = null
        settingsElement = null
        resetMenu()
    }

    override fun onGuiClosed() {
        autoScrollY = null
        closing = false
        openValue = 0f
        openVelocity = 0f
        resetMenu()
        saveConfig(clickGuiConfig)
        Keyboard.enableRepeatEvents(false)
        for (panel in panels) panel.fade = 0
    }

    fun Int.clamp(min: Int, max: Int): Int = this.coerceIn(min, max.coerceAtLeast(0))

    override fun doesGuiPauseGame() = false
}
