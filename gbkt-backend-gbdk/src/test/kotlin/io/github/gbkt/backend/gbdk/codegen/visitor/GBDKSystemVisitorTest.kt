/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.ActorPoolConfig
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.DestroyActor
import io.github.gbkt.core.ir.DialogSystem
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.FlagPageIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.GlobalFlagsIR
import io.github.gbkt.core.ir.PoolOverflowStrategy
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.SoundSystem
import io.github.gbkt.core.ir.SpawnActor
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.StatusEffectDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// =============================================================================
// GBDK SYSTEM VISITOR TESTS
// Verifies that GBDKSystemVisitor handles all 6 system types correctly,
// SpawnActor/DestroyActor generate real OAM management code,
// and buildSystemFunctions no longer filters to GenericSystem only.
// =============================================================================

class GBDKSystemVisitorTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    // =========================================================================
    // CameraSystem — generates update_camera function
    // =========================================================================

    @Test
    fun `visitCameraSystem generates update_camera function`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val cameraSystem = CameraSystem(id = "main_camera")

        val functions = visitor.visitCameraSystem(cameraSystem)

        assertTrue(functions.isNotEmpty(), "CameraSystem should generate at least one function")
        val names = functions.map { it.name }
        assertTrue(
            names.any { it.contains("update_camera") },
            "CameraSystem should generate an update_camera function, got: $names",
        )
    }

    @Test
    fun `visitCameraSystem function body references SCX_REG and SCY_REG`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val cameraSystem = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(cameraSystem)
        val updateCamera = functions.first { it.name.contains("update_camera") }

        val allRawCode = collectRawCode(updateCamera)
        assertTrue(allRawCode.any { it.contains("SCX_REG") }, "Camera body should contain SCX_REG")
        assertTrue(allRawCode.any { it.contains("SCY_REG") }, "Camera body should contain SCY_REG")
    }

    // =========================================================================
    // SaveSystem — generates save_game and load_game functions
    // =========================================================================

    @Test
    fun `visitSaveSystem generates save_game and load_game functions`() {
        val gameWithVars =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameWithVars)
        val saveSystem = SaveSystem(id = "save_slot1")

        val functions = visitor.visitSaveSystem(saveSystem)

        assertTrue(
            functions.size >= 2,
            "SaveSystem should generate at least 2 functions, got: ${functions.size}",
        )
        val names = functions.map { it.name }
        assertTrue(
            names.any { it.contains("save_game") },
            "Should generate save_game function, got: $names",
        )
        assertTrue(
            names.any { it.contains("load_game") },
            "Should generate load_game function, got: $names",
        )
    }

    @Test
    fun `visitSaveSystem save_game body writes to SRAM with ENABLE_RAM`() {
        val gameWithVars =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameWithVars)
        val saveSystem = SaveSystem(id = "saves")

        val functions = visitor.visitSaveSystem(saveSystem)
        val saveGame = functions.first { it.name.contains("save_game") }

        // Emit all statements to C text for inspection (typed AST uses CRawExpr for 0xA000 cast)
        val emittedBody =
            saveGame.body.joinToString("\n") { stmt ->
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(stmt)
            }

        assertTrue(
            emittedBody.contains("0xA000"),
            "save_game should reference SRAM address 0xA000, got:\n$emittedBody",
        )
        assertTrue(
            emittedBody.contains("ENABLE_RAM"),
            "save_game should call ENABLE_RAM, got:\n$emittedBody",
        )
        assertTrue(
            emittedBody.contains("DISABLE_RAM"),
            "save_game should call DISABLE_RAM, got:\n$emittedBody",
        )
        assertTrue(
            emittedBody.contains("_score"),
            "save_game should write _score variable, got:\n$emittedBody",
        )
    }

    // =========================================================================
    // SoundSystem — returns empty list (sound handled by buildSoundFunctions)
    // =========================================================================

    @Test
    fun `visitSoundSystem returns empty list to avoid duplicate sound function generation`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val soundSystem = SoundSystem(id = "sound_engine")

        val functions = visitor.visitSoundSystem(soundSystem)

        assertTrue(
            functions.isEmpty(),
            "SoundSystem should return empty list — sound handled by buildSoundFunctions",
        )
    }

    // =========================================================================
    // ExplorationSystem — generates exploration_move function
    // =========================================================================

    @Test
    fun `visitExplorationSystem generates exploration_move function`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val explorationSystem = ExplorationSystem(id = "dungeon_explore")

        val functions = visitor.visitExplorationSystem(explorationSystem)

        assertTrue(
            functions.isNotEmpty(),
            "ExplorationSystem should generate at least one function",
        )
        val names = functions.map { it.name }
        assertTrue(
            names.any { it.contains("exploration_move") },
            "ExplorationSystem should generate exploration_move function, got: $names",
        )
    }

    @Test
    fun `visitExplorationSystem function body reads dpad input and updates player position`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val explorationSystem = ExplorationSystem(id = "explore")

        val functions = visitor.visitExplorationSystem(explorationSystem)
        val moveFunc = functions.first { it.name.contains("exploration_move") }
        val allRawCode = collectRawCode(moveFunc)

        assertTrue(
            allRawCode.any { it.contains("dpad_held") },
            "exploration_move should read dpad input",
        )
        assertTrue(
            allRawCode.any { it.contains("_player_x") },
            "exploration_move should update _player_x",
        )
        assertTrue(
            allRawCode.any { it.contains("_player_y") },
            "exploration_move should update _player_y",
        )
    }

    // =========================================================================
    // DialogSystem — returns empty list (dialog handled by buildDialogHelpers)
    // =========================================================================

    @Test
    fun `visitDialogSystem returns empty list to avoid duplicate dialog function generation`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val dialogSystem = DialogSystem(id = "dialog_engine")

        val functions = visitor.visitDialogSystem(dialogSystem)

        assertTrue(
            functions.isEmpty(),
            "DialogSystem should return empty list — dialog handled by buildDialogHelpers",
        )
    }

    // =========================================================================
    // CombatEngineSystem (TURN_BASED / simpleBattle migration) — generates state machine
    // =========================================================================

    @Test
    fun `visitCombatEngineSystem TURN_BASED generates update_combat and trigger functions`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val battleSystem =
            CombatEngineSystem(id = "main_battle", combatType = CombatType.TURN_BASED)

        val functions = visitor.visitCombatEngineSystem(battleSystem)

        assertTrue(
            functions.isNotEmpty(),
            "TURN_BASED CombatEngineSystem should generate functions",
        )
        val names = functions.map { it.name }
        assertTrue(
            names.any { it.contains("update_combat") },
            "Should generate update_combat function, got: $names",
        )
        assertTrue(
            names.any { it.contains("trigger_") || it.contains("update_combat") },
            "Should generate trigger or combat function, got: $names",
        )
    }

    @Test
    fun `visitCombatEngineSystem TURN_BASED generates combat state machine functions`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val battleSystem = CombatEngineSystem(id = "combat", combatType = CombatType.TURN_BASED)

        val functions = visitor.visitCombatEngineSystem(battleSystem)
        assertTrue(
            functions.isNotEmpty(),
            "TURN_BASED combat should generate at least one function",
        )

        // CombatVisitor generates update_combat_<id> via generateCombatFunctions
        val names = functions.map { it.name }
        assertTrue(
            names.any { it.contains("combat") },
            "Should generate combat-related functions, got: $names",
        )
    }

    @Test
    fun `visitCombatEngineSystem TURN_BASED generates combat_request_state helper`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val battleSystem = CombatEngineSystem(id = "combat", combatType = CombatType.TURN_BASED)

        val functions = visitor.visitCombatEngineSystem(battleSystem)
        val names = functions.map { it.name }

        assertTrue(
            names.any { it.contains("combat") },
            "Should generate combat-related functions, got: $names",
        )
    }

    @Test
    fun `visitGenericSystem unknown type generates trigger no-op stub`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val genericSystem =
            GenericSystem(id = "custom_system", config = mapOf("type" to "unknown_type"))

        val functions = visitor.visitGenericSystem(genericSystem)

        assertTrue(functions.isNotEmpty(), "Generic system should generate at least one function")
        val names = functions.map { it.name }
        assertTrue(
            names.any { it.contains("trigger_") },
            "Unknown type should generate trigger_ stub, got: $names",
        )
    }

    // =========================================================================
    // buildSystemFunctions dispatch — no longer filters to GenericSystem only
    // =========================================================================

    @Test
    fun `buildSystemFunctions dispatches all system types via GBDKSystemVisitor`() {
        val gameWithAllSystems =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                systems =
                    listOf(
                        CameraSystem(id = "camera"),
                        SaveSystem(id = "saves"),
                        SoundSystem(id = "sound"),
                        ExplorationSystem(id = "exploration"),
                        DialogSystem(id = "dialog"),
                        // simpleBattle now produces CombatEngineSystem (TURN_BASED)
                        CombatEngineSystem(id = "battle", combatType = CombatType.TURN_BASED),
                    ),
            )
        val output = GBDKPipeline().generate(gameWithAllSystems)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // CameraSystem generates update_camera
        assertTrue(
            mainC.contains("update_camera_camera"),
            "CameraSystem should generate update_camera function",
        )
        // SaveSystem generates save_game/load_game
        assertTrue(
            mainC.contains("save_game_saves"),
            "SaveSystem should generate save_game function",
        )
        assertTrue(
            mainC.contains("load_game_saves"),
            "SaveSystem should generate load_game function",
        )
        // CombatEngineSystem(TURN_BASED) generates update_combat
        assertTrue(
            mainC.contains("update_combat_battle"),
            "CombatEngineSystem(TURN_BASED) should generate update_combat",
        )
    }

    @Test
    fun `CameraSystem does not generate no-op stub`() {
        val gameWithCamera =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                systems = listOf(CameraSystem(id = "cam")),
            )
        val output = GBDKPipeline().generate(gameWithCamera)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("/* system 'cam' has no v2 implementation — no-op stub */"),
            "CameraSystem should generate real code, not a no-op stub",
        )
        assertTrue(
            mainC.contains("update_camera_cam"),
            "CameraSystem should generate update_camera_cam function",
        )
    }

    // =========================================================================
    // SpawnActor/DestroyActor — OAM slot management (not comment stubs)
    // =========================================================================

    @Test
    fun `SpawnActor generates OAM slot claim code not comment stub`() {
        val op = SpawnActor(actorId = "player")

        val result = ScriptOpVisitor.visit(op)

        val block = assertIs<CBlock>(result)
        val allCode = collectRawCodeFromStatements(block.statements)
        assertFalse(
            allCode.any { it.contains("deferred") },
            "SpawnActor should not generate 'deferred' comment stub",
        )
        assertTrue(
            allCode.any { it.contains("spawn_actor") },
            "SpawnActor should call spawn_actor, got: $allCode",
        )
    }

    @Test
    fun `SpawnActor sets actor oam_slot global variable`() {
        val op = SpawnActor(actorId = "enemy")

        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)
        val allCode = collectRawCodeFromStatements(block.statements)

        assertTrue(
            allCode.any { it.contains("_enemy_oam_slot") },
            "SpawnActor should set _enemy_oam_slot global, got: $allCode",
        )
    }

    @Test
    fun `DestroyActor generates OAM slot release code not comment stub`() {
        val op = DestroyActor(actorId = "player")

        val result = ScriptOpVisitor.visit(op)

        val block = assertIs<CBlock>(result)
        val allCode = collectRawCodeFromStatements(block.statements)
        assertFalse(
            allCode.any { it.contains("deferred") },
            "DestroyActor should not generate 'deferred' comment stub",
        )
        assertTrue(
            allCode.any { it.contains("destroy_actor") },
            "DestroyActor should call destroy_actor, got: $allCode",
        )
    }

    @Test
    fun `DestroyActor resets actor oam_slot to 0xFF`() {
        val op = DestroyActor(actorId = "enemy")

        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)
        val allCode = collectRawCodeFromStatements(block.statements)

        assertTrue(
            allCode.any { it.contains("_enemy_oam_slot") },
            "DestroyActor should reference _enemy_oam_slot, got: $allCode",
        )
        assertTrue(
            allCode.any { it.contains("0xFF") },
            "DestroyActor should reset slot to 0xFF, got: $allCode",
        )
    }

    // =========================================================================
    // Actor pool codegen: buildActorPoolFunctions and buildActorPoolStateVars
    // =========================================================================

    @Test
    fun `buildActorPoolFunctions returns empty list when no pools`() {
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(emptyGameIR)
        assertTrue(functions.isEmpty(), "No pools in GameIR should produce no functions")
    }

    @Test
    fun `buildActorPoolStateVars returns empty list when no pools`() {
        val vars = GBDKSystemVisitor.buildActorPoolStateVars(emptyGameIR)
        assertTrue(vars.isEmpty(), "No pools in GameIR should produce no state vars")
    }

    @Test
    fun `buildActorPoolFunctions generates init spawn destroy for one pool`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)

        assertEquals(
            4,
            functions.size,
            "Should produce init, spawn, destroy, active_count for one pool",
        )
        val names = functions.map { it.name }
        assertTrue(names.contains("pool_bullets_init"), "Should generate pool_bullets_init")
        assertTrue(names.contains("pool_bullets_spawn"), "Should generate pool_bullets_spawn")
        assertTrue(names.contains("pool_bullets_destroy"), "Should generate pool_bullets_destroy")
    }

    @Test
    fun `buildActorPoolStateVars generates active and per-instance arrays for one pool`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                        )
                    ),
            )

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)

        assertEquals(4, vars.size, "Should produce active + x + y + oam arrays (4 total)")
        val names = vars.map { it.name }
        assertTrue(names.contains("_pool_bullets_active"), "Should declare _pool_bullets_active")
        assertTrue(names.contains("_pool_bullets_x"), "Should declare _pool_bullets_x")
        assertTrue(names.contains("_pool_bullets_y"), "Should declare _pool_bullets_y")
        assertTrue(names.contains("_pool_bullets_oam"), "Should declare _pool_bullets_oam")
        assertFalse(
            names.contains("_pool_bullets_oam_base"),
            "Should NOT declare _pool_bullets_oam_base (replaced by per-instance array)",
        )
    }

    @Test
    fun `buildActorPoolFunctions SILENT_NOOP spawn returns 0xFF when pool full`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config =
                                ActorPoolConfig(
                                    maxSize = 4,
                                    overflowStrategy = PoolOverflowStrategy.SILENT_NOOP,
                                ),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val spawnFn = functions.first { it.name == "pool_bullets_spawn" }
        val emitted = spawnFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertTrue(
            emitted.contains("0xFF"),
            "SILENT_NOOP spawn should return 0xFF when pool is full",
        )
        assertTrue(
            !emitted.contains("_oldest"),
            "SILENT_NOOP spawn should not contain oldest-slot recycling logic",
        )
    }

    @Test
    fun `buildActorPoolFunctions RECYCLE_OLDEST spawn tracks oldest slot`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "sparks",
                            actorTemplateId = "spark",
                            config =
                                ActorPoolConfig(
                                    maxSize = 4,
                                    overflowStrategy = PoolOverflowStrategy.RECYCLE_OLDEST,
                                ),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val spawnFn = functions.first { it.name == "pool_sparks_spawn" }
        val emitted = spawnFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertTrue(
            emitted.contains("_oldest"),
            "RECYCLE_OLDEST spawn should contain oldest-slot tracking, got:\n$emitted",
        )
    }

    @Test
    fun `buildActorPoolFunctions destroy guards against out-of-bounds slot index`() {
        // Static OAM assignment: the old 0xFF sentinel guard is removed (no dynamic free list).
        // Destroy now only guards against i >= maxSize (not i == 0xFF).
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val destroyFn = functions.first { it.name == "pool_bullets_destroy" }
        val emitted = destroyFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertFalse(
            emitted.contains("0xFF"),
            "Destroy should NOT contain 0xFF sentinel guard (static OAM, no free list), got:\n$emitted",
        )
        assertTrue(
            emitted.contains(">= 8"),
            "Destroy should guard against i >= maxSize (8) for bounds safety, got:\n$emitted",
        )
    }

    @Test
    fun `buildActorPoolFunctions generates functions for multiple pools`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                        ),
                        ActorPoolIR(
                            id = "sparks",
                            actorTemplateId = "spark",
                            config =
                                ActorPoolConfig(
                                    maxSize = 4,
                                    overflowStrategy = PoolOverflowStrategy.RECYCLE_OLDEST,
                                ),
                        ),
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)

        // 4 functions per pool (init, spawn, destroy, active_count) = 8 total
        assertEquals(8, functions.size, "Two pools should produce 8 lifecycle functions")
        val names = functions.map { it.name }
        assertTrue(names.contains("pool_bullets_init"))
        assertTrue(names.contains("pool_bullets_spawn"))
        assertTrue(names.contains("pool_bullets_destroy"))
        assertTrue(names.contains("pool_sparks_init"))
        assertTrue(names.contains("pool_sparks_spawn"))
        assertTrue(names.contains("pool_sparks_destroy"))
    }

    // =========================================================================
    // GAP-04: Level-Progressive Encounter Tables
    // =========================================================================

    @Test
    fun `encounter check emits level range guard when minLevel is set`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                encounterTable =
                    io.github.gbkt.core.ir.EncounterTableIR(
                        entries =
                            listOf(
                                io.github.gbkt.core.ir.EncounterEntryIR(
                                    id = "goblin_lv5",
                                    weight = 30,
                                    minLevel = 5,
                                ),
                                io.github.gbkt.core.ir.EncounterEntryIR(
                                    id = "goblin_lv1",
                                    weight = 30,
                                ),
                            )
                    ),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val encounterCheck = functions.firstOrNull {
            it.name == "exploration_encounter_check_dungeon"
        }

        requireNotNull(encounterCheck) { "exploration_encounter_check_dungeon function not found" }
        val emitted = collectRawCode(encounterCheck).joinToString(" ")
        assertTrue(
            emitted.contains("_player_level") && emitted.contains(">=") && emitted.contains("5"),
            "Level guard should emit _player_level >= 5, got: $emitted",
        )
    }

    @Test
    fun `encounter check emits level range guard when maxLevel is set`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                encounterTable =
                    io.github.gbkt.core.ir.EncounterTableIR(
                        entries =
                            listOf(
                                io.github.gbkt.core.ir.EncounterEntryIR(
                                    id = "weak_goblin",
                                    weight = 40,
                                    maxLevel = 9,
                                )
                            )
                    ),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val encounterCheck = functions.firstOrNull {
            it.name == "exploration_encounter_check_dungeon"
        }

        requireNotNull(encounterCheck) { "exploration_encounter_check_dungeon function not found" }
        val emitted = collectRawCode(encounterCheck).joinToString(" ")
        assertTrue(
            emitted.contains("_player_level") && emitted.contains("<") && emitted.contains("9"),
            "Level guard should emit _player_level < 9, got: $emitted",
        )
    }

    @Test
    fun `encounter check emits both minLevel and maxLevel guards when both are set`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                encounterTable =
                    io.github.gbkt.core.ir.EncounterTableIR(
                        entries =
                            listOf(
                                io.github.gbkt.core.ir.EncounterEntryIR(
                                    id = "mid_goblin",
                                    weight = 30,
                                    minLevel = 5,
                                    maxLevel = 9,
                                )
                            )
                    ),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val encounterCheck = functions.firstOrNull {
            it.name == "exploration_encounter_check_dungeon"
        }

        requireNotNull(encounterCheck) { "exploration_encounter_check_dungeon function not found" }
        val emitted = collectRawCode(encounterCheck).joinToString(" ")
        assertTrue(
            emitted.contains("_player_level") && emitted.contains(">=") && emitted.contains("5"),
            "Should emit minLevel guard _player_level >= 5, got: $emitted",
        )
        assertTrue(
            emitted.contains("_player_level") && emitted.contains("<") && emitted.contains("9"),
            "Should emit maxLevel guard _player_level < 9, got: $emitted",
        )
    }

    // =========================================================================
    // GAP-07: Zone Edge Transitions with Condition Gates
    // =========================================================================

    @Test
    fun `zone_check_edges function is generated when zones have edge transitions`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                transitions =
                    listOf(
                        io.github.gbkt.core.ir.ZoneTransitionIR(
                            targetZoneId = "floor2",
                            edge = io.github.gbkt.core.ir.TransitionEdge.NORTH,
                        )
                    ),
            )
        val floor2 = io.github.gbkt.core.ir.ZoneIR(id = "floor2", name = "Floor 2")
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone, floor2))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val edgeCheck = functions.firstOrNull { it.name == "zone_check_edges_dungeon" }

        assertNotNull(edgeCheck, "zone_check_edges_dungeon function should be generated")
    }

    @Test
    fun `zone_check_edges is NOT generated when no zones have edge transitions`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                transitions = emptyList(),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val edgeCheck = functions.firstOrNull { it.name == "zone_check_edges_dungeon" }

        assertTrue(edgeCheck == null, "zone_check_edges_dungeon should NOT be generated")
    }

    @Test
    fun `zone_check_edges emits conditionFlag guard when transition has conditionFlag`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                transitions =
                    listOf(
                        io.github.gbkt.core.ir.ZoneTransitionIR(
                            targetZoneId = "floor2",
                            edge = io.github.gbkt.core.ir.TransitionEdge.NORTH,
                            conditionFlag = "bossDefeated",
                        )
                    ),
            )
        val floor2 = io.github.gbkt.core.ir.ZoneIR(id = "floor2", name = "Floor 2")
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone, floor2))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val edgeCheck = functions.firstOrNull { it.name == "zone_check_edges_dungeon" }

        requireNotNull(edgeCheck) { "zone_check_edges_dungeon function not found" }
        val emitted = collectRawCode(edgeCheck).joinToString(" ")
        assertTrue(
            emitted.contains("_flag_bossDefeated"),
            "Edge check should emit _flag_bossDefeated guard, got: $emitted",
        )
    }

    @Test
    fun `exploration_step calls zone_check_edges when zones have transitions`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                transitions =
                    listOf(
                        io.github.gbkt.core.ir.ZoneTransitionIR(
                            targetZoneId = "floor2",
                            edge = io.github.gbkt.core.ir.TransitionEdge.SOUTH,
                        )
                    ),
            )
        val floor2 = io.github.gbkt.core.ir.ZoneIR(id = "floor2", name = "Floor 2")
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone, floor2))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val stepFn = functions.firstOrNull { it.name == "exploration_step_dungeon" }

        requireNotNull(stepFn) { "exploration_step_dungeon not found" }
        val emitted = collectRawCode(stepFn).joinToString(" ")
        assertTrue(
            emitted.contains("zone_check_edges_dungeon"),
            "exploration_step should call zone_check_edges_dungeon, got: $emitted",
        )
    }

    // =========================================================================
    // GAP-06: Zone Object DSL — Per-Floor Scripted Object Callbacks
    // =========================================================================

    @Test
    fun `zone_try_interact function generated for zone with chest object`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                objects =
                    listOf(
                        io.github.gbkt.core.ir.ChestObjectIR(
                            id = "chest1",
                            x = 5,
                            y = 3,
                            usedFlagId = "chest1_opened",
                        )
                    ),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val dispatchFn = functions.firstOrNull { it.name == "zone_try_interact_floor1" }
        val chestFn = functions.firstOrNull { it.name == "zone_chest_floor1_chest1_interact" }

        assertNotNull(dispatchFn, "zone_try_interact_floor1 dispatch function should be generated")
        assertNotNull(chestFn, "zone_chest_floor1_chest1_interact handler should be generated")
    }

    @Test
    fun `chest handler emits used-flag guard to prevent re-opening`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                objects =
                    listOf(
                        io.github.gbkt.core.ir.ChestObjectIR(
                            id = "chest1",
                            x = 5,
                            y = 3,
                            usedFlagId = "chest1_opened",
                        )
                    ),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val chestFn = functions.firstOrNull { it.name == "zone_chest_floor1_chest1_interact" }

        requireNotNull(chestFn) { "zone_chest_floor1_chest1_interact not found" }
        val emitted = collectRawCode(chestFn).joinToString(" ")
        assertTrue(
            emitted.contains("_flag_chest1_opened"),
            "Chest handler should check _flag_chest1_opened used-flag, got: $emitted",
        )
    }

    @Test
    fun `dispatch function checks position before routing to object handler`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                objects = listOf(io.github.gbkt.core.ir.SignObjectIR(id = "sign1", x = 2, y = 8)),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val dispatchFn = functions.firstOrNull { it.name == "zone_try_interact_floor1" }

        requireNotNull(dispatchFn) { "zone_try_interact_floor1 not found" }
        val emitted = collectRawCode(dispatchFn).joinToString(" ")
        assertTrue(
            emitted.contains("== 2") && emitted.contains("== 8"),
            "Dispatch should check position x==2, y==8, got: $emitted",
        )
        assertTrue(
            emitted.contains("zone_sign_floor1_sign1_interact"),
            "Dispatch should call zone_sign_floor1_sign1_interact, got: $emitted",
        )
    }

    @Test
    fun `sconce handler emits toggle logic with lit state variable`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                objects =
                    listOf(io.github.gbkt.core.ir.SconceObjectIR(id = "sconce1", x = 4, y = 6)),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val sconceFn = functions.firstOrNull { it.name == "zone_sconce_floor1_sconce1_interact" }

        requireNotNull(sconceFn) { "zone_sconce_floor1_sconce1_interact not found" }
        val emitted = collectRawCode(sconceFn).joinToString(" ")
        assertTrue(
            emitted.contains("_sconce_sconce1_lit"),
            "Sconce handler should reference _sconce_sconce1_lit state variable, got: $emitted",
        )
    }

    @Test
    fun `lever handler emits toggle logic with active state variable`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                objects =
                    listOf(io.github.gbkt.core.ir.LeverObjectIR(id = "gate_lever", x = 3, y = 7)),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val leverFn = functions.firstOrNull { it.name == "zone_lever_floor1_gate_lever_interact" }

        requireNotNull(leverFn) { "zone_lever_floor1_gate_lever_interact not found" }
        val emitted = collectRawCode(leverFn).joinToString(" ")
        assertTrue(
            emitted.contains("_lever_gate_lever_active"),
            "Lever handler should reference _lever_gate_lever_active state variable, got: $emitted",
        )
    }

    @Test
    fun `NPC handler emits visibility flag guard when visibleFlagId is set`() {
        val zone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "floor1",
                name = "Floor 1",
                objects =
                    listOf(
                        io.github.gbkt.core.ir.NpcObjectIR(
                            id = "elder",
                            x = 10,
                            y = 5,
                            visibleFlagId = "elderSpawned",
                        )
                    ),
            )
        val gameIR = GameIR(name = "Test", config = CartridgeConfig(), zones = listOf(zone))
        val visitor = GBDKSystemVisitor(gameIR)
        val system = ExplorationSystem(id = "dungeon")

        val functions = visitor.visitExplorationSystem(system)
        val npcFn = functions.firstOrNull { it.name == "zone_npc_floor1_elder_interact" }

        requireNotNull(npcFn) { "zone_npc_floor1_elder_interact not found" }
        val emitted = collectRawCode(npcFn).joinToString(" ")
        assertTrue(
            emitted.contains("_flag_elderSpawned"),
            "NPC handler should check _flag_elderSpawned visibility guard, got: $emitted",
        )
    }

    // =========================================================================
    // Variable declaration helpers — status effects, flags, combat state defines
    // (Plan 06.11-20: buildStatusEffectVarDecls, buildFlagVarDecls, buildCombatStateDefines)
    // =========================================================================

    @Test
    fun `game with status effects produces _effect_active _duration _stacks declarations`() {
        val statusEffectDef = StatusEffectDef(id = "poison", name = "Poison")
        val gameWithEffect =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                systems =
                    listOf(
                        GenericSystem(
                            id = "poison",
                            config = mapOf("type" to "rpg_status_effect", "def" to statusEffectDef),
                        )
                    ),
            )
        val output = GBDKPipeline().generate(gameWithEffect)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 _effect_poison_active"),
            "Status effect should produce _effect_poison_active declaration, got:\n${mainC.lines().take(60).joinToString("\n")}",
        )
        assertTrue(
            mainC.contains("UINT8 _effect_poison_duration"),
            "Status effect should produce _effect_poison_duration declaration",
        )
        assertTrue(
            mainC.contains("UINT8 _effect_poison_stacks"),
            "Status effect should produce _effect_poison_stacks declaration",
        )
    }

    @Test
    fun `game with flags produces _flag_ variable declarations`() {
        val gameWithFlags =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                flags =
                    listOf(
                        GlobalFlagsIR(
                            id = "story",
                            pages =
                                listOf(
                                    FlagPageIR(
                                        name = "chapter1",
                                        flags = listOf("bossDefeated", "chestOpened"),
                                    )
                                ),
                        )
                    ),
            )
        val output = GBDKPipeline().generate(gameWithFlags)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 _flag_bossDefeated"),
            "Flag should produce _flag_bossDefeated declaration, got:\n${mainC.lines().take(60).joinToString("\n")}",
        )
        assertTrue(
            mainC.contains("UINT8 _flag_chestOpened"),
            "Flag should produce _flag_chestOpened declaration",
        )
    }

    @Test
    fun `game with CombatEngineSystem produces _COMBAT_STATE_ defines`() {
        val gameWithCombat =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                systems =
                    listOf(CombatEngineSystem(id = "combat", combatType = CombatType.TURN_BASED)),
            )
        val output = GBDKPipeline().generate(gameWithCombat)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("#define _COMBAT_STATE_INIT 0"),
            "CombatEngineSystem should produce _COMBAT_STATE_INIT define",
        )
        assertTrue(
            mainC.contains("#define _COMBAT_STATE_PLAYER_TURN 1"),
            "CombatEngineSystem should produce _COMBAT_STATE_PLAYER_TURN define",
        )
        assertTrue(
            mainC.contains("#define _COMBAT_STATE_VICTORY 3"),
            "CombatEngineSystem should produce _COMBAT_STATE_VICTORY define",
        )
        assertTrue(
            mainC.contains("#define _COMBAT_STATE_DEFEAT 4"),
            "CombatEngineSystem should produce _COMBAT_STATE_DEFEAT define",
        )
        // Extended turn-based states (used by CombatStates.TARGET_SELECT etc.)
        assertTrue(
            mainC.contains("#define _COMBAT_STATE_FLEEING 7"),
            "TURN_BASED combat should produce _COMBAT_STATE_FLEEING extended define",
        )
    }

    @Test
    fun `game with monster definitions produces _mon_ AI variable declarations`() {
        val monsterDef =
            MonsterDef(
                id = "goblin",
                name = "Goblin",
                stats = CombatStats(hp = 30, atk = 5, def = 3),
                abilityCooldowns = mapOf("slash" to 2),
            )
        val gameWithMonster =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                systems =
                    listOf(
                        GenericSystem(
                            id = "goblin",
                            config = mapOf("type" to "rpg_monster", "def" to monsterDef),
                        )
                    ),
            )
        val output = GBDKPipeline().generate(gameWithMonster)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 _mon_goblin_hp_pct"),
            "Monster should produce _mon_goblin_hp_pct declaration",
        )
        assertTrue(
            mainC.contains("UINT8 _mon_goblin_cd_slash"),
            "Monster ability cooldown should produce _mon_goblin_cd_slash declaration",
        )
        assertTrue(
            mainC.contains("UINT8 _combat_difficulty"),
            "Monster AI should produce shared _combat_difficulty declaration",
        )
        assertTrue(
            mainC.contains("UINT8 _player_level"),
            "Monster AI should produce shared _player_level declaration",
        )
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun assertNotNull(value: Any?, message: String) {
        kotlin.test.assertNotNull(value, message)
    }

    /** Collect emitted C source strings for all statements in a CFunction's body. */
    private fun collectRawCode(fn: CFunction): List<String> = collectRawCodeFromStatements(fn.body)

    /**
     * Emit each statement in [stmts] to a C source string using [CEmitter].
     *
     * This helper was originally CRawCode-only. It now uses [CEmitter.emitStatement] so that tests
     * work against the final rendered output regardless of whether the codegen uses typed AST nodes
     * (CIf, CExprStatement, etc.) or the CRawCode escape hatch. Typed nodes were introduced in the
     * Phase 06.1-06 CRawCode elimination pass.
     */
    private fun collectRawCodeFromStatements(
        stmts: List<io.github.gbkt.backend.gbdk.codegen.ast.CStatement>
    ): List<String> = stmts.map { CEmitter.emitStatement(it) }
}
