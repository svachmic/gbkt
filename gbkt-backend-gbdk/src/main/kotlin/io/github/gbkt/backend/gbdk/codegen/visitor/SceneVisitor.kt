/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ScriptOp

// =============================================================================
// SCENE VISITOR
// Translates IR v2 SceneIR nodes into typed C AST CFunction nodes.
// Generates {id}_enter, {id}_frame, {id}_exit lifecycle functions.
// Empty lifecycle handlers are skipped (not generated).
// =============================================================================

/**
 * Visitor that converts IR v2 [SceneIR] nodes to lists of typed C AST [CFunction] nodes.
 *
 * Each [SceneIR] produces up to 3 lifecycle functions:
 * - `{id}_enter` — from [SceneIR.enterOps], if non-empty. Carries the sectionComment.
 * - `{id}_frame` — from [SceneIR.frameOps], if non-empty.
 * - `{id}_exit` — from [SceneIR.exitOps], if non-empty.
 *
 * Bank assignment: if [SceneIR.bankSlot] is non-null with bank > 0, the generated functions carry
 * [CFunction.bank] set to that bank number and [CFunction.isBanked] = true. If [bankSlot] is null
 * (no analysis ran, e.g., in tests), functions default to isBanked = true, bank = null (inherits
 * from CFile).
 *
 * All functions are marked [CFunction.isBanked] = true — scene code lives in banked ROM. The
 * sectionComment ("Scene: {id}") is added only to the first function (enter) so that the emitter
 * can output a block separator comment without repetition.
 *
 * Delegates script op translation to [ScriptOpVisitor]. Delegates scene enum generation to
 * [generateSceneEnum].
 */
object SceneVisitor {

    /**
     * Convert a [SceneIR] node to a list of [CFunction] nodes.
     *
     * Only lifecycle handlers with at least one [ScriptOp] are generated. Empty handlers are
     * skipped to avoid emitting empty C functions.
     *
     * If [SceneIR.bankSlot] is non-null with bank > 0, the generated functions have bank set to
     * that bank number. If bankSlot is null (default, no analysis ran), bank is null and isBanked
     * defaults to true for backward compatibility.
     *
     * @param scene The scene IR node to convert.
     * @param actors Actor list passed to [ExprVisitor] for collision-aware expression codegen.
     *   Defaults to empty list for backward-compatible usage without actor context.
     */
    fun visit(scene: SceneIR, actors: List<ActorIR> = emptyList()): List<CFunction> {
        val functions = mutableListOf<CFunction>()
        val exprVisitor = ExprVisitor(actors)

        val sceneBank = scene.bankSlot?.bank
        // isBanked is true when:
        // - bankSlot is present and bank > 0 (explicit bank assignment from analysis), OR
        // - bankSlot is null (default backward-compat behavior — all scene functions are BANKED)
        val sceneBanked = sceneBank == null || sceneBank > 0

        if (scene.enterOps.isNotEmpty()) {
            functions +=
                CFunction(
                    name = "${scene.id}_enter",
                    returnType = CVoid,
                    body = scene.enterOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
                    bank = sceneBank,
                    isBanked = sceneBanked,
                    sectionComment = "Scene: ${scene.id}",
                )
        }

        if (scene.frameOps.isNotEmpty()) {
            functions +=
                CFunction(
                    name = "${scene.id}_frame",
                    returnType = CVoid,
                    body = scene.frameOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
                    bank = sceneBank,
                    isBanked = sceneBanked,
                )
        }

        if (scene.exitOps.isNotEmpty()) {
            functions +=
                CFunction(
                    name = "${scene.id}_exit",
                    returnType = CVoid,
                    body = scene.exitOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
                    bank = sceneBank,
                    isBanked = sceneBanked,
                )
        }

        return functions
    }

    /**
     * Generate `#define` constants for a list of scene IDs.
     *
     * Scene IDs are uppercased and prefixed with `SCENE_`:
     * - `"title"` → `CDefine("SCENE_TITLE", "0")`
     * - `"game"` → `CDefine("SCENE_GAME", "1")`
     *
     * These constants are used by [ScriptOpVisitor.visitNavigateTo] to produce type-safe scene
     * references in the generated C code.
     */
    fun generateSceneEnum(sceneIds: List<String>): List<CDefine> {
        return sceneIds.mapIndexed { index, id -> CDefine("SCENE_${id.uppercase()}", "$index") }
    }
}
