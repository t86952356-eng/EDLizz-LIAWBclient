/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.inventorymovemodes

import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

/**
 * InventoryMove 新模式基类（与 NoSlowMode/CriticalsMode 同一套路的分发模型）。
 */
open class InventoryMoveMode(val modeName: String) : MinecraftInstance {
    open fun onUpdate() {}
    open fun onPreMotion(event: MotionEvent) {}
    open fun onPacketSend(event: PacketEvent) {}
    open fun onMoveInput(event: MovementInputEvent) {}
    open fun onMove(event: MoveEvent) {}
    open fun onEnable() {}
    open fun onDisable() {}
}
