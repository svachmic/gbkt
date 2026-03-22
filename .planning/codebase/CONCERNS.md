# Codebase Concerns

**Analysis Date:** 2026-02-17

## Tech Debt

**Monolithic gbkt-core Module:**
- Issue: All IR nodes, DSL types, and domain constructs live in one module due to Kotlin's sealed interface constraint
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/`
- Impact: The module contains 51,882 lines across 270+ files (largest files: Monster.kt 1,323 LOC, Validation.kt 1,079 LOC, Zone.kt 975 LOC). This makes the module harder to navigate and test in isolation
- Fix approach: This is an architectural constraint, not poor design. Sealed interfaces prevent splitting IR types across modules. The trade-off is acceptable: users have single `gbkt-core` dependency, and internal complexity is mitigated via organized subdirectories and clear CLAUDE.md documentation
- Status: Resolved by design, document for future maintainers

**Large Code Generator Files:**
- Issue: Code generation files exceed complexity/method count limits (exclusions in detekt.yml lines 20-28, 58-65)
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/`
  - MonsterCodegen.kt (1,705 LOC)
  - BattleCodegen.kt (1,323 LOC)
  - StatementCodegen.kt (1,402 LOC)
- Impact: Large switch/when expressions for code generation are inherently verbose. Each IR node type maps to C output, creating method-per-node patterns
- Fix approach: This is intentional. C code generation requires method chains: IRAssign → generateAssign() → write C assignment. Breaking up would reduce readability. Consider as "acceptable complexity for code generators"
- Status: Documented in detekt.yml, monitored via Sonar

**Extensive Operator Overloading:**
- Issue: `ExpressionWrapper.kt` contains 60+ operator overloads for DSL ergonomics
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/ExpressionWrapper.kt` (280 LOC)
- Impact: Detekt excluded from TooManyFunctions check (detekt.yml line 59). Overloads enable `playerX + 5` syntax instead of `playerX.add(5)`
- Fix approach: This is a design decision, not a bug. The DSL experience is the user-facing API. Consolidate via Kotlin's operator resolution if needed, but keep ergonomics
- Status: Intentional architectural choice

## Known Bugs

**Tile-Specific Collision Not Implemented:**
- Symptoms: All walkable tiles within map bounds are treated as passable; tile attributes are not consulted
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/world/ZoneCodegen.kt:1044`
- Trigger: Any exploration game that needs per-tile collision (e.g., walls, lava)
- Workaround: Use entity-based obstacles instead of tilemap collision. Place collision entities on top of blocked tiles
- Impact: High for dungeon crawlers, low for simpler games. LabyrinthOfTheDragon-port works around this via entity collision
- Fix approach: Extend `_map_collision()` to read tilemap attribute table during exploration. Requires: (1) Tilemap attribute format in TiledParser, (2) Bank allocation for attribute table, (3) Runtime lookup in collision check
- Priority: High (blocks full RPG dungeon support)

**Battle Engine System is Experimental:**
- Symptoms: Warning emitted to stderr; API and generated C code subject to change
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/combat/BattleEngineCodegen.kt:51-54`
- Trigger: Any game using `battleEngine()` DSL
- Workaround: Use standard `battle()` system for turn-based RPG combat (stable and tested)
- Impact: Medium. Games can deploy with experimental battle engines, but upgrades may break generated code
- Status: Documented in CLAUDE.md. Battle system is v1 and suitable for most RPGs; only advanced tactics/real-time systems need custom engines
- Priority: Medium (experimental status is intentional for v1)

**InlineExecutor Limitations:**
- Symptoms: Some IR statements and expressions are not fully simulated; test execution may produce incomplete results
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/InlineExecutor.kt:77-110`
- Trigger: Tests involving scene changes, pool operations, dialogs, menus, camera, sprites, save system
- Workaround: Use `SimulationContext` for full game simulation instead of `InlineExecutor` for isolated logic tests
- Impact: Low. InlineExecutor is designed for unit tests of isolated logic (variables, loops, conditionals). Complex game logic should use SimulationContext
- Fix approach: Extend InlineExecutor with stub implementations for unsupported operations, or document clearly when SimulationContext is required
- Status: Known limitation documented in InlineExecutor.kt

## Security Considerations

**Asset Path Handling:**
- Risk: File path validation in asset references (`SpriteAsset.fromPath()`) may not prevent path traversal attacks (e.g., `../../etc/passwd.png`)
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/assets/AssetRef.kt`
- Current mitigation: Assets are used at build time (compile to ROM), not runtime. Path traversal is mitigated by build sandbox (Gradle isolation)
- Recommendations: (1) Add path normalization in `AssetRef.fromPath()` to reject `..` components, (2) Validate asset paths during Gradle asset copying, (3) Document that asset paths are sandbox-relative
- Priority: Low (build-time only, not user data)

**Localization String Injection:**
- Risk: PO file parser accepts untrusted `.po` files; malformed string data could cause buffer overflow in C code
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/assets/PoParser.kt`
- Current mitigation: String table bank allocator enforces 16KB limits per bank (BankAllocator.kt)
- Recommendations: (1) Validate string length bounds in PoParser, (2) Add size assertions in generated C tables, (3) Use GBDK's safe string functions
- Priority: Low (localization files are developer-provided, not user input)

