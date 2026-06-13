/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.api.CollectionCodegen
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI16
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.CollElementType
import io.github.gbkt.core.ir.IRCollFixedSlots
import io.github.gbkt.core.ir.IRCollHashTable
import io.github.gbkt.core.ir.IRCollPool
import io.github.gbkt.core.ir.IRCollRingBuffer
import io.github.gbkt.core.ir.VarType

// =============================================================================
// GBDK COLLECTION CODEGEN
// =============================================================================
// Implements CollectionCodegen for the GBDK backend.
//
// Generates static C arrays and bookkeeping functions for the four collection
// types: hash table, object pool, ring buffer, and fixed slots.
//
// All generated C code targets GBDK's C89/C90 subset (lcc compiler):
// - No variable declarations inside for() init
// - UINT8/INT8/UINT16/INT16 GBDK type aliases
// - Static arrays for compile-time RAM allocation
// =============================================================================

/**
 * GBDK backend implementation of the [CollectionCodegen] interface.
 *
 * Generates static GBDK C arrays and bookkeeping functions for all four supported collection types.
 * All output is C89-compatible for GBDK's lcc compiler.
 *
 * Usage:
 * ```kotlin
 * val gen = GBDKCollectionCodegen()
 * val data = gen.generateHashTableData("scores", "UINT8", "UINT16", 16)
 * val funcs = gen.generateHashTableFunctions("scores", "UINT8", "UINT16", 16)
 * ```
 */
class GBDKCollectionCodegen : CollectionCodegen {

    // -----------------------------------------------------------------------
    // Convenience wrappers — accept IR types and delegate to string methods
    // -----------------------------------------------------------------------

    /** Generate data declarations for a hash table from its [IRCollHashTable] descriptor. */
    fun generateHashTableData(ht: IRCollHashTable): String =
        generateHashTableData(ht.name, ht.keyType.cTypeName, ht.valueType.cTypeName, ht.size)

    /** Generate helper functions for a hash table from its [IRCollHashTable] descriptor. */
    fun generateHashTableFunctions(ht: IRCollHashTable): String =
        generateHashTableFunctions(ht.name, ht.keyType.cTypeName, ht.valueType.cTypeName, ht.size)

    /** Generate data declarations for an object pool from its [IRCollPool] descriptor. */
    fun generatePoolData(pool: IRCollPool): String =
        generatePoolData(pool.name, pool.elementType.cTypeName, pool.capacity)

    /** Generate helper functions for an object pool from its [IRCollPool] descriptor. */
    fun generatePoolFunctions(pool: IRCollPool): String =
        generatePoolFunctions(pool.name, pool.elementType.cTypeName, pool.capacity)

    /** Generate data declarations for a ring buffer from its [IRCollRingBuffer] descriptor. */
    fun generateRingBufferData(rb: IRCollRingBuffer): String =
        generateRingBufferData(rb.name, rb.elementType.cTypeName, rb.capacity)

    /** Generate helper functions for a ring buffer from its [IRCollRingBuffer] descriptor. */
    fun generateRingBufferFunctions(rb: IRCollRingBuffer): String =
        generateRingBufferFunctions(rb.name, rb.elementType.cTypeName, rb.capacity)

    /**
     * Generate data declarations for a fixed-slots collection from its [IRCollFixedSlots]
     * descriptor.
     */
    fun generateFixedSlotsData(fs: IRCollFixedSlots): String =
        generateFixedSlotsData(fs.name, fs.elementType.cTypeName, fs.count)

    /**
     * Generate helper functions for a fixed-slots collection from its [IRCollFixedSlots]
     * descriptor.
     */
    fun generateFixedSlotsFunctions(fs: IRCollFixedSlots): String =
        generateFixedSlotsFunctions(fs.name, fs.elementType.cTypeName, fs.count)

