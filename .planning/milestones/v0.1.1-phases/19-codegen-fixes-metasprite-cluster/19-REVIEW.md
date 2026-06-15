---
phase: 19-codegen-fixes-metasprite-cluster
reviewed: 2026-06-13T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: clean
---

# Phase 19: Code Review Report

**Reviewed:** 2026-06-13
**Depth:** standard
**Files Reviewed:** 1
**Status:** clean

## Summary

One test class reviewed: `Phase19VisualEvidenceTest.kt`, a GBC-mode UAT evidence-capture harness
for SEED-004/005/006/013 and the Req-3 ROM-smoke shot. The class is structurally sound — `use {}`
wraps every agent, the `Assumptions.assumeTrue` skip guard is correct, `captureAndRename` properly
creates per-seed subdirectories, and the sidecar-JSON path uses `target.parentFile` (correct for
the nested `SEED-00x/screenshot.png` layout).

Two warnings surface around silent failure modes that would allow a misfire (wrong game state at
capture time) to produce a passing test with misleading evidence PNGs. Two info items cover
weak assertion strength and a minor resource-safety pattern.

## Warnings

### WR-01: `waitForScene` return value silently discarded — timeout is undetected in both test methods

**File:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt:117` (also line 165)

**Issue:** `StepAgent.waitForScene(name, maxFrames)` is documented to return the *last* `Observation`
on timeout without throwing (see `StepAgent.kt:326-327`). Both test methods call it and discard
the return value entirely:

```kotlin
agent.waitForScene("play", 120)   // return value dropped — timeout is silent
```

In `bootFrame`, a timeout means screenshots are captured at whatever boot frame the emulator
is on — SEED-004/005 and ROM-smoke evidence would be taken at a blank or partially-initialized
display frame. In `subPaletteClimax`, the subsequent 8 A-press/release sequence and final
capture occur in the wrong game state; the screenshots would not prove sub-palette at rot=8.
Both tests would pass the `file.length() > 0` assertion with meaningless evidence.

The analog (`MetaspriteUatTest`) sidesteps this by immediately asserting a variable value after
`waitForScene` (e.g. `assertEquals(0, agent.readVariable("idx"), ...)`), which implicitly
confirms the correct scene was reached. Phase19 has no such implicit guard.

**Fix:**
```kotlin
val obs = agent.waitForScene("play", 120)
assertTrue(
    obs.scene == "play",
    "Timed out waiting for play scene after stepN(30) — got: ${obs.scene}",
)
```
Apply at both call sites (line 117 and line 165).

---

### WR-02: No game-state assertion in `subPaletteClimax` — wrong-frame capture undetected

**File:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt:204`

**Issue:** After 8 A-press/release cycles intended to advance `rot` from 0 to 8, the test
immediately captures the SEED-006 and SEED-013 screenshots. There is no `readVariable("rot")`
call to confirm `rot == 8` was actually reached before capture. If any single A-press is
not registered as an edge event (e.g. a button-state flush gap, or if the scene transition
did not complete fully before inputs began), the captured frame would show sub-palette 0 or 1
rather than the cyan (sub-palette 2) that the evidence is supposed to prove.

The analog (`MetaspriteUatTest.behavior3`, line 325-338) explicitly reads `rot8 = agent.readVariable("rot")` and asserts `assertEquals(8, rot8, ...)` before the screenshot is captured.
Phase19 omits this gate entirely.

**Fix:**
```kotlin
// After the last agent.step(emptySet()) flush frame, before captureAndRename:
val rot = agent.readVariable("rot")
assertEquals(
    8,
    rot,
    "rot must be 8 at SEED-006/013 capture (subpal=cyan); got: $rot — " +
        "check edge-detection release frames",
)
```

## Info

### IN-01: Three byte-identical screenshots captured in `bootFrame` with no stepping between them

**File:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt:120-137`

**Issue:** `captureAndRename` is called three times consecutively — for SEED-004, SEED-005, and
ROM-smoke — with no `agent.step()` call between them. Each `captureScreenshot(label)` call
writes a new PNG file from the same emulator frame buffer, so all three evidence PNGs are
pixel-identical. This is explicitly noted in comments ("same boot frame") and is intentional
per plan 19-03 Task 1. However, three independent evidence artifacts that are byte-identical
may invite questions during manual review about whether SEED-004 and SEED-005 are truly
independent captures.

**Fix (optional):** No code change required if the identical-frame intent is accepted. To
document it more explicitly, a comment on each subsequent `captureAndRename` call could note
`// Same frame as SEED-004 (boot frame; no step between captures — intentional)`.

---

### IN-02: `newGbcAgent()` can leak the agent if `agent.start()` throws after construction

**File:** `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt:57-71`

**Issue:** The helper constructs and starts the agent before returning it to the `use {}` caller:

```kotlin
val agent = StepAgent(baseConfig, metadata)
agent.start()   // <-- if this throws, close() is never called
return agent
```

If `start()` throws after `StepAgent` has internally allocated resources (emulator thread,
frame buffer, etc.), those resources are not cleaned up because the `use {}` block in the
test never received the agent. This matches the analog `MetaspriteUatTest.newGbcAgent()`
pattern exactly, so it is a consistent (if fragile) convention in this codebase.

**Fix (low priority — matches analog):** Wrap the start call:
```kotlin
val agent = StepAgent(baseConfig, metadata)
try {
    agent.start()
} catch (e: Exception) {
    agent.close()
    throw e
}
return agent
```

---

## Resolution

**WR-01** — Fixed in commit `1a9a31f1`. Both `waitForScene` call sites now capture the returned
`Observation` and assert `obs.scene == "play"` before any screenshot capture. A timed-out wait
now fails immediately with a clear message instead of silently producing misleading evidence.

**WR-02** — Fixed in commit `1a9a31f1`. Added `agent.readVariable("rot")` + `assertEquals(8, rot, ...)`
after the GBC PPU flush frames and before the SEED-006/013 `captureAndRename` calls, mirroring
`MetaspriteUatTest.behavior3` exactly. Evidence re-captured against a clean `buildRom`; SEED-006
and SEED-013 (1325 bytes) differ from the boot-frame shots (1423 bytes), confirming the rot=8
assertion drove the emulator to the correct game state before capture.

**IN-01 / IN-02** — Intentionally not addressed per task scope (IN-01 is by-design per plan
19-03 Task 1; IN-02 matches the codebase-wide convention in `MetaspriteUatTest`).

---

_Reviewed: 2026-06-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Resolved: 2026-06-13 (WR-01/WR-02 fixed; warnings cleared)_
