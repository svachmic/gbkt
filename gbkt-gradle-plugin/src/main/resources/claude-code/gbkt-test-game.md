# /gbkt-test-game — Automated Game Verification

Run automated test recipes against a gbkt game using the MCP emulator tools.

**Usage:** `/gbkt-test-game <game-name>` (e.g., `/gbkt-test-game pong`, `/gbkt-test-game all`)

## Argument

`$ARGUMENTS` contains the game name, or `all` to test every available game.

## Process

### 1. Discover Games

Call `emulator_list_games` to get the list of available ROMs.

If `$ARGUMENTS` is `all` or empty: test every game with `hasMetadata: true`.
If `$ARGUMENTS` is a specific game name: test only that game.

### 2. For Each Game: Run Test Suite

For each game to test:

#### 2a. Start Session
```
emulator_start(game = "<name>")
emulator_describe_game() → save metadata
emulator_get_playbook() → save playbook context
```

**Print header:**
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 Testing: <Game Name>
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### 2b. Test 1: Boot & Title Screen
1. `emulator_step(frames = 120)` — let the game boot
2. `emulator_observe` — check initial state
3. `emulator_screenshot(label = "<game>-title")`
4. `emulator_assert` with checks:
   - `scene_is`: verify scene matches expected title scene from metadata
   - `text_on_screen`: check for game title text if known from playbook
   - `sprite_count`: verify expected sprite count for title

Report: `✓ Title screen` or `✗ Title screen — <details>`

#### 2c. Test 2: Scene Transitions
For each transition in the playbook's Scene Flow:
1. Navigate to the source scene (press START from title, use appropriate buttons)
2. `emulator_wait_for_scene(scene = "<target>", maxFrames = 300)`
3. `emulator_assert(scene_is = "<target>")`
4. Take screenshot at each new scene

Report each: `✓ title → game` or `✗ title → game — scene is still '<actual>'`

Only test transitions that are reachable from the title screen without complex gameplay.

#### 2d. Test 3: Input Response
For each scene with controls (from playbook):
1. Navigate to the scene
2. Record variable state before input
3. Hold each documented button for 30 frames
4. Record variable state after input
5. `emulator_assert` to verify something changed (variable or actor position)

Report each: `✓ game: UP moves paddle` or `✗ game: UP — no observable change`

#### 2e. Test 4: Actor Visibility
1. Navigate to gameplay scene
2. `emulator_step(frames = 10)` — let sprites render
3. For each actor in metadata, use `emulator_assert(actor_visible = "<name>")`

Report each: `✓ ball visible` or `✗ ball — not in observation`

#### 2f. Test 5: Variable Sanity
1. Read all documented variables via `emulator_read_variable`
2. Verify each returns a value (not null)
3. Verify values are within expected ranges based on semantic type:
   - score: 0-255
   - velocity: -128 to 127
   - position: 0-255
   - counter: 0-255

Report: `✓ Variables: 4/4 readable, all in range` or `✗ Variables: ballDx returned null`

#### 2g. Test 6: Stability Check
1. `emulator_step(frames = 600)` — run 10 seconds of gameplay
2. `emulator_observe` — verify game hasn't crashed
3. Assert `isTerminal` is false (unless it's a game-over condition from scoring)
4. Take a screenshot of the final state

Report: `✓ Stable after 600 frames` or `✗ Terminal state reached unexpectedly`

#### 2h. Cleanup
```
emulator_screenshot(label = "<game>-final")
emulator_stop()
```

### 3. Summary Report

After all games tested:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 Test Results
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

| Game | Boot | Scenes | Input | Actors | Vars | Stable | Score |
|------|------|--------|-------|--------|------|--------|-------|
| pong | ✓ | ✓ 3/3 | ✓ 2/2 | ✓ 3/3 | ✓ 4/4 | ✓ | 6/6 |
| breakout | ✓ | ✓ 5/5 | ✓ 2/2 | ✓ 3/3 | ✓ 9/9 | ✓ | 6/6 |
| ... | | | | | | | |

**Total: X/Y games passed all checks**

Screenshots saved to: build/gbkt/screenshots/
```

### 4. Failure Handling

If a test fails:
- Take a screenshot with label `<game>-fail-<test>`
- Record the observation details (frame, scene, variables)
- Continue to next test (don't abort the suite)
- Include all failures in the summary

If `emulator_start` fails for a game:
- Report: `✗ <game> — ROM not found or emulator error`
- Skip all tests for that game
- Continue to next game

## Guidelines

- **Be systematic** — run every test for every game, don't skip
- **Be precise** — use `emulator_assert` for all checks, not just observation eyeballing
- **Be informative** — report exact values on failure (expected X, got Y)
- **Be efficient** — reuse the emulator session within a game, only restart between games
- **Don't modify** — this is read-only testing. Never use `emulator_write_variable` during automated tests
