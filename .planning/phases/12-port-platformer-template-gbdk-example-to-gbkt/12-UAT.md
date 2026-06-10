---
status: draft
phase: 12-port-platformer-template-gbdk-example-to-gbkt
source:
  - 12-CONTEXT.md
  - 12-RESEARCH.md
  - 12-VALIDATION.md
started: 2026-05-21
updated: 2026-05-21
---

# Phase 12 — UAT Contract (5 anchors per D-08)

This document is the **binding evidence-shape contract** for Phase 12. Every later plan
in this phase checks its row against this contract; UAT plans 12-19 .. 12-23 (or
whichever per-anchor evidence-capture plans the planner names) MUST satisfy each anchor
verbatim. No 6th anchor — per D-09, **5 anchors is a SECOND ONE-TIME EXPANSION** beyond
Phase 9 / 10's 3-anchor floor and Phase 11's 4-anchor expansion. Future ports MUST
justify any further expansion the same way; Phase 12 is NOT a stepping stone to ≥6
anchors. If Phase 12.1 surfaces, it inherits AT MOST 5 anchors.

Per D-10 (binding) and CLAUDE.md §"Verification Methodology — Visual Evidence Rule",
**every one of the 5 anchors has a visible truth** ("scene rendered", "tilemap
rendered", "player animated", "scroll progressed", "level switched") and MUST follow
the visual-evidence rule — runtime screenshots from the MCP `gbkt-emulator` server are
binding. Variable assertions PAIR with screenshots but never substitute for them; per
user memory `feedback_visual_evidence_for_visual_truths.md`, codegen GREEN is necessary
but never sufficient when the truth is visual.

## Visual Evidence Rule (quoted verbatim from CLAUDE.md)

> For verification truths shaped **"X is visible on screen"** (e.g., "track tilemap is
> visible", "HUD shows lap count", "menu cursor is highlighted"), evidence MUST include
> a runtime screenshot, NOT just a variable-state assertion.
>
> Variable assertions like `assertVariable("_current_tileset_id", 1)` prove that the
> codegen wrote a value at one point in scene-enter — they do NOT prove the value is
> visually reflected by the time the player sees the screen. A subsequent op (e.g., a
> user-authored `clear()` lowering to `cls()`) can wipe the visual outcome while leaving
> the variable intact.

All 5 Phase 12 anchors are visual truths. Each one pairs THREE evidence tiers per
VALIDATION.md §Per-Anchor Verification Map:

1. **Screenshot** — required for every anchor (binding).
2. **Variable assertion** — paired with the screenshot; never the sole evidence.
3. **JVM emission invariant** — per-function `awk` brace-walk + `grep` over generated C
   (per CLAUDE.md §"Scope-level grep gates corollary"). File-level `grep -c` is
   forbidden for per-function invariants — the awk pattern is binding.

---

## Anchors

### Anchor 1: Title → gameplay scene transition (D-08 #1)

- **Anchor ID:** D-08 #1
- **Goal (verbatim from CONTEXT.md D-08 #1):** Press Start on title scene → banked title
  screen unloads → world1Area1 tilemap loads → player metasprite visible. Variable
  assertion: scene id + nextLevel transitions. Screenshot: gameplay scene rendered with
  tilemap + player.
- **Setup:** Fresh ROM boot. ROM loaded at
  `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`.
- **Steps:**
  1. `emulator_start` (default DMG profile)
  2. `emulator_step 60` — boot lead-in, settle on title scene
  3. `emulator_screenshot evidence/uat-screenshots/anchor-1/01-title.png`
  4. `emulator_press start` (rising edge — banked title unloads, transitions to gameplay)
  5. `emulator_step 30` — let gameplay scene `enter` complete (`setup_current_level`
     runs, world1Area1 tilemap loads, player metasprite spawns)
  6. `emulator_screenshot evidence/uat-screenshots/anchor-1/02-gameplay.png`
  7. `emulator_read_variable _current_scene` (expect: gameplay scene id; pinned by UAT
     plans 19-23 after `Banks.kt`-equivalent / scaffold lands)
  8. `emulator_read_variable _current_level` (expect: 0 — world1Area1 active)
  9. `emulator_read_variable _next_level` (expect: 0 at startup; paired with #5 anchor)
- **Visual evidence (binding):**
  - `evidence/uat-screenshots/anchor-1/01-title.png` — title screen rendered with
    banked tile data (full-screen background tilemap, not window-text).
  - `evidence/uat-screenshots/anchor-1/02-gameplay.png` — gameplay scene rendered with
    world1Area1 tilemap visible AND player metasprite visible at spawn position.
- **Variable assertions (paired):**
  - `_current_scene == SCENE_GAMEPLAY` post-Start press (id pinned in UAT plans 19-23).
  - `_current_level == 0` (world1Area1 active after first level setup).
  - `_next_level == 0` at fresh boot (anchor 5's source variable).
- **JVM emission invariant (D-16 #1):**
  ```bash
  # title_frame body must contain navigate_to_scene
  awk '/^void title_frame/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/main.c \
    | grep 'navigate_to_scene'
  # gameplay_enter body must contain setup_current_level
  awk '/^void gameplay_enter/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/bank1.c \
    | grep 'setup_current_level'
  ```
- **Verdict criteria:** **GREEN iff** screenshot `02-gameplay.png` shows tilemap +
  player metasprite (visual check) **AND** variable assertions `_current_scene`,
  `_current_level`, `_next_level` hold **AND** both JVM emission invariant greps return
  non-zero.

---

### Anchor 2: Tilemap collision works (jump + land on solid) (D-08 #2)

- **Anchor ID:** D-08 #2
- **Goal (verbatim from CONTEXT.md D-08 #2):** Press A on grounded player → jump
  velocity sets → player rises → gravity overrides → player falls → 5-point
  bounding-box probe detects solid tile under feet → `y_velocity = 0` → grounded.
  Screenshot: player standing on a tile; screenshot: player mid-air mid-jump. Variable
  assertion: y_velocity transitions through 0 → -550 → 0 cycle. Locks D-12 contract.
- **Setup:** Fresh ROM boot → press Start → gameplay scene active (depends on Anchor 1).
- **Steps:**
  1. `emulator_start`
  2. `emulator_step 60` (boot lead-in)
  3. `emulator_press start` (title → gameplay)
  4. `emulator_step 30` (gameplay enter complete; player grounded on solid tile)
  5. `emulator_screenshot evidence/uat-screenshots/anchor-2/01-grounded.png`
  6. `emulator_read_variable _player_vy` (expect: 0 — grounded; sub-pixel y-velocity)
  7. `emulator_press a` (rising edge — jump impulse)
  8. `emulator_step 6` (mid-rise — gravity has not yet overcome the impulse)
  9. `emulator_screenshot evidence/uat-screenshots/anchor-2/02-mid-jump.png`
  10. `emulator_read_variable _player_vy` (expect: negative — rising)
  11. `emulator_step 60` (let gravity + tilemap-collision finish the cycle; player
      lands on solid tile, 5-point probe detects it, `_player_vy` snaps to 0)
  12. `emulator_screenshot evidence/uat-screenshots/anchor-2/03-landed.png`
  13. `emulator_read_variable _player_vy` (expect: 0 — grounded again post-land)
- **Visual evidence (binding):**
  - `evidence/uat-screenshots/anchor-2/01-grounded.png` — player standing on a solid
    tile, y-velocity at rest.
  - `evidence/uat-screenshots/anchor-2/02-mid-jump.png` — player mid-air, visibly
    above the ground tile.
  - `evidence/uat-screenshots/anchor-2/03-landed.png` — player back on solid tile
    after gravity completes the cycle (jump → fall → land → ground).
- **Variable assertions (paired):**
  - `_player_vy` transitions `0 → negative → 0` over the jump cycle (exact `-550`
    impulse pinned in UAT plans 19-23 after physics-config lands; sub-pixel scale).
- **JVM emission invariant (D-16 #2):**
  ```bash
  # is_tile_solid() must be NONBANKED in HOME with SWITCH_ROM entry+exit wrapping
  awk '/^UINT8 is_tile_solid/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/main.c \
    | grep -c 'SWITCH_ROM'   # expect 2 (entry switch to area bank, exit restore)
  # AND the solidity check itself uses _current_level_non_solid_tile_count
  awk '/^UINT8 is_tile_solid/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/main.c \
    | grep '_current_level_non_solid_tile_count'
  ```
- **Verdict criteria:** **GREEN iff** all 3 screenshots match the expected poses
  (visual check) **AND** `_player_vy` transitions 0 → negative → 0 **AND** the
  `awk … | grep -c SWITCH_ROM` returns `2` AND the `_current_level_non_solid_tile_count`
  grep returns non-zero.

---

### Anchor 3: Horizontal scroll works (camera moves, no repeat) (D-08 #3)

- **Anchor ID:** D-08 #3
- **Goal (verbatim from CONTEXT.md D-08 #3):** Hold right → player crosses half-screen
  threshold (80 px) → `_cam_x` increments → `move_bkg(_cam_x, 0)` fires →
  tile-boundary crossing triggers `set_bkg_submap(map_pos_x + DEVICE_SCREEN_WIDTH, 0,
  1, ...)` → tilemap content visibly DIFFERS from initial frame (not repeating).
  Screenshot: scrolled position visibly different from initial. Locks D-13 contract.
- **Setup:** Fresh ROM boot → press Start → gameplay scene active (Anchor 1
  prerequisite).
- **Steps:**
  1. `emulator_start`
  2. `emulator_step 60` (boot)
  3. `emulator_press start` (title → gameplay)
  4. `emulator_step 30` (gameplay enter complete)
  5. `emulator_screenshot evidence/uat-screenshots/anchor-3/01-initial.png`
  6. `emulator_read_variable _camera_x` (expect: 0 at gameplay start)
  7. `emulator_read_variable _map_pos_x` (expect: 0 at gameplay start)
  8. Sustained dpad-right held: loop of (`emulator_press dpad_right` / `emulator_step
     1`) × ~120 frames to push player past half-screen threshold (80 px) and cross
     multiple tile boundaries.
  9. `emulator_screenshot evidence/uat-screenshots/anchor-3/02-scrolled.png`
  10. `emulator_read_variable _camera_x` (expect: > 0 — camera advanced rightward)
  11. `emulator_read_variable _map_pos_x` (expect: > 0 — tile-boundary crossing fired)
- **Visual evidence (binding):**
  - `evidence/uat-screenshots/anchor-3/01-initial.png` — gameplay scene initial frame,
    camera at origin.
  - `evidence/uat-screenshots/anchor-3/02-scrolled.png` — scrolled frame, tilemap
    content visibly DIFFERS from `01-initial.png` (NOT a repeating-tile artifact;
    new tile content from the rightward column update is visible).
- **Variable assertions (paired):**
  - `_camera_x > 0` after rightward traversal past the half-screen threshold.
  - `_map_pos_x > 0` (the tile-boundary-crossing latch incremented).
- **JVM emission invariant (D-16 #3):**
  ```bash
  # platformer_camera_update() body must contain a set_bkg_submap call AND the
  # _old_map_pos_x guard variable (proves the column-update path is wired).
  awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/bank1.c \
    | grep 'set_bkg_submap'
  awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/bank1.c \
    | grep '_old_map_pos_x'
  ```
- **Verdict criteria:** **GREEN iff** `02-scrolled.png` visibly differs from
  `01-initial.png` (no-repeat tile content) **AND** `_camera_x > 0`, `_map_pos_x > 0`
  **AND** both `awk … | grep` invariants return non-zero.

---

### Anchor 4: Metasprite animation (multi-frame walking + hflip) (D-08 #4)

- **Anchor ID:** D-08 #4
- **Goal (verbatim from CONTEXT.md D-08 #4):** Hold right → `threeFrameCounter` cycles
  → metasprite frame index transitions through 0..2 (walk cycle) → MetaspriteVisitor's
  frame-switch emission visible in OAM. Screenshot sequence (3 captures over ~6 frames
  apart) showing pose differences. Plus hflip: hold left → frames mirror (Phase 10
  codegen path).
- **Setup:** Fresh ROM boot → press Start → gameplay scene active (Anchor 1
  prerequisite).
- **Steps:**
  1. `emulator_start`
  2. `emulator_step 60` (boot)
  3. `emulator_press start` (title → gameplay)
  4. `emulator_step 30` (gameplay enter complete)
  5. Hold dpad-right for 1 frame:
     `emulator_press dpad_right` / `emulator_step 1`
  6. `emulator_screenshot evidence/uat-screenshots/anchor-4/01-walk-frame-0.png`
  7. `emulator_read_variable _walkFrameIdx` (expect: 0 or 1 — first walk frame)
  8. Hold dpad-right for 6 more frames (
     `emulator_press dpad_right` / `emulator_step 6`)
  9. `emulator_screenshot evidence/uat-screenshots/anchor-4/02-walk-frame-1.png`
  10. `emulator_read_variable _walkFrameIdx` (expect: cycled to next walk frame)
  11. Hold dpad-right for 6 more frames
  12. `emulator_screenshot evidence/uat-screenshots/anchor-4/03-walk-frame-2.png`
  13. `emulator_read_variable _walkFrameIdx` (expect: cycled again — 3 distinct
      poses across `01`/`02`/`03`)
  14. Now hold dpad-left to exercise hflip path:
      `emulator_press dpad_left` / `emulator_step 6`
  15. `emulator_screenshot evidence/uat-screenshots/anchor-4/04-facing-left.png`
  16. `emulator_read_variable _facingRot` (expect: hflip marker — 3 per CONTEXT or
      whatever the codegen pins it to in UAT plans 19-23)
- **Visual evidence (binding):**
  - `evidence/uat-screenshots/anchor-4/01-walk-frame-0.png`,
    `02-walk-frame-1.png`, `03-walk-frame-2.png` — three distinct walk-cycle poses
    visible (left arm forward / mid / right arm forward, or equivalent per the
    6-frame metasprite asset).
  - `evidence/uat-screenshots/anchor-4/04-facing-left.png` — player metasprite
    rendered with hflip bit set (visible left-facing pose; Phase 10 codegen path).
- **Variable assertions (paired):**
  - `_walkFrameIdx` cycles 0 → 1 → 2 → 0 while dpad-right held.
  - `_facingRot == 3` (or equivalent hflip marker) when dpad-left held.
- **JVM emission invariant (D-16 #4):**
  ```bash
  # gameplay_frame body must dereference a sprite_player_frames[] descriptor table
  # AND emit a move_metasprite_flipx call for the hflip path.
  awk '/^void gameplay_frame/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/bank1.c \
    | grep 'sprite_player_frames\['
  awk '/^void gameplay_frame/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/bank1.c \
    | grep 'move_metasprite_flipx'
  ```
- **Verdict criteria:** **GREEN iff** the 3 right-facing screenshots show 3 distinct
  poses (visual check) **AND** the left-facing screenshot shows the hflip mirror
  **AND** `_walkFrameIdx` cycle holds **AND** `_facingRot` matches the hflip marker
  **AND** both `awk … | grep` invariants return non-zero.

---

### Anchor 5: Level-switch (gameplay → NextLevel card → gameplay level 2) (D-08 #5)

- **Anchor ID:** D-08 #5
- **Goal (verbatim from CONTEXT.md D-08 #5):** Player reaches level-end trigger →
  `nextLevel++` → banked NextLevel card displays → Start pressed → world1Area2 tilemap
  loads → player respawns. Screenshot: NextLevel card rendered; screenshot: level 2
  gameplay (visibly different tilemap from level 1).
- **Setup:** Fresh ROM boot → press Start → gameplay scene active → traverse
  rightward to the level-end trigger (Anchor 1 + Anchor 3 prerequisites).
- **Steps:**
  1. `emulator_start`
  2. `emulator_step 60` (boot)
  3. `emulator_press start` (title → gameplay)
  4. `emulator_step 30` (gameplay enter complete, level 0 / world1Area1 active)
  5. `emulator_read_variable _current_level` (expect: 0)
  6. `emulator_read_variable _next_level` (expect: 0)
  7. Sustained dpad-right held: loop until the player crosses the level-end trigger
     (per D-claude-6: either `goalZone()` at right edge OR
     `whenever(player.x isAtLeast currentZone.width - 32)`; planner pins exact frame
     count after substrate lands. Estimate: ~600–1200 frames depending on jumpHold
     traversal speed).
  8. `emulator_screenshot evidence/uat-screenshots/anchor-5/01-near-end.png`
  9. `emulator_read_variable _next_level` (expect: 1 — trigger fired, level-switch
     guard armed)
  10. Step further until the NextLevel card displays:
      `emulator_wait_for_scene next_level_card` or `emulator_step 30`.
  11. `emulator_screenshot evidence/uat-screenshots/anchor-5/02-nextlevel-card.png`
  12. `emulator_press start` (advance past NextLevel card → world1Area2 loads,
      `setup_current_level` re-runs, player respawns)
  13. `emulator_step 30` (gameplay re-enter complete on level 2)
  14. `emulator_screenshot evidence/uat-screenshots/anchor-5/03-level-2.png`
  15. `emulator_read_variable _current_level` (expect: 1 — world1Area2 active)
  16. `emulator_read_variable _next_level` (expect: 1 — matches current — guard
      cleared)
- **Visual evidence (binding):**
  - `evidence/uat-screenshots/anchor-5/01-near-end.png` — player near right edge of
    world1Area1, just before trigger.
  - `evidence/uat-screenshots/anchor-5/02-nextlevel-card.png` — banked NextLevel
    card displayed (full-screen tile data, not window-text).
  - `evidence/uat-screenshots/anchor-5/03-level-2.png` — gameplay re-entered with
    world1Area2 tilemap visibly DIFFERENT from world1Area1 (different tileset OR
    different tilemap content; reference uses world1-tileset for both world1 levels,
    so the tilemap layout MUST visibly differ at minimum).
- **Variable assertions (paired):**
  - `_current_level == 1` after level-switch (world1Area2 active).
  - `_next_level == 1` at switch trigger (transitions 0 → 1 over the cycle).
- **JVM emission invariant (D-16 #5):**
  ```bash
  # main()'s main_loop body must contain a level-switch guard that checks
  # _next_level vs _current_level and re-runs setup_current_level.
  awk '/^void main\(\)/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/main.c \
    | grep '_next_level'
  awk '/^void main\(\)/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' \
    gbkt-examples/platformer-template/build/gbkt/generated/main.c \
    | grep 'setup_current_level'
  ```
- **Verdict criteria:** **GREEN iff** all 3 screenshots match the expected sequence
  (visual check; `03-level-2.png` visibly differs from any world1Area1 screenshot)
  **AND** `_current_level` transitions 0 → 1 **AND** `_next_level` transitions
  0 → 1 **AND** both `awk … | grep` invariants return non-zero.

---

## Anchor count rationale

Per CONTEXT.md D-09: **5 anchors is a SECOND ONE-TIME EXPANSION** beyond Phase 9 /
10's 3-anchor floor and Phase 11's 4-anchor expansion. Justification: the Phase 12
integration contract has 5 distinct surfaces — scene-transition (anchor 1),
tile-collision (anchor 2), scroll (anchor 3), animation (anchor 4), level-switch
(anchor 5) — and folding any two reduces honesty about what "Phase 12 done" means.
The user explicitly accepted the 5-anchor expansion knowing Phase 11 D-09 had stated
"Phase 12 NOT pre-licensed to ≥4 anchors".

**Phase 12.1 (if it surfaces) inherits AT MOST 5 anchors, NOT a 6-anchor expansion.**
Future ports MUST justify any further expansion the same way; this contract is NOT a
stepping stone to ≥6 anchors.

---

## Evidence directory layout

All evidence artifacts live under
`.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/`. Per
CONTEXT.md D-17a (Phase 9 / 10 / 11 carry-forward layout):

- `evidence/reference/` — reference GBDK ROM artifacts. Contains:
  - `BUILD.md` — reproducibility doc (GBDK_HOME, `make` invocation, expected outputs:
    `platformer_template.gb`, `platformer_template.map`, `platformer_template.noi`).
    Per D-17a, the `.gb` / `.map` / `.noi` binaries themselves stay local (gitignored
    — reproducible from `BUILD.md`).
- `evidence/uat-screenshots/anchor-1/` — Anchor 1 (title → gameplay) screenshots:
  `01-title.png`, `02-gameplay.png`.
- `evidence/uat-screenshots/anchor-2/` — Anchor 2 (jump cycle) screenshots:
  `01-grounded.png`, `02-mid-jump.png`, `03-landed.png`.
- `evidence/uat-screenshots/anchor-3/` — Anchor 3 (horizontal scroll) screenshots:
  `01-initial.png`, `02-scrolled.png`.
- `evidence/uat-screenshots/anchor-4/` — Anchor 4 (walk cycle + hflip) screenshots:
  `01-walk-frame-0.png`, `02-walk-frame-1.png`, `03-walk-frame-2.png`,
  `04-facing-left.png`.
- `evidence/uat-screenshots/anchor-5/` — Anchor 5 (level-switch) screenshots:
  `01-near-end.png`, `02-nextlevel-card.png`, `03-level-2.png`.
- `evidence/oracle-comparison.md` — 3-signal artifact (D-17): ROM-size signal,
  generated-C diff signal, UAT-verdict signal. Plus bank-layout signal (`.noi`
  `DEF l__CODE_<N>` ≤ 16384 per Phase 11 D-15).

---

## Anti-overfitting note

UAT verifies the **INTEGRATION contract** (the 5 anchors) — NOT pixel-and-frame
parity with the GBDK reference. Per CONTEXT.md §"Out of scope": pixel-and-frame
parity with reference is explicitly OUT of scope. The reference is the codegen-shape
oracle for `IsTileSolid()` / `SetCurrentLevelSubmap()` / `UpdateCamera()` /
`SetupCurrentLevel()` shapes (D-overfitting-2/3 carry-forward), NOT the visual
ground-truth.

No DSL features are added to make screenshots pretty. The 4 pre-budgeted named
codegen surfaces (D-12 .. D-15) and the per-level `platformerPhysics` overrides ARE
new DSL surfaces — each justified as a SCALABLE ABSTRACTION future games will reuse,
NOT as "make this port pretty" (D-overfitting-1 exception acknowledged in
CONTEXT.md).
