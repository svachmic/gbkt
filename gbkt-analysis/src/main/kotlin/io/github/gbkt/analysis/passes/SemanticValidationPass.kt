/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.DiagnosticCode
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.MusicPlay
import io.github.gbkt.core.ir.MusicStop
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ScriptOp

/**
 * Analysis pass that validates cross-references and structural correctness of the game IR.
 *
 * Checks performed:
 * - Duplicate scene IDs
 * - Duplicate actor IDs
 * - Duplicate variable names
 * - startScene references a valid scene ID
 * - Each scene.actorIds references valid actor IDs
 *
 * Any ERROR diagnostic causes this pass to return [PassResult.Failed]. The pipeline stops
 * immediately and downstream passes do not run.
 */
class SemanticValidationPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val diagnostics = mutableListOf<Diagnostic>()

        val sceneIds = collectDuplicates(game.scenes, "scene", "ID", diagnostics) { it.id }
        val actorIds = collectDuplicates(game.actors, "actor", "ID", diagnostics) { it.id }
        collectDuplicates(game.variables, "variable", "name", diagnostics) { it.name }
        checkStartScene(game, sceneIds, diagnostics)
        checkDanglingActorRefs(game, actorIds, diagnostics)
        checkRawOpUsage(game, diagnostics)
        checkFadeWithoutAudioMixer(game, diagnostics)
        if (game.config.gbcTarget != GbcTarget.DMG) {
            checkGbcPaletteCount(game, diagnostics)
        }

        val errors = diagnostics.filter { it.severity == Severity.ERROR }
        return if (errors.isNotEmpty()) {
            PassResult.Failed(diagnostics)
        } else {
            PassResult.Success(context.withDiagnostics(diagnostics))
        }
    }

    /**
     * Generic duplicate detection: iterates [items], extracts a name via [nameOf], and emits an
     * ANLZ-01 diagnostic for each duplicate. Returns the set of all unique names found.
     *
     * @param entityKind Human-readable entity label (e.g. "scene", "actor", "variable").
     * @param fieldKind Human-readable field label (e.g. "ID", "name").
     */
    private fun <T> collectDuplicates(
        items: List<T>,
        entityKind: String,
        fieldKind: String,
        diagnostics: MutableList<Diagnostic>,
        nameOf: (T) -> String,
    ): Set<String> {
        val seen = mutableSetOf<String>()
        for (item in items) {
            val name = nameOf(item)
            if (!seen.add(name)) {
                diagnostics +=
                    Diagnostic(
                        code = DiagnosticCode.SEMANTIC_INTEGRITY,
                        severity = Severity.ERROR,
                        message =
                            "Duplicate $entityKind $fieldKind '$name' — each $entityKind " +
                                "must have a unique $fieldKind.",
                        location = "$entityKind '$name'",
                        suggestion =
                            "Rename one of the ${entityKind}s with $fieldKind '$name' to a unique value.",
                    )
            }
        }
        return seen
    }

    private fun checkStartScene(
        game: GameIR,
        sceneIds: Set<String>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        if (game.startScene != null && game.startScene !in sceneIds) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.SEMANTIC_INTEGRITY,
                    severity = Severity.ERROR,
                    message =
                        "startScene '${game.startScene}' does not reference a known scene ID.",
                    location = "game.startScene",
                    suggestion =
                        "Set startScene to one of: " +
                            "${sceneIds.joinToString { "'$it'" }.ifEmpty { "(none defined)" }}.",
                )
        }
    }

    private fun checkDanglingActorRefs(
        game: GameIR,
        actorIds: Set<String>,
        diagnostics: MutableList<Diagnostic>,
    ) {
        for (scene in game.scenes) {
            for (actorId in scene.actorIds) {
                if (actorId !in actorIds) {
                    diagnostics +=
                        Diagnostic(
                            code = DiagnosticCode.SEMANTIC_INTEGRITY,
                            severity = Severity.ERROR,
                            message =
                                "Scene '${scene.id}' references actor '$actorId' which does " +
                                    "not exist.",
                            location = "scene '${scene.id}' actorIds",
                            suggestion =
                                "Remove '$actorId' from scene '${scene.id}'.actorIds or define " +
                                    "an actor with that ID.",
                        )
                }
            }
        }
    }

    /**
     * Counts [RawOp] instances across all ScriptOps in all scenes and emits a WARNING if any are
     * found. raw() calls bypass type safety and the DSL — they indicate patterns the DSL should
     * ideally support natively.
     *
     * Counts ops in enter, frame, and exit handlers for all scenes, as well as nested control flow.
     */
    private fun checkRawOpUsage(game: GameIR, diagnostics: MutableList<Diagnostic>) {
        var count = 0
        val locations = mutableListOf<String>()

        for (scene in game.scenes) {
            val sceneRaw =
                countRawOps(scene.enterOps) +
                    countRawOps(scene.frameOps) +
                    countRawOps(scene.exitOps)
            if (sceneRaw > 0) {
                count += sceneRaw
                locations +=
                    "scene '${scene.id}' ($sceneRaw raw call${if (sceneRaw > 1) "s" else ""})"
            }
        }

        if (count > 0) {
            val locationStr =
                if (locations.isNotEmpty()) " (${locations.joinToString(", ")})" else ""
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RAM_CAPACITY,
                    severity = Severity.WARNING,
                    message =
                        "$count raw() call${if (count > 1) "s" else ""} found — " +
                            "consider adding DSL support for these patterns$locationStr",
                    location = "game '${game.name}'",
                    suggestion =
                        "Replace raw() calls with typed DSL constructs when possible. " +
                            "raw() bypasses type checking and source map tracking.",
                )
        }
    }

    /** Counts [RawOp] instances within a list of [ScriptOp]s (including nested ops). */
    private fun countRawOps(ops: List<ScriptOp>): Int = collectAllOps(ops).count { it is RawOp }

    /**
     * Validates GBC hardware palette limits.
     *
     * GBC hardware provides 8 background palettes and 8 sprite palettes. Exceeding these limits
     * means the game cannot load all defined palettes at runtime.
     *
     * Only enforced when [GameIR.config.gbcTarget] != [GbcTarget.DMG] (palette limits are
     * GBC-only).
     */
    private fun checkGbcPaletteCount(game: GameIR, diagnostics: MutableList<Diagnostic>) {
        val bgCount = game.palettes.count { it.type == PaletteType.BACKGROUND }
        val spriteCount = game.palettes.count { it.type == PaletteType.SPRITE }

        if (bgCount > 8) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.GBC_PALETTE_LIMIT,
                    severity = Severity.ERROR,
                    message =
                        "GBC hardware limit: game uses $bgCount BACKGROUND palettes but max is 8.",
                    location = "game.palettes (BACKGROUND)",
                    suggestion =
                        "Reduce BACKGROUND palette count to 8 or fewer. " +
                            "Consider reusing palettes across scenes with compatible tile graphics.",
                )
        }

        if (spriteCount > 8) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.GBC_PALETTE_LIMIT,
                    severity = Severity.ERROR,
                    message =
                        "GBC hardware limit: game uses $spriteCount SPRITE palettes but max is 8.",
                    location = "game.palettes (SPRITE)",
                    suggestion =
                        "Reduce SPRITE palette count to 8 or fewer. " +
                            "Combine actors that share the same color range into a single palette.",
                )
        }
    }

    /**
     * Warns if any MusicPlay or MusicStop uses fade frames but no AudioMixer system is configured.
     *
     * Without `audioMixer { }`, the `fade_group()` C function won't exist — codegen falls back to
     * instant play/stop and emits a comment. This validation surfaces the issue at analysis time.
     */
    private fun checkFadeWithoutAudioMixer(game: GameIR, diagnostics: MutableList<Diagnostic>) {
        val hasAudioMixer =
            game.systems.any { it is GenericSystem && it.config["type"] == "audio_mixer" }
        if (hasAudioMixer) return

        val allOps = collectAllOps(collectAllTopLevelOps(game))

        for (op in allOps) {
            if (op is MusicPlay && op.fadeInFrames > 0) {
                diagnostics +=
                    Diagnostic(
                        code = DiagnosticCode.AUDIO_FADE_UNSUPPORTED,
                        severity = Severity.WARNING,
                        message =
                            "MusicPlay for '${op.songId}' requests fadeIn=${op.fadeInFrames} " +
                                "but no audioMixer {} is configured — falling back to instant play",
                        location = "scene ops",
                        suggestion = "Add audioMixer { } to your game {} block to enable fade.",
                    )
            }
            if (op is MusicStop && op.fadeOutFrames > 0) {
                diagnostics +=
                    Diagnostic(
                        code = DiagnosticCode.AUDIO_FADE_UNSUPPORTED,
                        severity = Severity.WARNING,
                        message =
                            "MusicStop requests fadeOut=${op.fadeOutFrames} " +
                                "but no audioMixer {} is configured — falling back to instant stop",
                        location = "scene ops",
                        suggestion = "Add audioMixer { } to your game {} block to enable fade.",
                    )
            }
        }
    }

    /**
     * Walks every GameIR subsystem and returns the flat list of top-level [ScriptOp]s. Used by
     * [checkFadeWithoutAudioMixer] (and a candidate caller for any future cross-subsystem op walk).
     * Delegates to focused per-category helpers to keep individual method complexity below the
     * S3776 threshold.
     */
    private fun collectAllTopLevelOps(game: GameIR): List<ScriptOp> =
        collectSceneOps(game) +
            collectZoneOps(game) +
            collectCollisionRuleOps(game) +
            collectActorPoolOps(game) +
            collectMenuOps(game) +
            collectPuzzleObjectOps(game) +
            collectSystemOps(game)

    private fun collectSceneOps(game: GameIR): List<ScriptOp> = buildList {
        for (scene in game.scenes) {
            addAll(scene.enterOps)
            addAll(scene.frameOps)
            addAll(scene.exitOps)
        }
    }

    private fun collectZoneOps(game: GameIR): List<ScriptOp> = buildList {
        for (zone in game.zones) {
            addAll(zone.onEnter)
            addAll(zone.onExit)
            for (obj in zone.objects) {
                addAll(obj.onInteract)
            }
        }
    }

    private fun collectCollisionRuleOps(game: GameIR): List<ScriptOp> = buildList {
        for (rule in game.collisionRules) {
            addAll(rule.onCollide)
        }
    }

    private fun collectActorPoolOps(game: GameIR): List<ScriptOp> = buildList {
        for (pool in game.actorPools) {
            addAll(pool.deathCallback)
        }
    }

    private fun collectMenuOps(game: GameIR): List<ScriptOp> = buildList {
        for (menu in game.menus) {
            for (item in menu.items) {
                addAll(item.body)
            }
        }
    }

    private fun collectPuzzleObjectOps(game: GameIR): List<ScriptOp> = buildList {
        for (puzzleObj in game.puzzleObjects) {
            for (h in puzzleObj.handlers) {
                addAll(h.actions)
            }
        }
    }

    private fun collectSystemOps(game: GameIR): List<ScriptOp> = buildList {
        for (system in game.systems) {
            when (system) {
                is ExplorationSystem -> addAll(collectExplorationSystemOps(system))
                is CombatEngineSystem -> addAll(collectCombatSystemOps(system))
                else -> Unit
            }
        }
    }

    private fun collectExplorationSystemOps(system: ExplorationSystem): List<ScriptOp> = buildList {
        addAll(system.stepStatements)
        addAll(system.blockedStatements)
        addAll(system.interactStatements)
        for (g in system.gauges) {
            addAll(g.onLowStatements)
            addAll(g.onDepletedStatements)
        }
    }

    private fun collectCombatSystemOps(system: CombatEngineSystem): List<ScriptOp> = buildList {
        addAll(system.onVictoryCondition)
        addAll(system.onDefeatCondition)
        addAll(system.onVictoryOps)
        addAll(system.onDefeatOps)
        for ((_, ops) in system.combatHooks) {
            addAll(ops)
        }
    }
}
