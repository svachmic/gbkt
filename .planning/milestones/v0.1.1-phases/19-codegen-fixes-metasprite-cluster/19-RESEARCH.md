# Phase 19: Codegen Fixes — Metasprite Cluster - Research

**Researched:** 2026-06-13
**Domain:** Game Boy (GBDK) metasprite codegen — confirmation/regression-guard phase
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Capture FIX-01 HEAD screenshots by reusing/extending the existing JVM `MetaspriteUatTest` StepAgent + `captureAndRename()` harness, emitting PNGs into `.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`.
- **D-02:** Where metasprites-stress lacks a UAT path, author a small new UAT scaffold rather than a one-off manual capture.
- **D-03 (constraint, locked):** Captures MUST run in the example's correct target mode — `gbcMode=true` with the `.noi` symFile if the metasprites example targets GBC; ROM MUST be rebuilt clean immediately before capture.
- **D-04:** Place each guard where its fix is observable: generic codegen invariants → `gbkt-backend-gbdk`; stress-example-specific output → `gbkt-examples/metasprites-stress`.
- **D-05:** Audit existing coverage FIRST; author guards only for seeds with no existing guard — no duplicate coverage. Each new guard asserts GREEN with a RED-by-design comment.
- **D-06:** Produce a standalone `19-AUDIT-FIX-02.md` in the phase dir (seed → file → assertion → existing-or-new → reverted-fix scenario).
- **D-07:** Satisfy Req 5 via a procedural before/after sha256 diff of generated `main.c` + bank files for both examples at phase start and end.
- **D-08:** Every Phase 19 commit contains only metasprite-confirmation work — zero S3776/PR-#77 cognitive-complexity refactors interleaved.
- **D-09 (constraint):** Executors must run `:module:spotlessApply :module:detekt` per-commit — `:module:test` and pre-commit hook do NOT run them.

### Claude's Discretion
- Exact test method/assertion names, evidence PNG filenames, and the precise hashing command for the byte-identity diff are left to the planner/executor, provided they meet the acceptance criteria.

### Deferred Ideas (OUT OF SCOPE)
- Banks trio (SEED-014/015/016) and tRNS sprite outline → Phase 20 (FIX-03/FIX-04).
- Platformer cEmit escapes and remaining DSL/tooling seeds → Phase 21 (FIX-05/FIX-06).
- Merging PR #77 (S3776 cognitive-complexity burn-down) → held open until Phases 19/20/21 complete.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FIX-01 | Fresh HEAD runtime screenshots for SEED-004/005/006/013 under `.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/` | MetaspriteUatTest.kt `newGbcAgent()` scaffold; GBC_COMPATIBLE target confirmed; .noi auto-discovered |
| FIX-02 | Audit + gap-fill: every seed (SEED-007..011) maps to a named JVM emission guard | All 5 seeds have existing guards; exact file+method names documented in audit table below |
| Req 3 | Metasprites ROM smoke: `buildRom` exits 0 + fresh runtime screenshot | Build output confirmed present; clean-rebuild procedure documented |
| Req 4 | Commit separation from S3776 | D-08 constraint locked |
| Req 5 | Byte-identity preservation — no Phase 19 production codegen drift | D-07 procedural sha256 diff; existing `MetaspritesGeneratedSpriteByteIdentityTest` still runs |
</phase_requirements>

---

## Summary

Phase 19 is a **confirmation-only phase**: all nine metasprite seeds are already VERIFIED-ALREADY-FIXED in the archived triage. The phase produces evidence artifacts and test guards so the archived seeds are traceably defended against future regression.

**FIX-01 (visual, 4 seeds):** Fresh HEAD runtime screenshots must be captured in GBC mode (both metasprites and metasprites-stress target `GbcTarget.GBC_COMPATIBLE`). The `MetaspriteUatTest.kt` `newGbcAgent()` helper already provides the `gbcMode=true` scaffold; a new Phase 19 evidence test class is needed to point `EVIDENCE_DIR` at the Phase 19 evidence directory instead of Phase 10's.

**FIX-02 (structural, 5 seeds — CRITICAL FINDING):** All five structural seeds already have dedicated named JVM emission guards authored in prior phases. ZERO new guards are required. The only FIX-02 work is authoring `19-AUDIT-FIX-02.md` mapping each seed to its existing guard and running `./gradlew :gbkt-backend-gbdk:test :gbkt-lang:test` to confirm all are GREEN.

