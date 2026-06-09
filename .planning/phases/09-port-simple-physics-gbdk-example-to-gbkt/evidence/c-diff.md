# C Diff — phys.c vs gbkt-generated main.c (informational)

## Purpose

This document is the D-09 part 3 (C-diff) **informational** appendix for Phase 9's
codegen-quality oracle. It is **NOT** a parity contract — the GBDK reference
`phys.c` is the codegen-quality oracle (correctness), not a DSL style template.

Where gbkt-generated C is **shorter/clearer** than the equivalent hand-written
GBDK C, that is the "DSL value" signal: framework primitives that buy the user
mechanical scaffolding for free. Where gbkt is **longer**, the rationale is
documented inline; per the Phase 9 anti-overfitting doctrine (D-overfitting-2),
this does NOT imply gbkt codegen should be "tuned" to look like `phys.c` —
genuine over-emission discoveries become seeds (see `evidence/oracle-comparison.md`
§Seeds harvested).

## Methodology

- **Left-hand side (reference):**
  `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/phys.c`
  — 99 lines; hand-written GBDK C; single `main()` containing a `while(TRUE)`
  game loop. Captured verbatim from
  `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c`
  during Plan 02.
- **Right-hand side (gbkt):**
  - `gbkt-examples/simple-physics/build/gbkt/generated/main.c` — 206 lines;
    HOME-bank scaffolding (variable defs, joypad helpers, OAM sync, sound driver,
    dialog window helpers, fade helpers, scene dispatcher, `main()`).
  - `gbkt-examples/simple-physics/build/gbkt/generated/bank1.c` — 62 lines;
    `#pragma bank 1` scene file containing `play_enter()` and `play_frame()` —
    the active per-frame physics body.
  - Rebuilt via `./gradlew :gbkt-examples:simple-physics:generateC` against the
    Plan-03 SimplePhysics.kt DSL with the Plan-04 ExprVisitor fix in place.
- **Active-code scope:** only the active per-frame physics body
  (`phys.c` L62-99 inside `main()` vs `bank1.c` L17-60 `play_frame()`) and the
  one-shot init (`phys.c` L46-60 vs `main.c` L182-203 + `bank1.c` L9-15) are
  diffed line-for-line. gbkt's HOME-bank boilerplate (joypad helpers, OAM sync,
  sound driver, dialog scaffolding, fade helpers) is acknowledged structurally
  but not line-by-line diffed — it is framework code emitted whether the game
  uses it or not, and the reference has no counterpart for any of it.

## Source files

- **DSL:**
  `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt`
  (132 lines total, ~95 lines of game-logic code excluding header/doc).

## Side-by-side mapping

The table below maps fragments of `phys.c` L37-94 to their DSL and generated-C
counterparts. The "Verdict" column is reckoned by character count of the
user-authored surface (DSL line vs reference C line). "Generated C" quotes are
verbatim from `bank1.c` (play_frame body) or `main.c` (initialization).

