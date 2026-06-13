/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("ClassNaming") // `dpad` and `buttons` are lowercase singleton objects by design

package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.VarRef

/**
 * Typed reference to a hardware input (d-pad direction or button).
 *
 * Wraps a GBDK joypad constant name (e.g. "J_UP", "J_A") and produces [Expr] instances for use in
 * [ScriptBuilder.runIf] conditions. Use [held] for continuous input, [pressed] for
 * edge-triggered input, and [released] for falling-edge (key-up) input.
 *
 * Example:
 * ```kotlin
 * runIf(dpad.up.held) { moveBy(player, 0, -2) }
 * runIf(buttons.a.pressed) { jump() }
 * runIf(buttons.b.released) { cancelDash() }
 * ```
 *
 * @param gbdkConstant The GBDK joypad bitmask constant name (e.g. "J_UP", "J_A").
 * @param heldFn The C helper function name for held input (e.g. "dpad_held", "button_held").
 * @param pressedFn The C helper function name for edge-triggered input (e.g. "dpad_pressed",
 *   "button_pressed").
 * @param releasedFn The C helper function name for falling-edge input (e.g. "dpad_released",
 *   "button_released").
 */
class InputRef(
    private val gbdkConstant: String,
    private val heldFn: String,
    private val pressedFn: String,
    private val releasedFn: String,
) {
    /**
     * Returns an expression that is true while this input is continuously held.
     *
     * Produces `CallExpr(heldFn, [VarRef(gbdkConstant)])` — same IR as the string-based
     * `dpadHeld("up")` or `buttonHeld("a")`.
     */
    val held: Expr
        get() = CallExpr(heldFn, listOf(VarRef(gbdkConstant)))

    /**
     * Returns an expression that is true on the first frame this input is pressed.
     *
     * Produces `CallExpr(pressedFn, [VarRef(gbdkConstant)])` — same IR as the string-based
     * `dpadPressed("up")` or `buttonPressed("a")`.
     */
    val pressed: Expr
        get() = CallExpr(pressedFn, listOf(VarRef(gbdkConstant)))

    /**
     * Returns an expression that is true on the first frame this input is released (falling edge).
     *
     * Produces `CallExpr(releasedFn, [VarRef(gbdkConstant)])`. True when the input was held on the
     * previous frame but is not held on the current frame.
     *
     * Example: `runIf(buttons.b.released) { cancelDash() }`
     */
    val released: Expr
        get() = CallExpr(releasedFn, listOf(VarRef(gbdkConstant)))
}

// =============================================================================
// D-PAD SINGLETON
// =============================================================================

/**
 * Type-safe d-pad input object.
 *
 * Provides directional properties ([up], [down], [left], [right]) returning [InputRef] instances
 * with [InputRef.held], [InputRef.pressed], and [InputRef.released] accessors. Also provides [any]
 * for detecting any directional input, [none] for detecting no directional input, and [x]/[y] axis
 * helpers returning -1/0/+1 ternary expressions.
 *
 * Usage:
 * ```kotlin
 * runIf(dpad.left.held) { moveBy(paddle, -3, 0) }
 * runIf(dpad.right.held) { moveBy(paddle, 3, 0) }
 * runIf(dpad.up.pressed) { jump() }
 * runIf(dpad.any) { stepCount += 1 }
 * // dx = dpad.x  → -1 (left), 0 (none), +1 (right)
 * // dy = dpad.y  → -1 (up), 0 (none), +1 (down)
 * ```
 */
@Suppress("ClassNaming")
object dpad {
    /** D-pad up direction. Use `.held`, `.pressed`, or `.released`. */
    val up = InputRef("J_UP", "dpad_held", "dpad_pressed", "dpad_released")

    /** D-pad down direction. Use `.held`, `.pressed`, or `.released`. */
    val down = InputRef("J_DOWN", "dpad_held", "dpad_pressed", "dpad_released")

    /** D-pad left direction. Use `.held`, `.pressed`, or `.released`. */
    val left = InputRef("J_LEFT", "dpad_held", "dpad_pressed", "dpad_released")

    /** D-pad right direction. Use `.held`, `.pressed`, or `.released`. */
    val right = InputRef("J_RIGHT", "dpad_held", "dpad_pressed", "dpad_released")

    /**
     * Returns an expression that is true when any d-pad direction is held.
     *
     * Emits a call to `dpad_any()` — a HOME-bank C helper that checks all 4 d-pad bits.
     */
    val any: Expr
        get() = CallExpr("dpad_any", emptyList())

    /**
     * Returns an expression that is true when no d-pad direction is held.
     *
     * Logical negation of [any]: `!dpad_any()`.
     */
    val none: Expr
        get() = UnaryExpr(UnaryOp.LOGICAL_NOT, CallExpr("dpad_any", emptyList()))

    /**
     * Returns a -1/0/+1 ternary expression for the horizontal d-pad axis.
     *
     * Evaluates to -1 when J_LEFT is held, +1 when J_RIGHT is held, 0 otherwise. Useful for smooth
     * movement: `playerDx set dpad.x`.
     *
     * Generated IR: `dpad_held(J_LEFT) ? -1 : (dpad_held(J_RIGHT) ? 1 : 0)`
     */
    val x: Expr
        get() =
            TernaryExpr(
                CallExpr("dpad_held", listOf(VarRef("J_LEFT"))),
                Literal(-1),
                TernaryExpr(
                    CallExpr("dpad_held", listOf(VarRef("J_RIGHT"))),
                    Literal(1),
                    Literal(0),
                ),
            )

    /**
     * Returns a -1/0/+1 ternary expression for the vertical d-pad axis.
     *
     * Evaluates to -1 when J_UP is held, +1 when J_DOWN is held, 0 otherwise. Useful for smooth
     * movement: `playerDy set dpad.y`.
     *
     * Generated IR: `dpad_held(J_UP) ? -1 : (dpad_held(J_DOWN) ? 1 : 0)`
     */
    val y: Expr
        get() =
            TernaryExpr(
                CallExpr("dpad_held", listOf(VarRef("J_UP"))),
                Literal(-1),
                TernaryExpr(
                    CallExpr("dpad_held", listOf(VarRef("J_DOWN"))),
                    Literal(1),
                    Literal(0),
                ),
            )
}

// =============================================================================
// BUTTONS SINGLETON
// =============================================================================

/**
 * Type-safe button input object.
 *
 * Provides button properties ([a], [b], [start], [select]) returning [InputRef] instances with
 * [InputRef.held], [InputRef.pressed], and [InputRef.released] accessors.
 *
 * Usage:
 * ```kotlin
 * runIf(buttons.a.pressed) { jump() }
 * runIf(buttons.start.pressed) { navigate(pauseScene) }
 * runIf(buttons.b.held) { sprint() }
 * runIf(buttons.b.released) { cancelSprint() }
 * ```
 */
@Suppress("ClassNaming")
object buttons {
    /** A button. Use `.held`, `.pressed`, or `.released`. */
    val a = InputRef("J_A", "button_held", "button_pressed", "button_released")

    /** B button. Use `.held`, `.pressed`, or `.released`. */
    val b = InputRef("J_B", "button_held", "button_pressed", "button_released")

    /** Start button. Use `.held`, `.pressed`, or `.released`. */
    val start = InputRef("J_START", "button_held", "button_pressed", "button_released")

    /** Select button. Use `.held`, `.pressed`, or `.released`. */
    val select = InputRef("J_SELECT", "button_held", "button_pressed", "button_released")
}
