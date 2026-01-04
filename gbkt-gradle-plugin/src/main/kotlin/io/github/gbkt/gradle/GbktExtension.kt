/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration extension for the gbkt Gradle plugin.
 *
 * Usage in build.gradle.kts:
 * ```
 * gbkt {
 *     game("sample.RunnerGameKt::runnerGame")
 *     assets("src/main/resources/sprites")
 *     outputName.set("runner")
 *
 *     optimization {
 *         enabled.set(true)
 *         verbose.set(false)
 *     }
 * }
 * ```
 */
abstract class GbktExtension @Inject constructor(objects: ObjectFactory) {

    /** Asset optimization settings. */
    val optimization: OptimizationExtension = objects.newInstance(OptimizationExtension::class.java)

    /** Emulator configuration for running built ROMs. */
    val emulator: EmulatorExtension = objects.newInstance(EmulatorExtension::class.java)

    /** Web export configuration for browser deployment. */
    val web: WebExportExtension = objects.newInstance(WebExportExtension::class.java)

    /** Output configuration for generated files. */
    val output: OutputExtension = objects.newInstance(OutputExtension::class.java)

    /** Type-safe asset generation settings. */
    val generateAssets: GenerateAssetsExtension =
        objects.newInstance(GenerateAssetsExtension::class.java)

    /**
     * Path to GBDK installation directory. If not set, will auto-detect from GBDK_HOME environment
     * variable or common installation paths.
     */
    abstract val gbdkHome: Property<String>

    /**
     * Game definition in format "package.ClassName::propertyName".
     *
     * Example: "sample.RunnerGameKt::runnerGame"
     *
     * The class should contain a top-level val of type Game:
     * ```
     * val runnerGame = gbGame("Runner") { ... }
     * ```
     */
    abstract val game: Property<String>

    /** Directory containing sprite PNG assets. Defaults to src/main/resources/assets if not set. */
    abstract val assetDirectory: DirectoryProperty

    /** Output ROM file name (without .gb extension). Defaults to "game". */
    abstract val outputName: Property<String>

    /** Additional compiler flags to pass to GBDK's lcc. Example: ["-DDEBUG", "-Wf--verbose"] */
    abstract val compilerFlags: ListProperty<String>

    /** Generate debug files (.map, .sym) alongside the ROM. Defaults to true. */
    abstract val debug: Property<Boolean>

    /**
     * GBC (Game Boy Color) mode. Options:
     * - "DISABLED" (default): Classic DMG grayscale only
     * - "COMPATIBLE": Works on both DMG and GBC (uses -Wm-yc flag)
     * - "ONLY": GBC exclusive, won't run on DMG (uses -Wm-yC flag)
     */
    abstract val gbcMode: Property<String>

    /**
     * Convenience method to set game definition.
     *
     * @param spec Format: "package.ClassName::propertyName"
     */
    fun game(spec: String) {
        require("::" in spec) {
            "Game spec must be in format 'package.ClassName::propertyName', got: $spec"
        }
        game.set(spec)
    }

    /**
     * Convenience method to set asset directory.
     *
     * @param path Path to assets directory (relative or absolute)
     */
    fun assets(path: String) {
        assetDirectory.set(File(path))
    }

    /**
     * Configure asset optimization settings.
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     optimization {
     *         enabled.set(true)
     *         verbose.set(true)
     *         detectDuplicates.set(true)
     *         detectEmpty.set(true)
     *         detectLowEntropy.set(true)
     *         lowEntropyThreshold.set(0.5f)
     *     }
     * }
     * ```
     */
    fun optimization(action: Action<OptimizationExtension>) {
        action.execute(optimization)
    }

    /**
     * Configure emulator settings.
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     emulator {
     *         path.set("/usr/local/bin/mgba")
     *         args.set(listOf("-s", "4"))  // 4x window scale
     *     }
     * }
     * ```
     */
    fun emulator(action: Action<EmulatorExtension>) {
        action.execute(emulator)
    }

    /**
     * Configure web export settings.
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     web {
     *         title.set("My Game Boy Game")
     *         enableControls.set(true)
     *     }
     * }
     * ```
     */
    fun web(action: Action<WebExportExtension>) {
        action.execute(web)
    }

    /**
     * Configure output settings for generated files.
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     output {
     *         keepGeneratedC.set(true)                              // Enable copying
     *         cOutputDir.set(layout.projectDirectory.dir("gen-c"))  // Custom location
     *         keepSourceMaps.set(true)                              // Include .gbkt.map
     *     }
     * }
     * ```
     */
    fun output(action: Action<OutputExtension>) {
        action.execute(output)
    }

    /**
     * Configure type-safe asset generation.
     *
     * When enabled, generates a Kotlin `Assets` object with type-safe references to all assets in
     * the asset directory.
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     generateAssets {
     *         enabled.set(true)
     *         packageName.set("com.example.mygame")
     *     }
     * }
     * ```
     *
     * Generated code example:
     * ```kotlin
     * object Assets : AssetRegistry {
     *     object Sprites {
     *         val player = SpriteAsset("player.png")
     *     }
     * }
     * ```
     *
     * Then use in your game:
     * ```kotlin
     * sprite(Assets.Sprites.player) { size = 8 x 16 }
     * ```
     */
    fun generateAssets(action: Action<GenerateAssetsExtension>) {
        action.execute(generateAssets)
    }
}

/**
 * Asset optimization settings for the gbkt plugin.
 *
 * Controls the asset analysis that runs during code generation to detect optimization opportunities
 * like duplicate tiles, empty tiles, and palette waste.
 */
abstract class OptimizationExtension @Inject constructor() {

    /** Enable asset optimization analysis during build. Default: true */
    abstract val enabled: Property<Boolean>

