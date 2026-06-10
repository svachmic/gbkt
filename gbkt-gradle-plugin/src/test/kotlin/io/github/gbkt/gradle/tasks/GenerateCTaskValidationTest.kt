/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

// =============================================================================
// Plan 12.5-06 Task 1 — D-04b null-check validation gate for 5 new MetaspriteIR fields
//
// Locks the codegen-boundary GradleException gate extended in
// GenerateCTask.validateMetaspriteSpritePaths() (Task 1 GREEN step).
// The gate checks spriteMode, pivotX, pivotY, frameWidth, frameHeight —
// all added in Plan 12.5-03 as nullable fields on MetaspriteIR.
//
// Contract (per D-04b):
//   - null spriteMode  → GradleException with "missing mode()" message
//   - null pivotX or pivotY  → GradleException with "missing pivot()" message
//   - null frameWidth or frameHeight → GradleException with "missing frameSize()" message
//   - all 5 fields non-null → passes silently
//   - spritePath null still throws D-01b message (regression guard — Plan 12.4-05 contract)
//
// Test approach: call internal fun validateMetaspriteSpritePaths(gameIR: Any) directly
// with real GameIR/MetaspriteIR instances on the test classpath (same approach as
// GenerateCSpritePathValidationTest.kt for Plan 12.4-05).
//
// RED: all 5 new sub-tests fail until validateMetaspriteSpritePaths() is extended
// with the 3 new check blocks (GREEN step).
// =============================================================================

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SpriteMode
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

class GenerateCTaskValidationTest {

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    /** A minimal MetaspriteFrame with one tile. */
    private fun minimalFrame() = MetaspriteFrame(listOf(MetaspriteTile(0, 0, 0)))

    /**
     * Build a GameIR containing one MetaspriteIR with all 5 Phase 12.5 D-04b fields populated, then
     * override specific fields via the named parameters.
     *
     * By default, all required fields (spritePath + the 5 new ones) are non-null so that a sub-test
     * can blank out exactly the one field under test.
     */
    private fun gameIRWith(
        id: String = "player",
        spritePath: String? = "sprites/player.png",
        spriteMode: SpriteMode? = SpriteMode.SPR8x16,
        pivotX: Int? = 12,
        pivotY: Int? = 6,
        frameWidth: Int? = 24,
        frameHeight: Int? = 32,
    ): GameIR =
        GameIR(
            name = "TestGame",
            metasprites =
                listOf(
                    MetaspriteIR(
                        id = id,
                        frames = listOf(minimalFrame()),
                        spritePath = spritePath,
                        spriteMode = spriteMode,
                        pivotX = pivotX,
                        pivotY = pivotY,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                    )
                ),
        )

    // =========================================================================
    // Case 1: null spriteMode → GradleException with "missing mode()" message
    //
    // RED: fails until the getSpriteMode null-check block is added to
    // validateMetaspriteSpritePaths() (GREEN step).
    // =========================================================================

