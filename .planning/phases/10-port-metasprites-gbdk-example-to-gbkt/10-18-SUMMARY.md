---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 18
subsystem: testing
tags: [uat, emulator, screenshot, metasprites, game-boy-color, gbc, step-agent, sub-palette]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    provides: metasprites.gb ROM (Plan 15 clean build + Plan 16 palette slot fix)
  - plan: 10-17
    provides: MetaspriteUatTest.kt with behavior1 + behavior2 @Test methods and newGbcAgent() stub
provides:
  - MetaspriteUatTest.kt with behavior3 @Test method appended (GBC sub-palette cycling)
  - behavior3-subpalette-cycle-gbc.png screenshot (GBC mode, _rot==8, cyan sprite visible)
  - 10-UAT.md behavior 3 row populated (result: pass-partial), D-V3/D-V4 defects added
  - oracle-comparison.md Signal 3 UAT verdict fully finalized (all three behaviors)
  - CoffeeGbEmulator.kt: GbcFrameReadyEvent now wired (Rule 1 bug fix)
affects: [Phase 10.1 — D-V1/D-V2/D-V3 seeded for follow-up, UAT contract complete]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "GBC frame event registration: Coffee-GB fires DmgFrameReadyEvent in DMG mode and GbcFrameReadyEvent in CGB mode — both must be registered in CoffeeGbEmulator to get non-black GBC screenshots"
    - "GBC boot frame count: GBC mode needs more boot frames (~30) than DMG (~10) before LCD shows content"
    - "elephant_subPalette global stale: moveMetasprite uses local `subpal = _rot >> 2` for OAM but does not write back to _elephant_subPalette global — sym-file reads of _elephant_subPalette always return 0 regardless of rot"

key-files:
  created:
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.json
  modified:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt
    - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md

key-decisions:
  - "GbcFrameReadyEvent must be wired in CoffeeGbEmulator for GBC screenshots to work; only DmgFrameReadyEvent was previously registered — a pre-existing bug exposed by this plan"
  - "D-08 mechanism (subpal = rot >> 2 passed to move_metasprite_ex) is confirmed correct by generated C inspection; _elephant_subPalette global variable is a stale tracker — never assigned in play_frame()"
  - "Behavior 3 verdict: pass-partial — mechanism PASS, GBC visual PASS (cyan sprite), visual defects D-V1/D-V2 persist, D-V3 newly surfaced"
  - "Screenshot climax frame: rot=8 (subpal=2 cyan) with 30 boot frames + 8 A presses + 2 PPU flush frames"

patterns-established:
  - "Always register both DmgFrameReadyEvent and GbcFrameReadyEvent in CoffeeGbEmulator for ROM tests that target GBC mode"

requirements-completed: []

# Metrics
duration: 33min
completed: 2026-05-18
---

# Phase 10 Plan 18: UAT GBC — Behavior 3 Sub-Palette Cycling Summary

**GBC sub-palette cycling confirmed: cyan sprite visible at rot=8 in GBC-mode screenshot. Rule 1 bug fixed: CoffeeGbEmulator now registers GbcFrameReadyEvent to produce non-black GBC screenshots.**

## Performance

- **Duration:** 33 min
- **Started:** 2026-05-18T17:45:00Z
- **Completed:** 2026-05-18T18:18:25Z
- **Tasks:** 1
- **Files modified:** 8

## Accomplishments

- `MetaspriteUatTest.kt` extended with `behavior3 a press cycles sub palettes in gbc mode` @Test method — now has three @Test methods (all GREEN)
- Behavior 3: `_rot` reaches 8 after 8 A presses (with release frames); GBC-mode screenshot shows cyan elephant sprite (sub-palette 2) — visually distinct from behaviors 1+2 DMG green-tinted screenshots
- `CoffeeGbEmulator.kt` fixed: `GbcFrameReadyEvent` listener registered alongside existing `DmgFrameReadyEvent`; GBC screenshots now produce actual game content (not all-black)
- `behavior3-subpalette-cycle-gbc.png` committed as binding visual evidence per CLAUDE.md Visual Evidence Rule
- `10-UAT.md` Summary updated: `passed:3-partial, issues:3, pending:0`; behavior 3 row fully filled; D-V3 (stale elephant_subPalette) and D-V4 (CoffeeGbEmulator GBC fix) documented
- `oracle-comparison.md` Signal 3 fully finalized: all three behaviors PASS in UAT verdict table

## Task Commits

1. **Task 1: behavior3 GBC UAT + CoffeeGbEmulator fix + screenshots + final UAT.md + oracle fill** - `6fe5222b` (feat)

## Files Created/Modified

- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt` — Register GbcFrameReadyEvent listener (Rule 1 fix); GBC-mode ROMs now produce visible screenshots
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` — behavior3 @Test method appended; 30-frame GBC boot, 8 A presses with release frames, 2 PPU flush frames, screenshot at rot=8
- `.planning/.../evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png` — Behavior 3 climax screenshot (GBC mode, rot=8, cyan elephant sprite)
- `.planning/.../evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.json` — Sidecar JSON (rot:8, elephant_subPalette:0 — stale global, not a mechanism defect)
- `.planning/.../10-UAT.md` — Behavior 3 result→pass-partial, actual+evidence+plan fields filled; D-V3/D-V4 defect entries added; Summary updated to passed:3-partial/pending:0
- `.planning/.../evidence/oracle-comparison.md` — All three behavior verdicts populated; phase verdict updated to three-of-three complete

