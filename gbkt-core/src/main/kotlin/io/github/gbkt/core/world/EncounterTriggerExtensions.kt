/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.builder.GameBuilder
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// ENCOUNTER TRIGGER PROPERTY DELEGATES
// =============================================================================

/** Property delegate for step-based triggers. */
class StepTriggerDelegate(
    private val gameBuilder: GameBuilder,
    private val init: StepTriggerBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, StepBasedTrigger>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, StepBasedTrigger> {
        val builder = StepTriggerBuilder(property.name)
        builder.init()
        val trigger = builder.build()
        gameBuilder.registerEncounterTrigger(trigger)

        return ReadOnlyProperty { _, _ -> trigger }
    }
}

/** Property delegate for time-based triggers. */
class TimeTriggerDelegate(
    private val gameBuilder: GameBuilder,
    private val init: TimeTriggerBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, TimeBasedTrigger>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, TimeBasedTrigger> {
        val builder = TimeTriggerBuilder(property.name)
        builder.init()
        val trigger = builder.build()
        gameBuilder.registerEncounterTrigger(trigger)

        return ReadOnlyProperty { _, _ -> trigger }
    }
}

/** Property delegate for region-based triggers. */
class RegionTriggerDelegate(
    private val gameBuilder: GameBuilder,
    private val init: RegionTriggerBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, RegionBasedTrigger>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, RegionBasedTrigger> {
        val builder = RegionTriggerBuilder(property.name)
        builder.init()
        val trigger = builder.build()
        gameBuilder.registerEncounterTrigger(trigger)

        return ReadOnlyProperty { _, _ -> trigger }
    }
}

/** Property delegate for wave-based triggers. */
class WaveTriggerDelegate(
    private val gameBuilder: GameBuilder,
    private val init: WaveTriggerBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, WaveBasedTrigger>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, WaveBasedTrigger> {
        val builder = WaveTriggerBuilder(property.name)
        builder.init()
        val trigger = builder.build()
        gameBuilder.registerEncounterTrigger(trigger)

        return ReadOnlyProperty { _, _ -> trigger }
    }
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Create a step-based encounter trigger.
 *
 * Traditional JRPG random encounters based on step count.
 *
 * Usage:
 * ```kotlin
 * val trigger by stepTrigger {
 *     safeSteps(10)
 *     initialChance(5)
 *     incrementPerStep(3)
 *     maxChance(100)
 *     encounters(dungeonEncounters)
 *
 *     onTrigger { scene(battleScene) }
 * }
 * ```
 */
fun GameBuilder.stepTrigger(init: StepTriggerBuilder.() -> Unit): StepTriggerDelegate {
    return StepTriggerDelegate(this, init)
}

/**
 * Create a time-based encounter trigger.
 *
 * For games with free-roaming movement where time matters more than steps.
 *
 * Usage:
 * ```kotlin
 * val trigger by timeTrigger {
 *     safeFrames(300)  // 5 seconds at 60fps
 *     checkInterval(60)  // Check every second
 *     baseChance(10)
 *     movingMultiplier(100)
 *     idleMultiplier(50)  // Half rate when standing still
 *
 *     onTrigger { startBattle() }
 * }
 * ```
 */
fun GameBuilder.timeTrigger(init: TimeTriggerBuilder.() -> Unit): TimeTriggerDelegate {
    return TimeTriggerDelegate(this, init)
}

/**
 * Create a region-based encounter trigger.
 *
 * Encounters happen only in specific danger zones.
 *
 * Usage:
 * ```kotlin
 * val trigger by regionTrigger {
 *     dangerZone(x1 = 0, y1 = 0, x2 = 10, y2 = 10, chance = 50)
 *     dangerZone(x1 = 20, y1 = 20, x2 = 30, y2 = 30, chance = 100)
 *     checkInterval(60)
 *
 *     onTrigger { startBattle() }
 * }
 * ```
 */
fun GameBuilder.regionTrigger(init: RegionTriggerBuilder.() -> Unit): RegionTriggerDelegate {
    return RegionTriggerDelegate(this, init)
}

/**
 * Create a wave-based encounter trigger.
 *
 * For arena/survival modes with waves of enemies.
 *
 * Usage:
 * ```kotlin
 * val trigger by waveTrigger {
 *     wave(1, delay = 0) {
 *         monsters("goblin", "goblin")
 *     }
 *     wave(2, delay = 180) {
 *         monsters("goblin", "goblin", "orc")
 *     }
 *     wave(3, delay = 180) {
 *         monster("boss_orc")
 *     }
 *     loop(scaling = 10)  // After wave 3, loop with 10% difficulty increase
 *
 *     onWaveComplete { showWaveMessage() }
 * }
 * ```
 */
fun GameBuilder.waveTrigger(init: WaveTriggerBuilder.() -> Unit): WaveTriggerDelegate {
    return WaveTriggerDelegate(this, init)
}
