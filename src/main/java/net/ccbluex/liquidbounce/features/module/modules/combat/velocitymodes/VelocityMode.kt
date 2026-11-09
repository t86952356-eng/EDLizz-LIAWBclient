/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

/**
 * Velocity 新模式基类（分发模型与 SpeedMode/FlyMode 相同）。
 */
open class VelocityMode(val modeName: String) : MinecraftInstance {
    open fun onPacket(event: PacketEvent) {}
    open fun onPreMotion(event: MotionEvent) {}
    open fun onUpdate() {}
    open fun onTick() {}
    open fun onStrafe() {}
    open fun onJump(event: JumpEvent) {}
    open fun onMove(event: MoveEvent) {}
    open fun onMoveInput(event: MovementInputEvent) {}
    open fun onAttack(target: net.minecraft.entity.Entity?) {}
    open fun onRender3D() {}
    open fun onEnable() {}
    open fun onDisable() {}
}
