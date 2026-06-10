# Phase 10: Port metasprites GBDK example to gbkt — Pattern Map

**Mapped:** 2026-05-18
**Files analyzed:** 22 new/modified files across 5 modules + test infra
**Analogs found:** 20 / 22 (2 files have no close match — see §No Analog Found)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `gbkt-ir/.../MetaspriteIR.kt` | model | transform | `gbkt-ir/.../ActorIR.kt` | exact |
| `GameIR.kt` (add `metasprites` field) | model | transform | existing `actors: List<ActorIR>` field | exact |
| `gbkt-lang/.../MetaspriteBuilder.kt` | builder/provider | request-response | `gbkt-lang/.../ActorBuilder.kt` | exact |
| `gbkt-lang/.../PaletteBuilder.kt` (add `spritePalette`) | builder | request-response | `PaletteBuilder.palette()` factory | exact |
| `gbkt-lang/.../GameBuilder.kt` (add `metasprite()` factory + `registerMetasprite()`) | provider | request-response | `GameBuilder.actor()` / `registerActor()` | exact |
| `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` | visitor | transform | `ActorVisitor.kt` | exact |
| `GBDKPipelineV2.kt` (add `cgb_compatibility()` + metasprites.h include) | pipeline | transform | `buildMainFunction()` / `buildHomeFile()` | role-match |
| `gbkt-examples/metasprites/build.gradle.kts` | config | — | `gbkt-examples/simple-physics/build.gradle.kts` | exact |
| `gbkt-examples/metasprites/.../Metasprites.kt` | entry-point | event-driven | `gbkt-examples/simple-physics/.../SimplePhysics.kt` | exact |
| `gbkt-examples/metasprites/.../MetaspriteIRTest.kt` | test | transform | `simple-physics/.../SimplePhysicsIRTest.kt` | exact |
| `gbkt-examples/metasprites/.../MetaspriteEmissionTest.kt` | test | transform | `simple-physics/.../SimplePhysicsEmissionTest.kt` | exact |
| `gbkt-examples/metasprites/.../MetaspriteUatTest.kt` | test | event-driven | `simple-physics/.../SimplePhysicsUatTest.kt` | exact |
| `gbkt-examples/metasprites/.../MetaspriteGameTest.kt` | test | transform | `simple-physics/.../SimplePhysicsGameTest.kt` | role-match |
| `gbkt-examples/settings.gradle.kts` (add include) | config | — | existing `simple-physics` include line | exact |
| `evidence/reference/BUILD.md` | doc | — | Phase 9 `evidence/reference/BUILD.md` | exact |
| `ScriptOpVisitor.kt` (add `visitMoveMetasprite` case) | visitor | event-driven | `visitSetPalette()` / `visitSetAnimationState()` | role-match |
| `ScriptOpVisitorI.kt` (add `visitMoveMetasprite`) | interface | — | `visitSetPalette` in `ScriptOpVisitorI.kt` | exact |
| `ScriptOp.kt` (add `MoveMetasprite` data class) | model | — | `SetPalette` / `SetAnimationState` in `ScriptOp.kt` | exact |
| `gbkt-ir/.../GameIRSerializer.kt` (extend for metasprites) | utility | transform | existing `serialize/deserializeActors` pattern | role-match |
| `ActorVisitor.kt` (call `generateHideSpritesRange` from metasprite visitor) | visitor | — | `generateHideSpritesRange()` existing function | exact |

---

## Pattern Assignments

### 1. `gbkt-ir/.../MetaspriteIR.kt` (model, transform)

**Analog:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ActorIR.kt`

**Imports pattern** (ActorIR.kt lines 1–11):
```kotlin
package io.github.gbkt.core.ir

/**
 * IR node representing a game actor (sprite entity).
 * ...
 */
data class ActorIR(
    val id: String,
    val position: PositionDef,
    val sprite: SpriteDef? = null,
    val hitbox: HitboxDef? = null,
    val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
    ...
) : PlatformAnnotatable
```

**Core pattern — recommended new shape:**
```kotlin
// gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt
data class MetaspriteIR(
    val id: String,                    // inferred from Kotlin property name
    val frames: List<MetaspriteFrame>, // variable-length; each frame can differ
    val sourceLocation: SourceLocation? = null,
)

data class MetaspriteFrame(
    val tiles: List<MetaspriteTile>,   // OAM entries for this frame
)

data class MetaspriteTile(
    val relX: Int,   // relative X offset from metasprite origin (pixels)
    val relY: Int,   // relative Y offset from metasprite origin (pixels)
    val tileId: Int, // base tile index into sprite VRAM block
)
```

**Deviation notes:**
- `ActorIR` implements `PlatformAnnotatable` (adds `bankSlot`, `vramRange`, `oamSlot`). `MetaspriteIR` does NOT implement `PlatformAnnotatable` in Phase 10 — OAM slot tracking is handled dynamically by `move_metasprite_*()` at runtime; static OAM slot assignment is inapplicable to variable-length frames.
- `ActorIR` has a single `sprite: SpriteDef?`; `MetaspriteIR` will reference the asset via an `assetRef: AssetRef` field on `MetaspriteIR` itself (parallel to `SpriteDef.assetRef`), or via a field on `MetaspriteFrame`. Design locked in MetaspriteBuilder plan.
- No `movementConfig`, `physicsConfig`, etc. — metasprite is a pure rendering primitive; physics state variables declared separately via `i16Var`.

---

### 2. `GameIR.kt` — add `metasprites: List<MetaspriteIR>` field

**Analog:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt` lines 64–98

