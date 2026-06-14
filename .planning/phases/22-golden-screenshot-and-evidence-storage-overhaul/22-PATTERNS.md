# Phase 22: Golden Screenshot and Evidence Storage Overhaul — Pattern Map

**Mapped:** 2026-06-14
**Files analyzed:** 8 (new/modified production + infra files; the 33 test-class migrations follow these patterns)
**Analogs found:** 8 / 8

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `gbkt-emulator/.../agent/GoldenAssertions.kt` (NEW) | utility | request-response | `gbkt-emulator/.../agent/UatRunner.kt` golden-diff block (lines 193-209) | role-match |
| `gbkt-emulator/.../agent/ScreenshotCapture.kt` (MODIFY) | utility | file-I/O | self | self |
| `gbkt-emulator/.../agent/AgentSessionConfig.kt` (MODIFY) | utility/config | file-I/O | self (`discoverFiles` body lines 63-83) | self |
| `gbkt-emulator/src/test/.../ScreenshotCaptureTest.kt` (MODIFY) | test | request-response | self (lines 88-95, capturedAt assertions) | self |
| `gbkt-examples/metasprites/build.gradle.kts` (MODIFY) | config | request-response | self + `build.gradle.kts` root `hasProperty` pattern (line 10) | exact |
| `gbkt-examples/platformer-template/build.gradle.kts` (MODIFY) | config | request-response | same as above | exact |
| 8 visual-UAT test classes (MODIFY) | test | request-response | `Phase19VisualEvidenceTest.kt` — `captureAndRename` + `.copy(gbcMode=true)` pattern | exact |
| 25 emission test classes (MODIFY) | test | file-I/O | `AutoExitSynthesisTest.kt` — `EVIDENCE_DIR` companion pattern (lines 60-74) | exact |

---

## Pattern Assignments

### `gbkt-emulator/.../agent/GoldenAssertions.kt` (NEW — utility, request-response)

**Closest analog:** `UatRunner.kt` golden-diff block, lines 193-209

**Imports pattern** — copy from `UatRunner.kt` lines 1-12:
```kotlin
package io.github.gbkt.emulator.agent

import java.io.File
import javax.imageio.ImageIO
```

**Core golden-diff pattern** (UatRunner.kt lines 193-209 — the block to extract into the helper):
```kotlin
// UatRunner.kt lines 193-209
val diffResult =
    if (goldenDir != null) {
        val goldenFile = File(goldenDir, "$label.png")
        if (goldenFile.exists()) {
            val tol = checkpointTolerances[label] ?: goldenTolerance
            VisualDiff.compare(goldenFile, screenshotFile, tol, config.screenshotDir)
        } else {
            logger.info(
                "GOLDEN MISSING: $goldenFile — promote with: " +
                    "cp ${screenshotFile.absolutePath} ${goldenFile.absolutePath}"
            )
            null
        }
    } else {
        null
    }
```

**Differences from analog for the new helper:**
- Missing golden → throw `AssertionError` (hard failure) not `logger.info` (soft log)
- Missing-golden message points at `-Pgbkt.updateGoldens` flag, not manual `cp`
- Tolerance is always `0.0` (exact match)
- Update mode: when `System.getProperty(GOLDEN_PROP_KEY) != null`, write golden and pass
- `GOLDEN_PROP_KEY = "gbkt.updateGoldens"` must be a named constant (Project Rule: no magic strings)

**`VisualDiff.compare()` call signature** (VisualDiff.kt lines 58-63) — the exact call to make:
```kotlin
// VisualDiff.compare signature:
fun compare(
    expected: File,   // golden file from src/test/resources/goldens/
    actual: File,     // captured scratch PNG in build/gbkt/screenshots/
    tolerance: Double = 0.0,       // always 0.0 for assertGoldenMatch
    diffOutputDir: File? = null,   // same scratch dir; produces *_diff.png on mismatch
): DiffResult
```

