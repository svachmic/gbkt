# UAT Guide

Debugging workflow for play-testing Game Boy ROMs with the MCP emulator agent.

## Overview

This document covers the debugging **workflow** — how to use the MCP emulator tools to find and
fix bugs in compiled Game Boy ROMs. It is the companion to the test infrastructure reference.

- `context/TESTING.md` covers the testing infrastructure: `GbktTestExtension`, assertions,
  recipes, `GameConstants`, `PLAYBOOK.md` format, and the MCP tool API reference.
- This guide covers the workflow: starting a session, navigating game scenes, asserting state,
  bisecting bugs with savestates, and reading variable values correctly.

See [context/TESTING.md](TESTING.md) for the test infrastructure reference.

## Prerequisites

- ROM must be built: `./gradlew :gbkt-examples:<game>:buildRom`
- MCP server JAR must be built: `./gradlew :gbkt-mcp-server:shadowJar`
- MCP server must be configured in `.claude/mcp_servers.json` (see CLAUDE.md → "MCP server setup")
- `PLAYBOOK.md` should exist for the game: `./gradlew :gbkt-examples:<game>:generatePlaybook`
- Symbol file (`.sym` or `.noi`) must exist alongside the ROM for variable inspection

## Debugging Workflow

1. **Start session.** Call `emulator_start` with the game name. The server locates the ROM,
   symbol file, and metadata file automatically via `AgentSessionConfig.discoverFiles()`.

   ```json
   { "tool": "emulator_start", "arguments": { "game": "pong" } }
   ```

2. **Read the playbook.** Call `emulator_get_playbook` to load the game's `PLAYBOOK.md`. The
   playbook describes all scenes, variables, expected behavior, and known edge cases. Read it
   before stepping frames.

   ```json
   { "tool": "emulator_get_playbook" }
   ```

3. **Boot and observe.** Call `emulator_step` with `frames=60` to advance past the boot screen.
   The returned `Observation` contains `scene`, `variables`, `bgText`, `winText`, and `sprites`.

   ```json
   { "tool": "emulator_step", "arguments": { "frames": 60 } }
   ```

4. **Navigate with button presses.** Call `emulator_press` to simulate a button tap. To advance
   past a title screen requiring a Start press, then wait 30 frames for the transition:

   ```json
   { "tool": "emulator_press", "arguments": { "button": "start" } }
   { "tool": "emulator_step", "arguments": { "frames": 30 } }
   ```

   Use `emulator_wait_for_scene` to block until a named scene becomes active:

   ```json
   { "tool": "emulator_wait_for_scene", "arguments": { "scene": "gameplay", "timeout": 120 } }
   ```

5. **Assert state.** Call `emulator_assert` with a batch of checks. Combine multiple assertion
   types in one call to minimize round trips:

   ```json
   {
     "tool": "emulator_assert",
     "arguments": {
       "checks": [
         { "type": "scene_is", "scene": "gameplay" },
         { "type": "variable_equals", "variable": "score", "value": 0 },
         { "type": "text_on_screen", "text": "0" }
       ]
     }
   }
   ```

6. **Bisect bugs with savestates.** Call `emulator_save_state` at a known-good point. If the
   game reaches a bad state, call `emulator_load_state` to restore and replay the sequence with
   different assertions.

   ```json
   { "tool": "emulator_save_state", "arguments": { "slot": 0 } }
   { "tool": "emulator_load_state", "arguments": { "slot": 0 } }
   ```

7. **Inspect variables.** Call `emulator_read_variable` for individual variable checks. Values
   are returned in their declared DSL type (INT8 returns signed, UINT16 returns 16-bit unsigned).

   ```json
   { "tool": "emulator_read_variable", "arguments": { "variable": "ballDx" } }
   ```

   All current variable values also appear in the `variables` field of every `Observation`.

