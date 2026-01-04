/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.graphics.Hitbox

// =============================================================================
// PHYSICS WORLD CODE GENERATION
// Global physics system with automatic collision response
// =============================================================================

internal fun CodeGenerator.generatePhysicsFunctions() {
    val physicsWorld = game.physicsWorld ?: return

    line("// === Physics World System ===")
    line()

    // Generate physics world constants
    line("// Physics world constants (fixed-point 8.8)")
    line("#define PHYSICS_GRAVITY ${floatToFixed88(physicsWorld.config.gravity)}")
    line("#define PHYSICS_FRICTION ${floatToFixed88(physicsWorld.config.friction)}")
    line("#define PHYSICS_BOUNCE ${floatToFixed88(physicsWorld.config.bounce)}")
    line()

    // Get all entities with physics component and tags
    val entitiesWithPhysics = game.entities.filter { it.hasPhysics }
    if (entitiesWithPhysics.isEmpty()) {
        line("// No entities with physics component")
        line()
        return
    }

    // Generate per-entity physics constants (mass, local friction)
    line("// Per-entity physics constants")
    for (entity in entitiesWithPhysics) {
        val name = entity.name.uppercase()
        val physics = entity.physicsComponent!!
        line("#define ${name}_MASS ${physics.mass}")
        if (physics.useLocalFriction) {
            line("#define ${name}_FRICTION ${physics.friction}")
        }
    }
    line()

    // Generate gravity zones if defined
    if (physicsWorld.gravityZones.isNotEmpty()) {
        line("// Gravity zones")
        line("#define GRAVITY_ZONE_COUNT ${physicsWorld.gravityZones.size}")
        line()
        line("typedef struct {")
        line("    INT16 x, y, width, height;")
        line("    INT16 gravity;")
        line("} GravityZone;")
        line()
        line("const GravityZone _gravity_zones[GRAVITY_ZONE_COUNT] = {")
        for (zone in physicsWorld.gravityZones) {
            line("    { ${zone.x}, ${zone.y}, ${zone.width}, ${zone.height}, ${zone.gravity} },")
        }
        line("};")
        line()

        // Helper function to get gravity for a position
        block("INT16 _get_gravity_at(INT16 x, INT16 y)") {
            line("UINT8 i;")
            block("for (i = 0; i < GRAVITY_ZONE_COUNT; i++)") {
                block(
                    "if (x >= _gravity_zones[i].x && x < _gravity_zones[i].x + _gravity_zones[i].width && " +
                        "y >= _gravity_zones[i].y && y < _gravity_zones[i].y + _gravity_zones[i].height)"
                ) {
                    line("return _gravity_zones[i].gravity;")
                }
            }
            line("return PHYSICS_GRAVITY;")
        }
        line()
    }

    // Generate physics update function
    block("void _physics_world_update(void)") {
        line("// Apply physics to all entities with physics component")
        line()

        val hasGravityZones = physicsWorld.gravityZones.isNotEmpty()

        // Step 1: Apply physics (gravity, friction, velocity clamping, position update)
        for (entity in entitiesWithPhysics) {
            val name = entity.name
            val nameUpper = name.uppercase()
            val physics = entity.physicsComponent!!

            block("// Physics update for $name") {
                // Apply gravity (check zones if defined)
                line("// Apply gravity")
                if (hasGravityZones) {
                    line("_${name}_vel_y_fp += _get_gravity_at(${name}_x, ${name}_y);")
                } else {
                    line("_${name}_vel_y_fp += PHYSICS_GRAVITY;")
                }

                // Apply friction (use local if configured, otherwise global)
                line("// Apply friction")
                val frictionConstant =
                    if (physics.useLocalFriction) "${nameUpper}_FRICTION" else "PHYSICS_FRICTION"
                line(
                    "_${name}_vel_x_fp = (INT16)(((INT32)_${name}_vel_x_fp * $frictionConstant) >> 8);"
                )

                // Clamp velocity to max bounds (convert max to fixed-point: max * 256)
                line("// Clamp velocity")
                line(
                    "if (_${name}_vel_x_fp > (${nameUpper}_MAX_VEL_X << 8)) _${name}_vel_x_fp = ${nameUpper}_MAX_VEL_X << 8;"
                )
                line(
                    "if (_${name}_vel_x_fp < -(${nameUpper}_MAX_VEL_X << 8)) _${name}_vel_x_fp = -(${nameUpper}_MAX_VEL_X << 8);"
                )
                line(
                    "if (_${name}_vel_y_fp > (${nameUpper}_MAX_VEL_Y << 8)) _${name}_vel_y_fp = ${nameUpper}_MAX_VEL_Y << 8;"
                )
                line(
                    "if (_${name}_vel_y_fp < -(${nameUpper}_MAX_VEL_Y << 8)) _${name}_vel_y_fp = -(${nameUpper}_MAX_VEL_Y << 8);"
                )

                // Update position from velocity (shift right 8 to get integer part)
                line("// Update position from velocity")
                line("${name}_x += (_${name}_vel_x_fp >> 8);")
                line("${name}_y += (_${name}_vel_y_fp >> 8);")

                // Also update the i8 velocity variables to match (for DSL access)
                line("// Sync i8 velocity variables")
                line("${name}_vel_x = (_${name}_vel_x_fp >> 8);")
                line("${name}_vel_y = (_${name}_vel_y_fp >> 8);")
            }
            line()
        }

        // Step 2: Collision detection and response
        if (physicsWorld.collisionPairs.isNotEmpty()) {
            line("// Collision detection and response")
            line()

            for ((tag1, tag2) in physicsWorld.collisionPairs) {
                generateCollisionResponse(tag1, tag2)
                line()
            }
        }
    }
    line()
}

