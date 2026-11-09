/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.ccbluex.liquidbounce.utils.movement

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockLiquid
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.client.Minecraft
import net.minecraft.network.Packet
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.login.client.C00PacketLoginStart
import net.minecraft.network.login.client.C01PacketEncryptionResponse
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraft.network.status.client.C00PacketServerQuery
import net.minecraft.network.status.client.C01PacketPing
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import net.minecraft.potion.Potion
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold2
import kotlin.math.*

/**
 * 公共状态跟踪器 + 运动/碰撞工具（供 NoSlow / Velocity / Speed / Fly 的
 * 新移植模式使用）。语义与另一套 1.8.9 实现保持一致：
 *
 *  - [ticksSinceVelocity]  = 收到上次 S12 击退后经过的 tick（对应 ae）
 *  - [ticksSinceTeleport]  = 收到上次 S08 传送后经过的 tick（对应 Zl）
 *  - [expectedVelX]/[expectedVelZ] = 服务端最后一次下发给玩家的期望速度（对应 crI/crK）
 *  - [itemUseTicks] = 当前物品已使用的 tick 数（向上计数，对应 tR）
 *  - [prevMotion]   = 上一 tick 结束时的 motion（对应 cry/crA/crz 的差值用法）
 */
object PortedTrackers : MinecraftInstance, Listenable {

    var lastVelocityTick = -10000
        private set
    var lastTeleportTick = -10000
        private set

    var expectedVelX = 0.0
        private set
    var expectedVelZ = 0.0
        private set

    var prevMotionX = 0.0
    var prevMotionY = 0.0
    var prevMotionZ = 0.0

    val ticksSinceVelocity: Int
        get() = (mc.thePlayer?.ticksExisted ?: 0) - lastVelocityTick

    val ticksSinceTeleport: Int
        get() = (mc.thePlayer?.ticksExisted ?: 0) - lastTeleportTick

    /** 当前使用物品的 tick 数（从 0 开始向上数，未使用时为 0）。 */
    val itemUseTicks: Int
        get() {
            val player = mc.thePlayer ?: return 0
            val inUse = player.itemInUse ?: return 0
            return (inUse.maxItemUseDuration - player.itemInUseCount).coerceAtLeast(0)
        }

    /** 摔落距离跟踪（PRE motion 阶段累计，onGround 时清零）。 */
    var trackedFallDistance = 0f

    private val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (event.eventType == EventState.RECEIVE) {
            when (packet) {
                is S12PacketEntityVelocity -> {
                    if (mc.thePlayer != null && packet.entityID == mc.thePlayer!!.entityId) {
                        lastVelocityTick = mc.thePlayer!!.ticksExisted
                        expectedVelX = packet.motionX / 8000.0
                        expectedVelZ = packet.motionZ / 8000.0
                    }
                }
                is S08PacketPlayerPosLook -> {
                    if (mc.thePlayer != null) {
                        lastTeleportTick = mc.thePlayer!!.ticksExisted
                    }
                }
            }
        } else {
            // BadPackets 跟踪：自上一次 C03 以来是否发送过“敏感”包
            when (packet) {
                is C09PacketHeldItemChange -> badSlotSwitch = true
                is C0APacketAnimation -> badSwing = true
                is C02PacketUseEntity -> badUseEntity = true
                is C08PacketPlayerBlockPlacement -> badPlace = true
                is C07PacketPlayerDigging -> {
                    badPlace = true
                    badDigging = true
                }
                is C0EPacketClickWindow, is C0DPacketCloseWindow -> badInventory = true
                is C16PacketClientStatus -> if (packet.status == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) badInventory = true
                is C03PacketPlayer -> resetBadPackets()
                else -> Unit
            }
        }
    }

    private val onMotion = handler<MotionEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        if (event.eventState == EventState.PRE) {
            val delta = player.lastTickPosY - player.posY
            if (delta > 0) trackedFallDistance += delta.toFloat()
            if (event.onGround) trackedFallDistance = 0f
        }
    }

    private val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        prevMotionX = player.motionX
        prevMotionY = player.motionY
        prevMotionZ = player.motionZ
    }

    // ---- BadPacketsComponent 语义 ----
    var badSlotSwitch = false
        private set
    var badSwing = false
        private set
    var badUseEntity = false
        private set
    var badPlace = false
        private set
    var badInventory = false
        private set
    var badDigging = false
        private set

    fun bad(switch: Boolean, swing: Boolean, use: Boolean, place: Boolean, inventory: Boolean, digging: Boolean = false): Boolean =
        (badSlotSwitch && switch) || (badUseEntity && use) || (badSwing && swing) ||
            (badPlace && place) || (badInventory && inventory) || (badDigging && digging)

    fun resetBadPackets() {
        badSlotSwitch = false
        badSwing = false
        badUseEntity = false
        badPlace = false
        badInventory = false
        badDigging = false
    }

    /** 是否有任意 PortedBlink 正在持包（对应 BlinkComponent.enabled）。 */
    private val activeBlinks = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<PortedBlink, Boolean>())
    val anyBlinkActive: Boolean
        get() = activeBlinks.isNotEmpty()

    internal fun registerBlinkActive(blink: PortedBlink) = activeBlinks.add(blink)
    internal fun unregisterBlinkActive(blink: PortedBlink) = activeBlinks.remove(blink)
}

