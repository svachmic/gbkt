# Phase 07: UAT Gameplay Validation - Research

**Researched:** 2026-03-13
**Domain:** Embedded emulator agent tooling + ROM UAT validation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Agent DX Debugging Suite (PREREQUISITE — BUILD FIRST)**
- Full agent debugging suite is the first deliverable of this phase — all subsequent UAT work depends on it
- Interface: Gradle tasks + CLI commands (Claude calls via Bash tool)
- Screenshot capture: PNG + metadata JSON sidecar (frame number, scene name, key variable snapshot) in `build/gbkt/screenshots/{game}_{frame}_{label}.png`
- Input scripting: Kotlin DSL — type-safe `press(RIGHT, frames=30); press(A); wait(60)` sequences run via Gradle
- Variable inspection: Both DSL-level named variables (`score`, `ball.x`, `lives`) AND raw memory address reads. Source maps + symbol table resolve names to addresses
- Savestates: Full emulator state save/restore to file. Claude can checkpoint before tricky sections, test variations, revert
- Visual diff: Pixel-level screenshot comparison with configurable tolerance (ignore 1-pixel timing shifts). Output diff image highlighting changes
- State introspection: Full game state awareness — current scene, active sprites, loaded tilemap, battle state, flag values via source map + memory inspection
- Automated headless suite: Separate `./gradlew emulatorTest` task. Script input sequences in headless mode for deterministic scenarios. Not part of `./gradlew test` (slower, optional in CI)

**Validation Scope**
- Full regression suite for ALL example games: Pong, Breakout, Explorer, Platformer, Shmup, Racer, Dungeon, RPG-Lite
- GBC variants tested too — platformer-gbc validated alongside base versions
- LotD excluded — deferred to Phase 07.1
- Per-game UAT checklists — each game gets a `UAT-{game}.md` with dual emulator columns (Coffee-GB AND mGBA pass/fail), iteration tracking, visual inspection, expected feel descriptions
- Happy path + edge cases + boundary conditions

**Execution Model**
- Claude drives the entire process
- User plays and verifies
- Claude fixes inline
- Automated pre-checks first

**Bug Handling Policy**
- Fix inline immediately — no log-and-defer
- Generic framework fixes only — no game-specific workarounds
- Retest all affected games after framework fixes
- All ROMs must build at all times
- One atomic commit per bug
- Always add a regression test
- 100% pass required

**Emulator Testing Strategy**
- Both Coffee-GB and mGBA must pass
- Investigate root cause on disagreements
- Headless automated suite via `./gradlew emulatorTest`

**Debugging Workflow Document**
- Location: `context/UAT_GUIDE.md`
- Audience: both humans and Claude agents
- Content: checklists format, debug log usage, source map tracing, agent debugging suite how-to, troubleshooting tables, real worked examples from this phase's bugs

**Planning Constraints**
- Agent DX tooling goes FIRST
- Small, focused plans — one thing per plan
- Quality over speed
- Every plan must be complete within its scope

### Claude's Discretion
- Exact Gradle task names and CLI argument design for agent debugging tools
- Order of game validation (which game first)
- Test scenario decomposition (how many scenarios per game)
- Specific automated test scripts for headless mode
- Internal tooling implementation details (how screenshot capture hooks into Coffee-GB, etc.)
- Visual diff tolerance thresholds

### Deferred Ideas (OUT OF SCOPE)
- LotD UAT — separate Phase 07.1 subphase
- Claude-autonomous playtesting (fully self-directed closed-loop)
- MCP server for emulator tools
- Real hardware testing
- GIF sequence capture
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| UAT-01 | All three example ROMs (Pong, Breakout, Explorer) are manually verified to play correctly in mGBA — not just boot | Agent DX toolkit provides input scripting, screenshot capture, and state introspection needed to drive systematic verification; per-game UAT checklists structure the validation |
| UAT-02 | Debugging workflow documented: source maps + mGBA debugging tools enable efficient issue diagnosis | Source map resolver already built (SourceMapResolver.kt); UAT_GUIDE.md documents the workflow including EMU_printf log capture, source map correlation, and agent debugging suite usage |
</phase_requirements>

---

## Summary

