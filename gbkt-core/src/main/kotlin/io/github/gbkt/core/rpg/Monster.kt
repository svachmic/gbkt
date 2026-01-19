/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.MonsterRef
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RawCodeEscapeHatch
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// MONSTER SYSTEM
// =============================================================================

/**
 * Size categories for monsters in battle.
 *
 * Size affects positioning and how many can appear at once.
 */
enum class MonsterSize {
    /** Small monster (can have 3 in battle) */
    SMALL,

    /** Medium monster (can have 2 in battle) */
    MEDIUM,

    /** Large monster (takes full width, 1 in battle) */
    LARGE,

    /** Boss monster (special positioning) */
    BOSS,
}

/** Power tier for monsters - affects stat scaling and difficulty. */
enum class MonsterTier(val statMultiplier: Int) {
    /** Common/weak monsters */
    C(100),

    /** Uncommon/moderate monsters */
    B(125),

    /** Rare/strong monsters */
    A(150),

    /** Boss-level monsters */
    S(200),
}

/** Base stats for a monster before tier scaling. */
data class MonsterBaseStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val matk: Int = 0,
    val mdef: Int = 0,
    val agl: Int,
)

/** Loot drop definition for a monster. */
data class LootDrop(
    /** The item that can drop */
    val item: Item,
    /** Drop chance as percentage (1-100) */
    val chance: Int,
    /** Quantity range min */
    val minQuantity: Int = 1,
    /** Quantity range max */
    val maxQuantity: Int = 1,
)

// =============================================================================
// MONSTER SPRITE SYSTEM
// =============================================================================

/**
 * Sprite configuration for monster rendering in battle.
 *
 * Monsters are rendered as background tiles (not OAM sprites) because:
 * 1. OAM has a limit of 40 sprites - battles with multiple monsters would exhaust this
 * 2. Original Game Boy games render monsters as background tiles
 * 3. Monsters can be larger than OAM limits (8x16 max per sprite)
 * 4. Tier palette variations work efficiently with background tiles
 *
 * @property assetPath Path to the sprite asset file (e.g., "monsters/goblin.png")
 * @property tileWidth Width in 8px tiles (e.g., 2 = 16px wide)
 * @property tileHeight Height in 8px tiles (e.g., 2 = 16px tall)
 * @property tierPalettes Map of tier to GBC palette index for color variation
 */
data class MonsterSpriteInfo(
    val assetPath: String,
    val tileWidth: Int = 2,
    val tileHeight: Int = 2,
    val tierPalettes: Map<MonsterTier, Int> = emptyMap(),
)

/**
 * Builder for monster sprite configuration.
 *
 * Usage within monster definition:
 * ```kotlin
 * val goblin by monster {
 *     name("Goblin")
 *     sprite(SpriteAsset("monsters/goblin.png")) {
 *         tileSize = 2 x 2  // 16x16 pixels
 *         palettes(c = 0, b = 1, a = 2, s = 3)
 *     }
 * }
 * ```
 *
 * For bosses:
 * ```kotlin
 * val dragon by monster {
 *     name("Dragon")
 *     size(MonsterSize.BOSS)
 *     sprite(SpriteAsset("monsters/dragon.png")) {
 *         tileSize = 4 x 4  // 32x32 pixels for boss
 *         palettes(s = 7)   // Custom palette for S-tier
 *     }
 * }
 * ```
 */
@GbktDsl
class MonsterSpriteBuilder(private val asset: SpriteAsset) {
    /**
     * Size of the sprite in 8px tiles.
     *
     * Common sizes:
     * - 2 x 2 = 16x16 pixels (small monsters)
     * - 2 x 4 = 16x32 pixels (medium monsters)
     * - 4 x 4 = 32x32 pixels (large/boss monsters)
     */
    var tileSize: Dimensions = Dimensions(2, 2)

    private val tierPalettes = mutableMapOf<MonsterTier, Int>()

    /**
     * Configure palette indices for each monster tier.
     *
     * On GBC, different tiers can use different background palettes for visual variety. Defaults to
     * palette 0 for all tiers if not specified.
     *
     * @param c Palette index for tier C (common) monsters
     * @param b Palette index for tier B (uncommon) monsters
     * @param a Palette index for tier A (rare) monsters
     * @param s Palette index for tier S (boss) monsters
     */
    fun palettes(c: Int = 0, b: Int = 1, a: Int = 2, s: Int = 3) {
        require(c in 0..7) { "Palette index must be 0-7" }
        require(b in 0..7) { "Palette index must be 0-7" }
        require(a in 0..7) { "Palette index must be 0-7" }
        require(s in 0..7) { "Palette index must be 0-7" }

        tierPalettes[MonsterTier.C] = c
        tierPalettes[MonsterTier.B] = b
        tierPalettes[MonsterTier.A] = a
        tierPalettes[MonsterTier.S] = s
    }

