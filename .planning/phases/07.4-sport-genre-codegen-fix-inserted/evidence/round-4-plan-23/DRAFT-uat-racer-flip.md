# DRAFT: UAT-racer.md frontmatter flip (NOT YET APPLIED)

This file documents the proposed flip to `.planning/phases/07.2-interactive-game-uat/UAT-racer.md`.
DO NOT apply until the human verdict is delivered (see resume-signal rules in
MCP-VERIFICATION-INSTRUCTIONS.md).

## Proposed frontmatter changes

```diff
 ---
 game: racer
-status: failed
+status: passed                       # OR: passed-with-caveats (if SC-2/SC-3 only)
 tester: claude-agent
-session_date: 2026-05-09
+session_date: 2026-05-11
 prior_session_date: 2026-05-08
 rom: gbkt-examples/racer/build/gbkt/output/racer.gb
 metadata: gbkt-examples/racer/build/gbkt/generated/game_metadata.json
 symbols: gbkt-examples/racer/build/gbkt/output/racer.noi
 target: GBC_COMPATIBLE
-verdict_reason: phase-07.4-round-3-navigability-corner-trap-blocks-manual-traversal
+verdict_reason: phase-07.4-round-4-plan-23-mcp-visual-confirmation-2026-05-11
 fix_phase_required: true
 phase_07_4_status: gaps_found
 re_verification:
-  round: 3
+  round: 4
-  previous_status: failed
-  previous_score: "3/9 must-haves (round 1)"
+  previous_status: failed
+  previous_score: "3/9 must-haves (round 1) → 7/9 (round 2) → 7/9 (round 3) → CLOSED (round 4)"
   gaps_closed_round_2: [GAP-A, GAP-B, GAP-C, GAP-D, SC-1, SC-4, TRACK-NAVIGABILITY (AI-tier), TILESET-VISUAL-CONTRAST]
   gaps_closed_round_3: []
+  gaps_closed_round_4: [SC-1, SC-4, SC-5, SC-6, GAP-RACE-BG-WIPE, GAP-RACE-BG-PRINT, SECONDARY-CAMERA-BOUNDS-UNDERFLOW, SECONDARY-CROSS-BANK-CONST-DATA-ACCESS, NAVIGABILITY-PLAYER-CORNER-TRAP]
-  gaps_remaining: [SC-5-PLAYER-TRAVERSAL, SC-6-3-LAP-COMPLETION, NAVIGABILITY-PLAYER-CORNER-TRAP]
+  gaps_remaining: []                  # OR: [SC-2 caveat] / [SC-3 caveat] depending on verdict
   regressions: []
 ---
```

## Proposed appended section

A new "## Round 4 Re-verification (2026-05-11 — after Plans 19-22 + this MCP play-through)"
section will list each SC's verdict with the corresponding screenshot file from
`evidence/round-4-plan-23/`. Template:

```markdown
## Round 4 Re-verification (2026-05-11)

Driven by `/gsd-execute-phase 07.4` Plan 07.4-23 (this plan). MCP play-through against the
ROM rebuilt at HEAD post-Plans-19+20+21+22 (timestamp 2026-05-11 09:39).

### Verdict per SC (with visual evidence)

| SC | Status | Screenshot | Variable evidence |
|----|--------|------------|-------------------|
| SC-1 (AI rival visible + motion) | <PASS/FAIL> | `evidence/round-4-plan-23/01-race-entry.png`, `04-ai-moved.png` | `_pool_carAi_active=1`, `_pool_carAi_wp_idx` advances |
| SC-2 (stats drive movement) | <PASS/FAIL/CAVEAT> | `02-after-hold-up.png` | `_vehicle_carPlayer_speed_cur` ramps |
| SC-3 (camera follows) | <PASS/FAIL/CAVEAT> | `03-camera-mid-zone.png` | `_camera_x`/`_camera_y` advance |
| SC-4 (track tilemap visible) | <PASS/FAIL> | `01-race-entry.png` | `_current_tileset_id=1` (was 255) |
| SC-5 (waypoint lap detection) | <PASS/FAIL> | `05-lap-1-complete.png` | `_racing_visited_track1` bits flip in order; `_racing_lap_count_track1=1` |
| SC-6 (3-lap race + results) | <PASS/FAIL> | `06-results-scene.png` | `_racing_lap_count_track1=3`; results scene reached |

### Closures locked at MCP tier

- BG-wipe (Plan 20 scene-aware ScreenClear) confirmed visually: track corridor visible on
  BG layer; LAP HUD on window layer; no BG corruption.
- BG-print (Plan 20 scene-aware PrintOp) confirmed: LAP text rendered cleanly via
  `_win_print_at`; no `gotoxy/printf` BG-tile corruption.
- Camera-bounds underflow (Plan 21) confirmed: SCX/SCY remain in valid range; no `=248`
  wraparound; track stays anchored on screen during scrolling.
- Cross-bank const data access (Plan 22) confirmed: `_zone_track1_tiles` (bank 2) reads
  correctly via emitted `SWITCH_ROM(2)` guard; track tilemap displays as designed (not
  garbage from bank 1's 0x4000–0x7FFF region).
- Navigability corner-trap: <pending — see SC-5 verdict; if SC-5 reached lap 1 via natural
  cardinal-dpad play, the corner trap is closed.>
```
