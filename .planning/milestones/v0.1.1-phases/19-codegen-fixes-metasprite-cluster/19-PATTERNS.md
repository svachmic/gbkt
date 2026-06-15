# Phase 19: Codegen Fixes — Metasprite Cluster - Pattern Map

**Mapped:** 2026-06-13
**Files analyzed:** 1 new code file + 1 new doc file
**Analogs found:** 1 / 1

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt` | test (UAT/emulator) | request-response (ROM frame-step + screenshot emit) | `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` | exact |
| `.planning/phases/19-codegen-fixes-metasprite-cluster/19-AUDIT-FIX-02.md` | doc (audit table) | n/a | `.planning/phases/16-seed-triage/TRIAGE.md` (structure/column convention) | layout-reference only — no code analog needed |

---

## Pattern Assignments

### `Phase19VisualEvidenceTest.kt` (test, request-response/emulator)

**Analog:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt`

**Imports pattern** (lines 1–17):
```kotlin
package io.github.gbkt.examples.metasprites

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions
```

**EVIDENCE_DIR + ROM_FILE companion pattern** (lines 40–50) — MUST change the evidence path to Phase 19:
```kotlin
companion object {
    // Evidence directory — user.dir resolves to gbkt-examples/metasprites/ at test runtime;
    // ../../ walks up to the repo root.
    private val EVIDENCE_DIR =
        File(
            "../../.planning/phases/" +
                "19-codegen-fixes-metasprite-cluster/evidence"
        )
    private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
    private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
}
```

Note: the analog (`MetaspriteUatTest.kt`) hardcodes Phase 10's evidence dir
(`10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots`). The new
class MUST point to `19-codegen-fixes-metasprite-cluster/evidence/` instead.

**GBC agent factory pattern** (lines 75–89) — use this, not `newAgent()`:
```kotlin
private fun newGbcAgent(): StepAgent {
    Assumptions.assumeTrue(
        ROM_FILE.exists(),
        "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
    )
    EVIDENCE_DIR.mkdirs()
    val baseConfig =
        AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
            .copy(gbcMode = true)
    val metadata =
        if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
    val agent = StepAgent(baseConfig, metadata)
    agent.start()
    return agent
}
```

Key points:
- `.copy(gbcMode = true)` is mandatory — all Phase 19 captures are GBC-target (D-03).
- `AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)` auto-discovers the `.noi` symFile from `build/gbkt/output/metasprites.noi` — no manual path required.
- `EVIDENCE_DIR.mkdirs()` must be called inside the factory so the directory exists before
  `captureScreenshot()` writes.

**captureAndRename helper pattern** (lines 96–111):
```kotlin
private fun captureAndRename(agent: StepAgent, label: String, targetName: String): File {
    val captured = agent.captureScreenshot(label)
    val target = File(EVIDENCE_DIR, targetName)
    if (target.exists()) target.delete()
    check(captured.renameTo(target)) {
        "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
    }
    // Sidecar JSON: rename in lock-step (best-effort).
    val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
    if (sidecar.exists()) {
        val targetJson = File(EVIDENCE_DIR, target.nameWithoutExtension + ".json")
        if (targetJson.exists()) targetJson.delete()
        sidecar.renameTo(targetJson)
    }
    return target
}
```

**GBC boot + scene-wait pattern** (lines 262–271):
```kotlin
newGbcAgent().use { agent ->
    // GBC mode needs 30 boot frames (not 10) — CGB PPU init takes extra time.
    agent.stepN(30)
    agent.waitForScene("play", 120)
    // ...
}
```

**Screenshot assertion pattern** (lines 336–341):
```kotlin
val png = captureAndRename(agent, "label", "SEED-XXX/screenshot.png")

assertTrue(
    png.length() > 0,
    "Phase 19 SEED-XXX screenshot must be non-empty: ${png.absolutePath}",
)
```

Note: `captureAndRename` second arg (`targetName`) should include the per-seed subdirectory
so the evidence layout matches the Phase 16 convention:
`SEED-004/screenshot.png`, `SEED-005/screenshot.png`, etc. The directory must be created
before `renameTo` — either extend `captureAndRename` to call `target.parentFile.mkdirs()`,
or create seed subdirs explicitly at test start.

**A-press input sequence for rot=8 (SEED-006/013 climax frame)** (lines 274–330):
The analog's `behavior3` test drives 8 A-presses with release frames between each press
to navigate to `rot=8` (subpal=2, cyan). The Phase 19 test can reuse the same sequence:
```kotlin
// Each press/release cycle: agent.step(setOf(Button.A)); agent.step(emptySet())
// After 8 presses rot=8, subpal=2 (cyan) — correct climax for SEED-006/013.
// Wait 2 extra frames for GBC PPU palette flush before capture.
agent.step(emptySet())
agent.step(emptySet())
val png = captureAndRename(agent, "seed006-013", "SEED-006/screenshot.png")
```

---

## Shared Patterns

### Assumptions.assumeTrue Skip Guard
**Source:** `MetaspriteUatTest.kt` lines 57–60
**Apply to:** All test methods / factory methods in `Phase19VisualEvidenceTest.kt`
```kotlin
Assumptions.assumeTrue(
    ROM_FILE.exists(),
    "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
)
```
Tests skip automatically when the ROM is absent — no test failure for missing GBDK.

### use{} Resource Management
**Source:** `MetaspriteUatTest.kt` lines 133, 182, 262
**Apply to:** Every `@Test` method body
```kotlin
newGbcAgent().use { agent ->
    // StepAgent implements Closeable — always use use{} to ensure agent.stop()
}
```

### Spotless + Detekt per commit (D-09)
**Apply to:** Every Phase 19 commit touching `gbkt-examples/metasprites`
```bash
./gradlew :gbkt-examples:metasprites:spotlessApply :gbkt-examples:metasprites:detekt
```

---

## No Analog Found

No files in Phase 19 lack a usable analog.

---

## `19-AUDIT-FIX-02.md` — Document Layout Reference

No code analog. The document follows the table convention established in Phase 16 TRIAGE.md.
Required columns (from D-06):

| Column | Content |
|--------|---------|
| SEED | SEED-007 … SEED-011 |
| Guarding test file | Absolute module-relative path |
| Assertion name | Exact method name(s) |
| Existing or newly authored | "existing" for all 5 (RESEARCH confirmed 0 new guards needed) |
| Reverted-fix scenario | What would go RED if the fix were reverted |

All 5 seeds are already guarded — document their existing guards only; do not author duplicate tests.

---

## Metadata

**Analog search scope:** `gbkt-examples/metasprites/src/test/`
**Files scanned:** 1 (MetaspriteUatTest.kt, 346 lines, read in full)
**Pattern extraction date:** 2026-06-13
