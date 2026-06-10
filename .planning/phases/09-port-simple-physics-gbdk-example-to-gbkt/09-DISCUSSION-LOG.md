# Phase 9: Port simple_physics GBDK example to gbkt — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-13
**Phase:** 09-port-simple-physics-gbdk-example-to-gbkt
**Areas discussed:** UAT contract floor, Named codegen bug-fix, Idiomatic mapping, Three-signal comparison artifact, Anti-overfitting doctrine

---

## Anti-overfitting doctrine (overarching guardrail, raised by user)

| Option | Description | Selected |
|--------|-------------|----------|
| Don't add DSL features just to make this port pretty | If the port surfaces a missing DSL primitive, bar is HIGH — must be defensible across future ports. | |
| Don't tune codegen visitors to this example's shape | Cosmetic emission tuning rejected; only real classes of bugs warrant the named fix. | |
| Don't let GBDK reference style become THE gbkt style | Reference is codegen oracle, not DSL style template. | |
| All three of the above | All three concerns apply together as overarching doctrine for the reference-port track. | ✓ |

**User's choice:** All three rails apply as overarching doctrine for Phases 9–12.
**Notes:** User raised this area unprompted as a 5th gray area beyond my initial 4. Locked into CONTEXT.md as D-overfitting-1/2/3 with explicit "applies to Phases 10/11/12, downstream planner inherits".

---

## UAT contract floor

| Option | Description | Selected |
|--------|-------------|----------|
| Tight: 3 core behaviors only | D-pad accel+clamp, A-press jump, decel-to-rest. Exercises i16 + signed comparison + literal interop. | ✓ |
| Medium: 5 behaviors including position/timing | Adds sub-pixel→pixel translation, exact decel-to-zero frame count. Couples to reference numerics. | |
| Exhaustive: pixel-and-frame parity with reference | Same constants, same trajectory frame-by-frame. Tightest oracle but breaks on intentional divergence. | |

**User's choice:** Tight — 3 core behaviors only.
**Notes:** Locked as D-01 in CONTEXT.md. The 3 behaviors cover the codegen surface that matters; pixel-perfect parity would create false brittleness if gbkt's lowering intentionally diverges.

---

| Option | Description | Selected |
|--------|-------------|----------|
| MCP play-through + screenshot per behavior | 3 MCP probes with input script + variable assertion + screenshot at climax. Satisfies visual-evidence rule. | ✓ |
| Variable-state only (no screenshots) | Read variables after scripted input. Fast but insufficient by CLAUDE.md visual-evidence rule. | |
| Per-behavior emulator probe class (no MCP) | JVM-tier `*Probe` classes via StepAgent. Mirrors Phase 07.9 shape. | |

**User's choice:** MCP play-through + screenshot per behavior.
**Notes:** Locked as D-02. Phase 07.4 plan 18 burned 5 plans on the variable-only gap — do not repeat. Screenshot at climax frame is mandatory.

---

| Option | Description | Selected |
|--------|-------------|----------|
| UAT first — lock contract before any DSL | Plan 1 = lock UAT.md + PLAYBOOK.md with no DSL yet. Forces scope cap. | ✓ |
| Interleaved — port a slice, write its UAT, repeat | Faster feedback but UAT scope drifts to match what was implemented. | |
| UAT after port, codegen-shape tests first | UAT becomes confirmation not contract. Weaker oracle property. | |

**User's choice:** UAT first.
**Notes:** Locked as D-03. Mirrors ROADMAP's "write per-example UAT first" phrasing.

---

## Named codegen bug-fix

| Option | Description | Selected |
|--------|-------------|----------|
| Exploratory — port first, name the bug after first build | Reference output drives discovery, not pre-committed hypothesis. | ✓ |
| Hypothesis: i16 + signed-comparison ergonomics | Concrete but pre-commits. Verifies 07.9 on tiny new surface. | |
| Hypothesis: sub-pixel / fixed-point at the actor level | Possible FP12.4 gap or i16Var compound-assign gap. | |
| Hypothesis: ROM-size or scene-less main loop | Scene overhead bloats ROM vs reference, or empty-scene path needed. | |

**User's choice:** Exploratory.
**Notes:** Locked as D-04. Stays honest to ROADMAP's "codegen oracle" framing — port first, surface the bug from reference comparison.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Seeds via `/gsd-capture --seed` | Each surplus defect → seed file with repro recipe + blast-radius hint. | |
| Seeds + a single rolled-up 'follow-up phase' entry in ROADMAP | Same as above + insert Phase 9.1 placeholder at port-close. | ✓ |
| Just commit-message captures | Surplus called out in commit body. Loses retrievability via `/gsd-progress`. | |

**User's choice:** Seeds + conditional ROADMAP placeholder.
**Notes:** Locked as D-05. User picked the more visible option over my "seeds-only" recommendation. Placeholder is CONDITIONAL on ≥1 surplus seed surfacing at port-close — no preemptive bureaucracy if zero surplus appears.

---

