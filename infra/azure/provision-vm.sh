#!/usr/bin/env bash
# Tạo hạ tầng Azure cho backend Smart Laundry Locker.
#
# Chạy trên máy bạn (đã `az login`). Idempotent: chạy lại nhiều lần không hỏng gì,
# resource nào có rồi thì bỏ qua.
#
#   ./provision-vm.sh
#
# Ghi đè mặc định bằng biến môi trường, ví dụ:
#   RG=laundry-rg LOCATION=southeastasia VM_SIZE=Standard_B2ms ./provision-vm.sh

set -Eeuo pipefail

# Git Bash/MSYS tự đổi tham số bắt đầu bằng "/" thành đường dẫn Windows, làm hỏng
# mọi Azure resource ID (/subscriptions/... -> C:/Program Files/Git/subscriptions/...).
# Tắt hẳn cho script này; không ảnh hưởng khi chạy trên Linux/macOS.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

RG="${RG:-laundry-locker-rg}"
# ⚠️ Subscription "Azure for Students" bị gắn policy "Allowed resource deployment
# regions" — deploy ra vùng ngoài danh sách sẽ lỗi RequestDisallowedByAzure.
# Danh sách đo được (2026-08): centralindia, eastasia, uaenorth, malaysiawest,
# koreacentral. southeastasia KHÔNG nằm trong đó. eastasia (Hong Kong) gần VN nhất.
# Xem lại danh sách của bạn bằng:
#   az policy assignment list --query "[].parameters.listOfAllowedLocations.value"
LOCATION="${LOCATION:-eastasia}"
VM_NAME="${VM_NAME:-laundry-locker-vm}"
# 12 service Spring Boot (mỗi cái ~-Xmx320m) + Postgres + RabbitMQ ≈ 6.3 GB RAM,
# nên cần 8 GB; loại 4 GB sẽ bị OOM lúc build image.
#
# Hạn mức thật đã đo trên subscription "Azure for Students" (southeastasia):
#   Total Regional vCPUs    : 6     <- ràng buộc chính
#   Standard BS    Family   : 4     (B2ms, B4ms)
#   Standard Basv2 Family   : 10    (B2as_v2 — AMD)
#   Standard Bsv2  Family   : 10    (B2s_v2  — Intel)
# Chọn B2as_v2 (2 vCPU / 8 GB) vì còn nhiều quota dự phòng.
# KHÔNG dùng các SKU *ps_v2 — đó là ARM (Ampere), image của dự án build cho amd64.
VM_SIZE="${VM_SIZE:-Standard_B2as_v2}"
VM_IMAGE="${VM_IMAGE:-Ubuntu2204}"
ADMIN_USER="${ADMIN_USER:-azureuser}"
# Azure `az vm create` chỉ nhận key RSA ("An RSA key file or key value must be
# supplied"), không nhận ed25519 — nên dùng một cặp key RSA riêng cho deploy.
# Tách khỏi key cá nhân cũng an toàn hơn: private key này sẽ nằm trong GitHub
# secret AZURE_VM_SSH_KEY. Chưa có thì tạo bằng:
#   ssh-keygen -t rsa -b 4096 -f ~/.ssh/laundry_azure_rsa -N "" -C "laundry-locker-azure-deploy"
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/laundry_azure_rsa.pub}"
OS_DISK_SIZE_GB="${OS_DISK_SIZE_GB:-64}"
# SSH mở cho mọi IP — CÓ CHỦ ĐÍCH, đừng siết lại nếu còn dùng auto-deploy:
# workflow deploy-azure.yml chạy trên GitHub-hosted runner với IP động, khoá SSH
# theo IP nhà sẽ làm mọi lần deploy thất bại. Bảo vệ ở đây là xác thực bằng key
# (VM tạo bằng --ssh-key-values không bật đăng nhập mật khẩu).
# Chỉ đặt SSH_SOURCE_PREFIX=<IP>/32 khi bạn chấp nhận deploy tay.
SSH_SOURCE_PREFIX="${SSH_SOURCE_PREFIX:-*}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }

command -v az >/dev/null || { echo "Chưa cài Azure CLI (az)."; exit 1; }
az account show >/dev/null 2>&1 || { echo "Chưa đăng nhập: chạy 'az login' trước."; exit 1; }