    // -----------------------------------------------------------------------
    // Hash table
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for a hash table.
     *
     * Layout:
     * - `_ht_<name>_keys[size]` — key array (pre-zeroed)
     * - `_ht_<name>_values[size]` — value array (pre-zeroed)
     * - `_ht_<name>_used[size]` — slot-occupied flags (all zero = empty table)
     */
    override fun generateHashTableData(
        name: String,
        keyType: String,
        valueType: String,
        size: Int,
    ): String = buildString {
        appendLine("/* Hash table: $name ($size slots, key=$keyType, value=$valueType) */")
        appendLine("$keyType _ht_${name}_keys[$size];")
        appendLine("$valueType _ht_${name}_values[$size];")
        append("UINT8 _ht_${name}_used[$size];")
    }

    /**
     * Generate helper functions for a hash table: insert, lookup, get, contains, remove, clear.
     *
     * Uses open addressing with linear probing. Hash function: `key % size`.
     * - `ht_<name>_insert(key, value)` — inserts or updates a key-value pair
     * - `ht_<name>_lookup(key, out)` — looks up a key; returns 1 on hit (sets *out), 0 on miss
     * - `ht_<name>_get(key)` — returns value for key, or 0 if not found
     * - `ht_<name>_contains(key)` — returns 1 if key is present, 0 otherwise
     * - `ht_<name>_remove(key)` — removes a key from the table (marks slot unused)
     * - `ht_<name>_clear()` — clears all entries (zeroes used[] array)
     */
    override fun generateHashTableFunctions(
        name: String,
        keyType: String,
        valueType: String,
        size: Int,
    ): String = buildString {
        // insert
        appendLine("void ht_${name}_insert($keyType key, $valueType value) {")
        appendLine("    UINT8 i;")
        appendLine("    UINT8 slot = (UINT8)(key % $size);")
        appendLine("    for (i = 0; i < $size; i++) {")
        appendLine("        if (!_ht_${name}_used[slot] || _ht_${name}_keys[slot] == key) {")
        appendLine("            _ht_${name}_keys[slot] = key;")
        appendLine("            _ht_${name}_values[slot] = value;")
        appendLine("            _ht_${name}_used[slot] = 1;")
        appendLine("            return;")
        appendLine("        }")
        appendLine("        slot = (UINT8)((slot + 1) % $size);")
        appendLine("    }")
        appendLine("}")
        appendLine()
        // lookup
        appendLine("UINT8 ht_${name}_lookup($keyType key, $valueType *out) {")
        appendLine("    UINT8 i;")
        appendLine("    UINT8 slot = (UINT8)(key % $size);")
        appendLine("    for (i = 0; i < $size; i++) {")
        appendLine("        if (!_ht_${name}_used[slot]) return 0;")
        appendLine("        if (_ht_${name}_keys[slot] == key) {")
        appendLine("            *out = _ht_${name}_values[slot];")
        appendLine("            return 1;")
        appendLine("        }")
        appendLine("        slot = (UINT8)((slot + 1) % $size);")
        appendLine("    }")
        appendLine("    return 0;")
        appendLine("}")
        appendLine()
        // get — returns value directly (0 if not found)
        appendLine("$valueType ht_${name}_get($keyType key) {")
        appendLine("    UINT8 i;")
        appendLine("    UINT8 slot = (UINT8)(key % $size);")
        appendLine("    for (i = 0; i < $size; i++) {")
        appendLine("        if (!_ht_${name}_used[slot]) return 0;")
        appendLine("        if (_ht_${name}_keys[slot] == key) {")
        appendLine("            return _ht_${name}_values[slot];")
        appendLine("        }")
        appendLine("        slot = (UINT8)((slot + 1) % $size);")
        appendLine("    }")
        appendLine("    return 0;")
        appendLine("}")
        appendLine()
        // contains
        appendLine("UINT8 ht_${name}_contains($keyType key) {")
        appendLine("    UINT8 i;")
        appendLine("    UINT8 slot = (UINT8)(key % $size);")
        appendLine("    for (i = 0; i < $size; i++) {")
        appendLine("        if (!_ht_${name}_used[slot]) return 0;")
        appendLine("        if (_ht_${name}_keys[slot] == key) return 1;")
        appendLine("        slot = (UINT8)((slot + 1) % $size);")
        appendLine("    }")
        appendLine("    return 0;")
        appendLine("}")
        appendLine()
        // remove
        appendLine("void ht_${name}_remove($keyType key) {")
        appendLine("    UINT8 i;")
        appendLine("    UINT8 slot = (UINT8)(key % $size);")
        appendLine("    for (i = 0; i < $size; i++) {")
        appendLine("        if (!_ht_${name}_used[slot]) return;")
        appendLine("        if (_ht_${name}_keys[slot] == key) {")
        appendLine("            _ht_${name}_used[slot] = 0;")
        appendLine("            return;")
        appendLine("        }")
        appendLine("        slot = (UINT8)((slot + 1) % $size);")
        appendLine("    }")
        appendLine("}")
        appendLine()
        // clear
        appendLine("void ht_${name}_clear(void) {")
        appendLine("    UINT8 i;")
        appendLine("    for (i = 0; i < $size; i++) {")
        appendLine("        _ht_${name}_used[i] = 0;")
        appendLine("    }")
        append("}")
    }

