package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.net.URI;
import java.util.UUID;

/** Native Git/PR operations with exact project, checkout and pull-request identities. */
final class T3GitScreen extends T3FormScreen {
	private final Field commit = field("Commit message", ""), prUrl = field("Pull request URL", ""), body = field("Review / PR body", "");
	private final Field verdict = choice("Review verdict", "comment", "comment", "approve", "request-changes"), merge = choice("Merge method", "squash", "squash", "merge", "rebase");
	private boolean loaded;
	private JsonObject gitRequest;
	T3GitScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super("Git & pull requests", mod, row);
		action("Status & checks", this::status); action("Commit", () -> git("commit")); action("Push", () -> git("push")); action("Create PR", () -> git("create_pr"));
		action("Save PR body", this::updatePr); action("Submit review", this::review); action("Watch PR", () -> watch(true)); action("Stop watching", () -> watch(false)); action("Merge PR", this::merge);
	}
	@Override protected void init() { super.init(); if (!loaded) { loaded = true; var linked = T3Protocol.object(row.raw(), "linkedPullRequest"); if (linked != null) prUrl.value = text(linked, "url"); status(); } }
	private JsonObject reference(String url) throws Exception {
		URI uri = URI.create(require(url, "a pull request URL"));
		String[] parts = uri.getPath().split("/");
		if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || parts.length < 5 || !"pull".equals(parts[3])) throw new java.io.IOException("Use a full HTTPS pull request URL, such as https://github.com/owner/repo/pull/123.");
		int number = Integer.parseInt(parts[4]); if (number < 1) throw new java.io.IOException("Invalid pull request number.");
		JsonObject ref = values("projectId", text(row.raw(), "projectId"), "host", uri.getHost(), "repository", parts[1] + "/" + parts[2]); ref.addProperty("number", number); return ref;
	}
	private void link(JsonObject ref, String url) throws Exception {
		JsonObject f = ref.deepCopy(); f.remove("projectId"); f.addProperty("url", url); f.addProperty("source", "manual"); api.operate("thread.pull-request.link", row.id(), f);
	}
	private void status() {
		String url = prUrl.value;
		request(() -> {
			JsonObject status = api.rpc("vcs.refreshStatus", values("cwd", T3WorkspaceScreen.root(api, api.thread(row.id(), 4))));
			StringBuilder out = new StringBuilder("Branch: " + text(status, "refName") + "\n");
			var working = T3Protocol.object(status, "workingTree"); if (working != null) for (var f : T3Protocol.array(working, "files")) out.append(text(f.getAsJsonObject(), "path")).append(" +").append(text(f.getAsJsonObject(), "insertions")).append(" -").append(text(f.getAsJsonObject(), "deletions")).append('\n');
			JsonObject pr = T3Protocol.object(status, "pr"); if (url.isBlank() && pr != null) urlFromStatus = text(pr, "url"); else urlFromStatus = url;
			if (!urlFromStatus.isBlank()) {
				var ref = reference(urlFromStatus); var detail = api.rpc("pullRequests.detail", ref); var checks = api.rpc("pullRequests.checks", ref);
				out.append("\n").append(text(detail, "title")).append(" · ").append(text(detail, "state")).append('\n').append(text(detail, "body")).append('\n');
				for (var check : T3Protocol.array(checks, "checks")) { var c = check.getAsJsonObject(); out.append(text(c, "name")).append(" · ").append(text(c, "status")).append('\n'); }
				for (var v : T3Protocol.array(api.projection(row.id()).getAsJsonObject("thread"), "pullRequests")) { var linked = v.getAsJsonObject(); if (text(linked, "repository").equals(text(ref, "repository")) && text(linked, "number").equals(text(ref, "number"))) out.append(T3Protocol.object(linked, "watch") == null ? "Not watching\n" : "T3 is watching for checks, comments and conflicts\n"); }
			}
			return out.toString();
		}, () -> { if (prUrl.value.isBlank() && !urlFromStatus.isBlank()) { prUrl.value = urlFromStatus; rebuildWidgets(); } });
	}
	private String urlFromStatus = "";
	private void git(String action) {
		String message = commit.value;
		request(() -> {
			String cwd = T3WorkspaceScreen.root(api, api.thread(row.id(), 4)); JsonObject input = values("cwd", cwd, "action", action, "threadId", row.id(), "projectId", text(row.raw(), "projectId"));
			if (action.equals("commit")) input.addProperty("commitMessage", require(message, "a commit message"));
			if (gitRequest == null || !same(gitRequest, input)) { input.addProperty("actionId", UUID.randomUUID().toString()); gitRequest = input; }
			var result = api.rpc("git.runStackedAction", gitRequest); JsonObject pr = T3Protocol.object(result, "pr");
			if (pr != null && !text(pr, "url").isBlank()) { urlFromStatus = text(pr, "url"); link(reference(urlFromStatus), urlFromStatus); }
			StringBuilder out = new StringBuilder(); for (String key : new String[]{"branch", "commit", "push", "pr"}) { var step = T3Protocol.object(result, key); if (step != null) out.append(key).append(": ").append(text(step, "status")).append(' ').append(text(step, "url")).append('\n'); } gitRequest = null; return out.toString();
		}, () -> { if (!urlFromStatus.isBlank()) { prUrl.value = urlFromStatus; rebuildWidgets(); } });
	}
	private static boolean same(JsonObject a, JsonObject b) { var copy = a.deepCopy(); copy.remove("actionId"); return copy.equals(b); }
	private void updatePr() {
		String url = prUrl.value, content = body.value;
		request(() -> { var ref = reference(url); link(ref, url); ref.addProperty("body", content); api.rpc("pullRequests.update", ref); return "Pull request description updated."; });
	}
	private void review() {
		String url = prUrl.value, content = body.value, chosen = verdict.value;
		request(() -> { var ref = reference(url); link(ref, url); ref.addProperty("body", content); ref.addProperty("verdict", chosen); ref.add("comments", new JsonArray()); api.rpc("pullRequests.submitReview", ref); return "Review submitted: " + chosen; });
	}
	private void watch(boolean enabled) {
		String url = prUrl.value;
		request(() -> { var ref = reference(url); link(ref, url); ref.remove("projectId"); ref.addProperty("watching", enabled); api.operate("thread.pull-request.watch", row.id(), ref); return enabled ? "T3 will wake this chat when checks finish, comments arrive or the branch conflicts." : "Pull request watch stopped."; });
	}
	private void merge() {
		String url = prUrl.value, method = merge.value;
		request(() -> { var ref = reference(url); link(ref, url); ref.addProperty("action", "merge"); ref.addProperty("mergeMethod", method); api.rpc("pullRequests.runAction", ref); return "Merge requested using " + method + ". Refresh to read the host's final status."; });
	}
}
