# Phase 22: Golden Screenshot and Evidence Storage Overhaul — Research

**Researched:** 2026-06-14
**Domain:** Test infrastructure — golden screenshot storage, PNG diff engine, Gradle property propagation, ROM CGB-flag detection
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Goldens live per-module under `src/test/resources/goldens/<rom>/<anchor>.png` — NOT a single repo-root location. Each example module owns its own goldens, co-located with its tests. Because each example module compiles a single ROM, the `<rom>/` segment is organizational; the planner MAY flatten to `goldens/<anchor>.png` if redundant, but keep structure consistent.

**D-02:** Anchor names are descriptive and phase-agnostic (e.g. `metasprites/elephant-cyan-subpalette.png`, `platformer/world1-boot.png`) — decoupled from phase/seed IDs. Migration preserves PNG bytes byte-identically (binding baselines, no re-render). ~12 anchors total.

**D-03:** Add a new thin `assertGolden`-style helper (e.g. `assertGoldenMatch(rom, anchor, capturedFrame)`) rather than rewiring the 33 bespoke tests onto `UatRunner`. Helper: captures to gitignored scratch, calls existing `VisualDiff.compare(...)` at tolerance `0.0`, fails the test on any mismatch. `UatRunner`'s own `goldenDir` flow is left untouched.

**D-04:** Reuse existing `VisualDiff.compare()` (`gbkt-emulator/.../agent/VisualDiff.kt`) as the diff engine — it supports exact match at tolerance 0.0 and emits a red diff image on mismatch. Do NOT introduce a second diff implementation.

**D-05:** Missing golden = test failure with a re-baseline hint: `GOLDEN MISSING <path> — run ./gradlew test -Pgbkt.updateGoldens to bless it`. A normal run never auto-creates a golden.

**D-06:** Re-baseline triggered by Gradle project property `-Pgbkt.updateGoldens` propagated into the test JVM as a system property that `assertGolden` reads. When set, helper writes the golden (and passes) instead of diffing. A plain `./gradlew test` (flag absent) must never write a golden.

**D-07:** Guarded bless — even in update mode, the GBC-header auto-detect (SPEC R5) still runs and GBC-target tests assert the ROM is GBC, so a mis-built DMG ROM cannot silently bless an inverted-palette golden.

**D-08:** Keep the `.json` sidecar but only in gitignored scratch, and drop the nondeterministic `capturedAt` field from `ScreenshotCapture.capture()` (line 106). The sidecar's `variables`/`debugLog` stay useful for debugging. `capturedAt` has zero production consumers — only `ScreenshotCaptureTest.kt` lines 92-94 assert on it, so update that test in lock-step. The 22 currently-committed sidecar `.json` files are `git rm`'d as part of the evidence migration (R6).

### Claude's Discretion

- Module placement of the new `assertGolden` helper (`gbkt-test` test-infra vs `gbkt-emulator` agent package) — planner decides; both are already on the example tests' classpath.
- Whether to flatten `goldens/<rom>/<anchor>.png` to `goldens/<anchor>.png` per module if the `<rom>/` segment is redundant (D-01).
- Exact name of the system-property key derived from `-Pgbkt.updateGoldens` and the precise wording of the missing-golden failure message.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope. pong-class ROM-hash non-determinism remains tracked separately; it affects `.gb` binaries not PNGs and is explicitly out of scope per SPEC.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| R1 | EVIDENCE_DIR elimination — no test writes to `.planning/phases/**/evidence` | All 33 EVIDENCE_DIR test classes identified; grouped by module and type |
| R2 | Immutable visual goldens with exact-match diff | VisualDiff.compare() at tolerance=0.0 is the engine; assertGolden helper design documented |
| R3 | Emission test scratch redirect to `build/` | Emission test pattern confirmed; all txt dumps redirect to `build/gbkt/test-evidence/` or similar |
| R4 | Explicit, reviewed re-baselining | Gradle property `-Pgbkt.updateGoldens` → JVM system property approach; wiring point is each module's `tasks.test { systemProperty(...) }` |
| R5 | GBC auto-detect from ROM CGB flag | discoverFiles() target identified; ROM offset 0x143, values 0x80/0xC0; GBC-target examples enumerated |
| R6 | Migration of binding goldens + scratch gitignore | 22 sidecar .json files confirmed; 33 PNG + 77 txt + 11 other tracked files to git rm; 22 blessed PNG anchors identified |
| R7 | TESTING.md documentation update | Current per-phase evidence convention documented in context/TESTING.md; stale references identified |
</phase_requirements>

---

## Summary

Phase 22 is a test-infrastructure overhaul — zero production code changes, no ROM rendering changes. The scope has three distinct work streams:

**Stream 1 — Core infrastructure (new code):** Two additions to the framework: (a) `ScreenshotCapture.kt` drops the `capturedAt` field; (b) an `assertGoldenMatch(rom, anchor, capturedFrame)` helper is added (placement: `gbkt-emulator` or `gbkt-test`, see below). The helper reuses `VisualDiff.compare()` at tolerance=0.0 and reads a JVM system property `gbkt.updateGoldens` to switch between write-golden-and-pass and diff-and-fail modes. `AgentSessionConfig.discoverFiles()` gains a one-line ROM byte-read at offset 0x143 to set `gbcMode` automatically.

**Stream 2 — Mass mechanical migration (33 test classes):** Every EVIDENCE_DIR companion object is replaced. Visual UAT tests (8 bespoke-capture test classes) swap `captureAndRename(...)` for `assertGoldenMatch(...)`. Emission tests (25 classes) redirect their txt dumps from `.planning/phases/**/evidence` to a gitignored `build/` path. The 21 `.copy(gbcMode = true)` calls in visual UAT tests are removed after discoverFiles() auto-detection lands.

**Stream 3 — Repository hygiene:** 143 tracked files under `.planning/phases/**/evidence/` are `git rm`'d (keeping only the 22 blessed PNG anchors, which are first cp'd into `src/test/resources/goldens/` in each example module). `.gitignore` gets two new rules. TESTING.md is updated.

