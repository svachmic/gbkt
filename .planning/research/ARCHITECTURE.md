# Architecture Research

**Domain:** DSL-to-native-code compiler framework (Kotlin DSL -> C for Game Boy)
**Researched:** 2026-02-17
**Confidence:** HIGH (derived from codebase analysis + established compiler architecture patterns)

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                         FRONTEND LAYER                               │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │   :dsl module   │  │   :assets module  │  │  :ir module      │   │
│  │  Kotlin DSL     │  │  PNG/TMX/PO proc  │  │  Sealed IR types │   │
│  │  builders       │  │  → AssetIR nodes  │  │  zero deps       │   │
│  └────────┬────────┘  └────────┬──────────┘  └────────┬─────────┘  │
│           │                   │                        │            │
│           └───────────────────┴────────────────────────┘            │
│                               │ emits GameIR                        │
└───────────────────────────────┼─────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────────┐
│                        ANALYSIS LAYER                                │
│  :analysis module — ordered pipeline of GameIR → GameIR passes      │
│                                                                      │
│  Pass 1: SemanticValidation   (type checks, missing refs)           │
│  Pass 2: ResourceInventory    (count sprites, tiles, sounds)        │
│  Pass 3: ConstraintCheck      (OAM ≤40, WRAM ≤6KB, palette ≤8)     │
│  Pass 4: BankingAnalysis      (bin-pack code into 16KB banks)       │
│  Pass 5: VRAMLayout           (assign tiles to VRAM addresses)      │
│  Pass 6: OAMAllocation        (assign sprite slots per scene)       │
│  Pass 7: DeadCodeElimination  (drop unused IR nodes)               │
│  Pass 8: ConstantFolding      (reduce static expressions)           │
│  Pass 9: AnnotatedIR          (attach layout metadata to nodes)     │
│                               │ produces AnnotatedGameIR            │
└───────────────────────────────┼─────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────────┐
│                        CODEGEN LAYER                                 │
│  :codegen module — IR → C AST → pretty-printed C strings           │
│                                                                      │
│  IR Visitor → C AST nodes (CFunction, CStruct, CPragma, etc.)      │
│  C AST → CEmitter (bank-aware, source-mapped)                      │
│  Output: Map<String, String>  filename → C source                  │
└──────────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────────┐
│                       TOOLING LAYER                                  │
│  :gradle-plugin  :cli  :intellij-plugin  :test-runner               │
│  Orchestrate build pipeline; invoke frontend → analysis → codegen   │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `:ir` (within gbkt-core) | Define sealed IR node hierarchies — zero dependencies | Kotlin sealed interfaces + data classes; all must be in one module (Kotlin constraint) |
| `:dsl` (within gbkt-core) | Execute Kotlin DSL, capture operations as IR via RecordingContext | Thread-local context; property delegates; operator overloads on Expr |
| `:assets` (within gbkt-core) | Process PNG/TMX/PO files into asset IR nodes | PNG decoder, Tiled TMX parser, PO parser; emits asset references with metadata |
| `:analysis` | Run ordered passes over GameIR; each pass is GameIR → GameIR | Pass interface + pipeline executor; passes annotate IR with layout metadata |
| `:codegen` | Transform annotated IR into C AST, then emit C source strings | Visitor over IR nodes; structured C AST builder (not raw string concatenation); bank-aware emitter |
| `:gradle-plugin` | Orchestrate pipeline; invoke lcc compiler; manage build tasks | Gradle plugin; GenerateCTask, CompileRomTask, RunEmulatorTask |
| `:test-runner` | JVM-side execution of game logic for unit tests | ScriptOp interpreter; SimulationContext; InlineExecutor |
| `:backend-api` | Swappable backend contract; future GBA/NES targets | CodegenBackend interface with validate(game) + generate(game) methods |

---

## Recommended Project Structure

