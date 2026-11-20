/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.extensions.sendUseItem
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemEnderPearl
import net.minecraft.item.ItemFireball
import net.minecraft.item.ItemStack
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.potion.Potion
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import net.minecraft.world.World
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fly「WatchdogDamage」模式（整体移植 Rewound 客户端 RewoundFlightMode）：
 * 六状态飞行机 —— DAMAGE（自伤包 + 摩擦链飞行 + 计时器爆发）/
 * XPROSS（高空坠落 blink 悬浮）/ DISABLER_PEARL / DISABLER_FIREBALL /
 * DISABLER_HOVER（悬停等待反作弊失效）/ DISABLER（3 秒自由飞行窗口）。
 *
 * 与源的已记录差异（无对应基础设施的部分）：
 * - 源 Features.FLIGHT_REWOUND 服务器功能门（anticheat feature flag）→ 移除；
 * - 源 canSelfDamage 的 Rewound 游戏状态门（lobby/BEDWARS 已开局）→ 移除（除
 *   Damage=None 外始终可自伤）；
 * - 源 Fireball 方法限定 BEDWARS 游戏状态 → 移除（无游戏跟踪）；
 * - 源 movementPredictor.historyTicks < 10 → 以 player.ticksExisted < 10 近似；
 * - 源 positionUpdateTicks = 0（私有字段）→ 省略；
 * - 源 Progress 进度条渲染（自绘 HUD）与 Progress 设置 → 移除（Lizz 无该渲染器）；
 * - 通知一律走 chat（源为弹窗 Notification）。
 */
object WatchdogDamage : FlyMode("WatchdogDamage") {

    // ---- 由 Fly 模块同步的设置（源 RewoundFlightMode） ----

    /** Speed（默认 1.0，0.5..5.0）。 */
    var flySpeed = 1.0

    /** Damage：Normal / Wait / None（默认 Normal）。 */
    var damageMode = "Normal"

    /** Timer Enabled（默认开）。 */
    var timerEnabled = true

    /** Timer Speed（默认 2.0x，0.5..10.0）。 */
    var timerSpeed = 2.0f

    /** Timer Time（默认 500ms，5..2000）。 */
    var timerTime = 500L

    /** Drop Enabled（默认开）。 */
    var dropEnabled = true

    /** Drop Speed（默认 4.0，0.5..10.0）。 */
    var dropSpeed = 4.0

    /** Disabler Enabled（默认开）。 */
    var disablerEnabled = true

    /** Ender pearl 方法（默认开）。 */
    var enderPearl = true

    /** Fireball 方法（默认开）。 */
    var fireball = true

    /** Packet 方法（默认开）。 */
    var packetMode = true

    /** Horizontal speed（默认 4.0，0.5..10.0）。 */
    var horizontalSpeed = 4.0

    /** Vertical speed（默认 2.0，0.5..10.0）。 */
    var verticalSpeed = 2.0

    /** Bobbing：None / Normal only / Always（默认 None）。 */
    var bobbing = "None"

    private enum class State {
        DAMAGE, XPROSS, DISABLER_PEARL, DISABLER_FIREBALL, DISABLER_HOVER, DISABLER
    }

    private enum class ProjectileStage { ROTATE, THROW, WAITING }

    private var state: State? = null
    private var timerBoostTimer = 0L
    private var xprossTimerMs = 0L
    private var moveSpeed = 0.0
    private var stage = 0
    private var groundTicks = 0
    private var projectileSlot = 0
    private var waitingForDamage = false
    private var projectileStage: ProjectileStage? = null
    private var hoverTimerMs = 0L

    // ---- 本地跟踪（对应源 EntityPlayer 补丁字段） ----

    /** 上一 tick 的水平位移（源的 lastDistance，PRE motion 时刷新）。 */
    private var lastDistance = 0.0

    /** 最近一次着地的 Y（源的 lastGroundY）。 */
    private var lastGroundY = 0.0

    /** XPROSS 悬浮包扣留状态（源的 blinker PacketHoldHandle）。 */
    private var blinking = false

    private val active get() = Fly.handleEvents() && Fly.mode == "WatchdogDamage"

