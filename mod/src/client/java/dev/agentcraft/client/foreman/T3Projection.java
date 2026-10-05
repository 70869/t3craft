package dev.agentcraft.client.foreman;

import com.google.gson.*;
import dev.agentcraft.Cast;
import dev.agentcraft.client.t3.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Presentation adapter only. T3 owns chats, runs, questions and permissions. */
public final class T3Projection {
    private static Map<String,T3State.ThreadRow> bindings=Map.of();
    private static int ticks;
    private static String previous;
    private static final StudioReplies replies=new StudioReplies();
    private T3Projection() {}
    static void init(ForemanState state) {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { if(++ticks%20==0 && !state.isHeld()) sync(state); });
    }
    public static T3State.ThreadRow agentThread(String agent) { return bindings.get(agent); }
    public static boolean agentOffline(String agent) { var row=bindings.get(agent); return row!=null && !T3CraftClient.get().state().online(row.id()); }
    public static void openAgent(String agent) { T3CraftClient.get().openAgent(agent); }
    private static void sync(ForemanState state) {
        var client=T3CraftClient.get(); var snapshot=client.state().snapshot();
        bindings=client.studioBindings(); boolean live=snapshot.connected();
        var phase=live ? LinkStatus.Phase.SYNCED : LinkStatus.Phase.WAITING_RETRY;
        state.setLink(new LinkStatus(phase,"T3 Code paired client",0,snapshot.error(),
            state.link().phase()==phase ? state.link().sinceMs() : System.currentTimeMillis(),0,live||state.link().everSynced()));
        var online=new HashSet<String>();
        for(var row:snapshot.threads()) if(client.state().online(row.id())) online.add(row.id());
        var details=new HashMap<>(client.state().studioDetails());
        var json=StudioProjection.build(snapshot.threads(),bindings,details,online);
        var status=new JsonObject(); status.addProperty("version","0.2.0"); status.addProperty("backend","t3");
        status.addProperty("auth",live ? "ok" : client.paired() ? "checking" : "unknown");
        status.addProperty("message",live ? snapshot.threads().size()+" T3 chats" : client.paired() ? snapshot.error() : "Press backtick to pair T3");
        json.add("foreman",status); var agents=new JsonArray(); var decisionBindings=new HashMap<String,String>();
        for(var cast:Cast.members().values()) {
            var row=bindings.get(cast.id()); boolean offline=row!=null&&!online.contains(row.id());
            var agent=new JsonObject(); agent.addProperty("id",cast.id()); agent.addProperty("name",client.npcName(cast.id())); agent.addProperty("skin",cast.id());
            agent.addProperty("role",cast.role()); agent.addProperty("title",row==null ? "T3 chat desk" : row.title());
            agent.addProperty("color",String.format("#%06X",cast.color())); agent.addProperty("active",row!=null); agent.addProperty("paused",false);
            String visual=row==null||offline ? "idle" : switch(row.status()) { case WORKING -> "running"; case NEEDS_YOU -> "waiting_user"; case DONE -> "done"; case ERROR -> "error"; default -> "idle"; };
            agent.addProperty("state",visual); agent.addProperty("station",visual.equals("waiting_user") ? "user" : visual.equals("running") ? "desk" : "lounge");
            agent.addProperty("activity",row==null ? "No T3 chat attached" : offline ? "T3 machine offline · "+row.title() : row.status()==T3State.Status.NEEDS_YOU && client.state().waitingDetail(row.id())!=null ? client.state().waitingDetail(row.id()) : row.title());
            if(row!=null) { agent.addProperty("taskId",row.id()); decisionBindings.put(cast.id(),row.id()); }
            agents.add(agent);
        }
        json.add("agents",agents); json.add("decisions",client.decisionProjection(decisionBindings));
        json.add("repos",new JsonArray()); json.add("goals",new JsonArray());
        String encoded=json.toString();
        if(!encoded.equals(previous)) { state.apply("snapshot",json); previous=encoded; }
        for(var reply:replies.observe(bindings,details,online)) {
            var speech=new JsonObject(); speech.addProperty("agentId",reply.agent()); speech.addProperty("text",reply.text());
            speech.addProperty("to","user"); speech.addProperty("ts",System.currentTimeMillis()); state.apply("agent.say",speech);
        }
    }
}