    // -----------------------------------------------------------------------
    // Object pool
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for an object pool.
     *
     * Layout:
     * - `_pool_<name>_data[capacity]` — element storage array
     * - `_pool_<name>_free[ceil(capacity/8)]` — free-list bitmap (1=free, 0=allocated)
     * - `_pool_<name>_count` — number of currently allocated elements
     *
     * The free-list bitmap uses 1 bit per slot packed into UINT8 bytes.
     */
    override fun generatePoolData(name: String, elementType: String, capacity: Int): String {
        val bitmapSize = (capacity + 7) / 8
        return buildString {
            appendLine("/* Object pool: $name (capacity=$capacity, element=$elementType) */")
            appendLine("$elementType _pool_${name}_data[$capacity];")
            appendLine("UINT8 _pool_${name}_free[$bitmapSize];")
            append("UINT8 _pool_${name}_count;")
        }
    }

    /**
     * Generate helper functions for an object pool: alloc, free, activeCount, hasSpace.
     * - `pool_<name>_alloc()` — allocates next free slot; returns index (0–capacity-1) or 0xFF if
     *   full
     * - `pool_<name>_free(idx)` — releases a slot back to the pool
     * - `pool_<name>_activeCount()` — returns number of currently allocated elements
     * - `pool_<name>_hasSpace()` — returns 1 if at least one free slot remains
     */
    override fun generatePoolFunctions(name: String, elementType: String, capacity: Int): String =
        buildString {
            // alloc
            appendLine("UINT8 pool_${name}_alloc(void) {")
            appendLine("    UINT8 i;")
            appendLine("    UINT8 byte_idx;")
            appendLine("    UINT8 bit_idx;")
            appendLine("    for (i = 0; i < $capacity; i++) {")
            appendLine("        byte_idx = (UINT8)(i >> 3);")
            appendLine("        bit_idx = (UINT8)(i & 7);")
            appendLine("        if (!(_pool_${name}_free[byte_idx] & (1 << bit_idx))) {")
            appendLine("            _pool_${name}_free[byte_idx] |= (1 << bit_idx);")
            appendLine("            _pool_${name}_count++;")
            appendLine("            return i;")
            appendLine("        }")
            appendLine("    }")
            appendLine("    return 0xFF;")
            appendLine("}")
            appendLine()
            // free
            appendLine("void pool_${name}_free(UINT8 idx) {")
            appendLine("    UINT8 byte_idx = (UINT8)(idx >> 3);")
            appendLine("    UINT8 bit_idx = (UINT8)(idx & 7);")
            appendLine(
                "    if (idx < $capacity && (_pool_${name}_free[byte_idx] & (1 << bit_idx))) {"
            )
            appendLine("        _pool_${name}_free[byte_idx] &= (UINT8)~(1 << bit_idx);")
            appendLine("        if (_pool_${name}_count > 0) _pool_${name}_count--;")
            appendLine("    }")
            appendLine("}")
            appendLine()
            // activeCount
            appendLine("UINT8 pool_${name}_activeCount(void) {")
            appendLine("    return _pool_${name}_count;")
            appendLine("}")
            appendLine()
            // hasSpace
            appendLine("UINT8 pool_${name}_hasSpace(void) {")
            appendLine("    return (_pool_${name}_count < $capacity) ? 1 : 0;")
            append("}")
        }

