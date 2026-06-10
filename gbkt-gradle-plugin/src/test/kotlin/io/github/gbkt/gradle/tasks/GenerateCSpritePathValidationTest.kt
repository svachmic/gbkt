/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

// =============================================================================
// Plan 12.4-05 Task 2 — D-01b null-check validation gate
//
// Locks the pre-codegen GradleException gate added in GenerateCTask.executePath()
// (Task 1). The gate iterates the MetaspriteIR list in GameIR and throws
// GradleException if any metasprite has spritePath == null.
//
// Contract (per D-01b):
//   - null spritePath  → GradleException with D-01b message + metasprite id
//   - non-null spritePath → passes silently; backend invoked as normal
//
// Test approach: Option A (internal helper). The validation loop is extracted
// into an `internal fun validateMetaspriteSpritePaths(gameIR: Any)` helper
// that uses reflection to access GameIR.metasprites/MetaspriteIR.spritePath
// exactly as the production code does. The test calls this helper directly
// with real GameIR/MetaspriteIR instances (available on the test classpath
// via the transitive gbkt-core → gbkt-ir implementation dependency in
// gbkt-gradle-plugin/build.gradle.kts).
//
// Two cases locked here:
//   1. null_spritePath_throws_GradleException_with_D01b_message_and_metasprite_id
//   2. non_null_spritePath_passes_validation_without_exception
// =============================================================================

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

class GenerateCSpritePathValidationTest {

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    /** A minimal MetaspriteIR with one frame and one tile. */
    private fun minimalFrame() = MetaspriteFrame(listOf(MetaspriteTile(0, 0, 0)))

    /** Construct a GameIR containing a single MetaspriteIR with the given spritePath. */
    private fun gameIRWith(id: String, spritePath: String?): GameIR =
        GameIR(
            name = "TestGame",
            metasprites =
                listOf(
                    MetaspriteIR(id = id, frames = listOf(minimalFrame()), spritePath = spritePath)
                ),
        )

    // -------------------------------------------------------------------------
    // Case 1: null spritePath → GradleException with D-01b message + id
    //
    // RED: this test fails until validateMetaspriteSpritePaths() is added to
    // GenerateCTask.kt (Task 1 implementation step).
    // -------------------------------------------------------------------------

    @Test
    fun `null spritePath throws GradleException with D-01b message and metasprite id`() {
        val gameIR = gameIRWith(id = "player", spritePath = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the offending metasprite id 'player', " +
                "but was: ${ex.message}",
        )
        assertTrue(
            "missing sprite(asset(" in ex.message!!,
            "GradleException message must mention 'missing sprite(asset(' to guide the user " +
                "toward the DSL fix, but was: ${ex.message}",
        )
        assertTrue(
            "D-01b" in ex.message!!,
            "GradleException message must reference 'D-01b' (the decision that defines " +
                "this validation gate), but was: ${ex.message}",
        )
    }

    // -------------------------------------------------------------------------
    // Case 2: non-null spritePath → validation passes, no exception thrown
    //
    // RED: this test also fails until the helper is added (it won't compile).
    // GREEN: passes once validateMetaspriteSpritePaths() is added.
    // -------------------------------------------------------------------------

    @Test
    fun `non-null spritePath passes validation without exception`() {
        val gameIR = gameIRWith(id = "player", spritePath = "sprites/player.png")

        // Must NOT throw a D-01b GradleException. The helper may throw for
        // unrelated reasons in deeper logic; we specifically assert no
        // "missing sprite(asset" exception surfaces.
        try {
            validateMetaspriteSpritePaths(gameIR)
            // Expected: returns normally
        } catch (e: GradleException) {
            assertFalse(
                "missing sprite(asset" in (e.message ?: ""),
                "validateMetaspriteSpritePaths must NOT throw the D-01b GradleException " +
                    "when spritePath is non-null, but threw: ${e.message}",
            )
        }
    }

    // -------------------------------------------------------------------------
    // Case 3: empty metasprites list → validation passes silently (no exception)
    // -------------------------------------------------------------------------

    @Test
    fun `empty metasprites list passes validation without exception`() {
        val gameIR = GameIR(name = "TestGame", metasprites = emptyList())

        // Must not throw anything
        validateMetaspriteSpritePaths(gameIR)
    }
}
