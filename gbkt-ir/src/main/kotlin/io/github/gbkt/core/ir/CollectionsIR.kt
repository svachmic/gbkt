/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// COLLECTIONS IR NODES
// =============================================================================
// Relocated from gbkt-core io.github.gbkt.core.ir.CollectionsIR (v1) to gbkt-ir (v2).
// Uses v2 VarType instead of GBVar.VarType.
// These types are preserved for Plan 06-06 (Collection System v2 codegen integration).
//
// Updated in Plan 06.6-04: element/key/value types changed from VarType to
// CollElementType so that collections can hold structured data (StructDef).
// =============================================================================

// -----------------------------------------------------------------------
// Collection descriptor types
// These represent the collection declaration itself — emitted once at game
// build time to allocate static RAM for the collection.
// -----------------------------------------------------------------------

/**
 * Hash table declaration.
 *
 * Generates a fixed-size open-addressing hash table backed by static arrays.
 *
 * @param name C identifier for the table
 * @param keyType Element type of the key stored in each slot (primitive or struct)
 * @param valueType Element type of the value stored in each slot (primitive or struct)
 * @param size Number of slots (must be power-of-2 for fast modulo)
 */
data class IRCollHashTable(
    val name: String,
    val keyType: CollElementType,
    val valueType: CollElementType,
    val size: Int,
    val sourceLocation: SourceLocation? = null,
) {
    /**
     * Compile-time byte count for this hash table.
     *
     * Layout per slot: key (keyType.byteSize) + value (valueType.byteSize) + 1 `used` byte.
     */
    val sizeBytes: Int
        get() = size * (keyType.byteSize + valueType.byteSize + 1)
}

/**
 * Object pool declaration.
 *
 * Generates a fixed-capacity pool with a free-list bitmap.
 *
 * @param name C identifier for the pool
 * @param elementType Element type of each element (primitive or struct)
 * @param capacity Maximum number of live elements
 */
data class IRCollPool(
    val name: String,
    val elementType: CollElementType,
    val capacity: Int,
    val sourceLocation: SourceLocation? = null,
) {
    /**
     * Compile-time byte count for this pool.
     *
     * Layout: capacity slots × (element size + 1 free-list byte) + 1 count byte.
     */
    val sizeBytes: Int
        get() = capacity * (elementType.byteSize + 1) + 1
}

/**
 * Ring buffer declaration.
 *
 * Generates a FIFO ring buffer backed by a static array.
 *
 * @param name C identifier for the buffer
 * @param elementType Element type of each element (primitive or struct)
 * @param capacity Maximum number of buffered elements
 */
data class IRCollRingBuffer(
    val name: String,
    val elementType: CollElementType,
    val capacity: Int,
    val sourceLocation: SourceLocation? = null,
) {
    /**
     * Compile-time byte count for this ring buffer.
     *
     * Layout: capacity × element size + 3 bytes (head, tail, count).
     */
    val sizeBytes: Int
        get() = capacity * elementType.byteSize + 3
}

/**
 * Fixed-slots declaration.
 *
 * Generates a fixed array of N slots with an active-set bitfield.
 *
 * @param name C identifier for the slots
 * @param elementType Element type of each slot element (primitive or struct)
 * @param count Number of slots (1–16)
 */
data class IRCollFixedSlots(
    val name: String,
    val elementType: CollElementType,
    val count: Int,
    val namedSlots: Map<String, Int> = emptyMap(),
    val sourceLocation: SourceLocation? = null,
) {
    /**
     * Compile-time byte count for these fixed slots.
     *
     * Layout: count × element size + bitfield (UINT8 for ≤8 slots, UINT16 for 9–16 slots).
     */
    val sizeBytes: Int
        get() = count * elementType.byteSize + (if (count <= 8) 1 else 2)
}

// -----------------------------------------------------------------------
// Collection operation statements
// These represent runtime operations emitted inside script blocks.
// -----------------------------------------------------------------------

/** Insert a key-value pair into a hash table. */
data class IRCollHashTableInsert(val tableName: String, val sourceLocation: SourceLocation? = null)

/** Clear all entries in a hash table. */
data class IRCollHashTableClear(val tableName: String, val sourceLocation: SourceLocation? = null)

/**
 * Allocate an element from a pool.
 *
 * Stores the allocated index (0–capacity-1) into [resultVar]. If pool is full, [resultVar] is set
 * to 0xFF.
 */
data class IRCollPoolAlloc(
    val poolName: String,
    val resultVar: String,
    val sourceLocation: SourceLocation? = null,
)

/** Return an element to a pool, marking its slot as free. */
data class IRCollPoolFree(val poolName: String, val sourceLocation: SourceLocation? = null)

/** Push a value onto the tail of a ring buffer. No-op if full. */
data class IRCollRingBufferPush(val bufferName: String, val sourceLocation: SourceLocation? = null)

/**
 * Claim a free slot from a fixed-slots collection.
 *
 * Stores the claimed slot index into [resultVar]. If no slots are free, [resultVar] is set to 0xFF.
 */
data class IRCollFixedSlotsClaim(
    val slotsName: String,
    val resultVar: String,
    val sourceLocation: SourceLocation? = null,
)

/** Release a slot back to a fixed-slots collection, marking it inactive. */
data class IRCollFixedSlotsRelease(
    val slotsName: String,
    val sourceLocation: SourceLocation? = null,
)

// -----------------------------------------------------------------------
// Collection expression types
// These return values usable in conditions and assignments.
// -----------------------------------------------------------------------

/** Look up a value in a hash table by key. Returns the stored value, or 0xFF if not present. */
data class IRCollHashTableLookup(val tableName: String)

/** Returns the number of currently active elements in a pool (0–capacity). */
data class IRCollPoolActiveCount(val poolName: String)

/** Returns 1 if the pool has at least one free slot, 0 otherwise. */
data class IRCollPoolHasSpace(val poolName: String)

/**
 * Pop (remove and return) the front element from a ring buffer.
 *
 * Returns the front element value. Behaviour is undefined on empty buffer.
 */
data class IRCollRingBufferPop(val bufferName: String)

/**
 * Peek at the front element of a ring buffer without removing it.
 *
 * Returns the front element value. Behaviour is undefined on empty buffer.
 */
data class IRCollRingBufferPeek(val bufferName: String)

/** Returns the number of elements currently in the ring buffer (0–capacity). */
data class IRCollRingBufferCount(val bufferName: String)

/** Returns 1 if the given slot index is active (claimed), 0 otherwise. */
data class IRCollFixedSlotsIsActive(val slotsName: String)

// NOTE: VarType.byteSize extension is defined in Types.kt — do not re-declare here.