/**
 * 出站包队列（对应 PacketQueueComponent）：
 * [active] 为 true 时所有出站包被暂存并取消；
 * [dispatch] 之后随下一个 C03 一起（无事件）发出。
 */
class PortedPacketQueue {
    val queue = ArrayDeque<Packet<*>>()
    var active = false
        private set
    private var dispatchRequested = false

    fun activate() {
        active = true
    }

    fun deactivate() {
        active = false
        dispatchRequested = false
    }

    fun dispatch() {
        if (active) dispatchRequested = true
    }

    /** 由宿主模块的 PacketEvent(SEND) 处理器调用；返回 true 表示已拦截该包。 */
    fun onSend(packet: Packet<*>, cancel: () -> Unit): Boolean {
        if (!active) return false
        val player = Minecraft.getMinecraft().thePlayer
        if (player == null || player.isDead) {
            flush()
            active = false
            return false
        }
        if (packet is C00Handshake || packet is C00PacketLoginStart || packet is C00PacketServerQuery ||
            packet is C01PacketPing || packet is C01PacketEncryptionResponse
        ) return false

        if (dispatchRequested && packet is C03PacketPlayer) {
            flush()
            dispatchRequested = false
            return false
        }
        queue.add(packet)
        cancel()
        return true
    }

    fun flush() {
        if (queue.isEmpty()) return
        val packets = queue.toList()
        queue.clear()
        packets.forEach { PacketUtils.sendPacket(it, triggerEvent = false) }
    }

    fun clear() {
        queue.clear()
        dispatchRequested = false
    }
}

/**
 * 每个模式独立的“迷你 Blink”：
 * 激活期间暂存（并取消）出站包（可选全部出站包或仅事务/关窗类），
 * 以及入站的 S1C 元数据包；[dispatch] 时全部补发。
 */
class PortedBlink(private val holdAllClientPackets: Boolean = false) {

    val heldPackets = ArrayDeque<Packet<*>>()
    private var expireTime = 0L
    var active = false
        private set

    fun activate(durationMs: Long) {
        active = true
        expireTime = System.currentTimeMillis() + durationMs
        PortedTrackers.registerBlinkActive(this)
    }

    /** 出站方向；返回 true 表示已拦截。 */
    fun onSend(packet: Packet<*>, isCancelled: Boolean, cancel: () -> Unit): Boolean {
        if (!active) return false
        if (System.currentTimeMillis() > expireTime) {
            active = false
            PortedTrackers.unregisterBlinkActive(this)
            return false
        }
        val capture = if (holdAllClientPackets) {
            packet !is C00Handshake && packet !is C00PacketLoginStart && packet !is C00PacketServerQuery &&
                packet !is C01PacketPing && packet !is C01PacketEncryptionResponse
        } else {
            // 组1：事务 / 关窗
            packet is C0FPacketConfirmTransaction || packet is C0DPacketCloseWindow
        }
        if (capture && !isCancelled) {
            heldPackets.add(packet)
            cancel()
            return true
        }
        return false
    }

    /** 入站方向；返回 true 表示已拦截。 */
    fun onReceive(packet: Packet<*>, isCancelled: Boolean, cancel: () -> Unit): Boolean {
        if (!active) return false
        if (System.currentTimeMillis() > expireTime) {
            active = false
            PortedTrackers.unregisterBlinkActive(this)
            return false
        }
        // 组1：S1C 实体元数据
        if (packet is S1CPacketEntityMetadata && !isCancelled) {
            heldPackets.add(packet)
            cancel()
            return true
        }
        return false
    }

