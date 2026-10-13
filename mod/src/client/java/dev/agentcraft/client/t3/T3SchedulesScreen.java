package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class T3SchedulesScreen extends T3FormScreen {
	private final Field number = field("Schedule number", "1"), name = field("Title", ""), prompt = field("Prompt", "");
	private final Field kind = choice("Schedule", "Interval", "Interval", "Every day", "Weekdays", "Custom days"), time = field("Minutes / HH:MM", "60");
	private final Field weekdays = field("Custom days (0 Sun to 6 Sat)", "1,2,3,4,5");
	private final Field destination = choice("Run in", "Current chat", "Current chat", "New chat each run");
	private volatile List<JsonObject> schedules = List.of();
	private JsonObject editing;
	private boolean loaded;
	private String saveId, saveFingerprint;
	T3SchedulesScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super("Schedules", mod, row);
		action("Refresh", this::load); action("Load selected", () -> { try { editing = selected().deepCopy(); name.value = text(editing, "title"); prompt.value = text(editing, "prompt"); var schedule = editing.getAsJsonObject("schedule"); kind.value = text(schedule, "type").equals("interval") ? "Interval" : T3Protocol.array(schedule, "weekdays").isEmpty() ? "Every day" : "Custom days"; weekdays.value = String.join(",", T3Protocol.array(schedule, "weekdays").asList().stream().map(v -> v.getAsString()).toList()); time.value = kind.value.equals("Interval") ? "" + (schedule.get("everyMs").getAsLong() / 60000) : text(schedule, "timeOfDay"); destination.value = text(editing, "threadId").isBlank() ? "New chat each run" : "Current chat"; saveId = null; rebuildWidgets(); } catch (Exception e) { message = e.getMessage(); } });
		action("New schedule", () -> { editing = null; saveId = null; name.value = ""; prompt.value = ""; rebuildWidgets(); message = "Creating a new schedule."; });
		action("Save", this::save); action("Enable / disable", () -> mutate("scheduledTasks.setEnabled")); action("Run now", () -> mutate("scheduledTasks.runNow")); action("Delete", () -> mutate("scheduledTasks.delete"));
	}
	@Override protected void init() { super.init(); if (!loaded) { loaded = true; load(); } }
	private void load() { request(this::catalog); }
	private String catalog() throws Exception {
		var result = api.rpc("scheduledTasks.list", new JsonObject()); List<JsonObject> list = new ArrayList<>();
		for (var v : T3Protocol.array(result, "tasks")) { var task = v.getAsJsonObject(); if (text(row.raw(), "projectId").equals(text(task, "projectId"))) list.add(task); } schedules = List.copyOf(list);
		StringBuilder out = new StringBuilder(); for (int i = 0; i < schedules.size(); i++) { var t = schedules.get(i); out.append(i + 1).append(". ").append(text(t, "title")).append(t.get("enabled").getAsBoolean() ? " · enabled" : " · disabled").append(" · next ").append(text(t, "nextRunAt")).append(" · ").append(text(t, "lastRunStatus")).append('\n'); if (!text(t, "lastRunError").isBlank()) out.append(text(t, "lastRunError")).append('\n'); } return out.isEmpty() ? "No schedules in this project. Fixed times use the T3 machine's local time." : out.toString();
	}
	private JsonObject selected() throws java.io.IOException { try { return schedules.get(Integer.parseInt(number.value.strip()) - 1); } catch (Exception e) { throw new java.io.IOException("Choose a schedule number from the list."); } }
	private void save() {
		try {
			JsonObject schedule = new JsonObject();
			if (kind.value.equals("Interval")) { long minutes = Long.parseLong(time.value.strip()); if (minutes < 1 || minutes > 525600) throw new IllegalArgumentException("Use an interval from 1 minute to 1 year."); schedule.addProperty("type", "interval"); schedule.addProperty("everyMs", minutes * 60000); }
			else { String clock = time.value.strip(); if (!clock.matches("([01]?\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException("Enter a time such as 09:00."); schedule.addProperty("type", "fixed_time"); schedule.addProperty("timeOfDay", clock); if (kind.value.equals("Weekdays") || kind.value.equals("Custom days")) { JsonArray days = new JsonArray(); if (kind.value.equals("Weekdays")) for (int d = 1; d <= 5; d++) days.add(d); else for (String value : weekdays.value.split(",")) { int d = Integer.parseInt(value.strip()); if (d < 0 || d > 6) throw new IllegalArgumentException("Weekdays must be 0 through 6."); if (!days.contains(new JsonPrimitive(d))) days.add(d); } if (days.isEmpty()) throw new IllegalArgumentException("Choose at least one day."); schedule.add("weekdays", days); } }
			JsonObject input = values("title", require(name.value, "a title"), "prompt", require(prompt.value, "a prompt"), "projectId", text(row.raw(), "projectId"));
			JsonObject template = editing == null ? row.raw() : editing;
			input.add("modelSelection", template.get("modelSelection")); input.add("runtimeMode", template.get("runtimeMode")); input.add("interactionMode", template.get("interactionMode")); input.add("schedule", schedule);
			input.addProperty("enabled", editing == null || editing.get("enabled").getAsBoolean()); input.add("threadId", destination.value.equals("Current chat") ? new JsonPrimitive(editing == null || text(editing, "threadId").isBlank() ? row.id() : text(editing, "threadId")) : JsonNull.INSTANCE);
			JsonObject strategy = editing == null ? values("type", text(row.raw(), "worktreePath").isBlank() ? "root" : "existing_worktree") : editing.getAsJsonObject("workspaceStrategy").deepCopy(); if (editing == null && !text(row.raw(), "worktreePath").isBlank()) strategy.addProperty("worktreePath", text(row.raw(), "worktreePath")); input.add("workspaceStrategy", strategy);
			if (editing != null) { input.add("id", editing.get("id")); input.addProperty("requireExisting", true); }
			String fingerprint = input.toString(); if (saveId == null || !fingerprint.equals(saveFingerprint)) { saveId = UUID.randomUUID().toString(); saveFingerprint = fingerprint; } input.addProperty("commandId", saveId);
			request(() -> { var saved = api.rpc("scheduledTasks.upsert", input); editing = saved.has("task") ? saved.getAsJsonObject("task").deepCopy() : saved.deepCopy(); return catalog(); }, () -> { saveId = null; if (!editing.has("id")) { editing = null; name.value = ""; prompt.value = ""; rebuildWidgets(); } });
		} catch (Exception e) { message = e.getMessage(); }
	}
	private void mutate(String method) {
		try { JsonObject selected = selected(); JsonObject input = values("id", text(selected, "id")); if (method.endsWith("setEnabled")) input.addProperty("enabled", !selected.get("enabled").getAsBoolean()); request(() -> { api.rpc(method, input); return catalog(); }); } catch (Exception e) { message = e.getMessage(); }
	}
}
