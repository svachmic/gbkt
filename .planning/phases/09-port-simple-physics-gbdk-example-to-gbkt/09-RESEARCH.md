# Phase 9: Port simple_physics GBDK example to gbkt — Research

**Researched:** 2026-05-13
**Domain:** GBDK reference port — actor/input/i16/shr codegen oracle validation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Anti-overfitting doctrine (overarching guardrail):**
- D-overfitting-1: Do not add DSL features just to make THIS port pretty.
- D-overfitting-2: Do not tune codegen visitors to this example's shape cosmetically.
- D-overfitting-3: Do not let GBDK reference style become THE gbkt style. Reference is codegen oracle, not DSL template.

**UAT contract floor:**
- D-01: 3 core behaviors — D-pad accel+clamp; A-press jump impulse; decel-to-rest. No pixel-perfect trajectory parity.
- D-02: MCP play-through + screenshot per behavior (visual-evidence rule; Phase 07.4 plan 18 burned 5 plans on variable-only gap).
- D-03: UAT first — Plan 1 = lock 09-UAT.md + PLAYBOOK.md with NO DSL yet.

**Named codegen bug-fix:**
- D-04: Exploratory — port first, name the bug after the first build.
- D-05: Surplus defects to seeds via `/gsd-capture --seed`; if >= 1 surplus seed at port-close, insert Phase 9.1 placeholder in ROADMAP.

**Idiomatic mapping:**
- D-06: Single `play` scene, no title. `enter { }` + `frame { }`.
- D-07: PNG asset via `asset("sprites/smiley.png")`. 8x8 4-frame sprite.
- D-08: Raw `i16Var` + manual `shr 4` mirroring reference 12.4 fixed-point. NO actor FP88. NO new FP12.4 mode.

**Three-signal comparison artifact:**
- D-09: ROM size + generated-C diff + UAT verdict. No .asm diff, no bank/section capture.
- D-10: Artifacts at `.planning/phases/09-.../evidence/reference/`. Reference C + BUILD.md + oracle-comparison.md committed. Reference .gb gitignored.
- D-11: 3 JVM-tier emission invariants (one per UAT behavior); awk brace-walk for per-function scope — no file-level grep counts.

### Claude's Discretion

- Plan count / wave structure.
- Phase 07.9 deliverable mapping (planner reads `gbkt-backend-gbdk/CLAUDE.md` directly).
- PNG asset specifics (8x8 4-frame; whether all 4 frames are used is a discovery moment).

### Deferred Ideas (OUT OF SCOPE)

- FP12.4 actor mode, inline tile-data DSL, .asm diff oracle, bank/section size capture, title screen, scene-less main loop, Phase 9.1 pre-insertion, reuse-an-existing-sprite shortcut.

</user_constraints>

---

## Executive Summary

Phase 9 re-implements the 99-line GBDK `simple_physics` example as an idiomatic gbkt DSL game. The reference is already built and measured: **32768-byte ROM** (standard minimum GB header-pad), **574 bytes actual code** (0x23E), **187 bytes HOME** (0xBB).

The DSL primitive surface needed is narrow: `i16Var`, `shr`, compound `+=`/`-=`, `whenever` with signed comparisons, `buttons.a.pressed`, `dpad.*.held`, single `scene` with `enter`/`frame`, one `actor` with PNG sprite, and a sprite-position update each frame. All of these primitives exist in the current codebase.

The critical gap is that `ActorRef.moveTo(x: Int, y: Int)` only accepts `Int` — there is no `moveTo(x: Expr, y: Expr)` overload. The physics translation `posX shr 4` produces an `Expr`, so the port cannot call `moveTo(posX shr 4, posY shr 4)` without either (a) a new overload, or (b) writing the position update as direct variable assignment. `ScriptBuilder.setPosition(actorId, x: Expr, y: Expr)` exists at the builder level but has no public `ActorRef` wrapper. This is the top candidate for the named codegen bug fix (D-07 / D-04).

A second codegen gap exists: the `visitLiteral` path in `ExprVisitor` emits ALL `Literal(N)` as `CLiteral(N)` regardless of whether the comparison context is signed. `CLiteral(64)` emits as `64u`. So `whenever(spdX isAbove 64)` where `spdX` is `i16Var` will emit `_spdX > 64u` — a signed-vs-unsigned comparison that SDCC optimises incorrectly (SDCC warning 94). Phase 07.9 fixed hardcoded `CIntLiteral` sites in visitors but left the DSL-path open: user-authored `whenever(signedVar isAbove positiveInt)` still emits `u`. This fires in phys.c's positive-bound clamp lines (`SpdX > MAX_X_SPEED_IN_SUBPIXELS`, `SpdY > MAX_Y_SPEED_IN_SUBPIXELS`). Negative-literal comparisons (`SpdX < -MAX`) work accidentally because `CLiteral(-64)` emits `-64` (no `u` suffix on negatives).

**Primary recommendation:** Plan 1 = UAT lock (D-03). Plan 2 = DSL port + first build. Plan 3 = diagnose first-blocker (name the bug per D-04). Plan 4 = fix + JVM emission invariants. Plan 5 = three-signal comparison + close.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Physics update (vel += accel, pos += vel) | gbkt DSL / frame loop | gbkt-ir ScriptOp codegen | All physics is manual arithmetic in the frame block — no genre system involved |
| Sprite position sync (`move_sprite`) | gbkt-backend-gbdk ActorVisitor | gbkt-ir SetPosition / actor x/y globals | Actor x/y globals are written by frame ops; ActorVisitor emits move_sprite sync after each frame block |
| Input polling | gbkt DSL `dpad.*.held` / `buttons.a.pressed` | gbkt-backend-gbdk ScriptOpVisitor | Lowers to INPUT_KEY / INPUT_KEYPRESS emission in generated C |
| Signed arithmetic for i16Var | gbkt-lang AssignableVar operators | gbkt-backend-gbdk ExprVisitor + CEmitter | Compound `+=`/`-=` uses AssignOp.ADD/SUB; SHR uses BinaryOp.SHR |
| Asset loading | gbkt-core asset pipeline | gbkt-gradle-plugin | PNG sprite processed by pipeline, tile-data array emitted in generated C |
| ROM build | gbkt-gradle-plugin (lcc invocation) | GBDK_HOME toolchain | Plugin detects GBDK, invokes lcc with standard flags |
| UAT / runtime verification | MCP gbkt-emulator | gbkt-test GbktTestExtension | MCP provides emulator_screenshot for visual-evidence rule |

