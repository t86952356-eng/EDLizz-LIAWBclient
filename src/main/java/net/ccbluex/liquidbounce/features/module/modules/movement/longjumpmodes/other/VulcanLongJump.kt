/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.LongJumpMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.network.play.server.S08PacketPlayerPosLook

/**
 * LongJump「Vulcan」模式：
 * 按 tick 脚本化的运动序列（跳跃/加速/垂直拉扯），在特定 motionY 窗口
 * 强制落地并再次起跳。
 * 逻辑按原版 1.8.9 实现等价移植（原实现中 fallDistance-2.2 的符号计算为
 * 死代码，无副作用，未移植；S08 拦截分支中的 ignore 标志原实现从不置真，保持原样）。
 */
object VulcanLongJump : LongJumpMode("Vulcan") {

    private var ignore = false
    private var ticks = 0

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        if (!player.onGround) {
            LongJump.toggle()
        }
        ignore = false
        ticks = 0
    }

    override fun onDisable() {}

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        ticks++

        when (ticks) {
            1 -> player.jump()

            4 -> {
                player.motionX *= 1.3
                player.motionZ *= 1.3
            }

            5 -> {
                player.motionX *= 1.05
                player.motionZ *= 1.05
            }

            10 -> player.motionY = 2.0

            11 -> player.motionY += 0.04

            40 -> {
                chat(player.motionY.toString())
                player.motionY = -0.09800000190735147
            }

            62 -> {
                chat(player.motionY.toString())
                event.onGround = true
                player.onGround = true
                player.jump()
            }
        }

        if (player.motionY < -1.53 && player.motionY > -1.54) {
            event.onGround = true
            player.onGround = true
            player.jump()
        }

        if (player.motionY < -0.51 && player.motionY > -0.52) {
            chat("if near void and the ghost block handler is on this may flag")
            player.motionY = 2.0
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val packet = event.packet
        if (packet is S08PacketPlayerPosLook && ignore) {
            event.cancelEvent()
            ignore = false
        }
    }
}
