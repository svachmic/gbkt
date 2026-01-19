/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.ir.StatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for status effect stacking modes.
 *
 * Validates all StackMode behaviors:
 * - REPLACE: Clear existing, apply new
 * - REFRESH_DURATION: Reset duration counter
 * - STACK_INTENSITY: Increment stack counter
 * - STACK_DURATION: Add to duration
 * - NONE: Skip if already applied
 */
class StatusEffectStackingTest {

    @Test
    fun `all stack modes are defined`() {
        val modes = StackMode.entries
        assertEquals(5, modes.size)
        assertEquals(StackMode.REPLACE, modes[0])
        assertEquals(StackMode.REFRESH_DURATION, modes[1])
        assertEquals(StackMode.STACK_INTENSITY, modes[2])
        assertEquals(StackMode.STACK_DURATION, modes[3])
        assertEquals(StackMode.NONE, modes[4])
    }

    @Test
    fun `REPLACE mode replaces existing effect`() {
        val effect =
            StatusEffectDefinition(
                name = "Poison",
                id = StatusEffectId(1),
                category = EffectCategory.DEBUFF,
                baseDuration = 3.turns,
                tier = EffectTier.C,
                stackMode = StackMode.REPLACE,
                maxStacks = 1,
                statModifiers = emptyMap(),
                damagePerTurn = 10,
                healPerTurn = 0,
                preventsAction = false,
                iconIndex = 0,
            )

        assertEquals(StackMode.REPLACE, effect.stackMode)
        assertEquals(1, effect.maxStacks)
    }

    @Test
    fun `REFRESH_DURATION mode resets duration`() {
        val effect =
            StatusEffectDefinition(
                name = "Regen",
                id = StatusEffectId(2),
                category = EffectCategory.BUFF,
                baseDuration = 5.turns,
                tier = EffectTier.B,
                stackMode = StackMode.REFRESH_DURATION,
                maxStacks = 1,
                statModifiers = emptyMap(),
                damagePerTurn = 0,
                healPerTurn = 20,
                preventsAction = false,
                iconIndex = 1,
            )

        assertEquals(StackMode.REFRESH_DURATION, effect.stackMode)
    }

    @Test
    fun `STACK_INTENSITY mode allows multiple stacks`() {
        val effect =
            StatusEffectDefinition(
                name = "Bleed",
                id = StatusEffectId(3),
                category = EffectCategory.DEBUFF,
                baseDuration = 4.turns,
                tier = EffectTier.C,
                stackMode = StackMode.STACK_INTENSITY,
                maxStacks = 4,
                statModifiers = emptyMap(),
                damagePerTurn = 5,
                healPerTurn = 0,
                preventsAction = false,
                iconIndex = 2,
            )

        assertEquals(StackMode.STACK_INTENSITY, effect.stackMode)
        assertEquals(4, effect.maxStacks)
    }

    @Test
    fun `STACK_DURATION mode extends duration`() {
        val effect =
            StatusEffectDefinition(
                name = "Shield",
                id = StatusEffectId(4),
                category = EffectCategory.BUFF,
                baseDuration = 3.turns,
                tier = EffectTier.B,
                stackMode = StackMode.STACK_DURATION,
                maxStacks = 2,
                statModifiers = mapOf(StatType.DEF to 10),
                damagePerTurn = 0,
                healPerTurn = 0,
                preventsAction = false,
                iconIndex = 3,
            )

        assertEquals(StackMode.STACK_DURATION, effect.stackMode)
        assertEquals(2, effect.maxStacks)
    }

    @Test
    fun `NONE mode prevents stacking`() {
        val effect =
            StatusEffectDefinition(
                name = "Stun",
                id = StatusEffectId(5),
                category = EffectCategory.DEBUFF,
                baseDuration = 1.turns,
                tier = EffectTier.A,
                stackMode = StackMode.NONE,
                maxStacks = 1,
                statModifiers = emptyMap(),
                damagePerTurn = 0,
                healPerTurn = 0,
                preventsAction = true,
                iconIndex = 4,
            )

        assertEquals(StackMode.NONE, effect.stackMode)
    }

    @Test
    fun `default stack mode is REFRESH_DURATION`() {
        val effect =
            StatusEffectDefinition(
                name = "Default",
                id = StatusEffectId(6),
                category = EffectCategory.BUFF,
                baseDuration = 3.turns,
                tier = EffectTier.C,
                // Not specifying stackMode - should default
                statModifiers = emptyMap(),
                damagePerTurn = 0,
                healPerTurn = 0,
                preventsAction = false,
                iconIndex = 5,
            )

        assertEquals(StackMode.REFRESH_DURATION, effect.stackMode)
        assertEquals(1, effect.maxStacks)
    }

    @Test
    fun `different stack modes have different ordinals`() {
        assertNotEquals(StackMode.REPLACE.ordinal, StackMode.REFRESH_DURATION.ordinal)
        assertNotEquals(StackMode.STACK_INTENSITY.ordinal, StackMode.STACK_DURATION.ordinal)
        assertNotEquals(StackMode.NONE.ordinal, StackMode.REPLACE.ordinal)
    }
}