**Primary recommendation:** Place `assertGoldenMatch` in `gbkt-emulator` alongside `VisualDiff.kt` since it directly orchestrates capture + VisualDiff; tests that already depend on `gbkt-emulator` (all 8 visual UAT modules) gain it transitively. The `gbkt-test` module already declares `api(project(":gbkt-emulator"))` so `gbkt-test` consumers also see it.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Golden PNG storage | Test resource classpath (`src/test/resources/goldens/`) | — | Static committed assets, classpath-loaded by helper |
| Capture-to-scratch | `gbkt-emulator` (ScreenshotCapture) | — | Already owns capture; scratch dir is a constructor param |
| Pixel diff | `gbkt-emulator` (VisualDiff) | — | Existing; reused at tolerance=0.0 per D-04 |
| assertGolden helper | `gbkt-emulator` (agent package) | `gbkt-test` (if co-location preferred) | VisualDiff is co-located in gbkt-emulator; either works for consumers |
| Re-baseline gate | Gradle `tasks.test { systemProperty }` per module | — | Standard Gradle property propagation pattern |
| GBC auto-detect | `gbkt-emulator` (AgentSessionConfig.discoverFiles) | — | Single entry point for session discovery |
| gitignore / git rm | Root `.gitignore` | — | Repo-wide scratch rules |
| EVIDENCE_DIR removal | Each test class (33) | — | Mechanical, module-by-module |
| TESTING.md update | `context/TESTING.md` | — | Documentation file |

---

## Standard Stack

### Core (all existing — no new packages)

| Library / Class | Location | Purpose | Status |
|----------------|----------|---------|--------|
| `VisualDiff.compare()` | `gbkt-emulator/.../agent/VisualDiff.kt` | PNG diff engine, tolerance=0.0, emits red diff image | [VERIFIED: live code read] — already production code |
| `ScreenshotCapture.capture()` | `gbkt-emulator/.../agent/ScreenshotCapture.kt` | PNG + JSON sidecar capture to `outputDir` | [VERIFIED: live code read] — drop `capturedAt` at line 106 |
| `AgentSessionConfig.discoverFiles()` | `gbkt-emulator/.../agent/AgentSessionConfig.kt` | Convention-based ROM + symFile + metadata discovery | [VERIFIED: live code read] — add ROM 0x143 read at line 76 return block |
| `UatRunner` | `gbkt-emulator/.../agent/UatRunner.kt` | Existing goldenDir flow (left UNTOUCHED per D-03) | [VERIFIED: live code read] — prior art only, no changes |
| `javax.imageio.ImageIO` | JDK | PNG read/write — already used by VisualDiff and ScreenshotCapture | [VERIFIED: live code read] |
| `RandomAccessFile` or `FileInputStream` | JDK | Read ROM byte at offset 0x143 for CGB flag | [ASSUMED] — standard JDK; no new dep needed |

**No new external packages required.** [VERIFIED: live code read]

### New Code to Write

| New Artifact | Location (recommended) | Purpose |
|-------------|----------------------|---------|
| `GoldenAssertions.kt` (or inline `assertGoldenMatch`) | `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/` | thin helper: locate golden from classpath/module resources, capture to scratch, call VisualDiff, check sys prop |
| `GBKT_UPDATE_GOLDENS_PROP` constant | same file | system property key derived from `-Pgbkt.updateGoldens` |

**Module placement decision (Claude's Discretion):** `gbkt-emulator` is recommended because `VisualDiff` already lives there and all 8 visual UAT example test modules already declare `testImplementation(project(":gbkt-emulator"))`. The `gbkt-test` module declares `api(project(":gbkt-emulator"))`, so `gbkt-test` consumers also gain the helper transitively.

**Installation command:** No `npm install` / `pip install` — pure JDK + existing project modules. [VERIFIED: live code read]

---

## Package Legitimacy Audit

Not applicable — this phase introduces zero new external package dependencies. All functionality uses existing project modules (`gbkt-emulator`, `gbkt-test`) and JDK standard library (`java.io`, `javax.imageio`). [VERIFIED: live code read]

---

## Architecture Patterns

### System Architecture Diagram

```
Test run (./gradlew :gbkt-examples:metasprites:test)
  │
  ├─ [visual UAT test] calls assertGoldenMatch(rom="metasprites", anchor="elephant-cyan-subpalette")
  │     │
  │     ├─ load golden from src/test/resources/goldens/metasprites/elephant-cyan-subpalette.png
  │     │     (classpath resource or File path)
  │     │
  │     ├─ StepAgent.captureScreenshot(label) → scratch PNG in build/gbkt/screenshots/ [gitignored]
  │     │     (ScreenshotCapture.capture() — no capturedAt field)
  │     │
  │     ├─ if System.getProperty("gbkt.updateGoldens") != null
  │     │       → copy scratch PNG to golden path, PASS
  │     │         (guarded by D-07 GBC-header assert)
  │     │
  │     └─ else → VisualDiff.compare(golden, scratch, tolerance=0.0, diffOutputDir=build/)
  │               → DiffResult.match? PASS : FAIL with "X pixels differ, diff: build/…_diff.png"
  │
  ├─ [emission test] writes .txt to build/gbkt/test-evidence/<phase>/inv1.txt [gitignored]
  │     (EVIDENCE_DIR constant removed; replaced with build/ path)
  │
  └─ after all tests: git status shows zero new untracked files under .planning/

Gradle project property propagation:
  ./gradlew test -Pgbkt.updateGoldens
    │
    └─ tasks.test { systemProperty("gbkt.updateGoldens", "true") }  [in each module's build.gradle.kts]
          │
          └─ JVM sees System.getProperty("gbkt.updateGoldens") == "true" inside assertGoldenMatch

discoverFiles() GBC auto-detect (R5):
  AgentSessionConfig.discoverFiles(romFile)
    │
    ├─ read romFile.readBytes()[0x143]  (CGB flag byte)
    ├─ gbcMode = (cgbByte == 0x80 || cgbByte == 0xC0)
    └─ return AgentSessionConfig(..., gbcMode = gbcMode)
```

### Recommended Project Structure (new files only)

```
gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/
├── GoldenAssertions.kt        # new: assertGoldenMatch + sys-prop constants

gbkt-examples/metasprites/src/test/resources/goldens/
├── metasprites/
│   ├── elephant-cyan-subpalette.png    # migrated from Phase 19 evidence
│   ├── elephant-boot-frame.png         # migrated from Phase 19 evidence
│   └── rom-smoke-boot.png              # migrated from Phase 19 evidence
│   [+ 2 more from Phase 20 FIX-04]

gbkt-examples/platformer-template/src/test/resources/goldens/
├── platformer-template/
│   ├── anchor1-title.png               # migrated from Phase 21 evidence
│   ├── anchor1-gameplay.png
│   ├── anchor2-grounded.png
│   ... [15 anchor frames from Phase 21]
│   [+ 2 from Phase 20 FIX-04]

.gitignore (additions):
  .planning/phases/**/evidence/
  build/**/screenshots/
```

### Pattern 1: assertGoldenMatch helper

**What:** Thin helper that locates a golden PNG, captures to scratch, diffs or blesses.

**When to use:** In every visual UAT test that currently uses `captureAndRename(...)`.

**Implementation sketch:**
```kotlin
// Source: derived from UatRunner.checkpoint() golden-diff pattern (UatRunner.kt:194-208)
// and VisualDiff.compare() (VisualDiff.kt:58-94)
fun assertGoldenMatch(
    agent: StepAgent,
    label: String,
    goldenFile: File,           // resolved from src/test/resources/goldens/<rom>/<anchor>.png
    scratchDir: File,           // build/gbkt/screenshots/ (gitignored)
) {
    val captured = agent.captureScreenshot(label)  // → scratchDir
    val updateGoldens = System.getProperty("gbkt.updateGoldens") != null
    if (updateGoldens) {
        goldenFile.parentFile.mkdirs()
        captured.copyTo(goldenFile, overwrite = true)
        // D-07: GBC guard still fires before this point (caller must assert ROM is GBC)
        return  // PASS — golden written
    }
    if (!goldenFile.exists()) {
        throw AssertionError(
            "GOLDEN MISSING ${goldenFile.absolutePath} — " +
                "run ./gradlew test -Pgbkt.updateGoldens to bless it"
        )
    }
    val result = VisualDiff.compare(goldenFile, captured, tolerance = 0.0, diffOutputDir = scratchDir)
    if (!result.match) {
        throw AssertionError(
            "Golden mismatch: ${result.diffCount}/${result.totalPixels} pixels differ. " +
                "Diff image: ${result.diffImage?.absolutePath}\n" +
                "Expected: ${goldenFile.absolutePath}\n" +
                "Actual:   ${captured.absolutePath}"
        )
    }
}
```

### Pattern 2: Gradle property → JVM system property propagation

**What:** Pass `-Pgbkt.updateGoldens` through to the test JVM.

**When to use:** In each example module's `build.gradle.kts` and in `gbkt-backend-gbdk` and `gbkt-genre-platformer` (for emission test scratch redirect only — those don't need goldens but may need consistent build path patterns).

