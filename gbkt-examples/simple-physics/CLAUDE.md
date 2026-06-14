# Simple Physics — Developer Notes

## Three Audience Angles

`simple-physics` is the smallest end-to-end gbkt game — the **codegen oracle** for
sub-pixel physics. Generated C maps 1:1 to GBDK's `phys.c`.

- **For game authors:** One scene, one sprite, raw `i16Var`, no genre packages.
  Read top-to-bottom in under a minute.
- **For framework contributors:** The codegen regression bar for sub-pixel physics.
  `SimplePhysicsEmissionTest` pins the generated C shape; any DSL or codegen change
  that breaks this file breaks the oracle.
- **For physics authors:** 12.4 fixed-point packed in `INT16`. `shr 4` extracts the
  pixel coordinate. Constants mirror `phys.c` L30-34 verbatim.

## Build Commands

```bash
./gradlew :gbkt-examples:simple-physics:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:simple-physics:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:simple-physics:test        # Run tests
./gradlew :gbkt-examples:simple-physics:runEmulator # Boot ROM in emulator
```

## Code Structure

`SimplePhysics.kt`:

1. Config: `ROM_ONLY`, single-bank ROM (default banking)
2. Variables: `posX`, `posY`, `spdX`, `spdY` declared via `i16Var` (signed 16-bit;
   12.4 fixed-point — high 12 bits = pixel coordinate, low 4 bits = sub-pixel)
3. One 8x8 PNG sprite asset: `res/sprites/ball.png`
4. Single `play` scene with `enter { }` and `frame { }` lifecycle hooks
5. Frame loop applies acceleration (D-pad), integrates velocity into position,
   clamps velocity against ±MAX_*_SPEED_IN_SUBPIXELS via signed comparison, then
   renders sprite at `posX shr 4`, `posY shr 4`

## Key DSL Patterns

### Signed Speed Clamp

Mirrors `phys.c` L67-L80 clamps. Each axis bounds velocity to
±`MAX_*_SPEED_IN_SUBPIXELS` (= ±64 sub-pixels = ±4 pixels/frame):

```kotlin
runIf(dpad.up.held) {
    spdY -= Y_ACCELERATION_IN_SUBPIXELS
    runIf(spdY isBelow -MAX_Y_SPEED_IN_SUBPIXELS) { spdY set -MAX_Y_SPEED_IN_SUBPIXELS }
}
```

Position is NOT clamped — the reference allows off-screen scroll; the port matches.

### Sub-Pixel to Pixel Conversion

`ActorRef.moveTo(Expr, Expr)` (added in Phase 9.1 Plan 01 — SEED-002) is the
idiomatic sub-pixel-to-pixel call. It lowers to a single `SetPosition` op:

```kotlin
// Reference's: SPRITE_X = posX >> 4; (phys.c L90)
ball.moveTo(posX shr 4, posY shr 4)   // 12.4 fixed → integer pixel coordinate
```

The GBDK backend bundles this through `update_sprites()` →
`move_sprite(0u, _ball_x + 8u, _ball_y + 16u)` per frame.

## How to Modify

- **Change max speed clamp:** Adjust `MAX_X_SPEED_IN_SUBPIXELS` /
  `MAX_Y_SPEED_IN_SUBPIXELS` at the top of `SimplePhysics.kt`.
- **Change acceleration:** Adjust `X_ACCELERATION_IN_SUBPIXELS` /
  `Y_ACCELERATION_IN_SUBPIXELS`.
- **Change jump impulse:** Adjust `JUMP_ACCELERATION_IN_SUBPIXELS` (mirrors
  `phys.c` `#define`).

## Dependencies

- `gbkt-backend-gbdk` — Game Boy C code generation
- (via BOM) `gbkt-ir`, `gbkt-lang`, `gbkt-engine`, `gbkt-core`
- No genre packages (pure primitive DSL)
