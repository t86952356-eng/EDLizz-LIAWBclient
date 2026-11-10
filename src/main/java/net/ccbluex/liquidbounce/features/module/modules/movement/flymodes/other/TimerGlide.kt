/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.extensions.airTicks
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.server.S12PacketEntityVelocity

/**
 * Fly「TimerGlide」模式：受伤后计时器爆发滑翔。
 * Timer Boost 起飞前用 TimerBeforeBoost 慢计时，受击瞬间切 TimerSpeed 爆发；
 * 支持 AutoAirDamage（摔落自伤 + onGround 伪装）、JumpDamage（原地起跳自伤）
 * 与 SmartHurt（限时飞行自动关闭）。
 */
object TimerGlide : FlyMode("TimerGlide") {

    /** TimerGlide-TimerSpeed（默认 20，1..100）。 */
    var timerSpeed = 20f

    /** TimerGlide-Speed（默认 0.02，0..1）。 */
    var glideSpeed = 0.02

    /** TimerGlide-NoSpeed（默认关）。 */
    var noSpeed = false

    /** TimerGlide-SmartHurt（默认关）。 */
    var smartHurt = false

    /** TimerGlide-SmartTicks（默认关）。 */
    var smartTicks = false

    /** TimerGlide-FlyTicks（默认 900，0..1600；SmartHurt 时可见）。 */
    var flyTicks = 900

    /** TimerGlide-JumpDamage（默认关）。 */
    var jumpDamage = false

    /** TimerGlide-JumpCounts（默认 4，3..8；JumpDamage 时可见）。 */
    var jumpCounts = 4

    /** TimerGlide-JumpTimer（默认 1，0.1..5；JumpDamage 时可见）。 */
    var jumpTimer = 1f

    /** TimerGlide-CustomMotionY（None/Stable/Multiply/Optimal，默认 None）。 */
    var customMotionY = "None"

    /** TimerGlide-MotionY（默认 -0.01，-0.3..0.3；CustomMotionY != None 时可见）。 */
    var motionSpeed = -0.01f

    /** TimerGlide-AutoToggle（默认关）。 */
    var autoToggle = false

    /** TimerGlide-AutoAirDamage（默认关）。 */
    var autoAirDamage = false

    /** TimerGlide-SpoofMode（Edit/Packet/1.8，默认 Edit）。 */
    var spoofMode = "Edit"

    /** TimerGlide-DamageDistance（默认 3.3，1..5；AutoAirDamage 时可见）。 */
    var damageDistance = 3.3f

    /** TimerGlide-TimerBeforeBoost（默认 1，0.05..1）。 */
    var timerBeforeBoost = 1f

    /** TimerGlide-NoMove（默认关；AutoAirDamage 时可见）。 */
    var stopMovement = false

    private var lastYaw = 0f
    private var lastPitch = 0f
    private var elapsedTicks = 0
    private var jumpCounter = 0
    private var boostDurationTicks = 0
    private var boosting = false
    private var nextSpoof = false
    private val cancelTimer = MSTimer()

