---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "04"
subsystem: planning
tags: [docs, backlog, feat-seeds, requirements]
dependency_graph:
  requires: [17-01]
  provides: [FEAT-STATE-MACHINES, FEAT-DIALOG-TICK-API, FEAT-MENU-GRID-STYLE, FEAT-SAVE-DATA-FIELDS, FEAT-ENTITY-POOL-LIFECYCLE, FEAT-TWEENING, FEAT-CAMERA-EXTRAS, FEAT-PHYSICS-WORLD, FEAT-PATHFINDING-NAVGRID, FEAT-TESTING-DSL, FEAT-BATTLE-MENUS, FEAT-INVENTORY-DELEGATE]
  affects: [REQUIREMENTS.md, .planning/backlog/v0.2.0/]
tech_stack:
  added: []
  patterns: [SEED-001-style frontmatter, verbatim-archive with provenance]
key_files:
  created:
    - .planning/backlog/v0.2.0/FEAT-STATE-MACHINES.md
    - .planning/backlog/v0.2.0/FEAT-DIALOG-TICK-API.md
    - .planning/backlog/v0.2.0/FEAT-MENU-GRID-STYLE.md
    - .planning/backlog/v0.2.0/FEAT-SAVE-DATA-FIELDS.md
    - .planning/backlog/v0.2.0/FEAT-ENTITY-POOL-LIFECYCLE.md
    - .planning/backlog/v0.2.0/FEAT-TWEENING.md
    - .planning/backlog/v0.2.0/FEAT-CAMERA-EXTRAS.md
    - .planning/backlog/v0.2.0/FEAT-PHYSICS-WORLD.md
    - .planning/backlog/v0.2.0/FEAT-PATHFINDING-NAVGRID.md
    - .planning/backlog/v0.2.0/FEAT-TESTING-DSL.md
    - .planning/backlog/v0.2.0/FEAT-BATTLE-MENUS.md
    - .planning/backlog/v0.2.0/FEAT-INVENTORY-DELEGATE.md
  modified:
    - .planning/REQUIREMENTS.md
decisions:
  - "All 12 aspirational DSL sections archived verbatim with source provenance before DSL_REFERENCE.md rewrite plans (17-08/09/10) remove them — DOCS-02 'no spec value lost' is provable"
  - "FEAT-XX generic placeholder in REQUIREMENTS.md expanded to 12 individual indexed FEAT-* entries, each pointing to its backlog file"
metrics:
  duration: 7 minutes
  completed: 2026-06-12
  tasks_completed: 2
  files_changed: 13
---

# Phase 17 Plan 04: Backlog Seed Archive and REQUIREMENTS Expansion Summary

12 aspirational DSL sections verbatim-archived as per-subsystem FEAT-*.md backlog seeds; REQUIREMENTS.md FEAT-XX placeholder expanded to 12 indexed entries.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Write 12 FEAT-*.md backlog seed files | 74719286 | 12 files created |
| 2 | Expand FEAT-XX placeholder in REQUIREMENTS.md | 90c1ffa6 | 1 file modified |

## What Was Built

### Task 1: 12 FEAT-*.md backlog seeds

Created 12 dormant v0.2.0 backlog seed files in `.planning/backlog/v0.2.0/`, one per aspirational DSL section identified in `DOCS-AUDIT.md`:

| File | Covers | DSL_REFERENCE.md Lines |
|------|--------|------------------------|
| FEAT-STATE-MACHINES.md | Top-level `states()` builder | ~370–408 |
| FEAT-DIALOG-TICK-API.md | `DialogHandle.tick/isActive/isComplete/show/hide` | ~922–1001 |
| FEAT-MENU-GRID-STYLE.md | `style {}`, `gridMenu()`, `MenuHandle.tick/isActive/isVisible/selectedIndex` | ~1007–1113 |
| FEAT-SAVE-DATA-FIELDS.md | `u16Field/flagsField/load/save/exists/erase` field-level save API | ~1234–1307 |
| FEAT-ENTITY-POOL-LIFECYCLE.md | Sprite/lifecycle pool with spawn/despawn/forEachActive | ~1316–1471 |
| FEAT-TWEENING.md | `tween()` + `Easing` enum + `MAX_TWEENS` | ~1473–1539 |
| FEAT-CAMERA-EXTRAS.md | Camera offset/deadzone/snapTo/followX/followY/shake-builder/wipe/iris (Sections 7+8) | ~1585–1698 |
| FEAT-PHYSICS-WORLD.md | Global `physics {}` world, `gravityZone()`, `tag()`, `mass`, `maxVelocity` | ~1704–1818 |
| FEAT-PATHFINDING-NAVGRID.md | `navGrid()`, `findPathTo` infix, weighted tiles, `Heuristic` enum | ~1824–1983 |
| FEAT-TESTING-DSL.md | `testGame/testScene` DSL, `press/tap` input, fluent assertions | ~2011–2239 |
| FEAT-BATTLE-MENUS.md | `battleMenu`, `combatFormulas`, `battleState()`, `battleTransition()` | ~2408–2483 |
| FEAT-INVENTORY-DELEGATE.md | game-scope `by item` delegate, `ItemCategory` enum, `equipSlot()`, ContainerRef advanced ops | ~2489–2600 |

Each file carries:
- SEED-001-style front-matter: `status: dormant`, `triage_disposition: RE-DEFERRED`, `triage_date: 2026-06-12`
- `## Source` section: "Removed from context/DSL_REFERENCE.md lines N–M (commit removal-commit-TBD)" + "Implemented today:" naming the real API
- `## Verbatim removed content` section: code samples copied exactly from `context/DSL_REFERENCE.md`

### Task 2: REQUIREMENTS.md expansion

Replaced the single FEAT-XX bullet under "Feature Implementation (from pruned docs)" with 12 per-subsystem indexed entries, each with a one-line description and a pointer to its backlog file. The generic placeholder is gone.

## Verification Results

- `ls .planning/backlog/v0.2.0/FEAT-*.md | wc -l` → 12 (PASS)
- All 12 files contain "Verbatim removed content" section (PASS)
- All 12 files contain "Removed from context/DSL_REFERENCE.md" provenance line (PASS)
- `grep -c 'FEAT-XX' .planning/REQUIREMENTS.md` → 0 (placeholder removed, PASS)
- `grep -c 'FEAT-STATE-MACHINES|FEAT-DIALOG-TICK-API|...' .planning/REQUIREMENTS.md` → 12 (PASS)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. These are planning files — no runtime code stubs.

## Threat Flags

None. Content is public API documentation already in the repo. No secrets, no PII.

## Self-Check: PASSED

- 12 FEAT-*.md files exist: confirmed (`ls .planning/backlog/v0.2.0/FEAT-*.md | wc -l` → 12)
- Commit 74719286 exists: confirmed (`git log --oneline | grep 74719286`)
- Commit 90c1ffa6 exists: confirmed (`git log --oneline | grep 90c1ffa6`)
- REQUIREMENTS.md updated: FEAT-XX gone, 12 FEAT-* entries present
