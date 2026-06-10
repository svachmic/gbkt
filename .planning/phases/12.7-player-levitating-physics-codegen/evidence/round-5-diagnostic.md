# Phase 12.7 Round-5 Diagnostic (verdict for W12-W13-W14)

**Captured:** 2026-05-26
**Plan:** 12.7-17
**HEAD at capture:** `worktree-agent-ae9bba682e9c94138` (post Plan 12.7-15 BLOCKED @ `3fd07e95`)
**Purpose:** Replace Plan 12.7-15's three-path recovery suggestion with a numeric verdict so
W12 (RED test), W13 (GREEN fix), and W14 (UAT-harness fix) target the exact defect rather
than guessing again.

This is a **NO-CODE-CHANGE evidence-gathering report.** No Kotlin / C / test files were
edited. The sections below cite source line numbers and runtime artifacts verbatim from
current HEAD so the next three plans can reference a single source of truth.

Mirrors Plan 12.7-01 W1 diagnostic discipline (see `evidence/diagnostic-baseline.md`).

---

## Section 1 — Level-1 tilemap floor row geometry

**Sources inspected:**

- `gbkt-examples/platformer-template/res/graphics/world1-area1.png` (level-1 tilemap source)
- `gbkt-examples/platformer-template/build/gbkt/generated/main.c` lines 528-553 (runtime probe site)
- `.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots/anchor-2/anchor2-variables.txt`
  (post-fix grounded state: `playerY_grounded: 1664` → `player_real_y = 104`)
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:261-265`
  (level-1 spawn `(40, 120)`, hitbox 8×24)

**Floor tile-row (level-1):** **row 16** at top-edge pixel **128**.

**Method (derived from snap formula + observed equilibrium):**

The post-Plan-12.7-11 snap formula (visitor source `PlatformerVisitor.kt:1284-1336`) computes:

```
foot_tile_row = (player_real_y + height) >> 3 = (104 + 24) >> 3 = 16
foot_pixel_top = foot_tile_row << 3 = 16 << 3 = 128
foot_pixel_anchor = foot_pixel_top - height = 128 - 24 = 104
_playerY = foot_pixel_anchor << 4 = 104 << 4 = 1664   (next-frame player_real_y = 104)
```

At equilibrium (the grounded state both anchors converge to), `player_real_y = 104` and the
HITBOX foot sits at pixel `104 + 24 = 128` — i.e., precisely at the top edge of tile-row 16.

**Cross-check vs. runtime trace:**

Anchor-2 `anchor2-variables.txt:7,9`:
```
playerY_grounded: 1664   → player_real_y = 104   → hitbox-foot pixel = 128 (top of row 16)
playerY_landed:   1664   → identical equilibrium
```

Anchor-5 `anchor5-variables.txt:20-22` (level-2):
```
playerY: 1664   → player_real_y = 104   → identical equilibrium (same tile-row geometry)
grounded: 1
```

Therefore: **level-1 AND level-2 floor row = row 16, top-edge pixel = 128.** The snap formula
correctly identifies and lands the hitbox foot on that pixel. Level-2 floor geometry IS
identical to level-1's at the equilibrium — **H3 is NOT a contributing hypothesis.**

**Caveat re: stale main.c.** The committed `build/gbkt/generated/main.c:549-550` still shows
the pre-Plan-12.7-11 broken-precedence form (`_playerY = foot_tile_row << 3u - 24u << 4u`),
NOT the intermediate-vars form in the visitor source. This is a stale build artifact in
the main checkout (the worktree's generated dir was never built). The visitor source at
`PlatformerVisitor.kt:1284-1336` is the authority; Plan 12.7-12's commit `879027a7` rebuilt
the ROM under the new emission and re-captured evidence — that ROM was used for the
anchor-2/anchor-5 evidence set. Any code change in W13 must trigger `:clean :buildRom` per
CLAUDE.md ROM-smoke rule.

---

## Section 2 — Metasprite pivot + render math

**Sources inspected:**

- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:328-333`
  (player metasprite declaration)
