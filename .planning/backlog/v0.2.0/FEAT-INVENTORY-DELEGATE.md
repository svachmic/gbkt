---
id: FEAT-INVENTORY-DELEGATE
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-13"
triage_date: 2026-06-12
---

# FEAT-INVENTORY-DELEGATE: by-item delegate at game scope, ItemCategory enum, custom equipSlot, advanced inventory ops (add/remove/equip/query)

## Source

Removed from context/DSL_REFERENCE.md lines 2489–2600 (commit 929653a4).

**Implemented today:** `ItemCatalogBuilder` and `ContainerBuilder` in `gbkt-lang/.../dsl/InventoryBuilders.kt:227,298`. `items { item("potion") { name("Potion"); category(consumable) } }` block works; `val potion by item { }` delegate works inside an `items { }` block. `onUse { target.heal(50) }` works via `ItemEffectBuilder`. `container("inventory") { slots(16) }` works. What is NOT implemented: `val potion by item { }` game-level delegate (only inside `items { }` block); `ItemCategory.CONSUMABLE` global enum (category is a string ID); `EquipSlot.WEAPON` etc. in core (`EquipSlot` is only in `gbkt-genre-rpg`); `val ringSlot by equipSlot("Ring")` custom slot; `ContainerRef.add(potion, 3)`, `ContainerRef.remove(potion)`, `ContainerRef.contains(potion)`, `ContainerRef.count(potion)`, `ContainerRef.isFull`; `inventory.equip(hero, ironSword)`, `inventory.unequip(hero, slot)`, `hero.isEquipped(ironSword)`.

## Why This Matters

Game-scope `val potion by item { }` delegates, a global `ItemCategory` enum, and a core `EquipSlot` enum would make items feel like first-class DSL citizens rather than catalog entries. The advanced inventory ops (`add`, `remove`, `equip`, `contains`, `count`) would complete the runtime inventory API so scenes can drive inventory changes without C escape-hatch code.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

### Item Definition (aspirational portions)

```kotlin
// Consumable item
val potion by item {
    name("Potion")
    description("Restores 50 HP")
    category(ItemCategory.CONSUMABLE)
    maxStack(10)
    buyPrice(50)
    sellPrice(25)

    onUse {
        target.heal(50)
        cEmit("play_sfx(SFX_HEAL);")  // Play sound effect
    }
}

// Equipment item with stat bonuses
val ironSword by item {
    name("Iron Sword")
    description("A sturdy blade")
    category(ItemCategory.WEAPON)
    slot(EquipSlot.WEAPON)
    maxStack(1)  // Equipment doesn't stack
    buyPrice(200)

    // Stat bonuses when equipped
    stats {
        atk(+10)
        acc(+5)
    }
}

// Key item (non-consumable, non-equipment)
val dungeonKey by item {
    name("Dungeon Key")
    description("Opens dungeon doors")
    category(ItemCategory.KEY_ITEM)
    maxStack(1)
}
```

### Item Categories & Equipment Slots

```kotlin
// Item categories
ItemCategory.CONSUMABLE   // Usable items (potions, scrolls)
ItemCategory.WEAPON       // Equippable weapons
ItemCategory.ARMOR        // Equippable armor
ItemCategory.ACCESSORY    // Equippable accessories
ItemCategory.KEY_ITEM     // Quest items
ItemCategory.MATERIAL     // Crafting materials

// Built-in equipment slots
EquipSlot.WEAPON
EquipSlot.OFFHAND
EquipSlot.HEAD
EquipSlot.BODY
EquipSlot.ACCESSORY
```

### Custom Equipment Slots

```kotlin
val game = game("MyGame") {
    // Define custom equipment slots
    val ringSlot by equipSlot("Ring")
    val bootsSlot by equipSlot("Boots")
    val glovesSlot by equipSlot("Gloves")

    // Use custom slot in item definition
    val powerRing by item {
        name("Power Ring")
        category(ItemCategory.EQUIPMENT)
        equipmentSlot(ringSlot)  // Use custom slot
        stats { atk(+5) }
    }
}
```

### Inventory Management

```kotlin
// Create inventory
val inventory by inventory { maxSlots(16) }

// Add items
inventory.add(potion, 3)         // Add 3 potions
inventory.add(ironSword)         // Add 1 item

// Remove items
inventory.remove(potion, 1)      // Remove 1 potion
inventory.remove(potion)         // Remove all potions of this type

// Query inventory
whenever(inventory.contains(potion)) { /* has at least one */ }
whenever(inventory.count(potion) isAtLeast 5) { /* has 5+ */ }
whenever(inventory.isFull) { showInventoryFullMessage() }
whenever(inventory.hasSpace) { /* can add more items */ }

// Equipment
inventory.equip(hero, ironSword)
inventory.unequip(hero, EquipSlot.WEAPON)
whenever(hero.isEquipped(ironSword)) { /* sword equipped */ }
```
