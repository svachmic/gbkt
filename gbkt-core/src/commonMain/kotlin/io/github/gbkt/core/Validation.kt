/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRAnimationPause
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRAnimationQueue
import io.github.gbkt.core.ir.IRAnimationResume
import io.github.gbkt.core.ir.IRAnimationSetFrame
import io.github.gbkt.core.ir.IRAnimationSetSpeed
import io.github.gbkt.core.ir.IRAnimationStop
import io.github.gbkt.core.ir.IRArrayAccess
import io.github.gbkt.core.ir.IRArrayAssign
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.IRDialogSay
import io.github.gbkt.core.ir.IRDialogShow
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRMenuCancel
import io.github.gbkt.core.ir.IRMenuMoveTo
import io.github.gbkt.core.ir.IRMenuOpen
import io.github.gbkt.core.ir.IRMenuSelect
import io.github.gbkt.core.ir.IRMenuShow
import io.github.gbkt.core.ir.IRMenuTick
import io.github.gbkt.core.ir.IRPoolDespawn
import io.github.gbkt.core.ir.IRPoolDespawnAll
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRPoolUpdate
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRTween
import io.github.gbkt.core.ir.IRUnary
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile
import io.github.gbkt.core.ir.PaletteType

// =============================================================================
// GAME VALIDATION
// =============================================================================

/** Validation result containing all errors and warnings found. */
data class ValidationResult(
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()

    fun throwIfInvalid() {
        if (errors.isNotEmpty()) {
            val message = buildString {
                appendLine("Game validation failed with ${errors.size} error(s):")
                errors.forEachIndexed { i, error ->
                    appendLine("  ${i + 1}. [${error.category}] ${error.message}")
                }
                if (warnings.isNotEmpty()) {
                    appendLine()
                    appendLine("Additionally, ${warnings.size} warning(s):")
                    warnings.forEachIndexed { i, warning ->
                        appendLine("  ${i + 1}. [${warning.category}] ${warning.message}")
                    }
                }
            }
            throw ValidationException(message, errors, warnings)
        }
    }
}

data class ValidationError(val category: ValidationCategory, val message: String)

data class ValidationWarning(val category: ValidationCategory, val message: String)

enum class ValidationCategory {
    OAM_LIMIT,
    PALETTE_LIMIT,
    SPRITE_REFERENCE,
    ANIMATION_REFERENCE,
    SCENE_REFERENCE,
    STATE_MACHINE,
    MEMORY_BUDGET,
    ASSET_FILE,
    DUPLICATE_NAME,
    GBC_COLOR,
    TWEEN,
    POOL_REFERENCE,
    MENU_REFERENCE,
    DIALOG_REFERENCE,
    ARRAY_BOUNDS,
    PHYSICS,
}

class ValidationException(
    message: String,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>,
) : RuntimeException(message)

/** Validates a game definition for common issues. */
class GameValidator(private val game: Game) {
    private val errors = mutableListOf<ValidationError>()
    private val warnings = mutableListOf<ValidationWarning>()

    companion object {
        const val MAX_OAM_SPRITES = 40
        const val MAX_SPRITE_PALETTES = 8
        const val MAX_BKG_PALETTES = 8
        const val MAX_TILES_PER_BANK = 256
        const val VRAM_SIZE = 8192
        // Game Boy has 8KB WRAM, but ~2KB is used by GBDK/stack, leaving ~6KB for user data
        const val AVAILABLE_WRAM = 6144
        const val WRAM_WARNING_THRESHOLD = 5120 // Warn at ~83% usage
    }

    fun validate(): ValidationResult {
        validateOAMLimit()
        validatePaletteLimits()
        validateSpriteReferences()
        validateAnimationReferences()
        validateSceneReferences()
        validateStateMachines()
        validateMemoryEstimates()
        validateDuplicateNames()
        validateGBCColors()
        validateAssetFiles()
        validateTweens()
        validateIRReferences()
        validateArrayBounds()
        validatePhysics()

        return ValidationResult(errors.toList(), warnings.toList())
    }

    /** Validate OAM sprite limit (40 hardware sprites max). */
    private fun validateOAMLimit() {
        // Count sprites from direct definitions
        var totalSprites = game.sprites.size

        // Count sprites from entities
        val entitySprites = game.entities.count { it.sprite != null }
        totalSprites += entitySprites

        // Count from pools (worst case: all slots used)
        val poolSprites = game.pools.sumOf { it.size }

        // Count from particle systems (each particle is a sprite)
        val particleSprites = game.particleSystems.sumOf { it.count }

        val worstCaseTotal = totalSprites + poolSprites + particleSprites

        // Check for exact overflow
        if (worstCaseTotal > MAX_OAM_SPRITES) {
            errors.add(
                ValidationError(
                    ValidationCategory.OAM_LIMIT,
                    "Game exceeds OAM limit: $worstCaseTotal sprites possible (max $MAX_OAM_SPRITES). " +
                        "Breakdown: Direct sprites: ${game.sprites.size}, Entity sprites: $entitySprites, " +
                        "Pool slots: $poolSprites, Particle sprites: $particleSprites",
                )
            )
        } else if (worstCaseTotal == MAX_OAM_SPRITES) {
            warnings.add(
                ValidationWarning(
                    ValidationCategory.OAM_LIMIT,
                    "Game is at OAM limit: $worstCaseTotal sprites (max $MAX_OAM_SPRITES). " +
                        "Any additional sprites will cause overflow.",
                )
            )
        } else if (worstCaseTotal > MAX_OAM_SPRITES - 5) {
            warnings.add(
                ValidationWarning(
                    ValidationCategory.OAM_LIMIT,
                    "Game is close to OAM limit: $worstCaseTotal sprites possible (max $MAX_OAM_SPRITES)",
                )
            )
        }

        // Check individual pools for overflow
        game.pools.forEach { pool ->
            if (pool.size > MAX_OAM_SPRITES) {
                errors.add(
                    ValidationError(
                        ValidationCategory.OAM_LIMIT,
                        "Pool '${pool.name}' has ${pool.size} sprites, which exceeds OAM limit of $MAX_OAM_SPRITES",
                    )
                )
            }
        }

        // Check if direct sprites alone exceed limit
        if (game.sprites.size > MAX_OAM_SPRITES) {
            errors.add(
                ValidationError(
                    ValidationCategory.OAM_LIMIT,
                    "Direct sprite count (${game.sprites.size}) exceeds OAM limit of $MAX_OAM_SPRITES",
                )
            )
        }
    }

