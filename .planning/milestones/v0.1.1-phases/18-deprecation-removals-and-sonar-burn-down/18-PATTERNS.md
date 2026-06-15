# Phase 18: Deprecation Removals and Sonar Burn-down — Pattern Map

**Mapped:** 2026-06-13
**Files analyzed:** 12 new/modified files (1 new: CHANGELOG.md; 11 modified)
**Analogs found:** 11 / 12 (CHANGELOG.md has no in-tree analog — use Keep a Changelog spec)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `gbkt-lang/.../dsl/ScriptBuilder.kt` | DSL builder | transform | self (runIf/unless/orElse at lines 226-242) | self-referential |
| `gbkt-lang/.../dsl/ActorPoolBuilder.kt` | DSL builder | transform | `ScriptBuilder.kt` whenever/runIf pattern | role-match |
| `gbkt-lang/.../dsl/VariableBuilders.kt` | DSL builder | transform | `ScriptBuilder.kt` | role-match |
| `gbkt-lang/.../dsl/ExprBuilder.kt` | DSL builder | transform | `ScriptBuilder.kt` | role-match |
| `gbkt-genre-rpg/.../dsl/RpgExtensions.kt` | DSL extension | transform | `ScriptBuilder.kt` whenever removal | role-match |
| `gbkt-genre-rpg/.../dsl/CombatStatesTest.kt` | test | request-response | any gbkt-genre-rpg test with typed DSL | role-match |
| `gbkt-core/.../constraints/TargetProfiles.kt` | config | transform | self (GAME_BOY_SCREEN already has bitsPerPixel=2) | self-referential |
| `gbkt-backend-gbdk/.../codegen/visitor/**` (29 S3776 EMITTING) | codegen visitor | transform | `GBDKPipeline.kt` existing extracted helpers | exact |
| `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipeline.kt` (10 S3776 EMITTING) | pipeline | transform | `GBDKPipeline.kt` existing `buildRpgCharStatVars`/`buildRpgCombatHelperVars` | exact |
| `CONTRIBUTING.md` | documentation | — | existing `## DSL Authoring Guidelines` section | role-match |
| `CHANGELOG.md` (new) | documentation | — | none in-tree | no analog |
| stale-string files (4 locations) | config/comment | — | `CONTRIBUTING.md` code examples | partial |

---

## Pattern Assignments

### Track A: `whenever` → `runIf` migration

#### Target: `ScriptBuilder.kt` — remove `whenever(Expr, block)` (line 209)

**Survivor pattern** (`ScriptBuilder.kt` lines 226–242 — the idiom `whenever` callers migrate TO):

```kotlin
// gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt, lines 226-242

fun runIf(condition: Expr, block: ScriptBuilder.() -> Unit) = ifOp(condition, block)

fun unless(condition: Expr, block: ScriptBuilder.() -> Unit) =
    ifOp(UnaryExpr(UnaryOp.LOGICAL_NOT, condition), block)

/**
 * Else branch chained to the most recent [runIf]. Delegates to [elseOp].
 *
 * Must immediately follow [runIf] or [ifOp].
 */
fun orElse(block: ScriptBuilder.() -> Unit) = elseOp(block)
```

**Function to DELETE** (`ScriptBuilder.kt` lines 195–214 — the outgoing pattern):

```kotlin
// gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt, lines 209-214
fun whenever(condition: Expr, block: ScriptBuilder.() -> Unit) {
    val loc = captureV2Location()
    val bodyBuilder = ScriptBuilder()
    ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }
    ops += IfOp(condition, bodyBuilder.build(), emptyList(), sourceLocation = loc)
}
```

**KDoc cross-reference fix required:** Lines 195-208 mention `[whenever]` and forward-reference SEED-023. Replace entire KDoc block on `runIf` to remove `[whenever]` references.

---

#### Target: `ActorPoolBuilder.kt` — rename `whenever(PoolPoolCollisionExpr, block)` to `runIf`

**Existing overload to rename** (`ActorPoolBuilder.kt` lines 396–414 — copy signature and body verbatim, change only the function name):

```kotlin
// gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorPoolBuilder.kt, lines 396-414
fun ScriptBuilder.whenever(
    collision: PoolPoolCollisionExpr,
    block: ScriptBuilder.(PoolIterator, PoolIterator) -> Unit,
) {
    val loc = captureV2Location()
    val iterA = PoolIterator("pool_${collision.poolA.poolId.take(1)}i")
    val iterB = PoolIterator("pool_${collision.poolB.poolId.take(1)}i")
    val bodyBuilder = ScriptBuilder()
    ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block(iterA, iterB) }
    val condition =
        CallExpr(
            function = "collides",
            args = listOf(
                VarRef(collision.poolA.actorTemplateId),
                VarRef(collision.poolB.actorTemplateId),
            ),
        )
    // ...
}
// AFTER: rename `whenever` → `runIf`; body unchanged.
```

