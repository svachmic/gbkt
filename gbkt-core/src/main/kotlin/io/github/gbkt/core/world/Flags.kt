/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRClearFlag
import io.github.gbkt.core.ir.IRFlagIsSet
import io.github.gbkt.core.ir.IRSetFlag
import io.github.gbkt.core.ir.IRToggleFlag

// =============================================================================
// GLOBAL FLAGS SYSTEM
// =============================================================================

/**
 * A reference to a single flag in the global flags array.
 *
 * Usage:
 * ```kotlin
 * val chest1Opened by flag()
 * val bossDefeated by flag()
 *
 * if (chest1Opened.isSet) { ... }
 * chest1Opened.set()
 * bossDefeated.clear()
 * ```
 */
class FlagRef
internal constructor(
    /** Flag name for code generation */
    val name: String,
    /** Page index (stored directly to avoid mutable page reference issues) */
    private val pageIndex: Int,
    /** Index within the page (0-31) */
    val indexInPage: Int,
) {
    /** Page this flag belongs to (set after page is fully built) */
    internal var page: FlagPage? = null
        private set

    /** Update the page reference after build */
    internal fun bindToPage(finalPage: FlagPage) {
        page = finalPage
    }

    /** Global bit index (page * 32 + indexInPage) */
    val globalIndex: Int
        get() = pageIndex * 32 + indexInPage

    /** Byte offset in the flags array */
    val byteOffset: Int
        get() = globalIndex / 8

    /** Bit mask within the byte */
    val bitMask: Int
        get() = 1 shl (globalIndex % 8)
}

/**
 * A page of flags (up to 32 flags per page).
 *
 * Pages help organize flags by category and map to 4-byte chunks in the flags array, making
 * persistence efficient.
 */
class FlagPage(
    /** Page name for organization */
    val name: String,
    /** Page index (0-7 for 256 flags total) */
    val pageIndex: Int,
    /** Flags in this page */
    val flags: List<FlagRef>,
) {
    init {
        require(pageIndex in 0..7) { "Page index must be 0-7 (max 8 pages, 256 flags)" }
        require(flags.size <= 32) { "Maximum 32 flags per page" }
    }
}

/**
 * Global flags system for persisting game state.
 *
 * Provides 256 boolean flags organized into 8 pages of 32 flags each. Flags are stored as a 32-byte
 * array for efficient save/load.
 *
 * Usage:
 * ```kotlin
 * val gameFlags = flags {
 *     page("chests") {
 *         flag("chest1Opened")
 *         flag("chest2Opened")
 *     }
 *     page("story") {
 *         flag("talkedToKing")
 *         flag("defeatedBoss")
 *     }
 * }
 * ```
 */
class GlobalFlags(
    /** All flag pages */
    val pages: List<FlagPage>
) {
    /** Total number of flags */
    val totalFlags: Int = pages.sumOf { it.flags.size }

    /** Size in bytes (32 bytes for 256 flags) */
    val sizeInBytes: Int = 32

    /** Cached flag lookup by name for O(1) access */
    private val flagsByName: Map<String, FlagRef> = buildMap {
        pages.forEach { page -> page.flags.forEach { flag -> put(flag.name, flag) } }
    }

    /** Cached page lookup by name for O(1) access */
    private val pagesByName: Map<String, FlagPage> = pages.associateBy { it.name }

    /** Get a flag by name across all pages - O(1) lookup */
    fun getFlag(name: String): FlagRef? = flagsByName[name]

    /** Get a page by name - O(1) lookup */
    fun getPage(name: String): FlagPage? = pagesByName[name]
}

// =============================================================================
// FLAGS BUILDER
// =============================================================================

/** Builder for global flags system. */
@GbktDsl
class GlobalFlagsBuilder {
    private val pages = mutableListOf<FlagPage>()
    private var nextPageIndex = 0

    /**
     * Define a page of flags.
     *
     * Usage:
     * ```kotlin
     * page("chests") {
     *     flag("chest1Opened")
     *     flag("chest2Opened")
     * }
     * ```
     */
    fun page(name: String, init: FlagPageBuilder.() -> Unit) {
        require(nextPageIndex < 8) { "Maximum 8 flag pages (256 flags total)" }

        val builder = FlagPageBuilder(name, nextPageIndex)
        builder.init()
        pages.add(builder.build())
        nextPageIndex++
    }

