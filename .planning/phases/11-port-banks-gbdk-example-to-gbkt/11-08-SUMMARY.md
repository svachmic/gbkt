---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 08
subsystem: testing
tags: [banks, codegen-invariants, jvm-tier, mbc5_ram_battery, save-data-builder, sram]

# Dependency graph
requires:
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: "Plan 11-05 (Banks.kt with cartridge=\"MBC5_RAM_BATTERY\"); Plan 11-06 (BanksEmissionTest scaffold + extractFunctionBody + EVIDENCE_DIR); Plan 11-07 (INV-1 GREEN + INV-2 RED-sentinel)"
provides:
  - "INV-3 JVM-tier invariant: gbkt-build.properties carries mbcType=0x1B (locks cartridge -> mbcType propagation)"
  - "INV-4 JVM-tier invariant: save_game_saves emits ENABLE_RAM; -> sram[ -> DISABLE_RAM; in order (locks SaveDataBuilder SRAM write contract)"
  - "Architectural finding: gbkt-build.properties is a Gradle-layer sidecar (GenerateCTask), NOT a GBDKPipelineV2.files map entry; INV-3 reads from the on-disk path the upstream :generateC task writes"
affects:
  - 11-09-first-buildrom-bug-naming (already routed to fix INV-2 sentinel; may also need to scope trigger_saves)
  - 11-10-trigger-saves-fix (Plan 11-10 will RED->GREEN this test by appending `mainC.contains("trigger_saves")` to INV-4)
  - 11-13-tier3-uat-cartridge-anchor (anchor 3 ROM byte at 0x0147; INV-3 is the upstream lock)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JVM-tier brace-walk + per-function grep invariants (continued from Phase 07.4 / 09.2 / Plan 11-07)"
    - "On-disk Gradle-sidecar reading from build/gbkt/generated/ for files NOT in the in-memory GBDKPipelineV2.files map (gbkt-build.properties)"
    - "Wave-scoped assertion deferral: INV-4 deliberately stops short of asserting trigger_saves (Plan 11-10 territory) to keep Wave-2 oracle work decoupled from Wave-4 bug-fix work"

key-files:
  created:
    - ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv3-build-properties.txt"
    - ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv4-save-game-saves.txt"
  modified:
    - "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt"

key-decisions:
  - "Rule 1 fix: read gbkt-build.properties from the on-disk path build/gbkt/generated/gbkt-build.properties (written by GenerateCTask.writeBuildMetadata) instead of the plan-specified output.files[\"gbkt-build.properties\"] which is always null"
  - "Honoured the Plan 11-08 Task 2 directive to NOT assert mainC.contains(\"trigger_saves\") in INV-4; that assertion is added by Plan 11-10's RED->GREEN cycle (Wave-4)"
  - "Preserved INV-2 RED-by-design sentinel from Plan 11-07 unchanged; orchestrator success criteria scope to INV-3/INV-4 only, not the full-suite green"

patterns-established:
  - "Pattern: When a plan's task spec references a pipeline-output key for a file actually emitted at the Gradle layer, read from the canonical on-disk path the upstream Gradle task produced (and document the architectural split in a comment block)"
  - "Pattern: Evidence-before-assert (write artifact BEFORE assertions fire) was applied to both INV-3 (props content) and INV-4 (save_game_saves body)"

requirements-completed:
  - BANK-INV-3
  - BANK-INV-4

# Metrics
duration: 7min
completed: 2026-05-20
---

# Phase 11 Plan 08: emission-inv3-inv4 Summary

