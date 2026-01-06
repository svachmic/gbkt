/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.builder

import io.github.gbkt.core.AudioMixer
import io.github.gbkt.core.AudioMixerBuilder
import io.github.gbkt.core.ButtonState
import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.CutsceneBuilder
import io.github.gbkt.core.CutsceneHandle
import io.github.gbkt.core.DialogBuilder
import io.github.gbkt.core.DialogHandle
import io.github.gbkt.core.InputBuffer
import io.github.gbkt.core.InputBufferData
import io.github.gbkt.core.LinkBuilder
import io.github.gbkt.core.LinkHandle
import io.github.gbkt.core.Music
import io.github.gbkt.core.MusicBuilder
import io.github.gbkt.core.NavGrid
import io.github.gbkt.core.NavGridBuilder
import io.github.gbkt.core.NavGridFromTileMapBuilder
import io.github.gbkt.core.SaveDataBuilder
import io.github.gbkt.core.SaveDataHandle
import io.github.gbkt.core.SceneTransitionScope
import io.github.gbkt.core.SoundEffect
import io.github.gbkt.core.SoundEffectBuilder
import io.github.gbkt.core.TransitionDefinition
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.CameraBuilder
import io.github.gbkt.core.graphics.FrameTiming
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.graphics.PaletteBuilder
import io.github.gbkt.core.graphics.PalettePreset
import io.github.gbkt.core.graphics.ParticleSystem
import io.github.gbkt.core.graphics.ParticleSystemBuilder
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.graphics.SpriteBuilder
import io.github.gbkt.core.graphics.TileMap
import io.github.gbkt.core.graphics.TileMapBuilder
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.Transition
import io.github.gbkt.core.ui.GridMenuBuilder
import io.github.gbkt.core.ui.MenuBuilder
import io.github.gbkt.core.ui.MenuHandle

// =============================================================================
// GAME BUILDER - FEATURE-SPECIFIC DSL METHODS
// =============================================================================

/**
 * Feature-specific DSL methods for GameBuilder.
 *
 * This file contains all the feature builders: camera, transitions, assets, sprites, tilemaps,
 * audio, link cable, cutscenes, navigation, sound effects, palettes, dialogs, menus, save data,
 * particles, and input buffers.
 */

/**
 * Define an input buffer for frame-perfect input timing.
 *
 * When the button is pressed, a countdown starts. Each frame the countdown decrements. The
 * [InputBuffer.consumed] property checks if the counter > 0 and resets it (consuming the input).
 *
 * Usage:
 * ```kotlin
 * val jumpBuffer = inputBuffer(buttons.a, 6.frames)
 *
 * scene("gameplay") {
 *     every.frame {
 *         // Jump if button was pressed within last 6 frames AND grounded
 *         whenever(jumpBuffer.consumed && grounded) {
 *             jump()
 *         }
 *     }
 * }
 * ```
 *
 * @param button The button to buffer (e.g., buttons.a, buttons.b)
 * @param frames The buffer window in frames (typically 4-8 frames)
 * @return An InputBuffer reference for use in conditions
 */
fun GameBuilder.inputBuffer(button: ButtonState, frames: Int): InputBuffer {
    require(frames in 1..255) { "Buffer window must be 1-255 frames, got $frames" }
    val name = "buffer_${nextInputBufferId()}"
    val buffer = InputBuffer(name, button, frames)
    addInputBuffer(InputBufferData(name, button.buttonMask, frames))
    return buffer
}

/**
 * Define an input buffer with a custom name.
 *
 * @param name Custom name for the buffer variable
 * @param button The button to buffer
 * @param frames The buffer window in frames
 */
fun GameBuilder.inputBuffer(name: String, button: ButtonState, frames: Int): InputBuffer {
    require(frames in 1..255) { "Buffer window must be 1-255 frames, got $frames" }
    val bufferName = "buffer_$name"
    val buffer = InputBuffer(bufferName, button, frames)
    addInputBuffer(InputBufferData(bufferName, button.buttonMask, frames))
    return buffer
}

