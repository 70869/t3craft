package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Project and workspace are selected before launch; a handoff updates the real T3 binding. */
final class T3WorkspaceScreen extends T3FormScreen {
	private final boolean handoff;
	private final Field projectNumber, modelNumber, workspace, branch, base, path, prompt;
	private volatile List<JsonObject> projects = List.of();
	private volatile List<JsonObject> models = List.of();
	private JsonObject launchRequest, createdWorktree;
	private boolean loaded;
	T3WorkspaceScreen(T3CraftClient mod, T3State.ThreadRow row, boolean handoff) {
		super(handoff ? "Worktrees & handoff" : "New chat & workspace", mod, row); this.handoff = handoff; guidance = handoff ? "Create a branch checkout, then move this idle chat into it." : "Choose a project, model and checkout before sending the first message.";
		projectNumber = handoff ? null : choice("Project", "Loading…", "Loading…"); modelNumber = handoff ? null : choice("Model", "Loading…", "Loading…");
		workspace = handoff ? null : choice("Workspace", row != null && !text(row.raw(), "worktreePath").isBlank() ? "Existing worktree" : "Project root", "Project root", "New worktree", "Existing worktree");
		base = field("Base ref", row == null || text(row.raw(), "branch").isBlank() ? "HEAD" : text(row.raw(), "branch"));
		branch = field("New branch", ""); path = field("Worktree path", !handoff && row != null ? text(row.raw(), "worktreePath") : ""); prompt = handoff ? null : field("First message", "");
		action("Refresh", this::load);
		if (handoff) { action("List worktrees", () -> request(() -> refs())); action("Create & move chat", this::handoff); }
		else action("Create chat", this::launch);
	}
	@Override protected void init() { super.init(); if (!loaded) { loaded = true; load(); } }
	@Override protected List<Field> visibleFields() {
		if (handoff) return fields;
		return fields.stream().filter(f -> f != base && f != branch && f != path
			|| (f == base || f == branch) && workspace.value.equals("New worktree")
			|| f == path && workspace.value.equals("Existing worktree")).toList();
	}
	@Override protected void fieldChanged(Field field) { if (field == workspace) { output = checkoutHint(); rebuildWidgets(); } }
	@Override protected int outputReserve() { return 40; }
	private String checkoutHint() { return switch (workspace.value) {
		case "New worktree" -> "Create a separate branch checkout from the base ref.";
		case "Existing worktree" -> "Use an existing checkout on this T3 machine.";
		default -> "Use the selected project's existing checkout.";
	}; }
	private void load() {
		request(() -> {
			var shell = api.shell(); List<JsonObject> list = new ArrayList<>(); for (var p : T3Protocol.array(shell, "projects")) list.add(p.getAsJsonObject()); projects = List.copyOf(list);
			List<JsonObject> catalog = new ArrayList<>(); try (var socket = api.openSocket(reason -> {})) { for (var provider : api.providers(socket)) for (var m : provider.models()) { JsonObject model = T3Api.modelSelection(provider.instanceId(), m.slug()); model.addProperty("label", provider.name() + " · " + m.name()); catalog.add(model); } } models = List.copyOf(catalog);
			StringBuilder out = new StringBuilder(handoff ? "" : checkoutHint());
			if (handoff) out = new StringBuilder("Move this chat into a new worktree. Stop its active run first. Leave path blank for T3's managed location.\n\n" + refs());
			return out.toString();
        }, () -> {
            if (!handoff) {
                String selectedProject = projectNumber.value, selectedModel = modelNumber.value;
                projectNumber.choices = java.util.stream.IntStream.range(0, projects.size()).mapToObj(i -> (i + 1) + ". " + text(projects.get(i), "title")).toList();
                modelNumber.choices = java.util.stream.IntStream.range(0, models.size()).mapToObj(i -> (i + 1) + ". " + text(models.get(i), "label")).toList();
                projectNumber.value = projectNumber.choices.contains(selectedProject) ? selectedProject : projectNumber.choices.isEmpty() ? "No projects" : projectNumber.choices.getFirst();
                modelNumber.value = modelNumber.choices.contains(selectedModel) ? selectedModel : modelNumber.choices.isEmpty() ? "No models" : modelNumber.choices.getFirst();
                if ("Loading…".equals(selectedProject) && row != null) for (int i = 0; i < projects.size(); i++) if (text(projects.get(i), "id").equals(text(row.raw(), "projectId"))) projectNumber.value = projectNumber.choices.get(i);
                if ("Loading…".equals(selectedModel) && row != null) {
                    var selected = T3Protocol.object(row.raw(), "modelSelection");
                    if (selected != null) for (int i = 0; i < models.size(); i++) if (text(models.get(i), "instanceId").equals(text(selected, "instanceId")) && text(models.get(i), "model").equals(text(selected, "model"))) modelNumber.value = modelNumber.choices.get(i);
                }
                rebuildWidgets();
            }
        });
	}
	static String root(T3Api api, JsonObject thread) throws Exception {
		String worktree = text(thread, "worktreePath"); if (!worktree.isBlank()) return worktree;
		for (var p : T3Protocol.array(api.shell(), "projects")) if (text(thread, "projectId").equals(text(p.getAsJsonObject(), "id"))) return require(text(p.getAsJsonObject(), "workspaceRoot"), "a project workspace");
		throw new java.io.IOException("The project is unavailable on this machine.");
	}
	private String refs() throws Exception {
		var result = api.rpc("vcs.listRefs", values("cwd", root(api, api.thread(row.id(), 4)))); StringBuilder out = new StringBuilder();
		for (var v : T3Protocol.array(result, "refs")) { var ref = v.getAsJsonObject(); if (!text(ref, "worktreePath").isBlank()) out.append(text(ref, "name")).append(" · ").append(text(ref, "worktreePath")).append('\n'); }
		return out.isEmpty() ? "No worktrees reported." : out.toString();
	}
	private void launch() {
		try {
            if (projects.isEmpty() || models.isEmpty() || projectNumber.choices.indexOf(projectNumber.value) < 0 || modelNumber.choices.indexOf(modelNumber.value) < 0) throw new java.io.IOException("Refresh to load this machine’s projects and models first.");
			JsonObject project = projects.get(projectNumber.choices.indexOf(projectNumber.value)), model = models.get(modelNumber.choices.indexOf(modelNumber.value)).deepCopy(); model.remove("label");
			String content = require(prompt.value, "a first message"); JsonObject strategy = new JsonObject();
			switch (workspace.value) {
				case "New worktree" -> { strategy.addProperty("type", "worktree"); strategy.addProperty("baseRef", require(base.value, "a base ref")); strategy.addProperty("branch", require(branch.value, "a new branch")); }
				case "Existing worktree" -> { strategy.addProperty("type", "existing_worktree"); strategy.addProperty("worktreePath", require(path.value, "a worktree path")); }
				default -> { strategy.addProperty("type", "root"); }
			}
			JsonObject input = values("projectId", text(project, "id"), "title", content.lines().findFirst().orElse("New chat").substring(0, Math.min(80, content.lines().findFirst().orElse("New chat").length())), "creationSource", "web",
				"runtimeMode", row == null ? "approval-required" : text(row.raw(), "runtimeMode"), "interactionMode", row == null ? "default" : text(row.raw(), "interactionMode"));
			input.add("modelSelection", model); input.add("workspaceStrategy", strategy); JsonObject message = values("text", content); message.add("attachments", new JsonArray()); input.add("initialMessage", message);
			if (launchRequest == null || !sameLaunch(launchRequest, input)) { input.addProperty("commandId", UUID.randomUUID().toString()); input.addProperty("threadId", UUID.randomUUID().toString()); launchRequest = input; }
			JsonObject request = launchRequest.deepCopy();
			request(() -> { var result = api.rpc("orchestration.launchThread", request); return "Created chat " + text(result, "threadId") + ". T3 is preparing its workspace."; }, () -> { mod.focus(text(request, "threadId")); mod.openPanel(); });
		} catch (Exception e) { message = "Choose valid project/model numbers and workspace fields. " + e.getMessage(); }
	}
	private static boolean sameLaunch(JsonObject a, JsonObject b) { JsonObject copy = a.deepCopy(); copy.remove("commandId"); copy.remove("threadId"); return copy.equals(b); }
	private void handoff() {
		String chosenBase = base.value, chosenBranch = branch.value, chosenPath = path.value;
		request(() -> {
			JsonObject current = api.thread(row.id(), 4);
			if (!text(current, "activeRunId").isBlank()) throw new java.io.IOException("Stop the active run before moving its workspace.");
			if (!text(current, "worktreePath").isBlank()) {
				if (createdWorktree != null && text(current, "worktreePath").equals(text(createdWorktree, "path"))) return "Chat moved to " + text(createdWorktree, "path") + ".";
				throw new java.io.IOException("This chat is already in a worktree. Create a new chat to use another workspace.");
			}
			if (!text(current, "archivedAt").isBlank()) throw new java.io.IOException("Restore this chat before moving it.");
			if (createdWorktree == null) {
				JsonObject input = values("cwd", root(api, current), "refName", require(chosenBase, "a base ref"), "newRefName", require(chosenBranch, "a new branch")); input.add("path", chosenPath.isBlank() ? JsonNull.INSTANCE : new JsonPrimitive(chosenPath.strip()));
				createdWorktree = api.rpc("vcs.createWorktree", input).getAsJsonObject("worktree");
			}
			JsonObject f = values("branch", text(createdWorktree, "refName"), "worktreePath", text(createdWorktree, "path")); f.add("expectedWorktreePath", JsonNull.INSTANCE);
			try { api.operate("thread.metadata.update", row.id(), f); } catch (Exception e) { throw new java.io.IOException("Worktree created at " + text(createdWorktree, "path") + "; binding failed. Retry to bind the same worktree. " + e.getMessage()); }
			return "Chat moved to " + text(createdWorktree, "path") + ". Its next message runs there.";
		});
	}
}
