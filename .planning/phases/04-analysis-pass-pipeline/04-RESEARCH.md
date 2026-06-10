# Phase 4: Analysis Pass Pipeline - Research

**Researched:** 2026-02-18
**Domain:** Compiler analysis passes, Game Boy hardware resource allocation, bin-packing
**Confidence:** HIGH

## Summary

Phase 4 introduces nine ordered compiler passes that transform a raw `GameIR` into a fully-annotated IR with hardware resource assignments (ROM banks, VRAM tile slots, OAM sprite slots, RAM layout). The analysis pipeline sits between DSL-to-IR construction and C code generation, consuming `GameIR` and producing an `AnnotatedGameIR` (or equivalent) with `PlatformAnnotatable` fields filled in.

The codebase is well-prepared for this phase. Key infrastructure already exists: `PlatformAnnotations.kt` defines `BankSlot`, `VRAMRange`, and `OAMSlot` annotations with `PlatformAnnotatable` interface; `SceneIR`, `ActorIR`, and `SystemIR` already implement it with nullable fields. `CartridgeConfig` carries cartridge type and bank counts. The `TargetProfile`/`MemorySpec`/`SpriteSpec`/`ScreenSpec` hierarchy in `gbkt-core` provides hardware constraint data. Collection IR nodes (`IRCollHashTable`, `IRCollPool`, `IRCollRingBuffer`, `IRCollFixedSlots`) expose `sizeBytes` properties for RAM planning. The v1 `GameValidator` (1000+ lines) demonstrates existing validation patterns including OAM limit checks, WRAM estimation, VRAM tile counting, and reference validation.

**Primary recommendation:** Create `gbkt-analysis` as a new Gradle module depending on `gbkt-core` and `gbkt-backend-api`. Define `AnalysisPass` as a functional interface with `fun run(context: PassContext): PassContext` signature (immutable context threading). Each pass produces a new context with accumulated annotations/diagnostics. The pipeline executor chains passes in order, failing fast on errors. The `GBDKBackend.generateV2()` method becomes the integration point -- it runs the analysis pipeline before calling `GBDKPipelineV2`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Bank Allocation Strategy:** Trampoline generation is fully automatic -- analysis pass inserts trampoline stubs in HOME bank for any cross-bank calls, developer never thinks about it. Bank budget limit is configurable per game (MBC1=32, MBC3=128, MBC5=256) -- catches oversized games early for cartridge targeting. DSL config block sets max banks per MBC type.
- **Error & Warning Messages:** Tile overflow errors include scene name + breakdown by source (which tilesets/sprites contribute how many tiles) -- actionable guidance. Tile overflow errors also suggest splitting strategies ("Consider splitting tileset X into a sub-scene"). Warnings (bank fullness, OAM scanline density) shown during every build -- developer always sees resource pressure. Budget report: ASCII table in terminal (per-bank size bars, per-scene tile usage, OAM slots). Resource thresholds are configurable with reasonable defaults that are overridable per game.
- **VRAM Tile Slot Planning:** Hybrid deduplication: common tiles (UI, fonts) get fixed global slots; scene-specific tiles are allocated per-scene. Sprite VRAM reservation computed per-scene based on actor analysis -- maximizes BG tile budget per scene. Scene transitions: middle ground -- aim for smooth-ish transitions without constraining the allocator (best-effort common tile slot reuse, not guaranteed).
- **Pass Pipeline Design:** Fail fast on first error -- no point running VRAM planning if semantic validation failed. Pipeline is open with plugin API -- users can register custom passes. Custom pass extension points: before built-in passes and after built-in passes (two hooks, simple API).

### Claude's Discretion
- Bank allocation algorithm choice (FFD bin-packing strategy, scene locality grouping, bank fill headroom policy) -- guiding principle: performance is paramount, memory correctness is non-negotiable
- Pass output format (structured annotations vs side effects) -- Claude picks based on compiler design best practices
- Exact warning threshold defaults
- Budget report column layout and formatting

