# Phase 10: Port metasprites GBDK example to gbkt — Research

**Researched:** 2026-05-18
**Domain:** GBDK metasprite DSL substrate + OAM attribute accessors + GBC palette surface + reference port
**Confidence:** HIGH (codebase fully read; reference C fully analyzed; no external package installs required)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-overfitting-1 (inherited):** Do not add DSL features just to make THIS port pretty. EXCEPTION: the `metasprite { }` primitive IS the port substrate (D-04). Any OTHER DSL surface surfaced during the port → seed or Phase 13 edit, NOT Phase 10 expansion.
- **D-overfitting-2 (inherited):** Do not tune codegen visitors to this example's shape. If the named codegen bug-fix is a real class of bugs, fine. Cosmetic emission tuning → no.
- **D-overfitting-3 (inherited):** Do not let GBDK reference style become THE gbkt style. Reference uses macros, raw int16_t, inline tile data — C conventions only. Skip the `#if HARDWARE_SPRITE_CAN_FLIP_*` macro fallback path — gbkt targets GBC hardware with hardware flip.
- **D-01:** Lock 3 core UAT behaviors: (1) B pressed (edge) → animation index advances + visible frame change; (2) A pressed (edge) → cycles Normal/Flip-Y/Flip-XY/Flip-X via OAM attribute byte writes (rot & 0x3 ladder); (3) A pressed (after 4 flip states wrap) → cycles 4 sprite sub-palettes (gray/pink/cyan/green) visible on GBC. Behaviors 1+2 work on DMG and GBC; behavior 3 requires GBC.
- **D-02:** MCP play-through + screenshot per behavior. For behavior 3, at least one screenshot MUST be in GBC mode.
- **D-03:** UAT first — Plan 1 = lock `10-UAT.md` + `PLAYBOOK.md` with NO DSL yet.
- **D-04:** Port substrate is the new `metasprite { frame { tile(x, y, id) } }` DSL primitive. Faithful to GBDK variable-length OAM descriptor model. New IR node + builder + visitor + optional analysis pass.
- **D-05:** Named codegen bug-fix is exploratory. Build substrate + port + compile + run UAT. Whatever first concrete codegen defect blocks a UAT behavior becomes the named fix. Plausible candidates ahead of build: (a) OAM-tail hiwater off-by-one when frame N has fewer tiles than frame N-1; (b) sprite-palette emission ordering vs `cgb_compatibility()` boot; (c) flip OAM attribute byte not flushed in `update_sprites()`.
- **D-06:** Surplus codegen defects → seeds via `/gsd-capture --seed`. At port-close: if ≥1 surplus seed, insert Phase 10.1 placeholder in the same commit that closes Phase 10.
- **D-07:** Runtime accessors on actor/metasprite ref — NOT declarative defaults. API: `actor.flipX set (...)`, `actor.flipY set (...)`, `actor.subPalette set (rot shr 2)`. Mirrors existing `actor.x`/`actor.visible` pattern.
- **D-08:** subPalette range is 0..3 (GBC); DMG behavior deferred to research/planning. GBDK's `set_sprite_prop()` writes the OAM attribute byte; on DMG the CGB palette bits are ignored by hardware.
- **D-09:** GBC-compatible single ROM via `config { target(GbcTarget.GBC_COMPATIBLE) }` equivalent + `set_sprite_palette` for 4 sub-pals. Port must boot clean on both DMG and GBC.
- **D-10:** Include 1-tile checkerboard BG fill for visual parity. Uses existing `set_bkg_data`/`fill_bkg_rect` equivalent DSL surfaces.
- **D-11:** Three artifacts — ROM size + generated-C diff + UAT verdict. NO .asm diff, NO bank/section size capture.
- **D-12:** Tier-1 JVM emission invariants — 3 tests matching the 3 UAT behaviors. Per-function awk brace-walk before grep (scope-level grep gate corollary from CLAUDE.md).
- **D-13:** Keep Phase 10 scoped to decisions above. Framework-shaping DSL gaps surfaced after the port works → Phase 13 via `/gsd-phase --edit 13`.
- **D-14:** Target ≥12 plans, expect ~15–18. Planner MUST NOT compress work into fewer plans to look efficient.

### Claude's Discretion

