/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Velocity「Watchdog-ProtectionNew」模式 —— 源客户端 NewVelocity 的
 * PolarJump / Intave / Intave2 / AirJumpReset / Prediction 五个子模式合并为一：
 * - PolarJump：受击后（随机 hurtTime 7..10）疾跑/地面/移动时钩子跳，重置击退
 * - Intave：受伤期间按包过滤 Blink（S12/C03/C02/C08/C07）+ 攻击触发的相位衰减 +
 *           IntaveJumpReset（可强制疾跑）
 * - Intave2：攻击触发的 XZ 衰减（因子随连续攻击递减，最低 MinFactor）
 * - AirJumpReset：空中 JumpReset（hurtTime 9 + 收到 S12 + 移动 + 前进）
 * - Prediction：受伤窗口 Blink 出站包 + 窗口内 BlinkTicks 次静默攻击 +
 *              收到自己 S12 时地面疾跑钩子跳
 * 五个组件均可用各自的开关单独启停（默认全开 = 源五个模式合并体）。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocitymodes

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.KeepSprint
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.realMotionX
import net.ccbluex.liquidbounce.utils.client.realMotionY
import net.ccbluex.liquidbounce.utils.client.realMotionZ
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.changeSprint
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.reduceXZ
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.roundToPlacesIfNeeded
import net.ccbluex.liquidbounce.utils.firefly.general.SomeUtil.runAttack
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

object WatchdogProtectionNewVelocityMode : VelocityMode("Watchdog-ProtectionNew") {

    // ==== 组件开关（由 Velocity 模块同步；默认全开 = 五模式合并体） ====
    var polarJumpEnabled = true
    var intaveEnabled = true
    var intave2Enabled = true
    var airJumpResetEnabled = true
    var predictionEnabled = true

    // ==== Prediction ====
    var blinkTicks = 5

    // ==== Intave ====
    var blinkWorkMaxDistance = 3.5f
    var maxBlinkTicks = 10
    var intaveJumpReset = true
    var intaveJumpResetSprint = false
    var intaveJumpResetNeedForward = true
    var extraC0APerReduce = false
    var extraPacketCount = 1
    var moreReduce = false
    var maxMoreReduce = 3
    var onlyWhenNeed = true
    var intaveSafe = true

    // ==== Intave2 ====
    var minFactor = 0.4f

    // ==== 工作时机（源 AllowWorkWhen 的 OnGround/InAir 两项） ====
    var workOnGround = true
    var workInAir = true

    var debugMessage = false

    enum class IntavePhase {
        PHASE_1, PHASE_2, PHASE_3, PHASE_4, PHASE_5, PHASE_6
    }

    private val triggeredPhases = mutableSetOf<IntavePhase>()

    @JvmField
    var canCancelHitSlow = false

    var hasReceivedVelocity = false
        private set

    var globalTarget: EntityLivingBase? = null
        private set

    // PolarJump
    private var polarHurtTime = nextInt(7, 10)

    // Intave 状态
    var intaveReversed = false
        private set
    var moreReduceTimes = 0
        private set
    var intaveReduceTimes = 0
        private set
    private var canOutPutMessage = false
    internal var shouldBlink = false
    internal var lastBlinkState = false

    // Intave2 状态
    private var intave2ReduceCounter = 0

    // Prediction 状态
    private var preBlinking = false
    private var preShouldBlink = false
    private var preShouldAttack = false

    // 包携带的 motion（intaveReduce 判断击退是否已反转用）
    var packetMotionX = 0.0
        private set
    private var packetMotionY = 0.0
    var packetMotionZ = 0.0
        private set

    override fun onEnable() {
        hasReceivedVelocity = false
        triggeredPhases.clear()
        moreReduceTimes = 0
        intaveReduceTimes = 0
        intaveReversed = false
        shouldBlink = false
        lastBlinkState = false
        canOutPutMessage = true
        intave2ReduceCounter = 0
        preBlinking = false
        preShouldBlink = false
        preShouldAttack = false
        polarHurtTime = nextInt(7, 10)
    }

