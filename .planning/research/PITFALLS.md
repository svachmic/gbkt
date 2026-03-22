# Pitfalls Research

**Domain:** Kotlin DSL-to-C compiler framework for Game Boy (incremental rebuild of tightly-coupled system)
**Researched:** 2026-02-17
**Confidence:** HIGH — evidence drawn from the actual codebase failure modes documented in PROJECT.md, CLAUDE.md MEMORY.md, and community sources for GBDK-2020 and compiler architecture patterns

---

## Critical Pitfalls

These are mistakes that caused the first version of gbkt to fail and will cause the rebuild to fail if repeated.

---

### Pitfall 1: Letting One Game Drive Framework Generality

**What goes wrong:**
A game framework written while porting a specific game unconsciously encodes that game's specific structure as framework primitives. The LabyrinthOfTheDragon port caused codegen, DSL constructs, and bank allocation to assume dungeon-floor-based exploration, RPG stat tables, and specific GBDK structs by name. Other game types (Pong, Breakout) became second-class citizens that forced workarounds.

**Why it happens:**
The fastest path to validating a framework is to run a real game through it. When the real game is complex, shortcuts taken to ship it become load-bearing walls. Each "just this once" assumption gets referenced by other parts of codegen until removing it requires rewriting 10 files.

**How to avoid:**
Write the three example games in parallel from Phase 1 onward. The Pong DSL definition, Breakout DSL definition, and Explorer DSL definition should all exist and compile before any RPG-specific codegen is written. If a codegen function references anything not present in Pong, flag it as domain-specific and route it through a feature-conditional path. The test `GeneralizationCodegenTest.kt` already validates this pattern — expand it aggressively.

**Warning signs:**
- Codegen files in `rpg/` that `game.monsters.isNotEmpty()` are fine; codegen files in `core/` that branch on `game.monsters.isNotEmpty()` are not
- Any hardcoded string like `"_combat_state"` or `"_pending_encounter"` in `core/` codegen
- A test that can only pass when the game has an RPG system configured
- The word "always" in a codegen comment: `// Always generated for dungeon systems`

**Phase to address:**
Phase 1 (IR definition) — the IR must represent Pong, Breakout, and Explorer without RPG nodes. Phase 2 (structured codegen) — codegen must be gated on IR content, not on game type assumptions.

---

### Pitfall 2: Bank State as Global Mutable Variable in String-Based Codegen

**What goes wrong:**
The current `GBDKCodeGenerator.currentBank` is an integer field modified by side effects (`setBank()`, `returnToHome()`). When a codegen function forgets to call `returnToHome()`, all subsequent codegen silently inherits the wrong bank. This caused the `ZoneCodegen.kt` bug documented in MEMORY.md: `generateBankedTilemapData()` must end with `setBank(16)` not `returnToHome()`, but the correct value is non-obvious and depends on understanding the exact ordering of 40+ codegen calls in `generate()`.

The rebuilt codegen must not have mutable bank state. Bank assignment is a compiler analysis output, not a side-effecting call inside codegen.

**Why it happens:**
String-based codegen emits `#pragma bank N` directives inline as strings. To avoid duplicate pragmas, the generator tracks "current bank" as state. This works until any codegen function is called out of order, added in the middle of the sequence, or forgets to restore state.

**How to avoid:**
In the new C AST approach, `BankPragma(bank: Int)` is a `CStatement` node. Bank assignment is determined by the analysis pass (Pass 3: Bank Allocation), which annotates IR nodes with their bank slot. The codegen reads `irNode.bankSlot` and emits `BankPragma` without any mutable state. The C AST pretty-printer emits `#pragma bank N` only where it appears structurally. No `currentBank` field exists.

**Warning signs:**
- Any `var currentBank` field in the new codegen
- Any `setBank()` or `returnToHome()` side-effecting function in the new codegen
- `assertHomeBank()` calls in the generate sequence (these are workarounds for the problem, not solutions)
- A codegen function that has a "return to home" call at the end as a contract

**Phase to address:**
Phase 2 (structured codegen) — the C AST design must make bank state a structural property, not imperative state. The `BankPragma` node must appear only where analysis has placed it.

---

### Pitfall 3: Sealed Interface Hierarchy in the Wrong Module

