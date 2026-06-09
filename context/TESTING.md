# Testing Guide

Complete reference for writing and running game tests in gbkt — from fast unit tests to real-ROM emulator integration tests and AI agent sessions.

## Test Tiers Overview

gbkt has three testing tiers. Use the lightest tier that covers your correctness need.

| Tier | Class Suffix | Speed | ROM Required | When to Use |
|------|-------------|-------|--------------|-------------|
| **Tier 1 — Simulation** | `*SimTest`, `*GameTest` | Fast (ms) | No | Logic correctness, IR output, codegen output |
| **Tier 2 — Emulator** | `*EmulatorTest`, `*IntegrationTest`, `*StepAgentTest` | Slow (s) | Yes | Scene navigation, sprite visibility, variable changes at runtime |
| **Tier 3 — MCP Agent** | N/A (interactive) | Slowest | Yes | Exploratory / AI-driven testing via Claude Code |

### Tier 1: Simulation Tests

`SimulationContext` (in `gbkt-core`) executes game logic without a real emulator. Use it to check DSL variable manipulation, scene transitions, and codegen output correctness. No ROM build required.

```kotlin
// Example: SimulationContext test
class PongSimTest {
    @Test fun `score increments on right wall hit`() {
        val ctx = SimulationContext()
        ctx.run(pongGame)
        ctx.simulate("gameplay", frames = 120)
        assertTrue(ctx.readVariable("score") > 0)
    }
}
```

### Tier 2: Emulator Integration Tests

`GbktTestExtension` (in `gbkt-test`) manages a headless Coffee-GB emulator per test. Use it for runtime behaviour: scene booting, sprite rendering, button response, VRAM text.

Tests **auto-skip** when the ROM has not been built — run `./gradlew buildRom` first.

Tests **auto-capture** a screenshot and variable dump JSON on failure.

### Tier 3: MCP Agent Testing

`gbkt-mcp-server` exposes the emulator as 19 MCP tools over stdio. Claude Code can call `emulator_start`, `emulator_step`, `emulator_assert`, etc. to explore and validate game behaviour interactively.

See the **MCP Agent Testing** section below.

---

## GbktTestExtension API

`GbktTestExtension` is a JUnit5 `Extension` providing zero-boilerplate lifecycle management for emulator integration tests.

### Usage

```kotlin
import io.github.gbkt.test.GbktTestExtension
import io.github.gbkt.test.assertTextOnScreen
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PongStepAgentTest {
    @JvmField
    @RegisterExtension
    val game = GbktTestExtension("pong")

    @Test
    fun `title screen shows PONG`() {
        val obs = game.stepN(120)
        assertTextOnScreen(obs, "PONG")
    }
}
```

### Constructor Parameters

```kotlin
class GbktTestExtension(
    val gameName: String,                          // ROM base name (e.g., "pong")
    private val customRomFile: File? = null,       // Explicit ROM path — overrides discovery
    private val bootScript: ((StepAgent) -> Unit)? = null, // Runs after start(), before test
    private val gbcMode: Boolean = false,          // Enable GBC emulation (GBC_COMPATIBLE ROMs)
)
```

**`gameName`** — Must match the ROM file base name. `GbktTestExtension("pong")` looks for `build/gbkt/output/pong.gb` under the Gradle subproject root.

**`customRomFile`** — Provide an explicit `File` to bypass convention-based discovery.

**`bootScript`** — Lambda that runs after `agent.start()` in `beforeEach`. Useful to skip to a specific scene before each test:

```kotlin
val game = GbktTestExtension("platformer-template") { agent ->
    agent.step(setOf(Button.START))  // skip title
    agent.waitForScene("gameplay", 300)
}
```

**`gbcMode`** — Required for ROMs compiled with `-Wm-yc` (GBC_COMPATIBLE) or `-Wm-yC` (GBC_ONLY):

```kotlin
val game = GbktTestExtension("platformer-template", gbcMode = true)
```

### Lifecycle

