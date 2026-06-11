# Quick Task 260611-k1w: Unify version catalog (libs.versions.toml) usage across all Gradle build scripts - Context

**Gathered:** 2026-06-11
**Status:** Ready for planning

<domain>
## Task Boundary

Unify version catalog (`gradle/libs.versions.toml`) usage across all Gradle build scripts. Today the catalog covers only 11 libraries and 8 of ~24 build scripts reference it; the rest carry inline versions. Known stragglers from the pre-task audit:

- `org.junit:junit-bom:5.11.4` inlined 6× across 4 modules (`gbkt-test`, `gbkt-emulator`, `gbkt-mcp-server`, `gbkt-gradle-plugin`) — not in the catalog at all
- `org.json:json:20251224` hardcoded at `gbkt-gradle-plugin/build.gradle.kts:52` despite `libs.json` existing AND the composite build already importing the catalog
- Plugin versions split across `pluginManagement` in root `settings.gradle.kts` (kotlin 2.3.20, spotless 8.6.0, detekt 1.23.8, sonarqube 7.3.1.8318, kover 0.9.4, plugin-publish 1.3.1) and standalone pins (`com.gradleup.shadow 9.0.0-beta12` + `kotlin("plugin.serialization") 2.3.0` in gbkt-mcp-server, `org.jetbrains.intellij.platform 2.10.5` in gbkt-intellij-plugin, spotless 8.6.0 + plugin-publish 1.3.1 re-pinned in the gbkt-gradle-plugin composite build)
- **Live drift bug:** `kotlin("plugin.serialization") version "2.3.0"` in `gbkt-mcp-server/build.gradle.kts:10` vs project Kotlin 2.3.20 — the serialization compiler plugin must match the Kotlin compiler version

Branch: `chore/unify-version-catalog` (created off origin/master).

</domain>

<decisions>
## Implementation Decisions

### Plugin scope — FULL [plugins] migration (user-selected)
- Add a `[plugins]` section to `gradle/libs.versions.toml` covering ALL plugins currently versioned in `pluginManagement` or inline: kotlin-jvm, kotlin-serialization, spotless, detekt, sonarqube, kover, plugin-publish, shadow, intellij-platform
- Convert every `plugins {}` block that relied on those versions to `alias(libs.plugins.x)` — root build.gradle.kts (`apply false` aliases), all subprojects using `kotlin("jvm")`, gbkt-mcp-server, gbkt-intellij-plugin, and the gbkt-gradle-plugin composite build (its settings.gradle.kts already imports the shared catalog)
- Remove the now-redundant version pins from `pluginManagement.plugins` in root settings.gradle.kts (keep `pluginManagement` itself: repositories + `includeBuild("gbkt-gradle-plugin")` MUST stay)
- Migration must be COMPLETE in one pass: once pluginManagement version pins are removed, any leftover versionless `kotlin("jvm")`/`id("...")` (other than the exceptions below) fails resolution

### Serialization mismatch — single bump point via catalog (user intent: one place to bump Kotlin)
- Add `kotlin = "2.3.20"` to `[versions]`; both `kotlin-jvm` and `kotlin-serialization` plugin entries use `version.ref = "kotlin"`
- This INTENTIONALLY bumps the serialization compiler plugin 2.3.0 → 2.3.20 (the only intended resolution change in the whole task)
- User selected "single-source via settings" for this question before selecting full migration in the prior question; the catalog `kotlin` version ref is the same intent (one bump point) realized through the mechanism the full migration dictates

### Verification bar — resolution diff + build (user-selected)
- Prove the refactor changes nothing it shouldn't: capture machine-diffable resolved-dependency snapshots per module BEFORE and AFTER the change; module dependency configurations must be IDENTICAL
- The serialization plugin bump lives on the build classpath, not module configurations — capture `./gradlew buildEnvironment` (at minimum for root + gbkt-mcp-server + the composite) where the ONLY expected diff is `org.jetbrains.kotlin.plugin.serialization` 2.3.0 → 2.3.20
- Then `./gradlew build` must pass (assemble + check across all subprojects)
- NEVER run two parallel `./gradlew` invocations against this repo (Kotlin daemon collision corrupts builds — project rule); chain tasks in single invocations