    /** Show per-asset details in output. Default: false */
    abstract val verbose: Property<Boolean>

    /** Suppress output when all assets are optimal. Default: true */
    abstract val quietWhenOptimal: Property<Boolean>

    /** Enable duplicate tile detection. Default: true */
    abstract val detectDuplicates: Property<Boolean>

    /** Enable empty tile detection. Default: true */
    abstract val detectEmpty: Property<Boolean>

    /** Enable low-entropy tile detection. Default: true */
    abstract val detectLowEntropy: Property<Boolean>

    /** Enable palette waste detection. Default: true */
    abstract val detectPaletteWaste: Property<Boolean>

    /**
     * Threshold for low-entropy detection (0.0 - 2.0). Lower values = more tiles flagged as
     * low-entropy. Default: 0.5
     */
    abstract val lowEntropyThreshold: Property<Float>

    /** Use ANSI colors in console output. Default: auto-detected */
    abstract val useColor: Property<Boolean>

    /** Use Unicode characters in console output. Default: auto-detected */
    abstract val useUnicode: Property<Boolean>
}

/**
 * Emulator configuration for running built ROMs.
 *
 * Supports mGBA with cross-platform path detection and live reload.
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     emulator {
 *         path.set("/usr/local/bin/mgba")  // or auto-detect
 *         args.set(listOf("-s", "4"))       // optional: scale 4x
 *         liveReload.set(true)              // enable live reload (default)
 *     }
 * }
 * ```
 */
abstract class EmulatorExtension @Inject constructor() {

    /**
     * Path to the emulator executable. If not set, will auto-detect mGBA from common installation
     * paths.
     *
     * Common paths checked:
     * - macOS: /Applications/mGBA.app, /usr/local/bin/mgba, ~/Applications/mGBA.app
     * - Linux: /usr/bin/mgba, /usr/local/bin/mgba, ~/.local/bin/mgba
     * - Windows: C:\Program Files\mGBA\mGBA.exe, C:\Program Files (x86)\mGBA\mGBA.exe
     */
    abstract val path: Property<String>

    /**
     * Additional command-line arguments to pass to the emulator.
     *
     * Useful mGBA options:
     * - "-s", "N" : Window scale factor (e.g., "-s", "4" for 4x size)
     * - "-f" : Start in fullscreen
     * - "-C" : Config option (e.g., "-C", "audio.volume=0.5")
     */
    abstract val args: ListProperty<String>

    /**
     * Enable live reload functionality. When enabled, the emulator will load a Lua script that
     * monitors the ROM file for changes and automatically reloads when rebuilt.
     *
     * Requires mGBA with Lua scripting support. Default: true
     */
    abstract val liveReload: Property<Boolean>

    /**
     * Custom path to the live-reload Lua script. If not set, uses the bundled script at
     * scripts/live-reload.lua.
     *
     * The script should use mGBA's Lua scripting API to:
     * 1. Monitor the ROM file for modifications
     * 2. Call emu:loadFile() when changes are detected
     */
    abstract val liveReloadScript: Property<String>
}

/**
 * Web export configuration for browser deployment.
 *
 * Configures the webExport task that generates an HTML page with EmulatorJS to run the Game Boy ROM
 * in a browser.
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     web {
 *         title.set("My Game Boy Game")
 *         enableControls.set(true)
 *         emulatorJsVersion.set("stable")
 *     }
 * }
 * ```
 */
abstract class WebExportExtension @Inject constructor() {

    /** Title for the HTML page. Defaults to the game name from outputName. */
    abstract val title: Property<String>

    /** Enable EmulatorJS controls overlay. Default: true */
    abstract val enableControls: Property<Boolean>

    /**
     * EmulatorJS CDN version to use. Options: "stable", "latest", or a specific version number.
     * Default: "stable"
     */
    abstract val emulatorJsVersion: Property<String>
}

/**
 * Output configuration for generated files.
 *
 * Configures where generated C files are copied for inspection and debugging.
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     output {
 *         keepGeneratedC.set(true)                              // Enable copying
 *         cOutputDir.set(layout.projectDirectory.dir("gen-c"))  // Custom location
 *         keepSourceMaps.set(true)                              // Include .gbkt.map
 *     }
 * }
 * ```
 */
abstract class OutputExtension @Inject constructor() {

    /**
     * Keep generated C files in a user-accessible location. When enabled, the generated .c file
     * will be copied to [cOutputDir]. Default: false
     */
    abstract val keepGeneratedC: Property<Boolean>

    /** Directory to copy generated C files to. Default: build/gbkt/src/ */
    abstract val cOutputDir: DirectoryProperty

    /**
     * Keep source map files (.gbkt.map) alongside C files. Source maps allow mapping from generated
     * C code back to Kotlin DSL. Default: true (when keepGeneratedC is true)
     */
    abstract val keepSourceMaps: Property<Boolean>
}

/**
 * Configuration for type-safe asset generation.
 *
 * When enabled, generates a Kotlin `Assets` object that provides compile-time checked references to
 * all asset files.
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     generateAssets {
 *         enabled.set(true)
 *         packageName.set("com.example.mygame")
 *         objectName.set("Assets")  // optional, default is "Assets"
 *     }
 * }
 * ```
 */
abstract class GenerateAssetsExtension @Inject constructor() {

    /** Enable type-safe asset generation. Default: false */
    abstract val enabled: Property<Boolean>

    /**
     * Package name for the generated Assets class. Required when enabled is true.
     *
     * Example: "com.example.mygame"
     */
    abstract val packageName: Property<String>

    /** Name of the generated object. Default: "Assets" */
    abstract val objectName: Property<String>
}