**INV-3 (gbkt-build.properties carries mbcType=0x1B) and INV-4 (save_game_saves emits ENABLE_RAM -> sram[ -> DISABLE_RAM in order) added as @Test methods in BanksEmissionTest.kt — both GREEN; INV-2 from Plan 11-07 remains a RED-by-design sentinel routed to Plan 11-09.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-05-20T05:35:56Z
- **Completed:** 2026-05-20T05:42:52Z
- **Tasks:** 2 (both GREEN)
- **Files modified:** 1 (BanksEmissionTest.kt)
- **Files created:** 2 (evidence)

## Accomplishments

- INV-3 GREEN — locks the cartridge -> mbcType propagation contract: `cartridge = "MBC5_RAM_BATTERY"` in Banks.kt resolves via CARTRIDGE_MBC_MAP to `mbcType=0x1B` in `gbkt-build.properties`. Evidence-before-assert pattern captures the full properties content at `evidence/tier1-shape/inv3-build-properties.txt`.
- INV-4 GREEN — locks the SaveDataBuilder SRAM write contract: brace-walked `save_game_saves` body in `main.c` contains `ENABLE_RAM;` -> `sram[` -> `DISABLE_RAM;` substrings AND their declaration order is enforced (ordering check). Evidence captured at `evidence/tier1-shape/inv4-save-game-saves.txt`.
- BanksEmissionTest now has exactly 4 `@Test` methods (INV-1..4) matching CONTEXT D-12, with INV-1/3/4 GREEN and INV-2 RED-by-design sentinel preserved from Plan 11-07.
- Architectural finding documented in INV-3's comment block: `gbkt-build.properties` is a Gradle-layer sidecar (GenerateCTask.writeBuildMetadata, gbkt-gradle-plugin), NOT a GBDKPipelineV2.files map entry. Future plans reading this file in JVM tests should read from the on-disk output dir.

## Task Commits

Each task was committed atomically (TDD: implementation arrived in a single edit but committed as 2 separate atomic commits — Task 1 alone, then Task 2 alone):

1. **Task 1: Add INV-3 (gbkt-build.properties mbcType propagation)** — `c9c3e732` (test)
2. **Task 2: Add INV-4 (save_game_saves ENABLE_RAM + sram write + DISABLE_RAM)** — `e74b0b18` (test)

## Files Created/Modified

- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` — appended INV-3 and INV-4 `@Test` methods after the existing INV-1 (GREEN) and INV-2 (RED sentinel) tests from Plan 11-07. Existing helpers (`extractFunctionBody`, `EVIDENCE_DIR` companion) re-used unchanged.
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv3-build-properties.txt` — full contents of `build/gbkt/generated/gbkt-build.properties` showing `cartridge=MBC5_RAM_BATTERY`, `gbcMode=DISABLED`, `mbcType=0x1B`.
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv4-save-game-saves.txt` — brace-walked `save_game_saves` body from `main.c` showing the ENABLE_RAM -> sram[0/1] writes -> DISABLE_RAM sequence.

## Decisions Made

- **D-11.08.1 (Rule 1 fix):** Plan 11-08 Task 1's `<action>` block specified `output.files["gbkt-build.properties"]` — but `GBDKPipelineV2.generate(...)` only populates its `files` map with C/header/`game_metadata.json` artifacts. `gbkt-build.properties` is written by `GenerateCTask.writeBuildMetadata()` in the Gradle layer (`gbkt-gradle-plugin/.../GenerateCTask.kt:488-545`), directly to the output dir. INV-3 was implemented to read from `build/gbkt/generated/gbkt-build.properties` (the canonical on-disk path), with the architectural split documented in an in-test comment block. This preserves the plan's intent (lock the cartridge -> mbcType contract) while reading the file from where it actually lives. The Gradle test task DAG ensures `:generateC` runs before `:test`, so the file always exists at assertion time.
- **D-11.08.2 (Honour wave decoupling):** Plan 11-08 Task 2 explicitly directs NOT to assert `mainC.contains("trigger_saves")` in INV-4 — that assertion is added by Plan 11-10's RED->GREEN cycle. INV-4 as authored only locks the SaveDataBuilder SRAM write contract (which IS unconditionally emitted by `GBDKSystemVisitor.visitSaveSystem`) and stops short of the `triggerSystem("saves")` -> `trigger_saves` trampoline naming gap.
- **D-11.08.3 (Plan 11-07 sentinel preserved):** INV-2 RED-by-design status from Plan 11-07 (documented in `evidence/inv2-failure.txt` and routed to Plan 11-09) is preserved unchanged. The orchestrator's success criteria scope to INV-3/INV-4 only, not the full-suite green — full-suite RED on INV-2 is expected and is the routing material for the Wave-3 bug-naming gate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] INV-3 read source corrected from in-memory pipeline output to on-disk Gradle sidecar**

- **Found during:** Task 1 (Add INV-3)
- **Issue:** Plan 11-08 Task 1 specified `output.files["gbkt-build.properties"]` which is always null. `GBDKPipelineV2.generate(...).files` is a Map<String, String> populated only with C / header / `game_metadata.json` artifacts (`GBDKPipelineV2.kt:175-188`). `gbkt-build.properties` is written by `GenerateCTask.writeBuildMetadata()` in the Gradle plugin layer (`GenerateCTask.kt:488-545`), directly to the output directory — it is NOT exposed as a pipeline-output map entry. The RED captured at `BanksEmissionTest.kt:214` was `java.lang.IllegalStateException: gbkt-build.properties not generated`, surfacing the architectural mismatch between the plan-author's mental model and the real codegen pipeline.
- **Fix:** Re-route INV-3 to read `propsFile = File(System.getProperty("user.dir"), "build/gbkt/generated/gbkt-build.properties")`. The Gradle test task DAG (`:gbkt-examples:banks:test` depends on `:generateC`) guarantees the file exists before the test runs. Retained the `pipeline.generate(banks.build())` call as a codegen warmup to keep the test fast-failing if the pipeline itself regresses. Documented the architectural split in a 22-line comment block above the `@Test` method so future readers understand the on-disk read is intentional.
- **Files modified:** `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt`
- **Verification:** `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-3*" --rerun-tasks` exits 0; evidence file contains `mbcType=0x1B` at line 5.
- **Committed in:** `c9c3e732` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 bug in the plan's task spec)
**Impact on plan:** The fix preserves the plan's intent (lock cartridge -> mbcType propagation) while routing the read to the layer that actually writes the file. No scope creep — the test still verifies exactly what the plan asked for, just via the correct file source. The architectural finding is documented in-test for future-plan benefit.

## Issues Encountered

- **INV-2 still RED at full-suite level.** The orchestrator's `--tests "...BanksEmissionTest" --rerun-tasks` invocation reports 4 tests run, 1 failed (INV-2 at `BanksEmissionTest.kt:167`). This is the Plan 11-07 RED-by-design sentinel (see `evidence/inv2-failure.txt`) — NOT a regression introduced by Plan 11-08. The plan's Task 2 acceptance criterion ("All 4 invariant tests GREEN in the full suite ... exits 0") conflicts with the upstream Plan 11-07 routing decision; the user's objective and the orchestrator success criteria explicitly scope to INV-3 + INV-4 GREEN only, which is satisfied.

## Plan 11-08 vs. Self-Check

- [x] BanksEmissionTest.kt contains INV-3 (`fun \`INV-3 gbkt-build_properties carries mbcType 0x1B\``)
- [x] BanksEmissionTest.kt contains INV-4 (`fun \`INV-4 save_game_saves in main_c emits ENABLE_RAM and DISABLE_RAM\``)
- [x] INV-1 and INV-2 from Plan 11-07 still present (lines 98 + 156 in current BanksEmissionTest.kt)
- [x] INV-3 is GREEN (`--tests "...INV-3*" --rerun-tasks` exits 0)
- [x] INV-4 is GREEN for ENABLE_RAM/sram[/DISABLE_RAM portion (`--tests "...INV-4*" --rerun-tasks` exits 0)
- [x] INV-4 does NOT assert `mainC.contains("trigger_saves")` (deferred to Plan 11-10)
- [x] Evidence files exist: `inv3-build-properties.txt`, `inv4-save-game-saves.txt`
- [x] Both task commits land on the worktree-agent branch

## Next Plan Readiness

- **Plan 11-09 (first-buildrom-bug-naming, Wave-3):** Ready. INV-2 sentinel routing material in `evidence/inv2-failure.txt` is preserved; INV-3/INV-4 GREEN provide the JVM-tier oracle for the first buildRom smoke test. Plan 11-09 may need to expand its bug-fix scope to cover BOTH `trigger_saves` AND `_bkg_tiles_load_banked` per `evidence/inv2-failure.txt` lines 35-46.
- **Plan 11-10 (trigger-saves-fix, Wave-4):** INV-4 is positioned for the RED->GREEN cycle. Plan 11-10 will append the single line `assertTrue(mainC.contains("trigger_saves"), "trigger_saves stub must be emitted post-fix")` to INV-4, observe RED, then fix GBDKSystemVisitor.visitSaveSystem to emit the trampoline.
- **Plan 11-13 (tier3-uat-cartridge-anchor):** INV-3 is the upstream lock; the actual ROM byte verification at 0x0147 happens in Plan 11-13.

## Self-Check: PASSED

- Created files exist:
  - `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` (modified, 4 @Test methods)
  - `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv3-build-properties.txt` (5 lines, contains `mbcType=0x1B`)
  - `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv4-save-game-saves.txt` (7 lines, contains `ENABLE_RAM;`, `sram[`, `DISABLE_RAM;`)
- Commits exist on worktree-agent-a9f1f6704bf8d34cd branch:
  - `c9c3e732` test(11-08): add INV-3
  - `e74b0b18` test(11-08): add INV-4

---
*Phase: 11-port-banks-gbdk-example-to-gbkt*
*Plan: 08-emission-inv3-inv4*
*Completed: 2026-05-20*
