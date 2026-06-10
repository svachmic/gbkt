# SEED: Phase 12 — Retroactive Banks Audit

**Created:** 2026-05-23 (Phase 12.2 close — terminal close as `gaps_found`)
**Origin phase:** 12.2 (ConvertZoneTilesetsTask real-tilemap extraction via png2asset -map mode)
**Source:** Phase 12.2 SPEC §"Out of scope"
**Status:** Trivially satisfied by D-01 — no action required unless Banks adopts tilemap rendering
**Routing:** Open; no future phase required at this time
**Blast radius:** Zero today; small (Banks DSL + smoke-test) if revival fires

## Context

Banks Phase 11's `play_zone` does not call `_bkg_tiles_load_banked` from
`setup_current_level`. The synthetic-tilemap defect that Phase 12.2 closed (Defect 7)
never affected Banks at runtime because Banks bypasses the broken codepath entirely.

Phase 12.2's D-01 (Path A one-invocation form — locked during `/gsd:discuss-phase 12.2`)
gives Banks correct tilemap output WITHOUT any DSL edits — the existing
`tileset(asset("tiles/checker.png"))` declaration is automatically treated as both
tileset AND tilemap source (when no separate `tilemap()` is set).

The 5-ROM regression sweep (Plan 12.2-09) confirmed Banks's `play_zone` now emits a
correct 2×2 tilemap (4 bytes, real `_tileset_map[]` from checker.png) — up from the
1024-byte synthetic modulo-tiled ramp that pre-12.2 emitted. Banks built GREEN.

## What's Deferred

Retroactive verification + retrofit if/when Banks (or any game previously bypassing the
tilemap codepath) adopts tilemap rendering in a future phase.

Until then, no action is required: D-01 Path A already produces correct output for
Banks's existing DSL surface.

## Revival Condition

Banks (or any game) modifies its DSL to call `_bkg_tiles_load_banked` (typically via
exploration system, scene tilemap rendering, or a new gameplay feature) AND the
resulting tilemap rendering is incorrect.

Symptom that fires this seed: a fresh UAT screenshot of the modified game shows
wrong tilemap output (synthetic-looking patterns, modulo-wrap artifacts, or
WIDTH/HEIGHT mismatches).

## Related artifacts

- Phase 11.1-17 (original synthesizer introduction — the antipattern that was removed
  in Phase 12.2)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-CONTEXT.md` §D-01 (semantic resolution)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/evidence/regression-sweep.md` — Banks 5-ROM sweep result (4 bytes via Path A)
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/` — Banks parent phase
