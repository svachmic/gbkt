# Phase 9: Port simple_physics GBDK Example to gbkt — Pattern Map

**Mapped:** 2026-05-13
**Files analyzed:** 11 (7 Wave 0 new files + 2 existing files modified + 2 build/evidence files)
**Analogs found:** 10 / 11

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `gbkt-examples/simple-physics/build.gradle.kts` | config | build-config | `gbkt-examples/pong/build.gradle.kts` | exact |
| `gbkt-examples/simple-physics/src/main/kotlin/.../SimplePhysics.kt` | game DSL | DSL → IR → C via GBDKPipelineV2 | `gbkt-examples/pong/src/main/kotlin/.../PongV2.kt` | role-match (single-scene diff) |
| `gbkt-examples/simple-physics/src/main/resources/sprites/smiley.png` | asset | PNG → tile-data array | `gbkt-examples/pong/res/sprites/ball.png` | exact |
| `gbkt-examples/simple-physics/src/test/.../SimplePhysicsIRTest.kt` | test (IR validation) | game DSL → GameIR assertions | `gbkt-examples/pong/src/test/.../PongIRTest.kt` | exact |
| `gbkt-examples/simple-physics/src/test/.../SimplePhysicsEmissionTest.kt` | test (emission invariant) | GameIR → GBDKPipelineV2 → C text assertions | `gbkt-backend-gbdk/src/test/.../SignedComparisonLiteralEmissionTest.kt` | role-match (same pipeline, DSL-authored path) |
| `gbkt-examples/simple-physics/src/test/.../SimplePhysicsGameTest.kt` | test (simulation logic) | GameIR → SimulationContextV2 → variable assertions | `gbkt-examples/pong/src/test/.../PongGameTest.kt` | exact |
| `gbkt-examples/simple-physics/PLAYBOOK.md` | planning doc | MCP input scripts + variable assertions | `gbkt-examples/shmup/PLAYBOOK.md` | role-match |
| `.planning/phases/09-.../09-UAT.md` | planning doc | MCP harness contract | `.planning/phases/07.4-.../07.4-UAT.md` | role-match |
| `settings.gradle.kts` | config | build-config modification | lines 56-64 of existing `settings.gradle.kts` | exact (single-line append) |
| `evidence/reference/phys.c` + `BUILD.md` | planning doc | reference artifact | `.planning/phases/07.4-.../evidence/` layout | role-match |
| Source files in `gbkt-backend-gbdk` (Plan 4 named bug fix) | codegen (ExprVisitor / CEmitter) | IR Literal → CIntLiteral in signed-comparison context | `gbkt-backend-gbdk/src/test/.../SignedComparisonLiteralEmissionTest.kt` + `gbkt-backend-gbdk/CLAUDE.md §"Literal Emission Convention"` | exact (same bug class, DSL-authored path) |

---

## Pattern Assignments

### `gbkt-examples/simple-physics/build.gradle.kts` (config, build-config)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/pong/build.gradle.kts`

**Full file pattern** (lines 1-39 — copy verbatim, change 3 values):
```kotlin
/**
 * Pong - Minimal gbkt example game
 *
 * Demonstrates: entities, input, collision, variables
 */
plugins {
    kotlin("jvm")
    id("io.github.gbkt")
}

group = "io.github.gbkt.examples"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform(project(":gbkt-bom")))
    implementation(project(":gbkt-backend-gbdk"))
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))
    testImplementation(project(":gbkt-test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

gbkt {
    game("io.github.gbkt.examples.pong.PongV2Kt::pongV2")
    assets("res")
    outputName.set("pong")
}
```

**Deltas from pong:**
1. Comment header: change "Pong - Minimal gbkt example game" to "SimplePhysics - GBDK simple_physics reference port"
2. `game(...)`: change to `"io.github.gbkt.examples.simple_physics.SimplePhysicsKt::simplePhysics"`
3. `outputName.set(...)`: change to `"simple-physics"`
4. Assets dir remains `"res"` — keep as-is

---

### `gbkt-examples/simple-physics/src/main/kotlin/.../SimplePhysics.kt` (game DSL, DSL → IR)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/PongV2.kt`

**Package / imports pattern** (lines 1-11):
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.simple_physics

