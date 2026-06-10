# Feature Research

**Domain:** Kotlin DSL-to-C compiler framework for Game Boy / Game Boy Color
**Researched:** 2026-02-17
**Confidence:** HIGH (codebase + PROJECT.md are authoritative; ecosystem verified with GB Studio, ZGB, GBDK-2020, Butano)

---

## Context: What This Is

gbkt is NOT a game engine — it is a **compiler framework**. Users declare a game in Kotlin DSL; gbkt
compiles it to GBDK-compatible C and then to a `.gb` ROM. The features below answer: what does a
DSL-to-C compiler for retro hardware need?

The primary reference for feature decisions is the project's own PROJECT.md (the vision document),
validated against what GB Studio, ZGB, and Butano each solved. Where gbkt deliberately does NOT
follow competitors (e.g., no visual editor, no manual banking), that is noted as intentional.

---

## Feature Landscape

### Table Stakes (Users Expect These)

These are the features developers would expect from any retro game DSL framework. Missing these
means the framework cannot produce working ROMs or cannot compete with GBDK raw C.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Scene system** | Every Game Boy game has scenes (title, gameplay, game over). Developers expect scene enter/update/exit lifecycle. GB Studio and ZGB both provide this. | MEDIUM | Scenes are the top-level organization unit. Must handle scene transitions with loading semantics. |
| **Sprite/entity support** | Game Boy games are sprite-based. Without sprite management, you can't make a game. | MEDIUM | Must handle 8x8 and 8x16 hardware sprites. OAM slots are a hard constraint (40 total). |
| **Input handling** | D-pad + A/B + Start/Select. Developers expect `.pressed`, `.held`, `.released` per button. | LOW | Already well-defined. Input buffering and edge detection are expected. |
| **Tilemap/background support** | Game Boy background layer uses tilemaps. Without this, no scrolling worlds, no platformers, no RPG maps. | MEDIUM | Tiled (.tmx) is the standard map format. Must convert to GB tile data format. |
| **Asset pipeline (PNG → tiles)** | Developers drop PNGs into assets/. They do not want to run `png2asset` manually. ZGB automates this; GB Studio automates this; users expect automation. | HIGH | PNG must be quantized to 4 colors (2bpp), sliced to 8x8 tiles, deduplicated. Critical for usability. |
| **ROM compilation via GBDK** | The final output must be a `.gb` file. Gradle task `buildRom` is the expected interface. | LOW | Already implemented in Gradle plugin. GBDK lcc invocation is understood. |
| **Save/load system (SRAM)** | Any game worth playing needs persistence. Developers expect a type-safe save API. | MEDIUM | Must generate correct MBC5_RAM_BATTERY cartridge config. SRAM layout must be planned automatically. |
| **Camera system** | Scrolling games need camera follow, bounds clamping. This is table stakes for any framework above raw GBDK. | MEDIUM | Camera offset must automatically propagate to all sprite rendering. |
| **Dialog / text rendering** | RPGs, adventures, even platformers need text. Window layer rendering is mandatory (background tile corruption otherwise). | MEDIUM | Font tiles, text placement, typewriter effect. Already implemented using window layer correctly. |
| **Menu system** | Title screen menu, pause menu, options — every game has menus. | MEDIUM | Navigation, cursor, selection callbacks. Already implemented. |
| **Audio (music + SFX)** | Game Boy games have music. Any framework without sound feels broken. | MEDIUM | hUGEDriver integration is the standard. Must support .uge music files and SFX triggers. |
| **JVM build integration (Gradle)** | Target users are Kotlin/JVM developers. Gradle is non-negotiable. `./gradlew build` must produce the ROM. | LOW | Already implemented. Gradle plugin exists and works. |
| **Working example games** | Without working examples, nobody trusts the framework. Pong, Breakout, Explorer are the minimum bar. | LOW-MEDIUM | Examples exist; they must compile through the rebuilt pipeline end-to-end. |
| **Collision detection** | Entity-to-entity and entity-to-tilemap collision is needed for virtually every game type. | MEDIUM | AABB collision, tilemap collision layer. Already implemented. |
| **Variable types matching hardware** | Game Boy uses u8 (0-255), u16 (0-65535), i8 (-128..127). DSL must expose these as typed delegates. | LOW | `u8Var`, `u16Var`, `i8Var` pattern already established. |
| **Animation system** | Sprite frame sequencing with timing. Every sprite-based game needs this. | LOW | Frame indices, animation speed (frames per tick), looping/one-shot. Already implemented. |
| **GBC color palette support** | Game Boy Color has 8 background palettes and 8 sprite palettes of 4 colors each. Framework must handle palette management. | MEDIUM | Palette assignment must be part of VRAM planning pass. |

