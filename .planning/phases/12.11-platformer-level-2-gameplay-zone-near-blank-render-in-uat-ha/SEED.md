# SEED — Phase 12.11: platformer level-2 gameplay-zone near-blank render (UAT harness)

**Created:** 2026-06-02 (routed out of Phase 12.10 wave-2 post-merge gate)
**Source:** Phase 12.10 `/gsd-execute-phase` — wave-2 post-merge full-suite gate surfaced
`PlatformerTemplateUatTest.anchor5LevelSwitch()` RED.
**Defect class:** codegen/render (level-2 gameplay-zone framebuffer near-blank) — NOT
test-harness capture timing (Phase 12.10's scope).

## Symptom (TWO distinct failures, both deterministic)

`anchor5LevelSwitch()` is deterministically RED. It surfaces **two** defects in the
level-2 path — disabling/reordering one only reveals the other:

### Failure A — level switch does not complete (logic)

With the render assertion downgraded, the test fails at the logic assertion
(`PlatformerTemplateUatTest.kt:1156`):

```
Expected _current_level == 1 after Phase 12.6 fix ... got 0.
```

Across 3/3 isolated reruns: `final_current_level: 0`, `post_release_scene: nextLevelScene`,
`mid_level_2_scene: nextLevelScene`. The scene stays on the **nextLevelScene card** and the
START-press → gameplay (level-2) navigation never fires in the harness run. `_current_level`
stays 0 (level-1).

### Failure B — level-2 BG renders near-blank (render)

When the switch *does* reach level-2 (observed in an earlier run with `current_level: 1`),
the level-2 capture fails the non-uniform assertion at
`PlatformerTemplateUatTest.kt:220`:

```
anchor5-level-2: dominant colour must cover < 95% of pixels ...
Dominant-colour ratio: 0.983 (22639/23040 pixels).
```

The captured `03-level-2.png` is a 661-byte near-blank frame (one colour over 98.3% of the
160×144 screen).

> NOTE: the initial routing framing was "near-blank render" only (Failure B). Investigation
> in 12.10 then found Failure A (the switch itself is deterministically not completing in the
> harness). 12.11 owns BOTH.

## Evidence gathered in Phase 12.10 (decisive)

1. **Pre-existing, NOT a 12.10 regression.** Phase 12.10 is test-infrastructure only —
   the only non-`.planning` files changed are `StepAgent.kt`, `VisualDiff.kt`,
   `SettleCaptureTest.kt`, `VisualDiffTest.kt`, `PlatformerTemplateUatTest.kt` (anchor4
   only). The platformer-template ROM is byte-identical to pre-12.10. `anchor5LevelSwitch`
   and its `captureAndRename` / `assertScreenshotIsNonUniform` helpers were untouched.

2. **NOT fixable by the settle primitive (the 12.10 deliverable).** Throwaway experiment:
   inserting `agent.settle()` (advance until 2 consecutive identical framebuffers)
   immediately before the level-2 capture still produced 0.983 / 661-byte near-blank.
   `settle()` correctly reaches a STABLE frame — and the stable frame IS near-blank. So
   this is a real render defect, not a capture-timing race.

3. **Logic state is correct; only the framebuffer is blank.** The anchor5 variables trace
   at the level-2 capture shows the state machine arrived in level-2 gameplay:
   `final_current_level: 1`, `post_release_scene: gameplay`, `mid_level_2_scene: gameplay`,
   `grounded: 1`, `playerY: 1920`. The gameplay scene is active but its BG tilemap is not
   on screen → near-blank.

4. **Deterministic across 3 isolated reruns** (same ROM, same 0.983 ratio) — not flaky.

5. **Regressed since Phase 12.7.** The committed `03-level-2.png` evidence from Phase 12.7
   is 1606 bytes (non-blank — level-2 rendered correctly then). Sibling anchor5 frames
   (`00-last-gameplay.png` 1214 B, `01-nextlevel-flip.png` 1214 B, `02-nextlevel-card.png`
   1357 B) are still non-blank. Only `03-level-2.png` collapsed to near-blank.

6. **Phase 12.9 approved level-2 via MCP, not this harness.** STATE records Phase 12.9's
   G3 anchor-5 gates (which include level-2) were user-approved via *direct MCP-driven
   capture*, explicitly NOT the UAT test-harness PNG ("Coffee-GB capture timing is
   unreliable"). So the test-harness level-2 frame may have been near-blank since the 12.9
   codegen bundle landed, while the MCP capture showed a good frame.

## Hypotheses to investigate (12.11 spec/diagnose)

- The level-2 (`world2Area1Zone`) `setup_current_level()` path does not upload the level-2
  BG tilemap to VRAM in the harness emulator run (or uploads to the wrong region / wrong
  bank), leaving the BG plane cleared/near-blank.
- Possible interaction with the Phase 12.9 `setup_current_level` palette/zone-load changes
  (`set_bkg_palette`, `-keep_palette_order`, windowed submap write) or the 12.6 windowed
  `_bkg_set_level_submap_banked(0u,0u,21u,18u)` submap write for level-2's wider tilemap.
- Why MCP capture renders level-2 but the scripted harness run does not — divergence in
  frame cadence / DISPLAY_ON timing / button-press scheduling for the card→gameplay
  transition on the SECOND level specifically.

## What was done in Phase 12.10 (scoping decision)

User decision (2026-06-02): scope anchor5 OUT of 12.10 as an acknowledged pre-existing
exception to R-04 (parallel to the already-excepted inherited-14 IntegrationTest baseline),
ship 12.10's genuine deliverables (settle primitive + anchor4 retune + 7-target ROM sweep),
and route the level-2 render defect here to NEW sibling Phase 12.11.

In `PlatformerTemplateUatTest.anchor5LevelSwitch()` the single
`assertScreenshotIsNonUniform(..., "anchor5-level-2")` assertion was downgraded to a
documented non-blocking KNOWN-ISSUE record referencing this phase; the capture is still
taken (evidence preserved) and all other anchor5 assertions (logic state + the 3 non-blank
transition frames) remain armed. **Re-arm that assertion in Phase 12.11 once the render is
fixed.**

## Acceptance for 12.11 (proposed)

- `world2Area1Zone` (level-2) gameplay BG tilemap renders on screen in the
  `PlatformerTemplateUatTest` harness run (non-blank framebuffer).
- Re-arm `assertScreenshotIsNonUniform(..., "anchor5-level-2")` → GREEN.
- Visual confirmation per CLAUDE.md Visual Evidence Rule (screenshot or live confirmation).
- 7-target byte-identical regression check on the non-platformer-template games (the fix is
  codegen — platformer-template ROM is expected to change).
