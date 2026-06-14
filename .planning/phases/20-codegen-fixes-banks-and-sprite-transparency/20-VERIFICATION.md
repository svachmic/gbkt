---
phase: 20-codegen-fixes-banks-and-sprite-transparency
verified: 2026-06-14T12:00:00Z
status: human_needed
score: 3/5
overrides_applied: 0
human_verification:
  - test: "Inspect evidence/fix-04/metasprites-sprite-outline.png and confirm the elephant metasprite renders without a black spurious outline — specifically, that transparent pixels around the elephant have no black border artifact. Accept only if the elephant silhouette shows no solid dark outline on the checkerboard background."
    expected: "The elephant sprite displays clean transparent edges on the checkerboard background. No black rectangle or contour outline is visible around the sprite — the GBC sub-palette correctly maps tRNS index to OBJ colour 0."
    why_human: "The mechanical gate (assertScreenshotIsNonUniform) only checks that >=2 colours exist and dominant ratio <95%. A sprite WITH the black outline (the regression class FIX-04 fixes) is MORE colourful and passes the gate. The gate cannot distinguish a clean outline from a broken one. Additionally, WR-01 (from 20-REVIEW.md): waitForScene return value is discarded in MetaspritePhase20OracleTest.kt:182, so the test cannot assert the 'play' scene was actually reached before capture; a boot failure would silently capture a different frame and still pass the non-blank gate."
  - test: "Inspect evidence/fix-04/platformer-player-transparency.png and confirm the platformer player character renders with no regression in sprite transparency — no spurious black outline around the player sprite."
    expected: "The player character (running right on the green grassy level) displays with correct GBC colours and clean transparent edges. No black outline or border artifact is visible around the player sprite."
    why_human: "Same mechanical gate limitation as oracle #1. Additionally, WR-01 applies: PlatformerTemplatePhase20OracleTest.kt:204 discards the waitForScene('gameplay') return value. If the title→gameplay scene transition never occurred (boot regression), the screenshot could be the title card, which also passes the non-blank gate. The precedent test (PlatformerTemplate128UatTest.kt:218-224) explicitly asserts assertEquals('gameplay', gameplayObs.scene) — this new oracle omitted that assertion."
---

# Phase 20: Codegen Fixes — Banks Trio and Sprite Transparency Verification Report

**Phase Goal:** The banks trio seeds (SEED-014/015/016) are resolved after a mandatory discuss-phase gate; the tRNS sprite outline defect is fixed without regressing platformer player transparency; SEED-014 re-verified first since the `hasZoneSceneBinder` guard may already satisfy it on master.
**Verified:** 2026-06-14T12:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SEED-014 confirmed closed: BanksEmissionTest.kt INV-2 sentinel GREEN; formal Phase 20 evidence artifact; seed in seeds/archive/ | VERIFIED | INV-2 PASSED in evidence/fix-03/inv2-emission-run.txt (BUILD SUCCESSFUL, 0 failures). INV-6 companion PASSED in inv6-emission-run.txt. SEED-014-banks-bkg-tiles-load-banked-gating.md is in .planning/seeds/archive/. 20-AUDIT-FIX-03.md is the formal evidence artifact with 1:1 seed→guard mapping table. |
| 2 | SEED-015 and SEED-016 confirmed closed: formal evidence artifacts per triage findings; discuss-phase gate scope review | VERIFIED | INV-5 PASSED in evidence/fix-03/inv5-emission-run.txt (SEED-015). Anchor 4 SRAM round-trip PASSED in evidence/fix-03/anchor4-sram-run.txt (SEED-016, with GBDK_HOME available). SEED-015 and SEED-016 are in .planning/seeds/archive/. 20-AUDIT-FIX-03.md documents the guard→seed 1:1 mapping for all three. |
| 3 | Sprite-outline tRNS defect confirmed closed — a runtime screenshot at HEAD confirms the outline renders without corruption (D-08 visual oracle for Phase 13.6 fix) | UNCERTAIN — human needed | evidence/fix-04/metasprites-sprite-outline.png exists (1423 bytes, 160x144, 4 distinct colours, dominant ratio 0.4978). Mechanical non-blank gate PASSED. VISUAL INSPECTION by verifier shows the elephant on the checkerboard with what appears to be a clean outline, but (a) the mechanical gate does not prove transparency correctness; and (b) WR-01: waitForScene return value discarded — the test cannot confirm 'play' scene was reached. Human sign-off required per Visual Evidence Rule. |
| 4 | Platformer player transparency confirmed unchanged — a runtime screenshot at HEAD shows no regression | UNCERTAIN — human needed | evidence/fix-04/platformer-player-transparency.png exists (1200 bytes, 160x144, 7 distinct colours, dominant ratio 0.8599). Mechanical non-blank gate PASSED. VISUAL INSPECTION by verifier shows the GBC platformer player on a grassy level scene, which looks like the gameplay scene (not title). However: WR-01 applies — waitForScene('gameplay') return discarded at line 204, so no mechanical proof of correct scene. Human sign-off required per Visual Evidence Rule. |
| 5 | A 7-example byte-identity ROM sweep passes after every commit in this phase | VERIFIED | evidence/byte-identity/phase-close-sweep.txt contains 14 .c file hashes across all 7 examples. Affected examples (banks/metasprites/platformer-template) are byte-identical to their Task 1 baselines: 7/7 files MATCH. Zero generated-C drift. D-07 no-codegen-change confirmed. |

