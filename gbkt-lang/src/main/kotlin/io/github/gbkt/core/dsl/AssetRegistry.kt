/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType

/**
 * Tracks all asset references created during DSL execution.
 *
 * The registry accumulates [AssetRef] instances so [GameBuilder.build] can include the complete
 * asset list in [GameIR][io.github.gbkt.core.ir.GameIR].
 */
class AssetRegistry {
    private val refs: MutableList<AssetRef> = mutableListOf()

    /** Records an existing [AssetRef] for inclusion in the compiled GameIR. */
    internal fun track(ref: AssetRef) {
        refs.add(ref)
    }

    /** Returns all recorded asset refs (for inclusion in the compiled GameIR). */
    fun allAssets(): List<AssetRef> = refs.toList()
}

// =============================================================================
// TOP-LEVEL DSL FACTORY
// =============================================================================

/**
 * Creates an [AssetRef] for a given path and type.
 *
 * This is a top-level function so it can be called from any builder scope without implicit receiver
 * conflicts.
 *
 * @param path Relative path to the asset file (e.g. "sprites/player.png")
 * @param type Asset processing type (default: [AssetType.GENERIC])
 */
fun asset(path: String, type: AssetType = AssetType.GENERIC): AssetRef = AssetRef(path, type)
