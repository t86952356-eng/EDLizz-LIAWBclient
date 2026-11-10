/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.antivoidmodes

import net.ccbluex.liquidbounce.event.BlockBBEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.minecraft.block.BlockAir
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MathHelper

/**
 * AntiVoid「Vulcan」模式：
 * 下落超过 Distance 且脚下 10 格内无地面时进入「虚空下落」状态：在下方铺假空气地板、
 * 固定 0.1 速度、偶数 tick 反向推动、跳跃力度清零；期间临时关闭 Speed，落地后恢复。
 * 逻辑按原版 1.8.9 实现等价移植。
 *
 * StrafeEvent.setForward 在 Lizz 无对应 API：取消事件后按原版 moveFlying 公式
 * 以覆盖的 forward 重新施加移动（与 Vulcan Phase 相同映射）。
 */
object VulcanAntiVoid : AntiVoidMode("Vulcan") {

    /** Distance（默认 2.6，0..10）。 */
    var distance = 2.6

    private var voidFalling = false
    private var toggledSpeed = false

    /** 脚下 10 格内是否存在碰撞（原 PlayerUtil.vh）。 */
    private fun groundBelow10(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false
        val box = player.entityBoundingBox.offset(0.0, -10.0, 0.0)
        return world.getCollidingBoundingBoxes(player, box).isNotEmpty()
    }

    override fun onEnable() {
        voidFalling = false
        toggledSpeed = false
    }

    override fun onDisable() {
        // 安全兜底：模块关闭时恢复被临时关闭的 Speed
        if (toggledSpeed && !Speed.handleEvents()) {
            Speed.state = true
        }
        toggledSpeed = false
        voidFalling = false
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        if (player.fallDistance > distance && !groundBelow10()) {
            voidFalling = true
        }

        if (Fly.handleEvents() || LongJump.handleEvents()) {
            voidFalling = false
        }

        if (Speed.handleEvents() && voidFalling) {
            toggledSpeed = true
            Speed.state = !Speed.state
        }

        if (!voidFalling && !Speed.handleEvents() && toggledSpeed) {
            Speed.state = !Speed.state
            toggledSpeed = false
        }
    }

    override fun onBlockBB(event: BlockBBEvent) {
        val player = mc.thePlayer ?: return

        if (event.block is BlockAir && !player.isSneaking && voidFalling) {
            if (event.y < player.posY) {
                event.boundingBox = AxisAlignedBB.fromBounds(-15.0, -1.0, -15.0, 15.0, 1.0, 15.0)
                    .offset(event.x.toDouble(), event.y.toDouble(), event.z.toDouble())
            }
        }

        if ((event.block !is BlockAir || player.isSneaking) && voidFalling && !player.isCollidedHorizontally) {
            voidFalling = false
        }
    }

    override fun onStrafe(event: StrafeEvent) {
        val player = mc.thePlayer ?: return

        if (voidFalling) {
            event.cancelEvent()
            PortedMove.strafe(0.1)
            if (player.ticksExisted % 2 != 1 && player.moveForward == 0f) {
                PortedMove.strafe(0.0)
                moveFlyingWithForward(event.strafe, -1f, event.friction)
            } else {
                moveFlyingWithForward(event.strafe, 1f, event.friction)
            }
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        if (event.packet is S08PacketPlayerPosLook) {
            voidFalling = false
        }
    }

    override fun onWorldChange() {
        voidFalling = false
    }

    override fun onJump(event: JumpEvent) {
        if (event.eventState != EventState.PRE) return
        if (voidFalling) {
            event.motion = 0f
        }
    }

    /** 原版 moveFlying 公式（以覆盖后的 forward 重新施加本 tick 的输入位移）。 */
    private fun moveFlyingWithForward(strafe: Float, forward: Float, friction: Float) {
        val player = mc.thePlayer ?: return
        var f = strafe * strafe + forward * forward
        if (f >= 1.0E-4f) {
            f = MathHelper.sqrt_float(f)
            if (f < 1.0f) {
                f = 1.0f
            }
            f = friction / f
            val s = strafe * f
            val fw = forward * f
            val sin = MathHelper.sin(player.rotationYaw * 0.017453292f)
            val cos = MathHelper.cos(player.rotationYaw * 0.017453292f)
            player.motionX += s * cos - fw * sin
            player.motionZ += fw * cos + s * sin
        }
    }
}
