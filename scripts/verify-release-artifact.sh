#!/usr/bin/env bash
set -Eeuo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <artifact-path> [checksum-file] [github-repo]" >&2
  exit 2
fi

ARTIFACT_PATH="$1"
CHECKSUM_FILE="${2:-${ARTIFACT_PATH}.sha256}"
GITHUB_REPO="${3:-${GITHUB_REPOSITORY:-BaoHuy-Dev/laundry-locker-microservices}}"

if [ ! -f "$ARTIFACT_PATH" ]; then
  echo "Artifact not found: $ARTIFACT_PATH" >&2
  exit 1
fi

if [ ! -f "$CHECKSUM_FILE" ]; then
  echo "Checksum file not found: $CHECKSUM_FILE" >&2
  exit 1
fi

command -v sha256sum >/dev/null
(
  cd "$(dirname "$CHECKSUM_FILE")"
  sha256sum -c "$(basename "$CHECKSUM_FILE")" --ignore-missing
)

if command -v gh >/dev/null; then
  gh attestation verify "$ARTIFACT_PATH" --repo "$GITHUB_REPO"
else
  echo "GitHub CLI not found; skipped artifact attestation verification." >&2
fi
