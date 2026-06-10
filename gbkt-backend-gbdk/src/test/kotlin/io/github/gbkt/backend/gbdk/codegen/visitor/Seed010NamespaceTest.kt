/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 10.1 Plan 05 — SEED-010 (CR-03) + WR-01 closure.
 *
 * **CR-03 (namespacing):** `MetaspriteVisitor.generateMetaspriteDescriptor` currently emits
 * `sprite_metasprite_$index[]` per-frame arrays AND a single `sprite_metasprites[]` pointer table —
 * both names are global-namespace literals that COLLIDE when ≥2 metasprites coexist (lcc reports
 * "duplicate definition" link errors). Plan 05 namespaces descriptor symbols as
 * `sprite_${ms.id}_frame_$index[]` / `sprite_${ms.id}_frames[]` so two distinct metasprites emit
 * non-colliding names.
 *
 * **WR-01 (var-ref reads):** `MetaspriteVisitor.generateMetaspriteFrameSwitch` hardcodes the
 * canonical `_idx` / `_rot` / `_posX` / `_posY` literals in every emission line, making per-
 * metasprite variable namespacing impossible (every metasprite shares the same 4 globals). Plan 05
 * adds 4 default parameters to the method signature so callers can pass user-bound variable names
 * from `MetaspriteIR.{posXVarName, posYVarName, idxVarName, rotVarName}` (the substrate landed by
 * Plan 03). The defaults preserve the canonical `_posX`/`_posY`/`_idx`/ `_rot` literals so Phase
 * 10's `Metasprites.kt` example — which does NOT call the new posX/ posY/idx/rot binders —
 * continues to emit identical C shape (Pitfall 6 mitigation).
 *
 * Reference:
 * - `.planning/seeds/SEED-010-metasprites-symbol-collision-multi-metasprite.md`
 * - `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-05-PLAN.md`
 * - `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-03-SUMMARY.md` (IR
 *   substrate: posXVarName/posYVarName/idxVarName/rotVarName nullable fields)
 *
 * **RED-state expectation (pre-Task-2):** This file will FAIL TO COMPILE because the
 * `generateMetaspriteFrameSwitch` overloaded signature (with posXVar/posYVar/idxVar/rotVar
 * parameters) does not exist yet. That compile failure IS the RED signal per Plan 05 Task 1
 * acceptance criteria ("kotlin tests that don't compile count as RED for our purposes"). After Task
 * 2 lands the parameterized signature + namespaced descriptor symbols, all 3 tests below flip
 * GREEN.
 */
class Seed010NamespaceTest {

    private val elephantMetasprite =
        MetaspriteIR(
            id = "elephant",
            frames =
                listOf(
                    MetaspriteFrame(
                        tiles =
                            listOf(
                                MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                            )
                    ),
                    MetaspriteFrame(
                        tiles =
                            listOf(
                                MetaspriteTile(relX = 0, relY = 0, tileId = 2),
                                MetaspriteTile(relX = 8, relY = 0, tileId = 3),
                            )
                    ),
                ),
        )

