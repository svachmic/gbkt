/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.ArrayDataSource
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.BorderStyle
import io.github.gbkt.core.ir.DialogChoice
import io.github.gbkt.core.ir.DialogDef
import io.github.gbkt.core.ir.DialogExprSegment
import io.github.gbkt.core.ir.DialogOption
import io.github.gbkt.core.ir.DialogSay
import io.github.gbkt.core.ir.DialogTextSegment
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.FontMode
import io.github.gbkt.core.ir.HudBar
import io.github.gbkt.core.ir.HudDef
import io.github.gbkt.core.ir.HudHide
import io.github.gbkt.core.ir.HudIcons
import io.github.gbkt.core.ir.HudNumber
import io.github.gbkt.core.ir.HudShow
import io.github.gbkt.core.ir.IconDisplayMode
import io.github.gbkt.core.ir.MenuDataSource
import io.github.gbkt.core.ir.MenuDef
import io.github.gbkt.core.ir.MenuHide
import io.github.gbkt.core.ir.MenuItemDef
import io.github.gbkt.core.ir.MenuLayout
import io.github.gbkt.core.ir.MenuOptionDef
import io.github.gbkt.core.ir.MenuShow
import io.github.gbkt.core.ir.MenuSliderDef
import io.github.gbkt.core.ir.MenuToggleDef
import io.github.gbkt.core.ir.PrintAligned
import io.github.gbkt.core.ir.PrintCentered
import io.github.gbkt.core.ir.ScreenClear
import io.github.gbkt.core.ir.ScreenFill
import io.github.gbkt.core.ir.ScrollBehavior
import io.github.gbkt.core.ir.TextAlignment

// =============================================================================
// DIALOG DSL BUILDERS
// =============================================================================

/**
 * Builder for a named dialog box configuration ([DialogDef]).
 *
 * Follow the method-setter pattern from [CameraBuilder]/[SaveDataBuilder].
 *
 * Usage:
 * ```kotlin
 * val elder = dialog("elder") {
 *     border(BorderStyle.SINGLE)
 *     speaker("Elder Moros")
 *     textSpeed(2)
 *     box(x = 0, y = 14, width = 20, height = 4)
 * }
 * ```
 */
@GbktDsl
class DialogBuilder(private val id: String) {
    private var textSpeedValue: Int = 1
    private var borderStyle: BorderStyle = BorderStyle.NONE
    private var customBorderTilesList: List<Int>? = null
    private var speakerName: String? = null
    private var portraitAsset: AssetRef? = null
    private var boxXVal: Int = 0
    private var boxYVal: Int = 14
    private var boxWidthVal: Int = 20
    private var boxHeightVal: Int = 4
    private var fontModeValue: FontMode = FontMode.FIXED_WIDTH

    /** Sets the typewriter speed in characters per frame. */
    fun textSpeed(speed: Int) {
        textSpeedValue = speed
    }

    /** Sets the border rendering style. */
    fun border(style: BorderStyle) {
        borderStyle = style
    }

    /**
     * Sets the custom border tile indices for [BorderStyle.CUSTOM] borders.
     *
     * Requires exactly 8 tile indices in order: TL, TR, BL, BR, H-top, H-bottom, V-left, V-right.
     */
    fun customBorderTiles(tiles: List<Int>) {
        customBorderTilesList = tiles
    }

    /** Sets the speaker name displayed above the dialog box. */
    fun speaker(name: String) {
        speakerName = name
    }

    /** Sets the portrait sprite asset displayed beside the dialog box. */
    fun portrait(asset: AssetRef) {
        portraitAsset = asset
    }

    /** Sets the dialog box position and dimensions in tile coordinates. */
    fun box(x: Int = 0, y: Int = 14, width: Int = 20, height: Int = 4) {
        boxXVal = x
        boxYVal = y
        boxWidthVal = width
        boxHeightVal = height
    }

    /** Sets the font rendering mode (fixed-width tile-based or variable-width VWF). */
    fun fontMode(mode: FontMode) {
        fontModeValue = mode
    }

