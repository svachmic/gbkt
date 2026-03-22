# Phase 2: Structured Codegen and Migration Cut - Research

**Researched:** 2026-02-18
**Domain:** Typed C AST code generation, pretty-printing, IR-to-AST visitor pattern
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**C AST granularity:**
- AST nodes mirror C syntax closely (CIfStatement, CForLoop, CSwitch, CFunction, CVariable, etc.) — not higher-level "intent" nodes
- Bank assignment lives at both CFile and CFunction levels: CFile carries a bank, CFunction can override. Default is inheritance from file.
- Include CRawCode escape hatch for GBDK-specific quirks or inline asm that the AST doesn't model

**Migration strategy:**
- Old GBDKCodeGenerator is deprecated in Phase 2 (new pipeline takes over for Pong) but NOT deleted until Phase 5 after all three games validated
- This adjusts the original Phase 2 roadmap criterion: "deleted" becomes "deprecated, bypassed by new pipeline"
- Roadmap update needed: Phase 2 SC#5 changes from "deleted" to "deprecated and unused by new pipeline; deleted in Phase 5"

**Pong validation scope:**
- Pong must use the v2 DSL definition from Phase 1 — proves the full new pipeline: v2 DSL → IR → C AST → C → ROM
- ROM must boot AND play correctly in mGBA: paddle moves, ball bounces, scoring works, game over triggers
- Zero RPG references anywhere in generated Pong C output — no RPG types, constants, or symbols in any generated file
- Basic quality checks on generated C: no dead code, no duplicate definitions, reasonable function names

**Pretty-printer style:**
- Human-readable output: proper indentation, blank lines between functions, aligned braces
- Section comments in generated C (e.g., `// Scene: gameplay`, `// Actor: player`) to help developers trace DSL → C

### Claude's Discretion

- Whether C AST includes GBDK-specific nodes (CPragma, BANKED annotation) or keeps pure generic C with GBDK specifics handled in IR→AST translation
- Validation approach during migration: C output diff vs ROM-level testing vs hybrid
- IR-to-C-AST visitor architecture: monolithic vs domain-split visitors
- Pretty-printer internal architecture: single class vs composable printers
- Formatting configuration: fixed style vs configurable (indent size, brace style)

### Deferred Ideas (OUT OF SCOPE)

- Old GBDKCodeGenerator deletion — deferred to Phase 5 after all three example games validated through new pipeline
- Breakout and Explorer compilation through new pipeline — Phase 5
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CGEN-01 | C AST sealed hierarchy (CFunction, CStatement, CExpr, CType) lives in codegen module | Sealed interface pattern established in Phase 1 IR; same pattern applies to C AST in `gbkt-backend-gbdk/codegen/` |
| CGEN-02 | Bank assignment is a typed field on C AST nodes — no mutable `currentBank` state | Root cause of bank leak bugs is `var currentBank = 0` in GBDKCodeGenerator; typed field + inheritance at CFile/CFunction level eliminates this |
| CGEN-03 | Pretty-printer is the single place C strings are assembled; no `line("")` calls outside it | All `line()` calls currently scattered across 50+ extension files; must be pulled into a single CEmitter class |
| CGEN-04 | Old string-based `GBDKCodeGenerator` fully replaced and deleted | Per locked decision: deprecated in Phase 2, not deleted; deletion is Phase 5 |
| CGEN-05 | Domain visitors generate C AST per IR domain (scenes, actors, systems) | IR v2 has SceneIR, ActorIR, SystemIR, ScriptOp sealed hierarchies ready to be pattern-matched by visitors |
</phase_requirements>

---

## Summary

Phase 2 replaces the current string-based code generator (`GBDKCodeGenerator`) with a two-stage pipeline: IR v2 nodes → typed C AST nodes → pretty-printed C strings. The C AST is a sealed Kotlin data class hierarchy that mirrors C syntax closely. Bank assignment becomes a typed field on `CFile` and `CFunction` nodes, eliminating the `var currentBank = 0` mutable state that causes the bank-leak bugs documented in MEMORY.md and CONCERNS.md.

The existing codebase gives Phase 2 a clean starting point. Phase 1 delivered a complete IR v2 hierarchy (`GameIR`, `SceneIR`, `ActorIR`, `SystemIR`, `ScriptOp`, `Expr`) with zero external dependencies. The `PongV2.kt` example game already exists and compiles through the IR layer. The old `GBDKCodeGenerator` produces working output for complex games (LabyrinthOfTheDragon-port), which means it must not be touched during Phase 2 — the new pipeline runs alongside it and handles only the Pong use case.

