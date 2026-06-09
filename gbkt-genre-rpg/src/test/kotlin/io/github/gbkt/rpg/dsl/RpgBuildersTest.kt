/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SystemIR
import io.github.gbkt.core.ir.TriggerSystem
import io.github.gbkt.rpg.domain.CharacterDef
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.MonsterDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests proving that RPG DSL builders on GameBuilder produce CORE IR types.
 *
 * Key constraint: NO new sealed IR subtypes created beyond core types. simpleBattle() produces
 * CombatEngineSystem (a core IR type, not an RPG-specific sealed subtype). Other RPG builders
 * produce GenericSystem instances.
 */
class RpgBuildersTest {

    // -------------------------------------------------------------------------
    // character {} builder
    // -------------------------------------------------------------------------

    @Test
    fun `character builder produces CharacterDef with correct fields`() {
        var capturedChar: CharacterDef? = null

        game("RPGTest") {
                capturedChar =
                    character("hero") {
                        name("Hero")
                        stats {
                            hp(20)
                            atk(5)
                            def(3)
                        }
                    }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(capturedChar)
        assertEquals("hero", capturedChar!!.id)
        assertEquals("Hero", capturedChar!!.name)
        assertEquals(CombatStats(hp = 20, atk = 5, def = 3), capturedChar!!.stats)
    }

    @Test
    fun `character builder result is a domain data class not an IR type`() {
        var capturedChar: CharacterDef? = null

        game("RPGTest") {
                capturedChar =
                    character("hero") {
                        name("Hero")
                        stats {
                            hp(20)
                            atk(5)
                            def(3)
                        }
                    }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        // CharacterDef must NOT implement any sealed IR interface
        val char = capturedChar!!
        assertTrue(char !is SystemIR, "CharacterDef must not implement SystemIR")
    }

    // -------------------------------------------------------------------------
    // monster {} builder
    // -------------------------------------------------------------------------

    @Test
    fun `monster builder produces MonsterDef with correct fields`() {
        var capturedMonster: MonsterDef? = null

        game("RPGTest") {
                capturedMonster =
                    monster("goblin") {
                        name("Goblin")
                        stats {
                            hp(10)
                            atk(3)
                            def(1)
                        }
                        exp(5)
                    }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(capturedMonster)
        assertEquals("goblin", capturedMonster!!.id)
        assertEquals("Goblin", capturedMonster!!.name)
        assertEquals(CombatStats(hp = 10, atk = 3, def = 1), capturedMonster!!.stats)
        assertEquals(5, capturedMonster!!.expReward)
    }

    // -------------------------------------------------------------------------
    // simpleBattle {} builder — now produces CombatEngineSystem (core IR type)
    // -------------------------------------------------------------------------

    @Test
    fun `simpleBattle registers a CombatEngineSystem in game IR systems`() {
        val ir =
            game("RPGTest") {
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(20)
                                atk(5)
                                def(3)
                            }
                        }
                    val goblin =
                        monster("goblin") {
                            name("Goblin")
                            stats {
                                hp(10)
                                atk(3)
                                def(1)
                            }
                            exp(5)
                        }

                    val gameplayScene = scene("gameplay") { enter {} }
                    val gameoverScene = scene("gameover") { enter {} }
                    simpleBattle("combat") {
                        party(hero)
                        encounter { +goblin }
                        onVictory { navigate(gameplayScene) }
                        onDefeat { navigate(gameoverScene) }
                    }

                    start = gameplayScene
                }
                .build()

        val combatSystem = ir.systems.find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected a system with id 'combat'")
        assertIs<CombatEngineSystem>(
            combatSystem,
            "simpleBattle must produce CombatEngineSystem (a core IR type, not an RPG-specific sealed subtype)",
        )
        assertEquals(CombatType.TURN_BASED, (combatSystem as CombatEngineSystem).combatType)
    }

    @Test
    fun `simpleBattle CombatEngineSystem encounterConfig contains required keys`() {
        val ir =
            game("RPGTest") {
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(20)
                                atk(5)
                                def(3)
                            }
                        }
                    val goblin =
                        monster("goblin") {
                            name("Goblin")
                            stats {
                                hp(10)
                                atk(3)
                                def(1)
                            }
                        }

                    simpleBattle("combat") {
                        party(hero)
                        encounter { +goblin }
                    }

                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val combatSystem = ir.systems.find { it.id == "combat" } as CombatEngineSystem
        val config = combatSystem.encounterConfig
        assertNotNull(config, "encounterConfig must be set by simpleBattle")
        assertTrue(config.containsKey("partyIds"), "encounterConfig must contain partyIds")
        assertTrue(
            config.containsKey("encounterData"),
            "encounterConfig must contain encounterData",
        )
    }

    @Test
    fun `simpleBattle partyIds contains registered character ids`() {
        val ir =
            game("RPGTest") {
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(20)
                                atk(5)
                                def(3)
                            }
                        }

