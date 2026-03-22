# Phase 1: IR Foundation and DSL - Research

**Researched:** 2026-02-17
**Domain:** Kotlin sealed IR hierarchy design, Compose-inspired DSL recording, multi-module BOM architecture
**Confidence:** HIGH (based on direct codebase analysis + Kotlin language specifications)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **DSL syntax direction:** Clean break from existing DSL syntax. No backward compatibility. Fresh design.
- **Declarative, state-driven** style inspired by Jetpack Compose. Describe what the game IS, not what it DOES.
- **Pure builder functions** (`scene {}`, `entity {}`, `game {}`). No annotation processing or compiler plugins.
- **Explicit composition** of small, testable pieces. No convention-based file discovery or black-box magic.
- **Declarative navigation** with routes for scene transitions (like Compose Navigation). Framework manages scene stack.
- **High-level abstractions by default** (entities, images, movement) with escape hatches for direct hardware access.
- **Pong (beginner):** Two paddles, ball, score, win condition + title screen, game-over screen, sound effects, 2-player.
- **Breakout (intermediate):** Brick grid (many entities), power-ups, level progression. Tests entity management at scale.
- **Explorer (advanced):** Simple dungeon crawler with grid movement, multiple floors, keys/doors, torch gauge + simple turn-based combat (attack/defend/item menu, HP/ATK/DEF stats). No deep RPG.
- **BOM (Bill of Materials) approach.** Developers select packages: core (DSL+IR), genre packages (rpg, platformer, puzzle), and backend (gameboy).
- **Phase 1 creates both core IR and RPG genre package.** Explorer uses the RPG package for simple turn-based combat.
- **Each example game is a separate Gradle module** with explicit package dependencies.
- **LabyrinthOfTheDragon kept as reference** (uses old pipeline). Not migrated or deleted in Phase 1.
- **Two-layer error checking:** DSL recording time (immediate) + IR validation pass (cross-cutting).
- **Fail fast:** Stop at first error.
- **Compiler-style error format:** `file:line:col: error: message`.
- **"Did you mean?" suggestions** included from the start.

### Claude's Discretion
- State management pattern for game variables (Compose-like `state()` vs property delegates vs other)
- Combat boundary between core collision and genre-specific combat systems
- Whether Pong/Breakout use only core IR or can pull genre packages if it makes code cleaner
- DSL extensibility mechanism for genre packages (full DSL extension vs IR + utilities)
- Sealed type constraint resolution for multi-module IR (extension mechanism design)

### Deferred Ideas (OUT OF SCOPE)
- Platformer genre package
- Puzzle genre package
- LabyrinthOfTheDragon migration to new pipeline
- IDE support for DSL (IntelliJ plugin)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| IR-01 | Sealed IR hierarchy represents all game-domain concepts (scenes, actors, sprites, tilemaps, scripts, systems, state) | Existing CoreIR.kt pattern is proven; new hierarchy adds `SceneIR`, `ActorIR`, `SystemIR` wrappers; sealed constraint documented |
| IR-02 | Platform annotations are nullable fields (bank slot, VRAM range, OAM slot) — null until analysis fills them | Kotlin nullable fields with default `null`; no external dependency needed |
| IR-03 | ScriptOp sealed instruction set covers movement, dialog, branching, state mutation, battle triggers, math | Existing IRStatement subtypes demonstrate the pattern; ScriptOp is a new named sealed hierarchy |
| IR-04 | IR module compiles independently with zero external dependencies | Existing gbkt-core only has `json` + test deps; IR module is already essentially dependency-free internally |
| DSL-01 | Kotlin DSL builders produce valid IR for all game constructs | Existing builder pattern (RecordingContext + property delegates) is the proven approach |
| DSL-02 | `ref()` provides typed, compile-time-validated references | Not yet implemented; design pattern researched below |
| DSL-03 | `asset()` references raw files for pipeline processing | Not yet implemented; design pattern researched below |
| DSL-04 | Pong, Breakout, and Explorer example games defined in new DSL from Phase 1 | Existing examples provide strong baseline; new DSL must cover all their constructs |
</phase_requirements>

