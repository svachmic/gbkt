# Phase 12.8 W5 Regression Sweep — Post-Plan-12.8-03/04 SHA-256 Manifest

**Date:** 2026-05-27
**Wave:** W5 (Plan 12.8-05)
**Current HEAD codegen:** Plan 12.8-03 (conditional `-keep_palette_order` flag-pin at
`ConvertZoneTilesetsTask.kt`, gated by `isIndexedPng` IHDR detection at line 315) +
Plan 12.8-04 (`World1TilesetGrassEncodingTest` JVM emission-invariant lock)
**Pre-fix baseline:** Phase 12.7 round-6 manifest at
`.planning/phases/12.7-player-levitating-physics-codegen/evidence/regression-sweep-round-6.md`

## Audit-Trail Context

Phase 12.8 absorbs the grass-tileset white-pixels diagnostic per W2 ABSORB verdict
(`12.8-DIAGNOSTIC.md` §"W2 fix-vs-route checkpoint verdict"). The single-file fix at
`ConvertZoneTilesetsTask.kt:315` pins `-keep_palette_order` ONLY when the source PNG's
IHDR color-type byte is 3 (indexed/colormap). This sweep verifies:

- The 6 strict targets (breakout, simple-physics, metasprites, metasprites-stress, banks,
  racer) remain byte-identical to the Phase 12.7 round-6 baseline — confirming the
  conditional gate did NOT activate for any non-indexed PNG asset in those games.
- platformer-template's ROM hash INTENTIONALLY changes — the only target with an indexed
  PNG (`world1-tileset.png` — confirmed `8-bit colormap` per
  `evidence/pre-fix-baseline/world1-tileset-file-info.txt`) that triggers the new flag
  path.
- pong's `PASS*` status (per `project_pong_toolchain_nondeterminism.md`) is acknowledged
  but excluded from strict-identity arithmetic.

## Sweep Command

Single chained invocation per `feedback_no_parallel_gradle_clean` (ONE `./gradlew clean`
at the start, NO parallel root cleans):

```bash
./gradlew clean \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:racer:buildRom \
  :gbkt-examples:platformer-template:buildRom
```

**Build verdict:** **BUILD SUCCESSFUL in 13s** (exit 0). 124 actionable tasks: 123
executed, 1 up-to-date. Full log captured at `evidence/rom-smoke.txt` (810 lines).

## Hashing Command

```bash
for game in pong breakout simple-physics metasprites metasprites-stress banks racer platformer-template; do
  shasum -a 256 gbkt-examples/$game/build/gbkt/output/$game.gb
done
```

## SHA-256 manifest (post-fix Phase 12.8)

```
55b9d3106cab039e607915ef19a7a4ef064bf3e07a0a272f88ac4e2d5b6fe97b  gbkt-examples/pong/build/gbkt/output/pong.gb
21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977  gbkt-examples/breakout/build/gbkt/output/breakout.gb
247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad  gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb
c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b  gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764  gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.gb
c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f  gbkt-examples/banks/build/gbkt/output/banks.gb
48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8  gbkt-examples/racer/build/gbkt/output/racer.gb
9056ac47a3b25f76b0cefee461f050d2e67f6616680c9c25a6829b7a5a5971f4  gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
```

## Comparison vs Phase 12.7 round-6 pre-fix baseline

| Target              | Phase 12.7 round-6 (pre-fix)                                         | Phase 12.8 W5 (post-fix)                                             | Verdict                                                            |
|---------------------|----------------------------------------------------------------------|----------------------------------------------------------------------|--------------------------------------------------------------------|
| pong                | `70112b44fa38691c519b41b1d287bb5f493ffce0a252f37b952e6ed5ce1bc326`  | `55b9d3106cab039e607915ef19a7a4ef064bf3e07a0a272f88ac4e2d5b6fe97b`  | PASS\* (toolchain non-determinism — see annotations)               |
| breakout            | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977`  | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977`  | PASS (byte-identical)                                              |
| simple-physics      | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad`  | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad`  | PASS (byte-identical)                                              |
| metasprites         | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b`  | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b`  | PASS (byte-identical)                                              |
| metasprites-stress  | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764`  | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764`  | PASS (byte-identical)                                              |
| banks               | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f`  | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f`  | PASS (byte-identical)                                              |
| racer               | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8`  | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8`  | PASS (byte-identical)                                              |
| platformer-template | `bef124da1346fc5ffd007aca7692c9470f3f1e41f30acdec4487944fd6d8d115`  | `9056ac47a3b25f76b0cefee461f050d2e67f6616680c9c25a6829b7a5a5971f4`  | INTENTIONALLY-CHANGED (Plan 12.8-03 D-02 flag-pin)                 |

**Strict targets (non-pong, non-platformer-template):** 6 / **Pass byte-identical to 12.7 round-6:** 6 / **Fail:** 0
**Pong:** PASS\* (toolchain non-determinism per `project_pong_toolchain_nondeterminism.md`)
**Platformer-template:** INTENTIONAL CHANGE — new W5 hash differs from Phase 12.7 round-6
hash because Plan 12.8-03 added the conditional `-keep_palette_order` flag, which
restructures the emitted tileset palette+tile arrays for indexed PNGs (see
`12.8-DIAGNOSTIC.md` §"Pre-fix vs post-fix byte-diff (D-14 ROOT-CAUSE-EVIDENCE)").

## Cross-checks

