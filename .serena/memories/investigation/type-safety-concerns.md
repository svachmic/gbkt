# Type-Safety Investigation: GenreVisitorResult and Undo Cap

## Status
Investigation INCOMPLETE due to context limit. Findings so far:

## 1. GenreSystemVisitor.kt (FULLY READ)
**File:** `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/GenreSystemVisitor.kt`

**Key Finding - GenreVisitorResult Type-Safety Issue:**
```kotlin
data class GenreVisitorResult(
    val functions: List<Any> = emptyList(),      // Untyped!
    val varDecls: List<Any> = emptyList(),       // Untyped!
)
```

**Rationale (from comments, lines 15-19):**
- Uses `List<Any>` so `GenreSystemVisitor` can live in `gbkt-backend-api` 
- Does NOT depend on `gbkt-backend-gbdk` (avoids circular dependency)
- Concrete backend pipeline casts to `List<CFunction>` and `List<CVarDecl>` at call site
- Genre implementations can return GBDK C-AST instances directly (safe because they depend on gbdk module)

**Problem:** Multiple UNCHECKED_CAST warnings at call sites

## 2. Files Located but NOT YET READ
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — Search for UNCHECKED_CAST
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt` — Search for undo coerceAtMost(16)
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/` — AST type hierarchy

## 3. C AST Files Identified
Located in `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/`:
- `CDeclaration.kt`
- `CExpr.kt`
- `CFile.kt`
- `CFunction.kt`
- `CStatement.kt`
- `CType.kt`

## Next Steps (PENDING)
1. Read GBDKPipelineV2.kt and grep for UNCHECKED_CAST usages
2. Read PuzzleVisitor.kt and find undo depth cap code
3. Read CFunction.kt and CVarDecl.kt to understand their class hierarchies
4. Check what interfaces they implement (might already exist in backend-api)
5. Determine if a common typed base can replace List<Any>
