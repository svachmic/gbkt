# Pitfalls Research

**Domain:** v0.1.1 hardening — cognitive-complexity burn-down, detekt mass cleanup, seed backlog closure, DSL doc reconciliation on a byte-identity-gated compiler codebase
**Researched:** 2026-06-12
**Confidence:** HIGH — drawn directly from the project's own post-mortems (RETROSPECTIVE.md), the verifier-gates contract, the seed corpus, and the detekt/Sonar configuration already in the tree

---

## Critical Pitfalls

### Pitfall 1: Behavior-Changing "Pure" Complexity Refactor

**What goes wrong:**
A function with S3776 HIGH cognitive-complexity is refactored by extracting private helpers, collapsing nested `when` branches, or reordering guard clauses. The JVM test suite stays green. But the generated C changes — either in whitespace that affects nothing, or in statement ordering that changes runtime behavior. Because no one ran a clean `:buildRom` before declaring the phase done, the regression ships.

This is the most expensive failure mode in this codebase. The Phase 09.1 post-mortem codified it: 15 JVM truths GREEN, yet a clean `simple-physics:buildRom` produced duplicate `_play_enter` / `_play_frame` link errors because a stale `bank1.c` was left from a prior build. JVM tests will never catch staleness in `build/gbkt/generated/`.

**Why it happens:**
The S3776 hotspots are concentrated in the visitor and pipeline files: `GBDKPipeline.kt` (5,397 lines), `GBDKSystemVisitor.kt` (6,390 lines), `RpgVisitor.kt` (3,377 lines), `CombatVisitor.kt` (2,837 lines), `PlatformerVisitor.kt` (2,548 lines). These files emit C by constructing `CStatement` trees through sequences of conditional accumulation — the order and nesting of conditions IS the correctness guarantee. Extracting a helper that inlines the same logic will pass all JVM shape tests but can subtly change which `if` branches are evaluated first, which early returns fire, or which accumulation happens in which order.

**How to avoid:**
Every plan in the S3776 phase that touches any file matched by `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/**` or `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt` must run the `rom-build` verifier gate (defined in `.planning/verifier-gates.md`) before declaring the plan done. Specifically: `./gradlew :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom`. For visitor files touching more than one example game (e.g. RPG visitor), run the 7-target sweep: `./gradlew :gbkt-examples:breakout:clean ... :gbkt-examples:platformer-template:buildRom` in a single chained invocation (no parallel `gradle clean` — Kotlin daemon collision; see `feedback_no_parallel_gradle_clean`). Pong should be flagged PASS* (known toolchain non-determinism) rather than investigated.

Add a byte-identity assertion step: compare the SHA-256 of the generated `.gb` against the pre-refactor hash for each target that is NOT intentionally changed. If the hash changes, the refactor is not behavior-preserving and must be diagnosed before proceeding.

**Warning signs:**
- A commit message saying "refactor: reduce complexity" touching `codegen/visitor/` or `codegen/pipeline/` with no mention of a ROM smoke test
- JVM tests green but no `buildRom` was run
- Helper method extraction that moves an `if (game.hasSomeFeature)` guard inside the helper instead of in the call site — this changes execution order relative to surrounding C emission statements

**Phase to address:**
The S3776 burn-down phase. Every plan must include a ROM smoke + byte-identity sweep as an exit criterion, not as an optional check.

---

### Pitfall 2: Complexity Displacement — S3776 Fixed, Code Worse

**What goes wrong:**
The Sonar finding disappears because the function's measured cognitive complexity falls below the threshold. But the complexity was moved into 8 newly extracted private helpers that each have complexity 10, no test coverage, hard-to-understand names, and indirect interactions. The overall system is harder to reason about than before. Future phases will re-break what the S3776 "fix" restructured, because the invariants are no longer locally visible.

**Why it happens:**
Mechanical extraction to a helper always reduces the parent function's measured complexity score, regardless of whether it improves clarity. A developer under pressure to close findings will take the path that makes the score go away, not the path that improves the code. The files in question — visitor dispatch methods for RPG systems, platformer physics, combat codegen — have high inherent complexity because they mirror the inherent complexity of the Game Boy's hardware constraints. Splitting them into helpers does not reduce that inherent complexity; it just spreads it.

**How to avoid:**
Before refactoring any S3776 finding, explicitly answer: "can this function be simplified by eliminating a case, not just hiding it?" For visitor dispatch, the correct refactor is often to split the visitor class itself along a semantic boundary (e.g., a `SimpleBattleVisitor` split out of `CombatVisitor`), not to extract one-use helper methods. Each extracted method must be given a test that exercises it independently — if a helper cannot be tested in isolation, it is not a real extraction, it is obfuscation.

