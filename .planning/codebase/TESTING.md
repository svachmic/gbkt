# Testing Patterns

**Analysis Date:** 2026-05-27

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) — every module wires `tasks.test { useJUnitPlatform() }` (see `gbkt-examples/pong/build.gradle.kts:31-33`)
- `org.junit.jupiter:junit-jupiter-api` declared `compileOnly` in `gbkt-test` so consumers bring their own JUnit5 version via `testImplementation` — no version conflicts between `gbkt-test` and the consumer module (`gbkt-test/CLAUDE.md` "Architecture Notes")

**Assertion Library:**
- `kotlin.test` (`kotlin("test")`) — provides `assertEquals`, `assertTrue`, `assertNotNull`, `assertFalse`
- `gbkt-test` fluent assertions — `assertScene`, `assertVariable`, `assertActorVisible`, `assertTextOnScreen` (`gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktGameAssertions.kt`)
- `org.junit.jupiter.api.Assumptions` for skip-on-missing-prereq (`Assumptions.assumeTrue(...)`)
- `org.junit.jupiter.api.extension.RegisterExtension` for the `GbktTestExtension` JUnit5 extension

**Run Commands:**
```bash
./gradlew test                              # All modules
./gradlew :gbkt-ir:test                     # IR module only (fastest)
./gradlew :gbkt-lang:test                   # DSL builders
./gradlew :gbkt-engine:test                 # Engine types
./gradlew :gbkt-core:test                   # Asset pipeline, parsers, test infra
./gradlew :gbkt-backend-api:test            # Backend contract
./gradlew :gbkt-backend-gbdk:test           # GBDK codegen
./gradlew :gbkt-analysis:test               # 11 analysis passes
./gradlew :gbkt-genre-rpg:test              # RPG genre
./gradlew :gbkt-genre-platformer:test       # Platformer genre
./gradlew :gbkt-genre-puzzle:test           # Puzzle genre
./gradlew :gbkt-genre-sport:test            # Sport genre
./gradlew :gbkt-emulator:test               # Coffee-GB wrapper, agent API
./gradlew :gbkt-test:test                   # Test infrastructure itself
./gradlew :gbkt-mcp-server:test             # MCP server
./gradlew :gbkt-examples:pong:test          # Per-example
./gradlew :gbkt-emulator:test --tests "*StepAgentTest*"  # Pattern match
```

## Four Testing Tiers

The full tier matrix (see `context/TESTING.md` and `gbkt-emulator/CLAUDE.md` for the canonical reference):

| Tier | Class Suffix | Speed | ROM Required | When to Use | Driven by |
|------|--------------|-------|--------------|-------------|-----------|
| 1: JVM unit | `*IRTest`, `*SimTest`, `*GameTest`, `*EmissionTest` | Fast (ms) | No | IR validation, logic correctness, codegen golden-shape | `SimulationContextV2`, direct `GameIR.build()`, `GBDKBackend.generate()` |
| 2: Emulator integration | `*EmulatorTest`, `*IntegrationTest`, `*StepAgentTest` | Slow (s) | Yes | Scene navigation, sprite visibility, runtime variable changes | `GbktTestExtension` + `StepAgent` |
| 3: UAT runner | `*UatTest` | Slow (s) | Yes | Checkpoint-based gameplay scenarios, golden screenshots | `UatRunner` |
| 4: MCP agent | n/a (interactive) | Slowest | Yes | Exploratory/AI-driven testing via Claude Code | `gbkt-mcp-server` with 17 tools |

### Tier 1: JVM unit tests

**Location:** `gbkt-*/src/test/kotlin/...` and `gbkt-examples/*/src/test/kotlin/...`

**Pattern A — IR validation tests** (canonical: `gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongIRTest.kt`):

```kotlin
class PongIRTest {
    private val ir = pongV2.build()

    @Test fun `has 3 scenes`() { assertEquals(3, ir.scenes.size) }
    @Test fun `has p1Score variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "p1Score" && it.type == VarType.U8 })
    }
    @Test fun `game scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "game" }.frameOps.isNotEmpty())
    }
}
```

**Pattern B — Simulation tests** using `SimulationContextV2` (`gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt`, used in `gbkt-core/.../SimulationContextV2Test.kt`):

