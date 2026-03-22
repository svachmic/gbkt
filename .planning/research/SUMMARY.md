# Project Research Summary

**Project:** gbkt — Kotlin DSL-to-C Compiler Framework for Game Boy
**Domain:** Retro game compiler framework (Kotlin DSL → GBDK C → .gb ROM)
**Researched:** 2026-02-17
**Confidence:** HIGH (codebase-grounded research; niche GBDK tooling MEDIUM)

## Executive Summary

gbkt is a compiler framework, not a game engine. The fundamental value proposition is eliminating the manual hardware resource management that makes Game Boy development with raw GBDK painful: developers declare a game in Kotlin DSL, and gbkt automatically bin-packs code into ROM banks, assigns VRAM tile slots, allocates OAM sprite slots, and plans WRAM layouts — all before compilation, with actionable build errors rather than cryptic runtime glitches. The rebuild is not a greenfield project; it is an incremental restructuring of a working but fragile codebase toward a clean compiler architecture: ordered analysis passes feeding a structured C AST codegen layer.

The recommended architecture follows LLVM-style pass pipeline conventions adapted to the Game Boy domain: a Kotlin DSL recording context emits a typed IR (sealed interfaces, exhaustive when-matching), nine ordered analysis passes annotate that IR with hardware resource assignments, and a structured C AST codegen layer (not StringBuilder concatenation) transforms annotated IR into bank-split C files for GBDK lcc to compile. The single most important structural change is eliminating mutable bank state in the code generator and replacing it with bank assignments as typed annotations on IR nodes — output of the analysis pass, not side effects during code emission. This eliminates the entire class of "bank state leak" bugs that caused the v1 rebuild decision.

The dominant risk is not technical — Kotlin 2.3.0 sealed types, Gradle 9.0, and GBDK 2020 are all proven. The risk is process: letting the complexity of the LabyrinthOfTheDragon port re-infiltrate the new architecture through one-game assumptions in core codegen, through InlineC escape hatches in framework internals, or through an indefinitely open migration seam where both old and new pipelines coexist. The prevention strategy is strict: all three example games (Pong, Breakout, Explorer) must compile through the new pipeline before any RPG-specific codegen is written, and the old GBDKCodeGenerator must be deleted — not deprecated — by the end of Phase 2.

---

## Key Findings

### Recommended Stack

The existing Kotlin 2.3.0 / Gradle 9.0 / JVM 21 stack requires no changes at the language level. The critical additions are structural: a custom internal C AST (approximately 400 lines of Kotlin data classes covering CFile, CFunction, CStatement, CExpression, CPragma) replaces the StringBuilder-based codegen. No external C code generation library exists for the JVM — KotlinPoet generates Kotlin/Java only, and LLVM bindings are heavyweight and inappropriate for a 4 MHz Z80 target. The custom C AST is the right tool: it makes bank splitting a 3-line groupBy call rather than 250 lines of fragile regex.

For the asset pipeline, Java's built-in ImageIO (JDK 21, zero additional dependency) is sufficient for PNG reading and 2bpp tile encoding. scrimage-core 4.3.6 (Feb 2025) should be added only if sprite sheet resizing or palette normalization is needed. Kotest should be upgraded to 6.1.3 for property-based testing of bank packing algorithms; this is the correct tool for verifying allocator correctness across hundreds of random game configurations.

**Core technologies:**
- Kotlin 2.3.0 with sealed interfaces — foundational exhaustive IR dispatch; no changes needed
- Custom internal C AST (CFile/CFunction/CStatement/CExpression) — replaces StringBuilder codegen; eliminates splitByBank() regex entirely
- Custom FFD bin-packing pass (BankingAnalysisPass) — extends existing BankAllocator; handles code sections and data in a unified pass
- Java ImageIO (JDK 21 built-in) — PNG to 2bpp tile encoding; zero additional dependency
- Kotest 6.1.3 — property-based testing for bank packing and IR transformation algorithms
- GBDK 2020 + bankpack — external toolchain; gbkt pre-validates bank assignments before lcc invocation

### Expected Features

The feature landscape divides cleanly between table stakes (what every GB framework provides) and differentiators (what only gbkt provides). The differentiators are almost entirely compiler infrastructure — the intelligence layer that automatically manages hardware resources the developer would otherwise track manually.