The critical risk is translation fidelity: the IR v2 `ScriptOp` instruction set is intentionally simpler than what the old generator handles (24 ScriptOp types vs 80+ IR v1 statement types). Pong uses only a subset of ScriptOps (Assign, IfOp, MoveBy, SetPosition, NavigateTo, ShowDialog, PrintOp) which maps naturally to C. The new pipeline only needs to handle what Pong actually uses — it does not need to be a complete replacement on Day 1.

**Primary recommendation:** Build the C AST hierarchy first (CGEN-01), then the pretty-printer (CGEN-03), then domain visitors for ScriptOp → C AST (CGEN-05), then wire into the pipeline and validate Pong end-to-end (CGEN-04). This sequence ensures each layer is testable before the next one is built.

---

## Standard Stack

### Core

No new external libraries required. Phase 2 is pure Kotlin data modeling and string generation.

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| Kotlin 2.3.0 | 2.3.0 (pinned) | Sealed interfaces, data classes, `when` exhaustive matching | Already pinned in `gradle.properties`; sealed interfaces are the same pattern as IR v2 |
| Kotlin Test | bundled | Unit testing for C AST construction and pretty-printer output | Already in all test configurations; no new dependency |
| Kotest Property | existing `libs.kotest.property` | Property-based tests for pretty-printer formatting invariants | Already in `gradle/libs.versions.toml`; optional but useful |

### No New Dependencies

This phase adds zero new runtime or test dependencies. The C AST, visitors, and pretty-printer are pure Kotlin.

---

## Architecture Patterns

### Recommended File Structure

```
gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/
├── codegen/
│   ├── ast/                     # NEW: C AST sealed hierarchy
│   │   ├── CFile.kt             # Top-level file node (carries bank number)
│   │   ├── CFunction.kt         # Function definition (bank field inherits from CFile)
│   │   ├── CStatement.kt        # Sealed: CIf, CFor, CWhile, CBlock, CReturn, CRawCode, ...
│   │   ├── CExpr.kt             # Sealed: CLiteral, CVar, CBinary, CUnary, CCall, ...
│   │   ├── CType.kt             # Sealed: CU8, CU16, CI8, CI16, CVoid, CPointer, CArray
│   │   └── CDeclaration.kt      # Variable decl, typedef struct, #define
│   ├── emit/                    # NEW: Pretty-printer (single place C strings assembled)
│   │   └── CEmitter.kt          # CFile → String (indentation, blank lines, braces)
│   ├── visitor/                 # NEW: IR v2 → C AST domain visitors
│   │   ├── SceneVisitor.kt      # SceneIR → CFunction (enter/frame/exit)
│   │   ├── ActorVisitor.kt      # ActorIR → CVariable (OAM struct)
│   │   ├── ScriptOpVisitor.kt   # ScriptOp → CStatement (exhaustive when)
│   │   ├── ExprVisitor.kt       # Expr → CExpr (exhaustive when)
│   │   └── SystemVisitor.kt     # SystemIR → C (dialog, sound, camera init)
│   ├── pipeline/                # NEW: Orchestration
│   │   └── GBDKPipelineV2.kt    # GameIR → List<CFile> → Map<String, String>
│   ├── core/                    # EXISTING: old SceneCodegen, StatementCodegen, etc.
│   ├── GBDKCodeGenerator.kt     # EXISTING: deprecated after Phase 2
│   └── ...
```

### Pattern 1: Sealed C AST Hierarchy

**What:** A Kotlin sealed interface hierarchy that mirrors C language constructs. Nodes are immutable data classes.

**When to use:** This is the single representation of generated C code before string emission. Visitors build it; the emitter consumes it.

**Example:**

```kotlin
// In ast/CStatement.kt
sealed interface CStatement

data class CIf(
    val condition: CExpr,
    val thenBody: List<CStatement>,
    val elseBody: List<CStatement> = emptyList(),
) : CStatement

data class CFor(
    val init: CStatement?,
    val condition: CExpr,
    val increment: CExpr?,
    val body: List<CStatement>,
) : CStatement

data class CWhile(
    val condition: CExpr,
    val body: List<CStatement>,
) : CStatement

data class CReturn(val value: CExpr? = null) : CStatement

data class CExprStatement(val expr: CExpr) : CStatement

data class CVarDecl(
    val name: String,
    val type: CType,
    val initializer: CExpr? = null,
    val isStatic: Boolean = false,
    val isConst: Boolean = false,
) : CStatement

/** Escape hatch for GBDK-specific code or inline asm. */
data class CRawCode(val code: String) : CStatement
```

