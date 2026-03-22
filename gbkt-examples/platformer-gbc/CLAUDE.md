# Platformer GBC — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:platformer-gbc:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:platformer-gbc:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:platformer-gbc:test        # Run tests
```

## Code Structure

`PlatformerGbc.kt` (~170 lines) — single file, single `game("Platformer GBC") { }` block.

This file is intentionally nearly identical to `Platformer.kt` in `gbkt-examples/platformer`.
The only differences are:

1. Package: `io.github.gbkt.examples.platformergbc`
2. Top-level val: `val platformerGbc = game("Platformer GBC")`
3. Config block: adds `target(GbcTarget.GBC_COMPATIBLE)`
4. Title scene: prints `"PLATFORMER GBC"` instead of `"PLATFORMER"`

All game logic, physics settings, camera, platforms, scenes, and actors are identical.

## The GBC_COMPATIBLE Config

```kotlin
config {
    cartridge = "ROM_ONLY"
    romBanks = 2
    // GBC_COMPATIBLE: GBC color mode on GBC; backward-compat DMG mode on original hardware
    // Switch to GBC_ONLY to drop DMG support and unlock full 8-palette color system
    target(GbcTarget.GBC_COMPATIBLE)
}
```

## When to Use GBC_COMPATIBLE vs GBC_ONLY

- **GBC_COMPATIBLE** — game must run on both DMG and GBC hardware. Use when: you want color
  on GBC but can't drop original GB support.
- **GBC_ONLY** — GBC hardware only. Unlocks full 8×4 palette color system. Use when: making
  a GBC-exclusive game and DMG compatibility is not required.

## DMG/GBC Variant Pattern

This example demonstrates the recommended pattern for making DMG and GBC variants of the same game:

1. Keep the DMG version in `gbkt-examples/platformer/` — pure DMG, no target config
2. Create a GBC variant in `gbkt-examples/platformer-gbc/` — copy the DSL, change the config
3. Both share the same genre package imports and game logic

This keeps the game logic in sync: changes to physics, levels, or scenes should be applied
to both files. For complex games, consider extracting shared logic into a common function or
a shared module.

## How to Modify

The same guidance applies as the DMG Platformer. See [../platformer/CLAUDE.md](../platformer/CLAUDE.md).

Additional GBC-specific changes:
- **Switch to GBC_ONLY:** Change `GbcTarget.GBC_COMPATIBLE` to `GbcTarget.GBC_ONLY`
- **Add color palettes:** Use the `palette()` DSL (not shown in this minimal example) to assign
  colors to sprites and backgrounds

## Dependencies

- `gbkt-core` — DSL, IR, scene/actor/sound system
- `gbkt-backend-gbdk` — Game Boy / GBC C code generation
- `gbkt-genre-platformer` — `platformerPhysics()`, `platformerCamera()`, `platform()`, `goalZone()`
