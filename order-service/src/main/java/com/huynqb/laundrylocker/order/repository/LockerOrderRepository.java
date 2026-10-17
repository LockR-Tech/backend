package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LockerOrderRepository extends JpaRepository<LockerOrder, Long>, JpaSpecificationExecutor<LockerOrder> {

    List<LockerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<LockerOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<LockerOrder> findByStaffIdOrderByCreatedAtDesc(Long staffId);

    Optional<LockerOrder> findByOrderCode(String orderCode);

    Optional<LockerOrder> findByPinCode(String pinCode);

    Optional<LockerOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<LockerOrder> findByTypeAndStatusOrderByCreatedAtAsc(String type, String status);

    List<LockerOrder> findByTypeAndStatusNotInOrderByUpdatedAtDesc(String type, List<String> statuses);

    List<LockerOrder> findByTypeOrderByUpdatedAtDesc(String type);

    long countByUserIdAndTypeAndStatus(Long userId, String type, String status);

    /// Đơn drone bị một phiếu sự cố chặn hoặc đã kết thúc vì phiếu đó — để KTV sự cố và admin
    /// thấy phiếu ảnh hưởng tới những đơn nào.
    @Query("select o from LockerOrder o where o.type = 'DRONE_DELIVERY' "
            + "and (o.deliveryBlockTicketId = :ticketId or o.failureTicketId = :ticketId) "
            + "order by o.updatedAt desc")
    List<LockerOrder> findDroneOrdersByTicket(@Param("ticketId") Long ticketId);

    /// Đơn drone đã đóng mà kiện (người gửi đã bỏ vào hệ thống) chưa được trả lại.
    List<LockerOrder> findByTypeAndStatusAndParcelDroppedAtIsNotNullAndParcelReturnedAtIsNull(
            String type, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from LockerOrder o where o.id = :id")
    Optional<LockerOrder> findByIdForUpdate(@Param("id") Long id);

    /// Đơn huỷ/hoàn tiền có dùng điểm mà loyalty-service chưa xác nhận hoàn điểm.
    @Query("""
            select o.id from LockerOrder o
            where o.pointsUsed > 0 and o.pointsRefundedAt is null
              and (o.status = 'CANCELED' or o.paymentStatus = 'REFUNDED')
            order by o.id""")
    List<Long> findPointsRefundPending(org.springframework.data.domain.Pageable page);

    /// Ghi nhận đã hoàn điểm. Giao dịch riêng: gọi sau khi giao dịch huỷ đơn đã commit.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Query("update LockerOrder o set o.pointsRefundedAt = :at where o.id = :id and o.pointsRefundedAt is null")
    int markPointsRefunded(@Param("id") Long id, @Param("at") java.time.LocalDateTime at);

    @Query("select o from LockerOrder o where (o.sendBoxId = :boxId or o.receiveBoxId = :boxId or o.reservedBoxId = :boxId or o.sourceBoxId = :boxId) and o.status not in ('COMPLETED', 'CANCELED', 'EXPIRED') order by o.createdAt desc")
    List<LockerOrder> findActiveByBoxId(@Param("boxId") Long boxId);

    /// Đơn người khác gửi cho `userId`: theo tài khoản người nhận, hoặc theo số điện thoại người nhận
    /// (so sau khi bỏ dấu cách, gạch, chấm, ngoặc — xem PhoneNumbers). `allStatuses = false` ⇒ chỉ
    /// lấy các trạng thái trong `statuses`. `phones` không được rỗng.
    @Query("select o from LockerOrder o where o.userId <> :userId"
            + " and (o.receiverUserId = :userId or o.receiverId = :userId"
            + " or replace(replace(replace(replace(replace(o.receiverPhone, ' ', ''), '-', ''), '.', ''), '(', ''), ')', '')"
            + " in :phones)"
            + " and (:allStatuses = true or o.status in :statuses)"
            + " order by o.createdAt desc, o.id desc")
    List<LockerOrder> findIncoming(
            @Param("userId") Long userId,
            @Param("phones") Collection<String> phones,
            @Param("allStatuses") boolean allStatuses,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    /// Đơn được ủy quyền lấy hộ cho số điện thoại trong `phones` (cùng cách so như trên).
    @Query("select o from LockerOrder o where o.delegatePhone is not null"
            + " and replace(replace(replace(replace(replace(o.delegatePhone, ' ', ''), '-', ''), '.', ''), '(', ''), ')', '')"
            + " in :phones"
            + " and (:allStatuses = true or o.status in :statuses)"
            + " order by o.delegatedAt desc, o.id desc")
    List<LockerOrder> findDelegatedTo(
            @Param("phones") Collection<String> phones,
            @Param("allStatuses") boolean allStatuses,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    /// Thời điểm tạo đơn trong [start, end) — dashboard gom theo giờ (giờ cao điểm).
    @Query("select o.createdAt from LockerOrder o where o.createdAt >= :start and o.createdAt < :end")
    List<java.time.LocalDateTime> createdAtBetween(
            @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);
}
