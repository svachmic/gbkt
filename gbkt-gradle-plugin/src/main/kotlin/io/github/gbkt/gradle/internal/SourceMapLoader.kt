/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.internal

import java.io.File
import org.gradle.api.logging.Logging
import org.json.JSONObject

/** Represents a source mapping from C code to Kotlin source. */
data class ParsedSourceMapping(
    val cLine: Int,
    val kotlinFile: String,
    val kotlinLine: Int,
    val kotlinColumn: Int = 0,
    val symbol: String? = null,
    val snippet: String? = null,
    val irNodeType: String? = null,
)

/** Represents a loaded source map. */
data class ParsedSourceMap(
    val version: String,
    val gameName: String,
    val cFile: String,
    val mappings: List<ParsedSourceMapping>,
    val bankNumber: Int? = null,
) {
    /**
     * Find the Kotlin source location for a given C line number. Returns the closest mapping if
     * exact match not found.
     */
    fun findKotlinLocation(cLine: Int): ParsedSourceMapping? {
        // First try exact match
        mappings
            .find { it.cLine == cLine }
            ?.let {
                return it
            }

        // Find the closest previous mapping (most recent mapping before this line)
        return mappings.filter { it.cLine < cLine }.maxByOrNull { it.cLine }
    }
}

/** Loader for source map files (.gbkt.map). */
object SourceMapLoader {

    private val logger = Logging.getLogger(SourceMapLoader::class.java)

    /**
     * Load a source map from a JSON file.
     *
     * @param sourceMapFile The source map file to load
     * @return Parsed source map, or null if file doesn't exist or is invalid
     */
    fun load(sourceMapFile: File): ParsedSourceMap? {
        if (!sourceMapFile.exists() || !sourceMapFile.isFile) {
            return null
        }

        try {
            val content = sourceMapFile.readText()
            val json = JSONObject(content)

            val version = json.optString("version", "1.0")
            val gameName = json.getString("gameName")
            val cFile = json.getString("cFile")
            val bankNumber = json.optInt("bankNumber", -1).takeIf { it >= 0 }
            val mappingsArray = json.getJSONArray("mappings")

            val mappings = mutableListOf<ParsedSourceMapping>()
            for (i in 0 until mappingsArray.length()) {
                val mappingObj = mappingsArray.getJSONObject(i)
                val mapping =
                    ParsedSourceMapping(
                        cLine = mappingObj.getInt("cLine"),
                        kotlinFile = mappingObj.getString("kotlinFile"),
                        kotlinLine = mappingObj.getInt("kotlinLine"),
                        kotlinColumn = mappingObj.optInt("kotlinColumn", 0),
                        symbol =
                            mappingObj.optString("symbol", null).takeIf { !it.isNullOrEmpty() },
                        snippet =
                            mappingObj.optString("snippet", null).takeIf { !it.isNullOrEmpty() },
                        irNodeType =
                            mappingObj.optString("irNodeType", null).takeIf { !it.isNullOrEmpty() },
                    )
                mappings.add(mapping)
            }

            return ParsedSourceMap(version, gameName, cFile, mappings, bankNumber)
        } catch (e: Exception) {
            logger.debug(
                "Failed to parse source map ${sourceMapFile.absolutePath}: ${e.message}",
                e,
            )
            return null
        }
    }

    /**
     * Load all source maps from a directory, scanning for all *.gbkt.map files.
     *
     * @param directory The directory to scan for source map files
     * @return Map of C file name (without path) to ParsedSourceMap
     */
    fun loadAllMaps(directory: File): Map<String, ParsedSourceMap> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyMap()
        }

        val result = mutableMapOf<String, ParsedSourceMap>()
        val mapFiles =
            directory.listFiles { file -> file.name.endsWith(".gbkt.map") } ?: return emptyMap()

        for (mapFile in mapFiles) {
            val sourceMap = load(mapFile)
            if (sourceMap != null) {
                // Key by the cFile name from the map (e.g., "main.c", "bank1.c")
                result[sourceMap.cFile] = sourceMap
            }
        }

        return result
    }

    /** Try to find the source map file next to the C source file. */
    fun findSourceMapFile(cSourceFile: File): File {
        return File(cSourceFile.parentFile, "${cSourceFile.name}.gbkt.map")
    }
}
