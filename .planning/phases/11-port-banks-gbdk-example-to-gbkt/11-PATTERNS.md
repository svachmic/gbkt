# Phase 11: Port banks GBDK example to gbkt — Pattern Map

**Mapped:** 2026-05-19
**Files analyzed:** 10 new/modified files
**Analogs found:** 10 / 10

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `gbkt-examples/banks/build.gradle.kts` | config | request-response | `gbkt-examples/simple-physics/build.gradle.kts` | exact |
| `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` | game-DSL | event-driven | `gbkt-examples/dungeon/src/main/kotlin/.../Dungeon.kt` | role-match (downsize: no RPG, no exploration system, same zone+saveData shape) |
| `gbkt-examples/banks/src/test/kotlin/.../BanksIRTest.kt` | test | transform | `gbkt-examples/dungeon/src/test/kotlin/.../DungeonIRTest.kt` | exact |
| `gbkt-examples/banks/src/test/kotlin/.../BanksEmissionTest.kt` | test | transform | `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt` | exact |
| `gbkt-examples/banks/src/test/kotlin/.../BanksUatTest.kt` | test | event-driven | `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsUatTest.kt` | exact |
| `gbkt-examples/banks/11-UAT.md` | doc/contract | — | `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md` | exact |
| `gbkt-examples/banks/PLAYBOOK.md` | doc/playbook | — | `gbkt-examples/simple-physics/PLAYBOOK.md` | exact |
| `gbkt-examples/banks/res/tiles/checker.png` | asset | file-I/O | `gbkt-examples/simple-physics/res/sprites/ball.png` (concept only) | partial |
| `settings.gradle.kts` (modified) | config | — | existing `settings.gradle.kts` lines 56–67 | exact |
| `GBDKSystemVisitor.kt` bug-fix: add `trigger_saves()` stub in `visitSaveSystem()` | service | request-response | `GBDKSystemVisitor.kt:2616-2631` (visitGenericSystem else-branch stub pattern) | role-match |

---

## Pattern Assignments

### `gbkt-examples/banks/build.gradle.kts` (config, request-response)

**Analog:** `gbkt-examples/simple-physics/build.gradle.kts`

**What to copy:** The entire file structure verbatim — plugins block, group/version, repositories, dependencies (no genre packages needed), kotlin jvmToolchain, tasks.test useJUnitPlatform, gbkt block.

**What to change:**
- Top-level KDoc comment: "Banks - GBDK banks example port" + "Demonstrates: ROM banking, BANKED calling convention, MBC5+RAM+BATT, SRAM persistence via SaveDataBuilder"
- `gbkt { game("io.github.gbkt.examples.banks.BanksKt::banks"); assets("res"); outputName.set("banks"); ramBanks.set(2) }`
- No genre package dependency (remove none — `simple-physics` already has none)

**Imports pattern** (`simple-physics/build.gradle.kts` lines 1–39):
```kotlin
/**
 * Banks - GBDK banks reference port
 *
 * Demonstrates: multi-bank ROM (MBC5_RAM_BATTERY), BANKED calling convention,
 * cross-bank scene navigation, SRAM persistence via SaveDataBuilder.
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

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }

gbkt {
    game("io.github.gbkt.examples.banks.BanksKt::banks")
    assets("res")
    outputName.set("banks")
    ramBanks.set(2)  // CRITICAL: must be here, not only in config { }; two-channel wiring
}
```

**Critical note:** `ramBanks.set(2)` is required in the Gradle `gbkt { }` block because `GenerateCTask.writeBuildMetadata()` does NOT propagate `CartridgeConfig.ramBanks` to `gbkt-build.properties`. `CompileRomTask` reads `ramBanks` from `GbktExtension.ramBanks` only. Without this, lcc does not receive `-Wl-ya2`.

---

### `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` (game-DSL, event-driven)

**Analog:** `gbkt-examples/dungeon/src/main/kotlin/.../Dungeon.kt` (lines 1–80 for header + config + zone + saveData shape)