| Phase | What Happens |
|-------|-------------|
| `beforeEach` | Discovers ROM via `GameDiscovery`; skips test with assumption if ROM missing; loads `game_metadata.json`; creates `StepAgent`; calls `agent.start()`; runs `bootScript` |
| `afterEach` | Calls `agent.close()` — stops emulator, frees resources |
| `handleTestExecutionException` | Captures screenshot to `build/gbkt/test-failures/`; writes JSON variable dump sidecar; re-throws exception |

### Auto-Skip on Missing ROM

When the ROM has not been built, `GbktTestExtension` calls `Assumptions.assumeTrue(false, ...)`. JUnit5 marks the test as **skipped** (not failed). CI pipelines that have not run `buildRom` will see skipped rather than failed tests.

### Accessing the Agent

```kotlin
// Delegate methods on GbktTestExtension (shortcuts)
game.step(buttons)            // Advance 1 frame
game.stepN(n, buttons)        // Advance N frames
game.readVariable("score")    // Read variable by name
game.writeVariable("score", 0) // Write variable by name
game.waitForScene("gameplay", 600) // Wait up to 600 frames
game.captureScreenshot("label")    // Save PNG

// Direct agent access
game.agent.listVariables()    // All variable names from .sym file
game.agent.listScenes()       // All scene names from metadata
game.agent.listActors()       // All actor names from metadata
game.agent.describeGame()     // Returns GameMetadata?

// Metadata access (null if game_metadata.json not found)
game.metadata?.scenes?.sceneNames
game.metadata?.terminalScenes
```

---

## Fluent Assertions

Four top-level assertion functions in `io.github.gbkt.test`. Also available as extension functions on `GbktTestExtension`.

### assertScene

```kotlin
fun assertScene(obs: Observation, expected: String, message: String? = null)
```

Asserts the observation's current scene matches `expected`. Failure message includes frame number and actual scene.

```kotlin
val obs = game.stepN(120)
assertScene(obs, "title")
```

### assertVariable

```kotlin
fun assertVariable(obs: Observation, name: String, expected: Int, message: String? = null)
```

Asserts a named variable equals `expected`. Failure message includes all available variable names when the variable is absent.

```kotlin
assertVariable(obs, "score", 0, message = "score should start at 0")
```

### assertActorVisible

```kotlin
fun assertActorVisible(obs: Observation, actorName: String, message: String? = null)
```

Asserts an actor with the given name appears in the observation's actors list. Actor names come from DSL `val ball by actor { ... }` declarations.

```kotlin
assertActorVisible(obs, "ball")
assertActorVisible(obs, "paddle")
```

### assertTextOnScreen

```kotlin
fun assertTextOnScreen(obs: Observation, text: String, message: String? = null)
```

Asserts `text` appears as a substring on either the background or window tilemap layer. Failure message shows both layers' non-blank text rows.

```kotlin
assertTextOnScreen(obs, "PRESS START")
assertTextOnScreen(obs, "GAME OVER")
```

### Extension function style

All four functions also exist as extension functions on `GbktTestExtension` so they can be called with `game.` prefix in test method bodies:

```kotlin
game.assertScene(obs, "title")
game.assertVariable(obs, "lives", 3)
game.assertActorVisible(obs, "ball")
game.assertTextOnScreen(obs, "PONG")
```

---

## Test Recipes

Five composable extension functions on `GbktTestExtension` in `GbktTestRecipes.kt`. All use metadata when available; fall back to heuristics otherwise.

### verifyTitleScreen

```kotlin
fun GbktTestExtension.verifyTitleScreen(expectedTexts: List<String> = emptyList()): Observation
```

Boots 120 frames and asserts the game is on the title/initial scene. If `expectedTexts` is provided, each string must appear on screen.

```kotlin
@Test fun `title screen shows correctly`() {
    game.verifyTitleScreen(listOf("PONG", "PRESS START"))
}
```

### verifyFirstSceneTransition

```kotlin
fun GbktTestExtension.verifyFirstSceneTransition()
```

Best-effort check that pressing START can reach the first non-title scene. Uses metadata scene list. No-op if metadata is absent or only one scene exists. Logs a warning (not fail) when a complex navigation path is required.

### verifyInputResponds

