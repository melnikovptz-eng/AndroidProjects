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
    private static final int MAX_PENDING=500;
    private SyncRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS,0);
    }

    public static synchronized void enqueue(Context c,String entityType,String entityId,String operation) {
        if(entityType==null||entityType.trim().isEmpty()||entityId==null||entityId.trim().isEmpty()||
            operation==null||operation.trim().isEmpty()) return;
        JSONArray arr=read(prefs(c).getString(KEY,"[]"));
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
        while(arr.length()>MAX_PENDING) {
            JSONArray trimmed=new JSONArray();
            for(int i=arr.length()-MAX_PENDING;i<arr.length();i++) {
                JSONObject old=arr.optJSONObject(i);
                if(old!=null) trimmed.put(old);
            }
            arr=trimmed;
        }
        prefs(c).edit().putString(KEY,arr.toString()).apply();
    }

    public static synchronized int getPendingCount(Context c) {
        return read(prefs(c).getString(KEY,"[]")).length();
    }

    public static String getStatus(Context c) {
        return "Ожидают синхронизации: "+getPendingCount(c)+". Сервер не настроен; отправка не выполняется.";
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
