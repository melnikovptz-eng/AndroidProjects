package com.pmca.bridge.queue;

import com.pmca.bridge.logging.PmcaLogger;

import java.util.concurrent.Callable;

/**
 * Первая версия очереди: синхронное последовательное выполнение.
 * Не создаёт фоновых потоков и дополнительных демонов.
 */
public final class ExecutionQueue {

    public synchronized boolean execute(
            String executionId,
            Callable<Boolean> operation) {

        PmcaLogger.log("Queue: выполнение " + executionId);

        try {
            boolean accepted = Boolean.TRUE.equals(operation.call());

            PmcaLogger.log("Queue: " + executionId
                    + (accepted
                    ? " — запрос обработан, результат ожидается отдельно"
                    : " — операция не принята"));

            return accepted;
        } catch (Exception e) {
            PmcaLogger.log("Queue: ошибка " + executionId + ": "
                    + e.getClass().getSimpleName());
            return false;
        }
    }
}
