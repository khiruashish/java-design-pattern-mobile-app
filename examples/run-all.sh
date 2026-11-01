#!/usr/bin/env bash
# Runs every *Demo.java example and compares its output with the matching
# *.expected.txt file (the same output shown on the docs page).
# If JUNIT_JAR points at junit-platform-console-standalone, *Test.java files run too.
set -euo pipefail
cd "$(dirname "$0")"
failed=0
while IFS= read -r demo; do
  expected="${demo%.java}.expected.txt"
  [[ -f "$expected" ]] || { echo "MISSING $expected"; failed=1; continue; }
  # Demos that measure real time say "run-all: retries=N" and may be retried on a busy machine.
  tries=$(grep -oE 'run-all: retries=[0-9]+' "$demo" | grep -oE '[0-9]+$' || echo 1)
  ok=0
  for ((t = 1; t <= tries; t++)); do
    if diff -q "$expected" <(java "$demo" 2>/dev/null) >/dev/null; then ok=1; break; fi
  done
  if [[ $ok == 1 ]]; then
    echo "PASS $demo"
  else
    diff -u "$expected" <(java "$demo" 2>/dev/null) || true
    echo "FAIL $demo"; failed=1
  fi
done < <(find . -name '*Demo.java' -not -path '*/src/*' -not -path '*/target/*' | sort)

if [[ -n "${JUNIT_JAR:-}" ]]; then
  while IFS= read -r dir; do
    out="$(mktemp -d)"
    javac -d "$out" -cp "$JUNIT_JAR" "$dir"/*.java
    java -jar "$JUNIT_JAR" execute -cp "$out" --scan-classpath --disable-banner --details=summary || failed=1
  done < <(find . -name '*Test.java' -not -path '*/src/*' -not -path '*/target/*' -exec dirname {} \; | sort -u)
fi
if command -v mvn >/dev/null 2>&1; then
  while IFS= read -r pom; do
    echo "MAVEN $(dirname "$pom")"
    (cd "$(dirname "$pom")" && mvn -B -q -ntp verify) || failed=1
  done < <(find . -name pom.xml -not -path '*/target/*' | sort)
else
  echo "SKIP Maven projects (mvn not installed)"
fi
exit "$failed"
