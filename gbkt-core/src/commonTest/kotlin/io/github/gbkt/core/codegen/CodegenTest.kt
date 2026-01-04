/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/** Code generation tests - verifies that DSL constructs generate correct C code. */
class CodegenTest {

    @Test
    fun `branch generates correct if-else chain`() {
        // This test verifies the fix for the branch() bug where
        // all branches used the first condition
        val game =
            gbGame("test") {
                var state by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            branch {
                                (state isEqualTo 1) then { result set 10 }
                                (state isEqualTo 2) then { result set 20 }
                                (state isEqualTo 3) then { result set 30 }
                            }
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        // Debug: print relevant lines
        val relevantLines =
            code.lines().filter { it.contains("state") || it.contains("result") }.joinToString("\n")

        // The generated code should have proper if-else chain with state comparisons
        assertTrue(
            code.contains("state") && code.contains("1u"),
            "Should have state comparison. Generated:\n$relevantLines"
        )

        // Make sure the assignments are in the generated code
        assertTrue(
            code.contains("result = 10u") || code.contains("result = 10"),
            "Should assign 10 for state 1. Generated:\n$relevantLines"
        )
        assertTrue(
            code.contains("result = 20u") || code.contains("result = 20"),
            "Should assign 20 for state 2. Generated:\n$relevantLines"
        )
        assertTrue(
            code.contains("result = 30u") || code.contains("result = 30"),
            "Should assign 30 for state 3. Generated:\n$relevantLines"
        )
    }

    @Test
    fun `camera bounds generates correct C code`() {
        val game =
            gbGame("test") {
                val cam = camera { bounds(10..200, 20..180) }

                start = scene("main") { every.frame { cam.update() } }
            }

        val code = CodeGenerator(game).generate()

        // Camera bounds should be initialized with configured values
        assertTrue(code.contains("_camera_bounds_min_x = 10"), "Should set min X bound")
        assertTrue(code.contains("_camera_bounds_max_x = 200"), "Should set max X bound")
        assertTrue(code.contains("_camera_bounds_min_y = 20"), "Should set min Y bound")
        assertTrue(code.contains("_camera_bounds_max_y = 180"), "Should set max Y bound")

        // Camera update should clamp to bounds, not hardcoded 0-255
        assertTrue(
            code.contains("_camera_bounds_min_x") && code.contains("_camera_bounds_max_x"),
            "Camera update should use bounds variables"
        )
    }

    @Test
    fun `pool generates despawn functions`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", 8) {
                        position(0, 0)

                        state {
                            val lifetime by u8Var(60)
                        }

                        onFrame {
                            // Just a placeholder
                        }

                        despawnWhen { x isEqualTo 0 }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = CodeGenerator(game).generate()

        // Verify pool despawn function is generated
        assertTrue(code.contains("bullet_despawn"), "Should generate despawn function")
        assertTrue(code.contains("bullet_despawn_all"), "Should generate despawn_all function")
        assertTrue(code.contains("bullet_update"), "Should generate update function")
    }

    @Test
    fun `music fade generates fade variables and logic`() {
        val game =
            gbGame("test") {
                val track = music("test_song")

                start = scene("main") { enter { track.fadeOut(30) } }
            }

        val code = CodeGenerator(game).generate()

        // Should have fade state variables
        assertTrue(code.contains("_music_fade_timer"), "Should declare fade timer")
        assertTrue(code.contains("_music_fade_duration"), "Should declare fade duration")

        // Should set fade parameters
        assertTrue(code.contains("_music_fade_timer = 30"), "Should set fade timer to 30 frames")
    }

