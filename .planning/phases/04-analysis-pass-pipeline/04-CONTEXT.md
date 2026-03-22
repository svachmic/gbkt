# Phase 4: Analysis Pass Pipeline - Context

**Gathered:** 2026-02-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Nine ordered compiler passes that analyze the IR and assign Game Boy hardware resources (banks, VRAM tile slots, OAM sprite slots, RAM layout) without developer intervention. Budget audit produces an actionable build report. Bank allocation is fully automatic.

</domain>

<decisions>
## Implementation Decisions

### Bank Allocation Strategy
- Trampoline generation is fully automatic — analysis pass inserts trampoline stubs in HOME bank for any cross-bank calls, developer never thinks about it
- Bank budget limit is configurable per game (MBC1=32, MBC3=128, MBC5=256) — catches oversized games early for cartridge targeting
- DSL config block sets max banks per MBC type

### Error & Warning Messages
- Tile overflow errors include scene name + breakdown by source (which tilesets/sprites contribute how many tiles) — actionable guidance
- Tile overflow errors also suggest splitting strategies ("Consider splitting tileset X into a sub-scene")
- Warnings (bank fullness, OAM scanline density) shown during every build — developer always sees resource pressure
- Budget report: ASCII table in terminal (per-bank size bars, per-scene tile usage, OAM slots)
- Resource thresholds are configurable with reasonable defaults that are overridable per game

### VRAM Tile Slot Planning
- Hybrid deduplication: common tiles (UI, fonts) get fixed global slots; scene-specific tiles are allocated per-scene
- Sprite VRAM reservation computed per-scene based on actor analysis — maximizes BG tile budget per scene
- Scene transitions: middle ground — aim for smooth-ish transitions without constraining the allocator (best-effort common tile slot reuse, not guaranteed)

### Pass Pipeline Design
- Fail fast on first error — no point running VRAM planning if semantic validation failed
- Pipeline is open with plugin API — users can register custom passes
- Custom pass extension points: before built-in passes and after built-in passes (two hooks, simple API)

### Claude's Discretion
- Bank allocation algorithm choice (FFD bin-packing strategy, scene locality grouping, bank fill headroom policy) — guiding principle: performance is paramount, memory correctness is non-negotiable
- Pass output format (structured annotations vs side effects) — Claude picks based on compiler design best practices
- Exact warning threshold defaults
- Budget report column layout and formatting

</decisions>

<specifics>
## Specific Ideas

- Performance is key for GBDK-2020 — if memory is not working, the game is not working
- Budget report should feel like Rust's `cargo build` output — clean ASCII tables in terminal
- Collection abstractions (Phase 3.1) will exist in IR by the time Phase 4 executes — RAMPlanningPass must account for their memory budgets

</specifics>

<deferred>
## Deferred Ideas

- **Collection Abstractions Phase (3.1):** IRHashTable, IRPool, IRRingBuffer, IRFixedSlots as first-class IR nodes with hybrid backend traits — to be inserted before Phase 4 via `/gsd:insert-phase`. Context already captured in `.planning/phases/03.1-collection-abstractions/03.1-CONTEXT.md`

</deferred>

---

*Phase: 04-analysis-pass-pipeline*
*Context gathered: 2026-02-18*