    /** Validate palette slot limits. */
    private fun validatePaletteLimits() {
        val spritePalettes = game.palettes.filter { it.type == PaletteType.SPRITE }
        val bkgPalettes = game.palettes.filter { it.type == PaletteType.BACKGROUND }

        if (spritePalettes.size > MAX_SPRITE_PALETTES) {
            errors.add(
                ValidationError(
                    ValidationCategory.PALETTE_LIMIT,
                    "Too many sprite palettes: ${spritePalettes.size} (max $MAX_SPRITE_PALETTES)",
                )
            )
        }

        if (bkgPalettes.size > MAX_BKG_PALETTES) {
            errors.add(
                ValidationError(
                    ValidationCategory.PALETTE_LIMIT,
                    "Too many background palettes: ${bkgPalettes.size} (max $MAX_BKG_PALETTES)",
                )
            )
        }

        // Check for duplicate slot assignments
        val spriteSlots = spritePalettes.map { it.slot }.filter { it >= 0 }
        val duplicateSpriteSlots = spriteSlots.groupBy { it }.filter { it.value.size > 1 }
        if (duplicateSpriteSlots.isNotEmpty()) {
            errors.add(
                ValidationError(
                    ValidationCategory.PALETTE_LIMIT,
                    "Duplicate sprite palette slots: ${duplicateSpriteSlots.keys.joinToString()}",
                )
            )
        }

        val bkgSlots = bkgPalettes.map { it.slot }.filter { it >= 0 }
        val duplicateBkgSlots = bkgSlots.groupBy { it }.filter { it.value.size > 1 }
        if (duplicateBkgSlots.isNotEmpty()) {
            errors.add(
                ValidationError(
                    ValidationCategory.PALETTE_LIMIT,
                    "Duplicate background palette slots: ${duplicateBkgSlots.keys.joinToString()}",
                )
            )
        }
    }

