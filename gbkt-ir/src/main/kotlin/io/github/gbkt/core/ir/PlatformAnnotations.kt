/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// PLATFORM ANNOTATIONS
// =============================================================================

/**
 * ROM bank assignment for a game element. `offset` is optional — if null, the linker places it
 * anywhere within the bank.
 */
data class BankSlot(val bank: Int, val offset: Int? = null)

/** VRAM tile range reservation (tile indices, inclusive). */
data class VRAMRange(val startTile: Int, val endTile: Int)

/** OAM (Object Attribute Memory) sprite slot reservation. */
data class OAMSlot(val slot: Int)

/**
 * Marker interface for IR nodes that carry optional platform-specific annotations.
 *
 * All three fields default to null — the backend assigns them during the resource allocation phase.
 * Game authors can override them explicitly for fine-grained hardware control.
 */
interface PlatformAnnotatable {
    val bankSlot: BankSlot?
    val vramRange: VRAMRange?
    val oamSlot: OAMSlot?
}
