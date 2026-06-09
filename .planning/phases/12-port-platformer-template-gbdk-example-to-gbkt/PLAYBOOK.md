# Platformer-Template — MCP Agent Playbook

This playbook is the **MCP-agent-runnable script** for the 5 anchors defined in the
companion UAT contract
[`12-UAT.md`](./12-UAT.md). Each anchor in this file maps 1:1 to `Anchor N (D-08 #N)`
in that contract; the contract is the *expected-state* doc and this playbook is the
*scripted-input* doc. UAT plans 12-19 .. 12-23 (or whichever per-anchor evidence-capture
plans the planner names) use this playbook verbatim as their script, then attach the
resulting screenshots + variable readings to
`evidence/uat-screenshots/anchor-N/` and to the per-plan SUMMARY.md.

**ROM path:**
`gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`

## Boot recipe

The MCP `gbkt-emulator` server (configured in `.claude/mcp_servers.json` and rebuilt
via `./gradlew :gbkt-mcp-server:shadowJar`) drives a Coffee-GB emulator and exposes
the 17 base tools listed in `context/UAT_GUIDE.md`. Every anchor below begins with the
same `emulator_start` invocation:

```
mcp__gbkt-emulator__emulator_start --rom gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
```

DMG mode is the default profile. **GBC mode via `--gbc` is manual-only** per
[`12-VALIDATION.md` §Manual-Only Verifications](./12-VALIDATION.md): the MCP emulator's
default DMG profile covers anchors 1–5; CGB-conditional palette load (per D-claude-4)
is a separate manual verification, NOT a sixth anchor.

After the start call, every anchor below uses these canonical tool names:

- `mcp__gbkt-emulator__emulator_press` — single-frame button press (rising edge).
  For held inputs, repeat in a step+press loop (see Anchor 3 and Anchor 4).
- `mcp__gbkt-emulator__emulator_step <N>` — advance N frames (no input).
- `mcp__gbkt-emulator__emulator_screenshot <path>` — capture PNG to disk (binding
  evidence per CLAUDE.md §"Verification Methodology — Visual Evidence Rule").
- `mcp__gbkt-emulator__emulator_read_variable <name>` — sample a metadata-exposed
  game variable (paired evidence; never sole).
- `mcp__gbkt-emulator__emulator_wait_for_scene <scene-id>` — block until the named
  scene becomes active (used in anchors 1 and 5 for scene-transition synchronization).

---

## Anchor 1 (D-08 #1): Title → gameplay scene transition

Goal: Press Start on title → banked title screen unloads → world1Area1 tilemap loads
→ player metasprite visible. Evidence binding: `evidence/uat-screenshots/anchor-1/`.

```
mcp__gbkt-emulator__emulator_start --rom gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
mcp__gbkt-emulator__emulator_step 60
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-1/01-title.png
mcp__gbkt-emulator__emulator_press start
mcp__gbkt-emulator__emulator_step 30
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-1/02-gameplay.png
mcp__gbkt-emulator__emulator_read_variable _current_scene
mcp__gbkt-emulator__emulator_read_variable _current_level
mcp__gbkt-emulator__emulator_read_variable _next_level
mcp__gbkt-emulator__emulator_wait_for_scene gameplay
```

Expected post-conditions (pinned by UAT plans 19-23 after substrate lands):
- `_current_scene` = gameplay scene id.
- `_current_level == 0` (world1Area1 active after first level setup).
- `_next_level == 0` at fresh boot.

---

## Anchor 2 (D-08 #2): Tilemap collision (jump + land on solid)

Goal: Press A on grounded player → jump impulse → gravity → 5-point bounding-box
probe detects solid tile under feet → grounded again. Evidence binding:
`evidence/uat-screenshots/anchor-2/`.

```
mcp__gbkt-emulator__emulator_start --rom gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
mcp__gbkt-emulator__emulator_step 60
mcp__gbkt-emulator__emulator_press start
mcp__gbkt-emulator__emulator_step 30
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-2/01-grounded.png
mcp__gbkt-emulator__emulator_read_variable _player_vy
mcp__gbkt-emulator__emulator_press a
mcp__gbkt-emulator__emulator_step 6
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-2/02-mid-jump.png
mcp__gbkt-emulator__emulator_read_variable _player_vy
mcp__gbkt-emulator__emulator_step 60
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-2/03-landed.png
mcp__gbkt-emulator__emulator_read_variable _player_vy
```

