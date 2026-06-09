---
id: SEED-007
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 closeout (Plan 10-20)
trigger_when: when a game uses actor-level palette injection with multiple different palettes, OR when Phase 10.1 (metasprites surplus codegen defects) is opened for D-V1/V2/V3 fixes
scope: small
---

# SEED-007: GameBuilder actor-level palette slot always defaults to 0 (D-extra)

## Why This Matters

`GameBuilder.kt` line 713 has the same `if (pal.slot >= 0) pal.slot else 0` bug
that was fixed in `SceneBuilder.palette()` during Phase 10 Plan 16.

The `SceneBuilder.palette()` fix (Plan 16, commit `2e8fb256`) changed the `else 0`
default to `else paletteOps.size` so sequential palette declarations get sequential
slot numbers. The same fix was NOT applied to the actor-level palette injection path
in `GameBuilder.kt`.

```kotlin
// GameBuilder.kt line 713 — UNFIXED
val slot = if (pal.slot >= 0) pal.slot else 0
```

When multiple actors each have a different non-null `palette` override (with auto-slot
assignment), all actor palette injections land in slot 0 — each one overwrites the
previous. Only the last actor's palette is actually effective at runtime.

## Current Impact

**LOW** — the metasprites example does NOT use actor-level palette injection (it uses
`SceneBuilder.palette()` for the four GBC sprite palettes). This bug is latent and
will only manifest in games that wire per-actor palette overrides to different palettes
without explicit slot numbers.

## Root Cause

The `GameBuilder` actor palette injection path:

```kotlin
// GameBuilder.kt ~line 710-716
val actorPaletteOps =
    actors.mapNotNull { actor ->
        actor.palette?.let { pal ->
            val slot = if (pal.slot >= 0) pal.slot else 0  // <-- BUG: should be index-based default
            SetPalette(pal.name, slot, PaletteType.SPRITE)
        }
    }
```

The `else 0` hardcodes slot 0 for all actors with auto-slot palettes.

## Fix

Analogous to the `SceneBuilder` fix:

```kotlin
// GameBuilder.kt ~line 710-716 — fixed
var actorPaletteSlotCounter = 0
val actorPaletteOps =
    actors.mapNotNull { actor ->
        actor.palette?.let { pal ->
            val slot = if (pal.slot >= 0) pal.slot else actorPaletteSlotCounter++
            SetPalette(pal.name, slot, PaletteType.SPRITE)
        }
    }
```

Or more idiomatically with `mapIndexedNotNull`:

```kotlin
val actorPaletteOps =
    actors.mapIndexedNotNull { idx, actor ->
        actor.palette?.let { pal ->
            val slot = if (pal.slot >= 0) pal.slot else idx  // NOTE: idx here is actor index, not palette count
            SetPalette(pal.name, slot, PaletteType.SPRITE)
        }
    }
```

The cleanest fix tracks a running `paletteCount` for just the actors with non-null
palettes (matching `SceneBuilder`'s `paletteOps.size` approach).

## Affected Files

- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt` (line 713)

## Scope Estimate

**Small** — 1-3 line change + 1 JVM-tier test. The fix is clearly bounded (same root
cause and same fix shape as the already-merged `SceneBuilder.palette()` fix).

## Why Not Fixed in Phase 10

Discovered during the post-Plan-16 palette-slot fix audit. The metasprites example
does not use actor-level palette injection, so this bug does not affect Phase 10 UAT.
Adding it to Phase 10 would violate the ONE named codegen bug-fix doctrine. Seeded
for Phase 10.1 (or a future plan that touches `GameBuilder` palette handling).

## When to Surface

**Trigger conditions (any one):**
- Phase 10.1 opens for D-V1/V2/V3 — this fix can be bundled as it is in the same
  file neighborhood (palette slot handling) and is guaranteed small.
- A game author reports that multiple actors with different palette overrides all
  render with the same (last-actor) palette.
- A DSL ergonomics plan audits `GameBuilder` palette handling.

## Related

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md`
  §"Post-16 fix: palette slot indexing" (SceneBuilder fix that was the source of this discovery)
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` — already-fixed analogue
- SEED-004 (D-V1), SEED-005 (D-V2), SEED-006 (D-V3) — companion Phase 10.1 seeds
