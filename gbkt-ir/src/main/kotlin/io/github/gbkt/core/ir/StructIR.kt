/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// STRUCT IR TYPES
// =============================================================================
// StructDef: an IR node representing a named C struct type with flat primitive
// fields. Used by the collection system to support structured element types.
//
// CollElementType: sealed interface that lets collection IR nodes accept either
// a primitive VarType or a StructDef as their element/key/value types.
// =============================================================================

/** A single field within a struct definition. */
data class StructFieldDef(val name: String, val type: VarType) {
    /** Size of this field in bytes on Game Boy hardware. */
    val byteSize: Int
        get() = type.byteSize
}

/**
 * A struct type definition that maps to a C `typedef struct { ... }` declaration.
 *
 * Only flat primitive fields (u8, u16, i8, i16) are supported — no nested structs, no pointers, no
 * arrays. This keeps the Game Boy memory model simple and avoids alignment issues on the lcc C89
 * compiler.
 *
 * @param name C identifier used as the typedef name (e.g. "TileHashEntry")
 * @param fields Ordered list of field definitions; must be non-empty; names must be unique
 * @param sourceLocation Optional source location for error reporting
 */
data class StructDef(
    val name: String,
    val fields: List<StructFieldDef>,
    val sourceLocation: SourceLocation? = null,
) {
    /** Total size in bytes — sum of all field sizes. */
    val byteSize: Int
        get() = fields.sumOf { it.byteSize }

    /** Look up a field by name, or null if not found. */
    fun field(name: String): StructFieldDef? = fields.find { it.name == name }

    init {
        require(fields.isNotEmpty()) { "Struct '$name' must have at least one field" }
        require(fields.map { it.name }.distinct().size == fields.size) {
            "Struct '$name' has duplicate field names: ${fields.map { it.name }}"
        }
    }
}

// =============================================================================
// COLLECTION ELEMENT TYPE
// =============================================================================

/**
 * Element type for collections — either a primitive [VarType] or a [StructDef].
 *
 * Replaces raw [VarType] in all four collection IR nodes ([IRCollHashTable], [IRCollPool],
 * [IRCollRingBuffer], [IRCollFixedSlots]) so that collections can hold structured data, not just
 * single primitives.
 *
 * The [cTypeName] property returns the C type string used in generated code:
 * - Primitive: "UINT8", "UINT16", "INT8", "INT16"
 * - Struct: the struct name (e.g. "TileHashEntry")
 *
 * [byteSize] is named consistently with [VarType.byteSize] so that callers can use either without
 * special-casing.
 */
sealed interface CollElementType {
    /** Size of this element type in bytes — consistent with [VarType.byteSize]. */
    val byteSize: Int

    /** C type name used in generated code (GBDK type alias or struct typedef name). */
    val cTypeName: String

    /**
     * Primitive element type — wraps a [VarType].
     *
     * [cTypeName] returns a canonical GBDK-compatible name. Backends that need a different mapping
     * (e.g. GBA) should use their own extension on [varType].
     */
    data class Primitive(val varType: VarType) : CollElementType {
        override val byteSize: Int
            get() = varType.byteSize

        override val cTypeName: String
            get() =
                when (varType) {
                    VarType.U8 -> "UINT8"
                    VarType.U16 -> "UINT16"
                    VarType.I8 -> "INT8"
                    VarType.I16 -> "INT16"
                }
    }

    /**
     * Struct element type — wraps a [StructDef].
     *
     * [cTypeName] returns the struct's name, which must match the typedef name emitted by the
     * backend (e.g. `typedef struct { ... } TileHashEntry;`).
     */
    data class Struct(val structDef: StructDef) : CollElementType {
        override val byteSize: Int
            get() = structDef.byteSize

        override val cTypeName: String
            get() = structDef.name
    }
}
