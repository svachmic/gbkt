/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRSaveArrayAccess
import io.github.gbkt.core.ir.IRSaveArrayWrite
import io.github.gbkt.core.ir.IRSaveCopy
import io.github.gbkt.core.ir.IRSaveErase
import io.github.gbkt.core.ir.IRSaveExists
import io.github.gbkt.core.ir.IRSaveFieldRead
import io.github.gbkt.core.ir.IRSaveFieldWrite
import io.github.gbkt.core.ir.IRSaveLoad
import io.github.gbkt.core.ir.IRSaveSave
import io.github.gbkt.core.ir.IRVar
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// SAVE DATA TYPES
// =============================================================================

/** Types of fields that can be stored in save data. */
enum class SaveFieldType(val cType: String, val baseSize: Int) {
    U8("UINT8", 1),
    U16("UINT16", 2),
    I8("INT8", 1),
    FLAGS("UINT8", 1),
    ARRAY("UINT8", 1), // Size = baseSize * arraySize
    STRING("char", 1), // Size = baseSize * arraySize
}

/** Checksum algorithms for save data integrity. */
enum class Checksum(val size: Int) {
    /** No checksum - fastest but no corruption detection */
    NONE(0),
    /** XOR all bytes - simple, 1 byte */
    XOR(1),
    /** CRC-8-CCITT - good error detection, 1 byte */
    CRC8(1),
    /** 16-bit sum - catches more errors, 2 bytes */
    SUM16(2),
}

// =============================================================================
// SAVE DATA MODEL
// =============================================================================

/** A single field in save data. */
data class SaveField(
    val name: String,
    val type: SaveFieldType,
    val offset: Int,
    val size: Int,
    val arraySize: Int = 0,
    val defaultValue: Int = 0,
)

/** Configuration for save data. */
data class SaveConfig(
    val slots: Int = 1,
    val checksum: Checksum = Checksum.NONE,
    val magic: String? = null,
    val version: Int = 1,
) {
    /** Calculate header size (magic + version + reserved) */
    val headerSize: Int
        get() = (if (magic != null) 4 else 0) + 2 // version(1) + reserved(1)
}

/** Complete save data definition. */
data class SaveData(val name: String, val fields: List<SaveField>, val config: SaveConfig) {
    /** Total size of one save slot in bytes */
    val slotSize: Int
        get() = config.headerSize + fields.sumOf { it.size } + config.checksum.size

    /** Size of the data fields only (excluding header and checksum) */
    val dataSize: Int
        get() = fields.sumOf { it.size }
}

// =============================================================================
// SAVE FIELD EXPRESSIONS - Type-safe field access
// =============================================================================

/**
 * Expression wrapper for save fields. Allows both reading and writing save data with natural
 * syntax.
 *
 * Usage: save.score set 100 save.score += 10 whenever(save.score isAbove 1000) { ... }
 */
class SaveFieldExpr(
    private val saveName: String,
    private val fieldName: String,
    private val fieldType: SaveFieldType,
    private val arraySize: Int = 0,
) : Expr(IRSaveFieldRead(saveName, fieldName)) {

    /** Assign an integer value */
    infix fun set(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveFieldWrite(saveName, fieldName, IRLiteral(value)))
        }
    }

    /** Assign an expression value */
    infix fun set(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveFieldWrite(saveName, fieldName, value.ir))
        }
    }

    /** Compound add-assign: save.score += 10 */
    operator fun plusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.ADD, IRLiteral(value)),
                    )
                )
        }
    }

    operator fun plusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRSaveFieldWrite(saveName, fieldName, IRBinary(ir, BinaryOp.ADD, value.ir)))
        }
    }

    /** Compound subtract-assign: save.lives -= 1 */
    operator fun minusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.SUB, IRLiteral(value)),
                    )
                )
        }
    }

    operator fun minusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRSaveFieldWrite(saveName, fieldName, IRBinary(ir, BinaryOp.SUB, value.ir)))
        }
    }

    /** Compound multiply-assign */
    operator fun timesAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.MUL, IRLiteral(value)),
                    )
                )
        }
    }

    /** Compound divide-assign */
    operator fun divAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.DIV, IRLiteral(value)),
                    )
                )
        }
    }

    /** Array element access: save.inventory[0] */
    operator fun get(index: Int): SaveArrayElementExpr {
        require(fieldType == SaveFieldType.ARRAY || fieldType == SaveFieldType.STRING) {
            "Index access only available for array/string fields"
        }
        require(index in 0 until arraySize) {
            "Array index $index out of bounds (size: $arraySize)"
        }
        return SaveArrayElementExpr(saveName, fieldName, IRLiteral(index))
    }

    /** Array element access with variable index */
    operator fun get(index: Expr): SaveArrayElementExpr {
        require(fieldType == SaveFieldType.ARRAY || fieldType == SaveFieldType.STRING) {
            "Index access only available for array/string fields"
        }
        return SaveArrayElementExpr(saveName, fieldName, index.ir)
    }
}