```kotlin
@Test fun `ball bounces off top wall`() {
    val ctx = SimulationContext(pongV2)
    ctx.navigate("game")
    ctx.set("ballDy", -1)
    ctx.set("ball_y", 10)
    ctx.frame()
    assertEquals(1, ctx.get("ballDy"))
}
```

**Pattern C — Golden-shape codegen tests** (`*EmissionTest.kt`) lock the generated C against expected shape via brace-walked function-body extraction. Canonical pattern from `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt:63-87`:

```kotlin
private fun extractFunctionBody(cSource: String, functionName: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.contains("void $functionName(") }
    if (startIdx == -1) return ""
    val body = StringBuilder()
    var depth = 0
    var started = false
    for (i in startIdx until lines.size) {
        val line = lines[i]
        body.appendLine(line)
        for (ch in line) {
            if (ch == '{') { depth++; started = true }
            if (ch == '}') depth--
        }
        if (started && depth == 0) break
    }
    return body.toString()
}
```

Emission tests then `.contains()`-check tokens within the extracted body. This is the **scope-level grep gate** required by `CLAUDE.md` §"Scope-level grep gates": a file-level grep cannot distinguish `race_enter` from `title_enter` — the brace-walk locks the invariant to the specific function. Examples: `BanksEmissionTest.kt`, `SimplePhysicsEmissionTest.kt`, `PlatformerTemplateEmissionTest.kt`, `MetaspriteEmissionTest.kt`.

**Pattern D — Backend integration codegen tests** (`gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/IntegrationCodegenTest.kt`, `Phase067CodegenIntegrationTest.kt`) build small `GameIR` fixtures and assert specific C output.

### Tier 2: Emulator integration tests

**Location:** `gbkt-examples/*/src/test/kotlin/.../<Game>StepAgentTest.kt`, `<Game>EmulatorTest.kt`, plus `gbkt-emulator/src/test/kotlin/.../*StepAgentTest.kt`.

**Driven by `GbktTestExtension`** (`gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt`). The extension:
- Auto-discovers ROM, `.sym`, and `game_metadata.json` via `GameDiscovery.configForGame(gameName, screenshotDir)` (`gbkt-test/.../GameDiscovery.kt`)
- Auto-skips the test (via `Assumptions.assumeTrue(false, ...)`) when the ROM is not built — CI shows skipped, not failed
- Auto-captures a PNG screenshot and JSON variable dump to `build/gbkt/test-failures/failure_<class>_<test>_frame<N>.{png,json}` on `handleTestExecutionException`
- Closes the `StepAgent` in `afterEach`

Canonical use (`gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongStepAgentTest.kt:40-58`):

```kotlin
class PongStepAgentTest {
    @JvmField @RegisterExtension val game = GbktTestExtension("pong")

    @Test
    fun `metadata and symbol table agree on variable names`() {
        game.verifyMetadataSymbolAgreement(
            MetadataExpectation(
                expectedSceneCount = 3,
                expectedScenes = setOf(Scenes.TITLE, Scenes.GAME, Scenes.GAMEOVER),
                expectedActors = setOf(Actors.PADDLE1, Actors.PADDLE2, Actors.BALL),
                expectedOamCounts = mapOf(Actors.PADDLE1 to 2, Actors.PADDLE2 to 2, Actors.BALL to 1),
                expectedTotalOam = 5,
            )
        )
    }
}
```

### Tier 3: UAT runner tests

**Location:** `gbkt-examples/*/src/test/kotlin/.../<Game>UatTest.kt`.

**Driven by `UatRunner`** (`gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/UatRunner.kt`). The runner:
- Wraps `AgentDebugSession`, advances emulation via `wait()` / `press()` / `hold()` / `release()`
- Captures `checkpoint(label)` snapshots (screenshot + variables + debug-log slice)
- Provides **soft assertions** (`assertVariable`, `assertVariableInRange`, `assertScene`, `assertTextOnScreen`, `assertSpriteAt`, `assertCustom`) — failed assertions are recorded, not thrown immediately
- Compares against golden PNGs in `src/test/resources/golden/<game>/` when `goldenDir` is non-null
- Produces an aggregated JSON report via `generateReport()`

Canonical use (`gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongUatTest.kt:74-96`):

