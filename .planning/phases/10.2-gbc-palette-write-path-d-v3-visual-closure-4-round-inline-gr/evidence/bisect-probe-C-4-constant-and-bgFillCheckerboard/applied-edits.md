# Sub-Probe C-4 Applied Edits — constant declaration + bgFillCheckerboard hoist (no set_bkg_palette)

**Plan:** 10.2-06d  
**Date:** 2026-05-19  
**Probe:** C-4 (C-1 + C-3 combined — constant + bgFillCheckerboard, no C-2 set_bkg_palette)

## Baseline State

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline, cfe41ad7 deviation from cbe81d29 per Plans 03/04/05)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **scratch/bisect HEAD before C-4:** 2767fab7 (probe-A — reset from C-3 sub-probe)

## C-4 Edits Applied

**Pair:** C-1 (constant declaration) + C-3 (bgFillCheckerboard hoist). Does NOT include C-2 (set_bkg_palette).

### Edit 1: C-1 — Constant Declaration

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`

**Change:** Added `gbktDefaultBgPalRaw` val block and restructured `paletteDataRaw` from a simple joinToString into a buildList that conditionally adds the constant.

```diff
+        // Plan 10.1-22 (DEF-10.1-13-C / 4TH LAYER): for GBC-targeted games, additionally emit
+        // `_gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000}` — the DMG-equivalent
+        // BG palette (white, lt-gray, dk-gray, black).
+        val gbktDefaultBgPalRaw =
+            if (gameIR.config.gbcTarget != GbcTarget.DMG) {
+                "const palette_color_t _gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000};"
+            } else {
+                null
+            }

         val paletteDataRaw =
-            gameIR.palettes
-                .joinToString("\n") { palette ->
-                    "const palette_color_t ${palette.name}_pal[4] = {${palette.toCArrayLiteral()}};"
+            buildList {
+                gameIR.palettes.forEach { palette ->
+                    add("const palette_color_t ${palette.name}_pal[4] = {${palette.toCArrayLiteral()}};")
                 }
-                .takeIf { gameIR.palettes.isNotEmpty() }
+                if (gbktDefaultBgPalRaw != null) add(gbktDefaultBgPalRaw)
+            }
+                .joinToString("\n")
+                .takeIf { it.isNotEmpty() }
```

### Edit 2: C-3 — bgFillCheckerboard hoist

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

**Scope:** Constant declaration (C-1) + bgFillCheckerboard hoist (C-3). NO C-2 `set_bkg_palette` call added.

**C-4 commit in scratch/bisect:** a7aacaa2

## Test Scaffolding Added

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt`
- Added `writeMemory(address, value)` method (wraps `session.getMemory().writeByte()`)
- Required for palette-dump-protocol.md BCPS/OCPS index pre-write loop

**File:** `gbkt-examples/metasprites/src/test/kotlin/.../ProbeC4PaletteDumpTest.kt`
- New test for V-5 evidence capture: 8 A-presses → screenshot + BCPD + OCPD 64-byte dumps
- Evidence written to MAIN checkout's `.planning/phases/.../evidence/bisect-probe-C-4-constant-and-bgFillCheckerboard/`

Test scaffolding commit: 636c9ddf (dropped from scratch/bisect after evidence capture)

## cfe41ad7 Anchor Deviation Note

The base anchor `cfe41ad7` is a deviation from the planned `cbe81d29` spec (see Plans 03/04/05
for the history). This is the buildable baseline used across all sub-probes (C-1/C-2/C-3/C-4)
and is consistent with the prior sub-probes' anchor.

## Verdict

**CYAN PRESERVED — C-4 (constant + bgFillCheckerboard) does NOT break cyan. HYPOTHESIS CONFIRMED.**

PNG at capture frame rot=8: 1452 bytes, 5 distinct colors (identical to Probe A 1452 bytes / 5 colors).
BCPD slot 0 = 0x7FFF (cgb_compatibility() default, NOT from the constant), OCPD slot 2 = 0x7FFF.

The minimal breaking pair is **set_bkg_palette + bgFillCheckerboard** (Emissions #2 + #3).

## Reset After Evidence Capture

After copying evidence files to the main checkout, scratch/bisect was reset:
- Before reset: HEAD at 636c9ddf (test scaffolding on top of C-4 emission commit a7aacaa2)
- After reset: HEAD at 2767fab7 (probe-A, 2 commits dropped: 636c9ddf + a7aacaa2)

## Drift Check (D-20)

See below.

## Drift Check Result

`git reflog --all | head -60` confirmed:
- `a7aacaa2` appears ONLY as `worktrees/bisect/HEAD@{2}` (C-4 emission commit)
- `636c9ddf` appears ONLY as `worktrees/bisect/HEAD@{1}` (test scaffolding commit)
- Neither hash appears in `refs/heads/feat/d_and_d_gaps` or `HEAD`
- `feat/d_and_d_gaps@{0}` still points at `949ecb04` (Plan 06d insert commit from orchestrator)

**DRIFT CHECK: CLEAN** — probe C-4 commit and test scaffolding are isolated to scratch/bisect.
No leakage to feat/d_and_d_gaps or any other branch.
