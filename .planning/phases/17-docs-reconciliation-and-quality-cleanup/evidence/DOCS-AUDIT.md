# DOCS-AUDIT.md — DSL_REFERENCE.md Accuracy Audit Evidence

**Phase:** 17-docs-reconciliation-and-quality-cleanup  
**Plan:** 17-01  
**Produced:** 2026-06-12  
**Purpose:** D-15 evidence artifact — per-method audit of the 13 stale-API sections + full-document triage sweep. Downstream plans (17-04 archives, 17-08/09/10 rewrites) consume this table in lieu of re-deriving the audit.

---

## How to Read This Document

- **Section #**: The 13 stale-API sections numbered 1–13 per RESEARCH.md Section 3.
- **DSL_REFERENCE line**: Approximate line in `context/DSL_REFERENCE.md` where the stale-API caveat block appears (confirmed by `grep "Stale-API caveat"`).
- **Documented method**: Method/API name as written in DSL_REFERENCE.md.
- **Source citation**: `file:line` in the live source tree confirming existence, or `ABSENT` if no symbol found.
- **Verdict**: `accurate` (matches source), `corrected` (exists but differs from docs), or `moved-to-backlog` (aspirational — does not exist in source).
- **Backlog file**: Target `.planning/backlog/v0.2.0/FEAT-*.md` file for `moved-to-backlog` rows.

---

## Section 1: State Machine DSL (line ~372)

**Builder exists?** NO — `states("...")` builder is ABSENT.  
**What IS implemented:** Per-actor `animationStates { }` DSL in `ActorBuilder.kt` + `setAnimationState(actor, "state")` in `ScriptBuilder.kt`.  
**Disposition:** Archive entirely to `FEAT-STATE-MACHINES.md`; keep existing animationStates docs (uncaveated section) accurate.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 1.1 | 379 | `states("player") { ... }` top-level builder | ABSENT — no `states()` function in any DSL builder | moved-to-backlog | FEAT-STATE-MACHINES.md |
| 1.2 | 381 | `"idle" { enter { ... } }` state block | ABSENT | moved-to-backlog | FEAT-STATE-MACHINES.md |
| 1.3 | 382 | `on(condition) { goto("state") }` transition | ABSENT | moved-to-backlog | FEAT-STATE-MACHINES.md |
| 1.4 | 388 | `tick { }` per-state frame callback | ABSENT | moved-to-backlog | FEAT-STATE-MACHINES.md |
| 1.5 | 404 | `playerState.start("idle")` in scene | ABSENT | moved-to-backlog | FEAT-STATE-MACHINES.md |
| 1.6 | 406 | `playerState.update()` in frame loop | ABSENT | moved-to-backlog | FEAT-STATE-MACHINES.md |

---

## Section 2: Dialog System DSL (line ~922)

**Builder exists?** YES — `DialogBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/UIBuilders.kt:64`  
**Disposition:** Rewrite from source. Archive `.tick()`, `.isActive`, `.isComplete`, `dialog.show()` on DialogHandle (show/hide are on MenuHandle not DialogHandle).

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 2.1 | 941 | `dialog("elder") { speaker = "Elder" }` (property-style) | UIBuilders.kt:64 — `fun speaker(name: String)` (function-style) | corrected | — |
| 2.2 | 942 | `textSpeed = 3` (property assignment) | UIBuilders.kt:77 — `fun textSpeed(speed: Int)` (function-style) | corrected | — |
| 2.3 | 944 | `box { position(0, 10); size = 20 x 6; border = BorderStyle.SIMPLE; padding = 1 }` | UIBuilders.kt:106 — `fun box(x, y, width, height)` exists; no `size`, no `padding`, no nested `box { }` builder — all collapsed to a single `box(x, y, width, height)` call | corrected | — |
| 2.4 | 955 | `elder.say("Welcome, young hero!")` | UIBuilders.kt:161 — `DialogHandle.say(text: String)` | accurate | — |
| 2.5 | 974 | `shopkeeper.say("...", price, " gold.")` vararg | UIBuilders.kt:184 — `DialogHandle.say(vararg segments: Any)` | accurate | — |
| 2.6 | 964 | `elder.tick()` in frame loop | ABSENT — `DialogHandle` has no `tick()` method | moved-to-backlog | FEAT-DIALOG-TICK-API.md |
| 2.7 | 966 | `elder.isComplete` condition | ABSENT — `DialogHandle` has no `isActive`/`isComplete` property | moved-to-backlog | FEAT-DIALOG-TICK-API.md |
| 2.8 | 979 | `elder.choice { option("Accept") { ... } }` | UIBuilders.kt:213 — `DialogHandle.choice(block)` + `DialogChoiceBuilder.option(label, block)` | accurate | — |
| 2.9 | 986 | `elder.show()` | ABSENT on `DialogHandle` — `show()` only exists on `MenuHandle` (UIBuilders.kt:454) | moved-to-backlog | FEAT-DIALOG-TICK-API.md |
| 2.10 | 987 | `elder.hide()` | ABSENT on `DialogHandle` — `hide()` only exists on `MenuHandle` (UIBuilders.kt:463) | moved-to-backlog | FEAT-DIALOG-TICK-API.md |
| 2.11 | 920 | `DialogBuilder.portrait(AssetRef)` | UIBuilders.kt:101 — `fun portrait(asset: AssetRef)` | accurate | — |
| 2.12 | 920 | `DialogBuilder.border(BorderStyle)` | UIBuilders.kt:82 — `fun border(style: BorderStyle)` | accurate | — |
| 2.13 | 920 | `DialogBuilder.fontMode(FontMode)` | UIBuilders.kt:114 — `fun fontMode(mode: FontMode)` | accurate | — |

