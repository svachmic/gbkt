# Phase 19: Codegen Fixes — Metasprite Cluster - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-13
**Phase:** 19-codegen-fixes-metasprite-cluster
**Areas discussed:** Screenshot harness, Emission-guard placement, Audit doc format, Byte-identity oracle

---

## Screenshot Capture Harness (FIX-01 + ROM smoke)

| Option | Description | Selected |
|--------|-------------|----------|
| Extend JVM UAT harness | Reuse/extend MetaspriteUatTest StepAgent + captureAndRename() → PNGs into evidence dir; repeatable, committed, deterministic; metasprites-stress may need a small new UAT scaffold | ✓ |
| MCP play session | Capture via MCP gbkt-emulator tools (emulator_start gbcMode + screenshot); faster but capture not committed as a repeatable test | |
| Hybrid | JVM harness for seeds with UAT coverage; MCP for ROM-smoke shot and seeds lacking a JVM path | |

**User's choice:** Extend JVM UAT harness
**Notes:** Matches established metasprites UAT pattern; MCP server wraps the same StepAgent API so JVM-tier ≡ MCP-tier. GBC-mode + clean-rebuild-before-capture constraints carried from SPEC.

---

## FIX-02 Emission-Guard Placement (SEED-007..011)

| Option | Description | Selected |
|--------|-------------|----------|
| Place by observability | Generic codegen guards (007/008/011) in gbkt-backend-gbdk; stress-specific (009/010) in gbkt-examples/metasprites-stress where that output is real | ✓ |
| All in backend-gbdk | All 5 in backend with synthetic fixtures; single location but 009/010 may not faithfully reproduce | |
| All in example modules | All 5 against concrete elephant/tiger fixtures; faithful but generic invariants better guarded engine-level | |

**User's choice:** Place by observability
**Notes:** Audit existing coverage first; author only missing guards (no duplicate coverage). Assert-GREEN + RED-by-design comment, no revert demonstration.

---

## FIX-02 Audit Document

| Option | Description | Selected |
|--------|-------------|----------|
| Standalone audit doc | Dedicated 19-AUDIT-FIX-02.md table: seed → test → assertion → new/existing → reverted scenario | ✓ |
| Fold into VERIFICATION.md | Mapping table inside phase VERIFICATION.md; fewer files but mixes deliverable with verification output | |
| Fold into CONTEXT.md | Capture mapping in CONTEXT.md; earliest but audit is an execution-time deliverable | |

**User's choice:** Standalone audit doc
**Notes:** Clean checkable acceptance-criteria deliverable, kept separate from verification output.

---

## Byte-Identity Oracle (Req 5 — no production codegen drift)

| Option | Description | Selected |
|--------|-------------|----------|
| Procedural before/after diff | Hash generated main.c + bank files (both example ROMs) at phase start, re-diff at end; any diff explained + screenshot-re-confirmed; existing elephant.c baseline test still runs | ✓ |
| Add committed main.c baseline | New committed byte-identity baseline for main.c/bank files; stronger but brittle to rebuild non-determinism | |
| elephant.c baseline only | Rely solely on existing test; minimal but Req 5 names main.c/bank files which it doesn't cover | |

**User's choice:** Procedural before/after diff
**Notes:** Confirmation-only phase touches no codegen; same-session diff robust to toolchain non-determinism, avoids maintained-baseline churn.

---

## Claude's Discretion

- Exact test method/assertion names, evidence PNG filenames, and the precise hashing command for the byte-identity diff — left to planner/executor provided acceptance criteria are met.

## Deferred Ideas

- Banks trio (SEED-014/015/016) + tRNS sprite outline → Phase 20.
- Platformer cEmit escapes + remaining DSL/tooling seeds → Phase 21.
- Merging PR #77 (S3776 burn-down) → held open until 19/20/21 complete.
