# Phase 16: Seed Triage - Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 8 artifact types to be created
**Analogs found:** 5 / 8 (3 novel — first triage phase in project)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `.planning/phases/16-seed-triage/TRIAGE.md` | disposition table | batch / audit | `.planning/research/SUMMARY.md` (structured multi-table report) | partial-match (same report genre, different schema) |
| `.planning/phases/16-seed-triage/evidence/substrate-sha.txt` | evidence artifact | file-I/O | `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` (named evidence artifact) | role-match |
| `.planning/phases/16-seed-triage/evidence/substrate-test-report.txt` | evidence artifact | file-I/O | same as above | role-match |
| `.planning/phases/16-seed-triage/evidence/<SEED-ID>/` | evidence per seed | file-I/O | `.planning/seeds/evidence/` directory convention | role-match |
| `.planning/phases/16-seed-triage/visual-review-document.md` | review gate doc | batch / audit | `.planning/verifier-gates.md` (structured verification gate) | partial-match |
| `seed frontmatter stamps (D-02)` | metadata patch | transform | `.planning/seeds/SEED-001-ide-and-tooling.md` YAML block (lines 1-8) | exact (YAML shape); `.planning/seeds/SEED-014-*.md` (markdown-only shape) |
| `.planning/seeds/archive/` directory | file movement | file-I/O | no analog (first use) | none |
| `.planning/backlog/v0.2.0/` directory | file movement | file-I/O | no analog (first use) | none |

---

## Pattern Assignments

### `TRIAGE.md` (disposition table, batch/audit)

**Analog:** `.planning/research/SUMMARY.md` (multi-section structured report) + RESEARCH.md §TRIAGE.md Recommended Schema

**Header block pattern** — follow SUMMARY.md's frontmatter style:
```markdown
# Phase 16: Seed Triage — Disposition Table

**Substrate SHA:** `<git rev-parse HEAD output>`
**Substrate build date:** 2026-06-12
**Total entries:** 47 (44 seeds + 3 folded todos)
**Status:** IN PROGRESS → FINAL (after W3 visual review gate)
```

