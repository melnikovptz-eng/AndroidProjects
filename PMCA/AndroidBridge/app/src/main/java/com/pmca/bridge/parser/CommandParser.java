package com.pmca.bridge.parser;

public final class CommandParser {

    public static final class ParsedCommand {
        public final boolean valid;
        public final String command;
        public final String route;
        public final String error;

        private ParsedCommand(
                boolean valid,
                String command,
                String route,
                String error) {
            this.valid = valid;
            this.command = command;
            this.route = route;
            this.error = error;
        }
    }

    public ParsedCommand parse(String input) {
        if (input == null) {
            return invalid("Команда отсутствует");
        }

        String command = input.trim();

        if (command.isEmpty()) {
            return invalid("Пустая команда");
        }

        // Пока поддерживается действующий маршрут Termux.
        // Новые маршруты подключаются через ModuleRouter.
        return new ParsedCommand(
                true,
                command,
                "TERMUX",
                "");
    }

    private ParsedCommand invalid(String error) {
        return new ParsedCommand(
                false,
                "",
                "",
                error);
    }
}
