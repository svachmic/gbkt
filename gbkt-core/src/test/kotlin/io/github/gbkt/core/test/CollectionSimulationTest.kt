/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for collection operation simulation in ScriptOpInterpreter (J10).
 *
 * Verifies that hash table, ring buffer, pool, and fixed slot operations are properly simulated
 * in-memory rather than returning 0 stubs. Each family exercises insert/read/remove/clear
 * round-trips. Unknown function calls must still return 0 gracefully (no crash).
 */
class CollectionSimulationTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    private fun emptyGame() =
        GameIR(
            name = "Test",
            scenes = listOf(SceneIR("main")),
            actors = emptyList(),
            variables = emptyList(),
            startScene = "main",
        )

    /** Evaluate a CallExpr via Assign so we can read the result from variables. */
    private fun ScriptOpInterpreter.evalCall(fn: String, vararg args: Long): Long {
        val argExprs = args.map { Literal(it.toInt()) }
        executeOp(Assign("__result", CallExpr(fn, argExprs)))
        return getVariable("__result")
    }

    /** Execute a CallOp (side-effecting, no return value). */
    private fun ScriptOpInterpreter.execCall(fn: String, vararg args: Long) {
        val argExprs = args.map { Literal(it.toInt()) }
        executeOp(CallOp(fn, argExprs))
    }

    // =========================================================================
    // Hash table tests
    // =========================================================================

    @Test
    fun `ht insert then get returns the stored value`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ht_scores_insert", 1L, 100L)
        val result = interpreter.evalCall("ht_scores_get", 1L)
        assertEquals(100L, result)
    }

    @Test
    fun `ht get on nonexistent key returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("ht_scores_get", 99L)
        assertEquals(0L, result)
    }

    @Test
    fun `ht get on nonexistent table returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("ht_unknown_get", 1L)
        assertEquals(0L, result)
    }

    @Test
    fun `ht contains returns 1 for present key`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ht_items_insert", 5L, 1L)
        val result = interpreter.evalCall("ht_items_contains", 5L)
        assertEquals(1L, result)
    }

    @Test
    fun `ht contains returns 0 for absent key`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("ht_items_contains", 5L)
        assertEquals(0L, result)
    }

    @Test
    fun `ht remove makes key absent`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ht_items_insert", 7L, 42L)
        interpreter.evalCall("ht_items_remove", 7L)
        val contains = interpreter.evalCall("ht_items_contains", 7L)
        assertEquals(0L, contains)
    }

    @Test
    fun `ht size reflects entry count`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        assertEquals(0L, interpreter.evalCall("ht_data_size"))
        interpreter.evalCall("ht_data_insert", 1L, 10L)
        assertEquals(1L, interpreter.evalCall("ht_data_size"))
        interpreter.evalCall("ht_data_insert", 2L, 20L)
        assertEquals(2L, interpreter.evalCall("ht_data_size"))
    }

    @Test
    fun `ht clear empties the table`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ht_data_insert", 1L, 10L)
        interpreter.evalCall("ht_data_insert", 2L, 20L)
        interpreter.evalCall("ht_data_clear")
        assertEquals(0L, interpreter.evalCall("ht_data_size"))
        assertEquals(0L, interpreter.evalCall("ht_data_get", 1L))
    }

    @Test
    fun `ht CallOp side-effecting insert updates state`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        // Use CallOp (statement form) for insert, then read back with CallExpr
        interpreter.execCall("ht_scores_insert", 3L, 999L)
        val result = interpreter.evalCall("ht_scores_get", 3L)
        assertEquals(999L, result)
    }

    @Test
    fun `ht multiple inserts overwrite existing key`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ht_scores_insert", 1L, 50L)
        interpreter.evalCall("ht_scores_insert", 1L, 75L)
        assertEquals(75L, interpreter.evalCall("ht_scores_get", 1L))
        // Size stays 1 since key was overwritten, not added
        assertEquals(1L, interpreter.evalCall("ht_scores_size"))
    }

    // =========================================================================
    // Ring buffer tests
    // =========================================================================

    @Test
    fun `ring push then peek returns first pushed value (FIFO)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ring_events_push", 10L)
        interpreter.evalCall("ring_events_push", 20L)
        // peek does not remove — should return first item
        val result = interpreter.evalCall("ring_events_peek")
        assertEquals(10L, result)
    }

    @Test
    fun `ring push then pop returns first pushed value (FIFO)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ring_events_push", 10L)
        interpreter.evalCall("ring_events_push", 20L)
        val first = interpreter.evalCall("ring_events_pop")
        val second = interpreter.evalCall("ring_events_pop")
        assertEquals(10L, first)
        assertEquals(20L, second)
    }

    @Test
    fun `ring pop on empty buffer returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("ring_events_pop")
        assertEquals(0L, result)
    }

    @Test
    fun `ring peek on empty buffer returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("ring_events_peek")
        assertEquals(0L, result)
    }

    @Test
    fun `ring size reflects entry count`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        assertEquals(0L, interpreter.evalCall("ring_queue_size"))
        interpreter.evalCall("ring_queue_push", 1L)
        assertEquals(1L, interpreter.evalCall("ring_queue_size"))
        interpreter.evalCall("ring_queue_push", 2L)
        assertEquals(2L, interpreter.evalCall("ring_queue_size"))
    }

    @Test
    fun `ring pop decreases size`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ring_queue_push", 5L)
        interpreter.evalCall("ring_queue_push", 6L)
        interpreter.evalCall("ring_queue_pop")
        assertEquals(1L, interpreter.evalCall("ring_queue_size"))
    }

    @Test
    fun `ring clear empties the buffer`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ring_queue_push", 1L)
        interpreter.evalCall("ring_queue_push", 2L)
        interpreter.evalCall("ring_queue_clear")
        assertEquals(0L, interpreter.evalCall("ring_queue_size"))
        assertEquals(0L, interpreter.evalCall("ring_queue_pop"))
    }

    @Test
    fun `ring CallOp side-effecting push updates state`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.execCall("ring_events_push", 42L)
        val result = interpreter.evalCall("ring_events_pop")
        assertEquals(42L, result)
    }

    // =========================================================================
    // Pool tests
    // =========================================================================

    @Test
    fun `pool alloc returns incrementing indices starting at 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val first = interpreter.evalCall("pool_bullets_alloc")
        val second = interpreter.evalCall("pool_bullets_alloc")
        val third = interpreter.evalCall("pool_bullets_alloc")
        assertEquals(0L, first)
        assertEquals(1L, second)
        assertEquals(2L, third)
    }

    @Test
    fun `pool active_count reflects allocated slots`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        assertEquals(0L, interpreter.evalCall("pool_bullets_active_count"))
        interpreter.evalCall("pool_bullets_alloc")
        assertEquals(1L, interpreter.evalCall("pool_bullets_active_count"))
        interpreter.evalCall("pool_bullets_alloc")
        assertEquals(2L, interpreter.evalCall("pool_bullets_active_count"))
    }

    @Test
    fun `pool free removes entry and active_count decreases`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val idx = interpreter.evalCall("pool_bullets_alloc")
        interpreter.evalCall("pool_bullets_alloc") // alloc second slot
        assertEquals(2L, interpreter.evalCall("pool_bullets_active_count"))
        interpreter.evalCall("pool_bullets_free", idx)
        assertEquals(1L, interpreter.evalCall("pool_bullets_active_count"))
    }

    @Test
    fun `pool active_count on fresh pool is 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        assertEquals(0L, interpreter.evalCall("pool_enemies_active_count"))
    }

    @Test
    fun `pool CallOp side-effecting alloc updates state`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.execCall("pool_bullets_alloc")
        assertEquals(1L, interpreter.evalCall("pool_bullets_active_count"))
    }

    @Test
    fun `pool alloc-free-alloc reuses lowest freed index`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        assertEquals(0L, interpreter.evalCall("pool_bullets_alloc"))
        assertEquals(1L, interpreter.evalCall("pool_bullets_alloc"))
        assertEquals(2L, interpreter.evalCall("pool_bullets_alloc"))
        interpreter.evalCall("pool_bullets_free", 1L)
        // Next alloc should reuse index 1, not allocate 3
        assertEquals(1L, interpreter.evalCall("pool_bullets_alloc"))
    }

    @Test
    fun `pool free on nonexistent pool is no-op`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("pool_ghost_free", 0L) // must not throw
        assertEquals(0L, interpreter.evalCall("pool_ghost_active_count"))
    }

    @Test
    fun `pool free same index twice is idempotent`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("pool_bullets_alloc") // index 0
        interpreter.evalCall("pool_bullets_alloc") // index 1
        interpreter.evalCall("pool_bullets_free", 0L)
        interpreter.evalCall("pool_bullets_free", 0L) // double-free
        assertEquals(1L, interpreter.evalCall("pool_bullets_active_count"))
    }

    @Test
    fun `pool alloc after free-all returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("pool_bullets_alloc") // index 0
        interpreter.evalCall("pool_bullets_alloc") // index 1
        interpreter.evalCall("pool_bullets_free", 0L)
        interpreter.evalCall("pool_bullets_free", 1L)
        // Pool is now empty — next alloc should return 0 (lowest available)
        assertEquals(0L, interpreter.evalCall("pool_bullets_alloc"))
    }

    @Test
    fun `pool active_count after alloc-free-alloc cycle is correct`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("pool_bullets_alloc") // 0
        interpreter.evalCall("pool_bullets_alloc") // 1
        interpreter.evalCall("pool_bullets_alloc") // 2
        interpreter.evalCall("pool_bullets_free", 1L) // free slot 1
        interpreter.evalCall("pool_bullets_alloc") // reuses slot 1
        assertEquals(3L, interpreter.evalCall("pool_bullets_active_count"))
    }

    // =========================================================================
    // Fixed slot tests
    // =========================================================================

    @Test
    fun `slot set then get at index returns stored value`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("slot_config_set", 0L, 42L)
        val result = interpreter.evalCall("slot_config_get", 0L)
        assertEquals(42L, result)
    }

    @Test
    fun `slot get at unset index returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("slot_config_get", 5L)
        assertEquals(0L, result)
    }

    @Test
    fun `slot get on nonexistent collection returns 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("slot_unknown_get", 0L)
        assertEquals(0L, result)
    }

    @Test
    fun `slot set overwrites existing value at index`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("slot_config_set", 2L, 10L)
        interpreter.evalCall("slot_config_set", 2L, 99L)
        assertEquals(99L, interpreter.evalCall("slot_config_get", 2L))
    }

    @Test
    fun `slot clear removes index entry`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("slot_config_set", 1L, 77L)
        interpreter.evalCall("slot_config_clear", 1L)
        assertEquals(0L, interpreter.evalCall("slot_config_get", 1L))
    }

    @Test
    fun `slot multiple indices are independent`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("slot_config_set", 0L, 10L)
        interpreter.evalCall("slot_config_set", 1L, 20L)
        interpreter.evalCall("slot_config_set", 2L, 30L)
        assertEquals(10L, interpreter.evalCall("slot_config_get", 0L))
        assertEquals(20L, interpreter.evalCall("slot_config_get", 1L))
        assertEquals(30L, interpreter.evalCall("slot_config_get", 2L))
    }

    @Test
    fun `slot CallOp side-effecting set updates state`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.execCall("slot_config_set", 3L, 55L)
        assertEquals(55L, interpreter.evalCall("slot_config_get", 3L))
    }

    // =========================================================================
    // Collection isolation tests
    // =========================================================================

    @Test
    fun `different collection names have independent state`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ht_table_a_insert", 1L, 100L)
        interpreter.evalCall("ht_table_b_insert", 1L, 200L)
        assertEquals(100L, interpreter.evalCall("ht_table_a_get", 1L))
        assertEquals(200L, interpreter.evalCall("ht_table_b_get", 1L))
    }

    @Test
    fun `ring buffers with different names have independent state`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.evalCall("ring_queue_a_push", 1L)
        interpreter.evalCall("ring_queue_b_push", 2L)
        assertEquals(1L, interpreter.evalCall("ring_queue_a_pop"))
        assertEquals(2L, interpreter.evalCall("ring_queue_b_pop"))
    }

    // =========================================================================
    // Unknown function calls return 0 without crash
    // =========================================================================

    @Test
    fun `unknown function call returns 0 without throwing`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val result = interpreter.evalCall("completely_unknown_function", 1L, 2L)
        assertEquals(0L, result)
    }

    @Test
    fun `unknown CallOp executes without throwing`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.execCall("some_unknown_function") // must not throw
    }

    @Test
    fun `ht prefix with unknown suffix returns 0 without throwing`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        // Function starts with "ht_" but has an unrecognized operation suffix
        val result = interpreter.evalCall("ht_mymap_unknown_op", 1L)
        assertEquals(0L, result)
    }
}