    // -----------------------------------------------------------------------
    // Ring buffer
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for a ring buffer.
     *
     * Layout:
     * - `_rb_<name>_buf[capacity]` — element storage array
     * - `_rb_<name>_head` — index of next element to pop (read pointer)
     * - `_rb_<name>_tail` — index of next write slot (write pointer)
     * - `_rb_<name>_count` — number of elements currently in buffer
     */
    override fun generateRingBufferData(name: String, elementType: String, capacity: Int): String =
        buildString {
            appendLine("/* Ring buffer: $name (capacity=$capacity, element=$elementType) */")
            appendLine("$elementType _rb_${name}_buf[$capacity];")
            appendLine("UINT8 _rb_${name}_head;")
            appendLine("UINT8 _rb_${name}_tail;")
            append("UINT8 _rb_${name}_count;")
        }

    /**
     * Generate helper functions for a ring buffer: push, pop, peek, count.
     * - `rb_<name>_push(val)` — pushes to tail; no-op if full
     * - `rb_<name>_pop()` — pops from head; behaviour undefined on empty buffer
     * - `rb_<name>_peek()` — peeks at head without removing; behaviour undefined on empty buffer
     * - `rb_<name>_count()` — returns number of elements in buffer
     */
    override fun generateRingBufferFunctions(
        name: String,
        elementType: String,
        capacity: Int,
    ): String = buildString {
        // push
        appendLine("void rb_${name}_push($elementType val) {")
        appendLine("    if (_rb_${name}_count >= $capacity) return;")
        appendLine("    _rb_${name}_buf[_rb_${name}_tail] = val;")
        appendLine("    _rb_${name}_tail = (UINT8)((_rb_${name}_tail + 1) % $capacity);")
        appendLine("    _rb_${name}_count++;")
        appendLine("}")
        appendLine()
        // pop
        appendLine("$elementType rb_${name}_pop(void) {")
        appendLine("    $elementType val = _rb_${name}_buf[_rb_${name}_head];")
        appendLine("    _rb_${name}_head = (UINT8)((_rb_${name}_head + 1) % $capacity);")
        appendLine("    if (_rb_${name}_count > 0) _rb_${name}_count--;")
        appendLine("    return val;")
        appendLine("}")
        appendLine()
        // peek
        appendLine("$elementType rb_${name}_peek(void) {")
        appendLine("    return _rb_${name}_buf[_rb_${name}_head];")
        appendLine("}")
        appendLine()
        // count
        appendLine("UINT8 rb_${name}_count(void) {")
        appendLine("    return _rb_${name}_count;")
        append("}")
    }

    // -----------------------------------------------------------------------
    // Fixed slots
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for a fixed-slots collection.
     *
     * Layout:
     * - `_fs_<name>_data[count]` — slot storage array
     * - `_fs_<name>_active` — bitfield tracking which slots are claimed (UINT8 for ≤8 slots, UINT16
     *   for 9–16 slots)
     */
    override fun generateFixedSlotsData(name: String, elementType: String, count: Int): String {
        val bitfieldType = if (count <= 8) "UINT8" else "UINT16"
        return buildString {
            appendLine("/* Fixed slots: $name (count=$count, element=$elementType) */")
            appendLine("$elementType _fs_${name}_data[$count];")
            append("$bitfieldType _fs_${name}_active;")
        }
    }

