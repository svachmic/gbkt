---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 21
subsystem: testing
tags: [uat, mcp-emulator, anchor-3, horizontal-scroll, column-scroll, camera, kotlin]

requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-19)
    provides: Anchor 1 GREEN — gameplay scene reachable + tilemap rendered
  - phase: 12 (Plan 12-11)
    provides: column-scroll codegen + camera_update() function definition (call wiring landed inline here)
provides:
  - Anchor 3 (horizontal scroll) GREEN — JVM test + 2 binding screenshots + human-verify approval
  - 3 inline user-DSL fixes to PlatformerTemplate.kt papering over PlatformerVisitor auto-emission gaps
  - SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS — framework follow-up
affects: [phase-12 anchor-4..5, phase-12-final-verifier, future-PlatformerVisitor-cleanup-phase]

tech-stack:
  added: []
  patterns:
    - "cEmit-fudge for camera-relative metasprite render: temporarily subtract camera_x from worldX before render, restore after"
    - "Hardware-global memory read via StepAgent.readMemory for codegen internals not in metadata (e.g. _camera_x at 0xC0DC)"

key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-3/01-initial.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-3/02-scrolled.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-3/anchor3-variables.txt
    - .planning/seeds/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md
  modified:
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt (anchor3 SKIP stub → real impl)
    - gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt (3 inline cEmit fixes + dpad→playerVx clauses + un-Suppress playerVx)

key-decisions:
  - "Variable read: _camera_x (0xC0DC, UINT16) and _map_pos_x (0xC0E0, UINT8) are HOME-bank globals NOT in game_metadata.json. Used StepAgent.readMemory() for direct address reads, resolved from platformer-template.noi sym file."
  - "3 inline-fix accumulation triggered SEED creation per memory rule feedback_route_to_proper_phase_when_blast_radius_is_wide.md. Stayed inline (D-01 lifted cap) but loaded the framework-fix to a SEED for follow-up; if Wave 13 surfaces a 4th wiring gap, that's the trigger to escalate to a Phase 12.3."
  - "Plan stays GREEN on its load-bearing truths (camera_x > 0, map_pos_x > 0, visible bg diff) despite the underlying framework debt — the runtime path IS proven to work, just not through clean auto-emission."

patterns-established:
  - "Inline-fix accumulation budget: 3 framework gaps in one plan is the soft cap for absorbing inline; the next gap should trigger escalation per blast-radius rule."
  - "cEmit-fudge for transient state mutation around a render call: save → mutate → render → restore. Useful when proper codegen would require multi-day visitor rework."

requirements-completed:
  - D-08
  - D-10
  - D-13
  - D-overfitting-1
  - D-overfitting-3

duration: ~40 min (3 iterations: surface gap → inline-fix → test → human-verify → next gap)
completed: 2026-05-23
---

# Plan 12-21: UAT Anchor 3 — Horizontal Scroll

**MCP-driven anchor 3 GREEN end-to-end: `_camera_x` advanced 0→64, `_map_pos_x` advanced 0→8, bg tilemap content visibly differs between 01-initial and 02-scrolled, player stays on-screen. THREE PlatformerVisitor auto-emission gaps surfaced and inline-fixed per D-01 lifted cap; framework follow-up captured as SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.**

## Performance

- **Duration:** ~40 min (slow due to 3 gap-discovery iterations)
- **Started:** 2026-05-23T12:15Z
- **Completed:** 2026-05-23T13:05Z
- **Tasks:** 2 (Task 1 auto + Task 2 human-verify gate, iterated 2x)
- **Files modified:** 2 source (test + DSL) + 3 evidence artifacts + 1 SEED

## Accomplishments

- Replaced anchor3HorizontalScroll SKIP stub with full MCP-driven scroll test (camera_x + map_pos_x via raw memory read, PNG byte-diff structural check)
- Captured 2 binding screenshots showing visibly different bg content between 01 and 02
- Surfaced + inline-fixed 3 PlatformerVisitor auto-emission gaps:
  1. Input → `_playerVx` wiring missing (added `whenever(dpad.*.held) { playerVx set ±127 }`)
  2. `platformer_camera_update()` defined but never called (added `cEmit("platformer_camera_update();")`)
  3. Metasprite render at world position, not screen-relative (added save/restore cEmit-fudge around moveMetasprite)
- Created SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS catalog for framework follow-up

## Task Commits

Each iteration committed atomically (planned: 1 commit, actual: bundled into one with detailed body):

1. **Task 1: anchor3 test + 3 inline DSL fixes** — included in this commit
2. **Task 2: Human-verify gate** — APPROVED on the 2nd iteration (after the 3rd inline fix landed)

## Files Created/Modified

- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — anchor3 SKIP stub replaced with raw-memory-read scroll test
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` — 3 inline cEmit fixes + dpad→playerVx clauses; removed `@Suppress("UNUSED_VARIABLE")` from `playerVx` (now actively used)
- `.planning/phases/.../evidence/uat-screenshots/anchor-3/01-initial.png` — pre-scroll frame at gameplay entry
- `.planning/phases/.../evidence/uat-screenshots/anchor-3/02-scrolled.png` — post-hold-right, visibly different bg content
- `.planning/phases/.../evidence/uat-screenshots/anchor-3/anchor3-variables.txt` — camera_x + map_pos_x trace
- `.planning/seeds/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` — comprehensive framework follow-up catalog

## Decisions Made

- **Variable address resolution:** `_camera_x` and `_map_pos_x` are codegen-internal HOME-bank globals not exposed in `game_metadata.json`. Resolved addresses from the .noi sym file (0xC0DC and 0xC0E0) and used `StepAgent.readMemory()` for direct UINT16/UINT8 reads. Established this as a pattern for future codegen-internal variable verification.
- **3 inline fixes vs escalate:** Per the memory rule on blast radius, 3 framework gaps in one plan is at the soft cap for inline absorption. Stayed inline (D-01 lifted cap permits it) but proactively captured a comprehensive SEED so the framework debt is visible. If Wave 13 surfaces a 4th gap, that's the escalation trigger.
- **walkSpeed = 127 (INT8 max), not 128:** `playerVx` is `i8Var` (signed 8-bit, range -128..127). Capped at 127. Reference GBDK uses INT16 with `moveSpeed = 0x80 = 128`; widening would require changing `playerVx by i8Var(0)` → `i16Var(0)` and adjusting integration math.
- **cEmit-fudge for camera-relative render:** Rather than re-emit the entire metasprite render block manually, temporarily mutate `_playerX` before render and restore after. Minimal code volume, preserves the facingRot switch logic from MetaspriteVisitor.

## Deviations from Plan

3 inline auto-fix deviations, all in PlatformerTemplate.kt:

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added dpad → playerVx clauses (input never reached velocity)**
- **Found during:** Task 1 first test run (`_camera_x` stayed at 0 after holding RIGHT 150 frames)
- **Issue:** PlatformerVisitor's auto-emitted gameplay_frame only set `_facingRot` on dpad input; never set `_playerVx`
- **Fix:** Added `whenever(dpad.right.held) { playerVx set 127 }` etc. in gameplay scene's `frame { }` block
- **Files modified:** PlatformerTemplate.kt:402-422
- **Verification:** Second test run showed `_camera_x = 37` (proves player moved)
- **Committed in:** this commit

**2. [Rule 1 - Bug] Added cEmit("platformer_camera_update();")** (function defined but never called)
- **Found during:** Task 1 second test run (camera_x advanced but map_pos_x stayed at 0)
- **Issue:** `platformer_camera_update()` body is emitted by PlatformerVisitor at main.c:437 but no call site exists
- **Fix:** Added `cEmit("platformer_camera_update();")` after input clauses in gameplay frame
- **Files modified:** PlatformerTemplate.kt:425-433
- **Verification:** Third test run showed `_map_pos_x = 8` (64>>3) and PNG byte-diff is non-zero
- **Committed in:** this commit

**3. [Rule 1 - Bug] cEmit-fudge for camera-relative metasprite render**
- **Found during:** Task 2 first human-verify iteration (player drifted off-screen as bg scrolled)
- **Issue:** `move_metasprite_ex(...)` rendered at absolute `_playerX >> 4` instead of screen-relative `(_playerX >> 4) - _camera_x`
- **Fix:** Save/restore cEmit around `moveMetasprite(player)` to temporarily shift `_playerX` to screen-relative coordinates for the render call
- **Files modified:** PlatformerTemplate.kt:435-453
- **Verification:** Re-shoot APPROVED — player stays on-screen, bg scrolls correctly
- **Committed in:** this commit

---

**Total deviations:** 3 auto-fixed (all Rule 1 - Bug, all framework auto-emission gaps in PlatformerVisitor)
**Impact on plan:** All 3 fixes essential for anchor 3 to pass its load-bearing truths. SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS holds the framework cleanup for follow-up; no code is lost — only one inline-fix layer needs unwinding later.

## Issues Encountered

3 PlatformerVisitor auto-emission gaps surfaced in one plan (input→velocity, camera_update call, metasprite camera offset). Each fixed inline per D-01 lifted cap. User-approved approach per session decision; SEED loaded for framework cleanup.

## User Setup Required

None.

## Next Phase Readiness

- Anchor 3 GREEN unblocks Wave 13 continuation: Plan 12-22 (anchor 4 metasprite animation).
- Established pattern for raw-memory reads of codegen-internal HOME-bank globals via `StepAgent.readMemory(addr)` from .noi-resolved addresses.
- Inline-fix budget consumed at 3/wave; if 12-22 or 12-23 surface more PlatformerVisitor gaps, escalate to a new Phase 12.3 per memory rule.

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 21*
*Completed: 2026-05-23*
