---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
reviewed: 2026-05-13T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - gbkt-backend-gbdk/CLAUDE.md
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CLAUDE.md
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
  - gbkt-examples/simple-physics/build.gradle.kts
  - gbkt-examples/simple-physics/CLAUDE.md
  - gbkt-examples/simple-physics/PLAYBOOK.md
  - gbkt-examples/simple-physics/README.md
  - gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt
  - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt
  - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt
  - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt
  - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt
findings:
  critical: 1
  warning: 5
  info: 5
  total: 11
status: issues_found
---

# Phase 09: Code Review Report

**Reviewed:** 2026-05-13
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Phase 09 introduces the `simple-physics` GBDK reference port and threads a variable-type registry into `ExprVisitor` / `SceneVisitor` so the DSL-authored signed-comparison RHS (`whenever(spdY isAbove 64)`) lowers `Literal(64)` through `CIntLiteral` instead of `CLiteral`. The visitor-layer fix is correct, minimal, and strictly additive — it preserves pre-fix `CLiteral` emission for every shape that doesn't satisfy the three-conjunct predicate, which protects downstream examples from regressions.

That said, the work has gaps and one finding that must be fixed before this phase is held up as a model for future Phase 9–12 reference ports:

- The fix's predicate looks only at `VarRef` on the LHS — it skips `PropertyAccessExpr`, which means signed actor properties registered via `i8Prop` remain on the buggy path (`ball.dx isAbove 0` still emits `_ball_dx > 0u`). For SimplePhysics this is non-blocking because the port uses `i16Var` exclusively, but the convention now claims "DSL-authored path is covered" — it isn't, not for the actor-property shape.
- `PLAYBOOK.md` contains hard-coded expected values that contradict the UAT test's own derivation in the same phase. Anyone running the MCP scripts will see RED at HEAD even on a correct ROM. This is a Critical doc-vs-code-conflict.
- A handful of pre-existing patterns (per-call `ExprVisitor()` allocation in companion fallback, ThreadLocal state leak between scenes) are now widened by the fix and warrant scoping for the follow-up phase.

The DSL port itself (`SimplePhysics.kt`) is clean, faithful to the oracle, and demonstrates exactly the patterns claimed.

## Critical Issues

### CR-01: PLAYBOOK.md expected values contradict UAT test verdicts

**File:** `gbkt-examples/simple-physics/PLAYBOOK.md:78-108`
**Issue:** All three MCP input scripts in PLAYBOOK.md document expected variable values that disagree with the same phase's UAT test (`SimplePhysicsUatTest.kt`), which derives the values frame-by-frame from the source in `SimplePhysics.kt`.

Concretely:

| PLAYBOOK script | PLAYBOOK expected | UAT-derived actual | Off by |
|---|---|---|---|
| Behavior 1 — `step(frames=30, buttons=[right])` | `spdX = 64` (clamp fired) | `spdX = 30` (net +1/frame, clamp does NOT fire until frame 64) | 2× |
| Behavior 2 — `step(frames=1, buttons=[a])` | `spdY = -512` | `spdY = -511` (decel ladder fires same frame) | +1 |
| Behavior 3 — `step(frames=20, buttons=[right])` | `spdX ~40` | `spdX = 20` | 2× |

Behavior 1 is the clearest defect: the PLAYBOOK comment reads "clamp fires by frame 32: 2\*32 >= 64", which counts the +2 accel but ignores the −1 decel that runs every frame at the bottom of `play_frame`. The UAT test (`SimplePhysicsUatTest.kt:152-156`) explicitly calls this out: "Plan-06 anticipated 64 because it counted +2/frame (forgetting the decel ladder runs every frame)." So the bug was identified during Plan 06 but never propagated back to PLAYBOOK.md.

Consequence: an MCP agent (Claude Code, future automated harnesses) running these scripts against a correct ROM will report RED — and the obvious "fix" is to patch the DSL until the variable matches the script. That's a code-modifies-to-match-doc anti-pattern that erodes the codegen oracle. The phase's own visual-evidence rule (CLAUDE.md §"Verification Methodology") explicitly warns that variable assertions are necessary but not sufficient — but here the variable assertions are also wrong.

**Fix:** Update PLAYBOOK.md expected values to match the UAT-derived truth (and the inline rationale already documented in `SimplePhysicsUatTest.kt`). Suggested patch:

