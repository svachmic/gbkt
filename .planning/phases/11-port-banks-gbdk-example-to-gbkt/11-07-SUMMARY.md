---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 07
subsystem: gbkt-examples + JVM-tier emission invariants
tags: [example-port, banks, jvm-tier-invariants, banked-keyword, switch-rom, scope-level-grep-gate, red-gate]
requires:
  - 11-01 (BanksEmissionTest scaffold — extractFunctionBody helper + EVIDENCE_DIR)
  - 11-05 (Banks.kt DSL substrate — multi-bank scenes + zone + saves)
provides:
  - INV-1 GREEN: BANKED auto-injection contract locked for play scene functions in bank1.c
  - INV-2 RED gate: SWITCH_ROM-from-HOME wrapper contract authored; surfaces Candidate 2 codegen bug for Plan 11-10
  - Surface for Plan 11-08 INV-3 / INV-4 (skeleton + helpers preserved unchanged)
  - inv2-failure.txt routing artifact for Plan 11-09 named-bug renaming
affects:
  - gbkt-examples/banks (Banks.kt: explicit `exit { hideSprites() }` on play scene)
  - gbkt-examples/banks (BanksEmissionTest.kt: 0 → 2 @Test methods)
  - Plan 11-08 (must append INV-3 / INV-4 additively after INV-2's `}`)
  - Plan 11-09 (must rename Plan 11-10 named-bug scope to cover BOTH `trigger_saves` AND `_bkg_tiles_load_banked`)
  - Plan 11-10 (named-bug-fix scope expands to TWO bugs: Candidate 1 + Candidate 2)
tech-stack:
  added: []
  patterns:
    - "Per-function brace-walk + .contains(\" BANKED\") substring (leading space pins signature, not string-literal)"
    - "Evidence-before-assert (write extracted body BEFORE assertTrue/assertFalse) — reviewable RED artifact"
    - "RED-gate-in-same-phase pattern (mirrors Phase 09 Plan 03 commit a31c3925: INV-2 RED at HEAD, flips GREEN at Plan 11-10)"
key-files:
  created:
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/inv2-failure.txt
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-enter.txt
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-frame.txt
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-exit.txt
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt (empty — captures RED state)
  modified:
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt (83 → 173 lines, +2 @Test methods)
    - gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt (added explicit `exit { hideSprites() }` block on play scene — Rule 3 deviation)
decisions:
  - "INV-1 satisfiable required adding `exit { hideSprites() }` to Banks.kt's play scene — scene codegen omits *_exit functions when no exit block is declared. Without this, play_exit extraction returns empty and the plan's enter/frame/exit triplet is unsatisfiable. The added exit block matches the standard pauseScene example in CLAUDE.md and is semantically correct."
  - "INV-2 RED-at-HEAD is the CORRECT outcome (not a regression): the test correctly locks the post-Plan-11-10 contract; the RED surfaces the named bug. RESEARCH §Open Questions Q1 expected GREEN ('helper is unconditional'); investigation of GBDKPipelineV2.kt:972-980 proves the helper is gated by `hasSportRacing && bankAllocation.values.any { it > 1 }`. Banks.kt has no sport_racing GenericSystem, so the helper is gated off."
  - "Per plan Task 2 acceptance_criteria failure-branch: failure captured to evidence/inv2-failure.txt; Plan 11-09 must rename Plan 11-10's scope to cover BOTH `trigger_saves` (Candidate 1) AND `_bkg_tiles_load_banked` emission gating (Candidate 2 — this)."
  - "Plan 11-08 additivity preserved: INV-1 / INV-2 appended below the `extractFunctionBody` helper; class skeleton, companion EVIDENCE_DIR, and import list left intact (assertFalse import retained for Plan 11-08's negative assertions)."
metrics:
  duration: "~30 minutes"
  completed: "2026-05-20"
  tasks_total: 2
  tasks_completed: 2
  commits:
    - "cfc3e418: test(11-07) add INV-1 BANKED keyword invariant for play scene"
    - "f7cc8b17: test(11-07) add INV-2 SWITCH_ROM wrapper invariant (RED gate for Plan 11-10)"
---

# Phase 11 Plan 07: Emission Invariants INV-1 + INV-2 Summary

Two JVM-tier emission invariants added to BanksEmissionTest. INV-1 GREEN locks the
BANKED auto-injection contract for the multi-bank trampoline target (play scene
enter/frame/exit in bank1.c). INV-2 RED locks the post-fix contract for the
Plan 07.4-30 SWITCH_ROM-from-HOME wrapper and surfaces named codegen bug
Candidate 2 (per RESEARCH §Top-2 Likely Codegen Bug Candidates) earlier than
expected — the helper is gated by a `hasSportRacing` guard that Banks.kt's
zone-without-genre substrate doesn't satisfy.

## What Was Built

### 1. `BanksEmissionTest.kt` — INV-1 (GREEN)

The `INV-1 play scene functions carry BANKED keyword in bank1` @Test extracts
play_enter, play_frame, and play_exit from `bank1.c` via the brace-walk helper
(`extractFunctionBody`) and asserts each contains the literal substring
` BANKED` (leading space — pins the BANKED keyword in the signature line,
distinguishing it from any occurrence inside a string literal or comment).

Evidence-before-assert: all three function bodies are written to
`evidence/tier1-shape/inv1-play-{enter,frame,exit}.txt` BEFORE the
`assertTrue` calls fire. All three files contain ` BANKED` (verified via
`grep -c "BANKED"` returning 1 for each).

### 2. `BanksEmissionTest.kt` — INV-2 (RED gate)

The `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence`
@Test extracts `_bkg_tiles_load_banked` from `main.c` and asserts the body
contains `SWITCH_ROM(`, `set_bkg_tiles(`, and `SWITCH_ROM(1);` (the
post-emission bank-restore). The brace-walk extraction returned **empty** at
HEAD — proving the helper is not emitted for Banks.kt's substrate.

### 3. `Banks.kt` — explicit `exit { hideSprites() }` on play scene

Required to make INV-1's `play_exit` extraction succeed. Without an explicit
exit block, scene codegen does NOT emit a `*_exit` function. The added block
matches the standard scene-lifecycle pattern shown in CLAUDE.md examples.

## How It Was Built

1. **Baseline RED:** Ran `./gradlew :gbkt-examples:banks:test --tests "...BanksEmissionTest"` — Gradle reported "No tests found" (confirming pre-Plan-11-07 RED state: zero @Test methods in the scaffold).
2. **INV-1 author:** Appended the @Test method after the `extractFunctionBody` helper. First run failed: `play_exit must have BANKED keyword in signature` with empty body.
3. **Root-cause analysis:** Inspected `build/gbkt/generated/bank1.c` — only `pause_enter`, `pause_frame`, `play_enter`, `play_frame` were emitted. No `*_exit` functions because no scene declared an `exit { }` block.
4. **Rule 3 deviation:** Added `exit { hideSprites() }` to play scene in Banks.kt. INV-1 then GREEN; all 3 evidence files contain ` BANKED`. Committed: `cfc3e418`.
5. **INV-2 author:** Appended the second @Test method after INV-1's closing brace. First run failed: `_bkg_tiles_load_banked helper must be emitted in main.c for games with zones`. Empty wrapper body.
6. **Root-cause analysis:** Confirmed `main.c` has zero occurrences of `bkg_tiles`, `SWITCH_ROM`, `set_bkg_tiles`, or `zone_load*`. Located the gating logic at `GBDKPipelineV2.kt:972-980`:
   ```kotlin
   val hasSportRacing = gameIR.systems
       .filterIsInstance<GenericSystem>()
       .any { (it.config["type"] as? String) == "sport_racing" }
   val crossBankHelper: List<CFunction> =
       if (hasSportRacing && bankAllocation.values.any { it > 1 }) {
           listOf(buildBkgTilesLoadBankedHelper())
       } else {
           emptyList()
       }
   ```
   Banks.kt has no sport_racing GenericSystem → helper gated off.
7. **RED-gate disposition:** Kept INV-2 as-authored (correctly locks the post-fix contract). Wrote `evidence/inv2-failure.txt` per Task 2 acceptance_criteria failure branch. Committed: `f7cc8b17`.

## Deviations from Plan

### Auto-applied (Rule 3 — blocking issue)

**1. [Rule 3 — Blocking] Added `exit { hideSprites() }` to play scene in Banks.kt**
- **Found during:** Task 1 INV-1 first run
- **Issue:** Plan's `<behavior>` required `play_exit` extraction to be non-empty AND contain BANKED. Without an explicit `exit { }` block in the DSL, scene codegen omits the `*_exit` function entirely.
- **Fix:** Added `exit { hideSprites() }` block to play scene. Matches the standard scene-lifecycle pattern (pauseScene in CLAUDE.md uses `hideSprites()` + `clear()` on exit). Semantically correct — the play scene should hide sprites when leaving.
- **Files modified:** `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt`
- **Commit:** `cfc3e418`
- **Plan files_modified scope:** The plan listed only the test file; modifying Banks.kt is a strict-scope deviation. Justified because the plan-spec contract is unsatisfiable without the change, and the change is benign (matches established DSL idiom).

### Documented RED state (per plan Task 2 fallback)

**2. [Plan-fallback path] INV-2 RED-at-HEAD per Task 2 acceptance_criteria failure branch**
- **Found during:** Task 2 INV-2 first run
- **Issue:** Plan's RESEARCH §Open Questions Q1 expected INV-2 GREEN ("helper is unconditional"). Actual: `_bkg_tiles_load_banked` is gated by `hasSportRacing AND bank>1`; Banks.kt has no sport_racing system.
- **Disposition:** Per plan Task 2 explicit fallback ("If test fails: STOP... Capture failure in `.../inv2-failure.txt` and flag for Plan 11-09 named-bug renaming"). Test stays as-authored — it correctly locks the post-fix contract.
- **Files created:** `evidence/inv2-failure.txt` (full disposition + routing instructions for Plan 11-09)
- **Commit:** `f7cc8b17`

## Test Outcomes

| Test | Status | Evidence |
|------|--------|----------|
| `INV-1 play scene functions carry BANKED keyword in bank1` | **PASSED** | 3 files in `evidence/tier1-shape/` — each contains ` BANKED` |
| `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence` | **FAILED (RED gate)** | `evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt` is empty (helper not emitted); `evidence/inv2-failure.txt` documents the root cause and routing |

Total @Test methods in `BanksEmissionTest.kt`: **2** (target: 2). Plan 11-08 will add INV-3, INV-4 → total 4.

## Routing to Downstream Plans

### Plan 11-08 (emission-inv3-inv4 — Wave 2 sibling)

The class skeleton, companion `EVIDENCE_DIR`, `extractFunctionBody` helper, and import list (including `assertFalse`, kept for Plan 11-08's negative assertions) are all unchanged. Plan 11-08 should:

- Append INV-3 / INV-4 @Test methods AFTER the closing brace of INV-2 (line 173)
- Reuse the same brace-walk pattern (do NOT duplicate the helper)
- Reuse `EVIDENCE_DIR` (do NOT redeclare)
- No merge conflicts expected — Plan 11-07's edits are strictly additive append

### Plan 11-09 (first-buildrom-bug-naming — Wave 3)

**MANDATORY scope update:** Plan 11-10's named-bug scope was previously expected to be SINGLE (Candidate 1: `trigger_saves` missing — HIGH probability per RESEARCH). INV-2 RED proves Candidate 2 (`_bkg_tiles_load_banked` gating) IS ALSO present. Plan 11-09 must:

1. Acknowledge `evidence/inv2-failure.txt` as a Wave 2 pre-finding (not just a Wave 3 buildRom finding)
2. Re-name Plan 11-10's scope to cover BOTH bugs (either as a combined plan or as split 11-10a + 11-10b)
3. Locate fix sites:
   - **Candidate 1 (trigger_saves):** GBDKSystemVisitor.visitSaveSystem (per RESEARCH §Pitfall 4)
   - **Candidate 2 (helper gating):** GBDKPipelineV2.kt:972-980 — relax the `hasSportRacing` guard, OR add a separate gate condition for plain `zone {}` substrates with zone-bank > 1

### Plan 11-10 (named-bug-fix)

After fix: INV-2 should flip GREEN. The `evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt` (currently empty) should re-populate with a body containing `SWITCH_ROM(`, `set_bkg_tiles(`, and `SWITCH_ROM(1);` — locking the Plan 07.4-30 contract for the banks substrate.

## Patterns Established

- **Scope-level grep gate via brace-walk:** `extractFunctionBody(cSource, fn).contains(" TOKEN")` is the locking pattern for per-function emission assertions. File-level grep is FORBIDDEN (per CLAUDE.md §"Scope-level grep gates corollary").
- **Evidence-before-assert:** Write the extracted body to `EVIDENCE_DIR/<test-name>.txt` BEFORE the `assertTrue`/`assertFalse` calls fire. RED tests still produce reviewable artifacts.
- **RED-gate-in-same-phase:** When a JVM-tier emission invariant locks a contract that the codegen doesn't yet satisfy, commit the RED test in the same phase as the planned fix. Mirrors Phase 09 Plan 03 (commit `a31c3925`) → Plan 09 Plan 04 (fix) pattern.

## Threat Flags

None. INV-1 and INV-2 only read pipeline output; no new code paths, no new trust boundaries.

## Self-Check: PASSED

- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` — FOUND (173 lines, 2 @Test methods)
- `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` — FOUND (modified: explicit play exit block)
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-enter.txt` — FOUND (contains ` BANKED`)
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-frame.txt` — FOUND (contains ` BANKED`)
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-exit.txt` — FOUND (contains ` BANKED`)
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt` — FOUND (empty — RED state capture)
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/inv2-failure.txt` — FOUND (full disposition + routing)
- Commit `cfc3e418` — FOUND in `git log --oneline --all`
- Commit `f7cc8b17` — FOUND in `git log --oneline --all`
