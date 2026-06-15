---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: 01
subsystem: docs-audit
tags: [docs, audit, evidence, DOCS-01]
dependency_graph:
  requires: []
  provides: [evidence/DOCS-AUDIT.md]
  affects: [17-04, 17-08, 17-09, 17-10]
tech_stack:
  added: []
  patterns: [audit-table-evidence, per-method-citation, triage-sweep]
key_files:
  created:
    - .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md
  modified: []
decisions:
  - "Confirmed 174 method-level verdict rows across 13 sections (including previously-unaudited DialogHandle show/hide absence and SoundRef.play() flag)"
  - "T-06 triage flag: Audio section SoundRef.play() is absent; filed as plan 17-08 fix item"
  - "camera.fadeIn() in Camera Basic Setup example is wrong — fade() is a ScriptBuilder op; fix in 17-08"
metrics:
  duration: 7
  completed: "2026-06-12"
---

# Phase 17 Plan 01: DSL_REFERENCE Audit Evidence Summary

Per-method accuracy audit of all 13 stale-API sections in `context/DSL_REFERENCE.md` plus a full-document triage sweep. Produces the D-15 evidence artifact consumed by all downstream docs rewrite and archive plans.

## What Was Built

**One file created:** `.planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md` (438 lines)

The file contains:
- 174 method-level audit rows across all 13 stale-API sections
- Each row: section number, DSL_REFERENCE line, documented method, source `file:line` citation (or `ABSENT`), verdict (accurate/corrected/moved-to-backlog), and target backlog file for moved-to-backlog rows
- 12 FEAT-* backlog target files named and assigned per RESEARCH.md Section 3 grouping
- Full-document triage sweep (D-13) covering 3,224 lines; 6 flags found

## Verdicts Summary

| Section | Accurate | Corrected | Moved-to-Backlog |
|---------|----------|-----------|------------------|
| 1. State Machine DSL | 0 | 0 | 6 (all absent) |
| 2. Dialog System | 4 | 4 | 4 (tick/isActive/isComplete/show/hide on DialogHandle) |
| 3. Menu System | 4 | 6 | 8 (style block/gridMenu/tick/isActive/isVisible/selectedIndex) |
| 4. Save Data | 1 | 4 | 8 (field-level API) |
| 5. Entity Pools | 2 | 1 | 14 (all lifecycle/spawn absent) |
| 6. Tweening/Easing | 0 | 0 | 5 (all absent) |
| 7. Camera System | 2 | 7 | 11 (handle methods absent; config-only builder) |
| 8. Camera Transitions | 2 | 0 | 2 (wipe/iris/flash absent) |
| 9. Physics | 5 | 4 | 7 (global world/zones absent) |
| 10. Pathfinding | 6 | 0 | 9 (navGrid/findPathTo absent) |
| 11. Testing Framework | 1 | 1 | 5 (testGame/testScene absent) |
| 12. Battle Menu/Formulas | 3 | 0 | 4 (battleMenu/combatFormulas/battleState absent) |
| 13. Item & Inventory | 5 | 5 | 8 (ItemCategory enum/equip/inventory ops absent) |

## Key Findings

1. **DialogHandle has no show()/hide()** — only `MenuHandle` (UIBuilders.kt:454/463) has these. The DSL_REFERENCE at line 986 (`elder.show()`) is wrong; Dialog visibility is not implemented.

2. **camera.fadeIn() in Camera Basic Setup (line 1606)** is wrong — `fade()` is a `ScriptBuilder` method, not a camera method. The entire Camera section docs treat `camera` as a runtime handle when it is config-time-only.

3. **SoundRef.play() absent (T-06)** — the Audio/Sound section (~line 820-900) lacks a stale-API caveat but documents `sounds.bump.play()` while `SoundRef` only has `id: String` (no `play()` method). This is an uncaveated stale API outside the 13 known sections.

4. **Physics Builder uses Int, not Float** — `PhysicsBuilder.gravity(Int)` and `MovementBuilder.friction(Int)` are integer-pixel functions. The DSL_REFERENCE documents float properties (0.5f, 0.9f) which are absent.

5. **MenuBuilder function-style, not property-style** — all MenuBuilder setters are method-calls: `cursor(">")` not `cursor = ">"`, `parent(mainMenu)` not `parent = mainMenu`. The DSL_REFERENCE shows the wrong style throughout the Menu section.

## Triage Sweep Flags

| Flag | Line(s) | Issue | Disposition |
|------|---------|-------|-------------|
| T-01 | 1606 | `camera.fadeIn()` wrong API in example | fix in 17-08 |
| T-02 | 997-1001 | Stale `dialog.tick()` bullet points | fix in 17-09 |
| T-03 | 1107-1113 | Stale `menu.tick()` bullet points | fix in 17-09 |
| T-04 | 1309 | Phase number reference in public doc | fix in 17-10 |
| T-05 | 1735-1745 | Float gravity/friction values vs Int API | fix in 17-08 |
| T-06 | ~820-900 | SoundRef.play() absent, no stale caveat | file as backlog-todo in 17-08 |
| T-10 | 44-45, 58 | subpixel{} needs IR clarification | fix in 17-10 (DOCS-03 Fix 2) |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan produces only a planning-layer evidence file; no code stubs exist.

## Threat Flags

None — this plan reads source and writes a planning-evidence file; no trust boundaries crossed.

## Self-Check: PASSED

- `evidence/DOCS-AUDIT.md` exists: FOUND (d4924015)
- 174 verdict rows present (grep confirmed)
- All 13 section names present
- Full-Document Triage Sweep heading present
- FEAT-STATE-MACHINES referenced
- All 12 FEAT-* backlog files named
- Commit d4924015 verified
