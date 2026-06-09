---
phase: 14
slug: cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-06
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> This is a CLEANUP-ONLY, behavior-neutral phase. The dominant validation gate is
> generated-C **byte-identity** (per mutating track) plus whole-tree compile +
> full JVM suite GREEN. See `14-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle 9.0 + JUnit5 (Kotlin 2.3.0, JVM 21) |
| **Config file** | `build.gradle.kts` / per-module `build.gradle.kts` |
| **Quick run command** | `./gradlew :<module>:test` (scoped to the touched module) |
| **Full suite command** | `./gradlew test pluginTest` (NOTE: `pluginTest`, NOT `:gbkt-gradle-plugin:test`) |
| **Estimated runtime** | full suite ~minutes (multi-module); per-module ~seconds |

---

## Sampling Rate

- **After every task commit:** Run scoped `./gradlew :<module>:test` for the touched module(s).
- **After every plan wave:** Run full `./gradlew test pluginTest` (whole-tree compile + suite GREEN).
- **After each MUTATING track (retire / dead-code sweep / V2 rename):** Regenerate C for every KEEP
  example (`./gradlew :<example>:generateC`) and diff SHA-256 of `main.c` + all `bank*.c` against the
  pre-phase baseline snapshot (D-05/D-07). Any non-zero diff localizes drift to that track.
- **Before release-readiness sign-off:** clean `:buildRom` EXIT 0 for each KEEP example (pong ROM
  exempt — PASS\* toolchain nondeterminism), full suite GREEN, acceptance grep returns zero.
- **Max feedback latency:** scoped module test < ~60s; byte-identity diff < ~minutes per example.

---

## Per-Task Verification Map

> Populated/maintained by the planner. Cleanup tasks verify via byte-identity diff, compile/suite
> GREEN, `git ls-files` emptiness, and the V2 acceptance grep rather than new unit assertions.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 1 | Req 5 (baseline) | — / — | N/A (behavior-neutral) | snapshot | `./gradlew <ex>:generateC && sha256sum build/gbkt/generated/*.c` | ✅ | ⬜ pending |
| 14-04-02 | 04 | 4 | Req 4 (dead-code fold) | — / — | non-reachability proven before removal | suite | `./gradlew test pluginTest` | ✅ | ⬜ pending |
| 14-05-03 | 05 | 5 | Req 3 (V2 rename) | T-14-09 / T-14-10 | reflection path clean + byte-identical C | snapshot+suite | `./gradlew :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` (+ `<ex>:generateC` byte-identity diff vs baseline) | ✅ | ⬜ pending |
| 14-06-03 | 06 | 6 | Req 3 (V2 rename) | T-14-11 / T-14-12 | acceptance grep == 0 + byte-identical C | snapshot+suite | `./gradlew test pluginTest` (+ acceptance grep `[A-Za-z_]*V2\b` == 0, byte-identity diff vs baseline) | ✅ | ⬜ pending |
| 14-08-01 | 08 | 8 | Req 1-5 (final sweep) | all | full-suite GREEN + buildRom + byte-identity | suite | `./gradlew test pluginTest` (+ KEEP-example `:buildRom`, final byte-identity diff vs baseline) | ✅ | ⬜ pending |
| 14-08-02 | 08 | 8 | Req 3 (acceptance gate) | T-14-11 | acceptance grep returns zero | grep gate | `grep -rE "[A-Za-z_]*V2\b" --include="*.kt" . --exclude-dir=build --exclude-dir=.git --exclude-dir=.claude \| grep -c .` (== 0) | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements: JUnit5 suite + committed
  `*GeneratedSpriteByteIdentityTest` (metasprites, metasprites-stress) provide the second
  byte-identity gate (D-06). No new test framework needed.
- Wave 0 deliverable for this phase = the **pre-phase generated-C baseline snapshot** for every KEEP
  example (D-05/D-07), captured BEFORE any mutating track, so subsequent diffs prove behavior-neutrality.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| KEEP-example boot + one input cycle | Req 1 (D-01/D-02) | Visual Evidence Rule — runtime-visible truth needs a screenshot, not a JVM assertion; GBDK ROM + live MCP emulator required | `mcp__gbkt-emulator__emulator_start` (gbcMode + .noi per example) → boot to first screen, `emulator_screenshot` → drive one input cycle per PLAYBOOK.md → `emulator_screenshot` again; store both PNGs in phase `evidence/` |
| clean `:buildRom` EXIT 0 per KEEP example | Req 1 / Req 5 | GBDK toolchain not provisioned in CI; local/manual only (D-13) | `./gradlew clean :gbkt-examples:<ex>:buildRom` serially (no parallel clean) |

---

## Validation Sign-Off

- [ ] Every mutating track is followed by a byte-identity diff over all KEEP examples
- [ ] Sampling continuity: no mutating track commits without compile + suite GREEN
- [ ] Pre-phase baseline snapshot captured before first mutation (Wave 0)
- [ ] No watch-mode flags
- [ ] Feedback latency acceptable (scoped tests fast; full sweep at wave boundaries)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
