package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Offline outbox for changes awaiting a configured server API.
 * It records only operation metadata, never names, message bodies or credentials.
 */
public final class SyncRepository {
    private static final String PREFS="flight_informer";
    private static final String KEY="sync_outbox_json";
    private static final String DROPPED="sync_outbox_dropped_count";
    private static final int MAX_PENDING=500;
    private SyncRepository() {}

    private static SharedPreferences prefs(Context c) {
        return LocalFilePreferences.get(c);
    }

    public static synchronized void enqueue(Context c,String entityType,String entityId,String operation) {
        if(entityType==null||entityType.trim().isEmpty()||entityId==null||entityId.trim().isEmpty()||
            operation==null||operation.trim().isEmpty()) {
            ModuleDebugLog.log(c, "DATA-01", "outbox_enqueue", "rejected", "invalid_metadata");
            return;
        }
        JSONArray source=read(prefs(c).getString(KEY,"[]"));
        JSONArray arr=new JSONArray();
        boolean priorCreate=false;
        boolean cancelCreateDelete=false;
        for(int i=0;i<source.length();i++) {
            JSONObject old=source.optJSONObject(i);
            if(old==null) continue;
            boolean same=entityType.equals(old.optString("entityType")) &&
                entityId.equals(old.optString("entityId"));
            if(!same) {
                arr.put(old);
                continue;
            }
            String priorOperation=old.optString("operation","");
            if("create".equals(priorOperation) && "delete".equals(operation)) {
                // Entity was never uploaded; create+delete cancels out in the outbox.
                cancelCreateDelete=true;
                continue;
            }
            if("create".equals(priorOperation)) {
                // Keep create so the server receives the current full local record.
                arr.put(old);
                priorCreate=true;
            }
            // Other pending operations are superseded by the new operation.
        }
        if(cancelCreateDelete || priorCreate) {
            prefs(c).edit().putString(KEY,arr.toString()).apply();
            ModuleDebugLog.log(c, "DATA-01", "outbox_enqueue", "coalesced", cancelCreateDelete ? "create_delete_cancelled" : "existing_create_preserved");
            return;
        }
        JSONObject item=new JSONObject();
        try {
            item.put("operationId",UUID.randomUUID().toString());
            item.put("entityType",entityType);
            item.put("entityId",entityId);
            item.put("operation",operation);
            item.put("createdAt",System.currentTimeMillis());
            item.put("state","pending_server_configuration");
        } catch(Exception ignored) {}
        arr.put(item);
        int dropped = prefs(c).getInt(DROPPED, 0);
        if (arr.length() > MAX_PENDING) {
            JSONArray bounded = new JSONArray();
            int first = arr.length() - MAX_PENDING;
            for (int i = first; i < arr.length(); i++) {
                JSONObject pending = arr.optJSONObject(i);
                if (pending != null) bounded.put(pending);
            }
            dropped += arr.length() - bounded.length();
            arr = bounded;
        }
        prefs(c).edit().putString(KEY,arr.toString()).putInt(DROPPED,dropped).apply();
        ModuleDebugLog.log(c, "DATA-01", "outbox_enqueue", "saved_local_only", "pending_count=" + arr.length());
    }

    public static synchronized void removeDemoFlightOperations(Context c, String flightId) {
        if (flightId == null || flightId.trim().isEmpty()) return;
        JSONArray source = read(prefs(c).getString(KEY, "[]"));
        JSONArray target = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String type = item.optString("entityType", "");
            String id = item.optString("entityId", "");
            String operation = item.optString("operation", "");
            boolean demoFlightOperation = ("flight".equals(type) && flightId.equals(id))
                || ("active_flight".equals(type) && "active".equals(id) && ("set:" + flightId).equals(operation))
                || ("attendance".equals(type) && (flightId.equals(id) || id.endsWith(":" + flightId)));
            if (demoFlightOperation) changed = true;
            else target.put(item);
        }
        if (changed) {
            prefs(c).edit().putString(KEY, target.toString()).apply();
            ModuleDebugLog.log(c, "DATA-01", "outbox_demo_cleanup", "completed_local_only", "pending_count=" + target.length());
        }
    }

    public static synchronized int getPendingCount(Context c) {
        return read(prefs(c).getString(KEY,"[]")).length();
    }

    public static String getStatus(Context c) {
        int dropped = prefs(c).getInt(DROPPED, 0);
        return "Ожидают синхронизации: "+getPendingCount(c)+". Сервер не настроен; отправка не выполняется."
            + (dropped > 0 ? " Старых операций удалено из-за лимита: " + dropped + "." : "");
    }

    public static synchronized String getPendingSummary(Context c,int limit) {
        JSONArray arr=read(prefs(c).getString(KEY,"[]"));
        StringBuilder out=new StringBuilder();
        int start=Math.max(0,arr.length()-Math.max(1,limit));
        for(int i=arr.length()-1;i>=start;i--) {
            JSONObject item=arr.optJSONObject(i);
            if(item!=null) out.append(item.optString("entityType",""))
                .append(" • ").append(item.optString("operation",""))
                .append(" • ").append(item.optString("state","pending")).append("\n");
        }
        return out.length()==0?"Очередь изменений пуста.":out.toString();
    }

    private static JSONArray read(String s) {
        try { return new JSONArray(s); } catch(Exception ignored) { return new JSONArray(); }
    }
}
