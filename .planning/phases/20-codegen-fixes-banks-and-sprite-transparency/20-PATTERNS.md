# Phase 20: Codegen Fixes — Banks and Sprite Transparency — Pattern Map

**Mapped:** 2026-06-14
**Files analyzed:** 3 new files to create
**Analogs found:** 3 / 3

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `20-AUDIT-FIX-03.md` (phase dir) | audit doc | transform (seed → guard mapping) | `.planning/phases/19-codegen-fixes-metasprite-cluster/19-AUDIT-FIX-02.md` | exact |
| `MetaspritePhase20OracleTest.kt` (in `gbkt-examples/metasprites/src/test/`) | test | request-response (screenshot capture) | `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` | exact |
| `PlatformerTemplatePhase20OracleTest.kt` (in `gbkt-examples/platformer-template/src/test/`) | test | request-response (screenshot capture, GBC mode) | `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate128UatTest.kt` | exact |

---

## Pattern Assignments

### `20-AUDIT-FIX-03.md` (audit doc, transform)

**Analog:** `.planning/phases/19-codegen-fixes-metasprite-cluster/19-AUDIT-FIX-02.md`

**Status block pattern** (lines 1–14):
```markdown
# 20-AUDIT-FIX-03 — FIX-03 Banks Trio Emission-Guard Audit

## Status

**Confirmation-only phase.** All 3 banks seeds (SEED-014/015/016) are already guarded
by named JVM emission assertions. Zero new guards were authored (D-03 no-duplicate-coverage
decision). This document maps each seed to its pre-existing guard and the reverted-fix
scenario it catches.

**Verification run result (DATE):** BUILD SUCCESSFUL — N tests, 0 failures, 0 errors.
Confirmed GREEN at HEAD (`chore/hardening_0_1_0`).

Run command:
```bash
./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"
./gradlew :gbkt-examples:banks:buildRom
./gradlew :gbkt-examples:banks:test --tests "*.BanksUatTest"
```
```

**1:1 seed table pattern** (lines 29–35 of analog):
```markdown
## 1:1 Seed → Guard Mapping

| SEED | Title | Guarding test file (module-relative path) | Assertion name(s) | Existing or newly authored | Reverted-fix scenario |
|------|-------|------------------------------------------|-------------------|----------------------------|-----------------------|
| SEED-014 | bkg_tiles_load_banked gating incomplete | `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` | INV-2 (`_bkg_tiles_load_banked` SWITCH_ROM sequence in main.c), INV-6 (`play_enter` calls `_bkg_tiles_load_banked(2u,...)` in bank1.c) | existing | Reverting `hasZoneSceneBinder` guard at `GBDKPipeline.kt:1428` to sport-racing-only causes INV-2 to fail: `_bkg_tiles_load_banked` wrapper absent from main.c; INV-6 fails: play_enter has no zone-load call |
| SEED-015 | Banks trampoline body inheritance wrong | `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` | INV-5 (`title_enter_trampoline` section comment not over-matched by FunctionDeduplicationPass) | existing | Re-allowing comment-line rewrites in `FunctionDeduplicationPass` causes INV-5 to fail: comment preceding `title_enter_trampoline` reads "pause_enter" instead of "title_enter" |
| SEED-016 | Banks Anchor 4 SRAM test not executed | `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` | Anchor 4 (`anchor 4 SRAM persistence via GBST round-trip`) | existing | Removing the `trigger_saves` trampoline stub from `GBDKSystemVisitor.visitSaveSystem` causes Anchor 4 to fail at the SRAM round-trip mid-mutation sentinel |
```

**Guard details section pattern** (lines 62–123 of analog):
Each guard gets its own `###` subsection documenting: fix location, guard module, what the class header documents, and test count. See 19-AUDIT-FIX-02.md §"Guard Details" for exact structure.

**Decisions captured table pattern** (lines 115–123 of analog):
```markdown
## Decisions Captured

| Decision | Outcome |
|----------|---------|
| D-01 (standalone audit doc) | This document; kept separate from VERIFICATION.md |
| D-02 (ordering gate) | BanksEmissionTest run to GREEN FIRST before authoring; D-02 gate confirmed |
| D-03 (audit-first, no duplicate coverage) | 0 of 3 seeds needed new guards; all pre-existed at HEAD |
| D-07 (single-commit audit) | This doc committed together with SUMMARY; no production Kotlin modified |
```

---

### `MetaspritePhase20OracleTest.kt` (test, request-response screenshot)