**Must have (table stakes):**
- Scene system with enter/update/exit lifecycle
- Sprite/entity system with 8x8 and 8x16 hardware sprites
- Input handling with pressed/held/released per button
- Tilemap/background support with Tiled TMX integration
- Asset pipeline (PNG to 2bpp tiles, Tiled TMX to tilemaps) as Gradle task
- ROM compilation via GBDK lcc in Gradle buildRom task
- Save/load system (SRAM) with type-safe API
- Camera system with scroll and bounds clamping
- Dialog and text rendering on window layer (not background — prevents tile corruption)
- Menu system with navigation and cursor
- Audio via hUGEDriver (.uge music files, SFX triggers)
- Collision detection (AABB entity-to-entity, entity-to-tilemap)
- Working example games: Pong, Breakout, Explorer

**Should have (differentiators — the core reason gbkt exists):**
- Automatic bank allocation (FFD bin-packing, trampoline generation, scene locality optimization) — no other GB framework provides pre-compile bank validation
- Automatic VRAM planning (per-scene tile slot assignment, tile deduplication, shared tile detection across transitions)
- Automatic OAM planning (sprite slot allocation, scanline density analysis, compile-time warnings)
- Automatic RAM planning (WRAM layout, SRAM save structure, HRAM allocation)
- Budget audit pass with actionable build errors ("Scene 'dungeon' uses 401 tiles, max 384")
- Structured C AST codegen enabling optimization passes and future backends
- JVM test runner (game logic tests in milliseconds on JVM, no emulator required) — unique in the GB homebrew ecosystem
- Compile-time reference resolution (broken asset paths and missing scene refs fail the build)
- gradle budgetReport standalone task

**Defer (v2+):**
- IntelliJ plugin real-time inspections (requires stable IR API and IntelliJ SDK work)
- GBA backend (architecture is ready; wait until GB backend is mature)
- Physics platformer library, dialog engine library, battle engine library as optional modules
- LDtk map format support, auto-download GBDK, link cable multiplayer

### Architecture Approach

The architecture is a three-layer compiler pipeline separated by explicit data contracts: Frontend (DSL recording context emits GameIR sealed types), Analysis (nine ordered passes annotate GameIR with hardware resource assignments producing AnnotatedGameIR), and Codegen (IR visitors build C AST nodes that BankAwareEmitter formats into bank-split C source files). A new gbkt-analysis module holds the pass pipeline and is the key structural addition — it depends on gbkt-core for IR types but has no backend dependency, making all analysis passes testable independently of GBDK.

**Major components:**
1. gbkt-core (IR + DSL) — sealed IR hierarchies that must co-locate due to Kotlin module constraint; RecordingContext captures DSL execution as IR nodes; all game-domain types live here
2. gbkt-analysis (NEW) — nine ordered AnalysisPass implementations; each pass takes GameIR and returns annotated GameIR; pipeline executor enforces ordering and pass dependencies
3. gbkt-backend-gbdk (C AST + codegen) — CStatement/CExpression/CUnit data classes; domain-specific IR visitors (StatementVisitor, RPGVisitor, WorldVisitor, GraphicsVisitor); BankAwareEmitter handles pragma injection structurally rather than through mutable state
4. gbkt-gradle-plugin (build orchestration) — unchanged structure; wires analysis pipeline and backend; invokes lcc compiler
5. gbkt-test-runner — JVM ScriptOp interpreter; frame simulation; input injection; variable assertion; no emulator required

**Build order for implementation (critical):**
1. IR refinement (pure sealed types, deepCopy, IRWalker) — no dependencies
2. DSL stabilization (RecordingContext, operators, scope markers) — depends on IR
3. Analysis module (9 passes) — depends on IR only
4. C AST types in codegen (independent — can run in parallel with step 3)
5. CEmitter + BankAwareEmitter — depends on C AST types
6. IR visitors in codegen — depends on IR, AnnotatedGameIR, C AST
7. GBDKCodeGenerator refactoring — depends on all codegen pieces
8. Gradle plugin and CLI wiring — depends on analysis pipeline and backend
9. JVM test runner — depends on IR and DSL only

### Critical Pitfalls

1. **One-game coupling (LabyrinthOfTheDragon patterns in core codegen)** — Write Pong, Breakout, and Explorer DSL definitions before any RPG-specific codegen. If a function in core/ branches on game.monsters.isNotEmpty(), it is domain logic in the wrong layer. The three examples are the regression gate: if Pong's generated C contains any RPG symbol names, unconditional generation is still active.

2. **Mutable bank state in code generator** — The current var currentBank field modified by setBank()/returnToHome() side effects is the documented root cause of the most fragile bugs. In the new architecture, bank assignment is an analysis pass output (BankingAnalysisPass annotates IR nodes with bankSlot). CFunction carries bank as a typed field. BankAwareEmitter emits pragma transitions by comparing adjacent nodes — no mutable state, no forgettable restore call.

