package ru.aeroclub.informer;

/** Immutable message record. LOCAL_ONLY never means delivered to another device. */
public final class LocalTransportMessage {
 public final String id, conversationId, recipientId, recipientLabel, direction, body, status;
 public final long createdAt;
 public LocalTransportMessage(String id,String conversationId,String recipientId,String recipientLabel,String direction,String body,String status,long createdAt){
  this.id=s(id);this.conversationId=s(conversationId);this.recipientId=s(recipientId);this.recipientLabel=s(recipientLabel);this.direction=s(direction);this.body=s(body);this.status=s(status);this.createdAt=createdAt;
 }
 private static String s(String v){return v==null?"":v;}
}
