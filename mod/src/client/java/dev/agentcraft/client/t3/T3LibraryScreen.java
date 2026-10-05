package dev.agentcraft.client.t3;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Saved-chat library and checkpoint desk share discovery, but dispatch only their explicit read action. */
final class T3LibraryScreen extends Screen {
    private final T3CraftClient mod; private final boolean review;
    private int page; private EditBox search; private String query="";
    private List<T3State.ThreadRow> rendered=List.of();
    T3LibraryScreen(T3CraftClient mod,boolean review) { super(Component.literal(review ? "T3 checkpoint desk" : "T3 history library")); this.mod=mod; this.review=review; }
    @Override protected void init() {
        clearWidgets(); rows.clear();
        search=addRenderableWidget(new EditBox(font,16,38,width-32,20,Component.literal("Search saved T3 chats"))); search.setValue(query);
        search.setResponder(value->{query=value;page=0;rebuildRows();}); rebuildRows();
    }
    private final List<Button> rows=new ArrayList<>();
    private List<T3State.ThreadRow> source=List.of();
    @Override public void tick() { if(!source.equals(mod.state().snapshot().threads())) rebuildRows(); }
    private void rebuildRows() {
        for(var button:rows) removeWidget(button); rows.clear();
        source=mod.state().snapshot().threads();
        rendered=source.stream().filter(r->(r.title()+" "+r.projectTitle()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int count=Math.max(1,(height-112)/30); page=Math.min(page,Math.max(0,(rendered.size()-1)/count));
        for(int i=page*count;i<Math.min(rendered.size(),(page+1)*count);i++) {
            var row=rendered.get(i); int y=68+(i-page*count)*30;
            rows.add(addRenderableWidget(Button.builder(Component.literal(review ? "Inspect changes" : "Read history"),b->{mod.focus(row.id());if(review) mod.openReview(row.id());else mod.openHistory(row.id());}).bounds(width-134,y,118,20).build()));
        }
        var prev=addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page--;rebuildRows();}).bounds(16,height-30,78,20).build()); prev.active=page>0;rows.add(prev);
        var next=addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;rebuildRows();}).bounds(100,height-30,60,20).build()); next.active=(page+1)*count<rendered.size();rows.add(next);
        rows.add(addRenderableWidget(Button.builder(Component.literal("Task board"),b->mod.openBoard()).bounds(166,height-30,94,20).build()));
        rows.add(addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width-76,height-30,60,20).build()));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(8,8,width-8,height-8,0xF0222529); g.text(font,(review ? "T3 checkpoint desk" : "Saved T3 chat library")+" · "+rendered.size()+" chats",16,18,0xFFFFFFFF,false);
        int count=Math.max(1,(height-112)/30);
        for(int i=page*count;i<Math.min(rendered.size(),(page+1)*count);i++) { var row=rendered.get(i); int y=68+(i-page*count)*30;
            g.text(font,T3Hud.ellipsize(font,row.title(),width-166),16,y+1,0xFFD4D4D4,false);
            g.text(font,T3Hud.ellipsize(font,row.projectTitle()+" · "+(mod.state().online(row.id()) ? StudioProjection.label(row.status()) : "Offline"),width-166),16,y+13,0xFFA8A8A8,false);
        }
        super.extractRenderState(g,mx,my,delta);
    }
}
