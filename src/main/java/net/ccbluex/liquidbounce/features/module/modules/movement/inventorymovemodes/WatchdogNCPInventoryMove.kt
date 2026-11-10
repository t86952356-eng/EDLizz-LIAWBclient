/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.inventorymovemodes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.GameSettings
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.client.C0EPacketClickWindow
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * InventoryMove「WatchdogNCP」模式：
 * 箱子/背包界面中的 Watchdog 背包移动绕过 —— 按真实按键回填移动键、
 * 界面中限速（Prediction Mode 或限速序列）、箱子打开延迟测量
 * （Measure Chest Open）与打开确认后的输入抑制。
 * 逻辑按原版 1.8.9 实现等价移植（原名 Watchdog）。
 *
 * 移植说明：
 * - BadPacketsComponent（C0E/C16 开包/C0D 后未发 C03 即为「坏包」）→ 本地等价跟踪；
 * - SprintEvent → UpdateEvent 中强制 isSprinting = false（b12.10 同款映射）；
 * - PreMotionEvent.setSprinting(false) → 置 isSprinting = false（发送端由 Mixin 同步）；
 * - MoveInputEvent.setStrafe/setForward(0) → MovementInputEvent.originalInput 置零；
 * - RotationComponent.setRotations → RotationUtils.currentRotation 注入；
 * - 原混淆字段 entity.adr（仅 NPC 判定用到，无法解出语义）未移植。
 */
object WatchdogNCPInventoryMove : InventoryMoveMode("WatchdogNCP") {

    /** Prediction Mode（默认关）。 */
    var predictionMode = false

    /** Ticks（默认 1，1..20）。 */
    var ticks = 1

    /** Measure Chest Open（默认开）。 */
    var measureChestOpen = true

    var inventoryClicking = false
        private set

    var chestOpenConfirmed = false
        private set

    private var speedSuppressed = false
    private var inputDelayPassed = false
    private var inputBlockStart = 0L
    private var forward = 0f
    private var strafe = 0f

    private var openSentTick = -1
    private var openLatencyTicks = -1
    private var chestOpenTick = -1
    private var lastChestPos: BlockPos? = null
    private var pendingChestPos: BlockPos? = null
    private var lastOpenSource = "unknown"
    private var pendingOpenSource = "unknown"
    private var openPending = false
    private var awaitingChestGui = false

    /** BadPacketsComponent 等价状态：C0E/C16 开包/C0D 已发但还没发过 C03。 */
    private var badCloseOrClickPackets = false

    private val movementKeys
        get() = arrayOf(
            mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindLeft,
            mc.gameSettings.keyBindJump
        )

    private val directionalKeys
        get() = arrayOf(
            mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindLeft
        )

    override fun onEnable() {
        speedSuppressed = false
        inputDelayPassed = false
        inputBlockStart = 0L
        forward = 0f
        strafe = 0f
        inventoryClicking = false
        chestOpenConfirmed = false
        openSentTick = -1
        openLatencyTicks = -1
        chestOpenTick = -1
        lastChestPos = null
        pendingChestPos = null
        lastOpenSource = "unknown"
        pendingOpenSource = "unknown"
        openPending = false
        awaitingChestGui = false
        badCloseOrClickPackets = false
    }

    override fun onDisable() {
        inventoryClicking = false
        chestOpenConfirmed = false
        RotationUtils.currentRotation = null
    }

    // ------------------------------------------------------------------
    // 原实现的第一个 PreMotion 监听
    // ------------------------------------------------------------------
    override fun onPreMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val screen = mc.currentScreen ?: return
        if (screen is GuiChat || screen is ClickGui) return

