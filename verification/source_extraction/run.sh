#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/source-extraction
mkdir -p "$out"
"${JAVAC:-javac}" -d "$out" verification/source_extraction/{ParsedJavaSource,ParsedJavaSourceCheck}.java
"${JAVA:-java}" -cp "$out" ParsedJavaSourceCheck "$out/Example.java"