/**
 * Define an input buffer using FrameTiming syntax.
 *
 * Usage:
 * ```kotlin
 * val jumpBuffer = inputBuffer(buttons.a, 6.frames)
 * ```
 *
 * @param button The button to buffer
 * @param timing The buffer window as FrameTiming (e.g., 6.frames)
 */
fun GameBuilder.inputBuffer(button: ButtonState, timing: FrameTiming): InputBuffer {
    return inputBuffer(button, timing.count)
}

/**
 * Define an input buffer with a custom name using FrameTiming syntax.
 *
 * @param name Custom name for the buffer variable
 * @param button The button to buffer
 * @param timing The buffer window as FrameTiming
 */
fun GameBuilder.inputBuffer(name: String, button: ButtonState, timing: FrameTiming): InputBuffer {
    return inputBuffer(name, button, timing.count)
}

/**
 * Define a camera for scrolling, following, and transitions.
 *
 * Usage:
 * ```kotlin
 * val camera = camera {
 *     smoothing = 0.15f
 *     offset(0, -16)
 *     bounds(0..256, 0..256)
 * }
 *
 * scene("gameplay") {
 *     enter { camera.follow(player) }
 *     every.frame { camera.update() }
 * }
 * ```
 */
fun GameBuilder.camera(name: String = "main", init: CameraBuilder.() -> Unit = {}): Camera {
    val builder = CameraBuilder(name)
    builder.init()
    val camera = builder.build()
    setCamera(camera)
    return camera
}

/**
 * Define a reusable transition.
 *
 * Usage:
 * ```kotlin
 * val cinematicOut = transition {
 *     shake(2) and fadeOut(30.frames) then wait(10.frames)
 * }
 *
 * // Later, in a scene:
 * cinematicOut to "victory"
 * ```
 */
fun GameBuilder.transition(
    name: String? = null,
    init: SceneTransitionScope.() -> Transition,
): TransitionDefinition {
    val scope = SceneTransitionScope()
    val trans = scope.init()
    val def = TransitionDefinition(name ?: "trans_${transitionsCount()}", trans)
    addTransition(def)
    return def
}

/**
 * Configure asset directory for sprite loading.
 *
 * Usage: assets { directory = "src/main/resources/sprites" }
 */
fun GameBuilder.assets(init: AssetConfig.() -> Unit) {
    val assetConfig = AssetConfig().apply(init)
    setAssetDir(assetConfig.directory)
}

/**
 * Define a sprite using a type-safe asset reference.
 *
 * Usage: sprite(Assets.Sprites.player) { size = 8 x 16 }
 */
fun GameBuilder.sprite(
    asset: io.github.gbkt.core.assets.SpriteAsset,
    init: SpriteBuilder.() -> Unit = {},
): Sprite {
    val builder = SpriteBuilder(asset.path, nextSpriteSlot())
    builder.init()
    val sprite = builder.build()
    addSprite(sprite)
    return sprite
}

/**
 * Define a tilemap from a Tiled JSON export.
 *
 * Usage: val level = tilemap("maps/level1.json") { tileset = "tiles/platformer.png" // Optional
 * override layer = "Background" // Optional: specific layer }
 */
fun GameBuilder.tilemap(asset: String, init: TileMapBuilder.() -> Unit = {}): TileMap {
    val builder = TileMapBuilder(asset, tilemapsCount())
    builder.init()
    val map = builder.build()
    addTilemap(map)
    return map
}