**Gradle Plugin Shell Execution:**
- Risk: `./gradlew buildRom` invokes GBDK's `lcc` compiler via shell; malformed generated C code could expose injection vectors
- Files: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/BuildRomTask.kt`
- Current mitigation: Code generation is deterministic and validated before compilation. GBDK compiler is sandboxed
- Recommendations: (1) Validate generated C syntax before invoking lcc, (2) Use ProcessBuilder (not shell) for compiler invocation, (3) Add compiler output validation
- Priority: Low (generated code is deterministic; shell injection unlikely)

## Performance Bottlenecks

**Asset Analysis Scan (AssetAnalyzer):**
- Problem: Cross-asset tile similarity detection is O(n²) and scans all 256 unique tiles per asset
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/optimization/AssetAnalyzer.kt:~500`
- Cause: Exhaustive comparison loop for duplicate tile detection
- Improvement path: (1) Use hash-based grouping (tile data → hashes) to reduce comparisons, (2) Set configurable similarity threshold via `AnalyzerConfig.maxTilesForSimilarity`, (3) Skip analysis for small tilesets
- Current config: `maxTilesForSimilarity = 256` (unbounded by default)
- Impact: High for games with 50+ assets; low for simple games. Analysis runs at build time only
- Priority: Medium (build-time performance, not runtime)

**Monster Tier Variation Codegen:**
- Problem: MonsterCodegen generates separate sprite/palette data for each tier variation; memory usage scales with tier count
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/rpg/MonsterCodegen.kt:~1700`
- Cause: No deduplication of similar tier variations. A monster with 3 tiers = 3x VRAM usage
- Improvement path: (1) Detect identical tier sprites and share tile data, (2) Use palette swaps for color-only variations, (3) Implement delta compression for similar sprites
- Impact: High for RPGs with many tiered monsters; low for single-tier games
- Priority: Medium (blocks complex monster systems)

**Validation Full Game Scan:**
- Problem: Validation walks all scenes, entities, and statements on every `game.build()` call
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/Validation.kt` (~1,079 LOC)
- Cause: Comprehensive checks for OAM limits, WRAM usage, IR references, array bounds
- Improvement path: (1) Cache validation results, (2) Incremental validation (re-validate only changed scenes), (3) Parallel validation runs
- Impact: Low-Medium. Validation runs once per build; complex games with 100+ scenes may experience 1-2 second overhead
- Priority: Low (build-time only; caching would help large games)

## Fragile Areas

**GBDK Banking System (setBank/returnToHome):**
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt:170-180`
- Why fragile: Bank state is persistent across function calls. Forgetting `returnToHome()` after `setBank(N)` causes all subsequent codegen to inherit the wrong bank. This manifests as linker errors ("MBC5 unknown address") at ROM build time, not in Java compilation
- Safe modification: (1) Add assertions that enforce `currentBank == 0 (HOME)` at codegen boundaries, (2) Use try/finally to restore bank state, (3) Document bank switching rules in code comments, (4) Add integration tests that verify bank boundaries
- Test coverage: `ZoneCodegen.kt` tests exist but don't verify all bank transitions
- Priority: High (banking bugs are hard to debug; MEMORY.md documents known workarounds)

**Monster Sprite OAM Limitations:**
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/rpg/MonsterCodegen.kt:~10-40` (architectural notes)
  - `gbkt-core/src/main/kotlin/io/github/gbkt/core/rpg/Monster.kt:~350` (documentation)
- Why fragile: OAM has 40 sprite limit. Battles render monsters as background tiles (not OAM sprites) to stay within limit. This is correct, but the decision is not enforced at DSL level. Users can define battles with overlapping monsters that would exceed OAM if they attempted sprite rendering
- Safe modification: (1) Document that battles use tile rendering, not sprite rendering, (2) Add validation that emits warning if monster battle configuration would exceed OAM if sprite-based, (3) Create utility to convert between sprite and tile coordinates
- Test coverage: Monster rendering tests exist but don't simulate multi-entity battles
- Priority: Medium (architectural constraint, not a bug; LabyrinthOfTheDragon-port works correctly)