```
gbkt/
├── gbkt-core/                       # All IR + DSL (sealed interface constraint forces co-location)
│   ├── ir/                          # Sealed IR node types (IRStatement, IRExpression hierarchies)
│   ├── dsl/                         # RecordingContext, operators, DSL builders
│   ├── builder/                     # GameBuilder, GameConfig, feature registration
│   ├── assets/                      # Asset reference types (SpriteAsset, MapAsset, etc.)
│   ├── entity/                      # Entity/component system IR
│   ├── rpg/                         # RPG domain IR (stats, battle, abilities)
│   ├── world/                       # World/dungeon IR (floors, encounters, flags)
│   ├── exploration/                 # Exploration system IR
│   ├── flow/                        # Game flow IR (scene transitions, pause, save)
│   ├── graphics/                    # Graphics IR (sprites, tilemaps, camera)
│   ├── input/                       # Input IR
│   ├── scene/                       # Scene lifecycle IR
│   ├── ui/                          # UI IR (dialogs, menus, status bars)
│   └── validation/                  # Constraint definitions (TargetProfile)
│
├── gbkt-analysis/                   # Analysis pass pipeline (NEW)
│   ├── api/                         # AnalysisPass interface, AnnotatedGameIR, PassContext
│   ├── passes/
│   │   ├── SemanticValidationPass.kt
│   │   ├── ResourceInventoryPass.kt
│   │   ├── ConstraintCheckPass.kt
│   │   ├── BankingAnalysisPass.kt
│   │   ├── VRAMLayoutPass.kt
│   │   ├── OAMAllocationPass.kt
│   │   ├── DeadCodeEliminationPass.kt
│   │   ├── ConstantFoldingPass.kt
│   │   └── AnnotatedIRPass.kt
│   └── pipeline/                    # PassPipeline executor; ordering enforcement
│
├── gbkt-backend-api/                # Backend contract (unchanged)
│   └── CodegenBackend.kt            # validate(game) + generate(annotatedGame) methods
│
├── gbkt-backend-gbdk/               # GBDK C code generator
│   ├── codegen/
│   │   ├── ast/                     # C AST node types (NEW — no more raw string building)
│   │   │   ├── CDeclaration.kt      # CFunction, CStruct, CTypedef, CGlobalVar
│   │   │   ├── CStatement.kt        # CIf, CWhile, CFor, CReturn, CExprStatement
│   │   │   ├── CExpression.kt       # CLiteral, CBinary, CUnary, CCall, CArrayAccess
│   │   │   └── CUnit.kt             # CFile (root), CPragma, CInclude, CComment
│   │   ├── emit/
│   │   │   ├── CEmitter.kt          # C AST → string with indentation, source mapping
│   │   │   └── BankAwareEmitter.kt  # Wraps CEmitter; handles #pragma bank switching
│   │   ├── visitors/                # IR node visitors → C AST subtrees
│   │   │   ├── StatementVisitor.kt
│   │   │   ├── ExpressionVisitor.kt
│   │   │   ├── RPGVisitor.kt
│   │   │   ├── WorldVisitor.kt
│   │   │   └── GraphicsVisitor.kt
│   │   └── GBDKCodeGenerator.kt     # Orchestrates visitors → CUnit → BankAwareEmitter
│   └── profiles/                    # GB/GBC TargetProfile (VRAM limits, OAM limits, etc.)
│
├── gbkt-gradle-plugin/              # Build tooling (unchanged structure)
├── gbkt-cli/                        # CLI tool (unchanged)
├── gbkt-intellij-plugin/            # IDE support (unchanged)
└── gbkt-examples/                   # Example games (unchanged)
```

### Structure Rationale

- **gbkt-core is monolithic by constraint:** Kotlin sealed interfaces require all subclasses in the same compilation module. IRStatement, IRExpression, and all their subtypes must co-locate. This is not poor design — it is the correct trade-off to retain exhaustive `when` matching in codegen.
- **gbkt-analysis is a new module:** Analysis passes depend on GameIR (from core) but produce AnnotatedGameIR without depending on any backend. This is the correct dependency direction.
- **C AST nodes in gbkt-backend-gbdk/codegen/ast/:** The current architecture builds C as raw strings. A structured C AST separates "what C code to generate" from "how to format it", making codegen testable and bank switching traceable.
- **Visitors separate IR traversal from output:** Each domain (RPG, World, Graphics) gets its own visitor class. This replaces the single monolithic GBDKCodeGenerator with 50+ extension functions.

