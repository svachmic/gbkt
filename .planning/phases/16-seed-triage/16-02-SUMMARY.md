---
phase: 16-seed-triage
plan: "02"
subsystem: testing
tags: [gradle, rom-build, jvm-test, substrate, evidence, seed-triage]

requires:
  - phase: 16-seed-triage-01
    provides: TRIAGE.md skeleton, evidence/ directory, archive/backlog dirs

provides:
  - "Pinned HEAD SHA (D-14): 8cef3dbca7d0868f42cf0d627921b8559d7754e8 in evidence/substrate-sha.txt"
  - "All 7 example ROMs built from single serial Gradle invocation (D-13)"
  - "Full JVM suite baseline: 3656 tests, 3655 PASS, 0 failures (seed-relevant tests highlighted)"
  - "Plugin validation baseline: validatePlugins + pluginTest 174/174 PASS"
  - "BanksEmissionTest INV-2 GREEN at HEAD → SEED-014 VERIFIED-ALREADY-FIXED signal"
  - "GameIRSerializerTest 15/15 GREEN → SEED-020 likely VERIFIED-ALREADY-FIXED signal"
  - "SEED-026 validatePlugins PASS → SEED-026 VERIFIED-ALREADY-FIXED signal"
  - "MCP shadow JAR rebuilt post-substrate: gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar"
  - "substrate-buildrom-report.txt, substrate-test-report.txt, plugin-validate-report.txt"

affects:
  - "16-seed-triage W2 cluster plans (metasprites, banks, platformer, DSL/tooling)"
  - "16-seed-triage W3 visual review gate"
  - "SEED-014 disposition in TRIAGE.md"
  - "SEED-015 disposition (INV-5 GREEN)"
  - "SEED-020 disposition (serializer round-trip GREEN)"
  - "SEED-026 disposition (validatePlugins GREEN)"
  - "metasprites-byte-identity-baseline-stale-since-12.8 folded todo disposition"

tech-stack:
  added: []
  patterns:
    - "D-13 substrate pass: single serial ./gradlew invocation for all 7 buildRom targets"
    - "D-14 SHA pinning: git rev-parse HEAD written to evidence/substrate-sha.txt before build"
    - "T-16-05 mitigation: ./gradlew :gbkt-mcp-server:shadowJar runs immediately post-substrate"

key-files:
  created:
    - ".planning/phases/16-seed-triage/evidence/substrate-sha.txt"
    - ".planning/phases/16-seed-triage/evidence/substrate-buildrom-report.txt"
    - ".planning/phases/16-seed-triage/evidence/substrate-test-report.txt"
    - ".planning/phases/16-seed-triage/evidence/plugin-validate-report.txt"
  modified: []

key-decisions:
  - "D-13 complied: single serial Gradle invocation for all 7 clean+buildRom (no parallel builds)"
  - "D-14 complied: SHA 8cef3dbca7d0868f42cf0d627921b8559d7754e8 pinned before build in substrate-sha.txt"
  - "MetaspritesGeneratedSpriteByteIdentityTest unexpectedly GREEN: todo metasprites-byte-identity-baseline-stale-since-12.8 may be moot; cluster agent must confirm"
  - "validatePlugins + pluginTest both GREEN on first run: SEED-026 signalled VERIFIED-ALREADY-FIXED"

patterns-established:
  - "Substrate pass pattern: all 7 clean+buildRom chained in one ./gradlew call, then MCP JAR rebuilt"

requirements-completed: [TRIAGE-01]

duration: 7min
completed: "2026-06-12"
---

# Phase 16 Plan 02: Substrate Pass Summary

**Single serial Gradle substrate pass produced all 7 example ROMs + pinned HEAD SHA 8cef3dbc; full JVM suite 3655/3656 GREEN with BanksEmissionTest INV-2, GameIRSerializerTest, and validatePlugins all PASS — SEED-014, SEED-015, SEED-020, and SEED-026 signalled VERIFIED-ALREADY-FIXED**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-12T13:48:23Z
- **Completed:** 2026-06-12T13:55:46Z
- **Tasks:** 2
- **Files modified:** 4 (all new evidence artifacts)

## Accomplishments

- Built all 7 example ROMs in one serial `./gradlew` invocation per D-13 (no parallel daemon collision risk)
- Pinned HEAD SHA `8cef3dbca7d0868f42cf0d627921b8559d7754e8` to `evidence/substrate-sha.txt` (D-14 evidence anchor) before the build
- Rebuilt `gbkt-mcp-server` shadow JAR post-substrate (T-16-05 mitigation; wiped by `clean` targets)
- Ran full JVM suite: 3656 tests, 3655 PASS, 0 failures, 1 skip (INV-8 expected skip) — all seed-relevant test classes GREEN
- `validatePlugins + pluginTest` both PASS on first invocation (174 plugin tests); no race failure

## Task Commits

Each task was committed atomically:

1. **Task 1: Build all 7 example ROMs and pin HEAD SHA** — `b54f5a1d` (chore)
2. **Task 2: Run full JVM suite + plugin validation** — `95ef19d6` (chore)

