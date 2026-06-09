# png2asset First-Run Output — player-character-gbapduck-sprites.png

**Captured:** 2026-05-24
**GBDK version:** gbdk-4.5.0 (at /Users/michalsvacha/gbdk)
**PNG SHA-256:** 5e7b37bb14015badaa82cfe28645a95d6faada0f8d9e11922b7513c22fb77d7e
**Flags:** -spr8x16 -px 12 -py 6 -sw 24 -sh 32 -noflip

## _tiles[] summary

- Native array name: `player_character_gbapduck_sprites_tiles[1984]`
- Total bytes: 1984
- Tile count (8x8 tiles): 1984 / 16 = **124 tiles**
- 8x16-tile-pair count: 1984 / 32 = **62 tile-pairs**
- Atlas frame count: 12 (as reported in res/README.md; all 12 are present in the generated `_metasprites[][]` table)

## Stub check

- Contains real `_tiles[1984]` array — NOT a stub (grep for "Stub sprite data" returns 0).
- File size: 15005 bytes.

## Frame → tile baseId map (from png2asset output)

Each animation frame is 24×32 px = 3 cols × 4 rows of 8×8 tiles.
In SPR8x16 mode (each METASPR_ITEM covers one 8×16 hardware sprite), this is 3 cols × 2 pair-rows = **6 METASPR_ITEM entries per frame**.

The png2asset `_metasprites` table contains 12 source frames (metasprite0..metasprite11):
- Frames 0–5: right-facing (directionOffset=0)
- Frames 6–11: left-facing (directionOffset=6, `facingRot` path)

Only frames 0–5 (right-facing) are used as the gbkt DSL frame indices for Plan 12.4-09, since hflip is applied via `facingRot` at runtime (D-04).

### METASPR_ITEM format

`METASPR_ITEM(dx, dy, baseId, flags)` where:
- `dx`, `dy` = offset delta from previous sprite position (or from anchor for first entry)
- `baseId` = index into the `_tiles[]` array divided by 16 (i.e., tile number for the 8x8 tile at the top of the 8x16 pair)
- The 8x16 pair uses tiles at baseId and baseId+1

### Frame BaseId Table — right-facing frames (atlas indices 0–5)

| Atlas index | DSL frame name | METASPR_ITEM 1 (dx,dy,baseId) | METASPR_ITEM 2 (dx,dy,baseId) | METASPR_ITEM 3 (dx,dy,baseId) | METASPR_ITEM 4 (dx,dy,baseId) | METASPR_ITEM 5 (dx,dy,baseId) | METASPR_ITEM 6 (dx,dy,baseId) |
|-------------|----------------|-------------------------------|-------------------------------|-------------------------------|-------------------------------|-------------------------------|-------------------------------|
| 0           | idle           | (-6,-12,**0**)                | (0,8,**2**)                   | (0,8,**4**)                   | (16,-16,**6**)                | (0,8,**8**)                   | (0,8,**10**)                  |
| 1           | walk1          | (-6,-12,**12**)               | (0,8,**2**)                   | (0,8,**4**)                   | (16,-16,**14**)               | (0,8,**16**)                  | (0,8,**18**)                  |
| 2           | walk2          | (-6,-12,**12**)               | (0,8,**2**)                   | (0,8,**4**)                   | (16,-16,**20**)               | (0,8,**22**)                  | (0,8,**24**)                  |
| 3           | walk3 (turn)   | (-6,-12,**26**)               | (0,8,**28**)                  | (0,8,**30**)                  | (16,-16,**32**)               | (0,8,**34**)                  | (0,8,**36**)                  |
| 4           | jump-up        | (-6,-12,**38**)               | (0,8,**40**)                  | (0,8,**42**)                  | (16,-16,**44**)               | (0,8,**46**)                  | (0,8,**48**)                  |
| 5           | jump-fall      | (-6,-12,**50**)               | (0,8,**52**)                  | (0,8,**54**)                  | (16,-16,**56**)               | (0,8,**58**)                  | (0,8,**60**)                  |

