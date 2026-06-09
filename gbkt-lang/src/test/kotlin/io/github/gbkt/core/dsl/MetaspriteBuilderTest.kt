/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.MoveMetasprite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MetaspriteBuilderTest {

    // -------------------------------------------------------------------------
    // Behavior 1: Single frame with one tile is registered correctly
    // -------------------------------------------------------------------------

    @Test
    fun `val foo by metasprite with one frame registers MetaspriteIR with id foo and 1 frame`() {
        val g =
            game("Test") {
                val foo by metasprite { frame { tile(0, 0, 0) } }
                val sScene = scene("s") {}
                start = sScene
            }
        val ir = g.build()
        assertEquals(1, ir.metasprites.size)
        val ms = ir.metasprites.first()
        assertEquals("foo", ms.id)
        assertEquals(1, ms.frames.size)
        assertEquals(1, ms.frames.first().tiles.size)
    }

    // -------------------------------------------------------------------------
    // Behavior 2: Empty metasprite (no frames) → builder-time error
    // -------------------------------------------------------------------------

    @Test
    fun `empty metasprite with no frames throws at build time`() {
        assertFailsWith<IllegalArgumentException> {
            game("Test") {
                    @Suppress("UNUSED_VARIABLE") val foo by metasprite { /* no frames */ }
                    val sScene = scene("s") {}
                    start = sScene
                }
                .build()
        }
    }

    // -------------------------------------------------------------------------
    // Behavior 3: Frame with no tiles → builder-time error
    // -------------------------------------------------------------------------

    @Test
    fun `frame with no tiles throws at build time`() {
        assertFailsWith<IllegalArgumentException> {
            game("Test") {
                    @Suppress("UNUSED_VARIABLE") val foo by metasprite { frame { /* no tiles */ } }
                    val sScene = scene("s") {}
                    start = sScene
                }
                .build()
        }
    }

    // -------------------------------------------------------------------------
    // Behavior 4: Negative tileId → builder-time error
    // -------------------------------------------------------------------------

    @Test
    fun `tile with negative tileId throws at build time`() {
        assertFailsWith<IllegalArgumentException> {
            game("Test") {
                    @Suppress("UNUSED_VARIABLE") val foo by metasprite { frame { tile(0, 0, -1) } }
                    val sScene = scene("s") {}
                    start = sScene
                }
                .build()
        }
    }

    // -------------------------------------------------------------------------
    // Behavior 5: metasprite called outside game block → clear error
    // -------------------------------------------------------------------------

    @Test
    fun `metasprite called outside game block raises error`() {
        val delegate = metasprite { frame { tile(0, 0, 0) } }
        assertFailsWith<IllegalStateException> {
            // Simulate property delegation outside game block by directly calling provideDelegate
            // with no GameBuilderContext active
            val prop =
                object : kotlin.reflect.KProperty<MetaspriteRef> {
                    override val name: String = "foo"
                    override val annotations: List<Annotation> = emptyList()

                    override fun call(vararg args: Any?): MetaspriteRef = error("not needed")

                    override fun callBy(args: Map<kotlin.reflect.KParameter, Any?>): MetaspriteRef =
                        error("not needed")

                    override val getter: kotlin.reflect.KProperty.Getter<MetaspriteRef>
                        get() = error("not needed")

                    override val isAbstract: Boolean = false
                    override val isConst: Boolean = false
                    override val isFinal: Boolean = true
                    override val isLateinit: Boolean = false
                    override val isOpen: Boolean = false
                    override val isSuspend: Boolean = false
                    override val parameters: List<kotlin.reflect.KParameter> = emptyList()
                    override val returnType: kotlin.reflect.KType
                        get() = error("not needed")

                    override val typeParameters: List<kotlin.reflect.KTypeParameter> = emptyList()
                    override val visibility: kotlin.reflect.KVisibility? = null
                }
            delegate.provideDelegate(null, prop)
        }
    }

    // -------------------------------------------------------------------------
    // Behavior 6: Multiple tiles in a frame preserve relX/relY/baseId order
    // -------------------------------------------------------------------------

    @Test
    fun `frame with two tiles preserves relX relY baseId for each tile`() {
        val g =
            game("Test") {
                @Suppress("UNUSED_VARIABLE")
                val a by metasprite {
                    frame {
                        tile(0, 0, 0)
                        tile(8, 0, 1)
                    }
                }
                val sScene = scene("s") {}
                start = sScene
            }
        val ir = g.build()
        val frame = ir.metasprites.first().frames.first()
        assertEquals(2, frame.tiles.size)
        assertEquals(MetaspriteTile(relX = 0, relY = 0, tileId = 0), frame.tiles[0])
        assertEquals(MetaspriteTile(relX = 8, relY = 0, tileId = 1), frame.tiles[1])
    }

    // -------------------------------------------------------------------------
    // Behavior 7: MetaspriteRef.flipX returns ActorPropertyRef("<id>", "flipX")
    // -------------------------------------------------------------------------

    @Test
    fun `MetaspriteRef flipX returns ActorPropertyRef with id and flipX`() {
        val ref = MetaspriteRef("elephant")
        assertEquals(ActorPropertyRef("elephant", "flipX"), ref.flipX)
    }

    @Test
    fun `MetaspriteRef flipY returns ActorPropertyRef with id and flipY`() {
        val ref = MetaspriteRef("elephant")
        assertEquals(ActorPropertyRef("elephant", "flipY"), ref.flipY)
    }

    @Test
    fun `MetaspriteRef subPalette returns ActorPropertyRef with id and subPalette`() {
        val ref = MetaspriteRef("elephant")
        assertEquals(ActorPropertyRef("elephant", "subPalette"), ref.subPalette)
    }

    // -------------------------------------------------------------------------
    // Task 2 (Plan 04): moveMetasprite DSL function
    // -------------------------------------------------------------------------

    @Test
    fun `moveMetasprite in frame block emits MoveMetasprite op with metaspriteId`() {
        val g =
            game("Test") {
                val foo by metasprite { frame { tile(0, 0, 0) } }
                val playScene = scene("play") { frame { moveMetasprite(foo) } }
                start = playScene
            }
        val ir = g.build()
        val scene = ir.scenes.first { it.id == "play" }
        val frameOps = scene.frameOps
        assertTrue(
            frameOps.any { it is MoveMetasprite && it.metaspriteId == "foo" },
            "Expected MoveMetasprite(\"foo\") in frame ops but got: $frameOps",
        )
    }
}