    override fun onDisable() {
        // 源 onDisable 不清理 blink；这里补上，防止关模块后包一直被扣住（服务端视角玩家卡死）
        if (BlinkUtils.isBlinking && (shouldBlink || preBlinking)) {
            BlinkUtils.unblink()
        }
        shouldBlink = false
        lastBlinkState = false
        preBlinking = false
        preShouldBlink = false
        preShouldAttack = false
        hasReceivedVelocity = false
    }

    override fun onAttack(target: Entity?) {
        val player = mc.thePlayer ?: return
        globalTarget = target as? EntityLivingBase

        if (!canWorkNow()) return

        // ---- Intave：按 hurtTime 分相位衰减（攻击触发） ----
        if (intaveEnabled) {
            when (player.hurtTime) {
                10 -> intaveReduce(0, intaveSafe)
                9, 6, 3 -> intaveReduce(1, intaveSafe)
                8, 5, 2 -> intaveReduce(2, intaveSafe)
                7, 4, 1 -> intaveReduce(3, intaveSafe)
            }
        }

        // ---- Intave2：攻击触发的递减 XZ 衰减 ----
        if (intave2Enabled) {
            val reduceFactor = max(1 - (intave2ReduceCounter * 0.1), minFactor.toDouble())
            reduceXZ(reduceFactor)
            debugMessage("Intave2Reduce")
            intave2ReduceCounter++
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (player.hurtTime == 0 && hasReceivedVelocity) hasReceivedVelocity = false

        if (!canWorkNow()) return

        // ---- Intave ----
        if (intaveEnabled) {
            val shouldStop = (player.hurtTime == 10 - maxBlinkTicks) || maxBlinkTicks == 0
            if (shouldStop && lastBlinkState) {
                stopIntaveBlink()
            }
            val checkSprint = !intaveJumpResetSprint
            if (intaveJumpReset) {
                if (shouldJumpReset(
                        checkSprint = checkSprint, checkOnGround = true,
                        checkMoving = intaveJumpResetNeedForward, needReceivedS12 = true,
                        needForward = intaveJumpResetNeedForward
                    )
                ) {
                    if (intaveJumpResetSprint) changeSprint(setState = true, forceChange = true, sendPacketToServer = true)
                    player.jump()

                    debugMessage("Jump | ${player.hurtTime}")
                }
            }

            if (packetMotionValid() && IntavePhase.PHASE_1 in triggeredPhases &&
                IntavePhase.PHASE_2 in triggeredPhases && IntavePhase.PHASE_3 in triggeredPhases &&
                player.hurtTime != 0 && canOutPutMessage
            ) {
                debugMessage(
                    "$packetMotionX $packetMotionZ -> ${playerNowMotionOutPut(
                        x = true, y = false, z = true
                    )}"
                )
                canOutPutMessage = false
            }
        }

        // ---- Prediction ----
        if (predictionEnabled) {
            preShouldBlink = player.hurtTime in (10 - blinkTicks)..10 && hasReceivedVelocity
        }

        // ---- AirJumpReset ----
        if (airJumpResetEnabled) {
            if (shouldJumpReset(checkSprint = false, checkOnGround = false)) {
                player.jump()
                debugMessage("Jump")
                hasReceivedVelocity = false
            }
        }

        // ---- PolarJump ----
        if (polarJumpEnabled) {
            if (shouldJumpReset(
                    checkSprint = true, checkOnGround = true, checkMoving = true,
                    needReceivedS12 = true, polarMode = true
                )
            ) {
                player.tryJump()
                polarHurtTime = nextInt(7, 10)
                if (player.hurtTime == 0) hasReceivedVelocity = false
            }
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (!canWorkNow()) return
        if (event.isCancelled) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is S12PacketEntityVelocity && isValidS12Packet(packet)) {
            packetMotionX = roundToPlacesIfNeeded(packet.realMotionX)
            packetMotionY = roundToPlacesIfNeeded(packet.realMotionY)
            packetMotionZ = roundToPlacesIfNeeded(packet.realMotionZ)

            if (polarJumpEnabled || airJumpResetEnabled) hasReceivedVelocity = true

            if (intaveEnabled) {
                hasReceivedVelocity = true
                triggeredPhases.clear()
                moreReduceTimes = 0
                canOutPutMessage = true
                shouldBlink = true
                intaveReversed = false
                intaveReduceTimes = 0
            }

            if (intave2Enabled) intave2ReduceCounter = 0

            if (predictionEnabled) {
                preShouldAttack = true
                hasReceivedVelocity = true
            }
        }

