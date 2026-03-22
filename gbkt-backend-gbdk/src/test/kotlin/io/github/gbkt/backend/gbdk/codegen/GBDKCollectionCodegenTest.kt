/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CollElementType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IRCollFixedSlots
import io.github.gbkt.core.ir.IRCollHashTable
import io.github.gbkt.core.ir.IRCollPool
import io.github.gbkt.core.ir.IRCollRingBuffer
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

// =============================================================================
// COLLECTION CODEGEN TESTS
// Verifies GBDKCollectionCodegen generates correct C for all 4 collection types.
//
// Tests verify:
// 1.  Hash table data arrays (keys, values, used)
// 2.  Hash table functions (insert, lookup, clear)
// 3.  Pool data arrays (data, free bitmap, count)
// 4.  Pool functions (alloc, free, activeCount, hasSpace)
// 5.  Ring buffer data (buf, head, tail, count)
// 6.  Ring buffer functions (push, pop, peek, count)
// 7.  Fixed slots data (data, active bitfield)
// 8.  Fixed slots functions (claim, release, isActive)
// 9.  Pipeline integration: collections appear in main.c output
// =============================================================================

class GBDKCollectionCodegenTest {

    private val codegen = GBDKCollectionCodegen()

    // =========================================================================
    // Hash table
    // =========================================================================

    @Test
    fun `hash table data generates three parallel arrays`() {
        val result = codegen.generateHashTableData("scores", "UINT8", "UINT16", 16)

        assertContains(result, "UINT8 _ht_scores_keys[16]")
        assertContains(result, "UINT16 _ht_scores_values[16]")
        assertContains(result, "UINT8 _ht_scores_used[16]")
    }

    @Test
    fun `hash table functions generate insert lookup and clear`() {
        val result = codegen.generateHashTableFunctions("scores", "UINT8", "UINT16", 16)

        assertContains(result, "void ht_scores_insert(UINT8 key, UINT16 value)")
        assertContains(result, "UINT8 ht_scores_lookup(UINT8 key, UINT16 *out)")
        assertContains(result, "void ht_scores_clear(void)")
    }

    @Test
    fun `hash table insert uses linear probing with modulo`() {
        val result = codegen.generateHashTableFunctions("scores", "UINT8", "UINT8", 8)

        // Linear probing: slot = (slot + 1) % size
        assertContains(result, "% 8")
        // Records used flag
        assertContains(result, "_ht_scores_used[slot] = 1")
    }

    @Test
    fun `hash table lookup returns zero on miss`() {
        val result = codegen.generateHashTableFunctions("hits", "UINT8", "UINT8", 16)

        assertContains(result, "return 0;")
        assertContains(result, "*out = _ht_hits_values[slot];")
        assertContains(result, "return 1;")
    }

    @Test
    fun `hash table IR wrapper delegates correctly`() {
        val ht =
            IRCollHashTable(
                name = "kills",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Primitive(VarType.U16),
                size = 8,
            )
        val dataResult = codegen.generateHashTableData(ht)
        val funcResult = codegen.generateHashTableFunctions(ht)

        assertContains(dataResult, "UINT8 _ht_kills_keys[8]")
        assertContains(dataResult, "UINT16 _ht_kills_values[8]")
        assertContains(funcResult, "void ht_kills_insert(UINT8 key, UINT16 value)")
        assertContains(funcResult, "UINT8 ht_kills_lookup(UINT8 key, UINT16 *out)")
    }

    // =========================================================================
    // Object pool
    // =========================================================================

    @Test
    fun `pool data generates data array bitmap and count`() {
        val result = codegen.generatePoolData("bullets", "UINT8", 16)

        assertContains(result, "UINT8 _pool_bullets_data[16]")
        // bitmap for 16 slots = 2 bytes (ceil(16/8))
        assertContains(result, "UINT8 _pool_bullets_free[2]")
        assertContains(result, "UINT8 _pool_bullets_count")
    }

