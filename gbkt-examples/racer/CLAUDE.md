# Racer — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:racer:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:racer:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:racer:test        # Run tests
```

## Code Structure

`Racer.kt` (~227 lines) — single file, single `game("Racer") { }` block:

1. Config (ROM_ONLY, 2 ROM banks, GBC_COMPATIBLE target)
2. Forward-declared `titleRef`
3. Variables: `lap`, `raceTime`, `position`
4. Sound effects: `engineSfx`, `turnSfx`, `lapSfx`, `winSfx`
5. Racing system: `racing("track1")` with track, vehicles, AI config
6. Zone: `zone("circuit")` — 32x32 tile map
7. Camera: smooth follow with 256x256 bounds
8. Actor: `car` with smooth movement controller
9. Scenes (reverse order): `results` → `race` → `title`

## Racing DSL Setup

### racing() Block
```kotlin
racing("track1") {
    mode(RacingMode.AI_OPPONENT)
    laps(3)
    track("circuit") {
        waypoint(x = 5,  y = 5,  checkpoint = true)
        waypoint(x = 15, y = 5,  checkpoint = false)
        waypoint(x = 15, y = 15, checkpoint = true)
        waypoint(x = 5,  y = 15, checkpoint = false)
    }
    vehicle("car_player") {
        name("Racer")
        stats { speed(200); acceleration(160); handling(180) }
    }
    vehicle("car_ai") {
        name("Rival")
        stats { speed(180); acceleration(150); handling(200) }
    }
    ai {
        speedPercent(85)
        difficulty(3)
        rubberBanding(enabled = true, strength = 40)
    }
}
```

### GBC Target
```kotlin
config {
    cartridge = "ROM_ONLY"
    romBanks = 2
    target(GbcTarget.GBC_COMPATIBLE)  // Enables GBC color palettes, runs on DMG too
}
```

### Smooth Movement Actor
```kotlin
val car by actor {
    movement {
        style(MovementStyle.SMOOTH)
        speed(3); acceleration(1); friction(1)
    }
}
```

## How to Modify

- **Add more laps:** Change `laps(3)` and update the `lap isAtLeast 3` finish condition
- **Add a third vehicle:** Add another `vehicle("car_ai2")` block in the racing DSL
- **Change track shape:** Add/remove `waypoint()` calls in `track("circuit")`
- **Disable rubber banding:** Set `rubberBanding(enabled = false)`
- **GBC-only mode:** Change `GBC_COMPATIBLE` to `GbcTarget.GBC_ONLY`

## Dependencies

- `gbkt-core` — DSL, IR, actor/zone/camera system
- `gbkt-backend-gbdk` — Game Boy C code generation
- `gbkt-genre-sport` — `racing()`, `RacingMode`, vehicle stats, AI config
