package com.pmca.bridge.router;

import com.pmca.bridge.core.ExecutionContext;
import com.pmca.bridge.core.ModuleContract;
import com.pmca.bridge.core.ModuleRegistry;
import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.parser.CommandParser;

public final class ModuleRouter {
    private final ModuleRegistry registry;

    public ModuleRouter(ModuleRegistry registry) {
        this.registry = registry;
    }

    public boolean route(
            CommandParser.ParsedCommand command,
            ExecutionContext context) {

        if (command == null || !command.valid || context == null) {
            PmcaLogger.log("Router: отсутствуют данные маршрутизации");
            return false;
        }

        ModuleContract module = registry.findRoute(command.route);

        if (module == null) {
            PmcaLogger.log("Router: исполнитель не найден: " + command.route);
            return false;
        }

        try {
            PmcaLogger.log("Router: " + context.getExecutionId()
                    + " -> " + module.moduleId());

            context.setState(
                    com.pmca.bridge.core.ExecutionState.RUNNING);

            boolean accepted = module.execute(context);

            /*
             * ACCEPTED означает только, что исполнитель принял запрос.
             * Фактическое завершение подтверждается каналом результата
             * Termux, а не возвращаемым boolean.
             */
            context.setState(accepted
                    ? com.pmca.bridge.core.ExecutionState.ACCEPTED
                    : com.pmca.bridge.core.ExecutionState.ERROR);

            PmcaLogger.log("Router: " + context.getExecutionId()
                    + (accepted
                    ? " принят к запуску; ожидается результат Termux"
                    : " отклонён исполнителем"));

            return accepted;

        } catch (Exception e) {
            context.setState(
                    com.pmca.bridge.core.ExecutionState.ERROR);

            PmcaLogger.log("Router: ошибка модуля "
                    + module.moduleId() + ": "
                    + e.getClass().getSimpleName());

            return false;
        }
    }
}
