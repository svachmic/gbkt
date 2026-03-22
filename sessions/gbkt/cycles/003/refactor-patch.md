## Changes: 4

### 1. SemanticValidationPass: Extract generic `collectDuplicates` helper (net -26 lines)

**File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt`

Three methods (`collectDuplicateSceneIds`, `collectDuplicateActorIds`, `collectDuplicateVariableNames`) followed the same pattern: iterate a list, extract a name/ID, add to a set, emit diagnostic on duplicate. Replaced with a single generic `collectDuplicates<T>(items, nameOf, kind, diagnostics)` method.

### 2. ScriptOpTraversal: Extract `collectAllGameOps` helper; simplify `checkFadeWithoutAudioMixer` (net -17 lines)

**Files:**
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt` (added `collectAllGameOps`)
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt` (simplified caller)

`checkFadeWithoutAudioMixer` contained a 25-line `buildList` block that manually collected all top-level ops from every game source (scenes, zones, collision rules, pools, menus, puzzle objects, systems). This pattern is general-purpose and was extracted into `collectAllGameOps(game: GameIR)` in ScriptOpTraversal, reducing the caller to a single line. Also removed the now-unused `CombatEngineSystem` and `ExplorationSystem` imports from `SemanticValidationPass`.

### 3. BackendRegistry: Deduplicate `discover()` and `all()` (net -2 lines)

**File:** `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/BackendRegistry.kt`

`discover()` and `all()` had identical implementations (call `ensureInitialized()`, return `state.backends.values.toList()`). Changed `discover()` to delegate to `all()`.

### 4. PoParser: Deduplicate `parse()` and `parseWithValidation()` (net -2 lines)

**File:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt`

`parse(file, padding)` duplicated the file-existence check and file-reading logic from `parseWithValidation(file, padding)`. Changed `parse()` to delegate to `parseWithValidation().entries`.

### Test results

All 168 Gradle tasks pass (`./gradlew test` -- BUILD SUCCESSFUL).
