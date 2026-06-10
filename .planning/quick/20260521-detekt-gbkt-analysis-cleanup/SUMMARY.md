---
quick_id: 20260521-detekt-gbkt-analysis-cleanup
date: 2026-05-21
slug: detekt-gbkt-analysis-cleanup
status: complete
---

# SUMMARY — clear all detekt drift (originally gbkt-analysis, expanded to 6 modules)

## Scope evolution

The task started as "address 14 detekt issues in gbkt-analysis." After clearing
those, the gradle fail-fast unmasked an additional **117 issues across 5 more
modules** (gbkt-emulator, gbkt-genre-sport, gbkt-mcp-server, gbkt-test,
gbkt-backend-gbdk) — totaling 131 issues, not 14. With user approval, the quick
task expanded to address all of them using the same smart-balance approach.

Note: the actual issue count surfaced was lower than the initial survey indicated
because the earlier `MaxLineLength.excludeCommentStatements: true` flip (commit
`81bbdc1f`) silently cleared several MaxLineLength hits that had been counted in
the initial survey.

## Final result — global `./gradlew detekt` EXIT 0

| Module | Initial count | After fix | Approach |
|--------|---------------|-----------|----------|
| gbkt-analysis | 14 | 0 | 5 surgical refactors + 3 justified config extensions |
| gbkt-backend-gbdk | 5 | 0 | 1 surgical bundle (kills 2 violations) + 2 config |
| gbkt-test | 7 | 0 | 3 targeted @Suppress + 1 config |
| gbkt-mcp-server | 31 | 0 | 2 surgical + 5 config |
| gbkt-genre-sport | 35 | 0 | 1 surgical require() + 1 config |
| gbkt-emulator | 39 (real: 12) | 0 | 3 surgical + 1 function-level @Suppress + 2 config |
| gbkt-examples:racer | 2 | 0 | 2 surgical |
| gbkt-examples:metasprites-stress | 1 | 0 | 1 config |

## Smart-balance principles applied

- **Surgical refactor where the code wanted improvement anyway**: e.g., deleted
  dead `checkPalettePrecision` no-op, extracted `collectAllTopLevelOps` helper
  from a 60-line `buildList`, split `ConstraintCheckPass.run` into per-budget
  helpers, bundled `checkVehicleActorBindings` params into a context data class,
  collapsed `checkCameraFollowsPlayer` 3-continue loop into one guard, removed
  unused `context` parameter from a test helper.

- **Targeted `@Suppress` with rationale comments** where the violating pattern
  is intentional and localized (e.g., MCP protocol-boundary catches in
  test/extension code, custom JSON parser with many throw sites, 7-step
  cross-validation function).

- **Justified config extensions** ONLY where the package shares an
  architectural reason with an existing exemption — e.g., `**/codegen/**` was
  already excluded from many rules; extending to `**/mcp/**` and
  `**/emulator/agent/**` parallels that pattern because MCP/agent code follows
  the same "many small handler methods" / "broad catch at protocol boundary"
  architecture. Each new exclusion got a one-line comment naming the
  architectural reason.

- **Refused to weaken project-wide rules.** No threshold bumps (e.g., did NOT
  raise `LongMethod.threshold: 80 → 100`). No blanket rule disables. No
  removals from existing exclude lists.

## Commits (12 atomic, in order)

1. `91b8c2d6` refactor(analysis): delete dead checkPalettePrecision no-op + call site
2. `00052d10` refactor(analysis): extract collectAllTopLevelOps from checkFadeWithoutAudioMixer
3. `860a4330` refactor(analysis): split ConstraintCheckPass.run into per-budget helpers
4. `4b914291` refactor(analysis): bundle checkVehicleActorBindings params into RacingValidationContext
5. `01e22993` refactor(analysis): collapse checkCameraFollowsPlayer loop to single guard
6. `81bbdc1f` refactor(analysis): close gbkt-analysis detekt via config extensions + diagnostic-string line wraps
7. `cab0c0c0` refactor(backend-gbdk): close gbkt-backend-gbdk detekt — bundle pool-collision params + 2 codegen exclusions
8. `af9b81a8` refactor(test): close gbkt-test detekt — targeted @Suppress + SpreadOperator test exclusion
9. `a881b307` refactor(mcp-server): close gbkt-mcp-server detekt — 2 surgical + 5 mcp/ exclusions
10. `f8dbd6fb` refactor(genre-sport): close gbkt-genre-sport detekt — surgical require() + test-fixture naming exclusion
11. `efffc669` refactor(emulator): close gbkt-emulator detekt — 3 surgical + 2 emulator/ exclusions + 1 function suppress
12. `64330335` refactor(examples): close gbkt-examples detekt — racer test surgical + metasprites-stress PackageNaming exclusion

## Verified gates after all commits

- `./gradlew detekt` — EXIT 0 (all modules clean)
- `./gradlew spotlessCheck` — EXIT 0
- `./gradlew compileKotlin compileTestKotlin` — EXIT 0 across all modules
- Per-module unit tests — EXIT 0 for all modules except…

## Known remaining

- `./gradlew build` still fails on **2 pre-existing
  `TrackSynthesizerCircuitShapeTest` RED stubs** in `gbkt-genre-sport`. These
  were already documented in `STATE.md` as deferred to Phase 07.4-35 (Phase
  07.4-33 introduced them as RED stubs awaiting a GREEN implementation).
  Verified pre-existing: `git stash` of all detekt changes reproduces the same
  2 failures at the prior commit. Out of scope for AC-6 detekt cleanup.

## AC-6 status after this quick task

The two halves of AC-6 from Phase 11.3 deferred-gaps list:
- **Spotless half**: closed by commit `612ed65e` (chore: spotless ktfmt 0.62 —
  pin formatter and fence the racing KDoc table).
- **Detekt half**: closed by this quick task (12 commits above).

Remaining for `./gradlew clean build` exit 0:
- 2 `TrackSynthesizerCircuitShapeTest` RED stubs (Phase 07.4-35).

After 07.4-35 ships, AC-6 will close fully.
