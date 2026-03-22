# gbkt-mcp-server — MCP Server for AI Agent Game Testing

MCP (Model Context Protocol) server that exposes the gbkt emulator as tools for AI coding agents. Enables frame-by-frame game control, variable inspection, screenshot capture, metadata query, save/load state, batch assertions, playbook access, and game discovery via the standard MCP stdio transport.

## Dependencies

- `gbkt-emulator` — StepAgent, GameMetadata, AgentSessionConfig
- `gbkt-test` — GameDiscovery for convention-based game name resolution
- `io.modelcontextprotocol:kotlin-sdk` — MCP Kotlin SDK (server + stdio transport)
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` — Async wrapping of blocking emulator calls
- `org.jetbrains.kotlinx:kotlinx-serialization-json` — JSON tool results

## Architecture

Single-session stdio server:
- One `StepAgent` at a time (MCP stdio is 1:1 with the client)
- Cached observation: `emulator_observe` returns the last observation without stepping
- Blocking StepAgent calls wrapped in `withContext(Dispatchers.IO)`
- Error convention: `CallToolResult(isError = true)` for all errors

## Key Files

| File | Role |
|------|------|
| `GbktMcpServer.kt` | Entry point: creates Server, registers tools, starts stdio transport |
| `McpEmulatorSession.kt` | Wraps StepAgent with session lifecycle (start/stop/step/observe/savestate/assert) |
| `ObservationSerializer.kt` | `Observation.toJsonObject()` and `GameMetadata.toJsonObject()` extensions |
| `ToolHandlers.kt` | `Server.registerEmulatorTools()` — 16 MCP tool definitions with input schemas |

## 16 MCP Tools

| Tool | Input | Output | Description |
|------|-------|--------|-------------|
| `emulator_start` | `romFile?`, `game?`, `symFile?`, `metadataFile?`, `gbcMode?` | metadata summary | Start emulator session (romFile or game name) |
| `emulator_stop` | — | `{stopped: true}` | Stop current session |
| `emulator_step` | `frames?`, `buttons?` | Full Observation JSON | Advance N frames |
| `emulator_observe` | — | Cached Observation | Get state without stepping |
| `emulator_wait_for_scene` | `scene`, `maxFrames` | `{met, framesElapsed, observation}` | Wait for scene transition |
| `emulator_wait_for_variable` | `name`, `expected`, `maxFrames` | `{met, framesElapsed, observation}` | Wait for variable value |
| `emulator_wait_until_text` | `text`, `maxFrames` | `{met, framesElapsed, observation}` | Wait for text on screen |
| `emulator_read_variable` | `name` | `{name, value}` | Read variable value |
| `emulator_write_variable` | `name`, `value` | `{success}` | Write variable value |
| `emulator_screenshot` | `label` | `{filePath}` | Capture PNG screenshot |
| `emulator_describe_game` | — | Full metadata JSON | Get scenes, actors, variables, texts |
| `emulator_save_state` | `label` | `{label, frame, scene, file}` | Save emulator state with label |
| `emulator_load_state` | `label` | `{restored, label, frame, scene}` | Load previously saved state |
| `emulator_assert` | `checks` | `{passed, failed, results}` | Batch assert multiple conditions |
| `emulator_get_playbook` | — | `{content, path}` | Get PLAYBOOK.md for loaded game |
| `emulator_list_games` | — | `{games: [{name, romFile, hasMetadata}]}` | List all built games in project |

### emulator_assert Check Types

| Type | Args | Description |
|------|------|-------------|
| `variable_equals` | `name`, `expected` | Check variable equals exact value |
| `variable_in_range` | `name`, `min`, `max` | Check variable in inclusive range |
| `scene_is` | `scene` | Check current scene name |
| `text_on_screen` | `text` | Check text substring on either tilemap layer |
| `actor_visible` | `name` | Check actor present in observation |
| `sprite_count` | `expected` | Check total visible sprite count |

### Convention-Based Game Discovery

`emulator_start` accepts a `game` parameter for project-relative discovery:

```json
{"game": "pong"}
```

Resolves to `build/gbkt/output/pong.gb` (standalone) or
`gbkt-examples/pong/build/gbkt/output/pong.gb` (multi-game layout).

## Setup

### Build the shadow JAR
```bash
./gradlew :gbkt-mcp-server:shadowJar
```
Output: `gbkt-mcp-server/build/libs/gbkt-mcp-server-all.jar`

### Configure Claude Code
Add to `.claude/mcp_servers.json`:
```json
{
  "gbkt-emulator": {
    "type": "stdio",
    "command": "java",
    "args": ["-jar", "path/to/gbkt-mcp-server-all.jar"]
  }
}
```

## Common Tasks

- **Run tests:** `./gradlew :gbkt-mcp-server:test`
- **Build shadow JAR:** `./gradlew :gbkt-mcp-server:shadowJar`
- **Add new tool:** Add `addTool()` call in `ToolHandlers.kt`, add method to `McpEmulatorSession`
