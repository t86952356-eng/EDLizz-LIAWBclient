/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.criticalsmodes

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.network.NetworkPlayerInfo
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import net.minecraft.world.WorldSettings

/**
 * Criticals「WatchdogNCP-Packet」模式（整体移植 Rewound 客户端 RewoundCriticalsMode）：
 * 服务端视角暴击 —— 跟踪服务端最后上报的 Y/onGround（lastReported*），
 * 累计服务端侧下落距离；着地且未加过偏移时给 PRE motion 包 +0.0301 偏移
 * （服务端看到悬空 → 判暴击），predictedFall >= 3.0 时上报 onGround=true；
 * 服务端认为空中且有下落时攻击 → 客户端补暴击动画。
 *
 * 与源的已记录差异：
 * - lastReportedPosY/OnGround 由 Lizz 的 MovementUtils.serverY/serverOnGround 跟踪
 *   （同样只在含位置的 C03 上报时更新 Y，onGround 每包更新，语义一致）；
 * - 源 jumped 标志由 RewoundSpeedMode/RewoundJesusMode 置位（未移植的模块），
 *   这里保留字段与复位逻辑，恒为 false；
 * - WorldResetEvent → 世界引用变化检测（FlyMode 同款手法）；
 * - Features.CRITICALS_REWOUND 服务器功能门 → 移除；
 * - BreakerModule 破坏中检查（Rewound 专属模块）→ 只保留 getIsHittingBlock。
 */
object WatchdogNCPPacketCriticalsMode : CriticalsMode("WatchdogNCP-Packet") {

    /** 已应用偏移标志（源的 offsetApplied）。 */
    var offsetApplied = false

    /** 源的 jumped 标志（由未移植的 Rewound Speed/Jesus 模块置位，恒 false）。 */
    var jumped = false

    /** 服务端侧累计下落距离（源的 serverFallDistance）。 */
    var serverFallDistance = 0.0

    /** 上一次服务端 Y（源的 lastServerY）。 */
    var lastServerY = 0.0

    /** 世界引用（变化时复位，等价源的 WorldResetEvent）。 */
    private var lastWorld: WorldClient? = null

    override fun onEnable() {
        offsetApplied = false
        jumped = false
        serverFallDistance = 0.0
        lastServerY = 0.0
        lastWorld = mc.theWorld
    }

    override fun onAttack(event: AttackEvent) {
        val player = mc.thePlayer ?: return
        val target = event.targetEntity ?: return

        // 源 onAttack(AttackEntityEvent.pre)：服务端认为空中且有下落 → 客户端暴击动画
        if (!MovementUtils.serverOnGround && serverFallDistance > 0.0 && canCritical(player, target)) {
            player.onCriticalHit(target)
        }
    }

    override fun onUpdate() {
        // 源 onUpdate(CancellablePlayerUpdateEvent)：每 tick 复位 jumped
        jumped = false

        // 源 onWorldReset(WorldResetEvent)
        val world = mc.theWorld
        if (world !== lastWorld) {
            lastWorld = world
            offsetApplied = false
            serverFallDistance = 0.0
            lastServerY = 0.0
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        // 源 onMotion(MotionEvent.pre)
        if (Fly.handleEvents()) return

        val serverY = MovementUtils.serverY
        if (MovementUtils.serverOnGround || player.isInWater || player.isOnLadder || player.isInWeb) {
            serverFallDistance = 0.0
        } else if (lastServerY > serverY) {
            serverFallDistance += lastServerY - serverY
        }
        lastServerY = serverY

        val breakingBlock = mc.playerController != null && mc.playerController.getIsHittingBlock()
        if (jumped || breakingBlock) {
            offsetApplied = false
            return
        }

        val predictedFall = serverY - event.y + serverFallDistance
        val ground = predictedFall >= 3.0
        val applied = player.onGround && !offsetApplied && (serverFallDistance <= 0.0 || predictedFall >= 3.0)
        if (applied) event.y += 0.0301
        offsetApplied = applied
        event.onGround = ground
    }

    /** 源 DamageUtil.canCritical(EntityPlayer, Entity)。 */
    private fun canCritical(player: EntityPlayer, target: Entity): Boolean {
        val feetBlock = player.worldObj.getBlockState(
            BlockPos(player.posX, player.entityBoundingBox.minY, player.posZ)
        ).block
        if (feetBlock === Blocks.ladder || feetBlock === Blocks.vine) {
            // 攀爬中：只有自己实为旁观者才允许
            val info: NetworkPlayerInfo? = mc.thePlayer?.let { mc.netHandler.getPlayerInfo(it.uniqueID) }
            if (info == null || info.gameType != WorldSettings.GameType.SPECTATOR) return false
        }
        return !player.isInWater && !player.isPotionActive(Potion.blindness) &&
            player.ridingEntity == null && target is EntityLivingBase
    }
}
