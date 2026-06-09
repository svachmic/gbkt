/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.SetPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// =============================================================================
// SEED-007 / D-extra REGRESSION TEST
//
// Verifies that `GameBuilder` injects sequential GBC sprite-palette slot indices
// into `SetPalette` ops when multiple actors carry auto-slotted palette overrides.
//
// Bug site (pre-fix, GameBuilder.kt:710-716):
//   val actorPaletteOps =
//       actors.mapNotNull { actor ->
//           actor.palette?.let { pal ->
//               val slot = if (pal.slot >= 0) pal.slot else 0   // ← BUG
//               SetPalette(pal.name, slot, PaletteType.SPRITE)
//           }
//       }
//
// The `else 0` collapses every auto-slotted actor palette into slot 0 — only the
// last actor's palette is actually loaded onto GBC hardware. Sibling fix already
// merged for `SceneBuilder.palette()` in commit 2e8fb256 (Phase 10 Plan 16).
//
// Fix shape mirrors SceneBuilder, adapted to a running counter (not
// `paletteOps.size`, because `mapNotNull` skips actors without palettes):
//
//   var actorPaletteAutoSlot = 0
//   val actorPaletteOps = actors.mapNotNull { actor ->
//       actor.palette?.let { pal ->
//           val slot = if (pal.slot >= 0) pal.slot else actorPaletteAutoSlot++
//           SetPalette(pal.name, slot, PaletteType.SPRITE)
//       }
//   }
//
// Companion: SceneBuilder analogue test at
//   gbkt-backend-gbdk/.../pipeline/SpritePaletteSlotEmissionTest.kt
// =============================================================================

class Seed007GameBuilderPaletteSlotTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a SPRITE palette with auto-assigned slot (-1). */
    private fun autoSpritePalette(name: String): GBCPalette =
        GBCPalette(
            name = name,
            colors =
                listOf(
                    Color.rgb555(31, 31, 31),
                    Color.rgb555(20, 20, 20),
                    Color.rgb555(10, 10, 10),
                    Color.rgb555(0, 0, 0),
                ),
            slot = -1,
            type = PaletteType.SPRITE,
        )

    /** Build a SPRITE palette with an explicit slot. */
    private fun explicitSpritePalette(name: String, slot: Int): GBCPalette =
        GBCPalette(
            name = name,
            colors =
                listOf(
                    Color.rgb555(31, 31, 31),
                    Color.rgb555(20, 20, 20),
                    Color.rgb555(10, 10, 10),
                    Color.rgb555(0, 0, 0),
                ),
            slot = slot,
            type = PaletteType.SPRITE,
        )

    /** Build a BACKGROUND palette with auto-assigned slot (-1). */
    private fun autoBgPalette(name: String): GBCPalette =
        GBCPalette(
            name = name,
            colors =
                listOf(
                    Color.rgb555(31, 31, 31),
                    Color.rgb555(20, 20, 20),
                    Color.rgb555(10, 10, 10),
                    Color.rgb555(0, 0, 0),
                ),
            slot = -1,
            type = PaletteType.BACKGROUND,
        )

    // -------------------------------------------------------------------------
    // BEHAVIOR 1: four sequential auto-slot actor palettes get slots 0, 1, 2, 3
    // -------------------------------------------------------------------------

    @Test
    fun sequential_actors_with_auto_slot_get_sequential_slot_indices() {
        val ir =
            game("Seed007Auto") {
                    val a1 by actor {
                        position(10, 10)
                        palette(autoSpritePalette("p1"))
                    }
                    val a2 by actor {
                        position(20, 10)
                        palette(autoSpritePalette("p2"))
                    }
                    val a3 by actor {
                        position(30, 10)
                        palette(autoSpritePalette("p3"))
                    }
                    val a4 by actor {
                        position(40, 10)
                        palette(autoSpritePalette("p4"))
                    }
                    @Suppress("UNUSED_VARIABLE") val unused = listOf(a1, a2, a3, a4)

                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.single { it.id == "main" }
        val setPals = scene.enterOps.filterIsInstance<SetPalette>()

        // Palette names must be ordered p1..p4 (actor declaration order)
        assertEquals(
            listOf("p1", "p2", "p3", "p4"),
            setPals.map { it.paletteName },
            "Expected SetPalette ops for p1..p4 in actor declaration order, got ${setPals.map { it.paletteName }}",
        )

        // Slots must be sequential 0, 1, 2, 3 — the bug collapses them all to 0
        assertEquals(
            listOf(0, 1, 2, 3),
            setPals.map { it.slot },
            "Expected sequential slot indices [0, 1, 2, 3] for auto-slotted actor palettes — " +
                "got ${setPals.map { it.slot }}. " +
                "If got [0, 0, 0, 0], the SEED-007 / D-extra bug in GameBuilder.kt:713 is still " +
                "present (else 0 — should be running counter actorPaletteAutoSlot++).",
        )
    }

    // -------------------------------------------------------------------------
    // BEHAVIOR 2: an explicit-slot actor between two auto-slot actors must NOT
    //             bump the auto counter (slot 0, slot 5, slot 1 — not slot 2)
    // -------------------------------------------------------------------------

    @Test
    fun actor_with_explicit_slot_does_not_consume_auto_slot_counter() {
        val ir =
            game("Seed007Mixed") {
                    val a1 by actor {
                        position(10, 10)
                        palette(autoSpritePalette("auto1"))
                    }
                    val a2 by actor {
                        position(20, 10)
                        palette(explicitSpritePalette("explicit5", slot = 5))
                    }
                    val a3 by actor {
                        position(30, 10)
                        palette(autoSpritePalette("auto2"))
                    }
                    @Suppress("UNUSED_VARIABLE") val unused = listOf(a1, a2, a3)

                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.single { it.id == "main" }
        val setPals = scene.enterOps.filterIsInstance<SetPalette>()

        assertEquals(
            listOf("auto1", "explicit5", "auto2"),
            setPals.map { it.paletteName },
            "Expected SetPalette ops for auto1, explicit5, auto2 in actor declaration order",
        )

        // auto1 → slot 0, explicit5 → slot 5, auto2 → slot 1.
        // The explicit-slot actor must NOT consume an auto counter increment, so auto2
        // gets slot 1 (NOT slot 2). The bug shape would give [0, 5, 0]; a naïve
        // mapIndexedNotNull fix would give [0, 5, 2].
        assertEquals(
            listOf(0, 5, 1),
            setPals.map { it.slot },
            "Expected slots [0, 5, 1] — the explicit-slot palette must not consume an auto-slot " +
                "counter increment, so the second auto-slot palette gets slot 1, not 2. Got ${setPals.map { it.slot }}.",
        )
    }

    // =========================================================================
    // D-09: SceneBuilder.palette(p, slot = N) — explicit-slot overload
    // =========================================================================

    @Test
    fun `scene palette with explicit slot emits SetPalette with that slot ignoring palette declared slot`() {
        // gray palette has slot = -1 (auto-assign), but we pass slot = 2 at the call site.
        // The scene's SetPalette op must carry slot 2, NOT the auto-assigned 0.
        val gray = autoBgPalette("gray")

        val ir =
            game("D09ExplicitSlot") {
                    val mainScene =
                        scene("main") {
                            palette(gray, slot = 2)
                            enter {}
                        }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.single { it.id == "main" }
        val setPals = scene.enterOps.filterIsInstance<SetPalette>()

        assertEquals(1, setPals.size)
        assertEquals("gray", setPals[0].paletteName)
        assertEquals(
            2,
            setPals[0].slot,
            "Expected explicit slot 2 in SetPalette, got ${setPals[0].slot}",
        )
    }

    // =========================================================================
    // D-10: auto-increment stays the default — palette(p) unchanged
    // =========================================================================

    @Test
    fun `scene auto_increment palette default is preserved sequential 0_1_2_3`() {
        // Regression guard: palette(p) with no slot must still assign 0,1,2,3 sequentially.
        val p0 = autoBgPalette("p0")
        val p1 = autoBgPalette("p1")
        val p2 = autoBgPalette("p2")
        val p3 = autoBgPalette("p3")

        val ir =
            game("D10AutoIncrement") {
                    val mainScene =
                        scene("main") {
                            palette(p0)
                            palette(p1)
                            palette(p2)
                            palette(p3)
                            enter {}
                        }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.single { it.id == "main" }
        val setPals = scene.enterOps.filterIsInstance<SetPalette>()

        assertEquals(listOf("p0", "p1", "p2", "p3"), setPals.map { it.paletteName })
        assertEquals(
            listOf(0, 1, 2, 3),
            setPals.map { it.slot },
            "Auto-increment must assign sequential slots 0,1,2,3 — got ${setPals.map { it.slot }}",
        )
    }

    // =========================================================================
    // D-11: range 0..7 guard
    // =========================================================================

    @Test
    fun `scene palette with slot 8 throws at build time naming bad slot`() {
        // slot = 8 is out of range 0..7 — must fail loudly with the slot number in message
        val p = autoBgPalette("outOfRange")

        val ex =
            assertFailsWith<IllegalArgumentException> {
                game("D11RangeCheck") {
                        val mainScene =
                            scene("main") {
                                palette(p, slot = 8)
                                enter {}
                            }
                        start = mainScene
                    }
                    .build()
            }
        assertTrue(
            ex.message?.contains("8") == true,
            "Exception message must name the offending slot (8), got: '${ex.message}'",
        )
    }

    // =========================================================================
    // D-11: duplicate-slot-within-scene guard
    // =========================================================================

    @Test
    fun `two palettes claiming same slot within one scene throws at build time`() {
        val a = autoBgPalette("alpha")
        val b = autoBgPalette("beta")

        val ex =
            assertFailsWith<IllegalArgumentException> {
                game("D11DuplicateSlot") {
                        val mainScene =
                            scene("main") {
                                palette(a, slot = 3)
                                palette(b, slot = 3)
                                enter {}
                            }
                        start = mainScene
                    }
                    .build()
            }
        assertTrue(
            ex.message?.contains("3") == true,
            "Exception message must name the duplicate slot (3), got: '${ex.message}'",
        )
    }

    @Test
    fun `same slot in different scenes does not throw`() {
        // Slot uniqueness is per-scene, not global. slot=3 in sceneX and slot=3 in sceneY is valid.
        val a = autoBgPalette("alpha")
        val b = autoBgPalette("beta")

        // Must NOT throw
        val ir =
            game("D11CrossSceneSlot") {
                    val sceneXScene =
                        scene("sceneX") {
                            palette(a, slot = 3)
                            enter {}
                        }
                    scene("sceneY") {
                        palette(b, slot = 3)
                        enter {}
                    }
                    start = sceneXScene
                }
                .build()

        val sceneX = ir.scenes.single { it.id == "sceneX" }
        val sceneY = ir.scenes.single { it.id == "sceneY" }
        assertEquals(3, sceneX.enterOps.filterIsInstance<SetPalette>().single().slot)
        assertEquals(3, sceneY.enterOps.filterIsInstance<SetPalette>().single().slot)
    }
}
