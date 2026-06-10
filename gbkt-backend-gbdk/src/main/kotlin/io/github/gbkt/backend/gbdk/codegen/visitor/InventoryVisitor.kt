/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.BuffEffect
import io.github.gbkt.core.ir.ContainerIR
import io.github.gbkt.core.ir.DropTableIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HealEffect
import io.github.gbkt.core.ir.ItemCategoryDef
import io.github.gbkt.core.ir.ItemDef
import io.github.gbkt.core.ir.ScriptEffect

// =============================================================================
// INVENTORY VISITOR
// Generates C code for inventory system: item catalog constants, container
// operations (add/remove/count/contains), use_item dispatch, and drop tables.
//
// All generated code is C89-compliant:
//  - Loop variables declared before loops (CVarDecl before CFor with init=null)
//  - All unsigned literals emit 'Nu' suffix
//  - No function pointers — switch dispatch only
//  - Arrays are fixed-size at compile time (slots from ContainerIR)
// =============================================================================

/**
 * Generates all C code for the inventory system from a [GameIR].
 *
 * Produces:
 * 1. Item catalog constants (ITEM_<ID>_ID, ITEM_<ID>_STACK, CATEGORY_<ID>_DEFAULT_STACK)
 * 2. Container globals (_inv_<id>_items[], _inv_<id>_counts[], _inv_<id>_size)
 * 3. Container operations: inv_<id>_add, inv_<id>_remove, inv_<id>_count, inv_<id>_contains
 * 4. use_item_<id>() dispatcher for item effects (HealEffect, BuffEffect, ScriptEffect)
 * 5. Drop table weighted random selection (roll_drop_table_<id>())
 * 6. _prng() helper and _prng_state global when any drop tables exist
 *
 * @param gameIR The full game IR. Items/containers/dropTables/itemCategories are all read from
 *   here.
 */
class InventoryVisitor(private val gameIR: GameIR) {

    // =========================================================================
    // Item catalog constants — ITEM_<ID>_ID, ITEM_<ID>_STACK, CATEGORY_*
    // =========================================================================

    /**
     * Generate compile-time constants for all items and categories.
     *
     * For each [ItemDef]: emits ITEM_<ID_UPPER>_ID and ITEM_<ID_UPPER>_STACK. For each
     * [ItemCategoryDef]: emits CATEGORY_<ID_UPPER>_DEFAULT_STACK.
     *
     * Stack resolution: item.maxStack (if non-null) > category defaultMaxStack > 1 (fallback).
     */
    fun generateItemConstants(): List<CVarDecl> {
        val result = mutableListOf<CVarDecl>()

        // Category DEFAULT_STACK constants
        for (category in gameIR.itemCategories) {
            val constName = "CATEGORY_${category.id.uppercase()}_DEFAULT_STACK"
            result +=
                CVarDecl(
                    name = constName,
                    type = CU8,
                    initializer = CLiteral(category.defaultMaxStack),
                    isConst = true,
                )
        }

        // Per-item ID and STACK constants
        for ((index, item) in gameIR.items.withIndex()) {
            val idUpper = item.id.uppercase()

            // ITEM_<ID>_ID = index in catalog
            result +=
                CVarDecl(
                    name = "ITEM_${idUpper}_ID",
                    type = CU8,
                    initializer = CLiteral(index),
                    isConst = true,
                )

            // ITEM_<ID>_STACK = resolved max stack
            val resolvedStack = resolveMaxStack(item)
            result +=
                CVarDecl(
                    name = "ITEM_${idUpper}_STACK",
                    type = CU8,
                    initializer = CLiteral(resolvedStack),
                    isConst = true,
                )
        }

        return result
    }

    // =========================================================================
    // _item_names[] lookup table — const char* const array for display names
    // =========================================================================

