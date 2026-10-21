package ru.aeroclub.informer;

/** Single owner for local message templates. Does not send messages or claim delivery. */
public final class MessageTemplates {
    private MessageTemplates() {}

    public static String defaultReminder(Flight flight) {
        return defaultReminder(flight, "Аэроклуб");
    }

    public static String defaultReminder(Flight flight, String clubName) {
        if (flight == null) return "";
        return FlightPresentation.clubNameOrDefault(clubName) + ": напоминаем о вылете «" + FlightPresentation.title(flight) + "» "
            + flight.date + " в " + flight.time + ". Место встречи: " + flight.place
            + ". Подтвердите, пожалуйста, участие.";
    }

    public static String forTemplate(Flight flight, int templateIndex) {
        return forTemplate(flight, templateIndex, "Аэроклуб");
    }

    public static String forTemplate(Flight flight, int templateIndex, String clubName) {
        if (flight == null) return "";
        String prefix = FlightPresentation.clubNameOrDefault(clubName) + ": ";
        String title = FlightPresentation.title(flight);
        switch (templateIndex) {
            case 0:
                return prefix + "напоминаем о вылете «" + title + "» " + flight.date + " в " + flight.time
                    + ". Место встречи: " + flight.place + ". Пожалуйста, подтвердите участие.";
            case 1:
                return prefix + "обновление по вылету «" + title + "»: " + flight.date + " в " + flight.time
                    + ". Место встречи: " + flight.place + ". Проверьте актуальные данные.";
            case 2:
                return prefix + "пожалуйста, подтвердите участие в вылете «" + title + "» " + flight.date
                    + " в " + flight.time + ". Место встречи: " + flight.place + ".";
            default:
                return "";
        }
    }

}
