/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.matrix

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.util.MathHelper

/**
 * Speed「MatrixNew」模式（原 Matrix，改名避让已有 Matrix）：
 * 地面起跳 + strafe；空中低速时可选补速；下落时微压 motionY（-0.0033）。
 * 疾跑跳跃的助推被旋转到移动方向（源的 setMovementYaw）。
 */
object MatrixNewSpeed : SpeedMode("MatrixNew") {

    /** Strafe（默认关）。 */
    var strafe = false

    override fun onEnable() {
        mc.thePlayer?.jumpMovementFactor = 0.02f
        mc.thePlayer?.speedInAir = 0.02f
    }

    override fun onDisable() {
        mc.thePlayer?.jumpMovementFactor = 0.02f
        mc.thePlayer?.speedInAir = 0.02f
    }

    override fun onJump(event: JumpEvent) {
        val player = mc.thePlayer ?: return
        if (!player.isSprinting) return

        // 源：event.setMovementYaw(toDegrees(getDirection()))——疾跑跳跃助推沿移动方向。
        // Lizz 的 JumpEvent 没有移动朝向字段，POST 阶段把沿视线的助推撤销、
        // 改沿移动方向重新施加（净效果与源一致）。
        if (event.eventState == EventState.POST) {
            val yawRad = Math.toRadians(player.rotationYaw.toDouble())
            player.motionX += MathHelper.sin(yawRad.toFloat()) * 0.2f
            player.motionZ -= MathHelper.cos(yawRad.toFloat()) * 0.2f

            val dir = PortedMove.direction()
            player.motionX -= MathHelper.sin(dir.toFloat()) * 0.2f
            player.motionZ += MathHelper.cos(dir.toFloat()) * 0.2f
        }
    }

    override fun onStrafeEvent(event: StrafeEvent) {
        val player = mc.thePlayer ?: return
        if (!PortedMove.isMoving()) return

        if (player.onGround) {
            player.tryJump()
            // strafe() = setSpeed(getSpeed(), true)：沿移动方向重设当前速度
            PortedMove.strafe()
        }

        if (PortedMove.speed() <= 0.2 && !player.isCollidedHorizontally && !player.onGround && strafe) {
            PortedMove.strafe(PortedMove.speed())
        }

        if (player.hurtTime <= 0) {
            player.motionY -= 0.0033
        }
    }
}