/** Generate collision response code between entities with tag1 and entities with tag2. */
private fun CodeGenerator.generateCollisionResponse(tag1: String, tag2: String) {
    // Get entities with each tag that have physics and position components
    val tag1Entities =
        game.entities.filter { entity ->
            tag1 in (entity.tags) && entity.hasPhysics && entity.hasPosition
        }
    val tag2Entities =
        game.entities.filter { entity ->
            tag2 in (entity.tags) && entity.hasPhysics && entity.hasPosition
        }

    if (tag1Entities.isEmpty() || tag2Entities.isEmpty()) {
        line("// Collision response: $tag1 <-> $tag2 (no entities found)")
        return
    }

    block("// Collision response: $tag1 <-> $tag2") {
        // Nested loops to check all pairs
        for (entity1 in tag1Entities) {
            for (entity2 in tag2Entities) {
                // Skip self-collision
                if (entity1.name == entity2.name) continue

                val name1 = entity1.name
                val name2 = entity2.name
                val nameUpper1 = name1.uppercase()
                val nameUpper2 = name2.uppercase()
                val pos1 = entity1.positionComponent!!
                val pos2 = entity2.positionComponent!!

                // Get hitboxes (default to 8x8 if not specified)
                val hitbox1 = entity1.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)
                val hitbox2 = entity2.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)

                block("// Check collision: $name1 <-> $name2") {
                    // AABB collision detection
                    line("INT16 left1 = ${pos1.xVarName} + ${hitbox1.xOffset};")
                    line("INT16 top1 = ${pos1.yVarName} + ${hitbox1.yOffset};")
                    line("INT16 right1 = left1 + ${hitbox1.width};")
                    line("INT16 bottom1 = top1 + ${hitbox1.height};")
                    line()
                    line("INT16 left2 = ${pos2.xVarName} + ${hitbox2.xOffset};")
                    line("INT16 top2 = ${pos2.yVarName} + ${hitbox2.yOffset};")
                    line("INT16 right2 = left2 + ${hitbox2.width};")
                    line("INT16 bottom2 = top2 + ${hitbox2.height};")
                    line()
                    block(
                        "if (right1 > left2 && left1 < right2 && bottom1 > top2 && top1 < bottom2)"
                    ) {
                        // Collision detected - apply mass-based bounce response
                        line("// Collision detected - apply mass-based bounce")
                        line()

                        // Calculate mass ratio for impulse distribution
                        // mass_ratio1 = mass2 / (mass1 + mass2)  (entity1 receives this fraction)
                        // mass_ratio2 = mass1 / (mass1 + mass2)  (entity2 receives this fraction)
                        line("// Calculate mass ratio for impulse distribution")
                        line(
                            "INT32 total_mass = (INT32)${nameUpper1}_MASS + (INT32)${nameUpper2}_MASS;"
                        )
                        line(
                            "INT16 mass_ratio1 = (INT16)(((INT32)${nameUpper2}_MASS << 8) / total_mass);"
                        )
                        line(
                            "INT16 mass_ratio2 = (INT16)(((INT32)${nameUpper1}_MASS << 8) / total_mass);"
                        )
                        line()

                        // Calculate collision normal (simplified: use center-to-center vector)
                        line("// Calculate collision normal")
                        line(
                            "INT16 center1_x = ${pos1.xVarName} + ${hitbox1.xOffset + hitbox1.width / 2};"
                        )
                        line(
                            "INT16 center1_y = ${pos1.yVarName} + ${hitbox1.yOffset + hitbox1.height / 2};"
                        )
                        line(
                            "INT16 center2_x = ${pos2.xVarName} + ${hitbox2.xOffset + hitbox2.width / 2};"
                        )
                        line(
                            "INT16 center2_y = ${pos2.yVarName} + ${hitbox2.yOffset + hitbox2.height / 2};"
                        )
                        line()
                        line("INT16 dx = center1_x - center2_x;")
                        line("INT16 dy = center1_y - center2_y;")
                        line()

                        // Separate entities (mass-based)
                        line("// Separate entities (mass-weighted)")
                        block("if (dx != 0 || dy != 0)") {
                            // Normalize (simplified: use max of abs(dx), abs(dy))
                            line("INT16 abs_dx = (dx < 0) ? -dx : dx;")
                            line("INT16 abs_dy = (dy < 0) ? -dy : dy;")
                            line()
                            block("if (abs_dx > abs_dy)") {
                                // Horizontal collision
                                line(
                                    "INT16 overlap = (dx > 0) ? (right2 - left1 + 1) : (left2 - right1 - 1);"
                                )
                                line("// Mass-weighted separation: lighter objects move more")
                                line(
                                    "${pos1.xVarName} += (INT16)(((INT32)overlap * mass_ratio1) >> 8);"
                                )
                                line(
                                    "${pos2.xVarName} -= (INT16)(((INT32)overlap * mass_ratio2) >> 8);"
                                )
                                line()
                                line("// Mass-weighted bounce velocity")
                                block("if (dx > 0)") {
                                    // Entity1 is to the right, bounce right
                                    block("if (_${name1}_vel_x_fp < 0)") {
                                        line(
                                            "_${name1}_vel_x_fp = -(INT16)(((INT32)_${name1}_vel_x_fp * PHYSICS_BOUNCE * mass_ratio1) >> 16);"
                                        )
                                    }
                                    block("if (_${name2}_vel_x_fp > 0)") {
                                        line(
                                            "_${name2}_vel_x_fp = -(INT16)(((INT32)_${name2}_vel_x_fp * PHYSICS_BOUNCE * mass_ratio2) >> 16);"
                                        )
                                    }
                                }
                                block("else") {
                                    // Entity1 is to the left, bounce left
                                    block("if (_${name1}_vel_x_fp > 0)") {
                                        line(
                                            "_${name1}_vel_x_fp = -(INT16)(((INT32)_${name1}_vel_x_fp * PHYSICS_BOUNCE * mass_ratio1) >> 16);"
                                        )
                                    }
                                    block("if (_${name2}_vel_x_fp < 0)") {
                                        line(
                                            "_${name2}_vel_x_fp = -(INT16)(((INT32)_${name2}_vel_x_fp * PHYSICS_BOUNCE * mass_ratio2) >> 16);"
                                        )
                                    }
                                }
                            }
                            block("else") {
                                // Vertical collision
                                line(
                                    "INT16 overlap = (dy > 0) ? (bottom2 - top1 + 1) : (top2 - bottom1 - 1);"
                                )
                                line("// Mass-weighted separation: lighter objects move more")
                                line(
                                    "${pos1.yVarName} += (INT16)(((INT32)overlap * mass_ratio1) >> 8);"
                                )
                                line(
                                    "${pos2.yVarName} -= (INT16)(((INT32)overlap * mass_ratio2) >> 8);"
                                )
                                line()
                                line("// Mass-weighted bounce velocity")
                                block("if (dy > 0)") {
                                    // Entity1 is below, bounce down
                                    block("if (_${name1}_vel_y_fp < 0)") {
                                        line(
                                            "_${name1}_vel_y_fp = -(INT16)(((INT32)_${name1}_vel_y_fp * PHYSICS_BOUNCE * mass_ratio1) >> 16);"
                                        )
                                    }
                                    block("if (_${name2}_vel_y_fp > 0)") {
                                        line(
                                            "_${name2}_vel_y_fp = -(INT16)(((INT32)_${name2}_vel_y_fp * PHYSICS_BOUNCE * mass_ratio2) >> 16);"
                                        )
                                    }
                                }
                                block("else") {
                                    // Entity1 is above, bounce up
                                    block("if (_${name1}_vel_y_fp > 0)") {
                                        line(
                                            "_${name1}_vel_y_fp = -(INT16)(((INT32)_${name1}_vel_y_fp * PHYSICS_BOUNCE * mass_ratio1) >> 16);"
                                        )
                                    }
                                    block("if (_${name2}_vel_y_fp < 0)") {
                                        line(
                                            "_${name2}_vel_y_fp = -(INT16)(((INT32)_${name2}_vel_y_fp * PHYSICS_BOUNCE * mass_ratio2) >> 16);"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Convert float to fixed-point 8.8 format (multiply by 256). */
private fun floatToFixed88(value: Float): Int {
    return (value * 256f).toInt().coerceIn(-32768, 32767)
}
