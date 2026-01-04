# Test Fixtures

This directory contains reusable game configuration fixtures for integration testing.

## Available Fixtures

### minimal-game.kt
Simplest possible gbkt game with just a counter variable. Used for testing basic code generation.

### sprite-game.kt
Game with sprites to test asset pipeline and sprite handling.

### complex-game.kt
Comprehensive game fixture testing:
- Multiple variable types (u8, u16)
- GBC palettes
- Sprites with animations and regions
- Multiple scenes with transitions
- Collision detection
- Input handling
- Dialog system

### entity-game.kt
Game using entity-component system to test:
- Entity creation
- Velocity-based movement
- Tag system
- Entity collisions

## Usage in Tests

These fixtures can be copied into test project directories or loaded programmatically for integration tests.

Example:
```kotlin
val fixtureContent = File("test-fixtures/minimal-game.kt").readText()
val gameFile = File(testProjectDir, "src/main/kotlin/test/TestGame.kt")
gameFile.writeText(fixtureContent)
```