    internal fun build(): MonsterSpriteInfo =
        MonsterSpriteInfo(
            assetPath = asset.path,
            tileWidth = tileSize.width,
            tileHeight = tileSize.height,
            tierPalettes = tierPalettes.toMap(),
        )
}

/**
 * A monster definition in the game.
 *
 * Monsters are enemies that can be encountered in battle. They have stats, AI behavior, and can
 * drop items and experience when defeated.
 *
 * Usage:
 * ```kotlin
 * val kobold by monster {
 *     name("Kobold")
 *     size(MonsterSize.SMALL)
 *     tier(MonsterTier.C)
 *
 *     baseStats {
 *         hp(20); atk(5); def(3); agl(8)
 *     }
 *
 *     aspects {
 *         vulnerable(Aspect.FIRE)
 *         resist(Aspect.DARK)
 *     }
 *
 *     ai { context ->
 *         if (context.hpPercent < 25) {
 *             flee()
 *         } else {
 *             basicAttack(context.randomTarget())
 *         }
 *     }
 *
 *     exp(15)
 *     drops(potion drop 10.percent)
 * }
 * ```
 */
class Monster(
    /** Unique identifier for this monster (from property name) */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Monster size for battle positioning */
    val size: MonsterSize,
    /** Power tier affecting stats */
    val tier: MonsterTier,
    /** Custom tier multiplier (overrides tier.statMultiplier if set) */
    val customTierMultiplier: Int? = null,
    /** Base stats before tier scaling */
    val baseStats: MonsterBaseStats,
    /** Aspect profile (resistances/vulnerabilities) */
    val aspectProfile: AspectProfile?,
    /** Status effects this monster is immune to */
    val statusImmunities: Set<StatusEffectDefinition>,
    /** AI behavior statements */
    val aiStatements: List<IRStatement>,
    /** Statements executed when the monster is defeated */
    val onDeathStatements: List<IRStatement>,
    /** Statements executed when the monster is about to be hit */
    val onHitStatements: List<IRStatement>,
    /** Experience points awarded on defeat */
    val expReward: Int,
    /** Possible loot drops */
    val lootDrops: List<LootDrop>,
    /** Sprite asset for the monster */
    val sprite: SpriteAsset? = null,
    /** Sprite configuration for rendering (tile size, palettes) */
    val spriteInfo: MonsterSpriteInfo? = null,
    /** Monster index for code generation (assigned by GameBuilder) */
    var monsterIndex: Int = -1,
) {
    /** Type-safe reference to this monster */
    val ref: MonsterRef
        get() = MonsterRef(id)

    /**
     * The effective stat multiplier for this monster.
     *
     * Uses customTierMultiplier if set, otherwise falls back to tier.statMultiplier.
     */
    val effectiveStatMultiplier: Int
        get() = customTierMultiplier ?: tier.statMultiplier

    /** Scaled HP based on tier */
    val scaledHp: Int
        get() = baseStats.hp * effectiveStatMultiplier / 100

    /** Scaled ATK based on tier */
    val scaledAtk: Int
        get() = baseStats.atk * effectiveStatMultiplier / 100

    /** Scaled DEF based on tier */
    val scaledDef: Int
        get() = baseStats.def * effectiveStatMultiplier / 100

    /** Scaled MATK based on tier */
    val scaledMatk: Int
        get() = baseStats.matk * effectiveStatMultiplier / 100

    /** Scaled MDEF based on tier */
    val scaledMdef: Int
        get() = baseStats.mdef * effectiveStatMultiplier / 100

    /** Scaled AGL based on tier */
    val scaledAgl: Int
        get() = baseStats.agl * effectiveStatMultiplier / 100

    /** Get damage modifier for an aspect */
    fun getAspectModifier(aspect: Aspect): DamageModifier =
        aspectProfile?.getModifier(aspect) ?: DamageModifier.NORMAL

    /** Check if immune to a status effect */
    fun isImmuneToStatus(effect: StatusEffectDefinition): Boolean = effect in statusImmunities
}

// =============================================================================
// MONSTER VARIANTS
// =============================================================================

