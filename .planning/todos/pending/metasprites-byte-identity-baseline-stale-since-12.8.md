---
id: metasprites-byte-identity-baseline-stale-since-12.8
title: Regenerate metasprites/metasprites-stress sprite byte-identity baselines (stale since Phase 12.8)
created: 2026-06-03
source: phase-13.2-regression-gate
status: pending
priority: medium
resolves_phase: 19
scope: gbkt-examples/metasprites/src/test/resources/baseline/elephant.c.baseline
---

## Context

Pre-existing RED test surfaced (not introduced) by the Phase 13.2 regression gate.

`MetaspritesGeneratedSpriteByteIdentityTest > generated elephant_tiles c file is
byte-identical to pre-12-4 baseline` FAILS. **Proven pre-existing:** the same test fails at
the Phase 13.2 base commit `bf414e59` before any 13.2 change, and 13.2 touched no
asset-pipeline / baseline / PNG files.

Root cause: `-keep_palette_order` was pinned into the png2asset invocation in Phase **12.8**
(commit `16ddf5ce`, `ConvertZoneTilesetsTask`), but the `elephant.c.baseline` resource was last
regenerated in Phase **12.5** (commit `945670e1`) — before the flag existed. So current
generation emits a `-keep_palette_order`-bearing header comment that differs from the
pre-12.4 baseline (size 10270 vs 10453; first diff at offset 126 = the flag text). The
analogous `metasprites-stress` baseline mismatch was independently flagged in
`13.2-07` evidence (`evidence/d18-rom-regression-sweep.md`).

This is a baseline-staleness bookkeeping failure, not a codegen defect — the generated sprite
data is correct; the committed golden file is simply older than the current (correct)
invocation. Phase acceptance for codegen phases is `:buildRom` (all 8 targets GREEN), not these
stale byte-identity goldens.

## Fix

Regenerate the `elephant.c.baseline` (and the metasprites-stress equivalent) from current
`-keep_palette_order` output after confirming the generated C is otherwise correct, then commit
the refreshed goldens. Belongs in a Phase 12.x asset-pipeline closeout, not a DSL phase.