---

## Summary

The existing codebase contains a working IR + DSL implementation that this phase redesigns with a clean break. The current system (in `gbkt-core`) already demonstrates the core recording pattern, sealed IR hierarchy, and builder approach — but uses an imperative style (`whenever(condition) { action }`). The new DSL must shift toward a declarative, Compose-style description while preserving the underlying recording context machinery that has proven correct.

The most significant design decisions are (1) how the new `GameDef`, `SceneDef`, `ActorDef` hierarchy maps to IR nodes, (2) how `ref()` validates cross-file references at DSL-recording time versus IR-validation time, and (3) how the genre-package boundary works given Kotlin's sealed-interface constraint (all sealed subtypes must live in the same module).

The sealed constraint is the architectural linchpin: `ScriptOp`, `ActorIR`, `SceneIR`, and `SystemIR` must all live in `gbkt-core`. The RPG genre package (`gbkt-rpg`) cannot add new sealed subtypes to `IRStatement` or `IRExpression` from a different module. The solution is that RPG-specific data lives in RPG domain objects (not as IR subtype extensions), and RPG-specific script operations are added as subtypes of a new `RPGScriptOp` sealed class that itself becomes a subtype registered within `gbkt-core`'s sealed hierarchy. Alternatively, the cleaner split is: `gbkt-core` holds all sealed IR, `gbkt-rpg` holds only DSL builder extensions and domain data structures that produce core IR.

**Primary recommendation:** The new DSL recording machinery and IR hierarchy live entirely in `gbkt-core`. Genre packages provide domain-specific DSL extension functions and domain data structures. All sealed IR subtypes (including RPG battle ops) remain in `gbkt-core` — the BOM separation is at the DSL API surface, not at the IR type level.

---

## Standard Stack

### Core (what already exists and continues to be used)

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| Kotlin 2.3.0 | 2.3.0 | Language for DSL + IR | Already set; sealed interface support is complete |
| Gradle 9.0 | 9.0 | Build system | Already set |
| JVM 21 | 21 | Runtime target | Already set |
| kotest-property | from libs.versions | Property-based testing for IR invariants | Already in test deps |

### Module Structure (what changes)

| Module | Role | What's New |
|--------|------|-----------|
| `gbkt-core` | All sealed IR + core DSL recording | Redesigned IR hierarchy; new DSL surface |
| `gbkt-rpg` (new) | RPG genre DSL extensions + domain data | New module; wraps core IR in RPG builders |
| `gbkt-backend-api` | Backend contract | No change |
| `gbkt-backend-gbdk` | GB/GBC codegen | No change in Phase 1 |
| `gbkt-bom` | BOM coordinator | Add `gbkt-rpg` to BOM |
| `gbkt-examples/pong` | Pong example | Rewritten with new DSL |
| `gbkt-examples/breakout` | Breakout example | Rewritten with new DSL |
| `gbkt-examples/explorer` | Explorer example | Rewritten with new DSL + gbkt-rpg |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| sealed interface in gbkt-core for all IR | abstract class + visitor | Loses exhaustive `when`; visitor is more boilerplate, less ergonomic |
| property delegates for variables | `state()` function ala Compose | Property delegates already proven; `state()` requires more runtime machinery |
| Thread-local RecordingContext | coroutine context element | ThreadLocal is simpler, already proven; coroutine approach adds complexity for no benefit in single-threaded DSL |
| Kotlin DSL without compiler plugins | Kotlin compiler plugin for DSL | Pure DSL (no plugin) is the locked decision; plugin would enable better IDE errors but is out of scope |

---

## Architecture Patterns

### Recommended IR Hierarchy Structure (New Design)

