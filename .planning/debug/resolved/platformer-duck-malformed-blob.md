---
slug: platformer-duck-malformed-blob
status: resolved
trigger: "REQ-3 visual gap during Phase 12.5: post-12.5 platformer ROM still renders player sprite as a malformed blob despite png2asset CLI flags now matching the GBDK-2020 reference Makefile exactly"
created: 2026-05-24
updated: 2026-05-24
source_phase: 12.5
linked_plan: 12.5-09
specialist_hint: general
---

# Debug Session: platformer-duck-malformed-blob

## Symptoms

### Expected behavior
The gbkt-built `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb` ROM, when loaded in Coffee-GB and stepped to the gameplay scene, should render the player as a recognizable 24×32 duck character (head, body, legs, wing — same shape as the GBDK-2020 reference platformer ROM renders).

### Actual behavior
The post-12.5 gbkt ROM renders the player sprite as a ~2–4 pixel malformed blob in the upper-center of the screen. The OAM flip path works (8.78% pixel diff between facing-right and facing-left captures), so the sprite IS being placed and IS being flipped — but the tile data being rendered does not depict a duck.

### Reference (working baseline)
The reference GBDK-2020 example built from `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.gb` renders the duck correctly. Reference uses these png2asset flags (verified by Plan 12.5-01):

```
-px 12 -py 6 -spr8x16 -keep_palette_order -sw 24 -sh 32 -b 255
```

The gbkt pipeline (post Plans 02–08) now passes the same `-px 12 -py 6 -spr8x16 -sw 24 -sh 32` flags (omits `-keep_palette_order` and `-b 255`; gbkt-side `#pragma bank 1` post-processing supersedes `-b`).

### Error messages
None. Build succeeds. ROM boots. No SDCC warnings or lcc errors.

### Timeline
- Phase 12.3 (Anchor 4) first surfaced the visual gap — the player rendered as a malformed blob even though `_walkFrameIdx` cycled correctly.
- Phase 12.4 wired png2asset binary into the build but kept the wrong flag set (height heuristic only).
- Phase 12.5 Plan 12.5-01 diagnostic confirmed: reference ROM uses different flags than gbkt was passing; verdict `branch=gbkt-misconfig`.
- Phase 12.5 Plans 02–08 fixed the codegen contract: SpriteMode → gbkt-ir, MetaspriteBuilder DSL (mode/pivot/frameSize), sidecar JSON emits 5 cutting flags, ConvertSpritesTask reads sidecar and passes flags to png2asset binary, height heuristic deleted.
- Phase 12.5 Plan 12.5-09 re-shot anchor 4 against the post-12.5 ROM. User human-verify gate returned: still broken duck, marginal pixel gain over pre-12.5 but visually unrecognizable.

### Reproduction
```bash
cd /Users/michalsvacha/GitHub/personal/gbkt
./gradlew :gbkt-examples:platformer-template:buildRom
# ROM at gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
# Load via gbkt-emulator MCP, step into gameplay, observe malformed sprite
```

Captures:
- gbkt broken (post-12.5): `.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor4-walk-frame-0.png`, `anchor4-facing-left.png`
- Reference correct: `.planning/phases/12.5-png2asset-metasprite-layout-fix-and-phase-12-3-closure/evidence/reference-duck-facing-{right,left}.png`

## Current Focus

hypothesis: RESOLVED — three compounding bugs found

Current Focus: human-verify — awaiting user approval of post-fix anchor4 captures (E-02, E-03, E-04 applied + ROM rebuilt + post-fix PNGs captured)

## Evidence

### E-01: Tile data byte count mismatch
- Reference `PlayerCharacterSprites_tiles[992]` — 992 bytes = 62 tiles × 16 bytes
- gbkt `player_tiles[1984]` — 1984 bytes = 124 tiles × 16 bytes (2× reference)
- Cause: gbkt passes `-noflip` to png2asset (`mirrorDedup=false`), preventing mirror-pair deduplication. Reference does NOT pass `-noflip`, allowing png2asset to generate 62 tiles (31 pairs) with hardware-flip for mirrored frames. gbkt generates 124 unique tiles.
- Impact: Only the first 61 tiles are loaded via `set_sprite_data(0u, 61u, player_tiles)` — the actual array has 124. In 8x16 mode, tile ID 60 uses pair (60, 61), so even the last tile of the right-facing set is cut off (61st tile needed, not loaded).