---

## Reference Code Analysis (phys.c)

Reference: `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c`  
99 lines. Confirmed read. [VERIFIED: direct file read]

### Line-by-line DSL mapping

| phys.c fragment | DSL primitive | Notes / codegen bug candidate |
|-----------------|---------------|-------------------------------|
| `int16_t PosX, PosY, SpdX, SpdY` (L37-39) | `var posX by i16Var(1024)` etc. | `i16Var` exists; maps to `INT16` global in generated C [VERIFIED] |
| `set_sprite_data(0, 4, sprite_data)` (L51) | `actor { sprite(asset("sprites/smiley.png")) { size(8,8) } }` | Asset pipeline produces equivalent tile-data array; OAM init emits `set_sprite_data` call [VERIFIED: ActorVisitor.generateOAMInit] |
| `set_sprite_tile(0, 0)` (L54) | Automatic — part of actor OAM init | ActorVisitor handles this [VERIFIED] |
| `SHOW_BKG; SHOW_SPRITES` (L57) | `showSprites()` in `enter { }` | `SHOW_BKG` is automatic from scene codegen; `showSprites()` emits `SHOW_SPRITES` |
| `PosX = PosY = PIXELS_TO_SUBPIXELS(64)` (L59) | `posX set 1024; posY set 1024` | Literal 1024 = 64 * 16 |
| `SpdX = SpdY = 0` (L60) | `spdX set 0; spdY set 0` | |
| `INPUT_PROCESS` (L64) | Implicit — gbkt input system polls joypad every frame | No DSL call needed |
| `if (INPUT_KEY(J_UP)) SpdY -= 2` (L67) | `whenever(dpad.up.held) { spdY -= 2 }` | [VERIFIED: InputBuilders.kt `dpad.up.held`] |
| `if (SpdY < -MAX_Y_SPEED) SpdY = -MAX_Y_SPEED` (L69) | `whenever(spdY isBelow -64) { spdY set -64 }` | Negative literal `-64` emits as `-64` (no `u`). Correct. [VERIFIED: CEmitter CLiteral negative path] |
| `else if (INPUT_KEY(J_DOWN)) SpdY += 2` (L70-72) | `whenever(dpad.down.held) { spdY += 2 }` — must be outside the `whenever(up.held)` block | DSL else-if requires nesting or separate `whenever`; separate `whenever` is idiomatic |
| `if (SpdY > MAX_Y_SPEED) SpdY = MAX_Y_SPEED` (L72) | `whenever(spdY isAbove 64) { spdY set 64 }` | **BUG CANDIDATE**: `CLiteral(64)` emits `64u`; signed comparison `_spdY > 64u` → SDCC warning 94 → always-false when spdY is positive [VERIFIED: ExprVisitor.visitLiteral + CEmitter] |
| `if (INPUT_KEY(J_LEFT)) SpdX -= 2` (L74) | `whenever(dpad.left.held) { spdX -= 2 }` | |
| `if (SpdX < -MAX_X_SPEED) SpdX = -MAX_X_SPEED` (L76) | `whenever(spdX isBelow -64) { spdX set -64 }` | Negative literal, correct |
| `else if ... SpdX += 2` (L77-79) | `whenever(dpad.right.held) { spdX += 2 }` | |
| `if (SpdX > MAX_X_SPEED) SpdX = MAX_X_SPEED` (L79) | `whenever(spdX isAbove 64) { spdX set 64 }` | **BUG CANDIDATE** same as SpdY upper bound |
| `if (INPUT_KEYPRESS(J_A)) SpdY = -JUMP_ACCEL` (L82-84) | `whenever(buttons.a.pressed) { spdY set -512 }` | JUMP_ACCEL = 32 → -32 * 16 = -512 in subpixels. Edge-detect pressed vs held [VERIFIED: InputBuilders.kt] |
| `PosX += SpdX, PosY += SpdY` (L87) | `posX += spdX; posY += spdY` | `plusAssign(AssignableVar)` exists [VERIFIED: VariableBuilders.kt line 137] |
| `move_sprite(0, SpdX>>4, SpdY>>4)` (L90) | **GAP**: `smiley.moveTo(posX shr 4, posY shr 4)` — but `moveTo(Int, Int)` only | **PRIMARY BUG CANDIDATE**: no `ActorRef.moveTo(Expr, Expr)` exists. Work-around: write x/y via direct variable assign. [VERIFIED: ActorBuilder.kt line 331] |
| `if (SpdY < 0) SpdY++; else if (SpdY) SpdY--` (L93) | `whenever(spdY isBelow 0) { spdY++ }` then needs else-if for positive | DSL `++spdY` emits `_spdY = _spdY + 1` not `_spdY++` (shape difference, correct semantics) |
| `if (SpdX < 0) SpdX++; else if (SpdX) SpdX--` (L94) | `whenever(spdX isBelow 0) { spdX++ }` then positive decel | |
| `vsync()` (L97) | Automatic — gbkt frame loop calls `vsync()` at end of every frame | No DSL call needed |

### Bug candidate ranking by phys.c firing frequency

