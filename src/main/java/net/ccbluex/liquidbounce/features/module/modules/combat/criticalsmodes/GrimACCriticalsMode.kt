/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 *
 * Criticals「GrimAC」模式 —— 由原独立模块 GrimCritical 整体移入（Jump / AutoFreeze / OnlyHorJump）。
 *
 * 跳跃一律走 jump() 钩子（原版 EntityLivingBase.jump()：motionY/sprint 冲量/
 * jumpTicks 全部由原版逻辑处理），不直接改 motion。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.criticalsmodes

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.Freeze
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.server.S08PacketPlayerPosLook

object GrimACCriticalsMode : CriticalsMode("GrimAC") {

    /** 子模式（源 GrimCritical 的 Mode：Jump / AutoFreeze / OnlyHorJump），由 Criticals 同步。 */
    var grimacMode = "Jump"

    // OnlyHorJump：横向速度倍率（1 = 完全不变）
    var onlyHorSpeed = 1f

    // AutoFreeze：下落期间冻结时间的占比
    // 100 = 全程冻结（旧行为）；25 = 每 4 tick 下落冻结 1 tick；50 = 隔 tick 冻一次
    var freezePercent = 100

    private var attacking = false
    private var stuckEnabled = false

    /** AutoFreeze：本次下落已经过了多少 tick（用于按百分比分配冻结） */
    private var fallTicks = 0

    // OnlyHorJump 的 stuck 状态：只锁 Y
    private var onlyHorStuck = false
    private var onlyHorY = 0.0

    override fun onEnable() {
        stuckEnabled = false
        onlyHorStuck = false
    }

    override fun onDisable() {
        // 复位：AutoFreeze 开着的 Freeze 要关掉
        if (stuckEnabled) {
            Freeze.state = false
            stuckEnabled = false
        }
        onlyHorStuck = false
        attacking = false
    }

    override fun onAttack(event: AttackEvent) {
        val player = mc.thePlayer ?: return
        if (player.onGround && event.targetEntity is EntityLivingBase) {
            val entity = event.targetEntity as EntityLivingBase
            // 目标 hurtTime == 9（刚被打过）→ 只补客户端暴击粒子，不再跳
            if (entity.hurtTime == 9) {
                player.onCriticalHit(entity)
            }
            attacking = true
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (grimacMode == "OnlyHorJump") {
            // 有目标且在地面 → 钩子跳跃
            if (KillAura.target != null && player.onGround) {
                player.jump()
            }

            // 下落中 → 进入「只锁 Y」的 stuck：客户端锁死高度，
            // 发出去的 C03 自然就是冻结的 Y；横向 motion 完全不动（可调 %）
            if (KillAura.target != null && player.fallDistance > 0 && !onlyHorStuck) {
                onlyHorStuck = true
                onlyHorY = player.posY
            }

            if (onlyHorStuck) {
                player.motionY = 0.0
                player.setPosition(player.posX, onlyHorY, player.posZ)
                if (onlyHorSpeed < 1f) {
                    player.motionX *= onlyHorSpeed
                    player.motionZ *= onlyHorSpeed
                }
                // fallDistance 保持 >0（悬停不落地不重置），攻击持续判暴击条件
                if (player.fallDistance <= 0f) {
                    player.fallDistance = 0.1f
                }
            }

            // 目标没了 → 解除 stuck
            if (KillAura.target == null && onlyHorStuck) {
                onlyHorStuck = false
            }
            return
        }

        if (grimacMode != "AutoFreeze") {
            return
        }

        // 有目标且在地面 → 钩子跳跃（起跳产生 fallDistance）
        if (KillAura.target != null && player.onGround) {
            player.jump()
        }

        // ---- 只在下落时冻结（上升阶段不动）----
        val falling = KillAura.target != null && !player.onGround && player.motionY <= 0.0

        if (!falling) {
            // 落地 / 上升 / 无目标 → 复位并解除冻结
            fallTicks = 0
            if (stuckEnabled) {
                Freeze.state = false
                stuckEnabled = false
            }
            return
        }

        fallTicks++

        // 按百分比分配：25% → 每 4 tick 冻结 1 tick；50% → 每 2 tick 冻 1 tick
        val pct = freezePercent.coerceIn(0, 100)
        val shouldFreeze = when {
            pct >= 100 -> true
            pct <= 0 -> false
            else -> {
                val cycle = (100.0 / pct).toInt().coerceAtLeast(1)
                fallTicks % cycle == 0
            }
        }

        if (shouldFreeze) {
            Freeze.state = true
            stuckEnabled = true
        } else if (stuckEnabled) {
            // 本 tick 不冻结：松开，让这一 tick 正常下落
            Freeze.state = false
            stuckEnabled = false
        }
    }

    // 被服务端拉回（lagback）→ 立刻解除 stuck，避免位置对不上
    override fun onPacket(event: PacketEvent) {
        if (onlyHorStuck && event.packet is S08PacketPlayerPosLook) {
            onlyHorStuck = false
        }
    }

    override fun onStrafe() {
        // Jump 模式：攻击后在 Strafe 钩子里跳（保证这一 tick 的移动方向已确定）
        if (grimacMode == "Jump" && attacking && mc.thePlayer?.onGround == true) {
            mc.thePlayer.jump()
            attacking = false
        }
    }
}
