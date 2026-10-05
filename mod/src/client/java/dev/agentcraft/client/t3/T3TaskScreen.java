package dev.agentcraft.client.t3;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

final class T3TaskScreen extends Screen {
    private final T3CraftClient mod;
    private final String threadId, cast;
    private final List<Button> writes=new ArrayList<>();
    T3TaskScreen(T3CraftClient mod,String threadId,String cast) { super(Component.literal(cast==null ? "T3 chat task" : "T3 desk")); this.mod=mod; this.threadId=threadId; this.cast=cast; }
    private T3State.ThreadRow row() { return mod.state().snapshot().threads().stream().filter(r -> r.id().equals(threadId)).findFirst().orElse(null); }
    @Override protected void init() {
        clearWidgets(); writes.clear();
        if(cast!=null) addRenderableWidget(Button.builder(Component.literal("Rename NPC"),b->minecraft.gui.setScreen(new T3NpcNameScreen(mod,cast,this))).bounds(width-112,16,92,20).build());
        int y=height-82,x=20;
        addRenderableWidget(Button.builder(Component.literal("Chat"),b->{ if(threadId!=null) mod.focus(threadId); mod.openPanel(); }).bounds(x,y,64,20).build()); x+=70;
        addRenderableWidget(Button.builder(Component.literal("History"),b->{ if(threadId!=null) mod.openHistory(threadId); else mod.openLibrary(); }).bounds(x,y,70,20).build()); x+=76;
        addRenderableWidget(Button.builder(Component.literal("Activity"),b->{ if(threadId!=null) {mod.focus(threadId); minecraft.gui.setScreen(new T3ActivityScreen(mod,threadId));} }).bounds(x,y,70,20).build()); x+=76;
        addRenderableWidget(Button.builder(Component.literal("Review"),b->{ if(threadId!=null) mod.openReview(threadId); else mod.openReviews(); }).bounds(x,y,70,20).build());
        addRenderableWidget(Button.builder(Component.literal("Decisions"),b->mod.openThreadDecisions(threadId)).bounds(20,height-56,88,20).build());
        writes.add(addRenderableWidget(Button.builder(Component.literal("Stop run"),b->mod.interrupt(threadId)).bounds(114,height-56,78,20).build()));
        addRenderableWidget(Button.builder(Component.literal(cast==null ? (mod.pinned(row()) ? "Unpin desk" : "Pin desk") : "Use selected chat"),b->{var selected=cast==null ? row() : mod.state().snapshot().focusedRow(); if(selected!=null) {if(cast==null) {mod.togglePin(selected); init();} else {mod.assignDesk(cast,selected); mod.openAgent(cast);}} }).bounds(198,height-56,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("Task board"),b->mod.openBoard()).bounds(20,height-30,94,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width-80,height-30,60,20).build());
    }
    @Override public void tick() { var row=row(); writes.forEach(b->b.active=row!=null&&mod.state().online(row.id())&&row.status()==T3State.Status.WORKING); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(8,8,width-8,height-8,0xF0222529);
        var row=row(); String name=cast==null ? "T3 chat task" : mod.npcName(cast)+" · T3 desk";
        g.text(font,T3Hud.ellipsize(font,name,width-(cast==null ? 40 : 144)),20,18,0xFFFFFFFF,false);
        String title=row==null ? "No T3 chat attached" : row.title();
        int y=40;
        for(var line:font.split(Component.literal(title),width-40)) { g.text(font,line,20,y,0xFF93C5FD,false); y+=font.lineHeight+2; if(y>70)break; }
        String body=row==null ? "Select a chat in the T3 panel, then use Use selected chat to attach it here." :
            (mod.state().online(row.id()) ? StudioProjection.label(row.status()) : "T3 machine offline")+"\n"+StudioProjection.location(row)+"\n\nModel: "+model(row)+"\nModes: "+T3Protocol.string(row.raw(),"interactionMode")+" · "+T3Protocol.string(row.raw(),"runtimeMode");
        g.enableScissor(20,78,width-20,height-88); y=80;
        for(var line:font.split(Component.literal(body),width-40)) { g.text(font,line,20,y,0xFFD4D4D4,false); y+=font.lineHeight+2; }
        g.disableScissor(); super.extractRenderState(g,mx,my,delta);
    }
    private String model(T3State.ThreadRow row) { var selection=T3Protocol.object(row.raw(),"modelSelection"); return selection==null ? "T3 default" : T3Protocol.string(selection,"model"); }
}
