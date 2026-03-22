# Shmup — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:shmup:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:shmup:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:shmup:test        # Run tests
```

## Code Structure

`Shmup.kt` (~246 lines) — single file, single `game("Shmup") { }` block:

1. Config (ROM_ONLY, 2 ROM banks)
2. Forward-declared `titleRef`
3. Variables: `score`, `lives`, `scrollY`, `shootCooldown`, `waveTimer`
4. Sound effects: `shootSfx`, `explodeSfx`, `hitSfx`, `scoreSfx`
5. Actor templates: `bullet` (4x8), `enemy` (16x16), `player` (16x16 ship)
6. Entity pools: `bulletPool` (max 8), `enemyPool` (max 4)
7. Scenes (reverse order): `gameover` → `gameplay` → `title`

## Actor Pool Patterns

### Spawn / Despawn
```kotlin
// Spawn a bullet at player position
spawn(bulletPool, player.x.toExpr(), player.y.toExpr())

// Destroy a specific pool entity inside forEachActive
forEachActive(bulletPool, "bi") {
    whenever(bullet.y isBelow 4) {
        destroy(bulletPool, VarRef("bi"))
    }
}

// Clear all on scene enter (prevents leftover entities from previous run)
destroyAll(bulletPool)
destroyAll(enemyPool)
```

### Cooldown Pattern
```kotlin
// Gate firing behind a countdown variable
whenever(buttons.a.pressed) {
    whenever(shootCooldown isEqualTo 0) {
        spawn(bulletPool, player.x.toExpr(), player.y.toExpr())
        shootCooldown set 8          // 8-frame cooldown
        playSound(shootSfx)
    }
}
whenever(shootCooldown isAbove 0) { shootCooldown -= 1 }
```

### Collision Between Pools
```kotlin
whenever(bullet.collides(enemy)) {
    score += 10
    playSound(explodeSfx)
}
whenever(enemy.collides(player)) {
    lives -= 1
    whenever(lives isEqualTo 0) { navigate(gameoverScene) }
}
```

## How to Modify

- **Increase pool size:** Change `max = 8` / `max = 4` in pool declarations
- **Faster fire rate:** Lower `shootCooldown set 8` (e.g., set to 4)
- **More enemy waves:** Decrease `waveTimer isAtLeast 60` threshold
- **Add power-ups:** Define a `powerupPool` actor pool, spawn periodically

## Dependencies

- `gbkt-core` — DSL, IR, actor pool system, collision detection
- `gbkt-backend-gbdk` — Game Boy C code generation
