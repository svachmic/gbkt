/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ui

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateExpr
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRStatusBarFlash
import io.github.gbkt.core.ir.IRStatusBarHide
import io.github.gbkt.core.ir.IRStatusBarSetValue
import io.github.gbkt.core.ir.IRStatusBarShow
import io.github.gbkt.core.ir.IRStatusBarTick
import io.github.gbkt.core.ui.BarOrientation
import io.github.gbkt.core.ui.StatusBarDefinition
import io.github.gbkt.core.ui.StatusBarStyle

// =============================================================================
// STATUS BAR CODE GENERATION
// =============================================================================

/**
 * Generate status bar system code.
 *
 * Creates:
 * - Status bar state variables
 * - Rendering functions
 * - Animation update functions
 * - Low/critical threshold callbacks
 */
internal fun GBDKCodeGenerator.generateStatusBarSystem() {
    val statusBars = game.statusBars
    if (statusBars.isEmpty()) return

    line("// =============================================================================")
    line("// STATUS BAR SYSTEM")
    line("// =============================================================================")
    line()

    // Generate constants
    generateStatusBarConstants()

    // Generate each status bar
    for (bar in statusBars) {
        generateStatusBarDefinition(bar)
    }
}

/**
 * Handle status bar IR statements.
 *
 * @return true if this was a status bar statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateStatusBarStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRStatusBarSetValue -> {
            generateSetValue(stmt)
            true
        }
        is IRStatusBarShow -> {
            generateShow(stmt)
            true
        }
        is IRStatusBarHide -> {
            generateHide(stmt)
            true
        }
        is IRStatusBarTick -> {
            generateTick(stmt)
            true
        }
        is IRStatusBarFlash -> {
            generateFlash(stmt)
            true
        }
        else -> false
    }

/** Generate status bar style constants. */
private fun GBDKCodeGenerator.generateStatusBarConstants() {
    line("// Status bar style constants")
    line("#define SBAR_STYLE_SOLID 0u")
    line("#define SBAR_STYLE_SEGMENTED 1u")
    line("#define SBAR_STYLE_PIPS 2u")
    line("#define SBAR_STYLE_NUMERIC 3u")
    line()

    // Helper function for scaled clamped division - prevents optimizer warnings
    line("// Helper: compute (current * scale / max), clamped to [0, scale]")
    line("// Separate function prevents SDCC optimizer from changing control flow")
    line("static UINT8 _sbar_scaled_value(UINT16 current, UINT16 max, UINT8 scale) {")
    indent++
    line("UINT16 result;")
    line("if (max == 0u) return 0u;")
    line("result = (current * scale) / max;")
    line("if (result > scale) return scale;")
    line("return (UINT8)result;")
    indent--
    line("}")
    line()
}

/** Generate status bar definition. */
private fun GBDKCodeGenerator.generateStatusBarDefinition(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    line("// -----------------------------------------------------------------------------")
    line("// Status Bar: $name")
    line("// -----------------------------------------------------------------------------")
    line()

    // Generate constants
    line("// ${name} constants")
    line("#define ${nameUpper}_X ${bar.x}u")
    line("#define ${nameUpper}_Y ${bar.y}u")
    line("#define ${nameUpper}_WIDTH ${bar.width}u")
    line("#define ${nameUpper}_HEIGHT ${bar.height}u")
    line("#define ${nameUpper}_STYLE ${bar.style.ordinal}u")
    line("#define ${nameUpper}_ANIM_SPEED ${bar.animationSpeed}u")
    line("#define ${nameUpper}_LOW_THRESHOLD ${bar.lowThreshold}u")
    line("#define ${nameUpper}_CRITICAL_THRESHOLD ${bar.criticalThreshold}u")

    if (bar.style == StatusBarStyle.SEGMENTED) {
        line("#define ${nameUpper}_SEGMENTS ${bar.segments}u")
    }
    if (bar.style == StatusBarStyle.PIPS) {
        line("#define ${nameUpper}_PIPS ${bar.pips}u")
    }

    // Tile IDs
    line("#define ${nameUpper}_TILE_EMPTY ${bar.tileIds.empty}u")
    line("#define ${nameUpper}_TILE_FILLED ${bar.tileIds.filled}u")
    line("#define ${nameUpper}_TILE_PARTIAL ${bar.tileIds.partial}u")

    // Sprite start index (for sprite-based bars)
    if (bar.useSprites) {
        line("#define ${nameUpper}_SPRITE_START ${bar.spriteStartIndex}u")
    }
    line()

    // Generate state variables
    line("// ${name} state")
    line("static UINT16 _${name}_current = 0u;")
    line("static UINT16 _${name}_max = 100u;")
    line("static UINT16 _${name}_display = 0u;") // Animated display value
    line("static UINT8 _${name}_visible = 0u;")
    line("static UINT8 _${name}_flash_timer = 0u;")
    line("static UINT8 _${name}_was_low = 0u;")
    line("static UINT8 _${name}_was_critical = 0u;")
    line()

    // Generate render function
    generateRenderFunction(bar)

    // Generate tick function
    generateTickFunction(bar)

    // Generate helper functions
    generateHelperFunctions(bar)
}

