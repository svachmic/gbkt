# Phase 21: Codegen Fixes — Platformer and Remaining Seeds - Pattern Map

**Mapped:** 2026-06-14
**Files analyzed:** 10 (new/modified files)
**Analogs found:** 10 / 10

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `gbkt-genre-platformer/.../dsl/PlatformerExtensions.kt` | DSL builder (setter addition) | request-response | same file — existing `solidThreshold(v: Int)` setter (line 654) | exact |
| `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` | visitor/codegen (config-read site replacement + predicate delegation) | request-response | same file — existing `(tcSystem?.config?.get("posYVar") as? String)` pattern (line 629) | exact |
| `gbkt-backend-api/src/main/kotlin/.../TilemapCollisionGate.kt` | utility / shared predicate | request-response | `gbkt-backend-api/.../GenreSystemVisitor.kt` — existing top-level fun `sanitizeCId(id: String)` (line 23) | role-match |
| `gbkt-ir/.../GameIRSerializer.kt` | serializer (deserialize 10 stubs) | CRUD | same file — existing `deserializeList(json.optJSONArray("scenes")) { deserializeScene(it) }` block (lines 150–176) | exact |
| `gbkt-genre-platformer/src/test/.../PlatformerSnapArithmeticEmissionTest.kt` | test (JVM emission test) | request-response | `TilemapPhysicsPlayerSymbolEmissionTest.kt` | exact |
| `gbkt-genre-platformer/src/test/.../TilemapCollisionPredicateLockstepTest.kt` | test (contract / lockstep) | request-response | `TilemapPhysicsPlayerSymbolEmissionTest.kt` — `buildGameWithTilemapCollision` helper + `buildGameWithoutTilemapCollision` helper | exact |
| `gbkt-ir/src/test/.../GameIRSerializerRoundTripTest.kt` | test (round-trip) | CRUD | `GameIRSerializerTest.kt` | exact |
| `gbkt-examples/platformer-template/.../PlatformerTemplate.kt` | game DSL (add `pivotAdjust(2)` call) | request-response | same file — existing `solidThreshold(17)` call in `tilemapCollision { }` block | exact |
| `gbkt-examples/platformer-template/src/test/.../PlatformerTemplateUatTest.kt` | UAT test (update `EVIDENCE_DIR` constant) | event-driven | same file — existing `EVIDENCE_DIR` companion constant (lines 47–53) | exact |
| `.planning/backlog/v0.2.0/SEED-*.md` (four files, move only) | planning artifact | — | `.planning/backlog/v0.2.0/SEED-003.md` (Phase 16 precedent) | exact |

---

## Pattern Assignments

### `PlatformerExtensions.kt` — add `pivotAdjust(Int)` setter to `TilemapCollisionBuilder`

**Analog:** same file, `solidThreshold(v: Int)` setter at lines 654–656 + its `build()` integration at line 681.

**Existing setter pattern to copy** (lines 645–656 + 681):
```kotlin
/**
 * Sets the tilemap solid-tile threshold. Tiles with index `< value` are treated as non-solid;
 * tiles with index `>= value` are solid in the tilemap-collision codegen path ...
 */
fun solidThreshold(v: Int) {
    solidThreshold = v
}

// ... in build():
configBuilder["solidThreshold"] = solidThreshold
```

**New setter to add (copy this shape exactly):**
```kotlin
private var pivotAdjust: Int? = null   // null = absent from config map (back-compat)

/**
 * Sets the pixel distance between the rendered metasprite's bottom edge and the
 * hitbox foot. Derived from: frameHeight − pivotY − hitboxH (coerced ≥ 0).
 *
 * Per SEED-021: setting this lifts resolution out of the visitor's metasprite-lookup
 * dance into the DSL as single source of truth (Project Rule #1). When absent, the
 * visitor falls back to companion constants REFERENCE_FRAME_HEIGHT / REFERENCE_PIVOT_Y.
 */
fun pivotAdjust(v: Int) {
    pivotAdjust = v
}

// ... in build(), after the solidThreshold line:
pivotAdjust?.let { configBuilder["pivotAdjust"] = it }
```