    internal fun build(): DialogDef =
        DialogDef(
            id = id,
            textSpeed = textSpeedValue,
            border = borderStyle,
            speaker = speakerName,
            portrait = portraitAsset,
            boxX = boxXVal,
            boxY = boxYVal,
            boxWidth = boxWidthVal,
            boxHeight = boxHeightVal,
            customBorderTiles = customBorderTilesList,
            fontMode = fontModeValue,
        )
}

/**
 * Handle returned by [GameBuilder.dialog] for emitting dialog script ops.
 *
 * Usage:
 * ```kotlin
 * val elder = dialog("elder") { speaker("Elder Moros") }
 * // In a scene:
 * scene("village") {
 *     frame {
 *         runIf(buttons.a.pressed) {
 *             elder.say("Welcome, traveler.")
 *             elder.choice {
 *                 option("Accept quest") { navigate(questScene) }
 *                 option("Decline") { navigate(villageScene) }
 *             }
 *         }
 *     }
 * }
 * ```
 */
class DialogHandle(val id: String) {

    /**
     * Emits a [DialogSay] into the active [ScriptBuilder] with a single text string.
     *
     * Convenience overload for simple one-liner dialogs.
     */
    fun say(text: String) {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("DialogHandle.say() must be called inside a scene lifecycle block")
        builder.emit(
            DialogSay(
                dialogId = id,
                segments = listOf(DialogTextSegment(text)),
                sourceLocation = loc,
            )
        )
    }

    /**
     * Emits a [DialogSay] into the active [ScriptBuilder] with mixed text/expression segments.
     *
     * Accepts [String] values (converted to [DialogTextSegment]) and [Expr] values (converted to
     * [DialogExprSegment]). Useful for variable interpolation:
     * ```kotlin
     * shopkeeper.say("That'll be ", price, " gold.")
     * ```
     */
    fun say(vararg segments: Any) {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("DialogHandle.say() must be called inside a scene lifecycle block")
        val irSegments = segments.map { seg ->
            when (seg) {
                is String -> DialogTextSegment(seg)
                is Expr -> DialogExprSegment(seg)
                else ->
                    error(
                        "DialogHandle.say() only accepts String or Expr segments, got: ${seg::class.simpleName}"
                    )
            }
        }
        builder.emit(DialogSay(dialogId = id, segments = irSegments, sourceLocation = loc))
    }

    /**
     * Emits a [DialogChoice] into the active [ScriptBuilder].
     *
     * Use [DialogChoiceBuilder.option] to define each selectable choice:
     * ```kotlin
     * elder.choice {
     *     option("Accept") { navigate(questScene) }
     *     option("Decline") { navigate(villageScene) }
     * }
     * ```
     */
    fun choice(block: DialogChoiceBuilder.() -> Unit) {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("DialogHandle.choice() must be called inside a scene lifecycle block")
        val choiceBuilder = DialogChoiceBuilder()
        choiceBuilder.block()
        builder.emit(
            DialogChoice(
                dialogId = id,
                options = choiceBuilder.buildOptions(),
                sourceLocation = loc,
            )
        )
    }
}

/**
 * Builder for collecting [DialogOption] entries in a choice prompt.
 *
 * Used inside [DialogHandle.choice] blocks.
 */
@GbktDsl
class DialogChoiceBuilder {
    private val options: MutableList<DialogOption> = mutableListOf()

    /**
     * Adds a choice option with the given [label] and a script block to execute when selected.
     *
     * The [label] should be i18n-ready — no magic strings.
     */
    fun option(label: String, block: ScriptBuilder.() -> Unit) {
        val bodyBuilder = ScriptBuilder()
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }
        options += DialogOption(label = label, body = bodyBuilder.build())
    }

    internal fun buildOptions(): List<DialogOption> = options.toList()
}

// =============================================================================
// MENU DSL BUILDERS
// =============================================================================

/**
 * Builder for a named interactive menu configuration ([MenuDef]).
 *
 * Usage:
 * ```kotlin
 * val mainMenu = menu("mainMenu") {
 *     layout(MenuLayout.VERTICAL)
 *     cursor(">")
 *     position(0, 0, 20, 18)
 *     item("Start Game") { navigate(gameScene) }
 *     item("Options") { mainMenu.show() }
 *     item("Quit") { navigate(titleScene) }
 * }
 * ```
 */
