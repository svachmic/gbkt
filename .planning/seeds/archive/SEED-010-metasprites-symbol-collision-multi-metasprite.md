---
id: SEED-010
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 close (code-review-gate, CR-03)
trigger_when: when any game declares ≥2 metasprites in the same compilation unit; before Phase 12 (platformer_template port likely has multiple)
scope: medium
triage_disposition: VERIFIED-ALREADY-FIXED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-010"
triage_date: 2026-06-12
---

# SEED-010: Non-namespaced C symbol names for metasprite descriptor arrays (CR-03)

## Why This Matters

`MetaspriteVisitor.generateMetaspriteDescriptor()` always emits per-frame OAM
descriptor arrays with hardcoded names:

```c
const metasprite_t sprite_metasprite_0[] = { ... };
const metasprite_t sprite_metasprite_1[] = { ... };
const metasprite_t* const sprite_metasprites[] = {
    sprite_metasprite_0, sprite_metasprite_1, ...
};
```

The names `sprite_metasprite_N` and the table `sprite_metasprites` do not include
the metasprite's ID. A game with TWO metasprites (e.g. `elephant` and `dragon`)
emits two `sprite_metasprite_0[]` arrays and two `sprite_metasprites[]` tables —
duplicate global symbol definitions, link-time error.

`generateMetaspriteFrameSwitch()` compounds the bug: it always emits the literal
string `sprite_metasprites[_idx]` regardless of which metasprite the
`moveMetasprite()` is operating on, so even if the duplicate-symbol issue is
fixed, the wrong metasprite's frame table would be referenced at runtime.

The metasprites Phase 10 example has exactly one metasprite (`elephant`), so the
bug is **latent**. Phase 12 (platformer_template port) likely has multiple sprite
types.

## Root Cause

In `gbkt-backend-gbdk/.../codegen/visitor/MetaspriteVisitor.kt`:

```kotlin
fun generateMetaspriteDescriptor(ms: MetaspriteIR): CRawCode {
    val frames = ms.frames.mapIndexed { i, frame ->
        "const metasprite_t sprite_metasprite_${i}[] = { ... };"  // ← no ms.id
    }
    val table = """
        const metasprite_t* const sprite_metasprites[] = {       // ← no ms.id
            ${frames.indices.joinToString { "sprite_metasprite_${it}" }}
        };
    """
    return CRawCode(frames.joinToString("\n") + "\n" + table)
}

fun generateMetaspriteFrameSwitch(ms: MetaspriteIR): CRawCode {
    // ... emits literal: sprite_metasprites[_idx]
    // ← does NOT use ms.id to reference the right per-metasprite table
}
```

## Fix Route

Namespace all emitted symbols with the metasprite ID:

```kotlin
const metasprite_t sprite_${ms.id}_frame_${i}[] = { ... };
const metasprite_t* const sprite_${ms.id}_frames[] = { ... };
```

And in the frame switch:

```kotlin
move_metasprite_ex(sprite_${ms.id}_frames[_${ms.id}_idx], ..., ...);
```

This requires:
1. `MetaspriteIR.id` is plumbed into both visitor methods (likely already)
2. `MetaspriteVisitor.frameSwitch` emission uses `ms.id` instead of literal `_idx`
3. The per-metasprite `_idx` variable name itself needs to be namespaced too
   (currently it's just `_idx` — would collide between two metasprites)

## Tests Needed

JVM-tier: build a game with two metasprites (`elephant` + `dragon`), assert the
generated C contains:
- `sprite_elephant_frame_0[]` and `sprite_dragon_frame_0[]` (both namespaced)
- `sprite_elephant_frames[]` and `sprite_dragon_frames[]` (both namespaced tables)
- Distinct `_elephant_idx` and `_dragon_idx` globals
- `move_metasprite_ex(sprite_elephant_frames[_elephant_idx], ...)` referencing
  the right table for each `moveMetasprite()` call

## Phase Routing

→ **Phase 10.1** scope (latent codegen bug cluster). Coupled with SEED-008
(VRAM allocation) and SEED-009 (bank includes) — same area of `GBDKPipelineV2`
+ `MetaspriteVisitor`.

## Discovery

Code review gate post-Plan 10-20 (CR-03).

## Related

- SEED-008 (CR-01: VRAM tile-slot collision)
- SEED-009 (CR-02: missing metasprites.h in bank1.c)
- SEED-011 (WR-05: hiwater reset collides OAM)
