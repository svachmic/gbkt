# Phase 12.2 — Deferred Items

Out-of-scope discoveries logged per SCOPE BOUNDARY rule (executor only auto-fixes
issues caused by the current task's changes).

## Pre-existing spotless violations (discovered during Plan 12.2-08)

`./gradlew :gbkt-backend-gbdk:spotlessKotlinCheck` reports format violations in files
NOT modified by Plan 12.2-08. They predate this plan (likely from earlier wave merges)
and should be cleaned up via `./gradlew spotlessApply` in a maintenance plan, not here.

Files:
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  (lines around 1129, 1294 — comment-line wrap)
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/ZoneTilesetsBankFieldTest.kt`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteBoundPosEmissionTest.kt`

Fix: A future maintenance task can run `./gradlew :gbkt-backend-gbdk:spotlessApply`.
This is unrelated to Phase 12.2's REQ-5 work.

## Pre-existing spotless violations in `:gbkt-gradle-plugin` (discovered during Plan 12.2-04)

Mirrors Plan 12.2-03's same finding (its SUMMARY's "Deferred Issues" section). `./gradlew
:gbkt-gradle-plugin:spotlessKotlinCheck` reports format violations across 9 files NOT
specifically targeted by Plan 12.2-04. Running `:gbkt-gradle-plugin:build` therefore
fails on a `spotlessCheck` gate even though Kotlin compilation + the `:test` suite both
pass cleanly.

Files (per the spotlessKotlinCheck output during Plan 12.2-04):
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt`
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt`
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt` (pre-existing + my edits, both formatted by spotlessApply)
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/GbktPluginTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTaskMetaspriteDefaultTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTaskMirrorDedupOptInTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTaskPragmaBankTest.kt`
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTaskTest.kt` (pre-existing + my edits, both formatted by spotlessApply)
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/OutputDirSyncTest.kt`

Workaround applied during Plan 12.2-04: `spotlessApply` was run to normalize the two
files I touched; reformat changes to the other 9 files were reverted via `git checkout
HEAD --`. Plan 12.2-04 verification then ran `:gbkt-gradle-plugin:compileKotlin` +
`:gbkt-gradle-plugin:test` (both GREEN), matching Plan 12.2-03's same workaround.

Fix: A maintenance plan can run `./gradlew :gbkt-gradle-plugin:spotlessApply` in a
single review-in-isolation commit. Out of scope per CLAUDE.md SCOPE BOUNDARY rule.

## RESOLVED — `GBDKBackend.generateV2` reflective-call ABI mismatch (inline fix as 12.2-08.1)

Fixed inline by orchestrator after user routing decision (Wave 3 → Wave 4 boundary).
`@JvmOverloads` annotation added to `GBDKBackend.generateV2`. Verification:
`./gradlew :gbkt-examples:platformer-template:generateC` → BUILD SUCCESSFUL. Wave 7+ unblocked.

Commit: see `fix(12.2-08.1): @JvmOverloads on GBDKBackend.generateV2 ...` on `feat/d_and_d_gaps`.

Original report (preserved for verifier traceability):

## Pre-existing `GBDKBackend.generateV2` reflective-call ABI mismatch (discovered during Plan 12.2-04)

Plan 12.2-08 commit `544c12f5` added a 4th `assetRoot: java.io.File? = null` parameter
to `GBDKBackend.generateV2(...)`. The commit message claims "Default null preserves
backward compatibility — Gradle plugin's GenerateCTask call site inherits the default".
This claim is incorrect for the JVM reflection path: Kotlin default arguments do NOT
synthesize an `@JvmOverloads`-style 3-arg companion on the JVM unless explicitly
annotated. The Gradle plugin's caller
(`GenerateCTask.kt:executeV2Path` → `backend.javaClass.getMethod("generateV2", GameIR,
AssetManifest, File::class.java)`) therefore throws `NoSuchMethodException` at runtime
when `:gbkt-examples:platformer-template:generateC` runs.

Symptom (observed during Plan 12.2-04 Step 7 smoke test):
```
Caused by: java.lang.NoSuchMethodException:
  io.github.gbkt.backend.gbdk.GBDKBackend.generateV2(io.github.gbkt.core.ir.GameIR,
  io.github.gbkt.core.AssetManifest, java.io.File)
```

Scope: this defect was introduced by Plan 12.2-08 and lives on the worktree base
commit (`0904a007`). It is unrelated to Plan 12.2-04's edits in
`ConvertZoneTilesetsTask.kt`.

Plan 04 impact: Step 7 (`./gradlew :gbkt-examples:platformer-template:generateC ...`)
in PLAN.md cannot exit 0 until this is fixed. Plan 04's CORE verification
(`./gradlew :gbkt-gradle-plugin:build :gbkt-gradle-plugin:test`) is unaffected —
the gradle-plugin test suite covers ConvertZoneTilesetsTask in isolation. Plan 09
(the full 5-ROM regression sweep) cannot pass until this is repaired.

Recommended fix (a future maintenance plan or a fast-follow plan 12.2-08.1):
either annotate `GBDKBackend.generateV2` with `@JvmOverloads`, or update
`GenerateCTask.executeV2Path` to look up the 4-arg `generateV2(GameIR, AssetManifest,
File, File)` signature and pass `null` for the new `assetRoot` parameter.

