# Reference ROM Build (Phase 12)

Reproduces the GBDK `platformer_template` reference ROM. Required by D-17a
(three-signal artifact — ROM size, generated-C diff, bank-layout signal)
and D-21 (verifier ROM-build smoke test re-runs `make` before declaring
the phase complete).

The reference is the upstream GBDK-2020 cross-platform example at
`/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/`.

Binaries (`.gb`, `.map`, `.noi`, `.sym`) are intentionally **NOT committed**
(see `.gitignore` rules for this phase's `evidence/reference/` path).
Rebuild locally with the steps below whenever a binary is needed for ROM
size comparison (the Phase 12-24 three-signal artifact plan) or for the
verifier's D-21 smoke test.

## Prerequisites

- GBDK-2020 installed (https://github.com/gbdk-2020/gbdk-2020/releases)
- `GBDK_HOME` env var set to the GBDK install dir
  (e.g. `/opt/gbdk-2020`, `~/gbdk-2020`, or `/Users/michalsvacha/gbdk` on
  the user's machine)
- Reference source at
  `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/`
  (per the user's machine layout; adjust paths in the commands below if
  GBDK is installed elsewhere)

## Build invocation

The reference's `Makefile.targets` defines a `gb` target that internally
runs `make build-target PORT=sm83 PLAT=gb EXT=gb SPRITES=gbapduck`. The
`build-target` recipe depends on `png2asset` (PNG asset conversion) and
`$(BINS)` (link the ROM) — both run automatically:

```bash
export GBDK_HOME=/path/to/gbdk-2020      # e.g. /Users/michalsvacha/gbdk
cd /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template
make gb                                  # png2asset + link in one step
```

Run `make gb-clean` first if you want a guaranteed clean rebuild
(removes `obj/gb/`, `gen/gb/src/`, `dist/`).

## Expected outputs

After `make gb` completes successfully, the following files exist:

| Path                                | Description                                  |
| ----------------------------------- | -------------------------------------------- |
| `build/gb/platformer_template.gb`   | ROM binary (size: ~64–128 KB per RESEARCH §Cartridge prediction; FFD/png2asset autobank determines actual) |
| `build/gb/platformer_template.map`  | Linker map                                   |
| `build/gb/platformer_template.noi`  | Linker `.noi` symbol/bank-size file (every `DEF l__CODE_<N>` byte size — verifier checks each ≤ 16384 per Phase 11 D-15) |
| `obj/gb/platformer_template.noi`    | Duplicate of the `.noi` (same content)       |
| `gen/gb/src/*.c, *.h`               | png2asset-generated tile data + tilemaps (9 generated files: PlayerCharacterSprites, World1Tileset, World2Tileset, World1Area1, World1Area2, World2Area1, TitleScreen, NextLevel) |

## Capture to evidence/

After building, copy the three primary artifacts into this directory so
the Phase 12-24 three-signal artifact plan can compute ROM-size /
bank-layout deltas against the gbkt port:

```bash
EVIDENCE=/Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference

cp /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.gb  "$EVIDENCE/"
cp /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.map "$EVIDENCE/"
cp /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.noi "$EVIDENCE/"
```

All three target files are gitignored — committing this directory tracks
only `BUILD.md` and `.gitkeep`.

## Cartridge type

| Property              | Reference (`platformer_template`)              | gbkt port (Phase 12)                                              |
| --------------------- | ---------------------------------------------- | ----------------------------------------------------------------- |
| Cartridge byte        | `0x1B` — MBC5+RAM+BATTERY                      | `"MBC1"` (per CONTEXT D-claude-3; FFD verdict from Plan 12-24)    |
| Source                | `Makefile.targets: LCCFLAGS_gb += -Wl-yt0x1B`  | `config { cartridge = "MBC1" }` (magic string per D-20 deferral)  |
| Reason for divergence | png2asset `-autobank` default + future-proof   | gbkt has no SRAM use; smaller MBC sufficient for ≥3 banks         |

This **expected divergence** is documented for the oracle-comparison
artifact created in Plan 12-24 (`evidence/oracle-comparison.md`). Phase
13 requirement #1 (typed `Cartridge` enum) replaces the magic string —
not in scope for Phase 12 per D-20.

## Asset flags reference

png2asset invocations from the reference Makefile (`PNG2ASSET_*_SETTINGS_gb`
is empty, so only the explicit per-asset flags appear below):

| Asset                                  | png2asset flags                                                                                      |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `player-character-gbapduck-sprites.png` | `-px 12 -py 6 -spr8x16 -keep_palette_order -sw 24 -sh 32 -b 255`                                    |
| `world1-tileset.png`                    | `-keep_palette_order -noflip -map -b 255`                                                            |
| `world2-tileset.png`                    | `-keep_palette_order -noflip -map -b 255`                                                            |
| `world1-area1.png`                      | `-noflip -map -maps_only -source_tileset res/graphics/world1-tileset.png -b 255`                     |
| `world1-area2.png`                      | `-noflip -map -maps_only -source_tileset res/graphics/world1-tileset.png -b 255`                     |
| `world2-area1.png`                      | `-noflip -map -maps_only -source_tileset res/graphics/world2-tileset.png -b 255`                     |
| `title-screen.png`                      | `-noflip -map -b 255`                                                                                |
| `next-level.png`                        | `-noflip -map -b 255`                                                                                |

`-b 255` = bank 255 (autobank — linker assigns the final bank). Tileset
PNGs emit tile data + index map. Area PNGs emit only the tile-index map
(referencing the source tileset's tile array).

The gbkt port consumes these same PNGs verbatim per D-claude-7
(`gbkt-examples/platformer-template/res/`); the gbkt asset pipeline
(`ConvertZoneTilesetsTask`) replaces the manual png2asset invocations.

## Compiler flags

The reference's `gb` target uses:

```
-Wm-ys -Wl-yt0x1B -autobank -Wl-j -Wm-yoA -Wm-ya4 -Wb-ext=.rel
```

Key flags:

- `-autobank` — automatic bank allocation (linker; matches gbkt's FFD bank packing)
- `-Wl-j` — join banks
- `-Wm-yoA` — 4× ROM banks (`-Wm-yoX` controls ROM-bank multiplier)
- `-Wm-ya4` — 4× RAM banks
- `-Wl-yt0x1B` — cartridge byte (`MBC5+RAM+BATTERY`)
- NO `-Wm-yc` — plain DMG build for the `gb` target (the `gbc` target
  adds it; gbkt port targets `gbcTarget = GBC_COMPATIBLE` per
  D-claude-4 which DOES add `-Wm-yc`, matching the `gbc` build path)

## Reproducibility checksum (optional)

After the first successful build, record the sha256 of the .gb here so
subsequent rebuilds can detect drift (different GBDK version, png2asset
update, etc.):

```bash
shasum -a 256 /Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/platformer_template.gb
```

| Date | sha256 | GBDK version | Notes |
| ---- | ------ | ------------ | ----- |
| 2026-05-21 | `7f4c5095d195446019004a7a07d8fb6ee75af073ef2ab7012c62c5cb9bd7d587` | gbdk-4.5.0 (lcc.c rev 2.0, 2025/12/28) | initial capture; 32 KB ROM; built via `make gb` on the orchestrator's machine during Plan 12-02 Task 3 human-verify |

## Verification (bank-layout signal)

The `.noi` file exposes each ROM bank's byte size as
`DEF l__CODE_<N> = 0x<hex>`. Phase 11 D-15 / Phase 12 D-17 mandates that
every bank ≤ 16384 bytes (hard MBC ROM-bank capacity). Inspect with:

```bash
grep '^DEF l__CODE' /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.noi
grep '^DEF l__HOME' /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.noi
```

The Phase 12-24 three-signal artifact plan reads these values and
compares them to the gbkt port's `.noi` at
`gbkt-examples/platformer-template/build/gbkt/output/platformer_template.noi`.