Phase 07 is fundamentally an agent tooling phase that enables systematic ROM validation. The key insight from the context is that the existing emulator infrastructure (`CoffeeGbEmulator`, `EmulatorSession`, `GbEmulator` interface) provides the core primitives but is missing the agent-facing abstractions: screenshot capture with metadata sidecars, input scripting DSL, savestate save/restore, visual diff, and state introspection via source maps.

The emulator infrastructure is highly capable. `CoffeeGbEmulator` exposes `getFrameBuffer()` (160x144 RGB), `getMemory()` (full MMU-based read/write), `stepFrame()` (deterministic single-frame advance), `getDebugLog()` (structured EMU_printf capture), and `onTick`/`onFrameReady`/`onDebugEntry` callbacks. `SourceMapResolver` provides C-line-to-Kotlin-DSL resolution. The `.sym` file enables DSL variable name → memory address lookup. The gap is that none of these are surfaced as agent-callable Gradle tasks.

The game suite includes 8 DMG games (Pong, Breakout, Explorer, Platformer, Shmup, Racer, Dungeon, RPG-Lite) plus 1 GBC variant (platformer-gbc). Each has distinct mechanics requiring tailored UAT scenarios. Pong and Breakout are simple (ball physics, collision, scoring). Platformer is physics-heavy (gravity, jump, platform landing). Explorer and Dungeon are complex (RPG combat, exploration, save/load). Shmup and Racer use entity pools and scrolling. Each game also has existing `SimulationContext`-based JVM tests that provide a regression baseline.

**Primary recommendation:** Build agent DX tooling module in `gbkt-emulator` first, expose via 5 new Gradle tasks (`captureScreenshot`, `runScript`, `readVariable`, `saveState`/`loadState`, `diffScreenshots`), then systematically validate each game using those tools per structured UAT checklists.

---

## Standard Stack

### Core (already in place)

| Library / Component | Version | Purpose | Why Standard |
|---------------------|---------|---------|--------------|
| `CoffeeGbEmulator` | current | Coffee-GB wrapper — frame stepping, memory access, debug log | Already used by `EmulatorTestTask`, `RunEmulatorTask`, `DebugEmulatorTask` |
| `GbEmulator` interface | current | Public control contract (`start`, `pause`, `stepFrame`, `getMemory`, etc.) | All agent tools implement against this interface |
| `SourceMapResolver` | current | C line → Kotlin DSL source location via `.gbkt.map` + `.noi` | Built in Phase 06.12; maps PC → function → Kotlin source |
| `EmuPrintfInterceptor` | current | Captures `EMU_printf` trap output from GBDK ROM | Detects `ld d,d` (0x52) signature, fires `onMessage` callback |
| `EmulatorConfig` | current | Configuration data class (`romFile`, `headless`, `sourceMapsDir`, `logFile`) | Parameter object for all emulator constructions |
| `EmulatorSession` | current | GUI + headless lifecycle orchestrator | Single entry point for UI or headless launch |

### To Be Built (Agent DX Toolkit)

| Component | Module | Purpose |
|-----------|--------|---------|
| `AgentDebugSession` | `gbkt-emulator` | Headless session with agent-specific APIs (screenshot, input script, variable read, savestate) |
| `InputScript` DSL | `gbkt-emulator` | Kotlin type-safe `press(Button, frames)` / `wait(frames)` / `hold(Button)` / `release(Button)` |
| `ScreenshotCapture` | `gbkt-emulator` | PNG write + JSON sidecar with frame number, scene name, key variables |
| `SavestateManager` | `gbkt-emulator` | Full emulator state serialization/deserialization to file |
| `VisualDiff` | `gbkt-emulator` | Pixel-level comparison of two PNG screenshots with diff image output |
| `VariableInspector` | `gbkt-emulator` | `.sym`-backed name→address resolution + memory read |
| `CaptureScreenshotTask` | `gbkt-gradle-plugin` | Gradle task: run N frames, capture screenshot, write PNG + JSON |
| `RunInputScriptTask` | `gbkt-gradle-plugin` | Gradle task: load + execute `.gbkt-script.kts` against ROM in headless mode |
| `ReadVariableTask` | `gbkt-gradle-plugin` | Gradle task: read named DSL variable from running ROM, print value |
| `SaveStateTask` | `gbkt-gradle-plugin` | Gradle task: serialize emulator state to `.gbkt.state` file |
| `LoadStateTask` | `gbkt-gradle-plugin` | Gradle task: restore from `.gbkt.state`, run N more frames |
| `DiffScreenshotsTask` | `gbkt-gradle-plugin` | Gradle task: compare two PNGs, produce diff image, exit non-zero on mismatch |

