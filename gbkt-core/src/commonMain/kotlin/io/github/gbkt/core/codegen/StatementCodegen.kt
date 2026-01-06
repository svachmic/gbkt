/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.Channel
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.Heuristic
import io.github.gbkt.core.Panning
import io.github.gbkt.core.ir.IRAnimationPause
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRAnimationQueue
import io.github.gbkt.core.ir.IRAnimationResume
import io.github.gbkt.core.ir.IRAnimationSetFrame
import io.github.gbkt.core.ir.IRAnimationSetSpeed
import io.github.gbkt.core.ir.IRAnimationStop
import io.github.gbkt.core.ir.IRArrayAssign
import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRCameraFollow
import io.github.gbkt.core.ir.IRCameraSetBounds
import io.github.gbkt.core.ir.IRCameraSetPosition
import io.github.gbkt.core.ir.IRCameraShake
import io.github.gbkt.core.ir.IRCameraShakeStop
import io.github.gbkt.core.ir.IRCameraSnapTo
import io.github.gbkt.core.ir.IRCameraStopFollow
import io.github.gbkt.core.ir.IRCameraUpdate
import io.github.gbkt.core.ir.IRClearScreen
import io.github.gbkt.core.ir.IRCollisionResponse
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.IRCutsceneSkip
import io.github.gbkt.core.ir.IRCutsceneStart
import io.github.gbkt.core.ir.IRCutsceneUpdate
import io.github.gbkt.core.ir.IRDialogChoice
import io.github.gbkt.core.ir.IRDialogHide
import io.github.gbkt.core.ir.IRDialogSay
import io.github.gbkt.core.ir.IRDialogShow
import io.github.gbkt.core.ir.IRDialogTick
import io.github.gbkt.core.ir.IREntityUpdate
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRInputBufferDecl
import io.github.gbkt.core.ir.IRInputBufferFill
import io.github.gbkt.core.ir.IRInputBufferReset
import io.github.gbkt.core.ir.IRLinkInit
import io.github.gbkt.core.ir.IRLinkSend
import io.github.gbkt.core.ir.IRLinkUpdate
import io.github.gbkt.core.ir.IRMenuCancel
import io.github.gbkt.core.ir.IRMenuClose
import io.github.gbkt.core.ir.IRMenuHide
import io.github.gbkt.core.ir.IRMenuMoveTo
import io.github.gbkt.core.ir.IRMenuOpen
import io.github.gbkt.core.ir.IRMenuSelect
import io.github.gbkt.core.ir.IRMenuShow
import io.github.gbkt.core.ir.IRMenuTick
import io.github.gbkt.core.ir.IRMenuToggle
import io.github.gbkt.core.ir.IRMixerFade
import io.github.gbkt.core.ir.IRMixerMute
import io.github.gbkt.core.ir.IRMixerPriorityCheck
import io.github.gbkt.core.ir.IRMixerSetVolume
import io.github.gbkt.core.ir.IRMixerToggleMute
import io.github.gbkt.core.ir.IRMusicFade
import io.github.gbkt.core.ir.IRMusicPause
import io.github.gbkt.core.ir.IRMusicPlay
import io.github.gbkt.core.ir.IRMusicResume
import io.github.gbkt.core.ir.IRMusicStop
import io.github.gbkt.core.ir.IRNavGridInit
import io.github.gbkt.core.ir.IRNavGridSetTile
import io.github.gbkt.core.ir.IRNavGridSetWeight
import io.github.gbkt.core.ir.IRPaletteApply
import io.github.gbkt.core.ir.IRPaletteFade
import io.github.gbkt.core.ir.IRPaletteFlash
import io.github.gbkt.core.ir.IRPaletteSetColor
import io.github.gbkt.core.ir.IRPathAdvance
import io.github.gbkt.core.ir.IRPathFind
import io.github.gbkt.core.ir.IRPathFollow
import io.github.gbkt.core.ir.IRPathReset
import io.github.gbkt.core.ir.IRPhysicsApply
import io.github.gbkt.core.ir.IRPhysicsWorldUpdate
import io.github.gbkt.core.ir.IRPoolDespawn
import io.github.gbkt.core.ir.IRPoolDespawnAll
import io.github.gbkt.core.ir.IRPoolDespawnWhere
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolPathFollow
import io.github.gbkt.core.ir.IRPoolPathRecalc
import io.github.gbkt.core.ir.IRPoolPathSetTarget
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRPoolUpdate
import io.github.gbkt.core.ir.IRPrintAt
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRSaveArrayWrite
import io.github.gbkt.core.ir.IRSaveCopy
import io.github.gbkt.core.ir.IRSaveErase
import io.github.gbkt.core.ir.IRSaveFieldWrite
import io.github.gbkt.core.ir.IRSaveLoad
import io.github.gbkt.core.ir.IRSaveSave
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRShowBackground
import io.github.gbkt.core.ir.IRShowSprites
import io.github.gbkt.core.ir.IRSoundEnable
import io.github.gbkt.core.ir.IRSoundMasterVolume
import io.github.gbkt.core.ir.IRSoundMuteChannel
import io.github.gbkt.core.ir.IRSoundPan
import io.github.gbkt.core.ir.IRSoundPlay
import io.github.gbkt.core.ir.IRSoundStop
import io.github.gbkt.core.ir.IRSpriteSetPalette
import io.github.gbkt.core.ir.IRStateMachineGoto
import io.github.gbkt.core.ir.IRStateMachineStart
import io.github.gbkt.core.ir.IRStateMachineUpdate
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTransitionCancel
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionFlash
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRTween
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile
import io.github.gbkt.core.ir.IrisType
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.TextPart
import io.github.gbkt.core.ir.WipeDirection

// Constants for repeated C code strings
private const val RESET_TRANSITION_TIMER = "_transition_timer = 0;"

/**
 * Statement code generation for IR statements.
 *
 * Core control flow statements are handled inline. All other statements are delegated to
 * category-specific handlers to reduce cognitive complexity.
 */
