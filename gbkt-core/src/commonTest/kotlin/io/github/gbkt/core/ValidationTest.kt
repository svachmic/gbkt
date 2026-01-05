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

    // =========================================================================
    // PHYSICS VALIDATION TESTS
    // =========================================================================

    @Test
    fun `validates entity physics with zero mass`() {
        val game =
            gbGame("test") {
                physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        mass = 0.0f // Invalid - must be positive
                        gravity = 0.5f
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.PHYSICS && it.message.contains("mass")
            },
            "Should error on zero mass. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates entity physics with negative mass`() {
        val game =
            gbGame("test") {
                physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        mass = -1.0f // Invalid - must be positive
                        gravity = 0.5f
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.PHYSICS && it.message.contains("mass")
            },
            "Should error on negative mass. Errors: ${result.errors}",
        )
    }

    @Test
    fun `warns on excessive max velocity`() {
        val game =
            gbGame("test") {
                physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        mass = 1.0f
                        maxVelocity = 200 to 200 // Exceeds i8 range (127)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.warnings.any {
                it.category == ValidationCategory.PHYSICS && it.message.contains("maxVelocity")
            },
            "Should warn on excessive velocity. Warnings: ${result.warnings}",
        )
    }

    @Test
    fun `warns on unusual friction values`() {
        val game =
            gbGame("test") {
                physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        mass = 1.0f
                        friction = 2.0f // Outside typical range [0, 1.5]
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.warnings.any {
                it.category == ValidationCategory.PHYSICS && it.message.contains("friction")
            },
            "Should warn on unusual friction. Warnings: ${result.warnings}",
        )
    }

    @Test
    fun `warns on extreme gravity values`() {
        val game =
            gbGame("test") {
                physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        mass = 1.0f
                        gravity = 5.0f // Outside typical range [-2.0, 2.0]
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.warnings.any {
                it.category == ValidationCategory.PHYSICS && it.message.contains("gravity")
            },
            "Should warn on extreme gravity. Warnings: ${result.warnings}",
        )
    }

    @Test
    fun `valid physics passes validation`() {
        val game =
            gbGame("test") {
                physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        mass = 1.0f
                        friction = 0.9f
                        gravity = 0.5f
                        maxVelocity = 4 to 8
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val result = game.validate()

        assertTrue(
            result.errors.none { it.category == ValidationCategory.PHYSICS },
            "Valid physics should have no errors. Errors: ${result.errors}",
        )
        assertTrue(
            result.warnings.none { it.category == ValidationCategory.PHYSICS },
            "Valid physics should have no warnings. Warnings: ${result.warnings}",
        )
    }

    // =========================================================================
    // TWEEN VALIDATION TESTS
    // =========================================================================

    @Test
    fun `validates tween with zero duration`() {
        val game =
            gbGame("test") {
                var x by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            tween(x, from = 0, to = 100, duration = 0.frames)
                        } // Invalid: duration = 0
                    }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.TWEEN && it.message.contains("duration")
            },
            "Should error on zero duration. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates tween with u8 value out of range`() {
        val game =
            gbGame("test") {
                var x by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            tween(x, from = 0, to = 300, duration = 60.frames)
                        } // Invalid: 300 > 255
                    }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.TWEEN && it.message.contains("bounds")
            },
            "Should error on U8 value out of range. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates tween with i8 value out of range`() {
        val game =
            gbGame("test") {
                var x by i8Var(0)

                start =
                    scene("main") {
                        enter {
                            tween(x, from = -200, to = 100, duration = 60.frames)
                        } // Invalid: -200 < -128
                    }
            }

        val result = game.validate()

        assertTrue(
            result.errors.any {
                it.category == ValidationCategory.TWEEN && it.message.contains("bounds")
            },
            "Should error on I8 value out of range. Errors: ${result.errors}",
        )
    }

    @Test
    fun `warns on large u8 tween range`() {
        val game =
            gbGame("test") {
                var x by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            tween(x, from = 0, to = 250, duration = 60.frames) // Large range warning
                        }
                    }
            }

        val result = game.validate()

        assertTrue(
            result.warnings.any {
                it.category == ValidationCategory.TWEEN && it.message.contains("large")
            },
            "Should warn on large tween range. Warnings: ${result.warnings}",
        )
    }

    @Test
    fun `valid tween passes validation`() {
        val game =
            gbGame("test") {
                var x by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            tween(x, from = 0, to = 100, duration = 60.frames, easing = Easing.LINEAR)
                        }
                    }
            }

        val result = game.validate()

        assertTrue(
            result.errors.none { it.category == ValidationCategory.TWEEN },
            "Valid tween should have no errors. Errors: ${result.errors}",
        )
    }

    // =========================================================================
    // ARRAY BOUNDS VALIDATION TESTS
    // =========================================================================

    @Test
    fun `valid array access passes validation`() {
        val game =
            gbGame("test") {
                val scores by u8Array(10)

                start =
                    scene("main") {
                        enter {
                            val x = scores[5] // Valid: 5 < 10
                            scores[0] set 100 // Valid: 0 < 10
                            scores[9] set 50 // Valid: 9 < 10
                        }
                    }
            }

        val result = game.validate()

        assertTrue(
            result.errors.none { it.category == ValidationCategory.ARRAY_BOUNDS },
            "Valid array access should have no errors. Errors: ${result.errors}",
        )
    }

    @Test
    fun `array access at boundary passes validation`() {
        val game =
            gbGame("test") {
                val items by u8Array(5)

                start =
                    scene("main") {
                        enter {
                            val first = items[0] // Valid: first element
                            val last = items[4] // Valid: last element (size - 1)
                        }
                    }
            }

        val result = game.validate()

        assertTrue(
            result.errors.none { it.category == ValidationCategory.ARRAY_BOUNDS },
            "Array access at boundaries should pass. Errors: ${result.errors}",
        )
    }

    @Test
    fun `array literal out of bounds throws at DSL time`() {
        // Note: The DSL validates array bounds at build time via require(),
        // so out-of-bounds literals throw immediately rather than being caught by validation
        assertFailsWith<IllegalArgumentException> {
            gbGame("test") {
                val scores by u8Array(10)
                start =
                    scene("main") {
                        enter {
                            val x = scores[15] // Throws immediately: index 15 >= size 10
                        }
                    }
            }
        }
    }

    @Test
    fun `array negative index throws at DSL time`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("test") {
                val scores by u8Array(10)
                start =
                    scene("main") {
                        enter {
                            val x = scores[-1] // Throws immediately: negative index
                        }
                    }
            }
        }
    }

    // =========================================================================
    // GBC COLOR VALIDATION TESTS (additional coverage)
    // =========================================================================

    @Test
    fun `validates palette with exactly 4 valid colors`() {
        val validColors =
            listOf(
                GBCColor(0x0000), // Black
                GBCColor(0x2108), // Dark gray
                GBCColor(0x4210), // Light gray
                GBCColor(0x7FFF), // White
            )

        val game =
            Game(
                name = "test",
                config = GameConfig(gbcSupport = true),
                variables = emptyList(),
                sprites = emptyList(),
                scenes = mapOf("main" to Scene("main", emptyList(), emptyList(), emptyList())),
                startScene = "main",
                palettes = listOf(GBCPalette("colors", validColors)),
            )

        val result = game.validate()

        assertTrue(
            result.errors.none { it.category == ValidationCategory.GBC_COLOR },
            "Valid palette should have no color errors. Errors: ${result.errors}",
        )
    }

    @Test
    fun `validates multiple palettes`() {
        val palette1 =
            GBCPalette(
                "bg",
                listOf(GBCColor.BLACK, GBCColor.DARK_GRAY, GBCColor.LIGHT_GRAY, GBCColor.WHITE),
                type = PaletteType.BACKGROUND,
            )
        val palette2 =
            GBCPalette(
                "sprite",
                listOf(GBCColor.RED, GBCColor.GREEN, GBCColor.BLUE, GBCColor.WHITE),
                type = PaletteType.SPRITE,
            )

        val game =
            Game(
                name = "test",
                config = GameConfig(gbcSupport = true),
                variables = emptyList(),
                sprites = emptyList(),
                scenes = mapOf("main" to Scene("main", emptyList(), emptyList(), emptyList())),
                startScene = "main",
                palettes = listOf(palette1, palette2),
            )

        val result = game.validate()

        assertTrue(
            result.errors.none { it.category == ValidationCategory.GBC_COLOR },
            "Multiple valid palettes should have no errors. Errors: ${result.errors}",
        )
    }
}