**What to copy:** File header (MPL 2.0), package declaration, `import io.github.gbkt.core.dsl.*`, top-level `@Suppress("LongMethod")` if needed, `game("Banks") { }` outer block, `config { }` block shape, `saveData()` DSL call, `zone()` DSL call, `val ...Ref = sceneRef(...)` forward-declaration idiom for circular navigation, scene lifecycle shape (`enter { } frame { } exit { }`).

**What to change:**
- No RPG imports (`io.github.gbkt.rpg.dsl.*`) — `Banks.kt` uses only `io.github.gbkt.core.dsl.*`
- No actors, no sound effects, no HUD, no exploration system, no flags, no camera
- `cartridge = "MBC5_RAM_BATTERY"` (not `"MBC5"`) to get ROM byte `0x1B` matching reference `-Wl-yt0x1B`
- `romBanks = 4` (HOME + bank1 scenes + bank2 zone + margin; RESEARCH §BankingAnalysisPass confirmed FFD packs all 3 scenes into bank 1)
- `ramBanks = 2` (SRAM for SaveDataBuilder slots)
- 3 scenes: `title`, `play`, `pause` with minimal frame ops (D-claude-2)
- 1 zone in play scene via `zone("play_zone") { ... }` (D-05)
- 1 save slot via `saveData("saves") { slots(2) }` (D-06); `triggerSystem("saves")` requires the named bug fix

**Config + saveData + zone pattern** (from `Dungeon.kt` lines 40–111):
```kotlin
val banks = game("Banks") {
    config {
        cartridge = "MBC5_RAM_BATTERY"   // 0x1B = MBC5+RAM+BATT; matches reference Makefile -Wl-yt0x1B
        romBanks = 4                     // HOME(0) + scenes(1) + zone(2) + margin(3)
        ramBanks = 2                     // SRAM for SaveDataBuilder; also set ramBanks.set(2) in build.gradle.kts
    }

    // Forward-declare scene refs for circular navigation
    val titleRef = sceneRef("title")

    // State variable — must be non-transient to be included in SRAM save
    var saveFlag by u8Var(0)

    // SaveDataBuilder — 2 slots; slotSize = 1 (saveFlag) + 1 (sentinel) = 2 bytes
    saveData("saves") { slots(2) }

    // Zone for cross-bank tilemap load (allocateZoneBanks places this in bank 2)
    val playZone by zone("play_zone") {
        // ... tileset + minimal tiles (8x8 or 16x16 checkerboard PNG)
    }

    // Scenes defined in reverse navigation order (pause before play before title)
    val pauseScene = scene("pause") {
        enter { clear() }
        frame { whenever(buttons.start.pressed) { navigate("play") } }
    }

    scene("play") {
        enter { showSprites() }
        frame {
            whenever(buttons.select.pressed) { triggerSystem("saves") }  // anchor 4; needs bug fix
            whenever(buttons.start.pressed) { navigate(pauseScene) }
        }
    }

    scene("title") {
        enter { clear() }
        frame { whenever(buttons.start.pressed) { navigate("play") } }
    }

    start = "title"
}
```

**Anti-pattern guard:** Do NOT use `cartridge = "MBC5"` expecting `0x1B` — `"MBC5"` maps to `0x19`. Use `"MBC5_RAM_BATTERY"` for `0x1B` (verified in `GenerateCTask.kt:673-674`).

---

### `gbkt-examples/banks/src/test/kotlin/.../BanksIRTest.kt` (test, transform)

**Analog:** `gbkt-examples/dungeon/src/test/kotlin/.../DungeonIRTest.kt`

**What to copy:** File header (MPL 2.0), package declaration, imports (`io.github.gbkt.core.ir.SaveSystem`, `VarType`, `kotlin.test.*`), class structure (`class BanksIRTest { private val ir = banks.build() }`), individual `@Test fun` naming style ("has N scenes", "start scene is X", "has N variables", "has zone definitions", "has save system"), `ir.scenes.size`, `ir.startScene`, `ir.variables.size`, `ir.zones.isNotEmpty()`, `ir.systems.any { it is SaveSystem }` pattern.

