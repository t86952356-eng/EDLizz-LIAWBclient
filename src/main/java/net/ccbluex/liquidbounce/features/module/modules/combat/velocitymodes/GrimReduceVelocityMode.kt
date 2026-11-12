/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Velocity「GrimReduce」模式 —— 源实现等价移植：
 * 收到自己的 S12 后开始持包（S12/S32/S14/S00/S13 系列全部扣住），
 * 满足释放条件（落地 / Delay Plus 窗口 / tR>25 超时）时一次性补发；
 * 持包期间在目标朝向上执行 Grim 减速（朝向锁 90° 俯视 + 额外攻击）
 * 与 Rotate（早期看目标 + 输入矫正）。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold2
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.toRotation
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.server.S00PacketKeepAlive
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S14PacketEntity
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import kotlin.math.min

object GrimReduceVelocityMode : VelocityMode("GrimReduce") {

    // ---- 选项（由 Velocity 同步） ----
    var reduceTicks = 14
    var teleportDisableTicks = 2
    var onSwingDisableOnAura = true
    var rotations = false
    var delayPlus = false
    var extraHit = true
    var stopSprint = true
    var range = 8
    var delayTillGround = true

    /** 源还读取父模块的 On Swing 开关（共享值）。 */
    var onSwing = false

    var holding = false
        private set

    val delayedPackets = ArrayList<Packet<*>>()
    private var shouldReduce = false

    /** Jump Reset 由收到的 S12 触发，在 MoveInput 中按下跳跃。 */
    private var pendingJumpReset = false

    /** 源 thePlayer.aY：距上次攻击的 tick（源在 AttackEvent 复位为 0）。 */
    private var ticksSinceAttack = Int.MAX_VALUE

    // ---- 持包与释放 ----

    override fun onEnable() {
        holding = false
        delayedPackets.clear()
        shouldReduce = false
        pendingJumpReset = false
        ticksSinceAttack = Int.MAX_VALUE
    }

    override fun onDisable() {
        flush()
    }

    override fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        if (event.isCancelled) return
        val packet = event.packet
        if (event.eventType != EventState.RECEIVE) return

        // 源：flushingPackets / ticksSinceTeleport < 3 / 网中 / (!delayTillGround && !delayPlus) 时跳过
        if (PortedTrackers.ticksSinceTeleport < 3) return
        if (player.isInWeb) return
        if (!delayTillGround && !delayPlus) return

