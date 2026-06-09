---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: "01"
subsystem: planning-artifacts
tags: [uat-contract, playbook, asset-spec, documentation, phase-10]
dependency_graph:
  requires: []
  provides:
    - 10-UAT.md — locked 3-behavior UAT contract for metasprites port
    - PLAYBOOK.md — LLM-readable game description for agent-driven testing
    - evidence/asset-spec.md — locked 5-frame elephant sprite shape
  affects:
    - All subsequent Phase 10 plans (substrate, DSL, codegen, port assembly)
    - Plan 10-17 (Tier-1 JVM emission invariants) — uses behavior specs from UAT
    - Plan 10-16 (UAT execution) — uses mcp_scripts from 10-UAT.md
tech_stack:
  added: []
  patterns:
    - UAT-first sequencing (inherited from Phase 9 D-03)
    - Visual Evidence Rule (CLAUDE.md) applied to behavior 3 (GBC sub-palette)
    - Anti-overfitting doctrine (D-overfitting-1/2/3) carried forward from Phase 9
key_files:
  created:
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/PLAYBOOK.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/asset-spec.md
  modified: []
decisions:
  - "Behavior 3 (sub-palette cycle) explicitly requires GBC mode screenshot per Visual Evidence Rule — DMG screenshot rejected as evidence"
  - "Anti-overfitting note (D-overfitting-1/2/3) included verbatim matching Phase 9 shape"
  - "Asset spec locks 5-frame layout and 31/33/33/32/32 tile counts before any DSL is written"
  - "png2asset -noflip flag documented — hardware OAM flip used instead of tile duplication"
metrics:
  duration: ~15 minutes
  completed: 2026-05-18T15:29:27Z
  tasks_completed: 3
  tasks_total: 3
  files_created: 3
  files_modified: 0
---

# Phase 10 Plan 01: UAT Contract Lock, PLAYBOOK, and Asset Spec

Locked UAT contract (3 behaviors with mcp_scripts), LLM playbook, and sprite asset spec for the Phase 10 metasprites port — BEFORE any DSL, IR, builder, or visitor is written (D-03).

## What Was Built

Three planning artifacts created to establish the scope cap and verification floor for Phase 10:

1. **`10-UAT.md`** — Locked 3-behavior UAT contract following the Phase 9 `09-UAT.md` shape exactly. Contains: Visual Evidence Rule quote verbatim, three behavior sections (B-press animation advance, A-press flip cycle, A-press sub-palette cycle), mcp_scripts with explicit variable assertions and screenshot paths, anti-overfitting note, and Summary block (`total: 3, pending: 3`). Behavior 3 explicitly requires `gbcMode=true` per CLAUDE.md Visual Evidence Rule.

2. **`PLAYBOOK.md`** — LLM-readable description of the metasprites game for agent-driven testing. Documents controls (A/B/D-pad button semantics from `metasprites.c` lines 22-27), visual expectations, and all key variables (`_idx`, `_rot`, `_posX`, `_posY`, `_spdX`, `_spdY`).

3. **`evidence/asset-spec.md`** — Locked asset shape: 64×240 PNG, 5 frames at 64×48 each, 8×8 tile grid (8×6 = 48 cells per frame), non-empty tiles 31/33/33/32/32 per frame, `png2asset` invocation flags (`-sh 48 -spr8x8 -noflip`). Includes derivation guide for transcribing png2asset output into DSL `frame { tile(relX, relY, baseId) }` calls.

## Deviations from Plan

None — plan executed exactly as written.

## Commits

| Task | Commit | Files | Description |
|------|--------|-------|-------------|
| 1 | 9f515284 | 10-UAT.md | Lock 3-behavior UAT contract |
| 2 | e984e5bc | PLAYBOOK.md | LLM-readable game description |
| 3 | a6e7ed7f | evidence/asset-spec.md | 5-frame elephant sprite spec |

## Known Stubs

None. These are planning artifacts with no UI rendering or data flow stubs.

## Threat Flags

None. No network endpoints, auth paths, file access patterns, or schema changes introduced — this plan creates documentation only.

## Self-Check: PASSED

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md` exists: VERIFIED
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/PLAYBOOK.md` exists: VERIFIED
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/asset-spec.md` exists: VERIFIED
- Commits 9f515284, e984e5bc, a6e7ed7f exist in git log: VERIFIED
- No `.kt`, `.gradle.kts`, or source files modified: VERIFIED