**What to change:**
- No RPG-specific imports (`CombatEngineSystem`, `ExplorationSystem`, etc.)
- No actors test (Banks.kt has no actors)
- No flags test (Banks.kt has no flags system)
- No sound effects test
- Test values: 3 scenes, startScene = "title", 1 variable (saveFlag U8), 1 zone, 1 SaveSystem
- Add: `ir.systems.any { it is SaveSystem }` → TRUE
- Add: zone id = "play_zone" check

**Core IR test pattern** (from `DungeonIRTest.kt` lines 41–130, condensed):
```kotlin
package io.github.gbkt.examples.banks

import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BanksIRTest {
    private val ir = banks.build()

    @Test fun `has 3 scenes`() { assertEquals(3, ir.scenes.size) }
    @Test fun `start scene is title`() { assertEquals("title", ir.startScene) }
    @Test fun `has 1 variable`() { assertEquals(1, ir.variables.size) }
    @Test fun `saveFlag is U8`() {
        assertTrue(ir.variables.any { it.name == "saveFlag" && it.type == VarType.U8 })
    }
    @Test fun `has zone definitions`() { assertTrue(ir.zones.isNotEmpty()) }
    @Test fun `has play_zone zone`() {
        assertTrue(ir.zones.any { it.id == "play_zone" })
    }
    @Test fun `has save system`() {
        assertTrue(ir.systems.any { it is SaveSystem })
    }
    @Test fun `scenes include title play pause`() {
        val ids = ir.scenes.map { it.id }.toSet()
        assertTrue(ids.contains("title"))
        assertTrue(ids.contains("play"))
        assertTrue(ids.contains("pause"))
    }
}
```

---

### `gbkt-examples/banks/src/test/kotlin/.../BanksEmissionTest.kt` (test, transform)

**Analog:** `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt`

**What to copy verbatim:** The `extractFunctionBody(cSource, functionName)` helper (lines 82–102) — this is the brace-walk implementation required by CLAUDE.md §"Scope-level grep gates". Copy the entire method without modification; it is the locking pattern for per-function emission assertions.

**What to change:**
- Package: `io.github.gbkt.examples.banks`
- `EVIDENCE_DIR`: resolve to `../../.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape`
- Replace 3 `@Test` methods with 4 invariant tests (one per UAT anchor), each using `extractFunctionBody`
- Use `banks.build()` not `simplePhysics.build()`
- Test class name: `BanksEmissionTest`

**`extractFunctionBody` helper** (copy verbatim from `SimplePhysicsEmissionTest.kt` lines 82–102):
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

**4 emission invariant tests** (compose using the helper and RESEARCH §JVM-Tier Brace-Walk Pattern):
```kotlin
// INV-1: play_enter / play_frame / play_exit all carry BANKED in bank1.c
@Test
fun `INV-1 play scene functions carry BANKED keyword in bank1`() {
    val pipeline = GBDKPipelineV2()
    val output = pipeline.generate(banks.build())
    val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")
    EVIDENCE_DIR.mkdirs()
    File(EVIDENCE_DIR, "inv1-bank1.txt").writeText(bank1C)

    assertTrue(extractFunctionBody(bank1C, "play_enter").contains(" BANKED"),
        "play_enter must have BANKED keyword")
    assertTrue(extractFunctionBody(bank1C, "play_frame").contains(" BANKED"),
        "play_frame must have BANKED keyword")
    assertTrue(extractFunctionBody(bank1C, "play_exit").contains(" BANKED"),
        "play_exit must have BANKED keyword")
}

// INV-2: _bkg_tiles_load_banked wrapper in main.c contains SWITCH_ROM sequence
@Test
fun `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence`() { ... }

// INV-3: gbkt-build.properties carries mbcType=0x1B
@Test
fun `INV-3 gbkt-build.properties carries mbcType 0x1B`() { ... }

// INV-4: save_game_saves in main.c has ENABLE_RAM + sram write + DISABLE_RAM
@Test
fun `INV-4 save_game_saves in main_c emits ENABLE_RAM and DISABLE_RAM`() { ... }
```

