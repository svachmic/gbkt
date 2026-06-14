# Phase 20: Codegen Fixes — Banks and Sprite Transparency - Research

**Researched:** 2026-06-14
**Domain:** Confirmation/regression-guard — JVM emission tests, UAT StepAgent screenshot harness, byte-identity oracle
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Produce a standalone `20-AUDIT-FIX-03.md` in the phase dir (mirrors `19-AUDIT-FIX-02.md`). Table columns: seed → guarding test file → assertion name → existing-or-newly-authored → reverted-fix scenario. SEED-014 → INV-2 + INV-6; SEED-015 → INV-5; SEED-016 → Anchor 4.
- **D-02 (ordering gate, locked):** Re-verify SEED-014 FIRST — run `BanksEmissionTest` fresh to GREEN to confirm the `hasZoneSceneBinder` guard (`GBDKPipeline.kt`) already satisfies INV-2/INV-6 on current master, before authoring any guard-gap work.
- **D-03:** Audit existing coverage FIRST; author new guards only where a seed has no existing guarding assertion — no duplicate coverage (Phase 19 D-05 pattern). Each new guard asserts the fixed behavior (GREEN) with a comment naming the reverted-fix scenario (RED-by-design). No revert→RED demonstration required.
- **D-04:** Capture D-08 visual oracle by reusing/extending the JVM `*UatTest` StepAgent `captureAndRename()` harness, emitting PNGs to the phase `evidence/` dir. Two screenshots: (1) sprite-outline rendering clean from the metasprites example; (2) platformer-template player-transparency twin shot.
- **D-05 (constraint, locked):** Captures MUST run in each example's correct target mode — `gbcMode=true` with the `.noi` symFile for any GBC-target example (platformer-template is GBC; verify metasprites example's target before capture).
- **D-06:** Byte-identity — two-tier: (1) per-commit procedural same-session hash diff on affected examples (banks, metasprites, platformer-template); (2) one full 7-example sweep at phase close to satisfy Criterion 5.
- **D-07:** Every Phase 20 commit contains only banks/tRNS confirmation work — zero S3776/PR-#77 refactors interleaved.
- **D-08 (constraint):** Executors must run `:module:spotlessApply :module:detekt` per-commit — `:module:test` and the pre-commit hook do NOT run them.

### Claude's Discretion

- Exact test method/assertion names, evidence PNG filenames, the precise hashing command for the byte-identity diffs, and whether any FIX-03 guard gap actually requires a new assertion (the audit may find full existing coverage) are left to the planner/executor.

### Deferred Ideas (OUT OF SCOPE)

- Platformer `cEmit()` escapes and remaining DSL/tooling seeds → Phase 21.
- Merging PR #77 (S3776 burn-down) → held open until Phases 19/20/21 complete.
- 13.8 WR follow-ups (WR-01/02/03), configbuilder-cartridge-setter, easetozero, orelse, compileRom-warning — all deferred per CONTEXT.md.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FIX-03 | Banks trio closure — re-verify SEED-014/015/016 VERIFIED-ALREADY-FIXED at HEAD; produce 20-AUDIT-FIX-03.md mapping seeds to guarding assertions; re-run BanksEmissionTest fresh to GREEN; build banks.gb and re-run BanksUatTest Anchor 4 for SEED-016 | INV-2/INV-6 in BanksEmissionTest.kt (SEED-014), INV-5 in BanksEmissionTest.kt (SEED-015), Anchor 4 in BanksUatTest.kt (SEED-016); hasZoneSceneBinder guard at GBDKPipeline.kt:1428; run commands documented below |
| FIX-04 | tRNS sprite-outline visual oracle — capture HEAD runtime screenshots confirming elephant outline renders clean (metasprites) and player-transparency shows no regression (platformer-template); embed in phase evidence/ dir | captureAndRename harness documented; GBC-mode requirement and .noi auto-discovery documented; Phase 13.6 tRNS auto-route location confirmed at ConvertSpritesTask.kt:328-372 |

</phase_requirements>

---

## Summary

Phase 20 is a confirmation/regression-guard phase identical in structure to Phase 19. All four seeds (SEED-014/015/016/SEED-PHASE-13-SPRITE-OUTLINE) were dispositioned VERIFIED-ALREADY-FIXED by Phase 16 triage. No production codegen change is expected or permitted.

**FIX-03:** `BanksEmissionTest.kt` already contains INV-2, INV-5, and INV-6 guarding the three banks seeds. `BanksUatTest.kt` contains Anchor 4 guarding SEED-016. The audit will find full existing coverage — the same D-05 no-duplicate outcome as Phase 19 FIX-02. The D-02 ordering gate must run `BanksEmissionTest` fresh to GREEN before authoring any guard-gap work. Anchor 4 requires a ROM build.

