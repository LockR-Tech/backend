#!/usr/bin/env bash
# Chạy trên VM production, do workflow .github/workflows/vm-ops.yml gọi qua
#   ssh <vm> 'bash -s' -- <action> <service> < scripts/vm-ops.sh
# để người không có SSH vẫn xem được trạng thái / lỗi và khởi động lại một service
# từ tab Actions.
#
#   status    trạng thái mọi container + RAM của VM
#   diagnose  trạng thái một service + 60 dòng lỗi gần nhất (đã che dữ liệu cá nhân)
#   restart   khởi động lại một service, chờ nó đăng ký lại Eureka
#
# Log Actions có thể bị người khác đọc nên KHÔNG in log thô: chỉ in dòng lỗi, và che
# email, chuỗi ≥ 6 chữ số (SĐT, PIN, mã), JWT/Bearer token.
set -Eeuo pipefail

ACTION="${1:-status}"
SERVICE="${2:-order-service}"
APP_DIR="${APP_DIR:-/opt/laundry-locker-microservices}"
EUREKA_WAIT_SECONDS="${EUREKA_WAIT_SECONDS:-420}"
SERVICES="discovery-server api-gateway auth-service user-service order-service locker-service payment-service notification-service iot-service store-service loyalty-service assistant-service"

case " $SERVICES " in
  *" $SERVICE "*) ;;
  *) echo "Service không hợp lệ: $SERVICE" >&2; exit 2 ;;
esac

cd "$APP_DIR"

mask() {
  sed -E \
    -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/<email>/g' \
    -e 's/(Bearer +|eyJ)[A-Za-z0-9._-]+/<token>/g' \
    -e 's/[0-9]{6,}/<số>/g'
}

state() {
  local id
  id="$(docker compose ps -a -q "$1" 2>/dev/null || true)"
  if [ -z "$id" ]; then
    echo "$1: KHÔNG có container"
    return
  fi
  docker inspect "$id" --format \
    "$1: {{.State.Status}} · OOMKilled={{.State.OOMKilled}} · ExitCode={{.State.ExitCode}} · Restarts={{.RestartCount}} · StartedAt={{.State.StartedAt}}"
}

errors() {
  docker compose logs --no-color --tail=3000 "$1" 2>&1 \
    | grep -E "ERROR|Exception|OutOfMemory|Killed|APPLICATION FAILED|Caused by|Started [A-Za-z]+ in " \
    | tail -n 60 \
    | mask || true
}

registered() {
  local app
  app="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"
  curl -fsS --max-time 15 http://127.0.0.1:8761/eureka/apps 2>/dev/null | grep -q "<name>${app}</name>"
}

case "$ACTION" in
  status)
    docker compose ps -a --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'
    echo
    free -h
    echo
    for s in $SERVICES; do state "$s"; done
    ;;
  diagnose)
    state "$SERVICE"
    echo "--- 60 dòng lỗi gần nhất của $SERVICE (đã che email / số / token)"
    errors "$SERVICE"
    ;;
  restart)
    echo "Trước: $(state "$SERVICE")"
    docker compose restart "$SERVICE"
    if [ "$SERVICE" = "discovery-server" ] || [ "$SERVICE" = "api-gateway" ]; then
      sleep 30
    else
      deadline=$((SECONDS + EUREKA_WAIT_SECONDS))
      until registered "$SERVICE"; do
        if [ "$SECONDS" -ge "$deadline" ]; then
          echo "Sau ${EUREKA_WAIT_SECONDS}s $SERVICE vẫn chưa đăng ký Eureka." >&2
          state "$SERVICE"
          errors "$SERVICE"
          exit 1
        fi
        sleep 15
      done
      echo "$SERVICE đã đăng ký lại Eureka."
    fi
    echo "Sau: $(state "$SERVICE")"
    ;;
  *)
    echo "Action không hợp lệ: $ACTION (status | diagnose | restart)" >&2
    exit 2
    ;;
esac