**What goes wrong:**
Kotlin's sealed interfaces require all implementations to be in the same compilation module. If `IRStatement` and `IRExpression` live in `:ir` but you later try to add `IRRPGAttack : IRStatement` in `:rpg-module`, the compiler rejects it. The current gbkt-core is monolithic because this constraint was discovered after initial module splits were attempted.

The rebuild plans a new module structure (`:ir`, `:dsl`, `:analysis`, `:codegen`). If the sealed hierarchy boundaries are not correct from the start, you will discover module placement errors only when you try to add the 20th IR node type — after the module structure is already load-bearing.

**Why it happens:**
The sealed hierarchy is designed up front, but domain IR nodes (RPG stats, dungeon tiles, battle states) are added incrementally. Each addition forces the question "which module does this go in?" The answer always resolves to "same module as the sealed base" — which is `:ir`. Without planning this ahead, you end up with `:ir` containing RPG-specific types, which violates the intended platform-agnostic design.

**How to avoid:**
Define the complete sealed hierarchy structure in Phase 1, including all game-domain subtypes (RPG, exploration, UI, audio). Accept that `:ir` will contain types that seem domain-specific. The key constraint is not "keep `:ir` small" but "keep `:ir` platform-agnostic." An `IRBattleAction` node is domain-specific but platform-agnostic — it belongs in `:ir`. An `IRGBDKPragmaBank` would not.

**Warning signs:**
- A Kotlin compilation error about sealed interface implementations in a different module
- An `else ->` clause in a codegen `when(irNode)` expression (means exhaustive matching is broken)
- An IR node that has a GBDK-specific field (bank number, VRAM address) baked in rather than as a nullable annotation

**Phase to address:**
Phase 1 (IR definition) — map all sealed subtypes needed for all three example games before writing any codegen. This prevents mid-project module restructuring.

---

### Pitfall 4: GBDK Bank 0 Overflow from Unconditional Code Generation

**What goes wrong:**
GBDK Bank 0 (HOME bank) has exactly 16,384 bytes. Every function or data array placed in Bank 0 without an explicit `#pragma bank N` consumes this budget. The current codegen generates many system stubs, variable declarations, and helper functions unconditionally — regardless of whether the game uses those features. For a simple game like Pong, this wastes Bank 0 space with RPG combat tables, encounter variables, and status effect lookups that will never execute.

Worse: Bank 0 overflow produces a linker error with no line-level attribution. The GBDK error is `bank overflow in bank 0` with a hex size — you must inspect the `.noi` symbol file to find which function pushed it over.

**Why it happens:**
The simplest codegen strategy is to always generate everything. Feature flags are added reactively when overflow occurs, not proactively. Each codegen subsystem is written independently, so nobody accounts for cumulative Bank 0 pressure.

**How to avoid:**
The analysis pass (Pass 3: Bank Allocation) must compute the total size of Bank 0 content before codegen runs. Every generated function must be tagged as "HOME required" or "banked OK." The budget audit (Pass 9) must fail the build if the estimated Bank 0 usage exceeds 14,000 bytes (2KB safety margin). Feature-gated generation (`if (game.hasBattleSystem) generateBattleCore()`) is acceptable during transition but must become analysis-driven as the architecture matures.

**Warning signs:**
- `lcc: bank overflow in bank 0` linker error
- Generated C code that includes full RPG combat tables for a game definition with no `battle()` or `monster()` DSL calls
- A `.noi` file showing `DEF l__CODE_0` at more than 14,000 bytes

**Phase to address:**
Phase 2 (structured codegen) for feature-gating; Phase 4 (analysis passes) for automatic Bank 0 budget tracking.

---

### Pitfall 5: Incremental Migration Without a Seam — Running Old and New Pipelines Simultaneously

**What goes wrong:**
The PROJECT.md specifies incremental refactoring (not a fresh start). The three example games must compile throughout the rebuild. If the new IR and codegen are built as a parallel system and the old system continues to generate for the examples, you end up maintaining two code paths. Each new IR node you add to the clean system requires a corresponding update to the old system to keep examples compiling. After a few weeks, the old system is more complex (now with shims) and the new system is still incomplete.