**Stateful RecordingContext in DSL:**
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/RecordingContext.kt` (thread-unsafe global state)
- Why fragile: DSL blocks use `RecordingContext.current` (ThreadLocal). Nested game definitions or parallel builds would corrupt context
- Safe modification: (1) Validate that only one `game { }` block is active at a time, (2) Use context managers (use { } pattern) to ensure cleanup, (3) Add runtime assertions that fail on context misuse
- Test coverage: No tests for nested game definitions or parallel builds
- Priority: Low (single-threaded build process; low risk in practice)

**Localization Bank Allocation:**
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/assets/BankAllocator.kt`
- Why fragile: Strings are allocated to banks based on msgctxt namespace. Changing a namespace or adding strings can shift bank boundaries and break ROM builds if not re-tested
- Safe modification: (1) Store bank allocation in ROM header for reference, (2) Validate bank allocation consistency across builds, (3) Add integration test that builds game with multiple localization files
- Test coverage: PoParser tests exist but don't test bank allocation edge cases (full 16KB overflow)
- Priority: Medium (complex interaction between string length and bank sizing)

## Scaling Limits

**OAM Sprite Limit (40 sprites):**
- Current capacity: 40 simultaneous visible sprites
- Limit: Game fails to display more than 40 sprites per frame (hardware limit)
- Games affected: Pong, Breakout (use 5-10 sprites), LabyrinthOfTheDragon-port (uses 15 monsters as background tiles to work around limit), complex action games (approach limit)
- Scaling path: (1) Render off-screen sprites to background tiles (current workaround), (2) Implement sprite culling to hide distant entities, (3) Use multiplexing (swap sprites each frame)
- Status: Documented in Monster.kt and MonsterCodegen.kt

**WRAM Memory Limit (6KB usable):**
- Current capacity: 6,144 bytes estimated usable WRAM (8KB total, ~2KB reserved by GBDK/stack)
- Limit: Game variables, arrays, and save data cannot exceed 6KB. Warning threshold: 5,120 bytes (83% usage)
- Games affected: Small games (Pong, Breakout < 100 bytes), medium games (LabyrinthOfTheDragon-port uses ~2KB), complex RPGs (potentially approach 5KB)
- Scaling path: (1) Use u8 instead of u16 where possible, (2) Reduce array sizes, (3) Compress save data via bit-packing, (4) Use GBDK SRAM (battery-backed cartridge RAM)
- Status: Validated in `Validation.kt:validateWRAMUsage()`. Warning and error thresholds enforced

**ROM Bank Limit (32KB per bank, typically 8-16 banks):**
- Current capacity: ~256KB ROM per game (typical GBDK project)
- Limit: Code generators split output across banks. Each bank is 16KB. Exceeding ROM size requires cart with more banks
- Games affected: Small games (< 64KB), medium games (64-256KB), complex games (300KB+, require larger cart)
- Scaling path: (1) Optimize generated C code, (2) Use GBDK compression, (3) Increase cart size (requires hardware changes)
- Status: Monitored in GBDKCodeGenerator.kt:828 (warning if bank > ~16KB)

**Palette Limit (8 sprite palettes, 8 background palettes on GBC):**
- Current capacity: 8 sprite palettes, 8 background palettes
- Limit: Game exceeds GBC constraint if more than 8 unique sprite palettes
- Games affected: Color-heavy games; LabyrinthOfTheDragon-port uses 4-6 palettes
- Scaling path: (1) Reuse palettes across similar sprites, (2) Use palette animations for variety, (3) Reduce unique colors per sprite
- Status: Validated in `Validation.kt:validatePaletteLimits()`

**Tiled Map Dimensions (32x32 tiles max):**
- Current capacity: 32x32 tile grid per map
- Limit: TiledParser enforces 32x32 max (TiledParser.kt:~100)
- Games affected: Medium maps (LabyrinthOfTheDragon-port uses 32x32), large games may need multiple maps
- Scaling path: (1) Use multiple zones/maps, (2) Procedural generation, (3) Streaming (load/unload zones dynamically)
- Status: Enforced in TiledParser.kt. LabyrinthOfTheDragon-port handles multi-map via zone system

## Dependencies at Risk

**GBDK-2020 (Game Boy Dev Kit):**
- Risk: GBDK is maintained by community volunteers; support is not guaranteed. Latest version is stable, but older versions have known compiler bugs
- Impact: If GBDK becomes unmaintained, future Kotlin code may not compile
- Migration plan: (1) Document minimal GBDK version (currently 6.0+), (2) Track GBDK releases and test compatibility, (3) Maintain fork if needed, (4) Consider supporting alternative toolchains (SDGBLib, etc.)
- Current status: GBDK-2020 is the de facto standard and actively maintained. Risk is low

