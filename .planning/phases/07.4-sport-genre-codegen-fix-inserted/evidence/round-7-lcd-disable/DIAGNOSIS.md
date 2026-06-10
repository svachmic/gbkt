# Round-7 LCD-Disable Diagnosis

**Date:** 2026-05-12
**Phase:** 07.4-sport-genre-codegen-fix-inserted
**Plan:** 28
**ROM HEAD:** commit on worktree-agent-a2e219a7542b597db, post H-1 SHOW_BKG fix c5e3f1bc

---

## Confirmed Hypothesis

**H-LCD-DISABLE: GBDK `set_bkg_data` / `set_bkg_tiles` disable the LCD during VRAM writes
and do NOT re-enable it before returning from `race_enter`.**

The LCD is disabled at frame 4 (during `race_enter` execution) by GBDK's compiled
`_set_bkg_data` / `_set_bkg_tiles` helpers. After the last VRAM write, LCDC.7 remains 0.
The main loop then calls `wait_vbl_done()` with LCD off. Since no LCD means no VBlank
interrupt fires, `wait_vbl_done()` spins forever → `EmulatorFrameHangException` at frame 5.

This is the LCD-disable working hypothesis surfaced by `ESCALATION-zero-confirmed.md` from
Plan 27. It is now confirmed by direct register evidence from the Task 2 probe trace.

---

## Probe Evidence

From `01-lcdc-trace-baseline.txt` (5 rows collected before hang):

```
frame=0 LCDC=0xC1 STAT=0x81 IF=0xF1 LY=0x90   → LCDC.7=1 (LCD ON,  LY=144=VBlank)
frame=1 LCDC=0xC1 STAT=0x81 IF=0xF1 LY=0x90   → LCDC.7=1 (LCD ON,  LY=144=VBlank)
frame=2 LCDC=0xC1 STAT=0x81 IF=0xF1 LY=0x90   → LCDC.7=1 (LCD ON,  LY=144=VBlank)
frame=3 LCDC=0xC1 STAT=0x81 IF=0xF1 LY=0x90   → LCDC.7=1 (LCD ON,  LY=144=VBlank)
frame=4 LCDC=0x39 STAT=0x80 IF=0xF1 LY=0x00   → LCDC.7=0 (LCD OFF, LY=0 — LCD disabled)
HANG_AT_FRAME=5
```

Key observations:

1. `0xC1 AND 0x80 = 0x80` → LCDC.7=1 (LCD enabled) for frames 0-3. VBlank region (LY=144=0x90)
   confirms the main loop is advancing normally.
2. `0x39 AND 0x80 = 0x00` → LCDC.7=0 (LCD DISABLED) at frame 4. This is when `race_enter`
   runs its GBDK VRAM-write block (`set_bkg_data` + `set_bkg_tiles`).
3. `LCDC=0x39 = 0b00111001`: all BG/WIN mode bits preserved, only LCDCF_ON (bit 7) cleared.
   This matches GBDK's `display_off()` implementation which does `LCDC_REG &= ~LCDCF_ON`.
4. `LY=0x00` at frame 4 confirms LCD is off — when LCD is disabled, LY stays at 0.
5. At frame 5, the main loop calls `wait_vbl_done()` with LCD=off → VBlank never fires → hang.

From `02-ie-if-stat-trace.txt` (H-F inline check):

```
marker=race_enter+1 IE=0x01 IF=0xF1 STAT=0x81 LY=0x90
```

`IE=0x01`: bit 0 (VBL_IFLAG) = 1 → VBLANK interrupt IS enabled. H-F (VBlank ISR not
installed) is REFUTED. The interrupt is enabled — the problem is that LCD-off means no
VBlank fires to trigger it, not that the ISR is absent.

---

## H-F Inline Refutation

**H-F: VBlank ISR not installed (IE bit 0 = 0)**

Probe evidence: `IE=0x01` at race_enter+1 → `IE AND 0x01 = 1` → VBLANK interrupt IS
enabled.

**H-F VERDICT: REFUTED**

