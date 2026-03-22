# UAT: Explorer

**Game:** Explorer (ExplorerV2.kt)
**ROM:** `gbkt-examples/explorer/build/gbkt/output/explorer.gb`
**Scenes:** title → gameplay ↔ pause, gameplay → combat\_scene → gameover → title
**Win condition:** Survive as long as possible (no explicit win — exploration loop)
**Lose conditions:** torchLevel reaches 0 (torch burns out), HP drops to 0 in combat

## Automated Test (Coffee-GB headless)

Run via: `./gradlew :gbkt-examples:explorer:emulatorTest`

| Check | Expected | Result |
|-------|----------|--------|
| 600 frames without crash | No exceptions | |
| No ERROR log entries | Empty error list | |

## Scenario Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | Title screen displays on launch | "EXPLORER" text centered (col 6), "A DUNGEON CRAWL" subtitle, "PRESS START" prompt visible | | | | |
| 2 | START button transitions to gameplay | State resets (hp=20, torchLevel=100, stepCount=0, keys=0, level=1); scene changes to gameplay; player sprite appears at (80,72) | | | | |
| 3 | HUD renders at top-left on gameplay entry | HP bar (5 tiles), torch number "T:100", key icons (0/5) render on window layer without corrupting background | | | | |
| 4 | D-pad UP moves player upward | Player y decreases by 2 per frame while D-pad UP held; stops when player.y <= 16 | | | | |
| 5 | D-pad DOWN moves player downward | Player y increases by 2 per frame while D-pad DOWN held; stops when player.y >= 136 | | | | |
| 6 | D-pad LEFT moves player leftward | Player x decreases by 2 per frame while D-pad LEFT held; stops when player.x <= 8 | | | | |
| 7 | D-pad RIGHT moves player rightward | Player x increases by 2 per frame while D-pad RIGHT held; stops when player.x >= 152 | | | | |
| 8 | Movement into walls is blocked at boundaries | Player cannot move past boundary edges (x<8, x>152, y<16, y>136) | | | | |
| 9 | Step counter increments on movement | stepCount += 1 each frame any D-pad direction is held | | | | |
| 10 | Torch gauge depletes during movement | torchLevel decrements by 1 when stepCount > 0 AND stepCount & 3 == 0 AND torchLevel > 0 (every 4 steps) | | | | |
| 11 | Torch level displayed in HUD updates | HUD torch number reflects current torchLevel after depletion | | | | |
| 12 | Torch-out warning dialog shows when torchLevel=0 | Dialog "Your torch burns out..." appears on window layer at bottom of screen (y=12) | | | | |
| 13 | Torch-out navigates to gameover | After torch-out dialog, scene transitions to gameover scene | | | | |
| 14 | Random encounter triggers at stepCount >= 120 | Scene changes to combat\_scene after 120 cumulative steps; stepCount resets to 0 | | | | |
| 15 | Combat scene shows encounter text | "GOBLIN APPEARS!", attack/damage messages, "Victory! +5 exp" render correctly (window layer, no tile corruption) | | | | |
| 16 | Combat: HP changes correctly | HP decremented by 3 (or set to 0 if hp <= 3) during combat animation | | | | |
| 17 | Combat: HP=0 navigates to gameover | After combat, whenever(hp isEqualTo 0) sends to gameover scene | | | | |
| 18 | Combat: returns to gameplay after battle | After combat animation + battleUpdate(), scene returns to gameplay | | | | |
| 19 | Pause menu opens with START | START in gameplay navigates to pause scene; "Resume" and "Quit to Title" items shown on window layer | | | | |
| 20 | Pause menu: Resume returns to gameplay | Selecting "Resume" navigates back to gameplay; HUD reappears | | | | |
| 21 | Pause menu: Quit to Title works | Selecting "Quit to Title" (or pressing B) navigates to title scene | | | | |
| 22 | HUD hides on gameplay exit | gameHud.hide() fires on gameplay exit; no stale HUD visible in pause/combat/gameover scenes | | | | |
| 23 | Game over screen displays correctly | "GAME OVER", "LEVEL: N", "PRESS START" all render on window layer without corruption | | | | |
| 24 | START on game over returns to title | buttons.start.pressed on gameover navigates to title scene | | | | |
| 25 | Entity collision blocks player at NPC/door | Player cannot pass through entities; onBlocked callback navigates back to gameplay | | | | |
| 26 | Floor transition (zone east edge) | Player walking to east edge of floor1 transitions to floor2 (Boss Chamber zone) | | | | |
| 27 | Floor2 is a safe zone (no encounters) | No random encounter triggers while in floor2 (safeZone flag active) | | | | |
| 28 | Edge case: stepCount at 119 — no encounter yet | No combat scene triggered at stepCount=119; encounter triggers only at >= 120 | | | | |
| 29 | Edge case: movement at boundary corner | Player correctly blocked at corner (x=8, y=16) with D-pad UP+LEFT simultaneously | | | | |
| 30 | Save data system registered | saveData("explorer\_save") available; no crash on navigation that would trigger save | | | | |

## Iteration Log

| Iteration | Date | Tester | Coffee-GB Result | mGBA Result | Issues Found |
|-----------|------|--------|-----------------|-------------|-------------|
| 1 | | | | | |

## Pass Criteria

All 30 scenarios pass in both Coffee-GB (headless) and mGBA columns before marking this game UAT-complete.
