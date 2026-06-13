---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "08"
subsystem: docs
tags: [docs, dsl-reference, stale-api, cleanup]
dependency_graph:
  requires: [17-01, 17-04]
  provides: [DOCS-03-fixes, sections-1-4-accurate]
  affects: [context/DSL_REFERENCE.md]
tech_stack:
  added: []
  patterns: [implemented-only-truth, source-verified-snippets]
key_files:
  created: []
  modified:
    - context/DSL_REFERENCE.md
decisions:
  - "State Machine DSL section replaced with Animation State Machine section documenting the real animationStates{}/setAnimationState() API (ActorBuilder.kt:913, ScriptBuilder.kt:531)"
  - "Dialog section rewrote to function-style config API; DialogHandle.tick/isActive/isComplete/show/hide removed (not on DialogHandle in UIBuilders.kt)"
  - "Menu section rewrote with direct MenuBuilder methods; style{} block, gridMenu(), menu.tick(), isVisible/isActive/selectedIndex removed"
  - "Save Data Fields section replaced with accurate slots()/checksum()/version() SaveDataBuilder API from SystemBuilders.kt"
  - "Deprecated API block (assign/varRef/literal) verified accurate via DOCS-AUDIT T-09 — no migration arrow changes needed"
metrics:
  duration: 4 minutes
  completed: 2026-06-12
  tasks_completed: 2
  files_changed: 1
---

# Phase 17 Plan 08: DOCS-03 Fixes + Sections 1-4 Rewrites Summary

DOCS-03 fixes applied (subpixel{} no-IR clarification); sections 1-4 (State Machine, Dialog, Menu, Save Data) rewritten as implemented-only truth with stale-API caveat banners removed.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Apply DOCS-03 fixes (deprecated block verified, subpixel{} clarified) | 0d0a064d | context/DSL_REFERENCE.md |
| 2 | Rewrite sections 1-4 from source; remove 4 stale-API caveat banners | eb0c6aaa | context/DSL_REFERENCE.md |

## What Was Built

### Task 1: DOCS-03 Fixes

**Fix 1 — Deprecated API block (lines 35-38):** Cross-checked `assign()` / `varRef()` / `literal()` migration arrows against `ScriptBuilder.kt`. DOCS-AUDIT T-09 verdict `accurate` confirmed — the migration arrows (`score set 0`, direct `score` use, raw Int auto-wrap) are all correct. No arrow changes needed. Recorded in SUMMARY as required.

**Fix 2 — subpixel{} clarification:**
- Section header (line 44-46): expanded from "no-op `subpixel { }` scope" to explicitly state "emits no IR — variables declared inside are recorded at the enclosing game scope, not a sub-scope"
- Inline comment (line 59-60): aligned to match — "emits no IR — variables inside are at enclosing game scope, not a sub-scope"
- Both text locations now state the no-op behavior unambiguously (header + inline comment consistent)

### Task 2: Sections 1-4 Rewrites

**Section 1 — State Machine DSL → Animation State Machine:**
- Removed: absent `states("player") { "idle" { enter/on/tick } }` DSL, `playerState.start()`, `playerState.update()`
- Added: accurate docs for `animationStates { state("idle") { ... }; transition(...) { ... } }` (ActorBuilder.kt:913) and `setAnimationState(actor, "stateName")` (ScriptBuilder.kt:531)
- Section renamed to "Animation State Machine" to match the real API

**Section 2 — Dialog System DSL:**
- Removed: property-style config (`speaker = "Elder"`, `textSpeed = 3`), nested `box { size = 20 x 6; padding = 1 }`, `elder.tick()`, `elder.isComplete`, `elder.show()`, `elder.hide()`
- Added: accurate function-style config (`speaker("Elder")`, `textSpeed(3)`, `box(x, y, width, height)`, `border(BorderStyle.SIMPLE)`, `fontMode(FontMode.FIXED_WIDTH)`, `portrait(asset(...))`) per UIBuilders.kt:64-132; `say(text)` and `say(vararg)` and `choice { option() { } }` per UIBuilders.kt:154-251

**Section 3 — Menu System DSL:**
- Removed: `style { position(5,8); cursor = ">"; border = BorderStyle.ROUNDED; spacing = 2 }`, `mainMenu.tick()`, `parent = mainMenu`, `toggle() { onChange { } }`, `slider(0..7) { step = 1 }`, `option() { choices(...) }`, `gridMenu()`, `itemsFrom(source) { block }`, `mainMenu.isVisible`, `mainMenu.isActive`, `mainMenu.selectedIndex`
- Added: accurate `cursor(">")`, `position(x,y,w,h)`, `parent(menuHandle)`, `toggle(label, variable)` (no block), `slider(label, variable, min, max, step)` (separate Int params), `option(label, variable, listOf(...))`, `itemsFrom(arrayVar)` (no block), `layout(MenuLayout.GRID)`, `columns(n)` per UIBuilders.kt:273-435; `MenuHandle.show()` / `MenuHandle.hide()` per UIBuilders.kt:451-469

**Section 4 — Save Data Fields → Save Data Configuration:**
- Removed: stale `saveData("mygame") { var score by u16Field(); ... config { slots = 3; checksum = Checksum.CRC8; magic = "GBKT"; version = 1 } }`, `save.exists()`, `save.load()`, `save.score +=`, `save.save()`, flags/array field access, `save.erase()`, `save.eraseAll()`, `save.copy()`
- Added: accurate `saveData { slots(3); checksum(true); version(1) }` per SystemBuilders.kt:139-167; transient var example; trigger via `triggerSystem(saves)`
- T-04 fix applied: "In Phase 13.1 and later" replaced with clean "The cartridge type is NOT auto-upgraded"

**Stale-API caveat banner count:** 12 → 8 (4 removed, one per rewritten section)

## Deviations from Plan

None — plan executed exactly as written.

The DOCS-AUDIT triage table showed T-10 (subpixel{}) as "fix-in-17-10" but PLAN.md Task 1 explicitly assigns it to 17-08. PLAN.md is the authoritative instruction — the fix was applied here. No impact on subsequent plans.

## Known Stubs

None. These are documentation prose edits — no runtime code stubs.

## Threat Flags

None. Public documentation; no secrets. T-17-08 (Information Disclosure) accepted per threat model.

## Self-Check: PASSED

- `context/DSL_REFERENCE.md` exists: confirmed
- Commit 0d0a064d exists: confirmed (`git log --oneline | grep 0d0a064d`)
- Commit eb0c6aaa exists: confirmed (`git log --oneline | grep eb0c6aaa`)
- Stale-API caveat count: 12 → 8 (PASS)
- No `states("player")` in file: confirmed (`grep -c 'states("player")' context/DSL_REFERENCE.md` → 0)
- No `elder.tick()` in file: confirmed
- No `gridMenu` in file: confirmed
- No `u16Field` in file: confirmed
- No v0.2.0/planned breadcrumbs added in sections 1-4: confirmed
