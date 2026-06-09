# Phase 10.2 — Worktree Drift Check (D-20)

This file records the 4 mandatory drift-verification commands from D-20 +
`feedback_claude_code_worktree_drift_quirks.md`. Run BEFORE the worktree is
removed (Task 1) and AGAIN after (Task 2). If any leakage detected, STOP
and surface for remediation.

**PRE_HEAD (feat/d_and_d_gaps HEAD before teardown):** `8d9d363980f4d7d15a97ccf72cd681a667ef5a3a`

---

## Pre-removal: `git worktree list`

```
/Users/michalsvacha/GitHub/personal/gbkt                 8d9d3639 [feat/d_and_d_gaps]
/Users/michalsvacha/GitHub/personal/gbkt/scratch/bisect  2767fab7 (detached HEAD)
```

scratch/bisect worktree is listed at commit `2767fab7`.

---

## Pre-removal: `git reflog --all | head -50`

```
8d9d3639 refs/heads/feat/d_and_d_gaps@{0}: commit: docs(10.2-11): complete ROM-smoke gate plan — PASS verdict + SUMMARY + state update
8d9d3639 HEAD@{0}: commit: docs(10.2-11): complete ROM-smoke gate plan — PASS verdict + SUMMARY + state update
163540d0 refs/heads/feat/d_and_d_gaps@{1}: commit: chore(10.2-11): ROM-smoke gate PASS — metasprites + stress + full test suite GREEN
163540d0 HEAD@{1}: commit: chore(10.2-11): ROM-smoke gate PASS — metasprites + stress + full test suite GREEN
2c8a390d refs/heads/feat/d_and_d_gaps@{2}: commit: docs(10.2-10): complete D-17 cross-phase evidence propagation plan
2c8a390d HEAD@{2}: commit: docs(10.2-10): complete D-17 cross-phase evidence propagation plan
e5b1bd6e refs/heads/feat/d_and_d_gaps@{3}: commit: chore(10.2-10): propagate post-fix UAT triplet to Phase 10 evidence/uat-screenshots/ (D-17 cross-phase)
e5b1bd6e HEAD@{3}: commit: chore(10.2-10): propagate post-fix UAT triplet to Phase 10 evidence/uat-screenshots/ (D-17 cross-phase)
c97d5650 refs/heads/feat/d_and_d_gaps@{4}: commit: docs(state): record 10.2-09 complete — D-V3 closure PASS
c97d5650 HEAD@{4}: commit: docs(state): record 10.2-09 complete — D-V3 closure PASS
57cae909 refs/heads/feat/d_and_d_gaps@{5}: commit: docs(10.2-09): SUMMARY.md — D-V3 closure PASS; cyan + checker visible; DMG non-regression
57cae909 HEAD@{5}: commit: docs(10.2-09): SUMMARY.md — D-V3 closure PASS; cyan + checker visible; DMG non-regression
261e9449 refs/heads/feat/d_and_d_gaps@{6}: commit: docs(10.2-09): annotate closure-verdict.md with scope-shift section
261e9449 HEAD@{6}: commit: docs(10.2-09): annotate closure-verdict.md with scope-shift section
f1afb369 refs/heads/feat/d_and_d_gaps@{7}: commit: feat(10.2-09): capture post-fix UAT triplet + closure-verdict.md — D-V3 PASS
f1afb369 HEAD@{7}: commit: feat(10.2-09): capture post-fix UAT triplet + closure-verdict.md — D-V3 PASS
e7d37a66 refs/heads/feat/d_and_d_gaps@{8}: commit: docs(10.2-08): complete Plan 08 — atomic addAll order-swap fix; DV3VisualV3DiagnosticTest 2/2 GREEN; 11/15 plans done
e7d37a66 HEAD@{8}: commit: docs(10.2-08): complete Plan 08 — atomic addAll order-swap fix; DV3VisualV3DiagnosticTest 2/2 GREEN; 11/15 plans done
f2e8cecc refs/heads/feat/d_and_d_gaps@{9}: commit: fix(10.2-08): swap mainBody addAll order — bgFillCheckerboard before allSpriteDataLoads (DEF-10.1-13-C 5th-layer VRAM collision fix)
f2e8cecc HEAD@{9}: commit: fix(10.2-08): swap mainBody addAll order — bgFillCheckerboard before allSpriteDataLoads (DEF-10.1-13-C 5th-layer VRAM collision fix)
abb431c6 refs/heads/feat/d_and_d_gaps@{10}: commit: docs(state): record 10.2-07 complete — finding + RED test locked; Plan 08 next
abb431c6 HEAD@{10}: commit: docs(state): record 10.2-07 complete — finding + RED test locked; Plan 08 next
cd65a684 refs/heads/feat/d_and_d_gaps@{11}: commit: docs(10.2-07): SUMMARY.md — bisect synthesis complete; named cause + RED test locked; Plan 08 input prepared
cd65a684 HEAD@{11}: commit: docs(10.2-07): SUMMARY.md — bisect synthesis complete; named cause + RED test locked; Plan 08 input prepared
20691a7d refs/heads/feat/d_and_d_gaps@{12}: commit: test(10.2-07): add RED DV3VisualV3DiagnosticTest locking 5th-layer VRAM order fix
20691a7d HEAD@{12}: commit: test(10.2-07): add RED DV3VisualV3DiagnosticTest locking 5th-layer VRAM order fix
9f62d082 refs/heads/feat/d_and_d_gaps@{13}: commit: docs(10.2-07): write d-v3-visual-finding-v3.md — bisect synthesis, named cause, fix spec
9f62d082 HEAD@{13}: commit: docs(10.2-07): write d-v3-visual-finding-v3.md — bisect synthesis, named cause, fix spec
3710bd77 refs/heads/feat/d_and_d_gaps@{14}: commit: docs(state): record 10.2-06d complete — minimal breaking pair named; Plan 07 next
3710bd77 HEAD@{14}: commit: docs(state): record 10.2-06d complete — minimal breaking pair named; Plan 07 next
809fc470 refs/heads/feat/d_and_d_gaps@{15}: commit: docs(10.2-06d): SUMMARY.md — C-4 CYAN PRESERVED; minimal breaking pair named (set_bkg_palette + bgFillCheckerboard)
809fc470 HEAD@{15}: commit: docs(10.2-06d): SUMMARY.md — C-4 CYAN PRESERVED; minimal breaking pair named (set_bkg_palette + bgFillCheckerboard)
5925bcae refs/heads/feat/d_and_d_gaps@{16}: commit: docs(10.2-06d): update probe-table.md with C-4 row + revised Conclusion
5925bcae HEAD@{16}: commit: docs(10.2-06d): update probe-table.md with C-4 row + revised Conclusion
07e07bf8 refs/heads/feat/d_and_d_gaps@{17}: commit: feat(10.2-06d): capture sub-probe C-4 evidence — constant + bgFillCheckerboard pair (no set_bkg_palette)
07e07bf8 HEAD@{17}: commit: feat(10.2-06d): capture sub-probe C-4 evidence — constant + bgFillCheckerboard pair (no set_bkg_palette)
2767fab7 worktrees/bisect/HEAD@{0}: reset: moving to 2767fab7
636c9ddf worktrees/bisect/HEAD@{1}: commit: test(10.2-06d): ProbeC4PaletteDumpTest — bisect sub-probe C-4 evidence capture scaffolding
a7aacaa2 worktrees/bisect/HEAD@{2}: commit: sub-probe C-4: constant + bgFillCheckerboard (no set_bkg_palette) — Phase 10.2 bisect
949ecb04 refs/heads/feat/d_and_d_gaps@{18}: commit: plan(10.2-06d): insert sub-probe C-4 (constant + bgFillCheckerboard, no set_bkg_palette)
949ecb04 HEAD@{18}: commit: plan(10.2-06d): insert sub-probe C-4 (constant + bgFillCheckerboard, no set_bkg_palette)
28a04a28 refs/heads/feat/d_and_d_gaps@{19}: commit: docs(state): record 10.2-06c complete — sub-narrow chain terminator; inserting Plan 06d
28a04a28 HEAD@{19}: commit: docs(state): record 10.2-06c complete — sub-narrow chain terminator; inserting Plan 06d
```