internal fun CodeGenerator.generateStatement(stmt: IRStatement) {
    // Core control flow statements - keep inline for clarity
    when (stmt) {
        is IRAssign -> generateAssignStatement(stmt)
        is IRIf -> generateIfStatement(stmt)
        is IRWhen -> generateWhenStatement(stmt)
        is IRWhile -> generateWhileStatement(stmt)
        is IRFor -> generateForStatement(stmt)
        is IRCall -> generateCallStatement(stmt)
        is IRSceneChange -> generateSceneChangeStatement(stmt)
        is IRRaw -> generateRawStatement(stmt)
        is IRArrayAssign -> generateArrayAssign(stmt)
        // Delegate to category handlers for all other statement types
        else -> generateDelegatedStatement(stmt)
    }
}

private fun CodeGenerator.generateAssignStatement(stmt: IRAssign) {
    val value = generateExpr(stmt.value)
    lineWithSource("${stmt.target} ${stmt.op.c} $value;", stmt.sourceLocation, stmt.target)
}

private fun CodeGenerator.generateWhileStatement(stmt: IRWhile) {
    val cond = generateExpr(stmt.condition)
    blockWithSource("while ($cond)", stmt.sourceLocation) {
        stmt.body.forEach { generateStatement(it) }
    }
}

private fun CodeGenerator.generateForStatement(stmt: IRFor) {
    val start = stmt.range.first
    val end = stmt.range.last
    blockWithSource(
        "for (UINT8 ${stmt.counter} = $start; ${stmt.counter} <= $end; ${stmt.counter}++)",
        stmt.sourceLocation,
        stmt.counter,
    ) {
        stmt.body.forEach { generateStatement(it) }
    }
}

private fun CodeGenerator.generateCallStatement(stmt: IRCall) {
    val args = stmt.args.joinToString(", ") { generateExpr(it) }
    lineWithSource("${stmt.function}($args);", stmt.sourceLocation, stmt.function)
}

private fun CodeGenerator.generateSceneChangeStatement(stmt: IRSceneChange) {
    lineWithSource(
        "_next_scene = SCENE_${stmt.sceneName.uppercase()};",
        stmt.sourceLocation,
        stmt.sceneName,
    )
    line("_scene_changed = 1;")
}

private fun CodeGenerator.generateRawStatement(stmt: IRRaw) {
    val lines = stmt.code.lines()
    if (lines.isNotEmpty()) {
        lineWithSource(lines.first(), stmt.sourceLocation)
        lines.drop(1).forEach { line(it) }
    }
}

private fun CodeGenerator.generateDelegatedStatement(stmt: IRStatement) {
    if (generateSoundMusicStatement(stmt)) return
    if (generateDisplayStatement(stmt)) return
    if (generateAnimationStatement(stmt)) return
    if (generateSaveStatement(stmt)) return
    if (generateDialogStatement(stmt)) return
    if (generateMenuStatement(stmt)) return
    if (generatePoolStatement(stmt)) return
    if (generateCameraStatement(stmt)) return
    if (generateTransitionStatement(stmt)) return
    if (generatePathfindingStatement(stmt)) return
    if (generateMiscStatement(stmt)) return
    error("Unhandled IR statement type: ${stmt::class.simpleName}")
}

// Core control flow helpers

private fun CodeGenerator.generateIfStatement(stmt: IRIf) {
    val cond = generateExpr(stmt.condition)
    blockWithSource("if ($cond)", stmt.sourceLocation) {
        stmt.then.forEach { generateStatement(it) }
    }
    if (stmt.otherwise != null) {
        line("else {")
        indent++
        stmt.otherwise.forEach { generateStatement(it) }
        indent--
        line("}")
    }
}

private fun CodeGenerator.generateWhenStatement(stmt: IRWhen) {
    stmt.branches.forEachIndexed { i, branch ->
        val keyword = if (i == 0) "if" else "else if"
        val cond = generateExpr(branch.condition)
        if (i == 0 && stmt.sourceLocation != null) {
            blockWithSource("$keyword ($cond)", stmt.sourceLocation) {
                branch.body.forEach { generateStatement(it) }
            }
        } else {
            block("$keyword ($cond)") { branch.body.forEach { generateStatement(it) } }
        }
    }
    if (stmt.otherwise != null) {
        block("else") { stmt.otherwise.forEach { generateStatement(it) } }
    }
}

private fun CodeGenerator.generateArrayAssign(stmt: IRArrayAssign) {
    val index = generateExpr(stmt.index)
    val value = generateExpr(stmt.value)
    val array = game.arrays.find { it.name == stmt.array }
    if (array != null) {
        lineWithSource(
            "GB_ARRAY_SET(${stmt.array}, $index, ${array.size}, $value);",
            stmt.sourceLocation,
            stmt.array,
        )
    } else {
        lineWithSource("${stmt.array}[$index] = $value;", stmt.sourceLocation, stmt.array)
    }
}

// =============================================================================
// STATEMENT HELPER FUNCTIONS
// =============================================================================

// Menu helper functions to avoid code duplication
private fun CodeGenerator.showMenu(menuName: String, isGrid: Boolean) {
    if (isGrid) {
        line("_${menuName}_cursor_x = 0;")
        line("_${menuName}_cursor_y = 0;")
    } else {
        line("_${menuName}_cursor = 0;")
    }
    line("_${menuName}_visible = 1;")
    line("_${menuName}_active = 1;")
    line("_${menuName}_draw();")
}

private fun CodeGenerator.hideMenu(menuName: String) {
    line("_${menuName}_visible = 0;")
    line("_${menuName}_active = 0;")
}

private fun CodeGenerator.generateSoundPlay(stmt: IRSoundPlay) {
    val sfx = game.soundEffects.find { it.name == stmt.soundName }
    if (sfx != null) {
        // If mixer is present, check priority and mute state
        if (game.audioMixer != null) {
            val channelIndex = sfx.channel.index
            val priorityValue = stmt.priority.ordinal
            block(
                "if (_mixer_can_play($channelIndex, $priorityValue) && !_mixer_is_muted($channelIndex))"
            ) {
                if (game.music.isNotEmpty()) {
                    line("hUGE_mute_channel(HT_CH${sfx.channel.index + 1}, 1);")
                }
                line("play_${stmt.soundName}();")
            }
        } else {
            if (game.music.isNotEmpty()) {
                line("hUGE_mute_channel(HT_CH${sfx.channel.index + 1}, 1);")
            }
            line("play_${stmt.soundName}();")
        }
    }
}

