/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRUnary
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.UnaryOp

// =============================================================================
// JOYPAD - Clean, property-based input handling
// =============================================================================

/** State for a single d-pad direction with held/pressed/released detection. */
class DpadDirectionState(private val btn: Button) {
    /** True while direction is held down */
    val held: Condition
        get() = buttonHeld(btn)

    /** True only on the frame the direction was pressed */
    val pressed: Condition
        get() = buttonPressed(btn)

    /** True only on the frame the direction was released */
    val released: Condition
        get() = buttonReleased(btn)

    // Implicit conversion to Condition (defaults to held)
    fun toCondition() = held

    /** Get the direction's hardware mask */
    val buttonMask: Int
        get() = btn.mask
}

// Allow using DpadDirectionState directly in conditions
infix fun DpadDirectionState.and(other: Condition) = this.held and other

infix fun DpadDirectionState.or(other: Condition) = this.held or other

infix fun Condition.and(other: DpadDirectionState) = this and other.held

infix fun Condition.or(other: DpadDirectionState) = this or other.held

infix fun DpadDirectionState.and(other: DpadDirectionState) = this.held and other.held

infix fun DpadDirectionState.or(other: DpadDirectionState) = this.held or other.held

infix fun DpadDirectionState.and(other: ButtonState) = this.held and other.held

infix fun DpadDirectionState.or(other: ButtonState) = this.held or other.held

infix fun ButtonState.and(other: DpadDirectionState) = this.held and other.held

infix fun ButtonState.or(other: DpadDirectionState) = this.held or other.held

/**
 * D-pad state with directional properties.
 *
 * Usage:
 * ```kotlin
 * whenever(dpad.right.held) { player.x += 2 }       // Explicit .held
 * whenever(dpad.right) { player.x += 2 }            // Implicit .held (backward compatible)
 * whenever(dpad.left.pressed) { dash() }            // Edge-triggered
 * playerX += dpad.x * speed                         // Axis value: -1, 0, or 1
 * ```
 */
object dpad {
    /** Left direction with .held, .pressed, .released */
    val left = DpadDirectionState(Button.LEFT)

    /** Right direction with .held, .pressed, .released */
    val right = DpadDirectionState(Button.RIGHT)

    /** Up direction with .held, .pressed, .released */
    val up = DpadDirectionState(Button.UP)

    /** Down direction with .held, .pressed, .released */
    val down = DpadDirectionState(Button.DOWN)

    /** Horizontal axis: -1 (left), 0 (none), +1 (right) Perfect for: playerX += dpad.x * speed */
    val x: Expr
        get() =
            Expr(
                IRTernary(
                    buttonHeld(Button.LEFT).ir,
                    IRLiteral(-1),
                    IRTernary(buttonHeld(Button.RIGHT).ir, IRLiteral(1), IRLiteral(0))
                )
            )

    /** Vertical axis: -1 (up), 0 (none), +1 (down) Note: In GB screen coords, down is +Y */
    val y: Expr
        get() =
            Expr(
                IRTernary(
                    buttonHeld(Button.UP).ir,
                    IRLiteral(-1),
                    IRTernary(buttonHeld(Button.DOWN).ir, IRLiteral(1), IRLiteral(0))
                )
            )

    /** True if any direction is held */
    val any: Condition
        get() = left or right or up or down

    /** True if no direction is held */
    val none: Condition
        get() = !any
}

/**
 * Button state for A, B, Start, Select, plus d-pad access for unified API.
 *
 * Usage:
 * ```kotlin
 * whenever(buttons.a.pressed) { jump() }
 * whenever(buttons.start.pressed) { scene(pauseScene) }
 * whenever(buttons.dpad.left.pressed) { dash() }  // Unified API
 * ```
 */
object buttons {
    val a = ButtonState(Button.A)
    val b = ButtonState(Button.B)
    val start = ButtonState(Button.START)
    val select = ButtonState(Button.SELECT)

