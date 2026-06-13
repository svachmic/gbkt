---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 23
subsystem: gbkt-backend-gbdk/codegen/emit, gbkt-backend-gbdk/codegen/visitor
tags: [sonar, s3776, extract-method, cemitter, soundvisitor, byte-identity]
dependency_graph:
  requires: ["18-22"]
  provides: ["E-12 resolved", "E-06 resolved", "SONAR-01/02 EMITTING complete"]
  affects: ["gbkt-backend-gbdk/codegen/emit/CEmitter.kt", "gbkt-backend-gbdk/codegen/visitor/SoundVisitor.kt"]
tech_stack:
  added: []
  patterns: ["extract-method with per-section helpers (file-structure emitter)", "top-level private functions (avoid TooManyFunctions)", "shared envelopeValue helper (DRY across PULSE1/PULSE2/NOISE)"]
key_files:
  created: []
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SoundVisitor.kt"
decisions:
  - "E-12 CEmitter.emit: EXTRACT-METHOD (not NOSONAR) — sequential file-structure emitter with clear sections; extraction improves readability and is not a flat sealed dispatch"
  - "E-06 SoundVisitor.buildNRxxRegisterWrites: EXTRACT-METHOD (not NOSONAR) — per-channel dispatch with extractable register-write bodies; helpers are top-level private functions to avoid TooManyFunctions"
  - "envelopeValue() extracted as shared helper used by PULSE1, PULSE2, NOISE (DRY bonus)"
  - "Milestone NOSONAR budget: 0 used out of ≤5 total (all 29 EMITTING findings resolved via extract-method)"
metrics:
  duration: "6 min"
  completed_date: "2026-06-13"
  tasks: 2
  files_modified: 2
---

# Phase 18 Plan 23: NOSONAR-candidate Findings (E-12, E-06) Summary

Resolved the two NOSONAR-candidate S3776 findings via extract-method refactoring. Both passed the D-06 byte-identity ROM sweeps. The milestone NOSONAR budget remains at 0 used.

## Tasks Completed

### Task 1: Extract per-section helpers from CEmitter.emit (E-12, cc=29)

**Disposition: EXTRACT-METHOD** (D-05 default path)

Inspection revealed `emit` is NOT a flat sealed-`CStatement` dispatch — it is a sequential file-structure emitter with eight independent sections (header, includes, defines, typedefs, variables, raw sections, functions, guard close). Extraction genuinely reduces reader burden by turning the 89-line function into a 10-line dispatcher.

**Helpers extracted (private methods on CEmitter object):**
- `computeGuardName(file): String?` — include-guard symbol or null
- `emitFilePrologue(ltb, file, guardName)` — header + guard open + bank pragma
- `emitIncludeBlock(ltb, file)` — `#include` directives
- `emitDefineBlock(ltb, file)` — `#define` macros
- `emitTypedefBlock(ltb, file)` — `typedef` declarations
- `emitVariableBlock(ltb, file)` — file-level variable declarations
- `emitRawSectionBlock(ltb, file)` — raw file-scope sections
- `emitFunctionBlock(ltb, file)` — function definitions with section comments
- `emitFileEpilogue(ltb, guardName)` — guard close

**Dead code removed:** `withIndex()` + empty `if (index < file.functions.size - 1)` block (no behavior change).

**Byte-identity sweep (Task 1):** 7/7 PASS — pong PASS*, 6/6 non-pong byte-identical on generated C and ROM.

---

### Task 2: Extract per-channel helpers from SoundVisitor.buildNRxxRegisterWrites (E-06, cc=43)

**Disposition: EXTRACT-METHOD** (D-05 default path)

Inspection confirmed per-channel NRxx register dispatch with extractable bodies. Each channel branch is independent and substantial (5-20 statements). Top-level private functions were used (outside the class) to avoid triggering detekt `TooManyFunctions` on `SoundVisitor`.

**Helpers extracted (top-level private functions in SoundVisitor.kt):**
- `nrWrite(register, value): CExprStatement` — promoted from local function; emits `register = 0xVVu` hex-literal assignment
- `envelopeValue(env: EnvelopeConfig?): Int` — NR12/NR22/NR42 envelope byte calculation, shared by PULSE1, PULSE2, NOISE (DRY bonus)
- `buildPulse1Writes(regs: SoundRegisters): List<CStatement>` — CH1: NR10-NR14 (sweep + square + envelope)
- `buildPulse2Writes(regs: SoundRegisters): List<CStatement>` — CH2: NR21-NR24 (square + envelope, no sweep)
- `buildWaveWrites(regs: SoundRegisters): List<CStatement>` — CH3: NR30-NR34 + optional wave RAM load
- `buildNoiseWrites(regs: SoundRegisters): List<CStatement>` — CH4: NR41-NR44 (noise params)

**`buildNRxxRegisterWrites` after refactoring:** 4-arm `when` dispatcher (~8 lines).

**New imports:** `EnvelopeConfig`, `SoundRegisters` (both in `io.github.gbkt.core.ir`).

**Byte-identity sweep (Task 2):** 7/7 PASS — pong PASS*, 6/6 non-pong byte-identical on generated C and ROM.

---

## NOSONAR Budget Tally

| Finding | CC | Disposition | NOSONAR used? |
|---------|----|-------------|---------------|
| E-12 CEmitter.emit | 29 | EXTRACT-METHOD | No |
| E-06 SoundVisitor.buildNRxxRegisterWrites | 43 | EXTRACT-METHOD | No |
| **Milestone total** | | | **0 / ≤5 budget** |

All 29 EMITTING S3776 findings across Phase 18 were resolved via extract-method. Zero NOSONAR suppressions used this milestone.

## Verification

| Check | Result |
|-------|--------|
| spotlessApply | PASS (both tasks) |
| detekt | PASS (both tasks) |
| :gbkt-backend-gbdk:test | PASS (both tasks) |
| C byte-identity sweep (Task 1) | 7/7 PASS (pong PASS*) |
| ROM byte-identity sweep (Task 1) | 6/6 PASS (pong PASS*) |
| C byte-identity sweep (Task 2) | 7/7 PASS (pong PASS*) |
| ROM byte-identity sweep (Task 2) | 6/6 PASS (pong PASS*) |
| SonarCloud E-12 cleared | Expected (CC reduced from 29 to ~3) |
| SonarCloud E-06 cleared | Expected (CC reduced from 43 to ~4) |

## Commits

| Task | Hash | Message |
|------|------|---------|
| 1 (E-12) | `fa357006` | `refactor(18-23): extract per-section helpers from CEmitter.emit (E-12, S3776 cc29)` |
| 2 (E-06) | `e9f9923c` | `refactor(18-23): extract per-channel helpers from SoundVisitor.buildNRxxRegisterWrites (E-06)` |

## Deviations from Plan

None — plan executed exactly as written. Both findings resolved via extract-method. No NOSONAR suppressions used. No seed files created (seeds are only created on the NOSONAR path per plan spec).

## Known Stubs

None.

## Self-Check: PASSED

- `fa357006` — confirmed in git log
- `e9f9923c` — confirmed in git log
- `CEmitter.kt` modified — confirmed (30 net insertions from helpers)
- `SoundVisitor.kt` modified — confirmed (155 insertions / 142 deletions from restructuring)
- 7/7 C byte-identity sweeps PASS for both tasks
- 6/6 ROM byte-identity sweeps PASS for both tasks