**Why it happens:**
The instinct during incremental migration is to not break working things. The result is a "strangler fig" pattern that strangles slowly — the seam between old and new is never clearly defined, so both systems grow.

**How to avoid:**
Define the migration seam at the IR boundary on day one. The three example games must be rewritten in the new DSL (not updated in the old DSL). The old codegen pipeline is deprecated as a whole, not file by file. The validation gate is: "do all three example games compile through the NEW pipeline?" not "do the old examples still work?" This requires writing the new DSL for Pong, Breakout, and Explorer early — even as simple stubs — so they anchor the new pipeline from the start.

**Warning signs:**
- Two separate code paths in the Gradle plugin (one for "legacy" games, one for "new" games)
- A comment like `// will be replaced later` in any shim or adapter
- The old `GBDKCodeGenerator` being called for any example game after Phase 2 completes
- Example games that compile through the old pipeline but fail through the new pipeline

**Phase to address:**
Phase 1 (IR) — define which examples anchor the new pipeline. Phase 2 (codegen) — the first example game must compile through the new path, period.

---

### Pitfall 6: String Interpolation Creeping into Structured Codegen

**What goes wrong:**
The new codegen architecture uses a C AST (`CStatement`, `CExpr`, etc.) and a single pretty-printer that converts it to strings. But under time pressure, a developer adds one `CStatement.InlineC("uint8_t x = ${irNode.name};")` for a "complex" case. This opens the door to the old disease: string interpolation in business logic. Within weeks, 20% of nodes use `InlineC` with embedded Kotlin string templates, recreating the original unanalyzable mess inside the supposedly clean AST.

**Why it happens:**
The `InlineC` escape hatch is designed for user code (`inlineC {}` in the DSL). It is not designed for framework codegen internals. But it is always available, and always tempting when a new IR node is awkward to express in `CStatement` terms.

**How to avoid:**
The `CStatement.InlineC` node must require an explicit justification comment citing "user escape hatch use only" in the codegen output. Any use of `InlineC` inside framework-authored codegen (as opposed to user-authored `inlineC {}` blocks) should trigger a code review flag. The sealed `CStatement` hierarchy should be sufficient for all framework needs — if it is not, extend the hierarchy rather than using `InlineC`.

**Warning signs:**
- `CStatement.InlineC("...")` appearing in any file outside the user's `inlineC {}` DSL handler
- String templates (`"${...}"`) inside codegen extension functions that return `CStatement`
- A `CExpr` value that is a raw string literal: `CExpr.Raw("_some_c_var")`
- The phrase "just use inline for now" in a PR description

**Phase to address:**
Phase 2 (structured codegen) — establish the rule before the first PR lands.

---

### Pitfall 7: VRAM Tilecount Assumptions Baked into IR

**What goes wrong:**
The Game Boy has 384 tiles of VRAM in block 0+1 combined (DMG), with GBC adding a second VRAM bank. The existing codegen sometimes hard-codes 256 as a tile limit (the size of one block), which is correct for background-only scenarios but wrong for sprite+background combinations. When a new scene type needs more tiles, the limit is discovered as a runtime graphical glitch, not a compile-time error.

**Why it happens:**
The 384 tile limit (256 in block 0+1, 128 in block 2) and the 192/192 split for background vs sprites are platform-specific details documented in Pan Docs but not enforced by any analysis pass in the current codebase. Developers write code that "works" for their test scene and assume it generalizes.

**How to avoid:**
The VRAM Planning pass (Pass 4) must be implemented before any game more complex than Pong attempts to compile. The pass must:
- Count unique tiles per scene
- Assign them to VRAM slots (block 0 vs block 1 vs GBC bank 1)
- Fail the build if a scene exceeds its budget
- Produce per-scene budget output in the build report

The IR must represent `TilesetIR.vramSlotAssignment` as a nullable field populated by Pass 4 — not hardcoded in codegen.

**Warning signs:**
- A `// 256 tiles max` comment hardcoded in codegen
- Tile counts that "work" in tests but produce garbled graphics in an emulator for a scene with more sprites
- No per-scene tile count in the build report output
- The field `vramSlot` not existing on `TilesetIR`

**Phase to address:**
Phase 4 (analysis passes) — VRAM planning pass. But the IR fields for VRAM slots must exist from Phase 1.