import io.github.gbkt.core.dsl.*
```

No `PositionDef` or `SoundPreset` import needed — simple-physics uses no `print()` calls or sound effects.

**Top-level game declaration pattern** (PongV2.kt lines 35-40):
```kotlin
@Suppress("LongMethod")
val pongV2 =
    game("Pong") {
        config {
            cartridge = "ROM_ONLY"
            romBanks = 2
        }
        // ...
    }
```

**Delta:** `val simplePhysics = game("SimplePhysics") { config { cartridge = "ROM_ONLY"; romBanks = 2 } ... }`. NO `sceneRef` forward declaration needed (single scene, no circular navigation).

**Variable declaration pattern** (PongV2.kt lines 49-52 — i8Var analog; phase uses i16Var):
```kotlin
var p1Score by u8Var(0)
var p2Score by u8Var(0)
var ballDx by i8Var(1)
var ballDy by i8Var(1)
```

**Delta — SimplePhysics uses i16Var for sub-pixel arithmetic:**
```kotlin
var posX by i16Var(1024)   // 64 * 16 = 1024 sub-pixels
var posY by i16Var(1024)
var spdX by i16Var(0)
var spdY by i16Var(0)
```
`i16Var` delegate is at `gbkt-lang/VariableBuilders.kt:403-442` (VERIFIED in RESEARCH.md).

**Actor declaration pattern** (PongV2.kt lines 82-88):
```kotlin
val ball by actor {
    position(80, 72)
    sprite(asset("sprites/ball.png")) {
        size(4, 4)
        hitbox(0, 0, 4, 4)
    }
}
```

**Delta:** replace `ball` with `smiley`, asset path with `"sprites/smiley.png"`, size with `(8, 8)`, hitbox with `(0, 0, 8, 8)`. Initial position `(64, 64)` — will be overwritten in `enter { }` via `posX set` / `posY set` + moveTo-equivalent.

**Single-scene structure pattern** (PongV2.kt lines 123-141 — game scene as closest shape to a single play scene):
```kotlin
val gameScene =
    scene("game") {
        enter {
            clear()
            showSprites()
            ball.moveTo(80, 72)
            ballDx set 1
            ballDy set 1
            p1Score set 0
            p2Score set 0
        }
        frame {
            whenever(dpad.up.held) { ... }
            whenever(dpad.down.held) { ... }
            // ball physics ...
        }
    }
start = gameScene.id
```

**Delta for SimplePhysics:**
- Scene name: `"play"` (D-06)
- `enter { }` sets `posX set 1024; posY set 1024; spdX set 0; spdY set 0; showSprites()`. NO `clear()` (no BG tilemap to corrupt).
- `frame { }` contains: input D-pad accel, clamp checks, A-press jump impulse, pos += spd, position sync (moveTo workaround), decel ladder.
- No `clear()` or `print()` in any block (no UI text per D-06 — single-scene no title).
- `start = playScene.id` replaces `start = gameScene.id`.

**Input pattern** (PongV2.kt lines 143-148):
```kotlin
whenever(dpad.up.held) {
    whenever(paddle1.y isAbove 16) { moveBy(paddle1, 0, -2) }
}
whenever(dpad.down.held) {
    whenever(paddle1.y isBelow 112) { moveBy(paddle1, 0, 2) }
}
```

**Delta for SimplePhysics frame block:**
```kotlin
// Accel + clamp (after named bug fix, isAbove N will emit signed CIntLiteral)
whenever(dpad.up.held) {
    spdY -= 2
    whenever(spdY isBelow -64) { spdY set -64 }
}
whenever(dpad.down.held) {
    spdY += 2
    whenever(spdY isAbove 64) { spdY set 64 }   // BUG CANDIDATE until Plan 4 fix
}
whenever(dpad.left.held) {
    spdX -= 2
    whenever(spdX isBelow -64) { spdX set -64 }
}
whenever(dpad.right.held) {
    spdX += 2
    whenever(spdX isAbove 64) { spdX set 64 }   // BUG CANDIDATE until Plan 4 fix
}
// Jump impulse — edge-triggered
whenever(buttons.a.pressed) { spdY set -512 }   // -32 * 16 sub-pixels
// Pos update
posX += spdX
posY += spdY
// Position sync (moveTo Expr workaround — see Pitfall 2 in RESEARCH.md)
// smiley.moveTo(posX shr 4, posY shr 4) DOES NOT COMPILE (Bug B)
// Workaround if Bug B is not the named fix:
//   smiley.x set (posX shr 4)  -- uses ActorRef property set with Expr
//   smiley.y set (posY shr 4)
// Decel toward zero (mutually exclusive conditions — no else-if needed)
whenever(spdY isBelow 0) { spdY++ }
whenever(spdY isAbove 0) { spdY-- }
whenever(spdX isBelow 0) { spdX++ }
whenever(spdX isAbove 0) { spdX-- }
```

**NOTE on Bug B workaround:** `smiley.x set (posX shr 4)` uses the `ActorPropertyRef.set(Expr)` API. Confirm during Plan 2 whether this compiles. If `smiley.x` only accepts `Int`, then `var smileySyncX by u8Var(0); smileySyncX set (posX shr 4); smiley.x set smileySyncX` is the fallback (two-step narrowing). This is a Port-time discovery per D-04 — the planner should document whichever path is chosen in the Plan 3 analysis.

---

### `gbkt-examples/simple-physics/src/test/.../SimplePhysicsIRTest.kt` (test, IR validation)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongIRTest.kt`