### Supporting Libraries (already available)

| Library | Purpose |
|---------|---------|
| `javax.imageio.ImageIO` (JDK) | PNG write/read — no extra dependency |
| `org.json:json` | Already in `gbkt-emulator` deps — JSON sidecar write |
| `eu.rekawek.coffeegb:coffee-gb` | Already wired — EventBus for input injection |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom savestate | Coffee-GB serialization API (if any) | Coffee-GB does not expose state serialization — must snapshot all state fields manually or use Java serialization on Gameboy object |
| PNG + JSON sidecar | Unified binary format | Simpler, readable by any tool; JSON sidecar is human-inspectable |
| Kotlin script DSL for input | Plain JSON / YAML file | Type-safe Kotlin script is more ergonomic and catches input errors at compile time |

**Installation:**
No new dependencies needed. All required libraries are already in `gbkt-emulator`'s build.gradle.kts.

---

## Architecture Patterns

### New Module Structure (additions to `gbkt-emulator`)

```
gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/
├── agent/                     # NEW — agent DX toolkit
│   ├── AgentDebugSession.kt   # Orchestrator for headless agent sessions
│   ├── InputScript.kt         # Type-safe input sequence builder (DSL)
│   ├── InputScriptPlayer.kt   # Executes InputScript against EventBus
│   ├── ScreenshotCapture.kt   # PNG write + JSON sidecar with metadata
│   ├── SavestateManager.kt    # Full emulator state save/restore
│   ├── VisualDiff.kt          # Pixel comparison with diff image output
│   └── VariableInspector.kt   # .sym-backed variable name→address resolution
├── CoffeeGbEmulator.kt        # (existing — no changes needed)
├── GbEmulator.kt              # (existing — no changes needed)
├── EmulatorSession.kt         # (existing — no changes needed)
└── ...
```

### New Tasks (additions to `gbkt-gradle-plugin`)

```
gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/
├── CaptureScreenshotTask.kt   # NEW
├── RunInputScriptTask.kt      # NEW
├── ReadVariableTask.kt        # NEW
├── SaveStateTask.kt           # NEW
├── DiffScreenshotsTask.kt     # NEW
├── EmulatorTestTask.kt        # (existing — enhanced with input script support)
└── ...
```

### UAT Checklist File Layout

```
context/
├── UAT_GUIDE.md                # NEW — full playbook
├── UAT-pong.md                 # NEW — per-game checklist with dual-emulator columns
├── UAT-breakout.md             # NEW
├── UAT-explorer.md             # NEW
├── UAT-platformer.md           # NEW
├── UAT-shmup.md                # NEW
├── UAT-racer.md                # NEW
├── UAT-dungeon.md              # NEW
├── UAT-rpg-lite.md             # NEW
└── UAT-platformer-gbc.md       # NEW
```

### Pattern 1: AgentDebugSession (headless session with agent APIs)

**What:** A specialized headless session that wraps `CoffeeGbEmulator` and provides high-level agent operations — run N frames, capture screenshot, execute input script, read variable, save/load state.

**When to use:** All Gradle agent tasks use this instead of `CoffeeGbEmulator` directly.

```kotlin
// Source: based on existing EmulatorSession + EmulatorTestTask patterns
class AgentDebugSession(private val config: AgentSessionConfig) {

    private val emulator = CoffeeGbEmulator(config.toEmulatorConfig())

    fun runFrames(n: Int) {
        emulator.pause()
        repeat(n) { emulator.stepFrame() }
    }

    fun captureScreenshot(label: String, outputDir: File): File {
        val frameBuffer = emulator.getFrameBuffer()
        return ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = label,
            frameNumber = frameCount,
            outputDir = outputDir,
            variableSnapshot = readVariables(config.watchVariables)
        )
    }

    fun executeInputScript(script: InputScript) {
        InputScriptPlayer(emulator).play(script)
    }

    fun readVariable(name: String): Int? {
        return VariableInspector(emulator.getMemory(), config.symFile)
            .readNamed(name)
    }

    fun saveState(file: File) = SavestateManager.save(emulator, file)
    fun loadState(file: File) = SavestateManager.load(emulator, file)
}
```