---

### Differentiators (Competitive Advantage)

These are features that no competitor provides. They are the core reason gbkt exists as a separate
project from GB Studio, ZGB, or raw GBDK.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Automatic bank allocation** | The biggest DX problem in GBDK development. Developers manually manage which code goes in which ROM bank, causing cryptic bank overflow errors. gbkt automatically bin-packs code and data into banks, generates trampoline functions, and produces a build report. No other GB framework does this automatically. | HIGH | First-fit-decreasing bin packing. Scene locality optimization (scenes that transition to each other share banks). This is the "GC for hardware" core value. |
| **Automatic VRAM planning** | VRAM is 16KB on Game Boy. Developers in GBDK manually calculate which tile indices go where. gbkt assigns tile slots per-scene, detects shared tiles across transitions, and emits an error with a count when a scene exceeds 384 unique tiles. | HIGH | Per-scene tile slot layout. Hash-based tile deduplication. Shared tile detection reduces transition cost. This converts a mysterious graphical glitch into a build error. |
| **Automatic OAM planning** | 40 sprite slots, 10 per scanline. gbkt allocates OAM slots, analyzes scanline density, and warns when sprites will flicker. Developers don't count sprites manually. | HIGH | Per-scanline sprite density analysis. Priority ordering. Error/warning at compile time, not runtime. |
| **Automatic RAM planning** | WRAM (4KB), HRAM (127 bytes), SRAM layout. gbkt plans all RAM usage, maps persistent variables to SRAM addresses, and errors when RAM budget is exceeded — before the ROM is compiled. | HIGH | WRAM variable layout. HRAM for performance-critical variables. SRAM save structure with checksum. |
| **Budget audit with actionable messages** | Instead of mysterious glitches, developers get: "Scene 'dungeon' uses 401 unique tiles (max 384). Consider splitting tileset 'dungeon_walls'." This is the compile-time safety story. | MEDIUM | Budget audit pass runs last, after all planning passes. Produces human-readable build report. |
| **Structured C AST codegen** | The current codebase uses `line("...")` string concatenation — this approach cannot be optimized, analyzed, retargeted, or source-mapped. A C AST (sealed `CStatement`/`CExpr` hierarchy) enables optimization passes, source mapping, and future non-GB backends. | HIGH | `CStatement.VarDecl`, `CStatement.If`, etc. Pretty-printer is the only place strings assemble. |
| **JVM test runner (no emulator)** | Game logic tests run in milliseconds on JVM. No emulator boot, no ROM flash, no visual inspection. `gradle test` validates battle formulas, dialog trees, scene transitions, and encounter logic in under 5 seconds. | HIGH | `ScriptOp` interpreter. Simulated game environment. Input simulation (press/hold/release). Variable assertions. This is unique in the Game Boy homebrew ecosystem. |
| **Semantic IR as module contract** | The IR (`GameIR`, `SceneIR`, `ActorIR`, `ScriptOp`) is a platform-agnostic game model — not a C AST. Platform annotations (bank slots, VRAM ranges, OAM slots) are nullable fields filled by analysis passes. This design allows future backends (GBA, SNES, NES) to reuse the same DSL and analysis layer, only swapping codegen. | HIGH | Sealed types for `ScriptOp` and `IRStatement`. Null platform fields. Two sealed worlds: game IR and C AST. |
| **Tile deduplication** | Automatically detect identical tiles across all tilesets in a scene, merge them. This directly increases effective VRAM capacity. GB Studio does this; ZGB and raw GBDK do not. | MEDIUM | SHA-256 per tile, hash map deduplication. H-flip and V-flip variant detection (reuse with flip flags). |
| **Scene transition VRAM planning** | When transitioning between scenes, gbkt pre-computes which tiles change, schedules VRAM transfers across VBlanks to avoid tearing, and recommends fade effects for high-cost transitions. | HIGH | Delta computation per transition pair. Transfer scheduling. Automatic fade selection for costly transitions. |
| **`inlineC {}` escape hatch** | For hot paths and hardware tricks the framework doesn't cover, users can drop raw C. These blocks are clearly marked, excluded from JVM testing, and don't break the rest of the framework. Butano has `BN_DATA_EWRAM` and direct VRAM calls for the same reason. | LOW | Already partially implemented as `raw()`. Needs to be formalized as `inlineC {}` and `inlineAsm {}`. |
| **`gradle budgetReport`** | Standalone task that runs all analysis passes and prints the budget report without compiling a ROM. Developers can check their memory budget on every commit without a full build. | LOW | Depends on analysis pass infrastructure being in place. |
| **Compile-time ref resolution** | `ref("nonexistent_scene")` fails the build. `asset("sprites/hero.png")` fails if the file doesn't exist. Type-safe references that catch typos and broken links at build time, not runtime. | MEDIUM | All references validated in Pass 1 (Validation pass). Error messages include the source location. |