**DiffResult fields** (VisualDiff.kt lines 22-27) — what to check after compare():
```kotlin
data class DiffResult(
    val match: Boolean,
    val diffCount: Int,
    val totalPixels: Int,
    val diffImage: File?,   // non-null on mismatch when diffOutputDir provided
)
```

**Error handling pattern** — copy from existing throw pattern in `AgentSessionConfig.kt` line 51:
```kotlin
require(romFile.exists()) { "ROM file does not exist: ${romFile.absolutePath}" }
// New helper mirrors: throw AssertionError("GOLDEN MISSING ...") for missing-golden failure
```

---

### `gbkt-emulator/.../agent/ScreenshotCapture.kt` (MODIFY — drop `capturedAt`, file-I/O)

**Analog:** self

**Change site** (ScreenshotCapture.kt line 106 — the exact line to remove):
```kotlin
// BEFORE (line 106):
.put("capturedAt", System.currentTimeMillis())

// AFTER: remove this line entirely — the sidecar still has frameNumber + label + variables
```

**Surrounding context** (lines 102-119 — keep everything except line 106):
```kotlin
val sidecar =
    JSONObject()
        .put("frameNumber", frameNumber)
        .put("label", label)
        .put("capturedAt", System.currentTimeMillis())   // <-- REMOVE THIS LINE
        .put("variables", variables)
if (debugLogEntries.isNotEmpty()) { ... }
```

---

### `gbkt-emulator/src/test/.../ScreenshotCaptureTest.kt` (MODIFY — remove capturedAt assertions)

**Analog:** self

**Change site** (ScreenshotCaptureTest.kt lines 85-95 — the test block containing capturedAt assertions):
```kotlin
// BEFORE (lines 85-95):
val beforeCapture = System.currentTimeMillis()
// ... capture call ...
val afterCapture = System.currentTimeMillis()

val jsonFile = File(tempDir, "metadata_frame99.json")
val json = JSONObject(jsonFile.readText())

assertEquals(99, json.getInt("frameNumber"))
assertEquals("metadata", json.getString("label"))
val capturedAt = json.getLong("capturedAt")                                      // line 92 REMOVE
assertTrue(capturedAt >= beforeCapture, "capturedAt should be >= time before capture")  // line 93 REMOVE
assertTrue(capturedAt <= afterCapture, "capturedAt should be <= time after capture")    // line 94 REMOVE
assertNotNull(json.getJSONObject("variables"), "variables field should exist")
```

**After:** Remove lines 92-94 (capturedAt assertions). Replace with an assertion that `capturedAt` key is absent:
```kotlin
// AFTER — add this instead of the removed capturedAt assertions:
assertFalse(json.has("capturedAt"), "capturedAt field must be absent from sidecar (D-08)")
```

---

### `gbkt-emulator/.../agent/AgentSessionConfig.kt` (MODIFY — add ROM 0x143 GBC detect)

**Analog:** self (`discoverFiles` body, lines 63-83)

**Current body** (lines 63-83 — the full function to modify):
```kotlin
fun discoverFiles(romFile: File, screenshotDir: File? = null): AgentSessionConfig {
    val outputDir = romFile.parentFile
    val gbktDir = outputDir?.parentFile
    val generatedDir = gbktDir?.let { File(it, "generated") }
    val baseName = romFile.nameWithoutExtension

    val symFile =
        outputDir?.let { File(it, "$baseName.noi") }?.takeIf { it.exists() }
            ?: outputDir?.let { File(it, "$baseName.sym") }?.takeIf { it.exists() }
    val metadataFile =
        generatedDir?.let { File(it, "game_metadata.json") }?.takeIf { it.exists() }
    val sourceMapsDir = generatedDir?.takeIf { it.exists() }

    return AgentSessionConfig(           // line 76 — insert gbcMode here
        romFile = romFile,
        symFile = symFile,
        metadataFile = metadataFile,
        sourceMapsDir = sourceMapsDir,
        screenshotDir = screenshotDir ?: File(outputDir ?: File("."), "screenshots"),
    )
}
```