---

## Architectural Patterns

### Pattern 1: Ordered Pass Pipeline (LLVM-inspired)

**What:** Analysis is a sequence of independent passes, each taking GameIR and returning GameIR (or AnnotatedGameIR). Passes declare their dependencies on prior passes. The pipeline executor enforces ordering.

**When to use:** Any analysis that needs to query results from a prior analysis. BankingAnalysis needs ResourceInventory results; VRAMLayout needs BankingAnalysis results.

**Trade-offs:** Slightly more boilerplate than inline analysis, but each pass is independently testable and the ordering is explicit rather than buried in 1,700-line files.

**Example:**
```kotlin
interface AnalysisPass<in I : GameIR, out O : GameIR> {
    val name: String
    val requires: List<String>   // names of passes that must run before this one

    fun run(input: I, context: PassContext): O
}

// BankingAnalysisPass declares it needs ResourceInventory results
class BankingAnalysisPass : AnalysisPass<GameIR, GameIRWithBanking> {
    override val requires = listOf("ResourceInventory", "ConstraintCheck")

    override fun run(input: GameIR, context: PassContext): GameIRWithBanking {
        val inventory = context.get<ResourceInventoryResult>()
        // bin-pack IR nodes into 16KB bank slots
        return input.withBankAssignments(bankLayout)
    }
}
```

### Pattern 2: Structured C AST (not raw string concatenation)

**What:** The codegen layer first builds a C AST (structured data classes for C constructs), then emits it via a separate CEmitter. Bank switching is a CEmitter concern, not a codegen concern.

**When to use:** Whenever you need to generate C code that has structure (conditionals, loops, function bodies). String concatenation breaks down when you need to retroactively add includes, reorder declarations, or track line numbers for source maps.

**Trade-offs:** More upfront type design. Eliminates classes of bugs where bank state leaks across generation functions (the most critical current fragile area per CONCERNS.md).

**Example:**
```kotlin
// Instead of: line("#pragma bank $bank"); line("void scene_gameplay() {")
// Build AST:
val fn = CFunction(
    name = "scene_gameplay",
    returnType = CType.Void,
    body = listOf(
        CExprStatement(CCall("update_player", emptyList())),
        CIf(condition = CBinary(CVar("score"), CIntLiteral(100), BinaryOp.GTE),
            then = listOf(CExprStatement(CCall("win", emptyList()))))
    ),
    bank = 1   // bank is a CFunction property, not a side-effectful pragma call
)
// Emit:
val emitter = BankAwareEmitter()
emitter.emit(fn)  // emitter handles #pragma bank automatically based on fn.bank
```

### Pattern 3: Sealed IR + Exhaustive When (retain existing pattern)

**What:** All IR node types are sealed interfaces. Code generation uses `when (node)` without an `else` branch. The Kotlin compiler verifies all IR node types are handled.

**When to use:** Always — this is the foundational correctness guarantee of the pipeline. Never add `else ->` to IR dispatch.

**Trade-offs:** Requires all IR in one module (Kotlin constraint). The trade-off is accepted — module cohesion matters less than type safety in a compiler.

**Example:**
```kotlin
// Correct — exhaustive, compiler-verified
fun generateStatement(stmt: IRStatement): List<CStatement> = when (stmt) {
    is IRAssign     -> listOf(CAssignment(generateExpr(stmt.target), generateExpr(stmt.value)))
    is IRIf         -> listOf(CIf(generateExpr(stmt.cond), generateStatements(stmt.then), generateStatements(stmt.else_)))
    is IRWhile      -> listOf(CWhile(generateExpr(stmt.cond), generateStatements(stmt.body)))
    is IRSceneChange -> listOf(CExprStatement(CCall("scene_change", listOf(CVar("SCENE_${stmt.scene.name.uppercase()}")))))
    // ... all other cases — NO else branch
}
```

### Pattern 4: RecordingContext for DSL Capture (retain existing pattern)

**What:** DSL execution runs inside a `RecordingContext` (thread-local). Kotlin operators on `Expr`/`AssignableExpr` emit IR nodes instead of computing values. The game is represented as IR, not as a live Kotlin object graph.

