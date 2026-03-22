---
phase: 01-ir-foundation-and-dsl
plan: 01
subsystem: ir
tags: [kotlin, sealed-interface, ir, type-hierarchy, platform-annotations]

# Dependency graph
requires: []
provides:
  - "Sealed IR v2 hierarchy in io.github.gbkt.core.ir.v2 package"
  - "GameIR top-level data class (root of compilation tree)"
  - "SceneIR with enter/frame/exit ScriptOp lists"
  - "ActorIR with PlatformAnnotatable fields (bankSlot, vramRange, oamSlot)"
  - "SystemIR sealed hierarchy: Dialog, Sound, Save, Exploration, Camera, Generic"
  - "ScriptOp 24-subtype sealed instruction set"
  - "Expr 9-subtype sealed expression hierarchy"
  - "PlatformAnnotations: BankSlot, VRAMRange, OAMSlot (nullable, default null)"
  - "Types: PositionDef, SizeDef, HitboxDef, SpriteDef, SourceLocation, enums"
  - "AssetRef, Ref foundation types"
  - "Exhaustive when matching compiles without else on all sealed hierarchies"
affects:
  - 01-02-PLAN (DSL builders that produce IR v2 nodes)
  - 01-03-PLAN (example game definitions using v2 IR)
  - future backend codegen (consumes GameIR for code generation)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sealed interface hierarchy with exhaustive when matching (no else branches)"
    - "PlatformAnnotatable interface with nullable annotation fields defaulting to null"
    - "v2 package namespace to coexist with existing ir/ types during rebuild"
    - "TDD: RED tests committed before GREEN implementation"

key-files:
  created:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/GameIR.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/SceneIR.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/ActorIR.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/SystemIR.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/ScriptOp.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/Expr.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/PlatformAnnotations.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/AssetRef.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/Ref.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/Types.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/IRHierarchyTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/ScriptOpTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/ExprTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/PlatformAnnotationsTest.kt
  modified: []

key-decisions:
  - "All IR v2 types placed in io.github.gbkt.core.ir.v2 package (v2 to avoid collision with existing ir/ during rebuild)"
  - "PlatformAnnotatable is an interface (not sealed) so data classes can implement it alongside other sealed hierarchies"
  - "SystemIR includes GenericSystem with Map<String,Any> config for future extensibility"
  - "ScriptOp.sourceLocation has default interface implementation returning null — data class subtypes can override selectively"
  - "StringLiteral added as 9th Expr subtype (beyond the 8 specified in plan) to handle dialog/print text values"

patterns-established:
  - "Sealed IR hierarchy pattern: sealed interface with all subtypes in same package (Kotlin constraint)"
  - "PlatformAnnotatable pattern: nullable annotation fields with null defaults on all IR nodes"
  - "TDD pattern: failing tests define contract, GREEN implementation makes them pass"

requirements-completed: [IR-01, IR-02, IR-04]

# Metrics
duration: 4min
completed: 2026-02-17
---

# Phase 1 Plan 01: IR Foundation Summary

**Sealed IR v2 hierarchy with 24-subtype ScriptOp instruction set, 9-subtype Expr tree, PlatformAnnotatable annotations, and exhaustive when matching — zero external dependencies**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-17T20:26:40Z
- **Completed:** 2026-02-17T20:31:27Z
- **Tasks:** 2 (TDD: 1 RED + 1 GREEN)
- **Files modified:** 14 (10 production, 4 test)

## Accomplishments

- Defined sealed IR v2 hierarchy as the central contract for the new gbkt compiler pipeline
- Proved exhaustive `when` matching compiles without `else` on ScriptOp, Expr, and SystemIR
- Platform annotation fields (bankSlot, vramRange, oamSlot) are nullable, default to null, and copyable on ActorIR, SceneIR, and all SystemIR subtypes
- Zero external dependencies: only kotlin-stdlib used in all production files
- All existing gbkt-core tests pass with no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: RED — failing tests for IR hierarchy, ScriptOp, Expr, and PlatformAnnotations** - `9f89cb0` (test)
2. **Task 2: GREEN — sealed IR v2 hierarchy implementation** - `bb62e01` (feat)

