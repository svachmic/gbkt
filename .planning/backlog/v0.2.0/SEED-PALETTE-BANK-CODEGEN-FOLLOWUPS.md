---
id: SEED-PALETTE-BANK-CODEGEN-FOLLOWUPS
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: low
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
source: 13.8-REVIEW.md / 13.8-VERIFICATION.md
area: gbkt-backend-gbdk (GBDKPipelineV2 / SceneVisitor) + gbkt-gradle-plugin (PngUtils)
original_priority: low
---

# Phase 13.8 palette/bank codegen follow-ups (advisory, non-blocking)

Three code-review warnings from Phase 13.8 were adjudicated by the phase verifier as
**acceptable deferred debt** — the phase goal (close 12.9/13.7 WR debt with zero visual
regression) was fully met (8/8 must-haves, byte-identity held, binding visual sign-off
approved). None affect current ROM output. Tracked here so they are not lost. Each is a
latent-hazard / robustness fix, not a current correctness defect.

## WR-01 — `SceneIR.allocatedZoneBank` first-zone population + short-circuited per-zone fallback
`GBDKPipelineV2.kt` populates `SceneIR.allocatedZoneBank` with only the **first** zone's
bank, and `SceneVisitor.kt` reads the field with a `?:` that short-circuits the per-zone
`zoneBankAllocation[zoneId]` lookup. For a multi-zone scene whose zones land in different
banks, every zone would emit the first zone's bank literal. **Currently inert** because
`SceneBuilder` hard-limits scenes to one zone. The load-bearing problem is the **comments**
at the read site and on the field that *claim* the fallback handles the multi-zone case —
these will mislead the future Phase-13 implementer of multi-zone-per-scene. Fix: either
make the field a per-zone map, or correct the comments to state the single-zone invariant
the code actually relies on.

## WR-02 — no slot-collision guard for `MetaspriteIR.initialSubPaletteSlot`
Two metasprites declaring the same `initialSubPaletteSlot` silently emit overlapping
`set_sprite_palette` slot writes. Add a validation pass (or codegen-time assertion) that
flags duplicate explicit slots within a scene.

## WR-03 — lenient RGB555 integer-fallback parsing in `PngUtils`
The RGB555 integer-fallback parse path accepts out-of-range values without a range check.
Add a 0..31 (per channel) / 0..0x7FFF bound and surface a clear error on violation.

**Routing note:** if/when multi-zone-per-scene is implemented in a future Phase 13 unit,
WR-01 MUST be addressed in that same unit (it is the structural prerequisite). WR-02/WR-03
are independent robustness items suitable for any palette-pipeline touch.
