# 19-AUDIT-FIX-02 — FIX-02 Structural-Latent Seed Emission-Guard Audit

## Status

**Confirmation-only phase.** All 5 structural-latent seeds (SEED-007 through SEED-011) are already
guarded by named JVM emission assertions. Zero new guards were authored (D-05 no-duplicate-coverage
decision). This document maps each seed to its pre-existing guard and the reverted-fix scenario it
catches.

**Verification run result (2026-06-13):** BUILD SUCCESSFUL — 17 tests across 5 guard classes,
0 failures, 0 errors. Confirmed GREEN at HEAD (`chore/hardening_0_1_0`).

Run command (all 5 guards in one invocation):

```bash
./gradlew :gbkt-backend-gbdk:test \
  --tests "*.Seed008VramCollisionTest" \
  --tests "*.Seed009BankIncludeTest" \
  --tests "*.Seed010NamespaceTest" \
  --tests "*.Seed011HiwaterFrameScopeTest" \
  :gbkt-lang:test \
  --tests "*.Seed007GameBuilderPaletteSlotTest"
```

---

## 1:1 Seed → Guard Mapping

| SEED | Title | Guarding test file (module-relative path) | Assertion name(s) | Existing or newly authored | Reverted-fix scenario |
|------|-------|------------------------------------------|-------------------|----------------------------|-----------------------|
| SEED-007 | Actor palette slot always defaults to 0 | `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/Seed007GameBuilderPaletteSlotTest.kt` | `sequential_actors_with_auto_slot_get_sequential_slot_indices`, `actor_with_explicit_slot_does_not_consume_auto_slot_counter` | existing | Reverting `actorPaletteAutoSlot++` to `else 0` in `GameBuilder.buildScenesWithActorPalettes()` causes `sequential_actors_with_auto_slot_get_sequential_slot_indices` to fail: expected `[0,1,2,3]`, got `[0,0,0,0]` — every actor palette collapses to slot 0 |
| SEED-008 | Metasprite VRAM collision with actors | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/Seed008VramCollisionTest.kt` | `main_c_actor_and_metasprite_set_sprite_data_use_distinct_start_offsets`, `main_c_set_sprite_data_calls_are_actors_first_then_metasprites` | existing | Reverting unified `buildAllSpriteDataLoadStatements()` to two separate `var nextTile = 0` loops causes `main_c_actor_and_metasprite_set_sprite_data_use_distinct_start_offsets` to find the collision pattern `set_sprite_data(0u, 48u, elephant_tiles)` instead of `set_sprite_data(2u, 48u, elephant_tiles)` |
| SEED-009 | Metasprites header missing in bank1.c | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/Seed009BankIncludeTest.kt` | `bank1_c_includes_metasprites_h_when_scene_frame_has_MoveMetasprite`, `bank1_c_does_not_include_metasprites_h_when_no_scene_uses_metasprite` | existing | Removing the conditional `#include <gbdk/metasprites.h>` from `buildSceneFile()` causes `bank1_c_includes_metasprites_h_when_scene_frame_has_MoveMetasprite` to fail — bank1.c would lack the header despite containing `MoveMetasprite` ops, producing SDCC compile errors in multi-scene games |
| SEED-010 | Symbol collision multi-metasprite games | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/Seed010NamespaceTest.kt` | `two_metasprites_emit_distinct_descriptor_symbol_names`, `two_metasprites_with_distinct_rot_vars_emit_distinct_var_refs`, `default_null_fields_emit_canonical_underscore_names` | existing | Reverting CR-03 namespacing in `MetaspriteVisitor.generateMetaspriteDescriptor` to unnamespaced `sprite_metasprites[]` / `sprite_metasprite_N[]` causes `two_metasprites_emit_distinct_descriptor_symbol_names` to find colliding symbol names in combined elephant+tiger emission (lcc "duplicate definition" link errors) |
| SEED-011 | hiwater collision multi-metasprite per frame | `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/Seed011HiwaterFrameScopeTest.kt` | `play_frame_body_contains_exactly_one_hiwater_init`, `play_frame_body_contains_exactly_one_hide_sprites_range_call`, `title_frame_body_without_metasprite_has_zero_hiwater_init` | existing | Reverting the hoist of `uint8_t hiwater = 0u;` and `hide_sprites_range()` from per-`moveMetasprite()` scope back to per-call scope causes `play_frame_body_contains_exactly_one_hiwater_init` to fail: finds 2 occurrences of `hiwater = 0u` (one per `moveMetasprite()` call) instead of 1 — second call resets OAM cursor, hiding sprites from the first call |

---

## Per-Seed Run Commands (Traceability)

```bash
# SEED-007 — gbkt-lang (DSL builder layer, not codegen backend)
./gradlew :gbkt-lang:test --tests "*.Seed007GameBuilderPaletteSlotTest"