Expected post-conditions:
- `_player_vy` cycles 0 → negative (during rise) → 0 (post-land). Exact `-550`
  reference impulse value pinned by UAT plans 19-23 after physics-config lands;
  sub-pixel scale.

---

## Anchor 3 (D-08 #3): Horizontal scroll (camera moves, no repeat)

Goal: Hold right → player crosses half-screen threshold (80 px) → camera advances →
`set_bkg_submap` column update emits new tile content (NOT a repeating-tile
artifact). Evidence binding: `evidence/uat-screenshots/anchor-3/`.

For sustained dpad-right held, emit a step+press loop. The MCP `emulator_press`
fires a rising edge each call; the runtime D-pad-held DSL hook in gbkt reads the
hardware register every frame, so re-issuing the press each step preserves "held"
semantics until the loop ends.

```
mcp__gbkt-emulator__emulator_start --rom gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
mcp__gbkt-emulator__emulator_step 60
mcp__gbkt-emulator__emulator_press start
mcp__gbkt-emulator__emulator_step 30
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-3/01-initial.png
mcp__gbkt-emulator__emulator_read_variable _camera_x
mcp__gbkt-emulator__emulator_read_variable _map_pos_x
# Sustained dpad-right held — ~120 frames to push past half-screen threshold
# and cross multiple tile boundaries. Loop: press + step 1, repeat 120x.
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 1
# ... repeat the press + step pair ~120 times in the loop body ...
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 1
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-3/02-scrolled.png
mcp__gbkt-emulator__emulator_read_variable _camera_x
mcp__gbkt-emulator__emulator_read_variable _map_pos_x
```

Expected post-conditions:
- `_camera_x > 0` after rightward traversal past the half-screen threshold (80 px).
- `_map_pos_x > 0` — tile-boundary crossing latched, column-update fired.
- `02-scrolled.png` visibly DIFFERS from `01-initial.png` (no repeating-tile
  artifact; new tile content from the rightward column update is visible).

---

## Anchor 4 (D-08 #4): Metasprite animation (multi-frame walking + hflip)

Goal: Hold right → `threeFrameCounter` cycles → metasprite frame index transitions
through 0..2 (walk cycle) over ~6 frames apart. Then hold left → hflip path emits
left-facing pose via Phase 10's `MoveMetasprite.flipX` codegen. Evidence binding:
`evidence/uat-screenshots/anchor-4/`.

```
mcp__gbkt-emulator__emulator_start --rom gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
mcp__gbkt-emulator__emulator_step 60
mcp__gbkt-emulator__emulator_press start
mcp__gbkt-emulator__emulator_step 30
# First walk frame
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 1
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-4/01-walk-frame-0.png
mcp__gbkt-emulator__emulator_read_variable _walkFrameIdx
# Walk frame 1 — hold for 6 more frames (3-frame counter cycles ~once per 3 frames)
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 6
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-4/02-walk-frame-1.png
mcp__gbkt-emulator__emulator_read_variable _walkFrameIdx
# Walk frame 2 — 6 more frames
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 6
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-4/03-walk-frame-2.png
mcp__gbkt-emulator__emulator_read_variable _walkFrameIdx
# hflip path: hold left for 6 frames
mcp__gbkt-emulator__emulator_press dpad_left
mcp__gbkt-emulator__emulator_step 6
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-4/04-facing-left.png
mcp__gbkt-emulator__emulator_read_variable _facingRot
```

Expected post-conditions:
- `_walkFrameIdx` cycles 0 → 1 → 2 → 0 while dpad-right held; three distinct
  poses visible across `01-walk-frame-0.png`, `02-walk-frame-1.png`,
  `03-walk-frame-2.png`.
- `_facingRot == 3` (or equivalent hflip marker) when dpad-left held;
  `04-facing-left.png` shows the mirrored pose.

---