**Insertion: add before the `return` block** — ROM 0x143 read:
```kotlin
// Insert after line 74 (sourceMapsDir assignment), before line 76 (return):
val gbcMode = romFile.inputStream().use { stream ->
    stream.skip(0x143)
    val cgbByte = stream.read()   // -1 on EOF → treated as DMG (correct)
    cgbByte == 0x80 || cgbByte == 0xC0
}
```

**Updated `return` block** — add `gbcMode`:
```kotlin
return AgentSessionConfig(
    romFile = romFile,
    symFile = symFile,
    metadataFile = metadataFile,
    sourceMapsDir = sourceMapsDir,
    screenshotDir = screenshotDir ?: File(outputDir ?: File("."), "screenshots"),
    gbcMode = gbcMode,    // <-- NEW: auto-detected from ROM header byte 0x143
)
```

---

### `gbkt-examples/metasprites/build.gradle.kts` (MODIFY — add systemProperty wiring)

**Analog:** self (lines 30-32) + root `build.gradle.kts` line 10 (`hasProperty` pattern)

**Current `tasks.test` block** (lines 30-32):
```kotlin
tasks.test {
    useJUnitPlatform()
}
```

**After — add systemProperty propagation:**
```kotlin
tasks.test {
    useJUnitPlatform()
    if (project.hasProperty("gbkt.updateGoldens")) {
        systemProperty("gbkt.updateGoldens", "true")
    }
}
```

**Precedent for `hasProperty` pattern** (root `build.gradle.kts` line 10):
```kotlin
val isRelease = project.hasProperty("release")
```
Apply the same boolean-presence check — `hasProperty` not `findProperty`, because the flag has no value, only presence.

**Apply this same change to** `gbkt-examples/platformer-template/build.gradle.kts` — same current shape, same addition.

---

### 8 visual-UAT test classes (MODIFY — swap `captureAndRename` for `assertGoldenMatch`)

**Analog:** `Phase19VisualEvidenceTest.kt` — complete pattern to replace

**Current `EVIDENCE_DIR` companion pattern** (Phase19VisualEvidenceTest.kt lines 41-48):
```kotlin
companion object {
    private val EVIDENCE_DIR =
        File("../../.planning/phases/" + "19-codegen-fixes-metasprite-cluster/evidence")
    private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
    private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
}
```

**After — remove EVIDENCE_DIR, add goldens path:**
```kotlin
companion object {
    // EVIDENCE_DIR removed (R1)
    private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
    private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    private val SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")
}
```

**Current `newGbcAgent()` helper** (Phase19VisualEvidenceTest.kt lines 58-72):
```kotlin
private fun newGbcAgent(): StepAgent {
    Assumptions.assumeTrue(ROM_FILE.exists(), "...")
    EVIDENCE_DIR.mkdirs()
    val baseConfig =
        AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
            .copy(gbcMode = true)          // <-- REMOVE after discoverFiles auto-detect
    ...
}
```

**After — remove `.copy(gbcMode = true)`, change screenshotDir to SCRATCH_DIR, add GBC-header assertion (D-07):**
```kotlin
private fun newGbcAgent(): StepAgent {
    Assumptions.assumeTrue(ROM_FILE.exists(), "...")
    val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = SCRATCH_DIR)
    // D-07 guarded bless: assert ROM is GBC before any golden write
    check(baseConfig.gbcMode) {
        "ROM 0x143 CGB flag not set — is this a DMG ROM? Aborting to prevent inverted-palette golden bless."
    }
    ...
}
```

**Current `captureAndRename` call site** (Phase19VisualEvidenceTest.kt lines 125, 132, 139):
```kotlin
val seed004 = captureAndRename(agent, "seed004-boot", "SEED-004/screenshot.png")
assertTrue(seed004.length() > 0, "...")
```