**Score:** 3/5 truths verified (2 require human visual sign-off)

### Deferred Items

None identified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `evidence/fix-03/inv2-emission-run.txt` | INV-2 GREEN console output (SEED-014) | VERIFIED | Exists, 94 lines, contains BUILD SUCCESSFUL, test PASSED |
| `evidence/fix-03/inv5-emission-run.txt` | INV-5 GREEN console output (SEED-015) | VERIFIED | Exists, contains BUILD SUCCESSFUL, INV-5 PASSED |
| `evidence/fix-03/inv6-emission-run.txt` | INV-6 GREEN console output (SEED-014) | VERIFIED | Exists, contains BUILD SUCCESSFUL, INV-6 PASSED |
| `evidence/fix-03/anchor4-sram-run.txt` | Anchor 4 GREEN console output (SEED-016) | VERIFIED | Exists, Anchor 4 PASSED, GBDK_HOME=/Users/michalsvacha/gbdk used |
| `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/20-AUDIT-FIX-03.md` | FIX-03 banks-trio emission-guard audit doc | VERIFIED | Exists, contains 1:1 seed→guard table with SEED-014/015/016, INV-2/INV-5/INV-6/Anchor 4, Decisions Captured table |
| `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt` | Phase 20 FIX-04 sprite-outline capture @Test | VERIFIED (substantive + wired) | Exists, 9322 bytes, contains captureAndRename, gbcMode=true, @Test method |
| `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt` | Phase 20 FIX-04 player-transparency @Test | VERIFIED (substantive + wired) | Exists, 10878 bytes, contains gbcMode=true, @Test method |
| `evidence/fix-04/metasprites-sprite-outline.png` | D-08 visual oracle #1 (Success Criterion 3) | VERIFIED (exists + non-blank) — visual truth UNCERTAIN | 1423 bytes, 160x144 GBC screenshot, 4 distinct colours. Mechanical gate passed. Scene-correctness not asserted by test (WR-01). |
| `evidence/fix-04/platformer-player-transparency.png` | D-08 visual oracle #2 (Success Criterion 4) | VERIFIED (exists + non-blank) — visual truth UNCERTAIN | 1200 bytes, 160x144 GBC screenshot, 7 distinct colours. Mechanical gate passed. Scene-correctness not asserted by test (WR-01). |
| `evidence/byte-identity/phase-close-sweep.txt` | Full 7-example sha256 sweep at phase close | VERIFIED | Exists, 14 .c hash lines, all 7 examples covered, affected examples byte-identical to baselines |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| BanksEmissionTest INV-2/INV-6 | GBDKPipeline.kt:1428 hasZoneSceneBinder guard | emitted _bkg_tiles_load_banked wrapper + play_enter call | VERIFIED | INV-2 asserts wrapper with SWITCH_ROM in main.c; INV-6 asserts play_enter calls _bkg_tiles_load_banked(2u,...) in bank1.c. Both GREEN per evidence files. |
| 20-AUDIT-FIX-03.md SEED rows | BanksEmissionTest.kt / BanksUatTest.kt assertions | 1:1 seed→guard mapping table | VERIFIED | Audit doc contains all four assertion names (INV-2, INV-5, INV-6, Anchor 4); confirmed at lines 200, 408, 463, 291 respectively via grep. |
| MetaspritePhase20OracleTest captureAndRename | evidence/fix-04/metasprites-sprite-outline.png | StepAgent.captureScreenshot + rename to Phase-20 EVIDENCE_DIR | VERIFIED (mechanically) | PNG at expected path, 1423 bytes. But scene-correctness gate is missing (WR-01 from 20-REVIEW.md). |
| PlatformerTemplatePhase20OracleTest gbcMode capture | evidence/fix-04/platformer-player-transparency.png | AgentSessionConfig.discoverFiles(...).copy(gbcMode=true) + captureAndRename | VERIFIED (mechanically) | PNG at expected path, 1200 bytes. But scene-correctness gate is missing (WR-01 from 20-REVIEW.md). |

