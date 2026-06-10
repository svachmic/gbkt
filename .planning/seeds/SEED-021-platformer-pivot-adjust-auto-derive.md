# SEED-021 — Auto-derive `pivot_adjust` in the `tilemapCollision { }` builder

**Origin:** SonarCloud Info-issue sweep of PR #33 (`feat/d_and_d_gaps`), 2026-06-10. Supersedes the never-created `SEED-PHASE-13-PIVOT-ADJUST-AUTO-DERIVE.md` that two `PlatformerVisitor` comments cited (the file was referenced in Plan 12.7-19 commentary but never written).
**Status:** Open — not yet bound to a target phase
**Routing:** Platformer-genre DSL/codegen phase (discuss-phase first). Touches the DSL builder + visitor contract, so it is a phase-sized change, not an inline patch.
**Blast radius:** `gbkt-genre-platformer` (`PlatformerVisitor`, `tilemapCollision { }` builder / `PlatformerPhysicsConfig`), platformer-template example regression (byte-identity or UAT reshoot — pivot offsets are a *visual* truth, see `.planning/verifier-gates.md`).

## Problem

The platformer tilemap-physics codegen needs a `pivot_adjust` Y-correction so the
rendered sprite doesn't overlay the top 2 px of the ground tile (user UAT
2026-05-26 anchor-2 report; fixed in Plan 12.7-19, Round-5 H1). Today the visitor
resolves it via a "metasprite lookup dance":

- `PlatformerVisitor.buildTilemapPhysicsUpdateFunction` matches
  `gameIR.metasprites` against the bound `posYSym` to derive the pivot from the
  metasprite geometry, with documented fallback constants locked verbatim to the
  platformer-template's reference geometry (companion constants near
  `PlatformerVisitor.kt:615`).
- The user DSL (`tilemapCollision { }`) never sees or owns this value.

So the single source of truth is split between IR-side metasprite geometry and
visitor-side fallback constants — fragile when a game binds a metasprite whose
geometry differs from the template's.

## Goal

Lift `pivot_adjust` resolution into the `tilemapCollision { }` builder so the
user DSL is the single source of truth: the builder reads the bound metasprite
directly (or accepts an explicit override), and the visitor consumes a resolved
config value. The metasprite lookup dance and the verbatim fallback constants in
`PlatformerVisitor` disappear.

## Scope sketch (for the discuss-phase)

1. `tilemapCollision { }` learns to read the bound metasprite (the GenericSystem
   config layer gains the resolved value, e.g. `pivotAdjust` next to `posYVar`).
2. Per Project Rule #1 (no magic strings), the binding must flow through DSL
   property names / typed refs — not a hardcoded `"player"` id.
3. Delete the visitor-side derivation + fallback companion constants; keep a
   validation diagnostic for "tilemapCollision bound but no metasprite resolvable".
4. Regression gate: platformer-template `buildRom` + runtime screenshot of the
   grounded player (Visual Evidence Rule — this fixed a 2 px visual defect; a
   variable assertion is not sufficient).

## Discovery hooks

- `PlatformerVisitor.kt` — `Deferred (SEED-021)` markers at the call-site
  resolution (~line 615-625) and on the `buildTilemapPhysicsUpdateFunction` KDoc
  (~line 1283-1293).
- Plan 12.7-19 / evidence/round-5-diagnostic.md Section 2 — origin of the
  pivot_adjust correction.
- [[SEED-022]] — the same visitor carries the duplicated
  `gameUsesTilemapCollision` predicate; a phase touching this area could absorb
  both seeds.