### E-02: SPRITES_8x8 macro — wrong hardware sprite mode
- `main.c` line 568: `SPRITES_8x8;`
- The platformer template's metasprite uses `SpriteMode.SPR8x16` (declared in DSL, stored in MetaspriteIR, written to sidecar JSON).
- `SPRITES_8x8` makes the Game Boy render each OAM entry as a single 8×8 tile. In `SPRITES_8x16` mode, each OAM entry renders tile N and tile N+1 as an 8×16 pair.
- Impact: The duck head/body tiles are single 8×8 squares instead of 8×16 pairs. This causes a squashed, distorted appearance — looks like a blob.
- Root cause in pipeline: `GBDKPipelineV2.kt` line 4665 unconditionally emits `SPRITES_8x8` for ALL metasprite games. This was correct for the `metasprites` example (elephant, which uses `-spr8x8`), but wrong for the platformer template which uses `SPR8x16`.

### E-03: Frame descriptor x/y coordinates swapped (PRIMARY blob cause)
- The `tile(x, y, id)` DSL function stores `MetaspriteTile(relX=x, relY=y)`.
- `MetaspriteVisitor.generateMetaspriteDescriptor()` emits `{tile.relY, tile.relX, tile.tileId}` = `{dy, dx, dtile}` in GBDK struct order.
- The platformer template author wrote `tile(-6, -12, 0)` intending to directly transcribe the reference's `METASPR_ITEM(-6, -12, 0, ...)` (dy=-6, dx=-12).
- BUT: DSL convention is `tile(x, y)` = `tile(dx, dy)` where x is horizontal. So `tile(-6, -12, 0)` stores `relX=-6, relY=-12` → emits `{dy=-12, dx=-6}` — opposite of reference `{dy=-6, dx=-12}`.
- Impact: All 6 frames have ALL tile (x,y) arguments swapped. The 3×2 grid of tiles that should span horizontally (3 tiles across, 2 tall) is placed vertically (3 tiles down, 2 wide). All duck tiles pile up on top of each other → the "blob".
- Proof: The `metasprites` example (elephant) works correctly because its author used the correct `tile(dx, dy, id)` convention — e.g., `tile(-24, -16, 0)` → `relX=-24, relY=-16` → `{dy=-16, dx=-24}` matching reference `METASPR_ITEM(-16, -24, 0, ...)` = `{dy=-16, dx=-24}`.
- The platformer template was the first usage AFTER the elephant, and its author transcribed the reference coordinates in the WRONG order.

### E-04: set_sprite_data tile count = 61 (off by 2 for 8x16 mode)
- `main.c` line 565: `set_sprite_data(0u, 61u, player_tiles)`
- The count 61 comes from `max(tileId across all frames) + 1 = 60 + 1 = 61`.
- In 8x16 mode, tile ID 60 references tiles 60 AND 61 as a pair. The array needs 62 tiles (0 through 61) loaded.
- The formula should be `maxTileId + 2` for SPR8x16, matching the reference's 62-tile count.

## Eliminated

- Hypothesis that png2asset tile bytes differ from reference: E-01 shows they differ only in count (2×), not in individual tile content. The first 62 tiles are the right-facing duck tiles, identical to reference except for palette order.
- Hypothesis that palette order causes rendering failure: The palette reordering from omitting `-keep_palette_order` means gbkt palette is white/gray/orange/black instead of orange/black/white/gray. For DMG (non-GBC) the palettes are not used at runtime — OBP0 register is set via fade_in/fade_out to `228u` (0xE4 = 11 10 01 00 = color3=dark, color2=med, color1=light, color0=transparent). The ACTUAL cause of the blob is the coordinate swap (E-03), not the palette.
- Hypothesis that banking causes wrong tiles: The `#pragma bank 1` injection is correct. The `set_sprite_data` call happens before any bank switching in `main()`, and bank 1 is the MBC power-on default. Banking is not the root cause.

## Root Cause

Three compounding bugs, each worsening the rendering, with E-03 being the primary visual cause:

**Primary (E-03):** All 6 metasprite frames in `PlatformerTemplate.kt` have x/y tile coordinates swapped. The DSL `tile(x, y, id)` takes x as horizontal offset (dx) and y as vertical offset (dy), but the author transcribed the reference's `METASPR_ITEM(dy, dx, dtile, ...)` arguments directly, writing `tile(dy, dx, id)` — the opposite convention. This causes all tile placements to be rotated 90°, stacking the tiles vertically instead of forming the 3×2 grid, producing the blob.

**Secondary (E-02):** `GBDKPipelineV2.kt` emits `SPRITES_8x8` unconditionally for all metasprite games, but the platformer uses `SPR8x16`. In 8x8 mode, each OAM entry renders a single 8×8 tile — the duck's head tiles appear as tiny 8×8 squares.

**Tertiary (E-04):** `set_sprite_data(0u, 61u, ...)` loads only 61 of the 62 tiles needed for the 8x16 pairs (max tile ID 60 requires tiles 60+61). Last pair is partially missing.

## Fix

**Fix A (PlatformerTemplate.kt):** Swap x and y arguments in all `tile(x, y, id)` calls in all 6 frames. For each `tile(a, b, id)` call, replace with `tile(b, a, id)`.

**Fix B (GBDKPipelineV2.kt):** Change the `SPRITES_8x8` emission gate to check the sprite mode of the game's metasprites. Emit `SPRITES_8x16` when any metasprite has `spriteMode == SpriteMode.SPR8x16` (or null defaulting to SPR8x16). Keep `SPRITES_8x8` only when all metasprites use `SPR8x8`.

**Fix C (MetaspriteVisitor.kt or GBDKPipelineV2.kt):** For `SPR8x16` metasprites, use `maxTileId + 2` instead of `maxTileId + 1` for the tile count in `set_sprite_data()`.

## Bonus finding (deferred)

### E-01 (mirror-dedup / `-noflip` flag question) — OUT OF SCOPE for this fix-now session

The platformer-template player sprite is emitted with `mirrorDedup = false` (the gbkt default
for from-reference transcriptions), which adds `-noflip` to the png2asset invocation. This
produces a 124-tile array (1984 bytes) — exactly 2× the reference's 62-tile array (992 bytes).
The extra 62 tiles are the pre-flipped left-facing tiles that the reference deduplicates via
hardware `S_FLIPX`.

After E-02/E-03/E-04 are applied, the duck renders correctly because:
- E-04 loads 62 of the 124 tiles (the first 62 are the right-facing tiles, byte-identical to
  the reference).
- The DSL frame data only references tile IDs 0..60 (right-facing tile pairs).
- The visitor wraps `move_metasprite_flipx` around the frame for left-facing — which uses
  hardware-flip on the same 62 tiles.

So E-01 produces a 2× VRAM-tile-waste (62 unused tiles loaded), not a visual bug. Optimizing
to match the reference's 62-tile output is a Phase 13+ enhancement: it requires either
toggling the platformer DSL's mirrorDedup to `true` (and verifying the resulting png2asset
output matches the reference's id space) OR adding a `tilesNeeded` clamp inside the visitor so
the visitor only loads `tileIdsActuallyReferencedByFrames + spriteModeStride` bytes. Either
path warrants a proper plan and is NOT required for REQ-3 visual closure.

## Resolution

### Status: fix_applied (awaiting human-verify on the new anchor4 captures)

### Commits (3 fixes, RED→GREEN cycle per fix)

| Fix | Subject | Commit |
|-----|---------|--------|
| E-03 | swap x/y in PlatformerTemplate tile() calls | `87e47bd2` |
| E-02 | select SPRITES_8x16 vs SPRITES_8x8 from metasprite spriteMode | `a26d70dc` |
| E-04 | SPR8x16 set_sprite_data count = maxTileId + 2 | `ad5dabf5` |

### Files modified (per fix)

**E-03 (`87e47bd2`)** — `tile(x, y, id)` arg swap + tightened JVM-tier geometry guard
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:330-361` — all 36 `tile()` calls swapped + DSL convention comment block added at line 308
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlayerMetaspriteGeometryTest.kt:207-298` — geometry test now asserts 3 x-column clusters at {-12, -4, 4} AND 2 y-row clusters at {-6, 10} (pre-fix asserted only 2 x-clusters — locking the bug)