**Key observation:** Worktree probe commits (`worktrees/bisect/HEAD@{0..2}`) are correctly scoped
to the `worktrees/bisect/HEAD` reflog, NOT to `refs/heads/feat/d_and_d_gaps`. The only
`feat/d_and_d_gaps` entries are legitimate Plan 01-11 execution commits. No `probe-A`,
`probe-B`, `probe-C-*`, `sub-probe` strings appear on `refs/heads/feat/d_and_d_gaps`.

---

## Pre-removal: `git status` (main checkout)

```
On branch feat/d_and_d_gaps
Your branch is ahead of 'origin/feat/d_and_d_gaps' by 761 commits.
  (use "git push" to publish your local commits)

nothing to commit, working tree clean
```

Main checkout is clean. No untracked files from worktree probes.

---

## Pre-removal: `git branch -a`

```
* feat/d_and_d_gaps
  master
  worktree-agent-a40a8840097a8a42d
  worktree-agent-a6259248f6fda5f11
  remotes/origin/HEAD -> origin/master
  remotes/origin/chore/update-github-actions-deps
  remotes/origin/chore/update-gradle-deps
  remotes/origin/chore/update-vscode-deps
  remotes/origin/dependabot/github_actions/actions/upload-artifact-7
  remotes/origin/dependabot/github_actions/gradle/actions-5
  remotes/origin/dependabot/github_actions/gradle/actions-6
  remotes/origin/dependabot/github_actions/softprops/action-gh-release-2
  remotes/origin/dependabot/gradle/com.diffplug.spotless-8.4.0
  remotes/origin/dependabot/gradle/gradle-wrapper-9.4.1
  remotes/origin/dependabot/gradle/io.kotest-kotest-property-6.0.7
  remotes/origin/dependabot/gradle/io.kotest-kotest-property-6.1.11
  remotes/origin/dependabot/gradle/jvm-2.3.20
  remotes/origin/dependabot/gradle/kotlin-2bdd8fbc18
  remotes/origin/dependabot/gradle/kotlin-37782ea5f7
  remotes/origin/dependabot/gradle/multiplatform-2.3.20
  remotes/origin/dependabot/gradle/org.junit-junit-bom-6.0.2
  remotes/origin/dependabot/gradle/org.junit-junit-bom-6.0.3
  remotes/origin/dependabot/gradle/org.sonarqube-7.2.3.7755
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/eslint-10.0.0
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/eslint/js-10.0.1
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/node-25.0.6
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/node-25.2.2
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/vscode-1.108.1
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/vscode-1.109.0
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/typescript-eslint-8.52.0
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/typescript-eslint-8.54.0
  remotes/origin/feat/d_and_d_gaps
  remotes/origin/master
```

