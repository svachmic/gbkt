/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * IR node representing a game scene.
 *
 * A scene is a named collection of lifecycle script handlers:
 * - [enterOps]: run once when entering the scene
 * - [frameOps]: run every frame while in the scene
 * - [exitOps]: run once when leaving the scene
 *
 * [actorIds] lists the IDs of actors that are active in this scene.
 *
 * [tilesetRef] is an optional reference to a tileset asset used as the scene's background. Used by
 * VRAMLayoutPass to estimate background tile usage. Null means no dedicated background tileset —
 * the scene uses no BG tiles beyond the global font/UI allocation.
 *
 * [collisionData] is an optional byte array of tile passability data extracted from a TMX or LDtk
 * collision layer. Each byte represents one tile: 0 = passable, non-zero = wall. Array length must
 * equal `mapWidth * mapHeight`. Null means no collision data for this scene (movement
 * unrestricted).
 *
 * [mapWidth] is the width of the collision map in tiles. Required when [collisionData] is non-null.
 *
 * Implements [PlatformAnnotatable] — the backend can assign bank slots and VRAM ranges for
 * scene-local assets. All annotation fields default to null.
 */
data class SceneIR(
    val id: String,
    val enterOps: List<ScriptOp> = emptyList(),
    val frameOps: List<ScriptOp> = emptyList(),
    val exitOps: List<ScriptOp> = emptyList(),
    val actorIds: List<String> = emptyList(),
    val tilesetRef: AssetRef? = null,
    val collisionData: ByteArray? = null,
    val mapWidth: Int? = null,
    /** Zone IDs to load on scene-enter (SEED-014; scene-to-zone binder DSL per CONTEXT D-01). */
    val zoneRefs: List<String> = emptyList(),
    val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
    /**
     * The ROM bank allocated for this scene's zone data by [allocateZoneBanks] in the pipeline.
     *
     * Pipeline-transient annotation (D-01, Phase 13.8 Plan 06): populated by
     * [GBDKPipeline.buildCFiles] after [allocateZoneBanks] returns via
     * `scene.copy(allocatedZoneBank = zoneBank)`. Null when the scene has no [zoneRefs] or in
     * pre-bank-allocation contexts (e.g. unit tests that do not run the full pipeline).
     *
     * Single source of truth: [SceneVisitor] reads this field for the [_bkg_tiles_load_banked]
     * bank literal, replacing a direct [zoneBankAllocation] map lookup at the visitor level.
     * This structural guarantee prevents future divergence if [allocateZoneBanks] were called
     * with inconsistent inputs at different pipeline stages (D-01 field-over-lookup).
     *
     * NOT serialized by [GameIRSerializer]: treated as a pipeline-transient annotation like
     * [bankSlot]/[vramRange]/[oamSlot] — recomputed on every codegen run. Omitting it from
     * serialization keeps the JSON schema stable and avoids stale-bank round-trip hazards.
     *
     * Contrast with runtime `_<id>_subPalette` variable (RESEARCH Pitfall 5): this field is a
     * compile-time bank number, not a runtime palette-slot selector.
     */
    val allocatedZoneBank: Int? = null,
) : PlatformAnnotatable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SceneIR) return false
        return id == other.id &&
            enterOps == other.enterOps &&
            frameOps == other.frameOps &&
            exitOps == other.exitOps &&
            actorIds == other.actorIds &&
            tilesetRef == other.tilesetRef &&
            collisionData.contentEquals(other.collisionData) &&
            mapWidth == other.mapWidth &&
            zoneRefs == other.zoneRefs &&
            sourceLocation == other.sourceLocation &&
            bankSlot == other.bankSlot &&
            vramRange == other.vramRange &&
            oamSlot == other.oamSlot &&
            allocatedZoneBank == other.allocatedZoneBank
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + enterOps.hashCode()
        result = 31 * result + frameOps.hashCode()
        result = 31 * result + exitOps.hashCode()
        result = 31 * result + actorIds.hashCode()
        result = 31 * result + (tilesetRef?.hashCode() ?: 0)
        result = 31 * result + (collisionData?.contentHashCode() ?: 0)
        result = 31 * result + (mapWidth ?: 0)
        result = 31 * result + zoneRefs.hashCode()
        result = 31 * result + (sourceLocation?.hashCode() ?: 0)
        result = 31 * result + (bankSlot?.hashCode() ?: 0)
        result = 31 * result + (vramRange?.hashCode() ?: 0)
        result = 31 * result + (oamSlot?.hashCode() ?: 0)
        result = 31 * result + (allocatedZoneBank ?: 0)
        return result
    }
}
