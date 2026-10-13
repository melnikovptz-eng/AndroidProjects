package com.pmca.bridge.modules.termux;

import android.content.Context;

import com.pmca.bridge.core.ExecutionContext;
import com.pmca.bridge.core.ModuleContract;
import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.termux.TermuxBridgeAdapter;

public final class TermuxModule implements ModuleContract {
    private final Context context;
    private final TermuxBridgeAdapter adapter;
    private volatile boolean started;

    public TermuxModule(Context context, TermuxBridgeAdapter adapter) {
        this.context = context.getApplicationContext();
        this.adapter = adapter;
    }

    @Override
    public String moduleId() {
        return "termux";
    }

    @Override
    public boolean supports(String route) {
        return "TERMUX".equals(route);
    }

    @Override
    public boolean start() {
        started = adapter != null;
        return started;
    }

    @Override
    public boolean stop() {
        // RUN_COMMAND управляется Termux. Не останавливаем чужие процессы.
        started = false;
        return true;
    }

    @Override
    public boolean restart() {
        stop();
        return start();
    }

    @Override
    public String status() {
        return started ? "STARTED" : "STOPPED";
    }

    @Override
    public boolean health() {
        return started && adapter != null && context != null;
    }

    @Override
    public boolean execute(ExecutionContext execution) {
        if (!health() || execution == null) {
            PmcaLogger.log("TermuxModule: модуль недоступен");
            return false;
        }

        PmcaLogger.log("TermuxModule: передача "
                + execution.getExecutionId());

        // Сохраняем действующий Termux RUN_COMMAND API.
        // DuplicateGuard и callback остаются в рабочем адаптере.
        return adapter.runNativeCommand(
                context,
                execution.getCommand());
    }
}
