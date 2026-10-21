package ru.aeroclub.informer;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttendanceStatusTest {
    @Test public void normalizesGoingAliases() {
        assertEquals(AttendanceStatus.GOING, AttendanceStatus.normalize("Еду"));
        assertEquals(AttendanceStatus.GOING, AttendanceStatus.normalize("Подтверждено участие"));
    }

    @Test public void normalizesNotGoingAliases() {
        assertEquals(AttendanceStatus.NOT_GOING, AttendanceStatus.normalize("Не еду"));
        assertEquals(AttendanceStatus.NOT_GOING, AttendanceStatus.normalize("Не подтвердил участие"));
    }

    @Test public void emptyMeansPendingButUnknownIsRejected() {
        assertEquals(AttendanceStatus.PENDING, AttendanceStatus.normalize(" "));
        assertNull(AttendanceStatus.normalize("Может быть"));
        assertNull(AttendanceStatus.normalize(null));
    }
}