```kotlin
UatRunner("Pong", config, goldenDir = goldenDir, metadata = metadata).use { runner ->
    runner.start()
    runner.waitUntilTextOnScreen(Texts.PONG, maxFrames = 300)
    runner.assertTextOnScreen(Texts.PONG)
    runner.assertTextOnScreen(Texts.PRESS_START)
    runner.checkpoint("01_title")

    runner.press(Button.START, 5)
    if (metadata != null) runner.waitForScene(Scenes.GAME, maxFrames = 120) else runner.wait(60)
    runner.assertVariable(Variables.P1_SCORE, 0)
    runner.checkpoint("02_gameplay_start")
}
```

Output: `build/gbkt/uat/<label>.png` for each checkpoint and a JSON report at the end.

### Tier 4: MCP agent tests

**Driven by `gbkt-mcp-server`** (`gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/GbktMcpServer.kt`), which wraps `StepAgent` and exposes the emulator as **17 MCP tools** over stdio:

| Tool | Purpose |
|------|---------|
| `emulator_start` | Start a session (use `{"game":"pong"}` for convention-based discovery) |
| `emulator_stop` | Stop the current session |
| `emulator_step` | Advance N frames with button state |
| `emulator_press` | Single-button-press helper (N held frames + 1 release frame) |
| `emulator_observe` | Return the last `Observation` without stepping |
| `emulator_wait_for_scene` | Block until scene transition |
| `emulator_wait_for_variable` | Block until a named variable equals a value |
| `emulator_wait_until_text` | Block until text appears on screen |
| `emulator_read_variable` | Type-correct read using `VariableInspector.readTypedValue()` (signed/unsigned from `game_metadata.json`) |
| `emulator_write_variable` | Write a value by name |
| `emulator_screenshot` | Capture PNG |
| `emulator_describe_game` | Return full `GameMetadata` (scenes, actors, variables, texts, terminal scenes) |
| `emulator_save_state` | Snapshot WRAM+OAM+HRAM to `build/gbkt/savestates/<label>.gbst` |
| `emulator_load_state` | Restore from snapshot |
| `emulator_assert` | Batch validate (`variable_equals`, `variable_in_range`, `scene_is`, `text_on_screen`, `actor_visible`, `sprite_count`) |
| `emulator_get_playbook` | Return `PLAYBOOK.md` for the loaded game |
| `emulator_list_games` | List all built ROMs in the project |

**Setup:** `./gradlew :gbkt-mcp-server:shadowJar gbktSetupClaude` installs the skills and writes `.claude/mcp_servers.json`. See `context/TESTING.md` §"MCP Agent Testing" for the full workflow and `context/UAT_GUIDE.md` for debugging walkthroughs.

**Test fixture for MCP tools:** `gbkt-mcp-server/src/test/kotlin/io/github/gbkt/mcp/PongMcpPlaythroughTest.kt`, `ToolHandlersTest.kt`, `SessionLifecycleTest.kt`, `ObservationSerializerTest.kt`.

## GbktTestExtension API

`GbktTestExtension` is a JUnit5 `BeforeEachCallback` / `AfterEachCallback` / `TestExecutionExceptionHandler` (`gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt:57-63`).

### Constructor

```kotlin
class GbktTestExtension(
    val gameName: String,                          // ROM base name — looks up build/gbkt/output/<gameName>.gb
    private val customRomFile: File? = null,       // Override convention-based discovery
    private val bootScript: ((StepAgent) -> Unit)? = null, // Runs after agent.start(), before each test
    private val gbcMode: Boolean = false,          // For ROMs compiled with GBC_COMPATIBLE / GBC_ONLY
    internal val stubEmulatorFactory: (() -> GbEmulator)? = null, // Test-only stub injection
)
```

### Fluent assertions (`GbktGameAssertions.kt`)

Available as both top-level functions and extension functions on `GbktTestExtension`:

| Function | Asserts |
|----------|---------|
| `assertScene(obs, expected, message?)` | `obs.scene == expected`. Failure includes frame number and actual scene |
| `assertVariable(obs, name, expected, message?)` | Named variable equals expected. Failure lists all available variable names |
| `assertActorVisible(obs, actorName, message?)` | Actor with the given name is in `obs.actors`. Failure lists present actors |
| `assertTextOnScreen(obs, text, message?)` | Substring found on `obs.bgText` or `obs.winText`. Failure lists both layers' non-blank rows |