**Core pattern** (GameIR.kt lines 64–98):
```kotlin
data class GameIR(
    val name: String,
    val config: CartridgeConfig = CartridgeConfig(),
    val scenes: List<SceneIR> = emptyList(),
    val actors: List<ActorIR> = emptyList(),   // <-- existing; metasprites parallels this
    val systems: List<SystemIR> = emptyList(),
    val variables: List<VariableDef> = emptyList(),
    ...
    val musicDefs: List<MusicDef> = emptyList(),
    val actorPools: List<ActorPoolIR> = emptyList(),
    ...
)
```

**What to add:**
```kotlin
val metasprites: List<MetaspriteIR> = emptyList(), // NEW — variable-length OAM descriptors
```

**Deviation notes:**
- Insert `metasprites` immediately after `actors` for logical grouping. Default to `emptyList()` so all existing GameIR construction sites compile without change.
- `GameIRSerializer.kt` must be extended with `serializeMetasprites()`/`deserializeMetasprites()` following the exact same JSON round-trip pattern used for `actors`.

---

### 3. `gbkt-lang/.../MetaspriteBuilder.kt` (builder, request-response)

**Analog:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt`

**Imports pattern** (ActorBuilder.kt lines 1–43):
```kotlin
@file:Suppress("TooManyFunctions")

