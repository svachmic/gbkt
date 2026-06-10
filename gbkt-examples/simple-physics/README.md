# Simple Physics

simple-physics is the shortest path from `game { }` to a working ROM.

A single 8×8 ball sprite drifts under sub-pixel physics: hold any D-pad
direction to accelerate, release to coast back to rest, press A for an upward
jump impulse. Position and velocity are stored as 12.4 fixed-point signed
integers (`i16Var`); the `phys.c` GBDK reference example is mirrored as the
codegen oracle.

## How to Play

| Button | Action |
|--------|--------|
| D-pad Left / Right | Apply horizontal thrust |
| D-pad Up / Down    | Apply vertical thrust |
| A                  | Upward jump impulse (edge-triggered) |

## For game authors

One scene, one sprite, four `i16Var`s, no genre packages. The whole game is
about 140 lines of Kotlin top-to-bottom in
`src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt`.
If you want the smallest possible end-to-end gbkt program to read, start here.

## For framework contributors

simple-physics is the **codegen oracle** for sub-pixel physics. The generated C
maps 1:1 to GBDK's `phys.c`
(committed verbatim under
`.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/phys.c`).
`SimplePhysicsEmissionTest` pins the generated C shape — any DSL or codegen
change that breaks this file breaks the oracle. The four-tier verification stack
covers `SimplePhysicsIRTest` (IR shape) → `SimplePhysicsEmissionTest` (codegen
shape) → `GameTest` (JVM simulation) → `UatTest` (emulator playback against
`PLAYBOOK.md`).

## For physics authors

12.4 fixed-point packed in `INT16`: the high 12 bits are the pixel coordinate,
the low 4 bits are the sub-pixel fraction. `posX shr 4` extracts the pixel
coordinate for sprite rendering. The seven `*_IN_SUBPIXELS` constants at the
top of `SimplePhysics.kt` mirror `phys.c` L30-34 verbatim
(`MAX_X_SPEED_IN_SUBPIXELS`, `MAX_Y_SPEED_IN_SUBPIXELS`,
`X_ACCELERATION_IN_SUBPIXELS`, `Y_ACCELERATION_IN_SUBPIXELS`,
`JUMP_ACCELERATION_IN_SUBPIXELS`, `INITIAL_POS_IN_SUBPIXELS`).

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:simple-physics:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:simple-physics:buildRom

# Run in emulator
./gradlew :gbkt-examples:simple-physics:runEmulator

# Run tests
./gradlew :gbkt-examples:simple-physics:test
```

Generated C: `gbkt-examples/simple-physics/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb`

## Playbook

See [PLAYBOOK.md](PLAYBOOK.md) for the MCP-driven verification scenarios that
back the Phase 9 UAT contract.

**Coming in Phase 13:** typed Cartridge, if/unless DSL, subpixel { } abstraction.
