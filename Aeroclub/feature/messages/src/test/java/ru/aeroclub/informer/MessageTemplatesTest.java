package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class MessageTemplatesTest {
    private Flight sample(boolean demo) {
        return new Flight("flight-1", "Тренировочный вылет", "Вс, 25 октября",
            "10:30", "аэродром", false, "2026-10-25", demo);
    }

    @Test public void defaultReminderLabelsDemoData() {
        assertTrue(MessageTemplates.defaultReminder(sample(true)).contains("ДЕМО"));
    }

    @Test public void templatesContainFlightDetails() {
        String body = MessageTemplates.forTemplate(sample(false), 1);
        assertTrue(body.contains("25 октября"));
        assertTrue(body.contains("10:30"));
        assertTrue(body.contains("аэродром"));
        assertFalse(body.contains("ДЕМО"));
    }

    @Test public void templatesUseConfiguredClubName() {
        assertTrue(MessageTemplates.defaultReminder(sample(false), "Клуб Небо").startsWith("Клуб Небо:"));
        assertTrue(MessageTemplates.forTemplate(sample(false), 1, "Клуб Небо").startsWith("Клуб Небо:"));
    }

    @Test public void unknownTemplateReturnsEmptyText() {
        assertEquals("", MessageTemplates.forTemplate(sample(false), 99));
    }

    @Test public void missingFlightReturnsEmptyText() {
        assertEquals("", MessageTemplates.defaultReminder(null));
    }
}
