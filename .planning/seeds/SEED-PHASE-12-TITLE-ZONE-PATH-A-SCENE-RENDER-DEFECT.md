# SEED: Phase 12 — Title-Zone D-01 Path A Scene-Render Defect

**Created:** 2026-05-23 (Phase 12.2 close — terminal close as `gaps_found`)
**Updated:** 2026-05-23 (scope narrowed after user clarified per-image verdict; gameplay PASS, title-only FAIL)
**Origin phase:** 12.2 (ConvertZoneTilesetsTask real-tilemap extraction via png2asset -map mode)
**Source:** Phase 12.2 Plan 10 Task 2 `checkpoint:human-verify` — title image REJECTED, gameplay image APPROVED.
**Status:** closed (2026-05-23 — see ## Closure below) — Phase 12.2 verdict flipped from gaps_found to passed via inline Plans 12.2-11/12/13.
**Routing:** Closed — Phase 12.2 verdict flipped to passed via the recommended routing: /gsd:debug session at .planning/debug/title-zone-path-a-render.md confirmed Hypothesis A; /gsd:plan-phase 12.2 --gaps produced inline Plans 12.2-11/12/13; Plan 12.2-12 Task 2 human-verify APPROVED both images per-image.
**Blast radius:** MEDIUM (scoped to `titleZone` D-01 Path A scene-enter render call — most likely in `gbkt-backend-gbdk/.../codegen/visitor/SceneVisitor.kt` and/or the Path A tile-loading glue. Gameplay Path B is confirmed working at the runtime tier.)

## Symptom

- **JVM tier — GREEN across all of REQ-2/3/4/5.** Phase 12.2 emits correct tilemap bytes
  for every zone:
  - Plan 12.2-05 (REQ-2): `_zone_world1Area1Zone_tilemap_raw_map` row 31 contains real ground bytes `{0x08..0x0d}` (empirically measured), NOT modulo-tiled sky `0x11`.
  - Plan 12.2-06 (REQ-3): WIDTH/HEIGHT macros are derived from PNG IHDR — `world1Area1Zone` = 60×32, `titleZone` = 20×9, `banks play_zone` = 2×2.
  - Plan 12.2-07 (REQ-4): Missing-PNG diagnostic fires `IllegalArgumentException` correctly.
  - Plan 12.2-08 (REQ-5): BankingAnalysisPass overflow guard is in place.
  - Plan 12.2-09 (REQ-6 build): 5-ROM regression sweep is GREEN. `platformer-template` ROM bank 2 = 6120 B = 3×1920 + 2×180 — the byte math is exact.

- **Visual tier — SPLIT verdict from the human-verify checkpoint.**

  **Re-shot `02-gameplay.png` (Plan 12-19 anchor1 gameplay frame) — APPROVED.** The user
  confirmed it matches the upper-left region of `world1-area1.png` (with a small
  rightward camera offset and the placeholder player metasprite mid-jump on-screen —
  both expected gameplay-state behaviour, captured in `SEED-PHASE-12-PLAYER-METASPRITE-RENDER`).
  **The gameplay zone (D-01 Path B, two-invocation, `tilemap(asset(...))`) renders the
  real tilemap correctly at runtime, not just at the JVM tier.** REQ-2/3 are confirmed
  at the runtime tier, not just at the byte-emission tier. Defect 7's render-path
  half is closed for Path B.

  **Re-shot `01-title.png` (Plan 12-19 anchor1 title frame) — REJECTED.** The title art
  is visually identical to the pre-12.2 buggy behaviour (scrambled / row-doubled title
  text). The user's initial "both incorrect" verdict was retracted once the four images
  were shown one-at-a-time with explicit labels — the gameplay image then read as
  correct; only the title remains broken.

This narrows the scope dramatically vs. the original "generic render path doesn't
consume real tilemap" framing: **the defect is in the title-zone D-01 Path A
scene-render path specifically. Path B (gameplay) works end-to-end.**

## Why this scoping matters

D-01 (locked during `/gsd:discuss-phase 12.2`) defines two ConvertZoneTilesetsTask paths:

- **Path A (one-invocation, `tileset()` only — no `tilemap()`):** `titleZone` and
  `nextLevelZone`. png2asset runs ONCE with the tileset PNG, and the same PNG doubles as
  the tilemap source. Emits `_zone_<id>_tileset_map[]` as the on-screen layout.
- **Path B (two-invocation, `tileset()` + `tilemap()`):** the 3 world*-area* zones.
  png2asset runs TWICE: once for the tileset, once with `-maps_only -source_tileset` for
  the area PNG to emit `_zone_<id>_tilemap_raw_map[]`.

The runtime visual evidence after Phase 12.2:

| Zone | Path | DSL | Tilemap symbol | Runtime render |
|------|------|-----|----------------|----------------|
| `world1Area1Zone` | B | `tilemap()` set | `_zone_..._tilemap_raw_map` | ✓ correct |
| `world1Area2Zone` | B | `tilemap()` set | `_zone_..._tilemap_raw_map` | — (not in anchor1) |
| `world2Area1Zone` | B | `tilemap()` set | `_zone_..._tilemap_raw_map` | — (not in anchor1) |
| `titleZone` | A | `tilemap()` NOT set | `_zone_..._tileset_map` | ✗ scrambled / row-doubled |
| `nextLevelZone` | A | `tilemap()` NOT set | `_zone_..._tileset_map` | — (not in anchor1) |

The visual evidence is one-image-deep for the title-only failure (anchor1 only exercises
the `titleZone` + first gameplay zone), but the conclusion holds: **Path A's render path
is broken, Path B's render path is correct.**

## Likely cause hypotheses (Path A-specific)

In order of prior probability given the gameplay-PASS / title-FAIL split:

### Hypothesis A (most likely): Scene-enter visitor hardcodes HEIGHT=18 for the title scene

`title-screen.png` is 160×72 pixels = 20×9 tiles = 180 bytes. Phase 12.2 emits
`_zone_titleZone_tilemap_WIDTH = 20` and `_zone_titleZone_tilemap_HEIGHT = 9` per the
new variable-WIDTH/HEIGHT path (REQ-3, Plan 12.2-06; LOCKED by JVM test).

If the title scene-enter call is `set_bkg_tiles(0, 0, 20, 18, _zone_titleZone_tileset_map)`
with `HEIGHT=18` HARDCODED (the pre-12.2 fixed 32×32 / 20×18 assumption), it reads 180
bytes from the 180-byte buffer (no out-of-bounds — no crash) but lays them out across
18 rows instead of 9. The same 180 bytes get split across twice as many output rows,
producing the **title row-doubling visual signature** the user reports.

**This hypothesis is consistent with:** Path B working (the 3 gameplay zones emit
`HEIGHT=32` correctly, so even if the scene-enter call hardcoded HEIGHT it would
coincidentally match for gameplay — or, more likely, the Path B render call uses the
new macros and the Path A render call was never migrated).

### Hypothesis B: Path A scene-enter references the wrong tilemap symbol

Plan 12.2-03 introduced different symbol names for the two paths:
- Path A emits `_zone_<id>_tileset_map[]`
- Path B emits `_zone_<id>_tilemap_raw_map[]`

If the title scene-enter visitor was migrated to expect `_tilemap_raw_map` (the new Path
B name) but Path A still emits `_tileset_map`, the linker either falls through to a
stale symbol (if both exist) OR fails (which would have shown up in Plan 09's GREEN
buildRom — so falls through to stale is more likely). Conversely, if the scene-enter
visitor still expects `_tileset_map` for Path A but the actual Path A code went through
some other transformation, similar mismatch.

**This hypothesis is consistent with:** gameplay (Path B) working because the
`_tilemap_raw_map` lookup hits the new bytes; title (Path A) breaking because the
`_tileset_map` lookup hits stale / wrongly-laid-out bytes.

### Hypothesis C: Path A's tile-index base is misaligned with the new tilemap byte values

Looking at `02-gameplay.json`'s sidecar `current_tileset_id = 255` (frame 155). 255 is a
sentinel / uninitialized value — that was suspicious in the original "all rendering
broken" framing. With gameplay confirmed working, the 255 sentinel is likely a
red herring for the gameplay path (the tilemap rendered correctly despite the sentinel,
implying the tileset was loaded by a different path). But for the title scene the same
sentinel may indicate a tileset-base misalignment specific to Path A: the title-zone
tile bytes reference tile indices `{0x00..0xNN}` but the VRAM base wasn't set or was
set wrong, so the title renders garbage.

**This hypothesis is consistent with:** the 255 sentinel being a Path A artifact, and
the gameplay scene having its own correctly-set tileset base via a different code path.

### Hypothesis D (less likely now): Bank switching wrong for title

The title scene-enter could `switch_bank(1)` instead of bank 2 — but Path A title bytes
likely live in bank 1 (the tileset bank, not the new tilemap bank). Plan 09's
regression sweep showed bank 2 = 6120 B for the THREE gameplay tilemaps + 2 title
tilemaps; if the title tilemap is in bank 2 but title scene-enter doesn't switch there,
that explains it. But the byte math (6120 = 3×1920 + 2×180) suggests title IS in bank 2
— so this is conditional on whether the scene-enter performs the bank switch correctly
for Path A.

## Investigation entry points

Recommended entry order for `/gsd:debug` or a diagnostic spike, prioritising the
title-zone Path A code path:

1. **Generated C for the title scene** — fastest path to confirm Hypothesis A:
   ```
   grep -nC2 'set_bkg_tiles' gbkt-examples/platformer-template/build/gbkt/generated/*.c
   ```
   Look for the title scene-enter (`scene_enter_title` or similar). If you see hardcoded
   `18` or `20` in the `set_bkg_tiles(..., w, h, ...)` argument list instead of
   `_zone_titleZone_tilemap_WIDTH` / `_HEIGHT` macros, Hypothesis A is confirmed and
   the fix is mechanical.

2. **Scene-enter visitor source** — `gbkt-backend-gbdk/.../codegen/visitor/SceneVisitor.kt`
   (or `ScreenVisitor.kt`, `ZoneCodegen.kt` — whichever emits `set_bkg_tiles` for scene
   enter). Verify it references the per-zone WIDTH / HEIGHT macros and the correct
   symbol-name-per-path (`_tileset_map` for Path A vs `_tilemap_raw_map` for Path B).

3. **Path A vs Path B in `ConvertZoneTilesetsTask` and downstream visitor** — the asymmetry
   is the load-bearing clue. What does the visitor consume differently between Path A
   and Path B? Re-read `Plan 12.2-03-SUMMARY.md` `parseMapArrayBytes` invocation list
   for the two paths.

4. **Tileset base + VRAM allocation** — `gbkt-backend-gbdk/.../codegen/pipeline/MultiTilesetAllocation.kt`
   or the equivalent base-allocation pass. Verify Path A's tileset base matches the
   tile-index convention used by the Path A `_tileset_map` bytes.

5. **Bank-switch glue at title scene-enter** — verify the title scene-enter switches to
   the correct bank before calling `set_bkg_tiles`. Cross-reference against bank 2 = 6120
   B math.

## Evidence pointers

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.png` — fresh post-12.2 capture (still defective visually — Path A broken)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/02-gameplay.png` — fresh post-12.2 capture (CORRECT — Path B working; slight rightward offset and player sprite mid-jump are expected gameplay-state)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.json` — variable sidecar at frame 121 (`current_tileset_id=255`, `current_scene=0`)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/02-gameplay.json` — variable sidecar at frame 155 (`current_scene=2`, `current_area_bank=2`, `current_level_width_in_tiles=60`)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/evidence/regression-sweep.md` — bank 2 = 6120 B math (bytes ARE in the ROM)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-03-SUMMARY.md` — `parseMapArrayBytes` invocation list (Path A vs Path B symbol names)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-06-SUMMARY.md` — `_zone_titleZone_tilemap_HEIGHT = 9` proven at JVM tier

## Revival Condition

This seed is the load-bearing reason Phase 12.2 closes as `gaps_found` (AC13 FAIL).
Revival is **immediate** — the next user action should route to:

- `/gsd:debug` against Hypothesis A first (it's a generated-C grep — cheapest to confirm
  or rule out), then escalate to B/C/D if A is ruled out.
- After diagnosis: `/gsd:plan-phase 12.2 --gaps` for in-phase closure. The fix radius is
  now scoped tightly enough (one render path, one scene type) that a sub-sub-phase
  (12.2.1) would violate the project rule "subphases must CLOSE their defect cluster".

The seed is satisfied when:

1. A re-run of `PlatformerTemplateUatTest.anchor1Title_to_Gameplay` produces a fresh
   `01-title.png` that renders the title-screen.png art as a single 9-tile-high block
   at the top of the screen (no row-doubling).
   - [x] SATISFIED via Plan 12.2-12 Task 1 (test exits 0; fresh 01-title.png at .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.png; sha256 differs from pre-Plan-12.2-11 HEAD version).
2. `02-gameplay.png` continues to render correctly (no regression from the working Path B).
   - [x] SATISFIED via Plan 12.2-12 Task 1 (fresh 02-gameplay.png captured; Plan 12.2-12 Task 2 human-verify gameplay APPROVE — no Path B regression).
3. The human-verify checkpoint APPROVES the title image.
   - [x] SATISFIED via Plan 12.2-12 Task 2 human-verify per-image APPROVE for both 01-title.png and 02-gameplay.png (verbatim verdict in 12.2-VERIFICATION.md ## Close-out summary).
4. `12.2-VERIFICATION.md` is updated to flip Gap 1's status from `failed` to `resolved`
   and AC13 from FAIL to PASS. Phase verdict moves from `gaps_found` to `passed`.
   - [x] SATISFIED via Plan 12.2-13 Task 1 (this plan: VERIFICATION.md frontmatter status=passed; AC13 row PASS; Gap 1 block status=resolved; ## Close-out summary section added).

## Related artifacts

- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-10-PLAN.md` — the plan that surfaced this gap at its human-verify checkpoint
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-VERIFICATION.md` — phase-level verdict (`gaps_found` because AC13 fails)
- `.planning/phases/12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asse/12.2-SPEC.md` REQ-6 — the visual acceptance criterion this gap blocks
- `.planning/seeds/SEED-PHASE-12-PLAYER-METASPRITE-RENDER.md` — neighbouring seed; relevant for `02-gameplay.png`'s placeholder square (which is correct rendering, not a gap)
- CLAUDE.md §"Verification Methodology — Visual Evidence Rule" — the policy that flagged the gap. Without that rule, the JVM-tier GREEN would have closed Phase 12.2 falsely on the title side
- Phase 12 parent UAT — Plan 12-19 (`anchor1Title_to_Gameplay`) remains `blocked-pending-escalation` until this seed is closed

## Closure

**Closure date:** 2026-05-23
**Closure plans:** Phase 12.2 inline gap-closure Plans 12.2-11 / 12.2-12 / 12.2-13.
**Closure debug session:** `.planning/debug/title-zone-path-a-render.md` (status=resolved, Hypothesis A CONFIRMED in step 1 of investigation).

**Hypothesis outcome:** A (most likely) was the actual root cause — SceneVisitor.kt scene-enter `_bkg_tiles_load_banked` hardcoded `CLiteral(zone.mapWidth)` / `CLiteral(zone.mapHeight)` (ZoneIR defaults = 32×32) for Path A zones instead of consuming the per-zone `_zone_<id>_tilemap_WIDTH/_HEIGHT` macros emitted by ConvertZoneTilesetsTask (Plan 12.2-06). Title was 20×9, not 32×32 — 1024 entries read from 180-byte buffer, producing the row-doubling visual signature. Hypotheses B (wrong tilemap symbol), C (VRAM base misalign), D (bank-switch wrong) were not investigated because A was confirmed in the first investigation step.

**Fix:** SceneVisitor.kt now branches on `zone.tilesetPath != null` — NEW-path zones emit `CVar("_zone_${zoneSanitized}_tilemap_WIDTH/_HEIGHT")`, LEGACY-path procedural zones keep `CLiteral(zone.mapWidth/mapHeight)` for back-compat. SceneVisitorTest TEST 15 + TEST 16 lock both branches at the JVM tier. TitleSceneEmissionTest macro assertion locks the title_enter brace-walked body.

**Post-fix evidence:**
- Generated `gbkt-examples/platformer-template/build/gbkt/generated/bank1.c` line for `title_enter`: `_bkg_tiles_load_banked(2u, 0u, 0u, _zone_titleZone_tilemap_WIDTH, _zone_titleZone_tilemap_HEIGHT, _zone_titleZone_tilemap);` (pre-fix shape was `2u, 0u, 0u, 32u, 32u, _zone_titleZone_tilemap`).
- Equivalent for `nextLevel_enter`: `_bkg_tiles_load_banked(2u, 0u, 0u, _zone_nextLevelZone_tilemap_WIDTH, _zone_nextLevelZone_tilemap_HEIGHT, _zone_nextLevelZone_tilemap);`.
- Fresh anchor-1 `01-title.png` (post-fix) at `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.png` — human-APPROVED at Plan 12.2-12 Task 2.
- Fresh anchor-1 `02-gameplay.png` (regression guard) at the same directory — human-APPROVED (no Path B regression).

**Audit trail preserved:** the pre-fix PNG content + the pre-Plan-12.2-11 Gap 1 evidence block live in git history (`git show HEAD~N:.../01-title.png` retrieves the broken state); the debug session record + the seed Symptom/Hypotheses sections remain unchanged as the historical investigation record.
