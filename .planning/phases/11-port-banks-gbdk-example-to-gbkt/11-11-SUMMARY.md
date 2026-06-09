---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 11
subsystem: examples/banks (UAT runtime tests)
tags: [uat, screenshots, visual-evidence, cross-bank-navigation, banked-tilemap]
requires:
  - 11-01-wave0-scaffold (BanksUatTest skeleton, newAgent helper)
  - 11-02-uat-contract (11-UAT.md reserved screenshot paths)
  - 11-03-playbook (PLAYBOOK.md anchor scripts)
  - 11-05-banks-dsl (Banks.kt: title/play/pause scenes + play_zone)
  - 11-10-named-bug-fix (trigger_saves stub — ROM must build)
provides:
  - "@Test methods anchor 1 + 2 (cross-bank scene nav + banked tilemap visible)"
  - "Binding PNG evidence at evidence/uat-screenshots/anchor1-play-scene.png and anchor2-tilemap.png"
  - "captureAndRename helper (mirrors SimplePhysicsUatTest pattern)"
affects:
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
tech-stack:
  added: []
  patterns:
    - "StepAgent.use { } AutoCloseable session block (SimplePhysicsUatTest analog)"
    - "captureAndRename: post-capture rename to plan-reserved file name"
    - "waitForScene(name, maxFrames) for cross-bank trampoline observation"
    - "Visual Evidence Rule conformance: PNG is binding evidence, variable assertion is secondary"
key-files:
  created: []
  modified:
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
  evidence-created:
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png (413 bytes)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.json (504 bytes, sidecar)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png (413 bytes)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.json (501 bytes, sidecar)
decisions:
  - "Two `@Test` methods committed atomically (shared helper + imports), not split across two commits — file edit was atomic; per-task split would have required artificial unwinding of shared infrastructure (helper/imports)."
  - "Evidence PNGs + sidecars committed as a separate commit from the test code — binding artifacts vs source code, distinct conceptual layers."
  - "Test passes by automated criteria (PNG signature + size floor) BUT visual content does not appear to satisfy anchor intent — surfaced to human-verify checkpoint per plan Task 3 (correct gating semantics)."
metrics:
  duration_seconds: 285
  duration_minutes: 5
  completed: 2026-05-20
  commits: 2
  tasks_completed: 2
  tasks_remaining: 1
  tasks_total: 3
---

# Phase 11 Plan 11: uat-anchor1-anchor2 Summary

UAT runtime evidence capture for Banks port — two `@Test` methods exercising the
cross-bank BANKED trampoline (anchor 1) and the SWITCH_ROM-from-HOME banked-tilemap
wrapper (anchor 2), each producing a binding PNG artifact at the reserved
`evidence/uat-screenshots/` path per CLAUDE.md Visual Evidence Rule. The
checkpoint:human-verify gate (Task 3) is reached after both `@Test` methods land
GREEN at the automated tier — a flagged visual concern about screenshot blankness
is escalated to the human reviewer rather than auto-remediated, per the plan's
explicit resume-signal options and `feedback_visual_evidence_for_visual_truths.md`
user-memory rule.

## Objective Recap

> Implement UAT anchors 1 (cross-bank scene navigation) and 2 (banked zone tilemap
> load) as JVM tests in `BanksUatTest.kt`, producing screenshot evidence per
> CLAUDE.md Visual Evidence Rule.

## What Was Built

### Task 1 — Anchor 1 `@Test` method (cross-bank scene navigation)

Added `@Test fun anchor 1 cross-bank scene navigation` to
`BanksUatTest.kt`. Flow:

1. `agent.stepN(10)` boots to title scene.
2. `agent.step(setOf(Button.START))` fires the edge-triggered HOME→bank-1
   BANKED trampoline via `whenever(buttons.start.pressed) { navigate("play") }`
   in Banks.kt.
3. `agent.waitForScene("play", maxFrames = 60)` reads `_current_scene` against
   the scene-id map from `game_metadata.json` and returns the matching
   `Observation`.
4. `agent.stepN(2)` flushes PPU frames so the captured frame is post-enter.
5. `captureAndRename(agent, "anchor1_play_scene", "anchor1-play-scene.png")`
   captures via `StepAgent.captureScreenshot()` into `EVIDENCE_DIR` and renames
   to the plan-reserved file name.
6. Asserts: PNG file exists + size > 100 bytes + `Observation.scene == "play"`.

The PNG is the binding evidence per the Visual Evidence Rule; the
`Observation.scene` equality assertion is intentionally labelled as
secondary/necessary-not-sufficient via inline comment.

### Task 2 — Anchor 2 `@Test` method (banked zone tilemap visible)

Added `@Test fun anchor 2 banked zone tilemap visible` to the same file. Same
boot/title/Start setup as anchor 1, then:

1. `agent.stepN(30)` allows the SWITCH_ROM-from-HOME wrapper
   (`_bkg_tiles_load_banked` → SWITCH_ROM(2) → set_bkg_tiles(...) →
   SWITCH_ROM(1)) to complete and the checker tilemap pixels to reach VRAM.
2. `captureAndRename(agent, "anchor2_tilemap", "anchor2-tilemap.png")` captures
   the post-zone-load PPU state.
3. Asserts: PNG file exists + size > 200 bytes.

Per the plan: anchor 2 does NOT separately assert `_current_tileset_id` or
`_current_zone` — that is the explicit Visual Evidence Rule anti-pattern (Phase
07.4 round-2 history). The screenshot IS the evidence.

### Supporting helpers + imports

- `captureAndRename(agent, label, targetName)` — mirrors the
  `SimplePhysicsUatTest` (Phase 09.4 Plan 02) helper that renames
  `StepAgent.captureScreenshot()`'s `{label}_frameN.png` output to the
  plan-reserved file name. Sidecar JSON is renamed in lock-step.
- Imports added: `io.github.gbkt.emulator.agent.Button`,
  `kotlin.test.assertEquals`, `kotlin.test.assertTrue`.

### ROM build prerequisite (in-worktree)

`:gbkt-examples:banks:buildRom` ran cleanly inside the worktree to materialise
`gbkt-examples/banks/build/gbkt/output/banks.gb` (65 536 bytes) +
`build/gbkt/generated/game_metadata.json`. Banking report:

```
4 banks (highest bank: 2), MBC: 0x1B
RAM banks: 2
```

Matches D-07 (MBC5_RAM_BATTERY = 0x1B) and the zone-in-bank-2 expectation from
Phase 11 D-05 / Plan 07.4-30. Build artifacts are NOT committed (gradle-output).

### Automated test verdict

```
TESTSUITE io.github.gbkt.examples.banks.BanksUatTest
tests="2" skipped="0" failures="0" errors="0"
```

Both anchors GREEN at the automated tier. Acceptance criteria check:

| Criterion                                                          | Status |
|--------------------------------------------------------------------|--------|
| `./gradlew :gbkt-examples:banks:test --tests "*BanksUatTest*"` 0   | PASS   |
| PNG `anchor1-play-scene.png` exists, valid signature, >100 bytes   | PASS (413 bytes, `89 50 4E 47 …`) |
| PNG `anchor2-tilemap.png` exists, valid signature, >200 bytes      | PASS (413 bytes, `89 50 4E 47 …`) |
| File contains literal `anchor1-play-scene.png`                     | PASS   |
| File contains literal `waitForScene("play"` AND `Button.START`     | PASS   |
| File contains literal `anchor2-tilemap.png`                        | PASS   |
| File contains literal `tilemap pixels`                             | PASS (inside anchor 2 assertion message) |
| Total `@Test` count = 2 (Plan 11-12 adds anchor 4)                 | PASS   |

## Key Files

| File | Purpose | Change |
|---|---|---|
| `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` | UAT test class | +151 / −3 lines: helper + 2 `@Test` methods + 3 imports |
| `.planning/phases/11-.../evidence/uat-screenshots/anchor1-play-scene.png` | Anchor 1 binding evidence | NEW, 413 bytes |
| `.planning/phases/11-.../evidence/uat-screenshots/anchor1-play-scene.json` | Sidecar metadata | NEW, 504 bytes |
| `.planning/phases/11-.../evidence/uat-screenshots/anchor2-tilemap.png` | Anchor 2 binding evidence | NEW, 413 bytes |
| `.planning/phases/11-.../evidence/uat-screenshots/anchor2-tilemap.json` | Sidecar metadata | NEW, 501 bytes |

## Commits

| Hash       | Message |
|-----------|---|
| `9d812a46` | `test(11-11): add UAT anchor 1+2 @Test methods to BanksUatTest` |
| `f352d53a` | `test(11-11): capture anchor 1+2 UAT screenshot evidence` |

Two-commit shape:
- Commit 1 = test source (single file, single edit, both anchors).
- Commit 2 = binding evidence artifacts (PNG + sidecar JSON).

This split keeps the source-code commit reproducible (re-running
`buildRom` + `:test` regenerates the PNGs) and the evidence commit
explicitly traceable as a runtime artifact rather than source.

## Decisions Made

1. **Two `@Test` methods committed atomically (one commit, not two).** The plan
   structures Tasks 1 and 2 as separate units, but both modify the same file and
   share infrastructure (helper, imports). Splitting would require artificial
   unwinding (commit anchor 1 → re-add helper imports → commit anchor 2). Per
   the per-task-commit rule's spirit (atomic, reproducible, conceptually
   coherent), one combined `test(11-11)` commit is the honest shape.
2. **Evidence PNGs + sidecars committed separately from test code.** Source
   code and binding runtime artifacts are conceptually distinct layers. The
   PNGs are regenerated on every `:test` run; the source-code commit therefore
   stays clean of binary churn while the evidence commit preserves the
   capture-point pixels for the human-verify checkpoint reviewer.
