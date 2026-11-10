/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.matrix

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import kotlin.math.max

/**
 * Speed「MatrixHighSpeed2」模式（源 MatrixHigh2）：
 * 地面起跳进入 boost 段；下落越过 DownMotion 且脚下有碰撞箱时
 * 伪装 onGround + 贴地起跳（阶梯式滞空加速），Inf 关闭时仅 boost 段生效。
 */
object MatrixHighSpeed2 : SpeedMode("MatrixHighSpeed2") {

    /** H（默认 0.2，0..0.5）：贴地起跳后的水平速度。 */
    var h = 0.2

    /** DownMotion（默认 0.3，-0.5..0.5）：触发贴地伪装的下落速度阈值。 */
    var downMotion = 0.3

    /** Inf（默认关）：boost 段之外也持续生效。 */
    var inf = false

    private var boost = false

    override fun onEnable() {
        boost = false
    }

    override fun onDisable() {
        boost = false
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (player.onGround) {
            // setSpeed(max(0.15, getSpeed()), true)：仅在移动时设置
            PortedMove.strafe(max(0.15, PortedMove.speed()))
            player.tryJump()
            boost = true
        } else if (PortedMove.speed() <= 0.2) {
            PortedMove.strafe(max(PortedMove.speed(), 0.2))
        }

        if (player.motionY < downMotion && !player.onGround && !player.isCollidedHorizontally &&
            (boost || inf)
        ) {
            // 脚下（向下 1 格、扩 0.2）没有碰撞箱则跳过
            if (!hasGroundBelow()) return

            // setSpeed(max(getSpeed(), H), false)：无移动输入也沿视线设置
            setSpeedLoftily(max(PortedMove.speed(), h))
            player.onGround = true
            player.tryJump()
            PortedMove.strafe()
            boost = false
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        if (player.hurtTime >= 8) {
            boost = false
        }

        if (event.eventState == EventState.PRE &&
            player.motionY < downMotion && !player.onGround && !player.isCollidedHorizontally &&
            (boost || inf)
        ) {
            if (!hasGroundBelow()) return

            event.onGround = true
            PortedMove.strafe()
        }
    }

    private fun hasGroundBelow(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false
        val box = player.entityBoundingBox.expand(0.2, 0.2, 0.2).offset(0.0, -1.0, 0.0)
        return world.getCollidingBoundingBoxes(player, box).isNotEmpty()
    }

    /** 源 setSpeed(speed, false)：无 movingCheck —— 即使没有输入也沿视线方向设置速度。 */
    private fun setSpeedLoftily(speed: Double) {
        val p = mc.thePlayer ?: return
        val input = p.movementInput
        val moveForward = input.moveForward.toDouble()
        val moveStrafe = input.moveStrafe.toDouble()
        val yaw = if (moveForward == 0.0 && moveStrafe == 0.0) {
            Math.toRadians(p.rotationYaw.toDouble())
        } else {
            Math.atan2(-moveStrafe, moveForward) + Math.toRadians(p.rotationYaw.toDouble())
        }
        p.motionX = -Math.sin(yaw) * speed
        p.motionZ = Math.cos(yaw) * speed
    }
}
