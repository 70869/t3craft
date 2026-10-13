package dev.agentcraft.client.foreman;

import com.google.gson.JsonObject;
import dev.agentcraft.client.foreman.Protocol.Ack;
import dev.agentcraft.client.foreman.Protocol.Diff;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Compatibility for historical rendering tools. Live networking belongs to the paired T3 client. */
public final class ForemanLink {
	public static final long ACK_TIMEOUT_MS = 20_000;
	private final URI uri;
	private final ForemanState state;
	public ForemanLink(URI uri, String version, ForemanState state, Executor executor, boolean enabled) { this.uri = uri; this.state = state; }
	public void start() {}
	public void stop() {}
	public void reconnectNow() { var client = dev.agentcraft.client.t3.T3CraftClient.get(); if (client != null) client.retryConnections(); }
	public LinkStatus status() { return state.link(); }
	public URI uri() { return uri; }
	public long messageCount() { return 0; }
	public long lastInboundAt() { return state.lastMessageAt(); }
	public void addDiffListener(Consumer<Diff> listener) {}
	public CompletableFuture<Ack> send(JsonObject message) { return CompletableFuture.failedFuture(new IllegalStateException("Use the native T3 controls.")); }
	public CompletableFuture<Diff> requestDiff(String repoId, String worktree) { return CompletableFuture.failedFuture(new IllegalStateException("Use T3 checkpoint review.")); }
}
