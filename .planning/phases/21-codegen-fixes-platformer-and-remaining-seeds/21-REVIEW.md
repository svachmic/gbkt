---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
reviewed: 2026-06-14T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/TilemapCollisionGate.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
  - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt
  - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt
  - gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt
  - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt
  - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: fixed
---

# Phase 21: Code Review Report

**Reviewed:** 2026-06-14
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 21 delivers four substantive changes: (1) `pivotAdjust` config-key lift into the DSL (D-05/SEED-021), (2) Path C tilemap-collision predicate consolidation into a shared util (SEED-022), (3) partial round-trip deserialization for the 10 previously-stub serializers in `GameIRSerializer` (SEED-020), and (4) GBC-mode fix in `PlatformerTemplateUatTest`.

The TilemapCollisionGate utility, `GBDKPipeline` delegation, and UAT test fix are clean. The core issues identified are:

- A **misleading/false-positive `System.err` warning** in `PlatformerVisitor` when Path A or Path B games (which use legacy `platformerPhysics { solidThreshold }` rather than the `tilemapCollision { }` DSL) reach `buildTilemapPhysicsUpdateFunction`.
- A **Project Rule #1 (no magic strings) violation**: the `"pivotAdjust"` config key is a duplicate string literal appearing in both the DSL builder (producer) and the visitor (consumer) with no shared constant.
- A **KDoc contract mismatch** in `GameIRSerializer`: the round-trip table claims `name` and `categoryId` are "Recovered fields" for `ItemDef`, but the serialize side uses `serializeSimple` which only writes `id`. On a serialize → deserialize round-trip, `name` and `categoryId` are silently reset to `""`.
- A **crash risk** on malformed external JSON: 8 new deserializer lambdas call `obj.getString("id")` (throwing) instead of `obj.optString("id", "")`, which breaks the documented "no crash on malformed input" contract.

---

## Structural Findings (fallow)

No structural pre-pass provided.

---

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Misleading `System.err` warning fires for legacy solidThreshold games (Path A/B), not just missing-pivotAdjust games

**File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:616-624`

**Issue:** `buildTilemapPhysicsUpdateFunction` is reached whenever `gameUsesTilemapCollision` returns `true`. That predicate has three detection paths: Path C (tilemap_collision system present → `tcSystem` non-null), Path A (platformer_physics system with `solidThreshold`), and Path B (per-zone `platformerPhysicsOverride` with `solidThreshold`). For Path A and Path B, there is no `tilemap_collision` GenericSystem, so `tcSystem` is `null`. The pivot-adjust resolution:

```kotlin
val pivotAdjust: Int =
    (tcSystem?.config?.get("pivotAdjust") as? Int)
        ?: run {
            System.err.println(
                "WARNING: tilemapCollision bound but no pivotAdjust declared; ..."
            )
            (REFERENCE_FRAME_HEIGHT - REFERENCE_PIVOT_Y - height).coerceAtLeast(0)
        }
```

When `tcSystem` is `null`, `tcSystem?.config?.get("pivotAdjust")` is `null`, the `run` block fires, and the warning "tilemapCollision bound but no pivotAdjust declared" is printed. But for a Path A/B game, no `tilemapCollision { }` block was ever declared — the message is factually wrong. Any existing game that uses `platformerPhysics { solidThreshold(N) }` will emit a spurious warning on every codegen run.

**Fix:** Guard the warning on whether `tcSystem` was actually found (i.e., Path C was taken). Only emit the warning if the game did declare a `tilemapCollision { }` system but omitted `pivotAdjust`:

```kotlin
val pivotAdjust: Int =
    if (tcSystem != null) {
        (tcSystem.config["pivotAdjust"] as? Int)
            ?: run {
                System.err.println(
                    "WARNING: tilemapCollision { } declared but no pivotAdjust set; " +
                        "using fallback geometry ($REFERENCE_FRAME_HEIGHT, $REFERENCE_PIVOT_Y)"
                )
                (REFERENCE_FRAME_HEIGHT - REFERENCE_PIVOT_Y - height).coerceAtLeast(0)
            }
    } else {
        // Path A or Path B: no tilemapCollision system; silently use reference geometry.
        (REFERENCE_FRAME_HEIGHT - REFERENCE_PIVOT_Y - height).coerceAtLeast(0)
    }
