---
id: SEED-VERSION-COORD-DERIVATION
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close — gbktVersion bump 0.1.0 → 0.1.1"
trigger_when: "next version bump (v0.1.2 / v0.2.0), or whenever the TestKit IntegrationTest harness is touched"
scope: small
triage_disposition: NEW
triage_date: 2026-06-15
---

# SEED-VERSION-COORD-DERIVATION: derive hardcoded module-version coordinates from `gbktVersion`

## Source

Two places hardcode the gbkt module version as a literal string instead of deriving it from the single source of truth (`gbktVersion` in `gradle.properties`):

1. `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt` (~lines 554–556) — the TestKit sandbox `build.gradle.kts` fixture pins:
   ```
   implementation("io.github.gbkt:gbkt-core:<VER>-SNAPSHOT")
   implementation("io.github.gbkt:gbkt-backend-api:<VER>-SNAPSHOT")
   runtimeOnly("io.github.gbkt:gbkt-backend-gbdk:<VER>-SNAPSHOT")
   ```
2. `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/project/ProjectFileGenerator.kt` (~line 99) — the new-project scaffold template emits `implementation("io.github.gbkt:gbkt-core:<VER>")`.

Both currently say `0.1.1` / `0.1.1-SNAPSHOT` (bumped manually during the v0.1.1 close).

## Why This Matters

This re-breaks on **every** version bump. During the v0.1.1 close, bumping `gbktVersion` 0.1.0 → 0.1.1 made the modules publish as `0.1.1-SNAPSHOT`, but the TestKit fixtures still referenced `0.1.0-SNAPSHOT` → the IntegrationTest sandbox could not resolve them → **14 CI test failures** on the `build` job (caught only after a push + full CI run). The IDE scaffold silently generates new user projects pinned to a stale version (no failure, just wrong output for whoever scaffolds a project).

The version is already centralized in `gradle.properties` (`gbktVersion`) and enforced consistent by `:checkVersionConsistency`. These two consumers should track it automatically.

## Sketch of the Fix

- **TestKit fixture:** have the `gbkt-gradle-plugin` test task inject the version, e.g. `systemProperty("gbktVersion", project.findProperty("gbktVersion") ?: rootGbktVersion)`, and build the fixture coordinates from `System.getProperty("gbktVersion")` (append `-SNAPSHOT` to match the non-release publish). Then a bump needs no fixture edit.
- **IDE scaffold:** generate the coordinate from a build-time-substituted constant (e.g. a generated `BuildConfig`-style version, or read the plugin's own version) rather than a literal in the template string.
- Optionally add a tiny guard test asserting no `io.github.gbkt:...:<literal-semver>` strings remain in `src/` so the landmine cannot be re-planted.

## When to Surface

Pull this in at the **next version bump** (so the bump is a single `gradle.properties` edit) or whenever the IntegrationTest harness / IDE scaffold is being worked on. Small, self-contained, no DSL surface change.
