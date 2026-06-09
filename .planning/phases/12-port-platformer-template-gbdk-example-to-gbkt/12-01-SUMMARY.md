---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 01
subsystem: planning-docs
tags:
  - uat-contract
  - playbook
  - mcp-agent
  - visual-evidence-rule
  - d-08
  - d-09
  - d-10
  - d-11
  - d-16
requires:
  - 12-CONTEXT.md (D-08, D-09, D-10, D-11, D-16, D-17a)
  - 12-VALIDATION.md (§Per-Anchor Verification Map, §Manual-Only Verifications)
  - CLAUDE.md (§Verification Methodology — Visual Evidence Rule)
  - gbkt-examples/banks/PLAYBOOK.md (analog reference for MCP-script shape)
provides:
  - 5-anchor UAT contract for Phase 12 (D-08 #1..#5) binding all later plans
  - MCP-agent-runnable script for UAT plans 12-19..12-23 to execute verbatim
  - JVM emission invariant patterns (per-function awk brace-walk + grep) for D-16 #1..#5
affects:
  - All Phase 12 plans that reference anchor IDs (every plan from 12-02 onward
    cross-references one of the 5 anchors via D-16 emission invariants)
  - UAT plans 12-19..12-23 (or per-anchor evidence-capture plans the planner
    names) consume PLAYBOOK.md verbatim
tech-stack:
  added: []
  patterns:
    - per-function awk brace-walk + grep for emission invariants (CLAUDE.md §Scope-level grep gates corollary)
    - visual-evidence rule binding (CLAUDE.md §Verification Methodology)
    - press+step loop for sustained dpad-held inputs via MCP edge-trigger API
key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-UAT.md
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/PLAYBOOK.md
  modified: []
decisions:
  - Quote CLAUDE.md §Visual Evidence Rule verbatim in 12-UAT.md (binding all 5 anchors as visual truths)
  - 5 anchors is a SECOND ONE-TIME EXPANSION (D-09); Phase 12.1 inherits at most 5 anchors
  - Per-function awk brace-walk + grep is the binding emission-invariant shape (no file-level grep -c for D-16)
  - PLAYBOOK.md leaves exact expected values TBD until UAT plans 19-23 reconcile against codegen metadata
  - Sustained dpad-held inputs use a press+step loop pattern (continuous-held semantics on top of MCP edge-trigger API)
metrics:
  duration: 5 minutes
  completed_date: 2026-05-21
---

# Phase 12 Plan 01: Lock UAT Contract Summary

UAT-first plan locks the 5-anchor evidence contract and the MCP-agent-runnable
playbook **before any DSL or codegen is authored** (per D-11), mirroring Phase 9 /
10 / 11's discipline.

## What shipped

Two markdown documents, zero Kotlin code, zero generated C:

1. **`12-UAT.md`** (386 lines) — the binding evidence-shape contract for Phase
   12. Each of the 5 D-08 anchors gets its own H3 subsection with:
   - Anchor ID (`D-08 #1` .. `D-08 #5`).
   - Goal quoted verbatim from `12-CONTEXT.md` §D-08.
   - Setup (ROM boot state + Anchor 1 prerequisites for downstream anchors).
   - Steps (numbered scripted-input sequence with `emulator_press` /
     `emulator_step` / `emulator_screenshot` / `emulator_read_variable` calls).
   - Visual evidence (binding screenshot paths under
     `evidence/uat-screenshots/anchor-N/`).
   - Variable assertions (paired with each screenshot; never sole evidence).
   - JVM emission invariant (per-function `awk` brace-walk + `grep` over the
     generated C, copied from `12-VALIDATION.md` §Per-Anchor Verification Map).
   - Verdict criteria ("GREEN iff screenshot matches expected pose AND variable
     assertion holds AND JVM emission invariant grep returns non-zero").

   The doc also quotes CLAUDE.md §Visual Evidence Rule verbatim, includes an
   "Anchor count rationale" paragraph citing D-09 (5 is a second one-time
   expansion; NOT a stepping stone to ≥6), enumerates the
   `evidence/{reference,uat-screenshots/anchor-N,oracle-comparison.md}` directory
   layout per D-17a, and closes with an anti-overfitting note (UAT verifies the
   integration contract, NOT pixel parity with the GBDK reference).

2. **`PLAYBOOK.md`** (261 lines) — the scripted-input companion. Each Anchor N
   section is a literal MCP tool-call sequence using the canonical names
   `mcp__gbkt-emulator__emulator_{start,press,step,screenshot,read_variable,
   wait_for_scene}`. Anchors 3, 4, 5 document the press+step loop pattern for
   sustained dpad-held inputs (continuous-held semantics on top of the edge-trigger
   MCP API). A "Variable naming note" flags that names like `_player_vy`,
   `_camera_x`, `_walkFrameIdx`, `_facingRot` are TENTATIVE — UAT plans 19-23
   reconcile against actual codegen metadata per user-memory
   `feedback_no_magic_strings.md`.

## Anchors locked (D-08 #1..#5)

| # | Anchor                                                | Evidence dir                       | JVM emission invariant target                          |
|---|-------------------------------------------------------|------------------------------------|--------------------------------------------------------|
| 1 | Title → gameplay scene transition                     | `evidence/uat-screenshots/anchor-1/` | `title_frame` body: `navigate_to_scene`; `gameplay_enter` body: `setup_current_level` |
| 2 | Tilemap collision (jump + land on solid)              | `evidence/uat-screenshots/anchor-2/` | `is_tile_solid()` body: 2× `SWITCH_ROM` + `_current_level_non_solid_tile_count` |
| 3 | Horizontal scroll (camera moves, no repeat)           | `evidence/uat-screenshots/anchor-3/` | `platformer_camera_update()` body: `set_bkg_submap` + `_old_map_pos_x`            |
| 4 | Metasprite animation (multi-frame walking + hflip)    | `evidence/uat-screenshots/anchor-4/` | `gameplay_frame` body: `sprite_player_frames[…]` + `move_metasprite_flipx`        |
| 5 | Level-switch (gameplay → NextLevel card → level 2)    | `evidence/uat-screenshots/anchor-5/` | `main()` body: `_next_level` + `setup_current_level`                              |

All 5 are visual truths → screenshots binding per CLAUDE.md §Visual Evidence Rule;
variable assertions PAIR with screenshots but never substitute; emission invariants
use the per-function awk brace-walk pattern (no file-level `grep -c`).

## How to use this contract downstream

- **Plan-checker** rejects any later plan that adds DSL or codegen without
  citing one of D-08 #1..#5 (or, for non-anchor-bound infrastructure, justifying
  the absence). D-11 binds: UAT contract MUST be locked before DSL.
- **UAT plans 12-19..12-23** (or the per-anchor evidence-capture plans the
  planner names) execute the PLAYBOOK.md scripts verbatim, capture
  `evidence/uat-screenshots/anchor-N/*.png`, record variable readings, and
  populate the "Result" field per anchor in 12-UAT.md.
- **D-16 invariant plans** in earlier waves (codegen-emission JVM tests) reuse
  the awk + grep patterns from 12-UAT.md so the JVM test, runtime UAT, and
  documentation contract all reference the SAME emission shape.
- **`12-VALIDATION.md` §Per-Task Verification Map** (currently TBD) can now
  populate its anchor-ID column directly from this contract.

## Deviations from Plan

None — plan executed exactly as written.

Both tasks completed verbatim:
- Task 1 created `12-UAT.md` with the 5-anchor contract per the action spec,
  passing all 5 acceptance gates (file exists, all D-08 #1..#5 mentions, awk
  pattern presence for ≥4 void functions + 1 `is_tile_solid` UINT8 function,
  no Kotlin DSL leakage, D-09 cited in rationale section).
- Task 2 created `PLAYBOOK.md` per the action spec, passing all 5 acceptance
  gates (file exists, Anchor 1..5 each present ≥1 time, all 5 MCP substrings
  present, `evidence/uat-screenshots/anchor-` appearing 21 times across the 5
  anchors, no Kotlin DSL leakage).

No CLAUDE.md directives required deviation; no auth gates; no architectural
decisions surfaced (Rule 4 inapplicable); no bugs found (Rule 1 inapplicable);
no missing critical functionality (Rule 2 inapplicable); no blockers (Rule 3
inapplicable).

## Commits

- `a4e9e3a8` — `feat(12-01): lock 5-anchor UAT contract for platformer-template port`
- `23b8a36c` — `feat(12-01): add MCP-agent PLAYBOOK.md scripting all 5 UAT anchors`

## Verification

All `<verify>` automated gates and `<acceptance_criteria>` checks passed for both
tasks:

```
# 12-UAT.md
file exists: yes
Anchor count: 22 (>=5 OK)
D-08 #1..#5: each present 3x
awk '/^void' patterns: 8 (>=4 OK across anchors 1, 3, 4, 5)
awk '/^UINT8 is_tile_solid' pattern: 2 (anchor 2; >=1 OK)
Kotlin 'val .* by ' leakage: 0
'scene(' leakage: 0
D-09 mentioned: 3 times

# PLAYBOOK.md
file exists: yes
Anchor 1..5 sections: each present (1-2x)
emulator_start / _press / _step / _screenshot / _read_variable: all present
(7/18/23/15/20 hits respectively)
evidence/uat-screenshots/anchor-: 21 mentions (>=5 OK; one path-set per anchor)
Kotlin 'val .* by ' leakage: 0

# Plan-level
ls 12-UAT.md + PLAYBOOK.md: exit 0
grep -c "D-08 #" 12-UAT.md: 15 (>=5 OK)
git status --short | grep '\.kt$': empty (no Kotlin source touched)
git diff HEAD~2 HEAD .kt: no .kt files in the last 2 commits
```

## Self-Check: PASSED

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-UAT.md` — FOUND
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/PLAYBOOK.md` — FOUND
- Commit `a4e9e3a8` — FOUND in git log
- Commit `23b8a36c` — FOUND in git log
- No Kotlin source modified (anti-overfitting / D-11 invariant) — VERIFIED
- All `<must_haves.truths>` from plan frontmatter — VERIFIED:
  - "12-UAT.md exists with exactly 5 anchor definitions matching D-08 #1..#5" ✓
  - "PLAYBOOK.md exists with MCP-agent-runnable scripted-input steps for each anchor" ✓
  - "No DSL or .kt source file is created or modified in this plan" ✓
- All `<must_haves.artifacts>` from plan frontmatter — VERIFIED:
  - `12-UAT.md` contains "Anchor 5" string ✓
  - `PLAYBOOK.md` contains "emulator_screenshot" string ✓
- `<must_haves.key_links>` — VERIFIED:
  - `12-UAT.md` cross-references `12-VALIDATION.md §Per-Anchor Verification Map`
    via the explicit `D-08 #1..#5` pattern (5 anchor subsections each carry a
    D-16 invariant copied from VALIDATION.md).
  - `PLAYBOOK.md` invokes the canonical `emulator_(start|press|step|screenshot|`
    `assert|read_variable|wait_for_scene)` MCP tool names (regex matches all
    7 listed in the plan's `pattern` field).
