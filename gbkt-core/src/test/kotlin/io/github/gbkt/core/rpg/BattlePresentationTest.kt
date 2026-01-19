/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the battle presentation system.
 *
 * Validates:
 * - Presentation configuration via DSL
 * - Code generation for damage numbers, messages, effects
 * - Integration with battle system
 */
class BattlePresentationTest {

    // =========================================================================
    // PRESENTATION CONFIGURATION
    // =========================================================================

    @Test
    fun `presentation builder creates default config`() {
        val builder = BattlePresentationBuilder()
        val config = builder.build()

        assertFalse(config.showDamageNumbers)
        assertEquals(0, config.hitShakeIntensity)
        assertEquals(0, config.critShakeIntensity)
        assertFalse(config.flashOnCrit)
        assertFalse(config.showActionMessages)
        assertFalse(config.showCritMessages)
        assertFalse(config.showDefeatMessages)
    }

    @Test
    fun `presentation builder configures damage numbers`() {
        val builder = BattlePresentationBuilder()
        builder.damageNumbers(true)
        val config = builder.build()

        assertTrue(config.showDamageNumbers)
    }

    @Test
    fun `presentation builder configures damage numbers with speed and duration`() {
        val builder = BattlePresentationBuilder()
        builder.damageNumbers(true, speed = 2, duration = 45)
        val config = builder.build()

        assertTrue(config.showDamageNumbers)
        assertEquals(2, config.damageNumberSpeed)
        assertEquals(45, config.damageNumberDuration)
    }

    @Test
    fun `presentation builder configures screen shake on hit`() {
        val builder = BattlePresentationBuilder()
        builder.screenShakeOnHit(intensity = 4, duration = 12)
        val config = builder.build()

        assertEquals(4, config.hitShakeIntensity)
        assertEquals(12, config.hitShakeDuration)
    }

    @Test
    fun `presentation builder configures screen shake on crit`() {
        val builder = BattlePresentationBuilder()
        builder.screenShakeOnCrit(intensity = 8, duration = 16)
        val config = builder.build()

        assertEquals(8, config.critShakeIntensity)
    }

    @Test
    fun `presentation builder configures crit flash`() {
        val builder = BattlePresentationBuilder()
        builder.flashOnCrit(duration = 6)
        val config = builder.build()

        assertTrue(config.flashOnCrit)
        assertEquals(6, config.critFlashDuration)
    }

    @Test
    fun `presentation builder configures action messages`() {
        val builder = BattlePresentationBuilder()
        builder.actionMessages(true)
        builder.critMessages(true)
        builder.defeatMessages(true)
        val config = builder.build()

        assertTrue(config.showActionMessages)
        assertTrue(config.showCritMessages)
        assertTrue(config.showDefeatMessages)
    }

    @Test
    fun `presentation builder configures message display duration`() {
        val builder = BattlePresentationBuilder()
        builder.messageDisplayDuration(90)
        val config = builder.build()

        assertEquals(90, config.messageDisplayDuration)
    }

    // =========================================================================
    // DSL INTEGRATION
    // =========================================================================

    @Test
    fun `battleSystem can configure presentation via DSL`() {
        val system =
            battleSystem("combat") {
                presentation {
                    damageNumbers(true)
                    screenShakeOnHit(4, 8)
                    screenShakeOnCrit(6)
                    flashOnCrit(4)
                    actionMessages(true)
                    critMessages(true)
                    defeatMessages(true)
                }
            }

        assertTrue(system.presentation.showDamageNumbers)
        assertEquals(4, system.presentation.hitShakeIntensity)
        assertEquals(8, system.presentation.hitShakeDuration)
        assertEquals(6, system.presentation.critShakeIntensity)
        assertTrue(system.presentation.flashOnCrit)
        assertTrue(system.presentation.showActionMessages)
        assertTrue(system.presentation.showCritMessages)
        assertTrue(system.presentation.showDefeatMessages)
    }