The new IR hierarchy introduces named intermediate types for better exhaustive matching across domains:

```
gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/
├── core/
│   ├── GameIR.kt        # Top-level game description (data class, not sealed)
│   ├── SceneIR.kt       # Scene: id, enter/frame/exit ScriptOp lists
│   ├── ActorIR.kt       # Actor: id, position, sprite, hitbox — platform annotations nullable
│   ├── SystemIR.kt      # System: id, per-frame update ops
│   └── ScriptOp.kt      # Sealed instruction set (replaces IRStatement)
├── expr/
│   ├── Expr.kt          # Expression wrapper (preserve existing Expr class)
│   └── Condition.kt     # Typed condition (boolean Expr)
├── platform/
│   └── PlatformAnnotations.kt  # BankSlot?, VRAMRange?, OAMSlot? — null until analysis
└── types/
    └── CoreTypes.kt     # u8, u16, i8, i16 — preserve as-is
```

```
gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/
├── dsl/
│   ├── CharacterDsl.kt  # character {} builder
│   ├── MonsterDsl.kt    # monster {} builder
│   ├── BattleDsl.kt     # battle {} builder
│   └── AbilityDsl.kt    # ability {} builder
└── domain/
    ├── CharacterDef.kt  # Domain data (hp, atk, etc.) — not IR
    ├── MonsterDef.kt
    └── BattleDef.kt
```

### Pattern 1: Sealed ScriptOp Hierarchy

The new `ScriptOp` replaces `IRStatement` as the instruction vocabulary. All operations the runtime executes are `ScriptOp`. The sealed hierarchy stays in `gbkt-core`.

```kotlin
// gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/core/ScriptOp.kt
sealed interface ScriptOp {
    val sourceLocation: SourceLocation?
        get() = null
}

// Movement and position
data class SetPosition(val actorId: String, val x: Expr, val y: Expr) : ScriptOp
data class MoveBy(val actorId: String, val dx: Expr, val dy: Expr) : ScriptOp

// Control flow
data class IfOp(val condition: Condition, val then: List<ScriptOp>, val otherwise: List<ScriptOp> = emptyList()) : ScriptOp
data class WhileOp(val condition: Condition, val body: List<ScriptOp>) : ScriptOp

// State mutation
data class Assign(val target: AssignableExpr, val value: Expr, val op: AssignOp = AssignOp.SET) : ScriptOp

// Scene navigation
data class NavigateTo(val route: String) : ScriptOp

// Battle trigger (genre-agnostic hook)
data class TriggerSystem(val systemId: String, val args: Map<String, Expr> = emptyMap()) : ScriptOp

// Math utility
data class MathOp(val result: AssignableExpr, val op: MathFunction, val args: List<Expr>) : ScriptOp

// Raw C escape hatch
data class RawOp(val code: String) : ScriptOp
```

Key: `when(op)` is exhaustive without `else` because `ScriptOp` is sealed.

### Pattern 2: Platform Annotations as Nullable Fields

All IR nodes that need platform-specific layout information carry nullable annotation fields. These start as `null` and are filled in by a platform analysis pass before codegen.

```kotlin
// gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/platform/PlatformAnnotations.kt
data class BankSlot(val bank: Int, val offset: Int? = null)
data class VRAMRange(val start: Int, val end: Int)
data class OAMSlot(val slot: Int)

// gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/core/ActorIR.kt
data class ActorIR(
    val id: String,
    val position: PositionDef,
    val sprite: SpriteDef?,
    val hitbox: HitboxDef?,
    // Platform annotations: null until analysis fills them
    val bankSlot: BankSlot? = null,
    val vramRange: VRAMRange? = null,
    val oamSlot: OAMSlot? = null,
)
```

### Pattern 3: `ref()` — Two-Stage Reference Validation

The key design for DSL-02: `ref()` gives typed cross-construct references. The locked decision says obvious errors fail at DSL recording time, cross-cutting errors at IR validation.