/** Generate status bar render function. */
private fun GBDKCodeGenerator.generateRenderFunction(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    line("// Render ${name}")
    line("static void _${name}_render(void) {")
    indent++
    line("if (!_${name}_visible) return;")
    line()

    // Calculate fill percentage using helper to avoid optimizer warnings
    line("// Calculate fill amount")
    line("UINT8 fill = _sbar_scaled_value(_${name}_display, _${name}_max, ${nameUpper}_WIDTH);")
    line()

    // Flash effect
    line("// Flash effect (toggle visibility)")
    line("if (_${name}_flash_timer > 0u) {")
    indent++
    line("if (_${name}_flash_timer & 0x04u) {")
    indent++
    line("return; // Skip render during flash-off frames")
    indent--
    line("}")
    indent--
    line("}")
    line()

    when (bar.style) {
        StatusBarStyle.SOLID -> generateSolidBarRender(bar)
        StatusBarStyle.SEGMENTED -> generateSegmentedBarRender(bar)
        StatusBarStyle.PIPS -> generatePipsRender(bar)
        StatusBarStyle.NUMERIC -> generateNumericRender(bar)
    }

    indent--
    line("}")
    line()
}

/** Generate solid bar rendering. */
private fun GBDKCodeGenerator.generateSolidBarRender(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    if (bar.useSprites) {
        line("// Sprite-based solid bar")
        line("// Uses sprite slots starting from ${nameUpper}_SPRITE_START")
        line("UINT8 tile;")
        line("for (UINT8 i = 0u; i < ${nameUpper}_WIDTH; i++) {")
        indent++
        line("if (i < fill) {")
        indent++
        line("tile = ${nameUpper}_TILE_FILLED;")
        indent--
        line("} else {")
        indent++
        line("tile = ${nameUpper}_TILE_EMPTY;")
        indent--
        line("}")
        line("UINT8 sprite_idx = ${nameUpper}_SPRITE_START + i;")

        if (bar.orientation == BarOrientation.HORIZONTAL) {
            line("move_sprite(sprite_idx, ${nameUpper}_X + (i << 3), ${nameUpper}_Y);")
        } else {
            line(
                "move_sprite(sprite_idx, ${nameUpper}_X, ${nameUpper}_Y + ((${nameUpper}_WIDTH - 1u - i) << 3));"
            )
        }

        line("set_sprite_tile(sprite_idx, tile);")
        indent--
        line("}")
    } else {
        line("// Background tile-based solid bar")
        line("UINT8 tile;")
        line("for (UINT8 i = 0u; i < ${nameUpper}_WIDTH; i++) {")
        indent++
        line("if (i < fill) {")
        indent++
        line("tile = ${nameUpper}_TILE_FILLED;")
        indent--
        line("} else {")
        indent++
        line("tile = ${nameUpper}_TILE_EMPTY;")
        indent--
        line("}")

        if (bar.orientation == BarOrientation.HORIZONTAL) {
            line("set_bkg_tile_xy(${nameUpper}_X + i, ${nameUpper}_Y, tile);")
        } else {
            line(
                "set_bkg_tile_xy(${nameUpper}_X, ${nameUpper}_Y + (${nameUpper}_WIDTH - 1u - i), tile);"
            )
        }

        indent--
        line("}")
    }
}

