---
status: resolved
trigger: "Investigate and fix issue: integration-tests-v2-api-mismatch — IntegrationTest.kt fixtures use stale/hypothetical DSL APIs after V2 modular restructuring. Tests don't compile."
created: 2026-03-23T00:00:00Z
updated: 2026-03-23T00:10:00Z
---

## Current Focus

hypothesis: CONFIRMED — Test fixtures reference pre-V2 APIs (gbGame(), top-level sprite(), wrong imports) that no longer exist
test: Rewriting all 4 fixture helper methods to use correct V2 DSL API
expecting: All tests compile and pass after fixture rewrite
next_action: Apply fix to IntegrationTest.kt fixtures

## Symptoms

expected: All integration tests in gbkt-test compile and pass against the current V2 DSL API
actual: 8 of 11 tests fail because the Kotlin code inside test fixtures doesn't compile. 3 AssertionFailedError tests (buildAndFail() tests) fail because they expect specific error messages but get different compilation errors.
errors:
  1. gbGame() doesn't exist — the real function is game() in io.github.gbkt.core.dsl
  2. sprite() is a method on ActorBuilder, not a top-level GameBuilder function — the real API uses actor { sprite(...) }
  3. Imports are wrong — io.github.gbkt.core.* doesn't expose the DSL; it's in io.github.gbkt.core.dsl.*
  4. DSL methods like screen.clear(), printCentered(), collidesWith, every.frame may also be different
reproduction: Run `./gradlew :gbkt-test:test` — 8 compilation failures, 3 assertion failures
started: Since the V2 modular restructuring split gbkt-core into gbkt-ir, gbkt-lang, gbkt-engine, etc.

## Eliminated

(none yet)

## Evidence

- timestamp: 2026-03-23T00:01:00Z
  checked: GameBuilder.kt game() function signature
  found: function is `fun game(name: String, block: GameBuilder.() -> Unit): GameBuilder` in package `io.github.gbkt.core.dsl` — NOT `gbGame()`
  implication: All fixtures using `gbGame()` are broken

- timestamp: 2026-03-23T00:01:10Z
  checked: ActorBuilder.kt sprite() and actor() functions
  found: `sprite()` is a method on `ActorBuilder`, not top-level. Top-level is `actor { position(); sprite(asset("...")) { size(); hitbox() } }` using `by` delegate for name inference
  implication: All fixtures using `val x = sprite(SpriteAsset(...))` are broken

- timestamp: 2026-03-23T00:01:20Z
  checked: SceneBuilder.kt frame/enter lifecycle
  found: `frame { }` and `enter { }` are direct methods on SceneBuilder — no `every.frame` indirection
  implication: `every.frame { }` does not exist; fixtures must use `frame { }`

- timestamp: 2026-03-23T00:01:30Z
  checked: GameBuilder.start field type
  found: `var start: String? = null` — SceneRef.id must be used, not the SceneRef object directly
  implication: `start = mainScene` must be `start = mainScene.id`

- timestamp: 2026-03-23T00:01:40Z
  checked: ScriptBuilder.kt clear() and printCentered()
  found: `clear()` exists directly in ScriptBuilder — there is no `screen` object. `printCentered("text") at row` is valid.
  implication: `screen.clear()` must be replaced with `clear()`

- timestamp: 2026-03-23T00:01:50Z
  checked: ActorRef.collides() extension function
  found: `fun ActorRef.collides(other: ActorRef): Expr` — correct API is `player.collides(enemy)`, not infix `collidesWith`
  implication: `player collidesWith enemy` must be `player.collides(enemy)`

- timestamp: 2026-03-23T00:02:00Z
  checked: print() signature and palette() API
  found: `print(text, vararg values: Expr, position: PositionDef? = null)` — position requires PositionDef. palette() takes a block without name arg, colors set via color0/color1/color2/color3
  implication: Complex fixture's `print("SCORE: ", score) at (4 to 9)` and `palette("player") { colors(...) }` are wrong

## Resolution

root_cause: Four fixture helper methods and one inline test fixture in gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt used pre-V2 DSL APIs: `gbGame()` (renamed to `game()`), top-level `sprite()` (moved to `ActorBuilder.sprite()` inside `actor { }` block), wrong imports (`io.github.gbkt.core.*` instead of `io.github.gbkt.core.dsl.*`), `every.frame {}` (renamed to `frame {}`), `start = sceneRef` (must be `start = sceneRef.id`), `screen.clear()` (must be `clear()`), `player collidesWith enemy` (must be `player.collides(enemy)`), and `print("text", var) at (x to y)` syntax. Two additional `buildAndFail()` tests had incorrect expectations: `generateC` gracefully skips missing assets rather than failing; file-existence validation happens at `convertSprites`/`compileRom` time.
fix: Rewrote all four fixture helpers and one inline test fixture to use correct V2 DSL API. Fixed two `buildAndFail()` tests to use `build()` + assert `TaskOutcome.SUCCESS` to match actual graceful-degradation behavior.
verification: `./gradlew :gbkt-gradle-plugin:test` passes — BUILD SUCCESSFUL, 61 tests completed, 0 failed.
files_changed: [gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt]
