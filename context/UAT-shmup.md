# UAT: Shmup

**Game:** Shmup — Vertical Scrolling Shoot-Em-Up (DMG)
**ROM:** `gbkt-examples/shmup/build/gbkt/output/shmup.gb`
**Source:** `gbkt-examples/shmup/src/main/kotlin/io/github/gbkt/examples/shmup/Shmup.kt`

## Legend

- Pass: checkmark or "PASS"
- Fail: "FAIL" with observed behavior
- N/A: not applicable for this emulator
- Attempt: iteration number (1 = first run, 2 = after first fix, etc.)

## Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | **Title screen displays** | "SHMUP", "SHOOT-EM-UP", "PRESS START" visible | | | | |
| 2 | **START begins game** | Press START transitions to gameplay scene | | | | |
| 3 | **Player ship appears** | Ship sprite visible at center-bottom (x=80, y=120) | | | | |
| 4 | **HUD shows score and lives** | "SC:0 LV:3" shown on gameplay screen | | | | |
| 5 | **D-pad up moves ship up** | Ship moves upward; clamped at y=8 (doesn't leave top area) | | | | |
| 6 | **D-pad down moves ship down** | Ship moves downward; clamped at y=128 | | | | |
| 7 | **D-pad left moves ship left** | Ship moves left; clamped at x=4 | | | | |
| 8 | **D-pad right moves ship right** | Ship moves right; clamped at x=140 | | | | |
| 9 | **A button fires bullet** | Bullet spawns at player position, shoot SFX plays | | | | |
| 10 | **Bullets move upward** | Active bullets travel upward at ~4px/frame | | | | |
| 11 | **Bullets despawn off-screen** | Bullet destroyed when reaching top of screen (y<4) | | | | |
| 12 | **Shoot cooldown (8 frames)** | Rapid A presses: bullet fires, then 8-frame delay before next | | | | |
| 13 | **Enemies spawn every 60 frames** | New enemy appears at top of screen approximately every second | | | | |
| 14 | **Enemies move downward** | Enemies descend at ~1px/frame from top to bottom | | | | |
| 15 | **Enemies despawn below screen** | Enemy destroyed when reaching y>144 | | | | |
| 16 | **Bullet-enemy collision destroys enemy** | When bullet hits enemy: explode SFX plays, score SFX plays | | | | |
| 17 | **Score increments on kill** | After bullet-enemy collision: score increases by 10 | | | | |
| 18 | **HUD updates after kill** | "SC:10 LV:3" (or current values) shown after first kill | | | | |
| 19 | **Enemy-player collision damages player** | Enemy touching player: hit SFX plays, lives decremented | | | | |
| 20 | **HUD updates after hit** | Lives count in HUD decreases after player is hit | | | | |
| 21 | **Zero lives triggers game over** | When lives=0, game navigates to gameover scene | | | | |
| 22 | **Game over screen displays** | "GAME OVER", "SCORE: X", "PRESS START" visible | | | | |
| 23 | **Score shown on game over** | Score value on gameover matches score earned during gameplay | | | | |
| 24 | **START on gameover returns to title** | Press START resets score/lives, navigates to title | | | | |
| 25 | **Entity pool: no sprite corruption** | No corrupted/flickering sprites when multiple bullets+enemies on screen | | | | |
| 26 | **Edge: rapid fire (many bullets)** | Hold A repeatedly: max 8 bullets active (pool limit), no crash | | | | |
| 27 | **Edge: many enemies on screen** | Max 4 enemies active simultaneously (pool limit), no crash | | | | |
| 28 | **Scroll simulation runs** | Background scroll variable increments each frame (visual effect active) | | | | |

## Headless Smoke Test (Coffee-GB)

Run: `./gradlew :gbkt-examples:shmup:test`

Smoke test: ROM boots, runs 600 frames without ERROR log entries. Screenshot captured at frame 300.

## Known Issues / Iteration Log

| Iteration | Issue Found | Fix Applied | Commit | Re-test Result |
|-----------|-------------|-------------|--------|----------------|
| — | — | — | — | — |