    /** 随模式加载初始化全局跟踪器（客户端启动即注册常驻监听，
     * 否则珍珠投掷/出站包计数只有首次启用后才开始工作）。 */
    @Suppress("unused")
    private val trackerInitTouch = RewoundFlyTracker

    override fun onEnable() {
        state = null
        stage = 0
        moveSpeed = 0.0
        timerBoostTimer = 0L
        groundTicks = 0
        hoverTimerMs = 0L
        waitingForDamage = false
        projectileStage = null
        stopBlink()

        val player = mc.thePlayer ?: return

        if (disablerEnabled) {
            if (RewoundFlyTracker.isActive) {
                state = State.DISABLER
                return
            }
            if (tryStartDisabler(player)) return
        }
        if (player.onGround) {
            state = State.DAMAGE
        } else if (dropEnabled && player.fallDistance > 4.0f) {
            state = State.XPROSS
            xprossTimerMs = System.currentTimeMillis()
        }
    }

    /** 源 tryStartDisabler：悬停 / 珍珠 / 火球三条路径。 */
    private fun tryStartDisabler(player: EntityPlayerSP): Boolean {
        if (!player.onGround && packetMode && !PortedMove.isMoving() &&
            (player.fallDistance < 4.0f || !dropEnabled)
        ) {
            state = State.DISABLER_HOVER
            return true
        }
        if (enderPearl) {
            val slot = findHotbarSlot(player) { it != null && it.item is ItemEnderPearl }
            if (slot != null) {
                projectileSlot = slot
                projectileStage = ProjectileStage.ROTATE
                state = State.DISABLER_PEARL
                return true
            }
        }
        // 源此处还要求当前游戏为 BEDWARS（rewoundGameTracker），Lizz 无游戏跟踪 → 始终允许
        if (fireball) {
            val slot = findHotbarSlot(player) { it != null && it.item is ItemFireball }
            if (slot != null) {
                projectileSlot = slot
                projectileStage = ProjectileStage.ROTATE
                state = State.DISABLER_FIREBALL
                return true
            }
        }
        return false
    }

    /** 源 WorldUtil.findHotbarSlot：从当前槽位向上再向下扫描快捷栏。 */
    private fun findHotbarSlot(player: EntityPlayerSP, filter: (ItemStack?) -> Boolean): Int? {
        val current = player.inventory.currentItem
        for (slot in current until 9) if (filter(player.inventory.getStackInSlot(slot))) return slot
        for (slot in current downTo 0) if (filter(player.inventory.getStackInSlot(slot))) return slot
        return null
    }

    override fun onDisable() {
        val player = mc.thePlayer
        if (player != null) {
            val baseSpeed = baseSpeed()
            if (PortedMove.speed() > baseSpeed) {
                PortedMove.stop()
                if (state == State.DISABLER) player.motionY = 0.0
            }
        }
        stopBlink()
        SilentHotbar.resetSlot(this)
    }

    /** 源 canSelfDamage：Damage=None 时禁用（游戏状态门已省略）。 */
    private fun canSelfDamage() = damageMode != "None"

    /** 源 sendSelfDamagePackets：3.1 格（含跳跃药水加成）的 C04 上下包对 + 落地包。 */
    private fun sendSelfDamagePackets(player: EntityPlayerSP): Boolean {
        val x = player.posX
        val y = player.posY
        val z = player.posZ
        var fallDistance = 3.1
        if (player.isPotionActive(Potion.jump)) {
            fallDistance += (player.getActivePotionEffect(Potion.jump).amplifier + 1).toDouble()
        }
        val step = 0.06249
        val packets = ArrayList<Packet<*>>(128)
        var distance = 0.0
        while (distance < fallDistance) {
            packets.add(C04PacketPlayerPosition(x, y + step, z, false))
            packets.add(C04PacketPlayerPosition(x, y, z, false))
            distance += step
        }
        packets.add(C04PacketPlayerPosition(x, y, z, true))

        if (795 - packets.size > RewoundFlyTracker.sentPacketsCount) {
            for (pkt in packets) sendPacket(pkt, triggerEvent = false)
            val slowdown = packets.size * 0.001f
            mc.timer.timerSpeed = if (slowdown > 0.2f) 0.8f else 1.0f - slowdown
            // 源还有 mc.thePlayer.positionUpdateTicks = 0（1.8.9 私有字段，无访问器）→ 省略
            return true
        }
        return false
    }

