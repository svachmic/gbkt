---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
fixed_at: 2026-06-14T15:05:00Z
review_path: .planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/21-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 21: Code Review Fix Report

**Fixed at:** 2026-06-14T15:05:00Z
**Source review:** `.planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/21-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 5
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-01: Misleading System.err warning fires for Path A/B games

**Files modified:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
**Commit:** 8297f895
**Applied fix:** Wrapped the `val pivotAdjust` resolution in an `if (tcSystem != null)` guard. When `tcSystem` is null (Path A or Path B — no `tilemapCollision { }` block declared), the fallback geometry is used silently. The `System.err.println` warning now only fires when `tcSystem` is non-null but `pivotAdjust` is absent from the config map (the true "declared but omitted" case). Fallback arithmetic is preserved exactly.

---

### WR-02: Magic-string "pivotAdjust" (Project Rule #1)

**Files modified:**
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt`
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
**Commit:** 8297f895
**Applied fix:** Added `companion object { const val CONFIG_KEY_PIVOT_ADJUST = "pivotAdjust" }` to `TilemapCollisionBuilder`. Updated the producer's `build()` method to use `configBuilder[CONFIG_KEY_PIVOT_ADJUST]` and added an import of `TilemapCollisionBuilder` in `PlatformerVisitor`, where the consumer now references `TilemapCollisionBuilder.CONFIG_KEY_PIVOT_ADJUST`. No bare `"pivotAdjust"` string literal remains at either site.

---

### WR-03: getString("id") crash risk in 8 new deserializers

**Files modified:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt`
**Commit:** ffeaaae5
**Applied fix:** Replaced all 8 `obj.getString("id")` calls with `obj.optString("id", "")` in the `fromJson` deserializer lambdas for `ZoneIR`, `GlobalFlagsIR`, `ItemCategoryDef`, `ItemDef`, `ContainerIR`, `DropTableIR`, `SwitchObjectIR`, and `CollisionGroupIR`. This matches the resilient `optString` pattern used throughout the rest of the method and satisfies the KDoc "no crash on malformed input" contract.

---

### IN-01: KDoc table inaccuracy for ItemDef recovered fields

**Files modified:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt`
**Commit:** ffeaaae5
**Applied fix:** Corrected the KDoc round-trip contract table. The `ItemDef` row now shows only `id` as "Recovered fields" and moves `name` (defaults to "") and `categoryId` (defaults to "") into the "Unsupported (serialize-only)" column, accurately reflecting that `serializeSimple("ItemDef", it.id)` only emits `{type, id}`.

---

### IN-02: Stale comment fragment in PlatformerVisitor

**Files modified:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
**Commit:** 8297f895
**Applied fix:** Removed the 14-line cut-off dead comment block ("Round-5 H1 ... so the strings") that was a remnant of the deleted metasprite-lookup code. The SEED-021 comment block that follows it (starting "SEED-021: resolved from DSL config key...") remains intact as the sole explanation of the pivotAdjust resolution.

---

## Skipped Issues

None.

---

**Test results:** `:gbkt-ir:test :gbkt-genre-platformer:test :gbkt-backend-gbdk:test` — BUILD SUCCESSFUL (all tests green).
**Linting:** `:gbkt-genre-platformer:spotlessApply :gbkt-genre-platformer:detekt :gbkt-ir:spotlessApply :gbkt-ir:detekt` — BUILD SUCCESSFUL.

---

_Fixed: 2026-06-14T15:05:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
