# Phase 3: Asset Pipeline and JVM Test Runner - Context

**Gathered:** 2026-02-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Asset files (PNG, TMX, LDtk) process into IR automatically as a Gradle task with incremental build support. Game logic runs on JVM without an emulator using a ScriptOp interpreter with full simulation (scenes, entities, collisions, input). Phase 4 depends on processed tile data from this pipeline.

</domain>

<decisions>
## Implementation Decisions

### Asset input formats
- Support both TMX (Tiled) and LDtk map formats
- Sprite sheet frame layout declared in Kotlin DSL alongside sprite() calls — no separate metadata files
- Palette enforcement: both strict and auto-quantize modes available, default to auto-quantize
- Collision layers identified by custom property flag (`gbkt_collision=true`) on any layer, not by naming convention

### Build integration
- Incremental builds from the start — use Gradle UP-TO-DATE checking with proper input/output annotations
- Asset validation failures fail the build immediately on first invalid asset (clear error with file path and issue)
- Processed asset output goes to `build/generated/assets/` — separate from C output, consumed by codegen as intermediate artifacts
- processAssets writes a JSON manifest (`asset-manifest.json`) listing all processed assets with metadata (tile count, palette, dimensions) for codegen to consume

### Test API design
- Both frame-by-frame stepping (`sim.advanceFrames(60)`) and run-until-condition (`sim.runUntil { score >= 10 }`) available on SimulationContext
- Both low-level input (press/release per frame) and high-level convenience (`sim.tap(A)`, `sim.holdDpad(RIGHT, frames=30)`) available
- Standard JUnit 5 assertions work out of the box; SimulationContext also exposes helper methods like `sim.assertVar("score", 10)` for convenience
- Optional frame-by-frame trace log via `sim.enableTracing()` — records state changes, printed on test failure for debugging

### Coverage scope
- Full game simulation on JVM — variables, scenes, entities, collisions, input all simulated
- Hardware-dependent features (VRAM writes, OAM, bank switches) use no-op stubs — tests verify logic, not rendering
- Collision detection runs on JVM — bounding box collision simulated so tests can verify collision triggers
- Each example game has 2-3 scenario-based test assertions (e.g., Pong: ball bounces off paddle, score increments), not just smoke tests

### Claude's Discretion
- LDtk parser implementation details (XML vs JSON parsing approach)
- Exact auto-quantize algorithm for palette mapping
- SimulationContext internal architecture (interpreter loop, state management)
- Specific test scenarios per example game (choosing which 2-3 behaviors to test)
- Frame trace log format and verbosity

</decisions>

<specifics>
## Specific Ideas

- DSL-declared sprite metadata means the pipeline reads frame layout from IR — no sidecar files to maintain
- JSON manifest bridges the Gradle task boundary: processAssets writes it, codegen reads it
- The 5-second test budget across all three games implies tests should be fast and focused — scenario-based, not exhaustive

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-asset-pipeline-and-jvm-test-runner*
*Context gathered: 2026-02-18*
