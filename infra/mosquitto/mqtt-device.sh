#!/usr/bin/env bash
# Cấp / thu hồi tài khoản MQTT cho bộ điều khiển tủ (Pi) trên broker riêng — SEC-04, ADR-0008.
# Chạy trên VM bằng sudo. Dữ liệu nằm NGOÀI thư mục deploy (deploy thay cả thư mục mỗi lần):
#   $MQTT_DEVICES_DIR (mặc định /etc/lockr/mosquitto): passwd (đã băm), devices.list, devices.acl
#
#   sudo infra/mosquitto/mqtt-device.sh add 2C:CF:67:DB:C5:C3 1   # Pi chỉ được làm tủ lockerId 1
#   sudo infra/mosquitto/mqtt-device.sh add sim-demo 1            # giả lập simulate_demo_cabinet.py
#   sudo infra/mosquitto/mqtt-device.sh move 2C:CF:67:DB:C5:C3 2  # Pi chuyển sang tủ 2
#   sudo infra/mosquitto/mqtt-device.sh reset 2C:CF:67:DB:C5:C3   # cấp mật khẩu mới
#   sudo infra/mosquitto/mqtt-device.sh remove 2C:CF:67:DB:C5:C3
#   sudo infra/mosquitto/mqtt-device.sh list
#
# Tủ '*' = được làm mọi tủ (chỉ dùng thử). Mật khẩu chỉ in ra MỘT lần — ghi ngay vào
# ~/iot/.env của Pi (MQTT_USERNAME=<MAC>, MQTT_PASSWORD=<mật khẩu>).
# Gán Pi sang tủ khác trên admin web thì phải `move` ở đây cho khớp.
set -euo pipefail

DIR="${MQTT_DEVICES_DIR:-/etc/lockr/mosquitto}"
IMAGE="${MQTT_IMAGE:-eclipse-mosquitto:2}"
CONTAINER="${MQTT_CONTAINER:-ll-ms-mosquitto}"
LIST="$DIR/devices.list"

die() { echo "Lỗi: $*" >&2; exit 1; }

check_name() {
  [[ "$1" =~ ^[A-Za-z0-9:_-]{3,64}$ ]] || die "tên thiết bị '$1' không hợp lệ (MAC dạng 2C:CF:67:DB:C5:C3)"
  [ "$1" != "iot-service" ] || die "'iot-service' là tài khoản của backend"
}

check_locker() {
  [[ "$1" =~ ^([0-9]+|\*)$ ]] || die "lockerId '$1' phải là số (id tủ trên admin) hoặc '*'"
}

exists() { [ -f "$LIST" ] && awk -v n="$1" '$1 == n {found=1} END {exit !found}' "$LIST"; }

passwd_tool() {
  docker run --rm -v "$DIR:/d" "$IMAGE" mosquitto_passwd "$@"
}

new_password() {
  openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | cut -c1-24
}

render_acl() {
  local tmp
  tmp="$(mktemp)"
  {
    echo "# Sinh bởi mqtt-device.sh — đừng sửa tay. Nguồn: devices.list"
    if [ -f "$LIST" ]; then
      while read -r name locker; do
        [ -n "${name:-}" ] || continue
        local target="$locker"
        [ "$locker" = "*" ] && target="+"
        echo ""
        echo "user $name"
        echo "topic read cabinet/$target/command/#"
        echo "topic write cabinet/$target/command/+/result"
        echo "topic write cabinet/$target/locker/+/status"
        echo "topic write cabinet/$target/heartbeat"
      done < "$LIST"
    fi
  } > "$tmp"
  install -m 600 "$tmp" "$DIR/devices.acl"
  rm -f "$tmp"
}

reload_broker() {
  if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    docker restart "$CONTAINER" >/dev/null
    echo "Đã khởi động lại $CONTAINER (Pi và iot-service tự kết nối lại)."
  else
    echo "Broker $CONTAINER chưa chạy — thay đổi có hiệu lực khi nó khởi động."
  fi
}

set_password() {
  local name="$1" password
  password="$(new_password)"
  touch "$DIR/passwd"
  passwd_tool -b /d/passwd "$name" "$password"
  chmod 600 "$DIR/passwd"
  echo "Tài khoản : $name"
  echo "Mật khẩu  : $password"
  echo "(chỉ hiện một lần — ghi vào ~/iot/.env của Pi: MQTT_USERNAME=$name, MQTT_PASSWORD=...)"
}

cmd="${1:-}"
[ "$(id -u)" -eq 0 ] || die "chạy bằng sudo"
install -d -m 700 "$DIR"

case "$cmd" in
  add)
    [ $# -eq 3 ] || die "cú pháp: add <MAC> <lockerId|*>"
    check_name "$2"; check_locker "$3"
    exists "$2" && die "$2 đã có — dùng move, reset hoặc remove"
    set_password "$2"
    echo "$2 $3" >> "$LIST"; chmod 600 "$LIST"
    render_acl; reload_broker
    ;;
  move)
    [ $# -eq 3 ] || die "cú pháp: move <MAC> <lockerId|*>"
    check_name "$2"; check_locker "$3"
    exists "$2" || die "$2 chưa có"
    awk -v n="$2" -v l="$3" '$1 == n {$2 = l} {print}' "$LIST" > "$LIST.tmp" && mv "$LIST.tmp" "$LIST"
    chmod 600 "$LIST"
    render_acl; reload_broker
    ;;
  reset)
    [ $# -eq 2 ] || die "cú pháp: reset <MAC>"
    exists "$2" || die "$2 chưa có"
    set_password "$2"
    reload_broker
    ;;
  remove)
    [ $# -eq 2 ] || die "cú pháp: remove <MAC>"
    exists "$2" || die "$2 chưa có"
    passwd_tool -D /d/passwd "$2"
    awk -v n="$2" '$1 != n' "$LIST" > "$LIST.tmp" && mv "$LIST.tmp" "$LIST"
    chmod 600 "$LIST"
    render_acl; reload_broker
    ;;
  list)
    if [ -s "$LIST" ]; then
      printf '%-20s %s\n' "THIẾT BỊ" "TỦ"
      awk '{printf "%-20s %s\n", $1, $2}' "$LIST"
    else
      echo "Chưa có thiết bị nào."
    fi
    ;;
  *)
    sed -n '2,17p' "$0"
    exit 1
    ;;
esac
