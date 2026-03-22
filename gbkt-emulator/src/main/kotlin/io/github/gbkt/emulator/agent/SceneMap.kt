/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.io.File

/**
 * Bidirectional mapping between scene names and their numeric indices.
 *
 * Scene indices are defined in the generated `game.h` as `#define SCENE_XXXX N`. This class parses
 * those defines and provides lookup in both directions, enabling [UatRunner.waitForScene] and
 * [UatRunner.currentScene] to work with human-readable scene names instead of raw numeric indices.
 *
 * @param entries Map of lowercase scene name to numeric index.
 */
class SceneMap(entries: Map<String, Int>) {

    private val nameToIndex: Map<String, Int> = entries.toMap()
    private val indexToName: Map<Int, String> = entries.entries.associate { it.value to it.key }

    /** Returns the numeric index for [sceneName], or null if unknown. */
    fun indexOf(sceneName: String): Int? = nameToIndex[sceneName]

    /** Returns the scene name for [sceneIndex], or null if unknown. */
    fun nameOf(sceneIndex: Int): String? = indexToName[sceneIndex]

    /** All known scene names. */
    val sceneNames: Set<String>
        get() = nameToIndex.keys

    companion object {
        private val SCENE_DEFINE_PATTERN = Regex("""#define\s+SCENE_(\w+)\s+(\d+)""")

        /**
         * Parses `#define SCENE_XXXX N` lines from a generated `game.h` file.
         *
         * Scene names are lowercased (e.g., `SCENE_TITLE` becomes `"title"`).
         *
         * @param headerFile The `game.h` file to parse.
         * @return A [SceneMap] with all discovered scene defines.
         */
        fun fromGameHeader(headerFile: File): SceneMap {
            val entries = mutableMapOf<String, Int>()
            headerFile.readLines().forEach { line ->
                SCENE_DEFINE_PATTERN.find(line)?.let { match ->
                    val name = match.groupValues[1].lowercase()
                    val index = match.groupValues[2].toInt()
                    entries[name] = index
                }
            }
            return SceneMap(entries)
        }

        /**
         * Creates a [SceneMap] from explicit name-to-index pairs.
         *
         * Useful for unit tests or when the header file is not available.
         */
        fun of(vararg pairs: Pair<String, Int>): SceneMap = SceneMap(pairs.toMap())
    }
}