### Claude's Discretion
- Exact catalog alias naming (follow existing kebab/dotted conventions in the toml)
- Whether to delete the `pluginManagement.plugins {}` block entirely or leave an explanatory comment pointing at the catalog
- JUnit library entries: add `junit-bom` (+ `junit-jupiter`, `junit-jupiter-api`, `junit-platform-launcher` as versionless module refs governed by the BOM platform) — keep semantics identical (`platform(libs.junit.bom)`, compileOnly usage in gbkt-test preserved)
- Snapshot tooling for the resolution diff (loop over modules vs init script) — must be deterministic and diff-cleanly

</decisions>

<specifics>
## Specific Ideas — known edge cases (ultrathink audit)

1. **Do NOT catalog versionless/special plugins:** `kotlin-dsl` (buildSrc + composite; version is bound to Gradle), the `gbkt.publishing` convention plugin (buildSrc), `id("io.github.gbkt")` in examples (resolved via includeBuild, versionless), and `kotlin("test")` dependencies (version from Kotlin plugin)
2. **Composite build is a separate Gradle build:** it does NOT inherit root pluginManagement; its `plugins {}` aliases resolve through its OWN settings.gradle.kts catalog import (`from(files("../gradle/libs.versions.toml"))`) — verify spotless/plugin-publish aliases resolve there; its plugin repositories default must include gradlePluginPortal
3. **Dynamic plugin application by string id stays:** root `subprojects {}` uses `apply(plugin = "com.diffplug.spotless")` / detekt / kover via `pluginManager.withPlugin(...)` — these react to/apply by id and need the plugin on the build classpath via the root `alias(... ) apply false` declarations; do not convert these strings
4. **`libs` accessor availability:** root build.gradle.kts already uses `libs.versions.ktfmt.get()` inside `subprojects {}` — must keep compiling after the catalog gains [plugins]
5. **Examples modules:** check what `gbkt-examples/*/build.gradle.kts` apply; any versionless `kotlin("jvm")` there must also move to alias once pluginManagement pins are removed
6. **buildSrc cannot see the catalog** without explicit wiring — it currently needs no versions (kotlin-dsl only); leave untouched
7. **Dependabot:** check `.github/dependabot.yml` — gradle ecosystem natively scans libs.versions.toml; confirm no per-directory entry breaks (composite build dir may be listed separately); recent Dependabot PRs bumped pluginManagement versions, so moving them to the catalog must not orphan its config
8. **checkVersionConsistency task** (root build.gradle.kts) compares `gbktVersion` across gradle.properties files — unrelated to the catalog, must keep passing
9. **IntegrationTest TestKit sandbox** (pluginTest) generates its own build files resolving gbkt from mavenLocal — it has no catalog access and must not be touched; pluginTest is OUT of the verification bar per user decision (resolution diff + build only)
10. **Identical-version invariant:** junit-bom 5.11.4 and json 20251224 catalog entries must match the inlined versions exactly so the resolution diff is empty
11. **kotlin("jvm") apply false in root + applied in subprojects:** after migration both become the same alias — same catalog version, no conflict; do NOT leave a mixed state where root uses alias and a subproject uses versionless `kotlin("jvm")`

</specifics>

<canonical_refs>
## Canonical References

- Pre-task audit (this conversation, 2026-06-11): catalog coverage inventory across all *.gradle.kts files
- CLAUDE.md build commands; gradle.properties jvmargs (4g heap needed for from-clean multi-module builds)
- Project rule: no parallel gradle invocations (Kotlin daemon collision)

</canonical_refs>
