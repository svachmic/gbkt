# Phase 3: Asset Pipeline and JVM Test Runner - Research

**Researched:** 2026-02-18
**Domain:** Asset processing (PNG/TMX/LDtk), Gradle incremental tasks, JVM ScriptOp interpreter
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Asset input formats**
- Support both TMX (Tiled) and LDtk map formats
- Sprite sheet frame layout declared in Kotlin DSL alongside sprite() calls — no separate metadata files
- Palette enforcement: both strict and auto-quantize modes available, default to auto-quantize
- Collision layers identified by custom property flag (`gbkt_collision=true`) on any layer, not by naming convention

**Build integration**
- Incremental builds from the start — use Gradle UP-TO-DATE checking with proper input/output annotations
- Asset validation failures fail the build immediately on first invalid asset (clear error with file path and issue)
- Processed asset output goes to `build/generated/assets/` — separate from C output, consumed by codegen as intermediate artifacts
- processAssets writes a JSON manifest (`asset-manifest.json`) listing all processed assets with metadata (tile count, palette, dimensions) for codegen to consume

**Test API design**
- Both frame-by-frame stepping (`sim.advanceFrames(60)`) and run-until-condition (`sim.runUntil { score >= 10 }`) available on SimulationContext
- Both low-level input (press/release per frame) and high-level convenience (`sim.tap(A)`, `sim.holdDpad(RIGHT, frames=30)`) available
- Standard JUnit 5 assertions work out of the box; SimulationContext also exposes helper methods like `sim.assertVar("score", 10)` for convenience
- Optional frame-by-frame trace log via `sim.enableTracing()` — records state changes, printed on test failure for debugging

**Coverage scope**
- Full game simulation on JVM — variables, scenes, entities, collisions, input all simulated
- Hardware-dependent features (VRAM writes, OAM, bank switches) use no-op stubs — tests verify logic, not rendering
- Collision detection runs on JVM — bounding box collision simulated so tests can verify collision triggers
- Each example game has 2-3 scenario-based test assertions (e.g., Pong: ball bounces off paddle, score increments), not just smoke tests

### Claude's Discretion
- LDtk parser implementation details (XML vs JSON parsing approach)
- Exact auto-quantize algorithm for palette mapping
- SimulationContext internal architecture (interpreter loop, state management)
- Specific test scenarios per example game (choosing which 2-3 behaviors to test)
- Frame trace log format and verbosity

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ASSET-01 | PNG → 2bpp tile data with deduplication and palette mapping | AssetPipeline.kt already implements the full conversion pipeline. Work is: extract into a standalone Gradle-integrated processor, add tile deduplication (currently missing), produce JSON manifest entries. |
| ASSET-02 | TMX/LDtk → tilemap IR with tile indices and collision layers | TiledParser.kt already handles TMX JSON format. Work is: wire collision layer detection via `gbkt_collision=true` custom property (currently name-based), add LDtk JSON parser, produce TilemapIR output with collision mask. |
| ASSET-03 | Sprite sheet slicing into frames with animation metadata | Frame layout comes from DSL (ActorIR.sprite.size + hitbox). Work is: slice the loaded SpriteSheet into sub-frames using those dimensions, produce SpriteSheetIR per actor. |
| ASSET-04 | Integrated into Gradle as a build task | ProcessAssetsTask.kt exists but is a stub (writes .processed markers, not actual tile data). Work is: replace stub with real processing logic: PNG → 2bpp tiles, TMX/LDtk → collision data, write `asset-manifest.json`. |
| TEST-01 | JVM test runner interprets ScriptOp without emulator | SimulationContext.kt interprets the OLD IR (IRStatement). There is NO v2 ScriptOp interpreter yet. Work is: build a new `ScriptOpInterpreter` for the v2 sealed hierarchy, or extend the v2 `GameIR` path through the existing SimulationContext. |
| TEST-02 | SimulationContext API for scene loading, input simulation, state inspection | TestScope.kt + SimulationContext.kt already implement this fully for OLD IR. Work is: add a v2-compatible entry point wrapping the same API surface, adding `sim.assertVar()`, `sim.tap()`, `sim.holdDpad()`, `sim.enableTracing()`. |
| TEST-03 | Game logic tests run in under 5 seconds | All three example games use the v2 DSL (PongV2.kt, BreakoutV2.kt, ExplorerV2.kt). The existing PongIRTest and BreakoutIRTest only validate IR structure, not game logic. Scenario-based tests (ball bounces, score increments) must be added. |
</phase_requirements>

