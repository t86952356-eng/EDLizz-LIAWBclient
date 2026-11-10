/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.watchdogncp.WatchdogNCPNewSpeed
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold2
import net.ccbluex.liquidbounce.utils.client.PacketUtils.handlePackets
import net.ccbluex.liquidbounce.utils.client.realMotionX
import net.ccbluex.liquidbounce.utils.client.realMotionY
import net.ccbluex.liquidbounce.utils.client.realMotionZ
import net.ccbluex.liquidbounce.utils.client.realX
import net.ccbluex.liquidbounce.utils.client.realY
import net.ccbluex.liquidbounce.utils.client.realZ
import net.ccbluex.liquidbounce.utils.movement.PortedBlink
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.entity.Entity
import net.minecraft.network.Packet
import net.minecraft.network.play.server.*
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Velocity「Vulcan-New」模式：
 * Stack + Ping Spoof + Backtrack + 落地补发击退（pendingVelocity）。
 * 与 WatchdogNCP-New 同族，Speed 开启时暂停吞包。
 */
object VulcanNewVelocityMode : VelocityMode("Vulcan-New") {

    /** Stack（默认开）。 */
    var stack = true

    /** Ping Spoof（默认开）。 */
    var pingSpoof = true

    /** Always Cancel Vertical（原实现默认开；受模块共享值控制）。 */
    var alwaysCancelVertical = true

    /** Backtrack（默认关）。 */
    var backtrack = false

    /** Damage Boost（默认关）。 */
    var damageBoost = false

    /** Damage Boost Speed（默认 1，1..10）。 */
    var damageBoostSpeed = 1.0

    var blinking = false
        private set
    private var releasing = false

