# Lizz b12.10 — Changelog (Major Mode Expansion)

## NoSlow — new modes

The NoSlow module now ships a whole new mode family next to `Custom`. All new modes share the module-level **Food / Potion / Sword / Bow** toggles that decide which item categories they act on.

- **WatchdogNCP** — slab-aware noslow with air-start eating: detects slab/uneven positions, offsets the outgoing Y position while grounded, applies in-air item usage and sends a slot-swap bypass for swords (with a `Slow down on Slabs` option).
- **Intave** — queues all outgoing packets while using an item and refreshes the server-side item state every 5 ticks with a RELEASE_USE_ITEM + re-use packet pair (sword), with dedicated handling for food and bow.
- **Matrix** — segmented movement control while consuming: keeps a 0.0265 strafe while the item still has ticks left and gently decelerates on the final tick (0.992 / 0.99 factor depending on Speed state).
- **-Watchdog Prediction** — next-generation watchdog bypass: after `Max Ping Spoof` ticks of usage it starts a full outgoing-packet blink to simulate high ping, automatically releases the use key at the `When to finish eating` threshold, keeps sprint alive while not blinking (`Non-Blink Speed Bypass`) and offsets the outgoing look packets by +45° while eating. Swords are bypassed via release + re-use packets (cross-version 1.19+ servers get a proper 1.19 Use Item packet through the Via pipeline).
- **Grim** — periodic held-item slot swap (`slot % 8 + 1` and back) on every PRE motion tick while using food / potion / sword / bow.
- **Grim 1.9** — cross-version bypass: on 1.9+ servers it injects a 1.9 Use Item packet into the Via pipeline every motion tick (with a 1.19+ variant carrying hand + sequence), and reminds you that 1.9+ is required on direct 1.8 connections.

## Velocity — new modes

- **WatchdogNCP-New** — full watchdog anti-knockback suite: velocity stacking (73% chance while airborne), ping spoof (holds transactions / keep-alives while blinking), watchdog backtracking with target tracking, red/green backtrack box rendering, damage-boost interplay with the Speed module and always-cancel-vertical recovery. Options: `Stack`, `Ping Spoof`, `Always Cancel Vertical`, `Watchdog Backtrack`, `Damage Boost (not for hypixel)`, `Damage Boost Speed`.
- **Vulcan-New** — the vulcan-flavored variant of the same engine: stacks velocities, holds packets while blinking and re-applies the stored velocity on the next ground contact, with its own `Backtrack` and `Damage Boost` options.
- **Matrix** — applies the vertical component directly and cancels the packet while moving, with post-velocity motion correction on the strafe stage.
- **Intave** — active sprint-reset handling around attacks (0.6 motion reduction + sprint cancel), gated by the new shared `On Swing` option.
- **Grim-New** — freezes movement after receiving velocity while airborne, holds transactions / entity / teleport / metadata packets, re-uses a block interaction packet to unfreeze and (optionally) spoofs a 90° downward look with `Rotate` and `Smart Combat (for High Ping)`.
- **Grim 2** — packet-level rotation sanitizer: replaces perfectly axis-aligned rotations (multiples of 90°) with tiny random noise (`Rotation Noise`), plus `Fake S08` teleport handling and raw velocity cancellation, each with a debug log option.
- **-Watchdog Prediction** — holds the velocity packet (plus transactions and keep-alives) until your look direction matches the knockback direction within 15°, then releases everything at once; includes `Velocity to let through` and `Prevent Ghosting`.
- **MMC** — zero horizontal motion on the first tick after velocity, heavy downward correction when jumping, and item-timing based movement nudges.

## Speed — new modes

- **WatchdogNCP-New** — the flagship watchdog speed with three sub-types (`Strafe`, `Low Strafe`, `NCP Strafe`): ice detection with multipliers, damage boost with attack-count interplay, glide strafing, friction overrides, collision handling and an auto-toggle safeguard for scaffold users. Options: `Air Strafe`, `Always Glide Strafe`, `Friction Override`, `Friction Full Disabler`, `Damage Boost`, `Damage Strafe (Hypixel Fly Disabler)`, `UHC Mode`, `Alternate Motion`.
- **Matrix** — timer-burst style speed: on-ground multiplication, 0.195 baseline strafe, velocity-aware corrections and a `Timer Boost (on sneak)` option with reset packets after sneaking.
- **Polar** — lightweight 1.009 timer loop with jump cycling and moveFlying nudges.
- **Old NCP Y-port** — classic staged YPort with horizontal-distance differencing, 2.14x stage boost, 0.4 Y-offset at stage 3 and jump motion of 0.4.

## Fly — new modes

- **WatchdogNCP-New** — staged flight with ground-offset spoofing (`onGround = true`), a staircase speed machine (0.3 → x2.14 → `Speed` → decay) and a **Vanilla Kick Bypass** that sends an offset position packet after 125 ticks.
- **Matrix** — swallows S08 teleports and answers them itself (position confirm + jump), neutralizes forward input during the 16-18 tick window, then boosts with 1.97 / 9.3 strafes; automatically disables on the second teleport. Cross-version 1.9+ servers also receive a teleport confirmation through the Via pipeline.
- **Matrix Damage** — damage-triggered timer flight: after taking damage the timer ramps to `Timer Speed` for its duration, with `No Speed`, `Detect Damage`, `Auto Disable`, `Fly Ticks`, self-damage jump preparation (`Self Damage`, `New Self Damage`), `Motion Y` sub-modes (None / Simple / Multiply) and closing packets on disable (`Packet`).

## Technical notes

- New shared infrastructure: packet trackers (ticks since velocity / teleport, expected server-side motion, item-use ticks, fall distance, bad-packet heuristics), a reusable outgoing-packet queue, per-mode blink queues and movement/collision helpers — used by all new modes.
- Version bumped to **b12.10**; window title and mcmod.info follow automatically.
