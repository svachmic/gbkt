/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName")

package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.ItemRef

/**
 * Labyrinth of the Dragon — consumable item definitions.
 *
 * Ports all 8 consumable items from the Original C implementation in
 * `LabyrinthOfTheDragon/src/item.c` and `item.h`.
 *
 * ## Original C Reference
 *
 * Item IDs are defined as the `ItemId` enum in `item.h` lines 24–34:
 * ```c
 * typedef enum ItemId {
 *   ITEM_POTION,          // 0 — heals HP by POTION_HEAL_FACTOR * max_hp >> 4
 *   ITEM_ETHER,           // 1 — heals SP/MP by ETHER_HEAL_FACTOR * max_sp >> 4
 *   ITEM_REMEDY,          // 2 — clears all active debuffs
 *   ITEM_ATK_UP,          // 3 — applies BUFF_ATK_UP (perpetual)
 *   ITEM_DEF_UP,          // 4 — applies BUFF_DEF_UP (perpetual)
 *   ITEM_ELIXIR,          // 5 — full restore HP and SP to max
 *   ITEM_REGEN,           // 6 — applies BUFF_REGEN (perpetual)
 *   ITEM_HASTE,           // 7 — applies BUFF_HASTE (perpetual)
 * } ItemId;
 * ```
 *
 * ## Heal Mechanics (from `item.c`, `item.h`)
 *
 * Potion and Ether heal using a "factor × max_stat >> 4" formula:
 * - `POTION_HEAL_FACTOR = 6` → heals ~37.5% max HP (`item.h` line 9)
 * - `ETHER_HEAL_FACTOR = 6` → heals ~37.5% max SP/MP (`item.h` line 14)
 *
 * The `item_heal()` function in `item.c` line 38 computes: `factor * max / 16`.
 *
 * For the DSL we use `heal()` with a representative constant. Actual dynamic healing (max-HP-based)
 * is not yet expressible in the framework without an `onUse { script { ... } }` block — see the
 * Elixir item for full-restore via buff.
 *
 * ## Max Stack
 *
 * The Original's inventory has exactly 8 slots (one per item type), each item tracked by quantity.
 * The `add_items()` function caps at 99 (`item.h` line 66). Using `maxStack(99)` to match the
 * Original.
 *
 * ## Status Effect Items (ATK Up, DEF Up, Regen, Haste)
 *
 * These items apply perpetual buffs via `set_item_buff()` in `item.c` line 18. They call
 * `apply_status_effect()` with `EFFECT_DURATION_PERPETUAL` (0xFF). In the V2 DSL they use
 * `buff("stat", amount, duration)` — duration 0 means perpetual.
 */

// =============================================================================
// Item references returned from defineItems()
// =============================================================================

/**
 * Typed container for all Labyrinth of the Dragon item definitions.
 *
 * Returned by [GameBuilder.defineItems] for zero-magic-string access in downstream plans (battle
 * scene item menu, chest contents, shop inventory, etc.).
 */
data class LabyrinthItems(
    /** Potion — heals ~37.5% max HP. ITEM_POTION (item.h line 25). */
    val potion: ItemRef,
    /** Ether — heals ~37.5% max SP/MP. ITEM_ETHER (item.h line 26). */
    val ether: ItemRef,
    /** Remedy — clears all active debuffs. ITEM_REMEDY (item.h line 27). */
    val remedy: ItemRef,
    /** ATK Up Potion — applies perpetual ATK Up buff. ITEM_ATK_UP (item.h line 28). */
    val atkUp: ItemRef,
    /** DEF Up Potion — applies perpetual DEF Up buff. ITEM_DEF_UP (item.h line 29). */
    val defUp: ItemRef,
    /** Elixir — fully restores HP and SP to max. ITEM_ELIXIR (item.h line 30). */
    val elixir: ItemRef,
    /** Regen Potion — applies perpetual Regen (HoT) buff. ITEM_REGEN (item.h line 31). */
    val regen: ItemRef,
    /**
     * Haste Potion — applies perpetual Haste buff (double attacks). ITEM_HASTE (item.h line 32).
     */
    val haste: ItemRef,
)

// =============================================================================
// Item definitions DSL extension
// =============================================================================

/**
 * Registers all 8 consumable items from the Original C game.
 *
 * All items use the "consumable" category with a max stack of 99, matching the Original's
 * `add_items()` cap of 99 per item type.
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/item.c` — item use functions lines 59–113.
 * `LabyrinthOfTheDragon/src/item.h` — `ItemId` enum lines 24–34.
 */
