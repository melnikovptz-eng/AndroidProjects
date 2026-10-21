package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Local attendance repository.
 * Until real authentication exists, responses are scoped to a stable local-device participant ID
 * and a flight ID; they are not shared with other devices or represented as club-wide totals.
 */
public final class AttendanceRepository {
    private static final String PREFS = "flight_informer";
    private static final String DEVICE_ID = "local_participant_id";
    private static final String RECORDS = "attendance_json";
    private static final String SELECTED_PARTICIPANT = "selected_attendance_participant_id";
    public static final String STATUS_GOING = AttendanceStatus.GOING;
    public static final String STATUS_NOT_GOING = AttendanceStatus.NOT_GOING;
    public static final String STATUS_PENDING = AttendanceStatus.PENDING;
    private AttendanceRepository() {}

    public static final class Attendance {
        public final String participantId, flightId, status, updatedAt;
        Attendance(String participantId, String flightId, String status, String updatedAt) {
            this.participantId=participantId; this.flightId=flightId; this.status=status; this.updatedAt=updatedAt;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return LocalFilePreferences.get(c);
    }

    public static synchronized String getLocalParticipantId(Context c) {
        SharedPreferences p=prefs(c);
        String id=p.getString(DEVICE_ID,null);
        if(id==null || id.trim().isEmpty()) {
            id="local-"+UUID.randomUUID();
            p.edit().putString(DEVICE_ID,id).apply();
        }
        return id;
    }

    public static synchronized boolean setResponse(Context c, String flightId, String status) {
        return setResponse(c, getLocalParticipantId(c), flightId, status);
    }

    public static synchronized boolean setResponse(Context c, String participantId, String flightId, String status) {
        status = normalizeStatus(status);
        if(participantId==null || participantId.trim().isEmpty() ||
           flightId==null || flightId.trim().isEmpty() || status==null) return false;
        if (FlightRepository.getById(c, flightId) == null) return false;
        JSONArray source=read(prefs(c).getString(RECORDS,"[]"));
        JSONArray target=new JSONArray();
        String now=String.valueOf(System.currentTimeMillis());
        boolean replaced=false;
        for(int i=0;i<source.length();i++) {
            JSONObject item=source.optJSONObject(i);
            if(item==null) continue;
            if(participantId.equals(item.optString("participantId")) && flightId.equals(item.optString("flightId"))) {
                if(!replaced) target.put(record(participantId,flightId,status,now));
                replaced=true;
            } else target.put(item);
        }
        if(!replaced) target.put(record(participantId,flightId,status,now));
        prefs(c).edit().putString(RECORDS,target.toString()).apply();
        Flight linkedFlight = FlightRepository.getById(c, flightId);
        if (linkedFlight != null && !linkedFlight.isDemo) SyncRepository.enqueue(c,"attendance",participantId+":"+flightId,"upsert");
        return LocalFilePreferences.get(c).isStorageHealthy();
    }

    public static synchronized void setSelectedParticipantId(Context c, String participantId) {
        SharedPreferences.Editor editor = prefs(c).edit();
        if (participantId == null || participantId.trim().isEmpty()) editor.remove(SELECTED_PARTICIPANT);
        else editor.putString(SELECTED_PARTICIPANT, participantId.trim());
        editor.apply();
    }

    public static synchronized String getSelectedParticipantId(Context c) {
        return prefs(c).getString(SELECTED_PARTICIPANT, null);
    }

    public static String normalizeStatus(String status) {
        return AttendanceStatus.normalize(status);
    }

    public static synchronized String getResponse(Context c, String flightId) {
        return getResponse(c, getLocalParticipantId(c), flightId);
    }

    public static synchronized String getResponse(Context c, String participantId, String flightId) {
        if(flightId==null || participantId==null) return "Ожидается ответ";
        JSONArray arr=read(prefs(c).getString(RECORDS,"[]"));
        for(int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i);
            if(o!=null && participantId.equals(o.optString("participantId")) && flightId.equals(o.optString("flightId"))) {
                String normalized = normalizeStatus(o.optString("status", STATUS_PENDING));
                return normalized == null ? STATUS_PENDING : normalized;
            }
        }
        return "Ожидается ответ";
    }

    public static synchronized void deleteFlight(Context c, String flightId) {
        deleteFlight(c, flightId, true);
    }

    public static synchronized void deleteFlight(Context c, String flightId, boolean enqueueSync) {
        if(flightId==null || flightId.trim().isEmpty()) return;
        JSONArray source=read(prefs(c).getString(RECORDS,"[]"));
        JSONArray target=new JSONArray();
        for(int i=0;i<source.length();i++) {
            JSONObject item=source.optJSONObject(i);
            if(item!=null && !flightId.equals(item.optString("flightId"))) target.put(item);
        }
        prefs(c).edit().putString(RECORDS,target.toString()).apply();
        if (enqueueSync) SyncRepository.enqueue(c,"attendance",flightId,"delete_flight_records");
    }

    public static synchronized void deleteParticipant(Context c, String participantId) {
        if(participantId==null || participantId.trim().isEmpty()) return;
        JSONArray source=read(prefs(c).getString(RECORDS,"[]"));
        JSONArray target=new JSONArray();
        for(int i=0;i<source.length();i++) {
            JSONObject item=source.optJSONObject(i);
            if(item!=null && !participantId.equals(item.optString("participantId"))) target.put(item);
        }
        prefs(c).edit().putString(RECORDS,target.toString()).apply();
        SyncRepository.enqueue(c,"attendance",participantId,"delete_participant_records");
    }

    public static synchronized int countResponses(Context c, String flightId, String status) {
        JSONArray arr=read(prefs(c).getString(RECORDS,"[]"));
        int count=0;
        for(int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i);
            if(o!=null && flightId!=null && flightId.equals(o.optString("flightId")) &&
                status != null && status.equals(normalizeStatus(o.optString("status")))) count++;
        }
        return count;
    }

    private static JSONArray read(String value) {
        try { return new JSONArray(value); } catch(Exception ignored) { return new JSONArray(); }
    }
    private static JSONObject record(String participantId,String flightId,String status,String updatedAt) {
        JSONObject o=new JSONObject();
        try { o.put("participantId",participantId); o.put("flightId",flightId); o.put("status",status); o.put("updatedAt",updatedAt); }
        catch(Exception ignored) {}
        return o;
    }
}