---

## Summary

Phase 3 involves completing two partially-built systems and wiring them together. The asset pipeline foundation (`AssetPipeline.kt`, `TiledParser.kt`, `ProcessAssetsTask.kt`) is already present in the codebase but is not yet end-to-end complete: the Gradle task only writes marker files, no JSON manifest is produced, tile deduplication is absent, and the LDtk parser does not exist. The test infrastructure also has a fundamental gap: the existing `SimulationContext` interprets old IR (`IRStatement`), while the new v2 DSL produces `ScriptOp` (from `GameIR`). No v2 interpreter exists.

The critical architectural decision for Phase 3 is how to bridge the v2 ScriptOp IR into a simulation context. The cleanest path is a new `ScriptOpInterpreter` class that mirrors the existing `SimulationContext` logic but operates on `v2.ScriptOp` and `v2.GameIR`. Since `ScriptOp` covers 28 sealed subtypes versus the ~130 in old IR, this is substantially smaller. The v2 games (pong, breakout, explorer) are already defined and verifiable.

For the asset pipeline, the decisions are simpler: promote `ProcessAssetsTask` from stub to real processor, extract tile deduplication logic into a `TileDeduplicator` utility, extend `TiledParser` to read `properties` arrays for `gbkt_collision=true`, write a `LdtkParser` (LDtk uses JSON, the same `org.json:json` library already in use), and write the JSON manifest using `JSONObject` (no new dependency).

**Primary recommendation:** Build the v2 ScriptOp interpreter first (Plan 03-03), because it unblocks writing meaningful game logic tests that exercise the asset pipeline's output indirectly. Then complete the asset pipeline Gradle task (Plans 03-01, 03-02), then write the example game tests (Plan 03-04).

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `javax.imageio.ImageIO` | JDK 21 (built-in) | PNG reading and BufferedImage | Zero-dependency; already used in AssetPipeline.kt |
| `org.json:json` | 20251224 | JSON parsing for TMX JSON and LDtk | Already declared in `gbkt-core/build.gradle.kts`; used in TiledParser.kt |
| Gradle `@Incremental` + `InputChanges` | Gradle 9.0 | Incremental task processing | Already used in ProcessAssetsTask.kt skeleton |
| `kotlin.test` + JUnit 5 | Kotlin 2.3.0 / JUnit Platform | Test framework | Already used in all example game test classes |
| `@CacheableTask` + `@InputDirectory` + `@OutputFile` | Gradle 9.0 | Build caching and UP-TO-DATE checking | Already applied in ProcessAssetsTask.kt and GenerateAssetsTask.kt |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Gradle `@PathSensitive(RELATIVE)` | Gradle 9.0 | Incremental fingerprinting — only changed bytes trigger rebuild | Applied to all `@InputDirectory` annotations |
| `kotlinx.serialization` or `org.json` | Already present | JSON manifest writing | Use `org.json.JSONObject` (already a dependency) rather than adding kotlinx.serialization |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `org.json.JSONObject` for manifest | `kotlinx.serialization` | kotlinx.serialization adds a compile plugin dependency; `org.json` is already declared, sufficient for writing a simple manifest |
| Custom LDtk parser | LDtk Kotlin library | No maintained Kotlin LDtk library exists on Maven Central (verified via search); implement a focused parser using `org.json` that covers the subset needed (layers, tilesets, collision flag) |
| JUnit 5 via `@Test` | kotest | kotest is already in libs for property testing; for scenario-based tests, standard JUnit 5 is simpler and already used in the examples |

---

## Architecture Patterns

### Recommended Project Structure

The phase adds files to existing locations — no new modules:

```
gbkt-core/src/main/kotlin/io/github/gbkt/core/
├── AssetPipeline.kt          (existing — extend with tile deduplication)
├── TiledParser.kt            (existing — extend to read layer custom properties)
├── LdtkParser.kt             (new — mirrors TiledParser interface, LDtk JSON)
├── TileDeduplicator.kt       (new — dedup logic extracted from pipeline)
└── test/
    └── ScriptOpInterpreter.kt (new — v2 ScriptOp execution engine)

gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/
└── ProcessAssetsTask.kt       (existing stub — replace with real logic)

gbkt-examples/pong/src/test/.../PongGameTest.kt    (new — scenario tests)
gbkt-examples/breakout/src/test/.../BreakoutGameTest.kt (new — scenario tests)
gbkt-examples/explorer/src/test/.../ExplorerGameTest.kt (new — scenario tests)
```

### Pattern 1: Tile Deduplication with Index Map

```kotlin
// Source: derived from AssetPipeline.kt existing pattern
class TileDeduplicator {
    private val uniqueTiles = mutableListOf<AssetPipeline.Tile>()
    private val tileIndex = mutableMapOf<ByteArrayKey, Int>()

    fun deduplicate(tiles: List<AssetPipeline.Tile>): Pair<List<AssetPipeline.Tile>, IntArray> {
        val indexMap = IntArray(tiles.size)
        for ((i, tile) in tiles.withIndex()) {
            val key = ByteArrayKey(tile.data)
            indexMap[i] = tileIndex.getOrPut(key) {
                val idx = uniqueTiles.size
                uniqueTiles.add(tile)
                idx
            }
        }
        return uniqueTiles to indexMap
    }
}
```

ByteArrayKey wraps a ByteArray with proper `equals`/`hashCode` based on `contentEquals`/`contentHashCode` — the same pattern used in `AssetPipeline.Tile.equals()`.

### Pattern 2: Gradle Incremental Task (proven pattern from ProcessAssetsTask.kt)

```kotlin
@CacheableTask
abstract class ProcessAssetsTask @Inject constructor() : DefaultTask() {

    @get:Incremental
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:OutputFile abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun processAssets(inputChanges: InputChanges) {
        if (inputChanges.isIncremental) {
            // Only process changed files
            inputChanges.getFileChanges(assetDirectory).forEach { change ->
                when (change.changeType) {
                    ChangeType.ADDED, ChangeType.MODIFIED -> processFile(change.file)
                    ChangeType.REMOVED -> removeOutput(change.file)
                }
            }
        } else {
            // Full rebuild
            processAll()
        }
        writeManifest() // Always write manifest after processing
    }
}
```

The stub already has this structure. The work is filling in `processFile()` and `writeManifest()`.

### Pattern 3: JSON Manifest Schema

The manifest bridges the Gradle task boundary. Structure (written via `org.json.JSONObject`):

```json
{
  "version": 1,
  "assets": [
    {
      "path": "sprites/ball.png",
      "type": "SPRITE",
      "tileCount": 1,
      "uniqueTileCount": 1,
      "widthInTiles": 1,
      "heightInTiles": 1,
      "palette": [0, 85, 170, 255]
    },
    {
      "path": "maps/level1.tmx",
      "type": "TILEMAP",
      "width": 20,
      "height": 18,
      "hasCollision": true,
      "tilesetPath": "sprites/dungeon.png"
    }
  ]
}
```

### Pattern 4: v2 ScriptOp Interpreter

The existing `SimulationContext` (800+ lines) handles old `IRStatement`. The v2 interpreter is simpler because `ScriptOp` has only 28 subtypes vs ~130. The v2 interpreter should:

1. Accept `GameIR` (v2 root node)
2. Maintain state: `variables: MutableMap<String, Long>`, `actorPositions: MutableMap<String, Pair<Int,Int>>`, `currentScene: String`
3. Execute `ScriptOp` via exhaustive `when` matching
4. Expose `SimulationContextV2` with the same API surface the locked decisions describe

