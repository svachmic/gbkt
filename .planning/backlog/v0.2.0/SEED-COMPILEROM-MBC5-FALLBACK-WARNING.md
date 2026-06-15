---
id: SEED-COMPILEROM-MBC5-FALLBACK-WARNING
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: medium
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
original_id: compilerom-silent-mbc5-fallback-warning
title: Warn (not silently fall back to MBC5) when cartridge metadata is missing
source: phase-13.1-code-review
area: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt
original_priority: medium
---

## Context

Carried advisory item **WR-04** from `13.1-REVIEW.md`.

`CompileRomTask.readMbcType` falls back to `return if (hasRam) "0x1B" else "0x19"` (hardcoded
MBC5) when banking is detected but `gbkt-build.properties` is entirely absent. With the new
typed `Cartridge` enum and reflective `getMbcByte` lowering (D-03), the metadata file *should*
always carry the correct byte; a missing file now represents a real lowering failure that is
being papered over. The ROM is built as MBC5 regardless of the author's
`config { cartridge(...) }` selection, and the mismatch is invisible.

(Note: WR-05 — the unguarded reflective `getMbcByte` cast that could cause the metadata write
to be skipped — was already fixed in-phase, commit 99b83634. This item is the complementary
"surface the fallback" half.)

## Fix

When banking is detected but no `mbcType` was found in metadata, log a WARNING that the
declared cartridge could not be read and the build is falling back to MBC5, so the silent
override is at least surfaced to the developer.
