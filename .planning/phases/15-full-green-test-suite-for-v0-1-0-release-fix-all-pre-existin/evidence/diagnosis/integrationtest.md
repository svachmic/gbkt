# Diagnosis fragment — IntegrationTest (×12) — F1

**Plan:** 15-02 · **Requirement:** REQ-3 · **Evidence tier:** D-03b (static, internal build-infra)

## Symptom (captured stack trace)

`./gradlew pluginTest` → `BUILD FAILED in 29s`. All 12 `IntegrationTest` cases fail with a
`GradleRunner` `UnexpectedBuildFailure`; the nested sandbox sub-build dies with:

```
java.lang.NoSuchMethodError: 'io.github.gbkt.core.ir.SceneIR
  io.github.gbkt.core.ir.SceneIR.copy$default(io.github.gbkt.core.ir.SceneIR,
  java.lang.String, java.util.List, java.util.List, java.util.List, java.ut…)'
```

(from `gbkt-gradle-plugin/build/test-results/test/TEST-io.github.gbkt.gradle.IntegrationTest.xml`,
fresh 2026-06-09 13:22). `GBDKPipeline.buildCFiles` calls `scene.copy(allocatedZoneBank=…)`;
`SceneIR` is a 14-field data class that gained `zoneRefs` (`eda282ec`) and `allocatedZoneBank`
(13.8), so the Kotlin-synthesized `copy$default(...)` bridge arity changes whenever a field is
added. A `backend-gbdk` built against the NEW `SceneIR` calls a `copy$default` arity that the
linked (stale) `gbkt-ir` does not provide → the linkage error at runtime.

## Root cause (A1 resolved — a NOT-republished transitive module, not the cache alone)

**Locus = a stale TRANSITIVE module (`gbkt-analysis`) that the republish set omits, linked
against the freshly-republished `gbkt-ir`.** (Diagnose-first revised this verdict after the
cache-only fix failed to clear the error — see "Investigation" below.)

Distinguishing evidence — `~/.m2` jar timestamps after `publishConsumedModulesToMavenLocal`:

| Module | `~/.m2` jar timestamp | In republish set? |
|--------|----------------------|-------------------|
| gbkt-ir, gbkt-lang, gbkt-engine, gbkt-world, gbkt-core, gbkt-backend-api, gbkt-backend-gbdk | **2026-06-09 13:28** (fresh) | ✅ yes (the 7 listed) |
| **gbkt-analysis** | **2026-06-05 10:26** (STALE) | ❌ **NO** |

The captured stack trace shows the caller is `io.github.gbkt.analysis.passes.ScriptOpTra…` — i.e.
**`gbkt-analysis` invokes `SceneIR.copy$default(...)`**. `gbkt-backend-gbdk` declares
`api(project(":gbkt-analysis"))` (gbkt-backend-gbdk/build.gradle.kts:27), so the sandbox's
`runtimeOnly("io.github.gbkt:gbkt-backend-gbdk:0.1.0-SNAPSHOT")` transitively resolves
`gbkt-analysis:0.1.0-SNAPSHOT` from mavenLocal. That jar is **stale (Jun 5)** — compiled against
the older **13-field** `SceneIR`, so it calls the 13-field `copy$default` arity. The freshly
republished `gbkt-ir` (Jun 9) carries the current **14-field** `SceneIR` whose `copy$default`
arity differs → the 13-field method is absent → `NoSuchMethodError`. The `~/.m2` artifacts in the
republish set are mutually consistent; the desync is entirely between **republished gbkt-ir** and
**not-republished gbkt-analysis**.

### Investigation (why the cache-only fix was insufficient)

The first hypothesis (changing-module Gradle cache desync, research F1/Pitfall 2) predicted a
`cacheChangingModulesFor(0,"seconds")` defeat in `createBasicBuildFile()` would clear it. It did
NOT — a clean `./gradlew --stop && ./gradlew pluginTest` still produced 12 identical
`copy$default` failures (fresh XML 13:29). The timestamp table then revealed the true cause: a
consumed transitive module (`gbkt-analysis`) is absent from `mavenLocalModulesForPluginTest`
(build.gradle.kts:45-48), so it is never republished and stays pinned at its last-published
(Jun 5, 13-field-SceneIR) shape.

## Fix Path

**`real-bug-fix`** — a genuine test-infra / build-hermeticity defect (NOT a stale assertion; no
assertion is weakened or deleted). Two complementary durable edits:

1. **Primary (root cause):** add `:gbkt-analysis` to `mavenLocalModulesForPluginTest` in
   `build.gradle.kts` so the republish covers every module the sandbox transitively consumes.
   This is the edit that actually clears the skew.
2. **Hardening (kept):** `configurations.all { resolutionStrategy.cacheChangingModulesFor(0,
   "seconds") }` in the single `createBasicBuildFile()` template — ensures the freshly-republished
   SNAPSHOTs are always re-resolved rather than served from the nested runner's changing-module
   cache (defends against the F1/Pitfall-2 24h-TTL desync independently of (1)).

Both preferred over `--refresh-dependencies` on the 19 `GradleRunner` calls (19 edit sites,
blunter) — the centralized two-edit fix is sufficient and minimal.

## Evidence ref

- Stack trace: `gbkt-gradle-plugin/build/test-results/test/TEST-io.github.gbkt.gradle.IntegrationTest.xml` — `NoSuchMethodError: SceneIR.copy$default(... 13-field ...)`, caller `io.github.gbkt.analysis.passes.ScriptOpTra…`
- Timestamp table above (`~/.m2/.../gbkt-analysis-0.1.0-SNAPSHOT.jar` = Jun 5 vs the 7 republished modules = Jun 9 13:28)
- Dependency edge: `gbkt-backend-gbdk/build.gradle.kts:27` `api(project(":gbkt-analysis"))`
- Fix surfaces: `build.gradle.kts:45-48` `mavenLocalModulesForPluginTest`; `gbkt-gradle-plugin/.../IntegrationTest.kt:533` `createBasicBuildFile()`
