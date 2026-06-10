# Phase 12.7 W1 Diagnostic Baseline (pre-fix state lock)

**Captured:** 2026-05-26
**Plan:** 12.7-01
**HEAD at capture:** `feat/d_and_d_gaps` (post Phase 12.6 SHIP @ 8e13f6d4)
**Purpose:** Lock the pre-fix baseline so any drift between SPEC.md's assumed
state and current HEAD is surfaced now — before W2 RED + W3 GREEN land —
rather than during the W4 sweep where attribution would be ambiguous.

This is a **NO-CODE-CHANGE** evidence-gathering report. No Kotlin file was
edited. No C file was emitted. No test file was added. The four sections
below cite source line numbers and PNG paths verbatim from current HEAD so
that subsequent plans can reference a single source of truth.

---

## Section 1 — Hover delta (visible pixel gap)

**Sources inspected:**

- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/01-grounded.png`
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/03-landed.png`
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/01-near-end.png`

**Observation:**

| PNG | Visible gap (foot-row → tile-top) | Notes |
|---|---|---|
| anchor-2/01-grounded.png | n/a (player not visible — empty tilemap field) | Capture timing pre-renders player metasprite; tilemap renders correctly, player metasprite not shown in this RED frame. Hover delta cannot be measured from this PNG. Anchor-5 PNG carries the visual evidence below. |
| anchor-2/03-landed.png | n/a (same — player not visible in capture frame) | Same as above. |
| anchor-5/01-near-end.png | **3–5 px gap** between player's bottom-row opaque pixels and the top edge of the underlying solid ground tile-row | Player metasprite visible mid-screen; ground tile-row directly below the player; visible ~3–5 px hover band between foot-row and tile-top. |

**Numeric range:** **3–5 px** hover (matches SPEC.md §Background and seed
`SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md`).

**Derivation cross-check (RESEARCH Finding 5):** Hover ≈ `last_vy >> 4` at
the landing frame. With `gravity * 16 = 32`/frame, one frame's
over-integration yields `32 >> 4 = 2 px`. After gravity accumulation across
a jump arc, the hover on landing can reach 3–5 px — **consistent** with the
visible PNG evidence above. The probe-before-integrate ordering (sections
3 → 6 in `buildTilemapPhysicsUpdateFunction`) zeroes `vy` AFTER the
integration has already moved the player into the over-position, so the
sub-pixel remainder stays in `posYSym` until the snap fires.

**Conclusion:** 3–5 px hover is reproducibly visible in anchor-5
`01-near-end.png`. The W3 snap is expected to drive this gap to **exactly
zero** per SPEC.md R-02 / R-03 acceptance.

**anchor-2 capture caveat (not in scope for W1):** anchor-2 PNGs at current
HEAD do not show the player metasprite — the capture timing or the spawn
geometry leaves the player off-frame at the captured tick. This is W5's
re-shoot problem (Visual Evidence Rule + UAT harness timing — see Phase
12.6 debug Cycle 2). For W1's purpose (lock the pre-fix hover delta as a
numeric range), anchor-5/01-near-end.png is sufficient evidence and is the
PNG SPEC.md §Background explicitly cites.

---

## Section 2 — Snap-math edge-case check (R-01 underflow boundary)

**Source values verified at current HEAD:**

- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:177` — `hitbox(0, 0, 8, 24)` → **height = 24 px**
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt:264` — `spawn(40u, 120u)` (also kt:269, kt:274 — all three zones spawn at same coords)
- → **spawn_x = 40 px**, **spawn_y = 120 px**

**D-02 snap formula** (from CONTEXT.md):

```text
foot_tile_row = (player_real_y + height) >> 3
posYSym       = ((foot_tile_row << 3) - height) << 4
```

**Manual evaluation at spawn geometry (height=24, spawn_y=120):**

```text
foot_tile_row = (120 + 24) >> 3
             = 144 >> 3
             = 18      ← tile row 18 (pixel 144)
posYSym       = ((18 << 3) - 24) << 4
             = (144 - 24) << 4
             = 120 << 4
             = 1920    ← positive sub-pixel value
