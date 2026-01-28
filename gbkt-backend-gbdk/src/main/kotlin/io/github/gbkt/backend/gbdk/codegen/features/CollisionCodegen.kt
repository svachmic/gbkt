/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.features

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator

// =============================================================================
// SWEEP COLLISION CODE GENERATION
// Generates optimized C helper functions for sweep/continuous collision detection
// =============================================================================

/**
 * Generate sweep collision helper functions.
 *
 * These functions implement continuous collision detection for fast-moving objects:
 * - sweep_aabb_test: Expanded AABB test (simple, efficient)
 * - sweep_aabb_precise: Precise collision with hit time and normal (for physics response)
 */
internal fun GBDKCodeGenerator.generateSweepCollisionFunctions() {
    // Only generate if game uses collision detection
    val hasCollision = game.entities.any { it.hasHitbox }
    if (!hasCollision) return

    line("// === Sweep Collision System ===")
    line()

    // Fixed-point constants for collision math
    line("// Fixed-point constants (8.8 format)")
    line("#define FP_ONE 256")
    line("#define FP_HALF 128")
    line()

    // Sweep result structure
    generateSweepResultStruct()
    line()

    // Simple sweep collision (expanded AABB)
    generateSimpleSweepFunction()
    line()

    // Precise sweep collision with hit time and normal
    generatePreciseSweepFunction()
    line()

    // Helper functions
    generateCollisionHelpers()
    line()
}

/** Generate the sweep result structure. */
private fun GBDKCodeGenerator.generateSweepResultStruct() {
    line("// Sweep collision result")
    line("typedef struct {")
    line("    UINT8 collided;      // 1 if collision occurred")
    line("    UINT8 hit_time;      // 0-255 fixed point (0=start, 255=end of movement)")
    line("    INT8 normal_x;       // Collision normal X (-1, 0, or 1)")
    line("    INT8 normal_y;       // Collision normal Y (-1, 0, or 1)")
    line("    INT16 contact_x;     // Contact point X")
    line("    INT16 contact_y;     // Contact point Y")
    line("} SweepResult;")
}

/**
 * Generate the simple sweep collision function (expanded AABB approach).
 *
 * This is fast and works well for detecting "did fast object hit target?".
 */
private fun GBDKCodeGenerator.generateSimpleSweepFunction() {
    line("// Simple sweep collision using expanded AABB")
    line("// Returns 1 if the moving box collided with target during movement")
    block(
        "UINT8 sweep_aabb_test(" +
            "INT16 start_x, INT16 start_y, " +
            "INT16 delta_x, INT16 delta_y, " +
            "UINT8 width, UINT8 height, " +
            "INT16 target_x, INT16 target_y, " +
            "UINT8 target_w, UINT8 target_h)"
    ) {
        line("// Calculate end position")
        line("INT16 end_x = start_x + delta_x;")
        line("INT16 end_y = start_y + delta_y;")
        line()
        line("// Calculate swept bounds (min/max of start and end)")
        line("INT16 min_x = (start_x < end_x) ? start_x : end_x;")
        line("INT16 max_x = (start_x > end_x) ? start_x + width : end_x + width;")
        line("INT16 min_y = (start_y < end_y) ? start_y : end_y;")
        line("INT16 max_y = (start_y > end_y) ? start_y + height : end_y + height;")
        line()
        line("// Target bounds")
        line("INT16 target_right = target_x + target_w;")
        line("INT16 target_bottom = target_y + target_h;")
        line()
        line("// Check if swept bounds overlap target")
        line("return (max_x > target_x && min_x < target_right &&")
        line("        max_y > target_y && min_y < target_bottom) ? 1u : 0u;")
    }
}

/**
 * Generate the precise sweep collision function.
 *
 * Uses the slab method for ray-AABB intersection with:
 * - Exact hit time (0-255)
 * - Collision normal (-1, 0, or 1)
 * - Contact point
 */
