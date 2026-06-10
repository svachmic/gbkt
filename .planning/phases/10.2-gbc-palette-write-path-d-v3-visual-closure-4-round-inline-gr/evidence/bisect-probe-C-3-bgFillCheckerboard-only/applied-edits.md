# Sub-Probe C-3 Applied Edits — bgFillCheckerboard hoist ONLY

**Plan:** 10.2-06c  
**Date:** 2026-05-19  
**Probe:** C-3 (Emission #3 of Plan 22 — bgFillCheckerboard hoist only)

## Baseline State

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline, cfe41ad7 deviation from cbe81d29 per Plans 03/04/05)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **scratch/bisect HEAD before C-3:** 2767fab7 (probe-A — reset from C-2 sub-probe)

## C-3 Edit Applied

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`

**Change:** Added `hoistedBgFillCheckerboardStatements` val block after `hoistedStartPaletteStatements`,
and the `addAll(hoistedBgFillCheckerboardStatements)` in mainBody after `addAll(allSpriteDataLoads)`.

```diff
         val hoistedStartPaletteStatements: List<CStatement> = ...

+        //
+        // RawOp text-match (intentional, scoped): we identify the bgFillCheckerboard
+        // emission by checking for BOTH `fill_bkg_rect` AND `set_bkg_data` substrings in
+        // the same RawOp.
+        val hoistedBgFillCheckerboardStatements: List<CStatement> =
+            startScene
+                ?.enterOps
+                ?.filterIsInstance<RawOp>()
+                ?.filter { it.code.contains("fill_bkg_rect") && it.code.contains("set_bkg_data") }
+                ?.map { it.accept(ScriptOpVisitor) }
+                ?: emptyList()

         val mainBody = buildList {
             ...
             addAll(allSpriteDataLoads)
+            addAll(hoistedBgFillCheckerboardStatements)
             // Bind OAM slots to tiles and set initial positions
             addAll(spriteOAMInits)
```

**Scope:** bgFillCheckerboard hoist ONLY. No C-1 constant. No C-2 set_bkg_palette call.

**C-3 commit in scratch/bisect:** 859fee04

## Test Scaffolding Added

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt`
- Added `writeMemory(address, value)` method (wraps `session.getMemory().writeByte()`)
- Required for palette-dump-protocol.md BCPS/OCPS index pre-write loop

**File:** `gbkt-examples/metasprites/src/test/kotlin/.../ProbeC3PaletteDumpTest.kt`
- New test for V-5 evidence capture: 8 A-presses → screenshot + BCPD + OCPD 64-byte dumps
- Evidence written to MAIN checkout's `.planning/phases/.../evidence/bisect-probe-C-3-bgFillCheckerboard-only/`

Test scaffolding commit: bae341f7 (dropped from scratch/bisect after evidence capture)

## Verdict

**CYAN PRESERVED — C-3 (bgFillCheckerboard hoist alone) does NOT break cyan. SURPRISE FINDING.**

PNG at capture frame ~49: 1452 bytes, 5 distinct colors (identical to Probe A 1452 bytes / 5 colors).
BCPD slot 0 = 0x7FFF, OCPD slot 2 = 0x7FFF — both identical to Probe A.

## BISECT CHAIN CONCLUSION

All 3 sub-probes CLEARED their individual emissions. The regression is an INTERACTION effect:
| Sub-probe | Emissions tested | Cyan result | Status |
|-----------|-----------------|-------------|--------|
| C-1 | #1: constant only | YES | CLEARED |
| C-2 | #1+#2: constant + set_bkg_palette | YES | CLEARED |
| C-3 | #3: bgFillCheckerboard hoist only | YES | CLEARED |
| Probe B | #1+#2+#3 all combined | NO | REGRESSION |

**The regression requires at least two emissions acting together.** Plan 07 must investigate
the interaction (most likely C-2 + C-3: set_bkg_palette + bgFillCheckerboard hoist together).

## Reset After Evidence Capture

After copying evidence files to the main checkout, scratch/bisect was reset:
- Before reset: HEAD at bae341f7 (test scaffolding on top of C-3)
- After reset: HEAD at 2767fab7 (probe-A, 2 commits dropped: bae341f7 + 859fee04)

## Drift Check (D-20)

See below.

## Drift Check Result

`git reflog --all | head -50` confirmed:
- `859fee04` appears ONLY as `worktrees/bisect/HEAD@{X}` (C-3 emission commit)
- `bae341f7` appears ONLY as `worktrees/bisect/HEAD@{Y}` (test scaffolding commit)
- Neither hash appears in `refs/heads/feat/d_and_d_gaps` or `HEAD`

**DRIFT CHECK: CLEAN** — probe C-3 commit and test scaffolding are isolated to scratch/bisect.
No leakage to feat/d_and_d_gaps or any other branch.
