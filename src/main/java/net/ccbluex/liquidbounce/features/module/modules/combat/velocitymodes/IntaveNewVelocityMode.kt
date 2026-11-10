/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.client.settings.GameSettings
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.MathHelper
import kotlin.math.cos
import kotlin.math.sin

/**
 * Velocity「IntaveNew」模式（原 Intave）：
 * 吃掉自己的正向击退包（只在受击状态机内放行），
 * 受击期间按 MoveFix 模式（GameSettings 按键 / Strafe 前进）修正移动，
 * 攻击瞬间取消疾跑并衰减水平速度。
 */
object IntaveNewVelocityMode : VelocityMode("IntaveNew") {

    /** OnlyOnce（默认关）。 */
    var onlyOnce = false

    /** HurtTime-Min（默认 3，1..10）。 */
    var hurtTimeMin = 3

    /** HurtTime-Max（默认 10，1..10）。 */
    var hurtTimeMax = 10

    /** Intave-OnlySprint（默认关）。 */
    var onlySprint = false

    /** IntaveMoveFixMode（None/GameSettings/Strafe，默认 None）。 */
    var intaveMoveFixMode = "None"

    private var canMove = false
    private var receivedVelocity = false

    override fun onEnable() {
        receivedVelocity = false
        canMove = false
    }

    override fun onDisable() {
        receivedVelocity = false
        canMove = false
        resetKeys()
    }

    private fun resetKeys() {
        if (mc.currentScreen != null) {
            mc.gameSettings.keyBindForward.pressed = false
            mc.gameSettings.keyBindBack.pressed = false
            mc.gameSettings.keyBindLeft.pressed = false
            mc.gameSettings.keyBindRight.pressed = false
        } else {
            mc.gameSettings.keyBindForward.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
            mc.gameSettings.keyBindBack.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindBack)
            mc.gameSettings.keyBindLeft.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)
            mc.gameSettings.keyBindRight.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindRight)
        }
    }

    private fun computeMovementKeys() {
        val player = mc.thePlayer ?: return
        if (mc.currentScreen != null) return

        val direction = if (RotationUtils.currentRotation == null) {
            // toRotation(position + (-motionX, 0, -motionZ))：看向击退反方向
            RotationUtils.toRotation(
                net.minecraft.util.Vec3(
                    player.posX - player.motionX,
                    player.posY,
                    player.posZ - player.motionZ
                )
            ).yaw
        } else {
            RotationUtils.currentRotation!!.yaw
        }

        val deltaYaw = MathHelper.wrapAngleTo180_float(direction - player.rotationYaw)
        resetKeys()
        val radian = Math.toRadians(deltaYaw.toDouble())
        val x = sin(radian)
        val z = cos(radian)

        if (z > 0.5) {
            mc.gameSettings.keyBindForward.pressed = true
        } else if (z < -0.5) {
            mc.gameSettings.keyBindBack.pressed = true
        }

        if (x > 0.5) {
            mc.gameSettings.keyBindRight.pressed = true
        } else if (x < -0.5) {
            mc.gameSettings.keyBindLeft.pressed = true
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (player.hurtTime <= 0) {
            receivedVelocity = false
            canMove = false
        }

        if (receivedVelocity ||
            player.hurtTime >= hurtTimeMin && player.hurtTime <= hurtTimeMax && canMove
        ) {
            when (intaveMoveFixMode) {
                "GameSettings" -> computeMovementKeys()

                "Strafe" -> {
                    if (RotationUtils.currentRotation != null) {
                        mc.gameSettings.keyBindForward.pressed = true
                        // 源在此把 RotationHandler.moveFix 置 STRICT；
                        // Lizz 里 currentRotation 激活时输入修正由 Mixin 自动完成（等效 MoveFix）
                    }
                }
            }
        } else {
            resetKeys()
        }
    }

    override fun onAttack(target: net.minecraft.entity.Entity?) {
        val player = mc.thePlayer ?: return

        if ((receivedVelocity ||
                    player.hurtTime >= hurtTimeMin && player.hurtTime <= hurtTimeMax && canMove && !onlyOnce) &&
            (!onlySprint || player.isSprinting)
        ) {
            player.isSprinting = false
            player.motionX *= 0.6
            player.motionZ *= 0.6
            receivedVelocity = false
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        if (event.isCancelled) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is S12PacketEntityVelocity) {
            if (packet.motionY < 0) {
                return
            }

            if (packet.entityID == player.entityId) {
                // 与源一致：不取消包，只标记受击状态
                receivedVelocity = true
                canMove = true
            }
        }
    }
}