## Idiomatic mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Single 'play' scene, no title | One scene with enter + frame. Closest to reference shape while idiomatic. | ✓ |
| Title → play scene flow | Adds gameFlow + navigation. Muddies codegen comparison. | |
| Scene-less main loop | Would require new DSL primitive. Violates anti-overfitting rail 1. | |

**User's choice:** Single play scene, no title.
**Notes:** Locked as D-06.

---

| Option | Description | Selected |
|--------|-------------|----------|
| PNG asset via `asset("sprites/smiley.png")` | Mirror asset pipeline used by every other gbkt example. | ✓ |
| Inline tile-data DSL primitive | New `inlineTileData(...)` to mirror reference shape. Anti-overfitting rail 1 violation. | |
| Use an existing sprite from another example | Reuse Pong paddle / Breakout ball. Loses realism. | |

**User's choice:** PNG asset.
**Notes:** Locked as D-07. Generated C will include the equivalent tile-data array — that's the point.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Raw `i16Var` + manual `shr 4` mirroring reference | Validates i16Var + signed comparison + shr on tiny surface. | ✓ |
| Use actor FP88 (built-in 8.8 fixed-point) | 8.8 diverges from reference's 12.4 — apples-to-oranges trajectory. | |
| Add FP12.4 mode to actor builder | Anti-overfitting rail 1 violation unless follow-on ports also benefit. | |

**User's choice:** Raw i16Var + manual shr 4.
**Notes:** Locked as D-08. If `actor.moveTo()` doesn't accept Expr args, that gap becomes a candidate named-bug or seed.

---

## Three-signal comparison artifact

| Option | Description | Selected |
|--------|-------------|----------|
| ROM size + generated-C diff + UAT verdict | Three concrete artifacts covering codegen-quality + DSL-value + UAT-contract signals. | ✓ |
| Above + lcc .asm output diff | Catches instruction-level drift. Fragile to SDCC version drift. | |
| Above + bank/section sizes from .map and .noi | Useful for Phase 11 banks port; overkill for simple_physics. | |

**User's choice:** ROM size + generated-C diff + UAT verdict.
**Notes:** Locked as D-09.

---

| Option | Description | Selected |
|--------|-------------|----------|
| `.planning/phases/09-.../evidence/reference/` + gitignore binaries | Reference source + BUILD.md + comparison report committed; binaries reproducible. | ✓ |
| Commit everything including reference .gb and listings | Bypass local rebuild. Adds binary noise to git. | |
| New top-level `references/gbdk-examples/` directory | Speculative pre-build structure for Phases 10+. Don't anticipate. | |

**User's choice:** Phase evidence/reference/ + gitignore binaries.
**Notes:** Locked as D-10. Mirrors existing Phase 07.4 / 07.9 evidence discipline.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Lock 3 emission invariants matching the 3 UAT behaviors | One JVM test per behavior asserting generated C shape. | ✓ |
| Lock the full generated main.c against a golden snapshot | Strong but brittle to unrelated changes. | |
| Just spot-grep counts | Lightweight but masks per-scope regressions (CLAUDE.md grep-gate corollary). | |

**User's choice:** 3 emission invariants matching 3 UAT behaviors.
**Notes:** Locked as D-11. Per-function invariants must use awk brace-walk per CLAUDE.md scope-level grep gate corollary.

---

## Wrap-up: anything else to discuss

| Option | Description | Selected |
|--------|-------------|----------|
| Lock and write CONTEXT.md | All 4 selected areas covered + anti-overfitting doctrine locked. | ✓ |
| Plan dependencies on Phase 7.9 | Already SHIPPED; could enumerate which deliverables. | |
| What gets deferred to per-port methodology | Doctrine generalization to Phases 10/11/12. | |
| Plan count / wave structure hint | Rough plan frame for planner. | |

**User's choice:** Lock and write CONTEXT.md.
**Notes:** Wrap-up options 2/3/4 either already covered in CONTEXT.md (Phase 7.9 refs in canonical_refs; doctrine generalization in `<specifics>`) or appropriately left to planner discretion.

---

## Claude's Discretion

- **Plan count / wave structure** — Rough frame is plan 1 = UAT lock, plan 2 = DSL port, plan 3 = build + first-blocker analysis, plan 4 = named bug-fix, plan 5 = three-signal comparison + close. Actual breakdown belongs in PLAN.md.
- **Phase 7.9 deliverable mapping** — Planner reads `gbkt-backend-gbdk/CLAUDE.md` § "Literal Emission Convention" directly; CONTEXT.md doesn't restate.
- **PNG asset specifics** — 8x8 4-frame sprite (mirroring reference's 4 face variants). Whether all 4 frames are used is a discovery moment, not context-locked.

## Deferred Ideas

See `<deferred>` section of CONTEXT.md for the full list. Highlights: FP12.4 actor mode (seed only), inline tile-data DSL primitive (rejected outright per anti-overfitting rail 1), `.asm` diff oracle (deferred), bank/section size capture (wait for Phase 11), title screen, scene-less main loop primitive, Phase 9.1 placeholder pre-insertion (conditional only).