    /** 源 calculateFriction。 */
    private fun calculateFriction(lastDistance: Double, baseSpeed: Double): Double = max(
        max(
            lastDistance - lastDistance / 160.0,
            lastDistance - (lastDistance - baseSpeed) / 33.3
        ),
        lastDistance - (lastDistance - baseSpeed) * 0.02
    )

    /** 源 MovementUtil.getSpeedWithPotion(0.2873, 0.2)。 */
    private fun baseSpeed(): Double {
        val player = mc.thePlayer ?: return 0.2873
        var base = 0.2873
        if (player.isPotionActive(Potion.moveSpeed)) {
            base *= 1.0 + 0.2 * (player.getActivePotionEffect(Potion.moveSpeed).amplifier + 1)
        }
        return base
    }

    /** 源 MovementUtil.getJumpMotion(0.42)：跳跃药水每级 +0.1。 */
    private fun jumpMotion(player: EntityPlayerSP): Double {
        var base = 0.42
        if (player.isPotionActive(Potion.jump)) {
            base += (player.getActivePotionEffect(Potion.jump).amplifier + 1) * 0.1f
        }
        return base
    }

    /** 源 WorldUtil.isOverVoid。 */
    private fun isOverVoid(player: EntityPlayerSP): Boolean =
        isFreeAt(player, player.posX, player.posY, player.posZ)

    /**
     * 源 WorldUtil.isFreeAt(feet, 0.0)：AABB 构造器自动排序边界，
     * 故盒子实际为「基岩层(0) 到脚底」的整柱 —— 无任何方块碰撞 = 下方悬空。
     */
    private fun isFreeAt(player: EntityPlayerSP, x: Double, y: Double, z: Double): Boolean {
        val halfWidth = player.width * 0.5f
        val box = AxisAlignedBB(x - halfWidth, y, z - halfWidth, x + halfWidth, 0.0, z + halfWidth)
        return !isColliding(player.worldObj, box)
    }

    /** 源 BlockUtil.isColliding：仅扫描方块碰撞盒（不含实体）。 */
    private fun isColliding(world: World, box: AxisAlignedBB): Boolean {
        val collisions = ArrayList<AxisAlignedBB>(1)
        val pos = BlockPos.MutableBlockPos()
        var x = MathHelper.floor_double(box.minX)
        while (x < MathHelper.floor_double(box.maxX + 1.0)) {
            var z = MathHelper.floor_double(box.minZ)
            while (z < MathHelper.floor_double(box.maxZ + 1.0)) {
                var y = MathHelper.floor_double(box.minY) - 1
                while (y < MathHelper.floor_double(box.maxY + 1.0)) {
                    pos.set(x, y, z)
                    val state = world.getBlockState(pos)
                    state.block.addCollisionBoxesToList(world, pos, state, box, collisions, null)
                    if (collisions.isNotEmpty()) return true
                    y++
                }
                z++
            }
            x++
        }
        return false
    }

