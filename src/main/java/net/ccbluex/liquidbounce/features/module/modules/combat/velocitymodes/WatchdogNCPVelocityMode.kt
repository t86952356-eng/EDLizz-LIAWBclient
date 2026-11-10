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
 * Velocity「WatchdogNCP-New」模式：
 * 击退堆叠（Stack）+ ping 欺骗（持包事务/保活）+ Watchdog 回溯（Backtrack）。
 * 逻辑按原版 1.8.9 实现等价移植；与 Speed 的 WatchdogNCP-New 模式联动（Damage Boost）。
 */
object WatchdogNCPVelocityMode : VelocityMode("WatchdogNCP-New") {

    /** Stack（默认开）。 */
    var stack = true

    /** Ping Spoof（默认开）。 */
    var pingSpoof = true

    /** Always Cancel Vertical（默认关）。 */
    var alwaysCancelVertical = false

    /** Watchdog Backtrack（默认关）。 */
    var watchdogBacktrack = false

    /** Damage Boost (not for hypixel)（默认关）。 */
    var damageBoostNotForHypixel = false

    /** Damage Boost Speed（默认 1，1..10）。 */
    var damageBoostSpeed = 1.0

    var dj = false
        private set
    var releasing = false
        private set

    private var onOffsetGround = false
    private var inBacktrackRange = false
    private var backtrackBlocked = false
    private var backtrackStart = -1L
    private var backtrackPos = Vec3(0.0, 0.0, 0.0)
    private var target: Entity? = null
    private var stackCount = 0
    private val heldPackets = ArrayList<Packet<*>>()
    private val blink = PortedBlink(holdAllClientPackets = false)

