package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class T3TasksScreen extends T3FormScreen {
	private final Field number = field("Task number", "1"), brief = field("New task brief", ""), modelNumber = field("Model number", "1");
	private volatile List<JsonObject> tasks = List.of(), models = List.of();
	private boolean loaded;
	private JsonObject pending;
	T3TasksScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super("Delegated tasks", mod, row);
		action("Refresh", this::load); action("Delegate task", this::delegate);
		action("Read result", () -> { try { var task = selected(); output = text(task, "title") + "\n" + text(task, "status") + "\n" + text(task, "progress") + "\n\n" + text(task, "result"); } catch (Exception e) { message = e.getMessage(); } });
		action("Open child chat", () -> { try { String child = require(text(selected(), "childThreadId"), "an app-owned child chat"); mod.focus(child); mod.openPanel(); } catch (Exception e) { message = e.getMessage(); } });
		action("Cancel task", this::cancel);
	}
	@Override protected void init() { super.init(); if (!loaded) { loaded = true; load(); } }
	private void load() { request(() -> catalog(api.projection(row.id()))); }
	private String catalog(JsonObject p) throws Exception {
		List<JsonObject> list = new ArrayList<>(); for (var v : T3Protocol.array(p, "subagents")) list.add(v.getAsJsonObject()); tasks = List.copyOf(list);
		List<JsonObject> choices = new ArrayList<>(); try (var socket = api.openSocket(reason -> {})) { for (var provider : api.providers(socket)) for (var m : provider.models()) { var model = T3Api.modelSelection(provider.instanceId(), m.slug()); model.addProperty("label", provider.name() + " · " + m.name()); choices.add(model); } } models = List.copyOf(choices);
		StringBuilder out = new StringBuilder("Parent: " + row.title() + "\n");
		for (int i = 0; i < tasks.size(); i++) { var t = tasks.get(i); out.append(i + 1).append(". ").append(text(t, "title")).append(" · ").append(text(t, "status")).append(" · ").append(text(t, "origin")).append('\n').append(text(t, "progress")).append('\n'); }
		if (tasks.isEmpty()) out.append("No delegated tasks. Creating one requires an active parent run.\n");
		out.append("\nModels:\n"); for (int i = 0; i < models.size(); i++) out.append(i + 1).append(". ").append(text(models.get(i), "label")).append('\n'); return out.toString();
	}
	private JsonObject selected() throws java.io.IOException { try { return tasks.get(Integer.parseInt(number.value.strip()) - 1); } catch (Exception e) { throw new java.io.IOException("Choose a task number from the list."); } }
	private void delegate() {
		try {
			String content = require(brief.value, "a task brief"); JsonObject model = models.get(Integer.parseInt(modelNumber.value.strip()) - 1).deepCopy(); model.remove("label");
			request(() -> {
				var p = api.projection(row.id()); var thread = p.getAsJsonObject("thread"); String active = require(text(thread, "activeRunId"), "an active parent run"); JsonObject run = null;
				for (var v : T3Protocol.array(p, "runs")) if (active.equals(text(v.getAsJsonObject(), "id"))) run = v.getAsJsonObject();
				if (run == null || text(run, "rootNodeId").isBlank()) throw new java.io.IOException("The parent run has no delegation context yet. Wait until it starts.");
				if (pending == null || !content.equals(text(pending, "task")) || !active.equals(text(pending, "parentRunId")) || !model.equals(pending.get("modelSelection"))) {
					pending = values("parentThreadId", row.id(), "parentRunId", active, "parentNodeId", text(run, "rootNodeId"), "task", content, "completionWake", "always", "createdBy", "user", "creationSource", "web", "commandId", UUID.randomUUID().toString(), "runtimeMode", text(thread, "runtimeMode"), "interactionMode", text(thread, "interactionMode")); pending.add("modelSelection", model);
				}
				api.operate("delegated_task.request", row.id(), pending); pending = null; return catalog(api.projection(row.id()));
			});
		} catch (Exception e) { message = e.getMessage(); }
	}
	private void cancel() {
		try {
			String taskId = text(selected(), "id");
			request(() -> {
				var p = api.projection(row.id()); JsonObject task = null;
				for (var v : T3Protocol.array(p, "subagents")) if (taskId.equals(text(v.getAsJsonObject(), "id"))) task = v.getAsJsonObject();
				if (task == null || !"app_owned".equals(text(task, "origin"))) throw new java.io.IOException("Only this parent's app-owned tasks can be cancelled here.");
				String state = text(task, "status");
				if (!List.of("completed", "failed", "cancelled", "interrupted").contains(state)) {
					String child = require(text(task, "childThreadId"), "a started child chat"); var detail = api.projection(child); String active = require(text(detail.getAsJsonObject("thread"), "activeRunId"), "an interruptible child run");
					api.operate("run.interrupt", child, values("runId", active));
				}
				api.operate("delegated_task.completion-delivery.dispose", row.id(), values("parentThreadId", row.id(), "taskId", taskId)); return catalog(api.projection(row.id()));
			});
		} catch (Exception e) { message = e.getMessage(); }
	}
}