**Key points:**
- Field is nullable `Int?` so absence means "not set" → visitor uses fallback.
- `?.let { }` pattern matches `posXVar?.let { configBuilder["posXVar"] = it }` (line 672).
- KDoc must cite SEED-021 + Project Rule #1.

---

### `PlatformerVisitor.kt` — replace metasprite lookup dance + delegate predicate

**Analog A (config-read site replacement):** same file, existing config-read pattern at lines 629 and 640–651.

**Current lookup-dance to replace** (lines 629–651):
```kotlin
val tcPosYVar = (tcSystem?.config?.get("posYVar") as? String)
val playerMetasprite = gameIR.metasprites.firstOrNull { ms ->
    tcPosYVar != null && ms.posYVarName == tcPosYVar
} ?: gameIR.metasprites.firstOrNull { ms ->
    ms.frameHeight != null && ms.pivotY != null
}
val pivotAdjust: Int = run {
    val frameH = playerMetasprite?.frameHeight ?: REFERENCE_FRAME_HEIGHT
    val pivotY = playerMetasprite?.pivotY ?: REFERENCE_PIVOT_Y
    (frameH - pivotY - height).coerceAtLeast(0)
}
```

**Replacement pattern (copy this shape, consistent with other config reads in same visitor):**
```kotlin
// SEED-021: resolved from DSL config key; falls back to companion constants when absent.
val pivotAdjust: Int = (tcSystem?.config?.get("pivotAdjust") as? Int)
    ?: run {
        System.err.println(
            "WARNING: tilemapCollision bound but no pivotAdjust declared; " +
                "using fallback geometry ($REFERENCE_FRAME_HEIGHT, $REFERENCE_PIVOT_Y)"
        )
        (REFERENCE_FRAME_HEIGHT - REFERENCE_PIVOT_Y - height).coerceAtLeast(0)
    }
```

**Analog B (gameUsesTilemapCollision predicate, D-09):** same file, existing `gameUsesTilemapCollision` at lines 1664–1678. After D-09, Path C moves to shared util; the existing Path A/B stay local.

**Existing predicate structure to adapt** (lines 1664–1678):
```kotlin
private fun gameUsesTilemapCollision(gameIR: GameIR): Boolean {
    // Path A — platformer_physics GenericSystem with non-null solidThreshold on physicsConfig
    val systemHasThreshold = gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
        (sys.config["type"] as? String) == "platformer_physics" &&
            (sys.config["physicsConfig"] as? PlatformerPhysicsConfig)?.solidThreshold != null
    }
    if (systemHasThreshold) return true

    // Path B — per-zone platformerPhysicsOverride with solidThreshold key
    return gameIR.zones.any { zone ->
        zone.platformerPhysicsOverride?.containsKey("solidThreshold") == true
    }
}
```

**After D-09:** add `if (gameUsesTilemapCollisionPathC(gameIR)) return true` at the top, delegating Path C to the shared util in `gbkt-backend-api`.

---

### `gbkt-backend-api/.../TilemapCollisionGate.kt` — new shared utility (D-09)

**Analog:** `gbkt-backend-api/.../GenreSystemVisitor.kt` — top-level package-level function `sanitizeCId` (line 23).

**Existing top-level utility pattern to copy** (GenreSystemVisitor.kt lines 1–23):
```kotlin
/* MPL 2.0 header */
package io.github.gbkt.backend.api

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem

/**
 * Sanitizes an identifier string for use as a C identifier.
 * ...
 */
fun sanitizeCId(id: String): String = id.replace('-', '_').replace(' ', '_')
```

**New file to create (copy this shape):**
```kotlin
/* MPL 2.0 header */
package io.github.gbkt.backend.api

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem

/**
 * Path C detection for the tilemap-collision predicate shared between
 * [GBDKPipeline] and [PlatformerVisitor].
 *
 * Path C: a [GenericSystem] with `config["type"] == "tilemap_collision"` is present.
 * This is the path added in Phase 12.1 when `tilemapCollision { }` DSL block is used.
 *
 * Callers (GBDKPipeline, PlatformerVisitor) add this as their first check; each caller
 * handles Path A (typed PlatformerPhysicsConfig.solidThreshold) locally because
 * gbkt-backend-gbdk and gbkt-genre-platformer each have their own access strategy.
 *
 * See SEED-022-tilemap-collision-predicate-consolidation.md.
 */
fun gameUsesTilemapCollisionPathC(gameIR: GameIR): Boolean =
    gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
        (sys.config["type"] as? String) == "tilemap_collision"
    }
```