3. **Sealed interface hierarchy in wrong module** — Kotlin requires all sealed implementations in the same compilation module. Plan the complete IR type hierarchy (including domain subtypes: IRBattleAction, IRExplorationStep) before writing any codegen. Moving sealed types after the module structure is load-bearing requires recompiling all consumers and cannot be done incrementally.

4. **Migration seam never closing** — Running old and new codegen pipelines in parallel beyond two weeks creates double maintenance burden. The seam must be defined at the IR boundary on day one. The old GBDKCodeGenerator must be deleted (not deprecated) when the first example game compiles through the new pipeline.

5. **InlineC escape hatch in framework internals** — The CStatement.InlineC node is designed for user-authored inlineC {} DSL blocks. Its use in framework-generated codegen recreates string interpolation inside the supposedly structured AST. Establish the rule before the first PR: no InlineC in any code the framework generates — extend the CStatement hierarchy instead.

---

## Implications for Roadmap

Based on the combined research, the implementation should follow the dependency ordering derived from ARCHITECTURE.md, with pitfall prevention woven into each phase gate.

### Phase 1: IR Foundation and DSL Stabilization

**Rationale:** Sealed IR hierarchies are the root dependency for every other component (analysis passes, codegen visitors, JVM test runner, IntelliJ plugin). Getting the IR wrong — or placing sealed types in the wrong module — forces a cascade rewrite later. This must be correct before any other work begins.

**Delivers:** Complete sealed IRStatement/IRExpression hierarchy covering all three example games without RPG nodes; stable RecordingContext; IRWalker pattern; deepCopy for all IR nodes; gbkt-analysis module skeleton with AnalysisPass interface.

**Addresses (from FEATURES.md):** Semantic IR as module contract (differentiator P1); compile-time ref resolution (P1)

**Avoids (from PITFALLS.md):** Sealed interface in wrong module (Pitfall 3); one-game coupling infiltrating IR design (Pitfall 1)

**Gate:** Pong, Breakout, and Explorer DSL definitions exist and produce valid IR — no RPG IR nodes required.

**Research flag:** Low risk — established compiler patterns; no research-phase needed.

### Phase 2: Structured C AST Codegen and Migration Cut

**Rationale:** String-based codegen is the root cause of bank state leaks, untestable output, and the splitByBank() complexity. The new C AST layer must replace the old GBDKCodeGenerator completely — not coexist with it. The migration seam closes in this phase.

**Delivers:** CFile/CFunction/CStatement/CExpression/CUnit sealed hierarchy; CEmitter with source mapping; BankAwareEmitter with structural pragma emission (no mutable currentBank); IR visitors for statements, expressions, and graphics; multi-file output (scenes/, actors/, systems/ subdirectories); feature-gated generation (Pong output contains no RPG symbols); old GBDKCodeGenerator deleted.

**Uses (from STACK.md):** Custom C AST (internal, ~400 lines); no string regex in codegen

**Implements (from ARCHITECTURE.md):** gbkt-backend-gbdk/codegen/ast/ and emit/ components; BankAwareEmitter; domain-specific visitors

**Avoids (from PITFALLS.md):** Bank state as global mutable (Pitfall 2); single StringBuilder output (Pitfall 8); InlineC in framework codegen (Pitfall 6); migration seam never closing (Pitfall 5)

**Gate:** Pong compiles end-to-end through new pipeline; old GBDKCodeGenerator is removed from codebase.

**Research flag:** Low risk — well-understood structured codegen patterns. BANKED calling convention details are documented; refer to GBDK docs during implementation.

### Phase 3: Asset Pipeline and JVM Test Runner

**Rationale:** The asset pipeline feeds VRAM planning (which cannot run without processed tile data), making it a prerequisite for Phase 4 analysis passes. The JVM test runner only requires the IR layer (completed in Phase 1) and can be built in parallel with asset pipeline work — both are independent of codegen.

**Delivers:** PNG to 2bpp tile encoding using Java ImageIO (zero new dependency for basic case); Tiled TMX to tilemap IR; sprite sheet slicing; asset hashing for deduplication; Gradle GenerateAssetsTask integration; ScriptOp interpreter executing game logic on JVM; SimulationContext with frame advance, input simulation, and variable assertions; example game logic tests running in CI without emulator.

**Uses (from STACK.md):** Java ImageIO (JDK 21 built-in); org.json (existing dep); scrimage-core 4.3.6 only if resizing is needed; Kotest property-based tests for tile encoding correctness

**Implements (from ARCHITECTURE.md):** :assets module; :test-runner module

