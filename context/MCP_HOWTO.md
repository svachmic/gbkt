# MCP Agent Testing — How-To Guide

How to use the gbkt MCP server to let Claude Code (or any MCP client) play-test Game Boy games.

## Prerequisites

- JDK 21+
- GBDK-2020 installed (for building ROMs)
- A built ROM (e.g., `./gradlew :gbkt-examples:pong:buildRom`)

## Setup

### 1. Build the shadow JAR

```bash
./gradlew :gbkt-mcp-server:shadowJar
```

Output: `gbkt-mcp-server/build/libs/gbkt-mcp-server-all.jar`

### 2. Configure Claude Code

The project includes `.claude/mcp_servers.json`:

```json
{
  "gbkt-emulator": {
    "type": "stdio",
    "command": "java",
    "args": ["-jar", "gbkt-mcp-server/build/libs/gbkt-mcp-server-all.jar", "--headed"]
  }
}
```

After building the shadow JAR, Claude Code will auto-detect the MCP server on next launch.

## Headed vs Headless Mode

The MCP server supports two modes:

| Mode | Flag | Use case |
|------|------|----------|
| **Headed** | `--headed` | Developer watches the agent play — a Swing window opens showing the Game Boy LCD in real time. Like Netflix's "smart monkey" testing. |
| **Headless** | (default) | CI / automated tests. No display window. Fast, no GUI dependencies. |

When `--headed` is active, every `emulator_start` call opens a 640x576 Swing window (160x144 at 4x scale) showing exactly what the Game Boy screen looks like. The agent still controls all input — you just watch.

The same flag works for programmatic use:

```kotlin
// Headed — opens viewer window
val config = AgentSessionConfig(romFile = rom, headless = false)

// Headless — CI mode (default)
val config = AgentSessionConfig(romFile = rom)
```

## Working Transcript — Pong Playthrough

This shows what a real Claude Code MCP session looks like testing Pong, step by step.

### 1. Start the emulator

```
emulator_start(romFile: "gbkt-examples/pong/build/gbkt/output/pong.gb",
               symFile: "gbkt-examples/pong/build/gbkt/output/pong.noi",
               metadataFile: "gbkt-examples/pong/build/gbkt/generated/game_metadata.json")
→ { started: true, metadata: { scenes: ["game","gameover","title"], actors: [...] } }
```

### 2. Describe the game

```
emulator_describe_game()
→ { scenes: ["game","gameover","title"],
    actors: [{ name: "paddle1", oamCount: 2, xVar: "paddle1_x", yVar: "paddle1_y" },
             { name: "paddle2", oamCount: 2, xVar: "paddle2_x", yVar: "paddle2_y" },
             { name: "ball", oamCount: 1, xVar: "ball_x", yVar: "ball_y" }],
    variables: [{ name: "p1Score", type: "UINT8" }, ...],
    terminalScenes: ["gameover"] }
```

### 3. Boot to title screen

```
emulator_step(frames: 120)
→ { frame: 120, scene: "title", bgText: ["...PONG...", "...PRESS START..."] }
```

### 4. Wait for text

```
emulator_wait_until_text(text: "PRESS START", maxFrames: 10)
→ { met: true, framesElapsed: 0 }
```

### 5. Press START and transition to gameplay

```
emulator_step(frames: 1, buttons: ["start"])
emulator_step(frames: 1)
emulator_wait_for_scene(scene: "game", maxFrames: 60)
→ { met: true, framesElapsed: 3, observation: { scene: "game", sprites: [...] } }
```

### 6. Observe gameplay state

```
emulator_observe()
→ { scene: "game", actors: [{ name: "ball", x: 80, y: 72 }, ...], sprites: 5 visible }
```

### 7. Read a variable

```
emulator_read_variable(name: "paddle1_y")
→ { name: "paddle1_y", value: 64 }
```

### 8. Move paddle

```
emulator_step(frames: 30, buttons: ["up"])
emulator_read_variable(name: "paddle1_y")
→ { name: "paddle1_y", value: 34 }   // Moved up by 30 frames of input
```

### 9. Force near-win state

```
emulator_write_variable(name: "p1Score", value: 4)
emulator_read_variable(name: "p1Score")
→ { name: "p1Score", value: 4 }
```

### 10. Take a screenshot

```
emulator_screenshot(label: "near_win")
→ { filePath: "/path/to/screenshots/near_win_f210.png" }
```

### 11. Let game play out

```
emulator_step(frames: 300)
→ { frame: 510, scene: "gameover", isTerminal: true }
```

### 12. Stop

```
emulator_stop()
→ { stopped: true }
```

## Tool Reference

| Tool | Input | Output |
|------|-------|--------|
| `emulator_start` | `romFile` (required), `symFile?`, `metadataFile?`, `gbcMode?` | Metadata summary |
| `emulator_stop` | — | `{stopped: true}` |
| `emulator_step` | `frames?` (default 1), `buttons?` (array of: up, down, left, right, a, b, start, select) | Full Observation |
| `emulator_observe` | — | Cached Observation (no frame advance) |
| `emulator_wait_for_scene` | `scene`, `maxFrames` | `{met, framesElapsed, observation}` |
| `emulator_wait_for_variable` | `name`, `expected`, `maxFrames` | `{met, framesElapsed, observation}` |
| `emulator_wait_until_text` | `text`, `maxFrames` | `{met, framesElapsed, observation}` |
| `emulator_read_variable` | `name` | `{name, value}` |
| `emulator_write_variable` | `name`, `value` (0-255) | `{success}` |
| `emulator_screenshot` | `label` | `{filePath}` |
| `emulator_describe_game` | — | Full metadata JSON |

## Adapting for Any gbkt Game

1. Build the game ROM: `./gradlew :gbkt-examples:<game>:buildRom`
2. Start with the ROM, sym, and metadata paths for that game
3. Use `emulator_describe_game` to discover scenes, actors, and variables
4. The workflow is identical — boot, navigate scenes, observe, assert

The metadata file (`game_metadata.json`) is emitted by the codegen pipeline alongside the generated C. It provides scene names, actor definitions (with OAM slots and position variables), variable types, and terminal scene detection — everything an agent needs to understand the game without reading source code.