    private val tigerMetasprite =
        MetaspriteIR(
            id = "tiger",
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 4)))
                ),
        )

    // =========================================================================
    // TEST 1 (CR-03): Two metasprites emit DISTINCT descriptor symbol names.
    //   - elephant → "sprite_elephant_frame_0", "sprite_elephant_frame_1", "sprite_elephant_frames"
    //   - tiger    → "sprite_tiger_frame_0",    "sprite_tiger_frames"
    //   - NEITHER  → "sprite_metasprites"  (the colliding old name)
    // =========================================================================
    @Test
    fun `two_metasprites_emit_distinct_descriptor_symbol_names`() {
        val elephantText = MetaspriteVisitor.generateMetaspriteDescriptor(elephantMetasprite).code
        val tigerText = MetaspriteVisitor.generateMetaspriteDescriptor(tigerMetasprite).code

        // Elephant emits its namespaced pointer table
        assertTrue(
            elephantText.contains("sprite_elephant_frames"),
            "Expected elephant descriptor to contain 'sprite_elephant_frames' " +
                "(CR-03 namespaced pointer table) in:\n$elephantText",
        )
        // Elephant emits its namespaced per-frame arrays
        assertTrue(
            elephantText.contains("sprite_elephant_frame_0"),
            "Expected elephant descriptor to contain 'sprite_elephant_frame_0' " +
                "(CR-03 namespaced frame 0 array) in:\n$elephantText",
        )
        // Elephant does NOT emit the colliding global name
        assertFalse(
            elephantText.contains("sprite_metasprites"),
            "Expected elephant descriptor to NOT contain 'sprite_metasprites' " +
                "(CR-03 — old global-namespace name is the collision source) in:\n$elephantText",
        )

        // Tiger emits its namespaced pointer table
        assertTrue(
            tigerText.contains("sprite_tiger_frames"),
            "Expected tiger descriptor to contain 'sprite_tiger_frames' " +
                "(CR-03 namespaced pointer table) in:\n$tigerText",
        )
        // Tiger does NOT emit the colliding global name
        assertFalse(
            tigerText.contains("sprite_metasprites"),
            "Expected tiger descriptor to NOT contain 'sprite_metasprites' " +
                "(CR-03 — old global-namespace name is the collision source) in:\n$tigerText",
        )

        // Concatenated outputs MUST NOT have duplicate `sprite_metasprites[` substrings
        // (the linker collision pattern). Count must be 0.
        val combined = elephantText + tigerText
        val collisionCount = combined.split("sprite_metasprites[").size - 1
        assertTrue(
            collisionCount == 0,
            "Expected zero occurrences of colliding 'sprite_metasprites[' across two " +
                "metasprite descriptors (found $collisionCount). This is the link-error " +
                "fingerprint CR-03 closes.\nCombined output:\n$combined",
        )
    }

    // =========================================================================
    // TEST 2 (WR-01): Two metasprites with distinct rotVar/idxVar bindings emit
    // distinct variable references — and BOTH preserve Plan 04's `_<id>_subPalette`
    // emission (regression guard: the `_rot` → `$rotVar` rename must propagate
    // through the 3 buf.append lines added by Plan 04).
    // =========================================================================
    @Test
    fun `two_metasprites_with_distinct_rot_vars_emit_distinct_var_refs`() {
        val elephantText =
            MetaspriteVisitor.generateMetaspriteFrameSwitch(
                    elephantMetasprite,
                    posXVar = "_elephant_posX",
                    posYVar = "_elephant_posY",
                    idxVar = "_elephant_idx",
                    rotVar = "_elephant_rot",
                )
                .code
        val tigerText =
            MetaspriteVisitor.generateMetaspriteFrameSwitch(
                    tigerMetasprite,
                    posXVar = "_tiger_posX",
                    posYVar = "_tiger_posY",
                    idxVar = "_tiger_idx",
                    rotVar = "_tiger_rot",
                )
                .code

        // Elephant uses its namespaced rot var; does NOT leak tiger's name
        assertTrue(
            elephantText.contains("_elephant_rot"),
            "Expected elephant frame-switch to contain '_elephant_rot' (WR-01 var-ref read) " +
                "in:\n$elephantText",
        )
        assertFalse(
            elephantText.contains("_tiger_rot"),
            "Expected elephant frame-switch to NOT contain '_tiger_rot' (var-ref isolation) " +
                "in:\n$elephantText",
        )

        // Tiger uses its namespaced rot var; does NOT leak elephant's name
        assertTrue(
            tigerText.contains("_tiger_rot"),
            "Expected tiger frame-switch to contain '_tiger_rot' (WR-01 var-ref read) " +
                "in:\n$tigerText",
        )
        assertFalse(
            tigerText.contains("_elephant_rot"),
            "Expected tiger frame-switch to NOT contain '_elephant_rot' (var-ref isolation) " +
                "in:\n$tigerText",
        )

        // Regression guard for Plan 04 emission (D-V3 + IN-01):
        // The `_<id>_subPalette = subpal;` line must survive the `_rot` → `$rotVar` rename.
        // (The local `subpal` name is NOT a var-ref — it's a method-local; the assignment
        // RHS stays `subpal` regardless of rotVar substitution.)
        assertTrue(
            elephantText.contains("_elephant_subPalette = subpal;"),
            "Expected elephant frame-switch to preserve Plan 04 '_elephant_subPalette = subpal;' " +
                "(D-V3 emission must survive the WR-01 rename) in:\n$elephantText",
        )
        assertTrue(
            tigerText.contains("_tiger_subPalette = subpal;"),
            "Expected tiger frame-switch to preserve Plan 04 '_tiger_subPalette = subpal;' " +
                "(D-V3 emission must survive the WR-01 rename) in:\n$tigerText",
        )

        // The `subpal = $rotVar >> 2;` local computation must use the parameterized rotVar
        // (proves the WR-01 rename touched the subpal-extraction line too, not just the switch).
        assertTrue(
            elephantText.contains("uint8_t subpal = _elephant_rot >> 2;"),
            "Expected elephant subpal extraction to use '_elephant_rot' (not literal '_rot') " +
                "in:\n$elephantText",
        )
        assertTrue(
            tigerText.contains("uint8_t subpal = _tiger_rot >> 2;"),
            "Expected tiger subpal extraction to use '_tiger_rot' (not literal '_rot') " +
                "in:\n$tigerText",
        )
    }

    // =========================================================================
    // TEST 3 (Pitfall 6 / Phase 10 back-compat): default-parameter call
    // (no var-ref overrides) MUST emit the canonical `_rot`/`_idx`/`_posX`/`_posY`
    // literals — preserving Phase 10 Metasprites.kt emission shape exactly.
    // =========================================================================
    @Test
    fun `default_null_fields_emit_canonical_underscore_names`() {
        // Call with NO parameter overrides — relies on the canonical defaults
        // posXVar = "_posX", posYVar = "_posY", idxVar = "_idx", rotVar = "_rot".
        val text = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite).code

        // Canonical _rot literal present (subpal extraction + switch + flip masks)
        assertTrue(
            text.contains("_rot"),
            "Expected canonical '_rot' literal in default-parameter emission " +
                "(Phase 10 Metasprites.kt back-compat — Pitfall 6 mitigation) in:\n$text",
        )
        // Canonical _idx literal present (sprite_*_frames[_idx] index lookup)
        assertTrue(
            text.contains("_idx"),
            "Expected canonical '_idx' literal in default-parameter emission " +
                "(Phase 10 Metasprites.kt back-compat — Pitfall 6 mitigation) in:\n$text",
        )
        // Canonical _posX literal present (X position in move_metasprite_* args)
        assertTrue(
            text.contains("_posX"),
            "Expected canonical '_posX' literal in default-parameter emission " +
                "(Phase 10 Metasprites.kt back-compat — Pitfall 6 mitigation) in:\n$text",
        )
        // Canonical _posY literal present (Y position in move_metasprite_* args)
        assertTrue(
            text.contains("_posY"),
            "Expected canonical '_posY' literal in default-parameter emission " +
                "(Phase 10 Metasprites.kt back-compat — Pitfall 6 mitigation) in:\n$text",
        )
    }
}