```kotlin
// Stage 1: DSL recording — ref() creates a typed placeholder
// Any ref that cannot be resolved at build() time fails immediately
class RefHandle<T>(val id: String, val kind: RefKind)

enum class RefKind { SCENE, ACTOR, ABILITY, MONSTER, ITEM, STATUS_EFFECT }

// Usage in DSL:
val gameplayScene = scene("gameplay") { ... }
// ref() returns a SceneRef which can be used in NavigateTo
val toGameplay = ref<SceneRef>(gameplayScene)  // immediate; no string lookup

// String-based ref (resolves at build time):
navigateTo(ref("gameplay"))  // validated during game.build()
```

**Implementation approach:** Because the new DSL is declarative and all constructs are defined before `build()` is called, string-based `ref()` can defer validation to `build()`. A `RefRegistry` accumulates all declared IDs during DSL execution; `ref("name")` records a pending reference. During `build()`, the registry resolves all pending refs and throws with `file:line:col: error: Unresolved reference "gameplay". Did you mean "gameplay-scene"?` for any that don't resolve.

### Pattern 4: `asset()` — File Reference with Deferred Validation

```kotlin
// asset() creates a typed file reference
data class AssetRef<T : AssetType>(val path: String, val type: KClass<T>)

inline fun <reified T : AssetType> asset(path: String): AssetRef<T> = AssetRef(path, T::class)

// Usage:
val playerSprite = asset<SpriteAsset>("sprites/player.png")
val dungeon = asset<TilemapAsset>("tilemaps/dungeon.tmj")
```

Asset file existence is validated at IR validation pass (not at DSL recording time), since asset files may not exist in the filesystem during unit tests.

### Pattern 5: Genre Package Extension Pattern

Since sealed interfaces can't be extended across modules, genre packages extend the DSL surface (not the IR). The `gbkt-rpg` module adds builder extension functions that produce core IR nodes:

```kotlin
// gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/BattleDsl.kt
// Extension on GameBuilder — lives in gbkt-rpg, adds to the core builder type
fun GameBuilder.simpleBattle(id: String, config: SimpleBattleConfig): SimpleBattleDef {
    // Produces TriggerSystem + Assign + IfOp nodes (all core IR)
    val def = SimpleBattleDef(id, config)
    registerSystem(def)
    return def
}
```

The Explorer game depends on both `gbkt-core` and `gbkt-rpg`. When it calls `simpleBattle {}`, it uses the RPG extension. Pong/Breakout only depend on `gbkt-core`.

### Pattern 6: Declarative Scene with Route Navigation

Inspired by Compose Navigation: scenes are identified by route strings, and navigation is declarative.

```kotlin
// New DSL target style:
game("Explorer") {
    routes {
        scene("title") {
            // Describe what this scene IS
            background = Color.BLACK
            actors = listOf(titleTextActor)
            onInput(buttons.start.pressed) {
                navigate("gameplay")
            }
        }
        scene("gameplay") {
            enter { camera.follow(player) }
            actors = listOf(player)
            onFrame {
                whenever(dpad.right.held) { player.x += 2 }
            }
        }
    }
    start = "title"
}
```

The `navigate()` call inside `onInput {}` or `onFrame {}` blocks emits a `NavigateTo` ScriptOp. Scene IDs are validated by the RefRegistry at `build()` time.

### Anti-Patterns to Avoid