**Analog shape for the new overload** — use `runIf(Expr, block)` at `ScriptBuilder.kt:226` as the naming template:

```kotlin
// TARGET SHAPE for the renamed overload:
fun ScriptBuilder.runIf(
    collision: PoolPoolCollisionExpr,
    block: ScriptBuilder.(PoolIterator, PoolIterator) -> Unit,
) { /* same body */ }
```

---

#### Internal framework caller migration pattern

Three internal callers — all follow the same mechanical rename:

```
gbkt-lang/.../dsl/VariableBuilders.kt:193   sb.whenever(BinaryExpr(...))  →  sb.runIf(BinaryExpr(...))
gbkt-lang/.../dsl/ExprBuilder.kt:298        sb.whenever(BinaryExpr(...))  →  sb.runIf(BinaryExpr(...))
gbkt-lang/.../dsl/ExprBuilder.kt:301        sb.whenever(BinaryExpr(...))  →  sb.runIf(BinaryExpr(...))
```

No structural change — text-only rename. These are migration targets, not pattern sources.

---

### Track B: `combatIsInState(String)` removal

#### Target: `RpgExtensions.kt` — remove overload at line 440

**Block to DELETE entirely** (lines 440–444 approximately):

```kotlin
// gbkt-genre-rpg/.../dsl/RpgExtensions.kt:440 — REMOVE
@Deprecated(
    message = "Use combatIsInState(CombatStateId, BattleRef) to eliminate magic strings",
    replaceWith = ReplaceWith("combatIsInState(CombatStateId(stateId), BattleRef(battleId))"),
)
fun combatIsInState(stateId: String, battleId: String): Expr =
    combatIsInState(CombatStateId(stateId), BattleRef(battleId))
```

**Typed overload to KEEP** (line 419 — the surviving pattern callers migrate to):

```kotlin
// gbkt-genre-rpg/.../dsl/RpgExtensions.kt:419 — KEEP
fun combatIsInState(stateId: CombatStateId, battleId: BattleRef): Expr = ...
```

#### Target: `CombatStatesTest.kt` — migrate test at line 122

**BEFORE** (to remove):
```kotlin
// gbkt-genre-rpg/.../dsl/CombatStatesTest.kt:122
@Suppress("DEPRECATION") val stringExpr = combatIsInState("COMBAT_STATE_VICTORY", "combat")
```

**AFTER** — either delete the equivalence assertion (preferred — string overload no longer exists to compare against), or re-express as:
```kotlin
val typedExpr = combatIsInState(CombatStates.VICTORY, BattleRef("combat"))
// assert typedExpr behaves correctly via the typed overload
```

Also remove the `@Suppress("DEPRECATION")` annotation and any now-unused imports.

---

### Track C: Documentation and seed fixes

#### `CONTRIBUTING.md` — add deprecation-convention section

**Analog:** Existing `## DSL Authoring Guidelines` section at line 255 sets the style template — numbered rules, code examples, cross-references. The new deprecation section follows the same pattern.

**Insertion point:** Between line 423 (`---` after Organizing Large Games) and line 425 (`## Code Review Checklist`). The research (RESEARCH.md open question 3) confirms "between DSL Authoring Guidelines and Organizing Large Games" is also acceptable — planner may choose.

**Section content spec** (from RESEARCH.md § CONTRIBUTING.md Deprecation Convention Content Spec):
- H2 heading: `## API Deprecation Convention`
- Two-tier rule as a numbered list
- Worked examples citing SEED-023, SEED-025, SEED-028 (v0.1.1 hard removals)
- Must cross-reference root `CHANGELOG.md` as the canonical breaking-change record

**Style pattern** from CONTRIBUTING.md lines 255-314 (DSL Authoring Guidelines sub-section shape):
```markdown
## DSL Authoring Guidelines

### @GbktDsl Marker

All DSL builder classes **must** be annotated with `@GbktDsl`:

```kotlin
// code example
```

This prevents ...
```
New deprecation section should follow the same H2+H3+code-block+prose structure.

---

#### `CHANGELOG.md` — new file at project root

**No in-tree analog.** Use [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format:

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.1.1] - 2026-XX-XX

