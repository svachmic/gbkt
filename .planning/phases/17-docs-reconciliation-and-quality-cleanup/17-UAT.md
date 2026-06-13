---
status: testing
phase: 17-docs-reconciliation-and-quality-cleanup
source: [17-VERIFICATION.md]
started: 2026-06-12T22:05:00Z
updated: 2026-06-12T22:05:00Z
---

## Current Test

number: 1
name: WR-01 — GAME_BOY_COLOR_SCREEN bitsPerPixel correctness vs advisory status
expected: |
  Developer either (a) aligns GAME_BOY_COLOR_SCREEN to bitsPerPixel=2 and wires
  GameBoyColorProfile.screen to the preset, OR (b) softens the MUST-derive KDoc to
  cover only width/height and defers full alignment to SEED-TARGETPROFILE-SCREEN-THREADING.
awaiting: user response

## Tests

### 1. WR-01 — GAME_BOY_COLOR_SCREEN bitsPerPixel correctness vs advisory status
expected: The preset at gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt declares bitsPerPixel = 4 while the shipped GameBoyColorProfile uses bitsPerPixel = 2. KDoc asserts "All backends MUST derive from this object" but only SCREEN_WIDTH/HEIGHT are consumed. Developer decides: align the preset to 2bpp, or soften the KDoc, or accept as advisory (zero consumers today — no runtime regression possible until SEED-TARGETPROFILE-SCREEN-THREADING lands).
result: [pending]

### 2. WR-04/WR-05 — ConfigBuilder breaking change advisory for external consumers
expected: Public mutable properties romBanks/ramBanks (property setters) were replaced with function setters in 17-11 with no deprecation shim, and GbktExtension.kt:166 (+3 other sites) still instruct the old `config { ramBanks = N }` syntax. Developer confirms no external v0.1.0 consumers would break on upgrade, OR approves a @Deprecated shim plan, and the stale guidance strings get fixed.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