private fun CodeGenerator.generateSoundStop(stmt: IRSoundStop) {
    val sfx = game.soundEffects.find { it.name == stmt.soundName }
    if (sfx != null) {
        when (sfx.channel) {
            Channel.PULSE1 -> line("NR12_REG = 0;")
            Channel.PULSE2 -> line("NR22_REG = 0;")
            Channel.WAVE -> line("NR30_REG = 0;")
            Channel.NOISE -> line("NR42_REG = 0;")
        }
        if (game.music.isNotEmpty()) {
            line("hUGE_mute_channel(HT_CH${sfx.channel.index + 1}, 0);")
        }
    }
}

private fun CodeGenerator.generateSoundPan(stmt: IRSoundPan) {
    val channelBit = 1 shl stmt.channel.index
    when (stmt.panning) {
        Panning.LEFT ->
            line(
                "NR51_REG = (NR51_REG & ~0x${channelBit.toString(16)}) | 0x${(channelBit shl 4).toString(16)};"
            )
        Panning.RIGHT ->
            line(
                "NR51_REG = (NR51_REG & ~0x${(channelBit shl 4).toString(16)}) | 0x${channelBit.toString(16)};"
            )
        Panning.CENTER -> line("NR51_REG |= 0x${((channelBit shl 4) or channelBit).toString(16)};")
    }
}

private fun CodeGenerator.generatePaletteApply(stmt: IRPaletteApply) {
    if (game.config.gbcSupport) {
        val func = if (stmt.type == PaletteType.SPRITE) "set_sprite_palette" else "set_bkg_palette"
        line("$func(${stmt.slot}, 1, ${stmt.paletteName}_pal);")
    }
}

private fun CodeGenerator.generatePaletteSetColor(stmt: IRPaletteSetColor) {
    if (game.config.gbcSupport) {
        line("${stmt.paletteName}_pal[${stmt.colorIndex}] = ${stmt.color.toHex()};")
        val func = if (stmt.type == PaletteType.SPRITE) "set_sprite_palette" else "set_bkg_palette"
        val palette = game.palettes.find { it.name == stmt.paletteName }
        val slot = palette?.slot ?: 0
        line("$func($slot, 1, ${stmt.paletteName}_pal);")
    }
}

private fun CodeGenerator.generatePaletteFlash(stmt: IRPaletteFlash) {
    if (game.config.gbcSupport) {
        line("// Flash effect")
        line("{")
        indent++
        line(
            "UINT16 _flash_pal[] = { ${stmt.flashColor.toHex()}, ${stmt.flashColor.toHex()}, ${stmt.flashColor.toHex()}, ${stmt.flashColor.toHex()} };"
        )
        val func = if (stmt.type == PaletteType.SPRITE) "set_sprite_palette" else "set_bkg_palette"
        val palette = game.palettes.find { it.name == stmt.paletteName }
        val slot = palette?.slot ?: 0
        line("$func($slot, 1, _flash_pal);")
        indent--
        line("}")
    }
}

private fun CodeGenerator.generatePaletteFade(stmt: IRPaletteFade) {
    if (game.config.gbcSupport) {
        line("// Palette fade (progress: ${generateExpr(stmt.progress)})")
        line("{")
        indent++
        val progress = generateExpr(stmt.progress)
        line("UINT8 _fade_progress = $progress;")
        line("UINT16 _fade_pal[4];")
        for (i in 0..3) {
            val targetColor = stmt.targetColors[i]
            line(
                "_fade_pal[$i] = _interpolate_color(${stmt.paletteName}_pal[$i], ${targetColor.toHex()}, _fade_progress);"
            )
        }
        val func = if (stmt.type == PaletteType.SPRITE) "set_sprite_palette" else "set_bkg_palette"
        val palette = game.palettes.find { it.name == stmt.paletteName }
        val slot = palette?.slot ?: 0
        line("$func($slot, 1, _fade_pal);")
        indent--
        line("}")
    }
}

private fun CodeGenerator.generatePrintAt(stmt: IRPrintAt) {
    line("gotoxy(${stmt.x}, ${stmt.y});")
    val formatParts = mutableListOf<String>()
    val args = mutableListOf<String>()
    for (part in stmt.parts) {
        when (part) {
            is TextPart.Literal -> formatParts.add(part.text)
            is TextPart.Variable -> {
                formatParts.add(part.format)
                args.add(generateExpr(part.expr))
            }
        }
    }
    val format = formatParts.joinToString("")
    if (args.isEmpty()) {
        line("printf(\"$format\");")
    } else {
        line("printf(\"$format\", ${args.joinToString(", ")});")
    }
}

private fun CodeGenerator.validateSpriteAnimation(
    sprite: io.github.gbkt.core.graphics.Sprite,
    stmt: IRAnimationPlay,
): io.github.gbkt.core.graphics.Animation? {
    if (!sprite.hasAnimations) {
        reportError("Sprite '${stmt.spriteName}' has no animations defined")
        return null
    }
    val anim = sprite.animations[stmt.animationName]
    if (anim == null) {
        reportError("Animation '${stmt.animationName}' not found on sprite '${stmt.spriteName}'")
        return null
    }
    if (anim.frames.isEmpty()) {
        reportError("Animation '${stmt.animationName}' has no frames")
        return null
    }
    return anim
}

private fun CodeGenerator.validatePoolAnimation(
    pool: io.github.gbkt.core.entity.Pool,
    stmt: IRAnimationPlay,
): io.github.gbkt.core.graphics.Animation? {
    if (pool.animations.isEmpty()) {
        reportError("Pool '${pool.name}' has no animations defined")
        return null
    }
    val anim = pool.animations[stmt.animationName]
    if (anim == null) {
        reportError("Animation '${stmt.animationName}' not found on pool '${pool.name}'")
        return null
    }
    if (anim.frames.isEmpty()) {
        reportError("Animation '${stmt.animationName}' has no frames")
        return null
    }
    return anim
}

