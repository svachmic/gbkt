/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.GBDKMacros
import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CContinue
import io.github.gbkt.backend.gbdk.codegen.ast.CExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI16
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CIntLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants
import io.github.gbkt.core.dsl.ChannelGroupDef
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.ChestObjectIR
import io.github.gbkt.core.ir.CollisionResponse
import io.github.gbkt.core.ir.CollisionRuleIR
import io.github.gbkt.core.ir.CollisionShape
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.DialogSystem
import io.github.gbkt.core.ir.DoorObjectIR
import io.github.gbkt.core.ir.EncounterEntryIR
import io.github.gbkt.core.ir.EntityCollisionMode
import io.github.gbkt.core.ir.ExplorationGaugeIR
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.LeverObjectIR
import io.github.gbkt.core.ir.NpcObjectIR
import io.github.gbkt.core.ir.PathfindingSystem
import io.github.gbkt.core.ir.PoolOverflowStrategy
import io.github.gbkt.core.ir.PressurePlateObjectIR
import io.github.gbkt.core.ir.PushDirection
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.SconceObjectIR
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SignObjectIR
import io.github.gbkt.core.ir.SoundSystem
import io.github.gbkt.core.ir.SwitchObjectIR
import io.github.gbkt.core.ir.SystemIRVisitorI
import io.github.gbkt.core.ir.TimedBlockObjectIR
import io.github.gbkt.core.ir.TransitionEdge
import io.github.gbkt.core.ir.TriggerObjectIR
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.ZoneIR
import io.github.gbkt.core.ir.ZoneObjectIR
import io.github.gbkt.core.ir.ZoneTransitionIR
import io.github.gbkt.core.pickup.PickupDef
import io.github.gbkt.core.pickup.PickupSystemConfig

// =============================================================================
// GBDK SYSTEM VISITOR
// Translates IR v2 SystemIR nodes into lists of typed CFunction nodes.
// Implements SystemIRVisitorI<List<CFunction>> for all 6 system types.
//
// All typed systems generate real C code — no silent drops:
//  - CameraSystem  → _camera_x/_camera_y globals + update_camera() function
//  - SaveSystem    → save_game()/load_game() SRAM functions
//  - SoundSystem   → empty list (sound handled by GBDKPipeline.buildSoundFunctions)
//  - ExplorationSystem → exploration state globals + exploration_move() function
//  - DialogSystem  → delegates to existing buildDialogHelpers()
//  - GenericSystem → delegates to buildSystemTriggerFunction()
// =============================================================================

/**
 * Visitor that converts IR v2 [SystemIR][io.github.gbkt.core.ir.SystemIR] nodes to lists of typed C
 * [CFunction] nodes.
 *
 * Implements [SystemIRVisitorI]<List<[CFunction]>> so that each system type dispatches via
 * [io.github.gbkt.core.ir.SystemIR.accept] instead of filterIsInstance pattern in
 * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline.buildSystemFunctions].
 *
 * @param gameIR The full [GameIR] for context (variable names, actor IDs, etc.)
 * @param zoneBankAllocation Map from zone ID to allocated ROM bank number. Used to emit
 *   SWITCH_ROM(N) before tilemap data access in zone_load functions. Empty map means no bank
 *   switching (all zone data in HOME bank).
 */