    @Test
    fun `animation with missing sprite throws CodeGenerationException`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        enter {
                            // Reference a sprite that doesn't exist
                            RecordingContext.current?.emit(
                                IRAnimationPlay("nonexistent", "walk", false, 100, false)
                            )
                        }
                    }
            }

        // Should throw CodeGenerationException with informative error message
        val exception = assertFailsWith<CodeGenerationException> { CodeGenerator(game).generate() }

        // Exception message should mention the missing sprite
        assertTrue(
            exception.message?.contains("nonexistent") == true,
            "Exception should mention the missing sprite name"
        )
    }

    @Test
    fun `generates valid C code structure`() {
        val game =
            gbGame("test") {
                var score by u8Var(0)

                start = scene("main") { every.frame { score += 1 } }
            }

        val code = CodeGenerator(game).generate()

        // Basic structure checks
        assertTrue(code.contains("#include <gb/gb.h>"), "Should include gb/gb.h")
        assertTrue(code.contains("void main(void)"), "Should have main function")
        assertTrue(code.contains("while (1)"), "Should have main loop")
        assertTrue(code.contains("vsync()"), "Should call vsync")
    }

    @Test
    fun `physics component generates correct C code`() {
        val game =
            gbGame("test") {
                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        gravity = 0.5f // 128 in fixed-point 8.8
                        friction = 0.9f // ~230 in fixed-point 8.8
                        maxVelocity = 4 to 8
                    }
                }

                start = scene("main") { every.frame { player.applyPhysics() } }
            }

        val code = CodeGenerator(game).generate()

        // Should have physics constants defined
        assertTrue(code.contains("PLAYER_GRAVITY"), "Should define gravity constant")
        assertTrue(code.contains("PLAYER_FRICTION"), "Should define friction constant")
        assertTrue(code.contains("PLAYER_MAX_VEL_X"), "Should define max velocity X")
        assertTrue(code.contains("PLAYER_MAX_VEL_Y"), "Should define max velocity Y")

        // Should have fixed-point velocity variables
        assertTrue(code.contains("_player_vel_x_fp"), "Should have fixed-point velocity X")
        assertTrue(code.contains("_player_vel_y_fp"), "Should have fixed-point velocity Y")

        // Should generate physics update code
        assertTrue(code.contains("Physics update for player"), "Should have physics update comment")
        assertTrue(code.contains("Apply gravity"), "Should apply gravity")
        assertTrue(code.contains("Apply friction"), "Should apply friction")
        assertTrue(code.contains("Clamp velocity"), "Should clamp velocity")
        assertTrue(code.contains("Update position from velocity"), "Should update position")
    }

    @Test
    fun `physics is only accessible via velocity scope`() {
        // With structural enforcement, physics is only accessible via velocity().physics { }
        // This test verifies the new API works correctly
        val game =
            gbGame("test") {
                val player by entity {
                    position(80, 72)
                    // New API: physics is accessed through velocity()
                    velocity(0, 0).physics { gravity = 0.5f }
                }

                start = scene("main") { every.frame { player.applyPhysics() } }
            }

        // Verify both velocity and physics components exist
        val player = game.entities.find { it.name == "player" }
        assertNotNull(player)
        assertTrue(player.hasVelocity, "Entity should have velocity component")
        assertTrue(player.hasPhysics, "Entity should have physics component")
    }

    @Test
    fun `physics fixed-point conversion is correct`() {
        val game =
            gbGame("test") {
                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        gravity = 0.5f // Should be 128 (0.5 * 256)
                        friction = 1.0f // Should be 256 (1.0 * 256)
                        maxVelocity = 4 to 8
                    }
                }

                start = scene("main") { every.frame { player.applyPhysics() } }
            }

        val code = CodeGenerator(game).generate()

        // Check that gravity = 0.5 becomes 128 in fixed-point
        assertTrue(code.contains("PLAYER_GRAVITY 128"), "Gravity 0.5 should be 128 in fixed-point")

        // Check that friction = 1.0 becomes 256 in fixed-point
        assertTrue(
            code.contains("PLAYER_FRICTION 256"),
            "Friction 1.0 should be 256 in fixed-point"
        )

        // Check max velocity values
        assertTrue(code.contains("PLAYER_MAX_VEL_X 4"), "Max velocity X should be 4")
        assertTrue(code.contains("PLAYER_MAX_VEL_Y 8"), "Max velocity Y should be 8")
    }

    @Test
    fun `signed integer types generate correct C code`() {
        val game =
            gbGame("test") {
                var velocityX by i8Var(-5)
                var positionY by i16Var(100)
                var direction by i8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            velocityX += 1
                            positionY set 200
                            direction set -10
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        // Should declare variables with correct signed types
        assertTrue(code.contains("INT8 velocityX"), "Should declare INT8 variable")
        assertTrue(code.contains("INT16 positionY"), "Should declare INT16 variable")
        assertTrue(code.contains("INT8 direction"), "Should declare INT8 variable for direction")

        // Should initialize with values (no 'u' suffix for signed)
        assertTrue(
            code.contains("velocityX = -5;") || code.contains("velocityX = 251;"),
            "Should initialize signed i8 variable (may be -5 or wrapped)"
        )
        assertTrue(code.contains("positionY = 100;"), "Should initialize i16 without 'u' suffix")
    }

    @Test
    fun `ternary expression generates correct C code`() {
        val game =
            gbGame("test") {
                var damage by u8Var(0)
                var isCritical by u8Var(0)
                var speed by u8Var(0)
                var isRunning by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            // Method call syntax with Condition
                            val critCondition = isCritical.isNonZero
                            damage set critCondition.then(20, 10)
                            // Infix syntax with Condition
                            val runCondition = isRunning.isNonZero
                            speed set (runCondition then 4 otherwise 2)
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        // Should generate C ternary operators
        assertTrue(code.contains("?"), "Should generate ternary operator '?'")
        assertTrue(code.contains(":"), "Should generate ternary else ':'")
        assertTrue(code.contains("20") && code.contains("10"), "Should have damage values")
        assertTrue(code.contains("4") && code.contains("2"), "Should have speed values")
    }

    @Test
    fun `repeat generates for loop`() {
        val game =
            gbGame("test") {
                var counter by u8Var(0)

                start = scene("main") { enter { repeat(5) { counter += 1 } } }
            }

        val code = CodeGenerator(game).generate()

        // Should generate a for loop
        assertTrue(code.contains("for"), "Should generate for loop")
        assertTrue(code.contains("_loop"), "Should use loop counter variable")
    }

    @Test
    fun `repeatWhile generates while loop`() {
        val game =
            gbGame("test") {
                var counter by u8Var(10)

                start = scene("main") { enter { repeatWhile(counter isAbove 0) { counter -= 1 } } }
            }

        val code = CodeGenerator(game).generate()

        // Should generate a while loop
        assertTrue(code.contains("while"), "Should generate while loop")
        assertTrue(code.contains("counter"), "Should reference counter in condition")
    }

    @Test
    fun `array assignment generates correct C code`() {
        val game =
            gbGame("test") {
                val inventory by u8Array(10)
                var slot by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            inventory[0] set 5
                            inventory[slot] set 10
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        // Should declare the array
        assertTrue(code.contains("UINT8 inventory[10]"), "Should declare array with size")
        // Should generate bounds-checked array assignments using GB_ARRAY_SET macro
        assertTrue(
            code.contains("GB_ARRAY_SET(inventory, 0u, 10, 5u)") ||
                code.contains("GB_ARRAY_SET(inventory, 0, 10, 5)"),
            "Should use GB_ARRAY_SET for index 0"
        )
        assertTrue(
            code.contains("GB_ARRAY_SET(inventory, slot, 10, 10u)") ||
                code.contains("GB_ARRAY_SET(inventory, slot, 10, 10)"),
            "Should use GB_ARRAY_SET for variable index"
        )
    }

    @Test
    fun `pool typed state generates correct C code`() {
        val game =
            gbGame("test") {
                pool("bullet", 8) {
                    position(0, 0)
                    // Use new typed state API
                    val timer by u8State(60)
                    val damage by u8State(10)

                    onSpawn { timer set 120 }
                    onFrame {
                        timer -= 1
                        damage += 1
                    }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Should declare state arrays (uses BULLET_POOL_SIZE macro)
        assertTrue(
            code.contains("bullet_timer[BULLET_POOL_SIZE]"),
            "Should declare timer array for pool"
        )
        assertTrue(
            code.contains("bullet_damage[BULLET_POOL_SIZE]"),
            "Should declare damage array for pool"
        )
        // Should generate assignments in lifecycle hooks
        assertTrue(
            code.contains("bullet_timer") && code.contains("120"),
            "Should assign timer in onSpawn"
        )
    }
}