```kotlin
// In ast/CFunction.kt
data class CFunction(
    val name: String,
    val returnType: CType,
    val params: List<CParam> = emptyList(),
    val body: List<CStatement> = emptyList(),
    val bank: Int? = null,       // null = inherits from CFile
    val isBanked: Boolean = false, // adds BANKED keyword in GBDK
    val isStatic: Boolean = false,
    val sectionComment: String? = null, // e.g. "Scene: gameplay"
)
```

```kotlin
// In ast/CFile.kt
data class CFile(
    val name: String,               // e.g. "main.c", "bank1.c"
    val bank: Int = 0,              // ROM bank for this file
    val includes: List<String> = emptyList(),
    val defines: List<CDefine> = emptyList(),
    val typedefs: List<CTypedef> = emptyList(),
    val functions: List<CFunction> = emptyList(),
    val variables: List<CVarDecl> = emptyList(),
)
```

**Confidence:** HIGH — This is the same sealed interface + exhaustive `when` pattern already proven in Phase 1 IR v2.

### Pattern 2: Pretty-Printer (CEmitter)

**What:** A single class (or object) that converts `CFile` → `String`. This is the only place C strings are assembled. No `line()` calls anywhere else.

**When to use:** Call `CEmitter.emit(cFile)` at the end of the pipeline. The emitter is deterministic: same AST → same string.

**Example:**

```kotlin
// In emit/CEmitter.kt
object CEmitter {
    fun emit(file: CFile): String = buildString {
        appendLine("// Generated by gbkt for [game name]")
        appendLine("// Bank ${file.bank}")
        appendLine()
        file.includes.forEach { appendLine("#include $it") }
        appendLine()
        file.defines.forEach { appendLine(it.toC()) }
        appendLine()
        file.typedefs.forEach { appendLine(it.toC()) }
        appendLine()
        file.variables.forEach { appendLine(emitVarDecl(it)) }
        appendLine()
        file.functions.forEach { fn ->
            fn.sectionComment?.let { appendLine("// $it") }
            appendLine(emitFunction(fn))
            appendLine()
        }
    }

    fun emitStatement(stmt: CStatement, indent: Int): String = when (stmt) {
        is CIf -> emitIf(stmt, indent)
        is CFor -> emitFor(stmt, indent)
        is CWhile -> emitWhile(stmt, indent)
        is CReturn -> emitReturn(stmt)
        is CVarDecl -> emitVarDecl(stmt)
        is CExprStatement -> emitExprStatement(stmt)
        is CRawCode -> stmt.code     // Pass through verbatim
        // ... exhaustive, no else needed
    }
}
```

**Key constraint:** `CEmitter` is the ONLY class that calls `buildString {}` or `appendLine()` to produce C text. Domain visitors produce `CStatement` / `CExpr` nodes — never strings.

**Confidence:** HIGH — Standard compiler pattern. The emitter is the simplest layer.

### Pattern 3: Domain Visitors (IR v2 → C AST)

**What:** Functions or classes that transform IR v2 nodes into C AST nodes. One visitor per IR domain (scenes, actors, expressions, script ops).

**When to use:** The pipeline calls visitors in order to produce a `List<CFile>` from a `GameIR`.

**Example — ScriptOp visitor:**

```kotlin
// In visitor/ScriptOpVisitor.kt
object ScriptOpVisitor {
    fun emit(op: ScriptOp): CStatement = when (op) {
        is Assign -> CExprStatement(
            CBinaryExpr(CVar(op.target), op.op.toCBinaryOp(), ExprVisitor.emit(op.value))
        )
        is IfOp -> CIf(
            condition = ExprVisitor.emit(op.condition),
            thenBody = op.then.map { emit(it) },
            elseBody = op.otherwise.map { emit(it) },
        )
        is MoveBy -> CExprStatement(
            CCall("move_actor", listOf(CStringLiteral(op.actorId), ExprVisitor.emit(op.dx), ExprVisitor.emit(op.dy)))
        )
        is NavigateTo -> CExprStatement(
            CCall("navigate_to", listOf(CVar("SCENE_${op.sceneId.uppercase()}")))
        )
        is RawOp -> CRawCode(op.code)
        // ... exhaustive, no else needed
        else -> CRawCode("/* TODO: ${op::class.simpleName} */")  // Temporary during development
    }
}
```

**Note on `else`:** During development it is pragmatic to have an `else` fallback that emits a TODO comment. This should be removed once all ScriptOp types needed by Pong are handled. The sealed interface guarantees exhaustive matching when `else` is removed.

