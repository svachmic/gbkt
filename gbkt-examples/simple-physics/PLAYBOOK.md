# SimplePhysics

## Overview
Sub-pixel physics demo ported from GBDK simple_physics reference
(`/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/`). One actor (ball
face) controlled via D-pad with 12.4 fixed-point sub-pixel physics. First reference port
for the Phase 9–12 GBDK reference-port validation track — the reference C is used as a
codegen-quality oracle, not a DSL style template.

## How to Play
Hold any D-pad direction to accelerate the ball sprite in that direction (2 sub-pixels
per frame, clamped at ±64 sub-pixels per frame in each axis — clamp roughly equals 4
pixels per frame at peak). Release the D-pad and the sprite decelerates 1 sub-pixel per
frame back to rest. Press A at any moment for an instant upward Y impulse of -JUMP_ACCELERATION_IN_SUBPIXELS
sub-pixels (edge-triggered on press — held A does not re-fire). There
is no win or lose state — the demo runs indefinitely.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| play  | UP     | Accelerate sprite upward (`spdY -= 2` sub-pixels/frame, clamped at `-64`) |
| play  | DOWN   | Accelerate sprite downward (`spdY += 2` sub-pixels/frame, clamped at `64`) |
| play  | LEFT   | Accelerate sprite left (`spdX -= 2` sub-pixels/frame, clamped at `-64`) |
| play  | RIGHT  | Accelerate sprite right (`spdX += 2` sub-pixels/frame, clamped at `64`) |
| play  | A      | Jump impulse — instant `spdY set -512` (edge-triggered on press) |

## Scene Flow
- play (only scene — no navigation)

## Win / Lose Conditions
None — infinite physics demo. Sprite remains on screen (no bounds wrapping); position
drifts freely.

## Known Quirks
- **DSL emission shape: `spdY++` / `spdX++` emit assignment form** (`_spdY = _spdY + 1`),
  not C's `_spdY++`. Semantically identical to the reference; the generated C diff in
  `evidence/oracle-comparison.md` documents this divergence as informational, not a
  correctness gap.
- **Single-frame 8x8 PNG used.** The reference cycles through 4 sprite frames via
  `set_sprite_tile(0, frame_counter & 3)`; this port uses one static 8x8 sprite. Sprite
  animation cycling is OUT of scope (D-overfitting-3 anti-overfitting rail; the GBDK
  reference is a codegen oracle, not a DSL style template).
- **Bug B workaround for position sync** — `ActorRef.moveTo(Expr, Expr)` does not exist
  in the current DSL surface (`moveTo` only accepts `Int`). The port uses
  `ball.x set (posX shr 4)` and `ball.y set (posY shr 4)` via the `ActorPropertyRef`
  set-with-Expr API. The reference uses `move_sprite(0, SUBPIXELS_TO_PIXELS(PosX), ...)`
  inline. Functionally equivalent; if Bug B is named as the Plan 04 codegen fix, this
  workaround is replaced with the idiomatic `ball.moveTo(posX shr 4, posY shr 4)`.
- **Position values are sub-pixel, not pixel.** `posX = 1024` means screen X = 64
  (12.4 fixed-point: `posX >> 4`). All variables are signed 16-bit (`INT16`) to allow
  negative speeds and sub-pixel precision.

## Variables Reference
| Variable | Type  | Semantic | Description |
|----------|-------|----------|-------------|
| posX     | INT16 | position | Sub-pixel X position (12.4 fixed-point; `posX >> 4` = screen pixel X). Initial value 1024 (= 64 px × 16). |
| posY     | INT16 | position | Sub-pixel Y position (12.4 fixed-point; `posY >> 4` = screen pixel Y). Initial value 1024 (= 64 px × 16). |
| spdX     | INT16 | velocity | Sub-pixel X velocity. Range -64..64 sub-pixels/frame. Clamped each frame after accel. |
| spdY     | INT16 | velocity | Sub-pixel Y velocity. Range -64..64 sub-pixels/frame. Clamped each frame after accel. Set to -512 on A-press jump impulse. |

## MCP Input Scripts

The following input scripts back the Phase 9 UAT contract (see
`.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-UAT.md`). Each script
verifies one of the three D-01 behaviors and captures a runtime screenshot at the
climax frame — the screenshot is the binding evidence (per `CLAUDE.md` §"Verification
Methodology — Visual Evidence Rule"; the variable assertion alone is necessary but never
sufficient).

> **Per-frame ladder.** Every frame runs accel/jump → integrate → decel ladder in that order
> (matches reference `phys.c` ordering). Net effect on velocity per held-D-pad frame is
> +2 (accel) − 1 (decel) = **+1 sub-pixel/frame**. The same ordering makes the A-press impulse
> end at `spdY = -511` (set −512, then end-of-frame decel runs once: −512 + 1 = −511).
> The +64 clamp is reached at frame 64 with held RIGHT and yields steady-state `spdX = 63`
> after end-of-frame decel. The values below are end-of-frame, after the ladder runs.

### Behavior 1 — D-pad held → sprite accelerates and clamps at max speed

```
emulator_start(game="simple-physics")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="play")
emulator_read_variable("spdX")                        # expect: 0
emulator_step(frames=30, buttons=["right"])           # hold right 30 frames (net +1/frame)
emulator_read_variable("spdX")                        # expect: 30 (end-of-frame after 30 frames at +1/frame)
emulator_screenshot(label="behavior1-clamp-right-30frames")
emulator_assert([{type:"variable_equals", name:"spdX", expected:30}])

# Continue holding RIGHT to verify the +64 clamp fires (binding clamp signature):
emulator_step(frames=34, buttons=["right"])           # 30 + 34 = 64 frames total
emulator_read_variable("spdX")                        # expect: 63 (clamp steady-state: 65 → clamp 64 → decel 63)
emulator_assert([{type:"variable_equals", name:"spdX", expected:63}])
```

### Behavior 2 — A pressed (edge) → instant Y impulse (jump)

```
emulator_start(game="simple-physics")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="play")
emulator_read_variable("spdY")                        # expect: 0
emulator_step(frames=1, buttons=["a"])                # single frame press (edge-triggered)
emulator_read_variable("spdY")                        # expect: -511 (set -512 then same-frame decel ++ → -511)
emulator_screenshot(label="behavior2-jump-impulse-spdY")
emulator_assert([{type:"variable_equals", name:"spdY", expected:-511}])
```

### Behavior 3 — D-pad released → sprite decelerates to rest

```
emulator_start(game="simple-physics")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="play")
emulator_step(frames=20, buttons=["right"])           # build up speed (+1/frame net)
emulator_read_variable("spdX")                        # expect: 20 (end-of-frame after 20 frames at +1/frame)
emulator_step(frames=60)                              # no buttons — decel loop fires 1/frame
emulator_read_variable("spdX")                        # expect: 0 (decelerates 1 sub-pixel/frame → 0 within 20 frames)
emulator_screenshot(label="behavior3-decel-rest-60frames")
emulator_assert([{type:"variable_equals", name:"spdX", expected:0}])
```
