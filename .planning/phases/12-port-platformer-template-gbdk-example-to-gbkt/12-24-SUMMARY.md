---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 24
subsystem: testing
tags: [oracle-comparison, three-signal, bank-layout, noi-parse, rom-size, generated-c-diff, uat-verdict, verifier-input]

requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-19..12-23)
    provides: 5 UAT anchor SUMMARYs that this artifact aggregates into the Signal 3 verdict table
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-02)
    provides: evidence/reference/BUILD.md (reference ROM build recipe + reproducibility checksum table)
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-18)
    provides: first-buildrom.md (initial ROM build evidence)
provides:
  - evidence/oracle-comparison.md — 3-signal verifier input (ROM size, generated-C diff, 5-anchor UAT verdict)
  - evidence/bank-layout-signal.md — 4th-signal verifier input (.noi parse + 16 384-cap check + cross-bank navigation implicit signal)
  - Honest verdict surface: Signals 1 + 2 GREEN; Signal 3 RED (anchor 5 JVM-tier GREEN + visual-RED → Phase 12.6); 4th signal GREEN
  - Verifier handoff contract: Phase 12 closure is gated on Phase 12.6 ship + anchor 5 retro-GREEN re-shoot
affects: [phase-12-final-verifier, phase-12.6-main-loop-level-switch-codegen-fix, phase-13-framework-primitives]

tech-stack:
  added: []
  patterns:
    - "3-signal oracle artifact shape (Phase 9/10/11 → 12 carry-forward): one markdown file with Signal 1/2/3 sections + Three-Signal Overall Verdict, each row citing reproducible shell measurement + the closing plan SUMMARY"
    - "4th-signal bank-layout artifact shape (Phase 11 D-15 → 12 D-17 carry-forward): one markdown file parsing .noi `DEF l__CODE_<N>` + per-bank content-category table sourced from .map symbol prefixes + `[ bank<N> ]` placement annotations"
    - "Honest verdict recording when a phase has a visual-RED anchor routed to a follow-up sub-phase: explicitly mark the anchor JVM-tier GREEN + visual-RED, cite the retro-close precedent (Plan 12-22's 12.3+12.5 pattern), and lock the RED baseline screenshots for the follow-up sub-phase"

key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/oracle-comparison.md
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/bank-layout-signal.md
  modified: []

key-decisions:
  - "Signal 1 verdict = GREEN (boundary) at ratio 2.000 exactly. The ROADMAP three-signal contract specifies `≤ 2×`; the measurement lands AT the ceiling. Documented the boundary status honestly rather than rounding to a softer GREEN — the ROM-size drivers (tileset duplication + MBC1 4-bank rounding) are surfaced as future-polish seeds, not regressions."
  - "Signal 2 verdict = GREEN (informational) with 3 seeded regressions. Overall gbkt C surface is ~35% shorter than reference (940 vs 1 450 LOC visible *.c). Three localized surfaces where gbkt is NOT shorter/clearer are seeded: (a) camera-relative metasprite render cEmit-fudge → existing SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS, (b) platformer_camera_update call-site gap → same SEED, (c) tileset deduplication (no `-source_tileset` exploitation) → new seed candidate (NOT filed in this plan — referenced in oracle-comparison.md as future-polish opportunity)."
  - "Signal 3 verdict = RED, honestly. Anchor 5 is JVM-tier GREEN + visual-RED per Plan 12-23's OPTION A close. The 3 round-2 PNGs are explicitly named as the Phase 12.6 RED baseline; the retro-close precedent (Plan 12-22 via Phase 12.3 + 12.5) is cited so the verifier understands the closure path."
  - "Overall verdict = NOT-YET-GREEN. Phase 12 closure is GATED on (1) Phase 12.6 ship and (2) follow-up anchor-5 retro-GREEN re-shoot. This contract is written into oracle-comparison.md's `## Three-Signal Overall Verdict` section so the verifier reads the gate without cross-referencing this SUMMARY."
  - "Bank-layout signal verdict = GREEN. All 3 numbered CODE banks within 16 384 cap (max 37.4%). Cross-bank navigation implicit signal GREEN — anchor 5's defects are HOME-bank main-loop ordering bugs, NOT bank-switching mechanism failures (proven by absence of MBC errors + by 03-level-2.png correctly showing world2-area1's rocky tileset, confirming the cross-bank load itself worked — only the destination level was wrong, which is DEFECT-2)."
  - "Reference ROM and reference C tree both read from `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/` per the BUILD.md path convention. The reference `.gb` (32 768 B) + `.noi` were already built 2026-05-21 (per BUILD.md sha256 capture table) — no rebuild needed for this plan."
  - "Build-artifact paths use the main checkout (`/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/...`) rather than the worktree's path, because gradle build outputs live outside `.planning/` and aren't carried into worktree resets. Measurements are reproducible from any path — the artifact docs all show the absolute paths used."

