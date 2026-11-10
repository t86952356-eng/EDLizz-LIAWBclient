# Lizz b12.12 — Changelog (Feature Wave III: 3 upgrades + 21 new modes)

## Fly — WatchdogNCP-New upgrades

- **OnlyDamage** (new option, default off): the fly only takes effect **after you take damage** — until then you hover in place waiting for the hit.
- **AutoDamage** (new option, default off): enabling the module now **automatically triggers self-damage** (the classic NCP glitch packet sequence) on toggle, so `OnlyDamage` flights start instantly.

## AntiVoid — arraylist tag

- AntiVoid now shows the **current mode as a tag** in the arraylist (same style as NoSlow / Disabler / Velocity), e.g. `AntiVoid WatchdogNCP`.

## ClickGUI — Classic layout StyleSettings (Normal / Best)

- New `StyleSettings` option (Classic layout, LiquidBounce / Fatality styles):
  - **Normal** (default): the current look, completely unchanged — module settings still open as a floating panel to the right of the module row.
  - **Best**: module settings now expand as a **drawer directly below the module** — the modules below automatically shift down (nothing is covered or overlapped) and the settings width stays inside the category panel boundaries (e.g. Combat). Clicking inside the drawer only affects the setting controls, not the module.

## KillAura — new AutoBlock modes

- **Matrix** — packet-blocks with the blocking packet (optional `InteractPacket` sends two INTERACT_AT packets first); never sends a stop-block packet. While blocking, every attack is intercepted: release → resend the attack → instantly re-block.
- **Verus** — starts blocking with the same blocking packet; when the target is lost it "stops blocking" by swapping the hotbar slot and swapping back.
- **HoldKey** — physically holds the use-item key down the whole time the target is in range (real client-side blocking), releases it when the target is gone.
- All three modes keep the **NoSlow link**: while auto-blocking, the sword NoSlow bypasses run exactly like with the Packet mode.

## Disabler — new Mode system + 3 modes

The Disabler now has its own `Mode` selection (like NoSlow) — `Custom` keeps every existing option and behavior.

- **WatchdogNCP** (Hypixel movement disabler): waits `WatchDogMovement-ActivationDelay(seconds)` after world load, jumps up, freezes your motion mid-air and offsets every other motion packet (+0.075) for `WatchDogMovement-Ticks` ticks with a live progress bar on screen; resets and retries with a 2s delay if you get rubber-banded, then prints "disabler enabled" once it sticks. Only activates on Hypixel games (lobby / replay / spectator aware) and locks your movement input while running.
- **MatrixFlag** (tx-timing + anti-setback): remembers your motion on every server setback and restores it when your next position packet leaves; on top of that a full **tx-burst engine** — `AutoBurst` windows (`BurstWindow` ms, at least 200 ms apart), `TxDelay` holds C0F transactions for `TxHoldMin`–`TxHoldMax` ms (max 3 queued, oldest is flushed), optional `KaDelay` for keep-alives, a `TimerBurst` cycle (`TimerSpeed` × hold/cool), `GroundSpoof` (reports onGround when a block is within `GroundTol` below you), and `AntiSetback` setback handling with `SetbackMode` (Recovery / Y-Partial / Rot-Keep / Full-Cancel) + `SetbackCool`. `Version` (Auto / Matrix6 / Matrix7) auto-detects 7.x after two flags. Reports "tx stable" / "auto: 7.x detected" in chat.
- **GrimAC** (aim + post): swallows position packets whose rotation exactly repeats the last reported one (resending position-only), and smooths every other rotating packet into the client rotation state; optional `GrimPost` delays a whitelist of server packets (velocity, transactions, teleports, block updates, … — critical login / chunk / chat packets always pass) while you're alive, and flushes them the moment the delay deactivates (with `GrimPost-NoScaffold` to pause while scaffolding).

## Phase — new mode

- **Intave** — hold attack while aiming at your feet (or pitch ≥ 70°) to spam STOP_DESTROY_BLOCK at the block below, sink 0.0052/tick and micro-shift 0.005/tick while sneaking to phase through.

## Fly — new modes