/**
 * Define an audio mixer for managing channel groups.
 *
 * The mixer provides channel groups with independent volume control, priority-based playback, and
 * fade effects.
 *
 * Usage:
 * ```kotlin
 * val mixer = audioMixer {
 *     group("sfx") {
 *         channels(Channel.PULSE1, Channel.NOISE)
 *         volume = 100
 *         priority = MixerPriority.HIGH
 *     }
 *     group("music") {
 *         channels(Channel.PULSE2, Channel.WAVE)
 *         volume = 70
 *         priority = MixerPriority.LOW
 *     }
 * }
 *
 * scene("gameplay") {
 *     enter {
 *         mixer.setGroupVolume("sfx", 50)
 *     }
 *     every.frame {
 *         whenever(buttons.start.pressed) {
 *             mixer.fadeGroup("music", to = 0, over = 30.frames)
 *         }
 *     }
 * }
 * ```
 *
 * @param init Configuration block
 * @return AudioMixer for runtime volume and fade control
 */
fun GameBuilder.audioMixer(init: AudioMixerBuilder.() -> Unit): AudioMixer {
    require(getAudioMixer() == null) { "Only one audioMixer block is allowed per game" }
    val builder = AudioMixerBuilder()
    builder.init()
    val mixer = builder.build()
    setAudioMixer(mixer)
    return mixer
}

/**
 * Define link cable multiplayer communication.
 *
 * Usage:
 * ```kotlin
 * val link = link {
 *     onReceive { data ->
 *         partnerX set data
 *     }
 * }
 *
 * scene("gameplay") {
 *     every.frame {
 *         link.update()
 *         link.send(playerX)
 *     }
 * }
 * ```
 */
fun GameBuilder.link(init: LinkBuilder.() -> Unit): LinkHandle {
    require(getLink() == null) { "Only one link block is allowed per game" }
    val builder = LinkBuilder()
    builder.init()
    val definition = builder.build()
    setLink(definition)
    return LinkHandle(definition)
}

/**
 * Define a cutscene with timeline-based sequencing.
 *
 * Usage:
 * ```kotlin
 * val opening = cutscene("opening") {
 *     wait(30.frames)
 *     parallel {
 *         action { camera.fadeIn(20.frames) }
 *         action { music.play() }
 *     }
 *     action { narrator.say("Long ago...") }
 *     wait(60.frames)
 *     then { scene("gameplay") }
 * }
 *
 * scene("intro") {
 *     enter { opening.start() }
 *     every.frame { opening.update() }
 * }
 * ```
 */
fun GameBuilder.cutscene(name: String, init: CutsceneBuilder.() -> Unit): CutsceneHandle {
    val builder = CutsceneBuilder(name)
    builder.init()
    val definition = builder.build()
    addCutscene(definition)
    return CutsceneHandle(definition)
}

/**
 * Define a navigation grid for A* pathfinding.
 *
 * Usage (manual definition):
 * ```kotlin
 * val navGrid = navGrid("arena") {
 *     size = 16 x 16
 *     walkable(2..14, 2..14)
 *     blocked(8, 8)
 * }
 * ```
 *
 * Usage (from tilemap):
 * ```kotlin
 * val navGrid = navGrid(from = dungeonMap) {
 *     blockedTiles(0, 1, 2)  // Wall tile indices
 * }
 * ```
 */
fun GameBuilder.navGrid(name: String, init: NavGridBuilder.() -> Unit): NavGrid {
    val builder = NavGridBuilder(name)
    builder.init()
    val grid = builder.build()
    addNavGrid(grid)
    return grid
}

/** Define a navigation grid derived from a TileMap. */
fun GameBuilder.navGrid(from: TileMap, init: NavGridFromTileMapBuilder.() -> Unit = {}): NavGrid {
    val builder = NavGridFromTileMapBuilder("${from.name}_nav", from)
    builder.init()
    val grid = builder.build()
    addNavGrid(grid)
    return grid
}

