# Platformer Template Assets

PNG assets imported verbatim from the GBDK-2020 `platformer_template` example per CONTEXT D-claude-7. Used in the Phase 12 reference port (`gbkt-examples/platformer-template/`).

## Attribution

- **Source:** GBDK-2020 examples, `cross-platform/platformer_template/res/graphics/`
- **Upstream:** https://github.com/gbdk-2020/gbdk-2020
- **License:** MPL 2.0 / dual-licensed (same as GBDK-2020 examples; carries forward into the gbkt repo)

The 8 PNG files in `res/graphics/` are byte-identical copies of the upstream reference (see `## SHA-256 sums (tamper detection)` below). No edits were applied during import. Subsequent plans (12-16 image-assets codegen; 12-17 metasprite descriptor) reference these PNGs in DSL via `asset("res/graphics/...")` and process them with `png2asset` flags documented below.

## File manifest

| File | Purpose | png2asset flags (Plan 12-16 codegen) |
|------|---------|--------------------------------------|
| player-character-gbapduck-sprites.png | 6-frame walking + jump player metasprite (12-frame atlas; only 6 used per D-04) | `-spr8x16 -px 12 -py 6 -sw 24 -sh 32` |
| world1-tileset.png | World 1 background tileset | `-noflip -map` |
| world2-tileset.png | World 2 background tileset | `-noflip -map` |
| world1-area1.png | Level 1 tilemap (shares world1 tileset) | `-noflip -map -maps_only -source_tileset world1-tileset.png` |
| world1-area2.png | Level 2 tilemap (shares world1 tileset) | `-noflip -map -maps_only -source_tileset world1-tileset.png` |
| world2-area1.png | Level 3 tilemap | `-noflip -map -maps_only -source_tileset world2-tileset.png` |
| title-screen.png | Banked title screen tile data (D-02) | `-noflip -map` |
| next-level.png | Banked NextLevel transition card (D-02) | `-noflip -map` |

## Sprite binding and png2asset cutting flags (Phase 12.4 + Phase 12.5)

Every metasprite in this example's DSL must declare its source PNG via `sprite(asset("..."))`
inside the `metasprite { }` block. As of Phase 12.5, the `sprite()` block also declares
the png2asset cutting flags (`mode`, `pivot`, `frameSize`) required for correct metasprite
layout.

For the player metasprite, the full declaration is:

```kotlin
val player by metasprite {
    sprite(asset("graphics/player-character-gbapduck-sprites.png")) {
        mode(SpriteMode.SPR8x16)   // -spr8x16 (8×16 hardware sprite pairs)
        pivot(12, 6)               // -px 12 -py 6 (anchor point within the frame)
        frameSize(24, 32)          // -sw 24 -sh 32 (frame slice: 3 cols × 2 rows)
    }
    // ... frames
}
```

These flags correspond to the png2asset CLI arguments documented in the file manifest above
and extracted verbatim from the GBDK reference Makefile (see
`.planning/phases/12.5-.../evidence/reference-toolchain-comparison.md`).

Phase 12.5 wired these flags through the DSL → IR → sidecar → ConvertSpritesTask → png2asset
chain. They are REQUIRED per the `GenerateCTask` validation gate (Phase 12.5 D-04b): omitting
`mode()`, `pivot()`, or `frameSize()` inside the `sprite() { }` block causes `GenerateCTask`
to throw a `GradleException` at build time — there is no silent fallback.

Omitting `sprite(asset(...))` entirely also causes `GenerateCTask` to throw a `GradleException`
(Phase 12.4 D-01b gate). See `context/TOOLING.md § Sprite Asset Pipeline` for the full
convention, pipeline flow, and the delta from pre-Phase 12.4 implicit path resolution.

## SHA-256 sums (tamper detection)

Captured at import time from this worktree's copy. Re-run `shasum -a 256 *.png` inside `res/graphics/` to detect tampering of the working copy.

```
5f32a2a157cffb5bcb6fbf8a7516c5067f359b0868d4499b0e1708607266b63a  next-level.png
5e7b37bb14015badaa82cfe28645a95d6faada0f8d9e11922b7513c22fb77d7e  player-character-gbapduck-sprites.png
2afc476e463112d0f5ede5cefee1fb17d6f5596a3f61fdbcf0b9d776c8c827fd  title-screen.png
fa40dd298521180a0ce1b5168d3ac58ab813cbfbc70b0fa7281472939bb697ef  world1-area1.png
130d8b9027d6925446441ccab5adc9be5e4854fff90a4b3fbea51ea32ec47364  world1-area2.png
780df1cc8079bc55395be05b4021d7d8c480ee2d01d9f5393fc2fd11afeab248  world1-tileset.png
a617062fb241f0f3e1223b7c9fcdb0d9e3a8c03d77b19a71f2e4e59d636eff91  world2-area1.png
5ca2d9ca7ffef67167908e55ec2a9b45e044ef1144636cb872de131f1ba5112a  world2-tileset.png
```

## Shared-tileset note (SEED-PHASE-12-SHARED-TILESET)

`world1-area1.png` and `world1-area2.png` share `world1-tileset.png` as their background source. Per RESEARCH §D-15, the current `ConvertZoneTilesetsTask` **duplicates** the shared tileset across both zones' bank files (correctness preserved; ~1-3 KB ROM overhead).

This is a known efficiency gap, not a bug. The seed file `SEED-PHASE-12-SHARED-TILESET.md` will be created at Phase 12 close (Plan 12-26) to track the future dedup work — outside the scope of the present port.