**Confidence:** HIGH — Phase 1 IR hierarchy is already built. Visitor pattern is standard and matches the domain structure.

### Pattern 4: Bank Assignment (Typed Field, Not Mutable State)

**What:** Bank number lives as an `Int` field on `CFile` (file-level bank) and optionally on `CFunction` (override). The emitter reads these fields to emit `#pragma bank N` and `BANKED` annotations correctly.

**Why:** The root cause of all GBDK bank bugs (documented in MEMORY.md) is `var currentBank = 0` in `GBDKCodeGenerator`. This mutable state leaks across function calls. A typed field eliminates the leak by design.

**Example:**

```kotlin
// Pipeline allocates banks up-front:
val homeFile = CFile(name = "main.c", bank = 0, ...)
val sceneFile = CFile(name = "bank1.c", bank = 1, ...)

// Functions in sceneFile inherit bank 1, no state tracking needed:
val enterFn = CFunction(
    name = "title_enter",
    returnType = CVoid,
    body = listOf(...),
    isBanked = true,   // Adds BANKED keyword
    // bank = null → inherits from containing CFile (bank 1)
)

// Emitter reads CFile.bank to emit #pragma bank, reads CFunction.isBanked for BANKED keyword
// No mutable state anywhere in the pipeline
```

**Confidence:** HIGH — This is a direct structural fix for the documented root cause.

### Pattern 5: Pipeline Orchestration

**What:** A single entry point (`GBDKPipelineV2`) that takes a `GameIR` and returns `Map<String, String>` (filename → C content). It replaces `GBDKBackend.generate()` for the Pong use case.

**Example:**

```kotlin
// In pipeline/GBDKPipelineV2.kt
class GBDKPipelineV2 {
    fun generate(gameIR: GameIR): Map<String, String> {
        // 1. Produce C AST from IR
        val cFiles = buildCFiles(gameIR)

        // 2. Pretty-print each file
        return cFiles.associate { cFile ->
            cFile.name to CEmitter.emit(cFile)
        }
    }

    private fun buildCFiles(gameIR: GameIR): List<CFile> {
        val homeFile = buildHomeFile(gameIR)       // main.c (bank 0)
        val sceneFile = buildSceneFile(gameIR)     // bank1.c (bank 1, scene functions)
        return listOf(homeFile, sceneFile)
    }
}
```

**Wiring into GBDKBackend:**

```kotlin
// In GBDKBackend.kt — Phase 2 change
override fun generate(game: Game, options: GenerationOptions): GenerationResult {
    // Detect if this game has a GameIR (v2 DSL) or old Game (v1 DSL)
    val gameIR: GameIR? = game.gameIR  // new nullable field, or passed separately
    return if (gameIR != null) {
        // New pipeline for v2 DSL games
        val files = GBDKPipelineV2().generate(gameIR)
        GenerationResult(success = true, files = files.toGeneratedFiles())
    } else {
        // Fallback to old generator for v1 DSL games
        val generator = GBDKCodeGenerator(game)
        val files = generator.generateMultiFile()
        GenerationResult(success = true, files = files.toGeneratedFiles())
    }
}
```

**Alternative wiring:** Since Pong V2 is the only game using the new pipeline, Phase 2 can also wire the new pipeline directly through a new Gradle task or test entry point, without touching `GBDKBackend`. This avoids modifying the stable v1 path. Decision: Claude's discretion.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| C syntax validation | Custom C parser | None — generate known-correct C | The AST represents exactly the C we want to emit; no validation needed if visitors are correct |
| Bank overflow detection | Custom size tracker | Assert in CEmitter that bank sizes are reasonable | Full bin-packing is Phase 4 (ANLZ-02); Phase 2 just needs correct bank field assignment |
| C code formatting style | Configurable formatter | Fixed style in CEmitter | Pong is simple; over-engineering the emitter adds risk. Fixed style now, refactor if needed |
| Complex expression precedence | Precedence table walker | Simple parenthesization rules | `ExpressionCodegen.kt` already implements correct precedence for GB arithmetic; port the logic |

**Key insight:** The C AST is not a general-purpose C compiler. It represents exactly the subset of C that gbkt generates for GBDK. This keeps the design simple and the scope achievable.

---

## Common Pitfalls

### Pitfall 1: Scope Creep — Handling All ScriptOps Before Pong Works

**What goes wrong:** The visitor tries to handle all 24 ScriptOp types before Pong's 8 ScriptOps work end-to-end.

