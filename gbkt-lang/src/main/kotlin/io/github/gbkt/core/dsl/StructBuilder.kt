/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.StructDef
import io.github.gbkt.core.ir.StructFieldDef
import io.github.gbkt.core.ir.VarType

// =============================================================================
// STRUCT BUILDER DSL
// =============================================================================
// Provides the `struct("Name") { field("key", u16); field("value", u8) }` DSL
// for declaring typed struct definitions. Registered structs are stored in
// GameIR.structs and emitted as C typedef struct declarations by the backend.
// =============================================================================

/**
 * Builder for declaring struct types with flat primitive fields.
 *
 * Only primitive types (u8, u16, i8, i16) are supported as field types — no nested structs, no
 * pointers, no arrays. This restriction maps cleanly to the Game Boy memory model and avoids
 * alignment surprises with the lcc C89 compiler.
 *
 * Usage (inside a `game { }` block):
 * ```kotlin
 * val tileEntry = struct("TileHashEntry") {
 *     field("key", u16)
 *     field("value", u8)
 * }
 * // tileEntry.byteSize == 3 (2 bytes for u16 + 1 byte for u8)
 * ```
 */
@GbktDsl
class StructBuilder(private val name: String) {
    private val fields = mutableListOf<StructFieldDef>()

    // Convenience type references — avoids `VarType.U8` verbosity in DSL blocks
    /** UINT8 primitive type (1 byte, unsigned). */
    val u8: VarType = VarType.U8

    /** UINT16 primitive type (2 bytes, unsigned). */
    val u16: VarType = VarType.U16

    /** INT8 primitive type (1 byte, signed). */
    val i8: VarType = VarType.I8

    /** INT16 primitive type (2 bytes, signed). */
    val i16: VarType = VarType.I16

    /**
     * Adds a field with the given [name] and primitive [type].
     *
     * Field names must be unique within the struct. Duplicate names cause an immediate
     * IllegalArgumentException to catch naming errors early.
     *
     * @param name C identifier for the field (must be a valid C identifier)
     * @param type Primitive type for this field — use [u8], [u16], [i8], [i16] shorthands
     */
    fun field(name: String, type: VarType) {
        require(fields.none { it.name == name }) {
            "Duplicate field name '$name' in struct '${this.name}'"
        }
        fields.add(StructFieldDef(name, type))
    }

    /** Builds and validates the [StructDef]. Called by [GameBuilder.struct]. */
    internal fun build(): StructDef = StructDef(name, fields.toList())
}

// =============================================================================
// STRUCT VAR PROXY
// =============================================================================

/**
 * Proxy for accessing fields of a named struct variable via dot-syntax in DSL scripts.
 *
 * A [StructVar] represents a concrete instance of a struct stored at a given C variable name. Field
 * access via `get(fieldName)` returns an [AssignableVar] whose underlying name is
 * `"<instanceName>.<fieldName>"` — this maps directly to C member access syntax.
 *
 * Usage (inside a script block after pool.acquire()):
 * ```kotlin
 * val entry: StructVar = pool.acquire()
 * entry["key"] set 42
 * entry["value"] set 7
 * ```
 */
data class StructVar(val name: String, val structDef: StructDef) {

    /**
     * Returns an [AssignableVar] for field [fieldName] of this struct instance.
     *
     * The returned variable's name is `"<name>.<fieldName>"` which generates `_<name>.<fieldName>`
     * member-access C code.
     *
     * @throws IllegalArgumentException if [fieldName] is not defined in [structDef]
     */
    operator fun get(fieldName: String): AssignableVar {
        requireNotNull(structDef.field(fieldName)) {
            "Struct '${structDef.name}' has no field '$fieldName'. " +
                "Available fields: ${structDef.fields.map { it.name }}"
        }
        return AssignableVar("${name}.${fieldName}")
    }
}

// =============================================================================
// GAME BUILDER EXTENSION — struct() DSL function
// =============================================================================

/**
 * Declares a struct type and registers it with the game for C codegen.
 *
 * Returns the [StructDef] for use in collection declarations:
 * ```kotlin
 * val tileEntry = struct("TileHashEntry") {
 *     field("key", u16)
 *     field("value", u8)
 * }
 * val cache by hashtable(tileEntry, 64)
 * ```
 *
 * The struct is added to [GameBuilder.structs] and emitted as a C `typedef struct` by the backend
 * before any collection declarations that reference it.
 *
 * @param name C typedef name (used in generated `typedef struct { ... } <name>;`)
 * @param block Builder block for declaring fields
 * @return the resulting [StructDef] for immediate use in collection delegates
 */
fun GameBuilder.struct(name: String, block: StructBuilder.() -> Unit): StructDef {
    val builder = StructBuilder(name)
    builder.block()
    val structDef = builder.build()
    registerStruct(structDef)
    return structDef
}
