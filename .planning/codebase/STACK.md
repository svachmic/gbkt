# Technology Stack

**Analysis Date:** 2026-05-27

## Languages

**Primary:**
- Kotlin 2.3.0 — All gbkt library, backend, genre, tooling, and example modules. Source under `src/main/kotlin/`.
- Kotlin DSL — Gradle build scripts (`*.gradle.kts`), convention plugin (`buildSrc/src/main/kotlin/gbkt.publishing.gradle.kts`).

**Secondary:**
- C (generated) — GBDK-compatible C emitted by `gbkt-backend-gbdk` (e.g. `main.c`, `bank1.c`, `game.h`, `zone_bankN.c`). Not hand-written. Lowered by SDCC via GBDK's `lcc` driver.
- Java 21 (runtime) — Bytecode target for all JVM modules; gradle-plugin uses `gradleTestKit()`, IntelliJ plugin includes `id("java")`.
- GNU gettext `.po` — Localization tables under `res/strings/*.po` per example, parsed at codegen time.

## Runtime

**Environment:**
- JVM 21 — Required for every Kotlin module (`kotlin { jvmToolchain(21) }` in every `build.gradle.kts`).
- Game Boy / Game Boy Color hardware — Final runtime target for emitted ROMs.

**Package Manager:**
- Gradle 9.0 — Pinned via `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl=...gradle-9.0-bin.zip`).
- Lockfile: Wrapper validation is on (`validateDistributionUrl=true`); no dependency lockfile in use. Dependencies pinned by exact version strings in `gradle/libs.versions.toml` and per-module `build.gradle.kts`.

## Frameworks

**Core:**
- Kotlin stdlib 2.3.0 — `kotlin("jvm")` applied through `settings.gradle.kts` plugin management.
- Kotlin Serialization 2.3.0 — Used by `gbkt-mcp-server` only (`kotlin("plugin.serialization") version "2.3.0"`); not project-wide.
- kotlinx-coroutines 1.10.1 — Async wrapping of blocking emulator calls in `gbkt-mcp-server`. Also `1.9.0` (via libs version `coroutines`) for test coroutines in `gbkt-core`.
- kotlinx-io 0.7.0 — Stdio transport in `gbkt-mcp-server`.

**Testing:**
- JUnit 5 (`org.junit:junit-bom:5.11.4`) — Primary test runner across `gbkt-emulator`, `gbkt-mcp-server`, `gbkt-test`, `gbkt-gradle-plugin`. `useJUnitPlatform()` set in each module's `tasks.test`.
- `kotlin.test` — Used in non-emulator modules (`gbkt-ir`, `gbkt-core`, `gbkt-backend-gbdk`, `gbkt-genre-*`, `gbkt-cli`, `gbkt-all`).
- Kotest Property 5.9.1 — Property-based tests in `gbkt-core` (`testImplementation(libs.kotest.property)`).
- kotlinx-coroutines-test 1.9.0 — In `gbkt-core` (`libs.coroutines.test`).
- JUnit 4 13.2 — Only in `gbkt-intellij-plugin` (IntelliJ test framework still requires JUnit 4).
- Gradle TestKit — Integration tests for the plugin (`gradleTestKit()` in `gbkt-gradle-plugin`).

**Build/Dev:**
- Spotless 8.1.0 (`com.diffplug.spotless`) — Kotlin formatting via ktfmt 0.62 (`kotlinlangStyle()`), license header injection (MPL 2.0 default; Apache 2.0 for `gbkt-intellij-plugin`), trailing-whitespace + EOF newline enforcement.
- Detekt 1.23.8 (`io.gitlab.arturbosch.detekt`) — Static analysis with project rules in `detekt.yml`; per-module baseline at `detekt-baseline.xml`; `buildUponDefaultConfig = true`.
- Kover 0.9.4 (`org.jetbrains.kotlinx.kover`) — Coverage; XML report consumed by SonarCloud.
- SonarQube 7.2.2.6593 (`org.sonarqube`) — Configured in root `build.gradle.kts` for `sonarcloud.io`, project `svachmic_gbkt`, org `svachmic`.
- Shadow 9.0.0-beta12 (`com.gradleup.shadow`) — Fat JAR for `gbkt-mcp-server` (`shadowJar` task; classifier `all`; `mergeServiceFiles()`).
- Gradle Plugin Publish 1.3.1 (`com.gradle.plugin-publish`) — Publishes `gbkt-gradle-plugin` to the Gradle Plugin Portal.
- IntelliJ Platform Gradle Plugin 2.10.5 (`org.jetbrains.intellij.platform`) — IntelliJ IDEA Community 2024.2 plugin build (`sinceBuild = "242"`, `untilBuild = null`).

## Key Dependencies

