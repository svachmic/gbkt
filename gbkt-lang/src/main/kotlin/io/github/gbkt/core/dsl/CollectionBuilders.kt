/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CollElementType
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.IRCollFixedSlots
import io.github.gbkt.core.ir.IRCollHashTable
import io.github.gbkt.core.ir.IRCollPool
import io.github.gbkt.core.ir.IRCollRingBuffer
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.StructDef
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// COLLECTION DELEGATES
// =============================================================================
// Provides `val cache by hashtable(...)`, `val bullets by pool(...)`, etc.
// delegates for declaring collections in the game DSL.
//
// Two forms are supported for struct element types:
//   1. Explicit: `val cache by hashtable(tileEntry, 64)`
//   2. Reified:  `val cache by hashtable<TileHashEntry>(64)`
//      (requires prior `val tileEntry = struct("TileHashEntry") { ... }`)
//
// Primitive element types use CollElementType.Primitive(VarType.*) — all four
// primitive types are supported (u8, u16, i8, i16).
// =============================================================================

// =============================================================================
// TYPED COLLECTION REFERENCES
// Returned by delegates after registration — hold name and element type info.
// =============================================================================

/**
 * Typed reference to a registered hash table.
 *
 * Returned by the `val cache by hashtable(...)` delegate when initialized inside a `game {}` block.
 */
data class HashTableRef(
    val name: String,
    val keyType: CollElementType,
    val valueType: CollElementType,
    val size: Int,
) {
    /**
     * Inserts (or updates) a key-value pair in the hash table.
     *
     * Emits `ht_<name>_insert(key, value)`.
     */
    fun put(key: Expr, value: Expr) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("HashTableRef.put() called outside a ScriptBuilder block")
        ctx.emit(CallOp("ht_${name}_insert", listOf(key, value)))
    }

    /** Convenience overload: `cache.put(keyExpr, 42)`. */
    fun put(key: Expr, value: Int) = put(key, Literal(value))

    /**
     * Returns the value associated with [key], or 0 if the key is not present.
     *
     * Returns `CallExpr` — use as an [Expr] in assignments or conditions.
     */
    fun get(key: Expr): Expr = CallExpr("ht_${name}_get", listOf(key))

    /**
     * Returns 1 if [key] is present in the hash table, 0 otherwise.
     *
     * Suitable for use in `runIf()` conditions.
     */
    fun contains(key: Expr): Expr = CallExpr("ht_${name}_contains", listOf(key))

    /**
     * Removes [key] from the hash table (marks the slot as unused).
     *
     * Emits `ht_<name>_remove(key)`.
     */
    fun remove(key: Expr) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("HashTableRef.remove() called outside a ScriptBuilder block")
        ctx.emit(CallOp("ht_${name}_remove", listOf(key)))
    }

    /**
     * Clears all entries in the hash table.
     *
     * Emits `ht_<name>_clear()`.
     */
    fun clear() {
        val ctx =
            ScriptBuilderContext.current
                ?: error("HashTableRef.clear() called outside a ScriptBuilder block")
        ctx.emit(CallOp("ht_${name}_clear", emptyList()))
    }
}

/**
 * Typed reference to a registered object pool.
 *
 * Returned by the `val bullets by pool(...)` delegate when initialized inside a `game {}` block.
 */
data class PoolRef(val name: String, val elementType: CollElementType, val capacity: Int) {
    /**
     * Allocates the next free slot from the pool.
     *
     * Emits a temp variable assignment into the active [ScriptBuilder] and returns a [PoolSlotRef]
     * with `.exists` and `.index` accessors. If called outside a ScriptBuilder block, falls back to
     * returning the raw alloc expression wrapped in a minimal [PoolSlotRef].
     *
     * Returns 0xFF index if the pool is full — check [PoolSlotRef.exists] before use.
     */
    fun acquire(): PoolSlotRef {
        val slotVar = "_pool_${name}_slot"
        val allocExpr = CallExpr("pool_${name}_alloc", emptyList())
        val sd = (elementType as? CollElementType.Struct)?.structDef
        // Emit assignment into the active ScriptBuilder if available
        ScriptBuilderContext.current?.emit(Assign(slotVar, allocExpr, AssignOp.SET))
        return PoolSlotRef(name, slotVar, sd)
    }