private fun CodeGenerator.generateAnimationPlay(stmt: IRAnimationPlay) {
    val sprite = game.sprites.find { it.name == stmt.spriteName }

    // Check if this is a pool sprite (naming convention: ${poolName}_sprite)
    val poolName =
        if (stmt.spriteName.endsWith("_sprite")) {
            stmt.spriteName.removeSuffix("_sprite")
        } else {
            null
        }
    val pool = poolName?.let { game.pools.find { p -> p.name == it } }

    when {
        sprite != null -> {
            validateSpriteAnimation(sprite, stmt)?.let { anim ->
                generateSpriteAnimation(stmt, sprite, anim)
            }
        }
        pool != null -> {
            validatePoolAnimation(pool, stmt)?.let { anim ->
                generatePoolAnimation(stmt, pool, anim)
            }
        }
        else -> {
            reportError("Sprite '${stmt.spriteName}' not found for animation")
        }
    }
}

private fun CodeGenerator.generateSpriteAnimation(
    stmt: IRAnimationPlay,
    sprite: io.github.gbkt.core.graphics.Sprite,
    anim: io.github.gbkt.core.graphics.Animation,
) {
    val animVar = "_${stmt.spriteName}_anim"
    val frameVar = "_${stmt.spriteName}_frame"
    val timerVar = "_${stmt.spriteName}_timer"
    val speedVar = "_${stmt.spriteName}_speed"
    val flagsVar = "_${stmt.spriteName}_flags"
    line("$animVar = ANIM_${stmt.spriteName.uppercase()}_${stmt.animationName.uppercase()};")
    val startFrame = if (stmt.reverse) anim.frames.lastIndex else 0
    val firstTile = if (stmt.reverse) anim.frames.last() else anim.frames.first()
    line("$frameVar = $startFrame;")
    line("$timerVar = ${anim.frameDelay};")
    line("$speedVar = ${stmt.speed};")
    val flags = if (stmt.reverse) "ANIM_FLAG_REVERSED" else "0"
    line("$flagsVar = $flags;")
    line("set_sprite_tile(${sprite.oamSlot}, $firstTile);")
}

private fun CodeGenerator.generatePoolAnimation(
    stmt: IRAnimationPlay,
    pool: io.github.gbkt.core.entity.Pool,
    anim: io.github.gbkt.core.graphics.Animation,
) {
    // Pool animations use the pool name (not pool_sprite) for variable naming
    val poolName = pool.name
    val animVar = "_${poolName}_anim"
    val frameVar = "_${poolName}_frame"
    val timerVar = "_${poolName}_timer"
    // Pool animations use array indexing with the current entity index
    line(
        "$animVar[_${poolName}_i] = ANIM_${poolName.uppercase()}_${stmt.animationName.uppercase()};"
    )
    val startFrame = if (stmt.reverse) anim.frames.lastIndex else 0
    line("$frameVar[_${poolName}_i] = $startFrame;")
    line("$timerVar[_${poolName}_i] = ${anim.frameDelay};")
}

private fun CodeGenerator.generateAnimationQueue(stmt: IRAnimationQueue) {
    val sprite = game.sprites.find { it.name == stmt.spriteName }
    if (sprite != null && sprite.hasAnimations) {
        val animConstant = "ANIM_${stmt.spriteName.uppercase()}_${stmt.animationName.uppercase()}"
        val queueVar = "_${stmt.spriteName}_queue"
        val queueLenVar = "_${stmt.spriteName}_queue_len"
        if (stmt.clearQueue) line("$queueLenVar = 0;")
        block("if ($queueLenVar < 4)") { line("$queueVar[$queueLenVar++] = $animConstant;") }
    }
}

private fun CodeGenerator.generateEntityUpdate(stmt: IREntityUpdate) {
    line("// Entity updates")
    for (entityName in stmt.entityNames) {
        val entity = game.entities.find { it.name == entityName }
        if (entity != null) {
            if (stmt.updatePosition && entity.hasSprite) {
                val sprite = entity.sprite!!
                line("move_sprite(${sprite.oamSlot}, ${entityName}_x, ${entityName}_y);")
            }
            if (stmt.updateStates && entity.hasStates) {
                line("${entityName}_update();")
            }
        }
    }
}

private fun CodeGenerator.generatePhysicsApply(stmt: IRPhysicsApply) {
    val entity = game.entities.find { it.name == stmt.entityName }
    if (entity == null) {
        line("// ERROR: Entity '${stmt.entityName}' not found for physics")
    } else if (!entity.hasPhysics) {
        line("// ERROR: Entity '${stmt.entityName}' has no physics component")
    } else {
        val name = stmt.entityName
        val nameUpper = name.uppercase()
        line("// Physics update for $name")
        line("{")
        indent++

        // Apply gravity to velocity Y (fixed-point)
        line("// Apply gravity")
        line("_${name}_vel_y_fp += ${nameUpper}_GRAVITY;")

        // Apply friction to velocity X (fixed-point multiplication)
        // friction * velX / 256 (since friction is 8.8 fixed-point)
        line("// Apply friction")
        line(
            "_${name}_vel_x_fp = (INT16)(((INT32)_${name}_vel_x_fp * ${nameUpper}_FRICTION) >> 8);"
        )

        // Clamp velocity to max bounds (convert max to fixed-point: max * 256)
        line("// Clamp velocity")
        line(
            "if (_${name}_vel_x_fp > (${nameUpper}_MAX_VEL_X << 8)) _${name}_vel_x_fp = ${nameUpper}_MAX_VEL_X << 8;"
        )
        line(
            "if (_${name}_vel_x_fp < -(${nameUpper}_MAX_VEL_X << 8)) _${name}_vel_x_fp = -(${nameUpper}_MAX_VEL_X << 8);"
        )
        line(
            "if (_${name}_vel_y_fp > (${nameUpper}_MAX_VEL_Y << 8)) _${name}_vel_y_fp = ${nameUpper}_MAX_VEL_Y << 8;"
        )
        line(
            "if (_${name}_vel_y_fp < -(${nameUpper}_MAX_VEL_Y << 8)) _${name}_vel_y_fp = -(${nameUpper}_MAX_VEL_Y << 8);"
        )

        // Update position from velocity (shift right 8 to get integer part)
        line("// Update position from velocity")
        line("${name}_x += (_${name}_vel_x_fp >> 8);")
        line("${name}_y += (_${name}_vel_y_fp >> 8);")

        // Also update the i8 velocity variables to match (for DSL access)
        line("// Sync i8 velocity variables")
        line("${name}_vel_x = (_${name}_vel_x_fp >> 8);")
        line("${name}_vel_y = (_${name}_vel_y_fp >> 8);")

        indent--
        line("}")
    }
}