/** Expression for accessing array elements in save data. */
class SaveArrayElementExpr(
    private val saveName: String,
    private val fieldName: String,
    private val indexExpr: IRExpression,
) : Expr(IRSaveArrayAccess(saveName, fieldName, indexExpr)) {

    /** Assign value to array element */
    infix fun set(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRSaveArrayWrite(saveName, fieldName, indexExpr, IRLiteral(value)))
        }
    }

    infix fun set(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRSaveArrayWrite(saveName, fieldName, indexExpr, value.ir))
        }
    }
}

/**
 * Expression for flags field with bitwise operations.
 *
 * Usage: whenever(save.flags[0] isEqualTo 1) { ... } // Check flag 0 save.flags.setBit(2) // Set
 * flag 2 save.flags.clearBit(3) // Clear flag 3 save.flags.toggleBit(1) // Toggle flag 1
 */
class SaveFlagsExpr(private val saveName: String, private val fieldName: String) :
    Expr(IRSaveFieldRead(saveName, fieldName)) {

    /** Get a specific flag (bit) as a condition (0 or 1) */
    operator fun get(bit: Int): Expr {
        require(bit in 0..7) { "Flag index must be 0-7" }
        return Expr(
            IRBinary(IRBinary(ir, BinaryOp.SHR, IRLiteral(bit)), BinaryOp.AND, IRLiteral(1))
        )
    }

    /** Check if a specific flag is set */
    fun isSet(bit: Int): Condition {
        require(bit in 0..7) { "Flag index must be 0-7" }
        return Condition(
            IRBinary(IRBinary(ir, BinaryOp.AND, IRLiteral(1 shl bit)), BinaryOp.NEQ, IRLiteral(0))
        )
    }

    /** Check if a specific flag is clear */
    fun isClear(bit: Int): Condition {
        require(bit in 0..7) { "Flag index must be 0-7" }
        return Condition(
            IRBinary(IRBinary(ir, BinaryOp.AND, IRLiteral(1 shl bit)), BinaryOp.EQ, IRLiteral(0))
        )
    }

    /** Set a specific flag (bit = 1) */
    fun setBit(bit: Int) {
        require(bit in 0..7) { "Flag index must be 0-7" }
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.OR, IRLiteral(1 shl bit)),
                    )
                )
        }
    }

    /** Clear a specific flag (bit = 0) */
    fun clearBit(bit: Int) {
        require(bit in 0..7) { "Flag index must be 0-7" }
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.AND, IRLiteral((1 shl bit).inv() and 0xFF)),
                    )
                )
        }
    }

    /** Toggle a specific flag */
    fun toggleBit(bit: Int) {
        require(bit in 0..7) { "Flag index must be 0-7" }
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRSaveFieldWrite(
                        saveName,
                        fieldName,
                        IRBinary(ir, BinaryOp.XOR, IRLiteral(1 shl bit)),
                    )
                )
        }
    }

    /** Assign the entire flags byte */
    infix fun set(value: Int) {
        require(value in 0..255) { "Flags value must be 0-255" }
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveFieldWrite(saveName, fieldName, IRLiteral(value)))
        }
    }
}

// =============================================================================
// SAVE DATA BUILDERS
// =============================================================================

/** Configuration builder for save data. */
@GbktDsl
class SaveConfigBuilder {
    /** Number of save slots (typically 1-3) */
    var slots: Int = 1

