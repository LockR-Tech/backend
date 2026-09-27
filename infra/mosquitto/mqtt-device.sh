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
# Pi: tên đăng nhập là MAC viết liền (2CCF67DBC5C3 — file mật khẩu Mosquitto cấm dấu `:`),
# được đọc/ghi namespace cấp phát iot/<MAC có dấu :>/… và tủ đã chỉ định. Tên khác MAC (giả lập)
# chỉ có quyền tủ. Tủ '*' = mọi tủ (chỉ dùng thử).
# Mật khẩu chỉ in ra MỘT lần — ghi ngay vào ~/iot/.env của Pi (MQTT_USERNAME, MQTT_PASSWORD).
# Gán Pi sang tủ khác trên admin web thì phải `move` ở đây cho khớp.
set -euo pipefail

DIR="${MQTT_DEVICES_DIR:-/etc/lockr/mosquitto}"
IMAGE="${MQTT_IMAGE:-eclipse-mosquitto:2}"
CONTAINER="${MQTT_CONTAINER:-ll-ms-mosquitto}"
LIST="$DIR/devices.list"   # mỗi dòng: <username> <lockerId|*> <MAC có dấu : hoặc ->

die() { echo "Lỗi: $*" >&2; exit 1; }

# In "<username> <mac>" cho một tên thiết bị: MAC (có hoặc không dấu :) ⇒ username viết liền.
identity() {
  local raw="${1^^}"
  local hex="${raw//[:-]/}"
  if [[ "$hex" =~ ^[0-9A-F]{12}$ ]]; then
    echo "$hex ${hex:0:2}:${hex:2:2}:${hex:4:2}:${hex:6:2}:${hex:8:2}:${hex:10:2}"
  elif [[ "$1" =~ ^[A-Za-z0-9_-]{3,64}$ ]] && [ "$1" != "iot-service" ]; then
    echo "$1 -"
  else
    die "tên thiết bị '$1' không hợp lệ (MAC dạng 2C:CF:67:DB:C5:C3, hoặc tên chữ-số như sim-demo)"
  fi
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
      while read -r name locker mac; do
        [ -n "${name:-}" ] || continue
        local target="$locker"
        [ "$locker" = "*" ] && target="+"
        echo ""
        echo "user $name"
        echo "topic read cabinet/$target/command/#"
        echo "topic write cabinet/$target/command/+/result"
        echo "topic write cabinet/$target/locker/+/status"
        echo "topic write cabinet/$target/heartbeat"
        if [ -n "${mac:-}" ] && [ "$mac" != "-" ]; then
          echo "topic read iot/$mac/command/#"
          echo "topic read iot/$mac/discovery/start"
          echo "topic write iot/$mac/discovery/result"
          echo "topic write iot/$mac/setup/#"
        fi
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
  touch "$DIR/passwd"; chmod 600 "$DIR/passwd"
  passwd_tool -b /d/passwd "$name" "$password"
  echo "MQTT_USERNAME=$name"
  echo "MQTT_PASSWORD=$password"
  echo "(mật khẩu chỉ hiện một lần — chép hai dòng trên vào ~/iot/.env của Pi)"
}

cmd="${1:-}"
[ "$(id -u)" -eq 0 ] || die "chạy bằng sudo"
install -d -m 700 "$DIR"

case "$cmd" in
  add)
    [ $# -eq 3 ] || die "cú pháp: add <MAC|tên> <lockerId|*>"
    ident="$(identity "$2")"; read -r name mac <<< "$ident"
    check_locker "$3"
    exists "$name" && die "$name đã có — dùng move, reset hoặc remove"
    set_password "$name"
    echo "$name $3 $mac" >> "$LIST"; chmod 600 "$LIST"
    render_acl; reload_broker
    ;;
  move)
    [ $# -eq 3 ] || die "cú pháp: move <MAC|tên> <lockerId|*>"
    ident="$(identity "$2")"; read -r name mac <<< "$ident"
    check_locker "$3"
    exists "$name" || die "$name chưa có"
    awk -v n="$name" -v l="$3" '$1 == n {$2 = l} {print}' "$LIST" > "$LIST.tmp" && mv "$LIST.tmp" "$LIST"
    chmod 600 "$LIST"
    render_acl; reload_broker
    ;;
  reset)
    [ $# -eq 2 ] || die "cú pháp: reset <MAC|tên>"
    ident="$(identity "$2")"; read -r name mac <<< "$ident"
    exists "$name" || die "$name chưa có"
    set_password "$name"
    reload_broker
    ;;
  remove)
    [ $# -eq 2 ] || die "cú pháp: remove <MAC|tên>"
    ident="$(identity "$2")"; read -r name mac <<< "$ident"
    exists "$name" || die "$name chưa có"
    passwd_tool -D /d/passwd "$name"
    awk -v n="$name" '$1 != n' "$LIST" > "$LIST.tmp" && mv "$LIST.tmp" "$LIST"
    chmod 600 "$LIST"
    render_acl; reload_broker
    ;;
  list)
    if [ -s "$LIST" ]; then
      printf '%-16s %-6s %s\n' "USERNAME" "TỦ" "MAC"
      awk '{printf "%-16s %-6s %s\n", $1, $2, $3}' "$LIST"
    else
      echo "Chưa có thiết bị nào."
    fi
    ;;
  *)
    sed -n '2,20p' "$0"
    exit 1
    ;;
esac