### Deferred Ideas (OUT OF SCOPE)
- **Collection Abstractions Phase (3.1):** IRHashTable, IRPool, IRRingBuffer, IRFixedSlots as first-class IR nodes with hybrid backend traits -- already implemented before Phase 4. Context already captured in `.planning/phases/03.1-collection-abstractions/03.1-CONTEXT.md`.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ANLZ-01 | Validation pass (ref resolution, type checks, DSL constraint enforcement) | Existing `GameValidator` validates v1 `Game` objects. Need equivalent for v2 `GameIR` walking `ScriptOp` sealed hierarchy, `Expr` sealed hierarchy, `Ref`/`RefKind` system. `IRWalker.walkStatements()` provides recursive descent pattern. |
| ANLZ-02 | Bank allocation pass (bin-packing, scene locality, trampoline generation) | `CFile.bank` and `CFunction.bank`/`isBanked` fields ready. `CartridgeConfig.romBanks` and `TargetProfile.maxRomSize`/`supportsBanking` provide constraints. FFD bin-packing maps functions to 16KB ROM banks. Trampoline stubs go in HOME bank (bank 0 = `main.c`). |
| ANLZ-03 | VRAM planning pass (per-scene tile slots, shared tile detection) | GB has 384 VRAM tiles total (0x8000-0x97FF). `TileDeduplicator` already does content-hash dedup. `SpriteDef.size` + `AssetRef` provide per-actor tile counts. `SceneIR.actorIds` links scenes to actors. Sprite tiles grow from index 0; BG tiles from 127 downward. |
| ANLZ-04 | OAM planning pass (sprite slot allocation, scanline density analysis) | `GameBoyConstants.MAX_SPRITES=40`, `MAX_SPRITES_PER_SCANLINE=10`. `SpriteSpec` in profile. `ActorIR.position` + `SpriteDef.size` enable scanline density estimation. `OAMSlot` annotation type exists. |
| ANLZ-05 | RAM planning pass (WRAM layout, HRAM allocation, SRAM structure) | `MemorySpec` gives WRAM (8KB DMG / 32KB GBC), HRAM (127 bytes), SRAM bank size (8KB). Collection IR nodes have `sizeBytes` properties. `VariableDef` has `VarType` with known sizes (U8=1, U16=2, I8=1, I16=2). v1 `validateWRAMUsage()` demonstrates the estimation pattern. |
| ANLZ-06 | Budget audit pass (human-readable build report, hard fail on overflow) | Output is terminal ASCII table. `ValidationResult` type with errors/warnings/info levels provides output model. Gradle `budgetReport` task will invoke analysis and format output. |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib | 2.3.0 | Data classes, sealed interfaces, collections | Already in project, immutable data classes ideal for pass contexts |
| JUnit 5 | (existing) | Testing analysis passes | Already used across all modules |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `gbkt-core` | (project) | IR types, validation infrastructure, collection IR nodes | Dependency for analysis module |
| `gbkt-backend-api` | (project) | `TargetProfile`, `ValidationResult`, `CodegenBackend` | Dependency for hardware constraints |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| New `gbkt-analysis` module | Put passes in `gbkt-backend-gbdk` | Separate module keeps analysis platform-agnostic; other backends can reuse semantic validation, dead code elimination, constant folding. Only hardware-specific passes (VRAM, OAM) are backend-specific. |
| Immutable context threading | Mutable pass state | Immutable context prevents accidental state corruption between passes, matches project's data class conventions. Small allocation overhead is irrelevant at compile time. |
| Sealed class for pass results | Open class hierarchy | Sealed ensures exhaustive handling of pass outcomes (success with annotations, error with diagnostics). |

**Installation:**
```kotlin
// settings.gradle.kts
include("gbkt-analysis")

// gbkt-analysis/build.gradle.kts
dependencies {
    api(project(":gbkt-backend-api"))  // Transitively includes gbkt-core
    testImplementation(kotlin("test"))
}
```

## Architecture Patterns

### Recommended Module Structure
```
gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/
├── AnalysisPass.kt           # fun interface + PassContext + PassResult
├── PassPipeline.kt           # Ordered executor with fail-fast + extension hooks
├── PassContext.kt             # Immutable context threading annotations + diagnostics
├── AnnotatedGameIR.kt         # GameIR + analysis annotations wrapper
├── passes/
│   ├── SemanticValidationPass.kt   # ANLZ-01: ref resolution, type checks
│   ├── ResourceInventoryPass.kt    # Collects all resources (tiles, sprites, vars)
│   ├── ConstraintCheckPass.kt      # Hardware limit validation
│   ├── BankingAnalysisPass.kt      # ANLZ-02: FFD bin-packing + trampolines
│   ├── VRAMLayoutPass.kt           # ANLZ-03: per-scene tile allocation
│   ├── OAMAllocationPass.kt        # ANLZ-04: sprite slots + scanline density
│   ├── RAMPlanningPass.kt          # ANLZ-05: WRAM + HRAM + SRAM layout
│   ├── DeadCodeEliminationPass.kt  # Remove unreachable scenes/actors
│   ├── ConstantFoldingPass.kt      # Fold compile-time-known expressions
│   └── BudgetAuditPass.kt          # ANLZ-06: report generation + hard fail
├── report/
│   └── BudgetReporter.kt           # ASCII table formatting
└── config/
    └── AnalysisConfig.kt           # Threshold defaults, MBC limits
```

