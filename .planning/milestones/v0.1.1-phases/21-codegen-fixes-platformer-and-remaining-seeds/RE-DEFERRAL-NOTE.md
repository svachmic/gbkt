# Phase 21 Re-deferral Evidence Note (D-04c)

**Date:** 2026-06-14
**Plan:** 21-06 (seed disposition — re-deferrals)
**Authority:** D-03 + D-04 (Phase 21 RESEARCH.md); Criterion 3 of the v0.1.1 milestone acceptance criteria ("re-deferred with evidence" is a valid terminal disposition for TRIAGE-01/TRIAGE-03)

---

## Re-deferred Seeds

All four seeds listed below were moved from `.planning/seeds/` to `.planning/backlog/v0.2.0/` via `git mv`, preserving git history. Each received a one-line `> Re-deferred to v0.2.0 (Phase 21): <rationale>` header prepended to the file.

| Seed | Destination | Rationale |
|------|-------------|-----------|
| `SEED-017-sport-zone-tileset-pipeline-unification.md` | `.planning/backlog/v0.2.0/` | Moderate sport-genre dual-pipeline refactor; no shipping example exercises it heavily; INV-8 lock-test stays GREEN. Needs its own discuss/spec phase. |
| `SEED-023-whenever-runif-unification.md` | `.planning/backlog/v0.2.0/` | whenever→runIf DSL unification needs a full deprecation cycle; functional API already removed in Phase 18 DEPR-01 but removal can't ship until one release after deprecation lands. |
| `SEED-025-remove-deprecated-combat-string-overload.md` | `.planning/backlog/v0.2.0/` | combatIsInState(String,String) overload removal scheduled v0.2.0 by design — cannot remove until one release after its deprecation ships (v0.1.0 shipped the deprecated overload). |
| `SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.md` | `.planning/backlog/v0.2.0/` | Wide blast radius — touches gbkt-lang + gbkt-engine IR + every zone() call site across all games; needs its own discuss/spec phase, not a hardening-release close. |

---

## REQUIREMENTS.md Impact (D-04a)

FIX-06 disposition in REQUIREMENTS.md is updated (Plan 21-06 Task 2) to reflect:

- `SEED-017` → RE-DEFERRED v0.2.0 (not in Phase 21 active scope)
- `SEED-ZONE-MAGIC-STRING` → RE-DEFERRED v0.2.0 (not in Phase 21 active scope)
- `SEED-020` → FIXED (Phase 21, Plan 21-02/21-03)
- `SEED-022` → FIXED (Phase 21, Plan 21-01)

---

## Terminal Disposition Check (D-01 / TRIAGE-03)

After this plan completes, the remaining `.planning/seeds/` files are:

- `SEED-021-platformer-pivot-adjust-auto-derive.md` — FIXED in Phase 21 (Plan 21-01), will be archived
- `SEED-022-tilemap-collision-predicate-consolidation.md` — FIXED in Phase 21 (Plan 21-01), will be archived
- `SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md` — FIXED in Phase 21 (Plans 21-04/21-07), will be archived
- `SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md` — accepted-as-correct in Phase 21 (Plan 21-04/21-07), will be archived
- `SEED-platformer-template-spawn-polish.md` — FIXED in Phase 21 (Plans 21-04/21-07), will be archived

The `.planning/seeds/` directory will be emptied at Phase 21 milestone close (21-08), satisfying TRIAGE-03.