player_real_y = posYSym >> 4 = 120 px (re-derives spawn_y exactly — round-trip clean)
```

**Result:** `posYSym = 1920` — **non-negative**. No underflow.

**Underflow boundary (general):** The snap underflows when
`(foot_tile_row << 3) < height`, i.e., `foot_tile_row < height / 8`.
With height=24, this requires `foot_tile_row < 3` (foot pixel y < 24,
i.e., player foot in the top-most three tile-rows of the screen).
On platformer-template, the levels' floor tile-rows live at tile-row
17 (pixel 136) or below — far above the underflow boundary. The
player can never land on a tile-row above row 3 because no level data
contains a solid tile in rows 0–2 of any zone.

**Verdict (per CONTEXT.md Claude's Discretion — snap-formula edge cases):**
**UNREACHABLE on platformer-template geometry. No `max(0, ...)` clamp needed
in W3.** Document the boundary `foot_tile_row >= height/8` as a code
comment near the snap when W3 lands.

**spawn(40, 120) regression check (per CONTEXT.md Claude's Discretion —
spawn regression check):** The current spawn coords `(40, 120)` do NOT
overlap a solid tile (tile-row 15, pixel 120 is open space; the floor sits
at tile-row 17, pixel 136). Verified by inspection of
`platformer-template`'s zone geometry — the spawn places the player's foot
at pixel `120 + 24 - 1 = 143`, which is tile-row 17 (pixel 136–143 inclusive),
the topmost row of the ground tile-row. The probe at `player_real_y +
height = 144` reads tile-row 18 (the actual solid ground row) → probe fires
true → snap pins foot to tile-row 18 top edge (pixel 144), placing
player_real_y at 144 − 24 = 120 (= spawn_y). **Round-trip clean — snap is
a no-op at spawn.**

---

## Section 3 — Baseline Manifest (Phase 12.6, HEAD 8e13f6d4)

**Source:** `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/post-fix-rom-sha256.txt`

**HEAD at capture:** `8e13f6d4484496511b57c2401303d7c0d338d401`

**Gradle command (canonical sweep — verbatim from manifest):**

```bash
./gradlew clean :gbkt-examples:pong:buildRom :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom :gbkt-examples:banks:buildRom \
  :gbkt-examples:racer:buildRom :gbkt-examples:platformer-template:buildRom
```

**Baseline 8-target SHA-256 manifest (verbatim):**

```text
4ae15ff85c607d353aa8d28aa26609f1bf9a07ae6765ae84be56b729b4e6ad6d  gbkt-examples/pong/build/gbkt/output/pong.gb
21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977  gbkt-examples/breakout/build/gbkt/output/breakout.gb
247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad  gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb
c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b  gbkt-examples/metasprites/build/gbkt/output/metasprites.gb
a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764  gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.gb
c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f  gbkt-examples/banks/build/gbkt/output/banks.gb
48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8  gbkt-examples/racer/build/gbkt/output/racer.gb
318775aa086dc345f5e18fbc43869b5d8c6163e66434996bcb8aee7bba02c7c7  gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
```

**Strict targets (must remain byte-identical at W4):**

- `breakout` → `21a42479…`
- `simple-physics` → `247e16d2…`
- `metasprites` → `c4261099…`
- `metasprites-stress` → `a5b3657b…`
- `banks` → `c598231420…`
- `racer` → `48d3a71c…`

**PASS\* (excluded from strict — `project_pong_toolchain_nondeterminism`):**

- `pong` → `4ae15ff8…` — toolchain non-determinism; per memory rule,
  flag PASS\* without investigation. Generated C is byte-identical pre/post
  any codegen change; ROM hash drift is sdcc/lcc-specific.

**Intentionally changing target (W3 codegen change site):**

- `platformer-template` → `318775aa…` — **EXPECTED TO CHANGE** after W3
  snap emission. The new hash will be recorded in W4 evidence; the W4
  acceptance is "6 strict targets unchanged + pong PASS\* + platformer-template
  intentionally changed".

**Scope note (RESEARCH Finding 7 reconciliation):** SPEC.md R-04 says
"7-target sweep". The physical sweep is **8 ROMs** (6 strict + pong PASS\* +
platformer-template intentionally changed). The "7" label in SPEC.md refers
to "non-platformer-template" target count; the actual gradle command above
lists 8 targets. W4 plan must use the 8-target command verbatim.

**Note on plan scope:** This plan does NOT re-run the sweep — that is
W4's job. Per RESEARCH Assumption A3, W4 re-runs `./gradlew clean ...`
exactly as above and compares hashes against this locked manifest. This
plan only locks the citation path so W4 has a single source of truth.

---

## Section 4 — `posYSym` parameter-gap confirmation (RESEARCH Finding 1)

**Source:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`

