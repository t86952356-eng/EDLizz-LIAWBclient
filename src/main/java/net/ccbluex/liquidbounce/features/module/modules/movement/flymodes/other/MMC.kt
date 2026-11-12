/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import kotlin.math.roundToInt

/**
 * Fly「MMC」模式 —— MMC 穿地起飞：
 * 启用后第 1 个落地 tick（且脚下方 2.5 格处是完整实体方块）→ timer 拉到 0.1、
 * 连发三个 C04（当前位置 onGround / 下方 2.5 格 roundToGround 处 / 回到当前位置），
 * 随后跳跃 + ~7.0 strafe 冲刺；服务器随后的 S08 传送取消一次（保持穿地状态）。
 * StrafeEvent 每帧 strafe() 维持速度。
 *
 * 移植说明：源 isFullBlock() → Block.isFullCube()；源的 PacketQueueComponent.cR
 * 开关在 Lizz 无对应组件（客户端自有出站包队列），略过。
 */
object MMC : FlyMode("MMC") {

    private var clipped = false
    private var ticks = 0

    override fun onEnable() {
        clipped = false
        ticks = 0
    }

    override fun onDisable() {
        PortedMove.stop()
    }

    override fun onMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        ticks++

        if (!player.onGround) return

        PortedMove.stop()

        if (ticks == 1 && PortedMove.blockAt(0.0, -2.5, 0.0)?.isFullCube == true) {
            mc.timer.timerSpeed = 0.1f

            sendPacket(
                C04PacketPlayerPosition(player.posX, player.posY, player.posZ, true),
                triggerEvent = false
            )
            sendPacket(
                C04PacketPlayerPosition(
                    player.posX,
                    roundToGround(player.posY - (2.5 - Math.random() / 100.0)),
                    player.posZ,
                    false
                ),
                triggerEvent = false
            )
            sendPacket(
                C04PacketPlayerPosition(player.posX, player.posY, player.posZ, false),
                triggerEvent = false
            )

            clipped = true
            player.jump()
            PortedMove.strafe(7.0 - Math.random() / 10.0)
        }
    }

    override fun onStrafe(event: StrafeEvent) {
        PortedMove.strafe()
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return

        // 源挂在 TeleportEvent（S08 到达）：穿地后取消第一次服务端拉回
        if (clipped && event.packet is S08PacketPlayerPosLook) {
            event.cancelEvent()
            clipped = false
        }
    }

    private fun roundToGround(y: Double) = (y / 0.015625).roundToInt() * 0.015625
}
