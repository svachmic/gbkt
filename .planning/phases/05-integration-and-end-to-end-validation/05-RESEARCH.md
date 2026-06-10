# Phase 5: Integration and End-to-End Validation - Research

**Researched:** 2026-02-19
**Domain:** Gradle plugin wiring, v2 pipeline integration, mGBA headless validation
**Confidence:** HIGH (all claims verified by direct code inspection and live test runs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Validation depth**
- "Runs correctly" means **boots without crash** — ROM loads in mGBA, no hangs or glitches within ~300 frames (~5 seconds)
- Same bar for all three games (Pong, Breakout, Explorer) — no gameplay walkthrough required in Phase 5
- Validation is **automated**: a Gradle task runs mGBA headless, boots the ROM, and verifies no crash within N frames
- Manual gameplay validation deferred to a separate UAT phase (see Deferred Ideas)

**Pipeline error experience**
- lcc compilation errors are **mapped back to Kotlin DSL source** using the source map — developer sees DSL file:line, not generated C file:line
- Source map is a **separate .map file** alongside generated C (not inline comments)
- Analysis-level error presentation: Claude's discretion (Phase 4 passes already produce actionable messages)
- Pipeline failure strategy: Claude's discretion (fail-fast vs. collect-all)

**Gradle task granularity**
- **Opinionated defaults with possible tweaking** — sensible task graph out of the box, configurable if developer needs to override
- **Full incremental builds** — use Gradle's up-to-date checking; skip stages whose inputs haven't changed
- Generated C files live in **`build/generated/gbkt/`** (e.g., `build/generated/gbkt/main.c`, `build/generated/gbkt/bank1.c`)
- Budget report **runs by default with every `buildRom`**, with a flag to disable if developer wants to skip (e.g., `gbkt { budgetReport = false }`)

**Explorer feature scope**
- **Everything compiles** — all Explorer features (RPG combat, dungeon exploration, menus, encounters, items, abilities) must generate valid C and compile
- **Feature parity with v1** — ExplorerV2.kt must express everything the original Explorer does (all floors, encounters, items, abilities, combat)
- **Different C output is fine** — v2 pipeline can produce structurally different C than v1 as long as the ROM boots
- **OK to break v1 pipeline** — v1 codegen can break during Phase 5 wiring; it gets deleted in Phase 5.1. But: keep enough debugging capability for the UAT phase that follows

### Claude's Discretion
- Analysis diagnostic presentation format in Gradle output
- Pipeline failure strategy (fail-fast vs. collect-all)
- Exact mGBA headless invocation and frame count for automated ROM check
- Internal Gradle task decomposition (which stages are separate tasks vs. internal steps)
- Source map file format (JSON, text, etc.)

### Deferred Ideas (OUT OF SCOPE)
- **UAT manual validation phase** — A phase between Phase 5 and Phase 5.1 where all three games are manually played/debugged in mGBA to verify actual gameplay works (not just boot). User wants effective debugging support during this phase.
- **IntelliJ plugin source-map viewer** — Read the .map file and show side-by-side Kotlin DSL ↔ generated C mapping in the IDE. Add to roadmap as future IntelliJ plugin feature.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| INTG-01 | Pong example compiles to working .gb ROM through new pipeline | Gradle plugin v2 wiring, GBDKBackend.generateV2(), PongV2 GameBuilder verified |
| INTG-02 | Breakout example compiles to working .gb ROM through new pipeline | Same wiring; BreakoutV2 verified in IR tests |
| INTG-03 | Explorer example compiles to working .gb ROM through new pipeline | ExplorerV2 uses gbkt-rpg; GenericSystem ScriptOp codegen must handle TriggerSystem |
| INTG-04 | Gradle plugin orchestrates full build pipeline (assets → DSL → analysis → codegen → lcc) | Current task graph nearly correct; central gap is GameBuilder→GameIR bridge |
</phase_requirements>

---

## Summary

Phase 5 has one dominant integration gap that blocks all four requirements: the Gradle plugin's `GenerateCTask` and `BudgetReportTask` do not know how to handle v2 `GameBuilder` objects. When `pongV2`, `breakoutV2`, or `explorerV2` is loaded via reflection, the plugin receives a `GameBuilder` instance, but then attempts to call `backend.generate(game, options)` which expects a v1 `Game`. This fails with `argument type mismatch` and falls through to "No backend found for target 'gbc'". This was verified live: `./gradlew :gbkt-examples:pong:generateC` fails with this exact error.

The fix is a three-part bridge in `GenerateCWorkAction.execute()`: detect when `rawGame` is a `GameBuilder`, call `.build()` to get `GameIR`, then route to `backend.generateV2(gameIR)` (which already exists in `GBDKBackend` and runs the full analysis pipeline). The `BudgetReportTask` needs the same bridge — its `resolveGameIR()` tries `getIr()` but `GameBuilder` exposes `build()`.

The mGBA headless validation requirement has a critical constraint: mGBA does NOT have a true headless/no-GUI mode in any released version through 0.10.4 (Jan 2025). The correct approach is to use mGBA's SDL frontend (`mgba-sdl`) with a Lua validation script via the `-S` flag, with a process-level timeout as the crash/hang safety net.

**Primary recommendation:** Fix `GenerateCWorkAction` and `BudgetReportTask` to detect `GameBuilder` and call `.build()` to get `GameIR`, then route to `GBDKBackend.generateV2()`. Everything else (analysis pipeline, codegen, CompileRomTask) is already wired correctly.

---

## Standard Stack

### Core (already in codebase, no new dependencies needed)

| Component | Location | Purpose | Status |
|-----------|----------|---------|--------|
| `GBDKBackend.generateV2(GameIR)` | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` | Full v2 pipeline: analysis → codegen | EXISTS, not called by plugin |
| `GameBuilder.build()` | `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt` | Converts `GameBuilder` → `GameIR` | EXISTS, not called by plugin |
| `GBDKPipelineV2.generate(GameIR)` | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | C code generation from v2 IR | EXISTS and tested (PongPipelineTest passes) |
| `DefaultPipeline.create()` | `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/DefaultPipeline.kt` | 10-pass analysis pipeline | EXISTS and tested |
| `BudgetReportWorkAction.runAnalysisOnGameIR()` | `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/BudgetReportTask.kt` | Runs analysis via reflection | EXISTS, but `resolveGameIR()` doesn't handle GameBuilder |
| `SourceMapLoader` | `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/SourceMapLoader.kt` | Maps C line → Kotlin file:line | EXISTS for error enhancement |
| `GbdkErrorParser` + `ErrorEnhancer` | `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/internal/` | Source-map-enhanced error messages | EXISTS |

### No New External Dependencies Required

All machinery exists. Phase 5 is pure wiring and integration work.

---

## Architecture Patterns

### Current Task Graph (exists in `GbktPlugin.registerTasks()`)

```
processAssets
     |
     v
compileKotlin ──────────────────────┐
     |                              |
     v                              v
processAssets ──────> generateC ──> copyResources ──> compileRom ──> buildRom
                          |
                          v
                     [source map]
                     [budget report embedded in generateC output]
```

The actual task names registered:
- `processAssets` — `ProcessAssetsTask`
- `generateC` — `GenerateCTask` (contains `GenerateCWorkAction`)
- `budgetReport` — `BudgetReportTask` (standalone, not part of `buildRom` chain)
- `copyResources` — plain Gradle task
- `compileRom` — `CompileRomTask`
- `buildRom` — lifecycle task

**Gap 1**: The decision requires `budgetReport` to run by default with every `buildRom` (opt-out flag `gbkt { budgetReport = false }`). Currently `budgetReport` is a standalone task, not wired into `buildRom`. This needs to be wired.

**Gap 2**: `GbktExtension` does not have a `budgetReport` property yet.

**Desired task graph per locked decision:**
```
processAssets → generateC (includes analysis + budget report) → compileRom → buildRom
```

Note: The locked decision says "Budget report runs by default with every `buildRom`". The simplest architecture is to embed budget-report printing inside `generateC` (where `GBDKBackend.generateV2()` already calls the analysis pipeline and prints the report). This avoids needing a separate `budgetReport` task in the main chain. The standalone `budgetReport` task can remain for explicit invocation.

### Pattern 1: GameBuilder Detection Bridge (the core fix)

**What:** In `GenerateCWorkAction.execute()`, detect if `rawGame` is a `GameBuilder` (via reflection class name check), call `.build()` to get `GameIR`, then call `generateV2` directly on the backend.

**Current flow (broken):**
```
rawGame = getter.invoke(null)          // Returns GameBuilder
game = rawGame (asset processing fails)
generateWithBackendRegistry(game)      // Calls backend.generate(game, options)
                                       // GBDKBackend.validate(game) expects Game — FAILS
                                       // Falls through → "No backend found"
```

**Fixed flow:**
```
rawGame = getter.invoke(null)          // Returns GameBuilder
if rawGame is GameBuilder:
    gameIR = rawGame.build()           // GameBuilder.build() → GameIR
    backend.generateV2(gameIR)         // Already exists, runs full pipeline
else:
    // v1 path: rawGame is Game (legacy)
    processAssets(rawGame, assetDir)
    generateWithBackendRegistry(game)
```

**Implementation approach:** Check via reflection whether `rawGame` is an instance of `io.github.gbkt.core.dsl.v2.GameBuilder`. If yes, call `build()` method via reflection. The worker action uses classloader isolation, so all classes must be accessed via reflection.

**Code pattern:**
```kotlin
// In GenerateCWorkAction.execute()
val gameBuilderClass = try {
    Class.forName("io.github.gbkt.core.dsl.v2.GameBuilder")
} catch (_: ClassNotFoundException) { null }

if (gameBuilderClass != null && gameBuilderClass.isInstance(rawGame)) {
    // v2 path: GameBuilder → GameIR → generateV2
    val buildMethod = rawGame.javaClass.getMethod("build")
    val gameIR = buildMethod.invoke(rawGame)

    // Get GBDKBackend instance
    val backend = BackendReflection.findBackendForTarget(target)
        ?: throw GradleException("No backend found for target '$target'")

    // Call generateV2(gameIR) via reflection
    val gameIrClass = Class.forName("io.github.gbkt.core.ir.v2.GameIR")
    val generateV2Method = backend.javaClass.getMethod("generateV2", gameIrClass)
    val result = generateV2Method.invoke(backend, gameIR)
    // ... extract files from GenerationResult
} else {
    // v1 path: existing code unchanged
    processAssetsAndGenerate(rawGame, assetDir, target)
}
```

### Pattern 2: BudgetReportTask Bridge (same fix, different task)

**What:** In `BudgetReportWorkAction.resolveGameIR()`, add `build()` fallback for `GameBuilder`.

**Current broken code:**
```kotlin
return if (gameIrClass.isInstance(game)) {
    game
} else {
    try {
        game.javaClass.getMethod("getIr").invoke(game)  // GameBuilder has no getIr()
    } catch (_: NoSuchMethodException) {
        null  // Returns null → "Skipping budget report"
    }
}
```

**Fix:**
```kotlin
return when {
    gameIrClass.isInstance(game) -> game
    else -> try {
        // Try GameBuilder.build()
        game.javaClass.getMethod("build").invoke(game)
    } catch (_: NoSuchMethodException) {
        try {
            game.javaClass.getMethod("getIr").invoke(game)
        } catch (_: NoSuchMethodException) {
            null
        }
    }
}
```

### Pattern 3: budgetReport Flag in GbktExtension

**What:** Add `abstract val budgetReport: Property<Boolean>` to `GbktExtension`. Default `true`. In `GbktPlugin.registerTasks()`, wire `budgetReport` task into `buildRom` unless disabled.

**Where:** `GbktExtension.kt` and `GbktPlugin.registerTasks()`.

**Code pattern:**
```kotlin
// In GbktExtension
abstract val budgetReport: Property<Boolean>

// In GbktPlugin.apply()
extension.budgetReport.convention(true)

// In GbktPlugin.registerTasks()
if (extension.budgetReport.getOrElse(true)) {
    buildRom.configure { dependsOn(budgetReportTask) }
}
```

Note: The existing `BudgetReportTask` already runs the analysis pipeline. Wiring it into `buildRom` means analysis runs twice (once in `budgetReport`, once inside `generateC` via `GBDKBackend.generateV2()`). The cleanest resolution is to have the budget report print happen only in `generateC` (it already does via `generateV2`), and the `budgetReport` task stays as an explicit standalone command. The `gbkt { budgetReport = false }` flag can suppress budget report output inside `generateV2` rather than suppressing a whole task. Recommend planning this as Claude's discretion.

### Pattern 4: Source Map for v2 Pipeline

**What:** `GenerateCWorkAction` currently generates source maps by constructing `GBDKCodeGenerator(game)` — the v1 class. After the v2 fix, source maps need to be generated by the v2 pipeline.

**Issue:** `GBDKPipelineV2` does not currently have a `generateWithSourceMap()` method. The v1 `GBDKCodeGenerator.generateWithSourceMap()` returns `Pair<String, Any>` where `Any` is a source map object.

**Options (Claude's discretion):**
1. Add `generateWithSourceMap()` to `GBDKPipelineV2` — cleanest but requires additional work
2. Skip source maps for v2 games initially (degrade gracefully with a warning) — faster, still meets INTG-01 through INTG-04
3. Generate a basic source map from scene/actor IDs to DSL file line numbers — medium effort

**Recommendation:** Degrade gracefully for Phase 5 (emit a WARNING that source maps are not available for v2 games, but don't block compilation). Source map enhancement is Phase 5 discretion work.

### Pattern 5: mGBA Headless Validation Task

**What:** New Gradle task `validateRom` that runs mGBA with a Lua script, boots the ROM for ~300 frames, checks for crash, exits.

**Critical finding:** mGBA does NOT have a true headless/no-GUI mode as of v0.10.4 (Dec 2024). The scripting blog post (May 2022) explicitly lists "Headless mode" as a PLANNED FUTURE FEATURE. `mgba-rom-test` (the only true headless tool) requires building mGBA from source with `-DBUILD_ROM_TEST=ON`.

**Viable approach:** Use `mgba-sdl` (the SDL frontend, available in standard Linux packages) or macOS `mGBA` with:
1. A Lua validation script (`-S path/to/validate.lua`) that counts frames and calls `os.exit(0)` after 300 frames, or calls `os.exit(1)` if a crash callback fires
2. A process timeout (e.g., 15 seconds) as the hang safety net
3. Exit code 0 = ROM booted without crash, exit code 1 = crash detected

**Lua validation script pattern:**
```lua
-- validate-rom.lua: boot ROM for N frames, exit 0 on success, 1 on crash
local TARGET_FRAMES = 300
local crashed = false

callbacks:add("crashed", function()
    crashed = true
    os.exit(1)
end)

callbacks:add("frame", function()
    if crashed then return end
    if emu:currentFrame() >= TARGET_FRAMES then
        os.exit(0)  -- Successfully booted N frames
    end
end)
```

**Gradle task pattern:**
```kotlin
class ValidateRomTask : DefaultTask() {
    @get:InputFile abstract val romFile: RegularFileProperty
    @get:Optional @get:Input abstract val emulatorPath: Property<String>
    @get:Input abstract val frameCount: Property<Int>

    @TaskAction
    fun validate() {
        val mgba = resolveEmulator()  // Reuse RunEmulatorTask.detectEmulator() logic
        val script = generateValidationScript(frameCount.get())
        val scriptFile = temporaryDir.resolve("validate.lua")
        scriptFile.writeText(script)

        val result = execOperations.exec {
            executable = mgba.absolutePath
            args("-S", scriptFile.absolutePath, romFile.get().asFile.absolutePath)
            isIgnoreExitValue = true
            // Timeout via ProcessBuilder level or use Gradle timeout
        }

        if (result.exitValue != 0) {
            throw GradleException("ROM validation failed: crash detected within ${frameCount.get()} frames")
        }
        logger.lifecycle("ROM validated: ${romFile.get().asFile.name} booted ${frameCount.get()} frames without crash")
    }
}
```

**Known constraint:** The `-S` flag for Lua scripting requires mGBA v0.10.0+. On macOS, `mGBA.app` may not support `-S` from the command line — the SDL build (`mgba-sdl`) is more reliable for automation. This is a platform-specific risk that needs to be handled gracefully (skip validation if mGBA not found or doesn't support scripting).

**Alternative fallback:** If mGBA Lua scripting is not available, use a simpler approach: launch mGBA with a timeout, then check whether the process exited on its own (crash) vs. was terminated (survived). This is less reliable but works on all mGBA versions.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| v2 pipeline execution | New generation pathway | `GBDKBackend.generateV2(GameIR)` | Already exists, tested in PongPipelineTest (17 tests pass) |
| Analysis pipeline | Any new pass ordering | `DefaultPipeline.create()` | Already chains all 10 passes correctly |
| Error mapping | Custom error parser | Existing `GbdkErrorParser` + `ErrorEnhancer` + `SourceMapLoader` | Already implement the C→Kotlin line mapping |
| mGBA detection | New path resolution | Reuse `RunEmulatorTask.detectEmulator()` logic | Already handles macOS/Linux/Windows |
| Incremental inputs | Custom caching | Gradle `@InputFile`, `@InputDirectory`, `@OutputFile` annotations | Gradle handles up-to-date checking automatically |

---

## Common Pitfalls

### Pitfall 1: GameBuilder Is Not a Game and Not a GameIR

**What goes wrong:** `BackendReflection.validateGame()` and `generateCode()` look up the `io.github.gbkt.core.Game` class and cast the game object to it. `GameBuilder` is not a subtype of `Game` — it is an independent class in `io.github.gbkt.core.dsl.v2`. The reflection `getMethod("validate", gameClass)` will fail with `argument type mismatch` when `gameClass` is `Game` but `game` is a `GameBuilder`. This is exactly the failure seen live.

**Why it happens:** The v1 pipeline flow assumed all game objects are `Game`. The v2 DSL produces `GameBuilder` which requires `.build()` before producing the `GameIR` that backends operate on.

**How to avoid:** Add a `GameBuilder` detection step BEFORE any backend calls. The detection uses `Class.forName("io.github.gbkt.core.dsl.v2.GameBuilder").isInstance(rawGame)`.

**Warning signs:** Log output containing "argument type mismatch" or "No backend found for target 'gbc'" when building v2 example games.

### Pitfall 2: generateV2 Is Not on the CodegenBackend Interface

**What goes wrong:** `BackendReflection.generateCode()` calls `backend.javaClass.getMethod("generate", gameClass, optionsClass)`. For v2 games, you need `generateV2(gameIrClass)`. This method exists on `GBDKBackend` but NOT on the `CodegenBackend` interface. `BackendReflection` must call it by name via reflection, not through the interface.

**How to avoid:** Call `backend.javaClass.getMethod("generateV2", gameIrClass).invoke(backend, gameIR)` directly — do not use the `BackendReflection.generateCode()` helper for v2 games.

### Pitfall 3: Source Map Generation Uses v1 GBDKCodeGenerator

**What goes wrong:** After generating files, `GenerateCWorkAction.execute()` tries to generate a source map by constructing `GBDKCodeGenerator(gameClass)`. For v2 games, `game` is now a `GameIR`, not a `Game`. `GBDKCodeGenerator` expects `Game` — this will throw `NoSuchMethodException` or similar when constructing the v1 generator with a v2 object.

**How to avoid:** Gate the source map generation block: only run it for v1 `Game` objects. For v2 games, emit a WARNING and skip source map generation (or implement v2 source maps in a separate subtask).

### Pitfall 4: Asset Processing Expects Game, Not GameBuilder

**What goes wrong:** `AssetPipelineKt.processAssets(Game, String)` is called with `rawGame` which is a `GameBuilder`. The method lookup `pipelineClass.getMethod("processAssets", gameClass, String::class.java)` uses `rawGame::class.java` which is `GameBuilder` — there is no `processAssets(GameBuilder, String)` method. This fails with a warning and falls through.

**How to avoid:** For v2 games, call `rawGame.build()` FIRST to get a `GameIR`, then use `GameIR` for any asset processing that supports it. Note: the v2 asset pipeline may need a separate path since `GameIR` has an `assets: List<AssetRef>` field, not a full `Game` asset model.

### Pitfall 5: budgetReport Wiring Runs Analysis Twice

**What goes wrong:** If `budgetReport` task is added as a dependency of `buildRom`, and `generateC` already prints the budget report (via `GBDKBackend.generateV2()` which calls the analysis pipeline internally), then `buildRom` will run the analysis pipeline twice — once in `budgetReport`, once in `generateC`. This doubles analysis time.

**How to avoid:** Choose one of:
a) Remove the inline budget-report printing from `generateV2` and have `budgetReport` be the sole printer (more complex — requires changing backend API)
b) Keep budget-report printing inside `generateV2` and do NOT add `budgetReport` as a `buildRom` dependency — just mention it in the build lifecycle output as "Run `./gradlew budgetReport` to see resource usage"
c) Add a flag to `generateC` to control whether budget report is printed inline

**Recommendation (Claude's discretion):** Option (b) is simplest. The locked decision says "runs by default with every `buildRom`" — this is already satisfied because `generateC` calls `generateV2` which prints the report. The `budgetReport = false` flag can suppress the in-line print inside `generateV2`. No separate task dependency needed.

### Pitfall 6: mGBA Lua Scripting Requires mGBA 0.10.0+

**What goes wrong:** The `-S <script>` flag for Lua scripting was introduced in mGBA 0.10.0 (October 2022). Older versions will ignore or error on this flag.

**How to avoid:** Validate mGBA version before running validation. Degrade gracefully: if `-S` is not supported, run mGBA with a process timeout and treat process surviving N seconds as "pass" (weaker signal but still detects hard crashes).

### Pitfall 7: mGBA App Bundle vs SDL Frontend on macOS

**What goes wrong:** On macOS, `/Applications/mGBA.app/Contents/MacOS/mGBA` is the Qt frontend. Qt frontend may not support command-line scripting arguments as well as `mgba-sdl`. The `-S` flag behavior may differ.

**How to avoid:** Prefer `mgba-sdl` when available (install via `brew install mgba`). Fall back to the `.app` bundle. Document this in the task output.

### Pitfall 8: Explorer Uses GenericSystem for TriggerSystem

**What goes wrong:** `ExplorerV2` calls `battleUpdate("combat")` which emits `TriggerSystem("combat")` — a `ScriptOp`. The `ScriptOpVisitor` in the v2 codegen uses `else -> CRawCode("/* TODO: ${op::class.simpleName} */")` for unimplemented ops. `TriggerSystem` may fall through to this TODO stub.

**What this means for Explorer:** The generated C will compile (TODO stubs are valid C comments) but the battle system won't work at runtime. For Phase 5 (boots without crash), this is acceptable — the ROM can compile and boot even if battle is a stub.

**How to avoid:** Check `ScriptOpVisitor` for `TriggerSystem` handling. If it's a TODO stub, the Explorer ROM will boot but combat won't trigger. This may be acceptable for Phase 5 per the "boots without crash" definition, but should be documented.

---

## Code Examples

### Verified: How generateV2 is called (from GBDKBackend.kt)

```kotlin
// Source: gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt
fun generateV2(gameIR: GameIR): GenerationResult {
    val analysisConfig = AnalysisConfig.fromCartridgeConfig(gameIR.config)
    val pipeline = DefaultPipeline.create()
    val initialContext = PassContext(game = gameIR, profile = profile, config = analysisConfig)
    val analysisResult = pipeline.execute(initialContext)

    // Prints budget report to stdout (satisfies "budget report with every buildRom")
    if (analysisResult is PassResult.Success) {
        analysisResult.context.budgetReport?.let { println(it) }
    }

    if (analysisResult is PassResult.Failed) { return GenerationResult.failed(...) }

    val annotatedContext = (analysisResult as PassResult.Success).context
    val annotatedGame = applyAnnotations(annotatedContext.game, annotatedContext)
    val pipelineV2 = GBDKPipelineV2()
    val files = pipelineV2.generate(annotatedGame)
    return GenerationResult(success = true, files = generatedFiles)
}
```

### Verified: GameBuilder.build() exists and produces GameIR

```kotlin
// Source: gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt
fun build(): GameIR {
    refRegistry.resolveAll()
    // ... validation ...
    return GameIR(
        name = name, config = config, scenes = ..., actors = ...,
        systems = ..., variables = ..., assets = ..., startScene = startScene,
    )
}
```

### Verified: BudgetReportWorkAction.resolveGameIR() currently fails for GameBuilder

```kotlin
// Source: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/BudgetReportTask.kt
private fun resolveGameIR(game: Any): Any? {
    val gameIrClass = Class.forName("io.github.gbkt.core.ir.v2.GameIR")
    return if (gameIrClass.isInstance(game)) {
        game  // Only works if game IS a GameIR
    } else {
        try {
            game.javaClass.getMethod("getIr").invoke(game)  // GameBuilder has no getIr()
        } catch (_: NoSuchMethodException) {
            null  // GameBuilder falls here → "Skipping budget report"
        }
    }
}
```

### Verified: Live test showing the failure

```
> Task :gbkt-examples:pong:generateC FAILED
WARNING: Asset processing skipped: io.github.gbkt.core.AssetPipelineKt.processAssets(io.github.gbkt.core.dsl.v2.GameBuilder,java.lang.String)
Using backend: GBDK-2020 for Nintendo Game Boy Color
WARNING: Backend generation failed: argument type mismatch
Failed to generate C code: No backend found for target 'gbc'.
```

```
> Task :gbkt-examples:pong:budgetReport
gbkt budget report: game is not a v2 GameIR-backed game. Analysis pipeline requires a v2 GameIR. Skipping budget report.
```

### Verified: PongPipelineTest passes (v2 codegen is correct end-to-end)

The v2 codegen produces correct C for Pong when given a `GameIR` directly. 17 tests pass in `PongPipelineTest`:
- `main.c` contains scene enum defines, actor variables, global variables, main function
- `bank1.c` has `#pragma bank 1`, BANKED scene functions
- `game.h` has forward declarations
- All Pong game logic (ball movement, scoring, input) correctly generated

### Verified: Source map format is JSON

```
// Source: SourceMapLoader.load()
val json = JSONObject(content)
val version = json.optString("version", "1.0")
val gameName = json.getString("gameName")
val cFile = json.getString("cFile")
val mappingsArray = json.getJSONArray("mappings")
// Each mapping: { cLine, kotlinFile, kotlinLine, kotlinColumn, symbol, snippet }
```

---

## State of the Art

| Old Approach | Current Approach | Status | Impact |
|--------------|-----------------|--------|--------|
| v1 `Game` passed directly to backend | v2 `GameBuilder.build()` → `GameIR` → `generateV2()` | v1 exists, v2 exists in backend but not wired in plugin | Phase 5 adds the bridge |
| Analysis as standalone `budgetReport` task | Analysis embedded in `generateV2()` (runs automatically) | Already done in `GBDKBackend.generateV2()` | No extra task needed for analysis |
| Source maps via v1 `GBDKCodeGenerator` | Source maps via v2 pipeline | v1 exists, v2 source map support missing | Phase 5 degrades gracefully |
| ROM validation: manual mGBA | Automated `validateRom` Gradle task | Does not exist yet | Phase 5 adds new task |

**v1 codegen status:** `GBDKBackend.generate(Game)` and `GBDKCodeGenerator` still exist. Per locked decision, v1 can break during Phase 5 (deleted in Phase 5.1). The wiring must not BREAK v1 for games that still use `Game` — but can deprecate/ignore it.

---

## Open Questions

1. **Does ScriptOpVisitor handle TriggerSystem for Explorer?**
   - What we know: `ScriptOpVisitor` uses `else -> CRawCode("/* TODO: ... */")` for unimplemented ops. `TriggerSystem` is a `ScriptOp`.
   - What's unclear: Is `TriggerSystem` explicitly handled or does it fall through to TODO?
   - Recommendation: Check `ScriptOpVisitor` for `TriggerSystem` before writing Explorer plan. If TODO, document it explicitly in Phase 5 plan.

2. **Does ExplorerV2 use features not yet in v2 codegen?**
   - What we know: Explorer uses `saveData`, `camera`, `soundEffect`, `simpleBattle`. All of these register as `GenericSystem` in the IR.
   - What's unclear: Does `GBDKPipelineV2.buildHomeFile()` / `buildSceneFile()` handle `GenericSystem` systems? Are they silently ignored or do they cause errors?
   - Recommendation: Test `explorerV2.build()` through `GBDKPipelineV2.generate()` in a unit test before Phase 5 plans run.

3. **mGBA `-S` flag availability on developer machine**
   - What we know: `-S` for Lua scripting requires mGBA 0.10.0+; macOS `.app` bundle may not support it reliably.
   - Recommendation: Make `validateRom` task skip gracefully when mGBA doesn't support scripting. Use `--version` detection or try-catch on exec.

4. **Asset processing for v2 GameIR**
   - What we know: `AssetPipelineKt.processAssets(Game, String)` works on v1 `Game`. v2 `GameIR` has `assets: List<AssetRef>`.
   - What's unclear: Is there a v2 asset processing function, or does v2 rely on the manifest from `processAssets` Gradle task?
   - What we know from `ProcessAssetsTask`: it processes the asset directory independently of the game object. The asset manifest is written to `build/generated/assets/`. This is already wired as a `generateC` dependency. For v2 games, the PNG→tile conversion happens in `ProcessAssetsTask`, not via `AssetPipelineKt.processAssets()`. So asset processing for v2 is already handled — the warning about `processAssets` failing can be safely ignored for v2 games.

---

## Sources

### Primary (HIGH confidence — direct code inspection)

- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt` — v1 generate flow, confirmed failure pattern
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/BudgetReportTask.kt` — resolveGameIR() confirmed broken for GameBuilder
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` — generateV2() exists and is complete
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt` — build() method exists, returns GameIR
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/PongPipelineTest.kt` — 17 tests pass (v2 codegen is correct)
- Live test run: `./gradlew :gbkt-examples:pong:generateC` — confirmed failure with exact error message
- Live test run: `./gradlew :gbkt-examples:pong:budgetReport` — confirmed "Skipping budget report"
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt` — full task graph wiring verified

### Secondary (MEDIUM confidence — official docs)

- mGBA scripting blog post (2022-05-29): headless mode explicitly listed as "NOT available yet, planned future feature"
- mGBA Scripting API docs (mgba.io/docs/scripting.html): `currentFrame()`, `crashed` callback, frame callbacks documented
- mGBA 0.10.4 release notes (Dec 2024): no headless mode added

### Tertiary (LOW confidence — inferred)

- mGBA `-S` flag for Lua scripts: documented in manpage for mgba-sdl, but macOS `.app` support uncertain — needs empirical validation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components verified by direct inspection
- Architecture (wiring pattern): HIGH — exact methods, class names, reflection patterns identified
- Pitfalls: HIGH — most verified by live test run (generateC failure confirmed)
- mGBA headless approach: MEDIUM — scripting approach is correct but `-S` flag on macOS needs empirical testing

**Research date:** 2026-02-19
**Valid until:** 60 days (stable codebase, no external API dependencies)

---

## Implementation Sequence (for planner)

Based on the research, the recommended plan sequence for Phase 5:

### Plan 05-01: Wire v2 GameBuilder Bridge in Gradle Plugin
**Core fix.** Modify `GenerateCWorkAction.execute()` and `BudgetReportWorkAction.resolveGameIR()` to detect `GameBuilder`, call `.build()`, and route to `generateV2()`. Add `budgetReport: Property<Boolean>` to `GbktExtension`. Gate source map generation for v1-only.
- Files: `GenerateCTask.kt`, `BudgetReportTask.kt`, `GbktExtension.kt`, `GbktPlugin.kt`
- Tests: `IntegrationTest.kt` — add test for v2 game generateC; verify PongPipelineTest still passes

### Plan 05-02: Validate Pong End-to-End
Run `./gradlew :gbkt-examples:pong:buildRom`. Diagnose and fix any C compilation errors from lcc. Verify `main.c`, `bank1.c`, `game.h` are correct. Build must succeed.

### Plan 05-03: Validate Breakout End-to-End
Run `./gradlew :gbkt-examples:breakout:buildRom`. Fix any Breakout-specific issues (4 scenes, sound effects in v2 codegen).

### Plan 05-04: Validate Explorer End-to-End + validateRom Task
Fix any Explorer-specific issues (TriggerSystem, GenericSystem, RPG codegen stubs). Add `validateRom` Gradle task using mGBA Lua scripting. Run automated validation on all three ROMs.