- **Splitting sealed IR across modules.** The Kotlin compiler enforces that all sealed subclasses/subinterfaces are in the same module. Attempting to put RPG-specific ops in `gbkt-rpg` as `IRStatement` subtypes will fail to compile. Genre packages must produce core IR nodes, not extend the sealed hierarchy.
- **Using `else` in `when(op)` expressions in codegen.** The requirement is that exhaustive matching compiles without `else`. Adding `else` masks missing cases when new ops are added. Use `@Suppress("REDUNDANT_ELSE_IN_WHEN")` as a lint check instead.
- **RecordingContext mutation from outside the DSL.** The thread-local pattern means DSL code must not escape recording blocks (e.g., launching coroutines or threads inside a builder). This is the same constraint as the current system.
- **Resolving `ref()` at DSL recording time via reflection or runtime lookup.** This creates ordering dependency (constructs must be declared before they're referenced). The two-stage approach (record all refs, resolve at `build()`) is more flexible.
- **Making `GameIR`/`GameDef` a sealed interface.** The top-level game description is a data class, not sealed. Only `ScriptOp`, `Expr`, and `Condition` need to be sealed for exhaustive matching.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Fuzzy string matching for "Did you mean?" | Custom Levenshtein | Existing `Suggestions.kt` in gbkt-core | Already has `levenshteinDistance`, `findSuggestions`, `formatSuggestion` — battle-tested |
| Source location capture | Stack frame parsing | Existing `SourceLocation.capture()` in dsl/RecordingContext.kt | Already captures `StackWalker` frame for sourcemaps |
| Property delegate variable recording | Custom KProperty delegation | Existing `U8Delegate`, `U16Delegate`, etc. in Variables.kt | Already handles GameScopeContext registration |
| IR deep copy for LogicBlock | Manual clone | Existing `IRSubstitution.kt` deepCopy infrastructure | Proven pattern; adapting is faster than rebuilding |

**Key insight:** The existing IR + DSL infrastructure (recording context, property delegates, sealed hierarchy, expression wrapper, suggestions) is sound and can be adapted. The clean break is in the *API surface* (how game authors write DSL), not in the *recording machinery* underneath.

---

## Common Pitfalls

### Pitfall 1: Sealed Interface Cross-Module Extension Attempt
**What goes wrong:** Developer puts `gbkt-rpg` in a separate Gradle module, tries to add `class BattleOp : ScriptOp` — Kotlin compiler refuses with "Inheritor of sealed class/interface must be in the same package or a subpackage".
**Why it happens:** This is a fundamental Kotlin language constraint for sealed interfaces/classes.
**How to avoid:** All sealed IR subtype definitions (including RPG battle ops) live in `gbkt-core`. The `gbkt-rpg` module provides DSL extension functions that produce these pre-existing IR types. Document this clearly in `gbkt-core/ir/CLAUDE.md`.
**Warning signs:** Any `class X : ScriptOp` appearing in `gbkt-rpg/src/`.

### Pitfall 2: ScriptOp Coverage Gap in Codegen
**What goes wrong:** A new `ScriptOp` subtype is added (e.g., `GridMoveOp`) but codegen's `when(op)` doesn't handle it — the Kotlin compiler would flag this, but only if there's no `else` branch. If someone adds `else -> error("unknown")`, new ops silently fail at runtime.
**Why it happens:** Pressure to add a catch-all during development.
**How to avoid:** Never add `else` branches in `when(op: ScriptOp)` in codegen. CI should fail on any `else` in codegen when-expressions over sealed types. Detekt can enforce this.
**Warning signs:** `else -> error("Unsupported op")` in any codegen file.

### Pitfall 3: `ref()` Ordering Requirement
**What goes wrong:** Author writes `ref("gameplay")` before `scene("gameplay") {}` is declared. With two-stage resolution this is fine, but if any code tries to resolve refs eagerly during DSL execution, ordering matters.
**Why it happens:** Temptation to resolve immediately for better IDE error feedback.
**How to avoid:** Refs are registered as pending and resolved at `build()` time. Document the two-stage lifecycle clearly. Unit tests should verify refs to nonexistent targets fail *at `build()`*, not during DSL execution.
**Warning signs:** `RefRegistry.resolve()` calls inside builder lambda execution.

### Pitfall 4: Platform Annotation Fields Forgotten in New IR Nodes
**What goes wrong:** Developer adds new `ActorIR` variant (e.g., `GridActorIR`) but forgets the three nullable annotation fields. Analysis pass silently skips it, codegen crashes with NPE on bank slot.
**Why it happens:** Pattern not enforced by type system.
**How to avoid:** Extract `PlatformAnnotatable` interface with the three nullable fields. All IR nodes that need layout annotations must implement it. The analysis pass iterates `PlatformAnnotatable` instances, so new IR nodes are automatically included.
**Warning signs:** New `*IR` data classes without `PlatformAnnotatable`.

### Pitfall 5: Example Game Modules Using Old API Surface
**What goes wrong:** Example games (`pong`, `breakout`, `explorer`) are rewritten in the new DSL, but Gradle module dependencies still reference the old `gbkt-core` API. After the clean break, old DSL functions (`gbGame {}`, `whenever {}`) are removed, causing compile errors in examples that haven't been updated.
**Why it happens:** Examples and core are developed in parallel; API surface changes faster than examples.
**How to avoid:** The three example games are rewritten as part of task 01-03 in the same PR as the new DSL API. They serve as the acceptance test: if they compile, the DSL is complete.
**Warning signs:** Examples importing `io.github.gbkt.core.gbGame` (old entry point).

### Pitfall 6: LabyrinthOfTheDragon Import Confusion
**What goes wrong:** Developer accidentally modifies `LabyrinthOfTheDragon-port` when refactoring `gbkt-core` shared types. The port depends on the old API and will break.
**Why it happens:** It's in the settings.gradle.kts include list and participates in Gradle composite builds.
**How to avoid:** Keep `LabyrinthOfTheDragon-port` on the old API by either (a) excluding it from CI during Phase 1, or (b) having it depend on a legacy compatibility shim. The simplest approach: mark it as `expected to fail` in CI with a clear comment, or remove it from `settings.gradle.kts` temporarily during Phase 1.
**Warning signs:** CI failures on `LabyrinthOfTheDragon-port` tasks during Phase 1.

---

## Code Examples

Verified patterns from existing codebase:

### Current Recording Context Pattern (preserve this machinery)
```kotlin
// Source: gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/RecordingContext.kt
object RecordingContext {
    private val holder = RecorderHolder()  // ThreadLocal<StatementRecorder?>

    val isRecording: Boolean get() = current != null

    fun <T> record(recorder: StatementRecorder, block: () -> T): T {
        val previous = holder.get()
        holder.set(recorder)
        return try { block() } finally { holder.set(previous) }
    }
}
```

### Current Sealed IRStatement (adapt to ScriptOp)
```kotlin
// Source: gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/CoreIR.kt
sealed interface IRStatement {
    val sourceLocation: SourceLocation?
        get() = null
}

// These are exhaustively matched in codegen — no 'else' needed:
when (stmt) {
    is IRAssign -> generateAssign(stmt)
    is IRIf -> generateIf(stmt)
    // ...
}
```

### Current Expr Operator Overloads (preserve entirely)
```kotlin
// Source: gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/ExpressionWrapper.kt
// The Expr class with 60+ operator overloads is the foundation for DSL ergonomics.
// Do not redesign this — it is proven and complete.
playerX + 2          // Expr(IRBinary(IRVar("playerX"), ADD, IRLiteral(2)))
score isAtLeast 100  // Condition(IRBinary(..., GTE, ...))
```

### Current Suggestions Utility (reuse directly)
```kotlin
// Source: gbkt-core/src/main/kotlin/io/github/gbkt/core/Suggestions.kt
val result = suggestFrom("gamepaly", validSceneIds)
// → "Did you mean: gameplay?"
```

### New `ref()` Target Design
```kotlin
// Target: New DSL surface for ref resolution
// File: gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/Refs.kt

// Type-safe ref: created from a SceneDef returned by scene {}
val titleScene: SceneDef = scene("title") { ... }
val ref: SceneRef = titleScene.ref  // No string lookup, always valid

// String-based ref: validated at build() time
data class PendingRef<T>(val id: String, val kind: RefKind)
fun sceneRef(id: String): PendingRef<SceneDef> = PendingRef(id, RefKind.SCENE)

// In game builder: validation at build()
fun build(): GameDef {
    val allSceneIds = scenes.map { it.id }.toSet()
    pendingRefs.filter { it.kind == RefKind.SCENE }.forEach { ref ->
        if (ref.id !in allSceneIds) {
            val suggestion = suggestFrom(ref.id, allSceneIds)
            throw DSLValidationError("${ref.sourceLocation}: error: Unresolved scene reference \"${ref.id}\".$suggestion")
        }
    }
    // ...
}
```

### New Error Format Target
```kotlin
// Target: Compiler-style error with source location
throw DSLValidationError(
    "${location.file}:${location.line}:${location.col}: error: " +
    "Unresolved reference \"${ref.id}\". Did you mean \"${suggestion}\"?"
)
```

---

## State of the Art

| Old Approach | New Approach | When Changed | Impact |
|---|---|---|---|
| `gbGame("Name") { ... }` entry point | `game("Name") { ... }` (or similar clean name) | Phase 1 clean break | Author-facing API changes; recording machinery stays |
| `whenever(condition) { }` imperative events | `onFrame {}` / `onInput {}` declarative handlers | Phase 1 | More Compose-like; still records to ScriptOp |
| `scene("name") { enter { } every.frame { } }` | `scene("name") { enter {} frame {} exit {} }` or dedicated block | Phase 1 | Similar structure; naming cleanup |
| String-based `scene("name")` navigation calls inside frame blocks | `navigate("name")` or `navigate(ref)` with route-based navigation | Phase 1 | Explicit routing, ref-validated |
| RPG in same module as core DSL | `gbkt-rpg` genre package separate module | Phase 1 | BOM selection pattern enabled |
| All IR as flat `IRStatement` subtypes | Grouped by domain: `ScriptOp`, `SceneIR`, `ActorIR`, `SystemIR` | Phase 1 | Cleaner exhaustive matching; easier to reason about domain boundaries |

**Deprecated/outdated:**
- `gbGame {}` entry point: replaced by `game {}` in new DSL
- `IRStatement` / `IRExpression` as top-level sealed names: replaced by `ScriptOp` / `Expr` for conceptual clarity (though the underlying mechanism is preserved)
- Direct `whenever()` in frame blocks: replaced by scoped event handlers

---

## Open Questions

1. **Where does Explorer's simple combat sit: core IR or RPG package?**
   - What we know: Explorer needs attack/defend/item menu, HP/ATK/DEF — very minimal. The locked decision says gbkt-rpg is created in Phase 1 and Explorer uses it.
   - What's unclear: Whether the Explorer combat is implemented via `gbkt-rpg` builders, or via manual ScriptOp construction using only core IR.
   - Recommendation: Explorer depends on `gbkt-rpg`. The RPG package provides a `simpleBattle {}` or `combatScene {}` builder. This proves the BOM concept with the minimal required combat. Explorer does not need full RPG features (abilities, equipment, deep monsters).

2. **Property delegates vs `state()` for game variables**
   - What we know: Current system uses property delegates (`var score by u8Var(0)`) which works well and is already proven. Compose uses `mutableStateOf()` / `remember {}`.
   - What's unclear: Whether `state()` provides any advantage in the new DSL. Property delegates give familiar Kotlin syntax and automatic name capture.
   - Recommendation: Keep property delegates (`var score by u8Var(0)`). The `by` keyword is already idiomatic Kotlin, provides automatic name resolution, and the existing delegate infrastructure is solid. `state()` would be a cosmetic change with no functional benefit.

3. **LabyrinthOfTheDragon-port during Phase 1 CI**
   - What we know: It depends on the old API and will fail after the clean break.
   - What's unclear: Whether to exclude from CI, add a compat shim, or accept build failure.
   - Recommendation: Add a Gradle property `gbkt.legacyBuild=false` that excludes `LabyrinthOfTheDragon-port` from the build. Set this in CI for Phase 1. Document clearly that it remains as a reference and will be migrated in a later phase.

4. **Exhaustive when-matching enforcement for ScriptOp in non-codegen code**
   - What we know: Detekt exclusions allow LongMethod in codegen. The requirement is no `else` branches.
   - What's unclear: Whether Detekt has a rule for this or whether it requires a custom rule.
   - Recommendation: Use a Detekt custom rule (or a simple Grep CI check) that flags `else ->` inside `when` expressions over `ScriptOp`, `ActorIR`, `SceneIR`, `SystemIR` types. This can be a lightweight bash check in the CI pipeline rather than a full custom Detekt rule.

---

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis: `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/CoreIR.kt` — confirmed sealed interface structure, IRStatement/IRExpression, sourceLocation pattern
- Direct codebase analysis: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/RecordingContext.kt` — confirmed thread-local recording context, StatementRecorder pattern
- Direct codebase analysis: `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/Variables.kt` — confirmed property delegate pattern (U8Delegate, U16Delegate, GameScopeContext)
- Direct codebase analysis: `gbkt-core/src/main/kotlin/io/github/gbkt/core/Game.kt` — confirmed Game data class structure with 60+ fields; BOM separation motivations clear
- Direct codebase analysis: `gbkt-examples/pong/src/main/kotlin/.../Pong.kt` — confirmed current DSL syntax; provides baseline for new DSL coverage
- Direct codebase analysis: `gbkt-examples/explorer/src/main/kotlin/.../Explorer.kt` — confirms zone system, camera, exploration, save patterns needed
- Direct codebase analysis: `gbkt-examples/breakout/src/main/kotlin/.../Breakout.kt` — confirms pool (multi-entity) pattern needed
- Direct codebase analysis: `gbkt-core/src/main/kotlin/io/github/gbkt/core/Suggestions.kt` — confirmed existing fuzzy matching via `levenshteinDistance`, `findSuggestions`
- Direct codebase analysis: `gbkt-core/src/main/kotlin/io/github/gbkt/core/Validation.kt` — confirmed existing two-layer validation structure (GameValidator)
- Kotlin language specification (training knowledge, HIGH confidence): sealed interfaces require all subclasses/subinterfaces in the same module — this is a hard constraint, not a convention

### Secondary (MEDIUM confidence)
- Jetpack Compose Navigation patterns (training knowledge): route-based navigation, NavHost/NavController design; applied as inspiration for `routes {}` / `navigate()` DSL
- Compose state management patterns (training knowledge): `remember {}` / `mutableStateOf()` comparison with property delegates

### Tertiary (LOW confidence)
- Gradle BOM (platform) patterns: modeled on Spring Boot BOM and Jetpack library groups; specific Maven/Gradle BOM mechanics for Kotlin multiplatform not verified for this specific use case

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Kotlin 2.3.0, Gradle 9.0, JVM 21 are all already in the project; no new external dependencies needed
- Architecture: HIGH — Based on direct codebase analysis; sealed interface constraint is a Kotlin language fact; recording context pattern is proven
- Pitfalls: HIGH — Sealed cross-module issue is a language constraint; other pitfalls are derived from existing code structure
- IR node design: MEDIUM — The specific naming (ScriptOp vs IRStatement, ActorIR vs Entity) is a design recommendation, not a verified constraint; the planner should adjust naming
- `ref()` implementation: MEDIUM — Two-stage resolution is the right approach; exact API surface is Claude's discretion

**Research date:** 2026-02-17
**Valid until:** Stable (Kotlin 2.x sealed semantics are stable; no external library versioning concerns)