### Pattern 1: Immutable Context Threading (Recommended for Pass Output)
**What:** Each pass receives an immutable `PassContext` and returns a new `PassContext` with accumulated results. No side effects between passes.
**When to use:** Always. This is the standard compiler pass pattern for correctness.
**Example:**
```kotlin
// Source: Standard compiler design pattern
fun interface AnalysisPass {
    val name: String
    fun run(context: PassContext): PassResult
}

sealed interface PassResult {
    data class Success(val context: PassContext) : PassResult
    data class Failed(val diagnostics: List<Diagnostic>) : PassResult
}

data class PassContext(
    val game: GameIR,
    val profile: TargetProfile,
    val config: AnalysisConfig,
    // Accumulated annotations from previous passes
    val bankAssignments: Map<String, BankSlot> = emptyMap(),
    val vramAssignments: Map<String, VRAMRange> = emptyMap(),
    val oamAssignments: Map<String, OAMSlot> = emptyMap(),
    val ramLayout: RAMLayout? = null,
    val diagnostics: List<Diagnostic> = emptyList(),
    // Resource inventory (filled by ResourceInventoryPass)
    val inventory: ResourceInventory? = null,
)
```

### Pattern 2: FFD Bin-Packing for Bank Allocation (Recommended)
**What:** Sort code units (scene functions, system functions, asset data) by estimated size in descending order. Assign each to the first bank with sufficient remaining capacity. Reserve headroom per bank (e.g., 90% fill target) to allow incremental builds without reshuffling.
**When to use:** BankingAnalysisPass.
**Why FFD:** Scene locality grouping is a secondary objective -- FFD naturally groups large scenes into dedicated banks. The 11/9 * OPT + 6/9 approximation ratio is well within acceptable bounds for Game Boy ROM banking.
**Example:**
```kotlin
data class CodeUnit(
    val id: String,          // e.g., "gameplay_enter", "tileset_dungeon"
    val estimatedBytes: Int,
    val sceneAffinity: String?, // Group by scene when possible
)

fun allocateBanks(
    units: List<CodeUnit>,
    bankCapacity: Int = 16_384,   // 16KB per ROM bank
    fillTarget: Double = 0.90,     // 90% headroom
    maxBanks: Int,                 // From MBC type config
): Map<String, BankSlot> {
    val effectiveCapacity = (bankCapacity * fillTarget).toInt()
    val sorted = units.sortedByDescending { it.estimatedBytes }
    // ... FFD assignment with scene locality tie-breaking
}
```

### Pattern 3: Per-Scene VRAM Budget Calculation
**What:** For each scene, compute BG tile budget = 384 - sprite tiles reserved for that scene's actors. Sprite tiles = sum of tile counts for all actors in `scene.actorIds`. BG tiles shared globally (fonts, UI) get fixed slots 0..N. Scene-specific BG tiles allocated from the remaining range.
**When to use:** VRAMLayoutPass.
**Example:**
```kotlin
// GB VRAM layout: 384 tiles total
// Tiles 0-127: Shared between sprites and BG (mode-dependent)
// Tiles 128-255: BG only
// Tiles 256-383: BG only (overlaps with sprite area in some addressing modes)

fun computeSceneTileBudget(
    scene: SceneIR,
    actors: List<ActorIR>,
    globalTiles: Int, // UI, fonts
): SceneTileBudget {
    val spriteActors = actors.filter { it.id in scene.actorIds && it.sprite != null }
    val spriteTiles = spriteActors.sumOf { computeSpriteTileCount(it.sprite!!) }
    val availableBgTiles = 384 - spriteTiles - globalTiles
    return SceneTileBudget(
        sceneId = scene.id,
        spriteTilesReserved = spriteTiles,
        bgTilesAvailable = availableBgTiles,
        globalTilesUsed = globalTiles,
    )
}
```