/** Generate segmented bar rendering. */
private fun GBDKCodeGenerator.generateSegmentedBarRender(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    line("// Segmented bar")
    line(
        "UINT8 segments_filled = _sbar_scaled_value(_${name}_display, _${name}_max, ${nameUpper}_SEGMENTS);"
    )
    line()
    line("UINT8 tile;")
    line("for (UINT8 i = 0u; i < ${nameUpper}_SEGMENTS; i++) {")
    indent++
    line("if (i < segments_filled) {")
    indent++
    line("tile = ${nameUpper}_TILE_FILLED;")
    indent--
    line("} else {")
    indent++
    line("tile = ${nameUpper}_TILE_EMPTY;")
    indent--
    line("}")

    if (bar.orientation == BarOrientation.HORIZONTAL) {
        line("set_bkg_tile_xy(${nameUpper}_X + i, ${nameUpper}_Y, tile);")
    } else {
        line(
            "set_bkg_tile_xy(${nameUpper}_X, ${nameUpper}_Y + (${nameUpper}_SEGMENTS - 1u - i), tile);"
        )
    }

    indent--
    line("}")
    line("(void)fill;")
}

/** Generate pips/hearts rendering. */
private fun GBDKCodeGenerator.generatePipsRender(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    line("// Pip display (hearts, etc)")
    line(
        "UINT8 pips_filled = _sbar_scaled_value(_${name}_display, _${name}_max, ${nameUpper}_PIPS);"
    )
    line()
    line("UINT8 tile;")
    line("for (UINT8 i = 0u; i < ${nameUpper}_PIPS; i++) {")
    indent++
    line("if (i < pips_filled) {")
    indent++
    line("tile = ${bar.tileIds.pipFilled}u;")
    indent--
    line("} else {")
    indent++
    line("tile = ${bar.tileIds.pipEmpty}u;")
    indent--
    line("}")

    if (bar.orientation == BarOrientation.HORIZONTAL) {
        line("set_bkg_tile_xy(${nameUpper}_X + i, ${nameUpper}_Y, tile);")
    } else {
        line("set_bkg_tile_xy(${nameUpper}_X, ${nameUpper}_Y + i, tile);")
    }

    indent--
    line("}")
    line("(void)fill;")
}

/** Generate numeric display rendering. */
private fun GBDKCodeGenerator.generateNumericRender(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    line("// Numeric display")
    line("gotoxy(${nameUpper}_X, ${nameUpper}_Y);")
    line("printf(\"%3u/%3u\", (unsigned)_${name}_display, (unsigned)_${name}_max);")
    line("(void)fill;")
}