@GbktDsl
class MenuBuilder(private val id: String) {
    private var layoutValue: MenuLayout = MenuLayout.VERTICAL
    private var cursorCharValue: String = ">"
    private var cursorSpriteAsset: AssetRef? = null
    private var parentIdValue: String? = null
    private var renderOnWindowValue: Boolean = true
    private var scrollBehaviorValue: ScrollBehavior = ScrollBehavior.AUTO_SCROLL
    private var sfxOnMoveId: String? = null
    private var sfxOnSelectId: String? = null
    private var sfxOnCancelId: String? = null
    private var xPos: Int = 0
    private var yPos: Int = 0
    private var widthVal: Int = 20
    private var heightVal: Int = 18
    private var columnsVal: Int = 1
    private var dataSourceValue: MenuDataSource? = null

    private val items: MutableList<MenuItemDef> = mutableListOf()
    private val toggles: MutableList<MenuToggleDef> = mutableListOf()
    private val sliders: MutableList<MenuSliderDef> = mutableListOf()
    private val options: MutableList<MenuOptionDef> = mutableListOf()

    /** Sets the item arrangement direction. */
    fun layout(layout: MenuLayout) {
        layoutValue = layout
    }

    /** Sets the text character used as the selection cursor. */
    fun cursor(char: String) {
        cursorCharValue = char
    }

    /** Sets an optional sprite asset for an animated cursor. */
    fun cursorSprite(asset: AssetRef) {
        cursorSpriteAsset = asset
    }

    /** Sets the parent menu handle for automatic back-navigation on B button. */
    fun parent(menu: MenuHandle) {
        parentIdValue = menu.id
    }

    /** Renders the menu on the background layer instead of the window layer. */
    fun renderOnBackground() {
        renderOnWindowValue = false
    }

    /** Sets the scroll behavior when more items than visible height exist. */
    fun scroll(behavior: ScrollBehavior) {
        scrollBehaviorValue = behavior
    }

    /** Sets optional SFX hooks for cursor movement, selection, and cancellation. */
    fun sfx(onMove: SoundRef? = null, onSelect: SoundRef? = null, onCancel: SoundRef? = null) {
        sfxOnMoveId = onMove?.id
        sfxOnSelectId = onSelect?.id
        sfxOnCancelId = onCancel?.id
    }

    /** Sets the menu position and dimensions in tile coordinates. */
    fun position(x: Int = 0, y: Int = 0, width: Int = 20, height: Int = 18) {
        xPos = x
        yPos = y
        widthVal = width
        heightVal = height
    }

    /** Sets the number of columns for [MenuLayout.GRID] menus. */
    fun columns(n: Int) {
        columnsVal = n
    }

    /**
     * Adds a static menu item that executes a script block when selected.
     *
     * The [label] text is displayed to the player.
     */
    fun item(label: String, block: ScriptBuilder.() -> Unit) {
        val bodyBuilder = ScriptBuilder()
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }
        items += MenuItemDef(label = label, body = bodyBuilder.build())
    }

    /**
     * Adds a toggle control item that flips a boolean variable.
     *
     * The [variable] must be an [AssignableVar] (a `var` declared with `u8Var` or similar).
     */
    fun toggle(label: String, variable: AssignableVar) {
        toggles += MenuToggleDef(label = label, variable = variable.name)
    }

    /**
     * Adds a slider control item that adjusts a numeric variable within a range.
     *
     * The [variable] must be an [AssignableVar].
     */
    fun slider(label: String, variable: AssignableVar, min: Int, max: Int, step: Int = 1) {
        sliders +=
            MenuSliderDef(
                label = label,
                variable = variable.name,
                min = min,
                max = max,
                step = step,
            )
    }

    /**
     * Adds a cycle-option control item that cycles through a list of choices.
     *
     * The [variable] must be an [AssignableVar]. The [choices] list provides the display strings.
     */
    fun option(label: String, variable: AssignableVar, choices: List<String>) {
        options += MenuOptionDef(label = label, variable = variable.name, choices = choices)
    }

    /**
     * Sets the dynamic data source for this menu.
     *
     * Accepts an [ArrayVar] (array data source) for grid menus that auto-populate from runtime
     * data. This enables inventory-driven menus:
     * ```kotlin
     * menu("inventory") {
     *     layout(MenuLayout.GRID)
     *     columns(5)
     *     itemsFrom(itemArray)
     * }
     * ```
     */
    fun itemsFrom(source: ArrayVar) {
        dataSourceValue = ArrayDataSource(arrayId = source.name)
    }

    /**
     * Sets the dynamic data source using a raw inventory or source ID string.
     *
     * For sources that don't have a typed handle (e.g. inventory systems), pass the ID directly.
     */
    fun itemsFrom(sourceId: String) {
        dataSourceValue = ArrayDataSource(arrayId = sourceId)
    }

    internal fun build(): MenuDef =
        MenuDef(
            id = id,
            layout = layoutValue,
            cursorChar = cursorCharValue,
            cursorSprite = cursorSpriteAsset,
            parentId = parentIdValue,
            renderOnWindow = renderOnWindowValue,
            scrollBehavior = scrollBehaviorValue,
            sfxOnMove = sfxOnMoveId,
            sfxOnSelect = sfxOnSelectId,
            sfxOnCancel = sfxOnCancelId,
            x = xPos,
            y = yPos,
            width = widthVal,
            height = heightVal,
            columns = columnsVal,
            items = items.toList(),
            dataSource = dataSourceValue,
        )
}