---

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Visual editor / GUI** | GB Studio has one; some users will ask why gbkt doesn't | gbkt is explicitly for code-first developers who want programmatic control. A GUI would require a separate frontend app, massive scope expansion, and would compete with GB Studio on its own terms — and lose. | Use GB Studio if you want a visual editor. gbkt exists because GB Studio's visual scripting hits a ceiling for complex games. |
| **Manual bank control (`BANKED`, `#pragma bank N`)** | Experienced GBDK developers know bank pragmas and want to use them | Exposes the platform leak gbkt is designed to eliminate. Defeats the entire value of automatic bank allocation. If users can manually set banks, the allocator must handle conflicts, partial overrides, and interaction bugs. | Provide `@BankHint` annotations as optional guidance to the allocator without hard overrides. |
| **Float arithmetic in DSL** | Game Boy has no FPU; some developers think they can use Kotlin floats | Floats are silently wrong on the Game Boy. SDCC converts floats to software emulation routines that are enormous and slow. The framework must reject floats in `ScriptOp` at compile time. | Fixed-point types. `0.5f` in the DSL = "use Q8.8 fixed-point math". The framework generates the correct integer approximations. |
| **Heap allocation in game logic** | Modern developers expect `new`, `ArrayList`, garbage collection | The Game Boy has no heap. Everything is stack or static. Dynamic allocation causes memory corruption. This must be a compile-time error, not a runtime crash. | Static pools. Entity pools (already implemented). Fixed-size arrays. The DSL enforces stack/static-only storage. |
| **Unconstrained scripting (full Kotlin in onUpdate)** | Developers want to write arbitrary Kotlin in their update loops | Arbitrary Kotlin in an update loop can allocate, recurse, call external functions — all of which violate GB constraints. The framework cannot analyze or generate safe C for arbitrary code. | `ScriptOp` sealed instruction set. Known operations the compiler can reason about exhaustively. `inlineC {}` for when users genuinely need something the framework doesn't provide. |
| **Building a game engine (scene-specific render code)** | Feature requests for custom renderers, particle systems, lighting | gbkt provides scene management, asset loading, and build tooling. Genre-specific systems (battle engines, physics) ship as optional libraries. Building a full render engine would consume all development bandwidth and still be less capable than raw GBDK for expert users. | Optional library crates: `dev.gbkt:physics-platformer`, `dev.gbkt:battle-engine`, `dev.gbkt:dialog-engine`. Core stays thin. |
| **Multiple codegen backends in MVP** | "Can it target GBA?" — yes eventually, but not now | Adding a GBA backend before the GB backend is stable multiplies implementation complexity. Two targets means two failing integration tests, two sets of hardware constraints, two sets of analysis passes. | Architecture is designed for future backends. The IR and analysis passes are platform-agnostic. GBA codegen is a separate module that exists when GB is stable. |

---

## Feature Dependencies

