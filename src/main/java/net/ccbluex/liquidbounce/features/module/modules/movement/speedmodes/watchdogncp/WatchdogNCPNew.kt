/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.watchdogncp

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.Velocity
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold2
import net.ccbluex.liquidbounce.utils.movement.PortedMove
import net.ccbluex.liquidbounce.utils.movement.PortedTrackers
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.potion.Potion
import net.minecraft.util.MathHelper
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Speed「WatchdogNCP-New」模式：
 * Strafe / Low Strafe / NCP Strafe 三类子式，含冰面加速、伤害加速、
 * 摩擦全禁用（低跳子式）等完整逻辑。
 */
object WatchdogNCPNewSpeed : SpeedMode("WatchdogNCP-New") {

    /** Type（Strafe / Low Strafe / NCP Strafe，默认 Low Strafe）。 */
    var type = "Low Strafe"

    /** Air Strafe（默认开）。 */
    var airStrafe = true

    /** Always Glide Strafe（默认开）。 */
    var alwaysGlideStrafe = true

    /** Friction Override（默认开）。 */
    var frictionOverride = true

    /** Friction Full Disabler（默认开）。 */
    var frictionFullDisabler = true

    /** Damage Boost（默认关）。 */
    var damageBoost = false

    /** Damage Strafe (Hypixel Fly Disabler)（默认关）。 */
    var damageStrafeHypixelFlyDisabler = false

    /** UHC Mode（默认关）。 */
    var uHCMode = false

    /** Alternate Motion（默认关）。 */
    var alternateMotion = false

    /** 供 Velocity 的 WatchdogNCP-New / Vulcan-New 联动读取。 */
    var damageBoostEnabled = false
    var damageStrafeEnabled = false
    var pendingToggle = false

    private var onIce = false
    private var onUnevenGround = false
    private var scaffoldActive = false
    private var appliedGlideBoost = false
    private var liftBoosted = false
    private var motionDelta = 0.0
    private var collisionExpireTick = 0
    private var suppressBoost = false
    private var verticallyBlocked = false
    private var recentlyCollided = false

    private val speedPotionLevel: Int
        get() {
            val player = mc.thePlayer ?: return 0
            return if (player.isPotionActive(Potion.moveSpeed)) player.getActivePotionEffect(Potion.moveSpeed).amplifier + 1 else 0
        }

    private val scaffoldEnabled get() = Scaffold.handleEvents() || Scaffold2.handleEvents()

    override fun onEnable() {
        pendingToggle = false
        onUnevenGround = false
        liftBoosted = false
        appliedGlideBoost = false
        recentlyCollided = false
        if (PortedTrackers.itemUseTicks > 2) {
            suppressBoost = true
        }
    }

    override fun onDisable() {
        onIce = false
        liftBoosted = false
        if (scaffoldEnabled) {
            mc.thePlayer?.let {
                it.motionX *= 0.85
                it.motionZ *= 0.85
            }
        }
        if (scaffoldEnabled && mc.gameSettings.keyBindJump.isKeyDown &&
            (mc.thePlayer?.isPotionActive(Potion.moveSpeed) != true)
        ) {
            PortedMove.strafe(0.355)
        }
        suppressBoost = false
    }

    override fun onMoveInput(event: MovementInputEvent) {
        event.originalInput.jump = false
    }

