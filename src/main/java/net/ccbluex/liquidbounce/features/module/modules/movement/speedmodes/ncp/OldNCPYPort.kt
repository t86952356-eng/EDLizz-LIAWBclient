/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.ncp

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import kotlin.math.sqrt

/**
 * Speed「Old NCP Y-port」模式：经典 YPort 阶段机（水平距离差分 + 0.4 提升）。
 */
object OldNCPYPort : SpeedMode("Old NCP Y-port") {

    /** Smooth Camera（默认关）。 */
    var smoothCamera = false

    private var speed = 0.0
    private var stage = 2
    private var lastHorizontalDistance = 0.0

    override fun onEnable() {
        stage = 2
        speed = 0.2873
        lastHorizontalDistance = 0.0
    }

    override fun onDisable() {
        speed = 0.0
    }

    override fun onPreMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        val dx = player.posX - player.lastTickPosX
        val dz = player.posZ - player.lastTickPosZ
        lastHorizontalDistance = sqrt(dx * dx + dz * dz)
        if (stage == 3) {
            event.y = event.y + 0.4
        }
    }

    override fun onMoveInput(event: MovementInputEvent) {
        event.originalInput.jump = false
    }

    override fun onStrafeEvent(event: StrafeEvent) {
        val player = mc.thePlayer ?: return

        when (stage) {
            2 -> {
                speed *= 2.14
                stage = 3
            }
            3 -> {
                stage = 2
                val d0 = 0.66 * (lastHorizontalDistance - 0.2873)
                speed = lastHorizontalDistance - d0
            }
            else -> {
                val collided = mc.theWorld.getCollidingBoundingBoxes(
                    player, player.entityBoundingBox.offset(0.0, player.motionY, 0.0)
                ).isNotEmpty()
                if (collided || player.isCollidedVertically) {
                    stage = 1
                }
                speed = lastHorizontalDistance - lastHorizontalDistance / 159.0
            }
        }

        if (player.isCollidedHorizontally) {
            speed = 0.2873
        }

        if (PortedTrackers.itemUseTicks == 1 && PortedTrackers.ticksSinceTeleport > 2) {
            speed = 0.3873
        }

        if (!player.onGround) {
            stage++
        }

        // 原实现 setSpeed：斜向 0.98 系数 + 先清零再按输入方向设置
        event.cancelEvent()
        val diagonal = player.moveForward != 0f && player.moveStrafing != 0f
        if (diagonal) {
            PortedMove.strafe(speed * 0.98)
        } else {
            PortedMove.strafe(speed)
        }
        PortedMove.preventDiagonalSpeed()
    }

    override fun onJump(event: JumpEvent) {
        event.motion = 0.4f
    }
}