# SEED-008 — gbkt-backend-gbdk
./gradlew :gbkt-backend-gbdk:test --tests "*.Seed008VramCollisionTest"

# SEED-009 — gbkt-backend-gbdk
./gradlew :gbkt-backend-gbdk:test --tests "*.Seed009BankIncludeTest"

# SEED-010 — gbkt-backend-gbdk
./gradlew :gbkt-backend-gbdk:test --tests "*.Seed010NamespaceTest"

# SEED-011 — gbkt-backend-gbdk
./gradlew :gbkt-backend-gbdk:test --tests "*.Seed011HiwaterFrameScopeTest"
```

---

## Guard Details

### SEED-007 — Actor palette slot always defaults to 0

- **Fix location:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt:756-759`
  (`actorPaletteAutoSlot++` counter in `buildScenesWithActorPalettes()`)
- **Guard module:** `gbkt-lang` (placement-by-observability: fix is in DSL builder layer, D-04)
- **Class header documents:** `else 0` bug + `actorPaletteAutoSlot++` fix; sibling fix at
  `SceneBuilder.palette()` merged in commit 2e8fb256 (Phase 10 Plan 16)
- **Test count:** 7

### SEED-008 — Metasprite VRAM collision with actors

- **Fix location:** `gbkt-backend-gbdk/.../GBDKPipeline.kt` — unified
  `buildAllSpriteDataLoadStatements()` with a single `VramAllocator` iterating actors first then
  metasprites (Route A per TRIAGE)
- **Guard module:** `gbkt-backend-gbdk`
- **Class header documents:** pre-fix dual-scope `var nextTile = 0` root cause; expected
  post-fix shape (`set_sprite_data(2u, …, elephant_tiles)`)
- **Test count:** 2

### SEED-009 — Metasprites header missing in bank1.c

- **Fix location:** `gbkt-backend-gbdk/.../GBDKPipeline.kt` — conditional
  `#include <gbdk/metasprites.h>` in `buildSceneFile()` (Route A per TRIAGE)
- **Guard module:** `gbkt-backend-gbdk`
- **Class header documents:** single-scene HOME fast-path escape; pitfall-2 mitigation
  (2-scene construction to force bank1.c emission)
- **Test count:** 2

### SEED-010 — Symbol collision multi-metasprite games

- **Fix location:** `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` — CR-03 namespacing:
  `sprite_${ms.id}_frame_N[]` / `sprite_${ms.id}_frames[]` replace collision-causing
  `sprite_metasprites[]` / `sprite_metasprite_N[]`
- **Guard module:** `gbkt-backend-gbdk`
- **Class header documents:** collision root cause, WR-01 var-ref scope, pitfall-6 backward
  compatibility (defaults preserve canonical `_posX`/`_posY`/`_idx`/`_rot` literals)
- **Note:** Asset-driven `elephant_metasprites[]` / `tiger_metasprites[]` names are naturally
  distinct by PNG filename — SEED-010 guards the procedural `sprite_<id>_frames[]` path only
- **Test count:** 3

### SEED-011 — hiwater collision multi-metasprite per frame

- **Fix location:** `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` — hoisted
  `uint8_t hiwater = 0u;` init and `hide_sprites_range()` call from per-`moveMetasprite()` scope
  to frame-function prelude/postlude (Route A per TRIAGE, D-11)
- **Guard module:** `gbkt-backend-gbdk`
- **Class header documents:** per-call reset root cause; Phase 12 blocker context
  (platformer needs 2+ metasprites per frame)
- **Test count:** 3

---

## Decisions Captured

| Decision | Outcome |
|----------|---------|
| D-04 (placement-by-observability) | SEED-007 guard lives in `gbkt-lang`, not `gbkt-backend-gbdk` — the fix is at the DSL builder layer |
| D-05 (audit-first, no duplicate coverage) | 0 of 5 seeds needed new guards; all pre-existed at HEAD |
| D-06 (standalone audit doc) | This document; kept separate from VERIFICATION.md |
| D-08 (single-commit audit) | This doc + SUMMARY committed together; no production Kotlin modified |
| D-09 (spotless/detekt gate) | Not triggered — plan modifies no Kotlin source |
