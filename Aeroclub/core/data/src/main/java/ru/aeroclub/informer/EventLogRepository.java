package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local diagnostic journal. Never stores names, message bodies, tokens or credentials. */
public final class EventLogRepository {
    private static final String PREFS="flight_informer";
    private static final String KEY="event_log_json";
    private static final int MAX_EVENTS=200;
    private EventLogRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS,0);
    }

    public static synchronized void record(Context c, String module, String action, String result, String entityId) {
        JSONArray arr=read(prefs(c).getString(KEY,"[]"));
        JSONObject event=new JSONObject();
        try {
            event.put("time",System.currentTimeMillis());
            event.put("module",clean(module));
            event.put("action",clean(action));
            event.put("result",clean(result));
            event.put("entityId",clean(entityId));
        } catch(Exception ignored) {}
        arr.put(event);
        while(arr.length()>MAX_EVENTS) {
            JSONArray trimmed=new JSONArray();
            for(int i=arr.length()-MAX_EVENTS;i<arr.length();i++) {
                JSONObject old=arr.optJSONObject(i);
                if(old!=null) trimmed.put(old);
            }
            arr=trimmed;
        }
        prefs(c).edit().putString(KEY,arr.toString()).apply();
        ModuleDebugLog.log(c, clean(module), clean(action), clean(result), "event_journal_mirrored");
    }

    public static synchronized String getRecent(Context c, int limit) {
        JSONArray arr=read(prefs(c).getString(KEY,"[]"));
        StringBuilder out=new StringBuilder();
        int start=Math.max(0,arr.length()-Math.max(1,limit));
        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("dd.MM HH:mm:ss",java.util.Locale.getDefault());
        for(int i=arr.length()-1;i>=start;i--) {
            JSONObject o=arr.optJSONObject(i);
            if(o==null) continue;
            out.append(fmt.format(new java.util.Date(o.optLong("time",0)))).append("  ")
                .append(o.optString("module","")).append(" • ")
                .append(o.optString("action","")).append(" — ")
                .append(o.optString("result",""));
            String id=o.optString("entityId","");
            if(!id.isEmpty()) out.append(" [").append(id,0,Math.min(8,id.length())).append("]");
            out.append("\n");
        }
        return out.length()==0?"Журнал пока пуст.":out.toString();
    }

    private static String clean(String s) { return s==null?"":s.trim().replaceAll("[\\r\\n]"," "); }
    private static JSONArray read(String s) { try { return new JSONArray(s); } catch(Exception ignored) { return new JSONArray(); } }
}