**Class scaffold pattern** (PongIRTest.kt lines 1-31):
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PongIRTest {

    private val ir = pongV2.build()

    @Test
    fun `has 3 scenes`() { assertEquals(3, ir.scenes.size) }

    @Test
    fun `has 3 actors`() { assertEquals(3, ir.actors.size) }

    @Test
    fun `start scene is title`() { assertEquals("title", ir.startScene) }

    @Test
    fun `has 4 variables`() { assertEquals(4, ir.variables.size) }
```

**Variable type check pattern** (PongIRTest.kt lines 53-70):
```kotlin
@Test
fun `has p1Score variable of type U8`() {
    assertTrue(ir.variables.any { it.name == "p1Score" && it.type == VarType.U8 })
}
@Test
fun `has ballDx variable of type I8`() {
    assertTrue(ir.variables.any { it.name == "ballDx" && it.type == VarType.I8 })
}
```

**Actor position check pattern** (PongIRTest.kt lines 88-99):
```kotlin
@Test
fun `ball actor has correct initial position`() {
    assertEquals(PositionDef(80, 72), ir.actors.first { it.id == "ball" }.position)
}
@Test
fun `ball actor has a sprite`() {
    assertNotNull(ir.actors.first { it.id == "ball" }.sprite)
}
```

**Deltas for SimplePhysicsIRTest:**
- `private val ir = simplePhysics.build()` (references the top-level `simplePhysics` DSL val)
- `has 1 scene` (only `play` — D-06)
- `has 1 actor` (only `smiley`)
- `start scene is play`
- `has 4 variables` (posX, posY, spdX, spdY — all `VarType.I16`)
- Variable type checks: `it.type == VarType.I16` for all four
- Actor position: `assertEquals(PositionDef(64, 64), ir.actors.first { it.id == "smiley" }.position)`
- No `RPG systems in output` test is still valid (copy)
- No `has sound effects` test (simple-physics has no sound)
- `play scene has frame ops` and `play scene has enter ops`

---

### `gbkt-examples/simple-physics/src/test/.../SimplePhysicsEmissionTest.kt` (test, emission invariant)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SignedComparisonLiteralEmissionTest.kt`

**Class scaffold + evidence pattern** (SignedComparisonLiteralEmissionTest.kt lines 53-74):
```kotlin
class SignedComparisonLiteralEmissionTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    companion object {
        val EVIDENCE_DIR = File(
            "/Users/michalsvacha/GitHub/personal/gbkt/" +
                ".planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/" +
                "evidence/tier1-shape"
        )
    }

    private fun emitCameraBody(system: CameraSystem): String {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val functions = visitor.visitCameraSystem(system)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }
```

**Key pattern: pipeline-level emission helper** (PongPipelineTest.kt lines 240-244):
```kotlin
class PongPipelineTest {

    private val pipeline = GBDKPipelineV2()
    private val pipelineOutput by lazy { pipeline.generate(pongGameIR) }
    private val output by lazy { pipelineOutput.files }
```

**Key pattern: per-function body extraction** (CLAUDE.md + SignedComparisonLiteralEmissionTest conceptual shape):

The `SimplePhysicsEmissionTest` should use `GBDKPipelineV2` to run the full pipeline on the `simplePhysics.build()` IR, then extract the `play_frame` function body from the generated `bank1.c` using a brace-walk helper. The CLAUDE.md "Scope-level grep gates" corollary mandates per-function scope extraction.

**Brace-walk function body extractor pattern** (as described in CLAUDE.md §"Scope-level grep gates"):
```kotlin
/**
 * Extract a C function body by brace-walking.
 * Finds the line containing `void functionName(`, then collects lines
 * until brace count reaches zero (matching the opening `{`).
 */
fun extractFunctionBody(cSource: String, functionName: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.contains("void ${functionName}(") }
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

**Three D-11 emission invariants (one per D-01 behavior):**

**Invariant D-11.1 — Accel clamp: signed upper-bound comparison emits without `u` suffix** (after Plan 4 named bug fix — this test is RED before fix, GREEN after):
```kotlin
@Test
fun `accel clamp emission - spdX upper bound uses signed comparison`() {
    val pipeline = GBDKPipelineV2()
    val output = pipeline.generate(simplePhysics.build()).files
    val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
    val frameBody = extractFunctionBody(bank1C, "play_frame")

    // After named bug fix: positive literal in signed comparison emits without u suffix
    assertTrue(
        frameBody.contains("_spdX > 64"),
        "Expected signed upper-clamp `_spdX > 64` in play_frame body"
    )
    assertFalse(
        frameBody.contains("_spdX > 64u"),
        "Must NOT emit unsigned `_spdX > 64u` — triggers SDCC warning 94 (always false)"
    )
}
```

**Invariant D-11.2 — Jump impulse: edge-detect (pressed, not held) + immediate -512 assignment:**
```kotlin
@Test
fun `jump impulse emission - A pressed emits edge-detect and immediate spdY assignment`() {
    val pipeline = GBDKPipelineV2()
    val output = pipeline.generate(simplePhysics.build()).files
    val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
    val frameBody = extractFunctionBody(bank1C, "play_frame")

    // Edge-detect: joypad_pressed (not joypad_held) bit check
    assertTrue(
        frameBody.contains("joypad_pressed"),
        "Expected joypad_pressed (edge-detect) for A button jump"
    )
    // Immediate impulse: -512 in signed context (CIntLiteral — no u suffix)
    assertTrue(
        frameBody.contains("-512"),
        "Expected signed literal -512 (sub-pixel jump impulse) in play_frame body"
    )
    assertFalse(
        frameBody.contains("-512u"),
        "Must NOT emit unsigned -512u for signed spdY assignment"
    )
}
```

**Invariant D-11.3 — Decel to zero: negative-speed decel branch emits signed comparison:**
```kotlin
@Test
fun `decel to zero emission - spdX negative branch uses signed comparison`() {
    val pipeline = GBDKPipelineV2()
    val output = pipeline.generate(simplePhysics.build()).files
    val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
    val frameBody = extractFunctionBody(bank1C, "play_frame")

    // Decel-up (positive): _spdX > 0 (signed, no u suffix after fix)
    // Decel-down (negative): _spdX < 0 (negative literal — already correct per 07.9)
    assertTrue(
        frameBody.contains("_spdX < 0"),
        "Expected signed `_spdX < 0` in decel branch (negative-speed case)"
    )
    assertFalse(
        frameBody.contains("_spdX < 0u"),
        "Must NOT emit unsigned `_spdX < 0u` in decel branch"
    )
}
```

**Imports required:**
```kotlin
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
```

**Evidence pattern** (mirror SignedComparisonLiteralEmissionTest.kt lines 58-63):
```kotlin
companion object {
    val EVIDENCE_DIR = File(
        "/Users/michalsvacha/GitHub/personal/gbkt/" +
            ".planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/" +
            "evidence/tier1-shape"
    )
}
```

Write evidence file before each assertion fires (mirrors `evidenceFile.writeText(out.toString())` before `assertTrue`).

**Deltas from SignedComparisonLiteralEmissionTest:**
- Uses the DSL game (`simplePhysics.build()`) instead of a hand-built IR fixture — because D-11 tests the DSL-authored path (bucket-b gap from Phase 07.9 audit).
- Imports `io.github.gbkt.examples.simple_physics.simplePhysics` instead of constructing a `GameIR`.
- Applies `extractFunctionBody(bank1C, "play_frame")` brace-walk before grepping — not file-level grep.
- Tests are RED before Plan 4 named bug fix (positive-literal clamp assertions fail), GREEN after.

---

### `gbkt-examples/simple-physics/src/test/.../SimplePhysicsGameTest.kt` (test, simulation logic)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongGameTest.kt`

**Class scaffold + SimulationContextV2 pattern** (PongGameTest.kt lines 1-50):
```kotlin
package io.github.gbkt.examples.pong

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PongGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = pongV2.build()
    }

    @Test
    fun `ball bounces off top wall - ballDy reverses to positive when ball y below 16`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("game")

        sim.setVar("ball.x", 80)
        sim.setVar("ball.y", 10)
        sim.setVar("ballDy", -1)

        sim.advanceFrames(1)

        sim.assertVar("ballDy", 1)
    }
```

**Scoring test pattern** (PongGameTest.kt lines 76-101):
```kotlin
@Test
fun `p1Score increments when ball exits right side`() {
    val sim = SimulationContextV2(ir)
    sim.enterScene("game")
    sim.enableTracing()

    sim.setVar("ball.x", 157)
    sim.setVar("ballDx", 1)

    val scoreBefore = sim.getVar("p1Score")
    sim.advanceFrames(1)
    val scoreAfter = sim.getVar("p1Score")
    assertTrue(scoreAfter > scoreBefore, ...)
    assertEquals(80, sim.getVar("ball.x"))
}
```

**Deltas for SimplePhysicsGameTest — 3 scenarios matching D-01 behaviors:**

**Scenario 1 — D-pad held → speed accelerates toward clamp:**
```kotlin
@Test
fun `D-pad right held - spdX increases each frame`() {
    val sim = SimulationContextV2(ir)
    sim.enterScene("play")

    sim.setVar("spdX", 0)
    sim.setVar("spdY", 0)

    sim.advanceFrames(1, heldButtons = setOf("right"))  // spdX += 2 per frame

    val spdXAfter = sim.getVar("spdX")
    assertTrue(spdXAfter > 0, "spdX should increase after holding right")
}
```

**Scenario 2 — A pressed → spdY jumps to -512:**
```kotlin
@Test
fun `A pressed - spdY set to negative jump impulse`() {
    val sim = SimulationContextV2(ir)
    sim.enterScene("play")
    sim.setVar("spdY", 0)

    sim.advanceFrames(1, pressedButtons = setOf("a"))  // edge-detect

    sim.assertVar("spdY", -512)
}
```

**Scenario 3 — No input → speed decelerates toward zero:**
```kotlin
@Test
fun `no input - positive spdX decelerates toward zero`() {
    val sim = SimulationContextV2(ir)
    sim.enterScene("play")
    sim.setVar("spdX", 10)  // pre-load speed

    sim.advanceFrames(1)  // no buttons

    val spdXAfter = sim.getVar("spdX")
    assertTrue(spdXAfter < 10, "spdX should decrease (decel toward zero)")
}
```

**Import change:** `simplePhysics` replaces `pongV2`.
**NOTE:** SimulationContextV2 support for `heldButtons` / `pressedButtons` per `advanceFrames` should be confirmed against the actual API signature in `gbkt-test/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt` at Plan time. If the API differs, use `sim.setVar("joypad_held", ...)` or `sim.setVar("joypad_pressed", ...)` directly per the PongPipelineTest.kt inline IR pattern (`VarRef("joypad_held")`, `VarRef("joypad_pressed")`).

---

### `gbkt-examples/simple-physics/PLAYBOOK.md` (planning doc, MCP harness)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/shmup/PLAYBOOK.md`

**Format pattern** (shmup PLAYBOOK.md — all sections):
```markdown
# Shmup

## Overview
A vertical scrolling shoot-em-up. ...

## How to Play
...

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start the game |
| gameplay | UP | Move ship up (2px/frame, min y=8) |
...

## Scene Flow
- title -> gameplay (press START)
...

## Win / Lose Conditions
...

## Known Quirks
...

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| score | UINT8 | score | Points scored; +10 per enemy destroyed |
...
```

**Deltas for SimplePhysics PLAYBOOK.md:**
- Overview: sub-pixel physics demo ported from GBDK simple_physics reference.
- Controls table: 5 rows (UP, DOWN, LEFT, RIGHT accel; A jump impulse). Scene column: all `play`.
- Scene Flow: single entry `play (only scene — no navigation)`.
- Win/Lose Conditions: no win or lose conditions — infinite demo.
- Known Quirks: document `++spdY` DSL emission shape (assignment form, not increment form); document single-frame 8x8 PNG (no animation cycling despite reference having 4 frames).
- Variables Reference: posX, posY (INT16, position, sub-pixel 12.4), spdX, spdY (INT16, velocity, range -64..64 sub-pixels/frame).
- ADD: MCP input scripts section (3 behavior scripts from RESEARCH.md §"Proposed 3 input scripts") — this is Phase 9-specific, not in shmup PLAYBOOK.

**MCP input scripts pattern is unique to Phase 9** — no direct analog. RESEARCH.md §"MCP UAT Harness Format" provides the content directly.

---

### `.planning/phases/09-.../09-UAT.md` (planning doc, MCP harness contract)

**Analog:** `/Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md`

**YAML frontmatter + status pattern** (07.4-UAT.md lines 1-8):
```yaml
---
status: diagnosed
phase: 07.4-sport-genre-codegen-fix-inserted
source: [07.4-VERIFICATION.md, 07.4-18-SUMMARY.md, ...]
started: 2026-05-10T00:00:00Z
updated: 2026-05-10T00:06:00Z
---
```

**Current Test + Diagnosis + Tests structure** (07.4-UAT.md):
```markdown
## Current Test
<!-- OVERWRITE each test - shows where we are -->

## Diagnosis Summary

## Tests

### 1. Test Name
expected: ...
result: pending
```

**Deltas for 09-UAT.md:**
- `status: pending` (initial)
- `phase: 09-port-simple-physics-gbdk-example-to-gbkt`
- Three tests mapping exactly to D-01 behaviors:
  - `### 1. D-pad held → sprite accelerates and clamps at max speed` (Behavior 1)
  - `### 2. A pressed → instant Y impulse (jump)` (Behavior 2)
  - `### 3. D-pad released → sprite decelerates to rest` (Behavior 3)
- Each test block contains: `expected:`, `result: pending`, `evidence: (MCP screenshot path)`, `mcp_script:` block from RESEARCH.md §"Proposed 3 input scripts".
- Visual evidence rule annotation: `## Visual Evidence Rule / Each test MUST include emulator_screenshot at climax frame`.

---

### `settings.gradle.kts` (config modification — single-line append)

**Analog:** Lines 56-64 of `/Users/michalsvacha/GitHub/personal/gbkt/settings.gradle.kts`

**Existing example includes pattern** (settings.gradle.kts lines 56-64):
```kotlin
// Example games
include("gbkt-examples:pong")
include("gbkt-examples:breakout")
include("gbkt-examples:explorer")
include("gbkt-examples:rpg-lite")
include("gbkt-examples:dungeon")
include("gbkt-examples:platformer")
include("gbkt-examples:platformer-gbc")
include("gbkt-examples:shmup")
include("gbkt-examples:racer")
```

**Delta:** Append `include("gbkt-examples:simple-physics")` after the last `include("gbkt-examples:racer")` line. Single-line change.

---

### `evidence/reference/phys.c` + `evidence/reference/BUILD.md` (planning docs)

**No code analog** — these are evidence artifacts.

- `phys.c`: verbatim copy of `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c` (99 lines, already read).
- `BUILD.md`: documents the reference ROM build: `cd /Users/michalsvacha/gbdk/examples/cross-platform/simple_physics && GBDK_HOME=/Users/michalsvacha/gbdk make gb`. Output: `build/gb/physics.gb` (32768 bytes; actual code 574 bytes). Binaries are gitignored.

---

### Plan 4 — Named Bug Fix: Source Files in `gbkt-backend-gbdk` (codegen, visitor pattern)

**Analog:** Phase 07.9 fix (same bug class — DSL-authored path not covered by 07.9 audit):
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SignedComparisonLiteralEmissionTest.kt` (test pattern)
- `gbkt-backend-gbdk/CLAUDE.md §"Literal Emission Convention"` (fix contract)

**Fix shape (from RESEARCH.md §"Bug A" and CONTEXT.md §"Code Insights"):**

Bug A (positive-literal signed comparison): `ExprVisitor.visitLiteral` emits all `Literal(N)` as `CLiteral(N)`, which for `N >= 0` emits `Nu`. When a DSL-authored `whenever(signedVar isAbove 64)` is lowered, the RHS becomes `CLiteral(64)` → `64u`. Fix: in `ExprVisitor.visitBinaryExpr`, when the operator is a comparison (`<`, `>`, `<=`, `>=`, `==`, `!=`) and the LHS is a known signed variable (type `I8` or `I16`), emit the RHS `Literal(N)` as `CIntLiteral(N)` instead of `CLiteral(N)`.

**Literal emission convention** (gbkt-backend-gbdk/CLAUDE.md):
- `CLiteral(value)` — unsigned context, emits `Nu` when `N >= 0`
- `CIntLiteral(value)` — signed-safe, emits bare `N` with NO `u` suffix
- Rule 1: Signed-context comparison RHS MUST use `CIntLiteral(N)`.
- Rule 2: Unsigned-context literals stay as `CLiteral(N)`.

**RED test pattern before fix** (mirrors SignedComparisonLiteralEmissionTest test structure):
The `SimplePhysicsEmissionTest.kt` D-11.1 test (`accel clamp emission`) IS the RED test. It asserts `assertFalse(body.contains("_spdX > 64u"))` — which FAILS at HEAD (before fix) because `_spdX > 64u` IS present. After the Plan 4 fix, the test goes GREEN.

**Files likely modified in Plan 4:**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt` — add signed-context awareness to `visitBinaryExpr` or `visitLiteral` when in comparison context.
- Possibly `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt` if the fix is at emission rather than visitor level.

**Phase 07.9 plan structure analog:** Plans 01 (RED test) → 02 (fix + GREEN) → 03 (audit + regression guard). Phase 9 compresses this into Plan 4: RED established by SimplePhysicsEmissionTest D-11.1, fix applied, GREEN verified. The audit scope is narrower (one visitor path, not six).

---

## Shared Patterns

### File Header (MPL License Block)
**Source:** Every existing source file (e.g., PongIRTest.kt lines 1-6, PongGameTest.kt lines 1-6)
**Apply to:** `SimplePhysics.kt`, `SimplePhysicsIRTest.kt`, `SimplePhysicsEmissionTest.kt`, `SimplePhysicsGameTest.kt`
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

### `@Suppress("LongMethod")` on main DSL val
**Source:** PongV2.kt line 34
**Apply to:** `SimplePhysics.kt` top-level `val simplePhysics` — the frame block physics logic will likely trigger LongMethod.

### Evidence-before-assert pattern
**Source:** `PlatformerJumpCancelAndFrictionProbe.kt` lines 151-153; `SignedComparisonLiteralEmissionTest.kt` lines 159-160
**Apply to:** `SimplePhysicsEmissionTest.kt` — write evidence file to disk BEFORE `assertTrue`/`assertFalse` assertions fire, so evidence lands even when test is RED.
```kotlin
// Flush evidence BEFORE assertions so file lands on disk even when RED
val evidenceFile = File(EVIDENCE_DIR, "01-accel-clamp-upper-bound.txt")
evidenceFile.writeText(frameBody)

// Then assertions
assertFalse(frameBody.contains("_spdX > 64u"), ...)
```

### GameIR shared fixture (companion object pattern)
**Source:** PongGameTest.kt lines 27-30; ShmupGameTest.kt lines 26-29
**Apply to:** `SimplePhysicsGameTest.kt` — build IR once per class:
```kotlin
companion object {
    private val ir: GameIR = simplePhysics.build()
}
```

### Visual Evidence Rule annotation
**Source:** `CLAUDE.md §"Verification Methodology — Visual Evidence Rule"`
**Apply to:** `09-UAT.md` header and each test block. Variable assertions alone do not satisfy any SC whose phrasing is "sprite is visible at position X". Each D-01 behavior's UAT block MUST include `emulator_screenshot` call and path.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `evidence/reference/BUILD.md` | planning doc | reference build instructions | No comparable "reference ROM build doc" exists in the codebase — first reference-port phase establishes this pattern |

---

## Metadata

**Analog search scope:** `gbkt-examples/pong/`, `gbkt-examples/shmup/`, `gbkt-backend-gbdk/src/test/`, `gbkt-examples/platformer/src/test/`, `.planning/phases/07.4-*/`, `.planning/phases/07.9-*/`, root `settings.gradle.kts`
**Files scanned:** 18 source files read directly; 5 additional files via Grep/Bash
**Pattern extraction date:** 2026-05-13
