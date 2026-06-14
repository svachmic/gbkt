---
gsd_state_version: 1.0
milestone: v0.1.1
milestone_name: Hardening
status: completed
stopped_at: Phase 22 context gathered
last_updated: "2026-06-14T18:34:29.836Z"
last_activity: 2026-06-14
progress:
  total_phases: 7
  completed_phases: 6
  total_plans: 65
  completed_plans: 68
  percent: 86
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-17)

**Core value:** The framework automatically manages Game Boy hardware resources (VRAM, banking, OAM, RAM) so the developer writes only declarative Kotlin DSL — like Jetpack Compose for Game Boy.
**Current focus:** Phase 22 — golden-screenshot-and-evidence-storage-overhaul (added 2026-06-14; not yet planned). Milestone v0.1.1 reopened: Phase 21 closed the seed backlog, but the milestone's own visual/emission evidence is stored via the broken per-phase EVIDENCE_DIR pattern (27 tests) and must be fixed before milestone close / PR #77 merge.

## Deferred Items

Items acknowledged and deferred at milestone close on 2026-06-09 (v0.1.0). All triaged as out-of-v0.1.0-scope; none block the full-green release gate. Full detail lives in the archived `milestones/v0.1.0-ROADMAP.md` and the source artifacts under `.planning/`.

| Category | Count | Disposition |
|----------|-------|-------------|
| Backlog seeds | 35 | 34 dormant + 1 active (SEED-013 GBC palette D-V3, Phase 10.2 driver) — future-milestone backlog |
| Verification gaps | 9 | Historical `human_needed`/`gaps_found` from shipped/superseded phases (05, 05.05, 05.05.1, 06, 06.11, 06.12, 07.4, 11, 12.4) |
| UAT gaps | 4 | Status flags only — all report 0 pending scenarios (07.4, 09, 09.1, 12) |
| Codegen todos | 5 | Advisory/low-medium; 3 are DSL-primitive correctness smells unreached by shipping examples |
| Debug sessions | 2 | `knowledge-base` is the resolved-sessions KB (false-positive); `racer-bg-tilemap-not-rendered` targets the retired racer example |
| Quick task | 1 | `260605-eqr-fix-three-test-infra-issues` (test-infra cleanup) |
| **Total** | **56** | Recorded as deferred tech debt; review via `/gsd-review-backlog` next milestone |

Known Gaps (4 pending requirements + deferred genre/cleanup phases) are recorded in `MILESTONES.md` under the v0.1.0 entry.

