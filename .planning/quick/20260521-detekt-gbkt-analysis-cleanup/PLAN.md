---
quick_id: 20260521-detekt-gbkt-analysis-cleanup
date: 2026-05-21
slug: detekt-gbkt-analysis-cleanup
status: in-progress
---

# Quick task: clear gbkt-analysis detekt drift (14 issues)

## Why

`./gradlew build` and `./gradlew detekt` fail with 14 weighted issues in `gbkt-analysis/`. These are pre-existing drift from prior phases (07.4 racing pass introduction + Semantic/Constraint pass evolution), surfaced as AC-6 deferred at the close of Phase 11.3. After Phase 11.3's `chore: spotless ktfmt 0.62` commit (`612ed65e`) closed the spotless half of AC-6, detekt is now the remaining blocker for green `clean build`.

User constraint: "Don't solve things just by adding ignores to detekt file. Smart balance."

## Smart-balance breakdown

| # | Issue | Site | Approach | Rationale |
|---|-------|------|----------|-----------|
| 1 | UnusedParameter × 2 (`game`, `diagnostics`) | `SemanticValidationPass.checkPalettePrecision` | **Surgical: delete the function** | Function is a documented no-op ("This function is intentionally a no-op to prevent false-positive warnings"); call site at L53 invokes it for no effect. Removing dead code beats suppression. |
| 2 | LongMethod (85) + CyclomaticComplexMethod (22) | `SemanticValidationPass.checkFadeWithoutAudioMixer` | **Surgical: extract `collectAllTopLevelOps` helper** | The 60-line `buildList { }` walks every GameIR subsystem polymorphically; that's a discrete responsibility. Extracting drops the host function below both thresholds in one move. |
| 3 | LongMethod (83) | `ConstraintCheckPass.run` | **Surgical: split into per-check helpers** | The `run` method runs OAM, WRAM, and other independent checks back-to-back; each one extracts cleanly. |
| 4 | LongParameterList (6 params) | `RacingValidationPass.checkVehicleActorBindings` | **Surgical: bundle into context data class** | Threshold is 6; function has exactly 6. The 5 input params naturally cluster as "racing validation context"; `diagnostics` stays separate as the output sink. Bundle now or this trips again if any related check grows. |
| 5 | LoopWithTooManyJumpStatements (3 continues) | `RacingValidationPass.checkCameraFollowsPlayer` L389 | **Surgical: combine guards** | The 3 `continue` statements collapse into one `filterIsInstance` + 1 combined predicate. Idiomatic Kotlin, no suppression needed. |
| 6 | TooManyFunctions (20 in class) | `RacingValidationPass` class | **Justified config: add `**/analysis/passes/**` to TooManyFunctions exclusion** | Already-documented project pattern. `detekt.yml` excludes `**/codegen/**` for the same reason; CLAUDE.md explicitly notes "Validation passes inherently produce long check methods." This is extending an existing exemption to a parallel package, not a new ignore. |
| 7 | MaxLineLength × 6 (all on KDoc lines) | RacingValidationPass / ConstantFoldingPass / SemanticValidationPass KDoc blocks | **Justified config: `MaxLineLength.excludeCommentStatements: true`** | Universal best practice: code-line-length rules are meant for code, not narrative documentation. The current `excludeCommentStatements: false` is unusual; the parallel `excludePackageStatements: true` and `excludeImportStatements: true` already in the file are the same kind of decision. |

## What NOT to do

- Do **not** add blanket `enabled: false` overrides for any rule
- Do **not** add per-file `@Suppress` annotations without a clear comment explaining why (we have none of those in this plan)
- Do **not** raise thresholds (e.g., `LongMethod` from 80 to 100) just to dodge the issue — refactor instead
- Do **not** weaken the rule globally when only one package needs the exemption (use package-scoped `excludes`)

## Execution order (atomic commits)

1. Fix 1: delete `checkPalettePrecision` + call site
2. Fix 2: extract `collectAllTopLevelOps` from `checkFadeWithoutAudioMixer`
3. Fix 3: split `ConstraintCheckPass.run` into per-check helpers
4. Fix 4: bundle `checkVehicleActorBindings` params into `RacingValidationContext` data class
5. Fix 5: refactor `checkCameraFollowsPlayer` loop
6. Fix 6+7: detekt.yml — add `**/analysis/passes/**` to TooManyFunctions excludes + set `MaxLineLength.excludeCommentStatements: true`

## Acceptance

- [ ] `./gradlew :gbkt-analysis:detekt` exits 0
- [ ] `./gradlew :gbkt-analysis:test` exits 0 (no behavior regressions from refactors)
- [ ] `./gradlew spotlessCheck` still exits 0 (no formatting regressions)
- [ ] `./gradlew compileKotlin compileTestKotlin` exits 0
- [ ] Each fix committed atomically with conventional-commits style
- [ ] SUMMARY.md written with per-fix verification evidence

## Out of scope

- The 2 RED `TrackSynthesizerCircuitShapeTest` stubs (Phase 07.4-33) — already routed to Phase 07.4-35
- `./gradlew clean build` end-to-end — depends on the above RED stubs to be GREEN; this quick task only closes the detekt leg
- Other modules' detekt status — none failing currently, just `gbkt-analysis`
