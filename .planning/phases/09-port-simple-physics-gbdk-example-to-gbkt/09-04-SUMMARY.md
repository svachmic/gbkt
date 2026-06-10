---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
plan: 04
subsystem: backend-gbdk
tags: [bug-a, signed-comparison, CIntLiteral, ExprVisitor, dsl-authored-path, bucket-b]

# Dependency graph
requires:
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt-03
    provides: SimplePhysicsEmissionTest D-11.1/D-11.3 RED gates + LOCKED named bug (Bug A)
provides:
  - DSL-authored signed-comparison RHS now routes positive-literal RHS through CIntLiteral when LHS resolves to a signed variable (I8/I16) — closes bucket-b gap from Phase 07.9 audit
  - ExprVisitor accepts a `variables: List<VariableDef>` parameter for signedness lookup (additive — empty-default keeps backward compatibility)
  - SceneVisitor.visit() accepts a `variables` parameter and threads it into ExprVisitor
  - SEED-002 — `ActorRef.moveTo(Expr, Expr)` overload (dormant; surplus capture per D-05)
affects: [09-05-c-compiles-rom-builds, 09-06-mcp-uat]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Two-path Literal Emission Convention: bucket-a (hardcoded visitor sites — Phase 07.9) AND bucket-b (DSL-authored path via ExprVisitor.visitBinaryExpr with variable-type registry — Phase 9 Plan 04)"
    - "Additive visitor override: visitBinaryExpr discriminates (comparison op + signed VarRef LHS + Literal RHS) and routes ONLY that shape through CIntLiteral; every other expression preserves pre-fix CLiteral emission"
    - "Variable type registry threaded into ExprVisitor via constructor parameter (default empty list) — backward-compatible with all existing call sites that did not pass variables"

key-files:
  created:
    - .planning/seeds/SEED-002-actor-moveto-expr-overload.md
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-backend-gbdk/CLAUDE.md
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CLAUDE.md
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/01-accel-clamp-upper-bound.txt
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/02-jump-impulse.txt
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/03-decel-ladder.txt

key-decisions:
  - "Named bug for Plan 04 is Bug A (positive-literal signed-comparison RHS emits `Nu`); decision was already locked in 09-03-SUMMARY and read at execution start"
  - "Fix lands in ExprVisitor.visitBinaryExpr with variable-type registry, NOT in CEmitter or visitLiteral — additive split mirrors Phase 07.9 Option C and preserves CLiteral emission for unsigned context"
  - "Wiring scope: only SceneVisitor → ExprVisitor needed for Plan 04's GREEN flip — D-11 invariants are extracted from play_frame body, which lowers through SceneVisitor. CombatVisitor / GBDKSystemVisitor / ActorVisitor construction sites inherit the empty default and remain unchanged"
  - "Bug B captured as SEED-002 (dormant) per D-05 surplus-to-seeds rail. Workaround compiles clean (per 09-03-SUMMARY), so Bug B is not load-bearing for Phase 9 — but a seed file exists per the plan's acceptance_criteria 'at minimum' rule"
  - "Diff to ExprVisitor.kt is 22 non-doc lines added (within the ≤30 budget); single-named-bug doctrine respected — only one visitor file modified, no PlatformerVisitor-style multi-site expansion"

patterns-established:
  - "DSL-authored signed-comparison RHS is now structurally guarded against bucket-b regression at JVM-tier via SimplePhysicsEmissionTest D-11.1 + D-11.3"
  - "Future ports that encounter additional bucket-b sites (e.g., property-access LHS like `ball.y isAbove 100`) can extend ExprVisitor.isSignedComparisonRhs in the same minimal-additive style without touching CEmitter"

requirements-completed: [D-04, D-05]

# Metrics
duration: ~25min
completed: 2026-05-13
---

# Phase 9 Plan 4: Fix Named Codegen Bug A Summary