8. **Correlate with source.** The source map file (`build/gbkt/generated/main.c.gbkt.map`)
   maps C line numbers to Kotlin DSL locations. When the debug log shows a C line producing
   wrong output, look up its Kotlin origin in the source map.

## Real Debugging Walkthroughs

### Walkthrough 1: Variable Inspector Sym Parsing Bug

**Problem.** After loading a ROM's `.sym` file, `emulator_read_variable` for a variable named
`score` returned a `"variable not found"` error even though the sym file listed it. Listing all
variables via `readAll()` returned one extra entry with a garbage name.

**Tool call sequence.**

```
emulator_start("pong")
emulator_step(frames=60)
emulator_read_variable(variable="score")
→ Error: "variable not found: score"
```

Ran `readAll()` to list what was loaded:

```
emulator_assert(checks=[{ type: "variable_equals", variable: "score", value: 0 }])
→ FAIL: "score" not in variable table
```

Examined the symbol file entries:

```
DEF _score 00:C100
DEF _lives 00:C101
DEF _ballDx 00:C102
DEF _ignored C102
```

**Observed output.** The inspector returned 4 symbols including `_ignored`, which should have
been skipped (it has no `bank:addr` colon format — just a bare hex address `C102`).

**Root cause.** Kotlin's `String.substringAfter(":")` returns the full string when the delimiter
is not found. The line `DEF _ignored C102` produced `addrStr = "C102"` which then parsed as a
valid hex address. The missing colon check allowed malformed lines to be treated as valid entries,
polluting the symbol table.

**Fix.** Added a guard in `VariableInspector.kt` before address parsing:

```kotlin
if (!addrStr.contains(":")) return@forEach
```

After the fix, `listVariables()` returned exactly 3 entries and `readAll()` included `score`
correctly. Commit `e98e4ae`.

---

### Walkthrough 2: Signed Variable Returning Wrong Value

**Problem.** The Pong ball's `ballDx` variable (declared as `var ballDx by i8Var(-1)` in the
DSL) was returning `255` from `emulator_read_variable` instead of `-1`. The agent incorrectly
concluded the ball was moving at speed 255 to the right, masking a direction-detection bug.

**Tool call sequence.**

```
emulator_start("pong")
emulator_step(frames=60)
emulator_read_variable(variable="ballDx")
→ { "ballDx": 255 }
```

The agent then checked the DSL declaration:

```kotlin
var ballDx by i8Var(-1)  // INT8 type, initial value -1
```

The initial value of `-1` stored in WRAM as a two's complement byte is `0xFF` = 255 decimal.
The old `VariableInspector.readNamed()` read raw bytes and returned unsigned values regardless
of the declared type.

**Observed output.**

```
variables: { "ballDx": 255, "score": 0, "lives": 3 }
```

`ballDx` should be `-1` (moving left at speed 1). Positive 255 is physically impossible on a
160-pixel screen and was a clear sign of unsigned interpretation of a signed byte.

**Root cause.** `VariableInspector` was reading all variables as `UINT8` (unsigned byte). It
had no mechanism to consult the declared type from `game_metadata.json`. INT8 values stored as
two's complement bytes (0xFF for -1, 0x80 for -128) were reported as unsigned 0–255 values.

**Fix.** Phase 07.1.1 Plan 01 added `readTypedValue()` to `VariableInspector`, which dispatches
on the IR type string from `game_metadata.json`:

```kotlin
fun readTypedValue(entry: SymbolEntry): Int = when (entry.type) {
    "I8"  -> memory.readByte(entry.address).toByte().toInt()   // sign-extend
    "U16" -> memory.readWord(entry.address)                    // little-endian 16-bit
    "I16" -> memory.readWord(entry.address).toShort().toInt()  // signed 16-bit
    else  -> memory.readByte(entry.address).toInt() and 0xFF   // UINT8 default
}
```

`StepAgent.start()` now calls `overrideVariableTypes()` immediately after loading the session,
applying the metadata types to the symbol table. After the fix:

```
variables: { "ballDx": -1, "score": 0, "lives": 3 }
```

Commit `3992e74`.

---

### Walkthrough 3: Scene Transition Not Triggering

**Problem.** During Dungeon game testing, pressing the `a` button at a door tile was expected
to trigger the `battle` scene transition but the game remained on the `gameplay` scene. The agent
could not tell whether the button press was not registered, or the scene-change condition was not
met.

**Tool call sequence.**

```
emulator_start("dungeon")
emulator_wait_for_scene(scene="gameplay", timeout=180)
→ OK: scene="gameplay" at frame 120
```

Old approach (2 calls to simulate a button press):

```
emulator_step(frames=1, buttons=["a"])
→ Observation: scene="gameplay", variables: { stepCount: 42, torchLevel: 230 }
emulator_step(frames=1)
→ Observation: scene="gameplay"
```

The scene did not change. Checked variable state:

```
emulator_read_variable(variable="stepCount")
→ { "stepCount": 42 }
```

Step count was 42 — below the encounter threshold of 120. The button press fired but the
encounter condition was not satisfied.

**Root cause.** The encounter logic in the Dungeon game required `stepCount >= 120` before any
`a` button interaction at a door tile triggered the `battle` scene. At step 42 the condition
was silently false — the door was interactable but led nowhere because the encounter was not
armed.

**Bisect with savestate.** Saved state at step 42, then simulated walking to step 120:

```
emulator_save_state(slot=0)
emulator_step(frames=390)   // ~78 more steps at 5 frames/step
emulator_press(button="a")
emulator_wait_for_scene(scene="battle", timeout=60)
→ OK: scene="battle" at frame 451
```

Using the new `emulator_press` tool (single call, hold+release semantics) confirmed that once
`stepCount` reached 120, the door interaction fired correctly.

**Fix.** The game logic was correct — the test sequence needed to walk past the safe-step
threshold first. Added a pre-encounter walking sequence to the Dungeon PLAYBOOK.md to document
that the battle scene requires `stepCount >= 120`.

## Using emulator_press

`emulator_press` replaces the two-call pattern for simple button taps.

**Old pattern (2 calls):**

```json
{ "tool": "emulator_step", "arguments": { "frames": 1, "buttons": ["a"] } }
{ "tool": "emulator_step", "arguments": { "frames": 1 } }
```

**New pattern (1 call):**

```json
{ "tool": "emulator_press", "arguments": { "button": "a" } }
```

`emulator_press` advances `frames + 1` total frames: N frames with the button held (default 1),
then 1 release frame. The returned `Observation` reflects state after the release frame. This
matches GBDK `pressed()` edge-detection semantics — the game sees a rising edge on the held
frame and an unpressed state on the release frame.

**Hold duration.** To hold a direction for 30 frames (e.g., moving a character across a room):

```json
{ "tool": "emulator_press", "arguments": { "button": "right", "frames": 30 } }
```

This advances 31 total frames (30 held + 1 release).

**Valid buttons:** `up`, `down`, `left`, `right`, `a`, `b`, `start`, `select`

**Error responses:**

| Condition | Message |
|-----------|---------|
| Missing `button` | `button is required` |
| Invalid button name | `Invalid button 'x'. Valid: up, down, left, right, a, b, start, select` |
| `frames` less than 1 | `frames must be positive` |
| No active session | `No active emulator session` |

Use `emulator_press` for all single-button taps. Reserve `emulator_step` with a `buttons` array
for sequences that require simultaneous button combinations.

## Text Assertions

All UI text in gbkt games renders on the **GBDK window layer** via `_win_*` helpers generated
by `WindowTextCodegen`. This prevents dialog text, menus, score displays, and status bars from
corrupting the background tile layer when custom tilesets are in use.

**Layer separation:**

