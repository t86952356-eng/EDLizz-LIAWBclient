# Lizz b12.16 — Changelog (ClickGUI "Menu" style + big module cleanup)

## ClickGUI — new StyleSettings option: Menu

- **Menu** (StyleSettings → Normal / Best / **Menu**, Classic layout with the LiquidBounce or Fatality style) — a unified settings panel: right-click any module and its settings open in one **Panel Menu anchored above that category's main list**, overlaying it without moving the module rows. Select another module and the same panel switches to it (one panel for everything — a unified interface). The panel carries a title bar with the module name and a close button, opens with a smooth grow animation, scrolls with the mouse wheel when the settings are longer than the list, follows its category panel when you drag it, never extends past the panel's content bounds (same boundary rule as the Best drawer), and reuses the `LB-SettingsDrawerColor` background color. Right-clicking the same module again, clicking the ✕, clicking empty space, or collapsing the category closes it. While Menu is active, modules no longer expand their old inline right-side settings — all settings live in the unified panel. Normal and Best behave exactly as before.

## Removed modules (permanent cleanup — deleted from source)

48 rarely-used / outdated modules were removed permanently, including their files, registrations, mixins, hooks and language entries:

- **Combat (8):** ArmorBreak, AutoLeave, CSGOFight, GunHack, JKLArBreak, MoreDamage, Strict, TNTBlock
- **Player (16):** AntiAFK, AntiCactus, AutoFish, AutoJump, AutoL, AutoRespawn, AutoWaterFall, AvoidHazards, Gapple, Gapple2, Hub, KeepAlive, KeyPearl, PotionSaver, PotionSpoof, SaveArmor
- **Movement (19):** AntiBounce, AutoSave, AutoWalk, BloxdMove, BufferSpeed, Entityspeed, FastStairs, GhostMove, IceSpeed, NCPSpeed, NoFluid, NoSprint, PerfectHorseJump, ReverseStep, SafeWalk, SendMovePackets, SlimeJump, Sneak, WallClimb
- **World (5):** AutoBreak, CivBreak, Liquids, Stealer, Stuck

Along with the modules themselves, their dedicated mixin hooks were removed (slime-block bounce cancel, liquid collision, water/lava "no fluid" checks, the Sneak fake-sprint/sneak-sync patches, the NoSprint sprint cancel, and the Gapple cross-version eat keep-alive). Everything else in the client is untouched.

## Velocity — removed outdated modes

Six legacy anti-cheat modes were removed from Velocity: **AAC, AACPush, AACZero, AACv4, SmoothReverse, GrimC03** (together with their settings: AACPushXZReducer, AACPushYReducer, AACv4MotionReducer, SmoothReverseStrength). All other Velocity modes are unchanged. If a saved config selected one of the removed modes it safely falls back to Simple.

## Files

| File | Description |
|---|---|
| `lizz-b12.16.jar` | The ready-to-use mod (Forge 1.8.9) |
| `lizz-b12.16-source.zip` | Full source code of this build |
