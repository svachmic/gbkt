/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.3 Plan 02 Task 1: MetaspriteBuilderContractTest — D-08 exactly-one
//
// Encodes the "exactly-one of { sprite(asset), frame{} }" contract for
// MetaspriteBuilder.build(). These tests are RED until Plan 13.3-06 adds the
// guard to build().
//
// D-08 specification:
//   - BOTH sprite(asset(...)) AND frame{} → build() MUST throw (contract violation)
//   - NEITHER sprite(asset) NOR frame{} → build() MUST throw (contract violation)
//   - sprite(asset(...)) ONLY (no frame{}) → build() SUCCEEDS; IR has
//       spritePath != null, frames.isEmpty()
//   - frame{} ONLY (no sprite()) → build() SUCCEEDS; IR has
//       spritePath == null, frames.isNotEmpty()
//
// RED reason: build() currently enforces `frameBuilders.isNotEmpty()` (line 317)
// but has NO exactly-one guard. As a result:
//   - the "both-present" case SUCCEEDS build() (no guard) → assertFails fires
//   - the "asset-only success" case FAILS build() (no frames) → assertEquals fires
// Both make this test class RED before Plan 13.3-06.
// =============================================================================

class MetaspriteBuilderContractTest {

    // -------------------------------------------------------------------------
    // Case 1: asset-driven only (spritePath != null, frames empty) — SUCCESS
    //
    // D-08 success path: sprite(asset(...)) without any frame{} block produces
    // a valid MetaspriteIR with spritePath set and an empty frames list.
    //
    // RED now: build() throws "must have at least one frame" because the
    // frameBuilders list is empty (Plan 13.3-06 relaxes this guard when
    // spritePath != null so the asset-driven path bypasses the frame-count check).
    // -------------------------------------------------------------------------
    @Test
    fun `asset-driven metasprite with sprite only and no frames builds successfully`() {
        val builder = MetaspriteBuilder("elephant")
        builder.sprite(asset("sprites/elephant.png"))
        // No frame{} block — asset-driven path; png2asset provides the frames

        val ir = builder.build() // Must not throw after Plan 13.3-06

        assertEquals(
            "sprites/elephant.png",
            ir.spritePath,
            "D-08 success: asset-driven metasprite must have spritePath == 'sprites/elephant.png'",
        )
        assertTrue(
            ir.frames.isEmpty(),
            "D-08 success: asset-driven metasprite must have no DSL frames (frames.isEmpty())",
        )
    }

    // -------------------------------------------------------------------------
    // Case 2: BOTH sprite(asset) AND frame{} → build() must throw (D-08 violation)
    //
    // D-08 says: never both. The combination is a contract violation because
    // asset-driven and procedural frame descriptors are mutually exclusive paths
    // (Path A vs escape-hatch D-04). Using both creates ambiguity at codegen time.
    //
    // RED now: build() currently has NO guard for this case — both sprite() and
    // frame{} are accepted without error, so build() SUCCEEDS. assertFails will
    // therefore fail (no exception thrown). Plan 13.3-06 adds the guard.
    //
    // The exception message must contain the metasprite id and identify the
    // conflict (either "sprite" or "frame" keyword — exact wording is not pinned
    // beyond these two identifiers).
    // -------------------------------------------------------------------------
    @Test
    fun `metasprite with both sprite and frame throws D-08 contract violation`() {
        val builder = MetaspriteBuilder("elephant")
        builder.sprite(asset("sprites/elephant.png"))
        builder.frame { tile(0, 0, 0) }

        val ex =
            assertFails(
                "D-08: build() must throw when both sprite(asset) and frame{} are present"
            ) {
                builder.build()
            }

        assertTrue(
            ex.message?.contains("elephant") == true,
            "D-08 exception message must contain the metasprite id 'elephant', got: ${ex.message}",
        )
        assertTrue(
            ex.message?.contains("sprite", ignoreCase = true) == true ||
                ex.message?.contains("frame", ignoreCase = true) == true,
            "D-08 exception message must identify the conflict ('sprite' or 'frame'), got: ${ex.message}",
        )
    }

    // -------------------------------------------------------------------------
    // Case 3: NEITHER sprite nor frame → build() must throw (D-08 violation)
    //
    // An empty metasprite is not valid: every metasprite must resolve to either
    // an asset-driven array reference (Path A) or a set of procedural frame
    // descriptors (escape-hatch D-04). An empty builder has no path to codegen.
    //
    // This case was already partially enforced by the "must have at least one frame"
    // check; after Plan 13.3-06 the message changes to reflect the exactly-one
    // contract. The test is lenient on message content — it only requires the
    // metasprite id and one of "sprite" or "frame".
    // -------------------------------------------------------------------------
    @Test
    fun `metasprite with neither sprite nor frame throws D-08 contract violation`() {
        val builder = MetaspriteBuilder("hero")

        val ex =
            assertFails(
                "D-08: build() must throw when neither sprite(asset) nor frame{} is present"
            ) {
                builder.build()
            }

        assertTrue(
            ex.message?.contains("hero") == true,
            "D-08 exception message must contain the metasprite id 'hero', got: ${ex.message}",
        )
    }

    // -------------------------------------------------------------------------
    // Case 4: frame{} only (escape-hatch D-04) — SUCCESS
    //
    // The procedural escape-hatch path: no sprite(asset(...)) call, one or more
    // frame{ tile(...) } blocks. This is the pre-Phase-13.3 form and must continue
    // to work after Plan 13.3-06 lands (back-compat for non-migrated metasprites).
    //
    // GREEN now: build() succeeds (frameBuilders.isNotEmpty() passes, no exactly-one
    // guard blocks the frame-only path). This case is a regression guard.
    // -------------------------------------------------------------------------
    @Test
    fun `procedural metasprite with frame only and no sprite builds successfully`() {
        val builder = MetaspriteBuilder("bat")
        builder.frame {
            tile(0, 0, 0)
            tile(8, 0, 1)
        }
        // No sprite() call — escape-hatch path

        val ir = builder.build() // Must not throw

        assertNull(
            ir.spritePath,
            "D-04 escape-hatch: frame-only metasprite must have spritePath == null",
        )
        assertEquals(
            1,
            ir.frames.size,
            "D-04 escape-hatch: frame-only metasprite must have 1 frame",
        )
        assertEquals(
            2,
            ir.frames.first().tiles.size,
            "D-04 escape-hatch: frame must contain 2 tiles",
        )
    }
}
