/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRDialogChoice
import io.github.gbkt.core.ir.IRDialogHide
import io.github.gbkt.core.ir.IRDialogIsActive
import io.github.gbkt.core.ir.IRDialogIsComplete
import io.github.gbkt.core.ir.IRDialogSay
import io.github.gbkt.core.ir.IRDialogShow
import io.github.gbkt.core.ir.IRDialogTick
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.TextPart

// =============================================================================
// DIALOG SYSTEM DSL
// Addictive, Compose-like developer experience for Game Boy text boxes
//
// Quick inline:   say("You found a key!")
// Named dialogs:  val npc = dialog("npc") { ... }
//                 npc.say("Hello!")
// =============================================================================

// =============================================================================
// BORDER STYLES
// =============================================================================

/** Visual border styles for dialog boxes. */
enum class BorderStyle {
    /** No border - text only */
    NONE,
    /** Basic ASCII border: +--+ */
    SIMPLE,
    /** Rounded corners (requires custom tiles) */
    ROUNDED,
    /** Double-line border (requires custom tiles) */
    DOUBLE,
}

// =============================================================================
// CONFIGURATION DATA CLASSES
// =============================================================================

/** Dialog box positioning and sizing. */
data class DialogBoxConfig(
    val x: Int = 0,
    val y: Int = 14, // Bottom of screen by default
    val width: Int = 20, // Full screen width
    val height: Int = 4, // 4 tile rows
    val borderStyle: BorderStyle = BorderStyle.SIMPLE,
    val padding: Int = 1, // Padding inside border for text
)

/** Typewriter effect configuration. */
data class TypewriterConfig(
    val charsPerFrame: Int = 2, // Characters revealed per frame
    val soundOnChar: String? = null, // Optional SFX per character
)

/** Portrait configuration for RPG-style dialogs. */
data class PortraitConfig(
    val asset: String,
    val width: Int = 32, // 4 tiles
    val height: Int = 32, // 4 tiles
    val position: PortraitPosition = PortraitPosition.LEFT,
)

enum class PortraitPosition {
    LEFT,
    RIGHT,
}

/** Complete dialog definition combining all configuration. */
data class DialogDefinition(
    val name: String,
    val box: DialogBoxConfig = DialogBoxConfig(),
    val typewriter: TypewriterConfig = TypewriterConfig(),
    val portrait: PortraitConfig? = null,
    val defaultSpeaker: String? = null,
)

// =============================================================================
// DSL BUILDERS
// =============================================================================

/**
 * Builder for dialog box configuration.
 *
 * Usage: box { position(0, 12) size = 20 x 4 border = BorderStyle.ROUNDED padding = 1 }
 */
@GbktDsl
class DialogBoxBuilder {
    private var x = 0
    private var y = 14
    private var width = 20
    private var height = 4

    /** Border style */
    var border = BorderStyle.SIMPLE

    /** Padding inside the border */
    var padding = 1

    /** Set box position in tile coordinates */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set box size using Dimensions syntax: size = 20 x 4 */
    var size: Dimensions = Dimensions(20, 4)
        set(value) {
            width = value.width
            height = value.height
            field = value
        }

    internal fun build() = DialogBoxConfig(x, y, width, height, border, padding)
}

/**
 * Builder for portrait configuration.
 *
 * Usage: portrait("npc.png") { size = 32 x 32 position = PortraitPosition.LEFT }
 */
@GbktDsl
class PortraitBuilder(private val asset: String) {
    /** Portrait size in pixels (must be 8-pixel aligned) */
    var size: Dimensions = Dimensions(32, 32)

    /** Portrait position relative to text box */
    var position = PortraitPosition.LEFT

    internal fun build() = PortraitConfig(asset, size.width, size.height, position)
}

/**
 * Main builder for defining a named dialog.
 *
 * Usage: val shopkeeper = dialog("shopkeeper") { portrait("shopkeeper.png") speaker = "Merchant"
 * textSpeed = 3 box { position(0, 10) size = 20 x 6 border = BorderStyle.ROUNDED } }
 */
@GbktDsl
class DialogBuilder(private val name: String) {
    private var boxConfig = DialogBoxConfig()
    private var typewriterConfig = TypewriterConfig()
    private var portraitConfig: PortraitConfig? = null
    private var _speaker: String? = null

    /** Configure the text box appearance and position */
    fun box(init: DialogBoxBuilder.() -> Unit) {
        boxConfig = DialogBoxBuilder().apply(init).build()
    }

    /**
     * Set portrait image for RPG-style dialogs.
     *
     * Usage: portrait("npc.png") portrait("npc.png") { position = PortraitPosition.RIGHT }
     */
    fun portrait(asset: String, init: PortraitBuilder.() -> Unit = {}) {
        portraitConfig = PortraitBuilder(asset).apply(init).build()
    }

    /** Text speed in characters per frame (higher = faster) */
    var textSpeed: Int
        get() = typewriterConfig.charsPerFrame
        set(value) {
            typewriterConfig = typewriterConfig.copy(charsPerFrame = value)
        }

    /** Sound effect to play on each character reveal */
    var textSound: String?
        get() = typewriterConfig.soundOnChar
        set(value) {
            typewriterConfig = typewriterConfig.copy(soundOnChar = value)
        }

    /** Default speaker name shown before text (e.g., "Elder: Hello!") */
    var speaker: String?
        get() = _speaker
        set(value) {
            _speaker = value
        }

    internal fun build() =
        DialogDefinition(name, boxConfig, typewriterConfig, portraitConfig, _speaker)
}

// =============================================================================
// DIALOG HANDLE - Runtime operations
// =============================================================================

