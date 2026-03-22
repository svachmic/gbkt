# External Integrations

**Analysis Date:** 2026-02-17

## APIs & External Services

**Compiler Toolchain:**
- **GBDK-2020 (lcc compiler)**
  - Purpose: Compiles generated C code to Game Boy/Game Boy Color ROM files
  - Location: Auto-detected or set via GBDK_HOME env var or `gbkt { gbdkHome.set("/path") }`
  - Integration: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/GbdkToolchain.kt`
  - Usage in tasks: `CompileRomTask.kt` invokes lcc via Gradle ExecOperations
  - Environment variables: GBDK_HOME
  - Search paths: /opt/gbdk-2020, ~/gbdk-2020, etc.

**Emulator Integration:**
- **mGBA (Game Boy/Game Boy Color Emulator)**
  - Purpose: Run and debug compiled ROMs
  - Location: Auto-detected on macOS (.app bundles), Linux, Windows
  - Integration: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/RunEmulatorTask.kt`
  - Features: Live reload support via Lua scripting (optional)
  - Configuration: Custom Lua reload script support
  - Command: via Gradle RunEmulatorTask (./gradlew runEmulator)

**Web Deployment:**
- **EmulatorJS**
  - Purpose: Browser-based Game Boy emulator for web deployment
  - Integration: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/WebExportTask.kt`
  - Output: Static HTML/JS files for any HTTP server deployment
  - Use case: Play games in web browsers without installation

## Data & Asset Processing

**Map Format Support:**
- **Tiled Map Editor (JSON format)**
  - Purpose: Load dungeon/world maps from Tiled editor JSON exports
  - Parser: `gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt`
  - Dependency: org.json:json 20251224
  - Supported features:
    - Layer parsing and normalization
    - Tileset firstGid handling
    - Layer visibility tracking
  - Usage: Explore example uses in exploration system

**Asset Management:**
- **Sprite/Image Assets** (PNG, etc.)
  - Purpose: Define in-game sprites and UI graphics
  - Type-safe asset references: `gbkt-core/.../assets/` module
  - Processing: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateAssetsTask.kt`
  - Output: Asset data structures embedded in generated C code

