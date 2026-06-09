# SEED: Phase 12 — Player Metasprite Render Path (placeholder square)

**Created:** 2026-05-23 (Phase 12.2 close — terminal close as `gaps_found`)
**Updated:** 2026-05-23 (after user clarified the per-image verdict — `02-gameplay.png` is correct: the player IS rendering, just as a placeholder square mid-jump)
**Origin phase:** 12.2 (ConvertZoneTilesetsTask real-tilemap extraction via png2asset -map mode)
**Source:** Phase 12.2 SPEC §"Out of scope" + Phase 12.2 Plan 10 closure
**Status:** Deferred — captured for a future inserted Phase 12.3 (or for Phase 12 resume to absorb). Note: this is NOT a load-bearing gap for Phase 12.2's verdict — the load-bearing gap is the title-zone render defect (see Related artifacts).
**Routing:** Open; not yet bound to a target phase
**Blast radius:** Moderate (touches `gbkt-backend-gbdk` MetaspriteVisitor + actor draw path + OAM allocation)

## Context

Plan 12-19's anchor1 UAT now visibly shows the player metasprite in `02-gameplay.png`
as a placeholder SQUARE (mid-jump above the ground row), not as the intended player
sprite (animated character with the platformer template's art). Phase 12.2 was scoped
at the tilemap-data layer (Defect 7 — synthetic modulo-tiled fallback); it intentionally
did NOT touch the metasprite render path. Plan 12.2-10's re-shot `02-gameplay.png`
confirms that **the tilemap render is now correct** (Path B gameplay rendering matches
the upper-left of `world1-area1.png` per the user-approved human-verify checkpoint), and
that the player metasprite IS being rendered and positioned correctly — but it's still
the placeholder square, not the platformer template's intended player art.

## What's Deferred

Investigation + fix of why the player metasprite renders as a placeholder square rather
than the platformer template's intended player art. Likely a combination of:

- `_posX/_posY` magic-name expectation in MetaspriteVisitor (Phase 12-18 Defect 5
  follow-on — Phase 12.1 covered the GBDK-backed metasprite_id register convention but
  may have left the actor-side art binding unwired).
- Player actor's sprite asset declared in DSL but the asset → metasprite-frame binding
  missing in codegen (so the metasprite registers exist + position correctly, but the
  tile data behind them is a placeholder pattern).
- OAM allocation looks plausible — `02-gameplay.json` sidecar shows `render_shadow_OAM=192`
  and `shadow_OAM=80` at frame 155 (positive evidence that the OAM water mark is
  advancing for the player, consistent with the placeholder square being visible at
  mid-jump position).

## Revival Condition

After the load-bearing title-zone render defect (separate SEED) is closed, re-run the
`PlatformerTemplateUatTest.anchor1Title_to_Gameplay` UAT:

- If `02-gameplay.png` shows the player as a recognisable platformer character (NOT a
  placeholder square), close this seed with the evidence PNG.
- If the player STILL renders as a placeholder square, route to a new inserted phase
  via `/gsd:phase --insert` parented under 12 (e.g. Phase 12.4) for the metasprite-art
  binding fix.

## Investigation entry points

- `gbkt-backend-gbdk/.../codegen/visitor/MetaspriteVisitor.kt` — metasprite emission +
  `_posX/_posY` symbol convention from Phase 12-18 Defect 5.
- `gbkt-backend-gbdk/.../codegen/visitor/ActorVisitor.kt` — per-frame actor draw glue.
- `gbkt-examples/platformer-template/src/main/kotlin/.../PlatformerTemplate.kt` — `player`
  actor declaration; verify the DSL actually wires a `moveMetasprite()` / equivalent
  per-frame call.

## Related artifacts

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/02-gameplay.png` — current ground truth (player rendering as a placeholder square mid-jump; gameplay tilemap correctly shows world1-area1 upper-left)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/02-gameplay.json` — variable sidecar (frame 155; `render_shadow_OAM=192`, `shadow_OAM=80` — OAM water mark advancing)
- Phase 12-18 Defect 5 evidence in `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/`
- Phase 12.1 SUMMARYs (partial Defect 5 closure)
- `.planning/seeds/SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT.md` — the load-bearing title-zone Path A render gap that has to close first (gameplay Path B renders correctly post-12.2, including this placeholder square mid-jump on `02-gameplay.png`)
