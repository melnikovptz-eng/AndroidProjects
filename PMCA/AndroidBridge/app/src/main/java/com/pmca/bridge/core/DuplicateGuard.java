package com.pmca.bridge.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Защищает от одновременного повторного запуска одинаковой команды.
 * Это не является глобальной защитой от циклов сообщений.
 */
public final class DuplicateGuard {
    public static final String EXTRA_TOKEN =
            "com.pmca.bridge.DUPLICATE_GUARD_TOKEN";

    private static final long STALE_AFTER_MS = 120_000L;

    private static final Map<String, Entry> ACTIVE = new HashMap<>();
    private static final Map<String, String> TOKEN_TO_COMMAND =
            new HashMap<>();

    private DuplicateGuard() {}

    private static final class Entry {
        final String token;
        final long startedAt;

        Entry(String token, long startedAt) {
            this.token = token;
            this.startedAt = startedAt;
        }
    }

    /**
     * Возвращает токен для новой активной команды.
     * Возвращает null, если такая команда уже выполняется.
     */
    public static synchronized String begin(String command) {
        pruneExpired();

        if (command == null || command.trim().isEmpty()) {
            return null;
        }

        String key = command.trim();

        if (ACTIVE.containsKey(key)) {
            return null;
        }

        String token = UUID.randomUUID().toString();
        ACTIVE.put(key, new Entry(token, System.currentTimeMillis()));
        TOKEN_TO_COMMAND.put(token, key);

        return token;
    }

    /**
     * Освобождает блокировку по токену операции.
     */
    public static synchronized void finish(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        String command = TOKEN_TO_COMMAND.remove(token);
        if (command == null) {
            return;
        }

        Entry entry = ACTIVE.get(command);
        if (entry != null && token.equals(entry.token)) {
            ACTIVE.remove(command);
        }
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> iterator =
                ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Entry> item = iterator.next();
            Entry entry = item.getValue();

            if (now - entry.startedAt >= STALE_AFTER_MS) {
                TOKEN_TO_COMMAND.remove(entry.token);
                iterator.remove();
            }
        }
    }
}