    /**
     * Generate a `const char* const _item_names[]` lookup table for displaying item names in
     * inventory menus.
     *
     * Returns `null` if [GameIR.items] is empty (no table needed).
     *
     * The generated declaration:
     * ```c
     * const char* const _item_names[] = { "Potion", "Iron Sword", ... };
     * ```
     *
     * Using [CRawExpr] for the array initializer — const char* const arrays cannot be expressed via
     * typed [CArray] with element [CType] since the C AST has no string literal type. This follows
     * the [generateContainerGlobals] pattern for array initializers.
     */
    fun generateItemNamesTable(): CVarDecl? {
        if (gameIR.items.isEmpty()) return null

        // Build initializer: { "Name1", "Name2", ... }
        val namesList =
            gameIR.items.joinToString(", ") { item -> "\"${item.name.replace("\"", "\\\"")}\"" }

        // CArray with CPointer(CU8) element — emits `const UINT8* _item_names[N]` which is
        // compatible with `const char*` on Game Boy hardware (char and UINT8 both 1 byte).
        return CVarDecl(
            name = "_item_names",
            type = CArray(CPointer(CU8), gameIR.items.size),
            initializer = CRawExpr("{ $namesList }"),
            isConst = true,
        )
    }

    /**
     * Resolve the max stack for an item: item.maxStack overrides category defaultMaxStack. Fallback
     * to 1 if neither is defined.
     */
    private fun resolveMaxStack(item: ItemDef): Int {
        val maxStack: Int? = item.maxStack
        if (maxStack != null) return maxStack
        val category = gameIR.itemCategories.find { it.id == item.categoryId }
        return category?.defaultMaxStack ?: 1
    }

    // =========================================================================
    // Container globals — _inv_<id>_items[], _inv_<id>_counts[], _inv_<id>_size
    // =========================================================================

    /**
     * Generate global variable declarations for all containers.
     *
     * For each [ContainerIR]:
     * - `UINT8 _inv_<id>_items[<slots>]` — item ID array (0xFF = empty slot)
     * - `UINT8 _inv_<id>_counts[<slots>]` — stack count array
     * - `UINT8 _inv_<id>_size = 0u` — current occupied slot count
     */
    fun generateContainerGlobals(): List<CVarDecl> {
        val result = mutableListOf<CVarDecl>()
        for (container in gameIR.containers) {
            val id = container.id
            val slots = container.slots

            // Items array initialized to all 0xFF (empty slots)
            val emptyInit = (0 until slots).joinToString(", ") { "0xFF" }
            result +=
                CVarDecl(
                    name = "_inv_${id}_items",
                    type = CArray(CU8, slots),
                    initializer = CRawExpr("{$emptyInit}"),
                )

            // Counts array initialized to all 0
            val zeroInit = (0 until slots).joinToString(", ") { "0" }
            result +=
                CVarDecl(
                    name = "_inv_${id}_counts",
                    type = CArray(CU8, slots),
                    initializer = CRawExpr("{$zeroInit}"),
                )

            // Size counter initialized to 0
            result += CVarDecl(name = "_inv_${id}_size", type = CU8, initializer = CLiteral(0))
        }
        return result
    }

    // =========================================================================
    // Container operation functions — add, remove, count, contains
    // =========================================================================

    /**
     * Generate all four operation functions for a single container.
     *
     * Generated functions:
     * - inv_<id>_add(UINT8 item_id, UINT8 count) → UINT8 (1=success, 0=full)
     * - inv_<id>_remove(UINT8 item_id, UINT8 count) → UINT8 (1=success, 0=not found)
     * - inv_<id>_count(UINT8 item_id) → UINT8 (stack count)
     * - inv_<id>_contains(UINT8 item_id) → UINT8 (1=found, 0=not found)
     */
    fun generateContainerFunctions(container: ContainerIR): List<CFunction> {
        return listOf(
            generateAddFunction(container),
            generateRemoveFunction(container),
            generateCountFunction(container),
            generateContainsFunction(container),
        )
    }

    /**
     * Generate inv_<id>_add(UINT8 item_id, UINT8 count) — finds existing stack or adds new slot.
     *
     * C89 pattern: loop variable `i` declared before the for loop. First loop: find existing stack
     * of same item_id, increment count, return 1u. If not found: check size < slots, add new slot,
     * increment size, return 1u. If full: return 0u.
     */
    private fun generateAddFunction(container: ContainerIR): CFunction {
        val id = container.id
        val slots = container.slots

        // C89: declare loop variable before loop
        val iDecl = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0))

        // First loop: find existing stack
        val findExistingLoop =
            CFor(
                init = null, // C89: declared above
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(slots)),
                increment = CRawExpr("i++"),
                body =
                    listOf(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CArrayAccess(CVar("_inv_${id}_items"), CVar("i")),
                                    "==",
                                    CVar("item_id"),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CArrayAccess(CVar("_inv_${id}_counts"), CVar("i")),
                                            "+=",
                                            CVar("count"),
                                        )
                                    ),
                                    CReturn(CLiteral(1)),
                                ),
                        )
                    ),
            )

        // If not found: add to new slot
        val addNewSlotBlock =
            CIf(
                condition = CBinaryExpr(CVar("_inv_${id}_size"), "<", CLiteral(slots)),
                thenBody =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("_inv_${id}_items"), CVar("_inv_${id}_size")),
                                "=",
                                CVar("item_id"),
                            )
                        ),
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("_inv_${id}_counts"), CVar("_inv_${id}_size")),
                                "=",
                                CVar("count"),
                            )
                        ),
                        CExprStatement(CBinaryExpr(CVar("_inv_${id}_size"), "+=", CLiteral(1))),
                        CReturn(CLiteral(1)),
                    ),
            )

        val categoryFilterComment =
            container.categoryFilter?.let {
                listOf(CComment("Category filter: $it (validation in DSL layer)"))
            } ?: emptyList()

        return CFunction(
            name = "inv_${id}_add",
            returnType = CU8,
            params = listOf(CParam("item_id", CU8), CParam("count", CU8)),
            body =
                categoryFilterComment +
                    listOf(iDecl, findExistingLoop, addNewSlotBlock) +
                    listOf(CReturn(CLiteral(0))),
        )
    }

    /**
     * Generate inv_<id>_remove(UINT8 item_id, UINT8 count) — decrements count and compacts on zero.
     *
     * C89: loop variable declared before loop. Find slot with matching item_id. Decrement count by
     * requested amount. If count reaches 0: swap-remove (copy last slot to this position),
     * decrement size. Return 1u on success, 0u if not found.
     */
    private fun generateRemoveFunction(container: ContainerIR): CFunction {
        val id = container.id
        val slots = container.slots

        val iDecl = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0))

        // Loop body: find matching item_id
        val loopBody =
            listOf(
                CIf(
                    condition =
                        CBinaryExpr(
                            CArrayAccess(CVar("_inv_${id}_items"), CVar("i")),
                            "==",
                            CVar("item_id"),
                        ),
                    thenBody =
                        listOf(
                            // Decrement count
                            CExprStatement(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_inv_${id}_counts"), CVar("i")),
                                    "-=",
                                    CVar("count"),
                                )
                            ),
                            // If count reaches 0: compact via swap-remove
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_inv_${id}_counts"), CVar("i")),
                                        "==",
                                        CLiteral(0),
                                    ),
                                thenBody =
                                    listOf(
                                        // Copy last slot into this position
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_inv_${id}_items"), CVar("i")),
                                                "=",
                                                CArrayAccess(
                                                    CVar("_inv_${id}_items"),
                                                    CBinaryExpr(
                                                        CVar("_inv_${id}_size"),
                                                        "-",
                                                        CLiteral(1),
                                                    ),
                                                ),
                                            )
                                        ),
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_inv_${id}_counts"), CVar("i")),
                                                "=",
                                                CArrayAccess(
                                                    CVar("_inv_${id}_counts"),
                                                    CBinaryExpr(
                                                        CVar("_inv_${id}_size"),
                                                        "-",
                                                        CLiteral(1),
                                                    ),
                                                ),
                                            )
                                        ),
                                        // Clear last slot
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(
                                                    CVar("_inv_${id}_items"),
                                                    CBinaryExpr(
                                                        CVar("_inv_${id}_size"),
                                                        "-",
                                                        CLiteral(1),
                                                    ),
                                                ),
                                                "=",
                                                CRawExpr("0xFF"),
                                            )
                                        ),
                                        // Decrement size
                                        CExprStatement(
                                            CBinaryExpr(CVar("_inv_${id}_size"), "-=", CLiteral(1))
                                        ),
                                    ),
                            ),
                            CReturn(CLiteral(1)),
                        ),
                )
            )

        val searchLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(slots)),
                increment = CRawExpr("i++"),
                body = loopBody,
            )

        return CFunction(
            name = "inv_${id}_remove",
            returnType = CU8,
            params = listOf(CParam("item_id", CU8), CParam("count", CU8)),
            body = listOf(iDecl, searchLoop, CReturn(CLiteral(0))),
        )
    }

    /**
     * Generate inv_<id>_count(UINT8 item_id) → UINT8.
     *
     * Linear scan — returns the count of the matching item_id, 0u if not found.
     */
    private fun generateCountFunction(container: ContainerIR): CFunction {
        val id = container.id
        val slots = container.slots

        val iDecl = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0))

        val searchLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(slots)),
                increment = CRawExpr("i++"),
                body =
                    listOf(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CArrayAccess(CVar("_inv_${id}_items"), CVar("i")),
                                    "==",
                                    CVar("item_id"),
                                ),
                            thenBody =
                                listOf(CReturn(CArrayAccess(CVar("_inv_${id}_counts"), CVar("i")))),
                        )
                    ),
            )

        return CFunction(
            name = "inv_${id}_count",
            returnType = CU8,
            params = listOf(CParam("item_id", CU8)),
            body = listOf(iDecl, searchLoop, CReturn(CLiteral(0))),
        )
    }

    /**
     * Generate inv_<id>_contains(UINT8 item_id) → UINT8.
     *
     * Returns 1u if the item is present, 0u otherwise.
     */
    private fun generateContainsFunction(container: ContainerIR): CFunction {
        val id = container.id
        val slots = container.slots

        val iDecl = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0))

        val searchLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(slots)),
                increment = CRawExpr("i++"),
                body =
                    listOf(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CArrayAccess(CVar("_inv_${id}_items"), CVar("i")),
                                    "==",
                                    CVar("item_id"),
                                ),
                            thenBody = listOf(CReturn(CLiteral(1))),
                        )
                    ),
            )

        return CFunction(
            name = "inv_${id}_contains",
            returnType = CU8,
            params = listOf(CParam("item_id", CU8)),
            body = listOf(iDecl, searchLoop, CReturn(CLiteral(0))),
        )
    }

    // =========================================================================
    // Use item effect dispatcher
    // =========================================================================

    /**
     * Generate use_item_<containerId>(UINT8 item_id) if any items have non-empty effects.
     *
     * Uses a CSwitch on item_id with one case per item that has effects. Effect dispatch:
     * - HealEffect: emit `target_hp += amount` (raw placeholder — game defines target_hp)
     * - BuffEffect: emit comment + raw buff operation
     * - ScriptEffect: emit ops via comment (full ScriptOpVisitor integration is game-specific)
     *
     * Only generated when at least one item has non-empty effects.
     */
    fun generateUseItemFunction(container: ContainerIR): List<CFunction> {
        val itemsWithEffects = gameIR.items.filter { it.effects.isNotEmpty() }
        if (itemsWithEffects.isEmpty()) return emptyList()

        val cases = mutableListOf<CSwitchCase>()

        for (item in gameIR.items) {
            if (item.effects.isEmpty()) continue
            val itemIndex = gameIR.items.indexOf(item)
            val caseBody = mutableListOf<CStatement>()

            for (effect in item.effects) {
                when (effect) {
                    is HealEffect -> {
                        // Emit heal: target_hp += amount
                        caseBody +=
                            CExprStatement(
                                CBinaryExpr(CVar("target_hp"), "+=", CLiteral(effect.amount))
                            )
                    }
                    is BuffEffect -> {
                        // Emit buff as comment + raw stat operation
                        caseBody +=
                            CComment(
                                "Buff: ${effect.statId} +${effect.amount} for ${effect.duration} turns"
                            )
                        caseBody +=
                            CExprStatement(
                                CBinaryExpr(CVar("_buff_stat"), "+=", CLiteral(effect.amount))
                            )
                    }
                    is ScriptEffect -> {
                        // Script effects: emit comment noting the script ops
                        caseBody +=
                            CComment(
                                "ScriptEffect: ${effect.ops.size} ops (game-specific dispatch)"
                            )
                    }
                    else -> {
                        // Unknown effect type: emit comment for extension effects
                        caseBody += CComment("Unknown effect type: ${effect::class.simpleName}")
                    }
                }
            }
            caseBody += CBreak
            cases += CSwitchCase(value = CLiteral(itemIndex), body = caseBody)
        }

        val switchStmt = CSwitch(expr = CVar("item_id"), cases = cases)

        return listOf(
            CFunction(
                name = "use_item_${container.id}",
                returnType = CVoid,
                params = listOf(CParam("item_id", CU8)),
                body = listOf(switchStmt),
            )
        )
    }

    // =========================================================================
    // Drop table functions — roll_drop_table_<id>() with weighted random
    // =========================================================================

    /**
     * Generate drop table weighted random selection functions.
     *
     * For each [DropTableIR]: generates roll_drop_table_<id>(void) → UINT8. Also generates _prng()
     * helper and _prng_state global when any drop tables exist.
     */
    fun generateDropTableFunctions(): List<CFunction> {
        if (gameIR.dropTables.isEmpty()) return emptyList()

        val functions = mutableListOf<CFunction>()
        for (dropTable in gameIR.dropTables) {
            functions += generateRollDropTableFunction(dropTable)
        }
        return functions
    }

    /**
     * Generate the _prng_state global (UINT8) when drop tables are present.
     *
     * Declared separately so it can be included in main.c globals alongside container globals.
     */
    fun generatePrngGlobal(): List<CVarDecl> {
        if (gameIR.dropTables.isEmpty()) return emptyList()
        return listOf(CVarDecl(name = "_prng_state", type = CU8, initializer = CLiteral(1)))
    }

    /**
     * Generate the _prng() helper function when drop tables are present.
     *
     * Simple 8-bit LCG: `_prng_state = _prng_state * 5 + 1; return _prng_state;`
     */
    fun generatePrngFunction(): List<CFunction> {
        if (gameIR.dropTables.isEmpty()) return emptyList()

        return listOf(
            CFunction(
                name = "_prng",
                returnType = CU8,
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("_prng_state"),
                                "=",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("_prng_state"), "*", CLiteral(5)),
                                    "+",
                                    CLiteral(1),
                                ),
                            )
                        ),
                        CReturn(CVar("_prng_state")),
                    ),
            )
        )
    }

    /**
     * Generate roll_drop_table_<id>(void) → UINT8 for a single [DropTableIR].
     *
     * Weighted random selection:
     * 1. Calculate total weight sum at compile time
     * 2. `UINT8 roll = _prng() % <totalWeight>;`
     * 3. Cumulative weight checks: `if (roll < w1) return ITEM_X_ID; if (roll < w1+w2) ...`
     * 4. Returns 0xFF if no entries or no match (empty table fallback)
     */
    private fun generateRollDropTableFunction(dropTable: DropTableIR): CFunction {
        if (dropTable.entries.isEmpty()) {
            return CFunction(
                name = "roll_drop_table_${dropTable.id}",
                returnType = CU8,
                body = listOf(CReturn(CRawExpr("0xFF"))),
            )
        }

        val totalWeight = dropTable.entries.sumOf { it.weight }

        val body = mutableListOf<CStatement>()

        // UINT8 roll = _prng() % totalWeight;
        val rollDecl =
            CVarDecl(
                name = "roll",
                type = CU8,
                initializer = CBinaryExpr(CCall("_prng"), "%", CLiteral(totalWeight)),
            )
        body += rollDecl

        // Cumulative weight checks
        var cumulative = 0
        for (entry in dropTable.entries) {
            cumulative += entry.weight
            // Find item index for this entry
            val itemIndex = gameIR.items.indexOfFirst { it.id == entry.itemId }
            val returnValue =
                if (itemIndex >= 0) {
                    CLiteral(itemIndex)
                } else {
                    // Item not found in catalog — return 0xFF as sentinel
                    CRawExpr("0xFF")
                }
            body +=
                CIf(
                    condition = CBinaryExpr(CVar("roll"), "<", CLiteral(cumulative)),
                    thenBody = listOf(CReturn(returnValue)),
                )
        }

        // Fallback: 0xFF (no match)
        body += CReturn(CRawExpr("0xFF"))

        return CFunction(name = "roll_drop_table_${dropTable.id}", returnType = CU8, body = body)
    }
}