**FIX-04:** Two runtime screenshots must be captured using the JVM `StepAgent` `captureAndRename()` harness. The metasprites example exercises the Phase 13.6 tRNS auto-route (`ConvertSpritesTask.kt:328-372`); the platformer-template provides the regression-guard twin shot. Both examples target `GbcTarget.GBC_COMPATIBLE`; `gbcMode=true` is required for the platformer-template (per D-05 and `learning_platformer_mcp_needs_gbc_mode`). The metasprites target is also GBC_COMPATIBLE — the planner should decide the capture mode (DMG is sufficient for transparency-specific check; GBC is the authentic target mode for this game).

**Primary recommendation:** Run `./gradlew :gbkt-examples:banks:test` (INV-2/INV-5/INV-6) FIRST to confirm D-02, then produce the audit doc, then build banks ROM + run Anchor 4, then capture FIX-04 screenshots, then run byte-identity sweep.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| FIX-03 emission guards | JVM test layer (`gbkt-examples/banks`) | Pipeline (GBDKPipeline.kt) | INV-2/INV-6 probe GBDKPipeline output; INV-5 probes GBDKBackend full-pipeline dedup pass |
| FIX-03 SRAM Anchor 4 | UAT runtime layer (`BanksUatTest.kt`) | Emulator (StepAgent) | SEED-016 is a save-system runtime behavior; cannot be verified from C text alone |
| FIX-04 sprite-outline | UAT runtime layer (MetaspriteUatTest.kt) | ConvertSpritesTask pipeline | tRNS correction happens at build time; visual confirmation requires runtime screenshot |
| FIX-04 player transparency | UAT runtime layer (PlatformerTemplateUatTest.kt) | ConvertSpritesTask pipeline | Regression guard: sprite transparency must survive unchanged |
| Byte-identity oracle | Hash comparison (sha256sum) | Gradle generateC task | Generated C files in `build/gbkt/generated/` are the invariant surface |

---

## FIX-03 — BanksEmissionTest.kt Sentinel Catalog

### Test File Location

`gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` [ASSUMED from direct file read; file read in this session]

Run command (emission-only, no ROM required):
```bash
./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"
```

### INV-2 — `_bkg_tiles_load_banked` SWITCH_ROM Sequence (SEED-014)

**Test method:** `` `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence` ``
**Line:** ~199 in BanksEmissionTest.kt
**Pipeline used:** `GBDKPipeline()` (raw pipeline — no BankingAnalysisPass; scene `bankSlot` is null)
**What it asserts:**
- `wrapperBody.isNotEmpty()` — `_bkg_tiles_load_banked` helper exists in main.c
- `wrapperBody.contains("SWITCH_ROM(")` — wrapper enters the zone bank
- `wrapperBody.contains("set_bkg_tiles(")` — wrapper calls the real tilemap load
- `wrapperBody.contains("SWITCH_ROM(1);")` — wrapper restores bank 1 on exit

**Scope-level grep gate:** Uses `extractFunctionBody(mainC, "_bkg_tiles_load_banked")` brace-walk — asserts only inside the named function body, not file-level (see BanksEmissionTest.kt comment on scope-level grep gate pattern).

**SEED-014 mapping:** The SEED-014 defect was that `_bkg_tiles_load_banked` was only emitted for sport-racing games. Fix: `hasZoneSceneBinder = gameIR.scenes.any { it.zoneRefs.isNotEmpty() }` at `GBDKPipeline.kt:1428`. INV-2 probes whether the wrapper is emitted and has the correct SWITCH_ROM shape.

**Note on pipeline:** INV-2 uses `GBDKPipeline()` directly (NOT `GBDKBackend()`). The `hasZoneSceneBinder` check at line 1428 reads `scene.zoneRefs` — this is populated from the DSL (`Banks.kt` has `zone(playZone)` binder), NOT from BankingAnalysisPass annotation. The guard works correctly without bankSlot annotation. INV-2 GREEN even with raw pipeline.

---

### INV-5 — `title_enter_trampoline` Section Comment (SEED-015)

**Test method:** `` `INV-5 title_enter_trampoline section comment retains title_enter name (SEED-015)` ``
**Line:** ~407 in BanksEmissionTest.kt
**Pipeline used:** `GBDKBackend()` (FULL pipeline — requires BankingAnalysisPass to assign bankSlot > 0 to title/pause scenes; requires FunctionDeduplicationPass to run the SEED-015 bug path)
**What it asserts:**
- `trampolineIdx >= 1` — `void title_enter_trampoline(` emitted in main.c
- `commentLine.contains("title_enter") && !commentLine.contains("pause_enter")` — the section comment on the line immediately preceding the trampoline function declaration has NOT been over-matched by `FunctionDeduplicationPass`'s callsite-rewrite regex

**SEED-015 root cause (documented in test):** `FunctionDeduplicationPass` callsite-rewrite regex `\bname\s*(` over-matched into section comments, rewriting "Trampoline: title_enter (bank 1)" to "Trampoline: pause_enter (bank 1)". Fix: skip comment lines in callsite-rewrite loop.

**Critical:** `GBDKBackend()` (not raw `GBDKPipeline()`) MUST be used because:
1. `BankingAnalysisPass` must run to assign `bankSlot > 0` to title/pause scenes (required for trampoline stubs to be emitted at all)
2. `FunctionDeduplicationPass` must run to trigger the SEED-015 comment-rewrite path