Plus the metadata-vs-symbol-table cross-check `verifyMetadataSymbolAgreement(expectation, currentSceneVar = "current_scene")` (`GbktTestRecipes.kt:266-404`) — a no-emulator recipe that checks: actor X/Y vars in `.noi`, scene names/indices match between `game_metadata.json` and `game.h`, scene count + names match expectation, expected actors exist with correct OAM counts, total OAM count, no OAM slot overlap.

### Recipes (`GbktTestRecipes.kt`)

Five composable extension functions on `GbktTestExtension`:

| Recipe | Purpose |
|--------|---------|
| `verifyTitleScreen(expectedTexts = emptyList())` | Boots 120 frames, asserts on title/initial scene, asserts each expected text on screen |
| `verifyFirstSceneTransition()` | Best-effort: presses START, waits ≤300 frames for first non-title scene. Logs warning (no fail) on complex navigation |
| `verifyInputResponds(scene, button, variableName, expectDecrease = false)` | Boots to scene, holds button 30 frames, asserts variable changed in expected direction |
| `verifySpriteVisibility(scene, expectedActors)` | Boots to scene, steps 10 frames, asserts each actor visible |
| `bootToScene(sceneName, maxFrames = 600)` | Boot flow: step 1, return if already there; else `waitForScene`; else press START + wait again; else throw `AssertionError` |

### Direct StepAgent access

```kotlin
game.step(buttons)                  // Advance 1 frame
game.stepN(n, buttons)              // Advance N frames
game.readVariable("score")          // Int? — null if absent
game.writeVariable("score", 0)      // Boolean — true if written
game.waitForScene("gameplay", 600)  // Observation at target scene
game.captureScreenshot("label")     // File pointing at the PNG
game.readByte(0xC100)               // Raw byte at address
game.agent.listVariables()          // Sorted variable names
game.agent.listScenes()             // Scene names from metadata
game.agent.listActors()             // Actor names from metadata
game.metadata?.terminalScenes       // Set<String> from metadata
```

## Test Discovery

**ROM discovery** (`gbkt-test/src/main/kotlin/io/github/gbkt/test/GameDiscovery.kt`):
- `configForGame(gameName, screenshotDir)` → `AgentSessionConfig?` (null if ROM not found)
- Walks up from `user.dir` looking for `build/gbkt/output/<gameName>.gb` under the Gradle subproject root
- Supports both standalone projects (`build/gbkt/output/`) and the multi-game layout (`gbkt-examples/<game>/build/gbkt/output/`)
- Returns `null` (not exception) on miss so `GbktTestExtension` can call `Assumptions.assumeTrue(false, ...)`

**Auto-discovery of associated files** (`gbkt-emulator/.../AgentSessionConfig.discoverFiles(romFile)`): finds the `.sym` / `.noi` symbol file, `game_metadata.json`, and `main.c.gbkt.map` source map by convention alongside the ROM. Auto-loaded by both `StepAgent` and `UatRunner`.

## GameConstants Pattern

Each game module emits a `GameConstants` object from `game_metadata.json` via the `GenerateGameConstantsTask` (triggered by `generateC`). Located at `build/generated/source/gbkt/main/GameConstants.kt`:

```kotlin
object GameConstants {
    object Scenes { const val TITLE = "title"; const val GAMEPLAY = "gameplay" }
    object Actors { const val BALL = "ball"; const val PADDLE = "paddle" }
    object Variables { const val SCORE = "score"; const val LIVES = "lives" }
    object Texts { const val PRESS_START = "PRESS START" }
}
```

**Usage** (`gbkt-examples/pong/src/test/kotlin/.../PongStepAgentTest.kt:12-15`):

```kotlin
import io.github.gbkt.examples.pong.GameConstants.Scenes
import io.github.gbkt.examples.pong.GameConstants.Actors
import io.github.gbkt.examples.pong.GameConstants.Texts
import io.github.gbkt.examples.pong.GameConstants.Variables

@Test fun `boot to gameplay`() {
    val obs = game.bootToScene(Scenes.GAME)
    assertVariable(obs, Variables.SCORE, 0)
    assertActorVisible(obs, Actors.BALL)
}
```

**Regenerate when DSL changes:**
```bash
./gradlew :gbkt-examples:pong:generateC
```

A compilation error in tests referencing `GameConstants.Scenes.TITLE` after adding a new scene means the file is stale — re-run `generateC`.