**Byte-identity oracle (Req 5):** Procedural sha256 diff of `main.c` + bank files before and after the phase (D-07). The existing `MetaspritesGeneratedSpriteByteIdentityTest` (elephant.c baseline) and `MetaspritesStressGeneratedSpriteByteIdentityTest` (elephant.c + tiger.c baselines) run alongside as continuous guards.

**Primary recommendation:** Author the Phase 19 GBC-mode UAT evidence test and the `19-AUDIT-FIX-02.md` document; verify all guards GREEN; no production codegen changes expected.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Visual screenshot capture | JVM UAT tier (StepAgent) | MCP emulator (same API) | MetaspriteUatTest.kt harness already exists; both tiers use the same StepAgent under the hood |
| FIX-02 emission guards | GBDK Backend (gbkt-backend-gbdk) | DSL tier (gbkt-lang) for SEED-007 | Guards live at the layer where the fix was applied |
| Byte-identity oracle | Filesystem diff (procedural sha256) | Committed sprite baselines (MetaspritesGeneratedSpriteByteIdentityTest) | D-07: same-session diff robust to toolchain non-determinism |
| ROM build + smoke | GBDK toolchain (lcc) + StepAgent | — | Requires GBDK installed + buildRom task |

---

## FIX-02 Emission-Guard Audit (Core Research Deliverable)

This is the definitive pre-research for the `19-AUDIT-FIX-02.md` document the planner must produce.

### Findings: All 5 Seeds Already Guarded

| SEED | Title | Guard File | Package | Test Method(s) | Status |
|------|-------|-----------|---------|---------------|--------|
| SEED-007 | Actor palette slot defaults to 0 | `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/Seed007GameBuilderPaletteSlotTest.kt` | `io.github.gbkt.core.dsl` | `sequential_actors_with_auto_slot_get_sequential_slot_indices()`, `actor_with_explicit_slot_does_not_consume_auto_slot_counter()` | **GUARDED** [VERIFIED: codebase grep] |
| SEED-008 | Metasprite VRAM collision with actors | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/Seed008VramCollisionTest.kt` | `io.github.gbkt.backend.gbdk.codegen.pipeline` | `main_c_actor_and_metasprite_set_sprite_data_use_distinct_start_offsets()`, `main_c_set_sprite_data_calls_are_actors_first_then_metasprites()` | **GUARDED** [VERIFIED: codebase grep] |
| SEED-009 | Metasprites header missing in bank1 | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/Seed009BankIncludeTest.kt` | `io.github.gbkt.backend.gbdk.codegen.pipeline` | `bank1_c_includes_metasprites_h_when_scene_frame_has_MoveMetasprite()`, `bank1_c_does_not_include_metasprites_h_when_no_scene_uses_metasprite()` | **GUARDED** [VERIFIED: codebase grep] |
| SEED-010 | Symbol collision multi-metasprite games | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/Seed010NamespaceTest.kt` | `io.github.gbkt.backend.gbdk.codegen.visitor` | `two_metasprites_emit_distinct_descriptor_symbol_names()`, `two_metasprites_with_distinct_rot_vars_emit_distinct_var_refs()`, `default_null_fields_emit_canonical_underscore_names()` | **GUARDED** [VERIFIED: codebase grep] |
| SEED-011 | hiwater collision multi-metasprite per frame | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/Seed011HiwaterFrameScopeTest.kt` | `io.github.gbkt.backend.gbdk.codegen.visitor` | `play_frame_body_contains_exactly_one_hide_sprites_range_call()`, `play_frame_body_contains_exactly_one_hiwater_init()`, `title_frame_body_without_metasprite_has_zero_hiwater_init()` | **GUARDED** [VERIFIED: codebase grep] |

**Result: 0 of 5 seeds need new guards.** FIX-02 work is documentation-only (19-AUDIT-FIX-02.md) + GREEN verification run.

### Per-Seed Guard Details

#### SEED-007 — Actor palette slot always defaults to 0
- **Fix location:** `gbkt-lang/.../GameBuilder.kt:756-759` — `actorPaletteAutoSlot++` counter in `buildScenesWithActorPalettes()` [VERIFIED: codebase grep at lines 752-768]
- **Guard module:** `gbkt-lang` (not `gbkt-backend-gbdk`; the fix is in the DSL builder layer, not the codegen backend)
- **Reverted-fix scenario:** Reverting to `else 0` causes `sequential_actors_with_auto_slot_get_sequential_slot_indices()` to fail with `expected [0,1,2,3], got [0,0,0,0]`
- **Run command:** `./gradlew :gbkt-lang:test --tests "*.Seed007GameBuilderPaletteSlotTest"`
- **Note:** The class header (lines 26-28 of the test file) explicitly documents the `else 0` bug and the `actorPaletteAutoSlot++` fix.

