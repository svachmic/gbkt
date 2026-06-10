---
phase: quick-260605-eqr
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt
  - build.gradle.kts
autonomous: true
requirements: [QUICK-EQR-ITEM1, QUICK-EQR-ITEM2, QUICK-EQR-ITEM3]
must_haves:
  truths:
    - "ConvertZoneTilesetsTaskTest passes 11/11 — the missing-tilemap case surfaces the zone-scoped 'Zone <id> tilemap PNG not found' message, not the lower-layer 'Can't read input file!'"
    - "Running :gbkt-gradle-plugin:test from a stale ~/.m2 no longer produces the 13 IntegrationTest compile failures — the consumed modules are republished to mavenLocal before the TestKit sandbox resolves them"
    - "The generateC stale-file / whenever{} concern is either fixed with a minimal correct change OR documented as a deferred item with a clear rationale and follow-up note"
  artifacts:
    - path: "gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt"
      provides: "Hoisted zone-scoped tilemap-PNG existence guard before the size-derivation ImageIO.read"
      contains: "tilemap PNG not found"
    - path: "build.gradle.kts"
      provides: "Composite-build wiring so the included gbkt-gradle-plugin :test task depends on publishToMavenLocal of the consumed modules"
      contains: "includedBuild"
  key_links:
    - from: "ConvertZoneTilesetsTask.convertZoneTilesets()"
      to: "convertOneTileset() require(tilemapPngFile.isFile)"
      via: "hoisted guard runs BEFORE the derivedDims ImageIO.read"
      pattern: "require\\(.*tilemapPngFile.*exists"
    - from: "root build.gradle.kts"
      to: "gbkt-gradle-plugin :test"
      via: "gradle.includedBuild(\"gbkt-gradle-plugin\").task(\":test\").dependsOn(... :publishToMavenLocal)"
      pattern: "includedBuild\\(\"gbkt-gradle-plugin\"\\)"
---

<objective>
Fix three test-infra issues surfaced during a test-suite triage (13 of 15 failures were stale-mavenLocal noise, not product bugs).

Purpose: One real codegen-guard regression (Item 1), one durable build-wiring fix for the TestKit+mavenLocal pattern (Item 2), and one investigate-then-decide item for a `generateC` stale-file/`whenever{}` concern (Item 3).

Output:
- A hoisted zone-scoped existence guard in `ConvertZoneTilesetsTask` (Item 1)
- Root-build composite wiring so `:gbkt-gradle-plugin:test` always resolves CURRENT artifacts from mavenLocal (Item 2)
- Either a minimal correct fix or a documented deferral for the `generateC` concern (Item 3)
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/STATE.md
@gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt
@gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt
@gbkt-gradle-plugin/build.gradle.kts
@build.gradle.kts
@settings.gradle.kts

# Project conventions
@CLAUDE.md