---

### Pitfall 8: The "Just One Mutable StringBuilder" Problem

**What goes wrong:**
The current `GBDKCodeGenerator` uses a single `out: StringBuilder` for all generated C code. Every codegen function appends to this one buffer in call order. This means the generated C file is exactly as structured as the call sequence in `generate()`. Adding a forward declaration, reordering includes, or inserting a `#pragma` anywhere other than the current cursor position requires buffering tricks or a complete restructure.

In the new architecture, C code must be organized into multiple files (per-scene `.c`, per-actor `.c`, `game.h` for declarations, `main.c` for entry point). A single StringBuilder cannot represent this.

**Why it happens:**
A single buffer is the simplest possible output model. It works fine for a single-file output. The problem surfaces only when multi-file output is needed — which is inherent to the clean architecture's plan for per-scene `.c` files.

**How to avoid:**
The C AST represents a collection of `CSourceFile` objects, not a flat string stream. Each file is an independent tree of `CStatement` nodes. The pretty-printer emits one string per `CSourceFile`. The codegen never calls `out.append()` directly — it builds data structures.

**Warning signs:**
- A single `StringBuilder` field on the code generator class
- Functions that return `Unit` and have side effects on a shared output buffer
- No `CSourceFile` concept in the new codegen hierarchy
- Generated C code that has all content in one file (`main.c`) when scenes should each have their own file

**Phase to address:**
Phase 2 (structured codegen) — the `CSourceFile` → `CFunction` → `CStatement` hierarchy must exist before any codegen is written.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Generate all subsystems unconditionally | Simpler codegen logic | Bank 0 overflow for simple games | Never in the new architecture — feature gates must exist from Phase 2 |
| Use `CStatement.InlineC` for complex nodes | Faster implementation | Recreates string-based codegen inside AST | Only for user-authored `inlineC {}` blocks, never for framework codegen |
| `var currentBank` mutable state | Avoids restructuring codegen | Bank state leaks cause silent corruption | Never — bank assignment is analysis output, not runtime state |
| Keep old pipeline running in parallel | Old examples keep compiling | Two systems to maintain; seam never closes | Acceptable for max 2 weeks as a bridge, then cut |
| Default all code to Bank 0 | No bank planning needed yet | Bank 0 overflow discovered late | Acceptable in Phase 2 with explicit TODO; must be resolved in Phase 4 |
| Hardcode OAM slot assignments | No OAM planning needed | Sprite flickering with more than 10 actors | Acceptable in Phase 2 for Pong/Breakout; unacceptable in Phase 5+ |
| Single `.c` output file | Simple Gradle integration | Cannot do per-scene loading or bank grouping | Acceptable in Phase 2; must change in Phase 4 when bank allocation lands |

---

## Integration Gotchas

Known failure modes when connecting to GBDK-2020 toolchain.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| GBDK `BANKED` calling convention | Functions in non-zero banks without `BANKED` keyword; caller uses function pointer without `__banked` qualifier | All functions in bank N > 0 must be declared `BANKED`; forward declarations in `game.h` must include `BANKED`; the codegen must auto-add `BANKED` to all function definitions in non-HOME banks |
| GBDK bank 0 size limit | Putting all stubs and system initializers in bank 0 without accounting for cumulative size | Reserve bank 0 for: interrupt handlers, main loop, scene manager, DMA routine. Everything else goes in numbered banks with `#pragma bank N` |
| GBDK MBC selection | Using MBC1 (SWITCH_ROM macro only supports bank 1-31, skips some banks) | Use MBC5 for all projects; it supports banks 0-255 cleanly; MBC1 adds complexity for no benefit in a framework context |
| GBDK `printf` in game code | `printf` writes to background tile layer, corrupting tilesets if debugGraphics is true | Set `debugGraphics = false` for any game with custom tilesets; replace `printf` with window-layer text helpers |
| GBDK interrupt routines calling banked code | `FAR()` or bank-switch macros inside VBlank ISR corrupt bank register because global state does not restore correctly | ISR code must be NONBANKED and call only HOME bank functions; no bank switches inside any interrupt routine |
| GBDK forward declaration duplication | Declaring the same function in multiple bank files causes multiple definition linker errors | Generate declarations only in `game.h`; bank files include `game.h` for prototypes; each function defined once |

