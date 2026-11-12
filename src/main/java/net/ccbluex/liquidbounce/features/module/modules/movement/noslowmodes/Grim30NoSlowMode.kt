/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * NoSlow「Grim 3.0」模式 —— 源 Grim30NoSlow 等价移植：
 * - 使用物品期间朝向偏移 +45°（两条路径：任意使用中空中无侧键 / itemInUse>1 且未按跳）
 * - 网中 strafe(0.64)
 * - itemInUse > 1 且未按跳时 moveFlying 微加速（2e-4 / Speed 开启时 1e-4）
 * - 右键节流：奇数 tick 且空中时取消右键
 * - SprintEvent：使用中前进时强制疾跑
 * - SlowDown：特定 tick 组合下取消减速（Food/Potion/Sword/Bow 四个门）
 * - Heypixel：使用食物/药水/弓期间扣住出站 C0F 事务
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement

object Grim30NoSlowMode : NoSlowMode("Grim 3.0") {

    /** Heypixel（默认关）：使用食物/药水/弓时扣住 C0F。 */
    var heypixel = false

    private var usingItemTicks = 0

    override fun onEnable() {
        usingItemTicks = 0
    }

    override fun onDisable() {
        usingItemTicks = 0
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        // 源挂在 PreUpdateEvent 的第一段
        if (player.isUsingItem && !player.onGround &&
            !mc.gameSettings.keyBindRight.isKeyDown && !mc.gameSettings.keyBindLeft.isKeyDown
        ) {
            RotationUtils.currentRotation =
                Rotation(player.rotationYaw + 45f, player.rotationPitch)
        }

        if (player.isInWeb) {
            PortedMove.strafe(0.64)
        }

        if (player.isUsingItem && player.itemInUseCount > 1 && !mc.gameSettings.keyBindJump.isKeyDown) {
            if (!Speed.handleEvents()) {
                PortedMove.moveFlying(2.0E-4)
            } else {
                PortedMove.moveFlying(1.0E-4)
            }

            if (!mc.gameSettings.keyBindRight.isKeyDown &&
                !mc.gameSettings.keyBindLeft.isKeyDown &&
                player.heldItem?.item !is ItemBow
            ) {
                RotationUtils.currentRotation =
                    Rotation(player.rotationYaw + 45f, player.rotationPitch)
            }
        }

        // 源挂在 SprintEvent：使用中前进时强制疾跑
        if (player.isUsingItem && player.moveForward > 0f) {
            player.isSprinting = true
        }
    }

    override fun onPacket(event: PacketEvent) {
        if (event.isCancelled) return
        if (event.eventType != EventState.SEND) return

        // 源挂在 PacketSendEvent（Heypixel 开启时）
        if (heypixel && event.packet is C0FPacketConfirmTransaction) {
            val player = mc.thePlayer ?: return
            val item = player.heldItem?.item
            if (player.isUsingItem && (item is ItemFood || item is ItemPotion || item is ItemBow)) {
                event.cancelEvent()
            }
        }

        // 源挂在 RightClickEvent：奇数 tick 且空中时取消右键（以出站 C08 近似）
        if (event.packet is C08PacketPlayerBlockPlacement) {
            val player = mc.thePlayer ?: return
            if (player.ticksExisted % 2 == 1 && !player.onGround) {
                event.cancelEvent()
            }
        }
    }

    override fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        val item = player.heldItem?.item

        if (player.isUsingItem) {
            usingItemTicks++
            if (!Speed.handleEvents()) {
                PortedMove.moveFlying(1.0E-4)
            }
        } else {
            usingItemTicks = 0
        }

        // 源：特定 tick 组合（itemInUseCount == 1 / 奇数空中 / 奇数落地）时取消减速
        if (player.itemInUseCount == 1 ||
            player.ticksExisted % 2 == 0 && !player.onGround ||
            player.itemInUseCount % 2 == 1 && player.onGround
        ) {
            if (NoSlow.food && player.isUsingItem && item is ItemFood) {
                event.forward = 1f
                event.strafe = 1f
            }

            if (NoSlow.potion && player.isUsingItem && item is ItemPotion) {
                event.forward = 1f
                event.strafe = 1f
            }

            if (NoSlow.sword && NoSlow.usingItemFunc() && item is ItemSword) {
                event.forward = 1f
                event.strafe = 1f
            }

            if (NoSlow.bow && player.isUsingItem && item is ItemBow) {
                event.forward = 1f
                event.strafe = 1f
            }
        }
    }
}
