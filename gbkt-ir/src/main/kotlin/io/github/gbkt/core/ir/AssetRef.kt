/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/** Type of asset, used to determine processing pipeline. */
enum class AssetType {
    SPRITE,
    TILEMAP,
    TILESET,
    SOUND,
    MUSIC,
    FONT,
    DATA,
    GENERIC,
}

/** Reference to a game asset by path and type. */
data class AssetRef(val path: String, val type: AssetType = AssetType.GENERIC)