## Anchor 5 (D-08 #5): Level-switch (gameplay → NextLevel card → gameplay level 2)

Goal: Traverse to level-end trigger → `nextLevel++` → banked NextLevel card displays
→ Start advances past card → world1Area2 tilemap loads → player respawns. Evidence
binding: `evidence/uat-screenshots/anchor-5/`.

```
mcp__gbkt-emulator__emulator_start --rom gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
mcp__gbkt-emulator__emulator_step 60
mcp__gbkt-emulator__emulator_press start
mcp__gbkt-emulator__emulator_step 30
mcp__gbkt-emulator__emulator_read_variable _current_level
mcp__gbkt-emulator__emulator_read_variable _next_level
# Sustained dpad-right held — traverse to level-end trigger.
# Frame count is TBD; planner pins exact value (~600-1200 frames) after substrate
# lands. Loop: press dpad_right + step 1, repeat until level-end trigger fires.
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 1
# ... repeat press + step pair until the player reaches the level-end trigger ...
mcp__gbkt-emulator__emulator_press dpad_right
mcp__gbkt-emulator__emulator_step 1
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-5/01-near-end.png
mcp__gbkt-emulator__emulator_read_variable _next_level
# NextLevel card should now display — step ahead to settle
mcp__gbkt-emulator__emulator_step 30
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-5/02-nextlevel-card.png
# Advance past the card
mcp__gbkt-emulator__emulator_press start
mcp__gbkt-emulator__emulator_step 30
mcp__gbkt-emulator__emulator_screenshot evidence/uat-screenshots/anchor-5/03-level-2.png
mcp__gbkt-emulator__emulator_read_variable _current_level
mcp__gbkt-emulator__emulator_read_variable _next_level
```

Expected post-conditions:
- `_current_level` transitions 0 → 1 (world1Area2 active after switch).
- `_next_level` transitions 0 → 1 at trigger; after the switch completes,
  `_next_level == _current_level == 1` (guard cleared).
- `03-level-2.png` visibly differs from any world1Area1 screenshot (different
  tilemap content at minimum; reference shares world1-tileset across both
  world1 levels so the layout MUST differ).

---

## Variable naming note

Variable names like `_current_scene`, `_current_level`, `_next_level`,
`_player_vy`, `_camera_x`, `_map_pos_x`, `_walkFrameIdx`, `_facingRot` are
**TENTATIVE** — they reflect the expected codegen output but the exact spelling
follows whatever the gbkt DSL property delegates and platformer-genre codegen pin
once `Banks.kt`-equivalent / scaffold lands. UAT plans 12-19 .. 12-23 reconcile
this playbook with the actual codegen output:

1. Read the generated C in
   `gbkt-examples/platformer-template/build/gbkt/generated/` and the metadata
   in `game_metadata.json`.
2. Reconcile each variable name above against the metadata; update this playbook
   in-place if names differ (e.g., codegen may pin `_player_velocity_y` instead
   of `_player_vy`).
3. Pin exact `emulator_assert` expected values (the contract in `12-UAT.md`
   leaves these as transitions: 0 → negative → 0, > 0, == 1).

No exact expected values are baked into this playbook today; UAT plans 19-23 fill
them in. Per user memory `feedback_no_magic_strings.md`, those names MUST reflect
the actual property delegate names, NOT placeholders this playbook invents.

---

## Notes on held-input semantics

`mcp__gbkt-emulator__emulator_press <button>` fires a single-frame rising edge.
For continuous-held inputs (anchors 3, 4, 5 use sustained dpad-right or dpad-left),
the script uses a `press + step 1` loop body that the agent expands inline. The
runtime D-pad-held DSL hook (`whenever(dpad.right.held) { ... }` per CLAUDE.md
§"Input API Distinction") reads the hardware register every frame — re-issuing
the press each step preserves "held" semantics until the loop ends.

This playbook is the SCRIPT, not the EVIDENCE. UAT plans 19-23 execute these
scripts, capture the screenshots into `evidence/uat-screenshots/anchor-N/`,
record the variable readings, and produce per-anchor verdicts (GREEN iff the
verdict criteria in `12-UAT.md` hold).
