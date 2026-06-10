# Milestones

## v0.1.0 MVP — Compiler Pipeline Rebuild (Shipped: 2026-06-09)

**Phases completed:** 66 phases, 652 plans, 887 tasks
**Git timeline:** 2026-01-04 → 2026-06-09
**Release gate:** Full-green JVM test suite — `./gradlew test --continue` (0 failures) + `./gradlew pluginTest` (IntegrationTest 19/0/0/0), reached diagnose-first with zero threshold-weakening and zero production-codegen drift (Phase 15, VERIFICATION passed 7/7).

**Key accomplishments:**

- **Clean compiler pipeline (DSL → IR → analysis → codegen → C):** replaced the string-concatenating prototype with a layered architecture — a Kotlin DSL records games into a non-sealed IR + visitor hierarchy (`gbkt-ir`, zero deps), nine ordered analysis passes annotate it with hardware-resource assignments, and a structured C AST emits bank-split GBDK C.
- **Semantic IR + visitor dispatch:** 24-subtype `ScriptOp` instruction set, 9-subtype `Expr` tree, nullable platform annotations (bank slot / VRAM range / OAM slot), unsealed to open interfaces so genre packages extend the IR without modifying core.
- **Idiomatic Kotlin DSL:** delegate-based variables (`u8Var`/`i8Var`/…), operator overloads (`set`/`+=`/comparisons), type-safe input (`dpad.up.held`), type-safe scene refs (`navigate(scene)`), actor name inference, AABB collision (`ball.collides(paddle)`) — zero magic strings.
- **Structured C AST codegen:** sealed `CFile/CFunction/CStatement/CExpr` hierarchy with bank as a typed immutable field (no mutable `currentBank`), single `CEmitter` pretty-printer, ~150 `CRawCode` escape hatches eliminated to typed nodes.
- **"GC for hardware" analysis passes:** FFD bank bin-packing with scene locality + auto-trampolines, per-scene VRAM tile planning with dedup + overflow errors, OAM/scanline planning, WRAM/HRAM/SRAM layout, and a Rust-cargo-style budget audit as the final gate.
- **Asset pipeline in Gradle:** PNG → deduplicated 2bpp tiles, Tiled/LDtk → tilemaps with collision, sprite-sheet slicing, hUGETracker music, png2asset metasprite path — all incremental build tasks.
- **JVM test runner:** `ScriptOpInterpreter` + `SimulationContextV2` run game logic on the JVM without an emulator (sub-5s), plus a `GbktTestExtension` JUnit5 layer, an embedded Coffee-GB emulator (`gbkt-emulator`), and a 17-tool MCP server for AI-agent UAT.
- **20-module architecture + genre plugins:** layered modules (ir/lang/engine/world/core/backend-api/backend-gbdk/analysis) with ServiceLoader-discovered genre packages (RPG, platformer, puzzle, sport) and a Gradle plugin orchestrating the build.
- **Source maps + IDE DX:** `.gbkt.map` C-line → Kotlin file:line mapping, Rust-style error formatting, bidirectional DSL↔C navigation and budget gutter icons in the IntelliJ plugin.
- **GBDK SDK reference-port validation track (Phases 9–13):** simple_physics, metasprites, banks, and platformer_template re-implemented as idiomatic gbkt DSL and validated against GBDK reference C as a codegen oracle, with binding visual UAT sign-offs (banking, OAM, palette/sprite polarity, tilemap collision).
- **Release hardening:** retired dead examples + dropped V2 suffixes + removed pre-AST dead code (Phase 14), then drove the entire pre-existing-red suite green diagnose-first (Phase 15) as the hard v0.1.0 gate.

**Known deferred items at close:** 56 (see STATE.md `## Deferred Items`) — 35 dormant/active backlog seeds, 9 historical verification gaps, 4 UAT-status flags (0 pending scenarios each), 5 advisory codegen todos, 2 debug sessions (one is the resolved-sessions KB; one targets the retired `racer` example), 1 quick task. All triaged as out-of-v0.1.0-scope; none block the release gate.

### Known Gaps

Shipped with these accepted gaps (deferred to a future milestone):

- **4 Pending requirements** — QUAL-01, QUAL-02 (Phase 5.15 quality), QUAL-03 (Phase 08 detekt/tech-debt cleanup), IDE-04 (Phase 5.4 IDE) — map to phases deliberately not executed in this milestone.
- **Deferred genre-codegen phases** — 07.5 (platformer genre), 07.6 (RPG genre audit), 07.7 (GBC palette init), 07.8 (UAT re-run), 08 (detekt cleanup) remain open in the archived roadmap; the 7 KEEP example games build and pass the full suite.
- **Advisory DSL-primitive correctness smells** (unreached by shipping examples) — `easeToZero` oscillation when `by > 1`, `wrapAt(0)` silent always-reset, `orElse` may attach to wrap-guard `IfOp`.

---
