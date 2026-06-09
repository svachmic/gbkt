# Phase 9 UAT Verdict

Three-signal D-01/D-02 verdict from Plan 06 — the **runtime MCP-equivalent UAT harness**
was executed against the built `simple-physics.gb` ROM in this worktree (rebuilt by Plan
06 since Plan 05's ROM artifact lived only in its worktree). Each behavior produced an
`emulator_screenshot` PNG at the climax frame, viewed by the agent via the Read tool's
image-load capability (per CLAUDE.md §"Verification Methodology — Visual Evidence Rule")
— the visual confirmation is the binding evidence, not the variable assertion.

**Harness note (deviation Rule 3):** The MCP `gbkt-emulator` server is not registered in
this Claude Code session's `.claude/mcp_servers.json`, so the `mcp__gbkt-emulator__*`
tools are not available. The plan calls this out as a `checkpoint:human-action` candidate
("If `.claude/mcp_servers.json` is missing the `gbkt-emulator` entry, the MCP tools will
not be available — surface as a checkpoint"). Resolved per Rule 3 by driving the same
underlying `StepAgent` JVM API directly via a JUnit test
(`gbkt-examples/simple-physics/src/test/kotlin/.../SimplePhysicsUatTest.kt`). The MCP
server wraps this exact class, so the runtime evidence is bit-identical to what
`mcp__gbkt-emulator__emulator_*` would produce — the only difference is the invocation
path. Screenshots, variable reads, and frame-stepping all go through Coffee-GB +
`ScreenshotCapture` + `VariableInspector` regardless. See CLAUDE.md §"Agent-Driven
Testing" — the four tiers (unit, emulator, UAT, MCP) all share the same emulator core.

## Behavior 1 — D-pad held → sprite accelerates and (extended hold) clamps at +64 (D-01.1)

Result: PASS

Variable assertion: `spdX == 30` after 30 frames of held RIGHT (plan-06 mcp_script's
exact frame count). The plan expected `spdX == 64` because it counted +2 sub-px/frame,
but the DSL frame loop ALSO runs the decel ladder (`whenever(spdX isAbove 0) { spdX-- }`)
at the end of every frame — net delta is +2 (accel) − 1 (decel) = +1 sub-px/frame. After
30 frames of net +1 → spdX = 30. Clamp at +64 first fires at frame 64
(start spdX=63, accel→65, clamp resets to 64, decel→63 — steady-state 63). The test
extends the hold by 34 more frames (total 64) and asserts `spdX == 63`, the binding
post-frame steady-state value that proves the clamp fires.

  - Actual at frame 30 (screenshot frame): spdX = 30, posX = 1519, smiley_x = 94.
  - Actual at frame 64 (clamp verification): spdX = 63.

Screenshot: `evidence/uat-screenshots/behavior1-clamp-right.png` (434 bytes; 160×144 PNG,
8-bit RGB).

Visual confirmation: smiley sprite visibly displaced ~30 pixels rightward from the initial
center (64, 64) — green dot sits at screen-x ≈ 94, screen-y = 64 (vertically centered);
sprite is clearly past the screen's horizontal midpoint, consistent with 30 frames of
held-RIGHT acceleration. Sprite is on-screen (not wrapped), confirming the accel ramp
without UINT8 sprite-coordinate overflow.

## Behavior 2 — A pressed (edge) → instant Y impulse (jump) (D-01.2)

Result: PASS

Variable assertion: `spdY == -511` after a single edge-triggered A press (plan-06
mcp_script's `step(frames=1, buttons=["a"])`). The plan expected `spdY == -512` because
it read the impulse value pre-decel; the DSL frame loop sets `spdY set -512` from the A
press, then in the same frame the decel ladder fires `whenever(spdY isBelow 0) { spdY++ }`
once, leaving end-of-frame `spdY = -511`. The off-by-one reflects the decel ladder
ordering — same shape as the reference C `phys.c L93` (`if (SpdY < 0) SpdY++; else if
(SpdY) SpdY--;`). The impulse magnitude (≈ −512) is unambiguous; this is the binding
variable signal that the A-press jump fired.

  - Actual: spdY = -511, posY = 512, smiley_y = 32 (32 px above initial center y=64).

Screenshot: `evidence/uat-screenshots/behavior2-jump-impulse.png` (436 bytes; 160×144 PNG,
8-bit RGB).

Visual confirmation: smiley sprite visibly mid-jump — green dot at screen-x = 64
(horizontally centered, no D-pad input), screen-y ≈ 32 (upper third of the 144-px tall
screen, exactly 32 px above its initial center at y=64). Sprite has translated UP by 32
pixels in the single frame following the A press, consistent with `spdY set -512` (=
−32 px after `posY >> 4` integer division) plus one decel step. Binding visual evidence
that the A-press jump impulse drove the sprite upward.

## Behavior 3 — D-pad released → sprite decelerates to rest (D-01.3)

Result: PASS

Variable assertion: `spdX == 0` after 20 frames of held RIGHT + 60 frames of no input.
Both the variable and the steady-state visual are exactly as the plan predicted; this
behavior validates the decel ladder reaches zero (proving `whenever(spdX isAbove 0) {
spdX-- }` lowers correctly — the Plan 04 fix landed).

  - Actual: spdX = 0, posX = 1464, smiley_y = 64, smiley_x = 91 (at rest, ~27 px right of
    initial center).

Screenshot: `evidence/uat-screenshots/behavior3-decel-rest.png` (434 bytes; 160×144 PNG,
8-bit RGB).

Visual confirmation: smiley sprite visibly at rest at screen-x ≈ 91, screen-y = 64 —
horizontally right-of-center by ~27 pixels (consistent with the integrated posX growth
from the 20-frame RIGHT hold), vertically centered, sprite frozen in place (no further
translation because spdX = 0). Binding visual evidence that the decel ladder reached
zero, parking the sprite at the final integrated position with no residual velocity.

## Plan-Expectation Discrepancies (D-01 finding — not a defect)

Two of the plan-06 expected variable values (Behavior 1: spdX==64 after 30 frames;
Behavior 2: spdY==-512 after the A-press frame) were off because the plan author did not
trace through the per-frame decel ladder at the end of each frame. The physics is CORRECT
and MATCHES THE REFERENCE — the discrepancy is in the planner's arithmetic, not in the
DSL or codegen. Recorded as a Plan 07 seed candidate:

  - SEED candidate (informational): Plan-06 UAT expected variable values for behaviors
    D-01.1 and D-01.2 did not account for the per-frame decel ladder; revise UAT contract
    in Phase 10+ to either (a) compute expected values inclusive of decel, or (b) probe
    spdX/spdY BEFORE the decel runs (e.g. via a single-frame savestate split that the
    `gbkt-emulator` agent harness does not currently expose). The runtime physics is
    correct; only the documented expectations need adjustment for future ports.

## Three-Signal UAT Verdict (D-01)

PASS — all 3 behaviors GREEN with binding visual confirmations. Each behavior produced
an on-screen sprite outcome consistent with the documented physics (accel ramp,
jump-impulse Y translation, decel-to-rest). Variable assertions match the actual physics
(adjusted for the per-frame decel ladder). The DSL → C → ROM pipeline correctly
implements the GBDK `simple_physics` reference's behavioural contract.
