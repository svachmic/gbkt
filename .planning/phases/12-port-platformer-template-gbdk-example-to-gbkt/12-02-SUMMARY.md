---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: "02"
subsystem: planning-evidence
tags: [evidence-scaffold, reference-rom, gitignore, build-doc, autonomous-false, human-verified]
status: complete
dependency_graph:
  requires: [12-01]
  provides:
    - evidence/reference/ directory tree
    - evidence/uat-screenshots/ directory tree
    - evidence/reference/BUILD.md (reference ROM rebuild doc)
    - .gitignore rules for evidence/reference/*.{gb,map,noi,sym} + build/ + obj/
  affects: [Plan 12-24 three-signal artifact, D-21 verifier ROM-build smoke test]
tech_stack:
  added: []
  patterns:
    - Phase-9 / Phase-10 / Phase-11 evidence/reference/ + uat-screenshots/ layout
    - "Track BUILD.md; gitignore binaries (reproducible from doc)"
    - "Track PNG screenshots (binding visual evidence per D-10)"
key_files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/.gitkeep
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/.gitkeep
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/BUILD.md
  modified:
    - .gitignore  # appended Phase 12 evidence/reference/ ignore rules
decisions:
  - "Reference build invocation is `make gb` (NOT `make EXT=gb gb` as the PLAN tentatively suggested) — the reference's Makefile.targets defines a `gb` target that internally sets EXT=gb. The plan's draft command was lightly out-of-date; the BUILD.md captures the correct minimal invocation."
  - "All four file-class ignores listed (.gb / .map / .noi / .sym) plus build/ + obj/ subdirectories; PNG screenshots NOT ignored per D-10."
  - "Compiler-flags table calls out the gbc-target's `-Wm-yc` add — gbkt port's `GBC_COMPATIBLE` matches the reference's `gbc` build path, not the plain `gb` target."
metrics:
  duration_minutes: 6
  completed: "2026-05-21T19:51:00Z"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 5
---

# Phase 12 Plan 02: Evidence scaffold + reference ROM BUILD.md — Summary

Stand up the `evidence/` directory tree per D-17a and write the reproducibility doc for the reference ROM build. Two automated tasks complete; the human-verify task (actually running `make gb` against the user's GBDK install) is **paused at checkpoint** — surfaced as a human-action structured return per the executor prompt's `<checkpoint_handling>` rule.

## What Was Built

### Task 1: Evidence directory tree + .gitignore rules (commit `270e83cd`)

- `evidence/reference/.gitkeep` — empty marker, git tracks the directory
- `evidence/uat-screenshots/.gitkeep` — empty marker, git tracks the directory
- `.gitignore` appended (after the existing Phase 11.3 `gbkt-examples/.archive/` block):
  - `.planning/phases/12-.../evidence/reference/*.gb`
  - `.planning/phases/12-.../evidence/reference/*.map`
  - `.planning/phases/12-.../evidence/reference/*.noi`
  - `.planning/phases/12-.../evidence/reference/*.sym`
  - `.planning/phases/12-.../evidence/reference/build/`
  - `.planning/phases/12-.../evidence/reference/obj/`
  - Comment clarifying `uat-screenshots/*.png` is NOT ignored (D-10).

### Task 2: evidence/reference/BUILD.md (commit `2e3794b5`)

156-line reproducibility doc with the sections required by the plan acceptance criteria:

- Purpose paragraph citing D-17a + D-21
- Prerequisites (GBDK-2020 + `GBDK_HOME`)
- Build invocation (the canonical `make gb` — Makefile.targets dispatches to `build-target PORT=sm83 PLAT=gb EXT=gb SPRITES=gbapduck`, which depends on the `png2asset` recipe + `$(BINS)` link in one step)
- Expected outputs (.gb / .map / .noi in `build/gb/` + png2asset gen files in `gen/gb/src/`)
- Capture to `evidence/` cp commands
- Cartridge type table (reference `0x1B` MBC5+RAM+BATTERY vs gbkt port `"MBC1"` magic string per D-claude-3; expected divergence per D-20)
- Asset flags reference (per-asset png2asset flags table)
- Compiler flags (`-Wm-yoA / -Wm-ya4 / -Wl-yt0x1B / -autobank`; NO `-Wm-yc` on `gb`)
- Reproducibility checksum table (sha256 / GBDK version, blank rows for first-build capture)
- Bank-layout signal verification (`grep DEF l__CODE` against the `.noi`)

## Task 3 (resolved — human-action satisfied by orchestrator)

`Task 3: Human-verify: reference ROM rebuilds on this machine` — declared
`type="checkpoint:human-verify" gate="blocking"` in the plan. After the
executor paused at this checkpoint, the user instructed the orchestrator
("please build it yourself — you have all tools necessary"). The orchestrator
ran the canonical `make gb-clean && make gb` invocation per BUILD.md on the
host machine and captured the sha256 + GBDK version into BUILD.md's
reproducibility table.

**Verified outputs:**

| Artifact | Path | Size |
| --- | --- | --- |
| ROM | `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.gb` | 32768 bytes |
| Linker map | `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.map` | 24512 bytes |
| `.noi` | `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/build/gb/platformer_template.noi` | 8332 bytes |

**sha256 (.gb):** `7f4c5095d195446019004a7a07d8fb6ee75af073ef2ab7012c62c5cb9bd7d587`

**GBDK version:** gbdk-4.5.0 (lcc.c rev 2.0, 2025-12-28)

**Binaries NOT committed** per the `.gitignore` rules added in Task 1
(evidence/reference/*.{gb,map,noi,sym} are reproducible from BUILD.md +
the captured sha256). Capture to evidence/ is optional and not necessary
for D-21 — the doc itself satisfies the "reproducibility" requirement.

**png2asset / lcc emitted warnings** (informational only, expected for the
reference; non-blocking):
- `gb.h:2148: warning 126: unreachable code` (×2; in common.c + level.c — generic GBDK header artifact)
- `player.c:208, 271: warning 110: conditional flow changed by optimizer` (×2; reference idiom)

These warnings are part of the reference's known noise floor and do not
affect ROM correctness or bank layout. Documented here so future
re-verifications can distinguish noise from real regressions.

## Verification (automated portions only)

```bash
# Task 1
test -d .planning/phases/12-.../evidence/reference                          # exit 0
test -d .planning/phases/12-.../evidence/uat-screenshots                    # exit 0
grep -c 'evidence/reference/\*.gb' .gitignore                               # ≥ 1 (returns 2: phase 10 + phase 12)
! grep -q 'uat-screenshots/\*.png' .gitignore                               # NOT ignored (D-10)

# Task 2
test -f .planning/phases/12-.../evidence/reference/BUILD.md                 # exit 0
grep -q 'GBDK_HOME' BUILD.md                                                # exit 0 (2 hits)
grep -q 'platformer_template.gb' BUILD.md                                   # exit 0 (3 hits)
grep -q '0x1B' BUILD.md                                                     # exit 0 (4 hits)
grep -q 'MBC5' BUILD.md                                                     # exit 0 (2 hits)
grep -q 'MBC1' BUILD.md                                                     # exit 0 (2 hits — port divergence)
```

All automated criteria pass.

## Deviations from Plan

### Rule 1 - Bug: corrected the make invocation

**Found during:** Task 2 (writing BUILD.md)

**Issue:** The plan's `<action>` step 3 draft command was
`make GBDK_HOME=$GBDK_HOME EXT=gb png2asset && make GBDK_HOME=$GBDK_HOME EXT=gb gb`.
After reading the actual reference `Makefile.targets`, the `gb` target
ALREADY dispatches via `${MAKE} build-target PORT=sm83 PLAT=gb EXT=gb
SPRITES=gbapduck`, and `build-target` depends on `png2asset` automatically.
The two-step invocation would still work but is redundant and (worse)
the `make EXT=gb png2asset` invocation alone misses the `SPRITES=gbapduck`
variable that the `gb` target injects.

**Fix:** BUILD.md documents the canonical single-step `make gb` invocation.
Run `make gb-clean` first for a guaranteed clean rebuild.

**Files modified:** `evidence/reference/BUILD.md` (Task 2).

**Commit:** `2e3794b5`

**Why this is Rule 1, not Rule 4:** This is a documentation correctness
fix — the BUILD.md must be self-sufficient (per the plan's `<done>` clause)
so an operator following it gets a working build. Substituting a verified
correct command for a slightly off draft is a textbook bug fix; the
plan's intent ("reproduce the reference ROM") is preserved exactly.

## Authentication Gates

None encountered. The build invocation requires `GBDK_HOME` and an
installed toolchain, but these are environment/install gates, NOT
authentication — the human-verify checkpoint surfaces them naturally.

## Known Stubs

None.

The "Reproducibility checksum (optional)" table in BUILD.md has a
`_TBD_` row for the sha256 / GBDK version. This is intentional and
documented as "record on first build" — it cannot be filled in from
the worktree because the build itself is the human-action step.

## Threat Flags

None. This plan adds documentation and `.gitignore` rules. No network
endpoints, auth paths, file access patterns, or schema changes at
trust boundaries.

## Self-Check: PASSED

- [x] `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/.gitkeep` — exists
- [x] `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/.gitkeep` — exists
- [x] `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/BUILD.md` — exists, 156 lines
- [x] `.gitignore` contains literal `evidence/reference/*.gb` rule for Phase 12
- [x] `.gitignore` does NOT contain `uat-screenshots/*.png` rule (D-10)
- [x] Commit `270e83cd` (Task 1) — exists in git log
- [x] Commit `2e3794b5` (Task 2) — exists in git log
- [x] Task 3 (`checkpoint:human-verify`) — resolved; orchestrator ran `make gb` and captured sha256 + GBDK version into BUILD.md reproducibility table

## Resume Path

Resolved on 2026-05-21. Plan 12-02 is complete; subsequent waves can
proceed. The Phase 12-24 three-signal artifact plan will re-build the
reference (if a fresh .gb is needed for bank-layout diff) using the same
`make gb` invocation documented in BUILD.md.
