---
status: resolved
phase: 17-docs-reconciliation-and-quality-cleanup
source: [17-VERIFICATION.md]
started: 2026-06-12T22:05:00Z
updated: 2026-06-13T00:00:00Z
resolution: Both items developer-decided 2026-06-13 and routed to Phase 18 as SEED-027 (WR-01) and SEED-028 (WR-04/05). No fix applied in Phase 17.
---

## Current Test

(none — all items resolved)

## Tests

### 1. WR-01 — GAME_BOY_COLOR_SCREEN bitsPerPixel correctness vs advisory status
expected: The preset at gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt declares bitsPerPixel = 4 while the shipped GameBoyColorProfile uses bitsPerPixel = 2. KDoc asserts "All backends MUST derive from this object" but only SCREEN_WIDTH/HEIGHT are consumed. Developer decides: align the preset to 2bpp, or soften the KDoc, or accept as advisory (zero consumers today — no runtime regression possible until SEED-TARGETPROFILE-SCREEN-THREADING lands).
result: [resolved] Decision 2026-06-13: align preset to 2bpp + narrow KDoc to width/height. Routed to Phase 18 as SEED-027.

### 2. WR-04/WR-05 — ConfigBuilder breaking change advisory for external consumers
expected: Public mutable properties romBanks/ramBanks (property setters) were replaced with function setters in 17-11 with no deprecation shim, and GbktExtension.kt:166 (+3 other sites) still instruct the old `config { ramBanks = N }` syntax. Developer confirms no external v0.1.0 consumers would break on upgrade, OR approves a @Deprecated shim plan, and the stale guidance strings get fixed.
result: [resolved] Decision 2026-06-13: accept hard removal (no shim) + v0.1.1 migration note + fix 4 stale guidance strings. Routed to Phase 18 as SEED-028.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None — both items resolved as developer decisions and routed to Phase 18 (SEED-027, SEED-028).