## Files Created/Modified

- `.planning/phases/16-seed-triage/evidence/substrate-sha.txt` — HEAD SHA + capture timestamp (D-14 anchor)
- `.planning/phases/16-seed-triage/evidence/substrate-buildrom-report.txt` — per-example pass/fail; pong flagged PASS*
- `.planning/phases/16-seed-triage/evidence/substrate-test-report.txt` — JVM suite summary; BanksEmissionTest INV-2/INV-6 GREEN; GameIRSerializer 15/15 GREEN; MetaspritesGeneratedSpriteByteIdentityTest GREEN (unexpected)
- `.planning/phases/16-seed-triage/evidence/plugin-validate-report.txt` — validatePlugins PASS; pluginTest 174/174 PASS; race re-run not required

## Decisions Made

- **D-13 complied:** All 7 `clean` + `buildRom` pairs chained in one `./gradlew` command. No parallel Gradle invocations.
- **D-14 complied:** SHA pinned to substrate-sha.txt before the build ran — all downstream evidence is attributable to commit `8cef3dbca7d0868f42cf0d627921b8559d7754e8`.
- **MetaspritesGeneratedSpriteByteIdentityTest unexpectedly GREEN:** The folded todo `metasprites-byte-identity-baseline-stale-since-12.8` predicted a RED test. At HEAD the test is GREEN. Possible explanation: baseline was updated after Phase 12.8 added `-keep_palette_order`, or the flag change did not alter elephant.c byte output. Cluster A agent must inspect the baseline file date to determine if the todo is moot (VERIFIED-ALREADY-FIXED) or if the test is a false positive.
- **pluginTest first-run clean:** The known publish/test ordering race did not fire on this run. Only one invocation needed.

## Deviations from Plan

None — plan executed exactly as written. All substrate-pass tasks ran in the specified order with the specified commands. The unexpected MetaspritesGeneratedSpriteByteIdentityTest GREEN is noteworthy but does not constitute a deviation (the plan said "if RED, record as EXPECTED; if GREEN, that's fine"). Results are documented in substrate-test-report.txt.

## Issues Encountered

None. The single serial `./gradlew` invocation completed in 27s for all 7 ROMs (BUILD SUCCESSFUL). The MCP shadow JAR rebuild completed in 4s. The full JVM suite (`./gradlew test`) took 2m 4s. The `pluginTest` first invocation took 35s.

## Known Stubs

None — this plan produces only evidence documentation artifacts, no code.

## Threat Flags

No new security-relevant surface introduced. This plan runs Gradle tasks and captures their output to `.planning/` text files. No network endpoints, auth paths, or schema changes.

## Seed Triage Signals (for cluster agents)

The following dispositions are **signalled** by this substrate pass but must be **confirmed** by cluster agents with per-seed evidence in TRIAGE.md:

| Seed | Signal from substrate | Recommended disposition |
|------|-----------------------|------------------------|
| SEED-014 | BanksEmissionTest INV-2 GREEN | VERIFIED-ALREADY-FIXED |
| SEED-015 | BanksEmissionTest INV-5 GREEN (title_enter_trampoline correct) | VERIFIED-ALREADY-FIXED |
| SEED-020 | GameIRSerializerTest 15/15 GREEN | Likely VERIFIED-ALREADY-FIXED (cluster agent to confirm stubs gone) |
| SEED-026 | validatePlugins PASS | VERIFIED-ALREADY-FIXED |
| metasprites-byte-identity-baseline-stale-since-12.8 | MetaspritesGeneratedSpriteByteIdentityTest GREEN (unexpected) | Likely VERIFIED-ALREADY-FIXED; cluster agent to verify baseline integrity |

## Next Phase Readiness

The substrate pass (W1) is complete. All 7 ROMs built and present in `gbkt-examples/<example>/build/gbkt/output/`. Generated C is available at `gbkt-examples/<example>/build/gbkt/generated/`. MCP shadow JAR is available for visual-seed cluster agents.

W2 cluster plans (Plans 03–06: metasprites, banks, platformer, DSL/tooling misc) can now proceed in parallel, reading these shared artifacts read-only per D-16. Cluster agents MUST NOT run `clean` or `buildRom`.

---
*Phase: 16-seed-triage*
*Completed: 2026-06-12*

## Self-Check: PASSED

- substrate-sha.txt: FOUND at `.planning/phases/16-seed-triage/evidence/substrate-sha.txt` (contains 40-char SHA)
- substrate-buildrom-report.txt: FOUND at `.planning/phases/16-seed-triage/evidence/substrate-buildrom-report.txt`
- substrate-test-report.txt: FOUND at `.planning/phases/16-seed-triage/evidence/substrate-test-report.txt`
- plugin-validate-report.txt: FOUND at `.planning/phases/16-seed-triage/evidence/plugin-validate-report.txt`
- Commit b54f5a1d (Task 1): FOUND in git log
- Commit 95ef19d6 (Task 2): FOUND in git log
