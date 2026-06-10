# gbkt-emulator — Embedded Game Boy Emulator

Embedded Coffee-GB emulator with debug log capture, source map resolution, and developer UI (display, toolbar, log viewer, memory inspector). Used by the Gradle plugin for `runEmulator`, `debugEmulator`, and `emulatorTest` tasks.

## Dependencies

- `eu.rekawek.coffeegb:coffee-gb` — Game Boy CPU/PPU/APU emulation core
- `org.json:json` — Source map file parsing
- Swing (JDK) — Developer UI components

## Architecture

Three layers:
1. **Core** (`CoffeeGbEmulator`, `EmulatorSession`, `EmulatorConfig`) — Lifecycle, frame loop, callback hooks
2. **Debug** (`EmuPrintfInterceptor`, `SourceMapResolver`, `DebugLogWriter`, `DebugLogEntry`) — EMU_printf trap detection, C→Kotlin source mapping, log persistence
3. **UI** (`EmulatorWindow`, `EmulatorToolbar`, `GbDisplayPanel`, `LogCatPanel`, `MemoryInspectorPanel`) — Swing developer tools

### Hung-ROM watchdog (`CoffeeGbEmulator.stepFrame`)

`stepFrame()`'s inner `do { gb.tick() } while (!frameDone)` loop is bounded by `maxTicksPerFrame`
(default 1 000 000 t-cycles ≈ 14 normal frames). If a ROM stalls — e.g. LCD disabled with a CPU
busy-wait, or a corrupted scene-enter that hangs the PPU — the loop throws
`EmulatorFrameHangException` instead of spinning forever. The watchdog also honours a `@Volatile`
cancellation flag (`requestCancellation()` on `GbEmulator`), so stop paths preempt within one tick.

The default ceiling is comfortable for any legitimate ROM init phase but bounded. Test code in
the same module may set `maxTicksPerFrame` lower to assert the watchdog fires.

History: the watchdog was added after a racer-fix regression where `gb.tick()` returned false
forever after `SHOW_BKG` was enabled with no valid BG state — the MCP server held its mutex
indefinitely and only an external `kill -9` recovered the JVM. The unit tests
(`CoffeeGbEmulatorTest`) lock both the watchdog trip and the cancellation path.

## Key Files

| File | Role |
|------|------|
| `CoffeeGbEmulator.kt` | Coffee-GB wrapper with custom tick loop, frame buffer, debug log |
| `GbEmulator.kt` | Public interface + MemoryAccess + LogLevel enum |
| `EmulatorSession.kt` | Orchestrates emulator + UI window creation and wiring |
| `EmulatorConfig.kt` | Configuration data class (ROM, scale, headless, source maps, log) |
| `EmuPrintfInterceptor.kt` | Detects GBDK EMU_printf trap (0x52 opcode sequence) in CPU stream |
| `SourceMapResolver.kt` | Resolves C line → Kotlin DSL location via .gbkt.map + .noi files |
| `DebugLogEntry.kt` | Structured log entry with timestamp, level, source location |
| `DebugLogWriter.kt` | Writes log entries to file with auto-flush |
| `AgentSessionConfig.kt` | Session config with `metadataFile` field and `discoverFiles()` auto-discovery |
| `StepAgent.kt` | Frame-by-frame agent returning `Observation` with `isTerminal`, introspection methods |
| `UatRunner.kt` | Checkpoint-based UAT runner with soft assertions and golden screenshot comparison |
| `GameMetadata.kt` | Codegen-emitted metadata (scenes, actors, variables, texts, terminal scenes) |
| `SceneMap.kt` | Bidirectional scene name/index mapping |
| `OamSpriteReader.kt` | Reads visible OAM sprites from emulator memory |
| `VramTextVerifier.kt` | Reads tilemap text from VRAM for assertion and search |
| `EmulatorWindow.kt` | Main Swing window (display + toolbar + status bar + keyboard shortcuts) |
| `EmulatorToolbar.kt` | Pause/step/speed/log/memory toggle toolbar |
| `GbDisplayPanel.kt` | Renders 160x144 Game Boy LCD frames at configurable scale |
| `LogCatPanel.kt` | LogCat-style dark terminal with level filtering |
| `MemoryInspectorPanel.kt` | Tabbed panel: named variables + hex dump |

## Agent API for Game Testing

### StepAgent

Frame-by-frame agent providing an observe-decide-act loop. Each `step()` advances one frame and returns a full `Observation`:

- **`step(buttons)`** / **`stepN(n, buttons)`** — Advance frames with declarative button state
- **`waitUntil(maxFrames) { predicate }`** — Step until condition met
- **`waitForScene(name, maxFrames)`** — Wait for scene transition
- **`waitForVariable(name, expected, maxFrames)`** — Wait for variable value
- **`waitUntilTextOnScreen(text, maxFrames)`** — Wait for text on either tilemap layer
- **`describeGame()`** — Return loaded `GameMetadata` or null
- **`listVariables()`** — All sym file variable names (sorted)
- **`listScenes()`** — All scene names from metadata
- **`listActors()`** — All actor names from metadata

### Observation

Full game state snapshot with `isTerminal` for game-ending scenes:

- `frame`, `variables`, `scene`, `sprites`, `actors`, `bgText`, `winText`, `newLogEntries`
- `isTerminal` — `true` when current scene is in `GameMetadata.terminalScenes`
- `hasText(text)` — Extension: check if text is on screen
- `toSummary()` — Extension: human-readable summary

### GameMetadata

Parsed from `game_metadata.json` emitted by codegen pipeline:

- `scenes: SceneMap` — Bidirectional scene name/index mapping
- `actors: List<ActorMetadata>` — Actor OAM slot assignments, sprite dimensions, position variables
- `variables: List<VariableDef>` — DSL-declared variable names and types
- `texts: List<String>` — Literal display strings from scene scripts
- `terminalScenes: Set<String>` — Convention-detected terminal scene names (gameover, victory, etc.)

Auto-loaded from `config.metadataFile` by both `StepAgent` and `UatRunner`.

### AgentSessionConfig

- `metadataFile: File?` — Optional path to `game_metadata.json`
- `discoverFiles(romFile)` — Convention-based auto-discovery of sym/metadata/source maps from standard Gradle layout

### UatRunner

Checkpoint-based UAT workflow wrapping `AgentDebugSession`:

- `wait()`, `press()`, `hold()`, `release()` — Input primitives
- `checkpoint(label)` — Capture screenshot + variables + assertions
- `assertVariable()`, `assertTextOnScreen()`, `assertScene()`, `assertSpriteAt()` — Soft assertions
- `waitForScene()`, `waitUntilTextOnScreen()`, `waitUntilVariable()` — Condition-based waits
- `generateReport()` — Aggregated JSON report with golden comparison results
- Accepts `metadata: GameMetadata?` parameter (or auto-loads from `config.metadataFile`)

## Common Tasks

- **Run emulator tests:** `./gradlew :gbkt-emulator:test`
- **Run agent tests:** `./gradlew :gbkt-emulator:test --tests "*StepAgentTest*"`
- **Run UAT tests:** `./gradlew :gbkt-examples:pong:test --tests "*PongUatTest*"`
- **Run detekt:** `./gradlew :gbkt-emulator:detekt`
- **Add new debug hook:** Wire callback in `CoffeeGbEmulator`, expose on `GbEmulator` interface
- **Add new UI panel:** Create JPanel subclass, wire in `EmulatorSession.launch()`
