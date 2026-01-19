/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.combat.BattleEngine
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.Pool
import io.github.gbkt.core.exploration.Exploration
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.ParticleSystem
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.graphics.TileMap
import io.github.gbkt.core.input.InputBufferData
import io.github.gbkt.core.ir.GBArray
import io.github.gbkt.core.ir.GBCMode
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.movement.MovementController
import io.github.gbkt.core.rpg.Ability
import io.github.gbkt.core.rpg.Character
import io.github.gbkt.core.rpg.CharacterClass
import io.github.gbkt.core.rpg.DamageCalculator
import io.github.gbkt.core.rpg.Economy
import io.github.gbkt.core.rpg.EquipmentSlot
import io.github.gbkt.core.rpg.Inventory
import io.github.gbkt.core.rpg.Item
import io.github.gbkt.core.rpg.Monster
import io.github.gbkt.core.rpg.Quest
import io.github.gbkt.core.rpg.QuestTracker
import io.github.gbkt.core.rpg.Shop
import io.github.gbkt.core.rpg.StatSchema
import io.github.gbkt.core.rpg.StatusEffectDefinition
import io.github.gbkt.core.scene.LinkDefinition
import io.github.gbkt.core.scene.Scene
import io.github.gbkt.core.ui.MenuDefinition
import io.github.gbkt.core.ui.StatusBarDefinition
import io.github.gbkt.core.world.EncounterTrigger
import io.github.gbkt.core.world.ExtensibleTileAttributeDefinition
import io.github.gbkt.core.world.GenericMapObject
import io.github.gbkt.core.world.GenericZone
import io.github.gbkt.core.world.GlobalFlags
import io.github.gbkt.core.world.MapObjectTypeDefinition

// =============================================================================
// GAME DEFINITION DSL
// =============================================================================

/**
 * Entry point for defining a Game Boy game.
 *
 * Usage:
 * ```
 * val myGame = gbGame("MyGame") {
 *     var playerX by u8(80)
 *
 *     scene("gameplay") {
 *         every.frame {
 *             playerX += dpad.x * 2
 *         }
 *     }
 * }
 * ```
 */
fun gbGame(name: String, init: GameBuilder.() -> Unit): Game {
    val builder = GameBuilder(name)
    return GameScopeContext.withScope(builder) { builder.apply(init).build() }
}

/** Compiled tile data for a sprite. */
data class CompiledTileData(
    val name: String,
    val data: List<ByteArray>,
    val tileCount: Int,
    /** ROM bank for this tile data. -1 means home bank (no pragma). */
    val bank: Int = -1,
) {
    /** Total size of tile data in bytes. */
    val sizeBytes: Int
        get() = data.sumOf { it.size }
}

/** Compiled map data for a tilemap background. */
data class CompiledMapData(
    val name: String,
    val width: Int, // Map width in tiles
    val height: Int, // Map height in tiles
    val data: ByteArray, // Tile indices (row-major order)
    val tilesetName: String, // Reference to associated tileset
    val collisionData: ByteArray? = null, // Collision map data (0 = walkable, >0 = blocked)
    /** ROM bank for this map data. -1 means home bank (no pragma). */
    val bank: Int = -1,
) {
    /** Total size of map data in bytes (including collision data if present). */
    val sizeBytes: Int
        get() = data.size + (collisionData?.size ?: 0)

    override fun equals(other: Any?) =
        other is CompiledMapData &&
            name == other.name &&
            width == other.width &&
            height == other.height &&
            data.contentEquals(other.data) &&
            tilesetName == other.tilesetName &&
            bank == other.bank &&
            (collisionData?.contentEquals(other.collisionData) ?: (other.collisionData == null))

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        result = 31 * result + tilesetName.hashCode()
        result = 31 * result + bank
        result = 31 * result + (collisionData?.contentHashCode() ?: 0)
        return result
    }
}

