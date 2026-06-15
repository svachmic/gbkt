---
phase: 17
slug: docs-reconciliation-and-quality-cleanup
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-12
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 via Gradle (Kotlin 2.3.20, Gradle 9.5.1) |
| **Config file** | build.gradle.kts (root + per-module), config/detekt/detekt.yml |
| **Quick run command** | `./gradlew detekt` |
| **Full suite command** | `./gradlew build && ./gradlew pluginTest` |
| **Estimated runtime** | ~300 seconds (full), ~60 seconds (detekt) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew detekt`
- **After every plan wave:** Run `./gradlew build` (compiles + tests affected modules)
- **Before `/gsd-verify-work`:** Full suite must be green, plus clean `:gbkt-examples:<game>:buildRom` smoke for codegen-touching plans
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner) | — | — | DOCS-01..03, QUAL-01..03 | — | N/A | gradle | `./gradlew detekt` / `./gradlew build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements — detekt and the Gradle test suite are already configured; no new test framework installation is needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| DSL_REFERENCE.md accuracy vs. implemented DSL | DOCS-01, DOCS-02 | Doc prose accuracy cannot be unit-tested | Cross-read each rewritten section against the builder source it documents |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