**Analog:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt`

**Package + imports pattern** (lines 7–17):
```kotlin
package io.github.gbkt.examples.metasprites

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions
```

**companion object with Phase-20-specific EVIDENCE_DIR pattern** (analog lines 40–49, adapted):
```kotlin
companion object {
    // Phase 20 evidence dir — resolves from gbkt-examples/metasprites/ (user.dir at test time)
    private val EVIDENCE_DIR =
        File(System.getProperty("user.dir"))
            .resolve(
                "../../.planning/phases/" +
                    "20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04"
            )
            .normalize()
    private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
    private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
}
```

**newGbcAgent() helper pattern** (analog lines 74–89):
```kotlin
private fun newGbcAgent(): StepAgent {
    Assumptions.assumeTrue(
        ROM_FILE.exists(),
        "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
    )
    EVIDENCE_DIR.mkdirs()
    val baseConfig =
        AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
            .copy(gbcMode = true)  // GBC_COMPATIBLE target — D-05 requires authentic mode
    val metadata =
        if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
    val agent = StepAgent(baseConfig, metadata)
    agent.start()
    return agent
}
```

**captureAndRename() 2-param pattern** (analog lines 96–111):
```kotlin
private fun captureAndRename(agent: StepAgent, label: String, targetName: String): File {
    val captured = agent.captureScreenshot(label)
    val target = File(EVIDENCE_DIR, targetName)
    if (target.exists()) target.delete()
    check(captured.renameTo(target)) {
        "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
    }
    // Sidecar JSON: rename in lock-step (best-effort; not required by plan).
    val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
    if (sidecar.exists()) {
        val targetJson = File(EVIDENCE_DIR, target.nameWithoutExtension + ".json")
        if (targetJson.exists()) targetJson.delete()
        sidecar.renameTo(targetJson)
    }
    return target
}
```

**@Test method structure pattern** (analog lines 132–160):
```kotlin
@Test
fun `phase20 fix04 sprite outline rendering clean`() {
    newGbcAgent().use { agent ->
        agent.stepN(10) // boot
        agent.waitForScene("play", 120)
        // ... navigate to show elephant at rest ...
        captureAndRename(agent, "phase20-fix04-sprite-outline", "metasprites-sprite-outline.png")
        // Variable evidence: sprite is visible on screen (transparent pixels at index 0
        // means no black outline). Visual oracle is the binding artifact per Visual Evidence Rule.
    }
}
```

**Assumption + auto-skip pattern** (analog lines 56–62): Use `Assumptions.assumeTrue(ROM_FILE.exists(), "...")` inside `newGbcAgent()`. Tests auto-skip if ROM absent — GBDK not required for test compilation.

---

### `PlatformerTemplatePhase20OracleTest.kt` (test, request-response screenshot, GBC mode required)

**Analog:** `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate128UatTest.kt`

**Clone-and-retarget pattern** (analog class KDoc, lines 21–33):
This file is a clone of `PlatformerTemplateUatTest` with two changes: (1) `EVIDENCE_DIR` points to the Phase 20 evidence dir, (2) `newAgent()` is replaced by `newGbcAgent()` to add `.copy(gbcMode = true)`. All behavioral test methods are the minimal set needed for the Phase 20 twin shot — planner discretion whether to include or omit non-Phase-20 anchors.

**companion object with Phase-20-specific EVIDENCE_DIR + ROM path** (analog lines 36–51):
```kotlin
companion object {
    val ROM_FILE = File("build/gbkt/output/platformer-template.gb")
    val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    // Phase 20 evidence dir — resolves from gbkt-examples/platformer-template/ (user.dir at test time)
    val EVIDENCE_DIR =
        File(System.getProperty("user.dir"))
            .resolve(
                "../../.planning/phases/" +
                    "20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04"
            )
            .normalize()
}
```

**newGbcAgent() — GBC mode (REQUIRED by D-05)** (analog lines 53–65, adapted from PlatformerTemplate128UatTest.newAgent() + MetaspriteUatTest.newGbcAgent()):
```kotlin
private fun newGbcAgent(): StepAgent {
    Assumptions.assumeTrue(
        ROM_FILE.exists(),
        "platformer-template.gb not found — run buildRom first",
    )
    EVIDENCE_DIR.mkdirs()
    // D-05 LOCKED: platformer-template targets GBC_COMPATIBLE; DMG-mode captures look
    // green-tinted and count as palette regressions (learning_platformer_mcp_needs_gbc_mode).
    val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
        .copy(gbcMode = true)
    val metadata =
        if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
    val agent = StepAgent(baseConfig, metadata)
    agent.start()
    return agent
}
```

**captureAndRename() 4-param pattern** (analog lines 73–92):
```kotlin
private fun captureAndRename(
    agent: StepAgent,
    label: String,
    anchorDir: File,
    targetName: String,
): File {
    val captured = agent.captureScreenshot(label)
    val target = File(anchorDir, targetName)
    if (target.exists()) target.delete()
    check(captured.renameTo(target)) {
        "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
    }
    val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
    if (sidecar.exists()) {
        val targetJson = File(anchorDir, target.nameWithoutExtension + ".json")
        if (targetJson.exists()) targetJson.delete()
        sidecar.renameTo(targetJson)
    }
    return target
}
```

**@Test method structure pattern** (analog methods throughout PlatformerTemplate128UatTest):
```kotlin
@Test
fun `phase20 fix04 platformer player transparency no regression`() {
    newGbcAgent().use { agent ->
        agent.stepN(10) // boot
        agent.waitForScene("world1Area1", 180)
        // ... navigate to show player sprite on screen ...
        captureAndRename(
            agent,
            "phase20-fix04-platformer-player-transparency",
            EVIDENCE_DIR,
            "platformer-player-transparency.png",
        )
        // GBC mode required — D-05 locked. Visual oracle is the binding artifact.
    }
}
```

**assertScreenshotIsNonUniform() utility** (analog lines 101–130): Copy verbatim from `PlatformerTemplate128UatTest` — it validates the PNG has >= 2 distinct colours and dominant colour < 95% of pixels. Apply to both FIX-04 captures to catch a blank/solid-colour regression.

---

## Shared Patterns

### Auto-skip on missing ROM
**Source:** `MetaspriteUatTest.newAgent()` lines 56–62 and `PlatformerTemplate128UatTest.newAgent()` lines 53–57
**Apply to:** Both Phase 20 oracle test classes
```kotlin
Assumptions.assumeTrue(
    ROM_FILE.exists(),
    "<game>.gb not found — run :<module>:buildRom first",
)
```

### EVIDENCE_DIR resolution from user.dir
**Source:** `PlatformerTemplate128UatTest` companion object, lines 44–50
**Apply to:** Both Phase 20 oracle test classes
```kotlin
val EVIDENCE_DIR =
    File(System.getProperty("user.dir"))
        .resolve("../../.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04")
        .normalize()
