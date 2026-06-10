---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 17
subsystem: testing
tags: [uat, emulator, screenshot, metasprites, game-boy, dmg, step-agent]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    provides: metasprites.gb ROM (Plan 15 clean build + Plan 16 palette slot fix)
provides:
  - MetaspriteUatTest.kt with behavior1 + behavior2 @Test methods
  - behavior1-animation-advance.png screenshot (DMG, _idx==2 climax frame)
  - behavior2-flip-cycle.png screenshot (DMG, _rot==2 Flip-XY climax frame)
  - 10-UAT.md behaviors 1+2 rows populated (result: pass)
  - oracle-comparison.md Signal 3 UAT row partially filled (behaviors 1+2 PASS)
affects: [10-18-UAT-GBC, D-01 behaviors 1+2 satisfied]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Release frame between edge-triggered presses: button_pressed() detects rising edges via joypad & mask & ~joypad_prev; consecutive step() calls with same button held register only one press; need step(emptySet()) between presses"
    - "newAgent()/newGbcAgent()/captureAndRename() helper pattern: plan-18 adds behavior 3 by calling newGbcAgent() without restructuring the file"

key-files:
  created:
    - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.json
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.json
  modified:
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md

key-decisions:
  - "Release frames required between edge-triggered B/A presses in JVM-tier UAT tests; button_pressed() rising-edge detection requires joypad state change"
  - "newGbcAgent() stub scaffolded in same file so Plan 18 adds behavior 3 without restructuring MetaspriteUatTest.kt"
  - "Screenshot captures taken at climax frame per Visual Evidence Rule — _idx==2 for behavior 1, _rot==2 (Flip-XY) for behavior 2"
  - "Behavior 3 (GBC sub-palette) deliberately left PENDING — Plan 18 owns it; DMG screenshots insufficient for sub-palette evidence per CLAUDE.md"

patterns-established:
  - "Edge-detection gap pattern: always interpose step(emptySet()) between consecutive button_pressed() presses in UAT tests for this game"

requirements-completed: []

# Metrics
duration: 9min
completed: 2026-05-18
---

# Phase 10 Plan 17: UAT DMG — Behaviors 1+2 Summary

**D-01 behaviors 1+2 verified in DMG mode: B-press advances _idx (animation frame), A-press cycles _rot (flip states), with PNG screenshots as binding visual evidence per Visual Evidence Rule**

## Performance

- **Duration:** 9 min
- **Started:** 2026-05-18T17:45:00Z
- **Completed:** 2026-05-18T17:53:48Z
- **Tasks:** 1
- **Files modified:** 7

## Accomplishments

- `MetaspriteUatTest.kt` created with two `@Test` methods (behavior1, behavior2) — both GREEN
- Behavior 1: `_idx` advances 0→1→2 on B presses (with release frames for edge detection); elephant tile arrangement visibly changes between frames
- Behavior 2: `_rot` cycles 0→1→2→3→4 on A presses; Flip-XY at rot=2 is the most visually distinctive state, confirmed in screenshot
- Two PNG screenshots committed as binding visual evidence per CLAUDE.md Visual Evidence Rule
- `10-UAT.md` Summary updated: passed:2, issues:0, pending:1
- `oracle-comparison.md` Signal 3: PARTIAL (DMG behaviors 1+2 PASS; behavior 3 PENDING Plan 18)

## Task Commits

1. **Task 1: MetaspriteUatTest.kt + behaviors 1+2 + screenshots + partial UAT.md fill** - `c0c901d5` (feat)

## Files Created/Modified

- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` — Two @Test methods, newAgent/newGbcAgent/captureAndRename helpers, scaffolded for Plan 18
- `.planning/.../evidence/uat-screenshots/behavior1-animation-advance.png` — Behavior 1 climax screenshot (DMG, _idx==2)
- `.planning/.../evidence/uat-screenshots/behavior2-flip-cycle.png` — Behavior 2 climax screenshot (DMG, _rot==2 Flip-XY)
- `.planning/.../10-UAT.md` — Behaviors 1+2 result→pass, actual+evidence+plan fields filled; Summary updated to passed:2
- `.planning/.../evidence/oracle-comparison.md` — Signal 3 UAT row updated: behaviors 1+2 PASS verdicts

## Decisions Made

- Release frames (step with emptySet()) are required between successive edge-triggered button presses. The generated C uses `button_pressed() = joypad & mask & ~(joypad_prev & mask)` — a rising-edge check. Pressing B on frame N and B again on frame N+1 both see B held, not a new rising edge. The UAT mcp_script in 10-UAT.md was updated to document this requirement.
- Screenshot captured at the climax frame (after the most presses), not mid-sequence, to maximize visual distinction from the boot frame — this is the binding evidence artifact per CLAUDE.md §"Verification Methodology".

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Release frames required between consecutive edge-triggered button presses**
- **Found during:** Task 1 (first test run)
- **Issue:** Initial test had two consecutive `agent.step(setOf(Button.B))` calls expecting _idx to advance twice. After first press _idx==1 (correct), after second press _idx==1 (not 2). The game's `button_pressed()` uses `joypad & mask & ~(joypad_prev & mask)` — two consecutive frames with B held only fire one rising edge.
- **Fix:** Added `agent.step(emptySet())` release frames between all consecutive edge-triggered button presses in both test methods. Updated mcp_script in 10-UAT.md to document the release frame requirement.
- **Files modified:** MetaspriteUatTest.kt, 10-UAT.md
- **Verification:** Both tests pass GREEN with release frames
- **Committed in:** c0c901d5 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug in test script from mcp_script literalization)
**Impact on plan:** Necessary correctness fix to the test script. The game behavior is correct; the mcp_script assumed consecutive step() calls would each register a press. The fix documents the release-frame requirement explicitly in the test and mcp_script.

## Issues Encountered

- ROM was not pre-built in the worktree (only in the main checkout). Built with `./gradlew :gbkt-examples:metasprites:buildRom` before running tests. ROM built cleanly.

## Known Stubs

- `newGbcAgent()` in `MetaspriteUatTest.kt` is a scaffolded helper body (creates a GBC-mode agent). It is not called by any test in this plan — it is an intentional scaffold for Plan 18 to add behavior 3 without restructuring the file. Plan 18 will implement the `behavior3_a_press_cycles_sub_palettes_gbc()` test using this helper.

## Next Phase Readiness

- Plan 18 (UAT-GBC) can add `behavior3_a_press_cycles_sub_palettes_gbc()` to `MetaspriteUatTest.kt` by calling `newGbcAgent()` — no restructuring needed
- `10-UAT.md` behavior 3 row is PENDING — Plan 18 fills it
- `oracle-comparison.md` Signal 3 is PARTIAL — Plan 18 completes it

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
