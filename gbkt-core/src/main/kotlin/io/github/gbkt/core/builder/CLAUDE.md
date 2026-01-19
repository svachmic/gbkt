# Builder Module

High-level game configuration and the `game { }` DSL entry point.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `GameBuilder.kt` | Main `game { }` DSL builder, registers all game elements | ~610 |
| `GameBuilderFeatures.kt` | Extension functions for sprites, tilemaps, audio, etc. | ~400 |
| `GameConfig.kt` | Hardware configuration (DMG/CGB, screen, etc.) | ~80 |

## Game Definition

The `game { }` function is the entry point for defining a complete Game Boy game:

```kotlin
val myGame = game("MyGame") {
    // Hardware config
    config {
        target = Target.CGB
        vblankWait = true
    }

    // Variables
    var score by u16Var(0)
    var playerX by u8Var(80)
    var playerY by u8Var(72)

    // Assets
    val playerSprite = sprite(SpriteAsset("player.png")) {
        size = 8 x 16
        position(80, 72)
    }

    // Scenes
    val titleScene = scene("title") { ... }
    val gameplayScene = scene("gameplay") { ... }

    start = titleScene
}
```

## GameBuilder (GameBuilder.kt)

The main builder class that collects all game elements:

### Registration Methods

```kotlin
// Internal registration (called by DSL functions)
internal fun registerEntity(entity: Entity)
internal fun registerCharacter(character: Character)
internal fun registerItem(item: Item)
internal fun registerInventory(inventory: Inventory)
internal fun registerMonster(monster: Monster)
internal fun registerAbility(ability: Ability)
internal fun registerStatusEffect(effect: StatusEffectDefinition)
internal fun registerFloor(floor: Floor)
internal fun registerPool(pool: Pool)
internal fun registerSprite(sprite: Sprite)
internal fun registerStateMachine(machine: StateMachine)
```

### Slot Allocation

```kotlin
// Automatic OAM slot management
internal fun nextSpriteSlot(): Int

// GBC palette slot allocation
internal fun allocatePaletteSlot(type: PaletteType): Int
```

### Dependency Injection

GameBuilder supports service injection for testing:

```kotlin
// Production (default services)
game("MyGame") { ... }

// Testing (mock services)
val mockServices = TestGameServices()
GameBuilder("test", mockServices).apply { ... }
```

## GameBuilderFeatures (GameBuilderFeatures.kt)

Extension functions that provide the DSL syntax:

### Sprite Features

```kotlin
fun GameBuilder.sprite(asset: SpriteAsset, init: SpriteBuilder.() -> Unit): Sprite
```

### Entity Features

```kotlin
fun GameBuilder.entity(init: EntityBuilder.() -> Unit): Entity
operator fun Entity.provideDelegate(...): ReadOnlyProperty<Any?, Entity>
```

### Audio Features

```kotlin
fun GameBuilder.sound(asset: SoundAsset): SoundEffect
fun GameBuilder.music(id: Int, asset: MusicAsset): Music
fun GameBuilder.audio(init: AudioMixerBuilder.() -> Unit): AudioMixer
```

### Camera Features

```kotlin
fun GameBuilder.camera(init: CameraBuilder.() -> Unit): Camera
```

### RPG Features

```kotlin
fun GameBuilder.character(init: CharacterBuilder.() -> Unit): Character
fun GameBuilder.item(init: ItemBuilder.() -> Unit): Item
fun GameBuilder.monster(init: MonsterBuilder.() -> Unit): Monster
fun GameBuilder.ability(init: AbilityBuilder.() -> Unit): Ability
fun GameBuilder.statusEffect(name: String, init: StatusEffectBuilder.() -> Unit): StatusEffect
fun GameBuilder.inventory(init: InventoryBuilder.() -> Unit): Inventory
```

### World Features

```kotlin
fun GameBuilder.floor(init: FloorBuilder.() -> Unit): Floor
fun GameBuilder.flags(init: FlagsBuilder.() -> Unit): GlobalFlags
fun GameBuilder.encounterTable(id: String, init: EncounterTableBuilder.() -> Unit): EncounterTable
```

## GameConfig (GameConfig.kt)

Hardware and system configuration:

```kotlin
config {
    // Target hardware
    target = Target.CGB        // Target.DMG for original Game Boy

    // System settings
    vblankWait = true          // Wait for VBlank before updating
    enableWindow = false       // Enable window layer
    enableSprites = true       // Enable sprite rendering
    enableBackground = true    // Enable background rendering

    // DMG palette (for non-CGB mode)
    dmgPalette = 0xE4          // Default grayscale palette
}
```

### Target Options

| Target | Description |
|--------|-------------|
| `Target.DMG` | Original Game Boy (4 shades of green) |
| `Target.CGB` | Game Boy Color (full color support) |

## Build Process

When `build()` is called:

1. Validates `start` scene is set and exists
2. Syncs variables/arrays to services for DI access
3. Creates immutable `Game` data class with all collected elements

```kotlin
fun build(): Game {
    require(_startScene.isNotEmpty()) { "Must set 'start' scene" }
    require(_startScene in scenes) { "Start scene '$_startScene' not defined" }

    // Sync to services
    variables.forEach { services.variables.registerVariable(it) }
    arrays.forEach { services.variables.registerArray(it) }

    return Game(
        name = name,
        config = config,
        variables = variables.toList(),
        sprites = sprites.toList(),
        entities = _entities.toList(),
        scenes = scenes.toMap(),
        startScene = _startScene,
        // ... all other collected elements
    )
}
```

## Tags

Type-safe tag references for grouping entities:

```kotlin
val enemyTag = tag("enemy")
val playerTag = tag("player")

val goblin by entity {
    tag(enemyTag)
}

whenever(player collidesWithAny enemyTag) { takeDamage() }
```

## Related Modules

- `scene/Scene.kt` - Scene definitions
- `entity/Entity.kt` - Entity system
- `services/` - Dependency injection services
- `Game.kt` - Immutable game data class