**Evidence-before-assert pattern** (copy from `SimplePhysicsEmissionTest.kt`): Write the function body to `EVIDENCE_DIR` via `File(EVIDENCE_DIR, "invN-*.txt").writeText(...)` BEFORE the `assertTrue`/`assertFalse` calls fire.

---

### `gbkt-examples/banks/src/test/kotlin/.../BanksUatTest.kt` (test, event-driven)

**Analog:** `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsUatTest.kt`

**What to copy:** File header (MPL 2.0), package declaration, companion object with `EVIDENCE_DIR`, `ROM_FILE`, `METADATA_FILE`, `newAgent()` with `Assumptions.assumeTrue(ROM_FILE.exists(), ...)`, `captureAndRename()` helper, `agent.use { }` pattern, `StepAgent` + `AgentSessionConfig.discoverFiles()` + `GameMetadata.fromJsonFile()` wiring, `agent.stepN(10)` boot idiom.

**What to change:**
- Package: `io.github.gbkt.examples.banks`
- `ROM_FILE = File("build/gbkt/output/banks.gb")`
- `METADATA_FILE = File("build/gbkt/generated/game_metadata.json")`
- `EVIDENCE_DIR` points to `11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots`
- 3 `@Test` methods covering UAT anchors 1, 2, and 4 (anchor 3 is a file-read step in the plan, not a JVM test)
- No `resolveI16Address` / `readI16` helpers (Banks.kt uses U8 variables, not I16)
- Anchor 1: `agent.step(setOf(Button.START))` from title → assert scene transitions to "play" + `captureAndRename`
- Anchor 2: assert tilemap visible after play_enter via screenshot (within same session as anchor 1)
- Anchor 4: `emulator_read_memory(0xA000, 4)` after save trigger → `emulator_save_state` → `emulator_load_state` → re-read

**newAgent / ROM-skip pattern** (copy from `SimplePhysicsUatTest.kt` lines 55–67):
```kotlin
private fun newAgent(): StepAgent {
    Assumptions.assumeTrue(
        ROM_FILE.exists(),
        "banks.gb not found — run buildRom first",
    )
    EVIDENCE_DIR.mkdirs()
    val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
    val metadata =
        if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
    val agent = StepAgent(baseConfig, metadata)
    agent.start()
    return agent
}
```

**SRAM save-state reboot idiom** (from RESEARCH §Pitfall 3 — do NOT use `emulator_stop` + `emulator_start`):
```kotlin
// Anchor 4 — SRAM persistence via GBST save-state round-trip
// Coffee-GB uses MemoryBattery (in-memory); SavestateManager captures WRAM/OAM/HRAM
// NOT SRAM (0xA000-0xBFFF). Use save_state/load_state as the "reboot" substitute.
val pre = agent.readMemory(0xA000)
agent.saveState(File(EVIDENCE_DIR, "anchor4-pre-reboot.gbst"))
agent.loadState(File(EVIDENCE_DIR, "anchor4-pre-reboot.gbst"))
val post = agent.readMemory(0xA000)
assertEquals(pre, post, "SRAM byte must match after GBST round-trip")
```

---

### `gbkt-examples/banks/11-UAT.md` (doc/contract)

**Analog:** `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md`

**What to copy:** Front-matter block (`status`, `phase`, `source`, `started`, `updated`), `## Visual Evidence Rule` section (verbatim quote from CLAUDE.md), `## Current Test` section with `<!-- OVERWRITE each test -->` comment, `## Tests` section structure with numbered behaviors, `mcp_script:` code block format, `result:`/`expected:`/`actual:`/`evidence:`/`plan:` fields per behavior.

