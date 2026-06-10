# gbkt-backend-gbdk

GBDK backend module -- compiles `GameIR` into GBDK-compatible C source files for Game Boy / Game Boy Color.

## Architecture

The backend follows a four-stage pipeline:

1. **Analysis** -- `DefaultPipeline` validates IR, allocates banks/VRAM/OAM, produces a budget report.
2. **Annotation** -- `applyAnnotations()` copies bank/VRAM/OAM assignments back onto the `GameIR`.
3. **Code generation** -- `GBDKPipeline` builds a typed C AST (`CFile` trees) from the annotated IR, then emits C text via `CEmitter`. Source maps are collected per file.
4. **Post-processing** -- `COutputOptimizer` runs `SharedConstantTablePass` and `FunctionDeduplicationPass` on the emitted C text to shrink ROM size.

## Literal Emission Convention

The C AST `CExpr` hierarchy carries TWO integer-literal flavours:

- `CLiteral(value: Int)` — **default unsigned-context literal.** Emits `${value}u` when `value >= 0`, `${value}` when negative. Use for assignment initializers, loop counters, screen coordinates, mask values, and every other context where the C target type is unsigned.
- `CIntLiteral(value: Int)` — **signed-safe literal.** Emits bare `${value}` with NO `u` suffix. Use ONLY as the RHS of a `CBinaryExpr(signedExpr, comparisonOp, CIntLiteral(N))` where `signedExpr` is INT8/INT16-typed.

The split exists because C11 §6.3.1.8 "usual arithmetic conversions" promote signed operands of a mixed-signedness comparison to unsigned, silently inverting tests like `signedVar < 0u` (which becomes `unsigned(huge) < 0` — always false). The convention prevents an entire class of runtime bugs that surfaced in Phase 07.4 (racer camera-follow never advanced past 1 second; platformer jump-cancel never fired). Plan 07.4-34 documents the original discovery; Phase 07.9 documents the architectural fix.

### Rules

1. **Signed-context comparison RHS** (`CBinaryExpr(signedExpr, "<"|">"|"<="|">="|"=="|"!=", literal)`) MUST use `CIntLiteral(N)` — never `CLiteral(N)`. The sealed-interface dispatch in `CEmitter.emitExpr` enforces exhaustiveness; the literal is emitted bare.
2. **Unsigned-context literals** (every other site — `UINT8 score = 0`, `UINT16 r = 0xFFFF`, loop bounds, masks, tile coords) MUST use `CLiteral(N)`. Probes captured in `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/evidence/research/probe_c.asm` and `probe_d.asm` confirm SDCC produces byte-identical asm whether the literal carries `u` or not for unsigned-typed targets — but `CLiteral` is the default and visitors MUST NOT cross-pollinate.
3. **`CTernary` then/else branches** that produce literals are NOT comparison RHS — they stay on `CLiteral`. Only the `condition` operand's RHS migrates.
4. **Bucket (b) non-comparison signed-context sites** (`(INT16)x - 80u` arithmetic, `CUnaryExpr("-", literal)` negation, bitwise masks of signed values) currently stay on `CLiteral`. See `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-AUDIT.md` for the per-site catalogue and the follow-up phase recommendation.

#### DSL-authored signed-comparison path (Phase 9 Plan 04, Bug A)

Phase 07.9 fixed the **hardcoded visitor sites** (bucket-a) — places like `ActorVisitor.generateMovementFunction` and `GBDKSystemVisitor.visitCameraSystem` that build `CBinaryExpr` directly. The **DSL-authored path** (bucket-b) — `whenever(spdY isAbove 64) { ... }` lowering through `BinaryExpr(VarRef("spdY"), GT, Literal(64))` → `ExprVisitor.visitBinaryExpr` — was NOT covered, because `ExprVisitor.visitLiteral` unconditionally emits `CLiteral(N)`. SDCC then promoted `_spdY > 64u` to `unsigned > unsigned`, silently making the clamp never fire for negative `spdY`.

Phase 9 Plan 04 closes that gap by giving `ExprVisitor` a variable-type registry (`variables: List<VariableDef>`) and overriding `visitBinaryExpr`: when the operator is a comparison, the LHS is a `VarRef` whose name resolves to an `I8` or `I16` variable, and the RHS is a `Literal`, the RHS lowers to `CIntLiteral(N)` instead of `CLiteral(N)`. The discrimination is **strictly additive** — every other expression shape preserves the pre-fix emission. The regression guard for bucket-a (`SignedComparisonLiteralEmissionTest` 8/8 GREEN) is unaffected.

The wiring goes `GBDKPipeline.buildSceneFile()` → `SceneVisitor.visit(scene, gameIR.actors, gameIR.variables)` → `ExprVisitor(actors, variables)`. Visitor construction sites that do not pass `variables` (`CombatVisitor`, `GBDKSystemVisitor`, `ActorVisitor.generateAnimationFunction`, etc.) inherit the empty default and behave exactly as before — the DSL-authored signed-comparison path is currently only exercised through scene script ops, which is the path Plan 04 fixes. Regression guard: `gbkt-examples/simple-physics/.../SimplePhysicsEmissionTest` (D-11.1/D-11.2/D-11.3, 3/3 GREEN after Plan 04).

