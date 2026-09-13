#!/usr/bin/env bash
# Cài đặt một lần trên Azure VM (Ubuntu 22.04): Docker, Nginx, Certbot,
# thư mục ứng dụng và file .env vận hành.
#
#   scp infra/azure/bootstrap-vm.sh azureuser@<IP>:/tmp/
#   ssh azureuser@<IP> 'sudo bash /tmp/bootstrap-vm.sh'
#
# Chạy lại được nhiều lần: đã có gì thì bỏ qua, KHÔNG ghi đè .env đang dùng.

set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/laundry-locker-microservices}"
DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-azureuser}}"
API_DOMAIN="${API_DOMAIN:-api.locker-drone.tech}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
[ "$(id -u)" -eq 0 ] || { echo "Chạy bằng sudo."; exit 1; }

say "Cập nhật hệ thống"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg lsb-release ufw

say "Cài Docker Engine + compose plugin"
if ! command -v docker >/dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
usermod -aG docker "$DEPLOY_USER" || true

say "Giới hạn log Docker (tránh đầy đĩa)"
if [ ! -f /etc/docker/daemon.json ]; then
  mkdir -p /etc/docker
  cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
  systemctl restart docker
fi

say "Tạo swap 4GB"
# 12 JVM + Postgres + RabbitMQ ăn gần hết 8GB RAM. Trên server cũ đã từng chạm trần
# lúc build image ngay trên máy; swap giúp deploy không bị OOM kill giữa chừng.
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10 >/dev/null
  grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi
free -h

say "Cài Nginx + Certbot"
apt-get install -y -qq nginx certbot python3-certbot-nginx

say "Tạo thư mục ứng dụng: $APP_DIR"
mkdir -p "$APP_DIR"
chown -R "$DEPLOY_USER":"$DEPLOY_USER" "$APP_DIR"
# Deploy script hoán đổi thư mục theo kiểu atomic: tạo "<APP_DIR>.new", đổi tên
# "<APP_DIR>" thành "<APP_DIR>.previous" rồi đưa .new vào chỗ cũ. Cả ba đều nằm
# TRONG /opt, nên deploy user phải ghi được vào chính /opt — chỉ chown thư mục
# app là chưa đủ (mkdir .new sẽ báo Permission denied).
# Cấp qua nhóm thay vì đổi chủ /opt; setgid để thư mục con kế thừa nhóm.
chgrp "$DEPLOY_USER" /opt
chmod 2775 /opt

say "Tạo file .env vận hành (nếu chưa có)"
# Deploy script giữ nguyên .env qua mỗi lần deploy, nên đây là nơi đặt secret thật.
if [ ! -f "$APP_DIR/.env" ]; then
  cat > "$APP_DIR/.env" <<EOF
# Cấu hình vận hành — KHÔNG commit file này.
# Deploy script (scripts/deploy-from-artifact.sh) giữ lại file này sau mỗi lần deploy.

# Gateway phải nằm ở host port 8080 để Nginx proxy vào 127.0.0.1:8080.
API_GATEWAY_PORT=8080

# CORS: thêm domain web admin thật.
APP_CORS_ALLOWED_ORIGINS=https://admin.locker-drone.tech,http://localhost:3000

# JWT — ĐỔI thành chuỗi ngẫu nhiên >= 32 ký tự.
APP_SECURITY_JWT_SECRET=$(head -c 32 /dev/urandom | base64 | tr -d '=+/' | cut -c1-40)

# SMTP gửi OTP (Gmail app password).
MAIL_USERNAME=
MAIL_PASSWORD=

# Firebase Admin (auth-service verify ID token) — dán nguyên JSON service account.
FIREBASE_CREDENTIALS_JSON=

# VNPay / MoMo — để trống thì dùng sandbox mặc định.
VNPAY_TMN_CODE=
VNPAY_HASH_SECRET=
VNPAY_RETURN_URL=https://${API_DOMAIN}/api/payments/vnpay/callback
MOMO_PARTNER_CODE=
MOMO_ACCESS_KEY=
MOMO_SECRET_KEY=
MOMO_REDIRECT_URL=https://${API_DOMAIN}/api/payments/momo/return
MOMO_IPN_URL=https://${API_DOMAIN}/api/payments/momo/callback
EOF
  chown "$DEPLOY_USER":"$DEPLOY_USER" "$APP_DIR/.env"
  chmod 600 "$APP_DIR/.env"
  echo "Đã tạo $APP_DIR/.env — nhớ điền MAIL_*/FIREBASE_*/thanh toán."
else
  echo ".env đã tồn tại, giữ nguyên."
fi

say "Cấu hình Nginx cho $API_DOMAIN"
if [ ! -f "/etc/nginx/sites-available/${API_DOMAIN}" ]; then
  # CHỈ khai báo block HTTP. Nếu viết sẵn "listen 443 ssl" khi chưa có chứng chỉ,
  # nginx -t sẽ fail ("no ssl_certificate is defined for the listen ... ssl
  # directive") và plugin nginx của certbot từ chối chạy vì nó kiểm tra config
  # trước — thành vòng luẩn quẩn. Certbot sẽ tự thêm block 443, ssl_certificate
  # và redirect 80→443 khi chạy: sudo certbot --nginx -d ${API_DOMAIN} --redirect
  cat > "/etc/nginx/sites-available/${API_DOMAIN}" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${API_DOMAIN};

    location /.well-known/acme-challenge/ { root /var/www/html; }

    # Gateway chạy trong Docker, chỉ nghe trên loopback của VM.
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # WebSocket/STOMP cho notification realtime.
        proxy_set_header Upgrade    \$http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_connect_timeout 60s;
        proxy_read_timeout    300s;
        proxy_send_timeout    300s;
    }

    client_max_body_size 20m;
}
EOF
  ln -sf "/etc/nginx/sites-available/${API_DOMAIN}" "/etc/nginx/sites-enabled/${API_DOMAIN}"
  rm -f /etc/nginx/sites-enabled/default
fi

if nginx -t 2>&1 | tail -1; then
  systemctl reload nginx
  echo "Nginx đã nạp vhost HTTP cho ${API_DOMAIN}."
else
  echo "Nginx báo lỗi cấu hình — xem chi tiết: nginx -t" >&2
  exit 1
fi
systemctl enable nginx

say "Tường lửa trong VM (NSG của Azure vẫn là lớp chặn chính)"
ufw allow OpenSSH >/dev/null
ufw allow 'Nginx Full' >/dev/null
ufw --force enable >/dev/null
ufw status numbered

say "Xong"
cat <<EOF

Còn lại:
  1. Trỏ DNS A record ${API_DOMAIN} về IP public của VM này (Cloudflare: DNS only).
  2. sudo certbot --nginx -d ${API_DOMAIN}
  3. Điền secret trong ${APP_DIR}/.env
  4. Push develop để workflow "Deploy to Azure VM" chạy.

Kiểm tra sau khi deploy:
  curl -fsS https://${API_DOMAIN}/actuator/health
  docker compose -f ${APP_DIR}/docker-compose.yml ps
EOF
