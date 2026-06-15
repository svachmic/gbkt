# Phase 16: Seed Triage - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 16-seed-triage
**Areas discussed:** Triage record format, Evidence standard, Open-seed routing, Execution mechanics

---

## Pre-discussion: Folded todos

Three pending todos matched the phase scope (score ≥ 0.4); user folded **all three**:

| Todo | Selected |
|------|----------|
| Stale metasprite byte-identity baselines (since 12.8) | ✓ |
| 13.8 WR follow-ups (GBDKPipelineV2/SceneVisitor/PngUtils) | ✓ |
| triggerSystem ref-registry validation at build() | ✓ |

Seven further matches were score-0.2 keyword noise ("phase") and were not presented as candidates.

---

## Triage record format

### Q1: Where does the canonical triage disposition live?

| Option | Description | Selected |
|--------|-------------|----------|
| Central table + seed stamp | TRIAGE.md canonical, per-seed frontmatter pointer stamp | ✓ |
| Central table only | Seed files untouched | |
| In-seed records only | Full disposition inline per seed | |

### Q2: What happens to seed files Phase 16 closes?

| Option | Description | Selected |
|--------|-------------|----------|
| Archive at phase close | Closed seeds move out immediately; seeds/ becomes open work queue | ✓ |
| Leave all in place | Bulk archival at milestone close (Phase 21) | |
| Move RE-DEFERRED only | Keep verified-fixed as safety margin | |

### Q3: Form of the v0.2.0 backlog record?

| Option | Description | Selected |
|--------|-------------|----------|
| Backlog dir + index entry | Full seed file → .planning/backlog/v0.2.0/ + REQUIREMENTS.md line | ✓ |
| Single BACKLOG.md file | Condensed section per seed | |
| REQUIREMENTS.md entries only | Delete files, rely on git history | |

### Q4: How are the 3 folded todos represented?

| Option | Description | Selected |
|--------|-------------|----------|
| Full rows, same pipeline | 47 uniform entries, same taxonomy + evidence bar | ✓ |
| Separate section, lighter bar | Advisory follow-ups relaxed | |
| Triage decides routing only | No verification, route only | |

---

## Evidence standard

### Q1: Minimum evidence for VERIFIED-ALREADY-FIXED (non-visual)?

| Option | Description | Selected |
|--------|-------------|----------|
| Executable evidence at HEAD | Green test run or generated-C inspection; attribution never sufficient | ✓ |
| Tiered by blast radius | Attribution OK for non-codegen seeds | |
| Attribution + green CI | Fix-commit pointer + green suite | |

### Q2: Evidence for CONFIRMED-OPEN?

| Option | Description | Selected |
|--------|-------------|----------|
| Repro at HEAD | Failing probe/test/screenshot/C-inspection; becomes fix phase's RED | ✓ |
| Code-path inspection | Unchanged-since-planted suffices | |
| Mixed | Repro for visual, inspection for structural | |

### Q3: Human sign-off for visual-seed dispositions?

| Option | Description | Selected |
|--------|-------------|----------|
| One batch review gate | All screenshots in one review document, one binding pass | ✓ |
| Per-cluster sign-off | 3–4 smaller review moments | |
| Agent verdict, human audit | Spot-check only | |

### Q4: Where do evidence artifacts live?

| Option | Description | Selected |
|--------|-------------|----------|
| Phase evidence dir | .planning/phases/16-seed-triage/evidence/ per seed ID | ✓ |
| Alongside the seeds | Expand .planning/seeds/evidence/ | |
| Inside each triage row | Embed text evidence in TRIAGE.md | |

---

## Open-seed routing

### Q1: Which dispositions can Phase 16 itself issue?

| Option | Description | Selected |
|--------|-------------|----------|
| Triage-only, no fixing | VERIFIED-ALREADY-FIXED / RE-DEFERRED / CONFIRMED-OPEN(routed); no code fixes | ✓ |
| Allow trivial inline fixes | One-liner seeds fixable in 16 | |
| Per-seed discretion | Decide during execution | |

### Q2: When triage diverges from FIX-01..06 lists?

| Option | Description | Selected |
|--------|-------------|----------|
| TRIAGE.md wins, update docs | Reconciliation pass on REQUIREMENTS/ROADMAP at phase close | ✓ |
| TRIAGE.md wins, docs untouched | Clean up at milestone close | |
| Escalate big divergence | Pause at >~25% cluster drift | |

### Q3: Six already-deferred seeds — full treatment?

| Option | Description | Selected |
|--------|-------------|----------|
| Fast-path RE-DEFERRED | Cite REQUIREMENTS.md decision; no verification | ✓ |
| Light sanity check first | Quick relevance check before stamping | |
| Full evidence bar | Uniform process | |

### Q4: Seeds that were never bugs?

| Option | Description | Selected |
|--------|-------------|----------|
| Add INVALID disposition | Fourth terminal label, same evidence bar, written rationale | ✓ |
| Fold into VERIFIED-ALREADY-FIXED | Keep 3-label taxonomy | |
| Escalate each to you | Per-seed user call | |

---

## Execution mechanics

### Q1: How is the build/test evidence substrate produced?

| Option | Description | Selected |
|--------|-------------|----------|
| One shared substrate pass | Single clean serial build: 7 ROMs + full JVM suite | ✓ |
| Per-cluster builds | Build when each cluster starts | |
| Per-seed as needed | Minimal builds per seed | |

### Q2: Master moving mid-triage?

| Option | Description | Selected |
|--------|-------------|----------|
| Pin a triage SHA | Evidence attributed to substrate SHA; 17/18 commits byte-identity-safe | ✓ |
| Strict same-SHA evidence | Re-capture on any movement | |
| Latest-HEAD per seed | No pinning | |

### Q3: Stale metasprite baselines — when to regenerate?

| Option | Description | Selected |
|--------|-------------|----------|
| Regen after visual gate | Capture at substrate, promote only after batch review approval | ✓ |
| Regen immediately at substrate | Stamp before visual verdicts | |
| Defer to Phase 19 | Regenerate after fixes land | |

### Q4: Work structure after substrate pass?

| Option | Description | Selected |
|--------|-------------|----------|
| Parallel cluster agents | Per-cluster agents off shared artifacts; no builds in agents; Serena tools mandated | ✓ |
| Serial cluster sweep | One executor, cluster by cluster | |
| Planner decides | Leave to /gsd-plan-phase | |

---

## Claude's Discretion

- Exact TRIAGE.md column layout and row schema
- Cluster boundaries for agent assignment
- Archive directory naming (consistent choice)
- Where the SEED-014 INV-2 sentinel run is scheduled (substrate pass vs banks cluster agent)

## Deferred Ideas

None — discussion stayed within phase scope.
