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

/**
 * Application-wide settings for gbkt plugin.
 *
 * Persists global settings like:
 * - Default GBDK-2020 path
 * - Default emulator configuration
 * - Plugin preferences
 *
 * For project-specific settings, see [GbktProjectSettings].
 */
@Service(Service.Level.APP)
@State(name = "GbktSettings", storages = [Storage("gbkt.xml")])
class GbktSettings : PersistentStateComponent<GbktSettings.State> {

    private var myState = State()

    data class State(
        /** Global GBDK-2020 installation path. Can be overridden per-project. */
        var gbdkPath: String? = null,

        /** Global emulator path. Can be overridden per-project. */
        var emulatorPath: String? = null,

        /** Emulator type (BGB, SameBoy, mGBA, etc.) */
        var emulatorType: String = "NONE",

        /** Whether to automatically build on save. */
        var autoBuildOnSave: Boolean = false,

        /** Whether to show SDK status in status bar. */
        var showSdkStatus: Boolean = true,

        /** Whether to show ROM size warnings. */
        var showRomSizeWarnings: Boolean = true,

        /** Custom emulator arguments. */
        var emulatorArgs: String = "",

        /** Enable auto-detection of GBDK and emulators. */
        var enableAutoDetection: Boolean = true,
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var gbdkPath: String?
        get() = myState.gbdkPath
        set(value) {
            myState.gbdkPath = value
        }

    var emulatorPath: String?
        get() = myState.emulatorPath
        set(value) {
            myState.emulatorPath = value
        }

    var emulatorType: String
        get() = myState.emulatorType
        set(value) {
            myState.emulatorType = value
        }

    var autoBuildOnSave: Boolean
        get() = myState.autoBuildOnSave
        set(value) {
            myState.autoBuildOnSave = value
        }

    var showSdkStatus: Boolean
        get() = myState.showSdkStatus
        set(value) {
            myState.showSdkStatus = value
        }

    var showRomSizeWarnings: Boolean
        get() = myState.showRomSizeWarnings
        set(value) {
            myState.showRomSizeWarnings = value
        }

    var emulatorArgs: String
        get() = myState.emulatorArgs
        set(value) {
            myState.emulatorArgs = value
        }

    var enableAutoDetection: Boolean
        get() = myState.enableAutoDetection
        set(value) {
            myState.enableAutoDetection = value
        }

    companion object {
        fun getInstance(): GbktSettings = service()
    }
}
