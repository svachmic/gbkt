# Anchor 4 BLOCKED — `_walkFrameIdx` Never Incremented (PlatformerVisitor Gap #4)

**Date:** 2026-05-23
**Blocking plan:** 12-22
**Routing:** Escalated to Phase 12.3 (PlatformerVisitor auto-emission wiring)

## Symptom

`PlatformerTemplateUatTest.anchor4MetaspriteAnimation` fails on the load-bearing assertion:

```
Expected walkFrameIdx to take >= 2 distinct values across 3 captures (cycling);
got values [0, 0, 0] (1 distinct)
```

Captured variable trace:
```
walkFrameIdx_at_01: 0  (after holding RIGHT for 6 frames)
walkFrameIdx_at_02: 0  (after holding RIGHT for another 6 frames)
walkFrameIdx_at_03: 0  (after holding RIGHT for another 6 frames)
facingRot_at_04: 3     (after holding LEFT — this PART works, hflip codegen GREEN)
```

## Root Cause

`_walkFrameIdx` is **declared** in the DSL (`PlatformerTemplate.kt:118`) and **read** by
the codegen-emitted metasprite render switch (`bank1.c:65,70,75,80` — `sprite_player_frames[_walkFrameIdx]`),
but **nothing increments it anywhere**. Same pattern as the 3 prior PlatformerVisitor
auto-emission gaps surfaced in Plan 12-21:

1. Input → `_playerVx` wiring missing (inline-fixed in 12-21)
2. `platformer_camera_update()` defined but never called (inline-fixed in 12-21)
3. Metasprite render uses world _playerX without camera offset (inline-fixed in 12-21)
4. **Walk-frame index cycle (`_walkFrameIdx`) never advances — THIS GAP**

The DSL comments at `PlatformerTemplate.kt:109-113` describe the expected cycle:
"walkFrameIdx: cycles 0..2 (walk1 / walk2 / walk3) and switches to 3 (idle) when not
walking; threeFrameCounter: counts up to 3 → resets walkFrameIdx, mirroring the
reference's threeFrameCounter walking-cadence pattern (player.c)." — but no code
implements the cycle.

## Why Escalate (Not Inline-Fix #4)

Per the session decision and memory rule `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`:
**3 inline framework fixes in one wave is the soft cap; the 4th triggers escalation**.

Phase 12.3 should wire all 4 PlatformerVisitor auto-emissions properly so the user
DSL doesn't need any inline-cEmit workarounds. See
`SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` for the comprehensive
follow-up catalog.

## State at Time of Escalation

- **Test code:** `PlatformerTemplateUatTest.anchor4MetaspriteAnimation` (full implementation
  landed; correct on its own but blocks on the framework gap).
- **Screenshots:** 4 PNGs captured but show the player **never cycling animation frames**.
  These are PRE-fix evidence; will be re-shot after 12.3 ships.
- **JVM test:** RED (fails on walkFrameIdx cycle assertion).
- **facingRot:** WORKS — confirms D-04 hflip codegen is correct (case 3 → flipx
  emission), so anchor 4's hflip portion is provably GREEN. Only the
  frame-cycle portion is blocked.

## Resume Path After Phase 12.3 Ships

1. Rebuild ROM with PlatformerVisitor auto-emission corrected
2. Remove the 3 inline cEmit fixes from `PlatformerTemplate.kt:402-453` (12-21's
   workarounds become redundant)
3. Re-run `:gbkt-examples:platformer-template:test --tests PlatformerTemplateUatTest.anchor4MetaspriteAnimation`
4. Re-shoot the 4 anchor-4 screenshots
5. Pause for human-verify gate (Plan 12-22 Task 2)
6. Close Plan 12-22, advance to Wave 14 (Plan 12-23 Anchor 5)
7. Also re-shoot Anchors 1, 2, 3 (12-19/20/21) since metasprite-camera-offset
   correction will change visual fidelity

## Related Artifacts

- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` (anchor4 test implementation — kept)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-4/` (pre-fix screenshots, will be re-shot)
- `.planning/seeds/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` (catalogs all 4 gaps + the 3 prior inline fixes)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` (target of Phase 12.3 fixes)
- Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c` (canonical walkFrameIdx cycle pattern around line 130-145)
