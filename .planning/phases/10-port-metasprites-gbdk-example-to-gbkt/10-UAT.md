---
status: resolved
phase: 10-port-metasprites-gbdk-example-to-gbkt
source: [10-CONTEXT.md, 10-RESEARCH.md, 10-VALIDATION.md]
started: 2026-05-18
updated: 2026-05-19
---

## Visual Evidence Rule

> For verification truths shaped "X is visible on screen", evidence MUST include a
> runtime screenshot, NOT just a variable-state assertion.

(Quoted verbatim from `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule".)

Every test below MUST end with an `emulator_screenshot` call at the climax frame.
Screenshots are written to:

```
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/{behavior-slug}.png
```

The screenshot is the BINDING evidence artifact. The accompanying `emulator_assert`
variable check is **necessary but never sufficient** — Phase 07.4 plans 14–18 verified
SC-4 (track visible) via `_current_tileset_id=1` variable evidence and burned 5 plans
before the user UAT revealed the runtime ROM never rendered the track. Phase 10 does not
repeat that mistake.

Variable assertions prove that the codegen wrote a value at one point in execution; they
do NOT prove the value is visually reflected by the time the player sees the screen.
Codegen GREEN is upstream of the visual surface; the screenshot is the only artifact
that binds the visual surface.

## Current Test

<!-- OVERWRITE each test - shows where we are -->

Behaviors 1 and 2 (DMG mode) passed in Plan 17. Behavior 3 (GBC sub-palette cycling)
remains pending — owned by Plan 18 (UAT-GBC).

## Diagnosis Summary

Plan 17 finding: button_pressed() uses rising-edge detection (joypad & mask & ~joypad_prev).
Consecutive step() calls with the same button held do NOT register as two separate presses.
Release frames (step with emptySet()) are required between edge-triggered presses in JVM-tier
UAT tests. This matches the reference game's behavior and is not a defect.

## Tests

### 1. B pressed (edge) → animation index advances + visible frame change

**Behavior:** D-01.1 — press B for a single frame (edge-triggered); `_idx` advances from
0 to 1. The metasprite visibly changes shape (different tile arrangement) because frame 0
and frame 1 have different non-empty tile counts and positions. The variable assertion is
NECESSARY but never SUFFICIENT — the screenshot is the binding evidence.

expected: After one B press, `_idx == 1`. After a second B press, `_idx == 2`. The elephant
metasprite is visibly displaying a different animation frame from the initial frame at each
climax screenshot. DMG screenshots are sufficient for this behavior (no palette cycle involved).

result: pass-partial (mechanism PASS; visual PARTIAL — see Defect D-V1)

actual: After first B press: `_idx == 1`. After release frame + second B press: `_idx == 2`.
Elephant sprite frame visibly changed on both presses — distinct tile arrangement on frame 2
vs frame 0 is visible in the screenshot. DMG mode confirmed sufficient.

evidence: .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png

plan: 10-17

mcp_script:
```
emulator_start(game="metasprites")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="play")
emulator_read_variable("_idx")                        # expect: 0 (initial frame)
emulator_step(frames=1, buttons=["b"])                # single-frame B press (edge-triggered)
emulator_read_variable("_idx")                        # expect: 1 (frame advanced)
emulator_step(frames=1)                               # release frame (edge-detection gap)
emulator_step(frames=1, buttons=["b"])                # second B press
emulator_read_variable("_idx")                        # expect: 2 (frame advanced again)
emulator_screenshot(path=".planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png")
emulator_assert([{type:"variable_equals", name:"_idx", expected:2}])
```

Climax frame: post-second-press frame (two visible frame changes since boot; `_idx == 2`).

### 2. A pressed (edge) → cycles 4 flip states via OAM attribute

**Behavior:** D-01.2 — press A for single frames (edge-triggered); `_rot` advances through
0 → 1 → 2 → 3 → 4. The `rot & 0x3` ladder selects: 0 = Normal, 1 = Flip-Y, 2 = Flip-XY,
3 = Flip-X. The metasprite visibly changes orientation on each press. The variable assertion
is NECESSARY but never SUFFICIENT — the screenshot is the binding evidence.

expected: After pressing A four times from initial state (`_rot == 0`):
- After 1st A press: `_rot == 1` (Flip-Y active)
- After 2nd A press: `_rot == 2` (Flip-XY active)
- After 3rd A press: `_rot == 3` (Flip-X active)
- After 4th A press: `_rot == 4` (rot & 0x3 == 0, Normal again; subpal advances to 1)
Screenshot captured at climax = rot=2 (Flip-XY, most visually distinctive orientation).
DMG screenshots are sufficient for this behavior (no palette cycle involved).

result: pass-partial (mechanism PASS; visual PARTIAL — see Defects D-V1 + D-V2)

actual: After 1st A press: `_rot == 1` (Flip-Y). After release + 2nd A press: `_rot == 2`
(Flip-XY). After release + 3rd A press: `_rot == 3` (Flip-X). After release + 4th A press:
`_rot == 4` (Normal again; rot & 0x3 == 0). Screenshot at rot=2 shows elephant visibly
mirrored on both axes vs normal orientation — Flip-XY confirmed. DMG mode sufficient.

evidence: .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png

plan: 10-17

mcp_script:
```
emulator_start(game="metasprites")
emulator_step(frames=10)                              # boot
emulator_wait_for_scene(scene="play")
emulator_read_variable("_rot")                        # expect: 0 (Normal initial)
emulator_step(frames=1, buttons=["a"])                # 1st A press
emulator_read_variable("_rot")                        # expect: 1 (Flip-Y active)
emulator_step(frames=1)                               # release frame (edge-detection gap)
emulator_step(frames=1, buttons=["a"])                # 2nd A press
emulator_read_variable("_rot")                        # expect: 2 (Flip-XY active)
emulator_screenshot(path=".planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png")
emulator_assert([{type:"variable_equals", name:"_rot", expected:2}])
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 3rd A press
emulator_read_variable("_rot")                        # expect: 3 (Flip-X active)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 4th A press
emulator_read_variable("_rot")                        # expect: 4 (back to Normal, subpal advances)
```

Climax frame: post-second-A-press frame (`_rot == 2`, Flip-XY; most visually distinctive).

### 3. A pressed (after 4 flip states wrap) → cycles 4 sub-palettes on GBC

**Behavior:** D-01.3 — after pressing A four times (bringing `_rot` to 4), the `rot >> 2`
expression yields `subpal == 1`. Pressing A four more times cycles `subpal` from 1 to 2,
then to 3, then to 0 (wrap). On GBC, the elephant metasprite visibly changes color:
gray (subpal 0) → pink (subpal 1) → cyan (subpal 2) → green (subpal 3). The variable
assertion (`_rot`) is NECESSARY but never SUFFICIENT — the screenshot is the binding evidence.

**NOTE:** DMG screenshot is NOT accepted as evidence for behavior 3 (CLAUDE.md Visual
Evidence Rule); GBC mode required. On DMG hardware, the CGB palette bits in the OAM
attribute byte are ignored — sub-palette changes are invisible, making DMG screenshots
inadequate evidence for this behavior. The `AgentSessionConfig(gbcMode = true)` flag
MUST be set for this test.

expected: After pressing A four times from `_rot == 0` (subpal=0) and then four more times
from `_rot == 4` (subpal=1), the elephant changes from gray to pink on GBC. Screenshot
captures the pink sub-palette state (`_rot == 8`, `subpal == 2`).

result: pass-partial (mechanism PASS; GBC-mode visual PASS — cyan sub-palette confirmed. Additional visual defects D-V1/D-V2 persist. New defect D-V3: _elephant_subPalette global stays 0 in sym snapshots even at rot=8 — seeded for Phase 10.1.)

actual: After 8 A presses (with release frames, edge-detection pattern from Plan 17): `_rot == 8`.
Screenshot in GBC mode shows elephant sprite rendered in CYAN color (sub-palette 2 = cyan) —
visibly distinct from DMG gray/green. The GBC frame event fix (Rule 1 bug in CoffeeGbEmulator:
GbcFrameReadyEvent was not wired, causing GBC screenshots to remain all-black) was required.
Fix: register GbcFrameReadyEvent alongside DmgFrameReadyEvent in CoffeeGbEmulator.kt.

evidence: .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png

plan: 10-18

mcp_script:
```
emulator_start(game="metasprites", gbcMode=true)      # GBC MODE REQUIRED for sub-palette evidence
emulator_step(frames=30)                              # boot (30 frames for GBC LCD init)
emulator_wait_for_scene(scene="play")
emulator_read_variable("_rot")                        # expect: 0 (subpal=0 gray)
# Note: release frames required between presses (edge detection fix from Plan 17)
emulator_step(frames=1, buttons=["a"])                # 1st A press (rot=1)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 2nd A press (rot=2)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 3rd A press (rot=3)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 4th A press (rot=4, subpal advances to 1 pink)
emulator_read_variable("_rot")                        # expect: 4 (subpal == rot >> 2 == 1 == pink)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 5th A press (rot=5)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 6th A press (rot=6)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 7th A press (rot=7)
emulator_step(frames=1)                               # release frame
emulator_step(frames=1, buttons=["a"])                # 8th A press (rot=8, subpal advances to 2 cyan)
emulator_read_variable("_rot")                        # expect: 8 (subpal == rot >> 2 == 2 == cyan)
emulator_step(frames=2)                               # PPU flush frames
emulator_screenshot(path=".planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png")
emulator_assert([{type:"variable_equals", name:"_rot", expected:8}])
# Note: gbcMode=true required — AgentSessionConfig(gbcMode = true) must be set
```

Climax frame: post-8th-A-press+2-flush frames (`_rot == 8`, `subpal == 2`, cyan palette;
visibly different from DMG screenshots — sprite is cyan in GBC mode).

## Anti-overfitting note (D-overfitting-1/2/3 — structural)

These three behaviors are the entire UAT floor for Phase 10. Sub-pixel movement fidelity,
pixel-and-frame trajectory parity with the GBDK reference, tile deduplication validation,
OAM hiwater range exhaustion, and any other "make it match reference visually" assertion
are explicitly **OUT of scope** per the phase anti-overfitting doctrine:

- **D-overfitting-1:** Do not add DSL features just to make THIS port pretty. EXCEPTION:
  the `metasprite { }` primitive IS the port substrate (D-04) — it is the minimum surface
  required for an honest port. Any OTHER DSL surface surfaced during the port → seed or
  Phase 13 edit, NOT Phase 10 expansion.
- **D-overfitting-2:** Do not tune codegen visitors to this example's cosmetic shape. If
  the named codegen bug-fix is a real class of bugs, fine. Cosmetic emission tuning to match
  reference style → no.
- **D-overfitting-3:** Do not let GBDK reference style become THE gbkt style. Reference
  uses macros, raw int16_t, inline tile data — those are C conventions. Use reference for
  codegen-quality comparison only. Skip the `#if HARDWARE_SPRITE_CAN_FLIP_*` macro fallback
  path entirely — gbkt is GBC-compatible, hardware flip works.

