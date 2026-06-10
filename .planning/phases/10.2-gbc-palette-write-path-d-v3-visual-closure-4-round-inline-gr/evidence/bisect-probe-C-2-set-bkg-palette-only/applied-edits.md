# Sub-Probe C-2 Applied Edits — constant + set_bkg_palette call

**Plan:** 10.2-06b  
**Date:** 2026-05-19  
**Probe:** C-2 (Emissions #1 and #2 of Plan 22 — constant declaration + set_bkg_palette call)

## Baseline State

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline, cfe41ad7 deviation from cbe81d29 per Plans 03/04/05)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **scratch/bisect HEAD before C-2:** 2767fab7 (probe-A — reset from C-1 sub-probe)

## C-2 Edits Applied

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`

### Edit 1: C-1 constant declaration (prerequisite for C-2)

Added `gbktDefaultBgPalRaw` val block before `paletteDataRaw`, plus restructured `paletteDataRaw`
to include the default BG pal constant:

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

### Edit 2: C-2 hoistedDefaultBgPaletteStatements val block (after hoistedStartPaletteStatements)

```diff
+        // Plan 10.1-22 (DEF-10.1-13-C / 4TH LAYER): explicit `set_bkg_palette(0u, 1u, ...)`
+        // call statement, gated on GBC target.
+        val hoistedDefaultBgPaletteStatements: List<CStatement> =
+            if (gameIR.config.gbcTarget != GbcTarget.DMG) {
+                listOf(CRawCode("set_bkg_palette(0u, 1u, _gbkt_default_bg_pal);"))
+            } else {
+                emptyList()
+            }
```

### Edit 3: addAll(hoistedDefaultBgPaletteStatements) in mainBody (after addAll(hoistedStartPaletteStatements))

```diff
             addAll(hoistedStartPaletteStatements)
+            // Plan 10.1-22 (DEF-10.1-13-C / 4TH LAYER): explicit BG palette slot 0 write
+            // via BCPS/BCPD — gbkt-emitted equivalent of what cgb_compatibility intends to
+            // do on real hardware.
+            addAll(hoistedDefaultBgPaletteStatements)
```

**Scope:** C-1 constant + C-2 set_bkg_palette call. Does NOT add bgFillCheckerboard hoist (C-3).

**C-2 commit in scratch/bisect:** 85de90af  
**Test scaffolding commit in scratch/bisect:** 55217d09 (amend of a6cd30a6)

## Test Scaffolding Added

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt`
- Added `writeMemory(address, value)` method (wraps `session.getMemory().writeByte()`)
- Required for palette-dump-protocol.md BCPS/OCPS index pre-write loop

**File:** `gbkt-examples/metasprites/src/test/kotlin/.../ProbeC2PaletteDumpTest.kt`
- New test for V-5 evidence capture: 8 A-presses → screenshot + BCPD + OCPD 64-byte dumps
- Evidence written to MAIN checkout's `.planning/phases/.../evidence/bisect-probe-C-2-set-bkg-palette-only/`

Both test scaffolding files dropped from scratch/bisect after evidence capture (reset to probe-A).

## Verdict

**CYAN PRESERVED** — C-2 (constant + set_bkg_palette) does NOT break cyan.

- PNG: 1452 bytes, 5 distinct colors (identical to Probe A 1452 bytes / 5 colors)
- BCPD slot 0 = [0x7FFF, 0x56B5, 0x294A, 0x0000] (same as Probe A — confirms the value was already there from Probe A's `cgb_compatibility()`)
- OCPD slot 2 = [0x7FFF, 0x7FEA, 0x56A0, 0x2940] (cyan_pal fully present)
- All 8 signals SAME as Probe A

**Emissions #1 and #2 are CLEARED as regression sites.** Plan 06c should proceed to test Emission #3 (bgFillCheckerboard hoist) against the same Probe A baseline.

## Reset After Evidence Capture

After copying evidence files to the main checkout, scratch/bisect was reset:
- Before reset: HEAD at 55217d09 (test scaffolding on top of C-2)
- After reset: HEAD at 2767fab7 (probe-A, 2 commits dropped: 55217d09 + 85de90af)

## Drift Check (D-20)

`git reflog --all | head -50` confirmed on main checkout:
- `85de90af` appears ONLY as `worktrees/bisect/HEAD@{3}` (C-2 emission commit)
- `55217d09` appears ONLY as `worktrees/bisect/HEAD@{1}` (test scaffolding commit)
- `a6cd30a6` appears ONLY as `worktrees/bisect/HEAD@{2}` (test scaffolding commit, pre-amend)
- Neither C-2 hash appears in `refs/heads/feat/d_and_d_gaps` or `HEAD`
- `feat/d_and_d_gaps@{0}` still points at `fc7f91a8` (Plan 06a docs commit)

**DRIFT CHECK: CLEAN** — probe C-2 commit and test scaffolding are isolated to scratch/bisect.
No leakage to feat/d_and_d_gaps or any other branch.
