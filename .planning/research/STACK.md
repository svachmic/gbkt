# Stack Research

**Domain:** Kotlin DSL-to-C compiler pipeline — v0.1.1 Hardening tooling
**Researched:** 2026-06-12
**Confidence:** HIGH (all three areas verified against official sources, existing codebase, and community discussions)

---

## Context: Hardening-Specific Scope

v0.1.0 shipped the full compiler pipeline. v0.1.1 is pure internal hardening: drain 44 seeds, remove 2 deprecated DSL APIs, reconcile docs, QUAL-01..03 cleanup, and burn down 46 SonarCloud S3776 HIGH findings. This research answers three specific questions:

1. How to run detekt systematically across 19 subprojects + the composite build
2. What tooling supports the S3776 cognitive-complexity burn-down safely
3. Whether `@Deprecated` mechanics and a binary-compatibility validator are worth adding

**Verdict up front:** No new Gradle plugin or library dependencies are needed for the hardening work. The changes are configuration-only — one composite-build detekt wiring fix, incremental `detekt.yml` exclusion removal, and pure refactoring discipline for S3776.

---

## Recommended Stack

### Core Technologies (unchanged)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Kotlin JVM | 2.3.20 | Language for all modules | Already in use; stay here for v0.1.1 |
| Gradle | 9.5.1 | Build system | Already in use; stay here for v0.1.1 |
| JVM target | 21 | Runtime | Already in use |

### Static Analysis (existing — configuration changes only)

| Technology | Version | Purpose | Hardening Action |
|------------|---------|---------|-----------------|
| detekt | 1.23.8 | Kotlin static analysis | Keep; fix composite-build wiring (see below). Do NOT upgrade to 2.0.0-alpha during hardening. |
| Spotless + ktfmt | 8.6.0 / 0.63 | Code formatting | No change needed |
| SonarCloud + sonarqube plugin | 7.3.1.8318 | Quality gate, S3776 source | No change needed |
| Kover | 0.9.4 | Coverage for Sonar gate | No change needed |

### Supporting Libraries (no additions for v0.1.1)

| Library | Decision | Reason |
|---------|----------|--------|
| kotlinx-binary-compatibility-validator 0.18.1 | **DO NOT ADD** | In maintenance mode (JetBrains halted new features); project constraint explicitly says "backward compatibility: none required"; adding an enforcement tool to a library with no-compat guarantee creates friction without value. Revisit at 1.0. |
| KGP built-in ABI validation (`kotlin { abi { } }`) | **DO NOT ADD** | Experimental as of KGP 2.2.0, API will change; same reasoning as above — not meaningful for pre-1.0 breaking-changes-acceptable release policy. |
| detekt 2.0.0-alpha.3 | **DO NOT UPGRADE** | Alpha status; the official compatibility table shows 1.23.8 targets Gradle 8.12.1 / Kotlin 2.0.21, but it is working in this project (CI green) and the risk of a mid-hardening tooling regression from an alpha upgrade outweighs any benefit. Track for post-stable upgrade. |
| SonarQube for IDE (IntelliJ plugin) | **Developer tool only — no build change** | Shows S3776 findings in-editor locally, matching SonarCloud's algorithm. Useful for "fix locally, verify in IDE, push to confirm" flow. Note: known issue in 2025 where S3776 is not always reported in the IntelliJ plugin; fall back to SonarCloud scan as the authoritative source. |

---

## Configuration Changes Required

### 1. Wire detekt into the composite build (SEED-026 prerequisite)

The root `build.gradle.kts` applies detekt to all subprojects via `subprojects {}`. The `gbkt-gradle-plugin` is an **included build**, not a subproject, so the root's `subprojects {}` block does not reach it. CI_CD.md confirms: "the plugin is an included build, so it must be addressed explicitly; it does not apply detekt — tracked debt."

The fix is to add detekt directly to `gbkt-gradle-plugin/build.gradle.kts`, mirroring how spotless is already applied there. The composite build's `settings.gradle.kts` already imports the root version catalog (`from(files("../gradle/libs.versions.toml"))`), so `libs.plugins.detekt` is available.

```kotlin
// gbkt-gradle-plugin/build.gradle.kts — add alongside the existing spotless alias:
plugins {
    // ... existing plugins ...
    alias(libs.plugins.detekt)
}

// Add detekt configuration block (after the existing spotless block):
configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.files("../detekt.yml"))   // root config
    buildUponDefaultConfig = true
    parallel = true
    baseline = file("detekt-baseline.xml")
}
```

Then add `:gbkt-gradle-plugin:detekt` to the CI `code-quality` job alongside the existing `:gbkt-gradle-plugin:spotlessCheck`:

```yaml
# .github/workflows/kotlin.yml — code-quality job:
run: ./gradlew detekt spotlessCheck :gbkt-gradle-plugin:detekt :gbkt-gradle-plugin:spotlessCheck
```

### 2. Detekt baseline strategy for QUAL-01 cleanup

**There are no existing `detekt-baseline.xml` files in the project.** CI is green because all legitimate architectural violations are already excluded in `detekt.yml` (codegen/ir/dsl/validation packages). This means QUAL-01 cleanup is NOT a baseline-drain exercise — it is an exclusion-removal exercise.

