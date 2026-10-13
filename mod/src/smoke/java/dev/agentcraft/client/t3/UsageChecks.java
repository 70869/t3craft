package dev.agentcraft.client.t3;

import com.google.gson.JsonParser;

final class UsageChecks {
	static void run() {
		var config = JsonParser.parseString("""
			{"providers":[{"displayName":"Codex","usageLimits":{"checkedAt":"2026-10-06T00:00:00Z",
			"windows":[{"label":"Session","usedPercent":45},{"label":"Weekly","usedPercent":43}]}}],
			"usageLimitSources":[{"label":"Other account","accounts":[{"driver":"codex","usageLimits":
			{"windows":[{"label":"Session","usedPercent":80}]}}]}]}
			""").getAsJsonObject();
		var summary = JsonParser.parseString("""
			{"contractVersion":6,"sinceDay":"2026-09-07","untilDay":"2026-10-06","readAt":"2026-10-06T00:00:00Z",
			"buckets":[{"totals":{"uncachedInputTokens":100,"cachedInputTokens":200,"cacheCreationTokens":30,
			"outputTokens":70,"reasoningTokens":50},"costUsd":1.25,"unpricedRecords":1}],"sources":[{"status":"partial"}]}
			""").getAsJsonObject();
		var report = T3Usage.parse("Test machine", config, summary);
		check(report.limits().size() == 3, "Provider and source accounts both appear");
		check(report.limits().get(0).remaining() == 55 && report.limits().get(1).remaining() == 57, "Screenshot percentages are remaining, not used");
		check(report.lines().contains("Total tokens  400"), "Reasoning is already included in output; cached categories count once");
		check(report.lines().contains("API-equivalent cost  $1.25 USD"), "Native cost retains its unit and meaning");
		check(report.lines().contains("Partial source coverage"), "Incomplete source coverage stays visible");
		check(report.lines().stream().anyMatch(s -> s.contains("cost incomplete")), "Unpriced records cannot silently look fully priced");
		var unavailable = T3Usage.parse("Test machine", config, null);
		check(unavailable.limits().size() == 3 && unavailable.lines().contains("Token usage & spend unavailable"), "Missing summary retains limits without inventing zero spend");
		summary.addProperty("contractVersion", 7);
		check(T3Usage.parse("Test machine", null, summary).lines().contains("Token usage & spend unavailable"), "Unknown contracts cannot display misleading totals");
		System.out.println("PASS usage limits, token accounting, estimated cost, partial coverage and unavailable data");
	}
	private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