**What to change:**
- `phase: 11-port-banks-gbdk-example-to-gbkt`
- `status: draft` (UAT not yet executed)
- 4 behaviors instead of 3 (D-09 one-time exception)
- Behavior 1: scene nav HOME→bank trampoline (visual; screenshot required)
- Behavior 2: zone tilemap load (visual; screenshot required)
- Behavior 3: MBC5 cartridge byte 0x0147 = 0x1B (mechanism; variable evidence only; ROM file read)
- Behavior 4: SRAM save persistence via GBST round-trip (mechanism; variable evidence only)
- Evidence dir: `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/`
- Anti-overfitting note references Phase 11 D-overfitting-1/2/3

**Front-matter + visual evidence header pattern** (from `10-UAT.md` lines 1–33):
```markdown
---
status: draft
phase: 11-port-banks-gbdk-example-to-gbkt
source: [11-CONTEXT.md, 11-RESEARCH.md, 11-PATTERNS.md]
started: 2026-05-19
updated: 2026-05-19
---

## Visual Evidence Rule

> For verification truths shaped "X is visible on screen", evidence MUST include a
> runtime screenshot, NOT just a variable-state assertion.

(Quoted verbatim from `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule".)

Every test below MUST end with an `emulator_screenshot` call at the climax frame.
Screenshots are written to:

```
.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/{behavior-slug}.png
```
```

**4-anchor mcp_script skeleton** (from RESEARCH §Validation Architecture Tier-3):
```
# Anchor 1 — cross-bank scene nav (HOME→bank-1 BANKED trampoline)
emulator_start(game="banks")
emulator_step(frames=10)
emulator_wait_for_scene(scene="title")
emulator_step(frames=1, buttons=["start"])
emulator_wait_for_scene(scene="play", timeout_frames=60)
emulator_screenshot(path=".../evidence/uat-screenshots/anchor1-play-scene.png")
emulator_assert([{type:"variable_equals", name:"_current_scene", expected:<play_scene_id>}])

# Anchor 2 — cross-bank zone tilemap load (SWITCH_ROM-from-HOME wrapper)
emulator_screenshot(path=".../evidence/uat-screenshots/anchor2-tilemap.png")

# Anchor 3 — MBC5 cartridge byte 0x0147 (ROM file read, no screenshot needed)
# python3 -c "f=open('banks.gb','rb'); f.seek(0x147); print(hex(f.read(1)[0]))"
# expect: 0x1b

# Anchor 4 — SRAM persistence via GBST round-trip (no screenshot needed)
emulator_step(frames=1, buttons=["select"])     # trigger save
emulator_read_memory("0xA000", 4)               # read SRAM bytes (expect non-zero after save)
emulator_save_state("anchor4-pre-reboot")
emulator_load_state("anchor4-pre-reboot")
emulator_read_memory("0xA000", 4)               # must match pre-reboot bytes
```

---

### `gbkt-examples/banks/PLAYBOOK.md` (doc/playbook)

**Analog:** `gbkt-examples/simple-physics/PLAYBOOK.md`

**What to copy:** Document structure — `## Overview`, `## How to Play`, `## Controls` table, `## Scene Flow`, `## Win / Lose Conditions`, `## Known Quirks`, `## Variables Reference` table, `## MCP Input Scripts` section with behavior-keyed code blocks.

**What to change:**
- Title: `# Banks`
- Overview: "Multi-bank ROM banking demo. 3 scenes (title/play/pause) over a multi-bank ROM. Cross-bank scene navigation, banked zone tilemap load, MBC5+RAM+BATT cartridge, SRAM save slot."
- Controls: Start (title→play), Select (play: trigger save), Start (play→pause), Start (pause→play)
- Scene Flow: title → play → pause → play; no gameover
- MCP Input Scripts: 4 scripts (anchors 1–4), keyed to UAT anchor IDs
- `## Variables Reference`: saveFlag (UINT8, initial=0)
- `## Known Quirks`: triggerSystem("saves") requires named bug fix (trigger_saves stub); SRAM persists within session and via GBST round-trip, not via emulator_stop/start

**Controls table pattern** (from `simple-physics/PLAYBOOK.md` lines 19–26):
```markdown
## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START  | Navigate to play scene (cross-bank trampoline: anchor 1) |
| play  | SELECT | Trigger save slot 0 (SRAM write: anchor 4) |
| play  | START  | Navigate to pause scene |
| pause | START  | Navigate back to play scene |
```

