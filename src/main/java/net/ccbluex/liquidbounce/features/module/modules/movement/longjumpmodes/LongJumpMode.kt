/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance

open class LongJumpMode(val modeName: String) : MinecraftInstance {
    open fun onUpdate() {}
    open fun onMove(event: MoveEvent) {}
    open fun onJump(event: JumpEvent) {}

    /** PRE 阶段移动包（对应原实现的 PreMotionEvent）。 */
    open fun onPreMotion(event: MotionEvent) {}

    /** 移动 tick（对应原实现的 Strafe/PostStrafe 家族事件）。 */
    open fun onStrafe() {}

    /** 出站/入站包（对应原实现的 PacketSend/PacketReceive）。 */
    open fun onPacket(event: PacketEvent) {}

    open fun onEnable() {}
    open fun onDisable() {}
}