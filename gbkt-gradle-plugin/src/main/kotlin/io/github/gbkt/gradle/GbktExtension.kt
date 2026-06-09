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

    /** Sprite pipeline settings (tRNS routing, strict mode). */
    val sprites: SpritesExtension = objects.newInstance(SpritesExtension::class.java)

    /**
     * Path to GBDK installation directory. If not set, will auto-detect from GBDK_HOME environment
     * variable or common installation paths.
     */
    abstract val gbdkHome: Property<String>

    /**
     * Target platform for code generation.
     *
     * Available targets depend on backends on the classpath. With gbkt-backend-gbdk:
     * - "gbc" (default): Game Boy Color
     * - "gb": Original Game Boy (DMG)
     *
     * The backend is discovered via ServiceLoader. Make sure gbkt-backend-gbdk is in your
     * dependencies for GB/GBC support.
     */
    abstract val target: Property<String>

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
     * Compile-time locale selection for building language-specific ROMs.
     *
     * When set, the build uses `res/strings/{locale}.po` as the localization source and appends
     * `_{locale}` to the output ROM name (e.g., "labyrinth_en.gb", "labyrinth_cs.gb").
     *
     * Can be overridden on the command line via `-Pgbkt.locale=cs`.
     *
     * Default: "en"
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     locale.set("en")  // build labyrinth_en.gb
     * }
     * ```
     *
     * Command-line override:
     * ```
     * ./gradlew buildRom -Pgbkt.locale=cs  # builds labyrinth_cs.gb
     * ```
     */
    abstract val locale: Property<String>

    /**
     * Skip backend validation before code generation. Default: false.
     *
     * When true, validation errors are printed as warnings but don't block the build. Useful for
     * games with known validation issues (e.g., OAM limits that are managed at runtime, missing
     * placeholder assets) that still generate valid C code.
     */
    abstract val skipValidation: Property<Boolean>

    /**
     * Print the budget report during every `generateC` execution. Default: true.
     *
     * The budget report is produced by the ten-pass analysis pipeline and shows ROM bank usage,
     * VRAM tile budget, OAM sprite count, and WRAM/HRAM consumption.
     *
     * Set to false to suppress the budget report output:
     * ```kotlin
     * gbkt {
     *     budgetReport.set(false)
     * }
     * ```
     *
     * Note: The budget report is printed inline during `generateC` by `generate()`. The
     * standalone `budgetReport` task remains independently callable regardless of this flag.
     */
    abstract val budgetReport: Property<Boolean>

    /**
     * Number of RAM banks for the cartridge.
     *
     * Maps to the GBDK linker flag `-Wl-ya<N>`. Common values:
     * - 0 (default): No external RAM
     * - 1: 8KB SRAM
     * - 4: 32KB SRAM (used by MBC5+RAM+Battery)
     *
     * Only meaningful for cartridge types with RAM (MBC1+RAM, MBC5+RAM+BATTERY, etc.).
     *
     * @deprecated Set `ramBanks` in the DSL `config { ramBanks = N }` block instead.
     * The DSL value flows through `gbkt-build.properties` and takes precedence over this
     * Gradle extension property. This property remains as a backward-compatibility fallback
     * for builds that have not yet migrated to the typed `config { cartridge(Cartridge.X) }` DSL form.
     */
    abstract val ramBanks: Property<Int>

    /**
     * Directory containing binary resource files (tilemaps, tilesets, etc.) that are referenced by
     * generated C code via INCBIN directives.
     *
     * These files are copied alongside the generated C source before GBDK compilation. The
     * directory structure is preserved (e.g., `res/tilemaps/floor1.tilemap` remains at that
     * relative path).
     *
     * If not set, no resource files are copied.
     */
    abstract val resourceDirectory: DirectoryProperty

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
     *         scale.set(4)                // 4x window scale (default)
     *         headless.set(false)         // show window (default)
     *         // Optional: use external emulator instead of embedded Coffee-GB
     *         externalEmulator.set("/usr/local/bin/mgba")
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

    /**
     * Configure sprite pipeline settings.
     *
     * Usage:
     * ```kotlin
     * gbkt {
     *     sprites {
     *         strictTransparency.set(true)
     *     }
     * }
     * ```
     *
     * When [SpritesExtension.strictTransparency] is true, any sprite PNG that declares its
     * transparent color at a non-zero palette index will fail the build with a
     * [org.gradle.api.GradleException] naming the sprite file and the index. When false (default),
     * the framework auto-corrects by pre-permuting the palette before handing to png2asset and
     * emits a D-06 WARNING.
     */
    fun sprites(action: Action<SpritesExtension>) {
        action.execute(sprites)
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
 * Uses the embedded Coffee-GB emulator by default. An external emulator can optionally be
 * configured as a fallback for users who prefer mGBA or other standalone emulators.
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     emulator {
 *         scale.set(4)              // 4x window scale (640x576), default
 *         headless.set(false)       // show window, default
 *         maxFrames.set(600)        // for headless/test mode, default 600
 *         // Optional: override with external emulator (e.g., mGBA)
 *         externalEmulator.set("/usr/local/bin/mgba")
 *     }
 * }
 * ```
 */
abstract class EmulatorExtension @Inject constructor() {

    /** Scale factor for the emulator display window. Default: 4 (640x576). */
    abstract val scale: Property<Int>

    /** Run in headless mode (no display window). Default: false. */
    abstract val headless: Property<Boolean>

    /** Maximum frames to run in test/headless mode. Default: 600 (10 seconds at 60fps). */
    abstract val maxFrames: Property<Int>

    /**
     * Optional path to an external emulator executable (e.g., mGBA).
     *
     * When set, [RunEmulatorTask] bypasses the embedded Coffee-GB emulator and launches the
     * external emulator with the ROM path as argument. This is useful for users who prefer a
     * different emulator for interactive play.
     *
     * When unset (default), the embedded Coffee-GB emulator is used. No external emulator
     * installation is required.
     *
     * Example:
     * ```kotlin
     * gbkt {
     *     emulator {
     *         externalEmulator.set("/Applications/mGBA.app/Contents/MacOS/mGBA")
     *     }
     * }
     * ```
     */
    abstract val externalEmulator: Property<String>
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

/**
 * Sprite pipeline settings for the gbkt plugin.
 *
 * Controls tRNS transparency routing behaviour for indexed sprite PNGs. When a sprite PNG
 * declares its transparent color at a non-zero palette index, the framework can either
 * auto-correct silently (default) or hard-fail the build (strict mode).
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     sprites {
 *         strictTransparency.set(true) // hard-fail on non-zero tRNS index (default: false)
 *     }
 * }
 * ```
 *
 * Phase 13.6 REQ-4 / D-01 / D-02: SpritesExtension mirrors the existing sub-extension
 * pattern (OptimizationExtension, EmulatorExtension, etc.) so the DSL surface is
 * property-name-inferred (no magic strings).
 */
abstract class SpritesExtension @Inject constructor() {

    /**
     * Enable strict transparency routing mode.
     *
     * When `true`, any indexed sprite PNG whose tRNS chunk declares a transparent color at a
     * non-zero palette index will fail the [io.github.gbkt.gradle.tasks.ConvertSpritesTask] with
     * a [org.gradle.api.GradleException] naming the sprite file and the non-zero index.
     *
     * When `false` (default), the framework auto-corrects by pre-permuting the palette so the
     * transparent color lands at index 0 before handing to png2asset, and emits a build WARNING
     * (D-06) naming the auto-corrected sprite and index.
     *
     * Default: `false`
     */
    abstract val strictTransparency: Property<Boolean>
}