    /**
     * Releases a pool slot back to the free list.
     *
     * Emits `pool_<name>_free(index)`.
     */
    fun free(index: Expr) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("PoolRef.free() called outside a ScriptBuilder block")
        ctx.emit(CallOp("pool_${name}_free", listOf(index)))
    }

    /** Returns 1 if the pool has at least one free slot, 0 otherwise. */
    val hasSpace: Expr
        get() = CallExpr("pool_${name}_hasSpace", emptyList())

    /** Returns the number of currently allocated elements (0–capacity). */
    val activeCount: Expr
        get() = CallExpr("pool_${name}_activeCount", emptyList())

    /**
     * Direct access to pool data at [index].
     *
     * Returns `_pool_<name>_data[index]` as an [ArrayAccessExpr].
     */
    operator fun get(index: Expr): Expr = ArrayAccessExpr("_pool_${name}_data", index)

    /** Direct access to pool data at a literal [index]. */
    operator fun get(index: Int): Expr = ArrayAccessExpr("_pool_${name}_data", Literal(index))

    /**
     * Iterates over all pool slots, executing [block] for each active element.
     *
     * Emits a [ForOp] from 0 to capacity-1 with the element [Expr] available inside the block.
     */
    fun forEach(block: ScriptBuilder.(element: Expr) -> Unit) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("PoolRef.forEach() called outside a ScriptBuilder block")
        val idxVar = "_pool_${name}_i"
        val bodyBuilder = ScriptBuilder()
        val element = ArrayAccessExpr("_pool_${name}_data", VarRef(idxVar))
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block(element) }
        ctx.emit(ForOp(idxVar, Literal(0), Literal(capacity - 1), bodyBuilder.build()))
    }
}

/**
 * Reference to a pool slot acquired via [PoolRef.acquire].
 *
 * Provides [exists] to check if the allocation succeeded (index != 0xFF) and [index] to access the
 * raw slot index.
 *
 * For struct pools, field access is available via the `get` operator: `slot["x"]`.
 *
 * Usage:
 * ```kotlin
 * val slot = bullets.acquire()
 * runIf(slot.exists) {
 *     slot["x"] set player.x
 * }
 * ```
 */
class PoolSlotRef(val poolName: String, val indexVar: String, val structDef: StructDef?) {
    /** True (non-zero) if the pool allocation succeeded (index != 0xFF). */
    val exists: Expr
        get() = BinaryExpr(VarRef(indexVar), BinaryOp.NEQ, Literal(0xFF))

    /** The raw slot index expression. */
    val index: Expr
        get() = VarRef(indexVar)

    /** Access a struct field on the allocated pool slot. */
    operator fun get(fieldName: String): AssignableVar {
        val s = structDef ?: error("Pool '$poolName' holds primitives, not structs")
        requireNotNull(s.field(fieldName)) { "Struct '${s.name}' has no field '$fieldName'" }
        return AssignableVar("_pool_${poolName}_data[$indexVar].$fieldName")
    }
}

/**
 * Typed reference to a registered ring buffer.
 *
 * Returned by the `val events by ringBuffer(...)` delegate when initialized inside a `game {}`
 * block.
 */
data class RingBufferRef(val name: String, val elementType: CollElementType, val capacity: Int) {
    /**
     * Pushes [value] onto the tail of the ring buffer. No-op if full.
     *
     * Emits `rb_<name>_push(value)`.
     */
    fun push(value: Expr) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("RingBufferRef.push() called outside a ScriptBuilder block")
        ctx.emit(CallOp("rb_${name}_push", listOf(value)))
    }

    /** Convenience overload: `events.push(42)`. */
    fun push(value: Int) = push(Literal(value))

    /**
     * Pops (removes and returns) the front element from the ring buffer.
     *
     * Behaviour is undefined on an empty buffer — check [count] first.
     */
    fun pop(): Expr = CallExpr("rb_${name}_pop", emptyList())

    /**
     * Peeks at the front element without removing it.
     *
     * Behaviour is undefined on an empty buffer — check [count] first.
     */
    fun peek(): Expr = CallExpr("rb_${name}_peek", emptyList())

    /** Returns the number of elements currently in the buffer (0–capacity). */
    val count: Expr
        get() = CallExpr("rb_${name}_count", emptyList())
}

