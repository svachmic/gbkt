/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.MoveMetasprite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// Plan 10.1-03: MetaspriteBuilder posX/posY/idx/rot binders propagate captured
// AssignableVar.name strings through both MetaspriteIR and MoveMetasprite.
//
// Companion to MetaspriteIRVarRefFieldsTest (in :gbkt-ir) which asserts the
// underlying nullable defaults. This test drives the full DSL surface:
//   var myX by i16Var(0)
//   var myIdx by u8Var(0)
//   val sprite by metasprite { posX(myX); idx(myIdx); frame { tile(0,0,0) } }
//   scene("play") { frame { moveMetasprite(sprite) } }
// and asserts both the resolved MetaspriteIR AND the emitted MoveMetasprite
// ScriptOp carry the property names ("myX", "myIdx") with the unbound posY/rot
// fields preserved as null.
//
// Per user feedback `feedback_no_magic_strings.md`: the binders take
// `AssignableVar` and capture `.name` internally — no String parameters.
// =============================================================================

class MetaspriteBuilderVarRefFieldsTest {

    @Test
    fun `builder_posX_binder_propagates_to_metaspriteIR_and_moveMetasprite`() {
        val g =
            game("Test") {
                @Suppress("UNUSED_VARIABLE") var myX by i16Var(0)
                @Suppress("UNUSED_VARIABLE") var myIdx by u8Var(0)
                val sprite by metasprite {
                    posX(myX)
                    idx(myIdx)
                    frame { tile(0, 0, 0) }
                }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        // MetaspriteIR side: bound fields carry property names; unbound stay null.
        val ms = ir.metasprites.first { it.id == "sprite" }
        assertEquals("myX", ms.posXVarName)
        assertEquals("myIdx", ms.idxVarName)
        assertNull(ms.posYVarName, "posY was not bound — must stay null")
        assertNull(ms.rotVarName, "rot was not bound — must stay null")

        // MoveMetasprite side: helper looks up the resolved MetaspriteIR and
        // propagates the same names to the ScriptOp so the visitor (Plan 05)
        // can emit per-metasprite-namespaced references.
        val scene = ir.scenes.first { it.id == "play" }
        val moveOp =
            scene.frameOps.filterIsInstance<MoveMetasprite>().firstOrNull()
                ?: error("expected a MoveMetasprite op in scene 'play' frameOps")
        assertEquals("sprite", moveOp.metaspriteId)
        assertEquals("myX", moveOp.posXVar)
        assertEquals("myIdx", moveOp.idxVar)
        assertNull(moveOp.posYVar, "posY was not bound — MoveMetasprite must stay null")
        assertNull(moveOp.rotVar, "rot was not bound — MoveMetasprite must stay null")
    }

    @Test
    fun `metasprite_with_no_binders_leaves_all_var_ref_fields_null`() {
        // Phase 10 back-compat scenario: Metasprites.kt does NOT call the new binders.
        // Build a metasprite with no posX/posY/idx/rot binders and assert all four
        // pass through as null on both MetaspriteIR and the emitted MoveMetasprite.
        val g =
            game("Test") {
                val sprite by metasprite { frame { tile(0, 0, 0) } }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        val ms = ir.metasprites.first { it.id == "sprite" }
        assertNull(ms.posXVarName)
        assertNull(ms.posYVarName)
        assertNull(ms.idxVarName)
        assertNull(ms.rotVarName)

        val moveOp =
            ir.scenes
                .first { it.id == "play" }
                .frameOps
                .filterIsInstance<MoveMetasprite>()
                .firstOrNull() ?: error("expected a MoveMetasprite op in scene 'play' frameOps")
        assertNull(moveOp.posXVar)
        assertNull(moveOp.posYVar)
        assertNull(moveOp.idxVar)
        assertNull(moveOp.rotVar)
    }

    @Test
    fun `posY_and_rot_binders_propagate_independently`() {
        // Verify the other two binders work independently of posX/idx.
        val g =
            game("Test") {
                @Suppress("UNUSED_VARIABLE") var myY by i16Var(0)
                @Suppress("UNUSED_VARIABLE") var myRot by u8Var(0)
                val sprite by metasprite {
                    posY(myY)
                    rot(myRot)
                    frame { tile(0, 0, 0) }
                }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        val ms = ir.metasprites.first { it.id == "sprite" }
        assertNull(ms.posXVarName, "posX was not bound")
        assertEquals("myY", ms.posYVarName)
        assertNull(ms.idxVarName, "idx was not bound")
        assertEquals("myRot", ms.rotVarName)

        val moveOp =
            ir.scenes.first { it.id == "play" }.frameOps.filterIsInstance<MoveMetasprite>().first()
        assertNull(moveOp.posXVar)
        assertEquals("myY", moveOp.posYVar)
        assertNull(moveOp.idxVar)
        assertEquals("myRot", moveOp.rotVar)
    }

    @Test
    fun `all_four_binders_propagate_together`() {
        val g =
            game("Test") {
                @Suppress("UNUSED_VARIABLE") var px by i16Var(0)
                @Suppress("UNUSED_VARIABLE") var py by i16Var(0)
                @Suppress("UNUSED_VARIABLE") var ix by u8Var(0)
                @Suppress("UNUSED_VARIABLE") var rt by u8Var(0)
                val sprite by metasprite {
                    posX(px)
                    posY(py)
                    idx(ix)
                    rot(rt)
                    frame { tile(0, 0, 0) }
                }
                val playScene = scene("play") { frame { moveMetasprite(sprite) } }
                start = playScene
            }
        val ir = g.build()

        val ms = ir.metasprites.first { it.id == "sprite" }
        assertEquals("px", ms.posXVarName)
        assertEquals("py", ms.posYVarName)
        assertEquals("ix", ms.idxVarName)
        assertEquals("rt", ms.rotVarName)

        val moveOp =
            ir.scenes.first { it.id == "play" }.frameOps.filterIsInstance<MoveMetasprite>().first()
        assertEquals("px", moveOp.posXVar)
        assertEquals("py", moveOp.posYVar)
        assertEquals("ix", moveOp.idxVar)
        assertEquals("rt", moveOp.rotVar)
    }

    @Test
    fun `multiple_metasprites_capture_distinct_var_names() — substrate for CR-03 namespacing`() {
        // Stress preview: two metasprites with DIFFERENT var-name bindings must
        // each carry their own captured names — substrate for Plan 12 (stress ROM)
        // and Plan 05 (visitor per-metasprite namespacing).
        val g =
            game("Test") {
                @Suppress("UNUSED_VARIABLE") var ax by i16Var(0)
                @Suppress("UNUSED_VARIABLE") var bx by i16Var(0)
                val a by metasprite {
                    posX(ax)
                    frame { tile(0, 0, 0) }
                }
                val b by metasprite {
                    posX(bx)
                    frame { tile(0, 0, 0) }
                }
                val playScene =
                    scene("play") {
                        frame {
                            moveMetasprite(a)
                            moveMetasprite(b)
                        }
                    }
                start = playScene
            }
        val ir = g.build()

        assertEquals("ax", ir.metasprites.first { it.id == "a" }.posXVarName)
        assertEquals("bx", ir.metasprites.first { it.id == "b" }.posXVarName)

        val moves = ir.scenes.first { it.id == "play" }.frameOps.filterIsInstance<MoveMetasprite>()
        assertEquals(2, moves.size)
        assertEquals("ax", moves.first { it.metaspriteId == "a" }.posXVar)
        assertEquals("bx", moves.first { it.metaspriteId == "b" }.posXVar)
    }
}
