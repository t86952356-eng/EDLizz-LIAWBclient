/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.criticalsmodes

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

/**
 * Criticals 新模式基类（与 NoSlowMode/VelocityMode 同一套路的分发模型）。
 */
open class CriticalsMode(val modeName: String) : MinecraftInstance {
    open fun onPreMotion(event: MotionEvent) {}
    open fun onAttack(event: AttackEvent) {}
    open fun onJump(event: JumpEvent) {}
    open fun onMove(event: MoveEvent) {}
    open fun onEnable() {}
    open fun onDisable() {}
}
