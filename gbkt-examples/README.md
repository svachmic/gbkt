# gbkt Examples

Nine example games demonstrating the gbkt DSL at various complexity levels.
All examples compile to `.gb` / `.gbc` ROMs via GBDK-2020.

## Examples Overview

| Game | Difficulty | Target | Description |
|------|-----------|--------|-------------|
| [pong](pong/) | Beginner | DMG | Classic two-player pong with AI opponent |
| [breakout](breakout/) | Beginner | DMG | Brick-breaking with lives, score, and HUD icons |
| [platformer](platformer/) | Intermediate | DMG | Side-scrolling platformer with physics and jump mechanics |
| [platformer-gbc](platformer-gbc/) | Intermediate | GBC | Same as platformer with GBC_COMPATIBLE color mode |
| [rpg-lite](rpg-lite/) | Intermediate | DMG | Mini-RPG: dungeon exploration, combat, town healing |
| [dungeon](dungeon/) | Advanced | DMG | Tile-based dungeon crawler with torch gauge and encounters |
| [explorer](explorer/) | Advanced | DMG/GBC | Full RPG: world map, multiple floors, party, equipment |
| [shmup](shmup/) | Intermediate | DMG | Shoot-em-up with entity pools and scrolling background |
| [racer](racer/) | Intermediate | GBC | Top-down racing game with GBC color support |

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

Replace `<name>` with the example folder name (e.g., `pong`, `breakout`, `platformer-gbc`).

## Prerequisites

- GBDK-2020 installed and `GBDK_HOME` set (or installed to a common path)
- Java 21+ for the Kotlin build
- mGBA or BGB emulator for `runEmulator`

## Learning Path

1. **pong** — start here; covers actors, variables, sound, and scene navigation
2. **breakout** — adds arrays, tile-based collision, lives system, and HUD icons
3. **platformer** — introduces the `gbkt-genre-platformer` package (physics, camera, platforms)
4. **platformer-gbc** — same game, adds `GbcTarget.GBC_COMPATIBLE` config
5. **rpg-lite** — introduces `gbkt-genre-rpg` (characters, monsters, `simpleBattle`)
6. **dungeon / explorer / shmup / racer** — advanced systems

See each example's `README.md` for game-specific controls and feature details.