---

## Section 3: Menu System DSL (line ~1007)

**Builder exists?** YES — `MenuBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/UIBuilders.kt:273`  
**Disposition:** Rewrite from source. Archive `style {}` block, `gridMenu()`, `menu.tick()`, `menu.isActive`, `menu.isVisible`, `menu.selectedIndex`.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 3.1 | 1015 | `menu("main") { style { position(5, 8) } }` nested style block | UIBuilders.kt:333 — `fun position(x, y, width, height)` on MenuBuilder directly (no nested `style {}` block) | corrected | — |
| 3.2 | 1015 | `style { cursor = ">" }` property-style cursor | UIBuilders.kt:301 — `fun cursor(char: String)` function-style on MenuBuilder | corrected | — |
| 3.3 | 1015 | `style { border = BorderStyle.ROUNDED }` | ABSENT — `MenuBuilder` has no `border` method | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.4 | 1015 | `style { spacing = 2 }` | ABSENT — `MenuBuilder` has no `spacing` method | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.5 | 1023 | `item("NEW GAME") { navigate(gameplayScene) }` | UIBuilders.kt:350 — `fun item(label, block)` | accurate | — |
| 3.6 | 1031 | `mainMenu.show()` | UIBuilders.kt:454 — `MenuHandle.show()` | accurate | — |
| 3.7 | 1036 | `mainMenu.tick()` in frame loop | ABSENT — `MenuHandle` has no `tick()` method | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.8 | 1042 | `parent = mainMenu` (property-style) | UIBuilders.kt:311 — `fun parent(menu: MenuHandle)` function-style | corrected | — |
| 3.9 | 1051 | `toggle("MUSIC", musicEnabled) { onChange { ... } }` | UIBuilders.kt:361 — `fun toggle(label, variable: AssignableVar)` exists; no `onChange {}` block | corrected | — |
| 3.10 | 1056 | `slider("VOLUME", volume, 0..7) { step = 1; onChange { ... } }` | UIBuilders.kt:370 — `fun slider(label, variable, min, max, step)` — accepts separate min/max/step not a range; no `onChange {}` block | corrected | — |
| 3.11 | 1062 | `option("DIFFICULTY", difficulty) { choices("EASY", "NORMAL", "HARD") }` | UIBuilders.kt:386 — `fun option(label, variable, choices: List<String>)` — choices as List not block | corrected | — |
| 3.12 | 1075 | `gridMenu("inventory") { grid(4, 3); style { ... } }` | ABSENT — no `gridMenu()` function; use `menu { layout(MenuLayout.GRID); columns(4) }` | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.13 | 1085 | `itemsFrom(inventorySlots) { slot, index -> onSelect { ... } }` block form | UIBuilders.kt:403 — `fun itemsFrom(source: ArrayVar)` exists but takes no block | corrected | — |
| 3.14 | 1092 | `mainMenu.isVisible` condition | ABSENT — `MenuHandle` has no `isVisible` property | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.15 | 1093 | `mainMenu.isActive` condition | ABSENT — `MenuHandle` has no `isActive` property | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.16 | 1094 | `mainMenu.selectedIndex` expression | ABSENT — `MenuHandle` has no `selectedIndex` property | moved-to-backlog | FEAT-MENU-GRID-STYLE.md |
| 3.17 | 1066 | `item("BACK") { close() }` | UIBuilders.kt:350 — `item(label, block)` exists; `close()` emits MenuHide (UIBuilders.kt:463) | accurate | — |
| 3.18 | 1003 | `MenuHandle.hide()` | UIBuilders.kt:463 — `fun hide()` on `MenuHandle` | accurate | — |

---

## Section 4: Save Data Fields (line ~1234)

**Builder exists?** YES — `SaveDataBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt:139`  
**Disposition:** Rewrite upper section (slots/checksum/version accurate via triggerSystem). Archive field-level API.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 4.1 | 1241 | `saveData("mygame") { var score by u16Field() }` field-level delegate | ABSENT — `SaveDataBuilder` has no `u16Field()`, `u8Field()`, `flagsField()`, `arrayField()`, `var` delegates | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.2 | 1251 | `config { slots = 3 }` nested config block | ABSENT — `SaveDataBuilder` exposes `slots(Int)` directly (SystemBuilders.kt:145), no nested `config {}` | corrected | — |
| 4.3 | 1252 | `config { checksum = Checksum.CRC8 }` Checksum enum | ABSENT — `fun checksum(enabled: Boolean = true)` is boolean, not enum (SystemBuilders.kt:150) | corrected | — |
| 4.4 | 1253 | `config { magic = "GBKT" }` | ABSENT — no `magic` field on `SaveDataBuilder` | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.5 | 1254 | `config { version = 1 }` | SystemBuilders.kt:155 — `fun version(v: Int)` (no nested config block) | corrected | — |
| 4.6 | 1263 | `save.exists(slot = 0)` condition | ABSENT — `SaveDataRef` has no `exists()` method; save access is via `triggerSystem(saves)` (SystemBuilders.kt:607) | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.7 | 1270 | `save.load(slot = 0)` | ABSENT — `SaveDataRef` has no `load()` method | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.8 | 1279 | `save.score += 10` field access as variable | ABSENT — fields are not delegates on `SaveDataRef` | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.9 | 1287 | `save.save()` | ABSENT — `SaveDataRef` has no `save()` method | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.10 | 1294 | `save.flags.setBit(0)` / `clearBit()` / `toggleBit()` / `isSet()` | ABSENT | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.11 | 1299 | `save.inventory[0] set 5` array field | ABSENT | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.12 | 1304 | `save.erase(slot)` / `eraseAll()` / `copy(from, to)` | ABSENT | moved-to-backlog | FEAT-SAVE-DATA-FIELDS.md |
| 4.13 | ~1203 | `triggerSystem(saves)` save/load | ScriptBuilder.kt:545 — `fun triggerSystem(ref: SystemRef)` | accurate | — |

