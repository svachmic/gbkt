# UAT: Platformer GBC

**Game:** Platformer GBC (PlatformerGbc.kt)
**ROM:** `gbkt-examples/platformer-gbc/build/gbkt/output/platformer-gbc.gb`
**Target:** GBC_COMPATIBLE — runs in color mode on GBC; falls back gracefully on DMG hardware
**Scenes:** title → gameplay → win → title
**Win condition:** Player reaches goal zone (x:112–128, y:24–56) on top of high platform

## Automated Test (Coffee-GB headless, GBC mode)

Run via: `./gradlew :gbkt-examples:platformer-gbc:emulatorTest`

| Check | Expected | Result |
|-------|----------|--------|
| 600 frames without crash | No exceptions | |
| No ERROR log entries | Empty error list | |

## Scenario Checklist

### DMG Parity Scenarios (identical to DMG Platformer)

| # | Scenario | Expected | Coffee-GB (GBC) | mGBA | Attempt | Notes |
|---|----------|----------|-----------------|------|---------|-------|
| 1 | Title screen displays on launch | "PLATFORMER GBC" text, "PRESS START" prompt, "LIVES: 3" shown | | | | |
| 2 | START button transitions to gameplay | Scene changes from title to gameplay; player sprite appears at (20, 104) | | | | |
| 3 | D-pad LEFT moves player left | Player x decreases by 2 per frame while held | | | | |
| 4 | D-pad RIGHT moves player right | Player x increases by 2 per frame while held | | | | |
| 5 | Gravity pulls player down | Player falls when no ground below; velocity increases each frame up to terminal velocity 12 | | | | |
| 6 | Player lands on ground platform | Player stops falling when y reaches ground level (y≈120); no passing through | | | | |
| 7 | A button triggers jump sound | jumpSfx plays on A press | | | | |
| 8 | Player can reach mid platform (one-way) | Player can jump up onto mid_platform (x:40, y:88, width:48); platform is passable from below | | | | |
| 9 | Player can reach high platform (one-way) | Player can jump from mid_platform to high_platform (x:96, y:56, width:48) | | | | |
| 10 | Fall detection triggers life loss | When player.y > 136, lives decrements by 1; landSfx plays; player respawns at (20, 104) | | | | |
| 11 | Lives counter decrements correctly | After 3 falls, lives reaches 0 | | | | |
| 12 | Game over on last life lost | When lives == 0 after fall, navigates back to title scene | | | | |
| 13 | Goal zone triggers win | When player reaches x:112–128, y:24–56, navigates to win scene | | | | |
| 14 | Win scene displays correctly | "YOU WIN!" and "PRESS START" shown; winSfx plays on enter | | | | |
| 15 | Restart from win screen | START on win screen returns to title; lives reset to 3 | | | | |
| 16 | Coyote time works | Player can still jump 6 frames after walking off a platform edge | | | | |
| 17 | Jump buffering works | Jump input buffered for 8 frames before landing still triggers jump on landing | | | | |
| 18 | Terminal velocity is respected | Player fall speed never exceeds 12 pixels/frame | | | | |
| 19 | Player respawn resets position | After fall, player always respawns at (20, 104) regardless of where they fell from | | | | |
| 20 | Navigation cycle complete | title → gameplay → win → title cycle works without getting stuck | | | | |

### GBC-Specific Scenarios

| # | Scenario | Expected | Coffee-GB (GBC) | mGBA | Attempt | Notes |
|---|----------|----------|-----------------|------|---------|-------|
| 21 | GBC color mode active | ROM runs in GBC color mode (not grayscale DMG mode); status header shows GBC in mGBA | | | | |
| 22 | No grayscale fallback | Colors render correctly — no palette defaulting to 4 shades of gray as on DMG | | | | |
| 23 | Background tiles use correct palette | Background tilemap uses GBC color palette (not scrambled or wrong colors) | | | | |
| 24 | Sprite colors render correctly | Player sprite uses correct GBC sprite palette; no color corruption | | | | |
| 25 | No palette corruption on scene enter | Each scene transition (title→gameplay, gameplay→win, win→title) preserves correct palettes | | | | |
| 26 | No palette corruption on scene exit | Palette state is consistent after returning from win scene to title | | | | |
| 27 | Coffee-GB GBC mode flag active | Headless test runs with gbcMode=true in AgentSessionConfig; emulator initialized as GameboyType.CGB | | | | |
| 28 | GBC_COMPATIBLE backward compat flag | ROM header correctly sets bit in 0x143 (0x80 = GBC_COMPATIBLE); verified in mGBA ROM info | | | | |

## Emulator Compatibility Notes

**Coffee-GB GBC Support:**
Coffee-GB emulates Game Boy Color via `GameboyType.CGB` in the core. The gbkt framework sets this via
`AgentSessionConfig.gbcMode = true` → `EmulatorConfig.gbcMode = true` → `CoffeeGbEmulator.start()` uses
`GameboyType.CGB`. Coffee-GB may not render GBC palettes with pixel-perfect accuracy, but must:
- Not crash during 600 frames
- Not produce ERROR log entries
- Run the game ROM without exception

**mGBA:**
Primary reference for GBC color correctness. mGBA accurately emulates GBC palette loading.
Open: `gbkt-examples/platformer-gbc/build/gbkt/output/platformer-gbc.gb`
In mGBA: View → Game Boy → Confirm "Game Boy Color" mode is shown in status bar.

## Iteration Log

| Iteration | Date | Tester | Coffee-GB Result | mGBA Result | Issues Found |
|-----------|------|--------|-----------------|-------------|-------------|
| 1 | | | | | |

## Pass Criteria

- All 20 DMG parity scenarios pass in both Coffee-GB (GBC mode) and mGBA
- All 8 GBC-specific scenarios pass
- No palette corruption, scrambled tiles, or rendering artifacts observed
- `./gradlew build` passes (all JVM unit tests green)

## GBC vs DMG Comparison

| Aspect | DMG Platformer | GBC Platformer |
|--------|---------------|----------------|
| Target config | none (DMG only) | `target(GbcTarget.GBC_COMPATIBLE)` |
| Color palette | 4 shades of gray | GBC hardware palettes |
| Title text | "PLATFORMER" | "PLATFORMER GBC" |
| ROM flag (0x143) | 0x00 | 0x80 (GBC_COMPATIBLE) |
| Game logic | identical | identical |
| Physics | identical | identical |
| Scenes | identical | identical |
