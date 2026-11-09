/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.matrix

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook

/**
 * Fly「Matrix Damage」模式：受伤后计时器爆发飞行，
 * 可选自伤起跳与关模块收尾包。
 */
object MatrixDamage : FlyMode("Matrix Damage") {

    /** Timer Speed（默认 30，1..100）。 */
    var timerSpeed = 30f

    /** MatrixDamage-Speed（默认 0.07，0..1）。 */
    var flySpeed = 0.07

    /** No Speed（默认关）。 */
    var noSpeed = false

    /** Detect Damage（默认开）。 */
    var detectDamage = true

    /** Auto Disable（默认开）。 */
    var autoDisable = true

    /** Fly Ticks（默认 1000，0..1600；Detect Damage 关闭时可见）。 */
    var flyTicks = 1000

    /** Self Damage（默认开）。 */
    var selfDamage = true

    /** New Self Damage（默认关）。 */
    var newSelfDamage = false

    /** Packet（默认开）。 */
    var packet = true

    /** Motion Y（None / Simple / Multiply，默认 None）。 */
    var motionYMode = "None"

    /** Motion（默认 -0.01，-0.3..0.3）。 */
    var motion = -0.01

    private var flightTicks = 0
    private var selfDamageJumps = 0
    private var speedTicksLeft = 0
    private var damaged = false
    private var startYaw = 0f
    private var startPitch = 0f

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        flightTicks = 0
        selfDamageJumps = 0
        speedTicksLeft = 0
        damaged = false
        startYaw = player.rotationYaw
        startPitch = player.rotationPitch
    }

    override fun onDisable() {
        val player = mc.thePlayer ?: return
        flightTicks = 0
        selfDamageJumps = 0
        speedTicksLeft = 0
        damaged = false
        mc.timer.timerSpeed = 1f
        if (packet) {
            sendPacket(
                C06PacketPlayerPosLook(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, false),
                triggerEvent = false
            )
            sendPacket(C04PacketPlayerPosition(player.posX, player.posY, player.posZ, false), triggerEvent = false)
            sendPacket(
                C06PacketPlayerPosLook(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, false),
                triggerEvent = false
            )
        }
    }

    override fun onMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        if (selfDamage && player.hurtTime <= 0 && selfDamageJumps < 4) {
            event.onGround = false
        }

        if (newSelfDamage && PortedTrackers.trackedFallDistance > 3.0f) {
            event.onGround = true
            player.onGround = true
            player.motionY = 0.0
            PortedTrackers.trackedFallDistance = 0f
        }
    }

    override fun onMoveInput(event: MovementInputEvent) {
        val player = mc.thePlayer ?: return
        if (selfDamage && !damaged && (selfDamageJumps < 4 || !player.onGround)) {
            event.originalInput.sneak = false
            event.originalInput.jump = false
            event.originalInput.moveStrafe = 0f
            event.originalInput.moveForward = 0f
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (player.hurtTime > 0 && !damaged && PortedTrackers.itemUseTicks >= 3) {
            speedTicksLeft = 20 * timerSpeed.toInt()
            damaged = true
        }

        if (selfDamage && player.hurtTime <= 0 && selfDamageJumps < 4 && player.onGround) {
            player.jump()
            selfDamageJumps++
        }

        if (!detectDamage || flightTicks <= flyTicks && damaged) {
            val speed = if (speedTicksLeft > 0) flySpeed else 0.03
            if (!noSpeed) {
                PortedMove.strafe(speed)
            }

            mc.timer.timerSpeed = timerSpeed

            when (motionYMode) {
                "None" -> player.motionY *= 0.039
                "Simple" -> Unit
                "Multiply" -> player.motionY *= motion
            }

            if (speedTicksLeft > 0) {
                speedTicksLeft--
            }

            flightTicks++
        }

        if (detectDamage && damaged) {
            if (!autoDisable) {
                if (flightTicks >= flyTicks) {
                    Fly.state = false
                }
            } else if (speedTicksLeft <= 0) {
                Fly.state = false
            }
        }
    }
}