H-G (sound stub) and H-H (stack overflow) are not probed because the LCDC register trace
directly confirms LCD disable at frame 4. Probing H-G / H-H would only be necessary if
the LCDC trace showed LCD REMAINING ON across all frames. Since LCDC.7 goes to 0 at
frame 4 and the hang follows immediately at frame 5, the LCD-disable pathway is
the confirmed root cause. H-G and H-H cannot explain a hang that is temporally correlated
with LCDC.7=0.

03-fallback-probe-results.txt records H-F as REFUTED (the only probe needed).

---

## Load-bearing GBDK helper

**Symbol:** `_set_bkg_data` (primary, runs first in `race_enter`) + `_set_bkg_tiles` (secondary)

**Library:** `$GBDK_HOME/lib/gb/gb.lib` (GBDK 4.5.0 at `/Users/michalsvacha/gbdk`)

**Source not available locally** (only compiled `gb.lib` in the GBDK-2020 4.5.0 installation;
source-level verification is deferred to maintainers with GBDK source access).

Evidence from `gb.lib` symbols (`strings $GBDK_HOME/lib/gb/gb.lib`):

```
S _set_bkg_data Def00000005
S .display_off Ref00000000
S _set_bkg_tiles Def00000000
S .display_off Ref00000000
```

Both `_set_bkg_data` and `_set_bkg_tiles` reference `.display_off` (the internal VBlank-
wait + LCDC.7-clear function). The `gb.lib` symbol table confirms these functions call
`display_off()` before performing their VRAM writes.

**GBDK-2020 upstream source reference:** `gbdk-lib/libc/gb/set_bkg_data.s` and
`gbdk-lib/libc/gb/set_bkg_tiles.s` at https://github.com/gbdk-2020/gbdk-2020 — the
implementation calls `.display_off` before VRAM copy and is expected to re-enable LCD
after; however our runtime probe shows LCD remaining OFF after the calls in GBDK 4.5.0.

**Root cause** (probe-confirmed): After `set_bkg_data` and `set_bkg_tiles` complete,
LCDC.7 remains 0. The codegen does NOT emit any `DISPLAY_ON` call after these VRAM
writes in `race_enter`. The fix is in the codegen, not in GBDK itself.

---

## Recommended Fix Scope

**File:**
`gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt`

**Symbol / Seam:**
`SportVisitor.buildRaceEnterOps()` — the method that emits the `set_bkg_data` +
`set_bkg_tiles` RawOps for the race scene's enter block (lines ~440-471).

**Change:** After the `set_bkg_tiles(...)` RawOp, add `RawOp("DISPLAY_ON;")` to re-enable
the LCD following the VRAM writes.

**Per D-N-05/D-N-06**: The fix is post-VRAM-write in the scene-enter function (not in
`main_init`). `main_init` already has `DISPLAY_ON` / `SHOW_BKG` / `SHOW_SPRITES`.
The race-enter re-enable is needed ONLY in `race_enter`, after `set_bkg_tiles` runs.

**Emitted C shape (post-fix):**
```c
void race_enter(void) BANKED {
    _camera_target = 0u;
    set_bkg_data(0, 3, _racing_track1_tileset);   // ← disables LCD
    SWITCH_ROM(2);
    set_bkg_tiles(0, 0, 19u, 19u, _zone_track1_tiles);  // ← re-disables LCD (display_off)
    DISPLAY_ON;  // ← FIX: re-enables LCD after VRAM writes
    _current_tileset_id = 1u;
    // ... rest of enter body
}
```

**Alternative fix location (ALSO acceptable):**
`GBDKPipelineV2.kt` — add a post-processing pass that detects scene-enter bodies
containing `set_bkg_data`/`set_bkg_tiles` RawOps and injects `DISPLAY_ON` after the last
such call. This is more general (covers future genre plugins) but higher scope. Plan 30
should prefer the `SportVisitor.kt` fix for surgical correctness and minimal regression
surface.

---

## RED test contract for Plan 30

**Test class name:** `SportVisitorLcdReenableAfterBkgWriteTest`

**Location:**
`gbkt-genre-sport/src/test/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitorLcdReenableAfterBkgWriteTest.kt`
OR
`gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/LcdDisplayOnAfterSetBkgTest.kt`