### Why this matters

See `.planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/round-8-camera-and-track/11-runtime-green-camera-monotonic-advance-after-fix.txt` for the original root-cause analysis. The bug produces SDCC warning 94 ("comparison is always false due to limited range of data type"), which gbkt's build pipeline currently does not surface to the user — so the bug went undetected through Phase 07.4 round-8 verification. Future hardening (Phase 08+) may add SDCC-warning-as-error escalation as a build-time guard (RESEARCH § Pitfall 5).

### Related

- `CEmitter.emitCastInner` (Plan 07.4-34): precedence-aware cast emission. Composes with the literal-emission convention without conflict — the helper wraps `CCast` inners that are `CTernary`/`CBinaryExpr`/`CUnaryExpr` in parens; literal emission is orthogonal.
- Phase 07.9 audit deliverable (D-04): full catalogue of `CLiteral` call sites by bucket — see `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-AUDIT.md`.
- Phase 07.9 SDCC + GBDK compile probes — see `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-RESEARCH.md` § Section 1 for the asm artifacts that ground the convention.
- Phase 07.9 CONTEXT.md (decisions D-01..D-09) — see `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-CONTEXT.md`.

## Entry Point

`GBDKBackend` implements `CodegenBackend`. Its `generate()` method orchestrates the full pipeline and returns a `GenerationResult` containing `main.c`, `bank1.c`, `game.h`, and optional zone bank files.

## Package Layout

| Package | Purpose |
|---------|---------|
| `codegen/ast/` | Typed C AST model -- `CFile`, `CFunction`, `CStatement`, `CExpr`, `CType` |
| `codegen/visitor/` | 13 visitors that convert IR subsystems into C AST nodes |
| `codegen/pipeline/` | `GBDKPipeline` orchestrates visitor calls; `SourceMapCollector` tracks C-line to DSL-line mappings |
| `codegen/postprocess/` | Text-level optimizations on emitted C output (dedup constants + functions) |
| `codegen/` | `GBDKCollectionCodegen` -- hash tables, pools, ring buffers, fixed slots |
| `profiles/` | `GameBoyProfile`, `GameBoyColorProfile`, `GameBoyConstants` -- target hardware specs |

## How Visitors Fit Together

`GBDKPipeline.buildHomeFile()` and `buildSceneFile()` invoke visitors to produce typed AST fragments:

- **Variable declarations** (`CVarDecl`): `ActorVisitor.visit()`, `SoundVisitor.buildSoundDriverGlobals()`, `DialogVisitor.buildDialogGlobalVars()`, `HudVisitor.buildHudGlobalVars()`
- **#define constants** (`CDefine`): `SceneVisitor.generateSceneEnum()`, `ActorVisitor.generateAnimationDefines()`/`generatePhysicsDefines()`
- **Functions** (`CFunction`): `ScriptOpVisitor.visit()` (called inside scene visitors), `ExprVisitor.visit()` (expression lowering), `MenuVisitor.buildMenuFunctions()`, `CollisionVisitor.buildCollisionCodegen()`
- **System functions**: `GBDKSystemVisitor` handles camera, save, sound, exploration, dialog, combat engine, pathfinding, puzzle objects, NPC collisions, and actor pools.

All visitors produce immutable `CFile`/`CFunction`/`CVarDecl` data classes. The pipeline assembles them into `CFile` instances, which `CEmitter` serializes to C text.

## Output Files

| File | Bank | Contents |
|------|------|----------|
| `main.c` | 0 (HOME) | Globals, input helpers, sprite helpers, sound driver, dialog/menu/HUD, system functions, `main()` |
| `bank1.c` | 1 | Scene enter/frame/exit functions, tileset guards |
| `game.h` | -- | Include guards, extern declarations, forward prototypes with `BANKED` |
| `zone_bankN.c` | N | Tilemap data arrays for dungeon zones |

## Dependencies

- **gbkt-backend-api**: `CodegenBackend` interface, `GenerationResult`, `ValidationResult`
- **gbkt-analysis**: `DefaultPipeline`, `PassContext`, analysis passes
- **gbkt-engine**: `GameIR`, all IR node types
- **gbkt-genre-rpg**: RPG IR nodes (characters, monsters, abilities, combat)

## Key Design Decisions

- **Typed C AST** eliminates bank-state-leak bugs. Each `CFile` carries an immutable `bank` field instead of relying on mutable `currentBank` state.
- **Visitors produce AST fragments, not strings.** The `CEmitter` is the single point of text serialization, which enables source map collection.
- **Post-processing operates on text**, not the AST, because dedup patterns (identical function bodies, identical constant arrays) are easier to detect after emission.
- **Sound and input helpers live in HOME bank** (bank 0) so they are always accessible without bank switching.
- **Header prototypes are auto-extracted** from the built `CFile` function lists via `CFunction.toPrototype()`, not manually enumerated. This guarantees every generated function has a matching prototype in `game.h` and eliminates a class of GBDK linker bugs.