```kotlin
// New class — does NOT extend or modify old SimulationContext
class ScriptOpInterpreter(private val game: GameIR) {

    private val variables = mutableMapOf<String, Long>()
    private val actorPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var currentSceneId: String = game.startScene ?: game.scenes.first().id
    private var frameCount = 0

    // Input state (joypad bitmask, same as old SimulationContext)
    var joypad: Int = 0
    var joypadPrev: Int = 0

    fun executeFrame() {
        val scene = game.scenes.first { it.id == currentSceneId }
        scene.frameOps.forEach { executeOp(it) }
        frameCount++
    }

    private fun executeOp(op: ScriptOp) = when (op) {
        is Assign -> executeAssign(op)
        is IfOp -> executeIf(op)
        is WhileOp -> executeWhile(op)
        is ForOp -> executeFor(op)
        is NavigateTo -> navigateTo(op.sceneId)
        is SetPosition -> setActorPosition(op)
        is MoveBy -> moveActorBy(op)
        is WaitFrames -> { /* no-op in simulation */ }
        is PlaySound, is ShowDialog, is ShowMenu, is PrintOp,
        is FadeOp, is SetVisible, is SpawnActor, is DestroyActor,
        is AnimateOp, is CameraOp, is CallOp, is ReturnOp,
        is MathOp, is RawOp, is ArrayAssign, is TriggerSystem -> { /* no-op stub */ }
    }
}
```

### Pattern 5: SimulationContextV2 Public API (matching locked decisions)

```kotlin
class SimulationContextV2(game: GameIR) {
    private val interpreter = ScriptOpInterpreter(game)

    fun advanceFrames(count: Int) = repeat(count) { interpreter.executeFrame() }

    fun runUntil(maxFrames: Int = 600, predicate: SimulationContextV2.() -> Boolean) {
        var frames = 0
        while (!predicate() && frames < maxFrames) {
            interpreter.executeFrame()
            frames++
        }
    }

    fun tap(button: GameBoyButton) { /* press for 1 frame, release */ }
    fun holdDpad(direction: DpadDirection, frames: Int) { /* hold for N frames */ }

    fun assertVar(name: String, expected: Int) {
        val actual = getVar(name)
        if (actual != expected) throw AssertionError("Expected $name=$expected but was $actual")
    }

    fun getVar(name: String): Int = interpreter.getVariable(name)
    val currentScene: String get() = interpreter.currentSceneId
    val frameCount: Int get() = interpreter.frameCount

    fun enableTracing() { interpreter.tracingEnabled = true }
}
```

### Pattern 6: LDtk Parser (JSON-based, matches locked decision)

LDtk exports are pure JSON (`.ldtk` extension). The format uses `org.json.JSONObject` like TiledParser. The relevant structure for Phase 3:

```
LDtk JSON root:
  └── levels[]
        └── layerInstances[]
              ├── __type: "Tiles" | "IntGrid" | "Entities"
              ├── __identifier: layer name
              ├── intGridCsv[]: flat array of int grid values (0 = empty)
              ├── gridTiles[]: {px, t, f} tile placement entries
              └── fieldInstances[]: custom fields (for gbkt_collision flag)
```

The `gbkt_collision=true` flag detection: for TMX, look in layer `properties` array for `{name: "gbkt_collision", value: true}`; for LDtk, look in layer `fieldInstances` for `{__identifier: "gbkt_collision", __value: true}` or use `__type == "IntGrid"` as collision layer convention (discretion call: recommend the `fieldInstances` approach to stay consistent with the TMX decision).

### Anti-Patterns to Avoid

- **Stub manifest format**: The current `ProcessAssetsTask` writes a pipe-delimited text manifest. The decision requires JSON (`asset-manifest.json`). Do not extend the existing text format.
- **Collision layer by name**: The current `loadTileMap()` function in `AssetPipeline.kt` takes `collisionLayerName: String?`. The decision is to use `gbkt_collision=true` custom property, not naming convention. The TMX parser `TiledParser.parseLayers()` must be extended to parse `properties` arrays.
- **Extending old SimulationContext for v2**: The old `SimulationContext` takes `Game` (old IR root). Do not try to adapt it to accept `GameIR`. Build a separate `ScriptOpInterpreter` / `SimulationContextV2`.
- **Single full-rebuild always**: Do not skip the `@Incremental` annotation or always call `processAll()`. The Gradle UP-TO-DATE checking depends on correct output tracking.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PNG decoding | Custom PNG parser | `javax.imageio.ImageIO` (JDK 21 built-in) | PNG parsing is complex (gamma, interlacing, color profiles); ImageIO handles it; already used in AssetPipeline.kt |
| JSON parsing for TMX/LDtk | Custom JSON parser | `org.json:json` (already a declared dependency) | Handles all edge cases; already imported in TiledParser.kt |
| Gradle UP-TO-DATE checking | Custom file hash tracking | Gradle `@Incremental` API with `InputChanges` | Gradle's incremental API handles file fingerprinting, caching, and directory watching correctly |
| Color quantization | Custom median-cut or k-means | Euclidean nearest-neighbor to extracted palette | For 4-color Game Boy palette, nearest-neighbor in RGB space is sufficient and already implemented in `AssetPipeline.rgbToPaletteIndex()` |

