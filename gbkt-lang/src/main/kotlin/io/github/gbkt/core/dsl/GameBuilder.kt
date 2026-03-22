/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ArrayDef
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CollisionGroupIR
import io.github.gbkt.core.ir.CollisionResponse
import io.github.gbkt.core.ir.CollisionRuleIR
import io.github.gbkt.core.ir.ContainerIR
import io.github.gbkt.core.ir.DialogDef
import io.github.gbkt.core.ir.DropTableIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GlobalFlagsIR
import io.github.gbkt.core.ir.HudDef
import io.github.gbkt.core.ir.IRCollFixedSlots
import io.github.gbkt.core.ir.IRCollHashTable
import io.github.gbkt.core.ir.IRCollPool
import io.github.gbkt.core.ir.IRCollRingBuffer
import io.github.gbkt.core.ir.ItemCategoryDef
import io.github.gbkt.core.ir.ItemDef
import io.github.gbkt.core.ir.MenuDef
import io.github.gbkt.core.ir.MusicDef
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.RefKind
import io.github.gbkt.core.ir.SetPalette
import io.github.gbkt.core.ir.SoundEffectDef
import io.github.gbkt.core.ir.StructDef
import io.github.gbkt.core.ir.SystemIR
import io.github.gbkt.core.ir.VariableDef
import io.github.gbkt.core.ir.ZoneIR

/**
 * Top-level builder that produces a [GameIR] instance from the v2 DSL.
 *
 * Usage:
 * ```kotlin
 * val ir = game("MyGame") {
 *     val player = actor("player") { position(80, 72) }
 *     val titleScene = scene("title") { enter { hideSprites() } }
 *     start = "title"
 * }.build()
 * ```
 *
 * **Build-time validation:**
 * 1. All pending [RefRegistry] refs are resolved — throws [DSLValidationError] for first
 *    unresolved.
 * 2. [start] scene must be set — throws if null.
 * 3. [start] must reference a registered scene ID.
 *
 * **Asset collection:** Assets referenced by actor sprites are collected automatically during
 * build. The top-level [asset] function is used directly in actor/sprite builder blocks — it is a
 * plain top-level function and is not blocked by [GbktDsl] scope markers.
 */
@GbktDsl
class GameBuilder(val name: String) {
    internal val refRegistry = RefRegistry()

    private val actorBuilders: MutableList<ActorBuilder> = mutableListOf()
    private val sceneBuilders: MutableList<io.github.gbkt.core.ir.SceneIR> = mutableListOf()
    private val systems: MutableList<SystemIR> = mutableListOf()
    private val variables: MutableList<VariableDef> = mutableListOf()
    private val arrays: MutableList<ArrayDef> = mutableListOf()
    private val soundEffectDefs: MutableList<SoundEffectDef> = mutableListOf()
    private val dialogs: MutableList<DialogDef> = mutableListOf()
    private val menus: MutableList<MenuDef> = mutableListOf()
    private val huds: MutableList<HudDef> = mutableListOf()
    private val _zones: MutableList<ZoneIR> = mutableListOf()
    private val _flags: MutableList<GlobalFlagsIR> = mutableListOf()
    private val _itemCategories: MutableList<ItemCategoryDef> = mutableListOf()
    private val _items: MutableList<ItemDef> = mutableListOf()
    private val _containers: MutableList<ContainerIR> = mutableListOf()
    private val _dropTables: MutableList<DropTableIR> = mutableListOf()
    private val _structs: MutableList<StructDef> = mutableListOf()
    private val _musicDefs: MutableList<MusicDef> = mutableListOf()
    private val _palettes: MutableList<io.github.gbkt.core.ir.GBCPalette> = mutableListOf()
    private val _hashTables: MutableList<IRCollHashTable> = mutableListOf()
    private val _pools: MutableList<IRCollPool> = mutableListOf()
    private val _ringBuffers: MutableList<IRCollRingBuffer> = mutableListOf()
    private val _fixedSlots: MutableList<IRCollFixedSlots> = mutableListOf()
    private val _actorPools: MutableList<io.github.gbkt.core.ir.ActorPoolIR> = mutableListOf()
    internal val puzzleObjects: MutableList<io.github.gbkt.core.ir.PuzzleObjectIR> = mutableListOf()
    private val _collisionGroups: MutableList<io.github.gbkt.core.ir.CollisionGroupIR> =
        mutableListOf()
    private val _collisionRules: MutableList<io.github.gbkt.core.ir.CollisionRuleIR> =
        mutableListOf()