**Critical:**
- Coffee-GB Core 1.6.0 (`eu.rekawek.coffeegb:core:1.6.0`) — Headless Game Boy CPU/PPU/APU emulator embedded in `gbkt-emulator`. Wrapped by `CoffeeGbEmulator.kt` with a watchdog (`maxTicksPerFrame ≈ 1 000 000` t-cycles) that throws `EmulatorFrameHangException` on stalled ROMs.
- MCP Kotlin SDK 0.9.0 (`io.modelcontextprotocol:kotlin-sdk:0.9.0`) — Server + stdio transport in `gbkt-mcp-server`; underlies the 17 tools used by Claude Code for AI-agent ROM testing.
- org.json 20251224 — JSON parsing for `.gbkt.map` source maps, Tiled `.tmj` maps, `game_metadata.json`. Declared centrally in `gradle/libs.versions.toml` as `libs.json`.

**Infrastructure:**
- SLF4J NOP 2.0.17 — Runtime-only in `gbkt-mcp-server` to suppress log noise on stdio.
- `kotlinx-serialization-json` 1.8.1 — JSON tool results in `gbkt-mcp-server`.
- `kotlinx-atomicfu` — Available via pluginManagement repositories; used transitively.

## Configuration

**Environment:**
- `GBDK_HOME` — Path to GBDK-2020 install; consumed by `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/GbdkToolchain.kt`. Falls back to `gbkt { gbdkHome.set(...) }` then common install paths (`/opt/gbdk-2020`, `~/gbdk-2020`, etc.).
- `OSSRH_USERNAME` / `OSSRH_PASSWORD` — Maven Central staging credentials (release).
- `SIGNING_KEY` / `SIGNING_PASSWORD` — Armored GPG key + passphrase for artifact signing (release).
- `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET` — Gradle Plugin Portal credentials.
- `CERTIFICATE_CHAIN` / `PRIVATE_KEY` / `PRIVATE_KEY_PASSWORD` / `PUBLISH_TOKEN` — JetBrains Marketplace signing/publishing for `gbkt-intellij-plugin`.
- `GITHUB_TOKEN` / `GITHUB_ACTOR` — GitHub Packages fallback publishing.
- `SONAR_TOKEN` — SonarCloud scan in CI.
- `gbktVersion` — Gradle project property (defaults from `gradle.properties` = `0.1.0`); `-Prelease` switches version from `-SNAPSHOT` to release.

**Build:**
- `build.gradle.kts` (root) — Configures Sonarqube, version-consistency task, and subproject Spotless/Detekt convention (`ktfmt("0.62").kotlinlangStyle()`).
- `settings.gradle.kts` — Plugin versions, 20 module includes, 8 example includes (`gbkt-examples:pong/breakout/racer/simple-physics/metasprites/metasprites-stress/banks/platformer-template`), composite-included build `includeBuild("gbkt-gradle-plugin")`.
- `gradle/libs.versions.toml` — Version catalog (`libs.json`, `libs.kotest.property`, `libs.coroutines.test`).
- `gradle.properties` — Single property: `gbktVersion=0.1.0`.
- `buildSrc/src/main/kotlin/gbkt.publishing.gradle.kts` — Convention plugin applied as `id("gbkt.publishing")` by every published module; wires Maven Central (OSSRH), GitHub Packages, POM metadata, signing, sources + javadoc jars.
- `detekt.yml` — Module-pattern-scoped rule exclusions (e.g. `**/codegen/**` skips `LongMethod`/`TooManyFunctions`).
- `.autoresearch.yml` — Autoresearch tool config (project root).

## Platform Requirements

**Development:**
- JDK 21 (Temurin recommended; matches CI).
- Gradle 9.0 (via wrapper — do not run system Gradle).
- GBDK-2020 4.5.0+ installed locally (or via the Docker image at `docker/Dockerfile.gbdk`) for any task that touches ROM compilation (`compileRom`, `buildRom`, `runEmulator`, `validateRom`, `emulatorTest`).
- mGBA (optional) — Auto-detected on PATH for `runEmulator` external-emulator fallback and `validateRom` Lua scripting (see `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ValidateRomTask.kt`).
- macOS / Linux supported by the toolchain wiring (`GbdkToolchain.isWindows()` exists but the project is dogfooded on macOS).

**Production:**
- Game Boy (DMG) and Game Boy Color (GBC) hardware/emulator. Target profiles in `gbkt-backend-gbdk/.../profiles/` (`GameBoyProfile`, `GameBoyColorProfile`, `GameBoyConstants`).
- Maven Central (`io.github.gbkt:*`) — Library distribution.
- Gradle Plugin Portal (`id("io.github.gbkt")`) — Build-system integration.
- JetBrains Marketplace — IntelliJ plugin distribution.

## Module Map (20 Gradle modules + 8 example games)