```markdown
### Behavior 1 — D-pad held → sprite accelerates and clamps at max speed

    emulator_start(game="simple-physics")
    emulator_step(frames=10)                              # boot
    emulator_wait_for_scene(scene="play")
    emulator_read_variable("spdX")                        # expect: 0
    # NOTE: per-frame net delta is +1 (accel +2, then decel -1 in the same frame).
    # Clamp at +64 first fires at frame 64 (steady-state spdX = 63 thereafter).
    emulator_step(frames=30, buttons=["right"])           # 30 frames → spdX = 30
    emulator_read_variable("spdX")                        # expect: 30 (accel ramp, clamp NOT yet fired)
    emulator_screenshot(label="behavior1-accel-ramp-30frames")
    emulator_assert([{type:"variable_equals", name:"spdX", expected:30}])
    emulator_step(frames=34, buttons=["right"])           # extend to 64 frames total → clamp fires
    emulator_assert([{type:"variable_equals", name:"spdX", expected:63}])

### Behavior 2 — A pressed (edge) → instant Y impulse (jump)

    ...
    emulator_step(frames=1, buttons=["a"])                # impulse + same-frame decel step
    emulator_read_variable("spdY")                        # expect: -511 (= -512 impulse + 1 decel)

### Behavior 3 — D-pad released → sprite decelerates to rest

    ...
    emulator_step(frames=20, buttons=["right"])           # build up speed (~20 sub-pixels net)
    emulator_read_variable("spdX")                        # expect: 20
```

The phase verdict cannot be `passed` while PLAYBOOK.md and the UAT test disagree on the oracle.

## Warnings

### WR-01: ExprVisitor signed-comparison fix does not cover PropertyAccessExpr LHS

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt:122-128`
**Issue:** `isSignedComparisonRhs` requires `expr.left as? VarRef ?: return false`. Actor properties declared with `i8Prop(...)` are stored as `PropertyAccessExpr(actorId, propName)` after `ActorPropertyRef.toExpr()` (see `gbkt-lang/.../ActorBuilder.kt:63`). They are NOT `VarRef`. So a user writing:

```kotlin
val ball by actor {
    position(80, 72)
    var dx by i8Prop(-1)   // registers _ball_dx as VarType.I8
}
...
whenever(ball.dx isAbove 0) { /* never fires for negative dx */ }
```

still triggers the original Bug A pattern that Phase 07.9 and Phase 9 Plan 04 were supposed to close — SDCC will promote `_ball_dx > 0u` to unsigned and warning 94 fires (or worse, silent always-false). The CLAUDE.md update at `gbkt-backend-gbdk/CLAUDE.md:32-36` claims the DSL-authored path is covered, but it's only covered for top-level `VarRef`s.

SimplePhysics doesn't exercise this shape (it uses `i16Var`, not `i8Prop`), so the existing examples remain GREEN. But this is exactly the kind of latent coverage gap the Phase 07.4 retrospective at `CLAUDE.md` §"Verification Methodology" warns about — claiming a class of bugs is fixed when only a subset is.

**Fix:** Extend `isSignedComparisonRhs` to also resolve `PropertyAccessExpr`. The variable name is `${objectId}_${property}` (registered in `ActorPropDelegate.provideDelegate` at `ActorBuilder.kt:1171-1173`):

```kotlin
private fun isSignedComparisonRhs(expr: BinaryExpr): Boolean {
    if (!isComparisonOp(expr.op)) return false
    if (expr.right !is Literal) return false
    val lhsName = when (val lhs = expr.left) {
        is VarRef -> lhs.name
        is PropertyAccessExpr -> "${lhs.objectId}_${lhs.property}"
        else -> return false
    }
    val lhsType = variableTypes[lhsName] ?: return false
    return lhsType == VarType.I8 || lhsType == VarType.I16
}
```

Then add a regression test that exercises `i8Prop` + signed comparison in the existing
`SignedComparisonLiteralEmissionTest` (bucket-a's home) or in a new
`gbkt-examples/simple-physics`-adjacent test if the example acquires a custom-prop variant.

### WR-02: companion-object ExprVisitor.sanitizeVarName / visit allocate a fresh visitor per call

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt:347, 356`
**Issue:** Both companion fallbacks construct a brand-new `ExprVisitor()` instance just to call a method that doesn't use any per-instance state. `sanitizeVarName` is a pure function (it builds nothing from `actors` or `variables`). The `visit(expr: Expr)` fallback at line 356 creates a no-arg `ExprVisitor()` whose `variables` map is empty — meaning if any caller routes through this fallback, the Plan 04 signed-comparison fix is silently disabled. `ScriptOpVisitor.kt:1206` is one such caller.

This is a pre-existing pattern, but the Phase 9 Plan 04 fix expands its blast radius: anything that flows through the no-arg companion now gets the *backward-compatible-but-wrong* behavior, and the diff doesn't add any guard against new callers.

