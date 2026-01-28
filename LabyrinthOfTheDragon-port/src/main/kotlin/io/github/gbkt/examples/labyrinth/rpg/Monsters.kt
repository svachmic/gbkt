/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.Aspect
import io.github.gbkt.core.rpg.Monster
import io.github.gbkt.core.rpg.MonsterSize
import io.github.gbkt.core.rpg.MonsterTier
import io.github.gbkt.core.rpg.MonsterVariant
import io.github.gbkt.core.rpg.atTier
import io.github.gbkt.core.rpg.monster

/**
 * All monsters in Labyrinth of the Dragon.
 *
 * Monsters are organized by dungeon floor difficulty:
 * - Floors 1-2: C-tier (Kobold, Goblin, Zombie)
 * - Floors 3-4: B-tier (Bugbear, Owlbear, Gelatinous Cube)
 * - Floors 5-6: A-tier (Displacer Beast, Will-o'-Wisp)
 * - Floors 7-8: S-tier (Death Knight, Mind Flayer, Beholder, Dragon)
 */
class Monsters(
    builder: GameBuilder,
    private val items: Items,
    private val monsterAbilities: MonsterAbilities,
) {

    // =========================================================================
    // FLOORS 1-2: WEAK MONSTERS (C-Tier)
    // =========================================================================

    /**
     * Kobold - Weakest enemy in the game. Cowardly creatures that flee when injured.
     *
     * AI:
     * - Flees when HP below 20%
     * - 25% chance to use Fire Loogie (uses MATK, FIRE aspect)
     * - Otherwise uses basic axe attack
     */
    val kobold: Monster by
        builder.monster {
            name("Kobold")
            sprite(SpriteAsset("monsters/kobold.png"))
            size(MonsterSize.SMALL)
            tier(MonsterTier.C)
            baseStats {
                hp(15)
                atk(6)
                def(4)
                matk(8) // Added for fire loogie
                agl(10)
            }
            exp(10)
            ai {
                hpBelow(20) { flee() }
                chance(25) { useAbility("fireLoogie") }
                basicAttackRandom()
            }
            drops {
                drop(items.koboldFang, chance = 25)
                drop(items.herb, chance = 15)
                drop(items.goldCoin, chance = 50, minQty = 1, maxQty = 3)
            }
        }

    /** Kobold at B-tier (125% stats) - appears in higher-level encounters */
    val koboldB: MonsterVariant by lazy { kobold.atTier(MonsterTier.B) }

    /** Kobold at A-tier (150% stats) - elite variant for tough encounters */
    val koboldA: MonsterVariant by lazy { kobold.atTier(MonsterTier.A) }

    /** Goblin - Slightly tougher than Kobolds. Also cowardly, will flee when hurt. */
    val goblin: Monster by
        builder.monster {
            name("Goblin")
            sprite(SpriteAsset("monsters/goblin.png"))
            size(MonsterSize.SMALL)
            tier(MonsterTier.C)
            baseStats {
                hp(20)
                atk(8)
                def(5)
                agl(12)
            }
            exp(15)
            ai {
                hpBelow(25) { flee() }
                basicAttackRandom()
            }
            drops {
                drop(items.goblinEar, chance = 20)
                drop(items.antidote, chance = 10)
                drop(items.goldCoin, chance = 60, minQty = 2, maxQty = 5)
            }
        }

    /** Goblin at B-tier (125% stats) - appears in higher-level encounters */
    val goblinB: MonsterVariant by lazy { goblin.atTier(MonsterTier.B) }

    /** Goblin at A-tier (150% stats) - elite variant for tough encounters */
    val goblinA: MonsterVariant by lazy { goblin.atTier(MonsterTier.A) }

    /**
     * Goblin at S-tier (200% stats) - Floor 1 boss encounter. Triggered when player reaches
     * Level 10. Original: goblin_generator(monster, 10, S_TIER)
     */
    val goblinS: MonsterVariant by lazy { goblin.atTier(MonsterTier.S) }

    /**
     * Zombie - Undead creature. Slow but resilient. Immune to dark, weak to fire and light.
     *
     * AI:
     * - One-time Poison Bite (15% chance on first opportunity) - applies poison
     * - Otherwise uses basic slam attack
     *
     * Uses hasSpecialCharge/useSpecialCharge for one-time ability tracking.
     */
    val zombie: Monster by
        builder.monster {
            name("Zombie")
            sprite(SpriteAsset("monsters/zombie.png"))
            size(MonsterSize.SMALL)
            tier(MonsterTier.C)
            baseStats {
                hp(30)
                atk(7)
                def(3)
                agl(4)
            }
            aspects {
                immune(Aspect.DARK)
                vulnerable(Aspect.FIRE)
                vulnerable(Aspect.LIGHT)
            }
            exp(18)
            ai {
                // One-time poison bite (15% chance, only if special charge available)
                hasSpecialCharge {
                    chance(15) {
                        useSpecialCharge()
                        useAbility("poisonBite")
                    }
                }
                // Fallback to basic slam
                basicAttackRandom()
            }
            drops {
                drop(items.boneDust, chance = 20)
                drop(items.herb, chance = 10)
                drop(items.goldCoin, chance = 40, minQty = 2, maxQty = 6)
            }
        }

    /** Zombie at B-tier (125% stats) - appears in higher-level encounters */
    val zombieB: MonsterVariant by lazy { zombie.atTier(MonsterTier.B) }

    /** Zombie at A-tier (150% stats) - elite variant for tough encounters */
    val zombieA: MonsterVariant by lazy { zombie.atTier(MonsterTier.A) }

    // =========================================================================
    // FLOORS 3-4: MEDIUM MONSTERS (B-Tier)
    // =========================================================================

    /** Bugbear - Cunning goblinoid. Targets the weakest enemy for maximum damage. */
    val bugbear: Monster by
        builder.monster {
            name("Bugbear")
            sprite(SpriteAsset("monsters/bugbear.png"))
            size(MonsterSize.MEDIUM)
            tier(MonsterTier.B)
            baseStats {
                hp(45)
                atk(14)
                def(10)
                agl(8)
            }
            exp(35)
            ai { basicAttack(context.weakestEnemy) }
            drops {
                drop(items.beastHide, chance = 20)
                drop(items.potion, chance = 15)
                drop(items.goldCoin, chance = 70, minQty = 5, maxQty = 10)
            }
        }

    /** Bugbear at C-tier (75% stats) - weaker variant for mixed encounters */
    val bugbearC: MonsterVariant by lazy { bugbear.atTier(MonsterTier.C) }

    /** Bugbear at A-tier (150% stats) - elite variant for deep dungeon encounters */
    val bugbearA: MonsterVariant by lazy { bugbear.atTier(MonsterTier.A) }

    /** Owlbear - Ferocious beast. High attack power, straightforward aggression. */
    val owlbear: Monster by
        builder.monster {
            name("Owlbear")
            sprite(SpriteAsset("monsters/owlbear.png"))
            size(MonsterSize.MEDIUM)
            tier(MonsterTier.B)
            baseStats {
                hp(55)
                atk(16)
                def(12)
                agl(6)
            }
            exp(45)
            ai { basicAttackRandom() }
            drops {
                drop(items.beastHide, chance = 30)
                drop(items.potion, chance = 20)
                drop(items.goldCoin, chance = 75, minQty = 6, maxQty = 12)
            }
        }

    /** Owlbear at C-tier (75% stats) - weaker variant for some encounters */
    val owlbearC: MonsterVariant by lazy { owlbear.atTier(MonsterTier.C) }

    /** Owlbear at A-tier (150% stats) - elite variant for deep dungeon encounters */
    val owlbearA: MonsterVariant by lazy { owlbear.atTier(MonsterTier.A) }

    /** Gelatinous Cube - Living ooze. Immune to physical damage! Use fire or lightning. */
    val gelatinousCube: Monster by
        builder.monster {
            name("Gelatinous Cube")
            sprite(SpriteAsset("monsters/gelatinous_cube.png"))
            size(MonsterSize.MEDIUM)
            tier(MonsterTier.B)
            baseStats {
                hp(65)
                atk(10)
                def(8)
                agl(2)
            }
            aspects {
                immune(Aspect.PHYSICAL)
                vulnerable(Aspect.FIRE)
                vulnerable(Aspect.LIGHTNING)
            }
            exp(50)
            ai { basicAttackRandom() }
            drops {
                drop(items.slimeGel, chance = 35)
                drop(items.ether, chance = 10)
                drop(items.goldCoin, chance = 60, minQty = 5, maxQty = 15)
            }
        }

    /** Gelatinous Cube at C-tier (75% stats) - weaker variant for some encounters */
    val gelatinousCubeC: MonsterVariant by lazy { gelatinousCube.atTier(MonsterTier.C) }

    /** Gelatinous Cube at A-tier (150% stats) - elite variant for deep dungeon encounters */
    val gelatinousCubeA: MonsterVariant by lazy { gelatinousCube.atTier(MonsterTier.A) }

    // =========================================================================
    // FLOORS 5-6: STRONG MONSTERS (A-Tier)
    // =========================================================================

    /**
     * Displacer Beast - Illusory predator. Has 30% chance to evade any attack due to displacement.
     */
    val displacerBeast: Monster by
        builder.monster {
            name("Displacer Beast")
            sprite(SpriteAsset("monsters/displacer_beast.png"))
            size(MonsterSize.LARGE)
            tier(MonsterTier.A)
            baseStats {
                hp(70)
                atk(18)
                def(14)
                agl(16)
            }
            exp(80)
            ai { basicAttackRandom() }
            onHit { chance(30) { cancelHit() } }
            drops {
                drop(items.beastHide, chance = 40)
                drop(items.ether, chance = 20)
                drop(items.goldCoin, chance = 80, minQty = 10, maxQty = 20)
            }
        }

    /** Displacer Beast at C-tier (50% stats) - weaker variant for mixed encounters */
    val displacerBeastC: MonsterVariant by lazy { displacerBeast.atTier(MonsterTier.C) }

    /** Will-o'-Wisp - Ethereal spirit. Immune to physical and lightning. Very fast. */
    val willOWisp: Monster by
        builder.monster {
            name("Will-o'-Wisp")
            sprite(SpriteAsset("monsters/will_o_wisp.png"))
            size(MonsterSize.SMALL)
            tier(MonsterTier.A)
            baseStats {
                hp(40)
                atk(12)
                def(6)
                matk(20)
                mdef(18)
                agl(20)
            }
            aspects {
                immune(Aspect.LIGHTNING)
                immune(Aspect.PHYSICAL)
            }
            exp(70)
            ai { basicAttackRandom() }
            drops {
                drop(items.ectoplasm, chance = 30)
                drop(items.ether, chance = 15)
                drop(items.goldCoin, chance = 70, minQty = 8, maxQty = 18)
            }
        }

    /** Will-o'-Wisp at C-tier (50% stats) - weaker variant for mixed encounters */
    val willOWispC: MonsterVariant by lazy { willOWisp.atTier(MonsterTier.C) }

    /** Will-o'-Wisp at B-tier (75% stats) - moderate variant for some encounters */
    val willOWispB: MonsterVariant by lazy { willOWisp.atTier(MonsterTier.B) }

    // =========================================================================
    // FLOORS 7-8: BOSS-TIER MONSTERS (S-Tier)
    // =========================================================================

    /**
     * Death Knight - Undead warrior. Uses hellfire orb when low on HP. Can revive once at 50% HP.
     */
    val deathknight: Monster by
        builder.monster {
            name("Death Knight")
            sprite(SpriteAsset("monsters/deathknight.png"))
            size(MonsterSize.LARGE)
            tier(MonsterTier.S)
            baseStats {
                hp(150)
                atk(25)
                def(20)
                matk(18)
                mdef(20)
                agl(12)
            }
            aspects {
                immune(Aspect.DARK)
                immune(Aspect.ICE)
                resist(Aspect.FIRE)
                vulnerable(Aspect.LIGHT)
            }
            exp(200)
            ai {
                hpBelow(30) { useAbility("hellfireOrb") }
                basicAttackRandom()
            }
            onDeath { chance(100) { revive(hpPercent = 50) } }
            drops {
                drop(items.deathEssence, chance = 25)
                drop(items.elixir, chance = 15)
                drop(items.goldCoin, chance = 85, minQty = 20, maxQty = 40)
            }
        }

    /** Death Knight at A-tier (75% stats) - appears in regular floor 7 encounters */
    val deathknightA: MonsterVariant by lazy { deathknight.atTier(MonsterTier.A) }

    /**
     * Mind Flayer - Psionic horror. Deadly 2-phase kill chain.
     *
     * AI (2-phase kill chain):
     * - Phase 1: Mind Blast (30% chance, one-time) - deals damage and applies confusion
     * - Phase 2: If target is confused, attempt Extract Brain (95% instakill)
     * - Fallback: Basic tentacle attack
     *
     * Uses hasSpecialCharge/useSpecialCharge for one-time mind blast, and targetHasEffect to check
     * for confusion before extract brain.
     */
    val mindflayer: Monster by
        builder.monster {
            name("Mind Flayer")
            sprite(SpriteAsset("monsters/mindflayer.png"))
            size(MonsterSize.MEDIUM)
            tier(MonsterTier.S)
            baseStats {
                hp(100)
                atk(14)
                def(12)
                matk(28)
                mdef(24)
                agl(14)
            }
            aspects {
                immune(Aspect.DARK)
                vulnerable(Aspect.LIGHT)
            }
            exp(220)
            ai {
                // Phase 1: Mind Blast (one-time, 30% chance) - applies confusion
                hasSpecialCharge {
                    chance(30) {
                        useSpecialCharge()
                        useAbility("mindBlast")
                    }
                }

                // Phase 2: Extract Brain if target is confused (95% instakill)
                targetHasEffect("Confusion") { useAbility("extractBrain") }

                // Fallback: Basic tentacle attack
                basicAttackRandom()
            }
            drops {
                drop(items.mindFragment, chance = 20)
                drop(items.elixir, chance = 20)
                drop(items.goldCoin, chance = 80, minQty = 25, maxQty = 50)
            }
        }

    /** Mind Flayer at C-tier (50% stats) - appears in regular floor 7 encounters */
    val mindflayerC: MonsterVariant by lazy { mindflayer.atTier(MonsterTier.C) }

    /**
     * Beholder - Eye tyrant with 8 deadly eye rays.
     *
     * AI: ~4% chance for each of 8 ray types (~32% total ray chance), rest is basic attack. Rays:
     * Paralyze, Fear, Slow, Necro (poison), Ice, Trip (prone), Fire, Death (1% instakill)
     */
    val beholder: Monster by
        builder.monster {
            name("Beholder")
            sprite(SpriteAsset("monsters/beholder.png"))
            size(MonsterSize.LARGE)
            tier(MonsterTier.S)
            baseStats {
                hp(180)
                atk(20)
                def(16)
                matk(30)
                mdef(22)
                agl(10)
            }
            exp(280)
            ai {
                // Original: ~31% chance for random ray, equal probability each
                // DSL approximation: ~4% per ray (32% total ray chance)
                chance(4) { useAbility("paralyzeRay") }
                chance(4) { useAbility("fearRay") }
                chance(4) { useAbility("slowRay") }
                chance(4) { useAbility("necroRay") }
                chance(4) { useAbility("iceRay") }
                chance(4) { useAbility("tripRay") }
                chance(4) { useAbility("fireRay") }
                chance(4) { useAbility("deathRay") }
                basicAttackRandom()
            }
            drops {
                drop(items.eyeStalk, chance = 30)
                drop(items.elixir, chance = 25)
                drop(items.goldCoin, chance = 90, minQty = 30, maxQty = 60)
            }
        }

    /**
     * Dragon - THE FINAL BOSS.
     *
     * The ancient dragon guarding the labyrinth's deepest floor. Immune to fire, resistant to
     * physical, weak to ice. Uses flame breath frequently, especially when injured. Enters rage
     * mode below 25% HP.
     */
    val dragon: Monster by
        builder.monster {
            name("Dragon")
            sprite(SpriteAsset("monsters/dragon.png"))
            size(MonsterSize.BOSS)
            tier(MonsterTier.S)
            baseStats {
                hp(500)
                atk(35)
                def(25)
                matk(35)
                mdef(25)
                agl(15)
            }
            aspects {
                immune(Aspect.FIRE)
                resist(Aspect.PHYSICAL)
                vulnerable(Aspect.ICE)
            }
            exp(1000)
            ai {
                hpBelow(50) { chance(60) { useAbility("flameBreath") } }
                hpBelow(25) { useAbility("rage") }
                chance(30) { useAbility("flameBreath") }
                basicAttackRandom()
            }
            drops {
                drop(items.dragonScale, chance = 50)
                drop(items.dragonHeart, chance = 100) // Guaranteed boss drop
                drop(items.elixir, chance = 100, minQty = 2, maxQty = 3)
                drop(items.goldCoin, chance = 100, minQty = 50, maxQty = 99)
            }
        }
}
