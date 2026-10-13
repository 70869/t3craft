package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real Windows PTY against a disposable T3 home, never the user's paired environment. */
public final class TerminalLiveChecks {
	public static void main(String[] args) throws Exception {
		Path workspace = Path.of(args[2]).toRealPath();
		String origin = args[1];
		check(origin.matches("http://127\\.0\\.0\\.1:293[0-9]{2}")
			&& Files.readString(workspace.getParent().resolve("t3craft-terminal-qa.marker")).strip().equals(origin), "Disposable server marker required");
		var grant = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
		var pairing = T3Api.pair(origin + "/pair#token=" + grant.get("credential").getAsString(), "T3Craft isolated shell QA");
		var api = new T3Api(pairing.baseUrl(), pairing.accessToken()); api.requireProtocol2();
		var before = api.shell();
		for (var project : T3Protocol.array(before, "projects")) check(Path.of(T3Protocol.string(project.getAsJsonObject(), "workspaceRoot")).toRealPath().equals(workspace), "QA projects must stay inside the disposable checkout");
		String threadsBefore = T3Protocol.array(before, "threads").toString();
		var params = values("cwd", workspace.toString()); params.addProperty("cols", 80); params.addProperty("rows", 24);
		var output = new StringBuffer();
		try {
			try (var socket = api.openSocket(reason -> {})) {
				check("running".equals(T3Protocol.string(socket.call("terminal.open", params, 20), "status")), "Shell must start");
				String stream = socket.stream("terminal.attach", values(), value -> {
					if ("output".equals(T3Protocol.string(value, "type"))) output.append(T3Protocol.string(value, "data"));
				});
				write(socket, "Write-Output ('T3CRAFT_' + 'SHELL_OK'); Write-Output ('QA_' + 'PWD=' + (Get-Location).Path)\r");
				waitFor(() -> output.toString().contains("T3CRAFT_SHELL_OK") && output.toString().contains("QA_PWD=" + workspace), "Real shell output and checkout");
				var size = values(); size.addProperty("cols", 100); size.addProperty("rows", 30); socket.call("terminal.resize", size, 20);
				write(socket, "Start-Sleep -Seconds 60; Write-Output ('SHOULD_' + 'NOT_RUN')\r"); Thread.sleep(700);
				write(socket, "\u0003"); Thread.sleep(300); write(socket, "Write-Output ('AFTER_' + 'INTERRUPT')\r");
				waitFor(() -> output.toString().contains("AFTER_INTERRUPT"), "Ctrl+C interrupts real command");
				check(!output.toString().contains("SHOULD_NOT_RUN"), "Interrupted command must not complete");
				socket.interrupt(stream);
			}
			try (var socket = api.openSocket(reason -> {})) {
				var snapshot = socket.call("terminal.open", params, 20);
				check(T3Protocol.string(snapshot, "history").contains("T3CRAFT_SHELL_OK"), "Detach/reconnect retains real shell history");
				socket.call("terminal.close", values(), 20);
				check("running".equals(T3Protocol.string(socket.call("terminal.restart", params, 20), "status")), "Explicit shell restart");
				socket.call("terminal.close", values(), 20);
			}
			check(T3Protocol.array(api.shell(), "threads").toString().equals(threadsBefore), "Terminal actions must leave startup conversations and runs unchanged");
			System.out.println("PASS real isolated T3 Windows PTY: Java pairing permissions, streamed output, checkout, resize, Ctrl+C, detach/reconnect, restart and close; no chats or runs dispatched");
		} finally { try (var socket = api.openSocket(reason -> {})) { socket.call("terminal.close", values(), 20); } }
	}
	private static JsonObject values(String... pairs) {
		var value = new JsonObject(); value.addProperty("threadId", "isolated-terminal-qa"); value.addProperty("terminalId", "t3craft");
		for (int i = 0; i < pairs.length; i += 2) value.addProperty(pairs[i], pairs[i + 1]); return value;
	}
	private static void write(T3Socket socket, String data) throws Exception { socket.call("terminal.write", values("data", data), 20); }
	private static void waitFor(BooleanSupplier condition, String label) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(100);
		check(condition.getAsBoolean(), label);
	}
	private static void check(boolean value, String label) { if (!value) throw new IllegalStateException(label); }
}
