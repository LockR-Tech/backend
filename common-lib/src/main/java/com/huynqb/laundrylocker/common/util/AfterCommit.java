package com.huynqb.laundrylocker.common.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// Chạy một việc SAU KHI giao dịch hiện tại commit — dùng cho sự kiện realtime: app nhận tin rồi tải lại
/// phải thấy dữ liệu mới, và giao dịch rollback thì không báo gì. Không có giao dịch ⇒ chạy ngay.
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