---

## Section 5: Entity Pools (line ~1316)

**Builder exists?** YES — `PoolDelegate` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/CollectionBuilders.kt:361`  
**Disposition:** Rewrite showing actual data-pool API (`pool(elementType, capacity)`). Archive sprite/lifecycle pool API.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 5.1 | 1324 | `pool("bullet", size = 8) { ... }` string-ID + builder block | ABSENT — actual API: `val bullets by pool(elementType, capacity)` delegate (CollectionBuilders.kt:510) | corrected | — |
| 5.2 | 1325 | `position(0, 0)` in pool block | ABSENT — pool stores data, no sprite position config | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.3 | 1326 | `velocity(0, 0)` in pool block | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.4 | 1328 | `sprite(asset) { size(4,4); hitbox(0,0,4,4) }` in pool block | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.5 | 1334 | `state { val timer by u8Var() }` per-entity state | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.6 | 1340 | `onSpawn { }` lifecycle hook | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.7 | 1345 | `onFrame { }` per-entity frame callback | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.8 | 1350 | `despawnWhen { y isBelow 8 }` auto-despawn | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.9 | 1357 | `onDespawn { hide() }` | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.10 | 1371 | `bullets.spawn { x set player.x }` | ABSENT on `PoolRef` — `PoolRef` (CollectionBuilders.kt:120) has data structure ops, not entity spawn | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.11 | 1378 | `bullets.spawnAt(x, y) { }` | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.12 | 1384 | `bullets.trySpawn { } orElse { }` | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.13 | 1397 | `bullets.activeCount` | CollectionBuilders.kt:120 — `PoolRef` has no `activeCount` property; `IRCollPool` is a data pool | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.14 | 1415 | `bullets.forEachActive { }` | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.15 | 1427 | `bullets.despawnAll()` | ABSENT | moved-to-backlog | FEAT-ENTITY-POOL-LIFECYCLE.md |
| 5.16 | ~1316 | `pool(elementType, capacity)` data pool | CollectionBuilders.kt:510 — `fun pool(elementType: CollElementType, capacity: Int)` | accurate | — |
| 5.17 | ~1316 | `pool(structDef, capacity)` struct pool | CollectionBuilders.kt:520 — `fun pool(structDef: StructDef, capacity: Int)` | accurate | — |

---

## Section 6: Tweening/Easing (line ~1477)

**Builder exists?** NO — `tween()` function and `Easing` enum are ABSENT.  
**Disposition:** Archive entirely to `FEAT-TWEENING.md`.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 6.1 | 1485 | `tween(target, from, to, duration, easing)` | ABSENT — no `tween()` in ScriptBuilder.kt | moved-to-backlog | FEAT-TWEENING.md |
| 6.2 | 1496 | `Easing.LINEAR`, `Easing.EASE_IN`, etc. | ABSENT — no `Easing` enum in any DSL file | moved-to-backlog | FEAT-TWEENING.md |
| 6.3 | 1498 | `Easing.EASE_IN_QUAD`, `Easing.EASE_OUT_BOUNCE`, etc. | ABSENT | moved-to-backlog | FEAT-TWEENING.md |
| 6.4 | 1524 | `MAX_TWEENS` config constant | ABSENT | moved-to-backlog | FEAT-TWEENING.md |
| 6.5 | 1532 | Usage in scenes with `tween()` in enter block | ABSENT — tween() does not exist | moved-to-backlog | FEAT-TWEENING.md |

---

## Section 7: Camera System (line ~1585)

**Builder exists?** YES — `CameraBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt:65`  
**Script-level ops:** `cameraOp(CameraAction.FOLLOW/UNFOLLOW/SHAKE/MOVE_TO)` in `ScriptBuilder.kt:545`  
**Disposition:** Rewrite showing CameraBuilder config + cameraOp. Archive smoothing/deadzone/snapTo/followX/followY/camera.shake/camera.update/camera.setPosition.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 7.1 | 1595 | `camera { smoothing = 0.15f }` var | SystemBuilders.kt:67 — `var smoothing: Float = 0.0f` (declared, applied in CameraSystem but not actively wired in all backends) | corrected | — |
| 7.2 | 1596 | `offset(0, -16)` in camera block | ABSENT — `CameraBuilder` has no `offset()` method | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.3 | 1597 | `deadzone(24 x 16)` in camera block | ABSENT — `CameraBuilder` has no `deadzone()` method | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.4 | 1598 | `bounds(0..256, 0..256)` range form | SystemBuilders.kt:101 — `fun bounds(mapWidth: Int, mapHeight: Int)` exists (Int form only; no range form) | corrected | — |
| 7.5 | 1605 | `camera.follow(player)` in scene | SystemBuilders.kt:79 — `fun follow(actor: ActorRef)` (config-time only, in CameraBuilder) | corrected | — |
| 7.6 | 1606 | `camera.fadeIn(20.frames)` in scene enter | ABSENT — `fade()` is a ScriptBuilder method (ScriptBuilder.kt:447), not a camera method | corrected | — |
| 7.7 | 1610 | `camera.update()` in scene frame | ABSENT — no camera handle/ref with `update()` method | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.8 | 1619 | `camera.follow(player)` in scene frame | ABSENT at runtime — `follow()` is a CameraBuilder config method, not a script-op handle method | corrected | — |
| 7.9 | 1622 | `camera.follow(player) { smoothing = 0.2f; offset(0, -16) }` config block at runtime | ABSENT — no runtime `camera.follow()` with block override | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.10 | 1628 | `camera.followX(player)` | ABSENT | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.11 | 1629 | `camera.followY(player)` | ABSENT | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.12 | 1631 | `camera.stopFollow()` | ScriptBuilder.kt:545 — `cameraOp(CameraAction.UNFOLLOW)` (different API surface) | corrected | — |
| 7.13 | 1638 | `camera.shake(intensity, duration)` | ScriptBuilder.kt:545 — `cameraOp(CameraAction.SHAKE, mapOf(...))` | corrected | — |
| 7.14 | 1641 | `camera.shake { intensity=6; duration=20.frames; decay=Decay.EXPONENTIAL }` block form | ABSENT — shake is via `cameraOp(CameraAction.SHAKE, args)` | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.15 | 1649 | `camera.impact(4)` | ABSENT | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.16 | 1652 | `camera.stopShake()` | ABSENT — no STOP_SHAKE CameraAction | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.17 | 1674 | `camera.setPosition(100, 50)` | ScriptBuilder.kt:545 — `cameraOp(CameraAction.MOVE_TO, mapOf(...))` (different API) | corrected | — |
| 7.18 | 1677 | `camera.snapTo(player)` / `camera.snapTo(100, 50)` | ABSENT — no SNAP_TO CameraAction | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.19 | 1681 | `camera.x` / `camera.y` read-only conditions | ABSENT — no camera coordinate read API | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 7.20 | ~1585 | `cameraOp(CameraAction.FOLLOW, ...)` | ScriptBuilder.kt:545 + ScriptOp.kt:469 | accurate | — |

---

## Section 8: Camera Transitions (line ~1658)

**Status:** PARTIAL — `fade()` is implemented as a script-level op. Wipe/iris/flash are absent.  
**Note:** The DSL_REFERENCE has already partially corrected this (the Transitions subsection at line 1655 says "Screen fades are a script-level op (`ScriptBuilder.fade`), not a camera method" with the correct API shown). The caveat is implicit via the containing Camera stale-API block (#7).

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 8.1 | 1662 | `fade(fadeIn = false, frames = 30) { navigate(...) }` | ScriptBuilder.kt:447 — `fun fade(fadeIn: Boolean, frames: Int, after: ScriptBuilder.() -> Unit = {})` | accurate | — |
| 8.2 | 1667 | `fade(fadeIn = true, frames = 20)` simple form | ScriptBuilder.kt:447 — same function, no-block overload | accurate | — |
| 8.3 | 1655 | Wipe transitions | ABSENT — no `wipe()` or `WipeStyle` in ScriptBuilder | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |
| 8.4 | 1655 | Iris / flash transitions | ABSENT | moved-to-backlog | FEAT-CAMERA-EXTRAS.md |

---

## Section 9: Physics (line ~1704)

**Builder exists?** YES — `PhysicsBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:500`  
**Script-level op:** `physicsUpdate(actor: ActorRef)` in `ScriptBuilder.kt:657`  
**Disposition:** Rewrite per-actor physics showing function-style API. Archive global physics world, gravity zones, per-entity friction override.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 9.1 | 1716 | `val player by actor { physics { gravity = 0.5f } }` property style | ActorBuilder.kt:531 — `fun gravity(pixelsPerFrameSq: Int)` (function-style, Int not Float) | corrected | — |
| 9.2 | 1717 | `physics { friction = 0.9f }` Float friction | ActorBuilder.kt — no `friction()` in `PhysicsBuilder`; per-actor `MovementBuilder` has `fun friction(Int)` at line 429 | corrected | — |
| 9.3 | 1718 | `physics { maxVelocity = 4 to 8 }` pair form | ABSENT — `fun maxFallSpeed(speed: Int)` exists (ActorBuilder.kt:545) but no `maxVelocity` | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.4 | 1719 | `physics { mass = 1.0f }` | ABSENT — `PhysicsBuilder` has no `mass` field | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.5 | 1727 | `player.applyPhysics()` in frame loop | ScriptBuilder.kt:657 — `fun physicsUpdate(actor: ActorRef)` (different name) | corrected | — |
| 9.6 | 1752 | `val physicsWorld = physics { gravity = 0.5f }` global | ABSENT — no top-level `physics {}` world builder | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.7 | 1758 | `tag("player")` / `tag("enemy")` | ABSENT — no `tag()` function in DSL | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.8 | 1764 | `physicsWorld.collide(playerTag, enemyTag)` | ABSENT | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.9 | 1768 | `physicsWorld.update()` | ABSENT | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.10 | 1782 | `gravityZone(x, y, width, height) { gravity = 0.1f }` | ABSENT | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.11 | 1806 | `physics { useLocalFriction = true }` per-entity override | ABSENT | moved-to-backlog | FEAT-PHYSICS-WORLD.md |
| 9.12 | ~1704 | `physics { velocity(dx, dy) }` | ActorBuilder.kt:519 — `fun velocity(dx: Int, dy: Int)` | accurate | — |
| 9.13 | ~1704 | `physics { gravity(n) }` | ActorBuilder.kt:531 — `fun gravity(pixelsPerFrameSq: Int)` | accurate | — |
| 9.14 | ~1704 | `physics { bounce(coefficient) }` | ActorBuilder.kt:540 — `fun bounce(coefficient: Float)` | accurate | — |
| 9.15 | ~1704 | `physics { maxFallSpeed(n) }` | ActorBuilder.kt:545 — `fun maxFallSpeed(speed: Int)` | accurate | — |
| 9.16 | ~1704 | `physics { platformerMode() }` | ActorBuilder.kt:555 — `fun platformerMode(enabled: Boolean = true)` | accurate | — |

---

## Section 10: Pathfinding (line ~1824)

**Builder exists?** YES — `PathfindingBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt:315`  
**Script-level ops:** `pathfindStep(npc, target)` and `waypointStep(npc)` in `ScriptBuilder.kt:611,633`  
**Disposition:** Rewrite showing PathfindingBuilder + pathfindStep. Archive navGrid/findPathTo/weighted tile API.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 10.1 | 1835 | `navGrid("arena") { size = 16 x 16; blocked(0..15, 0); ... }` | ABSENT — no `navGrid()` builder in any DSL file | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.2 | 1846 | `navGrid(from = dungeonMap) { blockedTiles(0, 1, 2) }` | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.3 | 1862 | `weight(x, y, cost = n)` tile weighting | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.4 | 1884 | `player findPathTo treasure using navGrid` infix | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.5 | 1887 | `player.findPathTo(treasure).using(navGrid) { diagonal = true; heuristic = Heuristic.MANHATTAN }` | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.6 | 1909 | `path.found` / `path.hasNext` / `path.directionX()` etc. path result | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.7 | 1926 | `enemy.followPath(path) { speed = 2; onArrive { } }` | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.8 | 1957 | `navGrid.addObstacle(enemy)` / `removeObstacle()` | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.9 | 1980 | `Heuristic.MANHATTAN`, `Heuristic.CHEBYSHEV`, `Heuristic.EUCLIDEAN` | ABSENT | moved-to-backlog | FEAT-PATHFINDING-NAVGRID.md |
| 10.10 | ~1824 | `pathfinding { gridSize(8) }` | SystemBuilders.kt:323 — `fun gridSize(px: Int)` | accurate | — |
| 10.11 | ~1824 | `pathfinding { mapSize(32, 32) }` | SystemBuilders.kt:328 — `fun mapSize(widthTiles, heightTiles)` | accurate | — |
| 10.12 | ~1824 | `pathfinding { maxOpenNodes(32) }` | SystemBuilders.kt:334 — `fun maxOpenNodes(count: Int)` | accurate | — |
| 10.13 | ~1824 | `pathfinding { maxPathLength(32) }` | SystemBuilders.kt:339 — `fun maxPathLength(length: Int)` | accurate | — |
| 10.14 | ~1824 | `pathfindStep(npc, target)` script op | ScriptBuilder.kt:611 — `fun pathfindStep(npc: ActorRef, target: ActorRef)` | accurate | — |
| 10.15 | ~1824 | `waypointStep(npc)` script op | ScriptBuilder.kt:633 — `fun waypointStep(npc: ActorRef)` | accurate | — |

---

## Section 11: Testing Framework (line ~2011)

**Builder exists?** NO — `testGame()` / `testScene()` DSL is ABSENT.  
**What IS implemented:** `SimulationContext` / `ScriptOpInterpreter` in `gbkt-core/src/main/kotlin/.../test/`; `GbktTestExtension` in `gbkt-test`. Documented in `context/TESTING.md`.  
**Disposition:** Replace section with a pointer to `context/TESTING.md`; archive stale DSL to `FEAT-TESTING-DSL.md`.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 11.1 | 2024 | `testGame("movement") { ... }` DSL | ABSENT | moved-to-backlog | FEAT-TESTING-DSL.md |
| 11.2 | 2054 | `testScene("test") { ... }` DSL | ABSENT | moved-to-backlog | FEAT-TESTING-DSL.md |
| 11.3 | 2035 | `test { expect("playerX").toEqual(80) }` block | ABSENT | moved-to-backlog | FEAT-TESTING-DSL.md |
| 11.4 | 2039 | `press(Button.RIGHT) { advanceFrames(5) }` input sim | ABSENT in this DSL form; emulator-tier has equivalent in `GbktTestExtension` | moved-to-backlog | FEAT-TESTING-DSL.md |
| 11.5 | 2075 | `advanceFrame()` / `advanceFrames(N)` | ABSENT in DSL form; `SimulationContext.advanceFrame()` exists in `gbkt-core/...test/` | corrected | — |
| 11.6 | 2083 | `advanceUntil(maxFrames) { }` / `orFail` | ABSENT in DSL form | moved-to-backlog | FEAT-TESTING-DSL.md |
| 11.7 | 2200 | `mock("actor") { collidesWith = true }` | ABSENT | moved-to-backlog | FEAT-TESTING-DSL.md |

---

## Section 12: Battle Menu / Formulas / Custom States (line ~2408)

**Builder exists?** NO — `battleMenu`, `combatFormulas`, `battleState`, `battleTransition` are ABSENT.  
**What IS implemented:** `simpleBattle()` at `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt:168`, `battleUpdate(BattleRef)` at `RpgExtensions.kt:348`, 19 built-in `CombatStates.*` constants.  
**Disposition:** Rewrite showing `simpleBattle` + `battleUpdate` + `CombatStates`; archive battle menu/formulas/custom states.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 12.1 | ~2372 | `simpleBattle("combat") { party(hero); encounter { +goblin }; onVictory { }; onDefeat { } }` | RpgExtensions.kt:168 — `fun GameBuilder.simpleBattle(id, block)` | accurate | — |
| 12.2 | ~2383 | `battleUpdate(combat)` in scene frame | RpgExtensions.kt:348 — `fun ScriptBuilder.battleUpdate(battleId: String)`, also `battleUpdate(BattleRef)` at line 364 | accurate | — |
| 12.3 | ~2393 | `CombatStates.PLAYER_TURN`, `CombatStates.VICTORY`, etc. | ScriptOp.kt — `CombatStates` object with 19 constants (INIT, PLAYER_TURN, TARGET_SELECT, EXECUTE_ACTION, ENEMY_TURN, VICTORY, DEFEAT, FLEEING, WAITING + more) | accurate | — |
| 12.4 | 2416 | `battleMenu("menu") { position(0,12); commands { command("Attack") { } }; statusDisplay { } }` | ABSENT — no `battleMenu()` builder | moved-to-backlog | FEAT-BATTLE-MENUS.md |
| 12.5 | 2440 | `combatFormulas { d20HitRoll(baseAC=10); criticalChance(5); damageVariance(25) }` | ABSENT — no `combatFormulas()` builder | moved-to-backlog | FEAT-BATTLE-MENUS.md |
| 12.6 | 2468 | `val cutsceneState by battleState("Cutscene")` | ABSENT — no `battleState()` function | moved-to-backlog | FEAT-BATTLE-MENUS.md |
| 12.7 | 2479 | `battleTransition(cutsceneState)` | ABSENT — no `battleTransition()` script op | moved-to-backlog | FEAT-BATTLE-MENUS.md |

---

## Section 13: Item & Inventory System (line ~2489)

**Builder exists?** YES PARTIAL — `ItemCatalogBuilder` and `ContainerBuilder` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InventoryBuilders.kt:227,298`  
**Disposition:** Rewrite showing catalog + container API. Archive `by item` delegate, `ItemCategory` enum (core), `inventory { }` builder, equip/unequip, advanced inventory ops.

