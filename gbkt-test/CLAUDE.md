# gbkt-test — JUnit5 Test Infrastructure

Reusable JUnit5 extension, fluent assertions, composable test recipes, and reporter utilities for gbkt emulator integration tests. Eliminates 50+ lines of boilerplate per test class.

## Dependencies

- `gbkt-emulator` (api) — `StepAgent`, `AgentSessionConfig`, `GameMetadata`, `Observation`
- `org.junit.jupiter:junit-jupiter-api` (compileOnly) — consumers bring their own JUnit5 version via `testImplementation`

## Key Files

| File | Role |
|------|------|
| `GbktTestExtension.kt` | JUnit5 `BeforeEachCallback`/`AfterEachCallback`/`TestExecutionExceptionHandler`; manages `StepAgent` lifecycle, auto-skip, auto-screenshot on failure |
| `GbktGameAssertions.kt` | Four fluent assertion functions: `assertScene`, `assertVariable`, `assertActorVisible`, `assertTextOnScreen` — available as both top-level and `GbktTestExtension` extensions |
| `GbktTestRecipes.kt` | Five composable recipes: `verifyTitleScreen`, `verifyFirstSceneTransition`, `verifyInputResponds`, `verifySpriteVisibility`, `bootToScene` |
| `GameDiscovery.kt` | Convention-based ROM/sym/metadata path resolution (`configForGame`, `scanForBuiltRoms`); returns `null` (not exception) when ROM not found |
| `GbktGameReporter.kt` | Per-game test summary: `recordScene`, `recordPass`, `recordFail`, `recordScreenshot`, `printSummary` |

## Build Commands

```bash
# Run gbkt-test unit tests
./gradlew :gbkt-test:test

# Build the module
./gradlew :gbkt-test:build
```

## Consuming This Module

Add to your game module's `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation(project(":gbkt-test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}
```

## Common Tasks

### Adding New Assertions

1. Add a top-level function to `GbktGameAssertions.kt`:

```kotlin
fun assertSpriteAt(obs: Observation, actorName: String, x: Int, y: Int, message: String? = null) {
    val prefix = if (message != null) "$message: " else ""
    val actor = obs.actors.find { it.name == actorName }
        ?: throw AssertionError("${prefix}Frame ${obs.frame}: actor '$actorName' not found")
    if (actor.x != x || actor.y != y) {
        throw AssertionError(
            "${prefix}Frame ${obs.frame}: '$actorName' at (${actor.x},${actor.y}) expected ($x,$y)",
        )
    }
}
```

2. Add the corresponding extension function at the bottom of `GbktGameAssertions.kt`:

```kotlin
fun GbktTestExtension.assertSpriteAt(obs: Observation, actorName: String, x: Int, y: Int, message: String? = null) =
    io.github.gbkt.test.assertSpriteAt(obs, actorName, x, y, message)
```

Do NOT use `kotlin.test` (only available in test source sets) or `org.junit.jupiter.api.Assertions` (compileOnly). Use `throw AssertionError(...)` directly in main source.

### Adding New Recipes

Add extension functions on `GbktTestExtension` in `GbktTestRecipes.kt`. Follow the pattern of using `metadata` when available and heuristics as fallback:

```kotlin
fun GbktTestExtension.verifyGameover(expectedText: String = "GAME OVER") {
    val obs = bootToScene(metadata?.terminalScenes?.firstOrNull() ?: "gameover")
    assertTextOnScreen(obs, expectedText)
}
```

### Extending the Reporter

`GbktGameReporter` is a simple counter class. Add new counters and `record*` / include them in `printSummary()`:

```kotlin
private var checkpointsReached = 0
fun recordCheckpoint() { checkpointsReached++ }
// In printSummary():
println("  Checkpoints: $checkpointsReached reached")
```

### Supporting a New ROM Layout

`GameDiscovery.configForGame()` resolves `build/gbkt/output/GAMENAME.gb` under the Gradle subproject root. If a project uses a non-standard layout, pass `customRomFile` directly to `GbktTestExtension`:

```kotlin
val game = GbktTestExtension(
    gameName = "mygame",
    customRomFile = File("path/to/custom/mygame.gb"),
)
```

## Architecture Notes

- **JUnit5 as `compileOnly`**: Consumers declare `testImplementation(junit-jupiter-api)` themselves, avoiding version conflicts between `gbkt-test` and the consumer.
- **Dual assertion API**: Assertions are top-level functions (callable without a receiver) and extension functions on `GbktTestExtension` (callable with `game.` prefix). Both compile to the same implementation.
- **No `kotlin.test` in main source**: `kotlin.test` is only available in test source sets. All assertions use `throw AssertionError(...)` directly.
- **No `org.json` dependency**: `org.json` is an implementation detail of `gbkt-emulator` not exposed on the api classpath. Failure JSON dumps are built with `StringBuilder` to avoid a transitive dependency.

## See Also

- `context/TESTING.md` — Complete testing guide (tiers, GbktTestExtension, recipes, GameConstants, playbook, MCP)
- `gbkt-emulator/CLAUDE.md` — StepAgent, Observation, GameMetadata, UatRunner API
- `gbkt-mcp-server/CLAUDE.md` — MCP tool reference (16 tools)