```kotlin
fun GbktTestExtension.verifyInputResponds(
    scene: String,
    button: Button,
    variableName: String,
    expectDecrease: Boolean = false,
)
```

Navigates to `scene`, holds `button` for 30 frames, and asserts `variableName` changed. Use `expectDecrease = true` for variables that decrease (e.g., torch level).

```kotlin
@Test fun `right dpad moves paddle`() {
    game.verifyInputResponds("gameplay", Button.RIGHT, "paddle_x")
}
```

### verifySpriteVisibility

```kotlin
fun GbktTestExtension.verifySpriteVisibility(scene: String, expectedActors: List<String>)
```

Navigates to `scene`, steps 10 frames, and asserts each actor in `expectedActors` is visible.

```kotlin
@Test fun `gameplay sprites are present`() {
    game.verifySpriteVisibility("gameplay", listOf("ball", "paddle"))
}
```

### bootToScene

```kotlin
fun GbktTestExtension.bootToScene(sceneName: String, maxFrames: Int = 600): Observation
```

Navigates to `sceneName` from the current emulator state. Boot flow:
1. Step one frame — return immediately if already in target scene.
2. Call `waitForScene(sceneName, maxFrames)`.
3. If not reached, press START and wait again.
4. Throw `AssertionError` if still not reached.

```kotlin
@Test fun `gameplay starts with 3 lives`() {
    val obs = game.bootToScene("gameplay")
    assertVariable(obs, "lives", 3)
}
```

---

## GbktGameReporter

`GbktGameReporter` aggregates test results per game and prints a human-readable summary after all tests complete.

```kotlin
class PongTest {
    companion object {
        @JvmField val reporter = GbktGameReporter("pong")

        @AfterAll @JvmStatic fun teardown() { reporter.printSummary() }
    }

    @JvmField @RegisterExtension val game = GbktTestExtension("pong")

    @Test fun `title screen boots correctly`() {
        val obs = game.verifyTitleScreen(listOf("PONG"))
        reporter.recordScene("title")
        reporter.recordPass()
    }
}
```

**Output:**
```
=== pong Test Summary ===
  Scenes verified: title, gameplay
  Assertions: 12 passed, 0 failed
  Screenshots: 3 captured
==============================
```

**API:**

| Method | Description |
|--------|-------------|
| `recordScene(scene: String)` | Record a verified scene |
| `recordPass()` | Increment passing assertion count |
| `recordFail()` | Increment failing assertion count |
| `recordScreenshot()` | Increment captured screenshot count |
| `printSummary()` | Print formatted summary to stdout |

---

## GameConstants

Each game module can generate a type-safe constants object from its `game_metadata.json`. Import it to avoid hardcoding scene names, actor names, and variable names as strings.

### Generated Structure

```kotlin
// Generated by GenerateGameConstantsTask (triggered by generateC)
// Located at: build/generated/source/gbkt/main/GameConstants.kt
object GameConstants {
    object Scenes {
        const val TITLE = "title"
        const val GAMEPLAY = "gameplay"
        const val GAMEOVER = "gameover"
    }
    object Actors {
        const val BALL = "ball"
        const val PADDLE = "paddle"
    }
    object Variables {
        const val SCORE = "score"
        const val LIVES = "lives"
        const val BALL_DX = "ball_dx"
    }
    object Texts {
        const val PRESS_START = "PRESS START"
    }
    object Controls {
        // Scene → button → type mappings
    }
}
```

### Using GameConstants in Tests

```kotlin
import io.github.gbkt.examples.pong.GameConstants

@Test fun `gameplay has correct initial state`() {
    val obs = game.bootToScene(GameConstants.Scenes.GAMEPLAY)
    assertVariable(obs, GameConstants.Variables.SCORE, 0)
    assertActorVisible(obs, GameConstants.Actors.BALL)
}
```

### Regenerating GameConstants

GameConstants is regenerated automatically when you run `./gradlew generateC`. If assertions fail because constants are stale:

```bash
./gradlew :gbkt-examples:pong:generateC
# Then rebuild your test project
```

---

## Playbook Format

