# DSL Reference

Complete reference for all gbkt DSL syntax and usage patterns.

## Variables and Assignments

Variables are declared using property delegates and used directly as typed `AssignableVar` values.

```kotlin
// Variable declaration — name inferred from property name
var score by u8Var(0)      // UINT8 (0-255), initial value 0
var ballDx by i8Var(1)     // INT8 (-128..127), initial value 1
var lives by u8Var(3)      // UINT8, initial value 3
var distance by u16Var(0)  // UINT16 (0-65535)

// Direct set — use infix `set` operator
score set 0           // score = 0
ballDx set -1         // ballDx = -1
lives set 3           // lives = 3

// Compound assignments (emit SET with ADD/SUB/MUL/DIV/MOD op)
score += 10           // add
lives -= 1            // subtract
ballDy *= -1          // multiply (bounce direction)
damage /= 2           // divide
frame %= 60           // modulo

// Increment / decrement
score++               // score = score + 1
--lives               // lives = lives - 1

// Use in arithmetic expressions (returns Expr)
val midX = (ball.x + paddle.x) / 2   // Expr arithmetic

// DEPRECATED API (do not use in new game code):
// assign("score", literal(0))  →  score set 0
// varRef("score")              →  score (use directly)
// literal(5)                   →  5 (raw Int auto-wrapped)
```

## Fixed-Point Variables (i16FixedVar / toPixel / subpixel / easeToZero)

Use `i16FixedVar` when you need 12.4 fixed-point sub-pixel physics. Declare in pixels;
the framework stores `initialPixels shl 4` internally. Extract pixel coords for rendering
with `.toPixel()`. Group related declarations with `subpixel { }` (emits no IR — variables
declared inside are recorded at the enclosing game scope, not a sub-scope).

```kotlin
// --- BEFORE (hand-rolled) ---
// posX initialized to pixel 64 × 16 sub-pixels = 1024
var posX by i16Var(1024)           // magic number: 64 * 16
ball.moveTo(posX shr 4, posY shr 4)  // leaks shr 4 arithmetic

// --- AFTER (idiomatic) ---
var posX by i16FixedVar(64)        // declare in pixels; stores 1024 internally (D-12 byte-identical)
var posY by i16FixedVar(72)        // screenCenter Y = 72 px
ball.moveTo(posX.toPixel(), posY.toPixel())  // extracts pixel coord (>> 4u)

// Optional: group declarations with subpixel { } for readability
// (emits no IR — variables inside are at enclosing game scope, not a sub-scope)
subpixel {
    var posX by i16FixedVar(80)
    var posY by i16FixedVar(72)
}

// Custom fractional bits (default 4 = 12.4)
var posX by i16FixedVar(64, fractionalBits = 4)  // same as i16FixedVar(64)
posX.toPixel(fractionalBits = 4)                 // same as posX.toPixel()

// Speed vars stay i16Var — speed is not a position (Pitfall 1/2)
var spdX by i16Var(0)
var spdY by i16Var(0)
```

### Ease Toward Zero (easeToZero)

Replaces the hand-rolled two-`whenever` deceleration ladder. Emits two `IfOp` nodes —
byte-identical to the ladder pattern (Pitfall 3: two separate checks, not one if-else).

```kotlin
// --- BEFORE (hand-rolled) ---
whenever(spdY isBelow 0) { spdY++ }
whenever(spdY isAbove 0) { spdY-- }
whenever(spdX isBelow 0) { spdX++ }
whenever(spdX isAbove 0) { spdX-- }

// --- AFTER (idiomatic) ---
spdY.easeToZero()        // default: by = 1
spdX.easeToZero()

// Custom step size
spdY.easeToZero(by = 2)  // converges faster (increments by 2 per frame)
```

### Declarative Wrap (u8Var wrapAt)

Declare the wrap invariant on the variable instead of emitting an explicit guard.
Power-of-two N uses a bitmask AND; non-power-of-two N uses compare-reset — both
are byte-identical to the hand-rolled patterns (D-15).

```kotlin
// --- BEFORE (hand-rolled) ---
var idx by u8Var(0)
var rot by u8Var(0)
// ... later in frame { }:
whenever(buttons.b.pressed) {
    idx++
    whenever(idx isAtLeast NUM_FRAMES) { idx set 0 }   // compare-reset
}
whenever(buttons.a.pressed) {
    rot++
    rot set (rot and 0xF)                               // bitmask wrap
}

// --- AFTER (idiomatic) ---
var idx by u8Var(0, wrapAt = NUM_FRAMES)  // non-power-of-two: compare-reset emitted after idx++
var rot by u8Var(0, wrapAt = 16)          // power-of-two: bitmask AND 15 emitted after rot++
// ... later in frame { }:
whenever(buttons.b.pressed) { idx++ }  // guard auto-emitted; no explicit wrap line
whenever(buttons.a.pressed) { rot++ }  // guard auto-emitted; no explicit wrap line
```

## Arrays

Arrays are declared with `u8Array()` and accessed via bracket operators.

```kotlin
// Array declaration — name inferred from property name
val bricks by u8Array(30)        // UINT8 array of 30 elements
val tiles by u8Array(16, "tile") // explicit C name override

// Bracket read — returns Expr for use in conditions and expressions
val b = bricks[bidx]             // bidx is AssignableVar
whenever(bricks[i] isEqualTo 1) { ... }

// Bracket write — emits ArrayAssign op into active ScriptBuilder
bricks[bidx] = 0                 // set to literal
bricks[i] = score                // set to another AssignableVar

// Compile-time size (not a C expression)
val n = bricks.size              // Int, use in forOp bounds

// DEPRECATED API (do not use in new game code):
// arrayAssign("bricks", varRef("i"), literal(1))  →  bricks[i] = 1
// arrayRef("bricks", varRef("i"))                 →  bricks[i]
```

## Readable Comparisons

Comparison functions return `Expr` for use in `whenever()`, `ifOp()`, and `whileOp()` conditions.

```kotlin
// Infix comparison functions (all return Expr)
whenever(score isEqualTo 0) { /* ... */ }
whenever(player.y isAbove 16) { /* ... */ }
whenever(ball.x isBelow 4) { /* ... */ }
whenever(score isAtLeast 100) { /* ... */ }
whenever(lives isAtMost 3) { /* ... */ }
whenever(hp isNotEqualTo 0) { /* ... */ }

// Cross-type comparisons (AssignableVar vs Int, Expr vs ActorPropertyRef, etc.)
whenever(ball.y isAtLeast paddle.y) { ... }          // two ActorPropertyRefs
whenever((paddle.y + 8) isAbove ball.y) { ... }      // Expr vs ActorPropertyRef
whenever(score isAbove 0) { ... }                    // AssignableVar vs Int

// Logical operators (infix — && and || cannot be overloaded in Kotlin)
whenever((hp isAbove 0) logicalAnd (torchLevel isAbove 0)) { ... }
whenever((x isAbove 0) logicalOr (y isAbove 0)) { ... }
```

## Text Rendering DSL

```kotlin
// Screen control — bare ScriptBuilder ops (inside enter/frame/exit blocks)
clear()          // Clear screen
showSprites()    // Show all sprites
hideSprites()    // Hide all sprites

// Print text with positioning — values need .toExpr(), position is PositionDef(x, y)
print("SCORE: %d", score.toExpr(), position = PositionDef(4, 9))
print("LIVES: %d", lives.toExpr(), position = PositionDef(4, 11))

// Print at tile coordinates (window layer)
printAt(0, 14, "Hello World")

// Centered text (auto-calculates X position)
printCentered("GAME OVER") at 6       // centered at row 6
printCentered("PRESS START") at 10

// Aligned text
printAligned("Score: 99", TextAlignment.RIGHT) at 0
```

## Actors (v2 DSL)

Actors are sprite entities with position, sprite asset, and hitbox. The `actor { }` delegate
infers the actor name from the Kotlin property name (via `provideDelegate`). The result is an
`ActorRef` with typed property accessors (`x`, `y`, `visible`).

```kotlin
// Actor declaration — name inferred from Kotlin property "ball", "player"
val ball by actor {
    position(80, 72)                     // initial screen position
    sprite(asset("sprites/ball.png")) {
        size(8, 8)                       // sprite dimensions
        hitbox(0, 0, 8, 8)              // collision rectangle (required for collides())
    }
}

val player by actor {
    position(80, 72)
    sprite(asset("sprites/player.png")) { size(8, 16); hitbox(0, 8, 8, 8) }
}

// Custom actor properties — registers prefixed global INT8/UINT8 variable
val bullet by actor {
    position(80, 72)
    sprite(asset("sprites/bullet.png")) { size(4, 4); hitbox(0, 0, 4, 4) }
    var speed by u8Prop(2)   // registers _bullet_speed UINT8 global
    var active by u8Prop(0)  // registers _bullet_active UINT8 global
}

// Actor property access — returns ActorPropertyRef with full operator set
ball.x += ballDx            // compound add (emits Assign with ADD op)
ball.y -= 2                 // compound subtract
ball.x set 80               // direct assignment
ball.y set 72               // direct assignment
ball.visible set false      // boolean set (false → 0)

// Typed comparisons on actor properties
whenever(ball.x isAbove 160) { ballDx set -1 }
whenever(ball.y isBelow 16) { ballDy set 1 }
whenever(ball.y isAtLeast paddle.y) { ... }      // ActorPropertyRef vs ActorPropertyRef

// Teleport (emits SetPosition op)
ball.moveTo(80, 72)

// Collision detection — emits inline AABB overlap test in C
// Requires both actors to have hitbox() defined in their sprite {} block
whenever(ball.collides(paddle)) { ballDy set -1 }
whenever(ball.collides(wall)) { ballDx set -1 }

// Movement helpers
moveBy(player, 2, 0)       // relative move using ActorRef
moveBy(player, 0, -2)

// Available actor properties: .x, .y, .visible
// Property access outside script context returns ActorPropertyRef (not Expr)
// — use .toExpr() to convert for use in print() vararg params:
print("HP: %d", hp.toExpr())
```

## Sprite Definition with Position

There is no standalone `sprite()` builder — sprites are always attached to an actor (or
metasprite). The `sprite(asset(...))` call lives inside an `actor { }` block; the actor owns the
position.

```kotlin
// Sprite attached to an actor — actor owns the position
val player by actor {
    position(80, 72)                          // initial screen position
    sprite(asset("sprites/player.png")) {
        size(8, 16)                           // sprite dimensions
        hitbox(2, 2, 4, 12)                   // x-offset, y-offset, width, height
    }
    palette(GbcPresets.FIRE)                  // optional GBC color palette
}

// Access position directly on the actor (inside enter/frame blocks)
player.x += 2                   // Move right
player.y set 100                // Set Y position
whenever(player.x isAbove 160) { player.x set 0 }  // Wrap around

// Collision detection — requires hitbox() on both actors
whenever(player.collides(obstacle)) {
    navigate(gameoverScene)
}
```

## Sprite Animations

Animations are defined as a state machine via `animationStates { }` inside the `actor { }` block.
Each state has a frame range, speed, and optional condition-based auto-transitions.

```kotlin
val player by actor {
    position(80, 72)
    sprite(asset("sprites/player.png")) { size(8, 16) }

    animationStates {
        state("idle") { frames(0..0); speed(8) }
        state("walk") { frames(1..4); speed(6) }
        transition("idle" to "walk") { dpad.any }   // auto-transition when d-pad held
        transition("walk" to "idle") { dpad.none }
    }
}

// Programmatic transitions from script blocks
whenever(buttons.a.pressed) { setAnimationState(player, "attack") }

// Play a named animation on an actor (by id)
animate("player", "walk")
```