```
Note: `user.dir` at Gradle test time resolves to the example module directory (e.g. `gbkt-examples/metasprites/`), so `../../` walks to the repo root.

### GBC mode activation via .copy()
**Source:** `MetaspriteUatTest.newGbcAgent()` lines 81–83
**Apply to:** Both Phase 20 oracle test classes (D-05 requires GBC mode for both: metasprites targets GBC_COMPATIBLE, platformer-template is locked GBC by D-05)
```kotlin
AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
    .copy(gbcMode = true)
```
The `.noi` sym file is auto-discovered from `build/gbkt/output/<name>.noi` by `discoverFiles()` — no explicit path needed.

### License header (MPL-2.0)
**Source:** Every existing test file, lines 1–6
**Apply to:** Both new Kotlin test files
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

---

## No Analog Found

None. All three deliverables have exact analogs in the codebase.

---

## Key Decision: Separate Phase-20 Test Classes (Not Methods in Existing Files)

The `PlatformerTemplate128UatTest.kt` precedent (Phase 12.8) establishes the "clone-and-retarget-EVIDENCE_DIR" pattern for phase-specific screenshot captures. Phase 20 should create:

- `MetaspritePhase20OracleTest.kt` in `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/`
- `PlatformerTemplatePhase20OracleTest.kt` in `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/`

Both classes have a Phase-20-specific `EVIDENCE_DIR` constant — this avoids invasive changes to existing test files and keeps phase evidence cleanly attributed. This is explicitly recommended in RESEARCH.md §"Open Questions" item 2.

---

## Metadata

**Analog search scope:** `.planning/phases/19-*/`, `gbkt-examples/metasprites/src/test/`, `gbkt-examples/platformer-template/src/test/`
**Files scanned:** 5 (19-AUDIT-FIX-02.md, MetaspriteUatTest.kt, PlatformerTemplate128UatTest.kt, PlatformerTemplateUatTest.kt partial)
**Pattern extraction date:** 2026-06-14