/**
 * Typed reference to a registered fixed-slots collection.
 *
 * Returned by the `val powerups by fixedSlots(...)` delegate when initialized inside a `game {}`
 * block.
 */
data class FixedSlotsRef(
    val name: String,
    val elementType: CollElementType,
    val count: Int,
    val namedSlots: Map<String, Int> = emptyMap(),
) {
    /**
     * Claims the first free slot.
     *
     * Returns the claimed slot index as an [Expr], or 0xFF if all slots are occupied.
     */
    fun claim(): Expr = CallExpr("fs_${name}_claim", emptyList())

    /**
     * Releases a slot, marking it inactive.
     *
     * Emits `fs_<name>_release(index)`.
     */
    fun release(index: Expr) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("FixedSlotsRef.release() called outside a ScriptBuilder block")
        ctx.emit(CallOp("fs_${name}_release", listOf(index)))
    }

    /** Returns 1 if the slot at [index] is active (claimed), 0 otherwise. */
    fun isActive(index: Expr): Expr = CallExpr("fs_${name}_isActive", listOf(index))

    /**
     * Direct access to fixed slot data at [index].
     *
     * Returns `_fs_<name>_data[index]` as an [ArrayAccessExpr].
     */
    operator fun get(index: Expr): Expr = ArrayAccessExpr("_fs_${name}_data", index)

    /** Direct access to fixed slot data at a literal [index]. */
    operator fun get(index: Int): Expr = ArrayAccessExpr("_fs_${name}_data", Literal(index))

    /**
     * Access a named slot by key.
     *
     * Purely compile-time resolution — the name maps to an integer constant at DSL record time.
     * Zero runtime cost.
     *
     * @throws IllegalArgumentException if no slot with the given name exists.
     */
    fun slot(slotName: String): Expr {
        val idx =
            namedSlots[slotName]
                ?: error(
                    "Fixed slots '$name' has no slot named '$slotName'. " +
                        "Available: ${namedSlots.keys}"
                )
        return get(Literal(idx))
    }
}

// =============================================================================
// PROPERTY DELEGATES — capture name via provideDelegate, register IR node
// =============================================================================

/**
 * Property delegate that registers an [IRCollHashTable] in the current [GameBuilder] and provides a
 * [HashTableRef].
 *
 * Created by the top-level [hashtable] factory functions.
 */
class HashTableDelegate(
    private val keyType: CollElementType,
    private val valueType: CollElementType,
    private val size: Int,
) {
    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name, registers the
     * [IRCollHashTable] with the active [GameBuilder], and returns a [ReadOnlyProperty] that yields
     * [HashTableRef].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, HashTableRef> {
        val name = property.name
        val ht = IRCollHashTable(name = name, keyType = keyType, valueType = valueType, size = size)
        GameBuilderContext.current?.registerHashTable(ht)
            ?: error("hashtable() called outside a game {} block")
        return ReadOnlyProperty { _, _ -> HashTableRef(name, keyType, valueType, size) }
    }
}

/**
 * Property delegate that registers an [IRCollPool] in the current [GameBuilder] and provides a
 * [PoolRef].
 *
 * Created by the top-level [pool] factory functions.
 */
class PoolDelegate(private val elementType: CollElementType, private val capacity: Int) {
    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name, registers the
     * [IRCollPool] with the active [GameBuilder], and returns a [ReadOnlyProperty] that yields
     * [PoolRef].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PoolRef> {
        val name = property.name
        val pool = IRCollPool(name = name, elementType = elementType, capacity = capacity)
        GameBuilderContext.current?.registerPool(pool)
            ?: error("pool() called outside a game {} block")
        return ReadOnlyProperty { _, _ -> PoolRef(name, elementType, capacity) }
    }
}

/**
 * Property delegate that registers an [IRCollRingBuffer] in the current [GameBuilder] and provides
 * a [RingBufferRef].
 *
 * Created by the top-level [ringBuffer] factory functions.
 */