**Why it happens:** Exhaustive `when` matching with no `else` forces you to handle all cases, which feels like you must implement everything before you can test anything.

**How to avoid:** Start with an `else -> CRawCode("/* TODO: ${op::class.simpleName} */")` fallback during development. Enable exhaustive matching (remove `else`) only for the ScriptOp types actually used by Pong. Test Pong ROM first, then expand.

**Warning signs:** You're implementing `ShowDialog` or `CameraOp` visitor logic before Pong's ball movement works.

### Pitfall 2: CEmitter Leaking Into Visitors

**What goes wrong:** A visitor produces a string instead of a CStatement: `line("if (${condition}) {")`.

**Why it happens:** The old code is full of `line()` calls; the pattern is familiar and hard to break.

**How to avoid:** The CEmitter rule is absolute: `buildString {}` and `appendLine()` are only called in `CEmitter.kt`. All other code returns typed nodes. Code review: grep for `appendLine` outside `emit/`.

**Warning signs:** Any visitor file contains `buildString`, `appendLine`, or produces a `String` result.

### Pitfall 3: Bank Mutable State Re-introduced

**What goes wrong:** A visitor tracks a `var currentBank` variable to know which bank it's currently processing.

**Why it happens:** Old generator uses this pattern; it feels natural to port.

**How to avoid:** Bank assignment is an attribute on `CFile` (set at construction time, not incremented). The visitor for scenes receives the target bank number as a parameter and stamps it onto the `CFile` it produces. No mutable bank variable anywhere.

**Warning signs:** Any `var` named `bank`, `currentBank`, or `bankNum` outside of the CFile/CFunction constructor.

### Pitfall 4: CGEN-04 Misread — The Old Generator Must NOT Be Deleted in Phase 2

**What goes wrong:** Developer reads SC#5 as "delete GBDKCodeGenerator" and deletes it, breaking all v1 DSL games.

**Why it happens:** The ROADMAP.md still says "deleted" in SC#5; the CONTEXT.md locked decision changed this to "deprecated and unused; deleted in Phase 5."

**How to avoid:** The CONTEXT.md decision is authoritative. Mark `GBDKCodeGenerator` with `@Deprecated(...)` after Pong works through the new pipeline. Do not delete any files. The old generator is the safety net for Breakout and Explorer until Phase 5.

**Warning signs:** Any `git rm` on `GBDKCodeGenerator.kt` or its extension files during Phase 2.

### Pitfall 5: Pong V2 IR Has No Asset Data Yet

**What goes wrong:** The new pipeline tries to generate sprite tile loading code for `paddle.png` and `ball.png`, but these assets don't exist as compiled tile data in the new pipeline.

**Why it happens:** Pong V2 uses `asset("sprites/paddle.png")` in ActorIR, but the asset pipeline (Phase 3) doesn't exist yet.

**How to avoid:** The Pong ROM validation for Phase 2 means the game boots and the game logic (input, ball physics, scoring) works correctly. Sprite rendering with actual PNG assets may require placeholder tile data or the assets must be provided manually. Agree on what "boots and plays correctly" means for Phase 2 without a full asset pipeline. Options: (a) Use CRawCode to embed minimal hardcoded tile data for paddles/ball, (b) Generate empty/stub sprite loading code and validate logic only, (c) Provide pre-processed tile arrays as test fixtures.

**Warning signs:** Blocking Pong ROM compilation on "but we need real PNG processing."

### Pitfall 6: GBDK BANKED Calling Convention Bugs

**What goes wrong:** Functions in non-zero banks don't have the `BANKED` keyword, causing linker errors ("MBC5 unknown address/value").

**Why it happens:** Documented in MEMORY.md. The old generator added BANKED via regex post-processing (`processBankedLine()`). The new pipeline must emit BANKED at AST construction time.

**How to avoid:** `CFunction.isBanked = true` when the function lives in bank > 0. The emitter always appends `BANKED` to the function signature when `isBanked = true`. No post-processing needed.

**Warning signs:** ROM builds but crashes or freezes when switching scenes.

---

## Code Examples

### C AST Construction for a Scene Enter Function

```kotlin
// Source: codebase analysis of SceneCodegen.kt and PongV2.kt
// What Phase 2 SceneVisitor must produce for Pong's "title" enter handler:

val titleEnter = CFunction(
    name = "title_enter",
    returnType = CVoid,
    params = emptyList(),
    isBanked = true,          // Lives in scene bank (bank 1)
    sectionComment = "Scene: title",
    body = listOf(
        CExprStatement(CCall("hide_sprites", emptyList())),
        CExprStatement(CCall("clear_screen", emptyList())),
        CExprStatement(CCall("print_at", listOf(CStringLiteral("PONG"), CLiteral(6), CLiteral(4)))),
        CExprStatement(CCall("print_at", listOf(CStringLiteral("PRESS START"), CLiteral(3), CLiteral(10)))),
    )
)
```