    private var config: CartridgeConfig = CartridgeConfig()

    /** The ID of the scene to display on game boot. Must be set before [build]. */
    var start: String? = null

    // -------------------------------------------------------------------------
    // Internal: variable registration (called by VarDelegate via GameBuilderContext)
    // -------------------------------------------------------------------------

    internal fun registerVariable(def: VariableDef) {
        variables.add(def)
    }

    internal fun registerArray(def: ArrayDef) {
        arrays.add(def)
    }

    /**
     * Registers a [StructDef] with this game builder so that it is included in [GameIR.structs].
     *
     * Called by [io.github.gbkt.core.dsl.struct] DSL extension after building the struct. Structs
     * are emitted as `typedef struct { ... }` declarations before any collection code.
     */
    internal fun registerStruct(def: StructDef) {
        _structs.add(def)
    }

    /**
     * Look up a registered struct by name.
     *
     * Used by reified generic collection delegates (`hashtable<TileHashEntry>(64)`) to resolve the
     * [StructDef] from the builder-local registry at DSL construction time.
     *
     * @return the matching [StructDef] or null if not registered
     */
    fun findStructByName(name: String): StructDef? = _structs.find { it.name == name }

    // -------------------------------------------------------------------------
    // Internal: collection registration (called by collection delegates)
    // -------------------------------------------------------------------------

    /** Registers a hash table IR node for inclusion in [GameIR.hashTables]. */
    internal fun registerHashTable(ht: IRCollHashTable) {
        _hashTables.add(ht)
    }

    /** Registers an object pool IR node for inclusion in [GameIR.pools]. */
    internal fun registerPool(pool: IRCollPool) {
        _pools.add(pool)
    }

    /** Registers a ring buffer IR node for inclusion in [GameIR.ringBuffers]. */
    internal fun registerRingBuffer(rb: IRCollRingBuffer) {
        _ringBuffers.add(rb)
    }

    /** Registers a fixed-slots IR node for inclusion in [GameIR.fixedSlots]. */
    internal fun registerFixedSlots(fs: IRCollFixedSlots) {
        _fixedSlots.add(fs)
    }

    /**
     * Registers an actor pool IR node for inclusion in [GameIR.actorPools].
     *
     * Called by [ActorPoolDelegate.provideDelegate] when a `val bullets by pool(bullet, max = 8)`
     * property is initialized inside a `game {}` block.
     */
    internal fun registerActorPool(pool: io.github.gbkt.core.ir.ActorPoolIR) {
        _actorPools.add(pool)
    }

    /**
     * Registers a [MusicDef] with this game builder so that it is included in [GameIR.musicDefs].
     *
     * Called by [MusicDelegate.provideDelegate] when a `val theme by music(...)` property is
     * initialized inside a `game {}` block.
     */
    internal fun registerMusic(def: MusicDef) {
        _musicDefs.add(def)
    }

    /**
     * Registers a [SoundEffectDef] with this game builder so that it is included in
     * [GameIR.soundEffects].
     *
     * Called by [SoundEffectDelegate.provideDelegate] when a `val hit by soundEffect { ... }`
     * property is initialized inside a `game {}` block.
     */
    internal fun registerSoundEffect(def: SoundEffectDef) {
        refRegistry.register(def.id, RefKind.SYSTEM)
        soundEffectDefs.add(def)
    }