- **TimerGlide** — damage-glide flight with a full settings panel: `TimerGlide-TimerSpeed` (up to 100x) kicks in the moment you're hurt (boost length = 20 × speed ticks), `TimerGlide-TimerBeforeBoost` slows time while waiting, `TimerGlide-Speed` / `NoSpeed` control the glide, `SmartHurt` auto-disables after `TimerGlide-FlyTicks` (or after the boost when `SmartTicks`), `JumpDamage` self-damages with `JumpCounts` × `JumpTimer` jumps, `AutoAirDamage` prepares the fall with `SpoofMode` (Edit / Packet / 1.8), `DamageDistance` and `NoMove`, `CustomMotionY` (None / Stable / Multiply / Optimal) shapes the vertical motion, and `TimerGlide-AutoToggle` turns the module off when done.
- **Intave** — 10x timer + noClip + constant onGround + a 0.0085 fly-speed capability spoof; after the first setback it buffers all transactions / velocities, flushing them on disable.
- **Matrix** (the former MatrixTest slot, rebuilt): eats S08 teleports into a buffer, cancels **all** outgoing position packets and instead re-sends your position `Matrix-Packets` times per tick (C06 with your current rotation or plain C04 — `Matrix-PacketMode`) at `Matrix-Timer` speed with `Matrix-Speed` horizontal motion and noClip.
- The previous Matrix fly was renamed to **OldMatrix** (unchanged behavior).

## Velocity — new modes

- **MatrixBoost** (the Matrix velocity family, new generation): cancels your velocity packets and applies the vertical knockback directly when falling / on ground; when standing still you get an instant 11% of the knockback speed, and `Matrix-Boost` (with `Matrix-BoostSpeed`) accelerates you mid-air instead. `Matrix-HurtStrafe` re-applies your horizontal speed during `StrafeHurtTime-Min`–`Max` hurt ticks, `Matrix-OnlyCombat` limits everything to fights (1.6 s after your last attack).
- **IntaveNew** — velocity-state flight for Intave: tracks positive knockback, `HurtTime-Min`–`Max` windows, `OnlyOnce` and `Intave-OnlySprint` gates; when you attack while knocked back it drops sprint and cuts your motion to 60%, and `IntaveMoveFixMode` (None / GameSettings / Strafe) steers your movement keys toward the knockback direction or the client rotation.

## Speed — new modes

- **MatrixNew** (the Matrix speed family, new generation): ground-jump + strafe cycle, optional `Strafe` top-up when your speed drops below 0.2 mid-air, −0.0033 vertical drag while unhurt, and your sprint-jump boost is redirected along your movement direction.
- **MatrixHighSpeed2** — ladder-flight speed: jumps into a boost, and whenever you fall past `DownMotion` with a block underneath it fakes onGround, re-jumps and strafes at `H` speed — repeat until you're moving. `Inf` keeps the cycle alive outside the boost window.

## NoSlow — new modes

- **MatrixNew** (the Matrix NoSlow family, new generation): while blocking a sword (`Sword`) or eating / drinking (`Food` / `Potion`), grounded ticks get a ±0.0002–0.0006 Y offset with onGround spoofed off, and airborne ticks get progressive motion damping (0.85/0.9 then 0.95). The sword branch runs through the module's own using-item logic, so it works **with KillAura auto-block**.
- **MatrixSemi** (food only): an eating-tick ballet — re-jumps on the ground, +45° silent yaw rotation while airborne without strafe keys, micro-speed pulses (2e-4 / 1e-4, cross-version aware), cancels the right-click packet on odd airborne ticks and fully cancels the slowdown on the landing tick / even airborne ticks / odd grounded ticks.
- **GrimSemi** (food only): alternates every tick — odd ticks of item use are full speed, even ticks use the configurable `GrimSemi-Forward` / `GrimSemi-Strafe` multipliers (default 0.2 = vanilla).

## Scaffold — Tower new mode

- **MatrixNew** (the Matrix tower, new generation): waits until `MatrixNew-WaitBlocks` blocks have been placed, then runs a leave-ground → touch-ground state machine that fake-jumps with motionY 0.42 and, whenever you fall below 0.19, spoofs onGround and pushes 0.42 again — a smooth hovering tower.

## Module renames (name slots reused)

- Fly: the old `Matrix` mode is now **`OldMatrix`** (same behavior); the new `Matrix` mode is the rebuilt MatrixTest-style fly described above.

---

**Full list of additions this release**: 3 feature upgrades (Fly OnlyDamage/AutoDamage, AntiVoid arraylist tag, ClickGUI StyleSettings drawer), 21 new modes across 8 modules (KillAura 3, Disabler 3, Phase 1, Fly 3, Velocity 2, Speed 2, NoSlow 3, Scaffold Tower 1) and 1 rename (Fly Matrix → OldMatrix).
