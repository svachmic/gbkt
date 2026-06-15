# SEED: Platformer-template spawn position appears mid-air instead of on a platform

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-platformer-template-spawn-polish](.planning/phases/16-seed-triage/TRIAGE.md#SEED-platformer-template-spawn-polish) · 2026-06-12

**Created:** 2026-05-23 (Phase 12.3 Wave 6 — Plan 12.3-13 Anchor 2 re-shoot human-verify)
**Origin phase:** 12.3-platformer-visitor-auto-emission-wiring (Plan 12.3-13)
**Source:** User UAT observation during Anchor 2 re-shoot human-verify gate
**Status:** Deferred — APPROVED with backlog capture (user explicitly asked for SEED rather than blocking 12.3-13)
**Routing:** Open; routed for a future "platformer-template spawn polish" phase per memory rule `feedback_route_to_proper_phase_when_blast_radius_is_wide`
**Blast radius:** Small (likely `gbkt-examples/platformer-template/src/main/kotlin/.../PlatformerTemplate.kt` spawn coords OR a per-zone DSL addition on `ZoneBuilder`); does NOT touch codegen visitors
**Supersedes:** [[SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY]] — same root cause, re-confirmed post-12.3-cleanup
**Pre-existing:** YES — Plan 12.3-13 evidence is byte-IDENTICAL to Phase 12 Plan 12-20 baseline `01-grounded.png`. This is NOT a Phase 12.3 regression.

## Context

Plan 12.3-13's anchor 2 (jump cycle + tilemap collision) re-shoot closed GREEN
end-to-end against the post-12.3-cleanup ROM:

- `:gbkt-examples:platformer-template:buildRom` exit 0 (1 bank, 22 WRAM bytes)
- `anchor2TilemapCollision` UAT test PASSED
- Variable trace reproduces Phase 12 baseline byte-for-byte: vy 0 → -800 → 0 over 61 frames
- `anchor2-grounded.png` MD5 = `anchor2-landed.png` MD5 = `78e22408...` (closed jump cycle)
- `anchor2-mid-jump.png` MD5 = `ac6b3eac...` (player visibly elevated)

During the human-verify gate the user APPROVED the jump cycle correctness but
noted:

> "Grounded is floating in the air, not on a platform. It also is the dead
> center of the screen."

Investigation: the "grounded" capture happens 30 frames after gameplay enter —
the player is at the **spawn position**, not on a platform. The byte-identical
grounded/landed pair proves the jump cycle returns the player to the spawn
position. The spawn position itself is mid-air and centred — caught by the
5-point foot probe on some middle-row platform that is not visually obvious in
the frame.

The 12.3-13 evidence is **byte-identical to the Phase 12 Plan 12-20 baseline**
(`.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/01-grounded.png`).
Phase 12.3's cleanup (Plans 12.3-01..11) did NOT change this — the spawn position
has been mid-air since Phase 12 first shipped the example.

The original SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY captured the same
observation at the Phase 12 close-out. This SEED supersedes it with the
post-cleanup confirmation that the issue persists and is structurally a Phase 12
example-content concern, not a 12.3 codegen concern.

## What's Deferred

The Phase 12 SEED already enumerates the options; restating with current
post-12.3 grounding:

1. **(a) Re-position the hardcoded spawn** in `PlatformerTemplate.kt:151`
   (`playerY by i16Var(72 shl 4)`) to a Y coordinate that visually lands on the
   bottom-ground row of `world1Area1` — matches the GBDK reference's per-level
   `level_start_pos` initialisation pattern.
2. **(b) Add per-zone `spawnPosition(x, y)` DSL on `ZoneBuilder`** so each zone
   declares its player start coordinates. Cleaner framework outcome but a wider
   DSL/visitor surface.
3. **Re-run Plan 12.3-13 anchor 2** (or its successor) with the corrected spawn
   so `anchor2-grounded.png` visually demonstrates "player on the floor" rather
   than "player at some grounded coordinate".
4. **(Optional)** capture a diagnostic frame at the moment we declare "grounded"
   that overlays `_playerX/_playerY` + the 4 corner probe tile values onto the
   PNG, making the visual evidence self-checking for future anchors.

## Revival Condition

- A future phase (likely "platformer-template polish" or absorbed by Phase 13)
  wants the anchor 2 grounded screenshot to demonstrate "player on the floor"
  rather than "player at the spawn coordinate".
- OR per-zone `spawnPosition()` DSL is greenlit as a framework feature.

## Reproduction

```bash
./gradlew :gbkt-examples:platformer-template:buildRom \
  :gbkt-examples:platformer-template:test --tests '*anchor2TilemapCollision'
open .planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor2-grounded.png
```

The PNG shows the player metasprite floating near the vertical centre of the
160x144 frame with no visually obvious platform under their feet.

## Hypothesis

The gameplay scene's initial spawn coords (`playerX=72`, `playerY=72`,
hardcoded in `PlatformerTemplate.kt`) place the player above the bottom-ground
tile row. Gravity then falls one or two frames until the 5-point foot probe
catches a solid middle-platform tile that is not the visually-dominant
"floor" the human eye expects. The variable evidence is correct (vy=0,
on a solid tile); the visual semantic mismatch is between "physically
grounded on any solid tile" and "visually grounded on the obvious floor".

A pre-settle step (e.g. spawn at Y=8 above the floor row and let gravity
drop the player onto the visible bottom-row tiles) OR a per-level
`spawnPosition()` DSL would both close the visual gap.

## Related Artifacts

- `.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor2-grounded.png` (Plan 12.3-13 capture)
- `.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor2-grounded-perceptual.txt` (executor's perceptual description)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/01-grounded.png` (Phase 12 baseline — byte-IDENTICAL to the 12.3 capture)
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` (spawn coord declaration around line 151)
- Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c` (level_start_pos initialisation)
- Related: [[SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY]] (predecessor — same root cause, pre-12.3 framing)
- Related: [[SEED-PHASE-12-PLAYER-METASPRITE-RENDER]] (placeholder square issue is orthogonal but compounds the visual ambiguity)

## Triage

- **Scope:** Out-of-scope for Phase 12.3. Phase 12.3 is "platformer visitor auto-emission wiring" — codegen-level cleanup. Spawn-position is example-content (or framework DSL surface), not codegen.
- **Phase 12.3-13 disposition:** APPROVED by user (jump cycle visually correct), SEED captured for backlog per user instruction.
- **Memory rule applied:** `feedback_route_to_proper_phase_when_blast_radius_is_wide` — do NOT drive inline recommendations into 12.3; route to a proper future phase.

> FIXED (Phase 21, plan 21-07): per-zone spawn(40u,120u) confirmed visually; binding sign-off (supersedes SPAWN-POSITION-CLARITY).
