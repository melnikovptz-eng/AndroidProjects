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
    private AttendanceRepository() {}

    public static final class Attendance {
        public final String participantId, flightId, status, updatedAt;
        Attendance(String participantId, String flightId, String status, String updatedAt) {
            this.participantId=participantId; this.flightId=flightId; this.status=status; this.updatedAt=updatedAt;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, 0);
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

    public static synchronized void setResponse(Context c, String flightId, String status) {
        if(flightId==null || flightId.trim().isEmpty()) return;
        String participantId=getLocalParticipantId(c);
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
        prefs(c).edit().putString(RECORDS,target.toString()).putString("status",status).apply();
    }

    public static synchronized String getResponse(Context c, String flightId) {
        if(flightId==null) return "Ответьте, будете ли вы участвовать";
        String participantId=getLocalParticipantId(c);
        JSONArray arr=read(prefs(c).getString(RECORDS,"[]"));
        for(int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i);
            if(o!=null && participantId.equals(o.optString("participantId")) && flightId.equals(o.optString("flightId")))
                return o.optString("status","Ответьте, будете ли вы участвовать");
        }
        return "Ответьте, будете ли вы участвовать";
    }

    public static synchronized int countResponses(Context c, String flightId, String status) {
        JSONArray arr=read(prefs(c).getString(RECORDS,"[]"));
        int count=0;
        for(int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i);
            if(o!=null && flightId!=null && flightId.equals(o.optString("flightId")) && status.equals(o.optString("status"))) count++;
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