    @Test
    fun `battleSystem without presentation has default config`() {
        val system = battleSystem("combat") {}

        assertFalse(system.presentation.showDamageNumbers)
        assertEquals(0, system.presentation.hitShakeIntensity)
        assertFalse(system.presentation.flashOnCrit)
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `presentation system generates damage number constants`() {
        val game =
            gbGame("DamageNumberCodegenTest") {
                val system =
                    battleSystem("battle") {
                        presentation { damageNumbers(true, speed = 2, duration = 45) }
                    }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("BATTLE_DMG_NUM_SPEED 2u"),
            "Should generate damage number speed constant",
        )
        assertTrue(
            code.contains("BATTLE_DMG_NUM_DURATION 45u"),
            "Should generate damage number duration constant",
        )
        assertTrue(
            code.contains("BATTLE_MAX_DMG_NUMBERS 8u"),
            "Should generate max damage numbers constant",
        )
    }

    @Test
    fun `presentation system generates damage number variables`() {
        val game =
            gbGame("DamageNumberVarsTest") {
                val system = battleSystem("battle") { presentation { damageNumbers(true) } }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_battle_dmg_num_count"),
            "Should generate damage number count var",
        )
        assertTrue(code.contains("_battle_dmg_num_x"), "Should generate damage number x array")
        assertTrue(code.contains("_battle_dmg_num_y"), "Should generate damage number y array")
        assertTrue(
            code.contains("_battle_dmg_num_value"),
            "Should generate damage number value array",
        )
    }

    @Test
    fun `presentation system generates show damage number function`() {
        val game =
            gbGame("ShowDamageNumberTest") {
                val system = battleSystem("battle") { presentation { damageNumbers(true) } }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_battle_show_damage_number"),
            "Should generate show damage number function",
        )
    }

    @Test
    fun `presentation system generates message system`() {
        val game =
            gbGame("MessageSystemTest") {
                val system =
                    battleSystem("battle") {
                        presentation {
                            actionMessages(true)
                            messageDisplayDuration(90)
                        }
                    }

                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("BATTLE_MSG_DISPLAY_DURATION 90u"),
            "Should generate message duration constant",
        )
        assertTrue(code.contains("_battle_msg_active"), "Should generate message active var")
        assertTrue(code.contains("_battle_show_message"), "Should generate show message function")
        assertTrue(
            code.contains("_battle_update_message"),
            "Should generate update message function",
        )
    }

    @Test
    fun `presentation system generates screen shake constants`() {
        val game =
            gbGame("ScreenShakeCodegenTest") {
                val system =
                    battleSystem("battle") {
                        presentation {
                            screenShakeOnHit(4, 12)
                            screenShakeOnCrit(8)
                        }
                    }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("BATTLE_HIT_SHAKE_INTENSITY 4u"),
            "Should generate hit shake intensity",
        )
        assertTrue(
            code.contains("BATTLE_HIT_SHAKE_DURATION 12u"),
            "Should generate hit shake duration",
        )
        assertTrue(
            code.contains("BATTLE_CRIT_SHAKE_INTENSITY 8u"),
            "Should generate crit shake intensity",
        )
    }

    @Test
    fun `presentation system generates crit flash constant`() {
        val game =
            gbGame("CritFlashCodegenTest") {
                val system = battleSystem("battle") { presentation { flashOnCrit(8) } }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("BATTLE_CRIT_FLASH_DURATION 8u"),
            "Should generate crit flash duration constant",
        )
    }

    @Test
    fun `presentation system generates update function`() {
        val game =
            gbGame("PresentationUpdateTest") {
                val system =
                    battleSystem("battle") {
                        presentation {
                            damageNumbers(true)
                            actionMessages(true)
                        }
                    }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_battle_presentation_update"),
            "Should generate presentation update function",
        )
        assertTrue(code.contains("_battle_update_message"), "Should call update_message")
        assertTrue(
            code.contains("_battle_update_damage_numbers"),
            "Should call update_damage_numbers",
        )
    }

    @Test
    fun `combatant position arrays are generated`() {
        val game =
            gbGame("CombatantPositionTest") {
                // Combat core only generates if there are abilities, characters, or monsters
                // Add a dummy ability to trigger combat core generation
                val dummyAbility by ability { name("Test") }

                val system =
                    battleSystem("battle") {
                        maxPartySize(3)
                        maxEnemies(4)
                    }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }

                // Suppress unused warning
                @Suppress("UNUSED_VARIABLE") val used = dummyAbility
            }

        val code = game.compileForTest()

        // Combat core uses default array sizes (4 party + 4 enemies = 8 max combatants)
        // even if specific battle systems have smaller sizes
        assertTrue(code.contains("_combatant_x[8]"), "Should generate combatant x position array")
        assertTrue(code.contains("_combatant_y[8]"), "Should generate combatant y position array")
    }

    @Test
    fun `no presentation code generated when no features enabled`() {
        val game =
            gbGame("NoPresentationTest") {
                val system =
                    battleSystem("battle") {
                        // No presentation block = default config with all features disabled
                    }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should have battle system but no presentation-specific code
        assertTrue(code.contains("BATTLE SYSTEM: battle"), "Should have battle system header")
        assertFalse(
            code.contains("BATTLE PRESENTATION"),
            "Should not have presentation header when disabled",
        )
        assertFalse(
            code.contains("_battle_dmg_num_count"),
            "Should not generate damage number vars when disabled",
        )
    }
}
