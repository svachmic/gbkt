/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.combat

import io.github.gbkt.core.builder.GameBuilder
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// BATTLE ENGINE PROPERTY DELEGATES
// =============================================================================

/** Property delegate for turn-based battle engines. */
class TurnBasedBattleDelegate(
    private val gameBuilder: GameBuilder,
    private val init: TurnBasedBattleBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, TurnBasedBattleEngine>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, TurnBasedBattleEngine> {
        val builder = TurnBasedBattleBuilder(property.name)
        builder.init()
        val engine = builder.build()
        gameBuilder.registerBattleEngine(engine)

        return ReadOnlyProperty { _, _ -> engine }
    }
}

/** Property delegate for active time battle engines. */
class ActiveTimeBattleDelegate(
    private val gameBuilder: GameBuilder,
    private val init: ActiveTimeBattleBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ActiveTimeBattleEngine>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ActiveTimeBattleEngine> {
        val builder = ActiveTimeBattleBuilder(property.name)
        builder.init()
        val engine = builder.build()
        gameBuilder.registerBattleEngine(engine)

        return ReadOnlyProperty { _, _ -> engine }
    }
}

/** Property delegate for real-time battle engines. */
class RealTimeBattleDelegate(
    private val gameBuilder: GameBuilder,
    private val init: RealTimeBattleBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, RealTimeBattleEngine>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, RealTimeBattleEngine> {
        val builder = RealTimeBattleBuilder(property.name)
        builder.init()
        val engine = builder.build()
        gameBuilder.registerBattleEngine(engine)

        return ReadOnlyProperty { _, _ -> engine }
    }
}

/** Property delegate for tactical battle engines. */
class TacticalBattleDelegate(
    private val gameBuilder: GameBuilder,
    private val init: TacticalBattleBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, TacticalBattleEngine>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, TacticalBattleEngine> {
        val builder = TacticalBattleBuilder(property.name)
        builder.init()
        val engine = builder.build()
        gameBuilder.registerBattleEngine(engine)

        return ReadOnlyProperty { _, _ -> engine }
    }
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Create a turn-based battle engine.
 *
 * For traditional JRPG combat with discrete turns and action selection.
 *
 * Usage:
 * ```kotlin
 * val combat by turnBasedBattle {
 *     name("Main Combat")
 *     maxPartySize(4)
 *     maxEnemies(4)
 *     turnOrder(TurnOrderStrategy.SPEED_BASED)
 *     fleeMechanics(baseChance = 50, perAgility = 2)
 *
 *     onVictory { awardExp(); scene(gameplayScene) }
 *     onDefeat { scene(gameOverScene) }
 *     onFlee { scene(gameplayScene) }
 * }
 * ```
 */
fun GameBuilder.turnBasedBattle(init: TurnBasedBattleBuilder.() -> Unit): TurnBasedBattleDelegate {
    return TurnBasedBattleDelegate(this, init)
}

/**
 * Create an active time battle engine.
 *
 * For ATB-style combat where turns are determined by a charging gauge.
 *
 * Usage:
 * ```kotlin
 * val combat by activeTimeBattle {
 *     name("ATB Combat")
 *     maxPartySize(4)
 *     baseFillRate(4)
 *     speedMultiplier(2)
 *     pauseSettings(onMenu = true, onAnimation = false)
 *
 *     onGaugeFull { showActionMenu() }
 *     onVictory { awardExp() }
 * }
 * ```
 */
fun GameBuilder.activeTimeBattle(
    init: ActiveTimeBattleBuilder.() -> Unit
): ActiveTimeBattleDelegate {
    return ActiveTimeBattleDelegate(this, init)
}

/**
 * Create a real-time battle engine.
 *
 * For action combat with hit detection, i-frames, and real-time movement.
 *
 * Usage:
 * ```kotlin
 * val combat by realTimeBattle {
 *     name("Action Combat")
 *     hitStun(10)
 *     invincibility(60)
 *     knockback(8)
 *     blocking(enabled = true, reduction = 50)
 *
 *     onHit { flashSprite(); playSound(hit) }
 *     onBlock { playSound(block) }
 * }
 * ```
 */
fun GameBuilder.realTimeBattle(init: RealTimeBattleBuilder.() -> Unit): RealTimeBattleDelegate {
    return RealTimeBattleDelegate(this, init)
}

/**
 * Create a tactical battle engine.
 *
 * For grid-based tactical combat with movement and positioning.
 *
 * Usage:
 * ```kotlin
 * val combat by tacticalBattle {
 *     name("Tactical Combat")
 *     gridSize(16, 16)
 *     baseMoveRange(4)
 *     facing(enabled = true, flankBonus = 25)
 *     heightBonus(10)
 *     turnOrder(TurnOrderStrategy.SPEED_BASED)
 *
 *     onUnitMove { updateVisibility() }
 *     onUnitAttack { showDamage() }
 * }
 * ```
 */
fun GameBuilder.tacticalBattle(init: TacticalBattleBuilder.() -> Unit): TacticalBattleDelegate {
    return TacticalBattleDelegate(this, init)
}