## PLAYBOOK.md Format

A natural-language game description **for agents and humans**, located at `<game-project-root>/PLAYBOOK.md`. Returned by `emulator_get_playbook`. Idempotent generator: `./gradlew :gbkt-examples:<game>:generatePlaybook` never overwrites an existing file.

**Structure** (canonical: `gbkt-examples/pong/PLAYBOOK.md`):

```markdown
# GameName

## Overview
2-3 sentences describing the game.

## How to Play
Core mechanics and goals.

## Controls
| Scene    | Button | Effect            |
|----------|--------|-------------------|
| title    | START  | Start the game    |
| gameplay | RIGHT  | Move paddle right |

## Scene Flow
- title -> gameplay
- gameplay -> gameover

## Win / Lose Conditions
Description of winning and losing.

## Known Quirks
Game-specific edge cases the agent should know about.

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| score    | U8   | score    | Player score (0-255) |
```

All playbooks should be version-controlled (they live in the project root, not `build/`).

## Test File Organization

**Location:** `gbkt-*/src/test/kotlin/io/github/gbkt/<module>/...` mirrors `src/main/kotlin/` package structure.

**Naming by tier:**

```
gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/
├── PongIRTest.kt          # Tier 1 — IR shape validation (no ROM)
├── PongGameTest.kt        # Tier 1 — Simulation tests via SimulationContext
├── PongStepAgentTest.kt   # Tier 2 — Real emulator via GbktTestExtension
├── PongEmulatorTest.kt    # Tier 2 — Emulator integration (alt name)
└── PongUatTest.kt         # Tier 3 — UatRunner checkpoint scenarios
```

**`*EmissionTest.kt`** (golden-shape codegen tests) live in either the game module (`gbkt-examples/banks/.../BanksEmissionTest.kt`, `gbkt-examples/simple-physics/.../SimplePhysicsEmissionTest.kt`) or the genre/backend module (`gbkt-genre-platformer/.../JumpHoldEmissionTest.kt`).

**Test fixtures** that need golden PNG comparison live at `<game>/src/test/resources/golden/<game>/<label>.png`.

## Mocking

**Framework:** no formal mocking framework. The codebase uses **stub factory injection** for the emulator.

```kotlin
class GbktTestExtension(
    /* ... */,
    internal val stubEmulatorFactory: (() -> GbEmulator)? = null,
)

class UatRunner(
    /* ... */,
    private val stubEmulatorFactory: (() -> GbEmulator)? = null,
)
```

`StepAgent` and `UatRunner` use the factory when non-null instead of creating a real `CoffeeGbEmulator`. This is the pattern for unit-testing the agent layer without a real ROM. See `gbkt-emulator/src/test/kotlin/.../UatRunnerTest.kt` and `gbkt-emulator/src/test/kotlin/.../CoffeeGbEmulatorTest.kt` for usage.

**What to mock:** the `GbEmulator` interface (`gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/GbEmulator.kt`) when testing the agent / runner / MCP layers in isolation.

**What NOT to mock:** IR, DSL builders, codegen. These are pure data transformations and should be tested directly. Mocking would obscure the real value.

## Fixtures and Factories

**Test data:** game definitions are themselves the test fixtures. The `pongV2` `Game` value in `gbkt-examples/pong/src/main/kotlin/.../PongV2.kt` is imported into `PongIRTest`, `PongGameTest`, etc.

**Stub `MemoryAccess`** for tests that need a memory interface but not real emulator state (`gbkt-test/.../GbktTestRecipes.kt:283-288`):

```kotlin
val zeroMemory = object : MemoryAccess {
    override fun readByte(address: Int): Int = 0
    override fun writeByte(address: Int, value: Int) {}
}
```

## Coverage

**Tool:** Kover (`xmlReportPaths = "**/build/reports/kover/report.xml"` in `build.gradle.kts:22`)

**Targets:** uploaded to SonarCloud (`sonar.projectKey = svachmic_gbkt`). No hard coverage gate enforced.

## Common Patterns

### Async/wait patterns

```kotlin
// Time-based: step N frames
val obs = game.stepN(120)

// Predicate-based: wait until scene reached
val obs = game.waitForScene("gameplay", 600)

// Predicate-based: wait until text appears
val obs = game.agent.waitUntilTextOnScreen("GAME OVER", 300)

// Generic: wait until predicate true
val obs = game.agent.waitUntil(maxFrames = 600) { it.variables["score"] ?: 0 > 0 }
```