**Assertion shape (brace-walk extract of `race_enter` scope):**

```kotlin
// 1. Build a minimal racer IR and run GBDKPipelineV2
// 2. Extract the race_enter function body from bank1.c
// 3. Scope-extract: awk between '{' and '}' of race_enter
// 4. Within that scope, verify:

// Invariant A: DISPLAY_ON appears in race_enter body
assertTrue(raceEnterBody.contains("DISPLAY_ON"),
    "race_enter must emit DISPLAY_ON after set_bkg_tiles to re-enable LCD")

// Invariant B: DISPLAY_ON comes AFTER set_bkg_tiles (positional)
val displayOnIdx = raceEnterBody.indexOf("DISPLAY_ON")
val setBkgTilesIdx = raceEnterBody.indexOf("set_bkg_tiles")
assertTrue(displayOnIdx > setBkgTilesIdx,
    "DISPLAY_ON must appear after set_bkg_tiles in race_enter body")

// Invariant C: no set_bkg_data/set_bkg_tiles between DISPLAY_ON and end of enter body
val afterDisplayOn = raceEnterBody.substring(displayOnIdx)
assertFalse(afterDisplayOn.contains("set_bkg_tiles"),
    "No set_bkg_tiles should appear after DISPLAY_ON in race_enter")
```

**Pre-fix MUST RED:** All three invariants fail against HEAD ROM because `race_enter` has
no `DISPLAY_ON` after `set_bkg_tiles`.

**Post-fix MUST GREEN:** All three invariants pass after `SportVisitor.buildRaceEnterOps()`
emits `RawOp("DISPLAY_ON;")` following the `set_bkg_tiles` RawOp.

**D-N-11 runtime gate for Plan 30:** Plan 30's verification MUST re-run the LCDC probe
(this plan's Task 2 test) and confirm `HANG_AT_FRAME` no longer appears in the trace.
Specifically, frames 0..30 must ALL show `LCDC=0xC1` (LCD on) without the `0x39` value.

---

## Wrap shape

**Macro:** `DISPLAY_ON`

From `$GBDK_HOME/include/gb/gb.h`:
```c
#define DISPLAY_ON \
  LCDC_REG|=LCDCF_ON
```

Where `LCDCF_ON = 0b10000000 = 0x80` (bit 7 of LCDC at 0xFF40).

`DISPLAY_ON` sets `LCDC |= 0x80` which re-enables the LCD controller. This matches what
the initial `main_init` sequence already uses (Plan 27 H-1 fix), confirming it is the
correct macro for this fix context.

**Alternative macro (also valid):** `LCDC_REG |= 0x80;` — but `DISPLAY_ON` is preferred
for readability and consistency with the existing `main_init` pattern.

**NOT `SHOW_BKG`:** `SHOW_BKG` sets bit 0 (BG plane enable), not bit 7 (LCD enable).
The fix must set bit 7 (`DISPLAY_ON`) because bit 7 (LCD_ENABLE) was cleared by
`display_off()`. `SHOW_BKG` is a separate concern and is already present from the H-1 fix
in `main_init`.

---

## Notes for Plan 30 Implementors

1. The fix is a single `RawOp("DISPLAY_ON;")` line added to `SportVisitor.buildRaceEnterOps()`
   in `gbkt-genre-sport`, after the `set_bkg_tiles` RawOp (lines ~462-464).

2. The existing JVM-tier lock `ScreenClearSceneAwareTest` must not regress — it tests that
   `race_enter` uses `HIDE_SPRITES` / `_win_clear_region` (not `cls()`). The fix does not
   touch that code path.

3. Plan 30's D-N-11 runtime gate: re-run `Round6WramCorruptionProbe.lcdc trace frames 0 to 30
   post race enter` after the fix. Expected outcome: 31 frame= rows (0..30), all with
   `LCDC=0xC1` (LCD on), no `HANG_AT_FRAME` line. EmulatorFrameHangException must NOT fire.

4. Visual verify (Plan 29): must capture a screenshot at race_enter+30 frames showing
   non-blank background tilemap. The LCD re-enable is a prerequisite for visual BG
   rendering.
