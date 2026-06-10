# gbkt-backend-api — Backend Contract

Defines the abstract contract that all codegen backends must implement, plus ServiceLoader-based discovery and genre-plugin extension points. This module is platform-agnostic — it knows nothing about GBDK, GBA, or any specific target.

## Dependencies
- **Depends on:** `gbkt-core` (for `GameIR`, `TargetProfile`, `GenericSystem`)
- **Used by:** `gbkt-backend-gbdk`, `gbkt-analysis`, genre modules (platformer, puzzle, sport)

## Key Files

| File | Role |
|------|------|
| `CodegenBackend.kt` | Core interface: `validate(GameIR)` and `generate(GameIR, GenerationOptions)`. Also defines `GenerationOptions` (debug, sourceMap, optimizationLevel, outputFormat) and `OutputFormat` enum. |
| `BackendRegistry.kt` | Singleton `object` that discovers backends via `ServiceLoader` and exposes `forId()`, `forTarget()`, `all()` lookups. |
| `GenreSystemVisitor.kt` | ServiceLoader extension point for genre plugins. `canHandle(systemType)` + `visit()` returns `GenreVisitorResult` containing `CodegenFragment` lists. |
| `CollectionCodegen.kt` | Interface for generating C data structures: hash tables, object pools, ring buffers, and fixed slots (data + functions for each). |
| `GenerationResult.kt` | Return type of `generate()`: success flag, file map (`Map<String, GeneratedFile>`), optional `SourceMap`, and error message. |
| `ValidationResult.kt` | Return type of `validate()`: `isValid` flag, error/warning lists of `ValidationMessage` with `ValidationSeverity`. |

## Architecture

**Backend contract:** `CodegenBackend` exposes four properties (`id`, `displayName`, `profile`, `romExtension`) and two methods (`validate`, `generate`). Compilation (invoking lcc, devkitPro, etc.) is deliberately outside this interface — it belongs in the Gradle plugin or CLI.

**Discovery:** `BackendRegistry.discover()` uses `java.util.ServiceLoader` to find all `CodegenBackend` implementations on the classpath. Discovery is idempotent and synchronized. Backends register via `META-INF/services/io.github.gbkt.backend.api.CodegenBackend`.

**Genre plugins:** `GenreSystemVisitor` lets genre modules (RPG, platformer, etc.) contribute codegen without a hard compile-time dependency on a specific backend. Visitors are also discovered via ServiceLoader. The `CodegenFragment` marker interface avoids circular dependencies — concrete types like `CFunction` implement it in the backend module.

**Collection codegen:** `CollectionCodegen` is a mix-in trait for backends that support static collection types. Each collection (hash table, pool, ring buffer, fixed slots) has a `Data` method (variable declarations) and a `Functions` method (init/lookup/insert helpers).

## Testing

```bash
./gradlew :gbkt-backend-api:test
```

## Common Tasks
- **Add a new backend:** Implement `CodegenBackend`, register via `META-INF/services/io.github.gbkt.backend.api.CodegenBackend`
- **Add genre codegen:** Implement `GenreSystemVisitor`, register via `META-INF/services/io.github.gbkt.backend.api.GenreSystemVisitor`
- **Add a collection type:** Add `generate{Type}Data` + `generate{Type}Functions` methods to `CollectionCodegen`
- **Extend generation options:** Add properties to `GenerationOptions`, or use `customOptions` map for backend-specific settings