Generated C output (from CEmitter):

```c
// Scene: title
void title_enter(void) BANKED {
    hide_sprites();
    clear_screen();
    print_at("PONG", 6, 4);
    print_at("PRESS START", 3, 10);
}
```

### ScriptOp Visitor for Assign

```kotlin
// Source: ScriptOp.kt (Phase 1) + old StatementCodegen.kt pattern
is Assign -> {
    val lhs = CVar(op.target)
    val rhs = ExprVisitor.emit(op.value)
    CExprStatement(
        when (op.op) {
            AssignOp.SET -> CAssign(lhs, rhs)
            AssignOp.ADD -> CCompoundAssign(lhs, "+", rhs)
            AssignOp.SUB -> CCompoundAssign(lhs, "-", rhs)
            AssignOp.MUL -> CCompoundAssign(lhs, "*", rhs)
            AssignOp.DIV -> CCompoundAssign(lhs, "/", rhs)
            // ...
        }
    )
}
```

### CEmitter Indentation

```kotlin
// Source: GBDKCodeGenerator.line() pattern — ported to emitter
private fun emitBody(stmts: List<CStatement>, indentLevel: Int): String = buildString {
    val pad = "    ".repeat(indentLevel)
    for (stmt in stmts) {
        appendLine("$pad${emitStatement(stmt, indentLevel)}")
    }
}

private fun emitIf(stmt: CIf, indentLevel: Int): String {
    val condition = emitExpr(stmt.condition)
    val thenPart = emitBlock(stmt.thenBody, indentLevel)
    return if (stmt.elseBody.isEmpty()) {
        "if ($condition) $thenPart"
    } else {
        "if ($condition) $thenPart else ${emitBlock(stmt.elseBody, indentLevel)}"
    }
}
```

### Pipeline Integration Test Pattern

```kotlin
// Test structure for Phase 2 — source: TESTING.md patterns
@Test
fun `Pong scene enum is generated with correct constants`() {
    val cFiles = GBDKPipelineV2().generate(pongV2)   // pongV2 from PongV2.kt
    val mainC = cFiles["main.c"] ?: fail("main.c not generated")

    assertTrue(mainC.contains("#define SCENE_TITLE"), "Should define SCENE_TITLE constant")
    assertTrue(mainC.contains("#define SCENE_GAME"), "Should define SCENE_GAME constant")
    assertTrue(mainC.contains("#define SCENE_GAMEOVER"), "Should define SCENE_GAMEOVER constant")
}

@Test
fun `Pong C output has no RPG symbol names`() {
    val cFiles = GBDKPipelineV2().generate(pongV2)
    val allContent = cFiles.values.joinToString("\n")

    assertFalse(allContent.contains("_party_size"), "No RPG party_size reference")
    assertFalse(allContent.contains("_combatant"), "No RPG combatant reference")
    assertFalse(allContent.contains("STATUS_EFFECT"), "No RPG status effect reference")
    assertFalse(allContent.contains("COMBAT_STATE"), "No RPG combat state reference")
}

@Test
fun `Pong enter functions are placed in bank 1`() {
    val cFiles = GBDKPipelineV2().generate(pongV2)
    val bank1C = cFiles["bank1.c"] ?: fail("bank1.c not generated")

    assertTrue(bank1C.contains("#pragma bank 1"), "Scene bank file has bank 1 pragma")
    assertTrue(bank1C.contains("void title_enter"), "title_enter in bank 1")
    assertTrue(bank1C.contains("BANKED"), "Functions are BANKED")
}
```

---

## Existing Code to Port

### From ExpressionCodegen.kt

The existing `generateExpr()` function (in `gbkt-backend-gbdk/codegen/core/ExpressionCodegen.kt`) handles expression precedence and constant folding. The Phase 2 `ExprVisitor` for IR v2 `Expr` types can port the core logic:

- `foldConstants()` — constant folding for binary expressions with two literals
- `precedence()` — C operator precedence for correct parenthesization
- `needsParens()` — determines when to add `()` around sub-expressions

The key difference: the old function takes `IRExpression` (IR v1 types); the new one takes `Expr` (IR v2 types). The mapping is straightforward since IR v2 `BinaryOp` mirrors IR v1's operators.