### Data-Flow Trace (Level 4)

Not applicable — this is a confirmation/evidence phase with no new production code. Test classes produce evidence PNGs; data flows verified by artifact existence and perceptual side-car files.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| BanksEmissionTest INV-2 exists and was GREEN | `grep -q "BUILD SUCCESSFUL" evidence/fix-03/inv2-emission-run.txt` | "BUILD SUCCESSFUL" present; testcase PASSED in XML | PASS |
| BanksEmissionTest INV-5 exists and was GREEN | `grep -q "BUILD SUCCESSFUL" evidence/fix-03/inv5-emission-run.txt` | "BUILD SUCCESSFUL" present | PASS |
| BanksEmissionTest INV-6 exists and was GREEN | `grep -q "BUILD SUCCESSFUL" evidence/fix-03/inv6-emission-run.txt` | "BUILD SUCCESSFUL" present | PASS |
| BanksUatTest Anchor 4 SRAM PASSED | `grep -q "Anchor 4 SRAM" evidence/fix-03/anchor4-sram-run.txt` | Anchor 4 PASSED, ROM freshly built | PASS |
| SEED-014/015/016 in archive | `ls .planning/seeds/archive/SEED-01{4,5,6}*` | All three present | PASS |
| 7-example sweep covers all examples | `grep -c '\.c$' evidence/byte-identity/phase-close-sweep.txt` | 14 .c files counted (pong:2, breakout:2, simple-physics:1, metasprites:1, metasprites-stress:2, banks:3, platformer-template:3) | PASS |
| MetaspritePhase20OracleTest has gbcMode=true | `grep 'gbcMode = true' MetaspritePhase20OracleTest.kt` | Line 74: `.copy(gbcMode = true)` | PASS |
| PlatformerTemplatePhase20OracleTest has gbcMode=true | `grep 'gbcMode = true' PlatformerTemplatePhase20OracleTest.kt` | Line present | PASS |

### Probe Execution

Not applicable — no probe scripts declared for Phase 20.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| FIX-03 | 20-01, 20-02 | Banks trio (SEED-014/015/016) confirmed closed — re-verify + audit guard | SATISFIED | INV-2/INV-5/INV-6 GREEN evidence files; Anchor 4 GREEN evidence; 20-AUDIT-FIX-03.md formal audit; all 3 seeds in archive/ |
| FIX-04 | 20-03, 20-04 | Sprite-outline tRNS visual oracle confirmation | PARTIALLY SATISFIED — visual truth needs human | Oracle PNGs exist and pass mechanical non-blank gate; visual correctness requires human sign-off per Visual Evidence Rule; WR-01 weakens mechanical proof |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| MetaspritePhase20OracleTest.kt | 182 | `agent.waitForScene("play", 120)` — return value discarded | Warning | Cannot mechanically confirm 'play' scene reached before screenshot; a boot regression would silently capture the wrong scene. Flagged in 20-REVIEW.md as WR-01. |
| PlatformerTemplatePhase20OracleTest.kt | 204 | `agent.waitForScene("gameplay", maxFrames = 60)` — return value discarded | Warning | Same as above. Precedent (PlatformerTemplate128UatTest.kt:218-224) uses `val gameplayObs = agent.waitForScene(...); assertEquals("gameplay", gameplayObs.scene)`. That assertion was dropped. |
| MetaspritePhase20OracleTest.kt | 91 | `check(captured.renameTo(target))` — hard fail on renameTo false | Info | Platform-dependent renameTo can fail for non-error reasons (cross-volume, transient lock). WR-03 from 20-REVIEW.md. Non-blocking for local development. |
| PlatformerTemplatePhase20OracleTest.kt | ~108 | Same renameTo pattern | Info | Same as above. |