    /**
     * Registers a [GBCPalette] with this game builder so that it is included in [GameIR.palettes].
     *
     * Called by [PaletteDelegate.provideDelegate] when a `val forest by palette { ... }` property
     * is initialized inside a `game {}` block. Also callable directly when palettes are constructed
     * as plain [GBCPalette] instances outside the delegate pattern (e.g. in a palette registry
     * object) and need to be registered explicitly.
     */
    fun registerPalette(palette: io.github.gbkt.core.ir.GBCPalette) {
        _palettes.add(palette)
    }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    /** Configures cartridge hardware settings (type, ROM banks, RAM banks). */
    fun config(block: ConfigBuilder.() -> Unit) {
        val builder = ConfigBuilder()
        builder.block()
        config = builder.build()
    }

    // -------------------------------------------------------------------------
    // Actor definition
    // -------------------------------------------------------------------------

    /**
     * Registers an actor with the given [id] and returns an [ActorRef].
     *
     * Used internally by [actor] string overload and [ActorDelegate.provideDelegate] so that both
     * explicit-name and name-inferred actor definitions share the same registration logic.
     */
    internal fun registerActor(id: String, block: ActorBuilder.() -> Unit): ActorRef {
        refRegistry.register(id, RefKind.ACTOR)
        val builder = ActorBuilder(id)
        builder.block()
        actorBuilders.add(builder)
        return ActorRef(id)
    }

    /**
     * Defines an actor (sprite entity) with an explicit [id] and registers its ID.
     *
     * @return [ActorRef] for use in script operations.
     */
    fun actor(id: String, block: ActorBuilder.() -> Unit): ActorRef = registerActor(id, block)

    /**
     * Defines an actor (sprite entity) with a name inferred from the Kotlin property.
     *
     * Returns an [ActorDelegate] that implements `provideDelegate` — Kotlin calls it when the `by`
     * keyword is used, at which point the property name is captured and the actor is registered.
     *
     * Usage:
     * ```kotlin
     * val paddle by actor { position(16, 64) }   // name inferred as "paddle"
     * val ball by actor { position(80, 72) }      // name inferred as "ball"
     * ```
     *
     * @return [ActorDelegate] for property delegation.
     */
    fun actor(block: ActorBuilder.() -> Unit): ActorDelegate = ActorDelegate(null, block)

    // -------------------------------------------------------------------------
    // Scene definition
    // -------------------------------------------------------------------------

    /**
     * Creates a forward-declared [SceneRef] for use in navigate() before the scene is defined.
     *
     * Use this to break circular scene navigation dependencies:
     * ```kotlin
     * val titleRef = sceneRef("title")       // forward-declare
     * val gameScene = scene("game") {
     *     frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
     * }
     * val titleScene = scene("title") { ... } // defined later
     * ```
     *
     * The scene MUST be defined later via [scene] with the same ID. If no matching scene is
     * defined, the ref will still work at codegen (NavigateTo uses the string ID internally) but
     * refRegistry validation will catch it if enabled.
     *
     * Do NOT use this for scenes that have already been defined — [scene] returns a [SceneRef]
     * directly. Use `sceneRef()` only when the scene is defined AFTER the navigation call site.
     */
    fun sceneRef(id: String): SceneRef = SceneRef(id)

    /**
     * Defines a scene and registers its ID.
     *
     * @return [SceneRef] for use in navigation operations.
     */
    fun scene(id: String, block: SceneBuilder.() -> Unit): SceneRef {
        refRegistry.register(id, RefKind.SCENE)
        val builder = SceneBuilder(id, refRegistry)
        builder.block()
        sceneBuilders.add(builder.build())
        return SceneRef(id)
    }

    // -------------------------------------------------------------------------
    // System builders
    // -------------------------------------------------------------------------

    /** Configures and registers the camera system. */
    fun camera(block: CameraBuilder.() -> Unit) {
        val builder = CameraBuilder()
        builder.block()
        val system = builder.build()
        refRegistry.register(system.id, RefKind.SYSTEM)
        systems.add(system)
    }

    /** Configures and registers a save/load system with the given ID. */
    fun saveData(id: String, block: SaveDataBuilder.() -> Unit) {
        val builder = SaveDataBuilder(id)
        builder.block()
        val system = builder.build()
        refRegistry.register(id, RefKind.SYSTEM)
        systems.add(system)
    }

