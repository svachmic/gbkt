/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * A single localized string entry.
 *
 * @property key The identifier for this string (e.g., "druid_heal", "victory")
 * @property value The actual string content with optional placeholders
 */
data class GameString(val key: String, val value: String)

/**
 * A namespace of related strings with a target ROM bank.
 *
 * @property name The namespace name (e.g., "ability", "battle", "monster")
 * @property bank The ROM bank where these strings will be placed (0-31)
 * @property strings The strings in this namespace
 */
data class StringNamespace(val name: String, val bank: Int, val strings: List<GameString>) {
    /** Total size of all strings in this namespace (including null terminators). */
    val sizeBytes: Int
        get() = strings.sumOf { it.value.length + 1 }
}

/** Collection of all game strings organized by namespace. */
data class StringTable(val namespaces: List<StringNamespace>) {
    /** Get a namespace by name. */
    fun namespace(name: String): StringNamespace? = namespaces.find { it.name == name }

    /** Get all strings across all namespaces. */
    val allStrings: List<GameString>
        get() = namespaces.flatMap { it.strings }

    /** Total size of all strings in bytes. */
    val totalSizeBytes: Int
        get() = namespaces.sumOf { it.sizeBytes }

    /** Get namespaces grouped by bank. */
    val byBank: Map<Int, List<StringNamespace>>
        get() = namespaces.groupBy { it.bank }
}