    @Test
    fun `pool data bitmap rounds up for non-multiple-of-8 capacity`() {
        val result = codegen.generatePoolData("enemies", "UINT8", 10)

        // ceil(10/8) = 2 bytes
        assertContains(result, "UINT8 _pool_enemies_free[2]")
    }

    @Test
    fun `pool functions generate alloc free activeCount hasSpace`() {
        val result = codegen.generatePoolFunctions("bullets", "UINT8", 16)

        assertContains(result, "UINT8 pool_bullets_alloc(void)")
        assertContains(result, "void pool_bullets_free(UINT8 idx)")
        assertContains(result, "UINT8 pool_bullets_activeCount(void)")
        assertContains(result, "UINT8 pool_bullets_hasSpace(void)")
    }

    @Test
    fun `pool alloc returns 0xFF when full`() {
        val result = codegen.generatePoolFunctions("enemies", "UINT8", 8)

        assertContains(result, "return 0xFF;")
    }

    @Test
    fun `pool IR wrapper delegates correctly`() {
        val pool =
            IRCollPool(
                name = "particles",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 32,
            )
        val dataResult = codegen.generatePoolData(pool)
        val funcResult = codegen.generatePoolFunctions(pool)

        assertContains(dataResult, "UINT8 _pool_particles_data[32]")
        assertContains(funcResult, "UINT8 pool_particles_alloc(void)")
    }

    // =========================================================================
    // Ring buffer
    // =========================================================================

    @Test
    fun `ring buffer data generates buf head tail count`() {
        val result = codegen.generateRingBufferData("events", "UINT8", 8)

        assertContains(result, "UINT8 _rb_events_buf[8]")
        assertContains(result, "UINT8 _rb_events_head")
        assertContains(result, "UINT8 _rb_events_tail")
        assertContains(result, "UINT8 _rb_events_count")
    }

    @Test
    fun `ring buffer functions generate push pop peek count`() {
        val result = codegen.generateRingBufferFunctions("events", "UINT8", 8)

        assertContains(result, "void rb_events_push(UINT8 val)")
        assertContains(result, "UINT8 rb_events_pop(void)")
        assertContains(result, "UINT8 rb_events_peek(void)")
        assertContains(result, "UINT8 rb_events_count(void)")
    }

    @Test
    fun `ring buffer push is no-op when full`() {
        val result = codegen.generateRingBufferFunctions("events", "UINT8", 4)

        assertContains(result, "if (_rb_events_count >= 4) return;")
    }

    @Test
    fun `ring buffer IR wrapper delegates correctly`() {
        val rb =
            IRCollRingBuffer(
                name = "inputs",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 16,
            )
        val dataResult = codegen.generateRingBufferData(rb)
        val funcResult = codegen.generateRingBufferFunctions(rb)

        assertContains(dataResult, "UINT8 _rb_inputs_buf[16]")
        assertContains(funcResult, "void rb_inputs_push(UINT8 val)")
    }

    // =========================================================================
    // Fixed slots
    // =========================================================================

    @Test
    fun `fixed slots data uses UINT8 bitfield for 8 or fewer slots`() {
        val result = codegen.generateFixedSlotsData("powerups", "UINT8", 8)

        assertContains(result, "UINT8 _fs_powerups_data[8]")
        assertContains(result, "UINT8 _fs_powerups_active")
        assertFalse(result.contains("UINT16 _fs_powerups_active"))
    }

    @Test
    fun `fixed slots data uses UINT16 bitfield for 9 to 16 slots`() {
        val result = codegen.generateFixedSlotsData("powerups", "UINT8", 12)

        assertContains(result, "UINT8 _fs_powerups_data[12]")
        assertContains(result, "UINT16 _fs_powerups_active")
    }

    @Test
    fun `fixed slots functions generate claim release isActive`() {
        val result = codegen.generateFixedSlotsFunctions("powerups", "UINT8", 8)

        assertContains(result, "UINT8 fs_powerups_claim(void)")
        assertContains(result, "void fs_powerups_release(UINT8 idx)")
        assertContains(result, "UINT8 fs_powerups_isActive(UINT8 idx)")
    }

