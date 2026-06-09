---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
plan: 03
subsystem: testing
tags: [i16Var, signed-comparison, sub-pixel, brace-walk, GBDKPipelineV2, CIntLiteral, dsl-port]

# Dependency graph
requires:
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt-01
    provides: simple-physics MCP UAT contract (D-03 ordering rail)
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt-02
    provides: gbkt-examples:simple-physics module scaffold (build.gradle.kts, res/sprites/smiley.png, PLAYBOOK.md)
provides:
  - SimplePhysics.kt DSL port of phys.c (single play scene, 4 i16 vars, ActorPropertyRef.set(Expr) Bug B workaround)
  - SimplePhysicsIRTest (13 tests pinning IR shape — D-06, D-08 satisfied)
  - SimplePhysicsGameTest (4 SimulationContextV2 scenarios — enter resets + 3 D-01 behavior coverage via state-setup oracle)
  - SimplePhysicsEmissionTest (3 D-11 brace-walk invariants; D-11.1 + D-11.3 RED at HEAD as Plan 04 gate; D-11.2 GREEN)
  - evidence/tier1-shape/{01,02,03}-*.txt captured baselines of play_frame body (pre-fix)
  - LOCKED named bug for Plan 04: **Bug A — positive-literal RHS in signed comparison emits with `u` suffix**
affects: [09-04-fix-named-bug, 09-05-c-compiles-rom-builds, 09-06-mcp-uat]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Bug B workaround for moveTo(Expr, Expr): `actorRef.x set (signedVar shr 4)` via ActorPropertyRef.set(Expr) instead of moveTo(Int, Int)"
    - "Worktree-safe EVIDENCE_DIR derivation: `File(System.getProperty(\"user.dir\")).resolve(\"../../<relative>\").normalize()` instead of hard-coded absolute path"
    - "Brace-walk function body extractor (`extractFunctionBody(cSource, functionName)`) for per-function scope-level grep gates (per CLAUDE.md §\"Scope-level grep gates\")"

key-files:
  created:
    - gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/01-accel-clamp-upper-bound.txt
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/02-jump-impulse.txt
    - .planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/03-decel-ladder.txt
  modified: []

key-decisions:
  - "LOCKED: Plan 04 named bug is Bug A (positive-literal signed-comparison RHS emits `Nu`). Bug B workaround compiled cleanly with `smiley.x set (posX shr 4)` — no fallback path needed; Bug B remains a seed candidate for later phases."
  - "Generated frame function confirmed as `play_frame` (Plan 04 will use this name in its emission contract)."
  - "EVIDENCE_DIR resolved via user.dir + relative path (worktree-safe) — hard-coded absolute paths route evidence files outside the active checkout."
  - "SimulationContextV2 input helpers (dpad_held, button_pressed) evaluate to 0L in JVM — input edges are covered by SimplePhysicsEmissionTest (C-shape oracle) and Plan 06 MCP UAT, NOT by SimplePhysicsGameTest. GameTest exercises post-input physics (decel ladder + enter-state init) via direct setVar setup."

patterns-established:
  - "DSL-port test triad: {Example}IRTest (IR shape) + {Example}GameTest (SimulationContextV2 logic) + {Example}EmissionTest (per-function C-shape oracle via brace-walk)"
  - "Per-function emission assertions via extractFunctionBody brace-walk — no file-level grep counts (CLAUDE.md scope-level grep gate)"
  - "Evidence-before-assert ordering: writeText fires BEFORE assertTrue/assertFalse so RED test failures still produce reviewable baselines on disk"

requirements-completed: [D-06, D-08, D-11]

# Metrics
duration: ~35min
completed: 2026-05-13
---

# Phase 9 Plan 3: Port + JVM Oracle for simple_physics Summary

