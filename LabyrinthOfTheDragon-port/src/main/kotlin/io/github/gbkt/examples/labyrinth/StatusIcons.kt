/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.entity.Entity

/**
 * Status effect icon sprites for battle display.
 *
 * Each combatant (player + 3 monsters) has 4 sprite slots for displaying active status effects. The
 * sprites use tile indices from 0x60-0x72 to show different effect icons (poison, burn, haste,
 * etc.).
 *
 * Palette assignment:
 * - Debuffs (poison, burn, stun, etc.): Palette 7 (red)
 * - Buffs (regen, haste, evasion, etc.): Palette 6 (blue)
 *
 * @property playerIcon1 Player's first status icon slot
 * @property playerIcon2 Player's second status icon slot
 * @property playerIcon3 Player's third status icon slot
 * @property playerIcon4 Player's fourth status icon slot
 * @property monster1Icon1 Monster 1's first status icon slot
 * @property monster1Icon2 Monster 1's second status icon slot
 * @property monster1Icon3 Monster 1's third status icon slot
 * @property monster1Icon4 Monster 1's fourth status icon slot
 * @property monster2Icon1 Monster 2's first status icon slot
 * @property monster2Icon2 Monster 2's second status icon slot
 * @property monster2Icon3 Monster 2's third status icon slot
 * @property monster2Icon4 Monster 2's fourth status icon slot
 * @property monster3Icon1 Monster 3's first status icon slot
 * @property monster3Icon2 Monster 3's second status icon slot
 * @property monster3Icon3 Monster 3's third status icon slot
 * @property monster3Icon4 Monster 3's fourth status icon slot
 */
data class StatusIcons(
    val playerIcon1: Entity,
    val playerIcon2: Entity,
    val playerIcon3: Entity,
    val playerIcon4: Entity,
    val monster1Icon1: Entity,
    val monster1Icon2: Entity,
    val monster1Icon3: Entity,
    val monster1Icon4: Entity,
    val monster2Icon1: Entity,
    val monster2Icon2: Entity,
    val monster2Icon3: Entity,
    val monster2Icon4: Entity,
    val monster3Icon1: Entity,
    val monster3Icon2: Entity,
    val monster3Icon3: Entity,
    val monster3Icon4: Entity,
) {
    /** All player status icon slots as a list for iteration */
    val playerIcons: List<Entity>
        get() = listOf(playerIcon1, playerIcon2, playerIcon3, playerIcon4)

    /** All monster 1 status icon slots as a list for iteration */
    val monster1Icons: List<Entity>
        get() = listOf(monster1Icon1, monster1Icon2, monster1Icon3, monster1Icon4)

    /** All monster 2 status icon slots as a list for iteration */
    val monster2Icons: List<Entity>
        get() = listOf(monster2Icon1, monster2Icon2, monster2Icon3, monster2Icon4)

    /** All monster 3 status icon slots as a list for iteration */
    val monster3Icons: List<Entity>
        get() = listOf(monster3Icon1, monster3Icon2, monster3Icon3, monster3Icon4)

    /** All status icon sprites (16 total) for batch operations */
    val allIcons: List<Entity>
        get() = playerIcons + monster1Icons + monster2Icons + monster3Icons
}