---

### INV-6 — `play_enter` Calls `_bkg_tiles_load_banked` in bank1.c (SEED-014)

**Test method:** `` `INV-6 play_enter in bank1 calls _bkg_tiles_load_banked for playZone` ``
**Line:** ~463 in BanksEmissionTest.kt
**Pipeline used:** `GBDKPipeline()` (raw pipeline)
**What it asserts:**
- `enterBody.isNotEmpty()` — play_enter emitted in bank1.c
- `enterBody.contains("_bkg_tiles_load_banked(")` — play_enter calls the HOME-bank wrapper
- `enterBody.contains("_bkg_tiles_load_banked(2u,")` — bank arg is 2 (playZone allocated to bank 2)

**Scope-level grep gate:** Uses `extractFunctionBody(bank1C, "play_enter")` — asserts ONLY inside play_enter, not against other bank1.c functions that might also call the helper.

**SEED-014 mapping:** Complements INV-2. INV-2 proves the wrapper exists in main.c with the right shape; INV-6 proves the actual call is emitted in `play_enter` with the correct bank argument.

---

### Anchor 4 — SRAM Persistence (SEED-016)

**CRITICAL:** Anchor 4 is in `BanksUatTest.kt`, NOT `BanksEmissionTest.kt`.

**File:** `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt`
**Test method:** `` `anchor 4 SRAM persistence via GBST round-trip` ``
**Line:** 291 in BanksUatTest.kt
**Type:** Runtime UAT — requires `banks.gb` ROM; auto-skips via `Assumptions.assumeTrue` if ROM absent

**Run command (requires ROM build first):**
```bash
./gradlew :gbkt-examples:banks:buildRom
./gradlew :gbkt-examples:banks:test --tests "*.BanksUatTest"
```

**What it asserts (non-tautological SRAM round-trip):**
1. Press SELECT → `save_game_saves` runs (ENABLE_RAM → sram writes → DISABLE_RAM)
2. Capture `preBytes` (4 bytes at 0xA000-0xA003)
3. `agent.saveState(stateFile)` — snapshot emulator state
4. Explicit `writeMemory(0x0000, 0x0A)` → ENABLE_RAM
5. `writeMemory(0xA000, 99)` — mid-mutation sentinel
6. Verify `midBytes[0] == 99` (mutation actually landed in SRAM)
7. `agent.loadState(stateFile)` — restore snapshot
8. Verify `postBytes` equals `preBytes` (SRAM restored from snapshot)
9. Verify `postBytes[0] != 99` (mutation was overwritten — non-tautological gate)

**SEED-016 mapping:** SEED-016 was "Banks Anchor 4 SRAM test not executed in substrate". Triage found it was present AND GREEN. Phase 20 re-runs it to produce Phase 20 evidence.

---

## FIX-03 Coverage Analysis

| SEED | Title | Guarding Test File | Test Method | Existing or New |
|------|-------|--------------------|-------------|-----------------|
| SEED-014 | bkg_tiles_load_banked gating incomplete | `BanksEmissionTest.kt` | INV-2 (SWITCH_ROM in main.c wrapper) | **EXISTING** |
| SEED-014 | bkg_tiles_load_banked gating incomplete | `BanksEmissionTest.kt` | INV-6 (play_enter call in bank1.c) | **EXISTING** |
| SEED-015 | Banks trampoline body inheritance wrong | `BanksEmissionTest.kt` | INV-5 (section comment not over-matched) | **EXISTING** |
| SEED-016 | Banks Anchor 4 SRAM test not executed | `BanksUatTest.kt` | Anchor 4 (SRAM GBST round-trip) | **EXISTING** |

**Coverage verdict:** Full existing coverage — zero gaps identified. Same D-05 outcome expected as Phase 19 FIX-02 (no new guards needed if verification confirms GREEN on current master).

**D-02 gate implication:** The planner must order plans to run `BanksEmissionTest` fresh FIRST and record GREEN output. Only after confirming GREEN should the audit doc be finalized. If any test is RED, that is an unexpected regression and must be investigated before writing the audit.

---

## FIX-03 — hasZoneSceneBinder Guard

**Location:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt:1426-1434` [VERIFIED by direct file read]

```kotlin
// SEED-014 (Phase 11.1 D-01): un-gate from sport_racing-only to also admit games with
// scene-to-zone binder DSL.
val hasZoneSceneBinder = gameIR.scenes.any { it.zoneRefs.isNotEmpty() }
return if ((hasSportRacing || hasZoneSceneBinder) && bankAllocation.values.any { it > 1 }) {
    listOf(buildBkgTilesLoadBankedHelper())
} else {
    emptyList()
}
```

**What it guards:** `buildBkgTilesLoadBankedHelpers()` — returns the `_bkg_tiles_load_banked` helper function (a `CFunction`) when any scene has a zone binder AND any bank allocation is > 1. Without this guard, the wrapper would only appear in sport-racing games, leaving bank-switching zone-load games without the SWITCH_ROM helper in HOME bank.

**Why it satisfies INV-2/INV-6 on master:** `Banks.kt` declares `zone(playZone)` in the play scene block, which populates `scene.zoneRefs`. The guard fires, emitting the wrapper. INV-2 confirms the wrapper shape; INV-6 confirms the call from play_enter. The `bankAllocation.values.any { it > 1 }` condition is satisfied because `BankingAnalysisPass` assigns play to bank 1+ (the play scene + tileset exceed bank 0 capacity in the Banks game).

---

## FIX-04 — tRNS Visual Oracle

### Phase 13.6 tRNS Auto-Route Location

**File:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt:328-372` [VERIFIED by direct file read]

