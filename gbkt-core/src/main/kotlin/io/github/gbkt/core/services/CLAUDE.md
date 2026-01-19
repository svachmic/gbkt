# Services Module

Dependency injection interfaces for testable game building.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Services.kt` | Service interfaces (AssetService, SpriteService, etc.) | ~137 |
| `DefaultServices.kt` | Production implementations | ~109 |
| `MockServices.kt` | Test implementations with verification | ~206 |

## Architecture

```
GameServices (aggregate interface)
├── AssetService      - Asset resolution and validation
├── SpriteService     - OAM slot allocation
├── VariableService   - Variable/array registration
└── EntityService     - Entity registration and queries
```

## Service Interfaces (Services.kt)

### AssetService

Asset resolution and validation:

```kotlin
interface AssetService {
    fun resolveAsset(path: String): String?
    fun validateAsset(path: String): Boolean
    fun getAssetPaths(): Set<String>
    fun registerAsset(path: String)
}
```

### SpriteService

OAM slot management (Game Boy has 40 hardware sprite slots):

```kotlin
interface SpriteService {
    fun allocateSlot(): Int        // Get next OAM slot
    fun registerSprite(sprite: Sprite)
    fun getSprites(): List<Sprite>
}
```

### VariableService

Variable and array registration:

```kotlin
interface VariableService {
    fun registerVariable(variable: GBVar<*>)
    fun registerArray(array: GBArray)
    fun getVariables(): List<GBVar<*>>
    fun getArrays(): List<GBArray>
}
```

### EntityService

Entity management with tag queries:

```kotlin
interface EntityService {
    fun registerEntity(entity: Entity)
    fun getEntities(): List<Entity>
    fun queryByTag(tag: TagRef): List<Entity>
}
```

### GameServices (Aggregate)

```kotlin
interface GameServices {
    val assets: AssetService
    val sprites: SpriteService
    val variables: VariableService
    val entities: EntityService
}
```

## Production Implementation (DefaultServices.kt)

```kotlin
class DefaultGameServices(
    override val assets: AssetService = DefaultAssetService(),
    override val sprites: SpriteService = DefaultSpriteService(),
    override val variables: VariableService = DefaultVariableService(),
    override val entities: EntityService = DefaultEntityService(),
) : GameServices
```

Used automatically by `GameBuilder` when no services are injected.

## Test Implementation (MockServices.kt)

Mock services provide verification capabilities:

```kotlin
class MockSpriteService : SpriteService {
    val registeredSprites: List<Sprite>  // Verification
    val allocatedSlots: Int              // Verification
    fun reset()                          // Clear state

    // Interface methods
    override fun allocateSlot(): Int = ...
    override fun registerSprite(sprite: Sprite) { ... }
    override fun getSprites(): List<Sprite> = ...
}
```

### TestGameServices

Aggregates all mock services:

```kotlin
class TestGameServices(
    override val assets: AssetService = MockAssetService(),
    override val sprites: SpriteService = MockSpriteService(),
    override val variables: VariableService = MockVariableService(),
    override val entities: EntityService = MockEntityService(),
) : GameServices {
    fun reset()  // Reset all mocks
}
```

## Usage Patterns

### Production (implicit)

```kotlin
// DefaultGameServices is used automatically
game("MyGame") {
    val player by entity { ... }
}
```

### Testing (explicit injection)

```kotlin
@Test
fun `sprites are registered`() {
    val mockSprites = MockSpriteService()
    val services = TestGameServices(sprites = mockSprites)

    testGame("test", services = services) {
        sprite(Assets.Sprites.player) { size = 8 x 16 }

        test {
            assertEquals(1, mockSprites.registeredSprites.size)
            assertEquals("player.png", mockSprites.registeredSprites[0].asset)
        }
    }
}
```

### Entity Tag Queries

```kotlin
@Test
fun `can query entities by tag`() {
    val mockEntities = MockEntityService()

    testGame("test", TestGameServices(entities = mockEntities)) {
        val enemy by entity {
            tags("hostile", "npc")
        }

        test {
            val hostiles = mockEntities.queryByTag(TagRef("hostile"))
            assertEquals(1, hostiles.size)
        }
    }
}
```

## Design Rationale

### Why Dependency Injection?

1. **Testability**: Mock services allow unit testing without full game setup
2. **Isolation**: Test specific subsystems independently
3. **Verification**: Track what was registered during game building
4. **Flexibility**: Custom implementations for special cases

### Why Separate Mock Classes?

Mock classes add verification capabilities beyond the interface:
- `registeredSprites` - See what was registered
- `validationCalls` - Track validation attempts
- `reset()` - Clean state between tests

## Related Modules

- `builder/GameBuilder.kt` - Consumes GameServices
- `test/LogicTest.kt` - Uses TestGameServices
- `entity/Entity.kt` - Registered via EntityService
- `graphics/Sprite.kt` - Registered via SpriteService