    /** D-pad access through buttons object for unified API */
    val dpad = io.github.gbkt.core.dpad
}

/** State for a single button with held/pressed/released detection. */
class ButtonState(private val btn: Button) {
    /** True while button is held down */
    val held: Condition
        get() = buttonHeld(btn)

    /** True only on the frame the button was pressed */
    val pressed: Condition
        get() = buttonPressed(btn)

    /** True only on the frame the button was released */
    val released: Condition
        get() = buttonReleased(btn)

    // Implicit conversion to Condition (defaults to held)
    fun toCondition() = held

    /** Get the button's hardware mask */
    val buttonMask: Int
        get() = btn.mask
}

// Allow using ButtonState directly in conditions
infix fun ButtonState.and(other: Condition) = this.held and other

infix fun ButtonState.or(other: Condition) = this.held or other

infix fun Condition.and(other: ButtonState) = this and other.held

infix fun Condition.or(other: ButtonState) = this or other.held

enum class Button(val mask: Int, val gbdkName: String) {
    A(0x10, "J_A"),
    B(0x20, "J_B"),
    SELECT(0x40, "J_SELECT"),
    START(0x80, "J_START"),
    RIGHT(0x01, "J_RIGHT"),
    LEFT(0x02, "J_LEFT"),
    UP(0x04, "J_UP"),
    DOWN(0x08, "J_DOWN")
}

// Internal helpers that generate IR
private fun buttonHeld(btn: Button): Condition {
    return Condition(IRBinary(IRCallExpr("joypad", emptyList()), BinaryOp.AND, IRLiteral(btn.mask)))
}

private fun buttonPressed(btn: Button): Condition {
    // (current & mask) && !(previous & mask)
    return Condition(
        IRBinary(
            IRBinary(IRVar("_joypad"), BinaryOp.AND, IRLiteral(btn.mask)),
            BinaryOp.LAND,
            IRUnary(UnaryOp.NOT, IRBinary(IRVar("_joypad_prev"), BinaryOp.AND, IRLiteral(btn.mask)))
        )
    )
}

private fun buttonReleased(btn: Button): Condition {
    // !(current & mask) && (previous & mask)
    return Condition(
        IRBinary(
            IRUnary(UnaryOp.NOT, IRBinary(IRVar("_joypad"), BinaryOp.AND, IRLiteral(btn.mask))),
            BinaryOp.LAND,
            IRBinary(IRVar("_joypad_prev"), BinaryOp.AND, IRLiteral(btn.mask))
        )
    )
}

// =============================================================================
// INPUT COMBINATIONS - For fighting game style inputs
// =============================================================================

/**
 * Check multiple buttons at once.
 *
 * Usage: if (buttons(button.a, button.b).allHeld) { ... } // A+B combo if (buttons(dpad.down,
 * button.a).allPressed) { ... } // Down+A
 */
fun buttons(vararg conditions: Condition): ButtonCombo {
    return ButtonCombo(conditions.toList())
}

class ButtonCombo(private val conditions: List<Condition>) {
    /** True if all buttons are currently held */
    val allHeld: Condition
        get() = conditions.reduce { acc, c -> acc and c }

    /** True if any button is held */
    val anyHeld: Condition
        get() = conditions.reduce { acc, c -> acc or c }
}

// =============================================================================
// DIRECTIONAL HELPERS
// =============================================================================

/**
 * Get the facing direction based on last d-pad input. Useful for sprite flipping.
 *
 * Returns: -1 (left), 0 (neutral), 1 (right)
 */
val facing: Expr
    get() = dpad.x

/** Check if moving in a specific direction. */
val movingLeft: Condition
    get() = dpad.left.held
val movingRight: Condition
    get() = dpad.right.held
val movingUp: Condition
    get() = dpad.up.held
val movingDown: Condition
    get() = dpad.down.held
val moving: Condition
    get() = dpad.any
val stationary: Condition
    get() = dpad.none
