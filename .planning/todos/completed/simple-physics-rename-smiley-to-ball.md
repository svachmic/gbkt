---
id: simple-physics-rename-smiley-to-ball
title: Rename smiley → ball across simple-physics module
created: 2026-05-18
source: phase-09.3-uat-checkpoint
status: completed
completed: 2026-05-18
resolves_phase: "09.4"
priority: medium
scope: gbkt-examples/simple-physics
---

## Context

Surfaced during Phase 09.3-04 Visual Evidence Rule sign-off (2026-05-18). The
sprite shown in the simple-physics example is literally a ball PNG — the asset
`gbkt-examples/simple-physics/assets/sprites/smiley.png` was copied verbatim
from `gbkt-examples/breakout/res/sprites/ball.png` (see CLAUDE.md L84-85 which
self-documents this) with the intent to swap in a real smiley later. The
"smiley" naming is therefore misleading throughout the module — at code, test,
docs, and JSON-sidecar tiers.

User decision at Phase 09.3-04 UAT checkpoint: physics evidence (JUMP oracle
correction) is PASS; ship 09.3 and route this rename as follow-up.

## Footprint

| Tier | File | Refs |
|------|------|------|
| Asset | `gbkt-examples/simple-physics/assets/sprites/smiley.png` | 1 (rename to `ball.png`) |
| Kotlin DSL | `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt` | 4 (`val smiley`, sprite asset path, KDoc L35, `smiley.moveTo` L124) |
| Tests | `.../src/test/kotlin/.../SimplePhysicsIRTest.kt` | 6 (KDoc + actor-id assertions on `"smiley"`) |
| Tests | `.../src/test/kotlin/.../SimplePhysicsUatTest.kt` | 4 comments + `_smiley_x`/`_smiley_y` JSON sidecar variable names |
| Docs | `gbkt-examples/simple-physics/CLAUDE.md` | 5 refs incl. L84-85 self-documenting note |
| Docs | `gbkt-examples/simple-physics/README.md` | TBD (re-scan) |
| Docs | `gbkt-examples/simple-physics/PLAYBOOK.md` | TBD (re-scan) |

## Out of Scope

- Phase 09 historical artifacts (`.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/`) — frozen, do NOT rewrite.
- Phase 09.3 evidence artifacts that already cite generated-C symbol names containing `smiley` (e.g., `_smiley_x` in JSON sidecars produced before the rename) — those reflect the state of the code at the time the artifact was generated and should remain as-is unless a complete re-baseline is performed in the same plan.

## Suggested Routing

This is bounded to one example module with cross-tier scope (assets + Kotlin +
tests + docs + emulator sidecar names). Recommend a small follow-on phase
(09.4 or backlog) rather than inline-extending 09.3 post-ship. Discuss-phase
should explicitly decide whether to also re-baseline the UAT screenshot + JSON
sidecar with the new `_ball_x`/`_ball_y` variable names.

## Resolution Criteria

- All non-historical sites in the footprint above renamed
- Asset file renamed `smiley.png` → `ball.png` (or content swapped if a real smiley is desired instead)
- ROM rebuilds; full `:gbkt-examples:simple-physics:test` GREEN
- If UAT re-baseline included: new screenshot captured under a fresh evidence dir; old PNG remains untouched
- CLAUDE.md L84-85 self-documenting note either removed (if asset content matches name) or kept (if naming intent is to swap a real smiley in later)

## Resolution

Completed by Phase 09.4 (Wave 1–4):

- **Wave 1 (Plan 01):** `smiley.png` → `ball.png` via `git mv`; `val smiley` → `val ball` in SimplePhysics.kt (5 edit sites).
- **Wave 2 (Plan 02):** SimplePhysicsIRTest.kt — 7 assertion sites renamed to `"ball"`; EVIDENCE_DIR redirected from Phase 09.3 to Phase 09.4 path to protect frozen history.
- **Wave 3 (Plan 03):** README.md, CLAUDE.md, PLAYBOOK.md — all smiley actor references renamed to ball (9 edit sites total; Asset Source section deleted; L41 anti-overfitting carve-out preserved).
- **Wave 4 (Plan 04):** ROM smoke gate GREEN (32768 bytes); generated main.c smiley-zero; UAT re-baseline screenshots in `.planning/phases/09.4-.../evidence/uat-screenshots/`; JSON sidecar contains `ball_x`/`ball_y` (not `smiley_x`/`smiley_y`); module-wide grep-zero gate GREEN (phase-path-name occurrences in EVIDENCE_DIR string accepted per Plan 02 deviation).

All resolution criteria met. Phase 09.4 verification-complete.