### Removed
- `whenever(condition, block)` / `whenever(collision, block)` — use `runIf` (SEED-023)
- `combatIsInState(String, String)` — use `combatIsInState(CombatStateId, BattleRef)` (SEED-025)

### Changed
- `config { ramBanks = N }` → `config { ramBanks(N) }` (SEED-028)
```

---

#### `TargetProfiles.kt` — SEED-027 bitsPerPixel fix (line 50)

**Analog within same file:** `GAME_BOY_SCREEN` (line 32) already uses `bitsPerPixel = 2`. Copy its structure. Change `bitsPerPixel = 4` → `bitsPerPixel = 2` and update the KDoc prose from "4 bits per pixel" to "2 bits per pixel, color via 8 hardware palettes". Narrow the "All backends MUST derive" KDoc claim to `width` and `height` only.

---

#### Stale guidance strings (SEED-028) — 4 locations

Text-only fixes. Each is a comment or KDoc string containing the dead `config { ramBanks = N }` syntax. Find and replace:

| File | Location | Old | New |
|------|----------|-----|-----|
| `gbkt-gradle-plugin/.../GbktExtension.kt` | line 166 | `ramBanks = N` | `ramBanks(N)` |
| `gbkt-gradle-plugin/.../tasks/CompileRomTask.kt` | line 319 | `ramBanks = N` | `ramBanks(N)` |
| `gbkt-examples/platformer-template/.../PlatformerTemplate.kt` | line 61 | `romBanks = 8` | `romBanks(8)` |
| `gbkt-examples/metasprites/.../MetaspriteEmissionTest.kt` | line 44 | `romBanks = 2` | `romBanks(2)` |

Do NOT touch `CartridgeConfig(romBanks = N, ramBanks = N)` named-argument constructor sites — those are valid Kotlin.

---

### Track D: NON-EMITTING S3776 extract-method pattern

All 17 non-emitting findings follow the same extract-method idiom. The closest analog is `GBDKPipeline.kt`'s own well-extracted helpers.

**Template: extract returning a value, not writing to shared mutable state**

`GBDKPipeline.kt` lines 3569–3578 (`buildRpgCharStatVars`) is the canonical example of a well-extracted helper:

```kotlin
// gbkt-backend-gbdk/.../pipeline/GBDKPipeline.kt, lines 3569-3578
private fun buildRpgCharStatVars(gameIR: GameIR): List<CVarDecl> {
    @Suppress("UNCHECKED_CAST")
    val characterSystems =
        gameIR.systems.filterIsInstance<GenericSystem>().filter {
            (it.config["type"] as? String) == "rpg_character_system"
        }
    if (characterSystems.isEmpty()) return emptyList()
    val visitor = RpgVisitor(gameIR)
    return characterSystems.flatMap { visitor.generateStatVarDecls(it) }
}
```

Key properties to replicate:
1. Returns a value (list/node); does NOT write to a mutable `vars` accumulator passed in as a parameter or captured from outer scope.
2. Early-return `emptyList()` when the subsystem is absent — avoids null checks in the caller.
3. Focused responsibility: one subsystem per helper.
4. Named with the subsystem it owns (`buildRpgCharStatVars`, `buildCameraGlobalVars`, `buildSaveGlobalVars`, etc.).

**`buildSystemGlobalVars` extract-method target** (E-03, cc=71) — the caller at `GBDKPipeline.kt:4195` accumulates into a mutable list via `when(system)` branches. Post-refactor shape:

```kotlin
// AFTER (target shape):
private fun buildSystemGlobalVars(gameIR: GameIR): List<CVarDecl> =
    buildCameraGlobalVars(gameIR) +
    buildExplorationGlobalVars(gameIR) +
    buildSaveGlobalVars(gameIR) +
    buildRpgGlobalVars(gameIR) +
    // ... one line per system
    genreVisitors.flatMap { it.buildGlobalVars(gameIR) }