**Fix:** Two-part.

1. Promote `sanitizeVarName` to a top-level `internal fun` (or `@JvmStatic` companion) that does not need a receiver, eliminating the per-call allocation:

```kotlin
companion object {
    @JvmStatic
    internal fun sanitizeVarName(name: String): String {
        val sanitized = name.replace('.', '_')
        return when {
            sanitized.startsWith("J_") -> sanitized
            sanitized.startsWith('_') -> sanitized
            else -> "_$sanitized"
        }
    }
    ...
}
```

2. For the companion `visit(expr: Expr)` fallback: either (a) audit every caller to confirm none flow through the empty-`variables` path during scene-script codegen, or (b) deprecate the companion `visit` entirely and require callers to pass an explicit `ExprVisitor` instance (matches the `ScriptOpVisitor.visit(op, exprVisitor)` pattern).

### WR-03: ScriptOpVisitor.exprVisitorContext leaks across scenes/actors on the same thread

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt:175, 282-285`
**Issue:** `exprVisitorContext.set(exprVisitor)` is called on entry to `visit(op, exprVisitor)`, but there is no `try/finally` clearing it on exit. After SceneVisitor finishes lowering scene N, the thread-local still holds the actor- and variable-aware visitor for scene N. If a downstream visitor (`ActorVisitor.generateAnimationFunction`, `MenuVisitor`, etc.) then calls the no-arg `ScriptOpVisitor.visit(op)`, `ev()` returns scene N's visitor — including its `variables` registry — which now causes those visitors' `Literal` RHSs in signed comparisons to route through `CIntLiteral` instead of `CLiteral`.

In the audit context this is technically still correct because Phase 07.9 hardcoded sites already use `CIntLiteral` directly. But it's a fragile invariant: any future visitor that constructs a signed comparison while expecting the default-`CLiteral` behavior of the no-arg visit path will silently get the variable-aware behavior instead.

The diff in this phase didn't introduce the leak, but it materially extended the consequences of the leak by giving the leaked state a new semantic axis (variable signedness).

**Fix:** Add try/finally to clear the thread-local. Defensive minimum:

```kotlin
fun visit(op: ScriptOp, exprVisitor: ExprVisitor): CStatement {
    val prev = exprVisitorContext.get()
    exprVisitorContext.set(exprVisitor)
    return try {
        op.accept(this)
    } finally {
        exprVisitorContext.set(prev)
    }
}
```

This preserves nesting and clears state when the call returns, mirroring the pattern already used for `setSceneContext` in `GBDKPipelineV2.buildSceneFile`.

### WR-04: extractFunctionBody brace-walker is naïve to braces in strings/comments

**File:** `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt:83-103`
**Issue:** The brace-walker increments/decrements `depth` for every literal `{` and `}` character on a line, including those inside C string literals, character literals, line comments (`// ... { ...`), and block comments. For the current generated output the visitors emit no embedded string-with-braces in `play_frame`, so this is RED-safe today — but the comment at line 25–29 promotes this as a generalizable "scope-level grep gate" pattern that future phases will reuse.

A future generated function that embeds `printf("hello {world}")` or has a block-comment containing `{` will silently truncate (close brace inside comment causes depth to go negative or exit early) or extend (open brace inside comment increases depth and the closer never arrives).

Additionally, `lines.indexOfFirst { it.contains("void $functionName(") }` will match a Doxygen comment line like `/** Calls void play_frame(void) */` that precedes the real function — startIdx then lands on the comment, no `{` appears on that line, depth stays 0, `started` stays false, and the loop continues into the comment block looking for an opener.

**Fix:** Two options.

1. **Tighten the start-line pattern** to a regex that requires the function definition shape, not just the substring: `Regex("""^\s*(?:BANKED\s+)?void\s+$functionName\s*\(""")`. This eliminates the doxygen false-match.
2. **Track string and comment state in the brace walk** — small state machine that ignores `{`/`}` inside `"..."`, `'...'`, `//...`, and `/* ... */`. For a test helper this is overkill, but if Plan 09 promotes this pattern to a shared utility (per the comment header), the utility should be correct.

For SimplePhysics specifically, option 1 is sufficient since the generated `play_frame` has no string literals containing braces and the file has no Doxygen above the function. Option 2 belongs in the follow-up phase that extracts the helper.

### WR-05: SimplePhysicsGameTest depends on undocumented `dpad_held`/`button_pressed` stub-resolves-to-0 behavior

**File:** `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt:21-31, 53-67`
**Issue:** All three D-01 simulation tests rely on the input-helper CallExprs being stubbed to `0L` by `ScriptOpInterpreter.evaluateCallExpr`. The class comment acknowledges this (lines 21-31). The tests then preload the variable to the expected post-input value via `setVar(...)` and exercise only the integration + decel paths.

