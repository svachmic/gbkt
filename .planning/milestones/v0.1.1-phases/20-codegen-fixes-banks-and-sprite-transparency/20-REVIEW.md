---
phase: 20-codegen-fixes-banks-and-sprite-transparency
reviewed: 2026-06-14T08:22:12Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspritePhase20OracleTest.kt
  - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplatePhase20OracleTest.kt
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-06-14T08:22:12Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Two new Phase-20 FIX-04 visual-oracle test classes were reviewed at standard depth. Both are
clone-and-retarget descendants of the established `PlatformerTemplate128UatTest` precedent (Phase
12.8). API claims in the source were cross-verified against the actual `StepAgent`,
`AgentSessionConfig`, and game DSL sources:

- `StepAgent` is `AutoCloseable` (`.use { }` valid), `waitForScene(name, maxFrames)` exists with the
  exact signatures used, `captureScreenshot(label)` returns a `File`.
- `AgentSessionConfig.discoverFiles()` does prefer the `.noi` symFile over `.sym` (doc-comment
  claim verified), and `gbcMode = true` is correctly set on both agents — the GBC-mode requirement
  (D-05 / `learning_platformer_mcp_needs_gbc_mode`) is honoured.
- Scene names are real: metasprites declares `scene("play")` (its sole scene); platformer declares
  `scene("title")` whose START handler does `navigate(SceneRef("gameplay"))`, so the platformer
  test's title→gameplay START flow and `waitForScene("gameplay", ...)` are correct.

The dominant defect is a **soundness regression versus the precedent**: both new tests discard the
`waitForScene` return value and never assert that the target scene was actually reached. Because
`waitForScene` returns the *final observation on timeout* (it does not throw), a boot/transition
regression would silently fall through to capturing the wrong scene, and the only gate
(`assertScreenshotIsNonUniform`) passes on any non-blank frame — including a title card. The
precedent it was cloned from explicitly pairs the screenshot with `assertEquals("gameplay",
gameplayObs.scene)` (PlatformerTemplate128UatTest.kt:218-224); that pairing was dropped.

## Warnings

### WR-01: `waitForScene` result discarded — oracle never confirms the target scene was reached

**File:** `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt:183`
**File:** `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt:204`
**Issue:** `waitForScene` does **not** throw on timeout — `StepAgent.waitForScene` (StepAgent.kt:325-326)
delegates to `waitUntil` and is documented to return "the final observation on timeout." Both new
tests call it as a statement and throw the return value away:

```kotlin
// metasprites
agent.waitForScene("play", 120)
// platformer
agent.waitForScene("gameplay", maxFrames = 60)
```

If the scene transition never happens (boot regression, renamed scene, START-mapping change, banked
tilemap load failure), execution falls through and captures a screenshot of whatever scene is
on-screen (e.g. the platformer title card). The sole gate, `assertScreenshotIsNonUniform`, passes on
*any* non-blank frame, so the wrong-scene capture is accepted as the binding D-08 oracle. This
defeats the test's stated purpose ("binding visual oracle for FIX-04 Success Criterion 3/4"). The
precedent this was cloned from does it correctly (PlatformerTemplate128UatTest.kt:204, 218-224):

```kotlin
val gameplayObs = agent.waitForScene("gameplay", maxFrames = 60)
...
assertEquals("gameplay", gameplayObs.scene, "...title → gameplay scene transition...")
```

**Fix:** Capture the observation and assert on it before capturing the screenshot:

```kotlin
// metasprites
val playObs = agent.waitForScene("play", 120)
assertEquals("play", playObs.scene, "metasprites must reach the 'play' scene before capture")

// platformer
val gameplayObs = agent.waitForScene("gameplay", maxFrames = 60)
assertEquals("gameplay", gameplayObs.scene, "platformer must reach 'gameplay' before capture")
```

(Add `import kotlin.test.assertEquals`.)

### WR-02: Non-uniform gate cannot detect the FIX-04 regression class it guards

