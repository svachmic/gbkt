# /gbkt-play-game — Interactive Game Session

Play a gbkt game interactively using the MCP emulator tools.

**Usage:** `/gbkt-play-game <game-name>` (e.g., `/gbkt-play-game pong`, `/gbkt-play-game breakout`)

## Argument

`$ARGUMENTS` contains the game name. If empty, list available games first.

## Process

### 1. Discover & Start

If no game name provided:
```
Call emulator_list_games to show available games.
Ask the user which game to play.
```

If game name provided:
```
Call emulator_start with game = "$ARGUMENTS"
```

If start fails, suggest:
- `./gradlew :gbkt-examples:<game>:buildRom` to build the ROM first
- Check `emulator_list_games` for available games

### 2. Load Context

After successful start:
1. Call `emulator_get_playbook` — read the PLAYBOOK.md into context. This is your instruction manual.
2. Call `emulator_describe_game` — get metadata (scenes, actors, variables, texts).
3. Call `emulator_observe` — see the initial game state.

**Print a brief game summary to the user:**
```
## Playing: <Game Name>

<1-2 sentence overview from playbook>

**Controls:** <key controls from playbook>
**Scenes:** <scene flow from playbook>
**Goal:** <win condition from playbook>

Starting on scene: <current scene>
```

### 3. Play Loop

Enter an interactive play loop. On each turn:

1. **Observe** — Describe what you see: current scene, visible text, sprite positions, relevant variable values. Be specific and concise.

2. **Decide** — Explain your strategy in 1 sentence based on the playbook knowledge.

3. **Act** — Use `emulator_step` with appropriate buttons. Use multiple steps when needed:
   - Hold direction buttons for movement (step with button held for N frames)
   - Use `emulator_wait_for_scene` when expecting a transition
   - Press START/A/B for single-frame presses (step 1 frame with button, then release)

4. **Report** — After acting, observe the result. Report what changed:
   - Scene transitions
   - Score/variable changes
   - Actor movement
   - Text appearing/disappearing

5. **Screenshot** — Take screenshots at interesting moments:
   - Title screen
   - First gameplay moment
   - Score events
   - Win/lose screen
   Use `emulator_screenshot` with descriptive labels.

### 4. Save States

Use `emulator_save_state` to bookmark interesting moments:
- Before risky actions ("pre-boss", "before-jump")
- At high scores ("score-4-0")
- At scene transitions ("entering-gameplay")

Offer to `emulator_load_state` if the user wants to retry a section.

### 5. Session End

When the game reaches a terminal state (game over, victory) or the user wants to stop:
1. Take a final screenshot
2. Print a session summary:
```
## Session Summary

**Game:** <name>
**Frames played:** <count>
**Final scene:** <scene>
**Key variables:** <relevant vars and values>
**Screenshots:** <list of captured screenshots>
```

## Play Style Guidelines

- **Be curious** — explore different scenes, try all buttons
- **Follow the playbook** — use the controls and scene flow as documented
- **Narrate naturally** — describe gameplay like a Let's Play, not a technical log
- **Validate assumptions** — use `emulator_assert` to check expectations
- **Don't rush** — step in reasonable increments (10-30 frames for movement, 60-120 for waiting)
- **Ask the user** — if you're unsure what to do, ask. If you're at a decision point, present options.
- **Read the quirks** — the playbook's Known Quirks section has important edge cases

## Error Recovery

- If the emulator crashes or gets stuck, try `emulator_stop` then `emulator_start` again
- If in an unexpected state, check `emulator_observe` and try `emulator_load_state` if a save exists
- If controls don't seem to work, re-read the playbook Controls section — you might be in the wrong scene