    /** Checksum algorithm for data integrity */
    var checksum: Checksum = Checksum.NONE

    /** 4-character magic string for validation (e.g., "GBKT") */
    var magic: String? = null

    /** Save format version (increment when changing structure) */
    var version: Int = 1

    fun build(): SaveConfig {
        magic?.let { require(it.length <= 4) { "Magic string must be 4 characters or less" } }
        require(slots in 1..8) { "Slots must be between 1 and 8" }
        require(version in 1..255) { "Version must be between 1 and 255" }
        return SaveConfig(slots, checksum, magic, version)
    }
}

/**
 * Property delegate provider for save fields. Uses provideDelegate to capture the property name
 * automatically.
 */
class SaveFieldDelegate<T>(
    private val fieldType: SaveFieldType,
    private val arraySize: Int = 0,
    private val defaultValue: Int = 0,
) : PropertyDelegateProvider<SaveDataBuilder, ReadOnlyProperty<SaveDataBuilder, T>> {

    override fun provideDelegate(
        thisRef: SaveDataBuilder,
        property: KProperty<*>,
    ): ReadOnlyProperty<SaveDataBuilder, T> {
        val size =
            when (fieldType) {
                SaveFieldType.ARRAY,
                SaveFieldType.STRING -> fieldType.baseSize * arraySize
                else -> fieldType.baseSize
            }

        val field =
            SaveField(
                name = property.name,
                type = fieldType,
                offset = thisRef.currentOffset,
                size = size,
                arraySize = arraySize,
                defaultValue = defaultValue,
            )

        thisRef.addField(field)

        @Suppress("UNCHECKED_CAST")
        return ReadOnlyProperty { _, _ -> thisRef.getFieldExpr(property.name) as T }
    }
}

/**
 * Builder for save data structures.
 *
 * Usage:
 * ```
 * val save = saveData("mygame") {
 *     var score by u16Field()
 *     var level by u8Field(default = 1)
 *     var flags by flagsField()
 *
 *     config {
 *         slots = 3
 *         checksum = Checksum.CRC8
 *         magic = "GBKT"
 *     }
 * }
 * ```
 */
@GbktDsl
class SaveDataBuilder(internal val saveName: String) {
    private val fields = mutableListOf<SaveField>()
    private val fieldExprs = mutableMapOf<String, Any>()
    private var config = SaveConfig()
    internal var currentOffset = 0

    // Field factory functions

    /** Unsigned 8-bit field (0-255) */
    fun u8Field(default: Int = 0): SaveFieldDelegate<SaveFieldExpr> {
        require(default in 0..255) { "Default value must be 0-255 for u8" }
        return SaveFieldDelegate(SaveFieldType.U8, defaultValue = default)
    }

    /** Unsigned 16-bit field (0-65535) */
    fun u16Field(default: Int = 0): SaveFieldDelegate<SaveFieldExpr> {
        require(default in 0..65535) { "Default value must be 0-65535 for u16" }
        return SaveFieldDelegate(SaveFieldType.U16, defaultValue = default)
    }

    /** Signed 8-bit field (-128 to 127) */
    fun i8Field(default: Int = 0): SaveFieldDelegate<SaveFieldExpr> {
        require(default in -128..127) { "Default value must be -128 to 127 for i8" }
        return SaveFieldDelegate(SaveFieldType.I8, defaultValue = default)
    }

    /** 8-bit flags field for boolean flags */
    fun flagsField(): SaveFieldDelegate<SaveFlagsExpr> {
        return SaveFieldDelegate(SaveFieldType.FLAGS)
    }

    /** Fixed-size array of u8 values */
    fun arrayField(size: Int): SaveFieldDelegate<SaveFieldExpr> {
        require(size in 1..255) { "Array size must be 1-255" }
        return SaveFieldDelegate(SaveFieldType.ARRAY, arraySize = size)
    }

    /** Fixed-length string field */
    fun stringField(length: Int): SaveFieldDelegate<SaveFieldExpr> {
        require(length in 1..255) { "String length must be 1-255" }
        return SaveFieldDelegate(SaveFieldType.STRING, arraySize = length)
    }