```

---

### WR-02: Project Rule #1 violated — `"pivotAdjust"` config key is a duplicated magic string with no shared constant

**File (producer):** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt:699`
**File (consumer):** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:617`

**Issue:** The project rule stated in `CLAUDE.md` and reinforced by `feedback_no_magic_strings.md` is: "DSL must reflect names from property delegates / lambda params, never duplicate as String params." The `"pivotAdjust"` key is written as a bare string literal in both the DSL builder `build()` method and in the visitor's `config.get(...)` call. A typo in either location silently causes the visitor to miss the config key, fall through to the fallback, and emit a wrong pivot offset with no compile-time error. This is exactly the failure mode the rule is designed to prevent.

The same string appears in the warning message body and in tests, multiplying the surface for silent divergence.

**Fix:** Define a companion constant in `TilemapCollisionBuilder` (or a shared constants file) and reference it from both sides:

```kotlin
// In TilemapCollisionBuilder companion object:
companion object {
    const val CONFIG_KEY_PIVOT_ADJUST = "pivotAdjust"
}
```

Then reference `TilemapCollisionBuilder.CONFIG_KEY_PIVOT_ADJUST` in the builder's `build()` method and in `PlatformerVisitor.buildTilemapPhysicsUpdateFunction`. This makes a key rename a single-site refactor caught by the compiler.

---

### WR-03: `GameIRSerializer` deserializers call `getString("id")` (throwing) instead of `optString("id", "")` — crashes on malformed external JSON

**File:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt:221, 231, 235, 242, 250, 254, 260, 264`

**Issue:** The class-level KDoc states "Absent/null JSON keys return `emptyList()` via `deserializeList` — no crash on malformed input." Eight new deserializer lambdas violate this contract by calling `obj.getString("id")`, which throws `org.json.JSONException` if the `"id"` key is absent or null. The serializer produces correct JSON when serializing from Kotlin IR, but the documented use case is external-tool consumption — non-JVM frontends and debuggers that construct GameIR JSON directly. A tool that emits a `ZoneIR` object without the `"id"` key causes a hard crash in the deserializer instead of a graceful fallback.

All other `optString` calls in this method use the safe two-argument form. The eight `getString` calls are the only non-resilient reads added in this phase:

```
line 221: ZoneIR(id = obj.getString("id"), ...)
line 231: GlobalFlagsIR(id = obj.getString("id"))
line 235: ItemCategoryDef(id = obj.getString("id"))
line 242: ItemDef(id = obj.getString("id"), ...)
line 250: ContainerIR(id = obj.getString("id"), ...)
line 254: DropTableIR(id = obj.getString("id"))
line 260: SwitchObjectIR(id = obj.getString("id"), ...)
line 264: CollisionGroupIR(id = obj.getString("id"))
```

**Fix:** Replace all eight with `optString("id", "")` to match the resilient pattern used throughout the rest of the deserializer:

```kotlin
ZoneIR(id = obj.optString("id", ""), ...)
GlobalFlagsIR(id = obj.optString("id", ""))
// ... same for the other six
```

---

## Info

### IN-01: `GameIRSerializer` KDoc table incorrectly claims `name` and `categoryId` are "Recovered fields" for `ItemDef`

**File:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt:45`

**Issue:** The KDoc round-trip contract table reads:

```
|[ItemDef] | id, name, categoryId | maxStack, effects, buyPrice, dropWeight |
```

However, the serializer uses `serializeSimple("ItemDef", it.id)` at line 131, which only writes `{type, id}`. The deserializer at lines 238-245 reads `name` with `optString("name", "")` and `categoryId` with `optString("categoryId", "")`. Since neither field was written, both will always deserialize as `""`, regardless of the original values. The table's "Recovered fields" column is therefore inaccurate — only `id` survives the round-trip. For `ContainerIR`, the table correctly says `slots` is unsupported (defaults to 0) even though the deserializer also reads `slots` — it just always gets 0 because the serializer doesn't write it.

This is a documentation bug that would mislead a developer extending the serializer or debugging round-trip failures for RPG item data.

**Fix:** Correct the KDoc table entry for `ItemDef`:

```
|[ItemDef] | id | name (defaults to ""), categoryId (defaults to ""), maxStack, effects, buyPrice, dropWeight |
```

Or, alternatively, update the serializer to use a dedicated `serializeItemDef` function that emits `name` and `categoryId` to make the "Recovered" claim true.

---

### IN-02: `PlatformerVisitor.buildTilemapPhysicsUpdateFunction` contains stale comment fragment (dead text from old metasprite-lookup code removal)

**File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:596-608`

**Issue:** After the SEED-021 refactor removed the metasprite-lookup dance, lines 596-608 contain a multi-line comment block that begins describing the old lookup strategy ("locate the player metasprite by matching its `posYVarName` against the tilemap-collision system's `posYVar` binding…") but is cut off mid-sentence at "…so the strings" (line 608 ends with "the strings"). The next line (609) begins the new SEED-021 comment without closing the old thought. This is the remnant of an improperly merged comment block that makes the code harder to read and may confuse future maintainers about what the code actually does.

**Fix:** Remove lines 604-608 (the cut-off old comment) and leave only the new SEED-021 comment block starting at line 609:

```kotlin
// SEED-021: resolved from DSL config key set by TilemapCollisionBuilder.pivotAdjust(v).
// This lifts resolution out of the metasprite-lookup dance into the DSL as the single
// source of truth per Project Rule #1. Falls back to companion constants when the key
// is absent...
```

---

_Reviewed: 2026-06-14_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
