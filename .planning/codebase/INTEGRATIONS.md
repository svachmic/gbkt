# External Integrations

**Analysis Date:** 2026-05-27

## APIs & External Services

**Build / Toolchains:**
- GBDK-2020 (`lcc` driver wrapping SDCC) — Compiles the generated C into a `.gb` ROM. Discovery and invocation live in `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/GbdkToolchain.kt` (search order: extension → `GBDK_HOME` → common paths) and `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt`. Toolchain version pinned in `docker/Dockerfile.gbdk` at `GBDK_VERSION=4.5.0`.
  - SDK/Client: Process invocation of `bin/lcc` (and `png2asset` for sprite conversion).
  - Auth: None (local tool).
- GBDK `png2asset` — PNG → 2bpp tile data + `.c/.h` includes. Driven by `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt` (registered as `convertSprites`) and `ConvertZoneTilesetsTask.kt` (registered as `convertZoneTilesets`).
- mGBA (external emulator, optional) — Auto-detected on PATH. Used by `runEmulator` when `gbkt { emulator { externalEmulator.set(...) } }` is configured, and by `validateRom` via mGBA's Lua scripting interface (`gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ValidateRomTask.kt`). Default `runEmulator` path uses the embedded Coffee-GB emulator instead.
- Coffee-GB (`eu.rekawek.coffeegb:core:1.6.0`) — Embedded Game Boy emulator. Used in `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt`. Drives `runEmulator`, `debugEmulator`, `validateRom`, `emulatorTest`, the MCP server, and `UatRunner`.
  - SDK/Client: Direct JVM API; not a network service.
  - Auth: None.

**Model Context Protocol:**
- MCP Kotlin SDK 0.9.0 (`io.modelcontextprotocol:kotlin-sdk`) — Server framework used in `gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/GbktMcpServer.kt`. Stdio transport. Exposes 17 tools registered in `gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/ToolHandlers.kt`.

**Sonar / Code Quality:**
- SonarCloud (`https://sonarcloud.io`, org `svachmic`, project `svachmic_gbkt`) — Configured in root `build.gradle.kts` `sonarqube { ... }` block; consumes Kover XML at `**/build/reports/kover/report.xml`. Invoked from `.github/workflows/sonar.yml` via `./gradlew sonar`.
  - Auth: `SONAR_TOKEN` (GitHub secret).
- SonarQube MCP server (`https://api.sonarcloud.io/mcp`) — Available to Claude Code (`.claude/mcp_servers.json`).
  - Auth: Bearer token + `SONARQUBE_ORG=svachmic`.

## Data Storage

**Databases:**
- None. gbkt is a code generator + emulator; there is no runtime data store.

**File Storage:**
- Local filesystem only. Generated artifacts live under `build/gbkt/`:
  - `build/gbkt/generated/main.c`, `bank1.c`, `game.h`, `zone_bankN.c`, `main.c.gbkt.map`, `game_metadata.json`
  - `build/gbkt/output/<name>.gb`, `.map`, `.sym`, `.noi`
- ROM-runtime state: SRAM persistence is encoded into the ROM by the GBDK `save` system (DSL `rpgSave { }`); not externalized.
- Optimization JSON, budget report, asset manifest: emitted alongside generated C.

**Caching:**
- Gradle build cache (via `gradle/actions/setup-gradle@v4` in CI workflows; `cache-read-only` toggled per ref).
- `GbktCodegenService` (IntelliJ plugin) — In-memory source-map cache (`gbkt-intellij-plugin/.../codegen/GbktCodegenService.kt`).
- MCP single-session cached `Observation` returned by `emulator_observe` without stepping.

## Authentication & Identity

**Auth Provider:**
- None for runtime gbkt; gbkt produces standalone ROMs.
- Publishing pipelines (release-only) use:
  - OSSRH (Sonatype Maven Central staging) — `OSSRH_USERNAME` / `OSSRH_PASSWORD`.
  - GPG armored key — `SIGNING_KEY` / `SIGNING_PASSWORD` (in-memory PGP).
  - Gradle Plugin Portal — `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET`.
  - JetBrains Marketplace — `CERTIFICATE_CHAIN` / `PRIVATE_KEY` / `PRIVATE_KEY_PASSWORD` / `PUBLISH_TOKEN`.
  - GitHub Packages (fallback) — `GITHUB_TOKEN` / `GITHUB_ACTOR`.

## Monitoring & Observability

**Error Tracking:**
- None at the framework level. GBDK build errors surface through `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/ErrorEnhancer.kt` + `GbdkErrorParser.kt`, which map `lcc` stderr back to Kotlin DSL source lines via `.gbkt.map`.
- Coffee-GB watchdog (`EmulatorFrameHangException` in `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt`) prevents hung-ROM JVM lockups (default `maxTicksPerFrame = 1 000 000` t-cycles ≈ 14 frames).

