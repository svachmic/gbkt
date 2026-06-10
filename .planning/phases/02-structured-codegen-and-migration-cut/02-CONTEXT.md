# Phase 2: Structured Codegen and Migration Cut - Context

**Gathered:** 2026-02-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace string-based C code emission with a typed C AST sealed hierarchy. Bank assignment becomes a typed field on AST nodes (no mutable `currentBank` state). A pretty-printer is the single place C strings are assembled. Pong (v2 DSL) compiles to a working ROM through the new pipeline. The old GBDKCodeGenerator is deprecated (deletion deferred to Phase 5 after all games validated).

</domain>

<decisions>
## Implementation Decisions

### C AST granularity
- AST nodes mirror C syntax closely (CIfStatement, CForLoop, CSwitch, CFunction, CVariable, etc.) — not higher-level "intent" nodes
- Bank assignment lives at both CFile and CFunction levels: CFile carries a bank, CFunction can override. Default is inheritance from file.
- Include CRawCode escape hatch for GBDK-specific quirks or inline asm that the AST doesn't model

### Migration strategy
- Old GBDKCodeGenerator is deprecated in Phase 2 (new pipeline takes over for Pong) but NOT deleted until Phase 5 after all three games validated
- This adjusts the original Phase 2 roadmap criterion: "deleted" becomes "deprecated, bypassed by new pipeline"
- Roadmap update needed: Phase 2 SC#5 changes from "deleted" to "deprecated and unused by new pipeline; deleted in Phase 5"

### Pong validation scope
- Pong must use the v2 DSL definition from Phase 1 — proves the full new pipeline: v2 DSL → IR → C AST → C → ROM
- ROM must boot AND play correctly in mGBA: paddle moves, ball bounces, scoring works, game over triggers
- Zero RPG references anywhere in generated Pong C output — no RPG types, constants, or symbols in any generated file
- Basic quality checks on generated C: no dead code, no duplicate definitions, reasonable function names

### Pretty-printer style
- Human-readable output: proper indentation, blank lines between functions, aligned braces
- Section comments in generated C (e.g., `// Scene: gameplay`, `// Actor: player`) to help developers trace DSL → C

### Claude's Discretion
- Whether C AST includes GBDK-specific nodes (CPragma, BANKED annotation) or keeps pure generic C with GBDK specifics handled in IR→AST translation
- Validation approach during migration: C output diff vs ROM-level testing vs hybrid
- IR-to-C-AST visitor architecture: monolithic vs domain-split visitors
- Pretty-printer internal architecture: single class vs composable printers
- Formatting configuration: fixed style vs configurable (indent size, brace style)

</decisions>

<specifics>
## Specific Ideas

- Bank field inheritance (CFile → CFunction) eliminates the documented root cause of bank state leak bugs from the old generator
- CRawCode keeps the AST practical without needing to model every GBDK edge case upfront
- Section comments create a "source map in comments" that helps developers debug without tooling

</specifics>

<deferred>
## Deferred Ideas

- Old GBDKCodeGenerator deletion — deferred to Phase 5 after all three example games validated through new pipeline
- Breakout and Explorer compilation through new pipeline — Phase 5

</deferred>

---

*Phase: 02-structured-codegen-and-migration-cut*
*Context gathered: 2026-02-18*