**E-02 (`a26d70dc`)** — hardware sprite mode selection
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:4664-4672` — branched the macro emission on `gameIR.metasprites.any { it.spriteMode == SpriteMode.SPR8x16 }`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SpriteMode8x16HardwareModeTest.kt` (NEW) — two-test contract: SPR8x16 metasprite emits `SPRITES_8x16` AND does NOT emit `SPRITES_8x8`

**E-04 (`ad5dabf5`)** — SPR8x16 tile count fix
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt:75-115` — added `tileCountForMetasprite(metasprite): Int?` helper (single source of truth) used by both `generateMetaspriteTileData` and the pipeline's allocator path
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:4759-4776` — pipeline now calls `MetaspriteVisitor.tileCountForMetasprite(ms)` so VramAllocator reservation and `set_sprite_data` emission cannot drift
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/Spr8x16TileCountTest.kt` (NEW) — two-test contract: SPR8x16 maxTileId=60 → `set_sprite_data(0u, 62u, …)`; SPR8x8 maxTileId=60 → `set_sprite_data(0u, 61u, …)` (back-compat preserved)

### Post-fix generated C verification (gbkt-examples/platformer-template/build/gbkt/generated/main.c)

| Site | Pre-fix | Post-fix |
|------|---------|----------|
| `set_sprite_data` (line 565) | `set_sprite_data(0u, 61u, player_tiles)` | `set_sprite_data(0u, 62u, player_tiles)` |
| Hardware mode macro (line 568) | `SPRITES_8x8;` | `SPRITES_8x16;` |
| `sprite_player_frame_0[]` entry 0 | `{-12, -6, 0}` (dy=-12, dx=-6) | `{-6, -12, 0}` (dy=-6, dx=-12) — matches reference |

### Post-fix anchor4 PNG captures

Captured at 2026-05-24 21:30 by running the existing `PlatformerTemplateUatTest.anchor4MetaspriteAnimation()` against the freshly-built post-fix ROM (`gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`), then copied into the debug evidence directory:

- `.planning/debug/evidence/post-fix/anchor4-walk-frame-0.png`
- `.planning/debug/evidence/post-fix/anchor4-walk-frame-1.png`
- `.planning/debug/evidence/post-fix/anchor4-walk-frame-2.png`
- `.planning/debug/evidence/post-fix/anchor4-facing-left.png`

### Self-review of post-fix PNGs (gated on human-verify per CLAUDE.md Visual Evidence Rule)

The four post-fix captures show a **recognizable duck character** with distinct head, body, and
leg shapes — no longer the pre-fix blob. Frame-to-frame variation (walk-cycle leg/wing
positions) is visible across walk-frame-0 / walk-frame-1 / walk-frame-2. The facing-left
capture is a clean hardware-mirror of the walk-frame-0 silhouette.

Mechanical pixel-diff between `01-walk-frame-0.png` and `04-facing-left.png` is **8.69%** —
just below the Phase 12.5 D-08 `> 10%` threshold for REQ-3b. Per the test message at
`PlatformerTemplateUatTest.kt:556-557`, this is acknowledged-acceptable: "If diffRatio < 10%
despite ≥80-frame scroll: REQ-3a (human-verify) is the primary closure signal — proceed to
checkpoint for duck-art approval."

### Human-verify checkpoint

Per CLAUDE.md §"Verification Methodology — Visual Evidence Rule", a visual truth ("the duck is
visible and recognizable") REQUIRES a human-confirmed runtime screenshot. The four PNGs above
are ready for review. Set `status: resolved` ONLY after user approval of the post-fix duck
art.

### Test impact summary

- Backend-gbdk full suite: **GREEN** post-fix (`./gradlew :gbkt-backend-gbdk:test`).
- Platformer-template non-UAT tests (geometry, emission, IR): **GREEN** post-fix.
- Platformer-template UAT `anchor4MetaspriteAnimation` fails at the `> 10%` mechanical pixel-diff gate (8.69% measured). REQ-3a (human-verify) is the canonical closure — see message at PlatformerTemplateUatTest.kt:556-557.
- Pre-existing unrelated failure: `MetaspritesGeneratedSpriteByteIdentityTest` was already RED prior to E-02/E-03/E-04 (verified via `git stash` round-trip). Not in scope for this debug session.
