/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRPrintAt
import io.github.gbkt.core.ir.TextPart

// =============================================================================
// TEXT RENDERING DSL
// Clean, Kotlin-native text output for Game Boy
//
// Note: screen object is defined in Game.kt with clear(), showSprites(), etc.
// =============================================================================

// =============================================================================
// PRINT DSL
// =============================================================================

/**
 * Builder for text output with position.
 *
 * Usage: print("GAME OVER") at (4, 8) print("SCORE: ", score) at (2, 10) print("HP: ", health, "/",
 * maxHealth) at (1, 1)
 */
class TextBuilder(private val parts: MutableList<TextPart> = mutableListOf()) {

    /** Position and emit the text */
    infix fun at(position: Pair<Int, Int>) {
        RecordingContext.require().emit(IRPrintAt(position.first, position.second, parts.toList()))
    }

    /** Add more parts to the text */
    operator fun plus(text: String): TextBuilder {
        parts.add(TextPart.Literal(text))
        return this
    }

    operator fun plus(expr: Expr): TextBuilder {
        parts.add(TextPart.Variable(expr.ir))
        return this
    }

    operator fun plus(value: Int): TextBuilder {
        parts.add(TextPart.Literal(value.toString()))
        return this
    }
}

/**
 * Start a text output.
 *
 * Usage: print("Hello") at (5, 5) print("Score: ", score) at (2, 10)
 */
fun print(text: String): TextBuilder {
    return TextBuilder(mutableListOf(TextPart.Literal(text)))
}

/** Print with a variable */
fun print(text: String, expr: Expr): TextBuilder {
    return TextBuilder(mutableListOf(TextPart.Literal(text), TextPart.Variable(expr.ir)))
}

/** Print with multiple parts */
fun print(vararg parts: Any): TextBuilder {
    val textParts =
        parts
            .map { part ->
                when (part) {
                    is String -> TextPart.Literal(part)
                    is Expr -> TextPart.Variable(part.ir)
                    is Int -> TextPart.Literal(part.toString())
                    else -> TextPart.Literal(part.toString())
                }
            }
            .toMutableList()
    return TextBuilder(textParts)
}

// =============================================================================
// CONVENIENCE EXTENSIONS
// =============================================================================

/** Create a position pair: 5 x 10 -> Pair(5, 10) */
// Note: Already defined in Core.kt as Dimensions, but we use Pair for positions
infix fun Int.at(y: Int): Pair<Int, Int> = Pair(this, y)

/**
 * Centered text helper.
 *
 * Usage: printCentered("GAME OVER") at 8 printCentered("PRESS START") at 12
 */
class CenteredTextBuilder(private val text: String) {
    infix fun at(row: Int) {
        val x = (screen.tileWidth - text.length) / 2
        RecordingContext.require().emit(IRPrintAt(x, row, listOf(TextPart.Literal(text))))
    }
}

fun printCentered(text: String) = CenteredTextBuilder(text)