**Key points:**
- File is named `TilemapCollisionGate.kt` to clearly signal it's a gate predicate, not a builder.
- Single top-level function — same shape as `sanitizeCId`.
- Import only `GameIR` and `GenericSystem` from `gbkt-core` (already on the classpath for `gbkt-backend-api`).
- Both `GBDKPipeline` and `PlatformerVisitor` import and call this function as their Path C check.

---

### `GameIRSerializer.kt` — deserialize 10 stubbed collections (D-08)

**Analog:** same file, already-implemented collection deserializers at lines 150–176.

**Existing deserializeList pattern to copy** (lines 150–176):
```kotlin
scenes = deserializeList(json.optJSONArray("scenes")) { deserializeScene(it) },
actors = deserializeList(json.optJSONArray("actors")) { deserializeActor(it) },
// ... same shape for all implemented collections
```

**The 8 "simple" stubs to implement follow this shape:**
```kotlin
// Existing serializeSimple emits: { "type": "GlobalFlagsIR", "id": "<id>" }
// Deserialize by reading the "id" field from each JSONObject:
flags = deserializeList(json.optJSONArray("flags")) { obj ->
    GlobalFlagsIR(id = obj.getString("id"))
},
itemCategories = deserializeList(json.optJSONArray("itemCategories")) { obj ->
    ItemCategoryDef(id = obj.getString("id"), /* other required fields */ ...)
},
// ... same shape for items, containers, dropTables, puzzleObjects,
//     collisionGroups, collisionRules
```

**For `zones` (partial deserializer — recover id, spawnX, spawnY, screenMode):**
```kotlin
zones = deserializeList(json.optJSONArray("zones")) { obj ->
    ZoneIR(
        id = obj.getString("id"),
        spawnX = obj.optInt("spawnX", 0).toUByte(),
        spawnY = obj.optInt("spawnY", 0).toUByte(),
        // ... other fields with safe defaults; mark unsupported in KDoc
    )
},
```

**For `systems` (GenericSystem subset only):**
```kotlin
systems = deserializeList(json.optJSONArray("systems")) { obj ->
    val type = obj.optString("type", "")
    GenericSystem(
        id = obj.optString("id", ""),
        config = mapOf("type" to type),
        // Document: full config cannot round-trip; only id+type recovered
    )
},
```

**Key points:**
- Each stub replaces exactly one `emptyList()` line with a `deserializeList { ... }` block.
- Follow the `obj.getString` / `obj.optString` / `obj.optInt` pattern used throughout the file.
- Add a KDoc comment above the 10-collection block documenting which fields are recovered vs. unsupported.

---

### `PlatformerSnapArithmeticEmissionTest.kt` — new JVM emission test (D-05/D-07)

**Analog:** `TilemapPhysicsPlayerSymbolEmissionTest.kt`

**Imports pattern to copy** (TilemapPhysicsPlayerSymbolEmissionTest.kt lines 1–20):
```kotlin
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.dsl.TilemapCollisionBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
```

**GameIR builder helper pattern to copy** (lines 144–191):
```kotlin
private val pipeline = GBDKPipeline()

private fun buildGameWithTilemapCollision(
    posXVar: String = "playerX",
    posYVar: String = "playerY",
    // ... other params with defaults
    pivotAdjust: Int = 2,   // ADD: the new param for D-05
    hitboxH: Int = 24,
    spawnY: Int = 120,
): GameIR {
    val physicsSystem = GenericSystem(id = "physics", config = mapOf(
        "type" to "platformer_physics",
        "physicsConfig" to PlatformerPhysicsConfig(gravity = 2, jumpForce = 8,
            terminalVelocity = 12, solidThreshold = 17),
    ))
    val tilemapCollisionSystem = GenericSystem(id = "tilemap_collision", config = mapOf(
        "type" to "tilemap_collision",
        "posXVar" to posXVar, "posYVar" to posYVar,
        // ... other bindings
        "hitboxH" to hitboxH,
        "pivotAdjust" to pivotAdjust,   // ADD: new config key from D-05
    ))
    return GameIR(
        name = "TestSnapArithmetic",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
        scenes = listOf(SceneIR(id = "gameplay")),
        systems = listOf(physicsSystem, tilemapCollisionSystem),
        startScene = "gameplay",
    )
}
```

