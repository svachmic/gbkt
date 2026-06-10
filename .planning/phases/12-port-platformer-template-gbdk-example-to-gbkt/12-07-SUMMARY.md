---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 07
subsystem: genre-platformer-dsl-extension
tags:
  - dsl-extension
  - zone-builder
  - shadowing-semantics
  - module-boundary
  - D-12
dependency_graph:
  requires:
    - "Plan 12-06 (ZoneIR.platformerPhysicsOverride slot + ZoneBuilder.setPlatformerPhysicsOverride internal API)"
  provides:
    - "Public DSL: zone(id) { platformerPhysics { gravity(3); solidThreshold(68); ... } }"
    - "OverrideTrackingPhysicsBuilder helper that captures only the explicitly-set fields"
    - "Stable key contract: gravity, jumpForce, terminalVelocity, solidThreshold, jumpHoldMaxFrames"
  affects:
    - "Plan 12-08 (PlatformerVisitor will read these key names back via Int cast at codegen time)"
    - "Plan 12-13 (variable-height jump codegen reads jumpHoldMaxFrames override)"
    - "Plan 12-16 (game-level composition uses this surface for per-world physics)"
tech-stack:
  added: []
  patterns:
    - "open + override marker subclass to capture WHICH builder setters were invoked (preserves shadowing — unset fields ABSENT from map, not present-with-default)"
    - "Public extension function on cross-module DSL builder + opaque Map<String, Any> setter to avoid leaking genre types into gbkt-lang"
key-files:
  created:
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/ZonePlatformerPhysicsTest.kt
  modified:
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerBuilders.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt
decisions:
  - "Promoted ZoneBuilder.setPlatformerPhysicsOverride from `internal` to `public` (resolution of the known follow-up logged in 12-06-SUMMARY.md). Kotlin `internal` is module-scoped; the genre-platformer extension lives in a sibling Gradle module and could not call an internal setter cross-module. The setter payload remains opaque Map<String, Any>, so no genre type leaks into gbkt-lang — leaf-module invariant preserved."
  - "Made `PlatformerPhysicsBuilder` (and its five tracked setter methods: gravity / jumpForce / terminalVelocity / solidThreshold / jumpHold) `open` so the OverrideTrackingPhysicsBuilder subclass can override them. The other PlatformerPhysicsBuilder methods (coyoteTime, jumpBuffer, airControl, fixedJump, wallJump) were left non-open intentionally — they are NOT currently per-level overridable (D-12 only covers the five tracked keys), so they fall through to super on the rare chance a user calls them inside a per-zone block."
  - "Used a subclass-with-overrides design (not a property-set-flag bitmap or a Map-driven wrapper) because it preserves the exact PlatformerPhysicsBuilder DSL signature inside the lambda — users get identical authoring experience whether they call platformerPhysics on GameBuilder or on ZoneBuilder."
  - "Test routes through public GameBuilder.zone(id) { } + currentZones() instead of the plan's literal ZoneBuilder(...).apply { }.build() form, because ZoneBuilder.build() is internal to gbkt-lang and unreachable from gbkt-genre-platformer. The chosen path is a strictly stronger end-to-end test — it exercises the exact public DSL path end users author."
metrics:
  duration_min: 4
  duration_sec: 240
  completed: 2026-05-21T20:45:00Z
  tasks_completed: 2
  files_modified: 3
  files_created: 1
  lines_added: 260
  lines_removed: 11
requirements_completed:
  - D-12  # Per-level platformerPhysics shadowing override (DSL surface — slot was added in 12-06)
---

# Phase 12 Plan 07: Wire `ZoneBuilder.platformerPhysics { }` DSL Extension Summary

Wires the public re-entrant `platformerPhysics { }` block as an extension on `ZoneBuilder`, completing the D-12 per-zone physics override surface. The extension uses an `OverrideTrackingPhysicsBuilder` subclass that captures ONLY the explicitly-set fields into the opaque `Map<String, Any>` override slot added in Plan 12-06 — preserving the shadowing semantic (unset fields are ABSENT from the map, NOT present-with-default-value).

## One-liner

Adds `fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit)` in gbkt-genre-platformer that records only the SET fields into ZoneIR.platformerPhysicsOverride via an open-subclass marker builder.

## Tasks Completed

| # | Task                                                                                  | Commit     | Files                                                                                                                                                                              |
| - | ------------------------------------------------------------------------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Add `fun ZoneBuilder.platformerPhysics` extension + `OverrideTrackingPhysicsBuilder` + open setter methods + promote setter to public | `9fc341de` | `gbkt-lang/.../WorldBuilders.kt`, `gbkt-genre-platformer/.../PlatformerBuilders.kt`, `gbkt-genre-platformer/.../PlatformerExtensions.kt`                                            |
| 2 | End-to-end DSL test (4 @Test methods) locking the shadowing contract                  | `c446db63` | `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/ZonePlatformerPhysicsTest.kt`                                                                            |

## What Was Built

### `fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit)` (gbkt-genre-platformer)

Public extension on `ZoneBuilder` defined in `PlatformerExtensions.kt`. Body:

```kotlin
fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit) {
    val builder = OverrideTrackingPhysicsBuilder()
    builder.block()
    setPlatformerPhysicsOverride(builder.toOverrideMap())
}
```

Calling this extension creates a fresh tracking builder, runs the user's lambda against it, and stores the captured override map on the zone via `ZoneBuilder.setPlatformerPhysicsOverride` (added in Plan 12-06).

The lambda receiver type is `PlatformerPhysicsBuilder` (the existing public class — not the internal subclass), so the user-facing DSL signature is identical to `GameBuilder.platformerPhysics { }`. Discoverability via IDE autocomplete and consistency with the game-level form is preserved.

### `internal class OverrideTrackingPhysicsBuilder : PlatformerPhysicsBuilder()` (gbkt-genre-platformer)

`PlatformerPhysicsBuilder` subclass that overrides the five tracked setter methods to record which fields were explicitly set:

| Builder method                  | Map key                  |
| ------------------------------- | ------------------------ |
| `gravity(value: Int)`           | `"gravity"`              |
| `jumpForce(value: Int)`         | `"jumpForce"`            |
| `terminalVelocity(value: Int)`  | `"terminalVelocity"`     |
| `solidThreshold(value: Int)`    | `"solidThreshold"`       |
| `jumpHold(maxFrames: Int)`      | `"jumpHoldMaxFrames"`    |

Each override calls `super.<method>(value)` first (so the underlying config still works if buildConfig/build is ever called on the tracker) and then records the value into a private `setFields: MutableMap<String, Any>`. `toOverrideMap(): Map<String, Any>` returns an immutable snapshot.

Marked `internal` so the helper class is invisible to end-users — they call `platformerPhysics { }`, never `OverrideTrackingPhysicsBuilder` directly.

### `PlatformerPhysicsBuilder` + 5 methods now `open` (gbkt-genre-platformer)

Changed in `PlatformerBuilders.kt`:
- `class PlatformerPhysicsBuilder(...)` → `open class PlatformerPhysicsBuilder(...)`
- `fun gravity(value: Int)` → `open fun gravity(value: Int)`
- `fun jumpForce(value: Int)` → `open fun jumpForce(value: Int)`
- `fun terminalVelocity(value: Int)` → `open fun terminalVelocity(value: Int)`
- `fun solidThreshold(value: Int)` → `open fun solidThreshold(value: Int)`
- `fun jumpHold(maxFrames: Int)` → `open fun jumpHold(maxFrames: Int)`

The other PlatformerPhysicsBuilder methods (`coyoteTime`, `jumpBuffer`, `airControl`, `fixedJump`, `wallJump`) are intentionally left non-open. They are NOT currently per-level overridable under D-12 — if a user calls them inside a `zone { platformerPhysics { } }` block, the set falls through to `super` and is silently NOT recorded in the override map. This is the documented contract (per plan task 1 action note: "If a method is NOT overridden here, its set-call falls through to super and is NOT recorded in the override map — that's intentional for fields like wallJump / variableHeightJump which are not currently per-level overridable").

### `ZoneBuilder.setPlatformerPhysicsOverride` promoted to `public` (gbkt-lang)

Changed in `WorldBuilders.kt` from:
```kotlin
internal fun setPlatformerPhysicsOverride(overrides: Map<String, Any>) { ... }
```
to:
```kotlin
fun setPlatformerPhysicsOverride(overrides: Map<String, Any>) { ... }
```

Kotlin's `internal` is Gradle-module-scoped. The gbkt-genre-platformer extension defined above lives in a sibling module and could not see `internal` symbols from gbkt-lang. The payload remains an opaque `Map<String, Any>` (per Plan 12-06's design), so widening to `public` does NOT leak any gbkt-genre-platformer type into gbkt-lang — the leaf-module invariant promised by Plan 12-06 is preserved.

KDoc updated to note the widening and the recommended call path (genre extension, not direct call).

### `ZonePlatformerPhysicsTest` (4 @Test methods, gbkt-genre-platformer/src/test)

End-to-end test locking the shadowing contract:

1. **`platformerPhysics with two set fields populates exactly those two keys`** — `zone { platformerPhysics { solidThreshold(68); gravity(3) } }` produces `{"solidThreshold" → 68, "gravity" → 3}` (key contract verbatim).
2. **`platformerPhysics with empty block records an empty override map (not null)`** — distinguishes "block called, no overrides" from "block never called".
3. **`zone without platformerPhysics block leaves override null`** — locks the default.
4. **`calling platformerPhysics twice on the same zone replaces, not merges`** — last-writer-wins semantic (each call builds a fresh OverrideTrackingPhysicsBuilder and overwrites the slot).

The key names `"solidThreshold"`, `"gravity"`, and `"jumpHoldMaxFrames"` appear as string literals in the test source — this LOCKS the contract that Plan 12-08's PlatformerVisitor will read keys back by these exact names.

## Verification