**SimplePhysics.kt DSL port of phys.c (4 i16Var sub-pixel coordinates, single play scene, ActorPropertyRef.set(Expr) Bug B workaround) plus a three-file JVM oracle (IR shape + SimulationContextV2 logic + 3 brace-walk D-11 emission invariants) — D-11.1 and D-11.3 land RED at HEAD as the named Plan 04 Bug A gate, D-11.2 lands GREEN.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-05-13T16:46:00Z (worktree spawn)
- **Completed:** 2026-05-13T17:12:00Z
- **Tasks:** 2 (both autonomous, both TDD)
- **Files created:** 7 (4 .kt + 3 evidence .txt)

## Accomplishments

- **SimplePhysics.kt** — verbatim port of GBDK simple_physics reference. Single `play` scene, 4 INT16 sub-pixel variables (posX/posY/spdX/spdY), single 8×8 smiley actor at (64, 64), full frame loop mirroring `phys.c` L62-L94 (per-axis accel + clamp, A-pressed jump impulse, pos integration, sub-pixel→pixel render via `smiley.x set (posX shr 4)`, decel ladder).
- **SimplePhysicsIRTest** — 13 IR-shape assertions (1 scene, 1 actor, 4 I16 vars, smiley at PositionDef(64, 64), play start, enter+frame non-empty). ALL GREEN.
- **SimplePhysicsGameTest** — 4 simulation scenarios (D-01.1 decel from clamp 64, D-01.2 decel from jump impulse −512, D-01.3 decel to zero, enter resets state to 1024/1024/0/0). ALL GREEN. Documents the input-helper stub caveat (`dpad_held` → 0L in JVM interpreter).
- **SimplePhysicsEmissionTest** — 3 brace-walk D-11 invariants over `play_frame` body extracted from `GBDKPipelineV2.generate()`. D-11.1 (positive-literal upper-bound clamp) and D-11.3 (decel zero comparisons) RED at HEAD; D-11.2 (signed −512 jump impulse + button_pressed edge-detect) GREEN at HEAD.
- **Evidence baselines** under `evidence/tier1-shape/` capture the pre-fix C-shape so Plan 04's GREEN flip is a reviewable diff on disk.
- **LOCKED Plan 04 named bug: Bug A.** Bug B workaround (`smiley.x set (posX shr 4)` via `ActorPropertyRef.set(Expr)`) compiled and runs cleanly — no fallback path needed; Bug B remains a seed candidate for a later phase.

## Task Commits

Each task was committed atomically:

1. **Task 1: SimplePhysics.kt + SimplePhysicsIRTest + SimplePhysicsGameTest** — `e73719a8` (feat)
2. **Task 2: SimplePhysicsEmissionTest + 3 evidence baselines** — `a31c3925` (test)

_Note: Tasks were marked `tdd="true"` but the test/feat steps were authored together per file because the DSL + IRTest + GameTest form a single "DSL-port-of-reference" deliverable and the EmissionTest is RED-by-design (the named bug is the implementation, fixed in Plan 04 — not in this plan). The RED-state of D-11.1 and D-11.3 in Task 2 IS the TDD-RED commit; Plan 04's `feat()` fix is the matching GREEN._

## Files Created