### Pattern 2: InputScript DSL

**What:** Kotlin type-safe builder for input sequences. Executed by `InputScriptPlayer` which injects `ButtonPressEvent`/`ButtonReleaseEvent` into Coffee-GB's EventBus.

**When to use:** All automated scenario testing; scripted reproduction of bugs.

```kotlin
// Source: based on Coffee-GB InputHandler.kt + EventBus patterns (gbkt-emulator)
val script = inputScript {
    wait(60)                        // 1 second at ~60fps
    press(Button.RIGHT, frames = 30)
    press(Button.A)                 // single-frame press
    wait(120)
    hold(Button.LEFT)
    wait(45)
    release(Button.LEFT)
}
```

Coffee-GB input injection uses existing `ButtonPressEvent` / `ButtonReleaseEvent` via `EventBus`. The `InputHandler` in `ui/InputHandler.kt` shows the existing pattern — the agent just fires the same events programmatically without a keyboard.

### Pattern 3: Screenshot Capture + JSON Sidecar

**What:** After running N frames, capture the 160x144 frame buffer as PNG. Write alongside it a JSON sidecar with metadata.

```kotlin
// Source: based on GbEmulator.getFrameBuffer() + javax.imageio.ImageIO
object ScreenshotCapture {
    fun capture(
        frameBuffer: IntArray,      // 160*144 RGB pixels
        label: String,
        frameNumber: Int,
        outputDir: File,
        variableSnapshot: Map<String, Int> = emptyMap()
    ): File {
        val baseName = "${label}_frame${frameNumber}"
        val pngFile = File(outputDir, "$baseName.png")
        val jsonFile = File(outputDir, "$baseName.json")

        // Write PNG using BufferedImage + ImageIO
        val img = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 160, 144, frameBuffer, 0, 160)
        ImageIO.write(img, "png", pngFile)

        // Write JSON sidecar
        val json = JSONObject().apply {
            put("frameNumber", frameNumber)
            put("label", label)
            put("capturedAt", System.currentTimeMillis())
            put("variables", JSONObject(variableSnapshot))
        }
        jsonFile.writeText(json.toString(2))

        return pngFile
    }
}
```

### Pattern 4: Savestate Save/Load

**What:** Serializes the full Coffee-GB `Gameboy` object state to a file. On load, restores it so testing can branch from a checkpoint.

**Known constraint:** Coffee-GB does not expose a built-in serialization API. Two approaches:
1. Serialize the `Gameboy` instance via Java serialization (requires `Gameboy` to be `Serializable` or use a custom serializer).
2. Snapshot WRAM + registers + MBC state manually via `getAddressSpace()` reads.

**Recommended approach:** Manual snapshot of relevant state regions (WRAM 0xC000-0xDFFF, OAM 0xFE00-0xFE9F, registers PC/SP/AF/BC/DE/HL, timer registers, key I/O registers). This avoids `Serializable` requirements and is deterministic. The state can be restored by writing back the same byte ranges.

```kotlin
// Approximate - exact implementation depends on Coffee-GB API inspection
object SavestateManager {
    private val WRAM_START = 0xC000
    private val WRAM_SIZE = 0x2000  // 8KB WRAM
    private val OAM_START = 0xFE00
    private val OAM_SIZE = 0xA0     // 160 bytes OAM

    fun save(emulator: GbEmulator, file: File) {
        val mem = emulator.getMemory()
        val wram = IntArray(WRAM_SIZE) { mem.readByte(WRAM_START + it) }
        val oam = IntArray(OAM_SIZE) { mem.readByte(OAM_START + it) }
        // Serialize to JSON or binary
        file.writeBytes(/* serialized state */)
    }
}
```

**Confidence:** MEDIUM — this pattern is confirmed by how `getMemory()` works (MMU-backed read/write), but the exact scope of state that needs snapshotting for a useful savestate requires validation. Registers may not be accessible via `getMemory()`.

### Anti-Patterns to Avoid