/**
 * Define a sound effect.
 *
 * Usage with preset: val jump = soundEffect("jump") { preset = SoundPreset.JUMP }
 *
 * Usage with custom settings: val laser = soundEffect("laser") { channel = Channel.PULSE1 sweep {
 * time = 3; direction = SweepDirection.DECREASE; shift = 4 } envelope { volume = 15; pace = 2 }
 * frequency = 1900 }
 */
fun GameBuilder.soundEffect(name: String, init: SoundEffectBuilder.() -> Unit = {}): SoundEffect {
    val builder = SoundEffectBuilder(name)
    builder.init()
    val sfx = builder.build()
    addSoundEffect(sfx)
    return sfx
}

/**
 * Define background music from a hUGETracker .uge file.
 *
 * Usage: val bgm = music("music/forest.uge")
 *
 * scene("gameplay") { enter { bgm.play() } }
 *
 * Note: The .uge file must be exported to C using hUGETracker's "Export to C" feature. Place the
 * generated .c file alongside your source.
 */
fun GameBuilder.music(asset: String, init: MusicBuilder.() -> Unit = {}): Music {
    val builder = MusicBuilder(asset, musicCount())
    builder.init()
    val m = builder.build()
    addMusic(m)
    return m
}

/**
 * Define a color palette for GBC mode.
 *
 * Usage: val playerPalette = palette("player") { colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000) }
 *
 * // Or with individual color setting: val enemyPalette = palette("enemy") { color(0, 0xFF, 0xFF,
 * 0xFF) // index, R, G, B color(1, 0xFF, 0x00, 0x00) color(2, 0x88, 0x00, 0x00) color(3, 0x00,
 * 0x00, 0x00) }
 */
fun GameBuilder.palette(name: String, init: PaletteBuilder.() -> Unit): Palette {
    val builder = PaletteBuilder(name)
    builder.init()
    val gbcPalette = builder.build(this)
    addPalette(gbcPalette)
    return Palette(name, gbcPalette.colors, assignedSlot = gbcPalette.slot, type = gbcPalette.type)
}

/** Define a palette with preset colors. */
fun GameBuilder.palette(name: String, preset: PalettePreset): Palette {
    val colors = preset.colors
    val slot = allocatePaletteSlot(PaletteType.SPRITE)
    val gbcPalette = GBCPalette(name, colors, slot, PaletteType.SPRITE)
    addPalette(gbcPalette)
    return Palette(name, colors, assignedSlot = slot, type = PaletteType.SPRITE)
}

