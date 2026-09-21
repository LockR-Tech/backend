# Nhật ký deploy — backend trên Azure

Do workflow `deploy-azure.yml` tự ghi sau **mỗi** lần chạy, kể cả khi thất bại.
Bản ghi mới nhất nằm trên cùng.

| Thời điểm (UTC) | Kết quả | Commit | Người đẩy | Nghiệm thu |
|---|---|---|---|---|
| 2026-09-21 14:19 | success | [`d8f455c`](https://github.com/LockR-Tech/backend/commit/d8f455c38dbe9b377757637e1871c5d9685f1069) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-21 14:00 | success | [`7a2620f`](https://github.com/LockR-Tech/backend/commit/7a2620f5ec6804648834f7515c294ac0c801f3ac) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-21 08:17 | success | [`ce81115`](https://github.com/LockR-Tech/backend/commit/ce81115c6f845154becb5e7e5a00a066e16842f8) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-20 15:59 | success | [`633722d`](https://github.com/LockR-Tech/backend/commit/633722de5e46c7f6e61261dc8fced397afa91881) | @LeThiYenVi | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-18 20:00 | success | [`78fa06a`](https://github.com/LockR-Tech/backend/commit/78fa06a566eb2a59a81906bba05e56e4874adc26) | @TruongNguyenThaiBinh77 | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-17 16:40 | success | [`95b29a9`](https://github.com/LockR-Tech/backend/commit/95b29a92788f334fd66c9fe70cf82c3cf838a4ed) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-17 15:14 | success | [`f8c4a1a`](https://github.com/LockR-Tech/backend/commit/f8c4a1a5cc251847cd7d6a329181053689c86ffc) | @TruongNguyenThaiBinh77 | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-17 14:35 | success | [`56c419f`](https://github.com/LockR-Tech/backend/commit/56c419fa23f38b641dc9eaaf3e85f07a9664d92f) | @TruongNguyenThaiBinh77 | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-16 09:58 | success | [`569d323`](https://github.com/LockR-Tech/backend/commit/569d323e9446b9c7be0534d3746a25146694fccf) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-15 23:15 | success | [`980f4fd`](https://github.com/LockR-Tech/backend/commit/980f4fd08ae29ef74c8fddb4a94081198271ba9b) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-15 23:02 | failure | [`c66a510`](https://github.com/LockR-Tech/backend/commit/c66a5103db397861351c318cd6f6b9c41cca60ac) | @BaoHuy-Dev | **THẤT BẠI** — [run 35032855218](https://github.com/LockR-Tech/backend/actions/runs/35032855218); đã rollback |
| 2026-09-15 22:48 | failure | [`8f5ca15`](https://github.com/LockR-Tech/backend/commit/8f5ca15622dabf7f675f80fce08f7ce03746f61f) | @BaoHuy-Dev | **THẤT BẠI** — [run 35031778235](https://github.com/LockR-Tech/backend/actions/runs/35031778235); đã rollback |
| 2026-09-15 18:58 | failure | [`0b5d0cc`](https://github.com/LockR-Tech/backend/commit/0b5d0cc54ef8417b972b5adfd2179725b50bd71a) | @TruongNguyenThaiBinh77 | **THẤT BẠI** — [run 35009616660](https://github.com/LockR-Tech/backend/actions/runs/35009616660); đã rollback |
| 2026-09-15 16:18 | failure | [`5180d00`](https://github.com/LockR-Tech/backend/commit/5180d0024c82b4ad7b14660fd8efd4841e3d29f2) | @BaoHuy-Dev | **THẤT BẠI** — [run 34992991471](https://github.com/LockR-Tech/backend/actions/runs/34992991471); đã rollback |
| 2026-09-15 16:06 | success | [`058ec73`](https://github.com/LockR-Tech/backend/commit/058ec73b2872e7c578d29c72b01986d37014633d) | @BaoHuy-Dev | health 200 · public 200 · admin 401 · bypass 404 |
| 2026-09-15 06:47 | failure | [`1ec4c85`](https://github.com/LockR-Tech/backend/commit/1ec4c8591d8d4cfc344f81a49765dadea0dfb21e) | @TruongNguyenThaiBinh77 | **THẤT BẠI** — [run 34938040284](https://github.com/LockR-Tech/backend/actions/runs/34938040284); đã rollback |