/** Generate status bar tick function. */
private fun GBDKCodeGenerator.generateTickFunction(bar: StatusBarDefinition) {
    val name = bar.name
    val nameUpper = name.uppercase()

    line("// Update ${name}")
    line("static void _${name}_tick(void) {")
    indent++
    line("if (!_${name}_visible) return;")
    line()

    // Flash timer
    line("// Update flash timer")
    line("if (_${name}_flash_timer > 0u) _${name}_flash_timer--;")
    line()

    // Smooth animation
    if (bar.animationSpeed > 0) {
        line("// Smooth animation toward target value")
        line("if (_${name}_display < _${name}_current) {")
        indent++
        line("UINT16 diff = _${name}_current - _${name}_display;")
        line("UINT16 step;")
        line("if (diff > ${nameUpper}_ANIM_SPEED) {")
        indent++
        line("step = ${nameUpper}_ANIM_SPEED;")
        indent--
        line("} else {")
        indent++
        line("step = diff;")
        indent--
        line("}")
        line("_${name}_display += step;")
        indent--
        line("} else if (_${name}_display > _${name}_current) {")
        indent++
        line("UINT16 diff = _${name}_display - _${name}_current;")
        line("UINT16 step;")
        line("if (diff > ${nameUpper}_ANIM_SPEED) {")
        indent++
        line("step = ${nameUpper}_ANIM_SPEED;")
        indent--
        line("} else {")
        indent++
        line("step = diff;")
        indent--
        line("}")
        line("_${name}_display -= step;")
        indent--
        line("}")
    } else {
        line("// Instant update (no animation)")
        line("_${name}_display = _${name}_current;")
    }
    line()

    // Check low/critical thresholds
    line("// Check thresholds")
    line("UINT8 percent = _sbar_scaled_value(_${name}_current, _${name}_max, 100u);")
    line()

    // Low threshold
    if (bar.onLowStatements.isNotEmpty()) {
        line("// Low threshold check")
        line("if (percent <= ${nameUpper}_LOW_THRESHOLD && !_${name}_was_low) {")
        indent++
        line("_${name}_was_low = 1u;")
        for (stmt in bar.onLowStatements) {
            generateStatement(stmt)
        }
        indent--
        line("} else if (percent > ${nameUpper}_LOW_THRESHOLD) {")
        indent++
        line("_${name}_was_low = 0u;")
        indent--
        line("}")
    }

    // Critical threshold
    if (bar.onCriticalStatements.isNotEmpty()) {
        line("// Critical threshold check")
        line("if (percent <= ${nameUpper}_CRITICAL_THRESHOLD && !_${name}_was_critical) {")
        indent++
        line("_${name}_was_critical = 1u;")
        for (stmt in bar.onCriticalStatements) {
            generateStatement(stmt)
        }
        indent--
        line("} else if (percent > ${nameUpper}_CRITICAL_THRESHOLD) {")
        indent++
        line("_${name}_was_critical = 0u;")
        indent--
        line("}")
    }
    line()

    // Render
    line("_${name}_render();")

    indent--
    line("}")
    line()
}

/** Generate helper functions for status bar. */
private fun GBDKCodeGenerator.generateHelperFunctions(bar: StatusBarDefinition) {
    val name = bar.name

    line("// Show ${name}")
    line("static void _${name}_show(void) {")
    indent++
    line("_${name}_visible = 1u;")
    line("_${name}_render();")
    indent--
    line("}")
    line()

    line("// Hide ${name}")
    line("static void _${name}_hide(void) {")
    indent++
    line("_${name}_visible = 0u;")
    indent--
    line("}")
    line()

    line("// Set ${name} value")
    line("static void _${name}_set_value(UINT16 current, UINT16 max) {")
    indent++
    line("_${name}_current = current;")
    line("if (max != 0xFFFFu) _${name}_max = max; // -1 means keep existing max")
    indent--
    line("}")
    line()

    line("// Flash ${name}")
    line("static void _${name}_flash(UINT8 duration) {")
    indent++
    line("_${name}_flash_timer = duration;")
    indent--
    line("}")
    line()
}

// =============================================================================
// IR STATEMENT GENERATORS
// =============================================================================

private fun GBDKCodeGenerator.generateSetValue(stmt: IRStatusBarSetValue) {
    val current = generateExpr(stmt.currentValue)
    val max = generateExpr(stmt.maxValue)
    lineWithSource("_${stmt.name}_set_value($current, $max);", stmt.sourceLocation, stmt.name)
}

private fun GBDKCodeGenerator.generateShow(stmt: IRStatusBarShow) {
    lineWithSource("_${stmt.name}_show();", stmt.sourceLocation, stmt.name)
}

private fun GBDKCodeGenerator.generateHide(stmt: IRStatusBarHide) {
    lineWithSource("_${stmt.name}_hide();", stmt.sourceLocation, stmt.name)
}

private fun GBDKCodeGenerator.generateTick(stmt: IRStatusBarTick) {
    lineWithSource("_${stmt.name}_tick();", stmt.sourceLocation, stmt.name)
}

private fun GBDKCodeGenerator.generateFlash(stmt: IRStatusBarFlash) {
    val duration = generateExpr(stmt.duration)
    lineWithSource("_${stmt.name}_flash($duration);", stmt.sourceLocation, stmt.name)
}