    @Test
    fun `fixed slots claim returns 0xFF when all slots taken`() {
        val result = codegen.generateFixedSlotsFunctions("keys", "UINT8", 4)

        assertContains(result, "return 0xFF;")
    }

    @Test
    fun `fixed slots IR wrapper delegates correctly`() {
        val fs =
            IRCollFixedSlots(
                name = "slots",
                elementType = CollElementType.Primitive(VarType.U8),
                count = 4,
            )
        val dataResult = codegen.generateFixedSlotsData(fs)
        val funcResult = codegen.generateFixedSlotsFunctions(fs)

        assertContains(dataResult, "UINT8 _fs_slots_data[4]")
        assertContains(funcResult, "UINT8 fs_slots_claim(void)")
    }

    // =========================================================================
    // Pipeline integration — collections in GameIR flow through to main.c
    // =========================================================================

    @Test
    fun `hash table in GameIR produces data arrays in main_c`() {
        val ht =
            IRCollHashTable(
                name = "scores",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Primitive(VarType.U8),
                size = 16,
            )
        val gameIR = buildMinimalGameIR(hashTables = listOf(ht))

        val output = GBDKPipelineV2().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not found")

        assertContains(mainC, "_ht_scores_keys[16]")
        assertContains(mainC, "_ht_scores_values[16]")
        assertContains(mainC, "_ht_scores_used[16]")
        assertContains(mainC, "void ht_scores_insert(")
        assertContains(mainC, "UINT8 ht_scores_lookup(")
    }

    @Test
    fun `pool in GameIR produces alloc and free functions in main_c`() {
        val pool =
            IRCollPool(
                name = "bullets",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 8,
            )
        val gameIR = buildMinimalGameIR(pools = listOf(pool))

        val output = GBDKPipelineV2().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not found")

        assertContains(mainC, "_pool_bullets_data[8]")
        assertContains(mainC, "UINT8 pool_bullets_alloc(void)")
        assertContains(mainC, "void pool_bullets_free(UINT8 idx)")
    }

    @Test
    fun `empty collections in GameIR produce no collection code in main_c`() {
        val gameIR = buildMinimalGameIR()

        val output = GBDKPipelineV2().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not found")

        // No collection declarations when no collections declared
        assertFalse(mainC.contains("_ht_"), "No hash table arrays expected")
        assertFalse(mainC.contains("_pool_"), "No pool arrays expected")
        assertFalse(mainC.contains("_rb_"), "No ring buffer arrays expected")
        assertFalse(mainC.contains("_fs_"), "No fixed slots arrays expected")
    }

    @Test
    fun `struct in GameIR produces typedef struct in main_c`() {
        val struct =
            io.github.gbkt.core.ir.StructDef(
                name = "TileEntry",
                fields =
                    listOf(
                        io.github.gbkt.core.ir.StructFieldDef("key", VarType.U16),
                        io.github.gbkt.core.ir.StructFieldDef("value", VarType.U8),
                    ),
            )
        val gameIR = buildMinimalGameIR(structs = listOf(struct))

        val output = GBDKPipelineV2().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not found")

        // Struct typedef should appear in main.c
        assertContains(mainC, "typedef")
        assertContains(mainC, "TileEntry")
        assertContains(mainC, "UINT16 key")
        assertContains(mainC, "UINT8 value")
    }

    @Test
    fun `struct hash table in GameIR uses struct type name in collection arrays`() {
        val struct =
            io.github.gbkt.core.ir.StructDef(
                name = "TileEntry",
                fields =
                    listOf(
                        io.github.gbkt.core.ir.StructFieldDef("key", VarType.U16),
                        io.github.gbkt.core.ir.StructFieldDef("value", VarType.U8),
                    ),
            )
        val ht =
            IRCollHashTable(
                name = "tileCache",
                keyType = CollElementType.Primitive(VarType.U16),
                valueType = CollElementType.Struct(struct),
                size = 32,
            )
        val gameIR = buildMinimalGameIR(hashTables = listOf(ht), structs = listOf(struct))

        val output = GBDKPipelineV2().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not found")

        // Value array should use the struct type name
        assertContains(mainC, "TileEntry _ht_tileCache_values[32]")
        // Key array uses primitive type
        assertContains(mainC, "UINT16 _ht_tileCache_keys[32]")
    }

