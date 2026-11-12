package net.ccbluex.liquidbounce.utils.attack

object SilentAttackManager {
    @Volatile
    private var silentAttack = false

    /**
     * 检查是否处于静默攻击模式
     */
    @JvmStatic
    fun isSilentAttack(): Boolean = silentAttack

    /**
     * 在静默攻击模式下执行代码块
     */
    @JvmStatic
    fun <T> withSilentAttack(block: () -> T): T {
        silentAttack = true
        try {
            return block()
        } finally {
            silentAttack = false
        }
    }
}