If a future port (Phase 11/12) needs richer UAT, that phase locks its own UAT — Phase 10
does not pre-allocate scope it does not need. The UAT contract is intentionally tight to
enforce the per-port single-bug-fix doctrine (one named codegen bug-fix; surplus → seeds
per `/gsd-capture --seed`).

## Summary

total: 3
passed: 3-partial
issues: 3
pending: 0
skipped: 0
blocked: 0

## Defects surfaced (seeded for Phase 10.1)

### Defect D-V1: elephant sprite tiles render corrupted (UAT-DMG)
status: resolved (Phase 10.1 — Plan 11 Seed004ElephantTileRenderingFixTest GREEN)
behaviors_affected: 1, 2
expected: clear elephant pixels, correct shape per asset-spec.md (5 frames at 64x48 each, 8x8 tiles)
observed: garbled tile rendering — sprite is visible but pixel arrangement is wrong (per human UAT review of behavior1/behavior2 screenshots)
likely_cause:
  - png2asset tile-data ordering vs MetaspriteVisitor.generateMetaspriteTileData() coordinate translation mismatch
  - 8x8 vs 8x16 sprite-mode mismatch (port uses 8x8 per asset-spec; reference probably 8x16)
  - tile-data byte ordering (lo/hi plane interleaving) regression