    /**
     * Generate helper functions for a fixed-slots collection: claim, release, isActive.
     * - `fs_<name>_claim()` — claims first free slot; returns slot index or 0xFF if full
     * - `fs_<name>_release(idx)` — releases a slot, marking it inactive
     * - `fs_<name>_isActive(idx)` — returns 1 if slot is claimed, 0 if free
     */
    override fun generateFixedSlotsFunctions(
        name: String,
        elementType: String,
        count: Int,
    ): String {
        val bitfieldType = if (count <= 8) "UINT8" else "UINT16"
        return buildString {
            // claim
            appendLine("UINT8 fs_${name}_claim(void) {")
            appendLine("    UINT8 i;")
            appendLine("    for (i = 0; i < $count; i++) {")
            appendLine("        if (!(_fs_${name}_active & (1 << i))) {")
            appendLine("            _fs_${name}_active |= ($bitfieldType)(1 << i);")
            appendLine("            return i;")
            appendLine("        }")
            appendLine("    }")
            appendLine("    return 0xFF;")
            appendLine("}")
            appendLine()
            // release
            appendLine("void fs_${name}_release(UINT8 idx) {")
            appendLine("    if (idx < $count) {")
            appendLine("        _fs_${name}_active &= ($bitfieldType)~(1 << idx);")
            appendLine("    }")
            appendLine("}")
            appendLine()
            // isActive
            appendLine("UINT8 fs_${name}_isActive(UINT8 idx) {")
            appendLine("    return (idx < $count && (_fs_${name}_active & (1 << idx))) ? 1 : 0;")
            append("}")
        }
    }
}

// -----------------------------------------------------------------------
// VarType → C type name extension (GBDK naming)
// -----------------------------------------------------------------------

/** Maps a [VarType] to the GBDK C type name string used in generated code. */
internal val VarType.cName: String
    get() =
        when (this) {
            VarType.U8 -> "UINT8"
            VarType.U16 -> "UINT16"
            VarType.I8 -> "INT8"
            VarType.I16 -> "INT16"
        }

// -----------------------------------------------------------------------
// Helpers: generate all collection code from a list of IR descriptors
// -----------------------------------------------------------------------

/**
 * Collect all data declaration strings in collection-type order: hash tables, pools, ring buffers,
 * fixed slots.
 */
private fun GBDKCollectionCodegen.buildAllDataStrings(
    hashTables: List<IRCollHashTable>,
    pools: List<IRCollPool>,
    ringBuffers: List<IRCollRingBuffer>,
    fixedSlots: List<IRCollFixedSlots>,
): List<String> {
    val parts = mutableListOf<String>()
    for (ht in hashTables) parts += generateHashTableData(ht)
    for (pool in pools) parts += generatePoolData(pool)
    for (rb in ringBuffers) parts += generateRingBufferData(rb)
    for (fs in fixedSlots) parts += generateFixedSlotsData(fs)
    return parts
}

/**
 * Collect all helper function strings in collection-type order: hash tables, pools, ring buffers,
 * fixed slots.
 */
private fun GBDKCollectionCodegen.buildAllFuncStrings(
    hashTables: List<IRCollHashTable>,
    pools: List<IRCollPool>,
    ringBuffers: List<IRCollRingBuffer>,
    fixedSlots: List<IRCollFixedSlots>,
): List<String> {
    val parts = mutableListOf<String>()
    for (ht in hashTables) parts += generateHashTableFunctions(ht)
    for (pool in pools) parts += generatePoolFunctions(pool)
    for (rb in ringBuffers) parts += generateRingBufferFunctions(rb)
    for (fs in fixedSlots) parts += generateFixedSlotsFunctions(fs)
    return parts
}

/**
 * Generate all collection data declarations and helper functions from GameIR collection lists.
 *
 * Returns a pair of (dataDeclarations, helperFunctions) — each is a single concatenated C string
 * containing all declarations or all functions for the given collection lists.
 */
fun GBDKCollectionCodegen.generateAllCollections(
    hashTables: List<IRCollHashTable>,
    pools: List<IRCollPool>,
    ringBuffers: List<IRCollRingBuffer>,
    fixedSlots: List<IRCollFixedSlots>,
): Pair<String, String> =
    buildAllDataStrings(hashTables, pools, ringBuffers, fixedSlots).joinToString("\n\n") to
        buildAllFuncStrings(hashTables, pools, ringBuffers, fixedSlots).joinToString("\n\n")

// -----------------------------------------------------------------------
// CollElementType → CType mapping for prototype generation
// -----------------------------------------------------------------------

/**
 * Maps a [CollElementType] to the corresponding C AST
 * [CType][io.github.gbkt.backend.gbdk.codegen.ast.CType].
 */
