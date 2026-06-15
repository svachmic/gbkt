# Phase 22: Golden Screenshot and Evidence Storage Overhaul — Specification

**Created:** 2026-06-14
**Ambiguity score:** 0.113 (gate: ≤ 0.20)
**Requirements:** 7 locked

## Goal

This milestone's visual evidence becomes durable, immutable, ROM+anchor-keyed PNG goldens that UAT tests DIFF against (exact match, failing on mismatch), the per-phase `EVIDENCE_DIR` pattern is eliminated from all 33 UAT/emission test classes, `.planning/phases/**/evidence/` is gitignored scratch, and GBC-vs-DMG capture mode is derived from the ROM CGB-flag byte — so a clean `./gradlew test` leaves zero new untracked files and zero modified committed evidence.

## Background

UAT/emission evidence currently plays two conflicting roles at once: immutable golden baseline AND live test scratch output. 33 test classes (`src/test`) hardcode an `EVIDENCE_DIR` companion pointing at `.planning/phases/<originating-phase>/evidence`. They split into two kinds:

- **Visual UAT tests** (e.g. `Phase19VisualEvidenceTest`, `BanksUatTest`, `PlatformerTemplateUatTest`) capture 160×144 PNG screenshots via `ScreenshotCapture.capture()`.
- **Emission tests** (e.g. `BanksEmissionTest`) dump `.txt` C-code snippets purely as human-readable artifacts; the in-test JVM assertion on the generated C is already the real gate.

148 evidence files are tracked in git (38 PNG, 110 text). Three recurring failure modes result:

1. **Sidecar timestamp churn.** `ScreenshotCapture.capture()` writes a `.json` sidecar with `"capturedAt": System.currentTimeMillis()` (`ScreenshotCapture.kt:106`). The PNG is deterministic; the sidecar is not. A single `./gradlew test` on 2026-06-14 re-dirtied 16 committed sidecars across the closed Phase 20 and Phase 21.
2. **Archived-phase regeneration.** Tests pinned to archived v0.1.0 phase dirs (07.9/09/10/11/12.x/13.5) regenerate those dirs as untracked garbage on every run, making "old phases resurface" in `git status`.
3. **Mode/baseline drift.** `AgentSessionConfig.discoverFiles()` (`AgentSessionConfig.kt:63`) wires the `.noi` symFile but never sets `gbcMode` (defaults `false` → DMG). A GBC ROM captured in DMG mode renders inverted and reads as a false palette regression — hit in Phase 13.5 (false D-11) and twice in Phase 21-07. Phase 21 worked around it with a per-test `.copy(gbcMode = true)` (commit `71dd3a57`).

The fix belongs in v0.1.1 because the visual+emission evidence IS this milestone's deliverable — every codegen fix is proven by it. USER decisions were locked 2026-06-14 (see `22-SEED-SOURCE.md` and `[[project_golden_screenshot_storage_decision]]`).

## Requirements

1. **EVIDENCE_DIR elimination**: No test writes evidence into a `.planning/phases/**/evidence` path.
   - Current: 33 `src/test` classes hardcode an `EVIDENCE_DIR` companion resolving to `.planning/phases/<phase>/evidence`
   - Target: The per-phase `EVIDENCE_DIR` constant is removed from all 33 classes; output is redirected to a central location (visual goldens) or gitignored `build/` scratch (emission text)
   - Acceptance: `grep -rl "EVIDENCE_DIR" --include="*.kt" | grep /src/test/` returns zero files; no test references a `.planning/phases/**/evidence` path

2. **Immutable visual goldens with exact-match diff**: Visual UAT PNG anchors become committed read-only goldens that tests diff against.
   - Current: UAT tests overwrite their committed PNG evidence in-place on every run
   - Target: PNG goldens live in one tracked top-level location keyed by ROM + anchor; UAT tests capture to a gitignored scratch dir and DIFF against the committed golden by exact byte/pixel equality, failing the test on mismatch
   - Acceptance: A UAT test fails when its captured PNG differs by ≥1 pixel from the golden; passes on an exact match; a normal test run does not modify any committed golden

3. **Emission test scratch redirect**: Emission tests stop polluting phase dirs; no golden-diff is added for text.
   - Current: Emission tests write `.txt` C-code dumps into `.planning/phases/**/evidence`
   - Target: Emission tests write their `.txt` dumps to gitignored `build/` scratch only; the existing in-test C assertion remains the gate; no committed text golden and no text diff is introduced
   - Acceptance: Emission tests write no files under `.planning/`; their `.txt` artifacts appear only under a gitignored `build/` path; the tests still pass/fail on their in-test C assertions

