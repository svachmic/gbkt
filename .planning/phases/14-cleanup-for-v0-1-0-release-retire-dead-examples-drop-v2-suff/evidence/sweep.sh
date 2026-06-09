#!/usr/bin/env bash
# Phase 14 deterministic verification sweep.
# Run at any commit; emits a stable, parseable report to stdout.
# Differential usage:
#   git checkout f92efec7 && bash <thispath> > /tmp/sweep-pre.txt 2>/dev/null
#   git checkout feat/d_and_d_gaps && bash <thispath> > /tmp/sweep-post.txt 2>/dev/null
#   diff /tmp/sweep-pre.txt /tmp/sweep-post.txt
# Intended-change lines (V2_COUNT, SETTINGS_*, *_TRACKED, RPGREGISTRY_CLEAR) SHOULD differ.
# Preservation lines (COMPILE_EXIT, BUILDROM_*, FAILING_TESTS block) MUST match.
set -o pipefail
ROOT="$(git rev-parse --show-toplevel)"; cd "$ROOT"
# Serialized + larger Kotlin daemon heap: avoids parallel-compile OOM so gate EXITs are
# deterministic (memory-flaky parallel compile is an environment artifact, not a code signal).
JVM='-Pkotlin.daemon.jvmargs=-Xmx4g --no-parallel --max-workers=2'
KEEP="pong breakout simple-physics metasprites metasprites-stress banks platformer-template"
./gradlew --stop >/dev/null 2>&1
echo "=== PHASE-14 SWEEP @ $(git rev-parse --short HEAD) ==="

# --- Intended-change gates (deterministic greps; SHOULD differ pre vs post) ---
echo "V2_COUNT=$(grep -rE '[A-Za-z_]*V2\b' --include='*.kt' . --exclude-dir=build --exclude-dir=.git --exclude-dir=.claude 2>/dev/null | grep -cv '\.planning/')"
echo "SETTINGS_EXAMPLE_INCLUDES=$(grep -c 'include("gbkt-examples' settings.gradle.kts)"
echo "RACER_DIR=$([ -d gbkt-examples/racer ] && echo present || echo gone)"
echo "LABYRINTH_TRACKED=$(git ls-files | grep -cE '^LabyrinthOfTheDragon')"
echo "ARCHIVE_TRACKED=$(git ls-files | grep -c 'gbkt-examples/.archive/')"
echo "RPGREGISTRY_CLEAR=$(grep -rEl 'fun clear\(\)' gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt 2>/dev/null | grep -c . )"
echo "VSTAR_FILES=$(find . -name '*V2.kt' -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.claude/*' 2>/dev/null | grep -c .)"
echo "CI_BAD_REFS=$(grep -cE 'gbkt-examples:explorer|gbkt-examples:racer|buildRom' .github/workflows/kotlin.yml 2>/dev/null)"
echo "VERSION=$(grep gbktVersion gradle.properties 2>/dev/null | tr -d ' ')"

# --- Preservation gate 1: whole-tree compile (MUST be 0 both) ---
./gradlew compileKotlin compileTestKotlin $JVM --console=plain >/tmp/sweep-compile.log 2>&1
echo "COMPILE_EXIT=$?"

# --- Preservation gate 2: clean buildRom per KEEP example (MUST be 0 both) ---
# single chained invocation (no parallel clean)
BR_ARGS="clean"; for e in $KEEP; do BR_ARGS="$BR_ARGS :gbkt-examples:$e:buildRom"; done
./gradlew $BR_ARGS $JVM --console=plain >/tmp/sweep-buildrom.log 2>&1
echo "BUILDROM_ALL_EXIT=$?"
for e in $KEEP; do echo "ROM_$e=$([ -f gbkt-examples/$e/build/gbkt/output/$e.gb ] && echo ok || echo MISSING)"; done

# --- Preservation gate 3: failing-test SET (MUST be identical pre vs post) ---
# run full unit+example suite and plugin integration suite, collect failures tree-wide
./gradlew test --continue $JVM --console=plain >/tmp/sweep-test.log 2>&1; echo "TEST_TASK_EXIT=$?"
./gradlew pluginTest --continue $JVM --console=plain >/tmp/sweep-plugintest.log 2>&1; echo "PLUGINTEST_TASK_EXIT=$?"
echo "--- FAILING_TESTS (sorted classname:failures) ---"
find . -path '*/build/test-results/*/TEST-*.xml' -not -path '*/.git/*' 2>/dev/null | while read -r x; do
  fc=$(grep -oE 'failures="[0-9]+"' "$x" | head -1 | grep -oE '[0-9]+')
  ec=$(grep -oE 'errors="[0-9]+"' "$x" | head -1 | grep -oE '[0-9]+')
  cls=$(basename "$x" .xml | sed 's/^TEST-//')
  tot=$(( ${fc:-0} + ${ec:-0} ))
  [ "$tot" -gt 0 ] && echo "$cls:$tot"
done | sort -u
echo "=== END SWEEP ==="
