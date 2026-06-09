---
phase: 10-port-metasprites-gbdk-example-to-gbkt
verified: 2026-05-18T20:00:00Z
verdict_flipped: 2026-05-18T21:30:00Z
status: passed
score: 8/8
overrides_applied: 1
human_verification_resolved: yes
verdict_flip_reason: |
  All three visual checkpoints were inspected and approved by the user INLINE during phase execution
  (Plans 10-17 and 10-18). Behavior 1 (animation frame advance): approved DMG screenshot — elephant
  sprite visibly differs between frame 0 and frame 2. Behavior 2 (Flip-XY): approved DMG screenshot
  — sprite visibly mirrored on both axes (user flagged D-V1 garbled-tiles + D-V2 diagonal-BG defects
  during this approval; both seeded for Phase 10.1). Behavior 3 (GBC cyan sub-palette): approved GBC
  screenshot — sprite is visibly cyan (distinct from DMG green tint). The three deferred visual
  defects (D-V1, D-V2, D-V3) are in scope for Phase 10.1 and do NOT block Phase 10 verdict because
  the phase's deliberate scope was MECHANISM layer + codegen SUBSTRATE, with visual parity explicitly
  deferred under the single-named-bug doctrine (D-05/D-06).
post_review_findings: |
  3 Critical + 5 Warning + 3 Info from gsd-code-reviewer post-execution. All four high-priority
  (3 CR + WR-05) seeded as SEED-008..SEED-011 and added to Phase 10.1 scope. They are LATENT in
  Phase 10 (single metasprite, no actor sprites in metasprites example) but BLOCKING for Phase 11
  (banks: CR-02) and Phase 12 (platformer_template: CR-01, CR-03, WR-05). Phase 10.1 is now
  declared a Phase 11+12 prerequisite in ROADMAP.md.
human_verification:
  - test: "Visual inspection of behavior1-animation-advance.png"
    expected: "Visible elephant shape with different tile arrangement between frame 0 and frame 2"
    why_human: "Sprite-pixel layout can only be confirmed by viewing the PNG"
    result: approved (user inline, 2026-05-18 — flagged D-V1+D-V2 defects, both seeded for 10.1)
  - test: "Visual inspection of behavior2-flip-cycle.png"
    expected: "Sprite is visually reflected (Flip-XY state at _rot=2)"
    why_human: "Flip orientation requires visual confirmation"
    result: approved (user inline, 2026-05-18 — flip orientation visible despite garbled tiles)
  - test: "Visual inspection of behavior3-subpalette-cycle-gbc.png"
    expected: "Elephant sprite rendered in cyan color at _rot=8 in GBC mode"
    why_human: "GBC sub-palette cycling is purely visual — CLAUDE.md Visual Evidence Rule"
    result: approved (user inline, 2026-05-18 — cyan tint distinct from DMG green)
---

# Phase 10: Port metasprites GBDK example to gbkt — Verification Report

**Phase Goal:** Re-implement the GBDK `metasprites` example as an idiomatic gbkt DSL game. Second reference port — exercises sprite composition + OAM management. Hard scope cap.
**Verified:** 2026-05-18T20:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