**After — swap to `assertGoldenMatch`:**
```kotlin
// goldenFile resolved from src/test/resources/goldens/metasprites/<anchor>.png
val goldenFile = File(
    javaClass.getResource("/goldens/metasprites/elephant-boot-seed004.png")!!.toURI()
)
assertGoldenMatch(agent, label = "seed004-boot", goldenFile = goldenFile, scratchDir = SCRATCH_DIR)
```

**The `captureAndRename` helper itself** (Phase19VisualEvidenceTest.kt lines 80-96) is removed entirely — `assertGoldenMatch` in `GoldenAssertions.kt` replaces it.

---

### 25 emission test classes (MODIFY — redirect EVIDENCE_DIR to build/)

**Analog:** `AutoExitSynthesisTest.kt` lines 60-74 — the companion pattern to replace

**Current EVIDENCE_DIR pattern** (AutoExitSynthesisTest.kt lines 68-74):
```kotlin
val EVIDENCE_DIR =
    File(System.getProperty("user.dir"))
        .resolve(
            "../.planning/phases/" +
                "13.5-framework-primitives-graphics-level-codegen-inserted/" +
                "evidence/tier1-shape"
        )
        .normalize()
```

**After — redirect to gitignored build/ scratch:**
```kotlin
val EVIDENCE_DIR =
    File(System.getProperty("user.dir"))
        .resolve("build/gbkt/test-evidence")
        .normalize()
```

The path `build/gbkt/test-evidence` is gitignored via the root `.gitignore`'s `build/` pattern. No golden migration needed for emission tests — the in-test C assertion remains the gate. The EVIDENCE_DIR companion name may remain for backward compatibility within the test, or be renamed to SCRATCH_DIR for clarity (planner decides; either is acceptable, consistent naming within each module preferred).

---

## Shared Patterns

### VisualDiff.compare() — the single diff engine for all golden comparisons
**Source:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VisualDiff.kt` lines 58-94
**Apply to:** `GoldenAssertions.kt` (the only consumer of the new helper)
**Do NOT introduce a second diff implementation (D-04)**

```kotlin
// Exact call pattern for assertGoldenMatch at tolerance=0.0:
val result = VisualDiff.compare(goldenFile, capturedFile, tolerance = 0.0, diffOutputDir = scratchDir)
if (!result.match) {
    throw AssertionError(
        "Golden mismatch: ${result.diffCount}/${result.totalPixels} pixels differ. " +
            "Diff image: ${result.diffImage?.absolutePath}"
    )
}
```

### Gradle property → JVM systemProperty propagation
**Source:** root `build.gradle.kts` line 10 (`hasProperty` pattern) + `gbkt-examples/metasprites/build.gradle.kts` lines 30-32 (tasks.test block shape)
**Apply to:** `build.gradle.kts` in each of the 8 visual-UAT example modules that have `tasks.test { useJUnitPlatform() }` blocks

```kotlin
// Root build.gradle.kts precedent (line 10):
val isRelease = project.hasProperty("release")

// Pattern to add to each example's tasks.test block:
tasks.test {
    useJUnitPlatform()
    if (project.hasProperty("gbkt.updateGoldens")) {
        systemProperty("gbkt.updateGoldens", "true")
    }
}
```

### `user.dir` relative scratch path
**Source:** `AutoExitSynthesisTest.kt` lines 68-73
**Apply to:** All 25 emission test classes that currently navigate to `.planning/phases/`; also used for SCRATCH_DIR in the 8 visual-UAT test classes

```kotlin
// Pattern: resolve from user.dir (module root at Gradle test runtime)
File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence").normalize()
```

### `AgentSessionConfig.discoverFiles` call pattern (post-R5)
**Source:** `Phase19VisualEvidenceTest.kt` lines 65-66 (current) → updated form
**Apply to:** All 8 visual-UAT test classes that currently call `discoverFiles(...).copy(gbcMode = true)`

```kotlin
// BEFORE (current in all 3 GBC-target test classes):
AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR).copy(gbcMode = true)

