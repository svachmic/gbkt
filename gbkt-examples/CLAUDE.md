# gbkt Examples — Developer Notes

## Module Structure

Each example is a standalone Gradle subproject under `gbkt-examples/`:

```
gbkt-examples/
├── settings.gradle.kts        # includes each example as a subproject
├── pong/
│   ├── build.gradle.kts       # plugin config: game name, sprites, GBDK settings
│   ├── src/main/kotlin/...    # single-file game DSL definition
│   └── src/test/kotlin/...    # IR validation + simulation tests
├── breakout/
├── platformer/
├── platformer-gbc/
├── rpg-lite/                  # uses gbkt-genre-rpg
├── dungeon/                   # uses gbkt-genre-rpg + exploration
├── explorer/                  # uses gbkt-genre-rpg + world
├── shmup/
└── racer/
```

## Build Pattern

All examples use the `io.github.gbkt` Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    id("io.github.gbkt") version "..."
}
gbkt {
    gameName.set("Pong")
    mainClass.set("io.github.gbkt.examples.pong.PongV2Kt")
    // optional: gbdkHome.set("/opt/gbdk-2020")
}
```

## Adding a New Example

1. Create `gbkt-examples/<name>/build.gradle.kts` modeled on an existing example
2. Add the subproject to `gbkt-examples/settings.gradle.kts`
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
    assertEquals(3, pongV2.scenes.size)
}

// Example simulation test
@Test fun `ball bounces off top wall`() {
    val ctx = SimulationContext(pongV2)
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