private fun collElementToCType(
    element: CollElementType
): io.github.gbkt.backend.gbdk.codegen.ast.CType =
    when (element) {
        is CollElementType.Primitive ->
            when (element.varType) {
                VarType.U8 -> CU8
                VarType.U16 -> CU16
                VarType.I8 -> CI8
                VarType.I16 -> CI16
            }
        is CollElementType.Struct ->
            CU8 // struct typedefs emit as named types; CU8 is a safe fallback
    }

// -----------------------------------------------------------------------
// Prototype generation: typed CFunction prototypes for game.h
// -----------------------------------------------------------------------

/**
 * Generate typed [CFunction] prototypes for all collection helper functions.
 *
 * These prototypes are included in `game.h` so that banked scene code can call collection functions
 * defined in `main.c` without relying on implicit declarations. The raw string codegen in
 * [generateAllCollections] cannot be scanned by the auto-prototype extraction in
 * `buildHeaderFile()`, so this method produces matching typed prototypes alongside the raw function
 * bodies.
 */
fun generateCollectionPrototypes(
    hashTables: List<IRCollHashTable>,
    pools: List<IRCollPool>,
    ringBuffers: List<IRCollRingBuffer>,
    fixedSlots: List<IRCollFixedSlots>,
): List<CFunction> = buildList {
    for (ht in hashTables) {
        val keyType = collElementToCType(ht.keyType)
        val valType = collElementToCType(ht.valueType)
        add(
            CFunction(
                name = "ht_${ht.name}_insert",
                returnType = CVoid,
                params = listOf(CParam("key", keyType), CParam("value", valType)),
                isPrototype = true,
            )
        )
        add(
            CFunction(
                name = "ht_${ht.name}_lookup",
                returnType = CU8,
                params = listOf(CParam("key", keyType), CParam("out", CPointer(valType))),
                isPrototype = true,
            )
        )
        add(
            CFunction(
                name = "ht_${ht.name}_get",
                returnType = valType,
                params = listOf(CParam("key", keyType)),
                isPrototype = true,
            )
        )
        add(
            CFunction(
                name = "ht_${ht.name}_contains",
                returnType = CU8,
                params = listOf(CParam("key", keyType)),
                isPrototype = true,
            )
        )
        add(
            CFunction(
                name = "ht_${ht.name}_remove",
                returnType = CVoid,
                params = listOf(CParam("key", keyType)),
                isPrototype = true,
            )
        )
        add(CFunction(name = "ht_${ht.name}_clear", returnType = CVoid, isPrototype = true))
    }
    for (pool in pools) {
        add(CFunction(name = "pool_${pool.name}_alloc", returnType = CU8, isPrototype = true))
        add(
            CFunction(
                name = "pool_${pool.name}_free",
                returnType = CVoid,
                params = listOf(CParam("idx", CU8)),
                isPrototype = true,
            )
        )
        add(CFunction(name = "pool_${pool.name}_activeCount", returnType = CU8, isPrototype = true))
        add(CFunction(name = "pool_${pool.name}_hasSpace", returnType = CU8, isPrototype = true))
    }
    for (rb in ringBuffers) {
        val elemType = collElementToCType(rb.elementType)
        add(
            CFunction(
                name = "rb_${rb.name}_push",
                returnType = CVoid,
                params = listOf(CParam("val", elemType)),
                isPrototype = true,
            )
        )
        add(CFunction(name = "rb_${rb.name}_pop", returnType = elemType, isPrototype = true))
        add(CFunction(name = "rb_${rb.name}_peek", returnType = elemType, isPrototype = true))
        add(CFunction(name = "rb_${rb.name}_count", returnType = CU8, isPrototype = true))
    }
    for (fs in fixedSlots) {
        add(CFunction(name = "fs_${fs.name}_claim", returnType = CU8, isPrototype = true))
        add(
            CFunction(
                name = "fs_${fs.name}_release",
                returnType = CVoid,
                params = listOf(CParam("idx", CU8)),
                isPrototype = true,
            )
        )
        add(
            CFunction(
                name = "fs_${fs.name}_isActive",
                returnType = CU8,
                params = listOf(CParam("idx", CU8)),
                isPrototype = true,
            )
        )
    }
}