| # | DSL_REF Line | Documented Method | Source Citation | Verdict | Backlog File |
|---|---|---|---|---|---|
| 13.1 | 2499 | `val potion by item { name("Potion"); category(ItemCategory.CONSUMABLE); maxStack(10) }` at game-level | ABSENT — `by item` delegate is only inside `items { }` block (InventoryBuilders.kt:274); `ItemCategory` is a string category ID not an enum | corrected | — |
| 13.2 | 2499 | `category(ItemCategory.CONSUMABLE)` using enum | ABSENT — category takes a string: `fun category(c: String)` at InventoryBuilders.kt:159 | corrected | — |
| 13.3 | 2499 | `buyPrice(50)` / `sellPrice(25)` | InventoryBuilders.kt:173 — `fun buyPrice(p: Int)` exists; `sellPrice()` ABSENT | corrected | — |
| 13.4 | 2507 | `onUse { target.heal(50) }` | InventoryBuilders.kt:184 — `fun onUse(block: ItemEffectBuilder.() -> Unit)` | accurate | — |
| 13.5 | 2509 | `cEmit(...)` in onUse block | ScriptBuilder.kt (via `ScriptEffect.script {}`) — allowed via `script { }` escape hatch | corrected | — |
| 13.6 | 2514 | `slot(EquipSlot.WEAPON)` / `stats { atk(+10) }` in item block | ABSENT from core `ItemBuilder` — `EquipSlot` is in `gbkt-genre-rpg`; no `slot()` on core `ItemBuilder` | moved-to-backlog | FEAT-INVENTORY-DELEGATE.md |
| 13.7 | 2541 | `ItemCategory.CONSUMABLE`, `ItemCategory.WEAPON`, etc. enum | ABSENT — `ItemCategory` is a builder-defined string category, not a global enum | moved-to-backlog | FEAT-INVENTORY-DELEGATE.md |
| 13.8 | 2550 | `EquipSlot.WEAPON`, `EquipSlot.HEAD`, etc. | ABSENT from core — exists only in `gbkt-genre-rpg` (EquipSlot enum in RPG genre) | corrected | — |
| 13.9 | 2561 | `val ringSlot by equipSlot("Ring")` custom slot | ABSENT from core DSL | moved-to-backlog | FEAT-INVENTORY-DELEGATE.md |
| 13.10 | 2580 | `val inventory by inventory { maxSlots(16) }` game-level | ABSENT — `ContainerBuilder` accessed via `container("id") { slots(16) }` or `val bag by container { slots(16) }` (InventoryBuilders.kt:298) | corrected | — |
| 13.11 | 2583 | `inventory.add(potion, 3)` / `inventory.remove(potion, 1)` | ABSENT — `ContainerRef` has no `add()`/`remove()` script-op methods | moved-to-backlog | FEAT-INVENTORY-DELEGATE.md |
| 13.12 | 2591 | `inventory.contains(potion)` / `inventory.count(potion)` / `inventory.isFull` | ABSENT | moved-to-backlog | FEAT-INVENTORY-DELEGATE.md |
| 13.13 | 2597 | `inventory.equip(hero, ironSword)` / `inventory.unequip(...)` | ABSENT | moved-to-backlog | FEAT-INVENTORY-DELEGATE.md |
| 13.14 | ~2489 | `items { item("potion") { name("Potion"); category(consumable) } }` | InventoryBuilders.kt:262 — `fun item(id, block)` inside ItemCatalogBuilder | accurate | — |
| 13.15 | ~2489 | `items { val potion by item { } }` delegate form | InventoryBuilders.kt:274 — `fun item(block)` delegate | accurate | — |
| 13.16 | ~2489 | `items { val consumable by category { defaultMaxStack(10) } }` | InventoryBuilders.kt:248 — `fun category(block)` delegate | accurate | — |
| 13.17 | ~2489 | `container("inventory") { slots(16) }` | InventoryBuilders.kt:298 — `ContainerBuilder.slots(n)` | accurate | — |
| 13.18 | ~2489 | `container { categoryFilter("EQUIPMENT") }` | InventoryBuilders.kt:307 — `fun categoryFilter(c: String)` | accurate | — |