        for (keyBinding in movementKeys) {
            keyBinding.pressed = GameSettings.isKeyDown(keyBinding)
        }
    }

    // ------------------------------------------------------------------
    // 原实现的 PreMotionMedium 监听（在第一个之后执行）
    // ------------------------------------------------------------------
    fun onPreMotionMedium(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        val screen = mc.currentScreen

        if (screen !is GuiInventory) {
            inventoryClicking = false
        }

        if (screen is GuiInventory) {
            if (!inventoryClicking && !badCloseOrClickPackets) {
                // 原实现：BadPacketsComponent.bad(false,false,false,false,true) 为假时
                // 无事件地补发一个关窗包
                sendPacket(
                    C0DPacketCloseWindow(screen.inventorySlots.windowId),
                    triggerEvent = false
                )
            }
        }

        if ((screen is GuiChest || inventoryClicking) && !predictionMode) {
            if (Speed.handleEvents() && !speedSuppressed) {
                player.motionZ *= -0.1
                player.motionX *= -0.1
                speedSuppressed = true
            }

            if (player.itemInUseCount < 10 &&
                !(abs(player.posY - player.posY.roundToInt().toDouble()) > 0.03) &&
                screen !is GuiChest
            ) {
                PortedMove.strafe(0.0365)
            } else if (!player.onGround) {
                PortedMove.stop()
            } else if (player.isPotionActive(Potion.moveSpeed) && 1 + player.getActivePotionEffect(Potion.moveSpeed).amplifier > 1) {
                PortedMove.strafe(0.0185 * (1 + player.getActivePotionEffect(Potion.moveSpeed).amplifier).toDouble())
            } else if (player.isPotionActive(Potion.moveSpeed) && 1 + player.getActivePotionEffect(Potion.moveSpeed).amplifier == 1) {
                PortedMove.strafe(0.0635 * (1 + player.getActivePotionEffect(Potion.moveSpeed).amplifier).toDouble())
            } else {
                PortedMove.strafe(0.09)
            }

            if (player.isJumping && player.onGround) {
                PortedMove.stop()
            } else if (player.isJumping) {
                PortedMove.stop()
            }

            PortedMove.preventDiagonalSpeed()
        } else if (speedSuppressed) {
            Speed.state = true
            speedSuppressed = false
        }

        if ((screen is GuiChest || inventoryClicking) && predictionMode &&
            !player.isPotionActive(Potion.moveSpeed)
        ) {
            player.isSprinting = false
            mc.gameSettings.keyBindSprint.pressed = false
            mc.gameSettings.keyBindJump.pressed = false
            return
        }

        if (screen !is GuiChest && !inventoryClicking) return

        if (!predictionMode) return

        player.isSprinting = false
    }

    // ------------------------------------------------------------------
    // 原实现的 PacketSend 监听
    // ------------------------------------------------------------------
    override fun onPacketSend(event: PacketEvent) {
        if (event.eventType != EventState.SEND || event.isCancelled) return
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return
        val packet = event.packet
        val screen = mc.currentScreen

        // BadPacketsComponent 等价跟踪
        if (packet is C0EPacketClickWindow || packet is C0DPacketCloseWindow) {
            badCloseOrClickPackets = true
        } else if (packet is C03PacketPlayer) {
            badCloseOrClickPackets = false
        }

        if (measureChestOpen && packet is C08PacketPlayerBlockPlacement) {
            val blockPos = packet.position
            if (screen !is GuiChest && isChestBlock(blockPos)) {
                pendingChestPos = blockPos
                pendingOpenSource = "C08 at $blockPos"
                openPending = true
            }
        } else if (measureChestOpen && packet is C02PacketUseEntity) {
            val entity = packet.getEntityFromWorld(world)
            if (screen !is GuiChest && packet.action != C02PacketUseEntity.Action.ATTACK &&
                isNpcEntity(entity)
            ) {
                pendingChestPos = null
                pendingOpenSource = "C02 at ${entity.name} (${entity.entityId})"
                openPending = true
            }
        }

        if (packet is C0EPacketClickWindow) {
            if (screen is GuiInventory && packet.mode < 1 && packet.clickedItem != null) {
                inventoryClicking = true
            }
        }
    }

    // ------------------------------------------------------------------
    // 原实现的 MoveInput 监听（High 优先级先捕获输入）
    // ------------------------------------------------------------------
    override fun onMoveInput(event: MovementInputEvent) {
        val player = mc.thePlayer ?: return
        val screen = mc.currentScreen

        // 原实现挂在 onMoveInputHigh：捕获输入
        forward = event.originalInput.moveForward
        strafe = event.originalInput.moveStrafe

        // 原实现的 onMoveInput（原实现中对按键计数的循环结果未被使用，未移植）

        if (chestOpenConfirmed && player.ticksExisted % 5 != 0 && screen is GuiChest) {
            event.originalInput.moveStrafe = 0f
            event.originalInput.moveForward = 0f
        }

        if (screen !is GuiChest) {
            chestOpenConfirmed = false
        }

        if ((screen is GuiChest || inventoryClicking) && predictionMode &&
            (player.isPotionActive(Potion.moveSpeed) || !player.onGround)
        ) {
            event.originalInput.moveStrafe = 0f
            event.originalInput.moveForward = 0f
        } else if (inventoryClicking && !inputDelayPassed) {
            event.originalInput.moveStrafe = 0f
            event.originalInput.moveForward = 0f
            if (inputBlockStart == 0L) {
                inputBlockStart = System.currentTimeMillis()
            }
        } else {
            inputBlockStart = 0L
        }

        if (inputBlockStart != 0L && System.currentTimeMillis() - inputBlockStart >= 60L) {
            inputDelayPassed = true
            inputBlockStart = 0L
        }

        if (screen !is GuiChest && !inventoryClicking) {
            inputDelayPassed = false
        }
    }

    // ------------------------------------------------------------------
    // 原实现的 Move 监听
    // ------------------------------------------------------------------
    override fun onMove(event: MoveEvent) {
        val player = mc.thePlayer ?: return
        val screen = mc.currentScreen
        if ((screen is GuiChest || inventoryClicking) && !predictionMode && !player.onGround) {
            event.z = 0.0
            event.x = 0.0
        }
    }

    // ------------------------------------------------------------------
    // 原实现的 PreUpdate + Sprint 监听（都映射到 UpdateEvent）
    // ------------------------------------------------------------------
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (mc.theWorld == null || player.ticksExisted < 50) return
        val screen = mc.currentScreen

        // 原实现挂在 SprintEvent：界面中强制取消疾跑
        if (screen is GuiChest || inventoryClicking) {
            player.isSprinting = false
        }

        // 原实现挂在 PreUpdateEvent：测量与朝向
        beginOpenMeasurement()
        finishOpenMeasurement()
        updateChestOpenState()

        var pressedKeyCount = 0
        for (keyBinding in directionalKeys) {
            if (keyBinding.isKeyDown) {
                pressedKeyCount++
            }
        }

        if ((screen is GuiChest || inventoryClicking) && pressedKeyCount > 1) {
            // 原实现：setRotations((toDegrees(g(forward, strafe)), pitch), 10, NORMAL)
            val yaw = Math.toDegrees(PortedMove.direction()).toFloat()
            RotationUtils.currentRotation = Rotation(yaw, player.rotationPitch)
        } else {
            RotationUtils.currentRotation = null
        }
    }

    // ------------------------------------------------------------------
    // 箱子打开测量（Measure Chest Open）
    // ------------------------------------------------------------------
    private fun beginOpenMeasurement() {
        if (!openPending) return
        val player = mc.thePlayer ?: return
        openSentTick = player.ticksExisted
        lastChestPos = pendingChestPos
        lastOpenSource = pendingOpenSource
        openPending = false
        awaitingChestGui = true
    }

    private fun finishOpenMeasurement() {
        if (!awaitingChestGui) return
        val player = mc.thePlayer ?: return
        if (mc.currentScreen is GuiChest) {
            openLatencyTicks = player.ticksExisted - openSentTick
            if (chestOpenTick == -1) {
                chestOpenTick = player.ticksExisted
            }
            awaitingChestGui = false
            return
        }
        if (player.ticksExisted - openSentTick > 40) {
            awaitingChestGui = false
        }
    }

    private fun updateChestOpenState() {
        val player = mc.thePlayer ?: return
        if (mc.currentScreen !is GuiChest) {
            chestOpenTick = -1
            chestOpenConfirmed = false
            return
        }
        if (chestOpenTick == -1) {
            chestOpenTick = player.ticksExisted
        }
        if (!chestOpenConfirmed && openLatencyTicks >= 0 &&
            player.ticksExisted - chestOpenTick >= openLatencyTicks - ticks
        ) {
            chestOpenConfirmed = true
        }
    }

    private fun isChestBlock(blockPos: BlockPos?): Boolean {
        if (blockPos == null) return false
        if (mc.theWorld == null) return false
        if (blockPos == BlockPos(-1, -1, -1)) return false
        val block = mc.theWorld.getBlockState(blockPos).block
        return block === Blocks.chest || block === Blocks.trapped_chest || block === Blocks.ender_chest
    }

    private fun isNpcEntity(entity: Entity?): Boolean {
        if (entity == null) return false
        val player = mc.thePlayer ?: return false
        if (entity === player) return false
        if (entity is EntityPlayer) {
            return mc.netHandler.getPlayerInfo(entity.uniqueID) == null
        }
        return isStationary(entity)
    }

    private fun isStationary(entity: Entity): Boolean {
        if (!(abs(entity.posX - entity.lastTickPosX) < 0.03)) return false
        if (!(abs(entity.posY - entity.lastTickPosY) < 0.03)) return false
        if (!(abs(entity.posZ - entity.lastTickPosZ) < 0.03)) return false
        if (!(abs(entity.motionX) < 0.03)) return false
        if (!(abs(entity.motionY) < 0.03)) return false
        if (!(abs(entity.motionZ) < 0.03)) return false
        return true
    }
}
