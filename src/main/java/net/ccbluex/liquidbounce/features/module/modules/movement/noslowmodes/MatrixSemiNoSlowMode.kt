/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.extensions.airTicks
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.item.Item
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos

/**
 * NoSlow「MatrixSemi」模式（仅 Food 分支可用，剑分支不生效）：
 * 吃东西期间循环起跳 + 偏航 +45° 静默旋转 + 微速度（1e-4 增量），
 * 落地一瞬/空中偶数 tick 完全取消减速。
 */
object MatrixSemiNoSlowMode : NoSlowMode("MatrixSemi") {

    /** 源 onGroundTicks：在地面的连续 tick 数（1.8.9 无原生字段，本地维护）。 */
    private var groundTicks = 0

    private fun isFoodType(item: Item?) =
        item is ItemFood || item is ItemBucketMilk || item is ItemPotion

    private fun foodActive(player: net.minecraft.client.entity.EntityPlayerSP): Boolean {
        val item = player.heldItem?.item
        return player.isUsingItem && isFoodType(item) &&
                (NoSlow.food && item is ItemFood ||
                        NoSlow.potion && (item is ItemPotion || item is ItemBucketMilk))
    }

    override fun onEnable() {
        groundTicks = 0
    }

    override fun onDisable() {
        groundTicks = 0
    }

    /** 源 RightClickEvent：空中奇数 tick 时取消使用动作（拦截出站的使用物品包）。 */
    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.SEND) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (packet is C08PacketPlayerBlockPlacement && packet.position == BlockPos(-1, -1, -1) &&
            isFoodType(player.heldItem?.item) && foodActive(player) &&
            player.airTicks % 2 == 1 && !player.onGround
        ) {
            event.cancelEvent()
        }
    }

    /** 源 PreUpdateEvent + LivingUpdateEvent（吃食物期间的行为，每 tick 驱动）。 */
    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        // LivingUpdateEvent：前进输入时强制疾跑
        if (foodActive(player) && player.moveForward > 0.0f) {
            player.isSprinting = true
        }

        if (!foodActive(player)) {
            groundTicks = if (player.onGround) groundTicks + 1 else 0
            return
        }

        // onGround && jumpTicks == 0 → tryJump（1.8.9 的 jumpTicks 不可访问，用扩展的 tryJump 近似）
        if (player.onGround) {
            player.tryJump()
        }

        // 使用中且空中且没有左右输入 → 偏航 +45°（Silent 旋转）
        if (!player.onGround &&
            !mc.gameSettings.keyBindRight.isKeyDown && !mc.gameSettings.keyBindLeft.isKeyDown
        ) {
            RotationUtils.currentRotation = Rotation(player.rotationYaw + 45f, player.rotationPitch)
        }

        if (player.isInWeb) {
            // strafeWithMotionReset(0.64)：沿移动方向重设 0.64
            PortedMove.strafe(0.64)
        }

        // 使用中且落地超过 1 tick 且没按跳跃 → 微速 + 旋转
        if (player.isUsingItem && groundTicks > 1 && !mc.gameSettings.keyBindJump.isKeyDown) {
            if (!Speed.handleEvents() &&
                ViaLoadingBase.getInstance().targetVersion.olderThan(ProtocolVersion.v1_14)
            ) {
                // strafe(2.0E-4)：源为增量（motionX += -sin*d）
                PortedMove.moveFlying(2.0E-4)
            } else {
                PortedMove.moveFlying(1.0E-4)
            }

            if (!mc.gameSettings.keyBindRight.isKeyDown && !mc.gameSettings.keyBindLeft.isKeyDown) {
                RotationUtils.currentRotation = Rotation(player.rotationYaw + 45f, player.rotationPitch)
            }
        }

        groundTicks = if (player.onGround) groundTicks + 1 else 0
    }

    /** 源 ItemSlowDownEvent：半数 tick 完全取消减速。 */
    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        if (!foodActive(player)) return

        // 源：!Speed 开启 && 跨版本 < 1.14 → strafe(1.0E-4)（增量）
        if (!Speed.handleEvents() &&
            ViaLoadingBase.getInstance().targetVersion.olderThan(ProtocolVersion.v1_14)
        ) {
            PortedMove.moveFlying(1.0E-4)
        }

        // lol：落地一瞬 || 空中偶数 tick || 地面奇数 tick → 取消减速
        val lol = groundTicks == 1 ||
                (player.airTicks % 2 == 0 && !player.onGround) ||
                (groundTicks % 2 == 1 && player.onGround)

        if (lol) {
            event.strafe = 1f
            event.forward = 1f
        }
    }
}
