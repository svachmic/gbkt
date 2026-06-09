# Metasprites Example

Port of the GBDK cross-platform `metasprites` example to idiomatic gbkt.

Demonstrates variable-length OAM composition, GBC sub-palette cycling, and hardware
flip (X/Y) — the three behaviors from the original GBDK example.

## What it shows

- **`val elephant by metasprite { frame { tile(...) } }`** — a 5-frame animated composite sprite
  built from multiple 8×8 hardware OAM tiles per frame.
- **`spritePalette { color0(...) ... }`** — four GBC sprite sub-palettes (gray, pink, cyan, green).
- **`config { target(GbcTarget.GBC_COMPATIBLE) }`** — runs on both DMG hardware and GBC hardware;
  GBC hardware enables color sub-palette assignment.
- **`bgFillCheckerboard()`** — fills the background layer with a 1-tile checkerboard pattern
  (visual parity with the original metasprites.c).
- Sub-pixel physics (i16Var, 12.4 fixed-point) — same acceleration model as `simple-physics`.

## Controls

| Input | Effect |
|-------|--------|
| D-pad | Move the elephant sprite |
| B | Advance animation frame (cycles 0 → 4 → 0) |
| A | Cycle flip state and sub-palette (rot 0-15: bits 0-1 = flip, bits 2-3 = sub-palette) |

## Build Commands

```bash
./gradlew :gbkt-examples:metasprites:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:metasprites:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:metasprites:test        # Run IR validation tests
./gradlew :gbkt-examples:metasprites:runEmulator # Boot ROM in emulator
```

## Three Audience Angles

**For game authors:** One scene, one metasprite, raw `i16Var` for physics, `u8Var` for
animation index and rotation state. No genre packages. Read top-to-bottom in under two minutes.

**For framework contributors:** The codegen oracle for the metasprite primitive. `MetaspriteIRTest`
pins the IR shape; any DSL or IR change that breaks this file breaks the metasprite contract.

**For GBC authors:** Shows `spritePalette { }` + `config { target(GbcTarget.GBC_COMPATIBLE) }`
as the minimal recipe for per-sprite GBC sub-palette selection.

## Reference

Original GBDK example: `gbdk/examples/cross-platform/metasprites/`
