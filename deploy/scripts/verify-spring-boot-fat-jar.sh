#!/usr/bin/env bash
# Fail if JAR is not a runnable Spring Boot repackaged fat jar.
# Usage: verify-spring-boot-fat-jar.sh /path/to/app.jar
set -euo pipefail

jar_path="${1:?JAR path required}"
label="${2:-$(basename "$jar_path")}"

if [ ! -f "$jar_path" ]; then
  echo "ERROR: ${label}: file not found: ${jar_path}" >&2
  exit 1
fi

size=$(stat -c '%s' "$jar_path" 2>/dev/null || stat -f '%z' "$jar_path")
if [ "$size" -lt 5000000 ]; then
  echo "ERROR: ${label}: too small (${size} bytes) — likely thin/corrupt JAR" >&2
  exit 1
fi

if ! unzip -t -q "$jar_path" 2>/dev/null; then
  echo "ERROR: ${label}: zip integrity check failed" >&2
  exit 1
fi

has_boot_lib=0
if command -v jar >/dev/null 2>&1 && jar tf "$jar_path" 2>/dev/null | grep -qE 'BOOT-INF/lib/spring-core.*\.jar'; then
  has_boot_lib=1
elif unzip -l "$jar_path" 2>/dev/null | grep -qE 'BOOT-INF/lib/spring-core'; then
  has_boot_lib=1
fi
if [ "$has_boot_lib" -ne 1 ]; then
  echo "ERROR: ${label}: missing BOOT-INF/lib (not a Spring Boot fat JAR)" >&2
  echo "       Rebuild with: ./mvnw -Pprod -DskipTests clean package" >&2
  exit 1
fi

main_class=$(unzip -p "$jar_path" META-INF/MANIFEST.MF 2>/dev/null | awk -F': ' '/^Main-Class:/{print $2; exit}' | tr -d '\r')
if [ "$main_class" != "org.springframework.boot.loader.launch.JarLauncher" ]; then
  echo "ERROR: ${label}: Main-Class is '${main_class:-<missing>}' (expected JarLauncher)" >&2
  echo "       Do not run the *.jar.original thin artifact with java -jar." >&2
  exit 1
fi

echo "OK  ${label}: Spring Boot fat JAR (${size} bytes, Main-Class=${main_class})"
