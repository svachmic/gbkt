---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 06
subsystem: ir-lang-extension
tags:
  - ir-extension
  - dsl-internal-api
  - module-boundary
  - D-12
dependency_graph:
  requires:
    - "Phase 12 base (12-04 + 12-05 wave 1 closed)"
  provides:
    - "ZoneIR.platformerPhysicsOverride opaque payload slot"
    - "ZoneBuilder.setPlatformerPhysicsOverride internal API"
  affects:
    - "Plan 12-07 (will call setPlatformerPhysicsOverride from genre-platformer extension)"
    - "Plan 12-08 (PlatformerVisitor reads keys back via Int cast at codegen time)"
tech-stack:
  added: []
  patterns:
    - "Opaque Map<String, Any>? payload to thread genre-domain data through a leaf IR module without breaking module boundaries (Pitfall 4 mitigation)"
    - "Internal setter on a public DSL builder for cross-module extension wiring without leaking the type into the user DSL surface"
key-files:
  created:
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/WorldBuilderOverrideTest.kt
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/WorldIR.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt
decisions:
  - "Stored as opaque Map<String, Any>? (not a typed PlatformerPhysicsOverride wrapper) per RESEARCH §Pitfall 4 — keeps gbkt-ir as a leaf module with zero genre dependencies."
  - "Setter on ZoneBuilder is `internal` (not public) — only the Plan 12-07 genre-platformer extension is allowed to populate the slot, so gbkt-lang stays free of compile-time dependency on gbkt-genre-platformer types."
  - "Field placed AFTER bankOverride and BEFORE objects (mirrors plan task 1 action contract)."
metrics:
  duration_min: 2
  duration_sec: 92
  completed: 2026-05-21T20:32:14Z
  tasks_completed: 3
  files_modified: 2
  files_created: 1
  lines_added: 93
  lines_removed: 0
requirements_completed:
  - D-12  # Per-level platformerPhysics override (Re-entrant ZoneBuilder block) — slot only; genre wiring lands in 12-07
---

# Phase 12 Plan 06: Per-Level platformerPhysics Override Slot (IR + DSL) Summary

Wires the loose-coupled `Map<String, Any>?` payload slot for D-12 per-level platformer-physics overrides into `ZoneIR` and `ZoneBuilder`, keeping the IR module a leaf (no genre-platformer dependency). Plan 12-07 will add the public `platformerPhysics { }` block as a genre-platformer extension that calls the new internal setter; Plan 12-08's PlatformerVisitor will cast the values back to `Int` at codegen time.

## One-liner

Adds opaque `Map<String, Any>?` per-level physics override slot to `ZoneIR` + internal setter on `ZoneBuilder`, preserving the gbkt-ir leaf-module boundary.

## Tasks Completed