**Key insight:** The hardest-looking problems (PNG decoding, JSON parsing, incremental builds) are already solved by the codebase or the JDK. The real work is plumbing: reading the right fields from Tiled/LDtk JSON, transforming tile arrays into the IR, and writing a clean JSON manifest.

---

## Common Pitfalls

### Pitfall 1: TMX Layer Custom Properties Not Parsed

**What goes wrong:** The current `TiledParser.parseLayers()` only reads `name`, `width`, `height`, `data`, and `visible` from each layer JSON object. It discards the `properties` array entirely. Passing a collision layer name to `loadTileMap()` currently works because it searches by name — but the locked decision requires detection via `gbkt_collision=true` custom property.

**Why it happens:** The property was added to the TiledLayer model later than the parser was written.

**How to avoid:** Extend `TiledLayer` to include `properties: Map<String, Any>` and update `parseLayers()` to read the `properties` JSONArray. Then `AssetPipeline.loadTileMap()` can find collision layers by `layer.properties["gbkt_collision"] == true`.

**Warning signs:** Tests that pass a `collisionLayerName` explicitly work, but tests using `gbkt_collision=true` properties silently produce no collision data.

### Pitfall 2: ProcessAssetsTask Output Directory Mismatch

**What goes wrong:** The current `ProcessAssetsTask` writes to `build/gbkt/processed-assets/`. The locked decision is `build/generated/assets/`. The `GenerateCTask` reads from `processedAssetsDir` which is wired to `processAssets.flatMap { it.outputDirectory }`. If the manifest is written to the wrong path or with wrong filename (`asset-manifest.txt` vs `asset-manifest.json`), codegen silently sees no assets.

**How to avoid:** Change the plugin's `GbktPlugin.kt` wiring to point `manifestFile` to `build/generated/assets/asset-manifest.json`.

### Pitfall 3: v2 ScriptOp `when` Not Exhaustive

**What goes wrong:** `ScriptOp` is a sealed interface with 28 subtypes. A `when(op)` without `else` will fail to compile if any subtype is missing. During development, adding `else -> {}` silently skips unhandled ops.

**How to avoid:** Never use `else` in the `when(op)` dispatch in `ScriptOpInterpreter`. Let the compiler enforce exhaustiveness. Mark unimplemented ops with a `/* no-op stub */` comment like the old `SimulationContext` does.

### Pitfall 4: Tile Deduplication Breaks Animation Frame Order

**What goes wrong:** `TileDeduplicator` produces a `uniqueTiles` list in first-occurrence order and an `indexMap` array. If the caller uses `uniqueTiles` for rendering and `indexMap` for tilemap data, but outputs them in wrong order, tile indices in the tilemap will point to wrong tiles.

**How to avoid:** The deduplicator returns both the deduplicated tile list and the index-mapping array. The codegen must use the index-mapping array to rewrite the tilemap data, not the original tile indices.

### Pitfall 5: 5-Second Test Budget

**What goes wrong:** Example game tests run against the `game("Pong")` v2 DSL which calls `pongV2.build()` at test initialization. If `build()` is slow or tests advance too many frames (e.g., `advanceFrames(3600)`), the 5-second budget across three games fails in CI.

**How to avoid:** Keep scenarios tight — 30-120 frames per scenario. Structure tests so `build()` is called once per test class (companion object or `@BeforeAll`). The v2 `game {}` lambda is pure Kotlin — no I/O — so `build()` should be fast.

### Pitfall 6: LDtk Format Version Drift

**What goes wrong:** LDtk has changed its JSON schema across versions. A parser targeting format `1.2.x` may break on `1.5.x` content.

**How to avoid:** Pin to the LDtk JSON format version field. The root JSON has a `jsonVersion` field. Validate it at parse time and emit a clear error if unsupported. For Phase 3, implement against LDtk format version `1.5.3` (current stable as of early 2026).

