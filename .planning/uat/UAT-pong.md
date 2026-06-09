# UAT: Pong

**Game:** Pong (PongV2.kt)
**ROM:** `gbkt-examples/pong/build/gbkt/output/pong.gb`
**Scenes:** title → game → gameover → title
**Win condition:** First player to 5 points

## Automated Test (Coffee-GB headless)

Run via: `./gradlew :gbkt-examples:pong:emulatorTest`

| Check | Expected | Result |
|-------|----------|--------|
| 600 frames without crash | No exceptions | |
| No ERROR log entries | Empty error list | |

## Scenario Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | Title screen displays on launch | "PONG" text centered, "PRESS START" prompt, "FIRST TO 5" line | | | | |
| 2 | START button transitions to game | Scene changes from title to game; sprites appear; score header "P1:0    P2:0" shown | | | | |
| 3 | Left paddle moves up with D-pad UP | Paddle 1 (left side) moves upward; stops at top boundary (y=16) | | | | |
| 4 | Left paddle moves down with D-pad DOWN | Paddle 1 moves downward; stops at bottom boundary (y=112) | | | | |
| 5 | Left paddle cannot go above top boundary | Paddle 1 stays at y=16 when D-pad UP held at edge | | | | |
| 6 | Left paddle cannot go below bottom boundary | Paddle 1 stays at y=112 when D-pad DOWN held at edge | | | | |
| 7 | Right paddle (AI) tracks ball | Paddle 2 moves toward ball position; tracks ball center at y+8 | | | | |
| 8 | Ball bounces off top wall | Ball direction reverses (dy=1) when reaching y<16; hit sound plays | | | | |
| 9 | Ball bounces off bottom wall | Ball direction reverses (dy=-1) when reaching y>120; hit sound plays | | | | |
| 10 | Ball bounces off left paddle | Ball dx reverses to +1 when within paddle zone (x:4-20, y overlap); hit sound plays | | | | |
| 11 | Ball bounces off right paddle | Ball dx reverses to -1 when within paddle zone (x:148-156, y overlap); hit sound plays | | | | |
| 12 | P2 scores when ball exits left | p2Score increments; "SCORE!" flash shown; ball resets to center (80,72); score header updates | | | | |
| 13 | P1 scores when ball exits right | p1Score increments; "SCORE!" flash shown; ball resets to center (80,72); score header updates | | | | |
| 14 | Score display updates correctly | Score header shows correct values after each point; no stale display | | | | |
| 15 | Win condition at 5 points (P1) | Win sound plays; scene navigates to gameover; "GAME OVER" + scores shown | | | | |
| 16 | Win condition at 5 points (P2) | Win sound plays; scene navigates to gameover; "GAME OVER" + scores shown | | | | |
| 17 | Game over screen shows scores | "GAME OVER", "P1:N P2:N" score display, "PRESS START" prompt all visible | | | | |
| 18 | Restart from game over | START button on gameover resets scores to 0 and returns to title scene | | | | |
| 19 | Edge case: ball at screen corners | Ball correctly bounces at corners (both x and y reverse); no stuck state | | | | |
| 20 | Edge case: ball travels at full speed continuously | Ball moves every frame without freezing or teleporting | | | | |

## Iteration Log

| Iteration | Date | Tester | Coffee-GB Result | mGBA Result | Issues Found |
|-----------|------|--------|-----------------|-------------|-------------|
| 1 | | | | | |

## Pass Criteria

All 20 scenarios pass in both Coffee-GB (headless) and mGBA columns before marking this game UAT-complete.
