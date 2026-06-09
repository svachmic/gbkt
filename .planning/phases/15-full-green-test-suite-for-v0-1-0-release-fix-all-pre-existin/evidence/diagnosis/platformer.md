# Diagnosis fragment — platformer-template (3 classes) — F3/F4/F7

**Plan:** 15-05 · **Requirement:** REQ-6

## PlayerMetaspriteGeometryTest ×2 (F3/F4) — `provably-stale-assertion` (D-03b static)

**Symptom:** both cases fail `sprite_player_frame_0[] not found in main.c`.

**Root cause:** the player metasprite is **Path A png2asset-native**. The per-frame arrays no
longer live in `main.c` (`main.c:70` is only a `/* Path A … */` comment); they live in
`build/gbkt/generated/sprites/player.c` as `const metasprite_t player_metasprite0[] = {
METASPR_ITEM(-6,-12,0,S_PAL(0)), … }` (verified on disk, lines 143-150), produced by the Gradle
`convertSprites` (png2asset) task — NOT by `GBDKPipeline.generate()`. So the test's in-JVM
`mainC()` could never contain the array, and the `{int,int,int}` regex could never match the
`METASPR_ITEM(...)` macro form. The CONTEXT D-04 "one-token grep rename" is under-scoped (research
Pitfall 1).

**Geometry is correct/byte-identical:** the 6 `METASPR_ITEM` rows
`(-6,-12,0)(0,8,2)(0,8,4)(16,-16,6)(0,8,8)(0,8,10)` → cumulative x `{-12,-4,4}` (3 cols), y
`{-6,10}` (2 rows) = the documented 3col×2row 24×32 SPR8x16 grid. So this is a CORRECTION, not a
removal (D-04).

**Fix Path = `provably-stale-assertion`** (4-part repoint, the D-04-under-scoping deviation):
(a) SOURCE → read on-disk `build/gbkt/generated/sprites/player.c` (skip via `Assumptions.assumeTrue`
if genuinely absent — not used to mask a present-but-wrong asset); (b) SYMBOL →
`player_metasprite0` (the per-frame array, NOT the `player_metasprites[12]` pointer table, NOT
`sprite_player_frame_0`); (c) PARSER → `METASPR_ITEM\(\s*(-?\d+),\s*(-?\d+),\s*(\d+)\s*,` (terminator
`METASPR_TERM` excluded naturally); (d) ASSET ACQUISITION → `tasks.test { dependsOn("convertSprites") }`
in platformer-template/build.gradle.kts so the asset is fresh before `:test`. The brace-walk
`extractArrayBody` and all geometry cluster assertions are preserved. **Runs GREEN, executed not
skipped** (`tests="2" skipped="0" failures="0"`).

**Codegen-touch:** NONE (test + build-wiring only). **D-04 deviation:** recorded — D-04's one-token
rename is provably insufficient; the array left main.c entirely.

## PlatformerTemplate128UatTest.anchor4MetaspriteAnimation ×1 (F7) — `provably-stale-assertion` (D-03 live screenshot)

**Symptom:** `facing-right vs facing-left pixel diff is 6.80% (must be > 10%)`.

**Live D-03 evidence (MCP GBC-mode, evidence/platformer-facing-{right,left}.png):** I drove the ROM
live (title → gameplay → walk RIGHT → settle → flip LEFT → settle) at a SETTLED camera (`cam_x=0`,
identical bgText between captures). Measurements between facing-right and facing-left:

| Scope | Diff |
|-------|------|
| Full frame (160×144) | **2.20%** |
| Player sprite region (x[40–74] y[96–127]) | **45.36%** |
| Diff bounding box | x[45–69] y[100–127] — exactly the player sprite, ZERO background diff |

OAM at facing-left: all 6 player sprites `xFlip=true`, `facingRot=3`, `player_flipX=3`, reversed
column order — a genuine hardware hflip. The duck is visibly mirrored in the two clean GBC captures.

**Root cause:** the hflip is VISUALLY REAL, but the former `>10%` gate measured the diff over the
WHOLE frame. A 24×32 sprite is ~3.3% of a 160×144 frame; at a settled camera a perfect mirror
changes only ~2.2% of the full frame — so a `>10%` GLOBAL threshold is **arithmetically unreachable**
no matter how correct the flip (research Pitfall 4). The old test tried to inflate the number with
camera-scroll accumulation (hold right ≥80 frames), which is fragile and conflated sprite-flip with
background-scroll; the observed 6.80% reflects that fragile proxy, not a flip defect.

**Fix Path = `provably-stale-assertion`** — RE-ARCHITECT the measure (NOT lower 10%→6%, forbidden):
replace the global `VisualDiff` gate with a **sprite-region diff at a settled camera** (`>= 20%` of
the player's OAM bounding region must differ — live signal ~45%, threshold has wide margin) PLUS a
direct **OAM `xFlip`** assertion (facing-right sprites not flipped, facing-left sprites flipped),
triple-locked with the existing `facingRot==3` state assertion. No global threshold weakened.

**Codegen-touch:** NONE (test-side measure re-architecture only).

## PlatformerTemplateUatTest — NOT FAILING (drift)

Per FRESH-RUN-INVENTORY.md, `PlatformerTemplateUatTest` is GREEN on the main checkout
(`tests="5" failures="0"`); its only failing XML was a stale agent worktree. No change needed; it
must remain green after the above edits.

## Evidence ref

- F3/F4: `gbkt-examples/platformer-template/build/gbkt/generated/sprites/player.c:143` `player_metasprite0[]`; repointed test runs `tests=2 skipped=0 failures=0`
- F7: live D-03 `evidence/platformer-facing-right.png` / `evidence/platformer-facing-left.png` (full-frame 2.20%, sprite-region 45.36%); OAM xFlip true on facing-left