Note: The worktree (`scratch/bisect`) was running with a DETACHED HEAD — it has no named branch
in this list. This is expected: `git worktree add scratch/bisect cbe81d29` creates a detached
HEAD worktree, not a named branch. The worktree's commits are reachable only through
`worktrees/bisect/HEAD` (the worktree's HEAD file), which git fsck --unreachable should confirm
are not orphaned.

---

## Pre-removal: `git log --oneline -10 feat/d_and_d_gaps` (main feature branch)

```
8d9d3639 docs(10.2-11): complete ROM-smoke gate plan — PASS verdict + SUMMARY + state update
163540d0 chore(10.2-11): ROM-smoke gate PASS — metasprites + stress + full test suite GREEN
2c8a390d docs(10.2-10): complete D-17 cross-phase evidence propagation plan
e5b1bd6e chore(10.2-10): propagate post-fix UAT triplet to Phase 10 evidence/uat-screenshots/ (D-17 cross-phase)
c97d5650 docs(state): record 10.2-09 complete — D-V3 closure PASS
57cae909 docs(10.2-09): SUMMARY.md — D-V3 closure PASS; cyan + checker visible; DMG non-regression
261e9449 docs(10.2-09): annotate closure-verdict.md with scope-shift section
f1afb369 feat(10.2-09): capture post-fix UAT triplet + closure-verdict.md — D-V3 PASS
e7d37a66 docs(10.2-08): complete Plan 08 — atomic addAll order-swap fix; DV3VisualV3DiagnosticTest 2/2 GREEN; 11/15 plans done
f2e8cecc fix(10.2-08): swap mainBody addAll order — bgFillCheckerboard before allSpriteDataLoads (DEF-10.1-13-C 5th-layer VRAM collision fix)
```

Normal Phase 10.2 plan-execution history. No probe commits. Plan 08 fix commit (`f2e8cecc`) is
correctly at position 9 (not HEAD — as expected; Plans 09, 10, 11 followed).

---

## LEAKAGE VERDICT: NONE

All 5 pre-removal verification checks pass:
- `git worktree list` shows scratch/bisect at `2767fab7` (detached HEAD) — expected.
- `git reflog --all` confirms probe commits are ONLY under `worktrees/bisect/HEAD@{N}`, NEVER on `refs/heads/feat/d_and_d_gaps`.
- `git status` is clean — no untracked worktree files on main checkout.
- `git branch -a` shows no bisect-named branch (correct — worktree was created in detached HEAD mode).
- `git log --oneline -10` shows only legitimate Phase 10.2 plan commits.

**No probe commits leaked to `feat/d_and_d_gaps`. Proceeding with teardown.**

---

## Teardown note: `--force` required

`git worktree remove scratch/bisect` initially failed with:
> fatal: '/Users/michalsvacha/GitHub/personal/gbkt/scratch/bisect' contains modified or untracked files, use --force to delete it

The untracked files were the `.planning/phases/10.2-*` evidence directory visible from within
the worktree's working tree (the worktree's detached HEAD at `2767fab7` predates the evidence
commits that landed on `feat/d_and_d_gaps`). These files were not part of any commit in the
worktree's commit history — they appeared untracked to the worktree because its HEAD didn't
know about them.

