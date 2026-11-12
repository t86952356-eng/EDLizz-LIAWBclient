# Lizz b12.14 — Changelog (2 fixes + 11 new modes)

## Scaffold — Backward rotation fix

- The **Backward** rotations mode now really faces **backwards**: previously, when your current rotation already hit the target block (the normal case while bridging), the rotation was kept as-is — i.e. still facing forward. Now the mode always computes the sent rotation from your placement direction and flips it 180°, so you consistently look **away from the bridge direction** (rotation determined purely by your position changes).

## ClickGUI — Classic Best drawer overflow fix

- The module settings drawer (StyleSettings = Best) can no longer stick out below the category panel: the panel now grows to its full content height **in the very same frame** the drawer opens (even for the very first open of a module), and as an extra safety the drawer is **clipped to the panel content area** — anything that would ever exceed the panel bounds is simply not drawn outside them.

## Fly — new modes

- **Watchdog-ProtectionBoat** — boat fly: ride a boat, the module swings and attacks your own boat with a use-entity packet (the server kills it) while your own dismount sneak packets are cancelled and all incoming transactions are buffered; once the boat is gone you fly — vertical motion is ±0.42 controlled by the jump/sneak keys (stopping exactly at whole-block heights), horizontal speed is driven by the **Speed** setting (0.1–9.5) through the strafe friction, with motion zeroed for 1s freeze windows after each phase switch; the module auto-disables on world change and flushes the buffered transactions on disable.
- **MMC** — ground-clip flight: on the first grounded tick with a full solid block 2.5 below you, it drops the timer to 0.1, sends the classic three-position packet chain (current pos grounded / rounded ground 2.5 below / current pos airborne), jumps and strafes at ~7.0 — and cancels the first server teleport that tries to pull you back.
- **AirJump** — infinite mid-air jumping: whenever you touch the ground you jump, and air blocks below your anchor height get a huge invisible collision platform (30×2×30), so every hop lands on a phantom floor and you keep climbing; the platform anchor follows you while holding jump/sneak, and sneaking disables the platforms to let you fall.

## Velocity — new modes

- **GrimReduce** — packet-hold knockback reducer: your own velocity packet plus transactions / entity moves / keep-alives are held after you get knocked back, and flushed once the release conditions match (`Delay till Ground`, `Delay Plus` with a target-distance / recent-attack gate, or a 25-tick timeout). While holding, it manages the fight: `Rotate` looks at the closest target right after the hit, locks a 90° straight-down view at the target (or the closest enemy in `Range`), performs the extra hit (`Extra Hit`, version-ordered attack + swing), and jump-resets on the knockback; `Reduce Ticks` / `Teleport Disable Ticks` / `On Swing Disable on Aura` / `Stop Sprint` refine the windows.
- **WatchdogPrediction** — the Watchdog knockback holder: holds the velocity packet and (almost) all server packets while knocked back mid-air, releasing everything the moment your view aligns with the knockback direction (±15°), when you touch the ground, or after the fall-tick timeout; `Velocity to let through` and `Prevent Ghosting` shape the release. (Replaces the old port of the same mode.)

## InventoryMove — new + replaced modes

- **Watchdog-Protection** (new) — the Watchdog inventory-move: real key presses are fed back into the movement keys, chests/inventories get the speed management (speed-suppression on Speed, the 0.0365 / potion-tiered / 0.09 strafe ladder, diagonal prevention, or the `Prediction Mode` input-freeze variant with sprint/jump key control), server chests are kept open with silent close-window packets, and `Measure Chest Open` measures the chest-open latency (C08 block clicks vs C02 NPC interactions, ignoring dead/stationary entities) to freeze your inputs for the confirmed window and blend your rotation toward the movement direction.
- **Grim** (replaced) — the updated Grim inventory-move: movement keys are refilled from the real keyboard in any GUI (except chat / the click GUI) and sprinting is force-disabled inside inventories and chests, with the `Manager Extra Sprint Ticks` option carried over.

## NoSlow — new + renamed modes

- **Grim 3.0** (new) — the Grim 3 no-slow: a +45° silent rotation while using items (two gated paths), 0.64 strafe while caught in cobwebs, tiny moveFlying boosts while eating (halved when Speed is enabled), right-clicks cancelled on odd airborne ticks, forced sprint while moving forward with an item, and the food/potion/sword/bow slowdowns cancelled on the Grim tick pattern (first item tick / even airborne ticks / odd grounded ticks); **Heypixel** holds your transactions while consuming.
- **WatchdogPrediction** (renamed) — the ping-spoof eater: after `Max Ping Spoof` ticks of using a food/bow/potion item it blinks everything, releases the use key at `When to finish eating`, cancels the slowdowns for food/potion/bow after the spoof window, and re-declares sword use with a release + use packet chain (cross-version aware); `Non-Blink Speed Bypass` keeps you sprinting before the blink kicks in. (Same mode, now under its source name.)

## Speed — new mode

- **MineMenClub** — knockback-driven speed: 8+ ticks after taking knockback it strafes at full speed on the ground or when your movement direction is within 90° of the knockback direction, and at 70% (`allow more strafing`) otherwise, auto-jumping every grounded tick.