### Pattern 4: Trampoline Generation for Cross-Bank Calls
**What:** When function A in bank X calls function B in bank Y, the compiler must generate a trampoline stub in HOME bank (bank 0) that does `SWITCH_ROM(Y); call B; SWITCH_ROM(saved_bank)`. The analysis pass detects these cross-bank calls and generates `CFunction` trampoline stubs automatically.
**When to use:** BankingAnalysisPass, after bank assignments are determined.
**GBDK detail:** GBDK-2020's `BANKED` calling convention handles this automatically for functions declared with `__banked` attribute. The analysis pass ensures all banked functions are tagged with `isBanked = true` on their `CFunction` node.

### Anti-Patterns to Avoid
- **Mutable shared state between passes:** Each pass must receive and return immutable context. Do not use `var` fields on a shared context object -- this caused the bank-leak bug documented in MEMORY.md.
- **Skipping fail-fast on validation errors:** Running VRAM planning on invalid IR (dangling refs, type mismatches) produces nonsensical results. Always gate later passes on earlier pass success.
- **Hardcoding hardware constants:** Always read from `TargetProfile` / `MemorySpec` / `SpriteSpec`. The same pipeline must work for DMG (8KB WRAM, 1 VRAM bank) and GBC (32KB WRAM, 2 VRAM banks).
- **Conflating code size with ROM bank size:** A function's C source lines do not directly predict compiled binary size. Use conservative heuristics (e.g., 4 bytes/C statement average) or read `.noi` output from previous builds for calibration.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bin packing | Custom bin-packing from scratch | FFD with scene-locality tie-breaking | FFD is a well-studied O(N log N) algorithm with proven 11/9 approximation ratio. No need for more complex heuristics for the typical Game Boy game size (< 256 banks). |
| IR tree walking | Manual recursive descent per pass | Extend existing `IRWalker.walkStatements()` pattern | Already proven in v1 `GameValidator`. Add `walkScriptOps()` for v2 `ScriptOp` sealed hierarchy. |
| Diagnostic formatting | Custom error message formatting | Reuse `ValidationResult` / `ValidationMessage` from `gbkt-core` | Already has severity levels, categories, locations. Extend with `Diagnostic` wrapper if needed. |
| VRAM tile counting | Per-sprite manual calculation | `TileDeduplicator` + `SpriteDef.size` math | `TileDeduplicator` already handles content-hash dedup. Tile count = `(width/8) * (height/8) * frameCount`. |
| Terminal formatting | Custom ASCII table builder | Small utility class (< 100 lines) | Terminal tables are simple enough to hand-build. No external dependency needed. Just build a `BudgetReporter` utility. |

**Key insight:** The existing v1 `GameValidator` (1000+ lines) contains working logic for OAM limit checking, WRAM estimation, VRAM tile counting, reference validation, duplicate detection, and memory breakdown. While it operates on the v1 `Game` type, the algorithms translate directly to v2 `GameIR`. Reuse the logic, not the code.

## Common Pitfalls

### Pitfall 1: Bank Size Estimation Inaccuracy
**What goes wrong:** Estimated function sizes in ROM bytes don't match actual compiled output. A function estimated at 200 bytes compiles to 400 bytes due to SDCC's code expansion for banked calls, switch tables, and library function inlining.
**Why it happens:** Source-level heuristics (bytes per C statement) are inherently approximate. SDCC generates different code sizes depending on optimization level and calling conventions.
**How to avoid:** Use conservative estimates (8 bytes/statement instead of 4). Set bank fill target to 85-90% to leave headroom. The BudgetAuditPass should emit warnings at 85% fill and errors only at 100%.
**Warning signs:** `buildRom` fails with "bank N overflow" when `budgetReport` showed the bank as under-limit.

### Pitfall 2: VRAM Tile Budget Ignoring Addressing Modes
**What goes wrong:** Analysis assumes 384 tiles are freely allocatable, but GBDK addressing mode affects which tiles sprites vs backgrounds can access.
**Why it happens:** Game Boy has two tile data addressing modes ($8800 and $8000). In $8000 mode (GBDK default), sprites use tiles 0-255 and backgrounds use tiles 0-383 (overlapping). In $8800 mode, sprites use tiles 0-255 and backgrounds use tiles 128-383.
**How to avoid:** Assume GBDK default ($8000 mode). Sprites take tiles 0..N from the bottom. BG tiles can use the full 0-383 range but must not overlap with sprite tiles unless explicitly shared.
**Warning signs:** Visual corruption on real hardware where sprites and BG tiles share VRAM indices.

