/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// UI IR TYPES — Dialog, Menu, HUD
// =============================================================================

// -----------------------------------------------------------------------------
// Shared enums
// -----------------------------------------------------------------------------

/** Border style for dialog boxes. */
enum class BorderStyle {
    /** No border drawn. */
    NONE,
    /** Single-line tile border. */
    SINGLE,
    /** Double-line tile border. */
    DOUBLE,
    /**
     * User-specified tile indices for each border segment. Requires [DialogDef.customBorderTiles]
     * to be set (8 tile indices: TL, TR, BL, BR, H-top, H-bottom, V-left, V-right).
     */
    CUSTOM,
}

/** Layout direction for menu items. */
enum class MenuLayout {
    /** Items arranged top-to-bottom (default). */
    VERTICAL,
    /** Items arranged left-to-right. */
    HORIZONTAL,
    /** Items arranged in a grid with configurable column count. */
    GRID,
}

/** Screen anchor position for HUD panels. */
enum class Anchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CENTER,
}

/** Text horizontal alignment. */
enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/** Dialog and menu scroll behavior. */
enum class ScrollBehavior {
    /** Auto-scroll with a scroll indicator arrow. */
    AUTO_SCROLL,
    /** Page-based navigation (player presses A to advance pages). */
    PAGE_BASED,
}

/** Display mode for icon-based HUD counters (hearts, stars, etc.). */
enum class IconDisplayMode {
    /** Show both full and empty icons (Zelda-style heart containers). */
    FULL_AND_EMPTY,
    /** Show only filled icons (Pokemon-style badge display). */
    FILLED_ONLY,
}

/**
 * Font rendering mode for text output operations.
 * - [FIXED_WIDTH]: default tile-based rendering using GBDK `_win_print_at` helper — compatible with
 *   all Game Boy models.
 * - [VARIABLE_WIDTH]: variable-width font rendering using `_vwf_print_at` — GBC preferred, requires
 *   VWF font data loaded into VRAM. Both paths are fully implemented in codegen.
 */
enum class FontMode {
    FIXED_WIDTH,
    VARIABLE_WIDTH,
}

// -----------------------------------------------------------------------------
// Dialog IR
// -----------------------------------------------------------------------------

/**
 * Defines a named dialog box configuration.
 *
 * Dialog boxes are rendered on the GBDK window layer via `_win_*` helpers. The dialog is referenced
 * by ID in [DialogSay] and [DialogChoice] script ops.
 *
 * @property id Unique dialog identifier.
 * @property textSpeed Typewriter effect speed (characters per frame). Default 1 = one char/frame.
 * @property border Border rendering style.
 * @property speaker Optional speaker name displayed above the dialog box.
 * @property portrait Optional portrait sprite asset displayed beside the dialog box.
 * @property boxX Dialog box left edge in tiles (window layer coordinates).
 * @property boxY Dialog box top edge in tiles (window layer coordinates).
 * @property boxWidth Dialog box width in tiles.
 * @property boxHeight Dialog box height in tiles.
 * @property customBorderTiles For [BorderStyle.CUSTOM] borders: 8 tile indices in order TL, TR, BL,
 *   BR, H-top, H-bottom, V-left, V-right.
 * @property fontMode Font rendering path selection. [FontMode.FIXED_WIDTH] uses tile-based
 *   `_win_print_at`; [FontMode.VARIABLE_WIDTH] uses the VWF rendering path.
 */
data class DialogDef(
    val id: String,
    val textSpeed: Int = 1,
    val border: BorderStyle = BorderStyle.NONE,
    val speaker: String? = null,
    val portrait: AssetRef? = null,
    val boxX: Int = 0,
    val boxY: Int = 14,
    val boxWidth: Int = 20,
    val boxHeight: Int = 4,
    val customBorderTiles: List<Int>? = null,
    val fontMode: FontMode = FontMode.FIXED_WIDTH,
)

/**
 * A segment of dialog text.
 *
 * Dialog segments allow mixing static strings with dynamic expression values (e.g. variable
 * interpolation: `"That'll be "`, price, `" gold."`).
 */
sealed interface DialogSegment

/** A static text segment in a dialog. */
data class DialogTextSegment(val text: String) : DialogSegment

/** A dynamic expression segment in a dialog (evaluated at runtime). */
data class DialogExprSegment(val expr: Expr) : DialogSegment

/** A single choice option in a dialog choice prompt. */
data class DialogOption(
    /** The label text displayed to the player. Should be i18n-ready (no magic strings). */
    val label: String,
    /** Script ops to execute when the player selects this option. */
    val body: List<ScriptOp>,
)

// -----------------------------------------------------------------------------
// Menu IR
// -----------------------------------------------------------------------------

/**
 * Dynamic data source for menu population.
 *
 * Enables menus that auto-populate from runtime data rather than static item lists.
 */
sealed interface MenuDataSource

/** Bind menu items to the contents of a registered inventory. */
data class InventoryDataSource(val inventoryId: String) : MenuDataSource

/** Bind menu items to the elements of a u8Array or similar collection. */
data class ArrayDataSource(val arrayId: String) : MenuDataSource