A `PLAYBOOK.md` file co-located in each game project root provides a natural-language game description for LLM agents and human testers.

### Purpose

Playbooks are **descriptions for agents**, not test scripts. They explain game mechanics, controls, and scene flow in plain language so an LLM agent can formulate correct test actions before starting an emulator session.

### Structure

```markdown
# GameName

## Overview
2-3 sentences describing the game.

## How to Play
Core mechanics and goals.

## Controls
| Scene    | Button | Type  |
|----------|--------|-------|
| gameplay | RIGHT  | held  |
| gameplay | LEFT   | held  |

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

### Generating a Playbook Skeleton

```bash
# Requires game_metadata.json — run generateC first
./gradlew :gbkt-examples:pong:generateC
./gradlew :gbkt-examples:pong:generatePlaybook
```

The task is **idempotent** — it never overwrites an existing `PLAYBOOK.md`. Edit the skeleton to fill in game-specific content. The file lives in the project root (not `build/`) so it can be version-controlled.

### Accessing Playbooks via MCP

The `emulator_get_playbook` tool returns the contents of `PLAYBOOK.md` for the currently loaded game. Agents should call it immediately after `emulator_start`.

---

## MCP Agent Testing

`gbkt-mcp-server` exposes the gbkt emulator as 19 MCP tools over stdio transport, enabling Claude Code and other MCP clients to test games interactively.

### Setup

**Automated (recommended):**

```bash
# Build the MCP server JAR, then install skills + config in one step
./gradlew :gbkt-mcp-server:shadowJar gbktSetupClaude
```

This installs `/gbkt-play-game` and `/gbkt-test-game` skills and configures `.claude/mcp_servers.json` automatically.

**Manual setup:**

```bash
# 1. Build the shadow JAR
./gradlew :gbkt-mcp-server:shadowJar
# Output: gbkt-mcp-server/build/libs/gbkt-mcp-server-all.jar

# 2. Configure Claude Code
# Add to .claude/mcp_servers.json (or Claude Desktop config):
```

```json
{
  "gbkt-emulator": {
    "type": "stdio",
    "command": "java",
    "args": ["-jar", "/path/to/gbkt-mcp-server-all.jar"]
  }
}
```

### 19 MCP Tools

| Tool | Input | Description |
|------|-------|-------------|
| `emulator_start` | `romFile?`, `game?`, `symFile?`, `metadataFile?`, `gbcMode?` | Start session. Use `game` for convention-based discovery (e.g., `{"game": "pong"}`) |
| `emulator_stop` | — | Stop current session |
| `emulator_step` | `frames?`, `buttons?` | Advance N frames with button state |
| `emulator_press` | `button`, `frames?` | Press a single button for N frames then release (hold + release in one call) — use instead of `emulator_step` for simple taps |
| `emulator_observe` | — | Return last observation without stepping |
| `emulator_wait_for_scene` | `scene`, `maxFrames` | Wait until scene transition |
| `emulator_wait_for_variable` | `name`, `expected`, `maxFrames` | Wait until variable equals value |
| `emulator_wait_until_text` | `text`, `maxFrames` | Wait until text appears on screen |
| `emulator_read_variable` | `name` | Read variable by name |
| `emulator_write_variable` | `name`, `value` | Write variable by name |
| `emulator_read_memory` | `address`, `length?` | Read N bytes from a raw Game Boy memory address (hardware register / OAM inspection) |
| `emulator_write_memory` | `address`, `value` | Write a single byte to a raw Game Boy memory address |
| `emulator_screenshot` | `label` | Capture PNG screenshot |
| `emulator_describe_game` | — | Return full game metadata (scenes, actors, variables, texts) |
| `emulator_save_state` | `label` | Save emulator state to file |
| `emulator_load_state` | `label` | Restore previously saved state |
| `emulator_assert` | `checks` | Batch-assert multiple conditions (see below) |
| `emulator_get_playbook` | — | Return `PLAYBOOK.md` contents for loaded game |
| `emulator_list_games` | — | List all built ROMs in project |

### Convention-Based Game Discovery

`emulator_start` accepts a `game` parameter for project-relative ROM resolution:

```json
{"game": "pong"}
```

Resolves `build/gbkt/output/pong.gb` (standalone project) or `gbkt-examples/pong/build/gbkt/output/pong.gb` (multi-game layout). Auto-discovers `.sym` and `game_metadata.json` files alongside the ROM.

### emulator_assert Check Types

`emulator_assert` accepts a `checks` array for batch validation without advancing frames:

| Type | Args | Example |
|------|------|---------|
| `variable_equals` | `name`, `expected` | `{"type":"variable_equals","name":"score","expected":10}` |
| `variable_in_range` | `name`, `min`, `max` | `{"type":"variable_in_range","name":"lives","min":1,"max":3}` |
| `scene_is` | `scene` | `{"type":"scene_is","scene":"gameplay"}` |
| `text_on_screen` | `text` | `{"type":"text_on_screen","text":"PRESS START"}` |
| `actor_visible` | `name` | `{"type":"actor_visible","name":"ball"}` |
| `sprite_count` | `expected` | `{"type":"sprite_count","expected":2}` |

```json
{
  "checks": [
    {"type": "scene_is", "scene": "gameplay"},
    {"type": "variable_equals", "name": "score", "expected": 0},
    {"type": "actor_visible", "name": "ball"}
  ]
}
```

Returns `{passed: N, failed: M, results: [...]}`.

### Save/Load State

```json
// Save: snapshot current WRAM+OAM+HRAM
{"label": "before-boss-fight"}