**File:** `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt:113-157, 197`
**File:** `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt:130-174, 232`
**Issue:** FIX-04 is about sprite **transparency** — transparent pixels must route to GB OBJ index 0
so the elephant/player has no spurious black outline. `assertScreenshotIsNonUniform` only checks
">= 2 distinct colours" and "dominant colour < 95% of pixels." A sprite rendered WITH the wrong
black outline (exactly the bug FIX-04 fixes) is *more* colourful and still non-uniform, so it passes
the gate. The mechanical gate therefore provides zero protection against the regression it is
nominally the oracle for; it only proves "something rendered." The docstrings disclose this ("this
test provides the mechanical non-blank gate", "Human visual sign-off happens at phase verification"),
so the Visual Evidence Rule is still satisfied via the PNG artifact + human sign-off — but a reader
could mistake a green test run for a passing FIX-04 regression check.
**Fix:** No code change strictly required (design is disclosed and human sign-off is the real gate),
but tighten one of:
1. Make the test name / docstring explicitly state "non-blank gate only — does NOT verify
   transparency; transparency is confirmed by human sign-off on the PNG", or
2. Add a targeted assertion on the sprite-bounding-box region (e.g. that the configured
   transparent/background colour, not an outline colour, borders the metasprite) so the test
   actually exercises FIX-04. Option 2 is the stronger guard if the agent can expose the sprite
   region.

### WR-03: `captureAndRename` aborts the whole test on a benign rename race, and leaks the source file on sidecar-rename failure

**File:** `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt:87-102`
**File:** `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt:100-119`
**Issue:** `check(captured.renameTo(target)) { ... }` throws `IllegalStateException` if `File.renameTo`
returns `false`. `renameTo` is platform-dependent and can fail for non-error reasons (cross-volume
moves, transient locks on the just-written PNG, antivirus handles on Windows/CI) — turning an
artifact-relocation hiccup into a hard test failure even though the screenshot was captured
successfully. The sidecar branch ignores its `renameTo` result (documented "best-effort"), which is
inconsistent with the hard `check` on the primary file. The net effect: a flaky environment fails
the oracle for the wrong reason. (This pattern is inherited from the precedent, but it is still a
robustness defect worth fixing in the new copies.)
**Fix:** Prefer `Files.move(..., StandardCopyOption.REPLACE_EXISTING)` (atomic where supported, falls
back to copy+delete across volumes) and surface the underlying `IOException`:

```kotlin
import java.nio.file.Files
import java.nio.file.StandardCopyOption
...
Files.move(captured.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
```

This removes the brittle boolean-return failure mode and the manual `if (target.exists()) delete()`
pre-step.

## Info

### IN-01: `assertScreenshotIsNonUniform` duplicated verbatim across (at least) three test files

**File:** `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt:113-157`
**File:** `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt:130-174`
**Issue:** The ~45-line perceptual-check method is copied verbatim into both new files and, per the
docstrings, also lives in `PlatformerTemplate128UatTest`. Three identical copies of a non-trivial
image-analysis routine drift independently. `captureAndRename` is similarly duplicated.
**Fix:** Extract `assertScreenshotIsNonUniform` (and ideally `captureAndRename`) into a shared test
helper in `gbkt-test` (or an internal `OracleScreenshotSupport` object reachable from
`gbkt-examples`), then call it from all three tests. Not blocking — these are example/test files —
but the duplication is exactly the maintenance hazard CLAUDE.md's no-duplication guidance targets.

### IN-02: Redundant manual metadata load — `discoverFiles` + `StepAgent` already resolve it

**File:** `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt:53, 75-77`
**File:** `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt:50, 87-89`
**Issue:** Both tests declare a `METADATA_FILE` and manually do
`if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null` to pass into
`StepAgent`. But `AgentSessionConfig.discoverFiles()` already populates `metadataFile` from the
`generated/` dir (AgentSessionConfig.kt:72-73), and `StepAgent` already resolves metadata from
`config.metadataFile` when the constructor `metadata` arg is null (StepAgent.kt:87-98). The manual
load is dead weight that re-derives what the config already knows, and hard-codes a relative path
(`build/gbkt/generated/...`) that duplicates `discoverFiles` logic. This matches the precedent, so
it is a consistency carry-over, not a new mistake — flagged for awareness.
**Fix:** Drop `METADATA_FILE` and the manual `GameMetadata.fromJsonFile` block; pass `StepAgent(baseConfig)`
and let config-driven resolution supply metadata. Verify behaviour is unchanged (it should be, since
`discoverFiles` points at the same `game_metadata.json`).

### IN-03: Frame-budget heuristics are magic numbers documented only in comments

**File:** `gbkt-examples/platformer-template/.../PlatformerTemplatePhase20OracleTest.kt:198, 204, 205, 211-215, 219`
**File:** `gbkt-examples/metasprites/.../MetaspritePhase20OracleTest.kt:179, 184, 187`
**Issue:** Boot/settle/navigation frame counts (`stepN(120)`, `maxFrames = 60`, `stepN(30)`,
`repeat(120)`, the `(frame / 8) % 3` jump cadence, `stepN(5)`) are bare literals justified only by
prose comments tied to one-time spatial assumptions ("spawn ~80 px", "trigger at player_real_x >
448 px"). If the level geometry or spawn point changes, these silently drift out of range and the
capture could land past the level-end trigger or before the player is visible — and (because of
WR-01) nothing would assert the failure. Low severity for a UAT test, but the coupling between the
hardcoded 120-frame walk and the level layout is fragile.
**Fix:** Promote the load-bearing values to named `private const val` (e.g. `BOOT_FRAMES`,
`SCENE_WAIT_FRAMES`, `NAV_FRAMES`, `JUMP_PERIOD`) with the spatial rationale in KDoc, and — better —
gate the capture on an observed position/scene predicate (`waitForVariable` / scene assert) rather
than a fixed frame count, so the test self-corrects when geometry changes.

---

_Reviewed: 2026-06-14T08:22:12Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