/**
 * Defines a named interactive menu configuration.
 *
 * Menus are rendered on the window layer by default (configurable via [renderOnWindow]). The menu
 * is opened and closed via [MenuShow] and [MenuHide] script ops.
 *
 * @property id Unique menu identifier.
 * @property layout Item arrangement direction.
 * @property cursorChar Text character used as the selection cursor (default ">").
 * @property cursorSprite Optional sprite asset for an animated cursor.
 * @property parentId Parent menu ID for automatic back-navigation on B button.
 * @property renderOnWindow When true (default), renders on the window layer; when false, renders on
 *   the background layer.
 * @property scrollBehavior How the menu handles more items than visible height.
 * @property sfxOnMove Sound effect ID played when the cursor moves between items.
 * @property sfxOnSelect Sound effect ID played when a menu item is selected.
 * @property sfxOnCancel Sound effect ID played when the player cancels / presses B.
 * @property x Menu left edge in tiles.
 * @property y Menu top edge in tiles.
 * @property width Menu width in tiles.
 * @property height Menu height in tiles.
 * @property columns Number of columns for [MenuLayout.GRID] layouts.
 * @property items Static menu item definitions. Empty when [dataSource] is set.
 * @property dataSource Dynamic data binding source (inventory or array). Null for static menus.
 */
data class MenuDef(
    val id: String,
    val layout: MenuLayout = MenuLayout.VERTICAL,
    val cursorChar: String = ">",
    val cursorSprite: AssetRef? = null,
    val parentId: String? = null,
    val renderOnWindow: Boolean = true,
    val scrollBehavior: ScrollBehavior = ScrollBehavior.AUTO_SCROLL,
    val sfxOnMove: String? = null,
    val sfxOnSelect: String? = null,
    val sfxOnCancel: String? = null,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 20,
    val height: Int = 18,
    val columns: Int = 1,
    val items: List<MenuItemDef> = emptyList(),
    val dataSource: MenuDataSource? = null,
)

/** A static menu item that executes a script block when selected. */
data class MenuItemDef(val label: String, val body: List<ScriptOp>)

/** A toggle control menu item that flips a boolean variable. */
data class MenuToggleDef(val label: String, val variable: String)

/** A slider control menu item for adjusting a numeric variable within a range. */
data class MenuSliderDef(
    val label: String,
    val variable: String,
    val min: Int,
    val max: Int,
    val step: Int = 1,
)

/** A cycle-option control menu item that cycles through a list of choices. */
data class MenuOptionDef(val label: String, val variable: String, val choices: List<String>)

// -----------------------------------------------------------------------------
// HUD IR
// -----------------------------------------------------------------------------

/**
 * Defines a named HUD panel containing one or more HUD elements.
 *
 * HUD panels group related display elements (bars, numbers, icons) and can be shown/hidden as a
 * unit via [HudShow] and [HudHide] script ops. Renders on the window layer by default.
 *
 * @property id Unique HUD panel identifier.
 * @property anchor Shorthand positioning anchor for common placements.
 * @property tileX Explicit tile X position (overrides anchor if set).
 * @property tileY Explicit tile Y position (overrides anchor if set).
 * @property renderOnWindow When true (default), renders on the window layer; when false renders on
 *   the background layer.
 * @property elements The HUD elements contained in this panel.
 */
data class HudDef(
    val id: String,
    val anchor: Anchor = Anchor.TOP_LEFT,
    val tileX: Int? = null,
    val tileY: Int? = null,
    val renderOnWindow: Boolean = true,
    val elements: List<HudElement> = emptyList(),
)

/** Base type for all HUD display elements. */
sealed interface HudElement

/**
 * A fill-bar HUD element showing a variable value as a horizontal bar.
 *
 * The bar renders using [fillTile] for filled portions and [emptyTile] for empty portions. These
 * are GBDK font/tile indices — enabling DMG-compatible pattern-based fill without requiring GBC
 * palettes. Use [gbcPalette] to add color enhancement on GBC.
 *
 * @property id Unique element identifier within the HUD panel.
 * @property variable Name of the game variable bound to this bar's current value.
 * @property maxVariable Name of a variable holding the maximum value (null uses [maxValue]).
 * @property maxValue Fixed maximum value when [maxVariable] is null.
 * @property width Bar width in tiles.
 * @property fillTile GBDK tile index for the filled (full) portion of the bar. Default 0x01.
 * @property emptyTile GBDK tile index for the empty (depleted) portion of the bar. Default 0x00.
 * @property fillFrames Number of frames to animate bar fill/drain transitions. 0 = instant.
 * @property gbcPalette Optional GBC palette index for color enhancement on GBC hardware.
 */
data class HudBar(
    val id: String,
    val variable: String,
    val maxVariable: String? = null,
    val maxValue: Int = 100,
    val width: Int = 8,
    val fillTile: Int = 0x01,
    val emptyTile: Int = 0x00,
    val fillFrames: Int = 0,
    val gbcPalette: Int? = null,
) : HudElement

/**
 * A numeric display HUD element showing a variable value as text.
 *
 * @property id Unique element identifier within the HUD panel.
 * @property variable Name of the game variable to display.
 * @property label Optional label prefix displayed before the number (e.g. "HP: ").
 * @property format C-style format string for the number (e.g. "%d", "%04d").
 */
data class HudNumber(
    val id: String,
    val variable: String,
    val label: String = "",
    val format: String = "%d",
) : HudElement

/**
 * An icon-counter HUD element showing a variable value as discrete icons (hearts, stars, etc.).
 *
 * @property id Unique element identifier within the HUD panel.
 * @property variable Name of the game variable holding the current icon count.
 * @property maxValue Total number of icon slots to render.
 * @property fullTile GBDK tile index for a filled icon slot.
 * @property emptyTile GBDK tile index for an empty icon slot.
 * @property displayMode Whether to show full+empty icons or only filled icons.
 */
data class HudIcons(
    val id: String,
    val variable: String,
    val maxValue: Int,
    val fullTile: Int = 0,
    val emptyTile: Int = 1,
    val displayMode: IconDisplayMode = IconDisplayMode.FULL_AND_EMPTY,
) : HudElement
