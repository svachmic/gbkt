# Cluster A: Metasprites Emission — Draft TRIAGE Rows (Plan 04)

**Cluster:** A (Metasprites emission/structural)
**Plan:** 16-04
**Substrate SHA:** 8cef3dbca7d0868f42cf0d627921b8559d7754e8
**Drafted by:** Plan 04 executor, 2026-06-12
**Status:** DRAFT — rows ready to merge into TRIAGE.md in Plan 09

---

> NOTE: These are PROPOSED dispositions for Plan 04's 8 entries.
> TRIAGE.md (owned by Plan 09) is the canonical record — do NOT edit TRIAGE.md in this plan.
> Pre-analysis expected CONFIRMED-OPEN for most of these; actual inspection shows all 6
> emission seeds + sprite-outline seed were fixed in intermediate phases. The stale-baseline
> todo was resolved by baseline regeneration post Phase-13.6 visual approval.

---

## Proposed TRIAGE rows (8 entries, Plan 04 scope)

| ID | Title | Type | Proposed Disposition | Evidence Path | Fix-phase routing | Notes |
|----|-------|------|---------------------|---------------|-------------------|-------|
| SEED-006 | _elephant_subPalette never assigned in frame loop | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-006/main-c-excerpt.txt | — | `_elephant_subPalette = subpal;` present at play_frame():283 in metasprites/main.c; scoped function-body grep |
| SEED-007 | GameBuilder actor palette slot defaults to 0 | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-007/main-c-excerpt.txt | — | `actorPaletteAutoSlot++` counter present at GameBuilder.kt:716; comment documents the bug + fix; latent in metasprites example (no actor-level palette injection used) |
| SEED-008 | VRAM tile-slot collision actors + metasprites | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-008/main-c-excerpt.txt | — | metasprites-stress main.c: set_sprite_data(0,2,player), set_sprite_data(2,N,elephant), set_sprite_data(2+N,M,tiger) — shared monotonic VRAM allocator (Route A) confirmed |
| SEED-009 | `<gbdk/metasprites.h>` missing in bank1.c | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-009/main-c-excerpt.txt | — | metasprites-stress bank1.c:7 includes `<gbdk/metasprites.h>`; bank contains move_metasprite_* calls; include is present and required |
| SEED-010 | Non-namespaced metasprite descriptor symbol names | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-010/main-c-excerpt.txt | — | metasprites-stress: elephant_metasprites[] and tiger_metasprites[] — namespaced by ID via png2asset native output; no sprite_metasprite_0 collision present |
| SEED-011 | hiwater reset per moveMetasprite call collides OAM | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-011/main-c-excerpt.txt | — | metasprites-stress bank1.c play_frame(): hiwater=0 once at frame start; hiwater+= throughout both metasprites; hide_sprites_range once at frame end; Route A fix confirmed |
| SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX | Sprite outline lost when tRNS index != 0 | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX/source-inspection.txt | — | Phase 13.6 tRNS auto-route in ConvertSpritesTask.kt:328-372; getTransparentIndexShared reads tRNS, prePermuteIndexedPng re-orders palette; elephant.c header confirms gbkt_permuted_elephant.png temp path; visual closure oracle (screenshot) belongs to Phase 20 D-08 batch gate |
| TODO-metasprites-baseline | Stale elephant.c.baseline since Phase 12.8 | jvm-test | VERIFIED-ALREADY-FIXED | evidence/TODO-metasprites-baseline/test-output.txt | — | MetaspritesGeneratedSpriteByteIdentityTest GREEN at HEAD; baseline modified Jun 10 2026 (after Phase 12.8); baseline content includes -keep_palette_order matching current output; D-15 satisfied by Phase 13.6 visual approval; todo is moot |

---

## Key deviation from pre-analysis

Plan 16-RESEARCH.md Cluster A pre-analysis expected most of these seeds to be CONFIRMED-OPEN:
- SEED-006: "Likely CONFIRMED-OPEN (sub-palette global not assigned)"
- SEED-007: "Likely CONFIRMED-OPEN (same `else 0` bug from Phase 10)"
- SEED-008: "Likely CONFIRMED-OPEN (structural latent)"
- SEED-009: "Depends on banking config"
- SEED-010: "CONFIRMED-OPEN (symbol collision)"
- SEED-011: "CONFIRMED-OPEN (hiwater collision)"

All 6 emission seeds and both Plan 04 non-emission items are actually VERIFIED-ALREADY-FIXED
at substrate SHA 8cef3dbc. The fixes were introduced during Phases 10.1, 12.x, 13.3, 13.6,
and 13.8 — after the seeds were planted but before Phase 16 triage.

This is the expected behavior of seed-triage: pre-analysis assumptions are tested against
evidence; actual dispositions are evidence-driven, not assumption-driven.

## Evidence quality summary

All 8 entries use scoped evidence (function-body inspection or source-symbol inspection),
never file-level contains() per T-16-09 / RESEARCH anti-pattern warning.

For the latent seeds (SEED-008, SEED-010, SEED-011): evidence uses metasprites-stress
(the multi-metasprite example) which exercises the codepaths the single-metasprites example
cannot trigger — satisfying D-07 "repro shaped so the receiving fix phase can adopt it"
(in these cases, the repro is the verified ABSENCE of the defect at HEAD; no fix phase needed).