private fun CodeGenerator.generateDialogShow(stmt: IRDialogShow) {
    val name = stmt.dialogName
    val dialog = game.dialogs.find { it.name == name }
    val x = dialog?.box?.x ?: 0
    val y = dialog?.box?.y ?: 14
    val w = dialog?.box?.width ?: 20
    val h = dialog?.box?.height ?: 4
    line("_dialog_draw_box($x, $y, $w, $h);")
    line("_${name}_visible = 1;")
}

private fun CodeGenerator.generateDialogHide(stmt: IRDialogHide) {
    val name = stmt.dialogName
    val dialog = game.dialogs.find { it.name == name }
    val x = dialog?.box?.x ?: 0
    val y = dialog?.box?.y ?: 14
    val w = dialog?.box?.width ?: 20
    val h = dialog?.box?.height ?: 4
    line("_dialog_clear_box($x, $y, $w, $h);")
    line("_${name}_visible = 0;")
}

private fun CodeGenerator.generateDialogSay(stmt: IRDialogSay) {
    val name = stmt.dialogName ?: "dialog"
    val dialog = if (stmt.dialogName == null) null else game.dialogs.find { it.name == name }
    val x = dialog?.box?.x ?: 0
    val y = dialog?.box?.y ?: 14
    val w = dialog?.box?.width ?: 20
    val h = dialog?.box?.height ?: 4

    val formatParts = mutableListOf<String>()
    val args = mutableListOf<String>()
    stmt.speaker?.let { formatParts.add("$it: ") }
    for (part in stmt.text) {
        when (part) {
            is TextPart.Literal -> formatParts.add(part.text.replace("\"", "\\\""))
            is TextPart.Variable -> {
                formatParts.add(part.format)
                args.add(generateExpr(part.expr))
            }
        }
    }
    val format = formatParts.joinToString("")
    // Use the correct buffer size constant based on dialog name
    val bufferSizeConst =
        if (name == "dialog") "DIALOG_BUFFER_SIZE" else "${name.uppercase()}_BUFFER_SIZE"
    if (args.isEmpty()) {
        line("// Show dialog text (using safe copy with buffer size limit)")
        line("strncpy(_${name}_buffer, \"$format\", $bufferSizeConst - 1);")
        line("_${name}_buffer[$bufferSizeConst - 1] = '\\0';")
    } else {
        line("// Show dialog text with variables (using safe snprintf)")
        line(
            "snprintf(_${name}_buffer, $bufferSizeConst, \"$format\", ${args.joinToString(", ")});"
        )
    }
    line("_${name}_text_len = strlen(_${name}_buffer);")
    line("_${name}_text_pos = 0;")
    line("_${name}_complete = 0;")
    line("_dialog_draw_box($x, $y, $w, $h);")
    line("_${name}_visible = 1;")
    if (stmt.waitForInput) line("_${name}_waiting = 1;")
}

private fun CodeGenerator.generateDialogChoice(stmt: IRDialogChoice) {
    val name = stmt.dialogName
    val dialog = game.dialogs.find { it.name == name }
    val x = dialog?.box?.x ?: 0
    val y = dialog?.box?.y ?: 14
    line("// Show choice menu")
    line("_${name}_choice = 0;")
    line("_${name}_choice_count = ${stmt.options.size};")
    stmt.options.forEachIndexed { i, option ->
        line("gotoxy(${x + 2}, ${y + 1 + i});")
        line("printf(\"$option\");")
    }
}

private fun CodeGenerator.generateDialogTick(stmt: IRDialogTick) {
    val name = stmt.dialogName
    val dialog = game.dialogs.find { it.name == name }
    val x = dialog?.box?.x ?: 0
    val y = dialog?.box?.y ?: 14
    val w = dialog?.box?.width ?: 20
    val speed = dialog?.typewriter?.charsPerFrame ?: 2
    line("// Typewriter tick")
    line("if (_${name}_text_pos < _${name}_text_len) {")
    indent++
    line(
        "_dialog_typewriter_tick(_${name}_buffer, &_${name}_text_pos, _${name}_text_len, $speed, $x, $y, $w);"
    )
    indent--
    line("} else if (!_${name}_complete) {")
    indent++
    line("_${name}_complete = 1;")
    indent--
    line("}")
    line("// Check for advance input")
    line("if (_${name}_complete && _${name}_waiting && _dialog_check_advance()) {")
    indent++
    line("_${name}_visible = 0;")
    line("_${name}_waiting = 0;")
    indent--
    line("}")
}

private fun CodeGenerator.generateTransitionWipe(stmt: IRTransitionWipe) {
    val transType =
        when (stmt.direction) {
            WipeDirection.LEFT -> "TRANS_WIPE_L"
            WipeDirection.RIGHT -> "TRANS_WIPE_R"
            WipeDirection.UP -> "TRANS_WIPE_U"
            WipeDirection.DOWN -> "TRANS_WIPE_D"
        }
    line("_transition_type = $transType;")
    line(RESET_TRANSITION_TIMER)
    line("_transition_duration = ${stmt.durationFrames};")
    if (stmt.onComplete.isNotEmpty()) {
        line("_transition_callback = ${getTransitionCallbackId(stmt.onComplete)};")
    }
}

private fun CodeGenerator.generateTransitionIris(stmt: IRTransitionIris) {
    val transType =
        when (stmt.type) {
            IrisType.CLOSE -> "TRANS_IRIS_IN"
            IrisType.OPEN -> "TRANS_IRIS_OUT"
        }
    line("_transition_type = $transType;")
    line(RESET_TRANSITION_TIMER)
    line("_transition_duration = ${stmt.durationFrames};")
    line("_transition_center_x = ${generateExpr(stmt.centerX)};")
    line("_transition_center_y = ${generateExpr(stmt.centerY)};")
    if (stmt.onComplete.isNotEmpty()) {
        line("_transition_callback = ${getTransitionCallbackId(stmt.onComplete)};")
    }
}

