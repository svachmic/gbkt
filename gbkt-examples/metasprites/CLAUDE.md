# Metasprites — Developer Notes

## Three Audience Angles

`metasprites` is the smallest end-to-end gbkt game using the **metasprite primitive** — the
**codegen oracle** for variable-length OAM composition + GBC sub-palette cycling.

- **For game authors:** One scene, one 5-frame composite sprite, `u8Var` animation/rotation
  state, `i16Var` sub-pixel physics. Read top-to-bottom in under two minutes.
- **For framework contributors:** The codegen regression bar for `MetaspriteIR` and `MoveMetasprite`
  ops. `MetaspriteIRTest` pins the IR shape; any DSL or IR change that breaks this file breaks
  the metasprite contract.
- **For GBC authors:** Minimal `spritePalette { }` + `target(GbcTarget.GBC_COMPATIBLE)` recipe for
  sub-palette cycling — four sprite palettes (gray, pink, cyan, green), each assigned via `rot >> 2`.

## Build Commands

```bash
./gradlew :gbkt-examples:metasprites:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:metasprites:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:metasprites:test        # Run IR validation tests
./gradlew :gbkt-examples:metasprites:runEmulator # Boot ROM in emulator
```

## Code Structure

`Metasprites.kt` (≤150 lines — Phase 13.3 ROADMAP SC):

1. Config: `ROM_ONLY`, `GbcTarget.GBC_COMPATIBLE`
2. Variables: `posX`/`posY`/`spdX`/`spdY` via `i16FixedVar`/`i16Var` (12.4 fixed-point);
   `idx`/`rot` via `u8Var` (Pitfall 6: must be unsigned for wrap comparisons)
3. Four sprite palettes: `gray`, `pink`, `cyan`, `green` via `spritePalette { }` using
   `Color.rgb555(...)` — 5-bit native components, no precision loss (Plan 13.3-01 Req #7)
4. `val elephant by metasprite { sprite(asset("sprites/elephant.png")) { mode/pivot/frameSize }; frames(5) }`
   — asset-driven; zero tile transcription; png2asset cuts frames from the PNG at build time
5. Single `play` scene with:
   - `enter`: `showSprites()`, `bgFillCheckerboard()`, initial state reset
   - `every.frame`: D-pad acceleration, B → `idx++` with wrap, A → `rot++` with 4-bit mask,
     position integration, `moveMetasprite(elephant)`, decel ladder

## Key DSL Patterns

### Asset-Driven Metasprite (Plan 13.3-09 migration)

The elephant is declared with `sprite(asset(...)) { mode/pivot/frameSize }` + `frames(N)`:

```kotlin
val elephant by metasprite {
    sprite(asset("sprites/elephant.png")) {
        mode(SpriteMode.SPR8x8)
        pivot(0, 0)
        frameSize(64, 48)
    }
    frames(NUM_FRAMES) // cross-validates against png2asset output at build time
}
```

This replaces 175+ lines of manually transcribed `frame { tile(relX, relY, tileId) }` blocks.
The asset pipeline (png2asset via `ConvertSpritesTask`) generates the C tile arrays automatically.

### Color.rgb555 for Palette Colors

All four sub-palettes use `Color.rgb555(r, g, b)` with native 5-bit hardware components —
no precision loss because the original RGB888 values were exact multiples of 8 (≡ 0 mod 8).

### u8Var for Animation/Rotation State

`idx` and `rot` are declared with `u8Var` (NOT `i8Var`). This matters because wrap comparisons
like `idx isAtLeast NUM_FRAMES` use unsigned semantics. Using `i8Var` would cause comparison
to fail for values ≥ 128 (Pitfall 6 in Phase 10 PATTERNS.md).

### Sub-Palette Cycling

`rot` encodes both flip state and sub-palette:
- `rot & 0x3` — flip state (0=normal, 1=flipY, 2=flipXY, 3=flipX)
- `rot >> 2` — sub-palette index (0=gray, 1=pink, 2=cyan, 3=green)

The `rot and 0xF` wrap mask limits the cycle to 16 steps (4 flip states × 4 palettes).

## PHASE-13 TODOs

Items tracked during Phase 13 assembly:

1. ~~`MetaspriteBuilder.sprite()` method is missing~~ — **RESOLVED in Plan 13.3-05/06.**
   `sprite(asset(...))` + `frames(N)` are implemented; the asset-driven elephant declaration
   collapses to a single block with zero tile transcription.
2. Sprite palette slot assignment for multi-palette scenes: `SceneBuilder.palette(p)` defaults
   all palettes to slot 0. Explicit per-slot assignment for 4 sub-palettes requires a DSL
   enhancement — **still deferred to Plan 18**.

## Dependencies

- `gbkt-backend-gbdk` — Game Boy C code generation
- (via BOM) `gbkt-ir`, `gbkt-lang`, `gbkt-engine`, `gbkt-core`
- No genre packages (pure primitive DSL)
