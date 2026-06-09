# gbkt Examples

Eight example games demonstrating the gbkt DSL at various complexity levels.
All examples compile to `.gb` / `.gbc` ROMs via GBDK-2020.

## Examples Overview

| Game | Difficulty | Target | Description |
|------|-----------|--------|-------------|
| [pong](pong/) | Beginner | DMG | Classic two-player pong with AI opponent |
| [breakout](breakout/) | Beginner | DMG | Brick-breaking with lives, score, and HUD icons |
| [simple-physics](simple-physics/) | Beginner | DMG | Shortest path from `game { }` to ROM — sub-pixel physics, single sprite |
| [metasprites](metasprites/) | Intermediate | DMG/GBC | Variable-length OAM metasprites, GBC sub-palettes, hardware flip |
| [metasprites-stress](metasprites-stress/) | Internal | DMG/GBC | Throwaway codegen-verification ROM (not a user-facing example) |
| [banks](banks/) | Advanced | DMG | Multi-bank ROM with cross-bank scene navigation and SRAM persistence |
| [racer](racer/) | Intermediate | GBC | Top-down racing game with GBC color support |
| [platformer-template](platformer-template/) | Advanced | DMG/GBC (MBC1) | GBDK `platformer_template` reference port — tilemap-collision, horizontal scroll, variable-height jump, banked title + NextLevel cards, 3-level substrate |

## Build Commands

Each example follows the same Gradle pattern:

```bash
# Generate C code only
./gradlew :gbkt-examples:<name>:generateC

# Build ROM (requires GBDK-2020 installed)
./gradlew :gbkt-examples:<name>:buildRom

# Run in emulator (auto-detects mGBA)
./gradlew :gbkt-examples:<name>:runEmulator

# Run tests
./gradlew :gbkt-examples:<name>:test
```

Replace `<name>` with the example folder name (e.g., `pong`, `breakout`, `racer`).

## Prerequisites

- GBDK-2020 installed and `GBDK_HOME` set (or installed to a common path)
- Java 21+ for the Kotlin build
- mGBA or BGB emulator for `runEmulator`

## Learning Path

1. **pong** — start here; covers actors, variables, sound, and scene navigation
2. **breakout** — adds arrays, tile-based collision, lives system, and HUD icons
3. **racer** — top-down racing via `gbkt-genre-sport` (track tilemap, AI rival, lap counting)

See each example's `README.md` for game-specific controls and feature details.
