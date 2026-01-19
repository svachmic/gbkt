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
import io.github.gbkt.core.Music
import io.github.gbkt.core.NavGrid
import io.github.gbkt.core.PhysicsWorld
import io.github.gbkt.core.SaveData
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.SoundEffect
import io.github.gbkt.core.StateMachine
import io.github.gbkt.core.TagRef
import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.EntityRegistry
import io.github.gbkt.core.entity.Pool
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.ParticleSystem
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.graphics.TileMap
import io.github.gbkt.core.input.InputBufferData
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.rpg.Character
import io.github.gbkt.core.rpg.EquipmentSlot
import io.github.gbkt.core.rpg.Inventory
import io.github.gbkt.core.rpg.Item
import io.github.gbkt.core.scene.LinkDefinition
import io.github.gbkt.core.scene.Scene
import io.github.gbkt.core.scene.SceneBuilder
import io.github.gbkt.core.scene.TransitionDefinition
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

    // RPG character tracking
    private val _characters = mutableListOf<Character>()

    // Item system tracking
    private val _items = mutableListOf<Item>()
    private val _inventories = mutableListOf<Inventory>()

    // Custom equipment slots tracking
    private val _customEquipmentSlots = mutableListOf<EquipmentSlot>()

    // Custom battle states tracking
    private val _customBattleStates = mutableListOf<io.github.gbkt.core.rpg.BattleState>()

    // Monster system tracking
    private val _monsters = mutableListOf<io.github.gbkt.core.rpg.Monster>()

    // Ability system tracking
    private val _abilities = mutableListOf<io.github.gbkt.core.rpg.Ability>()

    // Status effect system tracking
    private val _statusEffects = mutableListOf<io.github.gbkt.core.rpg.StatusEffectDefinition>()
    private var _nextStatusEffectId = 0

    // Generic zones tracking
    private val _zones = mutableListOf<io.github.gbkt.core.world.GenericZone>()

    // Global flags system
    private var _flags: io.github.gbkt.core.world.GlobalFlags? = null

    // Encounter tables for random encounters
    private val _encounterTables = mutableListOf<io.github.gbkt.core.world.EncounterTable>()

    // Encounter triggers (pluggable trigger systems)
    private val _encounterTriggers = mutableListOf<io.github.gbkt.core.world.EncounterTrigger>()

    // Exploration systems
    private val _explorations = mutableListOf<io.github.gbkt.core.exploration.Exploration>()

    // Movement controllers
    private val _movementControllers =
        mutableListOf<io.github.gbkt.core.movement.MovementController>()

    // Status bars
    private val _statusBars = mutableListOf<io.github.gbkt.core.ui.StatusBarDefinition>()

    // Battle systems (for file-scope code generation)
    private val _battleSystems = mutableListOf<io.github.gbkt.core.rpg.BattleSystem>()

    // Battle engines (pluggable combat systems)
    private val _battleEngines = mutableListOf<io.github.gbkt.core.combat.BattleEngine>()

    // Combat formulas
    private var _combatFormulas: io.github.gbkt.core.rpg.CombatFormulas? = null

    // Stat schemas
    private val _statSchemas = mutableListOf<io.github.gbkt.core.rpg.StatSchema>()

    // Custom map object types
    private val _mapObjectTypes = mutableListOf<io.github.gbkt.core.world.MapObjectTypeDefinition>()

    // Generic map objects
    private val _genericMapObjects = mutableListOf<io.github.gbkt.core.world.GenericMapObject>()

    // Character classes
    private val _characterClasses = mutableListOf<io.github.gbkt.core.rpg.CharacterClass>()

    // Damage calculators
    private val _damageCalculators = mutableListOf<io.github.gbkt.core.rpg.DamageCalculator>()

    // Quest system
    private val _quests = mutableListOf<io.github.gbkt.core.rpg.Quest>()
    private var _questTracker: io.github.gbkt.core.rpg.QuestTracker? = null

    // Shop/Economy system
    private val _shops = mutableListOf<io.github.gbkt.core.rpg.Shop>()
    private var _economy: io.github.gbkt.core.rpg.Economy? = null

    // Extensible tile attributes
    private val _tileAttributes =
        mutableListOf<io.github.gbkt.core.world.ExtensibleTileAttributeDefinition>()

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
     * Register an RPG character with the game.
     *
     * Also registers the underlying entity for collision detection, etc.
     */
    internal fun registerCharacter(character: Character) {
        _characters.add(character)
        // Also register the underlying entity
        registerEntity(character.entity)
    }

    /**
     * Register an item definition with the game.
     *
     * Assigns an item index for code generation.
     */
    internal fun registerItem(item: Item) {
        item.itemIndex = _items.size
        _items.add(item)
    }

    /**
     * Register an inventory with the game.
     *
     * Assigns an inventory index for code generation.
     */
    internal fun registerInventory(inventory: Inventory) {
        inventory.inventoryIndex = _inventories.size
        _inventories.add(inventory)
    }

    /** Register a custom equipment slot with the game. */
    internal fun registerEquipmentSlot(slot: EquipmentSlot) {
        _customEquipmentSlots.add(slot)
    }

    /**
     * Register a custom battle state with the game.
     *
     * Custom states are used for game-specific battle mechanics like cutscenes, special animations,
     * or unique combat phases.
     */
    internal fun registerBattleState(state: io.github.gbkt.core.rpg.BattleState) {
        _customBattleStates.add(state)
    }

    /**
     * Register a monster with the game.
     *
     * Assigns a monster index for code generation.
     */
    internal fun registerMonster(monster: io.github.gbkt.core.rpg.Monster) {
        monster.monsterIndex = _monsters.size
        _monsters.add(monster)
    }

    /**
     * Register an ability with the game.
     *
     * Assigns an ability index for code generation.
     *
     * @throws IllegalArgumentException if an ability with the same ID already exists
     */
    internal fun registerAbility(ability: io.github.gbkt.core.rpg.Ability) {
        val existing = _abilities.find { it.id == ability.id }
        require(existing == null) {
            "Duplicate ability ID: '${ability.id}'. Each ability must have a unique identifier."
        }
        ability.abilityIndex = _abilities.size
        _abilities.add(ability)
    }

    /** Register a status effect definition with the game. */
    internal fun registerStatusEffect(effect: io.github.gbkt.core.rpg.StatusEffectDefinition) {
        _statusEffects.add(effect)
    }

    /** Get next status effect ID for delegation. IDs start at 1 (0 = no effect in slot). */
    internal fun nextStatusEffectId(): Int = ++_nextStatusEffectId

    /**
     * Create and register a status effect.
     *
     * Usage:
     * ```kotlin
     * val poison = statusEffect("Poison") {
     *     debuff()
     *     duration(5)
     *     damagePerTurn(10)
     *     tier(EffectTier.C)
     * }
     *
     * val berserk = statusEffect("Berserk") {
     *     buff()
     *     duration(3)
     *     atkUp(150)
     *     defDown(75)
     * }
     * ```
     */
    fun statusEffect(
        name: String,
        init: io.github.gbkt.core.rpg.StatusEffectBuilder.() -> Unit,
    ): io.github.gbkt.core.rpg.StatusEffect {
        val id = _nextStatusEffectId++
        val builder = io.github.gbkt.core.rpg.StatusEffectBuilder(name, id)
        builder.init()
        val definition = builder.build()
        registerStatusEffect(definition)
        return io.github.gbkt.core.rpg.StatusEffect(definition)
    }

    /**
     * Register a generic zone with the game.
     *
     * Assigns a zone index for code generation.
     */
    internal fun registerZone(zone: io.github.gbkt.core.world.GenericZone) {
        zone.zoneIndex = _zones.size
        _zones.add(zone)
    }

    /** Register global flags with the game. */
    internal fun registerFlags(flags: io.github.gbkt.core.world.GlobalFlags) {
        _flags = flags
    }

    /**
     * Register an encounter table with the game.
     *
     * Assigns a table index for code generation.
     */
    internal fun registerEncounterTable(table: io.github.gbkt.core.world.EncounterTable) {
        _encounterTables.add(table)
    }

    /**
     * Register an encounter trigger with the game.
     *
     * Assigns a system index for code generation.
     */
    internal fun registerEncounterTrigger(trigger: io.github.gbkt.core.world.EncounterTrigger) {
        trigger.systemIndex = _encounterTriggers.size
        _encounterTriggers.add(trigger)
    }

    /**
     * Register an exploration system with the game.
     *
     * Assigns a system index for code generation.
     */
    internal fun registerExploration(exploration: io.github.gbkt.core.exploration.Exploration) {
        exploration.systemIndex = _explorations.size
        _explorations.add(exploration)
    }

    /**
     * Register a movement controller with the game.
     *
     * Assigns a system index for code generation.
     */
    internal fun registerMovementController(
        controller: io.github.gbkt.core.movement.MovementController
    ) {
        controller.systemIndex = _movementControllers.size
        _movementControllers.add(controller)
    }

    /**
     * Register a status bar with the game.
     *
     * Assigns a system index for code generation.
     */
    internal fun registerStatusBar(statusBar: io.github.gbkt.core.ui.StatusBarDefinition) {
        statusBar.systemIndex = _statusBars.size
        _statusBars.add(statusBar)
    }

    /**
     * Register a battle system for file-scope code generation.
     *
     * Unlike most DSL elements, battle systems must be generated at file scope (not inside
     * functions) because they define constants, variables, and helper functions used throughout the
     * combat code.
     */
    internal fun registerBattleSystem(battleSystem: io.github.gbkt.core.rpg.BattleSystem) {
        _battleSystems.add(battleSystem)
    }

    /**
     * Register a battle engine with the game.
     *
     * Assigns a system index for code generation.
     */
    internal fun registerBattleEngine(engine: io.github.gbkt.core.combat.BattleEngine) {
        engine.systemIndex = _battleEngines.size
        _battleEngines.add(engine)
    }

    /** Register combat formulas for code generation. */
    internal fun registerCombatFormulas(formulas: io.github.gbkt.core.rpg.CombatFormulas) {
        _combatFormulas = formulas
    }

    /**
     * Register a stat schema with the game.
     *
     * If marked as default, this schema will be used for characters that don't specify a schema.
     */
    internal fun registerStatSchema(schema: io.github.gbkt.core.rpg.StatSchema) {
        _statSchemas.add(schema)
    }

    /**
     * Register a custom map object type with the game.
     *
     * Assigns a type index for code generation.
     */
    internal fun registerMapObjectType(typeDef: io.github.gbkt.core.world.MapObjectTypeDefinition) {
        typeDef.typeIndex = _mapObjectTypes.size
        _mapObjectTypes.add(typeDef)
    }

    /**
     * Register a generic map object with the game.
     *
     * Assigns an object index for code generation.
     */
    internal fun registerGenericMapObject(mapObject: io.github.gbkt.core.world.GenericMapObject) {
        mapObject.objectIndex = _genericMapObjects.size
        _genericMapObjects.add(mapObject)
    }

    /**
     * Register a character class with the game.
     *
     * Assigns a class index for code generation.
     */
    internal fun registerCharacterClass(characterClass: io.github.gbkt.core.rpg.CharacterClass) {
        characterClass.classIndex = _characterClasses.size
        _characterClasses.add(characterClass)
    }

    /**
     * Register a damage calculator with the game.
     *
     * Assigns a system index for code generation.
     */
    internal fun registerDamageCalculator(calculator: io.github.gbkt.core.rpg.DamageCalculator) {
        calculator.systemIndex = _damageCalculators.size
        _damageCalculators.add(calculator)
    }

    /**
     * Register a quest with the game.
     *
     * Assigns a quest index for code generation.
     */
    internal fun registerQuest(quest: io.github.gbkt.core.rpg.Quest) {
        quest.questIndex = _quests.size
        _quests.add(quest)
    }

    /** Register a quest tracker configuration with the game. */
    internal fun registerQuestTracker(tracker: io.github.gbkt.core.rpg.QuestTracker) {
        tracker.trackerIndex = 0 // Only one tracker per game
        _questTracker = tracker
    }

    /**
     * Register a shop with the game.
     *
     * Assigns a shop index for code generation.
     */
    internal fun registerShop(shop: io.github.gbkt.core.rpg.Shop) {
        shop.shopIndex = _shops.size
        _shops.add(shop)
    }

    /** Register economy configuration with the game. */
    internal fun registerEconomy(economy: io.github.gbkt.core.rpg.Economy) {
        economy.economyIndex = 0 // Only one economy per game
        // Assign currency indices
        economy.currencies.forEachIndexed { index, currency -> currency.currencyIndex = index }
        _economy = economy
    }

    /**
     * Register a custom tile attribute with the game.
     *
     * Assigns an attribute index for code generation.
     */
    internal fun registerTileAttribute(
        attr: io.github.gbkt.core.world.ExtensibleTileAttributeDefinition
    ) {
        attr.attributeIndex = _tileAttributes.size
        _tileAttributes.add(attr)
    }

    /**
     * Create and register an encounter table.
     *
     * Usage:
     * ```kotlin
     * val dungeonEncounters = encounterTable("dungeon") {
     *     safeSteps(10)
     *     initialChance(5)
     *     incrementPerStep(3)
     *
     *     entry(weight = 30) { +kobold }
     *     entry(weight = 25) { +goblin }
     *     entry(weight = 20) { +kobold; +kobold }
     * }
     * ```
     */
    fun encounterTable(
        id: String,
        init: io.github.gbkt.core.world.EncounterTableBuilder.() -> Unit,
    ): io.github.gbkt.core.world.EncounterTable {
        val builder = io.github.gbkt.core.world.EncounterTableBuilder(id)
        builder.init()
        val table = builder.build()
        registerEncounterTable(table)
        return table
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
            characters = _characters.toList(),
            items = _items.toList(),
            inventories = _inventories.toList(),
            equipmentSlots = _customEquipmentSlots.toList(),
            customBattleStates = _customBattleStates.toList(),
            monsters = _monsters.toList(),
            abilities = _abilities.toList(),
            statusEffects = _statusEffects.toList(),
            zones = _zones.toList(),
            globalFlags = _flags,
            encounterTables = _encounterTables.toList(),
            encounterTriggers = _encounterTriggers.toList(),
            explorations = _explorations.toList(),
            movementControllers = _movementControllers.toList(),
            statusBars = _statusBars.toList(),
            battleSystems = _battleSystems.toList(),
            battleEngines = _battleEngines.toList(),
            combatFormulas = _combatFormulas,
            statSchemas = _statSchemas.toList(),
            mapObjectTypes = _mapObjectTypes.toList(),
            genericMapObjects = _genericMapObjects.toList(),
            characterClasses = _characterClasses.toList(),
            damageCalculators = _damageCalculators.toList(),
            quests = _quests.toList(),
            questTracker = _questTracker,
            shops = _shops.toList(),
            economy = _economy,
            tileAttributes = _tileAttributes.toList(),
        )
    }
}
