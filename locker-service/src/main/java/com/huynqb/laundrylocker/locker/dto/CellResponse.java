package com.huynqb.laundrylocker.locker.dto;

import com.huynqb.laundrylocker.locker.model.CellType;

/**
 * Thông tin một ô tủ trong lưới vật lý — dùng cho API layout và màn hình mobile.
 *
 * <p><b>Ý nghĩa các trường:</b>
 * <ul>
 *   <li>{@code id} — PK của locker_boxes.</li>
 *   <li>{@code boxNumber} — số thứ tự ô trong tủ (hiển thị cho người dùng).</li>
 *   <li>{@code size} — kích cỡ vật lý: {@code SMALL}, {@code MEDIUM}, {@code LARGE}, {@code XL}.</li>
 *   <li>{@code cellType} — loại ô, xem {@link CellType}:
 *       <ul>
 *         <li>{@link CellType#STANDARD} — đa dịch vụ (thuê, gửi, giặt).</li>
 *         <li>{@link CellType#DRONE} — chỉ nhận hàng drone; mobile hiển thị icon drone,
 *             không cho phép đặt dịch vụ thường.</li>
 *         <li>{@link CellType#XL} — vali cỡ lớn, thuê tủ loại XL.</li>
 *       </ul>
 *   </li>
 *   <li>{@code rowIndex} — hàng trong lưới, đếm từ 1 (hàng 1 = hàng trên cùng / drone).</li>
 *   <li>{@code colIndex} — cột trong lưới, đếm từ 0.</li>
 *   <li>{@code status} — {@code AVAILABLE}, {@code OCCUPIED}, {@code RESERVED}, {@code FAULT},
 *       {@code CLEANING}, {@code OUT_OF_SERVICE}.</li>
 *   <li>{@code faultReason} — mô tả lỗi nếu status là {@code FAULT}, ngược lại {@code null}.</li>
 * </ul>
 */
public record CellResponse(
        Long id,
        Integer boxNumber,
        String size,
        String cellType,
        Integer rowIndex,
        Integer colIndex,
        String status,
        String faultReason) {
}