### Error testing

```kotlin
@Test fun `set called outside ScriptBuilder throws`() {
    assertFailsWith<IllegalStateException> {
        AssignableVar("x") set 0   // No active ScriptBuilderContext
    }
}
```

### Skip-on-missing-prerequisite

```kotlin
Assumptions.assumeTrue(ROM_FILE.exists(), "pong.gb not found — run buildRom first")
Assumptions.assumeTrue(
    metadataFile.exists() && symFile.exists(),
    "metadata or .noi not found — run buildRom first",
)
```

JUnit5 reports skipped (not failed) — CI pipelines that have not run `buildRom` show skipped tests, not red builds.

## Visual Evidence Rule

**Codified in `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule" and `userMemory feedback_visual_evidence_for_visual_truths.md`.**

For truths shaped **"X is visible on screen"** (e.g. "track tilemap is visible", "HUD shows lap count", "menu cursor is highlighted"), evidence MUST include a runtime screenshot. **Variable-state assertions are insufficient.**

### Why

A variable assertion like `assertVariable(obs, "_current_tileset_id", 1)` proves that the codegen wrote `1` at one point in `scene_enter`. It does NOT prove that the value is visually reflected by the time the player sees the screen — a subsequent op (e.g. a user-authored `clear()` lowering to `cls()`) can wipe the visual outcome while leaving the variable intact. Phase 07.4 round-2 hit exactly this trap: SC-4 ("track visible") passed via `_current_tileset_id=1` while the runtime ROM rendered an empty screen. The bug took plans 15–18 to surface and was only caught by user UAT in round 4 (`.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md`).

### When the rule applies

- GSD verifier runs against truths/SCs whose phrasing is visual ("is visible", "renders on screen", "is shown to the player")
- MCP play-throughs verifying SCs at runtime
- UAT verdicts that flip a phase to `passed`

### When variable evidence is sufficient

- Internal state truths ("AI active", "lap counter incremented", "save written") — the visual surface is downstream of (and inferred from) the state
- JVM-tier codegen tests (`*EmissionTest.kt`) that lock the generated C shape, which is upstream of runtime visual outcomes — a generated-C grep is acceptable evidence here because it locks the contract one level below the visual

### How to satisfy the rule

Use the MCP tool `emulator_screenshot(path)`. Capture a PNG to the phase's `evidence/` directory at every visual SC checkpoint. The screenshot becomes the evidence artifact in `*-VERIFICATION.md`.

### Scope-level grep gates (corollary)

A file-level `grep -c cls() bank1.c` cannot distinguish `race_enter` from `title_enter` — if `title_enter` has back-compat `cls()`, the count masks a regression in `race_enter`. For per-function invariants, extract the function body via brace-walk (see `extractFunctionBody` pattern in `BanksEmissionTest.kt:63-87` above) and grep WITHIN scope.

## Test Failure Diagnostics

On test failure, `GbktTestExtension.handleTestExecutionException` writes to `build/gbkt/test-failures/`:

```
failure_<ClassName>_<testName>_frame<N>.png   ← screenshot at failure
failure_<ClassName>_<testName>_frame<N>.json  ← all variables as JSON at failure frame
```

The JSON dump contains every variable visible at failure time — useful for diagnosing wrong values without an interactive debugger. The capture itself is wrapped in `try/catch` so a failure in the diagnostics doesn't mask the original test failure (`GbktTestExtension.kt:125-141`).

## See Also

- `context/TESTING.md` — complete testing guide (tiers, `GbktTestExtension`, recipes, `GameConstants`, playbook, MCP)
- `context/UAT_GUIDE.md` — MCP debugging workflow with real walkthroughs (variable inspector sym parsing, signed variable reading, scene transition diagnosis)
- `gbkt-test/CLAUDE.md` — `gbkt-test` module-level details (assertion / recipe authoring conventions)
- `gbkt-emulator/CLAUDE.md` — `StepAgent`, `Observation`, `GameMetadata`, `UatRunner`, hung-ROM watchdog
- `gbkt-mcp-server/CLAUDE.md` — MCP tool reference, session model

---

*Testing analysis: 2026-05-27*
