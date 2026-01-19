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
package io.github.gbkt.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icon definitions for the gbkt plugin.
 *
 * Icons follow IntelliJ's icon guidelines:
 * - 16x16 for most UI elements
 * - 13x13 for tool windows
 * - SVG format for retina display support
 */
object GbktIcons {
    /** Main gbkt file icon (16x16). Used for .gbkt.kts files in the project tree. */
    @JvmField val FILE: Icon = IconLoader.getIcon("/icons/gbkt.svg", GbktIcons::class.java)

    /** gbkt tool window icon (13x13). */
    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/gbkt_tool.svg", GbktIcons::class.java)

    /** Scene icon for scene definitions in structure view. */
    @JvmField val SCENE: Icon = IconLoader.getIcon("/icons/scene.svg", GbktIcons::class.java)

    /** Entity icon for entity definitions in structure view. */
    @JvmField val ENTITY: Icon = IconLoader.getIcon("/icons/entity.svg", GbktIcons::class.java)

    /** Sprite icon for sprite assets. */
    @JvmField val SPRITE: Icon = IconLoader.getIcon("/icons/sprite.svg", GbktIcons::class.java)
}
