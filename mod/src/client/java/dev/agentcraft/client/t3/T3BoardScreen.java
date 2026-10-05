package dev.agentcraft.client.t3;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Four T3 lanes with independent pagination; all chats remain reachable beyond the physical wall. */
final class T3BoardScreen extends Screen {
    private final T3CraftClient mod;
    private final int[] pages=new int[4];
    private List<T3State.ThreadRow> rendered=List.of();
    private final List<List<T3State.ThreadRow>> lanes=new ArrayList<>();
    T3BoardScreen(T3CraftClient mod) { super(Component.literal("T3 task board")); this.mod=mod; }
    private static int lane(T3State.ThreadRow row) { return switch(row.status()) {case WORKING->1; case NEEDS_YOU,ERROR->2; case DONE->3; default->0;}; }
    @Override protected void init() { rebuild(); }
    private void rebuild() {
        clearWidgets(); rendered=mod.state().snapshot().threads(); lanes.clear();
        int count=Math.max(1,(height-124)/36),w=(width-44)/4;
        for(int col=0;col<4;col++) {
            final int c=col; int x=16+col*(w+4);
            var rows=rendered.stream().filter(r->lane(r)==c).toList(); lanes.add(rows);
            pages[c]=Math.min(pages[c],Math.max(0,(rows.size()-1)/count));
            for(int i=pages[c]*count;i<Math.min(rows.size(),(pages[c]+1)*count);i++) {
                var row=rows.get(i); int y=72+(i-pages[c]*count)*36;
                addRenderableWidget(Button.builder(Component.literal(T3Hud.ellipsize(font,row.title(),w-12)),b->mod.openTask(row.id())).bounds(x,y,w,20).build());
            }
            var prev=addRenderableWidget(Button.builder(Component.literal("<"),b->{pages[c]--;rebuild();}).bounds(x,height-54,24,18).build()); prev.active=pages[c]>0;
            var next=addRenderableWidget(Button.builder(Component.literal(">"),b->{pages[c]++;rebuild();}).bounds(x+w-24,height-54,24,18).build()); next.active=(pages[c]+1)*count<rows.size();
        }
        addRenderableWidget(Button.builder(Component.literal("Chat panel"),b->mod.openPanel()).bounds(16,height-30,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("History library"),b->mod.openLibrary()).bounds(112,height-30,110,20).build());
        addRenderableWidget(Button.builder(Component.literal("Checkpoint review"),b->mod.openReviews()).bounds(228,height-30,132,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width-76,height-30,60,20).build());
    }
    @Override public void tick() { if(!rendered.equals(mod.state().snapshot().threads())) rebuild(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(8,8,width-8,height-8,0xF0222529); g.text(font,"T3 task board · "+rendered.size()+" chats",16,18,0xFFFFFFFF,false);
        g.text(font,"Cards are T3 conversations. Select one to inspect its work.",16,34,0xFFA8A8A8,false);
        int w=(width-44)/4,count=Math.max(1,(height-124)/36);
        String[] names={"Idle","Working","Needs you / failed","Done"};
        for(int c=0;c<4;c++) {
            int x=16+c*(w+4); var rows=lanes.get(c);
            g.text(font,T3Hud.ellipsize(font,names[c]+" · "+rows.size(),w),x,54,0xFF93C5FD,false);
            for(int i=pages[c]*count;i<Math.min(rows.size(),(pages[c]+1)*count);i++) { var row=rows.get(i); String label=mod.state().online(row.id()) ? row.projectTitle() : "Offline · "+row.projectTitle(); g.text(font,T3Hud.ellipsize(font,label,w),x,94+(i-pages[c]*count)*36,0xFFA8A8A8,false); }
            g.text(font,(pages[c]+1)+"/"+Math.max(1,(rows.size()+count-1)/count),x+30,height-49,0xFFA8A8A8,false);
        }
        super.extractRenderState(g,mx,my,delta);
    }
}

