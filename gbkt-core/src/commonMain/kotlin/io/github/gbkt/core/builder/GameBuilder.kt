/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.builder

import io.github.gbkt.core.AudioMixer
import io.github.gbkt.core.CompiledMapData
import io.github.gbkt.core.CompiledTileData
import io.github.gbkt.core.CutsceneDefinition
import io.github.gbkt.core.DialogDefinition
import io.github.gbkt.core.Game
import io.github.gbkt.core.GameConfig
import io.github.gbkt.core.InputBufferData
import io.github.gbkt.core.LinkDefinition
import io.github.gbkt.core.Music
import io.github.gbkt.core.NavGrid
import io.github.gbkt.core.PhysicsWorld
import io.github.gbkt.core.SaveData
import io.github.gbkt.core.Scene
import io.github.gbkt.core.SceneBuilder
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.SoundEffect
import io.github.gbkt.core.StateMachine
import io.github.gbkt.core.TagRef
import io.github.gbkt.core.TransitionDefinition
import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.EntityRegistry
import io.github.gbkt.core.entity.Pool
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.ParticleSystem
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.graphics.TileMap
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.services.DefaultGameServices
import io.github.gbkt.core.services.GameServices
import io.github.gbkt.core.ui.MenuDefinition

// =============================================================================
// GAME BUILDER
// =============================================================================

/**
 * Builder for defining a complete Game Boy game.
 *
 * Optionally accepts injectable [GameServices] for testing and mocking:
 * ```kotlin
 * // Production usage (default services)
 * game("MyGame") { ... }
 *
 * // Test usage (mock services)
 * val mockServices = TestGameServices()
 * GameBuilder("test", mockServices).apply { ... }
 * ```
 *
 * @param name The name of the game (used for ROM filename)
 * @param services Optional game services for dependency injection
 */
