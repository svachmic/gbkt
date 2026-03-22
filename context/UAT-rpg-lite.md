# UAT: RPG Lite

**Game:** RPG Lite (RpgLite.kt)
**ROM:** `gbkt-examples/rpg-lite/build/gbkt/output/rpg-lite.gb`
**Scenes:** title → town → dungeon → gameover → title
**Win condition:** Survive and accumulate gold; dungeon level increases each time you reach the right edge
**Lose condition:** HP drops to 0 in dungeon

## Automated Test (Coffee-GB headless)

Run via: `./gradlew :gbkt-examples:rpg-lite:emulatorTest`

| Check | Expected | Result |
|-------|----------|--------|
| 600 frames without crash | No exceptions | |
| No ERROR log entries | Empty error list | |

## Scenario Checklist

| # | Scenario | Expected | Coffee-GB | mGBA | Attempt | Notes |
|---|----------|----------|-----------|------|---------|-------|
| 1 | Title screen displays on launch | "RPG LITE" text centered (col 6), "A MINI ADVENTURE" subtitle, "PRESS START" prompt visible | | | | |
| 2 | START resets state and enters town | hp=30, gold=0, dungeonLevel=1, stepCount=0 reset; scene transitions to town | | | | |
| 3 | Town scene displays correctly | "TOWN" header, "HP:30  GOLD:0" stats, "A: ENTER DUNGEON", "START: HEAL (5G)" instructions visible | | | | |
| 4 | heroActor position resets in town | heroActor.moveTo(80, 72) in town enter; hero centered on screen | | | | |
| 5 | A button enters dungeon from town | buttons.a.pressed navigates to dungeon scene; dungeon header "DUNGEON LV:1" shown | | | | |
| 6 | Dungeon displays level and stats | "DUNGEON LV:N" and "HP:N  GOLD:N" shown on entry; stepCount resets to 0 | | | | |
| 7 | D-pad UP moves hero upward in dungeon | heroActor y decreases by 2 per frame while D-pad UP held; stops at y <= 16 | | | | |
| 8 | D-pad DOWN moves hero downward in dungeon | heroActor y increases by 2 per frame while D-pad DOWN held; stops at y >= 128 | | | | |
| 9 | D-pad LEFT moves hero leftward in dungeon | heroActor x decreases by 2 per frame while D-pad LEFT held; stops at x <= 8 | | | | |
| 10 | D-pad RIGHT moves hero rightward in dungeon | heroActor x increases by 2 per frame while D-pad RIGHT held; stops at x >= 152 | | | | |
| 11 | Step counter increments on movement | stepCount += 1 each frame any D-pad direction is held in dungeon | | | | |
| 12 | Random encounter triggers at stepCount >= 60 | Encounter check fires at stepCount >= 60; stepCount resets to 0; hit SFX plays | | | | |
| 13 | Combat: battleUpdate drives state machine | battleUpdate(combat) called each frame when encounter triggered; combat resolves via simpleBattle | | | | |
| 14 | Combat victory: gold += 5 and win SFX | On victory: winSfx plays, gold increments by 5, navigate back to dungeon | | | | |
| 15 | Combat defeat: lose SFX and gameover | On defeat: loseSfx plays, navigate to gameover scene | | | | |
| 16 | Dungeon exit: reach right edge increases dungeon level | heroActor.x >= 152 triggers dungeonLevel += 1, gold += 3, coinSfx plays, heroActor moves to (8,72) | | | | |
| 17 | Dungeon level display updates after exit | "DUNGEON LV:2" shown after first exit; "DUNGEON LV:3" after second, etc. | | | | |
| 18 | HP = 0 in dungeon triggers gameover | whenever(hp isEqualTo 0) { loseSfx; navigate(gameoverScene) }; gameover scene shown | | | | |
| 19 | START button in dungeon returns to town | buttons.start.pressed navigates from dungeon to town scene | | | | |
| 20 | Town heal: START spends 5 gold for 10 HP | whenever(gold >= 5 AND hp < 30) { gold -= 5; hp += 10; coinSfx plays } | | | | |
| 21 | Town heal: does nothing if gold < 5 | Heal button with gold < 5 has no effect on hp or gold | | | | |
| 22 | Town heal: does nothing if HP already full | Heal button with hp >= 30 has no effect | | | | |
| 23 | Game over screen displays correctly | "GAME OVER", "HP: 0  GOLD: N", "PRESS START" render on window layer | | | | |
| 24 | START on gameover returns to title | buttons.start.pressed on gameover navigates to title scene | | | | |
| 25 | Ability definition compiles correctly | fireball ability (cost sp=8, SINGLE\_ENEMY, FIRE, power=15) registered in IR without crash | | | | |
| 26 | Combat: hero party vs slime encounter | simpleBattle encounter 1 has slime monster; battle resolves without crash | | | | |
| 27 | Combat: hero party vs bat encounter | simpleBattle encounter 2 has bat monster; battle resolves without crash | | | | |
| 28 | Edge case: stepCount at 59 — no encounter | No combat at stepCount=59; encounter triggers exactly at >= 60 | | | | |
| 29 | Edge case: gold accumulates across multiple dungeons | gold carries over between dungeon visits; display shows cumulative total | | | | |
| 30 | Edge case: dungeonLevel increments correctly across multiple right-edge exits | dungeonLevel goes 1→2→3 etc.; display updates each time | | | | |

## Iteration Log

| Iteration | Date | Tester | Coffee-GB Result | mGBA Result | Issues Found |
|-----------|------|--------|-----------------|-------------|-------------|
| 1 | | | | | |

## Pass Criteria

All 30 scenarios pass in both Coffee-GB (headless) and mGBA columns before marking this game UAT-complete.
