/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CollElementType
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

// =============================================================================
// STRUCT COLLECTION DSL TESTS
// Verifies the struct builder DSL, collection delegates (explicit and reified
// generic forms), and backward compatibility with primitive element types.
// =============================================================================

class StructCollectionTest {

    // =========================================================================
    // StructBuilder DSL
    // =========================================================================

    @Test
    fun `struct builder creates StructDef with correct name and fields`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val tileEntry =
                        struct("TileHashEntry") {
                            field("key", u16)
                            field("value", u8)
                        }
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.structs.size)
        assertEquals("TileHashEntry", ir.structs[0].name)
        assertEquals(2, ir.structs[0].fields.size)
        assertEquals("key", ir.structs[0].fields[0].name)
        assertEquals(VarType.U16, ir.structs[0].fields[0].type)
        assertEquals("value", ir.structs[0].fields[1].name)
        assertEquals(VarType.U8, ir.structs[0].fields[1].type)
    }

    @Test
    fun `struct builder computes correct byteSize`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val entry =
                        struct("Entry") {
                            field("x", i8) // 1 byte
                            field("y", i8) // 1 byte
                            field("vx", i8) // 1 byte
                            field("speed", u16) // 2 bytes
                        }
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(5, ir.structs[0].byteSize) // 1+1+1+2
    }

    // =========================================================================
    // StructVar field access
    // =========================================================================

    @Test
    fun `StructVar field access returns AssignableVar with dot notation`() {
        val ir =
            game("TestGame") {
                    val entry =
                        struct("TileHashEntry") {
                            field("key", u16)
                            field("value", u8)
                        }
                    val proxy = StructVar("slot", entry)
                    val keyRef = proxy["key"]
                    assertEquals("slot.key", keyRef.name)
                    val valueRef = proxy["value"]
                    assertEquals("slot.value", valueRef.name)
                    scene("main") {}
                    start = "main"
                }
                .build()

        // Verify struct registered
        assertEquals("TileHashEntry", ir.structs[0].name)
    }

    @Test
    fun `StructVar field access throws for unknown field`() {
        val structDef =
            io.github.gbkt.core.ir.StructDef(
                name = "Vec2",
                fields =
                    listOf(
                        io.github.gbkt.core.ir.StructFieldDef("x", VarType.I8),
                        io.github.gbkt.core.ir.StructFieldDef("y", VarType.I8),
                    ),
            )
        val proxy = StructVar("pos", structDef)
        assertFailsWith<IllegalArgumentException> { proxy["z"] }
    }

    // =========================================================================
    // Hash table delegate — explicit-param form
    // =========================================================================

    @Test
    fun `hashtable delegate with StructDef registers IRCollHashTable in GameIR`() {
        val ir =
            game("TestGame") {
                    val tileEntry =
                        struct("TileEntry") {
                            field("key", u16)
                            field("value", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val cache by hashtable(tileEntry, 64)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.hashTables.size)
        assertEquals("cache", ir.hashTables[0].name)
        assertEquals(64, ir.hashTables[0].size)
        // Key type defaults to U16 for struct-value convenience overload
        assertIs<CollElementType.Primitive>(ir.hashTables[0].keyType)
        assertEquals(VarType.U16, (ir.hashTables[0].keyType as CollElementType.Primitive).varType)
        // Value type should be CollElementType.Struct
        assertIs<CollElementType.Struct>(ir.hashTables[0].valueType)
        val valueStruct = (ir.hashTables[0].valueType as CollElementType.Struct).structDef
        assertEquals("TileEntry", valueStruct.name)
    }

    @Test
    fun `hashtable delegate with VarType registers primitive IRCollHashTable`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val scores by hashtable(VarType.U8, VarType.U16, 16)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.hashTables.size)
        assertEquals("scores", ir.hashTables[0].name)
        assertEquals(16, ir.hashTables[0].size)
        assertIs<CollElementType.Primitive>(ir.hashTables[0].keyType)
        assertIs<CollElementType.Primitive>(ir.hashTables[0].valueType)
        assertEquals(VarType.U8, (ir.hashTables[0].keyType as CollElementType.Primitive).varType)
        assertEquals(VarType.U16, (ir.hashTables[0].valueType as CollElementType.Primitive).varType)
    }

    // =========================================================================
    // Pool delegate — explicit-param form
    // =========================================================================

    @Test
    fun `pool delegate with StructDef registers IRCollPool in GameIR`() {
        val ir =
            game("TestGame") {
                    val entityDef =
                        struct("Entity") {
                            field("x", i8)
                            field("y", i8)
                        }
                    @Suppress("UNUSED_VARIABLE") val entities by pool(entityDef, 8)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.pools.size)
        assertEquals("entities", ir.pools[0].name)
        assertEquals(8, ir.pools[0].capacity)
        assertIs<CollElementType.Struct>(ir.pools[0].elementType)
        assertEquals("Entity", (ir.pools[0].elementType as CollElementType.Struct).structDef.name)
    }

    @Test
    fun `pool delegate with VarType registers primitive IRCollPool`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") val bullets by pool(VarType.U8, 16)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.pools.size)
        assertEquals("bullets", ir.pools[0].name)
        assertEquals(16, ir.pools[0].capacity)
        assertIs<CollElementType.Primitive>(ir.pools[0].elementType)
    }

    // =========================================================================
    // Ring buffer delegate — explicit-param form
    // =========================================================================

    @Test
    fun `ringBuffer delegate with VarType registers IRCollRingBuffer`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") val events by ringBuffer(VarType.U8, 8)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.ringBuffers.size)
        assertEquals("events", ir.ringBuffers[0].name)
        assertEquals(8, ir.ringBuffers[0].capacity)
        assertIs<CollElementType.Primitive>(ir.ringBuffers[0].elementType)
    }

    @Test
    fun `ringBuffer delegate with StructDef registers IRCollRingBuffer with struct type`() {
        val ir =
            game("TestGame") {
                    val inputEvent =
                        struct("InputEvent") {
                            field("type", u8)
                            field("value", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val inputQueue by ringBuffer(inputEvent, 4)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.ringBuffers.size)
        assertEquals("inputQueue", ir.ringBuffers[0].name)
        assertEquals(4, ir.ringBuffers[0].capacity)
        assertIs<CollElementType.Struct>(ir.ringBuffers[0].elementType)
    }

    // =========================================================================
    // Fixed slots delegate — explicit-param form
    // =========================================================================

    @Test
    fun `fixedSlots delegate with VarType registers IRCollFixedSlots`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") val powerups by fixedSlots(VarType.U8, 4)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.fixedSlots.size)
        assertEquals("powerups", ir.fixedSlots[0].name)
        assertEquals(4, ir.fixedSlots[0].count)
        assertIs<CollElementType.Primitive>(ir.fixedSlots[0].elementType)
    }

    @Test
    fun `fixedSlots delegate with StructDef registers IRCollFixedSlots with struct type`() {
        val ir =
            game("TestGame") {
                    val itemSlot =
                        struct("ItemSlot") {
                            field("id", u8)
                            field("count", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val inventory by fixedSlots(itemSlot, 8)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.fixedSlots.size)
        assertEquals("inventory", ir.fixedSlots[0].name)
        assertEquals(8, ir.fixedSlots[0].count)
        assertIs<CollElementType.Struct>(ir.fixedSlots[0].elementType)
        assertEquals(
            "ItemSlot",
            (ir.fixedSlots[0].elementType as CollElementType.Struct).structDef.name,
        )
    }

    // =========================================================================
    // Reified generic delegates
    // =========================================================================

    // NOTE: Reified generics resolve by T::class.simpleName which equals the type's name at
    // compile time. In tests, the "type name" must match the struct name.
    // We use a data class TileHashEntry as a marker type.

    /** Marker type for reified generic hashtable test. */
    private data class TileHashEntry(val unused: Int = 0)

    /** Marker type for reified generic pool test. */
    private data class Bullet(val unused: Int = 0)

    /** Marker type for reified generic ringBuffer test. */
    private data class GameEvent(val unused: Int = 0)

    /** Marker type for reified generic fixedSlots test. */
    private data class PowerupSlot(val unused: Int = 0)

    @Test
    fun `reified hashtable resolves StructDef from registry by type name`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val _def =
                        struct("TileHashEntry") {
                            field("key", u16)
                            field("value", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val cache by hashtable<TileHashEntry>(64)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.hashTables.size)
        assertEquals("cache", ir.hashTables[0].name)
        assertEquals(64, ir.hashTables[0].size)
        assertIs<CollElementType.Struct>(ir.hashTables[0].valueType)
        assertEquals(
            "TileHashEntry",
            (ir.hashTables[0].valueType as CollElementType.Struct).structDef.name,
        )
    }

    @Test
    fun `reified pool resolves StructDef from registry by type name`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val _def =
                        struct("Bullet") {
                            field("x", u8)
                            field("y", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val bullets by pool<Bullet>(16)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.pools.size)
        assertEquals("bullets", ir.pools[0].name)
        assertEquals(16, ir.pools[0].capacity)
        assertIs<CollElementType.Struct>(ir.pools[0].elementType)
        assertEquals("Bullet", (ir.pools[0].elementType as CollElementType.Struct).structDef.name)
    }

    @Test
    fun `reified ringBuffer resolves StructDef from registry by type name`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val _def =
                        struct("GameEvent") {
                            field("type", u8)
                            field("param", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val events by ringBuffer<GameEvent>(8)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.ringBuffers.size)
        assertEquals("events", ir.ringBuffers[0].name)
        assertIs<CollElementType.Struct>(ir.ringBuffers[0].elementType)
    }

    @Test
    fun `reified fixedSlots resolves StructDef from registry by type name`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val _def =
                        struct("PowerupSlot") {
                            field("id", u8)
                            field("active", u8)
                        }
                    @Suppress("UNUSED_VARIABLE") val slots by fixedSlots<PowerupSlot>(4)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.fixedSlots.size)
        assertEquals("slots", ir.fixedSlots[0].name)
        assertIs<CollElementType.Struct>(ir.fixedSlots[0].elementType)
    }

    @Test
    fun `reified hashtable throws with clear error when struct not registered`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                game("TestGame") {
                        @Suppress("UNUSED_VARIABLE")
                        val cache by hashtable<TileHashEntry>(64) // TileHashEntry not registered!
                        scene("main") {}
                        start = "main"
                    }
                    .build()
            }
        // Error message should mention the missing struct name
        assertNotNull(exception.message)
        assert(exception.message!!.contains("TileHashEntry")) {
            "Error message should contain the missing struct name, got: ${exception.message}"
        }
    }

    // =========================================================================
    // Multiple collections in one game
    // =========================================================================

    @Test
    fun `multiple collections from different types all register correctly`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE")
                    val scores by hashtable(VarType.U8, VarType.U16, 16)
                    @Suppress("UNUSED_VARIABLE") val bullets by pool(VarType.U8, 8)
                    @Suppress("UNUSED_VARIABLE") val events by ringBuffer(VarType.U8, 4)
                    @Suppress("UNUSED_VARIABLE") val slots by fixedSlots(VarType.U8, 4)
                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.hashTables.size)
        assertEquals(1, ir.pools.size)
        assertEquals(1, ir.ringBuffers.size)
        assertEquals(1, ir.fixedSlots.size)
    }
}
