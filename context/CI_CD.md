# CI/CD Workflows

gbkt uses three GitHub Actions workflows for build, release, and security analysis. All action dependencies are pinned to SHA hashes for supply-chain security and kept up-to-date by Dependabot.

## Workflows

### Kotlin (`kotlin.yml`)

**Triggers:** Push to `master` or PR to `master` (path-filtered to source modules + build files)

One sequenced `build` job plus two light parallel checks:

| Job | What it does |
|-----|-------------|
| `build` | Sequenced on one runner so nothing is compiled twice: install GBDK-2020 (version + sha256 pinned, cached) → publish sandbox modules to mavenLocal (`:publishConsumedModulesToMavenLocal --configure-on-demand`) → `./gradlew build pluginTest -x detekt -x spotlessCheck` (every module's assemble + tests, incl. examples and the gradle plugin; static analysis is excluded — it belongs to `code-quality`) → `koverXmlReport` (merge per-module coverage, uploaded as the `coverage-report` artifact) → ROM build smoke (`buildRom` for all 7 examples via lcc). Uploads test reports on failure. |
| `sonar` | `needs: build` — downloads the `coverage-report` artifact and runs the SonarCloud scan. Cheap by construction: `sonar` depends only on per-project `sonarResolver` tasks (verified via `--dry-run`), never compiling or testing; full-history checkout (`fetch-depth: 0`) for blame/new-code detection; no GBDK needed. |
| `code-quality` | All static analysis: `detekt spotlessCheck :gbkt-gradle-plugin:spotlessCheck` (the plugin is an included build, so it must be addressed explicitly; it does not apply detekt — tracked debt) |
| `version-consistency` | `:checkVersionConsistency --configure-on-demand` — verifies all modules declare the same version |

**Job separation rule:** build/test failures and lint findings must never share a step. Detekt and spotless hook into Gradle's `check` lifecycle by default (so local `./gradlew build` stays strict), but CI excludes them from the build job via `-x` and runs them only in `code-quality`.

**Cold-mavenLocal bootstrap rule:** configuring any example project compiles the gbkt-gradle-plugin included build, whose compile classpath resolves gbkt SNAPSHOTs from mavenLocal. Every job that triggers full project configuration must run `:publishConsumedModulesToMavenLocal --configure-on-demand` first — the rooted task path and the flag are both load-bearing (an unrooted task name makes Gradle configure every project while searching for it). Verify CI changes locally against a cold repository: `-Dmaven.repo.local=/tmp/empty-dir`.

**Secrets:** `SONAR_TOKEN`, `GITHUB_TOKEN` (build job, Sonar step)

Concurrency: one run per branch, cancels in-progress.

### Coverage

Every Kotlin module applies Kover (wired centrally in the root `build.gradle.kts`); the root `koverXmlReport` merges all module results into `build/reports/kover/report.xml` (JaCoCo XML format), which is the single report `sonar.coverage.jacoco.xmlReportPaths` points at. Do not re-introduce per-module report paths — a glob that matches only some modules silently under-reports project coverage (this is how SonarCloud once showed ~5% when actual line coverage was ~83%).

### GBDK in CI

The `build` job installs GBDK-2020 (release tarball pinned by version and sha256 in the workflow `env`, cached via `actions/cache`) so `convertSprites`, png2asset byte-identity tests, and the `buildRom` smoke all run for real. The pinned version must match the version developers use locally — png2asset output shape feeds byte-identity tests with committed goldens. To bump: update `GBDK_VERSION` and `GBDK_SHA256` together.

### Release (`release.yml`)

**Triggers:** Push of tags matching `v*` (e.g., `v0.1.0`)

Five sequential jobs:

```
build → publish-maven-central ──┐
     → publish-gradle-plugin ───┤→ create-release
     → publish-github-packages  │
```

| Job | What it does |
|-----|-------------|
| `build` | Builds and tests all modules with `-Prelease` flag. Uploads JARs as artifacts. |
| `publish-maven-central` | Publishes library modules to OSSRH (Maven Central) with GPG signing |
| `publish-gradle-plugin` | Publishes `gbkt-gradle-plugin` to the Gradle Plugin Portal |
| `create-release` | Creates a GitHub Release with auto-generated notes, SBOM, and downloadable artifacts |
| `publish-github-packages` | Publishes to GitHub Packages as a backup distribution channel |

**Secrets:**

| Secret | Used by |
|--------|---------|
| `OSSRH_USERNAME` / `OSSRH_PASSWORD` | Maven Central publishing |
| `SIGNING_KEY` / `SIGNING_PASSWORD` | GPG artifact signing |
| `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET` | Gradle Plugin Portal |
| `GITHUB_TOKEN` | GitHub Release + GitHub Packages (auto-provided) |

### CodeQL (`codeql.yml`)

**Triggers:** Push/PR to `master`/`main` (path-filtered) + weekly schedule (Monday 00:00 UTC)

Single blocking job. The build step is preceded by the same cold-mavenLocal bootstrap as `kotlin.yml` (`:publishConsumedModulesToMavenLocal --configure-on-demand`) — without it, configuring the build fails resolving gbkt SNAPSHOTs on a fresh runner.

CodeQL supports Kotlin up to 2.3.20 (since CodeQL 2.25.2). Kotlin 2.4.x is **not** yet supported (tracking: https://github.com/github/codeql/issues/21938) — hold compiler bumps past 2.3.20 until that lands, or re-add `continue-on-error: true` knowingly.

Concurrency: one run per branch, cancels in-progress.

## Dependency Pinning

All GitHub Actions references use full SHA hashes with the version tag preserved as a comment:

```yaml
uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6
```

This prevents supply-chain attacks where a compromised tag could inject malicious code into the CI pipeline.

### Automated Updates

Dependabot (`.github/dependabot.yml`) is configured with weekly checks for both `gradle` and `github-actions` ecosystems. When a new action version is released, Dependabot opens a PR with the updated SHA — just review and merge.

### Manual Version Bumps

To upgrade an action (e.g., `checkout` from v6 to v7):

1. **Option A (recommended):** Change the tag in the comment to the new version and re-run the pinning tool:
   ```bash
   # Edit the comment from # v6 to # v7, then:
   npx pin-github-action .github/workflows/<file>.yml
   ```

2. **Option B:** Replace the line with the new tag and re-run:
   ```bash
   # Change to: uses: actions/checkout@v7
   npx pin-github-action .github/workflows/<file>.yml
   ```

3. **Option C:** Let Dependabot handle it automatically via its weekly PR.

### Adding a New Action

When adding a new GitHub Action to any workflow:

1. Write the `uses:` line with the tag reference as normal
2. Before merging, pin it:
   ```bash
   npx pin-github-action .github/workflows/<file>.yml
   ```
3. Dependabot will keep it up-to-date from there