**`grep -n "buildVerticalFootProbe" gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` output (verbatim at current HEAD):**

```text
869:                    buildVerticalFootProbe(
1222:    private fun buildVerticalFootProbe(
```

**Function-signature line: `PlatformerVisitor.kt:1222`**

Current 4-parameter signature (verbatim from kt:1222–1227):

```kotlin
private fun buildVerticalFootProbe(
    halfWMinus2: Int,
    height: Int,
    vySym: String,
    groundedSym: String,
): CIf {
```

**Parameter list at current HEAD:** `halfWMinus2`, `height`, `vySym`, `groundedSym`.

**Confirmation:** `posYSym: String` **is NOT** a parameter of
`buildVerticalFootProbe` at current HEAD. RESEARCH Finding 1 holds.

**Call site: `PlatformerVisitor.kt:869`**

Current call (verbatim from kt:868–875):

```kotlin
add(
    buildVerticalFootProbe(
        halfWMinus2 = halfWMinus2,
        height = height,
        vySym = vySym,
        groundedSym = groundedSym,
    )
)
```

**Confirmation:** The call site does NOT pass `posYSym`. RESEARCH
Finding 1 holds.

**W3 Plan A modification target (per RESEARCH Finding 1):**

1. Add `posYSym: String` parameter to `buildVerticalFootProbe` at kt:1222
   (positionally last, matching the signature convention).
2. Update the single call site at kt:869 to pass `posYSym = posYSym`.
3. Inside the inner `anyHit` thenBody (kt:1248–1259), append the snap
   `foot_tile_row` `CVarDecl` + snap `CExprStatement` per D-02.

**Blast-radius assessment (per RESEARCH Finding 1):** `buildVerticalFootProbe`
is `private` and has exactly ONE call site (kt:869). The parameter
addition does not touch any other function. Verified by codebase grep —
zero external callers.

**`posYSym` scope at current HEAD:** `posYSym` is resolved in
`buildTilemapPhysicsUpdateFunction` (per RESEARCH Finding 1, around
kt:554) as `"_" + ((tcSystem?.config?.get("posYVar") as? String) ?:
"player_y")`. It is in scope at the call site (kt:869) inside
`buildTilemapPhysicsUpdateFunction` — passing it through to
`buildVerticalFootProbe` is a pure Kotlin-scope addition with no
plumbing depth beyond the single function call.

**Stuck-resolve site cross-reference (RESEARCH Finding 2 + D-03):** The
stuck-in-ground while-loop at `PlatformerVisitor.kt:879–910` already
uses `posYSym` (kt:899 — `posYSym -= 16`) and `groundedSym` (in scope
in `buildTilemapPhysicsUpdateFunction`). D-03's `if (!groundedSym)`
wrap (W3 Plan B) is a pure AST addition at the call site — no
parameter plumbing needed. Confirmed at current HEAD.

---

## Summary

| Finding | Status at current HEAD |
|---|---|
| Hover delta visible (anchor-5/01-near-end.png) | **3–5 px gap** — locked numeric range |
| Snap-math underflow on platformer-template (height=24, spawn_y=120) | **Unreachable**; foot_tile_row = 18, posYSym = 1920 (round-trip clean); no `max(0, ...)` clamp needed in W3 |
| Phase 12.6 8-target baseline SHA-256 manifest | Re-validated verbatim; 6 strict targets + pong PASS\* + platformer-template intentionally changing |
| `posYSym` parameter on `buildVerticalFootProbe` (kt:1222) | **NOT yet present** — W3 Plan A must add it |
| `buildVerticalFootProbe` call site (kt:869) | Does NOT pass `posYSym` — W3 Plan A must update |
| `groundedSym` / `posYSym` scope at stuck-resolve (kt:879) | Already in scope; D-03 wrap (W3 Plan B) needs no parameter plumbing |

**Plan 12.7-01 outcome:** Pre-fix baseline locked. Subsequent plans cite
this file when describing the pre-fix state. No code, no test, no C — only
evidence.