| phys.c fragment | gbkt DSL (SimplePhysics.kt) | gbkt generated C | Verdict |
|---|---|---|---|
| `int16_t PosX, PosY;` (L37) + `int16_t SpdX, SpdY;` (L39) | `var posX by i16Var(1024); var posY by i16Var(1024); var spdX by i16Var(0); var spdY by i16Var(0)` (L44-47) | `INT16 _posX = 1024u; INT16 _posY = 1024u; INT16 _spdX = 0u; INT16 _spdY = 0u;` (main.c L14-17) | gbkt SHORTER per variable (declaration + initializer fused in one line; reference splits decl on L37/L39 and assigns in `main()` L59-60) |
| `uint8_t joy = 0, old_joy;` (L42) | (none — implicit) | `UINT8 __joypad = 0u; UINT8 __joypad_prev = 0u;` (main.c L18-19) + `update_joypad()` helper (main.c L29-32) | gbkt SHORTER at user surface (zero DSL lines); framework emits joypad poll for free |
| `set_sprite_data(0, 4, sprite_data);` + literal 64-byte `sprite_data[]` (L9-14) | `sprite(asset("sprites/smiley.png")) { size(8, 8); hitbox(0, 0, 8, 8) }` (L55-58) | `set_sprite_data(0u, 1u, sprites_smiley_tiles);` (main.c L189) + auto-generated `sprites/smiley.h` | gbkt **dramatically** SHORTER (1 DSL line vs ~6 lines of inline hex + macro call; PNG asset is processed by gbkt pipeline) |
| `SHOW_BKG; SHOW_SPRITES;` (L57) + `BGP_REG = ...` (L48) | `showSprites()` inside `enter { }` (L68) | `DISPLAY_ON; SHOW_BKG; SHOW_SPRITES;` (main.c L186-188) | gbkt EQUAL/SHORTER (1 line for the user; framework emits init for free) |
| `PosX = PosY = PIXELS_TO_SUBPIXELS(64);` (L59) | `posX set 1024; posY set 1024` inside `enter { }` (L69-70) | `_posX = 1024u; _posY = 1024u;` inside `play_enter()` (bank1.c L11-12) | gbkt EQUAL (2 lines vs 1 macro line; both forms are clear; gbkt drops the macro and inlines the constant — see anti-overfitting note below) |
| `if (INPUT_KEY(J_UP)) { SpdY -= Y_ACCELERATION_IN_SUBPIXELS; if (SpdY < -MAX_Y_SPEED_IN_SUBPIXELS) SpdY = -MAX_Y_SPEED_IN_SUBPIXELS; }` (L67-69) | `whenever(dpad.up.held) { spdY -= 2; whenever(spdY isBelow -64) { spdY set -64 } }` (L78-81) | `if (button_held(J_UP)) { _spdY -= 2u; if (_spdY < -64) { _spdY = -64; } }` (bank1.c L18-23) | gbkt EQUAL (line count parity; DSL operator overloads `-=` and `whenever`/`isBelow` make the structure read as data flow instead of mutation) |
| `if (INPUT_KEY(J_DOWN)) { SpdY += Y_ACCELERATION_IN_SUBPIXELS; if (SpdY > MAX_Y_SPEED_IN_SUBPIXELS) SpdY = MAX_Y_SPEED_IN_SUBPIXELS; }` (L70-72) | `whenever(dpad.down.held) { spdY += 2; whenever(spdY isAbove 64) { spdY set 64 } }` (L82-85) | `if (button_held(J_DOWN)) { _spdY += 2u; if (_spdY > 64) { _spdY = 64u; } }` (bank1.c L24-29) | gbkt EQUAL — note the comparison RHS `64` lowers BARE (no `u` suffix) post Plan-04 fix; assignment RHS `64u` keeps the suffix (correct — assignment is unsigned context per Phase 07.9 Rule 2) |
| `if (INPUT_KEY(J_LEFT)) { SpdX -= ... }` / `if (INPUT_KEY(J_RIGHT)) { SpdX += ... }` (L74-80) | `whenever(dpad.left.held) { ... }; whenever(dpad.right.held) { ... }` (L90-97) | `if (button_held(J_LEFT)) { ... }; if (button_held(J_RIGHT)) { ... }` (bank1.c L30-41) | gbkt EQUAL (symmetric with Y-axis) |
| `if (INPUT_KEYPRESS(J_A)) { SpdY = -JUMP_ACCELERATION_IN_SUBPIXELS; }` (L82-84) | `whenever(buttons.a.pressed) { spdY set -512 }` (L103) | `if (button_pressed(J_A)) { _spdY = -512; }` (bank1.c L42-44) | gbkt EQUAL/SHORTER (single-line DSL; framework's `buttons.a.pressed` does the `INPUT_KEYPRESS`-style edge detection automatically via `button_pressed()` HOME helper) |
| `PosX += SpdX, PosY += SpdY;` (L87) | `posX += spdX; posY += spdY` (L108-109) | `_posX += _spdX; _posY += _spdY;` (bank1.c L45-46) | gbkt EQUAL (DSL splits the comma-expression for clarity; mechanical lowering matches reference exactly) |
| `move_sprite(0, SUBPIXELS_TO_PIXELS(PosX), SUBPIXELS_TO_PIXELS(PosY));` (L90) | ~~`smiley.x set (posX shr 4); smiley.y set (posY shr 4)` (L116-117) — **Bug B workaround**~~ → **RETRACTED in 9.1-01**: `smiley.moveTo(posX shr 4, posY shr 4)` (1 line, SEED-002 fix) | `_smiley_x = _posX >> 4u; _smiley_y = _posY >> 4u;` + framework `update_sprites()` calls `move_sprite(0u, _smiley_x + 8u, _smiley_y + 16u)` per frame (same generated C, single SetPosition op now) | gbkt EQUAL at DSL surface (1 line vs 1 line post-SEED-002 fix — the "longer regions" entry for this row is now **superseded**) |
| `if (SpdY < 0) SpdY++; else if (SpdY) SpdY--;` (L93) | `whenever(spdY isBelow 0) { spdY++ }; whenever(spdY isAbove 0) { spdY-- }` (L124-125) | `if (_spdY < 0) { _spdY = _spdY + 1u; } if (_spdY > 0) { _spdY = _spdY - 1u; }` (bank1.c L49-54) | gbkt LONGER at gen-C surface (4 lines for the two-arm ladder vs 1 chained line in reference). The DSL chose two independent `whenever` blocks for clarity (Pitfall 4: avoids the if-else-if shadowing); gen-C mirrors the DSL structurally, not the reference's chain. The two branches are mutually exclusive at runtime — same observable behavior |
| `if (SpdX < 0) SpdX++; else if (SpdX) SpdX--;` (L94) | `whenever(spdX isBelow 0) { spdX++ }; whenever(spdX isAbove 0) { spdX-- }` (L126-127) | `if (_spdX < 0) { _spdX = _spdX + 1u; } if (_spdX > 0) { _spdX = _spdX - 1u; }` (bank1.c L55-60) | gbkt LONGER at gen-C (same as above; same anti-Pitfall-4 rationale) |
| `vsync();` (L97) + outer `while (TRUE) { ... }` (L62) | (none — implicit `frame { }` lifecycle) | `while (1) { update_joypad(); switch (current_scene) { case SCENE_PLAY: play_frame_trampoline(); break; } update_sprites(); sound_driver_update(); wait_vbl_done(); }` (main.c L193-203) | gbkt SHORTER at user surface (zero DSL lines for the frame loop); framework owns: joypad poll, scene dispatch, OAM sync, sound driver tick, vsync — all emitted for free |

## Shorter/clearer regions (DSL value signal)

The DSL is dramatically shorter for the **user-authored game-logic surface** in
these regions:

- **Variable declarations:** `var posX by i16Var(1024)` (1 line) vs
  `int16_t PosX;` declared at file scope + `PosX = PIXELS_TO_SUBPIXELS(64);`
  assigned inside `main()` (2 lines split across file). gbkt fuses
  decl+initializer; reference splits.
- **Sprite asset:** `sprite(asset("sprites/smiley.png")) { size(8, 8); hitbox(...) }`
  (3 DSL lines) vs the reference's 64-hex-byte `const uint8_t sprite_data[]` +
  `set_sprite_data(0, 4, sprite_data)` (~6 lines of raw data + 1 macro call).
  gbkt's PNG asset is processed by the asset pipeline — no manual hex authoring.
- **Input edge detection:** `whenever(buttons.a.pressed)` (1 line, type-safe)
  vs `INPUT_PROCESS;` poll-macro at top of `main` + `if (INPUT_KEYPRESS(J_A))`
  state-comparison (2 sites the user must wire). gbkt's `buttons.a.pressed`
  reads as data flow; framework emits `update_joypad()` + `button_pressed()`
  helpers in HOME bank automatically.
- **Frame loop:** zero DSL lines for `while (TRUE) { ... vsync(); }` + sprite
  sync + scene dispatch + sound driver — gbkt's `frame { }` and `update_sprites()`
  + `sound_driver_update()` + `wait_vbl_done()` are framework-emitted.
- **Magic-number macros:** the reference defines `MAX_X_SPEED_IN_SUBPIXELS`,
  `Y_ACCELERATION_IN_SUBPIXELS`, `JUMP_ACCELERATION_IN_SUBPIXELS`,
  `SUBPIXELS_TO_PIXELS`, `PIXELS_TO_SUBPIXELS` as `#define`s. The DSL uses bare
  literals (`64`, `2`, `-512`, `shr 4`) inline; this is a clarity trade-off the
  Phase 9 author made deliberately for the port (literal values match the
  reference's `#define` substitutions and the DSL is short enough that named
  constants would add noise without clarity). See `09-PATTERNS.md` §"Pitfall 5".

## Equal regions

- **D-pad accel + clamp branches** (4 axis blocks: Y-up, Y-down, X-left, X-right):
  line-count parity (3 lines DSL vs 3 lines C); structural parity (if-block with
  compound update + nested clamp). The DSL operator overloads (`-=`, `whenever`,
  `isBelow`/`isAbove`, `set`) read as data flow but emit the same control-flow
  shape as the reference's `if (...) { ...; if (...) ... = ...; }` skeleton.
- **Position integration** (`posX += spdX`): 1-for-1 with reference's
  `PosX += SpdX`. The DSL splits the reference's comma-expression for clarity.
- **A-press jump impulse:** 1 line each side; same observable behavior.

## Longer regions and rationale

- **HOME-bank scaffolding (joypad, OAM, sound, dialog, fade):** main.c L28-160
  is ~130 lines of framework helpers the reference does NOT emit. **Rationale:**
  this is the framework's value-add — joypad edge detection (`button_pressed`),
  sprite OAM sync (`update_sprites` reading per-actor x/y globals), sound
  channel driver, dialog-window helpers, palette fade helpers. The simple-physics
  port uses only a small subset (joypad + OAM sync) but the framework emits all
  of them unconditionally. **Not a defect for THIS port** — but it is the
  primary contributor to the +14-byte `l__CODE` delta and the +132-byte `l__HOME`
  delta recorded in `rom-size-comparison.md`. **Future opportunity:** an
  unreferenced-helper DCE pass would cut these for trivial games (deferred —
  not a Phase 9 seed because none surfaced; future codegen-hygiene candidate).
- **Scene dispatcher infrastructure:** trampolines + switch statement (`play_enter_trampoline`,
  `play_frame_trampoline`, `navigate_to_scene`, the `switch (current_scene)` in
  `main()`). The reference has no scenes — its `main()` body is the single
  game loop. **Rationale:** gbkt's scene model is foundational (Phase 1 IR);
  the trampoline pattern is required for cross-bank calls (Phase 07.9 BANKED
  calling convention). Even single-scene games pay the dispatcher cost. **Not
  a defect.**
- ~~**Actor position-sync glue (Bug B workaround):** the DSL had to spell
  `smiley.x set (posX shr 4); smiley.y set (posY shr 4)` (2 lines) where the
  reference uses `move_sprite(0, PosX >> 4, PosY >> 4)` (1 line). The missing
  `ActorRef.moveTo(Expr, Expr)` overload forced the workaround. **Captured as
  SEED-002** — a small DSL-ergonomics gap.~~ **RETRACTED in 9.1-01:** SEED-002
  was resolved in Phase 9.1 Plan 01 by adding `ActorRef.moveTo(Expr, Expr)`.
  SimplePhysics.kt now uses `smiley.moveTo(posX shr 4, posY shr 4)` (1 line,
  DSL EQUAL to reference). The "longer regions" entry for this item is superseded.
- **Decel ladder lowered as two independent `if` blocks** (bank1.c L49-54 / L55-60):
  gen-C is 4 lines per axis where the reference packs it as `if (...) ...; else if (...) ...;`
  (1 line per axis). **Rationale:** the DSL author chose two `whenever` blocks
  for explicit mutual exclusion (Pitfall 4: `else if` shadowing risk in the DSL).
  The lowered C mirrors the DSL structurally. Observable behavior matches the
  reference exactly (post-Plan-06 UAT: D-01.3 PASS, sprite decelerates to rest).
  **Not a defect.** A future DSL extension might offer an `eitherOr { ... } { ... }`
  shape that lowers to `if/else if` — but that is a DSL-ergonomics question for
  a later milestone, NOT a "make gbkt look like phys.c" goal.

## DSL value verdict

**gbkt DSL is dramatically shorter overall for the user-authored game-logic
surface** (variables, scenes, actors, input, physics body). The user writes
~95 lines of Kotlin DSL; the reference is ~99 lines of GBDK C plus 64 bytes
of inline sprite hex. The DSL surface count is similar — but the DSL lines
read as data flow (`whenever`, `set`, `+=`, `isAbove`) while the reference
lines are bit-manipulation and macro-invocation. **gbkt is longer for the
generated HOME-bank scaffolding** (joypad helpers, OAM sync, sound driver,
dialog/fade scaffolding, scene dispatcher) — but this scaffolding is **the
gbkt value proposition** (resource management for free, no manual OAM/sound
plumbing, scene model for multi-screen games), **not a defect**. The 14-byte
`l__CODE` delta (1.025× reference) is well inside the 2× cap and represents
the per-frame cost of these primitives for THIS minimal game; non-trivial
games (Pong, Breakout, Explorer, the future Phase 10/11/12 ports) amortize
this cost across many more lines of game logic, so the ratio improves.

~~The single user-surface "longer" region was the Bug B workaround
(`smiley.x set (posX shr 4); smiley.y set (posY shr 4)` vs `move_sprite(0, ...)`).
Resolved by SEED-002.~~

**As of Phase 9.1 Plan 01, the Bug B workaround row is superseded.** `ActorRef.moveTo(Expr, Expr)` was
added (SEED-002) and SimplePhysics.kt migrated to `smiley.moveTo(posX shr 4, posY shr 4)`. The DSL
user-surface is now EQUAL to the reference for this region (1 line vs 1 line). There are no remaining
"longer" user-surface regions in this port.

_Retracted 2026-05-14 by `.planning/phases/09.1-simple-physics-surplus-codegen-defects-inserted/09.1-01-PLAN.md` (SEED-002 fix)._
