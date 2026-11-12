/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Speed「MatrixLates」模式 —— 源客户端的 Matrix3：
 * 地面 strafe 当前速度（可 AutoJump），空中撞墙限速 0.2，
 * 未受伤时逐 tick 下沉 0.0032。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.matrix

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.setSpeed
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.speed
import kotlin.math.pow
import kotlin.math.sqrt

object MatrixLates : SpeedMode("MatrixLates") {

    /** AutoJump（源 Speed.intaveNewAutoJump，与 IntaveNew 共用，由 Speed 同步）。 */
    var autoJump = true

    override fun onJump(event: JumpEvent) {
        if (!mc.thePlayer.isSprinting) return
    }

    override fun onStrafe() {
        val player = mc.thePlayer ?: return

        if (player.isMoving) {
            if (player.onGround) {
                MovementUtils.strafe(speed)
                if (autoJump) player.tryJump()
            }
            val speed = sqrt(player.motionX.pow(2) + player.motionZ.pow(2))
            if (speed > 0.2 && player.isCollidedHorizontally) {
                setSpeed(0.2, false)
            }
            if (player.hurtTime <= 0) {
                player.motionY -= 0.0032
            }
        }
    }
}