// AFTER (once discoverFiles auto-detect lands):
AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = SCRATCH_DIR)
// gbcMode is auto-detected from ROM 0x143 — no .copy() needed
```

---

## No Analog Found

All files have clear analogs in the codebase. No greenfield patterns needed.

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `GoldenAssertions.kt` | utility | request-response | Closest match is `UatRunner` golden-diff block (lines 193-209) — not a new file type, but the extraction into a standalone helper is new. Use UatRunner pattern directly. |

---

## Metadata

**Analog search scope:** `gbkt-emulator/src/`, `gbkt-examples/metasprites/src/test/`, `gbkt-backend-gbdk/src/test/`, root `build.gradle.kts`
**Files scanned:** 8 (AgentSessionConfig.kt, ScreenshotCapture.kt, VisualDiff.kt, UatRunner.kt, ScreenshotCaptureTest.kt, Phase19VisualEvidenceTest.kt, AutoExitSynthesisTest.kt, metasprites/build.gradle.kts)
**Pattern extraction date:** 2026-06-14

---

## PATTERN MAPPING COMPLETE

**Phase:** 22 - golden-screenshot-and-evidence-storage-overhaul
**Files classified:** 8 primary files + 33 test-class migrations following the two emission/visual-UAT patterns
**Analogs found:** 8 / 8

### Coverage
- Files with exact analog: 5 (ScreenshotCapture.kt, AgentSessionConfig.kt, ScreenshotCaptureTest.kt, 2× build.gradle.kts)
- Files with role-match analog: 3 (GoldenAssertions.kt → UatRunner golden block; 8 visual-UAT classes → Phase19VisualEvidenceTest; 25 emission classes → AutoExitSynthesisTest)
- Files with no analog: 0

### Key Patterns Identified
- `GoldenAssertions.assertGoldenMatch` extracts the UatRunner.kt lines 193-209 golden-diff flow into a standalone helper; missing golden = hard `AssertionError` pointing at `-Pgbkt.updateGoldens` (not a soft log)
- `VisualDiff.compare(expected, actual, tolerance=0.0, diffOutputDir=scratchDir)` is the single diff call — do not add a second diff implementation
- Gradle property propagation uses `project.hasProperty("gbkt.updateGoldens")` → `systemProperty(...)` in `tasks.test` blocks — same idiom as root `build.gradle.kts` line 10 `isRelease` check
- `EVIDENCE_DIR` companion in emission tests is a `user.dir`-relative path to `.planning/phases/` — the replacement is a `user.dir`-relative path to `build/gbkt/test-evidence` (same structure, different root)
- Visual-UAT classes swap `captureAndRename(...) + assertTrue(file.length() > 0)` for `assertGoldenMatch(agent, label, goldenFile, scratchDir)` — the assertTrue on file size is replaced by the pixel-exact diff failure
- `AgentSessionConfig.discoverFiles()` gains a 3-line insertion before its `return`: `inputStream().use { skip(0x143); read() }` → `gbcMode = cgbByte == 0x80 || cgbByte == 0xC0`; the `.copy(gbcMode = true)` in visual-UAT tests is removed after this lands
- `capturedAt` is removed from `ScreenshotCapture.kt` line 106 (`JSONObject.put("capturedAt", ...)` deleted); `ScreenshotCaptureTest.kt` lines 92-94 are replaced with `assertFalse(json.has("capturedAt"), ...)`

### File Created
`/Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/22-golden-screenshot-and-evidence-storage-overhaul/22-PATTERNS.md`

### Ready for Planning
Pattern mapping complete. Planner can now reference analog patterns in PLAN.md files.
