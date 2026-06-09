---
phase: 09-port-simple-physics-gbdk-example-to-gbkt
plan: 01
subsystem: testing
tags: [uat, mcp, playbook, screenshot, visual-evidence, gbdk-reference-port]

# Dependency graph
requires:
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt
    provides: "09-CONTEXT.md (D-01/D-02/D-03 locked decisions), 09-RESEARCH.md (3 MCP input scripts verbatim), 09-PATTERNS.md (07.4-UAT.md + shmup PLAYBOOK.md analogs)"
  - phase: 07.4-sport-genre-codegen-fix-inserted
    provides: "07.4-UAT.md frontmatter + Current Test + Diagnosis + Tests structural pattern (analog for 09-UAT.md shape)"
provides:
  - "09-UAT.md MCP harness contract — 3 D-01 test blocks (accel+clamp, jump impulse, decel-to-rest)"
  - "Each test block declares: expected, result: pending, evidence path under evidence/uat-screenshots/, mcp_script with emulator_start/step/screenshot/assert"
  - "Visual Evidence Rule annotation per CLAUDE.md — variable assertions are necessary but never sufficient"
  - "Anti-overfitting annotation per D-overfitting-1/2/3 — walk+jump beyond 3 behaviors explicitly OUT of scope"
  - "gbkt-examples/simple-physics/PLAYBOOK.md — 8-section player-facing reference with Controls + Variables + MCP Input Scripts"
  - "3 MCP input scripts in PLAYBOOK.md ready to drive gbkt-play-game / gbkt-test-game skills"
affects:
  - "09-02-PLAN (DSL port — must implement the 3 behaviors locked here; sprite update sync workaround called out in Known Quirks)"
  - "09-03-PLAN (first-build analysis — named-bug candidates surfaced via these UAT scripts)"
  - "09-04-PLAN (named bug fix — RED test corresponds to D-11.1 emission invariant, GREEN tracks Behavior 1 clamp)"
  - "09-05-PLAN (three-signal comparison — UAT verdict consumes evidence/uat-screenshots/*.png)"
  - "09-06-PLAN (UAT run — overwrites 09-UAT.md Current Test/Diagnosis sections with results)"
  - "Phase 10/11/12 reference ports (inherit the UAT-first + per-port PLAYBOOK MCP scripts pattern)"

# Tech tracking
tech-stack:
  added: []  # No new dependencies — pure markdown contract
  patterns:
    - "Phase-local UAT contract document (09-UAT.md) modeled on 07.4-UAT.md, locked BEFORE any DSL"
    - "PLAYBOOK.md MCP Input Scripts section — Phase 9-specific extension to the standard shmup-style PLAYBOOK shape"
    - "Visual Evidence Rule referenced verbatim at the top of every per-phase UAT contract"

key-files:
  created:
    - ".planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-UAT.md"
    - "gbkt-examples/simple-physics/PLAYBOOK.md"
  modified: []

key-decisions:
  - "Locked 3 D-01 behaviors verbatim into 09-UAT.md test blocks before any DSL was authored — satisfies D-03 ordering rail"
  - "Declared evidence/uat-screenshots/{behavior-slug}.png as the binding screenshot path per behavior (visible from both 09-UAT.md and PLAYBOOK.md)"
  - "Annotated anti-overfitting rails (D-overfitting-1/2/3) inside 09-UAT.md so future verifier/UAT agents see the scope cap inline"
  - "PLAYBOOK Controls table standardized on a single `play` scene (D-06) with all 5 inputs (UP/DOWN/LEFT/RIGHT/A) — no title scene"
  - "Bug B workaround (ActorRef.moveTo Expr gap) documented in PLAYBOOK Known Quirks rather than deferred to Plan 03 — keeps the player-facing doc honest about current DSL surface limitations"

patterns-established:
  - "UAT-first sequencing pattern for reference-port phases: Plan 01 writes the per-phase UAT contract + per-example PLAYBOOK in markdown only; no DSL/build/test scaffolds in this wave"
  - "Per-behavior MCP input script pattern: emulator_start → emulator_step (boot) → emulator_wait_for_scene → behavior input → emulator_read_variable → emulator_screenshot at climax frame → emulator_assert"
  - "Visual Evidence Rule citation pattern: quote CLAUDE.md verbatim at top of UAT contract; reaffirm 'necessary but never sufficient' inside each test block's expected: line"

requirements-completed: [D-01, D-02, D-03]

# Metrics
duration: 5min
completed: 2026-05-13
---

# Phase 9 Plan 01: Lock simple-physics MCP UAT contract Summary

**09-UAT.md harness contract (3 D-01 behaviors, screenshot-bound evidence) + gbkt-examples/simple-physics/PLAYBOOK.md (Controls + Variables + 3 MCP Input Scripts) — zero DSL written, D-03 ordering rail satisfied.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-13T14:37:00Z
- **Completed:** 2026-05-13T14:42:26Z
- **Tasks:** 2
- **Files modified:** 2 (both created)

