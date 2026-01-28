/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MaxLineLength") // Validation messages need to be descriptive

package io.github.gbkt.core

import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRTween
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.validation.extractLiteralValue
import io.github.gbkt.core.validation.validateArrayBounds
import io.github.gbkt.core.validation.validateIRReferences

// =============================================================================
// GAME VALIDATION
// =============================================================================

/** Validation message severity levels. */
enum class ValidationSeverity {
    /** Informational message, doesn't indicate a problem. */
    INFO,

    /** Warning that doesn't prevent compilation. */
    WARNING,

    /** Error that prevents compilation. */
    ERROR,
}

/** A validation message with severity and optional location. */
data class ValidationMessage(
    /** The message text. */
    val message: String,

    /** Severity level. */
    val severity: ValidationSeverity,

    /** Optional location reference (file:line, scene name, etc.). */
    val location: String? = null,

    /** Optional suggestion for fixing the issue. */
    val suggestion: String? = null,

    /**
     * Optional category for domain-specific categorization. This allows validation consumers to
     * filter/group messages by type.
     */
    val category: String? = null,
)

/** Validation result containing all errors and warnings found. */
data class ValidationResult(
    /** Whether validation passed (no errors). */
    val isValid: Boolean,

    /** Validation errors that prevent compilation. */
    val errors: List<ValidationMessage> = emptyList(),

    /** Warnings that don't prevent compilation but may indicate issues. */
    val warnings: List<ValidationMessage> = emptyList(),

    /** Informational messages (e.g., optimization suggestions). */
    val info: List<ValidationMessage> = emptyList(),
) {
    /** Total number of messages across all severity levels. */
    val messageCount: Int
        get() = errors.size + warnings.size + info.size

    /** Throw ValidationException if this result has errors. */
    fun throwIfInvalid() {
        if (!isValid) {
            throw ValidationException(this)
        }
    }

    companion object {
        /** A successful validation with no messages. */
        val SUCCESS = ValidationResult(isValid = true)

        /** Create a failed result with a single error. */
        fun error(message: String, location: String? = null, category: String? = null) =
            ValidationResult(
                isValid = false,
                errors =
                    listOf(
                        ValidationMessage(
                            message,
                            ValidationSeverity.ERROR,
                            location,
                            category = category,
                        )
                    ),
            )

        /** Create a successful result with warnings. */
        fun withWarnings(warnings: List<ValidationMessage>) =
            ValidationResult(isValid = true, warnings = warnings)
    }
}

/**
 * Validation error with category information.
 *
 * This is a convenience wrapper for internal use that includes the validation category.
 */
data class ValidationError(val category: ValidationCategory, val message: String) {
    /** Convert to a ValidationMessage for the unified API. */
    fun toMessage(): ValidationMessage =
        ValidationMessage(
            message = message,
            severity = ValidationSeverity.ERROR,
            category = category.name,
        )
}

/**
 * Validation warning with category information.
 *
 * This is a convenience wrapper for internal use that includes the validation category.
 */
data class ValidationWarning(val category: ValidationCategory, val message: String) {
    /** Convert to a ValidationMessage for the unified API. */
    fun toMessage(): ValidationMessage =
        ValidationMessage(
            message = message,
            severity = ValidationSeverity.WARNING,
            category = category.name,
        )
}

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

/** Exception thrown when validation fails. */
class ValidationException(val result: ValidationResult) :
    RuntimeException(
        buildString {
            append("Validation failed with ${result.errors.size} error(s)")
            result.errors.forEach { append("\n  - [${it.category ?: ""}] ${it.message}") }
            if (result.warnings.isNotEmpty()) {
                append("\n\nAdditionally, ${result.warnings.size} warning(s):")
                result.warnings.forEach { append("\n  - [${it.category ?: ""}] ${it.message}") }
            }
        }
    )

/** Validates a game definition for common issues. */
@Suppress("LargeClass", "TooManyFunctions") // Validation requires comprehensive checks
class GameValidator(internal val game: Game) {
    internal val errors = mutableListOf<ValidationError>()
    internal val warnings = mutableListOf<ValidationWarning>()

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

        // Convert internal error/warning lists to unified ValidationResult
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors.map { it.toMessage() },
            warnings = warnings.map { it.toMessage() },
        )
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
    @Suppress("CyclomaticComplexMethod") // IR statement matching is inherently complex
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
    @Suppress("LongMethod", "CyclomaticComplexMethod") // Memory tracking requires many checks
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
                var bytes = 4 // x (2) + y (2) - position is always present
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
            if (assetPath.isEmpty()) return@forEach
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

    /** Validate tween parameters for duration and value bounds. */
    @Suppress("CyclomaticComplexMethod") // Tween validation has many edge cases
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

    /** Recursively collect tweens from IR statements. */
    @Suppress("CyclomaticComplexMethod") // IR statement matching is inherently complex
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
     * Validate physics configuration values. Checks for common physics configuration errors that
     * could cause runtime issues.
     */
    @Suppress("CyclomaticComplexMethod") // Physics validation has many constraints
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
 * Asset validation using PNG header validation and full ImageIO decode.
 *
 * This first runs platform-independent PNG validation (signature check, dimension parsing), then
 * follows up with full ImageIO decode validation for comprehensive error detection.
 */
@Suppress("ReturnCount") // Multiple early returns for validation clarity
internal fun validateAssetFile(assetPath: String, assetDir: String?): AssetValidationResult {
    val errors = mutableListOf<String>()

    if (assetDir == null) {
        return AssetValidationResult(false, listOf("Asset directory not specified"))
    }

    val fullPath = FileIO.resolvePath(assetDir, assetPath)

    // Check if file exists
    if (!FileIO.exists(fullPath)) {
        errors.add("Asset file not found: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // Check if file is readable
    if (!FileIO.isReadable(fullPath)) {
        errors.add("Asset file is not readable: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // Check PNG extension
    if (!assetPath.lowercase().endsWith(".png")) {
        errors.add("Asset file must be a PNG file: $assetPath")
        return AssetValidationResult(false, errors)
    }

    // Read file bytes for common validation
    val bytes = FileIO.readBytes(fullPath)
    if (bytes == null) {
        errors.add("Failed to read asset file: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // First: Run platform-independent PNG validation
    // This checks PNG signature and parses IHDR for dimensions
    val pngResult = PngValidator.validate(bytes, assetPath)
    if (!pngResult.isValid) {
        // If common validation fails, return those errors immediately
        errors.addAll(pngResult.errors)
        return AssetValidationResult(false, errors)
    }

    // Second: Run full ImageIO decode validation for comprehensive checks
    // This catches issues the header-only validation might miss
    val imageIOResult = AssetValidator.validateAsset(assetPath, assetDir)
    if (!imageIOResult.isValid) {
        errors.addAll(imageIOResult.errors)
    }

    return AssetValidationResult(errors.isEmpty(), errors)
}

/** Result of asset file validation. */
internal data class AssetValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)
