package ru.aeroclub.informer;

/** Single owner for the local message templates. Does not send messages or claim delivery. */
public final class MessageTemplates {
    private MessageTemplates() {}

    public static String defaultReminder(Flight flight) {
        if (flight == null) return "";
        return "Напоминаем о вылете «" + displayTitle(flight) + "» " + flight.date + " в " + flight.time
            + ". Место встречи: " + flight.place + ". Подтвердите, пожалуйста, участие.";
    }

    public static String forTemplate(Flight flight, int templateIndex) {
        if (flight == null) return "";
        String title = displayTitle(flight);
        switch (templateIndex) {
            case 0:
                return "Напоминаем о вылете «" + title + "» " + flight.date + " в " + flight.time
                    + ". Место встречи: " + flight.place + ". Пожалуйста, подтвердите участие.";
            case 1:
                return "Обновление по вылету «" + title + "»: " + flight.date + " в " + flight.time
                    + ". Место встречи: " + flight.place + ". Проверьте актуальные данные.";
            case 2:
                return "Пожалуйста, подтвердите участие в вылете «" + title + "» " + flight.date
                    + " в " + flight.time + ". Место встречи: " + flight.place + ".";
            default:
                return "";
        }
    }

    private static String displayTitle(Flight flight) {
        return (flight.isDemo ? "ДЕМО · " : "") + flight.title;
    }
}