private fun CodeGenerator.generatePathFind(stmt: IRPathFind) {
    val diagonal = if (stmt.options.diagonal) "1" else "0"
    val heuristic =
        when (stmt.options.heuristic) {
            Heuristic.MANHATTAN -> "0"
            Heuristic.CHEBYSHEV -> "1"
            Heuristic.EUCLIDEAN -> "2"
        }
    line(
        "_astar_find_path(${stmt.gridName}_navgrid, ${generateExpr(stmt.startX)}, ${generateExpr(stmt.startY)}, ${generateExpr(stmt.endX)}, ${generateExpr(stmt.endY)}, $diagonal, ${stmt.options.maxDepth}, $heuristic);"
    )
}

private fun CodeGenerator.generatePathFollow(stmt: IRPathFollow) {
    block("if (_path_found && _path_current < _path_length)") {
        line("INT8 dir_x = _path_direction_x(${stmt.entityXVar});")
        line("INT8 dir_y = _path_direction_y(${stmt.entityYVar});")
        line("${stmt.entityXVar} += dir_x * ${stmt.speed};")
        line("${stmt.entityYVar} += dir_y * ${stmt.speed};")
        block("if (_path_at_waypoint(${stmt.entityXVar}, ${stmt.entityYVar}, 4))") {
            line("_path_advance();")
            block("if (_path_current >= _path_length)") {
                stmt.onComplete.forEach { generateStatement(it) }
            }
        }
    }
    if (stmt.onBlocked.isNotEmpty()) {
        block("else if (!_path_found)") { stmt.onBlocked.forEach { generateStatement(it) } }
    }
}

// =============================================================================
// CATEGORY HANDLER FUNCTIONS
// =============================================================================

/** Handle sound and music related statements. Returns true if the statement was handled. */
private fun CodeGenerator.generateSoundMusicStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRSoundPlay -> generateSoundPlay(stmt)
        is IRSoundStop -> generateSoundStop(stmt)
        is IRMusicPlay -> {
            line("hUGE_init(&${stmt.musicName}_song);")
            line("_music_playing = 1;")
        }
        is IRMusicStop -> {
            line("NR12_REG = 0; NR22_REG = 0; NR30_REG = 0; NR42_REG = 0;")
            line("_music_playing = 0;")
        }
        is IRMusicPause -> line("_music_playing = 0;")
        is IRMusicResume -> line("_music_playing = 1;")
        is IRMusicFade -> {
            line("// Start music fade over ${stmt.frames} frames")
            line("_music_fade_timer = ${stmt.frames};")
            line("_music_fade_duration = ${stmt.frames};")
            line("_music_fade_start_vol = 7;  // Max volume")
        }
        is IRSoundMasterVolume -> {
            val vol = (stmt.volume shl 4) or stmt.volume
            line("NR50_REG = 0x${vol.toString(16).padStart(2, '0').uppercase()};")
        }
        is IRSoundEnable -> line("NR52_REG = ${if (stmt.enable) "0x80" else "0x00"};")
        is IRSoundPan -> generateSoundPan(stmt)
        is IRSoundMuteChannel -> {
            if (game.music.isNotEmpty()) {
                line(
                    "hUGE_mute_channel(HT_CH${stmt.channel.index + 1}, ${if (stmt.mute) 1 else 0});"
                )
            }
        }
        is IRMixerSetVolume,
        is IRMixerFade,
        is IRMixerMute,
        is IRMixerToggleMute,
        is IRMixerPriorityCheck -> generateMixerStatement(stmt)
        else -> return false
    }
    return true
}

/** Handle palette and display related statements. */
private fun CodeGenerator.generateDisplayStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRPaletteApply -> generatePaletteApply(stmt)
        is IRPaletteSetColor -> generatePaletteSetColor(stmt)
        is IRPaletteFlash -> generatePaletteFlash(stmt)
        is IRPaletteFade -> generatePaletteFade(stmt)
        is IRSpriteSetPalette -> {
            if (game.config.gbcSupport) {
                line("set_sprite_prop(${stmt.spriteSlot}, ${stmt.paletteIndex} & 0x07);")
            }
        }
        is IRClearScreen -> line("cls();")
        is IRShowSprites -> line(if (stmt.show) "SHOW_SPRITES;" else "HIDE_SPRITES;")
        is IRShowBackground -> line(if (stmt.show) "SHOW_BKG;" else "HIDE_BKG;")
        is IRPrintAt -> generatePrintAt(stmt)
        else -> return false
    }
    return true
}

/** Handle animation related statements. */
private fun CodeGenerator.generateAnimationStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRAnimationPlay -> generateAnimationPlay(stmt)
        is IRAnimationStop -> {
            line("_${stmt.spriteName}_anim = ANIM_NONE;")
            line("_${stmt.spriteName}_flags = 0;")
        }
        is IRAnimationPause -> line("_${stmt.spriteName}_flags |= ANIM_FLAG_PAUSED;")
        is IRAnimationResume -> line("_${stmt.spriteName}_flags &= ~ANIM_FLAG_PAUSED;")
        is IRAnimationSetSpeed -> line("_${stmt.spriteName}_speed = ${stmt.speed};")
        is IRAnimationQueue -> generateAnimationQueue(stmt)
        is IRAnimationSetFrame -> {
            val sprite = game.sprites.find { it.name == stmt.spriteName }
            if (sprite != null) {
                line("set_sprite_tile(${sprite.oamSlot}, ${stmt.frameIndex});")
            }
        }
        is IRStateMachineStart -> {
            val stateVar = "_${stmt.machineName}_state"
            val nextVar = "_${stmt.machineName}_next"
            val changedVar = "_${stmt.machineName}_changed"
            line(
                "$stateVar = STATE_${stmt.machineName.uppercase()}_${stmt.initialState.uppercase()};"
            )
            line("$nextVar = $stateVar;")
            line("$changedVar = 1;")
        }
        is IRStateMachineGoto -> {
            val nextVar = "_${stmt.machineName}_next"
            val changedVar = "_${stmt.machineName}_changed"
            line(
                "$nextVar = STATE_${stmt.machineName.uppercase()}_${stmt.targetState.uppercase()};"
            )
            line("$changedVar = 1;")
        }
        is IRStateMachineUpdate -> line("${stmt.machineName}_update();")
        is IREntityUpdate -> generateEntityUpdate(stmt)
        else -> return false
    }
    return true
}

