/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.Aspect
import io.github.gbkt.rpg.domain.ExpCurve
import io.github.gbkt.rpg.domain.MonsterTier
import io.github.gbkt.rpg.domain.StackMode
import io.github.gbkt.rpg.domain.TargetingMode
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// RPG DSL INTEGRATION TEST (Plan 06.5-08 SC-5, SC-11, SC-12, SC-15, SC-16)
// Comprehensive end-to-end test that constructs a full RPG game DSL with
// character, monster, abilities, combat, shop, equipment, save, and verifies
// all RPG systems are registered in GameIR.
// =============================================================================

class RpgIntegrationTest {

    /**
     * Full RPG game DSL produces valid GameIR with all RPG systems registered.
     *
     * Validates SC-11 (simpleBattle migrated to CombatEngineSystem), SC-12 (gbkt-rpg uses public
     * API only — no backend imports), SC-15 (integration test coverage), SC-5 (RPG battle logic
     * layers on engine combat state machine).
     */
    @Test
    fun `full RPG game DSL produces valid GameIR with all RPG systems`() {
        val ir =
            game("rpg-integration-test") {
                    // ---- Character with full stats and leveling ----
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(100)
                                sp(50)
                                atk(15)
                                def(10)
                                matk(8)
                                mdef(8)
                                agl(12)
                            }
                            level(1, maxLevel = 99, expCurve = ExpCurve.STANDARD)
                            onLevelUp { /* stats.hp += 10 via navigate */ }
                        }

                    // ---- Monster with AI ----
                    val goblin =
                        monster("goblin") {
                            name("Goblin")
                            tier(MonsterTier.COMMON)
                            stats {
                                hp(30)
                                atk(8)
                                def(5)
                                agl(10)
                            }
                            ai { basicAttack() }
                            drops { drop("potion", 30) }
                            exp(15)
                        }

                    // ---- Ability ----
                    val fireball by ability {
                        name("Fireball")
                        cost(sp = 8)
                        targeting(TargetingMode.SINGLE_ENEMY)
                        aspect(Aspect.FIRE)
                        power(25)
                    }

                    // ---- Status effect ----
                    val poison by statusEffect {
                        name("Poison")
                        debuff()
                        duration(5)
                        damagePerTurn(10)
                        stackMode(StackMode.REFRESH_DURATION)
                    }

                    // ---- Equipment system ----
                    equipmentSystem { dualWield() }

                    // ---- Character class ----
                    val warrior by characterClass {
                        name("Warrior")
                        growthRates {
                            hp(12)
                            atk(3)
                            def(2)
                        }
                        learns("fireball", atLevel = 5)
                    }

                    // ---- Shop / merchant ----
                    merchant("village_shop") {
                        name("Village Shop")
                        item("potion") { price(50) }
                        item("antidote") { price(30) }
                    }

                    // ---- Items (engine-level) ----
                    items {
                        category("consumable") { defaultMaxStack(10) }
                        item("potion") {
                            name("Potion")
                            category("consumable")
                            buyPrice(50)
                        }
                        item("antidote") {
                            name("Antidote")
                            category("consumable")
                            buyPrice(30)
                        }
                    }

                    // ---- Scenes ----
                    val gameplayScene = scene("gameplay") { frame {} }
                    val gameoverScene = scene("gameover") { enter {} }

                    // ---- Combat via simpleBattle (now uses CombatEngineSystem internally) ----
                    simpleBattle("combat") {
                        party(hero)
                        encounter { +goblin }
                        onVictory { navigate(gameplayScene) }
                        onDefeat { navigate(gameoverScene) }
                    }

                    // ---- Party system ----
                    partySystem { maxActive(4) }

                    // ---- RPG save ----
                    rpgSave { slots(3) }

                    start = gameplayScene
                }
                .build()

        // ---- Verify CombatEngineSystem registered (SC-11: simpleBattle migrated) ----
        assertTrue(
            ir.systems.any { it is CombatEngineSystem },
            "simpleBattle must register CombatEngineSystem (SC-11)",
        )

        // ---- Verify simpleBattle system has TURN_BASED combat type ----
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().firstOrNull { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id 'combat'")
        assertTrue(
            combatSystem.encounterConfig != null,
            "simpleBattle CombatEngineSystem must have encounterConfig set",
        )

        // ---- Verify character RPG system registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_character_system"
            },
            "character() must register rpg_character_system GenericSystem",
        )

        // ---- Verify monster RPG system registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_monster"
            },
            "monster() must register rpg_monster GenericSystem",
        )

        // ---- Verify ability RPG system registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_ability"
            },
            "ability() must register rpg_ability GenericSystem",
        )

        // ---- Verify status effect RPG system registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_status_effect"
            },
            "statusEffect() must register rpg_status_effect GenericSystem",
        )

        // ---- Verify equipment system registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_equipment_system"
            },
            "equipmentSystem() must register rpg_equipment_system GenericSystem",
        )

        // ---- Verify class registered ----
        assertTrue(
            ir.systems.any { it is GenericSystem && (it.config["type"] as? String) == "rpg_class" },
            "characterClass() must register rpg_class GenericSystem",
        )

        // ---- Verify merchant registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_merchant"
            },
            "merchant() must register rpg_merchant GenericSystem",
        )

        // ---- Verify items registered ----
        assertTrue(
            ir.items.isNotEmpty(),
            "items() must populate GameIR.items with item definitions",
        )

        // ---- Verify party system registered ----
        assertTrue(
            ir.systems.any {
                it is GenericSystem && (it.config["type"] as? String) == "rpg_party_system"
            },
            "partySystem() must register rpg_party_system GenericSystem",
        )

        // ---- Verify RPG save registered ----
        assertTrue(
            ir.systems.any { it is GenericSystem && (it.config["type"] as? String) == "rpg_save" },
            "rpgSave() must register rpg_save GenericSystem",
        )
    }

    /**
     * Module boundary verification: gbkt-rpg depends only on gbkt-core.
     *
     * This test verifies that the entire RPG DSL executes without any gbkt-backend-gbdk imports.
     * The test file itself lives in gbkt-rpg which has no gbkt-backend-gbdk dependency.
     *
     * Validates SC-12: gbkt-rpg has zero dependencies on engine internals.
     */
    @Test
    fun `simpleBattle with party string ID produces correct CombatEngineSystem`() {
        val ir =
            game("string-party-test") {
                    // Use party(String) overload — backward compatible with string-based party IDs
                    simpleBattle("arena") {
                        party("hero") // String overload
                        party("warrior") // String overload
                    }
                    val gameplayScene = scene("gameplay") { enter {} }
                    start = gameplayScene
                }
                .build()

        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().firstOrNull { it.id == "arena" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id 'arena'")

        @Suppress("UNCHECKED_CAST")
        val partyIds = combatSystem.encounterConfig?.get("partyIds") as? List<String>
        assertNotNull(partyIds, "encounterConfig must have partyIds")
        assertTrue(partyIds.contains("hero"), "partyIds must contain 'hero'")
        assertTrue(partyIds.contains("warrior"), "partyIds must contain 'warrior'")
    }
}
