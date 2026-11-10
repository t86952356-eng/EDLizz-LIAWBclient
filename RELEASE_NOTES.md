# Lizz b12.11 — Changelog (Mode Expansion II + Fixes)

## Fixes

- **NoSlow / Velocity**: the `-Watchdog Prediction` mode was renamed to **`Watchdog-Protection`** in both modules, matching the naming of the Watchdog-Protection family in Speed.
- **Speed / Polar**: sprinting is now **legit** — the mode only sprints with pure forward input (no diagonal / backward sprinting), like the legal-sprint logic used by the Grim family modes.
- **NoSlow — Sword + KillAura**: all new NoSlow modes (`WatchdogNCP`, `Intave`, `Matrix`, `Watchdog-Protection`, `Grim`, `Grim 1.9`) now properly detect "using a sword" while **KillAura auto-blocks** (and while manually blocking in the UpdatedNCP style) by re-using the module's own using-item logic — the sword bypass now runs while fighting with KillAura.
- **Velocity — Damage Boost**: the `Damage Boost (not for hypixel)` option of `WatchdogNCP-New` (and the `Damage Boost` option of `Vulcan-New`) now actually works — when knocked back while moving, the horizontal knockback is redirected into your movement direction (scaled by `Damage Boost Speed`) and the incoming packet is cancelled.

## Criticals — new modes

- **WatchdogNCP** — ground-hugging crit sequence: attacking on the ground (after `WatchdogNCP Delay`, 0–1000 ms) marks a pending crit, then the mode holds a micro-fall (position +0.001, reported onGround=false) with step height 0.1 and SafeWalk for a few ticks, cancels jumps while using an item during the window and then restores step height / SafeWalk.
- **Vulcan** — fake-fall crits: for 18 ticks after receiving knockback, while the tracked fall distance is below 1.8, every PRE motion packet is reported as airborne (onGround=false).
- **Verus** — classic offset crits (`Verus Delay`, 0–1000 ms): while using an item with more than 2 ticks left and still hurt, two 0.0625 / 0.0 position packets make the next hit a critical.

## LongJump — new modes

- **Watchdog Fire Ball** — full fireball longjump: automatically switches to the fire charge slot while looking straight down (89°), places the fireball, holds the incoming velocity + transactions + keep-alives, replays them on the 13th item-use tick to launch, keeps a +45° yaw spoof while airborne and auto-disables after the boost (landing auto-disable applies to all new LongJump modes via `AutoDisable`).
- **Grim** — 2x timer longjump with a per-frame pitch jitter: jumps on the first strafe tick, bursts 20 silent flying packets, then freezes in place (canceling one flying packet per cycle) until either a lagback (S08) is absorbed, a downward velocity arrives or 20 cycles time out.
- **Vulcan** — scripted tick-sequence longjump: jump on tick 1, 1.3x / 1.05x motion boosts, vertical pulls (motionY 2.0 / +0.04 / -0.098) and a forced grounded re-jump at tick 62 and in the -1.53 motion window.

## Phase — new modes

- **WatchdogNCP-Blink** — SkyWars cage phase: on the countdown chat message ("The game starts in 3 seconds!" and variants) it starts a 4-second full outgoing blink and removes the collision boxes of glass / barrier blocks; when the cages open ("Cages opened! FIGHT!") everything is released.
- **Vulcan** — solid-block phase: on enable it shifts you one block down, removes block collision while inside (leaving a floor plate below), fixes the camera bob, holds a 0.306 strafe (speed-potion aware) inside blocks, alternates forward input every other tick, auto-jumps after a setback (S08) and disables itself when you end up outside a block.
- **Grim** — packet-hold phase: while inside a block every C03 flying packet is held back and `Semi Packets` (1–15) silent C06 packets are sent from your position; when you leave the block the mode releases with a `Release Mode` sequence (Simple / Double / Desync / None) and flushes all held packets.

## Step — new modes

- **Vulcan** — step height rises to 1.0 twelve ticks after the last full-block step; every full-block step halves the timer and sends a +0.5 position packet to compensate.
- **WatchdogNCP** — full-block step bypass: step height 1.0 on the ground (not in liquid, not while jumping, not while scaffolding), the step itself strafes at the allowed speed, sends the 0.42 / 0.75 / 1.0 stair packet sequence with a 0.25 timer and temporarily disables the Speed module for 300 ms around the step.

## InventoryMove — new Mode system

The module now has a **`Mode`** option:

- **Custom** — the complete original behavior with all previous settings (NotInChests, AACAdditionPro, Intave, SaveC0E, Onlychest, NoSprintWhenClosed, FreeLook, NoMove family, SilentlyCloseAndReopen, ReopenOnClick, InventoryMotion).
- **WatchdogNCP** — full watchdog chest/inventory bypass: refills movement keys from real key states, cancels sprinting in GUIs, limits speed in the inventory/chest (speed-potion aware strafe values), silently closes the survival inventory when no bad packets were sent since the last flying packet, measures the chest-open latency (`Measure Chest Open`) via outgoing chest-opens / NPC interactions and suppresses movement input until the chest is confirmed open (± `Ticks`), with an alternative `Prediction Mode` that stops sprinting instead. Options: `Prediction Mode`, `Ticks`, `Measure Chest Open`.
- **Grim** — Grim-friendly inventory move: refills movement keys from real key states in any GUI except chat / ClickGUI and cancels sprinting inside inventory / chest screens.

Also fixed in this module: the `Intave` setting never actually applied because its GUI check was evaluated only once at startup — it is now evaluated live.

## AntiVoid — new modes

- **WatchdogNCP** — predictive void protection: tracks your last safe position, simulates your horizontal trajectory (up to `WatchdogNCP Distance` × 40 steps) and when every predicted position hangs over air it starts a full outgoing blink; after the tracked fall distance exceeds the distance it silently teleports you back to the safe position (+0.098) and releases.
- **Vulcan** — void-floor protection: falling more than `Vulcan Distance` with no ground within 10 blocks enables a fake air floor below you, locks movement to 0.1 speed, zeroes jump motion, disables Speed while falling and restores it on landing / setback.

## Technical notes

- All new mode families follow the same dispatch model as b12.10 (mode objects + module-level routing), re-using the shared packet trackers, blink queues and movement helpers.
- New mode-specific infra: fall-distance tracking for crits / antivoid, a `movementKeys` refill helper, equivalent reimplementations of vanilla `moveFlying` with overridden forward for the StrafeEvent-based step/phase/antivoid modes and local `RotationUtils.currentRotation` injection for the rotation spoofing modes.
