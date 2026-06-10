/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SDCC SCAFFOLDING WARNINGS — INVARIANT TESTS  (D-09)
//
// Four independent tests, one per SDCC warning code (84, 85-from, 85-to, 126).
// Each test names its SDCC warning code in the assertion message so failures are
// unambiguously attributed without reading stack traces.
//
// Analogs: SpriteRenderingTest.kt (same package, same fixture+assertion shape)
// =============================================================================

/** Build a minimal GameIR with one actor so show_sprites_range is emitted. */
private fun buildMinimalGameIR(): GameIR {
    val actor =
        ActorIR(
            id = "player",
            position = PositionDef(80, 72),
            sprite =
                SpriteDef(
                    assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                    size = SizeDef(8, 8),
                ),
        )
    return GameIR(
        name = "ScaffoldingTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        actors = listOf(actor),
        scenes = listOf(SceneIR(id = "main")),
        startScene = "main",
    )
}

/**
 * Extract the body of a named C function from a C source string.
 *
 * Finds the function signature by locating `fnName` followed by `(`, then counts opening/closing
 * braces from the first `{` after the signature to find the matching closing brace. Returns the
 * substring between the outer braces (exclusive).
 *
 * Per CLAUDE.md §"Scope-level grep gates (corollary)": per-function invariants MUST extract the
 * function body via brace-walk, not use a file-level grep.
 */
private fun extractFunctionBody(c: String, fnName: String): String {
    // Find the function signature — look for the function name followed by '('
    val sigIdx = c.indexOf("$fnName(")
    if (sigIdx < 0) return ""
    // Find the opening brace after the signature
    val openIdx = c.indexOf('{', sigIdx)
    if (openIdx < 0) return ""
    // Walk braces to find matching close
    var depth = 0
    var closeIdx = -1
    for (i in openIdx until c.length) {
        when (c[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    closeIdx = i
                    break
                }
            }
        }
    }
    if (closeIdx < 0) return ""
    return c.substring(openIdx + 1, closeIdx)
}

class ScaffoldingWarningsTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: SDCC warning 84 — uninitialized variable `_d` in delay_frames
    //
    // Fix 3a: CVarDecl("_d", CU8, initializer = CLiteral(0))
    // Expected emission: "UINT8 _d = 0u;"
    // =========================================================================
    @Test
    fun `delay_frames _d initialized to 0 (SDCC warning 84)`() {
        val gameIR = buildMinimalGameIR()
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 _d = 0u;"),
            "SDCC warning 84: _d must be initialized to 0u; current emission still uses uninitialized form",
        )
        // The uninitialized form must NOT appear
        assertFalse(
            Regex("UINT8 _d;\\s*\n").containsMatchIn(mainC),
            "SDCC warning 84: uninitialized 'UINT8 _d;' form found — must be replaced with 'UINT8 _d = 0u;'",
        )
    }

    // =========================================================================
    // Test 2: SDCC warning 85 (from) — unused parameter 'from' in show_sprites_range
    //
    // Fix 3b: prepend CRawCode("(void)from;") to show_sprites_range body
    // =========================================================================
    @Test
    fun `show_sprites_range silences unused param 'from' with (void) (SDCC warning 85)`() {
        val gameIR = buildMinimalGameIR()
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("(void)from;"),
            "SDCC warning 85: unused parameter 'from' must be silenced with (void)from;",
        )
    }

    // =========================================================================
    // Test 3: SDCC warning 85 (to) — unused parameter 'to' in show_sprites_range
    //
    // Independent test per D-09: each parameter gets its own test for
    // unambiguous failure attribution.
    //
    // Fix 3b: prepend CRawCode("(void)to;") to show_sprites_range body
    // =========================================================================
    @Test
    fun `show_sprites_range silences unused param 'to' with (void) (SDCC warning 85)`() {
        val gameIR = buildMinimalGameIR()
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("(void)to;"),
            "SDCC warning 85: unused parameter 'to' must be silenced with (void)to;",
        )
    }

    // =========================================================================
    // Test 4: SDCC warning 126 — unreachable return after while(1) in main()
    //
    // Fix 3c: remove add(CReturn()) after add(CWhile(CVar("1"), gameLoopBody))
    //
    // Per CLAUDE.md §"Scope-level grep gates (corollary)": extract main() body
    // via brace-walk helper, then assert the trimmed body does NOT end with "return;"
    // =========================================================================
    @Test
    fun `main() has no trailing return after while(1) (SDCC warning 126)`() {
        val gameIR = buildMinimalGameIR()
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        val mainBody = extractFunctionBody(mainC, "main")
        assertTrue(
            mainBody.isNotEmpty(),
            "SDCC warning 126: could not extract main() body for assertion",
        )

        assertFalse(
            mainBody.trimEnd().endsWith("return;"),
            "SDCC warning 126: trailing return after while(1) is unreachable dead code; must be removed",
        )
    }
}
