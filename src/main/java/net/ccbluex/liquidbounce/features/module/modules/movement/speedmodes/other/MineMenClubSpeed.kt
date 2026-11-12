/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Speed「MineMenClub」模式 —— 源 MineMenClubSpeed 等价移植：
 * 收到击退超过 8 tick 后——地面 / 朝向夹角 <90° 时 strafe()，
 * 否则（allow more strafing 开启时）以 0.7× 当前速度 strafe。
 * PreMotion：落地即 jump()（源同函数内的 roundToGround 探测为死代码）。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.MathHelper
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.cos

object MineMenClubSpeed : SpeedMode("MineMenClub") {

    /** allow more strafing（默认开）。 */
    var allowMoreStrafing = true

    private var jumpCount = 0

    /** 源的死状态字段（velocityX/Z 捕获后未参与任何逻辑）——按源保留。 */
    private var velocityX = 0.0
    private var velocityZ = 0.0

    override fun onEnable() {
        velocityX = 0.0
        velocityZ = 0.0
    }

    override fun onJump(event: JumpEvent) {
        jumpCount++
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != net.ccbluex.liquidbounce.event.EventState.RECEIVE) return
        val player = mc.thePlayer ?: return

        val packet = event.packet
        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
            velocityX = packet.motionX / 8000.0
            velocityZ = packet.motionZ / 8000.0
        }
    }

    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return

        val d0 = MathHelper.wrapAngleTo180_double(Math.toDegrees(computedDirection()))
        val d1 = MathHelper.wrapAngleTo180_double(
            Math.toDegrees(atan2(player.motionZ, player.motionX)) - 90.0
        )

        if (PortedTrackers.ticksSinceVelocity > 8) {
            if (player.onGround) {
                strafeCurrent()
            }

            if (angleDifference(d0, d1) < 90.0) {
                strafeCurrent()
            } else if (allowMoreStrafing) {
                strafe(currentSpeed() * 0.7)
            }
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        if (player.onGround) {
            player.jump()
        }
    }

    // ---- 与源 MoveUtil / MathUtil 相同语义的本地实现 ----

    private fun strafe(speed: Double = currentSpeed()) {
        val player = mc.thePlayer ?: return
        val dir = computedDirection()
        player.motionX = -sin(dir) * speed
        player.motionZ = cos(dir) * speed
    }

    private fun strafeCurrent() = strafe()

    private fun currentSpeed(): Double {
        val player = mc.thePlayer ?: return 0.0
        return kotlin.math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)
    }

    private fun computedDirection(): Double {
        val player = mc.thePlayer ?: return 0.0
        var yaw = player.rotationYaw
        if (player.movementInput.moveForward < 0f) yaw += 180f
        var forward = 1f
        if (player.movementInput.moveForward < 0f) forward = -0.5f else if (player.movementInput.moveForward > 0f) forward = 0.5f
        if (player.movementInput.moveStrafe > 0f) yaw -= 90f * forward
        if (player.movementInput.moveStrafe < 0f) yaw += 90f * forward
        return Math.toRadians(yaw.toDouble())
    }

    /** 源 MathUtil.n：角度差（含 360 环绕三路最小值）。 */
    private fun angleDifference(a: Double, b: Double): Double =
        kotlin.math.min(abs(a - b), kotlin.math.min(abs(a - 360.0) - abs(b), abs(b - 360.0) - abs(a)))

    /** 源 simulateStrafe：按输入方向以「最大 45° 步进」平滑的目标朝向写入 MoveEvent。 */
    fun simulateStrafe(event: MoveEvent, speed: Double, forward: Float, strafe: Float, baseYaw: Float) {
        if (forward == 0f && strafe == 0f) return

        var yaw = baseYaw
        val backward = forward < 0f
        val turn = 90f * (if (forward > 0f) 0.5f else if (backward) -0.5f else 1f)

        if (backward) yaw += 180f
        if (strafe > 0f) yaw -= turn else if (strafe < 0f) yaw += turn

        yaw = (yaw + 360f) % 360f

        var delta = yaw - strafeAngle
        delta = (delta + 180f) % 360f - 180f
        strafeAngle = if (abs(delta) < 45f) yaw else strafeAngle + sign(delta) * 45f
        strafeAngle = (strafeAngle + 360f) % 360f

        event.x = cos(Math.toRadians(strafeAngle + 90.0)) * speed
        event.z = cos(Math.toRadians(strafeAngle.toDouble())) * speed
    }

    private var strafeAngle = 0f
}
