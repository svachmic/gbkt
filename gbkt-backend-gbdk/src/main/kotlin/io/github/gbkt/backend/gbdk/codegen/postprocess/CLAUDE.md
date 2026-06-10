# codegen/postprocess

Text-level optimization passes that run on emitted C source after `CEmitter` serialization. Controlled by `AnalysisConfig` flags (`sharedConstantTablesEnabled`, `functionDeduplicationEnabled`).

## COutputOptimizer

Coordinator class that applies enabled passes to each file in the output map. Returns a `COutputOptimizationSummary` with counts of deduplicated constants and functions, plus human-readable detail strings. The summary is folded into the analysis `OptimizationReport` and optionally written to `optimization-report.json`.

## SharedConstantTablePass

Deduplicates identical `const` arrays across the emitted C text. Extracts all constant array declarations, groups them by normalized initializer content, and replaces duplicates with `#define` aliases pointing to the canonical (first) occurrence. Reduces ROM usage when multiple scenes share identical tile or data arrays.

Key methods:
- `extractConstArrays()` -- Regex-based extraction of `const UINT8 name[] = { ... };` declarations
- `normalizeInitializer()` -- Strips whitespace differences for content comparison
- `optimize()` -- Performs the full extract-group-replace pass

## FunctionDeduplicationPass

Deduplicates functions with identical bodies and signatures. Extracts function definitions via regex, groups by a deduplication key (normalized body + normalized signature, excluding the function name), and replaces duplicate definitions with `/* Deduplicated: see canonical_name */` comments. Call sites referencing removed functions are rewritten to call the canonical function using word-boundary-aware regex replacement.

Key methods:
- `extractFunctions()` -- Regex-based extraction of C function definitions
- `buildDeduplicationKey()` -- Builds a signature+body fingerprint for grouping
- `optimize()` -- Performs the full extract-group-replace-rewrite pass

## Design Note

These passes operate on C text rather than the typed AST because duplicate detection (identical array initializers, identical function bodies) is simpler and more robust at the text level. The AST would require deep structural equality checks and call-site tracking that the regex approach handles naturally.