**Kotlin 2.3.0:**
- Risk: Kotlin is mature; risk is low. However, gbkt-gradle-plugin uses Kotlin DSL features that may change
- Impact: If Kotlin breaks sealed interface exhaustiveness or operator overloading, ExpressionWrapper.kt would need rewrite
- Migration plan: (1) Pin Kotlin version in gradle.properties, (2) Test with Kotlin 2.4+ betas, (3) Use deprecated API suppressions for safe upgrades
- Current status: Kotlin 2.3.0 is stable. No immediate risk

**Gradle 9.0:**
- Risk: Gradle is actively developed; plugin API changes are common but backward compatible
- Impact: If Gradle 10.0 breaks plugin APIs, GbktExtension.kt and tasks would need updates
- Migration plan: (1) Monitor Gradle releases, (2) Test with RC versions, (3) Use Gradle wrapper to pin version
- Current status: Gradle 9.0 is stable. Wrapper-based (gradle/wrapper/gradle-wrapper.jar) so upgrades are explicit

**IntelliJ Plugin API:**
- Risk: IntelliJ SDK is tightly coupled to IDE versions; breaking changes are common (every 2 months with new IDE releases)
- Impact: IntelliJ plugin may fail to load with IDE 2024.3+ if not updated
- Migration plan: (1) Pin IntelliJ SDK version in build.gradle.kts (currently IDE version 243.x), (2) Test against multiple IDE versions, (3) Use deprecation inspections
- Current status: Plugin supports IDE 2024.1 - 2024.3. Risk is medium (IDE vendor, not open source)

## Missing Critical Features

**Tile-Specific Collision Attributes:**
- Problem: Exploration games cannot detect walkability per-tile; all tiles within map bounds are passable
- Blocks: Complex dungeon designs with walls, lava, bridges, etc. Current workaround: place entity obstacles
- Priority: High (fundamental dungeon feature)
- Effort: Medium (requires TiledParser and codegen changes)
- Related: ZoneCodegen.kt:1044 TODO comment

**Advanced Monster Rendering:**
- Problem: Monsters render as background tiles, not OAM sprites. Limits animation, scaling, and visual effects
- Blocks: Dynamic monster status displays, sprite-based attacks, particle effects
- Priority: Medium (nice-to-have for v1; acceptable workaround exists)
- Effort: High (requires rearchitecting monster rendering pipeline)

**Multi-Engine Battle Selection:**
- Problem: `battleEngine()` is experimental; multi-engine routing is a TODO (BattleEngineCodegen.kt:stub for future routing)
- Blocks: Games with multiple battle types (turn-based, real-time, tactical)
- Priority: Low (edge case; most games use single battle type)
- Effort: High (complex state machine for engine selection)

**Procedural Map Generation:**
- Problem: Maps must be pre-authored in Tiled; no procedural generation support
- Blocks: Roguelikes, infinite dungeons
- Priority: Low (v1 focus is hand-crafted content)
- Effort: High (requires dungeon generation DSL and integration)

**Sprite Sheet Auto-Slicing:**
- Problem: Sprite sheets must be pre-sliced into tiles; automatic tile extraction is not supported
- Blocks: Asset pipeline cannot auto-convert sprite sheets to Game Boy format
- Priority: Low (asset preparation is typically manual)
- Effort: Medium (texture atlas packing algorithm)

## Test Coverage Gaps

**Banking System Integration:**
- What's not tested: Full codegen flow with multiple banks, bank boundary transitions, bank overflow scenarios
- Files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt` (banking state machine)
- Risk: Banking bugs only surface at ROM link time, not in Java tests. Hard to debug without full ROM build
- Priority: High (critical for stability)

**Parallel Builds / Nested DSL:**
- What's not tested: Two `game { }` blocks running concurrently; nested DSL definitions
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/RecordingContext.kt` (thread-unsafe context)
- Risk: RecordingContext.current is ThreadLocal but not validated. Parallel Gradle workers could corrupt state
- Priority: Medium (low risk in practice, but catastrophic if it happens)

**Large ROM Compilation:**
- What's not tested: Games > 128KB; monsters with > 5 tier variations; games with > 100 scenes
- Files: All codegen modules
- Risk: Unknown scaling issues with large projects. Current largest game is LabyrinthOfTheDragon-port (~80KB ROM)
- Priority: Medium (needed for complex RPGs)

**Localization Edge Cases:**
- What's not tested: PO files with 100+ strings per namespace; string length > 90 characters; multi-byte UTF-8 characters
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/assets/PoParser.kt`, BankAllocator.kt
- Risk: Bank overflow, string truncation in generated C code
- Priority: Medium (edge case for i18n)

**Array Bounds on Nested Structures:**
- What's not tested: Array indices in nested loops, complex expressions with array access (e.g., `items[counter[i]]`)
- Files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/validation/ArrayBoundsValidation.kt`
- Risk: Complex nested access patterns are marked "unchecked" and may fail at runtime
- Priority: Medium (advanced game logic patterns)

---

*Concerns audit: 2026-02-17*