    /** Configuration block */
    fun config(init: SaveConfigBuilder.() -> Unit) {
        config = SaveConfigBuilder().apply(init).build()
    }

    internal fun addField(field: SaveField) {
        fields.add(field)

        // Create the appropriate expression type
        val expr: Any =
            when (field.type) {
                SaveFieldType.FLAGS -> SaveFlagsExpr(saveName, field.name)
                else -> SaveFieldExpr(saveName, field.name, field.type, field.arraySize)
            }
        fieldExprs[field.name] = expr

        currentOffset += field.size
    }

    internal fun getFieldExpr(name: String): Any {
        return fieldExprs[name] ?: error("Field $name not found")
    }

    fun build(): SaveData {
        return SaveData(name = saveName, fields = fields.toList(), config = config)
    }

    internal fun buildHandle(): SaveDataHandle {
        return SaveDataHandle(build(), fieldExprs.toMap())
    }
}

// =============================================================================
// SAVE DATA HANDLE - Runtime operations
// =============================================================================

/**
 * Handle for save data runtime operations. Returned by saveData() DSL function.
 *
 * Usage:
 * ```
 * save.load(slot = 0)
 * save.score += 10
 * save.save()
 *
 * whenever(save.exists(slot = 0)) { ... }
 * save.erase(slot = 1)
 * save.copy(from = 0, to = 1)
 * ```
 */
class SaveDataHandle
internal constructor(internal val data: SaveData, private val fieldExprs: Map<String, Any>) {
    /** Load save data from a slot into RAM buffer */
    fun load(slot: Int) {
        require(slot in 0 until data.config.slots) {
            "Slot $slot out of range (max: ${data.config.slots - 1})"
        }
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveLoad(data.name, IRLiteral(slot)))
        }
    }

    /** Load from a variable slot */
    fun load(slot: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveLoad(data.name, slot.ir))
        }
    }

    /** Save data from RAM buffer to a slot */
    fun save(slot: Int) {
        require(slot in 0 until data.config.slots) {
            "Slot $slot out of range (max: ${data.config.slots - 1})"
        }
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveSave(data.name, IRLiteral(slot)))
        }
    }

    /** Save to last loaded slot (slot 0 if never loaded) */
    fun save() {
        if (RecordingContext.isRecording) {
            // Use the current slot variable
            RecordingContext.require()
                .emit(IRSaveSave(data.name, IRVar("${data.name}_current_slot")))
        }
    }

    /** Save to a variable slot */
    fun save(slot: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveSave(data.name, slot.ir))
        }
    }

    /** Check if a slot has valid save data */
    fun exists(slot: Int): Condition {
        require(slot in 0 until data.config.slots) {
            "Slot $slot out of range (max: ${data.config.slots - 1})"
        }
        return Condition(IRSaveExists(data.name, IRLiteral(slot)))
    }

    /** Check if a variable slot has valid save data */
    fun exists(slot: Expr): Condition {
        return Condition(IRSaveExists(data.name, slot.ir))
    }

    /** Erase a save slot (fill with 0xFF) */
    fun erase(slot: Int) {
        require(slot in 0 until data.config.slots) {
            "Slot $slot out of range (max: ${data.config.slots - 1})"
        }
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveErase(data.name, IRLiteral(slot)))
        }
    }

    /** Erase all slots */
    fun eraseAll() {
        for (slot in 0 until data.config.slots) {
            erase(slot)
        }
    }

    /** Copy one slot to another */
    fun copy(from: Int, to: Int) {
        require(from in 0 until data.config.slots) {
            "From slot $from out of range (max: ${data.config.slots - 1})"
        }
        require(to in 0 until data.config.slots) {
            "To slot $to out of range (max: ${data.config.slots - 1})"
        }
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSaveCopy(data.name, IRLiteral(from), IRLiteral(to)))
        }
    }

    /** Get a field expression by name (for dynamic access) */
    @Suppress("UNCHECKED_CAST")
    fun <T> field(name: String): T {
        return fieldExprs[name] as? T
            ?: error("Field '$name' not found in save data '${data.name}'")
    }
}
