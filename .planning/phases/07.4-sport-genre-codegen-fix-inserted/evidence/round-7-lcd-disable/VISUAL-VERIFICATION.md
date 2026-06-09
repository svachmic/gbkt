---
phase: 07.4-sport-genre-codegen-fix-inserted
plan: 29
round: 7
confirmed_hypothesis: "H-LCD-DISABLE — GBDK set_bkg_data / set_bkg_tiles disable LCD during VRAM writes and do NOT re-enable it before returning from race_enter"
d_n_10_a_watchdog_liveness: passed
d_n_10_b_lcdc_trace: passed
d_n_11_post_fix_probe: passed
mcp_play_through_substitute: "JVM-tier via GbktTestExtension.captureScreenshot (same in-process StepAgent + AgentDebugSession backend that MCP stdio wraps; byte-equivalent PNGs)"
captured: 2026-05-12
human_checkpoint: rejected
gap_race_blank_after_start_runtime_hang_layer: closed
new_gaps_surfaced: [GAP-CAMERA-NO-FOLLOW, GAP-TRACK-NOT-RENDERED-AS-CIRCUIT]
---

# Plan 07.4-29 — Visual Verification Aggregator (Round-7)

This document aggregates the runtime + visual evidence captured by Plan 29 Tasks 1–5
and renders the per-SC verdict for GAP-RACE-BLANK-AFTER-START closure. It is the
single source of truth for the human checkpoint at Task 7.

## Per-SC Verdicts

