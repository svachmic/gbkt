# gbkt Examples — Developer Notes

## Module Structure

Each example is a standalone Gradle subproject under `gbkt-examples/`, included via the root `settings.gradle.kts`:

```
gbkt-examples/
├── pong/
│   ├── build.gradle.kts       # plugin config: game name, sprites, GBDK settings
│   ├── src/main/kotlin/...    # single-file game DSL definition
│   └── src/test/kotlin/...    # IR validation + simulation tests
├── breakout/
├── simple-physics/
├── metasprites/
├── metasprites-stress/
├── banks/
└── platformer-template/
```

(7 active examples. `platformer-template/` was added in Phase 12 as the platformer-genre
reference port per D-03; the older `gbkt-examples/platformer/` was retired by the same
decision, and `racer/` was retired in Phase 14 — see `## Archived examples` below.)

## Build Pattern

All examples use the `io.github.gbkt` Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("io.github.gbkt")
}
gbkt {
    game("io.github.gbkt.examples.pong.PongKt::pong")
    assets("res")
    outputName.set("pong")
    // optional: gbdkHome.set("/opt/gbdk-2020")
}
```

## Adding a New Example

1. Create `gbkt-examples/<name>/build.gradle.kts` modeled on an existing example
2. Add the subproject to the root `settings.gradle.kts`
3. Create the game file at `src/main/kotlin/io/github/gbkt/examples/<name>/<Name>.kt`
4. Create a `GenerateC.kt` entry point (copy from an existing example)
5. Add `README.md` and `CLAUDE.md`

## Code Conventions

- One `.kt` file per game; top-level `val <name> = game("<Title>") { }` declaration
- Scenes defined in reverse navigation order to avoid SceneRef forward references
- Use `val titleRef = sceneRef("title")` for circular cycles (title defined last)
- All sound effects declared at the top of the game block as `val xSfx by soundEffect { }`

## Testing Pattern

Each example has two test files:

- `<Game>IRTest.kt` — validates the IR structure (actor count, variable count, scene count)
- `<Game>GameTest.kt` — simulation tests using `SimulationContext` to drive game logic

```kotlin
// Example IR test
@Test fun `game has correct scene count`() {
    assertEquals(3, pong.scenes.size)
}

// Example simulation test
@Test fun `ball bounces off top wall`() {
    val ctx = SimulationContext(pong)
    ctx.navigate("game")
    ctx.set("ballDy", -1)
    ctx.set("ball_y", 10)
    ctx.frame()
    assertEquals(1, ctx.get("ballDy"))
}
```

## Genre Packages

Examples that use genre packages (rpg-lite, dungeon, explorer) add the dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.gbkt:gbkt-genre-rpg:...")
    // or
    implementation("io.github.gbkt:gbkt-genre-platformer:...")
}
```

## Output Files

| File | Description |
|------|-------------|
| `build/gbkt/generated/main.c` | Generated C source |
| `build/gbkt/output/<name>.gb` | Compiled ROM |
| `build/gbkt/generated/main.c.gbkt.map` | Source map for debugging |


## Archived examples

The 6 examples below were archived in Phase 11.3 (2026-05-21) to narrow the
active matrix to the 7 framework-validated games; `racer/` and the `explorer`
GenerateC entry were additionally retired in Phase 14 (v0.1.0 release cleanup).

Archived sources are **not committed** — they were developer-local stashes under
`gbkt-examples/.archive/<name>/`, which may not exist on a fresh clone. To revive
an archived example, recover it from git history (it was deleted from the tree in
its archival phase) and check the revival condition below.

### explorer

- Original path: `gbkt-examples/explorer/`
- Archive path: `gbkt-examples/.archive/explorer/`
- Reason: SEED-018 RPG character codegen extern/decl mismatch (full 7-stat set, `:buildRom` fails)
- Revival condition: after SEED-018 is fixed and routed to a phase
- Date archived: 2026-05-21

### rpg-lite

- Original path: `gbkt-examples/rpg-lite/`
- Archive path: `gbkt-examples/.archive/rpg-lite/`
- Reason: SEED-018 RPG character codegen extern/decl mismatch
- Revival condition: after SEED-018 is fixed
- Date archived: 2026-05-21

### dungeon

- Original path: `gbkt-examples/dungeon/`
- Archive path: `gbkt-examples/.archive/dungeon/`
- Reason: SEED-018 RPG character codegen extern/decl mismatch
- Revival condition: after SEED-018 is fixed
- Date archived: 2026-05-21

### platformer

- Original path: `gbkt-examples/platformer/`
- Archive path: `gbkt-examples/.archive/platformer/`
- Reason: Phase 07.5 platformer genre codegen gap (deferred); also retired in favor of
  `platformer-template/` (Phase 12 reference port — D-03).
- Revival condition: kept archived; `platformer-template/` is the supported successor.
  Do NOT delete `.archive/platformer/` — kept for revival per the Phase 11.3 ledger policy.
- Date archived: 2026-05-21

### platformer-gbc

- Original path: `gbkt-examples/platformer-gbc/`
- Archive path: `gbkt-examples/.archive/platformer-gbc/`
- Reason: Phase 07.5 platformer genre codegen gap (deferred); shares platformer codegen path
- Revival condition: after Phase 07.5 ships
- Date archived: 2026-05-21

### shmup

- Original path: `gbkt-examples/shmup/`
- Archive path: `gbkt-examples/.archive/shmup/`
- Reason: polish backlog — F-A pool-pool collision + F-B stale OAM on scene re-entry (`UAT-shmup.md`)
- Revival condition: owner: TBD; after F-A and F-B are routed to a phase
- Date archived: 2026-05-21