- `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt` — DSL port of phys.c
- `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt` — IR shape validation
- `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt` — SimulationContextV2 logic tests
- `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt` — 3 brace-walk D-11 invariants
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/01-accel-clamp-upper-bound.txt` — D-11.1 baseline (RED)
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/02-jump-impulse.txt` — D-11.2 baseline (GREEN)
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/tier1-shape/03-decel-ladder.txt` — D-11.3 baseline (RED)

## Plan 04 Hand-off

### Generated frame function name

`play_frame` — confirmed in `gbkt-examples/simple-physics/build/gbkt/generated/bank1.c` and the test pipeline's `bank1.c`.

### D-11 RED/GREEN status at HEAD

| Test | State at HEAD | Bug A site? | Plan 04 target |
|------|---------------|-------------|----------------|
| D-11.1 — `_spdX > 64` / `_spdY > 64` (no `u`) | **RED** (asserts `_spdX > 64u` absent — present in body) | YES (positive literal 64) | Make `Literal(64)` in signed-comparison RHS lower to `CIntLiteral(64)` |
| D-11.2 — `button_pressed(J_A)` + `_spdY = -512` (no `u`) | **GREEN** | NO (negative literal already correct) | None — regression guard |
| D-11.3 — `_spdX < 0` / `_spdX > 0` / `_spdY < 0` / `_spdY > 0` (no `u`) | **RED** (asserts `_spdX < 0u` etc. absent — present in body) | YES (positive literal 0) | Same fix as D-11.1: `Literal(0)` in signed-comparison RHS → `CIntLiteral(0)` |

### Confirmed Bug A firing sites in `play_frame` (from evidence baselines)

```c
if (_spdY > 64u)          // L13 in 01-accel-clamp-upper-bound.txt
    _spdY = 64u;
if (_spdX > 64u)          // L25 in 01-accel-clamp-upper-bound.txt
    _spdX = 64u;
if (_spdY < 0u)           // L34 in 03-decel-ladder.txt
    _spdY = _spdY + 1u;
if (_spdY > 0u)           // L37
    _spdY = _spdY - 1u;
if (_spdX < 0u)           // L40
    _spdX = _spdX + 1u;
if (_spdX > 0u)           // L43
    _spdX = _spdX - 1u;
```

Total: **6 Bug A firing sites** in `play_frame` (4 in decel ladder + 2 in accel clamp). Plan 04 must flip all 6 from `Nu` to `N`.

The clamp assignment RHS (`_spdY = 64u`, `_spdX = 64u`) is **NOT** a Bug A site — it is unsigned-context assignment (the assignment RHS is not a comparison). Plan 04 must NOT migrate those literals (Phase 07.9 Rule 2 — unsigned-context literals stay on `CLiteral`).

### Bug B disposition

`smiley.x set (posX shr 4)` and `smiley.y set (posY shr 4)` compiled and runs cleanly through `ActorPropertyRef.set(Expr)` (`gbkt-lang/ActorBuilder.kt:81`). **No fallback path needed.** Bug B is NOT the named fix for Plan 04. Bug B (no `moveTo(Expr, Expr)` overload, no `smiley.moveTo(posX shr 4, posY shr 4)` syntax) remains an open ergonomic gap — flagged as a candidate seed for a later phase. No seed file created in this plan because (a) the workaround is clean enough that the seed is not load-bearing, and (b) it does not block any Phase 9 success criterion.

### Seeds added

None. (Bug B disposition recorded above; no seed file required.)

## Decisions Made

- **Bug A is the named bug for Plan 04** (locked in this plan's SUMMARY per the phase contract).
- **EVIDENCE_DIR uses `user.dir`-relative path** instead of the hard-coded absolute path used by the analog `SignedComparisonLiteralEmissionTest.kt`. The analog pattern silently writes evidence outside the active checkout when run in a Claude Code worktree (the absolute path resolves to the main repo, not the worktree). Using `File(System.getProperty("user.dir")).resolve("../../...").normalize()` keeps the evidence inside the active checkout.
- **SimplePhysicsGameTest does not exercise input edges via `holdDpad`/`tap`** because `ScriptOpInterpreter.evaluateCallExpr` returns `0L` for `dpad_held` / `button_pressed` (input helpers are not part of the interpreter's supported CallExpr surface — see `gbkt-core/.../test/ScriptOpInterpreter.kt:547`). Input-edge behavior is covered at C-shape level by `SimplePhysicsEmissionTest.D-11_2` and at runtime by Plan 06 MCP UAT. The GameTest exercises post-input physics paths (decel ladder + enter-state init) via direct `setVar` setup.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] EVIDENCE_DIR worktree-safety fix**
- **Found during:** Task 2 (first SimplePhysicsEmissionTest run)
- **Issue:** EVIDENCE_DIR was modeled on `SignedComparisonLiteralEmissionTest.kt` which hard-codes the main-repo absolute path. Tests run inside a Claude Code worktree wrote evidence to `/Users/michalsvacha/GitHub/personal/gbkt/.planning/...` (main repo) instead of `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-.../.planning/...` (worktree). Evidence files would not have been committed with this plan.
- **Fix:** Switched EVIDENCE_DIR to `File(System.getProperty("user.dir")).resolve("../../.planning/phases/09-.../evidence/tier1-shape").normalize()`. Gradle's test `user.dir` is the project's working dir (`gbkt-examples/simple-physics`), so `../..` walks up to the checkout root regardless of whether that is the main repo or a worktree.
- **Files modified:** `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt` (Task 2 edit, before commit).
- **Verification:** Re-ran `:gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest"`; confirmed `find /Users/michalsvacha/GitHub/personal/gbkt -name "01-accel-clamp-upper-bound.txt"` finds the file under the worktree's `.planning/...`, not the main repo's.
- **Committed in:** `a31c3925` (part of Task 2 commit).
- **Worth carrying forward as a pattern:** The fix is captured in `tech-stack.patterns` of this SUMMARY frontmatter so future emission tests do not repeat the worktree-leak hazard.

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking worktree path safety).
**Impact on plan:** Necessary to make evidence files commit with this plan; no scope creep.