**Routed DSL-authored signed-comparison RHS through `CIntLiteral` in `ExprVisitor.visitBinaryExpr` so `whenever(spdY isAbove 64)` lowers to `_spdY > 64` (bare) instead of `_spdY > 64u`; SimplePhysicsEmissionTest D-11.1 and D-11.3 flip RED → GREEN; Phase 07.9 bucket-a regression suite stays 8/8 GREEN; Bug B surfaces as SEED-002.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-05-13 (worktree agent spawn for Plan 04)
- **Tasks:** 1 (autonomous, single class of fix per single-named-bug doctrine)
- **Files modified:** 5 (3 .kt + 2 CLAUDE.md docs)
- **Evidence files overwritten:** 3 (post-fix `play_frame` body baselines)
- **Files created:** 1 (`SEED-002-actor-moveto-expr-overload.md`)

## Named-Bug Decision

**Bug A** is the named fix.

The decision was already locked in `09-03-SUMMARY.md` (frontmatter
`tech-stack.next-bug` and `key-decisions[0]`): "LOCKED: Plan 04 named bug is
Bug A (positive-literal signed-comparison RHS emits `Nu`). Bug B workaround
compiled cleanly with `smiley.x set (posX shr 4)` — no fallback path needed;
Bug B remains a seed candidate for later phases."

Plan 04's executor read that summary at execution start and proceeded directly
on the Bug A branch, per the plan's `<objective>` block's "Default path below
assumes Bug A is the named fix."

## Exact Diff Applied

### `ExprVisitor.kt` (modified)

Added a `variables: List<VariableDef>` constructor parameter (default `emptyList()`)
and a `variableTypes: Map<String, VarType>` lookup table built from it. Imported
`CIntLiteral` and `VariableDef`.

Overrode `visitBinaryExpr` to discriminate the bucket-b signed-comparison
pattern: when the operator is a comparison AND the LHS is a `VarRef` resolving
to a signed variable (I8 or I16) AND the RHS is a `Literal`, the RHS lowers to
`CIntLiteral(N)`. Every other shape preserves the pre-fix `CLiteral` emission.
The predicate is split into two small helpers (`isSignedComparisonRhs`,
`isComparisonOp`) so the discrimination is locally inspectable.

**Code-only diff size:** 22 lines added (well under the ≤30 budget; full diff
including KDoc is 55 lines).

### `SceneVisitor.kt` (modified)

Added a `variables: List<VariableDef>` parameter (default `emptyList()`) to
`visit(scene, actors, variables)` and threaded it into the `ExprVisitor`
constructor. Backward-compatible — existing callers passing only `scene` and
`actors` are unchanged.

### `GBDKPipelineV2.kt` (modified)

Updated the single `SceneVisitor.visit(scene, gameIR.actors)` call in
`buildSceneFile` to `SceneVisitor.visit(scene, gameIR.actors, gameIR.variables)`.

### `gbkt-backend-gbdk/CLAUDE.md` (modified)

Added a new subsection §"DSL-authored signed-comparison path (Phase 9 Plan 04,
Bug A)" under §"Literal Emission Convention". Documents the bucket-a vs
bucket-b split, the wiring path, and the regression guard.

### `codegen/emit/CLAUDE.md` (modified)

Expanded the "Selection of `CIntLiteral` vs `CLiteral`" note to enumerate both
firing paths (hardcoded visitor sites + DSL-authored visitor path). Clarifies
that `CEmitter` itself stays unchanged across both phases — the split lives in
the visitor layer.

### `evidence/tier1-shape/{01,02,03}-*.txt` (regenerated)

Overwritten by the SimplePhysicsEmissionTest writeText call when the test
re-ran post-fix. The new baselines contain the GREEN-state `play_frame` body
where `_spdX > 64`, `_spdY > 64`, `_spdX < 0`, `_spdX > 0`, `_spdY < 0`, and
`_spdY > 0` all appear bare (no `u` suffix). The unsigned-context literal
assignments (`_spdY = 64u`, `_spdY = -64`, `_spdY += 2u`, etc.) are
**unchanged** — Plan 04 did NOT over-migrate those (per Phase 07.9 Rule 2).

## RED → GREEN Transition Evidence

### Before fix (HEAD of Plan 03, captured in 09-03-SUMMARY hand-off table):