The correct approach:

1. Identify which exclusion blocks in `detekt.yml` cover genuinely fixable violations (e.g., `CyclomaticComplexMethod` excluded from `'**/codegen/**'` covers functions that CAN be refactored).
2. Remove one exclusion block.
3. Run `./gradlew detekt` — violations are now reported as errors (since `maxIssues: 0`).
4. Fix the violations. Techniques: extract `private` helper methods; split large `when` arms into named functions; introduce intermediate data structures to reduce parameter counts.
5. Re-run `./gradlew detekt` — green.
6. Commit. The exclusion is gone permanently.

**Do NOT use `detektBaseline`** for this workflow. Generating a baseline would snapshot the current violations and hide them, defeating the purpose of cleanup. The baseline mechanism is for violations you are tracking but not yet fixing — not for a cleanup phase.

**For the composite build**, after adding detekt (step 1 above): if `./gradlew :gbkt-gradle-plugin:detekt` reveals violations, fix them in the same phase. The `detekt.yml` exclusions already cover the `kotlin-dsl` generated code patterns.

### 3. S3776 cognitive-complexity burn-down approach

**No new tooling is needed.** SonarCloud already reports all 46 HIGH findings with function name, file, and exact complexity score. The workflow is:

1. Get the findings list from SonarCloud (filter: Rule=S3776, Severity=HIGH, Status=Open).
2. Sort by complexity score descending — tackle the worst ones first for maximum impact.
3. For each function: extract `private` helper methods until the cognitive-complexity score drops below SonarCloud's threshold (15 for Kotlin).
4. Before refactoring any function: ensure it has unit test coverage. Kover reports coverage per function — use this to identify untested functions before touching them (an untested refactor is riskier than the complexity itself).
5. After fixing a batch: push a commit, wait for the SonarCloud CI scan, verify the finding count dropped.

**Cognitive complexity vs cyclomatic complexity — Kotlin-specific notes:**
- Sonar's S3776 counts NESTING INCREMENTS, not branch counts. A flat `when(x) { A -> ... B -> ... }` expression does NOT increment S3776. What increments it: `if` inside a `when` arm, `when` nested inside another `when`, `try/catch` blocks, loops, lambdas passed as arguments.
- The codegen visitor functions are the expected hotspot: they have `when(op)` expressions where each arm contains `if` guards and nested `when` for sub-cases.
- Extraction pattern: move each `when` arm's body into a `private fun handleXxx(op: XxxOp)`. Each extracted function drops the parent's score by the complexity of that arm.

**Coverage gate:** The existing Sonar quality gate already requires coverage to not decline. Before removing complexity from functions with low coverage, add tests first. Kover + Sonar integration handles this automatically — a PR that drops coverage triggers a gate failure.

---

## Deprecation Mechanics for SEED-023 and SEED-025

### SEED-025: `combatIsInState(String, String)` — removal in v0.2.0

Already deprecated in v0.1.0 with `@Deprecated(...)`. The v0.1.1 action is verification only:

- Confirm the existing `@Deprecated` annotation includes `replaceWith = ReplaceWith("combatIsInState(CombatStateId, BattleRef)")`.
- Confirm `level = DeprecationLevel.WARNING` (which is the default — users can still call it and see a compiler warning; they can suppress it).
- No v0.1.1 change to the deprecation level. Removal happens in v0.2.0.
- Optionally: mark the SonarCloud S1133 "remove this deprecated code" Info issue as "Accepted" in the SonarCloud UI with a note pointing to SEED-025 and the v0.2.0 removal train.

### SEED-023: `whenever` → `runIf` unification

Discuss-phase decision first (as SEED-023 calls for), then v0.1.1 execution:

**If deprecating `whenever`:**
```kotlin
@Deprecated(
    message = "whenever() re-tests every frame and is semantically identical to runIf(). " +
        "Use runIf(condition) { } instead, which reads as the imperative single-frame conditional it is.",
    replaceWith = ReplaceWith("runIf(condition, block)"),
    level = DeprecationLevel.WARNING,
)
fun ScriptBuilder.whenever(condition: Expr, block: ScriptBuilder.() -> Unit) { ... }
```

The `ReplaceWith` annotation enables IDE auto-migration (Ctrl+Alt+Shift+I → "Replace deprecated calls"). This is the primary value: users can migrate all call sites in one action.

**Removal timing:** Since the project has no backward-compatibility guarantees (`constraints: "Backward compatibility: None required — breaking changes are acceptable during rebuild"`), removal can happen in v0.2.0 — or even in v0.1.1 itself if the discuss-phase confirms no external adopters. For an internal API change, the `WARNING` → removal path is appropriate without the intermediate `ERROR` level.

**DeprecationLevel guidance for this project:**
- `WARNING` → removal in next minor: correct for pre-1.0 APIs where you own all call sites
- `ERROR` → removal: use when you want to block new callsites from compiling before removing (useful when external users might have called it before the deprecation warning)
- `HIDDEN` → removal: use when you need binary compatibility across a release boundary (not applicable here — no compat guarantees)
- Skip `ERROR` and `HIDDEN` for v0.1.1: go `WARNING` then remove in v0.2.0

