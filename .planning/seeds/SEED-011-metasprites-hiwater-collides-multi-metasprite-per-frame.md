---
id: SEED-011
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 close (code-review-gate, WR-05)
trigger_when: any game that calls moveMetasprite() for ≥2 metasprites in a single frame; latent until then
scope: small
triage_disposition: VERIFIED-ALREADY-FIXED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-011"
triage_date: 2026-06-12
---

# SEED-011: `hiwater` reset on every `moveMetasprite` call collides OAM slot 0 (WR-05)

## Why This Matters

`MetaspriteVisitor.generateMetaspriteFrameSwitch()` (Plan 10-07) emits the OAM
hiwater tracking pattern:

```c
hiwater = 0;
switch (_rot & 0x3u) {
    case 0: hiwater += move_metasprite_ex(...); break;
    ...
}
hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);
```

The `hiwater = 0` reset is part of every emitted frame switch. If a game calls
`moveMetasprite()` for TWO different metasprites in the same frame, the second
call resets `hiwater = 0` and re-uses OAM slots 0..N — overwriting the first
metasprite's OAM entries.

Both metasprites would compete for the same hardware sprite slots; visually,
the second one drawn would clobber the first.

The metasprites Phase 10 example only calls `moveMetasprite(elephant)` once per
frame, so the bug is **latent**. Phase 12 (platformer_template port) and any
multi-NPC game would surface it.

## Root Cause

The hiwater pattern is generated per-`moveMetasprite` call rather than once per
frame. The reset and the `hide_sprites_range` tail are tightly coupled inside
the switch block — there's no shared per-frame hiwater context.

## Fix Routes

**Route A — frame-scope hiwater (preferred):**

Hoist `hiwater = 0` to the top of `frame { }` body and `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` to the bottom, both outside any individual `moveMetasprite` call. Each `moveMetasprite()` only contributes `hiwater += move_metasprite_...()` without resetting.

```c
// emitted ONCE at frame start
hiwater = 0;

// per moveMetasprite() call
switch (_rot & 0x3u) {
    case 0: hiwater += move_metasprite_ex(sprite_elephant_frames[...], ...); break;
    ...
}
// (no hide_sprites_range here)

// emitted ONCE at frame end (after all moveMetasprite calls)
hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);
```

This requires the codegen pipeline to wrap the entire `frame { }` body in a
hiwater scope, not just individual ops. Likely done via a `frame_prelude` /
`frame_postlude` emission point in the scene visitor.

**Route B — track hiwater as the IR walks moveMetasprite ops:**

`ScriptOpVisitor.visitMoveMetasprite()` checks a per-frame flag; the first call
emits `hiwater = 0` and a per-frame end-of-body hook to emit
`hide_sprites_range(...)`; subsequent calls just add to hiwater. More implicit
state, less clear emission ordering.

Route A is cleaner.

## Tests Needed

JVM-tier: build a game with two metasprites, both moved every frame. Assert
the generated `frame { }` body shape:

```c
play_frame() {
    hiwater = 0;
    // moveMetasprite(elephant)
    switch (...) { hiwater += move_metasprite_*(sprite_elephant_frames[...], ...); }
    // moveMetasprite(dragon)
    switch (...) { hiwater += move_metasprite_*(sprite_dragon_frames[...], ...); }
    hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);  // appears EXACTLY ONCE
}
```

Assert `hide_sprites_range` appears exactly ONCE in the body (not twice).

## Phase Routing

→ **Phase 10.1** scope (codegen defect cluster). Note: this likely requires
modest changes to the scene visitor to support frame_prelude/postlude hooks,
so it may need a small SPEC pass at the start of Phase 10.1 to decide between
Route A and Route B.

## Discovery

Code review gate post-Plan 10-20 (WR-05).

## Related

- SEED-008 (CR-01: VRAM collision)
- SEED-009 (CR-02: missing header in bank1.c)
- SEED-010 (CR-03: non-namespaced descriptor symbols)
