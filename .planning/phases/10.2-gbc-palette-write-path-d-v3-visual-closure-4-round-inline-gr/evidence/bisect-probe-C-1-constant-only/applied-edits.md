# Sub-Probe C-1 Applied Edits — _gbkt_default_bg_pal constant declaration ONLY

**Plan:** 10.2-06a  
**Date:** 2026-05-19  
**Probe:** C-1 (Emission #1 of Plan 22 — constant declaration only)

## Baseline State

- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline, cfe41ad7 deviation from cbe81d29 per Plans 03/04/05)
- **Probe A commit (carried forward):** 2767fab7 (Plan 19+20 selective restore)
- **scratch/bisect HEAD before C-1:** 2767fab7 (probe-A — reset from 0d4e4bb4 probe-B)

## C-1 Edit Applied

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`

**Change:** Added `gbktDefaultBgPalRaw` val block (the GBC-gated constant declaration).

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

**Scope:** CONSTANT DECLARATION ONLY. No `set_bkg_palette()` call added. No `bgFillCheckerboard` hoist.

**C-1 commit in scratch/bisect:** 9b04f0b7

## Test Scaffolding Added

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt`
- Added `writeMemory(address, value)` method (wraps `session.getMemory().writeByte()`)
- Required for palette-dump-protocol.md BCPS/OCPS index pre-write loop

**File:** `gbkt-examples/metasprites/src/test/kotlin/.../ProbeC1PaletteDumpTest.kt`
- New test for V-5 evidence capture: 8 A-presses → screenshot + BCPD + OCPD 64-byte dumps
- Evidence written to `.planning/phases/.../evidence/bisect-probe-C-1-constant-only/`

Test scaffolding commit: b5f07f2e (dropped from scratch/bisect after evidence capture)

## Verdict

CYAN PRESERVED — C-1 constant declaration alone does NOT break cyan.

PNG at capture frame 50: 1452 bytes, 5 distinct colors (identical to Probe A 1452 bytes / 5 colors).
BCPD slot 0 = 0x7FFF, OCPD slot 2 = 0x7FFF — both identical to Probe A.

**Emission #1 is CLEARED as the regression site.** Plan 06b should proceed to test Emission #2
(`set_bkg_palette` call) against the same Probe A baseline.

## Reset After Evidence Capture

After copying evidence files to the main checkout, scratch/bisect was reset:
- Before reset: HEAD at b5f07f2e (test scaffolding on top of C-1)
- After reset: HEAD at 2767fab7 (probe-A, 2 commits dropped: b5f07f2e + 9b04f0b7)

## Drift Check (D-20)

Verified via `git reflog --all | head -50` on main checkout.
See next section.

## Drift Check Result

`git reflog --all | head -50` confirmed:
- `9b04f0b7` appears ONLY as `worktrees/bisect/HEAD@{2}` (C-1 commit)
- `b5f07f2e` appears ONLY as `worktrees/bisect/HEAD@{1}` (test scaffolding commit)
- Neither hash appears in `refs/heads/feat/d_and_d_gaps` or `HEAD`
- `feat/d_and_d_gaps@{0}` still points at `f234cee9` (Plan 05 docs)

**DRIFT CHECK: CLEAN** — probe C-1 commit and test scaffolding are isolated to scratch/bisect.
No leakage to feat/d_and_d_gaps or any other branch.