Phase 12.1 SHIPPED 2026-05-22 (14/14) — codegen contract reconciliation pitstop. Defects 2/3/4/5 closed by Plans 12.1-01..09; Defect 6 (BANK macro vs data-array #pragma bank mismatch) surfaced during 12.1-10 terminal smoke and closed by gap-closure Plans 12.1-11/12/13/14 via option (c-prime) literal bank substitution. 6-ROM regression sweep GREEN; full JVM suite GREEN with 9 emission-invariant tests. Code review: 0 critical / 4 warning / 3 info (committed at 12.1-REVIEW.md — WR-01 fallback-comment claim is load-bearing follow-up).

Phase 12 Wave 11 (Plan 12-18 preflight buildRom): RE-CONFIRMED GREEN 2026-05-22 — Phase 12.1's Defect-2..6 closures hold; clean :buildRom produces 64 KB ROM, 4 banks, 0 errors. Resume signal from 12.1 was satisfied.

Phase 12 Wave 12 (Plan 12-19, UAT Anchor 1 title→gameplay): BLOCKED 2026-05-22 — visual UAT exposed Defect 7 in the upstream substrate (ConvertZoneTilesetsTask). Plan 12-19 lands three commits on `feat/d_and_d_gaps`:

  - `733770d6` feat(12-19): implement anchor1 UAT test (also inline-fixes 3 codegen contract bugs: sceneHasEnterContent for zone-only scenes, fill_bkg_rect-after-zone-substrate ordering in title/nextLevel enter, level-switch startup sentinel — latter reverted by e7e1bd48)
  - `e7e1bd48` fix(12-19): wire gameplay zone tileset+tilemap load via gameplay_enter cEmit + `_next_level=0` init (gameplay screenshot now visually distinct from title screenshot)
  - `29c6fa1a` docs(12-19): catalog ConvertZoneTilesetsTask synthetic-tilemap defect; escalate to Phase 12.2
  These commits are KEPT (net-positive). Plan 12-19 stays incomplete (no SUMMARY.md); 12.2 must ship before 12-19 can re-shoot screenshots and close.

  - Defect 7: `ConvertZoneTilesetsTask.synthesizeScreenTilemap()` (Plan 11.1-17 origin) emits a 32×32 modulo-tiled ramp of the png2asset `_tileset_map` dedup table instead of extracting the source PNG's real tile layout via png2asset's `-map -maps_only -source_tileset <tileset>` mode. Reference World1Area1_map[1920] (60×32, sky/platforms/ground rows) vs our `_zone_world1Area1Zone_tilemap[32*32]` (1024 bytes of `[0,1,2,3,4,5,6,7, 0,1,...]` repeat). Also explains title-screen "GBDK-2020 PLATFORMER TEMPLATE" doubling — title-screen.png is 20×18 tiles loaded as 32×32 with vertical wrap. Affects 5 zones in Phase 12 + retroactive Phase 11 Banks (checkerboard tilemap happened to coincide with the synthetic shape, masking the defect for 3 phases). Full evidence: `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/anchor-1-blocked-converttilesets-synthetic-tilemap.md`. Source: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt:432-439`.

## Current Position

Phase: 22
Plan: Not planned
Status: Milestone v0.1.1 REOPENED — Phase 22 (golden/evidence storage overhaul) added; Phases 16–21 complete
Last activity: 2026-06-14

Resume signal: Phase 22 added but not planned. Wide blast radius (27 phase-pinned test classes + harness + .gitignore + golden migration) → route /gsd-spec-phase 22 → /gsd-discuss-phase 22 → /gsd-plan-phase 22 WITH research. Do NOT execute inline. PR #77 (chore/hardening_0_1_0) should merge only after Phase 22 stores this milestone's evidence correctly.

### Roadmap Evolution

- Phase 22 added 2026-06-14: Golden Screenshot and Evidence Storage Overhaul (FIX-07). Reopens v0.1.1 because the milestone's visual/emission evidence — its own proof of every codegen fix — is stored via the broken per-phase EVIDENCE_DIR pattern (archived phases regenerate as untracked garbage; current phases churn capturedAt sidecars; GBC ROMs captured in DMG render inverted). USER chose "new phase in v0.1.1, before merge". Source: backlog/v0.2.0/SEED-GOLDEN-SCREENSHOT-STORAGE-OVERHAUL.md (to be moved into the phase). See [[project_golden_screenshot_storage_decision]].

### Bisect state as of Plan 06d complete

- **scratch/bisect HEAD:** 2767fab7 (probe-A baseline; C-4 commits a7aacaa2 + 636c9ddf dropped after evidence capture)
- **Probe A verdict:** REGRESSION-NAMED: no — CYAN=YES, CHECKER=YES, OCPD slot 2=0x7FFF
- **Sub-probe C-1 verdict:** CYAN PRESERVED — constant declaration alone does NOT break cyan; Emission #1 CLEARED
- **Sub-probe C-2 verdict:** CYAN PRESERVED — constant + set_bkg_palette call do NOT break cyan; Emission #2 CLEARED (individually)
- **Sub-probe C-3 verdict:** CYAN PRESERVED — bgFillCheckerboard hoist alone does NOT break cyan; Emission #3 CLEARED (individually)
- **Sub-probe C-4 verdict:** CYAN PRESERVED — constant + bgFillCheckerboard (no set_bkg_palette) do NOT break cyan; pair #1+#3 CLEARED
- **MINIMAL BREAKING PAIR CONFIRMED:** Emissions #2 + #3 = (set_bkg_palette + bgFillCheckerboard hoist); constant is compile-time required but interaction-inert
- **Plan 07 framing:** Scope-shift — defect is in OAM attribute / VRAM init sequence interaction, not the palette write path (which is CORRECT in all probes); fix-target = buildMainFunction() emission ordering in GBDKPipelineV2.kt
- **Drift hygiene:** CLEAN — C-4 sub-probe commits (a7aacaa2, 636c9ddf) isolated to worktrees/bisect/HEAD reflog only

Verified: Phase 07.9 (c-codegen-signed-vs-unsigned-literal-discipline) — Option C architectural fix; SignedComparisonLiteralEmissionTest 8/8 GREEN; PlatformerJumpCancelAndFrictionProbe 2/2 GREEN; Round8CameraMonotonicityProbe GREEN (Plan 07.4-32 RED gate closed); CLiteralAuditScanTest GREEN; 6/9 example ROMs build clean (3 pre-existing RPG const/extern SDCC errors not caused by 07.9); surgical-diff captured under evidence/surgical-diffs/. Full test suite: 158 tests; the 2 Plan 07.4-33 RED tests (TrackSynthesizerCircuitShapeTest) are now GREEN — closed by Plan 07.4-35 (commit `8d4c56e2`, 2026-05-21). See evidence/d-09-1-test-suite-status.md.
Verified: Phase 07.3 (entity-pool-codegen-fix-inserted) — fix commit `191c8f4c` resolves the deterministic frame-188 RAM corruption regression. Static OAM layout replaces dynamic free list; forEachActive re-checks active flag before display sync. Shmup UAT task 2 PASS. Debug session: `.planning/debug/shmup-073-ram-corruption.md` (resolved).
Paused: Phase 07.2 (interactive-game-uat) — plan 07.2-02 blocked on Racer fix. Plans 07.2-03 / 07.2-04 / 07.2-05 untouched.
Paused: Phase 07.4 (sport-genre-codegen-fix-inserted) — Plan 07.4-35 (TrackSynthesizer GREEN) CLOSED 2026-05-21 (commit `8d4c56e2` production + `docs(07.4-35)` evidence). JVM + codegen + runtime tiers all GREEN; mismatch_count vs expected = 0 (was 55). Plan 07.4-36 (round-8 visual UAT aggregator) is now the only outstanding work — ready for /gsd-execute-phase 07.4 --gaps-only.
Backlog: minor Shmup gameplay polish — pool-pool collision lacks destroy-on-hit (F-A); destroyAll leaves stale OAM positions for 1 frame on scene re-entry (F-B). Documented in UAT-shmup.md.

## Performance Metrics

**Velocity:**

- Total plans completed: 389
- Average duration: 6.4 min
- Total execution time: 0.71 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-ir-foundation-and-dsl | 5 | 33 min | 6.6 min |
| 02-structured-codegen-and-migration-cut | 1 | 6 min | 6 min |
| 07.9 | 6 | - | - |
| 09 | 7 | - | - |
| 09.1 | 6 | - | - |
| 09.2 | 5 | - | - |
| 09.3 | 4 | - | - |
| 09.4 | 4 | - | - |
| 10 | 20 | - | - |
| 10.1 | 22 | - | - |
| 10.2 | 15 | - | - |
| 11 | 13 | - | - |
| 11.2 | 12 | - | - |
| 11.1 | 17 | - | - |
| 12.1 | 14 | - | - |
| 12.2 | 13 | - | - |
| 12.3 | 15 | - | - |
| 12.5 | 14 | - | - |
| 12.6 | 8 | - | - |
| 12.7 | 29 | - | - |
| 12 | 28 | - | - |
| 12.10 | 4 | - | - |
| 12.11 | 4 | - | - |
| 13.1 | 10 | - | - |
| 13.2 | 7 | - | - |
| 13.3 | 22 | - | - |
| 13.4 | 11 | - | - |
| 13.5 | 8 | - | - |
| 13.6 | 7 | - | - |
| 13.7 | 7 | - | - |
| 13.8 | 7 | - | - |
| 15 | 6 | - | - |
| 16 | 10 | - | - |
| 19 | 4 | - | - |
| 20 | 4 | - | - |
| 21 | 8 | - | - |

**Recent Trend:**

- Last 5 plans: 9 min, 7 min, 5 min, 8 min, 6 min
- Trend: stable

*Updated after each plan completion*

| Phase/Plan | Duration | Tasks | Files |
|------------|----------|-------|-------|
| Phase 18 P26 | 3 min | 2 tasks | 2 files |
| Phase 18 P23 | 6 min | 2 tasks | 2 files |
| Phase 18 P22 | 8 min | 2 tasks | 2 files |
| Phase 18 P20 | 3 min | 2 tasks | 1 file |
| Phase 18 P16 | 4 min | 2 tasks | 1 file |
| Phase 02 P04 | 12 min | 2 tasks | 5 files |
| Phase 03-asset-pipeline-and-jvm-test-runner P01 | 6 | 2 tasks | 10 files |
| Phase 03 P03 | 13 | 2 tasks | 5 files |
| Phase 03 P04 | 3 | 2 tasks | 3 files |
| Phase 03-asset-pipeline-and-jvm-test-runner P02 | 5 | 1 tasks | 2 files |
| Phase 03.1-collection-abstractions P01 | 8 | 2 tasks | 9 files |
| Phase 03.1-collection-abstractions P02 | 7 | 2 tasks | 6 files |
| Phase 03.1-collection-abstractions P03 | 7 | 2 tasks | 5 files |
| Phase 04-analysis-pass-pipeline P01 | 12 | 1 tasks | 10 files |
| Phase 04-analysis-pass-pipeline P02 | 7 | 2 tasks | 7 files |
| Phase 04-analysis-pass-pipeline P04 | 3 | 1 tasks | 2 files |
| Phase 04-analysis-pass-pipeline P03 | 5 | 1 tasks | 2 files |
| Phase 04-analysis-pass-pipeline P05 | 14 | 2 tasks | 4 files |
| Phase 04 P06 | 15 | 2 tasks | 4 files |
| Phase 04 P07 | 11 | 2 tasks | 15 files |
| Phase 04-analysis-pass-pipeline P07 | 11 | 2 tasks | 15 files |
| Phase 04-analysis-pass-pipeline P08 | 5 min | 2 tasks | 5 files |
| Phase 04-analysis-pass-pipeline P09 | 3 | 2 tasks | 8 files |
| Phase 05-integration-and-end-to-end-validation P01 | 5 min | 2 tasks | 4 files |
| Phase 05-integration-and-end-to-end-validation P02 | 13 min | 1 tasks | 6 files |
| Phase 05-integration-and-end-to-end-validation P03 | 14 min | 1 tasks | 6 files |
| Phase 05 P04 | 4 | 2 tasks | 2 files |
| Phase 05.05-v2-source-map-implementation P01 | 4 min | 3 tasks | 11 files |
| Phase 05.05 P02 | 5 | 2 tasks | 8 files |
| Phase 05.05 P03 | 6 min | 3 tasks | 5 files |
| Phase 05.05.1-v2-codegen-runtime-completion P01 | 5 | 2 tasks | 10 files |
| Phase 05.05.1-v2-codegen-runtime-completion P02 | 9 | 2 tasks | 42 files |
| Phase 05.05.1-v2-codegen-runtime-completion P03 | 15 | 2 tasks | 4 files |
| Phase 05.05.1-v2-codegen-runtime-completion P04 | 12 | 4 tasks | 4 files |
| Phase 05.05.1-v2-codegen-runtime-completion P05 | 18 | 1 task complete (awaiting checkpoint) | 7 files |
| Phase 05.05.1-v2-codegen-runtime-completion P06 | 12 | 3 tasks | 2 files |
| Phase 05.05.1-v2-codegen-runtime-completion P07 | 8 | 3 tasks | 9 files |
| Phase 05.05.2-v2-dsl-ergonomics P01 | 6 | 2 tasks | 7 files |
| Phase 05.05.2-v2-dsl-ergonomics P02 | 9 | 2 tasks | 9 files |
| Phase 05.05.3-v2-dsl-ergonomics-completion P01 | 8 min | 2 tasks | 6 files |
| Phase 05.05.3-v2-dsl-ergonomics-completion P02 | 10 min | 2 tasks | 10 files |
| Phase 05.05.3-v2-dsl-ergonomics-completion P03 | 4 min | 2 tasks | 4 files |
| Phase 05.05.3-v2-dsl-ergonomics-completion P04 | 9 min | 2 tasks | 5 files |
| Phase 05.05.3-v2-dsl-ergonomics-completion P04 | 9 | 2 tasks | 5 files |
| Phase 06-complete-gap-closure P01 | 90 min | 2 tasks | 370+ files |
| Phase 06 P02 | 6 | 2 tasks | 85 files |
| Phase 06-complete-gap-closure P09 | 3 min | 2 tasks | 6 files |
| Phase 06-complete-gap-closure P05 | 11 | 2 tasks | 5 files |
| Phase 06-complete-gap-closure P08 | 18 | 2 tasks | 9 files |
| Phase 06-complete-gap-closure P04 | 35 min | 2 tasks | 10 files |
| Phase 06 P06 | 45 | 1 tasks | 8 files |
| Phase 06-complete-gap-closure P06-03 | 40 | 3 tasks | 15 files |
| Phase 06-complete-gap-closure P07 | 35 | 2 tasks | 15 files |
| Phase 06-complete-gap-closure P11 | 2 | 2 tasks | 2 files |
| Phase 06-complete-gap-closure P10 | 5 | 1 tasks | 5 files |
| Phase 06.1-v1-feature-parity-port P01 | 4 | 2 tasks | 4 files |
| Phase 06.1-v1-feature-parity-port P02 | 8 min | 2 tasks | 5 files |
| Phase 06.1-v1-feature-parity-port P03 | ~90 min (resumed) | 2 tasks | 11 files |
| Phase 06.1-v1-feature-parity-port P04 | ~25 min (resumed) | 2 tasks | 14 files |
| Phase 06.1-v1-feature-parity-port P05 | ~multi-session | 2 tasks | 15 files |
| Phase 06.1-v1-feature-parity-port P06 | ~4 hours (resumed) | 4 tasks | 23 files |
| Phase 06.1-v1-feature-parity-port P07 | 1 min | 1 tasks | 1 files |
| Phase 06.1-v1-feature-parity-port P08 | 2 | 2 tasks | 2 files |
| Phase 06.2-v1-feature-parity-ui-layer P01 | 60 | 2 tasks | 17 files |
| Phase 06.2-v1-feature-parity-ui-layer P02 | 7 min | 2 tasks | 3 files |
| Phase 06.2-v1-feature-parity-ui-layer P03 | 6 min | 2 tasks | 2 files |
| Phase 06.2-v1-feature-parity-ui-layer P04 | 15 min | 2 tasks | 3 files |
| Phase 06.2-v1-feature-parity-ui-layer P05 | 8 min | 2 tasks | 6 files |
| Phase 06.3-v1-feature-parity-world-system P01 | 5 min | 2 tasks | 6 files |
| Phase 06.3-v1-feature-parity-world-system P02 | ~20 min | 2 tasks | 3 files |
| Phase 06.3-v1-feature-parity-world-system P03 | ~60 min (resumed) | 2 tasks | 7 files |
| Phase 06.3-v1-feature-parity-world-system P04 | 13 min | 2 tasks | 6 files |
| Phase 06.3-v1-feature-parity-world-system P05 | 5 min | 2 tasks | 16 files |
| Phase 06.4-v1-feature-parity-combat-inventory P01 | 8 min | 2 tasks | 11 files |
| Phase 06.4-v1-feature-parity-combat-inventory P02 | 8 min | 2 tasks | 5 files |
| Phase 06.4 P03 | 7 | 2 tasks | 3 files |
| Phase 06.4-v1-feature-parity-combat-inventory P04 | 11 | 2 tasks | 5 files |
| Phase 06.5 P02 | 5 | 2 tasks | 7 files |
| Phase 06.5-v1-feature-parity-rpg-package P01 | 7 min | 2 tasks | 11 files |
| Phase 06.5-v1-feature-parity-rpg-package P03 | ~35 min | 2 tasks | 8 files |
| Phase 06.5-v1-feature-parity-rpg-package P10 | 15 | 2 tasks | 8 files |
| Phase 06.5-v1-feature-parity-rpg-package P09 | 20 | 2 tasks | 7 files |
| Phase 06.5-v1-feature-parity-rpg-package P06 | 40 | 1 tasks | 2 files |
| Phase 06.5-v1-feature-parity-rpg-package P05 | 45 | 2 tasks | 8 files |
| Phase 06.5-v1-feature-parity-rpg-package P04 | 30 | 2 tasks | 8 files |
| Phase 06.5-v1-feature-parity-rpg-package P07 | 9 min | 2 tasks | 14 files |
| Phase 06.5-v1-feature-parity-rpg-package P08 | 45 min | 2 tasks | 9 files |
| Phase 06.5-v1-feature-parity-rpg-package P11 | 4 min | 2 tasks | 2 files |
| Phase 06.5-v1-feature-parity-rpg-package P12 | 6 min | 2 tasks | 2 files |
| Phase 06.6-deferred-gaps-dsl-gbc-audio P01 | 45 | 2 tasks | 9 files |
| Phase 06.6-deferred-gaps-dsl-gbc-audio P02 | 45 | 2 tasks | 13 files |
| Phase 06.6 P03 | 45 | 2 tasks | 13 files |
| Phase 06.6-deferred-gaps-dsl-gbc-audio P04 | 46 min | 2 tasks | 13 files |
| Phase 06.6-deferred-gaps-dsl-gbc-audio P05 | 2 | 1 tasks | 61 files |
| Phase 06.7-deferred-gaps-entity-movement-world P04 | 9 | 2 tasks | 4 files |
| Phase 06.7 P09 | 11 | 2 tasks | 5 files |
| Phase 06.7-deferred-gaps-entity-movement-world P05 | 15 | 2 tasks | 4 files |
| Phase 06.7-deferred-gaps-entity-movement-world P03 | ~90 min (resumed) | 2 tasks | 9 files |
| Phase 06.7-deferred-gaps-entity-movement-world P01 | 90 | 2 tasks | 11 files |
| Phase 06.7-deferred-gaps-entity-movement-world P07 | ~90 min (resumed) | 2 tasks | 10 files |
| Phase 06.7-deferred-gaps-entity-movement-world P06 | 10 | 2 tasks | 4 files |
| Phase 06.7-deferred-gaps-entity-movement-world P08 | 90 min (resumed) | 2 tasks | 7 files |
| Phase 06.7-deferred-gaps-entity-movement-world P02 | 4 | 2 tasks | 10 files |
| Phase 06.7-deferred-gaps-entity-movement-world P10 | 9 | 2 tasks | 5 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P01 | 8 min | 2 tasks | 8 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P02 | 4 min | 2 tasks | 2 files |
| Phase 06.8 P02 | 4 | 2 tasks | 2 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P06 | 5 | 3 tasks | 6 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P08 | 18 min | 3 tasks | 12 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P07 | 11 | 2 tasks | 9 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P04 | 14 | 2 tasks | 8 files |
| Phase 06.8 P05 | 20 | 2 tasks | 8 files |
| Phase 06.8 P03 | 74 | 3 tasks | 11 files |
| Phase 06.8 P09 | 7 | 2 tasks | 6 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P11 | 4 | 2 tasks | 4 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P12 | 4 min | 2 tasks | 4 files |
| Phase 06.8 P10 | 7 | 2 tasks | 4 files |
| Phase 06.8-deferred-gaps-genre-packages-rpg-extensions P13 | 3 min | 1 tasks | 34 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P02 | 2 min | 2 tasks | 2 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P03 | 3 min | 2 tasks | 9 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P04 | 8 min | 2 tasks | 4 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P06 | 12 min | 2 tasks | 7 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P05 | 5 min | 3 tasks | 2 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P07 | 12 min | 2 tasks | 3 files |
| Phase 06.9-deferred-gaps-infrastructure-tech-debt P08 | 4 min | 2 tasks | 17 files |
| Phase 06.10 P02 | 10 | 2 tasks | 5 files |
| Phase 06.10 P03 | 1 | 2 tasks | 2 files |
| Phase 06.10 P05 | 4 | 1 tasks | 1 files |
| Phase 06.10 P04 | 82 | 1 tasks | 1 files |
| Phase 06.10 P07 | 3 | 1 tasks | 1 files |
| Phase 06.10 P08 | 3 | 1 tasks | 1 files |
| Phase 06.10 P06 | 5 | 1 tasks | 1 files |
| Phase 06.11-labyrinth-of-the-dragon-port P01 | 2 | 2 tasks | 19 files |
| Phase 06.11-labyrinth-of-the-dragon-port P02 | 45 | 1 tasks | 1 files |
| Phase 06.11 P04 | 11 | 2 tasks | 6 files |
| Phase 06.11-labyrinth-of-the-dragon-port P03 | 62 | 1 tasks | 10 files |
| Phase 06.11-labyrinth-of-the-dragon-port P03b | 90 | 1 tasks | 7 files |
| Phase 06.11-labyrinth-of-the-dragon-port P07 | 3 | 1 tasks | 1 files |
| Phase 06.11-labyrinth-of-the-dragon-port P19 | 18 | 2 tasks | 4 files |
| Phase 06.11 P05 | 9 | 2 tasks | 8 files |
| Phase 06.11 P06 | 8 | 2 tasks | 3 files |
| Phase 06.11-labyrinth-of-the-dragon-port P09 | 3 | 1 tasks | 1 files |
| Phase 06.11-labyrinth-of-the-dragon-port P08 | 12 | 2 tasks | 5 files |
| Phase 06.11-labyrinth-of-the-dragon-port P10 | 8 min | 2 tasks | 4 files |
| Phase 06.11-labyrinth-of-the-dragon-port P12 | 8 min | 1 tasks | 1 files |
| Phase 06.11-labyrinth-of-the-dragon-port P12b | 45 min | 1 tasks | 16 files |
| Phase 06.11-labyrinth-of-the-dragon-port P11 | 14 min | 2 tasks | 5 files |
| Phase 06.11 P14 | multi-session | 2 tasks | 4 files |
| Phase 06.11 P16 | 12 | 1 tasks | 2 files |
| Phase 06.11 P15 | 45 min | 2 tasks | 5 files |
| Phase 06.11-labyrinth-of-the-dragon-port P17 | 3 | 1 tasks | 1 files |
| Phase 06.11 P18 | 120 | 2 tasks | 31 files |
| Phase 06.12-embedded-emulator-core-and-debug-loop P01 | 2 | 2 tasks | 5 files |
| Phase 06.12 P02 | 6 | 2 tasks | 2 files |
| Phase 06.12 P04 | 3 | 2 tasks | 6 files |
| Phase 06.12 P05 | 2 | 2 tasks | 3 files |
| Phase 06.12-embedded-emulator-core-and-debug-loop P03 | 4 | 2 tasks | 3 files |
| Phase 06.12-embedded-emulator-core-and-debug-loop P07 | 2 | 2 tasks | 2 files |
| Phase 06.12-embedded-emulator-core-and-debug-loop P06 | 3 | 2 tasks | 3 files |
| Phase 06.12 P08 | 3 | 2 tasks | 4 files |
| Phase 06.12 P09 | 2 | 2 tasks | 2 files |
| Phase 06.12 P10 | 4 | 2 tasks | 7 files |
| Phase 06.12 P11 | 8 | 2 tasks | 5 files |
| Phase 07-uat-gameplay-validation P02 | 6 min | 2 tasks | 8 files |
| Phase 07-uat-gameplay-validation P01 | 7 | 2 tasks | 6 files |
| Phase 07-uat-gameplay-validation P03 | 4 min | 2 tasks | 5 files |
| Phase 07-uat-gameplay-validation P04 | 4 | 2 tasks | 7 files |
| Phase 07-uat-gameplay-validation P08 | 2 | 1 tasks | 3 files |
| Phase 07-uat-gameplay-validation P06 | 8 | 1 tasks | 9 files |
| Phase 07-uat-gameplay-validation P07 | 15min | 1 tasks | 6 files |
| Phase 07.1-test-dx-and-agent-tooling P01 | 5 min | 2 tasks | 3 files |
| Phase 07.1-test-dx-and-agent-tooling P02 | 8 | 2 tasks | 7 files |
| Phase 07.1 P04 | 4 min | 2 tasks | 5 files |
| Phase 07.1-test-dx-and-agent-tooling P05 | 4 | 2 tasks | 11 files |
| Phase 07.1 P03 | 9 min | 2 tasks | 21 files |
| Phase 07.1-test-dx-and-agent-tooling P06 | 4 | 2 tasks | 3 files |
| Phase 07.1.1-agent-testing-critical-gaps P02 | 2 min | 2 tasks | 3 files |
| Phase 07.1.1-agent-testing-critical-gaps P01 | 4 | 2 tasks | 7 files |
| Phase 07.1.1-agent-testing-critical-gaps P03 | 4 min | 2 tasks | 5 files |
| Phase 07.1.1-agent-testing-critical-gaps P04 | 3 min | 2 tasks | 2 files |
| Phase 07.1.2 P01 | 15 | 2 tasks | 4 files |
| Phase 07.1.2 P02 | 9 | 4 tasks | 4 files |
| Phase 07.1.2 P03 | 4 | 2 tasks | 0 files |
| Phase 07.3-entity-pool-codegen-fix-inserted P01 | 25 | 2 tasks | 7 files |
| Phase 07.3-entity-pool-codegen-fix-inserted P02 | 7 | 2 tasks | 4 files |
| Phase 07.3-entity-pool-codegen-fix-inserted P03 | 524064min | 1 tasks | 2 files |
| Phase 07.4 P18 | 90 | 3 tasks | 4 files |
| Phase 10.2 P04 | 60 | 4 tasks | 7 files |
| Phase 10.2 P05 | 90min | 4 tasks | 8 files |
| Phase 10.2 P06a | 30min | 2 tasks | 6 files |
| Phase 10.2 P06c | 22min | 2 tasks | 8 files |
| Phase 10.2 P06d | 15min | 2 tasks | 7 files |
| Phase 10.2 P08 | 15m | 1 tasks | 2 files |
| Phase 10.2 P09 | 20min | 3 tasks | 7 files |
| Phase 11.3 P06 | 6 | 2 tasks | 1 files |
| Phase 11.3 P11.3-07 | 4 | 2 tasks | 1 files |
| Phase 11.3 P08 | 1 | 2 tasks | 1 files |
| Phase 11.3 P09 | 10 min | 2 tasks | 1 files |
| Phase 12.2 P11 | 15 | 2 tasks | 4 files |
| Phase 12.9 P02 | 8 | 1 tasks | 2 files |
| Phase 12.9 P06 | 4 | 3 tasks | 2 files |
| Phase 12.9 P04 | 15m | 3 tasks | 1 files |
| Phase 12.9 P08f | 15 | 6 tasks | 12 files |
| Phase 12.10 P01 | 12 | 2 tasks | 2 files |
| Phase 12.10 P03 | 18 | 2 tasks | 1 files |
| Phase 12.10 P04 | 6min | 2 tasks | 1 files |
| Phase 12.11 P02 | 2 | 1 tasks | 1 files |
| Phase 12.11 P04 | 9 min | 2 tasks | 3 files |
| Phase 13.1 P01 | 5 | 3 tasks | 3 files |
| Phase 13.1 P02 | 5 | 3 tasks | 3 files |
| Phase 13.1 P03 | 3 | 2 tasks | 5 files |
| Phase 13.1 P05 | 3 | 1 tasks | 0 files |
| Phase 13.1 P07 | 25 | 3 tasks | 49 files |
| Phase 13.1 P06 | 8 | 1 tasks | 2 files |
| Phase 13.1 P08 | 6 | 3 tasks | 10 files |
| Phase 13.1 P09 | 8 | 1 tasks | 1 files |
| Phase 13.1 P10 | 15 | 2 tasks | 2 files |
| Phase 13.2 P01 | 3 | 2 tasks | 6 files |
| Phase 13.2 P02 | 4 | 2 tasks | 6 files |
| Phase 13.2 P03 | 1 | 1 tasks | 1 files |
| Phase 13.2 P04 | 8 | 2 tasks | 2 files |
| Phase 13.2-framework-primitives-delegate-ergonomics-variable-control-fl P05 | 8 | 1 tasks | 1 files |
| Phase 13.2 P06 | 6 | 3 tasks | 4 files |
| Phase 13.2 P07 | 8 | 2 tasks | 2 files |
| Phase 13.3 P18 | 100 | 2 tasks | 1 files |
| Phase 13.4 P01 | 3 | 2 tasks | 2 files |
| Phase 13.4 P02 | 22 | 2 tasks | 9 files |
| Phase 13.4 P03 | 11 | 2 tasks | 2 files |
| Phase 13.4 P04 | 12 | 2 tasks | 9 files |
| Phase 13.4 P05 | 9 | 2 tasks | 15 files |
| Phase 13.4 P07 | 15 | 2 tasks | 7 files |
| Phase 13.4 P08 | 6 | 2 tasks | 2 files |
| Phase 13.4 P09 | 25 min | 2 tasks | 98 files |
| Phase 13.6 P02 | 96 | 1 tasks | 1 files |
| Phase 13.6 P04 | 22min | 2 tasks | 4 files |
| Phase 13.6 P07 | 25 minutes | 3 tasks | 8 files |
| Phase 13.7 P02 | 10min | 1 tasks | 2 files |
| Phase 13.7 P03 | 20m | 1 tasks | 3 files |
| Phase 13.7 P04 | 5m | 1 tasks | 1 files |
| Phase 13.8 P01 | 503 | 2 tasks | 5 files |
| Phase 13.8 P02 | 8 | 2 tasks | 5 files |
| Phase 13.8 P05 | 4m | 2 tasks | 5 files |
| Phase 13.8 P03 | 15 | 2 tasks | 3 files |
| Phase 13.8 P07 | 15 | 2 tasks | 3 files |
| Phase 14 P03 | 5min | 2 tasks | 7 files |
| Phase 14 P04 | 26 | 3 tasks | 2 files |
| Phase 14 P05 | 90 | 3 tasks | 135 files |
| Phase 14 P06 | 45m | 3 tasks | 20 files |
| Phase 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff P07 | 6 | 2 tasks | 9 files |
| Phase 15 P01 | 16 min | 2 tasks | 2 files |
| Phase 15 P02 | 12 min | 2 tasks | 3 files |
| Phase 15 P04 | 8 min | 2 tasks | 2 files |
| Phase 15 P03 | 18 min | 2 tasks | 4 files |
| Phase 15 P05 | 34 min | 3 tasks | 6 files |
| Phase 15 P06 | 14 min | 2 tasks | 2 files |
| Phase 16-seed-triage P02 | 7min | 2 tasks | 4 files |
| Phase 16-seed-triage P05 | 6 | 2 tasks | 7 files |
| Phase 16-seed-triage P08 | 2min | 1 tasks | 2 files |
| Phase 16-seed-triage P09 | 5 | 2 tasks | 45 files |
| Phase 17 P01 | 7 | 2 tasks | 1 files |
| Phase 17 P02 | 1 | 3 tasks | 2 files |
| Phase 17 P03 | 4 | 2 tasks | 2 files |
| Phase 17 P04 | 7 | 2 tasks | 13 files |
| Phase 17 P05 | 6 | 3 tasks | 5 files |
| Phase 17 P08 | 4 | 2 tasks | 1 files |
| Phase 17 P07 | 10 | 2 tasks | 5 files |
| Phase 17 P09 | 5min | 2 tasks | 1 files |
| Phase 17 P10 | 70s | 2 tasks | 1 files |
| Phase 17 P11 | 45 | 3 tasks | 5 files |
| Phase 17 P12 | 3 | 2 tasks | 13 files |
| Phase 18 P02 | 4 | 1 tasks | 1 files |
| Phase 18 P03 | 2 | 1 tasks | 2 files |
| Phase 18 P04 | 2 | 2 tasks | 2 files |
| Phase 18 P05 | 2 min | 1 tasks | 2 files |
| Phase 18 P06 | 3 min | 3 tasks | 3 files |
| Phase 18 P08 | 3 min | 3 tasks | 3 files |
| Phase 18 P09 | 5min | 3 tasks | 2 files |
| Phase 18 P10 | 10 | 3 tasks | 3 files |
| Phase 18 P12 | 5 | 1 tasks | 4 files |
| Phase 18 P13 | 8 min | 2 tasks | 1 files |
| Phase 18 P14 | 7 | 2 tasks | 1 files |
| Phase 18 P17 | 7 min | 1 tasks | 1 files |
| Phase 18-deprecation-removals-and-sonar-burn-down P19 | 12 | 2 tasks | 1 files |
| Phase 18 P21 | 8 min | 1 tasks | 1 files |
| Phase 18 P24 | 90 | 2 tasks | 2 files |
| Phase 19 P01 | 2 min | 2 tasks | 1 files |
| Phase 19 P03 | 2 | 2 tasks | 6 files |
| Phase 19 P04 | 2 | 2 tasks | 1 files |
| Phase 20 P01 | 3min | 2 tasks | 4 files |
| Phase 20 P03 | 4min | 2 tasks | 6 files |
| Phase 20 P02 | 2 | 1 tasks | 1 files |
| Phase 20 P04 | 2 | 2 tasks | 4 files |
| Phase 21 P01 | 3 | 2 tasks | 4 files |
| Phase 21 P03 | 5 | - tasks | - files |
| Phase 21 P04 | 2 | 1 tasks | 2 files |
| Phase 21 P07 | 59 min | 3 tasks | 2 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Plan 20-03: FIX-04 oracle test classes use separate Phase-20-specific test files with dedicated EVIDENCE_DIR (not new methods in existing tests) — mirrors Phase 12.8/19 clone-and-retarget precedent; avoids invasive EVIDENCE_DIR changes to production test classes
- Plan 20-03: Both FIX-04 captures use gbcMode=true — metasprites and platformer-template both target GBC_COMPATIBLE; DMG mode produces false grayscale/green-tinted artifacts (D-05)
- Plan 18-20: E-11/E-14 S3776 cleared via value-returning extract-method (buildZoneOnExitSwitch, buildEdgeAutoPositionSwitch, buildEncounterRollStatements, buildEncounterEntryGuard); GBDKSystemVisitor.kt S3776 = 0; byte-identity ROM sweep green
- Plan 10.2-07: Fix shape Option B (Order-Tweaked) chosen for DEF-10.1-13-C 5th layer — swap addAll(hoistedBgFillCheckerboardStatements) before addAll(allSpriteDataLoads) in GBDKPipelineV2.kt mainBody buildList; mechanism = LCDC.4=1 shared $8000-$97FF VRAM, last-write wins, Plan 22 emit order corrupted sprite tile 0; all 5 locked tests preserved; RED test at commit 20691a7d
- UAT_GUIDE.md created with 3 real Phase 07 debugging walkthroughs; emulator_press documented with frames+1 semantics; variable type range table added; UAT-02 requirement satisfied
- scrollAware defaults to false on all VramTextVerifier methods to preserve backward compatibility; WINDOW layer is always unaffected by scroll registers (GB hardware behavior)
- StepAgent.buildObservation() passes metadata-driven decoders to readAllRows(); null decoders fall back to per-layer defaults automatically inside VramTextVerifier
- Sealed IR types must be finalized in Phase 1 — Kotlin module constraint prevents moving them later; getting the hierarchy wrong is the highest-cost mistake
- All three example games (Pong, Breakout, Explorer) must be defined as DSL in Phase 1 — prevents one-game (LabyrinthOfTheDragon) coupling from re-infiltrating core codegen
- Old GBDKCodeGenerator must be deleted (not deprecated) by end of Phase 2 — migration seam must close
- Bank assignment is a typed field on C AST nodes, not mutable state — eliminates the documented root cause of bank state leak bugs
- IR v2 types placed in io.github.gbkt.core.ir.v2 package (v2 to avoid collision with existing ir/ types during rebuild) — 2026-02-17
- PlatformAnnotatable is a plain interface (not sealed) so data classes can implement alongside sealed hierarchies — 2026-02-17
- StringLiteral added as 9th Expr subtype beyond the 8 specified (required for string-typed dialog/print values) — 2026-02-17
- 13 status effects implemented (not 17 as planned): BUFF_UNUSED_0/1/2 in Original C have no game behavior — skipped — 2026-02-27
- Items.kt: items {} returns Unit; ItemRef vars declared as lateinit outside block, assigned inside, returned after — 2026-02-27
- GameBuilderContext uses ThreadLocal for variable delegate registration — Kotlin local variable delegates receive Any? as thisRef; thread-local is the correct solution (same pattern as v1 RecordingContext) — 2026-02-17
- GenreVisitorResult uses List<Any> for functions/varDecls to prevent circular dependency: gbkt-backend-api cannot depend on gbkt-backend-gbdk where CFunction/CVarDecl live — 2026-02-25
- Module rename gbkt-rpg → gbkt-genre-rpg is Gradle-level only; Kotlin package paths (io.github.gbkt.rpg.*) unchanged to avoid breaking imports across the codebase — 2026-02-25
- ServiceLoader.load(GenreSystemVisitor::class.java) is hoisted above the loop in buildSystemFunctions/buildSystemGlobalVars for performance; genre visitors take priority over built-in GenericSystem dispatch — 2026-02-25
- SportVisitor lives in gbkt-genre-sport (not gbkt-backend-gbdk) — genre codegen belongs in the genre module for self-contained packages — 2026-02-25
- Pickup delegation via synthetic GenericSystem: convert SportPickupDef → PickupDef, create GenericSystem(type="pickup_system"), call system.accept(GBDKSystemVisitor) — avoids accessing private buildPickupFunctions while delegating all pickup logic — 2026-02-25
- Phase 06.8 integration: no Detekt baselines required for new genre modules — existing detekt.yml **/codegen/** exclusions cover all new genre visitor classes (PlatformerVisitor, PuzzleVisitor, SportVisitor) — 2026-02-25
- spotlessApply auto-fixes import ordering and KDoc line-length in genre modules — run before final build verification in multi-module phases — 2026-02-25
- asset() is a top-level function (not GameBuilder member) — @GbktDsl scope markers prevent member function calls from nested builder scopes; top-level functions bypass this restriction — 2026-02-17
- VarDelegate implements ReadWriteProperty<Any?> with no-op setValue — allows var score by u8Var(0) DSL syntax; script mutations use ScriptBuilder.assign() — 2026-02-17
- CBreak/CContinue added as data objects to CStatement sealed hierarchy — eliminates all CRawCode("break;") and CRawCode("continue;") patterns; zero-cost type-safe alternative — 2026-02-21
- GBDK hardware macros (ENABLE_RAM, DISABLE_RAM, SCX_REG, SCY_REG, SHOW_WIN, HIDE_WIN, DISPLAY_ON, SHOW_SPRITES) kept as CRawCode — these are C preprocessor macros that expand to volatile register lvalue writes not representable in typed C AST — 2026-02-21
- SimpleBattle switch body kept as deliberate CRawCode exception — multi-case state machine with inline literals; to be revisited in phase 06.2+ — 2026-02-21
- Scenes.register() accepts combatSystem: LabyrinthCombatSystem and state: GameState to wire BattleScene with typed combat state and SceneRef navigation — 2026-02-27
- GameplayScene delegates exploration mechanics to V2 exploration DSL; scene only handles A/START/SELECT input dispatch — 2026-02-27
- Floors.kt WorldFlags constructor fixed by adding floor2/3/4 elite defeated flag refs to the world flag page — 2026-02-27
- WorldFlags has 11 FlagRefs (not 8): floors 2-4 have both bossDefeated and eliteDefeated tracked separately — elite kill grants ABILITY_N, boss kill opens next-floor door — 2026-02-27
- showMessage()/grantItem() DSL methods do not exist in ScriptBuilder; correct pattern is callOp("map_textbox", StringLiteral("str_key")) and callOp("add_item", StringLiteral("item_id")) — 2026-02-27
- GameBuilder.registerSystem() added as generic public API — genre packages need to register GenericSystem instances; single registration point enables indefinite genre package expansion without per-genre core methods — 2026-02-17
- Simulation tests scoped to scene entry + variable initialization + frame smoke tests (not full navigation flows) — navigation tests would couple to exact frame sequences and input timing, creating brittle tests — 2026-02-27
- ScriptOpInterpreter SetPalette stub lives in gbkt-core (not the port) — the interpreter must handle all ScriptOp subtypes used by any game; hardware-dependent ops are no-op stubs — 2026-02-27
- UINT8 j underflow guard in insertion sort: use j < i (not j >= 0) — UINT8 wraps to 255 on j-- past 0, always > i for i <= 8; correct termination without signed arithmetic — 2026-02-24
- OptimizationReport toggle fields default true (always-on); users opt out with false — avoids breaking changes; all 3 IR passes and 2 C-output passes togglable via AnalysisConfig — 2026-02-26
- BudgetAuditPass chosen as optimization-report.json writer — runs last in pipeline after all summaries are accumulated; outputDirectory nullable so no crash in tests without temp dir — 2026-02-26
- GameIRSerializer uses type discriminator pattern ('type' field in JSONObject) for ScriptOp/Expr polymorphism; org.json library (already in version catalog); serializeOp/deserializeExpr marked internal for direct test access — 2026-02-26
- COutputOptimizer accepts plain boolean toggles (not AnalysisConfig) — compile-time decoupling; Plan 08 integration reads toggles from AnalysisConfig and passes booleans in — 2026-02-26
- COutputOptimizer wired in GBDKBackend.generateV2() (not GBDKPipelineV2) — backend has AnalysisConfig access; pipeline has none — 2026-02-26
- generateV2() optional params assetManifest and outputDirectory default null — backward compat with Gradle plugin reflection callers — 2026-02-26
- C-text dedup is per-file (not cross-file): each file in Map<String, String> processed independently — cross-bank #define aliases invalid in GBDK banking model — 2026-02-26
- EmulatorTestTask graceful skip test uses ProjectBuilder (unit test) not TestKit — Kotlin DSL build scripts cannot resolve plugin classpath types at compile time via withPluginClasspath(); ProjectBuilder directly instantiates the task and invokes run() — 2026-02-26
- SameBoy tester preferred over mGBA for headless ROM testing — exits 0 after N frames without crash, no Lua scripting required, fast and deterministic — 2026-02-26
- Timeout = PASS in emulatorTest — if ROM runs for full wall-clock budget without crashing, treat as passed (no crash is the success criterion for GB ROM testing) — 2026-02-26
- CFor init is CStatement? (accepts CVarDecl), increment is CExpr? (use CUnaryExpr not CRawExpr) — established pattern for typed loop variable management — 2026-02-24
- Genre package pattern: gbkt-rpg depends on gbkt-core (not reverse); GameBuilder extended via extension functions from genre package; GenericSystem config map carries all genre-specific data for backend routing — 2026-02-17
- RpgRegistry uses ThreadLocal for character/monster def registration during game {} block — mirrors GameBuilderContext pattern; defs needed by simpleBattle builder that runs later in same lambda — 2026-02-17
- Wildcard import required for dsl.v2 package in game definition files — operator extension functions (plus, minus, isAbove, etc.) on Expr require explicit import or wildcard; named imports miss operator functions — 2026-02-17
- literal() wrapping required for setPosition() when mixing Int and Expr args — Kotlin overload resolution fails on mixed Int/Expr; use literal() to make all args uniformly Expr — 2026-02-17
- gbkt-rpg NOT in gbkt-bom constraints — BOM coordinates only core platform modules; genre packages are opt-in per game module — 2026-02-17
- CBlankLine implemented as data object (singleton) — avoids unnecessary allocation per blank line in emitted output — 2026-02-18
- InputRef 4-param constructor (gbdkConstant, heldFn, pressedFn, releasedFn) — enables held/pressed/released triad from single type; releasedFn = "button_released" or "dpad_released" — 2026-02-24
- ArrayVar.get(Int) uses require() for compile-time bounds checking — throws IllegalArgumentException at DSL record time, not C runtime; dynamic AssignableVar/Expr indices bypass check intentionally — 2026-02-24
- CollElementType uses byteSize (not sizeBytes) to match VarType.byteSize convention — existing analysis code used byteSize; consistency reduces special-casing — 2026-02-24
- CollElementType.cTypeName is the public backend-neutral property (not gbdkCName) — allows non-GBDK backends to extend or override; GBDK-specific type mapping lives in CollElementType.Primitive — 2026-02-24
- Reified generic delegates use T::class.simpleName for StructDef registry lookup — convention-based (no marker interface required); struct("TileHashEntry") + hashtable<TileHashEntry> works by name match — 2026-02-24
- buildStructTypedefs uses CollElementType.Primitive(field.type).cTypeName to cross package boundary — VarType.cName is internal to codegen package; CollElementType.Primitive.cTypeName is public — 2026-02-24
- ScriptBuilderContext is internal to gbkt-lang — gbkt-core tests use game{}.build() pattern to establish context rather than accessing internal types directly — 2026-02-24
- Collection simulation J10: evaluateCallExpr refactored from when(fn) to when-with-guards for startsWith/endsWith pattern matching; executeCallOp constructs synthetic CallExpr to share dispatch logic — 2026-02-25
- Pool collections backed by hashTables map with __pool_ prefix to avoid namespace collision with user-defined hash tables — 2026-02-25
- CSwitchCase is a regular data class (not sealed CStatement subtype) — structural component of CSwitch, not a standalone statement — 2026-02-18
- CFunction.bank:Int? uses null-means-inherit semantics — emitter inherits file bank when null, enabling per-function bank overrides without requiring all callers to look up parent CFile bank — 2026-02-18
- CEmitter is the ONLY file calling buildString/appendLine for C text output — architectural rule enforced by convention, verified by grep — 2026-02-18
- emitType(CArray) returns element type only; array subscript [N] emitted at declaration site in emitVarDecl — 2026-02-18
- Post-increment in for loops: CUnaryExpr("++", i) renders as "i++" via emitExprForIncrement (trailing operator convention) — 2026-02-18
- ExprVisitor.sanitizeVarName() is internal (not private) — ScriptOpVisitor needs it for actor ID sanitization in SetPosition/MoveBy to avoid duplicating sanitization logic — 2026-02-18
- MoveBy skips zero Literal offsets at visitor level — avoids emitting no-op += 0 assignments in generated C — 2026-02-18
- SceneVisitor puts sectionComment only on enter function — frame/exit don't repeat the block separator, matching GBDK output style — 2026-02-18
- ScriptOpVisitor uses else → CRawCode TODO for unimplemented ScriptOps — prevents scope creep while keeping Pong compilable — 2026-02-18
- ActorVisitor uses CU8 for position types — matches GBDK native coordinate range 0-255, consistent with Game Boy hardware — 2026-02-18
- [Phase 02]: GBDKPipelineV2 produces 3 output files: main.c (HOME bank), bank1.c (bank 1 with BANKED scene functions), game.h (header with externs and forward declarations)
- [Phase 02]: generateV2(GameIR) added as new GBDKBackend method; existing generate(Game) untouched — zero regression risk for v1 games
- [Phase 02]: PongPipelineTest uses inline GameIR fixture — avoids cross-module dependency; 17 integration tests prove end-to-end correctness
- [Phase 03-asset-pipeline-and-jvm-test-runner]: ByteArrayKey wraps ByteArray with contentEquals/contentHashCode for tile deduplication map keys — mirrors AssetPipeline.Tile.equals() pattern
- [Phase 03-asset-pipeline-and-jvm-test-runner]: LdtkParser pinned to 1.5.x via startsWith() check; clear error message includes unsupported version string for diagnosability
- [Phase 03-asset-pipeline-and-jvm-test-runner]: AssetManifestEntry sealed class (not interface) — shared toJson() delegation via writeFields() abstract method pattern
- [Phase 03]: kotlin.test.* used (not JUnit 5) — project standard; JUnit 6.0.1 BOM in build.gradle.kts was invalid pre-written dependency, removed to restore compilation
- [Phase 03]: ScriptOpInterpreter uses exhaustive when with NO else branch on sealed types — compiler enforces ScriptOp/Expr coverage; 24 ScriptOp subtypes and 9 Expr subtypes fully handled
- [Phase 03]: __joypad/__joypad_prev variables synced in ScriptOpInterpreter.executeFrame() — allows v2 scripts to read input via VarRef without special input expression type
- [Phase 03-asset-pipeline-and-jvm-test-runner]: buttonPressed()/dpadHeld() DSL emits CallExpr (hardware stub) that evaluates to 0 in ScriptOpInterpreter — button-driven navigation cannot be tested directly; use variable comparisons or enterScene() for test scenarios
- [Phase 03-asset-pipeline-and-jvm-test-runner]: processPng uses DEFAULT_PALETTE for SpriteEntry.palette — extractPalette returns GBCPalette (RGB555), wrong type for SpriteEntry.palette which stores GB luminance thresholds (int list)
- [Phase 03.1-collection-abstractions]: IRColl prefix used for collection IR types to avoid collision with existing entity Pool IR (IRPoolSpawn, IRPoolUpdate, etc.)
- [Phase 03.1-collection-abstractions]: collPools field in Game (not pools) to avoid shadowing existing pools: List<Pool> entity field
- [Phase 03.1-collection-abstractions]: GameScope uses open methods with no-op defaults for collection registration; GameBuilder overrides
- [Phase 03.1-collection-abstractions]: Expr.ir property used to unwrap IRExpression in domain wrapper operation methods (not .unwrap())
- [Phase 03.1-collection-abstractions]: Unnamed Kotlin local variables (val _ = ...) are experimental in 2.3.0 — use named variables in tests
- [Phase 03.1-collection-abstractions]: Collection domain delegate pattern: private var cache, GameScopeContext.current?.register*() on first access only (idempotent)
- [Phase 03.1-collection-abstractions]: ExpressionCodegen uses else -> generateCollectionExpr() delegation — avoids 7 new import/branch pairs in large when block, consistent with mixer expression pattern
- [Phase 03.1-collection-abstractions]: generateCollectionData() placed after generateVariables() and generateCollectionFunctions() after generatePoolFunctions() in GBDKCodeGenerator.generate() — arrays declared before helpers, helpers available before scene code
- [Phase 04-analysis-pass-pipeline]: gbkt-analysis has no dependency on gbkt-backend-gbdk — analysis is platform-agnostic; backends consume analysis output, not the reverse
- [Phase 04-analysis-pass-pipeline]: PassPipeline.execute() concatenates all three pass lists before looping — single loop simplifies fail-fast logic at cost of one extra list allocation per pipeline run
- [Phase 04-analysis-pass-pipeline]: AnalysisConfig.fromCartridgeConfig uses minOf(typeMax, romBanks) — respects game author's declared bank count; type maximum is a ceiling not a floor
- [Phase 04-analysis-pass-pipeline]: SemanticValidationPass.run() refactored into 5 private helpers — detekt LongMethod threshold is 80 lines; analysis pass validation methods are inherently multi-check
- [Phase 04-analysis-pass-pipeline]: ResourceInventory.collectionBytes always 0 in v2 pipeline — v2 GameIR has no collection IR fields; v1 Game model carries collPools/collHashTables; documented as TODO in ResourceInventoryPass
- [Phase 04-analysis-pass-pipeline]: ConstraintCheckPass uses requireNotNull(context.inventory) — explicit prerequisite enforcement; hard fail at runtime if ResourceInventoryPass did not run before
- [Phase 04-analysis-pass-pipeline]: VRAMLayoutPass reads spriteTileCounts from ResourceInventory (authoritative source); estimateBgTiles returns 0 for v2 IR (no per-scene tileset refs); FONT assets imply 36 fixed global tile slots; overflow error includes scene name + breakdown + splitting suggestion per locked decision
- [Phase 04-analysis-pass-pipeline]: BankingAnalysisPass uses effectiveCapacity = (16384 * bankFillErrorThreshold).roundToInt() — capacity ceiling respects the error threshold to avoid issuing overflow on borderline cases
- [Phase 04-analysis-pass-pipeline]: collectNavigations() recursively walks IfOp/WhileOp/ForOp/FadeOp/ShowMenu — NavigateTo inside nested control flow is still a real transition; flat walk would miss locality edges
- [Phase 04-analysis-pass-pipeline]: OAMAllocationPass scanline density is advisory WARNING not ERROR — hardware flickering requires runtime clustering, not just static actor count; 12 actors spread across different Y positions never flicker
- [Phase 04-analysis-pass-pipeline]: RAMPlanningPass falls back to direct game.variables computation when inventory is null — makes pass safe to run standalone without ResourceInventoryPass prerequisite
- [Phase 04-analysis-pass-pipeline]: Actor state overhead constant is 5 bytes/actor (x:1, y:1, visible:1, type:1, reserved:1); engine overhead constant is 10 bytes (scene management 4 + camera state 6)
- [Phase 04]: DeadCodeEliminationPass uses NavigateTo (not EnterScene) — the actual v2 IR scene transition op is NavigateTo; analysis is INFO-only, no GameIR mutation
- [Phase 04]: ConstantFoldingPass division by zero returns null from evalBinaryOp — original BinaryExpr preserved to avoid compile-time error introduction
- [Phase 04]: ConstantFoldingPass foldOp() uses else -> op for ScriptOps with no expression fields (NavigateTo, PlaySound, ShowDialog, etc.) — prevents scope creep
- [Phase 04-analysis-pass-pipeline]: BudgetAuditPass hard-fails on ERROR diagnostics but passes with only WARNINGs — matches Rust cargo build semantics
- [Phase 04-analysis-pass-pipeline]: DefaultPipeline.create() chains all 10 passes with before/after extension hooks; BudgetAuditPass always last
- [Phase 04-analysis-pass-pipeline]: applyAnnotations() creates annotated GameIR copy via data class copy() — no mutation; null-coalescing favors context over existing annotation
- [Phase 04-analysis-pass-pipeline]: buildSceneFile() uses firstOrNull bankSlot to pick file bank — scenes in one bank per file is the current model; future multi-bank support would require splitting into per-bank files
- [Phase 04-analysis-pass-pipeline]: BudgetReportTask uses reflection via classloader isolation — analysis classes live in user runtime classpath, not plugin compile classpath; mirrors GenerateCTask pattern exactly
- [Phase 04-analysis-pass-pipeline]: navigate_to_scene dispatches through trampolines for banked scenes — HOME-resident code must never call BANKED functions directly; trampoline indirection ensures GBDK bank switching
- [Phase 04-analysis-pass-pipeline]: BG_TILES_DEFAULT_ESTIMATE = 256 — conservative heuristic for any non-null tilesetRef; actual tile count deferred to Phase 5 asset pipeline file I/O
- [Phase 04-analysis-pass-pipeline]: estimateBgTiles retains game parameter with @Suppress UnusedParameter for Phase 5 refinement (tileset metadata lookup by path)
- [Phase 04-analysis-pass-pipeline]: tilesetRef placed before bankSlot in SceneIR field order — asset-level fields before platform annotation fields; null default keeps all existing call sites unchanged
- [Phase 05-integration-and-end-to-end-validation]: v2 bridge uses early return in GenerateCWorkAction.execute() — v1 path completely untouched, zero regression risk
- [Phase 05-integration-and-end-to-end-validation]: Source maps deferred for v2 games — GBDKCodeGenerator incompatible with GameIR; WARNING printed explicitly
- [Phase 05-integration-and-end-to-end-validation]: resolveGameIR() tries build() before getIr() — GameBuilder is dominant v2 entry point; getIr() is legacy fallback
- [Phase 05-integration-and-end-to-end-validation]: cls() replaces CLS — GBDK-2020 removed the CLS macro; cls() is the function in gbdk/console.h
- [Phase 05-integration-and-end-to-end-validation]: Variable declarations prefixed with _ in GBDKPipelineV2 to match ExprVisitor.sanitizeVarName() convention — both must agree
- [Phase 05-integration-and-end-to-end-validation]: CFunction.isPrototype=true emits function prototype (;) not definition — prevents duplicate definition errors in game.h
- [Phase 05-integration-and-end-to-end-validation]: CVarDecl.isExtern=true for game.h declarations — header has extern declarations; main.c provides definitions
- [Phase 05-integration-and-end-to-end-validation]: Joypad state pattern: __joypad + __joypad_prev globals + update_joypad() per frame — edge detection for button_pressed vs button_held
- [Phase 05]: Explorer compiled first-try — all 6 codegen fixes from 05-03 covered RPG/complex features; GenericSystem instances for save/camera/battle/exploration silently ignored (acceptable)
- [Phase 05]: ValidateRomTask uses Gradle temporaryDir for Lua script — task-scoped, cleaned automatically
- [Phase 05]: Exit code 1 disambiguation: read process stdout to detect 'invalid option' vs ROM crash — Qt-only mGBA builds exit 1 on unsupported -S flag
- [Phase 05]: validateRom is opt-in (user runs explicitly) — buildRom does NOT depend on it per locked decision
- [Phase 05.05]: captureV2Location() is a top-level internal function in SourceLocationCapture.kt (not a method per builder) — single filtering logic shared by all dsl/v2 builders; skips io.github.gbkt.core.dsl, io.github.gbkt.core.ir, kotlin., java., jdk. frames
- [Phase 05.05]: Block-producing ScriptBuilder ops capture location eagerly before executing body block — val loc = captureV2Location() before child ScriptBuilder.block() ensures location is the outer call site
- [Phase 05.05]: SystemIR.sourceLocation added as abstract interface property (mirrors SystemIR.id) — all 6 subtypes override in constructor with null default
- [Phase 05.05]: CFor and CWhile get sourceLocation even though Pong does not use them — consistent CStatement coverage prevents gaps when those ops are added later
- [Phase 05.05]: SourceMapCollector.record() is a no-op when sourceLocation is null — structural C nodes (CBlankLine, CComment, CSwitch) silently skipped without branching at call site
- [Phase 05.05]: LineTrackingBuilder is a private inner class local to CEmitter.emit() — no mutable state on the CEmitter object; all state created per emit() call
- [Phase 05.05]: PipelineV2Output is a data class (not Pair) — self-documenting, returns files + sourceMaps; header files excluded from source map output
- [Phase 05.05]: getSourceMapJsonForFile() uses NoSuchMethodException catch for v1 backward-compat — reflection pattern mirrors existing GenerationResultWrapper.getContent() approach
- [Phase 05.05]: Regex-based JSON extraction in GbktCodegenService (no org.json) — IntelliJ plugin classpath doesn't include org.json (Gradle-only); v2 format is deterministic enough for regex parsing without external dependency
- [Phase 05.05]: ErrorEnhancer.enhanceErrors() delegates to enhanceErrorsMultiFile() with single-entry map — zero breaking changes to existing single-map callers; multi-file path adds per-file source map selection by error.file basename
- [Phase 05.05]: fileLineOffsets maps cFile to 1-based first content line in combined document — scrollToMatchingCLine uses fileOffset + cLine - 2 to get 0-based editor position
- [Phase 05.05]: VirtualFileListener with Disposable parent (CCodePreviewPanel itself) — auto-unregisters on panel dispose; monitors *.gbkt.map files for external generateC runs
- [Phase 05.05.1-v2-codegen-runtime-completion]: ScriptOp/Expr/SystemIR unsealed to interface — accept() visitor dispatch enables external module extension (BOM-01)
- [Phase 05.05.1-v2-codegen-runtime-completion]: ScriptOpVisitorI/ExprVisitorI/SystemIRVisitorI use I suffix to avoid collision with backend ScriptOpVisitor/ExprVisitor objects
- [Phase 05.05.1]: v1 engine packages kept in gbkt-core (circular dep constraint): entity/scene/graphics import v1 sealed IR which must stay in gbkt-core; moving them to gbkt-engine would require gbkt-engine→gbkt-core, circular with gbkt-core api()-re-exporting gbkt-engine
- [Phase 05.05.1]: Suggestions.kt moved to gbkt-ir (not gbkt-core) — pure utility with zero deps; gbkt-core gets it transitively via api(project(:gbkt-ir)); eliminates duplicate class problem
- [Phase 05.05.1]: Layered module hierarchy established: gbkt-ir ← gbkt-lang ← gbkt-engine ← gbkt-core (meta-module re-exporting all three via api()); existing downstream modules unchanged
- [Phase 05.05.1]: GBDK hardware coordinate offset +8x/+16y applied in all move_sprite() calls — position (0,0) in game logic maps to (8,16) in OAM hardware (required by GBDK hardware)
- [Phase 05.05.1]: CLiteral(n) emits 'nu' (not 'n') for non-negative values — CEmitter convention; tests must check move_sprite(0u, ...) not move_sprite(0, ...)
- [Phase 05.05.1]: hide_sprites_range uses CRawCode for loop body — avoids C89 mixed-declaration issue with UINT8 loop variable on GBDK; show_sprites_range is documented no-op (sprites shown by update_sprites)
- [Phase 05.05.1]: Sprite asset #include directives deduplicated with distinct() — two actors sharing same asset emit one #include; VRAM tile slots assigned sequentially from 0 without analysis pass dependency
- [Phase 05.05.1]: FadeOp revised to fade_in/fade_out palette helpers — sprite show/hide was wrong semantic for screen fade
- [Phase 05.05.1]: CallOp generates direct CCall — CallOp has function:String + args, not scriptId for body inlining
- [Phase 05.05.1]: Sound driver globals always generated even for silent games — consistent infrastructure for future use
- [Phase 05.05.1 P05]: ConvertSpritesTask bridges png2asset→v2 pipeline naming gap: png2asset generates paddle_tiles, v2 expects sprites_paddle_tiles; header uses #define alias to bridge
- [Phase 05.05.1 P05]: Zero-size tile array fixup: all-transparent PNGs produce uint8_t name[0] invalid in C89/C99; post-process output to replace with 16-byte placeholder
- [Phase 05.05.1 P05]: OAM slot allocation is tile-count-aware: nextOamSlot += tilesWide * tilesHigh; actor index (0,1,2) is not the correct OAM slot for multi-tile sprites
- [Phase 05.05.1 P05]: GenericSystem trigger stubs generated in HOME bank: simple_battle executes onVictoryOps immediately (MVP stub, no real RPG state machine)
- [Phase 05.05.1-v2-codegen-runtime-completion]: delay() wraps CallOp (not a new ScriptOp type) — reuses existing IR; delay_frames C helper does the VBlank waiting
- [Phase 05.05.1-v2-codegen-runtime-completion]: delay_frames loop uses CVarDecl before CFor with init=null — C89 compliance for GBDK lcc (matches hide_sprites_range pattern)
- [Phase 05.05.1-v2-codegen-runtime-completion]: timingAndUtilDecls group added to buildHeaderFile() — delay_frames and dpad_any callable from banked scene functions via game.h
- [Phase 05.05.1-v2-codegen-runtime-completion]: ArrayDef uses emptyList() default on GameIR — zero breaking changes to existing tests; u8Array() delegate follows VarDelegate pattern via GameBuilderContext thread-local
- [Phase 05.05.2]: ScriptBuilderContext uses ThreadLocal for active ScriptBuilder tracking — mirrors GameBuilderContext pattern; enables operator extensions on AssignableVar/ActorPropertyRef/ArrayVar to emit ops into enclosing builder without explicit receiver threading
- [Phase 05.05.2]: u8Array() signature changed to (size: Int, name: String? = null) — name inferred from property delegate name; backward-compatible via optional name override
- [Phase 05.05.2]: ArrayDelegate return type upgraded from String to ArrayVar — richer type with bracket operators, size property, and exists() bounds-check helper; consistent with plan requirement for bracket syntax
- [Phase 05.05.2]: forOp string-variable loop replaced with whileOp+bidx — forOp creates C loop var with no AssignableVar handle; whileOp with declared bidx satisfies zero-varRef() requirement
- [Phase 05.05.2]: @Deprecated(WARNING) for assign/varRef/literal/raw — allows gradual migration; WARNING not ERROR; consistent with Kotlin stdlib deprecation strategy
- [Phase 05.05.2]: Expr/ActorPropertyRef cross-type comparison and arithmetic overloads added — missing from Plan 01 infrastructure; needed for (paddle.y+8) isAbove ball.y and brow*10+bc patterns in migrated games
- [Phase 05.05.3]: InputRef wraps (gbdkConstant, heldFn, pressedFn); produces identical CallExpr IR as string-based methods — pure DSL layer, zero IR/codegen changes needed — 2026-02-21
- [Phase 05.05.3]: dpad and buttons are lowercase singleton objects with @Suppress(ClassNaming) — matches CLAUDE.md Quick DSL Examples syntax — 2026-02-21
- [Phase 05.05.3]: registerActor() extracted as internal GameBuilder method shared by actor(id,block) and ActorDelegate.provideDelegate — no duplication — 2026-02-21
- [Phase 05.05.3]: ActorDelegate uses ReadOnlyProperty<Any?,ActorRef> pattern matching ArrayDelegate — consistent delegation idiom — 2026-02-21
- [Phase 05.05.3]: DmgColor is minimal (4 int constants only) — GBC hex helpers deferred per CONTEXT.md — 2026-02-21
- [Phase 05.05.3]: ExprVisitor converted from object to class with actors: List<ActorIR> constructor; companion object provides backward-compat static calls — ThreadLocal<ExprVisitor> in ScriptOpVisitor propagates actor context through IfOp/WhileOp — 2026-02-21
- [Phase 05.05.3]: AABB inline C uses four CBinaryExpr conditions — no CGroupExpr needed (C && has lower precedence than comparison operators) — 2026-02-21
- [Phase 05.05.3]: PongV2 keeps nested whenever blocks for paddle collision (not ball.collides) — coordinate-range precision more important than AABB hitbox for Pong feel — 2026-02-21
- [Phase 05.05.3]: simpleBattle callbacks in ExplorerV2 kept as navigate("string") — builder accepts String not SceneRef — 2026-02-21
- [Phase 05.05.3]: Custom actor properties stored as prefixed global VariableDef (_actorId_propName) registered via GameBuilderContext.registerVariable — no ActorIR changes needed — 2026-02-21
- [Phase 05.05.3]: sceneRef() creates SceneRef without RefRegistry registration — avoids duplicate registration when scene() is called later with same ID; forward-declaration pattern breaks circular navigate cycles — 2026-02-21
- [Phase 05.05.3]: simpleBattle onVictory/onDefeat accept ScriptBuilder block which has navigate(SceneRef) overload — navigate(SceneRef) works in these callbacks; no API limitation — 2026-02-21
- [Phase 05.05.3]: Pong retains nested whenever for paddle collision (x 4..20, x 148..156) — coordinate-range precision trumps AABB hitbox for Pong gameplay feel; documented exception — 2026-02-21
- [Phase 05.05.3]: ScriptOpInterpreter.checkCollision() uses sprite.hitbox fallback chain: aIr?.hitbox ?: aIr?.sprite?.hitbox ?: default — ActorBuilder.hitbox() is rare; sprite { hitbox() } is the common pattern
- [Phase 05.05.3]: Assign to actorId.x/y syncs actorPositions immediately — AABB collision check reads from actorPositions map, not variables map
- [Phase 05.05.3]: ExplorerGameTest boundary tests use stability assertions (no clamping, player stays put without d-pad) because game uses movement PREVENTION guards, not position CLAMPING
- [Phase 06-01]: All v1 IR (36 files), v1 DSL (8 files), v1 codegen (40+ files), v1 domain models (165+ files) deleted — codebase is now v2-only — 2026-02-21
- [Phase 06-01]: GBCColor/GBCPalette/PaletteType relocated to gbkt-ir/src/.../ir/v2/CoreTypes.kt; CollectionsIR types (18) relocated to gbkt-ir/src/.../ir/v2/CollectionsIR.kt — shared types cross module boundaries via gbkt-ir — 2026-02-21
- [Phase 06-01]: CodegenBackend interface uses GameIR (not deleted Game class); validate() returns ValidationResult.SUCCESS (real constraint checking in analysis pipeline inside generateV2()) — 2026-02-21
- [Phase 06-01]: ValidationResult is self-contained in gbkt-backend-api — was re-exporting deleted io.github.gbkt.core.ValidationResult; now a proper data class with companion, enum, exception — 2026-02-21
- [Phase 06-01]: CEmitter.kt is a v2 file in emit/ directory — only file there; was accidentally deleted and restored; emit/ contains only v2 C AST pretty-printer — 2026-02-21
- [Phase 06-01]: LabyrinthOfTheDragon-port Kotlin sources deleted (all 40 used v1 DSL); will be rewritten in v2 in a later plan — 2026-02-21
- [Phase 06-01]: gbkt-gradle-plugin GenerateCTask has a dead v1 code path using GBDKCodeGenerator via reflection — silently fails at runtime (ClassNotFoundException caught); deferred to Plan 03 cleanup — 2026-02-21
- [Phase 06]: v2 package namespace removed — IR at io.github.gbkt.core.ir.*, DSL at io.github.gbkt.core.dsl.*
- [Phase 06]: detekt.yml WildcardImport excludeImports updated from dsl.v2.* to dsl.* — wildcard required for operator extension functions in game files
- [Phase 06-09]: C→DSL caret listener attaches to editor.caretModel directly (not EditorFactory.eventMulticaster) — scoped to C preview editor only
- [Phase 06-09]: BudgetGutterIconProvider targets KtNameReferenceExpression identifier leaf as anchor — one gutter icon per call site
- [Phase 06-09]: Budget report uses line-oriented text format to avoid org.json/Gson dependency on plugin classpath
- [Phase 06]: GBDKSystemVisitor returns emptyList() for SoundSystem and DialogSystem — their functions already generated by buildSoundFunctions()/buildDialogHelpers(), avoiding duplicate generation
- [Phase 06]: SimpleBattle COMBAT_STATE machine uses inline CRawCode for switch body (not typed CSwitch) — raw code is more readable for multi-case state machines with comment annotations
- [Phase 06]: OAM free list uses stack-based (LIFO) allocation: _oam_free_list[40] with _oam_free_top counter — O(1) claim and release without fragmentation
- [Phase 06]: Tileset reuse guard added in buildSceneFile() post-processing step (not SceneVisitor) — pipeline orchestrates the guard wrapping based on scene metadata
- [Phase 06-complete-gap-closure]: BitwiseOptimizationPass applies power-of-2 rewrites unconditionally — Game Boy variables are unsigned U8/U16 by convention; documented in diagnostic messages
- [Phase 06-complete-gap-closure]: Tile collision dispatch uses switch(current_scene) not function pointers — C89/GBDK compatibility requires switch-based dispatch; per-scene _map_collision_<id>() functions called from central _map_collision() dispatcher
- [Phase 06]: SoundEffectDef stored in GameIR.soundEffects (not systems) — sound effects are data not systems, avoids conflation with SystemIR visitor dispatch pattern
- [Phase 06]: hUGEDriver.h include and hUGE_dosound() loop call are conditional (hasMusicOps() scan) — only added when music ops actually used; avoids spurious header bloat in non-music games
- [Phase 06]: CFile.rawSections used for collection code injection — collections require 3+ parallel arrays per instance that don't fit CVarDecl typed AST
- [Phase 06]: VarType.cName defined as internal extension in GBDKCollectionCodegen — GBDK type names isolated in backend module
- [Phase 06-complete-gap-closure]: ScriptBuilderContext is internal to gbkt-lang module — tests using it must live in gbkt-lang, not gbkt-core
- [Phase 06]: CastExpr uses VarType (U8/U16/I8/I16) not C-type — IR stays platform-agnostic; ExprVisitor maps to CCast(CU8/CU16/CI8/CI16)
- [Phase 06]: ArrayVar helpers use ScriptBuilderContext.current (thread-local) not explicit ScriptBuilder parameter — consistent with AssignableVar/ActorPropertyRef operator extension pattern
- [Phase 06]: generateFrameOffsetInit returns emptyList when frameWidth is null — gradual adoption; existing actors without frameWidth unaffected
- [Phase 06-complete-gap-closure]: collisionData() validates mapWidth > 0, data non-empty, data.size % mapWidth == 0 at DSL layer — fail-fast prevents malformed SceneIR reaching codegen
- [Phase 06-complete-gap-closure]: gbkt-engine type files replace package-info.kt placeholders — same package, richer content with v2 types
- [Phase 06-complete-gap-closure]: gbkt-rpg added to gbkt-bom constraints — version coordination; opt-in per game module
- [Phase 06.1-01]: Kotlin cross-module smart cast: capture nullable CameraSystem fields into local vals in visitCameraSystem — Kotlin cannot smart cast public API properties from different modules
- [Phase 06.1-01]: SCX_REG/SCY_REG writes remain CRawCode — GBDK hardware register macros expand to lvalues that CBinaryExpr cannot represent as assignment targets
- [Phase 06.1-01]: CTernary chosen over CCall for camera clamp — C89/GBDK has no standard max()/min() functions; CTernary generates valid C89 inline ternary
- [Phase 06.1-03]: PHYSICS MovementStyle deferred to Plan 04 — generateMovementFunction() returns emptyList() for PHYSICS; function documented as PHYSICS stub placeholder
- [Phase 06.1-03]: Animation state machine uses CSwitch on _actorId_anim_state — O(1) dispatch, compiler-readable, mirrors target C output; each case handles frame cycling + auto-transition condition checks per frame
- [Phase 06.1-03]: Per-actor update calls injected via addMovementAndAnimationCalls() in GBDKPipelineV2 — prepends update_movement/update_animation CCall statements to scene frame function body before user script ops
- [Phase 06.1-03]: Cross-module smart cast for movementConfig.style captured in local val in GBDKPipelineV2 — Kotlin cannot smart cast public API properties from different modules (mirrors Phase 06.1-01 decision)
- [Phase 06.1-02]: GameBuilderContext uses separate transientHolder ThreadLocal<MutableSet<String>> for transient var tracking — mirrors existing holder ThreadLocal pattern; initialized fresh in with() call, restored in finally
- [Phase 06.1-02]: SaveDataBuilder uses fun slots(n)/checksum()/version(n) method setters — V2 DSL style; consistent with CameraBuilder/ExplorationBuilder pattern (not public var properties)
- [Phase 06.1-02]: CRawExpr for GBDK volatile pointer cast in visitSaveSystem — volatile UINT8* arithmetic cannot be expressed as typed CBinaryExpr; CRawExpr is approved escape hatch for GBDK-specific expressions
- [Phase 06.1-02]: CLiteral(0xAB) emits as 171u by CEmitter (decimal with unsigned suffix) — test assertions check for "171" OR "0xAB" for robustness
- [Phase 06.1-04]: PhysicsStep applies acceleration+gravity+velocity only — floor/ceiling bounce is scene-specific game logic via whenever() conditions; PhysicsStep does the universal 6-statement physics step with zero CRawCode
- [Phase 06.1-04]: Velocity INT8 (signed for direction) → position update via CCast(CU8) — matches GBDK hardware coordinate range; mirrors ActorVisitor CU8 convention from Phase 06.1-01
- [Phase 06.1-04]: Bounce coefficient stored as UINT8 0-255 (coefficient * 256) — integer math for Game Boy; DSL bounce(Float) converts internally; runtime divide-by-256 via bit-shift in game C code
- [Phase 06.1-05]: Iterative A* using CWhile (not recursive) — Game Boy stack is ~128 bytes; recursion would overflow immediately
- [Phase 06.1-05]: Bit-packed closed set: 1 bit per tile, mapWidth*mapHeight/8+1 bytes; 32x32 map = 129 bytes vs 1024 bytes naively
- [Phase 06.1-05]: PF_GRID_SIZE as #define constant (not CVar) — ScriptOpVisitor is singleton without gameIR access; #define emitted by GBDKPipelineV2.pathfindingDefines
- [Phase 06.1-05]: _pf_collision_fn stored as UINT8=0 stub — fn pointer typing (CFunctionPointerType) deferred; raw C check present but inactive until type promoted
- [Phase 06.1-07]: SaveDataBuilder.slots() is a function call not a property — slots(1) not slots = 1; consistent V2 DSL builder setter pattern
- [Phase 06.1-08]: A* walkability check uses CCall(_map_collision, [nx, ny]) directly — switch-based dispatch consistent with C89/GBDK architecture (fn pointers explicitly avoided project-wide)
- [Phase 06.1-08]: _pf_collision_fn UINT8 global removed entirely — non-functional stub; _map_collision() dispatch obviates any need for per-scene function pointer
- [Phase 06.2-01]: ShowDialog/ShowMenu removed entirely (not deprecated) — 12 typed ScriptOp subtypes (DialogSay, DialogChoice, MenuShow/Hide, HudShow/Hide, PrintAt/Centered/Aligned, ClearRegion, ScreenClear/Fill) forced immediate cleanup of analysis passes and backend
- [Phase 06.2-01]: DialogChoice.options: List<DialogOption> (not Map<Int,List<ScriptOp>>) — cleaner nested op traversal in analysis passes; index-based dispatch unneeded at IR level
- [Phase 06.2-01]: MenuDef in GameIR.menus canonical list — backend generates helpers from canonical list; GBDKPipelineV2 collectUniqueMenus() ShowMenu-scanning helpers removed
- [Phase 06.2-01]: FontMode enum (FIXED_WIDTH/VARIABLE_WIDTH) on PrintAt/PrintCentered/PrintAligned — backend selects _win_print_at vs _vwf_print_at at codegen time; both paths fully specified
- [Phase 06.2-02]: buildDialogFunctions(gameIR) generates per-dialog show_dialog_<id>() C functions from DialogDef IR — dialog ID encoded in function name for type-safe call sites
- [Phase 06.2-02]: visitMenuHide emits CRawCode(HIDE_WIN) not CCall(hide_menu_X) — pipeline only generates show_menu_X; HIDE_WIN macro is correct window-layer dismiss
- [Phase 06.2-02]: Portrait sprite slot 39 (PORTRAIT_SPRITE_ID) reserved as UI-only OAM slot — last slot avoids conflicts with actor OAM allocation starting at 0
- [Phase 06.2-02]: _vwf_char_widths[256] global emitted conditionally only when any dialog uses VARIABLE_WIDTH fontMode — avoids 256-byte constant overhead for non-VWF games
- [Phase 06.2-03]: buildMenuFunctions() iterates gameIR.menus directly — no collectUniqueMenus() ShowMenu-scanning needed (MenuDef canonical list established in 06.2-01)
- [Phase 06.2-03]: MENU_CURSOR_SPRITE_ID=38 reserved as named constant distinct from PORTRAIT_SPRITE_ID=39 — sprite slots 38/39 reserved for UI layer
- [Phase 06.2-03]: Sprite cursor hidden via move_sprite(38, 0, 0) — moves off OAM viewport rather than calling hide_sprites_range
- [Phase 06.2-03]: J_B cancel path emits CReturn(0xFF) directly (not CBreak) — loop exits via CBreak only for J_A (successful selection); cleanup before return
- [Phase 06.2-03]: Dynamic data source (InventoryDataSource/ArrayDataSource) uses CRawCode population loops — inline C variable _mi required for loop-scoped index not representable in typed CFor
- [Phase 06.2-04]: HUD tile index constants emitted as const UINT8 globals (_hud_fill_tile_<id>, _hud_empty_tile_<id>, etc.) — C89 requires lvalue for address-of operator in set_win_tiles calls
- [Phase 06.2-04]: _bkg_print_at/_bkg_clear_region generated only when any HUD has renderOnWindow=false — avoids dead code for window-only games
- [Phase 06.2-04]: DialogSystem _dialog_default_speed/_dialog_default_border generated via buildSystemGlobalVars (not visitor) — consistent with CameraSystem/ExplorationSystem globals pattern
- [Phase 06.2-04]: _hud_space_tile global emitted only when FILLED_ONLY icon mode is used — avoids 1-byte constant overhead for games not using FILLED_ONLY icons
- [Phase 06.2-v1-feature-parity-ui-layer]: BorderTiles data class reduces buildBorderStatements() from 10 to 5 params — detekt LongParameterList fix; screen object uses @Suppress(ClassNaming) matching dpad/buttons lowercase DSL singleton pattern
- [Phase 06.4]: InventoryVisitor uses buildItemCatalog/buildInventoryGlobals/buildInventoryFunctions in GBDKPipelineV2 — mirrors sound/dialog/HUD pattern
- [Phase 06.4]: Delegate tests use explicit-id combatEngine/container/item variants — Kotlin provideDelegate not called for local lambda variable delegates (only class-level property delegates)
- [Phase 06.4]: Menu codegen InventoryDataSource updated to _inv_<id>_size and _inv_<id>_items[_mi] — aligned with InventoryVisitor naming convention; printf format %u (not %s) since item IDs are UINT8 integers
- [Phase 06.5]: Delegate tests use explicit-id form (ability(id, block)) not val x by ability {} — provideDelegate not called for local variables in lambdas, only class-level property delegates; matches CombatInventoryBuilderTest pattern
- [Phase 06.5-01]: RpgVisitor uses generateCharacterStatStructs() + generateStatVarDecls() split: CFunction dispatch vs CVarDecl output — stat vars emitted from buildRpgCharStatVars in GBDKPipelineV2
- [Phase 06.5-01]: _item_names[] uses CArray(CPointer(CU8)) element type — emits const UINT8* _item_names[N] compatible with const char* on Game Boy hardware
- [Phase 06.5-03]: Behavior tree compiled to flat iterative C if/else chains at codegen time — zero C recursion for Game Boy ~128-byte stack
- [Phase 06.5-03]: BehaviorTreeBuilder.build() returns SelectorNode wrapping all accumulated root nodes (node accumulator pattern)
- [Phase 06.5-03]: MonsterDef.allowGlobalRepeatPrevention emits _mon_<id>_last_action UINT8 global and != guard before each action branch
- [Phase 06.5-03]: difficulty=EASY overrides all TargetStrategy to RANDOM; difficulty=HARD overrides to LOWEST_HP; difficulty=NORMAL uses tree-specified strategy (GAP-3)
- [Phase 06.5-03]: GAP-5 stat-contest apply chance: chance = applyChance - (target_resistStat - caster_matk) flat subtraction
- [Phase 06.5-03]: GAP-6 immunity: _char_<target>_immune_to_<effectId> boolean guard in apply_effect before applying
- [Phase 06.5-03]: GAP-7 INTENSITY stack scaling: damage = damagePerTurn * _effect_<id>_stacks when perStackScaling=true
- [Phase 06.5-03]: GAP-8 cleansing API: remove_effect_<id>() per-effect clear + dispel_buffs(target_idx) iterates all BUFF-category effects
- [Phase 06.5-10]: CombatHookPoint placed in gbkt-ir (not gbkt-rpg) because it is a direct field type on sealed CombatEngineSystem IR node
- [Phase 06.5-10]: hooks() extension bridges gbkt-rpg to gbkt-lang via setCombatHooks() setter pattern on CombatEngineBuilder
- [Phase 06.5-09]: WaveSurvivalConfig in gbkt-ir (not gbkt-rpg): CombatEngineSystem.waveSurvivalConfig field type must co-locate with CombatEngineSystem to avoid circular dependency
- [Phase 06.5-09]: Wave survival 6-state machine: INIT(0), WAVE_ACTIVE(1), WAVE_COMPLETE(2), BETWEEN_WAVE(3), VICTORY(4), DEFEAT(5) — distinct from normal combat 5-state machine
- [Phase 06.5-09]: generateWaveGlobals() added to CombatVisitor as public fun, wired in GBDKPipelineV2.buildSystemGlobalVars() alongside generateAtbGlobals() and generateHookGlobals()
- [Phase 06.5]: TACTICAL_GRID dispatch block in generateCombatFunctions() follows ATB/WAVE_SURVIVAL pattern; cfg defaults to TacticalGridConfig() when null
- [Phase 06.5-v1-feature-parity-rpg-package]: ATB INIT transitions to GAUGE_FILL (5) not PLAYER_TURN (1) — ATB always starts by filling gauges before any action
- [Phase 06.5-v1-feature-parity-rpg-package]: WAIT mode guard at GAUGE_FILL case level (not inside update fn) — update fn reused by PLAYER_TURN without menu_open check
- [Phase 06.5-v1-feature-parity-rpg-package]: Insertion sort for SPEED_BASED turn order (O(n^2)) — appropriate for n<=8 combatants on 4MHz Game Boy; minimal code size
- [Phase 06.5]: EquipmentConfig is a single config object carrying all equipment options (slots, dual-wield, set bonuses, upgrades, durability, enchanting) passed through GenericSystem config map for clean serialization
- [Phase 06.5-04]: ClassDef.equipRestrictions: Set<EquipSlot> defaults to all slots — restrictive DSL means removing slots, not adding
- [Phase 06.5]: sell override sentinel 0xFF for per-item sellPriceOverride in shop arrays (consistent with _equipped_slot null convention)
- [Phase 06.5]: GAP-4 guest member codegen as standalone functions (not CombatVisitor mutations) preserves engine layer purity
- [Phase 06.5]: GAP-11 save checksum uses CIf loop body for C89 compliance on GBDK lcc
- [Phase 06.5]: SC-5 PLAYER_TURN dispatch generated as standalone update_rpg_player_turn_id() — game author wires into combat frame
- [Phase 06.5-08]: simpleBattle() migrated from GenericSystem(type=simple_battle) to typed CombatEngineSystem(TURN_BASED) with encounterConfig: Map<String, Any>? field — null default is backward-compatible with all existing non-RPG usages
- [Phase 06.5-08]: buildSimpleBattleFunctions() removed from GBDKSystemVisitor (~168 lines) — CombatVisitor.generateCombatFunctions() already handles TURN_BASED correctly; simpleBattle now routes through existing dispatch
- [Phase 06.5-08]: party(String) overload added to SimpleBattleBuilder alongside party(CharacterDef) — API completeness for string-based party IDs in ExplorerV2 and integration tests
- [Phase 06.5]: CTernary used for sx/sy sign computation and direction offsets in tactical grid LOS and facing bonus — matches algorithm formula directly, generates clean ternary C output — 2026-02-24
- [Phase 06.5]: SC-7 closed: five tactical grid calc functions (BFS movement range, Bresenham LOS, facing bonus, elevation bonus, AoE shape dispatch) replace TODO stubs in CombatVisitor — 2026-02-24
- [Phase 06.6-deferred-gaps-dsl-gbc-audio]: GBC mode flows DSL config -> gbkt-build.properties -> Gradle flags to keep plugin decoupled from gbkt-lang IR
- [Phase 06.6-deferred-gaps-dsl-gbc-audio]: SemanticValidationPass only enforces 8-palette GBC hardware limit when gbcTarget != DMG
- [Phase 06.6-03]: Uge2cFinder extracted as internal object outside ProcessAssetsTask for testability without Gradle infrastructure
- [Phase 06.6-03]: Music DSL: val theme by music(asset('...uge')) registers MusicDef; scene { music(theme) } auto-emits MusicPlay/MusicStop in enter/exit ops
- [Phase 06.6-05]: Integration gate: after parallel plan execution run spotlessApply then build — caught formatting and detekt violations from plans 01-04
- [Phase 06.7-04]: SmoothMovementConfig as nullable field on MovementConfig for zero breaking changes to existing SMOOTH/GRID games
- [Phase 06.7-04]: DiagonalMode.RAW as default — no surprise normalization; NORMALIZED uses 181/256 integer approximation for 1/sqrt(2)
- [Phase 06.7]: Zone tilemap banking: auto-allocate zone tile arrays to ROM banks 2+ via first-fit bin-packing; SWITCH_ROM(N)/SWITCH_ROM(1) flanks set_bkg_tiles() in zone_load for banked data
- [Phase 06.7-deferred-gaps-entity-movement-world]: Coyote time counter only emits decrement; game logic handles reset to COYOTE_N when on_ground
- [Phase 06.7-deferred-gaps-entity-movement-world]: PhysicsConfig new fields all default to disabled (false/0) for backward compatibility
- [Phase 06.7-03]: NPC collision codegen placed in GBDKPipelineV2 (not GBDKSystemVisitor) — follows plan file list and existing entity collision pattern
- [Phase 06.7-03]: NPC collision test split: IR-model tests in gbkt-ir, codegen tests in gbkt-backend-gbdk — codegen tests need GBDKPipelineV2 access
- [Phase 06.7-03]: collidesWithNpcs(true) actors auto-assigned to _default_npc group in GameBuilder.build() — avoids magic strings in game code; pairwise OVERLAP rule auto-generated
- [Phase 06.7-03]: PUSH response uses integer mass-proportional displacement (massB/(massA+massB)) — appropriate for Game Boy 8-bit math
- [Phase 06.7-deferred-gaps-entity-movement-world]: Pool codegen in GBDKSystemVisitor companion object (static builders) not visitor override — pools are GameIR-level not SystemIR-level
- [Phase 06.7-deferred-gaps-entity-movement-world]: CRawExpr('0xFF') for GBDK sentinel values — CLiteral(0xFF) emits 255u breaking idiom and tests
- [Phase 06.7-deferred-gaps-entity-movement-world]: FixedPointMode.INTEGER is the default for both PhysicsConfig and SmoothMovementConfig — zero breaking changes to existing actors
- [Phase 06.7-deferred-gaps-entity-movement-world]: FP44 uses UINT8/INT8 accumulators (16 sub-pixels/pixel), FP88 uses UINT16/INT16 (256 sub-pixels/pixel) — compact RAM footprint for Game Boy hardware
- [Phase 06.7-deferred-gaps-entity-movement-world]: PoolGetActiveCount delegates accept() to CallExpr — adds Expr without touching ExprVisitorI interface, keeps all existing visitors working
- [Phase 06.7-deferred-gaps-entity-movement-world]: Pool ScriptOps carry maxSize field — ScriptOpVisitor singleton has no GameIR access, loop bounds embedded at DSL time via ActorPoolRef.maxSize
- [Phase 06.7-08]: TriggerObjectIR is the fifth sealed PuzzleObjectIR subtype — generic trigger with no built-in behavior, all logic in handlers; requires() takes PuzzleObjectRef (type-safe), stores IDs as List<String>
- [Phase 06.7-08]: Pool entity respondTo uses 'pool:<name>' prefix in respondToActorIds — avoids new field type, leverages existing string list; emits pool_<name>_any_at(x, y) check in codegen
- [Phase 06.7-08]: buildRequiresGuard() uses exhaustive when on PuzzleObjectIR sealed type to determine state variable per type — switch→_active, door→_open, plate→_pressed, block→_solid; TriggerObjectIR returns null (no single active state var)
- [Phase 06.7-08]: CSwitchCase value requires CExpr? not Int — wrap ordinal in CLiteral() for event-type dispatch in trigger fire function
- [Phase 06.7-deferred-gaps-entity-movement-world]: Integration tests build GameIR directly from data classes (not DSL) — isolates IR correctness from DSL parsing in integration tests
- [Phase 06.7-deferred-gaps-entity-movement-world]: ExplorationSystem required in codegen integration GameIR to trigger zone_load codegen with SWITCH_ROM emission
- [Phase 06.8]: H6 fix: generatePartyVarDecls now emits _party_reserve_exp_share CVarDecl(isConst=true) when enableReserve=true — was missing from original implementation
- [Phase 06.8-06]: All platformer builders produce GenericSystem (no sealed IR subtypes) for BOM pattern consistency
- [Phase 06.8-06]: WallJumpConfig is opt-in (null default) — wallJump{} block required to enable
- [Phase 06.8-06]: CollectibleDef is facade over engine PickupDef — codegen integration deferred to Plan 10
- [Phase 06.8-07]: Single PuzzleGridBuilder with mode switch (matchMode/blockPushMode) for unified match-3 + block-push API
- [Phase 06.8-07]: Undo stack unlimited by default (Int.MAX_VALUE), developer limits via undoDepth() when WRAM budget requires
- [Phase 06.8-07]: Optional timer omitted from GenericSystem config map when absent (buildMap pattern avoids nullable Any values)
- [Phase 06.8]: Stamina reuses ExplorationGaugeIR(id='stamina') per CONTEXT.md locked decision — StaminaGaugeConfig maps to gauge max, attackCost/dodgeCost emitted in ARPG codegen
- [Phase 06.8]: Roguelike uses GenericSystem(type=roguelike_system) — no new sealed IR subtypes
- [Phase 06.8]: Conditional roguelike codegen: dailyChallenge.enabled and roomClearGating flags drive function generation
- [Phase 06.8]: Multi-currency codegen uses PO key comment markers str_currency_{id} in add_{id}() function bodies for localization wiring
- [Phase 06.8]: CurrencyRef uses data class equality; id field normalized (hyphen/space to underscore) for C identifier safety
- [Phase 06.8]: PickupTypes in gbkt-engine, not gbkt-core — shared cross-genre engine constructs stay in engine module to avoid sealed-interface constraint
- [Phase 06.8]: Genre conversion extensions (CollectibleDef.toPickupDef, SportPickupDef.toPickupDef) live in genre packages, not gbkt-engine — engine must not depend on genre modules
- [Phase 06.8-deferred-gaps-genre-packages-rpg-extensions]: PuzzleVisitor lives in gbkt-genre-puzzle (not gbkt-backend-gbdk): genre modules own their codegen, ServiceLoader handles runtime discovery
- [Phase 06.8]: PlatformerVisitor lives in gbkt-genre-platformer (not gbkt-backend-gbdk); genre codegen belongs with genre domain types
- [Phase 06.9-06]: validateModuleBoundaries custom task (not configurations.all exclude) — exclude API blocks transitive deps which would break gbkt-lang's access to gbkt-ir types
- [Phase 06.9-06]: detekt-baseline-main.xml was legacy leftover — root build.gradle.kts configures detekt-baseline.xml; -main.xml variant never used by active detekt config
- [Phase 06.9-06]: @file:Suppress LargeClass on GameIRSerializer.kt — serializer handles all 34 IR node types; splitting would create artificial fragments without semantic benefit
- [Phase 06.10]: Used SoundPreset.WIN for Pong win condition (consistent with IR enum)
- [Phase 06.10]: Grouped Explorer .po strings by context: ui, combat, exploration
- [Phase 06.10]: RPG Lite uses sceneRef forward-declarations for circular navigation; battleUpdate in simpleBattle onVictory uses string navigate per ExplorerV2 pattern
- [Phase 06.10]: goalZone() has no onReach callback — navigation handled via coordinate checks in scene frame loop
- [Phase 06.10]: PlatformType must be explicitly imported from domain package (not re-exported by dsl.*)
- [Phase 06.10]: Used ExplorationPreset.DUNGEON_CRAWLER with gauge override for onLow/onDepleted callbacks in Dungeon example
- [Phase 06.10]: VarRef used for pool slot variables inside forEachActive bodies (not VarExpr which doesn't exist)
- [Phase 06.10]: Used racing() DSL RacingMode.AI_OPPONENT with rubberBanding to showcase full sport genre API in racer example
- [Phase 06.10]: SimpleBattleBuilder.onVictory/onDefeat must use ScriptBuilder.runWith() to properly set ScriptBuilderContext for compound assignment operators in battle callbacks
- [Phase 06.10]: Simulation scenario tests must avoid input-dependent paths (dpad_any, button_pressed are stubs in ScriptOpInterpreter); use pure variable-comparison paths instead
- [Phase 06.11-labyrinth-of-the-dragon-port]: Asset canonicalization: sprites/ for sprite PNGs (with monsters/ subdir), tiles/ for tileset PNGs with _tiles suffix — no duplicates across directories
- [Phase 06.11-labyrinth-of-the-dragon-port]: V2 entry point: LabyrinthOfTheDragon::create (object pattern), removed skipValidation and custom compilerFlags
- [Phase 06.11-labyrinth-of-the-dragon-port]: GAP-14 tables.c: treat Original's 48×99 stat lookup tables as static port data, not a new framework feature — game balance content, not infrastructure
- [Phase 06.11-labyrinth-of-the-dragon-port]: GAP-06 Zone Object DSL is the largest architectural gap — requires full ZoneObjectBuilder DSL (chests/sconces/levers/NPCs/doors) as its own framework plan before any floor porting
- [Phase 06.11-04]: PaddingConfig uses Map<String,Int> for per-context width — simpler than DSL builder
- [Phase 06.11-04]: Locale suffix only appended when -Pgbkt.locale explicitly set — preserves backward compat
- [Phase 06.11-04]: Over-width strings in PO files: warn but never truncate
- [Phase 06.11-03]: CombatStateId already existed as @JvmInline value class; CombatStates.kt adds constants object on top
- [Phase 06.11-03]: FlagRef placed in WorldBuilders.kt alongside FlagPageBuilder for co-location; checkFlag() is top-level function (returns Expr, not ScriptOp)
- [Phase 06.11-03]: CharacterDef.learningConfig defaults to null for zero breaking changes; AbilityLearningBuilder already existed, only wiring was missing
- [Phase 06.11-03b]: Sealed ZoneObjectIR in gbkt-ir — closed domain enables exhaustive when() dispatch in GBDKSystemVisitor without runtime overhead; 5 subtypes: ChestObjectIR, SignObjectIR, SconceObjectIR, NpcObjectIR, LeverObjectIR
- [Phase 06.11-03b]: zone_check_edges_{id}() generated separately from zone_transition_{id}() — zone_transition is a generic mover; edge checks are high-level decision points; called from exploration_step when zone has edge transitions
- [Phase 06.11-03b]: _player_level convention variable for encounter level range checks — ExplorationSystem has no party reference; developer must sync this variable to party leader level
- [Phase 06.11-03b]: Accumulator (acc) advances regardless of level/flag gate in encounter check — preserves correct weight distribution even when entries are filtered by level/flag conditions
- [Phase 06.11-03b]: EncounterDef backward compatibility: computed monsterIds property + EncounterDef.fromIds() factory retain all existing code using flat monster ID lists
- [Phase 06.11-labyrinth-of-the-dragon-port]: registerMonsters() is a GameBuilder extension (not standalone object) because monster() DSL requires GameBuilder context to register GenericSystem IR nodes
- [Phase 06.11-labyrinth-of-the-dragon-port]: MonsterTier has no ELITE — mindflayer/beholder mapped to BOSS, deathknight to RARE per Original tier table
- [Phase 06.11-19]: FontCharacterMapping placed in gbkt-core (not gbkt-backend-gbdk) — all games can use it without backend dependency; FONT_OFFSET=0x72 recommended for Czech builds (256-114=142 font tiles fit)
- [Phase 06.11-19]: Czech tile indices 128-141 in font.png: ě=128, š=129, č=130, ř=131, ž=132, ý=133, á=134, í=135, é=136, ú=137, ů=138, ď=139, ť=140, ň=141
- [Phase 06.11-19]: Diacritical marks (háček, čárka, kroužek) placed in rows 0-1 of 8x8 tile; glyph body in rows 3-7; replacement tile 0x3F ('?') used for unmapped characters with warning
- [Phase 06.11-05]: GameState.register(this) pattern: V2 delegates called inside game{} lambda so GameBuilderContext.current is set, enabling correct variable registration
- [Phase 06.11-05]: gbkt-genre-rpg dependency added in Plan 05 (not 06) because Characters.kt already existed in the module and needed it
- [Phase 06.11-labyrinth-of-the-dragon-port]: Use simpleBattle() with actual available DSL; plan template methods (maxPartySize, maxEnemies, turnOrder, onState, onTurnStart, awardExp, awardDrops) do not exist — documented mechanics in KDoc instead
- [Phase 06.11-labyrinth-of-the-dragon-port]: registerCombat() takes typed LabyrinthCharacters + Monsters parameters; all 4 classes registered via party() so runtime can select based on heroSelect choice
- [Phase 06.11]: Data class containers (not singletons) for ability refs — mirrors StatusEffects.kt pattern from plan 06; singleton objects can't hold AbilityRef instances requiring GameBuilder context without reflection
- [Phase 06.11]: appliesEffect() uses StatusEffectRef.id for zero-magic-string status effect references; trip/prone mechanics mapped to paralyzed; all-resist mapped to defUp buff
- [Phase 06.11-labyrinth-of-the-dragon-port]: Palettes as standalone GBCPalette instances outside game {} block; background palettes require explicit type=PaletteType.BACKGROUND
- [Phase 06.11-labyrinth-of-the-dragon-port]: defineSounds() as GameBuilder extension function follows defineCharacters() pattern; 31 SFX mapped to SoundPreset approximations
- [Phase 06.11-labyrinth-of-the-dragon-port]: SaveSystem.register() object wrapper for saveData() call; existing saveData() in LabyrinthOfTheDragon.kt replaced in scene wiring plans
- [Phase 06.11]: BattleScene.register() takes explicit gameplayRef/gameOverRef SceneRef params and GameState for battleMenuCursor/battleTargetIndex — dependency injection over Scenes.* direct access
- [Phase 06.11]: Floor 8 uses safeZone() matching floor8.c on_move returning false; mini-bosses as NPC objects with usedFlag; eye puzzle on floor 7 delegated to callOp runtime handler
- [Phase 06.11]: Plan 16: str_misc_physical retained as empty msgstr (correct — Original value is literally empty string for physical attack type)
- [Phase 06.11]: Plan 16: 20 missing gain_ability entries added to en.po and messages.pot — section comment said 0 strings but strings.h had 20
- [Phase 06.11]: Plan 16: Ability name padding stripped from en.po, framework auto-pads via PaddingConfig at compile time (Plan 04 approach)
- [Phase 06.11-17]: Czech RPG terminology: lektvar, elixír, éter, UTK+/OBR+ abbreviations
- [Phase 06.11-18]: buildRom deferred to Phase 07 — 758 compile errors from pre-existing framework gaps in RPG/flag/combat codegen (simpleBattle constants, player_level, flag declarations not emitted)
- [Phase 06.11-labyrinth-of-the-dragon-port]: Generate combat stub functions (monster_basic_attack, use_ability_*) and callOp stubs to allow ROM to link without external implementations
- [Phase 06.12-embedded-emulator-core-and-debug-loop]: gbkt-emulator is standalone tooling module with no gbkt-* deps; source map integration at Gradle plugin level
- [Phase 06.12-embedded-emulator-core-and-debug-loop]: GbEmulator.getMemory() returns MemoryAccess interface to encapsulate MMU behavior (bank switching, I/O side-effects)
- [Phase 06.12]: Custom tick loop (not gameboy.run()) enables stepFrame, speed control, and onTick hooks
- [Phase 06.12]: registerTickListener for onTick callback — fires inside Coffee-GB micro-op cycle
- [Phase 06.12]: Minimal test ROM: 32KB with ROM ONLY type + SKIP bootstrap avoids logo validation
- [Phase 06.12-04]: SourceMapResolver silently skips malformed .gbkt.map files; DebugLogWriter uses autoFlush PrintWriter with explicit flush per write; entry enrichment only when cLine set but kotlinFile null
- [Phase 06.12]: InputHandler takes EventBus parameter — EventBus is the Coffee-GB joypad input contract
- [Phase 06.12]: GbDisplayPanel uses nearest-neighbor interpolation for pixel-art crispness at 4x scale
- [Phase 06.12-03]: EmuPrintfInterceptor: check() takes Registers+AddressSpace for testability, lastInterceptedPc deduplication, public readCString, interceptor created in start()
- [Phase 06.12-07]: Use JTextArea with level prefix tags instead of JTextPane StyledDocument for simpler LogCatPanel implementation
- [Phase 06.12]: Used KeyboardFocusManager dispatcher for Space/F10 shortcuts so they fire regardless of toolbar button focus
- [Phase 06.12]: InputHandler (game controls) wired by caller not window — exposes isFocusable=true for caller to addKeyListener with EventBus
- [Phase 06.12]: MemoryAccess provider lambda pattern: () -> MemoryAccess? for null-safe emulator access when stopped
- [Phase 06.12]: HexViewTab default base address 0xC000 WRAM: where GBDK global variables live
- [Phase 06.12-09]: EmulatorSession does not duplicate source map enrichment — CoffeeGbEmulator already enriches entries internally; session only forwards to UI panel
- [Phase 06.12-09]: EmulatorSession overwrites EmulatorWindow placeholder toolbar callbacks after construction to wire real LogCatWindow and MemoryInspectorWindow toggle behavior
- [Phase 06.12]: EmulatorExtension clean break: replaced path/args/liveReload/liveReloadScript with scale/headless/maxFrames/externalEmulator
- [Phase 06.12]: run lifecycle task uses dependsOn(buildRom) + finalizedBy(runEmulator) pattern
- [Phase 06.12-11]: ROM boot integration test uses Assumptions.assumeTrue so CI works without pre-built ROMs
- [Phase 06.12-11]: gbkt-emulator added to BOM constraints and gbkt-all aggregator as first-class module
- [Phase 07-uat-gameplay-validation]: VariableInspector requires colon in bank:addr format to skip bare addresses
- [Phase 07-uat-gameplay-validation]: VisualDiff returns null diffImage on match - no unnecessary file writes on success path
- [Phase 07-uat-gameplay-validation]: InputScriptPlayer takes EventBus as constructor param (not reaching into CoffeeGbEmulator internals) for testability
- [Phase 07-uat-gameplay-validation]: ScreenshotCapture uses org.json.JSONObject (already in gbkt-emulator deps) for JSON sidecar — avoids additional dependency
- [Phase 07-uat-gameplay-validation]: stubEmulatorFactory injection enables pure unit tests for AgentDebugSession without real GB ROM
- [Phase 07-uat-gameplay-validation]: gbcMode propagates through AgentSessionConfig.toEmulatorConfig -> EmulatorConfig -> CoffeeGbEmulator.start() using GameboyType.CGB/DMG
- [Phase 07-uat-gameplay-validation]: Line-based script format for RunInputScriptTask (wait/press/hold/release/screenshot) — simpler than .kts eval for agent use
- [Phase 07-uat-gameplay-validation]: DiffScreenshotsTask has no buildRom dependency — pure file comparison task operating on any two PNG files
- [Phase 07-08]: UAT table split into DMG parity (20 scenarios) and GBC-specific (8 scenarios) tables; stub emulator for unit tests; color pixel verification as proxy for GBC mode active; gbkt-emulator added as testImplementation only
- [Phase 07-uat-gameplay-validation]: ROM-absent skip pattern: tests use if (!ROM_FILE.exists()) { return } instead of JUnit Assumptions — simpler and avoids Assumptions API dep
- [Phase 07-uat-gameplay-validation]: gbcMode=true for Racer emulator test: exercises CGB code path since Racer is GBC_COMPATIBLE target
- [Phase 07-uat-gameplay-validation]: SimulationContextV2 chosen over AgentDebugSession for explorer/dungeon/rpg-lite EmulatorTests: avoids testImplementation(:gbkt-emulator) dep addition
- [Phase 07.1-test-dx-and-agent-tooling]: extractControls/extractTransitions kept private (not internal) since tests only exercise them via buildMetadataFile JSON output
- [Phase 07.1-test-dx-and-agent-tooling]: inferVariableSemantic uses startsWith for word-boundary-aware flag detection (avoids 'frametimer' false-positive for 'met' substring)
- [Phase 07.1-test-dx-and-agent-tooling]: JUnit5 compileOnly in gbkt-test so consumers control JUnit5 version; assertions as top-level functions (not just extension fns) to support both receiver and non-receiver call sites
- [Phase 07.1]: emulator_start accepts game OR romFile for convention-based discovery; saveState/loadState added to StepAgent delegating to AgentDebugSession
- [Phase 07.1-test-dx-and-agent-tooling]: Playbooks live in project root as source files (not build dir) so developers can edit and version them — idempotent task never overwrites human-authored content
- [Phase 07.1-test-dx-and-agent-tooling]: PLAYBOOK.md sections: Overview, How to Play, Controls, Scene Flow, Win/Lose Conditions, Known Quirks, Variables Reference — written for LLM agent consumption, not deterministic test scripts
- [Phase 07.1]: Added gbcMode parameter to GbktTestExtension to support GBC ROMs; metadata-agreement tests use local Assumptions.assumeTrue directly
- [Phase 07.1-test-dx-and-agent-tooling]: TESTING.md covers both human developers and AI agents with 8 sections from quick-start to MCP tool reference
- [Phase 07.1-test-dx-and-agent-tooling]: Agent-Driven Testing section in CLAUDE.md restructured to 4 tiers with GbktTestExtension as Tier 2
- [Phase 07.1.1-02]: emulator_press tool is 17th MCP tool; press() holds button N frames then releases+advances 1 frame, matching GBDK pressed() edge-detection semantics — agents use 1 tool call instead of 2 for button taps
- [Phase 07.1.1-02]: contentOrNull used in handlePress (not content) — content would throw when the JSON primitive is absent; safe extraction pattern for optional args
- [Phase 07.1.1-agent-testing-critical-gaps]: readAll() returns type-correct values: INT8 sign-extended, UINT16/INT16 little-endian 2-byte; overrideTypes allows metadata to replace heuristic inference in VariableInspector
- [Phase 07.1.1-agent-testing-critical-gaps]: Pipeline always emits gbdk_offset for BG and direct_ascii for WIN in tileDecoders section; TileDecoderConfig.toDecoder() is private to GameMetadata.kt
- [Phase 07.1.2]: F-075: BankAllocator overflow throws IllegalStateException — no errors field added to AllocationResult since exception IS the error mechanism
- [Phase 07.1.2]: F-077: checkPalettePrecision replaced with intentional no-op — GBCColor stores rgb555 so precision checks at analysis time produce false positives
- [Phase 07.1.2]: F-033 advance: all bracket types get unconditional losses increment (every match has one loser).
- [Phase 07.1.2]: F-034: end-of-run clearing (not mid-scan) prevents corruption for runs > minMatch; run_start variable tracks run start.
- [Phase 07.1.2]: F-035: CWhile(CLiteral(1)) with swapped variable used as do-while emulation (no CDoWhile in C AST). Applied to both DOWN and UP gravity.
- [Phase 07.1.2]: Audit: all 4 genre modules clean for bug classes 1-4; no additional fixes required.
- [Phase 07.1.2]: All 9 examples passed generateC and buildRom without source changes — Plans 01-02 fixes were complete and correct
- [Phase 07.1.2]: Task 3 (emulator boot verification) deferred — requires active Claude Code session with MCP emulator server; all ROMs exist at correct paths
- [Phase 07.3-entity-pool-codegen-fix-inserted]: D-03: Dynamic OAM allocation — pool entities grab OAM slots at spawn time via spawn_actor(), release on destroy via destroy_actor(). Per-instance _pool_oam[N] array stores slot mapping.
- [Phase 07.3-entity-pool-codegen-fix-inserted]: Pool template actors excluded from update_sprites() and static OAM init; VRAM tile data still loaded for pool instance use.
- [Phase 07.3]: D-06: Pool context via ThreadLocal — ExprVisitor reads ScriptOpVisitor.activePoolContext directly (same package, internal visibility)
- [Phase 07.3]: D-07: Slot variable naming for nested collision loops uses _pool_<poolId.take(1)>i to avoid variable name collisions in nested loops
- [Phase 07.3]: D-08: Display sync offset constants +8 X, +16 Y are Game Boy hardware viewport offsets; multi-tile uses +8 per column/row
- [Phase 07.3-entity-pool-codegen-fix-inserted]: ScriptOpVisitor.setGameIR must be called in buildSceneFile before scene iteration to enable pool context redirection
- [Phase 07.3-entity-pool-codegen-fix-inserted]: OAM free list infrastructure (globals + 3 functions) generated only when actorPools is non-empty
- [Phase 07.3-entity-pool-codegen-fix-inserted]: init_oam_free_list() must precede pool init calls in main() so slots are populated before pool inits set oam entries to 0xFF
- [Phase ?]: Plan 22 (commit 0976e08b) is the named regression cluster for D-V3
- [Phase ?]: Scope-shift: palette WRITE path is NOT broken (OCPD slot 2 = cyan_pal correct); regression is in sprite tile/OAM subpal path — Emission #3 most likely culprit
- [Phase ?]: User chose sub-narrow + scope-shift: Plans 06a/06b/06c sub-probes then Plan 07 finding doc
- [Phase ?]: 10.2-06c
- [Phase ?]: MINIMAL BREAKING PAIR = set_bkg_palette (Emission #2) + bgFillCheckerboard hoist (Emission #3); constant declaration is compile-time required but interaction-inert; fix-target is buildMainFunction() emission ordering in GBDKPipelineV2.kt
- [Phase ?]: D-V3 closure verdict: PASS; scope-shift acknowledged: VRAM collision (LCDC.4=1), not palette write path; DMG non-regression confirmed (behavior1 byte-identical, behavior2 pixel-match)
- [Phase ?]: ROM-smoke gate D-16 PASS: clean metasprites + metasprites-stress buildRom both GREEN (32 KB each); Plan 08 codegen confirmed non-stale
- [Phase ?]: 1,053 JVM tests GREEN (gbkt-backend-gbdk + gbkt-mcp-server), 0 failures; DV3VisualV3DiagnosticTest confirmed GREEN
- [Phase ?]: AC-1 healed 2026-05-21 (11.3-06): gbkt-examples/.archive/ restored from 85d1c974^ + 6 empty shells removed; gitignored local stash now matches D-03 promise
- [Phase ?]: Plan 11.3-07: scrubbed gbkt-examples/CLAUDE.md Module Structure tree to drop 6 archived dirs + remove non-existent gbkt-examples/settings.gradle.kts reference; companion fix at line 44 under ## Adding a New Example applied per D-08 Claude's Discretion (WR-01 closed)
- [Phase ?]: Plan 11.3-08: replaced root README.md Explorer references with Racer to heal WR-02; out-of-scope IN-01..IN-04 deferred to 11.4-docs-sweep
- [Phase ?]: Phase 11.3 gap-closure 09/09: 3/3 gaps (AC-1, WR-01, WR-02) CLOSED via plans 06/07/08; 7-game ROM smoke green; AC-6 deferred to 11.4-quality-gate-clearance; out-of-scope gbkt-analysis:detekt+spotlessCheck drift verified pre-existing (commit 7ed229de baseline) and subsumed by AC-6
- [Phase ?]: SceneVisitor NEW-path (tilesetPath != null) branches to emit CVar macro refs for _zone_<id>_tilemap_WIDTH/_HEIGHT; LEGACY-path keeps CLiteral fallback — width/height are now sourced from ConvertZoneTilesetsTask macros (PNG IHDR), not ZoneIR defaults
- [Phase ?]: Avoids symbol-table noise for one-time call site per CONTEXT Claude's Discretion
- [Phase ?]: 12.10-01: StepAgent.settle() = 2-consecutive-identical / cap=30 / best-effort (never throws); advances via runFrames(1) to preserve held buttons
- [Phase ?]: 12.10-03: anchor4 hflip gated over OAM box (HIGH>=18%/LOW<=5%, calibrated 28.26%/0.00%); consumes captureScreenshotSettled on real ROM
- [Phase 12.10]: Phase 12.10 closed (test-harness-only): zero codegen drift proven via codegen-guard 27dab3f5..HEAD + base-commit byte-identical ROM rebuild; 6 strict targets byte-identical, pong PASS*
- [Phase ?]: Cartridge enum in Types.kt mirrors GbcTarget shape with mbcByte param; romBanks: Int? = null is the D-05 derive sentinel; absent-key JSON sentinel preserves backward compat for old JSON with explicit romBanks values
- [Phase ?]: Plan 13.1-04 Rule 3 auto-fix (d642d0d7) pre-satisfied Plan 13.1-05 — fromCartridgeConfig exhaustive Cartridge when expression already in place
- [Phase ?]: getMbcByte() reflective call on live Cartridge enum replaces CARTRIDGE_MBC_MAP — enum owns its byte per D-03
- [Phase ?]: CompileRomTask reads ramBanks from gbkt-build.properties first, extension as fallback (Pitfall 2 read-order guard)
- [Phase ?]: romBanks omitted from 7/8 examples (D-05 auto-derive); metasprites-stress keeps romBanks=4 defensive override
- [Phase ?]: DSL_REFERENCE.md documents cartridge(Cartridge.X), auto-sized romBanks, saveData delegate, triggerSystem typed ref for Phase 13.1 documentation gate
- [Phase ?]: Six Wave 0 RED test files authored (13.2-01): VarDelegateGuardTest, SaveDataDelegateSingleUseTest, I16FixedVarTest, RunIfAliasTest, EaseToZeroTest, WrapAtTest — all RED
- [Phase ?]: delegateUsed guard added to all five DSL delegate types; per-site @Suppress ceremony removed in PlatformerTemplate.kt and Banks.kt
- [Phase ?]: ZoneIR.mapWidth/mapHeight changed from Int=32 to Int?=null — mirrors bankOverride nullable sentinel pattern
- [Phase ?]: resolveZoneSize pure fn implements D-03 chain (explicit > PNG-derived > 20x18 fallback); no input combination returns 32x32
- [Phase ?]: Explicit zone import required in pipeline tests
- [Phase ?]: Circular-navigate SceneRef? var pattern for mutual scene references in embedded Kotlin fixtures
- [Phase ?]: LabyrinthOfTheDragon-port excluded, zone(String)/navigate(String)/start-String hard-deleted, public var start: SceneRef? established, no @Deprecated shims
- [Phase ?]: abstract class @Inject, newInstance, fun sprites(action: Action<SpritesExtension>) — no magic strings (Project Rule #1)
- [Phase ?]: strict fires before overflow, both before auto-correct permute, both gated on transparentIdx > 0 (Pitfall 6)
- [Phase ?]: countUsedVisibleColors > 3 with non-zero-pixel entries only; elephant (3 used) passes REQ-5
- [Phase ?]: ImageIO round-trip resets transparentPixel to 0 for synthetic indexed PNGs; use real elephant.png for Tests 13/15
- [Phase ?]: 13.7-02
- [Phase ?]: Per-sub-palette Spearman ranking (4-entry groups) revives dead flat-ranking BG polarity guard (Req 1, WR-01)
- [Phase ?]: Req 3 RGB555 quantization on source side deferred to 13.8-02 with @Disabled test citation
- [Phase ?]: @kotlin.test.Ignore used in backend-gbdk tests (kotlin-test only module, not JUnit5)
- [Phase ?]: Option B (MetaspriteIR.sceneId) for scene-scoped OBJ suppression — keeps blast radius inside MetaspriteIR layer
- [Phase ?]: initialSubPaletteSlot: Int? = null + sceneId: String? = null both null-default — zero ripple, preserves byte-identity (D-03) for shipped games
- [Phase ?]: W1: IOException-only catch in countUsedVisibleColors — scan-loop logic errors propagate, protecting REQ-5 OBJ-palette overflow guard from silent bypass
- [Phase ?]: W2+W3: stemName-keyed prePermuteIndexedPng temp name + temporaryDir relocation — collision-free naming for same-basename sprites, and temp files outside @OutputDirectory fingerprint
- [Phase ?]: Ran all 7 generateC tasks in single chained Gradle invocation (CLAUDE.md no-parallel-clean rule)
- [Phase 16-seed-triage]: D-13 complied: single serial Gradle invocation for all 7 clean+buildRom
- [Phase 16-seed-triage]: D-14 complied: SHA 8cef3dbca7d0868f42cf0d627921b8559d7754e8 pinned to substrate-sha.txt before build
- [Phase 16-seed-triage]: BanksEmissionTest INV-2 GREEN: SEED-014 and SEED-015 signalled VERIFIED-ALREADY-FIXED
- [Phase 16-seed-triage]: validatePlugins + pluginTest PASS: SEED-026 signalled VERIFIED-ALREADY-FIXED
- [Phase ?]: SEED-014 VERIFIED-ALREADY-FIXED: INV-2+INV-6 GREEN at substrate SHA; hasZoneSceneBinder guard sufficient
- [Phase ?]: SEED-015/016 VERIFIED-ALREADY-FIXED: trampoline calls dedup canonical pause_enter; Anchor-4 SRAM @Test present at BanksUatTest.kt:291
- [Phase ?]: TODO-13.8 WR-01/02/03 CONFIRMED-OPEN: allocatedZoneBank single-zone gap, initialSubPaletteSlot no collision guard, RGB555 no range check; route Phase 19/20
- [Phase ?]: D-08 passed: 10 visual seeds human-reviewed and locked by Michal Svacha on 2026-06-12
- [Phase ?]: SEED-004 override: user confirmed elephant renders correctly (VERIFIED-ALREADY-FIXED), overriding agent-proposed CONFIRMED-OPEN
- [Phase ?]: Visual cluster final split: 8 VERIFIED-ALREADY-FIXED + 2 CONFIRMED-OPEN (spawn-polish, sub-pixel-offset); cluster-visual.md locked for Plan 09 merge
- [Phase ?]: TargetProfiles.GAME_BOY_SCREEN added as canonical single-source-of-truth for 160x144 Game Boy screen dimensions; GameBoyConstants const val dropped to val for derivation chain
- [Phase ?]: MagicNumber ignoreNumbers=[0,1,2,3,4,8,16] covers idiomatic tile/bit values; expand if dry-run in 17-06 forces it
- [Phase ?]: detekt.yml D-01 complexity blocks preserved byte-unchanged for Phase 18 S3776 work (LongMethod/TooManyFunctions/LongParameterList/LargeClass)
- [Phase ?]: Entity Pools (#5) replaced with accurate data-pool API (pool delegate variants + acquire/free/hasSpace/activeCount)
- [Phase ?]: Tweening section (#6) removed entirely — tween()/Easing.* absent (FEAT-TWEENING.md archive)
- [Phase ?]: Camera System (#7) rewritten with CameraBuilder config + cameraOp(CameraAction.*) table
- [Phase ?]: Physics (#9) rewritten: per-actor gravity(n)/velocity/bounce/maxFallSpeed/platformerMode() + physicsUpdate()
- [Phase ?]: All 13 stale-API sections rewritten across plans 17-08/09/10
- [Phase ?]: D-16 cross-doc pass: zero stale-API references in any non-DSL_REFERENCE doc; FEAT-* provenance D-11 complete with real removal-commit hashes
- [Phase ?]: D-01: Hard-remove whenever with no @Deprecated grace — pool-collision overload relocated to runIf, all 80+ call sites migrated in same atomic wave
- [Phase ?]: Replace whenever with runIf throughout DSL_REFERENCE.md (DEPR-01 doc half)
- [Phase ?]: DEPR-02: Deleted typed-vs-string equivalence test; String overload removed, no meaningful re-expression possible
- [Phase ?]: Two-tier deprecation rule: post-1.0 uses @Deprecated grace period; pre-1.0/Hardening milestones use hard removal with CHANGELOG as minimum bar (D-04)
- [Phase ?]: N-01 batchAssert: EXTRACT-METHOD (not NOSONAR) — per-type helpers + CheckResult data class reduce cc=74 to below threshold
- [Phase ?]: Promoted walkOps from local closure to top-level private fun with explicit result parameter (E-13+E-19); extracted three buildHeaderFile sub-builders as value-returning functions (E-15)
- [Phase 18]: Per-type puzzle helpers return PuzzleObjectOutput(vars, functions, perFrameCalls) — value-returning extract-method with no shared-mutable accumulation — Pitfall 1 compliance: extracting each sealed-interface branch into a focused helper that returns its contribution as data (not side-effecting a shared list) preserves emission order and enables future independent testing of each puzzle type
- [Phase ?]: E-02 MenuVisitor.buildMenuFunction (cc90) decomposed: 11 class methods + 5 file-level privates; 7-example byte-identity sweep passes
- [Phase ?]: D-07 byte-identity oracle baseline captured at phase start; before.sha256 records 3 generated C hashes from clean dual buildRom
- [Phase ?]: after.sha256 byte-identity oracle CLEAN: Phase 19 introduced zero production codegen drift (D-07/Req 5)
- [Phase ?]: D-08 commit separation confirmed: all 8 Phase 19 commits are evidence/test/doc only, zero S3776 refactors
- [Phase ?]: D-02 gate: INV-2/INV-5/INV-6 GREEN at HEAD; SEED-014/015/016 VERIFIED-ALREADY-FIXED
- [Phase ?]: D-06 two-tier byte-identity proof: per-commit baselines (tier 1) + phase-close 7-example sweep (tier 2) — all 14 .c files stable, zero generated-C drift
- [Phase ?]: SEED-021 closed: pivotAdjust(Int) DSL setter is single source of truth; visitor reads from config with fallback
- [Phase ?]: GameIRSerializer: Option 2 documented contract for 10 stubs; GenericSystem round-trips; full typed SystemIR serialize-only
- [Phase ?]: SEED-027 + SEED-028 closed VERIFIED-ALREADY-FIXED (Phase 18); archived to seeds/archive/ with grep evidence; zero production code change
- [Phase ?]: GBC-target ROMs must be UAT-captured with gbcMode=true; discoverFiles() does not enable GBC (DMG default inverts the palette)
- [Phase ?]: Phase 21 plan 21-07: all four LOCKED-visual platformer seeds archived (3 FIXED, 1 CLOSED-AS-ACCEPTED) on binding GBC-mode sign-off

### Pending Todos

None yet.

### Roadmap Evolution

- Phase 15 added (2026-06-08): Full-green test suite for v0.1.0 release — RELEASE-BLOCKING. User declined the Phase 14 release sign-off because a cleanup phase must leave a working tree; the `:buildRom`+byte-identity-only acceptance carve-out is overruled for release. Scope = drive the ENTIRE JVM suite GREEN (all 7 pre-existing failures: gradle-plugin IntegrationTest, banks/pong/platformer UAT + geometry), diagnose-first, no assertion-weakening. Depends on Phase 14; BLOCKS Phase 14 sign-off + milestone v1.0/v0.1.0 completion. NEXT: /gsd-spec-phase 15 → /gsd-discuss-phase 15 → /gsd-plan-phase 15 WITH research.
- Phase 14 added (2026-06-06): cleanup for v0.1.0 release — TERMINAL cleanup-only phase. Retire LabyrinthOfTheDragon-port + all non-functioning examples (racer confirmed; dungeon/explorer/shmup suspect), drop migration-era `V2` suffixes (GBDKPipelineV2, SimulationContextV2, etc.), remove genuinely-dead pre-AST codegen code; end state = lean buildable tree ready to tag + publish the v0.1.0 GitHub release (project is honestly pre-1.0; internal "v1.0" milestone label was a working name). Depends on Phase 13. NEXT: /gsd-spec-phase 14.
- Phase 3.1 inserted after Phase 3: Collection Abstractions — first-class IR nodes for static collection patterns (IRHashTable, IRPool, IRRingBuffer, IRFixedSlots) with hybrid backend traits (INSERTED, 2026-02-18)
- Phase 4 dependency updated: now depends on Phase 3.1 (RAM planning needs collection size data)
- Phase 05.05.1 inserted after Phase 5.05: V2 Codegen Runtime Completion — sprite rendering, OAM management, sound effects, and critical ScriptOp handlers. Closes gap where v2 pipeline compiles valid ROMs but produces invisible gameplay (16/24 ScriptOps stubbed, no move_sprite/set_sprite_data calls). Blocks 5.06 UAT. (URGENT, 2026-02-20)
- Phase 5.06 dependency updated: now depends on Phase 05.05.1 (sprites must render for gameplay to be testable)
- Phase 05.05.2 inserted after Phase 5.05: V2 DSL Ergonomics — refactor v2 DSL from assembly-style syntax (assign/varRef/literal) to Kotlin-idiomatic syntax. Improves developer experience before UAT. (INSERTED, 2026-02-20)
- Phase 05.05.3 inserted after Phase 05.05.2: V2 DSL Ergonomics Completion — close remaining gaps from 05.05.2 CONTEXT.md dropped during planning: type-safe input API, type-safe scene refs, actor name inference, collision DSL in examples, custom actor properties, color definitions. (INSERTED, 2026-02-20)
- Phase 06.1 inserted after Phase 06: V1 Feature Parity Port — V1 code deleted in 06-01 before V2 achieved feature parity. V2 is ~18% feature-complete vs V1. Must port all V1 capabilities: RPG combat (13K lines codegen), dungeon/world system, UI (dialogs, menus, status bars), camera/animation, physics/movement, save/load, NPC pathfinding, and all example games including LabyrinthOfTheDragon. V1 reference: git commit f82518e (last commit with V1 code). (URGENT, 2026-02-21)
- Phase 06.12 inserted after Phase 06.11: Embedded Emulator Core and Debug Loop — embed Coffee-GB (Java GB emulator, MIT, Maven Central) as `gbkt-emulator` module to close the developer debug loop. Captures EMU_printf output (ld d,d trap) to `build/gbkt/logs/debug.log` in real-time. Replaces fire-and-forget mGBA launch with integrated emulator. SameBoy (C, JNI) as upgrade path if accuracy insufficient. Prerequisite for UAT (Phase 07) and IDE-embedded emulator (Phase 09). (INSERTED, 2026-02-28)
- Phase 07 dependency updated: now depends on Phase 06.12 (embedded emulator provides debug loop for UAT)
- Phase 07.1 inserted after Phase 07: Test DX and Agent Tooling — eliminate test boilerplate (GbktUatTestBase), simplify UatRunner DSL, create game playbook format for LLM agents, enrich game_metadata.json with input mappings/scene transitions/win conditions, create MCP skill skeleton for developer-defined LLM QA, write per-game UAT stubs for all 8 remaining examples. Prerequisite for meaningful UAT and LLM-driven game testing. (URGENT, 2026-03-18)
- Phase 07.2 inserted after Phase 07.1: Interactive Game UAT — play-test all 9 example games together (human + Claude) using improved tooling from 07.1. Verify gameplay, find bugs, capture golden screenshots. (INSERTED, 2026-03-18)
- Phase 07.1.1 inserted after Phase 07.1: Agent Testing Critical Gaps — close 3 gaps blocking autonomous LLM game testing: emulator_press tool, custom TileDecoder wiring through StepAgent/UatRunner/MCP, 16-bit variable support in Observation/MCP. Also absorbs Phase 07 Plan 09 (UAT Guide documentation). Audit revealed text assertions fail for ~40% of games (custom tilesets), button presses cost 2x tool calls, 16-bit vars silently truncate. (URGENT, 2026-03-20)
- Phase 07.2 dependency updated: now depends on Phase 07.1.1 (critical agent testing gaps must be closed before interactive UAT)
- Phase 07.1.2 inserted after Phase 07.1.1: Hardening Bug Fixes — fix 5 functional codegen bugs producing broken C output: F-033 tournament sort swaps wins not losses, F-034 match-3 never clears matched cells, F-035 puzzle gravity single-pass only, F-075 bank allocator overflow silently exceeds bank size, F-077 palette precision check after quantization is fundamentally wrong. Must land before UAT play-testing. (URGENT, 2026-03-22)
- Phase 07.2 dependency updated: now depends on Phase 07.1.2 (hardening bug fixes must land before play-testing)
- Phase 07.9 inserted after Phase 07: C-codegen signed-vs-unsigned literal discipline (URGENT)
- Phase 09.2 inserted after Phase 09.1: Fix generateC stale-output sync — GenerateCTask must clear stale files; add ROM-build smoke test to verifier (URGENT)
- Phase 09.4 inserted after Phase 9: resolve simple-physics smiley-vs-ball naming inconsistency surfaced at 09.3 UAT (URGENT)
- Phase 11.2 inserted after Phase 11: tileset-pipeline-set-bkg-data-emission — closes SEED-014 visual gap surfaced by Phase 11.1 plan 08 (set_bkg_data not emitted; PNGs blank). Sibling of 11.1; must ship GREEN before resuming /gsd-execute-phase 11.1 wave 5 Task 2 + wave 6. (URGENT)
- Phase 11.1 edited: Phase 11.1 PAUSED mid-wave-5 at plan 7/9 — Plan 11.1-08 Task 2 (anchor 1+2 PNG human-verify) blocked by SEED-014 set_bkg_data gap surfaced by 11.1-08 Task 1. Resumes after /gsd-execute-phase 11.2 ships GREEN: re-shoot PNGs (now non-blank), run wave 6 (11.1-09 regression sweep + handoff), code-review + verifier + roadmap close. Resume path tracked in TaskList #9.
- Phase 12.1 inserted after Phase 12: Resolve 4 wide-blast-radius codegen-symbol-contract defects surfaced by Plan 12-18 first :buildRom (BANK macro on HOME-bank tilemap; tilemap WIDTH/HEIGHT emission; _player_* naming + actor declaration; _posX/_posY metasprite render). Terminal subphase per feedback_many_small_plans_terminal_subphase — no 12.1.1. (URGENT)
- Phase 12.2 inserted after Phase 12: ConvertZoneTilesetsTask real-tilemap extraction via png2asset -map mode (close Defect 7) (URGENT)
- Phase 12.3 inserted after Phase 12: PlatformerVisitor auto-emission wiring — 4 framework gaps surfaced in Phase 12 Wave 13 (input→playerVx, camera_update call, metasprite camera-offset, walkFrameIdx cycle). Phase 12 Wave 13 blocked until 12.3 ships (SHIPPED 2026-05-24 via Plans 12.3-01..15; anchor 4 closed via Phase 12.5 + debug session)
- Phase 12.4 inserted after Phase 12: sprite-pipeline png2asset integration — unblocks Phase 12.3 anchor 4 visual closure (URGENT)
- Phase 12.5 inserted after Phase 12: png2asset metasprite layout fix + Phase 12.3 closure (continuation of 12.4 PARTIAL) (SHIPPED 2026-05-24 Plans 12.5-01..13 complete; Plan 12.5-14 code review remaining)
- Phase 12.11 inserted after Phase 12: platformer level-2 gameplay-zone near-blank render in UAT harness — codegen/render defect routed out of Phase 12.10 (settle primitive provably does not fix it; pre-existing, ROM byte-identical to pre-12.10) (URGENT)
- Phase 13.6 inserted after Phase 13: sprite-pipeline transparency — route non-zero PNG tRNS color to GB OBJ index 0 in ConvertSpritesTask (SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX) (URGENT)
- Phase 13.7 inserted after Phase 13: platformer color polarity — inverted BG (ConvertZoneTilesetsTask) + OBJ (ConvertSpritesTask) palettes; regressed since 12.9 (likely 13.3 Color refactor); diagnose-first. See SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ.md (URGENT)
- Phase 13.8 inserted after Phase 13: palette/sprite codegen hardening — close deferred WR debt from 12.9 (WR-04/05) + 13.7 (WR-01/02/03/05) (URGENT)

### Blockers/Concerns

- Phase 4 scene locality optimization (grouping scenes that transition to each other into shared banks) has no standard reference implementation — RESOLVED: implemented in BankingAnalysisPass via NavigateTo transition graph with bidirectional locality
- Phase 5 VBlank transfer scheduling is hardware-timing-sensitive — may need Pan Docs deep dive and mGBA debugging
- RpgRegistry.clear() not called after build() — potential ThreadLocal memory leak in long-running test processes; acceptable for now, should be addressed in Phase 2 or when writing integration tests

- [Phase 06.3-01]: WorldIR types placed in single WorldIR.kt (not split FlagsIR.kt) — all related world data types in one location, simpler imports for codegen consumers
- [Phase 06.3-01]: ExplorationGaugeIR and ExplorationKeyIR placed in WorldIR.kt (not SystemIR.kt) — world-specific types belong with world IR, not the system IR hierarchy
- [Phase 06.3-01]: recordStatements() defined as internal top-level function in WorldBuilders.kt — accessible from both WorldBuilders and SystemBuilders (same dsl package) without duplication
- [Phase 06.3-01]: ExplorationPreset enum in SystemBuilders.kt (not WorldBuilders.kt) — preset is ExplorationBuilder configuration, mirrors SoundPreset placement in same file
- [Phase 06.3-01]: FlagsBuilder dual overload: flags(block) for unnamed single container, flags(id, block) for named containers — consistent with exploration(block) vs saveData(id, block) precedent
- [Phase 06.3-04]: AudioMixerBuilder uses GenericSystem with type=audio_mixer config map — no new SystemIR sealed type needed, preserves sealed hierarchy exhaustiveness
- [Phase 06.3-04]: ChannelGroupDef placed in gbkt-lang (not gbkt-ir) — DSL-specific type; accessible from backend via transitive api() dependency chain
- [Phase 06.3-04]: Default groups auto-populated only when config['groups'] absent — single group() call disables all defaults (full user control)
- [Phase 06.3-04]: NR50_REG formula: eff = (vol * master) / 7; NR50_REG = (eff << 4) | eff for L+R identical volume
- [Phase 06.3-04]: NR51_REG masks combine L-enable (bits 7-4) and R-enable (bits 3-0) per channel simultaneously
- [Phase 06.3-04]: audio_mixer_duck() is no-op (not omitted) when autoDucking=false — consistent API surface
- [Phase 06.3-04]: buildEntityCollisionFunctions stub (emptyList) added to unblock compilation while Plan 03 implements full entity collision codegen
- [Phase 06.3-03]: Entity collision grid uses bit-packing (1 bit per tile) for 32x32 maps — 129 bytes total (MAP_SIZE=129); consistent with pathfinding closed set pattern
- [Phase 06.3-03]: Gap 1 pattern: emit _blocking_entity_id = entity_id before onBlocked statements in BLOCK_AND_TRIGGER case; emit _pushed_entity_id + _push_direction before onPushed statements in PUSH case
- [Phase 06.3-03]: Gap 2 pattern: HITBOX collision shape dispatches AABB pixel overlap check using px/py/ex/ey variables; hasHitbox flag gates HITBOX code path generation
- [Phase 06.3-03]: CFor uses increment: CExpr? parameter (not update: CStatement) — discovered from CStatement.kt data class; increment is a raw expression, not a statement
- [Phase 06.3-03]: PASSTHROUGH entities excluded from entity grid — collisionActors filter keeps only non-PASSTHROUGH actors; zero overhead for passthrough actors
- [Phase 06.3-02]: zone_transition sequence: onExit dispatch (switch on _current_zone_id) → edge-based position mapping (switch on edge) → zone_load call — matches Gap 8 requirement (exit fires before load)
- [Phase 06.3-02]: 0xFF sentinel for _current_zone_id — no zone loaded yet state; prevents false onExit dispatch on very first transition
- [Phase 06.3-02]: buildZoneData() returns Pair<List<CVarDecl>, List<CDefine>> — zone arrays are const data in main.c, not extern-declared in game.h; simpler than splitting into two injection points
- [Phase 06.3-02]: Tileset reuse guard compares _current_tileset_id before set_bkg_data() — avoids redundant VRAM writes for same-tileset zone transitions
- [Phase 06.3-05]: WorldBuilderTest uses game("test") { }.build() pattern from UIBuilderTest — full DSL registration path, then assert on resulting GameIR fields
- [Phase 06.3-05]: ExplorerV2 world DSL additions are purely additive — ExplorerIRTest scene/actor count assertions unchanged (still 5 scenes, 1 actor)
- [Phase 06.3-05]: EntityCollisionBuilders.kt renamed to EntityCollisionBuilder.kt — MatchingDeclarationName detekt rule (single top-level declaration must match filename)
- [Phase 06.3-05]: detekt.yml TooManyFunctions excludes **/dsl/** — GameBuilder is DSL top-level entry point that legitimately accumulates one method per system; 23 > threshold 20
- [Phase 06.4-01]: ItemEffectIR is a non-sealed interface — RPG module can extend with domain-specific effects without touching core IR; backends use is checks or registry pattern for dispatch
- [Phase 06.4-01]: ItemDef.maxStack is nullable — null means inherit from ItemCategoryDef.defaultMaxStack; set value overrides category default; codegen resolves inheritance at code generation time
- [Phase 06.4-01]: combatEngine() has two distinct overloads: combatEngine(id, block) returns CombatEngineRef; combatEngine(block) returns CombatEngineDelegate for property name inference — avoids Kotlin ambiguous overload resolution
- [Phase 06.4-01]: container() follows same two-overload pattern as combatEngine() — container(id, block) returns ContainerRef; container(block) returns ContainerDelegate
- [Phase 06.4-01]: GBDKSystemVisitor.visitCombatEngineSystem() is emptyList() stub with TODO 06.4-02 — Plan 02 implements real codegen dispatch to CombatVisitor
- [Phase 06.4-01]: CombatTypes.kt and InventoryTypes.kt use @file:Suppress(MatchingDeclarationName) — multiple top-level declarations per file; files named for purpose not first declaration; matches DslMarkers.kt precedent
- [Phase 06.4-02]: Sub-state IDs start at 64 — leaves 5-63 for custom states; typical Game Boy games have <10 custom states so 64 headroom is sufficient
- [Phase 06.4-02]: buildConditionCheck() extracts first IfOp condition from onVictoryCondition/onDefeatCondition list — DSL's whenever() always produces IfOp; fallback path included defensively
- [Phase 06.4-02]: CLiteral(0xFF) emits as 255u in CEmitter — combat codegen test assertions must check for 255u not 0xFF
- [Phase 06.4-02]: combat_parent_state() returns state itself for top-level states via switch default case — safe parent queries without caller tracking state type
- AC-1 healed 2026-05-21 — Phase 11.3 GAP-1 closed. `gbkt-examples/.archive/` local stash restored from commit `85d1c974^` (parent of the archive commit; = `2eaa6e7b`); 6 empty shell dirs at `gbkt-examples/{explorer,rpg-lite,dungeon,platformer,platformer-gbc,shmup}/` removed. Gitignored per D-03 / D-04 (`.gitignore:55` `gbkt-examples/.archive/`). Gap-closure plan: 11.3-06.
- AC-6 detekt half closed 2026-05-21 — Phase 11.3 AC-6 spotless half closed earlier (commit `612ed65e`); detekt half closed via quick task [`20260521-detekt-gbkt-analysis-cleanup`](quick/20260521-detekt-gbkt-analysis-cleanup/SUMMARY.md). 12 atomic commits across 6 library modules + 2 example subprojects. Smart balance: 11 surgical refactors / per-site `@Suppress` + 9 justified config extensions (each paralleling an existing exemption in detekt.yml). `./gradlew detekt` now EXIT 0 globally.
- AC-6 TrackSynthesizer RED stubs cleared 2026-05-21 — Plan 07.4-35 GREEN (commit `8d4c56e2` production + `docs(07.4-35)` evidence). `TrackSynthesizerCircuitShapeTest` 2 RED stubs (`racer_waypoints_synthesize_to_corridor_not_arena`, `racer_corridor_interior_is_non_drivable`) now GREEN; mismatch_count vs expected corridor = 0 (was 55). Three-tier verification: JVM (full :gbkt-genre-sport:test 159 GREEN) + codegen (regenerated `_zone_track1_tiles[361]` matches `07-expected-circuit-tilemap-ascii-art.txt` byte-for-byte) + runtime (Round8TrackTilemapShapeProbe VRAM probe mismatch_count=0). Racer ROM rebuilds clean. Phase 11.3 AC-6 `./gradlew clean build` exit 0 no longer blocked by this gap; remaining red on `./gradlew build` is the pre-existing `:gbkt-examples:metasprites-stress:test` "no tests discovered" failure (Gradle-9 strictness, unrelated, requires separate routing).

## Quick Tasks Completed

| Date | Slug | Outcome |
|------|------|---------|
| 2026-05-21 | [detekt-gbkt-analysis-cleanup](quick/20260521-detekt-gbkt-analysis-cleanup/SUMMARY.md) | AC-6 detekt half closed. 12 atomic commits across 6 modules + 2 examples. Global detekt EXIT 0. |
| 2026-05-21 | [07.4-35-track-synthesizer-circuit-shape](phases/07.4-sport-genre-codegen-fix-inserted/07.4-35-SUMMARY.md) | GAP-TRACK-NOT-RENDERED-AS-CIRCUIT closed inline (user override of route-to-phase rule). TrackSynthesizer Bresenham + Chebyshev thickening. JVM + codegen + runtime all GREEN; mismatch_count 55→0. Commit `8d4c56e2` + evidence files 14-18. |
| 2026-06-05 | [260605-eqr-fix-three-test-infra-issues](quick/260605-eqr-fix-three-test-infra-issues-convertzonet/260605-eqr-SUMMARY.md) | Test-suite triage follow-up: 13/15 failures were stale-mavenLocal noise, not bugs. Item 1: hoisted zone-scoped tilemap-PNG guard in ConvertZoneTilesetsTask (real 13.4 regression, 11/11 green). Item 2: new `pluginTest` root task republishes 7 consumed SNAPSHOT modules before plugin tests (local-dev durable fix; CI already publishes). Item 3: generateC `whenever{}` failure was a fixture forward-reference NPE, not a `syncOutputDir` bug. Full plugin suite 138/138; `pluginTest` BUILD SUCCESSFUL. Commits `0c9a5679`, `c512064b`, `5378fdea`. |
| 2026-06-11 | [260611-k1w-unify-version-catalog](quick/260611-k1w-unify-version-catalog-libs-versions-toml/260611-k1w-SUMMARY.md) | Verified (5/5). Full `[plugins]` migration to `gradle/libs.versions.toml` (9 plugins incl. kotlin/spotless/detekt/sonarqube/kover/plugin-publish/shadow/intellij-platform); JUnit BOM de-inlined (6 sites, 4 modules); `org.json:json` hardcode → `libs.json`; pluginManagement version pins removed (repositories + includeBuild retained). Serialization plugin drift fixed 2.3.0→2.3.20 via shared `kotlin` version.ref — proven the ONLY resolution change by before/after `dependencies` (byte-identical) + `buildEnvironment` diffs. Bonus: pre-existing `validatePlugins` red cleared (`@DisableCachingByDefault` on 12 plugin task classes). Branch `chore/unify-version-catalog`, commits `365dd19d`, `11ed1541`. |

## Session Continuity

Last session: 2026-06-14T18:34:29.829Z
Stopped at: Phase 22 context gathered

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