### From SceneCodegen.kt

The scene function naming convention (`${name}_enter`, `${name}_frame`, `${name}_exit`) and the scene enum pattern (`SCENE_${name.uppercase()}`) must be preserved exactly. GBDK's C linking depends on these names.

### From GBDKCodeGenerator — Bank Assignment

The old bank assignment properties (`codeBankScene`, `codeBankBattle`, etc.) should be replaced by a `BankConfig` data class passed to the pipeline. For Pong, only two banks matter: bank 0 (home, main.c) and bank 1 (scene functions, bank1.c).

---

## Discretion Recommendations

### C AST: Include GBDK-Specific Nodes

Recommendation: Include GBDK-specific nodes in the AST rather than handling them in IR→AST translation.

**Rationale:** `CPragma(bank: Int)` and `CFunction.isBanked: Boolean` are simple fields that belong in the AST since the emitter needs them to produce correct output. Trying to handle GBDK pragmas at the visitor layer (IR→AST) would force visitors to produce mixed string/AST output, which violates the single-place-for-strings rule.

**Proposed GBDK nodes:**
- `CFile.bank: Int` — emitted as `#pragma bank N` at top of file
- `CFunction.isBanked: Boolean` — emitted as `BANKED` keyword in function signature
- `CRawCode(code: String)` — already in locked decisions; handles `SWITCH_ROM()`, inline asm, etc.

### Validation Approach During Migration

Recommendation: Hybrid approach — C output assertions first, ROM-level test as final gate.

**Phase 1 of validation (fast feedback):** Unit tests assert that `GBDKPipelineV2().generate(pongV2)` produces correct C patterns (scene enum, function names, bank pragmas, no RPG symbols). These run in milliseconds with `./gradlew :gbkt-backend-gbdk:test`.

**Phase 2 of validation (ROM gate):** Manual `./gradlew :gbkt-examples:pong:buildRom` compiles with GBDK and runs in mGBA. This is the final success criterion for Phase 2. Human verification: paddle moves, ball bounces, scoring works, game over triggers.

No C output diff against the old generator — the old generator produces different (more complex) code because it handles the full v1 DSL game. The new pipeline produces clean, minimal C for the v2 DSL game.

### IR-to-C-AST Visitor Architecture: Domain-Split

Recommendation: Domain-split visitors (one per IR domain) rather than a single monolithic visitor.

**Rationale:** Phase 1 CONCERNS.md documents that `StatementCodegen.kt` (1,402 LOC) and `MonsterCodegen.kt` (1,705 LOC) are already unwieldy in the v1 generator. Domain-split visitors keep each file under 300 lines for Pong-scope work.

**Visitor files for Phase 2 (Pong scope):**
- `ScriptOpVisitor.kt` — handles all `ScriptOp` subtypes used by Pong
- `ExprVisitor.kt` — handles all `Expr` subtypes
- `SceneVisitor.kt` — maps `SceneIR` to `List<CFunction>` (enter/frame/exit)
- `ActorVisitor.kt` — maps `ActorIR` to variable/struct declarations

**Visitor files deferred to Phase 5 (Breakout/Explorer):**
- `SystemVisitor.kt` — handles `SystemIR` subtypes (dialog, sound, save, camera, exploration)
- RPG domain visitors — handles `GenericSystem` with `type=simple_battle`

### Pretty-Printer Architecture: Single Object

Recommendation: Single `CEmitter` object (no composable printers for Phase 2).

**Rationale:** Pong is simple. A single object with private helper functions for each AST node type is easier to test and modify than a composable system. If Phase 5 reveals the emitter needs extension points, refactor then.

### Formatting Configuration: Fixed Style

Recommendation: Fixed style with 4-space indentation and K&R braces (`{` on same line).

**Rationale:** Matches the existing generated C style in the old generator. Consistent with GBDK example code. Zero configuration surface area means zero bugs from configuration edge cases.

---

## Open Questions

1. **Asset placeholder for Pong ROM validation**
   - What we know: Pong V2 references `asset("sprites/paddle.png")` and `asset("sprites/ball.png")` in `ActorIR`. Phase 3 provides the asset pipeline. Phase 2 must produce a bootable ROM without Phase 3.
   - What's unclear: How to handle sprite tile data in the new pipeline during Phase 2. Options: (a) Hardcode minimal tile arrays as `CRawCode`, (b) Skip sprite loading and test logic only, (c) Provide pre-generated tile data as test fixtures.
   - Recommendation: Scope the Phase 2 ROM validation to game logic (input, movement, scoring, scene transitions). Sprite tiles can be minimal placeholder patterns. Defer actual PNG-to-tile conversion to Phase 3.

