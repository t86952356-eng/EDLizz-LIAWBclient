# Lizz b12.9 — Changelog (Module Renaming & Custom Modes Update)

## Module renames

- **Aimbot → AimAssist** — combat module fully renamed (module name, object, file, registration, mixin/Sprint references and language descriptions).
- **SuperKnockback → MoreKB** — combat module fully renamed, including the input-blocking mixin (`MixinMovementInputFromOptions`) and Sprint integration.
- **Scaffold: GodBridge → FastBridge** — every scaffold option containing "God" was renamed: the `ScaffoldMode` choice `GodBridge` → `FastBridge`, the `Rotations` choice `GodBridge` → `FastBridge`, the `GodBridgePitch` setting → `FastBridgePitch` (internal identifiers updated as well). Applied to both Scaffold and Scaffold2.
- **Fly mode renames**:
  - `Hypixel` → **WatchdogNCP**
  - `BoostHypixel` → **WatchdogNCP-Stable**
  - `FreeHypixel` → **WatchdogNCP-Free**
  - `BlocksMC` → **UpdatedNCP**
  - `BlocksMC2` → **UpdatedNCP2**
- **NoFall mode renames**:
  - `Hypixel` → **WatchdogNCP**
  - `HypixelTimer` → **WatchdogNCP-Packet**
- **Speed mode renames**:
  - `HypixelHop` → **WatchdogNCP-Hop**
  - `HypixelLowHop` → **WatchdogNCP-LowHop**
  - `BlocksMCHop` → **Verus**
  - `PredictionTimer2` → **Watchdog-Protection**
  - `PredictionTimer` → **Watchdog-Protection2**
- **Velocity mode rename**: `HypixelAir` → **WatchdogNCP**

## New Mode system for NoSlow and Disabler

- NoSlow and Disabler now expose a top-level **Mode** option containing a **Custom** mode:
  - Custom represents all of the module's previous settings; the settings are only visible while Custom is selected.
  - The selected mode is displayed as a tag next to the module name in the ArrayList.
- Disabler: the inner NoRotationDisabler mode setting was renamed from `Mode` to `ModifyMode` to make room for the new top-level `Mode` option.

## Fly — WatchdogNCP (former Hypixel)

- The `Hypixel-BoostDelay` setting maximum was raised from 2000 ms to **10000 ms**.

## Removed modules

- **LiquidWalk**, **BedGodMode** and **GodMode** were removed completely: source files, module registration, the `MixinEntityLivingBase` LiquidWalk hook, and their language entries.

## Technical notes

- Renamed mode packages for source consistency: `flymodes/hypixel` → `flymodes/watchdogncp`, `flymodes/blocksmc` → `flymodes/updatedncp`, `speedmodes/hypixel` → `speedmodes/watchdogncp`.
- Old saved configs referencing previous module/mode names gracefully fall back to defaults (unknown choice names are ignored by the config loader) — no crashes on config load.
- Registered module count is reduced by 3.
