/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// Plan 12.4-01 Task 3: MetaspriteSpriteDslTest — DSL-level propagation test for
// MetaspriteBuilder.sprite(AssetRef) → MetaspriteIR.spritePath (REQ-3 acceptance).
//
// Per user feedback `feedback_no_magic_strings.md`: the binder takes AssetRef
// (not a String) — only asset.path flows into the IR; AssetType is discarded
// (the resolver cares only about the path string, not the type enum).
//
// Both tests were RED before Tasks 1 + 2 landed (field + binder did not exist);
// they are GREEN once both are in place.
// =============================================================================

class MetaspriteSpriteDslTest {

    @Test
    fun `sprite_binder_captures_asset_path`() {
        // Test 1: sprite(asset("sprites/foo.png")) must flow the path string
        // through MetaspriteBuilder.spriteAssetPath → MetaspriteIR.spritePath.
        // D-08 update (Plan 13.3-06): asset-driven path must NOT include frame{} blocks —
        // that combination is now a contract violation. Use sprite() alone.
        val g =
            game("Test") {
                val sprite by metasprite {
                    sprite(asset("sprites/foo.png"))
                    // No frame{} — asset-driven path (D-08 exactly-one)
                }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        val metasprite =
            ir.metasprites.firstOrNull { it.id == "sprite" }
                ?: error("expected metasprite 'sprite' in IR")
        assertEquals(
            "sprites/foo.png",
            metasprite.spritePath,
            "MetaspriteBuilder.sprite(asset(...)) must capture asset.path into MetaspriteIR.spritePath",
        )
    }

    @Test
    fun `no_sprite_binder_leaves_path_null`() {
        // Test 2: metasprite without sprite() call must produce spritePath == null
        // (migration window D-01b — existing metasprites remain valid during Phase 12.4).
        val g =
            game("Test") {
                val nosprite by metasprite { frame { tile(0, 0, 0) } }
                val playScene = scene("play") { frame { moveMetasprite(nosprite) } }
                start = playScene
            }
        val ir = g.build()

        val metasprite =
            ir.metasprites.firstOrNull { it.id == "nosprite" }
                ?: error("expected metasprite 'nosprite' in IR")
        assertNull(
            metasprite.spritePath,
            "Metasprite without sprite() call must have spritePath == null (D-01b migration window)",
        )
    }
}