/**
 * Define a named dialog configuration.
 *
 * Usage:
 * ```
 * val shopkeeper = dialog("shopkeeper") {
 *     portrait("shopkeeper.png")
 *     speaker = "Merchant"
 *     textSpeed = 3
 *     box {
 *         position(0, 10)
 *         size = 20 x 6
 *         border = BorderStyle.ROUNDED
 *     }
 * }
 *
 * scene("shop") {
 *     enter {
 *         shopkeeper.say("Welcome to my shop!")
 *         shopkeeper.choice("Buy", "Sell", "Leave") { selected ->
 *             whenever(selected isEqualTo 0) { /* buy logic */ }
 *         }
 *     }
 * }
 * ```
 */
fun GameBuilder.dialog(name: String, init: DialogBuilder.() -> Unit): DialogHandle {
    val builder = DialogBuilder(name)
    builder.init()
    val definition = builder.build()
    addDialog(definition)
    return DialogHandle(definition)
}

/**
 * Define a vertical menu (title screens, pause menus, settings).
 *
 * Usage:
 * ```
 * val mainMenu = menu("main") {
 *     style {
 *         position(5, 8)
 *         cursor = ">"
 *         border = BorderStyle.ROUNDED
 *     }
 *
 *     item("START GAME") { scene("gameplay") }
 *     item("OPTIONS") { open(optionsMenu) }
 * }
 *
 * scene("title") {
 *     enter { mainMenu.show() }
 *     every.frame { mainMenu.tick() }
 * }
 * ```
 */
fun GameBuilder.menu(name: String, init: MenuBuilder.() -> Unit): MenuHandle {
    val builder = MenuBuilder(name)
    builder.init()
    val definition = builder.build()
    addMenu(definition)
    return MenuHandle(definition)
}

/**
 * Define a grid menu (inventories, item selection grids).
 *
 * Usage:
 * ```
 * val inventory = gridMenu("inventory") {
 *     grid(4, 3)  // 4 columns, 3 rows
 *     style {
 *         position(2, 2)
 *         cellSize = 2 x 2
 *     }
 *     itemsFrom(inventorySlots) { slot, index ->
 *         onSelect { useItem(index) }
 *     }
 * }
 * ```
 */
fun GameBuilder.gridMenu(name: String, init: GridMenuBuilder.() -> Unit): MenuHandle {
    val builder = GridMenuBuilder(name)
    builder.init()
    val definition = builder.build()
    addMenu(definition)
    return MenuHandle(definition)
}

/**
 * Define save data structure for SRAM persistence.
 *
 * Usage:
 * ```
 * val save = saveData("mygame") {
 *     var score by u16Field()
 *     var level by u8Field(default = 1)
 *     var lives by u8Field(default = 3)
 *     var flags by flagsField()
 *
 *     config {
 *         slots = 3
 *         checksum = Checksum.CRC8
 *         magic = "GBKT"
 *     }
 * }
 * ```
 *
 * Then in scenes:
 * ```
 * scene("gameplay") {
 *     enter { save.load(slot = 0) }
 *     every.frame {
 *         save.score += 10
 *         whenever(buttons.start.pressed) { save.save() }
 *     }
 * }
 * ```
 */
fun GameBuilder.saveData(name: String, init: SaveDataBuilder.() -> Unit): SaveDataHandle {
    require(getSaveData() == null) { "Only one saveData block is allowed per game" }

    val builder = SaveDataBuilder(name)
    builder.init()
    val handle = builder.buildHandle()
    setSaveData(handle.data)

    // Auto-upgrade cartridge to support battery-backed SRAM if needed
    val config = getConfig()
    if (!config.cartridge.name.contains("BATTERY")) {
        setConfig(
            config.copy(
                cartridge = Cartridge.MBC5_RAM_BATTERY,
                ramBanks = maxOf(config.ramBanks, 1),
            )
        )
    }

    return handle
}

/**
 * Define a particle system for visual effects.
 *
 * Particle systems wrap the Pool infrastructure with automatic lifetime management. Particles
 * automatically despawn after their lifetime expires.
 *
 * Usage:
 * ```kotlin
 * val sparks = particles("spark") {
 *     sprite(SpriteAsset("spark.png")) { size = 2 x 2 }
 *     count = 8
 *     lifetime = 15.frames
 *
 *     velocity(0, -1)  // Move upward
 *
 *     onSpawn {
 *         // Random horizontal velocity
 *     }
 *
 *     onFrame {
 *         x += velX
 *         y += velY
 *     }
 * }
 *
 * // In scene:
 * sparks.emit(player.x, player.y)        // Single particle
 * sparks.burst(enemy.x, enemy.y, count = 4)  // Multiple particles
 * ```
 *
 * @param name Unique name for the particle system
 * @param init Configuration block
 * @return ParticleSystem for emitting and managing particles
 */
fun GameBuilder.particles(name: String, init: ParticleSystemBuilder.() -> Unit): ParticleSystem {
    val builder = ParticleSystemBuilder(name, this)
    builder.init()

    // Allocate OAM slots for particle sprites
    val oamStartSlot = nextSpriteSlot()
    repeat(builder.count - 1) { nextSpriteSlot() } // Reserve additional slots

    val (pool, particleSystem) = builder.build(oamStartSlot)

    // Register the underlying pool (reuses pool code generation)
    registerPool(pool)
    registerParticleSystem(particleSystem)

    return particleSystem
}