**Avoids (from PITFALLS.md):** VRAM tilecount assumptions baked into IR (Pitfall 7 — IR fields for vramSlotAssignment must exist from Phase 1 as nullable fields)

**Gate:** All three example games' assets process without error; at least 10 JVM game logic tests pass in CI without GBDK installed.

**Research flag:** Asset encoding is well-documented (2bpp format via Pan Docs/RGBDS). JVM test runner pattern is novel — may need iteration on SimulationContext API design during implementation.

### Phase 4: Analysis Pass Pipeline

**Rationale:** Analysis passes are the core differentiator. They transform gbkt from a code generator into a compiler with intelligence. Bank allocation must run before full codegen; VRAM planning requires asset pipeline output (Phase 3). This phase delivers the "GC for hardware" value proposition.

**Delivers:** gbkt-analysis module with all nine ordered passes: SemanticValidation, ResourceInventory, ConstraintCheck, BankingAnalysis (FFD bin-packing), VRAMLayout (per-scene tile assignment, tile deduplication), OAMAllocation (scanline density analysis), DeadCodeElimination, ConstantFolding, AnnotatedIR; PassContext for inter-pass result sharing; gradle budgetReport task; build report with per-bank size breakdowns and per-scene tile budgets.

**Uses (from STACK.md):** Custom FFD bin-packing extending existing BankAllocator; Kotest property tests for BankingAnalysisPass correctness across random game configurations

**Implements (from ARCHITECTURE.md):** Full PassPipeline executor with ordering enforcement; AnnotatedGameIR type as contract between analysis and codegen

**Avoids (from PITFALLS.md):** GBDK Bank 0 overflow (Pitfall 4 — BankingAnalysisPass fails build if Bank 0 projected usage exceeds 14KB); VRAM tilecount assumptions (Pitfall 7 — VRAMLayoutPass populates nullable vramSlotAssignment fields)

**Gate:** budgetReport task runs on all three example games and outputs per-bank breakdowns; a deliberately oversized scene causes a build error with an actionable message.

**Research flag:** Bank allocation bin-packing is well-documented (First-Fit-Decreasing). Scene locality optimization (scenes that transition to each other share banks) may need iteration — no standard reference implementation exists; flag for deeper research during phase planning.

### Phase 5: Integration, End-to-End Validation, and Polish

**Rationale:** With all four layers in place (IR, codegen, assets, analysis), this phase wires them together through the Gradle plugin and CLI, runs all three example games through the complete pipeline, and addresses the verification checklist from PITFALLS.md.

**Delivers:** Complete Gradle plugin task graph (generateAssets → generateC → compileRom → runEmulator); CLI wiring; all three example games (Pong, Breakout, Explorer) compiling to working ROMs through the new pipeline; BANKED function declaration verification in generated game.h; tile deduplication in VRAM planning; scene transition VRAM delta planning; GBC palette system (8 bg + 8 sprite palettes); inlineC {} and inlineAsm {} formalized escape hatches.

**Addresses (from FEATURES.md):** Tile deduplication (P2); scene transition VRAM planning (P2); GBC palette system (P2); inlineC escape hatch (P2); gradle budgetReport (P2)

**Avoids (from PITFALLS.md):** BANKED function corruption (verify game.h has BANKED on all non-HOME functions); multi-file output verification (scenes/ directories exist); exhaustive IR coverage check (no else-> in when(irNode))

**Gate:** All three example games produce ROMs that run correctly in mGBA; BANKED functions verified via BGB emulator memory viewer; budgetReport shows no false positives.

**Research flag:** Scene transition VRAM delta scheduling and VBlank transfer timing are hardware-specific and may need consultation of Pan Docs + real hardware testing. Flag for research-phase during planning.

---

### Phase Ordering Rationale

- **IR must precede everything** because Kotlin sealed types cannot be split across modules and all other components (analysis, codegen, test runner) import IR types. Getting the hierarchy wrong in Phase 1 is the highest-cost mistake.
- **Codegen (Phase 2) must precede analysis integration (Phase 4)** because analysis passes need to attach annotations that codegen will read. The annotation fields (bankSlot, vramSlotAssignment, oamSlot) must exist on IR node types before analysis can populate them and codegen can read them.
- **Asset pipeline (Phase 3) can overlap Phase 2** because asset processing depends only on Phase 1 IR types and has no dependency on the codegen layer. JVM test runner can also be built in parallel with Phases 2 and 3.
- **Analysis passes (Phase 4) must follow asset pipeline (Phase 3)** because VRAM planning cannot compute tile budgets without processed tile data from the asset pipeline.
- **Phase 5 integration validates the full chain** — it is not a cleanup phase. The verification checklist from PITFALLS.md must be mechanically executed (grep for currentBank, InlineC, RPG symbols in Pong output, else-> in when-expressions).

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 4 (Bank allocation):** Scene locality optimization for bank grouping has no standard reference implementation; needs design session. First-Fit-Decreasing algorithm itself is standard.
- **Phase 5 (Scene transition VRAM):** VBlank transfer scheduling and delta tile computation are hardware-timing-sensitive; may need Pan Docs deep dive and mGBA debugging.