```
Asset Pipeline (PNG → tiles)
    └──required by──> VRAM Planning Pass
                          └──required by──> Budget Audit Pass
                                                └──required by──> Build Report

Semantic IR (GameIR, SceneIR, ScriptOp)
    └──required by──> ALL compiler passes
    └──required by──> JVM Test Runner
    └──required by──> Structured C Codegen
    └──required by──> IntelliJ Plugin (future)

Bank Allocation Pass
    └──required by──> Structured C Codegen (needs bank assignments before emitting pragmas)
    └──required by──> Budget Audit Pass

OAM Planning Pass
    └──required by──> Structured C Codegen (sprite slot assignments baked into C)
    └──required by──> Budget Audit Pass

RAM Planning Pass
    └──required by──> Structured C Codegen (WRAM variable addresses baked into C)
    └──required by──> SRAM Layout (save data structure)
    └──required by──> Budget Audit Pass

Validation Pass (Pass 1)
    └──must run before──> ALL other passes (ref resolution, type checks)

Structured C AST Codegen
    └──required by──> ROM compilation (GBDK lcc)
    └──enhances──> Optimization pass (can fold, merge, inline)

JVM Test Runner
    └──requires──> ScriptOp interpreter
    └──enhances──> CI without emulator

Example Games (Pong, Breakout, Explorer)
    └──require──> End-to-end pipeline correctness (validates everything)

Scene System
    └──required by──> Camera System (camera scoped to scene)
    └──required by──> VRAM Planning (per-scene tile budgets)
    └──required by──> OAM Planning (per-scene sprite budgets)
    └──required by──> Scene Transition Planning

Save System
    └──requires──> RAM Planning (SRAM layout)
    └──requires──> MBC5_RAM_BATTERY cartridge config (auto-selected by framework)

GBC Palette Support
    └──requires──> VRAM Planning (palette bank 0 vs bank 1 assignment)
    └──enhances──> Asset Pipeline (color quantization per palette)
```

### Dependency Notes

- **Semantic IR is the root dependency for everything.** Pass 1 (Validation) must run before any other pass. All analysis passes feed into Budget Audit. Codegen reads the fully-annotated IR output of all passes.
- **Asset Pipeline feeds VRAM Planning.** Tile counts are only known after PNG processing. VRAM Planning cannot run without processed tile data.
- **Bank Allocation must run before Codegen.** The C emitter needs bank assignments to emit `#pragma bank N` and trampoline function declarations.
- **Example games are the integration test.** They do not unlock features, but they validate that all features work end-to-end. If Pong doesn't compile, the pipeline has a bug.
- **JVM Test Runner is independent of codegen.** It requires only the IR and ScriptOp interpreter. It can be built before structured codegen and used to validate game logic correctness.

---

## MVP Definition

### Launch With (v1 — Rebuild Goal)

This is the rebuild milestone: bring the existing codebase to the restructured architecture with
all three example games compiling through the new pipeline.

- [ ] **Semantic IR** — `GameIR`, `SceneIR`, `ActorIR`, `ScriptOp`, platform annotation fields nullable — fundamental contract between all modules
- [ ] **Validation Pass (Pass 1)** — ref resolution, type checks, constraint enforcement at build time
- [ ] **Asset Pipeline** — PNG → 2bpp tiles, Tiled TMX → tilemaps, sprite sheet slicing, integrated as Gradle task
- [ ] **Bank Allocation Pass (Pass 3)** — automatic bin-packing, trampoline generation, scene locality optimization
- [ ] **VRAM Planning Pass (Pass 4)** — per-scene tile slot assignment, tile deduplication, shared tile detection
- [ ] **OAM Planning Pass (Pass 5)** — sprite slot allocation, scanline density analysis, warnings
- [ ] **RAM Planning Pass (Pass 6)** — WRAM layout, SRAM save data structure, HRAM allocation
- [ ] **Budget Audit Pass (Pass 9)** — final gate, human-readable build report with actionable errors
- [ ] **Structured C AST Codegen** — `CStatement`/`CExpr` sealed hierarchy, pretty-printer as the only string assembly point
- [ ] **Example games compile** — Pong (simple), Breakout (entity pools), Explorer (tilemap + collision + dungeon)
- [ ] **JVM Test Runner** — ScriptOp interpreter, frame advance, input simulation, variable assertions

### Add After Validation (v1.x)

Features to add once the core pipeline is working and examples compile.

- [ ] **`gradle budgetReport`** — standalone analysis task without ROM compilation (depends on analysis passes existing)
- [ ] **Tile deduplication** — hash-based dedup, H-flip/V-flip variant detection (enhances VRAM planning)
- [ ] **Scene transition VRAM planning** — delta computation, VBlank transfer scheduling (depends on VRAM planning pass)
- [ ] **`inlineC {}` / `inlineAsm {}`** — formalized escape hatches replacing current `raw()` (low risk, high DX value)
- [ ] **`dev.gbkt:physics-platformer` library** — gravity, AABB collision, moving platforms (can be built as optional library once core is stable)
- [ ] **`dev.gbkt:dialog-engine` library** — typewriter text, choice menus, variable interpolation
- [ ] **GBC palette system** — 8 bg palettes + 8 sprite palettes, automatic assignment in VRAM planning

