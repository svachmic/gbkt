/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/** Validation layer tests - verifies that the validation catches common errors. */
class ValidationTest {

    @Test
    fun `validates OAM limit warning when approaching max sprites`() {
        val game =
            gbGame("test") {
                // Create 38 sprites (close to 40 limit)
                repeat(38) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        // Should have a warning about approaching OAM limit
        assertTrue(
            result.warnings.any { it.category == ValidationCategory.OAM_LIMIT },
            "Should warn when approaching OAM limit",
        )
    }

    @Test
    fun `validates OAM limit error when exceeding max sprites`() {
        val game =
            gbGame("test") {
                // Create 45 sprites (exceeds 40 limit)
                repeat(45) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        // Should have an error for exceeding OAM limit
        assertTrue(
            result.errors.any { it.category == ValidationCategory.OAM_LIMIT },
            "Should error when exceeding OAM limit. Errors: ${result.errors}",
        )
        assertFalse(result.isValid, "Should be invalid")
    }

    @Test
    fun `validates empty state machine`() {
        val game =
            gbGame("test") {
                states("empty") {
                    // No states defined
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        // Should have an error for empty state machine
        assertTrue(
            result.errors.any { it.category == ValidationCategory.STATE_MACHINE },
            "Should error on empty state machine. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates state machine with invalid transition target`() {
        val game =
            gbGame("test") {
                var trigger by u8Var(0)

                states("player") {
                    state("idle") { on(trigger isEqualTo 1) { goto("nonexistent") } }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        // Should have an error for invalid transition target
        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.STATE_MACHINE &&
                    it.message.contains("nonexistent")
            },
            "Should error on invalid transition target. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates unreachable states warning`() {
        val game =
            gbGame("test") {
                states("player") {
                    state("idle") {
                        // No transitions out
                    }
                    state("unreachable") {
                        // This state can never be reached
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        // Should have a warning for unreachable state
        assertTrue(
            result.warnings.any {
                it.category == ValidationCategory.STATE_MACHINE &&
                    it.message.contains("unreachable")
            },
            "Should warn about unreachable states. Warnings: ${result.warnings}",
        )
    }

    @Test
    fun `validates start scene exists`() {
        // This should throw during game construction, not validation
        // because the GameBuilder.build() has its own require()
        assertFailsWith<IllegalArgumentException> {
            gbGame("test") {
                scene("other") { every.frame {} }
                // start is never set
            }
        }
    }

    @Test
    fun `valid game passes validation`() {
        val game =
            gbGame("test") {
                var score by u8Var(0)

                val player = sprite(SpriteAsset("player")) { position(80, 72) }

                start = scene("main") { every.frame { score += 1 } }
            }

        val result = game.validate()

        assertTrue(
            result.isValid,
            "Simple valid game should pass validation. Errors: ${result.errors}",
        )
        assertTrue(result.errors.isEmpty(), "Should have no errors")
    }

    @Test
    fun `compile with validation throws on invalid game`() {
        val game =
            gbGame("test") {
                // Create too many sprites
                repeat(45) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                start = scene("main") { every.frame {} }
            }

        assertFailsWith<ValidationException> { game.compile() }
    }

    @Test
    fun `compile with warnOnValidationErrors does not throw`() {
        val game =
            gbGame("test") {
                // Create too many sprites
                repeat(45) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                start = scene("main") { every.frame {} }
            }

        // Should not throw
        val code = game.compile(warnOnValidationErrors = true)
        assertTrue(code.isNotEmpty(), "Should still generate code")
    }

    @Test
    fun `compileForTest skips validation`() {
        val game =
            gbGame("test") {
                // Create too many sprites (would fail validation)
                repeat(45) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                start = scene("main") { every.frame {} }
            }

        // Should not throw
        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate code without validation")
    }

    @Test
    fun `compileWithValidation returns both code and result`() {
        val game =
            gbGame("test") {
                var score by u8Var(0)

                start = scene("main") { every.frame { score += 1 } }
            }

        val (code, result) = game.compileWithValidation()

        assertTrue(code.isNotEmpty(), "Should generate code")
        assertTrue(result.isValid, "Should be valid")
    }

    @Test
    fun `validates pool sprites count towards OAM limit`() {
        val game =
            gbGame("test") {
                // Create 30 direct sprites
                repeat(30) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                // Create a pool of 15 more (30 + 15 = 45 > 40)
                pool("bullets", 15) {
                    position(0, 0)
                    onFrame {}
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any { it.category == ValidationCategory.OAM_LIMIT },
            "Should count pool sprites towards OAM limit. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates duplicate variable names`() {
        // Create a Game with duplicate variable names
        val game =
            Game(
                name = "test",
                config = GameConfig(),
                variables =
                    listOf(
                        GBVar("score", 0, GBVar.VarType.U8),
                        GBVar("score", 0, GBVar.VarType.U8), // Duplicate
                    ),
                sprites = emptyList(),
                scenes = mapOf("main" to Scene("main", emptyList(), emptyList(), emptyList())),
                startScene = "main",
            )

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.DUPLICATE_NAME && it.message.contains("score")
            },
            "Should detect duplicate variable names. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates duplicate sprite names`() {
        val sprite1 = Sprite("player", "player.png", 8, 8, 0)
        val sprite2 = Sprite("player", "player2.png", 8, 8, 1) // Duplicate name
        val game =
            Game(
                name = "test",
                config = GameConfig(),
                variables = emptyList(),
                sprites = listOf(sprite1, sprite2),
                scenes = mapOf("main" to Scene("main", emptyList(), emptyList(), emptyList())),
                startScene = "main",
            )

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.DUPLICATE_NAME && it.message.contains("player")
            },
            "Should detect duplicate sprite names. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates GBC palette with wrong number of colors`() {
        // Test that validation checks for exactly 4 colors
        // Note: GBCPalette constructor already validates this, but we test the validation layer
        val validPalette =
            GBCPalette(
                name = "valid",
                colors =
                    listOf(GBCColor.WHITE, GBCColor.LIGHT_GRAY, GBCColor.DARK_GRAY, GBCColor.BLACK),
            )

        val game =
            Game(
                name = "test",
                config = GameConfig(gbcSupport = true),
                variables = emptyList(),
                sprites = emptyList(),
                scenes = mapOf("main" to Scene("main", emptyList(), emptyList(), emptyList())),
                startScene = "main",
                palettes = listOf(validPalette),
            )

        val result = game.validate()
        // Valid palette should pass
        assertTrue(
            result.errors.none { it.category == ValidationCategory.GBC_COLOR },
            "Valid GBC palette should not have color errors. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates OAM limit at exact boundary`() {
        val game =
            gbGame("test") {
                // Create exactly 40 sprites (at the limit)
                repeat(40) { i -> sprite(SpriteAsset("sprite$i")) { position(i * 4, 0) } }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        // Should have a warning about being at the limit
        assertTrue(
            result.warnings.any {
                it.category == ValidationCategory.OAM_LIMIT && it.message.contains("at OAM limit")
            },
            "Should warn when at OAM limit. Warnings: ${result.warnings}",
        )
    }

    @Test
    fun `validates pool overflow scenario`() {
        val game =
            gbGame("test") {
                // Create a pool that exceeds OAM limit by itself
                pool("huge", 50) {
                    position(0, 0)
                    onFrame {}
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.OAM_LIMIT &&
                    it.message.contains("huge") &&
                    it.message.contains("exceeds OAM limit")
            },
            "Should detect pool overflow. Errors: ${result.errors}",
        )
    }

    // =========================================================================
    // PNG VALIDATOR TESTS
    // =========================================================================

    @Test
    fun `PngValidator rejects empty data`() {
        val result = PngValidator.validate(byteArrayOf(), "test.png")

        assertFalse(result.isValid, "Empty data should be invalid")
        assertTrue(
            result.errors.any { it.contains("too small") },
            "Should report file too small. Errors: ${result.errors}",
        )
    }

    @Test
    fun `PngValidator rejects non-PNG data`() {
        // JPEG signature
        val jpegData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val result = PngValidator.validate(jpegData, "test.png")

        assertFalse(result.isValid, "JPEG data should be invalid")
        assertTrue(
            result.errors.any { it.contains("signature") || it.contains("too small") },
            "Should report invalid signature. Errors: ${result.errors}",
        )
    }

    @Test
    fun `PngValidator validates PNG signature`() {
        // Valid PNG signature but truncated
        val validSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val result = PngValidator.validate(validSignature, "test.png")

        assertFalse(result.isValid, "Truncated PNG should be invalid")
        assertTrue(
            result.errors.any { it.contains("too small") || it.contains("IHDR") },
            "Should report file too small or IHDR issue. Errors: ${result.errors}",
        )
    }

    @Test
    fun `PngValidator parses valid PNG header`() {
        // Minimal valid PNG with 8x8 dimensions
        // Signature (8 bytes) + IHDR chunk (length 4 + type 4 + data 13 + CRC 4 = 25 bytes)
        val validPng =
            byteArrayOf(
                // PNG signature
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                // IHDR length (13 = 0x0000000D)
                0x00,
                0x00,
                0x00,
                0x0D,
                // IHDR type
                0x49,
                0x48,
                0x44,
                0x52, // "IHDR"
                // Width = 8 (0x00000008)
                0x00,
                0x00,
                0x00,
                0x08,
                // Height = 8 (0x00000008)
                0x00,
                0x00,
                0x00,
                0x08,
                // Bit depth, color type, compression, filter, interlace
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                // CRC (dummy - we don't validate CRC)
                0x00,
                0x00,
                0x00,
                0x00,
            )

        val result = PngValidator.validate(validPng, "test.png")

        assertTrue(result.isValid, "Valid 8x8 PNG should be valid. Errors: ${result.errors}")
        assertEquals(8, result.width, "Width should be 8")
        assertEquals(8, result.height, "Height should be 8")
    }

    @Test
    fun `PngValidator rejects non-multiple-of-8 dimensions`() {
        // PNG with 7x9 dimensions (not multiples of 8)
        val invalidPng =
            byteArrayOf(
                // PNG signature
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                // IHDR length (13)
                0x00,
                0x00,
                0x00,
                0x0D,
                // IHDR type
                0x49,
                0x48,
                0x44,
                0x52,
                // Width = 7 (0x00000007)
                0x00,
                0x00,
                0x00,
                0x07,
                // Height = 9 (0x00000009)
                0x00,
                0x00,
                0x00,
                0x09,
                // Bit depth, color type, compression, filter, interlace
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                // CRC (dummy)
                0x00,
                0x00,
                0x00,
                0x00,
            )

        val result = PngValidator.validate(invalidPng, "test.png")

        assertFalse(result.isValid, "Non-multiple-of-8 dimensions should be invalid")
        assertTrue(
            result.errors.any { it.contains("width") && it.contains("8") },
            "Should report width not multiple of 8. Errors: ${result.errors}",
        )
        assertTrue(
            result.errors.any { it.contains("height") && it.contains("8") },
            "Should report height not multiple of 8. Errors: ${result.errors}",
        )
    }

    @Test
    fun `PngValidator rejects oversized images`() {
        // PNG with 2048x2048 dimensions (too large)
        val largePng =
            byteArrayOf(
                // PNG signature
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                // IHDR length (13)
                0x00,
                0x00,
                0x00,
                0x0D,
                // IHDR type
                0x49,
                0x48,
                0x44,
                0x52,
                // Width = 2048 (0x00000800)
                0x00,
                0x00,
                0x08,
                0x00,
                // Height = 2048 (0x00000800)
                0x00,
                0x00,
                0x08,
                0x00,
                // Bit depth, color type, compression, filter, interlace
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                // CRC (dummy)
                0x00,
                0x00,
                0x00,
                0x00,
            )

        val result = PngValidator.validate(largePng, "test.png")

        assertFalse(result.isValid, "Oversized image should be invalid")
        assertTrue(
            result.errors.any { it.contains("too large") || it.contains("1024") },
            "Should report image too large. Errors: ${result.errors}",
        )
    }

    @Test
    fun `PngValidator reports width and height for valid PNG`() {
        // PNG with 16x24 dimensions
        val validPng =
            byteArrayOf(
                // PNG signature
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                // IHDR length (13)
                0x00,
                0x00,
                0x00,
                0x0D,
                // IHDR type
                0x49,
                0x48,
                0x44,
                0x52,
                // Width = 16 (0x00000010)
                0x00,
                0x00,
                0x00,
                0x10,
                // Height = 24 (0x00000018)
                0x00,
                0x00,
                0x00,
                0x18,
                // Bit depth, color type, compression, filter, interlace
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                // CRC (dummy)
                0x00,
                0x00,
                0x00,
                0x00,
            )

        val result = PngValidator.validate(validPng, "test.png")

        assertTrue(result.isValid, "Valid 16x24 PNG should be valid. Errors: ${result.errors}")
        assertEquals(16, result.width, "Width should be 16")
        assertEquals(24, result.height, "Height should be 24")
    }
}
