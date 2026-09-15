package com.huynqb.laundrylocker.common.settings;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Store trong bộ nhớ cho unit test của các service (không phải bean Spring).
public class InMemorySettingsStore implements SettingsStore {

    private final Map<String, StoredSetting> values = new LinkedHashMap<>();
    private final List<SettingAuditView> audits = new ArrayList<>();

    @Override
    public synchronized Map<String, StoredSetting> loadAll() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public synchronized void upsert(String key, String value, Long actorUserId) {
        values.put(key, new StoredSetting(key, value, actorUserId, LocalDateTime.now()));
    }

    @Override
    public synchronized void delete(String key) {
        values.remove(key);
    }

    @Override
    public synchronized void audit(String key, String oldValue, String newValue, Long actorUserId) {
        audits.add(new SettingAuditView((long) audits.size() + 1, key, oldValue, newValue, actorUserId, LocalDateTime.now()));
    }

    @Override
    public synchronized List<SettingAuditView> audits(String key, int limit) {
        return audits.stream()
                .filter(audit -> key == null || key.equals(audit.key()))
                .sorted(Comparator.comparing(SettingAuditView::id).reversed())
                .limit(limit)
                .toList();
    }
}
