---
phase: 18
slug: deprecation-removals-and-sonar-burn-down
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-13
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Kotlin/JVM) + GBDK ROM byte-identity sweep |
| **Config file** | Gradle (`build.gradle.kts` per module); GBDK toolchain via gbkt-gradle-plugin |
| **Quick run command** | `./gradlew :gbkt-lang:test :gbkt-genre-rpg:test` (module-scoped per task) |
| **Full suite command** | `./gradlew test pluginTest` |
| **Estimated runtime** | ~minutes (JVM tests); ROM sweep adds GBDK compile per example |

> **Emitting-code oracle (SONAR-02 / D-06):** every S3776 refactor commit touching
> `codegen/visitor/**` or `GBDKPipeline.kt` (and the other EMITTING files in RESEARCH.md)
> must pass a 7-example byte-identity ROM sweep:
> `./gradlew :gbkt-examples:pong:buildRom :gbkt-examples:breakout:buildRom :gbkt-examples:simple-physics:buildRom :gbkt-examples:metasprites:buildRom :gbkt-examples:metasprites-stress:buildRom :gbkt-examples:banks:buildRom :gbkt-examples:platformer-template:buildRom`
> (single invocation — NO parallel `gradle clean`; Kotlin daemon collision risk).
> **pong.gb is PASS\*** — known toolchain non-determinism (hashes differ every rebuild even
> from identical C); compare generated C, not the ROM hash, for pong. See RESEARCH.md.

---

## Sampling Rate

- **After every task commit:** Run the module-scoped quick test; for EMITTING S3776 commits, run the 7-example byte-identity sweep as exit evidence.
- **After every plan wave:** Run `./gradlew test pluginTest`.
- **Before `/gsd-verify-work`:** Full suite green + one consolidated full 7-example sweep as backstop (D-06).
- **Max feedback latency:** module-scoped quick test (~seconds–low minutes).

---

## Per-Task Verification Map

> Populated by the planner from RESEARCH.md's S3776 inventory + DEPR/SEED tasks.
> EMITTING S3776 rows carry the byte-identity ROM sweep as their automated command;
> NON-EMITTING rows carry JVM-test-only evidence.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 18-XX-XX | XX | X | DEPR/SONAR/SEED | T-18-XX / — | N/A (refactor/docs) | unit / rom-byte-identity | `./gradlew …` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure (JUnit 5 + GBDK ROM build via gbkt-gradle-plugin) covers all phase requirements. No new test framework needed. byte-identity is verified against committed baselines / prior-commit C output.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| SonarCloud S3776 HIGH count = 0 | SONAR-01 | Live SonarCloud gate is the authoritative oracle; not reproducible as a local unit assertion | After phase, confirm SonarCloud reports 0 S3776 HIGH findings and ≤5 NOSONAR suppressions milestone-wide |

*All other phase behaviors have automated verification (JVM tests + byte-identity ROM sweep).*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency acceptable
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