- **Blocking the EDT in Gradle tasks:** All agent tasks must use headless mode only (`headless = true`). Never instantiate Swing components in Gradle tasks.
- **Saving screenshots inside the emulator thread:** `getFrameBuffer()` already returns a copy (see `synchronized(frameBufferLock)`). Always call it from outside the emulator thread.
- **Hardcoding wait frames:** Use `waitUntil { condition }` helpers when possible; fixed frame counts are brittle across emulator speed variations.
- **Multiple emulator instances per test:** One `AgentDebugSession` per game scenario. Multiple simultaneous instances on the same JVM can cause thread/port conflicts.
- **Using `Thread.sleep()` for synchronization:** Use `stepFrame()` for deterministic frame-counted waits. `Thread.sleep()` is non-deterministic.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PNG image I/O | Custom pixel serialization | `javax.imageio.ImageIO` + `java.awt.image.BufferedImage` | JDK standard, zero deps, handles RGB888 correctly |
| JSON for sidecars | Custom serialization | `org.json:json` (already in `gbkt-emulator` deps) | Already a dependency, used by `SourceMapResolver` |
| Input event injection | Custom CPU register manipulation | `ButtonPressEvent` / `ButtonReleaseEvent` via `EventBusImpl` | Existing pattern used by `InputHandler.kt` |
| DSL variable lookup | Manual memory scanning | `VariableInspector` backed by `.sym` file + `SourceMapResolver` | Symbol table already parsed from `.noi`/`.sym` via `SourceMapResolver` |
| Frame-accurate timing | `Thread.sleep()` loops | `emulator.stepFrame()` | Deterministic — exactly 70224 CPU cycles per call |
| Headless emulator lifecycle | Custom thread management | `CoffeeGbEmulator` + `EmulatorConfig(headless=true)` | Already implemented, tested, thread-safe |

**Key insight:** Almost all of the foundational work is done. The agent DX toolkit is primarily a thin coordination layer — aggregating existing primitives (`getFrameBuffer`, `getMemory`, `stepFrame`, `EventBus`) behind convenient agent-callable APIs.

---

## Common Pitfalls

### Pitfall 1: Coffee-GB Memory Reads Require Running Emulator

**What goes wrong:** Calling `emulator.getMemory().readByte(addr)` throws `IllegalStateException: Emulator is not running`.
**Why it happens:** The `MemoryAccess` implementation (inside `CoffeeGbEmulator.getMemory()`) checks `running.get()` before every read. If `stop()` was called, or the emulator hasn't started, reads throw.
**How to avoid:** Always call `getMemory()` after `start()` and before `stop()`. In agent sessions, keep the emulator paused (not stopped) between operations.
**Warning signs:** ISE in agent scripts; test passes in isolation but fails when cleanup order is wrong.

### Pitfall 2: stepFrame() Requires Paused State

**What goes wrong:** `stepFrame()` throws `IllegalStateException: stepFrame() requires the emulator to be paused`.
**Why it happens:** `stepFrame()` acquires `tickLock` and assumes no concurrent tick loop is running. The loop only stops when `paused = true`.
**How to avoid:** Always call `pause()` before any `stepFrame()` sequences. The pattern from `EmulatorTestTask`:
```kotlin
emulator.start()
emulator.pause()
repeat(frames) { emulator.stepFrame() }
```
**Warning signs:** ISE on first frame step in agent scripts.

### Pitfall 3: Screenshot Timing — Capture AFTER stepFrame Completes

**What goes wrong:** Screenshot captured mid-frame shows partial render (half-drawn sprites).
**Why it happens:** `getFrameBuffer()` returns the public buffer, which is updated atomically after each complete frame. But if called during `emulatorLoop` between the frame-ready swap and the `onFrameReady` callback, the race is possible.
**How to avoid:** In headless agent mode (`paused = true`, using `stepFrame()`), call `getFrameBuffer()` synchronously after `stepFrame()` returns — the frame is always complete at that point.
**Warning signs:** Screenshots showing graphical artifacts or mid-draw sprites.

### Pitfall 4: Savestate Scope — WRAM Only is Not Enough

**What goes wrong:** Restored savestate renders incorrectly — sprites in wrong positions, or game logic diverges from expected.
**Why it happens:** Game state spans WRAM (variables), OAM (sprite positions), VRAM (tile data), and CPU registers. Restoring only WRAM misses sprite position state.
**How to avoid:** Savestate must include at minimum: WRAM (0xC000-0xDFFF), OAM (0xFE00-0xFE9F), key I/O registers. For save/restore fidelity, also include VRAM (0x8000-0x9FFF) and MBC bank state.
**Warning signs:** Game renders correctly but sprite positions are reset to defaults after restore.

