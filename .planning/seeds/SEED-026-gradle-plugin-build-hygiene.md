---
id: SEED-026
status: dormant
planted: 2026-06-11
planted_during: post-v0.1.0 maintenance (between milestones)
trigger_when: next time a phase touches gbkt-gradle-plugin build infrastructure, or when a build-hygiene/tooling cluster forms
scope: small
---

# SEED-026: gbkt-gradle-plugin build hygiene — validatePlugins annotations + pluginTest ordering race

**Origin:** PR #61 (spotless 8.6.0 bump) verification, 2026-06-11. `./gradlew :gbkt-gradle-plugin:build` failed at `validatePlugins`; confirmed pre-existing on a clean tree (not caused by the bump).

## Why This Matters

Two pieces of debt live in the same module's build infrastructure and neither is visible in CI:

1. **`validatePlugins` is red.** Five task classes lack build-cache metadata, so
   `:gbkt-gradle-plugin:build` fails for anyone who runs it:
   - `io.github.gbkt.gradle.tasks.BudgetReportTask`
   - `io.github.gbkt.gradle.tasks.CaptureScreenshotTask`
   - `io.github.gbkt.gradle.tasks.CopyGeneratedCTask`
   - `io.github.gbkt.gradle.tasks.DebugEmulatorTask`
   - `io.github.gbkt.gradle.tasks.DiffScreenshotsTask`

   CI never sees it because the plugin gate is `pluginTest` (runs `test`, not
   `validatePlugins`). This also blocks any future Plugin Portal publish, which
   runs plugin validation.

2. **`pluginTest` has a publish/test ordering race** (observed during PR #33
   work, 2026-06-10): the mavenLocal republish of the 7 dependency modules and
   the TestKit `IntegrationTest` run can interleave, so a single invocation can
   fail spuriously. Current workaround is "verify via two invocations".

## The Fix (sketch)

1. Per task, decide the *honest* annotation — not a blanket suppression:
   - Emulator/launcher/interactive tasks (`DebugEmulatorTask`,
     `CaptureScreenshotTask`, `DiffScreenshotsTask`) and console-report tasks
     (`BudgetReportTask`) → `@DisableCachingByDefault(because = "...")`.
   - `CopyGeneratedCTask` is a copy task — plausibly `@CacheableTask`, but
     declaring it requires correct `@PathSensitive` input normalization; verify
     inputs/outputs before promising cacheability.
2. Add `validatePlugins` to the CI plugin gate (or fold it into `pluginTest`'s
   dependencies) so it cannot rot again.
3. Diagnose the `pluginTest` ordering race: make the TestKit test task properly
   `mustRunAfter` / depend on the `publishToMavenLocal` tasks of the 7 modules
   instead of relying on invocation order.

## When to Surface

**Trigger:** next phase that touches `gbkt-gradle-plugin` build infrastructure
(`GbktPlugin.kt`, task registration, `pluginTest` wiring), or when assembling a
build-hygiene/tooling cluster. Also surfaces if Plugin Portal publishing ever
lands on the roadmap — validation is a hard gate there.

## Scope Estimate

**Small** — a few hours. Annotation decisions are deliberate but bounded (5
classes); the ordering-race fix needs a short investigation plus task-dependency
wiring. Exit criteria: `./gradlew :gbkt-gradle-plugin:build` green including
`validatePlugins`; `pluginTest` passes deterministically on a single cold
invocation; CI runs the validation.

## Breadcrumbs

- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/` — the 5 task classes
- `gbkt-gradle-plugin/build.gradle.kts` — `java-gradle-plugin` registers `validatePlugins`
- Root `build.gradle.kts` — `pluginTest` task wiring (publish-then-test sequence)
- CLAUDE.md "Gradle-plugin tests: use pluginTest" note — documents the stale-mavenLocal
  constraint that shapes the race fix
- Memory: `project_pr33_sonar_gate_remediation.md` records the pluginTest race observation
