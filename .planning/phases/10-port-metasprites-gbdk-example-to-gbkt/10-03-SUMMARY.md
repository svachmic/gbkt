---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: "03"
subsystem: gbkt-lang/dsl
tags: [metasprite, dsl, builder, delegate, tdd]
dependency_graph:
  requires: [10-02]
  provides: [MetaspriteBuilder.kt, MetaspriteRef, GameBuilder.registerMetasprite]
  affects: [gbkt-lang, gbkt-core, downstream consumers of GameIR.metasprites]
tech_stack:
  added: []
  patterns: [provideDelegate name-inference, @GbktDsl scope marker, GameBuilderContext thread-local]
key_files:
  created:
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilderTest.kt
  modified:
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt
decisions:
  - "MetaspriteDelegate calls builder.build() inside provideDelegate (not lazily in getValue) — validates at DSL construction time, matching ActorDelegate pattern exactly"
  - "MetaspriteRef.flipX/flipY/subPalette return ActorPropertyRef (existing type) — zero new operator code needed; all 60+ operator extensions transfer automatically"
  - "Validation (empty frames, empty tiles, negative tileId) uses require() in build() methods — consistent with ActorBuilder error style"
metrics:
  duration: 132s
  completed: "2026-05-18"
  tasks: 2
  files: 3
---

# Phase 10 Plan 03: MetaspriteBuilder DSL Summary

Metasprite DSL primitive added in gbkt-lang as a leaf addition. Users can now write `val elephant by metasprite { frame { tile(relX, relY, tileId) } }` inside a `game { }` block to declare variable-length OAM descriptor sprites that produce `MetaspriteIR` and flow into `GameIR.metasprites`.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| RED | Add failing MetaspriteBuilderTest | 295284ea | MetaspriteBuilderTest.kt |
| GREEN (T1+T2) | MetaspriteBuilder.kt + GameBuilder wiring | 14b2f42d | MetaspriteBuilder.kt, GameBuilder.kt |

## What Was Built

### MetaspriteBuilder.kt (new file)

`MetaspriteFrameBuilder` (@GbktDsl) — `tile(x, y, baseId)` records OAM entries; `build()` validates tiles.isNotEmpty() and all tileIds >= 0.

`MetaspriteBuilder` (@GbktDsl) — `frame { }` records frames; `build()` validates frames.isNotEmpty() and delegates to MetaspriteFrameBuilder.build() per frame.

`MetaspriteRef` — data class with `flipX`, `flipY`, `subPalette` computed properties returning `ActorPropertyRef`; pre-wired for Plan 08+09 visitor lowering.

`MetaspriteDelegate` — implements `ReadOnlyProperty`, exposes `provideDelegate`; captures property name, calls `GameBuilderContext.current?.registerMetasprite(ir)`.

`metasprite()` — top-level factory function returning `MetaspriteDelegate`.

### GameBuilder.kt (modified)

Added `import io.github.gbkt.core.ir.MetaspriteIR`, `_metaspriteIRs: MutableList<MetaspriteIR>` field, `registerMetasprite(ir: MetaspriteIR)` internal method, and `metasprites = _metaspriteIRs.toList()` in the `GameIR(...)` constructor call within `build()`.

## Verification

- `./gradlew :gbkt-lang:test` — BUILD SUCCESSFUL (all tests pass, no regressions)
- `./gradlew :gbkt-lang:compileKotlin :gbkt-backend-gbdk:compileKotlin` — BUILD SUCCESSFUL (no upstream breakage)
- All 7 MetaspriteBuilderTest behaviors pass

## Deviations from Plan

None — plan executed exactly as written. TDD RED/GREEN cycle followed. No architectural changes needed.

## Known Stubs

None. The builder produces correct MetaspriteIR; no hardcoded empty values or placeholder text flows to any rendering surface (codegen/visitor not wired yet — that is intentional per plan scope).

## TDD Gate Compliance

- RED commit: 295284ea (`test(10-03): add failing MetaspriteBuilderTest`)
- GREEN commit: 14b2f42d (`feat(10-03): add MetaspriteBuilder DSL + GameBuilder wiring`)
- REFACTOR: not needed — code is clean as written

## Self-Check: PASSED