    val isBlinking: Boolean get() = dj

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        releaseAll()
        blink.dispatch()
        reset()
    }

    private fun reset() {
        dj = false
        stackCount = 0
        onOffsetGround = false
        inBacktrackRange = false
        backtrackBlocked = false
        backtrackStart = -1
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

        if (!releasing && !longJumpEnabled && !flyEnabled) {
            when (packet) {
                is S12PacketEntityVelocity -> {
                    // ---- Damage Boost (not for hypixel)：击退水平分量直接转为移动方向加速 ----
                    // （原选项此前只有定义没有实现；Speed 模块未启用时由本模式独立完成加速）
                    if (damageBoostNotForHypixel && packet.entityID == player.entityId &&
                        !speedEnabled && !scaffoldEnabled && !player.isInWater && !PortedMove.isInsideBlock() &&
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
                                onOffsetGround ||
                                !(packet.motionY / 8000.0 > 0.08) ||
                                PortedTrackers.ticksSinceTeleport <= 11
                            )
                    ) {
                        val useTicks = PortedTrackers.itemUseTicks
                        if ((stack && stackCount < 1 && !player.onGround && Math.random() < 0.73 && useTicks <= 13) ||
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
                            stackCount++
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
                            stackCount = 0
                            dj = true
                            heldPackets.add(packet)
                            event.cancelEvent()
                        }
                    } else if (!event.isCancelled && PortedMove.isInsideBlock()) {
                        event.cancelEvent()
                    } else if (!event.isCancelled && packet.entityID == player.entityId && !longJumpEnabled) {
                        packet.motionY *= 0
                        player.motionX *= 0.9
                        player.motionZ *= 0.9
                    }
                }

                is S32PacketConfirmTransaction -> if (dj && pingSpoof) {
                    heldPackets.add(packet)
                    event.cancelEvent()
                }

                // S00PacketKeepAlive：ping 欺骗期间直接吞掉（不补发）
                is S00PacketKeepAlive -> if (dj && pingSpoof) {
                    event.cancelEvent()
                }

                is S14PacketEntity.S16PacketEntityLook ->
                    if ((inBacktrackRange || dj) && watchdogBacktrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }

                is S14PacketEntity.S15PacketEntityRelMove ->
                    if ((inBacktrackRange || dj) && watchdogBacktrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }

                is S14PacketEntity.S17PacketEntityLookMove ->
                    if ((inBacktrackRange || dj) && watchdogBacktrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }

                is S19PacketEntityHeadLook ->
                    if ((inBacktrackRange || dj) && watchdogBacktrack && !backtrackBlocked) {
                        heldPackets.add(packet)
                        event.cancelEvent()
                    }
            }

            // ---- 目标跟踪 + 回溯释放 ----
            val targets = PortedMove.targetsInRange(12.0)
            if (targets.isEmpty()) {
                target = null
                if (watchdogBacktrack) {
                    releaseBacktrackPackets()
                    inBacktrackRange = false
                    backtrackBlocked = false
                }
            } else {
                val entity = targets[0]
                if (watchdogBacktrack) {
                    if (entity != target) {
                        target = entity
                        backtrackPos = Vec3(entity.posX, entity.posY, entity.posZ)
                    }

                    val eyes = player.getPositionEyes(1.0f)
                    val targetVec = Vec3(backtrackPos.xCoord, backtrackPos.yCoord, backtrackPos.zCoord)
                    val distance = eyes.distanceTo(targetVec)
                    if ((!(distance > 4.5) || !dj) && (PortedTrackers.itemUseTicks <= 9 || !dj)) {
                        if (dj) {
                            backtrackBlocked = false
                        }
                    } else {
                        backtrackBlocked = true
                    }

                    inBacktrackRange = distance < 4.5

                    if (inBacktrackRange) {
                        if (backtrackStart == -1L) {
                            backtrackStart = System.currentTimeMillis()
                        }
                        if (System.currentTimeMillis() - backtrackStart >= Math.round(Math.random() * 150.0) + 100L) {
                            releaseBacktrackPackets()
                            inBacktrackRange = false
                            backtrackStart = -1L
                        }
                    } else {
                        backtrackStart = -1L
                    }

                    if (backtrackBlocked && dj) {
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
                                backtrackPos = Vec3(
                                    backtrackPos.xCoord + packet.realMotionX,
                                    backtrackPos.yCoord + packet.realMotionY,
                                    backtrackPos.zCoord + packet.realMotionZ
                                )
                            }
                            is S18PacketEntityTeleport -> if (packet.entityId == tracked.entityId) {
                                backtrackPos = Vec3(packet.realX, packet.realY, packet.realZ)
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

        if (abs(player.posY - player.posY.roundToLong().toDouble()) > 0.01 && player.onGround) {
            onOffsetGround = true
        } else {
            onOffsetGround = false
        }

        val ae = PortedTrackers.ticksSinceVelocity
        if (ae == 1 && PortedMove.isMoving() &&
            (abs(player.posY - floor(player.posY)) < 0.03 || player.motionY < 0.0) && alwaysCancelVertical
        ) {
            PortedMove.strafe()
        } else if (ae == 1 &&
            (abs(player.posY - floor(player.posY)) < 0.03 || player.motionY < 0.0) && alwaysCancelVertical
        ) {
            player.motionX *= -1.0
            player.motionZ *= -1.0
        }

        // 原实现在此后（PostStrafe 阶段）释放超时持包，这里于 PRE 补齐
        if (PortedTrackers.itemUseTicks > 12 && dj) {
            dj = false
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

        if (inBacktrackRange && !dj && watchdogBacktrack) {
            blink.activate(50)
        }

        if (dj && player.onGround && !speedEnabled) {
            dj = false
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
    }

    override fun onJump(event: JumpEvent) {
        val player = mc.thePlayer ?: return
        if (player.onGround && dj && speedEnabled) {
            dj = false
            releasing = true
            backtrackBlocked = false
            blink.dispatch()
            val savedX = player.motionX
            val savedZ = player.motionZ
            releaseAll()
            player.motionX = savedX
            player.motionZ = savedZ
            releasing = false
        }
    }

    override fun onRender3D() {
        val player = mc.thePlayer ?: return
        val tracked = target ?: return
        if (!watchdogBacktrack || !(inBacktrackRange || dj)) return

        val renderManager = mc.renderManager
        val box = player.entityBoundingBox
            .offset(-player.posX, -player.posY, -player.posZ)
            .offset(backtrackPos.xCoord, backtrackPos.yCoord, backtrackPos.zCoord)
            .offset(-renderManager.renderPosX, -renderManager.renderPosY, -renderManager.renderPosZ)
            .expand(-0.14, -0.3, -0.14)
        RenderUtils.drawAxisAlignedBB(box, Color(60, 255, 60, 100))
    }

    /** 供 Velocity 模块驱动的 blink 拦截。 */
    fun driveBlink(event: PacketEvent): Boolean {
        if (event.eventType == EventState.SEND) {
            return blink.onSend(event.packet, event.isCancelled) { event.cancelEvent() }
        }
        return blink.onReceive(event.packet, event.isCancelled) { event.cancelEvent() }
    }
}