/**
 * Handle returned by [GameBuilder.menu] for emitting menu script ops.
 *
 * Usage:
 * ```kotlin
 * val mainMenu = menu("mainMenu") { ... }
 * // In a scene:
 * scene("title") {
 *     enter { mainMenu.show() }
 *     exit { mainMenu.hide() }
 * }
 * ```
 */
class MenuHandle(val id: String) {

    /** Opens and runs this menu. Emits [MenuShow] into the active [ScriptBuilder]. */
    fun show() {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("MenuHandle.show() must be called inside a scene lifecycle block")
        builder.emit(MenuShow(menuId = id, sourceLocation = loc))
    }

    /** Closes this menu. Emits [MenuHide] into the active [ScriptBuilder]. */
    fun hide() {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("MenuHandle.hide() must be called inside a scene lifecycle block")
        builder.emit(MenuHide(menuId = id, sourceLocation = loc))
    }
}

// =============================================================================
// HUD DSL BUILDERS
// =============================================================================

/**
 * Builder for a named HUD panel configuration ([HudDef]).
 *
 * Usage:
 * ```kotlin
 * val statsHud = hud("statsHud") {
 *     anchor(Anchor.TOP_LEFT)
 *     bar("hp") {
 *         variable(hp)
 *         max(maxHp)
 *         width(8)
 *         fillTile(0x01)
 *         emptyTile(0x00)
 *     }
 *     number("score") {
 *         variable(score)
 *         label("Score: ")
 *         format("%04d")
 *     }
 *     icons("lives") {
 *         variable(lives)
 *         max(3)
 *         fullTile(0x08)
 *         emptyTile(0x09)
 *     }
 * }
 * ```
 */
@GbktDsl
class HudBuilder(private val id: String) {
    private var anchorValue: Anchor = Anchor.TOP_LEFT
    private var tileXValue: Int? = null
    private var tileYValue: Int? = null
    private var renderOnWindowValue: Boolean = true

    private val elements: MutableList<io.github.gbkt.core.ir.HudElement> = mutableListOf()

    /** Sets the anchor shorthand position for common placements. */
    fun anchor(anchor: Anchor) {
        anchorValue = anchor
    }

    /** Sets an explicit tile coordinate position (overrides anchor). */
    fun position(tileX: Int, tileY: Int) {
        tileXValue = tileX
        tileYValue = tileY
    }

    /** Renders the HUD panel on the background layer instead of the window layer. */
    fun renderOnBackground() {
        renderOnWindowValue = false
    }

    /** Adds a fill-bar element to this HUD panel. */
    fun bar(id: String, block: BarBuilder.() -> Unit) {
        val builder = BarBuilder(id)
        builder.block()
        elements += builder.build()
    }

    /** Adds a numeric display element to this HUD panel. */
    fun number(id: String, block: NumberBuilder.() -> Unit) {
        val builder = NumberBuilder(id)
        builder.block()
        elements += builder.build()
    }

