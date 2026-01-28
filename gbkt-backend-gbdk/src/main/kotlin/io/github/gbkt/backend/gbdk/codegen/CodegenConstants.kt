/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

// =============================================================================
// CODEGEN SENTINEL VALUES
// Constants for special "none" / "invalid" / "full" values used in code generation.
// Using 255 (0xFF) as a sentinel is a common Game Boy pattern since it's the max UINT8.
// =============================================================================

/** Sentinel value for "no item" / empty inventory slot. Used in item/inventory code generation. */
internal const val SENTINEL_NO_ITEM = 255

/**
 * Sentinel value for "no equipment slot" / item not equippable. Used as a string for direct C code
 * output.
 */
internal const val SENTINEL_NO_EQUIP_SLOT = "255"

/**
 * Sentinel value for "no flag" / object has no persistence flag. Used in map object code
 * generation.
 */
internal const val SENTINEL_NO_FLAG = 255

/**
 * Sentinel value for "no object at position". Returned when searching for map objects at a position
 * yields no result.
 */
internal const val SENTINEL_NO_OBJECT = 255

/**
 * Sentinel value for "pool full" - no free slot available for spawning. Returned by pool spawn
 * functions when all slots are occupied.
 */
internal const val SENTINEL_POOL_FULL = 255

/**
 * Sentinel value for "no mixer group" - channel not assigned to any group. Used in audio mixer
 * channel-to-group mapping.
 */
internal const val SENTINEL_NO_MIXER_GROUP = 255

/**
 * Sentinel value for "no slot found" in status effect arrays. Used when searching for an existing
 * effect or free slot.
 */
internal const val SENTINEL_NO_SLOT = 255

/**
 * Special duration value indicating effect lasts until battle ends. Status effects with this
 * duration are cleared when battle ends.
 */
internal const val DURATION_UNTIL_BATTLE_END = 255

/**
 * Special duration value indicating effect is permanent. Status effects with this duration never
 * expire naturally.
 */
internal const val DURATION_PERMANENT = 254
