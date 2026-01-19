/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for GbktDslVisitor constants and utilities.
 *
 * Note: Tests that require actual Kotlin PSI parsing need the IntelliJ Platform test framework and
 * are in GbktDslVisitorIntegrationTest.
 */
@Suppress("TooManyFunctions")
class GbktDslVisitorTest {

    @Test
    fun `DSL_FUNCTIONS contains gbGame`() {
        assertTrue("gbGame should be in DSL functions", "gbGame" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains scene`() {
        assertTrue("scene should be in DSL functions", "scene" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains entity`() {
        assertTrue("entity should be in DSL functions", "entity" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains variable types`() {
        assertTrue("u8Var should be in DSL functions", "u8Var" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("u16Var should be in DSL functions", "u16Var" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("i8Var should be in DSL functions", "i8Var" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("i16Var should be in DSL functions", "i16Var" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains array types`() {
        assertTrue("u8Array should be in DSL functions", "u8Array" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue(
            "u16Array should be in DSL functions",
            "u16Array" in GbktDslVisitor.DSL_FUNCTIONS,
        )
    }

    @Test
    fun `DSL_FUNCTIONS contains scene lifecycle`() {
        assertTrue("enter should be in DSL functions", "enter" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("exit should be in DSL functions", "exit" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("every should be in DSL functions", "every" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains entity configuration`() {
        assertTrue(
            "position should be in DSL functions",
            "position" in GbktDslVisitor.DSL_FUNCTIONS,
        )
        assertTrue(
            "velocity should be in DSL functions",
            "velocity" in GbktDslVisitor.DSL_FUNCTIONS,
        )
        assertTrue("sprite should be in DSL functions", "sprite" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("hitbox should be in DSL functions", "hitbox" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains combat keywords`() {
        assertTrue("combat should be in DSL functions", "combat" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("maxHp should be in DSL functions", "maxHp" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue(
            "attackPower should be in DSL functions",
            "attackPower" in GbktDslVisitor.DSL_FUNCTIONS,
        )
        assertTrue("defense should be in DSL functions", "defense" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains state machine keywords`() {
        assertTrue("states should be in DSL functions", "states" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("state should be in DSL functions", "state" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("tick should be in DSL functions", "tick" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("on should be in DSL functions", "on" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("goto should be in DSL functions", "goto" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS contains control flow`() {
        assertTrue(
            "whenever should be in DSL functions",
            "whenever" in GbktDslVisitor.DSL_FUNCTIONS,
        )
        assertTrue("branch should be in DSL functions", "branch" in GbktDslVisitor.DSL_FUNCTIONS)
        assertTrue("repeat should be in DSL functions", "repeat" in GbktDslVisitor.DSL_FUNCTIONS)
    }

    @Test
    fun `DSL_FUNCTIONS does not contain arbitrary functions`() {
        assertFalse(
            "random should not be in DSL functions",
            "random" in GbktDslVisitor.DSL_FUNCTIONS,
        )
        assertFalse(
            "println should not be in DSL functions",
            "println" in GbktDslVisitor.DSL_FUNCTIONS,
        )
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has enter requiring scene`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["enter"]
        assertNotNull("enter should have context requirements", contexts)
        assertTrue("enter should require scene context", "scene" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has exit requiring scene`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["exit"]
        assertNotNull("exit should have context requirements", contexts)
        assertTrue("exit should require scene context", "scene" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has position requiring entity`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["position"]
        assertNotNull("position should have context requirements", contexts)
        assertTrue("position should require entity context", "entity" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has sprite requiring entity`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["sprite"]
        assertNotNull("sprite should have context requirements", contexts)
        assertTrue("sprite should require entity context", "entity" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has hitbox allowing entity and sprite`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["hitbox"]
        assertNotNull("hitbox should have context requirements", contexts)
        assertTrue("hitbox should allow entity context", "entity" in contexts!!)
        assertTrue("hitbox should allow sprite context", "sprite" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has state requiring states`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["state"]
        assertNotNull("state should have context requirements", contexts)
        assertTrue("state should require states context", "states" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has tick requiring state`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["tick"]
        assertNotNull("tick should have context requirements", contexts)
        assertTrue("tick should require state context", "state" in contexts!!)
    }

    @Test
    fun `CONTEXT_REQUIREMENTS has goto requiring state`() {
        val contexts = GbktDslVisitor.CONTEXT_REQUIREMENTS["goto"]
        assertNotNull("goto should have context requirements", contexts)
        assertTrue("goto should require state context", "state" in contexts!!)
    }

    @Test
    fun `DslType enum has expected values`() {
        val types = GbktDslVisitor.DslType.values()
        assertTrue("SCENE should be a DslType", GbktDslVisitor.DslType.SCENE in types)
        assertTrue("ENTITY should be a DslType", GbktDslVisitor.DslType.ENTITY in types)
        assertTrue("DIALOG should be a DslType", GbktDslVisitor.DslType.DIALOG in types)
        assertTrue("CAMERA should be a DslType", GbktDslVisitor.DslType.CAMERA in types)
        assertTrue("VARIABLE should be a DslType", GbktDslVisitor.DslType.VARIABLE in types)
        assertTrue("ARRAY should be a DslType", GbktDslVisitor.DslType.ARRAY in types)
        assertTrue("FLAGS should be a DslType", GbktDslVisitor.DslType.FLAGS in types)
    }

    @Test
    fun `DslType enum has 7 values`() {
        assertEquals(7, GbktDslVisitor.DslType.values().size)
    }

    @Test
    fun `new visitor has empty collections`() {
        val visitor = GbktDslVisitor()
        assertTrue("scenes should be empty", visitor.scenes.isEmpty())
        assertTrue("entities should be empty", visitor.entities.isEmpty())
        assertTrue("variables should be empty", visitor.variables.isEmpty())
        assertTrue("dialogs should be empty", visitor.dialogs.isEmpty())
        assertTrue("cameras should be empty", visitor.cameras.isEmpty())
        assertTrue("flags should be empty", visitor.flags.isEmpty())
        assertTrue("allDslCalls should be empty", visitor.allDslCalls.isEmpty())
        assertTrue("references should be empty", visitor.references.isEmpty())
    }
}
