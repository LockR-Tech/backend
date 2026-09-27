#!/bin/sh
# Sinh file mật khẩu + ACL cho Mosquitto rồi chạy broker — SEC-04, ADR-0008.
#   - tài khoản iot-service: mật khẩu lấy từ MQTT_IOT_SERVICE_PASSWORD (.env của VM)
#   - tài khoản từng Pi: /mosquitto/devices/passwd + devices.acl (mqtt-device.sh, ngoài git)
set -eu

RUN=/mosquitto/run
DEVICES=/mosquitto/devices
CONF=/mosquitto/lockr

if [ -z "${MQTT_IOT_SERVICE_PASSWORD:-}" ]; then
  echo "MQTT_IOT_SERVICE_PASSWORD chưa đặt trong .env — broker không khởi động." >&2
  exit 1
fi

mkdir -p "$RUN"
: > "$RUN/passwd"
if [ -f "$DEVICES/passwd" ]; then
  grep -v '^iot-service:' "$DEVICES/passwd" > "$RUN/passwd" || true
fi
mosquitto_passwd -b "$RUN/passwd" iot-service "$MQTT_IOT_SERVICE_PASSWORD"

cat "$CONF/acl.base" > "$RUN/acl"
if [ -f "$DEVICES/devices.acl" ]; then
  printf '\n' >> "$RUN/acl"
  cat "$DEVICES/devices.acl" >> "$RUN/acl"
fi

chown -R mosquitto:mosquitto "$RUN"
chmod 0700 "$RUN"
chmod 0600 "$RUN/passwd" "$RUN/acl"

echo "Mosquitto: $(grep -c . "$RUN/passwd") tài khoản (kể cả iot-service)."
exec /usr/sbin/mosquitto -c "$CONF/mosquitto.conf"