**When to use:** All DSL execution. This is what makes `playerX += 2` generate `IRAssign(IRVar("player_x"), IRBinary(IRVar("player_x"), IRLiteral(2), ADD))` instead of actually computing.

**Trade-offs:** Thread-local state is fragile (see CONCERNS.md — parallel builds could corrupt). The fix is to wrap RecordingContext in a structured scope object, not to change the DSL capture approach itself.

---

## Data Flow

### Compilation Pipeline

```
Kotlin DSL source (.kt file)
    ↓
[JVM class loading via reflection — Gradle plugin / CLI]
    ↓
gbGame("MyGame") { ... } executes
    ↓ DSL execution inside RecordingContext
GameBuilder captures scene builders, variable registrations, entity definitions
    ↓ each DSL operation emits IR nodes
GameBuilder.build() → Game (immutable data class with complete IR tree)
    ↓
PassPipeline.run(game, targetProfile)
    ↓ Pass 1: SemanticValidation — errors halt pipeline
    ↓ Pass 2: ResourceInventory — count sprites, tiles, sounds
    ↓ Pass 3: ConstraintCheck — enforce OAM/WRAM/palette limits
    ↓ Pass 4: BankingAnalysis — assign IR nodes to ROM banks
    ↓ Pass 5: VRAMLayout — assign tile data to VRAM addresses
    ↓ Pass 6: OAMAllocation — assign sprite slots per scene
    ↓ Pass 7: DeadCodeElimination — remove unreferenced IR
    ↓ Pass 8: ConstantFolding — simplify static expressions
    ↓ Pass 9: AnnotatedIR — attach all layout metadata
    ↓ produces AnnotatedGameIR
GBDKBackend.generate(annotatedGame)
    ↓ IR visitors produce C AST node trees
    ↓ BankAwareEmitter traverses C AST
    ↓ emits #pragma bank directives based on node annotations (not mutable state)
GenerationResult: Map<String, String> (filename → C source)
    ↓
[lcc compiler invoked by Gradle plugin with bank-split .c files]
    ↓
.gb ROM file
```

### Key Data Flows

1. **DSL → IR:** RecordingContext.emit() is called by every Expr operator, scene builder, entity builder. IR nodes are appended to the current StatementRecorder. On build(), all recorders flush to the Game data class.

2. **IR → Analysis annotations:** Each pass reads the prior pass's output from PassContext. Passes produce typed result objects (BankingResult, VRAMLayoutResult) stored in PassContext for downstream passes. Final AnnotatedGameIR wraps original IR plus all pass results.

3. **AnnotatedIR → C AST:** Visitors iterate IR nodes. Visitors query AnnotatedGameIR for layout metadata (which bank? which VRAM address? which OAM slot?). C AST nodes are assembled bottom-up.

4. **C AST → C source:** BankAwareEmitter traverses C AST. When it encounters a CFunction with `bank != currentBank`, it emits `\n#pragma bank N\n` and updates its own state. Source map entries are recorded during traversal.

---

## Build Order for Implementation

This is the critical ordering for the refactoring milestones:

```
1. :ir refinement (pure data, sealed types, deepCopy, IRWalker)
        No dependencies — pure Kotlin data classes
        ↓
2. :dsl stabilization (RecordingContext, operator overloads, scope markers)
        Depends on :ir for node types
        ↓
3. :analysis module (AnalysisPass interface, 9 passes)
        Depends on :ir (for GameIR) — does NOT depend on codegen
        ↓
4. C AST types in :codegen (CDeclaration, CStatement, CExpression, CUnit)
        Independent — pure data classes, no IR dependency
        ↓ (steps 3 and 4 can run in parallel)
5. CEmitter + BankAwareEmitter in :codegen
        Depends on C AST types (step 4)
        ↓
6. IR visitors in :codegen (StatementVisitor, ExpressionVisitor, domain visitors)
        Depends on :ir (step 1) + AnnotatedGameIR (step 3) + C AST (step 4)
        ↓
7. GBDKCodeGenerator refactoring (orchestrates visitors → emitter)
        Depends on all codegen pieces (steps 4-6)
        ↓
8. :gradle-plugin and :cli wiring
        Depends on analysis pipeline (step 3) + backend (step 7)
        ↓
9. :test-runner (JVM ScriptOp interpreter)
        Depends on :ir (step 1) + :dsl (step 2)
```

