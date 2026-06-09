/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.rpg.domain.AbilityDef
import io.github.gbkt.rpg.domain.AoeShape
import io.github.gbkt.rpg.domain.Aspect
import io.github.gbkt.rpg.domain.EffectCategory
import io.github.gbkt.rpg.domain.EffectTrigger
import io.github.gbkt.rpg.domain.ResistType
import io.github.gbkt.rpg.domain.StackMode
import io.github.gbkt.rpg.domain.StatusEffectDef
import io.github.gbkt.rpg.domain.TargetingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests proving that ability and status effect DSL builders produce CORE IR types.
 *
 * Key constraint: NO new sealed IR subtypes are created. All ability and status effect systems
 * registered by these builders are [GenericSystem] instances with config type "rpg_ability" or
 * "rpg_status_effect".
 */
class AbilityStatusEffectTest {

    // -------------------------------------------------------------------------
    // ability {} builder — produces GenericSystem with type=rpg_ability
    // -------------------------------------------------------------------------

    @Test
    fun `ability registers GenericSystem with correct config type`() {
        val ir =
            game("AbilityTest") {
                    val fireball by ability { name("Fireball") }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "fireball" }
        assertNotNull(system, "Expected a system with id 'fireball'")
        assertIs<GenericSystem>(
            system,
            "ability must produce GenericSystem, not a new sealed subtype",
        )
        assertEquals("rpg_ability", system.config["type"])
    }

