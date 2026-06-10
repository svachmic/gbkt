# Phase 07: UAT Gameplay Validation - Context

**Gathered:** 2026-03-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Verify all example ROMs actually play correctly — not just compile and boot. Build a full agent debugging suite (the "Playwright for Game Boy" tooling) FIRST, then use it to systematically validate every example game through per-game UAT checklists. Debug and fix all issues found. Document the debugging workflow in a comprehensive UAT guide.

**Scope includes:**
- Agent DX debugging toolkit (prerequisite — built first)
- Full regression UAT for: Pong, Breakout, Explorer, Platformer, Shmup, Racer, Dungeon, RPG-Lite + GBC variants (platformer-gbc)
- Inline bug fixing with regression tests
- UAT guide document (context/UAT_GUIDE.md)
- CLAUDE.md cross-references

**Scope excludes:**
- LotD UAT (deferred to Phase 07.1)
- Real hardware testing
- MCP server wrapper for emulator tools

</domain>

<decisions>
## Implementation Decisions

### Agent DX Debugging Suite (PREREQUISITE — BUILD FIRST)
- Full agent debugging suite is the **first deliverable** of this phase — all subsequent UAT work depends on it
- Interface: Gradle tasks + CLI commands (Claude calls via Bash tool)
- **Screenshot capture:** PNG + metadata JSON sidecar (frame number, scene name, key variable snapshot) in `build/gbkt/screenshots/{game}_{frame}_{label}.png`
- **Input scripting:** Kotlin DSL — type-safe `press(RIGHT, frames=30); press(A); wait(60)` sequences run via Gradle
- **Variable inspection:** Both DSL-level named variables (`score`, `ball.x`, `lives`) AND raw memory address reads. Source maps + symbol table resolve names to addresses
- **Savestates:** Full emulator state save/restore to file. Claude can checkpoint before tricky sections, test variations, revert
- **Visual diff:** Pixel-level screenshot comparison with configurable tolerance (ignore 1-pixel timing shifts). Output diff image highlighting changes
- **State introspection:** Full game state awareness — current scene, active sprites, loaded tilemap, battle state, flag values via source map + memory inspection
- **Automated headless suite:** Separate `./gradlew emulatorTest` task. Script input sequences in headless mode for deterministic scenarios. Not part of `./gradlew test` (slower, optional in CI)

