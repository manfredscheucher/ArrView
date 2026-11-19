#!/bin/sh
# Probe (needs tutte_anderson.patch applied to core/Tutte.kt): compiles core + the bundled examples + LayoutProbe.kt with kotlinc (no Gradle) and runs it.
set -e
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
OUT=${TMPDIR:-/tmp}/arrview-layout-probe
mkdir -p "$OUT"
kotlinc -nowarn "$ROOT"/composeApp/src/commonMain/kotlin/org/bytefred/arrview/core/*.kt \
  "$ROOT"/composeApp/build/generated/examples/GeneratedExamples.kt \
  "$ROOT"/scripts/probe/layout_accel/*.kt -d "$OUT/probe.jar" 2>&1 | grep -v "^warning" || true
cd "$(dirname "$0")" && kotlin -cp "$OUT/probe.jar" org.bytefred.arrview.core.LayoutProbeKt "$@"