### Pitfall 3: Scanline Analysis Without Y-Position Knowledge
**What goes wrong:** OAM scanline density analysis reports false positives because it doesn't know where sprites actually are on screen.
**Why it happens:** Actor positions in `ActorIR.position` are initial positions -- actors move at runtime. Scanline analysis can only estimate worst-case scenarios.
**How to avoid:** Report scanline warnings as advisory ("Scene 'gameplay' has 12 actors -- may exceed 10/scanline limit if clustered"). Don't hard-fail on scanline density.
**Warning signs:** False positive warnings that make developers ignore all warnings.

### Pitfall 4: Forgetting Collection IR in RAM Estimation
**What goes wrong:** RAMPlanningPass counts variables and actor state but forgets collection IR nodes, leading to WRAM underestimation.
**Why it happens:** Collections were added in Phase 3.1 -- their `sizeBytes` properties must be summed.
**How to avoid:** Explicitly enumerate all RAM-consuming IR nodes: `VariableDef`, actor state (4-5 bytes each), `IRCollHashTable.sizeBytes`, `IRCollPool.sizeBytes`, `IRCollRingBuffer.sizeBytes`, `IRCollFixedSlots.sizeBytes`, camera state, dialog state, scene management overhead.
**Warning signs:** `buildRom` produces a ROM that crashes at runtime due to stack/WRAM collision.

### Pitfall 5: Circular Dependency Between Analysis and Backend
**What goes wrong:** Analysis module imports GBDK-specific types, creating a circular dependency or preventing future backends from reusing analysis.
**Why it happens:** Temptation to use `CFunction` or `CFile` types in analysis passes.
**How to avoid:** Analysis operates purely on IR types (`GameIR`, `SceneIR`, `ActorIR`, etc.) and `TargetProfile`. It produces annotations (`BankSlot`, `VRAMRange`, `OAMSlot`) that the backend reads. Never import from `gbkt-backend-gbdk` in the analysis module.
**Warning signs:** `gbkt-analysis` has a dependency on `gbkt-backend-gbdk` in `build.gradle.kts`.

### Pitfall 6: Plugin API Misdesign -- Too Many Extension Points
**What goes wrong:** The custom pass API has complex registration with named phases, ordering constraints, and dependency tracking. Users never use it because the API is too complicated.
**Why it happens:** Over-engineering the extension points for hypothetical future needs.
**How to avoid:** Two simple hooks: `beforeBuiltIn: List<AnalysisPass>` and `afterBuiltIn: List<AnalysisPass>`. Custom passes get the same `PassContext` and return the same `PassResult`. No ordering API -- before-passes run in registration order before built-ins; after-passes run after.
**Warning signs:** More than 20 lines of code to register a custom pass.

## Code Examples

### Example 1: AnalysisPass Interface
```kotlin
// Functional interface for a single analysis pass
fun interface AnalysisPass {
    fun run(context: PassContext): PassResult
}

// Metadata companion
data class PassInfo(
    val name: String,
    val description: String,
)
```

### Example 2: PassPipeline Executor with Fail-Fast
```kotlin
class PassPipeline(
    private val beforePasses: List<AnalysisPass> = emptyList(),
    private val builtInPasses: List<AnalysisPass>,
    private val afterPasses: List<AnalysisPass> = emptyList(),
) {
    fun execute(initial: PassContext): PassResult {
        var context = initial
        val allPasses = beforePasses + builtInPasses + afterPasses
        for (pass in allPasses) {
            when (val result = pass.run(context)) {
                is PassResult.Success -> context = result.context
                is PassResult.Failed -> return result // Fail fast
            }
        }
        return PassResult.Success(context)
    }
}
```

### Example 3: Integrating Analysis into GBDKBackend
```kotlin
// In GBDKBackend.generateV2():
fun generateV2(gameIR: GameIR): GenerationResult {
    // 1. Run analysis pipeline
    val analysisConfig = AnalysisConfig.fromCartridgeConfig(gameIR.config)
    val pipeline = PassPipeline(builtInPasses = defaultPasses(profile))
    val initialContext = PassContext(game = gameIR, profile = profile, config = analysisConfig)

    val analysisResult = pipeline.execute(initialContext)
    if (analysisResult is PassResult.Failed) {
        return GenerationResult.failed(analysisResult.diagnostics.format())
    }

    val annotatedContext = (analysisResult as PassResult.Success).context

    // 2. Apply annotations to GameIR (copy with filled platform fields)
    val annotatedGame = applyAnnotations(gameIR, annotatedContext)

    // 3. Generate C code from annotated IR
    val codegen = GBDKPipelineV2()
    val files = codegen.generate(annotatedGame)
    // ...
}
```

