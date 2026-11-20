/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.extensions.getPing
import net.minecraft.item.ItemEnderPearl
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.S01PacketJoinGame
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S0EPacketSpawnObject
import net.minecraft.network.play.server.S27PacketExplosion
import kotlin.math.sqrt
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Rewound「Disabler」跟踪器（全局常驻，等价移植 RewoundDisablerTracker）：
 * - 跟踪最近 1 秒内的出站包数量（自伤 795 上限检查用）；
 * - 末影珍珠投掷（C08 持珍珠）→ S0E(type=65) 珍珠实体生成 → S08 拉回
 *   在 ping+500ms 内且珍珠已生成 → 触发 ENDER_PEARL 位移失效；
 * - S27 爆炸包（位移 > 0.05）→ 触发 EXPLOSION；
 * - S01 加入游戏 → 全部复位；
 * - isActive：触发后 3 秒内的飞行窗口。
 * trigger() 会同步通知 WatchdogDamage 模式（对应源的 RewoundDisablerEvent 事件分发）。
 */
object RewoundFlyTracker : MinecraftInstance, Listenable {

    const val TYPE_DISABLER = "Disabler"
    const val TYPE_ENDER_PEARL = "EnderPearl"
    const val TYPE_EXPLOSION = "Explosion"

    /** 最近 1000ms 内出站包的时间戳（源的 sentPackets: List<Timer>）。 */
    private val sentPackets = CopyOnWriteArrayList<Long>()

    /** 上次末影珍珠投掷时间（0 = 从未）。 */
    var pearlThrowTimer = 0L
        private set

    /** 珍珠实体已生成（S0E type=65 紧跟投掷）。 */
    var pearlSpawned = false
        private set

    /** 上次触发时间（0 = 从未触发）。 */
    var activeTimer = 0L
        private set

    var lastType: String? = null
        private set

    var triggerCount = 0
        private set

    val isActive: Boolean
        get() = activeTimer > 0L && System.currentTimeMillis() - activeTimer < 3000L

    fun canUseTimer() = sentPackets.size < 800

    val sentPacketsCount: Int
        get() = sentPackets.size

    fun trigger(type: String) {
        activeTimer = System.currentTimeMillis()
        lastType = type
        triggerCount++
        // 同步事件分发（等价源的 EventBus.post(RewoundDisablerEvent)）
        WatchdogDamage.onDisablerTriggered(type)
    }

    private val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (event.eventType == EventState.SEND) {
            // 源在 DirectionalPacketEvent(outgoing) 无条件计数
            sentPackets.add(System.currentTimeMillis())
            if (packet is C08PacketPlayerBlockPlacement && packet.stack?.item is ItemEnderPearl) {
                pearlThrowTimer = System.currentTimeMillis()
            }
            return@handler
        }

        // RECEIVE
        val player = mc.thePlayer ?: return@handler
        when (packet) {
            is S08PacketPlayerPosLook -> {
                if (System.currentTimeMillis() < pearlThrowTimer + player.getPing() + 500L) {
                    if (pearlSpawned) trigger(TYPE_ENDER_PEARL)
                    pearlSpawned = false
                }
            }

            is S0EPacketSpawnObject -> {
                if (packet.type == 65 && System.currentTimeMillis() < pearlThrowTimer + player.getPing() + 70L) {
                    pearlSpawned = true
                }
            }

            is S01PacketJoinGame -> {
                activeTimer = 0L
                lastType = null
                pearlThrowTimer = 0L
                pearlSpawned = false
                sentPackets.clear()
            }

            is S27PacketExplosion -> {
                // 源读 func_149149_c()/func_149144_d()/func_149147_e()（未映射的 getter），
                // 对应的三个 motion 字段在 stable_22 里就是 field_149152_f/g/h
                val mx = packet.field_149152_f.toDouble()
                val my = packet.field_149153_g.toDouble()
                val mz = packet.field_149159_h.toDouble()
                if (sqrt(mx * mx + my * my + mz * mz) > 0.05) {
                    trigger(TYPE_EXPLOSION)
                }
            }

            else -> Unit
        }
    }

    private val onTick = handler<GameTickEvent> {
        val now = System.currentTimeMillis()
        sentPackets.removeAll { it < now - 1000L }
    }
}