    /** Adds an icon-counter element to this HUD panel. */
    fun icons(id: String, block: IconBuilder.() -> Unit) {
        val builder = IconBuilder(id)
        builder.block()
        elements += builder.build()
    }

    internal fun build(): HudDef =
        HudDef(
            id = id,
            anchor = anchorValue,
            tileX = tileXValue,
            tileY = tileYValue,
            renderOnWindow = renderOnWindowValue,
            elements = elements.toList(),
        )
}

/**
 * Builder for a [HudBar] fill-bar element.
 *
 * Configures a horizontal fill bar that reads from a game variable and renders as a sequence of
 * [fillTile] and [emptyTile] GBDK tile indices — DMG-compatible, no GBC palette required.
 */
@GbktDsl
class BarBuilder(private val id: String) {
    private var variableName: String = ""
    private var maxVariableName: String? = null
    private var maxValueFixed: Int = 100
    private var widthTiles: Int = 8
    private var fillTileIdx: Int = 0x01
    private var emptyTileIdx: Int = 0x00
    private var fillFramesCount: Int = 0
    private var gbcPaletteIdx: Int? = null

    /** Binds the bar's current value to [variable]. */
    fun variable(v: AssignableVar) {
        variableName = v.name
    }

    /** Binds the bar's maximum value to another variable [v]. */
    fun max(v: AssignableVar) {
        maxVariableName = v.name
    }

    /** Sets the bar's maximum value to a fixed constant. */
    fun max(value: Int) {
        maxValueFixed = value
    }

    /** Sets the bar width in tiles. */
    fun width(tiles: Int) {
        widthTiles = tiles
    }

    /** Sets the GBDK tile index for the filled (full) portion of the bar. */
    fun fillTile(tile: Int) {
        fillTileIdx = tile
    }

    /** Sets the GBDK tile index for the empty (depleted) portion of the bar. */
    fun emptyTile(tile: Int) {
        emptyTileIdx = tile
    }

    /** Sets the number of frames to animate bar fill/drain transitions. 0 = instant. */
    fun animateFrames(n: Int) {
        fillFramesCount = n
    }

    /** Sets the optional GBC palette index for color enhancement on GBC hardware. */
    fun gbcPalette(paletteIndex: Int) {
        gbcPaletteIdx = paletteIndex
    }

    internal fun build(): HudBar =
        HudBar(
            id = id,
            variable = variableName,
            maxVariable = maxVariableName,
            maxValue = maxValueFixed,
            width = widthTiles,
            fillTile = fillTileIdx,
            emptyTile = emptyTileIdx,
            fillFrames = fillFramesCount,
            gbcPalette = gbcPaletteIdx,
        )
}

/**
 * Builder for a [HudNumber] numeric display element.
 *
 * Renders a game variable as formatted text (e.g., "HP: 45/100" or "Score: 0099").
 */
@GbktDsl
class NumberBuilder(private val id: String) {
    private var variableName: String = ""
    private var labelText: String = ""
    private var formatStr: String = "%d"

    /** Binds the numeric display to [variable]. */
    fun variable(v: AssignableVar) {
        variableName = v.name
    }

    /** Sets the label prefix displayed before the number (e.g., "HP: "). */
    fun label(text: String) {
        labelText = text
    }

    /** Sets the C-style format string for the number (e.g., "%d", "%04d"). */
    fun format(fmt: String) {
        formatStr = fmt
    }

    internal fun build(): HudNumber =
        HudNumber(id = id, variable = variableName, label = labelText, format = formatStr)
}

/**
 * Builder for a [HudIcons] icon-counter element.
 *
 * Renders a game variable as discrete icon tiles (hearts, stars, badges, etc.).
 */
@GbktDsl
class IconBuilder(private val id: String) {
    private var variableName: String = ""
    private var maxValueCount: Int = 3
    private var fullTileIdx: Int = 0
    private var emptyTileIdx: Int = 1
    private var displayModeValue: IconDisplayMode = IconDisplayMode.FULL_AND_EMPTY

    /** Binds the icon counter's current value to [variable]. */
    fun variable(v: AssignableVar) {
        variableName = v.name
    }

    /** Sets the total number of icon slots to render. */
    fun max(value: Int) {
        maxValueCount = value
    }

