/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

/**
 * Tilemap collision code generation.
 *
 * Generates helper functions for checking collision data from Tiled maps.
 */

/** Generate collision check helper functions for all tilemaps with collision data. */
internal fun CodeGenerator.generateCollisionHelpers() {
    val mapsWithCollision = game.mapData.filter { it.collisionData != null }
    if (mapsWithCollision.isEmpty()) return

    line("// === Collision Helpers ===")
    line()

    // Generate a generic collision check function
    line("// Check if a tile is blocked (collision value > 0)")
    block(
        "static inline UINT8 _check_collision(const unsigned char *collision_map, UINT8 map_width, UINT8 x, UINT8 y)"
    ) {
        line("UINT16 idx = (UINT16)y * map_width + x;")
        line("return collision_map[idx] > 0;")
    }
    line()

    // Generate map-specific collision check functions
    for (map in mapsWithCollision) {
        val name = map.name
        val upperName = name.uppercase()

        line("// Check collision for ${name} at tile coordinates")
        block("static inline UINT8 ${name}_is_blocked(UINT8 tile_x, UINT8 tile_y)") {
            line("if (tile_x >= ${upperName}_WIDTH || tile_y >= ${upperName}_HEIGHT) return 1;")
            line("return _check_collision(${name}_collision, ${upperName}_WIDTH, tile_x, tile_y);")
        }
        line()

        line("// Check collision for ${name} at pixel coordinates")
        block("static inline UINT8 ${name}_is_blocked_px(UINT16 pixel_x, UINT16 pixel_y)") {
            line("UINT8 tile_x = pixel_x >> 3;  // Divide by 8")
            line("UINT8 tile_y = pixel_y >> 3;")
            line("return ${name}_is_blocked(tile_x, tile_y);")
        }
        line()

        line("// Get collision value at tile coordinates (0 = walkable, >0 = blocked)")
        block("static inline UINT8 ${name}_get_collision(UINT8 tile_x, UINT8 tile_y)") {
            line("if (tile_x >= ${upperName}_WIDTH || tile_y >= ${upperName}_HEIGHT) return 0xFF;")
            line("UINT16 idx = (UINT16)tile_y * ${upperName}_WIDTH + tile_x;")
            line("return ${name}_collision[idx];")
        }
        line()
    }
}