## Accomplishments
- Locked the Phase 9 MCP UAT contract with 3 test blocks corresponding 1:1 to D-01 behaviors (accel+clamp, jump impulse, decel-to-rest); each block declares an explicit `emulator_screenshot` path under `evidence/uat-screenshots/`.
- Quoted the CLAUDE.md Visual Evidence Rule verbatim and reinforced "necessary but never sufficient" in every test block — guards against the Phase 07.4 plans 14–18 failure mode (variable-only verification).
- Annotated the D-overfitting-1/2/3 anti-overfitting rails inside 09-UAT.md so the UAT contract carries its own scope cap.
- Created `gbkt-examples/simple-physics/PLAYBOOK.md` with all 8 required sections; the new Phase 9-specific "MCP Input Scripts" section embeds the same 3 input scripts so the `gbkt-play-game` and `gbkt-test-game` skills can drive UAT without re-deriving them.
- D-03 ordering rail (UAT first) is structurally guaranteed: zero `.kt` / `.gradle.kts` / build files exist under `gbkt-examples/simple-physics/` at plan close.

## Task Commits

Each task was committed atomically:

1. **Task 1: Write 09-UAT.md MCP harness contract** — `d9b320d0` (docs)
2. **Task 2: Write gbkt-examples/simple-physics/PLAYBOOK.md** — `796c46df` (docs)

## Files Created/Modified
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-UAT.md` — MCP harness contract; YAML frontmatter (status: pending, phase, source, started, updated); Visual Evidence Rule annotation; 3 test blocks (D-01.1/2/3) with mcp_script + emulator_screenshot evidence path each; anti-overfitting note; summary counters.
- `gbkt-examples/simple-physics/PLAYBOOK.md` — player + agent reference; Overview / How to Play / Controls (5 rows, single `play` scene) / Scene Flow / Win-Lose / Known Quirks (Bug B workaround, single-frame sprite, ++spdY emission shape) / Variables Reference (4 INT16 rows) / MCP Input Scripts (3 fenced scripts copied verbatim from 09-RESEARCH.md).

## Decisions Made
- Adopted 07.4-UAT.md frontmatter (`status: pending`, `phase`, `source`, `started`, `updated`) as the canonical UAT-contract frontmatter shape for the reference-port track.
- Bound screenshot evidence paths to a phase-local directory (`.planning/phases/09-.../evidence/uat-screenshots/`) rather than the MCP default (`build/gbkt/screenshots/`) so evidence travels with the phase artifact when the build directory is cleaned.
- Embedded the same 3 MCP scripts in both 09-UAT.md and PLAYBOOK.md. Trade-off: minor duplication, but keeps the agent-facing playbook self-contained (no cross-doc lookup) and keeps the phase contract self-contained for the verifier. Acceptable redundancy at the ~50-line cost.
- Documented Bug B workaround (`smiley.x set (posX shr 4)`) in PLAYBOOK Known Quirks instead of hiding it as a Plan 03 surprise. This is more honest to the player/agent reader and gives Plan 03's first-build analysis a concrete starting reference.

## Deviations from Plan

None — plan executed exactly as written. All Task 1 and Task 2 acceptance criteria (file presence, header sections, table row counts, INT16 typing, MCP script content, screenshot count ≥ 3, no `.kt` files) verified inline before each commit.

## Issues Encountered
None.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- Plan 02 (DSL port) can begin in the next wave. The UAT contract and PLAYBOOK lock the surface area; the DSL must produce a ROM where the 3 MCP scripts pass.
- Plan 03 (first-build analysis) has a concrete reference point: the PLAYBOOK Known Quirks already names Bug B as a candidate for the D-04 named codegen bug-fix, and the 09-UAT.md mcp_script for Behavior 1 (clamp) will surface Bug A (positive-literal signed comparison) if Plan 02 implements `whenever(spdX isAbove 64)` literally.
- Plan 06 (UAT run) has the binding evidence target: `.planning/phases/09-.../evidence/uat-screenshots/{behavior1,behavior2,behavior3}.png`. Until those 3 PNGs land, the phase is NOT shippable per D-02.
- No blockers introduced.

## Self-Check: PASSED

- 09-UAT.md: FOUND at `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-UAT.md` (4 `emulator_screenshot` mentions, 3 `^### N.` test blocks, 3 `mcp_script:` fenced blocks, 7 `evidence/uat-screenshots` references, 3 "necessary but never sufficient" annotations, 4 `D-overfitting` mentions, `Visual Evidence Rule` and `status: pending` and `phase: 09-port-simple-physics-gbdk-example-to-gbkt` all present).
- PLAYBOOK.md: FOUND at `gbkt-examples/simple-physics/PLAYBOOK.md` (8 H2 sections all present: Overview / How to Play / Controls / Scene Flow / Win / Lose Conditions / Known Quirks / Variables Reference / MCP Input Scripts; 5 control rows (UP/DOWN/LEFT/RIGHT/A); 4 INT16 variable rows; 3 fenced MCP scripts with emulator_start/step/screenshot/assert each; required strings D-pad/12.4/sub-pixel/-512/clamp all present at counts 5/4/16/5/11).
- No `.kt` / `.gradle.kts` files under `gbkt-examples/simple-physics/` (D-03 ordering rail).
- Commit `d9b320d0` (Task 1): FOUND in `git log --oneline`.
- Commit `796c46df` (Task 2): FOUND in `git log --oneline`.

---
*Phase: 09-port-simple-physics-gbdk-example-to-gbkt*
*Completed: 2026-05-13*