---

## Performance Traps

Patterns that work for small games but break at greater complexity.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| All game state in global C variables | Works for Pong (10 variables); produces naming conflicts and WRAM overflow for RPG | The RAM Planning pass (Pass 6) must assign WRAM addresses explicitly; variables are grouped by lifetime (scene-local vs global vs SRAM) | Around 50+ global variables or 2 simultaneous scene states |
| One bank for all RPG data | Works for Breakout; bank overflows for monster databases over ~30 entries | Bank allocation must bin-pack; monster data across multiple banks with per-bank load functions | ~30 monsters, ~50 abilities, ~100 items |
| Exhaustive `when(irNode)` without `else` is fine at 10 nodes | Works at 10 IR node types; at 40+ types the case list becomes unmaintainable | Each subsystem codegen handles only its IR nodes; no single function handles all 40+ types | When adding the 15th IR node type to a single when-expression |
| Scene tilemaps loaded all at once at game start | Works for games with 2 scenes; initialization time becomes unacceptable with 10+ scenes | Per-scene VRAM loading with bank-switched tileset data; loaded on scene enter, unloaded on scene exit | More than 3-4 scenes with distinct tilesets |

---

## "Looks Done But Isn't" Checklist

Things that appear complete in the new pipeline but have hidden gaps.

- [ ] **Structured codegen:** If `CStatement` hierarchy exists but `InlineC` is used for >10% of IR node types, the migration is incomplete — verify each IR node type has a dedicated `CStatement` subtype
- [ ] **Bank allocation:** If `gradle budgetReport` runs without error but does not output per-bank size breakdowns, the allocator is not actually enforcing limits — verify with a game that intentionally exceeds bank 0
- [ ] **VRAM planning:** If Pong compiles but a game with two distinct tilesets does not produce a "tiles loaded on scene enter" pattern in generated C, VRAM planning is missing — verify by inspecting generated scene init functions
- [ ] **Feature gating:** If a generated `main.c` for Pong contains `_combat_state`, `_monster_data`, or `_encounter_table` symbols, unconditional generation is still active — verify by grepping generated output for RPG-specific identifiers
- [ ] **Multi-file output:** If all generated C is in one `main.c`, the per-scene file structure is not implemented — verify that `gradle generateC` produces `scenes/`, `actors/`, and `systems/` subdirectories
- [ ] **Exhaustive IR coverage:** If any `when(irNode)` in codegen has an `else ->` clause that calls `error()` or emits a comment, there is an unhandled IR node type — verify by running all three example games and checking for TODO comments in generated C
- [ ] **BANKED function declarations:** If any function defined in a non-zero bank file is missing `BANKED` in its declaration in `game.h`, the ROM will crash with MBC5 register errors on real hardware — verify by checking `game.h` for `BANKED` on all non-HOME functions
- [ ] **Test coverage of new pipeline:** If tests only call `GBDKCodeGenerator` directly on a `Game` object but do not run through the Gradle plugin task, integration failure modes in the build pipeline remain untested — verify that at least one test calls `gradle generateC` end-to-end

---

## Recovery Strategies

When pitfalls occur despite prevention.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| One-game coupling discovered in Phase 3+ | HIGH | Add the Pong/Breakout DSL definitions immediately; use them as regression tests to force generalization; accept that some codegen will need to be rewritten |
| Bank state leak corrupts output | MEDIUM | Add `assertHomeBank()` calls between every major codegen section; inspect generated C for unexpected `#pragma bank` directives; add a parse-time check in the C AST emitter |
| Sealed interface in wrong module | HIGH | Move the entire sealed base to the correct module; all consumers must recompile; cannot be done partially — plan the hierarchy correctly in Phase 1 |
| Bank 0 overflow at Phase 5 | HIGH | Enable `#pragma bank` on all RPG subsystems; regenerate; compare `.noi` file before/after; identify which systems were omitted from banking |
| String interpolation in new codegen | MEDIUM | Code review flag on all `CStatement.InlineC` usages; migrate each to proper `CStatement` subtype; takes 1-2 days per subsystem |
| Old pipeline never cut | MEDIUM | Set a hard deadline: after Phase 2, the old `GBDKCodeGenerator` is removed; example games must compile through new pipeline or be fixed |