#### SEED-008 — Metasprite VRAM collision with actors
- **Fix location:** `gbkt-backend-gbdk/.../GBDKPipeline.kt` — unified `buildAllSpriteDataLoadStatements()` with a single `VramAllocator` iterating actors first then metasprites (Route A/B per TRIAGE) [VERIFIED: Seed008VramCollisionTest.kt test class comment confirms pre-fix vs post-fix shape]
- **Guard module:** `gbkt-backend-gbdk`
- **Reverted-fix scenario:** Reverting to two separate `var nextTile = 0` loops causes `main_c_actor_and_metasprite_set_sprite_data_use_distinct_start_offsets()` to find `set_sprite_data(0u, 48u, elephant_tiles)` (the collision pattern) instead of `set_sprite_data(2u, 48u, elephant_tiles)`
- **Run command:** `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed008VramCollisionTest"`
- **Current metasprites-stress main.c confirms Route A:** `set_sprite_data(0u, 2u, sprites_player_tiles)` then `set_sprite_data(2u, sprites_elephant_tiles_count, sprites_elephant_tiles)` then `set_sprite_data(2u + sprites_elephant_tiles_count, sprites_tiger_tiles_count, sprites_tiger_tiles)` [VERIFIED: live build/gbkt/generated/main.c]

#### SEED-009 — Metasprites header missing in bank1.c
- **Fix location:** `gbkt-backend-gbdk/.../GBDKPipeline.kt` — conditional `#include <gbdk/metasprites.h>` in `buildSceneFile()` (Route A per TRIAGE) [VERIFIED: Seed009BankIncludeTest.kt class comment]
- **Guard module:** `gbkt-backend-gbdk` (not `gbkt-examples/metasprites-stress`; the guard tests the generic codegen behavior, which is sufficient per D-05 no-duplicate-coverage rule)
- **Reverted-fix scenario:** Removing the conditional include causes `bank1_c_includes_metasprites_h_when_scene_frame_has_MoveMetasprite()` to fail — bank1.c would lack `#include <gbdk/metasprites.h>` despite containing `MoveMetasprite` ops
- **Run command:** `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed009BankIncludeTest"`
- **Current metasprites-stress bank1.c confirms fix:** `#include <gbdk/metasprites.h>` present at line 7 [VERIFIED: live build/gbkt/generated/bank1.c]

#### SEED-010 — Symbol collision multi-metasprite games
- **Fix location:** `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` — CR-03 namespacing: `sprite_${ms.id}_frame_N[]` / `sprite_${ms.id}_frames[]` instead of the collision-causing `sprite_metasprites[]` / `sprite_metasprite_N[]` [VERIFIED: Seed010NamespaceTest.kt class comment]
- **Guard module:** `gbkt-backend-gbdk`
- **Reverted-fix scenario:** Reverting to unnamespaced symbols causes `two_metasprites_emit_distinct_descriptor_symbol_names()` to find `sprite_metasprites[` collision count > 0 in combined elephant+tiger emission
- **Run command:** `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed010NamespaceTest"`
- **Asset-driven path note:** The `elephant_metasprites[]` / `tiger_metasprites[]` arrays in metasprites-stress bank1.c are png2asset native symbols naturally distinct by PNG filename — no separate GBKT guard required for that naming path. The procedural `sprite_<id>_frames[]` namespace collision (the SEED-010 root cause) is what Seed010NamespaceTest.kt guards.

#### SEED-011 — hiwater collision multi-metasprite per frame
- **Fix location:** `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` — hoisted `hiwater = 0u` init and `hide_sprites_range()` call from per-`moveMetasprite()` scope to frame-function prelude/postlude (Route A per TRIAGE) [VERIFIED: Seed011HiwaterFrameScopeTest.kt class comment lines 47-53]
- **Guard module:** `gbkt-backend-gbdk`
- **Reverted-fix scenario:** Reverting hoist causes `play_frame_body_contains_exactly_one_hiwater_init()` to fail (finds 2 `hiwater = 0u` occurrences — one per `moveMetasprite()` call — instead of 1)
- **Run command:** `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed011HiwaterFrameScopeTest"`
- **Current metasprites-stress bank1.c confirms Route A:** `uint8_t hiwater = 0u;` at function open (line 15), then multiple `hiwater +=` accumulations, single `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` at close [VERIFIED: live build/gbkt/generated/bank1.c]