private fun GBDKCodeGenerator.generatePreciseSweepFunction() {
    line("// Precise sweep collision with hit time and normal")
    line("// Uses slab method for ray-AABB intersection")
    block(
        "SweepResult sweep_aabb_precise(" +
            "INT16 start_x, INT16 start_y, " +
            "INT16 delta_x, INT16 delta_y, " +
            "UINT8 width, UINT8 height, " +
            "INT16 target_x, INT16 target_y, " +
            "UINT8 target_w, UINT8 target_h)"
    ) {
        line("SweepResult result = {0, 0, 0, 0, 0, 0};")
        line()
        line("// Expand target by moving box size (Minkowski sum)")
        line("INT16 exp_left = target_x - width;")
        line("INT16 exp_right = target_x + target_w;")
        line("INT16 exp_top = target_y - height;")
        line("INT16 exp_bottom = target_y + target_h;")
        line()
        line("// Handle zero movement")
        block("if (delta_x == 0 && delta_y == 0)") {
            line("// Static collision check")
            block(
                "if (start_x >= exp_left && start_x < exp_right && " +
                    "start_y >= exp_top && start_y < exp_bottom)"
            ) {
                line("result.collided = 1;")
                line("result.contact_x = start_x;")
                line("result.contact_y = start_y;")
            }
            line("return result;")
        }
        line()
        line("// Calculate entry/exit distances for X axis")
        line("INT16 entry_dist_x, exit_dist_x;")
        block("if (delta_x >= 0)") {
            line("entry_dist_x = exp_left - start_x;")
            line("exit_dist_x = exp_right - start_x;")
        }
        block("else") {
            line("entry_dist_x = exp_right - start_x;")
            line("exit_dist_x = exp_left - start_x;")
        }
        line()
        line("// Calculate entry/exit distances for Y axis")
        line("INT16 entry_dist_y, exit_dist_y;")
        block("if (delta_y >= 0)") {
            line("entry_dist_y = exp_top - start_y;")
            line("exit_dist_y = exp_bottom - start_y;")
        }
        block("else") {
            line("entry_dist_y = exp_bottom - start_y;")
            line("exit_dist_y = exp_top - start_y;")
        }
        line()
        line("// Get absolute values for time comparison")
        line("INT16 abs_dx = (delta_x >= 0) ? delta_x : -delta_x;")
        line("INT16 abs_dy = (delta_y >= 0) ? delta_y : -delta_y;")
        line()
        line("// Handle axis-aligned movement (avoid divide by zero)")
        block("if (abs_dx == 0)") {
            line("// Moving vertically only")
            block("if (start_x < exp_left || start_x >= exp_right)") {
                line("return result; // No collision possible")
            }
            line("// Check Y axis only")
            block("if (abs_dy > 0)") {
                line("INT16 entry_time = (entry_dist_y * 255) / abs_dy;")
                line("INT16 exit_time = (exit_dist_y * 255) / abs_dy;")
                block("if (entry_time <= 255 && entry_time >= 0 && entry_time < exit_time)") {
                    line("result.collided = 1;")
                    line("result.hit_time = (UINT8)entry_time;")
                    line("result.normal_y = (delta_y > 0) ? -1 : 1;")
                    line("result.contact_x = start_x;")
                    line("result.contact_y = start_y + (delta_y * entry_time) / 255;")
                }
            }
            line("return result;")
        }
        line()
        block("if (abs_dy == 0)") {
            line("// Moving horizontally only")
            block("if (start_y < exp_top || start_y >= exp_bottom)") {
                line("return result; // No collision possible")
            }
            line("// Check X axis only")
            block("if (abs_dx > 0)") {
                line("INT16 entry_time = (entry_dist_x * 255) / abs_dx;")
                line("INT16 exit_time = (exit_dist_x * 255) / abs_dx;")
                block("if (entry_time <= 255 && entry_time >= 0 && entry_time < exit_time)") {
                    line("result.collided = 1;")
                    line("result.hit_time = (UINT8)entry_time;")
                    line("result.normal_x = (delta_x > 0) ? -1 : 1;")
                    line("result.contact_x = start_x + (delta_x * entry_time) / 255;")
                    line("result.contact_y = start_y;")
                }
            }
            line("return result;")
        }
        line()
        line("// Cross-multiplication to compare entry times without division")
        line("// entry_time_x > entry_time_y iff entry_dist_x * abs_dy > entry_dist_y * abs_dx")
        line("INT32 entry_x_scaled = (INT32)entry_dist_x * abs_dy;")
        line("INT32 entry_y_scaled = (INT32)entry_dist_y * abs_dx;")
        line("INT32 exit_x_scaled = (INT32)exit_dist_x * abs_dy;")
        line("INT32 exit_y_scaled = (INT32)exit_dist_y * abs_dx;")
        line()
        line("// Entry time is max of axis entry times")
        line("INT32 max_entry_scaled, min_exit_scaled;")
        line("UINT8 x_entry_later = (entry_x_scaled >= entry_y_scaled) ? 1u : 0u;")
        line("max_entry_scaled = x_entry_later ? entry_x_scaled : entry_y_scaled;")
        line()
        line("// Exit time is min of axis exit times")
        line("min_exit_scaled = (exit_x_scaled <= exit_y_scaled) ? exit_x_scaled : exit_y_scaled;")
        line()
        line("// Total delta for scaling")
        line("INT32 total_delta = (INT32)abs_dx * abs_dy;")
        line()
        line("// Collision if entry < exit and entry is in [0, total_delta]")
        block(
            "if (max_entry_scaled < min_exit_scaled && " +
                "max_entry_scaled >= 0 && max_entry_scaled <= total_delta)"
        ) {
            line("result.collided = 1;")
            line()
            line("// Calculate hit time as 0-255")
            line("result.hit_time = (UINT8)((max_entry_scaled * 255) / total_delta);")
            line()
            line("// Set normal based on which axis had later entry")
            block("if (x_entry_later)") {
                line("result.normal_x = (delta_x > 0) ? -1 : 1;")
                line("result.normal_y = 0;")
            }
            block("else") {
                line("result.normal_x = 0;")
                line("result.normal_y = (delta_y > 0) ? -1 : 1;")
            }
            line()
            line("// Calculate contact point")
            line("result.contact_x = start_x + (delta_x * result.hit_time) / 255;")
            line("result.contact_y = start_y + (delta_y * result.hit_time) / 255;")
        }
        line()
        line("return result;")
    }
}