---

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| One-game coupling | Phase 1: IR definition must cover Pong without RPG nodes | Three DSL examples exist and compile before Phase 3 |
| Bank state as global mutable | Phase 2: No `currentBank` field in new codegen | `grep -r "currentBank" gbkt-codegen/` returns zero results |
| Sealed interface in wrong module | Phase 1: Full sealed hierarchy mapped before any codegen | No `else ->` in any `when(irNode)` expression |
| GBDK Bank 0 overflow | Phase 4: Bank allocation pass; Phase 2: feature gating | `budgetReport` shows Bank 0 usage <14KB for Pong |
| Migration seam never closed | Phase 2: Old pipeline deprecated after first new example compiles | Old `GBDKCodeGenerator` deleted by end of Phase 2 |
| InlineC in framework codegen | Phase 2: Rule established before first PR | Code review checklist item; no `InlineC` in non-user codegen |
| VRAM tilecount assumptions | Phase 4: VRAM planning pass; IR fields in Phase 1 | Explorer game produces tile budget output; tile overflow fails build |
| Single StringBuilder | Phase 2: Multi-file C AST structure | `generateC` produces `scenes/`, `systems/` subdirectories |
| BANKED function corruption | Phase 2: All bank-N functions auto-BANKED in codegen | ROM runs on hardware (BGB emulator with memory viewer) without MBC5 register errors |
| Feature generation without gating | Phase 2: Feature-conditional codegen from first PR | Pong generated output contains no RPG symbol names |

---

## Sources

- **Project documentation (HIGH confidence):** `/Users/michalsvacha/GitHub/personal/gbkt/.planning/PROJECT.md` — lists the four specific failure modes that caused the v1 rebuild decision
- **CLAUDE.md MEMORY.md (HIGH confidence):** Documents specific bugs encountered: ZoneCodegen bank restore bug, BANKED calling convention issue, `debugGraphics` tilemap corruption, `splitByBank` BANKED auto-injection
- **gbkt-backend-gbdk source (HIGH confidence):** `GBDKCodeGenerator.kt` — `assertHomeBank()` calls document known bank state leak risk; 103 `setBank/returnToHome` calls in codegen confirm scope of the mutable state problem
- **GBDK-2020 documentation (MEDIUM confidence):** [GBDK ROM Banking docs](http://gbdk.org/docs/api/docs_rombanking_mbcs.html) — Bank 0 hard limit, BANKED calling convention, interrupt safety constraints
- **GBDK-2020 troubleshooting (MEDIUM confidence):** [Larold's Retro Gameyard](https://laroldsretrogameyard.com/tutorials/gb/troubleshooting-common-gbdk-2020-errors/) — buffer overflow, bank switching metasprite errors, MBC selection
- **GBDK community forums (MEDIUM confidence):** [gbdev.gg8.se bank switching discussions](https://gbdev.gg8.se/forums/viewtopic.php?id=557) — MBC1 vs MBC5 recommendation, auto-banking confusion
- **Nim compiler issue (MEDIUM confidence):** [nim-lang/compilerdev #6](https://github.com/nim-lang/compilerdev/issues/6) — string-based codegen prevents AST-to-AST optimizations; structured output is the correct architecture
- **Kotlin sealed interface constraint (HIGH confidence):** [Kotlin KEEP #226](https://github.com/Kotlin/KEEP/issues/226) — confirmed: sealed implementations must be same-module; no planned relaxation
- **Strangler Fig pattern pitfalls (MEDIUM confidence):** [Microsoft Azure Architecture Center](https://learn.microsoft.com/en-us/azure/architecture/patterns/strangler-fig) — entangled monolith with no clear separation is hardest case; need explicit seam definition
- **TypeScript codegen architecture (MEDIUM confidence):** [Singapore GDS: Templates vs AST](https://medium.com/singapore-gds/writing-a-typescript-code-generator-templates-vs-ast-ab391e5d1f5e) — "template" approach (string interpolation) prevents downstream analysis; AST approach enables optimization

---
*Pitfalls research for: gbkt Kotlin DSL-to-C Game Boy compiler framework rebuild*
*Researched: 2026-02-17*