---

## FIX-01 Screenshot Capture Harness

### Target Mode Confirmation (D-03 constraint)

Both examples are confirmed GBC targets [VERIFIED: codebase grep]:
- `gbkt-examples/metasprites/src/main/kotlin/.../Metasprites.kt:37`: `target(GbcTarget.GBC_COMPATIBLE)`
- `gbkt-examples/metasprites-stress/src/main/kotlin/.../MetaspritesStress.kt:54`: `target(GbcTarget.GBC_COMPATIBLE)`

**Consequence:** ALL FIX-01 screenshots and the ROM smoke screenshot MUST use `gbcMode=true`. Capturing in DMG mode on a GBC-targeted ROM produces false grayscale rendering (MEMORY: `learning_platformer_mcp_needs_gbc_mode`).

### Existing Harness (`MetaspriteUatTest.kt`)

Located at: `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt`

Key facts [VERIFIED: file read]:
- `newAgent()` — DMG mode (used for behaviors 1 and 2; insufficient for Phase 19 FIX-01)
- `newGbcAgent()` — GBC mode (`AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR).copy(gbcMode = true)`); already scaffolded for Phase 10-18 behavior 3
- `captureAndRename(agent, label, targetName)` — captures screenshot and renames to `EVIDENCE_DIR/targetName`
- `AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)` — auto-discovers `.noi` symFile at `build/gbkt/output/metasprites.noi` (confirmed present [VERIFIED: ls output])
- Current `EVIDENCE_DIR` points to Phase 10's evidence directory — Phase 19 needs a different evidence dir

### Phase 19 New Test Class

A new test class (e.g., `Phase19VisualEvidenceTest.kt`) is needed in `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/` that:
1. Points `EVIDENCE_DIR` to `.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`
   - Relative path from `gbkt-examples/metasprites/` (user.dir at test runtime): `../../.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`
2. Uses `newGbcAgent()` (gbcMode=true) for ALL captures — not `newAgent()`
3. Captures at minimum 4 PNGs labeled per seed (SEED-004, SEED-005, SEED-006, SEED-013)
4. Captures a ROM smoke screenshot (Req 3)

The four visual truths can be captured efficiently in 1-2 test methods:
- **Boot frame** (showing elephant on checkerboard): covers SEED-004 (elephant tiles uncorrupted) and SEED-005 (BG checkerboard not diagonal)
- **Subpalette frame** (at `rot=8`, subpal=2=cyan): covers SEED-006 (subPalette global assigned before move) and SEED-013 (correct GBC sub-palette colors)

The existing `behavior3` test method in `MetaspriteUatTest.kt` navigates to `rot=8` — the Phase 19 test can reuse the same input sequence.

### metasprites-stress UAT (D-02)

SEED-009, 010, 011 are guarded by JVM emission tests (not UAT), so D-02 (new stress UAT scaffold) only applies if a stress visual screenshot is needed. Per the SPEC (Req 3), the stress buildRom must succeed and a smoke screenshot is desirable for completeness. However, Req 1 only requires screenshots for SEED-004/005/006/013 (all in the metasprites example). Req 3 mentions both buildRom tasks but only one "fresh runtime screenshot."

**Planner recommendation:** A smoke screenshot of metasprites-stress is beneficial but not strictly required by Req 1. If the planner adds it, a simple new `MetaspritesStressUatTest.kt` class using the same StepAgent pattern suffices (stress ROM is already built at `build/gbkt/output/metasprites-stress.gb`; .noi at `build/gbkt/output/metasprites-stress.noi`).

---

## Byte-Identity Oracle (D-07 / Req 5)

### Generated Output Locations

| Example | File | Path |
|---------|------|------|
| metasprites | main.c | `gbkt-examples/metasprites/build/gbkt/generated/main.c` |
| metasprites-stress | main.c | `gbkt-examples/metasprites-stress/build/gbkt/generated/main.c` |
| metasprites-stress | bank1.c | `gbkt-examples/metasprites-stress/build/gbkt/generated/bank1.c` |

### Procedural Diff Approach (D-07)

