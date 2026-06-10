# D-V3 Visual Finding v3 — 5th-Layer Root Cause for DEF-10.1-13-C

**Phase:** 10.2-gbc-palette-write-path-d-v3-visual-closure-4-round-inline-gr
**Plan:** 10.2-07 (Wave 4 — diagnostic synthesizing bisect chain from Plans 03–06d)
**Defect:** DEF-10.1-13-C (GBC screenshot renders grayscale sprite / no cyan, after all
            previous 4-layer fixes landed GREEN at JVM tier)
**Date:** 2026-05-19
**Author:** plan-10.2-07 executor

---

## Scope-Shift Notice

This phase is titled "gbc-palette-write-path" but the bisect chain (Plans 03–06d) revealed
that the actual regression is NOT in the palette write path. OCPD slot 2 (cyan_pal) and
BCPD slot 0 (_gbkt_default_bg_pal) both contain correct values in every probe including
Probe B (the first regression probe). The defect is in the **sprite TILE VRAM / OAM
attribute byte / subpal selection path**, caused by an interaction in the GBC
initialization sequence ordering within `buildMainFunction()`.

---

### Section 1: Probe Result Table

#### Full Bisect Table (Plans 03–06d)

| Probe | Source Commit | Emissions Applied | Cyan | Checker | BCPD slot 0 | OCPD slot 2 | Distinct Colors | Verdict |
|-------|---------------|-------------------|------|---------|-------------|-------------|-----------------|---------|
| 0 (baseline) | cfe41ad7 (pre-Plan-19/20) | None beyond baseline | YES | YES | 0x7FFF (non-zero) | 0x7FFF (non-zero) | 5 | BOTH PATHS WORK — cyan sprite + checker BG |
| A (Plans 19+20) | +7b86049f (Plan 20 fix) | DISPLAY_OFF prepend; sprite-palette hoist into main pre-DISPLAY_ON; LCDC reorder | YES | YES | 0x7FFF | 0x7FFF | 5 | Plans 19+20 did NOT regress either path |
| B (Plan 22) | +0976e08b (Plan 22 fix) | Plan 22 all 3 emissions (#1+#2+#3) | NO | YES | 0x7FFF | 0x7FFF | 4 (grayscale) | **REGRESSION NAMED: Plan 22** |
| C-1 | constant only (#1) | `_gbkt_default_bg_pal[4]` constant declaration | YES | YES | 0x7FFF | 0x7FFF | 5 | CLEARED — constant alone inert |
| C-2 | constant + set_bkg_palette (#1+#2) | + `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` | YES | YES | 0x7FFF (BCPD confirmed writing correctly) | 0x7FFF | 5 | CLEARED — set_bkg_palette alone inert |
| C-3 | bgFillCheckerboard hoist only (#3) | `fill_bkg_rect` + `set_bkg_data(0, 1, ...)` hoist into main | YES | YES | 0x7FFF | 0x7FFF | 5 | CLEARED — bgFillCheckerboard hoist alone inert |
| C-4 | constant + bgFillCheckerboard (#1+#3) | Emissions #1 + #3, WITHOUT #2 | YES | YES | 0x7FFF | 0x7FFF | 5 | CLEARED — constant+bgFillCheckerboard WITHOUT set_bkg_palette inert |

#### Cluster Naming Verdict

**REGRESSION CLUSTER: Plan 22 (commit 0976e08b)**

**MINIMAL BREAKING PAIR CONFIRMED:**
- Emission #2: `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` (BCPD write in main() before DISPLAY_ON)
- Emission #3: `fill_bkg_rect` + `set_bkg_data(0, 1, _checkerboard_bg_pattern)` hoisted from play_enter into main() AFTER allSpriteDataLoads

C-4 eliminates Emission #1 (constant) as a contributing factor. No single emission causes the regression. The regression is an **interaction effect** between Emission #2 and Emission #3 acting together.

---

### Section 2: Named Regression Site

**Source commit:** `0976e08b` (Plan 10.1-22 fix commit: "fix(10.1-22): close DEF-10.1-13-C visually — explicit BG palette + bgFillCheckerboard hoist (Plan 21 named cause)")

**Source plan ID:** `10.1-22`

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`

**Relevant line ranges (current HEAD):**
- Emission #2 (`hoistedDefaultBgPaletteStatements`): lines 3779–3784 (val block) + line 3811 (`addAll(hoistedDefaultBgPaletteStatements)` in mainBody)
- Emission #3 (`hoistedBgFillCheckerboardStatements`): lines 3762–3768 (val block) + line 3835 (`addAll(hoistedBgFillCheckerboardStatements)` in mainBody)
- Constant declaration (`_gbkt_default_bg_pal`): lines 1013–1018 (compile-time required by Emission #2; interaction-inert at runtime)

**Symptom:**
After Plan 22 landed, `./behavior3-subpalette-cycle-gbc.png` at frame 61 = 147 bytes (1 color, all-black), and at frame ~8 = 4 grayscale colors (no cyan). OCPD slot 2 = `[0x7FFF, 0x7FEA, 0x56A0, 0x2940]` (cyan_pal IS correctly in palette RAM). Yet the elephant sprite renders gray, not cyan — OAM attribute byte subpal=2 is NOT reaching OCPD slot 2 during PPU compositing.

**Hypothesized mechanism (confirmed by bisect chain — most likely):**

The current mainBody emission order within Plan 22 is:

```
...
addAll(allSpriteDataLoads)          // set_sprite_data(0u, 48u, elephant_tiles) → writes to VRAM $8000+
addAll(hoistedBgFillCheckerboardStatements)  // set_bkg_data(0, 1, _checkerboard_bg_pattern) → OVERWRITES VRAM $8000
...
```

With `LCDC.4=1` (shared `$8000-$97FF` region for BOTH BG and sprite tile data — the default after `cgb_compatibility()` / SKIP-bootstrap), `set_bkg_data(0, 1, _checkerboard_bg_pattern)` writes checker bytes starting at VRAM tile 0 (address `$8000`). This OVERWRITES the first 16 bytes of the elephant sprite tiles that `set_sprite_data(0u, 48u, elephant_tiles)` just loaded — corrupting sprite tile 0 with checker pattern bytes.

However, `set_bkg_data` alone (C-3) does not break cyan. The critical coupling:

**When `set_bkg_palette` (Emission #2) is also present**, it writes to BCPD slot 0 (BG palette RAM) via BCPS/BCPD before DISPLAY_ON. This commits a BG-palette-aware rendering state. Coffee-GB's PPU then uses VRAM tile 0 for BG tile 0 (from the BG tilemap at $9800, which by default points to tile index 0). Since `set_bkg_data` last wrote checker bytes to tile 0, the BG sees checker. But the sprite tile 0 is now the checker bytes — and when the PPU composites OAM object 0 (the elephant), the OAM attribute byte's subpal bits (subpal=2) select OCPD slot 2, but the tile DATA at tile 0 is now the checker pattern. The checker tile has pixel values that correspond to colors 0–3 in a 2bpp format — which, when decoded through OCPD slot 2 (cyan_pal), SHOULD still produce cyan-tinted pixels for any non-zero color index.

**Alternative mechanism (also consistent with data):** Coffee-GB's OAM DMA or attribute-byte initialization has a timing interaction with the combined BCPD-write + BG-tile-write sequence that resets the OAM attribute byte for sprite 0 to `0x00` (subpal=0 = gray_pal at OCPD slot 0) AFTER Plan 22's initialization sequence completes but BEFORE the first composited frame. The `set_bkg_palette` call arms some internal Coffee-GB GBC-mode state that triggers an OAM-attribute reset when `set_bkg_data` subsequently touches BG VRAM.

In both cases, the fix is the same: prevent `set_bkg_data(0, 1, ...)` from overwriting the sprite tile data that `set_sprite_data(0u, 48u, ...)` loaded.

---

### Section 3: Coffee-GB Internals Cross-Reference

The bisect established (per Probe B verdict):

- **OCPD slot 2 = `[0x7FFF, 0x7FEA, 0x56A0, 0x2940]` — cyan_pal IS in palette RAM.** The palette write path (`set_sprite_palette(2u, 1u, cyan_pal)`) is NOT broken. Coffee-GB's `ColorPalette.setByte()` at the OCPS/OCPD address pair is receiving the write correctly.
- **BCPD slot 0 = `[0x7FFF, 0x56B5, 0x294A, 0x0000]` — `_gbkt_default_bg_pal` IS in BG palette RAM.** Plan 22's explicit `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` emission is working correctly.

The "smoking gun" from `d-v3-visual-finding-v2.md:137-145`:

```java
this.bgPalette = new ColorPalette(0xff68);
this.oamPalette = new ColorPalette(0xff6a);
oamPalette.fillWithFF();  // sprite palette pre-init to 0x7FFF (white)
                          // BG palette LEFT AT ZEROS (black)
```

This explains the previous (Plan 21 / 4th-layer) finding that BG palette RAM was zero-init. Plan 22 FIXED that by emitting `set_bkg_palette()` explicitly. But now the C-2 probe shows that `set_bkg_palette` alone preserves cyan — so the zero-init BG issue is closed. The new (5th-layer) regression is the INTERACTION of `set_bkg_palette` + `bgFillCheckerboard` hoist.

Coffee-GB's `Gpu.java` and the LCDC.4=1 shared tile region ($8000-$97FF) are the relevant internals:
- `LCDC.4 = 1` (enabled by Plan 20's LCDC reorder): both BG tiles and sprite tiles read from the `$8000-$97FF` region.
- Any `set_bkg_data(0, ...)` or `set_sprite_data(0, ...)` call writes to the SAME physical VRAM addresses.
- Last write wins: if sprite data loads BEFORE BG tile data in main(), the BG tile data overwrites the sprite VRAM → sprite displays BG tile pixels (shaped like the checker) through whichever OAM palette slot is selected.

---

### Section 4: Proposed Fix Shape

**CHOSEN FIX: Option B — Order-Tweaked (PREFERRED)**

Move `addAll(hoistedBgFillCheckerboardStatements)` to **BEFORE** `addAll(allSpriteDataLoads)` in the `mainBody` buildList.

**Why this works:**
The checker bytes write to VRAM tile 0 first. Then `set_sprite_data(0u, 48u, elephant_tiles)` writes 48 sprite tiles starting at tile 0 — OVERWRITING the checker bytes with elephant tile data. Sprite tile 0 wins because it is the **later write** to the shared $8000 region. The elephant sprite tiles are intact when DISPLAY_ON fires.

**Why this is the minimum change:**
- No emission is added or removed.
- The constant declaration (`_gbkt_default_bg_pal`) is untouched.
- The `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` emission is preserved (BG palette RAM write still happens pre-DISPLAY_ON — correct behavior).
- The `bgFillCheckerboard` hoist is preserved (BG tile data is still in main() pre-DISPLAY_ON — correct behavior per reference metasprites.c:177-180).
- The only change is the ORDER of two `addAll()` calls in the `buildList { }` body.

**Test contract impact:** None of the 5 locked tests assert the relative order of `hoistedBgFillCheckerboardStatements` vs `allSpriteDataLoads`. They assert PRESENCE and POSITION relative to DISPLAY_ON. (See Section 6 for detailed analysis.)

**Alternatives considered:**
- **Option A (Additive):** Emit a duplicate `set_sprite_data(0, 1, elephant_tile_0)` AFTER the bgFillCheckerboard hoist to restore sprite tile 0. Rejected: requires knowing which specific tile was corrupted and emitting a second VRAM write. More complexity, more surface to test.
- **Option C (Subtractive):** Remove `hoistedBgFillCheckerboardStatements` + its `addAll` entirely, leaving bgFillCheckerboard only in play_enter. Rejected: would require adjusting `DV3VisualV2DiagnosticTest` test 2 (which asserts `set_bkg_data` IS in main() body). D-12 AskUser would be required.
- **Alternative 3 (inline cgb_compatibility replacement):** Replace `cgb_compatibility()` with explicit BGP/OBP0/OBP1/BCPD/OCPD writes. Rejected: far larger surface; pre-authorized as escalation path only if simpler fixes fail.

---

### Section 5: Exact Fix Specification (the Plan 08 Input)

**Target file:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`

**Current code at the regression site (mainBody buildList, lines ~3826–3836):**

```kotlin
            addAll(allSpriteDataLoads)
            // Plan 10.1-22 (DEF-10.1-13-C / 4TH LAYER): hoisted bgFillCheckerboard RawOp(s)
            // from {start}_enter — BG tile data + tilemap fill must run with LCD off so that
            // the first composited frame on GBC reads the checker pattern from $8000-$800F
            // (NOT the elephant sprite tile 0 bytes that allSpriteDataLoads just wrote there
            // when LCDC.4=1). Mirrors reference metasprites.c:177-180. Original RawOp(s)
            // remain in {start}_enter per Plan 20's duplication-not-relocation pattern —
            // BG VRAM writes are idempotent, BgCheckerboardEmissionTest's play_enter-body
            // assertion is preserved.
            addAll(hoistedBgFillCheckerboardStatements)
```

**Required change — swap the two `addAll()` calls:**

```kotlin
            // Plan 10.2-08 (DEF-10.1-13-C / 5TH LAYER): hoist bgFillCheckerboard BEFORE
            // sprite-data loads. With LCDC.4=1 (shared $8000-$97FF sprite+BG VRAM region),
            // set_bkg_data(0, 1, ...) writes checker bytes to VRAM tile 0 — the same region
            // set_sprite_data(0u, 48u, ...) will write. Writing bgFillCheckerboard FIRST means
            // the elephant tile data (set_sprite_data) is the LAST write to tile 0 → wins.
            // Writing it AFTER (Plan 22's order) caused checker bytes to overwrite elephant
            // tile 0, triggering the sprite-renders-gray regression identified by Phase 10.2
            // bisect chain (see evidence/d-v3-visual-finding-v3.md Section 2).
            addAll(hoistedBgFillCheckerboardStatements)
            addAll(allSpriteDataLoads)
```

**Surrounding context (5 lines before + 5 lines after) for unambiguous insertion verification:**

Before (lines ~3817–3826):
```kotlin
            add(CExprStatement(CBinaryExpr(CVar("NR52_REG"), "=", CLiteral(0x80))))
            add(CExprStatement(CBinaryExpr(CVar("NR50_REG"), "=", CLiteral(0x77))))
            add(CExprStatement(CBinaryExpr(CVar("NR51_REG"), "=", CLiteral(0xFF))))
            // Plan 10.1-20 (DEF-10.1-13-C / GAP-3): move sprite-data + OAM init + pool init
            // BEFORE the LCDC sequence + DISPLAY_ON. With LCD off these VRAM writes complete
            // unconditionally ...
            //
            // Load all sprite-VRAM tile data into VRAM via unified loader: actor sprites first
            // (preserves Pong/Breakout/SimplePhysics shape), then metasprites (continue from
            // where actors left off — single VramAllocator instance, CR-01 fix).
            addAll(allSpriteDataLoads)
```

After (lines ~3836–3841):
```kotlin
            addAll(hoistedBgFillCheckerboardStatements)
            // Bind OAM slots to tiles and set initial positions
            addAll(spriteOAMInits)
            // Initialize actor pools: zero active bitmaps, set OAM entries to static base slots
            addAll(poolInitCalls)
```

**The exact old_string / new_string pair for the Edit tool:**

old_string:
```
            addAll(allSpriteDataLoads)
            // Plan 10.1-22 (DEF-10.1-13-C / 4TH LAYER): hoisted bgFillCheckerboard RawOp(s)
            // from {start}_enter — BG tile data + tilemap fill must run with LCD off so that
            // the first composited frame on GBC reads the checker pattern from $8000-$800F
            // (NOT the elephant sprite tile 0 bytes that allSpriteDataLoads just wrote there
            // when LCDC.4=1). Mirrors reference metasprites.c:177-180. Original RawOp(s)
            // remain in {start}_enter per Plan 20's duplication-not-relocation pattern —
            // BG VRAM writes are idempotent, BgCheckerboardEmissionTest's play_enter-body
            // assertion is preserved.
            addAll(hoistedBgFillCheckerboardStatements)
```

new_string:
```
            // Plan 10.2-08 (DEF-10.1-13-C / 5TH LAYER): hoist bgFillCheckerboard BEFORE
            // sprite-data loads. With LCDC.4=1 (shared $8000-$97FF sprite+BG VRAM region),
            // set_bkg_data(0, 1, ...) writes checker bytes to VRAM tile 0 — the same region
            // set_sprite_data(0u, 48u, ...) will write. Writing bgFillCheckerboard FIRST means
            // the elephant tile data (set_sprite_data) is the LAST write to tile 0 → wins.
            // Writing AFTER (Plan 22's order) caused checker bytes to overwrite elephant tile 0,
            // triggering the sprite-renders-gray regression identified by Phase 10.2 bisect chain
            // (evidence/d-v3-visual-finding-v3.md, Section 2).
            //
            // The bgFillCheckerboard writes are idempotent; the original RawOp(s) remain in
            // {start}_enter per Plan 20's duplication-not-relocation pattern. BgCheckerboard-
            // EmissionTest's play_enter-body assertion is unaffected by this reorder.
            addAll(hoistedBgFillCheckerboardStatements)
            addAll(allSpriteDataLoads)
```

**Verification: after applying this swap, the generated main.c must contain:**
1. `fill_bkg_rect` BEFORE `set_sprite_data` in the main() body.
2. `set_bkg_data` BEFORE `set_sprite_data` in the main() body.
3. Both still BEFORE `DISPLAY_ON`.

---

### Section 6: Test Contract Impact Analysis

#### Test 1: `DV3GbcPaletteWriteDiagnosticTest` (Plan 19, 3 tests)

**Impact: NO — this fix does not break these tests.**

These 3 tests lock the bootstrap-order contract established by Plan 10.1-19/20:
- sprite palette writes (`set_sprite_palette`) are in main() before DISPLAY_ON
- cgb_compatibility() is before NR52_REG (sound init)
- DISPLAY_OFF is the first statement in main()

The order-tweak ONLY swaps `hoistedBgFillCheckerboardStatements` and `allSpriteDataLoads` within the pre-DISPLAY_ON block. It does not touch sprite palette writes, cgb_compatibility, or DISPLAY_OFF ordering. All 3 tests remain GREEN.

#### Test 2: `DV3VisualV2DiagnosticTest` (Plan 21, 2 tests)

**Impact: NO — this fix does not break these tests.**

These 2 tests assert:
1. `set_bkg_palette` is present in main() body AND before DISPLAY_ON.
2. `set_bkg_data` AND `fill_bkg_rect` are present in main() body AND before DISPLAY_ON.

The order-tweak preserves ALL of these. `set_bkg_palette` stays at its current position (after sprite palettes, before sound init). `set_bkg_data` and `fill_bkg_rect` move from after `allSpriteDataLoads` to BEFORE `allSpriteDataLoads` — still before DISPLAY_ON. Both assertions remain GREEN.

No D-12 AskUser required for this test.

#### Test 3: `BgCheckerboardEmissionTest` (Plan 18+20, 4 tests)

**Impact: NO — this fix does not break these tests.**

These 4 tests assert:
1. `fill_bkg_rect(...)` is present in `play_enter` body.
2. `set_bkg_data(0, 1, ...)` is present in `play_enter` body.
3. True 4×4 checker pattern constant bytes present at file scope.
4. No `printf(` in `play_enter` body.

ALL of these assertions target `bank1.c`'s `play_enter` function, NOT `main()`. The order-tweak in `mainBody` has no effect on the `play_enter` body (per Plan 20's duplication-not-relocation pattern, the RawOp remains in `{start}_enter` unconditionally). All 4 tests remain GREEN.

#### Test 4: `SpritePaletteSlotEmissionTest` (Plan 20, 2 tests)

**Impact: NO — this fix does not break these tests.**

These tests assert distinct slot indices 0/1/2/3 in `set_sprite_palette(0u, 1u, ...)`, `set_sprite_palette(1u, ...)`, etc. These calls come from `hoistedStartPaletteStatements` (not from `allSpriteDataLoads` or `hoistedBgFillCheckerboardStatements`). The order-tweak does not touch `hoistedStartPaletteStatements`. Both tests remain GREEN.

#### Test 5: `GbcCompatEmissionTest` (1 relevant assertion at line 129)

**Impact: NO — this fix does not break this test.**

The assertion at line 129 checks that `cgb_compatibility()` is present in main() body AND appears before `NR52_REG` (sound init). The order-tweak touches the VRAM-write block (after sound init), not the cgb_compatibility / NR52_REG region. The cgb_compatibility test remains GREEN.

#### Alternative 3 caveat (A5 binding):

The chosen fix is NOT Alternative 3 (inline cgb_compatibility replacement), so the A5 binding caveat does not apply. `GbcCompatEmissionTest.kt:129`'s `mainBody.contains("cgb_compatibility()")` assertion is unaffected.

**Summary: Zero locked tests broken. No D-12 AskUser required.**

---

### Section 7: New RED Test Specification

#### What the test must assert

The order-tweaked fix produces this emission order in main():
```c
fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);
set_bkg_data(0, 1, _checkerboard_bg_pattern);
set_sprite_data(0u, 48u, elephant_tiles);   // ← must come AFTER bgFillCheckerboard
...
DISPLAY_ON;
```

The test must lock the **relative ordering invariant**: `fill_bkg_rect` / `set_bkg_data` must appear BEFORE `set_sprite_data` in main() — not merely "before DISPLAY_ON" (which `DV3VisualV2DiagnosticTest` already asserts). The new assertion is specifically about the relative position of two VRAM-write blocks.

#### Test method names

1. `main body emits hoisted bgFillCheckerboard BEFORE set_sprite_data (5TH LAYER order fix)` — asserts `fill_bkg_rect` appears at a lower index than `set_sprite_data` in the main() body.
2. `main body emits set_bkg_data BEFORE set_sprite_data (5TH LAYER VRAM collision fix)` — asserts `set_bkg_data` appears at a lower index than `set_sprite_data` in the main() body.

#### Brace-walk target

Function signature: `"void main(void)"` — same as `DV3VisualV2DiagnosticTest`.

#### Index assertions

```
setBkgDataIdx = mainBody.indexOf("set_bkg_data")
fillBkgRectIdx = mainBody.indexOf("fill_bkg_rect")
setSpriteDataIdx = mainBody.indexOf("set_sprite_data")

assertTrue(fillBkgRectIdx < setSpriteDataIdx)  // bgFillCheckerboard emitted before sprite data load
assertTrue(setBkgDataIdx < setSpriteDataIdx)   // bgFillCheckerboard emitted before sprite data load
```

#### Why these assertions lock the fix's emission shape uniquely

- `DV3VisualV2DiagnosticTest` tests 1 and 2 only assert **presence** and **before DISPLAY_ON**. They cannot distinguish the current (broken) order (`sprite_data` THEN `bkg_data`) from the fixed order (`bkg_data` THEN `sprite_data`).
- The new test adds the **relative ordering** constraint: `bkg_data` before `sprite_data`. This is the exact invariant that the order-tweak establishes.
- Plan 08's fix (swap of two `addAll()` calls) is the ONLY change that can flip these assertions GREEN — no other current or planned change affects this relative ordering.

#### Why the test is RED today

Today's mainBody emission order is:
```
allSpriteDataLoads → hoistedBgFillCheckerboardStatements
```
i.e., `set_sprite_data` BEFORE `set_bkg_data`. The assertions `fillBkgRectIdx < setSpriteDataIdx` and `setBkgDataIdx < setSpriteDataIdx` will FAIL because `fillBkgRectIdx > setSpriteDataIdx` in the current code. The test exits non-zero (RED).

---

### Section 8: Alternatives Considered

| Fix Shape | Description | Decision | Reason |
|-----------|-------------|----------|--------|
| **Option B — Order-Tweaked (CHOSEN)** | Move `addAll(hoistedBgFillCheckerboardStatements)` to BEFORE `addAll(allSpriteDataLoads)` | CHOSEN | Minimal change; preserves all 5 locked test contracts; eliminates VRAM tile 0 collision without removing any emission. Bisect data directly implies this fix (last write wins on shared $8000 region). |
| Option A — Additive | Emit a re-write of sprite tile 0 AFTER bgFillCheckerboard hoist (duplicate `set_sprite_data(0, 1, elephant_tile_0)`) | Rejected | More complexity; requires knowing which tile was corrupted; double-writes to VRAM. Option B achieves the same effect with a simpler swap. |
| Option C — Subtractive | Remove `hoistedBgFillCheckerboardStatements` + its `addAll` entirely | Rejected | Would break `DV3VisualV2DiagnosticTest` test 2 (which asserts `set_bkg_data` IS in main() body). Requires D-12 AskUser. Option B avoids test deletion. |
| Alternative 3 — Inline cgb_compatibility | Replace `cgb_compatibility();` with explicit BGP/OBP0/OBP1/BCPD/OCPD register writes | Rejected | Far larger surface to test; requires changing a fundamental GBC init call. Pre-authorized only as escalation if simpler fixes fail. The bisect data points at the VRAM-ordering issue, NOT at cgb_compatibility. |

---

### Section 9: Phase Closure Verdict Prediction

After Plan 08 fix lands (order-tweak applied):

**behavior3 PNG (subpalette-cycle-gbc) predicted outcome:**
- Probe A (Plans 19+20 baseline, with correct fix) showed 5 distinct colors including cyan.
- After the order fix, the elephant sprite tile 0 will contain correct elephant pixel data (not checker bytes) at DISPLAY_ON time.
- The OAM attribute byte subpal=2 will select OCPD slot 2 = `cyan_pal {0x7FFF, 0x7FEA, 0x56A0, 0x2940}`.
- **Predicted distinct colors: ≥5** — BG checker (white, lt-gray via `_gbkt_default_bg_pal` slot 0) + elephant sprite (GBC green from `0x56A0` = R10,G21,B0 → visible green/cyan) + background black = at least 4–5 colors. Cyan (criterion: R<100, G>150, B>150) should appear in the elephant sprite pixels.

**BCPD slot 0 at frame 60:**
- `[0x7FFF, 0x56B5, 0x294A, 0x0000]` — unchanged (same as Probes A–C4; `_gbkt_default_bg_pal`). BG palette RAM write is unaffected by the order-tweak.

**OCPD slot 2 at frame 60:**
- `[0x7FFF, 0x7FEA, 0x56A0, 0x2940]` — unchanged (same as all probes; `cyan_pal` is correctly loaded). The fix allows the PPU to ACTUALLY USE this palette (subpal=2 selects it correctly because OAM attribute byte is not corrupted).

**behavior1/2 (DMG) regression prediction: NONE expected.**
- The order-tweak only affects the relative position of `hoistedBgFillCheckerboardStatements` vs `allSpriteDataLoads`.
- `hoistedBgFillCheckerboardStatements` is built from a filter on `startScene.enterOps` — if there is no `bgFillCheckerboard()` RawOp in the start scene, the list is empty and the swap is a no-op.
- DMG games (GbcTarget.DMG) have `hoistedDefaultBgPaletteStatements` = empty (gated on non-DMG), so the triggering condition for the interaction never arises.
- Pong, Breakout, and SimplePhysics DMG examples do not use `bgFillCheckerboard()` in their start scenes, so `hoistedBgFillCheckerboardStatements` is empty → no effect on their codegen.

---

## RED Test Status

`DV3VisualV3DiagnosticTest` is **committed RED** (Plan 07 Task 2). Two tests:

1. `main body emits hoisted bgFillCheckerboard BEFORE set_sprite_data (5TH LAYER order fix)` — asserts `fill_bkg_rect` appears before `set_sprite_data` in main() body.
2. `main body emits set_bkg_data BEFORE set_sprite_data (5TH LAYER VRAM collision fix)` — asserts `set_bkg_data` appears before `set_sprite_data` in main() body.

Both tests FAIL today (exit non-zero). Plan 08 landing the order-tweak is the only change that flips them GREEN.
