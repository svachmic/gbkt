/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// GBC COMPAT EMISSION TESTS
// Verifies D-09 pipeline gaps:
//   1. cgb_compatibility() as first main() statement when target != DMG
//   2. No cgb_compatibility() when target == DMG
//   3. <gbdk/metasprites.h> included when metasprites present
//   4. <gbdk/metasprites.h> NOT included when no metasprites
//
// Uses brace-walk extraction to scope main() body assertions (CLAUDE.md §"Scope-level grep gates").
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper: extract the body of the first function matching `signature`
// ---------------------------------------------------------------------------

/**
 * Extracts the body of the first C function whose signature contains [signature].
 *
 * Performs a brace-balanced walk from the opening `{` after the signature to the matching `}`.
 * Returns the content between the outermost braces (excluding them), or null if not found.
 *
 * This is the Kotlin equivalent of the awk brace-walk used in shell-based tests (CLAUDE.md
 * §"Scope-level grep gates corollary") — prevents file-level grep from matching tokens in OTHER
 * functions that share the same keyword.
 */
private fun extractFunctionBody(source: String, signature: String): String? {
    val sigIdx = source.indexOf(signature)
    if (sigIdx == -1) return null

    // Find the opening brace after the signature
    val openIdx = source.indexOf('{', sigIdx + signature.length)
    if (openIdx == -1) return null

    // Brace-balanced walk to find the matching closing brace
    var depth = 0
    var i = openIdx
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    // Return content between outermost braces
                    return source.substring(openIdx + 1, i)
                }
            }
        }
        i++
    }
    return null // unbalanced braces (malformed input)
}

// ---------------------------------------------------------------------------
// Minimal GameIR builders
// ---------------------------------------------------------------------------

private fun buildGbcCompatGameIR(target: GbcTarget, withMetasprites: Boolean = false): GameIR {
    val metasprites =
        if (withMetasprites) {
            listOf(
                MetaspriteIR(
                    id = "playerMeta",
                    frames =
                        listOf(
                            MetaspriteFrame(
                                tiles =
                                    listOf(
                                        MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                        MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                                    )
                            )
                        ),
                )
            )
        } else {
            emptyList()
        }
    return GameIR(
        name = "GbcCompatTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2, gbcTarget = target),
        scenes = listOf(SceneIR(id = "play")),
        startScene = "play",
        metasprites = metasprites,
    )
}

// =============================================================================
// TEST CLASS
// =============================================================================

class GbcCompatEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: GBC_COMPATIBLE target → cgb_compatibility() is FIRST main() statement
    // Uses brace-walk to scope assertion to main() body only.
    // =========================================================================

    @Test
    fun `GBC_COMPATIBLE target emits cgb_compatibility as first main statement`() {
        val gameIR = buildGbcCompatGameIR(GbcTarget.GBC_COMPATIBLE)
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBody(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        // cgb_compatibility() must be in main() body
        assertTrue(
            mainBody.contains("cgb_compatibility()"),
            "Expected 'cgb_compatibility()' in main() body for GBC_COMPATIBLE target. main() body:\n$mainBody",
        )

        // cgb_compatibility() must appear BEFORE sound hardware init (NR52_REG)
        val cgbIdx = mainBody.indexOf("cgb_compatibility()")
        val nr52Idx = mainBody.indexOf("NR52_REG")
        assertTrue(
            cgbIdx < nr52Idx,
            "cgb_compatibility() must appear before NR52_REG (sound init) in main() body. " +
                "cgb_compatibility at index $cgbIdx, NR52_REG at index $nr52Idx",
        )
    }

    @Test
    fun `GBC_ONLY target also emits cgb_compatibility in main`() {
        val gameIR = buildGbcCompatGameIR(GbcTarget.GBC_ONLY)
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBody(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        assertTrue(
            mainBody.contains("cgb_compatibility()"),
            "Expected 'cgb_compatibility()' in main() body for GBC_ONLY target",
        )
    }

    // =========================================================================
    // Test 2: DMG target → NO cgb_compatibility() in main()
    // =========================================================================

    @Test
    fun `DMG target does NOT emit cgb_compatibility in main`() {
        val gameIR = buildGbcCompatGameIR(GbcTarget.DMG)
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBody(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        assertFalse(
            mainBody.contains("cgb_compatibility()"),
            "DMG target must NOT emit 'cgb_compatibility()' in main(). main() body:\n$mainBody",
        )
    }

    // =========================================================================
    // Test 3: Metasprites present → <gbdk/metasprites.h> included in main.c
    // =========================================================================

    @Test
    fun `metasprites present includes gbdk metasprites h in main c`() {
        val gameIR = buildGbcCompatGameIR(GbcTarget.DMG, withMetasprites = true)
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("<gbdk/metasprites.h>"),
            "Expected '<gbdk/metasprites.h>' in main.c when metasprites are present",
        )
    }

    // =========================================================================
    // Test 4: No metasprites → <gbdk/metasprites.h> NOT included
    // =========================================================================

    @Test
    fun `no metasprites does NOT include gbdk metasprites h`() {
        val gameIR = buildGbcCompatGameIR(GbcTarget.DMG, withMetasprites = false)
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("<gbdk/metasprites.h>"),
            "Expected NO '<gbdk/metasprites.h>' in main.c when no metasprites defined",
        )
    }

    // =========================================================================
    // Test 5: Metasprites present AND GBC target → both cgb_compatibility() and
    // <gbdk/metasprites.h> are emitted
    // =========================================================================

    @Test
    fun `GBC target with metasprites emits both cgb_compatibility and metasprites include`() {
        val gameIR = buildGbcCompatGameIR(GbcTarget.GBC_COMPATIBLE, withMetasprites = true)
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBody(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        assertTrue(
            mainBody.contains("cgb_compatibility()"),
            "Expected 'cgb_compatibility()' in main() for GBC_COMPATIBLE+metasprites",
        )
        assertTrue(
            mainC.contains("<gbdk/metasprites.h>"),
            "Expected '<gbdk/metasprites.h>' in main.c for GBC_COMPATIBLE+metasprites",
        )
    }
}
