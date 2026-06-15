---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
reviewed: 2026-06-15T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GoldenAssertions.kt
  - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt
  - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt
  - gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt
  - gbkt-examples/metasprites/build.gradle.kts
  - gbkt-examples/platformer-template/build.gradle.kts
  - .gitignore
findings:
  critical: 0
  warning: 5
  info: 4
  total: 9
status: issues_found
---

# Phase 22: Code Review Report

**Reviewed:** 2026-06-15
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Reviewed the golden-screenshot evidence-storage overhaul: `GoldenAssertions` (assert/bless),
GBC auto-detect from the ROM 0x143 header byte in `AgentSessionConfig.discoverFiles`, the
`capturedAt` removal in `ScreenshotCapture`, and the `CleanTreeEvidenceAcceptanceTest` source-tree
+ git-index guard. The core logic is sound and well-tested for the happy paths, but the headline
re-bless workflow is effectively broken for its real callers, and the acceptance test's git
shell-out and source-tree scanning heuristics have several robustness gaps that weaken the
regression guard they are meant to enforce.

No security vulnerabilities or crash-on-correct-input defects were found. No Critical findings. The
strongest finding (WR-01) is that the documented `-Pgbkt.updateGoldens` bless path silently writes
to a gitignored build directory instead of the committed source goldens — the feature appears to
work but does not persist.

## Warnings

### WR-01: `-Pgbkt.updateGoldens` blesses the gitignored build copy, not the committed golden

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GoldenAssertions.kt:83-87`
**Issue:** In update mode, `compareOrBless` raw-copies the capture over `goldenFile`. But the real
callers resolve `goldenFile` from the classpath:
`File(javaClass.getResource("/goldens/metasprites/...png")!!.toURI())`
(see `Phase19VisualEvidenceTest.kt:103-108`). That URI resolves to the **build** resource copy
(`gbkt-examples/metasprites/build/resources/test/goldens/...`), confirmed on disk — NOT
`src/test/resources/goldens/...`. The build copy is under `build/` which is gitignored and is
regenerated from `src/` on the next `processTestResources`. Consequently the documented re-bless
command (`./gradlew test -Pgbkt.updateGoldens`, GoldenAssertions.kt:20-23) updates a throwaway file:
the next `clean` or `build` wipes/overwrites it and the committed golden is never changed. The
headline workflow silently no-ops from the user's perspective. Additionally, blessing a *new*
(missing) golden through a resource-based caller is impossible: `getResource(...)` returns `null`
and `!!` throws an NPE before `assertGoldenMatch` runs, so the "golden missing + update mode → write"
path (unit-tested in `GoldenAssertionsTest` via a plain `File`) is unreachable from production tests.
**Fix:** Resolve goldens from a source path in update mode, or have callers pass a source-tree
`File` (e.g. `File(projectDir, "src/test/resources/goldens/...")`) rather than a classpath URL.
Document the limitation explicitly if classpath resolution is intentional. At minimum, the KDoc
re-bless instructions should warn that captures must be copied from `build/resources/test/...` back
into `src/test/resources/...` by hand.

### WR-02: Acceptance test ignores git exit code — a git error is mis-read as the file list

**File:** `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt:216-235`
**Issue:** The `runCatching` block starts `git ls-files`, reads stdout+stderr (merged via
`redirectErrorStream(true)`), and calls `waitFor()` — but never inspects the exit code. If git runs
but fails (not a repo, bad pathspec, detached/corrupt index), it writes a `fatal: ...` message that,
because stderr is merged into stdout, becomes `gitOutput`. Since `gitOutput != null`, the code takes
the git branch and `assertEquals("", gitOutput)` fails with R6 firing on a *git error*, not on
actual committed evidence — a false positive that masquerades as a real regression. The cleaner
filesystem fallback (the `else` branch) is only reached when the process fails to *start* at all.
**Fix:** Capture the exit code and treat non-zero as "git unavailable/failed" → fall through to the
filesystem fallback:
```kotlin
val proc = ProcessBuilder("git", "ls-files", "...").directory(root).redirectErrorStream(true).start()
val out = proc.inputStream.bufferedReader().readText().trim()
val code = proc.waitFor()
val gitOutput = if (code == 0) out else null
```

### WR-03: Non-blocking ProcessBuilder read can deadlock / hang the test on large output

**File:** `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt:218-225`
**Issue:** `process.inputStream.bufferedReader().readText()` reads to EOF and then calls `waitFor()`.
This is the correct ordering for normal output, but there is no timeout on `waitFor()`. If `git` for
any reason blocks (e.g. credential/hook prompt, a pager that ignores the non-tty heuristic, an index
lock held by a concurrent Gradle-driven git operation), the test hangs indefinitely with no
diagnostic. The CI runs many builds in parallel against the same checkout (per project memory the
pluginTest publish/test ordering already has a known race), so a held `.git/index.lock` is a real
possibility.
**Fix:** Bound the wait: `if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); /* fall back */ }`
and treat timeout the same as git-unavailable.

### WR-04: GBC auto-detect uses `InputStream.skip`, whose short-read contract is not honored

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt:90-95`
**Issue:** `romFile.inputStream()` returns an unbuffered `FileInputStream`; `stream.skip(0x143L)` is
called and its return value is discarded. `InputStream.skip` (and `FileInputStream.skip`) is
explicitly permitted to skip fewer bytes than requested. If a short skip occurred, the subsequent
`read()` would read the wrong offset and could classify a DMG ROM as GBC (or vice-versa) — and the
D-07 guard (`check(baseConfig.gbcMode)` in the visual-evidence tests) is the *only* thing standing
between a mis-detected DMG ROM and an inverted-palette golden bless. The unit tests pass only because
`FileInputStream.skip` happens to be exact on local regular files; the contract does not guarantee
it. This is a robustness gap in the safety mechanism, not just style.
**Fix:** Use a positional/guaranteed read instead of fire-and-forget skip:
```kotlin
val cgbByte = romFile.inputStream().use { s ->
    val buf = ByteArray(0x144)
    val n = s.readNBytes(buf, 0, 0x144)   // JDK 9+; reads fully unless EOF
    if (n <= CGB_FLAG_OFFSET.toInt()) -1 else buf[0x143].toInt() and 0xFF
}
gbcMode = cgbByte == CGB_ENHANCED || cgbByte == CGB_ONLY
```
(`and 0xFF` also future-proofs against any signed-byte path.)