    /** Validate sprite references in scenes and entities. */
    private fun validateSpriteReferences() {
        val knownSprites = game.sprites.map { it.name }.toSet()
        val entitySprites = game.entities.mapNotNull { it.sprite?.name }.toSet()
        val allSprites = knownSprites + entitySprites

        // Check animation references in state machines
        game.stateMachines.forEach { machine ->
            machine.states.forEach { (stateName, state) ->
                state.animation?.let { anim ->
                    if (anim.spriteName !in allSprites) {
                        val suggestion = Suggestions.formatSuggestion(anim.spriteName, allSprites)
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "State '${machine.name}::$stateName' references unknown sprite '${anim.spriteName}'.$suggestion " +
                                    "Available: ${allSprites.joinToString()}",
                            )
                        )
                    }
                }
            }
        }
    }

    /** Validate animation references. */
    private fun validateAnimationReferences() {
        // Build map of sprite -> animations
        val spriteAnimations = mutableMapOf<String, Set<String>>()
        game.sprites.forEach { sprite ->
            if (sprite.hasAnimations) {
                spriteAnimations[sprite.name] = sprite.animations.keys
            }
        }

        // Check state machine animation references
        game.stateMachines.forEach { machine ->
            machine.states.forEach { (stateName, state) ->
                state.animation?.let { anim ->
                    val animations = spriteAnimations[anim.spriteName]
                    if (animations != null && anim.animationName !in animations) {
                        val suggestion =
                            Suggestions.formatSuggestion(anim.animationName, animations)
                        errors.add(
                            ValidationError(
                                ValidationCategory.ANIMATION_REFERENCE,
                                "State '${machine.name}::$stateName' references unknown animation '${anim.animationName}' " +
                                    "on sprite '${anim.spriteName}'.$suggestion Available: ${animations.joinToString()}",
                            )
                        )
                    }
                }
            }
        }
    }

    /** Validate scene references. */
    private fun validateSceneReferences() {
        val knownScenes = game.scenes.keys

        // Validate start scene
        if (game.startScene !in knownScenes) {
            val suggestion = Suggestions.formatSuggestion(game.startScene, knownScenes)
            errors.add(
                ValidationError(
                    ValidationCategory.SCENE_REFERENCE,
                    "Start scene '${game.startScene}' not found.$suggestion Available: ${knownScenes.joinToString()}",
                )
            )
        }

        // Scan IR statements for scene transitions and validate them
        val referencedScenes = mutableSetOf<SceneReference>()

        // Scan all scenes
        for ((sceneName, scene) in game.scenes) {
            collectSceneReferences(scene.onEnter, sceneName, "enter", referencedScenes)
            collectSceneReferences(scene.onFrame, sceneName, "frame", referencedScenes)
            collectSceneReferences(scene.onExit, sceneName, "exit", referencedScenes)
        }

        // Scan state machines
        for (machine in game.stateMachines) {
            for ((stateName, state) in machine.states) {
                val context = "state machine '${machine.name}::$stateName'"
                collectSceneReferences(state.onEnter, context, "onEnter", referencedScenes)
                collectSceneReferences(state.onTick, context, "onTick", referencedScenes)
                collectSceneReferences(state.onExit, context, "onExit", referencedScenes)
            }
        }

        // Scan pools
        for (pool in game.pools) {
            collectSceneReferences(
                pool.onFrameStatements,
                "pool '${pool.name}'",
                "onFrame",
                referencedScenes,
            )
        }

        // Validate all found scene references
        for (ref in referencedScenes) {
            if (ref.targetScene !in knownScenes) {
                val suggestion = Suggestions.formatSuggestion(ref.targetScene, knownScenes)
                errors.add(
                    ValidationError(
                        ValidationCategory.SCENE_REFERENCE,
                        "Scene transition to '${ref.targetScene}' in ${ref.sourceContext} (${ref.block}) " +
                            "references unknown scene.$suggestion Available: ${knownScenes.joinToString()}",
                    )
                )
            }
        }
    }

    /** Data class to track scene references with source context. */
    private data class SceneReference(
        val targetScene: String,
        val sourceContext: String,
        val block: String,
    )

    /** Recursively collect scene references from IR statements. */
    private fun collectSceneReferences(
        statements: List<IRStatement>,
        sourceContext: String,
        block: String,
        refs: MutableSet<SceneReference>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is IRSceneChange -> {
                    refs.add(SceneReference(stmt.sceneName, sourceContext, block))
                }
                is IRComposedTransition -> {
                    stmt.targetScene?.let { target ->
                        refs.add(SceneReference(target, sourceContext, block))
                    }
                }
                is IRIf -> {
                    collectSceneReferences(stmt.then, sourceContext, block, refs)
                    stmt.otherwise?.let { collectSceneReferences(it, sourceContext, block, refs) }
                }
                is IRWhen -> {
                    for (branch in stmt.branches) {
                        collectSceneReferences(branch.body, sourceContext, block, refs)
                    }
                    stmt.otherwise?.let { collectSceneReferences(it, sourceContext, block, refs) }
                }
                is IRWhile -> {
                    collectSceneReferences(stmt.body, sourceContext, block, refs)
                }
                is IRFor -> {
                    collectSceneReferences(stmt.body, sourceContext, block, refs)
                }
                is IRPoolForEach -> {
                    collectSceneReferences(stmt.bodyStatements, sourceContext, block, refs)
                }
                is IRPoolSpawn -> {
                    collectSceneReferences(stmt.initStatements, sourceContext, block, refs)
                }
                is IRPoolSpawnAt -> {
                    collectSceneReferences(stmt.initStatements, sourceContext, block, refs)
                }
                is IRPoolTrySpawn -> {
                    collectSceneReferences(stmt.initStatements, sourceContext, block, refs)
                    collectSceneReferences(stmt.elseStatements, sourceContext, block, refs)
                }
                is IRTransitionFadeOut -> {
                    collectSceneReferences(stmt.onComplete, sourceContext, block, refs)
                }
                is IRTransitionFadeIn -> {
                    collectSceneReferences(stmt.onComplete, sourceContext, block, refs)
                }
                is IRTransitionWipe -> {
                    collectSceneReferences(stmt.onComplete, sourceContext, block, refs)
                }
                is IRTransitionIris -> {
                    collectSceneReferences(stmt.onComplete, sourceContext, block, refs)
                }
                else -> {
                    // No nested statements to process
                }
            }
        }
    }

    /** Validate state machine definitions. */
    private fun validateStateMachines() {
        game.stateMachines.forEach { machine ->
            if (machine.states.isEmpty()) {
                errors.add(
                    ValidationError(
                        ValidationCategory.STATE_MACHINE,
                        "State machine '${machine.name}' has no states defined",
                    )
                )
            }

            // Check for unreachable states (no transitions leading to them)
            val reachableStates = mutableSetOf<String>()
            machine.defaultState?.let { reachableStates.add(it) }

            machine.states.forEach { (_, state) ->
                state.transitions.forEach { transition ->
                    reachableStates.add(transition.targetState)
                }
            }

            val unreachable = machine.states.keys - reachableStates
            if (unreachable.isNotEmpty()) {
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.STATE_MACHINE,
                        "State machine '${machine.name}' has potentially unreachable states: ${unreachable.joinToString()}",
                    )
                )
            }

            // Check for states that reference non-existent targets
            machine.states.forEach { (stateName, state) ->
                state.transitions.forEach { transition ->
                    if (transition.targetState !in machine.states) {
                        val suggestion =
                            Suggestions.formatSuggestion(
                                transition.targetState,
                                machine.states.keys,
                            )
                        errors.add(
                            ValidationError(
                                ValidationCategory.STATE_MACHINE,
                                "State '${machine.name}::$stateName' transitions to unknown state '${transition.targetState}'.$suggestion " +
                                    "Available: ${machine.states.keys.joinToString()}",
                            )
                        )
                    }
                }
            }
        }
    }

    /** Estimate memory usage and warn if approaching limits. */
    private fun validateMemoryEstimates() {
        validateVRAMUsage()
        validateWRAMUsage()
    }

    /** Estimate VRAM tile usage. */
    private fun validateVRAMUsage() {
        var estimatedTiles = 0

        game.sprites.forEach { sprite ->
            // Each 8x8 tile uses 16 bytes (2bpp), 8x16 uses 32 bytes
            val tilesPerSprite =
                when {
                    sprite.width == 8 && sprite.height == 8 -> 1
                    sprite.width == 8 && sprite.height == 16 -> 2
                    sprite.width == 16 && sprite.height == 16 -> 4
                    else -> (sprite.width / 8) * (sprite.height / 8)
                }

            // For animated sprites, multiply by frame count
            val frameCount =
                if (sprite.hasAnimations) {
                    sprite.animations.values.maxOfOrNull { it.frameCount } ?: 1
                } else 1

            estimatedTiles += tilesPerSprite * frameCount
        }

        if (estimatedTiles > MAX_TILES_PER_BANK) {
            warnings.add(
                ValidationWarning(
                    ValidationCategory.MEMORY_BUDGET,
                    "Estimated $estimatedTiles sprite tiles may exceed single VRAM bank ($MAX_TILES_PER_BANK tiles). " +
                        "Consider using tile banking or reducing sprite complexity.",
                )
            )
        }
    }

    /** Estimate WRAM (RAM) usage and fail if exceeded. */
    private fun validateWRAMUsage() {
        val breakdown = mutableListOf<String>()
        var totalBytes = 0

        // Global variables
        val varBytes =
            game.variables.sumOf { v ->
                when (v.type) {
                    GBVar.VarType.U8,
                    GBVar.VarType.I8 -> 1
                    GBVar.VarType.U16,
                    GBVar.VarType.I16 -> 2
                }
            }
        if (varBytes > 0) {
            breakdown.add("Variables: $varBytes bytes (${game.variables.size} vars)")
            totalBytes += varBytes
        }

        // Entity state (position = 2 bytes each for x,y, plus sprite OAM index)
        val entityBytes =
            game.entities.sumOf { entity ->
                var bytes = 0
                if (entity.position != null) bytes += 4 // x (2) + y (2)
                if (entity.sprite != null) bytes += 1 // OAM slot index
                bytes
            }
        if (entityBytes > 0) {
            breakdown.add("Entities: $entityBytes bytes (${game.entities.size} entities)")
            totalBytes += entityBytes
        }

        // Pool allocations: size × (state fields + internal overhead)
        val poolBytes =
            game.pools.sumOf { pool ->
                // Each pool instance needs: active flag (1) + state fields
                val stateFieldBytes =
                    pool.stateFields.sumOf { field ->
                        when (field.type) {
                            GBVar.VarType.U8,
                            GBVar.VarType.I8 -> 1
                            GBVar.VarType.U16,
                            GBVar.VarType.I16 -> 2
                        }
                    }
                val perInstanceBytes = 1 + stateFieldBytes // 1 byte for active flag
                val overhead = 2 // pool count variable
                pool.size * perInstanceBytes + overhead
            }
        if (poolBytes > 0) {
            val poolDetails = game.pools.joinToString(", ") { "${it.name}(${it.size})" }
            breakdown.add("Pools: $poolBytes bytes ($poolDetails)")
            totalBytes += poolBytes
        }

        // Save data RAM buffer (used during gameplay before saving to SRAM)
        game.saveData?.let { save ->
            val saveBytes = save.dataSize // Just the data fields, not persisted to WRAM
            if (saveBytes > 0) {
                breakdown.add("Save buffer: $saveBytes bytes")
                totalBytes += saveBytes
            }
        }

        // State machines (current state index per machine)
        val stateMachineBytes = game.stateMachines.size * 1 // 1 byte per machine for state index
        if (stateMachineBytes > 0) {
            breakdown.add(
                "State machines: $stateMachineBytes bytes (${game.stateMachines.size} machines)"
            )
            totalBytes += stateMachineBytes
        }

        // Camera state (if defined)
        if (game.camera != null) {
            val cameraBytes = 8 // x, y (4 bytes) + target + smoothing state
            breakdown.add("Camera: $cameraBytes bytes")
            totalBytes += cameraBytes
        }

        // Dialog state (per dialog)
        val dialogBytes = game.dialogs.size * 8 // visible flag, current line, char index, etc.
        if (dialogBytes > 0) {
            breakdown.add("Dialogs: $dialogBytes bytes (${game.dialogs.size} dialogs)")
            totalBytes += dialogBytes
        }

        // Menu state (per menu)
        val menuBytes = game.menus.size * 6 // visible, active, cursor position, etc.
        if (menuBytes > 0) {
            breakdown.add("Menus: $menuBytes bytes (${game.menus.size} menus)")
            totalBytes += menuBytes
        }

        // Navigation grids
        val navGridBytes =
            game.navGrids.sumOf { grid ->
                (grid.width * grid.height + 7) / 8 // 1 bit per cell, packed into bytes
            }
        if (navGridBytes > 0) {
            breakdown.add("Nav grids: $navGridBytes bytes (${game.navGrids.size} grids)")
            totalBytes += navGridBytes
        }

        // Scene management overhead
        val sceneBytes = 4 // current scene, next scene, changed flag, frame counter
        breakdown.add("Scene management: $sceneBytes bytes")
        totalBytes += sceneBytes

        // Check limits
        if (totalBytes > AVAILABLE_WRAM) {
            errors.add(
                ValidationError(
                    ValidationCategory.MEMORY_BUDGET,
                    "Estimated WRAM usage ($totalBytes bytes) exceeds available RAM ($AVAILABLE_WRAM bytes). " +
                        "Breakdown: ${breakdown.joinToString("; ")}",
                )
            )
        } else if (totalBytes > WRAM_WARNING_THRESHOLD) {
            warnings.add(
                ValidationWarning(
                    ValidationCategory.MEMORY_BUDGET,
                    "Estimated WRAM usage ($totalBytes bytes) is approaching limit ($AVAILABLE_WRAM bytes). " +
                        "Breakdown: ${breakdown.joinToString("; ")}",
                )
            )
        }
    }

    /** Validate for duplicate variable and scene names. */
    private fun validateDuplicateNames() {
        // Check for duplicate variable names
        val variableNames = game.variables.map { it.name }
        val duplicateVariables = variableNames.groupBy { it }.filter { it.value.size > 1 }
        if (duplicateVariables.isNotEmpty()) {
            duplicateVariables.forEach { (name, occurrences) ->
                errors.add(
                    ValidationError(
                        ValidationCategory.DUPLICATE_NAME,
                        "Duplicate variable name '$name' found ${occurrences.size} times. Variable names must be unique.",
                    )
                )
            }
        }

        // Check for duplicate scene names
        val sceneNames = game.scenes.keys.toList()
        val duplicateScenes = sceneNames.groupBy { it }.filter { it.value.size > 1 }
        if (duplicateScenes.isNotEmpty()) {
            duplicateScenes.forEach { (name, occurrences) ->
                errors.add(
                    ValidationError(
                        ValidationCategory.DUPLICATE_NAME,
                        "Duplicate scene name '$name' found ${occurrences.size} times. Scene names must be unique.",
                    )
                )
            }
        }

        // Check for duplicate sprite names
        val spriteNames = game.sprites.map { it.name }
        val duplicateSprites = spriteNames.groupBy { it }.filter { it.value.size > 1 }
        if (duplicateSprites.isNotEmpty()) {
            duplicateSprites.forEach { (name, occurrences) ->
                errors.add(
                    ValidationError(
                        ValidationCategory.DUPLICATE_NAME,
                        "Duplicate sprite name '$name' found ${occurrences.size} times. Sprite names must be unique.",
                    )
                )
            }
        }
    }

    /** Validate GBC color values in palettes. */
    private fun validateGBCColors() {
        game.palettes.forEach { palette ->
            palette.colors.forEachIndexed { index, color ->
                // Check RGB555 range (0-32767)
                if (color.rgb555 < 0 || color.rgb555 > 0x7FFF) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.GBC_COLOR,
                            "Invalid GBC color value in palette '${palette.name}' at index $index: ${color.rgb555} " +
                                "(must be 0-32767)",
                        )
                    )
                }

                // Check individual RGB components (0-31 each)
                val r = color.red
                val g = color.green
                val b = color.blue

                if (r < 0 || r > 31 || g < 0 || g > 31 || b < 0 || b > 31) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.GBC_COLOR,
                            "Invalid GBC color component in palette '${palette.name}' at index $index: " +
                                "R=$r, G=$g, B=$b (each must be 0-31)",
                        )
                    )
                }
            }

            // Check palette has exactly 4 colors
            if (palette.colors.size != 4) {
                errors.add(
                    ValidationError(
                        ValidationCategory.GBC_COLOR,
                        "Palette '${palette.name}' must have exactly 4 colors, got ${palette.colors.size}",
                    )
                )
            }
        }
    }

    /** Validate asset files (PNG corruption, dimensions). */
    private fun validateAssetFiles() {
        // Only validate on JVM platform where AssetValidator is available
        if (game.assetDir == null) {
            return
        }

        // Validate sprite assets
        game.sprites.forEach { sprite ->
            val assetPath = sprite.asset
            if (assetPath.isNotEmpty()) {
                val validationResult = validateAssetFile(assetPath, game.assetDir)
                if (!validationResult.isValid) {
                    validationResult.errors.forEach { error ->
                        errors.add(
                            ValidationError(
                                ValidationCategory.ASSET_FILE,
                                "Asset '${sprite.name}' ($assetPath): $error",
                            )
                        )
                    }
                }
            }
        }
    }

    /** Validate tween parameters for duration and value bounds. */
    private fun validateTweens() {
        val tweens = mutableListOf<TweenInfo>()

        // Collect tweens from all scenes
        for ((sceneName, scene) in game.scenes) {
            collectTweens(scene.onEnter, "scene '$sceneName' enter", tweens)
            collectTweens(scene.onFrame, "scene '$sceneName' frame", tweens)
            collectTweens(scene.onExit, "scene '$sceneName' exit", tweens)
        }

        // Collect tweens from state machines
        for (machine in game.stateMachines) {
            for ((stateName, state) in machine.states) {
                val context = "state machine '${machine.name}::$stateName'"
                collectTweens(state.onEnter, "$context onEnter", tweens)
                collectTweens(state.onTick, "$context onTick", tweens)
                collectTweens(state.onExit, "$context onExit", tweens)
            }
        }

        // Collect tweens from pools
        for (pool in game.pools) {
            collectTweens(pool.onFrameStatements, "pool '${pool.name}'", tweens)
        }

        // Validate each tween
        for (info in tweens) {
            val tween = info.tween

            // Validate duration > 0
            if (tween.duration <= 0) {
                errors.add(
                    ValidationError(
                        ValidationCategory.TWEEN,
                        "Tween for '${tween.target}' in ${info.context} has invalid duration: " +
                            "${tween.duration}. Duration must be > 0.",
                    )
                )
            }

            // Get type bounds
            val (minValue, maxValue) =
                when (tween.targetType) {
                    GBVar.VarType.U8 -> 0 to 255
                    GBVar.VarType.U16 -> 0 to 65535
                    GBVar.VarType.I8 -> -128 to 127
                    GBVar.VarType.I16 -> -32768 to 32767
                }

            // Check 'from' value if it's a literal
            val fromValue = extractLiteralValue(tween.from)
            if (fromValue != null) {
                if (fromValue < minValue || fromValue > maxValue) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.TWEEN,
                            "Tween for '${tween.target}' in ${info.context} has 'from' value $fromValue " +
                                "outside ${tween.targetType.name} bounds ($minValue to $maxValue).",
                        )
                    )
                }
            }

            // Check 'to' value if it's a literal
            val toValue = extractLiteralValue(tween.to)
            if (toValue != null) {
                if (toValue < minValue || toValue > maxValue) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.TWEEN,
                            "Tween for '${tween.target}' in ${info.context} has 'to' value $toValue " +
                                "outside ${tween.targetType.name} bounds ($minValue to $maxValue).",
                        )
                    )
                }
            }

            // Warn about potential overflow for U8 with large ranges
            if (fromValue != null && toValue != null && tween.targetType == GBVar.VarType.U8) {
                val range = kotlin.math.abs(toValue - fromValue)
                if (range > 200) {
                    warnings.add(
                        ValidationWarning(
                            ValidationCategory.TWEEN,
                            "Tween for '${tween.target}' in ${info.context} has large value range " +
                                "($fromValue to $toValue). Consider using U16 for smoother interpolation.",
                        )
                    )
                }
            }
        }
    }

    /** Data class to track tween info with source context. */
    private data class TweenInfo(val tween: IRTween, val context: String)

    /** Extract an integer value from an IRExpression if it's a literal. */
    private fun extractLiteralValue(expr: IRExpression): Int? {
        return when (expr) {
            is IRLiteral -> {
                when (val value = expr.value) {
                    is Int -> value
                    is Long -> value.toInt()
                    is Short -> value.toInt()
                    is Byte -> value.toInt()
                    else -> null
                }
            }
            else -> null
        }
    }

    /** Recursively collect tweens from IR statements. */
    private fun collectTweens(
        statements: List<IRStatement>,
        context: String,
        tweens: MutableList<TweenInfo>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is IRTween -> {
                    tweens.add(TweenInfo(stmt, context))
                }
                is IRIf -> {
                    collectTweens(stmt.then, context, tweens)
                    stmt.otherwise?.let { collectTweens(it, context, tweens) }
                }
                is IRWhen -> {
                    for (branch in stmt.branches) {
                        collectTweens(branch.body, context, tweens)
                    }
                    stmt.otherwise?.let { collectTweens(it, context, tweens) }
                }
                is IRWhile -> {
                    collectTweens(stmt.body, context, tweens)
                }
                is IRFor -> {
                    collectTweens(stmt.body, context, tweens)
                }
                is IRPoolForEach -> {
                    collectTweens(stmt.bodyStatements, context, tweens)
                }
                is IRPoolSpawn -> {
                    collectTweens(stmt.initStatements, context, tweens)
                }
                is IRPoolSpawnAt -> {
                    collectTweens(stmt.initStatements, context, tweens)
                }
                is IRPoolTrySpawn -> {
                    collectTweens(stmt.initStatements, context, tweens)
                    collectTweens(stmt.elseStatements, context, tweens)
                }
                is IRTransitionFadeOut -> {
                    collectTweens(stmt.onComplete, context, tweens)
                }
                is IRTransitionFadeIn -> {
                    collectTweens(stmt.onComplete, context, tweens)
                }
                is IRTransitionWipe -> {
                    collectTweens(stmt.onComplete, context, tweens)
                }
                is IRTransitionIris -> {
                    collectTweens(stmt.onComplete, context, tweens)
                }
                else -> {
                    // No nested statements to process
                }
            }
        }
    }

    /**
     * Validate IR references (pools, menus, dialogs, sprites) in all IR statements. This extends
     * the existing validation to cover references outside state machines.
     */
    private fun validateIRReferences() {
        // Collect known names
        val knownSprites =
            game.sprites.map { it.name }.toSet() +
                game.entities.mapNotNull { it.sprite?.name }.toSet() +
                game.pools.map { "${it.name}_sprite" }.toSet()
        val knownPools = game.pools.map { it.name }.toSet()
        val knownMenus = game.menus.map { it.name }.toSet()
        val knownDialogs = game.dialogs.map { it.name }.toSet()

        // Scan all scenes
        for ((sceneName, scene) in game.scenes) {
            validateIRReferencesInStatements(
                scene.onEnter,
                "scene '$sceneName' enter",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
            validateIRReferencesInStatements(
                scene.onFrame,
                "scene '$sceneName' frame",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
            validateIRReferencesInStatements(
                scene.onExit,
                "scene '$sceneName' exit",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
        }

        // Scan pools
        for (pool in game.pools) {
            validateIRReferencesInStatements(
                pool.onFrameStatements,
                "pool '${pool.name}'",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
        }

        // Scan state machines
        for (machine in game.stateMachines) {
            for ((stateName, state) in machine.states) {
                val context = "state machine '${machine.name}::$stateName'"
                validateIRReferencesInStatements(
                    state.onEnter,
                    "$context onEnter",
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
                validateIRReferencesInStatements(
                    state.onTick,
                    "$context onTick",
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
                validateIRReferencesInStatements(
                    state.onExit,
                    "$context onExit",
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }
        }
    }

    /** Recursively validate IR references in statements. */
    private fun validateIRReferencesInStatements(
        statements: List<IRStatement>,
        context: String,
        knownSprites: Set<String>,
        knownPools: Set<String>,
        knownMenus: Set<String>,
        knownDialogs: Set<String>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                // Animation references
                is IRAnimationPlay -> {
                    if (stmt.spriteName !in knownSprites) {
                        val suggestion = Suggestions.formatSuggestion(stmt.spriteName, knownSprites)
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation references unknown sprite '${stmt.spriteName}'.$suggestion",
                            )
                        )
                    }
                }
                is IRAnimationStop -> {
                    if (stmt.spriteName !in knownSprites) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation stop references unknown sprite '${stmt.spriteName}'.",
                            )
                        )
                    }
                }
                is IRAnimationPause -> {
                    if (stmt.spriteName !in knownSprites) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation pause references unknown sprite '${stmt.spriteName}'.",
                            )
                        )
                    }
                }
                is IRAnimationResume -> {
                    if (stmt.spriteName !in knownSprites) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation resume references unknown sprite '${stmt.spriteName}'.",
                            )
                        )
                    }
                }
                is IRAnimationSetFrame -> {
                    if (stmt.spriteName !in knownSprites) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation setFrame references unknown sprite '${stmt.spriteName}'.",
                            )
                        )
                    }
                }
                is IRAnimationSetSpeed -> {
                    if (stmt.spriteName !in knownSprites) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation setSpeed references unknown sprite '${stmt.spriteName}'.",
                            )
                        )
                    }
                }
                is IRAnimationQueue -> {
                    if (stmt.spriteName !in knownSprites) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.SPRITE_REFERENCE,
                                "$context: Animation queue references unknown sprite '${stmt.spriteName}'.",
                            )
                        )
                    }
                }

                // Pool references
                is IRPoolSpawn -> {
                    if (stmt.poolName !in knownPools) {
                        val suggestion = Suggestions.formatSuggestion(stmt.poolName, knownPools)
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool spawn references unknown pool '${stmt.poolName}'.$suggestion",
                            )
                        )
                    }
                    validateIRReferencesInStatements(
                        stmt.initStatements,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRPoolSpawnAt -> {
                    if (stmt.poolName !in knownPools) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool spawnAt references unknown pool '${stmt.poolName}'.",
                            )
                        )
                    }
                    validateIRReferencesInStatements(
                        stmt.initStatements,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRPoolTrySpawn -> {
                    if (stmt.poolName !in knownPools) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool trySpawn references unknown pool '${stmt.poolName}'.",
                            )
                        )
                    }
                    validateIRReferencesInStatements(
                        stmt.initStatements,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                    validateIRReferencesInStatements(
                        stmt.elseStatements,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRPoolUpdate -> {
                    if (stmt.poolName !in knownPools) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool update references unknown pool '${stmt.poolName}'.",
                            )
                        )
                    }
                }
                is IRPoolDespawn -> {
                    if (stmt.poolName !in knownPools) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool despawn references unknown pool '${stmt.poolName}'.",
                            )
                        )
                    }
                }
                is IRPoolDespawnAll -> {
                    if (stmt.poolName !in knownPools) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool despawnAll references unknown pool '${stmt.poolName}'.",
                            )
                        )
                    }
                }
                is IRPoolForEach -> {
                    if (stmt.poolName !in knownPools) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.POOL_REFERENCE,
                                "$context: Pool forEach references unknown pool '${stmt.poolName}'.",
                            )
                        )
                    }
                    validateIRReferencesInStatements(
                        stmt.bodyStatements,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }

                // Menu references
                is IRMenuShow -> {
                    if (stmt.menuName !in knownMenus) {
                        val suggestion = Suggestions.formatSuggestion(stmt.menuName, knownMenus)
                        errors.add(
                            ValidationError(
                                ValidationCategory.MENU_REFERENCE,
                                "$context: Menu show references unknown menu '${stmt.menuName}'.$suggestion",
                            )
                        )
                    }
                }
                is IRMenuOpen -> {
                    if (stmt.menuName !in knownMenus) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.MENU_REFERENCE,
                                "$context: Menu open references unknown menu '${stmt.menuName}'.",
                            )
                        )
                    }
                }
                is IRMenuTick -> {
                    if (stmt.menuName !in knownMenus) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.MENU_REFERENCE,
                                "$context: Menu tick references unknown menu '${stmt.menuName}'.",
                            )
                        )
                    }
                }
                is IRMenuSelect -> {
                    if (stmt.menuName !in knownMenus) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.MENU_REFERENCE,
                                "$context: Menu select references unknown menu '${stmt.menuName}'.",
                            )
                        )
                    }
                }
                is IRMenuCancel -> {
                    if (stmt.menuName !in knownMenus) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.MENU_REFERENCE,
                                "$context: Menu cancel references unknown menu '${stmt.menuName}'.",
                            )
                        )
                    }
                }
                is IRMenuMoveTo -> {
                    if (stmt.menuName !in knownMenus) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.MENU_REFERENCE,
                                "$context: Menu moveTo references unknown menu '${stmt.menuName}'.",
                            )
                        )
                    }
                }

                // Dialog references
                is IRDialogShow -> {
                    if (stmt.dialogName !in knownDialogs) {
                        val suggestion = Suggestions.formatSuggestion(stmt.dialogName, knownDialogs)
                        errors.add(
                            ValidationError(
                                ValidationCategory.DIALOG_REFERENCE,
                                "$context: Dialog show references unknown dialog '${stmt.dialogName}'.$suggestion",
                            )
                        )
                    }
                }
                is IRDialogSay -> {
                    if (stmt.dialogName !in knownDialogs) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.DIALOG_REFERENCE,
                                "$context: Dialog say references unknown dialog '${stmt.dialogName}'.",
                            )
                        )
                    }
                }

                // Nested statements - recurse
                is IRIf -> {
                    validateIRReferencesInStatements(
                        stmt.then,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                    stmt.otherwise?.let {
                        validateIRReferencesInStatements(
                            it,
                            context,
                            knownSprites,
                            knownPools,
                            knownMenus,
                            knownDialogs,
                        )
                    }
                }
                is IRWhen -> {
                    for (branch in stmt.branches) {
                        validateIRReferencesInStatements(
                            branch.body,
                            context,
                            knownSprites,
                            knownPools,
                            knownMenus,
                            knownDialogs,
                        )
                    }
                    stmt.otherwise?.let {
                        validateIRReferencesInStatements(
                            it,
                            context,
                            knownSprites,
                            knownPools,
                            knownMenus,
                            knownDialogs,
                        )
                    }
                }
                is IRWhile -> {
                    validateIRReferencesInStatements(
                        stmt.body,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRFor -> {
                    validateIRReferencesInStatements(
                        stmt.body,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRTransitionFadeOut -> {
                    validateIRReferencesInStatements(
                        stmt.onComplete,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRTransitionFadeIn -> {
                    validateIRReferencesInStatements(
                        stmt.onComplete,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRTransitionWipe -> {
                    validateIRReferencesInStatements(
                        stmt.onComplete,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                is IRTransitionIris -> {
                    validateIRReferencesInStatements(
                        stmt.onComplete,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                else -> {
                    // No nested statements or references to validate
                }
            }
        }
    }

    /**
     * Validate array bounds for dynamic indices. Detects provably out-of-bounds accesses and warns
     * on unchecked dynamic access.
     */
    private fun validateArrayBounds() {
        val arrayBounds = game.arrays.associate { it.name to it.size }

        // Scan all scenes
        for ((sceneName, scene) in game.scenes) {
            validateArrayBoundsInStatements(
                scene.onEnter,
                "scene '$sceneName' enter",
                arrayBounds,
                emptyMap(),
            )
            validateArrayBoundsInStatements(
                scene.onFrame,
                "scene '$sceneName' frame",
                arrayBounds,
                emptyMap(),
            )
            validateArrayBoundsInStatements(
                scene.onExit,
                "scene '$sceneName' exit",
                arrayBounds,
                emptyMap(),
            )
        }

        // Scan pools
        for (pool in game.pools) {
            validateArrayBoundsInStatements(
                pool.onFrameStatements,
                "pool '${pool.name}'",
                arrayBounds,
                emptyMap(),
            )
        }

        // Scan state machines
        for (machine in game.stateMachines) {
            for ((stateName, state) in machine.states) {
                val context = "state machine '${machine.name}::$stateName'"
                validateArrayBoundsInStatements(
                    state.onEnter,
                    "$context onEnter",
                    arrayBounds,
                    emptyMap(),
                )
                validateArrayBoundsInStatements(
                    state.onTick,
                    "$context onTick",
                    arrayBounds,
                    emptyMap(),
                )
                validateArrayBoundsInStatements(
                    state.onExit,
                    "$context onExit",
                    arrayBounds,
                    emptyMap(),
                )
            }
        }
    }

    /**
     * Recursively validate array bounds in statements, tracking known variable ranges from loops.
     */
    private fun validateArrayBoundsInStatements(
        statements: List<IRStatement>,
        context: String,
        arrayBounds: Map<String, Int>,
        knownBounds: Map<String, IntRange>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is IRFor -> {
                    // Inside loop, counter has known bounds
                    val newBounds = knownBounds + (stmt.counter to stmt.range)
                    validateArrayBoundsInStatements(stmt.body, context, arrayBounds, newBounds)
                }
                is IRArrayAssign -> {
                    validateArrayIndex(stmt.array, stmt.index, context, arrayBounds, knownBounds)
                }
                is IRIf -> {
                    validateArrayBoundsInStatements(stmt.then, context, arrayBounds, knownBounds)
                    stmt.otherwise?.let {
                        validateArrayBoundsInStatements(it, context, arrayBounds, knownBounds)
                    }
                    // Also check expressions in condition
                    validateArrayBoundsInExpression(
                        stmt.condition,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRWhen -> {
                    for (branch in stmt.branches) {
                        validateArrayBoundsInStatements(
                            branch.body,
                            context,
                            arrayBounds,
                            knownBounds,
                        )
                        validateArrayBoundsInExpression(
                            branch.condition,
                            context,
                            arrayBounds,
                            knownBounds,
                        )
                    }
                    stmt.otherwise?.let {
                        validateArrayBoundsInStatements(it, context, arrayBounds, knownBounds)
                    }
                }
                is IRWhile -> {
                    validateArrayBoundsInStatements(stmt.body, context, arrayBounds, knownBounds)
                    validateArrayBoundsInExpression(
                        stmt.condition,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRPoolForEach -> {
                    validateArrayBoundsInStatements(
                        stmt.bodyStatements,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRPoolSpawn -> {
                    validateArrayBoundsInStatements(
                        stmt.initStatements,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRPoolSpawnAt -> {
                    validateArrayBoundsInStatements(
                        stmt.initStatements,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRPoolTrySpawn -> {
                    validateArrayBoundsInStatements(
                        stmt.initStatements,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                    validateArrayBoundsInStatements(
                        stmt.elseStatements,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRTransitionFadeOut -> {
                    validateArrayBoundsInStatements(
                        stmt.onComplete,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRTransitionFadeIn -> {
                    validateArrayBoundsInStatements(
                        stmt.onComplete,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRTransitionWipe -> {
                    validateArrayBoundsInStatements(
                        stmt.onComplete,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                is IRTransitionIris -> {
                    validateArrayBoundsInStatements(
                        stmt.onComplete,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                else -> {
                    // Check any expressions in the statement for array access
                }
            }
        }
    }

    /** Recursively validate array bounds in expressions. */
    private fun validateArrayBoundsInExpression(
        expr: IRExpression,
        context: String,
        arrayBounds: Map<String, Int>,
        knownBounds: Map<String, IntRange>,
    ) {
        when (expr) {
            is IRArrayAccess -> {
                validateArrayIndex(expr.array, expr.index, context, arrayBounds, knownBounds)
                // Also validate nested expressions in the index
                validateArrayBoundsInExpression(expr.index, context, arrayBounds, knownBounds)
            }
            is IRBinary -> {
                validateArrayBoundsInExpression(expr.left, context, arrayBounds, knownBounds)
                validateArrayBoundsInExpression(expr.right, context, arrayBounds, knownBounds)
            }
            is IRUnary -> {
                validateArrayBoundsInExpression(expr.operand, context, arrayBounds, knownBounds)
            }
            is IRTernary -> {
                validateArrayBoundsInExpression(expr.cond, context, arrayBounds, knownBounds)
                validateArrayBoundsInExpression(expr.`then`, context, arrayBounds, knownBounds)
                validateArrayBoundsInExpression(expr.`otherwise`, context, arrayBounds, knownBounds)
            }
            is IRCallExpr -> {
                expr.args.forEach { arg ->
                    validateArrayBoundsInExpression(arg, context, arrayBounds, knownBounds)
                }
            }
            // Leaf expressions - no nested array access possible
            is IRLiteral,
            is IRVar -> {
                // No array access in these expression types
            }
            else -> {
                // Other expression types - may contain IRExpression fields, but most are
                // domain-specific
                // and don't typically contain array accesses. Skip for now.
            }
        }
    }

    /** Validate a single array index for bounds. */
    private fun validateArrayIndex(
        arrayName: String,
        index: IRExpression,
        context: String,
        arrayBounds: Map<String, Int>,
        knownBounds: Map<String, IntRange>,
    ) {
        val arraySize = arrayBounds[arrayName] ?: return // Unknown array, skip

        when (index) {
            is IRLiteral -> {
                // Static index - check bounds
                val i = extractLiteralValue(index)
                if (i != null && (i < 0 || i >= arraySize)) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.ARRAY_BOUNDS,
                            "$context: Array '$arrayName' access with literal index $i is out of bounds (size: $arraySize)",
                        )
                    )
                }
            }
            is IRVar -> {
                val range = knownBounds[index.name]
                if (range != null) {
                    // We know the variable's range from a for loop
                    if (range.first < 0 || range.last >= arraySize) {
                        errors.add(
                            ValidationError(
                                ValidationCategory.ARRAY_BOUNDS,
                                "$context: Array '$arrayName' access with loop variable '${index.name}' " +
                                    "(range: ${range.first}..${range.last}) may be out of bounds (size: $arraySize)",
                            )
                        )
                    }
                } else {
                    // Unknown range - warn about unchecked access
                    warnings.add(
                        ValidationWarning(
                            ValidationCategory.ARRAY_BOUNDS,
                            "$context: Array '$arrayName' access with unchecked dynamic index '${index.name}'. " +
                                "Consider using bounds checking or a for loop with known range.",
                        )
                    )
                }
            }
            else -> {
                // Complex expression - warn about unchecked access
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.ARRAY_BOUNDS,
                        "$context: Array '$arrayName' access with complex expression index. " +
                            "Cannot verify bounds at compile time.",
                    )
                )
            }
        }
    }

    /**
     * Validate physics configuration values. Checks for common physics configuration errors that
     * could cause runtime issues.
     */
    private fun validatePhysics() {
        // Validate entity physics components
        for (entity in game.entities) {
            val physics = entity.physicsComponent ?: continue

            // Convert fixed-point 8.8 back to float for validation
            val mass = physics.mass / 256f
            val friction = physics.friction / 256f
            val gravity = physics.gravity / 256f

            // Error: mass must be positive
            if (mass <= 0f) {
                errors.add(
                    ValidationError(
                        ValidationCategory.PHYSICS,
                        "Entity '${entity.name}' has non-positive mass ($mass). Mass must be > 0.",
                    )
                )
            }

            // Warning: maxVelocity exceeds i8 range
            if (physics.maxVelocityX > 127) {
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.PHYSICS,
                        "Entity '${entity.name}' maxVelocityX (${physics.maxVelocityX}) exceeds i8 range (127). " +
                            "Velocity will be clamped.",
                    )
                )
            }
            if (physics.maxVelocityY > 127) {
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.PHYSICS,
                        "Entity '${entity.name}' maxVelocityY (${physics.maxVelocityY}) exceeds i8 range (127). " +
                            "Velocity will be clamped.",
                    )
                )
            }

            // Warning: friction outside typical range
            if (friction < 0f || friction > 1.5f) {
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.PHYSICS,
                        "Entity '${entity.name}' friction ($friction) outside typical range [0, 1.0]. " +
                            "Values > 1 will accelerate instead of decelerate.",
                    )
                )
            }

            // Warning: gravity outside typical range
            if (gravity < -2f || gravity > 2f) {
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.PHYSICS,
                        "Entity '${entity.name}' gravity ($gravity) outside typical range [-2.0, 2.0]. " +
                            "Extreme values may cause jittery movement.",
                    )
                )
            }
        }

        // Validate gravity zones
        val physicsWorld = game.physicsWorld
        if (physicsWorld != null) {
            for ((index, zone) in physicsWorld.config.gravityZones.withIndex()) {
                if (zone.width <= 0 || zone.height <= 0) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.PHYSICS,
                            "Gravity zone $index has invalid dimensions (${zone.width}x${zone.height}). " +
                                "Width and height must be > 0.",
                        )
                    )
                }
            }
        }
    }
}

/** Extension function to validate a game. */
fun Game.validate(): ValidationResult = GameValidator(this).validate()

/** Extension function to validate and throw if invalid. */
fun Game.validateOrThrow() {
    validate().throwIfInvalid()
}

/**
 * Platform-specific asset validation. Returns a validation result with errors if the asset is
 * invalid. On non-JVM platforms, returns a valid result (validation skipped).
 */
internal expect fun validateAssetFile(assetPath: String, assetDir: String?): AssetValidationResult

/** Result of asset file validation. */
internal data class AssetValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)