    private fun fallDamage(): Boolean {
        val player = mc.thePlayer ?: return false
        return player.fallDistance > damageDistance
    }

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        elapsedTicks = 0
        boosting = false
        lastYaw = player.rotationYaw
        lastPitch = player.rotationPitch
    }

    override fun onDisable() {
        elapsedTicks = 0
        jumpCounter = 0
        boostDurationTicks = 0
        boosting = false
        mc.timer?.timerSpeed = 1f
        nextSpoof = false
    }

    private fun disable() {
        if (autoToggle) {
            Fly.state = false
        } else {
            onDisable()
        }
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventType == EventState.RECEIVE) {
            val packet = event.packet
            if (packet is S12PacketEntityVelocity && autoAirDamage &&
                packet.entityID == player.entityId && !cancelTimer.hasTimePassed(2000)
            ) {
                event.cancelEvent()
            }
            return
        }

        if (event.eventType == EventState.SEND) {
            val packet = event.packet
            if (packet is C03PacketPlayer && packet.rotating &&
                !cancelTimer.hasTimePassed(3000) && autoAirDamage && !boosting && spoofMode != "1.8"
            ) {
                event.cancelEvent()
                if (packet.isMoving) {
                    sendPacket(
                        C04PacketPlayerPosition(packet.x, packet.y, packet.z, packet.onGround),
                        triggerEvent = false
                    )
                }
            }
        }
    }

    override fun onMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventState == EventState.PRE && autoAirDamage && nextSpoof) {
            if (spoofMode == "Edit") {
                event.onGround = true
            } else if (spoofMode == "Packet" || spoofMode == "1.8") {
                sendPacket(
                    C04PacketPlayerPosition(player.posX, player.posY, player.posZ, true),
                    triggerEvent = false
                )
            }

            cancelTimer.reset()
            nextSpoof = false
            mc.timer?.timerSpeed = 0.211f
        }

        if (jumpDamage) {
            if (player.hurtTime <= 0) {
                if (jumpCounter < jumpCounts && !boosting) {
                    if (event.eventState == EventState.PRE) {
                        event.onGround = false
                    }

                    player.rotationYaw = lastYaw
                    player.rotationPitch = lastPitch
                }
            }
        }
    }

    override fun onMove(event: MoveEvent) {
        if (customMotionY == "Optimal" && (!smartHurt || elapsedTicks <= flyTicks && boosting)) {
            event.y = -1.0E-14
        }
    }

    override fun onMoveInput(event: MovementInputEvent) {
        if (jumpDamage && !boosting) {
            event.originalInput.moveStrafe = 0f
            event.originalInput.moveForward = 0f
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (!boosting) {
            if (fallDamage() && autoAirDamage) {
                if (spoofMode != "1.8") {
                    player.motionY *= 0.05
                    player.motionX *= 0.0
                    player.motionZ *= 0.0
                }

                player.fallDistance = 0f
                if (stopMovement) {
                    // setMovement(0, 0, 100)：冻结移动输入
                    player.movementInput.moveForward = 0f
                    player.movementInput.moveStrafe = 0f
                }

                cancelTimer.reset()
                nextSpoof = true
            }

            mc.timer?.timerSpeed = timerBeforeBoost
        }

        if (player.hurtTime > 0 && !boosting) {
            mc.timer?.timerSpeed = timerSpeed
            if (player.onGround) {
                player.motionY = 0.42
            } else if (player.airTicks >= 6) {
                boostDurationTicks = 20 * timerSpeed.toInt()
                boosting = true
            }
        }

        if (jumpDamage && player.hurtTime <= 0 && jumpCounter < jumpCounts) {
            mc.timer?.timerSpeed = jumpTimer
            if (player.onGround) {
                player.tryJump()
                jumpCounter++
            }
        }

        if (!smartHurt || elapsedTicks <= flyTicks && boosting) {
            if (!noSpeed) {
                // setSpeed(speed, false)：沿输入方向（无输入时沿视线）设置固定速度
                setSpeedLoftily(glideSpeed)
            }

            mc.timer?.timerSpeed = timerSpeed

            when (customMotionY) {
                "None" -> player.motionY *= 0.042
                "Stable" -> player.motionY = motionSpeed.toDouble()
                "Multiply" -> player.motionY *= motionSpeed.toDouble()
                "Optimal" -> if (!isMovingInput()) {
                    // setMovement(1, 0, 49)：强制前进输入
                    player.movementInput.moveForward = 1f
                    player.movementInput.moveStrafe = 0f
                }
            }

            if (boostDurationTicks > 0) {
                boostDurationTicks--
            }

            elapsedTicks++
        }

        if (smartHurt && boosting) {
            if (!smartTicks) {
                if (elapsedTicks >= flyTicks) {
                    disable()
                }
            } else if (boostDurationTicks <= 0) {
                disable()
            }
        }
    }

    private fun isMovingInput(): Boolean {
        val p = mc.thePlayer ?: return false
        return p.movementInput.moveForward != 0f || p.movementInput.moveStrafe != 0f
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