### WR-05: `allTestKtFiles` path filter is OS-specific and silently skips files on Windows

**File:** `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt:74-77`
**Issue:** The scanner filters with `it.path.contains("/src/test/")` (forward-slash literal). On
Windows, `File.path` uses `\` separators, so the filter matches nothing and the R1/R5 gates scan
**zero** files — silently passing regardless of how many forbidden `.planning`/`copy(gbcMode)`
patterns exist. The same forward-slash assumption appears in the R6 filters
(lines 242-243, 266: `"/.planning/phases/"`, `"/evidence/"`, `"/src/test/resources/goldens/"`),
so the entire acceptance guard is effectively a no-op on non-POSIX hosts. A guard that silently
passes is worse than no guard. Even if CI is Linux-only today, a regression guard that depends on
the host OS is fragile.
**Fix:** Normalize separators before matching, e.g. `it.invariantSeparatorsPath.contains("/src/test/")`
(Kotlin's `File.invariantSeparatorsPath`) for every path-substring check in this file.

## Info

### IN-01: `assertGoldenMatch`'s `scratchDir` param does not control where the capture is written

**File:** `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GoldenAssertions.kt:44-52`
**Issue:** The KDoc says `scratchDir` is "Directory used for captured PNGs and diff images." It is
not: `agent.captureScreenshot(label)` writes to the agent's `config.screenshotDir`
(`AgentDebugSession.captureScreenshot` → `ScreenshotCapture.capture(outputDir = config.screenshotDir)`).
`scratchDir` is only forwarded to `VisualDiff.compare` as the diff-image dir. Callers currently set
both to the same dir so behavior is correct, but the docstring overstates the parameter's role and
invites a future caller to pass a different `scratchDir` expecting the capture to land there.
**Fix:** Reword the KDoc to "Directory for diff images on mismatch (captures are written to the
agent's configured screenshotDir)."

### IN-02: R6 golden-count gate hardcodes 22 with no per-example breakdown enforcement

**File:** `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt:259-276`
**Issue:** The gate asserts a global count of exactly 22 PNGs but does not assert the 6/16 split
(metasprites/platformer-template) it documents. Deleting one metasprites golden and adding one
platformer golden keeps the total at 22 and passes, defeating the intent. Magic number `22` also
appears only here; if a third example adds goldens the constant must be hunted down.
**Fix:** Either assert per-directory counts (`...goldens/metasprites/` == 6,
`...goldens/platformer-template/` == 16) or extract the expected counts to named constants and sum
them so the breakdown is the source of truth.

### IN-03: `isPathConstructionWithPlanning` matches assertion-message strings as path constructions

**File:** `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt:92-105`
**Issue:** The heuristic flags any non-comment line containing both `.planning` and a construction
keyword (`File(`, `.resolve(`, `Path(`). A legitimate assertion message that names a `.planning`
path for traceability *and* happens to also contain `File(` elsewhere on the same line (e.g. a
multi-arg call or a string-interpolated `${File(...).name}`) would be a false positive — the KDoc
claims such message strings are "permitted," but the implementation does not actually distinguish
a quoted message from a path expression. This is a precision gap that could block a future
legitimate edit. (Currently no offenders, so low impact.)
**Fix:** Tighten by requiring `.planning` to appear inside the first string argument of the
construction call, or by excluding lines where `.planning` only appears inside a string that is not
adjacent to the construction keyword. Given the difficulty, at minimum document the known
false-positive shape.

### IN-04: `findRepoRoot` falls back to a non-root directory after 10 levels without failing

**File:** `gbkt-test/src/test/kotlin/io/github/gbkt/test/CleanTreeEvidenceAcceptanceTest.kt:57-64`
**Issue:** If `settings.gradle.kts` is not found within 10 parent levels (or `parentFile` becomes
null), the method returns whatever `dir` happens to be — an arbitrary directory. Every gate then
scans/greps from the wrong root, most likely finding zero offenders and passing silently. A guard
that can pass against the wrong tree is unreliable.
**Fix:** Throw `IllegalStateException("repo root with settings.gradle.kts not found from ${System.getProperty("user.dir")}")`
when the loop exhausts, so a mis-resolved root fails loudly instead of vacuously passing.

---

_Reviewed: 2026-06-15_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
