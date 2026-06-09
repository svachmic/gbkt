---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
verified: 2026-06-09T00:00:00Z
status: passed
score: 7/7
overrides_applied: 0
re_verification: false
---

# Phase 15: Full-green test suite for v0.1.0 release — Verification

**Phase Goal:** The entire JVM test suite reports zero failures — both `./gradlew test --continue`
and `./gradlew pluginTest` pass — reached diagnose-first by fixing real bugs or correcting
provably-stale assertions, NEVER by weakening a threshold. Hard release gate for v0.1.0.

**Verified:** 2026-06-09 · **Status:** passed · **Score:** 7/7 requirements

---

## Requirement verification

| Req | Requirement | Verdict | Evidence |
|-----|-------------|---------|----------|
| REQ-1 | Full-suite green via both aggregate commands | ✅ PASS | `test --continue` BUILD SUCCESSFUL 0 failures; `pluginTest` BUILD SUCCESSFUL, IntegrationTest 19/0/0/0 (FINAL-GREEN.md) |
| REQ-2 | Re-run-first scope (fix ALL red) | ✅ PASS | FRESH-RUN-INVENTORY.md captured 18 red tests fresh; drift surfaced (PlatformerTemplateUatTest no longer red); all 18 driven green |
| REQ-3 | IntegrationTest fixed (SceneIR.copy$default skew) | ✅ PASS | 12→0; root cause = stale `gbkt-analysis` not republished; fixed via republish-set + cacheChangingModulesFor(0) (15-02) |
| REQ-4 | BanksUatTest fixed | ✅ PASS | 2→0; live D-03 screenshot proves banked checker renders (16×16 swatch); region-scoped gate, 0.95 intact (15-03) |
| REQ-5 | PongStepAgentTest fixed | ✅ PASS | 1→0; OAM expectation {1,1,1}/3 matches metadata + runtime read (15-04) |
| REQ-6 | platformer-template suite fixed (3 classes) | ✅ PASS | geometry 2→0 (repoint to png2asset, executed not skipped), 128UatTest 1→0 (sprite-region hflip + OAM xFlip), UatTest green; live D-03 (15-05) |
| REQ-7 | Diagnose-first justification per failure | ✅ PASS | DIAGNOSIS-LEDGER.md: 18 rows resolved (12 real-bug-fix, 6 provably-stale, 0 removals), zero threshold-weakening rows |

## Must-haves

- [x] `./gradlew test --continue` from a clean tree → 0 failing tests
- [x] `./gradlew pluginTest` from a clean tree → 0 failing tests (IntegrationTest green)
- [x] Phase-start fresh-run inventory recorded; every entry resolved
- [x] All 6 known classes green (IntegrationTest, BanksUatTest, PongStepAgentTest, PlatformerTemplate128UatTest, PlatformerTemplateUatTest, PlayerMetaspriteGeometryTest)
- [x] Per-failure diagnosis ledger exists; zero threshold-weakening
- [x] D-02 split guard: zero production codegen changed → all 7 KEEP examples byte-identical; 7× `:buildRom` EXIT 0
- [x] No assertion deleted/weakened to mask a failure (0.95 intact; >10% facing measure re-architected, not lowered; OAM corrected to proven value)

## No-weakening audit

Explicitly confirmed zero threshold-weakening:
- BanksUatTest `0.95` dominant-colour ratio UNCHANGED (re-scoped to the painted swatch region).
- PlatformerTemplate128UatTest `>10%` facing gate REPLACED by a sprite-region diff (≥20%, live ~45%) + OAM xFlip — a different, correct measure, not a lowered global threshold.
- PongStepAgentTest OAM expectation corrected to the proven runtime value {1,1,1}/3 — not deleted.

## Verdict

**PASSED 7/7.** The full JVM test suite is green on both canonical commands from a clean tree,
reached entirely by real-bug fixes and provably-stale-assertion corrections (each backed by static
or live-D-03 evidence), with zero threshold-weakening and zero production-codegen drift. The v0.1.0
release gate is satisfied. (Tagging v0.1.0 and re-presenting Phase 14's sign-off are downstream
manual steps, out of scope.)
