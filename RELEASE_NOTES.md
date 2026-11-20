# Lizz b12.15 — Changelog (4 new modes)

## Fly — new mode

- **WatchdogDamage** — a six-state damage flight: on the first grounded tick it sends the self-damage ladder (3.1 blocks of up/down position packets, jump-potion aware, rate-limited against your recent packet count), then flies with the classic stage machine (boost → jump ×2.145 → 0.66 recovery → triple-formula friction decay) while keeping the timer boosted for a configurable window; every other grounded tick nudges your client position by a 1e-9 offset. If you enable it high in the air (**Drop**), it freezes your fall over the void — all outgoing packets are held (blinked) while nothing solid is below you, and it auto-disables on ground, low health or after 3s. The **Disabler** branch can start a 3-second free-flight window three ways: throwing an **Ender pearl** (silent slot + 90° down rotation, the window opens when the server accepts the pearl teleport), throwing a **Fireball**, or the **Packet** hover (y+0.2/y+0.1 position ladder, then waits ~4s for the check to drop). Options: Speed, Damage (Normal/Wait/None), Timer enable/speed/time, Drop enable/speed, Disabler enable + methods (Ender pearl / Fireball / Packet) + horizontal & vertical speed, and Bobbing (None / Normal only / Always).

## LongJump — new mode

- **WatchdogNCP-New** — speed-potion friction long jump: requires an active Speed potion (auto-disables with a notice otherwise); charges the initial speed, jumps with potion-aware 0.42 motion ×2.149, then on the first airborne tick recovers with the 0.66 drop ×2.2 boost (reduced per Jump potion level) and afterwards decays through the 159/25 friction denominators — auto-disabling the moment you land.

## NoSlow — new mode

- **WatchdogNCP-Full** — the sword packet-hold no-slow: the moment you right-click with a sword, your use/swing/attack (and slot-switch-during-attack) packets are held back and only flushed **after** that tick's position packet, so the server sees the block start at your settled position; right-clicks aiming at interactive blocks (chests, doors, fences, …) are suppressed so you never accidentally open them, sneaking explicitly allows interacting, and the item slowdown itself is fully removed while the mode is on.

## Criticals — new mode

- **WatchdogNCP-Packet** — server-perspective crits: tracks the fall distance the server actually sees (from your reported positions), and while you're grounded and not already offset it lifts the outgoing motion packet by 0.0301 so the server believes you're airborne — your next hits crit without any fake packets. When the predicted fall distance crosses 3 blocks it reports onGround instead (vanilla would cancel crits), and attacks played while the server thinks you're falling get the critical hit animation client-side.