| Layer | Content | Tile decoder |
|-------|---------|--------------|
| BG (background) | Game graphics: dungeon tiles, terrain, sprites baked into map | GBDK offset decoder |
| WIN (window) | UI text: dialogs, menus, score, HUD, battle messages | Direct ASCII decoder |

`text_on_screen` in `emulator_assert` searches both layers and reports which layer the text was
found on. If a text assertion fails, check:

1. Is the text on WIN (expected for UI) or BG (expected only for games with BG-rendered text)?
2. Is the game currently in the scene where the text appears?
3. Is the text exactly matching — including trailing spaces used for fixed-width menu alignment?

**When to use `scrollAware: true`.** Games with camera scroll (platformers, dungeon crawlers
with large maps) read BG layer tiles offset by the hardware scroll registers SCX and SCY. When
the camera has scrolled, tile addresses in the tilemap shift relative to the viewport. Set
`scrollAware: true` on `text_on_screen` checks that read from the BG layer in scrolling games:

```json
{
  "type": "text_on_screen",
  "text": "LEVEL 1",
  "scrollAware": true
}
```

`scrollAware` has no effect on the WINDOW layer. The Game Boy window layer has independent
WX/WY registers and does not scroll with the background.

**Tile decoder configuration.** Each game's `game_metadata.json` includes a `tileDecoders`
section that `StepAgent` uses when building observations:

```json
{
  "tileDecoders": {
    "bg":  { "type": "gbdk_offset" },
    "win": { "type": "direct_ascii" }
  }
}
```

Games without a `tileDecoders` key fall back to these same defaults. No configuration is
required for standard games.

## Variable Inspection

Variables are read with their declared DSL types. The type system prevents misinterpreting
signed bytes as large unsigned values.

**Type-to-range mapping:**

| IR Type | DSL declaration | Range | Sign |
|---------|----------------|-------|------|
| UINT8 | `u8Var(n)` | 0 to 255 | unsigned |
| INT8 | `i8Var(n)` | -128 to 127 | signed |
| UINT16 | `u16Var(n)` | 0 to 65535 | unsigned |
| INT16 | `i16Var(n)` | -32768 to 32767 | signed |

**Common pitfall.** A variable declared as `var ballDx by i8Var(-1)` stores -1 in hardware as
the two's complement byte `0xFF`. Before Phase 07.1.1, this read as `255` (unsigned). After
Phase 07.1.1, it reads as `-1` (signed). If you see a variable value that seems impossibly
large (e.g., 255 for a direction that should be ±1), check whether the DSL declaration uses
`i8Var` rather than `u8Var`.

**Reading a single variable:**

```json
{ "tool": "emulator_read_variable", "arguments": { "variable": "ballDx" } }
```

**All variables in every observation.** The `variables` field in every `Observation` JSON
includes all symbol-table variables with type-correct values. Check it in the `emulator_step`
and `emulator_press` response rather than making a separate `emulator_read_variable` call.

**Observing 16-bit variables.** A UINT16 variable occupies two consecutive bytes in WRAM in
little-endian order. `readTypedValue` reads both bytes and combines them: `low | (high << 8)`.
For `raceTime` declared as `u16Var(0)`, a raw WRAM dump showing `0x2C 0x01` at the address
reports `0x012C` = 300 (30 seconds at 10 ticks per second).

## See Also

- [context/TESTING.md](TESTING.md) — Test infrastructure reference: `GbktTestExtension`,
  assertions, recipes, `GameConstants`, `PLAYBOOK.md` format, and all MCP tool documentation
- [gbkt-emulator/CLAUDE.md](../gbkt-emulator/CLAUDE.md) — Agent API architecture: `StepAgent`,
  `UatRunner`, `AgentDebugSession`, `VramTextVerifier`, `VariableInspector`, `SavestateManager`
- [gbkt-mcp-server/CLAUDE.md](../gbkt-mcp-server/CLAUDE.md) — MCP server architecture: 17
  tools, session model, `McpEmulatorSession`, `ToolHandlerLogic`, stdio transport