**extractFunctionBody helper to copy** (lines 112–132 — inlined per-test per convention):
```kotlin
private fun extractFunctionBody(cSource: String, functionSignaturePrefix: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.startsWith(functionSignaturePrefix) }
    if (startIdx == -1) return ""
    val body = StringBuilder()
    var depth = 0; var started = false
    for (i in startIdx until lines.size) {
        val line = lines[i]
        body.appendLine(line)
        for (ch in line) { if (ch == '{') { depth++; started = true }; if (ch == '}') depth-- }
        if (started && depth == 0) break
    }
    return body.toString()
}
```

**Test method shape to add for D-07 snap arithmetic:**
```kotlin
@Test
fun `buildVerticalFootProbe snap arithmetic produces correct posYSym`() {
    // spawn_y=120, hitboxH=24, pivotAdjust=2:
    // foot_tile_row = (120+24)>>3 = 18
    // foot_pixel_top = 18<<3 = 144
    // foot_pixel_anchor = 144 - 24 - 2 = 118
    // posYSym = 118<<4 = 1888
    val gameIR = buildGameWithTilemapCollision(pivotAdjust = 2, hitboxH = 24)
    val result = pipeline.generate(gameIR)
    val mainC = result.files["main.c"]?.content ?: fail("main.c not generated")
    val body = extractFunctionBody(mainC, "void platformer_physics_update")
    // Structural assertion: the snapped posYSym value appears as a literal assignment
    assertTrue(body.contains("1888"), "Expected snapped posYSym=1888 in physics update body")
}
```

---

### `TilemapCollisionPredicateLockstepTest.kt` — new contract test (D-09)

**Analog:** `TilemapPhysicsPlayerSymbolEmissionTest.kt` — same GameIR builder helpers and pipeline setup.

**Test structure to follow:**
```kotlin
class TilemapCollisionPredicateLockstepTest {
    private val pipeline = GBDKPipeline()
    private val visitor = PlatformerVisitor()   // if accessible; else invoke via pipeline

    @Test fun `both predicates agree — tilemap_collision system present`() { ... }
    @Test fun `both predicates agree — platformer_physics with solidThreshold`() { ... }
    @Test fun `both predicates agree — per-zone override`() { ... }
    @Test fun `both predicates agree — no tilemap collision`() { ... }
}
```

