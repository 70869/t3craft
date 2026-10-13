package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class T3QueueScreen extends T3FormScreen {
	private final Field number = field("Queued message", "1"), edit = field("Message", "");
	private volatile List<JsonObject> queued = List.of();
	private boolean loaded;
	T3QueueScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super("Run queue", mod, row);
		action("Refresh", this::load);
		action("Load message", () -> { try { edit.value = text(selected(), "text"); rebuildWidgets(); } catch (Exception e) { message = e.getMessage(); } });
		action("Save edit", () -> change("queued-run.edit")); action("Cancel message", () -> change("queued-run.cancel"));
		action("Move first", () -> change("queued-run.reorder")); action("Steer now", () -> change("queued-message.promote-to-steer"));
		action("Resume queue", () -> request(() -> { api.operate("queue.resume", row.id(), new JsonObject()); return describe(api.projection(row.id())); }));
	}
	@Override protected void init() { super.init(); if (!loaded) { loaded = true; load(); } }
	private void load() { request(() -> describe(api.projection(row.id()))); }
	private String describe(JsonObject projection) {
		List<JsonObject> values = new ArrayList<>();
		for (var runValue : T3Protocol.array(projection, "runs")) {
			var run = runValue.getAsJsonObject(); if (!"queued".equals(text(run, "status"))) continue; JsonObject item = run.deepCopy();
			for (var m : T3Protocol.array(projection, "messages")) if (text(run, "userMessageId").equals(text(m.getAsJsonObject(), "id"))) item.addProperty("text", text(m.getAsJsonObject(), "text")); values.add(item);
		}
		values.sort(Comparator.comparingInt(v -> v.has("queuePosition") && !v.get("queuePosition").isJsonNull() ? v.get("queuePosition").getAsInt() : Integer.MAX_VALUE)); queued = List.copyOf(values);
		StringBuilder out = new StringBuilder(); for (int i = 0; i < values.size(); i++) out.append(i + 1).append(". ").append(text(values.get(i), "text")).append('\n');
		return out.isEmpty() ? "No queued messages." : out.toString();
	}
	private JsonObject selected() throws java.io.IOException { try { return queued.get(Integer.parseInt(number.value.strip()) - 1); } catch (Exception e) { throw new java.io.IOException("Choose a queued message number from the list."); } }
	private void change(String type) {
		try {
			String run = text(selected(), "id"), content = edit.value;
			request(() -> {
				var p = api.projection(row.id()); describe(p); if (queued.stream().noneMatch(q -> run.equals(text(q, "id")))) throw new java.io.IOException("That message is no longer queued. Refresh the list.");
				JsonObject f = values("runId", run);
				if (type.equals("queued-run.edit")) f.addProperty("text", require(content, "a message"));
				if (type.equals("queued-run.reorder")) { if (text(queued.getFirst(), "id").equals(run)) return "That message is already first."; f.add("beforeRunId", queued.getFirst().get("id")); }
				if (type.equals("queued-message.promote-to-steer")) { String active = text(p.getAsJsonObject("thread"), "activeRunId"); f.remove("runId"); f.addProperty("queuedRunId", run); f.addProperty("targetRunId", require(active, "an active run")); }
				api.operate(type, row.id(), f); return describe(api.projection(row.id()));
			});
		} catch (Exception e) { message = e.getMessage(); }
	}
}
