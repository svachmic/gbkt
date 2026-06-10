/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for GbktSettings State (application-level settings). */
class GbktSettingsTest {

    private lateinit var state: GbktSettings.State

    @Before
    fun setUp() {
        state = GbktSettings.State()
    }

    @Test
    fun `default state has null gbdk path`() {
        assertNull(state.gbdkPath)
    }

    @Test
    fun `default state has null emulator path`() {
        assertNull(state.emulatorPath)
    }

    @Test
    fun `default state has NONE emulator type`() {
        assertEquals("NONE", state.emulatorType)
    }

    @Test
    fun `default state has auto build disabled`() {
        assertFalse(state.autoBuildOnSave)
    }

    @Test
    fun `default state has sdk status enabled`() {
        assertTrue(state.showSdkStatus)
    }

    @Test
    fun `default state has rom size warnings enabled`() {
        assertTrue(state.showRomSizeWarnings)
    }

    @Test
    fun `default state has auto detection enabled`() {
        assertTrue(state.enableAutoDetection)
    }

    @Test
    fun `default state has empty emulator args`() {
        assertEquals("", state.emulatorArgs)
    }

    @Test
    fun `state can be modified`() {
        state.gbdkPath = "/path/to/gbdk"
        state.emulatorType = "BGB"
        state.autoBuildOnSave = true

        assertEquals("/path/to/gbdk", state.gbdkPath)
        assertEquals("BGB", state.emulatorType)
        assertTrue(state.autoBuildOnSave)
    }

    @Test
    fun `state copy preserves values`() {
        state.gbdkPath = "/path/to/gbdk"
        state.emulatorType = "SameBoy"
        state.emulatorArgs = "--debug"

        val copy = state.copy()

        assertEquals(state.gbdkPath, copy.gbdkPath)
        assertEquals(state.emulatorType, copy.emulatorType)
        assertEquals(state.emulatorArgs, copy.emulatorArgs)
    }
}

/** Tests for GbktProjectSettings State (project-level settings). */
class GbktProjectSettingsTest {

    private lateinit var state: GbktProjectSettings.State

    @Before
    fun setUp() {
        state = GbktProjectSettings.State()
    }

    @Test
    fun `default state has gbdk override disabled`() {
        assertFalse(state.gbdkOverride)
    }

    @Test
    fun `default state has null gbdk path`() {
        assertNull(state.gbdkPath)
    }

    @Test
    fun `default state has build as build output dir`() {
        assertEquals("build", state.buildOutputDir)
    }

    @Test
    fun `default state has null rom name`() {
        assertNull(state.romName)
    }

    @Test
    fun `default state has emulator override disabled`() {
        assertFalse(state.emulatorOverride)
    }

    @Test
    fun `default state has null emulator path`() {
        assertNull(state.emulatorPath)
    }

    @Test
    fun `state can be modified`() {
        state.gbdkOverride = true
        state.gbdkPath = "/project/gbdk"
        state.buildOutputDir = "output"
        state.romName = "MyGame"

        assertTrue(state.gbdkOverride)
        assertEquals("/project/gbdk", state.gbdkPath)
        assertEquals("output", state.buildOutputDir)
        assertEquals("MyGame", state.romName)
    }

    @Test
    fun `state copy preserves values`() {
        state.gbdkOverride = true
        state.gbdkPath = "/project/gbdk"
        state.buildOutputDir = "dist"

        val copy = state.copy()

        assertEquals(state.gbdkOverride, copy.gbdkOverride)
        assertEquals(state.gbdkPath, copy.gbdkPath)
        assertEquals(state.buildOutputDir, copy.buildOutputDir)
    }
}
