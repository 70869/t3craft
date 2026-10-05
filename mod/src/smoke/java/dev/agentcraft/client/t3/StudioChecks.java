package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Studio projections must preserve T3's identity, provenance and state for every chat. */
public final class StudioChecks {
    public static void main(String[] args) {
        var reached=new HashSet<Integer>();
        for(int p=0;p<50;p++) {
            var page=WallPagination.of(150,3,p);
            for(int i=page.start();i<page.end();i++) check(reached.add(i),"wall pages cannot duplicate a chat");
        }
        check(reached.size()==150,"all 150 chats remain reachable on readable wall pages");
        check(WallPagination.of(149,3,49).end()==149,"partial final page has the last chat");
        check(WallPagination.of(2,3,49).page()==0,"history shrink clamps the current wall page");
        check(WallPagination.of(0,3,-1).start()==0 && WallPagination.of(0,3,-1).end()==0,"empty wall has no fake card");
        var rows=new ArrayList<T3State.ThreadRow>(); var bindings=new LinkedHashMap<String,T3State.ThreadRow>();
        var details=new HashMap<String,JsonObject>(); var online=new HashSet<String>();
        for(int i=0;i<26;i++) {
            var raw=new JsonObject(); raw.addProperty("branch","branch-"+i); raw.addProperty("worktreePath","/worktree/"+i);
            raw.addProperty("updatedAt","2026-10-05T00:00:00Z");
            var row=new T3State.ThreadRow("chat-"+i,"Chat "+i,"Project "+i,"Machine "+i%2,"http://localhost:"+(3773+i%2),T3State.Status.values()[i%5],Instant.now(),null,raw);
            rows.add(row); if(i<6) bindings.put("resident-"+i,row); if(i!=0) online.add(row.id());
            var detail=new JsonObject(); detail.addProperty("id",row.id());
            var messages=new JsonArray(); var message=new JsonObject(); message.addProperty("id","message-"+i); message.addProperty("role","assistant"); message.addProperty("text","Actual reply "+i); messages.add(message);
            detail.add("messages",messages); detail.add("activities",new JsonArray()); details.put(row.id(),detail);
        }
        var projected=StudioProjection.build(rows,bindings,details,online);
        check(projected.getAsJsonArray("tasks").size()==26,"task board includes unrepresented chats");
        check(projected.getAsJsonArray("memory").size()==26,"history catalog includes all loaded chats");
        check(projected.getAsJsonArray("feed").size()==26,"shared activity includes all chats");
        var tasks=projected.getAsJsonArray("tasks");
        for(int i=0;i<26;i++) {
            var task=tasks.get(i).getAsJsonObject();
            check(task.get("id").getAsString().equals(rows.get(i).id()),"card routes to exact chat");
            check(task.get("ci").getAsString().equals("unknown"),"chat completion does not imply passing CI");
            check(task.get("branch").getAsString().equals("branch-"+i),"actual branch retained");
            if(i>=6) check(task.get("assignee").isJsonNull(),"unrepresented chat cannot mark another NPC waiting");
        }
        check(tasks.get(0).getAsJsonObject().get("blockedReason").getAsString().contains("offline"),"offline provenance retained");
        for(int i=0;i<6;i++) {
            var log=projected.getAsJsonArray("logs").get(i).getAsJsonObject();
            check(log.get("agentId").getAsString().equals("resident-"+i),"monitor has correct resident");
            check(log.getAsJsonArray("entries").get(0).getAsJsonObject().get("text").getAsString().contains("Actual reply "+i),"each monitor reads its own chat");
        }
        var offlineLog=projected.getAsJsonArray("logs").get(0).getAsJsonObject().getAsJsonArray("entries");
        check(offlineLog.get(offlineLog.size()-1).getAsJsonObject().get("text").getAsString().contains("offline"),"cached logs are identified offline");
        check(StudioProjection.build(List.of(),Map.of(),Map.of(),Set.of()).getAsJsonArray("tasks").isEmpty(),"empty history creates no fake tasks");
        var replies=new StudioReplies(); check(replies.observe(bindings,details,online).isEmpty(),"loading existing replies creates no speech");
        var newMessage=new JsonObject(); newMessage.addProperty("id","new-reply"); newMessage.addProperty("role","assistant"); newMessage.addProperty("text","A new T3 reply"); newMessage.addProperty("streaming",true);
        details.get("chat-1").getAsJsonArray("messages").add(newMessage);
        check(replies.observe(bindings,details,online).isEmpty(),"streaming text waits until completion");
        newMessage.addProperty("streaming",false); var speech=replies.observe(bindings,details,online);
        check(speech.size()==1 && speech.getFirst().agent().equals("resident-1") && speech.getFirst().text().equals("A new T3 reply"),"completed new reply speaks on correct NPC");
        check(replies.observe(bindings,details,online).isEmpty(),"repeated snapshots do not replay speech");
        details.get("chat-1").getAsJsonArray("messages").remove(1);
        check(replies.observe(bindings,details,online).isEmpty(),"older resurfaced reply does not replay speech");
        bindings.put("resident-1",rows.get(8)); check(replies.observe(bindings,details,online).isEmpty(),"reassigning a desk does not speak loaded history");
        System.out.println("PASS full history board/library/feed, six independent monitors, exact routing, offline provenance, no fabricated CI or tasks");
    }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
}
