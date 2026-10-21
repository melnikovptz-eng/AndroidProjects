package ru.aeroclub.informer;

import android.content.Context;

/** Validated participant and attendance actions used by the admin UI. */
public final class ParticipantAdminActions {
    private ParticipantAdminActions() {}

    public static boolean isValidName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    public static Participant add(Context context, String displayName) {
        Participant participant = ParticipantRepository.add(context, displayName);
        EventLogRepository.record(context, "PAR-01", "create",
            participant == null ? "rejected_empty_or_duplicate" : "success",
            participant == null ? null : participant.id);
        return participant;
    }

    public static boolean rename(Context context, String participantId, String displayName) {
        boolean success = ParticipantRepository.rename(context, participantId, displayName);
        EventLogRepository.record(context, "PAR-01", "rename",
            success ? "success" : "rejected_empty_duplicate_or_missing", participantId);
        return success;
    }

    public static boolean delete(Context context, String participantId) {
        boolean success = ParticipantRepository.delete(context, participantId);
        EventLogRepository.record(context, "PAR-01", "delete",
            success ? "success" : "rejected_missing", participantId);
        return success;
    }

    public static boolean setResponse(Context context, String participantId, String flightId, String status) {
        String normalized = AttendanceRepository.normalizeStatus(status);
        if (participantId == null || participantId.trim().isEmpty() ||
            flightId == null || flightId.trim().isEmpty() || normalized == null) {
            EventLogRepository.record(context, "ATT-01", "participant_response_update", "rejected_invalid_input", participantId);
            return false;
        }
        AttendanceRepository.setResponse(context, participantId, flightId, normalized);
        EventLogRepository.record(context, "ATT-01", "participant_response_update", "success_local_only", participantId);
        return true;
    }
}