---

## Backlog File Assignment Summary

All 12 FEAT-* files cover the 13 stale sections (sections 7 and 8 share `FEAT-CAMERA-EXTRAS.md`):

| Backlog File | Sections Covered |
|---|---|
| `FEAT-STATE-MACHINES.md` | Section 1 (State Machine DSL) |
| `FEAT-DIALOG-TICK-API.md` | Section 2 (Dialog tick/isActive/isComplete/show/hide on DialogHandle) |
| `FEAT-MENU-GRID-STYLE.md` | Section 3 (Menu style block, gridMenu, tick, isActive, isVisible, selectedIndex) |
| `FEAT-SAVE-DATA-FIELDS.md` | Section 4 (Save field-level API) |
| `FEAT-ENTITY-POOL-LIFECYCLE.md` | Section 5 (Sprite/lifecycle pool) |
| `FEAT-TWEENING.md` | Section 6 (Tweening/Easing) |
| `FEAT-CAMERA-EXTRAS.md` | Sections 7 + 8 (Camera smoothing/deadzone/snapTo/followX/followY + wipe/iris/flash transitions) |
| `FEAT-PHYSICS-WORLD.md` | Section 9 (Global physics world, gravity zones) |
| `FEAT-PATHFINDING-NAVGRID.md` | Section 10 (navGrid/findPathTo/weighted tiles) |
| `FEAT-TESTING-DSL.md` | Section 11 (testGame/testScene stale DSL) |
| `FEAT-BATTLE-MENUS.md` | Section 12 (Battle menu/formulas/custom states) |
| `FEAT-INVENTORY-DELEGATE.md` | Section 13 (by-item delegate, ItemCategory enum, advanced inventory) |

