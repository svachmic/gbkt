---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
verified: 2026-06-14T15:45:00Z
status: passed
score: 9/9
overrides_applied: 0
---

# Phase 21: Codegen Fixes — Platformer and Remaining Seeds Verification Report

**Phase Goal:** All platformer `cEmit()` escape hatches are replaced by proper `PlatformerVisitor.kt` auto-emission; all remaining open seeds from FIX-06 reach terminal disposition; `.planning/seeds/` is empty at phase close.

**Verified:** 2026-06-14T15:45:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | No cEmit() escape hatches remain in platformer-template (Phase 13.5 pre-verified; Plan 21-07 UAT re-confirms) | VERIFIED | `grep -n "cEmit\|rawC" PlatformerTemplate.kt` returns 0 lines; 5 GBC anchors all assertions GREEN |
| 2 | Three platformer UAT anchor screenshots re-shot in GBC mode and all three pass assertion | VERIFIED | `evidence/uat-screenshots/anchor-1/`, `anchor-2/`, `anchor-3/` all contain PNG + JSON sidecars; `gbcMode=true` in harness (commit 71dd3a57); binding user visual sign-off recorded |
| 3 | SEED-017 and SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION explicitly re-deferred with evidence to backlog/v0.2.0 | VERIFIED | `.planning/backlog/v0.2.0/SEED-017-*.md` exists; `.planning/backlog/v0.2.0/SEED-ZONE-MAGIC-STRING-*.md` exists; RE-DEFERRAL-NOTE.md provides D-04c evidence |
| 4 | SEED-020 FIXED — GameIRSerializer deserialize() replaces all 10 emptyList() stubs | VERIFIED | Lines 196–275 of GameIRSerializer.kt show all 10 collections using `deserializeList(...)` with real deserializer blocks; `optString("id", "")` used throughout (WR-03 fix confirmed) |
| 5 | SEED-022 FIXED — shared `gameUsesTilemapCollisionPathC(GameIR)` in gbkt-backend-api; both callers delegate Path C | VERIFIED | `TilemapCollisionGate.kt` line 34: `fun gameUsesTilemapCollisionPathC`; `GBDKPipeline.kt` count=2; `PlatformerVisitor.kt` count=3 |
| 6 | SEED-021 FIXED — pivotAdjust(Int) DSL setter + config-driven visitor read; metasprite lookup dance removed | VERIFIED | `PlatformerExtensions.kt:677`: `fun pivotAdjust(v: Int)`; `PlatformerVisitor.kt:604–617`: if/else guard on tcSystem; `grep -c "Deferred (SEED-021)"` = 0 |
| 7 | FIX-05 spawn polish seeds resolved (SPAWN-POSITION-CLARITY, spawn-polish, sub-pixel) | VERIFIED | All 4 seeds archived: `seeds/archive/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md`, `seeds/archive/SEED-platformer-template-spawn-polish.md`, `seeds/archive/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md` (CLOSED-AS-ACCEPTED), `seeds/archive/SEED-021-platformer-pivot-adjust-auto-derive.md` |
| 8 | `.planning/seeds/` contains ONLY `archive/` and `evidence/` — zero loose SEED-*.md files | VERIFIED | `find .planning/seeds/ -maxdepth 1 -name "SEED-*.md"` returns empty; `ls .planning/seeds/` shows only `archive` and `evidence` |
| 9 | D-13 byte-identity: 5 untouched examples byte-identical; pong PASS*; platformer-template proven by emission tests + UAT anchors | VERIFIED | `21-BYTE-IDENTITY.md` records SHA-256 BEFORE/AFTER hashes for breakout/simple-physics/metasprites/metasprites-stress/banks (all IDENTICAL); pong generated C IDENTICAL; analytical proof documents why phase changes are gated behind `gameUsesTilemapCollision()` returning false for untouched examples |

