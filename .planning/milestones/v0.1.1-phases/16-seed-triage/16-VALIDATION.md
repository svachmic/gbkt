---
phase: 16
slug: seed-triage
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-12
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 via Gradle 9.5.1 (Kotlin 2.3.20, JVM 21) |
| **Config file** | build.gradle.kts (per module) |
| **Quick run command** | `./gradlew test` |
| **Full suite command** | `./gradlew test pluginTest` |
| **Estimated runtime** | ~300 seconds |

---

## Sampling Rate

- **After every task commit:** Run `{quick run command}`
- **After every plan wave:** Run `{full suite command}`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 16-01-01 | 01 | 1 | TRIAGE-01 | — | N/A | file-assert | `test -f .planning/seeds/archive/.gitkeep && test -f .planning/backlog/v0.2.0/.gitkeep && test -f evidence/_drafts/.gitkeep` | ✅ | ⬜ pending |
| 16-01-02 | 01 | 1 | TRIAGE-01 | — | N/A | grep-assert | `grep -c '^\| SEED-\|^\| TODO-' TRIAGE.md` (47 rows) + RE-DEFERRED count | ✅ | ⬜ pending |
| 16-02-01 | 02 | 1 | TRIAGE-01 | T-16 SHA-attribution | substrate SHA pinned before any evidence capture | build+file-assert | `grep -Eq '[0-9a-f]{40}' evidence/substrate-sha.txt` + 7 ROM artifacts + MCP shadow JAR present | ✅ | ⬜ pending |
| 16-02-02 | 02 | 1 | TRIAGE-01 | T-16 SHA-attribution | test logs traceable to pinned SHA | test-suite | `test -s evidence/substrate-test-report.txt && grep -iq 'INV-2' evidence/substrate-test-report.txt` | ✅ | ⬜ pending |
| 16-03-01 | 03 | 2 | TRIAGE-02 | T-16 evidence-integrity | screenshots captured at pinned HEAD, gbcMode=true | screenshot-assert | `test -f evidence/SEED-004/screenshot.png` (+ SEED-005, SEED-013) | ✅ | ⬜ pending |
| 16-03-02 | 03 | 2 | TRIAGE-02 | T-16 evidence-integrity | screenshots captured at pinned HEAD, gbcMode=true | screenshot-assert | per-seed `test -f evidence/<SEED>/screenshot.png` loop (7 platformer/metasprite seeds) | ✅ | ⬜ pending |
| 16-03-03 | 03 | 2 | TRIAGE-02 | T-16 verdict-finalization | no agent self-approval — doc marked PENDING HUMAN APPROVAL | grep-assert | `grep -q 'PENDING HUMAN APPROVAL' visual-review-document.md` + 10 `## SEED-` sections | ✅ | ⬜ pending |
| 16-04-01 | 04 | 2 | TRIAGE-01 | T-16 no-source-mutation | generated-C inspection only, no source edits | file-assert | per-seed `test -s evidence/<SEED>/main-c-excerpt.txt` loop (SEED-006..011) | ✅ | ⬜ pending |
| 16-04-02 | 04 | 2 | TRIAGE-01 | T-16 no-source-mutation | inspection-only evidence | file+grep-assert | sprite-outline + TODO-metasprites-baseline evidence + 8 cluster-draft rows | ✅ | ⬜ pending |
| 16-05-01 | 05 | 2 | TRIAGE-01 | T-16 no-source-mutation | INV-2 sentinel decides SEED-014, not source reading | test+file-assert | `test -s evidence/SEED-014/inv2-test-output.txt && grep -iq 'INV-2' ...` (+ SEED-015/016) | ✅ | ⬜ pending |
| 16-05-02 | 05 | 2 | TRIAGE-01 | T-16 no-source-mutation | inspection-only evidence | file+grep-assert | banks-audit evidence files + ≥8 cluster-banks draft rows | ✅ | ⬜ pending |
| 16-06-01 | 06 | 2 | TRIAGE-01 | T-16 no-source-mutation | Serena read-only inspection | file-assert | per-seed `ls evidence/<SEED>/*.txt` loop (SEED-002/003/012/020 + todo) | ✅ | ⬜ pending |
| 16-06-02 | 06 | 2 | TRIAGE-01 | T-16 no-source-mutation | Serena read-only inspection | file+grep-assert | SEED-023/025/026 evidence + 8 cluster-dsl draft rows | ✅ | ⬜ pending |
| 16-07-01 | 07 | 2 | TRIAGE-01 | T-16 no-source-mutation | Serena read-only inspection | file-assert | per-seed `test -s evidence/<SEED>/source-inspection.txt` loop (5 seeds) | ✅ | ⬜ pending |
| 16-07-02 | 07 | 2 | TRIAGE-01 | T-16 no-source-mutation | evidence-backed RE-DEFERRED rationales | file+grep-assert | zone-seed evidence files + 9 cluster-platformer-source draft rows | ✅ | ⬜ pending |
| 16-08-01 | 08 | 3 | TRIAGE-02 | T-16 verdict-finalization | blocking human gate (D-08) — manual, see below | checkpoint:human-verify | — (manual) | — | ⬜ pending |
| 16-08-02 | 08 | 3 | TRIAGE-02 | T-16 verdict-finalization | verdicts locked only after human sign-off | grep-assert | `grep -qi 'locked.*YES' visual-review-document.md` + ≥10 LOCKED rows in cluster-visual.md | ✅ | ⬜ pending |
| 16-09-01 | 09 | 4 | TRIAGE-01/02 | T-16 SHA-attribution | TRIAGE.md header carries substrate SHA | grep-assert | 47 rows, no TBD, SHA present, `grep -q 'FINAL' TRIAGE.md` | ✅ | ⬜ pending |
| 16-09-02 | 09 | 4 | TRIAGE-01 | — | stamps are pointers only (D-02) | grep-count | `grep -rl 'triage_disposition:\|> \*\*Triage:\*\*' .planning/seeds/SEED-*.md \| wc -l` = 44 | ✅ | ⬜ pending |
| 16-10-01 | 10 | 5 | TRIAGE-01/03 | — | git mv preserves history | file+grep-assert | every remaining seed has a TRIAGE row; archive/ and backlog/v0.2.0/ non-empty | ✅ | ⬜ pending |
| 16-10-02 | 10 | 5 | TRIAGE-03 | — | N/A | grep-assert | `grep -q 'backlog/v0.2.0' REQUIREMENTS.md` + TRIAGE.md referenced in ROADMAP/REQUIREMENTS | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Batch visual review gate (16-08-01) | TRIAGE-02 | Visual Evidence Rule + D-08 — human must approve each visual verdict; agent self-approval forbidden | Review `visual-review-document.md` (10 per-seed sections, HEAD screenshot vs reference image), tick verdict checkboxes, complete sign-off block |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (21/21 auto tasks; 16-08-01 is a deliberate human checkpoint)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — existing infrastructure suffices)
- [x] No watch-mode flags
- [x] Feedback latency < 300s (most verifies are file/grep asserts, sub-second)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-12 (plan-checker Dimension 8 PASS)
