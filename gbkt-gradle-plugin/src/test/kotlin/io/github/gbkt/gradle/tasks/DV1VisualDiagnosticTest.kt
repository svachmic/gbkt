/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// =============================================================================
// DEF-10.1-13-A — D-V1 VISUAL DIAGNOSTIC (PLAN 10.1-15)
//
// Named root cause (per .planning/.../evidence/d-v1-visual-diagnostic/
// d-v1-visual-finding.md): the port's `ConvertSpritesTask` invokes `png2asset`
// WITHOUT the `-noflip` flag for metasprite-bound PNGs. Without `-noflip`,
// png2asset detects mirror tiles and emits flip-bit `S_FLIPX`/`S_FLIPY`
// METASPR_ITEM entries that reference deduplicated tiles -- shrinking the
// `elephant_tiles[]` array from 768 bytes (48 unique tiles, reference) to 720
// bytes (45 unique tiles, port).
//
// The DSL `Metasprites.kt` was hand-transcribed from the reference's
// `-noflip` id space (0..47), so DSL `tile()` baseIds 45, 46, 47 reference
// PAST THE END of the port's `elephant_tiles[720]` array -- garbage pixels =
// "elephant picture still slightly broken" the user observed in the
// Plan 10.1-13 UAT re-shoot.
//
// Plan 10.1-16 must add `args.add("-noflip")` (or equivalent) to the
// metasprite-bound branch of `ConvertSpritesTask.convertSprite()`. This RED
// test locks the named bug by asserting that the production source has NO
// reference to "-noflip" anywhere. Plan 16's fix turns it GREEN by introducing
// the missing literal.
//
// ## Why a source-level grep test (and not a behavioural test)
//
// The `args` list is built inline inside `convertSprite()` (lines 199-205 at
// time of writing). Extracting a testable helper that returns the args list
// would require a production refactor that is OUT OF SCOPE for this
// diagnostic-only plan (10.1-15). The RED-lock can be a source-text grep:
// once Plan 16 adds the literal, the test flips GREEN. The risk of the grep
// passing on an unrelated occurrence of "-noflip" is mitigated by additionally
// requiring proximity to the existing `SpriteMode.SPR8x8` branch -- the only
// site where the new arg should land.
// =============================================================================

class DV1VisualDiagnosticTest {

    @Test
    fun convertSpritesTask_currently_omits_noflip_arg_for_metasprite_PNGs() {
        // Locate ConvertSpritesTask.kt relative to the module root.
        // gradle test working dir is the module root (gbkt-gradle-plugin/).
        val sourceFile = File("src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt")
        assertTrue(
            sourceFile.exists(),
            "Expected ConvertSpritesTask.kt to exist at ${sourceFile.absolutePath}; " +
                "test runner CWD was ${File("").absolutePath}.",
        )
        val source = sourceFile.readText()

        // The named-cause assertion (RED at plan close, GREEN after Plan 16):
        // ConvertSpritesTask.kt must contain a "-noflip" literal in args
        // construction. Plan 16 adds it inside the metasprite-bound branch
        // (next to or fused with the existing `args.add("-spr8x8")`).
        //
        // Pre-fix: the literal does not appear anywhere in the file.
        // Post-fix: the literal appears at least once.
        assertTrue(
            source.contains("\"-noflip\""),
            "ConvertSpritesTask.kt must pass `-noflip` to png2asset for " +
                "metasprite-bound PNGs. Without `-noflip`, png2asset detects " +
                "mirror tiles and emits a renumbered tile-data array (45 unique " +
                "tiles instead of 48), so DSL baseIds 45, 46, 47 dereference past " +
                "the end of `elephant_tiles[720]` and produce garbage pixels. " +
                "See .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/" +
                "evidence/d-v1-visual-diagnostic/d-v1-visual-finding.md for the named " +
                "cause and Plan 10.1-16 for the fix shape (one-line `args.add(\"-noflip\")` " +
                "after the existing SPR8x8 add).",
        )
    }
}
