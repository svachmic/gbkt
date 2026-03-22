# UAT: Breakout

**Game:** Breakout (BreakoutV2.kt)
**ROM:** `gbkt-examples/breakout/build/gbkt/output/breakout.gb`
**Scenes:** title → game → win / gameover → title
**Win condition:** Destroy all 30 bricks
**Lose condition:** Lose all 3 lives (ball falls below paddle)

## Automated Test (Coffee-GB headless)

Run via: `./gradlew :gbkt-examples:breakout:emulatorTest`

| Check | Expected | Result |
|-------|----------|--------|
| 600 frames without crash | No exceptions | |
| No ERROR log entries | Empty error list | |

## Scenario Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | Title screen displays on launch | "BREAKOUT" text, "PRESS START" prompt visible | | | | |
| 2 | START button transitions to game | Scene changes to game; sprites appear; "SCORE:0  LIVES:3" HUD shown; 3 rows of "#####" bricks drawn | | | | |
| 3 | Paddle moves left with D-pad LEFT | Paddle moves left; stops at left boundary (x=3) | | | | |
| 4 | Paddle moves right with D-pad RIGHT | Paddle moves right; stops at right boundary (x=136) | | | | |
| 5 | Paddle cannot go past left boundary | Paddle stays at x=3 when D-pad LEFT held at edge | | | | |
| 6 | Paddle cannot go past right boundary | Paddle stays at x=136 when D-pad RIGHT held at edge | | | | |
| 7 | Ball bounces off left wall | Ball dx reverses to +1 when ball.x < 4; hit sound plays | | | | |
| 8 | Ball bounces off right wall | Ball dx reverses to -1 when ball.x > 152; hit sound plays | | | | |
| 9 | Ball bounces off top wall | Ball dy reverses to +1 when ball.y < 16 (below HUD); hit sound plays | | | | |
| 10 | Ball bounces off paddle | Ball dy reverses to -1 on AABB overlap with paddle; hit sound plays | | | | |
| 11 | Bricks break on ball contact | Individual "#" character erased from background when ball contacts brick zone; bricksLeft decrements | | | | |
| 12 | Score increments per brick | score += 10 for each brick destroyed; HUD "SCORE:N" updates | | | | |
| 13 | Lives display updates on ball loss | When ball falls below paddle: "BALL LOST!" message shown, lives decrements, HUD "LIVES:N" updates | | | | |
| 14 | Ball resets after life lost | Ball resets to (80, 120) after life loss; paddle still controllable | | | | |
| 15 | Game over at zero lives | When lives reaches 0: navigate to gameover scene; "GAME OVER", "SCORE: N", "PRESS START" shown | | | | |
| 16 | Restart from game over | START on gameover returns to title scene | | | | |
| 17 | Win condition: all bricks cleared | When bricksLeft=0: win sound plays, navigate to win scene; "YOU WIN!", "SCORE: N", "PRESS START" shown | | | | |
| 18 | Restart from win screen | START on win screen returns to title scene | | | | |
| 19 | Edge case: ball at screen corners | Ball bounces correctly at corners; no stuck state | | | | |
| 20 | Edge case: paddle at screen edges | Paddle boundary clamp works correctly at both edges | | | | |
| 21 | HUD panel renders at top-left | SC: score and lives icons render on window layer; not corrupted by background tileset | | | | |

## Iteration Log

| Iteration | Date | Tester | Coffee-GB Result | mGBA Result | Issues Found |
|-----------|------|--------|-----------------|-------------|-------------|
| 1 | | | | | |

## Pass Criteria

All 21 scenarios pass in both Coffee-GB (headless) and mGBA columns before marking this game UAT-complete.
