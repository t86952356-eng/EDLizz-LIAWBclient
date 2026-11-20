/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.other

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.longjumpmodes.LongJumpMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.potion.Potion
import kotlin.math.max
import kotlin.math.sqrt

/**
 * LongJump「WatchdogNCP-New」模式（整体移植 Rewound 客户端 RewoundLongJumpMode）：
 * 需要速度药水；起跳后摩擦链助推（offGroundTicks==1 时 0.66 折损 × 2.149/2.2 增益，
 * 之后按 motionY 切换 159/25 摩擦分母），落地自动关闭。
 *
 * 与源的已记录差异：
 * - 源逻辑挂在 MoveFlyingEvent(post)（输入→motion 应用之后、moveEntity 之前），
 *   Lizz 的等价挂钩是 MoveEvent（moveEntity HEAD，同样在 moveFlying 之后）；
 *   因为该事件在构造时已捕获 motion 参数，模式在写入 player.motionX/Z/Y 的同时
 *   也把 event.x/y/z 同步覆盖（等价结果）；
 * - lastDistance/offGroundTicks 由模式在 PRE motion 自行跟踪（源为 EntityPlayer
 *   补丁字段，语义 = 上一 tick 的水平位移 / 距离落地的 tick 数）；
 * - 提示用 chat（源为弹窗 Notification）。
 */
object WatchdogNCPNewLongJump : LongJumpMode("WatchdogNCP-New") {

    private var speed = 0.0
    private var stage = 0

    /** 上一 tick 的水平位移（源的 lastDistance）。 */
    private var lastDistance = 0.0

    /** 距离落地的 tick 数（源的 offGroundTicks）。 */
    private var offGroundTicks = 0

    override fun onEnable() {
        stage = 0
        speed = 0.0
        lastDistance = 0.0
        offGroundTicks = 0
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        // 对应源 EntityPlayer.onUpdate 末尾的补丁计算（本 tick 结算，下一 tick 读取）
        lastDistance = sqrt(
            (player.posX - player.prevPosX) * (player.posX - player.prevPosX) +
                (player.posZ - player.prevPosZ) * (player.posZ - player.prevPosZ)
        )
        offGroundTicks = if (player.onGround) 0 else offGroundTicks + 1
    }

    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return

        val baseSpeed = baseSpeed()
        val initialSpeed = (baseSpeed * 1.38 - 0.01) / 1.6

        when (stage) {
            0 -> {
                if (!player.isPotionActive(Potion.moveSpeed)) {
                    LongJump.state = false
                    chat("This feature requires you to have the Speed potion effect.")
                    return
                }
                if (player.onGround) stage = 1
            }

            1 -> {
                speed = initialSpeed
                stage = 2
            }

            2 -> {
                player.motionY = jumpMotion(player)
                event.y = player.motionY
                speed *= 2.149
                stage = 3
            }

            3 -> {
                if (player.onGround) {
                    LongJump.state = false
                    return
                }
                if (offGroundTicks == 1) {
                    speed = lastDistance - (lastDistance - baseSpeed) * 0.66
                    val boost = if (player.isPotionActive(Potion.jump)) {
                        2.2 - (player.getActivePotionEffect(Potion.jump).amplifier + 1) * 0.1
                    } else {
                        2.2
                    }
                    speed *= boost
                } else {
                    val friction = if (player.motionY < -0.1) 25.0 else 159.0
                    speed = lastDistance - lastDistance / friction
                }
            }
        }

        if (stage >= 1) {
            speed = max(speed, baseSpeed)
            // 源 MovementUtil.setSpeed：无输入时清零，否则按移动方向覆写
            PortedMove.strafe(speed)
            event.x = player.motionX
            event.z = player.motionZ
        }
    }

    /** 源 MovementUtil.getSpeedWithPotion(0.2873, 0.2)。 */
    private fun baseSpeed(): Double {
        val player = mc.thePlayer ?: return 0.2873
        var base = 0.2873
        if (player.isPotionActive(Potion.moveSpeed)) {
            base *= 1.0 + 0.2 * (player.getActivePotionEffect(Potion.moveSpeed).amplifier + 1)
        }
        return base
    }

    /** 源 MovementUtil.getJumpMotion(0.42)：跳跃药水每级 +0.1。 */
    private fun jumpMotion(player: net.minecraft.client.entity.EntityPlayerSP): Double {
        var base = 0.42
        if (player.isPotionActive(Potion.jump)) {
            base += (player.getActivePotionEffect(Potion.jump).amplifier + 1) * 0.1f
        }
        return base
    }
}