/** The compiled game, ready for code generation. */
class Game(
    val name: String,
    val config: GameConfig,
    val variables: List<GBVar<*>>,
    val arrays: List<GBArray> = emptyList(),
    val sprites: List<Sprite>,
    val entities: List<Entity> = emptyList(),
    val pools: List<Pool> = emptyList(),
    val particleSystems: List<ParticleSystem> = emptyList(),
    val tilemaps: List<TileMap> = emptyList(),
    val soundEffects: List<SoundEffect> = emptyList(),
    val music: List<Music> = emptyList(),
    val scenes: Map<String, Scene>,
    val startScene: String,
    val assetDir: String? = null,
    val tileData: List<CompiledTileData> = emptyList(),
    val mapData: List<CompiledMapData> = emptyList(),
    val palettes: List<GBCPalette> = emptyList(),
    val stateMachines: List<StateMachine> = emptyList(),
    val saveData: SaveData? = null,
    val dialogs: List<DialogDefinition> = emptyList(),
    val menus: List<MenuDefinition> = emptyList(),
    val camera: Camera? = null,
    val physicsWorld: PhysicsWorld? = null,
    val navGrids: List<NavGrid> = emptyList(),
    val inputBuffers: List<InputBufferData> = emptyList(),
    val audioMixer: AudioMixer? = null,
    val link: LinkDefinition? = null,
    val cutscenes: List<CutsceneDefinition> = emptyList(),
    val characters: List<Character> = emptyList(),
    val items: List<Item> = emptyList(),
    val inventories: List<Inventory> = emptyList(),
    val equipmentSlots: List<EquipmentSlot> = emptyList(),
    val customBattleStates: List<io.github.gbkt.core.rpg.BattleState> = emptyList(),
    val monsters: List<Monster> = emptyList(),
    val abilities: List<Ability> = emptyList(),
    val statusEffects: List<StatusEffectDefinition> = emptyList(),
    val zones: List<GenericZone> = emptyList(),
    val globalFlags: GlobalFlags? = null,
    val encounterTables: List<io.github.gbkt.core.world.EncounterTable> = emptyList(),
    val encounterTriggers: List<EncounterTrigger> = emptyList(),
    val explorations: List<Exploration> = emptyList(),
    val movementControllers: List<MovementController> = emptyList(),
    val statusBars: List<StatusBarDefinition> = emptyList(),
    val battleSystems: List<io.github.gbkt.core.rpg.BattleSystem> = emptyList(),
    val battleEngines: List<BattleEngine> = emptyList(),
    val combatFormulas: io.github.gbkt.core.rpg.CombatFormulas? = null,
    val statSchemas: List<StatSchema> = emptyList(),
    val mapObjectTypes: List<MapObjectTypeDefinition> = emptyList(),
    val genericMapObjects: List<GenericMapObject> = emptyList(),
    val characterClasses: List<CharacterClass> = emptyList(),
    val damageCalculators: List<DamageCalculator> = emptyList(),
    val quests: List<Quest> = emptyList(),
    val questTracker: QuestTracker? = null,
    val shops: List<Shop> = emptyList(),
    val economy: Economy? = null,
    val tileAttributes: List<ExtensibleTileAttributeDefinition> = emptyList(),
    val stringTable: io.github.gbkt.core.ir.StringTable? = null,
    val balanceTables: io.github.gbkt.core.ir.BalanceTable? = null,
) {
    /**
     * Compile the game to C code.
     *
     * Always validates the game before compilation and throws [ValidationException] if validation
     * fails. Use [compileWithValidation] if you need access to the validation result.
     *
     * @param warnOnValidationErrors If true, collects warnings instead of throwing. Useful during
     *   development.
     * @return The generated C code
     * @throws ValidationException if validation fails and warnOnValidationErrors is false
     */
    fun compile(warnOnValidationErrors: Boolean = false): String {
        val result = GameValidator(this).validate()
        if (!result.isValid && !warnOnValidationErrors) {
            result.throwIfInvalid()
        }
        return CodeGenerator(this).generate()
    }

    /** Compile with validation, returning both code and validation result. */
    fun compileWithValidation(): Pair<String, ValidationResult> {
        val result = GameValidator(this).validate()
        val code = CodeGenerator(this).generate()
        return code to result
    }

    /**
     * Compile without validation. FOR TESTING ONLY.
     *
     * This method skips all safety validation and should only be used in unit tests that need to
     * test code generation in isolation without setting up valid game assets.
     *
     * Production code should always use [compile] which validates the game first.
     */
    @Suppress("unused") // Used extensively in tests
    internal fun compileForTest(): String = CodeGenerator(this).generate()
}

data class GameConfig(
    val cartridge: Cartridge = Cartridge.ROM_ONLY,
    val romBanks: Int = 2,
    val ramBanks: Int = 0,
    val gbcSupport: Boolean = false,
    val gbcMode: GBCMode = GBCMode.COMPATIBLE,
    /** Maximum level for all characters (1-255). Default is 99 for classic RPG feel. */
    val maxLevel: Int = 99,
    /** Maximum party size for battles (1-16). */
    val maxPartySize: Int = 4,
    /** Maximum enemy count in battles (1-16). */
    val maxEnemies: Int = 4,
    /** Maximum concurrent tweens (1-64). */
    val maxTweens: Int = 16,
    /** Animation queue size per sprite (1-16). */
    val animationQueueSize: Int = 4,
    /** A* pathfinding maximum open nodes (8-255). */
    val astarMaxNodes: Int = 64,
    /** Maximum path length in tiles (4-128). */
    val pathMaxLength: Int = 32,
)

enum class Cartridge(val gbdk: String) {
    ROM_ONLY("ROM"),
    MBC1("MBC1"),
    MBC1_RAM("MBC1+RAM"),
    MBC1_RAM_BATTERY("MBC1+RAM+BATTERY"),
    MBC3_TIMER_BATTERY("MBC3+TIMER+BATTERY"),
    MBC5("MBC5"),
    MBC5_RAM_BATTERY("MBC5+RAM+BATTERY"),
}