1. **Positive-literal signed comparison** (`isAbove 64`) — fires on 4 clamp lines (SpdX upper, SpdY upper in both directions). This is a real correctness bug: the clamp never fires, so speed is unbounded. [VERIFIED]
2. **`moveTo(Expr, Expr)` gap** — fires on the one `move_sprite` line. Without a fix, the port must use a workaround (direct variable assign into actor position globals, which is not idiomatic but correct). [VERIFIED]
3. **`++spdY` DSL emission shape** — fires on decel lines. Not a bug — emits `_spdY = _spdY + 1` which is semantically identical. Informational only.
4. **i16 compound `-=` with Expr arg** (`spdY -= 2`) — `minusAssign(Int)` is available; lowers to `_spdY -= 2u`. The RHS `2u` is an unsigned constant being subtracted from a signed variable. SDCC handles this correctly for small constants (no sign extension issue for subtraction RHS). Low risk.

---

## DSL Primitive Inventory

| Primitive | Status | Source | Notes |
|-----------|--------|--------|-------|
| `var x by i16Var(N)` | Exists | `gbkt-lang/VariableBuilders.kt:403-442` [VERIFIED] | `I16VarDelegate` → `VarType.I16` → `CI16` in pipeline |
| `var x by i16Var` compound `+=` / `-=` | Exists | `VariableBuilders.kt:128-149` [VERIFIED] | `plusAssign(AssignableVar)` emits `SET` op |
| `spdX shr 4` (infix on AssignableVar) | Exists | `VariableBuilders.kt:321-323` [VERIFIED] | Returns `Expr` via `BinaryOp.SHR` → `>>` in C |
| `whenever(spdX isBelow -64)` | Exists, correct | `ExprBuilder.kt:114; CEmitter:421` [VERIFIED] | Negative literal emits without `u` |
| `whenever(spdX isAbove 64)` | Exists, **BROKEN** | `ExprBuilder.kt:103; ExprVisitor:77; CEmitter:421` [VERIFIED] | `CLiteral(64)` → `64u`; signed cmp always-false |
| `dpad.up.held` / `dpad.down.held` | Exists | `gbkt-lang/InputBuilders.kt` [VERIFIED: CONTEXT.md ref] | D-pad held input |
| `dpad.left.held` / `dpad.right.held` | Exists | `gbkt-lang/InputBuilders.kt` [VERIFIED] | |
| `buttons.a.pressed` | Exists | `gbkt-lang/InputBuilders.kt` [VERIFIED: CONTEXT.md ref] | Edge-detect (rising edge) |
| `scene("play") { enter { } frame { } }` | Exists | `gbkt-lang/SceneBuilder.kt` [VERIFIED: CONTEXT.md ref] | |
| `val smiley by actor { position(64,64); sprite(asset(...)) }` | Exists | `gbkt-lang/ActorBuilder.kt` [VERIFIED: pong example] | |
| `smiley.moveTo(Int, Int)` | Exists | `ActorBuilder.kt:331` [VERIFIED] | Int only — no Expr overload |
| `smiley.moveTo(Expr, Expr)` | **MISSING** | `ActorBuilder.kt:331` [VERIFIED absent] | Primary bug candidate |
| `ScriptBuilder.setPosition(actorId, Expr, Expr)` | Exists (internal) | `ScriptBuilder.kt:209` [VERIFIED] | Not publicly accessible via `ActorRef` |
| `posX += spdX` (var += var) | Exists | `VariableBuilders.kt:137` [VERIFIED] | `plusAssign(AssignableVar)` |
| `showSprites()` | Exists | [VERIFIED: pong example enter block] | |
| PNG 8x8 asset via `asset("sprites/smiley.png")` | Exists | [VERIFIED: pong 8x8 ball.png, breakout ball.png] | Asset pipeline handles 8x8 |
| `start = playScene.id` | Exists | [VERIFIED: pong `start = titleScene.id`] | |

---

## Named-Bug Candidate Selection (D-07 pre-research)

D-07 says: "one named codegen bug — pick during port from screening list (i16 `-=`, signed cmp vs negative literal, shr on i16, compound-assign chains) — surplus → seeds".

The screening list in D-07 is approximate. Research has identified two clear bugs fired by phys.c:

### Bug A: Positive-literal signed comparison (`isAbove N` where N > 0, var is i16/i8)

**Root cause:** `ExprVisitor.visitLiteral` emits all `Literal(N)` as `CLiteral(N)`. `CEmitter` emits `CLiteral(N >= 0)` as `Nu` (unsigned suffix). A DSL-authored `whenever(spdX isAbove 64)` produces `_spdX > 64u` in C. SDCC warning 94: comparison of signed value with unsigned constant is always false when the variable is negative. In phys.c, this means the upper-bound speed clamp (`SpdX > MAX_X_SPEED_IN_SUBPIXELS`) never fires — speed is unbounded in the positive direction.

**Scope:** Affects ANY user-authored `whenever(signedVar isAbove positiveInt)` or `whenever(signedVar isAtLeast positiveInt)`. Phase 07.9 fixed hardcoded visitor sites (bucket-a) but left the DSL-authored path. This is the "bucket-a DSL path" gap.

**Fix shape:** Add signed-context awareness to `ExprVisitor.visitBinaryExpr` — when the operator is a comparison (`<`, `>`, `<=`, `>=`, `==`, `!=`) and the LHS is a known signed variable, emit the RHS `Literal` as `CIntLiteral` instead of `CLiteral`. [ASSUMED: exact fix implementation; needs design at plan time]

**phys.c firing lines:** L69 (`SpdY < -MAX` — accidentally correct), L72 (`SpdY > MAX` — broken), L76 (`SpdX < -MAX` — correct), L79 (`SpdX > MAX` — broken). Two broken lines per axis = 4 signed comparison sites directly exercised.

**Verdict: PRIMARY candidate for D-07 named bug.** [VERIFIED: root cause confirmed via code read]

### Bug B: `ActorRef.moveTo(Expr, Expr)` gap

**Root cause:** `moveTo` only accepts `Int` args. The natural DSL expression for the physics-to-pixel translation is `smiley.moveTo(posX shr 4, posY shr 4)` — but `posX shr 4` is an `Expr`, not an `Int`. `ScriptBuilder.setPosition(actorId, Expr, Expr)` exists at the builder level but is not exposed via `ActorRef`.