### Pitfall 5: EMU_printf Deduplication Reset

**What goes wrong:** Same debug log message appears only once across an entire session, not once per frame.
**Why it happens:** `EmuPrintfInterceptor.lastInterceptedPc` is reset by `resetDedup()` which is called at frame boundaries in `emulatorLoop`. In headless `stepFrame()` mode, `resetDedup()` IS called after each `stepFrame()` — but only via the `interceptor?.resetDedup()` line in `stepFrame()`. If stepping rapidly without pauses, duplicate entries may be suppressed.
**How to avoid:** Use normal `stepFrame()` sequences. The deduplication is designed for repeated calls at the same PC within a single frame — it resets between frames correctly.
**Warning signs:** Expected per-frame log messages (e.g., score display) only appear once.

### Pitfall 6: GBC ROMs Need GameboyType.GBC

**What goes wrong:** platformer-gbc renders in grayscale or crashes with palette errors in the embedded emulator.
**Why it happens:** `CoffeeGbEmulator.start()` always uses `GameboyType.DMG`. GBC ROMs may require `GameboyType.GBC` (or `CGB`).
**How to avoid:** `AgentSessionConfig` should include a `gbcMode` flag. When building for GBC ROMs, configure accordingly. The existing `EmulatorConfig` does not expose this — it may need to be extended.
**Warning signs:** Missing color, scrambled tiles, or emulator crash on GBC ROM load.

### Pitfall 7: UAT Scope — "Boots" is Not "Works"

**What goes wrong:** Game passes `emulatorTest` (600 frames, no ERROR logs) but gameplay is broken (ball doesn't bounce, collision doesn't register).
**Why it happens:** `emulatorTest` only checks for crash/error log entries. It doesn't verify game logic correctness.
**How to avoid:** UAT checklists must verify observable game state: score increments after point, sprite positions after movement, scene transitions on expected events. Use `readVariable("score")` and screenshot comparison, not just "no errors".
**Warning signs:** All emulator tests pass but manual playtesting reveals broken mechanics.

---

## Code Examples

Verified patterns from existing source:

### Input Injection via EventBus (existing pattern in InputHandler.kt)

```kotlin
// Source: gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/ui/InputHandler.kt
// InputScriptPlayer should use the same approach programmatically
class InputHandler(private val eventBus: EventBusImpl) : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
        val button = keyToButton(e.keyCode) ?: return
        eventBus.dispatch(ButtonPressEvent(button))
    }
    override fun keyReleased(e: KeyEvent) {
        val button = keyToButton(e.keyCode) ?: return
        eventBus.dispatch(ButtonReleaseEvent(button))
    }
}
```

Agent input scripting fires the same `ButtonPressEvent`/`ButtonReleaseEvent` without a keyboard event.

### Headless Frame Step Pattern (from EmulatorTestTask)

```kotlin
// Source: gbkt-gradle-plugin/src/main/.../EmulatorTestTask.kt
val emulator = CoffeeGbEmulator(config)
emulator.start()
emulator.pause()
repeat(frames) { emulator.stepFrame() }
val errors = emulator.getDebugLog().filter { it.level == LogLevel.ERROR }
```

### Frame Buffer Capture (from GbEmulator.getFrameBuffer)

```kotlin
// Source: gbkt-emulator/src/main/.../CoffeeGbEmulator.kt
// Returns a copy — safe to use immediately after stepFrame()
val frameBuffer: IntArray = emulator.getFrameBuffer() // 160*144 RGB pixels

// Write as PNG:
val img = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
img.setRGB(0, 0, 160, 144, frameBuffer, 0, 160)
ImageIO.write(img, "png", File(outputDir, "screenshot.png"))
```

### Source Map + .sym Variable Lookup (existing SourceMapResolver pattern)

```kotlin
// Source: gbkt-emulator/src/main/.../debug/SourceMapResolver.kt
// Resolves PC → symbol name via .noi; symbol name → cLine via source maps
val resolver = SourceMapResolver(
    sourceMapsDir = File("build/gbkt/generated"),
    noiFile = File("build/gbkt/output/game.noi")
)
val location = resolver.resolve("main.c", 42)
// location?.symbol == "score", location?.kotlinFile == "PongV2.kt"
```