**Table schema** (Claude's discretion per D-10; this schema is the canonical form):
```markdown
| ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes |
|----|-------|------|-------------|----------|------------------|-------|
| SEED-001 | IDE & Tooling | re-deferred | RE-DEFERRED | REQUIREMENTS.md IDE-02 | v0.2.0 backlog | D-12 fast-path |
| SEED-004 | Elephant tile rendering | visual | TBD | evidence/SEED-004/screenshot.png | Phase 19 FIX-01 | needs W3 gate |
| SEED-014 | bkg_tiles_load_banked gating | jvm-test | TBD | evidence/SEED-014/inv2-output.txt | Phase 20 FIX-03 | run INV-2 sentinel |
```

**Type column values** (from RESEARCH.md §TRIAGE.md Recommended Schema):
- `visual` — requires MCP screenshot at HEAD (D-08 visual gate)
- `emission` — verified by generated-C inspection
- `jvm-test` — verified by `./gradlew :module:test --tests "..."`
- `source-only` — verified by Serena symbol search (no build needed)
- `re-deferred` — fast-path RE-DEFERRED (D-12)

**Disposition column values** (D-10; exactly four from Phase 16):
- `VERIFIED-ALREADY-FIXED` — executable evidence confirms defect absent at HEAD
- `CONFIRMED-OPEN` — repro evidence confirms defect present at HEAD; routing required
- `RE-DEFERRED` — moved to v0.2.0 backlog with rationale
- `INVALID` — not-a-bug with written rationale + same evidence bar as VERIFIED-ALREADY-FIXED

**SHA pinning pattern** (D-14):
```markdown
> **Evidence substrate:** All evidence in this table was captured against commit
> `<SHA>` (recorded in `evidence/substrate-sha.txt`). Evidence captured against
> a different SHA is invalid and must be re-captured.
```

---

### `evidence/substrate-sha.txt` (evidence artifact, file-I/O)

**Analog:** `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` (named evidence file in a dedicated evidence directory)

**Content pattern** — simple two-line text file, no markup:
```
<output of: git rev-parse HEAD>
Captured: 2026-06-12 during Phase 16 substrate pass (W1)
```

**Directory convention** (from RESEARCH.md §Recommended Evidence Directory Structure):
```
.planning/phases/16-seed-triage/evidence/
├── substrate-sha.txt
├── substrate-test-report.txt
├── SEED-004/
│   └── screenshot.png
├── SEED-014/
│   └── inv2-test-output.txt
└── SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/
    ├── screenshot-head.png
    └── reference-pointer.txt   # path to Phase 13.8 baseline
```

---

### `evidence/<SEED-ID>/` per-seed directories (evidence artifacts, file-I/O)

**Naming rule:** Match the seed filename base exactly (e.g., `SEED-004` for `SEED-004-metasprites-corrupted-tile-rendering.md`). For folded todos, use a short slug (e.g., `TODO-metasprites-baseline`, `TODO-13.8-wr-followups`, `TODO-triggersystem-validation`).

**Screenshot file naming** (MCP emulator pattern from RESEARCH.md):
```
emulator_screenshot path: .planning/phases/16-seed-triage/evidence/<SEED-ID>/screenshot.png
```
For seeds requiring multiple captures (e.g., SEED-013 B-press):
```
evidence/SEED-013/screenshot-before-b-press.png
evidence/SEED-013/screenshot-after-b-press.png
```

**JVM test output file naming:**
```
evidence/SEED-014/inv2-test-output.txt    # full stdout from BanksEmissionTest INV-2
evidence/SEED-014/inv6-test-output.txt
```

**Generated-C excerpt file naming:**
```
evidence/SEED-006/main-c-excerpt.txt   # grep output showing relevant C lines
```

---

### `visual-review-document.md` (review gate doc, batch)

**Analog:** `.planning/verifier-gates.md` (structured verification doc with trigger/command/pass-criteria tables)

**Format pattern** — one section per visual seed, in cluster order (Cluster A metasprites → Cluster C platformer):
```markdown
# Phase 16: Batch Visual Review Gate (D-08)

**Status:** PENDING HUMAN APPROVAL
**Visual seeds:** 8
**Reference baseline source:** `.planning/phases/13.8-*/evidence/` (approved Phase 13.8 baselines)

---

## SEED-004 — Metasprites Elephant Tile Rendering

**ROM:** `gbkt-examples/metasprites/build/gbkt/output/metasprites.gb`
**Capture mode:** `gbcMode=true`
**Proposed verdict:** CONFIRMED-OPEN (proposed by cluster agent)

| HEAD screenshot | Reference |
|----------------|-----------|
| ![HEAD](evidence/SEED-004/screenshot.png) | (no prior approved baseline — compare to png2asset reference shape) |

**Agent rationale:** [agent fills this in]

**Human verdict:** ☐ CONFIRMED-OPEN  ☐ VERIFIED-ALREADY-FIXED  ☐ Override (describe):

---

## SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ

**ROM:** `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`
**Capture mode:** `gbcMode=true`
**Proposed verdict:** TBD (agent to fill)

| HEAD screenshot | Phase 13.4 "before" (defect) | Phase 13.8 approved baseline |
|----------------|------------------------------|------------------------------|
| ![HEAD](evidence/SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/screenshot-head.png) | ![before](../../seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png) | (path to 13.8 evidence) |

**Human verdict:** ☐ CONFIRMED-OPEN  ☐ VERIFIED-ALREADY-FIXED  ☐ Override (describe):
```

**Sign-off block** at bottom:
```markdown
---

## Human Approval

Reviewer: [name]
Date: [date]
All verdicts above locked: ☐ YES

> After approval, update TRIAGE.md rows for all visual seeds above.
```

---

### Seed frontmatter stamp (D-02, transform)

**Analog A — Seeds WITH existing YAML frontmatter** (14 seeds, e.g., `SEED-001-ide-and-tooling.md` lines 1-8):

Existing frontmatter shape:
```yaml
---
id: SEED-001
status: dormant
planted: 2026-05-13
planted_during: v1.0 / Phase 07.9 closeout
trigger_when: when v2.0 milestone is created (after v1.0 ships and audits clean)
scope: large
---
```

Add three new fields inside the existing `---` block:
```yaml
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-001"
triage_date: 2026-06-12
```

**Analog B — Seeds WITHOUT frontmatter** (30 seeds, markdown-only, e.g., `SEED-014-*.md` starts directly with `# SEED-014:`):

Add a blockquote immediately after the H1 title line:
```markdown
# SEED-014: `_bkg_tiles_load_banked` helper gated behind sport-racing genre

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-014](.planning/phases/16-seed-triage/TRIAGE.md#SEED-014) · 2026-06-12

**Surfaced by:** Phase 11 ...
```

**Rule:** Never duplicate the disposition details — the stamp is a pointer only (D-02). All detail lives in TRIAGE.md.

---

## Shared Patterns

### Substrate Pass Command (D-13)
**Apply to:** W1 substrate plan; copy exactly (no parallelism, no separate `clean` invocations)

```bash
./gradlew \
  :gbkt-examples:pong:clean :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:clean :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:clean :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom \
  :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom \
  && ./gradlew test
git rev-parse HEAD > .planning/phases/16-seed-triage/evidence/substrate-sha.txt
```

Post-substrate: rebuild MCP shadow JAR (may have been wiped by `clean`):
```bash
./gradlew :gbkt-mcp-server:shadowJar
```

### MCP Emulator Invocation (visual seeds)
**Apply to:** All visual-seed cluster agents (Cluster A metasprites, Cluster C platformer)

```
emulator_start:
  romPath: "gbkt-examples/<example>/build/gbkt/output/<name>.gb"
  gbcMode: true
  symFile: "gbkt-examples/<example>/build/gbkt/output/<name>.noi"

emulator_screenshot:
  path: ".planning/phases/16-seed-triage/evidence/<SEED-ID>/screenshot.png"
```

**Always `gbcMode: true` for:** `metasprites.gb`, `metasprites-stress.gb`, `platformer-template.gb`
**Platformer traversal:** Use RIGHT+A (jump) — held-RIGHT stalls at a designed tree obstacle

### BanksEmissionTest INV-2 Sentinel (SEED-014)
**Apply to:** Banks cluster plan for SEED-014

```bash
./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"
```

Capture stdout to `evidence/SEED-014/inv2-test-output.txt`. GREEN = VERIFIED-ALREADY-FIXED; RED = CONFIRMED-OPEN → route Phase 20 FIX-03.

### Serena Source Inspection (source-only seeds)
**Apply to:** Cluster D DSL/tooling seeds (SEED-002, SEED-021, SEED-022, SEED-023, SEED-025, SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION, etc.)

Per RESEARCH.md §Code/build/emission-verifiable seeds:
```
mcp__serena__find_symbol <symbolName>          # locate function/class definition
mcp__serena__search_for_pattern <pattern>      # cross-module pattern search
```

Capture Serena output to `evidence/<SEED-ID>/source-inspection.txt` or inline in the TRIAGE.md row Evidence cell.

### Generated-C Inspection Pattern (emission seeds)
**Apply to:** Cluster A emission seeds (SEED-006, SEED-007, SEED-008, SEED-009, SEED-010, SEED-011), Cluster B (SEED-015)

Primary file: `gbkt-examples/<example>/build/gbkt/generated/main.c` (from substrate pass)
Secondary: `gbkt-examples/<example>/build/gbkt/generated/bank1.c`

Evidence capture pattern:
```bash
grep -n "<pattern>" gbkt-examples/<example>/build/gbkt/generated/main.c \
  > .planning/phases/16-seed-triage/evidence/<SEED-ID>/main-c-excerpt.txt
```

**Anti-pattern from RESEARCH.md §Don't Hand-Roll:** Do NOT use file-level `contains()` to check for patterns that may appear in multiple functions. Use `extractFunctionBody()` pattern (see `BanksEmissionTest.kt` lines 200+) or scope the grep to the relevant function context.

### Pong PASS* Convention
**Apply to:** W1 substrate pass plan, TRIAGE.md row for pong-related seeds (if any)

```
# Pong ROM hash is non-deterministic (sdcc/lcc toolchain — pre-existing)
# Record as PASS* in substrate evidence. Never investigate pong hash drift.
```

---

## No Analog Found

| File/Artifact | Role | Data Flow | Reason |
|---------------|------|-----------|--------|
| `.planning/seeds/archive/` directory | file movement | file-I/O | First archive directory created in project; no prior convention exists |
| `.planning/backlog/v0.2.0/` directory | file movement | file-I/O | First milestone backlog directory created; no prior convention |
| `TRIAGE.md` row schema | disposition table | batch | No prior triage phase in project history; schema defined in RESEARCH.md §TRIAGE.md Recommended Schema and reproduced above |

**For these artifacts:** Use the schema defined in RESEARCH.md directly (reproduced in Pattern Assignments above). No codebase analog exists — the Research doc IS the authoritative pattern source.

---

## Metadata

**Analog search scope:** `.planning/` directory tree (phases, seeds, research, verifier-gates); no Kotlin source files needed (Phase 16 produces no source changes)
**Files scanned:** 6 planning docs + 2 seed files (representative samples of with/without YAML frontmatter)
**Pattern extraction date:** 2026-06-12