3. **Did NOT auto-fix the visual-content blankness concern (see "Known
   Concerns" below).** Per Rule 4 (architectural changes need user permission)
   and the plan's explicit resume-signal options ("re-run with stepN(60)
   instead of 30", "wait-for-scene timeout too short"), the binding visual
   decision belongs to the human reviewer — not to auto-remediation. Auto-fixing
   would also violate `feedback_visual_evidence_for_visual_truths.md`
   ("variable evidence alone is never sufficient").

## Deviations from Plan

None. Plan executed as written. The PNG screenshot artifacts are captured at the
exact plan-reserved paths; both `@Test` methods carry the literal-string
acceptance-criteria markers (`anchor1-play-scene.png`, `waitForScene("play"`,
`Button.START`, `anchor2-tilemap.png`, `tilemap pixels`). The captureAndRename
helper added is explicitly anticipated by the plan's `<read_first>` reference to
`SimplePhysicsUatTest`.

## Known Concerns (escalated to human-verify checkpoint, Task 3)

**Both screenshot PNGs render as solid pale-green DMG frames** (LCD palette
"lightest" color — no visible sprites, tilemap, or scene content). Both files
are exactly 413 bytes — symptomatic of a monochrome frame compressing to its
minimum PNG size.

This is **not** a test failure at the automated tier:
- Anchor 1's automated acceptance (PNG exists, >100 bytes, scene == "play",
  signature valid) PASSES.
- Anchor 2's automated acceptance (PNG exists, >200 bytes, signature valid)
  PASSES.

But it IS exactly the class of issue the Visual Evidence Rule + Task 3
human-verify checkpoint exist to catch:

- The plan's Task 3 `<how-to-verify>` explicitly enumerates "A blank Game Boy
  screen is suspect" for anchor 1 and "A blank screen or all-white tiles
  indicates the SWITCH_ROM-from-HOME wrapper didn't fire OR the tileset asset
  reference is broken" for anchor 2.
- The plan's Task 3 `<resume-signal>` explicitly enumerates options for this
  case: "anchor 2 screenshot is blank — re-run with stepN(60) instead of 30",
  "anchor 1 is on title screen — wait-for-scene timeout too short".

Possible diagnoses (the human reviewer decides):
1. **Timing / PPU flush** — `stepN(2)` (anchor 1) or `stepN(30)` (anchor 2) may
   be too short; longer step counts may surface visible pixels.
2. **DSL substrate gap** — Banks.kt's play scene calls `showSprites()` in
   `enter { }` but Banks.kt declares NO actors/sprites. The play scene also
   doesn't explicitly activate the `play_zone` zone (no `zone(play_zone)`-style
   call in `enter { }`), so the SWITCH_ROM-from-HOME wrapper may not fire.
   This may require a Banks.kt substrate update (Plan 11-05 re-spin or a new
   plan inserted ahead of 11-11).
3. **Runtime codegen gap** — the cross-bank trampoline may LAND on the play
   scene (per `Observation.scene == "play"` GREEN) but not actually load any
   pixels. This was the literal Phase 07.4 SC-4 failure mode.

**This is the right place to stop and surface the issue** rather than try
remediation inline. Per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`:
if the runtime never renders the play scene's content, the fix likely has wide
blast radius across Banks.kt + zone-activation codegen and belongs in either a
Plan 11-05 re-spin or a new plan inserted ahead of 11-11, not in 11-11 itself.

## Threat Flags

None. No new network endpoints, auth paths, file access at trust boundaries, or
schema changes were introduced. PNG writes are confined to the phase evidence
directory (per CLAUDE.md trust-boundary model: emulator → host filesystem under
`evidence/` is `accept`-disposition; the threat register T-11-23 "tampering via
pre-generated screenshot" is mitigated because PNGs are written in-line by
`agent.captureScreenshot()` — there is no other write path).

## Success Criteria Check

- [x] 2 `@Test` methods in BanksUatTest (anchor 1 + anchor 2) — verified via XML
      test report `tests="2"`.
- [x] 2 PNG evidence files at plan-reserved paths — both exist with valid PNG
      signatures.
- [ ] 11-UAT.md anchors 1 + 2 Result fields updated to `passed` — **DEFERRED to
      Task 3 human-verify checkpoint**. The Result flip is gated on the visual
      verification, NOT on the automated tests. Per plan: "Type `approved` to
      confirm both anchors are visually valid … On approval, mark anchors 1 + 2
      GREEN in 11-UAT.md".

## Self-Check: PASSED

- File `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt`
  exists and contains the two `@Test` methods.
- File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png`
  exists (413 bytes, valid PNG signature).
- File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor2-tilemap.png`
  exists (413 bytes, valid PNG signature).
- Commit `9d812a46` found in `git log` (test code).
- Commit `f352d53a` found in `git log` (evidence PNGs).