At phase start (Wave 0), after a clean `buildRom`, capture sha256 hashes:
```bash
sha256sum gbkt-examples/metasprites/build/gbkt/generated/main.c \
          gbkt-examples/metasprites-stress/build/gbkt/generated/main.c \
          gbkt-examples/metasprites-stress/build/gbkt/generated/bank1.c
```
Record these in an evidence file. At phase end (after all emission tests and audit work), repeat and diff. Expected result: identical hashes (no Phase 19 production codegen drift).

### Known Non-Determinism Caveat

The `.gb` binary ROM hashes differ between builds due to GBDK/SDCC toolchain non-determinism (MEMORY: `project_pong_toolchain_nondeterminism`). This affects `.gb` files only — the **generated C** (`main.c`, `bank1.c`) is fully deterministic given the same Kotlin DSL input. The D-07 oracle compares generated C (not `.gb` binaries), so it is robust.

### Existing Committed Sprite Baselines

These tests run alongside D-07 and provide continuous guards:
- `MetaspritesGeneratedSpriteByteIdentityTest.kt` (metasprites example): `elephant.c` baseline, SHA-256 prefix `0296ec36`, re-pinned 2026-06-05 after 13.6-07 deterministic-name fix [VERIFIED: file read]
- `MetaspritesStressGeneratedSpriteByteIdentityTest.kt` (metasprites-stress): `elephant.c` (SHA `6be5d78f`) + `tiger.c` (SHA `b09fe25d`) baselines [VERIFIED: file read]

Both use `assumeTrue(file.exists(), ...)` to skip gracefully when `convertSprites` has not been run.

---

## Architecture Patterns

### Screenshot Evidence Directory Structure (Phase 16 convention to follow)

Per Phase 16 pattern (`.planning/phases/16-seed-triage/evidence/SEED-004/screenshot.png`), Phase 19 should structure evidence as:
```
.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/
├── SEED-004/
│   └── screenshot.png        # elephant tiles uncorrupted
├── SEED-005/
│   └── screenshot.png        # BG checkerboard
├── SEED-006/
│   └── screenshot.png        # elephant sub-palette assigned
├── SEED-013/
│   └── screenshot.png        # correct GBC colors
├── ROM-smoke/
│   └── screenshot.png        # metasprites ROM correct rendering
└── byte-identity/
    ├── before.sha256          # hashes at phase start
    └── after.sha256           # hashes at phase end
```

The `captureAndRename()` helper in MetaspriteUatTest.kt creates directories via `EVIDENCE_DIR.mkdirs()`. Per-seed directories need to be created beforehand or `captureAndRename()` extended.

### Emission Test Pattern (for reference — already followed by all 5 guards)

All existing guards follow the same pattern:
1. Build a minimal `GameIR` directly (not via DSL) to bypass DSL validation guards
2. Run `GBDKPipeline().generate(gameIR)`
3. Extract relevant file content (`output.files["main.c"]`, `output.files["bank1.c"]`)
4. Use brace-walk `extractFunctionBody()` for scope-level grep gates (per CLAUDE.md §"scope-level grep gates")
5. Assert with `assertTrue`/`assertFalse` + descriptive failure message including the reverted-fix scenario

### Build Commands

```bash
# Phase 19 ROM builds (MUST be clean before capture — D-03)
./gradlew :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom
./gradlew :gbkt-examples:metasprites-stress:clean :gbkt-examples:metasprites-stress:buildRom

# FIX-02 verification runs
./gradlew :gbkt-backend-gbdk:test                    # guards SEED-008, 009, 010, 011
./gradlew :gbkt-lang:test                            # guard for SEED-007
./gradlew :gbkt-examples:metasprites:test            # UAT + byte-identity baseline
./gradlew :gbkt-examples:metasprites-stress:test     # byte-identity baseline (sprite sidecars)

# Spotless + detekt per-commit (D-09)
./gradlew :gbkt-examples:metasprites:spotlessApply :gbkt-examples:metasprites:detekt
./gradlew :gbkt-backend-gbdk:spotlessApply :gbkt-backend-gbdk:detekt
./gradlew :gbkt-lang:spotlessApply :gbkt-lang:detekt
```

---

## Common Pitfalls

