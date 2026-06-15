---
id: SEED-CONVERTSPRITES-HARDENING-FOLLOWUPS
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: low
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
source: 13.6-07-REVIEW.md / 13.6-VERIFICATION.md
area: gbkt-gradle-plugin / ConvertSpritesTask + PngUtils
original_priority: low
---

# ConvertSprites deterministic-permute hardening follow-ups

Three code-review warnings from the 13.6-07 gap closure were adjudicated by the phase
verifier as **acceptable follow-ups** (phase goal met; none block REQ-5/REQ-6 for the
shipped corpus). Tracked here so they are not lost. Each is a robustness/latent-hazard
fix, not a current correctness defect.

## W1 — narrow over-broad catch in `countUsedVisibleColors` (REQ-5 robustness)
File: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/PngUtils.kt`
Current: `catch (_: Exception) { 0 }`. The only caller reaches this AFTER
`getTransparentIndexShared` already parsed the PNG, so a scan-loop exception is a logic
error, not an unreadable file — swallowing to `0` makes `usedVisibleCount > 3` false and
could silently bypass the REQ-5 OBJ-palette overflow guard.
Fix: narrow the catch to `IOException` (let unexpected exceptions propagate).

## W2 — key permuted temp name on unique id, not source basename (REQ-6 latent collision)
File: `ConvertSpritesTask.kt` (call site) + `PngUtils.kt` (`prePermuteIndexedPng`)
Current deterministic name uses `file.nameWithoutExtension` → two sprites with the same
basename in different subdirs would collide. Safe today (serial processing, no collision
in the corpus). Fix: pass `stemName`/sprite id as the temp basename.

## W3 — move `sprites/tmp` out of the task `@OutputDirectory` (REQ-6 cache fingerprint)
File: `ConvertSpritesTask.kt:358` — `buildTempDir = File(sourceDir, "sprites/tmp")` where
`sourceDir = cSourceDir.get().asFile` and `cSourceDir` is `@get:OutputDirectory` on a
`@CacheableTask` (line 96). Writing undeclared temp files into the output tree pollutes
Gradle's build-cache fingerprint; the clean-rebuild determinism proof does not cover
cached/incremental builds. Fix: use the task's `temporaryDir` (`getTemporaryDir()`),
which is outside output tracking.

## Out of scope (already deferred by 13.6-07 scope lock — separate follow-ups)
WR-03 (error()→GradleException), WR-06 (platformer strict-mode buildRom verification),
IN-01..04.