// Load: restore to snapshot
{"label": "before-boss-fight"}
```

State files are written to `build/gbkt/savestates/<label>.gbst`. Load always steps one frame to refresh the cached observation.

### Typical Agent Workflow

1. Call `emulator_list_games` to discover available ROMs.
2. Call `emulator_start` with `{"game": "pong"}`.
3. Call `emulator_get_playbook` to read game description.
4. Call `emulator_describe_game` for metadata (scenes, variables, actors).
5. Call `emulator_step` with `{"frames": 120}` to boot the game.
6. Call `emulator_assert` to validate initial state.
7. Save state: `emulator_save_state` with `{"label": "initial"}`.
8. Test a flow; restore with `emulator_load_state` to test another.

### Headed vs Headless Mode

The MCP server supports two modes:

| Mode | Flag | Use case |
|------|------|----------|
| **Headed** | `--headed` | Developer watches the agent play — a Swing window opens showing the Game Boy LCD in real time |
| **Headless** | (default) | CI / automated tests. No display window. Fast, no GUI dependencies. |

When `--headed` is passed in the MCP server's `args`, every `emulator_start` call opens a 640x576 Swing window (160x144 at 4x scale) showing exactly what the Game Boy screen looks like. The agent still controls all input — you just watch.

The same flag works for programmatic use:

```kotlin
// Headed — opens viewer window
val config = AgentSessionConfig(romFile = rom, headless = false)

// Headless — CI mode (default)
val config = AgentSessionConfig(romFile = rom)
```

### Worked Example — Pong Playthrough

What a real Claude Code MCP session looks like testing Pong, step by step:

```
# 1. Start the emulator
emulator_start(romFile: "gbkt-examples/pong/build/gbkt/output/pong.gb",
               symFile: "gbkt-examples/pong/build/gbkt/output/pong.noi",
               metadataFile: "gbkt-examples/pong/build/gbkt/generated/game_metadata.json")
→ { started: true, metadata: { scenes: ["game","gameover","title"], actors: [...] } }

# 2. Describe the game
emulator_describe_game()
→ { scenes: ["game","gameover","title"],
    actors: [{ name: "paddle1", oamCount: 2, xVar: "paddle1_x", yVar: "paddle1_y" }, ...],
    variables: [{ name: "p1Score", type: "UINT8" }, ...],
    terminalScenes: ["gameover"] }

# 3. Boot to title screen
emulator_step(frames: 120)
→ { frame: 120, scene: "title", bgText: ["...PONG...", "...PRESS START..."] }

# 4. Wait for text
emulator_wait_until_text(text: "PRESS START", maxFrames: 10)
→ { met: true, framesElapsed: 0 }

