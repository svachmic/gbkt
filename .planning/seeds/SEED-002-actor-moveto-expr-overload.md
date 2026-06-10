---
id: SEED-002
status: dormant
planted: 2026-05-13
planted_during: v1.0 / Phase 09 Plan 04 closeout
trigger_when: when a future port or feature plan re-encounters the missing overload OR a DSL ergonomics milestone is opened
scope: small
---

# SEED-002: `ActorRef.moveTo(Expr, Expr)` overload

## Why This Matters

`ActorRef.moveTo(x: Int, y: Int)` exists (compile-time literal teleport). The
matching `moveTo(x: Expr, y: Expr)` overload does NOT exist. DSL fragments that
need to teleport an actor to a computed (expression) position must work around
the gap by writing into the underlying `ActorPropertyRef.x`/`.y` slots
individually:

```kotlin
// Workaround used by Phase 9 simple_physics port (Plan 03):
smiley.x set (posX shr 4)
smiley.y set (posY shr 4)
```

The workaround compiles and runs correctly, but it is two statements where the
reference style would be one, and it loses the "this is a teleport, emit
SetPosition op" semantic affordance (the workaround lowers to two separate
SetActorProperty ops). The reference C source for `simple_physics` reads
`SPRITE_X = posX >> 4; SPRITE_Y = posY >> 4` — two statements there too, but a
single `actor.moveTo(posX >> 4, posY >> 4)` would be a cleaner Kotlin-DSL
expression.

## When to Surface

**Trigger conditions (any one):**
- A future port plan (e.g., porting another GBDK reference example that has a
  computed-position teleport in its hot path) re-encounters the gap and the
  workaround becomes load-bearing rather than incidental.
- A DSL ergonomics milestone is opened and `moveTo` is in scope.
- Telemetry from real games shows enough `actor.x set expr; actor.y set expr`
  pairs that adding the overload would meaningfully shorten authored code.

**Do NOT surface this seed in v1.0.** Phase 9 deliberately did not fix it (the
named bug for Plan 04 is Bug A, the signed-comparison literal-emission gap,
because that one blocked a D-01 UAT behavior). Bug B is an ergonomic gap, not
a correctness gap.

## Scope Estimate

**Small** — a single overload in
`gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt`:

```kotlin
fun ActorRef.moveTo(x: Expr, y: Expr) {
    ScriptBuilderContext.current?.setPosition(id, x, y)
}
```

The underlying `ScriptBuilder.setPosition(id, x: Expr, y: Expr)` is already
present (verified in 09-RESEARCH.md). The only work is the DSL overload and a
small test pinning the lowering to `SetPosition(id, x, y)` instead of two
`SetActorProperty` ops.

## Repro Recipe

```kotlin
// Want this to compile:
smiley.moveTo(posX shr 4, posY shr 4)

// Currently does NOT compile — no overload accepting Expr.
// Workaround:
smiley.x set (posX shr 4)
smiley.y set (posY shr 4)
```

## Blast-Radius Hint

**Visitor sites affected:** zero (the underlying SetPosition op is already
emitted correctly by `ScriptOpVisitor.visitSetPosition`).

**DSL paths affected:** one — `ActorBuilder.kt`. Adding the overload is purely
additive.

**Tests affected:** zero existing tests would change. A new test pinning the
lowering to a single SetPosition op is the only test work.

## Why Not Fixed in Phase 9

Phase 9's contract is **one named codegen bug-fix per port** (D-04 exploratory
mode + D-05 surplus-to-seeds rails). The Plan 04 named bug is Bug A (positive
literal in signed-comparison RHS emits `Nu`) — that was the bug that blocked
SDCC from compiling cleanly without warning 94 in the runtime path. Bug B is
an ergonomic gap and the workaround compiles and runs cleanly (per
09-03-SUMMARY.md decision log). Fixing two bugs in one plan would violate the
single-named-bug doctrine (cf. Plan 07.9-02 PlatformerVisitor rejection).

## Possible Follow-Up Phase

A small DSL ergonomics phase that batches Bug B with similar additive overloads
across the actor API (e.g., `actor.position(x: Expr, y: Expr)` accepting Expr,
`actor.moveBy(dx: Expr, dy: Expr)`, etc.). One phase, 1–3 plans. Driven by
real-port telemetry, not speculation — wait for a port plan to re-encounter
the gap before authoring this phase, to avoid speculative overloads landing.

## Related

- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-03-SUMMARY.md`
  §"Bug B disposition" — Plan 03 closeout that explicitly disposed of Bug B as
  a seed candidate.
- `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-RESEARCH.md`
  §"Bug B" — original discovery and proposed fix shape.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:331` — fix
  site if/when this seed sprouts.