---

### `gbkt-examples/banks/res/tiles/checker.png` (asset, file-I/O)

**Analog:** `gbkt-examples/simple-physics/res/sprites/ball.png` (concept only — small PNG asset in `res/` subdir)

**What to create:** A minimal 8×8 or 16×16 PNG representing a 2-tile checkerboard pattern. The `zone()` DSL will reference it via `tileset(asset("tiles/checker.png"))`. Content must be non-trivial (not all-blank) so that `set_bkg_tiles(...)` is actually called by the generated C.

**What to copy:** Nothing verbatim. The asset must be a real PNG file with valid pixel data. Planner generates it programmatically (e.g., 8×8 PNG with alternating black/white 4×4 blocks, or a simple 2-color 16×16 tile).

**Directory structure pattern** (from `simple-physics/res/sprites/`):
```
gbkt-examples/banks/res/
└── tiles/
    └── checker.png    # 8x8 or 16x16 minimal checkerboard, 2 colors (black/white DMG palette)
```

---

### `settings.gradle.kts` (modified, config)

**Analog:** `settings.gradle.kts` lines 56–67 (existing example includes block)

**What to copy:** The pattern of `include("gbkt-examples:NAME")` lines.

**What to change:** Add `include("gbkt-examples:banks")` at line 68 (after `include("gbkt-examples:metasprites-stress")`).

**Existing include pattern** (lines 65–67):
```kotlin
include("gbkt-examples:simple-physics")
include("gbkt-examples:metasprites")
include("gbkt-examples:metasprites-stress")
// ADD HERE:
include("gbkt-examples:banks")
```

---

### `GBDKSystemVisitor.kt` bug-fix: `trigger_saves()` stub in `visitSaveSystem()` (service, request-response)

**Analog:** `GBDKSystemVisitor.kt:2616-2631` — the `visitGenericSystem` else-branch that already generates a `trigger_<id>()` no-op stub for unknown systems.

**What to copy:** The `CFunction` construction shape for the `trigger_` stub from the `visitGenericSystem` else branch (lines 2618–2630). The stub takes a `UINT8 slotIndex` param (matching `ScriptOpVisitor.visitTriggerSystem()` which emits `CCall("trigger_saves", args)`), and its body calls `save_game_$sanitizedId(slotIndex)`.

**What to change:** The stub in `visitSaveSystem()` is NOT a no-op — it delegates to `save_game_$sanitizedId(slotIndex)`. Minimal body:

```kotlin
// Add after the `return listOf(saveGame, loadGame)` at line 485 — change the return:
val triggerStub = CFunction(
    name = "trigger_$sanitizedId",
    returnType = CVoid,
    params = listOf(CParam("slotIndex", CU8)),
    body = listOf(
        CExprStatement(CCall("save_game_$sanitizedId", listOf(CVar("slotIndex"))))
    ),
    sectionComment = "SaveSystem trigger stub — called by ScriptOpVisitor.visitTriggerSystem",
)
return listOf(saveGame, loadGame, triggerStub)
```

**Existing trigger-stub pattern** (from `GBDKSystemVisitor.kt` lines 2617–2631):
```kotlin
// else branch in visitGenericSystem — the no-op shape to copy
listOf(
    CFunction(
        name = "trigger_$sanitizedId",
        returnType = CVoid,
        body = listOf(
            CComment("system '${system.id}' has no v2 implementation — no-op stub")
        ),
        sectionComment = "System trigger: ${system.id}",
    )
)
```

**Why this is the right analog:** `ScriptOpVisitor.visitTriggerSystem()` (line 666–671) always calls `trigger_<id>()`. Every other system visitor that needs `TriggerSystem` compatibility generates this stub (generic systems) or a real trigger (combat engine, puzzle). `visitSaveSystem()` is the only visitor returning `listOf(saveGame, loadGame)` with no `trigger_` function — this is the gap that causes lcc `undefined identifier 'trigger_saves'`.

