/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.Types
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.ViaUseItem
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword

/**
 * NoSlow「Grim 1.9」模式：跨版本（1.9+）时每个移动 tick 通过 Via 管线
 * 补发一个 Use Item 包，利用 1.9+ 协议的减速判定分支绕过。
 */
object Grim19NoSlowMode : NoSlowMode("Grim 1.9") {

    private var useTicks = 0
    private var sequence = 0

    override fun onEnable() {
        useTicks = 0
    }

    override fun onDisable() {
        useTicks = 0
    }

    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return

        if (player.isUsingItem && ViaUseItem.isCrossVersionTo9Plus()) {
            if (ViaLoadingBase.getInstance().targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
                sendUseItem19()
            } else {
                ViaUseItem.sendUseItem()
            }
            useTicks++
        } else if (player.isUsingItem && player.ticksExisted % 5 == 0) {
            chat("use 1.9+ for this bypass")
        } else {
            useTicks = 0
        }
    }

    /** 1.19+ 分支：主手 Use Item（hand=1）+ 递增序列号。 */
    private fun sendUseItem19() {
        try {
            val connections: Collection<UserConnection> = Via.getManager().connectionManager.connections
            if (connections.isEmpty()) return
            val user = connections.iterator().next()
            val wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM, user)
            wrapper.write(Types.VAR_INT as Type<Int>, 1)
            sequence++
            wrapper.write(Types.VAR_INT as Type<Int>, sequence)
            wrapper.sendToServer(Protocol1_19To1_18_2::class.java)
        } catch (_: Throwable) {
        }
    }

    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        if (NoSlow.food && player.isUsingItem && item is ItemFood) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.potion && player.isUsingItem && item is ItemPotion) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.sword && player.isUsingItem && item is ItemSword) {
            event.strafe = 1f
            event.forward = 1f
        }

        if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
            event.strafe = 1f
            event.forward = 1f
        }
    }
}