    /** Sets the GBDK tile index for a filled icon slot. */
    fun fullTile(tile: Int) {
        fullTileIdx = tile
    }

    /** Sets the GBDK tile index for an empty icon slot. */
    fun emptyTile(tile: Int) {
        emptyTileIdx = tile
    }

    /** Sets whether to show both full+empty icons or only filled icons. */
    fun displayMode(mode: IconDisplayMode) {
        displayModeValue = mode
    }

    internal fun build(): HudIcons =
        HudIcons(
            id = id,
            variable = variableName,
            maxValue = maxValueCount,
            fullTile = fullTileIdx,
            emptyTile = emptyTileIdx,
            displayMode = displayModeValue,
        )
}

/**
 * Panel handle returned by [GameBuilder.hud] for emitting HUD script ops.
 *
 * Usage:
 * ```kotlin
 * val statsHud = hud("statsHud") { ... }
 * // In a scene:
 * scene("gameplay") {
 *     enter { statsHud.show() }
 *     exit { statsHud.hide() }
 * }
 * ```
 */
class HudPanel(val id: String) {

    /** Makes this HUD panel visible. Emits [HudShow] into the active [ScriptBuilder]. */
    fun show() {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("HudPanel.show() must be called inside a scene lifecycle block")
        builder.emit(HudShow(hudId = id, sourceLocation = loc))
    }

    /** Hides this HUD panel. Emits [HudHide] into the active [ScriptBuilder]. */
    fun hide() {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("HudPanel.hide() must be called inside a scene lifecycle block")
        builder.emit(HudHide(hudId = id, sourceLocation = loc))
    }
}

// =============================================================================
// PRINT BUILDER HELPERS
// =============================================================================

/**
 * Intermediate builder for the `printCentered(text) at row` fluent API.
 *
 * Usage: `printCentered("Score: 0") at 2`
 */
class PrintCenteredBuilder(
    private val text: String,
    private val fontMode: FontMode = FontMode.FIXED_WIDTH,
) {
    /**
     * Emits a [PrintCentered] op for the given row.
     *
     * Usage: `printCentered("Title") at 0`
     */
    infix fun at(row: Int) {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("printCentered().at() must be called inside a scene lifecycle block")
        builder.emit(
            PrintCentered(text = text, row = row, fontMode = fontMode, sourceLocation = loc)
        )
    }
}

/**
 * Intermediate builder for the `printAligned(text, alignment) at row` fluent API.
 *
 * Usage: `printAligned("Score: 0", TextAlignment.RIGHT) at 0`
 */
class PrintAlignedBuilder(
    private val text: String,
    private val alignment: TextAlignment,
    private val fontMode: FontMode = FontMode.FIXED_WIDTH,
) {
    /**
     * Emits a [PrintAligned] op for the given row.
     *
     * Usage: `printAligned("Lives: 3", TextAlignment.RIGHT) at 0`
     */
    infix fun at(row: Int) {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("printAligned().at() must be called inside a scene lifecycle block")
        builder.emit(
            PrintAligned(
                text = text,
                row = row,
                alignment = alignment,
                fontMode = fontMode,
                sourceLocation = loc,
            )
        )
    }
}

// =============================================================================
// SCREEN OPERATIONS SCOPE
// =============================================================================

/**
 * Scope object providing screen-level operations in [ScriptBuilder] blocks.
 *
 * Accessed via `screen` in the DSL:
 * ```kotlin
 * scene("title") {
 *     enter {
 *         screen.clear()
 *         screen.fill(tile = 0x02)
 *     }
 * }
 * ```
 */
@Suppress("ClassNaming")
object screen {
    /** Clears the entire screen (both window and background layers). Emits [ScreenClear]. */
    fun clear() {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("screen.clear() must be called inside a scene lifecycle block")
        builder.emit(ScreenClear(sourceLocation = loc))
    }

    /** Fills the entire screen with the specified [tile] index. Emits [ScreenFill]. */
    fun fill(tile: Int) {
        val loc = captureV2Location()
        val builder =
            ScriptBuilderContext.current
                ?: error("screen.fill() must be called inside a scene lifecycle block")
        builder.emit(ScreenFill(tile = tile, sourceLocation = loc))
    }
}
