package dev.agentcraft.client.t3;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;

/** User actions tested only against isolated fixtures, never real T3 chats. */
public final class WriteChecks {
	public static void main(String[] args) throws Exception {
		String base = "http://127.0.0.1:" + args[0]; get(base + "/_qa/reset");
		var api = new T3Api(base, "fixture-only");
		var template = api.shell().getAsJsonArray("threads").get(0).getAsJsonObject();
		String existing = template.get("id").getAsString();
		template.addProperty("activeRunId", "fixture-active-run");
		var model = T3Api.modelSelection("codex", "fixture-model");
		api.sendPrompt(template, "Fixture reply", model);
		String created = api.startThread(template, "Explicit new chat", model);
		check(!created.equals(existing), "new chat has a different identity");
		api.configure(existing, "title", "Renamed fixture"); api.configure(existing, "interactionMode", "plan"); api.configure(existing, "runtimeMode", "approval-required");
		var commands = JsonParser.parseString(get(base + "/_qa/dispatches")).getAsJsonArray();
		boolean creation = false, modelChanged = false, replied = false;
		for (var value : commands) {
			var command = value.getAsJsonObject(); String type = command.get("type").getAsString();
			if (type.equals("thread.create")) { creation = true; check(command.get("threadId").getAsString().equals(created), "create identity matches new conversation"); check(command.get("projectId").equals(template.get("projectId")), "new chat uses selected T3 project"); }
			if (type.equals("thread.model-selection.set")) modelChanged = true;
			if (type.equals("message.dispatch")) {
				String mode = command.getAsJsonObject("dispatchMode").get("type").getAsString();
				if (command.get("threadId").getAsString().equals(existing)) { replied = true; check(command.get("text").getAsString().equals("Fixture reply"), "reply targets existing chat"); check(mode.equals("queue_after_active"), "reply queues behind the existing active run"); }
				else check(mode.equals("start_immediately"), "new chat never inherits another chat's active run");
			}
			check(!command.has("createdAt"), "protocol 2 command shape");
		}
		check(creation && modelChanged && replied && commands.size() == 7, "all explicit commands dispatched once");
		System.out.println("PASS existing-chat reply, explicit New chat, project ownership, model selection, rename, plan mode and permission mode; fixtures only");
	}
	private static String get(String url) throws Exception { return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).body(); }
	private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