        // ---- Intave：按包过滤的 blink ----
        if (intaveEnabled) {
            val blinkDistanceCheck = when {
                blinkWorkMaxDistance == 0.0f -> true
                KillAura.target == null -> false
                else -> getDistance(KillAura.target!!) <= blinkWorkMaxDistance
            }
            val matchesFilter = packet is S12PacketEntityVelocity || packet is C03PacketPlayer ||
                    packet is C02PacketUseEntity || packet is C08PacketPlayerBlockPlacement ||
                    packet is C07PacketPlayerDigging

            if (shouldBlink && player.hurtTime > 0 && blinkDistanceCheck && maxBlinkTicks > 0 && matchesFilter) {
                BlinkUtils.blink(packet, event, sent = true, receive = true)

                if (!lastBlinkState) debugMessage("Blinking")
                lastBlinkState = true
            } else if (!blinkDistanceCheck && BlinkUtils.isBlinking) {
                BlinkUtils.unblink()
                if (lastBlinkState) debugMessage("Out of range,stop blink")
                lastBlinkState = false
            }
        }

        // ---- Prediction ----
        if (predictionEnabled) {
            val isSelfVelocity = packet is S12PacketEntityVelocity && packet.entityID == player.entityId

            if (isSelfVelocity && isValidS12Packet(packet as S12PacketEntityVelocity)) {
                preShouldAttack = true
                hasReceivedVelocity = true
            }
            if (isSelfVelocity && player.onGround && player.isSprinting &&
                !mc.gameSettings.keyBindJump.isKeyDown
            ) player.jump()
            if (preShouldBlink) {
                BlinkUtils.blink(packet, event, sent = true, receive = false)
                preBlinking = true
                if (preShouldAttack) {
                    runAttack(attackCount = blinkTicks, silentAttack = true, fakeSwing = true)
                    preShouldAttack = false
                }
            }
            if (!preShouldBlink && preBlinking) BlinkUtils.unblink()
        }
    }

    // ===================== 共用逻辑（源 NewVelocity 原样移植） =====================

    private fun shouldJumpReset(
        checkSprint: Boolean, checkOnGround: Boolean? = true, checkMoving: Boolean? = true,
        needReceivedS12: Boolean? = true, needForward: Boolean? = true, polarMode: Boolean? = false
    ): Boolean {
        val player = mc.thePlayer ?: return false
        val jumpHurtTime = if (polarMode ?: false) nextInt(7, 10) else 9
        return player.hurtTime == jumpHurtTime &&
                (!(needReceivedS12 ?: true) || hasReceivedVelocity) &&
                (!checkSprint || player.isSprinting) &&
                (!(checkOnGround ?: true) || player.onGround) &&
                (!(checkMoving ?: true) || player.isMoving) &&
                (!(needForward ?: true) || player.moveForward > 0.707f) &&
                !mc.gameSettings.keyBindJump.isKeyDown
    }

    private fun canWorkNow(): Boolean {
        val player = mc.thePlayer ?: return false
        val onGround = player.onGround
        val inAir = !onGround

        return when {
            workOnGround && workInAir -> true
            workOnGround -> onGround
            workInAir -> inAir
            else -> true
        }
    }

    private fun isValidS12Packet(packet: S12PacketEntityVelocity): Boolean {
        val player = mc.thePlayer ?: return false
        return (packet.realMotionX != 0.0 &&
                packet.realMotionY != 0.0 &&
                packet.realMotionZ != 0.0 &&
                packet.entityID == player.entityId
                )
    }

    private fun debugMessage(message: Any?) {
        if (!debugMessage) return
        if (message == null) return
        chat(message.toString())
    }

    private fun packetMotionValid(): Boolean {
        return packetMotionX != 0.0 &&
                packetMotionZ != 0.0 &&
                packetMotionY != 0.0
    }

    @Suppress("SameParameterValue")
    private fun playerNowMotionOutPut(x: Boolean, y: Boolean, z: Boolean): String {
        val player = mc.thePlayer ?: return ""
        val motions = mutableListOf<String>()

        val mx = roundToPlacesIfNeeded(player.motionX)
        val my = roundToPlacesIfNeeded(player.motionY)
        val mz = roundToPlacesIfNeeded(player.motionZ)
        if (x) motions.add(mx.toString())
        if (y) motions.add(my.toString())
        if (z) motions.add(mz.toString())

        return motions.joinToString(" ")
    }

    fun knockBackIsNegated(xMotion: Double, zMotion: Double): Boolean {
        val player = mc.thePlayer ?: return false
        val motionX = player.motionX
        val motionZ = player.motionZ

        val isXNegated = sign(motionX) != sign(xMotion)
        val isZNegated = sign(motionZ) != sign(zMotion)

        return isXNegated && isZNegated
    }

    internal fun stopIntaveBlink() {
        if (shouldBlink && BlinkUtils.isBlinking) {
            runAttack(silentAttack = true, fakeSwing = true)
            BlinkUtils.unblink()
            shouldBlink = false
            lastBlinkState = false
            debugMessage("Unblink | ${mc.thePlayer?.hurtTime}")
        }
    }

    private fun getDistance(target: EntityLivingBase): Double {
        return mc.thePlayer?.getDistanceToEntityBox(target) ?: 0.0
    }

    private fun intaveReduce(phase: Int, safe: Boolean) {
        val player = mc.thePlayer ?: return

        fun intaveReduceTrigger(phase: Int) {
            when (phase) {
                1 -> triggeredPhases.add(IntavePhase.PHASE_1)
                2 -> triggeredPhases.add(IntavePhase.PHASE_2)
                3 -> triggeredPhases.add(IntavePhase.PHASE_3)
                4 -> triggeredPhases.add(IntavePhase.PHASE_4)
                5 -> triggeredPhases.add(IntavePhase.PHASE_5)
                6 -> triggeredPhases.add(IntavePhase.PHASE_6)
            }
        }
        if (safe && globalTarget !is net.minecraft.entity.player.EntityPlayer) return
        stopIntaveBlink()
        if (knockBackIsNegated(packetMotionX, packetMotionZ) && onlyWhenNeed) {
            if (intaveReversed && intaveReduceTimes in 2..4) {
                canCancelHitSlow = false
                reduceXZ(0.6)
                return
            }
            // 源此处查询 KeepSprint.shouldKeepSprint / KillAura.shouldKeepSprint，
            // lizz 按各自模块开关近似（见 KeepSprint/KillAura 的 shouldKeepSprint 注释）
            if (!KeepSprint.shouldKeepSprint && !KillAura.shouldKeepSprint) {
                canCancelHitSlow = true
                return
            }
            return
        } else canCancelHitSlow = false

        if (!intaveReversed && intaveReduceTimes == 1 && player.hurtTime in 1..9) {
            if (knockBackIsNegated(packetMotionX, packetMotionZ)) return
            val bps = currentBps()
            if (bps > 2.805) {
                if (calculateAngleDifferenceSafe() > 15.0) player.setBPSToSafe(-min(5.612, bps * 0.6))
                intaveReversed = true
                debugMessage("IntaveReverse | ${player.hurtTime}")
            }
        } else if (player.hurtTime != 10) intaveReduceTimes++ else intaveReduceTimes == 1

        if (intaveReduceTimes > 5 && intaveReversed) return

        when (phase) {
            0 -> {
                runAttack(silentAttack = true)
                debugMessage("IntaveReduce | ${player.hurtTime}")
            }
            1 -> {
                when {
                    !getTriggeredPhase(1) -> {
                        if (extraC0APerReduce) repeat(extraPacketCount) { sendPacket(C0APacketAnimation()) }
                        reduceXZ(0.6)

                        debugMessage("IntaveReduce | ${player.hurtTime}")
                        intaveReduceTrigger(1)
                        return
                    }
                    !getTriggeredPhase(4) -> {
                        if (extraC0APerReduce) repeat(extraPacketCount) { sendPacket(C0APacketAnimation()) }
                        if (calculateAngleDifferenceSafe() < 15.0)
                            reduceXZ(1.5)
                        else reduceXZ(0.6)
                        debugMessage("IntaveReduce | ${player.hurtTime}")
                        intaveReduceTrigger(4)
                        return
                    }
                }
                if (moreReduceTimes < maxMoreReduce && moreReduce) {
                    moreReduceTimes++
                    reduceXZ(getMoreReduceFactor(moreReduceTimes))
                }
            }
            2 -> {
                when {
                    !getTriggeredPhase(2) -> {
                        if (extraC0APerReduce) repeat(extraPacketCount) { sendPacket(C0APacketAnimation()) }
                        reduceXZ(0.36)
                        debugMessage("IntaveReduce | ${player.hurtTime}")
                        intaveReduceTrigger(2)
                        return
                    }
                    !getTriggeredPhase(5) -> {
                        if (extraC0APerReduce) repeat(extraPacketCount) { sendPacket(C0APacketAnimation()) }
                        if (calculateAngleDifferenceSafe() < 15.0)
                            reduceXZ(1.5)
                        else reduceXZ(0.6)
                        debugMessage("IntaveReduce | ${player.hurtTime}")
                        intaveReduceTrigger(5)
                        return
                    }
                }
                if (moreReduceTimes < maxMoreReduce && moreReduce) {
                    moreReduceTimes++
                    reduceXZ(getMoreReduceFactor(moreReduceTimes))
                }
            }
            3 -> {
                when {
                    !getTriggeredPhase(3) -> {
                        if (extraC0APerReduce) repeat(extraPacketCount) { sendPacket(C0APacketAnimation()) }
                        reduceXZ(0.216)
                        debugMessage("IntaveReduce | ${player.hurtTime}")
                        intaveReduceTrigger(3)
                        return
                    }
                    !getTriggeredPhase(6) -> {
                        if (extraC0APerReduce) repeat(extraPacketCount) { sendPacket(C0APacketAnimation()) }
                        if (calculateAngleDifferenceSafe() < 15.0)
                            reduceXZ(1.5)
                        else reduceXZ(0.6)
                        debugMessage("IntaveReduce | ${player.hurtTime}")
                        intaveReduceTrigger(6)
                        return
                    }
                }
                if (moreReduceTimes < maxMoreReduce && moreReduce) {
                    moreReduceTimes++
                    reduceXZ(getMoreReduceFactor(moreReduceTimes))
                }
            }
        }
    }

    /** 源 mc.thePlayer.bps：sqrt(vx²+vz²)*20 */
    private fun currentBps(): Double {
        val player = mc.thePlayer ?: return 0.0
        return sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ) * 20.0
    }

    private fun EntityPlayerSP.setBPSToSafe(targetBPS: Double) {
        val bps = currentBps()
        if (bps != 0.0) reduceXZ(targetBPS / bps)
    }

    private fun calculateAngleDifferenceSafe(): Double {
        val player = mc.thePlayer ?: return 0.0
        val movementAngle = Math.toDegrees(atan2(player.motionZ, player.motionX))
        val normalizedPlayerYaw = (player.rotationYaw % 360 + 360) % 360
        var angleDifference = abs(movementAngle - normalizedPlayerYaw)
        if (angleDifference > 180) {
            angleDifference = 360 - angleDifference
        }
        return angleDifference
    }

    private fun getMoreReduceFactor(reduceCount: Int): Double {
        return when (reduceCount) {
            1 -> 0.5 / 0.6
            2 -> 0.75
            else -> {
                val baseFactor = 0.7
                val reduction = (reduceCount - 3) * 0.05
                max(0.0, baseFactor - reduction)
            }
        }
    }

    private fun getTriggeredPhase(phase: Int): Boolean {
        return when (phase) {
            1 -> IntavePhase.PHASE_1 in triggeredPhases
            2 -> IntavePhase.PHASE_2 in triggeredPhases
            3 -> IntavePhase.PHASE_3 in triggeredPhases
            4 -> IntavePhase.PHASE_4 in triggeredPhases
            5 -> IntavePhase.PHASE_5 in triggeredPhases
            6 -> IntavePhase.PHASE_6 in triggeredPhases
            else -> false
        }
    }
}