This is fine as a *bounded* test scope, but it means:

1. If anyone later teaches `ScriptOpInterpreter` to honor a simulated joypad state (a reasonable evolution), all three tests start failing for the wrong reason — they would suddenly exercise the held-input branch and produce different values, not because the physics changed but because the input stub changed.
2. The test names (`D-01_1 decel from clamp ceiling`, `D-01_2 jump impulse target`) imply they verify the held-accel/clamp and jump impulse logic respectively. They do not. They verify the per-frame decel ladder from a preloaded value. The held-accel and jump-impulse logic is only covered by the emission test (C-shape) and the UAT test (runtime).

**Fix:** Rename the test functions to match what they actually verify (e.g. `decel ladder reduces spdX by 1 when held input stub returns 0`) and either (a) add a comment-level cross-reference to the emission/UAT tests that cover the input-edge cases, or (b) when `SimulationContextV2` gains explicit input stubbing, change the tests to drive input directly rather than preloading.

## Info

### IN-01: README.md "(once Plan 04 adds it)" suffix is stale

**File:** `gbkt-examples/simple-physics/README.md:59-60`
**Issue:** The README references `PLAYBOOK.md` with the trailing parenthetical "(once Plan 04 adds it)". PLAYBOOK.md exists at HEAD as part of this phase, so the parenthetical leaks phase-planning detail into user-facing documentation.
**Fix:** Replace with a plain reference: `See [PLAYBOOK.md](PLAYBOOK.md) for the MCP-driven verification scenarios.`

### IN-02: CLAUDE.md leaks phase-planning detail into developer notes

**File:** `gbkt-examples/simple-physics/CLAUDE.md:13, 40-51`
**Issue:** Lines reference "added in Plan 03 — not present after Plan 02", "Until Bug B is fixed in a later phase", "Plan 03 will pick the cleanest available approach; check the latest VALIDATION.md". These notes will become stale as soon as Phase 9 closes and are confusing to anyone reading the doc in isolation (no link to which VALIDATION.md, what Bug B is, etc.).
**Fix:** Replace plan-history references with current-state descriptions. The "Bug B workaround" section is fine to keep but should describe the workaround in its own right, not as "until X is fixed in plan Y".

### IN-03: `@Suppress("LongMethod")` on simplePhysics — established convention, but worth flagging

**File:** `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt:31`
**Issue:** The DSL block exceeds detekt's LongMethod threshold and is suppressed. This matches every other example in `gbkt-examples/`, so the suppression is consistent. Flagging for awareness only — the convention is established at the module-CLAUDE.md level.
**Fix:** None required. If the suppression list grows further it may be worth raising the LongMethod threshold for `gbkt-examples/**` in `detekt.yml`, mirroring the existing exclusions for `**/codegen/**` and `**/dsl/**`.

### IN-04: UAT test `behavior 1` carries a hard-coded `1519` magic posX value

**File:** `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt:161`
**Issue:** `assertEquals(1519, posXAt30, ...)` is correct (derived from the sum 1024 + 2+3+...+31 = 1024 + 495 = 1519) and the comment block explains the derivation. But the magic number sits in the assertion. If the per-frame ordering changes (e.g. decel moves before posX integration), the magic number stops matching the formula but the test message still claims the same derivation. The 1519 value is also coupled to the initial posX (1024); if `enterScene` initial values move, the formula breaks silently.
**Fix:** Compute the expected value inline so the test is self-documenting:

```kotlin
val expectedPosX = 1024 + (2..31).sum()  // 1024 + 495 = 1519
assertEquals(expectedPosX, posXAt30, "posX after 30 frames should be initial + Σ(2..31)")
```

This makes the relationship to the source obvious and fails with a more diagnosable message when the formula changes.

### IN-05: PLAYBOOK.md describes A as "edge-triggered on press — held A does not re-fire" but does not include a held-A test script

**File:** `gbkt-examples/simple-physics/PLAYBOOK.md:14-16`
**Issue:** The README/PLAYBOOK promote the edge-trigger property as a behavior, but only behavior 2 exercises a single-frame press. Holding A for multiple frames and confirming `spdY` does NOT keep re-firing (i.e., stays around -511 then climbs via decel) is missing. This is a gap in PLAYBOOK coverage, not a bug.
**Fix:** Add a behavior 4 script (or extend behavior 2) that holds A for 5 frames and confirms `spdY` follows the decel ladder rather than being re-clamped to -512 each frame.

---

_Reviewed: 2026-05-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