    // =========================================================================
    // Collection prototype generation — game.h coverage
    // =========================================================================

    @Test
    fun `hash table prototypes appear in game_h`() {
        val ht =
            IRCollHashTable(
                name = "scores",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Primitive(VarType.U8),
                size = 16,
            )
        val gameIR = buildMinimalGameIR(hashTables = listOf(ht))

        val output = GBDKPipelineV2().generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not found")

        assertContains(gameH, "void ht_scores_insert(")
        assertContains(gameH, "UINT8 ht_scores_lookup(")
        assertContains(gameH, "UINT8 ht_scores_get(")
        assertContains(gameH, "UINT8 ht_scores_contains(")
        assertContains(gameH, "void ht_scores_remove(")
        assertContains(gameH, "void ht_scores_clear(")
    }

    @Test
    fun `pool prototypes appear in game_h`() {
        val pool =
            IRCollPool(
                name = "bullets",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 10,
            )
        val gameIR = buildMinimalGameIR(pools = listOf(pool))

        val output = GBDKPipelineV2().generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not found")

        assertContains(gameH, "UINT8 pool_bullets_alloc(")
        assertContains(gameH, "void pool_bullets_free(")
        assertContains(gameH, "UINT8 pool_bullets_activeCount(")
        assertContains(gameH, "UINT8 pool_bullets_hasSpace(")
    }

    @Test
    fun `ring buffer prototypes appear in game_h`() {
        val rb =
            IRCollRingBuffer(
                name = "events",
                elementType = CollElementType.Primitive(VarType.U16),
                capacity = 8,
            )
        val gameIR = buildMinimalGameIR(ringBuffers = listOf(rb))

        val output = GBDKPipelineV2().generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not found")

        assertContains(gameH, "void rb_events_push(")
        assertContains(gameH, "UINT16 rb_events_pop(")
        assertContains(gameH, "UINT16 rb_events_peek(")
        assertContains(gameH, "UINT8 rb_events_count(")
    }

    @Test
    fun `fixed slots prototypes appear in game_h`() {
        val fs =
            IRCollFixedSlots(
                name = "party",
                elementType = CollElementType.Primitive(VarType.U8),
                count = 4,
            )
        val gameIR = buildMinimalGameIR(fixedSlots = listOf(fs))

        val output = GBDKPipelineV2().generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not found")

        assertContains(gameH, "UINT8 fs_party_claim(")
        assertContains(gameH, "void fs_party_release(")
        assertContains(gameH, "UINT8 fs_party_isActive(")
    }

    @Test
    fun `empty collections produce no prototypes in game_h`() {
        val gameIR = buildMinimalGameIR()

        val output = GBDKPipelineV2().generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not found")

        // No collection function prototypes should appear
        assert("ht_" !in gameH) { "Unexpected hash table prototype in game.h" }
        assert("pool_" !in gameH) { "Unexpected pool prototype in game.h" }
        assert("rb_" !in gameH) { "Unexpected ring buffer prototype in game.h" }
        assert("fs_" !in gameH) { "Unexpected fixed slots prototype in game.h" }
    }
}

// =========================================================================
// Test fixture helpers
// =========================================================================

private fun buildMinimalGameIR(
    hashTables: List<IRCollHashTable> = emptyList(),
    pools: List<IRCollPool> = emptyList(),
    ringBuffers: List<IRCollRingBuffer> = emptyList(),
    fixedSlots: List<IRCollFixedSlots> = emptyList(),
    structs: List<io.github.gbkt.core.ir.StructDef> = emptyList(),
): GameIR =
    GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "main", frameOps = emptyList())),
        hashTables = hashTables,
        pools = pools,
        ringBuffers = ringBuffers,
        fixedSlots = fixedSlots,
        structs = structs,
        startScene = "main",
    )