### Example 4: Budget Report ASCII Table
```
======================================================
 gbkt Budget Report                      pong v1.0
======================================================

 ROM Banks (2 / 32 max, MBC5)
 ├── Bank 0 (HOME)  ████████░░░░░░░░  3.2 KB / 16 KB  (20%)
 └── Bank 1         ██████████████░░  12.8 KB / 16 KB  (80%)

 VRAM Tile Budget (per scene)
 ┌───────────┬──────────┬──────────┬───────────┐
 │ Scene     │ Sprite   │ BG Avail │ BG Used   │
 ├───────────┼──────────┼──────────┼───────────┤
 │ title     │    4     │   380    │    32     │
 │ gameplay  │   24     │   360    │   180     │
 │ gameover  │    0     │   384    │    16     │
 └───────────┴──────────┴──────────┴───────────┘

 OAM Sprites: 6 / 40 (15%)
 WRAM: 142 / 6144 bytes (2%)
 HRAM: 0 / 127 bytes (0%)

 0 errors, 0 warnings
======================================================
```

### Example 5: Actionable Tile Overflow Error
```
error[ANLZ-03]: VRAM tile overflow in scene 'dungeon_floor1'
  --> src/main/kotlin/Dungeon.kt:45
  |
  | Total tiles required: 412 (limit: 384)
  |
  | Breakdown:
  |   Sprites (actors):      48 tiles (player: 16, enemy_goblin: 16, enemy_bat: 16)
  |   Background (tileset):  320 tiles (dungeon_tileset.png)
  |   UI (shared):           44 tiles (font: 36, hud_frame: 8)
  |
  | Suggestions:
  |   - Split 'dungeon_tileset.png' into shared/unique portions
  |   - Consider a sub-scene for areas with unique tiles
  |   - Reduce actor sprite frame count (currently 4 frames each)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `#pragma bank N` in generated C | Typed `CFile.bank` + `CFunction.bank` fields | Phase 2 (current) | Eliminated bank-leak bugs. Analysis pass populates these fields instead of string manipulation. |
| v1 `GameValidator` on `Game` type | v2 analysis passes on `GameIR` type | Phase 4 (this phase) | Moves from monolithic validation to ordered pass pipeline. Validation logic is reusable per-pass. |
| String-based code generation with `setBank()`/`returnToHome()` | Typed C AST with `CFile`/`CFunction` nodes | Phase 2 (current) | Bank assignment is structural, not stateful. Analysis pass simply sets `bank` field on IR nodes. |
| Developer manually assigns ROM banks | Automatic FFD bin-packing | Phase 4 (this phase) | Zero manual bank annotations. Developer only sets MBC type in config. |

**Deprecated/outdated:**
- v1 string-based `GBDKCodeGenerator` -- still functional but not extended. New games use `GBDKPipelineV2`.
- `setBank()`/`returnToHome()` mutable state pattern -- replaced by immutable `CFile.bank` field.

## Codebase Integration Points

### Existing Types to Consume