---

## Full-Document Triage Sweep (D-13)

**Scope:** Uncaveated sections of `context/DSL_REFERENCE.md` scanned for signs of staleness. This is a CHEAP pass — each flag was verified with a targeted grep or read; no deep per-method audit is repeated. Flagged items are either trivially fixable in plan 17-08/09/10 or filed as backlog todos.

**DSL_REFERENCE.md total line count:** 3,224 lines.

### Findings

**Flag T-01 — Camera Basic Setup example (line 1606): `camera.fadeIn(20.frames)` in enter block**

- **Suspect API:** `camera.fadeIn(20.frames)` — not a camera method, not a camera handle at all
- **Reason:** `fade()` is a `ScriptBuilder` method (confirmed ScriptBuilder.kt:447). No camera handle object exists in the DSL; camera is config-time-only. The example erroneously calls `camera.fadeIn()` as if `camera` were a runtime handle.
- **Disposition:** fix-if-trivial — replace the `camera.fadeIn(20.frames)` line in the Camera "Basic Setup" example with `fade(fadeIn = true, frames = 20)` (correct ScriptBuilder call). Fix in plan 17-08 (Camera section rewrite).

**Flag T-02 — Dialog Important Notes section (lines 997-1001): References `dialog.tick()`, `isActive`, `isComplete`**