class RingBufferDelegate(private val elementType: CollElementType, private val capacity: Int) {
    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name, registers the
     * [IRCollRingBuffer] with the active [GameBuilder], and returns a [ReadOnlyProperty] that
     * yields [RingBufferRef].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, RingBufferRef> {
        val name = property.name
        val rb = IRCollRingBuffer(name = name, elementType = elementType, capacity = capacity)
        GameBuilderContext.current?.registerRingBuffer(rb)
            ?: error("ringBuffer() called outside a game {} block")
        return ReadOnlyProperty { _, _ -> RingBufferRef(name, elementType, capacity) }
    }
}

/**
 * Property delegate that registers an [IRCollFixedSlots] in the current [GameBuilder] and provides
 * a [FixedSlotsRef].
 *
 * Created by the top-level [fixedSlots] factory functions.
 */
class FixedSlotsDelegate(
    private val elementType: CollElementType,
    private val count: Int,
    private val namedSlots: Map<String, Int> = emptyMap(),
) {
    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name, registers the
     * [IRCollFixedSlots] with the active [GameBuilder], and returns a [ReadOnlyProperty] that
     * yields [FixedSlotsRef].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, FixedSlotsRef> {
        val name = property.name
        val fs =
            IRCollFixedSlots(
                name = name,
                elementType = elementType,
                count = count,
                namedSlots = namedSlots,
            )
        GameBuilderContext.current?.registerFixedSlots(fs)
            ?: error("fixedSlots() called outside a game {} block")
        return ReadOnlyProperty { _, _ -> FixedSlotsRef(name, elementType, count, namedSlots) }
    }
}

// =============================================================================
// FACTORY FUNCTIONS — CollElementType param forms (explicit)
// =============================================================================

// -------------------------------------------------------------------------
// Hash table
// -------------------------------------------------------------------------

/**
 * Declares a hash table collection with explicit [CollElementType] key and value types.
 *
 * Supports both primitive and struct element types:
 * ```kotlin
 * val cache by hashtable(CollElementType.Primitive(VarType.U8), CollElementType.Primitive(VarType.U16), 16)
 * val tileCache by hashtable(CollElementType.Primitive(VarType.U16), CollElementType.Struct(tileEntry), 64)
 * ```
 *
 * @param keyType Element type for hash table keys
 * @param valueType Element type for hash table values
 * @param size Number of slots (use power-of-2 for best hash performance)
 */
fun hashtable(keyType: CollElementType, valueType: CollElementType, size: Int): HashTableDelegate =
    HashTableDelegate(keyType, valueType, size)

/**
 * Declares a hash table collection with [StructDef] value type and default U16 key type.
 *
 * Convenience overload for the common pattern of keying a struct table by a numeric ID:
 * ```kotlin
 * val tileCache by hashtable(tileEntry, 64)
 * // equivalent to: hashtable(CollElementType.Primitive(VarType.U16), CollElementType.Struct(tileEntry), 64)
 * ```
 *
 * @param structDef Struct type for the table values
 * @param size Number of slots
 */
fun hashtable(structDef: StructDef, size: Int): HashTableDelegate =
    HashTableDelegate(
        keyType = CollElementType.Primitive(VarType.U16),
        valueType = CollElementType.Struct(structDef),
        size = size,
    )

/**
 * Declares a hash table collection with explicit primitive key and value [VarType]s.
 *
 * Convenience overload for all-primitive hash tables (the common Game Boy case):
 * ```kotlin
 * val scores by hashtable(VarType.U8, VarType.U16, 16)
 * ```
 */
fun hashtable(keyType: VarType, valueType: VarType, size: Int): HashTableDelegate =
    HashTableDelegate(
        keyType = CollElementType.Primitive(keyType),
        valueType = CollElementType.Primitive(valueType),
        size = size,
    )

// -------------------------------------------------------------------------
// Object pool
// -------------------------------------------------------------------------