    /** Configures and registers the exploration system. */
    fun exploration(block: ExplorationBuilder.() -> Unit) {
        val builder = ExplorationBuilder()
        builder.block()
        val system = builder.build()
        refRegistry.register(system.id, RefKind.SYSTEM)
        systems.add(system)
    }

    /** Configures and registers the pathfinding system. */
    fun pathfinding(id: String = "pathfinding", block: PathfindingBuilder.() -> Unit = {}) {
        val builder = PathfindingBuilder(id)
        builder.block()
        val system = builder.build()
        refRegistry.register(system.id, RefKind.SYSTEM)
        systems.add(system)
    }

    /**
     * Configures and registers the audio channel group mixing system (A5).
     *
     * Generates NR50/NR51 register-based volume control with channel groups (music, sfx, ui).
     * Default groups are auto-populated when no explicit [AudioMixerBuilder.group] calls are made
     * (Gap 3).
     *
     * Usage with defaults:
     * ```kotlin
     * audioMixer { }
     * ```
     *
     * Usage with custom groups:
     * ```kotlin
     * audioMixer {
     *     group("music") { channels(1, 2); volume(7) }
     *     group("sfx")   { channels(3, 4); volume(6) }
     *     masterVolume(7)
     *     autoDucking(enabled = true, duckLevel = 3)
     * }
     * ```
     */
    fun audioMixer(id: String = "audio_mixer", block: AudioMixerBuilder.() -> Unit = {}) {
        val builder = AudioMixerBuilder(id)
        builder.block()
        val system = builder.build()
        refRegistry.register(system.id, RefKind.SYSTEM)
        systems.add(system)
    }

    // -------------------------------------------------------------------------
    // UI system builders — dialog, menu, HUD
    // -------------------------------------------------------------------------

    /**
     * Defines a named dialog box and returns a [DialogHandle] for emitting dialog script ops.
     *
     * Usage:
     * ```kotlin
     * val elder = dialog("elder") {
     *     border(BorderStyle.SINGLE)
     *     speaker("Elder Moros")
     *     textSpeed(2)
     * }
     * // In a scene lifecycle block:
     * elder.say("Welcome, traveler.")
     * ```
     */
    fun dialog(id: String, block: DialogBuilder.() -> Unit): DialogHandle {
        val builder = DialogBuilder(id)
        builder.block()
        dialogs += builder.build()
        return DialogHandle(id)
    }

    /**
     * Defines a named interactive menu and returns a [MenuHandle] for emitting menu script ops.
     *
     * Usage:
     * ```kotlin
     * val mainMenu = menu("mainMenu") {
     *     layout(MenuLayout.VERTICAL)
     *     item("Start") { navigate(gameScene) }
     *     item("Quit") { navigate(titleScene) }
     * }
     * // In a scene lifecycle block:
     * mainMenu.show()
     * ```
     */
    fun menu(id: String, block: MenuBuilder.() -> Unit): MenuHandle {
        val builder = MenuBuilder(id)
        builder.block()
        menus += builder.build()
        return MenuHandle(id)
    }

    /**
     * Defines a named HUD panel and returns a [HudPanel] for emitting HUD script ops.
     *
     * Usage:
     * ```kotlin
     * val statsHud = hud("statsHud") {
     *     anchor(Anchor.TOP_LEFT)
     *     bar("hp") { variable(hp); max(maxHp); width(8) }
     *     number("score") { variable(score); label("Score: ") }
     * }
     * // In a scene lifecycle block:
     * statsHud.show()
     * ```
     */
    fun hud(id: String, block: HudBuilder.() -> Unit): HudPanel {
        val builder = HudBuilder(id)
        builder.block()
        huds += builder.build()
        return HudPanel(id)
    }

    // -------------------------------------------------------------------------
    // World system builders — zones and flags
    // -------------------------------------------------------------------------

