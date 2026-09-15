# Nhật ký deploy — backend trên Azure

Do workflow `deploy-azure.yml` tự ghi sau **mỗi** lần chạy, kể cả khi thất bại.
Bản ghi mới nhất nằm trên cùng.

| Thời điểm (UTC) | Kết quả | Commit | Người đẩy | Nghiệm thu |
|---|---|---|---|---|
| 2026-09-15 22:48 | failure | [`8f5ca15`](https://github.com/LockR-Tech/backend/commit/8f5ca15622dabf7f675f80fce08f7ce03746f61f) | @BaoHuy-Dev | **THẤT BẠI** — [run 35031778235](https://github.com/LockR-Tech/backend/actions/runs/35031778235); đã rollback |
| 2026-09-15 18:58 | failure | [`0b5d0cc`](https://github.com/LockR-Tech/backend/commit/0b5d0cc54ef8417b972b5adfd2179725b50bd71a) | @TruongNguyenThaiBinh77 | **THẤT BẠI** — [run 35009616660](https://github.com/LockR-Tech/backend/actions/runs/35009616660); đã rollback |
| 2026-09-15 16:18 | failure | [`5180d00`](https://github.com/LockR-Tech/backend/commit/5180d0024c82b4ad7b14660fd8efd4841e3d29f2) | @BaoHuy-Dev | **THẤT BẠI** — [run 34992991471](https://github.com/LockR-Tech/backend/actions/runs/34992991471); đã rollback |
| 2026-09-15 16:06 | success | [`058ec73`](https://github.com/LockR-Tech/backend/commit/058ec73b2872e7c578d29c72b01986d37014633d) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-15 06:47 | failure | [`1ec4c85`](https://github.com/LockR-Tech/backend/commit/1ec4c8591d8d4cfc344f81a49765dadea0dfb21e) | @TruongNguyenThaiBinh77 | **THẤT BẠI** — [run 34938040284](https://github.com/LockR-Tech/backend/actions/runs/34938040284); đã rollback |