/**
 * Declares an object pool with explicit [CollElementType] element type.
 *
 * ```kotlin
 * val bullets by pool(CollElementType.Primitive(VarType.U8), 16)
 * val entities by pool(CollElementType.Struct(entityDef), 8)
 * ```
 *
 * @param elementType Element type for each pool slot
 * @param capacity Maximum number of live elements (0xFF is returned on alloc when full)
 */
fun pool(elementType: CollElementType, capacity: Int): PoolDelegate =
    PoolDelegate(elementType, capacity)

/**
 * Declares an object pool with [StructDef] element type.
 *
 * ```kotlin
 * val entities by pool(entityDef, 8)
 * ```
 */
fun pool(structDef: StructDef, capacity: Int): PoolDelegate =
    PoolDelegate(CollElementType.Struct(structDef), capacity)

/**
 * Declares an object pool with primitive [VarType] element type.
 *
 * ```kotlin
 * val bullets by pool(VarType.U8, 16)
 * ```
 */
fun pool(elementType: VarType, capacity: Int): PoolDelegate =
    PoolDelegate(CollElementType.Primitive(elementType), capacity)

// -------------------------------------------------------------------------
// Ring buffer
// -------------------------------------------------------------------------

/**
 * Declares a ring buffer with explicit [CollElementType] element type.
 *
 * ```kotlin
 * val events by ringBuffer(CollElementType.Primitive(VarType.U8), 8)
 * val inputQueue by ringBuffer(CollElementType.Struct(inputEventDef), 4)
 * ```
 *
 * @param elementType Element type for each buffered element
 * @param capacity Maximum number of elements in the buffer
 */
fun ringBuffer(elementType: CollElementType, capacity: Int): RingBufferDelegate =
    RingBufferDelegate(elementType, capacity)

/**
 * Declares a ring buffer with [StructDef] element type.
 *
 * ```kotlin
 * val inputQueue by ringBuffer(inputEventDef, 4)
 * ```
 */
fun ringBuffer(structDef: StructDef, capacity: Int): RingBufferDelegate =
    RingBufferDelegate(CollElementType.Struct(structDef), capacity)

/**
 * Declares a ring buffer with primitive [VarType] element type.
 *
 * ```kotlin
 * val events by ringBuffer(VarType.U8, 8)
 * ```
 */
fun ringBuffer(elementType: VarType, capacity: Int): RingBufferDelegate =
    RingBufferDelegate(CollElementType.Primitive(elementType), capacity)

// -------------------------------------------------------------------------
// Fixed slots
// -------------------------------------------------------------------------

/**
 * Declares a fixed-slots collection with explicit [CollElementType] element type.
 *
 * Fixed slots allocate exactly [count] slots (1–16). An active bitfield tracks which are claimed.
 *
 * ```kotlin
 * val powerups by fixedSlots(CollElementType.Primitive(VarType.U8), 4)
 * val inventorySlots by fixedSlots(CollElementType.Struct(itemSlotDef), 8)
 * ```
 *
 * @param elementType Element type for each slot
 * @param count Number of slots (must be 1–16)
 */
fun fixedSlots(elementType: CollElementType, count: Int): FixedSlotsDelegate =
    FixedSlotsDelegate(elementType, count)

/**
 * Declares a fixed-slots collection with [StructDef] element type.
 *
 * ```kotlin
 * val inventory by fixedSlots(itemSlotDef, 8)
 * ```
 */
fun fixedSlots(structDef: StructDef, count: Int): FixedSlotsDelegate =
    FixedSlotsDelegate(CollElementType.Struct(structDef), count)

/**
 * Declares a fixed-slots collection with primitive [VarType] element type.
 *
 * ```kotlin
 * val powerups by fixedSlots(VarType.U8, 4)
 * ```
 */
fun fixedSlots(elementType: VarType, count: Int): FixedSlotsDelegate =
    FixedSlotsDelegate(CollElementType.Primitive(elementType), count)

/**
 * Declares a fixed-slots collection with named keys and explicit [CollElementType].
 *
 * Each name maps to a slot index (0-based). Capacity is automatically set to the number of names.
 * Access via `ref.slot("weapon")` for compile-time resolved, zero-cost named key lookup.
 *
 * ```kotlin
 * val equip by fixedSlots(CollElementType.Primitive(VarType.U8), "weapon", "armor", "accessory")
 * equip.slot("weapon")  // equivalent to equip[0]
 * ```
 *
 * @param elementType Element type for each slot
 * @param names Slot names in order (index = position)
 */
