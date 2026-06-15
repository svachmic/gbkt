---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "12"
subsystem: docs
tags: [docs, cross-doc-consistency, feat-provenance, d-16, d-11]
dependency_graph:
  requires: [17-11]
  provides: [DOCS-01, DOCS-02, cross-doc-clean, feat-provenance-complete]
  affects:
    - .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md
    - .planning/backlog/v0.2.0/FEAT-*.md (all 12)
tech_stack:
  added: []
  patterns: [grep-driven-consistency-pass, provenance-backfill]
key_files:
  created: []
  modified:
    - .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md
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
decisions:
  - "All 50 CLAUDE.md files plus CONTRIBUTING.md and context/*.md are clean — zero live stale-API references to removed/renamed identifiers (states(, navGrid(, tween(, Easing., testGame(, testScene(, battleMenu, combatFormulas, battleState(, battleTransition(, gridMenu(, ItemCategory enum, dialog.tick)"
  - "gbkt-ir/CLAUDE.md:29 ItemCategoryDef left unchanged — it is the IR type name (not the stale DSL ItemCategory enum)"
  - "CLAUSE.md remains a routing index per D-16 standing rule — no quick-refs added"
  - "12 FEAT-*.md provenance headers backfilled with real git short hashes: eb0c6aaa (sections 1-4), d6e1e5f7 (5/6/9), 183bd5a3 (7+8), 63afe76a (10-11), 929653a4 (12-13)"
metrics:
  duration: "3 minutes"
  completed: "2026-06-12"
  tasks_completed: 2
  files_changed: 13
---

# Phase 17 Plan 12: Cross-Doc Consistency Pass + FEAT-* Provenance Backfill Summary

**One-liner:** Grep-driven D-16 cross-doc pass confirmed zero stale API references across 50 CLAUDE.md files, CONTRIBUTING.md, and context/*.md; all 12 FEAT-*.md provenance headers backfilled with real removal-commit hashes (D-11 complete).

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Grep-driven cross-doc consistency pass (D-16); append Cross-Doc Consistency section to DOCS-AUDIT.md | 53cc61d5 | DOCS-AUDIT.md |
| 2 | Backfill removal-commit hashes into all 12 FEAT-*.md provenance headers (D-11) | da798e28 | 12 FEAT-*.md files |

## What Was Built

### Task 1: Cross-Doc Consistency Pass (D-16)

Grepped the following stale API search terms across all 50 CLAUDE.md files (root + all module CLAUDE.md files), CONTRIBUTING.md, and all context/*.md files:

`states(`, `navGrid(`, `tween(`, `Easing.`, `testGame(`, `testScene(`, `battleMenu`, `combatFormulas`, `battleState(`, `battleTransition(`, `by item`, `ItemCategory`, dialog-context `.tick()`, `gridMenu(`, `romBanks =` (17-11 rename), `ramBanks =` (17-11 rename), `physicsWorld`, `gravityZone`, `findPathTo`, `Heuristic.`

**Result:** Zero live stale-API references in any non-DSL_REFERENCE doc.

**One justified exception:** `gbkt-ir/CLAUDE.md:29` contains `ItemCategoryDef` (part of a file table listing IR types in `InventoryIR.kt`). This is the real IR type name — distinct from the stale `ItemCategory` DSL enum removed from DSL_REFERENCE.md. Left unchanged with rationale documented in DOCS-AUDIT.md.

**CLAUDE.md routing-index confirmation (D-16):** Root CLAUDE.md contains no DSL quick-ref content — it remains a pure routing index. Confirmed unchanged.

Appended "Cross-Doc Consistency" section to evidence/DOCS-AUDIT.md listing every search term, zero-hit result, and the one justified exception.

### Task 2: FEAT-* Provenance Backfill (D-11)

Determined the removal commits via `git log --oneline context/DSL_REFERENCE.md` and mapped each FEAT file to the commit that removed its section's content:

| FEAT File | DSL_REFERENCE Sections Removed | Commit |
|-----------|-------------------------------|--------|
| FEAT-STATE-MACHINES.md | Section 1 (lines 370–408) | eb0c6aaa |
| FEAT-DIALOG-TICK-API.md | Section 2 (lines 922–1001) | eb0c6aaa |
| FEAT-MENU-GRID-STYLE.md | Section 3 (lines 1007–1113) | eb0c6aaa |
| FEAT-SAVE-DATA-FIELDS.md | Section 4 (lines 1234–1307) | eb0c6aaa |
| FEAT-ENTITY-POOL-LIFECYCLE.md | Section 5 (lines 1316–1471) | d6e1e5f7 |
| FEAT-TWEENING.md | Section 6 (lines 1473–1539) | d6e1e5f7 |
| FEAT-CAMERA-EXTRAS.md | Sections 7+8 (lines 1585–1698) | 183bd5a3 |
| FEAT-PHYSICS-WORLD.md | Section 9 (lines 1704–1818) | d6e1e5f7 |
| FEAT-PATHFINDING-NAVGRID.md | Section 10 (lines 1824–1983) | 63afe76a |
| FEAT-TESTING-DSL.md | Section 11 (lines 2011–2239) | 63afe76a |
| FEAT-BATTLE-MENUS.md | Section 12 (lines 2408–2483) | 929653a4 |
| FEAT-INVENTORY-DELEGATE.md | Section 13 (lines 2489–2600) | 929653a4 |

D-11 provenance requirement fully satisfied: each FEAT-*.md now carries source line range + real removal commit + what-is-implemented description.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. These are documentation and provenance edits — no runtime code stubs.

## Threat Flags

None. Public documentation; no secrets. T-17-12 (Information Disclosure) accepted per threat model.

## Self-Check: PASSED

- DOCS-AUDIT.md modified and committed: 53cc61d5 confirmed
- "Cross-Doc Consistency" section in DOCS-AUDIT.md: `grep -c 'Cross-Doc Consistency'` = 1 (PASS)
- Stale reference count outside DSL_REFERENCE.md: 0 (PASS)
- Remaining `removal-commit-TBD` placeholders: 0 (PASS)
- 12 FEAT-*.md files updated: confirmed via `grep 'commit [a-f0-9]' FEAT-*.md` (12 hits, all real hashes)
- CLAUDE.md routing-index rule (D-16): no quick-refs added (PASS)
- All 12 FEAT-*.md committed: da798e28 (12 files changed, 12 insertions, 12 deletions) (PASS)