```kotlin
val transparentIdx = getTransparentIndexShared(pngFile)
val tempFile: File?
if (transparentIdx != null && transparentIdx > 0) {
    // strict mode gate (throws GradleException if strictTransparency.get())
    // overflow guard (> 3 used visible colors → GradleException)
    logger.warn("sprite ... declares transparent color at palette index $transparentIdx (index != 0); framework routed it to GB OBJ index 0...")
    val buildTempDir = temporaryDir  // build/tmp/convertSprites/
    buildTempDir.mkdirs()
    tempFile = prePermuteIndexedPng(pngFile, transparentIdx, buildTempDir, stemName)
    // → temp file named gbkt_permuted_<stemName>.png
} else {
    tempFile = null
}
// buildPng2AssetArgs uses tempFile (permuted) or pngFile (original)
```

**What it fixes:** When a sprite's PNG declares transparency at palette index N > 0 (non-zero tRNS chunk), `prePermuteIndexedPng()` swaps that color to index 0, producing a temp PNG. `png2asset` then runs on the temp PNG, so the emitted C tile array has the transparent color at GB OBJ palette index 0 (the hardware-enforced transparent slot).

**Which example exercises it:** The metasprites elephant PNG (`res/sprites/elephant.png`). Triage evidence `evidence/SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX/source-inspection.txt` confirms: `elephant.c` header contains `gbkt_permuted_elephant.png` temp path, proving the auto-route fired for the elephant sprite.

### captureAndRename Harness

#### MetaspriteUatTest (2-param version)

**File:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` [VERIFIED by direct file read]

```kotlin
private fun captureAndRename(agent: StepAgent, label: String, targetName: String): File {
    val captured = agent.captureScreenshot(label)
    val target = File(EVIDENCE_DIR, targetName)  // EVIDENCE_DIR is class-level constant
    if (target.exists()) target.delete()
    check(captured.renameTo(target)) { "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}" }
    // Sidecar JSON rename (best-effort)
    val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
    if (sidecar.exists()) {
        val targetJson = File(EVIDENCE_DIR, target.nameWithoutExtension + ".json")
        if (targetJson.exists()) targetJson.delete()
        sidecar.renameTo(targetJson)
    }
    return target
}
```

For Phase 20, a new `@Test` method (or new file with Phase 20 `EVIDENCE_DIR`) needs to call `captureAndRename` with a Phase 20 evidence path.

#### PlatformerTemplateUatTest (4-param version)

**File:** `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` [VERIFIED by direct file read]

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
    check(captured.renameTo(target)) { "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}" }
    val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
    if (sidecar.exists()) { /* rename sidecar in lock-step */ }
    return target
}
```

The 4-param version uses an `anchorDir: File` param so each anchor can have its own subdirectory. Phase 20 should follow the same subdirectory convention.

### GBC Mode Pattern

**AgentSessionConfig.discoverFiles** (file: `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt`) [VERIFIED by direct file read]:

```kotlin
// Auto-discovers .noi file from build/gbkt/output/<name>.noi
fun discoverFiles(romFile: File, screenshotDir: File? = null): AgentSessionConfig
```

Discovery convention: ROM at `build/gbkt/output/game.gb` → sym auto-loaded from `build/gbkt/output/game.noi` (or `.sym`). **The `.noi` file is auto-discovered** — no explicit path needed.

**GBC mode activation pattern:**
```kotlin
val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
    .copy(gbcMode = true)
```

**D-05 constraint requires this pattern for platformer-template** (confirmed GBC_COMPATIBLE target). The existing `PlatformerTemplateUatTest.newAgent()` does NOT use GBC mode:
```kotlin
// Current newAgent() — DMG mode (NO gbcMode=true)
private fun newAgent(): StepAgent {
    val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
    // ...
}
```

Phase 20's platformer-template capture MUST use `.copy(gbcMode = true)`. The planner can either:
- Add a `newGbcAgent()` helper to `PlatformerTemplateUatTest` (mirrors `MetaspriteUatTest.newGbcAgent()`)
- Or create a new Phase-20-specific test class (avoids touching Phase 12.x UAT methods)

### GBC Target Verification (D-05)