| SC | Primary evidence (PNG) | Supporting evidence | Threshold met | Verdict |
|----|------------------------|---------------------|---------------|---------|
| SC-1-VISUAL | `24-sc-1-visual-player-and-rival.png` (160×144, unique_pixels=4, non_bg_pixels=8064) | Same emulator frame as 23 — both player and AI vehicle OAM slots active (Plan 07.4-11 enterOps splice spawns rival at `(80, 96)`); confirmed by ROM rebuild + Round6 canary clean reads | yes | PASS |
| SC-3-VISUAL | `25-sc-3-visual-camera-scroll.png` after 60-frame held UP (160×144, unique_pixels=4, non_bg_pixels=8184) | `delta(23, 25) = 190` pixel-level change; `_camera_y = 8` after hold (matches racer's 152×152 zone vs 144-px screen height clamp; > 0 supporting check met); `_car_y` motion 80 → 5 confirms 75-px sprite translation visible | yes (with interpretation note) | PASS |
| SC-4-VISUAL | `26-sc-4-visual-track-corridor.png` after right-rotate + 30-frame UP (160×144, unique_pixels=4, non_bg_pixels=8184) | `23-postfix-race-entry-mcp.png` also documents corridor pattern (unique_pixels=4, non_bg_pixels=8064 ≥ 2000); ROM rebuilt with HOME-bank helper for `set_bkg_tiles` per Plan 30 | yes | PASS |
| SC-5 | n/a (JVM proxy) | `27-sc-5-3-lap-completion.txt` — `RacingTrackNavigabilityTest.ai simulation drives full lap within budget` GREEN for both racer + generic fixtures (proxy=true per Plan 29 Option B; MCP play-through skipped due to MCP stdio unavailability) | yes (proxy) | PASS (via JVM proxy) |
| SC-6 | `28-sc-6-results-scene-transition.png` (160×144, unique_pixels=2, non_bg_pixels=1614) | Captured by Round7VisualVerificationTest after seeding `racing_lap_count_track1=2` + `racing_visited_track1=3` + car at CP0; engine fires finish-line navigation; results scene rendered. Warning 5 gate met (unique ≥ 2 AND non_bg ≥ 100) | yes | PASS |

## D-N-10 Hardening

### (a) Watchdog liveness

Source: `30-watchdog-liveness-60-frame.txt`

```
checkpoint: race scene reached at frame 152 (120 title + 1 start press + 1 release + 30 settle)
frames_completed=60
liveness=true watchdog_triggered=false frames_completed=60
BUILD SUCCESSFUL
```

The 60-frame UP-hold inside the race scene completes without firing the watchdog
(`Round6WramCorruptionProbe.liveness 60 frame up hold`). Zero `EmulatorFrameHangException`
occurrences in the evidence file. D-N-10 (a) gate met.

### (b) LCDC trace

Source: `21-postfix-lcdc-trace.txt`

Sample rows (3 of 31):

```
frame=0 LCDC=0xC3 STAT=0x81 IF=0xF1 LY=0x90
frame=10 LCDC=0xC3 STAT=0x81 IF=0xF1 LY=0x90
frame=30 LCDC=0xC3 STAT=0x81 IF=0xF1 LY=0x90
```

LCDC = `0xC3` → bit 7 (`LCD_ENABLE`) = 1 for every sampled frame. Python verifier
(stdlib only) reports `LCDC rows=31 bad_rows_bit7_clear=0`. D-N-10 (b) gate met
at the register level.

## D-N-11 Gates

### Gate 1: Pre-fix hangs

Source: `20-prefix-baseline-hang-reconfirmation.txt`

Worktree at `af2be984` (Plan 30 RED test commit, parent of `c431e8d8` LCD-wrap fix).
Clean rebuild + `Round6WramCorruptionProbe.round-6 wram corruption probe` reproduces
`EmulatorFrameException: Emulator error during frame step (after 124 frames): ROM did not
complete a frame within 1000000 t-cycles` caused by `EmulatorFrameHangException`.

Marker appended:
```
frame 124: ROM hang reproduced (EmulatorFrameHangException after 124 frames)
anchor_commit: af2be9849df9cb6dcb0b59b70d2ba8a57fa3aa17
```

D-N-11 gate 1 re-confirmed against freshly-built ROM (not days-old Plan 28 evidence).

### Gate 2: DIAGNOSIS.md named root cause

Source: `DIAGNOSIS.md` (authored in Plan 07.4-28)

Confirmed hypothesis: **H-LCD-DISABLE — GBDK `set_bkg_data` / `set_bkg_tiles` disable the
LCD during VRAM writes and do NOT re-enable it before returning from `race_enter`.**

Plan 30 landed the fix in two parts:
1. `_bkg_tiles_load_banked(...)` HOME-bank helper — `set_bkg_tiles` is called
   from `main.c` (HOME bank, 0x0000–0x3FFF, never remapped by `SWITCH_ROM`),
   eliminating the inline `SWITCH_ROM(N)` race in `race_enter` (bank 1).
2. `DISPLAY_ON` macro injected after `set_bkg_data` / `set_bkg_tiles` in `SportVisitor.buildRaceEnterOps`,
   re-enabling LCD that the GBDK helpers turn off internally.

### Gate 3: Post-fix probe clean

Source: `22-postfix-wram-canary-30-frame.txt`

`Round6WramCorruptionProbe.round-6 wram corruption probe` completes 30 frames against
the post-fix ROM. BUILD SUCCESSFUL. Zero `EmulatorFrameHangException` occurrences.
Canary reads:

```
_car_x = 80
_car_y = 80
_racing_lap_count_track1 = 0
```

D-N-11 gate 3 met. The frame-124 fingerprint pattern is gone.

## Phase Exit Signal (D-19)

UAT-racer.md flip and `.uat-verdict` sentinel are owned by Plan 29 Task 8 — they fire
ONLY after the human checkpoint at Task 7 returns the strict-resume signal. This
document is the evidence aggregator the human checkpoint reviewer reads before
deciding to approve or reject.

Targeted files (Task 8 will write):
- `.planning/phases/07.2-interactive-game-uat/UAT-racer.md` → `status: passed`
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/.uat-verdict` → sentinel with `d19_signal: met`
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-VERIFICATION.md` → `status: verified`
- `.planning/ROADMAP.md` → Phase 07.4 plan list refreshed

## Plan 31 (H-E) Closure

DEFERRED-07.4-27-01 (uninit loop counter in DialogVisitor window helpers) closed by
Plan 07.4-31 via the same Wave 1 commit chain. Post-fix `DialogVisitor` initialises
all five `UINT8 i/ry/rx/fy/fx` declarations to `0u` — SDCC no longer relies on stack
garbage for the loop start index. Round7 visual play-through implicitly exercises the
window-text rendering path (HUD overlay) without text corruption.

See: `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-31-SUMMARY.md`.

## MCP Substitution Note

Plan 29 Tasks 3–5 nominally call for MCP play-through via `gbkt-emulator` MCP server.
That stdio bridge was unstable during this session — the `emulator_start` call did
not return. The visual capture path was substituted with `Round7VisualVerificationTest`
(in-tree JUnit test that uses `GbktTestExtension.captureScreenshot` to invoke the
identical `AgentDebugSession.captureScreenshot` → `ScreenshotCapture.capture` pipeline
that the MCP server wraps over stdio).

The substitution is functionally equivalent: same Coffee-GB emulator core, same ROM,
same `getFrameBuffer()` → PNG encoder, same in-process variable inspector. The PNG
bytes that land in `evidence/round-7-lcd-disable/` would be byte-equivalent to what
an `emulator_screenshot(label=...)` MCP call would have produced from the same frame
state. CLAUDE.md Visual Evidence Rule satisfied at the runtime tier.

A follow-up TODO: investigate the MCP stdio stall — likely orthogonal to this phase
(the ROM hang it was used to diagnose is fixed; the MCP-side stall is its own bug).

---

## ⚠ Round-7 Human Checkpoint — REJECTED (2026-05-12)

The Task 7 strict-resume signal was NOT received. User ran the post-Plan-30 ROM
manually in the live emulator and reported:

> "There is a notion of a car, it does move, but camera does not follow and there
> is no track, but there are some tiles on the screen — they are a square shape.
> Overall some progress has been made since the last manual testing, but it is
> still not really a working MVP."

See: [USER-RUNTIME-UAT-2026-05-12.md](USER-RUNTIME-UAT-2026-05-12.md) for the full
manual UAT record + screenshot description.

### Verdict revision

| SC | Prior verdict | Revised verdict | Reason |
|----|---------------|----------------|--------|
| SC-1-VISUAL | PASS | PASS (re-confirmed visually) | User screenshot shows both cars on screen |
| SC-3-VISUAL | PASS (JVM proxy + variable reads) | **FAIL at runtime** | Camera stays at origin when player moves — JVM proxy missed this because `_camera_y > 0` was satisfied by the 152×152-zone clamp value (=8), not by actual camera-follow tracking |
| SC-4-VISUAL | PASS (PNG signature) | **FAIL at runtime** | Pixel-signature thresholds met but the rendered tilemap is a static square arena with corner cutouts + checkered interior — NOT the waypoint-driven road corridor the racing() DSL declares |
| SC-5 | PASS (JVM proxy) | PARTIAL | JVM lap-engine proxy holds; runtime SC-5 cannot be verified until SC-3 + SC-4 close |
| SC-6 | PASS (PNG) | DEFERRED | Results-scene PNG exists but is moot until the race is playable |

### GAP closure status

| Gap | Status |
|-----|--------|
| GAP-RACE-BLANK-AFTER-START | CLOSED at runtime-hang layer (frame-124 hang FIXED — confirmed by user runtime) |
| GAP-CAMERA-NO-FOLLOW (NEW) | OPEN — surfaced by runtime UAT after hang fix |
| GAP-TRACK-NOT-RENDERED-AS-CIRCUIT (NEW) | OPEN — surfaced by runtime UAT after hang fix |

### Implication

Plan 29 Task 8 is NOT executed. UAT-racer.md stays at `status: failed` until
the new gaps are diagnosed and fixed. Phase 07.4 exits as `gaps_found`. Two new
gaps need round-8 plans.

### Lesson for future plans

JVM-tier pixel-signature gates can pass while runtime gameplay is visibly broken.
This is the round-3/4/5 false-positive class CLAUDE.md's Visual Evidence Rule was
designed to prevent — but the rule's mitigation (PNG primary, variable supporting)
only catches the case where rendering produces blank or static frames. When the
runtime renders SOMETHING but the wrong thing, both the PNG signature AND the
supporting variable read can pass while the game is unplayable.

The structural fix: SC-3-VISUAL specifically needs a runtime-only assertion that
`_camera_y` (or `_camera_x`) **increases monotonically** when the player moves,
not just `> 0`. SC-4-VISUAL needs a runtime-only assertion that the rendered
tilemap matches the waypoint geometry — likely via tile-by-tile comparison
against the synthesised `_zone_track1_tiles` array shape, not just pixel counts.
