/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

// =============================================================================
// POOL SYSTEM CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generatePoolData() {
    if (game.pools.isEmpty()) return

    line("// === Entity Pools ===")
    line()

    for (pool in game.pools) {
        val name = pool.name
        val size = pool.size
        val nameUpper = name.uppercase()

        line("// Pool: $name (size: $size)")
        line("#define ${nameUpper}_POOL_SIZE $size")
        line("#define ${nameUpper}_OAM_START ${pool.oamStartSlot}")
        line()

        // Generate C arrays instead of unrolled variables
        // Active flags
        line("static UINT8 ${name}_active[${nameUpper}_POOL_SIZE];")

        // Position variables
        if (pool.hasPosition) {
            line("static UINT8 ${name}_x[${nameUpper}_POOL_SIZE];")
            line("static UINT8 ${name}_y[${nameUpper}_POOL_SIZE];")
        }

        // Velocity variables
        if (pool.hasVelocity) {
            line("static INT8 ${name}_vel_x[${nameUpper}_POOL_SIZE];")
            line("static INT8 ${name}_vel_y[${nameUpper}_POOL_SIZE];")
        }

        // Custom state fields
        for (field in pool.stateFields) {
            val cType = field.type.cType
            line("static $cType ${name}_${field.name}[${nameUpper}_POOL_SIZE];")
        }

        // Animation state (if sprite with animations)
        if (pool.animations.isNotEmpty()) {
            line("static UINT8 ${name}_anim[${nameUpper}_POOL_SIZE];")
            line("static UINT8 ${name}_frame[${nameUpper}_POOL_SIZE];")
            line("static UINT8 ${name}_timer[${nameUpper}_POOL_SIZE];")
            line("static UINT8 ${name}_anim_complete[${nameUpper}_POOL_SIZE];")
        }
        line()

        // Pool active count
        line("static UINT8 ${name}_pool_count;")
        line()

        // Animation constants
        if (pool.animations.isNotEmpty()) {
            var animIdx = 0
            for (animName in pool.animations.keys) {
                line("#define ANIM_${nameUpper}_${animName.uppercase()} $animIdx")
                animIdx++
            }
            line()
        }
    }
}

internal fun CodeGenerator.generatePoolFunctions() {
    if (game.pools.isEmpty()) return

    line("// === Pool Functions ===")
    line()

    // Forward declarations - pool functions may call each other (e.g., despawn calls hide)
    for (pool in game.pools) {
        val name = pool.name
        line("static UINT8 ${name}_spawn(void);")
        line("static void ${name}_despawn(UINT8 i);")
        line("static void ${name}_despawn_all(void);")
        line("static void ${name}_show(UINT8 i);")
        line("static void ${name}_hide(UINT8 i);")
        line("static void ${name}_update(void);")
    }
    line()

    for (pool in game.pools) {
        val name = pool.name
        val size = pool.size
        val nameUpper = name.uppercase()

        // Spawn function
        block("static UINT8 ${name}_spawn(void)") {
            line("UINT8 i;")
            block("for (i = 0; i < ${nameUpper}_POOL_SIZE; i++)") {
                block("if (${name}_active[i] == 0)") {
                    line("${name}_active[i] = 1;")
                    line("${name}_pool_count++;")

                    // Execute onSpawn statements
                    if (pool.onSpawnStatements.isNotEmpty()) {
                        line("// onSpawn")
                        // Use i as the index variable
                        line("{")
                        indent++
                        line("UINT8 _${name}_i = i;")
                        for (stmt in pool.onSpawnStatements) {
                            generateStatement(stmt)
                        }
                        indent--
                        line("}")
                    }

                    // Update sprite position
                    if (pool.spriteAsset != null && pool.hasPosition) {
                        line("move_sprite(${nameUpper}_OAM_START + i, ${name}_x[i], ${name}_y[i]);")
                    }

                    line("return i;")
                }
            }
            line("return 255;  // Pool full")
        }
        line()

        // Despawn function
        block("static void ${name}_despawn(UINT8 i)") {
            line("if (i >= ${nameUpper}_POOL_SIZE) return;")
            line("if (${name}_active[i] == 0) return;")
            line()

            // Execute onDespawn statements
            if (pool.onDespawnStatements.isNotEmpty()) {
                line("// onDespawn")
                line("{")
                indent++
                line("UINT8 _${name}_i = i;")
                for (stmt in pool.onDespawnStatements) {
                    generateStatement(stmt)
                }
                indent--
                line("}")
            }

            // Hide sprite
            if (pool.spriteAsset != null) {
                line("move_sprite(${nameUpper}_OAM_START + i, 0, 0);  // Hide sprite")
            }

            line("${name}_active[i] = 0;")
            line("${name}_pool_count--;")
        }
        line()

        // Despawn all function
        block("static void ${name}_despawn_all(void)") {
            line("UINT8 i;")
            block("for (i = 0; i < ${nameUpper}_POOL_SIZE; i++)") {
                block("if (${name}_active[i])") { line("${name}_despawn(i);") }
            }
        }
        line()

        // Show function
        block("static void ${name}_show(UINT8 i)") {
            if (pool.spriteAsset != null && pool.hasPosition) {
                line("move_sprite(${nameUpper}_OAM_START + i, ${name}_x[i], ${name}_y[i]);")
            }
        }
        line()

        // Hide function
        block("static void ${name}_hide(UINT8 i)") {
            if (pool.spriteAsset != null) {
                line("move_sprite(${nameUpper}_OAM_START + i, 0, 0);")
            }
        }
        line()

        // Update function
        block("static void ${name}_update(void)") {
            line("UINT8 i;")
            block("for (i = 0; i < ${nameUpper}_POOL_SIZE; i++)") {
                block("if (${name}_active[i])") {
                    // Execute onFrame statements
                    if (pool.onFrameStatements.isNotEmpty()) {
                        line("// onFrame")
                        line("{")
                        indent++
                        line("UINT8 _${name}_i = i;")
                        for (stmt in pool.onFrameStatements) {
                            generateStatement(stmt)
                        }
                        indent--
                        line("}")
                    }

                    // Update sprite position
                    if (pool.spriteAsset != null && pool.hasPosition) {
                        line("move_sprite(${nameUpper}_OAM_START + i, ${name}_x[i], ${name}_y[i]);")
                    }

                    // Check despawn conditions
                    if (pool.despawnConditions.isNotEmpty()) {
                        line("// Check despawn conditions")
                        line("{")
                        indent++
                        line("UINT8 _${name}_i = i;")
                        val conditions =
                            pool.despawnConditions.joinToString(" || ") { generateExpr(it) }
                        block("if ($conditions)") {
                            line("${name}_despawn(i);")
                            line("continue;")
                        }
                        indent--
                        line("}")
                    }
                }
            }
        }
        line()
    }
}
