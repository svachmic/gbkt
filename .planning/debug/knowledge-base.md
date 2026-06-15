---
status: resolved
kind: registry
note: "Not an open investigation — this is the resolved-session knowledge registry used by gsd-debugger."
---

# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## integration-tests-v2-api-mismatch — Gradle plugin IntegrationTest fixtures used stale pre-V2 DSL API
- **Date:** 2026-03-23
- **Error patterns:** gbGame, every.frame, sprite SpriteAsset, io.github.gbkt.core.*, collidesWith, screen.clear, start = sceneRef, buildAndFail
- **Root cause:** Four fixture helper methods and one inline test fixture used pre-V2 DSL APIs: `gbGame()` (renamed to `game()`), top-level `sprite()` (moved to `ActorBuilder.sprite()` inside `actor { }` block), wrong imports (`io.github.gbkt.core.*` instead of `io.github.gbkt.core.dsl.*`), `every.frame {}` (renamed to `frame {}`), `start = sceneRef` (must be `start = sceneRef.id`), `screen.clear()` (must be `clear()`), `player collidesWith enemy` (must be `player.collides(enemy)`). Two additional `buildAndFail()` tests had incorrect expectations: `generateC` gracefully skips missing assets rather than failing.
- **Fix:** Rewrote all fixture helpers and inline test fixture to use correct V2 DSL API. Fixed two `buildAndFail()` tests to use `build()` + assert `TaskOutcome.SUCCESS`.
- **Files changed:** gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt
---