| Gate                                                                                  | Result    |
| ------------------------------------------------------------------------------------- | --------- |
| `./gradlew :gbkt-lang:compileKotlin :gbkt-genre-platformer:compileKotlin`             | exit 0    |
| `grep -c 'fun ZoneBuilder.platformerPhysics' .../PlatformerExtensions.kt` (≥1)         | 1         |
| `grep -c 'internal class OverrideTrackingPhysicsBuilder' .../PlatformerExtensions.kt` (≥1) | 1     |
| `grep -c 'fun toOverrideMap' .../PlatformerExtensions.kt` (≥1)                         | 1         |
| `grep -c 'open class PlatformerPhysicsBuilder' .../PlatformerBuilders.kt`              | 1         |
| 5 setter methods (gravity, jumpForce, terminalVelocity, solidThreshold, jumpHold) all `open` | 5/5  |
| `./gradlew :gbkt-genre-platformer:test --tests "ZonePlatformerPhysicsTest"`           | 4/4 GREEN |
| `./gradlew :gbkt-genre-platformer:test`                                                | GREEN     |
| `./gradlew :gbkt-lang:test` (regression — no Plan 12-06 tests broken)                 | GREEN     |
| `grep -c 'io.github.gbkt.genre' gbkt-ir/.../WorldIR.kt` (must be 0)                    | 0         |

All plan-level success criteria met.

## Deviations from Plan

### 1. [Rule 3 - Blocking] Promoted `ZoneBuilder.setPlatformerPhysicsOverride` from `internal` to `public`

- **Found during:** Task 1 (compile of the new extension fails when the cross-module call sees an `internal` symbol from gbkt-lang)
- **Issue:** Kotlin `internal` is Gradle-module-scoped. The new `fun ZoneBuilder.platformerPhysics(...)` extension lives in **gbkt-genre-platformer**, while `setPlatformerPhysicsOverride` was declared `internal` in **gbkt-lang**. Cross-module call is not visible, so the extension cannot wire to the override slot.
- **Resolution:** Widened the setter to `public` — Option 1 in the predecessor's documented follow-up (12-06-SUMMARY.md "Known Follow-ups for Plan 12-07"). The setter is the only widening; `ZoneBuilder.build()` stays `internal`. The opaque `Map<String, Any>` payload means no gbkt-genre-platformer type leaks into gbkt-lang — the leaf-module invariant promised by Plan 12-06 is preserved.
- **Files modified:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt` (signature + KDoc updated)
- **Commit:** `9fc341de`
- **Alternatives considered:**
  - `@PublishedApi internal` + inline — adds complexity for no real surface narrowing (the JVM-visible symbol is still there).
  - Move the extension into gbkt-lang — breaks the genre-platformer ownership of platformer DSL surfaces. Rejected per CLAUDE.md "Module Layers" guidance.

### 2. [Rule 3 - Routing] Test uses `GameBuilder.zone()` + `currentZones()` instead of the plan's literal `ZoneBuilder(...).apply{}.build()` form

- **Found during:** Task 2 (test author noticed `ZoneBuilder.build()` is `internal` to gbkt-lang)
- **Issue:** The plan-as-written specifies a test like `ZoneBuilder("z1").apply { platformerPhysics { ... } }.build().platformerPhysicsOverride`. That literal form cannot compile from `gbkt-genre-platformer` because `ZoneBuilder.build()` is `internal` to `gbkt-lang`.
- **Resolution:** Routed all four tests through `GameBuilder("test").zone("zN") { platformerPhysics { ... } }` and read back via `gb.currentZones().single().platformerPhysicsOverride`. This is a strictly stronger end-to-end test — it exercises the exact public DSL path end users author.
- **Files modified:** `gbkt-genre-platformer/src/test/kotlin/.../ZonePlatformerPhysicsTest.kt` (test author choice — no production-code change required)
- **Commit:** `c446db63`
- **Behaviour locked:** Identical to the plan's intent — all 4 promised behaviours (only-set-fields, empty-block, no-block, twice-replaces) are asserted against `ZoneIR.platformerPhysicsOverride`. Key-name string literals (`"solidThreshold"`, `"gravity"`, `"jumpHoldMaxFrames"`) appear in the test source per the plan's acceptance.

## Auth Gates

None.

## Known Stubs

None — every code path is wired and tested.

## Known Follow-ups

None for Plan 12-07. Plan 12-08 (PlatformerVisitor) will read the override keys back via `Int` cast at codegen time; the key contract is locked by `ZonePlatformerPhysicsTest` string literals.

## Threat Flags

None — this plan adds a public DSL extension wired to an existing opaque IR field. No new I/O, no network, no auth, no schema changes at trust boundaries. The widening of `setPlatformerPhysicsOverride` from `internal` to `public` is an API-surface change only — the payload remains the same opaque `Map<String, Any>` Plan 12-06 introduced.

## Self-Check: PASSED

- File exists: `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt` (modified, contains `fun ZoneBuilder.platformerPhysics` + `internal class OverrideTrackingPhysicsBuilder` + `fun toOverrideMap`)
- File exists: `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerBuilders.kt` (modified, `open class` + 5 `open fun`)
- File exists: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt` (modified, setter widened to `public`)
- File exists: `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/dsl/ZonePlatformerPhysicsTest.kt` (created, 4 @Test methods)
- Commit `9fc341de` present in `git log`
- Commit `c446db63` present in `git log`