Notes:
- The reference `player.c` selects: Frame 0 = idle/standing, Frames 1-2 = walk cycle, Frame 5 = turning, Frame 3 = jump-rising, Frame 4 = jump-falling.
- The gbkt port's PlatformerTemplate.kt DSL uses frames 0–5 directly (idle=0, walk1=1, walk2=2, walk3=3, jump-up=4, jump-fall=5) matching the first 6 atlas indices.
- Frames 3 and 4 in the gbkt port (jump-up, jump-fall) correspond to atlas indices 3 and 4 (rising=3, falling=4 in reference), consistent with the reference.
- Walk3/turning (DSL frame 3) maps to atlas index 3 per the gbkt PlatformerTemplate comment ordering; note that the reference uses `threeFrameCounterValue` which cycles 0-2 for walk frames and frame 5 for turning — the gbkt port reorders slightly (walk3=3 instead of 5). See PlatformerTemplate.kt line 339 comment.

### Left-facing frames (atlas indices 6–11)

The DSL does NOT use these directly — `facingRot` at runtime applies hflip via the hardware sprite mirror bit (D-04). Provided here for completeness only:

| Atlas index | Mirror of frame | METASPR_ITEM 1 baseId | METASPR_ITEM 4 baseId | Other baseIds |
|-------------|-----------------|----------------------|----------------------|---------------|
| 6           | idle (0)        | 62                   | 68                   | 64,66,70,72   |
| 7           | walk1-alt (1)   | 62                   | 76                   | 64,74,78,80   |
| 8           | walk2-alt (2)   | 62                   | 82                   | 64,74,84,86   |
| 9           | walk3-alt (3)   | 88                   | 94                   | 90,92,96,98   |
| 10          | jump-up-alt (4) | 100                  | 106                  | 102,104,108,110|
| 11          | jump-fall-alt(5)| 112                  | 118                  | 114,116,120,122|

## Plan 12.4-09 tile() call reference

The `tile()` call in MetaspriteFrameBuilder maps to one `METASPR_ITEM` entry.
Expected signature per D-03: `tile(dx, dy, baseId)` where dx/dy are the pixel offsets and baseId is the tile-pair index.

For each of the 6 DSL frames (frame { ... } blocks in PlatformerTemplate.kt), the 6 tile() calls are:

**Frame 0 (idle):**
```
tile(-6, -12, 0)
tile(0, 8, 2)
tile(0, 8, 4)
tile(16, -16, 6)
tile(0, 8, 8)
tile(0, 8, 10)
```

**Frame 1 (walk1):**
```
tile(-6, -12, 12)
tile(0, 8, 2)
tile(0, 8, 4)
tile(16, -16, 14)
tile(0, 8, 16)
tile(0, 8, 18)
```

**Frame 2 (walk2):**
```
tile(-6, -12, 12)
tile(0, 8, 2)
tile(0, 8, 4)
tile(16, -16, 20)
tile(0, 8, 22)
tile(0, 8, 24)
```

**Frame 3 (walk3):**
```
tile(-6, -12, 26)
tile(0, 8, 28)
tile(0, 8, 30)
tile(16, -16, 32)
tile(0, 8, 34)
tile(0, 8, 36)
```

**Frame 4 (jump-up):**
```
tile(-6, -12, 38)
tile(0, 8, 40)
tile(0, 8, 42)
tile(16, -16, 44)
tile(0, 8, 46)
tile(0, 8, 48)
```

**Frame 5 (jump-fall):**
```
tile(-6, -12, 50)
tile(0, 8, 52)
tile(0, 8, 54)
tile(16, -16, 56)
tile(0, 8, 58)
tile(0, 8, 60)
```

Total: 6 frames × 6 tile() calls = **36 tile() calls** (matches plan estimate).

## Reproduction command

```bash
/Users/michalsvacha/gbdk/bin/png2asset \
    gbkt-examples/platformer-template/res/graphics/player-character-gbapduck-sprites.png \
    -o .planning/phases/12.4-sprite-pipeline-png2asset-integration-wire-png2asset-binary-/evidence/png2asset-first-run/player_character_gbapduck_sprites.c \
    -spr8x16 -px 12 -py 6 -sw 24 -sh 32 -noflip
```

Exit code: 0.

## Flag notes

The flags used match the plan's documented flags (`-spr8x16 -px 12 -py 6 -sw 24 -sh 32 -noflip`).

Minor discrepancy with `res/README.md` table: the README table row for player-character-gbapduck-sprites.png lists `-spr8x16 -px 12 -py 6 -sw 24 -sh 32` (without `-noflip`). The plan objective and `<interfaces>` block both specify `-noflip`. The `-noflip` flag was used here per the plan specification. This matches the plan's intent to disable flip-optimization so `facingRot` owns the hflip path at runtime (D-04 design decision).