For variable inspection: `.sym` file maps symbol names to addresses. The `MemoryInspectorPanel`/`NamedVariablesTab` already reads `.sym` — the agent `VariableInspector` follows the same pattern.

### Gradle Task Registration Pattern

```kotlin
// Source: GbktPlugin.kt — follow same pattern for new agent tasks
project.tasks.register<CaptureScreenshotTask>("captureScreenshot") {
    group = "gbkt-agent"
    description = "Capture a screenshot from the ROM after N frames"
    dependsOn("buildRom")
    romFile.set(romOutputFile)
    buildDirectory.set(project.layout.buildDirectory)
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `getDebugLog()` scan | `EmuPrintfInterceptor` with structured `DebugLogEntry` (level, timestamp, kotlin source) | Phase 06.12 | Logs now carry Kotlin file:line via source maps — actionable for debugging |
| External mGBA only | Embedded Coffee-GB + mGBA both required to pass | Phase 07 | Two-emulator validation catches emulator-specific bugs |
| `emulatorTest` = "no crashes" | Agent DX toolkit = input scripting + variable reads + visual diff | Phase 07 | "Gameplay correctness" becomes automated, not just "no crashes" |
| Debug log file + manual inspection | `captureScreenshot` + visual diff + `readVariable` | Phase 07 | Agent can validate game state without human eyes |

**Deprecated/outdated:**
- `raw()` / `assign()` / `varRef()` / `literal()`: deprecated DSL surface — game files use modern delegate operators. No impact on UAT, but bug fixes should not introduce these.
- `battleEngine()`: experimental, not used in any example game. Not in UAT scope.

---

## Open Questions

1. **GBC emulator type in Coffee-GB**
   - What we know: `CoffeeGbEmulator.start()` hardcodes `GameboyType.DMG`. platformer-gbc is a GBC ROM.
   - What's unclear: Whether Coffee-GB's `GameboyType.GBC` supports the `platformer-gbc` ROM correctly, and whether `EmulatorConfig` needs a `gbcMode` field.
   - Recommendation: Plan 1 (agent toolkit) should add `gbcMode: GameboyType` to `AgentSessionConfig` and test with `platformer-gbc`. Resolve before GBC UAT.

2. **Savestate API scope**
   - What we know: `getMemory()` provides MMU-backed read/write for 0x0000-0xFFFF. CPU registers are not directly accessible via `MemoryAccess`.
   - What's unclear: Whether WRAM + OAM snapshot is sufficient for reproducible savestates, or if registers (PC, SP) must also be captured.
   - Recommendation: For UAT savestates (checkpoint before a tricky section), WRAM + OAM is likely sufficient since we control the input sequence from the start. Full register save can be deferred if it requires Coffee-GB internals.

3. **Symbol file format for variable inspection**
   - What we know: `NamedVariablesTab.kt` reads `.sym` files to display variable names + addresses. `SourceMapResolver` reads `.noi` files.
   - What's unclear: Whether DSL variable names (e.g., `score`, `ballDx`) appear directly in the `.sym` file, or whether they're mangled by SDCC/lcc (prefixed with `_`).
   - Recommendation: Inspect an actual `.sym` file from a built Pong ROM to confirm naming. `SourceMapResolver.isFunctionSymbol()` already strips `_` prefix — variable symbols likely follow same convention (`_score` → `score`).

4. **Visual diff tolerance for DMG vs GBC**
   - What we know: DMG games use 4 shades of gray. GBC games use 15-bit color palettes.
   - What's unclear: Whether pixel-perfect tolerance (0) is achievable for DMG games, or whether Coffee-GB's rendering introduces minor shade variations between frames.
   - Recommendation: Default tolerance = 0 for DMG (4 fixed shades), 5% for GBC (palette interpolation may vary). Configurable as Claude's discretion per CONTEXT.md.

---

## Validation Architecture

> `workflow.nyquist_validation` is not explicitly set to false — section included.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 (existing in all modules) |
| Config file | `build.gradle.kts` in each module — `tasks.test { useJUnitPlatform() }` |
| Quick run command | `./gradlew :gbkt-emulator:test` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UAT-01 | Agent DX toolkit exists and APIs work | unit | `./gradlew :gbkt-emulator:test` | ❌ Wave 0 (new `agent/` tests) |
| UAT-01 | Screenshot capture produces PNG + JSON sidecar | unit | `./gradlew :gbkt-emulator:test --tests "*ScreenshotCaptureTest*"` | ❌ Wave 0 |
| UAT-01 | Input script player injects events into EventBus | unit | `./gradlew :gbkt-emulator:test --tests "*InputScriptPlayerTest*"` | ❌ Wave 0 |
| UAT-01 | Variable inspector reads named variable from .sym | unit | `./gradlew :gbkt-emulator:test --tests "*VariableInspectorTest*"` | ❌ Wave 0 |
| UAT-01 | Visual diff reports mismatch on different screenshots | unit | `./gradlew :gbkt-emulator:test --tests "*VisualDiffTest*"` | ❌ Wave 0 |
| UAT-01 | Savestate round-trips WRAM correctly | unit | `./gradlew :gbkt-emulator:test --tests "*SavestateManagerTest*"` | ❌ Wave 0 |
| UAT-01 | Per-game headless smoke tests (no crash, no error log) | integration | `./gradlew emulatorTest` (per game) | ❌ Wave 0 |
| UAT-02 | UAT_GUIDE.md exists with required sections | manual | manual review | ❌ Wave 0 |
| UAT-02 | Per-game UAT checklists exist and are complete | manual | manual review | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :gbkt-emulator:test`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** `./gradlew test` green + `./gradlew emulatorTest` (all games) green + manual UAT checklists 100% pass before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/AgentDebugSessionTest.kt` — covers UAT-01 agent session lifecycle
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/ScreenshotCaptureTest.kt` — covers screenshot + sidecar
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/InputScriptPlayerTest.kt` — covers input injection
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VariableInspectorTest.kt` — covers .sym variable lookup
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VisualDiffTest.kt` — covers pixel comparison
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/SavestateManagerTest.kt` — covers state serialization
- [ ] Framework install: not needed — JUnit Jupiter already configured

