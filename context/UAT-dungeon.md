# UAT: Dungeon

**Game:** Dungeon (Dungeon.kt)
**ROM:** `gbkt-examples/dungeon/build/gbkt/output/dungeon.gb`
**Scenes:** title → gameplay ↔ battle → gameover → title
**Win condition:** Survive as long as possible (infinite torch-crawl loop)
**Lose conditions:** torchLevel reaches 0 OR falls below 50 threshold (gameover via onLow/onDepleted)

## Automated Test (Coffee-GB headless)

Run via: `./gradlew :gbkt-examples:dungeon:emulatorTest`

| Check | Expected | Result |
|-------|----------|--------|
| 600 frames without crash | No exceptions | |
| No ERROR log entries | Empty error list | |

## Scenario Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | Title screen displays on launch | "DUNGEON" text centered (col 6), "A TORCH CRAWLER" subtitle, "PRESS START" prompt visible | | | | |
| 2 | START resets state and enters gameplay | torchLevel=255, keys=0, steps=0 reset; scene transitions to gameplay; player appears at (64,64) | | | | |
| 3 | HUD renders in gameplay | Torch number "T:255" and key icons (0/9) render on window layer at top-left | | | | |
| 4 | Grid-based D-pad movement (8px per step) | Player moves exactly 8 pixels per frame while D-pad held (not 2px — grid movement); boundary checked | | | | |
| 5 | D-pad UP moves player up (8px) | Player y decreases by 8 while D-pad UP held; stops when player.y <= 16 | | | | |
| 6 | D-pad DOWN moves player down (8px) | Player y increases by 8 while D-pad DOWN held; stops when player.y >= 128 | | | | |
| 7 | D-pad LEFT moves player left (8px) | Player x decreases by 8 while D-pad LEFT held; stops when player.x <= 8 | | | | |
| 8 | D-pad RIGHT moves player right (8px) | Player x increases by 8 while D-pad RIGHT held; stops when player.x >= 152 | | | | |
| 9 | Step SFX plays on each movement step | stepSfx (BEEP) plays via playSound() each time onStep fires | | | | |
| 10 | Bump SFX plays when movement is blocked | bumpSfx (BUMP) plays via playSound() each time onBlocked fires | | | | |
| 11 | Step counter increments on movement | steps += 1 each frame any D-pad direction is held | | | | |
| 12 | Torch depletion every 4 steps | torchLevel -= 1 when steps > 0 AND steps & 3 == 0 AND torchLevel > 0 | | | | |
| 13 | Torch HUD number updates | HUD "T:N" reflects decremented torchLevel value in real time | | | | |
| 14 | Torch-low warning dialog at torchLevel=50 | Dialog "Torch dimming..." shown when torchLevel hits exactly 50 | | | | |
| 15 | Torch-low navigates to gameover | onLow(50) callback: navigate(gameoverRef); scene transitions to gameover | | | | |
| 16 | Torch-out navigates to gameover | onDepleted callback: navigate(gameoverRef) when torchLevel reaches 0; "Your torch burns out!" dialog shown | | | | |
| 17 | Random encounter triggers at steps >= 120 | Scene changes to battle scene after 120 steps; steps resets to 0; "ENCOUNTER!" printed | | | | |
| 18 | Hit SFX plays on battle entry | hitSfx plays in battle scene enter; 30-frame delay before battle begins | | | | |
| 19 | Battle scene runs battleUpdate each frame | Combat state machine driven by battleUpdate(combat) every frame in battle scene | | | | |
| 20 | Battle victory returns to gameplay | onVictory: navigate(gameplayRef); scene returns to gameplay after combat resolves | | | | |
| 21 | Battle defeat navigates to gameover | onDefeat: navigate(gameoverRef); gameover scene shown on player defeat | | | | |
| 22 | Game over screen displays correctly | "GAME OVER", "TORCH EXPIRED", "PRESS START" render on window layer | | | | |
| 23 | Game over START resets and returns to title | State reset (torchLevel=255, keys=0, steps=0) then navigate(titleRef) | | | | |
| 24 | HUD hides on gameplay exit | gameHud.hide() fires in gameplay exit; no stale HUD in battle/gameover scenes | | | | |
| 25 | Zone encounters: safeSteps = 10 before first encounter | No encounter within first 10 steps in floor1 zone | | | | |
| 26 | Dungeon flags registered | flags("dungeon\_flags") with bossDefeated/gotTreasure/foundKey pages registered without crash | | | | |
| 27 | Save system available | saveData("dungeon\_save") slot-1 system registered; no crash on gameplay entry | | | | |
| 28 | Camera smooth follow registered | camera { smoothing = 0.2f } registered; no crash | | | | |
| 29 | Edge case: encounter at steps=120 (not 119) | No battle at steps=119; battle triggers exactly at steps >= 120 | | | | |
| 30 | Edge case: torch transition from 51 to 50 | Warning dialog fires when torchLevel first equals 50; not before, not after | | | | |

## Iteration Log

| Iteration | Date | Tester | Coffee-GB Result | mGBA Result | Issues Found |
|-----------|------|--------|-----------------|-------------|-------------|
| 1 | | | | | |

## Pass Criteria

All 30 scenarios pass in both Coffee-GB (headless) and mGBA columns before marking this game UAT-complete.