**No debt markers (TBD/FIXME/XXX) found in any Phase 20 modified files.**

### ROM-Build Smoke Gate

Per `.planning/verifier-gates.md`: the gate fires when `gbkt-backend-gbdk/src/main/kotlin/.../pipeline/GBDKPipeline.kt`, `codegen/visitor/**`, `BankingAnalysisPass.kt`, `GenerateCTask.kt`, or `CompileRomTask.kt` are modified. Phase 20 modified ONLY test files (`MetaspritePhase20OracleTest.kt`, `PlatformerTemplatePhase20OracleTest.kt`) and planning/evidence documents. No production codegen files were touched. Gate does NOT fire.

The byte-identity sweep in Plan 04 provides equivalent assurance: 7-example generateC sweep with zero drift confirms no codegen regression.

### Human Verification Required

#### 1. Metasprites Elephant Sprite-Outline Clean (FIX-04 Success Criterion 3)

**Test:** Open `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png` in an image viewer.

**Expected:** The cyan elephant metasprite appears on the black-and-white checkerboard background with clean transparent edges. No solid black rectangle, contour, or border outline should be visible around the elephant sprite. The transparent pixels outside the elephant silhouette should show the checkerboard tile pattern through (not a black fill).

**Why human:** The mechanical gate (`assertScreenshotIsNonUniform`) only verifies the image is non-blank — it counts distinct colours and rejects solid-colour frames. A sprite WITH the regression-class black outline would be MORE colourful and pass the gate. The gate cannot distinguish clean transparency from corrupted transparency. Additionally, per WR-01 (20-REVIEW.md): the test's `agent.waitForScene("play", 120)` discards its return value, so if a scene transition regression occurred, the capture could be an earlier frame. The precedent test (`PlatformerTemplate128UatTest.kt:218-224`) explicitly asserts the scene name before capture; this oracle omitted that assertion.

---

#### 2. Platformer Player Transparency No Regression (FIX-04 Success Criterion 4)

**Test:** Open `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png` in an image viewer.

**Expected:** The platformer player character is visible on a green grassy level background (world1Area1/gameplay scene in GBC mode), mid-traversal to the right. The player sprite should display correct GBC colours (not DMG grayscale, not colour-inverted) and show clean transparent edges — no spurious black outline or dark border around the player sprite. This confirms the Phase 13.6 tRNS fix did not regress platformer rendering.

**Why human:** Same non-blank-gate limitation as oracle #1. Additionally, WR-01 applies: `agent.waitForScene("gameplay", maxFrames=60)` at line 204 discards its return value. If the title→gameplay scene transition did not fire (e.g., banked tilemap load failure or START-mapping change), the screenshot could capture the title card, which passes the non-blank gate. The verifier's own visual inspection of the PNG shows a green-level GBC scene with a player sprite, strongly suggesting the correct scene was captured — but this is observational evidence, not mechanically asserted.

---

### Gaps Summary

No BLOCKER gaps were found. All FIX-03 Success Criteria (1, 2, 5) are fully verified by automated evidence. The two human verification items (Success Criteria 3 and 4) are the only items blocking phase sign-off.

**What automated evidence can prove:**
- Both oracle PNGs exist and contain real rendered content (non-blank, multi-colour GBC screenshots)
- The mechanical non-blank gate passed for both
- Both test classes compile and ran to BUILD SUCCESSFUL against freshly-built ROMs
- Both use gbcMode=true (GBC_COMPATIBLE target)
- No codegen drift across Phase 20 (byte-identity sweep)

**What human sign-off must confirm:**
- The elephant has no black spurious outline (the FIX-04 regression class)
- The platformer player has no regression in transparency rendering
- The captured scene is the correct one (gameplay, not title/boot) — the WR-01 gap means this is not mechanically asserted

**WR-01 note for future phases:** The oracle tests should be patched to capture the `waitForScene` return value and assert the scene name before the screenshot, matching the established precedent at `PlatformerTemplate128UatTest.kt:218-224`. This does not block current phase sign-off (the fix is to the oracle harness, not the codegen), but it should be done before the next oracle capture cycle.

---

_Verified: 2026-06-14T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
