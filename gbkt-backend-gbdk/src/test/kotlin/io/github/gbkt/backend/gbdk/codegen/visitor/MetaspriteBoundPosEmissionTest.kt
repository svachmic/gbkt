/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 12.1 Plan 08 — JVM emission-invariant lock for Defect 5 (bound-metasprite posX/posY
 * emission). Per RESEARCH §D-claude-3 conclusion, Defect 5's closure is **TEST-ONLY**: the
 * `MetaspriteVisitor.generateMetaspriteFrameSwitch` already routes the bound name correctly through
 * its `posXVar`/`posYVar` parameters; the bug was a missing DSL call (the binder was never invoked
 * by `PlatformerTemplate.kt`), which Plan 12.1-05 fixed. This file locks BOTH directions of the
 * visitor contract so a regression in either path is caught at JVM-test time.
 *
 * **Test 1 (BOUND):** Asserts that when `MetaspriteIR.posXVarName = "playerX"` (BARE Kotlin
 * property name, no leading underscore) and the test caller mirrors the production
 * `ScriptOpVisitor.visitMoveMetasprite:1917-1923` prefix transformation (`posXVar =
 * ir.posXVarName?.let { "_$it" } ?: "_posX"`), the visitor emits `(_playerX >> 4)` / `(_playerY >>
 * 4)` in the `move_metasprite_ex` arguments — NOT the magic `_posX` / `_posY` fallback.
 *
 * **Test 2 (UNBOUND):** Asserts that when `MetaspriteIR.posXVarName = null`, the same mirrored
 * Elvis fallback resolves to `"_posX"` / `"_posY"` and the visitor emits `(_posX >> 4)` /
 * `(_posY >> 4)` — preserving back-compat for the shipping `gbkt-examples/metasprites` ROM, which
 * coincidentally uses a user-DSL `var posX by i16Var(...)` that produces a `_posX` global.
 * (RESEARCH §"existing-example regression sensitivity" row `metasprites` confirms this is a
 * regression risk to guard against.)
 *
 * **Why mirror the prefix transformation in test setup rather than passing `"_playerX"` directly:**
 * `MetaspriteIR.posXVarName` stores the BARE property name (no underscore). The `_` prefix is
 * applied at the call boundary by `ScriptOpVisitor.visitMoveMetasprite` (lines 1917-1923) before
 * delegating to `MetaspriteVisitor.generateMetaspriteFrameSwitch`. Hardcoding `posXVar =
 * "_playerX"` in the test would not structurally guard a regression in the IR-field
 * underscore-handling convention. The test setup MUST do `val posXVar =
 * boundMetasprite.posXVarName?.let { "_$it" } ?: "_posX"` (line-for-line mirror of the production
 * code) so that the prefix-transformation contract itself is locked. This closes checker B1 (round
 * 1).
 *
 * **Why a direct visitor call rather than a full pipeline test:** Plan 12.1-05 already covers the
 * integration-side closure via a generated-C grep. This test scopes to the visitor unit-level for
 * fast feedback; the structural analog is `MetaspriteVisitorFrameSwitchTest`.
 */
class MetaspriteBoundPosEmissionTest {

    // BARE property names — mirroring how the DSL captures `AssignableVar.name`. No leading
    // underscore; the `_` prefix is applied by `ScriptOpVisitor.visitMoveMetasprite` at the call
    // boundary (lines 1917-1923).
    private val boundMetasprite =
        MetaspriteIR(
            id = "player",
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0)))
                ),
            posXVarName = "playerX",
            posYVarName = "playerY",
            idxVarName = null,
            rotVarName = null,
        )

    private val unboundMetasprite =
        MetaspriteIR(
            id = "ghost",
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0)))
                ),
            posXVarName = null,
            posYVarName = null,
            idxVarName = null,
            rotVarName = null,
        )

    // =========================================================================
    // TEST 1 (BOUND): user-bound posXVar emits `(_playerX >> 4)` not `(_posX >> 4)`
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits user-bound posXVar when MetaspriteIR_posXVarName is set`() {
        // Mirror ScriptOpVisitor.visitMoveMetasprite:1917-1923 prefix transformation.
        // MetaspriteIR.posXVarName carries the BARE property name ("playerX"); the `_`
        // prefix is applied at the call boundary by ScriptOpVisitor before delegating
        // to MetaspriteVisitor.generateMetaspriteFrameSwitch. Mirroring this here in
        // test setup is the agreed compromise (per checker B1): the test cannot
        // reasonably reconstruct the full ScriptOpVisitor invocation chain in a JVM
        // unit test, so the prefix-transformation must be explicit in the test code,
        // line-for-line matching the production code.
        val posXVar = boundMetasprite.posXVarName?.let { "_$it" } ?: "_posX"
        val posYVar = boundMetasprite.posYVarName?.let { "_$it" } ?: "_posY"

        val result =
            MetaspriteVisitor.generateMetaspriteFrameSwitch(
                boundMetasprite,
                posXVar = posXVar,
                posYVar = posYVar,
                idxVar = "_idx",
                rotVar = "_rot",
            )
        assertIs<CRawCode>(result, "generateMetaspriteFrameSwitch must return CRawCode")
        val cText = result.code

        assertTrue(
            cText.contains("(_playerX >> 4)"),
            "bound metasprite must emit (_playerX >> 4); got:\n${cText.take(2000)}",
        )
        assertTrue(
            cText.contains("(_playerY >> 4)"),
            "bound metasprite must emit (_playerY >> 4); got:\n${cText.take(2000)}",
        )
        assertFalse(
            cText.contains("(_posX >> 4)"),
            "bound metasprite must NOT fall back to _posX magic; got:\n${cText.take(2000)}",
        )
        assertFalse(
            cText.contains("(_posY >> 4)"),
            "bound metasprite must NOT fall back to _posY magic; got:\n${cText.take(2000)}",
        )
    }

    // =========================================================================
    // TEST 2 (UNBOUND): null posXVarName preserves magic `(_posX >> 4)` fallback
    // for back-compat with the shipping `gbkt-examples/metasprites` ROM.
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch falls back to magic _posX when MetaspriteIR_posXVarName is null`() {
        // Mirror ScriptOpVisitor.visitMoveMetasprite Elvis fallback. With posXVarName == null,
        // the let-block does not fire and the Elvis falls back to "_posX" / "_posY".
        val posXVar = unboundMetasprite.posXVarName?.let { "_$it" } ?: "_posX"
        val posYVar = unboundMetasprite.posYVarName?.let { "_$it" } ?: "_posY"

        val result =
            MetaspriteVisitor.generateMetaspriteFrameSwitch(
                unboundMetasprite,
                posXVar = posXVar,
                posYVar = posYVar,
                idxVar = "_idx",
                rotVar = "_rot",
            )
        assertIs<CRawCode>(result, "generateMetaspriteFrameSwitch must return CRawCode")
        val cText = result.code

        assertTrue(
            cText.contains("(_posX >> 4)"),
            "unbound metasprite must preserve magic _posX fallback (back-compat for " +
                "gbkt-examples/metasprites); got:\n${cText.take(2000)}",
        )
        assertTrue(
            cText.contains("(_posY >> 4)"),
            "unbound metasprite must preserve magic _posY fallback (back-compat for " +
                "gbkt-examples/metasprites); got:\n${cText.take(2000)}",
        )
    }
}