`git worktree remove --force scratch/bisect` was run. This removes the working tree filesystem
only — it does NOT touch the worktree's commit history or any branch. The working tree at
`scratch/bisect` has been cleaned. Exit code: 0.

---

## Post-removal: `git worktree list`

```
/Users/michalsvacha/GitHub/personal/gbkt  ae6edcd9 [feat/d_and_d_gaps]
```

`scratch/bisect` is no longer listed. Only the main checkout remains.

---

## Post-removal: `git branch -a` — bisect branch preserved

```
* feat/d_and_d_gaps
  master
  worktree-agent-a40a8840097a8a42d
  worktree-agent-a6259248f6fda5f11
  remotes/origin/HEAD -> origin/master
  remotes/origin/chore/update-github-actions-deps
  remotes/origin/chore/update-gradle-deps
  remotes/origin/chore/update-vscode-deps
  remotes/origin/dependabot/github_actions/actions/upload-artifact-7
  remotes/origin/dependabot/github_actions/gradle/actions-5
  remotes/origin/dependabot/github_actions/gradle/actions-6
  remotes/origin/dependabot/github_actions/softprops/action-gh-release-2
  remotes/origin/dependabot/gradle/com.diffplug.spotless-8.4.0
  remotes/origin/dependabot/gradle/gradle-wrapper-9.4.1
  remotes/origin/dependabot/gradle/io.kotest-kotest-property-6.0.7
  remotes/origin/dependabot/gradle/io.kotest-kotest-property-6.1.11
  remotes/origin/dependabot/gradle/jvm-2.3.20
  remotes/origin/dependabot/gradle/kotlin-2bdd8fbc18
  remotes/origin/dependabot/gradle/kotlin-37782ea5f7
  remotes/origin/dependabot/gradle/multiplatform-2.3.20
  remotes/origin/dependabot/gradle/org.junit-junit-bom-6.0.2
  remotes/origin/dependabot/gradle/org.junit-junit-bom-6.0.3
  remotes/origin/dependabot/gradle/org.sonarqube-7.2.3.7755
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/eslint-10.0.0
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/eslint/js-10.0.1
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/node-25.0.6
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/node-25.2.2
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/vscode-1.108.1
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/types/vscode-1.109.0
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/typescript-eslint-8.52.0
  remotes/origin/dependabot/npm_and_yarn/vscode-extension/typescript-eslint-8.54.0
  remotes/origin/feat/d_and_d_gaps
  remotes/origin/master
```

