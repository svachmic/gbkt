/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.*

/**
 * Tests for platform annotation types (BankSlot, VRAMRange, OAMSlot).
 *
 * Verifies construction, nullable defaults, and copy semantics.
 */
class PlatformAnnotationsTest {

    @Test
    fun `BankSlot constructs with bank number`() {
        val slot = BankSlot(bank = 3)
        assertEquals(3, slot.bank)
        assertNull(slot.offset)
    }

    @Test
    fun `BankSlot constructs with bank number and offset`() {
        val slot = BankSlot(bank = 2, offset = 0x4000)
        assertEquals(2, slot.bank)
        assertEquals(0x4000, slot.offset)
    }

    @Test
    fun `BankSlot copy works correctly`() {
        val slot = BankSlot(bank = 1)
        val withOffset = slot.copy(offset = 512)
        assertEquals(1, withOffset.bank)
        assertEquals(512, withOffset.offset)
    }

    @Test
    fun `VRAMRange constructs with start and end tile indices`() {
        val range = VRAMRange(startTile = 0, endTile = 127)
        assertEquals(0, range.startTile)
        assertEquals(127, range.endTile)
    }

    @Test
    fun `VRAMRange copy works correctly`() {
        val range = VRAMRange(startTile = 0, endTile = 63)
        val shifted = range.copy(startTile = 64, endTile = 127)
        assertEquals(64, shifted.startTile)
        assertEquals(127, shifted.endTile)
    }

    @Test
    fun `OAMSlot constructs with slot index`() {
        val slot = OAMSlot(slot = 0)
        assertEquals(0, slot.slot)
    }

    @Test
    fun `OAMSlot copy works correctly`() {
        val slot = OAMSlot(slot = 0)
        val next = slot.copy(slot = 1)
        assertEquals(1, next.slot)
    }

    @Test
    fun `ActorIR implements PlatformAnnotatable with null defaults`() {
        val actor: PlatformAnnotatable = ActorIR(id = "player", position = PositionDef(0, 0))
        assertNull(actor.bankSlot)
        assertNull(actor.vramRange)
        assertNull(actor.oamSlot)
    }

    @Test
    fun `SceneIR implements PlatformAnnotatable with null defaults`() {
        val scene: PlatformAnnotatable = SceneIR(id = "main")
        assertNull(scene.bankSlot)
        assertNull(scene.vramRange)
        assertNull(scene.oamSlot)
    }

    @Test
    fun `SystemIR implements PlatformAnnotatable with null defaults`() {
        val systems: List<PlatformAnnotatable> =
            listOf(
                DialogSystem(id = "d"),
                SoundSystem(id = "s"),
                SaveSystem(id = "sv"),
                ExplorationSystem(id = "e"),
                CameraSystem(id = "c"),
                GenericSystem(id = "g"),
            )
        for (sys in systems) {
            assertNull(sys.bankSlot)
            assertNull(sys.vramRange)
            assertNull(sys.oamSlot)
        }
    }

    @Test
    fun `ActorIR annotations can be set via copy`() {
        val actor = ActorIR(id = "player", position = PositionDef(0, 0))
        val annotated =
            actor.copy(
                bankSlot = BankSlot(bank = 1),
                vramRange = VRAMRange(0, 15),
                oamSlot = OAMSlot(0),
            )
        assertEquals(1, annotated.bankSlot?.bank)
        assertEquals(0, annotated.vramRange?.startTile)
        assertEquals(0, annotated.oamSlot?.slot)
    }

    @Test
    fun `SceneIR annotations can be set via copy`() {
        val scene = SceneIR(id = "gameplay")
        val annotated = scene.copy(bankSlot = BankSlot(bank = 2), vramRange = VRAMRange(128, 255))
        assertEquals(2, annotated.bankSlot?.bank)
        assertEquals(128, annotated.vramRange?.startTile)
        assertNull(annotated.oamSlot)
    }

    @Test
    fun `all PlatformAnnotatable types accept full annotation set`() {
        val bankSlot = BankSlot(bank = 3)
        val vramRange = VRAMRange(0, 63)
        val oamSlot = OAMSlot(2)

        // ActorIR
        val actor =
            ActorIR(
                id = "a",
                position = PositionDef(0, 0),
                bankSlot = bankSlot,
                vramRange = vramRange,
                oamSlot = oamSlot,
            )
        assertEquals(bankSlot, actor.bankSlot)
        assertEquals(vramRange, actor.vramRange)
        assertEquals(oamSlot, actor.oamSlot)

        // SceneIR
        val scene = SceneIR(id = "s", bankSlot = bankSlot, vramRange = vramRange, oamSlot = oamSlot)
        assertEquals(bankSlot, scene.bankSlot)
        assertEquals(vramRange, scene.vramRange)
        assertEquals(oamSlot, scene.oamSlot)
    }
}
