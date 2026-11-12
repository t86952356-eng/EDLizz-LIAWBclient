/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.aac.AAC
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.aac.LAAC
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave.Intave14
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave.IntaveNew
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave.IntaveOld
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.Matrix
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.MatrixNewNoWebMode
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.None
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.OldGrim
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.Rewi

object NoWeb : Module("NoWeb", Category.MOVEMENT) {

    private val noWebModes = arrayOf(
        // Vanilla
        None,

        // AAC
        AAC, LAAC,

        // Intave
        IntaveOld,
        IntaveNew,
        Intave14,

        // Other
        Rewi,
        OldGrim,

        // 新增模式
        Matrix, MatrixNewNoWebMode
    )

    private val modes = noWebModes.map { it.modeName }.toTypedArray()

    val mode by choices(
        "Mode", modes, "None"
    )

    // ---- MatrixNew（NoWeb）选项 ----
    private val matrixNoWebThreshold by int("MatrixNew-ThresholdTicks", 4, 0..20) { mode == "MatrixNew" }
    private val matrixShowThresholdTicks by boolean("MatrixNew-ShowThresholdTicks", false) { mode == "MatrixNew" }
    private val matrixKeepY by boolean("KeepY", false) { mode == "MatrixNew" }

    private fun syncNoWebModeSettings() {
        MatrixNewNoWebMode.noWebThreshold = matrixNoWebThreshold
        MatrixNewNoWebMode.showThresholdTicks = matrixShowThresholdTicks
        MatrixNewNoWebMode.keepY = matrixKeepY
    }

    override fun onEnable() {
        modeModule.onEnable()
    }

    override fun onDisable() {
        modeModule.onDisable()
    }

    val onUpdate = handler<UpdateEvent> {
        syncNoWebModeSettings()
        modeModule.onUpdate()
    }

    override val tag
        get() = mode

    private val modeModule
        get() = noWebModes.find { it.modeName == mode }!!
}
