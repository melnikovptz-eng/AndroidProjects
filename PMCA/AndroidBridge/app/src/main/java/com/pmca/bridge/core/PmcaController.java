package com.pmca.bridge.core;

import android.content.Context;

import com.pmca.bridge.clipboard.ClipboardCopyModule;
import com.pmca.bridge.gate.ProcessingGate;
import com.pmca.bridge.health.ModuleHealthChecker;
import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.modules.termux.TermuxModule;
import com.pmca.bridge.parser.CommandParser;
import com.pmca.bridge.queue.ExecutionQueue;
import com.pmca.bridge.router.ModuleRouter;
import com.pmca.bridge.termux.TermuxBridgeAdapter;

import java.util.concurrent.atomic.AtomicLong;

public final class PmcaController {
    private static final AtomicLong EXECUTION_COUNTER =
            new AtomicLong(0);

    private final Context appContext;
    private final ClipboardCopyModule clipboard;
    private final TermuxBridgeAdapter termux;

    private final CommandParser parser;
    private final ProcessingGate gate;
    private final ExecutionQueue queue;
    private final ModuleRegistry registry;
    private final ModuleRouter router;

    public PmcaController(Context context) {
        appContext = context.getApplicationContext();

        clipboard = new ClipboardCopyModule(appContext);
        termux = new TermuxBridgeAdapter(appContext);

        parser = new CommandParser();
        gate = new ProcessingGate();
        queue = new ExecutionQueue();
        registry = new ModuleRegistry();
        router = new ModuleRouter(registry);

        boolean registered = registry.register(
                new TermuxModule(appContext, termux));

        PmcaLogger.log(registered
                ? "Контроллер PMCA запущен; TermuxModule зарегистрирован"
                : "Контроллер PMCA запущен; ошибка регистрации TermuxModule");
    }

    /**
     * Основной путь: UI -> Controller -> Parser -> Gate -> Queue -> Router.
     */
    public boolean runNativeCommand(String command) {
        String executionId = String.format(
                java.util.Locale.ROOT,
                "EXEC-%06d",
                EXECUTION_COUNTER.incrementAndGet());

        PmcaLogger.log("Controller: принято выполнение " + executionId);

        CommandParser.ParsedCommand parsed = parser.parse(command);

        if (!parsed.valid) {
            PmcaLogger.log("Controller: Parser отклонил команду: "
                    + parsed.error);
            return false;
        }

        if (!gate.allow(parsed)) {
            PmcaLogger.log("Controller: Gate отклонил " + executionId);
            return false;
        }

        ExecutionContext execution = new ExecutionContext(
                appContext,
                executionId,
                parsed.command);

        execution.setState(ExecutionState.QUEUED);
        PmcaLogger.log("Controller: " + executionId
                + " поставлен в очередь");

        return queue.execute(
                executionId,
                () -> router.route(parsed, execution));
    }

    /**
     * Совместимость с существующим файловым мостом Taper.
     */
    public TermuxBridgeAdapter.Submission submitToTermux(String command) {
        PmcaLogger.log("Controller: запрос файлового моста Termux");
        return termux.submitShellCommand(command);
    }

    public String waitForTermuxResult(String operationId, long timeoutMs) {
        return termux.waitForResult(operationId, timeoutMs);
    }

    public boolean sendToTermux(String command) {
        return termux.sendShellCommand(command);
    }

    public boolean copy(String text, String label) {
        boolean ok = clipboard.copy(text, label);
        PmcaLogger.log(ok
                ? "ClipboardModule: текст скопирован"
                : "ClipboardModule: копирование не удалось");
        return ok;
    }

    public String readClipboard() {
        String text = clipboard.read();
        PmcaLogger.log(text.isEmpty()
                ? "ClipboardModule: буфер пуст"
                : "ClipboardModule: текст прочитан");
        return text;
    }

    public String getModuleStatus() {
        return registry.statusReport();
    }

    public String checkModuleHealth() {
        return ModuleHealthChecker.check(registry);
    }
}