    override fun onMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventState == EventState.PRE) {
            // 本地跟踪刷新（对应源 EntityPlayer.onUpdate 末尾的计算）
            lastDistance = sqrt(
                (player.posX - player.prevPosX) * (player.posX - player.prevPosX) +
                    (player.posZ - player.prevPosZ) * (player.posZ - player.prevPosZ)
            )
            if (player.onGround) lastGroundY = player.posY
        }

        when (state) {
            State.DAMAGE -> if (event.eventState == EventState.PRE) {
                if (stage == 0) {
                    if (player.onGround && lastDistance > 0.0) {
                        if (canSelfDamage() && sendSelfDamagePackets(player)) {
                            if (damageMode == "Wait") waitingForDamage = true else stage = 1
                        } else {
                            moveSpeed = 0.0
                            stage = 4
                        }
                        if (timerEnabled) timerBoostTimer = System.currentTimeMillis()
                    }
                } else {
                    // 源：偶数 groundTick 时给客户端位置一个 1e-9 级微偏移
                    if (groundTicks and 1 == 0) {
                        val offset = 1.0E-9 - Math.random() * 9.9999E-10
                        player.setPosition(player.posX, player.posY + offset, player.posZ)
                    }
                    groundTicks++
                }
                if (bobbing == "Normal only") player.cameraYaw = 0.1f
            }

            State.DISABLER_HOVER -> if (event.eventState == EventState.PRE && hoverTimerMs == 0L) {
                if (player.posY - lastGroundY < 0.1) {
                    val x = player.posX
                    val y = player.posY
                    val z = player.posZ
                    sendPacket(C04PacketPlayerPosition(x, y + 0.2, z, false), triggerEvent = false)
                    sendPacket(C04PacketPlayerPosition(x, y + 0.1, z, false), triggerEvent = false)
                    player.setPosition(x, y + 0.1, z)
                    event.y = player.posY
                }
                hoverTimerMs = System.currentTimeMillis()
                chat("Getting ready to fly.")
            }

            else -> Unit
        }

        if (event.eventState == EventState.PRE && bobbing == "Always") player.cameraYaw = 0.1f
    }

    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return
        val st = state ?: return

        when (st) {
            State.DAMAGE -> {
                val baseSpeed = baseSpeed()
                when (stage) {
                    0 -> moveSpeed = if (waitingForDamage) 0.0 else baseSpeed
                    1 -> {
                        player.motionY = 0.0
                        moveSpeed = flySpeed
                    }
                    2 -> {
                        player.motionY = jumpMotion(player)
                        moveSpeed *= 2.145
                    }
                    3 -> {
                        player.motionY = 0.0
                        moveSpeed = lastDistance - (lastDistance - baseSpeed) * 0.66
                    }
                    else -> {
                        moveSpeed = calculateFriction(lastDistance, baseSpeed) - 0.00001
                        player.motionY = 0.0
                        if (player.onGround && moveSpeed > baseSpeed) {
                            Fly.state = false
                            return
                        }
                    }
                }
                // 源 movementPredictor.historyTicks < 10（入世头 10 tick 不飞）→ ticksExisted 近似
                if (player.ticksExisted < 10) {
                    moveSpeed = 0.0
                    if (!waitingForDamage) moveSpeed = max(0.0, baseSpeed)
                } else if (!waitingForDamage) {
                    moveSpeed = max(moveSpeed, baseSpeed)
                }
                PortedMove.strafe(moveSpeed)
                if (stage >= 1) {
                    if (timerBoostTimer != 0L) {
                        if (System.currentTimeMillis() >= timerBoostTimer + timerTime) {
                            timerBoostTimer = 0L
                        } else {
                            mc.timer.timerSpeed = timerSpeed
                        }
                    }
                    stage++
                }
            }

            State.DISABLER -> {
                if (!RewoundFlyTracker.isActive) {
                    Fly.state = false
                } else {
                    var motionY = 0.0
                    if (mc.gameSettings.keyBindJump.isKeyDown) motionY += verticalSpeed
                    if (mc.gameSettings.keyBindSneak.isKeyDown) motionY -= verticalSpeed
                    player.motionY = motionY
                    PortedMove.strafe(horizontalSpeed)
                }
            }

            State.XPROSS -> {
                if (player.onGround || System.currentTimeMillis() >= xprossTimerMs + 3000L) {
                    Fly.state = false
                    return
                }
                if (player.health < 6.0f) {
                    Fly.state = false
                    chat("Automatically disabled due to being on low health.")
                    return
                }
                PortedMove.strafe(dropSpeed)
                player.motionY = 0.0
                val nextX = player.posX + player.motionX
                val nextY = player.posY + player.motionY
                val nextZ = player.posZ + player.motionZ
                val shouldBlink = isOverVoid(player) || isFreeAt(player, nextX, nextY, nextZ)
                if (shouldBlink && !blinking) {
                    blinking = true
                } else if (!shouldBlink && blinking) {
                    stopBlink()
                }
            }

            State.DISABLER_PEARL -> if (projectileStage != ProjectileStage.WAITING) PortedMove.stop()
            State.DISABLER_FIREBALL -> PortedMove.stop()
            State.DISABLER_HOVER -> {
                PortedMove.stop()
                player.motionY = 0.0
            }
        }

        event.x = player.motionX
        event.y = player.motionY
        event.z = player.motionZ
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return

        if (event.eventType == EventState.SEND) {
            // XPROSS blink：扣留全部出站包（源的 blinker PacketHoldHandle，filter=null）
            if (blinking) BlinkUtils.blink(event.packet, event, sent = true, receive = false)
            return
        }

        when (event.packet) {
            is S08PacketPlayerPosLook -> {
                if (state == State.DISABLER_HOVER && hoverTimerMs != 0L) {
                    if (System.currentTimeMillis() < hoverTimerMs + 3920L) {
                        Fly.state = false
                        chat("Failed to disable movement checks.")
                    } else {
                        RewoundFlyTracker.trigger(RewoundFlyTracker.TYPE_DISABLER)
                        state = State.DISABLER
                        chat("You can fly for 3 seconds.")
                    }
                } else if (state == State.DAMAGE) {
                    // 源 TeleportEvent(pre)：DAMAGE 期间被拉回 → 自动关闭
                    Fly.state = false
                    chat("Automatically disabled due to lag back.")
                }
            }

            is S19PacketEntityStatus -> {
                val pkt = event.packet
                if (state == State.DAMAGE && stage == 0 && waitingForDamage &&
                    pkt.opCode == 2.toByte() && pkt.entityId == player.entityId
                ) {
                    waitingForDamage = false
                    stage = 1
                }
            }

            else -> Unit
        }
    }

    /** 源 onUpdate(CancellablePlayerUpdateEvent)：state 为 null 时自动关闭。 */
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (state == null) {
            Fly.state = false
            return
        }
        // 投掷阶段推进（源挂 IngameActionEvent：每游戏 tick 一次，先转视角再模拟右键）
        if (state == State.DISABLER_PEARL || state == State.DISABLER_FIREBALL) {
            when (projectileStage) {
                ProjectileStage.ROTATE -> {
                    val yaw = RotationUtils.currentRotation?.yaw ?: player.rotationYaw
                    RotationUtils.currentRotation = Rotation(yaw, 90f)
                    spoofProjectileSlot()
                    projectileStage = ProjectileStage.THROW
                }

                ProjectileStage.THROW -> {
                    val yaw = RotationUtils.currentRotation?.yaw ?: player.rotationYaw
                    RotationUtils.currentRotation = Rotation(yaw, 90f)
                    throwProjectile(player)
                    projectileStage = ProjectileStage.WAITING
                }

                else -> Unit
            }
        }
    }

    /** 源 spoofProjectileSlot（SlotSpoofer → SilentHotbar 静默槽位等价）。 */
    private fun spoofProjectileSlot() {
        SilentHotbar.selectSlotSilently(
            this,
            projectileSlot,
            immediate = true,
            render = false,
            resetManually = true
        )
    }

    /** 源 THROW 阶段的模拟右键（SwingMouseClick）→ 直接对槽内物品发起使用。 */
    private fun throwProjectile(player: EntityPlayerSP) {
        val stack = player.inventory.getStackInSlot(projectileSlot) ?: return
        spoofProjectileSlot()
        player.sendUseItem(stack)
    }

    /** RewoundDisablerEvent 等价：触发后进入 DISABLER（由 RewoundFlyTracker 同步调用）。 */
    fun onDisablerTriggered(type: String) {
        if (!active) return
        if (state == State.DISABLER_PEARL && projectileStage == ProjectileStage.WAITING &&
            type == RewoundFlyTracker.TYPE_ENDER_PEARL
        ) {
            // 源文案为 "You an fly for 3 seconds."（错别字），此处修正拼写
            chat("You can fly for 3 seconds.")
        }
        state = State.DISABLER
    }

    private fun stopBlink() {
        if (blinking) {
            blinking = false
            BlinkUtils.syncReceived()
        }
    }
}