| Type | Location | How Analysis Uses It |
|------|----------|---------------------|
| `GameIR` | `gbkt-core/.../ir/v2/GameIR.kt` | Root input to analysis pipeline |
| `SceneIR` | `gbkt-core/.../ir/v2/SceneIR.kt` | Per-scene resource budgeting (implements `PlatformAnnotatable`) |
| `ActorIR` | `gbkt-core/.../ir/v2/ActorIR.kt` | Sprite tile counting, OAM slot allocation (implements `PlatformAnnotatable`) |
| `SystemIR` | `gbkt-core/.../ir/v2/SystemIR.kt` | Bank assignment for system code (implements `PlatformAnnotatable`) |
| `ScriptOp` | `gbkt-core/.../ir/v2/ScriptOp.kt` | Walk for dead code, constant folding, cross-ref validation |
| `Expr` | `gbkt-core/.../ir/v2/Expr.kt` | Constant folding (fold `Literal` operations at compile time) |
| `VariableDef` | `gbkt-core/.../ir/v2/Types.kt` | RAM size calculation (VarType U8=1 byte, U16=2 bytes) |
| `CartridgeConfig` | `gbkt-core/.../ir/v2/Types.kt` | MBC type, ROM bank count, RAM bank count |
| `SpriteDef` / `SizeDef` | `gbkt-core/.../ir/v2/Types.kt` | Tile count calculation per sprite |
| `AssetRef` / `AssetType` | `gbkt-core/.../ir/v2/AssetRef.kt` | Asset classification (SPRITE, TILEMAP, TILESET, FONT) |
| `Ref` / `RefKind` | `gbkt-core/.../ir/v2/Ref.kt` | Reference resolution validation |
| `BankSlot` / `VRAMRange` / `OAMSlot` | `gbkt-core/.../ir/v2/PlatformAnnotations.kt` | Output annotations from analysis |
| `PlatformAnnotatable` | `gbkt-core/.../ir/v2/PlatformAnnotations.kt` | Interface for IR nodes that receive annotations |
| `TargetProfile` | `gbkt-core/.../constraints/TargetProfile.kt` | Hardware constraint data source |
| `MemorySpec` | `gbkt-core/.../constraints/MemorySpec.kt` | WRAM, VRAM, HRAM, ROM bank size |
| `SpriteSpec` | `gbkt-core/.../constraints/SpriteSpec.kt` | OAM limits, scanline limits |
| `ValidationResult` | `gbkt-core/.../Validation.kt` | Diagnostic output model |
| `IRCollHashTable` / `IRCollPool` / etc. | `gbkt-core/.../ir/CollectionsIR.kt` | Collection `sizeBytes` for RAM planning |
| `TileDeduplicator` | `gbkt-core/.../TileDeduplicator.kt` | Content-hash tile deduplication |

### Existing Types to Produce/Modify

| Type | Location | What Analysis Sets |
|------|----------|--------------------|
| `SceneIR.bankSlot` | via data class `copy()` | ROM bank for scene code |
| `SceneIR.vramRange` | via data class `copy()` | Tile range for scene-specific BG tiles |
| `ActorIR.bankSlot` | via data class `copy()` | ROM bank for actor sprite data |
| `ActorIR.vramRange` | via data class `copy()` | VRAM tiles for this actor's sprite |
| `ActorIR.oamSlot` | via data class `copy()` | OAM slot for this actor |
| `CFunction.bank` | (backend reads annotation) | ROM bank for generated function |
| `CFunction.isBanked` | (backend reads annotation) | Whether BANKED keyword is needed |
| `CFile.bank` | (backend reads annotation) | Which bank file to emit to |

### Game Boy Hardware Constants (from `GameBoyConstants`)

| Constant | Value | Relevance |
|----------|-------|-----------|
| `MAX_SPRITES` | 40 | Hard OAM limit |
| `MAX_SPRITES_PER_SCANLINE` | 10 | Scanline density warning threshold |
| `ROM_BANK_SIZE` | 16,384 (16 KB) | Bank capacity for FFD bin-packing |
| `OAM_SIZE` | 160 bytes | 40 entries x 4 bytes |
| `HRAM_SIZE` | 127 bytes | High RAM budget |
| `RAM_BANK_SIZE` | 8,192 (8 KB) | External RAM bank size |
| `MAX_ROM_SIZE` | 8,388,608 (8 MB) | MBC5 max ROM |
| `MAX_RAM_BANKS` | 16 | MBC5 max external RAM banks |
| VRAM tiles | 384 | $8000-$97FF, 16 bytes/tile |
| DMG WRAM | 8,192 (8 KB) | $C000-$DFFF |
| GBC WRAM | 32,768 (32 KB) | 4 KB + 7 x 4 KB switchable |

### MBC Bank Limits (for DSL config)

| MBC Type | Max ROM Banks | Max Size | Banked Call Limit |
|----------|---------------|----------|-------------------|
| ROM_ONLY | 2 | 32 KB | N/A (no banking) |
| MBC1 | 32 | 512 KB | Bank 31 max |
| MBC3 | 128 | 2 MB | Bank 127 max |
| MBC5 | 256 | 4 MB | Bank 255 max |
| MBC5 (8M) | 512 | 8 MB | Requires special macro |

### Recommended Warning Threshold Defaults