---

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| Stay on detekt 1.23.8 | Upgrade to detekt 2.0.0-alpha.3 | Alpha status during a hardening milestone is wrong risk profile; 1.23.8 is already working |
| Remove `detekt.yml` exclusions + fix | Generate per-module baselines | Baselines hide violations; the goal of QUAL-01 is to eliminate them, not suppress them |
| Pure refactoring for S3776 | kotlin-complexity-reductions Gradle plugins (none mainstream) | No mature Kotlin-specific tool exists; the refactoring is straightforward extract-method work |
| Skip binary-compat-validator | Add kotlinx-binary-compatibility-validator 0.18.1 | Library is in maintenance mode; project has no compat guarantees; it would block legitimate API evolution |
| `WARNING` → removal for deprecations | Full `WARNING` → `ERROR` → `HIDDEN` → removal ladder | Overkill for pre-1.0 library owning all call sites; adds two extra release cycles for no benefit |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `kotlinx-binary-compatibility-validator` | In maintenance mode; JetBrains has stopped new feature development; wrong fit for pre-1.0 no-compat-guarantees library | Revisit when approaching 1.0 and making public API stability promises |
| KGP built-in ABI validation (`kotlin { abi { } }`) | Experimental in KGP 2.2.0+, API will change; same no-compat reasoning | Same: revisit at 1.0 |
| `detektBaseline` for QUAL-01 cleanup | Baselines snapshot and hide violations; this is not the goal of a cleanup phase | Remove exclusions from `detekt.yml` + fix violations directly |
| detekt 2.0.0-alpha.x | Alpha quality risk during a hardening milestone | detekt 1.23.8 is already working; track stable detekt 2.0.0 release (no ETA as of June 2026) for post-v0.1.1 upgrade |

---

## Version Compatibility

| Package | Version Used | Officially Tested Against | Notes |
|---------|-------------|--------------------------|-------|
| detekt | 1.23.8 | Gradle 8.12.1, Kotlin 2.0.21 | Working in practice with Gradle 9.5.1 / Kotlin 2.3.20 (CI green). Known gap: not official for 2.3.x. detekt 2.0.0-alpha.3 is the first version targeting Gradle 9.3.1 / Kotlin 2.3.21. |
| detekt 2.0.0-alpha.3 | NOT IN USE | Gradle 9.3.1, Kotlin 2.3.21 | Alpha. Close version match but risky for production hardening work. |
| SonarQube (Gradle plugin) | 7.3.1.8318 | Gradle 9.x | No known issues. |
| Kover | 0.9.4 | Kotlin 2.3.x, Gradle 9.x | No known issues. |

---

## Sources

- detekt compatibility table — official supported Gradle/Kotlin versions per release: https://detekt.dev/docs/introduction/compatibility/
- detekt GitHub discussion #9170: "Detekt compatibility with Newer Kotlin 2.3.20" — maintainer confirms 1.23.8 does not officially support 2.3.20; alpha version supports it: https://github.com/detekt/detekt/discussions/9170
- detekt baseline documentation — per-module vs unified baseline strategy; draining baselines: https://detekt.dev/docs/introduction/baseline/
- detekt releases — v2.0.0-alpha.3 built against Kotlin 2.3.21 / Gradle 9.3.1 (April 2026): https://github.com/detekt/detekt/releases
- kotlinx-binary-compatibility-validator GitHub — maintenance mode status, recommendation to use KGP built-in ABI validation instead: https://github.com/Kotlin/binary-compatibility-validator
- KGP built-in ABI validation docs (experimental, Kotlin 2.2.0+): https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html
- Kotlin `@Deprecated` DeprecationLevel documentation: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-deprecated/
- Android API guidelines on deprecation cycles (WARNING → ERROR → removal): https://android.googlesource.com/platform//frameworks/support/+/HEAD/docs/api_guidelines/deprecation.md
- SonarCloud S3776 rule — cognitive complexity threshold 15 for Kotlin: https://cloud-ci.sgs.com/sonar/coding_rules?open=kotlin:S3776&rule_key=kotlin:S3776
- SonarQube for IDE IntelliJ plugin (formerly SonarLint): https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide
- Sonar community discussion: S3776 not always reported in IntelliJ plugin (2025 known issue): https://community.sonarsource.com/t/not-reporting-cognitive-complexity-of-methods/135090
- Existing `detekt.yml` in repo — current exclusion config: `/Users/michalsvacha/GitHub/personal/gbkt/detekt.yml`
- Root `build.gradle.kts` — `subprojects {}` detekt wiring, composite-build gap: `/Users/michalsvacha/GitHub/personal/gbkt/build.gradle.kts`
- `gbkt-gradle-plugin/settings.gradle.kts` — confirms version catalog is imported from root: `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-gradle-plugin/settings.gradle.kts`

---
*Stack research for: gbkt v0.1.1 Hardening — detekt, S3776, deprecation tooling*
*Researched: 2026-06-12*