# Investigation findings (already confirmed during planning — do NOT re-derive):
# ITEM 1 ROOT CAUSE (CONFIRMED by running the test):
#   ConvertZoneTilesetsTask.convertZoneTilesets() computes `derivedDims` at lines ~205-214 by
#   calling `ImageIO.read(tilemapPngFile)` UNCONDITIONALLY (it runs even when explicit
#   mapWidth/mapHeight are supplied). When the tilemap PNG is missing, ImageIO.read throws
#   "Can't read input file!" — a lower-layer message — BEFORE convertOneTileset() reaches its
#   zone-scoped guard `require(tilemapPngFile.isFile) { "Zone $zoneId tilemap PNG not found ..." }`
#   at lines ~272-275. The test `throws GradleException when tilemapPath is set but file is missing`
#   (ConvertZoneTilesetsTaskTest.kt:961-970) asserts the message contains "play_zone" and fails
#   at line 963 because the surfaced message is "Can't read input file!".
#
# ITEM 2 ROOT CAUSE (CONFIRMED): IntegrationTest writes a TestKit sandbox build whose
#   `createBasicBuildFile()` (IntegrationTest.kt:533+) declares:
#     implementation("io.github.gbkt:gbkt-core:0.1.0-SNAPSHOT")
#     implementation("io.github.gbkt:gbkt-backend-api:0.1.0-SNAPSHOT")
#     runtimeOnly("io.github.gbkt:gbkt-backend-gbdk:0.1.0-SNAPSHOT")
#   resolved from mavenLocal(). Transitive deps of gbkt-core: gbkt-ir, gbkt-lang, gbkt-engine,
#   gbkt-world. Fixtures use `start = mainScene` (SceneRef) + `whenever{}`; against a stale
#   ~/.m2 where GameBuilder.start was still `String?`, the sandbox Kotlin compile fails.
#   COMPOSITE-BUILD NUANCE: gbkt-gradle-plugin is `includeBuild(...)` in settings.gradle.kts:17;
#   the library modules are regular `include(...)`. A plain `dependsOn(":gbkt-core:publishToMavenLocal")`
#   from inside the included build CANNOT cross into the root build. The wiring MUST live in the
#   ROOT build.gradle.kts via `gradle.includedBuild("gbkt-gradle-plugin").task(":test")`. All
#   consumed modules apply the `gbkt.publishing` convention plugin, so each exposes
#   `publishToMavenLocal`.
#
# ITEM 3 (INVESTIGATE-THEN-DECIDE): the "generateC deletes stale files" path is
#   GenerateCTask.syncOutputDir(outputDir, emittedSet) (GenerateCTask.kt:~329/431). The
#   diagnosis reports a runtime generateC failure on a fixture's `whenever{}` op. This is NOT
#   confirmed to be a product bug — investigate first, then either land the smallest correct fix
#   or capture it as a deferred item. Do NOT blind-patch.
</context>

<tasks>

<task type="auto">
  <name>Task 1: Hoist zone-scoped tilemap-PNG existence guard before size derivation (Item 1)</name>
  <files>gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt</files>
  <action>
In `convertZoneTilesets()`, the `derivedDims` block (around lines 205-214) calls `ImageIO.read(tilemapPngFile)` before any zone-scoped existence check, so a missing tilemap PNG raises the lower-layer "Can't read input file!" instead of the intended zone-scoped diagnostic.

