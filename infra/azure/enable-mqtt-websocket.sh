#!/usr/bin/env bash
# Mở wss://$API_DOMAIN/mqtt cho tủ: Nginx (chứng chỉ certbot sẵn có) → Mosquitto 127.0.0.1:9001.
# SEC-04, ADR-0008 — docs/01-overview/mqtt-contract.md § 6. Chạy một lần trên VM bằng sudo,
# SAU khi đã có TLS (certbot --nginx). Chạy lại nhiều lần không sao.
set -euo pipefail

API_DOMAIN="${API_DOMAIN:-api.locker-drone.tech}"
MQTT_WS_PORT="${MQTT_WS_PORT:-9001}"
SITE="/etc/nginx/sites-available/${API_DOMAIN}"
SNIPPET="/etc/nginx/snippets/lockr-mqtt.conf"
INCLUDE="include ${SNIPPET};"

[ "$(id -u)" -eq 0 ] || { echo "Chạy bằng sudo." >&2; exit 1; }
[ -f "$SITE" ] || { echo "Không thấy $SITE — chạy infra/azure/bootstrap-vm.sh trước." >&2; exit 1; }
grep -Eq 'listen[[:space:]]+443[[:space:]]+ssl' "$SITE" \
  || { echo "Vhost chưa có TLS — chạy: sudo certbot --nginx -d ${API_DOMAIN}" >&2; exit 1; }

install -d /etc/nginx/snippets
cat > "$SNIPPET" <<EOF
# Tủ (Pi) nối MQTT qua WebSocket. TLS ở server block 443 do certbot quản lý.
location = /mqtt {
    proxy_pass http://127.0.0.1:${MQTT_WS_PORT};
    proxy_http_version 1.1;
    proxy_set_header Upgrade \$http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host \$host;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    # Kết nối MQTT sống lâu; Pi gửi keepalive mỗi 60 s.
    proxy_read_timeout 1h;
    proxy_send_timeout 1h;
}
EOF

if ! grep -qF "$INCLUDE" "$SITE"; then
  BACKUP="${SITE}.bak-$(date +%Y%m%d%H%M%S)"
  cp "$SITE" "$BACKUP"
  # Chèn ngay sau dòng `listen 443 ssl` đầu tiên — nằm trong server block HTTPS của certbot.
  awk -v inc="    ${INCLUDE}" '
    !done && $0 ~ /listen[[:space:]]+443[[:space:]]+ssl/ { print; print inc; done = 1; next }
    { print }' "$BACKUP" > "$SITE"
  if ! grep -qF "$INCLUDE" "$SITE" || ! nginx -t; then
    cp "$BACKUP" "$SITE"
    echo "Không chèn được cấu hình — đã trả lại $SITE như cũ." >&2
    exit 1
  fi
  echo "Đã thêm location /mqtt vào $SITE (bản cũ: $BACKUP)."
fi

nginx -t
systemctl reload nginx
echo "Xong: wss://${API_DOMAIN}/mqtt → 127.0.0.1:${MQTT_WS_PORT}"