| Metric | Warning At | Error At | Rationale |
|--------|-----------|----------|-----------|
| Bank fill | 85% (13,926 B) | 100% (16,384 B) | Leave headroom for SDCC code expansion |
| VRAM tiles per scene | 350 / 384 (91%) | 385+ (overflow) | Small margin for off-by-one in tile counting |
| OAM sprites | 35 / 40 (87%) | 41+ (overflow) | Leave slots for particle effects |
| Scanline density | 8 / 10 per scanline | (warning only) | Runtime behavior is unpredictable |
| WRAM | 83% of available | 100% of available | Match v1 `WRAM_WARNING_THRESHOLD` |
| HRAM | 80% (101 / 127 B) | 100% (127 B) | HRAM is precious, warn early |

## Open Questions

1. **Code size estimation accuracy**
   - What we know: C statement count is a rough proxy. SDCC averages ~4-8 bytes per C statement depending on complexity.
   - What's unclear: Exact ratio for GBDK-2020 output with banked calling convention overhead.
   - Recommendation: Start with 6 bytes/statement. Calibrate against `.noi` output from existing example game builds. Make the ratio configurable in `AnalysisConfig`.

2. **GBC-specific VRAM banking (2 banks)**
   - What we know: GBC has 2 VRAM banks (16 KB total vs 8 KB on DMG). This doubles tile capacity to 768.
   - What's unclear: Whether GBDK-2020 exposes easy APIs for VRAM bank switching, or if this requires manual management.
   - Recommendation: For Phase 4, assume single VRAM bank (384 tiles) as the common case. Add GBC dual-VRAM support as a follow-up optimization. The `TargetProfile.memory.videoRam` field already distinguishes DMG (8 KB) from GBC (16 KB).

3. **Where does `gbkt-analysis` module go in dependency graph?**
   - What we know: It needs `gbkt-core` (IR types) and `gbkt-backend-api` (`TargetProfile`). The GBDK backend needs analysis results.
   - What's unclear: Whether analysis should be invoked by the backend or by the Gradle plugin.
   - Recommendation: `gbkt-analysis` depends on `gbkt-backend-api` (transitively gets `gbkt-core`). The `gbkt-backend-gbdk` depends on `gbkt-analysis`. Backend invokes analysis in `generateV2()`. Gradle plugin adds `budgetReport` task that invokes analysis directly (no codegen).

## Sources

### Primary (HIGH confidence)
- Codebase exploration via Serena MCP tools -- all IR types, backend interfaces, validation patterns, hardware profiles, pipeline structure directly read from source.
- [Pan Docs - Tile Data](https://gbdev.io/pandocs/Tile_Data.html) -- GB VRAM tile layout, 384 tiles, addressing modes
- [Pan Docs - OAM](https://gbdev.io/pandocs/OAM.html) -- 40 sprites, 10 per scanline, OAM scan order
- [Pan Docs - Memory Map](https://gbdev.io/pandocs/Memory_Map.html) -- WRAM, HRAM, SRAM, Echo RAM layout

### Secondary (MEDIUM confidence)
- [GBDK-2020 Docs - ROM Banking](http://gbdk.org/docs/api/docs_rombanking_mbcs.html) -- `#pragma bank`, autobanking, MBC limits, `SWITCH_ROM()`
- [First-fit-decreasing bin packing (Wikipedia)](https://en.wikipedia.org/wiki/First-fit-decreasing_bin_packing) -- FFD algorithm properties, 11/9 approximation bound
- [GB Studio Lab - VRAM Tile Limits](https://gbstudiolab.neocities.org/guides/vram-tile-limits) -- Practical tile allocation guidance
- [Larold's Retro Gameyard - ROM Banks](https://laroldsretrogameyard.com/tutorials/gb/how-to-use-rom-memory-banks/) -- Practical GBDK banking tutorial

### Tertiary (LOW confidence)
- Code size estimation heuristic (6 bytes/C statement) -- based on general SDCC knowledge, not verified against gbkt output. Needs calibration.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- Pure Kotlin, no external dependencies. All types exist in codebase.
- Architecture: HIGH -- Pass pipeline pattern is well-established in compiler design. Codebase conventions (data classes, sealed interfaces, immutable state) naturally support it.
- Hardware constraints: HIGH -- Game Boy specs are fixed hardware, well-documented in Pan Docs and verified against existing `GameBoyConstants` / `TargetProfile` in codebase.
- Code size estimation: MEDIUM -- Heuristic-based, needs calibration against real GBDK builds.
- Pitfalls: HIGH -- Bank-leak and VRAM corruption bugs documented in project MEMORY.md. Validation patterns proven in 1000+ line v1 `GameValidator`.

**Research date:** 2026-02-18
**Valid until:** 2026-03-18 (30 days -- stable domain, fixed hardware specs)