**Work-around without a fix:** The port can write `_smiley_x` directly via an Assign op. Since `_smiley_x` and `_smiley_y` are the actor position globals, assigning them correctly updates the sprite position (ActorVisitor emits `move_sprite` using these globals after each frame). So `var smileySyncX by u8Var(0); smileySyncX set (posX shr 4); smiley.x set smileySyncX` — awkward but correct. [ASSUMED: exact work-around; planner should evaluate at port time]

**Fix shape:** Add `fun ActorRef.moveTo(x: Expr, y: Expr)` to `ActorBuilder.kt` — one-liner calling `ScriptBuilderContext.current?.setPosition(id, x, y)`. [VERIFIED: `setPosition(actorId, Expr, Expr)` already exists in ScriptBuilder]

**Verdict:** Secondary candidate. The work-around is ugly but compilable, so it may be the named fix or remain a seed depending on what blocks the first build.

### Recommendation to planner

Research cannot pre-lock the named bug (D-04: exploratory). However, Bug A (positive-literal signed comparison) is a correctness defect that silently mis-compiles; Bug B (moveTo gap) is a DSL ergonomics defect. Both are real, well-scoped fixes. The planner should surface both in Plan 3 (first-build analysis) and let the blocking one become the named fix.

---

## Existing gbkt-examples Conventions

[VERIFIED: pong, breakout examples read directly]

### File layout

```
gbkt-examples/simple-physics/         # slug = simple-physics (kebab-case, per examples pattern)
├── build.gradle.kts                   # plugin config (exact copy of pong shape)
├── CLAUDE.md                          # module-specific dev notes
├── PLAYBOOK.md                        # game description + controls + variables ref
├── README.md                          # player-facing readme
├── res/
│   └── sprites/
│       └── smiley.png                 # 8x8 4-frame PNG (4 walking frames per reference shape)
└── src/
    ├── main/
    │   └── kotlin/io/github/gbkt/examples/simple_physics/
    │       └── SimplePhysics.kt       # single-file game DSL definition
    └── test/
        └── kotlin/io/github/gbkt/examples/simple_physics/
            ├── SimplePhysicsIRTest.kt  # IR structure validation
            └── SimplePhysicsGameTest.kt # SimulationContextV2 logic tests
```

### build.gradle.kts shape (copy from pong exactly)

```kotlin
plugins {
    kotlin("jvm")
    id("io.github.gbkt")
}

group = "io.github.gbkt.examples"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform(project(":gbkt-bom")))
    implementation(project(":gbkt-backend-gbdk"))
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))
    testImplementation(project(":gbkt-test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

gbkt {
    game("io.github.gbkt.examples.simple_physics.SimplePhysicsKt::simplePhysics")
    assets("res")
    outputName.set("simple-physics")
}
```

### settings.gradle.kts addition

Add `include("gbkt-examples:simple-physics")` to `/Users/michalsvacha/GitHub/personal/gbkt/settings.gradle.kts` alongside existing example includes (lines 56-64).

### Top-level `val` declaration convention

```kotlin
val simplePhysics = game("SimplePhysics") {
    config { ... }
    // variables
    // actors
    // scenes
    start = playScene.id
}
```

### Single-scene game — no `sceneRef` forward-declaration needed

Since there is only one `play` scene (D-06), the scene reference pattern from pong (forward `val titleRef = sceneRef("title")`) is not needed. `start = playScene.id` directly.

### Asset convention

