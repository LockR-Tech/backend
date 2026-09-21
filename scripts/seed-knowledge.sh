#!/usr/bin/env bash
# Nạp bộ tài liệu mặc định vào kho tri thức của trợ lý hỏi đáp và bộ câu hỏi đánh giá.
#
#   API_BASE_URL=https://api.locker-drone.tech ADMIN_TOKEN=<access token của tài khoản ADMIN> \
#     scripts/seed-knowledge.sh
#
# Đọc tài liệu từ các repo cùng cấp với backend (mobile/, legal/, docs/) — đổi bằng KNOWLEDGE_ROOT.
# Chạy lại an toàn: tài liệu đã có (trùng nội dung) được bỏ qua. Đánh chỉ mục chạy nền trên server
# và cần EMBEDDING_API_KEY; xem trạng thái ở GET /api/admin/knowledge/documents.
# SEED_EVAL=0 để bỏ qua bộ câu hỏi đánh giá.
set -Eeuo pipefail

: "${API_BASE_URL:?Cần API_BASE_URL (vd https://api.locker-drone.tech)}"
: "${ADMIN_TOKEN:?Cần ADMIN_TOKEN (access token của tài khoản ADMIN)}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KNOWLEDGE_ROOT="${KNOWLEDGE_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
API="${API_BASE_URL%/}/api/admin/knowledge"

command -v curl >/dev/null

upload() {
  local file="$1" title="$2"
  shift 2
  if [ ! -f "$file" ]; then
    echo "BỎ QUA  $title — không thấy $file"
    return
  fi
  local form=(-F "file=@${file}" -F "title=${title}")
  for role in "$@"; do
    form+=(-F "allowedRoles=${role}")
  done
  local status
  status="$(curl -sS -o /tmp/seed-knowledge-response.json -w '%{http_code}' \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" "${form[@]}" "${API}/documents")"
  case "$status" in
    200) echo "ĐÃ NẠP  $title ($*)" ;;
    409) echo "ĐÃ CÓ   $title" ;;
    *)
      echo "LỖI $status  $title: $(cat /tmp/seed-knowledge-response.json)" >&2
      return 1
      ;;
  esac
}

TECH_ROLES=(LOCKER_TECHNICIAN DRONE_TECHNICIAN ADMIN)

# Dành cho mọi người dùng.
upload "$SCRIPT_DIR/knowledge/huong-dan-khach-hang.md" "Hướng dẫn sử dụng Lock.R cho khách hàng" ALL
upload "$KNOWLEDGE_ROOT/legal/privacy-policy.html" "Chính sách quyền riêng tư" ALL
upload "$KNOWLEDGE_ROOT/legal/data-deletion.html" "Hướng dẫn xóa dữ liệu người dùng" ALL
upload "$KNOWLEDGE_ROOT/mobile/assets/markdown/terms_of_service.md" "Điều khoản dịch vụ" ALL

# Nội bộ: kỹ thuật viên và admin.
upload "$SCRIPT_DIR/knowledge/huong-dan-ktv-tu.md" "Quy trình vận hành và bảo trì tủ cho kỹ thuật viên" \
  LOCKER_TECHNICIAN ADMIN
upload "$KNOWLEDGE_ROOT/docs/02-flows/flow-1-drone-delivery.md" "Luồng giao hàng bằng drone" "${TECH_ROLES[@]}"
upload "$KNOWLEDGE_ROOT/docs/02-flows/flow-2-locker-send.md" "Luồng gửi hàng và thuê ô" "${TECH_ROLES[@]}"
upload "$KNOWLEDGE_ROOT/docs/02-flows/flow-3-roles-maintenance.md" "Luồng vai trò, vận hành và bảo trì" "${TECH_ROLES[@]}"
upload "$KNOWLEDGE_ROOT/docs/02-flows/flow-4-rag-assistant.md" "Luồng trợ lý hỏi đáp" "${TECH_ROLES[@]}"
upload "$KNOWLEDGE_ROOT/docs/04-engineering/release-deploy.md" "Quy trình phát hành và triển khai" ADMIN

if [ "${SEED_EVAL:-1}" != "0" ]; then
  existing="$(curl -fsS -H "Authorization: Bearer ${ADMIN_TOKEN}" "${API}/eval-cases")"
  if grep -q '"question"' <<<"$existing"; then
    echo "Bộ câu hỏi đánh giá đã có, giữ nguyên."
  else
    curl -fsS -o /dev/null -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" \
      --data-binary "@$SCRIPT_DIR/knowledge/eval-cases.json" "${API}/eval-cases"
    echo "Đã nạp bộ câu hỏi đánh giá."
  fi
fi

echo "Xong. Chạy đánh giá truy xuất (chỉ tốn phí nhúng): curl -X POST -H 'Authorization: Bearer …' ${API}/eval"