| Example | Target Setting | Evidence | Capture Mode Required |
|---------|---------------|----------|-----------------------|
| metasprites | `target(GbcTarget.GBC_COMPATIBLE)` at `Metasprites.kt:37` | Direct file read | Either mode valid for tRNS visibility; GBC_COMPATIBLE → planner chooses (DMG simpler, GBC more authentic) |
| platformer-template | `target(GbcTarget.GBC_COMPATIBLE)` at `PlatformerTemplate.kt:69` | Direct file read | `gbcMode=true` REQUIRED (D-05 locked; learning_platformer_mcp_needs_gbc_mode: DMG captures look green-tinted and falsely read as palette regression vs GBC) |

**Metasprites target mode decision (planner discretion per D-05 "verify before capture"):**
- tRNS transparency (palette index 0 being transparent) is visible in BOTH DMG and GBC modes
- However, `MetaspriteUatTest` behavior 3 already establishes `newGbcAgent()` — reusing it for Phase 20 FIX-04 is cleaner and more authentic
- Risk of DMG mode: captures look identical to a GBC-intended game running in the wrong mode — per `learning_platformer_mcp_needs_gbc_mode`, this is a known evidence-quality trap
- **Recommendation:** Use `newGbcAgent()` (GBC mode) for the metasprites FIX-04 capture to match the game's authentic target and avoid mode-mismatch artifacts

### ROM Build Prerequisite (D-05)

Before any FIX-04 screenshot capture:
```bash
# metasprites — clean rebuild required (feedback_rom_build_smoke_test_for_codegen_phases)
./gradlew :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom

# platformer-template — clean rebuild required
./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom
```

Staleness caveat: `build/gbkt/generated/` may contain output from a prior run. Clean before regenerating so the byte-identity baseline is not stale.

---

## Byte-Identity Oracle (D-06)

### Procedure

**Per-commit (affected examples: banks, metasprites, platformer-template):**

```bash
# Hash generated C before a commit (baseline)
./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:generateC
find gbkt-examples/banks/build/gbkt/generated -name "*.c" | sort | xargs sha256sum > /tmp/banks-before.txt

./gradlew :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:generateC
find gbkt-examples/metasprites/build/gbkt/generated -name "*.c" | sort | xargs sha256sum > /tmp/metasprites-before.txt

./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:generateC
find gbkt-examples/platformer-template/build/gbkt/generated -name "*.c" | sort | xargs sha256sum > /tmp/platformer-before.txt

# After commit — re-run the same hash command and diff
# diff /tmp/banks-before.txt /tmp/banks-after.txt
# Any drift = regression signal; must be explained
```

**Phase-close full 7-example sweep:**

```bash
# Clean + generateC for all 7 examples (chain to avoid parallel daemon collision per feedback_no_parallel_gradle_clean)
./gradlew \
  :gbkt-examples:pong:clean :gbkt-examples:pong:generateC \
  :gbkt-examples:breakout:clean :gbkt-examples:breakout:generateC \
  :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:generateC \
  :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:generateC \
  :gbkt-examples:metasprites-stress:clean :gbkt-examples:metasprites-stress:generateC \
  :gbkt-examples:banks:clean :gbkt-examples:banks:generateC \
  :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:generateC

# Collect hashes
find gbkt-examples/*/build/gbkt/generated -name "*.c" | sort | xargs sha256sum
```

**Staleness caveat (critical):** Always run `clean` before `generateC`. Without clean, old generated C from a prior build persists and hash comparison produces false-equal results.

**Pong note (PASS\*):** Per `project_pong_toolchain_nondeterminism` memory: `pong.gb` ROM hashes non-deterministically every rebuild. However, the **generated C** (`main.c` etc.) is stable — the non-determinism is in SDCC/lcc compilation, not C generation. The generated-C hash comparison is safe for pong. If comparing ROM binaries instead: pong is PASS\*, do not re-investigate.

**Expected result (no-codegen-change phase):** All hashes identical before and after every commit. Any diff is a regression signal requiring explanation and FIX.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Screenshot capture | Custom emulator invocation | `StepAgent.captureScreenshot()` + `captureAndRename()` | Already wired to Coffee-GB, handles sidecar JSON, auto-discovers .noi, repeatable |
| GBC mode sessions | Manual ROM execution | `AgentSessionConfig.discoverFiles().copy(gbcMode = true)` + `StepAgent` | Consistent with existing MetaspriteUatTest behavior3 GBC harness |
| File hash comparison | Custom hash tool | `sha256sum` (system command) or `find ... | xargs sha256sum` | Standard, deterministic, captures all *.c files in one invocation |
| C text verification | Reading full generated files | `extractFunctionBody()` brace-walk in BanksEmissionTest | Scope-level grep gate per CLAUDE.md — file-level grep false-positives across function boundaries |
| Audit table | Separate audit system | Markdown table in `20-AUDIT-FIX-03.md` | Mirrors `19-AUDIT-FIX-02.md` format; simple, committed, checkable |

---

## Common Pitfalls

### Pitfall 1: Anchor 4 is in BanksUatTest, NOT BanksEmissionTest

