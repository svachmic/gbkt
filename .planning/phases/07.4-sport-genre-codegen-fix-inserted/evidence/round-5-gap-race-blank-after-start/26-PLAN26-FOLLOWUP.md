# Plan 07.4-26 follow-up — round-5 rejected, round-6 needed

**Status**: Plan 26 H-1 fix (`SHOW_BKG;` in main_init) is correct but **not sufficient**. Rolled back; worktree-agent-abd1af3e4ac30fabd discarded. A round-6 gap plan must address both H-1 AND the second-order corruption surfaced below.

## What round-5 confirmed

The Plan 25 diagnosis (H-1: missing `SHOW_BKG` in `main_init`) is correct in isolation — adding `SHOW_BKG;` after `DISPLAY_ON;` enables LCDC bit 0 and the title screen renders properly (text visible, BG layer composited).

JVM tier evidence (already locked by the discarded worktree's Plan 26 RED test):

- `BgLayerEnabledTest` was RED at HEAD (no `SHOW_BKG` in main body), GREEN after the fix.
- Full `:gbkt-backend-gbdk:test` and `:gbkt-genre-sport:test` suites pass with the fix applied.
- Generated `racer/build/gbkt/generated/main.c` contains `SHOW_BKG;` immediately after `DISPLAY_ON;`.

## What round-5 surfaced as a deeper regression

When the post-fix ROM is exercised at runtime (real Coffee-GB emulator, not codegen tests), the title screen renders correctly but the **race scene corrupts WRAM and triggers a hang**:

1. `title_frame` correctly navigates to `race` on START press.
2. `race_enter` runs (still has the unrestored `SWITCH_ROM(2)` from Plan 22). Looks normal.
3. The **first** race_frame call reads `_racing_lap_count_track1` as 57 (should be 0). The `>= 3u` guard fires; the ROM navigates to `results`.
4. The next several frames run `results_frame`. The watchdog (commit `eaf93a49`) trips on frame 5 with `EmulatorFrameHangException`.

The WRAM-read corruption pattern is a smoking gun (snapshot from a JVM probe driving the post-fix ROM):

| Address | Variable | Expected | Actual |
|---------|----------|----------|--------|
| 0xC0B1 | `_pool_carAi_active[0]` | 1 (post-spawn) | 0 |
| 0xC0B2 | `_pool_carAi_x[0]` | 80 | 57 |
| 0xC0B3 | `_pool_carAi_y[0]` | 96 | 0 |
| 0xC0D3 | `_car_x` | 80 | 0 |
| 0xC0D4 | `_car_y` | 80 | 57 |
| 0xC0D5 | `_rival_x` | 80 | 0 |
| 0xC0D6 | `_rival_y` | 96 | 57 |
| 0xC0D7 | `_raceTime` | 0 | 0 |
| 0xC0E7 | `_camera_x` | 0 | 0 |
| 0xC0E8 | `_camera_y` | 0 | 57 |
| 0xC0EC | `_vehicle_carPlayer_speed_cur` | 0 | 57 |
| 0xC0ED | `_vehicle_carPlayer_heading` | 0 | 0 |
| 0xC0EE | `_racing_lap_count_track1` | 0 | 57 |

Pattern: **even-address bytes read 0x39 (57), odd-address bytes read 0**. That fingerprint suggests a **16-bit-wide write of `0x0039` striped across a wide WRAM region** — not a single-variable bug.

## Hypotheses for round-6 to investigate (in order of likelihood)

| # | Hypothesis | Why plausible | How to test |
|---|-----------|---------------|-------------|
| H-A | `racing_tick_track1` stack-overlay collision with globals (SDCC overlay analysis misses the nested `for` + `UINT8 blocked[4]` arrays + many UINT8 locals; locals overlap WRAM globals) | SDCC for GB allocates locals via static overlay by default. Two nested for-loops in racing_tick with stacked UINT8s could exceed the analyzer's recall. The corruption happens during the FIRST racing_tick call. | Compile with `-Wf--no-overlay` (or equivalent) and re-test. If hang disappears → confirmed. |
| H-B | `SWITCH_ROM(2)` leak in `race_enter` + GBDK `set_bkg_tiles` interaction with SHOW_BKG=1 | Pre-fix (`SHOW_BKG=0`) the leak was inert. With SHOW_BKG=1 the PPU is actively reading VRAM during set_bkg_tiles; race window may corrupt sound DMA. | Add `SWITCH_ROM(1)` after `set_bkg_tiles` in race_enter codegen. Re-test. |
| H-C | Sound driver corrupts WRAM 0xC0xx region on first race-scene frame (NR52/NR50/NR51 init in main only; sound_driver_update writes WRAM-resident state) | The `_sound_*` globals live in the same WRAM page being corrupted. | Stub `sound_driver_update()` to no-op and re-test. |
| H-D | GBDK VBlank/STAT interrupt handler writes stack-relative garbage when SHOW_BKG is on but BGP is uninitialised | LCD interrupt vector handlers use the stack. If stack is misaligned the IRQ handler can write past it. | Set `BGP_REG = 0xE4` explicitly in main_init before SHOW_BKG. Re-test. |

## Recommended round-6 scope

1. **Diagnose** which hypothesis is correct (run the experiments in order H-A → H-D, each on its own throwaway branch).
2. **Lock** the chosen root cause as a JVM RED test (e.g. assert `_car_x == 80` two frames after race_enter on the embedded emulator).
3. **Fix** the codegen.
4. **Visual-evidence verify** SC-1/SC-3/SC-4 with PNG screenshots via the **watchdog-safe** MCP server (commit `eaf93a49`) — no more indefinite hangs blocking diagnosis.
5. Flip `UAT-racer.md` only after all three visual SCs pass.

## What's on `feat/d_and_d_gaps` now

- Plan 25 (commits 827ce37a, e01f12dd) — DIAGNOSIS.md + 11 evidence files
- Tracking update (a960dcd3) — Plan 25 marked complete in ROADMAP
- MCP watchdog (eaf93a49) — protects all future MCP runs from infinite hangs
- This file (round-6 follow-up note)

Plan 26's H-1 work is **not** on `feat/d_and_d_gaps` — the worktree branch was deleted.

## Why visual-evidence rule paid off

The Plan 25 diagnosis was correct per its own scope, and Plan 26's JVM-tier RED→GREEN test was a faithful contract lock. But the *runtime visual* tier surfaced a second-order regression that no JVM test could catch — exactly as the visual-evidence rule in `CLAUDE.md` warns. The hang was caught by the orchestrator attempting to capture the post-fix MCP screenshots before flipping UAT-racer.md to `passed`. Without the visual gate, this ships.