    fun dispatch() {
        active = false
        PortedTrackers.unregisterBlinkActive(this)
        if (heldPackets.isEmpty()) return
        val packets = heldPackets.toList()
        heldPackets.clear()
        for (packet in packets) {
            when (packet) {
                is S1CPacketEntityMetadata -> PacketUtils.handlePackets(packet)
                else -> PacketUtils.sendPacket(packet, triggerEvent = false)
            }
        }
    }

    fun clear() {
        heldPackets.clear()
        active = false
        PortedTrackers.unregisterBlinkActive(this)
    }
}

/**
 * 运动/碰撞辅助（与另一套 1.8.9 实现的 MoveUtil/PlayerUtil 语义一致）。
 */
object PortedMove : MinecraftInstance {

    val player
        get() = mc.thePlayer

    val world
        get() = mc.theWorld

    fun isMoving(): Boolean {
        val p = player ?: return false
        return p.moveForward != 0f || p.moveStrafing != 0f
    }

    fun speed(): Double {
        val p = player ?: return 0.0
        return sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ)
    }

    /** 朝移动方向设置固定速度（strength=1：完全覆盖）。 */
    fun strafe(speed: Double = this.speed()) {
        val p = player ?: return
        if (isMoving()) {
            val dir = direction()
            p.motionX = -MathHelper.sin(dir.toFloat()) * speed
            p.motionZ = MathHelper.cos(dir.toFloat()) * speed
        }
    }

    /** 朝移动方向叠加速度（对应 moveFlying(delta)）。 */
    fun moveFlying(speed: Double) {
        val p = player ?: return
        if (isMoving()) {
            val dir = direction()
            p.motionX += -MathHelper.sin(dir.toFloat()) * speed
            p.motionZ += MathHelper.cos(dir.toFloat()) * speed
        }
    }

    /** 以当前速度的 p% 混合到目标方向。 */
    fun partialStrafePercent(percent: Double) {
        var p = (percent / 100.0).coerceIn(0.0, 1.0)
        val player = player ?: return
        val oldX = player.motionX
        val oldZ = player.motionZ
        strafe()
        player.motionX = oldX + (player.motionX - oldX) * p
        player.motionZ = oldZ + (player.motionZ - oldZ) * p
    }

    fun stop() {
        val p = player ?: return
        p.motionX = 0.0
        p.motionZ = 0.0
    }

    fun direction(): Double {
        val p = player ?: return 0.0
        var yaw = p.rotationYaw
        if (p.moveForward < 0f) yaw += 180f
        var forward = 1f
        if (p.moveForward < 0f) forward = -0.5f else if (p.moveForward > 0f) forward = 0.5f
        if (p.moveStrafing > 0f) yaw -= 90f * forward
        if (p.moveStrafing < 0f) yaw += 90f * forward
        return Math.toRadians(yaw.toDouble())
    }

    fun predictedMotion(motion: Double, ticks: Int): Double {
        var m = motion
        repeat(ticks) { m = (m - 0.08) * 0.98 }
        return m
    }

    private fun depthStriderLevel(): Int {
        val p = player ?: return 0
        return EnchantmentHelper.getDepthStriderModifier(p)
    }

    private val MOD_DEPTH_STRIDER = doubleArrayOf(1.0, 1.4304347400741908, 1.7347825295420372, 1.9217390955733897)

    private fun inLiquid(): Boolean {
        val p = player ?: return false
        return p.isInWater || p.isInLava
    }

    fun allowedHorizontalDistance(): Double {
        val p = player ?: return 0.221
        var dist: Double
        var sprintApplies = false
        if (p.isInWeb) {
            dist = 0.105
        } else if (inLiquid()) {
            dist = 0.11500000208616258
            val level = depthStriderLevel()
            if (level > 0) {
                dist *= MOD_DEPTH_STRIDER[level.coerceAtMost(3)]
                sprintApplies = true
            }
        } else if (p.isSneaking) {
            dist = 0.0663000026345253
        } else {
            dist = 0.221
            sprintApplies = true
        }
        if (sprintApplies) {
            if (canSprintForMovement()) dist *= 1.3
            if (p.isPotionActive(Potion.moveSpeed) && p.getActivePotionEffect(Potion.moveSpeed).duration > 0 && !Scaffold.state && !Scaffold2.state) {
                dist *= 1.0 + 0.2 * (p.getActivePotionEffect(Potion.moveSpeed).amplifier + 1)
            }
            if (p.isPotionActive(Potion.moveSlowdown)) dist = 0.29
        }
        return dist
    }

    /** 斜向修正：只有单一方向键按下时应用正向微加速，否则施加减速。 */
    fun useDiagonalSpeed() = applyDiagonal(true)

    fun preventDiagonalSpeed() = applyDiagonal(false)

    private fun applyDiagonal(use: Boolean) {
        val p = player ?: return
        val keys = intArrayOf(
            mc.gameSettings.keyBindForward.keyCode, mc.gameSettings.keyBindRight.keyCode,
            mc.gameSettings.keyBindBack.keyCode, mc.gameSettings.keyBindLeft.keyCode
        )
        val count = keys.count { org.lwjgl.input.Keyboard.isKeyDown(it) }
        val singleKey = count == 1
        if (if (use) singleKey else !singleKey) {
            val delta = if (p.onGround) 0.0026000750109401644 else 5.199896488849598E-4
            moveFlying(if (use) delta else -delta)
        }
    }

    fun canSprintForMovement(): Boolean {
        val p = player ?: return false
        return abs(p.moveForward) >= 0.8f || abs(p.moveStrafing) >= 0.8f
    }

    /** 基础速度（含速度药水加成）。 */
    fun vd(): Double {
        val p = player ?: return 0.2873
        var base = 0.2873
        if (p.isPotionActive(Potion.moveSpeed)) {
            base *= 1.0 + 0.2 * (p.getActivePotionEffect(Potion.moveSpeed).amplifier + 1)
        }
        return base
    }

    // ---- PlayerUtil 碰撞类 ----

    /** 玩家位置 + 偏移处的方块（对应 PlayerUtil.p）。 */
    fun blockAt(dx: Double, dy: Double, dz: Double) =
        world?.getBlockState(BlockPos(player!!.posX + dx, player!!.posY + dy, player!!.posZ + dz))?.block

    /** 玩家包围盒 + 纵向偏移是否与方块碰撞（对应 PlayerUtil.ae）。 */
    fun collidesAt(offsetY: Double): Boolean {
        val p = player ?: return false
        val bb = p.entityBoundingBox.offset(0.0, offsetY, 0.0)
        return world?.getCollidingBoundingBoxes(p, bb)?.isNotEmpty() == true
    }

    /** 前方/上方空间碰撞探测（对应 PlayerUtil.b(distance, true)）。 */
    fun blockAheadCollides(distance: Double): Boolean {
        val p = player ?: return false
        val bb = p.entityBoundingBox
            .offset(0.0, distance / 2.0, 0.0)
            .expand(0.0, distance - p.height, 0.0)
        return world?.getCollidingBoundingBoxes(p, bb)?.isNotEmpty() == true
    }

    /** 玩家是否身处实体方块内部（对应 PlayerUtil.vk）。 */
    fun isInsideBlock(): Boolean {
        val p = player ?: return false
        if (p.ticksExisted < 5) return false
        val bb = p.entityBoundingBox
        val minX = floor(bb.minX)
        val maxX = floor(bb.maxX) + 1
        val minY = floor(bb.minY)
        val maxY = floor(bb.maxY) + 1
        val minZ = floor(bb.minZ)
        val maxZ = floor(bb.maxZ) + 1
        var x = minX
        while (x < maxX) {
            var y = minY
            while (y < maxY) {
                var z = minZ
                while (z < maxZ) {
                    val block = world!!.getBlockState(BlockPos(x, y, z)).block
                    if (block != null && block !is BlockAir && block !is BlockLiquid) {
                        val blockBb = block.getCollisionBoundingBox(world, BlockPos(x, y, z), world!!.getBlockState(BlockPos(x, y, z)))
                        if (blockBb != null && bb.intersectsWith(blockBb)) return true
                    }
                    z++
                }
                y++
            }
            x++
        }
        return false
    }

    /** 指定距离内是否有可攻击目标（按 hurtTime 升序，最近受击优先）。 */
    fun targetsInRange(range: Double): List<EntityLivingBase> {
        val p = player ?: return emptyList()
        val w = world ?: return emptyList()
        return w.loadedEntityList
            .filterIsInstance<EntityLivingBase>()
            .filter {
                it != p && !it.isDead && it !is EntityArmorStand &&
                    p.getDistanceToEntity(it) <= range
            }
            .sortedBy { it.hurtTime }
    }

    /** 距地面近似高度（未用到时返回 0）。 */
    fun blockDown(): net.minecraft.block.Block? = blockAt(0.0, -1.0, 0.0)
}