4. **Explicit, reviewed re-baselining**: Re-blessing a golden is never a side effect of a normal test run.
   - Current: Goldens are overwritten implicitly by any `./gradlew test`
   - Target: Re-baselining requires an explicit, opt-in action (e.g. a dedicated gradle flag/task); without it, a normal run only reads goldens
   - Acceptance: Running `./gradlew test` without the re-baseline flag never writes or modifies a committed golden; running the explicit re-baseline action regenerates the golden(s)

5. **GBC auto-detect from ROM CGB flag**: Capture mode is derived from the ROM, not configured per-test.
   - Current: `AgentSessionConfig.discoverFiles()` never sets `gbcMode` (defaults `false`); Phase 21 added a per-test `.copy(gbcMode = true)` workaround (`71dd3a57`)
   - Target: `discoverFiles` reads the ROM CGB-flag byte at `0x143` (∈ {0x80, 0xC0}) and sets `gbcMode` automatically; the Phase 21 `.copy(gbcMode = true)` workaround is removed; GBC-target tests ALSO assert the ROM is GBC so a mis-built DMG ROM fails loudly
   - Acceptance: `discoverFiles` on a GBC ROM yields `gbcMode = true` and on a DMG ROM yields `false`; the `.copy(gbcMode = true)` call is gone; a GBC-target example captures render with correct (non-inverted) palette; feeding a DMG ROM to a GBC-target test fails with a clear GBC-header assertion message

6. **Migration of binding goldens + scratch gitignore**: The milestone's blessed visual anchors are preserved; everything else under phase evidence becomes scratch.
   - Current: Phase 19/20/21 binding anchors and ~145 other evidence files are committed inside per-phase `evidence/` dirs
   - Target: Only the Phase 19 (metasprite cyan-elephant), Phase 20 (banks/tRNS), and Phase 21 (platformer GBC anchors) blessed visual goldens are migrated into the central goldens dir as the baselines; all other tracked `.planning/phases/**/evidence` files are `git rm`'d; `.planning/phases/**/evidence/` and `build/**/screenshots/` are gitignored
   - Acceptance: The 19/20/21 blessed anchors exist in the central goldens dir; `git ls-files ".planning/phases/**/evidence/**"` returns zero files; `.gitignore` ignores `.planning/phases/**/evidence/` and the `build/` screenshot scratch path

7. **Documentation**: TESTING.md documents the new scheme.
   - Current: TESTING.md describes the per-phase evidence convention
   - Target: TESTING.md documents the central goldens layout (ROM+anchor keying), the capture-to-scratch + diff flow, and the explicit re-baseline command
   - Acceptance: TESTING.md contains a section describing the goldens dir layout and the re-baseline command; no stale reference to the per-phase `EVIDENCE_DIR` convention remains

## Boundaries

**In scope:**
- Removing the `EVIDENCE_DIR` companion from all 33 `src/test` classes
- A central, tracked, ROM+anchor-keyed goldens directory for visual PNG anchors
- A golden-diff helper (exact PNG match) wired into the UAT capture flow
- Redirecting emission-test `.txt` dumps to gitignored `build/` scratch (no text golden)
- An explicit re-baseline action (gradle flag/task)
- `AgentSessionConfig.discoverFiles` reading ROM byte `0x143` to set `gbcMode`; removing the Phase 21 `.copy(gbcMode = true)`; GBC-header assertion in GBC-target tests
- Dropping `capturedAt` churn (drop the field or stop committing sidecars)
- Migrating ONLY the Phase 19/20/21 blessed visual anchors into the central goldens dir
- `git rm` of all other tracked `.planning/phases/**/evidence` files
- `.gitignore` rules for `.planning/phases/**/evidence/` and `build/` screenshot scratch
- TESTING.md update (layout + re-baseline command)