| # | Task                                                                | Commit    | Files                                                                                                            |
| - | ------------------------------------------------------------------- | --------- | ---------------------------------------------------------------------------------------------------------------- |
| 1 | Add `platformerPhysicsOverride` field to `ZoneIR`                   | `30ee2f3e` | `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/WorldIR.kt`                                                       |
| 2 | Add `internal setPlatformerPhysicsOverride` to `ZoneBuilder` + wire `build()` | `cc5701a0` | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt`                                              |
| 3 | Round-trip test (`WorldBuilderOverrideTest`, 3 @Test methods)        | `1f3ca93c` | `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/WorldBuilderOverrideTest.kt`                                   |

## What Was Built

### `ZoneIR.platformerPhysicsOverride: Map<String, Any>? = null` (gbkt-ir)

New optional field on the `ZoneIR` data class, positioned between `bankOverride` and `objects`. Documented keys for downstream `PlatformerVisitor` lowering (Plan 12-08): `gravity`, `jumpForce`, `terminalVelocity`, `solidThreshold`, `jumpHoldMaxFrames`. Stored as an opaque `Map<String, Any>?` rather than a typed wrapper to preserve the gbkt-ir leaf-module invariant — adding a typed `PlatformerPhysicsOverride` class would force gbkt-ir to either inline the type (polluting the leaf) or depend on gbkt-genre-platformer (forbidden by `validateModuleBoundaries`).

### `ZoneBuilder.setPlatformerPhysicsOverride(overrides: Map<String, Any>)` (gbkt-lang)

`internal` setter exposed to the same Gradle module. The Plan 12-07 genre-platformer extension `fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit)` will:
1. Build a `Map<String, Any>` from a `PlatformerPhysicsBuilder` invocation
2. Call `zoneBuilder.setPlatformerPhysicsOverride(map)`

Marked `internal` (not `public`) on purpose: ZoneBuilder lives in gbkt-lang, and exposing a public method that takes a `Map<String, Any>` would invite end-user direct calls bypassing the typed builder. The internal restriction means only the gbkt-genre-platformer extension (which is in a sibling module and uses `@Suppress("INVISIBLE_REFERENCE")`-free public DSL via `ZoneBuilder.platformerPhysics`) reaches in. Wait — `internal` Kotlin visibility is module-scoped, so the genre-platformer module CANNOT call `setPlatformerPhysicsOverride` directly. **Open question for Plan 12-07:** widen to `public` with `@PublishedApi internal` + `@JvmSynthetic`, OR move the extension into gbkt-lang and have it delegate to genre-platformer-supplied builder, OR change the access modifier. **The plan as written specifies `internal` and the round-trip test confirms in-module access works** — Plan 12-07's planner must resolve the cross-module call. Documented as a known follow-up below; the slot + builder API as locked here is contract-correct for Plan 12-06's stated goal (in-module round-trip).

### `WorldBuilderOverrideTest` (gbkt-lang test)

Three `@Test` methods:
- `default ZoneBuilder build produces null platformerPhysicsOverride` — locks the default
- `setPlatformerPhysicsOverride preserves a single-entry map` — basic round-trip
- `setPlatformerPhysicsOverride preserves multiple entries` — 5-key map mirroring the documented key set

Lives in `gbkt-lang/src/test/kotlin/...` to satisfy the `internal` visibility constraint.

## Verification

| Gate                                                                                 | Result   |
| ------------------------------------------------------------------------------------ | -------- |
| `./gradlew :gbkt-ir:compileKotlin --quiet`                                           | exit 0   |
| `grep -c platformerPhysicsOverride gbkt-ir/.../WorldIR.kt` (≥1)                      | 1        |
| `! grep -q 'import io.github.gbkt.genre' gbkt-ir/.../WorldIR.kt`                     | OK (0)   |
| `./gradlew :gbkt-ir:validateModuleBoundaries`                                        | GREEN    |
| `./gradlew :gbkt-lang:compileKotlin --quiet`                                         | exit 0   |
| `grep -c 'internal fun setPlatformerPhysicsOverride\|platformerPhysicsOverride = platformerPhysicsOverride' WorldBuilders.kt` (≥2) | 2        |
| `./gradlew :gbkt-lang:test --tests "WorldBuilderOverrideTest"`                       | 3 / 3 GREEN |
| `./gradlew :gbkt-ir:test :gbkt-lang:test --quiet` (overall plan verification)        | GREEN    |

All plan-level success criteria met.

## Deviations from Plan

None — plan executed exactly as written. The plan-level note about `internal` visibility being callable from gbkt-genre-platformer (an extension in a sibling Gradle module) is technically inaccurate under Kotlin's module-scoped `internal` semantics, but the plan explicitly states "Plan 12-07 wires the public DSL surface" — the resolution belongs to Plan 12-07's planner (widen access, move extension, or use `@PublishedApi`). Plan 12-06 as written is in-module round-trip only; this is verified GREEN. Documented under "Known Follow-ups" below so the next planner sees it.

## Auth Gates

None.

## Known Stubs

None — every code path is wired and tested.

## Known Follow-ups for Plan 12-07

`setPlatformerPhysicsOverride` is `internal`, which scopes it to the `gbkt-lang` Gradle module. The genre-platformer extension function from Plan 12-07 lives in `gbkt-genre-platformer` — a different module. Plan 12-07's planner has three viable choices:

1. **Promote to public** (preferred) — change `internal` to `public`. Document with KDoc that callers should prefer the genre extension `platformerPhysics { }` and that this method is "for genre extension use". Trades a small public-surface widening for the cleanest cross-module call.
2. **`@JvmSynthetic` + `@PublishedApi internal`** — keeps Kotlin source visibility narrow but exposes to the JVM. Adds complexity.
3. **Move the extension into gbkt-lang** — keeps `internal` strict but breaks the gbkt-genre-platformer ownership of platformer DSL surfaces. Not recommended.

Plan 12-06 deliberately did NOT preemptively switch to `public` because the plan's `<action>` explicitly says `internal`. Plan 12-07 owns the resolution.

## Threat Flags

None — this plan adds an opaque IR field + an internal builder API. No new I/O, no network, no auth, no schema changes at trust boundaries.

## Self-Check: PASSED

- File exists: `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/WorldIR.kt` (modified, contains `platformerPhysicsOverride`)
- File exists: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt` (modified, contains internal setter)
- File exists: `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/WorldBuilderOverrideTest.kt` (created)
- Commit `30ee2f3e` present in `git log`
- Commit `cc5701a0` present in `git log`
- Commit `1f3ca93c` present in `git log`
