/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CollElementType
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// COLLECTION ACCESS TESTS
// Verifies that collection Ref type access methods emit correct IR ops/exprs
// when called inside a ScriptBuilder block.
// =============================================================================

class CollectionAccessTest {

    private fun buildScript(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    // =========================================================================
    // HashTableRef
    // =========================================================================

    private val cache =
        HashTableRef(
            name = "cache",
            keyType = CollElementType.Primitive(VarType.U8),
            valueType = CollElementType.Primitive(VarType.U16),
            size = 16,
        )

    @Test
    fun `HashTableRef put emits CallOp to ht_insert`() {
        val ops = buildScript { cache.put(Literal(5), Literal(100)) }
        assertEquals(1, ops.size)
        val call = assertIs<CallOp>(ops[0])
        assertEquals("ht_cache_insert", call.function)
        assertEquals(2, call.args.size)
    }

    @Test
    fun `HashTableRef get returns CallExpr to ht_get`() {
        val expr = cache.get(Literal(5))
        val call = assertIs<CallExpr>(expr)
        assertEquals("ht_cache_get", call.function)
        assertEquals(1, call.args.size)
    }

    @Test
    fun `HashTableRef contains returns CallExpr to ht_contains`() {
        val expr = cache.contains(Literal(5))
        val call = assertIs<CallExpr>(expr)
        assertEquals("ht_cache_contains", call.function)
    }

    @Test
    fun `HashTableRef remove emits CallOp to ht_remove`() {
        val ops = buildScript { cache.remove(Literal(5)) }
        assertEquals(1, ops.size)
        val call = assertIs<CallOp>(ops[0])
        assertEquals("ht_cache_remove", call.function)
    }

    @Test
    fun `HashTableRef clear emits CallOp to ht_clear`() {
        val ops = buildScript { cache.clear() }
        assertEquals(1, ops.size)
        val call = assertIs<CallOp>(ops[0])
        assertEquals("ht_cache_clear", call.function)
        assertEquals(0, call.args.size)
    }

    // =========================================================================
    // PoolRef
    // =========================================================================

    private val bullets = PoolRef("bullets", CollElementType.Primitive(VarType.U8), 16)

    @Test
    fun `PoolRef acquire returns PoolSlotRef with index var`() {
        val ops = buildScript {
            val slot = bullets.acquire()
            assertIs<PoolSlotRef>(slot)
            assertEquals("_pool_bullets_slot", slot.indexVar)
        }
        // The acquire() emits an Assign op for the temp variable
        val assign = assertIs<io.github.gbkt.core.ir.Assign>(ops[0])
        assertEquals("_pool_bullets_slot", assign.target)
    }

    @Test
    fun `PoolRef free emits CallOp to pool_free`() {
        val ops = buildScript { bullets.free(Literal(3)) }
        assertEquals(1, ops.size)
        val call = assertIs<CallOp>(ops[0])
        assertEquals("pool_bullets_free", call.function)
    }

    @Test
    fun `PoolRef hasSpace returns CallExpr`() {
        val expr = bullets.hasSpace
        val call = assertIs<CallExpr>(expr)
        assertEquals("pool_bullets_hasSpace", call.function)
    }

    @Test
    fun `PoolRef activeCount returns CallExpr`() {
        val expr = bullets.activeCount
        val call = assertIs<CallExpr>(expr)
        assertEquals("pool_bullets_activeCount", call.function)
    }

    @Test
    fun `PoolRef forEach emits ForOp`() {
        val ops = buildScript { bullets.forEach {} }
        assertEquals(1, ops.size)
        assertIs<ForOp>(ops[0])
    }

    // =========================================================================
    // RingBufferRef
    // =========================================================================

    private val events = RingBufferRef("events", CollElementType.Primitive(VarType.U8), 8)

    @Test
    fun `RingBufferRef push emits CallOp to rb_push`() {
        val ops = buildScript { events.push(Literal(42)) }
        assertEquals(1, ops.size)
        val call = assertIs<CallOp>(ops[0])
        assertEquals("rb_events_push", call.function)
    }

    @Test
    fun `RingBufferRef pop returns CallExpr to rb_pop`() {
        val expr = events.pop()
        val call = assertIs<CallExpr>(expr)
        assertEquals("rb_events_pop", call.function)
    }

    @Test
    fun `RingBufferRef peek returns CallExpr to rb_peek`() {
        val expr = events.peek()
        val call = assertIs<CallExpr>(expr)
        assertEquals("rb_events_peek", call.function)
    }

    @Test
    fun `RingBufferRef count returns CallExpr to rb_count`() {
        val expr = events.count
        val call = assertIs<CallExpr>(expr)
        assertEquals("rb_events_count", call.function)
    }

    // =========================================================================
    // FixedSlotsRef
    // =========================================================================

    private val powerups = FixedSlotsRef("powerups", CollElementType.Primitive(VarType.U8), 4)

    @Test
    fun `FixedSlotsRef claim returns CallExpr to fs_claim`() {
        val expr = powerups.claim()
        val call = assertIs<CallExpr>(expr)
        assertEquals("fs_powerups_claim", call.function)
    }

    @Test
    fun `FixedSlotsRef release emits CallOp to fs_release`() {
        val ops = buildScript { powerups.release(Literal(2)) }
        assertEquals(1, ops.size)
        val call = assertIs<CallOp>(ops[0])
        assertEquals("fs_powerups_release", call.function)
    }

    @Test
    fun `FixedSlotsRef isActive returns CallExpr to fs_isActive`() {
        val expr = powerups.isActive(Literal(1))
        val call = assertIs<CallExpr>(expr)
        assertEquals("fs_powerups_isActive", call.function)
    }

    // =========================================================================
    // PoolSlotRef (Gap 3)
    // =========================================================================

    @Test
    fun `PoolSlotRef exists produces NEQ comparison with 0xFF`() {
        val slot = PoolSlotRef("bullets", "_pool_bullets_slot", null)
        val expr = slot.exists
        val bin = assertIs<io.github.gbkt.core.ir.BinaryExpr>(expr)
        assertEquals(io.github.gbkt.core.ir.BinaryOp.NEQ, bin.op)
        val right = assertIs<Literal>(bin.right)
        assertEquals(0xFF, right.value)
    }

    @Test
    fun `PoolSlotRef index returns VarRef to slot variable`() {
        val slot = PoolSlotRef("bullets", "_pool_bullets_slot", null)
        val expr = slot.index
        val ref = assertIs<io.github.gbkt.core.ir.VarRef>(expr)
        assertEquals("_pool_bullets_slot", ref.name)
    }

    // =========================================================================
    // FixedSlotsRef named slots (Gap 4)
    // =========================================================================

    private val namedEquip =
        FixedSlotsRef(
            name = "equip",
            elementType = CollElementType.Primitive(VarType.U8),
            count = 3,
            namedSlots = mapOf("weapon" to 0, "armor" to 1, "accessory" to 2),
        )

    @Test
    fun `FixedSlotsRef slot returns same Expr as get with literal index`() {
        val byName = namedEquip.slot("weapon")
        val byIndex = namedEquip[0]
        assertEquals(byName, byIndex)
    }

    @Test
    fun `FixedSlotsRef slot with second name resolves to index 1`() {
        val expr = namedEquip.slot("armor")
        val access = assertIs<io.github.gbkt.core.ir.ArrayAccessExpr>(expr)
        assertEquals("_fs_equip_data", access.array)
        val idx = assertIs<Literal>(access.index)
        assertEquals(1, idx.value)
    }
}
