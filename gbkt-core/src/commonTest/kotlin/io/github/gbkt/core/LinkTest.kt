/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

class LinkTest {

    @Test
    fun `link DSL creates LinkDefinition`() {
        val game =
            gbGame("LinkTest") {
                var partnerX by u8Var(0)

                val link = link { onReceive { partnerX set 42 } }

                scene("test") {
                    enter { link.init() }
                    every.frame { link.update() }
                }

                start = scene("start") {}
            }

        assertNotNull(game.link)
        assertEquals("link", game.link?.name)
        assertTrue(game.link?.onReceiveStatements?.isNotEmpty() == true)
    }

    @Test
    fun `link generates C code`() {
        val game =
            gbGame("LinkCodegenTest") {
                var partnerX by u8Var(0)

                link { onReceive("received") {} }

                scene("test") { every.frame {} }

                start = scene("start") {}
            }

        val code = game.compileForTest()

        // Check for link state variables
        assertTrue(code.contains("_link_connected"), "Should have _link_connected")
        assertTrue(code.contains("_link_has_data"), "Should have _link_has_data")
        assertTrue(code.contains("_link_received"), "Should have _link_received")

        // Check for link functions
        assertTrue(code.contains("_link_init"), "Should have _link_init function")
        assertTrue(code.contains("_link_update"), "Should have _link_update function")
        assertTrue(code.contains("_link_send"), "Should have _link_send function")
    }

    @Test
    fun `link send generates IR`() {
        val game =
            gbGame("LinkSendTest") {
                var playerX by u8Var(80)

                val link = link {}

                scene("test") { every.frame { link.send(42) } }

                start = scene("start") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("_link_send"), "Should call _link_send")
    }
}