- PNG sprites in `res/sprites/` [VERIFIED: pong, breakout]
- `asset("sprites/smiley.png")` — path relative to `assets("res")` in build.gradle.kts
- 8x8 pixel sprites (confirmed matching reference's 8x8 tile width)

---

## MCP UAT Harness Format

[VERIFIED: pong PLAYBOOK.md, TESTING.md, UAT_GUIDE.md read directly]

### PLAYBOOK.md format (Plan 1 output)

```markdown
# SimplePhysics

## Overview
Sub-pixel physics demo ported from GBDK simple_physics reference.
One actor (smiley face) controlled via D-pad with 12.4 fixed-point sub-pixel physics.

## How to Play
...

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| play  | UP     | Accelerate sprite upward (SpdY -= 2 subpixels/frame) |
| play  | DOWN   | Accelerate sprite downward |
| play  | LEFT   | Accelerate sprite left |
| play  | RIGHT  | Accelerate sprite right |
| play  | A      | Jump impulse (SpdY = -512 instantly, edge-triggered) |

## Scene Flow
- play (only scene; no navigation)

## Variables Reference
| Variable | Type  | Semantic  | Description |
|----------|-------|-----------|-------------|
| posX     | INT16 | position  | Sub-pixel X position (12.4 fixed-point; divide by 16 for screen pixels) |
| posY     | INT16 | position  | Sub-pixel Y position |
| spdX     | INT16 | velocity  | Sub-pixel X speed (range -64..64) |
| spdY     | INT16 | velocity  | Sub-pixel Y speed (range -64..64) |
```

### Proposed 3 input scripts (one per D-01 behavior)

**Behavior 1: D-pad held → sprite accelerates and clamps at max speed**

```
emulator_start(game="simple-physics")
emulator_step(frames=10)                    # boot
emulator_wait_for_scene(scene="play")
emulator_read_variable("spdX")              # expect: 0
emulator_step(frames=30, buttons=["right"]) # hold right 30 frames (enough to hit clamp at 32 frames: 2*32 >= 64)
emulator_read_variable("spdX")              # expect: 64 (clamped — verifies clamp fired)
emulator_screenshot(label="behavior1-clamp-right-30frames")
emulator_assert([{type:"variable_equals", name:"spdX", expected:64}])
```

Climax frame: frame 30 (held 30 frames; 2 subpixels/frame × 30 = 60 approaching clamp at 64). The screenshot captures the sprite's visual position at that frame.

**Behavior 2: A pressed (edge) → instant Y impulse (jump)**

```
emulator_start(game="simple-physics")
emulator_step(frames=10)
emulator_wait_for_scene(scene="play")
emulator_read_variable("spdY")              # expect: 0
emulator_step(frames=1, buttons=["a"])      # single frame press (edge-triggered)
emulator_read_variable("spdY")              # expect: -512 (= -32 * 16 subpixels)
emulator_screenshot(label="behavior2-jump-impulse-spdY")
emulator_assert([{type:"variable_equals", name:"spdY", expected:-512}])
```

Climax frame: the frame immediately after the A press.

**Behavior 3: D-pad released → sprite decelerates to rest**

```
emulator_start(game="simple-physics")
emulator_step(frames=10)
emulator_wait_for_scene(scene="play")
emulator_step(frames=20, buttons=["right"])  # build up speed (~40 subpixels)
emulator_read_variable("spdX")               # should be ~40
emulator_step(frames=60)                     # no buttons — decel loop fires
emulator_read_variable("spdX")               # expect: 0 (decelerates 1 subpixel/frame → 0 in ≤64 frames)
emulator_screenshot(label="behavior3-decel-rest-60frames")
emulator_assert([{type:"variable_equals", name:"spdX", expected:0}])
```

Climax frame: frame 60 after release.

### Screenshot evidence paths

Screenshots go to `build/gbkt/screenshots/` (auto-path from MCP tool). Additionally, the MCP evidence artifacts get copied to `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/` per D-10.

---

## ROM-Size Baseline

[VERIFIED: reference ROM built during research session]

**Build command:**
```bash
cd /Users/michalsvacha/gbdk/examples/cross-platform/simple_physics
GBDK_HOME=/Users/michalsvacha/gbdk make gb
```

**Reference ROM output:** `build/gb/physics.gb`

**Sizes:**
| Metric | Value |
|--------|-------|
| ROM file size | 32768 bytes (standard minimum — 1 bank, padded to 32KB) |
| Actual code size (`l__CODE`) | 574 bytes (0x23E) |
| HOME bank size (`l__HOME`) | 187 bytes (0xBB) |
| Data size (`l__DATA`) | 26 bytes (0x1A) |

**"Within 2x" target:** gbkt ROM code size <= 1148 bytes actual code. The standard minimum ROM header-pad (32768 bytes file size) will likely be matched exactly for both reference and port — so the meaningful comparison is actual code bytes, not file size.

**Planner note:** The reference ROM exists locally at the path above. It is NOT committed to git (gitignored per D-10 discipline). The `BUILD.md` in `evidence/reference/` will document how to reproduce it.

---

## Validation Architecture

`workflow.nyquist_validation` is absent from `.planning/config.json` — treat as **enabled**.

### Three-signal validation contract

| Signal | What it validates | Test type | Evidence artifact |
|--------|-------------------|-----------|-------------------|
| Codegen quality | Generated C compiles clean; ROM size ≤ 2× reference | Build log (zero lcc warnings) + file size check | `evidence/oracle-comparison.md` §"ROM size" |
| DSL value | gbkt port is shorter/clearer than GBDK C, or documented why not | Generated-C diff (side-by-side) | `evidence/oracle-comparison.md` §"C diff" |
| UAT verdict | 3 D-01 behaviors verified by MCP + screenshots | Runtime MCP play-through | `evidence/uat-screenshots/` per behavior |

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Kotlin test — via `testImplementation(kotlin("test"))`) |
| Config file | None (JUnit 5 auto-discovery) |
| Quick run command | `./gradlew :gbkt-examples:simple-physics:test` |
| Full suite command | `./gradlew :gbkt-examples:simple-physics:test :gbkt-backend-gbdk:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-01.1 | D-pad held → speed accelerates and clamps at ±64 | JVM (emission invariant D-11) + Runtime (MCP) | `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest.accel clamp emission"` | No — Wave 0 |
| D-01.2 | A pressed → instant SpdY = -512 impulse | JVM (emission invariant D-11) + Runtime (MCP) | `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest.jump impulse emission"` | No — Wave 0 |
| D-01.3 | D-pad released → speed decelerates to 0 | JVM (emission invariant D-11) + Runtime (MCP) | `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest.decel to zero emission"` | No — Wave 0 |
| D-11 | Generated C shape locked per 3 behaviors | JVM codegen test (awk brace-walk) | `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest"` | No — Wave 0 |
| D-04 | Named codegen bug fixed (TBD after first build) | JVM RED→GREEN test | To be named after Plan 3 | No |

**Note:** REQUIREMENTS.md does not assign REQ-IDs to Phase 9. The D-IDs from CONTEXT.md serve as requirement anchors for this phase. The planner should note this gap — either map plans to D-IDs explicitly or surface that no REQUIREMENTS.md anchor exists.

### Sampling Rate
- **Per task commit:** `./gradlew :gbkt-examples:simple-physics:test`
- **Per wave merge:** `./gradlew test` (all modules)
- **Phase gate:** Full suite green + 3 MCP behavior screenshots before `/gsd-verify-work`

### Wave 0 Gaps (files that must be created before implementation)