package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssignOp
import ...
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
```

**MetaspriteDelegate — copy from ActorDelegate** (ActorBuilder.kt lines 1218–1241):
```kotlin
class ActorDelegate(private val nameOverride: String?, private val block: ActorBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, ActorRef> {
    private var ref: ActorRef? = null

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ActorRef> {
        val name = nameOverride ?: property.name
        val gameBuilder =
            GameBuilderContext.current ?: error("actor {} must be called inside a game {} block")
        ref = gameBuilder.registerActor(name, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): ActorRef =
        ref ?: error("ActorDelegate not initialized — was provideDelegate called?")
}
```

**MetaspriteRef — copy from ActorRef** (ActorBuilder.kt lines 306–320):
```kotlin
data class ActorRef(val id: String) {
    override fun toString(): String = id

    val x: ActorPropertyRef
        get() = ActorPropertyRef(id, "x")

    val y: ActorPropertyRef
        get() = ActorPropertyRef(id, "y")

    val visible: ActorPropertyRef
        get() = ActorPropertyRef(id, "visible")
}
```

**Recommended MetaspriteRef shape:**
```kotlin
data class MetaspriteRef(val id: String) {
    override fun toString(): String = id

    val flipX: ActorPropertyRef get() = ActorPropertyRef(id, "flipX")
    val flipY: ActorPropertyRef get() = ActorPropertyRef(id, "flipY")
    val subPalette: ActorPropertyRef get() = ActorPropertyRef(id, "subPalette")
}
```

The existing `ActorPropertyRef` type and ALL its operator extensions (`set`, `+=`, `isAbove`, `and`, `shr`, etc.) from ActorBuilder.kt lines 61–291 work without modification — `MetaspriteRef` returns `ActorPropertyRef` instances, so the lowering pipeline sees identical IR.

**GameBuilder.metasprite() factory — copy from GameBuilder.actor()** (GameBuilder.kt lines 250–279):
```kotlin
internal fun registerActor(id: String, block: ActorBuilder.() -> Unit): ActorRef {
    refRegistry.register(id, RefKind.ACTOR)
    val builder = ActorBuilder(id)
    builder.block()
    actorBuilders.add(builder)
    return ActorRef(id)
}

fun actor(block: ActorBuilder.() -> Unit): ActorDelegate = ActorDelegate(null, block)
```

**Deviation notes:**
- `MetaspriteDelegate.provideDelegate` calls `GameBuilderContext.current?.registerMetasprite(ir)` (new method to add to `GameBuilderContext`), not `registerActor`. Pattern is identical but target list is `_metasprites` not `actorBuilders`.
- `MetaspriteBuilder` has no `position()`, `sprite()`, `hitbox()` — it has `frame { }` and `sprite(assetRef)`. Builder is simpler than `ActorBuilder`.
- `@GbktDsl` annotation must be applied to `MetaspriteBuilder`, `MetaspriteFrameBuilder` — copy from `ActorBuilder`'s `@GbktDsl` annotation.

---

### 4. `gbkt-lang/.../PaletteBuilder.kt` — add `spritePalette { }` factory

**Analog:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/PaletteBuilder.kt` lines 123–162

**Existing delegate pattern** (PaletteBuilder.kt lines 123–141):
```kotlin
class PaletteDelegate(private val block: PaletteBuilder.() -> Unit) {
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, GBCPalette> {
        val name = property.name
        val builder = PaletteBuilder(name)
        builder.block()
        val palette = builder.build()     // <-- defaults to PaletteType.BACKGROUND
        GameBuilderContext.current?.registerPalette(palette)
            ?: error("palette { } called outside a game { } block")
        return ReadOnlyProperty { _, _ -> palette }
    }
}
```

**Existing factory** (PaletteBuilder.kt line 162):
```kotlin
fun palette(block: PaletteBuilder.() -> Unit): PaletteDelegate = PaletteDelegate(block)
```

**`build()` signature** (PaletteBuilder.kt line 89):
```kotlin
internal fun build(type: PaletteType = PaletteType.BACKGROUND): GBCPalette {
```

**What to add — new `spritePalette` factory:**
```kotlin
// New factory that builds with PaletteType.SPRITE — add directly below `palette { }` factory.
fun spritePalette(block: PaletteBuilder.() -> Unit): SpritePaletteDelegate = SpritePaletteDelegate(block)

class SpritePaletteDelegate(private val block: PaletteBuilder.() -> Unit) {
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, GBCPalette> {
        val name = property.name
        val builder = PaletteBuilder(name)
        builder.block()
        val palette = builder.build(PaletteType.SPRITE)   // <-- key difference
        GameBuilderContext.current?.registerPalette(palette)
            ?: error("spritePalette { } called outside a game { } block")
        return ReadOnlyProperty { _, _ -> palette }
    }
}
```

**Deviation notes:**
- Prefer a separate `SpritePaletteDelegate` class over parameterizing `PaletteDelegate` — avoids leaking `PaletteType` into the DSL author's import list, keeps `palette { }` unmodified.
- `visitSetPalette()` in `ScriptOpVisitor.kt` lines 1586–1596 ALREADY has the `PaletteType.SPRITE -> "set_sprite_palette"` branch — no change needed there.

---

### 5. `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` (visitor, transform)

**Analog:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt`

**Object declaration pattern** (ActorVisitor.kt lines 76–86):
```kotlin
object ActorVisitor {

    fun visit(actor: ActorIR): List<CVarDecl> {
        val prefix = "_${sanitizeId(actor.id)}"
        return listOf(
            CVarDecl(name = "${prefix}_x", type = CU8, initializer = CLiteral(actor.position.x)),
            CVarDecl(name = "${prefix}_y", type = CU8, initializer = CLiteral(actor.position.y)),
        )
    }
    ...
```

**`set_sprite_data` emission pattern** (ActorVisitor.kt lines 98–115):
```kotlin
fun generateSpriteDataLoad(
    actor: ActorIR,
    tileDataArrayName: String,
    startTile: Int,
): List<CStatement> {
    ...
    return listOf(
        CExprStatement(
            CCall(
                "set_sprite_data",
                listOf(CLiteral(startTile), CLiteral(totalTiles), CVar(tileDataArrayName)),
            )
        )
    )
}
```

**`hide_sprites_range` function** (ActorVisitor.kt lines 228–251) — copy verbatim, call from MetaspriteVisitor:
```kotlin
fun generateHideSpritesRange(): CFunction {
    val iVar = CVar("i")
    val loopVarDecl = CVarDecl("i", CU8, initializer = null)
    val forLoop = CFor(
        init = CExprStatement(CBinaryExpr(iVar, "=", CVar("from"))),
        condition = CBinaryExpr(iVar, "<", CVar("to")),
        increment = CUnaryExpr("++", iVar),
        body = listOf(
            CExprStatement(CCall("move_sprite", listOf(iVar, CRawExpr("0"), CRawExpr("0"))))
        ),
    )
    return CFunction(
        name = "hide_sprites_range",
        returnType = CVoid,
        params = listOf(CParam("from", CU8), CParam("to", CU8)),
        body = listOf(loopVarDecl, forLoop),
        sectionComment = "Sprite helpers (real OAM management)",
    )
}
```

**`update_sprites` as template for `generateMetaspriteFrameSwitch`** (ActorVisitor.kt lines 183–219):
```kotlin
fun generateUpdateSprites(
    actors: List<ActorIR>,
    excludeIds: Set<String> = emptySet(),
): CFunction {
    val statements = mutableListOf<CStatement>()
    ...
    return CFunction(
        name = "update_sprites",
        returnType = CVoid,
        body = statements,
        sectionComment = "Sprite OAM sync (called every frame)",
    )
}
```

**Deviation notes from ActorVisitor:**
- `MetaspriteVisitor` needs THREE distinct public methods: `generateMetaspriteTileData()` (→ `set_sprite_data` in scene enter), `generateMetaspriteDescriptor()` (→ `sprite_metasprites[]` C global array), and `generateMetaspriteFrameSwitch()` (→ per-frame switch on flip state + hiwater + `hide_sprites_range`).
- Unlike `ActorVisitor.generateUpdateSprites()` (always emits `move_sprite` in a fixed loop), `MetaspriteVisitor.generateMetaspriteFrameSwitch()` emits a `switch` on the flip bitmask variable (`_rot & 0x3`) selecting among `move_metasprite_ex`, `move_metasprite_flipy`, `move_metasprite_flipx`, `move_metasprite_flipxy`. The hiwater is a local `uint8_t` variable, not a static slot count.
- `generateMetaspriteDescriptor()` must emit raw C struct literals for `METASPRITE_DEF` using `CRawCode` — there is no existing analog in the typed C AST for struct literal arrays. Pattern: `CRawCode("const METASPRITE_DEF sprite_metasprite_0[] = { {dy, dx, dtile}, ..., {metasprite_end} };")`.
- The OAM attribute byte for flipX/flipY is managed implicitly by the `move_metasprite_flipx/y/xy` function selection — NOT by separate `set_sprite_prop()` calls. This differs from the D-07 API surface description; the visitor selects the right `move_metasprite_*` variant based on the runtime `_rot & 0x3` value.
- `subPalette` IS passed as a parameter to `move_metasprite_*` (4th argument: `subpal`), NOT via `set_sprite_prop` — the GBDK `move_metasprite_ex(descriptor, tileBase, subpal, oamSlot, x, y)` signature handles sub-palette assignment during move.

---

### 6. `GBDKPipelineV2.kt` — add `cgb_compatibility()` + `<gbdk/metasprites.h>` include

**Analog:** `GBDKPipelineV2.kt` lines 3531–3563 (`buildMainFunction`) and lines 1120–1131 (`buildHomeFile` includes section)

**`buildMainFunction` body — existing main body init** (lines 3531–3554):
```kotlin
val mainBody = buildList {
    // Sound hardware init
    add(CExprStatement(CBinaryExpr(CVar("NR52_REG"), "=", CLiteral(0x80))))
    add(CExprStatement(CBinaryExpr(CVar("NR50_REG"), "=", CLiteral(0x77))))
    add(CExprStatement(CBinaryExpr(CVar("NR51_REG"), "=", CLiteral(0xFF))))
    // Enable display
    add(CRawCode("DISPLAY_ON;"))
    add(CRawCode("SHOW_BKG;"))
    add(CRawCode("SHOW_SPRITES;"))
    // Load sprite tile data into VRAM
    addAll(spriteDataLoads)
    // Bind OAM slots to tiles and set initial positions
    addAll(spriteOAMInits)
    ...
    add(CWhile(CVar("1"), gameLoopBody))
}
```

**What to add — cgb_compatibility() injection:**
```kotlin
val mainBody = buildList {
    // NEW: GBC compatibility init — must be FIRST statement before any GBC hardware access
    if (gameIR.config.gbcTarget != GbcTarget.DMG) {
        add(CRawCode("cgb_compatibility();"))
    }
    // Sound hardware init (existing — unchanged)
    add(CExprStatement(CBinaryExpr(CVar("NR52_REG"), "=", CLiteral(0x80))))
    ...
```

**`buildHomeFile` include pattern** (lines 1126–1131):
```kotlin
val cgbHomeInclude = if (gameIR.palettes.isNotEmpty()) listOf("<gb/cgb.h>") else emptyList()
val allIncludes =
    listOf("<gb/gb.h>", "<stdio.h>", "<stdlib.h>", "<gbdk/console.h>", "\"game.h\"") +
        hUGEInclude +
        cgbHomeInclude +
        spriteIncludes
```

**What to add — metasprites.h conditional include:**
```kotlin
// Add <gbdk/metasprites.h> when game has any MetaspriteIR definitions
val metaspriteInclude = if (gameIR.metasprites.isNotEmpty()) listOf("<gbdk/metasprites.h>") else emptyList()
val allIncludes =
    listOf("<gb/gb.h>", ...) +
        hUGEInclude +
        cgbHomeInclude +
        metaspriteInclude +
        spriteIncludes
```

**Deviation notes:**
- `cgb_compatibility()` must be the VERY FIRST statement in `main()` — before NR52/NR50/NR51 sound init and before `DISPLAY_ON`. Reference's `metasprites.c` calls it before any hardware setup. The condition `gbcTarget != GbcTarget.DMG` covers both `GBC_COMPATIBLE` and `GBC_ONLY`.
- `<gbdk/metasprites.h>` goes into `allIncludes` for `main.c` (HOME bank). The scene file (`bank1.c`) may also need it if `move_metasprite_*` calls are emitted there — check which bank the frame function lives in; add to `buildSceneFile` includes if needed.

---

### 7. `ScriptOp.kt` — add `MoveMetasprite` data class

**Analog:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt` — existing `SetPalette` and `SetAnimationState` data classes

**Existing SetPalette pattern (from ScriptOpVisitor.kt lines 1586–1596 — what the op carries):**
```kotlin
override fun visitSetPalette(op: SetPalette): CStatement {
    val func = when (op.type) {
        PaletteType.BACKGROUND -> "set_bkg_palette"
        PaletteType.SPRITE -> "set_sprite_palette"
    }
    return CExprStatement(
        CCall(func, listOf(CLiteral(op.slot), CLiteral(1), CVar("${op.paletteName}_pal"))),
        sourceLocation = op.sourceLocation,
    )
}
```

**Recommended `MoveMetasprite` shape:**
```kotlin
// In ScriptOp.kt — add alongside other ScriptOp implementations
data class MoveMetasprite(
    val metaspriteId: String,       // e.g. "elephant" — resolves to _elephant_* vars
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <R> accept(visitor: ScriptOpVisitorI<R>): R = visitor.visitMoveMetasprite(this)
}
```

**`ScriptOpVisitorI.kt` addition:**
```kotlin
// Add to ScriptOpVisitorI interface alongside visitSetPalette:
fun visitMoveMetasprite(op: MoveMetasprite): R
```

**Deviation notes:**
- All backends that implement `ScriptOpVisitorI` must add the new `visitMoveMetasprite` method. The non-sealed interface pattern means the compiler flags every implementor. Check `gbkt-analysis` passes for any `ScriptOpVisitorI` implementations that need a default.
- The `MoveMetasprite` op carries only the `metaspriteId` — the visitor resolves flip state and subpal from the IR's variable registry at codegen time.

---

### 8. `ScriptOpVisitor.kt` — add `visitMoveMetasprite` implementation

**Analog:** `ScriptOpVisitor.kt` lines 1586–1596 (`visitSetPalette`) and lines 1614–1625 (`visitSetAnimationState`)

**`visitSetAnimationState` pattern — switch emission** (lines 1614–1625):
```kotlin
override fun visitSetAnimationState(op: SetAnimationState): CStatement {
    val actorId = op.actorId
    val stateConst = "ANIM_${actorId.uppercase()}_${op.stateName.uppercase()}"
    return CBlock(
        listOf(
            CExprStatement(CBinaryExpr(CVar("_${actorId}_anim_state"), "=", CVar(stateConst))),
            ...
        )
    )
}
```

**Recommended `visitMoveMetasprite` pattern:**
```kotlin
override fun visitMoveMetasprite(op: MoveMetasprite): CStatement {
    val id = op.metaspriteId
    // Emit: uint8_t hiwater = 0u;
    // switch (_rot & 0x3) {
    //   case 0: hiwater += move_metasprite_ex(sprite_metasprites[_idx], TILE_NUM_START, (_rot >> 2), hiwater, _${id}_x, _${id}_y); break;
    //   case 1: hiwater += move_metasprite_flipy(...); break;
    //   case 2: hiwater += move_metasprite_flipxy(...); break;
    //   case 3: hiwater += move_metasprite_flipx(...); break;
    // }
    // hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);
    return CBlock(listOf(
        CVarDecl("hiwater", CU8, CLiteral(0)),
        CSwitch(CRawExpr("_rot & 0x3u"), listOf(
            CSwitchCase(CRawExpr("0"), listOf(...move_metasprite_ex..., CBreak)),
            CSwitchCase(CRawExpr("1"), listOf(...move_metasprite_flipy..., CBreak)),
            ...
        )),
        CExprStatement(CCall("hide_sprites_range", listOf(CVar("hiwater"), CRawExpr("MAX_HARDWARE_SPRITES")))),
    ))
}
```

**Deviation notes:**
- `CLiteral(0)` for `hiwater` initial value is correct (unsigned context, `uint8_t`).
- `_rot & 0x3u` uses `CLiteral` for the mask (unsigned context bitwise AND — per Phase 07.9 rule 2, unsigned-context literals stay `CLiteral`).
- The `move_metasprite_*` call arguments include `subpal = _rot >> 2` (per GBDK API). `_rot >> 2` emits as `CRawExpr` or via `CBinaryExpr(CVar("_rot"), ">>", CLiteral(2))`.
- `hide_sprites_range` is already generated by `ActorVisitor.generateHideSpritesRange()` and placed in `main.c`. Do NOT emit a second copy — just call it.

---

### 9. Port entry point — `gbkt-examples/metasprites/.../Metasprites.kt`

**Analog:** `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt`

**Full structure pattern** (SimplePhysics.kt):
```kotlin
package io.github.gbkt.examples.simple_physics

import io.github.gbkt.core.dsl.*

internal const val CARTRIDGE_ROM_ONLY = "ROM_ONLY"
internal const val MAX_X_SPEED_IN_SUBPIXELS = 64
// ... other constants

@Suppress("LongMethod")
val simplePhysics =
    game("SimplePhysics") {
        config {
            cartridge = CARTRIDGE_ROM_ONLY
            romBanks = 2
        }

        var posX by i16Var(INITIAL_POS_IN_SUBPIXELS)
        var posY by i16Var(INITIAL_POS_IN_SUBPIXELS)
        var spdX by i16Var(0)
        var spdY by i16Var(0)

        val ball by actor {
            position(64, 64)
            sprite(asset("sprites/ball.png")) { size(8, 8); hitbox(0, 0, 8, 8) }
        }

        val playScene = scene("play") {
            enter { showSprites(); posX set INITIAL_POS_IN_SUBPIXELS; ... }
            frame {
                whenever(dpad.up.held) { ... }
                whenever(buttons.a.pressed) { ... }
                posX += spdX
                ball.moveTo(posX shr 4, posY shr 4)
                ...
            }
        }
        start = playScene.id
    }
```

**Recommended Metasprites.kt structure:**
```kotlin
package io.github.gbkt.examples.metasprites

import io.github.gbkt.core.dsl.*

internal const val CARTRIDGE_ROM_ONLY = "ROM_ONLY"
internal const val NUM_FRAMES = 5        // 5-frame elephant animation
internal const val SPR_NUM_START = 0     // OAM slot start
internal const val TILE_NUM_START = 0    // VRAM tile start

val metasprites =
    game("Metasprites") {
        config {
            cartridge = CARTRIDGE_ROM_ONLY
            romBanks = 2
            target(GbcTarget.GBC_COMPATIBLE)  // D-09: GBC_COMPATIBLE for sub-palette
        }

        // Sub-pixel physics (same i16Var pattern as simple-physics)
        var posX by i16Var(...)
        var posY by i16Var(...)
        var spdX by i16Var(0)
        var spdY by i16Var(0)

        // Animation/flip state
        var idx by u8Var(0)   // current frame index (u8, not i8 — Pitfall 6)
        var rot by u8Var(0)   // rot & 0x3 = flip state; rot >> 2 = subpal

        // Sprite sub-palettes (4 GBC colors)
        val gray by spritePalette { color0(...); color1(...); color2(...); color3(...) }
        val pink by spritePalette { ... }
        val cyan by spritePalette { ... }
        val green by spritePalette { ... }

        // Metasprite definition (5 frames, variable tile count)
        val elephant by metasprite {
            sprite(asset("sprites/elephant.png"))
            frame { tile(relX, relY, id); ... }  // frame 0: 31 tiles
            frame { tile(relX, relY, id); ... }  // frame 1: 33 tiles
            ...
        }

        val playScene = scene("play") {
            enter {
                showSprites()
                // Set sprite palettes (via SetPalette ops)
                ...
                posX set ...; posY set ...; spdX set 0; spdY set 0; idx set 0; rot set 0
            }
            frame {
                // D-pad accel (identical pattern to simple-physics)
                whenever(dpad.up.held) { spdY -= 2; ... }
                ...
                // Behavior 1: B pressed → advance animation frame
                whenever(buttons.b.pressed) {
                    idx++
                    whenever(idx isAtLeast NUM_FRAMES) { idx set 0 }
                }
                // Behavior 2+3: A pressed → cycle flip + subpal
                whenever(buttons.a.pressed) {
                    rot++
                    // rot and 0xF per reference (4-bit mask)
                }
                // Position integration
                posX += spdX; posY += spdY
                // Render metasprite (new MoveMetasprite ScriptOp)
                moveMetasprite(elephant)
                // Decel ladder (identical to simple-physics)
                whenever(spdY isBelow 0) { spdY++ }
                ...
            }
        }
        start = playScene.id
    }
```

**Deviation notes from SimplePhysics.kt:**
- `config { target(GbcTarget.GBC_COMPATIBLE) }` is new — required for behavior 3 (sub-palette). SimplePhysics has no GBC target configuration.
- `val elephant by metasprite { }` replaces `val ball by actor { }`. No `ActorRef` — instead a `MetaspriteRef`.
- `spritePalette { }` replaces `palette { }` — the new factory with `PaletteType.SPRITE`.
- `moveMetasprite(elephant)` replaces `ball.moveTo(...)` — new ScriptOp.
- `var idx by u8Var(0)` and `var rot by u8Var(0)` are additional state vars not in simple-physics.

---

### 10. Test files — `MetaspriteIRTest.kt`

**Analog:** `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsIRTest.kt`

**Core pattern** (SimplePhysicsIRTest.kt lines 26–101):
```kotlin
class SimplePhysicsIRTest {
    private val ir = simplePhysics.build()

    @Test fun `has 1 scene`() { assertEquals(1, ir.scenes.size) }
    @Test fun `has 1 actor`() { assertEquals(1, ir.actors.size) }
    @Test fun `start scene is play`() { assertEquals("play", ir.startScene) }
    @Test fun `has 4 variables`() { assertEquals(4, ir.variables.size) }
    @Test fun `has posX variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "posX" && it.type == VarType.I16 })
    }
    @Test fun `play scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "play" }.enterOps.isNotEmpty())
    }
    ...
}
```

**Recommended `MetaspriteIRTest` additions:**
```kotlin
class MetaspriteIRTest {
    private val ir = metasprites.build()

    @Test fun `has 1 metasprite`() { assertEquals(1, ir.metasprites.size) }
    @Test fun `elephant has 5 frames`() {
        assertEquals(5, ir.metasprites.first { it.id == "elephant" }.frames.size)
    }
    @Test fun `each frame has at least 1 tile`() {
        ir.metasprites.first().frames.forEach { frame ->
            assertTrue(frame.tiles.isNotEmpty())
        }
    }
    @Test fun `has 4 palettes all of type SPRITE`() {
        assertEquals(4, ir.palettes.size)
        assertTrue(ir.palettes.all { it.type == PaletteType.SPRITE })
    }
    @Test fun `has idx and rot variables of type U8`() {
        assertTrue(ir.variables.any { it.name == "idx" && it.type == VarType.U8 })
        assertTrue(ir.variables.any { it.name == "rot" && it.type == VarType.U8 })
    }
    ...
}
```

---

### 11. Test files — `MetaspriteEmissionTest.kt`

**Analog:** `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt`

**`extractFunctionBody` helper — copy verbatim** (SimplePhysicsEmissionTest.kt lines 82–102):
```kotlin
private fun extractFunctionBody(cSource: String, functionName: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.contains("void $functionName(") }
    if (startIdx == -1) return ""
    val body = StringBuilder()
    var depth = 0
    var started = false
    for (i in startIdx until lines.size) {
        val line = lines[i]
        body.appendLine(line)
        for (ch in line) {
            if (ch == '{') { depth++; started = true }
            if (ch == '}') depth--
        }
        if (started && depth == 0) break
    }
    return body.toString()
}
```

**Pipeline invocation pattern** (SimplePhysicsEmissionTest.kt lines 104–111):
```kotlin
private fun playFrameBody(): String {
    val pipeline = GBDKPipelineV2()
    val pipelineOutput = pipeline.generate(simplePhysics.build())
    val bank1C = pipelineOutput.files["bank1.c"]
        ?: error("bank1.c not generated by GBDKPipelineV2")
    return extractFunctionBody(bank1C, "play_frame")
}
```

**Evidence dir pattern** (SimplePhysicsEmissionTest.kt lines 62–67):
```kotlin
val EVIDENCE_DIR = File(System.getProperty("user.dir"))
    .resolve("../../.planning/phases/09-port-.../evidence/tier1-shape")
    .normalize()
```

**Deviation notes:**
- Change `simplePhysics.build()` → `metasprites.build()` in all pipeline calls.
- Change evidence path to `10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape`.
- D-12.1 asserts `play_frame` body contains animation index advance: `_idx++` or `sprite_metasprites[_idx]`.
- D-12.2 asserts flip variant selection: `move_metasprite_flipy` or `move_metasprite_flipx` or `move_metasprite_flipxy`.
- D-12.3 asserts sub-palette: `OAMF_CGB_PAL` or `_rot >> 2` or `subpal` parameter in `move_metasprite_ex`.

---

### 12. Test files — `MetaspriteUatTest.kt`

**Analog:** `gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsUatTest.kt`

**`newAgent()` pattern** (SimplePhysicsUatTest.kt lines 55–67):
```kotlin
private fun newAgent(): StepAgent {
    Assumptions.assumeTrue(
        ROM_FILE.exists(),
        "simple-physics.gb not found — run buildRom first",
    )
    EVIDENCE_DIR.mkdirs()
    val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
    val metadata = if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
    val agent = StepAgent(baseConfig, metadata)
    agent.start()
    return agent
}
```

**Deviation notes from SimplePhysicsUatTest:**
- For behavior 3 (sub-palette), `AgentSessionConfig` must set `gbcMode = true`. Pattern:
  ```kotlin
  val gcbConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
      .copy(gbcMode = true)  // GBC mode required for sub-palette evidence (D-02)
  val agent = StepAgent(gcbConfig, metadata)
  ```
- `resolveI16Address()` and `readI16()` helpers in SimplePhysicsUatTest.kt are NOT needed here — `idx` and `rot` are `u8Var` (UINT8), readable directly via `agent.readVariable("idx")` without the `.noi` address resolution workaround.
- Button constant: use `Button.B` for behavior 1, `Button.A` for behaviors 2+3.
- `captureAndRename()` helper — copy verbatim from SimplePhysicsUatTest.kt lines 103–118.

---

### 13. `gbkt-examples/metasprites/build.gradle.kts`

**Analog:** `gbkt-examples/simple-physics/build.gradle.kts`

**Full file pattern** (simple-physics/build.gradle.kts):
```kotlin
plugins {
    kotlin("jvm")
    id("io.github.gbkt")
}

group = "io.github.gbkt.examples"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform(project(":gbkt-bom")))
    implementation(project(":gbkt-backend-gbdk"))
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))
    testImplementation(project(":gbkt-test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

gbkt {
    game("io.github.gbkt.examples.simple_physics.SimplePhysicsKt::simplePhysics")
    assets("res")
    outputName.set("simple-physics")
}
```

**Change for metasprites:**
```kotlin
gbkt {
    game("io.github.gbkt.examples.metasprites.MetaspritesKt::metasprites")
    assets("res")
    outputName.set("metasprites")
}
```

---

## Shared Patterns

### Pattern A: `provideDelegate` name-inference (applies to MetaspriteDelegate)

**Source:** `gbkt-lang/.../ActorBuilder.kt` lines 1228–1241
**Apply to:** `MetaspriteDelegate`

```kotlin
operator fun provideDelegate(
    thisRef: Any?,
    property: KProperty<*>,
): ReadOnlyProperty<Any?, ActorRef> {
    val name = nameOverride ?: property.name      // <-- name inferred here
    val gameBuilder =
        GameBuilderContext.current ?: error("actor {} must be called inside a game {} block")
    ref = gameBuilder.registerActor(name, block)
    return this
}
```

### Pattern B: `ActorPropertyRef` operator extensions (apply to MetaspriteRef properties)

**Source:** `gbkt-lang/.../ActorBuilder.kt` lines 61–291
**Apply to:** `elephant.flipX`, `elephant.flipY`, `elephant.subPalette` in DSL

The entire operator extension block (`set`, `+=`, `-=`, `++`, `--`, `isAbove`, `isBelow`, `isAtLeast`, `isAtMost`, `isEqualTo`, `and`, `or`, `shr`, etc.) transfers without modification because `MetaspriteRef` returns `ActorPropertyRef` instances. Zero new operator code needed.

### Pattern C: Scope-level `extractFunctionBody` brace-walk before grep

**Source:** `gbkt-examples/simple-physics/.../SimplePhysicsEmissionTest.kt` lines 82–102
**Apply to:** All three `MetaspriteEmissionTest` D-12 tests

Per CLAUDE.md §"Scope-level grep gates": extract `play_frame` body before asserting. File-level grep on `bank1.c` cannot distinguish `play_frame` from `play_enter` — a palette call in `play_enter` could mask a missing call in `play_frame`.

### Pattern D: Unsigned literal convention for OAM bitmask constants

**Source:** `gbkt-backend-gbdk/CLAUDE.md` §"Literal Emission Convention"
**Apply to:** All `OAMF_*` flag constants in MetaspriteVisitor + ScriptOpVisitor `visitMoveMetasprite`

Rule: OAM attribute flag constants (`OAMF_X_FLIP`, `OAMF_Y_FLIP`, `OAMF_CGB_PAL0`–`OAMF_CGB_PAL3`) are unsigned bitmasks used in unsigned-context OR/AND operations → use `CLiteral(N)` (emits `Nu`). Do NOT use `CIntLiteral` for these.

### Pattern E: `@GbktDsl` annotation on builder classes

**Source:** `gbkt-lang/.../ActorBuilder.kt` lines 378, 499, 667, 709, 762, 819
**Apply to:** `MetaspriteBuilder`, `MetaspriteFrameBuilder`

```kotlin
@GbktDsl
class MetaspriteBuilder(val id: String) { ... }

@GbktDsl
class MetaspriteFrameBuilder { ... }
```

Prevents DSL scope leakage — without this, `frame { }` could accidentally call outer `game { }` methods.

### Pattern F: `AgentSessionConfig.gbcMode = true` for GBC UAT

**Source:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt` line 153 and `AgentSessionConfig.kt` line 42 (confirmed in RESEARCH.md §8)
**Apply to:** Behavior 3 test in `MetaspriteUatTest`

```kotlin
val gbcConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
    .copy(gbcMode = true)
```

This is the ONLY way to obtain visual evidence for behavior 3 (sub-palette cycling) — DMG mode hardware-ignores CGB palette bits.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `evidence/reference/BUILD.md` | doc | — | Documentation file; closest analog is Phase 9's `BUILD.md` (not in codebase — in `.planning/`). Executor reads Phase 9's equivalent and mirrors its structure. |
| `MetaspriteVisitor.generateMetaspriteDescriptor()` (the `sprite_metasprites[]` C array emission) | visitor | transform | No existing analog for emitting a C array of struct-pointer arrays. Only `CRawCode` escape hatch exists. The reference GBDK `sprite.c` generated by `png2asset` is the closest template. |

---

## Metadata

**Analog search scope:** `gbkt-ir/`, `gbkt-lang/`, `gbkt-backend-gbdk/`, `gbkt-examples/simple-physics/`
**Files scanned:** 14 source files (ActorIR.kt, GameIR.kt, ActorBuilder.kt, PaletteBuilder.kt, GameBuilder.kt, ActorVisitor.kt, ScriptOpVisitor.kt, GBDKPipelineV2.kt, SimplePhysics.kt, SimplePhysicsIRTest.kt, SimplePhysicsEmissionTest.kt, SimplePhysicsUatTest.kt, SimplePhysicsGameTest.kt, build.gradle.kts)
**Pattern extraction date:** 2026-05-18