**What goes wrong:** Looking only at `BanksEmissionTest.kt` for SEED-016 coverage — it has INV-1 through INV-8 but NOT Anchor 4.
**Why it happens:** CONTEXT.md D-01 mentions "Anchor 4 @Test" and TRIAGE.md notes "Anchor 4 @Test present (BanksUatTest.kt:291)". The test is in `BanksUatTest.kt`.
**How to avoid:** Run both test classes: `BanksEmissionTest.kt` (emission, no ROM) + `BanksUatTest.kt` (UAT runtime, requires ROM).
**Warning signs:** If a plan maps SEED-016 to `BanksEmissionTest.kt`, the mapping is wrong.

### Pitfall 2: INV-5 requires GBDKBackend (not raw GBDKPipeline)

**What goes wrong:** Changing INV-5 to use `GBDKPipeline()` — the test goes GREEN trivially because `FunctionDeduplicationPass` never runs (it runs in `COutputOptimizer` which is only invoked by `GBDKBackend.generate()`).
**Why it happens:** INV-2 and INV-6 use `GBDKPipeline()` directly; INV-5 appears similar.
**How to avoid:** The INV-5 test KDoc comment explicitly documents: "Must use GBDKBackend.generate() (not raw GBDKPipeline) because INV-5 requires (1) bank-analysis annotations so BankingAnalysisPass assigns bankSlot > 0 to title/pause (without which buildTrampolineStubs filters all scenes out — no trampolines emitted); (2) COutputOptimizer.FunctionDeduplicationPass to run."
**Warning signs:** A GREEN INV-5 that used `GBDKPipeline()` would be a false-green masking the SEED-015 bug.

### Pitfall 3: PlatformerTemplateUatTest.newAgent() is DMG mode

**What goes wrong:** Creating Phase 20 platformer screenshot using `newAgent()` as-is — the ROM runs in DMG mode, producing green-tinted captures that look like a palette regression versus the approved GBC baseline.
**Why it happens:** `newAgent()` calls `AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)` without `.copy(gbcMode = true)`. GBC mode is NOT the default.
**How to avoid:** Create a new helper method (e.g. `newGbcAgent()`) that adds `.copy(gbcMode = true)`. See `MetaspriteUatTest.newGbcAgent()` for the exact pattern.
**Warning signs:** Screenshot shows green tint or lacks GBC color palette → DMG mode was used for a GBC-target ROM.

### Pitfall 4: Stale generated C invalidates byte-identity baseline

**What goes wrong:** Running `generateC` without `clean` first — old C files from a prior build persist, the hash comparison shows "no change" even when codegen would have changed.
**Why it happens:** Gradle's incremental build skips `generateC` if inputs haven't changed since the last run.
**How to avoid:** Always `clean` before `generateC` when capturing a byte-identity baseline (per `feedback_rom_build_smoke_test_for_codegen_phases`).
**Warning signs:** Hash file shows same output regardless of what was changed.

### Pitfall 5: Parallel clean commands corrupt the Kotlin daemon

**What goes wrong:** Running `./gradlew :a:clean :a:generateC &; ./gradlew :b:clean :b:generateC` as parallel shell jobs against the same repo root.
**Why it happens:** Multiple Gradle daemons collide on the same `.gradle` directory.
**How to avoid:** Chain into a single `./gradlew :a:clean :a:generateC :b:clean :b:generateC` command — Gradle parallelizes per-task internally (per `feedback_no_parallel_gradle_clean`).
**Warning signs:** Daemon corruption errors, partial builds, inconsistent test results.

---

## Evidence Layout Pattern

Following Phase 16/19 conventions, Phase 20 evidence belongs under:
`phase_dir = .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/`

```
evidence/
├── fix-03/
│   ├── inv2-emission-run.txt        # BanksEmissionTest INV-2 GREEN output
│   ├── inv5-emission-run.txt        # BanksEmissionTest INV-5 GREEN output
│   ├── inv6-emission-run.txt        # BanksEmissionTest INV-6 GREEN output
│   └── anchor4-sram-run.txt         # BanksUatTest Anchor 4 GREEN output
├── fix-04/
│   ├── metasprites-sprite-outline.png      # D-08 visual oracle #1
│   └── platformer-player-transparency.png  # D-08 visual oracle #2 (GBC mode)
└── byte-identity/
    ├── banks-before.txt             # sha256sum snapshot before commits
    ├── metasprites-before.txt
    ├── platformer-before.txt
    └── phase-close-sweep.txt        # full 7-example sweep at phase close
```