### Validation Scope & Depth
- **Full regression suite** for ALL example games: Pong, Breakout, Explorer, Platformer, Shmup, Racer, Dungeon, RPG-Lite
- **GBC variants tested too** — platformer-gbc (and any other GBC builds) validated alongside base versions. Color palettes, double-speed mode verified
- **LotD excluded** — deferred to Phase 07.1 subphase (too large for core UAT phase)
- **Per-game UAT checklists** — each game gets a `UAT-{game}.md` with:
  - Numbered test scenarios with expected behavior
  - **Dual emulator columns:** Coffee-GB pass/fail AND mGBA pass/fail (both must pass)
  - Iteration tracking: attempt 1: fail (bug #X), attempt 2: pass after fix
  - Visual inspection items (sprite rendering, tile alignment, palette correctness)
  - Expected feel descriptions ("ball should feel snappy, not floaty")
  - Pass/fail for each scenario
- **Happy path + edge cases + boundary conditions:** What happens at screen edges, zero lives, full inventory, max score, etc.

### Execution Model
- **Claude drives the entire process** — creates checklists, identifies issues, proposes fixes, documents everything for reproducibility
- **User plays and verifies** — manually play-tests in emulators, reports results back to Claude
- **Claude fixes inline** — iterate until all scenarios pass
- **Automated pre-checks first** — headless emulator tests run deterministic scenarios before manual playtesting begins

### Bug Handling Policy
- **Fix inline immediately** — every bug found during UAT gets fixed before moving to the next game. No log-and-defer
- **Generic framework fixes only** — no game-specific workarounds. Same principle as Phase 06.11: every fix must be reusable for "the next game"
- **Retest all affected games** after framework fixes — framework change triggers re-running affected UAT checklist items across all games
- **All ROMs must build at all times** — every commit must leave all example ROMs building. `./gradlew build` as gate after framework fixes
- **One atomic commit per bug** — each bug gets its own commit with descriptive message. Clean bisectable history
- **Always add a regression test** — every bug fix includes a JVM-level regression test (SimulationContext, IR test, or headless emulator test). No exceptions
- **100% pass required** — phase is not complete until every checklist item for every game passes in both emulators

### Emulator Testing Strategy
- **Both Coffee-GB and mGBA must pass** — a test scenario passes only if it works in BOTH emulators
- **Investigate root cause on disagreements** — check GB hardware docs / other emulators to determine which is correct. Fix game if game is wrong, document emulator bug if emulator is wrong
- **Emulator-only** — no real hardware testing in this phase
- **Headless automated suite** via `./gradlew emulatorTest` — separate from unit tests, optional in CI

### Debugging Workflow Document
- **Location:** `context/UAT_GUIDE.md`
- **Scope:** Full UAT playbook — not just debugging workflow
- **Content:**
  - How to write UAT checklists (format standard for future games)
  - How to run games in embedded emulator and mGBA
  - How to use debug logs (EMU_printf → structured log with source map correlation)
  - How to read source maps to trace issues to DSL code
  - How to use the agent debugging suite (screenshots, input scripting, variable inspection, savestates, visual diff)
  - Comprehensive troubleshooting section: symptom → cause → fix tables (bank overflow, sprite glitches, sound issues, etc.)
  - **Real worked examples** from this phase's actual UAT bugs (2-3 real debugging walkthroughs with actual log output)
- **Audience:** Both humans (future gbkt users) and Claude agents
- **CLAUDE.md updated** with references to UAT_GUIDE.md in Documentation Index and Common Tasks Routing

### Planning Constraints — HARD REQUIREMENTS
- **Agent DX tooling goes FIRST.** It is the prerequisite. All UAT work builds on top of it
- **Small, focused plans.** Iteration over big swings. Each plan does ONE thing well
- **Quality over speed.** Completeness and correctness are the priority
- **Every plan must be complete within its scope.** No "we'll wire this up later"

### Claude's Discretion
- Exact Gradle task names and CLI argument design for agent debugging tools
- Order of game validation (which game first)
- Test scenario decomposition (how many scenarios per game)
- Specific automated test scripts for headless mode
- Internal tooling implementation details (how screenshot capture hooks into Coffee-GB, etc.)
- Visual diff tolerance thresholds

</decisions>

<specifics>
## Specific Ideas

- "We need the same agent DX like with Playwright MCP and Chrome DevTools MCP — we need to have a way for agents to debug games real-time, not just by me playing and reporting 'hey this probably doesn't work'. We need rich logging and rich toolset for agents to be able to debug."
- "The DX tooling really has to go FIRST — otherwise the rest is not possible or rather is possible, but really difficult to perform."
- "Small, focused plans during planning, in order to guarantee quality. Iteration over big swings."
- "Claude drives the entire process and documents fully for reproducibility. I play and verify correctness and I report back to Claude for corrections. We iterate and make the games complete."
- "Future development of tooling should also involve Claude being able to play and capture screenshots for full autonomy."
- "Full regression suite" — every mechanic has a documented test scenario with expected behavior
- Track UAT iterations: attempt 1: fail (bug #X), attempt 2: pass after fix — full audit trail

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GbEmulator` interface (`gbkt-emulator`): `start()`, `pause()`, `resume()`, `stepFrame()`, `setSpeed()`, `getFrameBuffer()`, `getMemory()`, `getDebugLog()` — foundation for agent debugging tools
- `CoffeeGbEmulator`: Coffee-GB integration with `ld d,d` trap for EMU_printf capture — debug log capture already works
- `EmulatorSession`: Manages emulator lifecycle — extend for headless mode and input scripting
- `SourceMapCollector` / `SourceMapLoader`: Generate and load `.gbkt.map` files — enables DSL variable name → memory address resolution
- `SimulationContextV2` / `ScriptOpInterpreter`: JVM-level game logic testing — existing pattern for regression tests
- `RunEmulatorTask`, `DebugEmulatorTask`, `EmulatorTestTask`: Existing Gradle task patterns — follow for new agent debugging tasks
- All example game ROMs already built: pong.gb, breakout.gb, explorer.gb, platformer.gb, shmup.gb, racer.gb, dungeon.gb, rpg-lite.gb, labyrinth_en.gb

### Established Patterns
- Gradle plugin registers tasks via `GbktPlugin.kt` with task dependencies
- Source maps generated as `main.c.gbkt.map` alongside C output
- Coffee-GB headless API: `GameboyConfiguration` → `Gameboy.tick()` → `EventBus` frame events
- Coffee-GB input: `ButtonPressEvent`/`ButtonReleaseEvent` via EventBus
- Coffee-GB memory: `getAddressSpace().getByte(addr)` / `setByte(addr, val)`
- Coffee-GB frame stepping: `tick()` returns per T-cycle, `TICKS_PER_FRAME = 69,905`

### Integration Points
- New agent debugging Gradle tasks registered alongside existing emulator tasks
- Headless mode builds on `GbEmulator` interface — no GUI, just tick + capture
- Screenshot capture hooks into Coffee-GB's `Display` frame buffer via `EventBus`
- Variable name resolution: `.gbkt.map` → C variable name → `.sym` file → memory address
- Input scripting: Kotlin DSL compiled and executed via Gradle, injecting `ButtonPressEvent`/`ButtonReleaseEvent`

</code_context>

<deferred>
## Deferred Ideas

- **LotD UAT** — separate Phase 07.1 subphase (LotD is too large for the core UAT phase)
- **Claude-autonomous playtesting (fully self-directed)** — future evolution where Claude plays entire games start-to-finish making real-time decisions without human involvement. Phase 07 DOES build all the individual agent-accessible tools (screenshots, input scripting, variable inspection, savestates, visual diff, state introspection) — what's deferred is only the closed-loop AI player that chains these tools autonomously without a human in the loop
- **MCP server for emulator tools** — wrap Gradle tasks as MCP server for richer Claude integration (analogous to Playwright MCP)
- **Real hardware testing** — flash cart validation on actual GB/GBC hardware
- **GIF sequence capture** — multi-frame animations for motion/transition validation

</deferred>

---

*Phase: 07-uat-gameplay-validation*
*Context gathered: 2026-03-13*
