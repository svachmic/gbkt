/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CConst
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR

// =============================================================================
// COLLISION VISITOR
// Generates C code for tile collision system: per-scene collision arrays,
// lookup functions, and dispatch function.
//
// All generated code is C89-compliant:
//  - UINT16 cast prevents 8-bit overflow on maps wider than 15 tiles
//  - const arrays stored in HOME bank
// =============================================================================

/**
 * Generates all C code for the tile collision system from a [GameIR].
 *
 * Produces:
 * 1. Per-scene `const UINT8 map_<scene>_collision[]` arrays
 * 2. Per-scene `_map_collision_<scene>(x, y)` lookup functions
 * 3. `_map_collision(x, y)` dispatch function (delegates based on `current_scene`)
 *
 * @param gameIR The full game IR. Scenes with non-null collisionData/mapWidth are processed.
 */
class CollisionVisitor(private val gameIR: GameIR) {

    /**
     * Determine whether a scene has tile collision data.
     *
     * @return true if the scene has non-null [SceneIR.collisionData] and [SceneIR.mapWidth].
     */
    fun hasCollisionData(scene: SceneIR): Boolean =
        scene.collisionData != null && scene.mapWidth != null

    /**
     * Collect all scenes with collision data and return collision C declarations and functions for
     * inclusion in main.c.
     *
     * Includes per-scene lookup functions AND the `_map_collision(x, y)` dispatch function if any
     * scenes have collision data.
     *
     * @return Pair of (collision array declarations, collision lookup + dispatch functions)
     */
    fun buildCollisionCodegen(): Pair<List<CVarDecl>, List<CFunction>> {
        val collisionArrays = mutableListOf<CVarDecl>()
        val collisionFunctions = mutableListOf<CFunction>()

        for (scene in gameIR.scenes) {
            if (!hasCollisionData(scene)) continue
            collisionArrays += buildCollisionArrayDecl(scene)
            collisionFunctions += buildCollisionFunction(scene)
        }

        // Add the dispatch function (only when at least one scene has collision data)
        buildCollisionDispatchFunction()?.let { collisionFunctions += it }

        return collisionArrays to collisionFunctions
    }

    /**
     * Build the `map_<sceneId>_collision[]` constant byte array for a scene with collision data.
     *
     * Each entry is 0 (passable) or 1 (wall). The array is stored as a `const UINT8` global in the
     * HOME bank for simplicity — it can be relocated to a banked file if needed.
     *
     * Generated C:
     * ```c
     * #define MAP_WIDTH_GAMEPLAY 32
     * const UINT8 map_gameplay_collision[] = { 0, 0, 1, 1, 0, ... };
     * ```
     */
    private fun buildCollisionArrayDecl(scene: SceneIR): CVarDecl {
        val collisionData = scene.collisionData!!
        // Render as { 0, 1, 0, 0, ... } initializer
        val initValues = collisionData.joinToString(", ") { b -> (b.toInt() and 0xFF).toString() }
        return CVarDecl(
            name = "map_${scene.id}_collision",
            type = CArray(CConst(CU8), collisionData.size),
            initializer = CRawExpr("{ $initValues }"),
        )
    }

    /**
     * Build the `_map_collision(x, y)` lookup function for a scene with collision data.
     *
     * The function uses a flat 1D index: `(UINT16)y * MAP_WIDTH + (UINT16)x`. UINT16 cast prevents
     * 8-bit overflow on maps wider than 15 tiles.
     *
     * Generated C:
     * ```c
     * UINT8 _map_collision_gameplay(UINT8 x, UINT8 y) {
     *     return map_gameplay_collision[(UINT16)y * 32 + (UINT16)x];
     * }
     * ```
     */
    private fun buildCollisionFunction(scene: SceneIR): CFunction {
        val mapWidth = scene.mapWidth!!
        val sceneId = scene.id
        // (UINT16)y * mapWidth + (UINT16)x — index into flat 1D collision array
        val castY = CCast(CU16, CVar("y"))
        val castX = CCast(CU16, CVar("x"))
        val indexExpr = CBinaryExpr(CBinaryExpr(castY, "*", CLiteral(mapWidth)), "+", castX)
        return CFunction(
            name = "_map_collision_$sceneId",
            returnType = CU8,
            params = listOf(CParam("x", CU8), CParam("y", CU8)),
            body = listOf(CReturn(CArrayAccess(CVar("map_${sceneId}_collision"), indexExpr))),
            sectionComment = "Tile collision lookup: $sceneId",
        )
    }

    /**
     * Build the `_map_collision(x, y)` dispatch function that delegates to per-scene lookup
     * functions based on `current_scene`.
     *
     * When no scenes have collision data, returns null (no dispatch function emitted).
     *
     * Generated C:
     * ```c
     * UINT8 _map_collision(UINT8 x, UINT8 y) {
     *     switch (current_scene) {
     *         case SCENE_DUNGEON: return _map_collision_dungeon(x, y);
     *         default: return 0; /* no collision data — always passable */
     *     }
     * }
     * ```
     *
     * The dispatch approach avoids function pointers (not C89-friendly on GBDK) while providing a
     * single stable call site for the exploration movement function.
     */
    private fun buildCollisionDispatchFunction(): CFunction? {
        val scenesWithCollision = gameIR.scenes.filter { hasCollisionData(it) }
        if (scenesWithCollision.isEmpty()) return null

        val switchCases =
            scenesWithCollision.map { scene ->
                val sceneConst = "SCENE_${scene.id.uppercase()}"
                CSwitchCase(
                    value = CRawExpr(sceneConst),
                    body =
                        listOf(
                            CReturn(
                                CCall("_map_collision_${scene.id}", listOf(CVar("x"), CVar("y")))
                            )
                        ),
                )
            } +
                CSwitchCase(
                    value = null, // default case
                    body = listOf(CReturn(CRawExpr("0"))), // no collision data — always passable
                )

        return CFunction(
            name = "_map_collision",
            returnType = CU8,
            params = listOf(CParam("x", CU8), CParam("y", CU8)),
            body = listOf(CSwitch(expr = CVar("current_scene"), cases = switchCases)),
            sectionComment = "Tile collision dispatch",
        )
    }
}
