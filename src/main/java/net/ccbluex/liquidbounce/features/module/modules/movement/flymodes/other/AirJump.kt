/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.event.BlockBBEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.minecraft.block.BlockAir
import net.minecraft.util.AxisAlignedBB
import kotlin.math.floor

/**
 * Fly「AirJump」模式 —— 空中跳板：
 * 落地时钩子 jump()；同时把脚下的「空气方块」碰撞盒替换成一个 30×2×30 的
 * 大跳板（锚定在方块位置，仅在低于锚点高度 +1 或按住跳跃键、且未按潜行时），
 * 于是每一段跳跃都能落在无形跳板上 → 无限空中跳跃。锚点高度 y 在按住
 * 跳跃/潜行时跟随当前高度更新，上升时也随之抬高。
 */
object AirJump : FlyMode("AirJump") {

    private var anchorY = 0.0

    override fun onEnable() {
        anchorY = floor(mc.thePlayer?.posY ?: 0.0)
    }

    override fun onMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return

        if (mc.gameSettings.keyBindJump.isKeyDown || mc.gameSettings.keyBindSneak.isKeyDown) {
            anchorY = player.posY
        }

        if (player.onGround) {
            player.jump()
        }
    }

    override fun onBB(event: BlockBBEvent) {
        val player = mc.thePlayer ?: return

        if (event.block is BlockAir &&
            !mc.gameSettings.keyBindSneak.isKeyDown &&
            (player.posY < anchorY + 1.0 || mc.gameSettings.keyBindJump.isKeyDown)
        ) {
            if (event.y.toDouble() < player.posY) {
                event.boundingBox = AxisAlignedBB(-15.0, -1.0, -15.0, 15.0, 1.0, 15.0)
                    .offset(event.x.toDouble(), event.y.toDouble(), event.z.toDouble())
            }
        }
    }

    override fun onMoveInput(event: MovementInputEvent) {
        event.originalInput.sneak = false
    }
}