For the `codegen/**` exclusion zone that detekt already carves out, Sonar S3776 has no corresponding exclusion. Methods excluded from detekt complexity checks are NOT excluded from S3776. This means the same method may be legitimately excluded from detekt `CyclomaticComplexMethod` but must still be addressed for Sonar. Document which methods are being addressed by semantic splitting vs. by tolerated suppression.

**Warning signs:**
- A PR that adds 5+ private helper methods all called only once, with names like `handleCaseForRpgItemWithBankN()`
- A method extracted from a visitor that has no corresponding test
- The complexity score drops on the parent but the number of lines in the file increases by more than the reduction — complexity was displaced, not eliminated

**Phase to address:**
The S3776 burn-down phase. Gate plan acceptance on: (1) the finding closed, (2) the extracted code is independently testable, (3) the ROM smoke passes.

---

### Pitfall 3: @Suppress Abuse — Silencing Sonar Instead of Fixing It

**What goes wrong:**
A developer adds `@Suppress("CognitiveComplexity")` (or the Sonar NOSONAR comment equivalent) to 46 methods to clear the finding list. The technical debt counter hits zero. But the underlying code is unchanged, the JVM test gaps are unchanged, and future contributors will encounter the same complexity without any diagnostic context. The `@Suppress` also masks legitimate new complexity added later to the same method.

**Why it happens:**
NOSONAR / `@Suppress` is always available and produces an immediate result. For a milestone whose explicit goal is "clear the 46 S3776 HIGHs," the path of least resistance is suppression. The v0.1.0 `detekt.yml` already shows signs of defensive exclusion: `'**/codegen/**'` is excluded from `LongMethod`, `CyclomaticComplexMethod`, `LargeClass`, `TooManyFunctions`, `WildcardImport`, `LoopWithTooManyJumpStatements`, `FunctionOnlyReturningConstant`, and `ReturnCount`. The same over-exclusion pattern must not spread into Sonar suppression.

**How to avoid:**
Establish a strict policy at the start of the phase: Sonar NOSONAR comments are only permitted when the finding is a provable false positive (e.g., a generated or macro-expanded method). For S3776 specifically, NOSONAR is permitted only when the function is covered by an exclusion documented in a reasoning comment. Cap the total number of new NOSONAR annotations for S3776 at a small fixed number (e.g., max 5 across the entire milestone) and require each to have a comment explaining why the complexity is irreducible and how the method is otherwise guarded (existing tests, ROM smoke).

The `@file:Suppress` pattern used in example files for detekt delegate warnings (Phase 13.2) is acceptable for DSL-ergonomics suppressions, but must never be used for codegen complexity.