2. **GBDKBackend wiring: modify existing or add new entry point?**
   - What we know: `GBDKBackend.generate()` currently instantiates `GBDKCodeGenerator`. Phase 2 needs the new pipeline to run for Pong V2.
   - What's unclear: Whether to detect v2 games inside `GBDKBackend.generate()` (requires passing `GameIR` through) or to create a separate Gradle task that bypasses `GBDKBackend` for v2 games.
   - Recommendation: Determine at plan time. The simplest approach is a new `generateV2` method on `GBDKBackend` that takes `GameIR` directly, called by a new `generateCFromIR` Gradle task. This avoids modifying the v1 code path.

3. **Roadmap update: SC#5 "deleted" → "deprecated"**
   - What we know: The CONTEXT.md locked decision changes Phase 2 SC#5 from "GBDKCodeGenerator deleted" to "deprecated, bypassed by new pipeline; deleted in Phase 5."
   - What's unclear: Whether the planner or the executor should update ROADMAP.md to reflect this.
   - Recommendation: The planner should include a ROADMAP.md update as an explicit task in Phase 2 Plan 01 or Plan 04.

---

## Phase 2 Scope for Pong

Pong V2 uses these ScriptOp types (from PongV2.kt analysis):

| ScriptOp | Pong Usage | C Output Pattern |
|----------|-----------|-----------------|
| Assign | `assign("ballDx", literal(1))` | `ballDx = 1u;` |
| IfOp | `whenever(condition) { ... }` | `if (condition) { ... }` |
| MoveBy | `moveBy(paddle1, 0, -2)` | `move_actor_by("paddle1", 0, -2);` |
| SetPosition | `setPosition(ball.id, 80, 72)` | `set_actor_pos("ball", 80u, 72u);` |
| NavigateTo | `navigate("game")` | `navigate_to(SCENE_GAME);` |
| PrintOp | `print("PONG", PositionDef(6, 4))` | `print_at("PONG", 6, 4);` |
| FadeOp | `hideSprites()` / `showSprites()` | `hide_sprites();` / `show_sprites();` |

Expr types used:

| Expr | Pong Usage | C Output |
|------|-----------|---------|
| Literal | `literal(1)`, `literal(80)` | `1u` / `80u` |
| VarRef | `varRef("ball.x")` | `_ball_x` |
| BinaryExpr | `varRef("ball.x") + varRef("ballDx")` | `_ball_x + ballDx` |
| CallExpr | `buttonPressed("start")` | `_joypad_just_pressed(J_START)` |

This is a small, well-bounded subset. The visitor only needs to handle these types to produce a working Pong ROM.

---

## Sources

### Primary (HIGH confidence)

- Codebase: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt` — existing bank state, `line()` pattern, `generate()` orchestration
- Codebase: `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/` — all 10 IR v2 files; sealed hierarchies ready for visitors
- Codebase: `gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/PongV2.kt` — exact ScriptOps used by Pong
- Codebase: `.planning/phases/01-ir-foundation-and-dsl/01-VERIFICATION.md` — Phase 1 artifacts confirmed present and working
- Codebase: `.planning/REQUIREMENTS.md` — CGEN-01 through CGEN-05 definitions
- Codebase: `.planning/codebase/CONCERNS.md` — bank mutable state documented as fragile area
- Codebase: `MEMORY.md` — GBDK BANKED calling convention bug pattern

### Secondary (MEDIUM confidence)

- `.planning/ROADMAP.md` — Phase 2 plan structure (SC#5 overridden by CONTEXT.md decision)
- `.planning/codebase/TESTING.md` — test patterns for codegen verification
- `.planning/codebase/ARCHITECTURE.md` — IR → C pipeline data flow

---

## Metadata

**Confidence breakdown:**
- C AST design: HIGH — sealed interface pattern proven in Phase 1; data class hierarchy is standard
- Bank typed field: HIGH — root cause of bugs is documented; typed field is the structural fix
- Pretty-printer: HIGH — trivial string assembly; only risk is discipline (no strings outside emitter)
- Domain visitors: HIGH — Phase 1 IR types are stable; Pong uses only 7 ScriptOp types
- Pong ROM validation: MEDIUM — ROM compilation requires GBDK and mGBA; human verification needed
- Asset placeholder strategy: MEDIUM — depends on decision made at plan time (open question #1)

**Research date:** 2026-02-18
**Valid until:** 2026-03-18 (stable domain; no fast-moving external dependencies)