/**
 * Handle for dialog runtime operations. Returned by dialog() DSL function and used to display text,
 * choices, etc.
 *
 * Usage: val npc = dialog("npc") { ... }
 *
 * scene("town") { enter { npc.say("Hello traveler!") npc.say("The road ahead is dangerous.")
 * npc.choice("I'm ready", "Tell me more") { selected -> whenever(selected isEqualTo 0) {
 * scene("adventure") } } } }
 */
class DialogHandle internal constructor(internal val definition: DialogDefinition) {
    /**
     * Display text in this dialog with typewriter effect.
     *
     * Usage: npc.say("Welcome to my shop!") npc.say("You have ", gold, " gold.")
     */
    fun say(vararg parts: Any): DialogSayBuilder {
        val textParts =
            parts.map { part ->
                when (part) {
                    is String -> TextPart.Literal(part)
                    is Expr -> TextPart.Variable(part.ir)
                    is AssignableExpr -> TextPart.Variable(part.ir)
                    is Int -> TextPart.Literal(part.toString())
                    else -> TextPart.Literal(part.toString())
                }
            }
        return DialogSayBuilder(definition.name, textParts, definition.defaultSpeaker)
    }

    /**
     * Display a choice menu and execute block with result.
     *
     * Usage: npc.choice("Buy", "Sell", "Leave") { selected -> whenever(selected isEqualTo 0) { /*
     * Buy logic */ } whenever(selected isEqualTo 1) { /* Sell logic */ } }
     */
    fun choice(vararg options: String, block: (Expr) -> Unit) {
        val resultVar = "_${definition.name}_choice"
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRDialogChoice(
                        dialogName = definition.name,
                        options = options.toList(),
                        resultVar = resultVar,
                    )
                )
        }
        // Execute block with result expression for conditionals
        block(Expr(IRVar(resultVar)))
    }

    /** Show the dialog box (without text) */
    fun show() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRDialogShow(definition.name))
        }
    }

    /** Hide the dialog box */
    fun hide() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRDialogHide(definition.name))
        }
    }

    /** Update typewriter animation - call in every.frame when dialog is active */
    fun tick() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRDialogTick(definition.name))
        }
    }

    /** Condition: is this dialog currently visible? */
    val isActive: Condition
        get() = Condition(IRDialogIsActive(definition.name))

    /** Condition: has the dialog finished displaying all text? */
    val isComplete: Condition
        get() = Condition(IRDialogIsComplete(definition.name))
}

/**
 * Builder for dialog say() with optional modifiers.
 *
 * Usage: npc.say("Hello!").withSpeaker("Guard") npc.say("Press A").autoAdvance(60)
 */
class DialogSayBuilder
internal constructor(
    private val dialogName: String?,
    private val textParts: List<TextPart>,
    private var speaker: String? = null,
    private var waitForInput: Boolean = true,
    private var autoAdvanceFrames: Int = 0,
) {
    init {
        emit()
    }

    private fun emit() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRDialogSay(
                        dialogName = dialogName,
                        text = textParts,
                        speaker = speaker,
                        waitForInput = waitForInput,
                        autoAdvanceFrames = autoAdvanceFrames,
                    )
                )
        }
    }

    /** Override the speaker name for this message */
    fun withSpeaker(name: String): DialogSayBuilder {
        speaker = name
        return this
    }

    /** Auto-advance after specified frames instead of waiting for input */
    fun autoAdvance(frames: Int): DialogSayBuilder {
        waitForInput = false
        autoAdvanceFrames = frames
        return this
    }

    /** Don't wait for input - immediately continue */
    fun noWait(): DialogSayBuilder {
        waitForInput = false
        return this
    }
}

// =============================================================================
// INLINE SAY FUNCTION - Zero-config quick dialogs
// =============================================================================

/**
 * Quick inline dialog - displays text at default position with default styling. Perfect for action
 * games, item pickups, and simple notifications.
 *
 * Usage: whenever(gotKey isEqualTo 1) { say("You found a key!") gotKey set 0 }
 *
 * say("Score: ", score, " points!")
 */
fun say(vararg parts: Any): DialogSayBuilder {
    val textParts =
        parts.map { part ->
            when (part) {
                is String -> TextPart.Literal(part)
                is Expr -> TextPart.Variable(part.ir)
                is AssignableExpr -> TextPart.Variable(part.ir)
                is Int -> TextPart.Literal(part.toString())
                else -> TextPart.Literal(part.toString())
            }
        }
    return DialogSayBuilder(null, textParts)
}

// =============================================================================
// WORD WRAPPING UTILITY
// =============================================================================

/**
 * Word wrap text to fit within a given character width. Returns list of lines that fit within
 * maxWidth.
 *
 * @param text The text to wrap
 * @param maxWidth Maximum characters per line
 * @return List of wrapped lines
 */
fun wordWrap(text: String, maxWidth: Int): List<String> {
    if (text.length <= maxWidth) return listOf(text)

    val words = text.split(" ")
    val lines = mutableListOf<String>()
    val currentLine = StringBuilder()

    for (word in words) {
        when {
            // Word fits on current line
            currentLine.isEmpty() -> currentLine.append(word)
            currentLine.length + 1 + word.length <= maxWidth -> {
                currentLine.append(" ").append(word)
            }
            // Word doesn't fit - start new line
            else -> {
                lines.add(currentLine.toString())
                currentLine.clear()
                currentLine.append(word)
            }
        }
    }

    if (currentLine.isNotEmpty()) {
        lines.add(currentLine.toString())
    }

    return lines
}

// =============================================================================
// IR NODES FOR DIALOG SYSTEM
// =============================================================================
// Moved to io.github.gbkt.core.ir.DialogIR