class GBDKSystemVisitor(
    private val gameIR: GameIR,
    private val zoneBankAllocation: Map<String, Int> = emptyMap(),
) : SystemIRVisitorI<List<CFunction>> {

    // -------------------------------------------------------------------------
    // CameraSystem: _camera_x, _camera_y globals + update_camera() function
    // -------------------------------------------------------------------------

    /**
     * Generate camera update function for a [CameraSystem] using fully typed C AST (zero CRawCode).
     *
     * The generated `update_camera_{id}()` function:
     * 1. If [CameraSystem.followActorId] is set: reads `_{actorId}_x` / `_{actorId}_y` and computes
     *    the desired camera position (center actor on 160x144 screen).
     * 2. If [CameraSystem.boundsWidth]/[CameraSystem.boundsHeight] are set: clamps camera
     *    coordinates using [CTernary] and INT16 intermediate math to avoid UINT8 underflow near the
     *    left/top edges.
     * 3. Shake logic using [CIf]/[CTernary]/[CExprStatement] — zero [CRawCode] instances.
     *
     * GBDK hardware register writes (`SCX_REG = ...`, `SCY_REG = ...`) are the only [CRawCode]
     * allowed since GBDK hardware register lvalue assignment cannot be expressed via [CBinaryExpr]
     * (register macros expand to lvalues that the typed AST does not model).
     */
    override fun visitCameraSystem(system: CameraSystem): List<CFunction> {
        val sanitizedId = system.id.replace('-', '_').replace(' ', '_')

        // Capture nullable fields into locals so Kotlin smart casts work across module boundary
        val followActorId: String? = system.followActorId
        val boundsWidth: Int? = system.boundsWidth
        val boundsHeight: Int? = system.boundsHeight

        val body =
            buildList<CStatement> {
                // ---- Step 1: follow-target tracking ----
                if (followActorId != null) {
                    val actorId = followActorId.replace('-', '_').replace(' ', '_')
                    // INT16 rawX = (INT16)_hero_x - 80  (80 = screen_width/2)
                    // INT16 rawY = (INT16)_hero_y - 72  (72 = screen_height/2)
                    add(
                        CVarDecl(
                            name = "rawX",
                            type = CI16,
                            initializer =
                                CBinaryExpr(CCast(CI16, CVar("_${actorId}_x")), "-", CLiteral(80)),
                        )
                    )
                    add(
                        CVarDecl(
                            name = "rawY",
                            type = CI16,
                            initializer =
                                CBinaryExpr(CCast(CI16, CVar("_${actorId}_y")), "-", CLiteral(72)),
                        )
                    )

                    // ---- Step 2: bounds clamping ----
                    if (boundsWidth != null && boundsHeight != null) {
                        // Plan 07.4-21: clamp to non-negative. When the zone is smaller than the
                        // screen (boundsWidth < 160 or boundsHeight < 144), the scroll range is
                        // zero — the camera locks at the origin. Without this guard the negative
                        // literal flows into a CTernary that ultimately casts to UINT8, producing
                        // SCX/SCY = 248/etc. (wraparound) and scrolling the BG off-screen.
                        // See 07.4-UAT.md secondary issue 1 (racer 19x19 zone → maxX = -8 → 248).
                        val maxX = kotlin.math.max(0, boundsWidth - GameBoyConstants.SCREEN_WIDTH)
                        val maxY = kotlin.math.max(0, boundsHeight - GameBoyConstants.SCREEN_HEIGHT)

                        // _camera_x = (UINT8)(rawX < 0 ? 0 : (rawX > maxX ? maxX : rawX))
                        val clampX =
                            CTernary(
                                CBinaryExpr(CVar("rawX"), "<", CIntLiteral(0)),
                                CLiteral(0),
                                CTernary(
                                    CBinaryExpr(CVar("rawX"), ">", CIntLiteral(maxX)),
                                    CLiteral(maxX),
                                    CVar("rawX"),
                                ),
                            )
                        add(CExprStatement(CBinaryExpr(CVar("_camera_x"), "=", CCast(CU8, clampX))))

                        // _camera_y = (UINT8)(rawY < 0 ? 0 : (rawY > maxY ? maxY : rawY))
                        val clampY =
                            CTernary(
                                CBinaryExpr(CVar("rawY"), "<", CIntLiteral(0)),
                                CLiteral(0),
                                CTernary(
                                    CBinaryExpr(CVar("rawY"), ">", CIntLiteral(maxY)),
                                    CLiteral(maxY),
                                    CVar("rawY"),
                                ),
                            )
                        add(CExprStatement(CBinaryExpr(CVar("_camera_y"), "=", CCast(CU8, clampY))))
                    } else {
                        // No bounds — clamp to 0..255 (UINT8 range), prevent negative wrapping
                        val clampX =
                            CTernary(
                                CBinaryExpr(CVar("rawX"), "<", CIntLiteral(0)),
                                CLiteral(0),
                                CVar("rawX"),
                            )
                        add(CExprStatement(CBinaryExpr(CVar("_camera_x"), "=", CCast(CU8, clampX))))
                        val clampY =
                            CTernary(
                                CBinaryExpr(CVar("rawY"), "<", CIntLiteral(0)),
                                CLiteral(0),
                                CVar("rawY"),
                            )
                        add(CExprStatement(CBinaryExpr(CVar("_camera_y"), "=", CCast(CU8, clampY))))
                    }
                }

                // ---- Step 3: shake logic using typed CIf / CTernary ----
                // Shake offset: timer & 1 ? shake_intensity : 0
                val shakeOffset =
                    CTernary(
                        CBinaryExpr(
                            CBinaryExpr(CVar("_camera_shake_timer"), "&", CLiteral(1)),
                            "!=",
                            CLiteral(0),
                        ),
                        CVar("_camera_shake_intensity"),
                        CLiteral(0),
                    )

                // if (_camera_shake_timer > 0) { SCX_REG = _camera_x + offset; SCY_REG = ...;
                // timer--; }
                // else { SCX_REG = _camera_x; SCY_REG = _camera_y; }
                val shakeTimerCheck = CBinaryExpr(CVar("_camera_shake_timer"), ">", CLiteral(0))

                val shakeThenBody =
                    buildList<CStatement> {
                        // offset = shakeOffset (declared inline as local var)
                        add(CVarDecl(name = "offset", type = CU8, initializer = shakeOffset))
                        // SCX_REG = _camera_x + offset  (hardware register — CRawCode for lvalue
                        // write)
                        add(CRawCode("SCX_REG = _camera_x + offset;"))
                        add(CRawCode("SCY_REG = _camera_y + offset;"))
                        // _camera_shake_timer--
                        add(CExprStatement(CUnaryExpr("--", CVar("_camera_shake_timer"))))
                    }

                val shakeElseBody =
                    buildList<CStatement> {
                        add(CRawCode("SCX_REG = _camera_x;"))
                        add(CRawCode("SCY_REG = _camera_y;"))
                    }

                add(
                    CIf(
                        condition = shakeTimerCheck,
                        thenBody = shakeThenBody,
                        elseBody = shakeElseBody,
                    )
                )
            }

        val updateCamera =
            CFunction(
                name = "update_camera_$sanitizedId",
                returnType = CVoid,
                body = body,
                sectionComment = "Camera system: ${system.id}",
            )

        return listOf(updateCamera)
    }

    // -------------------------------------------------------------------------
    // SaveSystem: save_game_{id}(slotIndex) / load_game_{id}(slotIndex) via SRAM
    // -------------------------------------------------------------------------

    /**
     * Generate save_game_{id}(slotIndex) and load_game_{id}(slotIndex) for a [SaveSystem].
     *
     * Uses GBDK ENABLE_RAM / DISABLE_RAM macros (CRawCode — GBDK macro lvalue exception) to
     * activate MBC SRAM bank 0 at 0xA000. All structural logic (slot offset, variable writes,
     * sentinel, checksum) uses typed C AST (zero CRawCode for logic).
     *
     * SRAM layout per slot:
     * - Bytes 0..N-1: saved variable values (non-transient variables only)
     * - Byte N: sentinel (0xAB) — verifies the save slot was written
     * - Byte N+1 (if useChecksum): 8-bit rolling sum of bytes 0..N for corruption detection
     *
     * Slot N starts at SRAM base + (slotIndex * SAVE_SLOT_SIZE_{id}).
     *
     * The `#define SAVE_SLOT_SIZE_{id} <size>` macro is NOT generated here — it is a constant
     * embedded in the pointer arithmetic CRawExpr because CDefine is not in the typed AST.
     *
     * GBDK macro exceptions (CRawCode):
     * - `ENABLE_RAM;` — activates SRAM for MBC cartridges (macro expands to lvalue write)
     * - `DISABLE_RAM;` — deactivates SRAM (always the last statement in both functions)
     */
    override fun visitSaveSystem(system: SaveSystem): List<CFunction> {
        val sanitizedId = system.id.replace('-', '_').replace(' ', '_')

        // Filter to non-transient variables only — transient vars excluded from SRAM
        val savedVars = gameIR.variables.filter { it.name !in system.transientVarNames }

        // Slot layout size: one byte per saved variable + 1 sentinel + optional checksum byte
        val slotSize = savedVars.size + 1 + (if (system.useChecksum) 1 else 0)
        val sentinelIdx = savedVars.size // sentinel written after all variables
        val checksumIdx = sentinelIdx + 1 // checksum byte (only when useChecksum = true)

        // ---- Helper: SRAM pointer declaration ----
        // volatile UINT8 *sram = (volatile UINT8 *)(0xA000 + slotIndex * slotSize)
        // CRawExpr acceptable for volatile cast — GBDK-specific pointer arithmetic
        fun buildSramPtrDecl(): CVarDecl =
            CVarDecl(
                name = "sram",
                type = io.github.gbkt.backend.gbdk.codegen.ast.CPointer(CU8),
                initializer =
                    CRawExpr("(volatile UINT8 *)(0xA000 + (UINT16)slotIndex * ${slotSize}u)"),
            )

        // ---- save_game_{id}(slotIndex) ----
        val saveBody =
            buildList<CStatement> {
                // ENABLE_RAM — GBDK macro, CRawCode exception
                add(GBDKMacros.enableRam())
                // volatile UINT8 *sram = ...
                add(buildSramPtrDecl())
                // Write each non-transient variable to sram[idx]
                for ((idx, varDef) in savedVars.withIndex()) {
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("sram"), CLiteral(idx)),
                                "=",
                                CVar("_${varDef.name}"),
                            )
                        )
                    )
                }
                // Write sentinel: sram[sentinelIdx] = 0xAB
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("sram"), CLiteral(sentinelIdx)),
                            "=",
                            CLiteral(0xAB),
                        )
                    )
                )
                // Optional checksum: compute 8-bit rolling sum of all saved bytes
                if (system.useChecksum) {
                    // UINT8 chk = 0;
                    add(CVarDecl(name = "chk", type = CU8, initializer = CLiteral(0)))
                    // UINT8 ci;
                    add(CVarDecl(name = "ci", type = CU8, initializer = null))
                    // for (ci = 0; ci < sentinelIdx + 1; ci++) { chk += sram[ci]; }
                    // Note: sum includes sentinel byte in checksum intentionally (consistent with
                    // load)
                    val checksumFor =
                        CFor(
                            init = CVarDecl("ci", CU8, CLiteral(0)),
                            condition = CBinaryExpr(CVar("ci"), "<", CLiteral(sentinelIdx + 1)),
                            increment = CUnaryExpr("++", CVar("ci")),
                            body =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("chk"),
                                            "+=",
                                            CArrayAccess(CVar("sram"), CVar("ci")),
                                        )
                                    )
                                ),
                        )
                    add(checksumFor)
                    // sram[checksumIdx] = chk & 0xFF
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("sram"), CLiteral(checksumIdx)),
                                "=",
                                CBinaryExpr(CVar("chk"), "&", CLiteral(0xFF)),
                            )
                        )
                    )
                }
                // DISABLE_RAM — GBDK macro, CRawCode exception; ALWAYS last
                add(GBDKMacros.disableRam())
            }

        val saveGame =
            CFunction(
                name = "save_game_$sanitizedId",
                returnType = CVoid,
                params = listOf(CParam("slotIndex", CU8)),
                body = saveBody,
                sectionComment =
                    "Save system: ${system.id} (slots=${system.slots}, checksum=${system.useChecksum})",
            )

        // ---- load_game_{id}(slotIndex) ----
        val loadBody =
            buildList<CStatement> {
                // ENABLE_RAM — GBDK macro, CRawCode exception
                add(GBDKMacros.enableRam())
                // volatile UINT8 *sram = ...
                add(buildSramPtrDecl())

                // Optional checksum verification BEFORE sentinel check
                if (system.useChecksum) {
                    // UINT8 chk = 0;
                    add(CVarDecl(name = "chk", type = CU8, initializer = CLiteral(0)))
                    add(CVarDecl(name = "ci", type = CU8, initializer = null))
                    // for (ci = 0; ci < sentinelIdx + 1; ci++) { chk += sram[ci]; }
                    val checksumFor =
                        CFor(
                            init = CVarDecl("ci", CU8, CLiteral(0)),
                            condition = CBinaryExpr(CVar("ci"), "<", CLiteral(sentinelIdx + 1)),
                            increment = CUnaryExpr("++", CVar("ci")),
                            body =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("chk"),
                                            "+=",
                                            CArrayAccess(CVar("sram"), CVar("ci")),
                                        )
                                    )
                                ),
                        )
                    add(checksumFor)
                    // if ((chk & 0xFF) != sram[checksumIdx]) { DISABLE_RAM; return; }
                    val checksumMismatch =
                        CBinaryExpr(
                            CBinaryExpr(CVar("chk"), "&", CLiteral(0xFF)),
                            "!=",
                            CArrayAccess(CVar("sram"), CLiteral(checksumIdx)),
                        )
                    add(
                        CIf(
                            condition = checksumMismatch,
                            thenBody = listOf(GBDKMacros.disableRam(), CReturn(null)),
                        )
                    )
                }

                // Sentinel check: if (sram[sentinelIdx] != 0xAB) { DISABLE_RAM; return; }
                val sentinelMismatch =
                    CBinaryExpr(
                        CArrayAccess(CVar("sram"), CLiteral(sentinelIdx)),
                        "!=",
                        CLiteral(0xAB),
                    )
                add(
                    CIf(
                        condition = sentinelMismatch,
                        thenBody = listOf(GBDKMacros.disableRam(), CReturn(null)),
                    )
                )

                // Load each non-transient variable from SRAM
                for ((idx, varDef) in savedVars.withIndex()) {
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("_${varDef.name}"),
                                "=",
                                CArrayAccess(CVar("sram"), CLiteral(idx)),
                            )
                        )
                    )
                }
                // DISABLE_RAM — GBDK macro, CRawCode exception; ALWAYS last
                add(GBDKMacros.disableRam())
            }

        val loadGame =
            CFunction(
                name = "load_game_$sanitizedId",
                returnType = CVoid,
                params = listOf(CParam("slotIndex", CU8)),
                body = loadBody,
            )

        // ---- trigger_{id}() — Plan 11-10 named-bug fix + arity follow-on ----
        // ScriptOpVisitor.visitTriggerSystem (line 666) emits CCall("trigger_<id>", args)
        // where `args` come from `op.args.values`. The DSL surface `triggerSystem("saves")`
        // produces zero args, so the trampoline must be zero-arg to match the caller.
        // The trampoline delegates to save_game_<id>(0) — slot 0 default; multi-slot
        // selection is a separate DSL surface and out of scope here.
        // Without this stub, lcc reports `?ASlink-Warning-Undefined Global '_trigger_<id>'`.
        val triggerStub =
            CFunction(
                name = "trigger_$sanitizedId",
                returnType = CVoid,
                params = emptyList(),
                body = listOf(CExprStatement(CCall("save_game_$sanitizedId", listOf(CLiteral(0))))),
                sectionComment =
                    "SaveSystem trigger stub — called by ScriptOpVisitor.visitTriggerSystem",
            )

        return listOf(saveGame, loadGame, triggerStub)
    }

    // -------------------------------------------------------------------------
    // SoundSystem: empty list — sound handled by buildSoundFunctions()
    // -------------------------------------------------------------------------

    /**
     * Return empty list for [SoundSystem] — sound driver functions are already generated by
     * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline.buildSoundFunctions].
     *
     * Returning an empty list here avoids duplicate function generation while still dispatching
     * through the visitor (no silent drop via filterIsInstance).
     */
    override fun visitSoundSystem(system: SoundSystem): List<CFunction> = emptyList()

    // -------------------------------------------------------------------------
    // ExplorationSystem: 6 functions — move, step, encounter_check, interact,
    //                    zone_load, zone_transition
    // -------------------------------------------------------------------------

    /**
     * Generate exploration C functions for an [ExplorationSystem].
     *
     * Base functions: exploration_move, exploration_step, exploration_encounter_check,
     * exploration_interact, zone_load, zone_transition. All typed C AST (zero new CRawCode).
     * Cross-module smart cast: all nullable fields captured in local vals.
     *
     * Additional functions (generated when applicable):
     * - `zone_check_edges_{id}()` — generated when any zone has edge transitions. Called from
     *   `exploration_step` each step. Supports [ZoneTransitionIR.conditionFlag] gates (GAP-07).
     * - Per-zone object handlers and dispatch — generated when any zone has [ZoneObjectIR] objects.
     *   E.g., `zone_chest_{zoneId}_{objId}_interact()` + `zone_try_interact_{zoneId}(x, y)`
     *   (GAP-06).
     */
    @Suppress("LongMethod")
    override fun visitExplorationSystem(system: ExplorationSystem): List<CFunction> {
        val sanitizedId = system.id.replace('-', '_').replace(' ', '_')
        val gauges: List<ExplorationGaugeIR> = system.gauges
        val interactStatements: List<ScriptOp> = system.interactStatements
        val stepStatements: List<ScriptOp> = system.stepStatements
        val zones: List<ZoneIR> = gameIR.zones
        val hasEncounters = zones.any { zone ->
            val table = zone.encounterTable
            table != null && table.entries.isNotEmpty()
        }
        val hasEdgeTransitions = zones.any { it.transitions.any { t -> t.edge != null } }
        val hasZoneObjects = zones.any { it.objects.isNotEmpty() }
        val playerX = CVar("_player_x")
        val playerY = CVar("_player_y")
        val nxVar = CVar("nx")
        val nyVar = CVar("ny")

        // Actors with non-passthrough entity collision configuration (G3 entity obstacle detection)
        // Capture entityCollision into local val for cross-module smart cast
        val collisionActors =
            gameIR.actors.filter {
                val ec = it.entityCollision
                ec != null && ec.mode != EntityCollisionMode.PASSTHROUGH
            }
        val hasEntityCollision = collisionActors.isNotEmpty()

        // 1. The exploration movement function, named exploration_move_<id>
        val jUp = CVar("J_UP")
        val jDown = CVar("J_DOWN")
        val jLeft = CVar("J_LEFT")
        val jRight = CVar("J_RIGHT")
        val gridBound = CLiteral(31)

        // D-pad branches shared by both entity-collision and simple variants
        val upCond =
            CBinaryExpr(CCall("dpad_held", listOf(jUp)), "&&", CBinaryExpr(nyVar, ">", CLiteral(0)))
        val downCond =
            CBinaryExpr(CCall("dpad_held", listOf(jDown)), "&&", CBinaryExpr(nyVar, "<", gridBound))
        val ifUp = CIf(upCond, listOf(CExprStatement(CUnaryExpr("--", nyVar))))
        val ifDown =
            CIf(
                downCond,
                listOf(CExprStatement(CUnaryExpr("++", nyVar))),
                listOf(
                    CIf(
                        CBinaryExpr(
                            CCall("dpad_held", listOf(jLeft)),
                            "&&",
                            CBinaryExpr(nxVar, ">", CLiteral(0)),
                        ),
                        listOf(CExprStatement(CUnaryExpr("--", nxVar))),
                        listOf(
                            CIf(
                                CBinaryExpr(
                                    CCall("dpad_held", listOf(jRight)),
                                    "&&",
                                    CBinaryExpr(nxVar, "<", gridBound),
                                ),
                                listOf(CExprStatement(CUnaryExpr("++", nxVar))),
                                listOf(CReturn(null)),
                            )
                        ),
                    )
                ),
            )
        val explorationMove =
            CFunction(
                name = "exploration_move_$sanitizedId",
                returnType = CVoid,
                body =
                    buildList {
                        add(CVarDecl("nx", CU8, playerX))
                        add(CVarDecl("ny", CU8, playerY))
                        if (hasEntityCollision) {
                            // direction: 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT (captured for entity push)
                            add(CVarDecl("direction", CU8, CLiteral(0xFF)))
                            val setDir0 =
                                CExprStatement(CBinaryExpr(CVar("direction"), "=", CLiteral(0)))
                            val setDir1 =
                                CExprStatement(CBinaryExpr(CVar("direction"), "=", CLiteral(1)))
                            val setDir2 =
                                CExprStatement(CBinaryExpr(CVar("direction"), "=", CLiteral(2)))
                            val setDir3 =
                                CExprStatement(CBinaryExpr(CVar("direction"), "=", CLiteral(3)))
                            add(
                                CIf(
                                    upCond,
                                    listOf(CExprStatement(CUnaryExpr("--", nyVar)), setDir0),
                                )
                            )
                            add(
                                CIf(
                                    downCond,
                                    listOf(CExprStatement(CUnaryExpr("++", nyVar)), setDir1),
                                    listOf(
                                        CIf(
                                            CBinaryExpr(
                                                CCall("dpad_held", listOf(jLeft)),
                                                "&&",
                                                CBinaryExpr(nxVar, ">", CLiteral(0)),
                                            ),
                                            listOf(
                                                CExprStatement(CUnaryExpr("--", nxVar)),
                                                setDir2,
                                            ),
                                            listOf(
                                                CIf(
                                                    CBinaryExpr(
                                                        CCall("dpad_held", listOf(jRight)),
                                                        "&&",
                                                        CBinaryExpr(nxVar, "<", gridBound),
                                                    ),
                                                    listOf(
                                                        CExprStatement(CUnaryExpr("++", nxVar)),
                                                        setDir3,
                                                    ),
                                                    listOf(CReturn(null)),
                                                )
                                            ),
                                        )
                                    ),
                                )
                            )
                        } else {
                            add(ifUp)
                            add(ifDown)
                        }
                        add(
                            CIf(
                                condition = CCall("_map_collision", listOf(nxVar, nyVar)),
                                thenBody = listOf(CReturn(null)),
                            )
                        )
                        // Entity obstacle detection (G3): check entity grid after tile collision
                        if (hasEntityCollision) {
                            // UINT8 entity_id = _entity_check(nx, ny);
                            add(
                                CVarDecl(
                                    "entity_id",
                                    CU8,
                                    CCall("_entity_check", listOf(nxVar, nyVar)),
                                )
                            )
                            // if (entity_id != 0xFF) { handle; if mode != OVERLAP_TRIGGER (3)
                            // return; }
                            add(
                                CIf(
                                    condition =
                                        CBinaryExpr(CVar("entity_id"), "!=", CLiteral(0xFF)),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall(
                                                    "_entity_handle_block",
                                                    listOf(
                                                        CVar("entity_id"),
                                                        nxVar,
                                                        nyVar,
                                                        CVar("direction"),
                                                    ),
                                                )
                                            ),
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_entity_collision_mode"),
                                                            CVar("entity_id"),
                                                        ),
                                                        "!=",
                                                        CLiteral(3), // OVERLAP_TRIGGER ordinal
                                                    ),
                                                thenBody = listOf(CReturn(null)),
                                            ),
                                        ),
                                )
                            )
                        }
                        add(CExprStatement(CBinaryExpr(playerX, "=", nxVar)))
                        add(CExprStatement(CBinaryExpr(playerY, "=", nyVar)))
                        add(CExprStatement(CCall("exploration_step_$sanitizedId", emptyList())))
                    },
                sectionComment = "Exploration system: ${system.id}",
            )

        // 2. The per-step bookkeeping function, named exploration_step_<id>
        val explorationStep =
            CFunction(
                name = "exploration_step_$sanitizedId",
                returnType = CVoid,
                body =
                    buildList {
                        add(CExprStatement(CUnaryExpr("++", CVar("_exploration_step_count"))))
                        for (gauge in gauges) {
                            val gaugeVar = CVar("_gauge_${gauge.id}")
                            val decrementBody =
                                buildList<CStatement> {
                                    add(
                                        CExprStatement(
                                            CBinaryExpr(
                                                gaugeVar,
                                                "-=",
                                                CLiteral(gauge.decrementPerStep),
                                            )
                                        )
                                    )
                                    val threshold: Int? = gauge.onLowThreshold
                                    val onLowOps: List<ScriptOp> = gauge.onLowStatements
                                    if (threshold != null && onLowOps.isNotEmpty()) {
                                        add(
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        gaugeVar,
                                                        "<=",
                                                        CLiteral(threshold),
                                                    ),
                                                thenBody =
                                                    onLowOps.map { op ->
                                                        ScriptOpVisitor.visit(op)
                                                    },
                                            )
                                        )
                                    }
                                    val onDepletedOps: List<ScriptOp> = gauge.onDepletedStatements
                                    if (onDepletedOps.isNotEmpty()) {
                                        add(
                                            CIf(
                                                condition =
                                                    CBinaryExpr(gaugeVar, "==", CLiteral(0)),
                                                thenBody =
                                                    onDepletedOps.map { op ->
                                                        ScriptOpVisitor.visit(op)
                                                    },
                                            )
                                        )
                                    }
                                }
                            add(
                                CIf(
                                    condition = CBinaryExpr(gaugeVar, ">", CLiteral(0)),
                                    thenBody = decrementBody,
                                )
                            )
                        }
                        for (op in stepStatements) {
                            add(ScriptOpVisitor.visit(op))
                        }
                        if (hasEncounters) {
                            add(
                                CExprStatement(
                                    CCall("exploration_encounter_check_$sanitizedId", emptyList())
                                )
                            )
                        }
                        // GAP-07: check edge-triggered zone transitions with optional flag gates
                        if (hasEdgeTransitions) {
                            add(CExprStatement(CCall("zone_check_edges_$sanitizedId", emptyList())))
                        }
                    },
            )

        // 3. The random-encounter check function, named exploration_encounter_check_<id>
        val explorationEncounterCheck = buildEncounterCheckFunction(sanitizedId, zones)

        // 4. The interaction handler function, named exploration_interact_<id>
        val explorationInteract =
            CFunction(
                name = "exploration_interact_$sanitizedId",
                returnType = CVoid,
                body =
                    if (interactStatements.isEmpty()) emptyList()
                    else interactStatements.map { op -> ScriptOpVisitor.visit(op) },
            )

        // 5. zone_load_{id}(zone_id)
        val zoneLoad = buildZoneLoadFunction(sanitizedId, zones)

        // 6. zone_transition_{id}(target_zone_id, edge, entry_x, entry_y)
        val zoneTransition = buildZoneTransitionFunction(sanitizedId, zones, playerX, playerY)

        // 7. zone_check_edges_{id}() — generated when any zone has edge-triggered transitions
        // (GAP-07)
        val zoneCheckEdges =
            if (hasEdgeTransitions) {
                listOf(buildZoneCheckEdgesFunction(sanitizedId, zones, playerX, playerY))
            } else {
                emptyList()
            }

        // 8. Zone object handler + dispatch functions (GAP-06)
        val zoneObjectFunctions =
            if (hasZoneObjects) buildZoneObjectFunctions(zones) else emptyList()

        // 9. Entity collision functions — generated when actors have non-PASSTHROUGH config
        val entityCollisionFunctions =
            if (hasEntityCollision) {
                buildEntityCollisionFunctions(sanitizedId, collisionActors)
            } else {
                emptyList()
            }

        return listOf(
            explorationMove,
            explorationStep,
            explorationEncounterCheck,
            explorationInteract,
            zoneLoad,
            zoneTransition,
        ) + zoneCheckEdges + zoneObjectFunctions + entityCollisionFunctions
    }

    /**
     * Generate entity collision functions for actors with non-PASSTHROUGH collision config (G3).
     *
     * Generates 6 C functions:
     * 1. `_entity_register(entity_id, tile_x, tile_y, mode)` — set grid bit, store
     *    position/mode/shape.
     * 2. `_entity_remove(entity_id)` — clear grid bits, reset mode to 0xFF (PASSTHROUGH).
     * 3. `_entity_check(nx, ny)` — dispatch on shape: TILE=grid-bit check, HITBOX=AABB pixel check.
     * 4. `_entity_handle_block(entity_id, nx, ny, direction)` — switch on mode ordinal: BLOCK(0):
     *    bump; BLOCK_AND_TRIGGER(2): set _blocking_entity_id + onBlocked ops; OVERLAP_TRIGGER(3):
     *    onOverlap ops; PUSH(4): try push + set _pushed_entity_id/_push_direction.
     * 5. `_entity_set_collision_mode(entity_id, mode)` — runtime mode change.
     * 6. `_entity_bump_feedback()` — bump sound stub.
     *
     * Gap 1: _blocking_entity_id set before onBlocked callback; _pushed_entity_id/_push_direction
     * set before onPushed callback. Gap 2: HITBOX collision shape dispatches AABB pixel check per
     * entity instead of grid-bit lookup.
     */
    private fun buildEntityCollisionFunctions(
        sanitizedId: String,
        actors: List<ActorIR>,
    ): List<CFunction> =
        listOf(
            buildEntityRegisterFunction(sanitizedId),
            buildEntityRemoveFunction(),
            buildEntityCheckFunction(actors),
            buildEntityHandleBlockFunction(actors),
            buildEntitySetCollisionModeFunction(),
            buildEntityBumpFeedbackFunction(),
        )

    private fun buildEntityRegisterFunction(sanitizedId: String): CFunction {
        val entityIdParam = CVar("entity_id")
        val tileXParam = CVar("tile_x")
        val tileYParam = CVar("tile_y")
        val modeParam = CVar("mode")
        val registerBody =
            buildList<CStatement> {
                // Store position
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_tile_x"), entityIdParam),
                            "=",
                            tileXParam,
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_tile_y"), entityIdParam),
                            "=",
                            tileYParam,
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_collision_mode"), entityIdParam),
                            "=",
                            modeParam,
                        )
                    )
                )
                // Default shape to TILE (0) on register
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_collision_shape"), entityIdParam),
                            "=",
                            CLiteral(0),
                        )
                    )
                )
                // Set grid bits for all tiles occupied by this entity (multi-tile support)
                add(CVarDecl("dy", CU8, CLiteral(0)))
                add(
                    CFor(
                        init = CExprStatement(CBinaryExpr(CVar("dy"), "=", CLiteral(0))),
                        condition =
                            CBinaryExpr(
                                CVar("dy"),
                                "<",
                                CArrayAccess(CVar("_entity_tiles_high"), entityIdParam),
                            ),
                        increment = CUnaryExpr("++", CVar("dy")),
                        body =
                            buildList {
                                add(CVarDecl("dx", CU8, CLiteral(0)))
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("dx"), "=", CLiteral(0))
                                            ),
                                        condition =
                                            CBinaryExpr(
                                                CVar("dx"),
                                                "<",
                                                CArrayAccess(
                                                    CVar("_entity_tiles_wide"),
                                                    entityIdParam,
                                                ),
                                            ),
                                        increment = CUnaryExpr("++", CVar("dx")),
                                        body =
                                            listOf(
                                                CVarDecl(
                                                    "idx",
                                                    CU8,
                                                    CBinaryExpr(
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                tileYParam,
                                                                "+",
                                                                CVar("dy"),
                                                            ),
                                                            "*",
                                                            CLiteral(32),
                                                        ),
                                                        "+",
                                                        CBinaryExpr(tileXParam, "+", CVar("dx")),
                                                    ),
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_entity_grid"),
                                                            CBinaryExpr(
                                                                CVar("idx"),
                                                                "/",
                                                                CLiteral(8),
                                                            ),
                                                        ),
                                                        "|=",
                                                        CBinaryExpr(
                                                            CLiteral(1),
                                                            "<<",
                                                            CBinaryExpr(
                                                                CVar("idx"),
                                                                "%",
                                                                CLiteral(8),
                                                            ),
                                                        ),
                                                    )
                                                ),
                                            ),
                                    )
                                )
                            },
                    )
                )
                add(CExprStatement(CUnaryExpr("++", CVar("_entity_count"))))
            }
        return CFunction(
            name = "_entity_register",
            returnType = CVoid,
            params =
                listOf(
                    CParam("entity_id", CU8),
                    CParam("tile_x", CU8),
                    CParam("tile_y", CU8),
                    CParam("mode", CU8),
                ),
            body = registerBody,
            sectionComment = "Entity collision functions: $sanitizedId",
        )
    }

    private fun buildEntityRemoveFunction(): CFunction {
        val entityIdParam = CVar("entity_id")
        val removeBody =
            buildList<CStatement> {
                add(CVarDecl("tx", CU8, CArrayAccess(CVar("_entity_tile_x"), entityIdParam)))
                add(CVarDecl("ty", CU8, CArrayAccess(CVar("_entity_tile_y"), entityIdParam)))
                // Clear grid bits for all tiles occupied by this entity (multi-tile support)
                add(CVarDecl("dy", CU8, CLiteral(0)))
                add(
                    CFor(
                        init = CExprStatement(CBinaryExpr(CVar("dy"), "=", CLiteral(0))),
                        condition =
                            CBinaryExpr(
                                CVar("dy"),
                                "<",
                                CArrayAccess(CVar("_entity_tiles_high"), entityIdParam),
                            ),
                        increment = CUnaryExpr("++", CVar("dy")),
                        body =
                            buildList {
                                add(CVarDecl("dx", CU8, CLiteral(0)))
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("dx"), "=", CLiteral(0))
                                            ),
                                        condition =
                                            CBinaryExpr(
                                                CVar("dx"),
                                                "<",
                                                CArrayAccess(
                                                    CVar("_entity_tiles_wide"),
                                                    entityIdParam,
                                                ),
                                            ),
                                        increment = CUnaryExpr("++", CVar("dx")),
                                        body =
                                            listOf(
                                                CVarDecl(
                                                    "idx",
                                                    CU8,
                                                    CBinaryExpr(
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("ty"),
                                                                "+",
                                                                CVar("dy"),
                                                            ),
                                                            "*",
                                                            CLiteral(32),
                                                        ),
                                                        "+",
                                                        CBinaryExpr(CVar("tx"), "+", CVar("dx")),
                                                    ),
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_entity_grid"),
                                                            CBinaryExpr(
                                                                CVar("idx"),
                                                                "/",
                                                                CLiteral(8),
                                                            ),
                                                        ),
                                                        "&=",
                                                        CUnaryExpr(
                                                            "~",
                                                            CBinaryExpr(
                                                                CLiteral(1),
                                                                "<<",
                                                                CBinaryExpr(
                                                                    CVar("idx"),
                                                                    "%",
                                                                    CLiteral(8),
                                                                ),
                                                            ),
                                                        ),
                                                    )
                                                ),
                                            ),
                                    )
                                )
                            },
                    )
                )
                // Reset to PASSTHROUGH sentinel
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_collision_mode"), entityIdParam),
                            "=",
                            CRawExpr("0xFF"),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_tile_x"), entityIdParam),
                            "=",
                            CRawExpr("0xFF"),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_entity_tile_y"), entityIdParam),
                            "=",
                            CRawExpr("0xFF"),
                        )
                    )
                )
                if (CVar("_entity_count") != CLiteral(0)) {
                    add(CExprStatement(CUnaryExpr("--", CVar("_entity_count"))))
                }
            }
        return CFunction(
            name = "_entity_remove",
            returnType = CVoid,
            params = listOf(CParam("entity_id", CU8)),
            body = removeBody,
        )
    }

    private fun buildEntityCheckFunction(actors: List<ActorIR>): CFunction {
        val tileSize = 8 // default tile size
        val nxVar = CVar("nx")
        val nyVar = CVar("ny")
        val hasHitbox = actors.any {
            val ec = it.entityCollision
            ec != null && ec.shape == CollisionShape.HITBOX
        }
        val checkBody =
            buildList<CStatement> {
                // TILE-based check: single bit lookup in _entity_grid
                add(
                    CVarDecl(
                        "idx",
                        CU8,
                        CBinaryExpr(CBinaryExpr(nyVar, "*", CLiteral(32)), "+", nxVar),
                    )
                )
                add(
                    CVarDecl(
                        "bit",
                        CU8,
                        CBinaryExpr(
                            CArrayAccess(
                                CVar("_entity_grid"),
                                CBinaryExpr(CVar("idx"), "/", CLiteral(8)),
                            ),
                            "&",
                            CBinaryExpr(
                                CLiteral(1),
                                "<<",
                                CBinaryExpr(CVar("idx"), "%", CLiteral(8)),
                            ),
                        ),
                    )
                )
                // If bit set, find which entity is at this tile (iterate)
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("bit"), "!=", CLiteral(0)),
                        thenBody =
                            buildList {
                                add(CVarDecl("i", CU8, CLiteral(0)))
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("i"), "=", CLiteral(0))
                                            ),
                                        condition =
                                            CBinaryExpr(CVar("i"), "<", CVar("_entity_count")),
                                        increment = CUnaryExpr("++", CVar("i")),
                                        body =
                                            buildList {
                                                // Skip PASSTHROUGH entities (mode == 0xFF)
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CArrayAccess(
                                                                    CVar("_entity_collision_mode"),
                                                                    CVar("i"),
                                                                ),
                                                                "==",
                                                                CRawExpr("0xFF"),
                                                            ),
                                                        thenBody = listOf(CContinue),
                                                    )
                                                )
                                                // Skip HITBOX entities in TILE path
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CArrayAccess(
                                                                    CVar("_entity_collision_shape"),
                                                                    CVar("i"),
                                                                ),
                                                                "==",
                                                                CLiteral(1),
                                                            ),
                                                        thenBody = listOf(CContinue),
                                                    )
                                                )
                                                // Check if entity occupies this tile
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_entity_tile_x"),
                                                                        CVar("i"),
                                                                    ),
                                                                    "==",
                                                                    nxVar,
                                                                ),
                                                                "&&",
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_entity_tile_y"),
                                                                        CVar("i"),
                                                                    ),
                                                                    "==",
                                                                    nyVar,
                                                                ),
                                                            ),
                                                        thenBody = listOf(CReturn(CVar("i"))),
                                                    )
                                                )
                                            },
                                    )
                                )
                            },
                    )
                )
                // HITBOX path (Gap 2) — AABB pixel collision for HITBOX-shape actors
                if (hasHitbox) {
                    add(CComment("HITBOX: AABB pixel collision for entities with shape=HITBOX"))
                    add(CVarDecl("px", CU8, CBinaryExpr(nxVar, "*", CLiteral(tileSize))))
                    add(CVarDecl("py", CU8, CBinaryExpr(nyVar, "*", CLiteral(tileSize))))
                    add(CVarDecl("j", CU8, CLiteral(0)))
                    add(
                        CFor(
                            init = CExprStatement(CBinaryExpr(CVar("j"), "=", CLiteral(0))),
                            condition = CBinaryExpr(CVar("j"), "<", CVar("_entity_count")),
                            increment = CUnaryExpr("++", CVar("j")),
                            body =
                                buildList {
                                    // Only process HITBOX shape entities (shape == 1)
                                    add(
                                        CIf(
                                            condition =
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_entity_collision_shape"),
                                                        CVar("j"),
                                                    ),
                                                    "!=",
                                                    CLiteral(1),
                                                ),
                                            thenBody = listOf(CContinue),
                                        )
                                    )
                                    // Skip PASSTHROUGH
                                    add(
                                        CIf(
                                            condition =
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_entity_collision_mode"),
                                                        CVar("j"),
                                                    ),
                                                    "==",
                                                    CRawExpr("0xFF"),
                                                ),
                                            thenBody = listOf(CContinue),
                                        )
                                    )
                                    // AABB: entity occupies its tile at pixel coords (entity_tile *
                                    // tileSize, tileSize x tileSize)
                                    add(
                                        CVarDecl(
                                            "ex",
                                            CU8,
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_entity_tile_x"), CVar("j")),
                                                "*",
                                                CLiteral(tileSize),
                                            ),
                                        )
                                    )
                                    add(
                                        CVarDecl(
                                            "ey",
                                            CU8,
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_entity_tile_y"), CVar("j")),
                                                "*",
                                                CLiteral(tileSize),
                                            ),
                                        )
                                    )
                                    // AABB overlap: px < ex+tileSize && px+tileSize > ex && py <
                                    // ey+tileSize && py+tileSize > ey
                                    val aabb =
                                        CBinaryExpr(
                                            CBinaryExpr(
                                                CBinaryExpr(
                                                    CVar("px"),
                                                    "<",
                                                    CBinaryExpr(
                                                        CVar("ex"),
                                                        "+",
                                                        CLiteral(tileSize),
                                                    ),
                                                ),
                                                "&&",
                                                CBinaryExpr(
                                                    CBinaryExpr(
                                                        CVar("px"),
                                                        "+",
                                                        CLiteral(tileSize),
                                                    ),
                                                    ">",
                                                    CVar("ex"),
                                                ),
                                            ),
                                            "&&",
                                            CBinaryExpr(
                                                CBinaryExpr(
                                                    CVar("py"),
                                                    "<",
                                                    CBinaryExpr(
                                                        CVar("ey"),
                                                        "+",
                                                        CLiteral(tileSize),
                                                    ),
                                                ),
                                                "&&",
                                                CBinaryExpr(
                                                    CBinaryExpr(
                                                        CVar("py"),
                                                        "+",
                                                        CLiteral(tileSize),
                                                    ),
                                                    ">",
                                                    CVar("ey"),
                                                ),
                                            ),
                                        )
                                    add(
                                        CIf(condition = aabb, thenBody = listOf(CReturn(CVar("j"))))
                                    )
                                },
                        )
                    )
                }
                add(CReturn(CRawExpr("0xFF")))
            }
        return CFunction(
            name = "_entity_check",
            returnType = CU8,
            params = listOf(CParam("nx", CU8), CParam("ny", CU8)),
            body = checkBody,
        )
    }

    @Suppress("LongMethod")
    private fun buildEntityHandleBlockFunction(actors: List<ActorIR>): CFunction {
        val entityIdParam = CVar("entity_id")
        val directionParam = CVar("direction")
        val modeSwitchCases =
            buildList<CSwitchCase> {
                // BLOCK (ordinal 0): bump feedback
                add(
                    CSwitchCase(
                        value = CLiteral(EntityCollisionMode.BLOCK.ordinal),
                        body =
                            buildList {
                                add(CComment("BLOCK: stop movement, emit bump feedback"))
                                add(CExprStatement(CCall("_entity_bump_feedback", emptyList())))
                                add(CBreak)
                            },
                    )
                )
                // PASSTHROUGH (ordinal 1): no-op (should not reach here but guard)
                add(
                    CSwitchCase(
                        value = CLiteral(EntityCollisionMode.PASSTHROUGH.ordinal),
                        body = listOf(CBreak),
                    )
                )
                // BLOCK_AND_TRIGGER (ordinal 2): Gap 1 — set _blocking_entity_id before callback
                val blockAndTriggerOps =
                    actors
                        .mapNotNull { it.entityCollision }
                        .filter { it.mode == EntityCollisionMode.BLOCK_AND_TRIGGER }
                        .flatMap { it.onBlockedStatements }
                        .map { op -> ScriptOpVisitor.visit(op) }
                add(
                    CSwitchCase(
                        value = CLiteral(EntityCollisionMode.BLOCK_AND_TRIGGER.ordinal),
                        body =
                            buildList {
                                add(
                                    CComment(
                                        "BLOCK_AND_TRIGGER: set _blocking_entity_id before callback (Gap 1)"
                                    )
                                )
                                add(
                                    CExprStatement(
                                        CBinaryExpr(CVar("_blocking_entity_id"), "=", entityIdParam)
                                    )
                                )
                                add(CExprStatement(CCall("_entity_bump_feedback", emptyList())))
                                addAll(blockAndTriggerOps)
                                add(CBreak)
                            },
                    )
                )
                // OVERLAP_TRIGGER (ordinal 3): emit onOverlap ops, movement continues (caller
                // handles)
                val overlapOps =
                    actors
                        .mapNotNull { it.entityCollision }
                        .filter { it.mode == EntityCollisionMode.OVERLAP_TRIGGER }
                        .flatMap { it.onOverlapStatements }
                        .map { op -> ScriptOpVisitor.visit(op) }
                add(
                    CSwitchCase(
                        value = CLiteral(EntityCollisionMode.OVERLAP_TRIGGER.ordinal),
                        body =
                            buildList {
                                add(
                                    CComment(
                                        "OVERLAP_TRIGGER: allow movement, fire overlap callback"
                                    )
                                )
                                addAll(overlapOps)
                                add(CBreak)
                            },
                    )
                )
                // PUSH (ordinal 4): Gap 1 — set _pushed_entity_id/_push_direction, try to push
                val pushOps =
                    actors
                        .mapNotNull { it.entityCollision }
                        .filter { it.mode == EntityCollisionMode.PUSH }
                        .flatMap { it.onPushedStatements }
                        .map { op -> ScriptOpVisitor.visit(op) }
                add(
                    CSwitchCase(
                        value = CLiteral(EntityCollisionMode.PUSH.ordinal),
                        body =
                            buildList {
                                add(
                                    CComment(
                                        "PUSH: try to move entity, set _pushed_entity_id/_push_direction (Gap 1)"
                                    )
                                )
                                // Push direction constraint check (Gap B)
                                add(
                                    CVarDecl(
                                        "push_dir",
                                        CU8,
                                        CArrayAccess(CVar("_entity_push_dir"), entityIdParam),
                                    )
                                )
                                // HORIZONTAL_ONLY (1): reject UP(0) or DOWN(1)
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("push_dir"),
                                                "==",
                                                CLiteral(PushDirection.HORIZONTAL_ONLY.ordinal),
                                            ),
                                        thenBody =
                                            listOf(
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                directionParam,
                                                                "==",
                                                                CLiteral(0),
                                                            ),
                                                            "||",
                                                            CBinaryExpr(
                                                                directionParam,
                                                                "==",
                                                                CLiteral(1),
                                                            ),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CCall(
                                                                    "_entity_bump_feedback",
                                                                    emptyList(),
                                                                )
                                                            ),
                                                            CBreak,
                                                        ),
                                                )
                                            ),
                                    )
                                )
                                // VERTICAL_ONLY (2): reject LEFT(2) or RIGHT(3)
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("push_dir"),
                                                "==",
                                                CLiteral(PushDirection.VERTICAL_ONLY.ordinal),
                                            ),
                                        thenBody =
                                            listOf(
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                directionParam,
                                                                "==",
                                                                CLiteral(2),
                                                            ),
                                                            "||",
                                                            CBinaryExpr(
                                                                directionParam,
                                                                "==",
                                                                CLiteral(3),
                                                            ),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CCall(
                                                                    "_entity_bump_feedback",
                                                                    emptyList(),
                                                                )
                                                            ),
                                                            CBreak,
                                                        ),
                                                )
                                            ),
                                    )
                                )
                                // SPECIFIC (3): check bitmask
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("push_dir"),
                                                "==",
                                                CLiteral(PushDirection.SPECIFIC.ordinal),
                                            ),
                                        thenBody =
                                            listOf(
                                                CIf(
                                                    condition =
                                                        CUnaryExpr(
                                                            "!",
                                                            CBinaryExpr(
                                                                CArrayAccess(
                                                                    CVar("_entity_push_allowed"),
                                                                    entityIdParam,
                                                                ),
                                                                "&",
                                                                CBinaryExpr(
                                                                    CLiteral(1),
                                                                    "<<",
                                                                    directionParam,
                                                                ),
                                                            ),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CCall(
                                                                    "_entity_bump_feedback",
                                                                    emptyList(),
                                                                )
                                                            ),
                                                            CBreak,
                                                        ),
                                                )
                                            ),
                                    )
                                )
                                // ANY (0) falls through — no constraint
                                // Calculate push destination based on direction
                                // (0=UP,1=DOWN,2=LEFT,3=RIGHT)
                                add(
                                    CVarDecl(
                                        "ptx",
                                        CU8,
                                        CArrayAccess(CVar("_entity_tile_x"), entityIdParam),
                                    )
                                )
                                add(
                                    CVarDecl(
                                        "pty",
                                        CU8,
                                        CArrayAccess(CVar("_entity_tile_y"), entityIdParam),
                                    )
                                )
                                add(
                                    CSwitch(
                                        expr = directionParam,
                                        cases =
                                            listOf(
                                                CSwitchCase(
                                                    CLiteral(0),
                                                    listOf(
                                                        CExprStatement(
                                                            CUnaryExpr("--", CVar("pty"))
                                                        ),
                                                        CBreak,
                                                    ),
                                                ),
                                                CSwitchCase(
                                                    CLiteral(1),
                                                    listOf(
                                                        CExprStatement(
                                                            CUnaryExpr("++", CVar("pty"))
                                                        ),
                                                        CBreak,
                                                    ),
                                                ),
                                                CSwitchCase(
                                                    CLiteral(2),
                                                    listOf(
                                                        CExprStatement(
                                                            CUnaryExpr("--", CVar("ptx"))
                                                        ),
                                                        CBreak,
                                                    ),
                                                ),
                                                CSwitchCase(
                                                    CLiteral(3),
                                                    listOf(
                                                        CExprStatement(
                                                            CUnaryExpr("++", CVar("ptx"))
                                                        ),
                                                        CBreak,
                                                    ),
                                                ),
                                                CSwitchCase(null, listOf(CBreak)),
                                            ),
                                    )
                                )
                                // Check destination is free: tile collision + entity grid
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CUnaryExpr(
                                                    "!",
                                                    CCall(
                                                        "_map_collision",
                                                        listOf(CVar("ptx"), CVar("pty")),
                                                    ),
                                                ),
                                                "&&",
                                                CBinaryExpr(
                                                    CCall(
                                                        "_entity_check",
                                                        listOf(CVar("ptx"), CVar("pty")),
                                                    ),
                                                    "==",
                                                    CRawExpr("0xFF"),
                                                ),
                                            ),
                                        thenBody =
                                            buildList {
                                                // Remove from old position, register at new
                                                // position
                                                add(
                                                    CExprStatement(
                                                        CCall(
                                                            "_entity_remove",
                                                            listOf(entityIdParam),
                                                        )
                                                    )
                                                )
                                                add(
                                                    CExprStatement(
                                                        CCall(
                                                            "_entity_register",
                                                            listOf(
                                                                entityIdParam,
                                                                CVar("ptx"),
                                                                CVar("pty"),
                                                                CArrayAccess(
                                                                    CVar("_entity_collision_mode"),
                                                                    entityIdParam,
                                                                ),
                                                            ),
                                                        )
                                                    )
                                                )
                                                // Gap 1: set globals before onPushed callback
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("_pushed_entity_id"),
                                                            "=",
                                                            entityIdParam,
                                                        )
                                                    )
                                                )
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("_push_direction"),
                                                            "=",
                                                            directionParam,
                                                        )
                                                    )
                                                )
                                                addAll(pushOps)
                                            },
                                        elseBody =
                                            listOf(
                                                // Push blocked: emit bump feedback
                                                CExprStatement(
                                                    CCall("_entity_bump_feedback", emptyList())
                                                )
                                            ),
                                    )
                                )
                                add(CBreak)
                            },
                    )
                )
                // Default: no-op
                add(CSwitchCase(null, listOf(CBreak)))
            }
        val handleBlockBody =
            listOf(
                CSwitch(
                    expr = CArrayAccess(CVar("_entity_collision_mode"), entityIdParam),
                    cases = modeSwitchCases,
                )
            )
        return CFunction(
            name = "_entity_handle_block",
            returnType = CVoid,
            params =
                listOf(
                    CParam("entity_id", CU8),
                    CParam("nx", CU8),
                    CParam("ny", CU8),
                    CParam("direction", CU8),
                ),
            body = handleBlockBody,
        )
    }

    private fun buildEntitySetCollisionModeFunction(): CFunction {
        val entityIdParam = CVar("entity_id")
        val modeParam = CVar("mode")
        val setModeBody =
            listOf(
                CExprStatement(
                    CBinaryExpr(
                        CArrayAccess(CVar("_entity_collision_mode"), entityIdParam),
                        "=",
                        modeParam,
                    )
                )
            )
        return CFunction(
            name = "_entity_set_collision_mode",
            returnType = CVoid,
            params = listOf(CParam("entity_id", CU8), CParam("mode", CU8)),
            body = setModeBody,
        )
    }

    private fun buildEntityBumpFeedbackFunction(): CFunction =
        CFunction(
            name = "_entity_bump_feedback",
            returnType = CVoid,
            body = listOf(CComment("bump feedback: play sound/visual indicator")),
        )

    /**
     * Build `exploration_encounter_check_{id}()` with weighted random encounter dispatch.
     *
     * Supports three optional entry guards that stack:
     * - `conditionFlag` — entry only fires when `_flag_{id}` is set (story-progression gate)
     * - `minLevel` — entry only fires when `_player_level >= minLevel` (inclusive)
     * - `maxLevel` — entry only fires when `_player_level < maxLevel` (exclusive)
     *
     * The accumulator (`acc`) advances regardless of guard conditions so that the weight
     * distribution stays stable even when some entries are filtered by level or flag.
     */
    private fun buildEncounterCheckFunction(sanitizedId: String, zones: List<ZoneIR>): CFunction {
        val body =
            buildList<CStatement> {
                add(CIf(condition = CVar("_current_zone_safe"), thenBody = listOf(CReturn(null))))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CVar("_exploration_step_count"),
                                "<",
                                CVar("_encounter_safe_steps"),
                            ),
                        thenBody = listOf(CReturn(null)),
                    )
                )
                val allEntries: List<EncounterEntryIR> = zones.flatMap { zone ->
                    zone.encounterTable?.entries ?: emptyList()
                }
                addAll(buildEncounterRollStatements(allEntries))
            }
        return CFunction(
            name = "exploration_encounter_check_$sanitizedId",
            returnType = CVoid,
            body = body,
        )
    }

    /**
     * Builds the roll/acc variable declarations and per-entry weight-check dispatch for encounter
     * resolution. Returns an empty list when there are no encounter table entries.
     */
    private fun buildEncounterRollStatements(allEntries: List<EncounterEntryIR>): List<CStatement> {
        if (allEntries.isEmpty()) return emptyList()
        val totalWeight = allEntries.sumOf { it.weight }
        val result = mutableListOf<CStatement>()
        result.add(CVarDecl("roll", CU8, null))
        result.add(
            CExprStatement(
                CBinaryExpr(
                    CVar("roll"),
                    "=",
                    CBinaryExpr(CCall("rand", emptyList()), "%", CLiteral(totalWeight)),
                )
            )
        )
        result.add(CVarDecl("acc", CU8, CLiteral(0)))
        for ((idx, entry) in allEntries.withIndex()) {
            val weightCheck =
                CBinaryExpr(
                    CVar("roll"),
                    "<",
                    CBinaryExpr(CVar("acc"), "+", CLiteral(entry.weight)),
                )
            val fireBody: List<CStatement> =
                listOf(
                    CExprStatement(CBinaryExpr(CVar("_encounter_triggered"), "=", CLiteral(1))),
                    CExprStatement(CBinaryExpr(CVar("_encounter_id"), "=", CLiteral(idx))),
                    CReturn(null),
                )
            val guard = buildEncounterEntryGuard(entry)
            if (guard != null) {
                result.add(
                    CIf(
                        condition = guard,
                        thenBody = listOf(CIf(condition = weightCheck, thenBody = fireBody)),
                    )
                )
            } else {
                result.add(CIf(condition = weightCheck, thenBody = fireBody))
            }
            result.add(CExprStatement(CBinaryExpr(CVar("acc"), "+=", CLiteral(entry.weight))))
        }
        return result
    }

    /**
     * Builds the combined guard condition for a single encounter entry: optional conditionFlag AND
     * optional min/max level range checks stacked with &&. Returns null when there is no guard.
     */
    private fun buildEncounterEntryGuard(entry: EncounterEntryIR): CExpr? {
        var guard: CExpr? = null
        val conditionFlag: String? = entry.conditionFlag
        if (conditionFlag != null) {
            guard = CVar("_flag_${conditionFlag}")
        }
        val minLvl: Int? = entry.minLevel
        if (minLvl != null) {
            val minCheck = CBinaryExpr(CVar("_player_level"), ">=", CLiteral(minLvl))
            guard = if (guard != null) CBinaryExpr(guard, "&&", minCheck) else minCheck
        }
        val maxLvl: Int? = entry.maxLevel
        if (maxLvl != null) {
            val maxCheck = CBinaryExpr(CVar("_player_level"), "<", CLiteral(maxLvl))
            guard = if (guard != null) CBinaryExpr(guard, "&&", maxCheck) else maxCheck
        }
        return guard
    }

    /**
     * Build `zone_load_{id}(zone_id)` with tileset reuse guard, tilemap load, onEnter, safe zone.
     *
     * When zone tilemap data is in a non-zero ROM bank (from [zoneBankAllocation]), emits
     * `SWITCH_ROM(bankN)` before the `set_bkg_tiles()` call and `SWITCH_ROM(1)` to restore the
     * scene bank afterward. Zones in bank 0 (HOME) receive no bank-switching.
     */
    private fun buildZoneLoadFunction(sanitizedId: String, zones: List<ZoneIR>): CFunction {
        val body =
            buildList<CStatement> {
                add(CExprStatement(CBinaryExpr(CVar("_current_zone_id"), "=", CVar("zone_id"))))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_current_tileset_id"), "!=", CVar("zone_id")),
                        thenBody =
                            listOf(
                                CComment("load tileset for zone"),
                                CExprStatement(
                                    CBinaryExpr(CVar("_current_tileset_id"), "=", CVar("zone_id"))
                                ),
                            ),
                    )
                )
                if (zones.isNotEmpty()) {
                    val tileLoadCases = zones.mapIndexed { idx, zone ->
                        val zs = zone.id.replace('-', '_').replace(' ', '_')
                        // REQ-14: mapWidth is nullable (auto sentinel); fall back to 20 (the
                        // standard
                        // 20×18 GB screen width) for tileData-size calculation on legacy zones.
                        val effectiveMapWidth = zone.mapWidth ?: 20
                        val tileRows =
                            if (zone.tileData.isEmpty()) 128
                            else zone.tileData.size / effectiveMapWidth
                        val bankNum = zoneBankAllocation[zone.id] ?: 0
                        val caseBody =
                            buildList<CStatement> {
                                // Emit SWITCH_ROM(bankN) before tilemap data access when in
                                // non-zero bank
                                if (bankNum > 0) {
                                    add(CRawCode("SWITCH_ROM($bankNum);"))
                                }
                                add(
                                    CExprStatement(
                                        CCall(
                                            "set_bkg_tiles",
                                            listOf(
                                                CLiteral(0),
                                                CLiteral(0),
                                                CLiteral(effectiveMapWidth),
                                                CLiteral(tileRows),
                                                CVar("_zone_${zs}_tiles"),
                                            ),
                                        )
                                    )
                                )
                                // Restore scene bank (bank 1) after tilemap data access
                                if (bankNum > 0) {
                                    add(CRawCode("SWITCH_ROM(1);"))
                                }
                                add(CBreak)
                            }
                        CSwitchCase(value = CLiteral(idx), body = caseBody)
                    }
                    add(
                        CSwitch(
                            expr = CVar("zone_id"),
                            cases =
                                tileLoadCases + CSwitchCase(value = null, body = listOf(CBreak)),
                        )
                    )
                }
                val zonesWithOnEnter = zones.filter { it.onEnter.isNotEmpty() }
                if (zonesWithOnEnter.isNotEmpty()) {
                    val onEnterCases: List<CSwitchCase> = zones.mapIndexedNotNull { idx, zone ->
                        val onEnterOps: List<ScriptOp> = zone.onEnter
                        if (onEnterOps.isNotEmpty()) {
                            CSwitchCase(
                                value = CLiteral(idx),
                                body =
                                    onEnterOps.map { op -> ScriptOpVisitor.visit(op) } +
                                        listOf(CBreak),
                            )
                        } else null
                    }
                    add(
                        CSwitch(
                            expr = CVar("zone_id"),
                            cases = onEnterCases + CSwitchCase(value = null, body = listOf(CBreak)),
                        )
                    )
                }
                if (zones.isNotEmpty()) {
                    val safeZoneCases = zones.mapIndexed { idx, zone ->
                        CSwitchCase(
                            value = CLiteral(idx),
                            body =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_current_zone_safe"),
                                            "=",
                                            CLiteral(if (zone.isSafeZone) 1 else 0),
                                        )
                                    ),
                                    CBreak,
                                ),
                        )
                    }
                    add(
                        CSwitch(
                            expr = CVar("zone_id"),
                            cases =
                                safeZoneCases +
                                    CSwitchCase(
                                        value = null,
                                        body =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_current_zone_safe"),
                                                        "=",
                                                        CLiteral(0),
                                                    )
                                                ),
                                                CBreak,
                                            ),
                                    ),
                        )
                    )
                }
            }
        return CFunction(
            name = "zone_load_$sanitizedId",
            returnType = CVoid,
            params = listOf(CParam("zone_id", CU8)),
            body = body,
        )
    }

    /**
     * Build `zone_transition_{id}(target_zone_id, edge, entry_x, entry_y)`. Gap 8: onExit dispatch
     * before loading new zone. Gap 7: player position from explicit coords (0xFF=not set) or edge
     * auto-mapping.
     */
    private fun buildZoneTransitionFunction(
        sanitizedId: String,
        zones: List<ZoneIR>,
        playerX: CVar,
        playerY: CVar,
    ): CFunction {
        val body =
            buildList<CStatement> {
                buildZoneOnExitSwitch(zones)?.let { add(it) }
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("entry_x"), "!=", CLiteral(0xFF)),
                        thenBody =
                            listOf(
                                CExprStatement(CBinaryExpr(playerX, "=", CVar("entry_x"))),
                                CExprStatement(CBinaryExpr(playerY, "=", CVar("entry_y"))),
                            ),
                        elseBody = listOf(buildEdgeAutoPositionSwitch(playerX, playerY)),
                    )
                )
                add(CExprStatement(CCall("zone_load_$sanitizedId", listOf(CVar("target_zone_id")))))
            }
        return CFunction(
            name = "zone_transition_$sanitizedId",
            returnType = CVoid,
            params =
                listOf(
                    CParam("target_zone_id", CU8),
                    CParam("edge", CU8),
                    CParam("entry_x", CU8),
                    CParam("entry_y", CU8),
                ),
            body = body,
        )
    }

    /**
     * Builds the onExit switch dispatch for zone_transition; returns null when no zone has onExit.
     */
    private fun buildZoneOnExitSwitch(zones: List<ZoneIR>): CSwitch? {
        val zonesWithOnExit = zones.filter { it.onExit.isNotEmpty() }
        if (zonesWithOnExit.isEmpty()) return null
        val onExitCases: List<CSwitchCase> = zones.mapIndexedNotNull { idx, zone ->
            val onExitOps: List<ScriptOp> = zone.onExit
            if (onExitOps.isNotEmpty()) {
                CSwitchCase(
                    value = CLiteral(idx),
                    body = onExitOps.map { op -> ScriptOpVisitor.visit(op) } + listOf(CBreak),
                )
            } else null
        }
        return CSwitch(
            expr = CVar("_current_zone_id"),
            cases = onExitCases + CSwitchCase(value = null, body = listOf(CBreak)),
        )
    }

    /**
     * Builds the edge-auto-position switch used in zone_transition when entry coords are absent.
     */
    private fun buildEdgeAutoPositionSwitch(playerX: CVar, playerY: CVar): CSwitch =
        CSwitch(
            expr = CVar("edge"),
            cases =
                listOf(
                    CSwitchCase(
                        value = CLiteral(TransitionEdge.EAST.ordinal),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(playerX, "=", CLiteral(0))),
                                CBreak,
                            ),
                    ),
                    CSwitchCase(
                        value = CLiteral(TransitionEdge.WEST.ordinal),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(playerX, "=", CLiteral(31))),
                                CBreak,
                            ),
                    ),
                    CSwitchCase(
                        value = CLiteral(TransitionEdge.NORTH.ordinal),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(playerY, "=", CLiteral(31))),
                                CBreak,
                            ),
                    ),
                    CSwitchCase(
                        value = CLiteral(TransitionEdge.SOUTH.ordinal),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(playerY, "=", CLiteral(0))),
                                CBreak,
                            ),
                    ),
                    CSwitchCase(value = null, body = listOf(CBreak)),
                ),
        )

    /**
     * Build `zone_check_edges_{id}()` with flag-gated edge transition dispatch.
     *
     * Checks whether the player is at a map edge that has a configured [ZoneTransitionIR]. When an
     * edge is reached, the transition fires immediately — unless a [ZoneTransitionIR.conditionFlag]
     * is set, in which case the flag must be active for the transition to proceed. If the flag is
     * not set, the player is silently blocked (no movement).
     *
     * Generated when at least one zone has at least one transition edge defined. The function is
     * called from `exploration_step_{id}()` each step.
     *
     * Zone indices match the order in [GameIR.zones] (same as `zone_load_{id}`).
     *
     * Generated C pattern (conditionFlag = "bossDefeated"):
     * ```c
     * void zone_check_edges_dungeon(void) {
     *   switch (_current_zone_id) {
     *     case 0: // floor1
     *       if (_player_y == 0) {
     *         if (!_flag_bossDefeated) return;
     *         zone_transition_dungeon(1, 2, 0xFF, 0xFF);
     *         return;
     *       }
     *       break;
     *   }
     * }
     * ```
     *
     * @param sanitizedId Exploration system ID with hyphens/spaces replaced by underscores.
     * @param zones All zones in the game; only zones with transitions generate switch cases.
     * @param playerX C variable reference for the player X tile coordinate.
     * @param playerY C variable reference for the player Y tile coordinate.
     */
    private fun buildZoneCheckEdgesFunction(
        sanitizedId: String,
        zones: List<ZoneIR>,
        playerX: CVar,
        playerY: CVar,
    ): CFunction {
        val zonesWithTransitions = zones.filter { it.transitions.isNotEmpty() }
        val body =
            buildList<CStatement> {
                if (zonesWithTransitions.isNotEmpty()) {
                    val switchCases: List<CSwitchCase> = zones.mapIndexedNotNull { zoneIdx, zone ->
                        if (zone.transitions.isEmpty()) return@mapIndexedNotNull null
                        val caseBody =
                            buildList<CStatement> {
                                for (transition in zone.transitions) {
                                    val edge = transition.edge ?: continue
                                    // Edge condition: player at map boundary
                                    val edgeCondition: CExpr =
                                        when (edge) {
                                            TransitionEdge.NORTH ->
                                                CBinaryExpr(playerY, "==", CLiteral(0))
                                            TransitionEdge.SOUTH ->
                                                CBinaryExpr(playerY, "==", CLiteral(31))
                                            TransitionEdge.WEST ->
                                                CBinaryExpr(playerX, "==", CLiteral(0))
                                            TransitionEdge.EAST ->
                                                CBinaryExpr(playerX, "==", CLiteral(31))
                                        }
                                    // Target zone index in zones list
                                    val targetIdx = zones.indexOfFirst {
                                        it.id == transition.targetZoneId
                                    }
                                    if (targetIdx < 0) return@mapIndexedNotNull null
                                    // entryX / entryY (0xFF = not set, use edge auto-mapping)
                                    val entryX = CLiteral(transition.entryX ?: 0xFF)
                                    val entryY = CLiteral(transition.entryY ?: 0xFF)
                                    // Build transition body: optional flag guard + call
                                    val transitionCall =
                                        CExprStatement(
                                            CCall(
                                                "zone_transition_$sanitizedId",
                                                listOf(
                                                    CLiteral(targetIdx),
                                                    CLiteral(edge.ordinal),
                                                    entryX,
                                                    entryY,
                                                ),
                                            )
                                        )
                                    val conditionFlag = transition.conditionFlag
                                    val transitionBody: List<CStatement> =
                                        if (conditionFlag != null) {
                                            listOf(
                                                // Block player if flag not set
                                                CIf(
                                                    condition =
                                                        CUnaryExpr(
                                                            "!",
                                                            CVar("_flag_${conditionFlag}"),
                                                        ),
                                                    thenBody = listOf(CReturn(null)),
                                                ),
                                                transitionCall,
                                                CReturn(null),
                                            )
                                        } else {
                                            listOf(transitionCall, CReturn(null))
                                        }
                                    add(CIf(condition = edgeCondition, thenBody = transitionBody))
                                }
                                add(CBreak)
                            }
                        CSwitchCase(value = CLiteral(zoneIdx), body = caseBody)
                    }
                    add(
                        CSwitch(
                            expr = CVar("_current_zone_id"),
                            cases = switchCases + CSwitchCase(value = null, body = listOf(CBreak)),
                        )
                    )
                }
            }
        return CFunction(name = "zone_check_edges_$sanitizedId", returnType = CVoid, body = body)
    }

    /**
     * Build per-zone object handler functions and a per-zone dispatch function.
     *
     * For each zone that has at least one [ZoneObjectIR], generates:
     * - Per-object handler functions: `zone_{type}_{objectId}_interact()` where `{type}` is
     *   `chest`, `sign`, `sconce`, `npc`, or `lever`. Each handler contains the object's
     *   [ZoneObjectIR.onInteract] script ops, preceded by a used-flag guard if
     *   [ZoneObjectIR.usedFlagId] is set.
     * - A per-zone dispatch function `zone_try_interact_{zoneId}(x, y)` that routes
     *   `tryInteractWithObject(x, y)` calls to the matching per-object handler.
     *
     * Special handling per object type:
     * - **ChestObjectIR**: used-flag guard prevents re-opening; sets used flag on first open
     * - **SconceObjectIR**: generates toggle logic with `_sconce_{id}_lit` state variable; calls
     *   `onLit` or `onExtinguished` based on current state
     * - **LeverObjectIR**: generates toggle logic with `_lever_{id}_active` state variable; calls
     *   `onActivate` or `onDeactivate` based on direction
     * - **NpcObjectIR**: visibility-flag guard (`visibleFlagId`) prevents interaction when hidden
     * - **SignObjectIR**: no state; directly runs `onInteract` ops
     *
     * @param zones All zones in the game; only zones with `objects.isNotEmpty()` generate code.
     * @return Flat list of all generated functions (per-object handlers + dispatch functions).
     */
    private fun buildZoneObjectFunctions(zones: List<ZoneIR>): List<CFunction> {
        val result = mutableListOf<CFunction>()
        for (zone in zones) {
            if (zone.objects.isEmpty()) continue
            val zs = zone.id.replace('-', '_').replace(' ', '_')

            // Per-object handler functions
            for (obj in zone.objects) {
                val objId = obj.id.replace('-', '_').replace(' ', '_')
                val handler: CFunction =
                    when (obj) {
                        is ChestObjectIR -> buildChestHandlerFunction(zs, objId, obj)
                        is SignObjectIR -> buildSignHandlerFunction(zs, objId, obj)
                        is SconceObjectIR -> buildSconceHandlerFunction(zs, objId, obj)
                        is NpcObjectIR -> buildNpcHandlerFunction(zs, objId, obj)
                        is LeverObjectIR -> buildLeverHandlerFunction(zs, objId, obj)
                    }
                result += handler
            }

            // Per-zone dispatch function: zone_try_interact_{zoneId}(x, y)
            val dispatchBody =
                buildList<CStatement> {
                    for (obj in zone.objects) {
                        val objId = obj.id.replace('-', '_').replace(' ', '_')
                        val typePrefix =
                            when (obj) {
                                is ChestObjectIR -> "chest"
                                is SignObjectIR -> "sign"
                                is SconceObjectIR -> "sconce"
                                is NpcObjectIR -> "npc"
                                is LeverObjectIR -> "lever"
                            }
                        val posCheck =
                            CBinaryExpr(
                                CBinaryExpr(CVar("x"), "==", CLiteral(obj.x)),
                                "&&",
                                CBinaryExpr(CVar("y"), "==", CLiteral(obj.y)),
                            )
                        add(
                            CIf(
                                condition = posCheck,
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CCall(
                                                "zone_${typePrefix}_${zs}_${objId}_interact",
                                                emptyList(),
                                            )
                                        ),
                                        CReturn(null),
                                    ),
                            )
                        )
                    }
                }
            result +=
                CFunction(
                    name = "zone_try_interact_$zs",
                    returnType = CVoid,
                    params = listOf(CParam("x", CU8), CParam("y", CU8)),
                    body = dispatchBody,
                )
        }
        return result.toList()
    }

    /** Generate `zone_chest_{zoneId}_{id}_interact()` — one-shot open with optional used-flag. */
    private fun buildChestHandlerFunction(
        zoneId: String,
        objId: String,
        obj: ChestObjectIR,
    ): CFunction {
        val body =
            buildList<CStatement> {
                val usedFlag = obj.usedFlagId
                if (usedFlag != null) {
                    // Guard: skip if already opened
                    add(
                        CIf(condition = CVar("_flag_${usedFlag}"), thenBody = listOf(CReturn(null)))
                    )
                    // Mark as opened
                    add(CExprStatement(CBinaryExpr(CVar("_flag_${usedFlag}"), "=", CLiteral(1))))
                }
                for (op in obj.onInteract) {
                    add(ScriptOpVisitor.visit(op))
                }
            }
        return CFunction(
            name = "zone_chest_${zoneId}_${objId}_interact",
            returnType = CVoid,
            body = body,
        )
    }

    /** Generate `zone_sign_{zoneId}_{id}_interact()` — re-readable, runs onInteract each time. */
    private fun buildSignHandlerFunction(
        zoneId: String,
        objId: String,
        obj: SignObjectIR,
    ): CFunction {
        val body = obj.onInteract.map { op -> ScriptOpVisitor.visit(op) }
        return CFunction(
            name = "zone_sign_${zoneId}_${objId}_interact",
            returnType = CVoid,
            body = body,
        )
    }

    /**
     * Generate `zone_sconce_{zoneId}_{id}_interact()` — toggle lit state with `_sconce_{id}_lit`.
     *
     * When toggling from unlit→lit: runs `onLit` ops. When toggling from lit→unlit: runs
     * `onExtinguished` ops.
     */
    private fun buildSconceHandlerFunction(
        zoneId: String,
        objId: String,
        obj: SconceObjectIR,
    ): CFunction {
        val litVar = "_sconce_${objId}_lit"
        val body =
            buildList<CStatement> {
                // Toggle state
                val toggleBody =
                    buildList<CStatement> {
                        add(CExprStatement(CBinaryExpr(CVar(litVar), "=", CLiteral(1))))
                        for (op in obj.onLit) add(ScriptOpVisitor.visit(op))
                    }
                val extinguishBody =
                    buildList<CStatement> {
                        add(CExprStatement(CBinaryExpr(CVar(litVar), "=", CLiteral(0))))
                        for (op in obj.onExtinguished) add(ScriptOpVisitor.visit(op))
                    }
                add(
                    CIf(
                        condition = CUnaryExpr("!", CVar(litVar)),
                        thenBody = toggleBody,
                        elseBody = extinguishBody,
                    )
                )
                for (op in obj.onInteract) add(ScriptOpVisitor.visit(op))
            }
        return CFunction(
            name = "zone_sconce_${zoneId}_${objId}_interact",
            returnType = CVoid,
            body = body,
        )
    }

    /**
     * Generate `zone_npc_{zoneId}_{id}_interact()` — optional visibility-flag guard.
     *
     * When [NpcObjectIR.visibleFlagId] is set:
     * - Default (`visibleWhenFlagUnset = false`): NPC only interactable when flag IS set
     * - `visibleWhenFlagUnset = true`: NPC only interactable when flag IS NOT set
     */
    private fun buildNpcHandlerFunction(
        zoneId: String,
        objId: String,
        obj: NpcObjectIR,
    ): CFunction {
        val body =
            buildList<CStatement> {
                val visibleFlag = obj.visibleFlagId
                if (visibleFlag != null) {
                    val flagVar = CVar("_flag_${visibleFlag}")
                    val blockCondition: CExpr =
                        if (obj.visibleWhenFlagUnset) {
                            // Interactable only when flag NOT set → block when flag IS set
                            flagVar
                        } else {
                            // Interactable only when flag IS set → block when flag NOT set
                            CUnaryExpr("!", flagVar)
                        }
                    add(CIf(condition = blockCondition, thenBody = listOf(CReturn(null))))
                }
                val usedFlag = obj.usedFlagId
                if (usedFlag != null) {
                    add(CExprStatement(CBinaryExpr(CVar("_flag_${usedFlag}"), "=", CLiteral(1))))
                }
                for (op in obj.onInteract) add(ScriptOpVisitor.visit(op))
            }
        return CFunction(
            name = "zone_npc_${zoneId}_${objId}_interact",
            returnType = CVoid,
            body = body,
        )
    }

    /**
     * Generate `zone_lever_{zoneId}_{id}_interact()` — toggle on/off with `_lever_{id}_active`.
     *
     * When toggling from off→on: runs `onActivate` ops. When toggling from on→off: runs
     * `onDeactivate` ops.
     */
    private fun buildLeverHandlerFunction(
        zoneId: String,
        objId: String,
        obj: LeverObjectIR,
    ): CFunction {
        val activeVar = "_lever_${objId}_active"
        val body =
            buildList<CStatement> {
                val activateBody =
                    buildList<CStatement> {
                        add(CExprStatement(CBinaryExpr(CVar(activeVar), "=", CLiteral(1))))
                        for (op in obj.onActivate) add(ScriptOpVisitor.visit(op))
                    }
                val deactivateBody =
                    buildList<CStatement> {
                        add(CExprStatement(CBinaryExpr(CVar(activeVar), "=", CLiteral(0))))
                        for (op in obj.onDeactivate) add(ScriptOpVisitor.visit(op))
                    }
                add(
                    CIf(
                        condition = CUnaryExpr("!", CVar(activeVar)),
                        thenBody = activateBody,
                        elseBody = deactivateBody,
                    )
                )
                for (op in obj.onInteract) add(ScriptOpVisitor.visit(op))
            }
        return CFunction(
            name = "zone_lever_${zoneId}_${objId}_interact",
            returnType = CVoid,
            body = body,
        )
    }

    // -------------------------------------------------------------------------
    // DialogSystem: delegate to buildDialogHelpers() — return empty (already generated)
    // -------------------------------------------------------------------------

    /**
     * Propagate extended [DialogSystem] configuration fields as global variables.
     *
     * The dialog helper functions (show_dialog, hide_dialog, etc.) are already generated by
     * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline.buildDialogFunctions]. This
     * visitor generates supplemental global variables for the extended config:
     * - `_dialog_default_speed` — system-level default typewriter speed
     * - `_dialog_default_border` — system-level default border style index
     *
     * Both are emitted as global UINT8 variable declarations. They serve as fallbacks when
     * individual [io.github.gbkt.core.ir.DialogDef] instances do not override these settings.
     *
     * Note: Returns empty list (no CFunction generated) — the config globals are injected into the
     * variables section of main.c via [buildSystemGlobalVars] rather than as functions. This
     * matches the pattern used by CameraSystem and ExplorationSystem globals.
     */
    override fun visitDialogSystem(system: DialogSystem): List<CFunction> {
        // Extended config propagated via buildSystemGlobalVars in GBDKPipeline.
        // _dialog_default_speed and _dialog_default_border globals generated there.
        // No CFunction output needed from this visitor.
        return emptyList()
    }

    // -------------------------------------------------------------------------
    // GenericSystem: delegate to buildSystemTriggerFunction pattern
    // -------------------------------------------------------------------------

    /**
     * Generate trigger function for a [GenericSystem].
     *
     * For `simple_battle` systems: generates a COMBAT_STATE enum and update_combat_{id}() state
     * machine function. For other system types: generates a no-op trigger stub.
     *
     * The SimpleBattle state machine replaces the previous pattern of immediately executing
     * onVictoryOps. It has 5 states: INIT, PLAYER_TURN, ENEMY_TURN, VICTORY, DEFEAT.
     */
    @Suppress("UNCHECKED_CAST")
    override fun visitGenericSystem(system: GenericSystem): List<CFunction> {
        val sanitizedId = system.id.replace('-', '_').replace(' ', '_')
        val systemType = system.config["type"] as? String

        return when (systemType) {
            "audio_mixer" -> buildAudioMixerFunctions(sanitizedId, system)
            "rpg_character_system" -> RpgVisitor(gameIR).generateCharacterStatStructs(system)
            "rpg_equipment_system" -> RpgVisitor(gameIR).generateEquipmentFunctions(system)
            "rpg_class" -> RpgVisitor(gameIR).generateClassFunctions(system)
            "rpg_ability" -> RpgVisitor(gameIR).generateAbilityFunctions(system)
            "rpg_status_effect" -> RpgVisitor(gameIR).generateStatusEffectFunctions(system)
            "rpg_monster" -> RpgVisitor(gameIR).generateMonsterAIFunctions(system)
            "rpg_merchant" -> RpgVisitor(gameIR).generateMerchantFunctions(system)
            "rpg_party_system" -> RpgVisitor(gameIR).generatePartyFunctions(system)
            "rpg_save" -> RpgVisitor(gameIR).generateRpgSaveFunctions(system)
            "rpg_ability_learning" -> RpgVisitor(gameIR).generateAbilityLearningFunctions(system)
            "rpg_loot_table" -> RpgVisitor(gameIR).generateLootTableFunctions(system)
            "rpg_crafting" -> RpgVisitor(gameIR).generateCraftingFunctions(system)
            "arpg_combat" -> RpgVisitor(gameIR).generateActionRpgFunctions(system)
            "roguelike_system" -> RpgVisitor(gameIR).generateRoguelikeFunctions(system)
            "rpg_currency" -> RpgVisitor(gameIR).generateCurrencyFunctions(system)
            "pickup_system" -> buildPickupFunctions(sanitizedId, system)
            else -> {
                // Generic no-op stub for unknown system types
                listOf(
                    CFunction(
                        name = "trigger_$sanitizedId",
                        returnType = CVoid,
                        body =
                            listOf(
                                CComment(
                                    "system '${system.id}' has no v2 implementation — no-op stub"
                                )
                            ),
                        sectionComment = "System trigger: ${system.id}",
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // AudioMixer: real NR50/NR51 register-based channel group mixing (A5)
    // -------------------------------------------------------------------------

    /**
     * Generate real C functions for audio channel group volume control using NR50/NR51 hardware
     * registers (Gap 4, Gap 5, Gap 6).
     *
     * Generated functions:
     * - `set_group_volume(group, vol)` — writes NR50_REG scaled by master volume
     * - `mute_group(group)` — clears NR51_REG bits for group channels
     * - `unmute_group(group)` — restores NR51_REG bits for group channels
     * - `set_master_volume(vol)` — updates _mixer_master_vol and recalculates NR50
     * - `fade_group(group, target_vol, frames)` — per-frame volume interpolation loop
     * - `audio_mixer_request_channel(group, priority)` — priority-based channel preemption (Gap 5)
     * - `audio_mixer_duck()` — saves music volume and ducks to configured level (Gap 6)
     * - `audio_mixer_unduck()` — restores saved music volume (Gap 6)
     * - `audio_mixer_save_state(ptr)` — writes mixer state to buffer (Gap 4)
     * - `audio_mixer_load_state(ptr)` — reads mixer state from buffer (Gap 4)
     * - `trigger_{id}()` — no-op entry point for TriggerSystem compatibility
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildAudioMixerFunctions(
        sanitizedId: String,
        system: GenericSystem,
    ): List<CFunction> {
        val groups =
            (system.config["groups"] as? List<ChannelGroupDef>)
                ?: listOf(
                    ChannelGroupDef("music", setOf(1, 2), 7, 0),
                    ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
                    ChannelGroupDef("ui", setOf(3), 7, 2),
                )
        val autoDucking = system.config["auto_ducking"] as? Boolean ?: false
        val autoDuckLevel = system.config["auto_duck_level"] as? Int ?: 3

        // ---- set_group_volume(UINT8 group, UINT8 vol) ----
        // Stores vol in _mixer_group_vol[group], scales by master volume, writes NR50_REG.
        val setGroupVolume =
            CFunction(
                name = "set_group_volume",
                returnType = CVoid,
                params = listOf(CParam("group", CU8), CParam("vol", CU8)),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("_mixer_group_vol"), CVar("group")),
                                "=",
                                CVar("vol"),
                            )
                        ),
                        CVarDecl(
                            name = "eff",
                            type = CU8,
                            initializer =
                                CBinaryExpr(
                                    CBinaryExpr(CVar("vol"), "*", CVar("_mixer_master_vol")),
                                    "/",
                                    CLiteral(7),
                                ),
                        ),
                        CExprStatement(
                            CBinaryExpr(
                                CVar("NR50_REG"),
                                "=",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("eff"), "<<", CLiteral(4)),
                                    "|",
                                    CVar("eff"),
                                ),
                            )
                        ),
                    ),
                sectionComment = "AudioMixer system: ${system.id} — NR50/NR51 register control",
            )

        // ---- mute_group(UINT8 group) ----
        // Sets _mixer_group_muted[group] = 1, clears NR51_REG bits for group channels.
        val muteGroupBody = mutableListOf<CStatement>()
        muteGroupBody +=
            CExprStatement(
                CBinaryExpr(
                    CArrayAccess(CVar("_mixer_group_muted"), CVar("group")),
                    "=",
                    CLiteral(1),
                )
            )
        val muteCases = groups.mapIndexed { idx, group ->
            CSwitchCase(
                value = CLiteral(idx),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("NR51_REG"),
                                "&=",
                                CUnaryExpr("~", CVar("_mixer_channel_mask_${group.name}")),
                            )
                        ),
                        CBreak,
                    ),
            )
        }
        muteGroupBody += CSwitch(expr = CVar("group"), cases = muteCases)
        val muteGroup =
            CFunction(
                name = "mute_group",
                returnType = CVoid,
                params = listOf(CParam("group", CU8)),
                body = muteGroupBody,
            )

        // ---- unmute_group(UINT8 group) ----
        // Sets _mixer_group_muted[group] = 0, restores NR51_REG bits for group channels.
        val unmuteGroupBody = mutableListOf<CStatement>()
        unmuteGroupBody +=
            CExprStatement(
                CBinaryExpr(
                    CArrayAccess(CVar("_mixer_group_muted"), CVar("group")),
                    "=",
                    CLiteral(0),
                )
            )
        val unmuteCases = groups.mapIndexed { idx, group ->
            CSwitchCase(
                value = CLiteral(idx),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("NR51_REG"),
                                "|=",
                                CVar("_mixer_channel_mask_${group.name}"),
                            )
                        ),
                        CBreak,
                    ),
            )
        }
        unmuteGroupBody += CSwitch(expr = CVar("group"), cases = unmuteCases)
        val unmuteGroup =
            CFunction(
                name = "unmute_group",
                returnType = CVoid,
                params = listOf(CParam("group", CU8)),
                body = unmuteGroupBody,
            )

        // ---- set_master_volume(UINT8 vol) ----
        // Updates _mixer_master_vol, re-applies volumes for all non-muted groups.
        val masterVolBody = mutableListOf<CStatement>()
        masterVolBody += CExprStatement(CBinaryExpr(CVar("_mixer_master_vol"), "=", CVar("vol")))
        groups.forEachIndexed { idx, _ ->
            masterVolBody +=
                CIf(
                    condition =
                        CBinaryExpr(
                            CArrayAccess(CVar("_mixer_group_muted"), CLiteral(idx)),
                            "==",
                            CLiteral(0),
                        ),
                    thenBody =
                        listOf(
                            CExprStatement(
                                CCall(
                                    "set_group_volume",
                                    listOf(
                                        CLiteral(idx),
                                        CArrayAccess(CVar("_mixer_group_vol"), CLiteral(idx)),
                                    ),
                                )
                            )
                        ),
                )
        }
        val setMasterVolume =
            CFunction(
                name = "set_master_volume",
                returnType = CVoid,
                params = listOf(CParam("vol", CU8)),
                body = masterVolBody,
            )

        // ---- fade_group(UINT8 group, UINT8 target_vol, UINT8 frames) ----
        // Per-frame volume interpolation using vsync() timing (C89-compatible).
        val fadeGroupBody =
            listOf(
                CVarDecl(name = "f", type = CU8, initializer = CLiteral(0)),
                CFor(
                    init = null,
                    condition = CBinaryExpr(CVar("f"), "<", CVar("frames")),
                    increment = CUnaryExpr("++", CVar("f")),
                    body =
                        listOf(
                            CVarDecl(
                                name = "cur",
                                type = CU8,
                                initializer = CArrayAccess(CVar("_mixer_group_vol"), CVar("group")),
                            ),
                            CIf(
                                condition = CBinaryExpr(CVar("cur"), "<", CVar("target_vol")),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CCall(
                                                "set_group_volume",
                                                listOf(
                                                    CVar("group"),
                                                    CBinaryExpr(CVar("cur"), "+", CLiteral(1)),
                                                ),
                                            )
                                        )
                                    ),
                                elseBody =
                                    listOf(
                                        CIf(
                                            condition =
                                                CBinaryExpr(CVar("cur"), ">", CVar("target_vol")),
                                            thenBody =
                                                listOf(
                                                    CExprStatement(
                                                        CCall(
                                                            "set_group_volume",
                                                            listOf(
                                                                CVar("group"),
                                                                CBinaryExpr(
                                                                    CVar("cur"),
                                                                    "-",
                                                                    CLiteral(1),
                                                                ),
                                                            ),
                                                        )
                                                    )
                                                ),
                                        )
                                    ),
                            ),
                            CExprStatement(CCall("vsync")),
                        ),
                ),
            )
        val fadeGroup =
            CFunction(
                name = "fade_group",
                returnType = CVoid,
                params =
                    listOf(CParam("group", CU8), CParam("target_vol", CU8), CParam("frames", CU8)),
                body = fadeGroupBody,
            )

        // ---- audio_mixer_request_channel(UINT8 group, UINT8 priority) ---- (Gap 5)
        // Priority-based channel preemption. Returns 0xFF if all channels have >= priority.
        val requestChannelBody = mutableListOf<CStatement>()
        requestChannelBody += CVarDecl(name = "best_ch", type = CU8, initializer = CRawExpr("0xFF"))
        requestChannelBody +=
            CVarDecl(name = "best_pri", type = CU8, initializer = CVar("priority"))
        val requestCases = groups.mapIndexed { idx, group ->
            val channelCheckStmts = mutableListOf<CStatement>()
            for (ch in group.channels.sorted()) {
                val chIdx = ch - 1 // convert 1-based GB channel to 0-based array index
                channelCheckStmts +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CArrayAccess(CVar("_mixer_priority"), CLiteral(chIdx)),
                                "<",
                                CVar("best_pri"),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("best_pri"),
                                        "=",
                                        CArrayAccess(CVar("_mixer_priority"), CLiteral(chIdx)),
                                    )
                                ),
                                CExprStatement(CBinaryExpr(CVar("best_ch"), "=", CLiteral(chIdx))),
                            ),
                    )
            }
            channelCheckStmts += CBreak
            CSwitchCase(value = CLiteral(idx), body = channelCheckStmts)
        }
        requestChannelBody += CSwitch(expr = CVar("group"), cases = requestCases)
        requestChannelBody +=
            CIf(
                condition = CBinaryExpr(CVar("best_ch"), "!=", CRawExpr("0xFF")),
                thenBody =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("_mixer_priority"), CVar("best_ch")),
                                "=",
                                CVar("priority"),
                            )
                        ),
                        CReturn(CBinaryExpr(CVar("best_ch"), "+", CLiteral(1))),
                    ),
            )
        requestChannelBody += CReturn(CRawExpr("0xFF"))
        val requestChannel =
            CFunction(
                name = "audio_mixer_request_channel",
                returnType = CU8,
                params = listOf(CParam("group", CU8), CParam("priority", CU8)),
                body = requestChannelBody,
            )

        // ---- audio_mixer_duck() / audio_mixer_unduck() ---- (Gap 6)
        // Duck: save current music (group 0) volume, set to duck level.
        // Unduck: restore saved volume.
        val duckBody: List<CStatement> =
            if (autoDucking) {
                listOf(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_mixer_preduck_vol"),
                            "=",
                            CArrayAccess(CVar("_mixer_group_vol"), CLiteral(0)),
                        )
                    ),
                    CExprStatement(
                        CCall("set_group_volume", listOf(CLiteral(0), CLiteral(autoDuckLevel)))
                    ),
                )
            } else {
                listOf(CComment("auto_ducking disabled — no-op"))
            }
        val audioDuck = CFunction(name = "audio_mixer_duck", returnType = CVoid, body = duckBody)

        val unduckBody: List<CStatement> =
            listOf(
                CExprStatement(
                    CCall("set_group_volume", listOf(CLiteral(0), CVar("_mixer_preduck_vol")))
                )
            )
        val audioUnduck =
            CFunction(name = "audio_mixer_unduck", returnType = CVoid, body = unduckBody)

        // ---- audio_mixer_save_state(ptr) / audio_mixer_load_state(ptr) ---- (Gap 4)
        // Save: write mixer globals to buffer (master_vol, group vols, mute states).
        // Load: read buffer back into globals and apply to hardware via set_group_volume.
        val saveStateBody = mutableListOf<CStatement>()
        saveStateBody +=
            CExprStatement(
                CBinaryExpr(CArrayAccess(CVar("ptr"), CLiteral(0)), "=", CVar("_mixer_master_vol"))
            )
        groups.forEachIndexed { idx, _ ->
            saveStateBody +=
                CExprStatement(
                    CBinaryExpr(
                        CArrayAccess(CVar("ptr"), CLiteral(idx + 1)),
                        "=",
                        CArrayAccess(CVar("_mixer_group_vol"), CLiteral(idx)),
                    )
                )
        }
        groups.forEachIndexed { idx, _ ->
            saveStateBody +=
                CExprStatement(
                    CBinaryExpr(
                        CArrayAccess(CVar("ptr"), CLiteral(groups.size + 1 + idx)),
                        "=",
                        CArrayAccess(CVar("_mixer_group_muted"), CLiteral(idx)),
                    )
                )
        }
        val saveState =
            CFunction(
                name = "audio_mixer_save_state",
                returnType = CVoid,
                params = listOf(CParam("ptr", CU8)),
                body = saveStateBody,
            )

        val loadStateBody = mutableListOf<CStatement>()
        loadStateBody +=
            CExprStatement(
                CBinaryExpr(CVar("_mixer_master_vol"), "=", CArrayAccess(CVar("ptr"), CLiteral(0)))
            )
        groups.forEachIndexed { idx, _ ->
            loadStateBody +=
                CExprStatement(
                    CBinaryExpr(
                        CArrayAccess(CVar("_mixer_group_muted"), CLiteral(idx)),
                        "=",
                        CArrayAccess(CVar("ptr"), CLiteral(groups.size + 1 + idx)),
                    )
                )
        }
        groups.forEachIndexed { idx, _ ->
            loadStateBody +=
                CExprStatement(
                    CCall(
                        "set_group_volume",
                        listOf(CLiteral(idx), CArrayAccess(CVar("ptr"), CLiteral(idx + 1))),
                    )
                )
        }
        val loadState =
            CFunction(
                name = "audio_mixer_load_state",
                returnType = CVoid,
                params = listOf(CParam("ptr", CU8)),
                body = loadStateBody,
            )

        // ---- trigger_{id}() — no-op entry point for TriggerSystem ----
        val triggerFn =
            CFunction(
                name = "trigger_$sanitizedId",
                returnType = CVoid,
                body = listOf(CComment("audio_mixer trigger: no-op")),
            )

        return listOf(
            triggerFn,
            setGroupVolume,
            muteGroup,
            unmuteGroup,
            setMasterVolume,
            fadeGroup,
            requestChannel,
            audioDuck,
            audioUnduck,
            saveState,
            loadState,
        )
    }

    // -------------------------------------------------------------------------
    // PathfindingSystem: iterative A* with bit-packed closed set (WRAM-safe)
    // -------------------------------------------------------------------------

    /**
     * Generate complete iterative A* pathfinding infrastructure for [PathfindingSystem].
     *
     * WRAM budget (default 32x32 map, maxOpenNodes=32, maxPathLength=32):
     * - Open list: maxOpenNodes * 4 bytes = 128 bytes (x, y, g_cost, f_cost per node)
     * - Closed set: mapWidth * mapHeight / 8 + 1 = 129 bytes (1 bit per tile)
     * - Path arrays: maxPathLength * 2 bytes = 64 bytes
     * - Total: ~321 bytes — acceptable within Game Boy WRAM constraints
     *
     * Generated functions:
     * - `pf_is_closed(x, y)` — bit-test closed set
     * - `pf_set_closed(x, y)` — bit-set closed set
     * - `pf_find_path(startX, startY, endX, endY)` — iterative A* (NOT recursive — GB stack is ~128
     *   bytes)
     * - `pf_step_toward_{id}(actorIdx)` — read next step from path and move actor one tile
     *
     * Uses [CWhile] for the main A* loop (not [CFor] recursion — critical for Game Boy stack
     * safety). Bit-packed closed set minimizes WRAM: 1024 tiles in 128 bytes vs 1024 bytes naively.
     */
    @Suppress("LongMethod")
    override fun visitPathfindingSystem(system: PathfindingSystem): List<CFunction> {
        val closedSetSize = system.mapWidth * system.mapHeight / 8 + 1

        // ------------------------------------------------------------------
        // pf_is_closed(x, y) — bit-test the closed set
        // idx = y * mapWidth + x; return closed[idx >> 3] & (1 << (idx & 7))
        // ------------------------------------------------------------------
        val pfIsClosed =
            CFunction(
                name = "pf_is_closed",
                returnType = CU8,
                params = listOf(CParam("x", CU8), CParam("y", CU8)),
                body =
                    buildList {
                        add(
                            CVarDecl(
                                "idx",
                                CU8,
                                CBinaryExpr(
                                    CBinaryExpr(CVar("y"), "*", CLiteral(system.mapWidth)),
                                    "+",
                                    CVar("x"),
                                ),
                            )
                        )
                        add(
                            CReturn(
                                CBinaryExpr(
                                    CArrayAccess(
                                        CVar("_pf_closed"),
                                        CBinaryExpr(CVar("idx"), ">>", CLiteral(3)),
                                    ),
                                    "&",
                                    CRawExpr("(1 << (idx & 7))"),
                                )
                            )
                        )
                    },
                sectionComment = "A* pathfinding: bit-packed closed set operations",
            )

        // ------------------------------------------------------------------
        // pf_set_closed(x, y) — bit-set in the closed set
        // ------------------------------------------------------------------
        val pfSetClosed =
            CFunction(
                name = "pf_set_closed",
                returnType = CVoid,
                params = listOf(CParam("x", CU8), CParam("y", CU8)),
                body =
                    buildList {
                        add(
                            CVarDecl(
                                "idx",
                                CU8,
                                CBinaryExpr(
                                    CBinaryExpr(CVar("y"), "*", CLiteral(system.mapWidth)),
                                    "+",
                                    CVar("x"),
                                ),
                            )
                        )
                        // _pf_closed[idx >> 3] |= (1 << (idx & 7));  (bit-set closed set)
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_pf_closed"), CRawExpr("idx >> 3")),
                                    "|=",
                                    CRawExpr("(1 << (idx & 7))"),
                                )
                            )
                        )
                    },
            )

        // ------------------------------------------------------------------
        // pf_find_path(startX, startY, endX, endY) — iterative A*
        //
        // Open list layout: _pf_open[i*4+0]=x, [i*4+1]=y, [i*4+2]=g_cost, [i*4+3]=f_cost
        // Neighbor expansion: 4-directional (UP, DOWN, LEFT, RIGHT)
        // Heuristic: Manhattan distance (abs(nx-endX) + abs(ny-endY))
        // Walkability: calls _map_collision(nx, ny) — switch-based dispatch to per-scene collision
        // ------------------------------------------------------------------
        val pfFindPath =
            CFunction(
                name = "pf_find_path",
                returnType = CVoid,
                params =
                    listOf(
                        CParam("startX", CU8),
                        CParam("startY", CU8),
                        CParam("endX", CU8),
                        CParam("endY", CU8),
                    ),
                body =
                    buildList {
                        add(CComment("Clear closed set and path"))
                        add(
                            CFor(
                                init = CVarDecl("ci", CU8, CLiteral(0)),
                                condition = CBinaryExpr(CVar("ci"), "<", CLiteral(closedSetSize)),
                                increment = CUnaryExpr("++", CVar("ci")),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_pf_closed"), CVar("ci")),
                                                "=",
                                                CLiteral(0),
                                            )
                                        )
                                    ),
                            )
                        )
                        add(CExprStatement(CBinaryExpr(CVar("_pf_path_length"), "=", CLiteral(0))))
                        add(CExprStatement(CBinaryExpr(CVar("_pf_open_count"), "=", CLiteral(0))))

                        add(CComment("Add start node: open[0] = {startX, startY, 0, h(start)}"))
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_pf_open"), CLiteral(0)),
                                    "=",
                                    CVar("startX"),
                                )
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_pf_open"), CLiteral(1)),
                                    "=",
                                    CVar("startY"),
                                )
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_pf_open"), CLiteral(2)),
                                    "=",
                                    CLiteral(0), // g_cost
                                )
                            )
                        )
                        // f_cost = h = abs(startX-endX) + abs(startY-endY) — ternary-based abs
                        // (C89, no stdlib)
                        val hStart =
                            CBinaryExpr(
                                CTernary(
                                    CBinaryExpr(CVar("startX"), ">", CVar("endX")),
                                    CBinaryExpr(CVar("startX"), "-", CVar("endX")),
                                    CBinaryExpr(CVar("endX"), "-", CVar("startX")),
                                ),
                                "+",
                                CTernary(
                                    CBinaryExpr(CVar("startY"), ">", CVar("endY")),
                                    CBinaryExpr(CVar("startY"), "-", CVar("endY")),
                                    CBinaryExpr(CVar("endY"), "-", CVar("startY")),
                                ),
                            )
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_pf_open"), CLiteral(3)),
                                    "=",
                                    hStart,
                                )
                            )
                        )
                        add(CExprStatement(CBinaryExpr(CVar("_pf_open_count"), "=", CLiteral(1))))

                        add(
                            CComment(
                                "Main A* loop — iterative (NOT recursive: GB stack is ~128 bytes)"
                            )
                        )
                        add(CVarDecl("best", CU8, CLiteral(0)))
                        add(CVarDecl("bestF", CU8, CLiteral(255)))
                        add(CVarDecl("cx", CU8, CLiteral(0)))
                        add(CVarDecl("cy", CU8, CLiteral(0)))
                        add(CVarDecl("cg", CU8, CLiteral(0)))
                        add(CVarDecl("nx", CU8, CLiteral(0)))
                        add(CVarDecl("ny", CU8, CLiteral(0)))
                        add(CVarDecl("ng", CU8, CLiteral(0)))
                        add(CVarDecl("nf", CU8, CLiteral(0)))
                        add(CVarDecl("d", CU8, CLiteral(0))) // direction index 0-3

                        add(
                            CWhile(
                                condition = CBinaryExpr(CVar("_pf_open_count"), ">", CLiteral(0)),
                                body =
                                    buildList {
                                        add(
                                            CComment(
                                                "Find node with lowest f_cost (linear scan — OK for small lists)"
                                            )
                                        )
                                        add(
                                            CExprStatement(
                                                CBinaryExpr(CVar("best"), "=", CLiteral(0))
                                            )
                                        )
                                        add(
                                            CExprStatement(
                                                CBinaryExpr(CVar("bestF"), "=", CLiteral(255))
                                            )
                                        )
                                        add(
                                            CFor(
                                                init = CVarDecl("j", CU8, CLiteral(0)),
                                                condition =
                                                    CBinaryExpr(
                                                        CVar("j"),
                                                        "<",
                                                        CVar("_pf_open_count"),
                                                    ),
                                                increment = CUnaryExpr("++", CVar("j")),
                                                body =
                                                    listOf(
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_pf_open"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar("j"),
                                                                                "*",
                                                                                CLiteral(4),
                                                                            ),
                                                                            "+",
                                                                            CLiteral(3),
                                                                        ),
                                                                    ),
                                                                    "<",
                                                                    CVar("bestF"),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("best"),
                                                                            "=",
                                                                            CVar("j"),
                                                                        )
                                                                    ),
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("bestF"),
                                                                            "=",
                                                                            CArrayAccess(
                                                                                CVar("_pf_open"),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CVar("j"),
                                                                                        "*",
                                                                                        CLiteral(4),
                                                                                    ),
                                                                                    "+",
                                                                                    CLiteral(3),
                                                                                ),
                                                                            ),
                                                                        )
                                                                    ),
                                                                ),
                                                        )
                                                    ),
                                            )
                                        )

                                        add(CComment("Extract best node"))
                                        add(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("cx"),
                                                    "=",
                                                    CArrayAccess(
                                                        CVar("_pf_open"),
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("best"),
                                                                "*",
                                                                CLiteral(4),
                                                            ),
                                                            "+",
                                                            CLiteral(0),
                                                        ),
                                                    ),
                                                )
                                            )
                                        )
                                        add(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("cy"),
                                                    "=",
                                                    CArrayAccess(
                                                        CVar("_pf_open"),
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("best"),
                                                                "*",
                                                                CLiteral(4),
                                                            ),
                                                            "+",
                                                            CLiteral(1),
                                                        ),
                                                    ),
                                                )
                                            )
                                        )
                                        add(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("cg"),
                                                    "=",
                                                    CArrayAccess(
                                                        CVar("_pf_open"),
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("best"),
                                                                "*",
                                                                CLiteral(4),
                                                            ),
                                                            "+",
                                                            CLiteral(2),
                                                        ),
                                                    ),
                                                )
                                            )
                                        )

                                        add(
                                            CComment(
                                                "Remove best from open list by swapping with last"
                                            )
                                        )
                                        add(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_pf_open_count"),
                                                    "-=",
                                                    CLiteral(1),
                                                )
                                            )
                                        )
                                        add(
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CVar("best"),
                                                        "!=",
                                                        CVar("_pf_open_count"),
                                                    ),
                                                thenBody =
                                                    buildList {
                                                        add(
                                                            CFor(
                                                                init =
                                                                    CVarDecl("k", CU8, CLiteral(0)),
                                                                condition =
                                                                    CBinaryExpr(
                                                                        CVar("k"),
                                                                        "<",
                                                                        CLiteral(4),
                                                                    ),
                                                                increment =
                                                                    CUnaryExpr("++", CVar("k")),
                                                                body =
                                                                    listOf(
                                                                        CExprStatement(
                                                                            CBinaryExpr(
                                                                                CArrayAccess(
                                                                                    CVar(
                                                                                        "_pf_open"
                                                                                    ),
                                                                                    CBinaryExpr(
                                                                                        CBinaryExpr(
                                                                                            CVar(
                                                                                                "best"
                                                                                            ),
                                                                                            "*",
                                                                                            CLiteral(
                                                                                                4
                                                                                            ),
                                                                                        ),
                                                                                        "+",
                                                                                        CVar("k"),
                                                                                    ),
                                                                                ),
                                                                                "=",
                                                                                CArrayAccess(
                                                                                    CVar(
                                                                                        "_pf_open"
                                                                                    ),
                                                                                    CBinaryExpr(
                                                                                        CBinaryExpr(
                                                                                            CVar(
                                                                                                "_pf_open_count"
                                                                                            ),
                                                                                            "*",
                                                                                            CLiteral(
                                                                                                4
                                                                                            ),
                                                                                        ),
                                                                                        "+",
                                                                                        CVar("k"),
                                                                                    ),
                                                                                ),
                                                                            )
                                                                        )
                                                                    ),
                                                            )
                                                        )
                                                    },
                                            )
                                        )

                                        add(CComment("Check if at target"))
                                        add(
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CBinaryExpr(CVar("cx"), "==", CVar("endX")),
                                                        "&&",
                                                        CBinaryExpr(CVar("cy"), "==", CVar("endY")),
                                                    ),
                                                thenBody =
                                                    buildList {
                                                        add(
                                                            CComment(
                                                                "Path found — record single step toward target"
                                                            )
                                                        )
                                                        add(
                                                            CIf(
                                                                condition =
                                                                    CBinaryExpr(
                                                                        CVar("_pf_path_length"),
                                                                        "<",
                                                                        CLiteral(1),
                                                                    ),
                                                                thenBody =
                                                                    buildList {
                                                                        add(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_pf_path_x"
                                                                                        ),
                                                                                        CLiteral(0),
                                                                                    ),
                                                                                    "=",
                                                                                    CVar("cx"),
                                                                                )
                                                                            )
                                                                        )
                                                                        add(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_pf_path_y"
                                                                                        ),
                                                                                        CLiteral(0),
                                                                                    ),
                                                                                    "=",
                                                                                    CVar("cy"),
                                                                                )
                                                                            )
                                                                        )
                                                                        add(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CVar(
                                                                                        "_pf_path_length"
                                                                                    ),
                                                                                    "=",
                                                                                    CLiteral(1),
                                                                                )
                                                                            )
                                                                        )
                                                                    },
                                                            )
                                                        )
                                                        add(CBreak)
                                                    },
                                            )
                                        )

                                        add(CComment("Mark current node closed"))
                                        add(
                                            CExprStatement(
                                                CCall(
                                                    "pf_set_closed",
                                                    listOf(CVar("cx"), CVar("cy")),
                                                )
                                            )
                                        )

                                        add(
                                            CComment(
                                                "Expand 4 neighbors: UP=0, DOWN=1, LEFT=2, RIGHT=3"
                                            )
                                        )
                                        add(
                                            CFor(
                                                init = CVarDecl("di", CU8, CLiteral(0)),
                                                condition =
                                                    CBinaryExpr(CVar("di"), "<", CLiteral(4)),
                                                increment = CUnaryExpr("++", CVar("di")),
                                                body =
                                                    buildList {
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("nx"),
                                                                    "=",
                                                                    CVar("cx"),
                                                                )
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("ny"),
                                                                    "=",
                                                                    CVar("cy"),
                                                                )
                                                            )
                                                        )
                                                        // Direction expansion: UP=0, DOWN=1,
                                                        // LEFT=2, RIGHT=3
                                                        add(
                                                            CIf(
                                                                condition =
                                                                    CBinaryExpr(
                                                                        CBinaryExpr(
                                                                            CVar("di"),
                                                                            "==",
                                                                            CLiteral(0),
                                                                        ),
                                                                        "&&",
                                                                        CBinaryExpr(
                                                                            CVar("ny"),
                                                                            ">",
                                                                            CLiteral(0),
                                                                        ),
                                                                    ),
                                                                thenBody =
                                                                    listOf(
                                                                        CExprStatement(
                                                                            CUnaryExpr(
                                                                                "--",
                                                                                CVar("ny"),
                                                                            )
                                                                        )
                                                                    ),
                                                                elseBody =
                                                                    listOf(
                                                                        CIf(
                                                                            condition =
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CVar("di"),
                                                                                        "==",
                                                                                        CLiteral(1),
                                                                                    ),
                                                                                    "&&",
                                                                                    CBinaryExpr(
                                                                                        CVar("ny"),
                                                                                        "<",
                                                                                        CLiteral(
                                                                                            system
                                                                                                .mapHeight -
                                                                                                1
                                                                                        ),
                                                                                    ),
                                                                                ),
                                                                            thenBody =
                                                                                listOf(
                                                                                    CExprStatement(
                                                                                        CUnaryExpr(
                                                                                            "++",
                                                                                            CVar(
                                                                                                "ny"
                                                                                            ),
                                                                                        )
                                                                                    )
                                                                                ),
                                                                            elseBody =
                                                                                listOf(
                                                                                    CIf(
                                                                                        condition =
                                                                                            CBinaryExpr(
                                                                                                CBinaryExpr(
                                                                                                    CVar(
                                                                                                        "di"
                                                                                                    ),
                                                                                                    "==",
                                                                                                    CLiteral(
                                                                                                        2
                                                                                                    ),
                                                                                                ),
                                                                                                "&&",
                                                                                                CBinaryExpr(
                                                                                                    CVar(
                                                                                                        "nx"
                                                                                                    ),
                                                                                                    ">",
                                                                                                    CLiteral(
                                                                                                        0
                                                                                                    ),
                                                                                                ),
                                                                                            ),
                                                                                        thenBody =
                                                                                            listOf(
                                                                                                CExprStatement(
                                                                                                    CUnaryExpr(
                                                                                                        "--",
                                                                                                        CVar(
                                                                                                            "nx"
                                                                                                        ),
                                                                                                    )
                                                                                                )
                                                                                            ),
                                                                                        elseBody =
                                                                                            listOf(
                                                                                                CIf(
                                                                                                    condition =
                                                                                                        CBinaryExpr(
                                                                                                            CBinaryExpr(
                                                                                                                CVar(
                                                                                                                    "di"
                                                                                                                ),
                                                                                                                "==",
                                                                                                                CLiteral(
                                                                                                                    3
                                                                                                                ),
                                                                                                            ),
                                                                                                            "&&",
                                                                                                            CBinaryExpr(
                                                                                                                CVar(
                                                                                                                    "nx"
                                                                                                                ),
                                                                                                                "<",
                                                                                                                CLiteral(
                                                                                                                    system
                                                                                                                        .mapWidth -
                                                                                                                        1
                                                                                                                ),
                                                                                                            ),
                                                                                                        ),
                                                                                                    thenBody =
                                                                                                        listOf(
                                                                                                            CExprStatement(
                                                                                                                CUnaryExpr(
                                                                                                                    "++",
                                                                                                                    CVar(
                                                                                                                        "nx"
                                                                                                                    ),
                                                                                                                )
                                                                                                            )
                                                                                                        ),
                                                                                                    elseBody =
                                                                                                        listOf(
                                                                                                            CContinue
                                                                                                        ),
                                                                                                )
                                                                                            ),
                                                                                    )
                                                                                ),
                                                                        )
                                                                    ),
                                                            )
                                                        )

                                                        add(CComment("Skip if closed"))
                                                        add(
                                                            CIf(
                                                                condition =
                                                                    CCall(
                                                                        "pf_is_closed",
                                                                        listOf(
                                                                            CVar("nx"),
                                                                            CVar("ny"),
                                                                        ),
                                                                    ),
                                                                thenBody = listOf(CContinue),
                                                            )
                                                        )

                                                        add(
                                                            CComment(
                                                                "Skip if impassable (via scene collision dispatch)"
                                                            )
                                                        )
                                                        add(
                                                            CIf(
                                                                condition =
                                                                    CCall(
                                                                        "_map_collision",
                                                                        listOf(
                                                                            CVar("nx"),
                                                                            CVar("ny"),
                                                                        ),
                                                                    ),
                                                                thenBody = listOf(CContinue),
                                                            )
                                                        )

                                                        add(CComment("Skip if open list full"))
                                                        add(
                                                            CIf(
                                                                condition =
                                                                    CBinaryExpr(
                                                                        CVar("_pf_open_count"),
                                                                        ">=",
                                                                        CLiteral(
                                                                            system.maxOpenNodes
                                                                        ),
                                                                    ),
                                                                thenBody = listOf(CContinue),
                                                            )
                                                        )

                                                        add(
                                                            CComment(
                                                                "Compute costs and add to open list"
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("ng"),
                                                                    "=",
                                                                    CBinaryExpr(
                                                                        CVar("cg"),
                                                                        "+",
                                                                        CLiteral(1),
                                                                    ),
                                                                )
                                                            )
                                                        )
                                                        // nf = ng + Manhattan(nx,endX) +
                                                        // Manhattan(ny,endY) — ternary-based abs
                                                        // (C89)
                                                        val hNeighbor =
                                                            CBinaryExpr(
                                                                CBinaryExpr(
                                                                    CVar("ng"),
                                                                    "+",
                                                                    CTernary(
                                                                        CBinaryExpr(
                                                                            CVar("nx"),
                                                                            ">",
                                                                            CVar("endX"),
                                                                        ),
                                                                        CBinaryExpr(
                                                                            CVar("nx"),
                                                                            "-",
                                                                            CVar("endX"),
                                                                        ),
                                                                        CBinaryExpr(
                                                                            CVar("endX"),
                                                                            "-",
                                                                            CVar("nx"),
                                                                        ),
                                                                    ),
                                                                ),
                                                                "+",
                                                                CTernary(
                                                                    CBinaryExpr(
                                                                        CVar("ny"),
                                                                        ">",
                                                                        CVar("endY"),
                                                                    ),
                                                                    CBinaryExpr(
                                                                        CVar("ny"),
                                                                        "-",
                                                                        CVar("endY"),
                                                                    ),
                                                                    CBinaryExpr(
                                                                        CVar("endY"),
                                                                        "-",
                                                                        CVar("ny"),
                                                                    ),
                                                                ),
                                                            )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("nf"),
                                                                    "=",
                                                                    hNeighbor,
                                                                )
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_pf_open"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar(
                                                                                    "_pf_open_count"
                                                                                ),
                                                                                "*",
                                                                                CLiteral(4),
                                                                            ),
                                                                            "+",
                                                                            CLiteral(0),
                                                                        ),
                                                                    ),
                                                                    "=",
                                                                    CVar("nx"),
                                                                )
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_pf_open"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar(
                                                                                    "_pf_open_count"
                                                                                ),
                                                                                "*",
                                                                                CLiteral(4),
                                                                            ),
                                                                            "+",
                                                                            CLiteral(1),
                                                                        ),
                                                                    ),
                                                                    "=",
                                                                    CVar("ny"),
                                                                )
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_pf_open"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar(
                                                                                    "_pf_open_count"
                                                                                ),
                                                                                "*",
                                                                                CLiteral(4),
                                                                            ),
                                                                            "+",
                                                                            CLiteral(2),
                                                                        ),
                                                                    ),
                                                                    "=",
                                                                    CVar("ng"),
                                                                )
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_pf_open"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar(
                                                                                    "_pf_open_count"
                                                                                ),
                                                                                "*",
                                                                                CLiteral(4),
                                                                            ),
                                                                            "+",
                                                                            CLiteral(3),
                                                                        ),
                                                                    ),
                                                                    "=",
                                                                    CVar("nf"),
                                                                )
                                                            )
                                                        )
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("_pf_open_count"),
                                                                    "+=",
                                                                    CLiteral(1),
                                                                )
                                                            )
                                                        )

                                                        add(
                                                            CComment(
                                                                "Record first step toward target"
                                                            )
                                                        )
                                                        add(
                                                            CIf(
                                                                condition =
                                                                    CBinaryExpr(
                                                                        CBinaryExpr(
                                                                            CVar("cg"),
                                                                            "==",
                                                                            CLiteral(0),
                                                                        ),
                                                                        "&&",
                                                                        CBinaryExpr(
                                                                            CVar("_pf_path_length"),
                                                                            "==",
                                                                            CLiteral(0),
                                                                        ),
                                                                    ),
                                                                thenBody =
                                                                    buildList {
                                                                        add(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_pf_path_x"
                                                                                        ),
                                                                                        CLiteral(0),
                                                                                    ),
                                                                                    "=",
                                                                                    CVar("nx"),
                                                                                )
                                                                            )
                                                                        )
                                                                        add(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_pf_path_y"
                                                                                        ),
                                                                                        CLiteral(0),
                                                                                    ),
                                                                                    "=",
                                                                                    CVar("ny"),
                                                                                )
                                                                            )
                                                                        )
                                                                        add(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CVar(
                                                                                        "_pf_path_length"
                                                                                    ),
                                                                                    "=",
                                                                                    CLiteral(1),
                                                                                )
                                                                            )
                                                                        )
                                                                    },
                                                            )
                                                        )
                                                    },
                                            )
                                        )
                                    },
                            )
                        )
                    },
                sectionComment =
                    "A* pathfinding: iterative find_path (NOT recursive — GB stack is ~128 bytes)",
            )

        // ------------------------------------------------------------------
        // pf_step_toward_{actorId}() — read next step from path, move actor one tile
        // NPC moves toward _pf_path_x[0], _pf_path_y[0] by one gridSize step.
        // ------------------------------------------------------------------
        val pfStepToward =
            CFunction(
                name = "pf_step_toward",
                returnType = CVoid,
                params = listOf(CParam("npc_x", CU8), CParam("npc_y", CU8)),
                body =
                    buildList {
                        add(CComment("Move NPC one step toward first path node"))
                        add(
                            CIf(
                                condition = CBinaryExpr(CVar("_pf_path_length"), "==", CLiteral(0)),
                                thenBody = listOf(CReturn(null)),
                            )
                        )
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("npc_x"),
                                        "<",
                                        CArrayAccess(CVar("_pf_path_x"), CLiteral(0)),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(CVar("npc_x"), "+=", CLiteral(1))
                                        )
                                    ),
                                elseBody =
                                    listOf(
                                        CIf(
                                            condition =
                                                CBinaryExpr(
                                                    CVar("npc_x"),
                                                    ">",
                                                    CArrayAccess(CVar("_pf_path_x"), CLiteral(0)),
                                                ),
                                            thenBody =
                                                listOf(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("npc_x"),
                                                            "-=",
                                                            CLiteral(1),
                                                        )
                                                    )
                                                ),
                                            elseBody =
                                                listOf(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CVar("npc_y"),
                                                                "<",
                                                                CArrayAccess(
                                                                    CVar("_pf_path_y"),
                                                                    CLiteral(0),
                                                                ),
                                                            ),
                                                        thenBody =
                                                            listOf(
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CVar("npc_y"),
                                                                        "+=",
                                                                        CLiteral(1),
                                                                    )
                                                                )
                                                            ),
                                                        elseBody =
                                                            listOf(
                                                                CIf(
                                                                    condition =
                                                                        CBinaryExpr(
                                                                            CVar("npc_y"),
                                                                            ">",
                                                                            CArrayAccess(
                                                                                CVar("_pf_path_y"),
                                                                                CLiteral(0),
                                                                            ),
                                                                        ),
                                                                    thenBody =
                                                                        listOf(
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CVar("npc_y"),
                                                                                    "-=",
                                                                                    CLiteral(1),
                                                                                )
                                                                            )
                                                                        ),
                                                                )
                                                            ),
                                                    )
                                                ),
                                        )
                                    ),
                            )
                        )
                    },
            )

        return listOf(pfIsClosed, pfSetClosed, pfFindPath, pfStepToward)
    }

    // =========================================================================
    // Companion object — static builder helpers
    // OAM free list, spawn/destroy actor, pathfinding globals
    // =========================================================================

    companion object {

        /**
         * Build global variable declarations for the pathfinding data structures.
         *
         * Called by [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline] to add pathfinding
         * globals to the main.c variable declarations section.
         *
         * Generated globals:
         * - `_pf_open[maxOpenNodes*4]` — UINT8 open list (x, y, g_cost, f_cost per node)
         * - `_pf_open_count` — current open list size
         * - `_pf_closed[closedSetSize]` — bit-packed closed set (1 bit per tile)
         * - `_pf_path_x[maxPathLength]`, `_pf_path_y[maxPathLength]` — result path
         * - `_pf_path_length` — result path length
         *
         * Walkability is checked via `_map_collision(nx, ny)` — the dispatch function generated by
         * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline.buildCollisionDispatchFunction]
         * that routes to per-scene `_map_collision_{sceneId}()` functions based on `current_scene`.
         * No function pointer global is needed.
         */
        fun buildPathfindingGlobals(system: PathfindingSystem): List<CVarDecl> {
            val closedSetSize = system.mapWidth * system.mapHeight / 8 + 1
            val openListSize = system.maxOpenNodes * 4
            return listOf(
                CVarDecl("_pf_open", CArray(CU8, openListSize)),
                CVarDecl("_pf_open_count", CU8, CLiteral(0)),
                CVarDecl("_pf_closed", CArray(CU8, closedSetSize)),
                CVarDecl("_pf_path_x", CArray(CU8, system.maxPathLength)),
                CVarDecl("_pf_path_y", CArray(CU8, system.maxPathLength)),
                CVarDecl("_pf_path_length", CU8, CLiteral(0)),
            )
        }

        /**
         * Generate the OAM free list global variables.
         *
         * A simple stack-based free list for dynamic OAM slot management:
         * - `_oam_free_list[MAX_SPRITES]`: stack of free OAM slots
         * - `_oam_free_top`: index of the top of the free stack (0 = all taken)
         *
         * Returns [CVarDecl] instances to be included in main.c variable declarations.
         */
        fun buildOAMFreeListGlobals(maxSprites: Int = 40): List<CVarDecl> {
            return listOf(
                CVarDecl(
                    name = "_oam_free_list",
                    type = CArray(CU8, maxSprites),
                    initializer = null,
                ),
                CVarDecl(
                    name = "_oam_free_top",
                    type = CU8,
                    initializer = io.github.gbkt.backend.gbdk.codegen.ast.CLiteral(0),
                ),
            )
        }

        /**
         * Generate `init_oam_free_list()` function that initializes the free stack with all OAM
         * slots in order (0..maxSprites-1).
         *
         * Called once from main() before the game loop.
         */
        fun buildOAMFreeListInit(maxSprites: Int = 40): CFunction {
            val iVar = CVar("i")
            return CFunction(
                name = "init_oam_free_list",
                returnType = CVoid,
                body =
                    buildList {
                        // C89: declare loop variable before for loop
                        add(CVarDecl("i", CU8, initializer = null))
                        add(
                            CFor(
                                init = CExprStatement(CBinaryExpr(iVar, "=", CLiteral(0))),
                                condition = CBinaryExpr(iVar, "<", CLiteral(maxSprites)),
                                increment = CUnaryExpr("++", iVar),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_oam_free_list"), iVar),
                                                "=",
                                                iVar,
                                            )
                                        )
                                    ),
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(CVar("_oam_free_top"), "=", CLiteral(maxSprites))
                            )
                        )
                    },
                sectionComment = "OAM free list initialization",
            )
        }

        /**
         * Generate `spawn_actor(actorId)` function.
         *
         * Claims the next free OAM slot from the stack. Stores the assigned slot in the actor's
         * `_<actorId>_oam_slot` global. Loads sprite tile data and positions the sprite.
         */
        fun buildSpawnActorFunction(): CFunction {
            val freeTopVar = CVar("_oam_free_top")
            val freeListVar = CVar("_oam_free_list")
            val slotVar = CVar("slot")
            return CFunction(
                name = "spawn_actor",
                returnType = CU8,
                params = listOf(CParam("actor_id", CU8)),
                body =
                    buildList {
                        // C89: declare slot before use
                        add(CVarDecl("slot", CU8, initializer = null))
                        // Guard: when the free list is empty, bail out with the 0xFF sentinel.
                        add(
                            CIf(
                                condition = CBinaryExpr(freeTopVar, "==", CLiteral(0)),
                                thenBody = listOf(CReturn(CLiteral(0xFF))),
                            )
                        )
                        // Pop the next free slot: decrement the stack top, then read the entry.
                        add(CExprStatement(CUnaryExpr("--", freeTopVar)))
                        add(
                            CExprStatement(
                                CBinaryExpr(slotVar, "=", CArrayAccess(freeListVar, freeTopVar))
                            )
                        )
                        add(CComment("caller is responsible for set_sprite_tile and move_sprite"))
                        add(CReturn(slotVar))
                    },
                sectionComment = "OAM slot management (spawn/destroy)",
            )
        }

        /**
         * Generate `destroy_actor(slot)` function.
         *
         * Returns an OAM slot to the free list and hides the sprite by moving it off-screen.
         */
        fun buildDestroyActorFunction(): CFunction {
            val slotVar = CVar("slot")
            val freeTopVar = CVar("_oam_free_top")
            val freeListVar = CVar("_oam_free_list")
            return CFunction(
                name = "destroy_actor",
                returnType = CVoid,
                params = listOf(CParam("slot", CU8)),
                body =
                    buildList {
                        // Guard: ignore the 0xFF invalid-slot sentinel.
                        add(
                            CIf(
                                condition = CBinaryExpr(slotVar, "==", CLiteral(0xFF)),
                                thenBody = listOf(CReturn(null)),
                            )
                        )
                        // Hide the sprite by moving it off-screen to position (0, 0).
                        add(
                            CExprStatement(
                                CCall("move_sprite", listOf(slotVar, CLiteral(0), CLiteral(0)))
                            )
                        )
                        // Push the slot back onto the free list, bounded by the 40-entry OAM
                        // capacity.
                        add(
                            CIf(
                                condition = CBinaryExpr(freeTopVar, "<", CLiteral(40)),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(freeListVar, freeTopVar),
                                                "=",
                                                slotVar,
                                            )
                                        ),
                                        CExprStatement(CUnaryExpr("++", freeTopVar)),
                                    ),
                            )
                        )
                    },
            )
        }

        // -------------------------------------------------------------------------
        // Actor pool codegen: per-pool init/spawn/destroy functions + state variables
        // -------------------------------------------------------------------------

        /**
         * Generate global variable declarations for all actor pools in a [GameIR].
         *
         * For each pool:
         * - `UINT8 _pool_<id>_active[max]` — per-slot active bitmap (1=active, 0=free)
         * - `UINT8 _pool_<id>_x[max]` — per-instance x positions
         * - `UINT8 _pool_<id>_y[max]` — per-instance y positions
         * - `UINT8 _pool_<id>_oam[max]` — per-instance OAM slot mapping (initialized to 0xFF)
         */
        fun buildActorPoolStateVars(gameIR: GameIR): List<CVarDecl> =
            gameIR.actorPools.flatMap { pool ->
                val id = pool.id.replace('-', '_').replace(' ', '_')
                val maxSize = pool.config.maxSize
                buildPoolCoreStateVars(id, maxSize) +
                    buildPoolInstancePropertyVars(id, maxSize, pool)
            }

        private fun buildPoolCoreStateVars(id: String, maxSize: Int): List<CVarDecl> =
            listOf(
                CVarDecl(
                    name = "_pool_${id}_active",
                    type = CArray(CU8, maxSize),
                    initializer = null,
                ),
                CVarDecl(name = "_pool_${id}_x", type = CArray(CU8, maxSize), initializer = null),
                CVarDecl(name = "_pool_${id}_y", type = CArray(CU8, maxSize), initializer = null),
                CVarDecl(name = "_pool_${id}_oam", type = CArray(CU8, maxSize), initializer = null),
            )

        // Per-instance property parallel arrays — one array per property per pool slot
        private fun buildPoolInstancePropertyVars(
            id: String,
            maxSize: Int,
            pool: ActorPoolIR,
        ): List<CVarDecl> =
            pool.instanceProperties.map { prop ->
                val elemType =
                    when (prop.type) {
                        VarType.U8,
                        VarType.U16 -> CU8
                        VarType.I8,
                        VarType.I16 -> CI8
                    }
                CVarDecl(
                    name = "_pool_${id}_${prop.name}",
                    type = CArray(elemType, maxSize),
                    initializer = null,
                )
            }

        /**
         * Generate all lifecycle functions for every actor pool in a [GameIR].
         *
         * Uses **static OAM assignment**: each pool entity `i` gets a fixed OAM base slot computed
         * at codegen time. No dynamic free list is used for pool entities — this eliminates the OAM
         * out-of-bounds write bug where a multi-tile pool sprite (e.g. 16x16 = 4 OAM entries) would
         * use OAM slots beyond index 39, corrupting GBDK internal variables at 0xC0A0+ (shadow_OAM
         * overflows into __cpu, __is_GBA, etc.).
         *
         * OAM base for the first pool = sum of all actor tile counts (pool templates reserve OAM
         * space in the static actor layout even though they don't get static move_sprite calls).
         * Subsequent pools follow immediately after the previous pool's OAM range.
         *
         * For each [ActorPoolIR] produces:
         * - `pool_<id>_init()` — zeros the active bitmap and pre-initializes oam[i] = oamBase + i *
         *   tilesPerEntity (permanent static assignment, not 0xFF sentinel).
         * - `pool_<id>_spawn(UINT8 x, UINT8 y) : UINT8` — finds a free slot, stores position, marks
         *   active, calls move_sprite for ALL tiles in the metasprite grid. Returns pool index.
         *   When full: [PoolOverflowStrategy.SILENT_NOOP] returns 0xFF;
         *   [PoolOverflowStrategy.RECYCLE_OLDEST] reuses oldest slot round-robin.
         * - `pool_<id>_destroy(UINT8 i)` — calls move_sprite(oam[i]+t, 0, 0) for each tile to hide
         *   all OAM entries, then marks slot inactive. Does NOT reset oam[i] (static assignment is
         *   permanent).
         */
        fun buildActorPoolFunctions(gameIR: GameIR): List<CFunction> {
            val functions = mutableListOf<CFunction>()

            // Compute the starting OAM slot for pools.
            // All actors (including pool templates) consume OAM slots in the static actor layout:
            // pool templates reserve their slot range even though they don't get static move_sprite
            // calls.
            // This mirrors ActorVisitor.generateUpdateSprites behavior which advances nextSlot for
            // all actors (including excluded pool templates).
            var poolOamBase = 0
            for (actor in gameIR.actors) {
                val sprite = actor.sprite ?: continue
                val tw = (sprite.size.width + 7) / 8
                val th = (sprite.size.height + 7) / 8
                poolOamBase += tw * th
            }
            // poolOamBase now holds the first OAM slot available for pools (after all static
            // actors)

            for (pool in gameIR.actorPools) {
                val id = pool.id.replace('-', '_').replace(' ', '_')
                val maxSize = pool.config.maxSize
                val activeArr = CVar("_pool_${id}_active")
                val oamArr = CVar("_pool_${id}_oam")
                val xArr = CVar("_pool_${id}_x")
                val yArr = CVar("_pool_${id}_y")

                // Resolve template actor sprite dimensions for multi-tile OAM management
                val templateActor = gameIR.actors.find { it.id == pool.actorTemplateId }
                val tilesWide = templateActor?.sprite?.size?.let { (it.width + 7) / 8 } ?: 1
                val tilesHigh = templateActor?.sprite?.size?.let { (it.height + 7) / 8 } ?: 1
                val tilesPerEntity = tilesWide * tilesHigh

                // This pool's OAM base (first entity gets slot oamBase, entity i gets oamBase +
                // i*tilesPerEntity)
                val thisPoolOamBase = poolOamBase

                // Advance base for next pool
                poolOamBase += maxSize * tilesPerEntity

                // --- pool_<id>_init() ---
                // Zeros active bitmap and pre-initializes oam[i] = oamBase + i * tilesPerEntity.
                // Static OAM assignment: oam[i] is permanent, not a 0xFF sentinel. This means
                // destroy does NOT need to reset oam[i], and there is no risk of move_sprite(0xFF).
                functions +=
                    CFunction(
                        name = "pool_${id}_init",
                        returnType = CVoid,
                        body =
                            buildList {
                                add(CVarDecl("i", CU8, initializer = null))
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("i"), "=", CLiteral(0))
                                            ),
                                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxSize)),
                                        increment = CUnaryExpr("++", CVar("i")),
                                        body =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(activeArr, CVar("i")),
                                                        "=",
                                                        CLiteral(0),
                                                    )
                                                ),
                                                // Static OAM assignment: oam[i] = oamBase + i *
                                                // tilesPerEntity
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(oamArr, CVar("i")),
                                                        "=",
                                                        CBinaryExpr(
                                                            CLiteral(thisPoolOamBase),
                                                            "+",
                                                            CBinaryExpr(
                                                                CVar("i"),
                                                                "*",
                                                                CLiteral(tilesPerEntity),
                                                            ),
                                                        ),
                                                    )
                                                ),
                                            ),
                                    )
                                )
                            },
                        sectionComment =
                            "Actor pool: $id (max=$maxSize, oamBase=$thisPoolOamBase, tilesPerEntity=$tilesPerEntity)",
                    )

                // --- pool_<id>_spawn(UINT8 x, UINT8 y) : UINT8 ---
                // Static OAM: oam[i] is pre-initialized in init(). spawn just finds a free slot,
                // stores position, marks it active, and calls move_sprite for ALL tiles.
                // No spawn_actor() call needed — OAM slot is already known from the static
                // assignment.
                val spawnBody =
                    buildList<CStatement> {
                        add(CVarDecl("i", CU8, initializer = null))

                        when (pool.config.overflowStrategy) {
                            PoolOverflowStrategy.SILENT_NOOP -> {
                                // Linear scan for free slot; return 0xFF sentinel when pool is full
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("i"), "=", CLiteral(0))
                                            ),
                                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxSize)),
                                        increment = CUnaryExpr("++", CVar("i")),
                                        body =
                                            listOf(
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CArrayAccess(activeArr, CVar("i")),
                                                            "==",
                                                            CLiteral(0),
                                                        ),
                                                    thenBody =
                                                        listOf(CRawCode("goto pool_${id}_found;")),
                                                )
                                            ),
                                    )
                                )
                                add(CReturn(CRawExpr("0xFF")))
                                add(CRawCode("pool_${id}_found:"))
                            }

                            PoolOverflowStrategy.RECYCLE_OLDEST -> {
                                // Static oldest-slot counter for round-robin recycling
                                add(CRawCode("static UINT8 _pool_${id}_oldest = 0;"))
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("i"), "=", CLiteral(0))
                                            ),
                                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxSize)),
                                        increment = CUnaryExpr("++", CVar("i")),
                                        body =
                                            listOf(
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CArrayAccess(activeArr, CVar("i")),
                                                            "==",
                                                            CLiteral(0),
                                                        ),
                                                    thenBody =
                                                        listOf(CRawCode("goto pool_${id}_found;")),
                                                )
                                            ),
                                    )
                                )
                                // No free slot: pick oldest and advance pointer
                                add(
                                    CExprStatement(
                                        CBinaryExpr(CVar("i"), "=", CVar("_pool_${id}_oldest"))
                                    )
                                )
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_pool_${id}_oldest"),
                                            "=",
                                            CBinaryExpr(
                                                CBinaryExpr(
                                                    CVar("_pool_${id}_oldest"),
                                                    "+",
                                                    CLiteral(1),
                                                ),
                                                "%",
                                                CLiteral(maxSize),
                                            ),
                                        )
                                    )
                                )
                                // Recycle: hide all OAM tiles for the slot being recycled
                                for (tileIndex in 0 until tilesPerEntity) {
                                    val oamSlotExpr: CExpr =
                                        if (tileIndex == 0) CArrayAccess(oamArr, CVar("i"))
                                        else
                                            CBinaryExpr(
                                                CArrayAccess(oamArr, CVar("i")),
                                                "+",
                                                CLiteral(tileIndex),
                                            )
                                    add(
                                        CExprStatement(
                                            CCall(
                                                "move_sprite",
                                                listOf(oamSlotExpr, CLiteral(0), CLiteral(0)),
                                            )
                                        )
                                    )
                                }
                                add(CRawCode("pool_${id}_found:"))
                            }
                        }

                        // Store per-instance position and mark active
                        // Note: oam[i] is already set by pool_<id>_init() — no spawn_actor()
                        // needed.
                        add(
                            CExprStatement(
                                CBinaryExpr(CArrayAccess(xArr, CVar("i")), "=", CVar("x"))
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(CArrayAccess(yArr, CVar("i")), "=", CVar("y"))
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(CArrayAccess(activeArr, CVar("i")), "=", CLiteral(1))
                            )
                        )
                        // Sync ALL tiles to OAM hardware on spawn
                        var tileIndex = 0
                        for (row in 0 until tilesHigh) {
                            for (col in 0 until tilesWide) {
                                val oamSlotExpr: CExpr =
                                    if (tileIndex == 0) CArrayAccess(oamArr, CVar("i"))
                                    else
                                        CBinaryExpr(
                                            CArrayAccess(oamArr, CVar("i")),
                                            "+",
                                            CLiteral(tileIndex),
                                        )
                                val xOffset = 8 + col * 8
                                val yOffset = 16 + row * 8
                                add(
                                    CExprStatement(
                                        CCall(
                                            "move_sprite",
                                            listOf(
                                                oamSlotExpr,
                                                CBinaryExpr(CVar("x"), "+", CLiteral(xOffset)),
                                                CBinaryExpr(CVar("y"), "+", CLiteral(yOffset)),
                                            ),
                                        )
                                    )
                                )
                                tileIndex++
                            }
                        }
                        // Return pool index (not OAM slot)
                        add(CReturn(CVar("i")))
                    }

                functions +=
                    CFunction(
                        name = "pool_${id}_spawn",
                        returnType = CU8,
                        params = listOf(CParam("x", CU8), CParam("y", CU8)),
                        body = spawnBody,
                    )

                // --- pool_<id>_destroy(UINT8 i) ---
                // Static OAM: hide all OAM tiles via move_sprite(oam[i]+t, 0, 0) for each tile.
                // Does NOT reset oam[i] to 0xFF — the static assignment is permanent.
                // Marks slot inactive.
                functions +=
                    CFunction(
                        name = "pool_${id}_destroy",
                        returnType = CVoid,
                        params = listOf(CParam("i", CU8)),
                        body =
                            buildList {
                                add(
                                    CIf(
                                        condition = CBinaryExpr(CVar("i"), ">=", CLiteral(maxSize)),
                                        thenBody = listOf(CReturn(null)),
                                    )
                                )
                                // Death callback — execute before releasing the slot
                                for (callbackOp in pool.deathCallback) {
                                    add(callbackOp.accept(ScriptOpVisitor))
                                }
                                // Hide all OAM tiles by moving them off-screen
                                for (tileIndex in 0 until tilesPerEntity) {
                                    val oamSlotExpr: CExpr =
                                        if (tileIndex == 0) CArrayAccess(oamArr, CVar("i"))
                                        else
                                            CBinaryExpr(
                                                CArrayAccess(oamArr, CVar("i")),
                                                "+",
                                                CLiteral(tileIndex),
                                            )
                                    add(
                                        CExprStatement(
                                            CCall(
                                                "move_sprite",
                                                listOf(oamSlotExpr, CLiteral(0), CLiteral(0)),
                                            )
                                        )
                                    )
                                }
                                // Mark pool slot as inactive (oam[i] stays as static assignment)
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CArrayAccess(activeArr, CVar("i")),
                                            "=",
                                            CLiteral(0),
                                        )
                                    )
                                )
                            },
                    )

                // --- pool_<id>_active_count() : UINT8 ---
                // Counts the number of active slots in the pool by iterating the active bitmap.
                functions +=
                    CFunction(
                        name = "pool_${id}_active_count",
                        returnType = CU8,
                        params = emptyList(),
                        body =
                            buildList {
                                add(CVarDecl("count", CU8, initializer = CLiteral(0)))
                                add(CVarDecl("i", CU8, initializer = null))
                                add(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("i"), "=", CLiteral(0))
                                            ),
                                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxSize)),
                                        increment = CUnaryExpr("++", CVar("i")),
                                        body =
                                            listOf(
                                                CIf(
                                                    condition = CArrayAccess(activeArr, CVar("i")),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CUnaryExpr("++", CVar("count"))
                                                            )
                                                        ),
                                                )
                                            ),
                                    )
                                )
                                add(CReturn(CVar("count")))
                            },
                        sectionComment = "Actor pool active count: $id",
                    )
            }
            return functions
        }
    }

    override fun visitCombatEngineSystem(system: CombatEngineSystem): List<CFunction> =
        CombatVisitor(gameIR).generateCombatFunctions(system)

    // =========================================================================
    // Puzzle object codegen — state variables + interaction check functions
    // =========================================================================

    /**
     * Generate state variables and C functions for all puzzle objects in [gameIR].
     *
     * Per puzzle object type:
     * - **Switch**: `UINT8 _switch_{id}_active = 0` + `puzzle_activate_{id}()` toggling state and
     *   running onActivate/onDeactivate ScriptOps.
     * - **Door**: `UINT8 _door_{id}_open = 0` + `puzzle_activate_{id}()` (open: tile swap +
     *   onOpen) + `puzzle_deactivate_{id}()` (close: tile swap + onClose).
     * - **Pressure plate**: `UINT8 _plate_{id}_pressed = 0` + `puzzle_check_plate_{id}()` that
     *   tests actor/pool entity positions against plate coordinates, running onStepOn/onStepOff on
     *   transitions. Pool entities use the prefix `pool:` in respondToActorIds.
     * - **Timed block**: `UINT16 _timedblock_{id}_timer = 0` + `UINT8 _timedblock_{id}_solid = 1`
     *     + `puzzle_update_timedblock_{id}()` that increments the timer and swaps tiles on
     *       interval.
     * - **Trigger**: `puzzle_trigger_{id}_fire(UINT8 event)` with switch-case dispatch to per-event
     *   handler callbacks based on [PuzzleEventType] ordinal value.
     *
     * requires() chaining: When a puzzle object has non-empty [requires], the generated activate
     * function checks all required objects' active state variables before proceeding. Example: `if
     * (!_switch_sw1_active || !_switch_sw2_active) return;`
     *
     * Also generates `puzzle_update_all()` that calls all per-frame checks (pressure plates and
     * timed block updates) in order.
     *
     * @return Pair of (state variable declarations, C functions).
     */
    fun buildPuzzleObjectFunctions(gameIR: GameIR): Pair<List<CVarDecl>, List<CFunction>> {
        if (gameIR.puzzleObjects.isEmpty()) return emptyList<CVarDecl>() to emptyList()

        val vars = mutableListOf<CVarDecl>()
        val functions = mutableListOf<CFunction>()
        val perFrameCalls = mutableListOf<CStatement>()
        val puzzleById = gameIR.puzzleObjects.associateBy { it.id }

        for (obj in gameIR.puzzleObjects) {
            val id = obj.id.replace('-', '_').replace(' ', '_')
            val output =
                when (obj) {
                    is SwitchObjectIR -> buildSwitchObjectOutput(obj, id, puzzleById)
                    is DoorObjectIR -> buildDoorObjectOutput(obj, id, puzzleById)
                    is PressurePlateObjectIR -> buildPressurePlateObjectOutput(obj, id, puzzleById)
                    is TimedBlockObjectIR -> buildTimedBlockObjectOutput(obj, id, puzzleById)
                    is TriggerObjectIR -> buildTriggerObjectOutput(obj, id, puzzleById)
                }
            vars += output.vars
            functions += output.functions
            perFrameCalls += output.perFrameCalls
        }

        functions +=
            CFunction(
                name = "puzzle_update_all",
                returnType = CVoid,
                body = perFrameCalls,
                sectionComment = "Puzzle per-frame update dispatcher",
            )

        return vars to functions
    }

    /**
     * Build requires() guard statements for a puzzle object's activate function.
     *
     * Returns a list of early-return `if` statements that check all required objects' active state
     * variables. If any required object is not active, the function returns immediately.
     *
     * The active variable name is derived from the required object's type:
     * - Switch → `_switch_{id}_active`
     * - Door → `_door_{id}_open`
     * - Pressure plate → `_plate_{id}_pressed`
     * - Timed block → `_timedblock_{id}_solid`
     * - Trigger → `_trigger_{id}_hidden` (inverted: trigger is "active" when not hidden)
     *
     * @param requiresIds List of puzzle object IDs that must be active.
     * @param puzzleById Map from puzzle object ID to [PuzzleObjectIR] for type lookup.
     * @return List of [CIf] statements that return early if any required object is inactive. Empty
     *   if [requiresIds] is empty.
     */
    private fun buildRequiresGuard(
        requiresIds: List<String>,
        puzzleById: Map<String, io.github.gbkt.core.ir.PuzzleObjectIR>,
    ): List<CStatement> {
        if (requiresIds.isEmpty()) return emptyList()

        // Build condition: !req1 || !req2 || ...
        // If any required object is not active → return early
        val conditions = requiresIds.mapNotNull { reqId ->
            val sanitizedId = reqId.replace('-', '_').replace(' ', '_')
            val activeVar =
                when (puzzleById[reqId]) {
                    is SwitchObjectIR -> "_switch_${sanitizedId}_active"
                    is DoorObjectIR -> "_door_${sanitizedId}_open"
                    is PressurePlateObjectIR -> "_plate_${sanitizedId}_pressed"
                    is TimedBlockObjectIR -> "_timedblock_${sanitizedId}_solid"
                    is TriggerObjectIR -> null // triggers have no simple active state — skip
                    null -> "_switch_${sanitizedId}_active" // fallback: assume switch naming
                } ?: return@mapNotNull null
            CRawExpr("!$activeVar")
        }

        if (conditions.isEmpty()) return emptyList()

        // Chain conditions: !req1 || !req2 || ...
        // reduceOrNull needs explicit typing since CRawExpr and CBinaryExpr share CExpr supertype
        val combined =
            conditions
                .map { it as CExpr }
                .reduceOrNull { acc, cond -> CBinaryExpr(acc, "||", cond) } ?: return emptyList()

        return listOf(CIf(condition = combined, thenBody = listOf(CReturn(null))))
    }

    /** Generate `puzzle_reveal_{id}()` — clears the hidden flag for a puzzle object. */
    private fun buildPuzzleRevealFunction(id: String, hiddenVarName: String): CFunction =
        CFunction(
            name = "puzzle_reveal_$id",
            returnType = CVoid,
            body = listOf(CExprStatement(CBinaryExpr(CVar(hiddenVarName), "=", CLiteral(0)))),
        )

    /** Generate `puzzle_hide_{id}()` — sets the hidden flag for a puzzle object. */
    private fun buildPuzzleHideFunction(id: String, hiddenVarName: String): CFunction =
        CFunction(
            name = "puzzle_hide_$id",
            returnType = CVoid,
            body = listOf(CExprStatement(CBinaryExpr(CVar(hiddenVarName), "=", CLiteral(1)))),
        )

    /** Accumulated output for a single puzzle object: vars, functions, and per-frame calls. */
    private data class PuzzleObjectOutput(
        val vars: List<CVarDecl>,
        val functions: List<CFunction>,
        val perFrameCalls: List<CStatement> = emptyList(),
    )

    private fun buildSwitchObjectOutput(
        obj: SwitchObjectIR,
        id: String,
        puzzleById: Map<String, io.github.gbkt.core.ir.PuzzleObjectIR>,
    ): PuzzleObjectOutput {
        val vars = mutableListOf<CVarDecl>()
        val functions = mutableListOf<CFunction>()

        // State variable: UINT8 _switch_{id}_active = 0
        vars += CVarDecl(name = "_switch_${id}_active", type = CU8, initializer = CLiteral(0))

        // puzzle_activate_{id}(): toggle switch active state, run onActivate
        val activateBody =
            buildList<CStatement> {
                // requires() guard: if any required object is not active, return early
                addAll(buildRequiresGuard(obj.requires, puzzleById))
                // _switch_{id}_active = 1;
                add(CExprStatement(CBinaryExpr(CVar("_switch_${id}_active"), "=", CLiteral(1))))
                // Run onActivate ScriptOps
                for (op in obj.onActivate) add(ScriptOpVisitor.visit(op))
            }
        functions +=
            CFunction(
                name = "puzzle_activate_$id",
                returnType = CVoid,
                body = activateBody,
                sectionComment = "Puzzle switch: $id",
            )

        // puzzle_deactivate_{id}(): toggle switch inactive state, run onDeactivate
        val deactivateBody =
            buildList<CStatement> {
                // _switch_{id}_active = 0;
                add(CExprStatement(CBinaryExpr(CVar("_switch_${id}_active"), "=", CLiteral(0))))
                // Run onDeactivate ScriptOps
                for (op in obj.onDeactivate) add(ScriptOpVisitor.visit(op))
            }
        functions +=
            CFunction(
                name = "puzzle_deactivate_$id",
                returnType = CVoid,
                body = deactivateBody,
            )

        // Hidden state variable and reveal/hide functions
        vars +=
            CVarDecl(
                name = "_switch_${id}_hidden",
                type = CU8,
                initializer = CLiteral(if (obj.hidden) 1 else 0),
            )
        functions += buildPuzzleRevealFunction(id, "_switch_${id}_hidden")
        functions += buildPuzzleHideFunction(id, "_switch_${id}_hidden")

        return PuzzleObjectOutput(vars = vars, functions = functions)
    }

    private fun buildDoorObjectOutput(
        obj: DoorObjectIR,
        id: String,
        puzzleById: Map<String, io.github.gbkt.core.ir.PuzzleObjectIR>,
    ): PuzzleObjectOutput {
        val vars = mutableListOf<CVarDecl>()
        val functions = mutableListOf<CFunction>()

        // State variable: UINT8 _door_{id}_open = 0
        vars += CVarDecl(name = "_door_${id}_open", type = CU8, initializer = CLiteral(0))

        // puzzle_activate_{id}(): open door — set state, swap tile to openTile, run onOpen
        val openBody =
            buildList<CStatement> {
                // requires() guard: all required objects must be active before door opens
                addAll(buildRequiresGuard(obj.requires, puzzleById))
                add(CExprStatement(CBinaryExpr(CVar("_door_${id}_open"), "=", CLiteral(1))))
                // Swap the door's background tile to its open graphic.
                add(
                    CExprStatement(
                        CCall(
                            "set_bkg_tile_xy",
                            listOf(CLiteral(obj.x), CLiteral(obj.y), CLiteral(obj.openTile)),
                        )
                    )
                )
                for (op in obj.onOpen) add(ScriptOpVisitor.visit(op))
            }
        functions +=
            CFunction(
                name = "puzzle_activate_$id",
                returnType = CVoid,
                body = openBody,
                sectionComment = "Puzzle door: $id",
            )

        // puzzle_deactivate_{id}(): close door — set state, swap tile to closedTile, run onClose
        val closeBody =
            buildList<CStatement> {
                add(CExprStatement(CBinaryExpr(CVar("_door_${id}_open"), "=", CLiteral(0))))
                // Swap the door's background tile back to its closed graphic.
                add(
                    CExprStatement(
                        CCall(
                            "set_bkg_tile_xy",
                            listOf(CLiteral(obj.x), CLiteral(obj.y), CLiteral(obj.closedTile)),
                        )
                    )
                )
                for (op in obj.onClose) add(ScriptOpVisitor.visit(op))
            }
        functions +=
            CFunction(
                name = "puzzle_deactivate_$id",
                returnType = CVoid,
                body = closeBody,
            )

        // Hidden state variable and reveal/hide functions
        vars +=
            CVarDecl(
                name = "_door_${id}_hidden",
                type = CU8,
                initializer = CLiteral(if (obj.hidden) 1 else 0),
            )
        functions += buildPuzzleRevealFunction(id, "_door_${id}_hidden")
        functions += buildPuzzleHideFunction(id, "_door_${id}_hidden")

        return PuzzleObjectOutput(vars = vars, functions = functions)
    }

    private fun buildPressurePlateObjectOutput(
        obj: PressurePlateObjectIR,
        id: String,
        puzzleById: Map<String, io.github.gbkt.core.ir.PuzzleObjectIR>,
    ): PuzzleObjectOutput {
        val vars = mutableListOf<CVarDecl>()
        val functions = mutableListOf<CFunction>()

        // State variable: UINT8 _plate_{id}_pressed = 0
        vars += CVarDecl(name = "_plate_${id}_pressed", type = CU8, initializer = CLiteral(0))

        // puzzle_check_plate_{id}(): test if any respondTo actor/pool entity is at plate position
        val checkBody =
            buildList<CStatement> {
                // requires() guard: check required objects before processing step events
                val requiresGuard = buildRequiresGuard(obj.requires, puzzleById)
                if (requiresGuard.isNotEmpty()) addAll(requiresGuard)

                // UINT8 _on = 0; (local var)
                add(CVarDecl("_on", CU8, initializer = CLiteral(0)))
                // Check each respondTo actor or pool entity
                for (actorId in obj.respondToActorIds) {
                    if (actorId.startsWith("pool:")) {
                        // Pool entity: pool:<poolName> — check pool entity positions
                        val poolName =
                            actorId.removePrefix("pool:").replace('-', '_').replace(' ', '_')
                        // pool_<name>_any_at(plate.x, plate.y) returns nonzero if any entity there
                        add(
                            CIf(
                                condition =
                                    CCall(
                                        "pool_${poolName}_any_at",
                                        listOf(CLiteral(obj.x), CLiteral(obj.y)),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(CBinaryExpr(CVar("_on"), "=", CLiteral(1)))
                                    ),
                            )
                        )
                    } else {
                        val sanitizedActorId = actorId.replace('-', '_').replace(' ', '_')
                        // if (_<actor>_x == plate.x && _<actor>_y == plate.y) _on = 1;
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CBinaryExpr(
                                            CVar("_${sanitizedActorId}_x"),
                                            "==",
                                            CLiteral(obj.x),
                                        ),
                                        "&&",
                                        CBinaryExpr(
                                            CVar("_${sanitizedActorId}_y"),
                                            "==",
                                            CLiteral(obj.y),
                                        ),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(CBinaryExpr(CVar("_on"), "=", CLiteral(1)))
                                    ),
                            )
                        )
                    }
                }
                // if (_on && !_plate_{id}_pressed) { onStepOn; _plate_{id}_pressed = 1; }
                if (obj.onStepOn.isNotEmpty()) {
                    val stepOnBody =
                        buildList<CStatement> {
                            for (op in obj.onStepOn) add(ScriptOpVisitor.visit(op))
                            add(
                                CExprStatement(
                                    CBinaryExpr(CVar("_plate_${id}_pressed"), "=", CLiteral(1))
                                )
                            )
                        }
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_on"),
                                    "&&",
                                    CRawExpr("!_plate_${id}_pressed"),
                                ),
                            thenBody = stepOnBody,
                        )
                    )
                } else {
                    // No onStepOn ops — just update state
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_on"),
                                    "&&",
                                    CRawExpr("!_plate_${id}_pressed"),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_plate_${id}_pressed"),
                                            "=",
                                            CLiteral(1),
                                        )
                                    )
                                ),
                        )
                    )
                }
                // if (!_on && _plate_{id}_pressed) { onStepOff; _plate_{id}_pressed = 0; }
                if (obj.onStepOff.isNotEmpty()) {
                    val stepOffBody =
                        buildList<CStatement> {
                            for (op in obj.onStepOff) add(ScriptOpVisitor.visit(op))
                            add(
                                CExprStatement(
                                    CBinaryExpr(CVar("_plate_${id}_pressed"), "=", CLiteral(0))
                                )
                            )
                        }
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CRawExpr("!_on"),
                                    "&&",
                                    CVar("_plate_${id}_pressed"),
                                ),
                            thenBody = stepOffBody,
                        )
                    )
                } else {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CRawExpr("!_on"),
                                    "&&",
                                    CVar("_plate_${id}_pressed"),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_plate_${id}_pressed"),
                                            "=",
                                            CLiteral(0),
                                        )
                                    )
                                ),
                        )
                    )
                }
            }
        functions +=
            CFunction(
                name = "puzzle_check_plate_$id",
                returnType = CVoid,
                body = checkBody,
                sectionComment = "Puzzle pressure plate: $id",
            )

        // Hidden state variable and reveal/hide functions
        vars +=
            CVarDecl(
                name = "_plate_${id}_hidden",
                type = CU8,
                initializer = CLiteral(if (obj.hidden) 1 else 0),
            )
        functions += buildPuzzleRevealFunction(id, "_plate_${id}_hidden")
        functions += buildPuzzleHideFunction(id, "_plate_${id}_hidden")

        return PuzzleObjectOutput(
            vars = vars,
            functions = functions,
            perFrameCalls = listOf(CExprStatement(CCall("puzzle_check_plate_$id", emptyList()))),
        )
    }

    private fun buildTimedBlockObjectOutput(
        obj: TimedBlockObjectIR,
        id: String,
        puzzleById: Map<String, io.github.gbkt.core.ir.PuzzleObjectIR>,
    ): PuzzleObjectOutput {
        val vars = mutableListOf<CVarDecl>()
        val functions = mutableListOf<CFunction>()

        // State variables: UINT16 _timedblock_{id}_timer = 0, UINT8 _timedblock_{id}_solid = 1
        vars += CVarDecl(name = "_timedblock_${id}_timer", type = CU16, initializer = CLiteral(0))
        vars += CVarDecl(name = "_timedblock_${id}_solid", type = CU8, initializer = CLiteral(1))

        // puzzle_update_timedblock_{id}(): increment timer, swap tile when interval reached
        val updateBody =
            buildList<CStatement> {
                // requires() guard: only update when required objects are active
                addAll(buildRequiresGuard(obj.requires, puzzleById))
                // _timedblock_{id}_timer++;
                add(CExprStatement(CUnaryExpr("++", CVar("_timedblock_${id}_timer"))))
                // if (_timedblock_{id}_timer >= interval) { swap tile; reset timer; }
                val swapBody =
                    buildList<CStatement> {
                        // _timedblock_{id}_timer = 0;
                        add(
                            CExprStatement(
                                CBinaryExpr(CVar("_timedblock_${id}_timer"), "=", CLiteral(0))
                            )
                        )
                        // if (_timedblock_{id}_solid) { set_bkg_tile_xy(x, y, emptyTile); }
                        // else { set_bkg_tile_xy(x, y, solidTile); }
                        add(
                            CIf(
                                condition = CVar("_timedblock_${id}_solid"),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CCall(
                                                "set_bkg_tile_xy",
                                                listOf(
                                                    CLiteral(obj.x),
                                                    CLiteral(obj.y),
                                                    CLiteral(obj.emptyTile),
                                                ),
                                            )
                                        ),
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_timedblock_${id}_solid"),
                                                "=",
                                                CLiteral(0),
                                            )
                                        ),
                                    ),
                                elseBody =
                                    listOf(
                                        CExprStatement(
                                            CCall(
                                                "set_bkg_tile_xy",
                                                listOf(
                                                    CLiteral(obj.x),
                                                    CLiteral(obj.y),
                                                    CLiteral(obj.solidTile),
                                                ),
                                            )
                                        ),
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_timedblock_${id}_solid"),
                                                "=",
                                                CLiteral(1),
                                            )
                                        ),
                                    ),
                            )
                        )
                    }
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CVar("_timedblock_${id}_timer"),
                                ">=",
                                CLiteral(obj.interval),
                            ),
                        thenBody = swapBody,
                    )
                )
            }
        functions +=
            CFunction(
                name = "puzzle_update_timedblock_$id",
                returnType = CVoid,
                body = updateBody,
                sectionComment = "Puzzle timed block: $id",
            )

        // Hidden state variable and reveal/hide functions
        vars +=
            CVarDecl(
                name = "_timedblock_${id}_hidden",
                type = CU8,
                initializer = CLiteral(if (obj.hidden) 1 else 0),
            )
        functions += buildPuzzleRevealFunction(id, "_timedblock_${id}_hidden")
        functions += buildPuzzleHideFunction(id, "_timedblock_${id}_hidden")

        return PuzzleObjectOutput(
            vars = vars,
            functions = functions,
            perFrameCalls =
                listOf(CExprStatement(CCall("puzzle_update_timedblock_$id", emptyList()))),
        )
    }

    private fun buildTriggerObjectOutput(
        obj: TriggerObjectIR,
        id: String,
        puzzleById: Map<String, io.github.gbkt.core.ir.PuzzleObjectIR>,
    ): PuzzleObjectOutput {
        val vars = mutableListOf<CVarDecl>()
        val functions = mutableListOf<CFunction>()

        // Generic trigger: no built-in state, all logic in handlers.
        // Generates puzzle_trigger_{id}_fire(UINT8 event) with switch-case dispatch.

        // Group handlers by event type for switch-case generation
        val handlersByEvent = obj.handlers.groupBy { it.event }

        // Build switch-case body for each registered event
        val cases =
            buildList<CSwitchCase> {
                for ((eventType, handlers) in handlersByEvent) {
                    val caseBody =
                        buildList<CStatement> {
                            // requires() guard inside each case
                            addAll(buildRequiresGuard(obj.requires, puzzleById))
                            for (handler in handlers) {
                                for (op in handler.actions) add(ScriptOpVisitor.visit(op))
                            }
                            add(CBreak)
                        }
                    // Use event type ordinal as the C case value (EVENT_INTERACT=0, etc.)
                    add(CSwitchCase(value = CLiteral(eventType.ordinal), body = caseBody))
                }
            }

        val fireBody =
            buildList<CStatement> {
                if (cases.isNotEmpty()) {
                    add(CSwitch(expr = CVar("event"), cases = cases))
                }
            }

        functions +=
            CFunction(
                name = "puzzle_trigger_${id}_fire",
                returnType = CVoid,
                params = listOf(CParam("event", CU8)),
                body = fireBody,
                sectionComment = "Puzzle generic trigger: $id",
            )

        // Hidden state variable and reveal/hide functions
        vars +=
            CVarDecl(
                name = "_trigger_${id}_hidden",
                type = CU8,
                initializer = CLiteral(if (obj.hidden) 1 else 0),
            )
        functions += buildPuzzleRevealFunction(id, "_trigger_${id}_hidden")
        functions += buildPuzzleHideFunction(id, "_trigger_${id}_hidden")

        return PuzzleObjectOutput(vars = vars, functions = functions)
    }

    /**
     * Builds NPC-NPC collision check functions for all [CollisionRuleIR] entries.
     *
     * For each rule, generates:
     * - `check_collision_{groupA}_{groupB}()` — nested loop over actors in groupA × groupB with
     *   AABB overlap check and built-in response dispatch.
     * - `check_all_npc_collisions()` — master function calling each rule's check function.
     *
     * Returns an empty list when [GameIR.collisionRules] is empty.
     */
    fun buildNpcCollisionFunctions(gameIR: GameIR): List<CFunction> {
        val rules = gameIR.collisionRules
        if (rules.isEmpty()) return emptyList()

        val groupActors = buildNpcGroupActorMap(gameIR)
        val actorMass = buildNpcActorMassMap(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)

        val ruleFunctions = rules.mapNotNull { rule ->
            buildNpcCollisionRuleFunction(rule, groupActors, actorMass, exprVisitor)
        }

        if (ruleFunctions.isEmpty()) return emptyList()

        val masterBody = ruleFunctions.map { fn -> CExprStatement(CCall(fn.name, emptyList())) }
        val masterFn =
            CFunction(
                name = "check_all_npc_collisions",
                returnType = CVoid,
                body = masterBody,
                sectionComment = "NPC collision master dispatcher",
            )

        return ruleFunctions + listOf(masterFn)
    }

    private fun buildNpcGroupActorMap(gameIR: GameIR): Map<String, List<String>> = buildMap {
        for (actor in gameIR.actors) {
            val cfg = actor.npcCollisionConfig ?: continue
            for (groupId in cfg.groupIds) {
                getOrPut(groupId) { mutableListOf() }
                (getValue(groupId) as MutableList).add(actor.id)
            }
        }
    }

    private fun buildNpcActorMassMap(gameIR: GameIR): Map<String, Int> = buildMap {
        for (actor in gameIR.actors) {
            val cfg = actor.npcCollisionConfig ?: continue
            put(actor.id, cfg.mass)
        }
    }

    private fun buildNpcCollisionRuleFunction(
        rule: CollisionRuleIR,
        groupActors: Map<String, List<String>>,
        actorMass: Map<String, Int>,
        exprVisitor: ExprVisitor,
    ): CFunction? {
        val actorsA = groupActors[rule.groupA] ?: emptyList()
        val actorsB = groupActors[rule.groupB] ?: emptyList()
        if (actorsA.isEmpty() || actorsB.isEmpty()) return null

        val fnName =
            "check_collision_${rule.groupA.replace('-', '_')}_${rule.groupB.replace('-', '_')}"
        val body = mutableListOf<CStatement>()
        body += buildNpcIntervalGuardStatements(rule, fnName)

        // Nested loop: for each actor in A, for each actor in B (explicit unrolled)
        for (idA in actorsA) {
            for (idB in actorsB) {
                if (rule.groupA == rule.groupB && idA == idB) continue
                body.add(buildNpcActorPairCollisionCheck(rule, idA, idB, actorMass, exprVisitor))
            }
        }

        return CFunction(
            name = fnName,
            returnType = CVoid,
            body = body,
            sectionComment = "NPC collision: ${rule.groupA} vs ${rule.groupB} (${rule.response})",
        )
    }

    private fun buildNpcIntervalGuardStatements(
        rule: CollisionRuleIR,
        fnName: String,
    ): List<CStatement> {
        if (rule.interval <= 1) return emptyList()
        val counterName = "_npc_interval_${fnName}"
        return listOf(
            CVarDecl(counterName, CU8, CLiteral(0), isStatic = true),
            CExprStatement(
                CBinaryExpr(
                    CVar(counterName),
                    "=",
                    CBinaryExpr(
                        CBinaryExpr(CVar(counterName), "+", CLiteral(1)),
                        "%",
                        CLiteral(rule.interval),
                    ),
                )
            ),
            CIf(
                condition = CBinaryExpr(CVar(counterName), "!=", CLiteral(0)),
                thenBody = listOf(CReturn()),
            ),
        )
    }

    private fun buildNpcActorPairCollisionCheck(
        rule: CollisionRuleIR,
        idA: String,
        idB: String,
        actorMass: Map<String, Int>,
        exprVisitor: ExprVisitor,
    ): CStatement {
        val xA = CVar("_${idA}_x")
        val yA = CVar("_${idA}_y")
        val xB = CVar("_${idB}_x")
        val yB = CVar("_${idB}_y")
        // AABB overlap: ax < bx+8 && ax+8 > bx && ay < by+8 && ay+8 > by
        // Using 8 as default hitbox size for sprite entities
        val hitSize = CLiteral(8)
        val overlapX =
            CBinaryExpr(
                CBinaryExpr(xA, "<", CBinaryExpr(xB, "+", hitSize)),
                "&&",
                CBinaryExpr(CBinaryExpr(xA, "+", hitSize), ">", xB),
            )
        val overlapY =
            CBinaryExpr(
                CBinaryExpr(yA, "<", CBinaryExpr(yB, "+", hitSize)),
                "&&",
                CBinaryExpr(CBinaryExpr(yA, "+", hitSize), ">", yB),
            )
        val aabb = CBinaryExpr(overlapX, "&&", overlapY)
        val responseBody =
            buildNpcCollisionResponseStatements(rule, idA, idB, actorMass, exprVisitor)
        return if (responseBody.isNotEmpty()) {
            CIf(condition = aabb, thenBody = responseBody)
        } else {
            // OVERLAP with no callback — still emit the check to allow future extension
            CIf(condition = aabb, thenBody = listOf(CRawCode("/* overlap */")))
        }
    }

    private fun buildNpcCollisionResponseStatements(
        rule: CollisionRuleIR,
        idA: String,
        idB: String,
        actorMass: Map<String, Int>,
        exprVisitor: ExprVisitor,
    ): List<CStatement> {
        val responseBody = mutableListOf<CStatement>()
        // Built-in response dispatch
        when (rule.response) {
            CollisionResponse.OVERLAP -> {
                // No movement change — only callback
            }
            CollisionResponse.BLOCK -> {
                // Zero velocity of actor A on collision
                responseBody.add(CComment("BLOCK: stop actor $idA velocity"))
                responseBody.add(CExprStatement(CBinaryExpr(CVar("_${idA}_vx"), "=", CLiteral(0))))
                responseBody.add(CExprStatement(CBinaryExpr(CVar("_${idA}_vy"), "=", CLiteral(0))))
            }
            CollisionResponse.BOUNCE -> {
                // Reverse velocity of actor A
                responseBody.add(CComment("BOUNCE: reverse actor $idA velocity"))
                responseBody.add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_${idA}_vx"),
                            "=",
                            CUnaryExpr("-", CVar("_${idA}_vx")),
                        )
                    )
                )
                responseBody.add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_${idA}_vy"),
                            "=",
                            CUnaryExpr("-", CVar("_${idA}_vy")),
                        )
                    )
                )
            }
            CollisionResponse.PUSH -> {
                // Mass-proportional displacement
                // dispA = massB / (massA + massB), dispB = massA / (massA + massB)
                val massA = actorMass[idA] ?: 1
                val massB = actorMass[idB] ?: 1
                val totalMass = massA + massB
                val xA = CVar("_${idA}_x")
                val xB = CVar("_${idB}_x")
                responseBody.add(CComment("PUSH: mass $idA=$massA, $idB=$massB, total=$totalMass"))
                // Horizontal overlap displacement
                responseBody.add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(xA, "+", CLiteral(4)),
                                "<",
                                CBinaryExpr(xB, "+", CLiteral(4)),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        xA,
                                        "=",
                                        CBinaryExpr(
                                            xA,
                                            "-",
                                            CLiteral(massB / totalMass.coerceAtLeast(1)),
                                        ),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        xB,
                                        "=",
                                        CBinaryExpr(
                                            xB,
                                            "+",
                                            CLiteral(massA / totalMass.coerceAtLeast(1)),
                                        ),
                                    )
                                ),
                            ),
                        elseBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        xA,
                                        "=",
                                        CBinaryExpr(
                                            xA,
                                            "+",
                                            CLiteral(massB / totalMass.coerceAtLeast(1)),
                                        ),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        xB,
                                        "=",
                                        CBinaryExpr(
                                            xB,
                                            "-",
                                            CLiteral(massA / totalMass.coerceAtLeast(1)),
                                        ),
                                    )
                                ),
                            ),
                    )
                )
            }
        }
        // Emit onCollide script ops (if any) as typed C statements
        if (rule.onCollide.isNotEmpty()) {
            responseBody.add(CComment("onCollide callback"))
            for (op in rule.onCollide) {
                responseBody.add(ScriptOpVisitor.visit(op, exprVisitor))
            }
        }
        return responseBody
    }

    // -------------------------------------------------------------------------
    // PickupSystem: shared engine pickup/collectible mechanics (Plan 06.8-09)
    //
    // Generated functions:
    //  - pickup_init_{id}()         — zero-initialise state arrays
    //  - pickup_check_collect_{id}(player_x, player_y) — AABB check each active pickup
    //  - pickup_spawn_{id}(pickup_type, x, y)  — add pickup to active array
    //  - pickup_update_{id}()       — decrement timed-effect counters
    //  - pickup_respawn_check_{id}() — respawn pickups when respawnFrames timer expires
    // -------------------------------------------------------------------------

    /**
     * Generate C functions for the shared pickup/collectible system (Plan 06.8-09).
     *
     * Handles three pickup effect modes: `"instant"` (score/coins), `"timed"` (speed boost), and
     * `"permanent"` (key items). Produces five functions:
     * - `pickup_init_{id}()` — initialise state arrays to zero
     * - `pickup_check_collect_{id}(player_x, player_y)` — AABB overlap check
     * - `pickup_spawn_{id}(pickup_type, x, y)` — add pickup to active list
     * - `pickup_update_{id}()` — decrement timed counters and clear expired effects
     * - `pickup_respawn_check_{id}()` — re-spawn pickups after respawnFrames elapsed
     *
     * @param sanitizedId System ID with hyphens/spaces replaced by underscores.
     * @param system [GenericSystem] carrying `"pickupConfig"` → [PickupSystemConfig].
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildPickupFunctions(sanitizedId: String, system: GenericSystem): List<CFunction> {
        val pickupConfig =
            system.config["pickupConfig"] as? PickupSystemConfig ?: PickupSystemConfig()
        val pickups = pickupConfig.pickups
        val maxTotal = pickupConfig.maxTotalPickups

        val hasTimedPickup = pickups.any { it.effectType == "timed" }
        val hasRespawnPickup = pickups.any { it.respawnFrames > 0 }

        val functions = mutableListOf<CFunction>()

        // ---- pickup_init_{id}() ----
        // Zero-initialise active pickup state arrays.
        val initBody =
            buildList<CStatement> {
                add(CComment("Initialise pickup state: $maxTotal slots"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxTotal)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(
                                            CVar("_pickup_active_$sanitizedId"),
                                            CVar("i"),
                                        ),
                                        "=",
                                        CLiteral(0),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_pickup_type_$sanitizedId"), CVar("i")),
                                        "=",
                                        CLiteral(0),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_pickup_x_$sanitizedId"), CVar("i")),
                                        "=",
                                        CLiteral(0),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_pickup_y_$sanitizedId"), CVar("i")),
                                        "=",
                                        CLiteral(0),
                                    )
                                ),
                            ),
                    )
                )
                if (hasTimedPickup) {
                    add(CComment("Clear timed effect counters"))
                    add(
                        CFor(
                            init = CVarDecl("j", CU8, CLiteral(0)),
                            condition = CBinaryExpr(CVar("j"), "<", CLiteral(maxTotal)),
                            increment = CUnaryExpr("++", CVar("j")),
                            body =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CArrayAccess(
                                                CVar("_pickup_timer_$sanitizedId"),
                                                CVar("j"),
                                            ),
                                            "=",
                                            CLiteral(0),
                                        )
                                    )
                                ),
                        )
                    )
                }
                if (hasRespawnPickup) {
                    add(CComment("Clear respawn countdown timers"))
                    add(
                        CFor(
                            init = CVarDecl("k", CU8, CLiteral(0)),
                            condition = CBinaryExpr(CVar("k"), "<", CLiteral(maxTotal)),
                            increment = CUnaryExpr("++", CVar("k")),
                            body =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CArrayAccess(
                                                CVar("_pickup_respawn_timer_$sanitizedId"),
                                                CVar("k"),
                                            ),
                                            "=",
                                            CLiteral(0),
                                        )
                                    )
                                ),
                        )
                    )
                }
            }
        functions.add(
            CFunction(
                name = "pickup_init_$sanitizedId",
                returnType = CVoid,
                body = initBody,
                sectionComment = "Pickup system: $sanitizedId — init",
            )
        )

        // ---- pickup_spawn_{id}(pickup_type, x, y) ----
        // Add a pickup to the first available active slot.
        val spawnBody =
            buildList<CStatement> {
                add(CComment("Find first empty slot and activate pickup"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxTotal)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CArrayAccess(
                                                CVar("_pickup_active_$sanitizedId"),
                                                CVar("i"),
                                            ),
                                            "==",
                                            CLiteral(0),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_pickup_type_$sanitizedId"),
                                                        CVar("i"),
                                                    ),
                                                    "=",
                                                    CVar("pickup_type"),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_pickup_x_$sanitizedId"),
                                                        CVar("i"),
                                                    ),
                                                    "=",
                                                    CVar("x"),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_pickup_y_$sanitizedId"),
                                                        CVar("i"),
                                                    ),
                                                    "=",
                                                    CVar("y"),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_pickup_active_$sanitizedId"),
                                                        CVar("i"),
                                                    ),
                                                    "=",
                                                    CLiteral(1),
                                                )
                                            ),
                                            CReturn(null),
                                        ),
                                    elseBody = emptyList(),
                                )
                            ),
                    )
                )
            }
        functions.add(
            CFunction(
                name = "pickup_spawn_$sanitizedId",
                returnType = CVoid,
                params = listOf(CParam("pickup_type", CU8), CParam("x", CU8), CParam("y", CU8)),
                body = spawnBody,
                sectionComment = "Pickup system: $sanitizedId — spawn",
            )
        )

        // ---- pickup_check_collect_{id}(player_x, player_y) ----
        // AABB check: iterate active pickups; collect if player overlaps.
        val collectBody =
            buildList<CStatement> {
                add(CComment("AABB check: collect any pickup overlapping player position"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxTotal)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CArrayAccess(
                                                CVar("_pickup_active_$sanitizedId"),
                                                CVar("i"),
                                            ),
                                            "==",
                                            CLiteral(1),
                                        ),
                                    thenBody =
                                        listOf(
                                            // Overlap check: player within 8-pixel AABB of pickup
                                            // centre
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("player_x"),
                                                                ">=",
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_pickup_x_$sanitizedId"
                                                                        ),
                                                                        CVar("i"),
                                                                    ),
                                                                    "-",
                                                                    CLiteral(4),
                                                                ),
                                                            ),
                                                            "&&",
                                                            CBinaryExpr(
                                                                CVar("player_x"),
                                                                "<=",
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_pickup_x_$sanitizedId"
                                                                        ),
                                                                        CVar("i"),
                                                                    ),
                                                                    "+",
                                                                    CLiteral(4),
                                                                ),
                                                            ),
                                                        ),
                                                        "&&",
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("player_y"),
                                                                ">=",
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_pickup_y_$sanitizedId"
                                                                        ),
                                                                        CVar("i"),
                                                                    ),
                                                                    "-",
                                                                    CLiteral(4),
                                                                ),
                                                            ),
                                                            "&&",
                                                            CBinaryExpr(
                                                                CVar("player_y"),
                                                                "<=",
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_pickup_y_$sanitizedId"
                                                                        ),
                                                                        CVar("i"),
                                                                    ),
                                                                    "+",
                                                                    CLiteral(4),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                thenBody =
                                                    buildList<CStatement> {
                                                        // Deactivate pickup slot
                                                        add(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_pickup_active_$sanitizedId"
                                                                        ),
                                                                        CVar("i"),
                                                                    ),
                                                                    "=",
                                                                    CLiteral(0),
                                                                )
                                                            )
                                                        )
                                                        // Start timed effect if this pickup type
                                                        // uses timed mode
                                                        if (hasTimedPickup) {
                                                            add(
                                                                CComment(
                                                                    "Start timed effect if applicable"
                                                                )
                                                            )
                                                            // Use pickup type index to look up
                                                            // duration
                                                            add(
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CArrayAccess(
                                                                            CVar(
                                                                                "_pickup_timer_$sanitizedId"
                                                                            ),
                                                                            CVar("i"),
                                                                        ),
                                                                        "=",
                                                                        CVar(
                                                                            "_pickup_type_$sanitizedId[i]"
                                                                        ),
                                                                    )
                                                                )
                                                            )
                                                        }
                                                        // Start respawn countdown if configured
                                                        if (hasRespawnPickup) {
                                                            add(CComment("Start respawn countdown"))
                                                            add(
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CArrayAccess(
                                                                            CVar(
                                                                                "_pickup_respawn_timer_$sanitizedId"
                                                                            ),
                                                                            CVar("i"),
                                                                        ),
                                                                        "=",
                                                                        CVar(
                                                                            "_pickup_respawn_delay_$sanitizedId[_pickup_type_$sanitizedId[i]]"
                                                                        ),
                                                                    )
                                                                )
                                                            )
                                                        }
                                                    },
                                                elseBody = emptyList(),
                                            )
                                        ),
                                    elseBody = emptyList(),
                                )
                            ),
                    )
                )
            }
        functions.add(
            CFunction(
                name = "pickup_check_collect_$sanitizedId",
                returnType = CVoid,
                params = listOf(CParam("player_x", CU8), CParam("player_y", CU8)),
                body = collectBody,
                sectionComment = "Pickup system: $sanitizedId — AABB collect check",
            )
        )

        // ---- pickup_update_{id}() ----
        // Decrement timed-effect counters each frame. Only emitted when timed pickups exist.
        if (hasTimedPickup) {
            val updateBody =
                buildList<CStatement> {
                    add(CComment("Decrement timed effect counters for active pickups"))
                    add(
                        CFor(
                            init = CVarDecl("i", CU8, CLiteral(0)),
                            condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxTotal)),
                            increment = CUnaryExpr("++", CVar("i")),
                            body =
                                listOf(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CArrayAccess(
                                                    CVar("_pickup_timer_$sanitizedId"),
                                                    CVar("i"),
                                                ),
                                                ">",
                                                CLiteral(0),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_pickup_timer_$sanitizedId"),
                                                            CVar("i"),
                                                        ),
                                                        "-=",
                                                        CLiteral(1),
                                                    )
                                                )
                                            ),
                                        elseBody = emptyList(),
                                    )
                                ),
                        )
                    )
                }
            functions.add(
                CFunction(
                    name = "pickup_update_$sanitizedId",
                    returnType = CVoid,
                    body = updateBody,
                    sectionComment = "Pickup system: $sanitizedId — timed effect update",
                )
            )
        }

        // ---- pickup_respawn_check_{id}() ----
        // Re-spawn pickups once their respawn countdown reaches zero.
        if (hasRespawnPickup) {
            val respawnBody =
                buildList<CStatement> {
                    add(CComment("Re-spawn collected pickups when respawn timer expires"))
                    add(
                        CFor(
                            init = CVarDecl("i", CU8, CLiteral(0)),
                            condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxTotal)),
                            increment = CUnaryExpr("++", CVar("i")),
                            body =
                                listOf(
                                    // Only check slots that are inactive (collected) AND have a
                                    // timer
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_pickup_active_$sanitizedId"),
                                                        CVar("i"),
                                                    ),
                                                    "==",
                                                    CLiteral(0),
                                                ),
                                                "&&",
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_pickup_respawn_timer_$sanitizedId"),
                                                        CVar("i"),
                                                    ),
                                                    ">",
                                                    CLiteral(0),
                                                ),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar(
                                                                "_pickup_respawn_timer_$sanitizedId"
                                                            ),
                                                            CVar("i"),
                                                        ),
                                                        "-=",
                                                        CLiteral(1),
                                                    )
                                                ),
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CArrayAccess(
                                                                CVar(
                                                                    "_pickup_respawn_timer_$sanitizedId"
                                                                ),
                                                                CVar("i"),
                                                            ),
                                                            "==",
                                                            CLiteral(0),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            // Re-activate pickup in same position
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_pickup_active_$sanitizedId"
                                                                        ),
                                                                        CVar("i"),
                                                                    ),
                                                                    "=",
                                                                    CLiteral(1),
                                                                )
                                                            )
                                                        ),
                                                    elseBody = emptyList(),
                                                ),
                                            ),
                                        elseBody = emptyList(),
                                    )
                                ),
                        )
                    )
                }
            functions.add(
                CFunction(
                    name = "pickup_respawn_check_$sanitizedId",
                    returnType = CVoid,
                    body = respawnBody,
                    sectionComment = "Pickup system: $sanitizedId — respawn check",
                )
            )
        }

        return functions
    }

    /**
     * Generate global variable declarations for the pickup system (Plan 06.8-09).
     *
     * Produces state arrays needed by the pickup functions:
     * - `_pickup_active_{id}[maxTotal]` — 0=empty, 1=active, 1 byte per slot
     * - `_pickup_type_{id}[maxTotal]` — pickup type index per slot
     * - `_pickup_x_{id}[maxTotal]` — X position per slot (pixels)
     * - `_pickup_y_{id}[maxTotal]` — Y position per slot (pixels)
     * - `_pickup_count_{id}` — total active pickups counter
     * - `_pickup_timer_{id}[maxTotal]` — timed-effect countdown (only if timed pickups exist)
     * - `_pickup_respawn_timer_{id}[maxTotal]` — respawn countdown (only if respawning pickups
     *   exist)
     *
     * Called from [GBDKPipeline.buildSystemGlobalVars] for `"pickup_system"` GenericSystems.
     *
     * @param system [GenericSystem] carrying `"pickupConfig"` → [PickupSystemConfig].
     * @param sanitizedId System ID with hyphens/spaces replaced by underscores.
     * @return List of [CVarDecl] for inclusion in the global variable section.
     */
    @Suppress("UNCHECKED_CAST")
    fun buildPickupVarDecls(system: GenericSystem, sanitizedId: String): List<CVarDecl> {
        val pickupConfig =
            system.config["pickupConfig"] as? PickupSystemConfig ?: PickupSystemConfig()
        val pickups: List<PickupDef> = pickupConfig.pickups
        val maxTotal = pickupConfig.maxTotalPickups

        val hasTimedPickup = pickups.any { it.effectType == "timed" }
        val hasRespawnPickup = pickups.any { it.respawnFrames > 0 }

        val vars = mutableListOf<CVarDecl>()

        // Core state arrays: active flag, type, position
        vars +=
            CVarDecl(
                name = "_pickup_active_$sanitizedId",
                type = CArray(CU8, maxTotal),
                initializer = CRawExpr("{${(0 until maxTotal).joinToString(", ") { "0" }}}"),
            )
        vars +=
            CVarDecl(
                name = "_pickup_type_$sanitizedId",
                type = CArray(CU8, maxTotal),
                initializer = CRawExpr("{${(0 until maxTotal).joinToString(", ") { "0" }}}"),
            )
        vars +=
            CVarDecl(
                name = "_pickup_x_$sanitizedId",
                type = CArray(CU8, maxTotal),
                initializer = CRawExpr("{${(0 until maxTotal).joinToString(", ") { "0" }}}"),
            )
        vars +=
            CVarDecl(
                name = "_pickup_y_$sanitizedId",
                type = CArray(CU8, maxTotal),
                initializer = CRawExpr("{${(0 until maxTotal).joinToString(", ") { "0" }}}"),
            )
        // Active pickup counter
        vars += CVarDecl(name = "_pickup_count_$sanitizedId", type = CU8, initializer = CLiteral(0))

        // Timed-effect countdown array (only when timed pickups configured)
        if (hasTimedPickup) {
            vars +=
                CVarDecl(
                    name = "_pickup_timer_$sanitizedId",
                    type = CArray(CU8, maxTotal),
                    initializer = CRawExpr("{${(0 until maxTotal).joinToString(", ") { "0" }}}"),
                )
        }

        // Respawn countdown array and per-type delay constants (only when respawning pickups
        // configured)
        if (hasRespawnPickup) {
            vars +=
                CVarDecl(
                    name = "_pickup_respawn_timer_$sanitizedId",
                    type = CArray(CU8, maxTotal),
                    initializer = CRawExpr("{${(0 until maxTotal).joinToString(", ") { "0" }}}"),
                )
            // Per-type respawn delay lookup (indexed by pickup type ordinal)
            if (pickups.isNotEmpty()) {
                val respawnDelays = pickups.joinToString(", ") { it.respawnFrames.toString() }
                vars +=
                    CVarDecl(
                        name = "_pickup_respawn_delay_$sanitizedId",
                        type = CArray(CU8, pickups.size),
                        initializer = CRawExpr("{$respawnDelays}"),
                        isConst = true,
                    )
            }
        }

        return vars
    }
}
