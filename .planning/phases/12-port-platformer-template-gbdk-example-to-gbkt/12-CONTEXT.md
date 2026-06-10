# Phase 12: Port platformer_template GBDK example to gbkt — Context

**Gathered:** 2026-05-19
**Status:** Ready for `/gsd-plan-phase 12` — research-driven planning REQUIRED (multi-bug
integration scope with 4 pre-budgeted named codegen surfaces + 3 new DSL primitives;
research must scout `gbkt-genre-platformer` lowering, `PlatformerVisitor`
camera-update path, asset pipeline multi-tileset story, and the GBDK reference's
exact `IsTileSolid` / `SetCurrentLevelSubmap` shape before plan-phase can carve waves).

---

<domain>
## Phase Boundary

Phase 12 re-implements the GBDK `platformer_template` example
(`/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/`, 802 LoC
across 5 .c files + 17 PNG assets) as an idiomatic gbkt DSL game. **Fourth and
final reference port** of the v1.0 milestone — the **integration check** that
all four building blocks validated by Phases 9 (sub-pixel physics), 10 +
10.1 + 10.2 (metasprites + GBC palette path), and 11 (banking + cross-bank
data + SRAM) compose correctly when assembled into one substantial game.

The GBDK reference is the **codegen-shape oracle** (per Phase 9/10/11
D-overfitting-1/2/3 carry-forward) — not a DSL authoring template. gbkt's
substrate uses its own declarative idioms; the reference is consulted for
the C-shape of `IsTileSolid()`, `SetCurrentLevelSubmap()`, `UpdateCamera()`,
multi-frame metasprite animation, and `SetupCurrentLevel()` level-switch.

This phase deliberately departs from Phase 9/10/11's ONE-named-codegen-bug-fix
discipline (D-01 below): the integration nature of the port surfaces multiple
codegen surfaces simultaneously, and the user accepted the wider scope.
Three primary axes the integration check exercises:

1. **Actor + metasprite player** rendered with multi-frame walking animation
   on top of a scrolling background.
2. **Multi-bank zone-tilemap data** (3 levels + 2 tilesets + banked title +
   banked NextLevel card) with FFD-driven bank packing.
3. **Pixel-physics gameplay** on a tilemap: gravity + variable-height jump +
   sub-pixel velocity + per-tile solidity check + horizontal-scroll camera
   with column-by-column tilemap update.

**In scope:**