        when (packet) {
            is S12PacketEntityVelocity -> {
                if (packet.entityID == player.entityId) {
                    pendingJumpReset = true
                    delayedPackets.add(packet)
                    holding = true
                    event.cancelEvent()
                }
            }

            is S32PacketConfirmTransaction, is S14PacketEntity,
            is S00PacketKeepAlive -> {
                if (holding) {
                    delayedPackets.add(packet)
                    event.cancelEvent()
                }
            }
            // 源的 z（S13 实体移动族）分支同样只在 dj 时持包 —— 这里以包名前缀近似
            else -> {
                if (holding) {
                    val name = packet.javaClass.simpleName
                    if (name.startsWith("S13") || name.startsWith("S15") ||
                        name.startsWith("S16") || name.startsWith("S17")
                    ) {
                        delayedPackets.add(packet)
                        event.cancelEvent()
                    }
                }
            }
        }
    }

    /** 源 onPreUpdate（EventLink(0)，最先执行）：减速决策 + 朝向 + 额外攻击。 */
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        ticksSinceAttack = min(ticksSinceAttack + 1, Int.MAX_VALUE - 1)

        shouldReduce = false

        val killAuraTarget: EntityLivingBase? = if (KillAura.handleEvents()) KillAura.target else null
        val closestTarget = killAuraTarget ?: getClosest(PortedMove.targetsInRange(range.toDouble()))
            ?: return

        // Rotate：受击后 7 tick 内看目标（Scaffold 开启时跳过）
        if (PortedTrackers.ticksSinceVelocity < 7 && rotations && !Scaffold.handleEvents() && !Scaffold2.handleEvents()) {
            lookAt(closestTarget)
        }

        // On Swing：不在挥动时若 KillAura 有目标且开关开 → 继续；否则跳过
        if (onSwing && !player.isSwingInProgress) {
            if (KillAura.target == null) return
            if (!onSwingDisableOnAura) return
        }

        if (player.ticksExisted <= 20) return

        if (PortedTrackers.ticksSinceVelocity <= reduceTicks &&
            !PortedTrackers.bad(switch = false, swing = false, use = false, place = false, inventory = true) &&
            !Scaffold.handleEvents() && !Scaffold2.handleEvents() &&
            PortedTrackers.ticksSinceTeleport > teleportDisableTicks
        ) {
            shouldReduce = true
        }

        // 朝向锁定：对 KillAura 目标（或最近目标）下压 90° 俯视
        if (KillAura.handleEvents() && KillAura.target != null) {
            val target = KillAura.target!!
            val mouse = mc.objectMouseOver
            if (!(player.getDistanceToEntityBox(target) <= 3.0 ||
                    mouse != null && mouse.entityHit === target || range <= 3)
            ) {
                RotationUtils.currentRotation =
                    Rotation(player.rotationYaw, (90.0 - Math.random() * 0.1).toFloat())
            }
        } else {
            val target = PortedMove.targetsInRange(range.toDouble()).firstOrNull() ?: return
            val eyes = player.getPositionEyes(1.0f)
            val box = target.entityBoundingBox.expand(0.1, 0.1, 0.1)
            val center = Vec3(
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5
            )
            if (!(closestDistance(eyes, box) <= 3.0) &&
                PortedTrackers.ticksSinceVelocity <= reduceTicks &&
                !PortedTrackers.bad(switch = false, swing = false, use = false, place = false, inventory = true)
            ) {
                RotationUtils.currentRotation =
                    Rotation(player.rotationYaw, (90.0 - Math.random() * 0.2).toFloat())
            }
        }

        // 额外攻击（extraHit）： ViaVersion > 1.8 先 attack 后 C0A；1.8 反之
        if (PortedTrackers.ticksSinceVelocity > reduceTicks + 1) return
        if (PortedTrackers.bad(switch = false, swing = false, use = false, place = false, inventory = true)) return
        if (Scaffold.handleEvents() || Scaffold2.handleEvents()) return
        if (PortedTrackers.ticksSinceTeleport <= teleportDisableTicks) return
        if (!extraHit) return

        mc.playerController?.attackEntity(player, closestTarget)
        PacketUtils.sendPacket(C0APacketAnimation(), triggerEvent = false)
    }

    /** 源 onPreUpdate（EventLink(2)）：释放条件；PreMotion 的 pendingJumpReset 清理合并到本 tick 末尾。 */
    override fun onTick() {
        val player = mc.thePlayer ?: return

        if (player.onGround && holding && !delayPlus ||
            PortedTrackers.ticksSinceTeleport < 3 && holding ||
            delayPlus && (player.onGround || !delayTillGround) && holding &&
            (KillAura.target == null || player.getDistanceToEntityBox(KillAura.target!!) < 2.7 || ticksSinceAttack == 1)
        ) {
            release()
        }

        if (PortedTrackers.itemUseTicks > 25 && holding) {
            release()
        }

        // 源 PreMotionEvent：每 tick 的位置包阶段清空跳跃复位标记（在 MoveInput 之后）
        pendingJumpReset = false
    }

    override fun onMoveInput(event: MovementInputEvent) {
        val player = mc.thePlayer ?: return

        if (pendingJumpReset) {
            event.originalInput.jump = true
        }

        if (PortedTrackers.ticksSinceVelocity < 7 && rotations &&
            !Scaffold.handleEvents() && !Scaffold2.handleEvents()
        ) {
            event.originalInput.moveForward = 1f
            event.originalInput.moveStrafe = 0f
        }
    }

    override fun onAttack(target: Entity?) {
        // 源 EntityTickComponent：AttackEvent → thePlayer.aY = 0
        ticksSinceAttack = 0
    }

    // ---- 内部 ----

    private fun release() {
        holding = false
        flush()
    }

    private fun flush() {
        if (delayedPackets.isEmpty()) return
        val packets = delayedPackets.toList()
        delayedPackets.clear()
        packets.forEach { PacketUtils.handlePackets(it) }
    }

    private fun getClosest(list: List<EntityLivingBase>): EntityLivingBase? {
        val player = mc.thePlayer ?: return null
        return list.minByOrNull { player.getDistanceToEntityBox(it) }
    }

    /** 源 lookAt：朝向目标 yaw、保持当前 pitch。 */
    private fun lookAt(entity: Entity) {
        val player = mc.thePlayer ?: return
        val rotation = toRotation(entity.positionVector, false, player)
        RotationUtils.currentRotation = Rotation(rotation.yaw, player.rotationPitch)
    }

    private fun closestDistance(eyes: Vec3, box: AxisAlignedBB): Double {
        return eyes.distanceTo(closestPointOnBox(eyes, box))
    }

    private fun closestPointOnBox(vec: Vec3, box: AxisAlignedBB): Vec3 {
        val x = clamp(vec.xCoord, box.minX, box.maxX)
        val y = clamp(vec.yCoord, box.minY, box.maxY)
        val z = clamp(vec.zCoord, box.minZ, box.maxZ)
        return Vec3(x, y, z)
    }

    private fun clamp(v: Double, min: Double, max: Double): Double =
        if (v < min) min else kotlin.math.min(v, max)
}