**Score:** 9/9 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `gbkt-genre-platformer/...PlatformerExtensions.kt` | pivotAdjust(Int) setter + CONFIG_KEY_PIVOT_ADJUST constant | VERIFIED | Line 677: `fun pivotAdjust(v: Int)`, line 585: `const val CONFIG_KEY_PIVOT_ADJUST = "pivotAdjust"` |
| `gbkt-genre-platformer/...PlatformerVisitor.kt` | Config-driven pivotAdjust read; no metasprite lookup dance; no SEED-021 markers | VERIFIED | Lines 604–617: if/else guard with tcSystem null check; `grep -c "Deferred (SEED-021)"` = 0 |
| `gbkt-examples/platformer-template/...PlatformerTemplate.kt` | `pivotAdjust(2)` declared | VERIFIED | Line 184: `pivotAdjust(2)` |
| `gbkt-genre-platformer/...PlatformerSnapArithmeticEmissionTest.kt` | Emission test asserting posYSym=1888 | VERIFIED | File exists; 2 @Test methods; line 221 asserts `body.contains("1888")` |
| `gbkt-backend-api/...TilemapCollisionGate.kt` | Shared Path-C detection function | VERIFIED | File exists; line 34: `fun gameUsesTilemapCollisionPathC` |
| `gbkt-genre-platformer/...TilemapCollisionPredicateLockstepTest.kt` | Lockstep contract test (4 fixtures) | VERIFIED | File exists; 4 @Test methods |
| `gbkt-ir/...GameIRSerializer.kt` | 10 real deserializers; optString("id","") safe | VERIFIED | Lines 196–275: all 10 collections use `deserializeList` with non-empty lambdas; no `getString("id")` in new deserializers (confirmed lines 221, 231, 235, 242, 250, 254, 260, 264 all use `optString`) |
| `gbkt-ir/...GameIRSerializerRoundTripTest.kt` | Round-trip test (maximal + minimal fixture) | VERIFIED | File exists; 2 @Test methods |
| `.planning/phases/21-.../evidence/uat-screenshots/` | 3+ GBC anchor screenshot directories with PNGs | VERIFIED | anchor-1/ (2 PNGs), anchor-2/ (3 PNGs + trace), anchor-3/ (2 PNGs) + anchors 4–5 |
| `.planning/phases/21-.../21-BYTE-IDENTITY.md` | Before/after generated-C diff record | VERIFIED | File exists; records SHA-256 hashes for 5 untouched examples + pong; analytical proof section present |
| `.planning/phases/21-.../RE-DEFERRAL-NOTE.md` | D-04c evidence note for 4 re-deferred seeds | VERIFIED | File exists; documents all 4 re-deferrals with rationale |
| `.planning/seeds/archive/SEED-020/021/022/027/028/029-*.md` | Archived fixed seeds | VERIFIED | All present in `seeds/archive/` |
| `.planning/seeds/archive/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md` | Archived spawn-clarity seed | VERIFIED | Present |
| `.planning/seeds/archive/SEED-platformer-template-spawn-polish.md` | Archived spawn-polish seed | VERIFIED | Present |
| `.planning/seeds/archive/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md` | Archived sub-pixel seed (CLOSED-AS-ACCEPTED) | VERIFIED | Present |
| `.planning/backlog/v0.2.0/SEED-017-*.md` | Re-deferred sport-zone seed | VERIFIED | Present |
| `.planning/backlog/v0.2.0/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.md` | Re-deferred zone magic-string seed | VERIFIED | Present |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PlatformerTemplate.kt tilemapCollision { pivotAdjust(2) }` | `GenericSystem config["pivotAdjust"]` | `TilemapCollisionBuilder.build()` using `CONFIG_KEY_PIVOT_ADJUST` | WIRED | Line 705: `configBuilder[CONFIG_KEY_PIVOT_ADJUST] = it` |
| `PlatformerVisitor.buildTilemapPhysicsUpdateFunction` | `tcSystem.config[TilemapCollisionBuilder.CONFIG_KEY_PIVOT_ADJUST]` | Config read with if/else guard | WIRED | Line 606: `(tcSystem.config[TilemapCollisionBuilder.CONFIG_KEY_PIVOT_ADJUST] as? Int)` |
| `GBDKPipeline.gameUsesTilemapCollision` | `gameUsesTilemapCollisionPathC` | First-check delegation | WIRED | `grep -c "gameUsesTilemapCollisionPathC" GBDKPipeline.kt` = 2 (import + call) |
| `PlatformerVisitor.gameUsesTilemapCollision` | `gameUsesTilemapCollisionPathC` | First-check delegation (fixes previously-missing Path C) | WIRED | `grep -c "gameUsesTilemapCollisionPathC" PlatformerVisitor.kt` = 3 (import + call + KDoc) |
| `PlatformerTemplateUatTest.EVIDENCE_DIR` | `21-codegen-fixes-platformer-and-remaining-seeds/evidence/uat-screenshots` | String constant in companion object | WIRED | Line 51 confirmed; `gbcMode = true` on line 67 |

---

### Data-Flow Trace (Level 4)

This phase is a code-generator pipeline; no reactive/stateful UI rendering to trace. The relevant data flow is:

| Data Source | Produces Real Data | Status |
|-------------|-------------------|--------|
| `pivotAdjust(2)` in DSL → `TilemapCollisionBuilder.build()` → `config["pivotAdjust"]=2` → `PlatformerVisitor` reads `tcSystem.config[CONFIG_KEY_PIVOT_ADJUST]` → generates literal `1888` in C output | Yes — `PlatformerSnapArithmeticEmissionTest` asserts literal 1888 in generated physics-update body | FLOWING |
| `gameUsesTilemapCollisionPathC()` shared util → both GBDKPipeline and PlatformerVisitor consume identical Path-C verdict | Yes — `TilemapCollisionPredicateLockstepTest` verifies 4-fixture matrix | FLOWING |
| `GameIRSerializer.deserialize()` → 10 previously-empty collections now populated from JSON | Yes — `GameIRSerializerRoundTripTest` asserts non-empty IDs after round-trip | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| pivotAdjust(Int) setter exists in PlatformerExtensions.kt | `grep -n "fun pivotAdjust" PlatformerExtensions.kt` | Line 677 | PASS |
| pivotAdjust(2) declared in platformer-template | `grep -n "pivotAdjust(2)" PlatformerTemplate.kt` | Line 184 | PASS |
| SEED-021 markers removed from PlatformerVisitor | `grep -c "Deferred (SEED-021)" PlatformerVisitor.kt` | 0 | PASS |
| SEED-022 markers removed from PlatformerVisitor | `grep -c "Deferred (SEED-022)" PlatformerVisitor.kt` | 0 | PASS |
| CONFIG_KEY_PIVOT_ADJUST constant exists (WR-02 fix) | `grep -n "CONFIG_KEY_PIVOT_ADJUST" PlatformerExtensions.kt` | Lines 585, 705 | PASS |
| WR-01: tcSystem null-guard protects warning (no spurious warning for Path A/B) | Read PlatformerVisitor.kt:604–617 | `if (tcSystem != null)` guard present; `else` branch silently uses fallback | PASS |
| WR-03: optString("id","") in new deserializers (no crash risk) | Read GameIRSerializer.kt:221,231,235,242,250,254,260,264 | All use `optString("id", "")` | PASS |
| seeds/ directory contains only archive/ and evidence/ | `find .planning/seeds/ -maxdepth 1 -name "SEED-*.md"` | Empty — no loose seeds | PASS |
| 3 anchor screenshot dirs with PNGs | `ls evidence/uat-screenshots/anchor-{1,2,3}/` | All 3 contain PNG files | PASS |
| gbcMode=true in UAT harness | `grep -n "gbcMode" PlatformerTemplateUatTest.kt` | Line 67: `.copy(gbcMode = true)` | PASS |

---

### Rom-Build Gate (verifier-gates.md)

`GBDKPipeline.kt` was modified in this phase, triggering the mandatory rom-build gate.

| Gate | Command | Result | Status |
|------|---------|--------|--------|
| rom-build (GBDKPipeline.kt changed) | `./gradlew :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom` | BUILD SUCCESSFUL; ROM 32 KB created | PASS |

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| FIX-05 | 21-01, 21-07 | Platformer seeds: pivotAdjust auto-derive, spawn polish, sub-pixel acceptance | SATISFIED | REQUIREMENTS.md traceability: `FIX-05 \| Phase 21 \| Complete`; 4 platformer seeds archived |
| FIX-06 | 21-02, 21-03, 21-04, 21-05, 21-06, 21-08 | Small DSL/tooling seeds dispositioned | SATISFIED | REQUIREMENTS.md traceability: `FIX-06 \| Phase 21 \| Complete`; SEED-020 + SEED-022 FIXED; SEED-017 + SEED-ZONE-MAGIC-STRING re-deferred with evidence |

---

### Anti-Patterns Found

| File | Pattern | Severity | Status |
|------|---------|----------|--------|
| (none) | No TBD/FIXME/XXX markers found in Phase 21 modified files | — | CLEAN |

Code review found 3 warnings + 2 info findings (WR-01 spurious warning, WR-02 magic string, WR-03 crash risk, IN-01 KDoc inaccuracy, IN-02 stale comment). All 5 were fixed in commits 8297f895 and ffeaaae5, verified in the codebase.

---

### Human Verification Required

None. The binding human visual sign-off on GBC-mode UAT anchor re-shoot was already obtained during Plan 21-07 (recorded in 21-07-SUMMARY.md: "binding user sign-off obtained on the GBC re-shoot"). Per the Visual Evidence Rule and the prompt's explicit instruction, this prior sign-off satisfies Criterion 2.

---

### Gaps Summary

No gaps. All 9 truths verified. All success criteria satisfied. FIX-05 and FIX-06 closed.

---

_Verified: 2026-06-14T15:45:00Z_
_Verifier: Claude (gsd-verifier)_