## Issues Encountered

- **`button_held` vs `dpad_held` in observed C output:** Initial inspection of `:generateC` output (`build/gbkt/generated/bank1.c`) showed `button_held(J_UP)`, but `GBDKPipelineV2.generate()` (used inside SimplePhysicsEmissionTest) emits `dpad_held(J_UP)`. The rename is a post-process dedup step in `GBDKPipelineV2:314-315` ("`dpad_held` to `held`"; "`button_held` to `held`"). The test's assertions don't depend on the input helper name (they assert on `_spdX > 64u` style strings), so the rename does not affect D-11.1 / D-11.3. D-11.2 asserts on `button_pressed(J_A)` which is the pre-dedup name — confirmed present in the test pipeline output. No code change needed.

## Self-Check: PASSED

- [x] `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt` exists.
- [x] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt` exists.
- [x] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt` exists.
- [x] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt` exists.
- [x] `gbkt-examples/simple-physics/build/gbkt/generated/main.c` exists (≥ 200 lines).
- [x] 3 evidence files under `evidence/tier1-shape/` exist with `play_frame` body captured.
- [x] Commits `e73719a8` and `a31c3925` exist in `git log`.
- [x] `./gradlew :gbkt-examples:simple-physics:compileKotlin` succeeds.
- [x] `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsIRTest"` GREEN.
- [x] `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsGameTest"` GREEN.
- [x] `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest"` RED on D-11.1 + D-11.3 (intentional Plan 04 gate); GREEN on D-11.2.
- [x] Evidence-before-assert ordering structurally verified (writeText line < first assert line in every @Test).

## Next Phase Readiness

- Plan 04 can begin: target site is `ExprVisitor.visitLiteral` (or `visitBinaryExpr` with signed-LHS detection); contract is the D-11.1 + D-11.3 RED tests flipping to GREEN without touching D-11.2.
- Plan 05 (`gradle buildRom`) needs Plan 04 GREEN first (otherwise the ROM compiles with SDCC warning 94 and the runtime behavior is broken).
- Plan 06 (MCP UAT) needs Plan 04 GREEN + Plan 05 ROM. The MCP scripts in 09-UAT.md are already locked from Plan 01.

## Threat Flags

None — no new network/auth/file-access surface introduced. Tests only read pipeline output and write evidence files inside the active checkout.

---
*Phase: 09-port-simple-physics-gbdk-example-to-gbkt*
*Plan: 03*
*Completed: 2026-05-13*
