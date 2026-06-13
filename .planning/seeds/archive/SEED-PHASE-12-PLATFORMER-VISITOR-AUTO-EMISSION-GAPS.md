# SEED: Phase 12 — PlatformerVisitor Auto-Emission Wiring Gaps (3 found in Wave 13)

> **Triage:** VERIFIED-ALREADY-FIXED — [TRIAGE.md#SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS) · 2026-06-12

**Created:** 2026-05-23 (Phase 12 Wave 13 — Plan 12-21 close-out)
**Origin phase:** 12 (port-platformer-template-gbdk-example-to-gbkt)
**Source:** Plan 12-21 anchor 3 (horizontal scroll) UAT — three runtime gaps surfaced in one wave
**Status:** Deferred — captured for a Phase 13 inserted phase OR Phase 12.3 if Wave 13 surfaces more.
**Routing:** Open; recommend a dedicated PlatformerVisitor-wiring phase before further platformer-template anchors
**Blast radius:** Medium-high — touches `gbkt-genre-platformer` PlatformerVisitor + MetaspriteVisitor; affects any future platformer port using tilemap-camera mode

## Context

Plan 12-21 (Anchor 3, horizontal scroll) closed GREEN, but only after 3 inline user-DSL
fixes papered over missing auto-emission in PlatformerVisitor. Each gap is small in code
volume but together they reveal that PlatformerVisitor's wave-7 codegen (Plans 12-11,
12-13) is incomplete for the platformer-template substrate.

## The 4 Gaps (updated 2026-05-23T13:20Z after Plan 12-22 surfaced gap #4)

### Gap 4: `_walkFrameIdx` Never Incremented (surfaced in Plan 12-22)

`_walkFrameIdx` is declared in the DSL and READ by the metasprite render switch
(`bank1.c:65,70,75,80` — `sprite_player_frames[_walkFrameIdx]`), but NOTHING
increments it. The DSL even comments out the expected cycle pattern at
`PlatformerTemplate.kt:109-113` but doesn't implement it.

**Symptom:** anchor 4 walk-frame cycling test fails — walkFrameIdx stays at 0
across all captures (no animation, player visually stuck on frame 0).

**Proper fix:** PlatformerVisitor should auto-emit the threeFrameCounter ++ +
walkFrameIdx cycle logic when the platformer-physics zone has a multi-frame
player metasprite bound. Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c:130-145`.

**Status:** NOT inline-fixed. Triggered the 4-gap escalation threshold per
session decision; routed to Phase 12.3.

---

### Gap 1: Input → Velocity Wiring (`whenever(dpad.*.held) → playerVx`)

`platformer_physics_update()` reads `_playerVx` for horizontal collision + integrates
`_playerX += _playerVx >> 4` — but NO code anywhere sets `_playerVx` from dpad input.
The reference GBDK `player.c` sets `playerXVelocity = ±moveSpeed` on RIGHT/LEFT held
with ground-friction deceleration on release.

**Inline workaround (Plan 12-21 commit):**
```kotlin
whenever(dpad.right.held) { playerVx set 127 }
whenever(dpad.left.held) { playerVx set -127 }
whenever(dpad.none) { playerVx set 0 }
```

**Proper fix:** PlatformerVisitor should auto-emit these clauses when a zone is bound
to platformer-physics, OR add a `platformerInput { walkSpeed(N); friction(F) }` DSL
on `ZoneBuilder` that lowers to the input-handler emission.

### Gap 2: `platformer_camera_update()` Defined But Never Called

`platformer_camera_update()` is emitted in main.c with full body (move_bkg call,
_map_pos_x computation, column-scroll trigger via `_bkg_set_level_submap_banked`),
but NOTHING invokes it. Plan 12-11's task to "wire the call site" landed the function
body but missed the caller wiring.

Without this call:
- `move_bkg(_camera_x, 0u)` never fires → no visual scroll
- `_map_pos_x` stays at 0 → column-scroll trigger never compares `!= _old_map_pos_x`
- New columns at scroll edges never load

**Inline workaround (Plan 12-21 commit):**
```kotlin
cEmit("platformer_camera_update();")
```

**Proper fix:** PlatformerVisitor's wave-7 codegen should append a
`platformer_camera_update();` call to every gameplay-zone scene's frame body,
immediately after the physics update.

### Gap 3: Metasprite Render Uses Absolute World Position (No Camera Offset)

`move_metasprite_ex(..., DEVICE_SPRITE_PX_OFFSET_X + (_playerX >> 4), ...)` renders
the player at WORLD pixel position, not screen-relative position. As the world scrolls,
the player metasprite drifts off-screen because the bg scrolls but the sprite layer
doesn't compensate.

Reference: `(player.pos.x >> 8) - cameraX` to get screen-relative position.

**Inline workaround (Plan 12-21 commit):**
```kotlin
cEmit("_playerX = (INT16)(_playerX - ((INT16)_camera_x << 4));")
moveMetasprite(player)
cEmit("_playerX = (INT16)(_playerX + ((INT16)_camera_x << 4));")
```

This fudges `_playerX` to be screen-relative for the render call, then restores
the world value for the next frame's physics_update. Ugly but minimal.

**Proper fix:** MetaspriteVisitor (when bound under PlatformerVisitor tilemap-camera
mode) should emit `(_playerX >> 4) - _camera_x` instead of `(_playerX >> 4)` for
the X coordinate. Detect the mode via the zone's `platformerCamera` config.

## What's Deferred

A dedicated phase (suggest Phase 12.3 if Wave 13 reveals more, or Phase 13 absorption)
that:

1. Removes all 3 inline-fix cEmits from `gbkt-examples/platformer-template/src/main/kotlin/...PlatformerTemplate.kt`
2. Adds the input→velocity auto-emission (or `platformerInput { }` DSL)
3. Wires the `platformer_camera_update()` call site in PlatformerVisitor wave-7 codegen
4. Modifies MetaspriteVisitor to apply camera offset when rendering in tilemap-camera mode
5. Re-shoots Anchor 1 (12-19), Anchor 2 (12-20), Anchor 3 (12-21) — all should still
   pass with proper auto-emission instead of inline fudges

## Revival Condition

- Wave 13 surfaces a 4th wiring gap (would clearly justify a new sub-phase)
- Phase 12 verifier flags the inline-cEmit accumulation as a code-quality concern
- Phase 13 framework-primitives work touches PlatformerVisitor

## Related Artifacts

- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:398-440` (the 3 inline-fix cEmit comments)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` (where auto-emission should land)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt` (where camera-offset render lives)
- Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c`
- Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/camera.c`
- Related: [[SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY]] (separate but compounding visual concern)
- Related: [[SEED-PHASE-12-PLAYER-METASPRITE-RENDER]] (placeholder square — separate issue)
