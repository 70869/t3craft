package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.HashMap;

/** Fault-injected transport checks. Every mutation stays inside the loopback fixture. */
public final class WorkflowChecks {
	public static void main(String[] args) throws Exception {
		String base = "http://127.0.0.1:" + args[0]; get(base + "/_qa/reset");
		get(base + "/_qa/invalid-protocol-metadata");
		try { new T3Api(base, "fixture-only").requireProtocol2(); throw new AssertionError("malformed protocol metadata must be rejected"); }
		catch (java.io.IOException expected) { check(expected.getMessage().contains("invalid protocol version"), "malformed protocol metadata gets a clear compatibility error"); }
		get(base + "/_qa/restore-protocol-metadata"); get(base + "/_qa/reset");
		T3Api api = new T3Api(base, "fixture-only"); var template = api.thread("A-done-0", 4);
		var model = T3Api.modelSelection("fixture", "fixture");
		try (var socket = api.openSocket(reason -> {})) { check(api.providers(socket).getFirst().models().getFirst().options().size() == 2, "live catalog retains select and boolean options"); }
		var drafts = new T3Drafts(new HashMap<>()); String key = T3Drafts.key(api.ownerKey(), "A-done-0", true);
		drafts.update(key, "First line\nSecond line");
		var prepared = api.prepareSend(template, drafts.get(key).text, model, true, "queue", new JsonArray());
		check(drafts.begin(key, prepared), "first send locks draft"); check(!drafts.begin(key, prepared), "double send rejected");
		get(base + "/_qa/lose-ack");
		try { api.deliver(prepared, () -> {}); throw new AssertionError("lost acknowledgement must fail"); } catch (java.io.IOException expected) { drafts.failed(key, "Disconnected"); }
		check(!drafts.get(key).text.isBlank() && prepared.acknowledged == 0, "ambiguous acceptance retains original draft and identities");
		Path privateDir = Files.createTempDirectory("t3craft-draft-check-");
		drafts.get(key).terminalInput = "echo terminal draft";
		T3Config config = new T3Config(); config.drafts.put(key, drafts.get(key)); Path file = privateDir.resolve("drafts.json"); check(config.save(file), "private draft persistence");
		var recovered = T3Config.load(file).drafts.get(key); check(recovered.text.contains("\n") && !recovered.sending && recovered.status.startsWith("Send interrupted"), "restart retains multiline draft without automatic replay");
		check(recovered.terminalInput.equals("echo terminal draft"), "terminal draft survives restart independently of a prepared chat send");
		api.deliver(recovered.prepared, () -> {}); check(commands(base).size() == 2, "lost create acknowledgement retries once without duplicate conversation or message");
		check(api.thread(prepared.threadId, 4).getAsJsonArray("messages").size() == 1, "one user message after retry");
		drafts.accepted(key); check(drafts.get(key).text.isEmpty() && drafts.get(key).prepared == null, "only acknowledgment clears draft");
		check(drafts.get(key).terminalInput.equals("echo terminal draft"), "chat acknowledgment does not clear the shell input");
		var reply = api.prepareSend(template, "Recover rejection", null, false, "queue", new JsonArray()); get(base + "/_qa/fail-next");
		try { api.deliver(reply, () -> {}); throw new AssertionError("fixture rejection must fail"); } catch (java.io.IOException expected) { check(reply.acknowledged == 0, "rejection does not advance outbox"); }
		api.deliver(reply, () -> {}); check(commands(base).size() == 3, "explicit rejection retry produces one message");
		template.addProperty("activeRunId", "A-done-0-turn"); var steer = api.prepareSend(template, "Change direction", null, false, "steer", new JsonArray()); api.deliver(steer, () -> {});
		var mode = commands(base).get(3).getAsJsonObject().getAsJsonObject("dispatchMode"); check(mode.get("type").getAsString().equals("steer_active") && mode.get("targetRunId").getAsString().equals("A-done-0-turn"), "steer names exact active run");
		Path attached = privateDir.resolve("notes.txt"); Files.writeString(attached, "fixture only");
		get(base + "/_qa/no-file-attachments");
		try { new T3Api(base, "fixture-only").validateAttachments(java.util.List.of(attached)); throw new AssertionError("unadvertised file uploads must be rejected before upload"); }
		catch (java.io.IOException expected) { check(expected.getMessage().contains("does not support file attachments"), "missing file capability gets an actionable compatibility message"); }
		get(base + "/_qa/small-file-limit");
		try { new T3Api(base, "fixture-only").validateAttachments(java.util.List.of(attached)); throw new AssertionError("advertised server upload limits must be enforced"); }
		catch (java.io.IOException expected) { check(expected.getMessage().contains("3 bytes"), "server-specific file limit is honored and reported accurately"); }
		get(base + "/_qa/enable-file-attachments");
		api.validateAttachments(java.util.List.of(attached)); JsonArray attachments = new JsonArray(); attachments.add(api.upload(attached));
		api.deliver(api.prepareSend(template, "Attached notes", null, false, "queue", attachments), () -> {}); check(commands(base).get(4).getAsJsonObject().getAsJsonArray("attachments").size() == 1, "signed upload included in accepted message");
		api.operate("thread.archive", "A-done-0", new JsonObject()); check(api.rpc("orchestration.getArchivedShellSnapshot", new JsonObject()).getAsJsonArray("threads").size() == 1, "archive remains discoverable");
		api.operate("thread.unarchive", "A-done-0", new JsonObject()); check(api.thread(api.fork("A-done-0"), 4).getAsJsonArray("messages").size() > 0, "fork retains timeline");
		var terminal = T3FormScreen.values("threadId", "A-done-0", "terminalId", "t3craft", "cwd", "/fixture"); check(api.rpc("terminal.open", terminal).get("history").getAsString().contains("ready"), "terminal opens on exact checkout");
		api.rpc("terminal.write", T3FormScreen.values("threadId", "A-done-0", "terminalId", "t3craft", "data", "echo fixture\r")); api.rpc("terminal.close", terminal);
		var text = new T3TerminalText(); text.append("progress 100%\r\u001b[Kdone\n\u001b[31mred\u001b[0m"); check(text.text().equals("done\nred"), "terminal carriage return, erase and ANSI colors");
		text.clear(); text.append("x".repeat(5000) + "\t\n" + "line\n".repeat(2100)); check(text.text().split("\n").length <= 2000, "terminal limits cannot hang on long tab or unbounded output");
		text.append("\u001b[2147483647B\u001b[2147483647CX"); check(text.text().length() <= 120000, "oversized cursor sequences and total transcript remain bounded");
		var range = api.review("A-done-0", 1, 1); check(range.turnCount() == 1, "explicit checkpoint comparison");
		try { api.review("A-done-0", 2, 1); throw new AssertionError("unknown checkpoint must fail"); } catch (java.io.IOException expected) {}
		Files.delete(attached); Files.delete(file); Files.delete(privateDir);
		System.out.println("PASS durable multiline drafts, interrupted restart, double-send guard, rejected/lost acknowledgements, idempotent retry, exact steer, uploads, archive/fork, terminal bounds and checkpoint ranges; fixture only");
	}
	private static JsonArray commands(String base) throws Exception { return JsonParser.parseString(get(base + "/_qa/dispatches")).getAsJsonArray(); }
	private static String get(String url) throws Exception { return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).body(); }
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