    private var inBacktrackRange = false
    private var backtrackBlocked = false
    private var backtrackStartTime = -1L
    private var targetPosition = Vec3(0.0, 0.0, 0.0)
    private var target: Entity? = null
    private var pendingVelocity = false
    private var stackedVelocities = 0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private val heldPackets = ArrayList<Packet<*>>()
    private val blink = PortedBlink(holdAllClientPackets = true)

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        // 原实现在此处直接丢弃持包；这里安全地补发以避免与服务器失步
        releaseAll()
        blink.dispatch()
        reset()
    }

    private fun reset() {
        blinking = false
        pendingVelocity = false
        stackedVelocities = 0
        inBacktrackRange = false
        backtrackBlocked = false
        backtrackStartTime = -1L
        target = null
    }

    private fun releaseAll() {
        if (heldPackets.isEmpty()) return
        releasing = true
        val packets = heldPackets.toList()
        heldPackets.clear()
        handlePackets(*packets.toTypedArray())
        releasing = false
    }

    private fun releaseBacktrackPackets() {
        releasing = true
        val filtered = heldPackets.filter {
            it is S14PacketEntity.S16PacketEntityLook || it is S14PacketEntity.S15PacketEntityRelMove ||
                it is S14PacketEntity.S17PacketEntityLookMove || it is S19PacketEntityHeadLook ||
                it is S18PacketEntityTeleport
        }
        handlePackets(*filtered.toTypedArray())
        heldPackets.clear()
        releasing = false
    }

    private val speedEnabled get() = Speed.handleEvents()
    private val flyEnabled get() = Fly.handleEvents()
    private val longJumpEnabled get() = LongJump.handleEvents()
    private val scaffoldEnabled get() = Scaffold.handleEvents() || Scaffold2.handleEvents()

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.RECEIVE) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (!releasing && !longJumpEnabled && !flyEnabled && !speedEnabled) {
            when (packet) {
                is S12PacketEntityVelocity -> {
                    // ---- Damage Boost：击退水平分量直接转为移动方向加速 ----
                    // （原选项此前只有定义没有实现；本分支在 Speed 模块未启用时独立完成加速）
                    if (damageBoost && packet.entityID == player.entityId &&
                        !scaffoldEnabled && !player.isInWater && !PortedMove.isInsideBlock() &&
                        PortedMove.isMoving() &&
                        hypot(packet.motionX / 8000.0, packet.motionZ / 8000.0) > 0.08
                    ) {
                        PortedMove.strafe(hypot(packet.motionX / 8000.0, packet.motionZ / 8000.0) * damageBoostSpeed)
                        player.motionY = packet.motionY / 8000.0
                        event.cancelEvent()
                    } else if (packet.entityID == player.entityId && !PortedMove.isInsideBlock() &&
                        (
                            player.itemInUseCount <= 0 ||
                                !alwaysCancelVertical ||
                                speedEnabled ||
                                player.isJumping ||
                                mc.gameSettings.keyBindJump.isKeyDown ||
                                !(packet.motionY / 8000.0 > 0.08) ||
                                PortedTrackers.ticksSinceTeleport <= 11
                            )
                    ) {
                        val useTicks = PortedTrackers.itemUseTicks
                        if ((stack && stackedVelocities < 1 && !player.onGround && Math.random() < 0.73 && useTicks <= 13) ||
                            player.isInWater ||
                            scaffoldEnabled && (
                                useTicks <= 3 ||
                                    !PortedMove.isMoving() ||
                                    !speedEnabled ||
                                    !WatchdogNCPNewSpeed.damageBoostEnabled ||
                                    !(
                                        hypot(
                                            PortedMove.predictedMotion(packet.motionX / 8000.0, 1),
                                            PortedMove.predictedMotion(packet.motionZ / 8000.0, 1)
                                        ) > PortedMove.speed()
                                        )
                                )
                        ) {
                            stackedVelocities++
                            event.cancelEvent()
                        }

                        if (event.isCancelled ||
                            scaffoldEnabled ||
                            useTicks > 11 ||
                            player.onGround && (!player.onGround || PortedMove.isMoving()) ||
                            packet.motionY / 8000.0 < 0.08 && !speedEnabled && !longJumpEnabled ||
                            !scaffoldEnabled && useTicks > 3 && PortedMove.isMoving() &&
                            speedEnabled && WatchdogNCPNewSpeed.damageBoostEnabled &&
                            hypot(packet.motionX / 8000.0, packet.motionZ / 8000.0) > PortedMove.speed()
                        ) {
                            if (!event.isCancelled) {
                                if (!scaffoldEnabled) {
                                    player.motionY = packet.motionY / 8000.0
                                }
                                if (!speedEnabled) {
                                    event.cancelEvent()
                                }
                            }
                        } else {
                            stackedVelocities = 0
                            blinking = true
                            heldPackets.add(packet)
                            event.cancelEvent()
                        }
                    } else if (!event.isCancelled && PortedMove.isInsideBlock()) {
                        event.cancelEvent()
                    } else if (!event.isCancelled && packet.entityID == player.entityId && !longJumpEnabled) {
                        pendingVelocity = true
                        heldPackets.add(packet)
                        velocityX = packet.motionX / 8000.0
                        velocityZ = packet.motionZ / 8000.0
                        velocityY = packet.motionY / 8000.0
                        event.cancelEvent()
                    }
                }

                is S32PacketConfirmTransaction -> if (blinking && pingSpoof) {
                    heldPackets.add(packet)
                    event.cancelEvent()
                }

                is S00PacketKeepAlive -> if (blinking && pingSpoof) {
                    event.cancelEvent()
                }

                is S14PacketEntity.S16PacketEntityLook ->
                    if ((inBacktrackRange || blinking) && backtrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }

                is S14PacketEntity.S15PacketEntityRelMove ->
                    if ((inBacktrackRange || blinking) && backtrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }

                is S14PacketEntity.S17PacketEntityLookMove ->
                    if ((inBacktrackRange || blinking) && backtrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }

                is S19PacketEntityHeadLook ->
                    if ((inBacktrackRange || blinking) && backtrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }
            }

            // ---- 目标跟踪 + 回溯释放 ----
            val targets = PortedMove.targetsInRange(12.0)
            if (targets.isEmpty()) {
                target = null
                if (backtrack) {
                    releaseBacktrackPackets()
                    inBacktrackRange = false
                    backtrackBlocked = false
                }
            } else {
                val entity = targets[0]
                if (backtrack) {
                    if (entity != target) {
                        target = entity
                        targetPosition = Vec3(entity.posX, entity.posY, entity.posZ)
                    }

                    val eyes = player.getPositionEyes(1.0f)
                    val targetVec = Vec3(targetPosition.xCoord, targetPosition.yCoord, targetPosition.zCoord)
                    val distance = eyes.distanceTo(targetVec)
                    if ((!(distance > 4.5) || !blinking) && (PortedTrackers.itemUseTicks <= 9 || !blinking)) {
                        if (blinking) {
                            backtrackBlocked = false
                        }
                    } else {
                        backtrackBlocked = true
                    }

                    inBacktrackRange = distance < 4.5

                    if (inBacktrackRange) {
                        if (backtrackStartTime == -1L) {
                            backtrackStartTime = System.currentTimeMillis()
                        }
                        if (System.currentTimeMillis() - backtrackStartTime >= Math.round(Math.random() * 150.0) + 100L) {
                            releaseBacktrackPackets()
                            inBacktrackRange = false
                            backtrackStartTime = -1L
                        }
                    } else {
                        backtrackStartTime = -1L
                    }

                    if (backtrackBlocked && blinking) {
                        inBacktrackRange = false
                        releaseBacktrackPackets()
                    }

                    if (player.ticksExisted < 100) {
                        inBacktrackRange = false
                        releaseBacktrackPackets()
                    }

                    if (player.hurtTime == 9) {
                        inBacktrackRange = false
                        releaseBacktrackPackets()
                    }

                    if (distance < 2.5) {
                        inBacktrackRange = false
                        releaseBacktrackPackets()
                    }

                    if (distance > 4.5) {
                        inBacktrackRange = false
                        releaseBacktrackPackets()
                    }

                    val tracked = target
                    if (tracked != null) {
                        when (packet) {
                            is S14PacketEntity -> if (packet.entityId == tracked.entityId) {
                                targetPosition = Vec3(
                                    targetPosition.xCoord + packet.realMotionX,
                                    targetPosition.yCoord + packet.realMotionY,
                                    targetPosition.zCoord + packet.realMotionZ
                                )
                            }
                            is S18PacketEntityTeleport -> if (packet.entityId == tracked.entityId) {
                                targetPosition = Vec3(packet.realX, packet.realY, packet.realZ)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        if ((PortedTrackers.ticksSinceVelocity != 1 || !PortedMove.isMoving()) &&
            PortedTrackers.ticksSinceVelocity == 1
        ) {
            player.motionX *= -1.0
            player.motionZ *= -1.0
        }

        if (pendingVelocity && player.onGround) {
            pendingVelocity = false
            releasing = true
            val savedX = player.motionX
            val savedZ = player.motionZ
            val savedY = player.motionY
            releaseAll()
            player.motionY = savedY
            player.motionX = savedX
            player.motionZ = savedZ
            event.y = event.y + velocityY
            releasing = false
        }

        // 原实现在 PostStrafe 阶段释放超时持包，这里于 PRE 补齐
        if (PortedTrackers.itemUseTicks > 12 && blinking) {
            blinking = false
            releasing = true
            val savedX = player.motionX
            val savedZ = player.motionZ
            blink.dispatch()
            releaseAll()
            if (!speedEnabled) {
                player.motionX = savedX
                player.motionZ = savedZ
            }
            releasing = false
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (blinking && player.onGround && !pendingVelocity && !speedEnabled) {
            blinking = false
            releasing = true
            backtrackBlocked = false
            blink.dispatch()
            val savedX = player.motionX
            val savedZ = player.motionZ
            val savedY = player.motionY
            releaseAll()
            player.jump()
            player.motionX = savedX
            player.motionZ = savedZ
            player.motionY = savedY
            if (player.isJumping) {
                var yaw = player.prevRotationYaw * (Math.PI / 180.0).toFloat()
                if (player.isSprinting) {
                    yaw = (PortedMove.direction() * (180.0 / Math.PI)).toFloat() * (Math.PI / 180.0).toFloat()
                }
                player.motionX = player.motionX - MathHelper.sin(yaw) * 0.2f
                player.motionZ = player.motionZ + MathHelper.cos(yaw) * 0.2f
            }
            releasing = false
        }

        if (inBacktrackRange && !blinking && backtrack) {
            blink.activate(50)
        }
    }

    override fun onRender3D() {
        val player = mc.thePlayer ?: return
        val tracked = target ?: return
        if (!backtrack || !(inBacktrackRange || blinking)) return

        val renderManager = mc.renderManager
        val box = player.entityBoundingBox
            .offset(-player.posX, -player.posY, -player.posZ)
            .offset(targetPosition.xCoord, targetPosition.yCoord, targetPosition.zCoord)
            .offset(-renderManager.renderPosX, -renderManager.renderPosY, -renderManager.renderPosZ)
            .expand(-0.14, -0.3, -0.14)
        RenderUtils.drawAxisAlignedBB(box, Color(255, 0, 0, 45))
    }

    /** 供 Velocity 模块驱动的 blink 拦截。 */
    fun driveBlink(event: PacketEvent): Boolean {
        if (event.eventType == EventState.SEND) {
            return blink.onSend(event.packet, event.isCancelled) { event.cancelEvent() }
        }
        return blink.onReceive(event.packet, event.isCancelled) { event.cancelEvent() }
    }
}