**Source Map Integration:**
- **Source Map Loader**
  - Purpose: Map generated C code back to Kotlin DSL for debugging
  - Integration: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/SourceMapLoader.kt`
  - Format: .c.gbkt.map files (JSON-based)
  - Dependency: org.json:json 20251224
  - Output location: build/gbkt/generated/main.c.gbkt.map

## Code Quality & Monitoring

**SonarCloud Integration:**
- **Service:** https://sonarcloud.io
- **Organization:** svachmic
- **Project Key:** svachmic_gbkt
- **Purpose:** Code quality metrics, security scanning, coverage tracking
- **CI Integration:** `.github/workflows/sonar.yml`
- **Coverage Reports:** Kover (kotlinx.kover) generates JaCoCo XML
  - Path: build/reports/kover/report.xml
  - Uploaded to SonarCloud for trend analysis
- **Trigger:** Push to master and pull requests
- **Authentication:** SONAR_TOKEN (GitHub secret)

**GitHub Actions CI/CD:**
- **Kotlin Build Pipeline:** `.github/workflows/kotlin.yml`
  - Runs on: ubuntu-latest
  - JDK: Temurin 21
  - Gradle caching: gradle/actions/setup-gradle@v4
  - Triggers: Push to master, PRs, selective path triggers
  - Tasks: Build all modules, run tests, verify C generation, upload test reports

**Code Quality Gates:**
- **Spotless (Code Formatting)**
  - Enforced via spotlessCheck in CI
  - ktfmt with Kotlin language style
  - License headers validated

- **Detekt (Static Analysis)**
  - Runs as part of build.gradle.kts configuration
  - Custom baseline for tracking known violations
  - Parallel execution enabled

## Version Control & Release Management

**GitHub Integration:**
- **Repository:** https://github.com/svachmic/gbkt
- **VCS Endpoints:**
  - HTTPS: https://github.com/svachmic/gbkt.git
  - SSH: ssh://git@github.com/svachmic/gbkt.git
- **GitHub Issues:** Bug tracking and feature requests
- **GitHub Packages:** Alternative Maven repository (mvn.pkg.github.com)
  - Authentication: GITHUB_ACTOR, GITHUB_TOKEN

**Maven Central (OSSRH) Publishing:**
- **Service:** https://s01.oss.sonatype.org/
- **Release Repository:** https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/
- **Snapshot Repository:** https://s01.oss.sonatype.org/content/repositories/snapshots/
- **Artifacts:**
  - io.github.gbkt:gbkt-core
  - io.github.gbkt:gbkt-backend-api
  - io.github.gbkt:gbkt-backend-gbdk
  - io.github.gbkt:gbkt-bom (Bill of Materials)
  - io.github.gbkt:gbkt-cli
- **Authentication:** OSSRH_USERNAME, OSSRH_PASSWORD
- **Signing:** GPG signing via SIGNING_KEY, SIGNING_PASSWORD

**Gradle Plugin Portal:**
- **Service:** https://plugins.gradle.org/
- **Plugin ID:** io.github.gbkt
- **Published By:** gbkt-gradle-plugin module
- **Release Pipeline:** `.github/workflows/release.yml`
- **Authentication:** PUBLISH_TOKEN (Gradle Plugin Portal API key)

**IntelliJ Plugin Marketplace:**
- **Service:** JetBrains Plugin Marketplace
- **Plugin:** gbkt (IntelliJ IDEA support)
- **Build Tool:** IntelliJ Platform Gradle Plugin 2.10.5
- **IDE Target:** IntelliJ IDEA Community 2024.2+
- **Signing:** Certificate chain and private key (CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD)
- **Publishing:** Token-based authentication (PUBLISH_TOKEN)
- **Verification:** IntelliJ Plugin Verifier runs against recommended IDE versions

## Development & Testing Infrastructure

**GitHub Actions Workflows:**
- **kotlin.yml** - Main build and test pipeline
  - Builds all modules on every push to master and PRs
  - Generates C code from examples to verify DSL compilation
  - Uploads test reports on failure
  - Cache strategy: Cache read-only for non-master branches

- **sonar.yml** - Code quality analysis
  - Runs Kover coverage and uploads to SonarCloud
  - Full git history fetch (fetch-depth: 0) for history analysis

- **release.yml** - Release automation
  - Publishes artifacts to Maven Central
  - Publishes plugin to Gradle Plugin Portal
  - Publishes plugin to JetBrains Marketplace
  - Triggered on release tag creation

- **codeql.yml** - GitHub CodeQL security scanning
  - Detects security vulnerabilities in Kotlin code

## Authentication & Credentials

**Required Environment Variables for Release:**

| Variable | Service | Purpose |
|----------|---------|---------|
| OSSRH_USERNAME | Maven Central (OSSRH) | Sonatype account username |
| OSSRH_PASSWORD | Maven Central (OSSRH) | Sonatype account password/token |
| SIGNING_KEY | GPG | Armored private key for artifact signing |
| SIGNING_PASSWORD | GPG | Passphrase for signing key |
| SONAR_TOKEN | SonarCloud | Code quality metrics upload |
| GITHUB_TOKEN | GitHub Actions | Automatic (available in CI context) |
| GITHUB_ACTOR | GitHub Packages | Automatic (available in CI context) |
| CERTIFICATE_CHAIN | JetBrains Marketplace | Plugin signing certificate chain |
| PRIVATE_KEY | JetBrains Marketplace | Plugin signing private key |
| PRIVATE_KEY_PASSWORD | JetBrains Marketplace | Plugin signing key passphrase |
| PUBLISH_TOKEN | JetBrains Marketplace | Plugin marketplace API token |

**Storage:**
- Secrets stored as GitHub repository secrets
- Environment variables referenced in workflow files
- Never committed to version control

## Build Artifact Repositories

**Maven Central (OSSRH):**
- **Releases:** Stable version artifacts (e.g., 0.1.0)
- **Snapshots:** Development versions (e.g., 0.1.0-SNAPSHOT)
- **Credentials:** Configured in `buildSrc/src/main/kotlin/gbkt.publishing.gradle.kts`

**GitHub Packages:**
- **Alternative mirror** for Maven artifacts
- **Credentials:** GITHUB_ACTOR and GITHUB_TOKEN

**mavenLocal():**
- **Local development** - Gradle resolves from ~/.m2/repository
- **Used when:** Publishing to local maven during development

**Gradle Plugin Portal:**
- **Plugin Discovery** - Gradle automatically finds io.github.gbkt plugin
- **Plugin Publishing** - gbkt-gradle-plugin published directly

## Debugging & Error Handling

**GBDK Compiler Error Parsing:**
- **Module:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/GbdkErrorParser.kt`
- **Purpose:** Parse lcc compiler errors and provide developer-friendly messages
- **Integration:** Used in CompileRomTask error handling

**Error Enhancement:**
- **Module:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/ErrorEnhancer.kt`
- **Purpose:** Augment error messages with context from source maps and DSL
- **Feature:** Links errors back to original Kotlin code

**Debug Emulator Support:**
- **Module:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/DebugEmulatorTask.kt`
- **Purpose:** Launch emulator with debugging support
- **Features:** Breakpoint support, memory inspection, step execution

## CI/CD Pipeline Dependencies

**Docker Images:**
- **Base:** ubuntu-latest (GitHub Actions runner)
- **Java:** Temurin JDK 21
- **Build:** Gradle 9.0 (via gradle/actions/setup-gradle)

**Cache Strategy:**
- **Gradle Build Cache:** Enabled
  - Cache read-only for non-master branches (avoid stale artifacts)
  - Full cache write for master branch
- **Action:** gradle/actions/setup-gradle@v4

---

*Integration audit: 2026-02-17*
