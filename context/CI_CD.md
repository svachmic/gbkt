# CI/CD Workflows

gbkt uses four GitHub Actions workflows for build, quality, release, and security analysis. All action dependencies are pinned to SHA hashes for supply-chain security and kept up-to-date by Dependabot.

## Workflows

### Kotlin (`kotlin.yml`)

**Triggers:** Push to `master` or PR to `master` (path-filtered to source modules + build files)

Three parallel jobs:

| Job | What it does |
|-----|-------------|
| `build` | Publishes library modules to mavenLocal, builds all modules, runs tests, verifies example C generation. Uploads test reports on failure. |
| `code-quality` | Runs Spotless formatting check (`spotlessCheck`) |
| `version-consistency` | Runs `checkVersionConsistency` to verify all modules declare the same version |

Concurrency: one run per branch, cancels in-progress.

### SonarCloud (`sonar.yml`)

**Triggers:** Push to `master` or PR to `master` (path-filtered)

Single job:
1. Publishes library modules to mavenLocal
2. Runs Kotlin tests with Kover coverage reporting
3. Executes SonarCloud scan

**Secrets:** `SONAR_TOKEN`, `GITHUB_TOKEN`

Concurrency: one run per branch, cancels in-progress.

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

Single job with `continue-on-error: true` — CodeQL does not yet support Kotlin 2.3.0 (tracking: https://github.com/github/codeql/issues/20661).

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