**Logs:**
- Embedded emulator captures GBDK `EMU_printf` traps via `EmuPrintfInterceptor.kt` and serializes to `DebugLogEntry` records via `DebugLogWriter.kt`; viewed in `LogCatPanel.kt`.
- SLF4J NOP runtime in `gbkt-mcp-server` deliberately suppresses logging on stdio (would corrupt MCP protocol frames).
- Standard Gradle `--info` / `--debug` for build-time diagnostics.

## CI/CD & Deployment

**Hosting:**
- GitHub: `https://github.com/svachmic/gbkt` (per POM scm + plugin website).

**CI Pipeline (`.github/workflows/`):**
- `kotlin.yml` — On push / PR to `master` for any Kotlin module path. Three jobs:
  1. `build` — Pre-publishes 13 library modules to `mavenLocal`, then `:gbkt-core:build :gbkt-cli:build :gbkt-backend-api:build :gbkt-backend-gbdk:build :gbkt-intellij-plugin:build :gbkt-examples:pong:build :gbkt-examples:breakout:build :gbkt-examples:explorer:build`, runs core/backend/plugin tests, verifies example `generateC` for pong/breakout/explorer.
  2. `code-quality` — `./gradlew spotlessCheck`.
  3. `version-consistency` — `./gradlew checkVersionConsistency` (asserts root `gbktVersion` matches `gbkt-gradle-plugin/gradle.properties`).
- `sonar.yml` — On push / PR to `master`; pre-publishes 13 modules to `mavenLocal`, runs `:gbkt-core:test :gbkt-core:koverXmlReport`, then `./gradlew sonar`.
- `codeql.yml` — On push / PR / weekly (Monday cron). Language `java-kotlin`. `continue-on-error: true` because CodeQL upstream doesn't yet support Kotlin 2.3.0 (tracked: https://github.com/github/codeql/issues/20661).
- `release.yml` — Triggered on `v*` tags. Four jobs:
  1. `build` — Full `./gradlew build` with `-Prelease -PgbktVersion=...`, then targeted tests for core/backend/plugin, uploads artifacts.
  2. `publish-maven-central` — Publishes `gbkt-core`, `gbkt-backend-api`, `gbkt-backend-gbdk`, `gbkt-bom`, `gbkt-cli` via `publishAllPublicationsToOSSRHRepository` (requires OSSRH + signing secrets).
  3. `publish-gradle-plugin` — `publishPlugins` (Gradle Plugin Portal); first submission needs manual approval.
  4. `create-release` — Builds artifacts, runs `cyclonedxBom` (SBOM, soft-fail), creates GitHub Release via `softprops/action-gh-release` with auto-generated notes and attaches jars + CLI distributions.
  5. `publish-github-packages` — Fallback publish to GitHub Packages (`https://maven.pkg.github.com/svachmic/gbkt`).

**Dependency Pinning (CI security policy):**
All GitHub Actions in the workflows are pinned by commit SHA (e.g. `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6`, `actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5`, `gradle/actions/setup-gradle@48b5f213c81028ace310571dc5ec0fbbca0b2947 # v4`, `actions/upload-artifact`, `github/codeql-action`, `softprops/action-gh-release`).

**Concurrency:**
Each workflow uses `concurrency: { group: <name>-${{ github.ref }}, cancel-in-progress: true }` to coalesce in-flight runs per branch/PR.

**JVM in CI:**
JDK 21 Temurin via `actions/setup-java` in all jobs.

## Environment Configuration

**Required env vars (development):**
- `GBDK_HOME` (or extension/auto-detection) — Required for any ROM-building task.

**Required env vars (release CI, set as GitHub secrets):**
- `OSSRH_USERNAME`, `OSSRH_PASSWORD`
- `SIGNING_KEY`, `SIGNING_PASSWORD`
- `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`
- `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN` (JetBrains Marketplace)
- `SONAR_TOKEN`
- `GITHUB_TOKEN` (provided by GitHub Actions)

**Secrets location:**
- GitHub repository secrets (read by `release.yml`, `sonar.yml`).
- Local: typically `~/.gradle/gradle.properties` (developer machine — never committed).
- `gradle.properties` at repo root contains only `gbktVersion=0.1.0` — no secrets committed.

## Webhooks & Callbacks

**Incoming:**
- None.

**Outgoing:**
- None at runtime. CI talks outbound to:
  - SonarCloud REST API (`sonar.host.url=https://sonarcloud.io`).
  - Sonatype OSSRH (`https://s01.oss.sonatype.org/...`).
  - Gradle Plugin Portal (`publishPlugins`).
  - JetBrains Marketplace (`intellijPlatform.publishing.token`).
  - GitHub API (release creation, packages publishing).

## Asset Pipeline Integrations

