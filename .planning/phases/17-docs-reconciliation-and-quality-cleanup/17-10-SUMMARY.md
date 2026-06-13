---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "10"
subsystem: docs
tags: [docs, dsl-reference, stale-api, cleanup]
dependency_graph:
  requires: [17-09]
  provides: [sections-10-through-13-accurate, zero-stale-api-caveats]
  affects: [context/DSL_REFERENCE.md]
tech_stack:
  added: []
  patterns: [implemented-only-truth, source-verified-snippets]
key_files:
  created: []
  modified:
    - context/DSL_REFERENCE.md
key-decisions:
  - "Pathfinding section (#10) replaced with PathfindingBuilder config API (gridSize/mapSize/maxOpenNodes/maxPathLength) + pathfindStep/waypointStep script ops; navGrid/findPathTo/Heuristic/weighted-tile API removed (archived in FEAT-PATHFINDING-NAVGRID.md)"
  - "Testing Framework section (#11) replaced with short section pointing to context/TESTING.md as authoritative guide; real tiers named (SimulationContext/GbktTestExtension/MCP server); entire stale testGame()/testScene() DSL removed (archived in FEAT-TESTING-DSL.md)"
  - "Stale testGame() call in Global Flags 'Complete Example' block removed — it was outside the 13 caveated sections but referenced the absent DSL; Rule 2 auto-fix"
  - "Battle section (#12) caveat banner and battleMenu/combatFormulas/battleState/battleTransition subsections removed (archived in FEAT-BATTLE-MENUS.md); simpleBattle/battleUpdate/CombatStates.* docs kept accurate"
  - "Item & Inventory section (#13) replaced with accurate ItemCatalogBuilder (items{}/category{}/item{}) + ContainerBuilder (container{}) API from InventoryBuilders.kt; by-item delegate/ItemCategory enum/EquipSlot removed from core section (archived in FEAT-INVENTORY-DELEGATE.md)"
  - "ZERO Stale-API caveat blocks remain in DSL_REFERENCE.md after this plan — all 13 removed across 17-08/09/10"
requirements-completed: [DOCS-01, DOCS-02]
duration: 8min
completed: 2026-06-12
---

# Phase 17 Plan 10: Sections 10-13 Rewrites Summary

Final batch of stale-API section rewrites. Sections 10-13 (Pathfinding, Testing Framework, Battle, Item & Inventory) are now implemented-only truth. Zero stale-API caveat banners remain in DSL_REFERENCE.md.

## Performance

- **Duration:** ~8 min
- **Completed:** 2026-06-12
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Pathfinding (#10): navGrid/findPathTo/weighted-tiles/Heuristic stale API replaced with PathfindingBuilder config (gridSize/mapSize/maxOpenNodes/maxPathLength from SystemBuilders.kt:315-351) + pathfindStep/waypointStep script ops (ScriptBuilder.kt:611,633); caveat banner removed
- Testing Framework (#11): stale testGame()/testScene() DSL (1,200+ lines) replaced with a 12-line section that points to context/TESTING.md and names the three real tiers; caveat banner removed
- Stale testGame() call removed from Global Flags "Complete Example" block (uncaveated section outside the 13 — Rule 2 auto-fix)
- Battle System (#12): Stale-API caveat banner removed; battleMenu/combatFormulas/Custom Battle States subsections (all absent from codebase) removed; simpleBattle/battleUpdate/CombatStates.* docs retained as accurate
- Item & Inventory (#13): stale by-item-delegate + ItemCategory enum + EquipSlot + inventory{} + inventory.add/remove/contains/equip API replaced with accurate ItemCatalogBuilder (items{}/category{}/item{}) + ContainerBuilder (container{}/slots/categoryFilter) from InventoryBuilders.kt:98-316; caveat banner removed
- **Final state:** ZERO Stale-API caveat blocks in DSL_REFERENCE.md (all 13 caveated sections cleaned across plans 17-08, 17-09, 17-10)

## Task Commits

1. **Task 1: Rewrite Pathfinding (#10) and Testing Framework (#11)** - `63afe76a` (docs)
2. **Task 2: Rewrite Battle (#12) and Item & Inventory (#13); confirm zero caveats** - `929653a4` (docs)

## Files Created/Modified

- `context/DSL_REFERENCE.md` — Sections 10-13 rewritten as implemented-only truth; zero stale-API caveats remain document-wide

## Decisions Made

- Testing Framework: replaced ~200 lines of stale DSL with a concise 12-line section + table pointing to context/TESTING.md. The testGame()/testScene() DSL is archived in FEAT-TESTING-DSL.md.
- Battle: only the three stale subsections (battleMenu/combatFormulas/Custom Battle States) were removed. The simpleBattle/battleUpdate/CombatStates sections above the caveat were accurate and retained unchanged.
- Item & Inventory: ItemEffectBuilder methods (`heal(Int)`, `buff(statId, amount, duration)`, `script {}`) are documented inline in the onUse block; `script {}` replaces the stale `cEmit()` in-use example. The `EquipSlot` enum from `gbkt-genre-rpg` is intentionally omitted from this core-DSL section.
- Global Flags stale example: the entire "Complete Example" block containing testGame() was removed (not just the testGame() call) since the block's sole purpose was to demonstrate the absent testing DSL.

## Deviations from Plan

**Auto-fix [Rule 2 - Missing correction]: Remove stale testGame() in Global Flags section**
- **Found during:** Task 1
- **Issue:** The "## Global Flags System" section had a "### Complete Example" block using the stale `testGame()` DSL. This was outside the 13 caveated sections but still referenced the absent API.
- **Fix:** Removed the entire stale "Complete Example" block.
- **Files modified:** `context/DSL_REFERENCE.md`
- **Commit:** `63afe76a`

## Known Stubs

None. These are documentation prose edits — no runtime code stubs.

## Threat Flags

None. Public documentation; no secrets. T-17-10 (Information Disclosure) accepted per threat model.

## Self-Check: PASSED

- `context/DSL_REFERENCE.md` exists: confirmed
- Commit `63afe76a` exists: confirmed
- Commit `929653a4` exists: confirmed
- Zero "Stale-API caveat" blocks remain: confirmed (grep count = 0)
- `navGrid` count: 0 (PASS)
- `testGame(` count: 0 (PASS)
- `testScene(` count: 0 (PASS)
- `battleMenu` count: 0 (PASS)
- `combatFormulas` count: 0 (PASS)
- `ItemCategory.` count: 0 (PASS)
- `EquipSlot.` count: 0 (PASS)
- `simpleBattle` count: 4 (PASS — documented correctly)
- `pathfindStep` count: 4 (PASS — documented correctly)
- `TESTING.md` count: 2 (PASS — pointer present)
- No v0.2.0/planned breadcrumbs added: confirmed
