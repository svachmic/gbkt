/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Plan 10.1-16 Task 3: MetaspriteBuilder.mirrorDedup() DSL method propagates
// to MetaspriteIR.mirrorDedup.
//
// Sibling to MetaspriteIRMirrorDedupFieldTest (in :gbkt-ir) which locks the
// underlying IR default + override. This test drives the full DSL surface:
//   val sprite by metasprite { mirrorDedup(); frame { tile(0, 0, 0) } }
// and asserts the resolved MetaspriteIR carries mirrorDedup = true.
//
// The default-false roundtrip test (metasprite WITHOUT mirrorDedup() opt-in)
// guards against an accidental "always-on" propagation that would silently
// disable -noflip for all metasprites -- which would re-open DEF-10.1-13-A.
// =============================================================================

class MetaspriteBuilderMirrorDedupTest {

    @Test
    fun `metasprite_without_mirrorDedup_opt_in_resolves_to_mirrorDedup_false`() {
        // Back-compat scenario: the Phase 10 Metasprites.kt does NOT call
        // mirrorDedup() and MUST resolve to mirrorDedup = false so Task 1's
        // unconditional `-noflip` behavior continues to apply (preserving the
        // DEF-10.1-13-A fix).
        val g =
            game("Test") {
                val sprite by metasprite { frame { tile(0, 0, 0) } }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        val ms = ir.metasprites.first { it.id == "sprite" }
        assertFalse(
            ms.mirrorDedup,
            "metasprite { } without mirrorDedup() opt-in MUST resolve to mirrorDedup = false " +
                "(default behavior keeps `-noflip` -> full unique-tile array). Regression " +
                "guard: if this fires true, the DSL silently disables -noflip and re-opens " +
                "DEF-10.1-13-A across all metasprites.",
        )
    }

    @Test
    fun `metasprite_with_mirrorDedup_opt_in_resolves_to_mirrorDedup_true`() {
        // Opt-in scenario: a from-scratch authored metasprite that wants to
        // take advantage of png2asset's mirror-pair tile deduplication to save
        // ROM. The DSL flag must round-trip through MetaspriteBuilder to
        // MetaspriteIR.mirrorDedup = true so Task 4's ConvertSpritesTask wiring
        // can OMIT `-noflip` for this metasprite's PNG.
        val g =
            game("Test") {
                val sprite by metasprite {
                    mirrorDedup()
                    frame { tile(0, 0, 0) }
                }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        val ms = ir.metasprites.first { it.id == "sprite" }
        assertTrue(
            ms.mirrorDedup,
            "metasprite { mirrorDedup(); ... } MUST resolve to MetaspriteIR.mirrorDedup = true. " +
                "Task 4's ConvertSpritesTask reads this flag at codegen time to gate the " +
                "`-noflip` arg -- when true, the arg is omitted, allowing png2asset's " +
                "mirror-pair tile dedup.",
        )
    }

    @Test
    fun `multiple_metasprites_carry_independent_mirrorDedup_flags`() {
        // CR-03-style stress preview: when two metasprites declare different
        // mirrorDedup values, each MetaspriteIR must carry its own captured
        // flag -- a flag set on one metasprite MUST NOT bleed into a sibling.
        val g =
            game("Test") {
                val deduped by metasprite {
                    mirrorDedup()
                    frame { tile(0, 0, 0) }
                }
                val canonical by metasprite {
                    // No mirrorDedup() opt-in -- stays false.
                    frame { tile(0, 0, 0) }
                }
                val playScene =
                    scene("play") {
                        frame {
                            moveMetasprite(deduped)
                            moveMetasprite(canonical)
                        }
                    }
                start = playScene
            }
        val ir = g.build()

        assertTrue(
            ir.metasprites.first { it.id == "deduped" }.mirrorDedup,
            "deduped metasprite (mirrorDedup() opted-in) must carry true",
        )
        assertFalse(
            ir.metasprites.first { it.id == "canonical" }.mirrorDedup,
            "canonical metasprite (no opt-in) must stay at default false -- " +
                "must not inherit the sibling's flag",
        )
    }
}