---

## Code Examples

### TMX Layer Custom Property Parsing

```kotlin
// Extension to existing TiledParser.parseLayers()
// Source: Tiled JSON format spec — https://doc.mapeditor.org/en/stable/reference/json-map-format/
private fun parseLayers(layersJson: JSONArray): List<TiledLayer> {
    return (0 until layersJson.length())
        .map { layersJson.getJSONObject(it) }
        .filter { it.getString("type") == "tilelayer" }
        .map { layerJson ->
            // Parse custom properties
            val properties = mutableMapOf<String, Any>()
            val propsArray = layerJson.optJSONArray("properties")
            if (propsArray != null) {
                for (i in 0 until propsArray.length()) {
                    val prop = propsArray.getJSONObject(i)
                    val name = prop.getString("name")
                    val type = prop.optString("type", "string")
                    val value: Any = when (type) {
                        "bool" -> prop.getBoolean("value")
                        "int" -> prop.getInt("value")
                        "float" -> prop.getDouble("value")
                        else -> prop.getString("value")
                    }
                    properties[name] = value
                }
            }

            TiledLayer(
                name = layerJson.getString("name"),
                width = layerJson.getInt("width"),
                height = layerJson.getInt("height"),
                data = parseLayerData(layerJson.getJSONArray("data")),
                visible = layerJson.optBoolean("visible", true),
                properties = properties,          // NEW field
            )
        }
}
```

### LDtk Parser (minimal — collision layer detection)

```kotlin
// New file: LdtkParser.kt
// Source: LDtk JSON format — https://ldtk.io/json/
object LdtkParser {

    data class LdtkMap(
        val tileSize: Int,
        val layers: List<LdtkLayer>,
    )

    data class LdtkLayer(
        val identifier: String,
        val type: String,    // "Tiles", "IntGrid", "Entities", "AutoLayer"
        val gridSize: Int,
        val cWid: Int,        // width in cells
        val cHei: Int,        // height in cells
        val intGridCsv: List<Int>,  // flat array, 0=empty for IntGrid layers
        val gridTiles: List<LdtkTilePlacement>,
        val isCollision: Boolean,   // true if fieldInstances has gbkt_collision=true
    )

    data class LdtkTilePlacement(val px: Pair<Int, Int>, val t: Int)

    fun parse(file: File): LdtkMap {
        val json = JSONObject(file.readText())
        val tileSize = json.getInt("defaultGridSize")
        val levels = json.getJSONArray("levels")
        val level = levels.getJSONObject(0) // Phase 3: single-level support
        val layersJson = level.getJSONArray("layerInstances")

        val layers = (0 until layersJson.length()).map { i ->
            val layer = layersJson.getJSONObject(i)
            parseLayer(layer)
        }

        return LdtkMap(tileSize, layers)
    }

    private fun parseLayer(layer: JSONObject): LdtkLayer {
        val identifier = layer.getString("__identifier")
        val type = layer.getString("__type")
        val gridSize = layer.getInt("__gridSize")
        val cWid = layer.getInt("__cWid")
        val cHei = layer.getInt("__cHei")

        // IntGrid data
        val intGridCsvArray = layer.optJSONArray("intGridCsv")
        val intGridCsv = if (intGridCsvArray != null) {
            (0 until intGridCsvArray.length()).map { intGridCsvArray.getInt(it) }
        } else emptyList()

        // Tile placements
        val gridTilesJson = layer.optJSONArray("gridTiles") ?: layer.optJSONArray("autoLayerTiles")
        val gridTiles = if (gridTilesJson != null) {
            (0 until gridTilesJson.length()).map { i ->
                val t = gridTilesJson.getJSONObject(i)
                val pxArray = t.getJSONArray("px")
                LdtkTilePlacement(pxArray.getInt(0) to pxArray.getInt(1), t.getInt("t"))
            }
        } else emptyList()

        // Detect collision via fieldInstances
        val fieldInstances = layer.optJSONArray("fieldInstances")
        val isCollision = if (fieldInstances != null) {
            (0 until fieldInstances.length()).any { i ->
                val field = fieldInstances.getJSONObject(i)
                field.optString("__identifier") == "gbkt_collision" &&
                    field.optBoolean("__value", false)
            }
        } else false

        return LdtkLayer(identifier, type, gridSize, cWid, cHei, intGridCsv, gridTiles, isCollision)
    }
}
```

