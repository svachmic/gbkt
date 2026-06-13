---
id: SEED-003
status: dormant
planted: 2026-05-15
planted_during: v1.0 / Phase 09.1 closeout (user UAT 2026-05-15)
trigger_when: when a "playable demos" or "examples polish" milestone is opened, OR when simple_physics is referenced from documentation/marketing where wrap behavior would mislead users
scope: small
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-003"
triage_date: 2026-06-12
---

# SEED-003: simple_physics is reference-faithful but not "playable"

## Why This Matters

During user UAT of Phase 09.1's gap-closure ROM (2026-05-15) two surprises
surfaced — both reference-faithful, neither a defect:

1. **Sprite wrap-around.** Holding UP long enough or pressing A makes the
   smiley leave the top of the screen and reappear at the bottom (and
   symmetrically for the other axes). This is a Game Boy hardware artifact —
   `posY shr 4` produces a negative INT16 → GBDK `move_sprite` casts to
   `UINT8` → `-1` becomes `255` (off-bottom but visible as the sprite
   re-enters the screen). The reference `phys.c` does exactly the same thing
   because it has no bounds clamp.
2. **A-press jump is "way too enthusiastic."** Jump impulse is
   `spdY = -512` sub-pixels/frame (= `JUMP_ACCEL × 16`). The Y-decel ladder
   runs `spdY++` at 1 sub-pixel/frame, so velocity takes **512 frames
   (~8.5 s)** to dissipate. Combined with the wrap above, the sprite
   traverses the screen multiple times before settling. Again — straight from
   the reference's constants.

Both behaviors match `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c`
verbatim. The gbkt port is correct. But "correct" ≠ "good user-facing demo."

## PLAYBOOK.md misclaim to fix in any future polish phase

`gbkt-examples/simple-physics/PLAYBOOK.md` currently states:

> Win / Lose Conditions: None — infinite physics demo. **Sprite remains on screen (no bounds wrapping)**; position drifts freely.

The bolded clause is wrong. The sprite does NOT remain on screen — it wraps
via UINT8 OAM Y. "Position drifts freely" is the accurate description.

## When to Surface

**Trigger conditions (any one):**
- A milestone for "examples polish" or "playable demos" is opened, with the
  goal of making the reference ports demo-quality (rather than codegen-oracle
  fixtures).
- simple_physics gets used in onboarding docs / marketing / tutorial content
  where users will be surprised by the wrap behavior.
- A future port re-encounters the missing-clamp pattern AND the port has a
  reasonable user-facing demo expectation (e.g., metasprites at Phase 10).

**Do NOT surface this seed in v1.0.** Phase 9 deliberately chose
"faithful port" over "playable demo" — the three-signal PASS (588 B ROM =
1.025× reference; zero simple_physics-specific SDCC warnings; 3/3 D-01
behaviors GREEN with binding visual evidence) is the codegen oracle's job,
and adding bounds clamps would diverge from the reference and invalidate the
ROM-size comparison.

## Scope Estimate

**Small** — two surgical changes:

```kotlin
// gbkt-examples/simple-physics/src/main/kotlin/.../SimplePhysics.kt
// Inside the frame { } block, after position integration:

// Clamp posX to screen [0, 160) — kill velocity at wall
whenever(posX isBelow 0) { posX set 0; spdX set 0 }
whenever(posX isAbove (152 shl 4)) { posX set (152 shl 4); spdX set 0 }  // 152 = 160 - 8 (sprite width)

// Clamp posY to screen [0, 144) — kill velocity at top/bottom
whenever(posY isBelow 0) { posY set 0; spdY set 0 }
whenever(posY isAbove (136 shl 4)) { posY set (136 shl 4); spdY set 0 }  // 136 = 144 - 8 (sprite height)
```

Optional second change: reduce `JUMP_ACCEL` from 32 (×16 = 512) to ~12–16
(×16 = 192–256) so the impulse decays in under 4 seconds even without bounds.
This is a deeper divergence — the constant is named in the reference.

Plus the PLAYBOOK.md fix (1 line).

## Why Not Fixed in v1.0

Phase 9's contract — one named codegen bug-fix per port, faithful to the
reference C — is what this seed deliberately preserves. Adding bounds clamps
to a port that explicitly didn't have them in the reference would:

- Invalidate the ROM-size comparison (`gbkt-build.properties` records the
  port's bytes vs. `phys.c`'s reference bytes — the clamps would inflate the
  port's ROM and break the 1.025× ratio claim).
- Set a precedent for "port = playable" instead of "port = codegen oracle,"
  which has different scope discipline (the three-signal contract assumes
  reference fidelity, not user-facing polish).
- Introduce per-author judgment about "what's playable enough?" — slippery
  slope into scope creep across all four reference ports (simple_physics,
  metasprites, banks, platformer_template).

## Blast-Radius Hint

**Files affected:** 1 (SimplePhysics.kt) + 1 (PLAYBOOK.md).
**Tests affected:** 0 existing; possibly 1 new (asserting clamp behavior).
**Other examples affected:** 0 — wrap-around isn't observed in metasprites/banks/platformer_template (they have different gameplay shapes).

## Possible Follow-Up Phase

An "examples playability polish" phase that batches:
- SEED-003 (this) — simple_physics bounds + jump tuning + PLAYBOOK fix.
- Any analogous gaps in metasprites / banks / platformer_template after they
  ship.
- A reusable `clampToScreen(actor)` DSL helper if 3+ ports need it.

One phase, 1–4 plans. Triggered by external pressure (user-facing docs, demo
videos, conference talks) rather than internal codegen pressure.