                    simpleBattle("combat") { party(hero) }

                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val combatSystem = ir.systems.find { it.id == "combat" } as CombatEngineSystem
        @Suppress("UNCHECKED_CAST")
        val partyIds = combatSystem.encounterConfig?.get("partyIds") as? List<String>
        assertNotNull(partyIds, "encounterConfig must contain partyIds")
        assertTrue(partyIds.contains("hero"), "partyIds must contain 'hero'")
    }

    @Test
    fun `no system in ir is an RPG-specific sealed subtype`() {
        val ir =
            game("RPGTest") {
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(20)
                                atk(5)
                                def(3)
                            }
                        }
                    val goblin =
                        monster("goblin") {
                            name("Goblin")
                            stats {
                                hp(10)
                                atk(3)
                                def(1)
                            }
                        }

                    simpleBattle("combat") {
                        party(hero)
                        encounter { +goblin }
                    }

                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        // All systems must be core IR types (GenericSystem, CombatEngineSystem, CameraSystem, etc.)
        // Not new RPG-specific sealed subtypes
        for (system in ir.systems) {
            assertTrue(
                system is GenericSystem ||
                    system is CombatEngineSystem ||
                    system is io.github.gbkt.core.ir.CameraSystem ||
                    system is io.github.gbkt.core.ir.SoundSystem ||
                    system is io.github.gbkt.core.ir.SaveSystem ||
                    system is io.github.gbkt.core.ir.ExplorationSystem ||
                    system is io.github.gbkt.core.ir.DialogSystem,
                "All systems must be core IR types. Found: ${system::class.simpleName}",
            )
        }
    }

    // -------------------------------------------------------------------------
    // battleUpdate() ScriptBuilder extension
    // -------------------------------------------------------------------------

    @Test
    fun `battleUpdate in scene frame produces TriggerSystem ScriptOp`() {
        val ir =
            game("RPGTest") {
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(20)
                                atk(5)
                                def(3)
                            }
                        }
                    val goblin =
                        monster("goblin") {
                            name("Goblin")
                            stats {
                                hp(10)
                                atk(3)
                                def(1)
                            }
                        }

                    simpleBattle("combat") {
                        party(hero)
                        encounter { +goblin }
                    }

                    val combat_sceneScene = scene("combat_scene") {
                        enter {}
                        frame { battleUpdate("combat") }
                    }
                    start = combat_sceneScene
                }
                .build()

        val combatScene = ir.scenes.find { it.id == "combat_scene" }
        assertNotNull(combatScene, "combat_scene must exist")
        val frameOps = combatScene!!.frameOps
        val triggerOp = frameOps.find { it is TriggerSystem && it.systemId == "combat" }
        assertNotNull(
            triggerOp,
            "battleUpdate('combat') must produce TriggerSystem('combat') in frame ops",
        )
    }

    // -------------------------------------------------------------------------
    // Minimal happy path (smoke test)
    // -------------------------------------------------------------------------

    @Test
    fun `minimal simpleBattle usage compiles and builds successfully`() {
        val ir =
            game("MinimalRPG") {
                    val hero =
                        character("c") {
                            name("C")
                            stats {
                                hp(1)
                                atk(1)
                                def(0)
                            }
                        }
                    simpleBattle("b") { party(hero) }
                    val sScene = scene("s") { enter {} }
                    start = sScene
                }
                .build()

        assertNotNull(ir)
        assertEquals("MinimalRPG", ir.name)
        assertTrue(ir.systems.any { it.id == "b" })
    }

    // -------------------------------------------------------------------------
    // onVictory / onDefeat produce ScriptOp sequences in config
    // -------------------------------------------------------------------------

    @Test
    fun `onVictory and onDefeat ScriptOp sequences stored in CombatEngineSystem`() {
        val ir =
            game("RPGTest") {
                    val hero =
                        character("hero") {
                            name("Hero")
                            stats {
                                hp(20)
                                atk(5)
                                def(3)
                            }
                        }

                    val gameplayScene = scene("gameplay") { enter {} }
                    val gameoverScene = scene("gameover") { enter {} }
                    simpleBattle("combat") {
                        party(hero)
                        onVictory { navigate(gameplayScene) }
                        onDefeat { navigate(gameoverScene) }
                    }

                    start = gameplayScene
                }
                .build()

        val system = ir.systems.find { it.id == "combat" } as CombatEngineSystem
        assertTrue(
            system.onVictoryOps.isNotEmpty(),
            "onVictoryOps must not be empty when navigate() is called",
        )
        assertTrue(
            system.onDefeatOps.isNotEmpty(),
            "onDefeatOps must not be empty when navigate() is called",
        )

        // encounterConfig also carries onVictoryOps/onDefeatOps for backward compatibility
        val config = system.encounterConfig
        assertNotNull(config, "encounterConfig must be set")
        assertTrue(config.containsKey("onVictoryOps"), "encounterConfig must contain onVictoryOps")
        assertTrue(config.containsKey("onDefeatOps"), "encounterConfig must contain onDefeatOps")
    }
}
