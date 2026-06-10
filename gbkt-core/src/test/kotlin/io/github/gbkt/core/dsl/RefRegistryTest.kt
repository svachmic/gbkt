/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.RefKind
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RefRegistryTest {

    @Test
    fun `registering and resolving a scene ref succeeds`() {
        val registry = RefRegistry()
        registry.register("gameplay", RefKind.SCENE)
        registry.ref("gameplay", RefKind.SCENE)
        // Should not throw
        registry.resolveAll()
    }

    @Test
    fun `resolving unresolved ref throws DSLValidationError`() {
        val registry = RefRegistry()
        registry.ref("nonexistent", RefKind.SCENE)
        assertFailsWith<DSLValidationError> { registry.resolveAll() }
    }

    @Test
    fun `resolving unresolved ref includes Unresolved reference message`() {
        val registry = RefRegistry()
        registry.ref("nonexistent", RefKind.SCENE)
        val exception = assertFailsWith<DSLValidationError> { registry.resolveAll() }
        assertTrue(exception.message!!.contains("Unresolved reference"))
    }

    @Test
    fun `resolving unresolved ref includes Did you mean suggestion when close match exists`() {
        val registry = RefRegistry()
        registry.register("gameplay", RefKind.SCENE)
        registry.ref("gamepaly", RefKind.SCENE)

        val exception = assertFailsWith<DSLValidationError> { registry.resolveAll() }
        // Should include the suggestion
        assertTrue(
            exception.message!!.contains("gameplay"),
            "Expected suggestion 'gameplay' in: ${exception.message}",
        )
    }

    @Test
    fun `resolving unresolved ref with no close match does not include Did you mean`() {
        val registry = RefRegistry()
        registry.register("totally_different_id", RefKind.SCENE)
        registry.ref("xyz", RefKind.SCENE)

        val exception = assertFailsWith<DSLValidationError> { registry.resolveAll() }
        // No close match exists — no "Did you mean" phrase
        assertTrue(
            !exception.message!!.contains("Did you mean"),
            "Should not contain suggestion: ${exception.message}",
        )
    }

    @Test
    fun `ref resolution is case sensitive`() {
        val registry = RefRegistry()
        registry.register("gameplay", RefKind.SCENE)
        registry.ref("Gameplay", RefKind.SCENE)

        // "Gameplay" != "gameplay" — should throw
        assertFailsWith<DSLValidationError> { registry.resolveAll() }
    }

    @Test
    fun `registering duplicate ID in same kind throws`() {
        val registry = RefRegistry()
        registry.register("gameplay", RefKind.SCENE)
        assertFailsWith<DSLValidationError> { registry.register("gameplay", RefKind.SCENE) }
    }

    @Test
    fun `registering same ID in different kinds does not throw`() {
        val registry = RefRegistry()
        // Same ID can be used for different kinds (e.g., actor "player" and variable "player")
        registry.register("player", RefKind.ACTOR)
        registry.register("player", RefKind.VARIABLE)
        // Should not throw
    }

    @Test
    fun `multiple ref kinds are tracked separately in same registry`() {
        val registry = RefRegistry()
        registry.register("main", RefKind.SCENE)
        registry.register("hero", RefKind.ACTOR)
        registry.register("camera", RefKind.SYSTEM)

        registry.ref("main", RefKind.SCENE)
        registry.ref("hero", RefKind.ACTOR)
        registry.ref("camera", RefKind.SYSTEM)

        // All refs resolve — should not throw
        registry.resolveAll()
    }

    @Test
    fun `error message format matches compiler style with quoted ref`() {
        val registry = RefRegistry()
        registry.ref("missingScene", RefKind.SCENE)

        val exception = assertFailsWith<DSLValidationError> { registry.resolveAll() }
        // Format: error: Unresolved reference "X". Did you mean...
        assertTrue(
            exception.message!!.contains("\"missingScene\""),
            "Expected quoted ref in message: ${exception.message}",
        )
    }

    @Test
    fun `Did you mean suggestion includes close match in quotes`() {
        val registry = RefRegistry()
        registry.register("gameplay", RefKind.SCENE)
        registry.ref("gamepaly", RefKind.SCENE)

        val exception = assertFailsWith<DSLValidationError> { registry.resolveAll() }
        // Should contain the suggestion in quotes
        assertTrue(
            exception.message!!.contains("'gameplay'"),
            "Expected quoted suggestion in message: ${exception.message}",
        )
    }
}