Insert a `require(tilemapPngFile.exists())` guard for the non-null `tilemapPngFile` case IMMEDIATELY BEFORE the `derivedDims` computation (i.e. before the `ImageIO.read` at ~line 208), regardless of whether `explicitSize` is non-null. Reuse the EXACT same zone-scoped message wording already used by the downstream guard in `convertOneTileset` so both paths agree: `"Zone $zoneId tilemap PNG not found at ${tilemapPngFile.absolutePath} (Phase 12.2 REQ-4)"`. Use `tilemapPngFile.isFile` (matching the convertOneTileset guard's predicate) for consistency.

Guard the new `require` inside the existing `if (tilemapPngFile != null)` shape or use a safe-call so the tileset-only-zone path (null tilemap) is unaffected. Do NOT remove the existing downstream guard in `convertOneTileset` — leave it as defense-in-depth; the hoisted guard simply runs first so the zone-scoped message wins.

Respect CLAUDE.md Project Rule #1 (no magic strings): the message already references `$zoneId` (derived from metadata `id`), not a literal — keep it that way.
  </action>
  <verify>
    <automated>./gradlew :gbkt-gradle-plugin:test --tests "*ConvertZoneTilesetsTaskTest*" --rerun-tasks</automated>
  </verify>
  <done>ConvertZoneTilesetsTaskTest passes 11/11. The previously-failing `throws GradleException when tilemapPath is set but file is missing` now passes because the surfaced exception message contains `play_zone` (zone-scoped) instead of `Can't read input file!`.</done>
</task>

<task type="auto">
  <name>Task 2: Wire :gbkt-gradle-plugin:test to publishToMavenLocal of consumed modules (Item 2)</name>
  <files>build.gradle.kts</files>
  <action>
Make the composite-build's `:gbkt-gradle-plugin:test` task depend on `publishToMavenLocal` of every module the IntegrationTest TestKit sandbox resolves from mavenLocal, so the sandbox always compiles against CURRENT artifacts (closing the 13 stale-mavenLocal IntegrationTest failures durably).

In the ROOT `build.gradle.kts`, register the cross-build dependency. Because `gbkt-gradle-plugin` is an INCLUDED build (`includeBuild` in settings.gradle.kts) and the library modules are regular `include(...)` projects, the wiring must be expressed from the root build's side using the included-build task handle. Add (at root build script top-level, after `subprojects { }` or in a dedicated block):

    val mavenLocalModulesForPluginTest = listOf(
        ":gbkt-ir", ":gbkt-lang", ":gbkt-engine", ":gbkt-world",
        ":gbkt-core", ":gbkt-backend-api", ":gbkt-backend-gbdk",
    )
    gradle.includedBuild("gbkt-gradle-plugin").task(":test").let { pluginTest ->
        mavenLocalModulesForPluginTest.forEach { path ->
            // dependsOn accepts a TaskReference for cross-build ordering
        }
    }

Resolve the precise Gradle API for declaring an included-build task's dependency on root-build tasks. The supported mechanism is to make the root modules' `publishToMavenLocal` tasks run BEFORE the included `:test` task. If the direct `IncludedBuild.task(":test")` handle does not accept `dependsOn` of root-project tasks (Gradle restricts included→root edges), invert the edge: declare it from the consuming side is not possible across the composite boundary, so instead wire it so that invoking the plugin test goes through a root lifecycle task. Concretely, prefer this robust pattern:

    project(":gbkt-core").tasks.named("publishToMavenLocal")  // and the other 6 modules
    // then:
    gradle.includedBuild("gbkt-gradle-plugin").task(":test")
        .dependsOn(/* the 7 publishToMavenLocal TaskReferences */)

Validate the exact API against this Gradle version by running the verify command. If `IncludedBuild.task(...).dependsOn(...)` is not available for root→included edges in this Gradle, fall back to the documented composite pattern: register a root aggregator task `publishConsumedModulesToMavenLocal` that `dependsOn` the 7 `:<module>:publishToMavenLocal` tasks, and make the included build's test depend on it via `gradle.includedBuild("gbkt-gradle-plugin").task(":test").dependsOn(rootAggregator)`. Whichever form compiles and orders correctly is acceptable — the load-bearing outcome is: a clean `:gbkt-gradle-plugin:test` invocation republishes gbkt-ir/lang/engine/world/core/backend-api/backend-gbdk to mavenLocal first.

Keep module list as a named `val` (not inline magic strings scattered) and add a one-line comment explaining WHY (TestKit sandbox resolves these SNAPSHOTs from ~/.m2; stale artifacts cause fixture compile failures). Do NOT add publishing to modules — they already apply `gbkt.publishing`.

If, after investigation, the cleanest durable wiring belongs in `gbkt-gradle-plugin/build.gradle.kts` instead (e.g. via `tasks.test { dependsOn(gradle.includedBuilds...) }` is not valid from the included side — so it must be root), keep it in root `build.gradle.kts` as planned.
  </action>
  <verify>
    <automated>./gradlew :gbkt-gradle-plugin:test --tests "*IntegrationTest*" --dry-run 2>&1 | grep -iE "publishToMavenLocal|gbkt-core" || echo "NO-PUBLISH-EDGE-VISIBLE"</automated>
  </verify>
  <done>A `--dry-run` of `:gbkt-gradle-plugin:test` lists `publishToMavenLocal` tasks for the consumed root modules (gbkt-core/backend-api/backend-gbdk + transitive gbkt-ir/lang/engine/world) scheduled BEFORE the test task, proving the durable wiring is in place. (A full run from a deliberately-stale ~/.m2 would no longer reproduce the 13 IntegrationTest compile failures.)</done>
</task>

<task type="auto">
  <name>Task 3: Investigate generateC stale-file / whenever{} failure, then fix-minimally OR defer (Item 3)</name>
  <files>gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt</files>
  <action>
DIAGNOSE FIRST — do not blind-patch. The triage reported a runtime `generateC` failure (referenced near GenerateCTask.kt:190, which is the reflective `Class.forName`/getter-invocation region) on a fixture's `whenever{}` op, and noted "generateC deletes stale files" (the `syncOutputDir(outputDir, emittedSet)` mechanism at ~lines 329/431).

Steps:
1. Run the IntegrationTest cases that exercise `whenever{}` fixtures AFTER Task 2 has republished current artifacts to mavenLocal — specifically `complex game configuration generates valid C code` and any case using `createGameWithSpritesFixture`/the multi-`whenever` fixtures (IntegrationTest.kt ~623-769). Command: `./gradlew :gbkt-gradle-plugin:test --tests "*IntegrationTest*" --rerun-tasks`.
2. CLASSIFY the outcome:
   a. If ALL IntegrationTest cases now pass once mavenLocal is current (Task 2's wiring), then the original `whenever{}` failure was ANOTHER facet of the stale-mavenLocal root cause (old DSL surface lacked the current `whenever`/`start` shapes). In that case NO code change to GenerateCTask is warranted — record this conclusion.
   b. If a specific IntegrationTest case STILL fails with current artifacts, read the failing stacktrace and `syncOutputDir` (GenerateCTask.kt:~431-end) to determine whether `syncOutputDir` deletes a file it should keep (or vice versa) on the `whenever{}` path. Only if it is a GENUINE product bug, land the SMALLEST correct change to `syncOutputDir`'s keep/delete set (or the reflective generate path), preserving the `emittedSet`-based contract.
3. If the investigation reveals the issue is out-of-scope (needs deeper work beyond a one-file minimal change) or is purely a fixture artifact, DO NOT force a fix. Instead capture it as a deferred item: append a short `## Deferred — Item 3 (generateC stale-file / whenever{})` section to the SUMMARY with: observed behavior, the exact failing case (if any), the classification (a or b above), and a concrete follow-up recommendation (e.g. "route to its own short investigation phase" or "no action — was stale-mavenLocal").

The done condition is satisfied by EITHER a minimal correct fix with a passing test OR a clear documented deferral — both are acceptable per the task framing.
  </action>
  <verify>
    <automated>./gradlew :gbkt-gradle-plugin:test --tests "*IntegrationTest*" --rerun-tasks 2>&1 | tail -20</automated>
  </verify>
  <done>The `whenever{}`/generateC concern is resolved one of two ways: (1) IntegrationTest passes with current mavenLocal artifacts and the SUMMARY records it as a stale-mavenLocal facet (no code change), OR (2) a genuine minimal fix to GenerateCTask landed with the previously-failing case now green, OR (3) the SUMMARY contains a `## Deferred — Item 3` section with observed behavior, classification, and a concrete follow-up. No blind patch was applied.</done>
</task>

</tasks>

<verification>
- `./gradlew :gbkt-gradle-plugin:test --tests "*ConvertZoneTilesetsTaskTest*"` passes 11/11 (Item 1).
- `./gradlew :gbkt-gradle-plugin:test --tests "*IntegrationTest*" --dry-run` shows the consumed modules' `publishToMavenLocal` scheduled before `:test` (Item 2).
- `./gradlew :gbkt-gradle-plugin:test` (full plugin suite) is green OR any remaining failure is documented as a deferral in the SUMMARY (Item 3).
- No magic strings introduced (CLAUDE.md Project Rule #1); zone-scoped message keeps `$zoneId` interpolation.
</verification>

<success_criteria>
- Item 1: missing-tilemap case surfaces the zone-scoped "Zone <id> tilemap PNG not found" message; ConvertZoneTilesetsTaskTest 11/11 green.
- Item 2: root-build composite wiring republishes gbkt-ir/lang/engine/world/core/backend-api/backend-gbdk to mavenLocal before `:gbkt-gradle-plugin:test`, durably eliminating the 13 stale-mavenLocal IntegrationTest failures.
- Item 3: the generateC `whenever{}` concern is either minimally fixed (with a green test) or explicitly deferred with rationale and a follow-up note — never blind-patched.
</success_criteria>

<output>
Create `.planning/quick/260605-eqr-fix-three-test-infra-issues-convertzonet/260605-eqr-SUMMARY.md` when done. The SUMMARY MUST include the Item 3 classification/deferral note.
</output>
