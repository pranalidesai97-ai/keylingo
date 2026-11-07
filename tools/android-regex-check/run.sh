#!/usr/bin/env bash
# Checks every production regex against ICU4C (Android's regex engine).
# Needs g++ and ICU development headers (Debian/Ubuntu: apt-get install libicu-dev).
#   1. ./gradlew :app:testDebugUnitTest --tests '*AndroidRegexCompatibilityTest*'   (writes the cases file)
#   2. tools/android-regex-check/run.sh
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cases="${1:-$root/app/build/android-regex-cases.tsv}"
bin="${TMPDIR:-/tmp}/keylingo_icu_regex_check"
g++ -std=c++17 -O1 -o "$bin" "$here/icu_regex_check.cpp" $(pkg-config --cflags --libs icu-uc icu-i18n)
"$bin" "$cases"