### Future Consideration (v2+)

Features to defer until the framework is stable and adopted.

- [ ] **IntelliJ plugin inspections** — real-time tile budget warnings, missing asset red underlines, unresolved ref squiggles. Depends on stable IR and analysis passes. Requires separate IntelliJ SDK development.
- [ ] **`dev.gbkt:battle-engine` library** — turn-based battle system as an optional library (RPG engine, not framework core)
- [ ] **`dev.gbkt:save-manager` library** — multi-slot saves with data migration
- [ ] **GBA backend (`:codegen-gba`)** — second codegen target. Architecture is ready (IR is platform-agnostic), but adds maintenance burden. Wait until GB backend is mature.
- [ ] **Link cable multiplayer** — specialized use case, small audience
- [ ] **LDtk map format support** — alternative to Tiled; add when there's community demand
- [ ] **Auto-download GBDK** — nice DX feature for onboarding; GBDK installation is currently a prerequisite but not a blocker

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Semantic IR (GameIR, ScriptOp) | HIGH | HIGH | P1 |
| Validation pass (Pass 1) | HIGH | MEDIUM | P1 |
| Asset pipeline (PNG → tiles) | HIGH | HIGH | P1 |
| Bank allocation (Pass 3) | HIGH | HIGH | P1 |
| VRAM planning (Pass 4) | HIGH | HIGH | P1 |
| OAM planning (Pass 5) | MEDIUM | MEDIUM | P1 |
| RAM planning (Pass 6) | HIGH | MEDIUM | P1 |
| Budget audit (Pass 9) | HIGH | MEDIUM | P1 |
| Structured C AST codegen | HIGH | HIGH | P1 |
| Example games compile end-to-end | HIGH | MEDIUM | P1 |
| JVM test runner | HIGH | HIGH | P1 |
| `gradle budgetReport` task | MEDIUM | LOW | P2 |
| Tile deduplication | HIGH | MEDIUM | P2 |
| Scene transition VRAM planning | MEDIUM | HIGH | P2 |
| `inlineC {}` escape hatch | MEDIUM | LOW | P2 |
| GBC palette system | MEDIUM | MEDIUM | P2 |
| Physics platformer library | MEDIUM | HIGH | P2 |
| Dialog engine library | MEDIUM | MEDIUM | P2 |
| IntelliJ plugin inspections | HIGH | HIGH | P3 |
| Battle engine library | MEDIUM | HIGH | P3 |
| GBA backend | HIGH | VERY HIGH | P3 |
| Link cable multiplayer | LOW | HIGH | P3 |
| LDtk format support | LOW | MEDIUM | P3 |

**Priority key:**
- P1: Must have for the rebuild milestone (examples compile through new pipeline)
- P2: Should have, add after rebuild is complete
- P3: Future, defer until framework is stable and adopted

---

## Competitor Feature Analysis

| Feature | GB Studio | ZGB | GBDK-2020 | Butano (GBA ref) | gbkt |
|---------|-----------|-----|-----------|-----------------|------|
| Scene system | YES (visual) | YES (game states) | NO (manual) | NO (manual) | YES (declarative DSL) |
| Automatic banking | PARTIAL (opaque) | YES (bankpack) | NO (manual pragmas) | NO (manual) | YES (bin-packing + analysis) |
| Automatic VRAM planning | PARTIAL (hides it) | NO | NO | PARTIAL (RAII ptrs) | YES (per-scene analysis pass) |
| Automatic OAM | PARTIAL | NO | NO | PARTIAL | YES (slot allocation + scanline analysis) |
| Asset pipeline | YES (drag-drop PNG) | YES (auto-convert) | NO | YES (Makefile convert) | YES (Gradle task, PNG/TMX) |
| Tile deduplication | YES | NO | NO | N/A | YES (hash-based) |
| Budget errors at compile time | PARTIAL (warnings) | NO | NO | PARTIAL | YES (hard errors + build report) |
| JVM testing (no emulator) | NO | NO | NO | NO | YES (unique differentiator) |
| Type-safe DSL | NO (visual blocks) | NO (C) | NO (C) | PARTIAL (C++ types) | YES (Kotlin sealed types) |
| Structured C AST | NO | NO | N/A | N/A | YES (enables optimization + retargeting) |
| Code-first approach | NO | YES (C) | YES (C) | YES (C++) | YES (Kotlin DSL) |
| Complexity ceiling | MEDIUM | HIGH | VERY HIGH | VERY HIGH | HIGH (targeting Pokémon-scale) |
| Learning curve | LOW | MEDIUM | HIGH | HIGH | MEDIUM (Kotlin devs) |
| Multi-platform future | NO | NO | YES (SMS, GG, Pocket) | NO | YES (IR + codegen separation) |

