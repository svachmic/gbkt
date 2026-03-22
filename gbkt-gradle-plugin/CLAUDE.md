# gbkt-gradle-plugin — Build Integration

Gradle plugin that orchestrates the full build pipeline: Kotlin DSL evaluation, C code generation (via backend reflection), GBDK compilation, and emulator launch. Included as a composite build (`includeBuild` in settings.gradle.kts), not a regular project dependency.

## Dependencies

- Composite build — not published or resolved as a normal dependency
- Uses `gbkt-core` and `gbkt-backend-api` at runtime via the project's classpath
- Backends (e.g. `gbkt-backend-gbdk`) are discovered reflectively through `BackendReflection`

## Key Files

| File | Role |
|------|------|
| `GbktPlugin.kt` | Entry point — creates `gbkt {}` extension, registers all 12+ tasks in `afterEvaluate` |
| `GbktExtension.kt` | DSL configuration block (`gbkt { }`) with sub-extensions: `optimization`, `emulator`, `web`, `output`, `generateAssets` |

## Task Pipeline

```
processAssets ─┐
compileKotlin ─┤
               ├─► generateC ─► convertSprites ─┐
               │                copyResources ───┤
               │                                 ├─► compileRom ─► buildRom ─► runEmulator
               │                                                      │
               │                                              validateRom / emulatorTest
               │
               └─► budgetReport (independent, requires compileKotlin only)
```

## Gradle Tasks

| Task | Command | Purpose |
|------|---------|---------|
| `generateC` | `./gradlew generateC` | Evaluate Kotlin DSL, run backend codegen, emit C files + source maps |
| `processAssets` | `./gradlew processAssets` | Incremental processing of PNG/TMX/LDtk/UGE assets |
| `convertSprites` | `./gradlew convertSprites` | Run GBDK `png2asset` on sprite PNGs referenced in generated C |
| `compileRom` | `./gradlew compileRom` | Invoke GBDK `lcc` to compile C into `.gb` ROM |
| `buildRom` | `./gradlew buildRom` | Lifecycle task — depends on `compileRom`, prints success summary |
| `runEmulator` | `./gradlew runEmulator` | Launch embedded Coffee-GB emulator (or external if configured) with live reload |
| `debugEmulator` | `./gradlew debugEmulator` | Launch embedded emulator with full debug tooling (log viewer, memory inspector) |
| `validateRom` | `./gradlew validateRom` | Headless ROM validation via embedded Coffee-GB emulator |
| `emulatorTest` | `./gradlew emulatorTest` | CI-safe headless test via embedded Coffee-GB emulator; skips if unavailable |
| `budgetReport` | `./gradlew budgetReport` | Run analysis pipeline, print ASCII ROM/RAM budget report |
| `webExport` | `./gradlew webExport` | Generate browser-playable HTML+JS wrapper around the ROM |
| `generateAssets` | `./gradlew generateAssets` | Emit type-safe Kotlin `Assets` object from asset directory |
| `copyGeneratedC` | (auto) | Copy generated C to user-visible dir (when `output.keepGeneratedC` is true) |
| `runWatch` | `./gradlew runWatch` | Build + launch emulator with live reload instructions |
| `gbktSetupClaude` | `./gradlew gbktSetupClaude` | Install/update Claude Code skills and MCP server config |
| `cleanGbkt` | `./gradlew cleanGbkt` | Delete `build/gbkt/` outputs |

## Internal Utilities

| File | Role |
|------|------|
| `BackendReflection.kt` | Discovers backends on classpath, invokes `validate()`/`generate()` reflectively |
| `GbdkToolchain.kt` | Finds GBDK installation — checks extension config, `GBDK_HOME` env, common paths |
| `ErrorEnhancer.kt` | Maps GBDK `lcc` errors back to Kotlin DSL source via source maps, adds fix suggestions |
| `GbdkErrorParser.kt` | Parses `lcc` stderr output into structured `GbdkError` objects |
| `SourceMapLoader.kt` | Loads `.gbkt.map` files to resolve C line numbers to Kotlin DSL locations |

## Common Tasks

- **Add a new Gradle task:** Create class in `tasks/`, register in `GbktPlugin.registerTasks()`
- **Add a new extension property:** Add to `GbktExtension` (or sub-extension), set convention default in `GbktPlugin.apply()`
- **Change GBDK detection logic:** Edit `GbdkToolchain.find()` / `commonPaths()`
- **Improve error messages:** Add patterns to `GbdkErrorParser`, suggestions to `ErrorEnhancer.ERROR_SUGGESTIONS`