private fun buildCameraGlobalVars(gameIR: GameIR): List<CVarDecl> {
    if (gameIR.systems.none { it is CameraSystem }) return emptyList()
    return listOf(
        CVarDecl(name = "_camera_x", type = CU8, initializer = CLiteral(0)),
        CVarDecl(name = "_camera_y", type = CU8, initializer = CLiteral(0)),
        // ... camera-specific vars
    )
}
```

---

### Track E: EMITTING S3776 extract-method pattern

Same extract-method idiom as Track D, but with the critical byte-identity constraint.

**Pitfall to avoid:** Extracted helpers MUST return values, not write to a shared mutable accumulator via side effects. If the original method does `vars.add(...)` in an order-sensitive loop, the extracted helper must return the chunk and the caller must concatenate in the same order.

**Template** — `GBDKPipeline.kt` `buildRpgCombatHelperVars` (lines 3610–3618):

```kotlin
// gbkt-backend-gbdk/.../pipeline/GBDKPipeline.kt, lines 3610-3618
private fun buildRpgCombatHelperVars(gameIR: GameIR): List<CVarDecl> {
    if (!hasRpgCombatAbilities(gameIR)) return emptyList()
    return listOf(
        CVarDecl("_char_active_sp", CU8, CLiteral(0)),
        CVarDecl("_combat_target_idx", CU8, CLiteral(0)),
        // ...
    )
}
```

**For visitor extract-method** — `ActorVisitor.generateMovementFunction` (lines 438–465) shows the pattern for a per-subsystem function that builds a `when`-dispatch into `statements += CIf(...)`:

```kotlin
// gbkt-backend-gbdk/.../visitor/ActorVisitor.kt, lines 438-464 (abbreviated)
fun generateMovementFunction(actor: ActorIR): List<CFunction> {
    val config = actor.movementConfig ?: return emptyList()
    val statements = mutableListOf<CStatement>()
    when (config.style) {
        MovementStyle.GRID -> {
            statements += CIf(condition = CBinaryExpr(...), thenBody = listOf(...))
            // ...
        }
        MovementStyle.PHYSICS -> { ... }
    }
    return listOf(CFunction(..., body = statements))
}
```

Extract-method shape for visitor findings: each logical "block" of the `when` branch (or each lifecycle section) becomes a `private fun buildXxxStatements(actor/gameIR): List<CStatement>` returning a list that the caller appends.

---

### NOSONAR pattern — no existing in-tree precedent

**Verified:** No `// NOSONAR`, `@SuppressWarnings`, or `@Suppress("CognitiveComplexity")` pattern exists anywhere in the codebase (grep found zero matches).

**Proposed format** (D-05: inline rationale + tracked v0.2.0 seed):

```kotlin
// NOSONAR: flat sealed-type dispatch — every CStatement branch is 1-depth emission with
// no nesting; extraction would scatter logically atomic emission patterns across 20+ helpers
// without reducing cognitive burden on readers. Tracked: SEED-XXX-cemitter-extract (v0.2.0).
fun emit(file: CFile, collector: SourceMapCollector? = null): String { // NOSONAR
```

Convention: the `// NOSONAR` tag goes at the END of the function signature line (SonarCloud matches it to the reported line number, which is the function declaration line). The longer rationale comment goes on the line immediately BEFORE the function declaration.

---

## Shared Patterns

### DSL builder function removal shape

**Source:** `ScriptBuilder.kt` lines 209–214 (the `whenever` function being removed).
**Apply to:** DEPR-01 and DEPR-02 removals.

When removing a DSL function:
1. Delete the function and its KDoc block entirely (no `@Deprecated` shim per D-02).
2. Search for all KDoc cross-references (`[whenever]`, `[combatIsInState]`) in the same file and adjacent files — update them to point at the replacement.
3. Run `./gradlew build` as the compilation gate — any missed call site becomes a compile error.

### Builder context idiom (for the new `runIf` overload in ActorPoolBuilder)

**Source:** `ScriptBuilder.kt` lines 209–213, `ActorPoolBuilder.kt` lines 396–414.

All `ScriptBuilder` block-capturing overloads use the same three-line idiom:
```kotlin
val bodyBuilder = ScriptBuilder()
ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block(/* params */) }
ops += IfOp(condition, bodyBuilder.build(), emptyList(), sourceLocation = loc)
```
The renamed `runIf(PoolPoolCollisionExpr, block)` overload preserves this idiom exactly.

### Extract-method return-value rule

**Source:** `GBDKPipeline.kt` lines 3569–3578, 3610–3618.
**Apply to:** All 46 S3776 extract-method refactors.

Extracted helpers always return their contribution as a value (`List<CVarDecl>`, `List<CStatement>`, `List<CFunction>`, etc.). They never mutate a shared mutable list via a captured reference. Caller assembles via `+` concatenation or `+=` on its own local accumulator after the call.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `CHANGELOG.md` (new root file) | documentation | — | No existing changelog in tree; use Keep a Changelog format per D-09 |

---

## Metadata

**Analog search scope:** `gbkt-lang/`, `gbkt-backend-gbdk/`, `gbkt-genre-rpg/`, `CONTRIBUTING.md`, root directory
**Files scanned:** 8 source files + directory listings
**Pattern extraction date:** 2026-06-13
