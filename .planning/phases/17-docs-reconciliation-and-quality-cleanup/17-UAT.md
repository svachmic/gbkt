---
status: resolved
phase: 17-docs-reconciliation-and-quality-cleanup
source: [17-VERIFICATION.md]
started: 2026-06-12T22:05:00Z
updated: 2026-06-15T00:00:00Z
resolution: Both items fully resolved 2026-06-15. WR-01 ALREADY FIXED (bitsPerPixel=2 at lines 34+53, Phase 18 plan 18-05, SEED-027 archived VERIFIED-ALREADY-FIXED). WR-04/05 ADVISORY ACCEPTED for v0.1.1 — stale guidance strings fixed Phase 18 plan 18-12 (SEED-028 archived VERIFIED-ALREADY-FIXED); API-consistency remainder migrated to v0.2.0 seed (SEED-CONFIGBUILDER-SETTER-API).
---

## Current Test

(none — all items resolved)

## Tests

### 1. WR-01 — GAME_BOY_COLOR_SCREEN bitsPerPixel correctness vs advisory status
expected: The preset at gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt declares bitsPerPixel = 4 while the shipped GameBoyColorProfile uses bitsPerPixel = 2. KDoc asserts "All backends MUST derive from this object" but only SCREEN_WIDTH/HEIGHT are consumed. Developer decides: align the preset to 2bpp, or soften the KDoc, or accept as advisory (zero consumers today — no runtime regression possible until SEED-TARGETPROFILE-SCREEN-THREADING lands).
result: [resolved] Decision 2026-06-13: align preset to 2bpp + narrow KDoc to width/height. Fixed by Phase 18 plan 18-05. SEED-027 archived VERIFIED-ALREADY-FIXED (confirmed 2026-06-15: bitsPerPixel=2 at TargetProfiles.kt lines 34 and 53).

### 2. WR-04/WR-05 — ConfigBuilder breaking change advisory for external consumers
expected: Public mutable properties romBanks/ramBanks (property setters) were replaced with function setters in 17-11 with no deprecation shim, and GbktExtension.kt:166 (+3 other sites) still instruct the old `config { ramBanks = N }` syntax. Developer confirms no external v0.1.0 consumers would break on upgrade, OR approves a @Deprecated shim plan, and the stale guidance strings get fixed.
result: [resolved] Decision 2026-06-13: accept hard removal (no shim) + v0.1.1 migration note + fix 4 stale guidance strings. Fixed by Phase 18 plan 18-12. SEED-028 archived VERIFIED-ALREADY-FIXED (confirmed 2026-06-15: all 4 stale guidance strings corrected). API-consistency remainder (uniform setter convention across all ConfigBuilder fields) deferred to v0.2.0 seed SEED-CONFIGBUILDER-SETTER-API.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None — both items resolved as developer decisions and routed to Phase 18 (SEED-027, SEED-028).
