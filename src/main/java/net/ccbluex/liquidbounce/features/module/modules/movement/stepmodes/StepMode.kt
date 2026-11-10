/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.stepmodes

import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.StepEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

/**
 * Step 新模式基类（与 NoSlowMode/CriticalsMode 同一套路的分发模型）。
 */
open class StepMode(val modeName: String) : MinecraftInstance {
    open fun onPreMotion(event: MotionEvent) {}
    open fun onStep(event: StepEvent) {}
    open fun onTick() {}
    open fun onEnable() {}
    open fun onDisable() {}
}