---

## Sources

### Primary (HIGH confidence)

- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/GbEmulator.kt` — public interface contract
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt` — implementation details, callback hooks, frame buffer, memory access
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/EmulatorSession.kt` — launch patterns, EventBus wiring, headless vs GUI
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/EmulatorConfig.kt` — configuration API
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/debug/EmuPrintfInterceptor.kt` — EMU_printf trap detection
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/debug/SourceMapResolver.kt` — source map + .noi file loading
- `gbkt-gradle-plugin/src/main/kotlin/.../tasks/EmulatorTestTask.kt` — headless stepFrame pattern
- `gbkt-gradle-plugin/src/main/kotlin/.../tasks/RunEmulatorTask.kt` — task registration pattern
- `gbkt-gradle-plugin/src/main/kotlin/.../tasks/DebugEmulatorTask.kt` — debug task pattern
- `gbkt-emulator/src/test/kotlin/.../integration/EmulatorIntegrationTest.kt` — real ROM test pattern
- `gbkt-emulator/src/test/kotlin/.../CoffeeGbEmulatorTest.kt` — unit test patterns
- `gbkt-examples/CLAUDE.md` — example game structure and test conventions
- `CLAUDE.md` (root) — project-wide conventions, module architecture

### Secondary (MEDIUM confidence)

- `gbkt-examples/pong/src/main/kotlin/.../PongV2.kt` — game mechanics for UAT scenario design
- `gbkt-examples/platformer/src/main/kotlin/.../Platformer.kt` — physics system mechanics
- `gbkt-examples/explorer/src/main/kotlin/.../ExplorerV2.kt` — RPG/exploration mechanics
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/ui/InputHandler.kt` — EventBus input injection pattern (inferred from EmulatorSession.kt wiring)

---

## Metadata

**Confidence breakdown:**
- Standard stack (existing components): HIGH — all verified from source code
- Agent DX toolkit design: HIGH — patterns derived directly from existing code
- Savestate implementation: MEDIUM — memory regions confirmed, CPU register scope unclear
- GBC emulator type gap: MEDIUM — identified as open question, needs validation

**Research date:** 2026-03-13
**Valid until:** 2026-04-13 (stable internal codebase — changes only if Phase 07 implementation diverges)