/** Handle save-related statements. */
private fun CodeGenerator.generateSaveStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRSaveLoad -> line("${stmt.saveName}_load(${generateExpr(stmt.slot)});")
        is IRSaveSave -> line("${stmt.saveName}_save(${generateExpr(stmt.slot)});")
        is IRSaveErase -> line("${stmt.saveName}_erase(${generateExpr(stmt.slot)});")
        is IRSaveCopy ->
            line(
                "${stmt.saveName}_copy(${generateExpr(stmt.fromSlot)}, ${generateExpr(stmt.toSlot)});"
            )
        is IRSaveFieldWrite ->
            line("${stmt.saveName}_data.${stmt.fieldName} = ${generateExpr(stmt.value)};")
        is IRSaveArrayWrite ->
            line(
                "${stmt.saveName}_data.${stmt.fieldName}[${generateExpr(stmt.index)}] = ${generateExpr(stmt.value)};"
            )
        else -> return false
    }
    return true
}

/** Handle dialog-related statements. */
private fun CodeGenerator.generateDialogStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRDialogShow -> generateDialogShow(stmt)
        is IRDialogHide -> generateDialogHide(stmt)
        is IRDialogSay -> generateDialogSay(stmt)
        is IRDialogChoice -> generateDialogChoice(stmt)
        is IRDialogTick -> generateDialogTick(stmt)
        else -> return false
    }
    return true
}

/** Handle menu-related statements. */
private fun CodeGenerator.generateMenuStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRMenuShow,
        is IRMenuOpen -> {
            val menuName =
                when (stmt) {
                    is IRMenuShow -> stmt.menuName
                    is IRMenuOpen -> stmt.menuName
                    else -> error("Unexpected")
                }
            val menu = game.menus.find { it.name == menuName }
            showMenu(menuName, menu?.isGrid == true)
        }
        is IRMenuHide -> hideMenu(stmt.menuName)
        is IRMenuToggle -> {
            val menu = game.menus.find { it.name == stmt.menuName }
            line("if (_${stmt.menuName}_visible) {")
            indent++
            hideMenu(stmt.menuName)
            indent--
            line("} else {")
            indent++
            showMenu(stmt.menuName, menu?.isGrid == true)
            indent--
            line("}")
        }
        is IRMenuTick -> line("_${stmt.menuName}_tick();")
        is IRMenuMoveTo -> {
            val menu = game.menus.find { it.name == stmt.menuName }
            val indexExpr = generateExpr(stmt.index)
            if (menu?.isGrid == true) {
                val cols = menu.columns
                line("_${stmt.menuName}_cursor_x = ($indexExpr) % $cols;")
                line("_${stmt.menuName}_cursor_y = ($indexExpr) / $cols;")
            } else {
                line("_${stmt.menuName}_cursor = $indexExpr;")
            }
            line("_${stmt.menuName}_draw();")
        }
        is IRMenuSelect -> {
            val menu = game.menus.find { it.name == stmt.menuName }
            if (menu?.isGrid == true) {
                line(
                    "_${stmt.menuName}_do_select(_${stmt.menuName}_cursor_y * ${stmt.menuName.uppercase()}_COLS + _${stmt.menuName}_cursor_x);"
                )
            } else {
                line("_${stmt.menuName}_do_select(_${stmt.menuName}_cursor);")
            }
        }
        is IRMenuCancel -> hideMenu(stmt.menuName)
        is IRMenuClose -> line("// Close current menu (handled by tick function)")
        else -> return false
    }
    return true
}

/** Handle pool-related statements. */
private fun CodeGenerator.generatePoolStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRPoolUpdate -> line("${stmt.poolName}_update();")
        is IRPoolSpawn -> {
            val indexVar = "_${stmt.poolName}_i"
            line("{")
            indent++
            line("UINT8 $indexVar = ${stmt.poolName}_spawn();")
            block("if ($indexVar != 255)") { stmt.initStatements.forEach { generateStatement(it) } }
            indent--
            line("}")
        }
        is IRPoolSpawnAt -> {
            val indexVar = "_${stmt.poolName}_i"
            line("{")
            indent++
            line("UINT8 $indexVar = ${stmt.poolName}_spawn();")
            block("if ($indexVar != 255)") {
                line("${stmt.poolName}_x[$indexVar] = ${generateExpr(stmt.x)};")
                line("${stmt.poolName}_y[$indexVar] = ${generateExpr(stmt.y)};")
                stmt.initStatements.forEach { generateStatement(it) }
            }
            indent--
            line("}")
        }
        is IRPoolTrySpawn -> {
            val indexVar = "_${stmt.poolName}_i"
            line("{")
            indent++
            line("UINT8 $indexVar = ${stmt.poolName}_spawn();")
            block("if ($indexVar != 255)") { stmt.initStatements.forEach { generateStatement(it) } }
            block("else") { stmt.elseStatements.forEach { generateStatement(it) } }
            indent--
            line("}")
        }
        is IRPoolDespawn -> line("${stmt.poolName}_despawn(${generateExpr(stmt.indexExpr)});")
        is IRPoolDespawnAll -> line("${stmt.poolName}_despawn_all();")
        is IRPoolForEach -> {
            val pool = game.pools.find { it.name == stmt.poolName }
            val size = pool?.size ?: 8
            block(
                "for (UINT8 ${stmt.indexVar} = 0; ${stmt.indexVar} < $size; ${stmt.indexVar}++)"
            ) {
                block("if (${stmt.poolName}_active[${stmt.indexVar}])") {
                    stmt.bodyStatements.forEach { generateStatement(it) }
                }
            }
        }
        is IRPoolDespawnWhere -> {
            val pool = game.pools.find { it.name == stmt.poolName }
            val size = pool?.size ?: 8
            block(
                "for (INT16 ${stmt.indexVar} = ${size - 1}; ${stmt.indexVar} >= 0; ${stmt.indexVar}--)"
            ) {
                val cond = generateExpr(stmt.condition)
                block("if (${stmt.poolName}_active[${stmt.indexVar}] && ($cond))") {
                    line("${stmt.poolName}_despawn(${stmt.indexVar});")
                }
            }
        }
        is IRPoolPathSetTarget -> {
            line("${stmt.poolName}_target_x = ${generateExpr(stmt.targetX)};")
            line("${stmt.poolName}_target_y = ${generateExpr(stmt.targetY)};")
        }
        is IRPoolPathFollow -> line("_pool_path_update_${stmt.poolName}();")
        is IRPoolPathRecalc -> line("_pool_path_recalc_${stmt.poolName}(${stmt.entityIndex});")
        else -> return false
    }
    return true
}