**File location:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt` — change `return listOf(saveGame, loadGame)` at line 485 to `return listOf(saveGame, loadGame, triggerStub)` with the stub constructed above.

---

## Shared Patterns

### MPL 2.0 File Header
**Source:** Every file in `gbkt-examples/simple-physics/src/`
**Apply to:** All new `.kt` files in `gbkt-examples/banks/src/`
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

### GBDKPipelineV2 invocation in emission tests
**Source:** `SimplePhysicsEmissionTest.kt` lines 104–111
**Apply to:** `BanksEmissionTest.kt` — all 4 `@Test` methods
```kotlin
val pipeline = GBDKPipelineV2()
val output = pipeline.generate(banks.build())
val mainC = output.files["main.c"] ?: error("main.c not generated")
val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")
```

### ROM-skip assumption guard
**Source:** `SimplePhysicsUatTest.kt` lines 56–60
**Apply to:** `BanksUatTest.kt` — `newAgent()` method
```kotlin
Assumptions.assumeTrue(
    ROM_FILE.exists(),
    "banks.gb not found — run buildRom first",
)
```

### Evidence directory resolution (worktree-safe)
**Source:** `SimplePhysicsEmissionTest.kt` lines 62–67
**Apply to:** `BanksEmissionTest.kt` companion object
```kotlin
val EVIDENCE_DIR = File(
    System.getProperty("user.dir"),
).resolve(
    "../../.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape"
).normalize()
```

### Scope-level brace-walk (per CLAUDE.md §"Scope-level grep gates")
**Source:** `SimplePhysicsEmissionTest.kt` lines 82–102
**Apply to:** ALL 4 emission invariant tests in `BanksEmissionTest.kt`

Never grep the whole file for BANKED or SWITCH_ROM. Always extract the function body first with `extractFunctionBody()`, then grep the body. File-level grep cannot distinguish `play_enter` from `title_enter` — if one scene has `BANKED` in an unrelated function, it masks a regression in the target function.

---

## No Analog Found

None. All 10 files have close codebase analogs.

---

## Critical Constraints (not analoged — research findings)

These are not pattern-copied items but must be respected in every plan that touches the listed files.

| Constraint | Source | Applies To |
|-----------|--------|-----------|
| `ramBanks.set(2)` MUST appear in `build.gradle.kts` gbkt block (not only DSL config) | RESEARCH §Pitfall 1; `GenerateCTask.kt:508`, `CompileRomTask.kt:134` | `build.gradle.kts` |
| `cartridge = "MBC5_RAM_BATTERY"` for `0x1B` byte; `"MBC5"` gives `0x19` | RESEARCH §Cartridge-Byte Emission; `GenerateCTask.kt:673` | `Banks.kt` |
| `triggerSystem("saves")` fails without the `trigger_saves()` stub bug fix | RESEARCH §DSL Call Surface Gap; `GBDKSystemVisitor.kt:485` | `Banks.kt`, `GBDKSystemVisitor.kt` |
| Anchor 4 SRAM persistence uses GBST save-state round-trip, NOT `emulator_stop`+`emulator_start` | RESEARCH §Pitfall 3; `SavestateManager.kt:14-19` | `BanksUatTest.kt`, `11-UAT.md`, `PLAYBOOK.md` |
| `romBanks = 4` minimum; zone allocator starts at bank 2 | RESEARCH §BankingAnalysisPass; `allocateZoneBanks:573` | `Banks.kt` |
| Per-function awk brace-walk before grep for all JVM emission invariants | CLAUDE.md §"Scope-level grep gates" | `BanksEmissionTest.kt` |

---

## Metadata

**Analog search scope:** `gbkt-examples/simple-physics/`, `gbkt-examples/dungeon/`, `gbkt-backend-gbdk/.../GBDKSystemVisitor.kt`, `settings.gradle.kts`
**Files scanned:** 9 source files read, 4 bash searches executed
**Pattern extraction date:** 2026-05-19

## PATTERN MAPPING COMPLETE