**Key insight from competitor analysis:**

- GB Studio and ZGB each automate *some* of VRAM/banking, but neither exposes what's happening or gives compile-time errors. Users discover budget problems at runtime (graphical glitches, bank crashes).
- Butano (GBA) is the closest architectural parallel: RAII-based resource management, automatic OAM buffering. But it targets C++ developers, not Kotlin developers, and has no budget audit system.
- **No framework in the Game Boy ecosystem provides JVM-based game logic testing.** This is gbkt's strongest unique differentiator. Developers currently debug logic by flashing a ROM and running it in an emulator — slow feedback loops that gbkt eliminates.

---

## Sources

- **gbkt PROJECT.md** — `/Users/michalsvacha/GitHub/personal/gbkt/.planning/PROJECT.md` — authoritative vision, architecture, requirements (HIGH confidence)
- **gbkt CLAUDE.md** — codebase documentation, existing feature list (HIGH confidence)
- **gbkt context/ARCHITECTURE.md** — IR nodes, data flow (HIGH confidence)
- **gbkt context/DSL_REFERENCE.md** — complete DSL syntax reference (HIGH confidence)
- **GB Studio** — [https://www.gbstudio.dev/](https://www.gbstudio.dev/) — feature comparison, event glossary (MEDIUM confidence — web fetch, current)
- **ZGB** — [https://github.com/Zal0/ZGB](https://github.com/Zal0/ZGB) — automatic handling vs manual responsibilities (MEDIUM confidence — web fetch, current)
- **GBDK-2020** — [https://github.com/gbdk-2020/gbdk-2020](https://github.com/gbdk-2020/gbdk-2020) — what the C layer provides, manual banking (MEDIUM confidence)
- **Butano (GBA)** — [https://gvaliente.github.io/butano/faq.html](https://gvaliente.github.io/butano/faq.html) — RAII resource management, OAM, VRAM patterns (MEDIUM confidence — web fetch, current)
- **gbdev.io tools guide** — [https://gbdev.io/guides/tools.html](https://gbdev.io/guides/tools.html) — ecosystem tradeoff analysis (MEDIUM confidence)

---

## Feature Categories Explained

### Why the IR + Analysis Pass Architecture IS the Feature

For a compiler framework, the "features" are fundamentally different from a game engine's features.
The compiler's features are:

1. What it **eliminates** from the developer's mental model (manual banking, VRAM math, OAM counting)
2. What it **catches at build time** instead of runtime (tile overflow, bank overflow, reference errors)
3. What it **generates automatically** (trampoline functions, VRAM load sequences, SRAM layout)
4. What it **enables in testing** (JVM game logic execution without emulator)

This is why the differentiators above are almost all compiler infrastructure, not user-visible DSL features. The DSL features (scenes, dialogs, menus, physics) are table stakes — they're what every other framework provides. What gbkt adds is the intelligence layer beneath the DSL.

### The "GC for Hardware" Metaphor

The JVM garbage collector is valuable not because of what it does, but because of what developers
DON'T have to do: `malloc`, `free`, reference counting, use-after-free debugging. Similarly, gbkt
is valuable because developers DON'T have to: add `#pragma bank N`, count VRAM tiles, assign OAM
slots, calculate SRAM addresses, write bank trampoline functions.

The analysis passes (bank allocation, VRAM planning, OAM planning, RAM planning) ARE the garbage
collector. They observe what the game needs and assign hardware resources automatically.

---

*Feature research for: gbkt — Kotlin DSL-to-C compiler for Game Boy*
*Researched: 2026-02-17*