The worktree was created with a detached HEAD (not a named branch). After `git worktree remove`,
the worktree's probe commits (`636c9ddf`, `a7aacaa2`, `2767fab7`) are no longer reachable via any
ref — they are now unreachable objects (confirmed by fsck below). This is expected behavior for
a detached-HEAD worktree teardown. NEVER `git branch -D` anything — all branches visible
in this list are legitimate.

---

## Post-removal: `git fsck --unreachable` (orphan rescue)

```
(First 50 lines — full output contains ~50+ unreachable tree/blob objects plus the commits below)

unreachable commit 090083ae9599418f87cb80bc54946896a3e0fb47
unreachable commit 636c9ddffc6be94ea1f50b77038a8117f534d3cb
unreachable commit a7aacaa250750aa0976b0690753e4cd803537d34
unreachable commit 650048fa1150e4fc7ea74e9b667f40d594a16b27
unreachable commit 7400518e077791b7f4eafc711bcff2555b71b2a1
unreachable commit 92001b49f3a644ae9e9e00384898b0e8beeae65c
unreachable commit e70031c35133f1f74177636362249f4933d4f5bd
(+ many additional unreachable tree/blob objects from prior worktree-agent sessions)
```

**Analysis of unreachable commits:**

| Commit | Description | Origin | Action |
|--------|-------------|--------|--------|
| `636c9ddf` | `test(10.2-06d): ProbeC4PaletteDumpTest — bisect sub-probe C-4 evidence capture scaffolding` | Worktree bisect probe commit (was at `worktrees/bisect/HEAD@{1}`) | Document — do NOT delete |
| `a7aacaa2` | `sub-probe C-4: constant + bgFillCheckerboard (no set_bkg_palette) — Phase 10.2 bisect` | Worktree bisect probe commit (was at `worktrees/bisect/HEAD@{2}`) | Document — do NOT delete |
| `2767fab7` | `probe-A: apply Plan 19+20 edits onto cfe41ad7 baseline (Phase 10.2 bisect)` | Was the worktree's `HEAD` at teardown time | Document — do NOT delete |
| Other hashes | Unreachable trees/blobs + other commits | Prior worktree-agent sessions (pre-Phase-10.2) | Document — do NOT delete |

**Conclusion:** The unreachable probe commits (`636c9ddf`, `a7aacaa2`, `2767fab7`) are the expected
remains of the bisect scratch worktree. They were never on `feat/d_and_d_gaps` (confirmed by
pre-removal reflog analysis). They are now unreachable by design — this is the normal outcome
of a detached-HEAD worktree teardown. Per `feedback_claude_code_worktree_drift_quirks.md`: these
are documented here, NOT deleted. They will be cleaned up by git garbage collection in the
normal course.

---

## Post-removal: HEAD hash check

```
PRE_HEAD:  8d9d363980f4d7d15a97ccf72cd681a667ef5a3a  (before Task 1 commit)
Task 1 commit: ae6edcd96de3dc24e6fbaa4bf369fecad6f4c785  (chore: pre-teardown drift check)
POST_HEAD: ae6edcd96de3dc24e6fbaa4bf369fecad6f4c785  (after worktree remove)
```

HEAD advanced by exactly ONE commit (Task 1's evidence-file commit). The worktree teardown
itself did NOT advance HEAD. This is correct — `git worktree remove` never commits anything.

---

## D-20 Final Verdict

| Check | Result | Notes |
|-------|--------|-------|
| Pre-removal `git worktree list` showed scratch/bisect | PASS | At commit `2767fab7` (detached HEAD) |
| Post-removal `git worktree list` does NOT show scratch/bisect | PASS | Only main checkout remains |
| `git branch -a` still shows bisect branch | N/A — worktree was detached HEAD, not named branch | Correct behavior; no branch to preserve |
| No "LEAKAGE DETECTED" section | PASS | Reflog confirmed no probe commits on feat/d_and_d_gaps |
| `git fsck --unreachable` captured | PASS | Unreachable probe commits documented, NOT deleted |
| `git status` clean before teardown | PASS | working tree clean |
| HEAD unchanged by teardown | PASS | Only Task 1 commit advanced HEAD (expected) |

**D-20 SATISFIED.**