    @Test
    fun `ability builder sets targeting and cost`() {
        val ir =
            game("AbilityTest") {
                    val heal by ability {
                        name("Heal")
                        cost(sp = 10, hp = 2)
                        targeting(TargetingMode.SINGLE_ALLY)
                        aspect(Aspect.HOLY)
                        power(50)
                        accuracy(95)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "heal" } as GenericSystem
        val def = system.config["def"] as AbilityDef
        assertEquals("Heal", def.name)
        assertEquals(10, def.spCost)
        assertEquals(2, def.hpCost)
        assertEquals(TargetingMode.SINGLE_ALLY, def.targeting)
        assertEquals(Aspect.HOLY, def.aspect)
        assertEquals(50, def.power)
        assertEquals(95, def.accuracy)
    }

    @Test
    fun `ability with execute block records ops`() {
        val ir =
            game("AbilityTest") {
                    val strike by ability {
                        name("Strike")
                        execute { navigate(SceneRef("battle_result")) }
                    }
                    scene("battle_result") { enter {} }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "strike" } as GenericSystem
        val def = system.config["def"] as AbilityDef
        assertTrue(def.executeOps.isNotEmpty(), "execute block must record ops")
        assertTrue(
            def.executeOps.any { it is NavigateTo },
            "execute block with navigate() must produce NavigateTo op",
        )
    }

    @Test
    fun `ability builder sets charge turns and effect application`() {
        val ir =
            game("AbilityTest") {
                    val meteor by ability {
                        name("Meteor")
                        chargeTurns(2)
                        appliesEffect("burn", chance = 60)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "meteor" } as GenericSystem
        val def = system.config["def"] as AbilityDef
        assertEquals(2, def.chargeTurns)
        assertEquals("burn", def.appliesEffect)
        assertEquals(60, def.effectChance)
    }

    @Test
    fun `ability builder sets range and aoe shape`() {
        val ir =
            game("AbilityTest") {
                    val arrow by ability {
                        name("Arrow")
                        range(min = 1, max = 4)
                        aoeShape(AoeShape.LINE)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "arrow" } as GenericSystem
        val def = system.config["def"] as AbilityDef
        assertEquals(1, def.rangeMin)
        assertEquals(4, def.rangeMax)
        assertEquals(AoeShape.LINE, def.aoeShape)
    }

    @Test
    fun `ability delegate infers name from property`() {
        val ir =
            game("AbilityTest") {
                    val fireball by ability { name("Fireball") }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        assertEquals("fireball", (ir.systems.find { it.id == "fireball" } as GenericSystem).id)
    }

    // -------------------------------------------------------------------------
    // statusEffect {} builder — produces GenericSystem with type=rpg_status_effect
    // -------------------------------------------------------------------------

    @Test
    fun `statusEffect registers GenericSystem`() {
        val ir =
            game("StatusTest") {
                    val poison by statusEffect { name("Poison") }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "poison" }
        assertNotNull(system, "Expected a system with id 'poison'")
        assertIs<GenericSystem>(system, "statusEffect must produce GenericSystem")
        assertEquals("rpg_status_effect", system.config["type"])
    }

    @Test
    fun `statusEffect with duration and stacking`() {
        val ir =
            game("StatusTest") {
                    val bleed by statusEffect {
                        name("Bleed")
                        debuff()
                        duration(4)
                        damagePerTurn(5)
                        stackMode(StackMode.INTENSITY)
                        maxStacks(5)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "bleed" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertEquals("Bleed", def.name)
        assertEquals(EffectCategory.DEBUFF, def.category)
        assertEquals(4, def.duration)
        assertEquals(5, def.damagePerTurn)
        assertEquals(StackMode.INTENSITY, def.stackMode)
        assertEquals(5, def.maxStacks)
        assertTrue(def.isDebuff, "DEBUFF category must set isDebuff=true")
    }

    @Test
    fun `statusEffect with buff category sets isDebuff false`() {
        val ir =
            game("StatusTest") {
                    val haste by statusEffect {
                        name("Haste")
                        buff()
                        duration(3)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "haste" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertEquals(EffectCategory.BUFF, def.category)
        assertFalse(def.isDebuff, "BUFF category must set isDebuff=false")
    }

    @Test
    fun `statusEffect with event triggers records ops per trigger`() {
        val ir =
            game("StatusTest") {
                    val thorns by statusEffect {
                        name("Thorns")
                        buff()
                        onTrigger(EffectTrigger.ON_DAMAGE_TAKEN) { navigate(SceneRef("battle_result")) }
                    }
                    scene("battle_result") { enter {} }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "thorns" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertTrue(
            def.triggers.contains(EffectTrigger.ON_DAMAGE_TAKEN),
            "ON_DAMAGE_TAKEN must be in triggers set",
        )
        val ops = def.triggerOps[EffectTrigger.ON_DAMAGE_TAKEN]
        assertNotNull(ops, "triggerOps must contain entry for ON_DAMAGE_TAKEN")
        assertTrue(ops.isNotEmpty(), "Trigger ops must be non-empty when navigate() is called")
    }

    @Test
    fun `statusEffect interaction rules stored correctly`() {
        val ir =
            game("StatusTest") {
                    val frozen by statusEffect {
                        name("Frozen")
                        debuff()
                        interacts("burn", "cancels")
                        interacts("wet", "converts_to")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "frozen" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertEquals("cancels", def.interactsWith["burn"])
        assertEquals("converts_to", def.interactsWith["wet"])
    }

    @Test
    fun `statusEffect INTENSITY mode with onStackApplied and onStackRemoved hooks records ops`() {
        val ir =
            game("StatusTest") {
                    val hemorrhage by statusEffect {
                        name("Hemorrhage")
                        debuff()
                        stackMode(StackMode.INTENSITY)
                        maxStacks(5)
                        onStackApplied { navigate(SceneRef("burst_result")) }
                        onStackRemoved { navigate(SceneRef("stack_fade")) }
                    }
                    scene("burst_result") { enter {} }
                    scene("stack_fade") { enter {} }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "hemorrhage" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertTrue(def.onStackAppliedOps.isNotEmpty(), "onStackApplied must record ops")
        assertTrue(def.onStackRemovedOps.isNotEmpty(), "onStackRemoved must record ops")
    }

    @Test
    fun `statusEffect with resistType STAT_CONTEST and resistStat stores correctly`() {
        val ir =
            game("StatusTest") {
                    val curse by statusEffect {
                        name("Curse")
                        debuff()
                        applyChance(70)
                        resistType(ResistType.STAT_CONTEST)
                        resistStat("mdef")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "curse" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertEquals(70, def.applyChance)
        assertEquals(ResistType.STAT_CONTEST, def.resistType)
        assertEquals("mdef", def.resistStat)
    }

    @Test
    fun `statusEffect immuneToEffect stores per-effect immunity set`() {
        val ir =
            game("StatusTest") {
                    val barrier by statusEffect {
                        name("Barrier")
                        buff()
                        immuneToEffect("poison")
                        immuneToEffect("burn")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "barrier" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertTrue(def.immuneToEffects.contains("poison"), "immuneToEffects must contain 'poison'")
        assertTrue(def.immuneToEffects.contains("burn"), "immuneToEffects must contain 'burn'")
    }

    @Test
    fun `statusEffect perStackScaling flag stored on def`() {
        val ir =
            game("StatusTest") {
                    val venom by statusEffect {
                        name("Venom")
                        debuff()
                        stackMode(StackMode.INTENSITY)
                        damagePerTurn(3)
                        perStackScaling()
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "venom" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertTrue(def.perStackScaling, "perStackScaling flag must be true when set")
    }

    @Test
    fun `statusEffect delegate infers name from property`() {
        val ir =
            game("StatusTest") {
                    val poison by statusEffect { name("Poison") }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        assertEquals("poison", (ir.systems.find { it.id == "poison" } as GenericSystem).id)
    }

    @Test
    fun `no ability or statusEffect system is a sealed IR subtype`() {
        val ir =
            game("ConstraintTest") {
                    val strike by ability { name("Strike") }
                    val heal by ability { name("Heal") }
                    val poison by statusEffect { name("Poison") }
                    val haste by statusEffect { buff() }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        for (system in ir.systems) {
            // All systems must be core IR types — no new RPG-specific sealed subtypes
            assertTrue(
                system is GenericSystem ||
                    system is io.github.gbkt.core.ir.CameraSystem ||
                    system is io.github.gbkt.core.ir.SoundSystem ||
                    system is io.github.gbkt.core.ir.SaveSystem ||
                    system is io.github.gbkt.core.ir.ExplorationSystem ||
                    system is io.github.gbkt.core.ir.DialogSystem ||
                    system is io.github.gbkt.core.ir.CombatEngineSystem,
                "All systems must be core IR types. Found: ${system::class.simpleName}",
            )
        }
    }

    @Test
    fun `ability defaults are correct`() {
        val ir =
            game("AbilityTest") {
                    val basic by ability {}
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "basic" } as GenericSystem
        val def = system.config["def"] as AbilityDef
        assertEquals(0, def.spCost)
        assertEquals(0, def.hpCost)
        assertEquals(TargetingMode.SINGLE_ENEMY, def.targeting)
        assertEquals(Aspect.NONE, def.aspect)
        assertEquals(100, def.accuracy)
        assertEquals(0, def.chargeTurns)
        assertNull(def.appliesEffect)
        assertEquals(100, def.effectChance)
        assertTrue(def.executeOps.isEmpty())
    }

    @Test
    fun `statusEffect immuneTo stores category immunity set`() {
        val ir =
            game("StatusTest") {
                    val holyShield by statusEffect {
                        name("Holy Shield")
                        buff()
                        immuneTo(EffectCategory.DEBUFF, EffectCategory.DOT)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "holyShield" } as GenericSystem
        val def = system.config["def"] as StatusEffectDef
        assertTrue(
            def.immuneCategories.contains(EffectCategory.DEBUFF),
            "immuneCategories must contain DEBUFF",
        )
        assertTrue(
            def.immuneCategories.contains(EffectCategory.DOT),
            "immuneCategories must contain DOT",
        )
    }
}
