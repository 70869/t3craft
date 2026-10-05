package dev.agentcraft.client.t3;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Read-only live check. Prints counts and availability, never chat content or credentials. */
public final class NativeProbe {
	public static void main(String[] args) throws Exception {
		var config = T3Config.load(Path.of(args[0]));
		if (!config.paired()) throw new IllegalStateException("Pair the client first.");
		var env = config.environments.getFirst();
		var api = new T3Api(env.baseUrl, env.accessToken);
		var shell = api.shell();
		var threads = shell.getAsJsonArray("threads");
		if (threads.isEmpty()) throw new IllegalStateException("T3 has no existing chats.");
		String selected = config.threadId;
		if (selected == null) selected = threads.get(0).getAsJsonObject().get("id").getAsString();
		var detail = api.thread(selected, 4);
		var focus = T3State.focus(detail);
		var history = api.history(selected, null);
		int historyItems = T3Protocol.array(history.getAsJsonObject("projection"), "turnItems").size();
		String historyCursor = T3Protocol.string(history, "historyCursor");
		if (historyCursor != null && history.get("hasMoreHistory").getAsBoolean()) historyItems += T3Protocol.array(api.history(selected, historyCursor), "items").size();
		System.out.println("PASS saved T3 timeline: " + historyItems + " items loaded through bounded history and available older page");
		try (var socket = api.openSocket(reason -> {})) {
			System.out.println("PASS live paired T3: protocol=" + api.negotiatedProtocol() + ", chats=" + threads.size()
				+ ", messages=" + focus.messages().size() + ", activity=" + focus.activity().size()
				+ ", available providers=" + api.providers(socket).size());
		}
		var state = new T3State(event -> {});
		state.connect(List.of(new T3State.Connection(api, env.label)), selected);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while ((!state.snapshot().connected() || !state.live()) && System.nanoTime() < deadline) Thread.sleep(100);
		if (!state.snapshot().connected() || !state.live()) throw new IllegalStateException("Live shell subscription did not connect.");
		System.out.println("PASS live socket subscription and T3 history projection; no messages sent or chats created.");
		System.exit(0);
	}
}