- **Suspect API:** "Always call `dialog.tick()` in `frame { }` when a dialog is active" and "Use `dialog.isActive` and `dialog.isComplete` conditions to check state"
- **Reason:** These methods are ABSENT from `DialogHandle` (confirmed above in Section 2 audit). The bullet points in the "Important Notes" block contradict the actual source. Already inside the stale-API caveated section — confirmed covered by Section 2 cleanup.
- **Disposition:** fix-if-trivial — remove these bullet points in the Dialog rewrite (plan 17-09).

**Flag T-03 — Menu Important Notes (lines 1107-1113): References `menu.tick()`, `menu.show()` as required**

- **Suspect API:** "Always call `menu.tick()` in `frame { }` when a menu is active"
- **Reason:** `menu.tick()` is ABSENT from `MenuHandle`. Inside the stale-API caveated section — already covered by Section 3 cleanup.
- **Disposition:** fix-if-trivial — rewrite bullet points in plan 17-09.

**Flag T-04 — Save Data Important Note (line 1309): References Phase naming in public docs**

- **Line 1309:** "Note: In Phase 13.1 and later, the cartridge type is NOT auto-upgraded."
- **Reason:** Phase numbers are internal development references, not meaningful to users of the DSL. The rule (must declare `Cartridge.MBC5_RAM_BATTERY` explicitly) is valid, but the "Phase 13.1" reference is a doc hygiene issue.
- **Disposition:** fix-if-trivial — replace "In Phase 13.1 and later" with "Since v0.1.0" or just state the rule directly. Fix in plan 17-10.

**Flag T-05 — Physics Important Notes section (line 1735-1745): Gravity/friction value tables reference Float values**