patterns-established:
  - "When a phase's UAT verdict has a visual-RED anchor routed to a follow-up sub-phase, the oracle-comparison.md MUST: (1) explicitly mark the anchor as JVM-tier GREEN + visual-RED in the verdict table, (2) cite the closing plan + the user-decision route (OPTION A/B/C), (3) lock the RED baseline screenshot paths, and (4) write the retro-GREEN gate into the Three-Signal Overall Verdict section. Pattern is established by Plan 12-24 referencing Plan 12-23's OPTION A close and Plan 12-22's Phase 12.3+12.5 retro-close precedent."
  - "Bank content categories MUST be sourced from BOTH the .noi (`DEF l__CODE_<N>`) AND the .map (`[ bank<N> ]` symbol-placement annotations + per-section `_CODE_<N>` Area lines). Listing only the .noi byte sizes without categorizing the symbols leaves a verifier guessing which bank holds zone data vs scene callbacks. Established here by the per-bank table in bank-layout-signal.md cross-referencing .map annotations."

requirements-completed:
  - D-17    # 3-signal artifact + bank-layout signal
  - D-17a   # Artifact location (evidence/{oracle-comparison,bank-layout-signal}.md)

duration: ~30 min
completed: 2026-05-25
---

# Plan 12-24: Three-Signal Oracle Artifact + Bank-Layout Signal

**Verifier handoff complete: oracle-comparison.md aggregates ROM size (65 536 vs 32 768 B → ratio 2.000, boundary GREEN), generated-C diff (gbkt 940 LOC vs reference 1 450 LOC, ~35% shorter), and 5-anchor UAT verdict (anchors 1–4 GREEN; anchor 5 JVM-tier GREEN + visual-RED → Phase 12.6 RED baseline locked). bank-layout-signal.md confirms all 3 numbered CODE banks within the 16 384 cap (max 37.4% utilization) and implicit cross-bank navigation GREEN (no MBC errors across 5 anchor runs). Phase 12 closure gated on Phase 12.6 ship + anchor-5 retro-GREEN re-shoot — gate written into oracle-comparison.md's Three-Signal Overall Verdict section.**

## Performance

- **Duration:** ~30 min (read 5 anchor SUMMARYs + CONTEXT D-17 + BUILD.md + parse .noi/.map for both ROMs + author 2 artifacts + 2 atomic commits)
- **Started:** 2026-05-25T~07:00Z
- **Completed:** 2026-05-25T~07:30Z
- **Tasks:** 2 of 2 completed (both auto, no checkpoints — autonomous: true)
- **Files modified:** 2 artifact files created; 0 source-tree changes

## Accomplishments