/** Generate collision helper functions. */
private fun GBDKCodeGenerator.generateCollisionHelpers() {
    line("// Helper: Simple AABB overlap test (static)")
    block(
        "UINT8 aabb_overlaps(" +
            "INT16 ax, INT16 ay, UINT8 aw, UINT8 ah, " +
            "INT16 bx, INT16 by, UINT8 bw, UINT8 bh)"
    ) {
        line("return (ax < bx + bw && ax + aw > bx &&")
        line("        ay < by + bh && ay + ah > by) ? 1u : 0u;")
    }
    line()

    line("// Helper: Point-in-AABB test")
    block("UINT8 point_in_aabb(INT16 px, INT16 py, INT16 ax, INT16 ay, UINT8 aw, UINT8 ah)") {
        line("return (px >= ax && px < ax + aw && py >= ay && py < ay + ah) ? 1u : 0u;")
    }
    line()

    line("// Helper: Get collision response velocity (bounce/slide)")
    line("// Returns adjusted velocity after collision")
    block("INT16 collision_response(INT16 velocity, INT8 normal, UINT8 bounce_factor)") {
        line("// bounce_factor is 0-255 (0 = full stop, 255 = full bounce)")
        block("if (normal != 0)") {
            line("// Reflect velocity with bounce factor")
            line("return -(INT16)(((INT32)velocity * bounce_factor) >> 8);")
        }
        line("return velocity;")
    }
}
