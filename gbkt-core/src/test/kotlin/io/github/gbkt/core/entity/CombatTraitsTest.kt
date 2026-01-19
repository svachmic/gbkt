/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for combat entity traits system.
 *
 * Validates:
 * - Combat component configuration
 * - Damage dealing and healing
 * - Knockback and stun mechanics
 * - Invincibility frames
 * - Code generation
 */
class CombatTraitsTest {

    // =========================================================================
    // COMBAT COMPONENT BUILDER (via entity)
    // =========================================================================

    @Test
    fun `combat component has default values`() {
        val game =
            gbGame("CombatDefaultsTest") {
                val entity by entity {
                    position(0, 0)
                    combat {}
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        val component = entity.combatComponent
        assertNotNull(component)

        assertEquals("entity", component.entityName)
        assertEquals(100, component.maxHp)
        assertEquals(100, component.currentHp)
        assertEquals(10, component.attackPower)
        assertEquals(0, component.defense)
        assertEquals(30, component.invincibilityFrames)
        assertEquals(4, component.knockbackForce)
        assertEquals(0, component.stunFrames)
        assertEquals(CombatTeam.NEUTRAL, component.team)
    }

    @Test
    fun `combat component can configure max hp`() {
        val game =
            gbGame("CombatMaxHpTest") {
                val entity by entity {
                    position(0, 0)
                    combat { maxHp(50) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        val component = entity.combatComponent
        assertNotNull(component)

        assertEquals(50, component.maxHp)
        assertEquals(50, component.currentHp)
    }

    @Test
    fun `combat component can configure starting hp`() {
        val game =
            gbGame("CombatStartHpTest") {
                val entity by entity {
                    position(0, 0)
                    combat {
                        maxHp(100)
                        startingHp(75)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        val component = entity.combatComponent
        assertNotNull(component)

        assertEquals(100, component.maxHp)
        assertEquals(75, component.currentHp)
    }

    @Test
    fun `combat component can configure attack power`() {
        val game =
            gbGame("CombatAttackPowerTest") {
                val entity by entity {
                    position(0, 0)
                    combat { attackPower(25) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        assertEquals(25, entity.combatComponent?.attackPower)
    }

    @Test
    fun `combat component can configure defense`() {
        val game =
            gbGame("CombatDefenseTest") {
                val entity by entity {
                    position(0, 0)
                    combat { defense(10) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        assertEquals(10, entity.combatComponent?.defense)
    }

    @Test
    fun `combat component can configure invincibility frames`() {
        val game =
            gbGame("CombatInvincibilityTest") {
                val entity by entity {
                    position(0, 0)
                    combat { invincibilityFrames(60) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        assertEquals(60, entity.combatComponent?.invincibilityFrames)
    }

    @Test
    fun `combat component can configure knockback force`() {
        val game =
            gbGame("CombatKnockbackTest") {
                val entity by entity {
                    position(0, 0)
                    combat { knockbackForce(8) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        assertEquals(8, entity.combatComponent?.knockbackForce)
    }

    @Test
    fun `combat component can configure stun frames`() {
        val game =
            gbGame("CombatStunTest") {
                val entity by entity {
                    position(0, 0)
                    combat { stunFrames(15) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        assertEquals(15, entity.combatComponent?.stunFrames)
    }

    @Test
    fun `combat component can configure team`() {
        val game =
            gbGame("CombatTeamTest") {
                val entity by entity {
                    position(0, 0)
                    combat { team(CombatTeam.PLAYER) }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "entity" }
        assertNotNull(entity)
        assertEquals(CombatTeam.PLAYER, entity.combatComponent?.team)
    }

    // =========================================================================
    // COMBAT TEAM
    // =========================================================================

    @Test
    fun `CombatTeam has all expected values`() {
        val teams = CombatTeam.entries

        assertTrue(teams.contains(CombatTeam.PLAYER))
        assertTrue(teams.contains(CombatTeam.ENEMY))
        assertTrue(teams.contains(CombatTeam.NEUTRAL))
        assertTrue(teams.contains(CombatTeam.HAZARD))
    }

    // =========================================================================
    // KNOCKBACK DIRECTION
    // =========================================================================

    @Test
    fun `KnockbackDirection has all expected values`() {
        val dirs = KnockbackDirection.entries

        assertTrue(dirs.contains(KnockbackDirection.AWAY))
        assertTrue(dirs.contains(KnockbackDirection.TOWARD))
        assertTrue(dirs.contains(KnockbackDirection.LEFT))
        assertTrue(dirs.contains(KnockbackDirection.RIGHT))
        assertTrue(dirs.contains(KnockbackDirection.UP))
        assertTrue(dirs.contains(KnockbackDirection.DOWN))
        assertTrue(dirs.contains(KnockbackDirection.NONE))
    }

    // =========================================================================
    // ENTITY WITH COMBAT
    // =========================================================================

    @Test
    fun `entity can have combat component`() {
        val game =
            gbGame("EntityCombatTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        attackPower(15)
                        defense(5)
                        team(CombatTeam.PLAYER)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val player = requireNotNull(game.entities.find { it.name == "player" })
        assertTrue(player.hasCombat)
        val combat = requireNotNull(player.combatComponent)
        assertEquals(100, combat.maxHp)
        assertEquals(15, combat.attackPower)
        assertEquals(5, combat.defense)
        assertEquals(CombatTeam.PLAYER, combat.team)
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `dealDamage emits IR`() {
        val game =
            gbGame("DealDamageIRTest") {
                val enemy by entity {
                    position(100, 72)
                    combat { maxHp(50) }
                }

                start = scene("main") { every.frame { dealDamage(to = enemy, amount = 10) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRActionDamage && stmt.damage == 10 }
        assertTrue(hasIR, "Should emit IRActionDamage")
    }

    @Test
    fun `heal emits IR`() {
        val game =
            gbGame("HealIRTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start = scene("main") { every.frame { heal(player, amount = 20) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IREntityHeal && stmt.amount == 20 }
        assertTrue(hasIR, "Should emit IREntityHeal")
    }

    @Test
    fun `knockback emits IR`() {
        val game =
            gbGame("KnockbackIRTest") {
                val enemy by entity {
                    position(100, 72)
                    combat { maxHp(50) }
                }

                start = scene("main") { every.frame { enemy.knockback(dirX = -4, dirY = 0) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IREntityKnockback && stmt.forceX == -4 && stmt.forceY == 0
            }
        assertTrue(hasIR, "Should emit IREntityKnockback")
    }

    @Test
    fun `stun emits IR`() {
        val game =
            gbGame("StunIRTest") {
                val enemy by entity {
                    position(100, 72)
                    combat { maxHp(50) }
                }

                start = scene("main") { every.frame { enemy.stun(frames = 30) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IREntityStun && stmt.frames == 30 }
        assertTrue(hasIR, "Should emit IREntityStun")
    }

    @Test
    fun `startInvincibility emits IR`() {
        val game =
            gbGame("InvincibilityIRTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start = scene("main") { every.frame { player.startInvincibility(frames = 60) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRStartInvincibility && stmt.frames == 60 }
        assertTrue(hasIR, "Should emit IRStartInvincibility")
    }

    @Test
    fun `isInvincible returns correct condition`() {
        val game =
            gbGame("IsInvincibleTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.isInvincible) {
                                // do something when invincible
                            }
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        // Should emit an IRIf with IREntityIsInvincible condition
        val hasCondition =
            scene.onFrame.any { stmt ->
                stmt is IRIf && (stmt.condition as? IREntityIsInvincible) != null
            }
        assertTrue(hasCondition, "Should check isInvincible condition")
    }

    @Test
    fun `kill emits IR`() {
        val game =
            gbGame("KillIRTest") {
                val enemy by entity {
                    position(100, 72)
                    combat { maxHp(50) }
                }

                start = scene("main") { every.frame { enemy.kill() } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IREntityDeath }
        assertTrue(hasIR, "Should emit IREntityDeath")
    }

    @Test
    fun `isStunned returns correct condition`() {
        val game =
            gbGame("IsStunnedTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.isStunned) {
                                // do something when stunned
                            }
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        // Should emit an IRIf with IREntityIsStunned condition
        val hasCondition =
            scene.onFrame.any { stmt ->
                stmt is IRIf && (stmt.condition as? IREntityIsStunned) != null
            }
        assertTrue(hasCondition, "Should check isStunned condition")
    }

    @Test
    fun `canAct returns correct condition`() {
        val game =
            gbGame("CanActTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.canAct) {
                                // do something when can act
                            }
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        // Should emit an IRIf with IREntityCanAct condition
        val hasCondition =
            scene.onFrame.any { stmt ->
                stmt is IRIf && (stmt.condition as? IREntityCanAct) != null
            }
        assertTrue(hasCondition, "Should check canAct condition")
    }

    @Test
    fun `hasKnockback returns correct condition`() {
        val game =
            gbGame("HasKnockbackTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.hasKnockback) {
                                // do something when knocked back
                            }
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        // Should emit an IRIf with IREntityHasKnockback condition
        val hasCondition =
            scene.onFrame.any { stmt ->
                stmt is IRIf && (stmt.condition as? IREntityHasKnockback) != null
            }
        assertTrue(hasCondition, "Should check hasKnockback condition")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `combat generates state variables`() {
        val game =
            gbGame("CombatCodegenVarsTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_hp"), "Should generate HP var")
        assertTrue(code.contains("player_hp_max"), "Should generate max HP var")
        assertTrue(code.contains("player_invincible"), "Should generate invincible var")
        assertTrue(code.contains("player_invincible_timer"), "Should generate invincible timer var")
        assertTrue(code.contains("player_stun_timer"), "Should generate stun timer var")
        assertTrue(code.contains("player_knockback_x"), "Should generate knockback X var")
        assertTrue(code.contains("player_knockback_y"), "Should generate knockback Y var")
    }

    @Test
    fun `combat generates helper functions`() {
        val game =
            gbGame("CombatCodegenFunctionsTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_player_deal_damage"), "Should generate deal_damage function")
        assertTrue(code.contains("_player_heal"), "Should generate heal function")
        assertTrue(code.contains("_player_combat_update"), "Should generate combat_update function")
    }

    @Test
    fun `combat generates constants`() {
        val game =
            gbGame("CombatCodegenConstantsTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        attackPower(15)
                        defense(5)
                        invincibilityFrames(60)
                        knockbackForce(4)
                        team(CombatTeam.PLAYER)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("PLAYER_MAX_HP 100u"), "Should generate MAX_HP constant")
        assertTrue(
            code.contains("PLAYER_ATTACK_POWER 15u"),
            "Should generate ATTACK_POWER constant",
        )
        assertTrue(code.contains("PLAYER_DEFENSE 5u"), "Should generate DEFENSE constant")
        assertTrue(
            code.contains("PLAYER_INVINCIBILITY_FRAMES 60u"),
            "Should generate INVINCIBILITY_FRAMES constant",
        )
        assertTrue(
            code.contains("PLAYER_KNOCKBACK_FORCE 4"),
            "Should generate KNOCKBACK_FORCE constant",
        )
    }

    @Test
    fun `combat generates team constants`() {
        val game =
            gbGame("CombatTeamConstantsTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        team(CombatTeam.PLAYER)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("TEAM_PLAYER"), "Should generate TEAM_PLAYER constant")
        assertTrue(code.contains("TEAM_ENEMY"), "Should generate TEAM_ENEMY constant")
        assertTrue(code.contains("TEAM_NEUTRAL"), "Should generate TEAM_NEUTRAL constant")
        assertTrue(code.contains("TEAM_HAZARD"), "Should generate TEAM_HAZARD constant")
    }

    // =========================================================================
    // HITBOX-BASED DAMAGE DEALING
    // =========================================================================

    @Test
    fun `collidesWith emits collision check IR`() {
        val game =
            gbGame("CollidesWithIRTest") {
                val player by entity {
                    position(80, 72)
                    hitbox(0, 0, 8, 16)
                    combat { maxHp(100) }
                }
                val enemy by entity {
                    position(100, 72)
                    hitbox(0, 0, 8, 8)
                    combat { maxHp(50) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player collidesWith enemy) {
                                dealDamage(to = enemy, amount = 10)
                            }
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        // Check that there's an IRIf with collision-related condition
        // The existing collidesWith implementation uses inline AABB checks
        val hasCollisionCheck = scene.onFrame.any { stmt -> stmt is IRIf }
        assertTrue(hasCollisionCheck, "Should emit IRIf for collision check")

        // Verify the damage is in the then block
        val ifStmt = scene.onFrame.filterIsInstance<IRIf>().firstOrNull()
        assertNotNull(ifStmt)
        val hasDamage = ifStmt.then.any { it is IRActionDamage }
        assertTrue(hasDamage, "Should emit damage in then block")
    }

    @Test
    fun `damageOnCollision emits IR`() {
        val game =
            gbGame("DamageOnCollisionIRTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        attackPower(15)
                        team(CombatTeam.PLAYER)
                    }
                }
                val enemy by entity {
                    position(100, 72)
                    combat {
                        maxHp(50)
                        team(CombatTeam.ENEMY)
                    }
                }

                start = scene("main") { every.frame { player.damageOnCollision(enemy) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRDamageOnCollision &&
                    stmt.attackerEntity == "player" &&
                    stmt.targetEntity == "enemy"
            }
        assertTrue(hasIR, "Should emit IRDamageOnCollision")
    }

    @Test
    fun `damageOnCollision with specific damage emits IR`() {
        val game =
            gbGame("DamageOnCollisionAmountIRTest") {
                val fireball by entity {
                    position(80, 72)
                    combat {
                        maxHp(1)
                        team(CombatTeam.PLAYER)
                    }
                }
                val enemy by entity {
                    position(100, 72)
                    combat {
                        maxHp(50)
                        team(CombatTeam.ENEMY)
                    }
                }

                start =
                    scene("main") { every.frame { fireball.damageOnCollision(enemy, damage = 25) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRDamageOnCollision &&
                    stmt.attackerEntity == "fireball" &&
                    stmt.targetEntity == "enemy" &&
                    stmt.damage == 25
            }
        assertTrue(hasIR, "Should emit IRDamageOnCollision with damage = 25")
    }

    @Test
    fun `damageOnCollision generates collision and damage code`() {
        val game =
            gbGame("DamageOnCollisionCodegenTest") {
                val player by entity {
                    position(80, 72)
                    hitbox(0, 0, 8, 16)
                    combat {
                        maxHp(100)
                        attackPower(15)
                        team(CombatTeam.PLAYER)
                    }
                }
                val enemy by entity {
                    position(100, 72)
                    hitbox(0, 0, 8, 8)
                    combat {
                        maxHp(50)
                        knockbackForce(4)
                        team(CombatTeam.ENEMY)
                    }
                }

                start = scene("main") { every.frame { player.damageOnCollision(enemy) } }
            }

        val code = game.compileForTest()

        // Should generate collision check
        assertTrue(code.contains("player_x"), "Should reference player position")
        assertTrue(code.contains("enemy_x"), "Should reference enemy position")

        // Should generate team check
        assertTrue(code.contains("PLAYER_TEAM"), "Should reference attacker team")
        assertTrue(code.contains("ENEMY_TEAM"), "Should reference target team")

        // Should call damage function
        assertTrue(code.contains("_enemy_deal_damage"), "Should call enemy damage function")
    }

    @Test
    fun `canDamage returns correct condition`() {
        val game =
            gbGame("CanDamageTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        team(CombatTeam.PLAYER)
                    }
                }
                val enemy by entity {
                    position(100, 72)
                    combat {
                        maxHp(50)
                        team(CombatTeam.ENEMY)
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.canDamage(enemy)) { player.dealDamage(to = enemy) }
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasCanDamageCheck =
            scene.onFrame.any { stmt -> stmt is IRIf && (stmt.condition as? IRCanDamage) != null }
        assertTrue(hasCanDamageCheck, "Should emit IRCanDamage in condition")
    }

    @Test
    fun `entity collision generates AABB check code`() {
        val game =
            gbGame("EntityCollisionCodegenTest") {
                val player by entity {
                    position(80, 72)
                    hitbox(0, 0, 8, 16)
                    combat { maxHp(100) }
                }
                val enemy by entity {
                    position(100, 72)
                    hitbox(2, 2, 4, 4)
                    combat { maxHp(50) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player collidesWith enemy) {
                                dealDamage(to = enemy, amount = 10)
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate collision check code
        assertTrue(code.contains("player_x"), "Should reference player position in collision check")
        assertTrue(code.contains("enemy_x"), "Should reference enemy position in collision check")
        assertTrue(code.contains("if"), "Should generate if statement for collision")
        assertTrue(
            code.contains("_enemy_deal_damage"),
            "Should call damage function in collision handler",
        )
    }

    // =========================================================================
    // STUN AND KNOCKBACK STATE QUERY CODE GENERATION
    // =========================================================================

    @Test
    fun `isStunned generates correct code`() {
        val game =
            gbGame("IsStunnedCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.isStunned) {
                                // do nothing - just test condition codegen
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate stun timer check
        assertTrue(code.contains("player_stun_timer > 0u"), "Should check stun timer > 0")
    }

    @Test
    fun `canAct generates correct code`() {
        val game =
            gbGame("CanActCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.canAct) {
                                // do nothing - just test condition codegen
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate stun timer == 0 check
        assertTrue(code.contains("player_stun_timer == 0u"), "Should check stun timer == 0")
    }

    @Test
    fun `hasKnockback generates correct code`() {
        val game =
            gbGame("HasKnockbackCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.hasKnockback) {
                                // do nothing - just test condition codegen
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate knockback check
        assertTrue(
            code.contains("player_knockback_x != 0") || code.contains("player_knockback_y != 0"),
            "Should check knockback values",
        )
    }

    @Test
    fun `isInvincible generates correct code`() {
        val game =
            gbGame("IsInvincibleCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start =
                    scene("main") {
                        every.frame {
                            whenever(player.combatComponent!!.isInvincible) {
                                // do nothing - just test condition codegen
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate invincibility check
        assertTrue(code.contains("player_invincible != 0u"), "Should check invincible flag")
    }

    @Test
    fun `startInvincibility generates correct code`() {
        val game =
            gbGame("StartInvincibilityCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat { maxHp(100) }
                }

                start = scene("main") { every.frame { player.startInvincibility(frames = 45) } }
            }

        val code = game.compileForTest()

        // Should set invincibility flag and timer
        assertTrue(code.contains("player_invincible = 1u"), "Should set invincible flag to 1")
        assertTrue(code.contains("player_invincible_timer = 45u"), "Should set invincible timer")
    }

    // =========================================================================
    // COMBAT ANIMATIONS INTEGRATION
    // =========================================================================

    @Test
    fun `onHit callback is recorded`() {
        val game =
            gbGame("OnHitCallbackTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        onHit { raw("// Hit callback executed") }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "player" }
        assertNotNull(entity)
        assertTrue(
            entity.combatComponent!!.onHitStatements.isNotEmpty(),
            "Should have onHit statements",
        )
    }

    @Test
    fun `onDeath callback is recorded`() {
        val game =
            gbGame("OnDeathCallbackTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        onDeath { raw("// Death callback executed") }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "player" }
        assertNotNull(entity)
        assertTrue(
            entity.combatComponent!!.onDeathStatements.isNotEmpty(),
            "Should have onDeath statements",
        )
    }

    @Test
    fun `blinkOnDamage is configurable`() {
        val game =
            gbGame("BlinkConfigTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        blinkOnDamage(true, interval = 8)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = requireNotNull(game.entities.find { it.name == "player" })
        val combat = requireNotNull(entity.combatComponent)
        assertTrue(combat.blinkOnDamage, "Should have blink enabled")
        assertEquals(8, combat.blinkInterval, "Should have custom blink interval")
    }

    @Test
    fun `blinkOnDamage generates blink code`() {
        val game =
            gbGame("BlinkCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        invincibilityFrames(60)
                        blinkOnDamage(true, interval = 4)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate blink effect code
        assertTrue(code.contains("Blink sprite during invincibility"), "Should have blink comment")
        // Blink uses move_sprite to hide (Y=0) and restore position
        assertTrue(
            code.contains("move_sprite") && code.contains("0, 0"),
            "Should move sprite off-screen to hide",
        )
        assertTrue(code.contains("Restore position"), "Should restore sprite position when visible")
    }

    @Test
    fun `disabled blinkOnDamage does not generate blink code`() {
        val game =
            gbGame("NoBlinkCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        invincibilityFrames(60)
                        blinkOnDamage(false)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should NOT generate blink effect code in combat update
        assertFalse(
            code.contains("Blink sprite during invincibility"),
            "Should not have blink comment when disabled",
        )
    }

    @Test
    fun `onHit callback generates code in damage function`() {
        val game =
            gbGame("OnHitCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        onHit { raw("play_hit_sound();") }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate onHit callback code
        assertTrue(code.contains("onHit callback"), "Should have onHit comment")
        assertTrue(code.contains("play_hit_sound();"), "Should include hit callback code")
    }

    @Test
    fun `onDeath callback generates code in damage function`() {
        val game =
            gbGame("OnDeathCodegenTest") {
                val player by entity {
                    position(80, 72)
                    combat {
                        maxHp(100)
                        onDeath { raw("play_death_animation();") }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate onDeath callback code
        assertTrue(code.contains("onDeath callback"), "Should have onDeath comment")
        assertTrue(code.contains("play_death_animation();"), "Should include death callback code")
    }
}
