#!/usr/bin/env bash
# One-time host-only Android JSON implementation; never packaged in the APK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$ROOT/verification/build/deps"
NAME=android-json-0.0.20131108.vaadin1.jar
DIGEST=dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79
mkdir -p "$DIR"
if [[ ! -f "$DIR/$NAME" ]]; then
 curl --fail --location --silent --show-error "https://repo.maven.apache.org/maven2/com/vaadin/external/google/android-json/0.0.20131108.vaadin1/$NAME" -o "$DIR/$NAME.download"
 echo "$DIGEST  $DIR/$NAME.download" | sha256sum --check --status
 mv "$DIR/$NAME.download" "$DIR/$NAME"
fi
echo "$DIGEST  $DIR/$NAME" | sha256sum --check
