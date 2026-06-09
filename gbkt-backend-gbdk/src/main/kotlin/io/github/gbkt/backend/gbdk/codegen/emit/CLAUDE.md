# codegen/emit

Single pretty-printer that converts the typed C AST (`CFile` trees) into C source text. `CEmitter` is the ONLY file in the gbkt-backend-gbdk module that calls `buildString` or `appendLine` to assemble C strings -- all visitors produce typed AST nodes.

## Files

| File | Role |
|------|------|
| `CEmitter.kt` | `object CEmitter` -- exhaustive `when`-dispatch over `CFile`, `CFunction`, `CStatement`, `CExpr`, `CType`. Emits C text with optional source-map collection. |

## Conventions

### Literal Emission (Phase 07.9)

`CExpr` carries two integer-literal flavours, dispatched by `CEmitter.emitExpr`:

- `CLiteral(value: Int)` -- emits `${value}u` when `value >= 0`, `${value}` when negative. Default for every unsigned-context site.
- `CIntLiteral(value: Int)` -- emits bare `${value}` with NO `u` suffix. Used ONLY as the RHS of signed-context comparisons (`CBinaryExpr(signedVar, "<"|">"|"<="|">="|"=="|"!=", CIntLiteral(N))` where `signedVar` is INT8/INT16-typed).

See `gbkt-backend-gbdk/CLAUDE.md` § "Literal Emission Convention" for the rule, the Phase 07.9 evidence trail, and the audit catalogue (`.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-AUDIT.md`).

The hot site is `CEmitter.kt` `emitExpr` -- `is CLiteral` and `is CIntLiteral` branches. The sealed-interface dispatch enforces exhaustiveness at compile time -- any new `CExpr` subtype must add an `emitExpr` arm.

Selection of `CIntLiteral` vs `CLiteral` fires through two paths now:

1. **Hardcoded visitor sites (bucket-a, Phase 07.9):** `ActorVisitor.generateMovementFunction`, `GBDKSystemVisitor.visitCameraSystem`, etc. construct `CBinaryExpr(signedVar, op, CIntLiteral(N))` directly.
2. **DSL-authored path (bucket-b, Phase 9 Plan 04):** `ExprVisitor.visitBinaryExpr` inspects its `variables: List<VariableDef>` registry; when the IR shape is `BinaryExpr(VarRef(name), comparisonOp, Literal(N))` and `name` resolves to `VarType.I8`/`VarType.I16`, the visitor emits `CIntLiteral(N)` for the RHS. All other expression shapes preserve the pre-fix `CLiteral` emission. The wiring is `GBDKPipelineV2 → SceneVisitor.visit(scene, actors, gameIR.variables) → ExprVisitor(actors, variables)`.

`CEmitter` itself is unchanged across both phases — the contract boundary stays at the AST. The split lives in the visitor layer.

### Precedence-aware Cast (Plan 07.4-34)

`CCast(type, inner)` emits as `(type)<rendered-inner>`. When `inner` is a `CTernary`, `CBinaryExpr`, or `CUnaryExpr`, `emitCastInner` wraps it in parens to keep the cast bound to the entire expression. See the helper docstring at `CEmitter.kt:451` and the original bug at Plan 07.4-32 / Plan 07.4-34.

The Phase 07.9 literal-emission convention composes with `emitCastInner` -- they operate on orthogonal axes (`emitCastInner` decides parens around the inner expression; the `is CLiteral`/`is CIntLiteral` dispatch decides whether the literal carries `u`).

### One-place text emission

This file is the contract boundary between typed AST and C text. Adding new emission policies (e.g. signed-context literals) belongs here. Adding new AST shapes (new `CExpr` subtypes) belongs in `../ast/`. Bypassing `CEmitter` (e.g. a visitor that concatenates C strings directly) regresses the source-map collector and breaks the contract.

## Related

- `../ast/CLAUDE.md` -- the typed C AST hierarchy. New emission policies usually pair with AST changes documented there.
- `gbkt-backend-gbdk/CLAUDE.md` § "Literal Emission Convention" -- the module-level convention doc.
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/round-8-camera-and-track/11-runtime-green-camera-monotonic-advance-after-fix.txt` -- the original Phase 07.4 root-cause analysis that produced the Phase 07.9 architectural fix.
- `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-RESEARCH.md` § Section 1 -- SDCC + GBDK compile probe artifacts.
- `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-CONTEXT.md` -- phase decisions (D-01..D-09).
