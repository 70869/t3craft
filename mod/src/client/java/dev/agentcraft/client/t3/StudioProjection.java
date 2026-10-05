package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Read-only studio views derived exclusively from paired T3 chats. No game or network dependencies. */
public final class StudioProjection {
    private StudioProjection() {}
    public static JsonObject build(List<T3State.ThreadRow> rows, Map<String,T3State.ThreadRow> bindings,
                                   Map<String,JsonObject> details, Set<String> online) {
        var result = new JsonObject();
        var tasks = new JsonArray(); var memory = new JsonArray(); var logs = new JsonArray(); var feed = new JsonArray();
        var owners = new HashMap<String,String>();
        bindings.forEach((cast,row) -> owners.put(row.id(),cast));
        for (var row : rows) {
            String owner = owners.get(row.id()); boolean live = online.contains(row.id());
            var task = new JsonObject(); task.addProperty("id",row.id()); task.addProperty("title",row.title());
            task.addProperty("description",location(row)); task.addProperty("assignee",owner);
            task.addProperty("status",switch(row.status()) { case WORKING -> "doing"; case NEEDS_YOU, ERROR -> "blocked"; case DONE -> "done"; default -> "todo"; });
            task.addProperty("ci","unknown"); task.add("deps",new JsonArray());
            task.addProperty("priority",row.status()==T3State.Status.NEEDS_YOU ? 2 : row.status()==T3State.Status.WORKING ? 1 : 0);
            task.addProperty("blockedReason",!live ? "T3 machine offline" : row.status()==T3State.Status.NEEDS_YOU ? "T3 needs your response" : T3Protocol.string(row.raw(),"lastError"));
            task.addProperty("branch",T3Protocol.string(row.raw(),"branch")); task.addProperty("worktree",T3Protocol.string(row.raw(),"worktreePath"));
            task.addProperty("createdAt",time(row.raw(),"createdAt")); task.addProperty("updatedAt",time(row.raw(),"updatedAt")); tasks.add(task);
            var note = new JsonObject(); note.addProperty("id",row.id()); note.addProperty("scope",owner==null ? "shared" : owner);
            note.addProperty("title",row.title()); note.addProperty("updated",time(row.raw(),"updatedAt")); note.addProperty("author",owner);
            note.addProperty("body",location(row)+"\n\n"+label(row.status())+(live ? "" : " · machine offline")+"\n\n"+preview(row,details.get(row.id()))); memory.add(note);
            var receipt = new JsonObject(); receipt.addProperty("ts",time(row.raw(),"updatedAt")); receipt.addProperty("kind","task");
            receipt.addProperty("agentId",owner); receipt.addProperty("text",row.title()+" · "+(live ? label(row.status()) : "Offline")+" · "+row.projectTitle()); feed.add(receipt);
        }
        for (var entry : bindings.entrySet()) {
            var row=entry.getValue(); var detail=details.get(row.id()); var entries=new JsonArray();
            if (detail != null) {
                var focus=T3State.focus(detail);
                for (var message : focus.messages().stream().skip(Math.max(0,focus.messages().size()-16)).toList())
                    entries.add(log(message.role()+": "+message.text(),"text"));
                for (var activity : focus.activity().stream().skip(Math.max(0,focus.activity().size()-8)).toList())
                    entries.add(log(activity.summary()+(activity.detail()==null ? "" : "\n"+activity.detail())+(activity.status()==null ? "" : " · "+activity.status()),"tool"));
            } else {
                String preview=preview(row,null);
                entries.add(log(preview.isBlank() ? location(row)+"\n"+label(row.status()) : preview,"text"));
            }
            if (!online.contains(row.id())) entries.add(log("T3 machine offline · last saved view","error"));
            var log=new JsonObject(); log.addProperty("agentId",entry.getKey()); log.add("entries",entries); logs.add(log);
        }
        result.add("tasks",tasks); result.add("memory",memory); result.add("logs",logs); result.add("feed",feed);
        return result;
    }
    private static JsonObject log(String text,String kind) { var log=new JsonObject(); log.addProperty("kind",kind); log.addProperty("text",text==null ? "" : text); return log; }
    public static String label(T3State.Status status) { return switch(status) { case WORKING -> "Working"; case NEEDS_YOU -> "Needs you"; case DONE -> "Done"; case ERROR -> "Failed"; default -> "Idle"; }; }
    public static String location(T3State.ThreadRow row) {
        String workspace=T3Protocol.string(row.raw(),"worktreePath"); String branch=T3Protocol.string(row.raw(),"branch");
        return row.projectTitle()+(row.environment()==null ? "" : " · "+row.environment())+(branch==null ? "" : "\nBranch: "+branch)+(workspace==null ? "" : "\nWorktree: "+workspace);
    }
    private static String preview(T3State.ThreadRow row,JsonObject detail) {
        if (detail!=null) {
            var messages=T3State.focus(detail).messages();
            if (!messages.isEmpty()) return messages.getLast().text();
        }
        var summary=T3Protocol.object(row.raw(),"latestVisibleMessage");
        if(summary!=null) for(String field:List.of("text","preview")) { String text=T3Protocol.string(summary,field); if(text!=null) return text; }
        return "Open saved history to read this T3 conversation.";
    }
    private static long time(JsonObject object,String key) { try { String value=T3Protocol.string(object,key); return value==null ? 0 : Instant.parse(value).toEpochMilli(); } catch(RuntimeException e) { return 0; } }
}
