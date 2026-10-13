package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;

final class T3ArchiveScreen extends T3FormScreen {
	private final Field number = field("Chat number", "1");
	private volatile List<JsonObject> archived = List.of();
	private boolean loaded;
	T3ArchiveScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super("Archived chats", mod, row); requiresThread = false; action("Refresh", this::load);
		action("Read history", () -> { try { minecraft.gui.setScreen(new T3HistoryScreen(mod, text(selected(), "id"), api)); } catch (Exception e) { message = e.getMessage(); } });
		action("Restore chat", () -> { try { String id = text(selected(), "id"); request(() -> { api.operate("thread.unarchive", id, new JsonObject()); return catalog(); }); } catch (Exception e) { message = e.getMessage(); } });
	}
	@Override protected void init() { super.init(); if (!loaded) { loaded = true; load(); } }
	private JsonObject selected() throws java.io.IOException { try { return archived.get(Integer.parseInt(number.value.strip()) - 1); } catch (Exception e) { throw new java.io.IOException("Choose a chat number from the list."); } }
	private void load() { request(this::catalog); }
	private String catalog() throws Exception {
		JsonObject result = api.rpc("orchestration.getArchivedShellSnapshot", new JsonObject()); List<JsonObject> list = new ArrayList<>();
		for (var item : T3Protocol.array(result, "threads")) list.add(item.getAsJsonObject()); archived = List.copyOf(list);
		StringBuilder out = new StringBuilder(); for (int i = 0; i < list.size(); i++) out.append(i + 1).append(". ").append(text(list.get(i), "title")).append('\n'); return out.isEmpty() ? "No archived chats on this machine." : out.toString();
	}
}