- Authored `evidence/oracle-comparison.md` with all 3 signals + Three-Signal Overall Verdict section.
  - Signal 1: ROM-size table + reproducible `stat -f '%z'` snippet + boundary-GREEN verdict (ratio 2.000 exact) + 2× delta drivers documented.
  - Signal 2: gbkt 4-file generated C tree (main.c 593 + bank1.c 78 + zone_bank2.c 4 + sprites/player.c 265 = 940 LOC) vs reference 13-file tree (src/*.c 750 + gen/gb/src/*.c 700 = 1 450 LOC) + side-by-side function-cluster mapping + "where gbkt is NOT shorter" seed-trigger table.
  - Signal 3: per-anchor 5-row verdict table citing each closing plan + commit hashes + human-verify approval dates. Anchor 5 explicitly recorded JVM-tier GREEN + visual-RED with both CODEGEN-DEFECT-1 + DEFECT-2 symptoms named and the Phase 12.6 RED baseline path locked.
  - Three-Signal Overall Verdict: NOT-YET-GREEN with explicit closure gate (Phase 12.6 ship + anchor-5 retro-GREEN re-shoot).
- Authored `evidence/bank-layout-signal.md` with .noi parse + 16 384-cap check + cross-bank navigation implicit signal.
  - Per-bank table sourced from BOTH `.noi` (`DEF l__CODE_<N>` sizes) AND `.map` (`[ bank<N> ]` symbol-placement annotations + `_CODE_<N>` Area lines) for accurate content-category attribution (HOME = sceneTable + physics + dispatch + init; CODE_1 = bank1.c BANKED scene callbacks; CODE_2 = ALL zone tileset+tilemap data).
  - 16 384-cap check: GREEN — max bank is CODE_2 at 6 120 B (37.4%); no bank near the 14 336 warning threshold.
  - Reference .noi parsed for comparison (CODE_1 = 13 148 B at 80.2%) — gbkt has substantially more per-bank headroom.
  - Cross-bank navigation implicit signal: GREEN — anchor 1 + anchor 5 both cross banks (CODE_1 ↔ HOME ↔ CODE_2) without MBC errors; anchor 5's defects are confirmed HOME-bank main-loop ordering bugs, NOT bank-switching failures.

## Task Commits

| Task | Name                                              | Commit     | Files                                                                                            |
| ---- | ------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------ |
| 1    | 3-signal oracle-comparison artifact               | `3a3a4ff0` | `.planning/phases/12-.../evidence/oracle-comparison.md`                                          |
| 2    | 4th-signal bank-layout artifact                   | `d64d2e50` | `.planning/phases/12-.../evidence/bank-layout-signal.md`                                         |

## Files Created/Modified

**Created:**
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/oracle-comparison.md` — 3-signal artifact (220 lines)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/bank-layout-signal.md` — 4th-signal artifact (197 lines)

**Modified:** none (no source-tree edits; this plan is pure documentation aggregation).

## Decisions Made

- **Boundary-GREEN on ratio 2.000 (not soft-GREEN):** ROADMAP three-signal contract specifies `≤ 2×`. The measurement is `65 536 / 32 768 = 2.000` exactly. Recorded as "GREEN (boundary)" with explicit drivers (tileset duplication, MBC1 4-bank rounding) rather than understating the boundary status. The 2× delta is structurally explained — not a verbosity regression.
- **Honest visual-RED recording for anchor 5:** Per the objective's critical context — anchor 5 is JVM-tier GREEN + visual-RED routed to Phase 12.6, NOT all 5 anchors visually GREEN. The verdict table row for anchor 5 explicitly names both CODEGEN-DEFECT-1 (NEXT LEVEL card VRAM raced by setup_current_level same-frame write) + CODEGEN-DEFECT-2 (preserved `_playerX` re-firing level-end trigger same-frame), cites Plan 12-23's OPTION A user decision (2026-05-25), and locks the 3 round-2 PNGs as the Phase 12.6 RED baseline.
- **Closure gate written into oracle-comparison.md:** the Three-Signal Overall Verdict section ends with a "Path to GREEN" enumeration (Phase 12.6 ships → retro-shoot anchor 5 → final verifier records both defects + retro-GREEN evidence). The verifier audit reads this gate directly without needing to cross-reference this SUMMARY.
- **Bank-layout content categories sourced from BOTH .noi and .map:** the `.noi` alone gives byte sizes per bank; the `.map`'s `[ bank<N> ]` symbol-placement annotations + per-section `_CODE_<N>` Area lines are required to attribute WHICH content lives in which bank (CODE_1 = bank1.c scene callbacks; CODE_2 = zone data; HOME = main.c + framework). This is the pattern the verifier expects when auditing bank-layout regressions across future ports.
- **Read main-checkout build artifacts (not worktree):** the worktree base reset to `e4ff4fc5` does NOT carry gradle build outputs (which live in `build/gbkt/output/` per the gitignore). Measurements were taken against `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/.../build/...` directly. Both artifact docs cite the absolute paths used so the verifier can reproduce against the same files.

## Deviations from Plan

None — plan executed exactly as written.

The two tasks landed atomically against the plan's `acceptance_criteria`:
- Task 1: file exists ✓, contains "Signal 1/2/3" ✓, each signal has a verdict ✓, final "Three-Signal Overall Verdict" present ✓.
- Task 2: file exists ✓, contains parsed `DEF l__CODE_` table ✓, contains "16 384" cap check ✓, contains overall verdict ✓.

The plan's `<verify><automated>` greps also pass:
```bash
test -f evidence/oracle-comparison.md && grep -c 'Signal [123]' evidence/oracle-comparison.md  # returns ≥3
test -f evidence/bank-layout-signal.md && grep -c 'DEF l__CODE_\|16384' evidence/bank-layout-signal.md  # returns 9 (≥2)
```

## Issues Encountered

None. The 5 anchor SUMMARYs + the reference BUILD.md + the on-disk .noi/.map files provided complete inputs for both artifacts in a single read pass.

## User Setup Required

None — pure documentation aggregation. The verifier reads the two artifact files plus the per-anchor SUMMARYs that they cite.

## Next Phase Readiness

- **Phase 12.6 (already inserted by orchestrator at base commit `e4ff4fc5`):** the oracle-comparison.md Signal 3 row for anchor 5 + the bank-layout-signal.md cross-bank section both name the 2 CODEGEN DEFECTS + their fix-option enumerations (per Plan 12-23 OPTION A handoff) — Phase 12.6's planner reads these directly.
- **Phase 12 final verifier:** the two artifacts are the verifier's primary inputs per D-17a. Verifier audit checklist:
  1. Read `evidence/oracle-comparison.md` → confirm Signals 1 + 2 GREEN, Signal 3 RED-with-defect-routing-to-12.6.
  2. Read `evidence/bank-layout-signal.md` → confirm 4th signal GREEN.
  3. After Phase 12.6 ships → run follow-up anchor-5 retro-GREEN plan → update Signal 3 row to GREEN (retro).
  4. Run `:gbkt-examples:platformer-template:buildRom` smoke test per D-21 (ROM-build gate) — current ROM build evidence at `evidence/first-buildrom.md` (Plan 12-18); re-verify post-12.6 ROM in the same shape.
  5. Sign off Phase 12 in PHASE-VERIFICATION.md with "Known Defects (routed to 12.6) — RESOLVED post-retro-shoot" section.

## Self-Check

- File created: `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/oracle-comparison.md`
  - Path-exists check: FOUND
- File created: `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/bank-layout-signal.md`
  - Path-exists check: FOUND
- Commit `3a3a4ff0` present in `git log` — FOUND
- Commit `d64d2e50` present in `git log` — FOUND

## Self-Check: PASSED

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 24*
*Completed: 2026-05-25*
