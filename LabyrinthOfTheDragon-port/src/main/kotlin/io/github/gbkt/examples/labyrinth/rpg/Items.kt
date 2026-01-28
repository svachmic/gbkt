/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.Item
import io.github.gbkt.core.rpg.ItemCategory
import io.github.gbkt.core.rpg.item

// =============================================================================
// LABYRINTH OF THE DRAGON - ITEMS
// =============================================================================
// Consumable items matching the original game.
// Heal factors: HP and SP restoration use (6 * max) >> 4 = 37.5% (POTION_HEAL_FACTOR = 6)

/** Item definitions for the game. Call initItems(builder, effects) to register all items. */
class Items(builder: GameBuilder, private val effects: StatusEffects) {

    // -------------------------------------------------------------------------
    // HEALING ITEMS
    // -------------------------------------------------------------------------

    /**
     * Potion - Restores HP equal to ~37.5% of max HP. The most common healing item found in chests.
     */
    val potion: Item by
        builder.item {
            name("Potion")
            description("Restores some HP")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 50)
            onUse {
                // Original: (6 * max_hp) >> 4 = 6/16 = 37.5%
                healPercent(37)
            }
        }

    /**
     * Ether - Restores SP equal to ~37.5% of max SP. Used to replenish magic/skill points for
     * abilities.
     */
    val ether: Item by
        builder.item {
            name("Ether")
            description("Restores some SP")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 100)
            onUse {
                // Original: (6 * max_sp) >> 4 = 6/16 = 37.5%
                restoreSpPercent(37)
            }
        }

    /** Elixir - Fully restores HP and SP. Rare and valuable item for emergency situations. */
    val elixir: Item by
        builder.item {
            name("Elixir")
            description("Fully restores HP and SP")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 500)
            onUse {
                healPercent(100)
                restoreSpPercent(100)
            }
        }

    // -------------------------------------------------------------------------
    // STATUS ITEMS
    // -------------------------------------------------------------------------

    /**
     * Remedy - Cures all negative status effects. Essential for dealing with poison, paralysis,
     * etc.
     */
    val remedy: Item by
        builder.item {
            name("Remedy")
            description("Cures status ailments")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 75)
            onUse { cureAll() }
        }

    // -------------------------------------------------------------------------
    // BUFF ITEMS
    // -------------------------------------------------------------------------

    /**
     * ATK Up - Temporarily increases attack power. Applies ATK boost status effect for battle
     * duration.
     */
    val atkUp: Item by
        builder.item {
            name("ATK Up")
            description("Boosts ATK temporarily")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 100)
            onUse {
                // Apply ATK Up status effect (25% boost for 5 turns)
                applyEffect(effects.atkUp.definition)
            }
        }

    /**
     * DEF Up - Temporarily increases defense. Applies DEF boost status effect for battle duration.
     */
    val defUp: Item by
        builder.item {
            name("DEF Up")
            description("Boosts DEF temporarily")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 100)
            onUse {
                // Apply DEF Up status effect (25% boost for 5 turns)
                applyEffect(effects.defUp.definition)
            }
        }

    /** Regen - Applies regeneration effect. Heals HP gradually over multiple turns. */
    val regen: Item by
        builder.item {
            name("Regen")
            description("Gradually restores HP")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 150)
            onUse {
                // Apply Regen status effect (8 HP per turn for 5 turns = 40 HP total)
                applyEffect(effects.regen.definition)
            }
        }

    /** Haste - Increases speed/agility temporarily. Allows acting earlier in turn order. */
    val haste: Item by
        builder.item {
            name("Haste")
            description("Boosts speed temporarily")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 150)
            onUse {
                // Apply Haste status effect (25% AGL boost for 5 turns)
                applyEffect(effects.haste.definition)
            }
        }

    // -------------------------------------------------------------------------
    // DROP-ONLY ITEMS (Monster Loot)
    // -------------------------------------------------------------------------

    /** Herb - Basic healing item. Weaker than Potion but common monster drop. */
    val herb: Item by
        builder.item {
            name("Herb")
            description("Restores a little HP")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 20, sell = 10)
            onUse { healPercent(10) }
        }

    /** Antidote - Cures poison status. Dropped by poison-using monsters. */
    val antidote: Item by
        builder.item {
            name("Antidote")
            description("Cures poison")
            category(ItemCategory.CONSUMABLE)
            maxStack(99)
            price(buy = 30, sell = 15)
            onUse { cure(effects.poison.definition) }
        }

    /** Gold Coin - Currency dropped by monsters. Sell for gold. */
    val goldCoin: Item by
        builder.item {
            name("Gold Coin")
            description("Sell for gold")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 10)
        }

    // -------------------------------------------------------------------------
    // MONSTER MATERIALS (Sell-only, valuable drops)
    // -------------------------------------------------------------------------

    /** Kobold Fang - Material dropped by Kobolds. */
    val koboldFang: Item by
        builder.item {
            name("Kobold Fang")
            description("A small sharp fang")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 5)
        }

    /** Goblin Ear - Material dropped by Goblins. */
    val goblinEar: Item by
        builder.item {
            name("Goblin Ear")
            description("A goblin's pointed ear")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 8)
        }

    /** Bone Dust - Material dropped by undead creatures. */
    val boneDust: Item by
        builder.item {
            name("Bone Dust")
            description("Powdered bones")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 12)
        }

    /** Beast Hide - Material dropped by beast-type monsters. */
    val beastHide: Item by
        builder.item {
            name("Beast Hide")
            description("Tough monster hide")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 25)
        }

    /** Slime Gel - Material dropped by Gelatinous Cubes. */
    val slimeGel: Item by
        builder.item {
            name("Slime Gel")
            description("Viscous ooze residue")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 30)
        }

    /** Ectoplasm - Material dropped by ethereal creatures. */
    val ectoplasm: Item by
        builder.item {
            name("Ectoplasm")
            description("Ghostly residue")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 50)
        }

    /** Death Essence - Rare material from powerful undead. */
    val deathEssence: Item by
        builder.item {
            name("Death Essence")
            description("Concentrated death magic")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 100)
        }

    /** Mind Fragment - Rare material from Mind Flayers. */
    val mindFragment: Item by
        builder.item {
            name("Mind Fragment")
            description("Psionic crystal shard")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 150)
        }

    /** Eye Stalk - Rare material from Beholders. */
    val eyeStalk: Item by
        builder.item {
            name("Eye Stalk")
            description("A magical eye stalk")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 200)
        }

    /** Dragon Scale - Very rare material from Dragons. */
    val dragonScale: Item by
        builder.item {
            name("Dragon Scale")
            description("An ancient dragon's scale")
            category(ItemCategory.MATERIAL)
            maxStack(99)
            price(buy = 0, sell = 500)
        }

    /** Dragon Heart - Ultimate treasure from the Dragon boss. */
    val dragonHeart: Item by
        builder.item {
            name("Dragon Heart")
            description("The heart of an ancient dragon")
            category(ItemCategory.MATERIAL)
            maxStack(1)
            price(buy = 0, sell = 5000)
        }
}

/**
 * Initialize all items for the game. Returns the Items instance for referencing individual items.
 */
fun initItems(builder: GameBuilder, effects: StatusEffects): Items {
    return Items(builder, effects)
}
