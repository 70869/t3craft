package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only, per-machine usage. Separate machines are never summed (their sources can overlap). */
public final class T3Usage {
	public record Limit(String label, double remaining, String resetsAt, String checkedAt) {}
	public record Report(String machine, List<Limit> limits, List<String> lines, String readAt) {}
	private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "t3craft-usage"); t.setDaemon(true); return t;
	});
	private static final AtomicBoolean BUSY = new AtomicBoolean();
	private static volatile List<Report> reports = List.of();
	private static long nextRefresh;
	private T3Usage() {}

	/** Called only while a usage wall is visible; all network reads stay off the render thread. */
	public static List<Report> visible(T3State state) {
		if (System.currentTimeMillis() >= nextRefresh && BUSY.compareAndSet(false, true)) {
			nextRefresh = System.currentTimeMillis() + 60_000;
			WORKER.execute(() -> {
				try {
					List<Report> next = new ArrayList<>();
					for (var machine : state.machines()) {
						var api = state.apiForOwner(machine.owner());
						if (!machine.online() || api == null) { next.add(unavailable(machine.label(), "Machine offline")); continue; }
						try { next.add(api.usage(machine.label())); }
						catch (Exception e) { next.add(unavailable(machine.label(), "Usage unavailable · check T3 connection")); }
					}
					reports = List.copyOf(next);
				} finally { BUSY.set(false); }
			});
		}
		return reports;
	}
	private static Report unavailable(String machine, String message) {
		return new Report(machine, List.of(), List.of(message), "");
	}

	static Report parse(String machine, JsonObject config, JsonObject summary) {
		List<Limit> limits = new ArrayList<>(); List<String> lines = new ArrayList<>();
		if (config != null) {
			for (var value : T3Protocol.array(config, "providers")) {
				var p = value.getAsJsonObject();
				appendLimits(limits, text(p, "displayName").isBlank() ? text(p, "driver") : text(p, "displayName"), T3Protocol.object(p, "usageLimits"));
			}
			for (var value : T3Protocol.array(config, "usageLimitSources")) {
				var source = value.getAsJsonObject();
				for (var account : T3Protocol.array(source, "accounts")) {
					var a = account.getAsJsonObject(); appendLimits(limits, text(source, "label") + " · " + text(a, "driver"), T3Protocol.object(a, "usageLimits"));
				}
			}
		}
		if (summary == null || number(summary, "contractVersion") < 4 || number(summary, "contractVersion") > 6) {
			lines.add("Token usage & spend unavailable");
		} else {
			long input = 0, cached = 0, created = 0, output = 0, unpriced = 0; double cost = 0;
			for (var value : T3Protocol.array(summary, "buckets")) {
				var b = value.getAsJsonObject(); var t = T3Protocol.object(b, "totals");
				if (t == null) continue;
				input += (long) number(t, "uncachedInputTokens"); cached += (long) number(t, "cachedInputTokens");
				created += (long) number(t, "cacheCreationTokens"); output += (long) number(t, "outputTokens");
				cost += number(b, "costUsd"); unpriced += (long) number(b, "unpricedRecords");
			}
			lines.add(text(summary, "sinceDay") + " to " + text(summary, "untilDay"));
			lines.add("Total tokens  " + count(input + cached + created + output));
			lines.add("Input  " + count(input) + "   Output  " + count(output));
			lines.add("Cache read  " + count(cached) + "   Cache write  " + count(created));
			lines.add(String.format(Locale.ROOT, "API-equivalent cost  $%,.2f USD", cost));
			lines.add("Subscription bills separately · not actual spend");
			boolean partial = T3Protocol.array(summary, "sources").isEmpty();
			for (var source : T3Protocol.array(summary, "sources")) if (!"ok".equals(text(source.getAsJsonObject(), "status"))) partial = true;
			lines.add(partial ? "Partial source coverage" : "Native session usage · this machine");
			if (unpriced > 0) lines.add(count(unpriced) + " unpriced responses · cost incomplete");
		}
		return new Report(machine, List.copyOf(limits), List.copyOf(lines), summary == null ? "" : text(summary, "readAt"));
	}
	private static void appendLimits(List<Limit> out, String provider, JsonObject usage) {
		if (usage == null) return;
		for (var value : T3Protocol.array(usage, "windows")) {
			var w = value.getAsJsonObject();
			out.add(new Limit(provider + " · " + text(w, "label"), Math.max(0, Math.min(100, 100 - number(w, "usedPercent"))), text(w, "resetsAt"), text(usage, "checkedAt")));
		}
	}
	private static String text(JsonObject o, String key) { return T3Protocol.string(o, key); }
	private static double number(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0; }
	private static String count(long n) { return String.format(Locale.ROOT, "%,d", n); }
}
