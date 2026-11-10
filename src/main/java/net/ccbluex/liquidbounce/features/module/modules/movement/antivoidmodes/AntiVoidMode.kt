/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.antivoidmodes

import net.ccbluex.liquidbounce.event.BlockBBEvent
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

/**
 * AntiVoid 新模式基类（与 NoSlowMode/CriticalsMode 同一套路的分发模型）。
 */
open class AntiVoidMode(val modeName: String) : MinecraftInstance {
    open fun onMove(event: MoveEvent) {}
    open fun onPreMotion(event: MotionEvent) {}
    open fun onStrafe(event: StrafeEvent) {}
    open fun onBlockBB(event: BlockBBEvent) {}
    open fun onPacket(event: PacketEvent) {}
    open fun onJump(event: JumpEvent) {}
    open fun onWorldChange() {}
    open fun onEnable() {}
    open fun onDisable() {}
}
