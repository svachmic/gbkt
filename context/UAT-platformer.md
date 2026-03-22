# UAT: Platformer

**Game:** Platformer (DMG)
**ROM:** `gbkt-examples/platformer/build/gbkt/output/platformer.gb`
**Source:** `gbkt-examples/platformer/src/main/kotlin/io/github/gbkt/examples/platformer/Platformer.kt`

## Legend

- Pass: checkmark or "PASS"
- Fail: "FAIL" with observed behavior
- N/A: not applicable for this emulator
- Attempt: iteration number (1 = first run, 2 = after first fix, etc.)

## Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | **Title screen displays** | "PLATFORMER" text and "PRESS START" prompt visible | | | | |
| 2 | **Lives shown on title** | "LIVES: 3" shown on title screen | | | | |
| 3 | **START begins game** | Press START on title screen transitions to gameplay scene | | | | |
| 4 | **Player appears at spawn** | Player sprite visible at x=20, y=104 (near bottom-left) | | | | |
| 5 | **D-pad left moves player left** | Player moves left when D-pad left held | | | | |
| 6 | **D-pad right moves player right** | Player moves right when D-pad right held | | | | |
| 7 | **Gravity pulls player down** | Without platform support, player falls downward | | | | |
| 8 | **A button triggers jump sound** | Jump SFX plays on A press (physics system handles velocity) | | | | |
| 9 | **Landing on ground stops fall** | Player lands on ground platform (y=120) and stops falling | | | | |
| 10 | **One-way mid platform (y=88)** | Player can jump through from below; lands on top when falling | | | | |
| 11 | **One-way high platform (y=56)** | Player can jump through from below; lands on top when falling | | | | |
| 12 | **Falling off screen loses life** | Player below y=136 triggers: lives-=1, bump SFX, respawn at (20,104) | | | | |
| 13 | **Lives counter decrements** | After falling, lives count decreases by 1 | | | | |
| 14 | **Zero lives returns to title** | When lives=0, game navigates back to title screen | | | | |
| 15 | **Goal zone triggers win** | Reaching x=112..128, y=24..56 triggers win scene navigation | | | | |
| 16 | **Win scene displays** | "YOU WIN!" and "PRESS START" text visible on win scene | | | | |
| 17 | **Win SFX plays** | Win sound effect plays on entering win scene | | | | |
| 18 | **START on win returns to title** | Press START on win scene returns to title | | | | |
| 19 | **Edge: jump near ceiling** | Player cannot exit screen from top (physics terminal velocity applies) | | | | |
| 20 | **Edge: walk to screen edges** | Player can move to left and right edges without crashing | | | | |
| 21 | **No sprite corruption** | No flickering or corrupted sprites during gameplay | | | | |
| 22 | **Smooth movement** | Player movement feels responsive, 2px/frame left/right | | | | |

## Headless Smoke Test (Coffee-GB)

Run: `./gradlew :gbkt-examples:platformer:test`

Smoke test: ROM boots, runs 600 frames without ERROR log entries. Screenshot captured at frame 300.

## Known Issues / Iteration Log

| Iteration | Issue Found | Fix Applied | Commit | Re-test Result |
|-----------|-------------|-------------|--------|----------------|
| — | — | — | — | — |