`EVIDENCE_DIR` in Phase 20 test code should resolve to the `evidence/fix-04/` dir above. Pattern (from existing harnesses):
```kotlin
val EVIDENCE_DIR = File(System.getProperty("user.dir"))
    .resolve("../../.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04")
    .normalize()
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Kotlin test wrappers (`kotlin.test.Test`, `kotlin.test.assertTrue`, etc.) |
| Config file | Configured in each module's `build.gradle.kts` (no explicit config file) |
| FIX-03 quick run | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"` |
| FIX-03 full (includes Anchor 4) | `./gradlew :gbkt-examples:banks:buildRom && ./gradlew :gbkt-examples:banks:test` |
| FIX-04 captures | `./gradlew :gbkt-examples:metasprites:buildRom :gbkt-examples:platformer-template:buildRom` then run UAT test class |
| Full suite | `./gradlew :gbkt-examples:banks:test :gbkt-examples:metasprites:test :gbkt-examples:platformer-template:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FIX-03 (SEED-014) | `_bkg_tiles_load_banked` wrapper emitted with SWITCH_ROM | JVM emission | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest.INV-2*"` | Yes (`BanksEmissionTest.kt`) |
| FIX-03 (SEED-014) | `play_enter` calls `_bkg_tiles_load_banked(2u,...)` in bank1.c | JVM emission | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest.INV-6*"` | Yes (`BanksEmissionTest.kt`) |
| FIX-03 (SEED-015) | Section comment preceding `title_enter_trampoline` not rewritten by FunctionDeduplicationPass | JVM emission | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest.INV-5*"` | Yes (`BanksEmissionTest.kt`) |
| FIX-03 (SEED-016) | SRAM round-trip preserves save data across loadState | Runtime UAT | `./gradlew :gbkt-examples:banks:buildRom :gbkt-examples:banks:test --tests "*.BanksUatTest"` | Yes (`BanksUatTest.kt`); skips if ROM absent |
| FIX-04 (SEED-PHASE-13-SPRITE-OUTLINE) | Elephant sprite renders without black outline (transparent pixels correct) | Runtime screenshot (visual) | New `@Test` in metasprites UAT using `newGbcAgent()` + `captureAndRename` | No — new Phase 20 test method needed |
| FIX-04 (regression guard) | Platformer player transparency unchanged at HEAD | Runtime screenshot (visual) | New `@Test` in platformer-template UAT using `.copy(gbcMode = true)` agent | No — new Phase 20 test method needed |

### Sampling Rate

- **Per task commit:** `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"` (no ROM required; fast)
- **Per wave merge:** Full banks + metasprites + platformer-template test run + byte-identity diff on affected examples
- **Phase gate:** Full 7-example byte-identity sweep + all 4 FIX-03 tests GREEN + 2 FIX-04 screenshots captured + `20-AUDIT-FIX-03.md` produced

### Wave 0 Gaps

- [ ] New Phase 20 `@Test` method in metasprites UAT (or new test class) — covers FIX-04 sprite-outline capture with Phase 20 `EVIDENCE_DIR` target
- [ ] New Phase 20 `@Test` method in platformer-template UAT — covers FIX-04 player-transparency capture with `gbcMode=true` and Phase 20 `EVIDENCE_DIR` target
- [ ] `20-AUDIT-FIX-03.md` — produced after D-02 re-verify confirms all emission tests GREEN

---

## Security Domain

Security enforcement not applicable to this phase. Phase 20 produces evidence artifacts (audit doc, screenshots, hash files) and optionally new JVM test methods — no user input processing, no authentication, no data persistence changes.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JVM / Kotlin | BanksEmissionTest, UAT tests | Yes (confirmed by existing test runs) | JVM 21 per CLAUDE.md | — |
| GBDK lcc | Banks ROM build (Anchor 4 + FIX-04 captures) | Conditional (`GBDK_HOME` must be set) | GBDK 4.5.0 (pinned in CI) | Skip ROM-dependent tests (UAT auto-skips via Assumptions) |
| Coffee-GB emulator (embedded) | UAT StepAgent | Yes (in `gbkt-emulator` module) | embedded | — |
| sha256sum | Byte-identity oracle | Yes (system tool, macOS/Linux) | system | `md5sum` or `openssl dgst -sha256` |

**Missing dependencies with fallback:**
- GBDK lcc absent: BanksUatTest.kt Anchor 4 auto-skips; FIX-04 UAT tests auto-skip. Byte-identity check of generated C still runs (does not require lcc). Plan must note: "requires GBDK locally or in CI" for ROM-dependent plans.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| INV-2 guarded by sport-racing gate only | `hasZoneSceneBinder` guard added (un-gated to zone-binder games) | Phase 11.1 D-01 | Banks game now correctly gets `_bkg_tiles_load_banked` helper |
| FunctionDeduplicationPass rewrote section comments | Skip comment lines in callsite-rewrite loop | Phase 11.1-02 | `title_enter_trampoline` comment preserved correctly |
| play_enter missing zone-load call | `zone(playZone)` DSL binder in Banks.kt + SceneVisitor emission | Phase 11.1-03 | play_enter now calls `_bkg_tiles_load_banked(2u, ...)` |
| SRAM write path missing `trigger_saves` | `GBDKSystemVisitor.visitSaveSystem` emits trampoline stub | Phase 11-10 | `trigger_saves` call surface works; Anchor 4 passes |
| Sprite tRNS index > 0 → black outline | tRNS auto-route in `ConvertSpritesTask.prePermuteIndexedPng()` | Phase 13.6 | Elephant outline clean; visual oracle deferred to Phase 20 D-08 |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | INV-2, INV-5, INV-6 remain GREEN on current `chore/hardening_0_1_0` master after Phases 17/18 changes | FIX-03 Coverage Analysis | If RED: the guard was broken by a Phase 17/18 change; must investigate before writing audit doc |
| A2 | Anchor 4 (BanksUatTest.kt:291) remains GREEN when banks.gb ROM is built on current HEAD | FIX-03 Coverage Analysis | If RED: SRAM save system regressed; separate investigation needed |
| A3 | metasprites GBC_COMPATIBLE target means the tRNS outline check is valid in GBC mode (no color-mode artifact masks the transparency) | FIX-04 — GBC Target Verification | If wrong: DMG mode should be used instead for transparency check |
| A4 | `sha256sum` is available on the executor machine | Byte-Identity Oracle | Use `md5sum` or `openssl dgst -sha256` as fallback |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed. (NOT empty — A1 and A2 must be verified by the D-02 gate.)