- PNG → 2bpp Game Boy tile data — Internal pipeline in `gbkt-core/src/main/kotlin/io/github/gbkt/core/AssetPipeline.kt`. Validates signatures (`PngValidator.kt`), deduplicates tiles (`TileDeduplicator.kt`), produces `AssetManifest.kt`.
- PNG → C sprite includes — Delegated to GBDK `png2asset` via `ConvertSpritesTask.kt` and `ConvertZoneTilesetsTask.kt`.
- Tiled `.tmj`/`.json` maps — Parsed by `gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt`.
- LDtk `.ldtk` levels — Parsed by `gbkt-core/src/main/kotlin/io/github/gbkt/core/LdtkParser.kt`.
- GNU gettext `.po` files (localization) — Compiled into bank-allocated string tables; see `context/LOCALIZATION.md`. Bank-group convention `msgctxt` → ROM bank.
- Source maps — `.gbkt.map` JSON files emitted alongside generated C, parsed by `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/SourceMapLoader.kt` and `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/SourceMapResolver.kt` (uses `.noi` GBDK linker maps for C-line → ROM-addr mapping).

## Claude Code & AI Agent Integrations

**MCP servers configured at `.claude/mcp_servers.json`:**
- `gbkt-emulator` (stdio) — `java -jar gbkt-mcp-server/build/libs/gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar --headed`. Built by `./gradlew :gbkt-mcp-server:shadowJar`.
- `serena` (stdio) — Symbol-aware code exploration tools.
- `sonarqube` (http) — SonarCloud MCP at `https://api.sonarcloud.io/mcp` (Bearer token).

**MCP tools exposed by `gbkt-mcp-server` (17 tools registered in `ToolHandlers.kt`):**
`emulator_start`, `emulator_stop`, `emulator_step`, `emulator_observe`, `emulator_wait_for_scene`, `emulator_wait_for_variable`, `emulator_wait_until_text`, `emulator_read_variable`, `emulator_write_variable`, `emulator_screenshot`, `emulator_describe_game`, `emulator_save_state`, `emulator_load_state`, `emulator_assert` (multi-check batch with `variable_equals`/`variable_in_range`/`scene_is`/`text_on_screen`/`actor_visible`/`sprite_count`), `emulator_get_playbook`, `emulator_list_games`. (Module CLAUDE.md tallies 16 in its summary table; the 17th — `emulator_list_games` — appears in the same file's last row, and is the count referenced in the repo-root CLAUDE.md.)

**Claude Code skills (`.claude/commands/`):**
- `gbkt-play-game.md` — `/gbkt-play-game <game>` interactive play session.
- `gbkt-test-game.md` — `/gbkt-test-game <game|all>` automated verification suite.

Installation/refresh task: `./gradlew gbktSetupClaude` (registered by the gbkt Gradle plugin) — installs skills, merges `.claude/mcp_servers.json`, writes `.claude/.gbkt-version` marker for staleness detection, cleans up legacy skill names.

## IntelliJ Plugin SDK

- IntelliJ Platform Gradle Plugin 2.10.5.
- Target: IntelliJ IDEA Community 2024.2 (`sinceBuild = "242"`, `untilBuild = null`).
- Bundled plugins: `com.intellij.java`, `org.jetbrains.kotlin`.
- Tooling: `pluginVerifier()`, `zipSigner()`, `testFramework(TestFrameworkType.Platform)`.
- Signing/publishing wired through `intellijPlatform { signing { ... }; publishing { ... } }` in `gbkt-intellij-plugin/build.gradle.kts`.

## Maven Central Publishing

- Convention plugin: `buildSrc/src/main/kotlin/gbkt.publishing.gradle.kts` — applied as `id("gbkt.publishing")` by every published module.
- POM metadata defaulted: license MPL 2.0 (Apache 2.0 only for `gbkt-intellij-plugin`), developer `svachmic`, SCM `github.com/svachmic/gbkt`.
- Repositories: OSSRH staging (`https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`) for releases; OSSRH snapshots (`https://s01.oss.sonatype.org/content/repositories/snapshots/`) for `-SNAPSHOT`; GitHub Packages alternative.
- Signing: in-memory PGP via `useInMemoryPgpKeys(...)`; only required when `!version.endsWith("SNAPSHOT") && hasTask("publishToOSSRH")` (or `publishPlugins`).
- Sources + Javadoc jars: `java { withJavadocJar(); withSourcesJar() }` auto-applied for JVM modules.
- BOM (`gbkt-bom/build.gradle.kts`) — `java-platform` constraints across 13 modules; published to OSSRH and GitHub Packages.

## Docker

- `docker/Dockerfile.gbdk` — `debian:bookworm-slim` base, installs GBDK-2020 4.5.0 from GitHub releases into `/opt/gbdk`, sets `GBDK_HOME=/opt/gbdk` and adjusts `PATH`. Used by CI / E2E ROM-build pipelines.

---

*Integration audit: 2026-05-27*