---

## Anti-Patterns

### Anti-Pattern 1: Mutable Bank State in Generator

**What people do:** Maintain `var currentBank = 0` as mutable state in GBDKCodeGenerator. Call `setBank(N)` / `returnToHome()` as side effects during code emission.

**Why it's wrong:** Every codegen function must remember to call `returnToHome()` after switching banks. One forgotten call leaks bank state into all subsequent output. The bug only manifests at GBDK link time as "MBC5 unknown address" — not in Kotlin tests. This is documented in CONCERNS.md as the most fragile area.

**Do this instead:** Bank assignment is an analysis pass output (step 4 in the pipeline). Each C AST node carries its bank as a field. BankAwareEmitter emits `#pragma bank` transitions by comparing adjacent nodes' bank fields. The emitter's bank state is a traversal variable, not a generator field.

### Anti-Pattern 2: String-Based Codegen

**What people do:** Build C code by concatenating strings inside `line("void my_func() {")` calls. Logic and formatting are interleaved.

**Why it's wrong:** Hard to test (test output is a string, not a structure). Hard to reorder declarations. Hard to add includes retroactively. Hard to verify all cases are generated correctly without parsing the output string. The current MonsterCodegen.kt at 1,705 LOC is a symptom.

**Do this instead:** Visitors produce C AST data structures. CEmitter formats them. Tests assert on C AST structure, not output strings. Formatting changes require modifying only CEmitter.

### Anti-Pattern 3: Mixed Analysis in Codegen

**What people do:** Compute banking constraints, OAM counts, VRAM assignments, and dead code inside the code generator — interleaved with C emission.

**Why it's wrong:** Analysis and emission become coupled. Adding a new analysis requires modifying the emitter. Errors in analysis surface as malformed C output rather than explicit validation errors. Testing requires full codegen runs.

**Do this instead:** All analysis runs before codegen. The PassPipeline produces AnnotatedGameIR. The codegen only reads the analysis results — it never computes constraints itself.

### Anti-Pattern 4: Domain-Specific Logic in Backend

**What people do:** Game Boy-specific logic (OAM limits, bank layout, VRAM constraints) lives inside the codegen files rather than in platform-specific analysis passes.

**Why it's wrong:** Adding a second backend (GBA) requires duplicating constraint analysis or coupling backends together. The "LabyrinthOfTheDragon" coupling mentioned in the milestone context happens when domain knowledge bleeds into codegen strings.

**Do this instead:** TargetProfile defines all platform constraints (already exists). Analysis passes read TargetProfile. Codegen only reads AnnotatedGameIR — it doesn't know about GB-specific limits.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| GBDK lcc compiler | Gradle exec task; ProcessBuilder (not shell) | Must validate generated C before invoking; detect GBDK_HOME at task execution time |
| Tiled TMX files | TiledParser at DSL time; produces MapAsset IR nodes | Already exists; feeds into :assets module |
| PNG files | Asset pipeline at DSL time; produces tile data + palette | Already exists in AssetPipeline.kt |
| PO localization files | PoParser at DSL time; BankAllocator assigns strings to banks | Already exists; should be its own analysis pass (StringBankingPass) |
| mGBA emulator | Gradle exec task; path auto-detection | Already exists in RunEmulatorTask |
| IntelliJ plugin | Code completion on DSL calls; uses gbkt-core types via SDK | Reads IR node types for completion; requires stable API contract |

### Internal Module Boundaries

| Boundary | Communication | Direction | Notes |
|----------|---------------|-----------|-------|
| :ir ↔ :dsl | Direct import | dsl → ir | DSL emits IR nodes; IR has no knowledge of DSL |
| :ir ↔ :analysis | Direct import | analysis → ir | Analysis reads GameIR; produces AnnotatedGameIR |
| :ir ↔ :codegen | Direct import | codegen → ir | Codegen reads IR nodes; IR has no knowledge of codegen |
| :analysis ↔ :codegen | AnnotatedGameIR type | codegen reads analysis results | Codegen does not run analysis; only consumes results |
| :codegen ↔ :backend-api | CodegenBackend interface | backend-api is the contract | Backends implement interface; API defines GenerationResult |
| :gradle-plugin ↔ :codegen | GenerationResult type | plugin invokes backend | Plugin passes game path, gets C source files back |
| :test-runner ↔ :ir | Direct import | runner → ir | Runner interprets IR nodes as JVM operations |