Phases with standard patterns (skip research-phase):
- **Phase 1 (IR foundation):** Sealed interface compiler patterns are well-established; Kotlin docs are authoritative.
- **Phase 2 (Structured codegen):** C AST + visitor pattern is classic compiler engineering; ample reference material.
- **Phase 3 (Asset pipeline):** 2bpp encoding format is fully documented in Pan Docs; JVM ImageIO is stable.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Existing Kotlin/Gradle/JVM stack is proven; C AST decision is grounded in codebase analysis; no external dependencies needed beyond Kotest upgrade |
| Features | HIGH | Primary source is the project's own PROJECT.md (authoritative vision); verified against GB Studio, ZGB, GBDK-2020, and Butano competitor analysis |
| Architecture | HIGH | Derived from direct codebase analysis (ARCHITECTURE.md, CONCERNS.md); LLVM pass pipeline is established compiler pattern; Kotlin sealed interface constraint is well-documented |
| Pitfalls | HIGH | Evidence grounded in actual codebase failures (MEMORY.md documents specific bugs); GBDKCodeGenerator source confirms 103 setBank/returnToHome calls as documented mutable state risk |

**Overall confidence:** HIGH

### Gaps to Address

- **Bank 0 size estimation before codegen:** The BankingAnalysisPass needs to estimate C code size from IR nodes before compilation. There is no established formula for IR node → generated C byte count. Initial implementation should use conservative heuristics and calibrate against actual .noi output from the three example games. Flag for iteration in Phase 4.

- **Scene locality optimization algorithm:** The claim that "scenes that transition to each other should share banks" is correct in principle but requires a concrete algorithm (graph partitioning on the scene transition graph). No reference implementation exists. This may be deferred to Phase 5 if Phase 4 basic bin-packing is sufficient for all three example games.

- **JVM test runner API design:** The SimulationContext API is described but not fully specified. The frame advance, input simulation, and variable assertion interfaces need design during Phase 3 implementation. No external reference exists — this is novel in the GB homebrew ecosystem.

- **GBDK lcc interaction with multi-file output:** The current single-file lcc invocation is understood. Multi-file invocation (passing scenes/*.c, actors/*.c) may have ordering requirements or linking nuances not covered in current GBDK documentation. Verify during Phase 5 integration.

---

## Sources

### Primary (HIGH confidence)
- gbkt PROJECT.md — authoritative vision, failure modes, rebuild requirements
- gbkt CLAUDE.md — existing feature list, architectural decisions, MEMORY.md bug records
- gbkt context/ARCHITECTURE.md — IR node inventory, data flow, module organization
- gbkt context/CONCERNS.md — documented fragile areas (bank state, monolithic codegen)
- gbkt-backend-gbdk/GBDKCodeGenerator.kt — direct evidence of mutable state problem (103 setBank calls)
- Kotlin sealed interface KEEP proposal — module constraint is confirmed, no relaxation planned
- LLVM New Pass Manager documentation — ordered pass pipeline pattern

### Secondary (MEDIUM confidence)
- GBDK-2020 documentation (ROM Banking, BANKED calling convention, bankpack tool)
- GB Studio feature analysis — competitor table stakes; automatic banking comparison
- ZGB framework analysis — bankpack integration; game state pattern
- Butano (GBA) architecture — RAII resource management as closest OAM/VRAM analog
- Pan Docs (gbdev.io) — OAM specification, VRAM layout, 2bpp tile format
- RGBDS rgbgfx — 2bpp encoding reference specification
- Kotest 6.1.3, scrimage 4.3.6, kotlinx-coroutines 1.10.2 release notes (Feb 2025)

### Tertiary (LOW confidence — needs validation during implementation)
- Scene locality optimization (graph partitioning approach) — no reference implementation found
- Multi-file lcc compilation ordering — not explicitly documented in GBDK API docs
- JVM-side IR node size estimation heuristics — no established formula exists

---
*Research completed: 2026-02-17*
*Ready for roadmap: yes*
