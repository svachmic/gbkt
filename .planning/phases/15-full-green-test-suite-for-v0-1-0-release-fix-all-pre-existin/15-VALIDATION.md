---
phase: 15
slug: full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-09
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit5 + `kotlin.test`; Gradle TestKit (`GradleRunner`) for the plugin; `GbktTestExtension` / `StepAgent` / `UatRunner` + `gbkt-emulator` MCP for example UAT |
| **Config file** | per-module `build.gradle.kts` `test {}`; root `build.gradle.kts` `pluginTest` aggregator |
| **Quick run command** | per failing module, e.g. `./gradlew :gbkt-examples:pong:test` |
| **Full suite command** | `./gradlew test --continue` AND `./gradlew pluginTest` (both must be green — SPEC Req 1) |
| **Estimated runtime** | ~minutes (full suite, host-dependent; emulator-tier tests auto-skip without prerequisites) |

---

## Sampling Rate

- **After every task commit:** Run the single affected module's `:test`
- **After every plan wave:** Run `./gradlew test --continue` (collateral drift) + `./gradlew pluginTest` (after F1)
- **Before `/gsd-verify-work`:** Both aggregate commands green from a clean tree + 7× `:buildRom` EXIT 0 + D-02 byte-identity split-guard
- **Max feedback latency:** single-module test runtime (seconds–low minutes)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-01 | 01 | 1 | Req 2 / Req 7 | — | N/A | aggregate inventory | `./gradlew test --continue` + `./gradlew pluginTest` | ✅ | ✅ green |
| 15-02 | 02 | 2 | Req 3 (F1) | — | deterministic SNAPSHOT resolve | TestKit | `./gradlew pluginTest` | ✅ | ✅ green |
| 15-03 | 03 | 2 | Req 4 (F5/F6) | — | N/A | UAT + D-03 screenshot | `./gradlew :gbkt-examples:banks:test` | ✅ | ✅ green |
| 15-04 | 04 | 2 | Req 5 (F2) | — | N/A | metadata/UAT | `./gradlew :gbkt-examples:pong:test` | ✅ | ✅ green |
| 15-05 | 05 | 3 | Req 6 (F3/F4/F7) | — | N/A | geometry grep + UAT + D-03 | `./gradlew :gbkt-examples:platformer-template:test` | ✅ | ✅ green |
| 15-06 | 06 | 4 | Req 1 / Req 7 | — | N/A | green gate + byte-identity guard | `./gradlew test --continue` + `pluginTest` + 7× `:buildRom` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. All six failing test classes
(`IntegrationTest`, `BanksUatTest`, `PongStepAgentTest`, `PlatformerTemplate128UatTest`,
`PlatformerTemplateUatTest`, `PlayerMetaspriteGeometryTest`) already exist — this phase
fixes/repoints them, it does not author new test infrastructure. The only new artifact is
the Req-7 diagnosis ledger under `evidence/` (D-06), scaffolded by 15-01.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Banks anchor scene is/ isn't legitimately near-blank (F5/F6 verdict) | Req 4 | Visual "X is visible" truth — D-03 / CLAUDE.md Visual Evidence Rule forbids variable-only evidence | Live `mcp__gbkt-emulator__emulator_screenshot` to `evidence/`; decide real-bug vs stale-premise |
| Platformer facing flip is visually real (F7 verdict) | Req 6 | Visual truth; the >10% global threshold has an arithmetic smell (Pitfall 4) | GBC-mode MCP (`gbcMode=true` + `.noi`), RIGHT+A traversal, capture facing-L vs facing-R screenshots to `evidence/` |

*The OAM-count verdict (F2) is NOT manual — it is a static metadata/runtime-OAM truth (D-03b);
use metadata.json + one emulator OAM read, not a visual screenshot session (Pitfall 3).*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — existing tests)
- [x] No watch-mode flags
- [x] Feedback latency < single-module test runtime (15-01/15-06 aggregate gates are inventory/final-gate plans by design; all per-fix plans use single-module/test-filtered commands)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated — all 6 tasks green, all requirements automated-or-justified-manual.

---

## Validation Audit 2026-06-09

Post-execution audit (State A). No test gaps to fill — all six target test classes
pre-existed and were fixed/repointed by the phase; both aggregate gate commands re-run
green on 2026-06-09 (`test --continue` BUILD SUCCESSFUL, `pluginTest` BUILD SUCCESSFUL).
The two manual-only visual verdicts are backed by committed screenshot evidence
(`evidence/banks-anchor1-play-scene.png`, `evidence/banks-anchor2-tilemap.png`,
`evidence/platformer-facing-left.png`, `evidence/platformer-facing-right.png`).
Per-Task Map statuses flipped ⬜ pending → ✅ green.

| Metric | Count |
|--------|-------|
| Requirements | 7 |
| Covered (automated) | 7 |
| Manual-only (justified, evidence-backed) | 2 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
