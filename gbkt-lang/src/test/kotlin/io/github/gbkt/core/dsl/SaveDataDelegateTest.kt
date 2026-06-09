/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.TriggerSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// SAVE DATA DELEGATE TESTS  (Wave 0 RED — Plan 13.1-01 Task 3)
// Verifies val saves by saveData { } infers the id from the property name (D-08),
// that triggerSystem(saves) resolves the typed ref and emits TriggerSystem (D-10),
// and that SaveDataRef.systemId equals the inferred name (Req #16).
//
// These tests reference:
//   - the `by saveData { }` delegate factory (not yet in SystemBuilders.kt)
//   - SaveDataRef (not yet in SystemBuilders.kt)
//   - SystemRef interface (not yet in SystemBuilders.kt)
//   - triggerSystem(ref: SystemRef) overload in ScriptBuilder (not yet present)
//
// They MUST fail to compile until Plan 13.1-04 adds all four symbols.
// =============================================================================

class SaveDataDelegateTest {

    // =========================================================================
    // Behavior 1: val saves by saveData { } infers id "saves" for SaveSystem (D-08)
    // =========================================================================

    @Test
    fun `val saves by saveData registers SaveSystem with id saves`() {
        val ir = game("Test") {
            @Suppress("UNUSED_VARIABLE") val saves by saveData { slots(2) }
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val saveSystem = ir.systems.filterIsInstance<SaveSystem>().firstOrNull()
        assertEquals(1, ir.systems.filterIsInstance<SaveSystem>().size,
            "Exactly one SaveSystem must be registered")
        assertEquals("saves", saveSystem?.id,
            "SaveSystem id must be inferred from property name, not a string param (Project Rule #1)")
    }

    // =========================================================================
    // Behavior 2: saveData returns a SaveDataRef whose systemId == "saves" (D-10)
    // =========================================================================

    @Test
    fun `saveData delegate returns a SaveDataRef with systemId saves`() {
        var capturedRef: SaveDataRef? = null
        game("Test") {
            val saves by saveData { slots(2) }
            @Suppress("UNUSED_VARIABLE")
            capturedRef = saves  // capturing the ref — suppressed because ref is only used here
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val ref = capturedRef
        assertEquals("saves", ref?.systemId,
            "SaveDataRef.systemId must equal the inferred property name")
    }

    // =========================================================================
    // Behavior 3: SaveDataRef implements SystemRef (D-10 typed ref contract)
    // =========================================================================

    @Test
    fun `SaveDataRef implements SystemRef`() {
        var capturedRef: SaveDataRef? = null
        game("Test") {
            val saves by saveData { slots(2) }
            @Suppress("UNUSED_VARIABLE")
            capturedRef = saves
            val sScene = scene("s") {}
            start = sScene
        }.build()

        assertIs<SystemRef>(capturedRef,
            "SaveDataRef must implement SystemRef for typed triggerSystem overload")
    }

    // =========================================================================
    // Behavior 4: triggerSystem(saves) emits TriggerSystem with systemId "saves" (D-10)
    // =========================================================================

    @Test
    fun `triggerSystem with SaveDataRef emits TriggerSystem with systemId saves`() {
        val ir = game("Test") {
            @Suppress("UNUSED_VARIABLE") val saves by saveData { slots(2) }
            val sScene = scene("s") {
                frame {
                    whenever(buttons.select.pressed) {
                        triggerSystem(saves)
                    }
                }
            }
            start = sScene
        }.build()

        val scene = ir.scenes.first()
        val frameOps = scene.frameOps
        // Unwrap the IfOp from whenever() to reach the TriggerSystem inside
        val triggerOps = frameOps.filterIsInstance<io.github.gbkt.core.ir.IfOp>()
            .flatMap { it.then }
            .filterIsInstance<TriggerSystem>()
        assertEquals(1, triggerOps.size,
            "Exactly one TriggerSystem must be emitted by triggerSystem(saves)")
        assertEquals("saves", triggerOps.first().systemId,
            "TriggerSystem.systemId must equal the SaveDataRef.systemId (D-10 ref→id resolution)")
    }

    // =========================================================================
    // Behavior 5: Multiple saveData delegates each infer their own id (D-08)
    // =========================================================================

    @Test
    fun `two saveData delegates infer distinct ids from their property names`() {
        val ir = game("Test") {
            @Suppress("UNUSED_VARIABLE") val saves by saveData { slots(2) }
            @Suppress("UNUSED_VARIABLE") val checkpoints by saveData { slots(10) }
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val saveSystems = ir.systems.filterIsInstance<SaveSystem>()
        assertEquals(2, saveSystems.size)
        val ids = saveSystems.map { it.id }.toSet()
        assertEquals(setOf("saves", "checkpoints"), ids,
            "Each saveData delegate must infer its id from the property name")
    }
}