# 5. Press START and transition to gameplay
emulator_press(button: "start")
emulator_wait_for_scene(scene: "game", maxFrames: 60)
→ { met: true, framesElapsed: 3, observation: { scene: "game", sprites: [...] } }

# 6. Observe gameplay state
emulator_observe()
→ { scene: "game", actors: [{ name: "ball", x: 80, y: 72 }, ...], sprites: 5 visible }

# 7. Move paddle and verify
emulator_step(frames: 30, buttons: ["up"])
emulator_read_variable(name: "paddle1_y")
→ { name: "paddle1_y", value: 34 }   // Moved up by 30 frames of input

# 8. Force near-win state
emulator_write_variable(name: "p1Score", value: 4)

# 9. Take a screenshot
emulator_screenshot(label: "near_win")
→ { filePath: "/path/to/screenshots/near_win_f210.png" }

# 10. Let game play out, then stop
emulator_step(frames: 300)
→ { frame: 510, scene: "gameover", isTerminal: true }
emulator_stop()
```

The workflow is identical for any gbkt game: build the ROM, start with `{"game": "<name>"}`, call `emulator_describe_game` to discover scenes/actors/variables, then boot, navigate, observe, assert. The metadata file (`game_metadata.json`) is emitted by the codegen pipeline alongside the generated C and provides everything an agent needs without reading source code.

---

## Troubleshooting

### ROM Not Found — Tests are Skipped

**Symptom:** All emulator tests show as skipped in JUnit output.

**Cause:** `buildRom` has not been run for the game module.

**Fix:**
```bash
./gradlew :gbkt-examples:pong:buildRom
# Then re-run tests
./gradlew :gbkt-examples:pong:test
```

### Metadata Stale — Wrong Scene Names or Missing Variables

**Symptom:** `game.metadata` has outdated scenes, or `assertVariable` fails with "variable not found".

**Cause:** `game_metadata.json` is only updated when `generateC` runs. If DSL was changed without regenerating, the metadata is stale.

**Fix:**
```bash
./gradlew :gbkt-examples:pong:generateC
./gradlew :gbkt-examples:pong:buildRom
```

### Screenshot Location

On test failure, `GbktTestExtension` writes files to:
```
<game-module>/build/gbkt/test-failures/
  failure_ClassName_testName_frameN.png   ← screenshot
  failure_ClassName_testName_frameN.json  ← variable dump
```

The JSON dump contains all variables visible at failure time — useful for diagnosing wrong variable values without a debugger.

### GameConstants Not Regenerated

**Symptom:** Compilation error in test file referencing `GameConstants.Scenes.TITLE` after adding a new scene.

**Cause:** `GameConstants.kt` is generated into `build/generated/source/gbkt/main/` and must be regenerated when the DSL changes.

**Fix:**
```bash
./gradlew :gbkt-examples:pong:generateC
```

### Assertion Fails at Wrong Frame

**Symptom:** `assertScene(obs, "gameplay")` fails even though the game appears to boot correctly.

**Cause:** The game may require more than 120 frames to reach `gameplay`. The boot animation or title screen may be longer.

**Fix:** Use `waitForScene` instead of a fixed frame count:
```kotlin
val obs = game.agent.waitForScene("gameplay", 600)
assertScene(obs, "gameplay")
```

Or provide a `bootScript` to the extension to automate scene navigation:
```kotlin
val game = GbktTestExtension("platformer-template") { agent ->
    agent.waitForScene("title", 120)
    agent.step(setOf(Button.START))
    agent.waitForScene("gameplay", 300)
}
```

### MCP Server Not Starting

**Symptom:** Claude Code reports "failed to connect to gbkt-emulator MCP server".

**Cause:** Shadow JAR not built, or path in config is wrong.

**Fix:**
```bash
./gradlew :gbkt-mcp-server:shadowJar
# Verify: gbkt-mcp-server/build/libs/gbkt-mcp-server-all.jar exists
ls gbkt-mcp-server/build/libs/
```

Check `.claude/mcp_servers.json` uses the absolute path to the JAR.