### Pitfall 1: DMG Mode on GBC-Target ROM
**What goes wrong:** Screenshots appear in grayscale / palettes appear wrong even though the fix is correct
**Why it happens:** DMG emulation ignores GBC palette RAM; the OBJ sub-palette selection bits are hardware-ignored on real DMG hardware and in DMG emulation mode
**How to avoid:** Always use `newGbcAgent()` (not `newAgent()`) for FIX-01 captures; verify `baseConfig.copy(gbcMode = true)` is set
**Warning signs:** Screenshot appears monochrome/green-tinted; cannot distinguish palette cycling

### Pitfall 2: Stale ROM Used for Screenshots
**What goes wrong:** Screenshots show pre-fix behavior even though the code is fixed
**Why it happens:** `build/gbkt/output/metasprites.gb` may be from a prior run
**How to avoid:** Run `:gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom` immediately before screenshot capture; never skip the clean step
**Warning signs:** ROM date is older than current DSL changes

### Pitfall 3: S3776 Commits Interleaved with Phase 19 Commits
**What goes wrong:** The byte-identity oracle can't attribute C-output diffs to Phase 19 or to S3776 refactors
**Why it happens:** PR #77 (S3776 burn-down) is open on the same branch; cherry-picking or rebasing may interleave commits
**How to avoid:** D-08 strictly forbids mixing; verify `git log` shows only Phase 19 / FIX-01 / FIX-02 scope per commit; no cognitive-complexity refactors

### Pitfall 4: SEED-007 Guard Is in gbkt-lang, Not gbkt-backend-gbdk
**What goes wrong:** Running only `:gbkt-backend-gbdk:test` misses the SEED-007 guard; appears to have "4 of 5 seeds guarded"
**Why it happens:** The SEED-007 fix is in the DSL builder layer (`GameBuilder.kt` in `gbkt-lang`), not in the codegen backend
**How to avoid:** Run `./gradlew :gbkt-lang:test` separately; confirm `Seed007GameBuilderPaletteSlotTest` is GREEN
**Warning signs:** Only 4 seed files found in `gbkt-backend-gbdk`; forgetting the fifth in `gbkt-lang`

### Pitfall 5: Mistaking D-04 Observability Guidance for Placement Mandate
**What goes wrong:** Authoring NEW guards in `gbkt-examples/metasprites-stress` for SEED-009/010, duplicating the existing `Seed009BankIncludeTest.kt` / `Seed010NamespaceTest.kt` in `gbkt-backend-gbdk`
**Why it happens:** D-04 listed "stress-example-specific output → metasprites-stress module tests" as the intended placement if guards were absent; D-05 supersedes D-04 when guards exist
**How to avoid:** D-05 says "no duplicate coverage"; the existing guards in `gbkt-backend-gbdk` are sufficient and authoritative
**Warning signs:** Authoring a test in `gbkt-examples/metasprites-stress` that asserts the same include/namespace property already tested in `Seed009BankIncludeTest.kt` / `Seed010NamespaceTest.kt`

### Pitfall 6: pluginTest Instead of Module Tests
**What goes wrong:** Using `pluginTest` instead of per-module test tasks; publish/test ordering race may occur
**Why it happens:** `pluginTest` republishes all 7 core modules before running; not needed for Phase 19 (no Gradle plugin changes)
**How to avoid:** Phase 19 touches only `gbkt-lang`, `gbkt-backend-gbdk`, `gbkt-examples/metasprites`, and `gbkt-examples/metasprites-stress` test sources; per-module test tasks are correct
**Warning signs:** Running `pluginTest` when only emission test changes are involved

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via `kotlin("test")` |
| Config | `useJUnitPlatform()` in each module's `build.gradle.kts` |
| Quick run | `./gradlew :gbkt-backend-gbdk:test` + `./gradlew :gbkt-lang:test` |
| Full suite | `./gradlew :gbkt-backend-gbdk:test :gbkt-lang:test :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FIX-01 | Fresh HEAD GBC screenshots SEED-004/005 (elephant tiles + checkerboard) | UAT/emulator | `./gradlew :gbkt-examples:metasprites:test --tests "*.Phase19VisualEvidenceTest"` | ❌ Wave 0 (new class needed) |
| FIX-01 | Fresh HEAD GBC screenshots SEED-006/013 (subpalette + GBC colors) | UAT/emulator | same as above | ❌ Wave 0 |
| FIX-02 SEED-007 | Actor palette slots sequential 0,1,2,3 | unit | `./gradlew :gbkt-lang:test --tests "*.Seed007GameBuilderPaletteSlotTest"` | ✅ exists |
| FIX-02 SEED-008 | Actor + metasprite set_sprite_data non-colliding | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed008VramCollisionTest"` | ✅ exists |
| FIX-02 SEED-009 | bank1.c includes metasprites.h when MoveMetasprite used | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed009BankIncludeTest"` | ✅ exists |
| FIX-02 SEED-010 | Two metasprites emit distinct descriptor names | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed010NamespaceTest"` | ✅ exists |
| FIX-02 SEED-011 | play_frame has exactly one hiwater init | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed011HiwaterFrameScopeTest"` | ✅ exists |
| Req 3 | metasprites buildRom exits 0 | build | `./gradlew :gbkt-examples:metasprites:buildRom` | ✅ (task exists) |
| Req 3 | metasprites ROM smoke screenshot | UAT/emulator | part of Phase19VisualEvidenceTest | ❌ Wave 0 |
| Req 5 | byte-identity no drift (main.c + bank1.c sha256) | manual procedure | `sha256sum build/gbkt/generated/main.c ...` | manual (D-07) |
| Req 5 | elephant.c sprite sidecar unchanged | unit | `./gradlew :gbkt-examples:metasprites:test --tests "*ByteIdentity*"` | ✅ exists |

