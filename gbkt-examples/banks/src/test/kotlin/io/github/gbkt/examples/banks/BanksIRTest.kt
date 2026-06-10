/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.banks

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Banks DSL definition.
 *
 * Tier-1 oracle for IR structure (Plan 11-06). JVM-tier, no ROM. Catches regressions in the DSL or
 * in IR construction (e.g., a future refactor that drops scenes silently).
 *
 * The 8 tests below lock the substrate contract from Plan 11-05: 3 scenes (title / play / pause),
 * startScene=title, 1 zone (playZone), 1 u8 variable (saveFlag), 1 SaveSystem in `ir.systems`.
 *
 * The `private val ir = banks.build()` field locks the test's dependency on the `banks` symbol so
 * the compile contract is enforced.
 */
class BanksIRTest {
    private val ir = banks.build()

    @Test
    fun `has 3 scenes`() {
        assertEquals(3, ir.scenes.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `scenes include title play pause`() {
        val ids = ir.scenes.map { it.id }.toSet()
        assertTrue(ids.contains("title"))
        assertTrue(ids.contains("play"))
        assertTrue(ids.contains("pause"))
    }

    @Test
    fun `has 1 variable`() {
        assertEquals(1, ir.variables.size)
    }

    @Test
    fun `saveFlag is U8`() {
        assertTrue(ir.variables.any { it.name == "saveFlag" && it.type == VarType.U8 })
    }

    @Test
    fun `has zone definitions`() {
        assertTrue(ir.zones.isNotEmpty())
    }

    @Test
    fun `has playZone zone`() {
        assertTrue(ir.zones.any { it.id == "playZone" })
    }

    @Test
    fun `has save system`() {
        assertTrue(ir.systems.any { it is SaveSystem })
    }

    // Wave 4 typed assertions (Plan 13.1-08)

    @Test
    fun `config cartridge is MBC5_RAM_BATTERY`() {
        assertEquals(Cartridge.MBC5_RAM_BATTERY, ir.config.cartridge)
    }

    @Test
    fun `config ramBanks is 2`() {
        assertEquals(2, ir.config.ramBanks)
    }

    @Test
    fun `config romBanks is null at build time`() {
        // romBanks is null in the DSL (D-05 derive sentinel) — backend derives at codegen time
        assertNull(ir.config.romBanks)
    }

    @Test
    fun `save system id is saves`() {
        val saveSystem = ir.systems.filterIsInstance<SaveSystem>().firstOrNull()
        assertEquals("saves", saveSystem?.id)
    }
}