/** Handle camera-related statements. */
private fun CodeGenerator.generateCameraStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRCameraUpdate -> line("_camera_update();")
        is IRCameraSetPosition -> {
            line("_camera_x = ${generateExpr(stmt.x)};")
            line("_camera_y = ${generateExpr(stmt.y)};")
        }
        is IRCameraFollow -> {
            line("_camera_follow_active = 1;")
            line("_camera_follow_x = &${stmt.targetXVar};")
            line("_camera_follow_y = &${stmt.targetYVar};")
            line("_camera_offset_x = ${stmt.offsetX};")
            line("_camera_offset_y = ${stmt.offsetY};")
            line("_camera_smoothing = ${stmt.smoothing};")
        }
        is IRCameraStopFollow -> line("_camera_follow_active = 0;")
        is IRCameraSnapTo -> {
            val x = generateExpr(stmt.x)
            val y = generateExpr(stmt.y)
            line("_camera_x = $x;")
            line("_camera_y = $y;")
            line("_camera_target_x = $x;")
            line("_camera_target_y = $y;")
        }
        is IRCameraSetBounds -> {
            line("_camera_bounds_min_x = ${stmt.minX};")
            line("_camera_bounds_max_x = ${stmt.maxX};")
            line("_camera_bounds_min_y = ${stmt.minY};")
            line("_camera_bounds_max_y = ${stmt.maxY};")
        }
        is IRCameraShake -> {
            line("_shake_intensity = ${stmt.intensity};")
            line("_shake_timer = ${stmt.durationFrames};")
            line("_shake_decay = ${stmt.decay.ordinal};")
        }
        is IRCameraShakeStop -> {
            line("_shake_intensity = 0;")
            line("_shake_timer = 0;")
            line("_shake_offset_x = 0;")
            line("_shake_offset_y = 0;")
        }
        else -> return false
    }
    return true
}

/** Handle transition-related statements. */
private fun CodeGenerator.generateTransitionStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRTransitionFadeOut -> {
            line("_transition_type = TRANS_FADE_OUT;")
            line(RESET_TRANSITION_TIMER)
            line("_transition_duration = ${stmt.durationFrames};")
            if (stmt.onComplete.isNotEmpty()) {
                line("_transition_callback = ${getTransitionCallbackId(stmt.onComplete)};")
            }
        }
        is IRTransitionFadeIn -> {
            line("_transition_type = TRANS_FADE_IN;")
            line(RESET_TRANSITION_TIMER)
            line("_transition_duration = ${stmt.durationFrames};")
            if (stmt.onComplete.isNotEmpty()) {
                line("_transition_callback = ${getTransitionCallbackId(stmt.onComplete)};")
            }
        }
        is IRTransitionFlash -> {
            line("_transition_type = TRANS_FLASH;")
            line(RESET_TRANSITION_TIMER)
            line("_transition_duration = ${stmt.durationFrames};")
            line("_transition_flash_color = ${stmt.color.rgb555};")
        }
        is IRTransitionWipe -> generateTransitionWipe(stmt)
        is IRTransitionIris -> generateTransitionIris(stmt)
        is IRComposedTransition -> generateComposedTransition(stmt.transition, stmt.targetScene)
        is IRTransitionCancel -> {
            line("_transition_type = TRANS_NONE;")
            line(RESET_TRANSITION_TIMER)
            line("_trans_seq_active = 0;")
            line("BGP_REG = 0xE4;  // Restore default palette")
        }
        else -> return false
    }
    return true
}

/** Handle pathfinding-related statements. */
private fun CodeGenerator.generatePathfindingStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        is IRNavGridInit -> {
            /* Handled at compile time */
        }
        is IRNavGridSetTile -> {
            val walkable = if (stmt.walkable) "1" else "0"
            line(
                "_navgrid_set_tile(${stmt.gridName}_navgrid, ${generateExpr(stmt.x)}, ${generateExpr(stmt.y)}, $walkable);"
            )
        }
        is IRNavGridSetWeight -> {
            line(
                "_navgrid_set_weight(${stmt.gridName}_navgrid, ${generateExpr(stmt.x)}, ${generateExpr(stmt.y)}, ${stmt.weight});"
            )
        }
        is IRPathFind -> generatePathFind(stmt)
        is IRPathAdvance -> line("_path_advance();")
        is IRPathReset -> line("_path_current = 0;")
        is IRPathFollow -> generatePathFollow(stmt)
        else -> return false
    }
    return true
}

/** Handle miscellaneous statements (input, link, cutscene, tween, physics). */
private fun CodeGenerator.generateMiscStatement(stmt: IRStatement): Boolean {
    when (stmt) {
        // Input buffer statements
        is IRInputBufferDecl -> {
            /* Declaration is handled in generateVariables */
        }
        is IRInputBufferReset -> line("${stmt.bufferName} = 0;")
        is IRInputBufferFill -> line("${stmt.bufferName} = ${stmt.frames};")
        // Link cable statements
        is IRLinkInit -> line("_link_init();")
        is IRLinkUpdate -> line("_link_update();")
        is IRLinkSend -> line("_link_send(${generateExpr(stmt.data)});")
        // Cutscene statements
        is IRCutsceneStart -> line("_${stmt.cutsceneName}_start();")
        is IRCutsceneUpdate -> line("_${stmt.cutsceneName}_update();")
        is IRCutsceneSkip -> line("_${stmt.cutsceneName}_skip();")
        // Tween statements
        is IRTween -> generateTweenStart(stmt)
        // Physics world statements
        is IRPhysicsWorldUpdate -> line("_physics_world_update();")
        is IRPhysicsApply -> generatePhysicsApply(stmt)
        is IRCollisionResponse -> {
            line("// Collision response: ${stmt.tag1} <-> ${stmt.tag2}")
        }
        else -> return false
    }
    return true
}