_TDD plan: RED commit before GREEN implementation_

## Files Created/Modified

**Production (10 files):**
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/GameIR.kt` — Top-level game IR data class with nullable startScene
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/SceneIR.kt` — Scene IR with enter/frame/exit ScriptOp lists and actor IDs
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/ActorIR.kt` — Actor IR with position, sprite, hitbox, PlatformAnnotatable fields
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/SystemIR.kt` — Sealed SystemIR hierarchy (Dialog, Sound, Save, Exploration, Camera, Generic)
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/ScriptOp.kt` — 24-subtype sealed ScriptOp instruction set with CameraAction enum
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/Expr.kt` — 9-subtype sealed Expr hierarchy (includes StringLiteral)
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/PlatformAnnotations.kt` — BankSlot, VRAMRange, OAMSlot, PlatformAnnotatable interface
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/AssetRef.kt` — AssetRef with AssetType enum
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/Ref.kt` — Ref with RefKind enum
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/Types.kt` — Foundation types: PositionDef, SizeDef, HitboxDef, SpriteDef, SourceLocation, enums

**Tests (4 files):**
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/IRHierarchyTest.kt` — GameIR/SceneIR/ActorIR/SystemIR construction and exhaustive when
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/ScriptOpTest.kt` — All 24 ScriptOp subtypes, AssignOp variants, defaults
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/ExprTest.kt` — All 9 Expr subtypes, BinaryOp/UnaryOp completeness
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/PlatformAnnotationsTest.kt` — BankSlot/VRAMRange/OAMSlot construction, null defaults, copy

## Decisions Made

- Placed all IR v2 types in `io.github.gbkt.core.ir.v2` package (v2 to avoid collision with existing `ir/` types during the rebuild transition)
- `PlatformAnnotatable` is a plain interface (not sealed) so that data classes can implement it alongside sealed hierarchies without conflict
- `SystemIR` includes `GenericSystem` with `Map<String, Any>` config for extensibility beyond the 5 named systems
- `ScriptOp.sourceLocation` has a default interface implementation returning null — each data class subtype gets it for free and can override
- Added `StringLiteral` as a 9th `Expr` subtype (plan specified 8) to properly handle string values in dialog, print, and sound operations

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added StringLiteral as 9th Expr subtype**
- **Found during:** Task 2 (GREEN implementation)
- **Issue:** Plan specified 8 Expr subtypes. Without StringLiteral, ShowDialog and PrintOp cannot reference string values through the Expr hierarchy — callers would be forced to use RawOp for any string output.
- **Fix:** Added `data class StringLiteral(val value: String) : Expr` and added ExprTest coverage.
- **Files modified:** Expr.kt, ExprTest.kt
- **Verification:** Test `StringLiteral constructs correctly` passes; all other Expr tests pass
- **Committed in:** `bb62e01` (Task 2 feat commit)

---

**Total deviations:** 1 auto-fixed (Rule 2 — missing critical for correctness)
**Impact on plan:** The addition is purely additive — no existing tests broken, plan success criteria all met or exceeded.

## Issues Encountered

None — plan executed cleanly. TDD RED/GREEN cycle completed in one pass.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- IR v2 hierarchy is the stable contract for all subsequent work
- Phase 01-02 (DSL builders) can now define how Kotlin DSL constructs produce IR v2 nodes
- Phase 01-03 (example game definitions) can write DSL for Pong, Breakout, Explorer using v2 types
- Phase 02 (GBDK backend) can implement `GameIR -> C code` code generation against the finalized sealed hierarchy

---
*Phase: 01-ir-foundation-and-dsl*
*Completed: 2026-02-17*

## Self-Check: PASSED

- All 15 files verified to exist on disk
- Both task commits verified in git log: `9f89cb0` (RED tests) and `bb62e01` (GREEN implementation)
- All tests pass: `./gradlew :gbkt-core:test` BUILD SUCCESSFUL