## Metasprite Primitives

Metasprites are variable-length composite sprites made from multiple OAM hardware sprites. gbkt
supports two authoring paths — asset-driven (preferred for PNG sprite sheets) and procedural
(escape hatch for hand-crafted tile layouts).

### Asset-Driven Metasprite (recommended)

Declare with `sprite(asset(...)) { mode/pivot/frameSize }` + `frames(N)`. The asset pipeline
invokes `png2asset` automatically — no tile transcription, no `METASPR_ITEM` hand-coding.

```kotlin
import io.github.gbkt.core.ir.SpriteMode

// 5-frame elephant from a 64×240 px PNG (one 64×48 px frame per row)
val elephant by metasprite {
    sprite(asset("sprites/elephant.png")) {
        mode(SpriteMode.SPR8x8)   // -spr8x8 flag to png2asset
        pivot(0, 0)               // -px 0 -py 0  (origin at top-left)
        frameSize(64, 48)         // -sw 64 -sh 48 (one frame = 64×48 px)
    }
    frames(5)  // build-time cross-validation against png2asset output
}

// In a scene frame block — renders the metasprite at its current position
scene("play") {
    frame {
        moveMetasprite(elephant)
    }
}
```

**Note:** An author never writes `METASPR_ITEM` / `frame { tile(...) }` blocks for PNG-sourced
metasprites. The asset pipeline generates the C tile arrays automatically from `frameSize` and
`mode`. The `frames(N)` declaration enables build-time validation — the build fails with a clear
message if png2asset produces a different frame count than declared.

### Procedural Metasprite (frame{} escape hatch, D-04)

For hand-crafted OAM layouts where no source PNG exists — legacy ports, generated tile sets, or
metasprites whose tiles are shared with a background tileset. Each `frame { }` block describes one
animation frame as an ordered list of OAM tile entries.

```kotlin
// Procedural: each tile() call is one OAM hardware sprite entry
val cursor by metasprite {
    frame {
        tile(0, 0, 0)    // tile at (relX=0, relY=0), VRAM tileId=0
        tile(8, 0, 1)    // tile at (relX=8, relY=0), VRAM tileId=1
    }
    frame {
        tile(0, 0, 2)    // second animation frame, different tileIds
        tile(8, 0, 3)
    }
}
```

**Distinction from asset-driven path:** `frame { tile(...) }` blocks and `sprite(asset(...))` are
mutually exclusive within one `metasprite { }` block (D-08 exactly-one guard). Mixing both causes
a DSL build-time error. Choose one path per metasprite.

| Path | When to use |
|------|-------------|
| `sprite(asset(...)) + frames(N)` | PNG sprite sheet — png2asset cuts frames automatically |
| `frame { tile(x, y, id) }` | Hand-crafted layouts; shared/legacy tile sets (D-04 escape hatch) |

## Animation State Machine

Per-actor animation states are declared with `animationStates { }` inside the `actor { }` block.
Condition-based auto-transitions and manual `setAnimationState()` calls are the two ways to
drive the state machine at runtime. See "Sprite Animations" above for the full example.

```kotlin
val player by actor {
    position(80, 72)
    sprite(asset("sprites/player.png")) { size(8, 16) }

    // Declare states with frame ranges and optional auto-transitions
    animationStates {
        state("idle") { frames(0..0); speed(8) }
        state("walk") { frames(1..4); speed(6) }
        state("jump") { frames(5..5); speed(1) }
        transition("idle" to "walk") { dpad.any }   // auto: held d-pad → walk
        transition("walk" to "idle") { dpad.none }  // auto: released → idle
    }
}

// Manual transition from any script block (enter/frame/exit)
whenever(buttons.a.pressed) { setAnimationState(player, "jump") }
```

State transitions happen automatically when a `transition` condition becomes true.
`setAnimationState(actor, "stateName")` forces a transition unconditionally from script.

## Control Flow

```kotlin
// whenever() — conditional block, runs if condition is true each frame
whenever(buttons.a.pressed) {
    playerVelY set 8
}

// Nested conditions — both must be true
whenever(isJumping isEqualTo 1) {
    whenever(playerVelY isAbove 0) {
        playerY -= 2
    }
}

// ifOp/elseOp — one-shot branch (use in enter/exit blocks, not frame)
ifOp(hp isAbove 0) { hp -= damage }
elseOp { navigate(gameoverScene) }

// whileOp — loop (use in enter/exit only; frame is already called each tick)
var i by u8Var(0)
whileOp(i isBelow 30) {
    bricks[i] = 1
    i += 1
}
```

## Single-Frame Conditionals (runIf / unless / orElse)

