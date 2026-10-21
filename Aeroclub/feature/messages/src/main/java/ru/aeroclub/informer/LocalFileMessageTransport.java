package ru.aeroclub.informer;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Single-device simulator: private app file, no network, no claim of remote delivery. */
public final class LocalFileMessageTransport implements MessageTransport {
 private static final String DIR="local_message_transport", NAME="messages.json";
 private static final int LIMIT=500;
 public static final String OUTGOING="OUTGOING", INCOMING="INCOMING", LOCAL_ONLY="LOCAL_ONLY", SIMULATED_INCOMING="SIMULATED_INCOMING";
 @Override public LocalTransportMessage send(Context c,String recipientId,String recipientLabel,String body){
  String text=clean(body), recipient=clean(recipientId); if(c==null||text.isEmpty()||recipient.isEmpty())return null;
  synchronized(LocalFileMessageTransport.class){List<LocalTransportMessage> all=read(c);LocalTransportMessage m=new LocalTransportMessage(UUID.randomUUID().toString(),recipient,recipient,clean(recipientLabel),OUTGOING,text,LOCAL_ONLY,System.currentTimeMillis());all.add(m);return write(c,all)?m:null;}
 }
 /** Explicit test hook only. This does not represent a message from another device. */
 public LocalTransportMessage simulateIncoming(Context c,String conversationId,String senderId,String senderLabel,String body){
  String text=clean(body), conversation=clean(conversationId);if(c==null||text.isEmpty()||conversation.isEmpty())return null;
  synchronized(LocalFileMessageTransport.class){List<LocalTransportMessage> all=read(c);LocalTransportMessage m=new LocalTransportMessage(UUID.randomUUID().toString(),conversation,clean(senderId),clean(senderLabel),INCOMING,text,SIMULATED_INCOMING,System.currentTimeMillis());all.add(m);return write(c,all)?m:null;}
 }
 @Override public List<LocalTransportMessage> getMessages(Context c,String conversationId){if(c==null)return Collections.emptyList();String f=clean(conversationId);synchronized(LocalFileMessageTransport.class){List<LocalTransportMessage> all=read(c);if(f.isEmpty())return Collections.unmodifiableList(all);List<LocalTransportMessage> result=new ArrayList<>();for(LocalTransportMessage m:all)if(f.equals(m.conversationId))result.add(m);return Collections.unmodifiableList(result);}}
 private static List<LocalTransportMessage> read(Context c){List<LocalTransportMessage> out=new ArrayList<>();File f=file(c);if(!f.isFile())return out;try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);JSONArray a=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(new LocalTransportMessage(o.optString("id",""),o.optString("conversationId",""),o.optString("recipientId",""),o.optString("recipientLabel",""),o.optString("direction",""),o.optString("body",""),o.optString("status",""),o.optLong("createdAt",0)));}}catch(Exception ignored){/* Preserve malformed file; don't silently overwrite it. */}return out;}
 private static boolean write(Context c,List<LocalTransportMessage> items){File tmp=null;try{JSONArray a=new JSONArray();for(int i=Math.max(0,items.size()-LIMIT);i<items.size();i++){LocalTransportMessage m=items.get(i);JSONObject o=new JSONObject();o.put("id",m.id);o.put("conversationId",m.conversationId);o.put("recipientId",m.recipientId);o.put("recipientLabel",m.recipientLabel);o.put("direction",m.direction);o.put("body",m.body);o.put("status",m.status);o.put("createdAt",m.createdAt);a.put(o);}File f=file(c),p=f.getParentFile();if(p==null||(!p.isDirectory()&&!p.mkdirs()))return false;tmp=new File(p,NAME+".tmp");try(FileOutputStream stream=new FileOutputStream(tmp,false)){stream.write(a.toString().getBytes(StandardCharsets.UTF_8));stream.flush();stream.getFD().sync();}if(f.exists()&&!f.delete())return false;if(!tmp.renameTo(f))return false;return true;}catch(Exception ignored){return false;}finally{if(tmp!=null&&tmp.exists())tmp.delete();}}
 private static File file(Context c){return new File(new File(c.getApplicationContext().getFilesDir(),DIR),NAME);}
 private static String clean(String s){return s==null?"":s.trim();}
}
