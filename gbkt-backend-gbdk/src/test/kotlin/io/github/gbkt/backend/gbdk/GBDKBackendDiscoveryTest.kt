/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

import io.github.gbkt.backend.api.BackendRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for GBDK backend ServiceLoader discovery.
 *
 * These tests verify that the ServiceLoader mechanism correctly discovers the GBDK backend.
 */
class GBDKBackendDiscoveryTest {

    @BeforeTest
    fun setUp() {
        BackendRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        BackendRegistry.clear()
    }

    @Test
    fun `ServiceLoader discovers GBDK backend`() {
        val backends = BackendRegistry.discover()

        assertTrue(backends.isNotEmpty(), "At least one backend should be discovered")
        assertTrue(backends.any { it.id == "gbdk" }, "GBDK backend should be discovered")
    }

    @Test
    fun `discovered GBDK backend has correct profile`() {
        BackendRegistry.discover()
        val backend = BackendRegistry.forId("gbdk")

        assertNotNull(backend)
        assertEquals("gbc", backend.profile.id)
        assertEquals("Nintendo Game Boy Color", backend.profile.name)
    }

    @Test
    fun `can find backend by target ID`() {
        BackendRegistry.discover()
        val backend = BackendRegistry.forTarget("gbc")

        assertNotNull(backend)
        assertEquals("gbdk", backend.id)
    }

    @Test
    fun `supportedTargets includes gbc`() {
        val targets = BackendRegistry.supportedTargets()

        assertTrue(targets.contains("gbc"), "GBC should be in supported targets")
    }

    @Test
    fun `GBDK backend has correct rom extension`() {
        BackendRegistry.discover()
        val backend = BackendRegistry.forId("gbdk")

        assertNotNull(backend)
        assertEquals("gbc", backend.romExtension)
    }

    @Test
    fun `GBDK backend display name includes platform name`() {
        BackendRegistry.discover()
        val backend = BackendRegistry.forId("gbdk")

        assertNotNull(backend)
        assertTrue(backend.displayName.contains("Game Boy Color"))
    }
}
