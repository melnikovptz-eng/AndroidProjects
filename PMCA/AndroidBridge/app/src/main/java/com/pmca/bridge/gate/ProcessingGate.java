package com.pmca.bridge.gate;

import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.parser.CommandParser;

public final class ProcessingGate {

    public boolean allow(CommandParser.ParsedCommand command) {
        if (command == null || !command.valid) {
            PmcaLogger.log("Gate: команда отклонена — Parser не подтвердил ввод");
            return false;
        }

        if (command.command == null || command.command.trim().isEmpty()) {
            PmcaLogger.log("Gate: команда отклонена — пустой ввод");
            return false;
        }

        if (!"TERMUX".equals(command.route)) {
            PmcaLogger.log("Gate: маршрут пока не разрешён: " + command.route);
            return false;
        }

        PmcaLogger.log("Gate: выполнение разрешено");
        return true;
    }
}