- Lock the per-example UAT contract (5 anchor behaviors — second one-time
  expansion beyond Phase 9/10's 3-anchor floor; see D-09 for justification)
  BEFORE any DSL is written (Plan 1, mirrors Phase 11 D-11).
- Port `platformer_template` to idiomatic gbkt DSL in **new subdirectory**
  `gbkt-examples/platformer-template/` (D-02). Substrate is fully faithful:
  3 levels (`World1Area1`, `World1Area2`, `World2Area1`) + 2 tilesets
  (`World1Tileset`, `World2Tileset`) + 12-frame-equivalent player metasprite
  (authored as 6 frames + hflip-multiplier via Phase 10 codegen) + banked
  tile-data title screen + banked NextLevel transition card.
- Add **4 pre-budgeted named codegen surfaces** to genre-platformer + asset
  pipeline (D-12 .. D-15). Each is in scope; each gets its own JVM-tier
  emission invariant; each is a UAT-anchor source:
  1. **Tilemap-collision primitive** (`tilemapCollision` / `solidThreshold`
     on `platformerPhysics`, per-level overrides) with auto-derived 5-point
     bounding-box probe from the player actor's hitbox, lowered to a HOME-bank
     NONBANKED `is_tile_solid()` helper using the Phase 07.4-30 SWITCH_ROM
     wrapper pattern.
  2. **Column-by-column horizontal scroll** (`set_bkg_submap` updates) emitted
     inside `PlatformerVisitor.buildCameraUpdateFunction()` after the existing
     `move_bkg(_cam_x, _cam_y)` call, gated on `platformerCamera { horizontal() }` +
     `smoothFollow()` already-existing DSL hooks (no new DSL surface for the
     trigger, only for the codegen).
  3. **Variable-height jump** — new `platformerPhysics.jumpHold(maxFrames)`
     primitive + lowering that extends jump velocity while A/Up held up to
     `maxFrames` frames. Scout confirmed `platformerPhysics` has `jumpBuffer`
     but NOT explicit variable-height; this is a new field + lowering.
  4. **Multi-tileset asset pipeline + multi-zone bank allocation** — verify
     and (if needed) extend the asset pipeline + `allocateZoneBanks` to support
     N distinct tilesets across M zones referencing tilesets by ID. Pre-budgeted
     as a candidate named surface even though research may collapse it if "it
     just works" (D-15 escape hatch).
- Build the ROM via the standard gbkt pipeline
  (`:gbkt-examples:platformer-template:buildRom`), zero lcc warnings, zero
  SDCC `unknown address/value` errors, ROM boots to title cleanly.
- **Lifted bug cap (D-01)** — additional codegen defects discovered during
  the port are addressed inline (not relegated to seeds) up to a reasonable
  bound; only genuinely orthogonal / blast-radius-wide defects surface as
  seeds + conditional Phase 12.1.
- Capture surplus codegen defects that DON'T belong inline as seeds via
  `/gsd-capture --seed`. At port-close: if ≥1 surplus seed exists, insert
  Phase 12.1 placeholder in the same commit that closes Phase 12. Phase 12.1
  (if created) MUST be the terminal closer for any defect cluster — no Phase
  12.1.1 / 12.2 (per user memory `feedback_many_small_plans_terminal_subphase.md`).
- **Retire existing `gbkt-examples/platformer/`** during Phase 12 close
  (D-03): remove its subproject entry from `gbkt-examples/settings.gradle.kts`
  and delete (or archive) its directory. The `platform()` / `goalZone()` DSL
  primitives in `gbkt-genre-platformer` REMAIN — they're a scalable abstraction
  usable by future rectangle-based games; only the example disappears.
- Capture **SEED-PHASE-12-ONE-WAY-TILE** (D-13b) for `oneWayThreshold` /
  one-way tile encoding as a future extension of `tilemapCollision`. Phase 12
  ships solid-only.
- Edit Phase 13's requirements list (`/gsd-phase --edit 13`) for any
  framework-shaping DSL gaps surfaced during the port (e.g., per-level
  physics-config-table primitive if the per-level override pattern proves
  reusable across genres).

**Out of scope:**

- Anti-overfitting rails 1, 2, 3 (carried forward from Phase 9 / Phase 10 /
  Phase 11 unchanged — see D-overfitting-* below). **Note the tension:** the
  4 pre-budgeted named codegen surfaces and the per-level `platformerPhysics`
  overrides ARE new DSL surfaces added for this port. The user explicitly
  accepted this scope (D-01); each is justified as a scalable abstraction
  future games will reuse, NOT as "make this port pretty".
- ONE_WAY tile-type in tilemap collision — solid-only this phase; seeded for
  future port that surfaces a real need (D-13b).
- Vertical scroll codegen — horizontal-only matches the reference; vertical
  is Phase 13 territory IF a future port needs it (D-14b).
- Retiring or modifying the `platform()` / `goalZone()` genre-rectangle DSL
  surface — coexists with `tilemapCollision` as a scalable abstraction (D-13c).
- Manual-banking DSL surface — REQUIREMENTS.md explicit out-of-scope clause
  (carried forward from Phase 11 D-overfitting-inherited).
- Typed `Cartridge` enum — Phase 13 requirement #1; this phase uses magic-string
  `"MBC1"` (or `"ROM_ONLY"` if FFD verdict allows + tilemap data fits) per
  D-claude-3.
- Sub-pixel physics typed wrapper (`i16FixedVar`) — Phase 13 requirement #3;
  this phase uses `i16Var` + manual `shr 4` per Phase 9/11 carry-forward
  (D-overfitting-inherited).
- Pixel-and-frame parity with reference — UAT verifies INTEGRATION contract
  (the 5 anchors), not text/animation pixel-exact match.
- Console-mode `puts` / `printf` text output (reference uses none; this is
  a non-issue for platformer_template, but the inheritance note is preserved
  from Phase 11 for completeness).
- Pre-inserting Phase 12.1 placeholder before port surfaces surplus seeds
  (Phase 9/10/11 discipline — conditional on ≥1 surplus at port-close).
- A 6th UAT anchor (5 is the explicit one-time-this-phase cap — see D-09).
- Bank-count parity with reference (FFD nondeterminism per Phase 11 D-04
  corollary).

</domain>

<decisions>
## Implementation Decisions

### Anti-overfitting doctrine (inherited from Phase 9 / 10 / 11 — overarching guardrail)

- **D-overfitting-1 (inherited):** Do not add DSL features just to make THIS
  port pretty. **EXCEPTION acknowledged this phase:** the 4 named codegen
  surfaces and per-level `platformerPhysics` overrides ARE new DSL — each is
  justified as a scalable abstraction (tilemap-collision is the canonical way
  to express tilemap-based platformer collision; per-level physics override is
  a re-entrant builder pattern future genres will reuse; column-by-column
  scroll is the canonical way to render wide tilemaps; jumpHold is a standard
  platformer mechanic). Anti-overfitting still binds for ANYTHING ELSE
  surfaced during the port — those go to seeds or Phase 13 edit.
- **D-overfitting-2 (inherited):** Do not tune codegen visitors to this
  example's shape. The 4 named surfaces above are about adding NEW codegen
  paths, not tuning existing ones to match this example's literal output.
- **D-overfitting-3 (inherited):** Do not let the GBDK reference style become
  THE gbkt style. Reference's manual `BANKED` / `BANKREF` / `SWITCH_ROM` is
  GBDK C convention; gbkt's substrate is declarative.

### Phase shape (scope cap relaxation, bug discipline)

- **D-01: Lift the "ONE named codegen bug-fix" hard cap from Phase 9/10/11.**
  Phase 12 is an EXPLICIT multi-bug integration phase. The 4 pre-budgeted
  named codegen surfaces (D-12 .. D-15) are first-class scope; additional
  defects surfaced during port-construction are addressed inline up to a
  reasonable bound. Only orthogonal / blast-radius-wide defects route to
  seeds. **Justification:** the integration nature of the port surfaces
  multiple codegen gaps simultaneously, and addressing them one-per-phase
  (Phase 12 → Phase 12.1 → Phase 12.2 → ...) would generate the kind of
  sub-sub-phase cascade user memory `feedback_many_small_plans_terminal_subphase.md`
  forbids. **This cap-lift is THIS phase only** — future ports (none planned
  in v1.0) revert to one-bug discipline unless they justify expansion the
  same way.

### Port shape — substrate selection

- **D-02: Substrate = 3 levels faithful + banked tile-data title + banked
  NextLevel card.** Selected over (i) 1-level minimum, (ii) 2-level
  cross-level switch, and (iii) the "window-text title" idiomatic-gbkt option.
  Three levels exercise: (a) multi-zone bank allocation (3 maps + 2 tilesets
  + 2 menu screens = up to 7 banked data units), (b) level-switch with banked
  transition card, (c) cross-bank tilemap content swap. Banked title +
  NextLevel exercise the Zone-data-for-menu-graphics path. Most faithful
  integration substrate.
- **D-03: New subdirectory `gbkt-examples/platformer-template/`; existing
  `gbkt-examples/platformer/` is retired at Phase 12 close.** Reasoning:
  (a) the new port is the working playable platformer example going forward
  (user observation: existing example "doesn't really work"); (b) no dead
  code at close; (c) `platform()` / `goalZone()` DSL primitives in
  `gbkt-genre-platformer` REMAIN as a scalable abstraction (NOT deprecated,
  NOT deleted — just the example directory disappears). Both
  `tilemapCollision` and `platform()` coexist in the genre DSL as
  alternative collision mechanics — the user wants both surfaces available.
  Retirement happens in a dedicated late plan in Phase 12 (D-claude-2;
  planner decides exact timing — recommended last plan before phase-close).
- **D-04: Player metasprite = 6 frames + hflip-multiplier (Phase 10 codegen
  path).** Selected over (a) 12-frame faithful and (c) reduced 4-frame.
  Exercises Phase 10 + 10.1's `MoveMetasprite.flipX` codegen surface;
  half the asset bytes vs reference; honest authoring shape for GB. Frames:
  idle / walk1 / walk2 / walk3 / jump-up / jump-fall (right-facing); hflip
  produces the 6 left-facing equivalents at draw-time via Phase 10's
  generated descriptor switching.

### UAT contract floor (5 anchors — second one-time expansion beyond Phase 9/10's 3-cap)

- **D-08: Tight UAT — 5 anchor behaviors.** Lock:
  1. **Title → gameplay scene transition.** Press Start on title scene →
     banked title screen unloads → world1Area1 tilemap loads → player
     metasprite visible. Variable assertion: scene id + nextLevel
     transitions. Screenshot: gameplay scene rendered with tilemap + player.
  2. **Tilemap collision works (jump+land on solid).** Press A on grounded
     player → jump velocity sets → player rises → gravity overrides → player
     falls → 5-point bounding-box probe detects solid tile under feet →
     `y_velocity = 0` → grounded. Screenshot: player standing on a tile;
     screenshot: player mid-air mid-jump. Variable assertion: y_velocity
     transitions through 0 → -550 → 0 cycle. Locks D-12 contract.
  3. **Horizontal scroll works (camera moves, no repeat).** Hold right →
     player crosses half-screen threshold (80 px) → `_cam_x` increments →
     `move_bkg(_cam_x, 0)` fires → tile-boundary crossing triggers
     `set_bkg_submap(map_pos_x + DEVICE_SCREEN_WIDTH, 0, 1, ...)` → tilemap
     content visibly DIFFERS from initial frame (not repeating). Screenshot:
     scrolled position visibly different from initial. Locks D-13 contract.
  4. **Metasprite animation (multi-frame walking visible).** Hold right →
     `threeFrameCounter` cycles → metasprite frame index transitions through
     0..2 (walk cycle) → MetaspriteVisitor's frame-switch emission visible
     in OAM. Screenshot sequence (3 captures over ~6 frames apart) showing
     pose differences. Plus hflip: hold left → frames mirror (Phase 10 codegen
     path).
  5. **Level-switch works (gameplay → NextLevel card → gameplay level 2).**
     Player reaches level-end trigger → `nextLevel++` → banked NextLevel
     card displays → Start pressed → world1Area2 tilemap loads → player
     respawns. Screenshot: NextLevel card rendered; screenshot: level 2
     gameplay (visibly different tilemap from level 1).
- **D-09: 5-anchor cap is a SECOND ONE-TIME EXPANSION beyond Phase 9/10's
  3-anchor and Phase 11's 4-anchor.** Justification: the integration contract
  has 5 distinct surfaces (scene-transition, tile-collision, scroll, animation,
  level-switch). Folding any two reduces honesty. **Future ports MUST justify
  any anchor-count expansion the same way — this is NOT a precedent for ≥5
  anchors.** Phase 11 D-09 said "Phase 12 is NOT pre-licensed to ≥4 anchors";
  user explicitly accepted the 5-anchor expansion here knowing that
  commitment. If Phase 12.1 surfaces, it inherits AT MOST 5 anchors, NOT a
  6-anchor expansion.
- **D-10: MCP play-through + screenshot for all 5 anchors.** Every one of
  the 5 anchors has a visible truth ("scene rendered", "tilemap rendered",
  "player animated", "scroll progressed", "level switched") and MUST follow
  the CLAUDE.md visual-evidence rule — screenshots are binding. Variable
  assertions PAIR with screenshots; codegen GREEN is NOT sufficient evidence
  (per user memory `feedback_visual_evidence_for_visual_truths.md`).
  Each anchor gets a JVM-tier emission invariant per D-16.
- **D-11: UAT first — `12-UAT.md` + `PLAYBOOK.md` BEFORE any DSL.** Plan 1
  of the phase = lock UAT contract with no DSL yet. Mirrors Phase 9 D-03 /
  Phase 10 D-03 / Phase 11 D-11.

### Pre-budgeted named codegen surfaces

- **D-12: Tilemap-collision primitive on `platformerPhysics` with per-level
  overrides.** New DSL:
  ```kotlin
  platformerPhysics {
      gravity(2); jumpForce(8); terminalVelocity(12); jumpHold(20)  // game-level defaults
      solidThreshold(17)  // game-level default
  }
  zone("world2_area1") {
      tileset(asset("res/world2-tileset.png"))
      tiles(asset("res/world2-area1.png"))
      platformerPhysics {  // per-level override
          gravity(3)        // heavier in world 2
          solidThreshold(68)
      }
  }
  ```
  Per-level overrides shadow specific fields; missing fields inherit from
  game-level defaults. Lowers to a level-keyed C config table swapped at
  `SetupCurrentLevel()` boundary.
- **D-12a: `IsTileSolid()` helper codegen — HOME bank, NONBANKED, with
  SWITCH_ROM wrapper (Phase 07.4-30 pattern).** Generated helper sits in
  `main.c`; reads the active level's tilemap by `SWITCH_ROM(_current_area_bank)`
  then restores via `SWITCH_ROM(_previous_bank)`. Tier-1 JVM invariant 2's
  per-function awk brace-walk grep targets this exact shape.
- **D-12b: 5-point bounding-box probe auto-derived from actor hitbox.** When
  a scene uses tilemap-collision and an actor has a hitbox, the codegen emits
  a 5-point sample (top-half-left, top-half-right, mid-left, mid-right,
  bottom-half-left/right — exact pattern derived from reference's player.c
  lines 158-184). User authors `hitbox(0, 0, 8, 24)`; framework derives probe
  points. Hidden from user DSL.
- **D-13: Column-by-column horizontal scroll codegen** inside
  `PlatformerVisitor.buildCameraUpdateFunction()`. After the existing
  `move_bkg(_cam_x, _cam_y)` call, emit:
  ```c
  // Generated by Phase 12 codegen extension
  _map_pos_x = (uint8_t)(_cam_x >> 3);
  if (_map_pos_x != _old_map_pos_x) {
      if (_cam_x < _old_cam_x) {
          set_bkg_submap(_map_pos_x + 1, 0, 1, DEVICE_SCREEN_HEIGHT, ...);
      } else if ((_current_level_width_in_tiles - DEVICE_SCREEN_WIDTH) > _map_pos_x) {
          set_bkg_submap(_map_pos_x + DEVICE_SCREEN_WIDTH, 0, 1, DEVICE_SCREEN_HEIGHT, ...);
      }
      _old_map_pos_x = _map_pos_x;
  }
  _old_cam_x = _cam_x;
  ```
  Gated on `platformerCamera { horizontal() } + smoothFollow()` (existing DSL
  hooks; no new user-facing DSL for the trigger). Threshold for "camera starts
  following" is derived from `smoothFollow` + `deadZone` semantics.
- **D-13b: ONE_WAY tile-type seed deferred.** Phase 12 ships solid-only
  (`solidThreshold(N)`: tile < N is solid). `SEED-PHASE-12-ONE-WAY-TILE`
  captures the future extension (`oneWayThreshold(M)`: N ≤ tile < M is
  one-way, tile ≥ M is passable). Seeded at phase-close via
  `/gsd-capture --seed`.
- **D-13c: `tilemapCollision` and `platform()` rectangles coexist as
  scalable abstractions.** No deprecation of either. Games choose based on
  level geometry. Phase 12 substrate uses only tilemap-collision; existing
  `gbkt-examples/platformer/` (retired in this phase) used only `platform()`.
- **D-14: Variable-height jump — `platformerPhysics.jumpHold(maxFrames)`.**
  New field on `PlatformerPhysicsConfig` + lowering. While the jump button
  (A/Up) is held AND `_jump_increase_timer > 0`, gravity is suppressed; on
  release OR timer expiry, normal gravity resumes. Reference uses 20-frame
  max (`PLAYER_CHARACTER_INCREASE_JUMP_TIMER_MAX`). Phase 12 substrate uses
  `jumpHold(20)`. Lowers inside the existing `buildPhysicsUpdateFunction()`
  in `PlatformerVisitor`.
- **D-14b: Vertical-scroll codegen NOT in scope.** Reference is horizontal-only;
  Phase 12 substrate matches; vertical-scroll codegen is Phase 13 candidate IF
  a future port surfaces it. No seed (deferred via roadmap, not seed file).
- **D-15: Multi-tileset asset pipeline + multi-zone bank allocation
  verification + extension.** Pre-budgeted as a candidate named codegen
  surface. Researcher confirms whether the existing asset pipeline +
  `allocateZoneBanks` cleanly supports N distinct tilesets across M zones
  with tilemaps referencing tilesets by ID. If "just works" → plan collapses
  into an asset-pipeline-verification plan (no codegen work). If gaps exist
  → plan extends the pipeline. Either way, the slot is allocated so the
  plan budget covers it.

### Tier-1 JVM emission invariants

- **D-16: 5 JVM-tier emission invariants — one per UAT anchor.** Each
  asserts the generated C contains the right shape (per-function awk
  brace-walk before grep, per CLAUDE.md scope-level grep gates corollary):
  1. **Scene transition + level setup shape**: `main.c` contains
     `navigate_to_scene(N)` from title to gameplay; gameplay-scene `enter`
     emits `setup_current_level()` call which lives in HOME / NONBANKED
     and calls `set_native_tile_data` + `set_bkg_submap` for the active
     level's first screen.
  2. **`is_tile_solid()` helper shape**: HOME bank, NONBANKED, body contains
     `SWITCH_ROM(_current_area_bank); ... SWITCH_ROM(_previous_bank);`
     wrapping a `_current_level_map[index] < _current_level_non_solid_tile_count`
     check (or equivalent). Per-function awk brace-walk grep.
  3. **Scroll update shape**: `_camera_update()` body contains
     `set_bkg_submap(_map_pos_x + 1, ...)` AND
     `set_bkg_submap(_map_pos_x + DEVICE_SCREEN_WIDTH, ...)`
     inside a `if (_map_pos_x != _old_map_pos_x)` guard. Per-function
     awk brace-walk grep.
  4. **Metasprite frame-switch shape**: Player actor's frame field is read
     in `play_frame`, switches between metasprite descriptor pointers
     `sprite_player_frame_0` .. `sprite_player_frame_5` via Phase 10's
     codegen path; hflip path emits FLIPX bit via `MoveMetasprite.flipX`
     (Plan 10.1-04 emission site).
  5. **Level-switch shape**: `main_loop` body contains
     `if (nextLevel != currentLevel) { ... show_centered_NextLevel(...);
     wait_for_start(); setup_current_level(); }` block; banked NextLevel
     card data lives in a non-zero bank per FFD verdict.

### Plan sizing — multi-bug integration phase

- **D-18: Floor ≥22 plans; planner picks ceiling (expected 25-32).** Plan
  count floor inherits Phase 10 D-14 / Phase 11 D-18's ≥12 baseline and
  scales up for the wider integration scope. Mapping:
  - 1 plan: UAT lock (D-11)
  - 1 plan: reference ROM build + evidence/reference/ artifact (D-17a)
  - 1 plan: project scaffold (gbkt-examples/platformer-template/ + build.gradle.kts + settings.gradle.kts)
  - 1 plan: asset import (6-frame metasprite, 2 tilesets, 3 tilemaps, title screen, NextLevel card via png2asset-equivalent gbkt path)
  - 4+ plans: tilemap-collision DSL + IR + visitor + JVM-tier invariant (D-12)
  - 3+ plans: horizontal scroll codegen + JVM-tier invariant (D-13)
  - 2+ plans: variable-height jump primitive + JVM-tier invariant (D-14)
  - 1-3 plans: multi-tileset asset pipeline verification + extension (D-15)
  - 3+ plans: 3-level substrate wiring + level-switch + banked NextLevel card
  - 5 plans: 5 UAT anchors evidence capture (D-08, one plan each)
  - 1 plan: 3-signal artifact + bank-layout check
  - 1 plan: retire `gbkt-examples/platformer/` from settings.gradle.kts (D-03)
  - 1 plan: phase close (surplus seeds via /gsd-capture --seed, conditional Phase 12.1, Phase 13 edits, SEED-PHASE-12-ONE-WAY-TILE creation)
  Sums to ~22-30 plans. Planner refines after research. **Plan-checker MUST
  flag any plan count under 22 as a sizing concern given the multi-bug
  scope** — split a plan, never lower the floor.
  - ≤2 distinct concerns per plan; "and also" twice → split (carried forward
    from Phase 10 D-14).
- **D-19: Phase 12.1 (if it surfaces) MUST be terminal.** User memory
  `feedback_many_small_plans_terminal_subphase.md` explicit constraint.
  No Phase 12.1.1 / 12.2. Planner sizes Phase 12.1's plans small enough
  that any in-execution surplus discovery is ABSORBED (split a plan,
  insert a plan in a wave), NOT escalated to a new sub-sub-phase.

### Three-signal artifact + bank-layout signal

- **D-17: Three artifacts + bank-layout signal (Phase 11 D-15 carry-forward).**
  1. **ROM size**: `gbkt.gb` byte size vs reference `platformer_template.gb`
     (target: within 2×, per ROADMAP three-signal contract).
  2. **Generated-C diff**: gbkt's `main.c` + `bank1.c` + `zone_bank*.c` (etc.)
     vs reference's 5-file C tree. Side-by-side; gbkt's declarative shape
     vs reference's manual-banking+`SWITCH_ROM`+`BANKED` shape. Where gbkt
     is NOT shorter/clearer → seed.
  3. **UAT verdict**: per-anchor verdict (5 GREEN with screenshots + paired
     variable assertions).
  4. **Bank-layout signal**: built `.noi` file's every `DEF l__CODE_<N>` byte
     size is ≤ 16384 (hard ROM-bank capacity threshold per Phase 11 D-15).
     Plus the implicit signal in UAT anchor 1 + 5: cross-bank navigation
     and cross-bank tilemap loads resolve without "MBC5 unknown address/value"
     errors.
- **D-17a: Artifacts location — `.planning/phases/12-.../evidence/reference/` +
  `.../evidence/oracle-comparison.md` + `.../evidence/uat-screenshots/`.**
  Same Phase 9 / 10 / 11 layout. Reference `.gb`/`.map`/`.noi` binaries stay
  local (gitignored — reproducible from `evidence/reference/BUILD.md`).

### Phase 13 routing

- **D-20: Keep Phase 12 scoped to D-01..D-19. Framework-shaping DSL gaps
  surfaced AFTER the port works → Phase 13 via `/gsd-phase --edit 13`.**
  Specifically:
  - Typed `Cartridge` enum (Phase 13 requirement #1 — Phase 12 uses
    `"MBC1"` magic string per D-claude-3, NOT add the enum).
  - Fixed-point sub-pixel abstraction (`i16FixedVar`, Phase 13 requirement
    #3 — Phase 12 uses `i16Var` + manual `shr 4`).
  - Per-genre per-level config-table primitive — IF the per-level
    `platformerPhysics` override pattern (D-12) proves reusable across other
    genres (e.g. RPG per-floor config). Currently isolated to platformer.
  - Vertical-scroll codegen — D-14b; future port territory.
  - ONE_WAY-tile encoding (`oneWayThreshold(M)`) — `SEED-PHASE-12-ONE-WAY-TILE`
    captures it; Phase 13 IFF future port surfaces the need.

### ROM-build smoke test (memory rule, inherited)

- **D-21: Verifier MUST run a clean `:gbkt-examples:platformer-template:buildRom`
  AND the reference `make` (or its `evidence/reference/BUILD.md`-documented
  equivalent) before declaring the phase complete.** Per user memory
  `feedback_rom_build_smoke_test_for_codegen_phases.md` — codegen phases
  touching `GBDKPipelineV2` / `BankingAnalysisPass` / `GenerateCTask` / visitor
  surface MUST include this gate. Phase 12 touches all of these.

### Claude's Discretion

- **D-claude-1: Exact level / scene names + minimum-viable substrate.**
  Reference uses `World1Area1` / `World1Area2` / `World2Area1` — planner
  picks Kotlin-idiomatic names (e.g. `world1Area1Zone`, `world1Area2Zone`,
  `world2Area1Zone`). Plus scene names: `titleScene` / `gameplayScene` /
  `nextLevelScene`.
- **D-claude-2: Exact timing for `gbkt-examples/platformer/` retirement.**
  Recommended: last plan before phase close. Could also be earlier (e.g.
  Plan 3 alongside scaffolding) if Gradle settings interplay requires.
- **D-claude-3: Cartridge config — `"ROM_ONLY"` or `"MBC1"` magic string.**
  FFD verdict + ROM size determines. Reference uses minimal `0x01` (MBC1
  WITHOUT RAM). Phase 12 expects ≥3 ROM banks (HOME + bank1 scenes + zone
  banks 2..6 for 3 tilemaps + 2 tilesets + title + NextLevel card) → likely
  `"MBC1"`. Planner sets per FFD output. Typed Cartridge enum is Phase 13
  per D-20.
- **D-claude-4: GBC vs DMG target.** Reference detects `_cpu == CGB_TYPE`
  and loads CGB palettes accordingly. Recommended Phase 12 target:
  `gbcTarget = GBC_COMPATIBLE` (the default; runs on both, palette load is
  conditional on CGB_TYPE). Substrate doesn't need CGB-only features.
- **D-claude-5: Joypad edge-detection parity.** Reference uses
  `joypadCurrent & J_A && !(joypadPrevious & J_A)`. gbkt has
  `buttons.a.pressed` which is edge-triggered (existing DSL). Planner
  verifies the emission matches reference's intent (rising-edge only,
  NOT continuous-held).
- **D-claude-6: Level-end trigger DSL.** Reference uses
  `if (playerRealX > currentLevelWidth - 32) { nextLevel++ }`. gbkt has
  `goalZone()` (rectangle-based). For tilemap-collision substrate, planner
  may use:
  - `goalZone()` rectangle at level's right edge (simplest, existing DSL), OR
  - level-end DSL inferred from zone width (`whenever(player.x isAtLeast
    currentZone.width - 32) { advanceLevel() }`).
  Planner picks based on existing DSL surface; reuses `goalZone` if it
  cleanly composes with tilemap-collision.
- **D-claude-7: Asset-pipeline tilesets — assets from reference's
  `res/graphics/` or custom-authored.** Recommended: import reference's
  PNG assets verbatim (`title-screen.png`, `world1-tileset.png`,
  `world2-tileset.png`, `world1-area1.png`, `world1-area2.png`,
  `world2-area1.png`, `next-level.png`, `player-character-gbapduck-sprites.png`).
  Smaller authoring burden; faithful to reference. Copyright/licensing:
  reference is GBDK-2020 example, MPL/dual-licensed — reuse with
  attribution in `gbkt-examples/platformer-template/res/README.md`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Reference port source (external — THIS IS THE ORACLE)

- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/main.c`
  (~93 lines) — main loop, title-screen wait, level-transition card, joypad
  poll, UpdatePlayer + UpdateCamera tick. **Read before writing any DSL.**
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c`
  (~355 lines) — sub-pixel physics (`>>4`), variable-height jump
  (`playerJumpIncrease`), gravity, ground friction, multi-frame metasprite
  animation (`threeFrameCounter`), 5-point bounding-box collision (lines
  158-200), facing-flip via frame-offset, half-screen camera trigger
  (line 261). **Source of truth for D-12, D-13, D-14 codegen shapes.**
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/level.c`
  (~153 lines) — `IsTileSolid()` NONBANKED helper with SWITCH_ROM pattern
  (lines 41-69), `SetupCurrentLevel()` switch-on-level-index with banked
  tileset/tilemap swap (lines 72-150). Source of truth for D-12a + D-15 +
  D-16 invariant 2.
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/camera.c`
  (~83 lines) — `SetCurrentLevelSubmap()` (lines 31-41),
  `UpdateCamera()` column-update logic (lines 56-83). Source of truth for
  D-13 codegen shape + D-16 invariant 3.
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/common.c`
  (~66 lines) — `WaitForStartOrA()` (joypad edge-detection),
  `setBKGPalettes()` (DMG/CGB conditional), `ShowCentered()` (banked
  tile-data screen rendering for title + NextLevel cards). Source of truth
  for D-claude-5 + the banked-title-card codegen shape.
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/Makefile`,
  `Makefile.targets` — reference build invocation with `png2asset` flags
  (`-noflip -map -tileset_only -keep_duplicate_tiles` per Readme.md), `bo<N>`
  filename hints for bank assignment, `-Wl-yt0x01` (MBC1). Needed for
  reproducible reference ROM build (D-17a).
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/Readme.md`
  — high-level structure, level-authoring conventions ("solid tiles first
  in tileset"), png2asset usage. Read to understand the reference's
  authoring intent before deciding what gbkt DSL surface to mirror vs
  diverge.

### Roadmap & doctrine

- `.planning/ROADMAP.md` §"Phase 12: Port platformer_template GBDK example
  to gbkt" (lines 1341-1353) — three-signal contract + hard scope cap
  (ONE example, ONE named codegen bug-fix — **LIFTED THIS PHASE per D-01**).
  Surplus → seeds. ROADMAP explicitly notes: "If the existing gbkt platformer
  example is functionally equivalent to this port, retire it in favor of the
  reference port" — D-03 chose to retire even though the existing example is
  NOT functionally equivalent (genre-rectangle vs tilemap-collision).
- `.planning/ROADMAP.md` §"Phase 13: framework primitives surfaced by example
  ports (rolling)" (lines 1355-1380) — downstream collector for framework-shaping
  gaps. Typed `Cartridge` enum + `i16FixedVar` already listed; Phase 12 may
  add per-level-physics-table primitive + vertical-scroll if surfaced.
- `.planning/ROADMAP.md` §"Phase 10.1: Metasprites surplus codegen defects"
  (lines 1276-1325) — CR-01 (VRAM tile-slot collision: actors + metasprites
  coexist), CR-03 (descriptor namespacing), WR-05 (hiwater frame-scope hoist)
  ALL CLOSED 2026-05-19. Phase 12 inherits clean state; **no need to re-verify
  these** unless a related regression surfaces during port construction.
- `.planning/REQUIREMENTS.md` §"Out of Scope" — "Manual banking DSL syntax |
  Defeats the core value proposition". Locks Phase 12 substrate against any
  manual-banking authoring shape (carried forward from Phase 11 D-overfitting).
- `.planning/PROJECT.md` §"North Star" + §"Core Principles" + §"Complexity
  Ceiling" — Super Mario Land called out explicitly as a complexity ceiling
  target. Phase 12 is the integration check that gbkt can produce a
  Mario-scale platformer.
- `.planning/STATE.md` (head, lines 1-46) — Phase 10.2 EXECUTING (Wave 3
  pending after MCP server rebuild); Phases 10 + 10.1 SHIPPED. Phase 11
  context gathered (ready for plan-phase). Phase 12 begins parallel to 10.2.

### Phase 9 / 10 / 10.1 / 11 deliverables Phase 12 inherits

- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-CONTEXT.md`
  — anti-overfitting doctrine D-overfitting-1/2/3 (carried forward unchanged),
  UAT-first sequencing (D-11 mirror), three-signal artifact + evidence/reference/
  layout, surplus-seed + conditional placeholder discipline.
  **Required reading for the planner.**
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-CONTEXT.md`
  — plan-sizing D-14 (≥12 plans, ≤2 concerns per plan, planner must err
  small); Tier-1 JVM emission invariants D-12 substrate; per-function awk
  brace-walk before grep. **Required reading for the planner.**
- `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-CONTEXT.md`
  — terminal-subphase policy D-03b (Phase 12.1, if it surfaces, MUST be
  terminal); file-affinity grouping; CR-01/CR-03/WR-05 fixes shipped
  (Phase 12 inherits VRAM allocator + descriptor namespacing + frame-scope
  hiwater hoist as a clean baseline).
- `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md`
  — Phase 11 D-09 anchor-count discipline ("Phase 12 NOT pre-licensed to
  ≥4 anchors" — Phase 12 explicitly accepted 5 per D-09 above);
  three-signal + bank-layout signal layout (D-15 mirror); Phase 07.4-30
  HOME-bank SWITCH_ROM wrapper pattern (Phase 12 D-12a inherits this for
  `is_tile_solid()` codegen); BANKED calling convention validated.
  **Required reading for the planner.**
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/` — the Phase
  that originally surfaced bank-overflow + BANKED-trampoline +
  SWITCH_ROM-from-banked-context problems. Plan 07.4-22 (cross-bank
  `set_bkg_tiles` guard) and Plan 07.4-30 (HOME-bank SWITCH_ROM wrapper)
  are the existing codegen surface Phase 12 extends in D-12a + D-13.
- `.planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/07.9-CONTEXT.md`
  — literal-emission convention (relevant for sub-pixel velocity
  literals + tile-threshold integer emission).

### Verification methodology

- `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule" — drives
  D-10 (all 5 anchors are visible truths → screenshots binding).
- `CLAUDE.md` §"Scope-level grep gates (corollary)" — drives D-16 (per-function
  awk brace-walk before grep for emission invariants).
- `CLAUDE.md` §"GBDK Setup & ROM Building" + §"Common Errors" — drives D-21
  (ROM-build smoke test gate).
- `CLAUDE.md` §"Banking Defaults" — `BankingConfig` defaults; Phase 12 expects
  FFD to land scenes in bank 1 + zone data across banks 2+.
- `CLAUDE.md` §"Window-Layer UI" — NOTE: Phase 12 uses banked tile-data title
  screens (D-02), NOT window-text. The reference's title-screen mechanism is
  EXPLICIT scope; window-layer UI rule applies to in-game HUD text only
  (Phase 12 substrate has none).
- `context/TESTING.md` — JVM-tier test recipes, GbktTestExtension API, MCP
  tools reference (drives all 5 anchor implementations).
- `context/UAT_GUIDE.md` — MCP agent tool playbook (drives all 5 anchor
  scripted-input implementations).
- User memory `feedback_rom_build_smoke_test_for_codegen_phases.md` — D-21.
- User memory `feedback_many_small_plans_terminal_subphase.md` — D-18, D-19.
- User memory `feedback_visual_evidence_for_visual_truths.md` — D-10.
- User memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`
  — if any of the 4 pre-budgeted named codegen surfaces has blast-radius
  across multiple visitors / IR nodes wider than Phase 12 can absorb,
  ESCALATE to a proper new phase rather than driving inline. (Phase 12's
  cap-lift is bounded by this memory rule — not a license for unbounded
  scope.)

### gbkt module surfaces this port will exercise (or extend)

- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerBuilders.kt`
  — `PlatformerPhysicsBuilder` (extended in D-12 with `solidThreshold`,
  D-14 with `jumpHold`); `WallJumpConfigBuilder` (not exercised by Phase 12).
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt`
  — `platformerPhysics` / `platformerCamera` / `platform` / `goalZone` /
  `hazard` / `ladder` extensions; Phase 12 may add `tilemapCollision` here
  if it's a separate top-level builder vs nested inside `platformerPhysics`.
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
  — `buildPhysicsUpdateFunction` (line 183, extended in D-14 for `jumpHold`);
  `buildCameraUpdateFunction` (line 500+, extended in D-13 for
  `set_bkg_submap` column updates); new method `buildIsTileSolidFunction`
  added in D-12a.
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/domain/PlatformerTypes.kt`
  — `PlatformerPhysicsConfig` (extended with `solidThreshold` field +
  `jumpHold` field); `PlatformerCameraConfig` (no new fields; existing
  `smoothFollow` + `horizontal` + `deadZone` are sufficient triggers).
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  — `buildSceneFile`, `allocateZoneBanks`, `buildTilemapBankFiles`, the
  Plan 07.4-30 `bg_load_zone_tiles` SWITCH_ROM wrapper. Phase 12's D-12a
  + D-15 extensions ride on these existing surfaces.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt`
  — frame-switch emission (Plan 10.1-04); hflip via `MoveMetasprite.flipX`
  (Plan 10.1-05 namespacing fix). Phase 12 D-04 reuses verbatim.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt` —
  `ZoneBuilder` (`tileset`, `tiles`, `collision`); Phase 12 substrate
  adds 3 zones + per-zone `platformerPhysics` override block via D-12.
  Investigate whether `ZoneBuilder` needs the re-entrant
  `platformerPhysics { }` block surface or whether the per-level override
  is a `PlatformerPhysicsBuilder.level(zone) { }` shape — planner picks
  (D-claude detail).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` —
  scene navigation via existing `scene { enter { } frame { } exit { } }`
  shape. Phase 12 uses verbatim.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InputBuilders.kt` —
  `buttons.a.pressed`, `buttons.start.pressed`, `dpad.left.held`,
  `dpad.right.held`, `dpad.up.pressed` (verify edge-trigger emission
  parity per D-claude-5).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt`
  `ConfigBuilder` (line 525+) — `cartridge: String`, `romBanks: Int`,
  `gbcTarget: GbcTarget`. Phase 12 sets `cartridge = "MBC1"` per D-claude-3.
- `gbkt-gradle-plugin/` — build pipeline, asset import (png2asset
  equivalent for gbkt's asset pipeline); D-15 verifies multi-tileset
  story rides on this without extension or surfaces a gap.

### Example references in gbkt-examples

- `gbkt-examples/dungeon/` — existing multi-bank gbkt example (3 banks:
  HOME + bank1 + bank2 zone tilemap). Closest existing architecture to
  Phase 12 substrate (smaller scope, single tileset).
- `gbkt-examples/metasprites/` — Phase 10's finished metasprite port;
  reference for the metasprite + asset-pipeline + descriptor-namespacing
  story (Plan 10.1-05). Phase 12 player metasprite follows this shape.
- `gbkt-examples/metasprites-stress/` — Phase 10.1-12 synthetic stress
  test (1 actor + 2 metasprites + 2 scenes) — closest existing example
  for the actor+metasprite-coexist VRAM allocation story (CR-01 fixed).
  Phase 12 has 1 actor (the player metasprite) — no actor+metasprite mix,
  but Phase 12 will be the first example to exercise actor-as-metasprite
  at this scale.
- `gbkt-examples/platformer/` — to be RETIRED at Phase 12 close (D-03).
  185-LoC genre-rectangle showcase; doesn't compose with tilemap collision.
- `gbkt-examples/platformer-gbc/` — sibling existing example; may inform
  GBC-target details (D-claude-4) even though Phase 12 targets
  GBC_COMPATIBLE not GBC-only.
- `gbkt-examples/CLAUDE.md` — "Adding a New Example" 5-step recipe.
  `gbkt-examples/platformer-template/` follows it.

### Project-level

- `CLAUDE.md` (root) — verification methodology, BANKED calling convention,
  banking defaults, scope-level grep gates, Window-Layer UI clarification
  (banked tile screens for Phase 12 title/level-card are NOT a window-layer
  violation — they're full-screen background tilemap swaps, distinct surface).
- `.planning/PROJECT.md` — north star (complexity ceiling: Super Mario Land
  is the platformer target); declarative-over-imperative as hard constraint
  framing the per-level override DSL (D-12).
- User memory `feedback_no_magic_strings.md` — Phase 12 DSL must reflect
  names from property delegates / lambda params (e.g. zone names derived
  from `val world1Area1 by zone { }`); applies to the new `tilemapCollision`
  / `jumpHold` / per-level override surfaces.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`gbkt-examples/dungeon/`** — multi-bank reference (HOME + bank1 + bank2
  zone tilemap). Phase 12 substrate is similar shape but larger (3 zones +
  2 tilesets + 2 menu screens = up to 7 banked data units). Same
  `allocateZoneBanks` + ZoneIR architecture.
- **`gbkt-examples/metasprites-stress/`** — Phase 10.1-12 closest existing
  actor+metasprite stress test; Phase 12 single-metasprite player rides the
  same VRAM allocator path (CR-01 fix).
- **`gbkt-genre-platformer/.../PlatformerBuilders.kt`** —
  `PlatformerPhysicsBuilder` with `gravity`, `jumpForce`, `terminalVelocity`,
  `coyoteTime`, `jumpBuffer`, `wallJump`. Phase 12 ADDS: `solidThreshold(N)`,
  `jumpHold(maxFrames)`, optional per-level override via re-entrant block
  in `zone {}`.
- **`gbkt-genre-platformer/.../PlatformerVisitor.kt`** —
  `buildPhysicsUpdateFunction` (line 183) extended for jumpHold (D-14);
  `buildCameraUpdateFunction` (line 500+) extended for column-update scroll
  (D-13). New helper `buildIsTileSolidFunction` for D-12a.
- **`gbkt-backend-gbdk/.../GBDKPipelineV2.kt`** —
  `allocateZoneBanks` (line 573+) verified for N-zone scenario in D-15;
  Plan 07.4-30 `bg_load_zone_tiles` HOME-bank SWITCH_ROM wrapper (line
  1882+) is the reuse pattern for D-12a `is_tile_solid()` helper.
- **`gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt`** — frame-switch
  emission (Plan 10.1-04) + hflip via `MoveMetasprite.flipX` (Plan 10.1-05)
  ALL the metasprite infrastructure D-04 needs.
- **`gbkt-lang/.../SystemBuilders.kt:ConfigBuilder`** — `cartridge`,
  `romBanks`, `ramBanks`, `gbcTarget`. Phase 12 sets `cartridge = "MBC1"`
  (or `"ROM_ONLY"` if FFD permits) + `gbcTarget = GBC_COMPATIBLE`.
- **Phase 11 D-12 invariant 2 (cross-bank zone tilemap load wrapper)** —
  validates Plan 07.4-30 surface; Phase 12 D-12a builds on the same wrapper
  shape for `is_tile_solid()`.
- **png2asset-equivalent gbkt asset pipeline** — investigate during research
  (D-15) whether the existing pipeline cleanly handles 2 distinct tilesets +
  3 tilemaps + 2 menu screens (title + NextLevel card) all in one game.

### Established Patterns

- **Anti-overfitting D-overfitting-1/2/3** (inherited; D-01 cap-lift bounded
  by `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`).
- **UAT-first sequencing** (Phase 9 D-03 / Phase 10 D-03 / Phase 11 D-11) —
  Plan 1 is UAT lock, no DSL yet (D-11).
- **Three-signal artifact + bank-layout signal** (Phase 11 D-15 carry).
- **Tier-1 JVM emission invariants** (Phase 9 D-11 / Phase 10 D-12 /
  Phase 11 D-12) — one per UAT anchor; per-function awk brace-walk before
  grep. Phase 12 D-16 expands to 5 invariants (one per anchor).
- **Visual-evidence rule** (CLAUDE.md) — drives D-10 (all 5 anchors visual).
- **ROM-build smoke test in verifier** (user memory rule, D-21).
- **Terminal-subphase rule** (`feedback_many_small_plans_terminal_subphase.md`)
  — Phase 12.1 if surfaces is TERMINAL.
- **Coexistence of `platform()` + `tilemapCollision`** (D-13c) — both surfaces
  remain in genre-platformer DSL; user explicitly accepted as "scalable
  abstractions".

### Integration Points

- **GBDK toolchain** — D-17a requires building the reference ROM via the
  GBDK Makefile to produce comparison artifacts
  (`platformer_template.gb`, `platformer_template.map`, `platformer_template.noi`).
  Local-only (binaries gitignored), reproducible from
  `evidence/reference/BUILD.md`. Same pattern as Phase 9 / 10 / 11.
- **MCP `gbkt-emulator`** — all 5 anchors use `emulator_press`,
  `emulator_step`, `emulator_read_variable`, `emulator_screenshot`. Plus
  `emulator_wait_for_scene` for anchor 1 + 5 scene transitions. All 17 base
  tools + Phase 10.2's `emulator_read_memory` + `emulator_write_memory`
  available (relevant if anchor 2 / 3 needs raw-memory inspection of
  `_current_level_map` or `_cam_x` / `_old_cam_x` for diagnostic).
- **`.planning/seeds/`** — D-13b writes `SEED-PHASE-12-ONE-WAY-TILE.md` at
  phase close. Surplus codegen seeds (if D-01 lifted cap nonetheless leaves
  orthogonal residue) also go here.
- **`/gsd-phase --edit 13`** — D-20 routing for framework-shaping DSL gaps
  (per-level-physics-table, vertical scroll, ONE_WAY-tile).
- **`gbkt-build.properties`** — pipeline-emitted properties file consumed by
  `CompileRomTask`. Phase 12 verifies `mbc=MBC1` (or `ROM_ONLY`) propagation;
  same surface Phase 11 D-12 invariant 3 covers.
- **`gbkt-examples/settings.gradle.kts`** — D-03 retires
  `gbkt-examples/platformer/` entry here at phase close.

</code_context>

<specifics>
## Specific Ideas

- **"Current example doesn't really work. I need an example that works, but I
  want the DSL to scale. I don't want dead code but I also want modern DSL
  with the right abstractions."** — verbatim user framing of D-03's
  retirement decision. The reference port is BOTH a working example AND a
  DSL evolution. Future genre extensions should follow the
  "scalable-abstraction" criterion: don't add a DSL surface unless it serves
  this port AND future games of the same kind.
- **Per-level platformer-physics overrides as a re-entrant builder pattern.**
  The user reframed the original tile-collision question into "expand
  platformer-physics so that it works per level as well". This is a DSL
  evolution beyond just tile-collision: per-level overrides for gravity,
  jumpForce, terminalVelocity, etc. — anything in `platformerPhysics`
  becomes overridable per-zone. Phase 12 substrate uses the override path
  for `gravity` (world 2 heavier) + `solidThreshold` (world 2 has 68 solid
  tiles vs world 1's 17). If the planner finds the override path useful for
  the entire `PlatformerPhysicsConfig`, generalize accordingly.
- **5 anchors is a second one-time expansion.** Phase 11 was the first
  (4 anchors, justified by 4-distinct-surfaces); Phase 12 is the second
  (5 anchors, justified by 5-distinct-integration-surfaces). The user
  explicitly chose 5 over 3 / 4 / "you decide". **Future ports MUST justify
  expansions the same way; this is NOT a stepping stone to 6+ anchors.**
- **Both `platform()` and `tilemapCollision` remain in genre-platformer.**
  The user explicitly rejected deprecating `platform()` rectangles. Both
  surfaces are scalable abstractions for different kinds of platformer level
  geometry (rectangle-based vs tilemap-based). No D-platform()-deprecation
  decision exists; planner does NOT mark `platform()` deprecated in any
  doc/comment.
- **Hflip-based 6-frame metasprite (D-04)** — user picked this OVER 12-frame
  faithful AND 4-frame reduced. Demonstrates the user values exercising
  Phase 10's hflip codegen path more than literal asset-count faithfulness.
- **Lifted bug cap (D-01)** — user explicitly accepted "lift the cap; explicit
  multi-bug integration phase" over "first bug = named, rest = seeds" and
  "pre-budget 2-3 named slots". The phase is the integration check; the
  user wants honest scope.
- **Banked tile-data title + NextLevel card (D-02)** — user picked OVER
  window-text-idiomatic AND the hybrid option. The reference's full
  banked-graphics title screen is in scope; this exercises the
  zone-data-as-menu-graphics codegen path (potentially novel for
  non-RPG/non-exploration genres).

</specifics>

<deferred>
## Deferred Ideas

- **ONE_WAY tile-type encoding** (`oneWayThreshold(M)` as a secondary
  threshold on `solidThreshold`). Phase 12 ships solid-only; a seed file
  `SEED-PHASE-12-ONE-WAY-TILE.md` will be created at phase close via
  `/gsd-capture --seed`. Future port that surfaces a real need triggers
  the Phase 13 edit OR a new phase entirely (per blast-radius rule). Not
  in Phase 12 scope.
- **Vertical scroll codegen** (`set_bkg_submap` row updates in addition to
  column updates). Reference is horizontal-only; Phase 12 substrate
  matches. Vertical scroll is Phase 13 candidate IF a future port surfaces
  it. NOT seeded (low-frequency need; routed via roadmap).
- **Typed `Cartridge` enum** — already Phase 13 requirement #1. Phase 12
  uses magic-string `"MBC1"` per D-claude-3. Enum lands in Phase 13.
- **Fixed-point sub-pixel typed wrapper (`i16FixedVar`)** — already Phase
  13 requirement #3. Phase 12 uses `i16Var` + manual `shr 4` per
  carry-forward.
- **Per-genre per-level config-table primitive** — if the per-level
  `platformerPhysics` override pattern (D-12) proves reusable across other
  genres (RPG per-floor stat overrides, sport per-track physics overrides),
  generalize via Phase 13 edit. Currently isolated to platformer; not
  pre-budgeted.
- **`platform()` rectangle deprecation** — user explicitly rejected
  deprecating. Both surfaces coexist. Future phase MAY revisit IF
  tilemap-collision proves to subsume all `platform()` use cases AND
  no game in the wild uses `platform()` (Phase 13+ evaluation, not
  pre-budgeted).
- **Fixing the existing `gbkt-examples/platformer/` IN PLACE** — rejected
  in favor of retirement (D-03). User explicitly chose retirement over
  "keep both — plan a separate fix-up plan inside Phase 12".
- **5+ UAT anchors** — explicitly capped at 5 (D-09 second one-time
  expansion). Phase 12.1, if it surfaces, inherits AT MOST 5 anchors.
- **Pre-inserting Phase 12.1 placeholder before port surfaces surplus seeds**
  — same Phase 9/10/11 rejection (bureaucracy if no surplus). Phase 12.1
  is conditional on ≥1 surplus seed at port-close.
- **Manual-banking DSL** — REQUIREMENTS.md hard constraint; carried forward
  unchanged from Phase 11 D-overfitting-inherited.

</deferred>

---

*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Context gathered: 2026-05-19*