**Each test:** build a `GameIR` using the `buildGameWith*` helper pattern, call both predicates (the shared `gameUsesTilemapCollisionPathC()` + the visitor's local predicate result), assert `assertEquals(expectedFromPipelineSide, visitorSideResult)`.

---

### `GameIRSerializerRoundTripTest.kt` — new round-trip test (D-08)

**Analog:** `GameIRSerializerTest.kt`

**Imports to copy** (GameIRSerializerTest.kt lines 1–14):
```kotlin
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.json.JSONObject
```

**Test method shape to follow** (lines 27–62):
```kotlin
@Test
fun `minimal game round-trips through JSON`() {
    val game = GameIR(name = "MinimalGame", startScene = "main", ...)
    val json = GameIRSerializer.toJson(game)
    val back = GameIRSerializer.fromJson(json)
    assertEquals(game.name, back.name)
    // ... further assertions
}
```

**New test to add for D-08:**
```kotlin
@Test
fun `game with all 10 domain collections round-trips with non-empty IDs`() {
    val game = GameIR(
        name = "RoundTripAll10",
        flags = listOf(GlobalFlagsIR(id = "hasKey")),
        itemCategories = listOf(ItemCategoryDef(id = "weapon", ...)),
        items = listOf(ItemDef(id = "sword", ...)),
        containers = listOf(ContainerIR(id = "chest", ...)),
        dropTables = listOf(DropTableIR(id = "enemyDrops", ...)),
        puzzleObjects = listOf(/* PuzzleObjectIR impl */ ...),
        collisionGroups = listOf(CollisionGroupIR(id = "enemies", ...)),
        collisionRules = listOf(CollisionRuleIR(...)),
        zones = listOf(ZoneIR(id = "area1", spawnX = 40u, spawnY = 120u)),
        systems = listOf(GenericSystem(id = "tilemapCollision",
            config = mapOf("type" to "tilemap_collision"))),
    )
    val json = GameIRSerializer.toJson(game)
    val back = GameIRSerializer.fromJson(json)
    // Assert each collection is non-empty and IDs match
    assertTrue(back.flags.isNotEmpty(), "flags must survive round-trip")
    assertEquals("hasKey", back.flags[0].id)
    // ... same pattern for all 10 collections
}
```

---

### `PlatformerTemplate.kt` — add `pivotAdjust(2)` call (D-05)

**Analog:** same file, existing `solidThreshold(17)` call inside `tilemapCollision { }` block.

**Pattern:** in the `tilemapCollision { }` DSL block, add `pivotAdjust(2)` immediately after `hitbox(0, 0, 8, 24)`. Value 2 is derived from `frameSize(24, 32)` + `pivot(12, 6)` + `hitbox h=24`: `32 - 6 - 24 = 2`.

---

### `PlatformerTemplateUatTest.kt` — update `EVIDENCE_DIR` constant (D-15)

**Analog:** same file, existing `EVIDENCE_DIR` companion constant (lines 47–53).

**Current value to replace:**
```kotlin
val EVIDENCE_DIR =
    java.io.File(System.getProperty("user.dir"))
        .resolve("../../.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots")
        .normalize()
```

**New value (Phase 21 evidence dir):**
```kotlin
val EVIDENCE_DIR =
    java.io.File(System.getProperty("user.dir"))
        .resolve("../../.planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/evidence/uat-screenshots")
        .normalize()
```

**Key points:**
- `System.getProperty("user.dir")` resolves to `<repo>/gbkt-examples/platformer-template` for the `:gbkt-examples:platformer-template:test` task. The `../../` ascent is correct.
- `EVIDENCE_DIR.mkdirs()` in `newAgent()` creates the directory automatically before any capture.

---

## Shared Patterns

### MPL 2.0 License Header
**Source:** Any existing `.kt` file (e.g., `GenreSystemVisitor.kt` lines 1–6)
**Apply to:** `TilemapCollisionGate.kt`, `PlatformerSnapArithmeticEmissionTest.kt`, `TilemapCollisionPredicateLockstepTest.kt`, `GameIRSerializerRoundTripTest.kt`
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

### Nullable field + `?.let { }` builder pattern
**Source:** `PlatformerExtensions.kt` lines 671–676 (posXVar, posYVar, etc.)
**Apply to:** `pivotAdjust: Int?` field in `TilemapCollisionBuilder` + its `build()` integration.

### Brace-walk `extractFunctionBody` helper
**Source:** `TilemapPhysicsPlayerSymbolEmissionTest.kt` lines 112–132
**Apply to:** `PlatformerSnapArithmeticEmissionTest.kt` — inline the helper (per per-test convention cited in that file's KDoc)

### `deserializeList { }` pattern
**Source:** `GameIRSerializer.kt` lines 150–176
**Apply to:** All 10 stubbed collection deserializers in `GameIRSerializer.deserialize()`

### UAT `captureAndRename` helper
**Source:** `PlatformerTemplateUatTest.kt` lines 76–95
**Apply to:** Any new anchor test methods added in Phase 21; the helper is already present, just call it.

### Backlog file-move pattern
**Source:** `.planning/backlog/v0.2.0/SEED-003.md`, `SEED-PHASE-12-ONE-WAY-TILE.md` (Phase 16 Plan 10 precedent)
**Apply to:** Four D-03 seeds moving from `seeds/` to `backlog/v0.2.0/`
Pattern: copy seed file, add one-line `> Re-deferred: <rationale>` header at top, delete original from `seeds/`.

---

## No Analog Found

All files have close analogs. No novel patterns required.

---

## Metadata

**Analog search scope:** `gbkt-genre-platformer/src/`, `gbkt-ir/src/`, `gbkt-backend-api/src/`, `gbkt-examples/platformer-template/src/test/`
**Files scanned:** 8 source files read directly
**Pattern extraction date:** 2026-06-14
