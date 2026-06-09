# Round 6 — ALL HYPOTHESES REFUTED — escalation

**Date:** 2026-05-11
**Phase:** 07.4-sport-genre-codegen-fix-inserted
**Plan:** 27 (entered ZERO-CONFIRMED branch)
**ROM HEAD:** c5e3f1bc fix(07.4-27): re-introduce H-1 SHOW_BKG in main_init

## Summary

Plan 07.4-27 reproduced the WRAM-corruption hang deterministically against the
H-1-only ROM (frame 124, watchdog `EmulatorFrameHangException`), then ran the
four hypothesis experiments listed in `26-PLAN26-FOLLOWUP.md` (H-A, H-B, H-C,
H-D) plus one additional experiment (H-E) surfaced by code inspection.

**All five experiments produced the IDENTICAL hang signature.** No hypothesis
survived; the plan therefore enters its ZERO-CONFIRMED escalation branch and
STOPS without naming a confirmed root cause.

## What this rules out

- **H-A (SDCC overlay collision in racing_tick locals):** REFUTED. Compiling
  with `-Wf--no-overlay` did not change the hang frame or signature.
- **H-B (SWITCH_ROM(2) leak after set_bkg_tiles):** REFUTED. Appending
  `SWITCH_ROM(1)` after `set_bkg_tiles` in `race_enter` did not change the
  hang.
- **H-C (sound_driver_update WRAM corruption):** REFUTED. Removing the
  per-frame `sound_driver_update()` call did not change the hang.
- **H-D (uninitialised BGP_REG before SHOW_BKG):** REFUTED. Initialising
  `BGP_REG = 0xE4` before `SHOW_BKG` did not change the hang.
- **H-E (uninitialised for-loop counters in _win_print_at /
  _win_clear_region):** Found via code inspection — real codegen bug, but
  REFUTED as the round-6 hang cause. Initialising the counters did not change
  the hang.

## What this points at (next-round suspect)

The hang signature, deterministic frame count, and watchdog payload's literal
hint ("Check LCDC ... and the most recent scene-enter") all point at a
different pathway:

**Working hypothesis (NOT confirmed; surfaced for future planning):** the LCD
is disabled during `race_enter` (likely by GBDK's `set_bkg_data` /
`set_bkg_tiles` helpers that temporarily disable LCD to safely write VRAM
during a mid-frame moment) and is never re-enabled before the main loop hits
`wait_vbl_done()`. With LCD off, no VBlank fires, and the wait spins forever.

This pathway is upstream of all four documented hypotheses and one inspected
hypothesis (H-E). It needs its own diagnostic plan that:

1. Probes the LCDC register (`0xFF40`) directly at race_enter+1 frame via
   `StepAgent.readMemory(0xFF40)`. If LCDC.7 (`BIT_LCD_ENABLED`) is 0, the LCD
   is off — confirmed cause class.
2. Reads the GBDK lib source at `$GBDK_HOME/lib/gb/` (specifically `set_bkg_*`
   implementations) to identify the LCD-disable hook.
3. Decides whether the fix is in our codegen (wrap `set_bkg_data` / `set_bkg_tiles`
   in `DISPLAY_ON` before returning from `race_enter`) or in the user-facing
   DSL.

## What ships in commit history despite escalation

- **commit `c5e3f1bc`:** H-1 SHOW_BKG re-introduction in `GBDKPipelineV2.kt`.
  Correct in isolation (Plan 25 DIAGNOSIS.md) and load-bearing for any
  subsequent round-6+ work. Do NOT revert.
- **commit `7a6f96ce`:** Round6WramCorruptionProbe.kt + baseline evidence.
  Probe is reusable; retain.
- **commit (this task):** experiments matrix + this escalation note +
  per-hypothesis evidence files. Documents the refuted hypotheses so future
  diagnoses do not re-run the same experiments.

## What Plan 07.4-28 must NOT do

- Do NOT pick one of H-A..H-D and ship it as a "best guess" production fix.
  All four are refuted; shipping a refuted fix would be the round-3/4/5
  false-positive class recurrence the visual-evidence rule was codified to
  prevent.
- Do NOT skip directly to "fix LCDC disabled" without first running the LCDC
  read-memory probe described above. The LCDC pathway is a working hypothesis,
  not a confirmed root cause.

## What Plan 07.4-28 (or a new round-7) MUST do

1. Read the LCDC register at race_enter+1 frame via `readMemory(0xFF40)` from
   the probe.
2. If LCDC.7 == 0: confirm the LCD-disable pathway and trace which GBDK helper
   is responsible (via map file / symbol file).
3. Land a production fix (whatever shape that takes) with a JVM-tier RED test
   contract specified BEFORE the fix lands.
4. Honour the visual-evidence rule: PNG screenshot at race_enter+30 frames
   showing non-blank BG and unique_pixels >= 4.

## Follow-up codegen issues surfaced (out-of-scope for round 6 fix, file as
deferred)

H-E surfaced a real bug: `_win_print_at`, `_win_clear_region`, and
`_win_fill_screen` in `DialogVisitor.kt` declare their loop counters
uninitialised. Generated C looks like:

```c
void _win_print_at(UINT8 x, UINT8 y, const UINT8* str, UINT8 len) {
    UINT8 i;                    // uninitialised
    for (; i < len; i++) {      // condition on uninitialised i
        set_win_tiles(x + i, y, 1u, 1u, (unsigned char*)&str[i]);
    }
}
```

This is real undefined behaviour. SDCC may pick a stack slot that happens to
be 0 (giving correct behaviour by luck) or any value 1..255 (truncating /
expanding the printed string). The fix is one line per helper: change
`CVarDecl("i", CU8, initializer = null)` to
`CVarDecl("i", CU8, initializer = CLiteral(0))`. The fix did not resolve the
round-6 hang, but the underlying bug remains and should be filed as a
deferred follow-up.

Suggested follow-up gap: **GAP-WIN-HELPER-UNINIT-LOOP-COUNTER** —
DialogVisitor's window-text helpers leave their loop counters uninitialised,
making the printed-text length and clear-region size depend on stack garbage.
Filed in `deferred-items.md`.