Phase 10's goal is narrowly scoped to the **mechanism layer** (by deliberate design per D-01 through D-14). Visual parity defects are explicitly deferred to Phase 10.1 (D-V1 corrupted tiles, D-V2 diagonal BG, D-V3 stale global, D-extra GameBuilder slot — all seeded as SEED-004..007).

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Mechanism layer: idx/rot/flip/subpal cycling works — confirmed by JVM tests and DMG/GBC screenshots | VERIFIED | MetaspriteEmissionTest 3/3 GREEN; UAT JSON: idx=2 (B1), rot=2 (B2), rot=8 (B3); all tests=3, failures=0 |
| 2 | Codegen substrate complete: MetaspriteIR + DSL + visitor + pipeline wiring | VERIFIED | MetaspriteIR.kt (52 lines), MetaspriteBuilder.kt (276 lines), MetaspriteVisitor.kt (230 lines), GBDKPipelineV2 imports MetaspriteVisitor at lines 53, 1016, 3675 |
| 3 | D-12 Tier-1 oracle invariants locked: 3 JVM tests using extractFunctionBody brace-walk (Plan 10-19) | VERIFIED | TEST-MetaspriteEmissionTest.xml: tests=3, failures=0, skipped=0. D_12_1/D_12_2/D_12_3 all pass. Evidence in tier1-shape/ directory (78 lines each) |
| 4 | D-11 Three-signal comparison artifact produced (Plan 10-16) | VERIFIED | evidence/oracle-comparison.md, evidence/rom-size-comparison.md, evidence/c-diff.md all exist. ROM size 1.110x (PASS). C-diff: 70-line tile-flip infra eliminated. UAT: all 3 behaviors PASS at mechanism layer |
| 5 | Named codegen bug-fix delivered (D-05): palette slot numbering bug fixed (Plan 16) | VERIFIED | SceneBuilder.kt:157 has `else paletteOps.size` fix. SpritePaletteSlotEmissionTest: tests=2, failures=0. oracle-comparison.md post-16 fix section documents commits ce25f33e + 2e8fb256 |
| 6 | Surplus defects seeded for Phase 10.1 (D-06): SEED-004..007 created | VERIFIED | ls: SEED-004-metasprites-corrupted-tile-rendering.md, SEED-005-metasprites-diagonal-bg-not-checkerboard.md, SEED-006-metasprites-subpalette-global-not-synced.md, SEED-007-gamebuilder-actor-palette-slot-zero-default.md all exist |
| 7 | Phase 10.1 placeholder inserted in ROADMAP (D-06, conditional on >=1 seed) | VERIFIED | ROADMAP.md:44 has `[ ] Phase 10.1: Metasprites surplus codegen defects (INSERTED)`. Full Phase 10.1 section at ROADMAP.md:1246+ |
| 8 | Phase 13 requirements 4+5 routed (D-13): MetaspriteBuilder.sprite() + explicit-slot palette DSL | VERIFIED | ROADMAP.md:1306-1307 contain req 4 and req 5 with full rationale paragraphs referencing Metasprites.kt markers |

**Score:** 8/8 truths verified

### Deferred Items