**Out of scope:**
- Golden-diffing or committing emission `.txt` C-code dumps — the in-test C assertion is already the gate (USER decision: redirect scratch only)
- Migrating archived v0.1.0-phase evidence or non-19/20/21 PNGs as goldens — they become gitignored scratch / are git-rm'd (USER decision: 19/20/21 visual anchors only)
- Perceptual-tolerance PNG diffing — exact match is required; toolchain non-determinism affects ROM binaries, not generated PNGs (USER decision)
- Fixing pong-class ROM-hash non-determinism — affects `.gb` binaries, not PNGs; tracked separately ([[project_pong_toolchain_nondeterminism]])
- New codegen/visual fixes — this phase changes evidence STORAGE only, not what any ROM renders
- Changing the MCP/emulator runtime behavior beyond `discoverFiles` GBC detection

## Constraints

- PNG golden comparison MUST be exact byte/pixel match — no tolerance threshold (seed confirms generated PNGs are deterministic; non-determinism is confined to ROM binaries).
- The CGB-flag read MUST use ROM offset `0x143` with values `0x80` (GBC-enhanced) and `0xC0` (GBC-only) → `gbcMode = true`.
- Re-baselining MUST be opt-in (explicit flag/task), never triggered by a plain `./gradlew test`.
- Migration MUST preserve the exact bytes of the 19/20/21 blessed anchors (they are binding, USER-signed-off baselines — no re-render).
- Standard project conventions: per-commit `:module:spotlessApply :module:detekt`; codegen-touching changes require a clean `:gbkt-examples:<game>:buildRom` smoke per [[feedback_rom_build_smoke_test_for_codegen_phases]].

## Acceptance Criteria

- [ ] No `src/test` class references `EVIDENCE_DIR` or a `.planning/phases/**/evidence` path
- [ ] Visual UAT tests capture to gitignored scratch and DIFF against a committed ROM+anchor-keyed golden, failing on any pixel mismatch
- [ ] A normal `./gradlew test` modifies zero committed goldens and creates zero new untracked files (no `capturedAt` churn, no archived-phase dir regeneration)
- [ ] An explicit re-baseline action exists and is the only way a normal workflow updates a golden
- [ ] Emission tests write their `.txt` dumps only under gitignored `build/` scratch; in-test C assertions still gate
- [ ] `AgentSessionConfig.discoverFiles` sets `gbcMode` from ROM byte `0x143`; the Phase 21 `.copy(gbcMode = true)` is removed
- [ ] A GBC-target test fed a DMG ROM fails with a clear GBC-header assertion; GBC examples capture non-inverted palettes
- [ ] The Phase 19/20/21 blessed anchors exist (byte-identical) in the central goldens dir
- [ ] `git ls-files ".planning/phases/**/evidence/**"` returns zero files; `.gitignore` ignores `.planning/phases/**/evidence/` and the `build/` screenshot scratch
- [ ] TESTING.md documents the goldens layout and the re-baseline command with no stale `EVIDENCE_DIR` reference

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                              |
|--------------------|-------|------|--------|----------------------------------------------------|
| Goal Clarity       | 0.92  | 0.75 | ✓      | Precise storage-overhaul goal, no render changes   |
| Boundary Clarity   | 0.90  | 0.70 | ✓      | Emission=redirect-only; migrate 19/20/21 only      |
| Constraint Clarity | 0.85  | 0.65 | ✓      | Exact PNG match; 0x143 GBC detect; opt-in rebaseline|
| Acceptance Criteria| 0.85  | 0.70 | ✓      | 10 pass/fail criteria                              |
| **Ambiguity**      | 0.113 | ≤0.20| ✓      | Gate passed after 1 round                          |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

## Interview Log

| Round | Perspective     | Question summary                                  | Decision locked                                              |
|-------|-----------------|---------------------------------------------------|--------------------------------------------------------------|
| 0     | Researcher      | (USER pre-locked decisions in seed, 2026-06-14)   | Immutable goldens + diff; central ROM+anchor dir; GBC auto-detect from 0x143 |
| 1     | Boundary Keeper | How treat emission-test `.txt` dumps?             | Redirect to gitignored `build/` scratch only; no text golden; in-test C assertion stays gate |
| 1     | Boundary Keeper | How wide is golden migration of tracked evidence? | Only Phase 19/20/21 blessed visual anchors; everything else `git rm`'d + gitignored |
| 1     | Failure Analyst | PNG diff strictness + GBC-assert stretch goal?    | Exact byte/pixel match; GBC-header assertion IS in scope     |

---

*Phase: 22-golden-screenshot-and-evidence-storage-overhaul*
*Spec created: 2026-06-14*
*Next step: /gsd-discuss-phase 22 — implementation decisions (goldens dir path, diff helper API, re-baseline flag shape, sidecar disposition)*