### Sampling Rate

- **Per task commit:** `./gradlew :module:spotlessApply :module:detekt :module:test` (D-09 gate)
- **Per wave merge:** Full suite: `:gbkt-backend-gbdk:test :gbkt-lang:test :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test`
- **Phase gate:** All above GREEN + 4 FIX-01 screenshots exist + 19-AUDIT-FIX-02.md authored + byte-identity sha256 diff shows no change

### Wave 0 Gaps

- [ ] `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt` — new GBC-mode UAT test class for FIX-01 + ROM smoke captures

*(All other test infrastructure — 5 SEED emission guards, byte-identity baselines, framework configs — already exists.)*

---

## Runtime State Inventory

> Skipped — this is a greenfield evidence/documentation phase with no rename/refactor scope.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GBDK (lcc) | ROM buildRom | Confirmed (build artifacts exist at `build/gbkt/output/*.gb`) | 4.5.0 (per CI pin) | generateC only (no ROM); cannot capture runtime screenshots |
| metasprites.gb ROM | FIX-01 screenshots + Req 3 | ✓ (present in build/gbkt/output/) | — | Rebuild with `:gbkt-examples:metasprites:buildRom` |
| metasprites.noi symFile | StepAgent variable reads | ✓ (`build/gbkt/output/metasprites.noi` present) | — | Agent captures screenshots without variable reads |
| metasprites-stress.gb | Req 3 smoke build | ✓ (present in build/gbkt/output/) | — | Rebuild with `:gbkt-examples:metasprites-stress:buildRom` |
| metasprites-stress.noi | StepAgent (stress) | ✓ (`build/gbkt/output/metasprites-stress.noi` present) | — | — |
| Coffee-GB emulator (gbkt-emulator JAR) | StepAgent | Bundled in `gbkt-emulator` module | — | — |

**Missing dependencies with no fallback:** None — ROM, symFile, and emulator are all present.

---

## Security Domain

> Skipped — this phase adds test/evidence files only; no authentication, input validation, cryptography, or network access involved. `security_enforcement` does not apply.

---

## Package Legitimacy Audit

> Not applicable — Phase 19 installs no new external packages. All dependencies are existing project modules and already-resolved test infrastructure.

---

## Seeds Archive Verification

All 9 Phase 19 metasprite seeds confirmed in `.planning/seeds/archive/` [VERIFIED: `ls` of archive directory]:
- `SEED-004-metasprites-corrupted-tile-rendering.md`
- `SEED-005-metasprites-diagonal-bg-not-checkerboard.md`
- `SEED-006-metasprites-subpalette-global-not-synced.md`
- `SEED-007-gamebuilder-actor-palette-slot-zero-default.md`
- `SEED-008-metasprites-vram-collision-with-actors.md`
- `SEED-009-metasprites-header-missing-in-bank1.md`
- `SEED-010-metasprites-symbol-collision-multi-metasprite.md`
- `SEED-011-metasprites-hiwater-collides-multi-metasprite-per-frame.md`
- `SEED-013-gbc-palette-write-path-d-v3-visual.md`

No orphaned seeds remain in `.planning/seeds/` (non-archive location) for these 9. [VERIFIED: archive ls]

---

## Commit Discipline Gate (D-08)

