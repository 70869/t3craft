package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.util.*;

/** Speech is a new, completed T3 assistant reply, never a replay of loaded history. */
public final class StudioReplies {
    public record Reply(String agent,String thread,String text) {}
    private record Seen(String thread,LinkedHashSet<String> messages) {}
    private final Map<String,Seen> desks=new HashMap<>();
    public List<Reply> observe(Map<String,T3State.ThreadRow> bindings,Map<String,JsonObject> details,Set<String> online) {
        desks.keySet().retainAll(bindings.keySet()); var replies=new ArrayList<Reply>();
        for(var entry:bindings.entrySet()) {
            String cast=entry.getKey(), thread=entry.getValue().id(); var detail=details.get(thread);
            if(detail==null) continue;
            var completed=T3Protocol.array(detail,"messages").asList().stream().filter(v->v.isJsonObject())
                .map(v->v.getAsJsonObject()).filter(m->"assistant".equals(T3Protocol.string(m,"role")) && !Boolean.parseBoolean(T3Protocol.string(m,"streaming")) && T3Protocol.string(m,"id")!=null && T3Protocol.string(m,"text")!=null && !T3Protocol.string(m,"text").isBlank()).toList();
            if(completed.isEmpty()) continue;
            var previous=desks.get(cast); boolean baseline=previous==null || !previous.thread.equals(thread);
            if(baseline) { previous=new Seen(thread,new LinkedHashSet<>()); desks.put(cast,previous); }
            var latest=completed.getLast(); String id=T3Protocol.string(latest,"id");
            if(!baseline && !previous.messages.contains(id) && online.contains(thread)) replies.add(new Reply(cast,thread,T3Protocol.string(latest,"text")));
            for(var message:completed) previous.messages.add(T3Protocol.string(message,"id"));
            while(previous.messages.size()>128) previous.messages.remove(previous.messages.iterator().next());
        }
        return List.copyOf(replies);
    }
}
