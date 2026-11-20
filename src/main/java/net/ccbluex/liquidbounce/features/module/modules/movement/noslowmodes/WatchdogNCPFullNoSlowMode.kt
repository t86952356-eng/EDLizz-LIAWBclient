/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslowmodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.SlowDownEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.block.Block
import net.minecraft.block.BlockDoor
import net.minecraft.block.BlockFence
import net.minecraft.block.BlockFenceGate
import net.minecraft.entity.Entity
import net.minecraft.entity.passive.EntityVillager
import net.minecraft.init.Blocks
import net.minecraft.item.ItemSword
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils

/**
 * NoSlow「WatchdogNCP-Full」模式（整体移植 Rewound 客户端 RewoundNoSlowMode）：
 * 剑专属「包扣留」无减速 —— 右键格挡时把 C08/C0A/C02（及攻击期的 C09）扣到
 * POST motion 之后再发出，服务端因此在位置包之后才看到格挡开始；
 * 指向可交互方块时抑制右键（源在点击前门控）；减速无条件移除（不限物品类型）。
 *
 * 与源的已记录差异：
 * - 源点击前门控挂 IngameActionEvent（点击处理之前），Lizz 通过
 *   MixinMinecraft 的 rightClickMouse HEAD 注入调用 [onRightClick] 等价实现；
 * - isInServersideGui（服务端 GUI 跟踪）→ InventoryUtils.serverOpenContainer/
 *   serverOpenInventory 近似；
 * - BreakerModule.isBreakingOnRewound（Rewound 专属自动破block模块）→ 省略；
 * - ControllerUpdatePhaseEvent（交互处理期 C09 扣留标志）→ 攻击期 AttackEvent 覆盖；
 * - 模块级 Click while using 设置（点击处理重放）不属于该模式 → 未移植。
 */
object WatchdogNCPFullNoSlowMode : NoSlowMode("WatchdogNCP-Full") {

    /** 源 BlockUtil.INTERACTABLE_BLOCKS + 栅栏/门判定。 */
    private val interactableBlocks = setOf(
        Blocks.enchanting_table, Blocks.dispenser, Blocks.cauldron,
        Blocks.anvil, Blocks.chest, Blocks.beacon, Blocks.brewing_stand,
        Blocks.stone_button, Blocks.wooden_button, Blocks.cake, Blocks.lever,
        Blocks.noteblock, Blocks.jukebox, Blocks.ender_chest, Blocks.trapped_chest,
        Blocks.furnace, Blocks.hopper, Blocks.unpowered_repeater, Blocks.powered_repeater,
        Blocks.unpowered_comparator, Blocks.powered_comparator, Blocks.crafting_table, Blocks.trapdoor,
        Blocks.iron_trapdoor, Blocks.command_block, Blocks.bed
    )

    /** 扣留中的出站包（源的 outboundPacketQueue hold）。 */
    private val heldPackets = ArrayDeque<Packet<*>>()

    /** hold 是否激活（源的 hold != null）。 */
    private var holdActive = false

    /** 攻击期切换槽位包也一并扣留（源的 holdSlotChange，C09 条件）。 */
    var holdSlotChange = false

    /** 本 tick 已触发的格挡（源的 blocking，UserInputVerification 每刻复位）。 */
    var blocking = false

    private val active get() = NoSlow.handleEvents() && NoSlow.mode == "WatchdogNCP-Full"

    /**
     * 右键点击前门控（由 MixinMinecraft#rightClickMouse HEAD 调用；等价源的
     * IngameActionEvent 两个 handler：优先级 72 的 onControllerUpdate + 11 的 onUseItem）。
     * 返回 true = 取消本次右键。
     */
    @JvmStatic
    fun onRightClick(): Boolean {
        if (!active) return false
        val player = mc.thePlayer ?: return false
        if (player.ridingEntity != null) return false
        if (InventoryUtils.serverOpenContainer || InventoryUtils.serverOpenInventory) return false
        if (player.heldItem?.item !is ItemSword) return false

        if (!canInteract()) {
            // 源：非攻击键按住时禁用右键（避免剑右键打开箱子等）
            return !mc.gameSettings.keyBindAttack.isKeyDown()
        }

        // 源 onControllerUpdate（优先级 72，先执行）
        acquireHold()
        blocking = true
        if (player.isUsingItem) {
            mc.playerController.onStoppedUsingItem(player)
        }
        mc.rightClickDelayTimer = 0

        // 源 onUseItem（优先级 11，后执行）：格挡中指向方块 → 清除射线，让点击走纯物品使用
        if (mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        ) {
            clearMouseOver()
        }
        return false
    }

    /** 源 WorldUtil.canInteract（Breaker 集成部分省略）。 */
    private fun canInteract(): Boolean {
        val hit = mc.objectMouseOver ?: return true
        if (mc.playerController.getIsHittingBlock()) return false
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            return hit.entityHit == null || hit.entityHit !is EntityVillager
        }
        if (hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return true
        if (mc.thePlayer.movementInput.sneak) return true
        val block = mc.theWorld.getBlockState(hit.blockPos).block
        return !isInteractable(block)
    }

    /** 源 BlockUtil.isInteractable。 */
    private fun isInteractable(block: Block) =
        block is BlockFenceGate || block is BlockFence || block is BlockDoor || block in interactableBlocks

    /** 源 WorldUtil.clearMouseOver。 */
    private fun clearMouseOver() {
        if (mc.objectMouseOver == null) {
            mc.objectMouseOver = MovingObjectPosition(
                MovingObjectPosition.MovingObjectType.MISS,
                Vec3(0.0, 0.0, 0.0),
                EnumFacing.UP,
                BlockPos.ORIGIN
            )
        } else {
            mc.objectMouseOver.typeOfHit = MovingObjectPosition.MovingObjectType.MISS
        }
        mc.pointedEntity = null
    }

    /** 源 acquireHold：登记出站包过滤器（C09 条件 / C0A / C08 / C02）。 */
    private fun acquireHold() {
        if (holdActive) return
        holdActive = true
        heldPackets.clear()
    }

    /** 源 releaseHold：POST motion 后补发全部扣留包。 */
    fun releaseHold() {
        holdSlotChange = false
        if (!holdActive) return
        holdActive = false
        val packets = heldPackets.toList()
        heldPackets.clear()
        for (packet in packets) sendPacket(packet, triggerEvent = false)
    }

    override fun onDisable() {
        releaseHold()
        blocking = false
    }

    override fun onUpdate() {
        // 源 UserInputVerificationEvent：每 tick 复位 blocking
        blocking = false
    }

    override fun onPostMotion(event: MotionEvent) {
        // 源 onMotion(!pre)：位置包发出后释放扣留
        releaseHold()
    }

    override fun onAttack(target: Entity?) {
        // 源 onAttack(AttackEntityEvent.pre)：攻击期间把 C09 一并扣留
        holdSlotChange = holdActive
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != EventState.SEND || event.isCancelled) return
        if (!holdActive) return

        val packet = event.packet
        val hold = when (packet) {
            is C09PacketHeldItemChange -> holdSlotChange
            is C0APacketAnimation -> true
            is C08PacketPlayerBlockPlacement -> true
            is C02PacketUseEntity -> true
            else -> false
        }
        if (hold) {
            heldPackets.add(packet)
            event.cancelEvent()
        }
    }

    override fun onSlowDown(event: SlowDownEvent) {
        // 源 ItemSlowdownEvent：apply=false + allowSprinting + ignoreSneakForSprint（无条件，不限物品类型）
        event.strafe = 1f
        event.forward = 1f
    }
}
