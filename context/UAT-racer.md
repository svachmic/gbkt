# UAT: Racer

**Game:** Racer — Top-Down Circuit Racing (GBC Compatible)
**ROM:** `gbkt-examples/racer/build/gbkt/output/racer.gb`
**Source:** `gbkt-examples/racer/src/main/kotlin/io/github/gbkt/examples/racer/Racer.kt`

## Legend

- Pass: checkmark or "PASS"
- Fail: "FAIL" with observed behavior
- N/A: not applicable for this emulator
- Attempt: iteration number (1 = first run, 2 = after first fix, etc.)

## Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | **Title screen displays** | "RACER", "TOP-DOWN RACING", "PRESS START", "3 LAPS TO WIN" visible | | | | |
| 2 | **START begins race** | Press START transitions to race scene | | | | |
| 3 | **Car appears at start** | Car sprite visible at (40, 100) | | | | |
| 4 | **LAP counter shown** | "LAP: 0" shown on race HUD at top of screen | | | | |
| 5 | **D-pad up accelerates forward** | Car moves upward (negative y), engine SFX plays | | | | |
| 6 | **D-pad down brakes/reverses** | Car moves downward (positive y), engine SFX plays | | | | |
| 7 | **D-pad left steers left** | Car moves left, turn SFX plays | | | | |
| 8 | **D-pad right steers right** | Car moves right, turn SFX plays | | | | |
| 9 | **Smooth movement controller** | Car movement feels fluid (acceleration=1, friction=1) | | | | |
| 10 | **Camera follows car** | Camera smoothly follows car with 0.3 smoothing factor | | | | |
| 11 | **Camera bounded to 256x256 world** | Camera does not scroll beyond world boundaries | | | | |
| 12 | **Lap detection zone** | Driving through start/finish zone (x<50, y=95..115) increments lap | | | | |
| 13 | **LAP counter increments** | After crossing start/finish: "LAP: 1" displayed | | | | |
| 14 | **Lap SFX plays** | Coin sound plays when lap increments | | | | |
| 15 | **Race timer increments** | raceTime variable increments each frame during race | | | | |
| 16 | **3 laps triggers finish** | After 3 laps: win SFX plays, race navigates to results scene | | | | |
| 17 | **Results screen displays** | "RACE COMPLETE", "POSITION: 1", "TIME: X", "PRESS START" visible | | | | |
| 18 | **Results show correct time** | Race time shown matches actual elapsed frames | | | | |
| 19 | **START on results returns to title** | Press START resets lap/time/position, navigates to title | | | | |
| 20 | **GBC color rendering** | On GBC/GBC-mode emulator: track uses color palettes (not monochrome) | | | | |
| 21 | **DMG fallback** | ROM also runs on DMG mode without crashing (GBC_COMPATIBLE target) | | | | |
| 22 | **Edge: car at screen edges** | Car can drive to any position within 256x256 world bounds | | | | |
| 23 | **No sprite corruption** | No flickering or corrupted car sprite during racing | | | | |
| 24 | **AI opponent defined** | Racing system includes AI vehicle (Rival) with rubber-band at 85% speed | | | | |

## Headless Smoke Test (Coffee-GB)

Run: `./gradlew :gbkt-examples:racer:test`

Smoke test: ROM boots, runs 600 frames without ERROR log entries. Screenshot captured at frame 300.

## Known Issues / Iteration Log

| Iteration | Issue Found | Fix Applied | Commit | Re-test Result |
|-----------|-------------|-------------|--------|----------------|
| — | — | — | — | — |
