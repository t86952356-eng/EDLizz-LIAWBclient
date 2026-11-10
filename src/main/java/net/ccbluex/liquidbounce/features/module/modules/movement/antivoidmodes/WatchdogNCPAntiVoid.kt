/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.antivoidmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedBlink
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import kotlin.math.abs

/**
 * AntiVoid「WatchdogNCP」模式：
 * 持续记录最后安全位置；沿移动轨迹预测未来位置，若一路下方皆为空气（虚空前兆）
 * 则进入 blink 扣包，累计下落超过 Distance 后无事件地发回安全位置的传送包。
 * 逻辑按原版 1.8.9 实现等价移植（原名 Watchdog）。
 *
 * 移植说明：
 * - 原实现的 PacketQueueComponent.a(...) 调用在源客户端中从不被启用（cR 标志只由
 *   其它模块置位），为无操作调用，未移植；
 * - 原实现的 Watchdog2LongJump.launched / "Watchdog Fire Ball 2" 排除项对应
 *   未移植的模式，仅保留 "Watchdog Fire Ball" 判定；
 * - FallDistanceComponent.cY（累计下落距离）在 PRE 移动包时机等价跟踪。
 */
object WatchdogNCPAntiVoid : AntiVoidMode("WatchdogNCP") {

    /** Distance（默认 3，0..10）。 */
    var distance = 3.0

    private var lastSafePosition: Vec3? = null
    private var lastSafeMotion: Vec3? = null
    private var blinking = false
    private var positionSent = false
    private var voidTicks = 0
    private var moving = false

    /** FallDistanceComponent 等价跟踪（累计下落距离）。 */
    private var trackedFallDistance = 0f

    private val blink = PortedBlink(holdAllClientPackets = true)

    override fun onEnable() {
        lastSafePosition = null
        lastSafeMotion = null
        blinking = false
        positionSent = false
        voidTicks = 0
        moving = false
        trackedFallDistance = 0f
        blink.clear()
    }

    override fun onDisable() {
        blink.dispatch()
        blinking = false
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        // FallDistanceComponent 等价跟踪（每 PRE 更新一次）
        val player = mc.thePlayer ?: return
        val delta = player.lastTickPosY - player.posY
        if (delta > 0) {
            trackedFallDistance += delta.toFloat()
        }
        if (event.onGround) {
            trackedFallDistance = 0f
        }
    }

    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return
        if (player.ticksExisted <= 75) return
        if (Fly.handleEvents()) return

        var flag = false
        moving = abs(player.moveForward) >= 0.8f || abs(player.moveStrafing) >= 0.8f

        val maxSteps = (distance * 40.0).toInt()
        for (i in 0..maxSteps) {
            val world = mc.theWorld ?: break
            val predicted = predictPosition(i) ?: break
            val blockPos = BlockPos(predicted.xCoord, predicted.yCoord - i, predicted.zCoord)
            if (world.getBlockState(blockPos).block !== Blocks.air || player.onGround) {
                flag = false
                break
            }
            flag = true
        }

        if (flag) {
            voidTicks++
        } else if (player.onGround) {
            voidTicks = 0
        }

        if (!flag ||
            lastSafePosition == null ||
            lastSafeMotion == null ||
            !(voidTicks < 30.0 + distance * 30.0) ||
            LongJump.handleEvents() && LongJump.mode == "Watchdog Fire Ball" ||
            Fly.handleEvents() ||
            player.inventory.getStackInSlot(0) != null && player.inventory.getStackInSlot(0)!!.item === Items.compass ||
            Scaffold.handleEvents()
        ) {
            positionSent = false
            if (blinking) {
                blink.dispatch()
                blinking = false
            }

            lastSafeMotion = Vec3(player.motionX, player.motionY, player.motionZ)
            lastSafePosition = Vec3(player.posX, player.posY, player.posZ)
        } else if (!positionSent) {
            blinking = true
            blink.activate(60000L)
            if (trackedFallDistance > distance || positionSent) {
                val safe = lastSafePosition ?: return
                sendPacket(
                    C04PacketPlayerPosition(safe.xCoord, safe.yCoord + 0.09800000190735147, safe.zCoord, false),
                    triggerEvent = false
                )
                positionSent = true
                trackedFallDistance = 0f
            }
        } else {
            blink.dispatch()
        }
    }

    /**
     * 原实现 MoveUtil.a(player, (strafe, forward), ticks, true)：
     * 无重力的水平轨迹预测（motion ×0.546 + moveFlying 增量每 tick）。
     */
    private fun predictPosition(ticks: Int): Vec3? {
        val player = mc.thePlayer ?: return null
        var posX = player.posX
        var posY = player.posY
        var posZ = player.posZ
        var motionX = player.posX - player.lastTickPosX
        var motionZ = player.posZ - player.lastTickPosZ
        val yaw = player.rotationYaw
        val strafe = player.moveStrafing
        val forward = player.moveForward

        for (i in 0..ticks) {
            motionZ *= 0.54600006
            motionX *= 0.54600006
            val add = moveFlyingPredict(strafe, forward, yaw) ?: return Vec3(posX, posY, posZ)
            motionX += add[0]
            motionZ += add[1]
            posX += motionX
            posZ += motionZ
        }
        return Vec3(posX, posY, posZ)
    }

    /** 原实现 MoveUtil.moveFlying(strafe, forward, true, yaw, true)。 */
    private fun moveFlyingPredict(strafeIn: Float, forwardIn: Float, yaw: Float): DoubleArray? {
        val player = mc.thePlayer ?: return null
        val f1 = player.aiMoveSpeed
        var f = f1 / 2f * 0.9999998f
        f += f1 / 2f * 0.3f

        var strafe = strafeIn
        var forward = forwardIn
        var f5 = strafe * strafe + forward * forward
        if (f5 >= 1.0E-4f) {
            f5 = MathHelper.sqrt_float(f5)
            if (f5 < 1f) {
                f5 = 1f
            }
            f5 = f / f5
            strafe *= f5
            forward *= f5
            val sin = MathHelper.sin(yaw * 0.017453292f)
            val cos = MathHelper.cos(yaw * 0.017453292f)
            val d0 = (strafe * cos - forward * sin).toDouble()
            val d1 = (forward * cos + strafe * sin).toDouble()
            return doubleArrayOf(d0, d1)
        }
        return null
    }
}