- **6 strict targets (breakout, simple-physics, metasprites, metasprites-stress, banks,
  racer):** All 6 have Phase 12.8 W5 post-fix hash equal to Phase 12.7 round-6 baseline
  byte-for-byte. Proves the Plan 12.8-03 conditional flag-pin is correctly **gated by
  `isIndexedPng`** — for these 6 games (which use only RGB / non-indexed PNG assets or
  no zone-tilemap assets at all), `ConvertZoneTilesetsTask.isIndexedPng` returned false
  and the flag was NOT appended; codegen output is unchanged. **PASS.**

- **Pong:** `55b9d310...` differs from the Phase 12.7 round-6 `70112b44...`. Expected per
  `project_pong_toolchain_nondeterminism.md` — pong.gb hashes differently every rebuild
  from the same commit; pre-existing sdcc/lcc artifact. The generated C is unchanged.
  **PASS\*.** Do NOT investigate.

- **Platformer-template:** `9056ac47...` differs from Phase 12.7 round-6 `bef124da...`.
  The 12.7 → 12.8 delta is purely the W3 conditional `-keep_palette_order` flag-pin
  changing the byte ORDER of palette indices in `_zone_world1Area1Zone_tileset_tiles[]`
  AND expanding `_zone_world1Area1Zone_tileset_palettes` from `[4]` to `[16]` entries
  (the full PLTE table when png2asset preserves source order). See
  `12.8-DIAGNOSTIC.md` §"Pre-fix vs post-fix byte-diff (D-14 ROOT-CAUSE-EVIDENCE)" for
  the line-level diff. **PASS — INTENTIONAL CHANGE.**

## Annotations

- **pong PASS\***: pong.gb hashes differently every rebuild from the same commit per
  `project_pong_toolchain_nondeterminism.md`. Pre-existing sdcc/lcc non-determinism;
  NOT a W5 regression. Do NOT investigate.

- **platformer-template INTENTIONALLY-CHANGED**: Plan 12.8-03 added a conditional
  `-keep_palette_order` flag at `ConvertZoneTilesetsTask.kt:315`, gated by an
  `isIndexedPng()` check that inspects the PNG IHDR color-type byte. For
  `world1-tileset.png` (color-type 3 — indexed/colormap), the flag activates and
  changes the emitted palette+tile arrays. The hash change `bef124da...` →
  `9056ac47...` reflects:
    - Conversion-args header gains `-keep_palette_order` token (1-line change in
      `_zone_*_tileset.c` C comment).
    - `_zone_world1Area1Zone_tileset_palettes` array grows from `[4]` to `[16]` entries
      (252-byte file size growth from 2919 → 3171 bytes).
    - `_zone_world1Area1Zone_tileset_tiles[432]` re-permutes — every tile's bitplane
      bytes change because the palette-index-to-pixel-byte mapping now follows the
      source PLTE order instead of the auto-sorted (light→dark) order.
    - Two zones reference `world1-tileset.png` (world1Area1Zone, world1Area2Zone) per
      the build log; both emitted tileset C files inherit the same byte-diff.
  All other emission shape (banking, scene scripts, character codegen) is UNCHANGED.

- **6 strict targets PASS**: breakout, simple-physics, metasprites, metasprites-stress,
  banks, racer all hash-match their Phase 12.7 round-6 baselines byte-for-byte. Proves
  the Plan 12.8-03 change is correctly scoped by `isIndexedPng()` gate AND does NOT
  leak into other games' codegen paths.

## Cross-references

- **Phase 12.7 round-6 baseline:** `.planning/phases/12.7-player-levitating-physics-codegen/evidence/regression-sweep-round-6.md` (the 7-target pre-fix anchor used in the comparison table above)
- **W3 fix commit:** `13037c9b — feat(12.8-03): conditional -keep_palette_order — detect indexed PNG via IHDR color-type byte`
- **W3 initial flag-pin (superseded by re-scope):** `16ddf5ce — fix(12.8-03): pin -keep_palette_order at ConvertZoneTilesetsTask args list (D-02)`
- **W4 JVM emission-invariant test:** `976ce98a — test(12.8-04): add World1TilesetGrassEncodingTest — D-13 byte-pattern lock`
- **W5 ROM-build smoke log:** `evidence/rom-smoke.txt`
- **W5 pre/post tileset byte-diff:** `12.8-DIAGNOSTIC.md` §"Pre-fix vs post-fix byte-diff (D-14 ROOT-CAUSE-EVIDENCE)"
- **D-14 byte-diff partner files:** `evidence/pre-fix-baseline/_zone_world1Area1Zone_tileset.c` ↔ `evidence/post-fix-baseline/_zone_world1Area1Zone_tileset.c`

## Conclusion — D-13 + D-14 GREEN

- **D-13 ROM smoke gate:** `./gradlew clean :gbkt-examples:platformer-template:buildRom`
  exited 0; ROM produced at `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`
  (65,536 bytes). The codegen-phase ROM-build smoke gate per
  `feedback_rom_build_smoke_test_for_codegen_phases` is GREEN.

- **D-14 7-target regression sweep:** 6/6 strict-identity targets byte-identical to
  Phase 12.7 round-6 baseline. Pong `PASS*` per memory caveat. Platformer-template
  intentionally changed with the byte-diff captured as ROOT-CAUSE-EVIDENCE in
  `12.8-DIAGNOSTIC.md`.

The conditional flag-pin (Plan 12.8-03 re-scope) is correctly gated, scoped, and
exercises the intended emission path for indexed PNGs only. W5 GREEN; W6 (anchor-5 PNG
re-shoot via `PlatformerTemplate128UatTest`) can proceed on a fresh, verified-good
platformer-template.gb ROM.
