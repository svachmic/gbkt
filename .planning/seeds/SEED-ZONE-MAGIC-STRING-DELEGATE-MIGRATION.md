# SEED: `zone(id: String)` magic-string violation — convert to delegate-pattern

**Created:** 2026-05-25 (Phase 12.6 discuss-phase — user-surfaced during DEFECT-2 fix DSL placement question)
**Origin phase:** 12.6 (main-loop-level-switch-codegen-fix) — surfaced while adding `spawn(x, y)` to `ZoneBuilder` for DEFECT-2 per-level spawn fix
**Source:** User direct callout during the AskUserQuestion follow-up on `spawn()` DSL surface placement. Quote: "Can be on zone block, but as always, I am raising that there is a magic string in zone !!! This should NEVER be accepted unless it's a path to a file!!!"
**Status:** Open — Project Rule #1 (`feedback_no_magic_strings`) violation in substrate DSL. Not blocking any active phase; defers cleanly. NOT a Phase 12 close gate.
**Routing:** Phase 13 (framework-primitives-surfaced-by-example-ports-rolling) — substrate DSL primitive belongs in framework-shaping work, not in a port-specific codegen-fix phase.
**Blast radius:** Wide. Touches `gbkt-lang` (builder + new `ZoneDelegate.provideDelegate`), `gbkt-engine` (IR `Zone` data class — id field semantics), and EVERY game that uses `zone()`: at minimum dungeon, explorer, rpg-lite, banks, racer, platformer-template, simple-physics, plus all in-tree test fixtures. Each usage site needs a coordinated rewrite from `val world1Area1 = zone("world1Area1") { ... }` → `val world1Area1 by zone { ... }`.

## The violation

`gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt:480`:

```kotlin
fun zone(id: String, block: ZoneBuilder.() -> Unit): ZoneRef {
    // ...
}
```

Usage in current ports:

```kotlin
val world1Area1 = zone("world1Area1") { ... }  // string ID DUPLICATED
val world1Area2 = zone("world1Area2") { ... }  // string ID DUPLICATED
val world2Area1 = zone("world2Area1") { ... }  // string ID DUPLICATED
```

This violates Project Rule #1 (`feedback_no_magic_strings` in user memory): "DSL must
reflect names from property delegates / lambda params, never duplicate as String
params, unless the string is a path to a file." The zone ID is duplicated:
once as the property name, once as the string argument. Either drifts out of sync
silently or imposes maintenance friction.

## The fix shape (delegate pattern)

Mirror the established pattern already used by `actor()`, `scene()`, `character()`,
`monster()`, `item()`, `ability()`, etc.:

```kotlin
// Current (magic-string):
val world1Area1 = zone("world1Area1") { spawn(16, 120); tileset(...) }

// Target (delegate):
val world1Area1 by zone { spawn(16, 120); tileset(...) }
//                ^^                  ^ name inferred from property delegate
```

Implementation outline:

1. **`gbkt-lang`** — Add `ZoneDelegate` class implementing
   `PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ZoneRef>>`. Its
   `provideDelegate` captures the property name and registers the zone with the
   active `GameBuilder`. Mirror `ActorDelegate.provideDelegate` shape exactly.
2. **`gbkt-lang`** — Add `fun GameBuilder.zone(block: ZoneBuilder.() -> Unit):
   ZoneDelegate` (no String id param). Keep the old `zone(id: String, ...)`
   signature briefly with `@Deprecated("Use delegate form: val foo by zone { }")`
   to surface the migration path in the IDE; remove after every in-tree
   usage migrates.
3. **`gbkt-engine`** — `Zone.id` semantics: id is set by `provideDelegate` at
   property-resolution time (same as `actor.id` today). No IR shape change.
4. **All callers** — mechanical migration `val X = zone("X") { ... }` → `val X by
   zone { ... }`. Test fixtures included.
5. **Migration round-trip test** — `gbkt-lang` adds a DSL round-trip test
   asserting that `val foo by zone { }` produces `Zone(id = "foo", ...)`,
   matching the existing pattern in `ActorDelegateTest` / `SceneDelegateTest`.

## Why this is NOT in Phase 12.6 scope

Per `feedback_route_to_proper_phase_when_blast_radius_is_wide`: a substrate DSL
refactor that touches `gbkt-lang` + `gbkt-engine` + ≥7 games + test fixtures has
wide enough blast radius to earn its own phase. Phase 12.6's charter is a
narrow codegen fix for two main-loop level-switch defects (DEFECT-1 + DEFECT-2);
absorbing this would balloon scope and risk a 12.6.1 follow-up
(`feedback_many_small_plans_terminal_subphase`).

Phase 12.6 adds `spawn(x: UByte, y: UByte)` to the existing `ZoneBuilder` —
numeric coords only, no new magic strings introduced. The PRE-EXISTING
`zone(id: String)` violation predates Phase 12.6 and is preserved as-is by
this phase (no regression added; existing tech debt unchanged).

## Why Phase 13, not a new dedicated phase

Phase 13 (framework-primitives-surfaced-by-example-ports-rolling) is already
charted as the home for substrate DSL primitives that surface as example
ports roll. `zone()` is exactly that kind of primitive — used by multiple
genre ports (RPG dungeons, platformer levels, racer tracks). A delegate-form
migration fits Phase 13's framing better than a one-off phase.

If Phase 13 already has too many in-scope items by the time it's planned,
this can spin out as Phase 13.1 or a peer phase. Leaving the routing
recommendation as "Phase 13 default; spin out if needed" gives the planner
flexibility.

## Diagnostic / scoping notes for the future phase

- Search command for the migration surface:
  `git grep -n 'zone("' -- 'gbkt-examples/**/*.kt' 'gbkt-*/**/*.kt'`
- Search command for the receiving builder pattern:
  `git grep -n 'fun.*zone(\|class ZoneBuilder' gbkt-lang/ gbkt-engine/`
- Reference delegate implementations to mirror:
  - `gbkt-lang/.../ActorBuilders.kt` — `ActorDelegate.provideDelegate`
  - `gbkt-lang/.../SceneBuilder.kt` — `SceneDelegate` shape
- Reference round-trip test to mirror:
  - `gbkt-lang/src/test/kotlin/.../ActorDelegateTest.kt`

## Related

- Phase 12.6 CONTEXT.md § Deferred Ideas — the original capture of this
  violation, with cross-reference to this seed
  (`.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/12.6-CONTEXT.md`)
- `feedback_no_magic_strings` (user memory) — Project Rule #1, the source of
  this seed's rationale
- `gbkt-engine/.../pickup/PickupBuilder.kt:229` — `zone(id: String, pickupId:
  String, ...)` is a SEPARATE `zone()` overload in the PickupBuilder
  namespace; it has the SAME magic-string violation pattern and should be
  audited together with the GameBuilder.zone migration (both call sites
  need the delegate-pattern treatment, or PickupBuilder.zone needs renaming
  + delegate treatment).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt:65` —
  `class ZoneBuilder(private val id: String)` — receives the id today;
  after migration the id flows in via `provideDelegate` like other builders.