- `gbkt-examples/platformer-template/build/gbkt/generated/main.c:70-95` (emitted `metasprite_t[]` arrays)
- `gbkt-examples/platformer-template/build/gbkt/generated/bank1.c:46-65` (emitted `move_metasprite_*` call sites)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt:297`
  (y-arg emission: `"DEVICE_SPRITE_PX_OFFSET_Y + ($posYVar >> 4)"`)

**Metasprite declaration (PlatformerTemplate.kt:329-333):**

| Field | Value |
|---|---|
| `mode` | `SpriteMode.SPR8x16` |
| `pivot` | `(12, 6)` |
| `frameSize` | `(24, 32)` |
| hitbox (PlatformerTemplate.kt:177) | `(0, 0, 8, 24)` |

**Emitted `metasprite_t[]` table (main.c:70-72, frame 0):**

```c
const metasprite_t sprite_player_frame_0[] = {
    {-6, -12, 0},      // dy=-6, dx=-12, tile=0     ← top-left 8x16 tile
    {0, 8, 2},          // dy=+0, dx=+8, tile=2      ← next col (top row)
    {0, 8, 4},          // dy=+0, dx=+8, tile=4      ← next col (top row)
    {16, -16, 6},       // dy=+16, dx=-16, tile=6    ← newline: dy advances 16, dx back to col 0
    {0, 8, 8},          // dy=+0, dx=+8, tile=8
    {0, 8, 10},         // dy=+0, dx=+8, tile=10
    {metasprite_end}
};
```

`dy=-6` on the top-left tile encodes `pivotY = 6` (each tile's OAM-Y = caller's `y` arg
plus the tile's `dy`). The metasprite spans `dy = -6 .. +10` (TOP-left tile to BOTTOM-left
tile), each tile being 16 px tall under `SPRITES_8x16` mode, so the full metasprite
spans **32 px** from anchor-y `-6` to anchor-y `+26`.

**Emitted `move_metasprite_*` call (bank1.c:46-65, all 4 flip variants):**

```c
DEVICE_SPRITE_PX_OFFSET_Y + (_playerY >> 4)   // y-arg passed to move_metasprite_*
```

The y-arg is **plain `_playerY >> 4`** — NO pivot adjustment, NO half-height shift is added
at the C call site. The pivot is baked into the `metasprite_t[]` data table's negative `dy`
entries on the top row. GBDK's `move_metasprite_ex` adds the caller's y-arg + each tile's
`dy` + 16 (for SPRITES_8x16 OAM-Y register convention) to produce the OAM-Y value.

**Rendered metasprite extent given `_playerY >> 4 = 104`:**

| Pixel | Value | Computation |
|---|---|---|
| caller y-arg → `move_metasprite_*` | 120 | `DEVICE_SPRITE_PX_OFFSET_Y(16) + 104` |
| top tile OAM-Y register | 114 | `120 + dy(-6)` |
| top tile visible TOP (screen) | 98 | `114 - 16` (SPR8x16 OAM-Y → screen-Y) |
| top tile visible BOTTOM | 114 | `98 + 16` |
| bottom tile OAM-Y register | 130 | `120 + dy(+10)` (where +10 = -6 + 16 newline) |
| bottom tile visible TOP | 114 | `130 - 16` |
| **bottom tile visible BOTTOM (= metasprite bottom)** | **130** | `114 + 16` |
| **hitbox foot (snap target)** | **128** | `player_real_y(104) + hitbox.h(24)` |
| **METASPRITE-vs-HITBOX gap** | **130 − 128 = 2 px BELOW tile-row-16 top** | |

**This matches the user-reported anchor-2 "ALMOST perfect, 1-2 px too low, overlays
top 2 pixels of ground tile" verbatim.** The metasprite's visible bottom row extends 2 px
below the hitbox-foot snap target.

**Algebraic pivot_adjust constant:**

```
pivot_adjust = frameSize.height − pivotY − hitbox.height = 32 − 6 − 24 = 2
```

This 2-px constant is the exact correction needed to align the metasprite's visible bottom
with the hitbox foot (and therefore with the tile-row top edge).

---

## Section 3 — Current snap formula trace (player_real_y=104, height=24)

**Algebraic trace** (from `PlatformerVisitor.kt:1284-1336` visitor source):

| Step | Computation | Result |
|---|---|---|
| `foot_tile_row` | `(player_real_y + height) >> 3 = (104 + 24) >> 3` | **16** |
| `foot_pixel_top` | `foot_tile_row << 3 = 16 << 3` | **128** |
| `foot_pixel_anchor` | `foot_pixel_top - height = 128 - 24` | **104** |
| `_playerY` | `foot_pixel_anchor << 4 = 104 << 4` | **1664** |
| next-frame `player_real_y` | `_playerY >> 4 = 1664 >> 4` | **104** |
| hitbox-foot pixel | `player_real_y + height = 104 + 24` | **128** ← correct (top of row 16) |
| metasprite-bottom pixel | `player_real_y + (frameSize.h - pivotY) = 104 + 26` | **130** ← **2 px overshoot** |

**Algebra is preserved.** The hitbox foot lands at pixel 128 (tile-row-16 top edge) — the
snap formula is **algebraically correct for the hitbox**. But because the metasprite's
visible bottom extends `frameSize.h - pivotY = 26` px below the anchor (NOT `hitbox.h = 24`
px), the rendered foot pixel is 2 px below the hitbox foot.

**Generated C (visitor source emission for the snap-block, post-Plan-12.7-11
intermediate-vars rewrite — `PlatformerVisitor.kt:1284-1336`):**

```c
// Snap to tile-top: precedence-immune via intermediate CVarDecl locals
// (one binary-op class per line). Pins foot-row to underlying solid tile's
// top edge. Plan 12.7-11 — Path A; CParenExpr AST surgery deferred to seed.
UINT16 foot_tile_row = (player_real_y + 24u) >> 3u;
UINT16 foot_pixel_top = foot_tile_row << 3u;
UINT16 foot_pixel_anchor = foot_pixel_top - 24u;
_playerY = foot_pixel_anchor << 4u;
```

**Note:** The committed `build/gbkt/generated/main.c:549-550` shows the OLD broken-
precedence form (`_playerY = foot_tile_row << 3u - 24u << 4u`) — that artifact is STALE
relative to the visitor source. Plan 12.7-12's commit `879027a7` rebuilt the ROM under
the new emission for evidence re-capture, but the worktree's main.c was never rebuilt.
The visitor source IS the authority. W13 must run `:clean :buildRom` before re-shooting
evidence.

**The defect is NOT algebraic.** The hitbox foot lands exactly on tile-row-16's top. The
defect is in the snap-target choice: it targets the HITBOX foot, but the user sees the
METASPRITE foot. These differ by 2 px under the current pivot(12, 6) + frameSize(24, 32)
+ hitbox(0, 0, 8, 24) geometry.

---

## Section 4 — Anchor-5 capture-timing inspection

**Sources inspected:**

- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt:716-767`
  (anchor5LevelSwitch — `01-near-end.png` capture site)
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt:806-865`
  (anchor5LevelSwitch — `anchor5-variables.txt` write site)

**Captured frame for `01-near-end.png`:** **First frame of `nextLevelScene`**, captured at
line 727-728 IMMEDIATELY after the main()-loop guard fired `navigate_to_scene(SCENE_NEXTLEVELSCENE)`.

The comment at PlatformerTemplateUatTest.kt:716-721 is explicit:

> Screenshot 01: first frame of nextLevelScene ("near end" label is historical).
> The main()-loop guard fires navigate_to_scene(SCENE_NEXTLEVELSCENE) in the SAME
> main() iteration as the level-end trigger, so current_scene is already
> SCENE_NEXTLEVELSCENE by the time the loop exits. captureAndRename here captures
> the first rendered frame of the nextLevelScene.

**Captured frame for `anchor5-variables.txt`:** **frame 1277**, written at line 835-865
AFTER:

1. Scene flip to `nextLevelScene` (line 744)
2. `agent.stepN(3)` — let card scene-enter ops settle (line 759)
3. Capture nextLevelCardScreenshot (line 760-766)
4. Press START to advance through card (line 791-803)
5. `agent.stepN(130)` — let gameplay scene settle after the level-2 spawn (line 806)
6. Final read of `playerY/playerVy/grounded/current_level` from `agent.step()` (line 827-830)

The `playerY=1664, grounded=1, current_level=1` data in `anchor5-variables.txt` describes
the LEVEL-2 EQUILIBRIUM 130+ frames AFTER the screenshot frame. The PNG describes the
nextLevelScene's FIRST rendered frame (≈ frame 953 = `frames_to_trigger(952) + 1`).

**The PNG and variables.txt describe DIFFERENT MOMENTS in execution.**

**Implication for the "very much levitating" symptom:**

The `01-near-end.png` PNG shows the **first frame of the next-level card scene**, NOT a
gameplay frame near the level-1 right edge. At that frame:

- `current_scene = SCENE_NEXTLEVELSCENE` (card scene is active)
- The card scene's `enter` hook is mid-execution (per Phase 12.6 CYCLE 2 comment at line
  752-758: "scene-enter runs DURING that frame after a long sequence of bank-switching
  writes — empirically the frame buffer captured immediately afterwards reflects the
  PRE-enter state")
- `hide_sprites` may not have run yet (it's emitted in the card's appended-enter ops)
- The player sprite from the LAST gameplay frame is **still in OAM** at its
  pre-trigger position — `_playerX` was JUST set to 449 (last gameplay frame, right-edge
  trigger zone) and `_playerY` was the gameplay-frame final value

The "levitating" character the user sees in `01-near-end.png` is therefore the
**leftover gameplay-frame sprite still in OAM** while the BACKGROUND has flipped to the
nextLevelScene card art. The card-scene background is a DIFFERENT visual context (its
tilemap is the title/card art — `next-level.png` — NOT the level-1 floor); the player
sprite suspended over that unrelated background reads as "levitating" because the card
art has no ground row.

**The PNG label `01-near-end.png` is HISTORICAL** (Plan 12-23 round-2 era). The actual
captured state is "first rendered frame after the main()-loop guard navigated to
nextLevelScene with the previous frame's sprite OAM still in place".

**The "near end of level" capture truth that SPEC R-03 requires is NOT captured anywhere
in the current UAT harness.** The truth "player pinned to floor near right-edge trigger"
is a GAMEPLAY-frame truth, but the current harness flips to nextLevelScene one frame
after the trigger fires. To capture R-03's binding truth, a NEW screenshot must be added
that snapshots the LAST gameplay frame BEFORE the level-end-trigger main()-loop guard
runs `navigate_to_scene`.

---

## Section 5 — Hypothesis verdict

**Verdict:** **H1 + H2 (compound)**

**Evidence cited:**

- **H1 — Anchor-2 off-by-N (metasprite-vs-hitbox gap, 2 px):** Section 2 numeric trace shows
  metasprite-bottom at pixel 130 vs hitbox-foot at pixel 128. The 2-px constant equals
  `frameSize.height − pivotY − hitbox.height = 32 − 6 − 24`. Matches the user's
  "1-2 px too low, overlays top 2 pixels of ground tile" report on anchor-2 verbatim.
  Section 3 confirms the algebra is correct — the defect is in the snap-target choice.

- **H2 — Anchor-5 capture-timing decoupling:** Section 4 numeric trace shows the PNG captures
  the FIRST frame of `nextLevelScene` (≈ frame 953) while the variables.txt is written 324
  frames later at frame 1277 (post-level-2 settled state). The PNG label is historical;
  the actual capture is "scene-flip frame with stale-OAM player sprite over card-art
  background". The R-03 spec-cited "near-end-of-level" truth is NOT captured.

- **H3 (level-2 floor mismatch):** **REJECTED.** Section 1 verifies level-1 and level-2
  both equilibrate to `player_real_y = 104` with hitbox foot at pixel 128 (tile-row 16
  top). Floor geometry is identical between levels at the equilibrium.

- **H4 (`_playerY` not reset in `setup_current_level`):** **REJECTED.** `main.c:206, 274,
  342` show `_playerY = _level_spawn_y[N] << 4` IS written by `setup_current_level` for
  all three zones (this was Phase 12.6 D-06's broader fix, not just X). Anchor-5's
  `playerY=1664` at frame 1277 is the post-spawn snap-converged equilibrium, NOT a stale
  level-1 value.

- **H5 (1-frame-stale render):** **SUBSUMED BY H2.** The harness CAN capture 1+ frames
  behind variable state (Phase 12.6 debug Cycle 2 precedent), but in this case the PNG/
  variables decoupling is intentional — they describe different program moments by ≥ 324
  frames, not a 1-frame timing artifact.

**Why H1 + H2 are BOTH load-bearing:**

- H1 explains anchor-2's "almost perfect, 1-2 px too low" symptom directly. Fixing H1
  alone closes anchor-2 verification (R-02). H1 is a CODEGEN defect in PlatformerVisitor.
- H2 explains anchor-5's "very much levitating" symptom. Fixing H2 alone closes the
  evidence gap for R-03's "near-end-of-level" capture. H2 is a UAT-HARNESS defect in
  PlatformerTemplateUatTest.
- Without H1's fix: anchor-2 still shows 2 px overshoot; R-02 stays open.
- Without H2's fix: even with H1's snap correction, anchor-5's PNG still shows scene-flip-
  stale-OAM over card-art, NOT the R-03 binding state.

**W12 RED test target** (RED gate — must FAIL on current emission):

File: `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitorSnapTest.kt` (new).

Behavior: assert per-function (awk-brace-walk against `^void platformer_physics_update`)
that the snap-block contains `foot_pixel_anchor = foot_pixel_top - height - pivot_adjust`
where `pivot_adjust` is derived from the bound metasprite's geometry
(`frameSize.height - pivotY - hitbox.height`). The current emission omits `pivot_adjust`
entirely, so the test fails on HEAD.

Alternative simpler RED: assert the emitted snap-block lands the rendered METASPRITE-bottom
(not the hitbox foot) on the tile-row top, by computing the expected `_playerY` for the
platformer-template fixture (`pivot_adjust = 2`, expected `foot_pixel_anchor = 102`,
`_playerY = 1632`). The current emission produces `foot_pixel_anchor = 104`, `_playerY = 1664`
— RED.

**W13 GREEN fix target** (GREEN gate — passes the W12 RED):

File: `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:1240-1340`.

Change: extend `buildVerticalFootProbe`'s signature to accept the metasprite's
`pivotY` and `frameSize.height` (resolved at codegen time via the GenericSystem config
populated by `tilemapCollision { ... }` + `metasprite { ... }` bindings — both
configs already live on the active scene/game's `genericSystems` list). Compute
`pivot_adjust = frameSize.height − pivotY − hitbox.height`. Emit a fifth `CVarDecl`
named `pivot_adjust` and an updated `foot_pixel_anchor` line that subtracts it:

```c
UINT16 foot_tile_row = (player_real_y + 24u) >> 3u;
UINT16 foot_pixel_top = foot_tile_row << 3u;
UINT16 pivot_adjust = 2u;                              // NEW (derived from metasprite geometry)
UINT16 foot_pixel_anchor = foot_pixel_top - 24u - pivot_adjust;
_playerY = foot_pixel_anchor << 4u;
```

Round-trip with platformer-template fixture: spawn_y=120, height=24, pivot_adjust=2 →
foot_tile_row=18, foot_pixel_top=144, pivot_adjust=2, foot_pixel_anchor=118, _playerY=1888 →
next-frame player_real_y=118 (rendered metasprite-bottom at 118+26=144 = top of tile-row 18 — zero gap).
On settled grounded state: foot_tile_row=16, foot_pixel_top=128, pivot_adjust=2,
foot_pixel_anchor=102, _playerY=1632 → next-frame player_real_y=102 (rendered
metasprite-bottom at 102+26=128 = top of tile-row 16 — zero gap).

**Caveat:** `pivot_adjust` is currently a per-game constant (2 for platformer-template). A
fully-generic codegen derives it from MetaspriteIR.pivotY / frameSize.height /
TilemapCollisionConfig.hitbox.height at emit time. W13 must wire the codegen-time
lookup (the visitor already receives the active Game; both configs are on it). If that
lookup is too expensive for one plan, W13 may emit a hardcoded `pivot_adjust = 2`
locked to the platformer-template's metasprite — and SEED a follow-up for full generic
derivation. Document the choice in W13's SUMMARY.

**W14 UAT-harness target** (UAT gate — captures R-03's binding truth):

File: `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt:716`.

Change (ADDITIVE — preserves the existing scene-flip capture):

1. Add a NEW screenshot `00-last-gameplay.png` captured at the LAST gameplay frame BEFORE
   the main()-loop guard fires `navigate_to_scene(SCENE_NEXTLEVELSCENE)`. Hook this into
   the existing trigger-detection loop (around line 700) — when `detectedNext != initialNext`
   is observed (level-end trigger fired this frame), the PNG to capture is the one
   from the PREVIOUS step's frame buffer, or equivalently: keep a 1-deep ring buffer of
   the last step's framebuffer and write that to `00-last-gameplay.png` when the trigger
   is detected. Alternative: detect the trigger-pending state ONE frame earlier by polling
   `_playerX > some_threshold` (the gbkt platformer trigger is X-edge-based) and snapshot
   then.

2. RENAME the existing `01-near-end.png` → `01-nextlevel-flip.png` (the historical label
   is misleading; the actual capture is the scene-flip frame).

3. UPDATE SPEC R-03 / acceptance criteria comments to cite `00-last-gameplay.png` as the
   binding "player pinned to floor near right-edge trigger" evidence, and
   `01-nextlevel-flip.png` as supplementary evidence of the scene-flip path (which is
   itself useful for Phase 12.6 regression coverage).

W14 is purely a TEST CHANGE — no codegen, no Kotlin DSL, no IR change. SCOPE BOUNDARY:
W14 may not modify any other UAT test or any code outside the test file.

---

## Provenance

- **Plan 12.7-17** — diagnostic (this document).
- **Plan 12.7-15** — BLOCKED gate; verdict=blocked; three recovery paths offered to user.
  This diagnostic REPLACES the "pick one and hope" routing with a numeric verdict.
- **Plan 12.7-12** — evidence re-capture (the 6 PNGs + 2 variables.txt this analysis depends on;
  commit `879027a7`).
- **Plan 12.7-11** — intermediate-vars snap rewrite (the algebra layer is correct; the
  visual outcome was not yet diagnosed; commit lives in the visitor source).
- **Plan 12.7-01** — diagnostic-baseline.md (format reference for this document).

## Open requirements after this plan

R-01, R-02, R-03 remain OPEN. Plan 12.7-17 produces a verdict only — it does NOT close
them. W12 (RED test), W13 (GREEN fix), W14 (UAT-harness fix) are the closure plans.

The verdict pins:

- **Snap formula site** at `PlatformerVisitor.kt:1240-1340` (W13 GREEN target).
- **Snap formula RED test site** at `gbkt-genre-platformer/src/test/.../PlatformerVisitorSnapTest.kt` (W12 RED target — new file).
- **Anchor-5 capture site** at `PlatformerTemplateUatTest.kt:716-767` (W14 UAT target).

The diagnostic verdict is concrete enough for W12 to write a RED test against, W13 to
write the GREEN fix against, and W14 to write the UAT change against without further
investigation.