### JSON Manifest Writing

```kotlin
// Inside ProcessAssetsTask.writeManifest()
// Source: org.json API — https://stleary.github.io/JSON-java/
private fun writeManifest(entries: List<AssetManifestEntry>, outputDir: File) {
    val root = JSONObject()
    root.put("version", 1)
    val assets = JSONArray()
    for (entry in entries) {
        val obj = JSONObject()
        obj.put("path", entry.path)
        obj.put("type", entry.type.name)
        when (entry) {
            is SpriteManifestEntry -> {
                obj.put("tileCount", entry.tileCount)
                obj.put("uniqueTileCount", entry.uniqueTileCount)
                obj.put("widthInTiles", entry.widthInTiles)
                obj.put("heightInTiles", entry.heightInTiles)
                obj.put("palette", JSONArray(entry.palette))
            }
            is TilemapManifestEntry -> {
                obj.put("width", entry.width)
                obj.put("height", entry.height)
                obj.put("hasCollision", entry.hasCollision)
                if (entry.tilesetPath != null) obj.put("tilesetPath", entry.tilesetPath)
            }
        }
        assets.put(obj)
    }
    root.put("assets", assets)
    File(outputDir, "asset-manifest.json").writeText(root.toString(2))
}
```

### v2 Scenario Test Pattern

```kotlin
// In gbkt-examples/pong/src/test/.../PongGameTest.kt
class PongGameTest {
    private val ir = pongV2.build()
    private val sim = SimulationContextV2(ir)

    @Test
    fun `ball bounces off top wall`() {
        sim.enterScene("game")
        // Force ball to top wall position
        sim.setVar("ball.y", 14)
        sim.setVar("ballDy", -1)
        sim.advanceFrames(1)
        assertEquals(1, sim.getVar("ballDy"))  // direction reversed
    }

    @Test
    fun `p1 scores when ball exits left`() {
        sim.enterScene("game")
        sim.setVar("ball.x", 4)
        sim.setVar("p1Score", 0)
        sim.setVar("p2Score", 0)
        sim.advanceFrames(2)
        assertEquals(0, sim.getVar("p1Score"))
        assertEquals(1, sim.getVar("p2Score"))  // P2 scores when ball exits left
    }

    @Test
    fun `win condition triggers scene change to gameover`() {
        sim.enterScene("game")
        sim.setVar("p1Score", 4)
        sim.setVar("ball.x", 160) // trigger P1 scores
        sim.advanceFrames(1)
        assertEquals("gameover", sim.currentScene)
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ProcessAssetsTask` writes `.processed` marker files, no actual tile data | Will become real tile processor outputting `.2bpp` binary + JSON manifest | Phase 3 | Codegen can read pre-processed tile data from manifest rather than reprocessing at C generation time |
| Collision layers identified by string name parameter to `loadTileMap()` | Collision layers identified by `gbkt_collision=true` custom property | Phase 3 | No naming convention needed; works with Tiled GUI property editor |
| No v2 ScriptOp interpreter — only old IRStatement-based SimulationContext | New `ScriptOpInterpreter` for v2 `GameIR` | Phase 3 | All three example games defined in v2 DSL can have logic tests |
| Example tests validate IR structure only (PongIRTest, BreakoutIRTest) | Example tests validate game logic behavior (ball bounces, score increments) | Phase 3 | CI enforces game logic correctness without GBDK |

**Deprecated/outdated:**
- `ProcessAssetsTask.processSprite()` stub: Creates `.processed` marker file, writes `lastModified` timestamp. Replace entirely — the stub is only a tracking placeholder.
- `AssetPipeline.loadTileMap(collisionLayerName: String?)` parameter: Keep for backward compat but route via property detection when `collisionLayerName == null`.

---

## Open Questions