**Warning signs:**
- A diff that adds NOSONAR to more than 2 methods without extracting any code
- A NOSONAR comment with no explanation
- The Sonar finding list hitting zero on the same day the phase starts (suppression, not remediation)
- Any `@Suppress` annotation added to a file in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/` for complexity-related rules

**Phase to address:**
S3776 burn-down phase. The phase spec must enumerate which findings will be addressed by genuine refactoring vs. which (if any) will be tolerated suppressions, with explicit reasoning for each suppression.

---

### Pitfall 4: Closing a Seed "Already Fixed" Without Reproduction Evidence

**What goes wrong:**
A seed planted during Phase 10–12 is triaged in v0.1.1 as "closed — fixed by Phase 13.7" (or similar). The triage is based on reading the seed text, reading the phase closeout summary, and concluding the fix is complete. No reproduction step is run. In fact, the seed's symptom IS gone — but for a different reason than the Phase 13.x fix (the fix was in a different code path), and the original root cause is still present for a different input configuration. The seed is deleted, and the knowledge of the original defect is lost.

The most dangerous variant: a seed written before later phases shipped that has a "status: dormant" but whose triggering condition has not actually been closed. SEED-004 (metasprites corrupted tile rendering) is status dormant; its trigger was "when Phase 10.1 opens." Phase 10.1 DID fix some metasprites issues, but the full visual parity D-V1 may or may not have been independently verified. Closing it because "Phase 10.1 ran" without re-checking the visual is the failure mode.

**Why it happens:**
Seed backlog drain under milestone deadline pressure pushes toward optimistic closure. The seed text itself may say "trigger: when X phase opens" — if that phase has shipped, a developer reads it as "closed." But "triggered" means the fix was attempted; it does not mean the original symptom is provably gone.

**How to avoid:**
For every seed whose closure argument is "fixed in phase X," the triage must include: (1) identify the commit where the fix landed, (2) verify the fix covers the exact symptom described in the seed (not just a related symptom), (3) for visual seeds, run a MCP screenshot session against the current HEAD ROM. For seeds whose fix involves codegen changes (anything touching `GBDKPipeline`, `MetaspriteVisitor`, `ConvertZoneTilesetsTask`), run a clean `:buildRom` on the affected example as part of the triage.

For the known INVERTED_PALETTE no-op: `AssetPipeline.INVERTED_PALETTE` is defined and documented but has no callers that affect emitted C — it is a dead constant, not a codegen path. Any seed referencing inverted palette behavior should verify whether the fix was in the polarity of the COLOR VALUES in the palette arrays (fixed in Phase 13.3-22/24 and 12.9) vs. a DSL setting (no such setting exists). Do NOT add a test that asserts INVERTED_PALETTE produces inverted output — it does not and must not, per the project memory entry.

**Warning signs:**
- A seed closure that cites a phase number without a commit hash
- A visual-symptom seed (renders inverted, colors wrong, tiles garbled) closed without a screenshot captured at HEAD
- Any seed whose "status" is updated from "dormant" to "closed" without a reproduction step in the triage record
- Closing multiple seeds in a single batch without per-seed evidence

**Phase to address:**
Seed triage phase. Each seed must have a corresponding triage record (even a one-liner commit+screenshot evidence) before the file is deleted from `.planning/seeds/`.

---

### Pitfall 5: Re-Breaking Visual UAT Baselines with "Safe" Refactors

**What goes wrong:**
The detekt cleanup or S3776 refactoring touches `ConvertZoneTilesetsTask`, `ConvertSpritesTask`, or the palette pipeline code in `AssetPipeline.kt`. The ROM builds clean. JVM tests pass. But a visual UAT baseline (a screenshot approved by the user as "pixel-correct") silently becomes invalid because the palette ordering or tile-index mapping shifted. The refactor did not change C source content, but it changed asset-pipeline output that feeds INTO the generated C.

This is the lesson of Phase 13.3 (color refactor): the `gbc()`/`GbcColor`/`gbcHex()` → `Color` namespace migration looked pure — same bit layouts, different names. But it caused an inverted-luminance polarity bug in the BG palette pipeline that only surfaced via live MCP screenshot 3 phases later.

**Why it happens:**
Cleanup work targets Kotlin code quality, not asset-pipeline output. Developers assume "I only changed naming/extraction, not computation." But in the asset pipeline, computation is the accumulation of thresholds, orderings, and array constructions — and refactoring often changes the evaluation order of those accumulations subtly.

**How to avoid:**
Any plan touching `gbkt-core/src/main/kotlin/io/github/gbkt/core/AssetPipeline.kt`, `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt`, or `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt` must run the full 7-target byte-identity sweep as the exit criterion — not just JVM tests. If ANY target changes hash (excluding pong PASS*), the change is not safe until the hash change is explained and approved.

For GBC-palette code specifically (anything touching `Color`, `GBCPalette`, `RGB555`): run the platformer-template in GBC mode and capture a screenshot before and after the refactor. The Phase 13.3 ascending-ramp polarity fix and the Phase 12.9 `-keep_palette_order` pin must not be reverted. Check the commit history of any method being refactored: if it was touched in Phase 12.9, 13.3, 13.6, 13.7, or 13.8, treat it as a visual-regression risk.

**Warning signs:**
- A cleanup diff in `AssetPipeline.kt` that reorders a `sortedBy` or `groupBy` call
- Any change to the luminance-threshold constants or the palette extraction loop in `AssetPipeline`
- A detekt fix that changes a `map { }.flatten()` to a `flatMap { }` inside asset conversion code (semantically identical but triggers re-verification of byte identity)
- Any cleanup in `ConvertZoneTilesetsTask` or `ConvertSpritesTask` without a byte-identity sweep

**Phase to address:**
Both the detekt cleanup phase and the S3776 phase. Impose the byte-identity sweep requirement on any plan touching the asset pipeline path.

---

### Pitfall 6: Detekt Baseline-File Misuse

**What goes wrong:**
The project configures `baseline = file("detekt-baseline.xml")` per module in `build.gradle.kts`. No `detekt-baseline.xml` files currently exist outside `build/` (they are generated on demand). During mass cleanup, a developer generates a baseline to suppress all current violations, commits it, and then fixes violations incrementally. The baseline silently re-suppresses any newly introduced violations of the same rule type in the same file, because the fingerprint matches. Technical debt that should be fixed accumulates invisibly behind the baseline.

**Why it happens:**
`./gradlew detektBaseline` is the fastest way to make a red detekt run go green. The intent is to use it as a starting point and then shrink the baseline. Under time pressure, the baseline is generated, committed, and never reduced.

**How to avoid:**
Do not commit `detekt-baseline.xml` files. Instead, fix violations directly or add explicit detekt rule exclusions in `detekt.yml` for categories that are genuinely out of scope (following the existing pattern for `'**/codegen/**'`). The distinction: a baseline silently suppresses individual violation instances without documentation; a `detekt.yml` exclusion makes a deliberate rule-scope decision visible in code review. Any new rule exclusion added to `detekt.yml` must have a comment explaining why that path is excluded.

If a baseline is needed temporarily during a large cleanup sprint, add it to `.gitignore` rather than committing it, and enforce a policy that the sprint ends only when the baseline is empty.

**Warning signs:**
- `detekt-baseline.xml` files appearing in `git status` for module directories
- A commit that adds a baseline file and then a follow-up commit that "starts reducing it"
- A detekt.yml diff that adds a new `excludes` pattern for a path that didn't have one before, without a comment

**Phase to address:**
The detekt cleanup phase. Define upfront: no baseline files will be committed; violations are fixed or explicitly excluded in `detekt.yml` with rationale.

---

### Pitfall 7: Formatting Churn Polluting Git Blame in Visitor/Pipeline Files

**What goes wrong:**
Detekt's `MaxLineLength` (120 chars, active) or code style fixes touch large blocks in `GBDKPipeline.kt` or `GBDKSystemVisitor.kt`. A reformatting commit introduces hundreds of line-number changes. Future `git blame` on these files no longer identifies the original commit for each line; everything blames the formatting sweep. When a runtime defect is diagnosed in a visitor method, the history needed to understand "why was this decision made?" is gone.

This is a real cost in this codebase, where multi-phase debugging (Phases 12.7–12.11, 13.3–13.8) relied heavily on `git log -S` to bisect which commit introduced a palette inversion or bank assignment regression.

**Why it happens:**
Detekt and spotless reformatting runs produce correct-looking diffs but have no semantic content. They are irresistible to combine with other cleanup work. The temptation is to "clean up while I'm in here."

**How to avoid:**
Separate concerns into distinct commits or plans: one commit that is pure formatting (no logic changes), one commit that is pure logic (no formatting). This allows future reviewers and debuggers to `git log --follow` and skip the formatting commit. For files larger than 1000 lines in `codegen/visitor/` or `codegen/pipeline/`, avoid reformatting unless the logic change requires it — the git blame value of these files outweighs the style benefit.

The spotless plugin (already in the project, version 8.6.0 per PR #61) should be run as a separate dedicated pass, not mixed into the cleanup commits.

**Warning signs:**
- A commit touching 200+ lines in a visitor file but described as a "refactor" — if the actual logic change is 5 lines, the other 195 are formatting churn
- A diff where `+` and `-` lines are identical except for whitespace
- Reformatting mixed into a plan whose primary goal is an S3776 fix

**Phase to address:**
Detekt cleanup phase. Establish a rule: formatting-only commits are labeled `style:` and contain NO logic changes; they are independent and reviewable separately.

---

### Pitfall 8: Cross-Module Detekt Rule Drift

**What goes wrong:**
The project uses a single shared `detekt.yml` (at root), but 19 modules with separate `detekt-baseline.xml` paths. When a detekt fix in one module introduces a pattern (e.g., a new lambda style or a helper function shape) that violates a rule active in another module but suppressed in the first, the build passes in the fixed module but would fail if the same pattern were applied to the second module. The cleanup is inconsistent and makes future cross-module refactors unpredictable.

**Why it happens:**
The shared `detekt.yml` has many per-path exclusions (e.g., `'**/codegen/**'`). A developer working in `gbkt-genre-platformer` may apply a fix that works fine there (because `codegen/**` is excluded from the relevant rule) but would fail in `gbkt-engine` (which is NOT in `codegen/**`). The developer doesn't see the failure because they're not running the engine's detekt check.

**How to avoid:**
Run detekt across all modules in a single pass before and after each cleanup commit: `./gradlew detekt`. The root `build.gradle.kts` applies detekt to all subprojects, so this should be sufficient. Do not run per-module detekt selectively and assume the cross-module result is clean.

When adding a new exclusion to `detekt.yml`, explicitly check whether the new glob matches only the intended module or inadvertently covers other modules. Use the most specific glob pattern possible.

**Warning signs:**
- A detekt fix verified only by running `./gradlew :gbkt-genre-platformer:detekt` instead of `./gradlew detekt`
- A new `detekt.yml` exclusion using a broad path like `'**/dsl/**'` that covers multiple modules
- Detekt clean in the module being modified but the root `./gradlew detekt` is not verified before commit

**Phase to address:**
Detekt cleanup phase. The acceptance criterion for each plan is `./gradlew detekt` clean at the root, not per-module green.

---

### Pitfall 9: Fixing a Seed's Symptom Whose Root Cause Was a Different Already-Fixed Bug

**What goes wrong:**
A seed describes symptom S. A developer reads the seed, runs the current HEAD code, observes symptom S is gone, and closes the seed. But the seed was written when the root cause was bug X, and in the interim, bug X was fixed by a different mechanism that also eliminated symptom S. The developer, not knowing about bug X, adds a "fix" for symptom S that is actually redundant with the existing fix — or worse, re-introduces a version of bug X on a code path not covered by the existing fix.

The INVERTED_PALETTE and platformer color-inversion seeds are the highest-risk examples. Multiple phases (12.9, 13.3, 13.7, 13.8) each touched some part of the palette pipeline. A seed describing "colors inverted" could be closed by Phase 13.7's polarity fix, by Phase 13.3's ascending-ramp fix, by Phase 12.9's `-keep_palette_order` pin, or by all three together. If a developer sees "colors inverted" seed and adds a FOURTH fix for a different code path without understanding which of the three existing fixes actually closed the original symptom, they risk breaking the careful balance established in Phases 12.9–13.8.

**How to avoid:**
For any seed that describes a visual or codegen defect, the triage procedure must include: (a) identify ALL commits between the seed's `planted_during` date and HEAD that touched the relevant code path; (b) determine which commit(s) fixed the symptom; (c) verify the fix is still present and not accidentally reverted. Only then close the seed.

Seeds MUST NOT be "fixed forward" by adding new code that addresses the symptom without understanding the existing fix chain. If the symptom is already gone and the root cause is known, close the seed with a pointer to the commit that fixed it. If the symptom is gone but the root cause is unknown, leave a note and do a brief root-cause trace before closing.

**Warning signs:**
- A seed triage that says "symptom no longer present, closing" with no commit attribution
- A "fix" commit that touches code in a path already covered by a Phase 12.9/13.3/13.7/13.8 change
- Multiple seeds describing the same symptom being closed on the same day without cross-referencing

**Phase to address:**
Seed triage phase. Require commit attribution for every "already fixed" closure.

---

### Pitfall 10: Deleting Doc Sections for APIs That Partially Exist

**What goes wrong:**
`context/DSL_REFERENCE.md` has 13 sections flagged as dead APIs (camera smooth/shake/snapTo, physics `tag()`/`gravityZone()`, flash/fade transitions, etc.). During reconciliation, a developer deletes the entire Camera System section because "it's not implemented." But the basic camera `follow(actor)` IS implemented; only the advanced APIs (deadzone, snapTo, smooth follow builder) are not. Users who need the implemented part can no longer find documentation for it.

**Why it happens:**
The "dead API" classification was made at the section level in the milestone spec, not at the method level within each section. The sections contain a mix of implemented and unimplemented APIs. Deleting the section header causes both to disappear.

**How to avoid:**
For each of the 13 flagged sections, do a source-code audit before editing the docs: `mcp__serena__find_symbol` for each method name, check whether it exists in the DSL builders and has corresponding IR support and codegen. Classify each API within the section individually:
- **Implemented and working:** keep in docs as-is
- **Stubbed but not codegen-complete:** mark with an explicit "Not yet implemented in v0.1.x — planned for v0.2.0" callout; do NOT delete
- **Referenced nowhere in source:** safe to delete from docs, but file as a v0.2.0 feature candidate in a tracking comment

The milestone spec is explicit: "each removed subsystem becomes a tracked v0.2.0 feature candidate." This means a corresponding entry must be created in the v0.2.0 deferred feature list for everything removed from docs, not just silently dropped.

**Warning signs:**
- A DSL_REFERENCE.md diff that removes an entire section (`## Camera System` and everything under it) without verifying per-method implementation status
- A section deletion that removes both "not implemented" callout text AND the code examples for the implemented subset
- A deleted section with no corresponding v0.2.0 tracking entry

**Phase to address:**
DSL_REFERENCE.md reconciliation phase. The acceptance criterion is per-method audit, not per-section deletion.

---

### Pitfall 11: Losing the Spec Value of Aspirational Documentation

**What goes wrong:**
The "not implemented" sections of `DSL_REFERENCE.md` — pathfinding, entity-pool lifecycle callbacks, tweening, full physics world — serve a dual purpose: (1) they warn current users that the API does not work, and (2) they specify what the API SHOULD look like when it is implemented. Deleting them removes the warning (good) but also destroys the design spec (bad). When v0.2.0 tries to implement pathfinding, the developer has no reference for what the DSL surface was intended to be.

**Why it happens:**
The milestone goal is "docs match the implemented DSL." Aspirational sections that describe unimplemented APIs do not match the implemented DSL. The obvious action is to delete them.

**How to avoid:**
Move aspirational API docs to a separate file (`context/DSL_ASPIRATIONAL.md` or directly into the v0.2.0 feature candidate tracking document). Do not delete the design — archive it. The current text in the "not implemented" sections of DSL_REFERENCE.md is more detailed than any other specification document for those features; it represents design decisions that should inform future phases.

For the milestone reconciliation, the correct output is: DSL_REFERENCE.md contains ONLY implemented APIs with accurate documentation; a separate tracking artifact records the API design for each removed section alongside its v0.2.0 candidate status.

**Warning signs:**
- A DSL_REFERENCE.md diff that removes the `## Pathfinding` section entirely with no corresponding v0.2.0 spec document created
- A milestone completion checklist that shows "13 sections removed" without showing "13 feature candidates filed"
- Post-reconciliation, a search for `pathfinding` or `tweening` returning zero results anywhere in `.planning/` or `context/`

**Phase to address:**
DSL_REFERENCE.md reconciliation phase. Define the archive destination for aspirational docs before starting deletions.

---

### Pitfall 12: pluginTest Race Causing False-Positive Red Runs

**What goes wrong:**
The `pluginTest` task has a documented publish/test ordering race (SEED-026, memory entry `project_pr33_sonar_gate_remediation.md`): the mavenLocal republish of the 7 dependency modules and the TestKit IntegrationTest run can interleave, producing a spurious failure. During the hardening milestone, a developer runs `pluginTest` once, sees it fail, concludes a code change broke it, and spends time debugging a false positive.

**Why it happens:**
The race is non-deterministic. A cold maven local or a slow machine makes the interleave more likely. During a cleanup sprint with many small commits, `pluginTest` is run frequently, increasing the probability of hitting the race.

**How to avoid:**
The current documented workaround is "verify via two invocations." Before concluding any `pluginTest` failure is real, run it a second time from clean. If the second run passes, the first was the race. If both fail, the failure is real and must be diagnosed.

Do not attempt to fix the race itself during this milestone — SEED-026 covers it and it requires dedicated plugin-infrastructure work. Flagging the race as a known issue in the phase plan prevents wasted diagnostic time.

For any phase that modifies Gradle plugin code (task registration, plugin wiring), add `validatePlugins` to the acceptance criteria explicitly — this gate is currently not run in CI and `./gradlew :gbkt-gradle-plugin:build` would fail due to the 5 tasks lacking build-cache metadata (SEED-026 debt). Running it during this milestone on a plugin-touching change surfaces existing debt vs. newly introduced debt.

**Warning signs:**
- A `pluginTest` failure on the first invocation after a commit to `gbkt-ir`, `gbkt-lang`, `gbkt-engine`, or `gbkt-world` (these are the 7 mavenLocal dependency modules — most likely race, not logic failure)
- A half-hour debugging session on a `pluginTest` failure that disappears on the second run
- Attempting to `./gradlew :gbkt-gradle-plugin:test` instead of `pluginTest` (wrong task — see CLAUDE.md)

**Phase to address:**
Any phase that modifies code consumed by `pluginTest`. Note the race workaround in every phase plan that touches the plugin path.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| NOSONAR on S3776 findings | Score drops to zero immediately | Complexity stays; new additions to the same method accumulate unseen | Only for provable false positives (e.g., macro-generated code), max 5 per milestone, each with a comment |
| detekt-baseline.xml committed | All detekt violations disappear | Silently re-suppresses new violations of the same type; blocks cleanup progress visibility | Never commit — use `.gitignore` if needed temporarily |
| Closing seeds as "already fixed" without evidence | Backlog shrinks fast | Root cause of visual defects may persist; knowledge lost | Never for visual defects; only for infra/build seeds with clear commit attribution |
| Delete entire DSL doc sections instead of per-method audit | Reconciliation completes quickly | Implemented APIs lose documentation; design specs for v0.2.0 are lost | Never — always audit per method |
| Running per-module detekt instead of root `./gradlew detekt` | Faster feedback | Cross-module rule drift not detected | Never for pre-commit verification |
| Mixing formatting churn with logic changes in same commit | Fewer commits | git blame history destroyed in high-value files | Never for files in `codegen/visitor/` or `codegen/pipeline/` |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Sonar S3776 + detekt CyclomaticComplexMethod | Assuming a detekt `'**/codegen/**'` exclusion also covers Sonar | Sonar runs independently; detekt exclusions have no effect on Sonar findings. Check the Sonar UI directly for the 46 S3776 HIGHs. |
| 7-target ROM sweep | Running `./gradlew clean :a:buildRom` then `./gradlew clean :b:buildRom` in separate invocations | Must be a SINGLE chained invocation: `./gradlew :a:clean :a:buildRom :b:clean :b:buildRom ...` — parallel gradle clean commands cause Kotlin daemon collision |
| byte-identity verification | Comparing ROM hashes before/after refactor and accepting any hash change | Pong hash is non-deterministic (sdcc/lcc toolchain, not gbkt bug) — flag as PASS* rather than investigating. All other ROMs should be byte-identical unless the refactor intentionally changes codegen. |
| GBC mode MCP emulator | Starting emulator without `gbcMode=true` for platformer-template or metasprites | Both ROMs are GBC-compatible targets; DMG-mode captures look green-tinted and falsely read as palette regressions. Always use `emulator_start` with `gbcMode=true` for these ROMs. |
| `./gradlew build` for plugin | Using `./gradlew :gbkt-gradle-plugin:build` directly | This runs `validatePlugins` which is currently RED (5 tasks lack build-cache annotations — SEED-026 debt). Use `pluginTest` for plugin verification per CLAUDE.md. |

---

## "Looks Done But Isn't" Checklist

- [ ] **S3776 refactor:** JVM tests green AND clean `:buildRom` EXIT 0 AND byte-identical to pre-refactor hash. If only JVM tests were checked, the plan is incomplete.
- [ ] **Seed closure:** Visual-symptom seed has a screenshot captured at HEAD (not a commit-message citation). Non-visual seed has a commit hash where the fix landed.
- [ ] **DSL_REFERENCE.md reconciliation:** Every removed section has a corresponding v0.2.0 feature-candidate tracking entry. Verify `grep -c "v0.2.0" context/DSL_REFERENCE.md` increased OR the candidates moved to a tracking file.
- [ ] **Deprecation removal (SEED-023/025):** After removing `whenever` or `combatIsInState(String, String)`, `./gradlew test` is green AND no example uses the removed API (grep examples dir for the removed call site).
- [ ] **Detekt cleanup:** `./gradlew detekt` run at root (not per-module) is green before declaring the cleanup plan done.
- [ ] **INVERTED_PALETTE no-op:** No test was added that asserts `AssetPipeline.INVERTED_PALETTE` produces inverted visual output — this constant has no callers that affect emitted C. Such a test would encode a false guarantee.
- [ ] **pluginTest green:** After any change to library modules, run `pluginTest` TWICE before marking the change done (first failure may be the publish/test ordering race, not a real failure).

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Behavior-changing refactor ships undetected | HIGH — must diagnose which method changed which C path | `git bisect` on the 7-target ROM hash; binary search to find the exact commit; revert to pre-refactor of that method; re-fix using a structure-preserving approach instead of method extraction |
| Seed closed as fixed, symptom returns later | MEDIUM | Reconstruct the seed from git history (it was deleted); identify the new code path that re-introduced the symptom; fix that path; add a JVM-tier emission test that guards the fixed behavior |
| Detekt baseline committed and silencing new violations | LOW-MEDIUM | Delete the baseline file and run `./gradlew detekt`; new violations revealed; fix them or add explicit `detekt.yml` exclusions with comments |
| DSL doc section deleted that had partially-implemented APIs | MEDIUM | Recover from git history; restore the implemented-API subset; re-file the unimplemented subset as v0.2.0 candidates |
| pluginTest race falsely diagnosed as a code regression | LOW | Second invocation passes; document the race occurrence and continue |
| S3776 NOSONAR commit causes Sonar finding count to re-appear after suppression | MEDIUM | Remove the NOSONAR, implement a genuine refactor, re-submit to Sonar |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Behavior-changing complexity refactor | S3776 burn-down (every plan) | ROM smoke + byte-identity sweep is an explicit exit criterion in every plan touching visitor/pipeline files |
| Complexity displacement into opaque helpers | S3776 burn-down (spec) | Every extracted method has an independent JVM test; plan acceptance requires this |
| @Suppress / NOSONAR abuse | S3776 burn-down (spec) | Count of new NOSONAR annotations tracked; cap enforced; each has a reasoning comment |
| Seed closed without evidence | Seed triage (all plans) | Per-seed triage record with commit hash or screenshot; deletions blocked without it |
| Re-breaking visual UAT baselines | Detekt cleanup + S3776 (any plan touching asset pipeline) | 7-target byte-identity sweep required for any plan touching AssetPipeline, ConvertZoneTilesetsTask, ConvertSpritesTask |
| Detekt baseline misuse | Detekt cleanup (phase spec) | `git status` shows no new `detekt-baseline.xml` files; baseline files added to `.gitignore` |
| Formatting churn in visitor/pipeline files | Detekt cleanup (any plan) | Formatting-only commits are labeled `style:` and reviewed separately; no `style:` diff mixed with `refactor:` in same commit |
| Cross-module detekt drift | Detekt cleanup (every plan) | Acceptance criterion includes `./gradlew detekt` root-level green |
| Symptom-fix without root-cause understanding | Seed triage (all visual seeds) | Triage record includes: all relevant commits between planted date and HEAD, which commit closed the symptom |
| API docs deleted for partially-implemented sections | DSL_REFERENCE reconciliation | Per-method source audit before any section removal; tracking entry created for each removed API |
| Aspirational doc value lost | DSL_REFERENCE reconciliation | Archive destination defined before deletions start; every removed aspirational section maps to a v0.2.0 candidate entry |
| pluginTest race false-positive | Any phase modifying library modules | Phase plan documents the race and the two-invocation workaround; no time spent debugging a single-invocation failure |

---

## Sources

- `RETROSPECTIVE.md` (HIGH) — v0.1.0 post-mortem: "paying to confirm the obvious," platformer palette saga, diagnose-first discipline, the full-green release gate as a pattern
- `.planning/verifier-gates.md` (HIGH) — Phase 09.1 incident: 15 JVM truths GREEN, clean buildRom produced link errors from stale generated C; rom-build gate codified as a result
- `.planning/seeds/` corpus (HIGH) — 44 seeds in various states; SEED-004 (dormant, planted Phase 10, metasprites visual parity), SEED-013 (active, GBC palette), SEED-023 (whenever/runIf), SEED-025 (deprecated combat overload), SEED-026 (pluginTest race + validatePlugins)
- `CLAUDE.md` memory section (HIGH) — INVERTED_PALETTE known no-op; pong non-determinism; Kotlin daemon parallel-clean collision; pluginTest ordering race documented
- `detekt.yml` (HIGH) — full exclusion list showing existing over-exclusion pattern; basis for pitfalls 6–8
- `build.gradle.kts` detekt config (HIGH) — per-module baseline path; no baseline files exist outside build/
- `.planning/seeds/SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ.md` (HIGH) — root-cause taxonomy for palette polarity; multiple distinct fix phases; risk of re-breaking during cleanup
- `context/DSL_REFERENCE.md` (HIGH) — section inventory showing mix of implemented and unimplemented APIs in same sections ("not implemented" callouts at lines 1588, 1658, 1708)
- `feedback_dont_pay_to_confirm_obvious.md` (HIGH) — project lesson: Phase 12.8 paid for a regression because plans were structured around "try the cheap fix, let the gate catch it" when diagnostic evidence already said the cheap fix was insufficient
- `feedback_visual_evidence_for_visual_truths.md` (HIGH) — Visual Evidence Rule: for "X is visible on screen" truths, variable-state assertions are insufficient

---
*Pitfalls research for: v0.1.1 hardening milestone — cognitive-complexity burn-down, detekt cleanup, seed backlog closure, DSL doc reconciliation*
*Researched: 2026-06-12*