    override fun onPacket(event: PacketEvent) {
        if (event.eventType != net.ccbluex.liquidbounce.event.EventState.RECEIVE) return
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (!Velocity.handleEvents() && type == "Low Strafe") {
            if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
                val useTicks = PortedTrackers.itemUseTicks
                if (useTicks == 1 || useTicks == 2 || useTicks == 3) {
                    event.cancelEvent()
                }
            }
        }
    }

    override fun onPreMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        val ae = PortedTrackers.ticksSinceVelocity

        damageBoostEnabled = damageBoost
        damageStrafeEnabled = damageStrafeHypixelFlyDisabler

        // ---- PostStrafe 阶段逻辑（朝向角差修正） ----
        if (ae > 1 || !damageBoost && PortedTrackers.itemUseTicks < 12) {
            val moveAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(PortedMove.direction()))
            val motionAngle = MathHelper.wrapAngleTo180_double(
                Math.toDegrees(atan2(player.motionZ, player.motionX)) - 90.0
            )
            if (airStrafe && type == "Strafe") {
                if (angleDiff(moveAngle, motionAngle) > 90.0) {
                    strafeAt(PortedMove.speed(), (motionAngle - 180.0).toFloat())
                } else if (damageStrafeHypixelFlyDisabler && ae > 20) {
                    val oldX = player.motionX
                    val oldZ = player.motionZ
                    PortedMove.strafe()
                    player.motionZ = (player.motionZ * 3.0 + oldZ * 1.0) / 4.0
                    player.motionX = (player.motionX * 3.0 + oldX * 1.0) / 4.0
                }
            } else if (airStrafe) {
                if (angleDiff(moveAngle, motionAngle) > 90.0) {
                    strafeAt(PortedMove.speed(), (motionAngle - 180.0).toFloat())
                } else if (damageStrafeHypixelFlyDisabler && ae > 20) {
                    val oldX = player.motionX
                    val oldZ = player.motionZ
                    PortedMove.strafe()
                    player.motionZ = (player.motionZ * 3.0 + oldZ * 1.0) / 4.0
                    player.motionX = (player.motionX * 3.0 + oldX * 1.0) / 4.0
                }
            }
        }

        // ---- PreMotion 阶段逻辑 ----
        if (PortedMove.isMoving()) {
            if (ae < 10 && damageStrafeHypixelFlyDisabler) {
                PortedMove.strafe()
            } else if (ae == 1) {
                PortedMove.strafe()
            }
        } else if (ae == 1) {
            player.motionX *= -1.0
            player.motionZ *= -1.0
        }

        if (scaffoldEnabled) {
            scaffoldActive = true
        }

        if (player.isCollidedHorizontally || PortedTrackers.ticksSinceTeleport < 2) {
            recentlyCollided = true
            collisionExpireTick = player.ticksExisted + 9
        }

        if (!player.isCollidedHorizontally && player.ticksExisted > collisionExpireTick) {
            recentlyCollided = false
            verticallyBlocked = false
        }

        val below = PortedMove.blockAt(0.0, -1.0, 0.0)
        if (below == Blocks.packed_ice || below == Blocks.ice) {
            onIce = true
        } else if (PortedTrackers.itemUseTicks > 1) {
            onIce = false
        }

        if (player.onGround) {
            appliedGlideBoost = false
        }

        if (PortedMove.blockAt(0.0, player.motionY, 0.0) != Blocks.air) {
            suppressBoost = false
        }

        if (player.isCollidedVertically && !player.onGround && type == "Low Strafe" &&
            PortedMove.blockAheadCollides(2.0)
        ) {
            suppressBoost = true
        }

        val posY = event.y
        if (abs(posY - posY.roundToLong().toDouble()) > 0.0325 && player.onGround) {
            onUnevenGround = true
        } else if (player.onGround) {
            onUnevenGround = false
        }

        if (player.isInWeb) {
            player.motionY = -0.0784000015258789
            PortedMove.strafe(PortedMove.allowedHorizontalDistance() * 3.1)
        }

        if (player.isInWeb && speedPotionLevel >= 1 && !player.onGround) {
            PortedMove.strafe(PortedMove.allowedHorizontalDistance() * 3.8)
        }

        if (player.isInWeb && speedPotionLevel >= 2 && !player.onGround) {
            PortedMove.strafe(PortedMove.allowedHorizontalDistance() * 4.0)
        }
    }

    override fun onStrafeEvent(event: StrafeEvent) {
        val player = mc.thePlayer ?: return
        val ae = PortedTrackers.ticksSinceVelocity
        val useTicks = PortedTrackers.itemUseTicks
        val allowed = PortedMove.allowedHorizontalDistance()

        if (player.itemInUseCount > 1 && scaffoldEnabled && player.ticksExisted % 3 == 1) {
            PortedMove.stop()
            return
        }

        if (ae < 2 && damageStrafeHypixelFlyDisabler && type != "Strafe") {
            suppressBoost = true
        }

        if (ae < 20 && damageStrafeHypixelFlyDisabler) {
            PortedMove.partialStrafePercent(100.0)
        }

        if (scaffoldEnabled && speedPotionLevel > 0 && mc.gameSettings.keyBindJump.isKeyDown) {
            recentlyCollided = true
            collisionExpireTick = player.ticksExisted + 8
        }

        val inventoryOpen = mc.currentScreen is net.minecraft.client.gui.inventory.GuiChest &&
            net.ccbluex.liquidbounce.features.module.modules.movement.InventoryMove.handleEvents()
        if (mc.currentScreen !is net.minecraft.client.gui.inventory.GuiChest || type == "Strafe" ||
            !net.ccbluex.liquidbounce.features.module.modules.movement.InventoryMove.handleEvents()
        ) {
            // 无操作（原实现内层分支不可达）
        } else {
            pendingToggle = true
        }

        when (type) {
            "Strafe" -> strafeBranch(player, ae, useTicks, allowed)
            "NCP Strafe" -> ncpStrafeBranch(player, ae, useTicks, allowed)
            else -> lowStrafeBranch(player, ae, useTicks, allowed)
        }

        // ---- 冰面加成（所有子式共享） ----
        if (PortedMove.speed() < 0.45 ||
            speedPotionLevel == 1 && PortedMove.speed() < 0.55 ||
            speedPotionLevel >= 2 && PortedMove.speed() < 0.55
        ) {
            if (onIce && player.onGround && !suppressBoost) {
                player.motionX *= 1.45
                player.motionZ *= 1.45
            }

            val atMotion = PortedMove.blockAt(0.0, player.motionY, 0.0)
            if (onIce && (atMotion == Blocks.ice || atMotion == Blocks.packed_ice) && !suppressBoost) {
                player.motionX *= 1.01
                player.motionZ *= 1.01
            }

            if (onIce && useTicks == 1 && !suppressBoost) {
                player.motionX *= 1.15
                player.motionZ *= 1.15
            }

            if (onIce && useTicks > 1 && !suppressBoost && type == "Low Strafe") {
                player.motionX *= 1.015
                player.motionZ *= 1.015
            }
        }

        // ---- Friction Full Disabler（仅 Low Strafe） ----
        val moveAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(PortedMove.direction()))
        val motionAngle = MathHelper.wrapAngleTo180_double(
            Math.toDegrees(atan2(player.motionZ, player.motionX)) - 90.0
        )
        if (angleDiff(moveAngle, motionAngle) < 5.0 && type == "Low Strafe" && frictionFullDisabler &&
            (!verticallyBlocked || useTicks in 3..5) &&
            (!mc.gameSettings.keyBindJump.isKeyDown || !scaffoldEnabled) &&
            PortedTrackers.ticksSinceTeleport > 10 && !suppressBoost && ae > 1 &&
            PortedMove.speed() < PortedMove.vd() - 0.0323 && useTicks < 18
        ) {
            // 原实现 ChatUtil.c 为空操作；setSpeed → 摩擦覆盖
            event.cancelEvent()
            if (speedPotionLevel > 0) {
                PortedMove.strafe(PortedMove.vd() - 0.003)
            } else {
                PortedMove.strafe(PortedMove.vd() - 0.001)
            }
        }
    }

    /** 「Strafe」子式。 */
    private fun strafeBranch(player: net.minecraft.client.entity.EntityPlayerSP, ae: Int, useTicks: Int, allowed: Double) {
        val potion = speedPotionLevel

        if (player.onGround && suppressBoost) {
            player.motionY = 0.42
        }

        if (player.onGround && !player.isPotionActive(Potion.moveSpeed) && PortedMove.isMoving() && !recentlyCollided) {
            PortedMove.strafe(allowed)
            player.jump()
        } else if (player.onGround && PortedMove.isMoving() && !recentlyCollided) {
            PortedMove.strafe(allowed)
            player.jump()
        } else if (player.onGround && PortedMove.isMoving()) {
            PortedMove.strafe(PortedMove.vd())
            player.jump()
        }

        if (useTicks == 1 && PortedTrackers.prevMotionY > -0.43 && !suppressBoost && !recentlyCollided) {
            if (potion >= 2) {
                PortedMove.strafe(0.48)
            } else if (potion == 1) {
                PortedMove.strafe(0.4)
            } else {
                PortedMove.strafe(0.33)
            }
            PortedMove.strafe()
        }

        if (useTicks == 2 && airStrafe && !suppressBoost) {
            val oldX = player.motionX
            val oldZ = player.motionZ
            player.motionZ = (player.motionZ * 1.0 + oldZ * 2.0) / 3.0
            player.motionX = (player.motionX * 1.0 + oldX * 2.0) / 3.0
            // 速度低于阈值时的检查（原实现为空体）
        }

        if (useTicks == 8 && PortedMove.blockAt(0.0, player.motionY * 4.7, 0.0) != Blocks.air) {
            player.motionZ *= 1.001
            player.motionX *= 1.001
        }

        val moveAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(PortedMove.direction()))
        val motionAngle = MathHelper.wrapAngleTo180_double(
            Math.toDegrees(atan2(player.motionZ, player.motionX)) - 90.0
        )
        val angleDelta = abs(moveAngle - motionAngle)
        val delta = hypot(
            player.motionX - PortedTrackers.prevMotionX,
            player.motionZ - PortedTrackers.prevMotionZ
        )
        val flag1 = !(angleDelta < 2.0) && !(angleDiff(moveAngle, motionAngle) > 90.0) &&
            !pendingToggle && !(delta < 0.0125)

        if (useTicks == 9 && PortedMove.blockAt(0.0, player.motionY * 3.5, 0.0) != Blocks.air) {
            if (!suppressBoost) {
                if (player.isPotionActive(Potion.moveSpeed)) {
                    player.motionZ *= 1.003
                    player.motionX *= 1.003
                } else if (!scaffoldEnabled) {
                    player.motionZ *= 1.003
                    player.motionX *= 1.003
                }

                if (alwaysGlideStrafe || flag1) {
                    player.motionY += 0.075
                    PortedMove.strafe()
                }
            }
        }

        if (useTicks == 10 && PortedMove.blockAt(0.0, player.motionY * 2.0, 0.0) != Blocks.air) {
            if (!suppressBoost && !recentlyCollided) {
                player.motionZ *= 1.005
                player.motionX *= 1.005
            }

            if (alwaysGlideStrafe && !recentlyCollided) {
                if ((PortedMove.speed() < allowed || player.motionX == 0.0 || player.motionZ == 0.0) &&
                    !suppressBoost && !recentlyCollided
                ) {
                    PortedMove.strafe(allowed)
                }
                PortedMove.strafe()
            }
        }

        if (PortedMove.blockAt(0.0, player.motionY, 0.0) != Blocks.air) {
            PortedMove.strafe()
            if (useTicks == 11) {
                if (potion >= 2) {
                    PortedMove.strafe(0.45)
                } else if (potion == 1) {
                    PortedMove.strafe(0.375)
                } else {
                    PortedMove.strafe(0.31)
                }
            }
        }

        motionDelta = hypot(
            player.motionX - PortedTrackers.prevMotionX,
            player.motionZ - PortedTrackers.prevMotionZ
        )
        if (motionDelta < 0.0125 && frictionOverride) {
            PortedMove.strafe()
        }

        PortedMove.useDiagonalSpeed()
        if (PortedMove.speed() < 0.125) {
            PortedMove.strafe(0.125)
        }
    }

    /** 「NCP Strafe」子式。 */
    private fun ncpStrafeBranch(player: net.minecraft.client.entity.EntityPlayerSP, ae: Int, useTicks: Int, allowed: Double) {
        val potion = speedPotionLevel

        if (player.onGround && PortedMove.isMoving()) {
            PortedMove.strafe(allowed)
            player.jump()
        }

        if (useTicks == 1 && !suppressBoost) {
            if (potion >= 2) {
                PortedMove.strafe(0.48)
            } else if (potion == 1) {
                PortedMove.strafe(0.4)
            } else {
                PortedMove.strafe(0.334)
            }

            player.motionY += 0.03f
            PortedMove.preventDiagonalSpeed()
        }

        if (useTicks == 2 && !suppressBoost) {
            val oldX = player.motionX
            val oldZ = player.motionZ
            PortedMove.strafe()
            player.motionZ = (player.motionZ * 1.0 + oldZ * 3.0) / 4.0
            player.motionX = (player.motionX * 1.0 + oldX * 3.0) / 4.0
            PortedMove.useDiagonalSpeed()
            player.motionY -= 0.005f
        }

        if (useTicks == 3 && !suppressBoost) {
            PortedMove.useDiagonalSpeed()
            player.motionY -= 0.04f
        }

        if (useTicks == 4 && (ae > 1 || damageBoost) && !suppressBoost) {
            PortedMove.useDiagonalSpeed()
            player.motionY -= 0.05f
        }

        if (useTicks == 5 && !suppressBoost) {
            PortedMove.useDiagonalSpeed()
        }

        if (useTicks == 6 && !suppressBoost && ae > 10 && damageStrafeHypixelFlyDisabler) {
            PortedMove.useDiagonalSpeed()
            player.motionZ *= 1.01
            player.motionX *= 1.01
        }

        if (useTicks == 7 && !suppressBoost && ae > 10 && damageStrafeHypixelFlyDisabler) {
            PortedMove.useDiagonalSpeed()
            if (!player.isPotionActive(Potion.moveSpeed)) {
                player.motionZ *= 1.01
                player.motionX *= 1.01
            } else {
                player.motionZ *= 1.008
                player.motionX *= 1.008
            }
        }

        if (useTicks == 8 && PortedMove.blockAt(0.0, player.motionY * 3.0, 0.0) != Blocks.air) {
            PortedMove.useDiagonalSpeed()
            if (alwaysGlideStrafe) {
                player.motionY += 0.075f
                PortedMove.strafe()
            }
        }

        if (useTicks == 9 && PortedMove.blockAt(0.0, player.motionY * 3.0, 0.0) != Blocks.air) {
            PortedMove.useDiagonalSpeed()
            if (alwaysGlideStrafe) {
                PortedMove.strafe()
            }
        }

        if (PortedMove.blockAt(0.0, player.motionY, 0.0) != Blocks.air) {
            if (potion >= 2) {
                PortedMove.strafe(0.45)
            } else if (potion == 1) {
                PortedMove.strafe(0.375)
            } else {
                PortedMove.strafe(0.31)
            }
        }

        if (scaffoldEnabled && mc.gameSettings.keyBindJump.isKeyDown) {
            if (player.onGround && pendingToggle) {
                Speed.toggle()
                pendingToggle = false
            }
        } else if ((suppressBoost || useTicks > 4 || player.onGround) && pendingToggle) {
            Speed.toggle()
            pendingToggle = false
        }

        if (PortedMove.speed() <= 0.125) {
            PortedMove.strafe(0.125)
        }

        val delta = hypot(
            player.motionX - PortedTrackers.prevMotionX,
            player.motionZ - PortedTrackers.prevMotionZ
        )
        if (delta <= 0.0125) {
            PortedMove.strafe()
        }
    }

    /** 「Low Strafe」子式（默认）。 */
    private fun lowStrafeBranch(player: net.minecraft.client.entity.EntityPlayerSP, ae: Int, useTicks: Int, allowed: Double) {
        val potion = speedPotionLevel

        PortedMove.useDiagonalSpeed()

        if (player.isPotionActive(Potion.moveSpeed) && player.onGround) {
            if (scaffoldEnabled && mc.gameSettings.keyBindJump.isKeyDown ||
                recentlyCollided ||
                !(PortedMove.speed() < allowed) && ae <= 5 && damageBoost
            ) {
                if (!scaffoldEnabled || recentlyCollided ||
                    !(PortedMove.speed() < 0.29) && ae <= 5 && damageBoost
                ) {
                    if (PortedMove.speed() < 0.29 && ae < 5 && damageBoost && !recentlyCollided) {
                        player.jump()
                        if (ae > 1) {
                            PortedMove.strafe()
                        }
                    } else {
                        PortedMove.strafe(0.29)
                        player.jump()
                    }
                } else {
                    PortedMove.strafe(0.29)
                    player.jump()
                }
            } else {
                PortedMove.strafe(allowed)
                player.jump()
            }
        } else if (player.onGround) {
            if (recentlyCollided || !(PortedMove.speed() < allowed) && ae <= 5 && damageBoost) {
                if (scaffoldEnabled) {
                    PortedMove.strafe(0.23)
                    player.jump()
                } else if (!(PortedMove.speed() < PortedMove.vd()) && ae <= 5 && damageBoost) {
                    player.jump()
                    if (ae > 1) {
                        PortedMove.strafe()
                    }
                } else {
                    PortedMove.strafe(PortedMove.vd())
                    player.jump()
                }
            } else {
                PortedMove.strafe(allowed)
                player.jump()
            }
        }

        if (useTicks == 1 && !suppressBoost) {
            liftBoosted = true
            player.motionY += 0.057f
            if (!player.isPotionActive(Potion.moveSpeed) ||
                scaffoldEnabled && mc.gameSettings.keyBindJump.isKeyDown ||
                potion < 2 ||
                suppressBoost ||
                recentlyCollided ||
                !(PortedMove.speed() < 0.48) && ae <= 5 && damageBoost
            ) {
                if (!player.isPotionActive(Potion.moveSpeed) ||
                    potion < 2 ||
                    !(PortedMove.speed() < 0.4) && ae <= 5 && damageBoost
                ) {
                    if (recentlyCollided ||
                        !player.isPotionActive(Potion.moveSpeed) ||
                        potion != 1 ||
                        !(PortedMove.speed() < 0.41) && ae <= 5 && damageBoost
                    ) {
                        if (!recentlyCollided ||
                            !player.isPotionActive(Potion.moveSpeed) ||
                            potion != 1 ||
                            !(PortedMove.speed() < 0.41) && ae <= 5 && damageBoost
                        ) {
                            if (!(PortedMove.speed() < 0.33) && ae <= 3 && damageBoost) {
                                PortedMove.strafe()
                            } else {
                                PortedMove.strafe(0.3325)
                            }
                        } else {
                            PortedMove.strafe(0.35)
                        }
                    } else {
                        PortedMove.strafe(0.41)
                    }
                } else {
                    PortedMove.strafe(0.4)
                }
            } else {
                PortedMove.strafe(0.4875)
            }
        }

        if (useTicks == 2 && airStrafe) {
            val oldX = player.motionX
            val oldZ = player.motionZ
            player.motionZ = (player.motionZ * 1.0 + oldZ * 2.0) / 3.0
            player.motionX = (player.motionX * 1.0 + oldX * 2.0) / 3.0
        }

        if (useTicks == 3 && !suppressBoost) {
            player.motionY -= 0.1309f
        }

        if (useTicks == 4 && !suppressBoost) {
            player.motionY -= 0.2
        }

        val moveAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(PortedMove.direction()))
        val motionAngle = MathHelper.wrapAngleTo180_double(
            Math.toDegrees(atan2(player.motionZ, player.motionX)) - 90.0
        )
        val angleDelta = abs(moveAngle - motionAngle)
        val delta = hypot(
            player.motionX - PortedTrackers.prevMotionX,
            player.motionZ - PortedTrackers.prevMotionZ
        )
        val flag2 = !(angleDelta < 5.0) && !(angleDiff(moveAngle, motionAngle) > 90.0) &&
            !pendingToggle && !(delta < 0.0125)

        if (useTicks == 6 && !suppressBoost &&
            PortedMove.collidesAt(player.motionY * 3.0) &&
            (alwaysGlideStrafe || flag2) &&
            (PortedMove.speed() < allowed * 0.994 || !damageBoost || ae > 5) &&
            (!suppressBoost || !damageStrafeHypixelFlyDisabler) &&
            ae > 1
        ) {
            player.motionY += 0.075
            PortedMove.strafe(PortedMove.speed())
            val current = PortedMove.speed()
            if ((current < allowed || player.motionX == 0.0 || player.motionZ == 0.0) &&
                !suppressBoost && !recentlyCollided &&
                player.isPotionActive(Potion.moveSpeed) && !scaffoldEnabled
            ) {
                PortedMove.strafe(allowed * 0.994)
            } else if (!suppressBoost && !scaffoldEnabled &&
                (current < allowed || player.motionX == 0.0 || player.motionZ == 0.0)
            ) {
                PortedMove.strafe(allowed - 0.05)
            }
        }

        if (useTicks < 7 && PortedMove.blockAt(0.0, player.motionY, 0.0) != Blocks.air &&
            player.isPotionActive(Potion.moveSpeed) && !onUnevenGround
        ) {
            verticallyBlocked = true
            collisionExpireTick = player.ticksExisted + 9
            recentlyCollided = true
        }

        if (useTicks == 7 && !suppressBoost &&
            PortedMove.collidesAt(player.motionY * 2.0) &&
            !scaffoldEnabled &&
            (PortedMove.speed() < allowed * 1.1 || !damageBoost || ae > 5) &&
            ae > 1
        ) {
            PortedMove.strafe(if (alwaysGlideStrafe) PortedMove.speed() else allowed * 1.1)
        }

        if (!PortedMove.collidesAt(player.motionY) ||
            ae <= 1 ||
            suppressBoost ||
            uHCMode ||
            (useTicks <= 6 || !alwaysGlideStrafe) && (useTicks <= 6 || alwaysGlideStrafe) ||
            appliedGlideBoost ||
            !(PortedMove.speed() < allowed * 1.095) && damageBoost && ae <= 5
        ) {
            if ((!PortedMove.collidesAt(player.motionY) || suppressBoost || ae <= 1 || uHCMode ||
                useTicks <= 6 || !alwaysGlideStrafe) &&
                (
                    useTicks <= 6 || alwaysGlideStrafe || appliedGlideBoost ||
                        !(PortedMove.speed() < allowed) && damageBoost && ae <= 5
                    )
            ) {
                if (!PortedMove.collidesAt(player.motionY) || useTicks <= 5 || appliedGlideBoost ||
                    ae <= 1 || !(PortedMove.speed() < allowed) && damageBoost && ae <= 5
                ) {
                    if (PortedMove.collidesAt(player.motionY) && useTicks > 5 && !appliedGlideBoost &&
                        ae > 1 && damageBoost
                    ) {
                        PortedMove.strafe()
                    }
                } else {
                    PortedMove.strafe(allowed)
                    appliedGlideBoost = true
                }
            } else {
                PortedMove.strafe(allowed)
                appliedGlideBoost = true
            }
        } else {
            if (alwaysGlideStrafe) {
                if (potion >= 2) {
                    PortedMove.strafe(allowed * 1.093)
                } else {
                    PortedMove.strafe(allowed * 1.095)
                }
            } else if (potion >= 2) {
                PortedMove.strafe(allowed * 1.093)
            } else {
                PortedMove.strafe(allowed * 1.095)
            }

            appliedGlideBoost = true
        }

        if (scaffoldEnabled && mc.gameSettings.keyBindJump.isKeyDown) {
            if (player.onGround && pendingToggle) {
                Speed.toggle()
                pendingToggle = false
            }
        } else if ((suppressBoost || useTicks > 3 || player.onGround) && pendingToggle) {
            Speed.toggle()
            pendingToggle = false
        }

        val delta2 = hypot(
            player.motionX - PortedTrackers.prevMotionX,
            player.motionZ - PortedTrackers.prevMotionZ
        )
        if (delta2 < 0.0125) {
            PortedMove.strafe()
        }

        if (PortedMove.speed() < 0.1245 && frictionOverride) {
            PortedMove.strafe(0.1245)
        }

        if (useTicks > 1 && !player.isCollidedHorizontally) {
            PortedMove.moveFlying(9.0E-4)
        }
    }

    override fun onJump(event: JumpEvent) {
        val player = mc.thePlayer ?: return
        if (player.isInLava || player.isInWeb) {
            event.cancelEvent()
        }

        if (!suppressBoost && type == "Low Strafe" && alternateMotion) {
            event.motion = 0.42001f
        } else if (type == "Low Strafe" && alternateMotion) {
            event.motion = 0.42001f
        }
    }

    /** 以指定角度平移速度（对应 MoveUtil.a(speed, angle)）。 */
    private fun strafeAt(speed: Double, angle: Float) {
        val player = mc.thePlayer ?: return
        if (!PortedMove.isMoving()) return
        val rad = Math.toRadians(angle.toDouble())
        player.motionX = -MathHelper.sin(rad.toFloat()) * speed
        player.motionZ = MathHelper.cos(rad.toFloat()) * speed
    }

    private fun angleDiff(a: Double, b: Double): Double =
        abs(MathHelper.wrapAngleTo180_double(a - b))
}
