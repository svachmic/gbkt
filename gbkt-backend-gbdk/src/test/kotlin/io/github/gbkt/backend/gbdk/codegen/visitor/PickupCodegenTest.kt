/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.pickup.PickupDef
import io.github.gbkt.core.pickup.PickupSystemConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// PICKUP CODEGEN TESTS (Plan 06.8-09 Task 2 success criteria)
//
// 4 tests verifying GBDKSystemVisitor "pickup_system" branch:
//   - pickup system generates pickup_init and pickup_check_collect functions
//   - timed pickup generates pickup_update function with duration decrement
//   - respawning pickup generates pickup_respawn_check function
//   - pickup var decls include active array and count global
// =============================================================================

/** Build a minimal GameIR with a pickup_system GenericSystem. */
private fun buildPickupGameIR(
    config: PickupSystemConfig = PickupSystemConfig(),
    id: String = "pickups",
): GameIR {
    val system =
        GenericSystem(id = id, config = mapOf("type" to "pickup_system", "pickupConfig" to config))
    return GameIR(
        name = "TestPickupGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = listOf(SceneIR(id = "gameplay")),
        systems = listOf(system),
        startScene = "gameplay",
    )
}

class PickupCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: pickup system generates pickup_init and pickup_check_collect functions
    // =========================================================================

    @Test
    fun `pickup system generates init and collect functions`() {
        val config =
            PickupSystemConfig(
                pickups = listOf(PickupDef(id = "coin", effectType = "instant", value = 10)),
                maxTotalPickups = 8,
            )
        val gameIR = buildPickupGameIR(config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pickup_init_pickups"),
            "Expected 'pickup_init_pickups' function in generated C",
        )
        assertTrue(
            mainC.contains("pickup_check_collect_pickups"),
            "Expected 'pickup_check_collect_pickups' function in generated C",
        )
        assertTrue(
            mainC.contains("pickup_spawn_pickups"),
            "Expected 'pickup_spawn_pickups' function in generated C",
        )
    }

    // =========================================================================
    // Test 2: timed pickup generates update function with duration decrement
    // =========================================================================

    @Test
    fun `timed pickup generates pickup_update function with duration decrement`() {
        val config =
            PickupSystemConfig(
                pickups =
                    listOf(
                        PickupDef(
                            id = "speed_boost",
                            effectType = "timed",
                            value = 1,
                            duration = 180,
                        )
                    ),
                maxTotalPickups = 4,
            )
        val gameIR = buildPickupGameIR(config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pickup_update_pickups"),
            "Expected 'pickup_update_pickups' function for timed pickup in generated C",
        )
        // The update function decrements timer by 1 each frame
        assertTrue(
            mainC.contains("-= 1"),
            "Expected '-= 1' decrement in pickup_update function for timed effect",
        )
        // Timer array must exist
        assertTrue(
            mainC.contains("_pickup_timer_pickups"),
            "Expected '_pickup_timer_pickups' timer array in generated C",
        )
    }

    // =========================================================================
    // Test 3: respawning pickup generates pickup_respawn_check function
    // =========================================================================

    @Test
    fun `respawning pickup generates pickup_respawn_check function`() {
        val config =
            PickupSystemConfig(
                pickups =
                    listOf(
                        PickupDef(
                            id = "coin",
                            effectType = "instant",
                            value = 10,
                            respawnFrames = 120,
                        )
                    ),
                maxTotalPickups = 8,
            )
        val gameIR = buildPickupGameIR(config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pickup_respawn_check_pickups"),
            "Expected 'pickup_respawn_check_pickups' function for respawning pickup in generated C",
        )
        // Respawn timer array must exist
        assertTrue(
            mainC.contains("_pickup_respawn_timer_pickups"),
            "Expected '_pickup_respawn_timer_pickups' array in generated C",
        )
    }

    // =========================================================================
    // Test 4: pickup var decls include active array and count global
    // =========================================================================

    @Test
    fun `pickup var decls include active array and count global`() {
        val config =
            PickupSystemConfig(
                pickups = listOf(PickupDef(id = "star", effectType = "instant", value = 100)),
                maxTotalPickups = 16,
            )
        val gameIR = buildPickupGameIR(config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_pickup_active_pickups"),
            "Expected '_pickup_active_pickups' active-slot array in global vars",
        )
        assertTrue(
            mainC.contains("_pickup_count_pickups"),
            "Expected '_pickup_count_pickups' active count global in global vars",
        )
        assertTrue(
            mainC.contains("_pickup_x_pickups"),
            "Expected '_pickup_x_pickups' position array in global vars",
        )
        assertTrue(
            mainC.contains("_pickup_y_pickups"),
            "Expected '_pickup_y_pickups' position array in global vars",
        )
        // Non-timed pickup should NOT generate timer array
        assertFalse(
            mainC.contains("_pickup_timer_pickups"),
            "Instant pickup should NOT generate '_pickup_timer_pickups' timer array",
        )
    }
}