fun fixedSlots(elementType: CollElementType, vararg names: String): FixedSlotsDelegate {
    val namedSlots = names.withIndex().associate { (i, n) -> n to i }
    return FixedSlotsDelegate(elementType, names.size, namedSlots)
}

// =============================================================================
// REIFIED GENERIC FACTORY FUNCTIONS — type-safe struct resolution
// =============================================================================
// These functions resolve a StructDef from the GameBuilder registry by looking
// up the reified type's simple name. Requires prior registration via:
//   val tileEntry = struct("TileHashEntry") { ... }
//
// Then the reified form resolves "TileHashEntry" from T::class.simpleName.
// =============================================================================

/**
 * Resolves a [StructDef] from the [GameBuilder] registry using the reified type's simple name.
 *
 * Used internally by the reified generic collection factories. The name lookup uses
 * `T::class.simpleName` which must match the name passed to `struct("Name") { ... }`.
 *
 * @throws IllegalStateException if called outside a `game {}` block
 * @throws IllegalArgumentException if no struct with the matching name is registered
 */
inline fun <reified T> GameBuilder.resolveStructDef(): StructDef {
    val name = T::class.simpleName ?: error("Cannot resolve struct type for ${T::class}")
    return findStructByName(name)
        ?: error(
            "No struct registered with name '$name'. " +
                "Define it with struct(\"$name\") { ... } before using reified collection delegates."
        )
}

/**
 * Declares a hash table whose value type is the struct registered as [T].
 *
 * Requires that a struct with name `T::class.simpleName` was declared earlier in the same `game {}`
 * block via `struct("T") { ... }`. Uses U16 as the default key type.
 *
 * ```kotlin
 * val tileEntry = struct("TileHashEntry") { field("key", u16); field("value", u8) }
 * val cache by hashtable<TileHashEntry>(64)
 * ```
 *
 * @param size Number of hash table slots (use power-of-2 for best performance)
 */
inline fun <reified T> GameBuilder.hashtable(size: Int): HashTableDelegate {
    val structDef = resolveStructDef<T>()
    return HashTableDelegate(
        keyType = CollElementType.Primitive(VarType.U16),
        valueType = CollElementType.Struct(structDef),
        size = size,
    )
}

/**
 * Declares an object pool whose element type is the struct registered as [T].
 *
 * ```kotlin
 * val entityDef = struct("Entity") { field("x", i8); field("y", i8) }
 * val entities by pool<Entity>(8)
 * ```
 *
 * @param capacity Maximum number of live pool elements
 */
inline fun <reified T> GameBuilder.pool(capacity: Int): PoolDelegate {
    val structDef = resolveStructDef<T>()
    return PoolDelegate(CollElementType.Struct(structDef), capacity)
}

/**
 * Declares a ring buffer whose element type is the struct registered as [T].
 *
 * ```kotlin
 * val inputEventDef = struct("InputEvent") { field("type", u8); field("value", u8) }
 * val inputQueue by ringBuffer<InputEvent>(4)
 * ```
 *
 * @param capacity Maximum number of buffered elements
 */
inline fun <reified T> GameBuilder.ringBuffer(capacity: Int): RingBufferDelegate {
    val structDef = resolveStructDef<T>()
    return RingBufferDelegate(CollElementType.Struct(structDef), capacity)
}

/**
 * Declares a fixed-slots collection whose element type is the struct registered as [T].
 *
 * ```kotlin
 * val itemSlotDef = struct("ItemSlot") { field("id", u8); field("count", u8) }
 * val inventory by fixedSlots<ItemSlot>(8)
 * ```
 *
 * @param count Number of fixed slots (must be 1–16)
 */
inline fun <reified T> GameBuilder.fixedSlots(count: Int): FixedSlotsDelegate {
    val structDef = resolveStructDef<T>()
    return FixedSlotsDelegate(CollElementType.Struct(structDef), count)
}
