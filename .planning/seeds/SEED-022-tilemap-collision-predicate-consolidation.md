# SEED-022 — Consolidate the duplicated `gameUsesTilemapCollision` predicate

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-022](.planning/phases/16-seed-triage/TRIAGE.md#SEED-022) · 2026-06-12

**Origin:** SonarCloud Info-issue sweep of PR #33 (`feat/d_and_d_gaps`), 2026-06-10
**Status:** Open — not yet bound to a target phase
**Routing:** Small contained refactor plan — can ride inside any phase that already touches `gbkt-backend-gbdk` ↔ genre-module layering, or pair with [[SEED-021]] (same visitor).
**Blast radius:** `gbkt-genre-platformer/.../PlatformerVisitor.kt`, `gbkt-backend-gbdk/.../GBDKPipeline`, plus wherever the shared utility lands (likely `gbkt-backend-api`, the only module both depend on).

## Problem

The predicate `gameUsesTilemapCollision(gameIR)` exists twice, by design but
fragilely (Plan 12-08):

- `PlatformerVisitor.gameUsesTilemapCollision` (~line 1656) — direct
  compile-time access to `PlatformerPhysicsConfig.solidThreshold`, plus the
  GenericSystem `"solidThreshold"` config-key path.
- `GBDKPipeline.gameUsesTilemapCollision` — same logic via **reflection**,
  because `gbkt-backend-gbdk` does not depend on the platformer genre module.

The two implementations MUST stay in lockstep (documented in both KDocs); any
drift silently desyncs which C globals get emitted (`_camera_x`,
`_old_camera_x`, `_map_pos_x`, … — see `visitCamera` ~line 1585) from the
physics code that reads them. Nothing enforces the lockstep today.

## Goal

One shared predicate (working name `TilemapCollisionGate`) consumed by both the
platformer visitor and the GBDK pipeline, with the reflection hack confined to —
or eliminated by — the shared utility. Until the cross-genre / cross-backend
pattern stabilises, an acceptable interim is a contract test that runs both
predicates over a fixture matrix and asserts identical verdicts.

## Scope sketch (for the discuss-phase)

1. Decide the home: `gbkt-backend-api` hosts `GenreSystemVisitor`, so a
   capability-style hook there ("does this system require tilemap-collision
   globals?") lets each genre answer for itself — no reflection, no
   backend→genre dependency.
2. Alternative cheap v1: keep both call sites but extract the GenericSystem
   config-key path (the duplicated half) into a shared helper; only the typed
   `PlatformerPhysicsConfig` arm stays genre-local.
3. Lockstep contract test as the regression net either way.
4. Smoke gate per `feedback_rom_build_smoke_test_for_codegen_phases`: clean
   `:gbkt-examples:platformer-template:buildRom`.

## Discovery hooks

- `PlatformerVisitor.kt` — `Deferred (SEED-022)` marker in `visitCamera`
  (~line 1585) and the lockstep warning in the `gameUsesTilemapCollision` KDoc
  (~line 1650-1657).
- `GBDKPipeline.gameUsesTilemapCollision` — the reflection twin (Plan 12-08).
- [[SEED-021]] — sibling debt in the same visitor.
