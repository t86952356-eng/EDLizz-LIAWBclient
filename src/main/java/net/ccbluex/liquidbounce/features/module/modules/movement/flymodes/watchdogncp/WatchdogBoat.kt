/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.watchdogncp

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.StrafeEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.handlePackets
import net.minecraft.entity.item.EntityBoat
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import org.lwjgl.input.Keyboard
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Fly「Watchdog-ProtectionBoat」模式 —— 简历坐船飞行（Watchdog Boat）：
 * 骑上船 → 挥手并对船发出 C02 ATTACK（服务端把船视为已破坏），期间
 * 自己的 C0B START_SNEAKING 被取消（保持骑乘状态）、收到的 S32 事务全部缓冲；
 * 船死亡/从世界移除后进入飞行阶段：motionX/Z 归零、Y 由跳跃/潜行键 ±0.42 控制
 * （仅在非整数高度时写入 —— 到达整格即停），水平速度由 moveFlying 的
 * friction（Speed 设置）驱动；两次阶段切换各冻结 1 秒（motion 归零）。
 * 切换世界时自动关闭模块。
 */
object WatchdogBoat : FlyMode("Watchdog-ProtectionBoat") {

    /** Speed（默认 1，0.1..9.5，步进 0.1）。 */
    var flySpeed = 1f

    private var boat: EntityBoat? = null

    /** 阶段 1：已对船发出攻击包。 */
    private var attacked = false

    /** 阶段 2：船已消失 → 飞行阶段。 */
    private var flying = false

    private var freezeUntil = 0L

    /** ±0.42 的本 tick 垂直速度。 */
    private var vertical = 0.0

    /** 阶段 1 期间缓冲的 S32 事务（模块关闭时统一补发）。 */
    private val bufferedPackets = ConcurrentLinkedDeque<Packet<*>>()

    /** 世界变化检测（源挂在 WorldChangeEvent 自动关闭模块）。 */
    private var lastWorld: Any? = null

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        flushBufferedPackets()
        reset()
        zeroMotion()

        val player = mc.thePlayer ?: return

        // 源：恢复潜行键的真实物理按键状态（飞行期间被强制按住=false）
        val sneakKey = mc.gameSettings.keyBindSneak
        val keyCode = sneakKey.keyCode
        if (keyCode > 0) {
            sneakKey.pressed = Keyboard.isKeyDown(keyCode)
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        // 世界切换 → 自动关闭（源：WorldChangeEvent → toggle()）
        val world = mc.theWorld
        if (lastWorld == null) lastWorld = world
        if (world !== lastWorld) {
            lastWorld = world
            if (Fly.state) Fly.state = false
            return
        }

        handleBoatPhases(player)

        if (freezeActive()) {
            vertical = 0.0
            zeroMotion()
            return
        }

        if (!flying) return

        vertical = 0.0

        if (mc.currentScreen != null) return

        if (Keyboard.isKeyDown(mc.gameSettings.keyBindJump.keyCode)) {
            vertical = 0.42
        } else if (Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.keyCode)) {
            vertical = -0.42
        }

        // 防止潜行键导致客户端下船
        mc.gameSettings.keyBindSneak.pressed = false
    }

    /** 源 hq()：骑船 → 攻击包（阶段 1）；船没了 → 飞行（阶段 2）。 */
    private fun handleBoatPhases(player: net.minecraft.client.entity.EntityPlayerSP) {
        val riding = player.ridingEntity

        if (freezeActive()) {
            zeroMotion()
        }

        if (!attacked && riding is EntityBoat) {
            boat = riding
            player.swingItem()
            sendPacket(C02PacketUseEntity(riding, C02PacketUseEntity.Action.ATTACK), triggerEvent = false)
            attacked = true
            freezeUntil = System.currentTimeMillis() + 1000L
            zeroMotion()
        }

        if (attacked && !flying && boatGone()) {
            flying = true
            freezeUntil = System.currentTimeMillis() + 1000L
            zeroMotion()
        }
    }

    /** 源 hr()：船已死亡或从世界移除。 */
    private fun boatGone(): Boolean {
        val theBoat = boat ?: return true
        val world = mc.theWorld ?: return true
        if (theBoat.isDead) return true
        return world.getEntityByID(theBoat.entityId) !== theBoat
    }

    private fun freezeActive() = freezeUntil > System.currentTimeMillis()

    /** 源 ht()：三向 motion 归零。 */
    private fun zeroMotion() {
        val player = mc.thePlayer ?: return
        player.motionX = 0.0
        player.motionY = 0.0
        player.motionZ = 0.0
    }

    override fun onStrafe(event: StrafeEvent) {
        val player = mc.thePlayer ?: return

        if (freezeActive()) {
            zeroMotion()
            event.strafe = 0.0f
            event.forward = 0.0f
            event.friction = 0.0f
            return
        }

        if (!flying) return

        // 只在非整数高度写 motionY —— 升/降到整格高度即精确停住
        if (player.posY % 1.0 != 0.0) {
            player.motionY = vertical
        }
        player.motionX = 0.0
        player.motionZ = 0.0

        // 水平速度全部由 moveFlying 的 friction 驱动
        event.friction = flySpeed
    }

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if (event.eventType == EventState.SEND) {
            // 骑船/持有船期间：取消自己的 C0B START_SNEAKING（下船意图）
            if (packet is C0BPacketEntityAction && packet.action == C0BPacketEntityAction.Action.START_SNEAKING) {
                val player = mc.thePlayer ?: return
                if (player.ridingEntity is EntityBoat || boat != null) {
                    event.cancelEvent()
                }
            }
            return
        }

        // 阶段 1（攻击包之后）：缓冲收到的 S32 事务
        if (attacked && packet is S32PacketConfirmTransaction) {
            bufferedPackets.offer(packet)
            event.cancelEvent()
        }
    }

    /** 源 hv()：关闭时把缓冲的 S32 重新交给网络处理器。 */
    private fun flushBufferedPackets() {
        val netHandler = mc.thePlayer?.sendQueue ?: run {
            bufferedPackets.clear()
            return
        }
        while (true) {
            val packet = bufferedPackets.poll() ?: break
            try {
                handlePackets(packet)
            } catch (_: Throwable) {
            }
        }
    }

    private fun reset() {
        boat = null
        attacked = false
        flying = false
        freezeUntil = 0L
        vertical = 0.0
        bufferedPackets.clear()
    }
}