@GbktDsl
class GameBuilder(
    private val name: String,
    /** Injected services for DI testing and mocking. */
    val services: GameServices = DefaultGameServices(),
) : GameScope() {
    private var config = GameConfig()
    private val sprites = mutableListOf<Sprite>()
    private val tilemaps = mutableListOf<TileMap>()
    private val soundEffects = mutableListOf<SoundEffect>()
    private val musicList = mutableListOf<Music>()
    private val scenes = mutableMapOf<String, Scene>()
    private var _startScene: String = ""
    private var _assetDir: String? = null
    private val _tileData = mutableListOf<CompiledTileData>()
    private val _mapData = mutableListOf<CompiledMapData>()
    private val _palettes = mutableListOf<GBCPalette>()
    private val _stateMachines = mutableListOf<StateMachine>()
    private var _nextSpritePaletteSlot = 0
    private var _nextBkgPaletteSlot = 0
    private var _saveData: SaveData? = null

    // Dialog system
    private val _dialogs = mutableListOf<DialogDefinition>()

    // Menu system
    private val _menus = mutableListOf<MenuDefinition>()

    // Entity tracking
    private val _entities = mutableListOf<Entity>()
    internal val entityRegistry = EntityRegistry()
    private var _nextSpriteSlot = 0

    // Pool tracking
    private val _pools = mutableListOf<Pool>()

    // Camera
    private var _camera: Camera? = null

    // Physics
    private var _physicsWorld: PhysicsWorld? = null

    // Transitions
    private val _transitions = mutableListOf<TransitionDefinition>()

    // Navigation grids for pathfinding
    private val _navGrids = mutableListOf<NavGrid>()

    // Input buffers for frame-perfect input timing
    private val _inputBuffers = mutableListOf<InputBufferData>()
    private var _nextInputBufferId = 0

    // Audio mixer for channel groups
    private var _audioMixer: AudioMixer? = null

    // Link cable for multiplayer
    private var _link: LinkDefinition? = null

    // Cutscene tracking
    private val _cutscenes = mutableListOf<CutsceneDefinition>()

    // Tag registry for type-safe tag references
    private val _tags = mutableMapOf<String, TagRef>()

    // Particle system tracking
    private val _particleSystems = mutableListOf<ParticleSystem>()

    /**
     * Create a type-safe tag reference.
     *
     * Usage:
     * ```kotlin
     * val enemyTag = tag("enemy")
     * val playerTag = tag("player")
     *
     * val enemy by entity {
     *     tag(enemyTag)
     * }
     *
     * whenever(player collidesWithAny enemyTag) { ... }
     * ```
     */
    fun tag(name: String): TagRef {
        return _tags.getOrPut(name) { TagRef(name) }
    }

    /** Configure hardware settings */
    fun config(init: ConfigBuilder.() -> Unit) {
        config = ConfigBuilder().apply(init).build()
    }

    /**
     * Define a scene.
     *
     * Returns a [SceneRef] for type-safe scene transitions.
     *
     * Usage:
     * ```kotlin
     * val titleScene = scene("title") { ... }
     * val gameplayScene = scene("gameplay") { ... }
     *
     * start = titleScene
     * ```
     */
    fun scene(name: String, init: SceneBuilder.() -> Unit): SceneRef {
        val builder = SceneBuilder(name, this)
        builder.init()
        scenes[name] = builder.build()
        return SceneRef(name)
    }

    /**
     * The scene to start on.
     *
     * Usage:
     * ```kotlin
     * val titleScene = scene("title") { ... }
     * start = titleScene
     * ```
     */
    var start: SceneRef
        get() = SceneRef(_startScene)
        set(value) {
            _startScene = value.name
        }

    /** Add pre-compiled tile data (for JVM asset pipeline) */
    fun addTileData(data: CompiledTileData) {
        _tileData.add(data)
    }

    /** Add pre-compiled map data (for JVM asset pipeline) */
    fun addMapData(data: CompiledMapData) {
        _mapData.add(data)
    }

    internal fun allocatePaletteSlot(type: PaletteType): Int {
        return when (type) {
            PaletteType.SPRITE -> {
                require(_nextSpritePaletteSlot < 8) { "Maximum 8 sprite palettes allowed" }
                _nextSpritePaletteSlot++ // Returns current value, then increments
            }
            PaletteType.BACKGROUND -> {
                require(_nextBkgPaletteSlot < 8) { "Maximum 8 background palettes allowed" }
                _nextBkgPaletteSlot++ // Returns current value, then increments
            }
        }
    }

    /** Register a state machine with the game */
    internal fun registerStateMachine(machine: StateMachine) {
        _stateMachines.add(machine)
    }

    /**
     * Register an entity with the game.
     *
     * Also registers with the [services.entities] for DI access.
     */
    internal fun registerEntity(entity: Entity) {
        _entities.add(entity)
        entityRegistry.register(entity)
        services.entities.registerEntity(entity)
    }

    /**
     * Register a sprite from an entity.
     *
     * Also registers with the [services.sprites] for DI access.
     */
    internal fun registerSprite(sprite: Sprite) {
        if (sprites.none { it.name == sprite.name }) {
            sprites.add(sprite)
            services.sprites.registerSprite(sprite)
        }
    }

    /**
     * Get next available sprite OAM slot.
     *
     * Delegates to [services.sprites] for allocation tracking.
     */
    internal fun nextSpriteSlot(): Int {
        val slot = services.sprites.allocateSlot()
        _nextSpriteSlot = slot + 1 // Keep internal counter in sync
        return slot
    }

    /** Register a pool with the game */
    internal fun registerPool(pool: Pool) {
        _pools.add(pool)
    }

    /** Register a physics world with the game */
    internal fun registerPhysicsWorld(physicsWorld: PhysicsWorld) {
        _physicsWorld = physicsWorld
    }

    /** Register a particle system with the game */
    internal fun registerParticleSystem(particleSystem: ParticleSystem) {
        _particleSystems.add(particleSystem)
    }

    // =========================================================================
    // Internal accessors for feature extension functions
    // =========================================================================

    internal fun nextInputBufferId(): Int = _nextInputBufferId++

    internal fun addInputBuffer(buffer: InputBufferData) = _inputBuffers.add(buffer)

    internal fun setCamera(camera: Camera) {
        _camera = camera
    }

    internal fun transitionsCount(): Int = _transitions.size

    internal fun addTransition(transition: TransitionDefinition) = _transitions.add(transition)

    internal fun setAssetDir(dir: String) {
        _assetDir = dir
    }

    internal fun addSprite(sprite: Sprite) {
        if (sprites.none { it.name == sprite.name }) {
            sprites.add(sprite)
            services.sprites.registerSprite(sprite)
        }
    }

    internal fun tilemapsCount(): Int = tilemaps.size

    internal fun addTilemap(tilemap: TileMap) = tilemaps.add(tilemap)

    internal fun getAudioMixer(): AudioMixer? = _audioMixer

    internal fun setAudioMixer(mixer: AudioMixer) {
        _audioMixer = mixer
    }

    internal fun getLink(): LinkDefinition? = _link

    internal fun setLink(link: LinkDefinition) {
        _link = link
    }

    internal fun addCutscene(cutscene: CutsceneDefinition) = _cutscenes.add(cutscene)

    internal fun addNavGrid(grid: NavGrid) = _navGrids.add(grid)

    internal fun addSoundEffect(sfx: SoundEffect) = soundEffects.add(sfx)

    internal fun musicCount(): Int = musicList.size

    internal fun addMusic(music: Music) = musicList.add(music)

    internal fun addPalette(palette: GBCPalette) = _palettes.add(palette)

    internal fun addDialog(dialog: DialogDefinition) = _dialogs.add(dialog)

    internal fun addMenu(menu: MenuDefinition) = _menus.add(menu)

    internal fun getSaveData(): SaveData? = _saveData

    internal fun setSaveData(data: SaveData) {
        _saveData = data
    }

    internal fun getConfig(): GameConfig = config

    internal fun setConfig(newConfig: GameConfig) {
        config = newConfig
    }

    fun build(): Game {
        require(_startScene.isNotEmpty()) { "Must set 'start' scene" }
        require(_startScene in scenes) { "Start scene '$_startScene' not defined" }

        // Sync variables and arrays to services for DI access
        variables.forEach { services.variables.registerVariable(it) }
        arrays.forEach { services.variables.registerArray(it) }

        return Game(
            name = name,
            config = config,
            variables = variables.toList(),
            arrays = arrays.toList(),
            sprites = sprites.toList(),
            entities = _entities.toList(),
            pools = _pools.toList(),
            particleSystems = _particleSystems.toList(),
            tilemaps = tilemaps.toList(),
            soundEffects = soundEffects.toList(),
            music = musicList.toList(),
            scenes = scenes.toMap(),
            startScene = _startScene,
            assetDir = _assetDir,
            tileData = _tileData.toList(),
            mapData = _mapData.toList(),
            palettes = _palettes.toList(),
            stateMachines = _stateMachines.toList(),
            saveData = _saveData,
            dialogs = _dialogs.toList(),
            menus = _menus.toList(),
            camera = _camera,
            physicsWorld = _physicsWorld,
            navGrids = _navGrids.toList(),
            inputBuffers = _inputBuffers.toList(),
            audioMixer = _audioMixer,
            link = _link,
            cutscenes = _cutscenes.toList(),
        )
    }
}