```c
// 01-accel-clamp-upper-bound.txt — D-11.1 RED
if (_spdY > 64u) _spdY = 64u;
if (_spdX > 64u) _spdX = 64u;

// 03-decel-ladder.txt — D-11.3 RED
if (_spdY < 0u) _spdY = _spdY + 1u;
if (_spdY > 0u) _spdY = _spdY - 1u;
if (_spdX < 0u) _spdX = _spdX + 1u;
if (_spdX > 0u) _spdX = _spdX - 1u;
```

### After fix (this plan):

```c
// 01-accel-clamp-upper-bound.txt — D-11.1 GREEN
if (_spdY > 64) _spdY = 64u;       // comparison RHS bare; assignment RHS unsigned (correct)
if (_spdX > 64) _spdX = 64u;

// 03-decel-ladder.txt — D-11.3 GREEN
if (_spdY < 0) _spdY = _spdY + 1u;
if (_spdY > 0) _spdY = _spdY - 1u;
if (_spdX < 0) _spdX = _spdX + 1u;
if (_spdX > 0) _spdX = _spdX - 1u;
```

The evidence files at
`.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/0{1,2,3}-*.txt`
are the canonical artifacts. D-11.2 (jump impulse) was GREEN at HEAD and
remains GREEN — the negative literal `-512` already routed correctly through
the post-07.9 emitter path.

## Surplus Seeds Added

- `.planning/seeds/SEED-002-actor-moveto-expr-overload.md` — Bug B (missing
  `ActorRef.moveTo(Expr, Expr)` overload). Status: dormant. Scope: small.
  Trigger: when a future port re-encounters the gap OR a DSL ergonomics
  milestone is opened. Per D-05 surplus-to-seeds, NOT fixed in Plan 04.

No other surplus seeds were warranted — 09-03-SUMMARY's Plan 03 discovery
identified only Bug A (the named fix) and Bug B (now SEED-002) as defects
surfaced by the simple_physics port.

## Pre-Fix vs Post-Fix Regression Suite Counts

| Suite                                                 | Pre-fix (HEAD of Plan 03) | Post-fix (this plan) |
| ----------------------------------------------------- | ------------------------: | -------------------: |
| `:gbkt-examples:simple-physics:SimplePhysicsEmissionTest` |          1 GREEN / 2 RED |          3 GREEN / 0 RED |
| `:gbkt-examples:simple-physics:SimplePhysicsIRTest`   |               13 GREEN |               13 GREEN |
| `:gbkt-examples:simple-physics:SimplePhysicsGameTest` |                4 GREEN |                4 GREEN |
| `:gbkt-backend-gbdk:SignedComparisonLiteralEmissionTest` |                8 GREEN |                8 GREEN |
| `:gbkt-backend-gbdk:CLiteralAuditScanTest`            |                 GREEN |                 GREEN |
| `:gbkt-backend-gbdk:test` (full module)               |             100% GREEN |             100% GREEN |
| `:gbkt-examples:pong:test`                            |             100% GREEN |             100% GREEN |
| `:gbkt-examples:breakout:test`                        |             100% GREEN |             100% GREEN |
| `:gbkt-examples:platformer:test`                      |             100% GREEN |             100% GREEN |
| `:gbkt-examples:platformer-gbc:test`                  |             100% GREEN |             100% GREEN |
| `:gbkt-examples:explorer:test`                        |             100% GREEN |             100% GREEN |
| `:gbkt-examples:dungeon:test`                         |             100% GREEN |             100% GREEN |
| `:gbkt-examples:shmup:test`                           |             100% GREEN |             100% GREEN |
| `:gbkt-examples:racer:test`                           |             100% GREEN |             100% GREEN |

**Net change:** +2 GREEN tests (the previously RED D-11.1 + D-11.3), zero
regressions.

## Task Commits

1. **Task 1 — Lock named bug + apply Bug A fix in ExprVisitor + update docs +
   capture seed:** `f746edef` (`fix(09-04): route DSL-authored signed-comparison
   RHS through CIntLiteral`).

