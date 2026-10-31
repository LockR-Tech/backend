package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.service.OrderHistoryLocator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

public class OrderStatusHistoryRepositoryCustomImpl implements OrderStatusHistoryRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    /// Lấy lười: test lát cắt JPA không có bean này thì vẫn lưu được, chỉ không có toạ độ.
    private final ObjectProvider<OrderHistoryLocator> locator;

    public OrderStatusHistoryRepositoryCustomImpl(ObjectProvider<OrderHistoryLocator> locator) {
        this.locator = locator;
    }

    @Override
    @Transactional
    public <S extends OrderStatusHistory> S save(S history) {
        if (history.getId() == null) {
            OrderHistoryLocator l = locator.getIfAvailable();
            if (l != null) {
                l.fill(history);
            }
            entityManager.persist(history);
            return history;
        }
        return entityManager.merge(history);
    }
}