evidence:
  - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png
  - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png
investigate: compare hex-dump of port main.c elephant_tiles[] vs reference metasprites.c sprite tiles; verify SPRITES_8x8 vs SPRITES_8x16 mode matches what png2asset generated
seed_for: Phase 10.1

### Defect D-V2: BG renders as diagonal stripes instead of checkerboard
status: resolved (Phase 10.1 — Plan 02 Seed005CheckerboardBytePatternTest GREEN)
behaviors_affected: 1, 2, 3 (visual only)
expected: 8x8 checkerboard pattern alternating tiles (standard GB BG fill)
observed: diagonal stripes of black squares running top-left to bottom-right
root_cause: the byte pattern in `bgFillCheckerboard()` (gbkt-lang/.../MetaspriteBuilder.kt) is `0x80,0x80,0x40,0x40,...` which encodes a DIAGONAL LINE, not a checkerboard. The helper is misnamed: it's actually a diagonal-fill helper.
fix_route:
  - option A: rename helper to `bgFillDiagonal()`, add a separate `bgFillCheckerboard()` that emits the correct pattern (e.g., 0xAA,0xAA,0x55,0x55 repeating to make 4x4 checker squares OR copy the reference pattern verbatim)
  - option B: replace the literal pattern in place to make the existing helper produce a real checker
  - either way: emit one tile_pattern_t[] that, when fill_bkg_rect with tile 0, produces visible checkerboard squares