1. **Pong/Breakout example actor position variables**
   - What we know: The v2 `ActorIR` stores `position: PositionDef` (initial position), but `ScriptOp.SetPosition` and `ScriptOp.MoveBy` operate on actor IDs. The interpreter needs a mutable position map.
   - What's unclear: Are actor positions accessed as `ball.x`/`ball.y` string variable names (as used in PongV2.kt: `varRef("ball.x")`), or via the actor ID map?
   - Recommendation: In PongV2.kt, `assign("ball.x", ...)` and `varRef("ball.x")` treat actor positions as variables named `"<actorId>.x"` and `"<actorId>.y"`. The interpreter should initialize these from `GameIR.actors[].position` and `SetPosition`/`MoveBy` should update the corresponding variable entries. This is consistent with how old `SimulationContext` tracks sprite position variables.

2. **Sprite sheet frame slicing vs. tile data**
   - What we know: ASSET-03 requires "sprite sheet slicing into frames with animation metadata stored in SpriteSheetIR". But `SpriteSheetIR` is not defined anywhere in the codebase — only `AssetPipeline.SpriteSheet` exists.
   - What's unclear: Is `SpriteSheetIR` a new IR node to add to `gbkt-core/ir/`, or is it metadata stored in the JSON manifest only?
   - Recommendation: Do not add a new sealed IR subtype (maintains the monolithic IR constraint). Instead, store frame metadata in the JSON manifest entry for sprite assets: `frameWidth`, `frameHeight`, `frameCount`. The existing `SpriteDef.size` in `ActorIR` provides the frame dimensions, and the asset pipeline can compute `frameCount = (sheetWidth / frameWidth) * (sheetHeight / frameHeight)`.

3. **Explorer example test scenarios**
   - What we know: ExplorerV2 includes RPG combat via `gbkt-rpg` DSL builders (character, monster, simpleBattle). The combat system emits `TriggerSystem("combat")` ScriptOps which the interpreter stubs as no-ops.
   - What's unclear: Can meaningful scenario tests be written for Explorer without simulating RPG combat?
   - Recommendation: Focus Explorer tests on gameplay scene logic: movement clamping (player can't walk off screen), torch depletion (torchLevel decrements each frame), pause scene navigation. Skip combat testing — `TriggerSystem` is a no-op stub, which is explicitly acceptable per the locked decision (hardware-dependent features use no-op stubs).

---

## Sources

### Primary (HIGH confidence)
- Codebase direct inspection: `AssetPipeline.kt` — full PNG-to-2bpp pipeline, GBC palette extraction, `rgbToGBColor()`, `convertTile()`, `extractPalette()`, existing `processAssets()` function
- Codebase direct inspection: `TiledParser.kt` — TMX JSON parsing, `TiledLayer`, `TiledTileset`, `normalizeLayer()`
- Codebase direct inspection: `ProcessAssetsTask.kt` — Gradle `@Incremental` + `InputChanges` pattern, `@CacheableTask`, manifest stub
- Codebase direct inspection: `SimulationContext.kt` — full old IR interpreter, variable map, pool simulation, input joypad model
- Codebase direct inspection: `TestScope.kt` — `advanceFrames()`, `advanceUntil()`, `tap()`, `press()`, `hold()`, `expect()` — the full existing API
- Codebase direct inspection: `ScriptOp.kt` — all 28 sealed subtypes that the v2 interpreter must handle
- Codebase direct inspection: `PongV2.kt`, `BreakoutV2.kt`, `ExplorerV2.kt` — the three games whose logic tests must pass
- Codebase direct inspection: `PongIRTest.kt`, `BreakoutIRTest.kt` — the existing structural tests (not logic tests)
- Codebase direct inspection: `GbktPlugin.kt` — task wiring, manifest path currently `gbkt/asset-manifest.txt`
- LDtk JSON format spec: https://ldtk.io/json/ (format version 1.5.x)

### Secondary (MEDIUM confidence)
- Tiled map editor JSON format: https://doc.mapeditor.org/en/stable/reference/json-map-format/ — custom properties array structure confirmed in Tiled docs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — No new libraries needed; all identified tools are already in the project
- Architecture: HIGH — All patterns derived from existing codebase code, not assumptions
- Pitfalls: HIGH — Each pitfall directly observed in current code (stub manifest, missing properties parsing, old IR interpreter not covering v2 types)

**Research date:** 2026-02-18
**Valid until:** 2026-03-18 (stable APIs; LDtk format moves slowly)
