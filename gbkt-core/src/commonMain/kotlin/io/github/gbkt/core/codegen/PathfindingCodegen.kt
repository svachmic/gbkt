/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

// =============================================================================
// A* PATHFINDING CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generatePathfindingHelpers() {
    line("// === A* Pathfinding ===")
    line()

    // Check if any navgrid uses weights
    val hasWeightedGrids = game.navGrids.any { it.hasWeights }

    // NavGrid access functions
    block("static inline UINT8 _navgrid_is_walkable(const UINT8 *grid, UINT8 x, UINT8 y)") {
        line("UINT8 w = grid[0];")
        line("UINT8 h = grid[1];")
        line("if (x >= w || y >= h) return 0;")
        line("UINT16 idx = (UINT16)y * w + x;")
        line("return (grid[2 + (idx >> 3)] >> (idx & 7)) & 1;")
    }
    line()

    block("static inline void _navgrid_set_tile(UINT8 *grid, UINT8 x, UINT8 y, UINT8 walkable)") {
        line("UINT8 w = grid[0];")
        line("UINT8 h = grid[1];")
        line("if (x >= w || y >= h) return;  // Bounds check")
        line("UINT16 idx = (UINT16)y * w + x;")
        line("if (walkable) {")
        line("    grid[2 + (idx >> 3)] |= (1 << (idx & 7));")
        line("} else {")
        line("    grid[2 + (idx >> 3)] &= ~(1 << (idx & 7));")
        line("}")
    }
    line()

    // Weight functions (only generated if any grid uses weights)
    if (hasWeightedGrids) {
        line("// Weight data is stored after walkability data: grid + 2 + (w*h+7)/8")
        block(
            "static inline UINT8 _navgrid_get_weight(const UINT8 *grid, const UINT8 *weights, UINT8 x, UINT8 y)"
        ) {
            line("UINT8 w = grid[0];")
            line("UINT8 h = grid[1];")
            line("if (x >= w || y >= h) return 0;")
            line("if (weights == (void*)0) return 1;  // No weight data, use default")
            line("UINT16 idx = (UINT16)y * w + x;")
            line("return weights[idx];")
        }
        line()

        block(
            "static inline void _navgrid_set_weight(UINT8 *grid, UINT8 x, UINT8 y, UINT8 weight)"
        ) {
            line("UINT8 w = grid[0];")
            line("UINT8 h = grid[1];")
            line("if (x >= w || y >= h) return;")
            line("UINT16 idx = (UINT16)y * w + x;")
            line("UINT16 walkable_bytes = ((UINT16)w * h + 7) >> 3;")
            line("grid[2 + walkable_bytes + idx] = weight;")
            line("// Also update walkability")
            line("if (weight > 0) {")
            line("    grid[2 + (idx >> 3)] |= (1 << (idx & 7));")
            line("} else {")
            line("    grid[2 + (idx >> 3)] &= ~(1 << (idx & 7));")
            line("}")
        }
        line()
    }

    // Heuristic functions
    block("static UINT8 _heuristic_manhattan(UINT8 x1, UINT8 y1, UINT8 x2, UINT8 y2)") {
        line("UINT8 dx = (x1 > x2) ? (x1 - x2) : (x2 - x1);")
        line("UINT8 dy = (y1 > y2) ? (y1 - y2) : (y2 - y1);")
        line("return dx + dy;")
    }
    line()

    block("static UINT8 _heuristic_chebyshev(UINT8 x1, UINT8 y1, UINT8 x2, UINT8 y2)") {
        line("UINT8 dx = (x1 > x2) ? (x1 - x2) : (x2 - x1);")
        line("UINT8 dy = (y1 > y2) ? (y1 - y2) : (y2 - y1);")
        line("return (dx > dy) ? dx : dy;")
    }
    line()

    // Binary heap operations
    block("static void _heap_push(UINT8 node_idx, UINT8 f_score)") {
        line("UINT8 i = _astar_open_count++;")
        line("_astar_open_heap[i] = node_idx;")
        line("// Bubble up")
        block("while (i > 0)") {
            line("UINT8 parent = (i - 1) >> 1;")
            line("UINT8 parent_idx = _astar_open_heap[parent];")
            line("UINT8 parent_f = _astar_g_scores[parent_idx] + (_astar_nodes[parent_idx] >> 10);")
            line("if (f_score >= parent_f) break;")
            line("_astar_open_heap[i] = parent_idx;")
            line("i = parent;")
        }
        line("_astar_open_heap[i] = node_idx;")
    }
    line()

    block("static UINT8 _heap_pop(void)") {
        line("UINT8 result = _astar_open_heap[0];")
        line("_astar_open_count--;")
        block("if (_astar_open_count > 0)") {
            line("_astar_open_heap[0] = _astar_open_heap[_astar_open_count];")
            line("// Bubble down")
            line("UINT8 i = 0;")
            block("while (1)") {
                line("UINT8 left = (i << 1) + 1;")
                line("UINT8 right = left + 1;")
                line("UINT8 smallest = i;")
                line("UINT8 cur_idx = _astar_open_heap[i];")
                line("UINT8 cur_f = _astar_g_scores[cur_idx] + (_astar_nodes[cur_idx] >> 10);")
                block("if (left < _astar_open_count)") {
                    line("UINT8 l_idx = _astar_open_heap[left];")
                    line("UINT8 l_f = _astar_g_scores[l_idx] + (_astar_nodes[l_idx] >> 10);")
                    line("if (l_f < cur_f) { smallest = left; cur_f = l_f; }")
                }
                block("if (right < _astar_open_count)") {
                    line("UINT8 r_idx = _astar_open_heap[right];")
                    line("UINT8 r_f = _astar_g_scores[r_idx] + (_astar_nodes[r_idx] >> 10);")
                    line("if (r_f < cur_f) smallest = right;")
                }
                line("if (smallest == i) break;")
                line("UINT8 tmp = _astar_open_heap[i];")
                line("_astar_open_heap[i] = _astar_open_heap[smallest];")
                line("_astar_open_heap[smallest] = tmp;")
                line("i = smallest;")
            }
        }
        line("return result;")
    }
    line()

    // Main A* function
    block(
        "static void _astar_find_path(const UINT8 *grid, UINT8 start_x, UINT8 start_y, UINT8 end_x, UINT8 end_y, UINT8 diagonal, UINT8 max_depth, UINT8 heuristic)"
    ) {
        line("UINT8 grid_w = grid[0];")
        line("UINT8 grid_h = grid[1];")
        line()
        line("// Reset state")
        line("_path_found = 0;")
        line("_path_length = 0;")
        line("_path_current = 0;")
        line("_astar_open_count = 0;")
        line()
        line("// Validate start/end")
        line(
            "if (!_navgrid_is_walkable(grid, start_x, start_y) || !_navgrid_is_walkable(grid, end_x, end_y)) return;"
        )
        line()
        line("// Initialize start node: nodes[i] = tile_idx<<6 | parent_idx")
        line("// We also store heuristic in upper bits for f-score calculation")
        line("UINT16 start_tile = (UINT16)start_y * grid_w + start_x;")
        line(
            "UINT8 start_h = (heuristic == 1) ? _heuristic_chebyshev(start_x, start_y, end_x, end_y) : _heuristic_manhattan(start_x, start_y, end_x, end_y);"
        )
        line("_astar_nodes[0] = (start_tile << 6) | ((UINT16)start_h << 10);")
        line("_astar_g_scores[0] = 0;")
        line("_heap_push(0, start_h);")
        line("UINT8 node_count = 1;")
        line()
        line("// Closed set - using the nodes array itself, mark visited tiles")
        line("static UINT8 _closed[128];  // Up to 32x32 grid")
        line("for (UINT8 i = 0; i < 128; i++) _closed[i] = 0;")
        line()
        line("UINT8 iterations = 0;")
        block("while (_astar_open_count > 0 && iterations < max_depth)") {
            line("iterations++;")
            line("UINT8 current_idx = _heap_pop();")
            line("UINT16 current_node = _astar_nodes[current_idx];")
            line("UINT16 current_tile = (current_node >> 6) & 0x3FF;")
            line("UINT8 current_x = current_tile % grid_w;")
            line("UINT8 current_y = current_tile / grid_w;")
            line("UINT8 current_g = _astar_g_scores[current_idx];")
            line()
            line("// Goal check")
            block("if (current_x == end_x && current_y == end_y)") {
                line("_path_found = 1;")
                line("// Reconstruct path")
                line("UINT8 temp_path[PATH_MAX_LENGTH * 2];")
                line("UINT8 temp_len = 0;")
                line("UINT8 idx = current_idx;")
                block("while (temp_len < PATH_MAX_LENGTH * 2)") {
                    line("UINT16 tile = (_astar_nodes[idx] >> 6) & 0x3FF;")
                    line("temp_path[temp_len++] = tile % grid_w;")
                    line("temp_path[temp_len++] = tile / grid_w;")
                    line("UINT8 parent_idx = _astar_nodes[idx] & 0x3F;")
                    line("if (parent_idx == idx) break;  // Reached start")
                    line("idx = parent_idx;")
                }
                line("// Reverse path into _path_waypoints")
                line("_path_length = temp_len / 2;")
                block("for (UINT8 i = 0; i < temp_len; i += 2)") {
                    line("UINT8 rev_i = temp_len - 2 - i;")
                    line("_path_waypoints[i] = temp_path[rev_i];")
                    line("_path_waypoints[i + 1] = temp_path[rev_i + 1];")
                }
                line("return;")
            }
            line()
            line("// Mark as closed")
            line("_closed[current_tile >> 3] |= (1 << (current_tile & 7));")
            line()
            line("// Expand neighbors")
            line("static const INT8 dx4[] = {0, 1, 0, -1};")
            line("static const INT8 dy4[] = {-1, 0, 1, 0};")
            line("static const INT8 dx8[] = {0, 1, 1, 1, 0, -1, -1, -1};")
            line("static const INT8 dy8[] = {-1, -1, 0, 1, 1, 1, 0, -1};")
            line("UINT8 num_dirs = diagonal ? 8 : 4;")
            line("const INT8 *dxs = diagonal ? dx8 : dx4;")
            line("const INT8 *dys = diagonal ? dy8 : dy4;")
            line()
            block("for (UINT8 d = 0; d < num_dirs; d++)") {
                line("INT8 nx = current_x + dxs[d];")
                line("INT8 ny = current_y + dys[d];")
                line()
                line("// Bounds check")
                line("if (nx < 0 || nx >= grid_w || ny < 0 || ny >= grid_h) continue;")
                line("if (!_navgrid_is_walkable(grid, nx, ny)) continue;")
                line()
                line("UINT16 neighbor_tile = (UINT16)ny * grid_w + nx;")
                line()
                line("// Already closed?")
                line("if ((_closed[neighbor_tile >> 3] >> (neighbor_tile & 7)) & 1) continue;")
                line()
                line("// Calculate g score (diagonal costs same as cardinal for simplicity)")
                line("UINT8 tentative_g = current_g + 1;")
                line()
                block("if (node_count < ASTAR_MAX_NODES)") {
                    line(
                        "UINT8 h = (heuristic == 1) ? _heuristic_chebyshev(nx, ny, end_x, end_y) : _heuristic_manhattan(nx, ny, end_x, end_y);"
                    )
                    line(
                        "_astar_nodes[node_count] = ((UINT16)neighbor_tile << 6) | current_idx | ((UINT16)h << 10);"
                    )
                    line("_astar_g_scores[node_count] = tentative_g;")
                    line("_heap_push(node_count, tentative_g + h);")
                    line("node_count++;")
                }
            }
        }
    }
    line()

    // Path access functions
    block("static void _path_advance(void)") {
        line("if (_path_current < _path_length) _path_current++;")
    }
    line()

    block("static INT8 _path_direction_x(UINT8 current_x)") {
        line("if (_path_current >= _path_length) return 0;")
        line("UINT8 next_x = _path_waypoints[_path_current * 2] << 3;  // Convert tile to pixel")
        line("if (next_x > current_x) return 1;")
        line("if (next_x < current_x) return -1;")
        line("return 0;")
    }
    line()

    block("static INT8 _path_direction_y(UINT8 current_y)") {
        line("if (_path_current >= _path_length) return 0;")
        line(
            "UINT8 next_y = _path_waypoints[_path_current * 2 + 1] << 3;  // Convert tile to pixel"
        )
        line("if (next_y > current_y) return 1;")
        line("if (next_y < current_y) return -1;")
        line("return 0;")
    }
    line()

    block("static UINT8 _path_at_waypoint(UINT8 x, UINT8 y, UINT8 threshold)") {
        line("if (_path_current >= _path_length) return 1;")
        line("UINT8 next_x = _path_waypoints[_path_current * 2] << 3;  // Convert tile to pixel")
        line("UINT8 next_y = _path_waypoints[_path_current * 2 + 1] << 3;")
        line("UINT8 dx = (x > next_x) ? (x - next_x) : (next_x - x);")
        line("UINT8 dy = (y > next_y) ? (y - next_y) : (next_y - y);")
        line("return (dx <= threshold && dy <= threshold) ? 1 : 0;")
    }
    line()
}
