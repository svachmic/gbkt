# codegen/ast

Typed C AST model used between visitor code generation and text emission. All types are immutable `data class` / `sealed interface` hierarchies.

## Hierarchy

```
CFile                        -- Top-level: one per output file (main.c, bank1.c, game.h, zone_bankN.c)
  ├── includes: List<String>
  ├── defines: List<CDefine>
  ├── typedefs: List<CTypedef>
  ├── variables: List<CVarDecl>
  ├── rawSections: List<String>   -- Escape hatch for collection data patterns
  └── functions: List<CFunction>
        ├── params: List<CParam>
        └── body: List<CStatement>
              ├── CIf / CFor / CWhile / CSwitch  -- Control flow
              ├── CVarDecl / CExprStatement        -- Declarations and expressions
              ├── CReturn / CBreak / CContinue     -- Jump statements
              ├── CBlock / CComment / CBlankLine   -- Structure
              └── CRawCode                         -- Escape hatch
```

## Key Types

- **CType**: `CU8`, `CU16`, `CI8`, `CI16`, `CVoid`, `CPointer`, `CArray`, `CConst`
- **CExpr**: `CLiteral`, `CStringLiteral`, `CVar`, `CBinaryExpr`, `CUnaryExpr`, `CCall`, `CTernary`, `CArrayAccess`, `CCast`, `CRawExpr`
- **CStatement**: `CIf`, `CFor`, `CWhile`, `CSwitch`/`CSwitchCase`, `CReturn`, `CBlock`, `CVarDecl`, `CExprStatement`, `CRawCode`, `CComment`, `CBlankLine`, `CBreak`, `CContinue`
- **CFunction**: Carries `bank`, `isBanked`, `isPrototype`, `isStatic` flags. `isPrototype = true` emits a declaration (`;`) instead of a definition with body. Used in `game.h`.
- **CFile**: Carries immutable `bank` field (0 = HOME). `isHeader = true` triggers include-guard emission.

## Design Rationale

The typed AST eliminates the bank-state-leak bugs that plagued the earlier string-concatenation codegen. Each `CFile` knows its bank at construction time, and `CEmitter` is the only code that serializes AST to text, enabling source map collection during emission.
