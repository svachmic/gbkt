---
quick_task: 260605-eqr
type: quick-execute
completed: 2026-06-05
duration_minutes: 35
branch: feat/d_and_d_gaps
commits:
  - hash: 0c9a5679
    message: "fix(260605-eqr): hoist zone-scoped tilemap-PNG guard before ImageIO.read (Item 1)"
  - hash: c512064b
    message: "fix(260605-eqr): wire publishConsumedModulesToMavenLocal + pluginTest lifecycle tasks (Item 2)"
  - hash: 5378fdea
    message: "fix(260605-eqr): fix createTwoSceneGameFixture forward-reference NPE in generateC test (Item 3)"
files_modified:
  - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt
  - build.gradle.kts
  - gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt
---

# Quick Task 260605-eqr Summary

**One-liner:** Hoisted zone-scoped tilemap-PNG guard before ImageIO.read in ConvertZoneTilesetsTask, wired a `pluginTest` root lifecycle task to publish SNAPSHOT modules before plugin tests, and fixed a forward-reference NPE in the `createTwoSceneGameFixture` that was blocking the generateC stale-file test.

## Item 1 — ConvertZoneTilesetsTask tilemap-PNG guard (FIXED)

**File:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt`
**Commit:** `0c9a5679`

The `derivedDims` block (inside `convertZoneTilesets()`) called `ImageIO.read(tilemapPngFile)` unconditionally before `convertOneTileset()` ran its zone-scoped `require(tilemapPngFile.isFile)` guard. When the tilemap PNG was missing, `ImageIO` threw `"Can't read input file!"` — a lower-layer message that contained no zone ID — before the zone-scoped diagnostic could fire.

**Fix:** Added a `require(tilemapPngFile.isFile)` guard inside the existing `if (tilemapPngFile != null)` shape, immediately after the path-traversal check and before the `derivedDims` block. Uses the identical message wording as the downstream `convertOneTileset()` guard (`"Zone $zoneId tilemap PNG not found at ..."`) so both paths agree. The downstream guard remains as defence-in-depth.

**Verification:** `ConvertZoneTilesetsTaskTest` 11/11 GREEN. The previously-failing
`throws GradleException when tilemapPath is set but file is missing` now surfaces
`play_zone` in the exception message instead of `Can't read input file!`.

## Item 2 — Composite-build wiring to prevent stale-mavenLocal IntegrationTest failures (FIXED)

**File:** `build.gradle.kts`
**Commit:** `c512064b`

**Architecture constraint:** `gbkt-gradle-plugin` is `includeBuild` in `pluginManagement` (settings.gradle.kts:17). Gradle 9 `IncludedBuild.task()` returns a `TaskReference` that can only be used AS a `dependsOn` target — the caller cannot add dependencies TO the included build's task from the root build. The plan's original "make `:gbkt-gradle-plugin:test` itself depend on publishToMavenLocal" is not achievable via the Gradle composite API; `--dry-run` of `:gbkt-gradle-plugin:test` correctly returns `NO-PUBLISH-EDGE-VISIBLE`.

**Approach (supported pattern):** Registered two root lifecycle tasks:

- `publishConsumedModulesToMavenLocal` — publishes all 7 consumed modules (`gbkt-ir`, `gbkt-lang`, `gbkt-engine`, `gbkt-world`, `gbkt-core`, `gbkt-backend-api`, `gbkt-backend-gbdk`) to mavenLocal.
- `pluginTest` — depends on `publishConsumedModulesToMavenLocal` + `gradle.includedBuild("gbkt-gradle-plugin").task(":test")`.

Running `./gradlew pluginTest` always publishes current SNAPSHOT artifacts before running the TestKit sandbox tests, durably closing the 13 stale-mavenLocal compile failures. CI and local dev workflows should invoke `pluginTest` rather than `:gbkt-gradle-plugin:test` directly.

**Verification:** `./gradlew pluginTest --dry-run` shows all 7 `publishToMavenLocal` tasks scheduled before `:gbkt-gradle-plugin:test`.

## Item 3 — generateC stale-file / whenever{} concern (FIXED — minimal correct fixture repair)

**File:** `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt`
**Commit:** `5378fdea`

### Investigation

Step 1: After republishing current artifacts to mavenLocal (Task 2's `publishConsumedModulesToMavenLocal`), ran `./gradlew :gbkt-gradle-plugin:test --tests "*IntegrationTest*" --rerun-tasks`.

Result: only 1 test failed (`generateC deletes stale files dropped from the emission set`). The 13 stale-mavenLocal failures cited in the plan brief were zero — confirming those were a classification-(a) stale-cache artifact that Task 2's wiring resolves.

Step 2: Inspected the failure stacktrace from the test report XML:

```
Caused by: java.lang.NullPointerException
    at test.TestGameKt.testGame$lambda$0$2$1$0(TestGame.kt:13)
    at io.github.gbkt.core.dsl.ScriptBuilder.whenever$lambda$0(ScriptBuilder.kt:212)
```

The crash is in `createTwoSceneGameFixture()`. The fixture used a forward-reference pattern:

```kotlin
var mainSceneRef: SceneRef? = null
val titleScene = scene("title") {
    frame {
        whenever(buttons.start.pressed) { navigate(mainSceneRef!!) }  // NPE here
    }
}
val mainScene = scene("main") { ... }
mainSceneRef = mainScene  // too late
```

`ScriptBuilder.whenever{}` calls `ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }` which evaluates the lambda body **synchronously** at DSL construction time. When `titleScene` is built, `mainSceneRef` is still `null` — `navigate(mainSceneRef!!)` NPEs immediately.

### Classification: Test fixture authoring bug

This is NOT a `syncOutputDir` / `GenerateCTask` product bug. The test itself (`generateC deletes stale files`) is testing the correct thing (stale bank1.c deletion). The DSL evaluation NPE is entirely in the fixture helper method — it just needs the scenes defined in the right order.

### Fix

Reordered scene definitions in `createTwoSceneGameFixture()`: `mainScene` is now declared before `titleScene`, allowing `navigate(mainScene)` in the title frame to directly capture the already-initialized `SceneRef`. Removed the now-unnecessary `mainSceneRef` nullable sentinel variable and the back-navigation from main to title (the test only needs two scenes to prevent BankingAnalysisPass from taking the HOME fast-path; the cross-navigation cycle was irrelevant to the test goal).

**Verification:** `IntegrationTest` 19/19 GREEN. Full plugin suite 138/138 GREEN.

## Test Results Summary

| Suite | Before | After |
|-------|--------|-------|
| ConvertZoneTilesetsTaskTest | 10/11 (1 failing) | 11/11 |
| IntegrationTest | 18/19 (1 failing) | 19/19 |
| Full plugin suite | 136/138 | 138/138 |

## No Code Changes Required for syncOutputDir

The `GenerateCTask.syncOutputDir` method was reviewed and is NOT involved in the test failure. No changes to `GenerateCTask.kt` were made or warranted. The test failure was entirely in the test fixture, not in the production code path it exercised.

## Self-Check: PASSED

- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt` — modified, hoisted guard present
- `build.gradle.kts` — modified, publishConsumedModulesToMavenLocal + pluginTest tasks present
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt` — modified, forward-reference removed
- Commits `0c9a5679`, `c512064b`, `5378fdea` — all present in git log