- [ ] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt` — IR structure validation
- [ ] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt` — 3 D-11 emission invariants (awk brace-walk)
- [ ] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt` — SimulationContextV2 logic tests
- [ ] `gbkt-examples/simple-physics/PLAYBOOK.md` — Plan 1 deliverable
- [ ] `.planning/phases/09-.../09-UAT.md` — Plan 1 deliverable (MCP input scripts + assertions + screenshot targets)
- [ ] `.planning/phases/09-.../evidence/reference/phys.c` — copy of reference source
- [ ] `.planning/phases/09-.../evidence/reference/BUILD.md` — reproducible build instructions

---

## Common Pitfalls

### Pitfall 1: Positive-literal clamp check silently fails

**What goes wrong:** `whenever(spdX isAbove 64) { spdX set 64 }` compiles without error but emits `_spdX > 64u`. SDCC warning 94 fires (comparison always false for signed negative half); speed clamps correctly for negative speed but not for positive speed. Game appears to work until sprite exits screen bounds at high velocity.

**Why it happens:** `ExprVisitor.visitLiteral` always emits `CLiteral(N)`. For `N >= 0`, CEmitter appends `u`. Phase 07.9 fixed hardcoded visitor `CIntLiteral` sites but the DSL-authored path was not in scope (bucket-a audit covered only visitor-internal constructions).

**How to avoid:** The named bug fix (D-07/D-04) addresses this. Until fixed, the symptom is that the upper-bound speed clamp does not fire — test by holding RIGHT for 60+ frames and checking `spdX` never exceeds 64 via `emulator_assert`.

**Warning signs:** SDCC warning 94 in lcc output: "comparison is always false due to limited range of data type". gbkt build pipeline does not currently surface this warning as an error.

### Pitfall 2: `moveTo(Expr, Expr)` does not compile

**What goes wrong:** `smiley.moveTo(posX shr 4, posY shr 4)` is a compile error — `moveTo` only accepts `Int`. The expression `posX shr 4` is `Expr`.

**Why it happens:** `ActorRef.moveTo(x: Int, y: Int)` was designed for static (non-expression) positions. The underlying `ScriptBuilder.setPosition(actorId, Expr, Expr)` was never promoted to an `ActorRef` convenience overload.

**How to avoid:** Work-around — update actor position globals directly via Assign ops, or add the `moveTo(Expr, Expr)` overload as the named fix. Direct assign: `smileyX set (posX shr 4); smileyY set (posY shr 4)` using actor property refs or additional `i16Var` helpers. The planner should evaluate whether this is the named fix or a work-around during Plan 3.

**Warning signs:** Kotlin compile error: "None of the following candidates is applicable: `moveTo(Int, Int)`".

### Pitfall 3: Sprite frame cycling is optional

**What goes wrong:** The reference cycles through 4 tile frames by calling `set_sprite_tile(0, frame_counter & 3)` inside the loop. gbkt's asset pipeline handles multi-frame sprites differently. Using all 4 frames requires animation support; a single-frame PNG still validates the port.

**Why it happens:** GBDK's `set_sprite_tile` sets the OAM tile directly; gbkt's actor system uses the asset pipeline + animation system. Adding animation cycling would require `animation { ... }` DSL (anti-overfitting concern).

**How to avoid:** Use a single-frame 8x8 PNG. The port goal is physics validation, not sprite animation fidelity (anti-overfitting rail 3). Document the gap in the oracle-comparison.md appendix.

### Pitfall 4: `SpdY < 0 then SpdY++` decel requires careful DSL ordering

**What goes wrong:** The reference's decel logic is `if (SpdY < 0) SpdY++; else if (SpdY) SpdY--;`. The `else if (SpdY)` branch means "if SpdY is nonzero" (i.e., positive). In gbkt DSL, two separate `whenever` blocks do not have else-if semantics — both conditions are evaluated per frame. For decel this is correct behavior (only one fires if signed, but both check independently).

**Why it happens:** gbkt has no `otherwise`/`else` construct. Multiple `whenever` blocks with non-overlapping conditions are the idiomatic pattern.

**How to avoid:** `whenever(spdY isBelow 0) { spdY++ }` and `whenever(spdY isAbove 0) { spdY-- }` — the conditions are mutually exclusive (spdY cannot be both negative and positive), so the lack of else-if semantics does not matter here.

### Pitfall 5: `then` prefix for `++spdY` vs `spdY++`

**What goes wrong:** `spdY++` emits `_spdY = _spdY + 1` in gbkt (Kotlin delegates use `SET` op with `var + 1`), not C's `_spdY++`. The generated C shape differs from reference. This is informational — no correctness gap — but appears in the C diff.

**Why it happens:** Kotlin operator overloading pattern for `inc()` — see `VariableBuilders.kt:194`.

**How to avoid:** Accept the shape difference; document in oracle-comparison.md as "gbkt uses assignment form; semantically equivalent".

### Pitfall 6: Visual-evidence rule — variable read is insufficient

**What goes wrong:** `emulator_assert(variable_equals("_smiley_x", 64))` proves the position variable has been set, but does NOT prove the sprite is visible on screen at that position. A downstream `clear()` or missing `showSprites()` could leave the variable correct but sprite invisible.

**Why it happens:** Phase 07.4 plan 18 root cause — variable assertions satisfied without screen evidence.

**How to avoid:** D-02 mandates `emulator_screenshot` at the climax frame of each behavior. The screenshot is the evidence artifact, not the variable read.

---

## Code Examples

### i16Var declaration and compound assign

```kotlin
// Source: gbkt-lang/VariableBuilders.kt:403-442 [VERIFIED]
var posX by i16Var(1024)   // initial value = 64 * 16 (sub-pixel units)
var posY by i16Var(1024)
var spdX by i16Var(0)
var spdY by i16Var(0)
```

### Actor declaration for simple-physics

```kotlin
// Source: pong PongV2.kt actor pattern [VERIFIED]
val smiley by actor {
    position(64, 64)          // initial pixel position (will be overridden by posX/posY in enter)
    sprite(asset("sprites/smiley.png")) {
        size(8, 8)
        hitbox(0, 0, 8, 8)
    }
}
```

### Single play scene structure

```kotlin
// Source: pong PongV2.kt scene pattern [VERIFIED]
val playScene = scene("play") {
    enter {
        showSprites()
        posX set 1024
        posY set 1024
        spdX set 0
        spdY set 0
    }
    frame {
        // input + physics + position sync here
    }
}
start = playScene.id
```

### shr expression for sub-pixel to pixel translation

```kotlin
// Source: gbkt-lang/ExprBuilder.kt:95, VariableBuilders.kt:321 [VERIFIED]
val pixX = posX shr 4   // Expr: BinaryExpr(VarRef("posX"), BinaryOp.SHR, Literal(4))
val pixY = posY shr 4   // Emits: _posX >> 4
```

### D-11 emission invariant skeleton (awk brace-walk pattern)

```kotlin
// Source: CLAUDE.md "Scope-level grep gates" + Plan 07.4-23 Task 1 step 3 [CITED]
@Test
fun `frame function contains signed comparison for spdX upper clamp`() {
    val generated = generateC(simplePhysics)
    // Extract play_frame function body via awk brace-walk
    val frameBody = extractFunctionBody(generated, "play_frame")
    // After named bug fix, expects CIntLiteral: _spdX > 64  (no u suffix)
    assertTrue(frameBody.contains("_spdX > 64"), "Expected signed upper clamp without u suffix")
    assertFalse(frameBody.contains("_spdX > 64u"), "Must not emit unsigned suffix on signed comparison")
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| All `CLiteral(N)` for every numeric constant | `CIntLiteral(N)` for signed-context comparison RHS (hardcoded visitor sites) | Phase 07.9 (2026-05-13) | Fixes camera-follow + platformer jump-cancel; DSL-authored signed comparisons still use old path |
| `sealed interface` IR (monolithic gbkt-core) | Non-sealed + visitor dispatch (20-module architecture) | V2 rebuild | Genre plugins can add IR nodes without touching core |
| Mutable `currentBank` codegen state | Typed `bank` field on C AST nodes | V2 rebuild | Bank-state-leak bugs eliminated |

**Deprecated/outdated:**
- `moveTo(x: Int, y: Int)` accepting only literals — should accept `Expr`; current design is a holdover from when actor positions were always static initializations.
- File-level grep counts for codegen invariants — CLAUDE.md scope-level grep corollary requires per-function awk brace-walk instead.

---

## Risks, Unknowns, Landmines

### Risk 1: Named bug selection ambiguity (Plan 3)

The port will hit at least two distinct codegen gaps (Bug A: positive-literal signed comparison; Bug B: moveTo Expr gap). D-04/D-07 says pick one as the named fix; surplus → seeds. There is a risk of scope creep if both feel small and "easy to fix together." Strict D-07 enforcement: pick the one that BLOCKS a UAT behavior. If Bug A prevents the clamp test from passing, Bug A is the named fix. Bug B is a seed unless it literally prevents the ROM from compiling (compile error → must be fixed to make progress).

**Anti-overfitting guard:** Do NOT fix both bugs just because both are small. The per-phase single-bug doctrine (STATE.md, ROADMAP, Plan 07.9-02) exists for this exact reason.

### Risk 2: `posX shr 4` type narrowing

`posX` is `INT16`; `shr 4` produces an `INT16` value. `move_sprite` in GBDK takes `uint8_t` arguments. The generated C may need an explicit cast: `(UINT8)(_posX >> 4)`. Without it, SDCC may warn or truncate unexpectedly. The reference uses `SUBPIXELS_TO_PIXELS(PosX)` which is a bare `>>` without cast — SDCC infers UINT8 from the argument position. gbkt's ExprVisitor emits `_posX >> 4` which may need a `(UINT8)` cast in the `move_sprite` call site. [ASSUMED: specific SDCC behavior; needs verification during Plan 2-3]

### Risk 3: Actor x/y globals are UINT8, not INT16

gbkt actor position globals (`_smiley_x`, `_smiley_y`) are declared `UINT8` by ActorVisitor (sprite position is always pixel-space 0-255). If the physics update writes `_posX >> 4` (INT16 → pixel) into `_smiley_x`, the type mismatch generates a SDCC warning. The reference doesn't have this issue because it uses `move_sprite(0, SUBPIXELS_TO_PIXELS(PosX), ...)` directly without a named UINT8 intermediary. The workaround `smileyX set (posX shr 4)` would coerce via the actor property accessor — which may or may not handle the INT16→UINT8 narrowing silently. [ASSUMED: exact type behavior needs verification during Plan 2-3]

### Risk 4: Single-scene game with `config { cartridge = ? }`

Pong uses `config { cartridge = "ROM_ONLY"; romBanks = 2 }`. Simple physics fits in HOME entirely (574 bytes code; no banking needed). The default config may emit a minimal ROM correctly. If the analysis pass rejects a missing `config { }` block or defaults to ROM_ONLY, no issue. If it defaults to a banked config and emits bank-switching infrastructure for a game that doesn't need it, the ROM size may blow past 2× reference. [ASSUMED: default config behavior; verify against pipeline defaults]

### Risk 5: Anti-overfitting trap — "make the port idiomatic" vs. "mirror the reference"

The reference's decel pattern `if (SpdY < 0) SpdY++; else if (SpdY) SpdY--;` is compact C but slightly awkward in DSL (two separate `whenever` blocks). The temptation to make the port "cleaner" with a new `decrementTowardZero(spdY)` DSL helper violates D-overfitting-1 (do not add DSL features just to make THIS port pretty). Enforce: use two separate `whenever` blocks; document the shape difference in oracle-comparison.md.

### Risk 6: No REQ-ID anchor in REQUIREMENTS.md

REQUIREMENTS.md does not have a requirement covering Phase 9 reference-port work (ROADMAP says "TBD — define during /gsd-discuss-phase" for Phase 9 requirements). The planner cannot map plan tasks to REQ-IDs. Acceptable resolution: use D-IDs from CONTEXT.md as requirement anchors (D-01 through D-11 are the phase's locked decisions, effectively functioning as requirements).

---

## Open Questions for Planner

1. **Named bug lock (Plan 3):** Bug A (signed comparison) or Bug B (moveTo Expr gap) — whichever blocks a UAT behavior first becomes the named fix. Research cannot pre-lock this; exploratory discovery per D-04.

2. **Actor position globals type:** Are `_smiley_x` / `_smiley_y` declared `UINT8` in generated C (ActorVisitor), and does writing `posX shr 4` (INT16 expression) into them via Assign op produce a SDCC warning? If so, does the port need an explicit `(UINT8)` cast, and does that belong in the DSL port or the codegen fix?

3. **Config block:** Should simple-physics use `config { cartridge = "ROM_ONLY" }` explicitly, or rely on defaults? Pong uses `ROM_ONLY`. Simple physics fits in HOME entirely — confirm the analysis pass accepts the default or requires explicit config.

4. **Sprite frame cycling:** Reference cycles 4 sprite frames. Port uses a single static 8x8 PNG (anti-overfitting). Confirm the asset pipeline processes a 1-frame 8x8 PNG without errors (no animation block required). If `size(8, 8)` with a 1-frame PNG causes a pipeline validation error, document as a seed.

5. **Build command for simple-physics in root Gradle:** `./gradlew :gbkt-examples:simple-physics:generateC` and `:buildRom` — confirm these work once the module is added to settings.gradle.kts.

6. **REQUIREMENTS.md anchor:** Phase 9 has no REQ-ID in REQUIREMENTS.md. The planner should decide: (a) add a REFERENCES.md entry for the reference-port track, or (b) use D-IDs as plan anchors. Either is acceptable; the gap should be noted in the plan.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| GBDK lcc | Reference ROM build (D-10 BUILD.md) | Yes | `/Users/michalsvacha/gbdk/bin/lcc` [VERIFIED] | None needed |
| GBDK_HOME env | lcc invocation | Yes | `/Users/michalsvacha/gbdk` [VERIFIED] | Set manually |
| Reference phys.c | Oracle comparison | Yes | Exists at canonical path [VERIFIED] | None |
| Reference physics.gb | ROM-size baseline | Yes (built during research) | 32768 bytes at `build/gb/physics.gb` [VERIFIED] | Rebuild with `make gb` |
| mGBA or emulator | UAT play-through | Not checked | — | MCP gbkt-emulator (headless) |
| MCP gbkt-emulator | D-02 screenshot capture | Configured in `.claude/mcp_servers.json` [VERIFIED: file exists] | — | Manual ROM testing |
| Gradle | Build + test | Yes (project standard) | — | — |

**Missing dependencies with no fallback:** None.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Explicit `(UINT8)` cast needed when writing INT16 expression to actor position global | Risks / Risk 3 | If implicit narrowing works silently, no action needed; if SDCC warns, cast must be added to C codegen path |
| A2 | `config { cartridge = "ROM_ONLY" }` should be explicit for single-bank game | Risks / Risk 4 | If default is already ROM_ONLY, no action needed; if default is banked, ROM overhead increases |
| A3 | Bug B workaround (direct variable assign into actor globals) is correct without a new DSL method | DSL Primitive Inventory | If ActorVisitor requires `SetPosition` IR op (not Assign) for the move_sprite sync to fire, direct Assign bypasses the sync and sprite doesn't move visually |
| A4 | Exact fix shape for Bug A involves type-inference in ExprVisitor.visitBinaryExpr | Named-Bug Candidate | If the fix requires type annotations on Expr nodes (which don't currently carry type info), the fix scope is larger than expected |
| A5 | SDCC 4.x emits `_spdX > 64u` with warning 94 (comparison always false) for signed < unsigned | Bug A analysis | If SDCC promotes both operands to int before comparison (C11 §6.3.1.8 behavior), the bug may not manifest at runtime — but the warning still fires and indicates fragility |

---

## Sources

### Primary (HIGH confidence)
- `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c` — reference source read directly
- `gbkt-backend-gbdk/src/main/kotlin/.../visitor/ActorBuilder.kt:331` — `moveTo(Int, Int)` only confirmed
- `gbkt-backend-gbdk/src/main/kotlin/.../visitor/ExprVisitor.kt:77` — `visitLiteral → CLiteral` confirmed
- `gbkt-backend-gbdk/src/main/kotlin/.../emit/CEmitter.kt:421` — `CLiteral(N>=0)` emits `Nu` confirmed
- `gbkt-lang/src/main/kotlin/.../dsl/VariableBuilders.kt:403-442` — `i16Var` delegate confirmed
- `gbkt-lang/src/main/kotlin/.../dsl/ExprBuilder.kt:93-95` — `shr` infix on Expr confirmed
- `gbkt-lang/src/main/kotlin/.../dsl/ActorBuilder.kt:331` — no `moveTo(Expr, Expr)` confirmed
- `.planning/phases/07.9-.../07.9-AUDIT.md` — bucket-a migration scope confirmed (hardcoded sites only)
- `gbkt-backend-gbdk/CLAUDE.md` §"Literal Emission Convention" — CLiteral/CIntLiteral convention
- `gbkt-examples/pong/build.gradle.kts` + `PongV2.kt` + `PongIRTest.kt` — example conventions

### Secondary (MEDIUM confidence)
- `gbkt-examples/pong/PLAYBOOK.md` — PLAYBOOK.md format template
- `context/TESTING.md:491` — `emulator_screenshot` tool confirmed
- `context/UAT_GUIDE.md` — MCP tool API confirmed

### Tertiary (LOW confidence)
- A1-A5 in Assumptions Log — behavior claims verified via code structure, not live SDCC compilation

---

## Metadata

**Confidence breakdown:**
- Reference code analysis: HIGH — phys.c read and every line mapped
- DSL primitive inventory: HIGH — source files verified directly
- Named-bug analysis: HIGH for Bug A (CEmitter emission verified); MEDIUM for Bug A fix shape (ASSUMED)
- moveTo gap: HIGH — confirmed by direct source read
- ROM-size baseline: HIGH — built from source during research session
- MCP harness format: HIGH — PLAYBOOK.md and TESTING.md read directly
- Pitfalls: HIGH for pitfalls 1/2/6; MEDIUM for pitfalls 3/4/5
- Examples conventions: HIGH — pong + breakout files read

**Research date:** 2026-05-13
**Valid until:** 2026-06-13 (stable domain — low churn risk for 30 days)