---

## Open Questions

1. **Metasprites capture mode (DMG vs GBC) for FIX-04 sprite-outline check**
   - What we know: metasprites targets GBC_COMPATIBLE; tRNS transparency visible in both modes; `learning_platformer_mcp_needs_gbc_mode` warns about DMG-mode artifacts for GBC-target games
   - What's unclear: whether a DMG-mode capture of the metasprites ROM is sufficient visual evidence for the Phase 20 audit, or whether GBC mode is required for completeness
   - Recommendation: use GBC mode (mirrors `newGbcAgent()` pattern already in `MetaspriteUatTest`; avoids the DMG-mode artifact trap; consistent with CONTEXT.md D-05 "correct target mode")

2. **New test method placement (new class vs extending existing test files)**
   - What we know: Phase 19 added evidence-dir-retargeted test classes (e.g. `PlatformerTemplate128UatTest.kt`); Phase 20 needs Phase-20-specific `EVIDENCE_DIR`
   - What's unclear: whether to add Phase-20-specific `@Test` methods to existing test files (changing `EVIDENCE_DIR` is invasive) or create new standalone test classes
   - Recommendation: create new Phase 20 focused test class (e.g. `MetaspritePhase20OracleTest.kt`, `PlatformerTemplatePhase20OracleTest.kt`) with dedicated `EVIDENCE_DIR` pointing to Phase 20 evidence dir — mirrors `PlatformerTemplate128UatTest.kt` precedent

---

## Sources

### Primary (HIGH confidence)

- Direct file read: `BanksEmissionTest.kt` — all INV-2/INV-5/INV-6 test methods, line numbers, pipeline choices, assertion patterns
- Direct file read: `BanksUatTest.kt` — Anchor 4 at line 291, SRAM round-trip recipe
- Direct file read: `GBDKPipeline.kt:1426-1434` — `hasZoneSceneBinder` guard code and comment
- Direct file read: `MetaspriteUatTest.kt` — `captureAndRename()` 2-param signature, `newAgent()` / `newGbcAgent()` patterns
- Direct file read: `PlatformerTemplateUatTest.kt` — `captureAndRename()` 4-param signature, `newAgent()` DMG-mode pattern, `anchorDir` subdirectory convention
- Direct file read: `AgentSessionConfig.kt` — `discoverFiles()`, `gbcMode`, `.noi` auto-discovery convention
- Direct file read: `ConvertSpritesTask.kt:328-372` — tRNS auto-route (Phase 13.6 REQ-4 / Plan 04)
- Direct file read: `Metasprites.kt` — `GbcTarget.GBC_COMPATIBLE` at line 37
- Direct file read: `PlatformerTemplate.kt` — `GbcTarget.GBC_COMPATIBLE` at line 69
- Direct file read: `.planning/phases/16-seed-triage/TRIAGE.md` — SEED-014/015/016/SEED-PHASE-13-SPRITE-OUTLINE dispositions
- Direct file read: `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/20-CONTEXT.md` — D-01 through D-08
- Direct file read: `.planning/phases/19-codegen-fixes-metasprite-cluster/19-AUDIT-FIX-02.md` — audit table format precedent

### Secondary (MEDIUM confidence)

- Inferred from codebase structure: `sha256sum` for byte-identity hashing (standard system tool, consistent with Phase 19 D-07 procedural approach)

---

## Metadata

**Confidence breakdown:**
- FIX-03 sentinel catalog: HIGH — all test methods read directly from source; line numbers and pipeline choices verified
- hasZoneSceneBinder guard: HIGH — code read directly at GBDKPipeline.kt:1426-1434
- FIX-04 harness signatures: HIGH — captureAndRename signatures read from MetaspriteUatTest.kt and PlatformerTemplateUatTest.kt
- GBC mode requirement: HIGH — AgentSessionConfig.kt read directly; GBC_COMPATIBLE confirmed in both game files
- Byte-identity procedure: HIGH — consistent with Phase 19 D-07 and CLAUDE.md feedback rules
- Phase 13.6 tRNS route: HIGH — ConvertSpritesTask.kt:328-372 read directly; confirmed elephant.c uses gbkt_permuted path per TRIAGE evidence note

**Research date:** 2026-06-14
**Valid until:** 2026-07-14 (30 days; no external dependencies — all claims from codebase reads)