/**
 * A monster variant at a specific tier.
 *
 * Variants share the base monster's AI, drops, and aspect profile but have different stat scaling
 * based on their tier override. This allows the same monster type to appear at different difficulty
 * levels in encounters.
 *
 * Usage:
 * ```kotlin
 * val kobold by monster { /* base definition with tier C */ }
 *
 * // Create variants at different tiers
 * val koboldB = kobold.atTier(MonsterTier.B)  // 125% stats
 * val koboldA = kobold.atTier(MonsterTier.A)  // 150% stats
 *
 * // Use in encounters
 * encounters {
 *     entry(weight = 50) { +kobold }     // Common tier C
 *     entry(weight = 30) { +koboldB }    // Uncommon tier B
 *     entry(weight = 20) { +koboldA }    // Rare tier A
 * }
 * ```
 */
data class MonsterVariant(
    /** The base monster definition */
    val baseMonster: Monster,
    /** Tier override (null uses base monster's tier) */
    val tierOverride: MonsterTier?,
    /** Custom multiplier override (null uses tierOverride's multiplier) */
    val multiplierOverride: Int?,
) {
    /** Variant index for code generation (assigned during codegen). */
    var variantIndex: Int = -1
    /**
     * The effective stat multiplier for this variant.
     *
     * Priority: multiplierOverride > tierOverride.statMultiplier >
     * baseMonster.effectiveStatMultiplier
     */
    val effectiveMultiplier: Int
        get() =
            multiplierOverride
                ?: tierOverride?.statMultiplier
                ?: baseMonster.effectiveStatMultiplier

    /**
     * Unique variant identifier.
     *
     * Format: "baseId_tierX" or "baseId_tXXX" for custom multiplier
     */
    val variantId: String
        get() =
            when {
                multiplierOverride != null -> "${baseMonster.id}_t${multiplierOverride}"
                tierOverride != null -> "${baseMonster.id}_${tierOverride.name.lowercase()}"
                else -> baseMonster.id
            }

    /** Display name including tier indicator */
    val displayName: String
        get() =
            when {
                multiplierOverride != null -> "${baseMonster.displayName} (${multiplierOverride}%)"
                tierOverride != null -> "${baseMonster.displayName} (${tierOverride.name})"
                else -> baseMonster.displayName
            }

    // Scaled stats using variant's effective multiplier
    val scaledHp: Int
        get() = baseMonster.baseStats.hp * effectiveMultiplier / 100

    val scaledAtk: Int
        get() = baseMonster.baseStats.atk * effectiveMultiplier / 100

    val scaledDef: Int
        get() = baseMonster.baseStats.def * effectiveMultiplier / 100

    val scaledMatk: Int
        get() = baseMonster.baseStats.matk * effectiveMultiplier / 100

    val scaledMdef: Int
        get() = baseMonster.baseStats.mdef * effectiveMultiplier / 100

    val scaledAgl: Int
        get() = baseMonster.baseStats.agl * effectiveMultiplier / 100

    /** Scaled experience reward based on tier */
    val scaledExpReward: Int
        get() = baseMonster.expReward * effectiveMultiplier / 100

    /** Whether this is the same as the base monster (no override) */
    val isBaseVariant: Boolean
        get() = tierOverride == null && multiplierOverride == null
}

/**
 * Create a variant of this monster at a specific tier.
 *
 * The variant shares this monster's AI, drops, and aspect profile but has stats scaled according to
 * the specified tier.
 *
 * @param tier The tier for the variant
 * @return A MonsterVariant with the specified tier
 */
fun Monster.atTier(tier: MonsterTier): MonsterVariant = MonsterVariant(this, tier, null)

/**
 * Create a variant of this monster with a custom stat multiplier.
 *
 * The variant shares this monster's AI, drops, and aspect profile but has stats scaled by the
 * specified multiplier.
 *
 * @param multiplier Custom stat multiplier (100 = 100% = base stats)
 * @return A MonsterVariant with the specified multiplier
 */
fun Monster.atTier(multiplier: Int): MonsterVariant {
    require(multiplier > 0) { "Multiplier must be positive" }
    return MonsterVariant(this, null, multiplier)
}

/**
 * Convert a monster to its base variant representation.
 *
 * This is useful when you want to treat a Monster uniformly with MonsterVariants.
 */
fun Monster.toVariant(): MonsterVariant = MonsterVariant(this, null, null)

// =============================================================================
// MONSTER BUILDER
// =============================================================================

/**
 * Property delegate for monsters.
 *
 * Usage: val kobold by monster { ... }
 */
class MonsterDelegate(
    private val gameBuilder: GameBuilder,
    private val init: MonsterBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Monster>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Monster> {
        val builder = MonsterBuilder(property.name)
        builder.init()
        val monster = builder.build()
        gameBuilder.registerMonster(monster)

        return ReadOnlyProperty { _, _ -> monster }
    }
}

/** Builder for monster definitions. */
@GbktDsl
class MonsterBuilder(private val monsterId: String) {
    private var displayName: String = monsterId.replaceFirstChar { it.uppercaseChar() }
    private var size: MonsterSize = MonsterSize.SMALL
    private var tier: MonsterTier = MonsterTier.C
    private var customTierMultiplier: Int? = null
    private var baseStats: MonsterBaseStats? = null
    private var aspectProfile: AspectProfile? = null
    private val statusImmunities = mutableSetOf<StatusEffectDefinition>()
    private var aiStatements: List<IRStatement> = emptyList()
    private var onDeathStatements: List<IRStatement> = emptyList()
    private var onHitStatements: List<IRStatement> = emptyList()
    private var expReward: Int = 0
    private val lootDrops = mutableListOf<LootDrop>()
    private var sprite: SpriteAsset? = null
    private var spriteInfo: MonsterSpriteInfo? = null

    /** Set the display name of the monster. */
    fun name(name: String) {
        displayName = name
    }

    /** Set the monster size. */
    fun size(size: MonsterSize) {
        this.size = size
    }

    /** Set the monster power tier. */
    fun tier(tier: MonsterTier) {
        this.tier = tier
        this.customTierMultiplier = null
    }

    /**
     * Set a custom stat multiplier for this monster.
     *
     * This allows for more granular control over monster power than the predefined tiers. The
     * multiplier is a percentage where 100 = normal, 150 = 50% stronger, etc.
     *
     * Usage:
     * ```kotlin
     * val eliteGuard by monster {
     *     tier(130)  // 30% stronger than base stats
     *     baseStats { hp(50); atk(15); def(12); agl(10) }
     * }
     * ```
     *
     * @param multiplier Stat scaling percentage (100 = base, 200 = 2x stats)
     */
    fun tier(multiplier: Int) {
        require(multiplier > 0) { "Tier multiplier must be positive" }
        this.customTierMultiplier = multiplier
    }

    /** Define base stats for the monster. */
    fun baseStats(init: MonsterStatsBuilder.() -> Unit) {
        val builder = MonsterStatsBuilder()
        builder.init()
        baseStats = builder.build()
    }

    /** Define aspect resistances and vulnerabilities. */
    fun aspects(init: AspectProfileBuilder.() -> Unit) {
        val builder = AspectProfileBuilder(monsterId)
        builder.init()
        aspectProfile = builder.build()
    }

    /** Add status effect immunity. */
    fun immune(vararg effects: StatusEffectDefinition) {
        statusImmunities.addAll(effects)
    }

    /**
     * Define AI behavior for the monster's turn.
     *
     * Usage:
     * ```kotlin
     * ai { context ->
     *     when {
     *         context.hpPercent < 25 -> flee()
     *         context.hpPercent < 50 -> heal()
     *         else -> basicAttack(context.randomTarget())
     *     }
     * }
     * ```
     */
    fun ai(init: MonsterAIScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) {
            val scope = MonsterAIScope(monsterId)
            scope.init()
        }
        aiStatements = recorder.statements
    }

    /**
     * Define behavior when the monster is defeated.
     *
     * The death hook is called when the monster's HP reaches 0. It can be used to:
     * - Revive the monster at a percentage of max HP
     * - Transform into a different monster type
     * - Award bonus experience
     * - Set flags for story progression
     *
     * If the monster is revived during the death hook, it will NOT be removed from battle.
     *
     * Usage:
     * ```kotlin
     * onDeath {
     *     chance(33) {
     *         revive(hpPercent = 25) // 33% chance to revive at 25% HP
     *     }
     * }
     * ```
     * ```kotlin
     * onDeath {
     *     awardBonusExp(50)  // Always award 50 bonus EXP
     *     chance(10) {
     *         transformTo(powerfulForm)  // 10% chance to transform
     *     }
     * }
     * ```
     */
    fun onDeath(init: MonsterDeathScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) {
            val scope = MonsterDeathScope(monsterId)
            scope.init()
        }
        onDeathStatements = recorder.statements
    }

    /**
     * Define behavior when the monster is about to be hit.
     *
     * The hit hook is called before damage is applied to the monster. It can be used to:
     * - Cancel the hit entirely (monster evades)
     * - Modify the incoming damage
     * - Decrement evasion counters (for phasing/evasion mechanics)
     *
     * The hook runs in the context of a hit, with access to the monster slot and incoming damage
     * amount. If the hit is cancelled, no damage is applied.
     *
     * Usage:
     * ```kotlin
     * onHit {
     *     // First 3 hits are automatically evaded
     *     hasEvasion {
     *         decrementEvasion()
     *         cancelHit()
     *     }
     * }
     * ```
     * ```kotlin
     * onHit {
     *     chance(50) {
     *         cancelHit()  // 50% chance to evade
     *     }
     * }
     * ```
     * ```kotlin
     * onHit {
     *     modifyDamage(50)  // Take only 50% damage
     * }
     * ```
     */
    fun onHit(init: MonsterHitScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) {
            val scope = MonsterHitScope(monsterId)
            scope.init()
        }
        onHitStatements = recorder.statements
    }

    /** Set experience reward for defeating this monster. */
    fun exp(amount: Int) {
        require(amount >= 0) { "Experience reward must be non-negative" }
        expReward = amount
    }

    /** Add a loot drop. */
    fun drops(vararg drops: LootDrop) {
        lootDrops.addAll(drops)
    }

    /** Add a loot drop with builder. */
    fun drops(init: LootDropBuilder.() -> Unit) {
        val builder = LootDropBuilder()
        builder.init()
        lootDrops.addAll(builder.build())
    }

    /**
     * Set the sprite asset for this monster.
     *
     * Simple usage (backwards compatible):
     * ```kotlin
     * sprite(SpriteAsset("monsters/goblin.png"))
     * ```
     *
     * Extended usage with configuration:
     * ```kotlin
     * sprite(SpriteAsset("monsters/dragon.png")) {
     *     tileSize = 4 x 4  // 32x32 pixels for boss
     *     palettes(s = 7)   // Custom palette for S-tier
     * }
     * ```
     *
     * @param asset The sprite asset reference
     * @param init Optional configuration block for tile size and palettes
     */
    fun sprite(asset: SpriteAsset, init: MonsterSpriteBuilder.() -> Unit = {}) {
        sprite = asset
        val builder = MonsterSpriteBuilder(asset)
        builder.init()
        spriteInfo = builder.build()
    }

    internal fun build(): Monster {
        val stats = requireNotNull(baseStats) { "Monster '$monsterId' must have baseStats defined" }

        return Monster(
            id = monsterId,
            displayName = displayName,
            size = size,
            tier = tier,
            customTierMultiplier = customTierMultiplier,
            baseStats = stats,
            aspectProfile = aspectProfile,
            statusImmunities = statusImmunities.toSet(),
            aiStatements = aiStatements,
            onDeathStatements = onDeathStatements,
            onHitStatements = onHitStatements,
            expReward = expReward,
            lootDrops = lootDrops.toList(),
            sprite = sprite,
            spriteInfo = spriteInfo,
        )
    }
}

/** Builder for monster base stats. */
@GbktDsl
class MonsterStatsBuilder {
    private var hp: Int = 10
    private var atk: Int = 5
    private var def: Int = 5
    private var matk: Int = 0
    private var mdef: Int = 0
    private var agl: Int = 5

    fun hp(value: Int) {
        require(value > 0) { "HP must be positive" }
        hp = value
    }

    fun atk(value: Int) {
        require(value >= 0) { "ATK must be non-negative" }
        atk = value
    }

    fun def(value: Int) {
        require(value >= 0) { "DEF must be non-negative" }
        def = value
    }

    fun matk(value: Int) {
        require(value >= 0) { "MATK must be non-negative" }
        matk = value
    }

    fun mdef(value: Int) {
        require(value >= 0) { "MDEF must be non-negative" }
        mdef = value
    }

    fun agl(value: Int) {
        require(value >= 0) { "AGL must be non-negative" }
        agl = value
    }

    internal fun build() = MonsterBaseStats(hp, atk, def, matk, mdef, agl)
}

/** Builder for loot drops. */
@GbktDsl
class LootDropBuilder {
    private val drops = mutableListOf<LootDrop>()

    /** Add a drop with percentage chance. */
    fun drop(item: Item, chance: Int, minQty: Int = 1, maxQty: Int = 1) {
        require(chance in 1..100) { "Drop chance must be 1-100" }
        require(minQty >= 1) { "Min quantity must be at least 1" }
        require(maxQty >= minQty) { "Max quantity must be >= min quantity" }
        drops.add(LootDrop(item, chance, minQty, maxQty))
    }

    internal fun build(): List<LootDrop> = drops.toList()
}

// =============================================================================
// MONSTER AI SCOPE
// =============================================================================

/**
 * Scope for defining monster AI behavior.
 *
 * Provides helper methods for conditional checks and actions. All conditionals are evaluated at
 * runtime in the generated code.
 *
 * **Usage**:
 * ```kotlin
 * ai {
 *     // Conditional actions with helper methods
 *     hpBelow(25) { flee() }
 *     hpAbove(75) { basicAttackRandom() }
 *     hasAlly { defend() }
 *     enemyCountIs(1) { basicAttack(context.weakestEnemy) }
 *     chance(30) { useAbility("fireball") }
 *
 *     // Target selection with context
 *     basicAttack(context.randomTarget)
 *     basicAttack(context.weakestEnemy)
 *     basicAttack(context.strongestEnemy)
 *
 *     // Direct actions
 *     basicAttackRandom()
 *     useAbility("powerStrike")
 *     defend()
 *     flee()
 * }
 * ```
 *
 * **Important**: Do NOT use Kotlin `when`/`if` with context properties directly. Context properties
 * are placeholders that don't work in Kotlin conditionals. Always use the helper methods like
 * `hpBelow()`, `hasAlly()`, `chance()` etc.
 */
@GbktDsl
class MonsterAIScope(private val monsterId: String) {
    /**
     * Battle context providing access to HP, targets, and battle state.
     *
     * The context values are resolved at runtime by the codegen using `_ai_ctx_*` placeholder
     * variables.
     */
    val context: MonsterAIContext = MonsterAIContext(monsterId)

    /** Perform a basic physical attack on a target. */
    fun basicAttack(targetName: String) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterBasicAttack(monsterId, targetName))
    }

    /** Perform a basic attack on a random player character. */
    fun basicAttackRandom() {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterBasicAttack(monsterId, null))
    }

    /**
     * Perform a basic attack on the target from context.
     *
     * @param target Target expression from context (e.g., `context.weakestEnemy`)
     */
    fun basicAttack(target: AITargetExpression) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterBasicAttackExpr(monsterId, target.expressionCode))
    }

    /** Use an ability on a target. */
    fun useAbility(abilityId: String, targetName: String? = null) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterUseAbility(monsterId, abilityId, targetName))
    }

    /** Use an ability on a target from context. */
    fun useAbility(abilityId: String, target: AITargetExpression) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRMonsterUseAbilityExpr(
                    monsterId,
                    abilityId,
                    target.expressionCode,
                )
            )
    }

    /** Attempt to flee from battle. */
    fun flee() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRMonsterFlee(monsterId))
    }

    /** Defend (reduce incoming damage this turn). */
    fun defend() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRMonsterDefend(monsterId))
    }

    /** Do nothing this turn. */
    fun skip() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRMonsterSkipTurn(monsterId))
    }

    /** Check if HP is below a threshold. */
    fun hpBelow(percent: Int, action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRMonsterHpCheck(monsterId, percent, below = true)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Check if HP is above a threshold. */
    fun hpAbove(percent: Int, action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRMonsterHpCheck(monsterId, percent, below = false)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Random chance action. */
    fun chance(percent: Int, action: () -> Unit) {
        require(percent in 1..100) { "Chance must be 1-100" }
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRRandomChance(percent)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Conditional action based on ally presence. */
    fun hasAlly(action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRMonsterHasAlly(monsterId)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Conditional action based on enemy count. */
    fun enemyCountIs(count: Int, action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRAIEnemyCountCheck(count)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Raw C code for complex AI logic. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }

    // =========================================================================
    // ONE-TIME SPECIAL ABILITY SUPPORT
    // =========================================================================

    /**
     * Check if the monster has NOT used its special ability yet.
     *
     * Monsters have a special ability flag stored in their parameter byte. This flag is cleared at
     * battle start and can be set once with [useSpecialCharge].
     *
     * Usage:
     * ```kotlin
     * ai {
     *     hasSpecialCharge {
     *         chance(30) {
     *             useSpecialCharge()
     *             useAbility("poison_bite")
     *         }
     *     }
     *     basicAttackRandom()  // Fallback after special is used
     * }
     * ```
     *
     * @param action The action to perform if the special ability hasn't been used yet
     */
    fun hasSpecialCharge(action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRMonsterHasSpecialCharge(monsterId)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /**
     * Mark that the monster has used its special ability.
     *
     * Once called, subsequent [hasSpecialCharge] checks will return false for this monster instance
     * for the rest of the battle.
     *
     * Usage:
     * ```kotlin
     * ai {
     *     hasSpecialCharge {
     *         useSpecialCharge()  // Mark as used
     *         useAbility("poison_bite")
     *     }
     *     basicAttackRandom()
     * }
     * ```
     */
    fun useSpecialCharge() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRMonsterUseSpecialCharge(monsterId))
    }

    // =========================================================================
    // TARGET STATE CHECKS
    // =========================================================================

    /**
     * Check if the current target has a specific status effect active.
     *
     * Useful for AI patterns that react to the player's status, such as special finisher attacks
     * that only work on debuffed targets.
     *
     * Usage:
     * ```kotlin
     * ai {
     *     // Phase 1: Apply confusion
     *     hasSpecialCharge {
     *         chance(30) {
     *             useSpecialCharge()
     *             useAbility("mind_blast")  // Applies confusion
     *         }
     *     }
     *
     *     // Phase 2: Instakill confused target
     *     targetHasEffect("confused") {
     *         useAbility("extract_brain")  // Near-instakill
     *     }
     *
     *     basicAttackRandom()
     * }
     * ```
     *
     * @param effectId The ID of the status effect to check for
     * @param action The action to perform if the target has the effect
     */
    fun targetHasEffect(effectId: String, action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRMonsterTargetHasEffect(monsterId, effectId)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }
}

/**
 * Provides runtime battle context for monster AI decisions.
 *
 * **Important**: For conditional checks (HP thresholds, ally presence, etc.), use the helper
 * methods in [MonsterAIScope] instead of accessing context properties directly in Kotlin
 * conditionals:
 * ```kotlin
 * ai {
 *     // CORRECT: Use helper methods for conditionals
 *     hpBelow(25) { flee() }
 *     hasAlly { defend() }
 *     chance(30) { useAbility("fireball") }
 *
 *     // CORRECT: Use context for target selection
 *     basicAttack(context.weakestEnemy)
 *     basicAttack(context.randomTarget)
 *
 *     // WRONG: Don't use context properties in Kotlin if/when
 *     // These are evaluated at DSL build time, not runtime!
 *     // if (context.hpPercent < 25) { ... } // Won't work correctly!
 * }
 * ```
 *
 * Target expression properties ([randomTarget], [weakestEnemy], etc.) are safe to use as they
 * return expressions evaluated in generated code.
 */
@GbktDsl
class MonsterAIContext(private val monsterId: String) {
    /**
     * Get a random target from alive enemies (player party).
     *
     * Returns a target expression usable with `basicAttack(context.randomTarget)`.
     *
     * Example:
     * ```kotlin
     * basicAttack(context.randomTarget)
     * ```
     */
    val randomTarget: AITargetExpression
        get() = AITargetExpression("_ai_random_target()")

    /**
     * Get the enemy with lowest HP (player party).
     *
     * Returns a target expression for "focus fire" AI pattern.
     *
     * Example:
     * ```kotlin
     * basicAttack(context.weakestEnemy)
     * ```
     */
    val weakestEnemy: AITargetExpression
        get() = AITargetExpression("_ai_weakest_target()")

    /**
     * Get the enemy with highest ATK (player party).
     *
     * Returns a target expression for "eliminate threats" AI pattern.
     *
     * Example:
     * ```kotlin
     * basicAttack(context.strongestEnemy)
     * ```
     */
    val strongestEnemy: AITargetExpression
        get() = AITargetExpression("_ai_strongest_target()")

    /**
     * Get the first valid target (player party, lowest index).
     *
     * Returns a target expression for simple/predictable AI.
     *
     * Example:
     * ```kotlin
     * basicAttack(context.firstTarget)
     * ```
     */
    val firstTarget: AITargetExpression
        get() = AITargetExpression("_ai_first_target()")
}

/**
 * Represents a target expression for monster AI actions.
 *
 * Used to defer target resolution to runtime based on battle state.
 */
data class AITargetExpression(val expressionCode: String)

// =============================================================================
// MONSTER DEATH SCOPE
// =============================================================================

/**
 * Scope for defining monster death behavior.
 *
 * Provides methods for actions that can occur when a monster is defeated, such as reviving,
 * transforming, or awarding bonus experience.
 *
 * **Usage:**
 *
 * ```kotlin
 * onDeath {
 *     chance(33) {
 *         revive(hpPercent = 25)  // 33% chance to revive at 25% HP
 *     }
 * }
 * ```
 * ```kotlin
 * onDeath {
 *     awardBonusExp(100)  // Award 100 bonus EXP
 *     transformTo(phoenixReborn)  // Transform into different monster
 * }
 * ```
 */
@GbktDsl
class MonsterDeathScope(private val monsterId: String) {
    /**
     * Revive the monster at a percentage of its max HP.
     *
     * When called, the monster will NOT be removed from battle. Instead, its HP will be restored to
     * the specified percentage of max HP.
     *
     * @param hpPercent Percentage of max HP to restore (1-100)
     */
    fun revive(hpPercent: Int) {
        require(hpPercent in 1..100) { "HP percent must be 1-100" }
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterRevive(monsterId, hpPercent))
    }

    /**
     * Transform the monster into a different monster type.
     *
     * The current monster is replaced with the specified monster type, initialized at full HP. This
     * can be used for multi-phase boss battles.
     *
     * @param target The monster definition to transform into
     */
    fun transformTo(target: Monster) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterTransform(monsterId, target.id))
    }

    /**
     * Award bonus experience points on death.
     *
     * This is added to the base experience reward for the monster.
     *
     * @param amount Extra experience to award
     */
    fun awardBonusExp(amount: Int) {
        require(amount > 0) { "Bonus EXP must be positive" }
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterAwardBonusExp(monsterId, amount))
    }

    /**
     * Random chance action within death hook.
     *
     * @param percent Probability of executing the action (1-100)
     * @param action The action to perform if the roll succeeds
     */
    fun chance(percent: Int, action: () -> Unit) {
        require(percent in 1..100) { "Chance must be 1-100" }
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRRandomChance(percent)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Raw C code for complex death hook logic. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }
}

// =============================================================================
// MONSTER HIT SCOPE
// =============================================================================

/**
 * Scope for defining monster hit behavior.
 *
 * Provides methods for actions that can occur when a monster is about to be hit, such as evading
 * the hit or modifying the damage.
 *
 * **Usage:**
 *
 * ```kotlin
 * onHit {
 *     hasEvasion {
 *         decrementEvasion()
 *         cancelHit()  // Evade if evasion counter > 0
 *     }
 * }
 * ```
 * ```kotlin
 * onHit {
 *     chance(25) {
 *         cancelHit()  // 25% chance to evade
 *     }
 * }
 * ```
 * ```kotlin
 * onHit {
 *     modifyDamage(50)  // Always take 50% damage
 * }
 * ```
 */
@GbktDsl
class MonsterHitScope(private val monsterId: String) {
    /**
     * Cancel the hit entirely (monster evades).
     *
     * When called, the incoming damage will not be applied to the monster. This can be used for
     * evasion mechanics.
     */
    fun cancelHit() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRMonsterCancelHit(monsterId))
    }

    /**
     * Modify the incoming damage by a percentage.
     *
     * @param multiplier Damage multiplier as percentage (50 = halve damage, 200 = double damage)
     */
    fun modifyDamage(multiplier: Int) {
        require(multiplier >= 0) { "Damage multiplier must be non-negative" }
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRMonsterModifyHitDamage(monsterId, multiplier))
    }

    /**
     * Decrement the monster's evasion counter.
     *
     * Used for phasing mechanics where the monster evades a certain number of hits. Call
     * [cancelHit] after this to actually evade the attack.
     */
    fun decrementEvasion() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRMonsterDecrementEvasion(monsterId))
    }

    /**
     * Conditional action if the monster has evasion charges remaining.
     *
     * @param action The action to perform if evasion counter > 0
     */
    fun hasEvasion(action: () -> Unit) {
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRMonsterHasEvasion(monsterId)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /**
     * Random chance action within hit hook.
     *
     * @param percent Probability of executing the action (1-100)
     * @param action The action to perform if the roll succeeds
     */
    fun chance(percent: Int, action: () -> Unit) {
        require(percent in 1..100) { "Chance must be 1-100" }
        val innerRecorder = StatementRecorder()
        RecordingContext.record(innerRecorder, action)
        val condition = io.github.gbkt.core.ir.IRRandomChance(percent)
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRIf(condition, innerRecorder.statements))
    }

    /** Raw C code for complex hit hook logic. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }
}

// =============================================================================
// LOOT DROP DSL HELPERS
// =============================================================================

/**
 * Helper for creating loot drops with infix syntax.
 *
 * Usage: potion drop 10.percent
 */
infix fun Item.drop(chance: DropChance): LootDrop = LootDrop(this, chance.percent)

/** Wrapper for drop chance percentage. */
data class DropChance(val percent: Int)

/** Convert Int to DropChance. Usage: 10.percent */
val Int.percent: DropChance
    get() {
        require(this in 1..100) { "Drop chance must be 1-100" }
        return DropChance(this)
    }

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/**
 * Create a monster definition.
 *
 * Usage:
 * ```kotlin
 * val kobold by monster {
 *     name("Kobold")
 *     size(MonsterSize.SMALL)
 *     tier(MonsterTier.C)
 *     baseStats { hp(20); atk(5); def(3); agl(8) }
 *     exp(15)
 * }
 * ```
 */
fun GameBuilder.monster(init: MonsterBuilder.() -> Unit): MonsterDelegate {
    return MonsterDelegate(this, init)
}