**Example:**
```kotlin
// In gbkt-examples/metasprites/build.gradle.kts (and each example that has visual UAT tests)
tasks.test {
    useJUnitPlatform()
    if (project.hasProperty("gbkt.updateGoldens")) {
        systemProperty("gbkt.updateGoldens", "true")
    }
}
```

**Why `hasProperty` not `findProperty`:** The property flag is boolean presence — it has no value, just existence. `hasProperty` is idiomatic; `findProperty("gbkt.updateGoldens") as? String` also works if a value is passed. [ASSUMED — based on standard Gradle usage; confirm against project's existing `project.hasProperty("release")` pattern in `build.gradle.kts`.]

**Existing precedent:** Root `build.gradle.kts` already uses `project.hasProperty("release")` for the version string. The same pattern applies here. [VERIFIED: root build.gradle.kts read]

### Pattern 3: ROM CGB-flag read

**What:** Read byte at offset 0x143 from ROM to determine GBC mode.

**When to use:** In `AgentSessionConfig.discoverFiles()` after the ROM file is validated.

```kotlin
// Source: SPEC R5 (22-SPEC.md), values from Nintendo GB Technical Reference
// CGB flag byte at 0x143: 0x80 = GBC-enhanced, 0xC0 = GBC-only
val cgbByte = romFile.inputStream().use { stream ->
    stream.skip(0x143)
    stream.read()  // returns -1 on EOF (short ROM — treat as DMG)
}
val gbcMode = cgbByte == 0x80 || cgbByte == 0xC0
```

**Edge case:** A 0-byte or very short ROM (impossible in practice for a valid GB ROM, which must be at least 32KB) returns -1 from `stream.read()` — treated as DMG. No special handling needed.

### Anti-Patterns to Avoid

- **Calling `System.currentTimeMillis()` in sidecars:** Drop `capturedAt` from `ScreenshotCapture.capture()` (line 106). The existing `ScreenshotCaptureTest.kt` assertions at lines 92-94 test `capturedAt` — update them to NOT assert the field (or assert the field is absent). [VERIFIED: ScreenshotCaptureTest.kt read]

- **`git rm -r .planning/phases/**/evidence/` without first cp'ing blessed goldens:** The 22 blessed PNG anchors from Phases 19/20/21 MUST be cp'd into `src/test/resources/goldens/` byte-identically BEFORE the `git rm` phase. Doing the rm first loses the binding baselines.

- **`gitignore` does not untrack:** `.gitignore` only prevents NEW files from being tracked. The 143 currently-tracked evidence files require explicit `git rm --cached` (or `git rm`) — they will NOT become untracked by adding `.planning/phases/**/evidence/` to `.gitignore` alone. [VERIFIED: fundamental git behavior]

- **Putting test resource goldens in `src/main/resources/`:** Goldens are test-only assets — they belong in `src/test/resources/goldens/`. Classpath loading in tests should use the test classpath. [ASSUMED — standard Kotlin/JVM test resource convention]

- **Using `InputStream` directly for ROM 0x143:** A ROM file can be up to 1MB+. Use `skip(0x143)` on an InputStream rather than reading all 32KB+ into memory just to access one byte. [ASSUMED — JVM best practice]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PNG pixel comparison | Custom pixel loop | `VisualDiff.compare()` (existing) | Already handles mismatch coords, red diff image, tolerance, 160×144 validation |
| PNG pixel comparison with region | Custom crop | `VisualDiff.compareRegion()` (existing) | PlatformerTemplateUatTest already uses it for anchor4 hflip gate |
| PNG read/write | Custom PNG codec | `javax.imageio.ImageIO` (JDK) | Already used throughout; produces deterministic PNG output |
| GBC flag detection | Complex ROM parsing | One-byte read at 0x143 | The CGB flag is a single byte in the ROM header — no GBDK parsing needed |

**Key insight:** Phase 22's test-infra additions are thin orchestration on top of already-written components. `VisualDiff` and `ScreenshotCapture` are complete; the new `assertGoldenMatch` helper is ~30 lines of wiring code.

---

## Runtime State Inventory

> Included because this phase is a migration involving tracked files, not a greenfield phase.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data (git-tracked evidence) | 143 files: 33 PNG + 77 txt + 22 JSON + 11 other (md, sha256, gitkeep) across 5 phase evidence dirs | `git rm` 121 non-blessed files; `cp` 22 blessed PNG anchors to `src/test/resources/goldens/` then `git rm` those phase-evidence originals |
| Blessed PNG anchors to preserve | 22 PNGs: 5 from Phase 19, 2 from Phase 20, 15 from Phase 21 | cp byte-identically to per-module `src/test/resources/goldens/<rom>/<anchor>.png` BEFORE git rm |
| Committed JSON sidecars | 22 `.json` sidecar files (all in Phases 19/20/21 evidence) | `git rm` — they are noise (capturedAt non-determinism); do NOT migrate to goldens |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | `.planning/phases/**/evidence/` dirs will continue to exist locally (gitignored) after git rm — harmless | `git rm -r` removes tracking; dirs may re-appear on next test run but are gitignored |

**Verified by:** `git ls-files ".planning/phases/**/evidence/**"` enumeration (143 total, 5 phase dirs). 33 PNG total: 22 from blessed phases 19/20/21, 11 from Phases 16/17 (non-visual evidence — markdown, sha256 checksums, audit txt files — git rm only, no golden migration needed).

**IMPORTANT:** The Phase 16 and Phase 17 evidence files (63 + 3 = 66 files) are NOT PNG screenshots — they are `.md`, `.txt`, and `.sha256` documentation files from the triage and docs-reconciliation phases. These are git rm'd as scratch, not migrated as goldens. [VERIFIED: git ls-files enumeration]

---

## EVIDENCE_DIR Test Class Enumeration (Critical)

**Total: 33 test classes** — confirmed by direct codebase scan. The CONTEXT.md says "33" and REQUIREMENTS.md says "27" — the live count is **33**. [VERIFIED: grep scan]

### VISUAL-UAT classes (8 classes — use capture + diff, write PNGs to EVIDENCE_DIR)

These classes use `captureAndRename()` + `AgentSessionConfig.discoverFiles().copy(gbcMode = true)` and write PNG + JSON sidecar to phase evidence dirs. These are the primary targets for `assertGoldenMatch` migration.

| Class | Module | Target Phase Evidence Dir |
|-------|--------|--------------------------|
| `Phase19VisualEvidenceTest` | `gbkt-examples/metasprites` | `19-codegen-fixes-metasprite-cluster/evidence` |
| `MetaspritePhase20OracleTest` | `gbkt-examples/metasprites` | `20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04` |
| `MetaspriteUatTest` | `gbkt-examples/metasprites` | `10-port-metasprites-gbdk-example-to-gbkt/evidence` |
| `PlatformerTemplateUatTest` | `gbkt-examples/platformer-template` | `21-codegen-fixes-platformer-and-remaining-seeds/evidence/uat-screenshots` |
| `PlatformerTemplatePhase20OracleTest` | `gbkt-examples/platformer-template` | `20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04` |
| `PlatformerTemplate128UatTest` | `gbkt-examples/platformer-template` | `12.8-grass-tileset-white-pixels-diagnostic/evidence` |
| `SimplePhysicsUatTest` | `gbkt-examples/simple-physics` | `09.4-.../evidence` |
| `BanksUatTest` | `gbkt-examples/banks` | `11-.../evidence` |

**Note:** All 8 visual UAT classes depend on `gbkt-emulator` via `testImplementation`. After discoverFiles() auto-detect lands, the `.copy(gbcMode = true)` calls in the 3 GBC-target classes (metasprites, platformer-template) are removed.

### EMISSION-ONLY classes (25 classes — write .txt dumps to EVIDENCE_DIR, no PNG)

These classes write `.txt` C-code snippets for human review. The in-test C assertion is the real gate. They are redirected to `build/gbkt/test-evidence/` (gitignored) — no golden migration needed.

**In `gbkt-backend-gbdk/src/test/`:**
1. `AutoExitSynthesisTest` → `13.5-.../evidence/tier1-shape`
2. `BindCurrentLevelEmissionTest` → `13.5-.../evidence/tier1-shape`
3. `LevelCardSceneEmissionTest` → `12.6-.../evidence`
4. `LevelSwitchEmissionTest` → `12-port-.../evidence`
5. `ScreenPrimitiveEmissionTest` → (near-untracked phase)
6. `TitleSceneEmissionTest` → `12-port-.../evidence`
7. `CLiteralAuditScanTest` → `07.9-.../evidence`
8. `MetaspriteCameraOffsetEmissionTest` → `12.3-.../evidence`
9. `SignedComparisonLiteralEmissionTest` → `07.9-.../evidence`

**In `gbkt-genre-platformer/src/test/`:**
10. `HorizontalScrollEmissionTest` → `12-port-.../evidence`
11. `JumpHoldEmissionTest` → `12-port-.../evidence`
12. `LevelEndTriggerGroundedGuardEmissionTest` → `12.7-.../evidence`
13. `PlatformerCameraCallSiteEmissionTest` → `12.3-.../evidence`
14. `PlatformerInputEmissionTest` → `12.3-.../evidence`
15. `PlatformerPhysicsSnapToTileTopEmissionTest` → `12.7-.../evidence`
16. `PlatformerWalkCycleEmissionTest` → `12.3-.../evidence`
17. `TilemapCollisionEmissionTest` → `12-port-.../evidence`
18. `TilemapPhysicsPlayerSymbolEmissionTest` → (untracked phase)

**In `gbkt-examples/*/src/test/`:**
19. `MetaspriteEmissionTest` (`metasprites`) → `10-port-.../evidence`
20. `PlatformerTemplateEmissionTest` (`platformer-template`) → `12-port-.../evidence`
21. `PlayerMetaspriteGeometryTest` (`platformer-template`) → `12.5-.../evidence`
22. `SimplePhysicsEmissionTest` (`simple-physics`) → `09-port-.../evidence`
23. `PongNoExitRegressionTest` (`pong`) → (untracked phase)
24. `BreakoutNoExitRegressionTest` (`breakout`) → (untracked phase)
25. `BanksEmissionTest` (`banks`) → `11-port-.../evidence`

**Key observation:** `gbkt-genre-platformer` tests do NOT depend on `gbkt-emulator` (only on `gbkt-core`). They are pure emission tests. Their EVIDENCE_DIR redirect to `build/` requires only updating the path constant — no `assertGoldenMatch` migration needed.

Similarly, `gbkt-backend-gbdk` tests depend on `gbkt-emulator` but only for classpath completeness (they test codegen via GBDKBackend, not the emulator). They are all emission-only.

### MIXED class (1 class)

| Class | Module | Notes |
|-------|--------|-------|
| `MetaspritePhase20OracleTest` | `gbkt-examples/metasprites` | Visual UAT (uses capture), but ALSO writes a `.txt` perceptual-check artifact to EVIDENCE_DIR. Both the PNG and the `.txt` must be redirected. |

---

## Blessed PNG Anchors to Migrate (22 files)

These are the exact files that must be cp'd byte-identically to `src/test/resources/goldens/`:

### From `gbkt-examples/metasprites` (target: `src/test/resources/goldens/metasprites/`)

| Current path (in Phase 19 evidence) | Proposed golden anchor name |
|--------------------------------------|----------------------------|
| `.planning/phases/19-.../evidence/SEED-004/screenshot.png` | `elephant-boot-seed004.png` |
| `.planning/phases/19-.../evidence/SEED-005/screenshot.png` | `elephant-boot-seed005-checkerboard.png` |
| `.planning/phases/19-.../evidence/SEED-006/screenshot.png` | `elephant-cyan-subpalette.png` |
| `.planning/phases/19-.../evidence/SEED-013/screenshot.png` | `elephant-gbc-colors.png` |
| `.planning/phases/19-.../evidence/ROM-smoke/screenshot.png` | `rom-smoke-boot.png` |
| `.planning/phases/20-.../evidence/fix-04/metasprites-sprite-outline.png` | `elephant-sprite-outline-clean.png` |

**Note:** Planner should propose descriptive names per D-02; the names above are suggestions. USER review at verify phase confirms them.

### From `gbkt-examples/platformer-template` (target: `src/test/resources/goldens/platformer-template/`)

| Current path (in Phase 21 evidence) | Proposed golden anchor name |
|--------------------------------------|----------------------------|
| `.planning/.../21-.../evidence/uat-screenshots/anchor-1/01-title.png` | `anchor1-title.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-1/02-gameplay.png` | `anchor1-gameplay.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-2/01-grounded.png` | `anchor2-grounded.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-2/02-mid-jump.png` | `anchor2-mid-jump.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-2/03-landed.png` | `anchor2-landed.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-3/01-initial.png` | `anchor3-initial.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-3/02-scrolled.png` | `anchor3-scrolled.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-4/01-walk-frame-0.png` | `anchor4-walk-frame-0.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-4/02-walk-frame-1.png` | `anchor4-walk-frame-1.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-4/03-walk-frame-2.png` | `anchor4-walk-frame-2.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-4/04-facing-left.png` | `anchor4-facing-left.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-5/00-last-gameplay.png` | `anchor5-last-gameplay.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-5/01-nextlevel-flip.png` | `anchor5-nextlevel-flip.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-5/02-nextlevel-card.png` | `anchor5-nextlevel-card.png` |
| `.planning/.../21-.../evidence/uat-screenshots/anchor-5/03-level-2.png` | `anchor5-level-2.png` |
| `.planning/.../20-.../evidence/fix-04/platformer-player-transparency.png` | `platformer-player-transparency.png` |

**Total: 22 blessed PNG anchors** (6 metasprites + 16 platformer-template). [VERIFIED: git ls-files enumeration]

---

## GBC-Target Examples (R5 scope)

**GBC-target examples (need CGB flag 0x80/0xC0 in ROM header, need GBC mode capture):**
1. `gbkt-examples/metasprites` — uses `GbcTarget.GBC_COMPATIBLE` [VERIFIED: Metasprites.kt read]
2. `gbkt-examples/metasprites-stress` — also uses `GbcTarget.GBC_COMPATIBLE` [VERIFIED: codebase scan]
3. `gbkt-examples/platformer-template` — also uses `GbcTarget.GBC_COMPATIBLE` [VERIFIED: PlatformerTemplate.kt referenced in CLAUDE.md]

**DMG-only examples (ROM header byte 0x143 == 0x00 or other, gbcMode stays false):**
- `pong`, `breakout`, `simple-physics`, `banks` [VERIFIED: no GbcTarget reference in their source]

After `discoverFiles()` auto-detect lands, the `GbktTestExtension(gbcMode = true)` constructor param in tests for GBC examples becomes redundant — but it is safe to leave in place (the auto-detect sets it; the explicit param just confirms it). The `.copy(gbcMode = true)` calls in bespoke visual UAT tests ARE the target for removal per D-05 and commit `71dd3a57`.

**GBC-header assertion (D-07):** In GBC-target test classes, after creating the agent, assert `agentConfig.gbcMode == true` with message "ROM 0x143 CGB flag not set — is this a DMG ROM? Aborting to prevent inverted-palette golden bless." This gates the update-golden path (D-07 guarded bless).

---

## Common Pitfalls

### Pitfall 1: gitignore does not untrack committed files

**What goes wrong:** Developer adds `.planning/phases/**/evidence/` to `.gitignore` and expects all 143 tracked files to disappear from `git status`. They do not.

**Why it happens:** `.gitignore` prevents NEW (untracked) files from being staged, but has zero effect on files already tracked in the index.

**How to avoid:** Always run `git rm --cached` (or `git rm` if also deleting from working tree) for each tracked file before or alongside adding the `.gitignore` rule. In this phase: `git rm -r .planning/phases/**/evidence/` (or selective per-file). The planner should structure this as an explicit `git rm` task in the plan.

**Warning signs:** `git ls-files ".planning/phases/**/evidence/**"` still returns files after the gitignore-only commit.

### Pitfall 2: byte-identity loss during golden migration

**What goes wrong:** The "migration" copies a PNG through an image-processing library that re-encodes it (even at the same settings), changing the byte stream and making the subsequent `VisualDiff.compare()` at tolerance=0.0 fail even though the image looks visually identical.

**Why it happens:** PNG encoding is not fully deterministic across different encoders — filter choices, compression level, ancillary chunks differ.

**How to avoid:** Use a raw file copy (`File.copyTo()` or `Files.copy()`) — NOT `ImageIO.read()` + `ImageIO.write()`. The bytes of the golden must match the bytes that `ScreenshotCapture.capture()` produces, since capture also uses `ImageIO.write()` with the same JDK PNG encoder. The existing Phase 19/21 PNGs were produced by `ScreenshotCapture.capture()`, so a raw copy preserves them correctly.

**Warning signs:** `VisualDiff.compare()` reports a mismatch of N pixels immediately after migration, even though the images look identical visually.

### Pitfall 3: `capturedAt` test in ScreenshotCaptureTest.kt

**What goes wrong:** The planner drops `capturedAt` from `ScreenshotCapture.capture()` (line 106) but forgets to update `ScreenshotCaptureTest.kt` lines 92-94, which assert `capturedAt >= beforeCapture` and `capturedAt <= afterCapture`. The test fails.

**Why it happens:** The CONTEXT.md explicitly calls out this coupling (D-08), but it's easy to miss in a large mechanical migration.

**How to avoid:** The plan for "drop capturedAt from ScreenshotCapture" MUST include updating `ScreenshotCaptureTest.kt` in the same commit. The `JSON sidecar contains required fields` test (lines 76-96) asserts `capturedAt` — remove that assertion and add an assertion that `capturedAt` key is absent from the JSON, or simply remove the field-presence check.

**Warning signs:** `:gbkt-emulator:test` RED after the capturedAt change; error on line 93-94 of ScreenshotCaptureTest.

### Pitfall 4: PlatformerTemplateUatTest.anchor4 uses VisualDiff.compareRegion, not captureAndRename-then-diff

**What goes wrong:** The planner treats anchor4 as a simple "captureAndRename → assertGoldenMatch" swap, but the test uses `VisualDiff.compareRegion()` with OAM-derived bounding box coordinates (a HIGH/LOW gate). The region crop depends on live OAM data at capture time, making the comparison non-deterministic across ROM builds (player position may shift).

**Why it happens:** `PlatformerTemplateUatTest.anchor4MetaspriteAnimation()` uses a unique per-pixel OAM-region hflip gate that is inherently position-dependent.

**How to avoid:** Anchor 4 PNGs (4 walk frames) ARE migrated to goldens — the golden diff applies to the full-frame PNGs. The OAM-region `compareRegion` gate is kept as-is (it operates on already-captured PNGs, not the golden). The `captureAndRename()` calls in anchor4 are replaced with `captureAndRename() + assertGoldenMatch()`. The `compareRegion` HIGH/LOW gate runs after that. Order is preserved.

**Warning signs:** Anchor4 test fails on the region diff — that means the player moved to a different OAM position between baseline and test run (a test isolation issue, not a golden issue).

### Pitfall 5: Emission tests in non-example modules cannot use build/ of the example

**What goes wrong:** Emission tests in `gbkt-genre-platformer/src/test/` and `gbkt-backend-gbdk/src/test/` redirect their EVIDENCE_DIR to a `build/` path. The `user.dir` in those modules at test time resolves to the GENRE/BACKEND module root, not an example module. The scratch path must be `<module>/build/gbkt/test-evidence/`, not a cross-module path.

**Why it happens:** These modules currently use relative paths like `"../.planning/phases/..."` from `System.getProperty("user.dir")`. The replacement scratch path must similarly be relative to the correct module root.

**How to avoid:** Use `System.getProperty("user.dir")` + `"build/gbkt/test-evidence/<class-name>/"` for the new scratch EVIDENCE_DIR in each emission test class. This resolves to the module's own build directory, which is always gitignored via the root `.gitignore`'s `build/` pattern.

**Warning signs:** Tests write to an unexpected path; a `build/` directory appears in the wrong location.

### Pitfall 6: Worktree / sequential test run collision

**What goes wrong:** Two test runs execute in parallel on the same machine, both trying to write to the same screenshot scratch dir (e.g. `build/gbkt/screenshots/`). The rename in `captureAndRename` may race with an in-progress capture.

**Why it happens:** The current `captureAndRename` helper deletes the target before renaming. If two tests race to the same target name, one rename fails.

**How to avoid:** The new `assertGoldenMatch` helper writes captures to a test-class-specific subdirectory (e.g. `build/gbkt/screenshots/<ClassName>/`) rather than a flat dir. The existing worktree-sequential mode (see memory `feedback_claude_code_worktree_drift_quirks`) already serializes plan execution, so this risk is low in practice. Document it as a future improvement (not a blocker).

---

## Code Examples

### Example 1: Updated discoverFiles() with GBC auto-detect

```kotlin
// Source: AgentSessionConfig.kt companion (modified for R5)
fun discoverFiles(romFile: File, screenshotDir: File? = null): AgentSessionConfig {
    val outputDir = romFile.parentFile
    val gbktDir = outputDir?.parentFile
    val generatedDir = gbktDir?.let { File(it, "generated") }
    val baseName = romFile.nameWithoutExtension

    val symFile = outputDir?.let { File(it, "$baseName.noi") }?.takeIf { it.exists() }
        ?: outputDir?.let { File(it, "$baseName.sym") }?.takeIf { it.exists() }
    val metadataFile = generatedDir?.let { File(it, "game_metadata.json") }?.takeIf { it.exists() }
    val sourceMapsDir = generatedDir?.takeIf { it.exists() }

    // R5: read CGB flag byte at ROM offset 0x143
    val gbcMode = romFile.inputStream().use { stream ->
        stream.skip(0x143)
        val cgbByte = stream.read()
        cgbByte == 0x80 || cgbByte == 0xC0
    }

    return AgentSessionConfig(
        romFile = romFile,
        symFile = symFile,
        metadataFile = metadataFile,
        sourceMapsDir = sourceMapsDir,
        screenshotDir = screenshotDir ?: File(outputDir ?: File("."), "screenshots"),
        gbcMode = gbcMode,  // auto-detected from ROM header
    )
}
```

### Example 2: Updated ScreenshotCapture.capture() without capturedAt

```kotlin
// Source: ScreenshotCapture.kt (modified for D-08)
// Line 102-120 region — remove .put("capturedAt", System.currentTimeMillis())
val sidecar = JSONObject()
    .put("frameNumber", frameNumber)
    .put("label", label)
    // capturedAt REMOVED — was non-deterministic; caused sidecar churn on every test run
    .put("variables", variables)
```

### Example 3: Emission test EVIDENCE_DIR replacement (generic pattern)

```kotlin
// Before (writes to committed phase dir):
val EVIDENCE_DIR = File(System.getProperty("user.dir"))
    .resolve("../../.planning/phases/12-port-.../evidence/tier1-shape")
    .normalize()

// After (writes to gitignored build/ dir):
val EVIDENCE_DIR = File(System.getProperty("user.dir"))
    .resolve("build/gbkt/test-evidence")
    .normalize()
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Evidence in `.planning/phases/**/evidence/` as committed + overwritten on every test run | Goldens in `src/test/resources/goldens/` (committed once, read-only) + scratch in `build/` (gitignored) | Closed phases never resurface; no capturedAt churn |
| `captureAndRename()` + `capturedAt` timestamp | `assertGoldenMatch()` + no timestamp | Deterministic; captures are compared not replaced |
| Manual `gbcMode = true` per test | Auto-detect from ROM 0x143 | GBC-wrong-mode capture class eliminated |

**Deprecated/outdated:**
- `EVIDENCE_DIR` companion pattern: all 33 instances removed
- `.copy(gbcMode = true)` per-test workaround: removed after discoverFiles() auto-detect
- `capturedAt` field in JSON sidecar: dropped (zero production consumers)

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `project.hasProperty("gbkt.updateGoldens")` is the idiomatic Gradle pattern for boolean flags | Architecture Patterns / Pattern 2 | Minor — `findProperty` also works; easy to swap |
| A2 | Golden PNG from `src/test/resources/` is loaded via classpath or File path relative to module root | Architecture Patterns | Medium — if classpath loading is used, packaging in `src/test/resources` requires verifying Gradle test resource inclusion; a File-based approach is simpler and avoids classpath issues |
| A3 | `InputStream.skip(0x143)` + `stream.read()` is sufficient for ROM 0x143 read (no buffered stream needed) | Architecture Patterns / Pattern 3 | Low — standard JDK; correct for local ROM files; not an issue for GB ROM sizes |
| A4 | The 15 Phase 21 platformer-template anchor PNGs are all confirmed USER-blessed baselines | Blessed PNG Anchors section | HIGH if wrong — any non-blessed PNG migrated as a golden becomes a wrong baseline; planner must gate migration on explicit anchor list confirmed by USER |
| A5 | `gbkt-genre-platformer` tests can use `System.getProperty("user.dir") + "build/gbkt/test-evidence"` for scratch | Pitfall 5 | Low — confirmed by existing EVIDENCE_DIR pattern using user.dir |
| A6 | Anchor4's `compareRegion` OAM-region hflip gate does NOT need golden migration — it compares two already-captured PNGs from the same test run | Pitfall 4 | Medium — if the planner misunderstands and tries to golden-ize the region diff, it becomes position-dependent |

---

## Open Questions (RESOLVED)

> All three resolved by planner decisions (2026-06-14): Q1 → classpath loading via `javaClass.getResource("/goldens/...")` (plans 22-06/07); Q2 → per-module `systemProperty` wiring (plan 22-03); Q3 → `compareRegion` preserved as supplemental gate alongside `assertGoldenMatch` (plan 22-07).

1. **Golden file resolution: classpath resource vs File path?**
   - What we know: `src/test/resources/` is on the test classpath in Gradle; classpath resources are accessed via `javaClass.getResourceAsStream()` or similar.
   - What's unclear: Whether the assertGoldenMatch helper should use classpath loading (portable) or `File("src/test/resources/goldens/...")` relative path (simpler but depends on `user.dir` being the module root, which it is in Gradle but may not be in IDE).
   - Recommendation: Use classpath loading via `javaClass.getResource("/goldens/<rom>/<anchor>.png")?.let { File(it.toURI()) }` — portable across Gradle and IDE runs. On miss, fall back to a clear error (not NPE). The `src/test/resources/` is included in the test jar so the resource is always present when tests run.

2. **`-Pgbkt.updateGoldens` wiring: per-module or root build?**
   - What we know: Root `build.gradle.kts` does NOT configure `tasks.test` globally (no `subprojects { tasks.test { ... } }` block). Each module configures its own `tasks.test { useJUnitPlatform() }`.
   - What's unclear: Whether adding `systemProperty` to the root allprojects/subprojects block is cleaner than updating each module's `build.gradle.kts` individually.
   - Recommendation: Add to each module that has visual UAT tests (8 example modules). The gbkt-genre-platformer and gbkt-backend-gbdk emission tests do NOT need this property (they have no goldens). A root allprojects block is acceptable but adds the property to ALL test tasks even where not needed.

3. **Does anchor4's VisualDiff.compareRegion gate survive the migration?**
   - What we know: Anchor4 captures 4 PNGs (walk-frame-0, walk-frame-1, walk-frame-2, facing-left) AND runs `compareRegion(walk0, facingLeft, box)`. The golden for walk-frame-0 and facing-left exist.
   - What's unclear: The `compareRegion` gate uses live OAM bounding box coordinates computed at test time. If the player spawn position changes (e.g. from a future codegen change), the box shifts and the region diff changes. This is correct behavior — a codegen change should fail the golden if the visual changes.
   - Recommendation: Keep `compareRegion` as a supplemental gate; add `assertGoldenMatch` for each of the 4 frame PNGs as the primary golden gate. The region gate remains as an animation-specific structural check.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | All Kotlin compilation | ✓ | 21 (per build config) | — |
| Kotlin 2.3.20 | All Kotlin code | ✓ | Per `gradle.properties` | — |
| Gradle 9.5.1 | Build system | ✓ | Per `gradle-wrapper.properties` | — |
| `javax.imageio.ImageIO` | PNG read/write | ✓ | JDK built-in | — |
| `java.io.InputStream` | ROM 0x143 read | ✓ | JDK built-in | — |
| GBDK / lcc | ROM rebuild (not needed for this phase) | Unknown | N/A | N/A — phase is storage-only; no ROM rebuild needed for the migration itself |
| `git` CLI | `git rm` for tracked evidence files | ✓ | Repo is a git repo | — |

**Missing dependencies with no fallback:** None — this phase requires no external tools beyond the standard JDK and Gradle.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) |
| Config file | Each module: `tasks.test { useJUnitPlatform() }` in `build.gradle.kts` |
| Quick run command | `./gradlew :gbkt-emulator:test :gbkt-examples:metasprites:test` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| R1 | No EVIDENCE_DIR in src/test | grep gate | `grep -rl "EVIDENCE_DIR" --include="*.kt" . \| grep "src/test"` → 0 results | ✅ acceptance criterion |
| R1 | No .planning/phases/**/evidence path in src/test | grep gate | `grep -rl "planning/phases" --include="*.kt" . \| grep "src/test"` → 0 results | ✅ acceptance criterion |
| R2 | Visual UAT tests fail on golden mismatch (1+ pixel) | unit/integration | `./gradlew :gbkt-examples:metasprites:test` with modified golden | ❌ Wave 0: new assertGoldenMatch tests |
| R2 | Normal test run does not modify committed golden | clean-tree assertion | `git diff --exit-code` after `./gradlew test` | ✅ acceptance criterion |
| R3 | Emission tests write no files under .planning/ | grep + file-system check | `find .planning/phases -name "*.txt" -newer` before/after test run | ✅ acceptance criterion |
| R4 | `./gradlew test` without flag = no golden writes | integration | Run tests, check `git diff` shows no golden changes | ✅ acceptance criterion |
| R4 | `./gradlew test -Pgbkt.updateGoldens` = golden written | integration | Run with flag, verify golden file updated | ❌ Wave 0: new update-goldens test |
| R5 | discoverFiles() sets gbcMode=true for GBC ROM | unit | `./gradlew :gbkt-emulator:test --tests "*AgentSessionConfigTest*"` | ❌ Wave 0: new discoverFiles test |
| R5 | discoverFiles() sets gbcMode=false for DMG ROM | unit | Same test class | ❌ Wave 0: new test case |
| R5 | .copy(gbcMode = true) absent from bespoke tests | grep gate | `grep -rl "gbcMode = true" --include="*.kt" . \| grep "src/test"` → 0 results | ✅ acceptance criterion |
| R6 | `git ls-files ".planning/phases/**/evidence/**"` = 0 files | clean-tree assertion | `git ls-files ".planning/phases/**/evidence/**"` | ✅ acceptance criterion |
| R6 | 22 blessed anchors in `src/test/resources/goldens/` | file existence | `find . -path "*/src/test/resources/goldens/**/*.png" -type f \| wc -l` = 22 | ❌ Wave 0: create goldens dirs |
| R7 | TESTING.md has goldens layout section | grep | `grep -l "goldens" context/TESTING.md` | ✅ acceptance criterion |
| R7 | TESTING.md has no EVIDENCE_DIR reference | grep | `grep "EVIDENCE_DIR" context/TESTING.md` → 0 results | ✅ acceptance criterion |

### Sampling Rate

- **Per task commit:** `./gradlew :gbkt-emulator:test` (fast; covers ScreenshotCapture + discoverFiles unit tests)
- **Per wave merge:** `./gradlew test` (full suite across all modules)
- **Phase gate:** Full suite green + `git diff --exit-code` shows no modified tracked evidence after a fresh `./gradlew test`

### Wave 0 Gaps (infrastructure required before main migration)

- [ ] `AgentSessionConfigTest.kt` — covers R5 (discoverFiles GBC auto-detect for GBC and DMG ROMs); needs a 320-byte synthetic ROM with 0x143 = 0x80 (GBC) and one with 0x00 (DMG)
- [ ] `GoldenAssertions.kt` — new file; at least one unit test validating the missing-golden failure message and the update-golden write path
- [ ] `.gitignore` update — `.planning/phases/**/evidence/` and `build/**/screenshots/` rules
- [ ] `src/test/resources/goldens/` dir skeletons in metasprites and platformer-template modules
- [ ] `ScreenshotCaptureTest.kt` updated — remove capturedAt assertions (lines 92-94), add capturedAt-absent check

*(If no gaps: "None" — but here Wave 0 gaps are real prerequisites for all subsequent migration tasks.)*

---

## Security Domain

Security enforcement is not applicable to this phase. Phase 22 touches only test infrastructure and gitignore patterns — no authentication, session management, access control, cryptography, or user input handling. The phase does not modify any production code paths. The `discoverFiles()` change reads a local ROM file (existing trust boundary). The Gradle property propagation is a standard build-system pattern with no security implications. [VERIFIED: SPEC.md Boundaries section; phase description]

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| Run `:module:spotlessApply :module:detekt` per commit | Every commit touching `ScreenshotCapture.kt`, `AgentSessionConfig.kt`, `GoldenAssertions.kt`, or test classes must include spotless + detekt |
| No magic strings | `GOLDEN_PROP_KEY = "gbkt.updateGoldens"` must be a named constant, not an inline string literal |
| Visual evidence requires screenshots | When verifying R2 (golden mismatch causes test failure), evidence must include a screenshot of the test failure output — a variable assertion alone is insufficient |
| Clean `:gbkt-examples:<game>:buildRom` smoke for codegen-touching changes | The `discoverFiles()` GBC-detect change touches the capture flow; run `./gradlew :gbkt-examples:metasprites:buildRom :gbkt-examples:platformer-template:buildRom` as a sanity smoke during verification |
| `./gradlew pluginTest` is the correct plugin integration test command | Not applicable — this phase does not touch the Gradle plugin |
| `./gradlew test` before declaring phase complete | Full suite green required |

---

## EVIDENCE_DIR Discrepancy Resolution

**"27 test classes" (REQUIREMENTS.md and STATE.md) vs "33 test classes" (CONTEXT.md and SPEC.md):**

The live count is **33 classes**. [VERIFIED: `grep -rl "EVIDENCE_DIR" --include="*.kt" . | grep "src/test"` returns exactly 33 paths]

The "27" count in REQUIREMENTS.md and STATE.md predates the Phase 22 SPEC. During Phases 18–21, additional emission tests were added to `gbkt-genre-platformer` (9 tests) and `gbkt-backend-gbdk` (9 tests) as each feature was implemented, growing the count. The SPEC's 33 reflects the final accurate count. The planner should use **33** as the authoritative class count.

---

## Sources

### Primary (HIGH confidence — live code read)

- `/gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VisualDiff.kt` — complete diff engine verified; `compare()` and `compareRegion()` signatures confirmed
- `/gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt` — `capturedAt` at line 106 confirmed
- `/gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt` — `discoverFiles()` at line 63 confirmed; gbcMode not set
- `/gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/UatRunner.kt` — golden flow (lines 44, 194-208) confirmed; left untouched
- `/gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/ScreenshotCaptureTest.kt` — `capturedAt` assertions at lines 92-94 confirmed
- `/gbkt-examples/metasprites/src/test/kotlin/.../Phase19VisualEvidenceTest.kt` — representative bespoke visual UAT pattern confirmed; `captureAndRename` + `.copy(gbcMode=true)` present
- `/gbkt-examples/platformer-template/src/test/kotlin/.../PlatformerTemplateUatTest.kt` — `compareRegion` anchor4 OAM gate + EVIDENCE_DIR pattern confirmed
- `grep -rl "EVIDENCE_DIR" --include="*.kt" . | grep "src/test"` — exactly 33 files found
- `git ls-files ".planning/phases/**/evidence/**"` — 143 tracked files: 33 PNG + 77 txt + 22 JSON + 11 other across 5 phase dirs

### Secondary (MEDIUM confidence — cited from project docs)

- `.planning/phases/22-golden-screenshot-and-evidence-storage-overhaul/22-CONTEXT.md` — D-01..D-08 locked decisions
- `.planning/phases/22-golden-screenshot-and-evidence-storage-overhaul/22-SPEC.md` — 7 locked requirements
- `CLAUDE.md` — project constraints, build commands, multi-module architecture
- `context/TESTING.md` — current evidence convention (target of R7 update)

### Tertiary (LOW confidence — training knowledge applied)

- Gradle `hasProperty`/`systemProperty` propagation pattern [A1]
- JDK `InputStream.skip()` for ROM byte access [A3]
- Classpath resource loading in JVM tests [A2]

---

## Metadata

**Confidence breakdown:**
- EVIDENCE_DIR class enumeration: HIGH — direct live grep confirmed 33 classes
- Blessed anchor file list: HIGH — git ls-files confirmed 22 PNG anchors in phases 19/20/21
- VisualDiff / ScreenshotCapture code structure: HIGH — files read directly
- Gradle property propagation: MEDIUM — training knowledge; existing `release` property precedent validates the pattern
- Golden classpath loading: LOW — assumed from JVM test conventions; open question raised

**Research date:** 2026-06-14
**Valid until:** 2026-07-14 (stable framework; only risk is new test classes added before planning begins)