## Decisions Made

- The `CoffeeGbEmulator` registered only `DmgFrameReadyEvent` at the `EventBus`. In CGB mode (`GameboyType.CGB`), Coffee-GB fires `Display.GbcFrameReadyEvent` instead — so the frame buffer was never updated. The fix adds a second `eventBus.register` for `GbcFrameReadyEvent` using `event.toRgb(buffer)` (no grayscale flag needed — GBC emits native RGB).
- GBC mode requires ~30 boot frames (vs ~10 for DMG) before the LCD shows visible content. The test uses `stepN(30)` to ensure the LCD is initialized before button presses.
- `_elephant_subPalette` global variable (emitted by codegen) is never assigned in `play_frame()`. The local variable `uint8_t subpal = _rot >> 2` is computed and passed to `move_metasprite_ex()` correctly, but the global tracker is stale. This is D-V3 — a debug variable correctness gap, not a mechanism failure. The mechanism (D-08) is confirmed working.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CoffeeGbEmulator did not register GbcFrameReadyEvent**
- **Found during:** Task 1 (first GBC screenshot was all-black, 147 bytes)
- **Issue:** In CGB mode, Coffee-GB fires `Display.GbcFrameReadyEvent` not `DmgFrameReadyEvent`. The `CoffeeGbEmulator` only registered a listener for `DmgFrameReadyEvent`, so the frame buffer stayed at the initial all-zero (black) state indefinitely in GBC mode.
- **Fix:** Added `eventBus.register { event -> event.toRgb(internalFrameBuffer); synchronized(frameBufferLock) { publicFrameBuffer = internalFrameBuffer.copyOf() } }, Display.GbcFrameReadyEvent::class.java` in `CoffeeGbEmulator.kt`.
- **Files modified:** `CoffeeGbEmulator.kt`
- **Verification:** behavior3 screenshot now shows cyan elephant sprite (visually distinct from DMG)
- **Committed in:** `6fe5222b` (Task 1 commit)

**2. [Rule 1 - Bug] GBC boot requires more frames than specified in mcp_script**
- **Found during:** Task 1 (even with GbcFrameReadyEvent fix, screenshot was still black at frame 26)
- **Issue:** The `mcp_script` specified `emulator_step(frames=10)` for boot. In GBC mode, the CGB LCD initialization sequence takes more frames to produce visible content. At frame 26 (10 boot + 16 presses), the LCD was still rendering a black frame.
- **Fix:** Changed `stepN(10)` to `stepN(30)` in behavior3 test. Added 2 PPU flush frames after the last press before screenshot.
- **Files modified:** `MetaspriteUatTest.kt`
- **Committed in:** `6fe5222b` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (Rule 1 bugs)
**Impact on plan:** Both were blocking issues for GBC visual evidence. The fixes are correct and bounded to the test and emulator infrastructure.

## Additional Defects Surfaced

### D-V3: _elephant_subPalette global never written in play_frame()
- **Status:** Open, seeded for Phase 10.1
- **Finding:** `_elephant_subPalette` global is declared in generated C but the `play_frame()` uses a local `uint8_t subpal = _rot >> 2;` passed to `move_metasprite_ex()`. The global is never assigned. Sym-file reads of `_elephant_subPalette` always return 0.
- **Impact:** Mechanism is correct (OAM gets correct subpal); only the debug variable is stale.

### D-V4: CoffeeGbEmulator GbcFrameReadyEvent gap (FIXED in this plan)
- **Status:** Fixed (Rule 1 auto-fix)
- **Context:** This was a pre-existing bug in `gbkt-emulator` that was only exposed when Plan 18 first attempted a GBC screenshot. Fixed in `6fe5222b`.

## Known Stubs

None — all three behaviors have been executed and documented. The behavior 3 `newGbcAgent()` helper is no longer a stub; it is called by the behavior3 test.

## Threat Flags

None — this plan adds a test method and fixes an emulator infrastructure bug. No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries.

## Self-Check

- [ ] `behavior3-subpalette-cycle-gbc.png` exists: YES — 547 bytes (cyan sprite visible)
- [ ] `behavior3-subpalette-cycle-gbc.json` exists: YES — rot:8 confirmed
- [ ] `MetaspriteUatTest.kt` has 3 @Test methods: YES (behavior1, behavior2, behavior3)
- [ ] `MetaspriteUatTest.kt` contains `gbcMode = true`: YES (in newGbcAgent())
- [ ] `10-UAT.md` Summary: `passed:3-partial, pending:0`: YES
- [ ] `oracle-comparison.md` behavior 3 verdict: PASS (not PENDING): YES
- [ ] All 3 tests GREEN: YES — XML shows tests:3, failures:0, skipped:0
- [ ] Task 1 commit `6fe5222b` exists: YES

## Self-Check: PASSED

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
