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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Project-level settings for gbkt plugin.
 *
 * Persists per-project settings like:
 * - Project-specific GBDK path override
 * - Build output directory
 * - ROM name
 * - Project-specific emulator override
 */
@Service(Service.Level.PROJECT)
@State(name = "GbktProjectSettings", storages = [Storage("gbkt-project.xml")])
class GbktProjectSettings : PersistentStateComponent<GbktProjectSettings.State> {

    private var myState = State()

    data class State(
        /** Whether to override global GBDK path for this project. */
        var gbdkOverride: Boolean = false,

        /** Project-specific GBDK path. */
        var gbdkPath: String? = null,

        /** Build output directory relative to project root. */
        var buildOutputDir: String = "build",

        /** Custom ROM name (without extension). */
        var romName: String? = null,

        /** Whether to override global emulator for this project. */
        var emulatorOverride: Boolean = false,

        /** Project-specific emulator path. */
        var emulatorPath: String? = null,
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var gbdkOverride: Boolean
        get() = myState.gbdkOverride
        set(value) {
            myState.gbdkOverride = value
        }

    var gbdkPath: String?
        get() = myState.gbdkPath
        set(value) {
            myState.gbdkPath = value
        }

    var buildOutputDir: String
        get() = myState.buildOutputDir
        set(value) {
            myState.buildOutputDir = value
        }

    var romName: String?
        get() = myState.romName
        set(value) {
            myState.romName = value
        }

    var emulatorOverride: Boolean
        get() = myState.emulatorOverride
        set(value) {
            myState.emulatorOverride = value
        }

    var emulatorPath: String?
        get() = myState.emulatorPath
        set(value) {
            myState.emulatorPath = value
        }

    /**
     * Gets the effective GBDK path for this project. Returns project-specific path if override is
     * enabled, otherwise falls back to global.
     */
    fun getEffectiveGbdkPath(): String? {
        return if (gbdkOverride && !gbdkPath.isNullOrBlank()) {
            gbdkPath
        } else {
            GbktSettings.getInstance().gbdkPath
        }
    }

    /**
     * Gets the effective emulator path for this project. Returns project-specific path if override
     * is enabled, otherwise falls back to global.
     */
    fun getEffectiveEmulatorPath(): String? {
        return if (emulatorOverride && !emulatorPath.isNullOrBlank()) {
            emulatorPath
        } else {
            GbktSettings.getInstance().emulatorPath
        }
    }

    companion object {
        fun getInstance(project: Project): GbktProjectSettings = project.service()
    }
}