    internal fun build() = GlobalFlags(pages.toList())
}

/** Builder for a flag page. */
@GbktDsl
class FlagPageBuilder(private val pageName: String, private val pageIndex: Int) {
    private val flags = mutableListOf<FlagRef>()
    private var nextFlagIndex = 0

    /**
     * Define a flag in this page.
     *
     * Usage:
     * ```kotlin
     * flag("chest1Opened")
     * flag("bossDefeated")
     * ```
     */
    fun flag(name: String): FlagRef {
        require(nextFlagIndex < 32) { "Maximum 32 flags per page" }

        val flagRef = FlagRef(name, pageIndex, nextFlagIndex)
        flags.add(flagRef)
        nextFlagIndex++
        return flagRef
    }

    internal fun build(): FlagPage {
        val page = FlagPage(pageName, pageIndex, flags.toList())
        // Bind all flag refs to the final page so flagRef.page returns the complete page
        flags.forEach { flag -> flag.bindToPage(page) }
        return page
    }
}

// =============================================================================
// FLAG RUNTIME OPERATIONS
// =============================================================================

/**
 * Runtime flag operations for use in game logic.
 *
 * These generate IR statements that compile to efficient bit operations.
 */
class FlagOperations(private val flags: GlobalFlags) {

    /** Check if a flag is set (returns expression for use in conditions) */
    fun isSet(flag: FlagRef): IRFlagIsSet = IRFlagIsSet(flag)

    /** Check if a flag is set by name */
    fun isSet(flagName: String): IRFlagIsSet? {
        val flag = flags.getFlag(flagName) ?: return null
        return IRFlagIsSet(flag)
    }

    /** Set a flag */
    fun set(flag: FlagRef): IRSetFlag = IRSetFlag(flag)

    /** Clear a flag */
    fun clear(flag: FlagRef): IRClearFlag = IRClearFlag(flag)

    /** Toggle a flag */
    fun toggle(flag: FlagRef): IRToggleFlag = IRToggleFlag(flag)
}

// =============================================================================
// DSL FUNCTION
// =============================================================================

/**
 * Create a global flags system.
 *
 * Usage:
 * ```kotlin
 * val gameFlags = flags {
 *     page("chests") {
 *         flag("chest1Opened")
 *         flag("chest2Opened")
 *     }
 *     page("story") {
 *         flag("talkedToKing")
 *         flag("defeatedBoss")
 *     }
 * }
 * ```
 */
fun flags(init: GlobalFlagsBuilder.() -> Unit): GlobalFlags {
    val builder = GlobalFlagsBuilder()
    builder.init()
    return builder.build()
}

/** Extension to register flags with a GameBuilder. */
fun GameBuilder.flags(init: GlobalFlagsBuilder.() -> Unit): GlobalFlags {
    val flagSystem = io.github.gbkt.core.world.flags(init)
    registerFlags(flagSystem)
    return flagSystem
}

// =============================================================================
// FLAG REFERENCE DSL EXTENSIONS
// =============================================================================

/**
 * Check if this flag is set (returns Condition for use in whenever).
 *
 * Usage:
 * ```kotlin
 * whenever(myFlag.isSet()) {
 *     // flag is true
 * }
 * ```
 */
fun FlagRef.isSet(): Condition = Condition(IRFlagIsSet(this))

/**
 * Set this flag to true.
 *
 * Usage:
 * ```kotlin
 * onInteract {
 *     myFlag.set()
 * }
 * ```
 */
fun FlagRef.set() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRSetFlag(this))
    }
}

/**
 * Clear this flag (set to false).
 *
 * Usage:
 * ```kotlin
 * onInteract {
 *     myFlag.clear()
 * }
 * ```
 */
fun FlagRef.clear() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRClearFlag(this))
    }
}

/**
 * Toggle this flag (flip its value).
 *
 * Usage:
 * ```kotlin
 * onInteract {
 *     myFlag.toggle()
 * }
 * ```
 */
fun FlagRef.toggle() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRToggleFlag(this))
    }
}
