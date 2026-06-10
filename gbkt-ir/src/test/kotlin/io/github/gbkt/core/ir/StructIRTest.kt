/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// =============================================================================
// STRUCT IR TESTS
// Verifies StructDef, StructFieldDef, and CollElementType behaviour.
// =============================================================================

class StructIRTest {

    // =========================================================================
    // StructFieldDef
    // =========================================================================

    @Test
    fun `StructFieldDef byteSize matches VarType byteSize`() {
        assertEquals(1, StructFieldDef("a", VarType.U8).byteSize)
        assertEquals(2, StructFieldDef("b", VarType.U16).byteSize)
        assertEquals(1, StructFieldDef("c", VarType.I8).byteSize)
        assertEquals(2, StructFieldDef("d", VarType.I16).byteSize)
    }

    // =========================================================================
    // StructDef
    // =========================================================================

    @Test
    fun `StructDef byteSize sums all field sizes`() {
        // key=U16 (2 bytes) + value=U8 (1 byte) = 3 bytes
        val def =
            StructDef(
                name = "TileEntry",
                fields =
                    listOf(StructFieldDef("key", VarType.U16), StructFieldDef("value", VarType.U8)),
            )
        assertEquals(3, def.byteSize)
    }

    @Test
    fun `StructDef byteSize sums four primitive fields`() {
        // x=I8, y=I8, vx=I8, vy=I8 = 4 bytes
        val def =
            StructDef(
                name = "Entity",
                fields =
                    listOf(
                        StructFieldDef("x", VarType.I8),
                        StructFieldDef("y", VarType.I8),
                        StructFieldDef("vx", VarType.I8),
                        StructFieldDef("vy", VarType.I8),
                    ),
            )
        assertEquals(4, def.byteSize)
    }

    @Test
    fun `StructDef field lookup by name returns correct field`() {
        val def =
            StructDef(
                name = "Vec2",
                fields = listOf(StructFieldDef("x", VarType.I16), StructFieldDef("y", VarType.I16)),
            )
        val found = def.field("x")
        assertNotNull(found)
        assertEquals("x", found.name)
        assertEquals(VarType.I16, found.type)
    }

    @Test
    fun `StructDef field lookup returns null for unknown name`() {
        val def =
            StructDef(
                name = "Vec2",
                fields = listOf(StructFieldDef("x", VarType.I16), StructFieldDef("y", VarType.I16)),
            )
        assertNull(def.field("z"))
    }

    @Test
    fun `StructDef rejects empty fields list`() {
        assertFailsWith<IllegalArgumentException> {
            StructDef(name = "Empty", fields = emptyList())
        }
    }

    @Test
    fun `StructDef rejects duplicate field names`() {
        assertFailsWith<IllegalArgumentException> {
            StructDef(
                name = "Bad",
                fields =
                    listOf(
                        StructFieldDef("x", VarType.U8),
                        StructFieldDef("x", VarType.U16), // duplicate!
                    ),
            )
        }
    }

    // =========================================================================
    // CollElementType
    // =========================================================================

    @Test
    fun `CollElementType Primitive byteSize matches wrapped VarType`() {
        assertEquals(1, CollElementType.Primitive(VarType.U8).byteSize)
        assertEquals(2, CollElementType.Primitive(VarType.U16).byteSize)
        assertEquals(1, CollElementType.Primitive(VarType.I8).byteSize)
        assertEquals(2, CollElementType.Primitive(VarType.I16).byteSize)
    }

    @Test
    fun `CollElementType Primitive cTypeName produces GBDK type alias`() {
        assertEquals("UINT8", CollElementType.Primitive(VarType.U8).cTypeName)
        assertEquals("UINT16", CollElementType.Primitive(VarType.U16).cTypeName)
        assertEquals("INT8", CollElementType.Primitive(VarType.I8).cTypeName)
        assertEquals("INT16", CollElementType.Primitive(VarType.I16).cTypeName)
    }

    @Test
    fun `CollElementType Struct byteSize reflects struct total size`() {
        val structDef =
            StructDef(
                name = "TileEntry",
                fields =
                    listOf(
                        StructFieldDef("key", VarType.U16), // 2 bytes
                        StructFieldDef("value", VarType.U8), // 1 byte
                    ),
            )
        val elemType = CollElementType.Struct(structDef)
        assertEquals(3, elemType.byteSize)
    }

    @Test
    fun `CollElementType Struct cTypeName returns struct name`() {
        val structDef =
            StructDef(name = "TileHashEntry", fields = listOf(StructFieldDef("key", VarType.U8)))
        assertEquals("TileHashEntry", CollElementType.Struct(structDef).cTypeName)
    }

    // =========================================================================
    // sizeBytes integration with collection IR nodes
    // =========================================================================

    @Test
    fun `IRCollHashTable sizeBytes uses CollElementType byteSize`() {
        // 4 slots * (key=U8=1 + value=U16=2 + 1 used) = 4 * 4 = 16 bytes
        val ht =
            IRCollHashTable(
                name = "ht",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Primitive(VarType.U16),
                size = 4,
            )
        assertEquals(16, ht.sizeBytes)
    }

    @Test
    fun `IRCollPool sizeBytes uses CollElementType byteSize`() {
        // capacity=4, element=U8 (1 byte): 4*(1+1)+1 = 9 bytes
        val pool =
            IRCollPool(
                name = "pool",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 4,
            )
        assertEquals(9, pool.sizeBytes)
    }

    @Test
    fun `IRCollRingBuffer sizeBytes uses CollElementType byteSize`() {
        // capacity=4, element=U8 (1 byte): 4*1+3 = 7 bytes
        val rb =
            IRCollRingBuffer(
                name = "rb",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 4,
            )
        assertEquals(7, rb.sizeBytes)
    }

    @Test
    fun `IRCollFixedSlots sizeBytes uses CollElementType byteSize`() {
        // count=8, element=U8 (1 byte), <=8 slots so UINT8 bitfield (1 byte): 8*1+1 = 9 bytes
        val fs =
            IRCollFixedSlots(
                name = "fs",
                elementType = CollElementType.Primitive(VarType.U8),
                count = 8,
            )
        assertEquals(9, fs.sizeBytes)
    }
}
