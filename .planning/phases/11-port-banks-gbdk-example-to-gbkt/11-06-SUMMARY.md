---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 06
subsystem: testing
tags: [ir-test, junit5, jvm-tier, banks, gbdk]

requires:
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: "Banks.kt DSL substrate (banks symbol with build() entrypoint)"
provides:
  - "8 GREEN @Test methods locking IR shape of banks.build()"
  - "Tier-1 oracle for IR structure regression detection (scene count, start scene, variables, zones, save system)"
affects: ["11-07", "11-08", "11-09", "11-10", "11-11", "11-12", "11-13", "11-14"]

tech-stack:
  added: []
  patterns:
    - "JVM-tier IR validation per gbkt-examples/<name>/<Name>IRTest.kt convention"
    - "private val ir = banks.build() field-level init (one-shot deterministic IR)"

key-files:
  created: []
  modified:
    - "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt"

key-decisions:
  - "Replaced the 6 combined-assertion tests seeded by 11-05 RED gate with 8 single-assertion tests matching the plan's exact `<action>` block verbatim, so each assertion is independently failable and each method name is independently greppable (Tier-1 oracle granularity)."

patterns-established:
  - "BanksIRTest pattern: 8 single-assertion @Test methods, one-line bodies where possible, no @BeforeEach (ir is field-level)"

requirements-completed: [BANK-IR-STRUCTURE]

duration: 4min
completed: 2026-05-20
---

# Phase 11 Plan 06: ir-test Summary

**8 IR-shape JUnit5 tests locking the Banks.kt DSL substrate contract (3 scenes, start=title, 1 zone, 1 u8 saveFlag, 1 SaveSystem) at the JVM tier with deterministic single-shot `banks.build()` IR.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-05-20T05:25:30Z
- **Completed:** 2026-05-20T05:27:03Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- BanksIRTest.kt now contains exactly 8 `@Test fun` methods, each matching the plan's named test list verbatim.
- All 8 tests GREEN; test report at `gbkt-examples/banks/build/test-results/test/TEST-io.github.gbkt.examples.banks.BanksIRTest.xml` reports `tests=8 failures=0 errors=0`.
- Each plan-required name is independently greppable: `has 3 scenes`, `start scene is title`, `scenes include title play pause`, `has 1 variable`, `saveFlag is U8`, `has zone definitions`, `has play_zone zone`, `has save system`.

## Task Commits

1. **Task 1: Add 8 IR-shape @Test methods to BanksIRTest** — `b0d4ba93` (test)

## Files Created/Modified

- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt` — expanded from 6 combined-assertion tests (11-05 RED seed) to 8 single-assertion tests matching plan's verbatim `<action>` block (21 insertions, 41 deletions per `git diff --stat`).

## Decisions Made

- **Granularity:** Replaced 11-05's 6 combined-assertion tests (e.g., `has 3 scenes title play pause` mixing count + set-membership) with the plan's 8 single-assertion tests. Rationale: the plan's acceptance criteria require each named method to be greppable and the test count to be exactly 8. Coverage is equivalent (every assertion in the 6 is retained as a dedicated test in the 8) and the new shape better matches the analog DungeonIRTest.kt pattern referenced in 11-PATTERNS.md.

## Deviations from Plan

None - plan executed exactly as written.

The plan's `<action>` block specified the verbatim 8 tests and `<acceptance_criteria>` required exactly 8 occurrences of `@Test fun ` plus each named method. The execution followed both literally.

## Issues Encountered

None.

## Verification

- `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksIRTest" --quiet` → exit 0
- Test report: `tests="8" skipped="0" failures="0" errors="0"`
- `grep -c '@Test fun' gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt` → 8
- All 8 expected method names present (verified via `grep "@Test fun"` listing).

## User Setup Required

None - JVM-tier test, no external dependencies, no ROM required.

## Next Phase Readiness

- Tier-1 IR oracle is locked. Any future DSL/IR refactor that drops a scene, drops the zone, drops the SaveSystem, or changes saveFlag's type will break BanksIRTest deterministically.
- Plan 11-07 onward can extend tests at codegen/build-tier without re-validating IR shape.

## Self-Check: PASSED

- FOUND: `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt`
- FOUND: commit `b0d4ba93` (test(11-06): expand BanksIRTest to 8 IR-shape tests)

---
*Phase: 11-port-banks-gbdk-example-to-gbkt*
*Completed: 2026-05-20*
