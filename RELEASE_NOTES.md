# Lizz b12.13 — Changelog (6 fixes/upgrades + 16 new modes)

## Fly — WatchdogNCP-New OnlyDamage rework

- **OnlyDamage** now **fully disables the flight until you actually take damage** — no hovering, no onGround spoofing, no boost packets, no vanilla-kick bypass: you fall and move exactly like with the module off. The moment you get hit, the complete flight (stage machine + boost + bypass) starts from a fresh stage. Previously the normal "hover" flight still worked before the damage — only the boost part was gated, making it a half-trigger.

## ClickGUI — Classic Best drawer upgrades

- **Drawer background**: new `LB-SettingsDrawerColor` option (default dark gray, clearly distinct from the category panel and the untouched module rows) — the expanded settings drawer is now visually separated from everything else. Works for both LiquidBounce and Fatality styles.
- **Drawer overflow fix**: opening a module's settings in a short category (e.g. Combat) — or after scrolling a long category — no longer lets the drawer stick out below the panel: the panel height now follows its content **instantly** (category open/close keeps the smooth animation).

## Scaffold — new Rotations mode

- **Backward**: the same rounding system as ReverseYaw (90° / 45° by facing), but the rotation sent to the server faces the **opposite direction** (yaw + 180°) — the visible rotation is derived purely from your position changes, while placement still targets the block behind you.

## Scaffold — Tower MatrixNew new option

- **MatrixNew-Timer** (0.2 – 1.1, default 0.90): while towering in MatrixNew the game timer is set to this value, and it's restored to 1.0 as soon as you stop towering.

## WaterMark — new option

- **Size** (0.5 – 2, default 1): scales the whole watermark island on the HUD (anchored at the top-center, so it stays centered for every style — Normal / Opal / Lizz Morden / Scaffold / Chest / Notifications).

## Criticals — new mode GrimAC (replaces the old GrimCritical module)

- The former standalone **GrimCritical** module has been **moved into Criticals** as the new **GrimAC** mode (the module itself is gone; the arraylist tag shows `GrimAC`):
  - **Jump** — jumps in the strafe hook right after attacking, so every hit can be a falling (critical) hit; targets that were just hit (hurtTime 9) only get the client-side critical particles.
  - **AutoFreeze** — jumps with a KillAura target on ground and freezes time while falling (`FreezePercent` = share of falling ticks frozen; 100 = the whole fall).
  - **OnlyHorJump** — hover-style: locks your Y while falling with a target (client-side), optionally slows horizontal motion by **OnlyHorSpeed**; releases on target loss or a server lagback.

## Velocity — new modes

- **Watchdog-ProtectionNew** — a full anti-knockback protection stack combining five techniques, each with its own toggle (all on by default):
  - `PolarJump` — after receiving knockback, jumps at a randomized hurt tick (7–10) while sprinting / grounded / moving to reset the knockback;
  - `Intave` — holds your S12 / C03 / C02 / C08 / C07 packets (blink) while hurt within `BlinkWorkMaxDistance` of a KillAura target for up to `MaxBlinkTicks`, and reduces motion per attack in six phases (0.6 / 0.36 / 0.216 + reverse boosts); `IntaveJumpReset` (with `ForceSprintJump` / `ForceSprintJumpNeedForward`), `ExtraC0APerReduce` + `ExtraC0APacketCount`, `MoreReduce` + `MaxMoreReduceCount`, `OnlyWhenNeed` and `IntaveSafe` (players only) refine it; cancels the sprint-hit-slow at the right moments;
  - `Intave2` — attack-triggered motion reduce with a decaying factor (10% per attack, down to `MinFactor`);
  - `AirJumpReset` — jump-resets **in the air** at hurt tick 9 when moving forward with received knockback;
  - `Prediction` — blinks your outgoing packets during the `BlinkTicks` hurt window and lands `BlinkTicks` silent attacks inside it, plus a ground jump the moment your own knockback packet arrives;
  - `WorkOnGround` / `WorkInAir` choose where the whole stack is allowed to run; `DebugMessage` prints the internal decisions.
- **AttackReduce** — while your hurt time is inside `AttackHurtTime` (default 9), every attack you land multiplies your horizontal motion by `AttackXZFactor` (default 0.6).
- **MatrixReduce2** — on knockback (hurt ≥ 9): moving cleanly on the ground zeroes your horizontal motion; blocking / sneaking / eating / airborne / standing keeps 20%.
- **MatrixReduce3** — cancels your knockback packet and rebuilds motion from it: the vertical part is applied directly, standing still keeps 10% of the knockback speed, and `BoostAfterReduce` (+ `BoostFactor`, `BoostCooldown`) can accelerate you instead; zero-horizontal special knockbacks are cancelled too (`KeepMotionYVelocity` keeps their Y).

## Speed — new modes

- **MatrixLates** — ground-strafes your current speed with an optional `AutoJump`, caps the speed at 0.2 when airborne against a wall, and sinks 0.0032/tick while unhurt.
- **IntaveTimer** — auto-jumps on the ground while moving and switches the timer by fall distance: 1.0 when falling far, 0.7 mid-fall, 1.4 right after the jump.
- **IntaveNew** — ground motion ×1.00666666666 with timer 1.1, first air tick ×1.05 with timer 0.95, then sustained ×1.0015 with timer 1.0015; optional `AutoJump` (safe jump strength 0.22); the timers reset to 1.0 when you stop.
- **Intave14New** — auto-jumps on the ground, multiplies motion by 1.04 on the first air tick and 1.02 on ticks 2–4, optional `Timer` 1.002 (with `Debugger` chat output).

## NoWeb — new modes

- **Matrix** — web movement factor 0.12425 with a −0.0125 sink (−0.1625 sneaking) and a 3.0 timer burst every 40 ticks.
- **MatrixNew** — counts your moving-in-web ticks; past `MatrixNew-ThresholdTicks` every tick gets a jump + motion ×1.1 + air factor 0.26; `KeepY` freezes your Y while not jump/sneaking, `MatrixNew-ShowThresholdTicks` prints the counter.
- **Intave14** — pure forward in the web against a wall gets a 0.74 jump factor, otherwise 0.2 with onGround spoofed.

## NoSlow — new modes

All four work for the item types you enable with the shared `Sword` / `Food` / `Potion` / `Bow` toggles (each runs its packets on both motion phases, and fully cancels the slowdown for the matching item):

- **IntaveBlink** — while using an item, your C07 / C02 / C12 / C19 / C03 packets are held in a blink; every 5 ticks the blink is flushed and re-declared with a release + use packet; your own knockback or an explosion cancels the blink instantly.
- **Intave14New** — an 800 ms packet cycle: on cooldown end a RELEASE at an invalid position, otherwise start-sprint on PRE and a barrier click-window + empty slot switch + use + stop-sprint + transaction on POST.
- **GrimNew** — every tick sends three rapid hotbar slot switches (%8+1 / %7+2 / back) to refresh the server-side use state.
- **GrimTick** — in a 32-tick cycle, the first two ticks of every 4 send a RELEASE_USE_ITEM to fake the release/re-press rhythm.

## FastUse — new mode

- **Matrix** — slows the timer to 0.5 and re-sends a movement packet every update while consuming, for the fastest legit-looking eat/drink finish.