    /**
     * Defines a navigable zone (dungeon floor, overworld region, town, etc.) and returns a
     * [ZoneRef] for use in [ExplorationBuilder.startZone] and transition configurations.
     *
     * Usage:
     * ```kotlin
     * val floor1 = zone("floor1") {
     *     name("Dungeon Level 1")
     *     tileset("dungeon.png")
     *     size(32, 32)
     *     encounters { safeSteps(10); entry("goblin_pack", weight = 30) }
     * }
     * // Then reference it in exploration:
     * exploration { startZone(floor1) }
     * ```
     *
     * @return [ZoneRef] with the zone's ID for type-safe references.
     */
    fun zone(id: String, block: ZoneBuilder.() -> Unit): ZoneRef {
        val builder = ZoneBuilder(id)
        builder.block()
        val zone = builder.build()
        _zones += zone
        return ZoneRef(id)
    }

    /**
     * Defines a global flags container grouping named boolean flags into pages.
     *
     * Usage:
     * ```kotlin
     * flags {
     *     page("story") { flag("metElder"); flag("hasKey") }
     * }
     * ```
     */
    fun flags(block: FlagsBuilder.() -> Unit) {
        val builder = FlagsBuilder("flags")
        builder.block()
        _flags += builder.build()
    }

    /**
     * Defines a global flags container with an explicit [id] for disambiguation.
     *
     * Use this overload when you need multiple flags containers or want a custom ID for save/load.
     */
    fun flags(id: String, block: FlagsBuilder.() -> Unit) {
        val builder = FlagsBuilder(id)
        builder.block()
        _flags += builder.build()
    }

    // -------------------------------------------------------------------------
    // Combat engine builders
    // -------------------------------------------------------------------------

    /**
     * Configures and registers a combat engine system with an explicit [id] and returns a
     * [CombatEngineRef] for use in [TriggerSystem] script ops.
     *
     * Usage:
     * ```kotlin
     * val combat = combatEngine("battle") {
     *     type(CombatType.TURN_BASED)
     *     combatant("hero", CombatantSide.PLAYER)
     *     combatant("goblin", CombatantSide.ENEMY)
     *     onVictory { navigate(victoryScene) }
     * }
     * ```
     */
    fun combatEngine(id: String, block: CombatEngineBuilder.() -> Unit): CombatEngineRef {
        val builder = CombatEngineBuilder(id)
        builder.block()
        val system = builder.build()
        registerSystem(system)
        return CombatEngineRef(id)
    }

    /**
     * Property delegate factory for defining a combat engine with ID inferred from property name.
     *
     * Usage: `val combat by combatEngine { type(CombatType.TURN_BASED) }`
     *
     * @return [CombatEngineDelegate] for property delegation.
     */
    fun combatEngine(block: CombatEngineBuilder.() -> Unit): CombatEngineDelegate =
        CombatEngineDelegate("", block)

    // -------------------------------------------------------------------------
    // Inventory system builders
    // -------------------------------------------------------------------------

    /**
     * Defines the item catalog (categories + items) for this game.
     *
     * Usage:
     * ```kotlin
     * items {
     *     val consumable by category { defaultMaxStack(10) }
     *     val potion by item { name("Potion"); category(consumable); buyPrice(50); onUse { heal(50) } }
     * }
     * ```
     */
    fun items(block: ItemCatalogBuilder.() -> Unit) {
        val builder = ItemCatalogBuilder()
        builder.block()
        _itemCategories += builder.categories
        _items += builder.items
    }

    /**
     * Defines an inventory container with an explicit [id] and returns a [ContainerRef].
     *
     * Usage:
     * ```kotlin
     * val bag = container("mainBag") { slots(16) }
     * ```
     */
    fun container(id: String, block: ContainerBuilder.() -> Unit): ContainerRef {
        val builder = ContainerBuilder(id)
        builder.block()
        _containers += builder.build()
        return ContainerRef(id)
    }

    /**
     * Property delegate factory for defining a container with ID inferred from property name.
     *
     * Usage: `val bag by container { slots(16) }`
     *
     * @return [ContainerDelegate] for property delegation.
     */
    fun container(block: ContainerBuilder.() -> Unit): ContainerDelegate =
        ContainerDelegate("", block)