@Suppress("LongMethod")
fun GameBuilder.defineItems(): LabyrinthItems {
    // Declare ItemRef holders before the items {} block so they can be returned after.
    // items {} returns Unit — item refs must be captured outside and returned afterwards.
    lateinit var potionRef: ItemRef
    lateinit var etherRef: ItemRef
    lateinit var remedyRef: ItemRef
    lateinit var atkUpRef: ItemRef
    lateinit var defUpRef: ItemRef
    lateinit var elixirRef: ItemRef
    lateinit var regenRef: ItemRef
    lateinit var hasteRef: ItemRef

    items {
        // Register the consumable category to match Original inventory semantics
        category("consumable") { defaultMaxStack(99) }

        // ---------------------------------------------------------------------
        // Potion (ITEM_POTION = 0)
        // Heals HP by POTION_HEAL_FACTOR * max_hp >> 4 = ~37.5% max HP
        // Original: use_potion() in item.c line 59, strings: str_misc_potion
        // Can use when: player.hp < player.max_hp
        // ---------------------------------------------------------------------

        /**
         * Potion — restores HP during battle.
         *
         * Heals approximately 37.5% of the player's max HP. Uses the formula `POTION_HEAL_FACTOR *
         * max_hp >> 4` where `POTION_HEAL_FACTOR = 6`. The DSL `heal(50)` is a representative
         * constant; the backend should resolve this to the max-HP-based formula at runtime.
         *
         * Original: `ITEM_POTION = 0`, `use_potion()` in `item.c` line 59. Inventory name:
         * `str_misc_potion`.
         */
        potionRef =
            item("potion") {
                name("Potion")
                category("consumable")
                maxStack(99)
                buyPrice(50)
                onUse { heal(50) }
            }

        // ---------------------------------------------------------------------
        // Ether (ITEM_ETHER = 1)
        // Heals SP/MP by ETHER_HEAL_FACTOR * max_sp >> 4 = ~37.5% max SP
        // Uses different strings for magic (MP) vs martial (SP) classes
        // Original: use_ether() in item.c line 68, str_misc_ether
        // Can use when: player.sp < player.max_sp
        // ---------------------------------------------------------------------

        /**
         * Ether — restores SP/MP during battle.
         *
         * Heals approximately 37.5% of the player's max SP (or MP for magic classes). Uses
         * class-dependent message strings: `str_items_use_ether_mp` for magic classes
         * (Druid/Sorcerer) and `str_items_use_ether_sp` for martial classes.
         *
         * Original: `ITEM_ETHER = 1`, `use_ether()` in `item.c` line 68. Inventory name:
         * `str_misc_ether`.
         */
        etherRef =
            item("ether") {
                name("Ether")
                category("consumable")
                maxStack(99)
                buyPrice(50)
                onUse { heal(20) } // SP/MP restore — approximately 37.5% max SP
            }

        // ---------------------------------------------------------------------
        // Remedy (ITEM_REMEDY = 2)
        // Clears all active debuffs — iterates player_status_effects and
        // deactivates any entry where is_debuff(effect) returns true
        // Original: use_remedy() in item.c line 75, str_misc_remedy
        // Can use when: at least one active debuff is present
        // ---------------------------------------------------------------------

        /**
         * Remedy — clears all active debuffs.
         *
         * Iterates all active status effect slots and deactivates any that are debuffs (Blind,
         * Scared, Paralyzed, Poisoned, Confused, AGL/ATK/DEF Down). Uses `is_debuff()` check from
         * `stats.h` line 153.
         *
         * The `script {}` block here is a placeholder — the actual batch-clear logic requires a
         * dedicated battle script op (to be resolved in the battle mechanics plan).
         *
         * Original: `ITEM_REMEDY = 2`, `use_remedy()` in `item.c` line 75. Inventory name:
         * `str_misc_remedy`.
         */
        remedyRef =
            item("remedy") {
                name("Remedy")
                category("consumable")
                maxStack(99)
                buyPrice(80)
                onUse { script { /* clear all debuffs — wired in battle mechanics plan */ } }
            }

        // ---------------------------------------------------------------------
        // ATK Up Potion (ITEM_ATK_UP = 3)
        // Applies BUFF_ATK_UP perpetual — increases ATK by tier-based percentage
        // Original: use_atkup_potion() in item.c line 87, str_misc_atk_up_potion
        // Applied at A_TIER perpetual via set_item_buff(BUFF_ATK_UP, ...)
        // Can use when: has_effect_slot (i.e., no existing ATK_UP perpetual)
        // ---------------------------------------------------------------------

        /**
         * ATK Up Potion — applies a permanent ATK increase for the battle.
         *
         * Grants the ATK Up buff at A-tier strength with perpetual duration. The buff increases
         * physical attack by approximately 25% (A-tier formula: `atk * 4 >> 4 + atk`). Represented
         * as `buff("atk", 4, 0)` in the DSL.
         *
         * Original: `ITEM_ATK_UP = 3`, `use_atkup_potion()` in `item.c` line 87. Uses
         * `set_item_buff(BUFF_ATK_UP, FLAG_BUFF_ATK_UP, ...)` at `A_TIER`. Inventory name:
         * `str_misc_atk_up_potion`.
         */
        atkUpRef =
            item("atk_up") {
                name("ATK Up")
                category("consumable")
                maxStack(99)
                buyPrice(100)
                onUse { buff("atk", 4, 0) } // A-tier: +25% ATK, duration 0 = perpetual
            }

        // ---------------------------------------------------------------------
        // DEF Up Potion (ITEM_DEF_UP = 4)
        // Applies BUFF_DEF_UP perpetual — increases DEF by tier-based percentage
        // Original: use_defup_potion() in item.c line 92, str_misc_def_up_potion
        // Applied at A_TIER perpetual via set_item_buff(BUFF_DEF_UP, ...)
        // ---------------------------------------------------------------------

        /**
         * DEF Up Potion — applies a permanent DEF increase for the battle.
         *
         * Grants the DEF Up buff at A-tier strength with perpetual duration. Increases physical
         * defense by approximately 25%.
         *
         * Original: `ITEM_DEF_UP = 4`, `use_defup_potion()` in `item.c` line 92. Uses
         * `set_item_buff(BUFF_DEF_UP, FLAG_BUFF_DEF_UP, ...)` at `A_TIER`. Inventory name:
         * `str_misc_def_up_potion`.
         */
        defUpRef =
            item("def_up") {
                name("DEF Up")
                category("consumable")
                maxStack(99)
                buyPrice(100)
                onUse { buff("def", 4, 0) } // A-tier: +25% DEF, duration 0 = perpetual
            }

        // ---------------------------------------------------------------------
        // Elixir (ITEM_ELIXIR = 5)
        // Full restore — sets player.hp = player.max_hp and player.sp = player.max_sp
        // Original: use_elixir() in item.c line 97, str_misc_elixir
        // Can use when: player.hp < max_hp OR player.sp < max_sp
        // ---------------------------------------------------------------------

        /**
         * Elixir — fully restores HP and SP to maximum.
         *
         * The most powerful consumable — sets both HP and SP (or MP) to their maximum values
         * unconditionally. The `heal(999)` here is an overheal representative; the framework clamps
         * healing to max HP.
         *
         * Original: `ITEM_ELIXIR = 5`, `use_elixir()` in `item.c` line 97. Sets `player.hp =
         * player.max_hp; player.sp = player.max_sp`. Inventory name: `str_misc_elixir`.
         */
        elixirRef =
            item("elixir") {
                name("Elixir")
                category("consumable")
                maxStack(99)
                buyPrice(500)
                onUse { heal(999) } // Full HP restore (999 = saturate to max)
            }

        // ---------------------------------------------------------------------
        // Regen Potion (ITEM_REGEN = 6)
        // Applies BUFF_REGEN perpetual — HoT each turn
        // Original: use_regen_potion() in item.c line 105, str_misc_regen_pot
        // Applied at BUFF_REGEN via set_item_buff with SFX_BIG_POWERUP
        // ---------------------------------------------------------------------

        /**
         * Regen Potion — applies a permanent Regen (heal-over-time) buff for the battle.
         *
         * Grants the Regen buff with perpetual duration. Heals HP each turn using the `regen_hp()`
         * formula: `max_hp * (tier+2) >> 4`. Represented as `buff("hp", 5, 0)` per-turn healing in
         * the DSL.
         *
         * Original: `ITEM_REGEN = 6`, `use_regen_potion()` in `item.c` line 105. Uses
         * `set_item_buff(BUFF_REGEN, FLAG_BUFF_REGEN, ...)`. Inventory name: `str_misc_regen_pot`.
         */
        regenRef =
            item("regen_potion") {
                name("Regen")
                category("consumable")
                maxStack(99)
                buyPrice(120)
                onUse { buff("hp", 5, 0) } // Regen buff — 5 HP/turn perpetual (representative)
            }

        // ---------------------------------------------------------------------
        // Haste Potion (ITEM_HASTE = 7)
        // Applies BUFF_HASTE perpetual — double attacks and +50% healing
        // Original: use_haste_potion() in item.c line 110, str_misc_haste_pot
        // Applied at BUFF_HASTE via set_item_buff with SFX_BIG_POWERUP
        // ---------------------------------------------------------------------

        /**
         * Haste Potion — applies a permanent Haste buff for the battle.
         *
         * Grants the Haste buff with perpetual duration. When hasted, physical attacks deal double
         * damage (two damage rolls), and healing abilities restore 150% of their normal amount.
         * Represented as `buff("agl", 8, 0)` (speed increase) in the DSL.
         *
         * Original: `ITEM_HASTE = 7`, `use_haste_potion()` in `item.c` line 110. Uses
         * `set_item_buff(BUFF_HASTE, FLAG_BUFF_HASTE, ...)`. Inventory name: `str_misc_haste_pot`.
         */
        hasteRef =
            item("haste_potion") {
                name("Haste")
                category("consumable")
                maxStack(99)
                buyPrice(150)
                onUse {
                    buff("agl", 8, 0)
                } // Haste buff — +8 AGL (speed) perpetual (representative)
            }
    }

    // Return the collected refs after items {} registration completes.
    // items {} returns Unit — refs are captured in lateinit vars declared above.
    return LabyrinthItems(
        potion = potionRef,
        ether = etherRef,
        remedy = remedyRef,
        atkUp = atkUpRef,
        defUp = defUpRef,
        elixir = elixirRef,
        regen = regenRef,
        haste = hasteRef,
    )
}