| Layer | Module | Build File | Role |
|-------|--------|-----------|------|
| IR (leaf) | `gbkt-ir` | `gbkt-ir/build.gradle.kts` | Zero gbkt deps; `validateModuleBoundaries` task enforces leaf status |
| DSL | `gbkt-lang` | `gbkt-lang/build.gradle.kts` | DSL builders (`GameBuilder`, `ScriptBuilder`, variable delegates) |
| DSL | `gbkt-engine` | `gbkt-engine/build.gradle.kts` | Combat, input, entity, scene, graphics runtime types |
| DSL | `gbkt-world` | `gbkt-world/build.gradle.kts` | World / exploration / floor / encounter types |
| Aggregator | `gbkt-core` | `gbkt-core/build.gradle.kts` | `api(project(":gbkt-ir"))` + lang + engine + world; adds asset pipeline, parsers, source maps |
| Backend | `gbkt-backend-api` | `gbkt-backend-api/build.gradle.kts` | `CodegenBackend` interface, `GenreSystemVisitor` |
| Backend | `gbkt-backend-gbdk` | `gbkt-backend-gbdk/build.gradle.kts` | GBDK C codegen via typed C AST + 13 visitors |
| Analysis | `gbkt-analysis` | `gbkt-analysis/build.gradle.kts` | 11 analysis passes (validation → banking → VRAM → OAM → RAM → budget) |
| Genre | `gbkt-genre-rpg` | `gbkt-genre-rpg/build.gradle.kts` | Characters, abilities, battles, equipment |
| Genre | `gbkt-genre-platformer` | `gbkt-genre-platformer/build.gradle.kts` | Physics, camera, level elements |
| Genre | `gbkt-genre-puzzle` | `gbkt-genre-puzzle/build.gradle.kts` | Match-3, block-push |
| Genre | `gbkt-genre-sport` | `gbkt-genre-sport/build.gradle.kts` | Racing, ball sports, tournaments |
| Tooling | `gbkt-emulator` | `gbkt-emulator/build.gradle.kts` | Embedded Coffee-GB + StepAgent + UatRunner + Swing dev UI |
| Tooling | `gbkt-test` | `gbkt-test/build.gradle.kts` | `GbktTestExtension` (JUnit5), assertions, recipes |
| Tooling | `gbkt-mcp-server` | `gbkt-mcp-server/build.gradle.kts` | Shadow JAR exposing 17 MCP tools over stdio |
| Tooling | `gbkt-gradle-plugin` | `gbkt-gradle-plugin/build.gradle.kts` | Composite-included; registers 16+ tasks (`generateC`, `compileRom`, `buildRom`, `runEmulator`, `debugEmulator`, `validateRom`, `emulatorTest`, `budgetReport`, `webExport`, `generateAssets`, `gbktSetupClaude`, `cleanGbkt`, etc.) |
| Tooling | `gbkt-cli` | `gbkt-cli/build.gradle.kts` | `application` plugin; `gbkt new/build/run/list-targets` |
| Tooling | `gbkt-intellij-plugin` | `gbkt-intellij-plugin/build.gradle.kts` | IntelliJ 2024.2 plugin (highlighting, completion, inspections, visual editors, C preview) |
| Coordination | `gbkt-all` | `gbkt-all/build.gradle.kts` | Pure dependency aggregator (api re-exports) |
| Coordination | `gbkt-bom` | `gbkt-bom/build.gradle.kts` | `java-platform` BOM publishing version constraints |
| Examples | `gbkt-examples:pong` | `gbkt-examples/pong/build.gradle.kts` | Apply gbkt Gradle plugin directly |
| Examples | `gbkt-examples:breakout` | `gbkt-examples/breakout/build.gradle.kts` | — |
| Examples | `gbkt-examples:racer` | `gbkt-examples/racer/build.gradle.kts` | — |
| Examples | `gbkt-examples:simple-physics` | `gbkt-examples/simple-physics/build.gradle.kts` | — |
| Examples | `gbkt-examples:metasprites` | `gbkt-examples/metasprites/build.gradle.kts` | — |
| Examples | `gbkt-examples:metasprites-stress` | `gbkt-examples/metasprites-stress/build.gradle.kts` | — |
| Examples | `gbkt-examples:banks` | `gbkt-examples/banks/build.gradle.kts` | — |
| Examples | `gbkt-examples:platformer-template` | `gbkt-examples/platformer-template/build.gradle.kts` | — |
| Reference | `LabyrinthOfTheDragon-port` | `LabyrinthOfTheDragon-port/build.gradle.kts` | Reference RPG port |

## Repositories

Plugin and project repositories (`build.gradle.kts`, `settings.gradle.kts`, per-module):
- Maven Central (`mavenCentral()`) — Primary.
- Maven Local (`mavenLocal()`) — Composite plugin development; CI pre-publishes 13 modules to `mavenLocal` before building consumers.
- Gradle Plugin Portal — Plugin discovery in `pluginManagement`.
- IntelliJ Platform repositories — Configured via `intellijPlatform { defaultRepositories() }` in `gbkt-intellij-plugin/build.gradle.kts`.

---

*Stack analysis: 2026-05-27*
