#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/laundry-locker-microservices}"
ARCHIVE="${ARCHIVE:-/tmp/laundry-locker-microservices.tar.gz}"
CHECKSUM_FILE="${CHECKSUM_FILE:-${ARCHIVE}.sha256}"
NEW_DIR="${APP_DIR}.new"
BACKUP_DIR="${APP_DIR}.previous"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-720}"

# Publish the API gateway on host port 8080 on the server. Nginx terminates TLS
# on :443 and proxies to 127.0.0.1:8080, so the gateway must sit on 8080 (the
# Azure NSG only opens 22 + 80 + 443 inbound; 8080 stays VM-internal).
# docker-compose.yml defaults this to 18080 for local dev (where host 8080 is
# usually occupied); the deploy path overrides it to 8080 here so the gateway is
# reachable after every deploy without manual env edits (which a deploy would
# wipe). The health check below also probes :8080.
export API_GATEWAY_PORT="${API_GATEWAY_PORT:-8080}"

rollback() {
  echo "Rolling back to previous release..."
  if [ -d "$BACKUP_DIR" ]; then
    rm -rf "$APP_DIR"
    mv "$BACKUP_DIR" "$APP_DIR"
    cd "$APP_DIR"
    docker compose up -d --build --remove-orphans || true
  fi
}

trap 'echo "Deploy failed."; rollback' ERR

if [ ! -s "$ARCHIVE" ]; then
  echo "Artifact not found: $ARCHIVE" >&2
  exit 1
fi

command -v docker >/dev/null
docker compose version >/dev/null
command -v curl >/dev/null

if [ -s "$CHECKSUM_FILE" ]; then
  command -v sha256sum >/dev/null
  # `sha256sum -c` giải tên file bên trong checksum theo THƯ MỤC HIỆN TẠI. Script
  # này chạy qua `ssh 'bash -s'` nên cwd là HOME của deploy user, trong khi artifact
  # nằm ở /tmp -> kiểm tra phải chạy trong đúng thư mục chứa artifact, nếu không
  # luôn báo "FAILED open or read" và rollback oan.
  ( cd "$(dirname "$ARCHIVE")" && sha256sum -c "$(basename "$CHECKSUM_FILE")" )
fi

wait_for_http() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))

  echo "Waiting for ${name}: ${url}"
  until curl -fsS --max-time 10 "$url" >/dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for ${name}" >&2
      return 1
    fi
    sleep 10
  done
}

wait_for_eureka_apps() {
  local apps=("$@")
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  local registry

  echo "Waiting for Eureka registrations: ${apps[*]}"
  while true; do
    registry="$(curl -fsS --max-time 15 http://127.0.0.1:8761/eureka/apps || true)"
    local missing=()

    for app in "${apps[@]}"; do
      if ! grep -q "<name>${app}</name>" <<<"$registry"; then
        missing+=("$app")
      fi
    done

    if [ "${#missing[@]}" -eq 0 ]; then
      return 0
    fi

    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for Eureka registrations. Missing: ${missing[*]}" >&2
      return 1
    fi

    echo "Still waiting for: ${missing[*]}"
    sleep 15
  done
}

DEFAULT_EUREKA_APPS=(
  API-GATEWAY
  AUTH-SERVICE
  USER-SERVICE
  ORDER-SERVICE
  LOCKER-SERVICE
  PAYMENT-SERVICE
  NOTIFICATION-SERVICE
  IOT-SERVICE
  STORE-SERVICE
  LOYALTY-SERVICE
)

if [ -n "${EUREKA_EXPECTED_APPS:-}" ]; then
  read -r -a EXPECTED_EUREKA_APPS <<<"$EUREKA_EXPECTED_APPS"
else
  EXPECTED_EUREKA_APPS=("${DEFAULT_EUREKA_APPS[@]}")
fi

rm -rf "$NEW_DIR"
mkdir -p "$NEW_DIR"
tar -xzf "$ARCHIVE" -C "$NEW_DIR"

# Preserve operator-managed .env (SMTP creds, API_GATEWAY_PORT, etc.) across deploys.
# The artifact tarball does not contain .env, so without this each deploy would reset
# the operator's runtime config back to compose defaults.
if [ -f "$APP_DIR/.env" ]; then
  cp "$APP_DIR/.env" "$NEW_DIR/.env"
fi

cd "$NEW_DIR"
docker compose config >/tmp/laundry-locker-compose-check.yml

rm -rf "$BACKUP_DIR"
if [ -d "$APP_DIR" ]; then
  mv "$APP_DIR" "$BACKUP_DIR"
fi
mv "$NEW_DIR" "$APP_DIR"

cd "$APP_DIR"
docker compose up -d --build --remove-orphans
docker compose ps

wait_for_http "discovery-server" "http://127.0.0.1:8761/actuator/health"
wait_for_http "api-gateway" "http://127.0.0.1:8080/actuator/health"
wait_for_eureka_apps "${EXPECTED_EUREKA_APPS[@]}"

docker compose ps

rm -f "$ARCHIVE" "$CHECKSUM_FILE"
trap - ERR

echo "Deploy completed."
