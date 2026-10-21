package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local draft and handoff history. Does not claim external delivery. */
public final class MessageRepository {
    private static final String PREFS = "flight_informer";
    private static final String KEY = "message_history_json";
    private MessageRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS,0);
    }

    public static synchronized MessageRecord saveDraft(Context c, String flightId, String text) {
        String body=text==null?"":text.trim();
        if(body.isEmpty()) return null;
        return append(c, new MessageRecord(UUID.randomUUID().toString(), flightId, body,
            "Черновик сохранён локально", System.currentTimeMillis()));
    }

    public static synchronized MessageRecord recordShareChooserOpened(Context c, String flightId, String text) {
        String body=text==null?"":text.trim();
        if(body.isEmpty()) return null;
        return append(c, new MessageRecord(UUID.randomUUID().toString(), flightId, body,
            "Открыто меню «Поделиться»; доставка не подтверждена", System.currentTimeMillis()));
    }

    public static synchronized List<MessageRecord> getHistory(Context c, String flightId) {
        ArrayList<MessageRecord> result=new ArrayList<>();
        JSONArray arr=read(prefs(c).getString(KEY,"[]"));
        for(int i=arr.length()-1;i>=0;i--) {
            JSONObject o=arr.optJSONObject(i);
            if(o==null) continue;
            String id=o.optString("id",""), f=o.optString("flightId","");
            if(flightId==null ? f.isEmpty() : flightId.equals(f))
                result.add(new MessageRecord(id,f,o.optString("text",""),o.optString("state",""),o.optLong("updatedAt",0)));
        }
        return result;
    }

    private static MessageRecord append(Context c, MessageRecord item) {
        JSONArray arr=read(prefs(c).getString(KEY,"[]"));
        JSONObject o=new JSONObject();
        try { o.put("id",item.id); o.put("flightId",item.flightId==null?"":item.flightId);
            o.put("text",item.text); o.put("state",item.state); o.put("updatedAt",item.updatedAt); }
        catch(Exception ignored) {}
        arr.put(o);
        // Keep local history bounded to avoid unbounded preference growth.
        while(arr.length()>100) {
            JSONArray trimmed=new JSONArray();
            for(int i=arr.length()-100;i<arr.length();i++) {
                JSONObject old=arr.optJSONObject(i); if(old!=null) trimmed.put(old);
            }
            arr=trimmed;
        }
        prefs(c).edit().putString(KEY,arr.toString()).apply();
        return item;
    }

    private static JSONArray read(String value) {
        try { return new JSONArray(value); } catch(Exception ignored) { return new JSONArray(); }
    }
}