- Plan count / wave structure: ~15–18 plans as detailed in CONTEXT.md §"Plan sizing". IR node → builder → optional analysis → visitor-tiledata → visitor-descriptor → visitor-frameswitch + hiwater → runtime accessor flipX/flipY → runtime accessor subPalette → GBC compat → BG checkerboard → reference ROM build → port assembly → first-build/blocker analysis → named codegen bug-fix → three-signal comparison artifact → Tier-1 JVM emission invariants → phase close.
- Scene shape: Single `play` scene. No title, no game-over.
- PNG asset specifics: 5-frame elephant sprite (64×240 PNG; 5 frames of 48px each; 8×6 tiles per frame; variable non-empty tile count per frame 31–33 tiles).
- Cartridge type: `CARTRIDGE_ROM_ONLY` string magic inherited from Phase 9.4 (no MBC needed).
- Metasprite tile-load timing: Once in `scene.enter { }` (analog to reference's `load_and_duplicate_sprite_tile_data()` after `DISPLAY_OFF`).

### Deferred Ideas (OUT OF SCOPE)

- Per-tile attribute granularity inside `metasprite { frame { } }`.
- Declarative defaults for flip/subPal inside `metasprite { }` block.
- Tile-duplication fallback for X/Y flip (the `#if !HARDWARE_SPRITE_CAN_FLIP_*` path).
- Variable-length OAM hiwater as a UAT anchor (4th behavior).
- Sub-pixel movement as a UAT anchor.
- DMG-only ROM.
- GBC-only ROM.
- Pre-inserting Phase 10.1 placeholder before port surfaces surplus.
- 4th comparison artifact (.asm diff or bank/section size).
- Moving metasprite primitive to Phase 13.
- Title screen / game-flow scene.

</user_constraints>

---

## Summary

Phase 10 adds a NEW `metasprite { frame { tile(x, y, id) } }` DSL primitive as a port substrate and uses it to re-implement the GBDK `metasprites` cross-platform example idiomatically. The reference C (`metasprites.c`, 309 lines) exercises three surfaces gbkt does not yet have: (1) GBDK's variable-length OAM descriptor model (`sprite_metasprites[]` array of pointer arrays), (2) runtime OAM attribute byte writes for flip and sub-palette, and (3) `cgb_compatibility()` + `set_sprite_palette()` GBC init. All three already have partial or full infrastructure in gbkt; this phase wires and extends that infrastructure.

The key finding is that **the GBC target surface (D-09) already works end-to-end**: `config { target(GbcTarget.GBC_COMPATIBLE) }` propagates `-Wm-yc` to lcc via the Gradle plugin; `val gray by palette { ... }` with the existing `palette { }` DSL already emits `const palette_color_t gray_pal[4]` + `set_sprite_palette()` via `visitSetPalette()` which already has the `PaletteType.SPRITE` branch; `<gb/cgb.h>` is conditionally included when palettes are present. The `cgb_compatibility()` call is the one genuine gap: it does not exist in gbkt's generated `main()` today. This is a small surgical addition to `GBDKPipelineV2.buildMainFunction()`. The MCP emulator GBC mode is available via `AgentSessionConfig(gbcMode = true)` — not a new tool, just a flag that must be set in the UAT test config for behavior 3.

The metasprite IR/DSL/visitor is a clean greenfield addition following the existing patterns (ActorIR → ActorBuilder → ActorDelegate → ActorVisitor). The new IR nodes go in `gbkt-ir`, the builder in `gbkt-lang`, the visitor in `gbkt-backend-gbdk`. The runtime accessors (`actor.flipX`, `actor.flipY`, `actor.subPalette`) extend the existing `ActorRef` + `ActorPropertyRef` pattern with three new property names that lower to `set_sprite_prop()` calls in the generated `update_sprites_metasprite_*()` function.

**Primary recommendation:** Follow the ~18-plan fan-out in D-14. The substrate decomposition (6–8 plans) and port/verification trail (10–12 plans) are clearly distinct phases of work; each plan can be atomically committed and tested in isolation.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Metasprite IR data model | gbkt-ir (leaf) | — | IR is always in gbkt-ir per module boundaries; zero deps |
| Metasprite DSL builder | gbkt-lang | — | All DSL builders live in gbkt-lang; depends on gbkt-ir |
| Metasprite analysis/validation | gbkt-analysis | gbkt-lang (builder-time) | Tile-count/baseId bounds can fold into builder; separate pass if cross-actor or cross-frame invariants needed |
| MetaspriteVisitor (tiledata) | gbkt-backend-gbdk/codegen/visitor | — | All codegen in backend; `set_sprite_data()` emission |
| MetaspriteVisitor (descriptor) | gbkt-backend-gbdk/codegen/visitor | — | `sprite_metasprites[]` equivalent emission |
| MetaspriteVisitor (frame switch + hiwater) | gbkt-backend-gbdk/codegen/visitor | — | Per-frame switch + `hide_sprites_range()` tail |
| Runtime accessor flipX/flipY lowering | gbkt-backend-gbdk/codegen/visitor | gbkt-lang (ActorRef surface) | New ActorPropertyRef names in ActorRef; lowering in visitor |
| Runtime accessor subPalette lowering | gbkt-backend-gbdk/codegen/visitor | gbkt-lang (ActorRef surface) | Same pattern; OAM attr byte write via `set_sprite_prop()` |
| GBC compat init (`cgb_compatibility()`) | gbkt-backend-gbdk/codegen/pipeline | gbkt-lang (CartridgeConfig.gbcTarget) | Conditional emission in `GBDKPipelineV2.buildMainFunction()` |
| Sprite palette DSL + `set_sprite_palette()` | Already exists | — | `PaletteType.SPRITE` path in visitSetPalette() + palette { } DSL + <gb/cgb.h> include |
| BG checkerboard fill | gbkt-backend-gbdk/codegen | — | `set_bkg_data()` + `fill_bkg_rect()` raw emissions in scene enter |
| GBC mode emulator boot | gbkt-emulator/agent | — | `AgentSessionConfig(gbcMode = true)` flag — exists today |
| ROM build + reference build | gbkt-gradle-plugin | Makefile (reference) | Standard `:buildRom` task + `GBDK_HOME=/Users/michalsvacha/gbdk make gb` |
| UAT capture (screenshots) | gbkt-emulator/agent | gbkt-mcp-server | `StepAgent.stepN()` + screenshot via JVM harness or MCP |

---

## Standard Stack

### Core (no new packages — all in-tree)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| gbkt-ir | project | New IR data classes: `MetaspriteIR`, `MetaspriteFrame`, `MetaspriteTile` | All IR nodes live here (module boundary enforced by `validateModuleBoundaries`) |
| gbkt-lang | project | New `MetaspriteBuilder`, `MetaspriteDelegate`, `MetaspriteRef` (if needed), extended `ActorRef` with `flipX`/`flipY`/`subPalette` | All DSL builders live here |
| gbkt-backend-gbdk | project | New `MetaspriteVisitor` (or extension to `ActorVisitor`), OAM attr lowering, `cgb_compatibility()` emission | All GBDK codegen lives here |
| gbkt-emulator | project | `StepAgent`, `AgentSessionConfig(gbcMode = true)`, `OamSpriteReader` (attribute byte introspection) | UAT MCP and JVM harness |
| gbkt-gradle-plugin | project | `:generateC`, `:buildRom`, `CompileRomTask` (adds `-Wm-yc` for GBC_COMPATIBLE) | Already wires GbcTarget to lcc flags |

### Supporting (reference external)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| GBDK-2020 lcc | local install | Compile reference ROM for oracle-comparison.md | Reference build only; not a gbkt dependency |
| png2asset | bundled with GBDK | Convert sprite.png → C metasprite descriptors (reference-side only) | Reference build only |

**No new external packages to install.** This phase is pure Kotlin/JVM in-tree work.

### Package Legitimacy Audit

> No external packages are installed in this phase. All code is in-tree.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
DSL (gbkt-lang)                     IR (gbkt-ir)                   Codegen (gbkt-backend-gbdk)
────────────────────────────────────────────────────────────────────────────────────────────
metasprite { frame { tile() } }  →  MetaspriteIR                →  MetaspriteVisitor
val playerMeta by metasprite {}     MetaspriteFrame               set_sprite_data() [scene enter]
                                    MetaspriteTile                sprite_metasprites[] descriptor
                                                                   play_frame switch on _meta_idx
                                                                   hide_sprites_range(hiwater, MAX)

actor.flipX set expr             →  Assign("player.flipX", ...)  →  set_sprite_prop(slot, OAMF_X_FLIP)
actor.flipY set expr             →  Assign("player.flipY", ...)  →  set_sprite_prop(slot, OAMF_Y_FLIP)
actor.subPalette set expr        →  Assign("player.subPalette".) →  set_sprite_prop(slot, OAMF_CGB_PAL*)

config { target(GBC_COMPATIBLE) } → CartridgeConfig.gbcTarget   →  cgb_compatibility() in main()

val gray by palette { ... }      →  GBCPalette(type=SPRITE)     →  set_sprite_palette(PAL0,1,gray_pal)
                                                                   + const palette_color_t gray_pal[4]

[runtime]
emulator_start(gbcMode=true)     — StepAgent / AgentSessionConfig(gbcMode=true)
emulator_step(buttons=[b])       — B-press edge → _meta_idx++
emulator_step(buttons=[a])       — A-press edge → _rot++; flipX/flipY/subPal update
emulator_screenshot(path=...)    — visual evidence capture
```

### Recommended Project Structure

```
gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/
└── MetaspriteIR.kt          # MetaspriteIR, MetaspriteFrame, MetaspriteTile data classes + registry key

gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/
└── MetaspriteBuilder.kt     # MetaspriteBuilder, MetaspriteFrameBuilder, MetaspriteDelegate, MetaspriteRef
                             # + GameBuilder.metasprite() factory
                             # + ActorRef extensions: .flipX, .flipY, .subPalette

gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/
└── MetaspriteVisitor.kt     # Sibling to ActorVisitor; 3 methods:
                             #   generateTileData(), generateDescriptor(), generateFrameSwitch()

gbkt-examples/metasprites/
├── build.gradle.kts
├── src/main/kotlin/.../Metasprites.kt
├── src/main/resources/sprites/elephant.png   # copy of res/sprite.png
└── src/test/kotlin/.../
    ├── MetaspriteIRTest.kt       # IR structure invariants
    ├── MetaspriteEmissionTest.kt # D-12 JVM emission invariants (3 tests)
    ├── MetaspriteUatTest.kt      # D-02 MCP/StepAgent UAT (3 behaviors + screenshots)
    └── MetaspriteGameTest.kt     # SimulationContext smoke tests (optional)

.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/
├── evidence/
│   ├── reference/BUILD.md
│   ├── oracle-comparison.md
│   ├── c-diff.md
│   ├── rom-size-comparison.md
│   └── uat-screenshots/
│       ├── behavior1-animation-advance.png
│       ├── behavior2-flip-cycle.png
│       └── behavior3-subpalette-cycle-gbc.png
├── 10-UAT.md
└── PLAYBOOK.md
```

---

## Reference C Structural Map

> The planner needs this to pin each plan to a section of `metasprites.c`.

| Section | Lines | Description | gbkt analog |
|---------|-------|-------------|-------------|
| Includes + constants | 1–49 | `#include` GBDK headers, `#define` TILE_WIDTH/HEIGHT, `NUM_BYTES_PER_TILE`, `ACC_X/Y`, `SPR_NUM_START`, `TILE_NUM_START`; checkerboard `pattern[]`; `reverse_bits[256]` lookup table | gbkt: constants as Kotlin `const val`; `reverse_bits` NOT ported (hardware flip used instead) |
| Global state | 50–64 | `int16_t PosX, PosY, SpdX, SpdY`; `uint8_t PosF, idx, rot`; `flipped_data[NUM_BYTES_PER_TILE]`; `joyp, old_joyp` | gbkt: `var posX/posY/spdX/spdY by i16Var()`; `var idx/rot by u8Var()` |
| `set_tile()` helper | 90–104 | Flips a tile in X/Y using `reverse_bits[]`, writes via `set_sprite_data()` | NOT ported — gbkt uses hardware OAM flip (`OAMF_X_FLIP`/`OAMF_Y_FLIP`); tile duplication is D-overfitting-3 rejected |
| `get_tile_offset()` helper | 106–118 | Returns tile offset for the pre-flipped duplicate | NOT ported — same reason; gbkt targets GBC/DMG with hardware flip |
| `load_and_duplicate_sprite_tile_data()` | 120–140 | Loads tiles + creates X/Y-flipped duplicates; sets `num_tiles` | gbkt: `scene.enter { }` emits `set_sprite_data(0, totalTiles, sprite_tiles)` — no duplication needed; direct hardware flip |
| GBC palette init | 142–174 | 4 `palette_color_t` arrays (gray/pink/cyan/green); `cgb_compatibility()` + 4× `set_sprite_palette()` calls; guarded by `#if defined(GAMEBOY)` | gbkt: 4× `val gray by palette { ... }` with `PaletteType.SPRITE`; `SetPalette` ops in scene enter; `cgb_compatibility()` emission added to pipeline for `GBC_COMPATIBLE` target |
| BG fill init | 176–181 | `fill_bkg_rect(0,0,DEVICE_SCREEN_WIDTH,HEIGHT,0)` + `set_bkg_data(0,1,pattern)` | gbkt: `set_bkg_data`/`fill_bkg_rect` raw C or new visitor method; checkerboard pattern is 16-byte constant |
| Display + sprite size init | 182–201 | `load_and_duplicate...()`, `SHOW_BKG; SHOW_SPRITES;`, sprite size selector (`SPRITES_8x8` for this example), `DISPLAY_ON`, initial pos/speed/idx/rot | gbkt: `scene.enter { showSprites(); posX set CENTER_X; ... }` + pipeline emits `SHOW_BKG; SHOW_SPRITES; DISPLAY_ON;` in `main()` already |
| Main loop: input poll | 203–207 | `KEY_INPUT` macro; `PosF = 0` | gbkt: `frame { }` loop; input via `dpad.*` / `buttons.*` |
| Main loop: dpad accel | 208–227 | Y-axis ±2 + clamp ±32; X-axis ±2 + clamp ±32 | gbkt: `whenever(dpad.up.held) { spdY -= 2; whenever(spdY isBelow -32) { spdY set -32 } }` etc. |
| Main loop: B-press (behavior 1) | 229–231 | `if KEY_PRESSED(J_B): idx++; if (idx >= sizeof(sprite_metasprites) >> 1) idx = 0;` | gbkt: `whenever(buttons.b.pressed) { idx++; whenever(idx isAtLeast NUM_FRAMES) { idx set 0 } }` |
| Main loop: A-press (behavior 2+3) | 233–237 | `if KEY_PRESSED(J_A): rot++; rot &= 0xF;` | gbkt: `whenever(buttons.a.pressed) { rot++; rot and 0xF }` (bitmasked) |
| Main loop: position integration | 239–239 | `PosX += SpdX; PosY += SpdY;` | gbkt: `posX += spdX; posY += spdY` |
| Main loop: subpal + flip switch | 241–283 | `uint8_t subpal = rot >> 2; switch (rot & 0x3) { case 1: move_metasprite_flipy(..., subpal,...); break; ... }` | gbkt: `actor.subPalette set (rot shr 2); actor.flipX set (...); actor.flipY set (...)` with 4-case `whenever` ladder |
| Main loop: hide tail | 287–287 | `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` | gbkt: MetaspriteVisitor emits this as the frame's OAM-tail cleanup; `hiwater` tracks dynamic tile count |
| Main loop: decel | 289–302 | X/Y decel toward zero (same pattern as Phase 9 simple-physics) | gbkt: `whenever(spdX isAbove 0) { spdX-- }` etc. — identical to Phase 9 pattern |
| vsync | 307 | `vsync()` | gbkt: pipeline already emits `vsync()` at end of game loop frame |

---

## Research Findings by Plan Area

### 1. Metasprite IR Node Shape

**Finding:** No `MetaspriteIR` exists in gbkt-ir today. It must be added as a new data class. [VERIFIED: codebase grep]

**Recommended shape:**

```kotlin
// gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt
data class MetaspriteIR(
    val id: String,                    // e.g. "playerMeta" (inferred from Kotlin property name)
    val frames: List<MetaspriteFrame>, // variable-length; each frame can have different tile count
    val sourceLocation: SourceLocation? = null,
)

data class MetaspriteFrame(
    val tiles: List<MetaspriteTile>,   // OAM entries for this frame
)

data class MetaspriteTile(
    val relX: Int,   // relative X offset from metasprite origin (pixels, hardware coords)
    val relY: Int,   // relative Y offset from metasprite origin (pixels, hardware coords)
    val tileId: Int, // base tile index into the sprite tile data VRAM block
)
```

`GameIR` must grow a `metasprites: List<MetaspriteIR>` field. Check `GameIR.kt` before writing — add alongside `actors`.

**Registry pattern:** Same as `ActorDelegate` — `GameBuilder.metasprite()` returns a `MetaspriteDelegate`; `provideDelegate` captures the Kotlin property name as `id`, calls `GameBuilderContext.current.registerMetasprite(...)`.

**Named ref:** `MetaspriteRef(val id: String)` — like `ActorRef`, with properties `flipX`, `flipY`, `subPalette` returning `ActorPropertyRef(id, "flipX")` etc. (the existing `ActorPropertyRef` type works for metasprite properties too — it just needs the operator extensions to be wired to the right `set_sprite_prop()` lowering).

### 2. Metasprite DSL Builder Shape

**Finding:** The `ActorDelegate` + `ActorBuilder` + `ActorRef` pattern is the template. [VERIFIED: codebase read]

**Recommended shape:**

```kotlin
// gbkt-lang: MetaspriteBuilder.kt
@GbktDsl
class MetaspriteTileBuilder {
    private var relX: Int = 0
    private var relY: Int = 0
    private var tileId: Int = 0
    fun tile(x: Int, y: Int, baseId: Int) { relX = x; relY = y; tileId = baseId }
    internal fun build() = MetaspriteTile(relX, relY, tileId)
}

@GbktDsl
class MetaspriteFrameBuilder {
    private val tiles = mutableListOf<MetaspriteTile>()
    fun tile(x: Int, y: Int, baseId: Int) {
        tiles += MetaspriteTile(x, y, baseId)
    }
    internal fun build() = MetaspriteFrame(tiles.toList())
}

@GbktDsl
class MetaspriteBuilder(val id: String) {
    private val frames = mutableListOf<MetaspriteFrame>()
    fun frame(block: MetaspriteFrameBuilder.() -> Unit) {
        frames += MetaspriteFrameBuilder().apply(block).build()
    }
    internal fun build() = MetaspriteIR(id, frames.toList(), captureV2Location())
}

class MetaspriteDelegate(private val block: MetaspriteBuilder.() -> Unit) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, MetaspriteRef> {
        val name = property.name
        val builder = MetaspriteBuilder(name).apply(block)
        val ir = builder.build()
        GameBuilderContext.current?.registerMetasprite(ir)
            ?: error("metasprite { } called outside a game { } block")
        return ReadOnlyProperty { _, _ -> MetaspriteRef(name) }
    }
}

fun metasprite(block: MetaspriteBuilder.() -> Unit): MetaspriteDelegate = MetaspriteDelegate(block)
```

**MetaspriteRef with OAM attribute property extensions:**

```kotlin
data class MetaspriteRef(val id: String) {
    val flipX: ActorPropertyRef get() = ActorPropertyRef(id, "flipX")
    val flipY: ActorPropertyRef get() = ActorPropertyRef(id, "flipY")
    val subPalette: ActorPropertyRef get() = ActorPropertyRef(id, "subPalette")
}
```

The existing `ActorPropertyRef.set(value: Expr)` / `set(value: Int)` / `set(value: Boolean)` operators work without modification because `MetaspriteRef` returns `ActorPropertyRef` instances.

### 3. Metasprite Visitor Decomposition

**Finding:** `ActorVisitor.kt` is an `object` (stateless). The new `MetaspriteVisitor` should be a sibling `object` in the same package. Three distinct functions needed: [VERIFIED: codebase read]

**Sub-area A — Tile data emission (`generateMetaspriteTileData`)**

Emits `set_sprite_data(startTile, totalTiles, sprite_tiles)` in scene enter. Unlike actors (which compute total tiles from sprite sheet width/height), metasprites carry explicit tile IDs. The total unique tiles = `max(tileId) + 1` across all frames (or can be passed from the asset pipeline).

For the reference port, the png2asset output (`sprite_tiles[]` array) is the tile source. The gbkt equivalent bundles the sprite asset in the `metasprite { }` block or points to the asset ref. This is a design decision for the builder — one option: `metasprite { sprite(asset("sprites/elephant.png")) { ... }; frame { ... } }` to mirror the actor shape.

**Sub-area B — Descriptor emission (`generateMetaspriteDescriptor`)**

GBDK's `sprite_metasprites[]` is an array of pointers to per-frame OAM entries:

```c
// GBDK generated by png2asset:
const METASPRITE_DEF sprite_metasprite_0[] = { {relX, relY, tileId}, ..., {metasprite_end} };
const METASPRITE_DEF sprite_metasprite_1[] = { {relX, relY, tileId}, ..., {metasprite_end} };
const METASPRITE_DEF* const sprite_metasprites[] = { sprite_metasprite_0, sprite_metasprite_1, ... };
```

gbkt's codegen must emit an equivalent structure. The key: `METASPRITE_DEF` is a `{int8_t dy, dx; uint8_t dtile;}` struct; the sentinel is `{metasprite_end}` (`{0x80, 0x80, 0x00}` in GBDK). The C AST model (`CRawCode`) is the escape hatch for struct literals.

**Sub-area C — Frame switch + hiwater cleanup (`generateMetaspriteFrameSwitch`)**

In the frame loop, the reference does:

```c
uint8_t hiwater = SPR_NUM_START;
switch (rot & 0x3) {
    case 1: hiwater += move_metasprite_flipy(sprite_metasprites[idx], TILE_NUM_START, subpal, hiwater, x, y); break;
    // ...
    default: hiwater += move_metasprite_ex(sprite_metasprites[idx], TILE_NUM_START, subpal, hiwater, x, y); break;
}
hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);
```

gbkt's codegen equivalent: the scene `frame { }` script ops lower to the play_frame C function. The visitor must:
1. Declare `uint8_t hiwater = 0u;` local variable
2. Emit `move_metasprite_ex(...)` or `move_metasprite_flipx/y/xy(...)` depending on the flipX/flipY state variables
3. Accumulate hiwater: `hiwater += move_metasprite_*(...)` return value
4. Emit `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` at frame end

The design question: does the metasprite frame switch live in the visitor (always emitted) or in ScriptOpVisitor (emitted when the user writes `moveMetasprite(ref)` in the DSL)? The Phase 9 pattern for actors is that `update_sprites()` is always emitted for actors with sprites; the analog for metasprites is `update_metasprite(ref)` emitted by a new ScriptOp (`MoveMetasprite`). This is the cleaner design — the user writes `moveMetasprite(playerMeta)` in the frame loop and the visitor handles flip state reading and hiwater tracking.

### 4. OAM Attribute Byte Writes (D-07/D-08)

**Finding:** The existing `ScriptOpVisitor.visitSetPalette()` already handles `PaletteType.SPRITE` → `set_sprite_palette()`. What does NOT exist today is lowering for `actor.flipX`, `actor.flipY`, `actor.subPalette` to `set_sprite_prop()`. [VERIFIED: codebase grep]

**The lowering path:**

When the ScriptOpVisitor encounters `Assign("player.flipX", expr, SET)`:
- Recognize that the property name `"flipX"` maps to OAM attribute manipulation (not a plain variable assignment)
- Emit `set_sprite_prop(slot, currentProp | OAMF_X_FLIP)` or `set_sprite_prop(slot, currentProp & ~OAMF_X_FLIP)`

The problem: `set_sprite_prop()` sets the OAM attribute byte for a specific hardware sprite slot, and flip bits are OR'd into the existing prop byte. The metasprite may span multiple hardware sprite slots (one per tile). The correct emission pattern writes `set_sprite_prop()` for each slot in the metasprite.

**Design recommendation:** Introduce a `SetMetaspriteAttr` ScriptOp (or handle specially in `visitAssign` when `objectId` resolves to a metasprite and `property` is one of `flipX`/`flipY`/`subPalette`). The special handling reads the metasprite's OAM slot assignments from the IR and emits `set_sprite_prop(slot_i, ...)` for each slot.

The reference's flip approach via `move_metasprite_flipx/y/xy()` functions is preferred over per-slot `set_sprite_prop()` because `move_metasprite_*` handles variable-length frames automatically. The flip/subpal state is tracked in user variables (`rot`); the visitor reads `rot & 0x3` to select the right `move_metasprite_*` variant at frame update time — this is an argument for encoding flip state as a bitmask variable rather than individual `flipX`/`flipY` booleans.

**D-08 sub-palette concern:** `OAMF_CGB_PAL0 = 0`, `OAMF_CGB_PAL1 = 1`, `OAMF_CGB_PAL2 = 2`, `OAMF_CGB_PAL3 = 3` — on DMG, the CGB palette bits (bits 0-2 of OAM attr byte) are ignored by hardware. GBDK's `set_sprite_prop()` writes the byte unconditionally; on DMG the hardware ignores it. So the gbkt emission can use `set_sprite_prop()` unconditionally — no conditional codegen needed. [ASSUMED based on GBDK hardware documentation; cross-reference with GBDK docs if needed]

**Unsigned literal convention (Phase 07.9):** OAM flag constants (`OAMF_X_FLIP`, `OAMF_Y_FLIP`, `OAMF_CGB_PAL0..3`) are unsigned bitmasks used in unsigned-context OR/AND operations. These must use `CLiteral(N)` (emits `Nu` suffix) per the literal emission convention in `gbkt-backend-gbdk/CLAUDE.md`. [VERIFIED: CLAUDE.md Literal Emission Convention section]

### 5. Runtime Accessor Surface (typed AssignablePropertyRef)

**Finding:** `ActorRef.x`, `ActorRef.y`, `ActorRef.visible` already exist in `ActorBuilder.kt`. Adding `flipX`, `flipY`, `subPalette` is a straightforward extension. [VERIFIED: codebase read]

```kotlin
// In MetaspriteRef (or extended ActorRef if metasprite binds to an actor):
val flipX: ActorPropertyRef get() = ActorPropertyRef(id, "flipX")
val flipY: ActorPropertyRef get() = ActorPropertyRef(id, "flipY")
val subPalette: ActorPropertyRef get() = ActorPropertyRef(id, "subPalette")
```

The existing operator extensions on `ActorPropertyRef` (set, +=, comparisons, bitwise) all work without modification. The visitor lowering is what needs to be new.

**Key question for the planner:** Should flipX/flipY/subPalette be properties on `MetaspriteRef` OR should the metasprite be modeled as an actor that has an attached metasprite? The reference's D-07 DSL example (`actor.flipX set (...)`) suggests the metasprite is associated with an actor. Recommendation: make `MetaspriteRef` a standalone ref (not an actor subtype) but share the `ActorPropertyRef` type for its writable attributes. The port assembly plan can clarify the final shape.

### 6. GBC Compatibility Surface (D-09)

**Finding — what already works:**

1. `config { target(GbcTarget.GBC_COMPATIBLE) }` → `CartridgeConfig.gbcTarget = GBC_COMPATIBLE` → `GenerateCTask` writes `gbcMode=COMPATIBLE` to `gbkt-build.properties` → `CompileRomTask` reads it and adds `-Wm-yc` to lcc args. This is fully wired. [VERIFIED: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:150`]

2. `val gray by palette { color0(...); ... }` → `GBCPalette(name="gray", colors=[...], type=PaletteType.SPRITE)` (NOTE: `PaletteBuilder.build()` defaults to `PaletteType.BACKGROUND` — the port must pass `PaletteType.SPRITE` explicitly or add a `spritePalette { }` variant). [VERIFIED: `PaletteBuilder.build(type: PaletteType = PaletteType.BACKGROUND)` line 89]

3. `visitSetPalette()` already has `PaletteType.SPRITE -> "set_sprite_palette"`. [VERIFIED: `ScriptOpVisitor.kt:1589-1590`]

4. `<gb/cgb.h>` is conditionally included in `main.c` and `game.h` when any palette exists. [VERIFIED: `GBDKPipelineV2.kt:1125-1126`]

**Finding — what is MISSING (the genuine gap):**

`cgb_compatibility()` call does NOT exist in gbkt's generated `main()` body. The current `buildMainFunction()` emits sound init, `DISPLAY_ON`, `SHOW_BKG`, `SHOW_SPRITES`, sprite data loads, OAM inits, then the game loop — but no `cgb_compatibility()`. [VERIFIED: `GBDKPipelineV2.kt:3531-3563`]

**Recommended fix:** In `GBDKPipelineV2.buildMainFunction()`, after sound init and BEFORE `DISPLAY_ON`, add:

```kotlin
if (gameIR.config.gbcTarget != GbcTarget.DMG) {
    add(CRawCode("cgb_compatibility();"))
    // optionally: add(CRawCode("cpu_fast();")) for GBC speed-doubling
}
```

This is a small surgical addition to the pipeline — not a new visitor, not a new IR node. It belongs in the "GBC compat surface" plan (Plan 10 in the frame).

**Finding — sprite palette DSL gap:**

The `PaletteDelegate.provideDelegate()` calls `PaletteBuilder.build()` with NO `type` argument — defaults to `PaletteType.BACKGROUND`. The port needs sprite palettes. Options:
- (A) Add `fun spritePalette(block: PaletteBuilder.() -> Unit): PaletteDelegate` factory that calls `build(PaletteType.SPRITE)` — preferred, minimal, clear intent
- (B) Add a `type(PaletteType)` method to `PaletteBuilder` — more flexible but the enum import leaks into user code

Option A is recommended: cleaner DSL, no new builder methods.

### 7. Reference ROM Reproducibility (D-11)

**Finding:** GBDK is installed at `/Users/michalsvacha/gbdk`. The Makefile's `GBDK_HOME` defaults to `../../../` relative to the example directory, which resolves to `/Users/michalsvacha/gbdk`. The reference build command is:

```bash
cd /Users/michalsvacha/gbdk/examples/cross-platform/metasprites
GBDK_HOME=/Users/michalsvacha/gbdk make gb
```

This produces `build/gb/metasprites.gb` + `.map` + `.noi` companion files. [VERIFIED: Makefile, matches Phase 9 `BUILD.md` pattern]

**Important:** The Makefile uses `-autobank` and `-Wl-j -Wm-yoA -Wm-ya4 -Wb-ext=.rel -Wb-v` flags. The reference ROM uses autobanking for the sprite asset. The gbkt port fits in HOME (no banking needed). ROM size comparison uses `l__CODE` from `.noi` file.

**Evidence directory layout (mirrors Phase 9):**

```
evidence/
├── reference/BUILD.md                  # Reproducible build instructions
├── oracle-comparison.md                # Three-signal summary (ROM size + C-diff + UAT verdict)
├── c-diff.md                           # Side-by-side metasprites.c vs gbkt main.c + bank1.c
├── rom-size-comparison.md              # l__CODE byte count comparison
├── uat-screenshots/
│   ├── behavior1-animation-advance.png
│   ├── behavior2-flip-cycle.png
│   └── behavior3-subpalette-cycle-gbc.png
└── buildrom-log.txt                    # lcc compile output (zero warnings target)
```

### 8. MCP GBC-Mode Boot (D-02 behavior 3)

**Finding:** `AgentSessionConfig.gbcMode: Boolean = false` already exists. Setting `gbcMode = true` causes `CoffeeGbEmulator.start()` to initialize with `GameboyType.CGB` instead of `GameboyType.DMG`. Coffee-GB renders GBC palettes in CGB mode. [VERIFIED: `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt:153`, `AgentSessionConfig.kt:42`]

**The UAT test for behavior 3 must use `AgentSessionConfig(romFile=..., gbcMode=true)`** — this is the only way to observe sub-palette color changes (on DMG the CGB palette bits are hardware-ignored).

**CONTEXT D-08 research question resolution:** `emulator_start` does NOT have a GBC/DMG mode flag in the MCP tool schema — the flag is in `AgentSessionConfig`. For JVM-tier UAT tests (using `StepAgent`), set `AgentSessionConfig(gbcMode = true)`. For MCP-tier scripts, the MCP server would need to pass `gbcMode=true` through to `AgentSessionConfig`. This is a candidate seed if MCP `emulator_start` cannot accept a `gbcMode` parameter — but the JVM-tier `StepAgent` approach (same as Phase 9) avoids this gap entirely.

**OAM attribute introspection for behavior 2 verification:** `OamSpriteReader` reads OAM bytes from emulator memory. The attribute byte (byte 3 of OAM entry) contains flip bits. For behavior 2 (flip state), variable assertion on `_rot` is reliable (it's a u8 variable); combined with visual screenshot, this satisfies D-02.

### 9. Validation Pass Analysis

**Finding:** A separate analysis pass for metasprite is NOT needed for Phase 10. Builder-time validation suffices. [VERIFIED: reasoning from analysis pass catalog]

Builder-time checks that should fire at `MetaspriteBuilder.build()`:
- `frames.isNotEmpty()` — must have at least one frame
- Each frame's `tiles.isNotEmpty()` — must have at least one tile
- All `tileId >= 0` — non-negative tile indices
- No out-of-bounds tileId checks (would need VRAM budget info not available at build time)

A separate analysis pass would be needed only if cross-metasprite invariants (e.g., tile VRAM budget across multiple metasprites) are required. Phase 10 has only one metasprite. If the planner wants to be conservative, add a minimal `MetaspriteValidationPass` with the same structure as existing passes — but this is skippable per CONTEXT D-04 ("only if research surfaces a need").

**Recommendation:** Fold validation into `MetaspriteBuilder.build()`. Skip the separate analysis pass plan unless the planner disagrees.

### 10. Three-Signal Comparison Execution (D-11)

**Concrete steps:**

1. **ROM size:** `grep '^DEF l__CODE' <path-to-metasprites.gb.noi>` for reference; same grep on gbkt output. Target: within 2× of reference l__CODE. [VERIFIED: Phase 9 BUILD.md pattern]

2. **Generated C diff:** Reference is `metasprites.c` (309 lines); gbkt generates `main.c` + `bank1.c`. The comparison is the game-logic surface (everything outside the pipeline's HOME-bank scaffolding). Shorter/clearer = win; over-emission → seeds.

3. **UAT verdict:** Three behavior probes with screenshots in `evidence/uat-screenshots/`.

**Evidence file: `evidence/oracle-comparison.md`** — same shape as Phase 9's `09-port.../evidence/oracle-comparison.md`. Populate with the three-signal summary table at phase close.

### 11. Tier-1 JVM Emission Invariants (D-12)

**Template from Phase 9:** `SimplePhysicsEmissionTest.kt` demonstrates the exact pattern — `GBDKPipelineV2().generate(game.build())`, extract bank1.c, call `extractFunctionBody("play_frame")`, assert/deny presence of C tokens. [VERIFIED: `SimplePhysicsEmissionTest.kt`]

**Three invariants for Phase 10:**

**D-12.1 — Animation index advance emission (behavior 1):**
```kotlin
// Assert: play_frame body contains idx increment + wrap
assertTrue(frameBody.contains("_idx++") || frameBody.contains("_idx = _idx + 1u"))
assertTrue(frameBody.contains("_idx = 0u") || frameBody.contains("_idx = 0"))
// OR: look for move_metasprite call using _idx
assertTrue(frameBody.contains("sprite_metasprites[_idx]") || frameBody.contains("_meta_idx"))
```

**D-12.2 — Flip OAM attribute byte write emission (behavior 2):**
```kotlin
// Assert: play_frame body contains move_metasprite_flipy / flipx / flipxy / move_metasprite_ex
// OR: set_sprite_prop calls with OAMF_X_FLIP / OAMF_Y_FLIP
assertTrue(
    frameBody.contains("move_metasprite_flipy") || 
    frameBody.contains("OAMF_Y_FLIP") || 
    frameBody.contains("move_metasprite_flipx") ||
    frameBody.contains("move_metasprite_flipxy")
)
```

**D-12.3 — Sub-palette OAM attribute byte write emission (behavior 3):**
```kotlin
// Assert: play_frame body contains OAMF_CGB_PAL or equivalent subpal parameter in move_metasprite_*
assertTrue(
    frameBody.contains("OAMF_CGB_PAL") ||
    frameBody.contains("_subPalette") ||
    frameBody.contains("_rot >> 2")  // subpal = rot >> 2
)
```

**Brace-walk helper (from Phase 9 — copy verbatim):**

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

The exact strings to assert will depend on the visitor's chosen emission pattern; the planner must leave the exact token patterns as a "RED → GREEN" contract established in Plan 17.

### 12. Named Codegen Bug-Fix Risk Topology (D-05)

**Plausible first-blocker candidates (do not pre-commit; let the build name it):**

| Risk | File(s) | Mechanism |
|------|---------|-----------|
| OAM-tail hiwater off-by-one on variable-length frames | `MetaspriteVisitor.kt` | Frame N has fewer tiles than frame N-1; hiwater must track the current frame's tile count, not a fixed max. If hiwater = 0 + (tiles in previous frame), sprites from prior frame bleed through. The reference's `hiwater += move_metasprite_*()` return value exactly tracks this. |
| Sprite palette emission ordering before `cgb_compatibility()` | `GBDKPipelineV2.kt` | `set_sprite_palette()` must be called AFTER `cgb_compatibility()` (GBC hardware requires CGB init before palette writes). If `SetPalette` ops are emitted in scene enter but `cgb_compatibility()` is missing from main(), sprite palettes are loaded in the wrong mode. The fix (add `cgb_compatibility()` to pipeline) resolves this. |
| `set_sprite_prop()` not flushed per frame after move | `MetaspriteVisitor.kt` | OAM attributes (flip, palette) are written as static values; `move_metasprite_*()` does NOT persist attribute bits — it only positions sprites. If the visitor emits flip/subpal once at init and never again, the attributes may be lost after the first move_metasprite call. The reference's `move_metasprite_*` variant selection (move_metasprite_flipy vs move_metasprite_flipxy) handles flip within the move itself. |
| Missing `<gbdk/metasprites.h>` include | `GBDKPipelineV2.kt` | `move_metasprite_*()` functions require `#include <gbdk/metasprites.h>`. If the pipeline only includes `<gb/gb.h>`, the linker will fail with undefined reference. Add conditional include when game has metasprites. |
| Signed-comparison in animation index wrap | `MetaspriteVisitor.kt` or ExprVisitor | `whenever(idx isAtLeast NUM_FRAMES)` emits a signed-context comparison if `idx` is declared `u8Var`. Per Phase 07.9 convention, the comparison RHS must use `CIntLiteral` for signed vars. `u8Var` is UINT8 — actually unsigned — so `CLiteral` is correct. But if `idx` is declared `i8Var`, the signed-comparison path fires. Use `u8Var` for `idx` to avoid this pitfall. |

### 13. Surplus Seed + Phase 10.1 Placeholder (D-06)

**Finding:** `/gsd-capture --seed` is the correct tool — confirmed by Phase 9 usage in `09-07-PLAN.md:156` (`git log --since=... .planning/seeds/`). The seed file format is `SEED-NNN-*.md`. [VERIFIED: Phase 9 plans]

**Phase 10.1 placeholder pattern:** Same as Phase 9 → Phase 9.1. At port-close, count seeds added since Phase 10 epoch. If ≥1, insert `Phase 10.1: metasprites surplus codegen defects` into ROADMAP.md in the same commit that marks Phase 10 complete.

### 14. Sprite PNG Asset Analysis (Reference)

**Finding:** `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/res/sprite.png` is 64×240 pixels, 5 animation frames of 48px each (from `-sh 48` in Makefile). Each frame is 64×48 = 8×6 tiles = 48 tiles in the grid. Non-empty tile count per frame: frames 0–4 have 31, 33, 33, 32, 32 non-empty tiles respectively. [VERIFIED: PNG dimensions extracted from file header; non-empty tile analysis via pixel inspection]

The gbkt metasprite definition for this sprite will define 5 frames, each with variable tile count (31–33 tiles). The DSL syntax:

```kotlin
val elephant by metasprite {
    sprite(asset("sprites/elephant.png"))   // if asset bundling is chosen
    frame { /* frame 0: 31 tiles */ tile(relX, relY, id); ... }
    frame { /* frame 1: 33 tiles */ tile(relX, relY, id); ... }
    // ... 5 frames total
}
```

The exact tile coordinates and IDs are determined by running `png2asset -sh 48 -spr8x8 -noflip -c sprite.c` and reading the generated `sprite_metasprites[]` array from the reference. The port assembly plan must read the reference-generated `sprite.c` to populate the DSL tile coordinates.

### 15. Validation Architecture

**Finding:** `workflow.nyquist_validation` is not explicitly set to `false` in `.planning/config.json`. [VERIFIED: config.json read]

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 (Kotlin Test) — same as all gbkt examples |
| Config file | Inherited via BOM; no per-example junit config |
| Quick run command | `./gradlew :gbkt-examples:metasprites:test` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-01.1 | B pressed → animation index advances + visible frame change | UAT (StepAgent) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteUatTest*behavior1*"` | No — Wave 0 |
| D-01.2 | A pressed → cycles 4 flip states via OAM attribute | UAT (StepAgent) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteUatTest*behavior2*"` | No — Wave 0 |
| D-01.3 | A pressed (after wrap) → cycles 4 sub-palettes (GBC mode) | UAT (StepAgent, gbcMode=true) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteUatTest*behavior3*"` | No — Wave 0 |
| D-12.1 | Animation index advance emission | JVM emission (D-12) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteEmissionTest*D-12_1*"` | No — Wave 0 |
| D-12.2 | Flip OAM attribute byte write emission | JVM emission (D-12) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteEmissionTest*D-12_2*"` | No — Wave 0 |
| D-12.3 | Sub-palette OAM attribute byte write emission | JVM emission (D-12) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteEmissionTest*D-12_3*"` | No — Wave 0 |
| D-04 IR | MetaspriteIR shape integrity | JVM IR test | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteIRTest*"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :gbkt-ir:test :gbkt-lang:test :gbkt-backend-gbdk:test` (whichever module was touched)
- **Per wave merge:** `./gradlew :gbkt-examples:metasprites:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- `gbkt-examples/metasprites/src/test/kotlin/.../MetaspriteIRTest.kt` — IR structure invariants
- `gbkt-examples/metasprites/src/test/kotlin/.../MetaspriteEmissionTest.kt` — D-12 invariants (RED → GREEN pattern matching Phase 9)
- `gbkt-examples/metasprites/src/test/kotlin/.../MetaspriteUatTest.kt` — D-02 UAT with StepAgent + screenshots
- `gbkt-examples/metasprites/build.gradle.kts` — example module config
- `gbkt-examples/settings.gradle.kts` — add `:gbkt-examples:metasprites` include

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OAM tail cleanup | Custom sprite-hide loop | `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` — already in `ActorVisitor.generateHideSpritesRange()` | Phase 07.3 / 07.4-32 proved the hiwater pattern is load-bearing; reinventing it creates new off-by-one risks |
| Sprite flip in software | Tile duplication + bit-reversal (reference's `set_tile()` / `reverse_bits[]`) | Hardware flip via `OAMF_X_FLIP` / `OAMF_Y_FLIP` in OAM attribute byte | gbkt targets GBC/DMG/Pocket — all support hardware flip; D-overfitting-3 explicitly rejects this |
| Metasprite descriptor format | Custom struct in C AST | `move_metasprite_*()` GBDK functions + GBDK's `METASPRITE_DEF` struct | GBDK's metasprite library handles variable-length OAM per frame, sentinel detection, and hardware coordinate offsets |
| Signed comparison literal | Emit `CLiteral(N)` for all literals | `CIntLiteral(N)` for signed comparison RHS (per Phase 07.9 convention) | SDCC warning 94 + always-false comparison bugs (Phase 07.4 post-mortem) |
| `cgb_compatibility()` alternative | Runtime GBC detect with `_cpu == CGB_TYPE` | `cgb_compatibility()` GBDK macro in `main()` | `cgb_compatibility()` is the standard GBDK idiom; it handles dual DMG/GBC boot sequencing |
| Brace-walk in awk | File-level grep for emission tests | Kotlin `extractFunctionBody()` helper (from Phase 9 `SimplePhysicsEmissionTest.kt`) | Scope-level grep gate — file-level grep cannot distinguish `play_frame` from `play_enter` etc. |

---

## Common Pitfalls

### Pitfall 1: hiwater Off-By-One on Variable-Length Frames
**What goes wrong:** Frame N has fewer tiles than frame N-1. If hiwater doesn't account for the current frame's tile count, old sprites from the previous frame bleed through (visible artifact: ghost tiles from prior animation frame).
**Why it happens:** Static OAM slot tracking assumes fixed tile count per actor. Metasprites have variable tile count per frame.
**How to avoid:** The visitor must declare `hiwater` as a local variable initialized to `SPR_NUM_START`, increment by the return value of `move_metasprite_*()` (which returns the number of hardware sprites used), and emit `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` unconditionally after every frame.
**Warning signs:** Animation frame transitions show ghost sprites; `hide_sprites_range()` missing or called with wrong hiwater.

### Pitfall 2: Sprite Palette Loaded Before `cgb_compatibility()`
**What goes wrong:** `set_sprite_palette()` called before `cgb_compatibility()` → GBC palette hardware not initialized → incorrect palette behavior on GBC.
**Why it happens:** `cgb_compatibility()` must run before any GBC-specific hardware access. Without it, the ROM boots in DMG mode even on GBC hardware.
**How to avoid:** Pipeline emits `cgb_compatibility()` as the FIRST statement in `main()` when `gbcTarget != DMG`. Palette loads happen later (scene enter or after `DISPLAY_ON`).
**Warning signs:** Sprites display in wrong colors on GBC; visual evidence for behavior 3 shows grayscale even in GBC emulator mode.

### Pitfall 3: PaletteType.SPRITE Not Set on Palette Builder
**What goes wrong:** `val gray by palette { ... }` defaults to `PaletteType.BACKGROUND`, emitting `set_bkg_palette()` instead of `set_sprite_palette()`. Sprite colors are wrong.
**Why it happens:** `PaletteBuilder.build()` defaults to `PaletteType.BACKGROUND`. The `PaletteType.SPRITE` enum value exists but is not default.
**How to avoid:** Add `fun spritePalette(block: PaletteBuilder.() -> Unit): PaletteDelegate` factory OR add a `type(PaletteType.SPRITE)` call in the builder block. The former is cleaner.
**Warning signs:** GBDK compiler emits warning about mismatched palette type; sprite colors unchanged by palette cycling.

### Pitfall 4: `<gbdk/metasprites.h>` Missing
**What goes wrong:** `move_metasprite_ex()`, `move_metasprite_flipx/y/xy()` are undefined at compile time → lcc linker error.
**Why it happens:** The pipeline includes `<gb/gb.h>` and conditionally `<gb/cgb.h>`, but not `<gbdk/metasprites.h>`.
**How to avoid:** Add a conditional include in `GBDKPipelineV2.buildHomeFile()` or `buildSceneFile()` when `gameIR.metasprites.isNotEmpty()`.
**Warning signs:** First lcc compile fails with "undefined identifier: move_metasprite_ex".

### Pitfall 5: debugGraphics = true with Metasprite Tile Load
**What goes wrong:** If `debugGraphics = true` is set anywhere in the game config, `printf()` calls are emitted into `load_and_duplicate_sprite_tile_data()` equivalent, corrupting the background tile layer.
**Why it happens:** GBDK `printf()` writes to the BG tile layer.
**How to avoid:** Set `debugGraphics = false` (or don't set it; default should be false for production games). The metasprites port uses a BG checkerboard pattern — any printf corruption would be immediately visible.
**Warning signs:** BG checkerboard replaced by ASCII character garbage on first frame.

### Pitfall 6: Unsigned literal in signed animation-idx comparison
**What goes wrong:** `whenever(idx isAtLeast NUM_FRAMES)` where `idx` is `i8Var` → SDCC warning 94 (comparison always false due to limited range) if `NUM_FRAMES > 127`.
**Why it happens:** Phase 07.9 literal convention.
**How to avoid:** Declare `idx` as `u8Var` (UINT8) since frame counts are always positive. The reference uses `uint8_t idx`.
**Warning signs:** SDCC warning 94 on build.

### Pitfall 7: Coffee-GB sub-palette rendering (behavior 3 evidence)
**What goes wrong:** Screenshots captured with `gbcMode = false` (DMG mode) show no color difference when cycling sub-palettes — screenshot is correct for DMG hardware but NOT evidence of behavior 3.
**Why it happens:** DMG hardware ignores CGB palette bits.
**How to avoid:** Per D-02 and CLAUDE.md Visual Evidence Rule: behavior 3 screenshot MUST be captured with `AgentSessionConfig(gbcMode = true)`. A DMG screenshot is NOT accepted as evidence for behavior 3.
**Warning signs:** Three UAT screenshots all identical grayscale → behavior 3 screenshot captured in wrong mode.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| File-level grep for codegen tests | Scope-level awk brace-walk then grep | Phase 07.4 Plan 23 (codified in CLAUDE.md) | Prevents false GREEN from unrelated functions masking regressions |
| CLiteral for all signed comparison RHS | CIntLiteral for signed-context comparison RHS | Phase 07.9 | Fixes SDCC warning 94 + always-false comparisons in signed arithmetic |
| Sealed IR interfaces (v1) | Non-sealed + visitor dispatch (v2) | Phase 05 modular split | Enables per-module IR extensions without modifying leaf module |
| `actor { animationStates { } }` for frame cycling | New `metasprite { frame { } }` primitive | This phase (Phase 10) | Faithful variable-length OAM descriptor model; enables hiwater-correct rendering |

---

## Open Questions (RESOLVED)

1. **Should metasprite attach to an actor or be standalone?**
   - RESOLVED: Standalone — MetaspriteRef is its own type, not an ActorRef subtype. Port uses `val elephant by metasprite { }` and exposes `elephant.flipX/flipY/subPalette` (mirrors PATTERNS row 10-3.A; D-04 + D-07).
   - What we know: D-07 DSL uses `actor.flipX set ...` syntax, suggesting metasprite is actor-adjacent.
   - What's unclear: Whether the port should use `val player by actor { ... }; val playerMeta by metasprite { ... }` with a separate binding, or `val player by metasprite { ... }` where MetaspriteRef IS the actor.
   - Recommendation: MetaspriteRef is standalone (not an actor subtype). The port uses `val elephant by metasprite { ... }` and exposes `elephant.flipX`, `elephant.flipY`, `elephant.subPalette`. This is cleaner and avoids overloading ActorIR with metasprite concerns.

2. **How does png2asset output map to MetaspriteFrame tile coordinates?**
   - RESOLVED: Plan 10-13 (port assembly) reads the reference-generated `sprite.c` (`METASPRITE_DEF` entries with {dy,dx,dtile}) and transcribes tile coordinates into the DSL `frame { tile(x,y,id) }` calls. Acceptance: dy/dx/dtile values in DSL match the reference sprite.c byte-for-byte.
   - What we know: png2asset with `-sh 48 -spr8x8 -noflip` generates `METASPRITE_DEF` entries with `{dy, dx, dtile}` where dy/dx are relative to the metasprite origin and dtile is the tile VRAM index offset.
   - What's unclear: The exact tile coordinates and tile IDs for each of the 5 frames require reading the generated `sprite.c` output. This is a port assembly discovery moment.
   - Recommendation: The planner should include a "read png2asset output" step in the port assembly plan; the executor runs `png2asset` and transcribes tile coordinates into the DSL `frame { tile(x, y, id) }` calls.

3. **Is a `moveMetasprite(ref)` ScriptOp needed or can the visitor emit the frame-switch logic automatically?**
   - RESOLVED: Yes — approach (b). Plan 10-04 adds `MoveMetasprite` ScriptOp + ScriptOpVisitor case; the user writes `moveMetasprite(elephant)` in the frame loop and the visitor emits the full switch + hiwater + hide_sprites_range. Composability + state-aware lowering both ride on this op.
   - What we know: Phase 9 actors emit `update_sprites()` automatically every frame (pipeline hardcodes the call). A similar approach for metasprites would always emit `move_metasprite_ex(...)` using the metasprite's state variables.
   - What's unclear: The flip/subpal variant selection requires reading runtime variables (`_rot & 0x3`), which means either (a) the codegen always emits a switch on a known variable, or (b) the user emits a `moveMetasprite(elephant)` ScriptOp that the visitor lowers with the current flip/subpal state.
   - Recommendation: Approach (b) — a new `MoveMetasprite` ScriptOp is cleaner and more composable. The user writes `moveMetasprite(elephant)` in the frame loop and the visitor emits the full switch + hiwater + hide_sprites_range.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GBDK-2020 lcc | Reference ROM build (D-11) | Assumed available at `/Users/michalsvacha/gbdk` | Matches Phase 9 setup | Cannot build reference ROM — use Phase 9's BUILD.md as template |
| png2asset | Reference sprite descriptor generation | Bundled with GBDK at `/Users/michalsvacha/gbdk/bin/png2asset` | Same as GBDK | Run manually in port assembly plan |
| Coffee-GB (JVM) | UAT GBC mode testing | ✓ (in gbkt-emulator module) | From `eu.rekawek.coffeegb:coffee-gb` BOM | — |
| Gradle | Build + test | ✓ | 9.0 | — |
| JVM | Runtime | ✓ | 21 | — |

---

## Security Domain

> This phase adds game DSL primitives and OAM rendering logic. No authentication, session management, network I/O, cryptography, or user data is involved. ASVS security controls are not applicable to Game Boy ROM codegen.

---

## Sources

### Primary (HIGH confidence — codebase verified)

- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` — ActorRef, ActorPropertyRef, ActorDelegate patterns; operator extensions
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt` — generateUpdateSprites(), generateHideSpritesRange(), generateSpriteDataLoad(), OAM slot tracking
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt:1586-1596` — visitSetPalette() with PaletteType.SPRITE branch
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:3531-3563` — buildMainFunction() body (no cgb_compatibility() present)
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:980-995` — palette data array emission, cgb.h conditional include
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/PaletteBuilder.kt:89` — PaletteBuilder.build() defaults to PaletteType.BACKGROUND
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/CoreTypes.kt:327-343` — GBCPalette with PaletteType.SPRITE
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt:153` — gbcMode → GameboyType.CGB
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt:42` — gbcMode field
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:150` — -Wm-yc for GBC_COMPATIBLE
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsEmissionTest.kt` — brace-walk helper + D-12 pattern
- `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c` — reference C oracle (all 309 lines read)
- `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/Makefile` — reference build invocation
- `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/res/sprite.png` — 64×240px, 5 frames of 48px, variable non-empty tile count per frame (31–33 tiles)
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/oracle-comparison.md` — Phase 9 three-signal artifact shape
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/BUILD.md` — reference ROM build pattern
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-23-PLAN.md:151-169` — canonical awk brace-walk pattern
- `CLAUDE.md` — Visual Evidence Rule, Scope-level grep gates, BANKED calling convention, debugGraphics

### Secondary (MEDIUM confidence)

- `.planning/STATE.md` — current pivot to reference-port track; Phase 09.4 SHIPPED confirmation
- `.planning/phases/06.6-deferred-gaps-dsl-gbc-audio/06.6-02-SUMMARY.md` — GbcTarget enum + target() DSL surface confirmation

### Tertiary (LOW confidence)

- GBDK OAMF_CGB_PAL0-3 bit behavior on DMG (that DMG ignores CGB palette bits) — training knowledge, verified by reference C's `#if defined(GAMEBOY)` guard comment [ASSUMED]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | DMG hardware ignores OAMF_CGB_PAL bits (sub-palette 0–3 writes via set_sprite_prop() are no-ops on DMG) | Research Finding 4 (D-08) | If DMG panics or corrupts OAM on OAMF_CGB_PAL writes, conditional codegen is needed (`#if defined(GAMEBOY)` guard) — similar to reference's palette init guard |
| A2 | `move_metasprite_*()` functions return the number of hardware sprites consumed (enabling hiwater tracking) | Research Finding 3 (sub-area C) | If the return value is a different convention, hiwater tracking approach must change |
| A3 | Coffee-GB renders GBC sub-palette colors in CGB mode with sufficient fidelity to visually distinguish the 4 palette variants in a screenshot | Research Finding 8 | If Coffee-GB renders all palettes identical, behavior 3 visual evidence cannot be captured via emulator; UAT would require mGBA or manual verification |

---

## Metadata

**Confidence breakdown:**
- GBC target surface (D-09): HIGH — fully verified in Gradle plugin + pipeline + ScriptOpVisitor
- Metasprite IR/DSL/visitor design: HIGH — pattern is clear from ActorIR analogs; details are design decisions for planner
- OAM attribute lowering (flipX/flipY/subPalette): HIGH — ActorPropertyRef mechanism verified; exact GBDK API calls confirmed from reference C
- Reference ROM build reproducibility: HIGH — Makefile read; GBDK path confirmed from Phase 9 BUILD.md
- MCP GBC mode boot: HIGH — AgentSessionConfig.gbcMode confirmed in emulator code
- Named codegen bug-fix candidates: MEDIUM — plausible from code analysis but unverified until first build
- Coffee-GB palette rendering fidelity: MEDIUM/ASSUMED — not directly verified in emulator code

**Research date:** 2026-05-18
**Valid until:** 2026-06-18 (30 days; stable domain)