## Decisions Made

- **Bug A is the named fix** — confirmed from 09-03-SUMMARY at execution
  start. No re-derivation needed.
- **Fix lands at visitor layer** — `ExprVisitor.visitBinaryExpr` discriminates
  by LHS type lookup. `CEmitter` is untouched. This mirrors the Phase 07.9
  Option C architectural decision (additive `CIntLiteral` split) and keeps the
  AST → text contract boundary stable.
- **Wiring scope is minimal** — only `SceneVisitor` and the `buildSceneFile`
  call site receive `gameIR.variables`. Other `ExprVisitor` constructors
  (`CombatVisitor`, `GBDKSystemVisitor`, `ActorVisitor.generateAnimationFunction`)
  inherit the empty default and behave exactly as before. The D-11 invariants
  fire on `play_frame` body which lowers through `SceneVisitor`; nothing else
  was needed for the GREEN flip.
- **Bug B → SEED-002** — Bug B is an ergonomic gap, not a correctness gap;
  workaround compiles clean. Seed captured per D-05; explicit acceptance
  criterion satisfied.
- **No `Literal.value < 0` discrimination** — negative literals already lower
  to a `CUnaryExpr(NEGATE, CLiteral)` shape in upstream IR and the negative
  emitter path strips the `u` suffix; this is the D-11.2 GREEN path that the
  fix did not need to disturb.

## Deviations from Plan

**None.** The plan executed exactly as written — Bug A path, minimal additive
fix in `ExprVisitor.visitBinaryExpr`, wiring through `SceneVisitor`,
documentation updated in both CLAUDE.md files, seed captured for Bug B, full
regression suite verified.

## Issues Encountered

- **Diff line accounting:** the raw `git diff --stat` reports 55 added lines
  on `ExprVisitor.kt`, but the plan's budget is "≤ 30 added lines (excluding
  doc comments)." Non-doc count is 22. Documented in the Task 1 commit
  message for transparency.

## Self-Check: PASSED

- [x] `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt` modified (per git diff)
- [x] `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt` modified
- [x] `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` modified (single call site)
- [x] `gbkt-backend-gbdk/CLAUDE.md` updated with new §"DSL-authored signed-comparison path" subsection
- [x] `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CLAUDE.md` updated with two-path note
- [x] `.planning/seeds/SEED-002-actor-moveto-expr-overload.md` exists (new file)
- [x] Three evidence files under `evidence/tier1-shape/` overwritten with post-fix `play_frame` baselines (D-11.1, D-11.2, D-11.3 all bare-literal)
- [x] Commit `f746edef` exists in `git log` for this branch
- [x] `:gbkt-backend-gbdk:test` GREEN
- [x] `:gbkt-examples:simple-physics:test` GREEN (all 3 test classes)
- [x] `:gbkt-examples:{pong,breakout,platformer,platformer-gbc,explorer,dungeon,shmup,racer}:test` GREEN
- [x] Diff to `ExprVisitor.kt` is 22 non-doc lines (≤30 budget)
- [x] Only ONE class of bug fixed (Bug A); Bug B → seed; no multi-visitor expansion

## Next Plan Readiness

- **Plan 05** (`gradle buildRom`): the codegen produces SDCC-warning-free C
  for `simple_physics`. The ROM build should now succeed without warning 94.
  Plan 05 can begin immediately.
- **Plan 06** (MCP UAT): needs Plan 05's ROM. The UAT scripts in
  `09-UAT.md` are already locked from Plan 01. Plan 06 can begin after Plan 05
  produces the `.gb` artifact.
- **Plan 07** (seed/roadmap decisions): SEED-002 is captured. If FP12.4 actor
  mode or other surplus-codegen-defects surface, they would land here. None
  surfaced during Plan 04.

## Threat Flags

None — fix is purely additive at the visitor layer; no new network/auth/file-access surface; CEmitter and existing AST emission paths unchanged.

---
*Phase: 09-port-simple-physics-gbdk-example-to-gbkt*
*Plan: 04*
*Completed: 2026-05-13*
