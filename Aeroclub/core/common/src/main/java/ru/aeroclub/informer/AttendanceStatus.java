package ru.aeroclub.informer;

/** Canonical attendance states and legacy-label normalization shared by all features. */
public final class AttendanceStatus {
    public static final String GOING = "Подтвердил участие";
    public static final String NOT_GOING = "Не участвует";
    public static final String PENDING = "Ожидается ответ";

    private AttendanceStatus() {}

    /** Returns a canonical state, or null when the supplied value is unknown. */
    public static String normalize(String status) {
        if (status == null) return null;
        String value = status.trim();
        if (GOING.equals(value) || "Вы подтвердили участие".equals(value) ||
            "Подтверждено участие".equals(value) || "Еду".equals(value)) return GOING;
        if (NOT_GOING.equals(value) || "Вы не участвуете".equals(value) ||
            "Не подтвердил участие".equals(value) || "Не еду".equals(value)) return NOT_GOING;
        if (PENDING.equals(value) || value.isEmpty()) return PENDING;
        return null;
    }
}