evidence: same as D-V1 (background visible in both screenshots)
fix_size: 1 line literal change (option B) or ~10 lines (option A)
seed_for: Phase 10.1

### Defect D-V3: _elephant_subPalette global variable not written in frame loop (UAT-GBC)
status: resolved (Phase 10.1 — Plan 04 Seed006SubPaletteSyncTest GREEN; original variable-mirror defect closed. NOTE: a separate BG-palette visual defect DEF-10.1-13-C surfaced mid-execution in Phase 10.1 and was escalated to Phase 10.2 per commit 94890c63)
behaviors_affected: 3
expected: _elephant_subPalette global variable reflects current sub-palette index (rot >> 2) during each frame, readable via sym file
observed: _elephant_subPalette is declared as `UINT8 _elephant_subPalette = 0u;` in generated C but never assigned in play_frame(). The local variable `uint8_t subpal = _rot >> 2;` is computed and passed to move_metasprite_ex() correctly, but the global tracking variable is never updated. Sym file reads always return 0.
root_cause: MetaspriteVisitor generates _elephant_subPalette as a global tracker but the frame loop uses a local `subpal` variable for the OAM call. The global is never synchronized from the local.
visual_impact: none — the OAM gets the correct subpal value. sym-file assertion on elephant_subPalette will always fail. The mechanism is correct; only the debug variable is stale.
fix_route:
  - option A: add `_elephant_subPalette = subpal;` assignment in the generated play_frame() after computing subpal
  - option B: remove the _elephant_subPalette global if it is not used anywhere else
evidence:
  - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.json (shows elephant_subPalette: 0 at rot=8)
seed_for: Phase 10.1

### Defect D-V4: CoffeeGbEmulator did not register GbcFrameReadyEvent (fixed in Plan 18)
status: fixed (Plan 18 auto-fix Rule 1)
behaviors_affected: 3 (GBC mode screenshots were all-black before fix)
observed: GBC-mode screenshots produced all-black 147-byte PNG files. Frame buffer stayed at initial all-zero state.
root_cause: CoffeeGbEmulator registered a listener only for Display.DmgFrameReadyEvent. In CGB mode, Coffee-GB fires Display.GbcFrameReadyEvent instead. The frame buffer was never updated.
fix: Register GbcFrameReadyEvent listener in CoffeeGbEmulator.kt alongside DmgFrameReadyEvent. Uses event.toRgb(buffer) (no grayscale flag — GBC fires native RGB).
commit: (Plan 18 Task 1 commit)


## Gaps

None at draft time — the three D-01 behaviors are fully specified with mcp_scripts,
variable assertions, and screenshot paths. GBC mode requirement for behavior 3 is
explicitly documented (AgentSessionConfig(gbcMode = true) must be set). UAT execution
awaits ROM build completion (Plans 2–13).
