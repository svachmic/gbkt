# Phase 5: Integration and End-to-End Validation - Context

**Gathered:** 2026-02-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire the complete v2 compiler pipeline (DSL → IR → analysis → codegen → lcc) as Gradle tasks and validate all three example games (Pong, Breakout, Explorer) produce bootable .gb ROMs. The full Gradle task graph executes with correct ordering and incremental build support.

</domain>

<decisions>
## Implementation Decisions

### Validation depth
- "Runs correctly" means **boots without crash** — ROM loads in mGBA, no hangs or glitches within ~300 frames (~5 seconds)
- Same bar for all three games (Pong, Breakout, Explorer) — no gameplay walkthrough required in Phase 5
- Validation is **automated**: a Gradle task runs mGBA headless, boots the ROM, and verifies no crash within N frames
- Manual gameplay validation deferred to a separate UAT phase (see Deferred Ideas)

### Pipeline error experience
- lcc compilation errors are **mapped back to Kotlin DSL source** using the source map — developer sees DSL file:line, not generated C file:line
- Source map is a **separate .map file** alongside generated C (not inline comments)
- Analysis-level error presentation: Claude's discretion (Phase 4 passes already produce actionable messages)
- Pipeline failure strategy: Claude's discretion (fail-fast vs. collect-all)

### Gradle task granularity
- **Opinionated defaults with possible tweaking** — sensible task graph out of the box, configurable if developer needs to override
- **Full incremental builds** — use Gradle's up-to-date checking; skip stages whose inputs haven't changed
- Generated C files live in **`build/generated/gbkt/`** (e.g., `build/generated/gbkt/main.c`, `build/generated/gbkt/bank1.c`)
- Budget report **runs by default with every `buildRom`**, with a flag to disable if developer wants to skip (e.g., `gbkt { budgetReport = false }`)

### Explorer feature scope
- **Everything compiles** — all Explorer features (RPG combat, dungeon exploration, menus, encounters, items, abilities) must generate valid C and compile
- **Feature parity with v1** — ExplorerV2.kt must express everything the original Explorer does (all floors, encounters, items, abilities, combat)
- **Different C output is fine** — v2 pipeline can produce structurally different C than v1 as long as the ROM boots
- **OK to break v1 pipeline** — v1 codegen can break during Phase 5 wiring; it gets deleted in Phase 5.1. But: keep enough debugging capability for the UAT phase that follows

### Claude's Discretion
- Analysis diagnostic presentation format in Gradle output
- Pipeline failure strategy (fail-fast vs. collect-all)
- Exact mGBA headless invocation and frame count for automated ROM check
- Internal Gradle task decomposition (which stages are separate tasks vs. internal steps)
- Source map file format (JSON, text, etc.)

</decisions>

<specifics>
## Specific Ideas

- Automated ROM validation: run mGBA in headless mode for ~300 frames, check for crash/hang as pass/fail signal
- Source map should support future IntelliJ plugin integration (side-by-side Kotlin DSL ↔ generated C view)
- Budget report as default-on with opt-out flag follows the "opinionated defaults" philosophy

</specifics>

<deferred>
## Deferred Ideas

- **UAT manual validation phase** — A phase between Phase 5 and Phase 5.1 where all three games are manually played/debugged in mGBA to verify actual gameplay works (not just boot). User wants effective debugging support during this phase.
- **IntelliJ plugin source-map viewer** — Read the .map file and show side-by-side Kotlin DSL ↔ generated C mapping in the IDE. Add to roadmap as future IntelliJ plugin feature.

</deferred>

---

*Phase: 05-integration-and-end-to-end-validation*
*Context gathered: 2026-02-19*