    @Test
    fun `null spriteMode throws GradleException with missing mode message`() {
        val gameIR = gameIRWith(spriteMode = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the metasprite id 'player', got: ${ex.message}",
        )
        assertTrue(
            "missing mode()" in ex.message!!,
            "GradleException message must contain 'missing mode()' to guide the user, " +
                "got: ${ex.message}",
        )
        assertTrue(
            "D-04b" in ex.message!!,
            "GradleException message must reference 'D-04b', got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 2: null pivotX → GradleException with "missing pivot()" message
    //
    // RED: fails until the getPivotX null-check block is added.
    // =========================================================================

    @Test
    fun `null pivotX throws GradleException with missing pivot message`() {
        val gameIR = gameIRWith(pivotX = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the metasprite id 'player', got: ${ex.message}",
        )
        assertTrue(
            "missing pivot()" in ex.message!!,
            "GradleException message must contain 'missing pivot()' to guide the user, " +
                "got: ${ex.message}",
        )
        assertTrue(
            "D-04b" in ex.message!!,
            "GradleException message must reference 'D-04b', got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 3: null pivotY → GradleException with "missing pivot()" message
    //
    // The pivot() DSL call sets both pivotX and pivotY — either being null means
    // pivot() was not called. Both map to the same "missing pivot()" message.
    // RED: fails until the getPivotY null-check block is added.
    // =========================================================================

    @Test
    fun `null pivotY throws GradleException with missing pivot message`() {
        val gameIR = gameIRWith(pivotY = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the metasprite id 'player', got: ${ex.message}",
        )
        assertTrue(
            "missing pivot()" in ex.message!!,
            "GradleException message must contain 'missing pivot()' to guide the user, " +
                "got: ${ex.message}",
        )
        assertTrue(
            "D-04b" in ex.message!!,
            "GradleException message must reference 'D-04b', got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 4: null frameWidth → GradleException with "missing frameSize()" message
    //
    // RED: fails until the getFrameWidth null-check block is added.
    // =========================================================================

    @Test
    fun `null frameWidth throws GradleException with missing frameSize message`() {
        val gameIR = gameIRWith(frameWidth = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the metasprite id 'player', got: ${ex.message}",
        )
        assertTrue(
            "missing frameSize()" in ex.message!!,
            "GradleException message must contain 'missing frameSize()' to guide the user, " +
                "got: ${ex.message}",
        )
        assertTrue(
            "D-04b" in ex.message!!,
            "GradleException message must reference 'D-04b', got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 5: null frameHeight → GradleException with "missing frameSize()" message
    //
    // frameSize(w, h) sets both — either being null means frameSize() was not called.
    // RED: fails until the getFrameHeight null-check block is added.
    // =========================================================================

    @Test
    fun `null frameHeight throws GradleException with missing frameSize message`() {
        val gameIR = gameIRWith(frameHeight = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the metasprite id 'player', got: ${ex.message}",
        )
        assertTrue(
            "missing frameSize()" in ex.message!!,
            "GradleException message must contain 'missing frameSize()' to guide the user, " +
                "got: ${ex.message}",
        )
        assertTrue(
            "D-04b" in ex.message!!,
            "GradleException message must reference 'D-04b', got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 6: all 5 new fields non-null → validation passes, no exception
    //
    // GREEN baseline: passes once all 3 new check blocks are added.
    // =========================================================================

    @Test
    fun `all 5 new fields populated passes validation without exception`() {
        val gameIR = gameIRWith() // defaults: all fields non-null

        // Must NOT throw any of the D-04b GradleExceptions
        try {
            validateMetaspriteSpritePaths(gameIR)
            // Expected: returns normally
        } catch (e: GradleException) {
            assertFalse(
                "D-04b" in (e.message ?: ""),
                "validateMetaspriteSpritePaths must NOT throw a D-04b GradleException " +
                    "when all 5 new fields are non-null, but threw: ${e.message}",
            )
        }
    }

    // =========================================================================
    // Case 7: null spritePath still throws D-01b message (regression guard)
    //
    // Plan 12.4-05 contract unchanged — the existing spritePath check must still fire.
    // Constructs a metasprite with all 5 new fields populated but spritePath=null.
    // =========================================================================

    @Test
    fun `null spritePath still throws D-01b message (Plan 12-4-05 regression guard)`() {
        val gameIR = gameIRWith(spritePath = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "player" in ex.message!!,
            "GradleException message must name the metasprite id 'player', got: ${ex.message}",
        )
        // The spritePath check fires BEFORE the new D-04b checks, so the D-01b message appears.
        assertTrue(
            "D-01b" in ex.message!!,
            "GradleException must still reference 'D-01b' for missing spritePath (Plan 12.4-05 " +
                "contract unchanged), got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 8: CR-01 regression — legacy IR without getSpriteMode still throws,
    //         NOT silently continues (skipping pivot/frameSize checks).
    //
    // The fix changes `continue` → `null` so that the `if (spriteMode == null)`
    // gate fires for legacy IR classes too.
    //
    // This test uses a real MetaspriteIR (spriteMode=null) as proxy for the
    // reflection path: both paths result in spriteMode=null reaching the gate.
    // The critical invariant is that pivot/frameSize are NOT silently skipped.
    // =========================================================================

    @Test
    fun `legacy IR spriteMode absent triggers mode error not silent skip (CR-01 regression guard)`() {
        // spriteMode=null simulates a legacy IR where getSpriteMode returns null.
        // Before CR-01 fix: `continue` would skip ALL remaining D-04b checks.
        // After CR-01 fix: spriteMode=null causes GradleException with "missing mode()".
        val gameIR = gameIRWith(spriteMode = null)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "missing mode()" in ex.message!!,
            "CR-01: spriteMode=null must throw 'missing mode()' GradleException, not silently " +
                "skip the remaining D-04b checks. Got: ${ex.message}",
        )
        assertTrue(
            "D-04b" in ex.message!!,
            "GradleException must reference 'D-04b', got: ${ex.message}",
        )
    }

    // =========================================================================
    // Case 9: WR-03 — mixed SPR8x8 + SPR8x16 in same game throws GradleException
    //
    // Hardware LCDC.SPRITE_SIZE is a global bit. A game that declares one
    // metasprite as SPR8x8 and another as SPR8x16 would render incorrectly at
    // runtime (the global SPRITES_8x16 macro causes SPR8x8 sprites to render
    // with doubled rows).
    //
    // RED: no mixed-mode check exists before this fix.
    // GREEN: validateMetaspriteSpritePaths() now collects distinct spriteModeNames
    //        after the per-metasprite loop and throws if > 1 distinct mode found.
    // =========================================================================

    /**
     * Build a GameIR with two metasprites using the given sprite modes. Both metasprites have all
     * other required fields set correctly so the per-metasprite checks pass and only the mixed-mode
     * guard fires.
     */
    private fun twoMetaspriteGameIR(mode1: SpriteMode, mode2: SpriteMode): GameIR =
        GameIR(
            name = "MixedModeGame",
            metasprites =
                listOf(
                    MetaspriteIR(
                        id = "player",
                        frames = listOf(minimalFrame()),
                        spritePath = "sprites/player.png",
                        spriteMode = mode1,
                        pivotX = 0,
                        pivotY = 0,
                        frameWidth = 24,
                        frameHeight = 32,
                    ),
                    MetaspriteIR(
                        id = "enemy",
                        frames = listOf(minimalFrame()),
                        spritePath = "sprites/enemy.png",
                        spriteMode = mode2,
                        pivotX = 0,
                        pivotY = 0,
                        frameWidth = 8,
                        frameHeight = 8,
                    ),
                ),
        )

    @Test
    fun `mixed SPR8x8 and SPR8x16 throws GradleException (WR-03)`() {
        val gameIR = twoMetaspriteGameIR(SpriteMode.SPR8x16, SpriteMode.SPR8x8)

        val ex = assertFailsWith<GradleException> { validateMetaspriteSpritePaths(gameIR) }

        assertTrue(
            "WR-03" in ex.message!!,
            "GradleException message must reference 'WR-03', got: ${ex.message}",
        )
        assertTrue(
            "SPR8x8" in ex.message!! && "SPR8x16" in ex.message!!,
            "GradleException message must name both modes; got: ${ex.message}",
        )
        assertTrue(
            "LCDC.SPRITE_SIZE" in ex.message!! || "global" in ex.message!!,
            "GradleException message must explain that SPRITE_SIZE is global; got: ${ex.message}",
        )
    }

    @Test
    fun `two metasprites with same SPR8x16 mode passes validation (WR-03 no false positive)`() {
        val gameIR = twoMetaspriteGameIR(SpriteMode.SPR8x16, SpriteMode.SPR8x16)

        // Must NOT throw — consistent SPR8x16 game is valid
        try {
            validateMetaspriteSpritePaths(gameIR)
        } catch (e: GradleException) {
            assertFalse(
                "WR-03" in (e.message ?: ""),
                "WR-03: must NOT throw for two SPR8x16 metasprites; threw: ${e.message}",
            )
        }
    }

    @Test
    fun `two metasprites with same SPR8x8 mode passes validation (WR-03 no false positive)`() {
        val gameIR = twoMetaspriteGameIR(SpriteMode.SPR8x8, SpriteMode.SPR8x8)

        // Must NOT throw — consistent SPR8x8 game is valid
        try {
            validateMetaspriteSpritePaths(gameIR)
        } catch (e: GradleException) {
            assertFalse(
                "WR-03" in (e.message ?: ""),
                "WR-03: must NOT throw for two SPR8x8 metasprites; threw: ${e.message}",
            )
        }
    }
}