Use `runIf` / `unless` for single-frame conditional logic (one-shot checks), not reactive
event triggers. `whenever()` should be used at the top level for input/state reactive triggers.
Nested `whenever` calls are the anti-pattern — use `runIf` instead (D-08 / Req #2).

```kotlin
// --- BEFORE (nested whenever — anti-pattern for single-frame logic) ---
whenever(dpad.right.held) {
    spdX += ACCEL
    whenever(spdX isAbove MAX_SPEED) { spdX set MAX_SPEED }   // semantically `if`, not reactive
}

// --- AFTER (idiomatic: runIf for single-frame clamp) ---
whenever(dpad.right.held) {
    spdX += ACCEL
    runIf(spdX isAbove MAX_SPEED) { spdX set MAX_SPEED }      // clear intent: one-shot check
}

// unless — negated condition (runs if condition is FALSE)
unless(hp isAbove 0) { navigate(gameoverScene) }

// orElse — chained else branch after runIf
runIf(hp isAbove 0) { hp -= damage }
    .orElse { navigate(gameoverScene) }

// D-08 rule: top-level reactive triggers stay as whenever()
whenever(buttons.a.pressed) { ... }    // KEEP whenever — reactive input trigger
whenever(dpad.left.held) { ... }       // KEEP whenever — reactive input trigger
```

## Input API

The type-safe input API uses typed objects instead of magic strings.

```kotlin
// D-pad — .held (continuous), .pressed (rising edge), .released (falling edge)
whenever(dpad.up.held) { player.y -= 2 }
whenever(dpad.down.held) { player.y += 2 }
whenever(dpad.left.held) { player.x -= 2 }
whenever(dpad.right.held) { player.x += 2 }

// D-pad edge-triggered (rising edge, one frame only)
whenever(dpad.up.pressed) { jump() }
whenever(dpad.left.released) { stopSlide() }

// D-pad special
whenever(dpad.any) { stepCount += 1 }   // any direction held
whenever(dpad.none) { idle() }          // no direction held

// Buttons — .held, .pressed, .released
whenever(buttons.a.pressed) { shoot() }
whenever(buttons.b.pressed) { navigate(titleScene) }   // SceneRef — type-safe
whenever(buttons.start.pressed) { navigate(pauseScene) }
whenever(buttons.select.held) { showMap() }

// Axis helpers (returns Expr: -1, 0, or 1)
player.x += dpad.x * 2                 // horizontal axis
player.y += dpad.y * 2                 // vertical axis

// Logical combinations
whenever(dpad.up.held logicalAnd buttons.b.held) { fastMove() }

// DEPRECATED (do not use in new game code):
// dpadHeld("up")          →  dpad.up.held
// buttonPressed("start")  →  buttons.start.pressed
// dpadAny()               →  dpad.any
```

## Zones

Zones are named tilemap regions — banked tileset + optional tilemap PNG, loaded on scene entry.
Declare with `val x by zone { }`: the zone ID is inferred from the Kotlin property name (no
magic string), mirroring the `by metasprite { }` pattern.

```kotlin
// Tileset-only zone — 20×18 fallback (Game Boy full-screen)
// No size() call → resolveZoneSize(explicit=null, pngDims=null) → (20, 18)
val playZone by zone {
    tileset(asset("tiles/checker.png"))
}

// Tilemap zone — size derived from tilemap PNG (D-03)
// 480×256 px tilemap PNG → 60×32 tiles; explicit size() NOT needed
val world1Area1Zone by zone {
    tileset(asset("graphics/world1-tileset.png"))
    tilemap(asset("graphics/world1-area1.png"))  // PNG dims drive the emitted tilemap size
    spawn(40u, 120u)                              // optional player spawn point in this zone
}

// Banner zone — explicit size() always wins (D-03 priority 1)
// 160×72 px banner → 20×9 tiles; must be explicit: tileset-only fallback would give 20×18
val titleZone by zone {
    tileset(asset("graphics/title-screen.png"))
    size(20, 9)  // explicit: 160×72 px = 20 cols × 9 rows of 8×8 px tiles
}
```

**Smart `size` default — D-03 priority chain:**

| Condition | Resolved size | When to use |
|-----------|--------------|-------------|
| Explicit `size(w, h)` called | `(w, h)` — always wins | Banner zones, non-standard dimensions |
| `tilemap(asset(...))` present | Derived from tilemap PNG IHDR (`px÷8`, `py÷8`) | World/level zones with full tilemap PNG |
| Tileset-only (no `tilemap()`) | `(20, 18)` — Game Boy full-screen fallback | Checker/repeating background zones |

The old `(32, 32)` default is eliminated. Omitting `size()` on a tileset-only zone is safe and
intentional — it produces a full-screen 20×18 tilemap (Game Boy native resolution in 8×8 tiles).

**Zone IDs from property names:** `val playZone by zone { }` registers a zone with ID
`"playZone"`. No string argument required. Reusing the same delegate instance throws an
`IllegalStateException` — each `by zone { }` must use a fresh delegate call.

**Loading a zone in a scene** (load overload — distinct from the declaration form):

```kotlin
val gameScene = scene("gameplay") {
    enter {
        zone(playZone)    // binds playZone's banked tileset on scene entry
    }
}
```

## Static Full-Screen Images (screen)

Use `screen(asset(...))` in a `scene { }` block when you want a static full-screen (or centered
card) background image with no collision, scroll, or spawn semantics. It is a `SceneBuilder`-level
call — place it at the top of the scene block alongside `palette()`, not inside `enter { }`.

```kotlin
// Title screen — static 160×144 px image fills the background
val titleScene = scene("title") {
    screen(asset("graphics/title-screen.png"))  // SceneBuilder level, not inside enter{}
    enter {
        // sprite/scroll state is already reset by screen() emit sequence:
        //   hide_sprites_range, move_bkg(0,0), fill_bkg_rect, then _bkg_tiles_load_banked
    }
    frame {
        whenever(buttons.start.pressed) { navigate(gameplayScene) }
    }
}

// Inter-level card — smaller centered PNG (e.g. 128×112 px)
val nextLevelScene = scene("nextlevel") {
    screen(asset("graphics/next-level.png"))   // auto-centers; no size() needed
    frame {
        whenever(buttons.start.pressed) { navigate(gameplayScene) }
    }
}
```

**Key properties:**

| Property | Detail |
|----------|--------|
| Placement | `SceneBuilder` scope (parallel to `zone()`), NOT inside `enter { }` |
| PNG size | Derived from PNG IHDR at build time — no `size()` call needed |
| Centering | Smaller-than-full-screen PNGs auto-center: `(DEVICE_SCREEN_WIDTH - tilemap_WIDTH) / 2` |
| Reset sequence | Emits `hide_sprites_range`, `move_bkg(0,0)`, `fill_bkg_rect(0,0,32,32,0)` before tile load |
| Single call | One `screen()` per scene; calling it twice throws at build time |
| Static only | No collision, scroll, or spawn data. For scrollable game maps, use `zone { }` instead |

**When NOT to use `screen()`:**

`screen()` is for static pictures only. Use `zone { }` for any tilemap that scrolls, has
collision data, or has a spawn point — that is, all game-world maps.

```kotlin
// CORRECT: static full-screen title — use screen()
val titleScene = scene("title") {
    screen(asset("graphics/title-screen.png"))
}

// CORRECT: scrollable game world with collision — use zone { }
val world1Area1Zone by zone {
    tileset(asset("graphics/world1-tileset.png"))
    tilemap(asset("graphics/world1-area1.png"))
    spawn(40u, 120u)
}
val gameplayScene = scene("gameplay") {
    enter { zone(world1Area1Zone) }
}
```

## Level Binding (bindCurrentLevel)

Use `bindCurrentLevel()` inside `enter { }` or `frame { }` script blocks in platformer games to
load the active level's tileset + tilemap into VRAM before the first gameplay frame. It is a
`ScriptBuilder`-level call that lowers to `setup_current_level()` with no raw-C escape hatch and
no build-time WARNING.

```kotlin
val gameplayScene = scene("gameplay") {
    enter {
        bindCurrentLevel()   // lowers to: setup_current_level();
        showSprites()
    }
    frame {
        // gameplay logic …
    }
}
```

**Valid only when** a `platformerPhysics { }` + `tilemapCollision { }` system pair is registered
in the game. Without these systems, `setup_current_level()` is not generated and the call has no
effect.

**Replaces the `cEmit` escape hatch:**

```kotlin
// BEFORE (escape hatch — emits a build-time stderr WARNING):
enter { cEmit("setup_current_level();") }

// AFTER (typed — no WARNING, same generated C):
enter { bindCurrentLevel() }
```

**Canonical names (D-01 / D-02):** The only valid name is `bindCurrentLevel()`. There is no
`loadLevel()` alias. The only valid name for static-image scenes is `screen()` — no other alias
exists.

## Auto-Synthesized `*_exit` Functions (MBC games)

In MBC cartridges (`maxRomBanks > 2`), every cross-bank scene automatically receives a BANKED
`${scene.id}_exit` function even when the DSL `exit { }` block is empty or absent. Authors no
longer need to declare a dummy `exit { }` block as a codegen trick to force the banked exit stub.

```kotlin
// MBC game — play_exit BANKED is auto-emitted even with no exit { } block
config { cartridge(Cartridge.MBC5_RAM_BATTERY) }

val playScene = scene("play") {
    enter { showSprites(); bindCurrentLevel() }
    frame {
        // game logic …
        whenever(buttons.start.pressed) { navigate(titleScene) }
    }
    // No exit { } needed — play_exit BANKED is auto-synthesized in bank1.c
}
```

**Generated C shape (MBC game, play scene in bank 1):**

```c
void play_exit(void) BANKED { }
```

**Rules:**

| Condition | Behavior |
|-----------|----------|
| MBC cartridge + cross-bank scene + no `exit { }` | `*_exit` BANKED auto-synthesized |
| MBC cartridge + cross-bank scene + non-empty `exit { }` | `*_exit` emitted from explicit ops |
| ROM_ONLY cartridge (`maxRomBanks <= 2`) | No auto-synthesis; single-bank games gain no new `*_exit` |
| HOME-bank scene in any cartridge | No auto-synthesis |

**Previous workaround (no longer needed):**

```kotlin
// BEFORE — empty exit block needed as codegen trick in MBC games:
val playScene = scene("play") {
    enter { showSprites() }
    frame { /* … */ }
    exit { hideSprites() }  // <-- TRICK: forces play_exit BANKED emission
}

// AFTER — remove the trick; auto-synthesis handles it:
val playScene = scene("play") {
    enter { showSprites() }
    frame { /* … */ }
    // exit {} removed — play_exit BANKED is auto-emitted in MBC games
}
```

## Scenes and Navigation

Scenes are defined with the `scene()` builder that returns a `SceneRef` — a type-safe handle for navigation.

```kotlin
// Define scenes — assign the returned SceneRef to a val
// Scene ordering: define targets BEFORE the scene that navigates to them
// (gameover before game, game before title) so each SceneRef is in scope.

val gameoverScene = scene("gameover") {
    enter {
        hideSprites()
        clear()
        print("GAME OVER", position = PositionDef(6, 6))
        print("PRESS START", position = PositionDef(5, 13))
    }
    frame {
        // titleScene defined below — use SceneRef("title") as a forward reference
        whenever(buttons.start.pressed) { navigate(SceneRef("title")) }
    }
}

val gameScene = scene("game") {
    enter { showSprites() }
    frame {
        whenever(lives isEqualTo 0) { navigate(gameoverScene) }  // SceneRef — type-safe
    }
}

val titleScene = scene("title") {
    enter { print("PRESS START", position = PositionDef(5, 12)) }
    frame { whenever(buttons.start.pressed) { navigate(gameScene) } }
}

// Set the start scene — assign the SceneRef directly (no .id accessor)
start = titleScene

// Navigate using SceneRef — validated at compile/build time
navigate(gameoverScene)   // preferred: val in scope above current scene declaration
navigate(SceneRef("title"))  // forward reference: scene defined later in the same game {} block
```

**Forward references:** When a scene navigates to a scene declared later in the same `game {}`
block, use `SceneRef("id")` as a forward reference. This is the only case where a string appears
in navigation — it is an explicit forward-reference, not an unvalidated magic string. Both forms
are validated at build time against the registered scene set.

**`navigate(sceneRef)` vs `SceneRef("id")`:** Use a captured `val` (`navigate(titleScene)`)
whenever the target is defined above the current scene. Use `SceneRef("id")` only for forward
references where the target is defined later.

**`start = sceneRef`:** Assign the `SceneRef` val directly — no `.id` accessor. Assigning an
unregistered `SceneRef` id causes a `DSLValidationError` at build time with a "Did you mean?"
suggestion.

**Timing in scenes:**

```kotlin
val gameScene = scene("game") {
    enter { /* called once on scene entry */ }
    exit { /* called once on scene exit */ }
    frame {
        /* runs every frame (60 fps) */
        /* use this for game logic, input handling, rendering */
    }
}

// Delay execution within enter/exit blocks
delay(60)    // pause 60 frames (~1 second)
delay(30)    // pause 30 frames (~0.5 second)
```

## DMG Color Constants

For the original DMG Game Boy, the 4-shade grayscale palette is expressed as integer indices 0–3. Use the `DmgColor` constants for readability:

```kotlin
import io.github.gbkt.core.dsl.v2.DmgColor

// Available constants (compile-time Int values)
DmgColor.WHITE      // 0 — lightest shade
DmgColor.LIGHT_GRAY // 1 — second lightest
DmgColor.DARK_GRAY  // 2 — second darkest
DmgColor.BLACK      // 3 — darkest shade

// Usage example — sprite palette definition
val player by actor {
    position(80, 72)
    sprite(asset("sprites/player.png")) {
        size(8, 16)
        hitbox(0, 8, 8, 8)
        // palette index for each of the 4 shades
        // (transparent, body, outline, shadow)
    }
}
```

**Note:** GBC (Game Boy Color) games use 15-bit RGB color defined via hardware palette registers. DMG constants apply only to DMG-mode games where the 4-shade index controls which hardware shade is displayed.

## GBC Color — Color Namespace

For Game Boy Color games, use the unified `Color` namespace. It exposes three constructor functions
and sixteen named constants — all returning `GBCColor` (a 15-bit RGB555 value). This supersedes
the legacy `gbc` and `gbcHex` top-level functions (both removed in Phase 13.3).

```kotlin
import io.github.gbkt.core.dsl.Color

// --- Constructor functions ---

// rgb888: from standard 8-bit-per-channel web/Photoshop values
// WARNING: prints to stderr when precision is lost in the RGB888→RGB555 conversion.
// Precision is lost when any channel's low 3 bits are non-zero.
// Exact multiples of 8 convert without a warning (e.g. 0, 8, 16, 24, 32, … 248).
val sky    = Color.rgb888(0, 136, 255)    // warns: 255 low-3-bits=111 → lossy
val exact  = Color.rgb888(0, 128, 248)    // no warning: all channels ≡ 0 mod 8

// rgb555: from native 5-bit hardware components (0–31 per channel) — no precision loss
// Use when you already know the hardware register values, e.g. from a palette editor.
val gray   = Color.rgb555(21, 21, 21)     // mid gray  (r=21, g=21, b=21)
val shadow = Color.rgb555(10, 10, 10)     // dark gray

// hex: from CSS hex string "#RRGGBB" or "RRGGBB" (case-insensitive)
// Delegates to GBCColor.fromHex → fromRGB888; same precision-loss rules as rgb888.
val snow   = Color.hex("#FFFFFF")
val ocean  = Color.hex("0080C8")

// --- Named constants (compile-time GBCColor values) ---
Color.WHITE        // (31, 31, 31) — brightest
Color.BLACK        // ( 0,  0,  0) — darkest
Color.RED          // (31,  0,  0)
Color.GREEN        // ( 0, 31,  0)
Color.BLUE         // ( 0,  0, 31)
Color.YELLOW       // (31, 31,  0)
Color.CYAN         // ( 0, 31, 31)
Color.MAGENTA      // (31,  0, 31)
Color.ORANGE       // (31, 16,  0)
Color.LIGHT_GRAY   // (22, 22, 22)
Color.DARK_GRAY    // (10, 10, 10)
Color.BROWN        // (18, 10,  4)
Color.PINK         // (31, 20, 24)
Color.LIME         // (16, 31,  8)
Color.NAVY         // ( 0,  0, 16)
Color.TEAL         // ( 0, 20, 20)

// --- Usage example — four sprite sub-palettes for GBC sub-palette cycling ---
val gray by spritePalette {
    color0(Color.WHITE);      color1(Color.rgb555(21, 21, 21))
    color2(Color.rgb555(10, 10, 10)); color3(Color.BLACK)
}
val pink by spritePalette {
    color0(Color.WHITE);      color1(Color.rgb555(31, 0, 31))
    color2(Color.rgb555(21, 0, 21));  color3(Color.rgb555(10, 0, 10))
}
```

**rgb888 precision-loss rule:** `Color.rgb888(r, g, b)` emits a `WARNING` to stderr when any
channel's low 3 bits are non-zero (the value cannot be represented exactly in RGB555). Use
`Color.rgb555(...)` for palette entries sourced from hardware registers or palette editors — the
5-bit components map directly to hardware values with zero precision loss.

## GBC Palette Slot Assignment

`palette()` inside a `scene { }` block loads a GBC sprite palette into a hardware slot (0–7) at
scene entry. Two overloads are available:

```kotlin
// Auto-increment (default) — slots assigned in declaration order (0, 1, 2, 3, …)
scene("play") {
    palette(gray)   // → slot 0
    palette(pink)   // → slot 1
    palette(cyan)   // → slot 2
    palette(green)  // → slot 3
    enter { /* ... */ }
}

// Explicit slot — override the auto-assigned slot for precise hardware control
scene("battle") {
    palette(heroSpritePalette, slot = 0)    // player always in slot 0
    palette(enemySpritePalette, slot = 3)   // enemies always in slot 3
    enter { /* ... */ }
}
```

**Auto-increment detail:** When a palette is declared without an explicit `slot = N`, the slot
equals the number of `palette()` calls already made in that scene (0-indexed). This means
`palette(gray); palette(pink); palette(cyan); palette(green)` reliably produces
`set_sprite_palette(0u,…)`, `set_sprite_palette(1u,…)`, etc.

**Duplicate-slot guard (D-11):** If two palettes map to the same slot within one scene, the DSL
throws an `IllegalArgumentException` at scene build time — the error is reported with a clear
message identifying which scene and which slot is duplicated.

**Slot range:** Hardware supports 8 GBC sprite palette slots (0–7). Passing a value outside this
range to `palette(p, slot = N)` throws at call site.

## Raw C Escape Hatch

`cEmit()` injects raw C directly into the output (the old `raw()` helper no longer exists).
It prints a build warning — prefer adding proper DSL support for recurring patterns.

```kotlin
// Rarely needed with new DSL features
cEmit("SHOW_SPRITES;")
cEmit("custom_function();")
```

## Dialog System DSL

gbkt provides a typewriter-effect dialog system for RPGs, adventures, and action games.
Config is function-style (not property-style). Source: `UIBuilders.kt` (`DialogBuilder`,
`DialogHandle`).

```kotlin
// === Quick Inline Dialog (action games) ===
// say() uses the default dialog box at the bottom of the screen
gameplayScene = scene("gameplay") {
    frame {
        whenever(gotItem isEqualTo 1) {
            say("You found a key!")
            gotItem set 0
        }
    }
}

// === Named Dialog (RPGs, adventures) ===
// Define once at game scope; reuse from any scene via the returned DialogHandle
lateinit var questScene: SceneRef
val elder = dialog("elder") {
    speaker("Elder")              // Prefix displayed above the box: "Elder: ..."
    textSpeed(3)                  // Characters per frame (higher = faster)
    box(x = 0, y = 10, width = 20, height = 6)  // tile coordinates + dimensions
    border(BorderStyle.SIMPLE)    // NONE, SIMPLE, ROUNDED, DOUBLE
    fontMode(FontMode.FIXED_WIDTH)
    portrait(asset("sprites/elder.png"))  // optional portrait asset
}

villageScene = scene("village") {
    enter {
        elder.say("Welcome, young hero!")
        elder.say("The kingdom needs you.")
    }
}

// === Variable Interpolation ===
val shopkeeper = dialog("shop") { textSpeed(2) }

// say() accepts vararg Any — mix String literals and AssignableVar/Expr
shopkeeper.say("That'll be ", price, " gold.")
shopkeeper.say("You have ", coins, " coins!")

// === Choice Menus ===
elder.choice {
    option("Accept quest") { navigate(questScene) }
    option("Decline") { navigate(villageScene) }
    option("Tell me more") { elder.say("Long ago...") }
}
```

**Important Notes:**
- `dialog("id") { }` config is function-style: `speaker("Name")`, `textSpeed(3)`, not property assignment
- `box(x, y, width, height)` is a single function call — no nested box builder, no `padding`
- `DialogHandle` methods: `say(text)`, `say(vararg segments)`, `choice { option(...) { } }`
- Inline `say()` (no handle) uses the default dialog at the bottom of the screen

## Menu System DSL

gbkt provides a menu system for title screens, pause menus, settings, and inventories. Config
is function-style (no `style { }` block). Source: `UIBuilders.kt` (`MenuBuilder`, `MenuHandle`).

```kotlin
// === Simple Vertical Menu (Title Screens, Pause Menus) ===
// menu("id") { } returns a MenuHandle for show/hide from script blocks
val mainMenu = menu("main") {
    position(5, 8, 10, 10)    // x, y, width, height in tile coordinates
    cursor(">")               // cursor character (function-style)

    item("NEW GAME") { navigate(gameplayScene) }
    item("CONTINUE") { navigate(continueScene) }
    item("OPTIONS") { open(optionsMenu) }
}

titleScene = scene("title") {
    enter {
        mainMenu.show()   // MenuHandle.show() — opens and focuses the menu
    }
    exit {
        mainMenu.hide()   // MenuHandle.hide() — closes the menu
    }
}

// === Settings Menu with Controls ===
val optionsMenu = menu("options") {
    parent(mainMenu)         // B button auto-returns to parent (function-style)
    position(3, 4, 14, 12)

    // Toggle: A button or left/right flips the variable (0 ↔ 1)
    toggle("MUSIC", musicEnabled)

    // Slider: left/right adjusts variable in [min, max] with step
    slider("VOLUME", volume, min = 0, max = 7, step = 1)

    // Option cycle: left/right cycles through a list of string choices
    option("DIFFICULTY", difficulty, listOf("EASY", "NORMAL", "HARD"))

    item("BACK") { close() }
}

// === Grid Menu (Inventories) ===
val inventoryMenu = menu("inventory") {
    layout(MenuLayout.GRID)
    columns(4)                // 4-column grid
    position(2, 2, 16, 12)
    itemsFrom(inventorySlots) // ArrayVar data source — items auto-populate
}
```

**Important Notes:**
- `menu("id") { }` config is function-style: `cursor(">")`, `position(x, y, w, h)`, `parent(menuHandle)` — no property assignment, no `style { }` block
- `toggle(label, variable)` — no block; `slider(label, variable, min, max, step)` — separate Int params, not a range
- `option(label, variable, listOf(...))` — choices as `List<String>`, not a block
- `itemsFrom(arrayVar)` — accepts `ArrayVar`; takes no block
- `MenuHandle.show()` / `MenuHandle.hide()` are the script-op entry points; D-Pad navigates, A selects, B cancels

## Game Configuration

Configure cartridge hardware, ROM banking, and SRAM inside the `config { }` block.

### Cartridge Type

Select the cartridge hardware using the `Cartridge` enum. The enum owns the MBC hardware byte —
no string magic values needed.

```kotlin
val myGame = game("MyGame") {
    config {
        cartridge(Cartridge.MBC5_RAM_BATTERY)  // typed — replaces cartridge = "MBC5_RAM_BATTERY"
        target(GbcTarget.GBC_COMPATIBLE)        // GBC palette support
    }
}
```

Valid `Cartridge` entries:

| Enum entry | MBC byte | Notes |
|---|---|---|
| `Cartridge.ROM_ONLY` | 0x00 | Default — no MBC, 32 KB ROM, no SRAM |
| `Cartridge.MBC1` | 0x01 | Up to 2 MB ROM, no SRAM |
| `Cartridge.MBC1_RAM` | 0x02 | Up to 2 MB ROM + volatile SRAM |
| `Cartridge.MBC1_RAM_BATTERY` | 0x03 | Up to 2 MB ROM + persistent SRAM |
| `Cartridge.MBC3_TIMER_BATTERY` | 0x10 | MBC3 + real-time clock |
| `Cartridge.MBC5` | 0x19 | Up to 8 MB ROM, no SRAM |
| `Cartridge.MBC5_RAM_BATTERY` | 0x1B | Up to 8 MB ROM + persistent SRAM — required for `saveData` |

### ROM Banking (auto-sized)

gbkt automatically derives `romBanks` from `BankingAnalysisPass`. In most games you should
omit `romBanks` entirely:

```kotlin
config {
    cartridge(Cartridge.MBC1)
    // romBanks omitted — derived automatically from analysis
}
```

Advanced users may supply an explicit override to reserve headroom. The override must be
**at least as large** as the derived count; setting it below the analysis result is a
hard build error with an actionable message:

```
romBanks=4 too small; banking analysis needs 6.
Set romBanks >= 6 or remove romBanks to auto-derive.
```

```kotlin
config {
    cartridge(Cartridge.MBC1)
    romBanks = 8   // advanced override — must be >= derived count
}
```

### SRAM Banks

`ramBanks` is configured exclusively in the DSL `config { }` block. The Gradle
`gbkt { ramBanks }` extension is deprecated — remove it from `build.gradle.kts` if
present; the DSL value is the single source of truth.

```kotlin
config {
    cartridge(Cartridge.MBC5_RAM_BATTERY)
    ramBanks = 2   // 2 × 8 KB SRAM banks
}
```

### Full Config Example

```kotlin
val myGame = game("MyGame") {
    config {
        cartridge(Cartridge.MBC5_RAM_BATTERY)
        ramBanks = 2
        target(GbcTarget.GBC_COMPATIBLE)
        // romBanks omitted — auto-derived by BankingAnalysisPass
    }

    // Declare save data — name inferred from property ("saves")
    @Suppress("UNUSED_VARIABLE") val saves by saveData { slots(2) }

    scene("gameplay") {
        frame {
            // Trigger the save system by typed ref (not a magic string)
            whenever(buttons.select.pressed) { triggerSystem(saves) }
        }
    }
}
```

## Save System DSL

gbkt supports type-safe SRAM persistence with auto-serialization, multi-slot saves, and data integrity validation.

### Declaring Save Data

Use the `by saveData { }` property delegate. The system id is inferred from the property
name — no string parameter needed. The `@Suppress("UNUSED_VARIABLE")` annotation suppresses
the Kotlin unused-variable warning that arises because the delegate registers the system as a
side effect of `provideDelegate`; this will be resolved globally in a future phase.

```kotlin
// Declare at game scope — id "saves" inferred from property name
@Suppress("UNUSED_VARIABLE") val saves by saveData { slots(2) }

// Trigger from a scene frame via typed ref (not triggerSystem("saves"))
whenever(buttons.select.pressed) { triggerSystem(saves) }
```

> **Cartridge requirement:** `saveData` requires a persistent SRAM cartridge such as
> `Cartridge.MBC1_RAM_BATTERY` or `Cartridge.MBC5_RAM_BATTERY`. Declare the cartridge in
> `config { }` — the framework no longer auto-upgrades the cartridge type silently.

### Save Data Configuration

`SaveDataBuilder` exposes three configuration functions. Source: `SystemBuilders.kt`.

```kotlin
@Suppress("UNUSED_VARIABLE") val saves by saveData {
    slots(3)              // number of independent SRAM save slots (default: 1)
    checksum(true)        // enable 8-bit rolling checksum on load (default: false)
    version(1)            // save format version number (default: 1)
}
```

Save/load is triggered at runtime via `triggerSystem(saves)` from any script block.
Game state variables (declared with `u8Var`, `i8Var`, etc.) are included automatically;
mark a variable `transient = true` to exclude it.

```kotlin
// Include in save (default — no annotation needed)
var score by u8Var(0)

// Exclude from save (e.g. frame counters, debug state)
var frameCounter by u8Var(0, transient = true)

// Trigger save/load from a scene
scene("gameplay") {
    frame {
        whenever(buttons.select.pressed) { triggerSystem(saves) }
    }
}
```

**Note:** The cartridge type is NOT auto-upgraded. Declare
`cartridge(Cartridge.MBC5_RAM_BATTERY)` explicitly in `config { }` when using `saveData`.

## Entity Pools

Entity pools manage collections of similar entities (bullets, particles, enemies) with lifecycle management.

> **Stale-API caveat:** the entity-pool DSL below does not match the current codebase. The
> implemented `pool(...)` builders (in `gbkt-lang/.../dsl/CollectionBuilders.kt`) are data
> pools — `pool(elementType, capacity)` / `pool(structDef, capacity)` — without sprite or
> lifecycle blocks. Cross-check before relying on this section.

### Pool Definition

```kotlin
val bullets = pool("bullet", size = 8) {
    position(0, 0)                    // Each entity has x, y position
    velocity(0, 0)                    // Optional: velX, velY (signed)

    sprite(asset("sprites/bullet.png")) {
        size(4, 4)
        hitbox(0, 0, 4, 4)
    }

    // Per-entity custom state
    state {
        val timer by u8Var()          // Creates bullet_0_timer, bullet_1_timer, etc.
        val damage by u8Var(10)       // With default value
    }

    // Lifecycle hooks
    onSpawn {
        play("fly")
        timer set 120                 // 2 seconds at 60fps
    }

    onFrame {
        y -= 4                        // Move up
        timer -= 1
    }

    // Auto-despawn conditions (entity despawns when ANY is true)
    despawnWhen {
        y isBelow 8                   // Off-screen top
        timer isEqualTo 0             // Timer expired
        isAnimationComplete           // One-shot animation finished
    }

    onDespawn {
        hide()
    }
}
```

### Spawning Entities

```kotlin
gameplayScene = scene("gameplay") {
    frame {
        bullets.update()              // REQUIRED: Updates all active entities

        whenever(buttons.a.pressed) {
            // Simple spawn with init block
            bullets.spawn {
                x set player.x
                y set player.y
            }

            // Spawn at position (shorthand)
            bullets.spawnAt(player.x, player.y) {
                this["damage"] set 20 // Access custom state
            }

            // Try spawn with fallback
            bullets.trySpawn {
                x set player.x
            } orElse {
                // Pool full - handle gracefully
            }
        }
    }
}
```

### Pool Queries

```kotlin
// Check active count
whenever(bullets.activeCount isEqualTo 0) {
    // No bullets active
}

// Check if pool has space
whenever(bullets.hasSpace) {
    bullets.spawn { /* ... */ }
}

// Check if pool is full
whenever(bullets.isFull) {
    // Show "MAX" indicator
}
```

### Iterating Active Entities

```kotlin
bullets.forEachActive {
    // 'this' is the current entity scope
    whenever(collidesWith(enemy)) {
        enemy.takeDamage(this["damage"])
        despawn()
    }
}
```

### Bulk Operations

```kotlin
bullets.despawnAll()                  // Clear all bullets

bullets.despawnWhere { x isAbove 160 } // Conditional bulk despawn
```

### Lifecycle Scope Properties

Inside `onSpawn`, `onFrame`, `onDespawn`, and `spawn` blocks:

```kotlin
// Position
x                    // AssignableExpr for X position
y                    // AssignableExpr for Y position

// Velocity (if velocity() was called)
velX                 // AssignableExpr for X velocity
velY                 // AssignableExpr for Y velocity

// Sprite operations
play("animation")    // Play animation
show()               // Show sprite
hide()               // Hide sprite

// Custom state (from state {} block)
this["timer"]        // Access custom field
this["damage"]       // Access custom field

// Index
index                // Current entity's pool index (0..size-1)

// Lifecycle control
despawn()            // Return this entity to pool

// Animation state
isAnimationComplete  // Condition: current animation finished
isPlaying("name")    // Condition: specific animation playing
```

### Generated C Code

For a pool with size 4, the generated code includes:
- Per-entity static variables (unrolled for performance)
- Pointer arrays for indexed access
- `spawn()`, `despawn()`, `update()` functions
- Active count tracking

## Tweening/Easing

Smooth value interpolation for animations, UI effects, and transitions.

> **Stale-API caveat:** the `tween()` function and `Easing` enum below do not exist in the
> current codebase. Cross-check `gbkt-lang/.../dsl/ScriptBuilder.kt` before relying on this
> section.

### Basic Tweening

```kotlin
// Tween a sprite position from 0 to 100 over 60 frames
tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)

// Tween a variable
tween(fadeAlpha, from = 0, to = 255, duration = 30.frames, easing = Easing.LINEAR)

// Tween with expression values
tween(enemy.x, from = Expr(startX), to = Expr(targetX), duration = 120.frames)
```

### Easing Functions

```kotlin
// Basic easing
Easing.LINEAR          // Constant speed
Easing.EASE_IN         // Start slow, end fast
Easing.EASE_OUT        // Start fast, end slow (default)
Easing.EASE_IN_OUT     // Slow at both ends
Easing.EASE_OUT_IN     // Fast at both ends

// Quadratic (t²)
Easing.EASE_IN_QUAD
Easing.EASE_OUT_QUAD
Easing.EASE_IN_OUT_QUAD

// Cubic (t³)
Easing.EASE_IN_CUBIC
Easing.EASE_OUT_CUBIC
Easing.EASE_IN_OUT_CUBIC

// Special effects
Easing.EASE_OUT_BOUNCE  // Bouncy landing
Easing.EASE_OUT_ELASTIC // Springy overshoot
```

### How It Works

- Pre-computed 256-entry lookup tables for each easing function
- Only tables for used easing types are generated (saves ROM space)
- Supports both increasing and decreasing tweens (signed math)
- Maximum 16 concurrent tweens (configurable via `MAX_TWEENS`)

### Usage in Scenes

```kotlin
introScene = scene("intro") {
    enter {
        // Slide title in from left
        tween(titleX, from = -80, to = 80, duration = 45.frames, easing = Easing.EASE_OUT_BOUNCE)
    }

    frame {
        // Tweens update automatically
    }
}
```

## Tilemap Collision

Access collision data from Tiled maps for physics and pathfinding.

### Configuring Collision Layer

```kotlin
val level = tilemap("level.json") {
    collisionLayer = "Collision"  // Name of collision layer in Tiled
}
```

### Checking Collisions

```kotlin
// Check if tile is blocked (by tile coordinates)
val blocked = level.isBlocked(tileX, tileY)

// Check if pixel position is blocked
val hit = level.isBlockedAtPixel(player.x, player.y)

// Get raw collision data
val collisionData = level.getCollisionData()
```

### Generated C Helpers

For each tilemap with collision data, these C functions are generated:

```c
// Check by tile coordinates
UINT8 level_is_blocked(UINT8 tile_x, UINT8 tile_y);

// Check by pixel coordinates  
UINT8 level_is_blocked_px(UINT16 pixel_x, UINT16 pixel_y);

// Get collision value (0 = walkable, >0 = blocked)
UINT8 level_get_collision(UINT8 tile_x, UINT8 tile_y);
```

## Camera System

The camera system provides scrolling, smooth follow, screen shake, and transitions.

> **Stale-API caveat:** the current `CameraBuilder` (in `gbkt-lang/.../dsl/SystemBuilders.kt`)
> implements `follow(actor)` and `bounds(mapWidth, mapHeight)` only; low-level camera ops go
> through `cameraOp(CameraAction.FOLLOW/UNFOLLOW/SHAKE/MOVE_TO)`. The smoothing/deadzone,
> shake-builder, followX/followY, and snapTo APIs below are not implemented. Cross-check
> before relying on details.

### Basic Setup

```kotlin
// Define camera with configuration
val camera = camera {
    smoothing = 0.15f           // Lerp factor (0 = instant, 1 = slow)
    offset(0, -16)              // Look-ahead offset from target
    deadzone(24 x 16)           // No movement within this area
    bounds(0..256, 0..256)      // World bounds clamp
}

// Use in scene
gameplayScene = scene("gameplay") {
    enter {
        camera.follow(player)   // Start following
        camera.fadeIn(20.frames)
    }

    frame {
        camera.update()         // Required: processes follow/shake/transitions
    }
}
```

### Smooth Follow

```kotlin
// Simple follow - camera tracks sprite/entity position
camera.follow(player)

// Follow with custom configuration
camera.follow(player) {
    smoothing = 0.2f            // Override smoothing
    offset(0, -16)              // Camera 16px above target
}

// Follow single axis
camera.followX(player)          // Only follow horizontally
camera.followY(player)          // Only follow vertically

// Stop following
camera.stopFollow()
```

### Screen Shake

```kotlin
// Basic shake - intensity in pixels, duration in frames
camera.shake(4, 10.frames)

// With decay configuration
camera.shake {
    intensity = 6
    duration = 20.frames
    decay = Decay.EXPONENTIAL   // or LINEAR, NONE
}

// Quick impact shake (short, punchy)
camera.impact(4)

// Stop shake
camera.stopShake()
```

### Transitions

Screen fades are a script-level op (`ScriptBuilder.fade`), not a camera method. Wipe, iris,
and flash transitions are not implemented in the current DSL.

```kotlin
// Fade out over 30 frames, then navigate (continuation runs after fade completes)
fade(fadeIn = false, frames = 30) {
    navigate(gameoverScene)
}

// Fade in over 20 frames
fade(fadeIn = true, frames = 20)
```

### Direct Positioning

```kotlin
// Set camera position directly
camera.setPosition(100, 50)

// Snap instantly to target (no smoothing)
camera.snapTo(player)
camera.snapTo(100, 50)

// Read camera position
whenever(camera.x isAbove 100) { /* ... */ }
```

### Automatic Sprite Offsetting

When a camera is defined, all sprite positions are automatically offset by the camera position. This means you can use world coordinates for sprites and they'll be correctly positioned on screen.

```kotlin
// Player at world position (200, 100)
player.x set 200
player.y set 100

// Camera at world position (100, 50)
camera.setPosition(100, 50)

// Player appears at screen position (100, 50) = (200-100, 100-50)
// This happens automatically - no manual offsetting needed!
```

## Physics

gbkt provides a complete physics system for platformers and action games with gravity, friction, collision response, and gravity zones.

> **Stale-API caveat:** the implemented per-actor `physics { }` block (in
> `gbkt-lang/.../dsl/ActorBuilder.kt`) is function-style — `gravity(n)`, `friction(n)`,
> `velocity(dx, dy)`, `bounce(coefficient)`, `maxFallSpeed(n)`, `platformerMode()` — driven by
> `physicsUpdate(...)` in the frame loop. The property-style snippets, global
> `physics { }` world, `tag()`, and `gravityZone()` below are not implemented.

### Entity Physics Component

Add physics to individual entities for gravity, friction, and velocity clamping:

```kotlin
val player by actor {
    position(80, 72)
    velocity(0, 0)  // REQUIRED for physics

    physics {
        gravity = 0.5f    // Applied to velocityY each frame (0.5 = normal platformer)
        friction = 0.9f   // Multiplied to velocityX each frame (0.9 = normal)
        maxVelocity = 4 to 8  // Clamp velocityX to ±4, velocityY to ±8
        mass = 1.0f       // For collision response (heavier = harder to push)
    }
}

// Apply physics in frame loop
gameplayScene = scene("gameplay") {
    frame {
        player.applyPhysics()  // Applies gravity, friction, clamping
    }
}
```

**Gravity values:**
- `0.0f` = No gravity (space, swimming)
- `0.25f` = Light gravity (floating/moon)
- `0.5f` = Normal platformer gravity
- `1.0f` = Heavy gravity

**Friction values:**
- `1.0f` = No friction (ice, space)
- `0.9f` = Normal friction
- `0.8f` = High friction (sticky surfaces)
- `0.0f` = Instant stop

### Physics World (Global Physics)

For games with global physics rules and automatic collision response:

```kotlin
val physicsWorld = physics {
    gravity = 0.5f
    friction = 0.9f
    bounce = 0.3f  // Collision bounce coefficient (0.0-1.0)
}

// Enable collision response between tagged entities
val playerTag = tag("player")
val enemyTag = tag("enemy")

gameplayScene = scene("gameplay") {
    enter {
        physicsWorld.collide(playerTag, enemyTag)  // Auto-bounce on collision
    }

    frame {
        physicsWorld.update()  // Update all physics
    }
}
```

### Gravity Zones

Define rectangular areas with custom gravity:

```kotlin
val physicsWorld = physics {
    gravity = 0.5f

    // Water area with reduced gravity
    gravityZone(x = 0, y = 100, width = 160, height = 44) {
        gravity = 0.1f  // Slow fall in water
    }

    // Zero-gravity space section
    gravityZone(x = 100, y = 0, width = 60, height = 100) {
        gravity = 0f
    }

    // Reverse gravity zone
    gravityZone(x = 0, y = 50, width = 50, height = 50) {
        gravity = -0.3f  // Float upward
    }
}
```

### Per-Entity Friction Override

Make entities act as friction surfaces (ice, mud, etc.):

```kotlin
val icePlatform by actor {
    position(0, 100)
    physics {
        friction = 0.99f  // Very slippery
        useLocalFriction = true  // Use this instead of global friction
    }
}

val mudPatch by actor {
    position(50, 100)
    physics {
        friction = 0.7f  // Very sticky
        useLocalFriction = true
    }
}
```

## Pathfinding

gbkt provides A* pathfinding optimized for tile-based games with navigation grids, weighted tiles, and dynamic obstacles.

> **Stale-API caveat:** the implemented pathfinding DSL is `PathfindingBuilder`
> (`gridSize`, `mapSize`, `maxOpenNodes`, `maxPathLength`) plus `pathfindStep(...)` /
> `waypointStep(...)` script ops. The `navGrid()` / `findPathTo` APIs below are not
> implemented — cross-check `gbkt-lang/.../dsl/SystemBuilders.kt` and `ScriptBuilder.kt`.

### Navigation Grid Setup

Define which tiles are walkable for pathfinding:

```kotlin
// Manual definition
val navGrid = navGrid("arena") {
    size = 16 x 16
    default = true        // All tiles walkable by default
    blocked(0..15, 0)     // Top wall
    blocked(0..15, 15)    // Bottom wall
    blocked(0, 0..15)     // Left wall
    blocked(15, 0..15)    // Right wall
    blocked(8, 8)         // Obstacle in center
}

// From tilemap (auto-extract from Tiled map)
val navGrid = navGrid(from = dungeonMap) {
    blockedTiles(0, 1, 2)  // Wall tile indices are blocked
}

// With collision layer from Tiled
val navGrid = navGrid(from = dungeonMap) {
    collisionLayer = "Collision"  // Use Tiled layer name
}
```

### Weighted Tiles

Give tiles different movement costs for more realistic pathfinding:

```kotlin
val navGrid = navGrid("dungeon") {
    size = 16 x 16
    default = true

    // Swamp area is slow
    weight(4..8, 4..8, cost = 3)  // 3x slower than normal

    // Road is fast
    weight(0..15, 8, cost = 1)  // Normal speed

    // Impassable walls (cost = 0 means blocked)
    blocked(0..15, 0)
}
```

### Pathfinding Queries

Find paths between entities or tiles:

```kotlin
gameplayScene = scene("gameplay") {
    frame {
        // Fluent infix syntax
        val path = player findPathTo treasure using navGrid

        // Or with options
        val path2 = player.findPathTo(treasure).using(navGrid) {
            diagonal = true   // Allow 8-way movement
            maxDepth = 64     // Search limit
            heuristic = Heuristic.MANHATTAN  // or CHEBYSHEV, EUCLIDEAN
        }

        // From/to tile coordinates
        val path3 = findPath(fromTileX = 0, fromTileY = 0, toTileX = 15, toTileY = 15)
            .using(navGrid)
    }
}
```

### Following Paths

Move entities along computed paths:

```kotlin
gameplayScene = scene("gameplay") {
    frame {
        val path = enemy findPathTo player using navGrid

        whenever(path.found and path.hasNext) {
            // Move toward next waypoint
            enemy.x += path.directionX(enemy.x)  // Returns -1, 0, or 1
            enemy.y += path.directionY(enemy.y)

            // Advance when waypoint reached
            whenever(path.atWaypoint(enemy, threshold = 4)) {
                path.advance()
            }
        }
    }
}

// Or use automatic path following
frame {
    val path = enemy findPathTo player using navGrid

    enemy.followPath(path) {
        speed = 2
        onArrive { /* reached destination */ }
        onBlocked { /* path blocked */ }
    }
}
```

### Path State Queries

Check path state with conditions:

```kotlin
whenever(path.found) { /* valid path exists */ }
whenever(path.notFound) { /* no valid path */ }
whenever(path.hasNext) { /* more waypoints remain */ }

// Path properties (as Expr)
val len = path.length       // Total waypoints
val idx = path.currentIndex // Current waypoint index
val nextX = path.nextX      // Next waypoint X (tiles)
val nextY = path.nextY      // Next waypoint Y (tiles)
```

### Dynamic Obstacles

Modify navigation at runtime:

```kotlin
frame {
    // Block tile where enemy stands (pixels → tiles automatic)
    navGrid.addObstacle(enemy)

    // Later, clear it
    navGrid.removeObstacle(enemy)

    // Or by tile coordinates
    navGrid.setBlocked(8, 8)
    navGrid.setWalkable(8, 8)

    // Change movement cost
    navGrid.setWeight(x = 5, y = 5, cost = 3)

    // Check if tile is walkable
    whenever(navGrid.isWalkable(tileX, tileY)) {
        // Tile is passable
    }
}
```

### Heuristics

Choose the distance calculation method:

- `Heuristic.MANHATTAN` - |dx| + |dy| - Best for 4-way movement (default)
- `Heuristic.CHEBYSHEV` - max(|dx|, |dy|) - Best for 8-way movement
- `Heuristic.EUCLIDEAN` - sqrt(dx² + dy²) - Most accurate but slower

## Signed Integer Types

For velocities, directions, and relative positions, use signed integer delegates:

```kotlin
// Signed 8-bit (-128 to 127)
var velocityX by i8Var(0)
var direction by i8Var(-1)

// Signed 16-bit (-32768 to 32767)
var cameraOffsetX by i16Var(0)
var relativePosition by i16Var(-100)

// Usage in scenes
gameplayScene = scene("gameplay") {
    frame {
        velocityX += 1
        direction set -5
        cameraOffsetX -= 2
    }
}
```

## Testing Framework

gbkt includes a built-in testing framework that lets you test game logic without compiling to ROM or running an emulator. Tests run directly on the JVM with simulated game state.

> **Stale-API caveat:** the `testGame()`/`testScene()` DSL below does not exist in the current
> codebase. JVM-tier simulation is done via `SimulationContext` / `ScriptOpInterpreter` in
> `gbkt-core/.../test/`; emulator-tier testing uses `GbktTestExtension` in `gbkt-test` (see
> context/TESTING.md).

### Basic Test Structure

```kotlin
import io.github.gbkt.core.test.*
import kotlin.test.*

class MyGameTest {
    @Test
    fun `player moves right`() = testGame("movement") {
        var playerX by u8Var(80)

        val gameplay = scene("gameplay") {
            frame {
                playerX += dpad.x * 2
            }
        }
        start = gameplay

        test {
            // Initially at 80
            expect("playerX").toEqual(80)

            // Hold right for 5 frames
            press(Button.RIGHT) { advanceFrames(5) }

            // Should have moved 10 pixels (2 * 5)
            expect("playerX").toEqual(90)
        }
    }
}
```

### Testing Single Scenes

For simpler tests, use `testScene` to test a scene in isolation:

```kotlin
@Test
fun `counter increments each frame`() = testScene("test") {
    var counter by u8Var(0)

    frame { counter += 1 }

    test {
        expect("counter").toEqual(0)
        advanceFrame()
        expect("counter").toEqual(1)
        advanceFrames(9)
        expect("counter").toEqual(10)
    }
}
```

### Frame Control

```kotlin
test {
    // Advance one frame
    advanceFrame()

    // Advance multiple frames
    advanceFrames(60)

    // Advance by approximate seconds (60 FPS)
    advanceSeconds(2.5f)

    // Advance until condition is met (with safety limit)
    val result = advanceUntil(maxFrames = 600) { getVariable("timer") >= 50 }
    result.assertMet("Timer should reach 50")

    // Or use orFail for cleaner syntax
    advanceUntil { getVariable("health") == 0 } orFail "Player should die"

    // Advance while condition is true
    advanceWhile { getVariable("jumping") == 1 }

    // Step one frame with inline assertions
    stepFrame {
        expect("score").toBeGreaterThan(0)
    }

    // Access frame count
    println("Current frame: $frameCount")
}
```

### Input Simulation

```kotlin
test {
    // Tap a button (press for one frame, release)
    tap(Button.A)
    tap(Button.START)

    // Tap multiple buttons simultaneously
    tap(Button.A, Button.B)

    // Hold while executing block
    press(Button.RIGHT) {
        advanceFrames(30)
        expect("playerX").toBeGreaterThan(80)
    }

    // Manual hold and release
    hold(Button.A)
    advanceFrames(10)
    release(Button.A)

    // Release all buttons
    releaseAll()
}
```

Available buttons: `Button.A`, `Button.B`, `Button.START`, `Button.SELECT`, `Button.UP`, `Button.DOWN`, `Button.LEFT`, `Button.RIGHT`

### Fluent Assertions

Integer expectations:

```kotlin
test {
    expect("score").toEqual(100)
    expect("health").toBeGreaterThan(0)
    expect("lives").toBeAtLeast(1)
    expect("timer").toBeLessThan(60)
    expect("ammo").toBeAtMost(99)
    expect("x").toBeBetween(0..160)
    expect("count").toBeZero()
    expect("money").toBePositive()
    expect("velocity").toBeNegative()
    expect("value").toSatisfy("is even") { it % 2 == 0 }
}
```

Sprite expectations:

```kotlin
test {
    expectSprite("player").toBeAt(80, 72)
    expectSprite("player").toHaveX(80)
    expectSprite("player").toHaveY(72)
    expectSprite("player").toBeVisible()
    expectSprite("enemy").toBeHidden()
    expectSprite("hero").toBePlayingAnimation("run")
    expectSprite("idle_enemy").toNotBeAnimating()
    expectSprite("player").toCollideWith(simulation.getSprite("enemy")!!)
    expectSprite("player").toNotCollideWith(simulation.getSprite("wall")!!)
}
```

Pool expectations:

```kotlin
test {
    expectPool("bullets").toHaveActiveCount(5)
    expectPool("particles").toBeEmpty()
    expectPool("enemies").toNotBeEmpty()
    expectPool("bullets").toHaveSpace()
    expectPool("bullets").toHaveSpaceFor(3)
    expectPool("bullets").toBeFull()

    // Check all/any entities match condition
    expectPool("bullets").allMatch("moving up") { idx ->
        getVariable("bullet_${idx}_vel_y") < 0
    }
    expectPool("enemies").anyMatch("on screen") { idx ->
        getVariable("enemy_${idx}_x") in 0..160
    }
}
```

Game/scene expectations:

```kotlin
test {
    game.toBeInScene("gameplay")
    game.toHaveFrameCount(100)
    game.toHaveRunForAtLeast(60)
    expectScene("gameplay")
}
```

### State Access

```kotlin
test {
    // Get variable value
    val health = getVariable("health")

    // Set variable directly (for test setup)
    setVariable("score", 1000)

    // Access current scene
    println("In scene: $currentScene")

    // Direct scene entry (for test setup)
    enterScene("gameplay")

    // Listen for scene changes
    onSceneChange { from, to ->
        println("Scene changed: $from -> $to")
    }
}
```

### IR Verification (Advanced)

For testing that your DSL generates correct IR:

```kotlin
import io.github.gbkt.core.test.*
import io.github.gbkt.core.ir.*

@Test
fun `assignment generates correct IR`() {
    val ir = recordIR {
        playerX += 1
    }

    assertTrue(ir.containsType<IRAssign>())
    val assigns = ir.filterType<IRAssign>()
    assertEquals("playerX", assigns.first().target)
}
```

## RPG Stats System

gbkt provides a complete RPG stats system for turn-based games with character stats, leveling, and combat calculations.

### Character Definition

```kotlin
// Define a playable character with stats and leveling
val hero by character {
    name("Hero")

    // Base stats
    stats {
        hp(100)      // Health points
        sp(50)       // Skill/magic points
        atk(15)      // Physical attack
        def(10)      // Physical defense
        matk(8)      // Magic attack
        mdef(8)      // Magic defense
        agl(12)      // Agility (turn order)
        acc(95)      // Accuracy (hit chance)
        eva(5)       // Evasion
    }

    // Leveling configuration
    level(startLevel = 1, maxLevel = 99, expCurve = ExpCurve.STANDARD)

    // Level-up stat bonuses
    onLevelUp {
        stats.hp += 10
        stats.sp += 3
        stats.atk += 2
        stats.def += 2
    }
}

// Experience curves available:
// ExpCurve.FLAT         - Same XP per level
// ExpCurve.LINEAR       - Linear increase
// ExpCurve.STANDARD     - Balanced curve (default)
// ExpCurve.STEEP        - Slow early, fast late
// ExpCurve.GRADUAL      - Fast early, slow late
```

### Stats Access

```kotlin
// Read stats
val currentHp = hero.stats.hp
val maxHp = hero.stats.maxHp

// Modify stats during gameplay
hero.stats.hp -= 10                    // Take damage
hero.stats.hp set hero.stats.maxHp     // Full heal

// Check conditions
whenever(hero.stats.hp isBelow 20) { showLowHealthWarning() }
whenever(hero.stats.sp isAtLeast fireball.cost) { enableFireball() }
```

### Custom Stats

```kotlin
stats {
    // Built-in stats work as before
    hp(100, max = 999)
    sp(50)
    atk(15)
    def(10)

    // Add custom stats (max 3 to preserve Game Boy memory)
    custom("luck", "LCK", base = 10, max = 99)
    custom("faith", "FAI", base = 5)
    custom("charisma", base = 8, use16Bit = true)  // For values > 255

    // Alias built-in stat display names
    alias(StatType.HP, "LIFE")
    alias(StatType.SP, "MANA")
}
```

### Character Base Attack

```kotlin
// Define class-specific base attack
val druidClass by character {
    name("Druid")
    stats { hp(80); sp(60); matk(15); mdef(12) }

    baseAttack {
        targeting(TargetingMode.SINGLE_ENEMY)
        aspect(Aspect.NONE)
        execute {
            target.damage(caster.stats.matk)
        }
    }
}

val fighterClass by character {
    name("Fighter")
    stats { hp(120); atk(20); def(15) }

    baseAttack {
        targeting(TargetingMode.SINGLE_ENEMY)
        execute {
            target.damage(caster.stats.atk * 2)
        }
    }
}
```

### Level Cap Configuration

```kotlin
val game = game("MyGame") {
    config {
        maxLevel(50)   // Classic short game (default: 99)
        // or maxLevel(255)  // Extended progression
    }
}
```

## Battle System

Turn-based combat driven by a generated combat state machine with victory/defeat callbacks.

> **Note:** Use `simpleBattle()` for v1. The `combatEngine()` DSL is experimental and will be revised in a future release.

### Battle Definition

```kotlin
// simpleBattle(id) registers a turn-based combat system and returns a BattleRef
val combat = simpleBattle("combat") {
    party(hero)                       // CharacterRef(s) in the party
    encounter { +goblin }             // MonsterRefs in the encounter
    onVictory { navigate(explorationScene) }
    onDefeat { navigate(gameoverScene) }
}

// The battle scene must drive the state machine every frame
scene("battle") {
    frame {
        battleUpdate(combat)
        whenever(combatIsInState(CombatStates.VICTORY, combat)) {
            // handle victory UI, etc.
        }
    }
}
```

### Battle States

Combat states are `CombatStateId` constants on `CombatStates` (generated as
`COMBAT_STATE_*` in C). Query them with `combatIsInState(state, battleRef)`:

```kotlin
CombatStates.INIT             // Setting up combatants
CombatStates.PLAYER_TURN      // Waiting for player command
CombatStates.TARGET_SELECT    // Player selecting target
CombatStates.EXECUTE_ACTION   // Action being performed
CombatStates.ENEMY_TURN       // AI computing/performing action
CombatStates.VICTORY          // Party wins
CombatStates.DEFEAT           // Party loses
CombatStates.FLEEING          // Party fleeing
CombatStates.WAITING          // Idle/waiting
```

> **Stale-API caveat:** the Battle Menu, Combat Formulas, and Custom Battle States
> subsections below describe APIs (`battleMenu`, `combatFormulas`, `battleState`,
> `battleTransition`) that do not exist in the current codebase. Cross-check
> `gbkt-genre-rpg/.../dsl/RpgExtensions.kt` for the implemented RPG DSL entry points.

### Battle Menu

```kotlin
val battleMenu by battleMenu("menu") {
    position(0, 12)           // Menu position (tile coords)

    // Main commands
    commands {
        command("Attack") { action(ActionType.ATTACK) }
        command("Magic") { submenu(magicMenu) }
        command("Item") { submenu(itemMenu) }
        command("Flee") { action(ActionType.FLEE) }
    }

    // Status display configuration
    statusDisplay {
        showHp(true)
        showSp(true)
        showStatusIcons(true)
        position(0, 0)
    }
}
```

### Combat Formulas

```kotlin
val combat = combatFormulas {
    // Hit formula strategies
    d20HitRoll(baseAC = 10)           // D&D-style: roll + ATK vs DEF + AC
    percentageHitChance(baseChance = 80, minChance = 20, maxChance = 95, perDiff = 3)
    agilityBasedHit(baseChance = 70)  // Hit based on AGL difference
    alwaysHits()                       // No miss chance

    // Critical hit strategies
    criticalChance(5)                  // Flat 5% chance
    criticalOnHighRoll(threshold = 20, dieSize = 20)  // Natural 20
    noCriticalHits()                   // Disable crits
    criticalMultiplier(200)            // 2x damage on crit

    // Damage variance strategies
    damageVariance(25)                 // ±12.5% variance
    damageMultiplierRange(min = 75, max = 125)  // Lookup table
    noVariance()                       // Exact damage

    // Fumble system
    enableFumble(threshold = 1)        // Fumble on natural 1
}
```

### Custom Battle States

```kotlin
val game = game("MyGame") {
    // Define custom battle states beyond the 19 built-in states
    val cutsceneState by battleState("Cutscene")
    val animationState by battleState("Animation")

    val combat by battle("combat") {
        onState(cutsceneState) {
            // Custom cutscene logic
        }
    }

    scene("battle") {
        frame {
            battleTransition(cutsceneState)  // Transition to custom state
        }
    }
}
```

## Item & Inventory System

Complete item management with consumables, equipment, and stacking.

> **Stale-API caveat:** the implemented core entry points are `items { item("potion") { ... } }`
> (ItemCatalogBuilder, category is a string) and `container("inventory") { slots(16) }` — see
> `gbkt-lang/.../dsl/InventoryBuilders.kt`. The `by item` delegate and `ItemCategory` enum below
> are not in the current core DSL; equipment slots/stat bonuses live in the RPG genre plugin
> (`equipmentSystem`, `EquipSlot` in `gbkt-genre-rpg`).

### Item Definition

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

## Ability System

Define abilities with costs, targeting, and custom effects.

### Ability Definition

```kotlin
// Single-target offensive ability
val fireball by ability {
    name("Fireball")
    description("Deals fire damage to one enemy")
    cost(sp = 8)
    targeting(TargetingMode.SINGLE_ENEMY)
    aspect(Aspect.FIRE)

    execute {
        val damage = caster.stats.matk * 2
        target.damage(damage, Aspect.FIRE)
    }
}

// Multi-target healing ability
val healAll by ability {
    name("Heal All")
    description("Heals all party members")
    cost(sp = 20)
    targeting(TargetingMode.ALL_ALLIES)

    execute {
        target.heal(caster.stats.matk)
    }
}

// Status-inflicting ability
val poison by ability {
    name("Poison")
    cost(sp = 5)
    targeting(TargetingMode.SINGLE_ENEMY)

    execute {
        target.inflictStatus(poisonEffect, duration = 5)
    }
}

// Ability with level unlock
val fireball by ability {
    name("Fireball")
    unlocksAt(level = 10)  // Unlocks at level 10
    cost(sp = 12)
    targeting(TargetingMode.SINGLE_ENEMY)
    aspect(Aspect.FIRE)

    execute {
        target.damage(caster.matk * 2, Aspect.FIRE)
    }
}

// Instant kill ability
val quiveringPalm by ability {
    name("Quivering Palm")
    cost(sp = 25)
    targeting(TargetingMode.SINGLE_ENEMY)

    execute {
        instantKill(chance = 12, ignoreImmunity = false)
        target.damage(caster.atk)  // Fallback if instant kill fails
    }
}
```

### Targeting Modes

```kotlin
TargetingMode.SINGLE_ENEMY      // One enemy
TargetingMode.ALL_ENEMIES       // All enemies
TargetingMode.SINGLE_ALLY       // One party member
TargetingMode.ALL_ALLIES        // All party members
TargetingMode.SELF              // Caster only
TargetingMode.SINGLE_ANY        // Any single target
TargetingMode.ALL               // Everyone on battlefield
```

### Elemental Aspects

```kotlin
Aspect.NONE      // No element
Aspect.FIRE      // Fire damage
Aspect.ICE       // Ice damage
Aspect.THUNDER   // Lightning damage
Aspect.EARTH     // Earth damage
Aspect.WIND      // Wind damage
Aspect.WATER     // Water damage
Aspect.LIGHT     // Light/holy damage
Aspect.DARK      // Dark/shadow damage
```

## Monster & AI System

Define monsters with stats, AI behaviors, and loot drops.

### Monster Definition

```kotlin
val goblin by monster {
    name("Goblin")
    tier(MonsterTier.COMMON)

    // Base statistics
    baseStats {
        hp(30)
        atk(8)
        def(5)
        matk(3)
        mdef(3)
        agl(10)
    }

    // Elemental weaknesses/resistances
    aspects {
        weak(Aspect.FIRE)       // Takes 150% fire damage
        resist(Aspect.EARTH)    // Takes 50% earth damage
        immune(Aspect.NONE)     // (optional)
    }

    // AI behavior
    ai {
        // Default targeting
        target { lowestHp() }   // Attack weakest target

        // Conditional behaviors
        whenHpBelow(25) { flee() }
        whenAloneOnField { useAbility(desperateStrike) }
    }

    // Rewards
    exp(15)
    drops {
        item(herb, chance = 30)       // 30% chance
        item(goldCoin, chance = 50)
        gold(10..25)                  // Random gold amount
    }
}
```

### Monster Tiers

```kotlin
MonsterTier.COMMON       // Regular enemies
MonsterTier.UNCOMMON     // Slightly stronger
MonsterTier.RARE         // Dangerous enemies
MonsterTier.BOSS         // Boss encounters
MonsterTier.MINIBOSS     // Mini-bosses
MonsterTier.LEGENDARY    // Very rare, powerful
```

### AI Target Strategies

```kotlin
ai {
    target { lowestHp() }         // Lowest HP target
    target { highestHp() }        // Highest HP target
    target { random() }           // Random target
    target { lowestDef() }        // Lowest defense
    target { frontRow() }         // Front row priority

    // Conditional AI
    whenHpBelow(50) { useAbility(heal) }
    whenHpBelow(25) { flee() }
    whenAloneOnField { rage() }
    whenAllyKilled { enrage() }
}
```

### Custom Tier Multipliers

```kotlin
// Standard tiers
MonsterTier.COMMON       // 100% stats
MonsterTier.UNCOMMON     // 125% stats (B tier)
MonsterTier.RARE         // 150% stats (A tier)
MonsterTier.BOSS         // 200% stats (S tier)

// Custom tier multiplier (between standard tiers)
val eliteGuard by monster {
    tier(130)  // 130% potency (between B and A tier)
    baseStats { hp(50); atk(12); def(10) }
}
```

### Monster Death Hooks

```kotlin
val deathKnight by monster {
    name("Death Knight")
    baseStats { hp(150); atk(25); def(20); agl(10) }

    onDeath {
        chance(33) {
            revive(hpPercent = 25)  // 33% chance to revive at 25% HP
        }
    }
}

val phoenix by monster {
    name("Phoenix")
    baseStats { hp(200); atk(30); def(15); agl(20) }

    onDeath {
        awardBonusExp(100)          // Award bonus EXP
        transformTo(phoenixReborn)   // Transform into stronger form
    }
}
```

### Monster Hit Hooks (Phasing/Evasion)

```kotlin
val displacerBeast by monster {
    name("Displacer Beast")
    baseStats { hp(80); atk(20); def(15); agl(25) }

    onHit {
        // First 3 attacks are automatically evaded
        hasEvasion {
            decrementEvasion()
            cancelHit()  // Prevents damage
        }
    }
}

val etherealGhost by monster {
    name("Ethereal Ghost")
    baseStats { hp(40); atk(15); def(5); agl(30) }

    onHit {
        chance(50) {
            cancelHit()  // 50% chance to phase through attacks
        }
    }
}

// Hit hook methods:
// cancelHit()              - Prevents damage from being applied
// modifyDamage(multiplier) - Scale damage (50 = halve, 200 = double)
// decrementEvasion()       - Decrement monster's evasion counter
// hasEvasion { ... }       - Execute if evasion counter > 0
```

## Status Effect System

Define status effects with duration, stacking, and per-turn effects.

### Status Effect Definition

```kotlin
val poisonEffect by statusEffect {
    name("Poison")
    icon(StatusIcon.POISON)

    // Duration in turns
    duration(5)

    // Stack behavior
    stackMode(StackMode.REFRESH_DURATION)  // Reset timer on reapply
    // Other modes:
    // StackMode.STACK_INTENSITY  - Increase damage
    // StackMode.NO_STACK         - Ignore new applications

    // Per-turn effect
    onTurnStart { /* triggered at turn start */ }
    onTurnEnd {
        // Deal damage at end of turn
        target.damage(target.stats.maxHp / 10)
    }

    // On removal
    onRemove { /* clean up */ }
}

// Buff example
val attackUp by statusEffect {
    name("ATK Up")
    icon(StatusIcon.BUFF)
    duration(3)

    // Stat modifier while active
    stats {
        atk(+25)  // +25% attack
    }
}
```

### Stack Modes

```kotlin
StackMode.REFRESH_DURATION    // Resets duration, same intensity
StackMode.STACK_INTENSITY     // Increases effect strength
StackMode.STACK_DURATION      // Adds to duration
StackMode.NO_STACK            // Ignores new applications
```

### Duration Modes

```kotlin
// Turn-based (default for RPGs)
val poisonEffect by statusEffect {
    duration(5)  // 5 turns
    durationMode(EffectDurationMode.TURNS)  // Optional, default
}

// Frame-based (for action games)
val speedBoost by statusEffect {
    duration(180)  // 180 frames (~3 seconds at 60fps)
    durationMode(EffectDurationMode.FRAMES)
}

// Perpetual (never expires)
val curseEffect by statusEffect {
    perpetual()  // Uses EffectDurationMode.PERPETUAL
}
```

### Damage/Healing Multipliers

```kotlin
val hasteEffect by statusEffect {
    buff()
    duration(3)
    doubleDamage()       // 200% damage output
    doubleHealing()      // 200% healing output
}

// Custom percentages
val weakenedEffect by statusEffect {
    debuff()
    duration(4)
    damageMultiplier(50)     // 50% damage output
    healingMultiplier(75)    // 75% healing output
}
```

### Incoming Damage/Healing Modifiers

```kotlin
val barkskinEffect by statusEffect {
    buff()
    duration(3)
    halveIncomingDamage()  // 50% damage taken
}

val vulnerableEffect by statusEffect {
    debuff()
    duration(2)
    doubleIncomingDamage()  // 200% damage taken
}

// Custom percentages
val protectEffect by statusEffect {
    buff()
    incomingDamageMultiplier(75)   // 75% damage taken
    incomingHealingMultiplier(125) // 125% healing received
}
```

### Hit Chance/Evasion Modifiers

```kotlin
val sleetStormEffect by statusEffect {
    debuff()
    duration(3)
    reduceHitChance(50)  // -50% hit chance while active
}

val blurEffect by statusEffect {
    buff()
    duration(3)
    increaseEvasion(50)  // +50% evasion (harder to hit)
}

// Raw values (-100 to +100)
val accuracyUp by statusEffect {
    buff()
    hitChanceModifier(25)   // +25% hit chance
    evasionModifier(-10)    // -10% evasion
}
```

### Target Redirect (Confusion/Charm)

```kotlin
val confused by statusEffect {
    debuff()
    duration(3)
    confuseRandomly()  // Attack any random target (ally, enemy, or self)
}

val charmed by statusEffect {
    debuff()
    duration(2)
    redirectToAllies()  // Attack own allies instead of enemies
}

val selfDestructive by statusEffect {
    debuff()
    duration(1)
    redirectToSelf()  // Always attack self
}

val betrayed by statusEffect {
    debuff()
    duration(2)
    redirectToOpposite()  // Attack opposite side from intended
}
```

### Action Prevention (Stun/Trip)

```kotlin
val stunEffect by statusEffect {
    category(EffectCategory.CONDITION)
    duration(2)
    preventsAction()  // Causes turn skipping
}

val trippedEffect by statusEffect {
    category(EffectCategory.CONDITION)
    duration(1)
    preventsAction()  // Single turn skip (trip/prone)
}
```

## Floor & Dungeon System

Multi-floor dungeon management with maps, exits, and encounter tables.

### Floor Definition

```kotlin
val floor1 by floor {
    name("Dungeon Level 1")
    defaultPosition(5, 5)       // Starting tile
    defaultMap("entrance")      // Starting map

    // Define maps within the floor
    map("entrance") {
        tileset("dungeon_tiles.png")
        size(32, 32)
        data(entranceMapData)   // IntArray of tile indices

        // Collision configuration
        walls(0, 1, 2)          // Tile indices that block movement
    }

    map("hallway") {
        tileset("dungeon_tiles.png")
        size(64, 32)
        data(hallwayMapData)
    }

    // Define exits between maps
    exits {
        door(from = "entrance" at 15 x 5, to = "hallway" atDest 0 x 5)
        stairsDown(from = "hallway" at 60 x 10, to = floor2 at 5 x 5)
    }

    // Floor-specific palettes
    palettes(0, 1, 2)

    // Callbacks
    onEnter { showMessage("You enter the dungeon...") }
    onExit { saveProgress() }
}
```

### Exit Types

```kotlin
// In exits {} block:
door(from = ..., to = ...)           // Standard door
stairsUp(from = ..., to = ...)       // Stairs going up
stairsDown(from = ..., to = ...)     // Stairs going down
ladder(from = ..., to = ...)         // Ladder
portal(from = ..., to = ...)         // Teleporter
auto(from = ..., to = ...)           // Invisible/auto-trigger
```

## Encounter System

Random encounters with weighted monster tables.

### Encounter Table Definition

```kotlin
val floor1Encounters = encounterTable("floor1") {
    safeSteps(10)             // Steps before encounters possible
    initialChance(5)          // Starting chance (out of 256)
    incrementPerStep(3)       // Chance increase per step
    maxChance(128)            // Maximum encounter chance

    // Weighted encounter entries
    entry(weight = 30) { +goblin }                    // Single goblin
    entry(weight = 25) { +goblin; +goblin }           // Two goblins
    entry(weight = 20) { +slime }                     // Single slime
    entry(weight = 15) { +goblin; +slime }            // Mixed group
    entry(weight = 10) { +bugbear }                   // Rare: bugbear
}

// Attach to floor
val floor1 by floor {
    // ... map definitions ...
    encounters(floor1Encounters)
}
```

## Global Flags System

Persistent boolean flags organized into pages for game state tracking.

### Flags Definition

```kotlin
val gameFlags by flags {
    // Story progress flags
    page("story") {
        flag("metElder")
        flag("acceptedQuest")
        flag("foundKey")
        flag("defeatedBoss")
    }

    // Collection flags
    page("items") {
        flag("hasSword")
        flag("hasShield")
        flag("foundTreasure1")
        flag("foundTreasure2")
    }

    // World state
    page("world") {
        flag("doorUnlocked")
        flag("bridgeBuilt")
        flag("secretRevealed")
    }
}
```

### Flag Operations

```kotlin
// Set flags
gameFlags.set("story", "metElder")
gameFlags["story"]["acceptedQuest"] = true

// Clear flags
gameFlags.clear("story", "metElder")

// Check flags
whenever(gameFlags.isSet("story", "metElder")) {
    showElderDialogue()
}

// Toggle
gameFlags.toggle("world", "doorUnlocked")
```

### Complete Example

```kotlin
class PlatformerTest {
    @Test
    fun `player jumps when A pressed on ground`() = testGame("platformer") {
        var playerY by u8Var(100)  // Ground level
        var velocityY by i8Var(0)
        var jumping by u8Var(0)

        val gameplay = scene("gameplay") {
            frame {
                // Jump when A pressed and on ground
                whenever(buttons.a.pressed and (jumping isEqualTo 0)) {
                    velocityY set -8
                    jumping set 1
                }

                // Apply gravity
                whenever(jumping isEqualTo 1) {
                    playerY += velocityY
                    velocityY += 1

                    // Land
                    whenever(playerY isAtLeast 100) {
                        playerY set 100
                        jumping set 0
                    }
                }
            }
        }
        start = gameplay

        test {
            // Initially on ground
            expect("playerY").toEqual(100)
            expect("jumping").toEqual(0)

            // Press A to jump
            tap(Button.A)
            expect("jumping").toEqual(1)
            expect("velocityY").toEqual(-8)

            // Should rise
            advanceFrames(5)
            expect("playerY").toBeLessThan(100)

            // Wait to land
            advanceUntil { getVariable("jumping") == 0 } orFail "Player should land"

            // Back on ground
            expect("playerY").toEqual(100)
            expect("jumping").toEqual(0)
        }
    }
}
```