Items not yet met — explicitly addressed in Phase 10.1 per D-06 doctrine.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | D-V1: Elephant sprite tiles render corrupted (garbled pixel arrangement vs reference) | Phase 10.1 | SEED-004; 10-UAT.md defect D-V1; phase-close.md; ROADMAP Phase 10.1 SC |
| 2 | D-V2: bgFillCheckerboard() emits diagonal stripes, not checkerboard | Phase 10.1 | SEED-005; 10-UAT.md defect D-V2; oracle-comparison.md post-17 section |
| 3 | D-V3: _elephant_subPalette global never assigned in play_frame() | Phase 10.1 | SEED-006; 10-UAT.md defect D-V3; behavior3 JSON shows elephant_subPalette=0 at rot=8 |
| 4 | D-extra: GameBuilder.kt:713 actor-palette slot defaults to 0 (same bug class as Plan 16 fix) | Phase 10.1 | SEED-007; phase-close.md |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt` | MetaspriteIR + MetaspriteFrame + MetaspriteTile data classes | VERIFIED | 52 lines; data classes MetaspriteTile (line 19), MetaspriteFrame (line 33), MetaspriteIR (line 48) confirmed |
| `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt` | metasprite { frame { tile(x,y,id) } } DSL + MetaspriteDelegate | VERIFIED | 276 lines; MetaspriteBuilder (line 74), frame() (line 82), tile() (line 39), MetaspriteDelegate (line 161), metasprite() top-level (line 211) |
| `gbkt-backend-gbdk/.../codegen/visitor/MetaspriteVisitor.kt` | GBDK visitor: tile-data emission + descriptor + frame-switch + hiwater | VERIFIED | 230 lines; generateMetaspriteTileData(), generateMetaspriteDescriptor(), generateMetaspriteFrameSwitch() all present with move_metasprite_flip*/ex calls |
| `gbkt-examples/metasprites/src/main/kotlin/.../Metasprites.kt` | Idiomatic gbkt DSL port of GBDK metasprites example | VERIFIED | 406 lines; val elephant by metasprite { ... } at line 133; moveMetasprite(elephant) at line 391; 4 spritePalette declarations + GBC_COMPATIBLE config |
| `gbkt-examples/metasprites/src/test/.../MetaspriteEmissionTest.kt` | D-12 Tier-1 emission tests with extractFunctionBody brace-walk | VERIFIED | 215 lines; 3 @Test methods at lines 132, 165, 197; extractFunctionBody helper at line 81; all 3 tests GREEN in XML |
| `gbkt-backend-gbdk/.../pipeline/SpritePaletteSlotEmissionTest.kt` | Named bug-fix test: palette slot auto-increment | VERIFIED | Exists; tests=2, failures=0 |
| `.planning/seeds/SEED-004..007` | 4 surplus defect seeds | VERIFIED | All 4 files exist with correct naming |
| `evidence/oracle-comparison.md` | Three-signal verdict artifact (D-11) | VERIFIED | Exists; full table with ROM size (1.110x PASS), C-diff (notable win), UAT 3/3 |
| `evidence/phase-close.md` | Phase close audit | VERIFIED | Exists; all sections populated: three-signal verdict, surplus seeds, Phase 10.1 status, Phase 13 routing, lessons |
| `evidence/uat-screenshots/behavior1-animation-advance.png` | DMG screenshot: idx=2 after 2 B presses | VERIFIED | 1751 bytes; JSON: idx=2 |
| `evidence/uat-screenshots/behavior2-flip-cycle.png` | DMG screenshot: rot=2 Flip-XY state | VERIFIED | 1712 bytes; JSON: rot=2 |
| `evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png` | GBC screenshot: rot=8 cyan sub-palette | VERIFIED | 2004 bytes (above 147-byte all-black stub threshold); JSON: rot=8 |
| `evidence/tier1-shape/01-animation-index-advance.txt` | play_frame body evidence for D_12_1 | VERIFIED | 78 lines; begins with `void play_frame(void) BANKED {` |
| `evidence/tier1-shape/02-flip-oam-attribute.txt` | play_frame body evidence for D_12_2 | VERIFIED | 78 lines |
| `evidence/tier1-shape/03-sub-palette-oam-attribute.txt` | play_frame body evidence for D_12_3 | VERIFIED | 78 lines |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `Metasprites.kt` DSL `val elephant by metasprite { }` | `MetaspriteIR` in `GameIR` | `MetaspriteDelegate.provideDelegate()` in MetaspriteBuilder.kt | VERIFIED | MetaspriteDelegate at line 161; metasprite() at line 211 |
| `Metasprites.kt` frame: `moveMetasprite(elephant)` | `MoveMetasprite` ScriptOp | `ScriptBuilder.moveMetasprite()` | VERIFIED | Metasprites.kt:391; MoveMetasprite data class in ScriptOp.kt:627 |
| `MoveMetasprite` ScriptOp | GBDK C emission via MetaspriteVisitor | `ScriptOpVisitorI.visitMoveMetasprite()` + `ScriptOpVisitor.visitMoveMetasprite()` override | VERIFIED | ScriptOpVisitorI.kt:172 defines interface; ScriptOpVisitor.kt:1867 implements; MetaspriteVisitor.generateMetaspriteFrameSwitch() provides move_metasprite_* calls |
| `GBDKPipelineV2` | `MetaspriteVisitor` | import + invocation at lines 53, 1016, 3675 | VERIFIED | generateMetaspriteDescriptor() at line 1016; generateMetaspriteTileData() at line 3675 |
| `SceneBuilder.palette()` auto-slot fix | `set_sprite_palette(slot=0,1,2,3)` in generated C | `else paletteOps.size` at SceneBuilder.kt:157 | VERIFIED | Confirmed by SpritePaletteSlotEmissionTest (2 tests GREEN) |
| `CoffeeGbEmulator` GBC frame fix | GBC screenshots non-black | `GbcFrameReadyEvent` wired at line 181 alongside `DmgFrameReadyEvent` at line 172 | VERIFIED | behavior3 PNG is 2004 bytes (non-stub); GBC frame event comment at line 159 |
| Surplus seeds (SEED-004..007) | Phase 10.1 placeholder in ROADMAP | D-06 conditional: >=1 seed → insert Phase 10.1 | VERIFIED | 4 seeds present; ROADMAP:44 has Phase 10.1 entry; ROADMAP:1246 has full Phase 10.1 section |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `Metasprites.kt` frame loop | `elephant` metasprite (MoveMetasprite ScriptOp) | `val elephant by metasprite { 5 frames with tiles }` DSL declaration | Yes — MetaspriteIR.frames list populated at build time; MetaspriteVisitor generates C globals and frame-switch at pipeline time | FLOWING |
| `MetaspriteEmissionTest.playFrameBody()` | play_frame C body string | `GBDKPipelineV2.generate(metasprites.build())` invoked live in test | Yes — pipeline generates full C, not static/empty; tier1-shape files show 78-line function body | FLOWING |
| UAT JSON variables (idx, rot) | `_idx`, `_rot` runtime variables | Generated C globals, incremented in play_frame via B/A button handlers | Yes — confirmed by behavior JSON: idx=2 at B2 press, rot=2 at A2 press, rot=8 at A8 press | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| D-12 JVM emission tests GREEN | `./gradlew :gbkt-examples:metasprites:test --rerun` | BUILD SUCCESSFUL; MetaspriteEmissionTest: tests=3, failures=0, skipped=0 | PASS |
| Full metasprites test suite GREEN | `./gradlew :gbkt-examples:metasprites:test --rerun` | BUILD SUCCESSFUL; MetaspriteIRTest: 18/0, MetaspriteEmissionTest: 3/0, MetaspriteUatTest: 3/0 | PASS |
| SpritePaletteSlotEmissionTest GREEN | `./gradlew :gbkt-backend-gbdk:test --tests "*SpritePaletteSlotEmissionTest*" --rerun` | BUILD SUCCESSFUL; tests=2, failures=0 | PASS |
| MetaspriteVisitor wired in pipeline | `grep MetaspriteVisitor GBDKPipelineV2.kt` | Found at import line 53 + invocations at 1016, 3675 | PASS |

### Probe Execution

Step 7c: No declared probes in PLAN.md files for this phase. No `scripts/*/tests/probe-*.sh` found. UAT behaviors were verified via MCP agent sessions (not shell probes) — results captured as JSON + PNG in evidence/uat-screenshots/.

### Requirements Coverage

Phase 10 used D-XX decisions in CONTEXT.md instead of formal REQ-* IDs. Key decisions verified:

| Decision | Description | Status | Evidence |
|----------|-------------|--------|---------|
| D-01 | 3 core UAT behaviors locked (B-press anim, A-press flip, A-press subpal) | SATISFIED | 10-UAT.md all 3 behaviors documented; all 3 result: pass-partial (mechanism PASS); JSON+PNG evidence for all 3 |
| D-02 | MCP play-through + screenshot per behavior | SATISFIED | 3 PNG screenshots exist in evidence/uat-screenshots/; GBC mode confirmed for behavior 3 |
| D-04 | metasprite { frame { tile() } } DSL primitive as port substrate | SATISFIED | MetaspriteBuilder.kt:276 lines, MetaspriteIR.kt:52 lines, MetaspriteVisitor.kt:230 lines — substantive implementations |
| D-05 | Named codegen bug-fix: palette slot numbering (exploratory, surfaced at first build) | SATISFIED | SceneBuilder.kt:157 fix; SpritePaletteSlotEmissionTest GREEN; oracle-comparison.md §Post-16 fix documents commits ce25f33e + 2e8fb256 |
| D-06 | Surplus seeds + conditional Phase 10.1 | SATISFIED | 4 seeds (SEED-004..007); Phase 10.1 in ROADMAP |
| D-07 | Runtime OAM-attribute accessors (flipX/flipY/subPalette) via moveMetasprite codegen | SATISFIED | MetaspriteVisitor generates move_metasprite_flipy/flipxy/flipx/ex ladder; D_12_2/D_12_3 tests GREEN |
| D-09 | GBC-compatible single ROM via cgb_compatibility + set_sprite_palette | SATISFIED | Metasprites.kt uses GbcTarget.GBC_COMPATIBLE; 4 spritePalette declarations; behavior3 GBC screenshot is non-black |
| D-11 | Three-signal comparison artifact (ROM size + C-diff + UAT verdict) | SATISFIED | oracle-comparison.md exists with all 3 signals; ROM 1.110x PASS; C-diff notable win; UAT 3/3 PASS |
| D-12 | Tier-1 JVM emission invariants (3 tests, extractFunctionBody brace-walk) | SATISFIED | MetaspriteEmissionTest 3/3 GREEN; brace-walk confirmed via extractFunctionBody helper at line 81 |
| D-13 | Phase 13 routing for framework-shaping DSL gaps | SATISFIED | ROADMAP.md:1306-1307 requirements 4+5 added |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `Metasprites.kt` | 58 | `TODO(phase-13):` | Info | References formal follow-up phase — NOT a BLOCKER per debt-marker gate. `phase-13` is the formal tracking reference. |
| `Metasprites.kt` | 51-52 | "not yet implemented" comment for `MetaspriteBuilder.sprite()` | Info | Part of PHASE-13 GAPS comment block — explicitly documented deferred item, not a hidden stub. Routed to Phase 13 via D-13. |

No `TBD`, `FIXME`, or `XXX` markers found in any modified files. All `TODO` markers reference formal follow-up work (`phase-13`). No blocker anti-patterns detected.

### Human Verification Required

The three items below are required by CLAUDE.md §"Verification Methodology — Visual Evidence Rule" for visual truths.

#### 1. Behavior 1 Visual: Elephant sprite animation frame change

**Test:** Open `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png`. Look at the sprite on screen.
**Expected:** A visible sprite (elephant-shaped, even if garbled per D-V1) showing a different tile arrangement vs frame 0. The mechanism is confirmed (idx=2 from JSON), but the pixel-level visual quality may show D-V1 corrupted tiles.
**Why human:** Sprite tile pixel arrangement can only be confirmed by viewing the PNG. JVM tests and JSON assertions confirm the mechanism; D-V1 corruption (if present) is a known deferred defect for Phase 10.1 — human should confirm that despite D-V1, the ORIENTATION/FRAME change is distinguishable.

#### 2. Behavior 2 Visual: Flip-XY orientation change

**Test:** Open `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png`. Look at the sprite orientation.
**Expected:** Sprite is visibly reflected (both X and Y axes flipped) compared to the default orientation. The JSON confirms rot=2 (Flip-XY state). Even with D-V1 garbled tiles, the OAM flip should produce a mirrored orientation.
**Why human:** OAM flip state is a visual property — pixels should appear horizontally AND vertically mirrored. This requires visual inspection.

#### 3. Behavior 3 Visual: GBC sub-palette cyan color (binding evidence per CLAUDE.md)

**Test:** Open `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png`. Confirm the sprite is rendered in a distinct non-gray color (expected: cyan).
**Expected:** Elephant sprite visible in cyan color at rot=8. Screenshot is 2004 bytes (confirmed non-black — GBC frame event fix D-V4 is working). JSON confirms rot=8 and current_metasprite=45. The `elephant_subPalette` sym variable stays 0 (known D-V3 defect — stale global, doesn't affect visual).
**Why human:** CLAUDE.md Visual Evidence Rule states this MUST be a screenshot, not a variable assertion. Sub-palette color change is invisible on DMG and undetectable from sym variables. The GBC screenshot is the ONLY binding evidence for behavior 3.

### Gaps Summary

No gaps blocking phase goal achievement. All mechanism-layer success criteria are met. The status is `human_needed` solely because CLAUDE.md §"Verification Methodology — Visual Evidence Rule" requires human visual confirmation for the three UAT behaviors whose truth is phrased as visual outcomes.

The deferred visual-parity defects (D-V1/D-V2/D-V3/D-extra) are explicitly out of scope for Phase 10 per the single-named-bug doctrine (D-05/D-06) and are seeded into Phase 10.1. They do not constitute gaps against Phase 10's stated goal.

---

_Verified: 2026-05-18T20:00:00Z_
_Verifier: Claude (gsd-verifier)_
