/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.entity.Pool

// =============================================================================
// POOL SYSTEM CODE GENERATION
// =============================================================================

// Constants for repeated C code strings
private const val LOOP_INDEX_DECL = "UINT8 i;"

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
        generatePoolSpawnFunction(pool)
        generatePoolDespawnFunction(pool)
        generatePoolDespawnAllFunction(pool)
        generatePoolShowFunction(pool)
        generatePoolHideFunction(pool)
        generatePoolUpdateFunction(pool)
    }
}

private fun CodeGenerator.generatePoolSpawnFunction(pool: Pool) {
    val name = pool.name
    val nameUpper = name.uppercase()

    block("static UINT8 ${name}_spawn(void)") {
        line(LOOP_INDEX_DECL)
        block("for (i = 0; i < ${nameUpper}_POOL_SIZE; i++)") {
            block("if (${name}_active[i] == 0)") {
                line("${name}_active[i] = 1;")
                line("${name}_pool_count++;")

                if (pool.onSpawnStatements.isNotEmpty()) {
                    line("// onSpawn")
                    line("{")
                    indent++
                    line("UINT8 _${name}_i = i;")
                    for (stmt in pool.onSpawnStatements) {
                        generateStatement(stmt)
                    }
                    indent--
                    line("}")
                }

                if (pool.spriteAsset != null && pool.hasPosition) {
                    line("move_sprite(${nameUpper}_OAM_START + i, ${name}_x[i], ${name}_y[i]);")
                }

                line("return i;")
            }
        }
        line("return 255;  // Pool full")
    }
    line()
}

private fun CodeGenerator.generatePoolDespawnFunction(pool: Pool) {
    val name = pool.name
    val nameUpper = name.uppercase()

    block("static void ${name}_despawn(UINT8 i)") {
        line("if (i >= ${nameUpper}_POOL_SIZE) return;")
        line("if (${name}_active[i] == 0) return;")
        line()

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

        if (pool.spriteAsset != null) {
            line("move_sprite(${nameUpper}_OAM_START + i, 0, 0);  // Hide sprite")
        }

        line("${name}_active[i] = 0;")
        line("${name}_pool_count--;")
    }
    line()
}

private fun CodeGenerator.generatePoolDespawnAllFunction(pool: Pool) {
    val name = pool.name
    val nameUpper = name.uppercase()

    block("static void ${name}_despawn_all(void)") {
        line(LOOP_INDEX_DECL)
        block("for (i = 0; i < ${nameUpper}_POOL_SIZE; i++)") {
            block("if (${name}_active[i])") { line("${name}_despawn(i);") }
        }
    }
    line()
}

private fun CodeGenerator.generatePoolShowFunction(pool: Pool) {
    val name = pool.name
    val nameUpper = name.uppercase()

    block("static void ${name}_show(UINT8 i)") {
        if (pool.spriteAsset != null && pool.hasPosition) {
            line("move_sprite(${nameUpper}_OAM_START + i, ${name}_x[i], ${name}_y[i]);")
        }
    }
    line()
}

private fun CodeGenerator.generatePoolHideFunction(pool: Pool) {
    val name = pool.name
    val nameUpper = name.uppercase()

    block("static void ${name}_hide(UINT8 i)") {
        if (pool.spriteAsset != null) {
            line("move_sprite(${nameUpper}_OAM_START + i, 0, 0);")
        }
    }
    line()
}

private fun CodeGenerator.generatePoolOnFrameBlock(pool: Pool) {
    val name = pool.name
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

private fun CodeGenerator.generatePoolDespawnConditionsBlock(pool: Pool) {
    val name = pool.name
    line("// Check despawn conditions")
    line("{")
    indent++
    line("UINT8 _${name}_i = i;")
    val conditions = pool.despawnConditions.joinToString(" || ") { generateExpr(it) }
    block("if ($conditions)") {
        line("${name}_despawn(i);")
        line("continue;")
    }
    indent--
    line("}")
}

private fun CodeGenerator.generatePoolUpdateFunction(pool: Pool) {
    val name = pool.name
    val nameUpper = name.uppercase()

    block("static void ${name}_update(void)") {
        line(LOOP_INDEX_DECL)
        block("for (i = 0; i < ${nameUpper}_POOL_SIZE; i++)") {
            block("if (${name}_active[i])") {
                if (pool.onFrameStatements.isNotEmpty()) {
                    generatePoolOnFrameBlock(pool)
                }

                if (pool.spriteAsset != null && pool.hasPosition) {
                    line("move_sprite(${nameUpper}_OAM_START + i, ${name}_x[i], ${name}_y[i]);")
                }

                if (pool.despawnConditions.isNotEmpty()) {
                    generatePoolDespawnConditionsBlock(pool)
                }
            }
        }
    }
    line()
}
