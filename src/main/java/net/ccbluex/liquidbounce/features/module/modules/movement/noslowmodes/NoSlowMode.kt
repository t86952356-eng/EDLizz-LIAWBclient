/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

/**
 * NoSlow 新模式基类（与 SpeedMode/FlyMode 同一套路的分发模型）。
 */
open class NoSlowMode(val modeName: String) : MinecraftInstance {
    open fun onPreMotion(event: MotionEvent) {}
    open fun onPostMotion(event: MotionEvent) {}
    open fun onSlowDown(event: SlowDownEvent) {}
    open fun onStrafe() {}
    open fun onUpdate() {}
    open fun onPacket(event: PacketEvent) {}
    open fun onAttack(target: net.minecraft.entity.Entity?) {}
    open fun onEnable() {}
    open fun onDisable() {}
}