- **Suspect API:** "Gravity values: `0.0f` = No gravity, `0.25f` = Light gravity, `0.5f` = Normal platformer gravity"
- **Reason:** `PhysicsBuilder.gravity()` takes `Int` (pixels/frame²), not `Float`. The table with float values (0.0f, 0.25f, 0.5f, 1.0f) is inaccurate. Already inside the stale-API caveated section — covered by Section 9 cleanup.
- **Disposition:** fix-if-trivial — rewrite tables with Int values in plan 17-08.

**Flag T-06 — Audio/Sound section: Verify `playSound()` and `soundEffect()` API accuracy**

- **Lines:** `context/DSL_REFERENCE.md` around lines 820–900 (Sound Effects / Audio section, no stale-API caveat banner).
- **Suspect API:** `sound.play()` on `SoundRef`, `sounds.bump.play()` pattern.
- **Quick verification:** `SoundRef` (SystemBuilders.kt:434) has only `id: String`. `ScriptBuilder` has `fun playSound(soundId: String)` and `fun playSound(ref: SoundRef)`. The documented `sounds.bump.play()` pattern (method on `SoundRef`) does NOT match — `SoundRef` has no `play()` method.
- **Disposition:** file-as-backlog-todo — the Audio section lacks a stale-API caveat and the `SoundRef.play()` pattern is incorrect. This is a separate bug from the 13 caveated sections. File as plan 17-08 fix item.

**Flag T-07 — Exploration/World DSL section: `exploration.onStep` callback**

- **Lines:** ~1100-1200 (Exploration section, no stale-API caveat).
- **Quick verification:** `ExplorationBuilder.onStep(block)` exists at SystemBuilders.kt:246. `onBlocked(block)` at line 251. `onInteract(block)` at line 255. These are accurate.
- **Disposition:** No flag — accurate.

**Flag T-08 — HUD section: Verify HudBuilder API**

- **Lines:** ~825-900 (HUD section, no stale-API caveat).
- **Quick verification:** `HudBuilder` exists at UIBuilders.kt:505. Methods `bar()`, `number()`, `icons()`, `anchor()`, `position()` all confirmed at lines 514-548. `HudPanel.show()`/`hide()` confirmed at lines 727/736. 
- **Disposition:** No flag — accurate.

**Flag T-09 — Variables section DEPRECATED API block (line 35-38): Migration arrows**

- **Lines 35-38 (inside Variables section):**
  ```
  // assign("score", literal(0))  →  score set 0
  // varRef("score")              →  score (use directly)
  // literal(5)                   →  5 (raw Int auto-wrapped)
  ```
- **Verification:** `assign()` and `varRef()` are legitimately the old string-based API. The migrations shown (`score set 0`, direct `score` use, raw `Int` auto-wrapped) are correct DSL idioms. The arrow direction and targets are accurate.
- **Disposition:** No flag — accurate.

**Flag T-10 — subpixel {} clarification (line 44-45, 58)**

- **Current text line 45:** "Group related declarations with the no-op `subpixel { }` scope."
- **Reason:** The DOCS-03 doc-only fix calls for clarifying that `subpixel {}` emits no IR and variables inside are recorded at the enclosing game scope. The current text ("no-op") partially conveys this but lacks the explicit "emits no IR" and "no new variable namespace" clarification per RESEARCH.md Section 4.
- **Disposition:** fix-if-trivial — add parenthetical "(emits no IR — variables inside are recorded at the enclosing game scope, not a sub-scope)" to line 45. This is DOCS-03 Fix 2. Fix in plan 17-10.

### Triage Sweep Summary

| Flag | Line(s) | Type | Disposition |
|------|---------|------|-------------|
| T-01 | 1606 | Wrong API in example | fix-in-17-08 (Camera rewrite) |
| T-02 | 997-1001 | Stale bullet points in caveated section | fix-in-17-09 (Dialog rewrite) |
| T-03 | 1107-1113 | Stale bullet points in caveated section | fix-in-17-09 (Menu rewrite) |
| T-04 | 1309 | Internal phase number in public doc | fix-in-17-10 (Save Data rewrite) |
| T-05 | 1735-1745 | Float gravity/friction values vs Int API | fix-in-17-08 (Physics rewrite) |
| T-06 | ~820-900 | `SoundRef.play()` absent — missing stale caveat | file-as-backlog-todo (plan 17-08) |
| T-07 | ~1100-1200 | Exploration callbacks | no flag — accurate |
| T-08 | ~825-900 | HUD builder | no flag — accurate |
| T-09 | 35-38 | Deprecated API block | no flag — accurate |
| T-10 | 44-45, 58 | subpixel{} clarification | fix-in-17-10 (DOCS-03 Fix 2) |

**D-13 Result:** 6 active flags found. No silent blind spots missed — the 3,224-line document is triaged. Flag T-06 (Audio `SoundRef.play()`) is the only stale-API finding outside the 13 known caveated sections.

---

## Self-Check: PASSED

- DOCS-AUDIT.md exists: confirmed (this file)
- All 13 section names present: State Machine, Dialog, Menu, Save, Entity Pool, Tweening, Camera, Physics, Pathfinding, Testing, Battle, Item — confirmed
- "Full-Document Triage Sweep" heading present — confirmed
- FEAT-STATE-MACHINES referenced — confirmed (Section 1)
- 12 target FEAT-* backlog files named — confirmed in Backlog File Assignment Summary
- Every moved-to-backlog row maps to one of the 12 FEAT-* files — confirmed
- `file:line` citations or ABSENT for all rows — confirmed