    /**
     * Defines a drop/loot table for enemy drops and chest loot.
     *
     * Usage:
     * ```kotlin
     * dropTable("goblin_drops") {
     *     drop("potion", weight = 60)
     *     drop("gold_coin", weight = 30, minCount = 1, maxCount = 3)
     * }
     * ```
     */
    fun dropTable(id: String, block: DropTableBuilder.() -> Unit) {
        val builder = DropTableBuilder(id)
        builder.block()
        _dropTables += builder.build()
    }

    /**
     * Registers any [SystemIR] instance directly and records its ID for ref resolution.
     *
     * Used by genre packages (e.g., gbkt-rpg) to register systems produced by their DSL builders
     * without requiring each genre package to add a dedicated function to [GameBuilder].
     *
     * The [system]'s [SystemIR.id] is registered in the [RefRegistry] under [RefKind.SYSTEM] so
     * that [TriggerSystem] script ops can reference it without validation errors.
     */
    fun registerSystem(system: io.github.gbkt.core.ir.SystemIR) {
        refRegistry.register(system.id, RefKind.SYSTEM)
        systems.add(system)
    }

    // -------------------------------------------------------------------------
    // NPC collision group and rule registration
    // -------------------------------------------------------------------------

    /**
     * Registers a [CollisionGroupIR] so it is included in [GameIR.collisionGroups].
     *
     * Called by [CollisionGroupDelegate.provideDelegate] when a `val x by collisionGroup()`
     * property is initialized inside a `game {}` block.
     */
    internal fun registerCollisionGroup(group: CollisionGroupIR) {
        _collisionGroups.add(group)
    }

