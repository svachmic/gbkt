/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

import io.github.gbkt.core.ir.BankSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PassContextTest {

    @Test
    fun `withDiagnostics appends to existing diagnostics`() {
        val existing = Diagnostic("D1", Severity.INFO, "first")
        val ctx = baseContext().copy(diagnostics = listOf(existing))
        val newDiags =
            listOf(
                Diagnostic("D2", Severity.WARNING, "second"),
                Diagnostic("D3", Severity.ERROR, "third"),
            )

        val updated = ctx.withDiagnostics(newDiags)

        assertEquals(3, updated.diagnostics.size)
        assertEquals("D1", updated.diagnostics[0].id)
        assertEquals("D2", updated.diagnostics[1].id)
        assertEquals("D3", updated.diagnostics[2].id)
    }

    @Test
    fun `withDiagnostics on empty context produces list with only new diagnostics`() {
        val ctx = baseContext()
        val newDiag = Diagnostic("D1", Severity.INFO, "hello")

        val updated = ctx.withDiagnostics(listOf(newDiag))

        assertEquals(1, updated.diagnostics.size)
        assertEquals("D1", updated.diagnostics.first().id)
    }

    @Test
    fun `withBankAssignment adds entry without removing existing`() {
        val ctx = baseContext().withBankAssignment("scene-a", BankSlot(bank = 1))

        val updated = ctx.withBankAssignment("scene-b", BankSlot(bank = 2))

        assertEquals(2, updated.bankAssignments.size)
        assertEquals(BankSlot(bank = 1), updated.bankAssignments["scene-a"])
        assertEquals(BankSlot(bank = 2), updated.bankAssignments["scene-b"])
    }

    @Test
    fun `withBankAssignment replaces existing entry for same id`() {
        val ctx = baseContext().withBankAssignment("scene-a", BankSlot(bank = 1))

        val updated = ctx.withBankAssignment("scene-a", BankSlot(bank = 3))

        assertEquals(1, updated.bankAssignments.size)
        assertEquals(BankSlot(bank = 3), updated.bankAssignments["scene-a"])
    }

    @Test
    fun `context is immutable — original unchanged after withDiagnostics`() {
        val original = baseContext()

        val updated = original.withDiagnostics(listOf(Diagnostic("D1", Severity.INFO, "test")))

        assertTrue(original.diagnostics.isEmpty())
        assertNotSame(original, updated)
    }

    @Test
    fun `context is immutable — original unchanged after withBankAssignment`() {
        val original = baseContext()

        val updated = original.withBankAssignment("scene-x", BankSlot(bank = 1))

        assertTrue(original.bankAssignments.isEmpty())
        assertNotSame(original, updated)
    }

    @Test
    fun `withDiagnostics does not modify original list`() {
        val initialDiag = Diagnostic("D1", Severity.INFO, "initial")
        val original = baseContext().withDiagnostics(listOf(initialDiag))
        val originalSize = original.diagnostics.size

        original.withDiagnostics(listOf(Diagnostic("D2", Severity.WARNING, "new")))

        // Original context's diagnostics list must be unchanged
        assertEquals(originalSize, original.diagnostics.size)
    }
}