if [ ! -f "$SSH_KEY_PATH" ]; then
  echo "Không thấy public key: $SSH_KEY_PATH"
  echo "Tạo bằng: ssh-keygen -t ed25519 -C 'laundry-locker-deploy'"
  exit 1
fi

say "Subscription đang dùng"
az account show --query '{name:name, id:id}' -o table

say "Resource group: $RG ($LOCATION)"
az group create --name "$RG" --location "$LOCATION" -o none

say "Virtual machine: $VM_NAME ($VM_SIZE)"
if az vm show --resource-group "$RG" --name "$VM_NAME" >/dev/null 2>&1; then
  echo "VM đã tồn tại, bỏ qua bước tạo."
else
  az vm create \
    --resource-group "$RG" \
    --name "$VM_NAME" \
    --image "$VM_IMAGE" \
    --size "$VM_SIZE" \
    --admin-username "$ADMIN_USER" \
    --ssh-key-values "$SSH_KEY_PATH" \
    --public-ip-sku Standard \
    --os-disk-size-gb "$OS_DISK_SIZE_GB" \
    --storage-sku StandardSSD_LRS \
    -o none
fi

say "Gán IP tĩnh (để đổi DNS một lần là xong)"
NIC_ID="$(az vm show -g "$RG" -n "$VM_NAME" --query 'networkProfile.networkInterfaces[0].id' -o tsv)"
IP_ID="$(az network nic show --ids "$NIC_ID" --query 'ipConfigurations[0].publicIPAddress.id' -o tsv)"
az network public-ip update --ids "$IP_ID" --allocation-method Static -o none

say "Network security group: chỉ mở 22 / 80 / 443"
# Cổng 8080 KHÔNG mở ra ngoài: Nginx trên VM nhận 443 rồi proxy vào 127.0.0.1:8080.
NSG_NAME="$(az network nic show --ids "$NIC_ID" --query 'networkSecurityGroup.id' -o tsv | awk -F/ '{print $NF}')"

add_rule() {
  local name="$1" port="$2" priority="$3" source="$4"
  az network nsg rule create \
    --resource-group "$RG" \
    --nsg-name "$NSG_NAME" \
    --name "$name" \
    --priority "$priority" \
    --access Allow \
    --protocol Tcp \
    --direction Inbound \
    --source-address-prefixes "$source" \
    --destination-port-ranges "$port" \
    -o none 2>/dev/null || \
  az network nsg rule update \
    --resource-group "$RG" \
    --nsg-name "$NSG_NAME" \
    --name "$name" \
    --source-address-prefixes "$source" \
    --destination-port-ranges "$port" \
    -o none
}

add_rule allow-ssh   22  1001 "$SSH_SOURCE_PREFIX"
add_rule allow-http  80  1002 "*"
add_rule allow-https 443 1003 "*"

PUBLIC_IP="$(az network public-ip show --ids "$IP_ID" --query ipAddress -o tsv)"

say "Xong. Thông tin cần dùng:"
cat <<EOF

  Public IP     : $PUBLIC_IP
  SSH           : ssh $ADMIN_USER@$PUBLIC_IP
  Resource group: $RG

Bước tiếp theo:

  1) Cài Docker + Nginx trên VM:
       scp infra/azure/bootstrap-vm.sh $ADMIN_USER@$PUBLIC_IP:/tmp/
       ssh $ADMIN_USER@$PUBLIC_IP 'sudo bash /tmp/bootstrap-vm.sh'

  2) Trỏ DNS: A record  api.locker-drone.tech -> $PUBLIC_IP  (Cloudflare: DNS only, mây xám)

  3) Xin chứng chỉ TLS trên VM:
       ssh $ADMIN_USER@$PUBLIC_IP 'sudo certbot --nginx -d api.locker-drone.tech'

  4) Thêm secret vào GitHub repo (Settings -> Secrets and variables -> Actions):
       AZURE_VM_HOST    = $PUBLIC_IP
       AZURE_VM_USER    = $ADMIN_USER
       AZURE_VM_SSH_KEY = nội dung private key khớp với $SSH_KEY_PATH
       AZURE_VM_PORT    = 22   (tùy chọn)

  5) Push vào develop -> workflow "Deploy to Azure VM" tự build và deploy.

EOF