PR #77 (S3776 cognitive-complexity burn-down) is confirmed open on `chore/hardening_0_1_0`. Phase 19 is on the same branch. Every Phase 19 commit MUST be verifiable as containing only:
- Evidence PNGs (`.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`)
- Audit document (`19-AUDIT-FIX-02.md`)
- New emission test file (`Phase19VisualEvidenceTest.kt`)
- Documentation updates

Commits that also contain `extract-method` refactors, `@Suppress` additions, or cognitive-complexity reductions are interleaved S3776 commits — forbidden per D-08.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Both `.gb` ROMs in `build/gbkt/output/` at research time are current-code builds | Environment Availability | Screenshots would be of a stale ROM; clean rebuild (D-03 gate) mitigates |
| A2 | `gbkt-emulator` module (Coffee-GB) correctly runs `gbcMode=true` for GBC_COMPATIBLE targets | FIX-01 Harness | Screenshots could appear DMG-mode even with gbcMode=true; mitigated by comparing with known Phase 16 GBC captures |

**If this table is empty after mitigations:** Both risks have procedural mitigations — the clean rebuild and the GBC-mode comparison checks make the assumptions safe to proceed.

---

## Open Questions (RESOLVED)

1. **Should Phase 19 produce a metasprites-stress UAT screenshot?**
   - What we know: Req 3 says `metasprites-stress:buildRom` must exit 0; no FIX-01 seed maps to stress-only visual behavior; D-02 says author a scaffold if needed
   - What's unclear: Whether Req 3 implies a stress-example runtime screenshot or only a stress buildRom success
   - Recommendation: Add a minimal smoke screenshot of the stress ROM as a good-practice deliverable (1 additional PNG, not a hard acceptance-criteria requirement per SPEC Req 1/3 wording)
   - **RESOLVED:** Not a hard requirement per SPEC Req 1/3 wording — a stress runtime screenshot is optional good practice, not an acceptance criterion. Plan 19-01 covers the mandatory `metasprites-stress:buildRom` exit-0 smoke; Plan 19-03 captures the required metasprites runtime/ROM-smoke screenshots.

2. **SEED-007 guard in gbkt-lang — does the planner need a `./gradlew :gbkt-lang:test` task in the gate?**
   - What we know: The SPEC acceptance criteria says "`:gbkt-backend-gbdk:test` (and any touched `gbkt-examples` module test) is GREEN"; SEED-007's guard is in `gbkt-lang`
   - What's unclear: Whether the SPEC's "and any touched" clause extends to `gbkt-lang` even though Phase 19 doesn't modify `gbkt-lang` sources
   - Recommendation: Include `./gradlew :gbkt-lang:test` in the verification gate as part of FIX-02 confirmation, since the audit document maps SEED-007 to that module
   - **RESOLVED:** Yes — Plan 19-02 Task 1's verify explicitly includes `:gbkt-lang:test` alongside `:gbkt-backend-gbdk:test` (SEED-007's `Seed007GameBuilderPaletteSlotTest` lives in `gbkt-lang`), and Plan 19-04's full-suite gate includes it.

---

## Sources

### Primary (HIGH confidence)
- Codebase grep + file reads: all test file paths, method names, and assertions verified by reading actual source files
- Live `build/gbkt/generated/` output: bank1.c and main.c content verified for SEED-009/010/011 patterns
- `.planning/phases/16-seed-triage/TRIAGE.md`: disposition and fix-location citations
- `.planning/phases/16-seed-triage/evidence/SEED-007/main-c-excerpt.txt`: SEED-007 bug and fix commentary

### Secondary (MEDIUM confidence)
- Project MEMORY entries (`learning_platformer_mcp_needs_gbc_mode`, `project_pong_toolchain_nondeterminism`): GBC mode capture requirement and .gb non-determinism caveat

### Tertiary (LOW confidence)
- None — all key claims were verified from codebase reads.

---

## Metadata

**Confidence breakdown:**
- FIX-02 audit (5 seeds, existing guards): HIGH — all 5 guard files read and confirmed
- FIX-01 capture harness: HIGH — MetaspriteUatTest.kt read, GBC target confirmed in DSL source
- Byte-identity oracle: HIGH — generated file paths confirmed, sha256 approach is standard
- Environment availability: HIGH — build artifacts verified present

**Research date:** 2026-06-13
**Valid until:** 2026-07-13 (30 days; stable phase, no fast-moving dependencies)
