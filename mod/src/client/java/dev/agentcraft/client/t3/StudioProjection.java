package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Read-only studio views derived exclusively from paired T3 chats. No game or network dependencies. */
public final class StudioProjection {
    private StudioProjection() {}
    public enum WorkActivity {
        IDLE("idle", "Idle"), THINKING("thinking", "Thinking"), READING("reading", "Reading"),
        EDITING("editing", "Editing"), RUNNING("running", "Running a command"), TESTING("testing", "Testing"),
        REVIEWING("reviewing", "Reviewing changes"), WORKING("running", "Working");
        private final String wire, label;
        WorkActivity(String wire, String label) { this.wire = wire; this.label = label; }
        public String wire() { return wire; }
        public String label() { return label; }
    }
    /** Only active receipts from the current run can specialize the generic working state. */
    public static WorkActivity workActivity(T3State.ThreadRow row, JsonObject detail) {
        if (row.status() != T3State.Status.WORKING) return WorkActivity.IDLE;
        if (detail == null) return WorkActivity.WORKING;
        String active = T3Protocol.string(row.raw(), "activeRunId");
        var items = T3Protocol.array(detail, "turnItems");
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).isJsonObject()) continue; var item = items.get(i).getAsJsonObject();
            if (active == null || !active.equals(T3Protocol.string(item, "runId"))) continue;
            String status = T3Protocol.string(item, "status");
            if (!Set.of("running", "in_progress", "started").contains(status == null ? "" : status)) continue;
            String kind = T3Protocol.string(item, "type");
            if (kind == null) continue;
            switch (kind) {
                case "reasoning" -> { return WorkActivity.THINKING; }
                case "file_search", "web_search" -> { return WorkActivity.READING; }
                case "file_change" -> { return WorkActivity.EDITING; }
                case "checkpoint" -> { return WorkActivity.REVIEWING; }
                case "command_execution" -> {
                    String input = T3Protocol.string(item, "input");
                    String command = input == null ? "" : input.substring(0, Math.min(2000, input.length())).toLowerCase(Locale.ROOT);
                    if (command.matches("(?s).*\\b(test|pytest|vitest|jest|ctest|cargo test|go test)\\b.*")) return WorkActivity.TESTING;
                    if (command.matches("(?s).*\\bgit\\s+(diff|show|log)\\b.*")) return WorkActivity.REVIEWING;
                    if (command.matches("(?s).*\\b(rg|grep|cat|head|tail|get-content|select-string)\\b.*")) return WorkActivity.READING;
                    return WorkActivity.RUNNING;
                }
                default -> { return WorkActivity.WORKING; }
            }
        }
        return WorkActivity.WORKING;
    }
    public static String delegationSummary(T3State.ThreadRow row, JsonObject detail) {
        var lineage = T3Protocol.object(row.raw(), "lineage");
        String parent = lineage == null ? null : T3Protocol.string(lineage, "parentThreadId");
        StringBuilder text = new StringBuilder(parent == null ? "Top-level conversation" : "Parent chat: " + parent);
        if (detail == null) return text.append("\nDelegated task details not loaded yet.").toString();
        var tasks = T3Protocol.array(detail, "subagents"); text.append("\nDelegated tasks: ").append(tasks.size());
        for (int i = 0; i < Math.min(8, tasks.size()); i++) {
            if (!tasks.get(i).isJsonObject()) continue; var task = tasks.get(i).getAsJsonObject();
            text.append("\n").append(T3Protocol.string(task, "title")).append(" · ").append(T3Protocol.string(task, "status"));
            String progress = T3Protocol.string(task, "progress"); if (progress != null) text.append(" · ").append(progress);
        }
        if (tasks.size() > 8) text.append("\nOpen Delegated tasks for all results.");
        return text.toString();
    }
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
            task.addProperty("status",switch(row.status()) { case WORKING -> "doing"; case NEEDS_YOU -> live ? "review" : "blocked"; case ERROR -> "blocked"; case DONE -> "done"; default -> "todo"; });
            task.addProperty("ci","unknown"); task.add("deps",new JsonArray());
            task.addProperty("priority",row.status()==T3State.Status.NEEDS_YOU ? 2 : row.status()==T3State.Status.WORKING ? 1 : 0);
            task.addProperty("blockedReason",!live ? "T3 machine offline" : row.status()==T3State.Status.NEEDS_YOU ? "Waiting for you" : T3Protocol.string(row.raw(),"lastError"));
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