    /**
     * Registers a [CollisionRuleIR] so it is included in [GameIR.collisionRules].
     *
     * Called by [collisionRule] after building the rule IR node.
     */
    internal fun registerCollisionRule(rule: CollisionRuleIR) {
        _collisionRules.add(rule)
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Validates all refs and returns the compiled [GameIR].
     *
     * **Validation steps:**
     * 1. Resolve all pending refs via [RefRegistry.resolveAll] — fails on first unresolved.
     * 2. Check that [start] is not null — fails if game has no start scene.
     * 3. Check that [start] references a registered scene ID — fails with "Did you mean?" if close
     *    match exists.
     *
     * @throws DSLValidationError on any validation failure.
     */
    fun build(): GameIR {
        // Step 1: resolve all registered refs
        refRegistry.resolveAll()

        // Step 2: start scene must be set
        val startScene =
            start
                ?: throw DSLValidationError(
                    "error: No start scene set. Use `start = \"sceneId\"` in the game block."
                )

        // Step 3: start scene must reference a known scene
        val knownScenes = refRegistry.registeredIds(RefKind.SCENE)
        if (!knownScenes.contains(startScene)) {
            // Use Suggestions for "Did you mean?"
            val suggestion =
                io.github.gbkt.core.Suggestions.formatSuggestion(startScene, knownScenes)
            throw DSLValidationError(
                "error: Unresolved reference \"$startScene\" in start scene.$suggestion"
            )
        }

        // Collect actors (build from builders)
        val actors = actorBuilders.map { it.build() }

        // Collect all asset refs from actor sprites
        val assets = mutableListOf<AssetRef>()
        for (actor in actors) {
            actor.sprite?.assetRef?.let { assets.add(it) }
        }

        // Inject per-actor palette SetPalette ops into each scene's enter handler.
        // Actors with a non-null palette override get a SPRITE palette load at scene enter time.
        val actorPaletteOps =
            actors.mapNotNull { actor ->
                actor.palette?.let { pal ->
                    val slot = if (pal.slot >= 0) pal.slot else 0
                    SetPalette(pal.name, slot, PaletteType.SPRITE)
                }
            }
        val scenes =
            if (actorPaletteOps.isNotEmpty()) {
                sceneBuilders.map { scene ->
                    scene.copy(enterOps = actorPaletteOps + scene.enterOps)
                }
            } else {
                sceneBuilders.toList()
            }

        // Auto-create implicit _default_npc group and OVERLAP rule.
        // Any actor with collidesWithNpcs=true but NO explicit groupIds gets added to this group.
        // Actors with explicit groupIds are NOT added to _default_npc (no double-checking).
        val implicitNpcActors =
            actors.filter { actor ->
                val cfg = actor.npcCollisionConfig
                cfg != null && cfg.collidesWithNpcs && cfg.groupIds.isEmpty()
            }
        val effectiveGroups = _collisionGroups.toMutableList()
        val effectiveRules = _collisionRules.toMutableList()
        if (implicitNpcActors.isNotEmpty()) {
            if (effectiveGroups.none { it.id == CollisionGroupIR.DEFAULT_NPC_GROUP }) {
                effectiveGroups.add(CollisionGroupIR(CollisionGroupIR.DEFAULT_NPC_GROUP))
            }
            if (
                effectiveRules.none { rule ->
                    rule.groupA == CollisionGroupIR.DEFAULT_NPC_GROUP &&
                        rule.groupB == CollisionGroupIR.DEFAULT_NPC_GROUP
                }
            ) {
                effectiveRules.add(
                    CollisionRuleIR(
                        groupA = CollisionGroupIR.DEFAULT_NPC_GROUP,
                        groupB = CollisionGroupIR.DEFAULT_NPC_GROUP,
                        response = CollisionResponse.OVERLAP,
                    )
                )
            }
        }
        // Re-map actors: assign implicit _default_npc groupId to eligible actors
        val finalActors =
            actors.map { actor ->
                val cfg = actor.npcCollisionConfig
                if (cfg != null && cfg.collidesWithNpcs && cfg.groupIds.isEmpty()) {
                    actor.copy(
                        npcCollisionConfig =
                            cfg.copy(groupIds = listOf(CollisionGroupIR.DEFAULT_NPC_GROUP))
                    )
                } else {
                    actor
                }
            }

        return GameIR(
            name = name,
            config = config,
            scenes = scenes,
            actors = finalActors,
            systems = systems.toList(),
            variables = variables.toList(),
            arrays = arrays.toList(),
            soundEffects = soundEffectDefs.toList(),
            structs = _structs.toList(),
            hashTables = _hashTables.toList(),
            pools = _pools.toList(),
            ringBuffers = _ringBuffers.toList(),
            fixedSlots = _fixedSlots.toList(),
            assets = assets,
            palettes = _palettes.toList(),
            startScene = startScene,
            sourceLocation = captureV2Location(),
            dialogs = dialogs.toList(),
            menus = menus.toList(),
            huds = huds.toList(),
            zones = _zones.toList(),
            flags = _flags.toList(),
            itemCategories = _itemCategories.toList(),
            items = _items.toList(),
            containers = _containers.toList(),
            dropTables = _dropTables.toList(),
            musicDefs = _musicDefs.toList(),
            actorPools = _actorPools.toList(),
            puzzleObjects = puzzleObjects.toList(),
            collisionGroups = effectiveGroups.toList(),
            collisionRules = effectiveRules.toList(),
        )
    }
}

// =============================================================================
// TOP-LEVEL DSL ENTRY POINT
// =============================================================================

/**
 * Creates a [GameBuilder] with the given [name] and configures it via [block].
 *
 * Sets [GameBuilderContext] thread-local so that variable delegates (u8Var, u16Var, etc.) can
 * register [io.github.gbkt.core.ir.VariableDef] instances with the builder.
 *
 * Call [GameBuilder.build] on the returned builder to produce a [GameIR].
 *
 * ```kotlin
 * val ir = game("Pong") {
 *     scene("game") { ... }
 *     start = "game"
 * }.build()
 * ```
 */
fun game(name: String, block: GameBuilder.() -> Unit): GameBuilder {
    val builder = GameBuilder(name)
    GameBuilderContext.with(builder) { builder.block() }
    return builder
}
