/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.project.templates

/**
 * Base interface for game project templates.
 *
 * Templates define the starting code and assets for different game types.
 */
interface GameTemplate {
    /** Display name shown in the wizard */
    val displayName: String

    /** Short description of the template */
    val description: String

    /**
     * Generate the main game source code.
     *
     * @param gameName Name of the game
     * @param packageName Kotlin package for the code
     * @return The generated Kotlin source code
     */
    fun generateMainSource(gameName: String, packageName: String): String

    /**
     * Get additional source files to generate.
     *
     * @param gameName Name of the game
     * @param packageName Kotlin package for the code
     * @return Map of relative path to file content
     */
    fun getAdditionalSources(gameName: String, packageName: String): Map<String, String> =
        emptyMap()

    /**
     * Get sample asset files to include.
     *
     * @return Map of relative path (in res/) to asset content description
     */
    fun getSampleAssets(): Map<String, AssetDescription> = emptyMap()
}

/** Description of a sample asset to generate. */
sealed class AssetDescription {
    /** A placeholder text file describing what asset should go here */
    data class Placeholder(val description: String) : AssetDescription()

    /** A PNG image with specific dimensions and colors */
    data class PngImage(val width: Int, val height: Int, val description: String) :
        AssetDescription()

    /** A tilemap placeholder */
    data class Tilemap(val width: Int, val height: Int, val description: String) :
        AssetDescription()
}