---

## Scaling Considerations

| Concern | Current State | At 50+ scenes / 200+ monsters | At complex multi-game SDK |
|---------|---------------|-------------------------------|---------------------------|
| Codegen time | Sub-second (all in-memory) | Linear growth; unlikely to exceed 10s | Parallel per-game generation feasible |
| ROM bank overflow | Manual; error-prone (fragile area) | Analysis pass auto-detects; fails fast with diagnostic | Configurable bank strategy per TargetProfile |
| WRAM overflow | Validated post-hoc in Validation.kt | Pre-codegen check in ConstraintCheckPass | Target-specific WRAM limit in TargetProfile |
| IR node count | ~1,000 nodes for LabyrinthOfTheDragon | 10,000+ for complex RPGs; no performance issue (in-memory tree) | Per-game IR isolation; no cross-contamination |
| Test coverage | Black-box string output comparison | White-box C AST assertion; faster and more precise | Per-pass unit tests; pipeline integration tests |

### Scaling Priorities

1. **First bottleneck:** Bank overflow at ROM link time. Fix with BankingAnalysisPass (pre-codegen, gives clear error with "X bytes over limit in bank N").
2. **Second bottleneck:** OAM sprite limit exceeded silently. Fix with OAMAllocationPass (validates per-scene sprite counts before codegen).

---

## Confidence Assessment

| Claim | Confidence | Source |
|-------|------------|--------|
| Kotlin sealed interfaces require same-module subclasses | HIGH | Official KEEP proposal; Kotlin docs |
| LLVM-style ordered pass pipeline is the standard pattern | HIGH | LLVM documentation; multiple compiler textbooks |
| Structured C AST is superior to string concatenation | HIGH | Established compiler engineering practice; cgen library example |
| BankAwareEmitter approach eliminates mutable bank state bugs | HIGH | Derived from CONCERNS.md analysis of current fragile areas |
| 9-pass ordering is correct | MEDIUM | Derived from dependency analysis; specific ordering subject to revision during implementation |
| Analysis module can be a separate Gradle module from :ir | HIGH | Analysis does not define sealed types; only reads them |

---

## Sources

- Kotlin sealed interface module constraint: https://github.com/Kotlin/KEEP/blob/master/proposals/sealed-interface-freedom.md
- Kotlin sealed classes official docs: https://kotlinlang.org/docs/sealed-classes.html
- LLVM New Pass Manager (pipeline ordering): https://rocm.docs.amd.com/projects/llvm-project/en/latest/LLVM/llvm/html/NewPassManager.html
- LLVM pass ordering constraints: https://stephenverderame.github.io/blog/scheduling_llvm/
- Structured C AST generation (cgen library): https://github.com/inducer/cgen
- Flattening ASTs for compiler data structures: https://www.cs.cornell.edu/~asampson/blog/flattening.html
- Braid compiler architecture (multi-pass analysis): https://capra.cs.cornell.edu/braid/docs/hacking.html
- Kotlin IR lowering pipeline: https://deepwiki.com/JetBrains/kotlin/7.4-native-compilation-pipeline
- Compiler optimization pass ordering (Wikipedia): https://en.wikipedia.org/wiki/Optimizing_compiler
- gbkt codebase ARCHITECTURE.md: /Users/michalsvacha/GitHub/personal/gbkt/.planning/codebase/ARCHITECTURE.md (HIGH confidence — direct codebase analysis)
- gbkt codebase CONCERNS.md: /Users/michalsvacha/GitHub/personal/gbkt/.planning/codebase/CONCERNS.md (HIGH confidence — direct codebase analysis)

---

*Architecture research for: gbkt DSL-to-C compiler framework restructuring*
*Researched: 2026-02-17*
